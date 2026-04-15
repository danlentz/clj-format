(ns clj-format.table
  "Tabular formatting facility built on the clj-format DSL.

  Table specifications follow the Hiccup convention used throughout
  clj-format. A table is expressed as `[:table opts? & cols]`, which
  clj-format.core/clj-format dispatches automatically:

    (clj-format nil [:table] rows)
    (clj-format nil [:table :name :age] rows)
    (clj-format nil
      [:table {:style :unicode :header-case :upcase}
        [:col :product {:width 15}]
        [:col :qty  {:align :right :format [:int {:group true}]}]]
      products)

  This namespace exposes two entry points:
    - table-dsl — build the DSL body + argument list for inspection
    - render-to — internal dispatch target used by clj-format

  Architecture is a strict pipeline. Every cell is preprocessed to a
  string at prep time; the rendered DSL uses `:str` directives
  exclusively. This trades a less showcase-y generated DSL for one
  uniform rendering path, single-mode semantics, and dramatically
  simpler cell and row construction."
  (:require [clojure.string       :as str]
            #?(:clj  [clojure.pprint :as pp]
               :cljs [cljs.pprint    :as pp])
            [clj-format.compiler  :as compiler]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Border Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private +border-styles+
  "Fully declarative style configurations.

   Every style carries explicit flags describing which rules and
   borders it emits, so downstream rule/row builders never need to
   special-case a style by name:

     :outer?    rows have left/right border characters
     :top?      emit a top border rule
     :bottom?   emit a bottom border rule
     :mid?      emit a mid rule (under header, above footer, between rows)
     :markdown? use markdown alignment markers for the mid rule

   Character keys (when present):
     :h         horizontal fill character
     :v         vertical separator character
     :tl :tr :bl :br   four corners
     :tt :bt           top/bottom T-junctions
     :lt :rt           left/right T-junctions
     :cross            interior crossroads
     :sep              column separator for borderless styles"
  {:ascii    {:outer? true  :top? true  :bottom? true  :mid? true
              :h "-" :v "|"
              :tl "+" :tr "+" :bl "+" :br "+"
              :tt "+" :bt "+" :lt "+" :rt "+" :cross "+"}
   :unicode  {:outer? true  :top? true  :bottom? true  :mid? true
              :h "─" :v "│"
              :tl "┌" :tr "┐" :bl "└" :br "┘"
              :tt "┬" :bt "┴" :lt "├" :rt "┤" :cross "┼"}
   :rounded  {:outer? true  :top? true  :bottom? true  :mid? true
              :h "─" :v "│"
              :tl "╭" :tr "╮" :bl "╰" :br "╯"
              :tt "┬" :bt "┴" :lt "├" :rt "┤" :cross "┼"}
   :heavy    {:outer? true  :top? true  :bottom? true  :mid? true
              :h "━" :v "┃"
              :tl "┏" :tr "┓" :bl "┗" :br "┛"
              :tt "┳" :bt "┻" :lt "┣" :rt "┫" :cross "╋"}
   :double   {:outer? true  :top? true  :bottom? true  :mid? true
              :h "═" :v "║"
              :tl "╔" :tr "╗" :bl "╚" :br "╝"
              :tt "╦" :bt "╩" :lt "╠" :rt "╣" :cross "╬"}
   :markdown {:outer? true  :top? false :bottom? false :mid? true  :markdown? true
              :h "-" :v "|"
              :tl "|" :tr "|" :bl "|" :br "|"
              :tt "|" :bt "|" :lt "|" :rt "|" :cross "|"}
   :org      {:outer? true  :top? true  :bottom? true  :mid? true
              :h "-" :v "|"
              :tl "|" :tr "|" :bl "|" :br "|"
              :tt "+" :bt "+" :lt "|" :rt "|" :cross "+"}
   :simple   {:outer? false :top? false :bottom? false :mid? true
              :h "-" :sep "  "}
   :none     {:outer? false :top? false :bottom? false :mid? false
              :sep "  "}})

(defn- resolve-style
  "Resolve a style keyword or custom map into a style configuration."
  [style]
  (cond
    (nil? style)     (+border-styles+ :ascii)
    (keyword? style) (or (+border-styles+ style)
                         (throw (ex-info "Unknown table style" {:style style})))
    (map? style)     style
    :else            (throw (ex-info "Invalid table style" {:style style}))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rule Construction
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- repeat-str
  "Repeat a single character/string `n` times and concatenate."
  [n s]
  (apply str (repeat n s)))

(defn- markdown-rule-string
  "Build the markdown header rule, using per-column alignment markers."
  [widths aligns]
  (let [segments (map (fn [w align]
                        (let [inner (max (- w 2) 0)]
                          (case (or align :left)
                            :left   (str ":" (repeat-str (inc inner) "-"))
                            :right  (str (repeat-str (inc inner) "-") ":")
                            :center (str ":" (repeat-str inner "-") ":"))))
                      widths aligns)]
    (str "| " (str/join " | " segments) " |")))

(defn- bordered-rule-string
  "Build a rule for a bordered style at the given position."
  [style position widths]
  (let [{:keys [h tl tr tt bl br bt lt rt cross]} style
        dash (or h "-")
        [left right junc] (case position
                            :top    [tl tr tt]
                            :mid    [lt rt cross]
                            :bottom [bl br bt])]
    (str left
         (str/join junc (map #(repeat-str (+ % 2) dash) widths))
         right)))

(defn- borderless-rule-string
  "Build a rule for a borderless style (simple, none, or custom)."
  [style widths]
  (let [dash (or (:h style) "-")
        sep  (or (:sep style) "  ")]
    (str/join sep (map #(repeat-str % dash) widths))))

(defn- rule-string
  "Dispatch to the rule builder appropriate for the style.

   `position` is :top, :mid, or :bottom. `aligns` is used only by
   markdown; other styles ignore it."
  [style position widths aligns]
  (cond
    (:markdown? style) (markdown-rule-string widths aligns)
    (:outer? style)    (bordered-rule-string style position widths)
    :else              (borderless-rule-string style widths)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Column Normalization
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- humanize-key
  "Convert a keyword into a human-readable title.
   Returns nil for non-keyword keys (computed columns)."
  [k]
  (when (keyword? k)
    (->> (str/split (name k) #"[-_]")
         (map str/capitalize)
         (str/join " "))))

(defn- normalize-column
  "Expand a column form into a fully-normalized column map.

   Accepted shapes, in order of terseness:
     :name                                     ;; bare keyword
     [:col :name]                              ;; explicit [:col k]
     [:col :name {:width 10 :align :right}]    ;; [:col k opts]
     [:col (fn [row] ...) {:title \"Name\"}]   ;; computed column
     {:key :name :width 10}                    ;; raw map (advanced)"
  [col]
  (let [base (cond
               (keyword? col)
               {:key col}

               (and (vector? col) (= :col (first col)))
               (let [[_ k opts] col]
                 (when-not (or (keyword? k) (fn? k))
                   (throw (ex-info "Column key must be a keyword or fn"
                                   {:column col})))
                 (merge (or opts {}) {:key k}))

               (map? col)
               col

               :else
               (throw (ex-info "Invalid column spec" {:column col})))]
    (assoc base :title (or (:title base)
                           (humanize-key (:key base))
                           "Column"))))

(defn- infer-columns
  "Infer column specifications from the first row of data."
  [rows]
  (when-let [row (first rows)]
    (cond
      (map? row)        (mapv normalize-column (keys row))
      (sequential? row) (mapv (fn [i] (normalize-column {:key i :title (str "Col " i)}))
                              (range (count row)))
      :else             [])))

(defn- merge-defaults
  "Apply table-level :defaults to every column (column opts win)."
  [columns defaults]
  (if (seq defaults)
    (mapv #(merge defaults %) columns)
    columns))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data Extraction and Value Formatting
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- extract-value
  "Extract a raw value from a row for a given column."
  [row col]
  (let [k (:key col)]
    (cond
      (fn? k)           (k row)
      (map? row)        (get row k)
      (sequential? row) (when (number? k) (nth row k nil))
      :else             nil)))

(defn- format-fn
  "Return a unary function that formats a value per the column's `:format`.

   The returned function is closed over a once-compiled cl-format
   string, so repeated invocations across rows do not re-compile the
   DSL. The `:format` may be a keyword, a DSL vector, or a Clojure
   function; in every case we return a `(fn [v] string)` closure."
  [col]
  (let [fmt (:format col :str)]
    (cond
      (fn? fmt)        fmt
      (= fmt :str)     str
      (= fmt :pr)      pr-str
      (keyword? fmt)   (let [fmt-str (compiler/compile-format [fmt])]
                         #(pp/cl-format nil fmt-str %))
      (vector? fmt)    (let [fmt-str (compiler/compile-format fmt)]
                         #(pp/cl-format nil fmt-str %))
      :else            str)))

(defn- with-format-fns
  "Attach a compiled `:format-fn` to every column. Called once per
   table so format strings are compiled once per column, not per cell."
  [columns]
  (mapv #(assoc % :format-fn (format-fn %)) columns))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Width Computation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- measure-width
  "Display width of a value for a column, using the column's format-fn."
  [v col nil-value]
  (if (nil? v)
    (count (str nil-value))
    (count ((:format-fn col) v))))

(defn- compute-width
  "Auto-sized width for a single column."
  [col rows nil-value]
  (let [title-w  (count (:title col))
        data-ws  (map #(measure-width (extract-value % col) col nil-value) rows)
        natural  (apply max title-w data-ws)
        min-w    (or (:min-width col) title-w)]
    (cond-> (max natural min-w)
      (:max-width col) (min (:max-width col)))))

(defn- compute-widths
  "Assign widths to every column, auto-sizing where not explicitly set."
  [columns rows nil-value]
  (mapv (fn [col]
          (if (:width col)
            col
            (assoc col :width (compute-width col rows nil-value))))
        columns))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Text Overflow
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- elide
  "Truncate `s` to `width`, appending `ellipsis` if truncation occurred."
  [s width ellipsis]
  (let [ell  (or ellipsis "...")
        elen (count ell)]
    (cond
      (<= (count s) width) s
      (<= width elen)      (subs s 0 width)
      :else                (str (subs s 0 (- width elen)) ell))))

(defn- wrap-line
  "Fit a single line of text within `width`.

   If the line already fits, it is preserved verbatim — interior
   whitespace is not collapsed. This matters for pre-formatted
   content like ASCII art banners and nested tables, where every
   space is load-bearing.

   If the line exceeds width, greedy word-wrap takes over: break at
   word boundaries, splitting overlong single words at the width
   boundary."
  [s width]
  (cond
    (empty? s)           [""]
    (<= (count s) width) [s]
    :else
    (let [words (str/split s #"\s+")]
      (loop [remaining words
             line      ""
             lines     []]
        (if (empty? remaining)
          (if (empty? line) lines (conj lines line))
          (let [word      (first remaining)
                candidate (if (empty? line) word (str line " " word))]
            (cond
              (<= (count candidate) width)
              (recur (rest remaining) candidate lines)

              (empty? line)
              (if (<= (count word) width)
                (recur (rest remaining) word lines)
                (recur (cons (subs word width) (rest remaining))
                       ""
                       (conj lines (subs word 0 width))))

              :else
              (recur remaining "" (conj lines line)))))))))

(defn- word-wrap
  "Split a string into lines that fit within width.

   Preserves explicit newlines as hard breaks and wraps each
   resulting segment with `wrap-line`."
  [s width]
  (cond
    (or (nil? s) (= "" s)) [""]
    (<= width 0)           [s]
    :else                  (vec (mapcat #(wrap-line % width)
                                        (str/split s #"\n" -1)))))

(defn- apply-overflow
  "Apply the column's overflow policy to a prepared string.
   `:wrap` is handled later during row expansion, so here it's a no-op."
  [s col]
  (let [width    (:width col)
        overflow (or (:overflow col) :ellipsis)]
    (if (and width (> (count s) width) (not= :wrap overflow))
      (case overflow
        :clip     (subs s 0 width)
        :ellipsis (elide s width (:ellipsis col)))
      s)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data Preparation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- prepare-cell
  "Preprocess a single value to its final string representation."
  [v col nil-value]
  (let [s (if (nil? v)
            (str nil-value)
            ((:format-fn col) v))]
    (apply-overflow s col)))

(defn- expand-wrapped-row
  "Expand one prepared logical row into physical rows. Non-wrapping
   columns show their value only on the first physical row."
  [row wrap-flags widths]
  (let [lines-per-cell (mapv (fn [cell wraps? w]
                               (if (and wraps? (string? cell))
                                 (word-wrap cell w)
                                 [cell]))
                             row wrap-flags widths)
        line-count     (apply max 1 (map count lines-per-cell))]
    (vec (for [i (range line-count)]
           (mapv #(or (nth % i nil) "") lines-per-cell)))))

(defn- prepare-rows
  "Extract and preprocess all rows into physical row sublists."
  [rows columns nil-value]
  (let [prepared (mapv (fn [row]
                         (mapv #(prepare-cell (extract-value row %) % nil-value)
                               columns))
                       rows)]
    (if (some #(= :wrap (:overflow %)) columns)
      (let [wrap-flags (mapv #(= :wrap (:overflow %)) columns)
            widths     (mapv :width columns)]
        (vec (mapcat #(expand-wrapped-row % wrap-flags widths) prepared)))
      prepared)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Directive Construction
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- str-directive
  "Build a `:str` DSL directive from a base options map, for a given
   alignment. Center uses `:justify` because `:str` has no center mode."
  [width align extra-opts]
  (let [opts (merge {:width width} extra-opts)]
    (case align
      :left   [:str opts]
      :right  [:str (assoc opts :pad :left)]
      :center [:justify {:width width :pad-before true :pad-after true}
               (if (seq extra-opts) [:str extra-opts] :str)])))

(defn- cell-directive
  "DSL directive for a data cell. Values are already strings at this
   point, so we only need to pad to column width with the requested
   alignment and optional case conversion."
  [{:keys [width align case] :or {align :left}}]
  (str-directive width align (when case {:case case})))

(defn- header-directive
  "DSL directive for a header cell."
  [col header-case]
  (let [align     (or (:title-align col) (:align col) :left)
        extras    (when header-case {:case header-case})]
    (str-directive (:width col) align extras)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Row Assembly
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- row-elements
  "Build DSL elements for one row: optional outer borders, cell
   directives interleaved with column separators, and a trailing
   newline."
  [directives style]
  (let [{:keys [v sep outer?]} style
        cell-sep (if outer? (str " " v " ") (or sep "  "))]
    (vec (concat
           (when outer? [(str v " ")])
           (interpose cell-sep directives)
           (when outer? [(str " " v)])
           [:nl]))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Footer Computation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- aggregate
  "Apply an aggregate function to a sequence of raw column values.
   Built-ins: :sum :avg :min :max :count. A function is called
   directly on the value seq."
  [f values]
  (let [nums (filter number? values)]
    (case f
      :sum   (reduce + 0 nums)
      :avg   (if (seq nums) (/ (reduce + 0.0 nums) (count nums)) 0)
      :min   (when (seq nums) (apply min nums))
      :max   (when (seq nums) (apply max nums))
      :count (count values)
      (when (fn? f) (f values)))))

(defn- compute-footer-values
  "Compute one row of raw footer values from columns + footer opts.
   Precedence per column: explicit :values entry > :fns entry >
   :label on the first column > empty."
  [columns rows {:keys [label values fns]}]
  (mapv (fn [i col]
          (let [k (:key col)]
            (cond
              (contains? values k) (get values k)
              (contains? fns k)    (aggregate (get fns k) (map #(extract-value % col) rows))
              (zero? i)            (or label "")
              :else                "")))
        (range)
        columns))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sections
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; Each section returns {:dsl [...] :args [...]} or nil. The full table
;; DSL is the concatenation of every non-nil section's contribution.

(defn- column-widths [columns] (mapv :width columns))
(defn- column-aligns [columns] (mapv #(or (:align %) :left) columns))

(defn- top-rule-section
  "The topmost border rule, when the style has one."
  [columns style]
  (when (:top? style)
    {:dsl  [(rule-string style :top (column-widths columns) (column-aligns columns)) :nl]
     :args []}))

(defn- header-section
  "The header row and the rule under it (if enabled)."
  [columns style {:keys [header header-rule header-case]
                  :or   {header true header-rule true header-case :capitalize}}]
  (when header
    (let [widths (column-widths columns)
          aligns (column-aligns columns)]
      {:dsl  (vec (concat
                    (row-elements (mapv #(header-directive % header-case) columns) style)
                    (when (and header-rule (:mid? style))
                      [(rule-string style :mid widths aligns) :nl])))
       :args (mapv :title columns)})))

(defn- body-section
  "The `:each {:from :sublists}` element that iterates prepared rows.
   `:row-rules true` injects a mid rule between rows using `~:^` so
   that the rule does not appear after the final row."
  [columns prepared-rows style {:keys [row-rules] :or {row-rules false}}]
  (let [widths    (column-widths columns)
        aligns    (column-aligns columns)
        wrapped?  (some #(= :wrap (:overflow %)) columns)
        row-body  (row-elements (mapv cell-directive columns) style)
        each-body (if (and row-rules (:mid? style) (not wrapped?))
                    (into row-body
                          [[:stop {:outer true}]
                           (rule-string style :mid widths aligns)
                           :nl])
                    row-body)]
    {:dsl  [(vec (into [:each {:from :sublists}] each-body))]
     :args [prepared-rows]}))

(defn- footer-section
  "The footer row, preceded by a rule if the style has a mid rule and
   the footer opts opt in via `:rule` (default true)."
  [columns rows style {:keys [footer nil-value] :or {nil-value ""}}]
  (when (map? footer)
    (let [widths   (column-widths columns)
          aligns   (column-aligns columns)
          raw-vals (compute-footer-values columns rows footer)
          prepared (mapv #(prepare-cell %1 %2 nil-value) raw-vals columns)
          rule?    (and (:mid? style) (get footer :rule true))]
      {:dsl  (vec (concat
                    (when rule? [(rule-string style :mid widths aligns) :nl])
                    (row-elements (mapv cell-directive columns) style)))
       :args prepared})))

(defn- bottom-rule-section
  "The bottommost border rule, when the style has one."
  [columns style]
  (when (:bottom? style)
    {:dsl  [(rule-string style :bottom (column-widths columns) (column-aligns columns))]
     :args []}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Composition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- assemble
  "Concatenate non-nil sections into a single {:dsl ... :args ...} map."
  [sections]
  (let [live (remove nil? sections)]
    {:dsl  (vec (mapcat :dsl  live))
     :args (vec (mapcat :args live))}))

(defn- compose-table
  "Compose the full table DSL and argument list for already-normalized
   columns and raw row data. Returns {:dsl body-vector :args argv}."
  [columns rows opts]
  (let [nil-value (get opts :nil-value "")
        style     (resolve-style (:style opts))
        columns   (-> columns
                      with-format-fns
                      (compute-widths rows nil-value))
        prepared  (prepare-rows rows columns nil-value)]
    (assemble
      [(top-rule-section    columns style)
       (header-section      columns style opts)
       (body-section        columns prepared style opts)
       (footer-section      columns rows style opts)
       (bottom-rule-section columns style)])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- parse-table-spec
  "Extract [opts columns] from a [:table opts? & cols] form. Columns
   default to an empty vector (meaning: infer from data)."
  [spec]
  (when-not (and (vector? spec) (= :table (first spec)))
    (throw (ex-info "Expected [:table ...] spec" {:spec spec})))
  (let [[_ & body] spec
        [opts cols] (if (and (seq body) (map? (first body)))
                      [(first body) (rest body)]
                      [{} body])]
    [opts (vec cols)]))

(defn- resolve-columns
  "Produce normalized column maps from the spec body and rows."
  [col-forms rows defaults]
  (-> (if (seq col-forms)
        (mapv normalize-column col-forms)
        (or (infer-columns rows)
            (throw (ex-info "Cannot infer columns from empty data"
                            {:reason :no-columns}))))
      (merge-defaults defaults)))

(defn table-dsl
  "Build the table DSL and argument list without rendering.

  Returns a map with `:dsl` (the clj-format DSL body vector) and
  `:args` (the argument list to feed clj-format). Useful for
  inspecting the generated DSL or calling clj-format directly.

  `spec` is a `[:table opts? & cols]` form matching the Hiccup
  convention; `rows` is a seq of maps or vectors."
  [spec rows]
  (let [[opts col-forms] (parse-table-spec spec)
        columns          (resolve-columns col-forms rows (:defaults opts))]
    (compose-table columns rows opts)))

(defn render-to
  "Render a [:table ...] spec to a target. Internal entry point used
  by clj-format.core/clj-format.

  `target` is the normalized output target: nil, true, or a Writer.
  `spec` is a [:table ...] form. `rows` is the data sequence."
  [target spec rows]
  (let [{:keys [dsl args]} (table-dsl spec rows)
        fmt-str            (compiler/compile-format dsl)]
    (apply pp/cl-format target fmt-str args)))

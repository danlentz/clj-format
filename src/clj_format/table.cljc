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

  This namespace exposes two low-level entry points:
    - table-dsl — build the DSL body + argument list for inspection
    - render-to — internal dispatch target used by clj-format"
  (:require [clojure.string           :as str]
            #?(:clj  [clojure.pprint :as pp]
               :cljs [cljs.pprint    :as pp])
            [clj-format.compiler     :as compiler]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Border Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private +border-styles+
  "Character sets for each named border style."
  {:ascii   {:tl "+" :tr "+" :bl "+" :br "+"
             :tt "+" :bt "+" :lt "+" :rt "+" :cross "+"
             :h "-" :v "|"}
   :unicode {:tl "┌" :tr "┐" :bl "└" :br "┘"
             :tt "┬" :bt "┴" :lt "├" :rt "┤" :cross "┼"
             :h "─" :v "│"}
   :rounded {:tl "╭" :tr "╮" :bl "╰" :br "╯"
             :tt "┬" :bt "┴" :lt "├" :rt "┤" :cross "┼"
             :h "─" :v "│"}
   :heavy   {:tl "┏" :tr "┓" :bl "┗" :br "┛"
             :tt "┳" :bt "┻" :lt "┣" :rt "┫" :cross "╋"
             :h "━" :v "┃"}
   :double  {:tl "╔" :tr "╗" :bl "╚" :br "╝"
             :tt "╦" :bt "╩" :lt "╠" :rt "╣" :cross "╬"
             :h "═" :v "║"}
   :markdown {:h "-" :v "|" :markdown true}
   :org     {:tl "|" :tr "|" :bl "|" :br "|"
             :tt "+" :bt "+" :lt "|" :rt "|" :cross "+"
             :h "-" :v "|"}
   :simple  {:h "-" :sep "  " :borderless true}
   :none    {:sep "  " :borderless true :no-rules true}})

(defn- resolve-style
  "Resolve a style keyword or map into a style configuration."
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


(defn- rule-string
  "Build a horizontal rule line for the given position and column widths.
   position is :top, :mid, or :bottom."
  [style position widths]
  (let [{:keys [h tl tr tt bl br bt lt rt cross sep borderless]} style
        dash (or h "-")]
    (if borderless
      ;; Borderless: segments separated by spaces
      (str/join (or sep "  ") (map #(apply str (repeat % dash)) widths))
      ;; Bordered: junctions + dash segments (width + 2 for cell padding)
      (let [[left right junc]
            (case position
              :top    [(or tl "+") (or tr "+") (or tt "+")]
              :mid    [(or lt "+") (or rt "+") (or cross "+")]
              :bottom [(or bl "+") (or br "+") (or bt "+")])]
        (str left
             (str/join junc (map #(apply str (repeat (+ % 2) dash)) widths))
             right)))))

(defn- markdown-rule-string
  "Build a markdown header rule with alignment markers."
  [widths aligns]
  (let [segments (map (fn [w align]
                        (let [inner (- w 2)]
                          (case (or align :left)
                            :left   (str ":" (apply str (repeat (max inner 0) "-")) "-")
                            :right  (str "-" (apply str (repeat (max inner 0) "-")) ":")
                            :center (str ":" (apply str (repeat (max inner 0) "-")) ":"))))
                      widths aligns)]
    (str "| " (str/join " | " segments) " |")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Column Normalization
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- humanize-key
  "Convert a keyword to a human-readable title.
   :created-at => \"Created At\", :name => \"Name\".
   Returns nil for non-keyword keys (computed columns)."
  [k]
  (when (keyword? k)
    (->> (str/split (name k) #"[-_]")
         (map str/capitalize)
         (str/join " "))))

(defn- normalize-column
  "Expand a column form into a fully normalized column map.

   Accepts these shapes, in order of terseness:
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data Extraction and Value Formatting
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- extract-value
  "Extract a raw value from a row for a given column."
  [row col]
  (let [k (:key col)]
    (cond
      (fn? k)          (k row)
      (map? row)       (get row k)
      (sequential? row) (when (number? k) (nth row k nil))
      :else            nil)))

(defn- format-value
  "Format a value according to a column's format spec.
   Used for width measurement and preprocessing."
  [v col]
  (let [fmt (:format col :str)]
    (cond
      (nil? v)       ""
      (fn? fmt)      (fmt v)
      (= fmt :str)  (str v)
      (= fmt :pr)   (pr-str v)
      (keyword? fmt) (pp/cl-format nil (compiler/compile-format [fmt]) v)
      (vector? fmt)  (pp/cl-format nil (compiler/compile-format fmt) v)
      :else          (str v))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Width Computation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- measure-width
  "Measure the display width of a value for a column."
  [v col nil-value]
  (if (nil? v)
    (count (str nil-value))
    (count (format-value v col))))

(defn- compute-width
  "Compute the auto-sized width for a single column."
  [col rows nil-value]
  (let [title-w  (count (:title col))
        data-ws  (map #(measure-width (extract-value % col) col nil-value) rows)
        max-data (if (seq data-ws) (apply max data-ws) 0)
        natural  (max title-w max-data)
        min-w    (or (:min-width col) title-w)
        max-w    (:max-width col)]
    (cond-> (max natural min-w)
      max-w (min max-w))))

(defn- compute-widths
  "Assign widths to all columns, auto-sizing where not explicitly set."
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
  "Truncate string s to width, appending ellipsis if truncated."
  [s width ellipsis]
  (let [ell  (or ellipsis "...")
        elen (count ell)]
    (cond
      (<= (count s) width) s
      (<= width elen)      (subs s 0 width)
      :else                (str (subs s 0 (- width elen)) ell))))

(defn- wrap-line
  "Fit a single line of text within width.

   If the line already fits, it is preserved verbatim — interior
   whitespace is not collapsed. This matters for pre-formatted
   content like ASCII art banners and nested table renderings,
   where every space is load-bearing.

   If the line exceeds width, greedy word-wrap takes over: break at
   word boundaries, splitting words longer than width at the width
   boundary."
  [s width]
  (cond
    (empty? s)
    [""]

    (<= (count s) width)
    [s]

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

   Preserves explicit newlines as hard breaks; wraps the text
   between them at word boundaries. Long words are broken at the
   width boundary. Returns a vector of line strings."
  [s width]
  (cond
    (or (nil? s) (= "" s)) [""]
    (<= width 0)           [s]
    :else                  (vec (mapcat #(wrap-line % width)
                                        (str/split s #"\n" -1)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rendering Mode Resolution
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private +str-formats+
  "Formats that produce strings and support elision."
  #{:str :pr})

(def ^:private +width-directives+
  "Directives that accept a :width parameter."
  #{:str :pr :int :bin :oct :hex :radix :float :exp :gfloat :money})

(def ^:private +right-natural+
  "Directives that naturally right-justify with :width."
  #{:int :bin :oct :hex :radix :float :exp :gfloat :money})

(defn- string-format?
  "True if the column's format is string-based."
  [col]
  (let [fmt (:format col :str)]
    (or (fn? fmt) (+str-formats+ fmt))))

(defn- wrap-mode?
  "True when any column uses :overflow :wrap.
   In wrap mode, every column is preprocessed to strings so that
   continuation rows (with empty values for non-wrap columns) can
   be rendered uniformly with :str directives."
  [columns]
  (boolean (some #(= :wrap (:overflow %)) columns)))

(defn- needs-preprocess?
  "True if a column needs values preprocessed to strings."
  [col rows]
  (or (fn? (:format col))
      (and (not (string-format? col))
           (some #(nil? (extract-value % col)) rows))))

(defn- resolve-modes
  "Tag each column with :mode (:direct or :preprocessed).
   Wrap mode forces every column to :preprocessed."
  [columns rows]
  (let [force? (wrap-mode? columns)]
    (mapv (fn [col]
            (assoc col :mode (if (or force? (needs-preprocess? col rows))
                               :preprocessed
                               :direct)))
          columns)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Cell Directive Construction
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- parse-format-spec
  "Parse a column :format into [keyword opts-map children-seq].
   Returns nil for function formats."
  [fmt]
  (cond
    (keyword? fmt) [fmt {} nil]
    (vector? fmt)  (let [[kw & body] fmt
                         [opts children] (if (map? (first body))
                                           [(first body) (rest body)]
                                           [{} body])]
                     [kw opts (seq children)])
    :else          nil))

(defn- build-justify
  "Wrap a DSL element in a :justify directive for alignment."
  [width align inner]
  (let [opts (cond-> {:width width}
               (= align :left)   (assoc :pad-after true)
               (= align :center) (assoc :pad-before true :pad-after true))]
    [:justify opts inner]))

(defn- cell-directive
  "Build the DSL directive for a data cell."
  [{:keys [width align format case mode] :or {align :left format :str}}]
  (if (= mode :preprocessed)
    ;; Preprocessed: value is a string, use :str
    (cond
      (= align :left)   [:str (cond-> {:width width} case (assoc :case case))]
      (= align :right)  [:str (cond-> {:width width :pad :left} case (assoc :case case))]
      (= align :center) (build-justify width align
                                       (if case [:str {:case case}] :str))
      :else              [:str {:width width}])

    ;; Direct mode: use typed directives
    (let [parsed (parse-format-spec format)
          [fmt-kw fmt-opts children] (or parsed [:str {} nil])
          can-merge?     (and (+width-directives+ fmt-kw) (nil? children))
          str-fmt?       (+str-formats+ fmt-kw)
          right-natural? (+right-natural+ fmt-kw)
          ;; Can we merge width directly for this alignment?
          merge-align?   (or (and str-fmt? (#{:left :right} align))
                             (and right-natural? (= align :right)))]
      (if (and can-merge? merge-align?)
        ;; Direct merge: width + alignment into directive opts
        [fmt-kw (cond-> (merge fmt-opts {:width width})
                  case                              (assoc :case case)
                  (and str-fmt? (= align :right))   (assoc :pad :left))]
        ;; Justify wrapper for non-natural alignment or compound formats
        (let [inner (cond
                      (and (nil? children) (empty? fmt-opts) (not case))
                      fmt-kw

                      (nil? children)
                      [fmt-kw (cond-> fmt-opts case (assoc :case case))]

                      :else
                      (let [form (vec (concat [fmt-kw]
                                              (when (seq fmt-opts) [fmt-opts])
                                              children))]
                        form))]
          (build-justify width align inner))))))

(defn- header-directive
  "Build the DSL directive for a header cell."
  [col header-case]
  (let [{:keys [width align title-align]} col
        align (or title-align align :left)
        opts  (cond-> {:width width}
                header-case (assoc :case header-case))]
    (cond
      (= align :left)   [:str opts]
      (= align :right)  [:str (assoc opts :pad :left)]
      (= align :center) (build-justify width align
                                       (if header-case
                                         [:str {:case header-case}]
                                         :str))
      :else              [:str opts])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Row Assembly
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- row-elements
  "Build the DSL elements for one row: borders + directives + newline."
  [directives style]
  (let [{:keys [v sep borderless]} style
        bordered? (and (some? v) (not borderless))
        cell-sep  (if bordered? (str " " v " ") (or sep "  "))]
    (vec (concat
           (when bordered? [(str v " ")])
           (interpose cell-sep directives)
           (when bordered? [(str " " v)])
           [:nl]))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Aggregation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- aggregate
  "Apply an aggregate function to a sequence of values."
  [f values]
  (let [nums (filter number? values)]
    (case f
      :sum   (reduce + 0 nums)
      :avg   (if (seq nums) (/ (reduce + 0.0 nums) (count nums)) 0)
      :min   (if (seq nums) (apply min nums) nil)
      :max   (if (seq nums) (apply max nums) nil)
      :count (count values)
      (if (fn? f) (f values) nil))))

(defn- compute-footer-values
  "Compute footer values from data rows."
  [columns rows footer-opts]
  (let [{:keys [label values fns]} footer-opts]
    (mapv (fn [i col]
            (let [k (:key col)]
              (cond
                ;; First column gets the label
                (and (zero? i) label (not (get values k)) (not (get fns k)))
                label

                ;; Explicit static value
                (contains? values k)
                (get values k)

                ;; Aggregate function
                (contains? fns k)
                (aggregate (get fns k) (map #(extract-value % col) rows))

                ;; First column label fallback
                (and (zero? i) label)
                label

                :else "")))
          (range) columns)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data Preparation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- apply-overflow
  "Apply the column's overflow policy to a prepared string value.
   :wrap is handled later during row expansion, so here it is a no-op."
  [s col]
  (let [width    (:width col)
        overflow (or (:overflow col) :ellipsis)]
    (if (and width (> (count s) width) (not= :wrap overflow))
      (case overflow
        :clip     (subs s 0 width)
        :ellipsis (elide s width (:ellipsis col)))
      s)))

(defn- prepare-cell
  "Prepare a single cell value for rendering."
  [v col nil-value]
  (let [v (if (nil? v) nil-value v)]
    (if (= :preprocessed (:mode col))
      (let [s (if (= v nil-value)
                (str nil-value)
                (format-value v col))]
        (apply-overflow s col))
      (if (and (string-format? col) (:width col) (string? v))
        (apply-overflow v col)
        v))))

(defn- expand-wrapped-row
  "Expand one logical row into physical rows when any column wraps.
   Non-wrapping columns show their value only on the first physical row."
  [row wrap-flags widths]
  (let [lines-per-cell (mapv (fn [cell wraps? w]
                               (if (and wraps? (string? cell))
                                 (word-wrap cell w)
                                 [cell]))
                             row wrap-flags widths)
        line-count     (apply max 1 (map count lines-per-cell))]
    (vec (for [i (range line-count)]
           (mapv #(or (nth % i nil) "") lines-per-cell)))))

(defn- prepare-data
  "Extract and prepare all row data as sublists.
   When any column wraps, expands logical rows into physical rows."
  [rows columns nil-value]
  (let [prepared (mapv (fn [row]
                         (mapv #(prepare-cell (extract-value row %) % nil-value)
                               columns))
                       rows)]
    (if (wrap-mode? columns)
      (let [wrap-flags (mapv #(= :wrap (:overflow %)) columns)
            widths     (mapv :width columns)]
        (vec (mapcat #(expand-wrapped-row % wrap-flags widths) prepared)))
      prepared)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Table DSL Composition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- compose-table
  "Compose the full table DSL and argument list.
   Returns {:dsl body-vector :args argument-vector}."
  [columns rows opts]
  (let [{:keys [style header header-rule header-case top-rule bottom-rule
                row-rules footer nil-value]
         :or   {header true header-rule true header-case :capitalize
                top-rule true bottom-rule true row-rules false
                nil-value ""}} opts
        style       (resolve-style style)
        columns     (compute-widths columns rows nil-value)
        columns     (resolve-modes columns rows)
        widths      (mapv :width columns)
        borderless? (:borderless style)
        no-rules?   (:no-rules style)
        markdown?   (:markdown style)

        ;; Build directives
        data-dirs   (mapv cell-directive columns)
        header-dirs (mapv #(header-directive % header-case) columns)

        ;; Build row element patterns
        data-row    (row-elements data-dirs style)
        header-row  (row-elements header-dirs style)

        ;; Build rule strings
        top-rule-str    (when (and top-rule (not no-rules?) (not borderless?)
                                  (not markdown?))
                          (rule-string style :top widths))
        header-rule-str (when (and header header-rule (not no-rules?))
                          (if markdown?
                            (markdown-rule-string widths (mapv #(or (:align %) :left) columns))
                            (rule-string style :mid widths)))
        bottom-rule-str (when (and bottom-rule (not no-rules?) (not borderless?)
                                   (not markdown?))
                          (rule-string style :bottom widths))

        ;; Row separator elements for :row-rules.
        ;; Uses [:stop {:outer true}] (~:^) to guard the separator,
        ;; because ~^ only checks the current sublist's remaining args
        ;; while ~:^ checks if more sublists remain in the outer list.
        ;; Disabled in wrap mode: each physical row iteration cannot
        ;; distinguish "last physical row of a logical group" from
        ;; "middle of a group", so rules would appear between
        ;; continuation rows of the same logical row.
        row-sep-elems (when (and row-rules (not no-rules?)
                                 (not (wrap-mode? columns)))
                        [[:stop {:outer true}]
                         (rule-string style :mid widths) :nl])

        ;; Footer
        footer-opts (when (and footer (map? footer))
                      footer)
        footer-vals (when footer-opts
                      (compute-footer-values columns rows footer-opts))
        footer-dirs (when footer-vals
                      (mapv cell-directive columns))
        footer-row  (when footer-dirs
                      (row-elements footer-dirs style))
        footer-rule-str (when (and footer-vals (not no-rules?)
                                   (get footer-opts :rule true))
                          (if borderless?
                            (rule-string style :mid widths)
                            (rule-string style :mid widths)))

        ;; Prepare data
        data        (prepare-data rows columns nil-value)
        header-titles (mapv :title columns)

        ;; Build :each element
        each-opts   {:from :sublists}
        each-body   (if row-sep-elems
                      (concat data-row row-sep-elems)
                      data-row)
        each-elem   (vec (concat [:each each-opts] each-body))

        ;; Compose full DSL body
        dsl-parts   (concat
                      (when top-rule-str    [top-rule-str :nl])
                      (when header          header-row)
                      (when header-rule-str [header-rule-str :nl])
                      [each-elem]
                      (when footer-rule-str [footer-rule-str :nl])
                      (when footer-row      footer-row)
                      (when bottom-rule-str [bottom-rule-str]))
        dsl         (vec dsl-parts)

        ;; Compose argument list
        args        (vec (concat
                           (when header header-titles)
                           [data]
                           (when footer-vals footer-vals)))]
    {:dsl dsl :args args}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- parse-table-spec
  "Extract [opts columns] from a [:table opts? & cols] form.
   Columns default to an empty vector (infer from data)."
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
  (let [cols (if (seq col-forms)
               (mapv normalize-column col-forms)
               (or (infer-columns rows)
                   (throw (ex-info "Cannot infer columns from empty data"
                                   {:reason :no-columns}))))]
    (if (seq defaults)
      (mapv #(merge defaults %) cols)
      cols)))

(defn table-dsl
  "Build the table DSL and argument list without rendering.

  Returns a map with :dsl (the clj-format DSL body vector) and :args
  (the argument list). Useful for inspecting the generated DSL or
  calling clj-format directly.

  spec is a [:table opts? & cols] form matching the Hiccup convention:
     [:table]                          ;; infer everything from rows
     [:table :name :age]               ;; bare-keyword columns
     [:table {:style :unicode}
       [:col :name {:width 20}]
       [:col :age  {:align :right}]]

  rows is a seq of maps or vectors."
  [spec rows]
  (let [[opts col-forms] (parse-table-spec spec)
        columns          (resolve-columns col-forms rows (:defaults opts))]
    (compose-table columns rows opts)))

(defn render-to
  "Render a [:table ...] spec to a target. Internal entry point used
  by clj-format.core/clj-format.

  target is the normalized output target: nil, true, or a Writer.
  spec is a [:table ...] form. rows is the data sequence."
  [target spec rows]
  (let [{:keys [dsl args]} (table-dsl spec rows)
        fmt-str            (compiler/compile-format dsl)]
    (apply pp/cl-format target fmt-str args)))

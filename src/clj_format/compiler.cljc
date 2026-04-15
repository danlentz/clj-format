(ns clj-format.compiler
  "Compiles the clj-format s-expression DSL into cl-format format strings.

  The inverse of clj-format.parser/parse-format. Takes a DSL body vector
  and produces the equivalent cl-format string.

  Examples:
    (compile-format :str)                       => \"~A\"
    (compile-format [:str])                      => \"~A\"
    (compile-format [\"Hello \" :str \"!\"])      => \"Hello ~A!\"
    (compile-format [:cardinal \" file\" [:plural {:rewind true}]])
                                                  => \"~R file~:P\"
    (compile-format [[:each {:sep \", \"} :str]]) => \"~{~A~^, ~}\"
    (compile-format [[:if \"yes\" \"no\"]])       => \"~:[no~;yes~]\""
  (:require [clojure.string :as str]
            [clj-format.directives :as d]
            [clj-format.errors :as err]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parameter and Flag Serialization
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- format-param
  "Format a single positional param value for cl-format."
  [v]
  (case v
    nil ""
    :V  "V"
    :#  "#"
    (if (char? v) (str "'" v) (str v))))

(defn- format-params
  "Build the comma-separated param string, trimming trailing nils."
  [param-names opts]
  (let [positional (mapv #(get opts %) param-names)
        last-idx   (reduce (fn [acc i] (if (some? (nth positional i)) i acc))
                           -1 (range (count positional)))]
    (if (neg? last-idx)
      ""
      (->> (subvec positional 0 (inc last-idx))
           (map format-param)
           (str/join ",")))))

(defn- flag-active?
  "Check whether a semantic flag option is active in opts.
   rule is [opt-key opt-val] from the directive's flag-rules."
  [[opt-key opt-val] opts]
  (let [v (get opts opt-key)]
    (if (true? opt-val) v (= v opt-val))))

(defn- compile-flags
  "Determine colon and at flags from semantic opts using flag rules."
  [{:keys [colon at]} opts]
  (str (when (and colon (flag-active? colon opts)) ":")
       (when (and at (flag-active? at opts)) "@")))

(defn- compile-char-flags
  "Serialize ~C modifiers while remaining compatible with the older
   {:format :name|:readable} option shape."
  [opts]
  (str (when (or (:name opts) (= :name (:format opts))) ":")
       (when (or (:readable opts) (= :readable (:format opts))) "@")))

(defn- format-raw-flags
  "Format a raw [colon? at?] flag tuple."
  [[colon? at?]]
  (str (when colon? ":")
       (when at? "@")))

(defn- escape-tildes [s]
  (str/replace s "~" "~~"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- invalid-dsl
  "Throw a structured DSL compilation error."
  [message data]
  (throw (err/compile-error message data)))

(defn- validate-directive-keyword
  "Ensure `kw` names a known DSL directive."
  [kw elem]
  (when-not (d/known-directive-keyword? kw)
    (invalid-dsl "Unknown DSL directive keyword"
                 {:kind :unknown-directive
                  :directive kw
                  :element elem})))

(defn- validate-element-type
  "Ensure a DSL element is one of the supported types."
  [elem]
  (when-not (or (string? elem) (keyword? elem) (vector? elem))
    (invalid-dsl "DSL elements must be strings, keywords, or vectors"
                 {:kind :invalid-element
                  :element elem})))

(defn- validate-clause-type
  "Ensure a compound clause is one of the supported types."
  [clause]
  (when-not (or (nil? clause) (string? clause) (keyword? clause) (vector? clause))
    (invalid-dsl "Directive clauses must be nil, strings, keywords, or vectors"
                 {:kind :invalid-clause
                  :clause clause})))

(defn- validate-child-count
  "Ensure a compound directive receives the expected number of children."
  [kw children expected elem]
  (when-not (= expected (count children))
    (invalid-dsl "Directive received an invalid number of child forms"
                 {:kind :invalid-child-count
                  :directive kw
                  :expected expected
                  :actual (count children)
                  :children children
                  :element elem})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Case Conversion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- wrap-case
  "Wrap a compiled format string fragment in case conversion."
  [compiled-str mode]
  (str (d/+case-open+ mode) compiled-str "~)"))

(defn- maybe-wrap-case
  "If opts contains :case, wrap result in case conversion and return
   opts without :case. Otherwise return result unchanged."
  [opts compile-fn]
  (let [case-mode (:case opts)
        result    (compile-fn (dissoc opts :case))]
    (if case-mode (wrap-case result case-mode) result)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Element Compilation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare compile-element)

(defn- compile-body
  "Compile a sequence of DSL elements into a format string."
  [elements]
  (apply str
         (map (fn [elem]
                (validate-element-type elem)
                (compile-element elem))
              elements)))

(defn- compile-clause
  "Compile a clause value (from :if, :choose, :justify).
   nil -> empty, string -> escaped literal, keyword -> directive,
   vector -> directive or body.

   Returns a compiled string in the common case, OR a map of the
   form `{:separator-opts {...} :body \"...\"}` when the clause is
   a `[:clause opts? & body]` wrapper carrying clause-local
   separator parameters. `join-clauses` understands both shapes;
   callers that do not support clause-local separators must go
   through `compile-plain-clause`, which rejects the map form."
  [clause]
  (validate-clause-type clause)
  (cond
    (nil? clause)     ""
    (string? clause)  (escape-tildes clause)
    (keyword? clause) (compile-element clause)
    (vector? clause)  (cond
                       (= :clause (first clause))
                       (let [[_ maybe-opts & body] clause
                             [opts children] (if (map? maybe-opts)
                                               [maybe-opts body]
                                               [{} (cons maybe-opts body)])]
                         (when (empty? children)
                           (invalid-dsl "Clause wrapper must contain body elements"
                                        {:kind :invalid-clause
                                         :clause clause}))
                         {:separator-opts opts
                          :body (compile-body children)})

                       (keyword? (first clause))
                       (compile-element clause)

                       :else
                       (compile-body clause))
    :else             (invalid-dsl "Unreachable invalid clause"
                                   {:kind :invalid-clause
                                    :clause clause})))

(defn- compile-plain-clause
  "Compile a clause for constructs that do not support clause-local separator
   options. Rejects [:clause ...] wrappers outside justification/logical blocks."
  [clause]
  (let [compiled (compile-clause clause)]
    (if (map? compiled)
      (invalid-dsl "Clause-local separator options are only valid in :justify and :logical-block"
                   {:kind :invalid-clause
                    :clause clause})
      compiled)))

(defn- separator-fragment
  "Build a ~; clause separator with optional clause-local parameters
   and flags (used inside :justify and :logical-block)."
  [{:keys [width pad-step min-pad fill pad-before pad-after]}]
  (str "~"
       (format-params [:width :pad-step :min-pad :fill]
                      {:width width :pad-step pad-step
                       :min-pad min-pad :fill fill})
       (format-raw-flags [pad-before pad-after])
       ";"))

(defn- normalize-clause
  "Compile a clause and lift it to the `{:separator-opts :body}` shape
   that `join-clauses` iterates over. Plain clauses get an empty
   separator-opts map; `[:clause ...]` wrappers carry their own."
  [clause]
  (let [compiled (compile-clause clause)]
    (if (map? compiled)
      compiled
      {:separator-opts {} :body compiled})))

(defn- join-clauses
  "Compile clauses and join with ~; separators. The first clause has
   no leading separator; every subsequent clause is preceded by one
   built from its own clause-local separator options."
  [clauses]
  (let [[head & tail] (map normalize-clause clauses)]
    (str (:body head)
         (apply str
                (for [c tail]
                  (str (separator-fragment (:separator-opts c))
                       (:body c)))))))

(defn- compile-simple
  "Compile a simple (non-compound) directive using shared config."
  [kw opts]
  (let [{:keys [char params flags]} (d/directive-config kw)]
    (maybe-wrap-case opts
      (fn [opts]
        (str "~"
             (format-params params opts)
             (if (= kw :char)
               (compile-char-flags opts)
               (compile-flags flags opts))
             char)))))

(defn- compile-special
  "Compile special-dispatch directives (:cardinal, :ordinal, etc.)."
  [kw opts]
  (maybe-wrap-case opts
    (fn [opts]
      (let [{:keys [char flags params]} (d/special->raw kw opts)]
        (str "~" (format-params params opts) (format-raw-flags flags) char)))))

(defn- compile-each
  "Compile [:each opts? & body] -> ~flags max{ body ~close}"
  [opts body-elements]
  (maybe-wrap-case opts
    (fn [{:keys [from max min sep]}]
      (let [open-flags (case from
                         :rest "@", :sublists ":", :rest-sublists ":@", nil "")
            body-str   (compile-body body-elements)
            body-str   (if sep (str body-str "~^" (escape-tildes sep)) body-str)]
        (str "~" (some-> max str) open-flags "{"
             body-str
             (if (= 1 min) "~:}" "~}"))))))

(defn- compile-if
  "Compile [:if opts? then else] -> ~:[false~;true~]"
  [opts then-clause else-clause]
  (maybe-wrap-case opts
    (fn [_]
      (str "~:[" (compile-plain-clause else-clause) "~;" (compile-plain-clause then-clause) "~]"))))

(defn- compile-when-cond
  "Compile [:when opts? & body] -> ~@[body~]"
  [opts body-elements]
  (maybe-wrap-case opts
    (fn [_] (str "~@[" (compile-body body-elements) "~]"))))

(defn- compile-choose
  "Compile [:choose opts? & clauses] -> ~selector[c0~;c1~;...~:;default~]"
  [opts clauses]
  (maybe-wrap-case opts
    (fn [{:keys [selector default]}]
      (let [joined (str/join "~;" (map compile-plain-clause clauses))]
        (str "~" (some-> selector format-param)
             "[" (if default (str joined "~:;" (compile-plain-clause default)) joined)
             "~]")))))

(defn- compile-justify
  "Compile [:justify opts? & clauses] -> ~params flags<c0~;c1~;...~>"
  [opts clauses]
  (str "~" (format-params (:params (d/compound-directive-config :justify)) opts)
       (format-raw-flags [(:pad-before opts) (:pad-after opts)])
       "<" (join-clauses clauses) "~>"))

(defn- compile-logical-block
  "Compile [:logical-block opts? & clauses] -> ~flags<c0~;...~:>"
  [opts clauses]
  (str "~" (format-raw-flags [(:colon opts) nil])
       "<" (join-clauses clauses) "~:>"))

(defn- interpret-directive-form
  "Interpret a directive vector into keyword, opts, and children.
   If the second element is a map, it's options; everything after is children.
   Returns [keyword opts children]."
  [v]
  (let [[kw & body] v
        raw-opts        (first body)
        _               (validate-directive-keyword kw v)
        [opts children] (if (map? raw-opts)
                          [raw-opts (vec (rest body))]
                          [{} (vec body)])]
    [kw opts children]))

(defn- compile-element
  "Compile a single DSL element into a format string fragment."
  [elem]
  (cond
    (string? elem)
    (escape-tildes elem)

    (keyword? elem)
    (do
      (validate-directive-keyword elem elem)
      (if (d/+special-keywords+ elem)
        (compile-special elem {})
        (str "~" (:char (d/directive-config elem)))))

    (vector? elem)
    (let [[kw opts children] (interpret-directive-form elem)]
      (case kw
        :each          (compile-each opts children)
        :if            (do
                         (validate-child-count kw children 2 elem)
                         (compile-if opts (first children) (second children)))
        :when          (compile-when-cond opts children)
        :choose        (compile-choose opts children)
        :justify       (compile-justify opts children)
        :logical-block (compile-logical-block opts children)
        (:downcase :upcase :capitalize :titlecase)
        (do
          (when (seq opts)
            (invalid-dsl "Case wrapper directives do not accept options"
                         {:kind :invalid-options
                          :directive kw
                          :options opts
                          :element elem}))
          (wrap-case (compile-body children) kw))
        (cond
          ;; A known directive with non-option children is a body
          ;; wrapping that directive (e.g. [:int " file"] is the
          ;; :int directive followed by a literal).
          (seq children)            (compile-body elem)
          (d/+special-keywords+ kw) (compile-special kw opts)
          (d/directive-config kw)   (compile-simple kw opts)
          :else (invalid-dsl "Unknown directive form"
                             {:kind :invalid-directive
                              :directive kw
                              :element elem}))))

    :else
    (invalid-dsl "DSL elements must be strings, keywords, or vectors"
                 {:kind :invalid-element
                  :element elem})))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- directive-vector?
  "True if v is a single directive vector (not a body of elements).

   The rule: if the second element is a map, the user has committed
   to `[directive options ...]` shape, so v is a directive. Compound,
   special, and case-wrapper keywords are always directives. Simple
   keywords with 0-1 elements are also directives (a trivial form).
   Everything else is ambiguous at the root level — `[:int \" file\"]`
   reads naturally as \"integer directive followed by a literal\",
   i.e. a body — so we default to body mode and route through
   `compile-body`. `compile-element` also handles that shape via
   its `(seq children) -> compile-body elem` branch, so the two
   dispatch paths stay consistent."
  [v]
  (and (vector? v)
       (keyword? (first v))
       (or (d/+compound-keywords+ (first v))
           (d/+special-keywords+ (first v))
           (contains? d/+case-keywords+ (first v))
           (<= (count v) 1)
           (map? (second v)))))

(defn compile-format
  "Compile a DSL form into a cl-format format string.

   Accepts:
     - a body vector of elements: [\"Hello \" :str \"!\"]
     - a single directive vector: [:each {:sep \", \"} :str]
     - a bare keyword: :str

   Examples:
     (compile-format :str)                     ;=> \"~A\"
     (compile-format [:str])                     ;=> \"~A\"
     (compile-format [:str {:width 10}])         ;=> \"~10A\"
     (compile-format [\"Hello \" :str \"!\"])     ;=> \"Hello ~A!\"
     (compile-format [:cardinal \" file\" [:plural {:rewind true}]])
                                                ;=> \"~R file~:P\"
     (compile-format [:each {:sep \", \"} :str])  ;=> \"~{~A~^, ~}\"
     (compile-format [:if \"yes\" \"no\"])        ;=> \"~:[no~;yes~]\""
  [dsl]
  (cond
    (keyword? dsl)          (compile-element dsl)
    (directive-vector? dsl) (compile-element dsl)
    (vector? dsl)           (compile-body dsl)
    :else                   (invalid-dsl "DSL root must be a keyword or vector"
                                         {:kind :invalid-root
                                          :dsl dsl})))

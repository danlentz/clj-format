(ns clj-format.compiler
  "Compiles the clj-format s-expression DSL into cl-format format strings.

  The inverse of clj-format.parser/parse-format. Takes a DSL body vector
  and produces the equivalent cl-format string.

  Examples:
    (compile-format [:str])                      => \"~A\"
    (compile-format [\"Hello \" :str \"!\"])      => \"Hello ~A!\"
    (compile-format [[:each {:sep \", \"} :str]]) => \"~{~A~^, ~}\"
    (compile-format [[:if \"yes\" \"no\"]])       => \"~:[no~;yes~]\""
  (:require [clojure.string :as str]
            [clj-format.directives :as d]))


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

(defn- escape-tildes [s]
  (str/replace s "~" "~~"))


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
  (apply str (map compile-element elements)))

(defn- compile-clause
  "Compile a clause value (from :if, :choose, :justify).
   nil -> empty, string -> escaped literal, keyword -> directive,
   vector -> directive or body."
  [clause]
  (cond
    (nil? clause)     ""
    (string? clause)  (escape-tildes clause)
    (keyword? clause) (compile-element clause)
    (vector? clause)  (if (keyword? (first clause))
                        (compile-element clause)
                        (compile-body clause))
    :else             (str clause)))

(defn- join-clauses
  "Compile clauses and join with ~; separators."
  [clauses]
  (str/join "~;" (map compile-clause clauses)))

(defn- compile-simple
  "Compile a simple (non-compound) directive using shared config."
  [kw opts]
  (let [{:keys [char params flags]} (d/+directives+ kw)]
    (maybe-wrap-case opts
      (fn [opts]
        (str "~" (format-params params opts) (compile-flags flags opts) char)))))

(defn- compile-special
  "Compile special-dispatch directives (:cardinal, :ordinal, etc.)."
  [kw opts]
  (maybe-wrap-case opts
    (fn [opts]
      (case kw
        :cardinal  "~R"
        :ordinal   "~:R"
        :roman     "~@R"
        :old-roman "~:@R"
        :back      (str "~" (some-> (:n opts) str) ":*")
        :goto      (str "~" (some-> (:n opts) str) "@*")
        :break     (case (:mode opts)
                     :fill      "~:_"
                     :miser     "~@_"
                     :mandatory "~:@_"
                     "~_")))))

(defn- compile-each
  "Compile [:each opts? & body] -> ~flags max{ body ~close}"
  [opts body-elements]
  (maybe-wrap-case opts
    (fn [{:keys [from max min sep] :as opts}]
      (let [open-flags (case from
                         :rest "@", :sublists ":", :rest-sublists ":@", nil "")
            body-str   (compile-body body-elements)
            body-str   (if sep (str body-str "~^" sep) body-str)]
        (str "~" (some-> max str) open-flags "{"
             body-str
             (if (= 1 min) "~:}" "~}"))))))

(defn- compile-if
  "Compile [:if opts? then else] -> ~:[false~;true~]"
  [opts then-clause else-clause]
  (maybe-wrap-case opts
    (fn [_]
      (str "~:[" (compile-clause else-clause) "~;" (compile-clause then-clause) "~]"))))

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
      (let [joined (join-clauses clauses)]
        (str "~" (some-> selector format-param)
             "[" (if default (str joined "~:;" (compile-clause default)) joined)
             "~]")))))

(defn- compile-justify
  "Compile [:justify opts? & clauses] -> ~params flags<c0~;c1~;...~>"
  [opts clauses]
  (str "~" (format-params (:params (d/+directives+ :justify)) opts)
       (when (:pad-before opts) ":") (when (:pad-after opts) "@")
       "<" (join-clauses clauses) "~>"))

(defn- compile-logical-block
  "Compile [:logical-block opts? & clauses] -> ~flags<c0~;...~:>"
  [opts clauses]
  (str "~" (when (:colon opts) ":")
       "<" (join-clauses clauses) "~:>"))

(defn- parse-hiccup
  "Destructure a directive vector using the Hiccup convention.
   If the second element is a map, it's options; everything after is children.
   Returns [keyword opts children]."
  [v]
  (let [[kw & body] v
        [opts children] (if (map? (first body))
                          [(first body) (vec (rest body))]
                          [{} (vec body)])]
    [kw opts children]))

(defn- compile-element
  "Compile a single DSL element into a format string fragment."
  [elem]
  (cond
    (string? elem)
    (escape-tildes elem)

    (keyword? elem)
    (if (d/+special-keywords+ elem)
      (compile-special elem {})
      (str "~" (:char (d/+directives+ elem))))

    (vector? elem)
    (let [[kw opts children] (parse-hiccup elem)]
      (case kw
        :each          (compile-each opts children)
        :if            (compile-if opts (first children) (second children))
        :when          (compile-when-cond opts children)
        :choose        (compile-choose opts children)
        :justify       (compile-justify opts children)
        :logical-block (compile-logical-block opts children)
        (:downcase :upcase :capitalize :titlecase)
                       (wrap-case (compile-body children) kw)
        ;; Special or simple — if children present, it's a body vector
        (cond
          (and (d/+special-keywords+ kw) (seq children)) (compile-body elem)
          (d/+special-keywords+ kw)                      (compile-special kw opts)
          (seq children)                                  (compile-body elem)
          :else                                           (compile-simple kw opts))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- directive-vector?
  "True if v is a single directive vector (not a body of elements).
   A vector starting with a compound keyword is always a directive.
   A vector starting with a simple keyword is a directive if it has
   0-1 elements or the second element is a map (options)."
  [v]
  (and (vector? v)
       (keyword? (first v))
       (or (d/+compound-keywords+ (first v))
           (d/+special-keywords+ (first v))
           (<= (count v) 1)
           (map? (second v)))))

(defn compile-format
  "Compile a DSL form into a cl-format format string.

   Accepts:
     - a body vector of elements: [\"Hello \" :str \"!\"]
     - a single directive vector: [:each {:sep \", \"} :str]
     - a bare keyword: :str

   Examples:
     (compile-format [:str])                     ;=> \"~A\"
     (compile-format [:str {:width 10}])         ;=> \"~10A\"
     (compile-format [\"Hello \" :str \"!\"])     ;=> \"Hello ~A!\"
     (compile-format [:each {:sep \", \"} :str])  ;=> \"~{~A~^, ~}\"
     (compile-format [:if \"yes\" \"no\"])        ;=> \"~:[no~;yes~]\""
  [dsl]
  (cond
    (keyword? dsl)            (compile-element dsl)
    (directive-vector? dsl)   (compile-element dsl)
    :else                     (compile-body dsl)))

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
  (cond
    (nil? v)     ""
    (integer? v) (str v)
    (char? v)    (str "'" v)
    (= :V v)     "V"
    (= :# v)     "#"
    :else        (str v)))

(defn- format-params
  "Build the comma-separated param string, trimming trailing nils."
  [param-names opts]
  (let [positional (mapv #(get opts %) param-names)
        last-idx (reduce (fn [acc i]
                           (if (some? (nth positional i)) i acc))
                         -1 (range (count positional)))]
    (if (neg? last-idx)
      ""
      (str/join "," (map format-param (subvec positional 0 (inc last-idx)))))))

(defn- flag-active?
  "Check whether a semantic flag option is active in opts.
   rule is [opt-key opt-val] from the directive's flag-rules."
  [[opt-key opt-val] opts]
  (let [v (get opts opt-key)]
    (if (= true opt-val)
      v
      (= v opt-val))))

(defn- compile-flags
  "Determine colon and at flags from semantic opts, using the directive's
   flag rules from the shared configuration."
  [kw opts]
  (let [rules (d/flag-rules kw)]
    (str (when (and (:colon rules) (flag-active? (:colon rules) opts)) ":")
         (when (and (:at rules) (flag-active? (:at rules) opts)) "@"))))

(defn- escape-tildes
  "Escape literal tildes in text for cl-format."
  [s]
  (str/replace s "~" "~~"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Case Conversion Wrapping
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- wrap-case
  "Wrap a compiled format string fragment in case conversion."
  [compiled-str mode]
  (str (get d/+case-open+ mode) compiled-str "~)"))


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

(defn- compile-simple
  "Compile a simple (non-compound) directive using shared config."
  [kw opts]
  (let [case-mode (:case opts)
        opts (dissoc opts :case)
        param-str (format-params (d/param-names kw) opts)
        flag-str (compile-flags kw opts)
        result (str "~" param-str flag-str (d/directive-char kw))]
    (if case-mode (wrap-case result case-mode) result)))

(defn- compile-special
  "Compile special-dispatch directives (:cardinal, :ordinal, etc.)."
  [kw opts]
  (let [case-mode (:case opts)
        result (case kw
                 :cardinal  "~R"
                 :ordinal   "~:R"
                 :roman     "~@R"
                 :old-roman "~:@R"
                 :back      (let [n (:n opts)]
                              (if n (str "~" n ":*") "~:*"))
                 :goto      (let [n (:n opts)]
                              (if n (str "~" n "@*") "~@*"))
                 :break     (case (:mode opts)
                              :fill      "~:_"
                              :miser     "~@_"
                              :mandatory "~:@_"
                              nil        "~_"))]
    (if case-mode (wrap-case result case-mode) result)))

(defn- compile-each
  "Compile [:each opts? & body] -> ~flags max{ body ~close}"
  [opts body-elements]
  (let [case-mode (:case opts)
        open-flags (case (:from opts)
                     :rest          "@"
                     :sublists      ":"
                     :rest-sublists ":@"
                     nil            "")
        max-str (if-let [m (:max opts)] (str m) "")
        body-str (compile-body body-elements)
        sep (:sep opts)
        body-with-sep (if sep (str body-str "~^" sep) body-str)
        close (if (= 1 (:min opts)) "~:}" "~}")
        result (str "~" max-str open-flags "{" body-with-sep close)]
    (if case-mode (wrap-case result case-mode) result)))

(defn- compile-if
  "Compile [:if opts? then else] -> ~:[false~;true~]
   Reverses clause order: DSL true-first -> cl-format false-first."
  [opts then-clause else-clause]
  (let [case-mode (:case opts)
        false-str (compile-clause else-clause)
        true-str (compile-clause then-clause)
        result (str "~:[" false-str "~;" true-str "~]")]
    (if case-mode (wrap-case result case-mode) result)))

(defn- compile-when-cond
  "Compile [:when opts? & body] -> ~@[body~]"
  [opts body-elements]
  (let [case-mode (:case opts)
        body-str (compile-body body-elements)
        result (str "~@[" body-str "~]")]
    (if case-mode (wrap-case result case-mode) result)))

(defn- compile-choose
  "Compile [:choose opts? & clauses] -> ~selector[c0~;c1~;...~:;default~]"
  [opts clauses]
  (let [case-mode (:case opts)
        selector (:selector opts)
        default (:default opts)
        selector-str (if selector (format-param selector) "")
        clause-strs (map compile-clause clauses)
        joined (str/join "~;" clause-strs)
        with-default (if default
                       (str joined "~:;" (compile-clause default))
                       joined)
        result (str "~" selector-str "[" with-default "~]")]
    (if case-mode (wrap-case result case-mode) result)))

(defn- compile-case-compound
  "Compile case conversion compound form (multi-element body)."
  [mode body-elements]
  (wrap-case (compile-body body-elements) mode))

(defn- compile-justify
  "Compile [:justify opts? & clauses] -> ~params flags<c0~;c1~;...~>"
  [opts clauses]
  (let [param-str (format-params (d/param-names :justify) opts)
        colon (:pad-before opts)
        at (:pad-after opts)
        flag-str (str (when colon ":") (when at "@"))
        clause-strs (map compile-clause clauses)
        joined (str/join "~;" clause-strs)]
    (str "~" param-str flag-str "<" joined "~>")))

(defn- compile-logical-block
  "Compile [:logical-block opts? & clauses] -> ~flags<c0~;...~:>"
  [opts clauses]
  (let [flag-str (if (:colon opts) ":" "")
        clause-strs (map compile-clause clauses)
        joined (str/join "~;" clause-strs)]
    (str "~" flag-str "<" joined "~:>")))

(defn- parse-hiccup
  "Destructure a directive vector using the Hiccup convention.
   If the second element is a map, it's options; everything after is children.
   Returns [keyword opts children]."
  [v]
  (let [kw (first v)
        has-opts (and (> (count v) 1) (map? (nth v 1)))
        opts (if has-opts (nth v 1) {})
        children (if has-opts (subvec v 2) (subvec v 1))]
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
      (str "~" (d/directive-char elem)))

    (vector? elem)
    (let [[kw opts children] (parse-hiccup elem)]
      (cond
        ;; Compound directives
        (= :each kw)           (compile-each opts children)
        (= :if kw)             (compile-if opts (first children) (second children))
        (= :when kw)           (compile-when-cond opts children)
        (= :choose kw)         (compile-choose opts children)
        (= :justify kw)        (compile-justify opts children)
        (= :logical-block kw)  (compile-logical-block opts children)

        ;; Case conversion compounds (multi-element body, opts ignored)
        (#{:downcase :upcase :capitalize :titlecase} kw)
        (compile-case-compound kw children)

        ;; Special dispatch — if it has children, it's a body vector
        (d/+special-keywords+ kw)
        (if (seq children) (compile-body elem) (compile-special kw opts))

        ;; Simple directive — if it has children, it's a body vector
        true
        (if (seq children) (compile-body elem) (compile-simple kw opts))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn compile-format
  "Compile a DSL body vector into a cl-format format string.

   The input is a vector of elements as produced by parse-format or
   written by hand. Elements can be literal strings, bare keywords
   (simple directives), or vectors (directives with options or
   compound directives).

   Examples:
     (compile-format [:str])                       ;=> \"~A\"
     (compile-format [\"Hello \" :str \"!\"])       ;=> \"Hello ~A!\"
     (compile-format [[:each {:sep \", \"} :str]])  ;=> \"~{~A~^, ~}\"
     (compile-format [[:if \"yes\" \"no\"]])        ;=> \"~:[no~;yes~]\""
  [dsl-body]
  (compile-body dsl-body))

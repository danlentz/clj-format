(ns clj-format.compiler
  "Compiles the clj-format s-expression DSL into cl-format format strings.

  The inverse of clj-format.parser/parse-format. Takes a DSL body vector
  and produces the equivalent cl-format string.

  Examples:
    (compile-format [:str])                      => \"~A\"
    (compile-format [\"Hello \" :str \"!\"])      => \"Hello ~A!\"
    (compile-format [[:each {:sep \", \"} :str]]) => \"~{~A~^, ~}\"
    (compile-format [[:if \"yes\" \"no\"]])       => \"~:[no~;yes~]\""
  (:require [clojure.string :as str]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Keyword → Directive Character Mapping
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private +keyword->char+
  {:str \A  :pr \S  :write \W  :char \C
   :int \D  :bin \B  :oct \O  :hex \X  :plural \P
   :float \F  :exp \E  :gfloat \G  :money \$
   :nl \%  :fresh \&  :page \|  :tab \T  :tilde \~
   :recur \?  :stop \^  :indent \I
   :skip \*  :back \*  :goto \*
   :radix \R  :cardinal \R  :ordinal \R  :roman \R  :old-roman \R})

(def ^:private +param-names+
  "Positional parameter names for each directive keyword."
  {:str [:width :pad-step :min-pad :fill]
   :pr [:width :pad-step :min-pad :fill]
   :write [] :char [] :plural []
   :int [:width :fill :group-sep :group-size]
   :bin [:width :fill :group-sep :group-size]
   :oct [:width :fill :group-sep :group-size]
   :hex [:width :fill :group-sep :group-size]
   :radix [:base :width :fill :group-sep :group-size]
   :float [:width :decimals :scale :overflow :fill]
   :exp [:width :decimals :exp-digits :scale :overflow :fill :exp-char]
   :gfloat [:width :decimals :exp-digits :scale :overflow :fill :exp-char]
   :money [:decimals :int-digits :width :fill]
   :nl [:count] :fresh [:count] :page [:count] :tilde [:count]
   :tab [:col :step]
   :recur [] :indent [:n]
   :stop [:arg1 :arg2 :arg3]
   :skip [:n] :back [:n] :goto [:n]})


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

(defn- compile-flags
  "Determine colon and at flags from semantic opts for a given keyword."
  [kw opts]
  (let [colon (case kw
                (:int :bin :oct :hex :radix) (:group opts)
                :money    (:sign-first opts)
                :plural   (:rewind opts)
                :write    (:pretty opts)
                :char     (= :name (:format opts))
                :stop     (:outer opts)
                :indent   (= :current (:relative-to opts))
                false)
        at (case kw
             (:int :bin :oct :hex :radix :float :exp :gfloat :money)
             (= :always (:sign opts))
             (:str :pr) (= :left (:pad opts))
             :write     (:full opts)
             :char      (= :readable (:format opts))
             :tab       (:relative opts)
             :recur     (= :rest (:from opts))
             :plural    (= :ies (:form opts))
             false)]
    (str (when colon ":") (when at "@"))))

(defn- escape-tildes
  "Escape literal tildes in text for cl-format."
  [s]
  (str/replace s "~" "~~"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Case Conversion Wrapping
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private +case-open+
  {:downcase "~(" :capitalize "~:(" :titlecase "~@(" :upcase "~:@("})

(defn- wrap-case
  "Wrap a compiled format string fragment in case conversion."
  [compiled-str mode]
  (str (get +case-open+ mode) compiled-str "~)"))


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
   nil → empty, string → escaped literal, keyword → directive,
   vector → directive or body."
  [clause]
  (cond
    (nil? clause)     ""
    (string? clause)  (escape-tildes clause)
    (keyword? clause) (str "~" (get +keyword->char+ clause))
    (vector? clause)  (if (keyword? (first clause))
                        (compile-element clause)
                        (compile-body clause))
    :else             (str clause)))

(defn- compile-simple
  "Compile a simple (non-compound) directive."
  [kw opts]
  (let [case-mode (:case opts)
        opts (dissoc opts :case)
        param-names (get +param-names+ kw [])
        param-str (format-params param-names opts)
        flag-str (compile-flags kw opts)
        char (get +keyword->char+ kw)
        result (str "~" param-str flag-str char)]
    (if case-mode (wrap-case result case-mode) result)))

(defn- compile-special
  "Compile special-dispatch directives (:cardinal, :ordinal, etc.)."
  [kw opts]
  (let [case-mode (:case opts)
        opts (dissoc opts :case)
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
  "Compile [:each opts? & body] → ~flags max{ body ~close}"
  [opts body-elements]
  (let [case-mode (:case opts)
        from (:from opts)
        open-flags (case from
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
  "Compile [:if opts? then else] → ~:[false~;true~]
   Reverses clause order: DSL true-first → cl-format false-first."
  [opts then-clause else-clause]
  (let [case-mode (:case opts)
        false-str (compile-clause else-clause)
        true-str (compile-clause then-clause)
        result (str "~:[" false-str "~;" true-str "~]")]
    (if case-mode (wrap-case result case-mode) result)))

(defn- compile-when-cond
  "Compile [:when opts? & body] → ~@[body~]"
  [opts body-elements]
  (let [case-mode (:case opts)
        body-str (compile-body body-elements)
        result (str "~@[" body-str "~]")]
    (if case-mode (wrap-case result case-mode) result)))

(defn- compile-choose
  "Compile [:choose opts? & clauses] → ~selector[c0~;c1~;...~:;default~]"
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
  "Compile [:justify opts? & clauses] → ~params flags<c0~;c1~;...~>"
  [opts clauses]
  (let [param-names [:width :pad-step :min-pad :fill]
        param-str (format-params param-names opts)
        colon (:pad-before opts)
        at (:pad-after opts)
        flag-str (str (when colon ":") (when at "@"))
        clause-strs (map compile-clause clauses)
        joined (str/join "~;" clause-strs)]
    (str "~" param-str flag-str "<" joined "~>")))

(defn- compile-logical-block
  "Compile [:logical-block opts? & clauses] → ~flags<c0~;...~:>"
  [opts clauses]
  (let [flag-str (if (:colon opts) ":" "")
        clause-strs (map compile-clause clauses)
        joined (str/join "~;" clause-strs)]
    (str "~" flag-str "<" joined "~:>")))

(defn- parse-hiccup
  "Destructure a directive vector using the Hiccup convention.
   Returns [keyword opts children]."
  [v]
  (let [kw (first v)
        has-opts (and (> (count v) 1) (map? (nth v 1)))
        opts (if has-opts (nth v 1) {})
        children (if has-opts (subvec v 2) (subvec v 1))]
    [kw opts children]))

(def ^:private +compound-keywords+
  #{:each :when :if :choose
    :downcase :upcase :capitalize :titlecase
    :justify :logical-block})

(def ^:private +special-keywords+
  #{:cardinal :ordinal :roman :old-roman :back :goto :break})

(defn- compile-element
  "Compile a single DSL element into a format string fragment."
  [elem]
  (cond
    (string? elem)
    (escape-tildes elem)

    (keyword? elem)
    (if (+special-keywords+ elem)
      (compile-special elem {})
      (str "~" (get +keyword->char+ elem)))

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

        ;; Case conversion compounds (multi-element body)
        (#{:downcase :upcase :capitalize :titlecase} kw)
        (compile-case-compound kw (into (if (seq opts) [opts] []) children))

        ;; Special dispatch — if it has children, it's a body vector
        (+special-keywords+ kw) (if (seq children)
                                  (compile-body elem)
                                  (compile-special kw opts))

        ;; Simple directive — if it has children, it's a body vector, not a directive
        true (if (seq children)
               (compile-body elem)
               (compile-simple kw opts))))))


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
     (compile-format [:str])
     ;=> \"~A\"

     (compile-format [\"Hello \" :str \"!\"])
     ;=> \"Hello ~A!\"

     (compile-format [[:each {:sep \", \"} :str]])
     ;=> \"~{~A~^, ~}\"

     (compile-format [[:if \"yes\" \"no\"]])
     ;=> \"~:[no~;yes~]\""
  [dsl-body]
  (compile-body dsl-body))

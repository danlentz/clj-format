(ns clj-format.figlet
  "Optional :figlet DSL directive — FIGlet ASCII-art banners.

  Integrates com.github.danlentz/clj-figlet with the clj-format DSL.
  Requiring this namespace installs a preprocessor that expands
  `[:figlet opts? & body]` forms into their rendered multi-line
  strings before compilation.

  This namespace is JVM-only. clj-figlet must be on the classpath:

    [com.github.danlentz/clj-figlet \"0.1.4\"]

  Once loaded, the directive is available through the normal
  clj-format entry point:

    (require 'clj-format.figlet)
    (clj-format true [:figlet {:font \"small\"} \"Hello\"])

  Directive shape (Hiccup convention):

    [:figlet \"Hello\"]                       ;; default font
    [:figlet {:font \"small\"} \"Hello\"]     ;; explicit font
    [:figlet {:font \"slant\"} \"Line 1\" \"Line 2\"]   ;; multi-line

  The body must be one or more literal strings; the :figlet form is
  expanded at preprocessing time, so runtime argument values cannot
  feed into it. Users who need runtime-derived banner text should
  call clj-figlet.core/render themselves and pass the result as a
  normal string argument."
  (:require [clj-figlet.core  :as figlet]
            [clj-format.core  :as core]
            [clojure.string   :as str]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rendering
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private render-cached
  "Memoized renderer — keyed on [font-name text] so repeated banners
   in a single format call (or across calls) skip the font load."
  (memoize
    (fn [font-name text]
      (figlet/render font-name text))))

(defn- render-figlet
  "Render body lines as a FIGlet banner using opts."
  [body opts]
  (let [font (or (:font opts) "standard")
        text (str/join "\n" body)]
    (render-cached font text)))

(defn- figlet-form?
  "True if x is a [:figlet ...] DSL form."
  [x]
  (and (vector? x) (= :figlet (first x))))

(defn- expand-figlet-form
  "Expand a single [:figlet opts? & body] form into a literal string."
  [form]
  (let [[_ & tail] form
        [opts body] (if (and (seq tail) (map? (first tail)))
                      [(first tail) (rest tail)]
                      [{} tail])]
    (when (empty? body)
      (throw (ex-info ":figlet directive requires at least one body string"
                      {:form form})))
    (when-not (every? string? body)
      (throw (ex-info ":figlet body must contain only literal strings"
                      {:form form :body (vec body)})))
    (render-figlet body opts)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DSL Walk
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn expand
  "Walk a DSL form and replace every [:figlet ...] subform with its
   rendered multi-line string output. Non-figlet forms pass through
   unchanged.

   This is the function installed as clj-format.core/*dsl-preprocessor*
   when this namespace is loaded. Users may call it directly to
   preprocess a DSL without invoking clj-format."
  [dsl]
  (cond
    (figlet-form? dsl) (expand-figlet-form dsl)
    (vector? dsl)      (mapv expand dsl)
    (seq? dsl)         (mapv expand dsl)
    :else              dsl))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Preprocessor Installation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; Install the expander as clj-format's DSL preprocessor when this
;; namespace is loaded. Namespaces that don't require clj-format.figlet
;; leave *dsl-preprocessor* at its identity default.

(alter-var-root #'core/*dsl-preprocessor* (constantly expand))

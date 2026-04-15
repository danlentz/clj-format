(ns clj-format.figlet
  "Optional :figlet DSL directive — FIGlet ASCII-art banners.

  Integrates com.github.danlentz/clj-figlet with the clj-format DSL.
  Requiring this namespace installs a preprocessor that expands
  `[:figlet opts? & body]` forms into their rendered multi-line
  strings before compilation.

  This namespace is JVM-only and opt-in. `clj-figlet` is declared
  with `:scope \"provided\"` in clj-format, so it is not pulled
  transitively — consumer projects that want the `:figlet`
  directive must add it to their own dependencies:

    [com.github.danlentz/clj-figlet \"0.1.4\"]

  Then require this namespace once at startup to install the
  preprocessor.

  Once loaded, the directive is available through the normal
  clj-format entry point:

    (require 'clj-format.figlet)
    (clj-format true [:figlet {:font \"small\"} \"Hello\"])

  Directive shape (Hiccup convention):

    [:figlet \"Hello\"]                       ;; default font
    [:figlet {:font \"small\"} \"Hello\"]     ;; explicit font
    [:figlet {:font \"slant\"} \"Line 1\" \"Line 2\"]   ;; multi-line

  The :font option accepts every shape clj-figlet.core/render accepts:

    :font \"standard\"                   ;; bundled font name
    :font \"fonts/small.flf\"            ;; classpath resource
    :font \"/abs/path/custom.flf\"       ;; filesystem path
    :font (java.io.File. \"f.flf\")     ;; File object
    :font my-preloaded-font             ;; font map from load-font

  Layout (smushing/fitting/full), smushing rules, direction, and
  character width are all determined by the font file itself —
  clj-figlet has no render-time layout overrides. Pre-loading a font
  with clj-figlet.core/load-font is the right way to customize.

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


(defn- render-figlet
  "Render body lines as a FIGlet banner using opts.

   Delegates directly to `clj-figlet.core/render` with no caching at
   this layer. Caching a `(font, text) -> rendered` map would be
   unbounded in `text`, and caching `font-key -> loaded-font` here
   would only duplicate work that belongs in clj-figlet. Callers
   that need to amortize font I/O across many banners should call
   `clj-figlet.core/load-font` themselves once and pass the
   resulting font map via `:font`."
  [body opts]
  (let [font (or (:font opts) "standard")
        text (str/join "\n" body)]
    (figlet/render font text)))

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

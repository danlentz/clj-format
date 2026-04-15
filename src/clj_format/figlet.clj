(ns clj-format.figlet
  "Optional :figlet DSL directive — FIGlet ASCII-art banners.

  Integrates com.github.danlentz/clj-figlet with the clj-format DSL.
  Requiring this namespace installs a preprocessor that expands
  `[:figlet opts? & body]` forms into their rendered multi-line
  strings before compilation.

  This namespace is JVM-only. `clj-figlet` ships as a normal
  dependency of clj-format, so nothing extra needs to be added to
  consumer projects — just require this namespace once at startup.

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
            [clojure.string   :as str])
  (:import (java.io File Reader)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rendering
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private render-cached
  "Memoized renderer for stable font keys.

   Only safe for values whose equality is cheap and that don't mutate
   across calls: strings (font names and paths) and File objects.
   Pre-loaded font maps skip the cache because the font I/O they would
   amortize has already happened, and comparing large maps as cache
   keys is wasteful. Readers skip the cache because they are stateful
   and can only be consumed once."
  (memoize
    (fn [font-key text]
      (figlet/render font-key text))))

(defn- cacheable-font?
  "True when a font source can safely be used as a memoize key."
  [font]
  (or (string? font)
      (instance? File font)))

(defn- render-figlet
  "Render body lines as a FIGlet banner using opts."
  [body opts]
  (let [font (or (:font opts) "standard")
        text (str/join "\n" body)]
    (if (cacheable-font? font)
      (render-cached font text)
      (figlet/render font text))))

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

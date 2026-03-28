(ns clj-format.directives
  "Single source of truth for directive configuration.

  Used by both the parser and compiler. Each directive is defined once
  with its character, parameter names, and flag-to-semantic-option mapping.")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Shared Parameter/Flag Configurations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private padded-output
  {:params [:width :pad-step :min-pad :fill]
   :flags  {:at [:pad :left]}})

(def ^:private integer-output
  {:params [:width :fill :group-sep :group-size]
   :flags  {:colon [:group true] :at [:sign :always]}})

(def ^:private exp-output
  {:params [:width :decimals :exp-digits :scale :overflow :fill :exp-char]
   :flags  {:at [:sign :always]}})

(def ^:private nav-param
  {:params [:n] :flags {}})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Directive Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def +directives+
  "Master configuration for all directives, keyed by DSL keyword.
   Each entry maps:
     :char   — cl-format directive character (nil for compound-only)
     :params — positional parameter names in order
     :flags  — map of raw flag (:colon/:at) to [semantic-key semantic-val]"
  {;; Output
   :str       (assoc padded-output :char \A)
   :pr        (assoc padded-output :char \S)
   :write     {:char \W :params [] :flags {:colon [:pretty true] :at [:full true]}}
   :char      {:char \C :params [] :flags {:colon [:format :name] :at [:format :readable]}}
   ;; Integer
   :int       (assoc integer-output :char \D)
   :bin       (assoc integer-output :char \B)
   :oct       (assoc integer-output :char \O)
   :hex       (assoc integer-output :char \X)
   :plural    {:char \P :params [] :flags {:colon [:rewind true] :at [:form :ies]}}
   ;; Float
   :float     {:char \F :params [:width :decimals :scale :overflow :fill]
               :flags {:at [:sign :always]}}
   :exp       (assoc exp-output :char \E)
   :gfloat    (assoc exp-output :char \G)
   :money     {:char \$ :params [:decimals :int-digits :width :fill]
               :flags {:colon [:sign-first true] :at [:sign :always]}}
   ;; Layout
   :nl        {:char \% :params [:count] :flags {}}
   :fresh     {:char \& :params [:count] :flags {}}
   :page      {:char \| :params [:count] :flags {}}
   :tab       {:char \T :params [:col :step] :flags {:at [:relative true]}}
   :tilde     {:char \~ :params [:count] :flags {}}
   ;; Navigation / control
   :recur     {:char \? :params [] :flags {:at [:from :rest]}}
   :stop      {:char \^ :params [:arg1 :arg2 :arg3] :flags {:colon [:outer true]}}
   :indent    {:char \I :params [:n] :flags {:colon [:relative-to :current]}}
   ;; Special dispatch — multiple keywords share one character
   :cardinal  {:char \R :params [] :flags {}}
   :ordinal   {:char \R :params [] :flags {}}
   :roman     {:char \R :params [] :flags {}}
   :old-roman {:char \R :params [] :flags {}}
   :radix     (assoc integer-output :char \R
                     :params [:base :width :fill :group-sep :group-size])
   :skip      (assoc nav-param :char \*)
   :back      (assoc nav-param :char \*)
   :goto      (assoc nav-param :char \*)
   :break     {:char \_ :params [] :flags {}}
   ;; Compound — no :char, but :params needed for serialization
   :justify   {:params [:width :pad-step :min-pad :fill] :flags {}}
   :each      {:params [:max] :flags {}}
   :choose    {:params [:selector] :flags {}}})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Derived Lookups
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:const +special-chars+
  "Characters with flag-based keyword dispatch in the parser."
  #{\R \* \_})

(def +char->simple+
  "Maps directive characters to config entries for simple (non-special)
   directives. Each value includes :kw alongside :char, :params, :flags."
  (reduce-kv (fn [m kw {:keys [char] :as cfg}]
               (if (or (nil? char) (+special-chars+ char))
                 m
                 (assoc m char (assoc cfg :kw kw))))
             {} +directives+))

(def ^:const +compound-keywords+
  "Keywords that represent compound (bracketed) directives."
  #{:each :when :if :choose
    :downcase :upcase :capitalize :titlecase
    :justify :logical-block})

(def ^:const +special-keywords+
  "Keywords that compile via special dispatch (not table-driven)."
  #{:cardinal :ordinal :roman :old-roman :back :goto :break})

(def ^:const +case-open+
  "Maps case conversion mode keywords to their cl-format opening strings."
  {:downcase "~(" :capitalize "~:(" :titlecase "~@(" :upcase "~:@("})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Raw → Semantic Translation (used by parser)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn positional->named
  "Convert positional params to a named map using the given name list.
   Nil values are omitted."
  [param-names params]
  (into {}
        (keep-indexed (fn [i v]
                        (when (and (some? v) (< i (count param-names)))
                          [(nth param-names i) v])))
        params))

(defn translate-flags
  "Translate raw colon/at flags to semantic options using a flag-map.
   flag-map is {:colon [key val], :at [key val]}."
  [raw-flags flag-map]
  (reduce-kv (fn [m flag-key [opt-key opt-val]]
               (if (get raw-flags flag-key)
                 (assoc m opt-key opt-val)
                 m))
             {} flag-map))

(defn raw->opts
  "Convert raw cl-format params and flags to a semantic opts map.
   kw-or-config is either a directive keyword or a config map."
  [kw-or-config positional-params raw-flags]
  (let [{:keys [params flags]} (if (keyword? kw-or-config)
                                 (+directives+ kw-or-config)
                                 kw-or-config)]
    (merge (positional->named params positional-params)
           (translate-flags raw-flags flags))))

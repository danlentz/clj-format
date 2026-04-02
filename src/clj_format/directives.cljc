(ns clj-format.directives
  "Directive metadata and dispatch rules shared by the parser and compiler."
  (:require [clj-format.errors :as err]))


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
;; Directive Families
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def +simple-directives+
  "Simple char-backed directives with their real parameter and flag metadata."
  {;; Output
   :str    (assoc padded-output :char \A)
   :pr     (assoc padded-output :char \S)
   :write  {:char \W :params [] :flags {:colon [:pretty true] :at [:full true]}}
   :char   {:char \C :params [] :flags {:colon [:name true] :at [:readable true]}}
   ;; Integer
   :int    (assoc integer-output :char \D)
   :bin    (assoc integer-output :char \B)
   :oct    (assoc integer-output :char \O)
   :hex    (assoc integer-output :char \X)
   :plural {:char \P :params [] :flags {:colon [:rewind true] :at [:form :ies]}}
   ;; Float
   :float  {:char \F :params [:width :decimals :scale :overflow :fill]
            :flags {:at [:sign :always]}}
   :exp    (assoc exp-output :char \E)
   :gfloat (assoc exp-output :char \G)
   :money  {:char \$ :params [:decimals :int-digits :width :fill]
            :flags {:colon [:sign-first true] :at [:sign :always]}}
   ;; Layout
   :nl     {:char \% :params [:count] :flags {}}
   :fresh  {:char \& :params [:count] :flags {}}
   :page   {:char \| :params [:count] :flags {}}
   :tab    {:char \T :params [:col :step] :flags {:at [:relative true]}}
   :tilde  {:char \~ :params [:count] :flags {}}
   ;; Navigation / control
   :recur  {:char \? :params [] :flags {:at [:from :rest]}}
   :stop   {:char \^ :params [:arg1 :arg2 :arg3] :flags {:colon [:outer true]}}
   :indent {:char \I :params [:n] :flags {:colon [:relative-to :current]}}})

(def +compound-directives+
  "True compound directives whose syntax is structurally bracketed."
  {:justify {:params [:width :pad-step :min-pad :fill]}
   :each    {:params [:max]}
   :choose  {:params [:selector]}
   :when    {}
   :if      {}
   :logical-block {}})

(def ^:const +case-keywords+
  "Case-conversion wrapper directives."
  #{:downcase :upcase :capitalize :titlecase})

(def ^:private +special-families+
  "Special dispatch families whose behavior depends on flags or params."
  {\R {:char \R
       :default-config {:params [] :flags {}}
       :param-variant  {:kw :radix
                        :config (assoc integer-output
                                  :char \R
                                  :params [:base :width :fill :group-sep :group-size])}
       :flag-variants  {[false false] {:kw :cardinal}
                        [true false]  {:kw :ordinal}
                        [false true]  {:kw :roman}
                        [true true]   {:kw :old-roman}}}
   \* {:char \*
       :default-config (assoc nav-param :char \*)
       :flag-variants  {[false false] {:kw :skip}
                        [true false]  {:kw :back}
                        [false true]  {:kw :goto}}}
   \_ {:char \_
       :default-config {:char \_ :params [] :flags {}}
       :flag-variants  {[false false] {:kw :break}
                        [true false]  {:kw :break :opts {:mode :fill}}
                        [false true]  {:kw :break :opts {:mode :miser}}
                        [true true]   {:kw :break :opts {:mode :mandatory}}}}})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Derived Lookups
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- keyword-config-pairs
  "Expand special families into keyword/config pairs."
  [families]
  (mapcat (fn [[_char {:keys [default-config param-variant flag-variants]}]]
            (concat
              (when param-variant
                [[(:kw param-variant) (:config param-variant)]])
              (for [[_flags {:keys [kw]}] flag-variants]
                [kw default-config])))
          families))

(def ^:private +special-keyword->config+
  (into {} (keyword-config-pairs +special-families+)))

(def ^:private +special-dispatch-keywords+
  (into #{}
        (comp (mapcat (comp vals :flag-variants val))
              (map :kw))
        +special-families+))

(def ^:const +special-chars+
  "Characters with special dispatch in the parser."
  (set (keys +special-families+)))

(def +char->simple+
  "Map of simple directive chars to parser/compiler metadata."
  (reduce-kv (fn [m kw cfg]
               (assoc m (:char cfg) (assoc cfg :kw kw)))
             {} +simple-directives+))

(def ^:const +compound-keywords+
  "Keywords that represent non-case compound directives."
  (set (keys +compound-directives+)))

(def +special-keywords+
  "Keywords produced by or compiled through special dispatch."
  +special-dispatch-keywords+)

(def ^:const +case-open+
  "Maps case conversion mode keywords to their cl-format opening strings."
  {:downcase "~(" :capitalize "~:(" :titlecase "~@(" :upcase "~:@("})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Directive Access
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn simple-directive-config
  "Return metadata for a simple directive keyword."
  [kw]
  (get +simple-directives+ kw))

(defn compound-directive-config
  "Return metadata for a compound directive keyword."
  [kw]
  (get +compound-directives+ kw))

(defn special-directive-config
  "Return derived metadata for a special-dispatch keyword."
  [kw]
  (get +special-keyword->config+ kw))

(defn directive-config
  "Return metadata for any known directive keyword."
  [kw]
  (or (simple-directive-config kw)
      (compound-directive-config kw)
      (special-directive-config kw)))

(defn known-directive-keyword?
  "True when `kw` names a known DSL directive or wrapper."
  [kw]
  (or (contains? +simple-directives+ kw)
      (contains? +compound-directives+ kw)
      (contains? +special-keyword->config+ kw)
      (contains? +case-keywords+ kw)))


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
  "Translate raw colon/at flags to semantic options using a flag-map."
  [raw-flags flag-map]
  (reduce-kv (fn [m flag-key [opt-key opt-val]]
               (if (get raw-flags flag-key)
                 (assoc m opt-key opt-val)
                 m))
             {} (or flag-map {})))

(defn- flag-key
  "Normalize raw colon/at flags to a lookup key."
  [raw-flags]
  [(boolean (:colon raw-flags)) (boolean (:at raw-flags))])

(defn- variant->kw+opts
  "Normalize a special-dispatch variant to [kw opts]."
  [{:keys [kw opts]}]
  [kw (or opts {})])

(declare raw->opts)

(defn parse-special
  "Translate a special-dispatch directive to DSL form."
  [char positional-params raw-flags]
  (let [{:keys [param-variant flag-variants]} (get +special-families+ char)
        variant (if (and param-variant (seq positional-params))
                  param-variant
                  (get flag-variants (flag-key raw-flags) ::invalid))]
    (when (= ::invalid variant)
      (throw (err/parse-error
               (str "Invalid flag combination for directive: " char)
               {:kind :invalid-special-flags
                :char char
                :flags raw-flags})))
    (let [[kw base-opts] (variant->kw+opts variant)
          opts           (merge base-opts
                                (raw->opts kw
                                           positional-params
                                           (if (= kw (:kw param-variant)) raw-flags {})))]
      (if (seq opts) [kw opts] kw))))

(defn special->raw
  "Translate a special-dispatch DSL form to raw compile metadata."
  [kw opts]
  (or
    (some (fn [[char {:keys [flag-variants]}]]
            (some (fn [[flags variant]]
                    (let [[variant-kw base-opts] (variant->kw+opts variant)
                          param-names            (:params (directive-config variant-kw))
                          allowed-keys           (concat param-names (keys base-opts))]
                      (when (and (= kw variant-kw)
                                 (= base-opts (select-keys opts (keys base-opts)))
                                 (empty? (apply dissoc opts allowed-keys)))
                        {:char char :flags flags :params param-names})))
                  flag-variants))
          +special-families+)
    (throw (err/compile-error
             "Unknown special directive form"
             {:kind :unknown-special-directive
              :directive kw
              :opts opts}))))

(defn raw->opts
  "Convert raw cl-format params and flags to a semantic opts map.
   kw-or-config is either a directive keyword or a config map."
  [kw-or-config positional-params raw-flags]
  (let [{:keys [params flags]} (if (keyword? kw-or-config)
                                 (directive-config kw-or-config)
                                 kw-or-config)]
    (merge (positional->named params positional-params)
           (translate-flags raw-flags flags))))

(ns clj-format.directives
  "Single source of truth for directive configuration.

  Used by both the parser and compiler. Each directive is defined once
  with its character, parameter names, and flag-to-semantic-option mapping.")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Directive Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def +directives+
  "Master configuration for all directives, keyed by DSL keyword.
   Each entry maps:
     :char   — cl-format directive character
     :params — positional parameter names in order
     :flags  — map of raw flag (:colon/:at) to [semantic-key semantic-val]"
  {:str       {:char \A :params [:width :pad-step :min-pad :fill]
               :flags {:at [:pad :left]}}
   :pr        {:char \S :params [:width :pad-step :min-pad :fill]
               :flags {:at [:pad :left]}}
   :write     {:char \W :params [] :flags {:colon [:pretty true] :at [:full true]}}
   :char      {:char \C :params [] :flags {:colon [:format :name] :at [:format :readable]}}
   :int       {:char \D :params [:width :fill :group-sep :group-size]
               :flags {:colon [:group true] :at [:sign :always]}}
   :bin       {:char \B :params [:width :fill :group-sep :group-size]
               :flags {:colon [:group true] :at [:sign :always]}}
   :oct       {:char \O :params [:width :fill :group-sep :group-size]
               :flags {:colon [:group true] :at [:sign :always]}}
   :hex       {:char \X :params [:width :fill :group-sep :group-size]
               :flags {:colon [:group true] :at [:sign :always]}}
   :plural    {:char \P :params [] :flags {:colon [:rewind true] :at [:form :ies]}}
   :float     {:char \F :params [:width :decimals :scale :overflow :fill]
               :flags {:at [:sign :always]}}
   :exp       {:char \E :params [:width :decimals :exp-digits :scale :overflow :fill :exp-char]
               :flags {:at [:sign :always]}}
   :gfloat    {:char \G :params [:width :decimals :exp-digits :scale :overflow :fill :exp-char]
               :flags {:at [:sign :always]}}
   :money     {:char \$ :params [:decimals :int-digits :width :fill]
               :flags {:colon [:sign-first true] :at [:sign :always]}}
   :nl        {:char \% :params [:count] :flags {}}
   :fresh     {:char \& :params [:count] :flags {}}
   :page      {:char \| :params [:count] :flags {}}
   :tab       {:char \T :params [:col :step] :flags {:at [:relative true]}}
   :tilde     {:char \~ :params [:count] :flags {}}
   :recur     {:char \? :params [] :flags {:at [:from :rest]}}
   :stop      {:char \^ :params [:arg1 :arg2 :arg3] :flags {:colon [:outer true]}}
   :indent    {:char \I :params [:n] :flags {:colon [:relative-to :current]}}
   ;; Special dispatch — multiple keywords share one character
   :cardinal  {:char \R :params [] :flags {}}
   :ordinal   {:char \R :params [] :flags {}}
   :roman     {:char \R :params [] :flags {}}
   :old-roman {:char \R :params [] :flags {}}
   :radix     {:char \R :params [:base :width :fill :group-sep :group-size]
               :flags {:colon [:group true] :at [:sign :always]}}
   :skip      {:char \* :params [:n] :flags {}}
   :back      {:char \* :params [:n] :flags {}}
   :goto      {:char \* :params [:n] :flags {}}
   :break     {:char \_ :params [] :flags {}}
   ;; Compound directives — no :char, but need :params for serialization
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
   directives. Each value has :kw, :char, :params, :flags."
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
;; Accessors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn directive-char
  "Returns the cl-format character for a DSL keyword."
  [kw]
  (:char (+directives+ kw)))

(defn param-names
  "Returns the positional parameter name vector for a DSL keyword."
  [kw]
  (or (:params (+directives+ kw)) []))

(defn flag-rules
  "Returns the flag translation map for a DSL keyword.
   Keys are :colon/:at, values are [semantic-key semantic-val]."
  [kw]
  (or (:flags (+directives+ kw)) {}))

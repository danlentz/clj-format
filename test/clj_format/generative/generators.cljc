(ns clj-format.generative.generators
  "Composable test.check generators for valid and invalid clj-format data."
  (:require [clojure.test.check.generators :as gen]))

(def ^:private text-chars
  (vec (concat
         (map char (range 48 58))
         (map char (range 65 91))
         (map char (range 97 123))
         [\space \- \_ \, \. \: \; \/ \? \! \* \+ \= \"])))

(def ^:private fill-chars
  [\0 \* \- \space \. \#])

(def ^:private printable-chars
  [\a \b \c \x \y \z \space \newline])

(def ^:private case-modes
  [:downcase :capitalize :titlecase :upcase])

(def ^:private scalar-keywords
  [:foo :bar :baz :quux])

(def ^:private simple-no-arg-keywords
  [:str :pr :char :int :bin :oct :hex :float :exp :gfloat :money
   :nl :fresh :page :tab :tilde :recur :stop :indent :skip :back :goto
   :cardinal :ordinal :roman :old-roman :break])

(defn- non-tilde-string-gen
  [min-len max-len]
  (gen/fmap #(apply str %)
            (gen/vector (gen/elements text-chars) min-len max-len)))

(def ^:private literal-string-gen
  (non-tilde-string-gen 0 10))

(def ^:private non-empty-string-gen
  (non-tilde-string-gen 1 10))

(def ^:private small-int-gen
  (gen/choose -500 500))

(def ^:private non-negative-int-gen
  (gen/choose 0 500))

(def ^:private positive-int-gen
  (gen/choose 1 12))

(def ^:private printable-char-gen
  (gen/elements printable-chars))

(def ^:private scalar-arg-gen
  (gen/one-of
    [non-empty-string-gen
     small-int-gen
     gen/boolean
     (gen/elements scalar-keywords)]))

(def ^:private decimal-number-gen
  (gen/fmap (fn [[whole frac neg?]]
              (let [n (+ whole (/ frac 1000.0))]
                (if neg? (- n) n)))
            (gen/tuple (gen/choose 0 5000)
                       (gen/choose 0 999)
                       gen/boolean)))

(defn- simple-form
  [kw opts]
  (if (seq opts) [kw opts] kw))

(defn- body-form
  [kw opts children]
  (if (seq opts)
    (into [kw opts] children)
    (into [kw] children)))

(defn- clause-value-gen
  [element-gen]
  (gen/one-of
    [(gen/return nil)
     literal-string-gen
     element-gen
     (gen/fmap vec (gen/vector element-gen 2 3))]))

(defn- padded-opts-gen []
  (gen/fmap (fn [[width fill left? case-mode]]
              (cond-> {:width width :fill fill}
                left?     (assoc :pad :left)
                case-mode (assoc :case case-mode)))
            (gen/tuple positive-int-gen
                       (gen/elements fill-chars)
                       gen/boolean
                       (gen/frequency [[4 (gen/return nil)]
                                       [1 (gen/elements case-modes)]]))))

(defn- char-opts-gen []
  (gen/elements
    [{} {:name true} {:readable true} {:name true :readable true}]))

(defn- integer-opts-gen []
  (gen/fmap (fn [[width fill group? sign]]
              (cond-> {:width width :fill fill}
                group? (assoc :group true)
                sign   (assoc :sign :always)))
            (gen/tuple positive-int-gen
                       (gen/elements fill-chars)
                       gen/boolean
                       gen/boolean)))

(defn- float-opts-gen []
  (gen/fmap (fn [[width decimals case-mode]]
              (cond-> {:width width :decimals decimals}
                case-mode (assoc :case case-mode)))
            (gen/tuple (gen/choose 1 12)
                       (gen/choose 0 4)
                       (gen/frequency [[5 (gen/return nil)]
                                       [1 (gen/elements case-modes)]]))))

(defn- money-opts-gen []
  (gen/fmap (fn [[width decimals sign?]]
              (cond-> {:width width :decimals decimals}
                sign? (assoc :sign :always)))
            (gen/tuple (gen/choose 1 10)
                       (gen/choose 0 4)
                       gen/boolean)))

(defn- tab-opts-gen []
  (gen/fmap (fn [[col relative?]]
              (cond-> {:col col}
                relative? (assoc :relative true)))
            (gen/tuple (gen/choose 1 12) gen/boolean)))

(defn- indent-opts-gen []
  (gen/fmap (fn [[n relative?]]
              (cond-> {:n n}
                relative? (assoc :relative-to :current)))
            (gen/tuple (gen/choose 1 5) gen/boolean)))

(defn- break-opts-gen []
  (gen/elements [{:mode :fill} {:mode :miser} {:mode :mandatory}]))

(defn- compile-valid-leaf-element-gen []
  (gen/one-of
    [literal-string-gen
     (gen/elements simple-no-arg-keywords)
     (gen/fmap #(simple-form :str %) (padded-opts-gen))
     (gen/fmap #(simple-form :pr %) (padded-opts-gen))
     (gen/fmap #(simple-form :char %) (char-opts-gen))
     (gen/fmap #(simple-form (first %) (second %))
               (gen/tuple (gen/elements [:int :bin :oct :hex])
                          (integer-opts-gen)))
     (gen/fmap #(simple-form (first %) (second %))
               (gen/tuple (gen/elements [:float :exp :gfloat])
                          (float-opts-gen)))
     (gen/fmap #(simple-form :money %) (money-opts-gen))
     (gen/fmap #(simple-form :tab %) (tab-opts-gen))
     (gen/fmap #(simple-form :indent %) (indent-opts-gen))
     (gen/fmap #(simple-form :break %) (break-opts-gen))
     (gen/return [:plural {:rewind true}])]))

(def compile-valid-element-gen
  (gen/recursive-gen
    (fn [inner]
      (gen/one-of
        [(gen/fmap (fn [[sep body]]
                     (body-form :each {:sep sep} body))
                   (gen/tuple non-empty-string-gen
                              (gen/vector inner 0 3)))
         (gen/fmap (fn [[then else]]
                     [:if then else])
                   (gen/tuple (clause-value-gen inner)
                              (clause-value-gen inner)))
         (gen/fmap (fn [[clauses default]]
                     (if default
                       (into [:choose {:default default}] clauses)
                       (into [:choose] clauses)))
                   (gen/tuple (gen/vector (clause-value-gen inner) 2 4)
                              (gen/frequency [[3 (gen/return nil)]
                                              [1 (clause-value-gen inner)]])))
         (gen/fmap (fn [body]
                     (into [:when] body))
                   (gen/vector inner 1 3))
         (gen/fmap (fn [[width clauses]]
                     [:justify {:width width} (nth clauses 0) (nth clauses 1)])
                   (gen/tuple (gen/choose 6 24)
                              (gen/vector (clause-value-gen inner) 2 2)))
         (gen/fmap (fn [[prefix body suffix]]
                     [:logical-block prefix (vec body) suffix])
                   (gen/tuple literal-string-gen
                              (gen/vector inner 2 3)
                              literal-string-gen))]))
    (compile-valid-leaf-element-gen)))

(def ^:private compile-valid-root-gen
  (gen/vector compile-valid-element-gen 0 3))

(def compile-valid-dsl-gen
  (gen/fmap vec compile-valid-root-gen))

(defn- executable-element
  [dsl args]
  {:dsl dsl :args args})

(defn- map-case
  [f g]
  (gen/fmap (fn [[x y]] (f x y)) g))

(defn- executable-leaf-gen []
  (gen/one-of
    [(gen/fmap #(executable-element % [])
               (gen/fmap vector literal-string-gen))
     (gen/fmap (fn [[arg opts]]
                 (executable-element [(simple-form :str opts)] [arg]))
               (gen/tuple scalar-arg-gen (padded-opts-gen)))
     (gen/fmap (fn [[arg opts]]
                 (executable-element [(simple-form :pr opts)] [arg]))
               (gen/tuple scalar-arg-gen (padded-opts-gen)))
     (gen/fmap (fn [[arg opts]]
                 (executable-element [(simple-form :char opts)] [arg]))
               (gen/tuple printable-char-gen (char-opts-gen)))
     (gen/fmap (fn [[kw arg opts]]
                 (executable-element [(simple-form kw opts)] [arg]))
               (gen/tuple (gen/elements [:int :bin :oct :hex])
                          non-negative-int-gen
                          (integer-opts-gen)))
     (gen/fmap (fn [[kw arg]]
                 (executable-element [kw] [arg]))
               (gen/tuple (gen/elements [:cardinal :ordinal :roman :old-roman])
                          non-negative-int-gen))
     (gen/fmap (fn [[kw arg opts]]
                 (executable-element [(simple-form kw opts)] [arg]))
               (gen/tuple (gen/elements [:float :exp :gfloat])
                          decimal-number-gen
                          (float-opts-gen)))
     (gen/fmap (fn [[arg opts]]
                 (executable-element [(simple-form :money opts)] [arg]))
               (gen/tuple decimal-number-gen (money-opts-gen)))
     (gen/fmap (fn [n]
                 (executable-element [[:int " item" [:plural {:rewind true}]]] [n]))
               non-negative-int-gen)
     (gen/fmap (fn [[n plural-form]]
                 (executable-element [[:int " famil" [:plural {:rewind true :form plural-form}]]] [n]))
               (gen/tuple non-negative-int-gen (gen/return :ies)))
     (gen/fmap (fn [[truthy then else]]
                 (executable-element [[:if then else]] [truthy]))
               (gen/tuple gen/boolean literal-string-gen literal-string-gen))
     (gen/fmap (fn [[selector clauses default]]
                 (let [opts (cond-> {}
                              default (assoc :default default))]
                   (executable-element [(if (seq opts)
                                          (into [:choose opts] clauses)
                                          (into [:choose] clauses))]
                                      [selector])))
               (gen/tuple (gen/choose 0 5)
                          (gen/vector literal-string-gen 2 4)
                          (gen/frequency [[3 (gen/return nil)]
                                          [1 literal-string-gen]])))
     (gen/fmap (fn [[sep items]]
                 (executable-element [[:each {:sep sep} :int]] [items]))
               (gen/tuple non-empty-string-gen
                          (gen/vector non-negative-int-gen 0 4)))
     (gen/fmap (fn [[sep items]]
                 (executable-element [[:each {:sep sep} :str]] [items]))
               (gen/tuple non-empty-string-gen
                          (gen/vector non-empty-string-gen 0 4)))
     (gen/fmap (fn [[width left right]]
                 (executable-element [[:justify {:width width} left right]] []))
               (gen/tuple (gen/choose 6 24) literal-string-gen literal-string-gen))
     (gen/fmap (fn [[a b c]]
                 (executable-element [[:logical-block "rgb(" [:int ", " :int ", " :int] ")"]]
                                     [[a b c]]))
               (gen/tuple (gen/choose 0 255) (gen/choose 0 255) (gen/choose 0 255)))
     (gen/fmap (fn [[start end]]
                 (executable-element [[:logical-block "range[" [:int ", " :int] "]"]]
                                     [[start end]]))
               (gen/tuple small-int-gen small-int-gen))]))

(def executable-case-gen
  (gen/fmap (fn [cases]
              {:dsl  (vec (mapcat :dsl cases))
               :args (vec (mapcat :args cases))})
            (gen/vector (executable-leaf-gen) 0 4)))

(def invalid-dsl-gen
  (gen/one-of
    [(gen/return 42)
     (gen/return [1])
     (gen/return [[:bogus]])
     (gen/return [[:if]])
     (gen/return [[:downcase {:foo true} :str]])
     (gen/fmap (fn [n] [[:str n]]) small-int-gen)
     (gen/fmap (fn [k] [[k]])
               (gen/fmap #(keyword (str "unknown-" %))
                         (gen/choose 0 1000)))]))

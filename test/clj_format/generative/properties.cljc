(ns clj-format.generative.properties
  "Shared test.check properties for parser/compiler/runtime invariants."
  (:require [clj-format.compiler :as compiler]
            [clj-format.core :as core]
            [clj-format.parser :as parser]))

(defn canonicalize-dsl
  "Compile a valid DSL form and parse it back into the parser's canonical DSL."
  [dsl]
  (parser/parse-format (compiler/compile-format dsl)))

(defn canonical-form-stable?
  "Valid compileable DSL should canonicalize to a parse/compile fixed point."
  [dsl]
  (let [canonical (canonicalize-dsl dsl)]
    (= canonical (canonicalize-dsl canonical))))

(defn canonical-compile-idempotent?
  "The compiler's string output should be stable once a DSL form has been
   canonicalized through the parser."
  [dsl]
  (let [fmt (compiler/compile-format (canonicalize-dsl dsl))]
    (= fmt (compiler/compile-format (parser/parse-format fmt)))))

(defn execution-equivalent?
  [{:keys [dsl args]}]
  (let [fmt (compiler/compile-format dsl)]
    (= (apply core/clj-format nil dsl args)
       (apply core/clj-format nil fmt args))))

(defn invalid-dsl-error?
  [dsl]
  (try
    (compiler/compile-format dsl)
    false
    (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core.ExceptionInfo) e
      (and (= :clj-format (:library (ex-data e)))
           (= :compile (:phase (ex-data e)))
           (keyword? (:kind (ex-data e)))))))

(ns clj-format.generative-test
  "Property-based tests for DSL canonicalization, runtime execution, and
   structured invalid-DSL failures."
  (:require [clj-format.generative.generators :as gg]
            [clj-format.generative.properties :as gp]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop])
  )

(defn- passing-check?
  [result]
  (is (:result result) (pr-str result)))

(deftest known-noncanonical-dsl-forms-test
  (testing "valid DSL can canonicalize to an equivalent, different shape"
    (doseq [dsl [[[:int " item" [:plural {:rewind true}]]]
                 [[:if ["" ""] ""]]
                 [[:logical-block "" ["" [:choose {:default ""} nil nil]] ""]]]]
      (let [canonical (gp/canonicalize-dsl dsl)]
        (is (not= dsl canonical))
        (is (= canonical (gp/canonicalize-dsl canonical)))))))

(deftest canonical-form-stable-spec
  (passing-check?
    (tc/quick-check 100
      (prop/for-all [dsl gg/compile-valid-dsl-gen]
        (gp/canonical-form-stable? dsl)))))

(deftest canonical-compile-idempotent-spec
  (passing-check?
    (tc/quick-check 100
      (prop/for-all [dsl gg/compile-valid-dsl-gen]
        (gp/canonical-compile-idempotent? dsl)))))

(deftest execution-equivalence-spec
  (passing-check?
    (tc/quick-check 100
      (prop/for-all [format-case gg/executable-case-gen]
        (gp/execution-equivalent? format-case)))))

(deftest invalid-dsl-structured-error-spec
  (passing-check?
    (tc/quick-check 60
      (prop/for-all [dsl gg/invalid-dsl-gen]
        (gp/invalid-dsl-error? dsl)))))

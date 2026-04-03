(ns clj-format.bb-runner
  (:require [clojure.test :as test]
            [clj-format.core-test]
            [clj-format.compiler-test]
            [clj-format.examples-test]
            [clj-format.generative-test]
            [clj-format.host-parity-test]
            [clj-format.parser-test]))

(defn -main
  [& _]
  (let [summary (test/run-tests 'clj-format.compiler-test
                                'clj-format.core-test
                                'clj-format.examples-test
                                'clj-format.generative-test
                                'clj-format.host-parity-test
                                'clj-format.parser-test)]
    (System/exit (if (zero? (+ (:fail summary) (:error summary))) 0 1))))

(-main)

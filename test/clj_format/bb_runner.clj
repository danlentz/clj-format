(ns clj-format.bb-runner
  (:require [clojure.test :as test]
            [clj-format.core-test]
            [clj-format.compiler-test]
            [clj-format.examples-test]
            [clj-format.generative-test]
            [clj-format.host-parity-test]
            [clj-format.parser-test]
            [clj-format.table-test]))

(defn -main
  [& _]
  (let [summary (test/run-tests 'clj-format.compiler-test
                                'clj-format.core-test
                                'clj-format.examples-test
                                'clj-format.generative-test
                                'clj-format.host-parity-test
                                'clj-format.parser-test
                                'clj-format.table-test)]
    (System/exit (if (zero? (+ (:fail summary) (:error summary))) 0 1))))

;; Auto-run when loaded directly by Babashka (`bb bb_runner.clj`).
;; Guarded so that other runners (lein test, clojure -X:test, etc.) that
;; happen to scan the test directory do not trip the System/exit call
;; and hijack their own test invocations.
(when (System/getProperty "babashka.version")
  (-main))

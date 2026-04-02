(ns clj-format.cljs-runner
  (:require [cljs.test :as test]
            [clj-format.compiler-test]
            [clj-format.core-test]
            [clj-format.examples-test]
            [clj-format.generative-test]
            [clj-format.host-parity-test]
            [clj-format.parser-test]))

(defn- exit!
  [summary]
  (when-let [process (.-process js/globalThis)]
    (.exit process (if (test/successful? summary) 0 1))))

(defmethod test/report [::test/default :end-run-tests] [summary]
  (exit! summary))

(defn -main []
  (test/run-tests 'clj-format.compiler-test
                  'clj-format.core-test
                  'clj-format.examples-test
                  'clj-format.generative-test
                  'clj-format.host-parity-test
                  'clj-format.parser-test))

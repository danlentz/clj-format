(ns clj-format.cljs-runner
  (:require [cljs.test :as test]
            [clj-format.compiler-test]
            [clj-format.core-test]
            [clj-format.examples-test]
            [clj-format.parser-test]))

(defn -main []
  (test/run-tests 'clj-format.compiler-test
                  'clj-format.core-test
                  'clj-format.examples-test
                  'clj-format.parser-test))

(set! *main-cli-fn* -main)

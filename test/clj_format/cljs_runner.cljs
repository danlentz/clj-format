(ns clj-format.cljs-runner
  (:require [cljs.test :as test]
            [clj-format.compiler-test]
            [clj-format.core-test]
            [clj-format.examples-test]
            [clj-format.generative-test]
            [clj-format.host-parity-test]
            [clj-format.parser-test]
            [clj-format.table-test]))

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
                  'clj-format.parser-test
                  'clj-format.table-test))

;; bin/test-cljs uses `-c` (compile-only) and then runs the output via
;; node. cljs.nodejscli invokes *main-cli-fn* on startup, so we wire it
;; up explicitly at load time.
(set! *main-cli-fn* -main)

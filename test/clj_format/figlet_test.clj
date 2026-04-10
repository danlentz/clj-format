(ns clj-format.figlet-test
  "Tests for the optional :figlet DSL directive.

  Loading this namespace requires clj-format.figlet, which installs
  the preprocessor hook. Tests verify rendering, DSL integration, and
  error handling."
  (:require [clojure.test    :refer [deftest is testing]]
            [clojure.string  :as str]
            [clj-format.core :as fmt]
            [clj-format.figlet :as figlet]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic Expansion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest expand-simple-form-test
  (testing "A [:figlet text] form expands to a multi-line string"
    (let [result (figlet/expand [:figlet "Hi"])]
      (is (string? result))
      (is (str/includes? result "\n"))
      (is (> (count (str/split-lines result)) 1)))))

(deftest expand-with-font-option-test
  (testing "The :font option selects an alternate font"
    (let [standard (figlet/expand [:figlet "X"])
          small    (figlet/expand [:figlet {:font "small"} "X"])]
      (is (not= standard small)
          "different fonts produce different output"))))

(deftest expand-passthrough-test
  (testing "Non-figlet forms pass through unchanged"
    (is (= [:str] (figlet/expand [:str])))
    (is (= ["hello" :str] (figlet/expand ["hello" :str])))
    (is (= [:each {:sep ", "} :str]
           (figlet/expand [:each {:sep ", "} :str])))))

(deftest expand-nested-test
  (testing "figlet forms nested inside other DSL forms are expanded"
    (let [result (figlet/expand [[:figlet "Hi"] :nl "done"])]
      (is (vector? result))
      (is (= 3 (count result)))
      (is (string? (first result)))
      (is (str/includes? (first result) "\n")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integration with clj-format
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest clj-format-dispatches-figlet-test
  (testing "clj-format preprocesses :figlet into banner text"
    (let [result (fmt/clj-format nil [:figlet {:font "small"} "OK"])]
      (is (string? result))
      (is (str/includes? result "\n")))))

(deftest clj-format-mixed-body-test
  (testing "figlet can sit alongside normal DSL elements"
    (let [result (fmt/clj-format nil
                                 [[:figlet {:font "small"} "HI"]
                                  :nl "name: " :str]
                                 "Alice")]
      (is (str/includes? result "Alice"))
      (is (str/includes? result "name: ")))))

(deftest clj-format-prints-figlet-test
  (testing "true writer prints figlet output"
    (let [output (with-out-str
                   (fmt/clj-format true [:figlet {:font "small"} "Hi"]))]
      (is (str/includes? output "\n"))
      (is (not (str/blank? output))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Error Handling
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest empty-body-error-test
  (testing ":figlet with no body throws a helpful error"
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"requires at least one body string"
          (figlet/expand [:figlet])))
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"requires at least one body string"
          (figlet/expand [:figlet {:font "small"}])))))

(deftest non-string-body-error-test
  (testing ":figlet rejects non-string body elements"
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"literal strings"
          (figlet/expand [:figlet :str])))
    (is (thrown-with-msg?
          clojure.lang.ExceptionInfo
          #"literal strings"
          (figlet/expand [:figlet {:font "small"} "Hello" :str])))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Preprocessor Installation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest preprocessor-installed-test
  (testing "Loading clj-format.figlet installs expand as the preprocessor"
    (is (= figlet/expand fmt/*dsl-preprocessor*))))

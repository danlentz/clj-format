(ns clj-format.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [cl-format]]
            [clj-format.core :refer [clj-format]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; String Passthrough
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest string-passthrough-test
  (testing "identical to cl-format when given a string"
    (is (= (cl-format nil "~A" 42)
           (clj-format nil "~A" 42)))
    (is (= (cl-format nil "~:D" 1000000)
           (clj-format nil "~:D" 1000000)))
    (is (= (cl-format nil "~{~A~^, ~}" [1 2 3])
           (clj-format nil "~{~A~^, ~}" [1 2 3])))
    (is (= (cl-format nil "~:[no~;yes~]" true)
           (clj-format nil "~:[no~;yes~]" true)))
    (is (= (cl-format nil "Hello ~A!" "world")
           (clj-format nil "Hello ~A!" "world")))))

(deftest string-passthrough-complex-test
  (testing "complex format strings pass through correctly"
    (is (= (cl-format nil "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 1 [46])
           (clj-format nil "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 1 [46])))
    (is (= (cl-format nil "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%" "br" [] true)
           (clj-format nil "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%" "br" [] true)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DSL Vector Format
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest dsl-simple-test
  (is (= "42" (clj-format nil [:str] 42)))
  (is (= "42" (clj-format nil [:int] 42)))
  (is (= "\"foo\"" (clj-format nil [:pr] "foo")))
  (is (= "3.14" (clj-format nil [:money] 3.14159)))
  (is (= "\n" (clj-format nil [:nl]))))

(deftest dsl-with-opts-test
  (is (= "    42" (clj-format nil [[:int {:width 6}]] 42)))
  (is (= "1,000,000" (clj-format nil [[:int {:group true}]] 1000000)))
  (is (= "  3.14" (clj-format nil [[:float {:width 6 :decimals 2}]] 3.14159)))
  (is (= "00ff" (clj-format nil [[:hex {:width 4 :fill \0}]] 255))))

(deftest dsl-mixed-body-test
  (is (= "Hello world!" (clj-format nil ["Hello " :str "!"] "world")))
  (is (= "Name: Alice, Age: 30"
         (clj-format nil ["Name: " :str ", Age: " :int] "Alice" 30))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DSL Compound Directives
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest dsl-each-test
  (is (= "1, 2, 3" (clj-format nil [[:each {:sep ", "} :str]] [1 2 3])))
  (is (= "" (clj-format nil [[:each {:sep ", "} :str]] [])))
  (is (= "1" (clj-format nil [[:each {:sep ", "} :str]] [1]))))

(deftest dsl-if-test
  (is (= "yes" (clj-format nil [[:if "yes" "no"]] true)))
  (is (= "no" (clj-format nil [[:if "yes" "no"]] nil))))

(deftest dsl-when-test
  (is (= "val: 42" (clj-format nil [[:when "val: " :str]] 42)))
  (is (= "" (clj-format nil [[:when "val: " :str]] nil))))

(deftest dsl-choose-test
  (is (= "uno" (clj-format nil [[:choose "cero" "uno" "dos"]] 1)))
  (is (= "mucho" (clj-format nil [[:choose {:default "mucho"} "cero" "uno"]] 99))))

(deftest dsl-case-test
  (is (= "THE QUICK BROWN FOX"
         (clj-format nil [[:str {:case :upcase}]] "the quick brown fox")))
  (is (= "The Quick Brown Fox"
         (clj-format nil [[:str {:case :capitalize}]] "tHe Quick BROWN foX")))
  (is (= "xlii" (clj-format nil [[:roman {:case :downcase}]] 42))))

(deftest dsl-justify-test
  (is (= "foo    bar" (clj-format nil [[:justify {:width 10} "foo" "bar"]]))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DSL Bare Keyword Format
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest bare-keyword-test
  (testing "single keyword as format spec"
    (is (= "42" (clj-format nil :str 42)))
    (is (= "42" (clj-format nil :int 42)))
    (is (= "forty-two" (clj-format nil :cardinal 42)))
    (is (= "XLII" (clj-format nil :roman 42)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Writer Variants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest nil-writer-test
  (testing "nil writer returns string"
    (is (= "hello" (clj-format nil "~A" "hello")))
    (is (string? (clj-format nil "~A" "hello")))))

(deftest false-writer-test
  (testing "false writer returns string"
    (is (= "hello" (clj-format false "~A" "hello")))))

(deftest true-writer-test
  (testing "true writer prints to *out*"
    (is (= "hello" (with-out-str (clj-format true "~A" "hello"))))))

(deftest stream-writer-test
  (testing "java.io.Writer receives output"
    (let [sw (java.io.StringWriter.)]
      (clj-format sw "~A" "hello")
      (is (= "hello" (.toString sw))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Error Handling
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest invalid-format-spec-test
  (is (thrown? clojure.lang.ExceptionInfo
               (clj-format nil 42 "bad"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Equivalence: DSL produces same output as format string
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest dsl-string-equivalence-test
  (testing "DSL and string produce identical output"
    (is (= (clj-format nil "~D item~:P" 5)
           (clj-format nil [:int " item" [:plural {:rewind true}]] 5)))
    (is (= (clj-format nil "~{~A~^, ~}" [1 2 3])
           (clj-format nil [[:each {:sep ", "} :str]] [1 2 3])))
    (is (= (clj-format nil "~:[no~;yes~]" true)
           (clj-format nil [[:if "yes" "no"]] true)))
    (is (= (clj-format nil "~:(~A~)" "hello world")
           (clj-format nil [[:str {:case :capitalize}]] "hello world")))
    (is (= (clj-format nil "~R" 42)
           (clj-format nil [:cardinal] 42)))
    (is (= (clj-format nil "~@R" 42)
           (clj-format nil [:roman] 42)))
    (is (= (clj-format nil "~8,'0B" 42)
           (clj-format nil [[:bin {:width 8 :fill \0}]] 42)))))

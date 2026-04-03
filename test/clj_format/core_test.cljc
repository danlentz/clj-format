(ns clj-format.core-test
  "Tests the clj-format public API: string passthrough, DSL dispatch,
   writer variants, and DSL/string equivalence."
  (:require [clj-format.core :refer [clj-format compile-format parse-format]]
            [clj-format.test-support :as support]
            [#?(:clj clojure.test :cljs cljs.test) :refer [deftest is testing]]
            [#?(:clj clojure.pprint :cljs cljs.pprint) :refer [cl-format]]))


(deftest exposed-helpers-test
  (is (= [:str] (parse-format "~A")))
  (is (= ["Hello " :str "!"] (parse-format "Hello ~A!")))
  (is (= "~A" (compile-format :str)))
  (is (= "~R file~:P"
         (compile-format [:cardinal " file" [:plural {:rewind true}]]))))


(deftest string-passthrough-test
  (testing "string format specs delegate directly to cl-format"
    (is (= (cl-format nil "~A" 42) (clj-format nil "~A" 42)))
    (is (= (cl-format nil "~:D" 1000000) (clj-format nil "~:D" 1000000)))
    (is (= (cl-format nil "~{~A~^, ~}" [1 2 3]) (clj-format nil "~{~A~^, ~}" [1 2 3])))
    (is (= (cl-format nil "~:[no~;yes~]" true) (clj-format nil "~:[no~;yes~]" true)))
    (is (= (cl-format nil "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 1 [46])
           (clj-format nil "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 1 [46])))))

(deftest dsl-vector-test
  (testing "vector format specs compile and produce correct output"
    (is (= "42" (clj-format nil [:str] 42)))
    (is (= "1,000,000" (clj-format nil [[:int {:group true}]] 1000000)))
    (is (= "Hello world!" (clj-format nil ["Hello " :str "!"] "world")))
    (is (= "1, 2, 3" (clj-format nil [[:each {:sep ", "} :str]] [1 2 3])))
    (is (= "yes" (clj-format nil [[:if "yes" "no"]] true)))
    (is (= "no" (clj-format nil [[:if "yes" "no"]] nil)))
    (is (= "THE QUICK BROWN FOX" (clj-format nil [[:str {:case :upcase}]] "the quick brown fox")))
    (is (= "foo    bar" (clj-format nil [[:justify {:width 10} "foo" "bar"]])))))

(deftest bare-keyword-test
  (testing "bare keyword format specs"
    (is (= "42" (clj-format nil :str 42)))
    (is (= "forty-two" (clj-format nil :cardinal 42)))
    (is (= "XLII" (clj-format nil :roman 42)))))

(deftest writer-variants-test
  (is (= "hello" (clj-format nil "~A" "hello")) "nil returns string")
  (is (= "hello" (clj-format false "~A" "hello")) "false returns string")
  (is (= "hello" (with-out-str (clj-format true "~A" "hello"))) "true prints to *out*")
  (let [sw (support/make-writer)]
    (clj-format sw "~A" "hello")
    (is (= "hello" (support/writer-content sw)) "Writer receives output")))

(deftest invalid-format-spec-test
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core.ExceptionInfo)
               (clj-format nil 42 "bad")))
  (try
    (clj-format nil 42 "bad")
    (is false "expected ExceptionInfo")
    (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core.ExceptionInfo) e
      (is (= {:library :clj-format
              :phase :api
              :kind :invalid-format-spec
              :fmt 42}
             (ex-data e))))))

(deftest invalid-output-target-test
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core.ExceptionInfo)
               (clj-format 42 "~A" "bad")))
  (try
    (clj-format 42 "~A" "bad")
    (is false "expected ExceptionInfo")
    (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core.ExceptionInfo) e
      (is (= {:library :clj-format
              :phase :api
              :kind :invalid-output-target
              :target 42
              :platform support/platform}
             (ex-data e))))))

(deftest dsl-string-equivalence-test
  (testing "DSL and string produce identical output"
    (is (= (clj-format nil "~D item~:P" 5)
           (clj-format nil [:int " item" [:plural {:rewind true}]] 5)))
    (is (= (clj-format nil "~{~A~^, ~}" [1 2 3])
           (clj-format nil [[:each {:sep ", "} :str]] [1 2 3])))
    (is (= (clj-format nil "~R" 42)
           (clj-format nil [:cardinal] 42)))
    (is (= (clj-format nil "~@R" 42)
           (clj-format nil [:roman] 42)))
    (is (= (clj-format nil "~8,'0B" 42)
           (clj-format nil [[:bin {:width 8 :fill \0}]] 42)))))

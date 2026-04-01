(ns clj-format.parser-test
  "Tests the parser: parameter parsing, flag translation, every directive,
   compound nesting, and edge cases."
  (:require [clojure.test :refer :all]
            [clj-format.parser :refer [parse-format]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parameters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-params-test
  (is (= [[:str {:width 10}]] (parse-format "~10A")))
  (is (= [[:float {:scale -1}]] (parse-format "~,,-1F")))
  (is (= [[:int {:fill \0}]] (parse-format "~,'0D")))
  (is (= [[:str {:width :V}]] (parse-format "~vA")))
  (is (= [[:str {:width :#}]] (parse-format "~#A")))
  (is (= [[:str {:width 10 :min-pad 3}]] (parse-format "~10,,3A")))
  (is (= [[:str {:width :V :pad-step :# :min-pad 1 :fill \-}]]
         (parse-format "~V,#,1,'-A"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Semantic Flags
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-flags-test
  (is (= [[:int {:group true}]] (parse-format "~:D")))
  (is (= [[:int {:sign :always}]] (parse-format "~@D")))
  (is (= [[:int {:group true :sign :always}]] (parse-format "~:@D")))
  (is (= [[:int {:group true :sign :always}]] (parse-format "~@:D")))
  (is (= [[:str {:pad :left}]] (parse-format "~@A")))
  (is (= [[:write {:pretty true}]] (parse-format "~:W")))
  (is (= [[:tab {:col 4 :relative true}]] (parse-format "~4@T")))
  (is (= [[:plural {:rewind true :form :ies}]] (parse-format "~:@P")))
  (is (= [[:char {:name true}]] (parse-format "~:C")))
  (is (= [[:char {:readable true}]] (parse-format "~@C")))
  (is (= [[:char {:name true :readable true}]] (parse-format "~:@C"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Every Simple Directive
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-all-simple-directives-test
  (doseq [[fmt expected]
          [["~A" :str] ["~S" :pr] ["~W" :write] ["~C" :char]
           ["~D" :int] ["~B" :bin] ["~O" :oct] ["~X" :hex] ["~P" :plural]
           ["~F" :float] ["~E" :exp] ["~G" :gfloat] ["~$" :money]
           ["~%" :nl] ["~&" :fresh] ["~|" :page] ["~T" :tab] ["~~" :tilde]
           ["~?" :recur] ["~^" :stop] ["~I" :indent] ["~*" :skip]
           ["~:*" :back] ["~@*" :goto]
           ["~R" :cardinal] ["~:R" :ordinal] ["~@R" :roman] ["~:@R" :old-roman]
           ["~_" :break]]]
    (is (= [expected] (parse-format fmt)) (str "directive: " fmt)))
  ;; Case insensitive
  (is (= [:str] (parse-format "~a")))
  (is (= [:int] (parse-format "~d"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Compound Directives
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-each-test
  (is (= [[:each :str " "]] (parse-format "~{~A ~}")))
  (is (= [[:each {:sep ", "} :str]] (parse-format "~{~A~^, ~}")))
  (is (= [[:each {:from :sublists} :str " "]] (parse-format "~:{~A ~}")))
  (is (= [[:each {:from :rest} :str " "]] (parse-format "~@{~A ~}")))
  (is (= [[:each {:from :rest-sublists} :str " "]] (parse-format "~:@{~A ~}")))
  (is (= [[:each {:max 5} :str ", "]] (parse-format "~5{~A, ~}")))
  (is (= [[:each {:min 1} :str]] (parse-format "~{~A~:}")))
  (is (= [[:each]] (parse-format "~{~}")))
  (testing "sep not detected when escape is mid-body"
    (is (= [[:each :str :stop ", " :int]] (parse-format "~{~A~^, ~D~}")))))

(deftest parse-conditionals-test
  (is (= [[:choose "zero" "one" "two"]] (parse-format "~[zero~;one~;two~]")))
  (is (= [[:choose {:default "other"} "zero" "one"]] (parse-format "~[zero~;one~:;other~]")))
  (is (= [[:choose {:selector 1} "a" "b"]] (parse-format "~1[a~;b~]")))
  (is (= [[:choose nil nil nil]] (parse-format "~[~;~;~]")))
  (is (= [[:if "yes" "no"]] (parse-format "~:[no~;yes~]")))
  (is (= [[:if :int :str]] (parse-format "~:[~A~;~D~]")))
  (is (= [[:when "yes: " :str]] (parse-format "~@[yes: ~A~]"))))

(deftest parse-case-conversion-test
  (is (= [[:str {:case :downcase}]] (parse-format "~(~A~)")))
  (is (= [[:str {:case :capitalize}]] (parse-format "~:(~A~)")))
  (is (= [[:str {:case :titlecase}]] (parse-format "~@(~A~)")))
  (is (= [[:str {:case :upcase}]] (parse-format "~:@(~A~)")))
  (is (= [[:str {:width 10 :case :capitalize}]] (parse-format "~:(~10A~)")))
  (is (= [[:each {:sep ", " :case :capitalize} :str]] (parse-format "~:(~{~A~^, ~}~)")))
  (is (= [[:downcase "the " :str " is " :str]] (parse-format "~(the ~A is ~A~)")))
  (is (= [[:capitalize [:str {:case :downcase}]]] (parse-format "~:(~(~A~)~)"))))

(deftest parse-justify-test
  (is (= [[:justify "left" "right"]] (parse-format "~<left~;right~>")))
  (is (= [[:justify {:width 40} "left" "right"]] (parse-format "~40<left~;right~>")))
  (is (= [[:justify {:pad-before true} "a" "b"]] (parse-format "~:<a~;b~>")))
  (is (= [[:justify {:pad-after true} "a" "b"]] (parse-format "~@<a~;b~>")))
  (is (= [[:justify {:pad-before true :pad-after true} "a" "b"]] (parse-format "~:@<a~;b~>")))
  (is (= [[:logical-block :str]] (parse-format "~<~A~:>")))
  (is (= [[:logical-block "(" :str ")"]] (parse-format "~<(~;~A~;)~:>"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Nesting and Composition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-nesting-test
  (is (= [[:each [:if "y" "n"] " "]] (parse-format "~{~:[n~;y~] ~}")))
  (is (= [[:each {:from :sublists} [:each :str " "] :nl]] (parse-format "~:{~{~A ~}~%~}")))
  (is (= [[:each [:if {:case :upcase} "yes" "no"] " "]] (parse-format "~{~:@(~:[no~;yes~]~) ~}")))
  (is (= [[:choose "none" [:each {:sep ", "} :str]]] (parse-format "~[none~;~{~A~^, ~}~]")))
  (is (= [[:each {:from :sublists}
           [:each [:str {:case :capitalize}] " "] :nl]]
         (parse-format "~:{~{~:(~A~) ~}~%~}"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Edge Cases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-mixed-body-test
  (is (= ["Hello " :str "!"] (parse-format "Hello ~A!")))
  (is (= [:str :int :float] (parse-format "~A~D~F")))
  (is (= [] (parse-format "")))
  (is (= ["just text"] (parse-format "just text"))))

(deftest parse-format-newline-test
  (is (= ["a" "b"] (parse-format "a~\n   b")))
  (is (= ["a" "   b"] (parse-format "a~:\n   b")))
  (is (= ["a" :nl "b"] (parse-format "a~@\n   b"))))

(deftest parse-unknown-directive-test
  (is (thrown? clojure.lang.ExceptionInfo (parse-format "~Q"))))

(deftest parse-invalid-special-flags-test
  (is (thrown? clojure.lang.ExceptionInfo (parse-format "~:@*"))))

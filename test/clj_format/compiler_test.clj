(ns clj-format.compiler-test
  (:require [clojure.test :refer :all]
            [clj-format.compiler :refer [compile-format]]
            [clj-format.parser :refer [parse-format]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Compilation Unit Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest compile-simple-directives-test
  (is (= "~A" (compile-format [:str])))
  (is (= "~S" (compile-format [:pr])))
  (is (= "~W" (compile-format [:write])))
  (is (= "~C" (compile-format [:char])))
  (is (= "~D" (compile-format [:int])))
  (is (= "~B" (compile-format [:bin])))
  (is (= "~O" (compile-format [:oct])))
  (is (= "~X" (compile-format [:hex])))
  (is (= "~P" (compile-format [:plural])))
  (is (= "~F" (compile-format [:float])))
  (is (= "~E" (compile-format [:exp])))
  (is (= "~G" (compile-format [:gfloat])))
  (is (= "~$" (compile-format [:money])))
  (is (= "~%" (compile-format [:nl])))
  (is (= "~&" (compile-format [:fresh])))
  (is (= "~|" (compile-format [:page])))
  (is (= "~T" (compile-format [:tab])))
  (is (= "~~" (compile-format [:tilde])))
  (is (= "~?" (compile-format [:recur])))
  (is (= "~^" (compile-format [:stop])))
  (is (= "~I" (compile-format [:indent])))
  (is (= "~*" (compile-format [:skip]))))

(deftest compile-special-directives-test
  (is (= "~R" (compile-format [:cardinal])))
  (is (= "~:R" (compile-format [:ordinal])))
  (is (= "~@R" (compile-format [:roman])))
  (is (= "~:@R" (compile-format [:old-roman])))
  (is (= "~:*" (compile-format [:back])))
  (is (= "~2:*" (compile-format [[:back {:n 2}]])))
  (is (= "~@*" (compile-format [:goto])))
  (is (= "~5@*" (compile-format [[:goto {:n 5}]])))
  (is (= "~_" (compile-format [:break])))
  (is (= "~:_" (compile-format [[:break {:mode :fill}]])))
  (is (= "~@_" (compile-format [[:break {:mode :miser}]])))
  (is (= "~:@_" (compile-format [[:break {:mode :mandatory}]]))))

(deftest compile-params-test
  (is (= "~10A" (compile-format [[:str {:width 10}]])))
  (is (= "~8,'0D" (compile-format [[:int {:width 8 :fill \0}]])))
  (is (= "~10,2,1,'*A" (compile-format [[:str {:width 10 :pad-step 2 :min-pad 1 :fill \*}]])))
  (is (= "~8,2F" (compile-format [[:float {:width 8 :decimals 2}]])))
  (is (= "~,,2F" (compile-format [[:float {:scale 2}]])))
  (is (= "~2,1,10$" (compile-format [[:money {:decimals 2 :int-digits 1 :width 10}]]))))

(deftest compile-flags-test
  (is (= "~:D" (compile-format [[:int {:group true}]])))
  (is (= "~@D" (compile-format [[:int {:sign :always}]])))
  (is (= "~:@D" (compile-format [[:int {:group true :sign :always}]])))
  (is (= "~@A" (compile-format [[:str {:pad :left}]])))
  (is (= "~:W" (compile-format [[:write {:pretty true}]])))
  (is (= "~@?" (compile-format [[:recur {:from :rest}]])))
  (is (= "~:^" (compile-format [[:stop {:outer true}]])))
  (is (= "~:P" (compile-format [[:plural {:rewind true}]])))
  (is (= "~@P" (compile-format [[:plural {:form :ies}]])))
  (is (= "~4@T" (compile-format [[:tab {:col 4 :relative true}]]))))

(deftest compile-literal-text-test
  (is (= "" (compile-format [])))
  (is (= "hello" (compile-format ["hello"])))
  (is (= "~~" (compile-format ["~"])))
  (is (= "~~~~" (compile-format ["~~"]))))

(deftest compile-mixed-test
  (is (= "Hello ~A!" (compile-format ["Hello " :str "!"])))
  (is (= "Name: ~A, Age: ~D" (compile-format ["Name: " :str ", Age: " :int]))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Compound Directives
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest compile-each-test
  (is (= "~{~A ~}" (compile-format [[:each :str " "]])))
  (is (= "~{~A~^, ~}" (compile-format [[:each {:sep ", "} :str]])))
  (is (= "~@{~A~^, ~}" (compile-format [[:each {:sep ", " :from :rest} :str]])))
  (is (= "~:{~A ~}" (compile-format [[:each {:from :sublists} :str " "]])))
  (is (= "~:@{~A ~}" (compile-format [[:each {:from :rest-sublists} :str " "]])))
  (is (= "~5{~A, ~}" (compile-format [[:each {:max 5} :str ", "]])))
  (is (= "~{~A~:}" (compile-format [[:each {:min 1} :str]]))))

(deftest compile-if-test
  (is (= "~:[no~;yes~]" (compile-format [[:if "yes" "no"]])))
  (is (= "~:[~A~;~D~]" (compile-format [[:if :int :str]])))
  (is (= "~:[nothing~;~A found~]"
         (compile-format [[:if [:str " found"] "nothing"]]))))

(deftest compile-when-test
  (is (= "~@[yes: ~A~]" (compile-format [[:when "yes: " :str]])))
  (is (= "~@[present~]" (compile-format [[:when "present"]]))))

(deftest compile-choose-test
  (is (= "~[zero~;one~;two~]" (compile-format [[:choose "zero" "one" "two"]])))
  (is (= "~[zero~;one~;two~:;other~]"
         (compile-format [[:choose {:default "other"} "zero" "one" "two"]])))
  (is (= "~1[a~;b~;c~]"
         (compile-format [[:choose {:selector 1} "a" "b" "c"]])))
  (is (= "~[~;~;~]" (compile-format [[:choose nil nil nil]]))))

(deftest compile-case-option-test
  (is (= "~:(~A~)" (compile-format [[:str {:case :capitalize}]])))
  (is (= "~:@(~A~)" (compile-format [[:str {:case :upcase}]])))
  (is (= "~(~A~)" (compile-format [[:str {:case :downcase}]])))
  (is (= "~@(~A~)" (compile-format [[:str {:case :titlecase}]])))
  (is (= "~:(~10A~)" (compile-format [[:str {:width 10 :case :capitalize}]])))
  (is (= "~:(~{~A~^, ~}~)" (compile-format [[:each {:sep ", " :case :capitalize} :str]]))))

(deftest compile-case-compound-test
  (is (= "~(the ~A is ~A~)" (compile-format [[:downcase "the " :str " is " :str]])))
  (is (= "~:(~)" (compile-format [[:capitalize]]))))

(deftest compile-justify-test
  (is (= "~<left~;right~>" (compile-format [[:justify "left" "right"]])))
  (is (= "~40<left~;right~>" (compile-format [[:justify {:width 40} "left" "right"]])))
  (is (= "~:<a~;b~>" (compile-format [[:justify {:pad-before true} "a" "b"]])))
  (is (= "~:@<a~;b~;c~>" (compile-format [[:justify {:pad-before true :pad-after true} "a" "b" "c"]]))))

(deftest compile-logical-block-test
  (is (= "~<~A~:>" (compile-format [[:logical-block :str]])))
  (is (= "~<(~;~A~;)~:>" (compile-format [[:logical-block "(" :str ")"]])))
  (is (= "~:<~A~:>" (compile-format [[:logical-block {:colon true} :str]]))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Round-Trip Tests: parse → compile → compare
;;
;; Verifies that (compile-format (parse-format s)) produces an equivalent
;; format string. Minor differences (flag ordering) are acceptable.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- round-trip [s]
  (compile-format (parse-format s)))

(deftest round-trip-simple-directives-test
  (doseq [s ["~A" "~S" "~W" "~C" "~D" "~B" "~O" "~X" "~P"
             "~F" "~E" "~G" "~$" "~%" "~&" "~|" "~T" "~~"
             "~?" "~^" "~I" "~*" "~_"
             "~R" "~:R" "~@R" "~:@R"
             "~:*" "~@*"
             "~:_" "~@_" "~:@_"]]
    (is (= s (round-trip s)) (str "round-trip failed for: " s))))

(deftest round-trip-params-test
  (doseq [s ["~10A" "~8,'0D" "~10,2,1,'*A" "~8,2F" "~,,2F"
             "~10,4,2E" "~2,1,10$" "~3%" "~2&" "~20T" "~20,8T"
             "~3~" "~2|" "~3*" "~2:*" "~5@*"
             "~16R" "~4I" "~2:I" "~0^" "~1,1^" "~1,2,3^"]]
    (is (= s (round-trip s)) (str "round-trip failed for: " s))))

(deftest round-trip-flags-test
  (doseq [s ["~:D" "~@D" "~:@D" "~@A" "~10@A"
             "~:W" "~@W" "~:@W" "~:C" "~@C"
             "~:P" "~@P" "~:@P"
             "~8,'0:D" "~8,'0@D" "~8,'0:@D"
             "~@F" "~8,2@F" "~@$" "~:$" "~:@$"
             "~4@T" "~@?" "~:^" "~:I"]]
    (is (= s (round-trip s)) (str "round-trip failed for: " s))))

(deftest round-trip-compound-test
  (doseq [s ["~{~A ~}"
             "~{~A~^, ~}"
             "~:{~A ~}"
             "~@{~A~^, ~}"
             "~:@{~A ~}"
             "~5{~A, ~}"
             "~{~A~:}"
             "~:[no~;yes~]"
             "~@[present~]"
             "~@[yes: ~A~]"
             "~[zero~;one~;two~]"
             "~[zero~;one~;two~:;other~]"
             "~1[a~;b~;c~]"
             "~(~A~)"
             "~:(~A~)"
             "~@(~A~)"
             "~:@(~A~)"
             "~(the ~A is ~A~)"
             "~<left~;right~>"
             "~40<left~;right~>"
             "~:<a~;b~>"
             "~<~A~:>"
             "~<(~;~A~;)~:>"]]
    (is (= s (round-trip s)) (str "round-trip failed for: " s))))

(deftest round-trip-mixed-test
  (doseq [s ["Hello ~A!"
             "Name: ~A, Age: ~D"
             "~D item~:P"
             "~A~%~D"]]
    (is (= s (round-trip s)) (str "round-trip failed for: " s))))

(deftest round-trip-complex-test
  (doseq [s ["~D item~:P: ~{~A~^, ~}"
             "~:{~:(~A~): ~:[baz~;~A~]~%~}"
             "~@{~@R. ~:(~A~)~%~}"
             "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
             "~30<Name~;Count~;Price~>~%~{~30<~A~;~D~;~$~>~%~}"
             "~@{~@[~:(~A~)~]~^, ~}"
             "~:{~{~:(~:[n~;y~]~) ~}~%~}"]]
    (is (= s (round-trip s)) (str "round-trip failed for: " s))))

(deftest round-trip-tilde-in-text-test
  (is (= "a~~b" (round-trip "a~~b"))))

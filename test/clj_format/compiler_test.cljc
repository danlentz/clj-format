(ns clj-format.compiler-test
  "Tests the compiler: serialization of every directive type, and
   round-trip tests verifying parse -> compile -> compare."
  (:require [#?(:clj clojure.test :cljs cljs.test) :refer [deftest is testing]]
            [clj-format.parser :refer [parse-format]]
            [clj-format.compiler :refer [compile-format]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Compilation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest compile-simple-test
  (is (= "~A" (compile-format [:str])))
  (is (= "~S" (compile-format [:pr])))
  (is (= "~D" (compile-format [:int])))
  (is (= "~%" (compile-format [:nl])))
  (is (= "~~" (compile-format [:tilde])))
  (is (= "~R" (compile-format [:cardinal])))
  (is (= "~:R" (compile-format [:ordinal])))
  (is (= "~@R" (compile-format [:roman])))
  (is (= "~:@R" (compile-format [:old-roman])))
  (is (= "~:*" (compile-format [:back])))
  (is (= "~@*" (compile-format [:goto])))
  (is (= "~_" (compile-format [:break])))
  (is (= "~:@_" (compile-format [[:break {:mode :mandatory}]]))))

(deftest compile-params-test
  (is (= "~10A" (compile-format [[:str {:width 10}]])))
  (is (= "~8,'0D" (compile-format [[:int {:width 8 :fill \0}]])))
  (is (= "~8,2F" (compile-format [[:float {:width 8 :decimals 2}]])))
  (is (= "~,,2F" (compile-format [[:float {:scale 2}]])))
  (is (= "~2,1,10$" (compile-format [[:money {:decimals 2 :int-digits 1 :width 10}]])))
  (is (= "~:$" (compile-format [[:money {:sign-first true}]])))
  (is (= "~0^" (compile-format [[:stop {:arg1 0}]])))
  (is (= "~1,2^" (compile-format [[:stop {:arg1 1 :arg2 2}]])))
  (is (= "~1,2,3^" (compile-format [[:stop {:arg1 1 :arg2 2 :arg3 3}]]))))

(deftest compile-flags-test
  (is (= "~:D" (compile-format [[:int {:group true}]])))
  (is (= "~@D" (compile-format [[:int {:sign :always}]])))
  (is (= "~:@D" (compile-format [[:int {:group true :sign :always}]])))
  (is (= "~@A" (compile-format [[:str {:pad :left}]])))
  (is (= "~:P" (compile-format [[:plural {:rewind true}]])))
  (is (= "~@P" (compile-format [[:plural {:form :ies}]])))
  (is (= "~:W" (compile-format [[:write {:pretty true}]])))
  (is (= "~@W" (compile-format [[:write {:full true}]])))
  (is (= "~:@W" (compile-format [[:write {:pretty true :full true}]])))
  (is (= "~:C" (compile-format [[:char {:name true}]])))
  (is (= "~@C" (compile-format [[:char {:readable true}]])))
  (is (= "~:@C" (compile-format [[:char {:name true :readable true}]])))
  (is (= "~:C" (compile-format [[:char {:format :name}]])))
  (is (= "~@C" (compile-format [[:char {:format :readable}]])))
  (is (= "~4@T" (compile-format [[:tab {:col 4 :relative true}]])))
  (is (= "~@?" (compile-format [[:recur {:from :rest}]])))
  (is (= "~:^" (compile-format [[:stop {:outer true}]]))))

(deftest compile-case-option-test
  (is (= "~:(~A~)" (compile-format [[:str {:case :capitalize}]])))
  (is (= "~:@(~A~)" (compile-format [[:str {:case :upcase}]])))
  (is (= "~(~A~)" (compile-format [[:str {:case :downcase}]])))
  (is (= "~:(~{~A~^, ~}~)" (compile-format [[:each {:sep ", " :case :capitalize} :str]]))))

(deftest compile-compounds-test
  (is (= "~{~A~^, ~}" (compile-format [[:each {:sep ", "} :str]])))
  (is (= "~:{~A ~}" (compile-format [[:each {:from :sublists} :str " "]])))
  (is (= "~{~A~:}" (compile-format [[:each {:min 1} :str]])))
  (is (= "~:[no~;yes~]" (compile-format [[:if "yes" "no"]])))
  (is (= "~@[yes: ~A~]" (compile-format [[:when "yes: " :str]])))
  (is (= "~[zero~;one~]" (compile-format [[:choose "zero" "one"]])))
  (is (= "~[zero~;one~:;other~]" (compile-format [[:choose {:default "other"} "zero" "one"]])))
  (is (= "~40<left~;right~>" (compile-format [[:justify {:width 40} "left" "right"]])))
  (is (= "~<~%~0,20:;~A ~>"
         (compile-format [[:justify :nl [:clause {:width 0 :pad-step 20 :pad-before true} :str " "]]])))
  (is (= "~<~A~:>" (compile-format [[:logical-block :str]])))
  (is (= "~(the ~A~)" (compile-format [[:downcase "the " :str]]))))

(deftest compile-literal-test
  (is (= "" (compile-format [])))
  (is (= "hello" (compile-format ["hello"])))
  (is (= "~~" (compile-format ["~"])))
  (is (= "Hello ~A!" (compile-format ["Hello " :str "!"]))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Round-Trip: parse -> compile -> compare
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- round-trip [s]
  (compile-format (parse-format s)))

(deftest round-trip-simple-test
  (doseq [s ["~A" "~S" "~W" "~:W" "~@W" "~:@W" "~C" "~D" "~B" "~O" "~X" "~P"
             "~F" "~E" "~G" "~$" "~%" "~&" "~|" "~T" "~~"
             "~?" "~^" "~I" "~*" "~_"
             "~R" "~:R" "~@R" "~:@R" "~:*" "~@*"
             "~:_" "~@_" "~:@_"]]
    (is (= s (round-trip s)) (str "round-trip: " s))))

(deftest round-trip-params-test
  (doseq [s ["~10A" "~8,'0D" "~10,2,1,'*A" "~8,2F" "~,,2F"
             "~10,4,2E" "~2,1,10$" "~3%" "~20T" "~20,8T"
             "~16R" "~4I" "~2:I" "~0^" "~1,1^" "~1,2,3^"
             "~:D" "~@D" "~:@D" "~@A" "~10@A"
             "~:P" "~@P" "~:@P" "~8,'0:D" "~@$" "~:$"
             "~4@T" "~@?" "~:^"]]
    (is (= s (round-trip s)) (str "round-trip: " s))))

(deftest round-trip-compound-test
  (doseq [s ["~{~A ~}" "~{~A~^, ~}" "~:{~A ~}" "~@{~A~^, ~}"
             "~5{~A, ~}" "~{~A~:}"
             "~:[no~;yes~]" "~@[present~]" "~@[yes: ~A~]"
             "~[zero~;one~;two~]" "~[zero~;one~:;other~]"
             "~(~A~)" "~:(~A~)" "~@(~A~)" "~:@(~A~)"
             "~(the ~A is ~A~)"
             "~<left~;right~>" "~40<left~;right~>"
             "~:<a~;b~>" "~<~%~0,20:;~A ~>" "~<~A~:>" "~<(~;~A~;)~:>"]]
    (is (= s (round-trip s)) (str "round-trip: " s))))

(deftest round-trip-complex-test
  (doseq [s ["~D item~:P: ~{~A~^, ~}"
             "~:{~:(~A~): ~:[baz~;~A~]~%~}"
             "~@{~@R. ~:(~A~)~%~}"
             "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
             "~30<Name~;Count~;Price~>~%~{~30<~A~;~D~;~$~>~%~}"
             "~@{~@[~:(~A~)~]~^, ~}"
             "~:{~{~:(~:[n~;y~]~) ~}~%~}"
             "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}"
             "I saw ~R el~:*~[ves~;f~:;ves~]."
             "I saw ~[no~:;~:*~R~] el~:*~[ves~;f~:;ves~]."
             "~{~#[~;~A~;~A and ~A~:;~@{~A~#[~;, and ~:;, ~]~}~]~}"
             "Items:~#[ none~; ~S~; ~S and ~S~:;~@{~#[~; and~] ~S~^,~}~]."
             "Done.~^ ~D warning~:P.~^ ~D error~:P."]]
    (is (= s (round-trip s)) (str "round-trip: " s))))

(deftest round-trip-char-flags-test
  (doseq [s ["~:C" "~@C" "~:@C"]]
    (is (= s (round-trip s)) (str "round-trip: " s))))

(deftest compile-each-separator-escaping-test
  (is (= "~{~A~^~~~}" (compile-format [[:each {:sep "~"} :str]]))))

(deftest compile-invalid-dsl-test
  (doseq [[dsl kind]
           [[[:bogus] :unknown-directive]
           [[[:str 1]] :invalid-element]
           [[[:if]] :invalid-child-count]
           [[[:if [:clause {:width 1} :str] "x"]] :invalid-clause]
           [[[:downcase {:foo true} :str]] :invalid-options]
           [[1] :invalid-element]
           [42 :invalid-root]]]
    (try
      (compile-format dsl)
      (is false (str "expected ExceptionInfo for " (pr-str dsl)))
      (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core.ExceptionInfo) e
        (is (= :clj-format (:library (ex-data e))) (pr-str dsl))
        (is (= :compile (:phase (ex-data e))) (pr-str dsl))
        (is (= kind (:kind (ex-data e))) (pr-str dsl))))))

(deftest round-trip-tilde-test
  (is (= "a~~b" (round-trip "a~~b"))))

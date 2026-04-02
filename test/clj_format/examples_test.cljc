(ns clj-format.examples-test
  "Tests every example from doc/examples.md. For each format string, verifies
  that the DSL round-trip (parse → compile → cl-format) produces the same
  output as the original format string."
  (:require [clj-format.core :as fmt]
            [clj-format.test-support :as support]
            [#?(:clj clojure.test :cljs cljs.test) :refer [deftest is testing]]))

(defn- equiv
  "Assert that cl-format produces identical output with the original string
  and with the DSL round-tripped string."
  [fmt-str & args]
  (let [{:keys [compiled expected actual]} (apply support/equiv-data fmt-str args)]
    (is (= expected actual)
        (str "Format: " (pr-str fmt-str)
             "\nCompiled: " (pr-str compiled)
             "\nExpected: " (pr-str expected)
             "\nActual: " (pr-str actual)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Printing Values
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest aesthetic-test
  (equiv "The value is: ~A" "foo")
  (equiv "~A" 42)
  (equiv "~A" nil)
  (equiv "~A" [1 2 3]))

(deftest standard-test
  (equiv "~S" "foo")
  (equiv "~S" 42)
  (equiv "~S" nil))

(deftest padded-test
  (equiv "~10A" "foo")
  (equiv "~10@A" "foo")
  (equiv "~10,3A" "foo"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integer Formatting
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest comma-grouped-test
  (equiv "~:D" 1000000)
  (equiv "~:D" 0)
  (equiv "~:D" -1234567))

(deftest signed-test
  (equiv "~@D" 42)
  (equiv "~@D" -42)
  (equiv "~@D" 0))

(deftest zero-padded-date-test
  (equiv "~4,'0D-~2,'0D-~2,'0D" 2005 6 10))

(deftest european-grouping-test
  (equiv "~,,'.,4:D" 100000000))

(deftest multiple-bases-test
  (equiv "decimal ~D binary ~B octal ~O hex ~X" 63 63 63 63))

(deftest zero-padded-binary-test
  (equiv "~8,'0B" 5)
  (equiv "~8,'0B" 255)
  (equiv "~8,'0B" 0))

(deftest padded-hex-test
  (equiv "~4,'0X" 255)
  (equiv "~4,'0X" 0)
  (equiv "~4,'0X" 16))

(deftest custom-pad-char-test
  (equiv "~5,'*D" 3))

(deftest arbitrary-radix-test
  (equiv "~7R" 63)
  (equiv "~2R" 10)
  (equiv "~36R" 255))

(deftest binary-space-groups-test
  (testing "CLtL2: binary with space-separated groups of 4"
    (equiv "~,,' ,4:B" 0xFACE)
    (equiv "~,,' ,4:B" 0x1CE)
    (equiv "~19,,' ,4:B" 0x1CE)))

(deftest negative-zero-pad-test
  (testing "ClojureDocs: zero padding before sign"
    (equiv "~8,'0D" -2)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; English Words and Roman Numerals
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest cardinal-test
  (equiv "~R" 4)
  (equiv "~R" 42)
  (equiv "~R" 1234)
  (equiv "~R" 0))

(deftest ordinal-test
  (equiv "~:R" 4)
  (equiv "~:R" 42)
  (equiv "~:R" 1234))

(deftest roman-test
  (equiv "~@R" 1999)
  (equiv "~@R" 4)
  (equiv "~@R" 42))

(deftest old-roman-test
  (equiv "~:@R" 1999)
  (equiv "~:@R" 4))

(deftest lowercase-roman-test
  (equiv "~(~@R~)" 124))

(deftest roman-with-case-test
  (testing "CLtL2: uppercase and lowercase Roman in one string"
    (equiv "~@R ~(~@R~)" 14 14)))

(deftest character-formatting-test
  (equiv "~:C" \newline)
  (equiv "~@C" \newline)
  (equiv "~:@C" \newline))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Floating Point
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest fixed-float-test
  (equiv "~,4F" 3.14159265)
  (equiv "~8,2F" 3.14159265)
  (equiv "~F" 0.5))

(deftest exponential-test
  (testing "CLtL2: exponential notation"
    (equiv "~,4E" Math/PI)
    (equiv "~9,2,1E" 3.14159)))

(deftest monetary-test
  (equiv "~$" 3.14159265)
  (equiv "~$" 100.0)
  (equiv "~$" 0.5))

(deftest v-parameter-monetary-test
  (testing "Practical Common Lisp: V param supplies decimal count from arg"
    (equiv "~V$" 3 Math/PI)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pluralization
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest simple-plural-test
  (equiv "~D file~:P" 1)
  (equiv "~D file~:P" 0)
  (equiv "~D file~:P" 10))

(deftest ies-plural-test
  (equiv "~D famil~:@P" 1)
  (equiv "~D famil~:@P" 10))

(deftest english-plural-test
  (equiv "~R file~:P" 1)
  (equiv "~R file~:P" 0)
  (equiv "~R file~:P" 42))

(deftest tries-wins-test
  (testing "CLtL2: combined plurals"
    (equiv "~D tr~:@P/~D win~:P" 7 1)
    (equiv "~D tr~:@P/~D win~:P" 1 0)
    (equiv "~D tr~:@P/~D win~:P" 1 3)))

(deftest error-count-sentence-case-test
  (testing "CLtL2: ~@( capitalizes first word of English number"
    (equiv "~@(~R~) error~:P detected." 0)
    (equiv "~@(~R~) error~:P detected." 1)
    (equiv "~@(~R~) error~:P detected." 23)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Case Conversion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest downcase-test
  (equiv "~(~A~)" "THE QUICK BROWN FOX"))

(deftest titlecase-first-test
  (equiv "~@(~A~)" "tHe Quick BROWN foX"))

(deftest capitalize-words-test
  (equiv "~:(~A~)" "tHe Quick BROWN foX"))

(deftest upcase-test
  (equiv "~:@(~A~)" "the quick brown fox"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Conditionals
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest ordinal-conditional-test
  (equiv "~[cero~;uno~;dos~]" 0)
  (equiv "~[cero~;uno~;dos~]" 1)
  (equiv "~[cero~;uno~;dos~]" 2))

(deftest default-clause-test
  (equiv "~[cero~;uno~;dos~:;mucho~]" 0)
  (equiv "~[cero~;uno~;dos~:;mucho~]" 100))

(deftest boolean-conditional-test
  (equiv "~:[FAIL~;pass~]" true)
  (equiv "~:[FAIL~;pass~]" nil)
  (equiv "~:[FAIL~;pass~]" false))

(deftest at-sign-conditional-test
  (equiv "~@[x = ~A ~]~@[y = ~A~]" 10 20)
  (equiv "~@[x = ~A ~]~@[y = ~A~]" 10 nil)
  (equiv "~@[x = ~A ~]~@[y = ~A~]" nil nil))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Iteration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest simple-list-iteration-test
  (equiv "~{~A~^, ~}" [1 2 3])
  (equiv "~{~A~^, ~}" [1])
  (equiv "~{~A~^, ~}" []))

(deftest tilde-separator-test
  (equiv "~{~A~^~~~}" [1 2 3]))

(deftest at-sign-iteration-test
  (equiv "~@{~A~^, ~}" 1 2 3))

(deftest pairs-from-flat-list-test
  (equiv "~{~A: ~A~^, ~}" ["name" "Alice" "age" 30]))

(deftest nil-filtering-test
  (equiv "~{~@[~A ~]~}" [1 2 nil 3 nil 4]))

(deftest plist-key-extraction-test
  (equiv "~{~A~*~^ ~}" [:a 10 :b 20]))

(deftest sublist-iteration-test
  (testing "CLtL2: ~:{ iterates over sublists"
    (equiv "Pairs:~:{ <~S,~S>~}." '(("a" 1) ("b" 2) ("c" 3)))))

(deftest simple-iteration-with-pr-test
  (testing "CLtL2: simple ~{ with ~S"
    (equiv "Winners:~{ ~S~}." '("fred" "harry" "jill"))))

(deftest oxford-comma-test
  (equiv "~{~A~#[~;, and ~:;, ~]~}" [1 2 3])
  (equiv "~{~A~#[~;, and ~:;, ~]~}" [1 2])
  (equiv "~{~A~#[~;, and ~:;, ~]~}" [1]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Argument Navigation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest backup-and-reuse-test
  (equiv "~R ~:*(~D)" 1)
  (equiv "~R ~:*(~D)" 42))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Escape (~^)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest progressive-disclosure-test
  (testing "CLtL2: ~^ at top level terminates on no remaining args"
    (equiv "Done.~^ ~D warning~:P.~^ ~D error~:P.")
    (equiv "Done.~^ ~D warning~:P.~^ ~D error~:P." 3)
    (equiv "Done.~^ ~D warning~:P.~^ ~D error~:P." 1 5)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Justification
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest two-segment-test
  (equiv "~10<foo~;bar~>"))

(deftest center-test
  (equiv "~10:@<foobar~>"))

(deftest right-justify-test
  (equiv "~10<foobar~>"))

(deftest left-justify-test
  (equiv "~10@<foobar~>"))

(deftest colon-justify-test
  (testing "CLtL2: padding before first segment"
    (equiv "~10:<foo~;bar~>")
    (equiv "~10:@<foo~;bar~>")))

(deftest task-board-row-test
  (testing "Three-column status row with evenly distributed padding"
    (equiv "~36<Task~;Owner~;State~>" "Parser port" "Dan" "done")
    (equiv "~36<Task~;Owner~;State~>" "CLJS parity" "Dan" "green")))

(deftest three-column-table-test
  (testing "Header and rows aligned by shared justification width"
    (equiv "~36<Task~;Owner~;State~>~%~{~36<~A~;~A~;~A~>~%~}"
           ["Parser port" "Dan" "done"
            "CLJS parity" "Dan" "green"])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Logical Blocks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest rgb-tuple-logical-block-test
  (testing "Logical block can wrap a structured body with prefix and suffix"
    (equiv "~<rgb(~;~D, ~D, ~D~;)~:>" [255 140 0])))

(deftest range-logical-block-test
  (testing "Logical block clauses work well for compact bracketed notations"
    (equiv "~<range[~;~D, ~D~;]~:>" [10 20])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Indirection
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest indirect-test
  (equiv "~? ~D" "<~A ~D>" ["Foo" 5] 7))

(deftest at-sign-indirect-test
  (equiv "~@? ~D" "<~A ~D>" "Foo" 5 7))

(deftest runtime-template-test
  (testing "HyperSpec-style shared-arg indirection via ~@?"
    (equiv "~@? after ~D tries"
           "~A saved as ~A" "Report" "report.txt" 3)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tabulation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest column-alignment-test
  (equiv "~A~20T~A" "Name" "Extension"))

(deftest tabular-data-report-test
  (testing "HyperSpec-style tabular report with tab stops, numeric formatting, and argument reuse"
    (equiv "~:{~%~a~10t~6,2f ~v~~30t~:*~d~}"
           [["Alpha" 3.14 5]
            ["Beta" 12.0 2]])))

(deftest word-wrapping-string-test
  (testing "Word wrapping remains available via direct string passthrough"
    (is (= "\n\nThe power of FORMAT \nis that it can wrap \nwords beautifully. "
           (fmt/clj-format nil
                           "~%~%~{~<~%~0,20:;~a ~>~}"
                           ["The" "power" "of" "FORMAT" "is"
                            "that" "it" "can" "wrap" "words"
                            "beautifully."])))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Miscellaneous
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest literal-tilde-test
  (equiv "100~~"))

(deftest newlines-test
  (equiv "line one~%line two~%"))

(deftest tilde-in-text-test
  (equiv "a~~b"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Irregular Plurals (Practical Common Lisp)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest elf-elves-test
  (testing "PCL: elf/elves via ~R with ~:* and ~["
    (equiv "I saw ~R el~:*~[ves~;f~:;ves~]." 0)
    (equiv "I saw ~R el~:*~[ves~;f~:;ves~]." 1)
    (equiv "I saw ~R el~:*~[ves~;f~:;ves~]." 2)
    (equiv "I saw ~R el~:*~[ves~;f~:;ves~]." 100)))

(deftest no-elves-variant-test
  (testing "PCL footnote 7: 'no' instead of 'zero'"
    (equiv "I saw ~[no~:;~:*~R~] el~:*~[ves~;f~:;ves~]." 0)
    (equiv "I saw ~[no~:;~:*~R~] el~:*~[ves~;f~:;ves~]." 1)
    (equiv "I saw ~[no~:;~:*~R~] el~:*~[ves~;f~:;ves~]." 2)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Complex Compositions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest search-results-test
  (testing "PCL: There is/are N result(s)"
    (equiv "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 3 [46 38 22])
    (equiv "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 1 [46])
    (equiv "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 0 [])))

(deftest printf-composite-test
  (equiv "Color ~A, num1 ~D, num2 ~5,'0D, hex ~X, float ~5,2F"
         "red" 123456 89 255 3.14))

(deftest xml-tag-test
  (equiv "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
         "img" ["src" "cat.jpg" "alt" "cat"] true)
  (equiv "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
         "div" ["class" "main"] nil)
  (equiv "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
         "br" [] true))

(deftest key-value-report-test
  (equiv "~:{~:(~A~): ~:[baz~;~A~]~%~}"
         [["name" true "Alice"]
          ["title" nil nil]
          ["role" true "admin"]]))

(deftest roman-enumerated-list-test
  (equiv "~:{~@R. ~:(~A~)~%~}" [[1 "first"] [2 "second"] [3 "third"]]))

(deftest roman-enumerated-rest-args-test
  (testing "Flat rest-arg variant of the Roman numeral list"
    (equiv "~@{~@R. ~:(~A~)~%~}" 1 "first" 2 "second" 3 "third")))

(deftest table-with-header-test
  (equiv "~30<Name~;Count~;Price~>~%~{~30<~A~;~D~;~$~>~%~}"
         ["Widget" 100 9.99 "Gadget" 42 24.50]))

(deftest sparse-name-list-test
  (testing "Combines rest iteration, conditional output, case conversion, and separators"
    (equiv "~@{~@[~:(~A~)~^, ~]~}" "alice" nil "bob" "carol")))

(deftest items-example-test
  (testing "CLtL2: Items with ~#[ dispatching on remaining arg count"
    (equiv "Items:~#[ none~; ~S~; ~S and ~S~:;~@{~#[~; and~] ~S~^,~}~].")
    (equiv "Items:~#[ none~; ~S~; ~S and ~S~:;~@{~#[~; and~] ~S~^,~}~]." "foo")
    (equiv "Items:~#[ none~; ~S~; ~S and ~S~:;~@{~#[~; and~] ~S~^,~}~]." "foo" "bar")
    (equiv "Items:~#[ none~; ~S~; ~S and ~S~:;~@{~#[~; and~] ~S~^,~}~]." "foo" "bar" "baz")
    (equiv "Items:~#[ none~; ~S~; ~S and ~S~:;~@{~#[~; and~] ~S~^,~}~]." "foo" "bar" "baz" "quux")))

(deftest english-list-test
  (testing "PCL: the most famous FORMAT example — proper English list"
    (equiv "~{~#[~;~A~;~A and ~A~:;~@{~A~#[~;, and ~:;, ~]~}~]~}" [])
    (equiv "~{~#[~;~A~;~A and ~A~:;~@{~A~#[~;, and ~:;, ~]~}~]~}" [1])
    (equiv "~{~#[~;~A~;~A and ~A~:;~@{~A~#[~;, and ~:;, ~]~}~]~}" [1 2])
    (equiv "~{~#[~;~A~;~A and ~A~:;~@{~A~#[~;, and ~:;, ~]~}~]~}" [1 2 3])
    (equiv "~{~#[~;~A~;~A and ~A~:;~@{~A~#[~;, and ~:;, ~]~}~]~}" [1 2 3 4])))

(ns clj-format.equivalence-test
  "Tests that the DSL representation and the original format string produce
  identical output when run through cl-format. Uses well-known examples from
  Practical Common Lisp, ClojureDocs, and the CL FORMAT spec."
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [cl-format]]
            [clj-format.parser :refer [parse-format]]
            [clj-format.compiler :refer [compile-format]]))

(defn- fmt
  "Run cl-format with the given format string and args, returning a string."
  [fmt-str & args]
  (apply cl-format nil fmt-str args))

(defn- dsl-fmt
  "Parse a format string to DSL, compile it back, then run cl-format."
  [fmt-str & args]
  (apply cl-format nil (compile-format (parse-format fmt-str)) args))

(defmacro equiv
  "Assert that the format string and its DSL round-trip produce the same output."
  [fmt-str & args]
  `(let [expected# (fmt ~fmt-str ~@args)
         actual#   (dsl-fmt ~fmt-str ~@args)]
     (is (= expected# actual#)
         (str "Format: " (pr-str ~fmt-str)
              "\nArgs: " (pr-str [~@args])
              "\nExpected: " (pr-str expected#)
              "\nActual: " (pr-str actual#)
              "\nDSL: " (pr-str (parse-format ~fmt-str))
              "\nCompiled: " (pr-str (compile-format (parse-format ~fmt-str)))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic Output (~A, ~S)
;; Source: Practical Common Lisp, ClojureDocs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest aesthetic-output-test
  (equiv "The value is: ~A" "foo")
  (equiv "~A" 42)
  (equiv "~A" nil)
  (equiv "~A" [1 2 3]))

(deftest standard-output-test
  (equiv "~S" "foo")
  (equiv "~S" 42)
  (equiv "~S" nil))

(deftest padded-output-test
  (equiv "~10A" "foo")
  (equiv "~10@A" "foo")
  (equiv "~10,3A" "foo"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integer Formatting (~D, ~B, ~O, ~X)
;; Source: Practical Common Lisp ch.18, ClojureDocs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest comma-grouped-integers-test
  (equiv "~:D" 1000000)
  (equiv "~:D" 0)
  (equiv "~:D" -1234567))

(deftest signed-integers-test
  (equiv "~@D" 42)
  (equiv "~@D" -42)
  (equiv "~@D" 0))

(deftest zero-padded-date-test
  (testing "Practical Common Lisp date formatting example"
    (equiv "~4,'0D-~2,'0D-~2,'0D" 2005 6 10)))

(deftest european-grouping-test
  (testing "Dot separator, groups of 4"
    (equiv "~,,'.,4:D" 100000000)))

(deftest multiple-bases-test
  (equiv "decimal ~D binary ~B octal ~O hex ~X" 63 63 63 63))

(deftest zero-padded-binary-test
  (testing "8-bit byte display"
    (equiv "~8,'0B" 5)
    (equiv "~8,'0B" 255)
    (equiv "~8,'0B" 0)))

(deftest padded-hex-test
  (equiv "~4,'0X" 255)
  (equiv "~4,'0X" 0)
  (equiv "~4,'0X" 16))

(deftest custom-pad-char-test
  (testing "Asterisk padding"
    (equiv "~5,'*D" 3)))

(deftest arbitrary-radix-test
  (equiv "~7R" 63)
  (equiv "~2R" 10)
  (equiv "~36R" 255))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; English Words and Roman Numerals (~R)
;; Source: CL HyperSpec, Practical Common Lisp
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest cardinal-english-test
  (equiv "~R" 4)
  (equiv "~R" 1234)
  (equiv "~R" 0))

(deftest ordinal-english-test
  (equiv "~:R" 4)
  (equiv "~:R" 1234))

(deftest roman-numerals-test
  (equiv "~@R" 1999)
  (equiv "~@R" 4)
  (equiv "~@R" 42))

(deftest old-roman-numerals-test
  (equiv "~:@R" 1999)
  (equiv "~:@R" 4))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Floating Point (~F, ~E, ~$)
;; Source: CLtL2
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest fixed-float-test
  (equiv "~,4F" 3.14159265)
  (equiv "~8,2F" 3.14159265)
  (equiv "~F" 0.5))

(deftest monetary-format-test
  (equiv "~$" 3.14159265)
  (equiv "~$" 100.0)
  (equiv "~$" 0.5))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pluralization (~P)
;; Source: Practical Common Lisp
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest simple-plural-test
  (equiv "~D file~:P" 1)
  (equiv "~D file~:P" 0)
  (equiv "~D file~:P" 10))

(deftest ies-plural-test
  (equiv "~D famil~:@P" 1)
  (equiv "~D famil~:@P" 10))

(deftest english-plural-test
  (testing "Cardinal English with pluralization"
    (equiv "~R file~:P" 1)
    (equiv "~R file~:P" 0)
    (equiv "~R file~:P" 42)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Case Conversion (~( ~))
;; Source: CLtL2, Practical Common Lisp
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest downcase-test
  (equiv "~(~A~)" "THE QUICK BROWN FOX"))

(deftest titlecase-first-test
  (equiv "~@(~A~)" "tHe Quick BROWN foX"))

(deftest capitalize-words-test
  (equiv "~:(~A~)" "tHe Quick BROWN foX"))

(deftest upcase-test
  (equiv "~:@(~A~)" "the quick brown fox"))

(deftest lowercase-roman-test
  (testing "Classic trick: lowercase Roman numerals via case conversion"
    (equiv "~(~@R~)" 124)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Conditional (~[ ~])
;; Source: CLtL2, Practical Common Lisp
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
;; Iteration (~{ ~})
;; Source: Practical Common Lisp, ClojureDocs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest simple-list-iteration-test
  (equiv "~{~A~^, ~}" [1 2 3])
  (equiv "~{~A~^, ~}" [1])
  (equiv "~{~A~^, ~}" []))

(deftest at-sign-iteration-test
  (testing "Iterate over remaining args"
    (equiv "~@{~A~^, ~}" 1 2 3)))

(deftest pairs-from-flat-list-test
  (testing "Consume two args per iteration"
    (equiv "~{~A: ~A~^, ~}" ["name" "Alice" "age" 30])))

(deftest nil-filtering-test
  (testing "Classic pattern: filter nils with ~@[ inside ~{}"
    (equiv "~{~@[~A ~]~}" [1 2 nil 3 nil 4])))

(deftest plist-key-extraction-test
  (testing "Skip values with ~* to extract keys"
    (equiv "~{~A~*~^ ~}" [:a 10 :b 20])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Argument Navigation (~*)
;; Source: CLtL2
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest backup-and-reuse-test
  (testing "English word then decimal in parens"
    (equiv "~R ~:*(~D)" 1)
    (equiv "~R ~:*(~D)" 42)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Justification (~< ~>)
;; Source: CLtL2
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest two-segment-justify-test
  (equiv "~10<foo~;bar~>"))

(deftest center-justify-test
  (equiv "~10:@<foobar~>"))

(deftest right-justify-test
  (equiv "~10<foobar~>"))

(deftest left-justify-test
  (equiv "~10@<foobar~>"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Indirection (~?)
;; Source: CLtL2
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest indirect-format-test
  (equiv "~? ~D" "<~A ~D>" ["Foo" 5] 7))

(deftest at-sign-indirect-test
  (equiv "~@? ~D" "<~A ~D>" "Foo" 5 7))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tabulation (~T)
;; Source: CLtL2
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest absolute-tab-test
  (equiv "~A~20T~A" "Name" "Extension"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Miscellaneous
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest literal-tilde-test
  (equiv "100~~"))

(deftest multiple-newlines-test
  (equiv "line one~%line two~%"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Complex Compositions
;; Source: Practical Common Lisp, various internet examples
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest search-results-test
  (testing "Practical Common Lisp 'results' example"
    (equiv "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 3 [46 38 22])
    (equiv "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 1 [46])
    (equiv "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 0 [])))

(deftest irregular-plural-test
  (testing "Elf/elves: Practical Common Lisp irregular plural trick"
    (equiv "I saw ~R el~:*~[ves~;f~:;ves~]." 0)
    (equiv "I saw ~R el~:*~[ves~;f~:;ves~]." 1)
    (equiv "I saw ~R el~:*~[ves~;f~:;ves~]." 2)
    (equiv "I saw ~R el~:*~[ves~;f~:;ves~]." 100)))

(deftest printf-composite-test
  (testing "Printf-style mixed formatting"
    (equiv "Color ~A, num1 ~D, num2 ~5,'0D, hex ~X, float ~5,2F"
           "red" 123456 89 255 3.14)))

(deftest xml-tag-test
  (testing "XML/HTML tag formatter"
    (equiv "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
           "img" ["src" "cat.jpg" "alt" "cat"] true)
    (equiv "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
           "div" ["class" "main"] nil)
    (equiv "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
           "br" [] true)))

(deftest key-value-report-test
  (testing "Capitalized key-value with nil handling"
    (equiv "~:{~:(~A~): ~:[baz~;~A~]~%~}"
           [["name" true "Alice"]
            ["title" nil nil]
            ["role" true "admin"]])))

(deftest roman-enumerated-list-test
  (testing "Roman numeral enumerated list via sublists"
    (equiv "~:{~@R. ~:(~A~)~%~}" [[1 "first"] [2 "second"] [3 "third"]])))

(deftest oxford-comma-test
  (testing "English list with Oxford comma using ~#"
    (equiv "~{~A~#[~;, and ~:;, ~]~}" [1 2 3])
    (equiv "~{~A~#[~;, and ~:;, ~]~}" [1 2])
    (equiv "~{~A~#[~;, and ~:;, ~]~}" [1])))

(deftest table-with-header-test
  (testing "Justified table with header row"
    (equiv "~30<Name~;Count~;Price~>~%~{~30<~A~;~D~;~$~>~%~}"
           ["Widget" 100 9.99 "Gadget" 42 24.50])))

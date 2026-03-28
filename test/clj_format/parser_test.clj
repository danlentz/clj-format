(ns clj-format.parser-test
  (:require [clojure.test :refer :all]
            [clj-format.parser :refer [parse-format]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parameter Parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-params-test
  (testing "integer params"
    (is (= [[:str {:width 10}]] (parse-format "~10A")))
    (is (= [[:str {:width 0}]] (parse-format "~0A"))))
  (testing "signed integer params"
    (is (= [[:float {:scale 2}]] (parse-format "~,,2F")))
    (is (= [[:float {:scale -1}]] (parse-format "~,,-1F"))))
  (testing "character literal params"
    (is (= [[:int {:fill \0}]] (parse-format "~,'0D")))
    (is (= [[:str {:width 10 :pad-step 2 :min-pad 1 :fill \*}]]
           (parse-format "~10,2,1,'*A"))))
  (testing "V and # params"
    (is (= [[:str {:width :V}]] (parse-format "~vA")))
    (is (= [[:str {:width :#}]] (parse-format "~#A"))))
  (testing "omitted middle params"
    (is (= [[:float {:scale 2}]] (parse-format "~,,2F")))
    (is (= [[:str {:width 10 :min-pad 3}]] (parse-format "~10,,3A"))))
  (testing "mixed param types"
    (is (= [[:str {:width :V :pad-step :# :min-pad 1 :fill \-}]]
           (parse-format "~V,#,1,'-A")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Semantic Flag Translation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-flags-test
  (testing "integer flags"
    (is (= [[:int {:group true}]] (parse-format "~:D")))
    (is (= [[:int {:sign :always}]] (parse-format "~@D")))
    (is (= [[:int {:group true :sign :always}]] (parse-format "~:@D")))
    (is (= [[:int {:group true :sign :always}]] (parse-format "~@:D"))))
  (testing "str pad flag"
    (is (= [[:str {:pad :left}]] (parse-format "~@A")))
    (is (= [[:str {:width 10 :pad :left}]] (parse-format "~10@A"))))
  (testing "flags with params"
    (is (= [[:int {:width 8 :fill \0 :group true}]]
           (parse-format "~8,'0:D")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Case Insensitivity
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-case-insensitive-test
  (is (= [:str] (parse-format "~a")))
  (is (= [:str] (parse-format "~A")))
  (is (= [:pr] (parse-format "~s")))
  (is (= [:int] (parse-format "~d")))
  (is (= [:bin] (parse-format "~b")))
  (is (= [:oct] (parse-format "~o")))
  (is (= [:hex] (parse-format "~x")))
  (is (= [:cardinal] (parse-format "~r")))
  (is (= [:float] (parse-format "~f")))
  (is (= [:exp] (parse-format "~e")))
  (is (= [:gfloat] (parse-format "~g")))
  (is (= [:write] (parse-format "~w")))
  (is (= [:char] (parse-format "~c")))
  (is (= [:plural] (parse-format "~p")))
  (is (= [:tab] (parse-format "~t")))
  (is (= [[:indent {:n 4}]] (parse-format "~4i"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic Output Directives
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-str-test
  (is (= [:str] (parse-format "~A")))
  (is (= [[:str {:width 10 :pad-step 2 :min-pad 1 :fill \0}]]
         (parse-format "~10,2,1,'0A")))
  (is (= [[:str {:width 10 :pad :left}]] (parse-format "~10@A"))))

(deftest parse-pr-test
  (is (= [:pr] (parse-format "~S")))
  (is (= [[:pr {:width 15 :pad-step 3 :min-pad 2 :fill \.}]]
         (parse-format "~15,3,2,'.S"))))

(deftest parse-write-test
  (is (= [:write] (parse-format "~W")))
  (is (= [[:write {:pretty true}]] (parse-format "~:W")))
  (is (= [[:write {:full true}]] (parse-format "~@W")))
  (is (= [[:write {:pretty true :full true}]] (parse-format "~:@W"))))

(deftest parse-char-test
  (is (= [:char] (parse-format "~C")))
  (is (= [[:char {:format :name}]] (parse-format "~:C")))
  (is (= [[:char {:format :readable}]] (parse-format "~@C"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integer / Radix Directives
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-int-test
  (is (= [:int] (parse-format "~D")))
  (is (= [[:int {:group true}]] (parse-format "~:D")))
  (is (= [[:int {:sign :always}]] (parse-format "~@D")))
  (is (= [[:int {:group true :sign :always}]] (parse-format "~:@D")))
  (is (= [[:int {:width 8 :fill \0}]] (parse-format "~8,'0D")))
  (is (= [[:int {:width 12 :fill \space :group-sep \. :group-size 3}]]
         (parse-format "~12,' ,'.,3D"))))

(deftest parse-bin-test
  (is (= [:bin] (parse-format "~B")))
  (is (= [[:bin {:group true}]] (parse-format "~:B")))
  (is (= [[:bin {:width 8 :fill \0}]] (parse-format "~8,'0B"))))

(deftest parse-oct-test
  (is (= [:oct] (parse-format "~O")))
  (is (= [[:oct {:width 6 :fill \0}]] (parse-format "~6,'0O"))))

(deftest parse-hex-test
  (is (= [:hex] (parse-format "~X")))
  (is (= [[:hex {:group true :sign :always}]] (parse-format "~:@X")))
  (is (= [[:hex {:width 4 :fill \0 :group true}]] (parse-format "~4,'0:X"))))

(deftest parse-radix-test
  (is (= [[:radix {:base 16}]] (parse-format "~16R")))
  (is (= [[:radix {:base 2}]] (parse-format "~2R")))
  (is (= [[:radix {:base 8 :width 10 :fill \0 :group true}]]
         (parse-format "~8,10,'0:R")))
  (is (= [:cardinal] (parse-format "~R")))
  (is (= [:ordinal] (parse-format "~:R")))
  (is (= [:roman] (parse-format "~@R")))
  (is (= [:old-roman] (parse-format "~:@R"))))

(deftest parse-plural-test
  (is (= [:plural] (parse-format "~P")))
  (is (= [[:plural {:rewind true}]] (parse-format "~:P")))
  (is (= [[:plural {:form :ies}]] (parse-format "~@P")))
  (is (= [[:plural {:rewind true :form :ies}]] (parse-format "~:@P"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Floating Point
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-float-test
  (is (= [:float] (parse-format "~F")))
  (is (= [[:float {:width 8 :decimals 2}]] (parse-format "~8,2F")))
  (is (= [[:float {:width 8 :decimals 2 :sign :always}]] (parse-format "~8,2@F")))
  (is (= [[:float {:width 10 :decimals 3 :scale 2 :overflow \# :fill \space}]]
         (parse-format "~10,3,2,'#,' F"))))

(deftest parse-exp-test
  (is (= [:exp] (parse-format "~E")))
  (is (= [[:exp {:width 10 :decimals 4 :exp-digits 2}]] (parse-format "~10,4,2E")))
  (is (= [[:exp {:width 12 :decimals 5 :exp-digits 2 :scale 1
                  :overflow \# :fill \space :exp-char \e}]]
         (parse-format "~12,5,2,1,'#,' ,'eE"))))

(deftest parse-gfloat-test
  (is (= [:gfloat] (parse-format "~G")))
  (is (= [[:gfloat {:width 10 :decimals 4}]] (parse-format "~10,4G"))))

(deftest parse-money-test
  (is (= [:money] (parse-format "~$")))
  (is (= [[:money {:decimals 2 :int-digits 1 :width 10}]] (parse-format "~2,1,10$")))
  (is (= [[:money {:sign :always}]] (parse-format "~@$")))
  (is (= [[:money {:sign-first true}]] (parse-format "~:$")))
  (is (= [[:money {:sign-first true :sign :always}]] (parse-format "~:@$"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Layout and Whitespace
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-nl-test
  (is (= [:nl] (parse-format "~%")))
  (is (= [[:nl {:count 3}]] (parse-format "~3%"))))

(deftest parse-fresh-test
  (is (= [:fresh] (parse-format "~&")))
  (is (= [[:fresh {:count 2}]] (parse-format "~2&"))))

(deftest parse-tab-test
  (is (= [:tab] (parse-format "~T")))
  (is (= [[:tab {:col 20}]] (parse-format "~20T")))
  (is (= [[:tab {:col 20 :step 8}]] (parse-format "~20,8T")))
  (is (= [[:tab {:col 4 :relative true}]] (parse-format "~4@T"))))

(deftest parse-page-test
  (is (= [:page] (parse-format "~|")))
  (is (= [[:page {:count 2}]] (parse-format "~2|"))))

(deftest parse-tilde-test
  (is (= [:tilde] (parse-format "~~")))
  (is (= [[:tilde {:count 3}]] (parse-format "~3~"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Navigation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-skip-test
  (is (= [:skip] (parse-format "~*")))
  (is (= [[:skip {:n 3}]] (parse-format "~3*"))))

(deftest parse-back-test
  (is (= [:back] (parse-format "~:*")))
  (is (= [[:back {:n 2}]] (parse-format "~2:*"))))

(deftest parse-goto-test
  (is (= [:goto] (parse-format "~@*")))
  (is (= [[:goto {:n 5}]] (parse-format "~5@*"))))

(deftest parse-recur-test
  (is (= [:recur] (parse-format "~?")))
  (is (= [[:recur {:from :rest}]] (parse-format "~@?"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Stop, Break, Indent
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-stop-test
  (is (= [:stop] (parse-format "~^")))
  (is (= [[:stop {:arg1 0}]] (parse-format "~0^")))
  (is (= [[:stop {:arg1 1 :arg2 1}]] (parse-format "~1,1^")))
  (is (= [[:stop {:arg1 1 :arg2 2 :arg3 3}]] (parse-format "~1,2,3^")))
  (is (= [[:stop {:outer true}]] (parse-format "~:^"))))

(deftest parse-break-test
  (is (= [:break] (parse-format "~_")))
  (is (= [[:break {:mode :fill}]] (parse-format "~:_")))
  (is (= [[:break {:mode :miser}]] (parse-format "~@_")))
  (is (= [[:break {:mode :mandatory}]] (parse-format "~:@_"))))

(deftest parse-indent-test
  (is (= [:indent] (parse-format "~I")))
  (is (= [[:indent {:n 4}]] (parse-format "~4I")))
  (is (= [[:indent {:relative-to :current}]] (parse-format "~:I")))
  (is (= [[:indent {:n 2 :relative-to :current}]] (parse-format "~2:I"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Iteration (:each)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-each-test
  (testing "basic"
    (is (= [[:each :str " "]] (parse-format "~{~A ~}"))))
  (testing "from variants"
    (is (= [[:each {:from :sublists} :str " "]] (parse-format "~:{~A ~}")))
    (is (= [[:each {:from :rest} :str " "]] (parse-format "~@{~A ~}")))
    (is (= [[:each {:from :rest-sublists} :str " "]] (parse-format "~:@{~A ~}"))))
  (testing "max iterations"
    (is (= [[:each {:max 5} :str ", "]] (parse-format "~5{~A, ~}")))
    (is (= [[:each {:max 10 :from :rest} :str]] (parse-format "~10@{~A~}"))))
  (testing "min (force once)"
    (is (= [[:each {:min 1} :str]] (parse-format "~{~A~:}")))
    (is (= [[:each {:from :sublists :min 1} :str " "]] (parse-format "~:{~A ~:}"))))
  (testing "sep detection"
    (is (= [[:each {:sep ", "} :str]] (parse-format "~{~A~^, ~}")))
    (is (= [[:each {:sep " " :from :sublists} :str]] (parse-format "~:{~A~^ ~}")))
    (is (= [[:each {:sep ", " :from :rest} :str]] (parse-format "~@{~A~^, ~}"))))
  (testing "no sep when escape is mid-body"
    (is (= [[:each :str :stop ", " :int]] (parse-format "~{~A~^, ~D~}"))))
  (testing "multiple directives in body"
    (is (= [[:each :str " = " :int :nl]] (parse-format "~{~A = ~D~%~}"))))
  (testing "empty body"
    (is (= [[:each]] (parse-format "~{~}")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Conditionals
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-choose-test
  (is (= [[:choose "zero" "one" "two"]]
         (parse-format "~[zero~;one~;two~]")))
  (is (= [[:choose "only"]]
         (parse-format "~[only~]")))
  (is (= [[:choose nil nil nil]]
         (parse-format "~[~;~;~]")))
  (is (= [[:choose {:selector 1} "a" "b" "c"]]
         (parse-format "~1[a~;b~;c~]")))
  (is (= [[:choose {:default "other"} "zero" "one" "two"]]
         (parse-format "~[zero~;one~;two~:;other~]")))
  (is (= [[:choose {:default :str} "known"]]
         (parse-format "~[known~:;~A~]")))
  (testing "clauses with directives"
    (is (= [[:choose ["x=" :int] ["y=" :str]]]
           (parse-format "~[x=~D~;y=~A~]")))))

(deftest parse-if-test
  (is (= [[:if "yes" "no"]]
         (parse-format "~:[no~;yes~]")))
  (is (= [[:if :int :str]]
         (parse-format "~:[~A~;~D~]")))
  (testing "multi-element clauses"
    (is (= [[:if [:str " found"] "nothing"]]
           (parse-format "~:[nothing~;~A found~]")))))

(deftest parse-when-test
  (is (= [[:when "yes: " :str]]
         (parse-format "~@[yes: ~A~]")))
  (is (= [[:when "present"]]
         (parse-format "~@[present~]"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Case Conversion
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-case-conversion-test
  (testing "single element — flattened to :case option"
    (is (= [[:str {:case :downcase}]] (parse-format "~(~A~)")))
    (is (= [[:str {:case :capitalize}]] (parse-format "~:(~A~)")))
    (is (= [[:str {:case :titlecase}]] (parse-format "~@(~A~)")))
    (is (= [[:str {:case :upcase}]] (parse-format "~:@(~A~)"))))
  (testing "single directive with opts — :case merged"
    (is (= [[:str {:width 10 :case :capitalize}]] (parse-format "~:(~10A~)"))))
  (testing "single compound — :case merged into opts"
    (is (= [[:each {:sep ", " :case :capitalize} :str]]
           (parse-format "~:(~{~A~^, ~}~)"))))
  (testing "multi-element body — compound form"
    (is (= [[:downcase "the " :str " is " :str]]
           (parse-format "~(the ~A is ~A~)"))))
  (testing "empty body — compound form"
    (is (= [[:downcase]] (parse-format "~(~)")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Justification and Logical Block
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-justify-test
  (is (= [[:justify "left" "right"]] (parse-format "~<left~;right~>")))
  (is (= [[:justify "a" "b" "c"]] (parse-format "~<a~;b~;c~>")))
  (is (= [[:justify "only"]] (parse-format "~<only~>")))
  (is (= [[:justify {:width 40} "left" "right"]]
         (parse-format "~40<left~;right~>")))
  (is (= [[:justify {:width 40 :pad-step 1 :min-pad 0 :fill \.} "left" "right"]]
         (parse-format "~40,1,0,'.<left~;right~>")))
  (is (= [[:justify {:pad-before true} "a" "b"]] (parse-format "~:<a~;b~>")))
  (is (= [[:justify {:pad-after true} "a" "b"]] (parse-format "~@<a~;b~>")))
  (is (= [[:justify {:pad-before true :pad-after true} "a" "b" "c"]]
         (parse-format "~:@<a~;b~;c~>")))
  (is (= [[:justify :str :int]] (parse-format "~<~A~;~D~>"))))

(deftest parse-logical-block-test
  (is (= [[:logical-block :str]] (parse-format "~<~A~:>")))
  (is (= [[:logical-block "(" :str ")"]] (parse-format "~<(~;~A~;)~:>")))
  (is (= [[:logical-block {:colon true} :str]] (parse-format "~:<~A~:>"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tilde-Newline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-format-newline-test
  (is (= ["a" "b"] (parse-format "a~\n   b")))
  (is (= ["a" "   b"] (parse-format "a~:\n   b")))
  (is (= ["a" :nl "b"] (parse-format "a~@\n   b"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Literal and Mixed
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-literal-test
  (is (= [] (parse-format "")))
  (is (= ["Hello, World!"] (parse-format "Hello, World!"))))

(deftest parse-mixed-test
  (is (= ["Hello " :str "!"] (parse-format "Hello ~A!")))
  (is (= ["Name: " :str ", Age: " :int] (parse-format "Name: ~A, Age: ~D")))
  (is (= [:str :int :float] (parse-format "~A~D~F")))
  (is (= [:str :nl :int] (parse-format "~A~%~D"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Complex Composition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest compose-each-inside-compounds-test
  (is (= [[:choose "none" [:each {:sep ", "} :str]]]
         (parse-format "~[none~;~{~A~^, ~}~]")))
  (is (= [[:if [:each :int " "] "empty"]]
         (parse-format "~:[empty~;~{~D ~}~]")))
  (is (= [[:when "items: " [:each {:sep ", "} :str]]]
         (parse-format "~@[items: ~{~A~^, ~}~]")))
  (is (= [[:each {:sep ", " :case :capitalize} :str]]
         (parse-format "~:(~{~A~^, ~}~)")))
  (is (= [[:each {:from :sublists} [:each :str " "] :nl]]
         (parse-format "~:{~{~A ~}~%~}"))))

(deftest compose-conditionals-inside-compounds-test
  (is (= [[:each [:if "y" "n"] " "]]
         (parse-format "~{~:[n~;y~] ~}")))
  (is (= [[:if {:case :titlecase} "true" "false"]]
         (parse-format "~@(~:[false~;true~]~)")))
  (is (= [[:choose "zero" [:if "1t" "1f"] "two"]]
         (parse-format "~[zero~;~:[1f~;1t~]~;two~]"))))

(deftest compose-case-inside-compounds-test
  (is (= [[:each [:str {:case :capitalize}] " "]]
         (parse-format "~{~:(~A~) ~}")))
  (is (= [[:when [:str {:case :upcase}]]]
         (parse-format "~@[~:@(~A~)~]")))
  (testing "nested case — inner already has :case, outer uses compound"
    (is (= [[:capitalize [:str {:case :downcase}]]]
           (parse-format "~:(~(~A~)~)")))))

(deftest compose-triple-nesting-test
  (is (= [[:each [:if {:case :upcase} "yes" "no"] " "]]
         (parse-format "~{~:@(~:[no~;yes~]~) ~}")))
  (is (= [[:each {:sep ", " :case :capitalize} :str]]
         (parse-format "~:(~{~A~^, ~}~)"))))

(deftest compose-quadruple-nesting-test
  (is (= [[:each {:from :sublists} [:each [:if {:case :capitalize} "y" "n"] " "] :nl]]
         (parse-format "~:{~{~:(~:[n~;y~]~) ~}~%~}"))))

(deftest compose-simple-interleaved-test
  (is (= ["Item " [:back {:n 2}] [:if "on" "off"]
          ": " [:each {:sep ", "} :str] :nl]
         (parse-format "Item ~2:*~:[off~;on~]: ~{~A~^, ~}~%")))
  (is (= [:tilde "hello" :tilde]
         (parse-format "~~hello~~"))))

;; --- realistic format strings ---

(deftest compose-table-test
  (is (= [[:justify {:width 30} "Name" "Count" "Price"]
          :nl
          [:each [:justify {:width 30} :str :int :money] :nl]]
         (parse-format
          "~30<Name~;Count~;Price~>~%~{~30<~A~;~D~;~$~>~%~}"))))

(deftest compose-pluralized-summary-test
  (is (= [:int " item" [:plural {:rewind true}] ": "
          [:each {:sep ", "} :str]]
         (parse-format "~D item~:P: ~{~A~^, ~}"))))

(deftest compose-nested-key-value-test
  (is (= [[:each {:from :sublists}
           [:str {:case :capitalize}] ": " [:if :str "baz"] :nl]]
         (parse-format "~:{~:(~A~): ~:[baz~;~A~]~%~}"))))

(deftest compose-indented-tree-test
  (is (= [[:logical-block "[" [:each {:sep ", " :from :rest} :str] "]"]]
         (parse-format "~<[~;~@{~A~^, ~}~;]~:>"))))

(deftest compose-roman-list-test
  (is (= [[:each {:from :rest} :roman ". " [:str {:case :capitalize}] :nl]]
         (parse-format "~@{~@R. ~:(~A~)~%~}"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Error Handling
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-unknown-directive-test
  (is (thrown? clojure.lang.ExceptionInfo (parse-format "~Q")))
  (is (thrown? clojure.lang.ExceptionInfo (parse-format "~Z"))))

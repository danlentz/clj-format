# clj-format Examples

Side-by-side comparisons of cl-format strings and their clj-format DSL
equivalents. Every example shows both forms, the arguments, and the output.
Sources include Practical Common Lisp, CLtL2, ClojureDocs, and the CL
HyperSpec.

## Reading The DSL

Examples use both bare keywords and directive vectors:
- `:str` and `[:str]` both mean `~A`
- `["Name: " :str]` is a body vector containing literal text and directives
- `[:cardinal " file" [:plural {:rewind true}]]` is also a body vector:
  without an opts map, the remaining elements are body content

If the second element is a map, it is the directive's options map.
Otherwise the rest of the vector is treated as body content.

## Printing Values

### Human-readable output

```clojure
(cl-format  nil "The value is: ~A" "foo")
(clj-format nil ["The value is: " :str] "foo")
;; => "The value is: foo"
```

### Readable output (with quotes)

```clojure
(cl-format  nil "~S" "foo")
(clj-format nil [:pr] "foo")
;; => "\"foo\""
```

### Right-padded to minimum width

```clojure
(cl-format  nil "~10A" "foo")
(clj-format nil [:str {:width 10}] "foo")
;; => "foo       "
```

### Left-padded

```clojure
(cl-format  nil "~10@A" "foo")
(clj-format nil [:str {:width 10 :pad :left}] "foo")
;; => "       foo"
```

### Character names and readable literals

```clojure
(cl-format  nil "~:C ~@C ~:@C" \newline \space \newline)
(clj-format nil [[:char {:name true}] " "
                 [:char {:readable true}] " "
                 [:char {:name true :readable true}]]
            \newline \space \newline)
;; => "Newline \\space Newline"
```

## Integer Formatting

### Comma-grouped

```clojure
(cl-format  nil "~:D" 1000000)
(clj-format nil [:int {:group true}] 1000000)
;; => "1,000,000"
```

### Always show sign

```clojure
(cl-format  nil "~@D" 42)
(clj-format nil [:int {:sign :always}] 42)
;; => "+42"
```

### Zero-padded date

*Source: Practical Common Lisp ch. 18*

```clojure
(cl-format  nil "~4,'0D-~2,'0D-~2,'0D" 2005 6 10)
(clj-format nil [[:int {:width 4 :fill \0}] "-"
                 [:int {:width 2 :fill \0}] "-"
                 [:int {:width 2 :fill \0}]]
             2005 6 10)
;; => "2005-06-10"
```

### European-style grouping (dot separator, groups of 4)

```clojure
(cl-format  nil "~,,'.,4:D" 100000000)
(clj-format nil [:int {:group-sep \. :group-size 4 :group true}] 100000000)
;; => "1.0000.0000"
```

### Multiple bases in one string

```clojure
(cl-format  nil "decimal ~D binary ~B octal ~O hex ~X" 63 63 63 63)
(clj-format nil ["decimal " :int " binary " :bin " octal " :oct " hex " :hex]
             63 63 63 63)
;; => "decimal 63 binary 111111 octal 77 hex 3f"
```

### Zero-padded 8-bit binary

```clojure
(cl-format  nil "~8,'0B" 255)
(clj-format nil [:bin {:width 8 :fill \0}] 255)
;; => "11111111"
```

### Custom padding character

```clojure
(cl-format  nil "~5,'*D" 3)
(clj-format nil [:int {:width 5 :fill \*}] 3)
;; => "****3"
```

### Arbitrary radix (base 7)

```clojure
(cl-format  nil "~7R" 63)
(clj-format nil [:radix {:base 7}] 63)
;; => "120"
```

### Binary with space-separated groups of 4

*Source: CLtL2*

```clojure
(cl-format  nil "~,,' ,4:B" 0xFACE)
(clj-format nil [:bin {:group-sep \space :group-size 4 :group true}] 0xFACE)
;; => "1111 1010 1100 1110"

(cl-format  nil "~19,,' ,4:B" 0x1CE)
(clj-format nil [:bin {:width 19 :group-sep \space :group-size 4 :group true}] 0x1CE)
;; => "        1 1100 1110"
```

### Negative zero-padding anomaly

*Source: ClojureDocs. Per the CL HyperSpec, zero-padding goes before the sign.*

```clojure
(cl-format  nil "~8,'0D" -2)
(clj-format nil [:int {:width 8 :fill \0}] -2)
;; => "000000-2"
```

## English Words and Roman Numerals

### Cardinal English

```clojure
(cl-format  nil "~R" 42)
(clj-format nil [:cardinal] 42)
;; => "forty-two"
```

### Ordinal English

```clojure
(cl-format  nil "~:R" 42)
(clj-format nil [:ordinal] 42)
;; => "forty-second"
```

### Roman numerals

```clojure
(cl-format  nil "~@R" 1999)
(clj-format nil [:roman] 1999)
;; => "MCMXCIX"
```

### Old-style Roman (no subtractive notation)

```clojure
(cl-format  nil "~:@R" 1999)
(clj-format nil [:old-roman] 1999)
;; => "MDCCCCLXXXXVIIII"
```

### Lowercase Roman numerals

*Classic trick: wrap Roman numerals in case conversion.*

```clojure
(cl-format  nil "~(~@R~)" 124)
(clj-format nil [:roman {:case :downcase}] 124)
;; => "cxxiv"
```

### Roman with case conversion

*Source: CLtL2. Uppercase and lowercase Roman in one string.*

```clojure
(cl-format  nil "~@R ~(~@R~)" 14 14)
(clj-format nil [:roman " " [:roman {:case :downcase}]] 14 14)
;; => "XIV xiv"
```

## Floating Point

### Fixed precision

```clojure
(cl-format  nil "~,4F" 3.14159265)
(clj-format nil [:float {:decimals 4}] 3.14159265)
;; => "3.1416"
```

### Exponential notation

*Source: CLtL2*

```clojure
(cl-format  nil "~,4E" Math/PI)
(clj-format nil [:exp {:decimals 4}] Math/PI)
;; => "3.1416E+0"

(cl-format  nil "~9,2,1E" 3.14159)
(clj-format nil [:exp {:width 9 :decimals 2 :exp-digits 1}] 3.14159)
;; => "  3.14E+0"
```

### Monetary format

```clojure
(cl-format  nil "~$" 3.14159)
(clj-format nil [:money] 3.14159)
;; => "3.14"
```

### Monetary with V parameter (arg-supplied decimal count)

*Source: Practical Common Lisp. The `:V` value means "take this param from
the next argument."*

```clojure
(cl-format  nil "~V$" 3 Math/PI)
(clj-format nil [:money {:decimals :V}] 3 Math/PI)
;; => "3.142"
```

## Pluralization

*Source: Practical Common Lisp*

### Simple plural (s / no s)

```clojure
(cl-format  nil "~D file~:P" 1)
(clj-format nil [:int " file" [:plural {:rewind true}]] 1)
;; => "1 file"

(cl-format  nil "~D file~:P" 10)
(clj-format nil [:int " file" [:plural {:rewind true}]] 10)
;; => "10 files"
```

### Y/ies pluralization

```clojure
(cl-format  nil "~D famil~:@P" 1)
(clj-format nil [:int " famil" [:plural {:rewind true :form :ies}]] 1)
;; => "1 family"

(cl-format  nil "~D famil~:@P" 10)
(clj-format nil [:int " famil" [:plural {:rewind true :form :ies}]] 10)
;; => "10 families"
```

### English words with plural

```clojure
(cl-format  nil "~R file~:P" 1)
(clj-format nil [:cardinal " file" [:plural {:rewind true}]] 1)
;; => "one file"

(cl-format  nil "~R file~:P" 0)
(clj-format nil [:cardinal " file" [:plural {:rewind true}]] 0)
;; => "zero files"
```

### Tries and wins (combined plurals)

*Source: CLtL2*

```clojure
(cl-format  nil "~D tr~:@P/~D win~:P" 7 1)
(clj-format nil [:int " tr" [:plural {:rewind true :form :ies}]
                 "/" :int " win" [:plural {:rewind true}]]
             7 1)
;; => "7 tries/1 win"

(cl-format  nil "~D tr~:@P/~D win~:P" 1 0)
(clj-format nil [:int " tr" [:plural {:rewind true :form :ies}]
                 "/" :int " win" [:plural {:rewind true}]]
             1 0)
;; => "1 try/0 wins"

(cl-format  nil "~D tr~:@P/~D win~:P" 1 3)
(clj-format nil [:int " tr" [:plural {:rewind true :form :ies}]
                 "/" :int " win" [:plural {:rewind true}]]
             1 3)
;; => "1 try/3 wins"
```

### Error count with sentence case

*Source: CLtL2. The `:titlecase` case conversion capitalizes the first word,
turning "zero" into "Zero".*

```clojure
(cl-format  nil "~@(~R~) error~:P detected." 0)
(clj-format nil [[:cardinal {:case :titlecase}]
                 " error" [:plural {:rewind true}] " detected."]
             0)
;; => "Zero errors detected."

(cl-format  nil "~@(~R~) error~:P detected." 1)
(clj-format nil [[:cardinal {:case :titlecase}]
                 " error" [:plural {:rewind true}] " detected."]
             1)
;; => "One error detected."

(cl-format  nil "~@(~R~) error~:P detected." 23)
(clj-format nil [[:cardinal {:case :titlecase}]
                 " error" [:plural {:rewind true}] " detected."]
             23)
;; => "Twenty-three errors detected."
```

## Case Conversion

### Lowercase

```clojure
(cl-format  nil "~(~A~)" "THE QUICK BROWN FOX")
(clj-format nil [:str {:case :downcase}] "THE QUICK BROWN FOX")
;; => "the quick brown fox"
```

### Capitalize each word

```clojure
(cl-format  nil "~:(~A~)" "tHe Quick BROWN foX")
(clj-format nil [:str {:case :capitalize}] "tHe Quick BROWN foX")
;; => "The Quick Brown Fox"
```

### Capitalize first word only

```clojure
(cl-format  nil "~@(~A~)" "tHe Quick BROWN foX")
(clj-format nil [:str {:case :titlecase}] "tHe Quick BROWN foX")
;; => "The quick brown fox"
```

### Uppercase

```clojure
(cl-format  nil "~:@(~A~)" "the quick brown fox")
(clj-format nil [:str {:case :upcase}] "the quick brown fox")
;; => "THE QUICK BROWN FOX"
```

## Conditionals

### Numeric dispatch (select by index)

```clojure
(cl-format  nil "~[cero~;uno~;dos~]" 1)
(clj-format nil [:choose "cero" "uno" "dos"] 1)
;; => "uno"
```

### Default clause

```clojure
(cl-format  nil "~[cero~;uno~;dos~:;mucho~]" 100)
(clj-format nil [:choose {:default "mucho"} "cero" "uno" "dos"] 100)
;; => "mucho"
```

### Boolean conditional

```clojure
(cl-format  nil "~:[FAIL~;pass~]" true)
(clj-format nil [:if "pass" "FAIL"] true)
;; => "pass"

(cl-format  nil "~:[FAIL~;pass~]" nil)
(clj-format nil [:if "pass" "FAIL"] nil)
;; => "FAIL"
```

### Truthiness guard (print only if non-nil)

```clojure
(cl-format  nil "~@[x = ~A ~]~@[y = ~A~]" 10 20)
(clj-format nil [[:when "x = " :str " "] [:when "y = " :str]] 10 20)
;; => "x = 10 y = 20"

(cl-format  nil "~@[x = ~A ~]~@[y = ~A~]" 10 nil)
(clj-format nil [[:when "x = " :str " "] [:when "y = " :str]] 10 nil)
;; => "x = 10 "
```

## Iteration

### Comma-separated list

```clojure
(cl-format  nil "~{~A~^, ~}" [1 2 3])
(clj-format nil [:each {:sep ", "} :str] [1 2 3])
;; => "1, 2, 3"
```

### Iterate over remaining args

```clojure
(cl-format  nil "~@{~A~^, ~}" 1 2 3)
(clj-format nil [:each {:sep ", " :from :rest} :str] 1 2 3)
;; => "1, 2, 3"
```

### Key-value pairs from a flat list

```clojure
(cl-format  nil "~{~A: ~A~^, ~}" ["name" "Alice" "age" 30])
(clj-format nil [:each {:sep ", "} :str ": " :str] ["name" "Alice" "age" 30])
;; => "name: Alice, age: 30"
```

### Nil filtering

*Classic pattern: `:when` inside `:each` to skip nil values.*

```clojure
(cl-format  nil "~{~@[~A ~]~}" [1 2 nil 3 nil 4])
(clj-format nil [:each [:when :str " "]] [1 2 nil 3 nil 4])
;; => "1 2 3 4 "
```

### Extract keys from a plist (skip values)

```clojure
(cl-format  nil "~{~A~*~^ ~}" [:a 10 :b 20])
(clj-format nil [:each {:sep " "} :str :skip] [:a 10 :b 20])
;; => ":a :b"
```

### Sublist iteration

*Source: CLtL2. `:from :sublists` iterates where each element is itself a
list of arguments for one pass through the body.*

```clojure
(cl-format  nil "Pairs:~:{ <~S,~S>~}." '(("a" 1) ("b" 2) ("c" 3)))
(clj-format nil ["Pairs:" [:each {:from :sublists} " <" :pr "," :pr ">"] "."]
             '(("a" 1) ("b" 2) ("c" 3)))
;; => "Pairs: <\"a\",1> <\"b\",2> <\"c\",3>."
```

```clojure
(cl-format  nil "Winners:~{ ~S~}." '("fred" "harry" "jill"))
(clj-format nil ["Winners:" [:each " " :pr] "."] '("fred" "harry" "jill"))
;; => "Winners: \"fred\" \"harry\" \"jill\"."
```

### English list with Oxford comma

*Uses `~#` (remaining arg count) to select between separators.*

```clojure
(cl-format  nil "~{~A~#[~;, and ~:;, ~]~}" [1 2 3])
(clj-format nil [[:each :str [:choose {:selector :# :default ", "} nil ", and "]]]
             [1 2 3])
;; => "1, 2, and 3"
```

## Argument Navigation

### Reuse an argument

*Print as English word, back up, print as decimal in parens.*

```clojure
(cl-format  nil "~R ~:*(~D)" 42)
(clj-format nil [:cardinal " " :back "(" :int ")"] 42)
;; => "forty-two (42)"
```

### Irregular plurals (elf / elves)

*Source: Practical Common Lisp. Back up after `:cardinal` to select the
correct suffix by numeric index.*

```clojure
(cl-format  nil "I saw ~R el~:*~[ves~;f~:;ves~]." 0)
(clj-format nil ["I saw " :cardinal " el" :back
                 [:choose {:default "ves"} "ves" "f"] "."]
             0)
;; => "I saw zero elves."

(cl-format  nil "I saw ~R el~:*~[ves~;f~:;ves~]." 1)
(clj-format nil ["I saw " :cardinal " el" :back
                 [:choose {:default "ves"} "ves" "f"] "."]
             1)
;; => "I saw one elf."

(cl-format  nil "I saw ~R el~:*~[ves~;f~:;ves~]." 2)
(clj-format nil ["I saw " :cardinal " el" :back
                 [:choose {:default "ves"} "ves" "f"] "."]
             2)
;; => "I saw two elves."
```

### "I saw no elves" variant

*Source: Practical Common Lisp footnote 7. Uses `:choose` with a default to
print "no" for zero and English words for everything else.*

```clojure
(cl-format  nil "I saw ~[no~:;~:*~R~] el~:*~[ves~;f~:;ves~]." 0)
(clj-format nil ["I saw " [:choose {:default [:back :cardinal]} "no"]
                 " el" :back [:choose {:default "ves"} "ves" "f"] "."]
             0)
;; => "I saw no elves."

(cl-format  nil "I saw ~[no~:;~:*~R~] el~:*~[ves~;f~:;ves~]." 1)
(clj-format nil ["I saw " [:choose {:default [:back :cardinal]} "no"]
                 " el" :back [:choose {:default "ves"} "ves" "f"] "."]
             1)
;; => "I saw one elf."

(cl-format  nil "I saw ~[no~:;~:*~R~] el~:*~[ves~;f~:;ves~]." 2)
(clj-format nil ["I saw " [:choose {:default [:back :cardinal]} "no"]
                 " el" :back [:choose {:default "ves"} "ves" "f"] "."]
             2)
;; => "I saw two elves."
```

## Progressive Disclosure

*Source: CLtL2. `:stop` (`~^`) at the top level terminates formatting when no
args remain. Add more args to reveal more of the message.*

```clojure
(cl-format  nil "Done.~^ ~D warning~:P.~^ ~D error~:P.")
(clj-format nil ["Done." :stop " " :int " warning"
                 [:plural {:rewind true}] "." :stop " " :int
                 " error" [:plural {:rewind true}] "."])
;; => "Done."

(cl-format  nil "Done.~^ ~D warning~:P.~^ ~D error~:P." 3)
(clj-format nil ["Done." :stop " " :int " warning"
                 [:plural {:rewind true}] "." :stop " " :int
                 " error" [:plural {:rewind true}] "."]
             3)
;; => "Done. 3 warnings."

(cl-format  nil "Done.~^ ~D warning~:P.~^ ~D error~:P." 1 5)
(clj-format nil ["Done." :stop " " :int " warning"
                 [:plural {:rewind true}] "." :stop " " :int
                 " error" [:plural {:rewind true}] "."]
             1 5)
;; => "Done. 1 warning. 5 errors."
```

## Justification

### Two segments, spaced apart

```clojure
(cl-format  nil "~10<foo~;bar~>")
(clj-format nil [:justify {:width 10} "foo" "bar"])
;; => "foo    bar"
```

### Centered

```clojure
(cl-format  nil "~10:@<hello~>")
(clj-format nil [:justify {:width 10 :pad-before true :pad-after true} "hello"])
;; => "   hello  "
```

### Right-justified

```clojure
(cl-format  nil "~10<hello~>")
(clj-format nil [:justify {:width 10} "hello"])
;; => "     hello"
```

### Padding before first segment

*Source: CLtL2*

```clojure
(cl-format  nil "~10:<foo~;bar~>")
(clj-format nil [:justify {:width 10 :pad-before true} "foo" "bar"])
;; => "  foo  bar"

(cl-format  nil "~10:@<foo~;bar~>")
(clj-format nil [:justify {:width 10 :pad-before true :pad-after true} "foo" "bar"])
;; => "  foo bar "
```

### Three-column status row

*A practical use for `:justify`: report rows with evenly distributed padding.*

```clojure
(cl-format  nil "~36<Task~;Owner~;State~>" "Parser port" "Dan" "done")
(clj-format nil [:justify {:width 36} "Parser port" "Dan" "done"])
;; => "Parser port         Dan         done"
```

### Table with header

```clojure
(cl-format  nil "~36<Task~;Owner~;State~>~%~{~36<~A~;~A~;~A~>~%~}"
             ["Parser port" "Dan" "done"
              "CLJS parity" "Dan" "green"])
(clj-format nil [[:justify {:width 36} "Task" "Owner" "State"] :nl
                 [:each [:justify {:width 36} :str :str :str] :nl]]
             ["Parser port" "Dan" "done"
              "CLJS parity" "Dan" "green"])
;; => "Task           Owner           State\nParser port         Dan         done\nCLJS parity         Dan        green\n"
```

### Tabular numeric report with tabs

*A denser report layout: tab stops, fixed-width numbers, repeated tildes,
and argument reuse in one row format.*

```clojure
(cl-format  nil "~:{~%~a~10t~6,2f ~v~~30t~:*~d~}"
            [["Alpha" 3.14 5]
             ["Beta" 12.0 2]])
(clj-format nil [[:each {:from :sublists}
                  :nl
                  :str
                  [:tab {:col 10}]
                  [:float {:width 6 :decimals 2}]
                  " "
                  [:tilde {:count :V}]
                  [:tab {:col 30}]
                  :back
                  :int]]
            [["Alpha" 3.14 5]
             ["Beta" 12.0 2]])
;; => "\nAlpha       3.14 ~~~~~        5\nBeta       12.00 ~~           2"
```

## Logical Blocks

*`:logical-block` opens a single sequence argument as a structured block.
Its prefix and suffix clauses make it a good fit for wrapped notations and
pretty-printable forms.*

### RGB tuple

```clojure
(cl-format  nil "~<rgb(~;~D, ~D, ~D~;)~:>" [255 140 0])
(clj-format nil [[:logical-block "rgb(" [:int ", " :int ", " :int] ")"]]
             [255 140 0])
;; => "rgb(255, 140, 0)"
```

### Bracketed range

```clojure
(cl-format  nil "~<range[~;~D, ~D~;]~:>" [10 20])
(clj-format nil [[:logical-block "range[" [:int ", " :int] "]"]]
             [10 20])
;; => "range[10, 20]"
```

## Indirection

### Format string from an argument

```clojure
(cl-format  nil "~? ~D" "<~A ~D>" ["Foo" 5] 7)
(clj-format nil [:recur " " :int] "<~A ~D>" ["Foo" 5] 7)
;; => "<Foo 5> 7"
```

### Runtime template with shared arguments

*Source: Common Lisp `~@?` indirection. The format string itself is data.*

```clojure
(cl-format  nil "~@? after ~D tries"
            "~A saved as ~A" "Report" "report.txt" 3)
(clj-format nil [[:recur {:from :rest}] " after " :int " tries"]
            "~A saved as ~A" "Report" "report.txt" 3)
;; => "Report saved as report.txt after 3 tries"
```

## Tabulation

### Column alignment

```clojure
(cl-format  nil "~A~20T~A" "Name" "Extension")
(clj-format nil [:str [:tab {:col 20}] :str] "Name" "Extension")
;; => "Name                Extension"
```

### Word-wrapped prose

*One of the classic FORMAT tricks. This example is currently best expressed
as a raw format string, and `clj-format` still supports it directly via
string passthrough.*

```clojure
(cl-format  nil "~%~%~{~<~%~0,20:;~a ~>~}"
            ["The" "power" "of" "FORMAT" "is"
             "that" "it" "can" "wrap" "words"
             "beautifully."])
(clj-format nil "~%~%~{~<~%~0,20:;~a ~>~}"
            ["The" "power" "of" "FORMAT" "is"
             "that" "it" "can" "wrap" "words"
             "beautifully."])
;; => "\n\nThe power of FORMAT \nis that it can wrap \nwords beautifully. "
```

## Complex Compositions

### Search results with grammar

*Source: Practical Common Lisp. Combines conditionals, argument backup,
pluralization, and iteration in one format string.*

```clojure
(cl-format  nil "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 1 [46])
(clj-format nil ["There " [:choose {:default "are"} "are" "is"] :back
                 " " :int " result" [:plural {:rewind true}] ": "
                 [:each {:sep ", "} :int]]
             1 [46])
;; => "There is 1 result: 46"

(cl-format  nil "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 3 [46 38 22])
(clj-format nil ["There " [:choose {:default "are"} "are" "is"] :back
                 " " :int " result" [:plural {:rewind true}] ": "
                 [:each {:sep ", "} :int]]
             3 [46 38 22])
;; => "There are 3 results: 46, 38, 22"

(cl-format  nil "There ~[are~;is~:;are~]~:* ~D result~:P: ~{~D~^, ~}" 0 [])
(clj-format nil ["There " [:choose {:default "are"} "are" "is"] :back
                 " " :int " result" [:plural {:rewind true}] ": "
                 [:each {:sep ", "} :int]]
             0 [])
;; => "There are 0 results: "
```

### Printf-style composite

```clojure
(cl-format  nil "Color ~A, num1 ~D, num2 ~5,'0D, hex ~X, float ~5,2F"
             "red" 123456 89 255 3.14)
(clj-format nil ["Color " :str ", num1 " :int ", num2 " [:int {:width 5 :fill \0}]
                 ", hex " :hex ", float " [:float {:width 5 :decimals 2}]]
             "red" 123456 89 255 3.14)
;; => "Color red, num1 123456, num2 00089, hex ff, float  3.14"
```

### XML/HTML tag formatter

```clojure
(cl-format  nil "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
             "img" ["src" "cat.jpg" "alt" "cat"] true)
(clj-format nil ["<" :str [:each :stop " " :str "=\"" :str "\""]
                 [:if "/" nil] ">" :nl]
             "img" ["src" "cat.jpg" "alt" "cat"] true)
;; => "<img src=\"cat.jpg\" alt=\"cat\"/>\n"

(cl-format  nil "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
             "br" [] true)
(clj-format nil ["<" :str [:each :stop " " :str "=\"" :str "\""]
                 [:if "/" nil] ">" :nl]
             "br" [] true)
;; => "<br/>\n"

(cl-format  nil "<~A~{~^ ~A=\"~A\"~}~:[~;/~]>~%"
             "div" ["class" "main"] nil)
(clj-format nil ["<" :str [:each :stop " " :str "=\"" :str "\""]
                 [:if "/" nil] ">" :nl]
             "div" ["class" "main"] nil)
;; => "<div class=\"main\">\n"
```

### Capitalized key-value report

```clojure
(cl-format  nil "~:{~:(~A~): ~:[baz~;~A~]~%~}"
             [["name" true "Alice"] ["title" nil nil] ["role" true "admin"]])
(clj-format nil [[:each {:from :sublists}
                  [:str {:case :capitalize}] ": " [:if :str "baz"] :nl]]
             [["name" true "Alice"] ["title" nil nil] ["role" true "admin"]])
;; => "Name: Alice\nTitle: baz\nRole: admin\n"
```

### Roman numeral enumerated list

```clojure
(cl-format  nil "~:{~@R. ~:(~A~)~%~}"
             [[1 "first"] [2 "second"] [3 "third"]])
(clj-format nil [[:each {:from :sublists}
                  :roman ". " [:str {:case :capitalize}] :nl]]
             [[1 "first"] [2 "second"] [3 "third"]])
;; => "I. First\nII. Second\nIII. Third\n"
```

### Roman numeral enumerated list from flat arguments

```clojure
(cl-format  nil "~@{~@R. ~:(~A~)~%~}"
             1 "first" 2 "second" 3 "third")
(clj-format nil [[:each {:from :rest}
                  :roman ". " [:str {:case :capitalize}] :nl]]
             1 "first" 2 "second" 3 "third")
;; => "I. First\nII. Second\nIII. Third\n"
```

### Justified table with header

```clojure
(cl-format  nil "~30<Name~;Count~;Price~>~%~{~30<~A~;~D~;~$~>~%~}"
             ["Widget" 100 9.99 "Gadget" 42 24.50])
(clj-format nil [[:justify {:width 30} "Name" "Count" "Price"] :nl
                 [:each [:justify {:width 30} :str :int :money] :nl]]
             ["Widget" 100 9.99 "Gadget" 42 24.50])
;; => "Name        Count        Price\nWidget         100        9.99\nGadget          42       24.50\n"
```

### Sparse capitalized name list

```clojure
(cl-format  nil "~@{~@[~:(~A~)~^, ~]~}"
             "alice" nil "bob" "carol")
(clj-format nil [[:each {:from :rest}
                  [:when [:str {:case :capitalize}] :stop ", "]]]
             "alice" nil "bob" "carol")
;; => "Alice, Bob, Carol"
```

### CLtL2 Items example

*Source: CLtL2. Uses `:choose` with `:selector :#` to dispatch on the number
of remaining args, selecting none/single/pair/list formatting.*

```clojure
(def items-fmt "Items:~#[ none~; ~S~; ~S and ~S~:;~@{~#[~; and~] ~S~^,~}~].")

(def items-dsl
  ["Items:"
   [:choose {:selector :#
             :default [:each {:sep "," :from :rest}
                       [:choose {:selector :#} nil " and"] " " :pr]}
    " none" [" " :pr] [" " :pr " and " :pr]]
   "."])

(cl-format  nil items-fmt)
(clj-format nil items-dsl)
;; => "Items: none."

(cl-format  nil items-fmt "foo")
(clj-format nil items-dsl "foo")
;; => "Items: \"foo\"."

(cl-format  nil items-fmt "foo" "bar")
(clj-format nil items-dsl "foo" "bar")
;; => "Items: \"foo\" and \"bar\"."

(cl-format  nil items-fmt "foo" "bar" "baz")
(clj-format nil items-dsl "foo" "bar" "baz")
;; => "Items: \"foo\", \"bar\", and \"baz\"."

(cl-format  nil items-fmt "foo" "bar" "baz" "quux")
(clj-format nil items-dsl "foo" "bar" "baz" "quux")
;; => "Items: \"foo\", \"bar\", \"baz\", and \"quux\"."
```

### Practical Common Lisp's English list

*Source: Practical Common Lisp ch. 18. The most famous FORMAT example — a
single format string that handles 0, 1, 2, and N-element lists with correct
English grammar and Oxford comma.*

```clojure
(def english-list
  "~{~#[~;~A~;~A and ~A~:;~@{~A~#[~;, and ~:;, ~]~}~]~}")

(def english-list-dsl
  [[:each
    [:choose {:selector :#
              :default [:each {:from :rest}
                        :str [:choose {:selector :# :default ", "} nil ", and "]]}
     nil :str [:str " and " :str]]]])

(cl-format  nil english-list [])
(clj-format nil english-list-dsl [])
;; => ""

(cl-format  nil english-list [1])
(clj-format nil english-list-dsl [1])
;; => "1"

(cl-format  nil english-list [1 2])
(clj-format nil english-list-dsl [1 2])
;; => "1 and 2"

(cl-format  nil english-list [1 2 3])
(clj-format nil english-list-dsl [1 2 3])
;; => "1, 2, and 3"

(cl-format  nil english-list [1 2 3 4])
(clj-format nil english-list-dsl [1 2 3 4])
;; => "1, 2, 3, and 4"
```

*This is arguably the hardest cl-format string in existence. The DSL makes the
structure visible: an outer `:each` that dispatches on remaining arg count
via `:choose` with `:#`, with a nested `:each` for the 3+ case that uses
its own `:#` dispatch for comma/and placement.*

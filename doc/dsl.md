# clj-format DSL Reference

This document describes the s-expression DSL that clj-format compiles into
`cl-format` format strings. The DSL uses vectors, keywords, maps, and strings
to represent the full range of cl-format directives in a readable way.

## Principles

- **Semantic naming:** Keywords describe intent, not cl-format mechanics
- **Bare keywords:** `:str` in a body is shorthand for `[:str]`
- **Strings are literal text:** `"hello"` emits itself verbatim
- **Options map always last:** `[:keyword body opts]` or `[:keyword opts]`
- **Transparent passthrough:** String format specs go directly to `cl-format`

## Output

### `:str` — Print argument (`~A`)

```clojure
:str                                  ;; ~A
[:str {:width 10}]                    ;; ~10A
[:str {:width 10 :pad :left}]         ;; ~10@A
[:str {:width 10 :fill \0}]           ;; ~10,1,0,'0A
```

Options: `:width`, `:pad-step`, `:min-pad`, `:fill`, `:pad` (`:left`/`:right`)

### `:pr` — Print readable (`~S`)

Same options as `:str`. Prints with quotes, escape characters, etc.

```clojure
:pr                                   ;; ~S
[:pr {:width 20 :pad :left}]          ;; ~20@S
```

### `:write` — Pretty-print (`~W`)

```clojure
:write                                ;; ~W
[:write {:pretty true}]               ;; ~:W
[:write {:full true}]                 ;; ~@W
```

### `:char` — Character (`~C`)

```clojure
:char                                 ;; ~C
[:char {:format :name}]               ;; ~:C  ("Space", "Newline")
[:char {:format :readable}]           ;; ~@C  (\x)
```

## Integers

### `:int` — Decimal (`~D`)

```clojure
:int                                  ;; ~D
[:int {:width 8 :fill \0}]            ;; ~8,'0D
[:int {:group true}]                  ;; ~:D  (1,000,000)
[:int {:sign :always}]                ;; ~@D  (+42)
[:int {:group true :group-sep \. :group-size 4}]
```

Options: `:width`, `:fill`, `:group`, `:sign`, `:group-sep`, `:group-size`

### `:bin`, `:oct`, `:hex` — Other bases (`~B`, `~O`, `~X`)

Same options as `:int`.

```clojure
[:hex {:width 4 :fill \0}]            ;; ~4,'0X
[:bin {:width 8 :fill \0 :group true}] ;; ~8,'0:B
```

### `:radix` — General base (`~nR`)

```clojure
[:radix {:base 16}]                   ;; ~16R
[:radix {:base 8 :width 10 :fill \0}] ;; ~8,10,'0R
```

### `:cardinal`, `:ordinal`, `:roman`, `:old-roman` — English and Roman

```clojure
:cardinal                             ;; ~R   "twenty-three"
:ordinal                              ;; ~:R  "twenty-third"
:roman                                ;; ~@R  "IV"
:old-roman                            ;; ~:@R "IIII"
```

### `:plural`

```clojure
:plural                               ;; ~P   "s"/""
[:plural {:form :ies}]                ;; ~@P  "y"/"ies"
[:plural {:rewind true}]              ;; ~:P  back up one arg first
```

## Floating Point

### `:float` — Fixed format (`~F`)

```clojure
:float                                ;; ~F
[:float {:width 8 :decimals 2}]       ;; ~8,2F
[:float {:width 8 :decimals 2 :sign :always}]  ;; ~8,2@F
```

Options: `:width`, `:decimals`, `:scale`, `:overflow`, `:fill`, `:sign`

### `:exp` — Exponential (`~E`)

```clojure
[:exp {:width 10 :decimals 4}]        ;; ~10,4E
[:exp {:width 10 :decimals 4 :exp-digits 2}]  ;; ~10,4,2E
```

Additional options: `:exp-digits`, `:exp-char`

### `:gfloat` — General float (`~G`)

Auto-selects between fixed and exponential. Same options as `:exp`.

### `:money` — Monetary (`~$`)

```clojure
:money                                ;; ~$
[:money {:decimals 2 :int-digits 1 :width 10}]  ;; ~2,1,10$
[:money {:sign :always}]              ;; ~@$
[:money {:sign-first true}]           ;; ~:$  sign before padding
```

## Layout

### `:nl` — Newline (`~%`)

```clojure
:nl                                   ;; ~%
[:nl {:count 3}]                      ;; ~3%
```

### `:fresh` — Fresh line (`~&`)

```clojure
:fresh                                ;; ~&
[:fresh {:count 2}]                   ;; ~2&
```

### `:tab` — Tabulate (`~T`)

```clojure
[:tab {:col 20}]                      ;; ~20T
[:tab {:col 20 :step 8}]              ;; ~20,8T
[:tab {:col 4 :relative true}]        ;; ~4@T
```

### `:page` — Page separator (`~|`)

```clojure
:page                                 ;; ~|
```

### `:tilde` — Literal tilde (`~~`)

```clojure
:tilde                                ;; ~~
[:tilde {:count 3}]                   ;; ~3~
```

## Navigation

### `:skip` — Skip forward (`~*`)

```clojure
:skip                                 ;; ~*   skip 1
[:skip {:n 3}]                        ;; ~3*  skip 3
```

### `:back` — Skip backward (`~:*`)

```clojure
:back                                 ;; ~:*  back up 1
[:back {:n 2}]                        ;; ~2:* back up 2
```

### `:goto` — Absolute go-to (`~@*`)

```clojure
:goto                                 ;; ~@*  go to arg 0
[:goto {:n 5}]                        ;; ~5@* go to arg 5
```

### `:recur` — Indirection (`~?`)

```clojure
:recur                                ;; ~?   sublist args
[:recur {:from :rest}]                ;; ~@?  remaining args
```

## Iteration

### `:each` — Iterate (`~{...~}`)

```clojure
;; Basic — iterate over list argument
[:each [:str]]                               ;; ~{~A~}

;; With separator (the common pattern)
[:each [:str] {:sep ", "}]                   ;; ~{~A~^, ~}

;; Over remaining args
[:each [:str] {:sep ", " :from :rest}]       ;; ~@{~A~^, ~}

;; Over sublists
[:each [:str " = " :int] {:from :sublists}]  ;; ~:{~A = ~D~}

;; Over remaining-arg sublists
[:each [:str] {:from :rest-sublists}]        ;; ~:@{~A~}

;; Max iterations
[:each [:str] {:sep ", " :max 5}]            ;; ~5{~A~^, ~}

;; Force at least once
[:each [:str] {:min 1}]                      ;; ~{~A~:}
```

Options: `:sep`, `:from` (`:rest`, `:sublists`, `:rest-sublists`), `:max`, `:min`

## Conditionals

### `:when` — Truthiness guard (`~@[...~]`)

Print body only if the argument is truthy.

```clojure
[:when ["value: " :str]]                     ;; ~@[value: ~A~]
```

### `:if` — Boolean dispatch (`~:[...~;...~]`)

True-clause first, false-clause second (natural order).

```clojure
[:if ["yes"] ["no"]]                         ;; ~:[no~;yes~]
[:if [[:each [:str] {:sep ", "}]] ["(empty)"]]
```

### `:choose` — Numeric dispatch (`~[...~;...~]`)

Select clause by index.

```clojure
[:choose [["zero"] ["one"] ["two"]]]
[:choose [["zero"] ["one"]] {:default ["other"]}]
[:choose [["a"] ["b"] ["c"]] {:selector 1}]
```

## Case Conversion

Each variant is its own keyword — no flags needed.

### `:downcase` (`~(`)

```clojure
[:downcase [:str]]                    ;; all lowercase
```

### `:capitalize` (`~:(`)

```clojure
[:capitalize [:str]]                  ;; Capitalize Each Word
```

### `:titlecase` (`~@(`)

```clojure
[:titlecase [:str]]                   ;; Capitalize first word only
```

### `:upcase` (`~:@(`)

```clojure
[:upcase [:str]]                      ;; ALL UPPERCASE
```

## Justification

### `:justify` — Text justification (`~<...~>`)

```clojure
[:justify [["left"] ["right"]]]
[:justify [["left"] ["right"]] {:width 40}]
[:justify [["a"] ["b"] ["c"]] {:pad-before true :pad-after true}]
```

Options: `:width`, `:pad-step`, `:min-pad`, `:fill`, `:pad-before`, `:pad-after`

### `:logical-block` — Pretty-print logical block (`~<...~:>`)

```clojure
[:logical-block [[:str]]]
[:logical-block [["("] [:str] [")"]] {:colon true}]
```

## Control

### `:stop` — Exit iteration (`~^`)

Normally abstracted by `:sep` on `:each`. Use directly for complex cases.

```clojure
:stop                                 ;; ~^
[:stop {:outer true}]                 ;; ~:^ exit enclosing iteration
```

### `:break` — Conditional newline (`~_`)

```clojure
:break                                ;; ~_  linear
[:break {:mode :fill}]                ;; ~:_
[:break {:mode :miser}]               ;; ~@_
[:break {:mode :mandatory}]           ;; ~:@_
```

### `:indent` — Indentation (`~I`)

```clojure
[:indent {:n 4}]                      ;; ~4I  relative to block
[:indent {:n 2 :relative-to :current}] ;; ~2:I relative to current
```

## Full Examples

Format a table row:

```clojure
(clj-format nil
  [[:int {:width 6}] " | " [:str {:width 20}] " | " [:float {:width 8 :decimals 2}]]
  42 "widget" 3.14159)
;; => "    42 | widget               |     3.14"
```

Comma-separated list:

```clojure
(clj-format nil [:each [:str] {:sep ", "}] ["a" "b" "c"])
;; => "a, b, c"
```

Count with pluralization:

```clojure
(clj-format nil [:int " item" [:plural {:rewind true}]] 5)
;; => "5 items"
```

Capitalized enumerated list:

```clojure
(clj-format nil
  [:each [:roman ". " [:capitalize [:str]] :nl] {:from :rest}]
  "first" "second" "third")
```

String passthrough:

```clojure
(clj-format nil "~{~A~^, ~}" ["a" "b" "c"])
;; => "a, b, c"
```

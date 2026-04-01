# clj-format

A Clojure DSL for `clojure.pprint/cl-format`.

cl-format is extraordinarily powerful — it handles comma-grouped integers, Roman
numerals, English number words, conditional pluralization, justified text,
iteration with separators, and much more. But its format strings are notoriously
hard to read:

```clojure
(cl-format nil "~:{~:(~A~): ~:[baz~;~A~]~%~}" data)
```

clj-format lets you write the same thing as a Clojure data structure:

```clojure
(clj-format nil
  [:each {:from :sublists}
    [:str {:case :capitalize}] ": " [:if :str "baz"] :nl]
  data)
```

When given a string, clj-format passes it directly to cl-format — full
backward compatibility, zero migration cost.

See [50+ side-by-side examples](doc/examples.md) from Practical Common
Lisp, CLtL2, and the CL HyperSpec.

## Quick Start

```clojure
(require '[clj-format.core :as fmt])

;; String passthrough — identical to cl-format
(fmt/clj-format nil "~D item~:P" 5)
;; => "5 items"

;; DSL form — same result, readable syntax
(fmt/clj-format nil [:int " item" [:plural {:rewind true}]] 5)
;; => "5 items"

;; Parse a format string into the DSL
(fmt/parse-format "~R file~:P")
;; => [:cardinal " file" [:plural {:rewind true}]]

;; Compile DSL back to a format string
(fmt/compile-format [:cardinal " file" [:plural {:rewind true}]])
;; => "~R file~:P"
```

## The DSL

The DSL follows the [Hiccup convention](https://github.com/weavejester/hiccup):
`[:keyword optional-opts-map & body]`. Bare keywords are shorthand for
directives with no options. Strings are literal text. The complete
[DSL reference](doc/dsl.md) covers all 33 cl-format directives.

There are two common vector shapes:
- A single directive vector like `[:str]` or `[:int {:width 8}]`
- A body vector like `["Name: " :str]` or `[:cardinal " file" [:plural {:rewind true}]]`

If the second element is a map, it is the options map. Otherwise the
remaining elements are treated as body content. That means both `:str`
and `[:str]` are valid ways to express `~A`, depending on context.

### Basics

```clojure
;; Print values
:str                            ;; => ~A  (bare keyword shorthand)
[:str]                          ;; => ~A  (human readable)
[:pr]                           ;; => ~S  (readable with quotes)

;; Bare keywords in a body
["Name: " :str ", Age: " :int] ;; => "Name: ~A, Age: ~D"

;; Options in a map
[:int {:width 8 :fill \0}]     ;; => ~8,'0D
[:str {:width 20 :pad :left}]  ;; => ~20@A
[:char {:name true}]           ;; => ~:C   character name
[:char {:readable true}]       ;; => ~@C   readable char literal
```

### Numbers

```clojure
:int                                  ;; decimal
:bin :oct :hex                        ;; other bases
[:int {:group true}]                  ;; comma-grouped: 1,000,000
[:int {:sign :always}]                ;; always show sign: +42
[:hex {:width 4 :fill \0}]           ;; zero-padded hex: 00ff

:cardinal                             ;; "forty-two"
:ordinal                              ;; "forty-second"
:roman                                ;; "XLII"

[:float {:width 8 :decimals 2}]      ;; fixed-point
:money                                ;; monetary: 3.14
```

### Iteration

```clojure
;; Comma-separated list
[:each {:sep ", "} :str]                    ;; ~{~A~^, ~}

;; Iterate sublists
[:each {:from :sublists} :str ": " :int]    ;; ~:{~A: ~D~}

;; From remaining args
[:each {:sep ", " :from :rest} :str]        ;; ~@{~A~^, ~}
```

### Conditionals

```clojure
;; Boolean (true clause first)
[:if "yes" "no"]                            ;; ~:[no~;yes~]

;; Truthiness guard
[:when "value: " :str]                      ;; ~@[value: ~A~]

;; Numeric dispatch
[:choose "zero" "one" "two"]                ;; ~[zero~;one~;two~]
[:choose {:default "many"} "zero" "one"]    ;; ~[zero~;one~:;many~]
```

### Case Conversion

Applied as a `:case` option — no extra nesting:

```clojure
[:str {:case :capitalize}]                  ;; ~:(~A~) Capitalize Each Word
[:str {:case :upcase}]                      ;; ~:@(~A~) ALL CAPS
[:each {:sep ", " :case :capitalize} :str]  ;; capitalize a whole list
```

### Pluralization

```clojure
[:int " item" [:plural {:rewind true}]]     ;; "5 items" / "1 item"
[:int " famil" [:plural {:rewind true :form :ies}]]  ;; "1 family" / "2 families"
```

### Layout

```clojure
:nl                                   ;; newline
:fresh                                ;; newline only if not at column 0
[:tab {:col 20}]                      ;; tab to column 20
:tilde                                ;; literal ~
```

### Navigation

```clojure
:skip                                 ;; skip forward one arg
[:back {:n 2}]                        ;; back up two args
[:goto {:n 0}]                        ;; jump to arg 0
```

## Real-World Examples

### Date formatting
```clojure
(clj-format nil [[:int {:width 4 :fill \0}] "-"
                 [:int {:width 2 :fill \0}] "-"
                 [:int {:width 2 :fill \0}]]
  2005 6 10)
;; => "2005-06-10"
```

### Search results with grammar
```clojure
;; "There are 3 results: 46, 38, 22"
;; "There is 1 result: 46"
(clj-format nil
  ["There " [:choose {:default "are"} "are" "is"] :back
   " " :int " result" [:plural {:rewind true}] ": "
   [:each {:sep ", "} :int]]
  n results)
```

### XML tag formatter
```clojure
(clj-format nil
  ["<" :str [:each :stop " " :str "=\"" :str "\""] [:if "/" nil] ">" :nl]
  "img" ["src" "cat.jpg" "alt" "cat"] true)
;; => "<img src=\"cat.jpg\" alt=\"cat\"/>\n"
```

### Lowercase Roman numerals
```clojure
(clj-format nil [:roman {:case :downcase}] 42)
;; => "xlii"
```

## API

The public API is available from `clj-format.core`. The lower-level
`clj-format.parser` and `clj-format.compiler` namespaces remain available,
but `clj-format.core` re-exports `parse-format` and `compile-format` for
convenience.

### `clj-format.core/clj-format`

```clojure
(clj-format writer fmt & args)
```

Drop-in replacement for `clojure.pprint/cl-format`.

**`writer`** — output destination:
- `nil` or `false` — return formatted string
- `true` — print to `*out*`, return nil
- a `java.io.Writer` — write to it, return nil

**`fmt`** — format specification:
- **string** — passed directly to `cl-format` (full backward compatibility)
- **vector** — compiled from DSL to a format string, then passed to `cl-format`
- **keyword** — shorthand for a single bare directive (e.g., `:str` for `~A`)

```clojure
(fmt/clj-format nil "~D item~:P" 5)                            ;; => "5 items"
(fmt/clj-format nil [:int " item" [:plural {:rewind true}]] 5) ;; => "5 items"
(fmt/clj-format nil :cardinal 42)                               ;; => "forty-two"
```

### `clj-format.core/parse-format`

```clojure
(fmt/parse-format s)
```

Parse a cl-format format string into the DSL. Returns a vector of elements:
literal strings, bare keywords (simple directives), and vectors (directives
with options or compound directives).

```clojure
(fmt/parse-format "~A")             ;=> [:str]
(fmt/parse-format "Hello ~A!")      ;=> ["Hello " :str "!"]
(fmt/parse-format "~R file~:P")     ;=> [:cardinal " file" [:plural {:rewind true}]]
(fmt/parse-format "~{~A~^, ~}")    ;=> [[:each {:sep ", "} :str]]
(fmt/parse-format "~:[no~;yes~]")  ;=> [[:if "yes" "no"]]
(fmt/parse-format "~:(~A~)")       ;=> [[:str {:case :capitalize}]]
```

When `parse-format` rejects an input it throws `ExceptionInfo` with
structured `ex-data` describing the parse failure. Errors raised by
`clojure.pprint/cl-format` itself still come from that library.

### `clj-format.core/compile-format`

```clojure
(fmt/compile-format dsl-body)
```

Compile a DSL form into a cl-format format string. The inverse of
`parse-format`. Accepts a body vector, a single directive vector, or a
bare keyword.

```clojure
(fmt/compile-format :str)                       ;=> "~A"
(fmt/compile-format [:str])                      ;=> "~A"
(fmt/compile-format [:str {:width 10}])          ;=> "~10A"
(fmt/compile-format ["Hello " :str "!"])         ;=> "Hello ~A!"
(fmt/compile-format [:cardinal " file" [:plural {:rewind true}]])
                                                ;=> "~R file~:P"
(fmt/compile-format [:each {:sep ", "} :str])    ;=> "~{~A~^, ~}"
(fmt/compile-format [:if "yes" "no"])            ;=> "~:[no~;yes~]"
```

Round-trip fidelity: `(= s (compile-format (parse-format s)))` holds for
any valid format string.

When `compile-format` rejects an invalid DSL form it throws
`ExceptionInfo` with structured `ex-data` describing the compile-phase
error.

## Development

```
lein test                              # run all tests
lein test clj-format.core-test        # API mechanics
lein test clj-format.parser-test       # parser tests
lein test clj-format.compiler-test     # compiler + round-trip tests
lein test clj-format.examples-test     # cl-format output equivalence
lein repl                              # start a REPL
```

## Background and References

The FORMAT facility originated in MIT Lisp Machine Lisp and was
standardized as part of Common Lisp. clj-format builds on the Clojure
implementation in `clojure.pprint/cl-format`.

### Specification

- **Common Lisp the Language, 2nd Edition (CLtL2)** — Guy L. Steele Jr.,
  1990. Chapter 22.3.3, "Formatted Output to Character Streams."
  https://www.cs.cmu.edu/Groups/AI/html/cltl/clm/node200.html

- **Common Lisp HyperSpec** — the authoritative language reference.
  Section 22.3, "Formatted Output."
  http://www.lispworks.com/documentation/HyperSpec/Body/22_c.htm

- **ANSI Common Lisp Standard (X3.226-1994)** — the formal standard
  that defines FORMAT. The HyperSpec is derived from this document.

### Clojure Implementation

- **clojure.pprint/cl-format** — Tom Faulhaber's implementation of
  Common Lisp FORMAT for Clojure, included in Clojure core since 1.2.
  100% compatible with the CLtL2 specification (with minor exceptions
  documented at the link below).
  https://clojure.github.io/clojure/doc/clojure/pprint/CommonLispFormat.html

- **ClojureDocs cl-format page** — community-contributed examples.
  https://clojuredocs.org/clojure.pprint/cl-format

### Examples and Tutorials

- **Practical Common Lisp** — Peter Seibel, 2005. Chapter 18, "A Few
  FORMAT Recipes." The source of the famous English list formatter,
  the elf/elves irregular plural trick, and the search results example.
  https://gigamonkeys.com/book/a-few-format-recipes.html

- **Successful Lisp** — David B. Lamkins, 2004. Chapter 24 covers
  FORMAT with additional examples.
  https://dept-info.labri.fr/~strandh/Teaching/MTP/Common/David-Lamkins/chapter24.html

- **HexstreamSoft FORMAT Reference** — a well-organized directive-by-directive
  reference with cross-links.
  https://www.hexstreamsoft.com/articles/common-lisp-format-reference/

## License

Copyright 2026

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
https://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

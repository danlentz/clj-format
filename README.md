# clj-format

[![Clojars Project](https://img.shields.io/clojars/v/com.github.danlentz/clj-format.svg)](https://clojars.org/com.github.danlentz/clj-format)

A Clojure, ClojureScript, and Babashka-friendly DSL for `cl-format`.

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

When given a string, clj-format passes it directly to host `cl-format` —
full backward compatibility, zero migration cost.

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

On ClojureScript, the same public API is available from `clj-format.core`
and delegates to `cljs.pprint/cl-format`.

On Babashka, the same parser/compiler/core API works, and the full
JVM-hosted suite is exercised under Babashka in CI.

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

## Tables

Tables are a first-class DSL form: `[:table opts? & cols]`. They render
through the same `clj-format` entry point as every other format call —
no separate function, no new writer rules — and the `[:col :key opts]`
children are Hiccup all the way down. Every column `:format` accepts
any directive from the clj-format DSL, so every feature of the library
composes into a table cell.

```clojure
(fmt/clj-format nil  [:table ...] rows)   ;; return string
(fmt/clj-format true [:table ...] rows)   ;; print to *out*
(fmt/clj-format sw   [:table ...] rows)   ;; write to a Writer
```

### A report in a single spec

Five distinct directives — Roman numerals, signed grouped integers,
boolean dispatch, signed currency, and centered alignment — inside
rounded Unicode borders with uppercased headers. Every directive
composes with every other; the whole table compiles to a single
cl-format call.

```clojure
(def players
  [{:rank 1 :name "Alice" :score 1250 :active true  :balance 50.0}
   {:rank 2 :name "Bob"   :score 890  :active true  :balance -12.30}
   {:rank 3 :name "Carol" :score 450  :active false :balance 0.0}])

(fmt/clj-format true
  [:table {:style :rounded :header-case :upcase}
    [:col :rank    {:width 8  :align :center :format :roman}]
    [:col :name    {:width 15}]
    [:col :score   {:width 12 :align :right  :format [:int {:group true :sign :always}]}]
    [:col :active  {:width 10 :align :center :format [:if "Yes" "No"]}]
    [:col :balance {:width 14 :align :right  :format [:money {:sign :always}]}]]
  players)
```
```
╭──────────┬─────────────────┬──────────────┬────────────┬────────────────╮
│   RANK   │ NAME            │        SCORE │   ACTIVE   │        BALANCE │
├──────────┼─────────────────┼──────────────┼────────────┼────────────────┤
│     I    │ Alice           │       +1,250 │     Yes    │         +50.00 │
│    II    │ Bob             │         +890 │     Yes    │         -12.30 │
│    III   │ Carol           │         +450 │     No     │          +0.00 │
╰──────────┴─────────────────┴──────────────┴────────────┴────────────────╯
```

### Word wrap beside typed columns

`:overflow :wrap` expands a long prose cell across multiple physical
rows while sibling columns keep their typed formatting on the first
line. Monetary columns stay right-aligned and monetary-formatted; the
name column elides; the description wraps cleanly at word boundaries.

```clojure
(def catalog
  [{:name "Widget Pro"
    :description "Premium widget with extended warranty and free shipping worldwide"
    :price 49.99}
   {:name "Gadget"      :description "Basic gadget"                                  :price 24.50}
   {:name "Sprocket Deluxe"
    :description "High-quality precision sprocket for industrial applications"
    :price 12.75}])

(fmt/clj-format true
  [:table {:style :unicode :header-case :upcase}
    [:col :name        {:width 12}]
    [:col :description {:width 25 :overflow :wrap}]
    [:col :price       {:width 10 :align :right :format :money}]]
  catalog)
```
```
┌──────────────┬───────────────────────────┬────────────┐
│ NAME         │ DESCRIPTION               │      PRICE │
├──────────────┼───────────────────────────┼────────────┤
│ Widget Pro   │ Premium widget with       │      49.99 │
│              │ extended warranty and     │            │
│              │ free shipping worldwide   │            │
│ Gadget       │ Basic gadget              │      24.50 │
│ Sprocket ... │ High-quality precision    │      12.75 │
│              │ sprocket for industrial   │            │
│              │ applications              │            │
└──────────────┴───────────────────────────┴────────────┘
```

### Anything multi-line goes in a cell

Wrap mode preserves interior whitespace verbatim, so any pre-formatted
string drops cleanly into a cell. An ASCII inner table rendered as a
column `:format` sits inside a Unicode outer table:

```clojure
(def inner
  (fn [rows]
    (fmt/clj-format nil
      [:table {:style :ascii :header false}
        [:col :k {:width 5}]
        [:col :v {:width 5 :align :right}]]
      rows)))

(fmt/clj-format true
  [:table {:style :unicode}
    [:col :group   {:width 10}]
    [:col :details {:width 22 :overflow :wrap :format inner}]]
  [{:group "Team A" :details [{:k "Mon" :v 10} {:k "Tue" :v 15} {:k "Wed" :v 8}]}
   {:group "Team B" :details [{:k "Mon" :v 7}  {:k "Tue" :v 12}]}])
```
```
┌────────────┬────────────────────────┐
│ Group      │ Details                │
├────────────┼────────────────────────┤
│ Team A     │ +-------+-------+      │
│            │ | Mon   |    10 |      │
│            │ | Tue   |    15 |      │
│            │ | Wed   |     8 |      │
│            │ +-------+-------+      │
│ Team B     │ +-------+-------+      │
│            │ | Mon   |     7 |      │
│            │ | Tue   |    12 |      │
│            │ +-------+-------+      │
└────────────┴────────────────────────┘
```

The same recipe — `:overflow :wrap` plus a function `:format` — works
for any multi-line content. Load the optional `clj-format.figlet`
extension, carry a `:font` on each row, and `clj-figlet.core/render`
becomes a computed column. Every row picks its own font:

```clojure
(require 'clj-format.figlet)
(require '[clj-figlet.core :as cf])

(fmt/clj-format true
  [:table {:style :unicode}
    [:col :name {:width 12}]
    [:col (fn [row] (cf/render (:font row) (:banner row)))
          {:title "Banner" :width 36 :overflow :wrap}]]
  [{:name "Alice" :banner "HELLO" :font "small"}
   {:name "Bob"   :banner "HI"    :font "slant"}])
```
```
┌──────────────┬──────────────────────────────────────┐
│ Name         │ Banner                               │
├──────────────┼──────────────────────────────────────┤
│ Alice        │  _  _ ___ _    _    ___              │
│              │ | || | __| |  | |  / _ \             │
│              │ | __ | _|| |__| |_| (_) |            │
│              │ |_||_|___|____|____\___/             │
│ Bob          │     __  ______                       │
│              │    / / / /  _/                       │
│              │   / /_/ // /                         │
│              │  / __  // /                          │
│              │ /_/ /_/___/                          │
└──────────────┴──────────────────────────────────────┘
```

The `clj-format.figlet` extension is opt-in: add
`[com.github.danlentz/clj-figlet "0.1.4"]` to your dependencies and
`(require 'clj-format.figlet)` once at startup to enable it. When
loaded it also installs a `[:figlet ...]` DSL directive for standalone
banners anywhere else in the DSL.

### Markdown for docs and READMEs

One style keyword produces GitHub-flavored markdown with per-column
alignment markers in the header rule. The table stays aligned in the
raw markdown source *and* in the rendered document:

```clojure
(fmt/clj-format true
  [:table {:style :markdown}
    [:col :name  {:width 12}]
    [:col :score {:width 8 :align :right}]
    [:col :grade {:width 8 :align :center}]]
  [{:name "Alice" :score 95 :grade "A"}
   {:name "Bob"   :score 82 :grade "B"}
   {:name "Carol" :score 71 :grade "C"}])
```
```
| Name         |    Score |   Grade  |
| :----------- | -------: | :------: |
| Alice        |       95 |     A    |
| Bob          |       82 |     B    |
| Carol        |       71 |     C    |
```

### Aggregation in the footer

`:footer` computes `:sum`, `:avg`, `:min`, `:max`, `:count`, or any
custom function across the data and renders the result as a new row
with the same column directives — so a summed quantity stays
comma-grouped, a summed price stays monetary:

```clojure
(def inventory
  [{:item "Widget"   :qty 100  :price 9.99}
   {:item "Gadget"   :qty 42   :price 24.50}
   {:item "Sprocket" :qty 1200 :price 3.75}])

(fmt/clj-format true
  [:table {:style :unicode :header-case :upcase
           :footer {:label "Total" :fns {:qty :sum :price :sum}}}
    [:col :item  {:width 15}]
    [:col :qty   {:width 10 :align :right :format [:int {:group true}]}]
    [:col :price {:width 12 :align :right :format :money}]]
  inventory)
```
```
┌─────────────────┬────────────┬──────────────┐
│ ITEM            │        QTY │        PRICE │
├─────────────────┼────────────┼──────────────┤
│ Widget          │        100 │         9.99 │
│ Gadget          │         42 │        24.50 │
│ Sprocket        │      1,200 │         3.75 │
├─────────────────┼────────────┼──────────────┤
│ Total           │      1,342 │        38.24 │
└─────────────────┴────────────┴──────────────┘
```

Column source and option docstrings live in
[`src/clj_format/table.cljc`](src/clj_format/table.cljc); nine border
styles ship built-in (`:ascii`, `:unicode`, `:rounded`, `:heavy`,
`:double`, `:markdown`, `:org`, `:simple`, `:none`), and every column
option (width, alignment, overflow, case, title, computed keys) is
documented there.

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

### Tabular status board with `:justify`
```clojure
;; cl-format:
;; ~36<Task~;Owner~;State~>~%~{~36<~A~;~A~;~A~>~%~}

(clj-format nil
  [[:justify {:width 36} "Task" "Owner" "State"] :nl
   [:each
    [:justify {:width 36} :str :str :str] :nl]]
  ["Parser port" "Dan" "done"
   "CLJS parity" "Dan" "green"])
;; =>
;; Task           Owner           State
;; Parser port         Dan         done
;; CLJS parity         Dan        green
```

### Tabular numeric report with tabs
```clojure
;; cl-format:
;; ~A~16T~A~28T~A~46T~A~%~14,,,'-A~16T~10,,,'-A~28T~16,,,'-A~46T~5,,,'-A~%~:{~A~16T~6,2F~28T~V~~46T~:*~D~%~}

(clj-format nil
  ["Name" [:tab {:col 16}] "Value" [:tab {:col 28}] "Histogram" [:tab {:col 46}] "Count" :nl
   [:str {:width 14 :fill \-}] [:tab {:col 16}]
   [:str {:width 10 :fill \-}] [:tab {:col 28}]
   [:str {:width 16 :fill \-}] [:tab {:col 46}]
   [:str {:width 5 :fill \-}] :nl
   [:each {:from :sublists}
    :str [:tab {:col 16}]
    [:float {:width 6 :decimals 2}] [:tab {:col 28}]
    [:tilde {:count :V}] [:tab {:col 46}]
    :back :int :nl]]
  "" "" "" ""
  [["Alpha" 3.14 5]
   ["Beta" 12.0 2]
   ["Gamma" 98.5 9]
   ["Delta" 42.42 7]])
;; =>
;; Name            Value       Histogram         Count
;; --------------  ----------  ----------------  -----
;; Alpha             3.14      ~~~~~             5
;; Beta             12.00      ~~                2
;; Gamma            98.50      ~~~~~~~~~         9
;; Delta            42.42      ~~~~~~~           7
```

### Wrapped notation with `:logical-block`
```clojure
;; cl-format:
;; ~<rgb(~;~D, ~D, ~D~;)~:>

(clj-format nil
  [[:logical-block "rgb(" [:int ", " :int ", " :int] ")"]]
  [255 140 0])
;; => "rgb(255, 140, 0)"
```

### Word-wrapped prose
```clojure
;; cl-format:
;; ~%~%~{~<~%~0,20:;~a ~>~}

(clj-format nil
  [:nl :nl
   [:each
    [:justify :nl
     [:clause {:width 0 :pad-step 20 :pad-before true}
      :str " "]]]]
  ["The" "power" "of" "FORMAT" "is"
   "that" "it" "can" "wrap" "words"
   "beautifully."])
;; =>
;;
;; The power of FORMAT
;; is that it can wrap
;; words beautifully.
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

Drop-in replacement for host `cl-format`:
- `clojure.pprint/cl-format` on the JVM
- `cljs.pprint/cl-format` in ClojureScript

**`writer`** — output destination:
- `nil` or `false` — return formatted string
- `true` — print to the host default output, return nil
- a writer object — write to it, return nil

Writer details are host-specific:
- Clojure uses `java.io.Writer`
- ClojureScript uses `cljs.core/IWriter`

**`fmt`** — format specification:
- **string** — passed directly to `cl-format` (full backward compatibility)
- **vector** — compiled from the DSL to a format string, then passed to `cl-format`
- **keyword** — shorthand for a single bare directive (e.g., `:str` for `~A`)
- **`[:table ...]` vector** — rendered via the table facility
- **extension forms** (e.g. `[:figlet ...]`) — preprocessed by any
  extension registered in `*dsl-preprocessor*`

```clojure
(fmt/clj-format nil "~D item~:P" 5)                            ;; => "5 items"
(fmt/clj-format nil [:int " item" [:plural {:rewind true}]] 5) ;; => "5 items"
(fmt/clj-format nil :cardinal 42)                               ;; => "forty-two"

(fmt/clj-format nil [:table :name :age]
                [{:name "Alice" :age 30} {:name "Bob" :age 25}])

(fmt/clj-format true [:figlet {:font "small"} "HI"])            ;; requires clj-format.figlet
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

### `clj-format.core/table-dsl`

```clojure
(fmt/table-dsl spec rows)
```

Build the table DSL and argument list without rendering. Returns a map
`{:dsl [...] :args [...]}`. Useful for inspecting the generated DSL
expression or calling `clj-format` directly with it.

```clojure
(fmt/table-dsl [:table :name :age] [{:name "Alice" :age 30}])
;; => {:dsl  ["+-----+...\n..." ...]
;;     :args ["Name" "Age" [["Alice" 30]]]}
```

### `clj-format.core/*dsl-preprocessor*`

A dynamic var holding a function applied to every DSL vector before
compilation. Defaults to `identity`. Extension namespaces (currently
`clj-format.figlet`) install transformers here to expand custom
directives. See [doc/extensions.md](doc/extensions.md) if you want to
add your own.

## Development

```
lein test                              # run all tests
lein test clj-format.core-test        # API mechanics
lein test clj-format.parser-test       # parser tests
lein test clj-format.compiler-test     # compiler + round-trip tests
lein test clj-format.examples-test     # cl-format output equivalence
./bin/test-cljs                        # compile once, run shared CLJS suite via Node
bb test/clj_format/bb_runner.clj       # full Babashka suite
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

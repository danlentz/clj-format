# Table Formatting Tutorial

Tables in clj-format are a first-class DSL form. A table specification
follows the Hiccup convention used throughout the library:

```clojure
[:table opts? & cols]
```

Tables render through the same `clj-format` entry point, with the same
writer semantics, as every other format call. There is no separate
`format-table` or `print-table` function — the one unified API handles
everything.

```clojure
(require '[clj-format.core :as fmt])

(fmt/clj-format nil  [:table :name :age] rows)  ;; return a string
(fmt/clj-format true [:table :name :age] rows)  ;; print to *out*
(fmt/clj-format sw   [:table :name :age] rows)  ;; write to a Writer
```

## Quick Start

The simplest spec is just the directive and a seq of maps — columns
are inferred from the first row, widths auto-size, and keyword keys
are humanized into titles (`:first-name` → `"First Name"`):

```clojure
(def staff
  [{:name "Alice" :age 30 :role "Admin"}
   {:name "Bob"   :age 25 :role "User"}
   {:name "Carol" :age 35 :role "Editor"}])

(fmt/clj-format true [:table] staff)
```
```
+-------+-----+--------+
| Name  | Age | Role   |
+-------+-----+--------+
| Alice | 30  | Admin  |
| Bob   | 25  | User   |
| Carol | 35  | Editor |
+-------+-----+--------+
```

The table body accepts three column shapes, in order of terseness:

```clojure
:name                                    ;; bare keyword
[:col :name]                             ;; explicit, same as above
[:col :name {:width 20 :align :right}]   ;; with options
```

Mix and match as needed. Bare keywords are for quick filters; the
`[:col ...]` form takes over as soon as you need width, alignment, a
typed format, or any other option.

## Column Options

Column specs are either `:name` (bare keyword) or `[:col :name opts]`
(Hiccup with options). Options:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:title` | string | (humanized key) | Header text |
| `:width` | integer | (auto) | Fixed column width |
| `:min-width` | integer | (title length) | Minimum auto-sized width |
| `:max-width` | integer | nil | Maximum auto-sized width |
| `:align` | keyword | `:left` | `:left`, `:right`, or `:center` |
| `:title-align` | keyword | (from `:align`) | Header alignment |
| `:format` | keyword/vector/fn | `:str` | Cell format (see DSL Formats below) |
| `:overflow` | keyword | `:ellipsis` | `:ellipsis`, `:clip`, or `:wrap` |
| `:ellipsis` | string | `"..."` | Ellipsis marker |
| `:case` | keyword | nil | Case conversion for values |

## Table Options

Table-level options go in the second position (the opts map in the
Hiccup form):

```clojure
[:table {:style :unicode :header-case :upcase}
  :name :age :role]
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:style` | keyword/map | `:ascii` | Border style |
| `:header` | boolean | `true` | Show header row |
| `:header-rule` | boolean | `true` | Rule under header |
| `:header-case` | keyword | `:capitalize` | Header case conversion |
| `:top-rule` | boolean | `true` | Top border |
| `:bottom-rule` | boolean | `true` | Bottom border |
| `:row-rules` | boolean | `false` | Rules between data rows |
| `:nil-value` | string | `""` | Display for nil values |
| `:footer` | map | nil | Footer config (see Footer below) |
| `:defaults` | map | `{}` | Default options applied to all columns |

## Border Styles

Nine built-in styles, selected with `:style`:

| Style | Character set | Typical use |
|-------|---------------|-------------|
| `:ascii` (default) | `+`, `-`, `\|` | Portable terminals, logs, anywhere |
| `:unicode` | Box-drawing `┌─┬┐` | Modern terminals |
| `:rounded` | Soft corners `╭─┬╮` | Reports, softer look |
| `:heavy` | Thick strokes `┏━┳┓` | Emphasis |
| `:double` | Double lines `╔═╦╗` | Emphasis, classic feel |
| `:markdown` | Pipes with alignment markers | README/GitHub documents |
| `:org` | Org-mode `\|`/`+` | Emacs buffers |
| `:simple` | Horizontal rules only | Pipes, no box |
| `:none` | Spaces only | Plain alignment |

### Box-style gallery

The basic box styles share the same structure — only the border
characters differ. Here is the same 3-column table rendered in four
different styles:

```clojure
(def cities
  [{:city "New York" :pop 8336817}
   {:city "London"   :pop 8982000}
   {:city "Tokyo"    :pop 13960000}])

(def city-cols
  [[:col :city {:width 12}]
   [:col :pop  {:width 12 :align :right
                :format [:int {:group true}] :title "Population"}]])
```

`:unicode`:

```
┌──────────────┬──────────────┐
│ City         │   Population │
├──────────────┼──────────────┤
│ New York     │    8,336,817 │
│ London       │    8,982,000 │
│ Tokyo        │   13,960,000 │
└──────────────┴──────────────┘
```

`:rounded`:

```
╭──────────────┬──────────────╮
│ City         │   Population │
├──────────────┼──────────────┤
│ New York     │    8,336,817 │
│ London       │    8,982,000 │
│ Tokyo        │   13,960,000 │
╰──────────────┴──────────────╯
```

`:heavy`:

```
┏━━━━━━━━━━━━━━┳━━━━━━━━━━━━━━┓
┃ City         ┃   Population ┃
┣━━━━━━━━━━━━━━╋━━━━━━━━━━━━━━┫
┃ New York     ┃    8,336,817 ┃
┃ London       ┃    8,982,000 ┃
┃ Tokyo        ┃   13,960,000 ┃
┗━━━━━━━━━━━━━━┻━━━━━━━━━━━━━━┛
```

`:double`:

```
╔══════════════╦══════════════╗
║ City         ║   Population ║
╠══════════════╬══════════════╣
║ New York     ║    8,336,817 ║
║ London       ║    8,982,000 ║
║ Tokyo        ║   13,960,000 ║
╚══════════════╩══════════════╝
```

`:org` follows the same layout with `|` on every edge and `+` at
junctions — the Emacs org-mode convention. `:ascii` is the default
portable form you have already seen.

### Markdown output for docs

`:markdown` produces a GitHub-flavored markdown table with per-column
alignment markers in the header rule, so that right-aligned numeric
columns stay right-aligned when the markdown is rendered:

```clojure
(def students
  [{:name "Alice" :score 95 :grade "A"}
   {:name "Bob"   :score 82 :grade "B"}
   {:name "Carol" :score 71 :grade "C"}])

(fmt/clj-format true
  [:table {:style :markdown}
    [:col :name  {:width 12}]
    [:col :score {:width 8 :align :right}]
    [:col :grade {:width 8 :align :center}]]
  students)
```
```
| Name         |    Score |   Grade  |
| :----------- | -------: | :------: |
| Alice        |       95 |     A    |
| Bob          |       82 |     B    |
| Carol        |       71 |     C    |
```

### Borderless layouts for pipes and logs

`:simple` and `:none` drop vertical borders — useful for output that
will be piped into other tools or displayed in fixed-width logs.
`:simple` keeps a horizontal rule under the header; `:none` drops
rules entirely:

```clojure
(def directory
  [{:name "Joe"  :ext 3215 :office "Room 12" :status :active}
   {:name "Mary" :ext 3246 :office "Room 7"  :status :away}])

(fmt/clj-format true
  [:table {:style :simple}
    [:col :name   {:width 10}]
    [:col :ext    {:width 5 :align :right :format :int}]
    [:col :office {:width 10}]
    [:col :status {:width 8 :align :center
                   :format (fn [s] (if (= :active s) "on" "off"))}]]
  directory)
```
```
Name          Ext  Office       Status
----------  -----  ----------  --------
Joe          3215  Room 12        on
Mary         3246  Room 7         off
```

## DSL Format Showcase

The `:format` option on each column accepts any clj-format DSL directive.
This is what makes the table facility a genuine showcase of the DSL.

### Numeric Formatting

```clojure
;; Comma-grouped integers
[:col :qty {:format [:int {:group true}]}]           ;; 1,200

;; Signed integers
[:col :score {:format [:int {:group true :sign :always}]}]  ;; +1,250

;; Monetary
[:col :price {:format :money}]                        ;; 9.99

;; Signed monetary
[:col :bal {:format [:money {:sign :always}]}]        ;; +50.00

;; Fixed-point float
[:col :rate {:format [:float {:decimals 3}]}]         ;; 0.042

;; Hexadecimal
[:col :code {:format [:hex {:width 4 :fill \0}]}]     ;; 00ff
```

### Special Number Formats

```clojure
[:col :rank  {:format :roman}]      ;; XIV
[:col :count {:format :cardinal}]   ;; forty-two
[:col :place {:format :ordinal}]    ;; forty-second
```

### Boolean and Conditional Formats

```clojure
;; Boolean dispatch
[:col :active {:format [:if "Yes" "No"]}]

;; Numeric dispatch
[:col :level {:format [:choose "Low" "Medium" "High"]}]

;; Truthiness guard
[:col :note {:format [:when "Note: " :str]}]
```

### Putting It All Together

A single table using Roman numerals, signed integers, boolean dispatch,
and signed monetary — five distinct DSL features in one call:

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

## Text Overflow

Every column has an `:overflow` policy that controls what happens when
a cell's content exceeds its `:width`:

- `:ellipsis` (default) — truncate and append `"..."`
- `:clip` — truncate without any marker
- `:wrap` — word-wrap across multiple lines

### Ellipsis

```clojure
(def books
  [{:title "The Hitchhiker's Guide to the Galaxy" :author "Douglas Adams"     :year 1979}
   {:title "Dune"                                 :author "Frank Herbert"     :year 1965}
   {:title "Neuromancer"                          :author "William Gibson"    :year 1984}
   {:title "The Left Hand of Darkness"            :author "Ursula K. Le Guin" :year 1969}])

(fmt/clj-format true
  [:table
    [:col :title  {:width 25 :overflow :ellipsis}]
    [:col :author {:width 15}]
    [:col :year   {:width 6 :align :right}]]
  books)
```
```
+---------------------------+-----------------+--------+
| Title                     | Author          |   Year |
+---------------------------+-----------------+--------+
| The Hitchhiker's Guide... | Douglas Adams   |   1979 |
| Dune                      | Frank Herbert   |   1965 |
| Neuromancer               | William Gibson  |   1984 |
| The Left Hand of Darkness | Ursula K. Le... |   1969 |
+---------------------------+-----------------+--------+
```

The `:ellipsis` option takes a custom marker string, so you can replace
the default `"..."` with `" [more]"`, `"…"`, or any other indicator.
`:overflow :clip` truncates without any marker at all — useful when
every visible character is load-bearing.

### Word Wrapping

With `:overflow :wrap`, long cells flow across multiple physical rows.
Non-wrapping columns show their value only on the first row of each
logical group, and typed numeric columns keep formatting normally:

```clojure
(def catalog
  [{:name "Widget Pro"
    :description "Premium widget with extended warranty and free shipping worldwide"
    :price 49.99}
   {:name "Gadget"
    :description "Basic gadget"
    :price 24.50}
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

Note how `Sprocket Deluxe` gets elided (the name column does not wrap)
while `description` wraps across three physical rows. The row grows
to fit the tallest wrapped cell; any number of columns can wrap
simultaneously and independently.

Notes on wrap mode:
- Wrapping requires an explicit `:width`. Without one, the column
  auto-sizes to the longest value and wrapping never triggers.
- Embedded newlines in the source text are preserved as hard breaks.
- **Lines that already fit are preserved verbatim** — interior
  whitespace is not collapsed. This matters for pre-formatted content
  like ASCII art or nested tables (see below).
- Long words or lines exceeding the column width are broken at the
  width boundary.
- `:row-rules` is disabled in wrap mode to avoid rules between
  continuation rows of the same logical group.

### Embedding Multi-Line Content

Because wrap mode preserves lines that already fit, any pre-formatted
multi-line string drops cleanly into a cell — as long as the column is
at least as wide as the widest line. The recipe is always the same:

1. Column has `:overflow :wrap`
2. Column has a `:format` function returning the rendered string
3. Column `:width` is ≥ the widest line of the rendered content

#### FIGlet banners in a cell

With `clj-format.figlet` loaded (see the main README), you can render
directly via the `[:figlet ...]` directive, or drop a pre-rendered
banner into a table cell using `clj-figlet.core/render` as a column
format:

```clojure
(require '[clj-figlet.core :as cf])

(fmt/clj-format true
  [:table {:style :unicode}
    [:col :name   {:width 12}]
    [:col :banner {:width 30 :overflow :wrap
                   :format (fn [s] (cf/render "small" s))}]]
  [{:name "Alice" :banner "HELLO"}
   {:name "Bob"   :banner "HI"}])
```
```
┌──────────────┬────────────────────────────────┐
│ Name         │ Banner                         │
├──────────────┼────────────────────────────────┤
│ Alice        │  _  _ ___ _    _    ___        │
│              │ | || | __| |  | |  / _ \       │
│              │ | __ | _|| |__| |_| (_) |      │
│              │ |_||_|___|____|____\___/       │
│ Bob          │  _  _ ___                      │
│              │ | || |_ _|                     │
│              │ | __ || |                      │
│              │ |_||_|___|                     │
└──────────────┴────────────────────────────────┘
```

The figlet's internal whitespace is preserved exactly — every space
contributes to the ASCII art. Make sure the column width covers the
widest rendered line or the banner will get re-wrapped.

#### Nested tables in a cell

Any rendered table is itself a multi-line string, so one table can
live inside another. The outer column's `:format` function renders
the inner table; wrap mode expands the result across physical rows:

```clojure
(def inner-fn
  (fn [rows]
    (fmt/clj-format nil
      [:table {:style :ascii :header false}
        [:col :k {:width 5}]
        [:col :v {:width 5 :align :right}]]
      rows)))

(fmt/clj-format true
  [:table {:style :unicode}
    [:col :group   {:width 10}]
    [:col :details {:width 22 :overflow :wrap :format inner-fn}]]
  [{:group "Team A"
    :details [{:k "Mon" :v 10} {:k "Tue" :v 15} {:k "Wed" :v 8}]}
   {:group "Team B"
    :details [{:k "Mon" :v 7} {:k "Tue" :v 12}]}])
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

Inner and outer tables can use different border styles — an ASCII
inner table inside a `:unicode` outer table is perfectly legible.
Same-style nesting (unicode inside unicode, for example) also works
but can be visually noisier.

## Row Rules for Scanning Dense Data

For dense rows with many numeric columns, `:row-rules true` draws a
horizontal rule between every row so the eye can track across without
losing the line. Here combined with `:rounded` borders, an `:roman`
rank column, and signed grouped integers:

```clojure
(def leaderboard
  [{:rank 1 :team "Red"    :wins 12 :delta  +3}
   {:rank 2 :team "Blue"   :wins  9 :delta  -1}
   {:rank 3 :team "Green"  :wins  7 :delta  +2}])

(fmt/clj-format true
  [:table {:style :rounded :row-rules true :header-case :upcase}
    [:col :rank  {:width 6  :align :center :format :roman}]
    [:col :team  {:width 10}]
    [:col :wins  {:width 6  :align :right :format :int}]
    [:col :delta {:width 7  :align :right :format [:int {:sign :always}]}]]
  leaderboard)
```
```
╭────────┬────────────┬────────┬─────────╮
│  RANK  │ TEAM       │   WINS │   DELTA │
├────────┼────────────┼────────┼─────────┤
│    I   │ Red        │     12 │      +3 │
├────────┼────────────┼────────┼─────────┤
│   II   │ Blue       │      9 │      -1 │
├────────┼────────────┼────────┼─────────┤
│   III  │ Green      │      7 │      +2 │
╰────────┴────────────┴────────┴─────────╯
```

## Footer with Aggregation

Add a summary row below the data with aggregate functions:

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

Built-in aggregate functions: `:sum`, `:avg`, `:min`, `:max`, `:count`.
Custom aggregates are also supported as `(fn [values] result)`.

## Header Options

### Case Conversion

```clojure
{:header-case :upcase}       ;; NAME, AGE
{:header-case :capitalize}   ;; Name, Age (default)
{:header-case :downcase}     ;; name, age
{:header-case nil}           ;; no conversion, use title as-is
```

### Suppressing Headers

```clojure
(fmt/clj-format true [:table {:header false} :name :val] data)
```

## Nil Handling in Typed Columns

By default nil renders as an empty string, but you can supply a
`:nil-value` placeholder. More interestingly, nil works seamlessly in
*typed* columns — the facility detects nil cells and silently falls
back to preprocessed rendering for that column, so `:int`, `:money`,
and other typed formats never crash on missing data:

```clojure
(def readings
  [{:sensor "A1" :temp 72.4 :pressure 1013.2}
   {:sensor "A2" :temp nil  :pressure 1012.8}
   {:sensor "B1" :temp 68.1 :pressure nil}])

(fmt/clj-format true
  [:table {:style :heavy :nil-value "--"}
    [:col :sensor   {:width 8}]
    [:col :temp     {:width 8 :align :right :format [:float {:decimals 1}]
                     :title "Temp °F"}]
    [:col :pressure {:width 10 :align :right :format [:float {:decimals 1}]
                     :title "Pressure"}]]
  readings)
```
```
┏━━━━━━━━━━┳━━━━━━━━━━┳━━━━━━━━━━━━┓
┃ Sensor   ┃  Temp °F ┃   Pressure ┃
┣━━━━━━━━━━╋━━━━━━━━━━╋━━━━━━━━━━━━┫
┃ A1       ┃     72.4 ┃     1013.2 ┃
┃ A2       ┃       -- ┃     1012.8 ┃
┃ B1       ┃     68.1 ┃         -- ┃
┗━━━━━━━━━━┻━━━━━━━━━━┻━━━━━━━━━━━━┛
```

Notice that the `--` placeholders still obey each column's width and
alignment — the fallback is per-column, not per-row.

## Computed Columns and Function Formats

### Computed Columns

Use a function as the column key to derive values from the row:

```clojure
(def people
  [{:first "Alice" :last "Smith" :active true}
   {:first "Bob"   :last "Jones" :active false}])

(fmt/clj-format true
  [:table
    [:col (fn [r] (str (:first r) " " (:last r)))
          {:title "Full Name" :width 18}]
    [:col :active {:width 10 :align :center
                   :format (fn [v] (if v "Active" "Inactive"))}]]
  people)
```
```
+--------------------+------------+
| Full Name          |   Active   |
+--------------------+------------+
| Alice Smith        |   Active   |
| Bob Jones          |  Inactive  |
+--------------------+------------+
```

### Function Formats

Use a function as `:format` for arbitrary value-to-string transformations.
The column's `:width` then pads the string result.

## Inspecting the Generated DSL

`table-dsl` returns the DSL expression and argument list instead of
rendering. This is useful for learning how the DSL works, debugging, or
reusing the generated format.

```clojure
(fmt/table-dsl [:table :name :age] [{:name "Alice" :age 30}])
;; =>
;; {:dsl  ["+------+-----+" :nl
;;         "| " [:str {:width 4 :case :capitalize}]
;;         " | " [:str {:width 3 :case :capitalize :pad :left}] " |" :nl
;;         "+------+-----+" :nl
;;         [:each {:from :sublists}
;;           "| " [:str {:width 4}] " | " [:str {:width 3}] " |" :nl]
;;         "+------+-----+"]
;;  :args ["Name" "Age" [["Alice" 30]]]}
```

The DSL shows exactly how the table is constructed:
- Rule strings are literal text
- Header cells use `:str` with `:case :capitalize`
- Data cells use typed directives (e.g. `:int` when `:format :int` is set)
- `:each {:from :sublists}` iterates over data rows
- The argument list feeds header titles, then the data sublists

You can render this yourself:

```clojure
(apply fmt/clj-format nil dsl args)
```

## See Also

- **`clj-format.figlet`** — optional `:figlet` directive for ASCII-art
  banners. When loaded, `[:figlet {:font "small"} "HELLO"]` expands to
  a multi-line banner string anywhere in a DSL form, and the same
  `clj-figlet.core/render` call also works as a column `:format`
  function for banner columns.
- **`doc/dsl.md`** — full DSL reference covering every cl-format
  directive. Any of those directives can be used as a column `:format`.
- **`doc/examples.md`** — 50+ side-by-side cl-format examples.

## Summary

```clojure
;; The one and only entry point — writer semantics match every
;; other clj-format call:
(fmt/clj-format nil  [:table ...] rows)   ;; return string
(fmt/clj-format true [:table ...] rows)   ;; print
(fmt/clj-format sw   [:table ...] rows)   ;; write to writer

;; DSL inspection
(fmt/table-dsl [:table ...] rows)          ;; => {:dsl ... :args ...}
```

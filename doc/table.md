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

The simplest spec — just the directive and a seq of maps:

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

Columns are inferred from the first row's map keys. Widths auto-size.
Headers are humanized from keywords: `:first-name` becomes `"First Name"`.

## Selecting Columns

Bare keywords inside `[:table ...]` are columns:

```clojure
(fmt/clj-format true [:table :name :role] staff)
```
```
+-------+--------+
| Name  | Role   |
+-------+--------+
| Alice | Admin  |
| Bob   | User   |
| Carol | Editor |
+-------+--------+
```

`[:col :name]` is an equivalent, fully explicit form. Use it when a
column needs options:

```clojure
(fmt/clj-format true
  [:table
    [:col :name {:width 20}]
    [:col :role {:width 12 :align :right}]]
  staff)
```

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

### `:ascii` (default)

```clojure
(fmt/clj-format true [:table :name :score] scores)
```
```
+-------+-------+
| Name  | Score |
+-------+-------+
| Alice |    95 |
| Bob   |    82 |
+-------+-------+
```

### `:unicode`

```clojure
(fmt/clj-format true
  [:table {:style :unicode :header-case :upcase}
    [:col :name {:width 12}]
    [:col :dept {:width 10}]
    [:col :salary {:width 10 :align :right :format [:int {:group true}]}]]
  employees)
```
```
┌──────────────┬────────────┬────────────┐
│ NAME         │ DEPT       │     SALARY │
├──────────────┼────────────┼────────────┤
│ Alice        │ Eng        │     95,000 │
│ Bob          │ Sales      │     72,000 │
│ Carol        │ Eng        │     88,000 │
└──────────────┴────────────┴────────────┘
```

### `:rounded`

```clojure
(fmt/clj-format true [:table {:style :rounded} :name :score] scores)
```
```
╭──────────┬──────────╮
│ Name     │    Score │
├──────────┼──────────┤
│ Alice    │       95 │
╰──────────┴──────────╯
```

### `:heavy`

```clojure
(fmt/clj-format true
  [:table {:style :heavy :header-case :upcase}
    [:col :city {:width 12}]
    [:col :pop  {:width 12 :align :right
                 :format [:int {:group true}] :title "Population"}]]
  cities)
```
```
┏━━━━━━━━━━━━━━┳━━━━━━━━━━━━━━┓
┃ CITY         ┃   POPULATION ┃
┣━━━━━━━━━━━━━━╋━━━━━━━━━━━━━━┫
┃ New York     ┃    8,336,817 ┃
┃ London       ┃    8,982,000 ┃
┃ Tokyo        ┃   13,960,000 ┃
┗━━━━━━━━━━━━━━┻━━━━━━━━━━━━━━┛
```

### `:double`

```clojure
(fmt/clj-format true
  [:table {:style :double}
    [:col :item   {:width 10}]
    [:col :status {:width 8 :align :center}]]
  tasks)
```
```
╔════════════╦══════════╗
║ Item       ║  Status  ║
╠════════════╬══════════╣
║ Task A     ║   Done   ║
║ Task B     ║    WIP   ║
║ Task C     ║   Todo   ║
╚════════════╩══════════╝
```

### `:markdown`

Produces GitHub-flavored markdown tables with alignment markers:

```clojure
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

### `:org`

Emacs org-mode style:

```clojure
(fmt/clj-format true
  [:table {:style :org}
    [:col :task  {:width 15}]
    [:col :owner {:width 10}]
    [:col :state {:width 8}]]
  tasks)
```
```
|-----------------+------------+----------|
| Task            | Owner      | State    |
|-----------------+------------+----------|
| Parser port     | Dan        | done     |
| CLJS parity     | Dan        | green    |
|-----------------+------------+----------|
```

### `:simple`

No vertical borders, columns separated by spaces:

```clojure
(fmt/clj-format true
  [:table {:style :simple}
    [:col :name   {:width 12}]
    [:col :ext    {:width 8 :align :right}]
    [:col :office {:width 10}]]
  directory)
```
```
Name               Ext  Office
------------  --------  ----------
Joe               3215  Room 12
Mary              3246  Room 7
```

### `:none`

No borders, no rules — just aligned columns:

```clojure
(fmt/clj-format true [:table {:style :none} :name :age] staff)
```
```
Name   Age
Alice  30
Bob    25
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

### Custom Ellipsis

```clojure
(fmt/clj-format true
  [:table {:header false}
    [:col :desc {:width 20 :overflow :ellipsis :ellipsis " [more]"}]]
  [{:desc "Short"}
   {:desc "A description that is way too long to fit"}])
```
```
+----------------------+
| Short                |
| A description [more] |
+----------------------+
```

### Clip

Truncate without any marker:

```clojure
[:col :s {:width 10 :overflow :clip}]
```

### Word Wrapping

With `:overflow :wrap`, long cells flow across multiple physical rows.
Non-wrapping columns show their value only on the first row of each
logical group:

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

Multiple wrapping columns are supported — the row grows to fit the
tallest wrapped cell:

```clojure
(fmt/clj-format true
  [:table {:style :unicode}
    [:col :title {:width 15 :overflow :wrap}]
    [:col :notes {:width 25 :overflow :wrap}]]
  [{:title "Project Alpha"
    :notes "Initial scoping complete. Timeline pending review."}
   {:title "The Very Long Secondary Initiative"
    :notes "On hold until Q3."}])
```
```
┌─────────────────┬───────────────────────────┐
│ Title           │ Notes                     │
├─────────────────┼───────────────────────────┤
│ Project Alpha   │ Initial scoping complete. │
│                 │ Timeline pending review.  │
│ The Very Long   │ On hold until Q3.         │
│ Secondary       │                           │
│ Initiative      │                           │
└─────────────────┴───────────────────────────┘
```

Notes on wrap mode:
- Wrapping requires an explicit `:width`. Without one, the column
  auto-sizes to the longest value and wrapping never triggers.
- Embedded newlines in the source text are preserved as hard breaks.
- Long words exceeding the column width are broken at the width
  boundary.
- `:row-rules` is disabled in wrap mode to avoid rules between
  continuation rows of the same logical group.

## Row Rules

Add horizontal rules between every data row:

```clojure
(fmt/clj-format true [:table {:style :unicode :row-rules true} :name :age] staff)
```
```
┌────────────┬───────┐
│ Name       │   Age │
├────────────┼───────┤
│ Alice      │    30 │
├────────────┼───────┤
│ Bob        │    25 │
├────────────┼───────┤
│ Carol      │    35 │
└────────────┴───────┘
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

## Nil Handling

By default, nil values render as empty strings. Use `:nil-value` to
customize:

```clojure
(def contacts
  [{:name "Alice" :email "alice@co.com"}
   {:name "Bob"   :email nil}
   {:name "Carol" :email "carol@co.com"}])

(fmt/clj-format true
  [:table {:nil-value "(none)"}
    [:col :name  {:width 10}]
    [:col :email {:width 20}]]
  contacts)
```
```
+------------+----------------------+
| Name       | Email                |
+------------+----------------------+
| Alice      | alice@co.com         |
| Bob        | (none)               |
| Carol      | carol@co.com         |
+------------+----------------------+
```

Nil values in typed columns (`:int`, `:money`, etc.) are automatically
handled by switching to preprocessed mode — no crash, no special effort.

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

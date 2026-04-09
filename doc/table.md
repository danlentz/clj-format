# clj-format.table Tutorial

A tabular formatting facility built on the clj-format DSL. It constructs
DSL expressions from declarative table specifications and renders entire
tables via a single `clj-format` call.

## Quick Start

```clojure
(require '[clj-format.table :refer [print-table format-table table-dsl]])
```

The simplest call — just pass a seq of maps:

```clojure
(print-table [{:name "Alice" :age 30 :role "Admin"}
              {:name "Bob"   :age 25 :role "User"}
              {:name "Carol" :age 35 :role "Editor"}])
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

Columns are inferred from map keys. Widths auto-size to fit the data.
Headers are humanized from keywords: `:first-name` becomes `"First Name"`.

## Selecting Columns

Pass a vector of keywords to choose which columns to show and in what order:

```clojure
(print-table [:name :role]
  [{:name "Alice" :age 30 :role "Admin"}
   {:name "Bob"   :age 25 :role "User"}])
```
```
+-------+-------+
| Name  | Role  |
+-------+-------+
| Alice | Admin |
| Bob   | User  |
+-------+-------+
```

## Column Specifications

For full control, pass column-spec maps instead of bare keywords:

```clojure
(print-table
  [{:key :product :width 15 :title "Product"}
   {:key :qty     :width 10 :align :right :format [:int {:group true}] :title "Quantity"}
   {:key :price   :width 12 :align :right :format :money :title "Unit Price"}]
  [{:product "Widget"   :qty 1200  :price 9.99}
   {:product "Gadget"   :qty 42    :price 24.50}
   {:product "Sprocket" :qty 85000 :price 3.75}])
```
```
+-----------------+------------+--------------+
| Product         |   Quantity |   Unit Price |
+-----------------+------------+--------------+
| Widget          |      1,200 |         9.99 |
| Gadget          |         42 |        24.50 |
| Sprocket        |     85,000 |         3.75 |
+-----------------+------------+--------------+
```

Column options:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:key` | keyword/fn | (required) | Map key to extract, or `(fn [row] value)` |
| `:title` | string | (humanized key) | Header text |
| `:width` | integer | (auto) | Fixed column width |
| `:min-width` | integer | (title length) | Minimum auto-sized width |
| `:max-width` | integer | nil | Maximum auto-sized width |
| `:align` | keyword | `:left` | `:left`, `:right`, or `:center` |
| `:title-align` | keyword | (from `:align`) | Header alignment |
| `:format` | keyword/vector/fn | `:str` | Cell format (see DSL Formats) |
| `:overflow` | keyword | `:ellipsis` | `:ellipsis` or `:clip` |
| `:ellipsis` | string | `"..."` | Ellipsis marker |
| `:case` | keyword | nil | Case conversion for values |


## Border Styles

Nine built-in styles, selected with `:style`:

### `:ascii` (default)

```clojure
(print-table [:name :score] data)
```
```
+-------+-------+
| Name  | Score |
+-------+-------+
| Alice |    95 |
+-------+-------+
```

### `:unicode`

```clojure
(print-table [:name :dept :salary] employees
  {:style :unicode :header-case :upcase})
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
(print-table [:name :score] data {:style :rounded})
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
(print-table [{:key :city :width 12}
              {:key :pop  :width 12 :align :right
               :format [:int {:group true}] :title "Population"}]
  cities
  {:style :heavy :header-case :upcase})
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
(print-table [{:key :item :width 10}
              {:key :status :width 8 :align :center}]
  tasks
  {:style :double})
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
(print-table
  [{:key :name :width 12}
   {:key :score :width 8 :align :right}
   {:key :grade :width 8 :align :center}]
  students
  {:style :markdown})
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
(print-table
  [{:key :task :width 15} {:key :owner :width 10} {:key :state :width 8}]
  tasks
  {:style :org})
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
(print-table
  [{:key :name :width 12}
   {:key :ext  :width 8 :align :right :title "Ext"}
   {:key :office :width 10}]
  directory
  {:style :simple})
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
(print-table [:name :age] data {:style :none})
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
{:key :qty :format [:int {:group true}]}          ;; 1,200

;; Signed integers
{:key :score :format [:int {:group true :sign :always}]}  ;; +1,250

;; Monetary
{:key :price :format :money}                       ;; 9.99

;; Signed monetary
{:key :bal :format [:money {:sign :always}]}       ;; +50.00

;; Fixed-point float
{:key :rate :format [:float {:decimals 3}]}        ;; 0.042

;; Hexadecimal
{:key :code :format [:hex {:width 4 :fill \0}]}   ;; 00ff
```

### Special Number Formats

```clojure
;; Roman numerals
{:key :rank :format :roman}                        ;; XIV

;; English cardinal words
{:key :count :format :cardinal}                    ;; forty-two

;; English ordinal words
{:key :place :format :ordinal}                     ;; forty-second
```

### Boolean and Conditional Formats

```clojure
;; Boolean dispatch
{:key :active :format [:if "Yes" "No"]}

;; Numeric dispatch
{:key :level :format [:choose "Low" "Medium" "High"]}

;; Truthiness guard
{:key :note :format [:when "Note: " :str]}
```

### Putting It All Together

A single table using Roman numerals, signed integers, boolean dispatch,
and signed monetary — five distinct DSL features in one call:

```clojure
(print-table
  [{:key :rank    :width 8  :align :center :format :roman}
   {:key :name    :width 15}
   {:key :score   :width 12 :align :right  :format [:int {:group true :sign :always}]}
   {:key :active  :width 10 :align :center :format [:if "Yes" "No"]}
   {:key :balance :width 14 :align :right  :format [:money {:sign :always}]}]
  [{:rank 1 :name "Alice" :score 1250 :active true  :balance 50.0}
   {:rank 2 :name "Bob"   :score 890  :active true  :balance -12.30}
   {:rank 3 :name "Carol" :score 450  :active false :balance 0.0}]
  {:style :rounded :header-case :upcase})
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

### Ellipsis (default)

When text exceeds the column width, it is truncated with `"..."`:

```clojure
(print-table
  [{:key :title  :width 25 :overflow :ellipsis}
   {:key :author :width 15}
   {:key :year   :width 6  :align :right}]
  [{:title "The Hitchhiker's Guide to the Galaxy" :author "Douglas Adams"    :year 1979}
   {:title "Dune"                                 :author "Frank Herbert"    :year 1965}
   {:title "Neuromancer"                          :author "William Gibson"   :year 1984}
   {:title "The Left Hand of Darkness"            :author "Ursula K. Le Guin" :year 1969}])
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
{:key :desc :width 20 :overflow :ellipsis :ellipsis " [more]"}
```
```
+----------------------+
| Desc                 |
+----------------------+
| Short                |
| A description [more] |
+----------------------+
```

### Clip

Truncate without any marker:

```clojure
{:key :s :width 10 :overflow :clip}
```


## Row Rules

Add horizontal rules between every data row:

```clojure
(print-table
  [{:key :name :width 10} {:key :age :width 5 :align :right}]
  [{:name "Alice" :age 30} {:name "Bob" :age 25} {:name "Carol" :age 35}]
  {:style :unicode :row-rules true})
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
(print-table
  [{:key :item  :width 15}
   {:key :qty   :width 10 :align :right :format [:int {:group true}]}
   {:key :price :width 12 :align :right :format :money}]
  [{:item "Widget"   :qty 100  :price 9.99}
   {:item "Gadget"   :qty 42   :price 24.50}
   {:item "Sprocket" :qty 1200 :price 3.75}]
  {:style :unicode :header-case :upcase
   :footer {:label "Total" :fns {:qty :sum :price :sum}}})
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
(print-table [:name :val] data {:header false})
```
```
+-------+-----+
| alpha | one |
| beta  | two |
+-------+-----+
```


## Nil Handling

By default, nil values render as empty strings. Use `:nil-value` to
customize:

```clojure
(print-table
  [{:key :name :width 10} {:key :email :width 20}]
  [{:name "Alice" :email "alice@co.com"}
   {:name "Bob"   :email nil}
   {:name "Carol" :email "carol@co.com"}]
  {:nil-value "(none)"})
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

Use a function as `:key` to derive values from the row:

```clojure
(print-table
  [{:key (fn [r] (str (:first r) " " (:last r)))
    :title "Full Name" :width 18}
   {:key :active :width 10 :align :center
    :format (fn [v] (if v "Active" "Inactive"))}]
  [{:first "Alice" :last "Smith" :active true}
   {:first "Bob"   :last "Jones" :active false}])
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


## Default Column Options

Apply options to all columns at once with `:defaults`:

```clojure
(print-table [:name :role] data {:defaults {:align :right}})
```


## Inspecting the Generated DSL

`table-dsl` returns the DSL expression and argument list instead of
rendering. This is useful for learning how the DSL works, debugging, or
reusing the generated format.

```clojure
(let [{:keys [dsl args]} (table-dsl
                            [{:key :name :width 10}
                             {:key :age :width 5 :align :right :format :int}]
                            [{:name "Alice" :age 30}])]
  (println "DSL:" (pr-str dsl))
  (println "Args:" (pr-str args)))
```
```
DSL:  ["+------------+-------+" :nl
       "| " [:str {:width 10, :case :capitalize}]
       " | " [:str {:width 5, :case :capitalize, :pad :left}] " |" :nl
       "+------------+-------+" :nl
       [:each {:from :sublists}
        "| " [:str {:width 10}] " | " [:int {:width 5}] " |" :nl]
       "+------------+-------+"]
Args: ["Name" "Age" [["Alice" 30]]]
```

The DSL shows exactly how the table is constructed:
- Rule strings are literal text
- Header cells use `:str` with `:case :capitalize`
- Data cells use typed directives (`:int` for the age column)
- `:each {:from :sublists}` iterates over data rows
- The argument list feeds header titles, then the data sublists

You can render this yourself:

```clojure
(apply clj-format.core/clj-format nil dsl args)
```


## Table Options Reference

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:style` | keyword/map | `:ascii` | Border style |
| `:header` | boolean | `true` | Show header row |
| `:header-rule` | boolean | `true` | Rule under header |
| `:header-case` | keyword | `:capitalize` | Header case conversion |
| `:top-rule` | boolean | `true` | Top border |
| `:bottom-rule` | boolean | `true` | Bottom border |
| `:row-rules` | boolean | `false` | Rules between rows |
| `:nil-value` | string | `""` | Display for nil values |
| `:footer` | map | nil | Footer config |
| `:defaults` | map | `{}` | Default column options |

## API Summary

```clojure
;; Returns a string
(format-table rows)
(format-table rows opts)
(format-table columns rows)
(format-table columns rows opts)

;; Prints to *out*
(print-table rows)
(print-table rows opts)
(print-table columns rows)
(print-table columns rows opts)

;; Returns {:dsl [...] :args [...]}
(table-dsl rows)
(table-dsl rows opts)
(table-dsl columns rows)
(table-dsl columns rows opts)
```

# Tables in clj-format

Tables are a first-class DSL form: `[:table opts? & cols]`. They render
through the same `clj-format` entry point as every other format call —
no separate function, no new writer rules, no extra dependency — and
the `[:col :key opts]` children are Hiccup all the way down. Every
column `:format` accepts any directive from the clj-format DSL, so
every feature of the library composes into a table cell.

```clojure
(require '[clj-format.core :as fmt])

(fmt/clj-format nil  [:table ...] rows)   ;; return string
(fmt/clj-format true [:table ...] rows)   ;; print to *out*
(fmt/clj-format sw   [:table ...] rows)   ;; write to a Writer
```

Here is what that unlocks.

## A report in a single spec

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

## Word wrap beside typed columns

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

## Anything multi-line goes in a cell

Wrap mode preserves interior whitespace verbatim, so any pre-formatted
string drops cleanly into a cell. Here an ASCII inner table is
rendered as a column format and lives inside a Unicode outer table:

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

The same recipe — `:overflow :wrap` plus a function `:format` — puts
any multi-line content in a cell. Load `clj-format.figlet` and
`clj-figlet.core/render` becomes a drop-in banner column; any external
renderer does the same.

## Markdown for docs and READMEs

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

## Aggregation in the footer

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

## See also

- [`doc/dsl.md`](dsl.md) — full clj-format DSL reference
- [`doc/examples.md`](examples.md) — 50+ side-by-side cl-format examples
- [`src/clj_format/table.cljc`](../src/clj_format/table.cljc) —
  the implementation and complete docstrings for every option

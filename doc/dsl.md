# clj-format DSL Reference

A complete definition of the s-expression DSL that clj-format compiles into
cl-format format strings. Covers all 33 cl-format directives.

## Structure

A format specification is a **body** — a vector of **elements**. Each
element is one of:

- **string** — literal text, emitted verbatim (`"hello"` → `hello`)
- **bare keyword** — a directive with no options (`:str` → `~A`)
- **directive vector** — `[:keyword opts]` (simple) or `[:keyword opts & body]` (compound)

The vector form follows the **Hiccup convention**: if the second element is
a map, it is the options map; everything after is the body/children.

```clojure
;; Bare keyword — no options
:str                              ;; ~A

;; Directive with options
[:str {:width 10}]                ;; ~10A

;; Compound directive: keyword, opts, then body elements
[:each {:sep ", "} :str]          ;; ~{~A~^, ~}

;; Mixed body
["Name: " :str ", Age: " :int]   ;; Name: ~A, Age: ~D
```

### Special Parameter Values

Anywhere an option accepts a numeric or character value, two special values
are also accepted:

- `:V` — take the value from the next format argument at runtime
- `:#` — use the count of remaining format arguments

```clojure
[:str {:width :V}]                ;; ~vA  — width from arg
[:str {:width :#}]                ;; ~#A  — width = remaining arg count
```

## Argument Consumption

When reading a format body, it helps to know which directives consume
arguments and which don't. The rule of thumb: **directives that name a
data type consume an argument; directives that name an action don't.**

### Consume one argument (print it)

| Keyword | What it prints |
|---------|---------------|
| `:str` | Any value, human-readable |
| `:pr` | Any value, readable form |
| `:write` | Any value, via pprint |
| `:char` | A character |
| `:int` `:bin` `:oct` `:hex` | An integer in the given base |
| `:radix` | An integer in an arbitrary base |
| `:cardinal` `:ordinal` | An integer as English words |
| `:roman` `:old-roman` | An integer as Roman numerals |
| `:float` `:exp` `:gfloat` | A floating-point number |
| `:money` | A floating-point number as currency |
| `:plural` | An integer (tests it for "s"/"ies", doesn't print it) |

### Consume no arguments (layout and control)

| Keyword | What it does |
|---------|-------------|
| `:nl` `:fresh` `:page` | Emit whitespace / page breaks |
| `:tab` | Move to a column position |
| `:tilde` | Emit literal `~` |
| `:stop` | Exit iteration or format |
| `:break` | Conditional newline (pretty printer) |
| `:indent` | Set indentation (pretty printer) |

### Modify the argument pointer (don't print)

| Keyword | What it does |
|---------|-------------|
| `:skip` | Advance past arguments without printing |
| `:back` | Move the argument pointer backward |
| `:goto` | Jump to an absolute argument position |

### Special

| Keyword | Consumption |
|---------|------------|
| `:recur` | Consumes a format string + an argument list (or shares parent args with `{:from :rest}`) |
| `:each` | Consumes a list argument (or remaining args with `{:from :rest}`) and iterates |
| `:when` | Consumes one argument (tests truthiness), body may consume more |
| `:if` | Consumes one argument (tests truthiness), selected clause may consume more |
| `:choose` | Consumes one argument (as index), selected clause may consume more |
| `:case` option | Consumes nothing extra — wraps output of the directive it's attached to |

## Directive Reference

### Output

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:str` | `~A` | Print argument (human-readable, no quotes) |
| `:pr` | `~S` | Print argument (readable form, with quotes) |
| `:write` | `~W` | Print via pprint `write` |
| `:char` | `~C` | Print a character |

**`:str` and `:pr` options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:width` | integer | 0 | Minimum column width |
| `:pad-step` | integer | 1 | Padding column increment |
| `:min-pad` | integer | 0 | Minimum padding characters |
| `:fill` | char | space | Padding character |
| `:pad` | `:left` | `:right` | Padding direction (`:left` = right-align) |

**`:write` options:**

| Option | Type | Description |
|--------|------|-------------|
| `:pretty` | boolean | Enable pretty-printing |
| `:full` | boolean | Ignore `*print-level*` and `*print-length*` |

**`:char` options:**

| Option | Type | Description |
|--------|------|-------------|
| `:format` | `:name` / `:readable` | `:name` = "Space"; `:readable` = `\space` |

### Integers

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:int` | `~D` | Decimal |
| `:bin` | `~B` | Binary |
| `:oct` | `~O` | Octal |
| `:hex` | `~X` | Hexadecimal |

All four share the same options:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:width` | integer | 0 | Minimum column width |
| `:fill` | char | space | Padding character |
| `:group` | boolean | false | Enable digit grouping (e.g., `1,000,000`) |
| `:sign` | `:always` | nil | Always show `+`/`-` sign |
| `:group-sep` | char | `,` | Separator character between groups |
| `:group-size` | integer | 3 | Digits per group |

```clojure
:int                                         ;; ~D
[:int {:width 8 :fill \0}]                   ;; ~8,'0D
[:int {:group true :sign :always}]           ;; ~:@D
[:hex {:width 4 :fill \0}]                   ;; ~4,'0X
[:bin {:width 8 :fill \0 :group true
       :group-sep \space :group-size 4}]     ;; ~8,'0,' ,4:B
```

### Radix

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:radix` | `~nR` | Print in arbitrary base `n` |
| `:cardinal` | `~R` | English cardinal ("forty-two") |
| `:ordinal` | `~:R` | English ordinal ("forty-second") |
| `:roman` | `~@R` | Roman numeral ("XLII") |
| `:old-roman` | `~:@R` | Old Roman, no subtractive ("XXXXII") |

`:radix` accepts integer options (`:base` plus `:width`, `:fill`, etc.).
The other four are bare keywords — no options.

```clojure
[:radix {:base 16}]              ;; ~16R
[:radix {:base 2 :width 8 :fill \0}]
:cardinal                        ;; ~R
:ordinal                         ;; ~:R
:roman                           ;; ~@R
:old-roman                       ;; ~:@R
```

### Pluralization

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:plural` | `~P` | Print "s" or "" based on argument |

| Option | Type | Description |
|--------|------|-------------|
| `:rewind` | boolean | Back up one argument before testing |
| `:form` | `:ies` | Use "y"/"ies" instead of ""/"s" |

```clojure
:plural                          ;; ~P   "s" or ""
[:plural {:form :ies}]           ;; ~@P  "y" or "ies"
[:plural {:rewind true}]         ;; ~:P  back up, then "s" or ""
```

### Floating Point

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:float` | `~F` | Fixed-point |
| `:exp` | `~E` | Exponential notation |
| `:gfloat` | `~G` | General (auto-selects fixed/exp) |
| `:money` | `~$` | Monetary (dollars and cents) |

**`:float` options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:width` | integer | nil | Total field width |
| `:decimals` | integer | nil | Digits after decimal point |
| `:scale` | integer | 0 | Multiply by 10^scale before printing |
| `:overflow` | char | nil | Print this char when value overflows width |
| `:fill` | char | space | Padding character |
| `:sign` | `:always` | nil | Always show sign |

**`:exp` and `:gfloat` additional options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:exp-digits` | integer | nil | Digits in exponent |
| `:exp-char` | char | `E` | Exponent marker character |

**`:money` options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:decimals` | integer | 2 | Digits after decimal |
| `:int-digits` | integer | 1 | Minimum digits before decimal |
| `:width` | integer | 0 | Minimum total width |
| `:fill` | char | space | Padding character |
| `:sign` | `:always` | nil | Always show sign |
| `:sign-first` | boolean | false | Place sign before padding |

```clojure
[:float {:width 8 :decimals 2}]             ;; ~8,2F
[:exp {:width 10 :decimals 4 :exp-digits 2}] ;; ~10,4,2E
[:money {:decimals 2 :sign :always}]         ;; ~2@$
```

### Layout

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:nl` | `~%` | Emit newline(s) |
| `:fresh` | `~&` | Emit newline only if not at column 0 |
| `:page` | `~\|` | Emit page separator (formfeed) |
| `:tab` | `~T` | Move to column position |
| `:tilde` | `~~` | Emit literal tilde character(s) |

`:nl`, `:fresh`, `:page`, and `:tilde` accept `{:count n}`.

**`:tab` options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:col` | integer | 1 | Column number (absolute) or spaces (relative) |
| `:step` | integer | 1 | Column increment for rounding |
| `:relative` | boolean | false | Relative mode (move forward, not to absolute column) |

```clojure
:nl                              ;; ~%
[:nl {:count 3}]                 ;; ~3%
[:tab {:col 20}]                 ;; ~20T
[:tab {:col 4 :relative true}]  ;; ~4@T
```

### Navigation

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:skip` | `~*` | Skip forward `n` arguments (default 1) |
| `:back` | `~:*` | Skip backward `n` arguments (default 1) |
| `:goto` | `~@*` | Jump to absolute argument position `n` (default 0) |
| `:recur` | `~?` | Recursive format — next arg is format string, next is arg list |

`:skip`, `:back`, and `:goto` accept `{:n count}`.
`:recur` accepts `{:from :rest}` to use remaining args instead of a sublist.

```clojure
:skip                            ;; ~*   skip 1
[:skip {:n 3}]                   ;; ~3*  skip 3
:back                            ;; ~:*  back up 1
[:goto {:n 5}]                   ;; ~5@* jump to arg 5
[:recur {:from :rest}]           ;; ~@?  use remaining args
```

### Iteration

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:each` | `~{...~}` | Iterate body over arguments |

The body elements follow the keyword and optional opts map (Hiccup convention).

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:sep` | string | nil | Separator between iterations (abstracts `~^sep`) |
| `:from` | keyword | nil | Argument source (see below) |
| `:max` | integer | nil | Maximum number of iterations |
| `:min` | integer | nil | Minimum iterations (1 = execute at least once) |

**`:from` values:**

| Value | cl-format | Description |
|-------|-----------|-------------|
| (nil) | `~{` | Iterate over a single list argument |
| `:rest` | `~@{` | Iterate over remaining arguments |
| `:sublists` | `~:{` | Each element of the list is a sublist of arguments |
| `:rest-sublists` | `~:@{` | Remaining args, each is a sublist |

```clojure
[:each {:sep ", "} :str]                          ;; ~{~A~^, ~}
[:each {:sep ", " :from :rest} :str]              ;; ~@{~A~^, ~}
[:each {:from :sublists} :str ": " :int :nl]      ;; ~:{~A: ~D~%~}
[:each {:max 5} :str ", "]                         ;; ~5{~A, ~}
[:each {:min 1} :str]                              ;; ~{~A~:}
```

For complex cases where `:sep` is insufficient, use `:stop` directly in
the body:

```clojure
[:each :str :stop " " :str "=\"" :str "\""]       ;; ~{~A~^ ~A="~A"~}
```

### Conditionals

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:when` | `~@[...~]` | Print body only if argument is truthy |
| `:if` | `~:[...~;...~]` | Boolean dispatch — true-clause first |
| `:choose` | `~[...~;...~]` | Numeric dispatch — select clause by index |

**`:when`** — body elements are inline:

```clojure
[:when "value: " :str]           ;; ~@[value: ~A~]
```

**`:if`** — two clauses, true first (opposite of cl-format's false-first
order). Each clause is a single element: a string, a bare keyword, or a
multi-element body vector.

```clojure
[:if "yes" "no"]                 ;; ~:[no~;yes~]
[:if :str "none"]                ;; ~:[none~;~A~]
[:if [:str " found"] "nothing"]  ;; ~:[nothing~;~A found~]
```

**`:choose`** — clauses are inline, opts map (if any) in second position.

| Option | Type | Description |
|--------|------|-------------|
| `:default` | element | Default clause when index is out of range |
| `:selector` | integer/`:V`/`:#` | Override the argument as selector |

```clojure
[:choose "zero" "one" "two"]                     ;; ~[zero~;one~;two~]
[:choose {:default "other"} "zero" "one" "two"]  ;; ~[zero~;one~;two~:;other~]
[:choose {:selector :#} "none" "one" "some"]     ;; ~#[none~;one~;some~]
```

### Case Conversion

Case conversion can be applied two ways:

**As a `:case` option** on any directive (preferred — avoids nesting):

| Value | cl-format | Effect |
|-------|-----------|--------|
| `:downcase` | `~(` | all lowercase |
| `:capitalize` | `~:(` | Capitalize Each Word |
| `:titlecase` | `~@(` | Capitalize first word only |
| `:upcase` | `~:@(` | ALL UPPERCASE |

```clojure
[:str {:case :capitalize}]                   ;; ~:(~A~)
[:str {:case :upcase}]                       ;; ~:@(~A~)
[:each {:sep ", " :case :capitalize} :str]   ;; ~:(~{~A~^, ~}~)
[:roman {:case :downcase}]                   ;; ~(~@R~)
```

The parser automatically flattens case conversion into the `:case` option
when the body contains a single element.

**As compound directives** (for multi-element bodies):

| Keyword | cl-format | Effect |
|---------|-----------|--------|
| `:downcase` | `~(` | all lowercase |
| `:capitalize` | `~:(` | Capitalize Each Word |
| `:titlecase` | `~@(` | Capitalize first word only |
| `:upcase` | `~:@(` | ALL UPPERCASE |

```clojure
[:downcase "the " :str " is " :str]         ;; ~(the ~A is ~A~)
[:capitalize "hello " :str]                  ;; ~:(hello ~A~)
```

### Justification

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:justify` | `~<...~>` | Justify/pad text within a field |
| `:logical-block` | `~<...~:>` | Pretty-printer logical block |

**`:justify`** — clauses are inline, separated by padding.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:width` | integer | 0 | Minimum total width |
| `:pad-step` | integer | 1 | Padding increment |
| `:min-pad` | integer | 0 | Minimum padding |
| `:fill` | char | space | Padding character |
| `:pad-before` | boolean | false | Pad before first clause |
| `:pad-after` | boolean | false | Pad after last clause |

```clojure
[:justify {:width 10} "foo" "bar"]           ;; ~10<foo~;bar~>
[:justify {:width 10 :pad-before true
           :pad-after true} "hello"]         ;; ~10:@<hello~>  (centered)
[:justify {:width 40} :str :int :money]      ;; ~40<~A~;~D~;~$~>
```

**`:logical-block`** — clauses define prefix, body, and suffix for the
pretty printer.

```clojure
[:logical-block :str]                        ;; ~<~A~:>
[:logical-block "(" :str ")"]                ;; ~<(~;~A~;)~:>
[:logical-block {:colon true} :str]          ;; ~:<~A~:>
```

### Control

| Keyword | cl-format | Description |
|---------|-----------|-------------|
| `:stop` | `~^` | Exit enclosing iteration (or top-level format) |
| `:break` | `~_` | Conditional newline (pretty printer) |
| `:indent` | `~I` | Set indentation (pretty printer) |

**`:stop` options:**

| Option | Type | Description |
|--------|------|-------------|
| `:outer` | boolean | Exit the enclosing `~:{` iteration, not just the current sublist |
| `:arg1` | integer | One-arg test: exit if arg1 = 0 |
| `:arg2` | integer | Two-arg test: exit if arg1 = arg2 |
| `:arg3` | integer | Three-arg test: exit if arg1 ≤ arg2 ≤ arg3 |

Usually abstracted by `:sep` on `:each`. Use directly for complex cases.

```clojure
:stop                            ;; ~^   exit if no args remain
[:stop {:outer true}]            ;; ~:^  exit enclosing iteration
[:stop {:arg1 0}]                ;; ~0^  exit if param = 0
```

**`:break` options:**

| Option | Type | Description |
|--------|------|-------------|
| `:mode` | keyword | `:fill`, `:miser`, or `:mandatory` (default: linear) |

```clojure
:break                           ;; ~_   linear
[:break {:mode :fill}]           ;; ~:_
[:break {:mode :miser}]          ;; ~@_
[:break {:mode :mandatory}]      ;; ~:@_
```

**`:indent` options:**

| Option | Type | Description |
|--------|------|-------------|
| `:n` | integer | Indentation amount (default 0) |
| `:relative-to` | `:current` | Relative to current position instead of logical block |

```clojure
[:indent {:n 4}]                             ;; ~4I
[:indent {:n 2 :relative-to :current}]       ;; ~2:I
```

## Complete Keyword Table

| Keyword | cl-format | Category |
|---------|-----------|----------|
| `:str` | `~A` | Output |
| `:pr` | `~S` | Output |
| `:write` | `~W` | Output |
| `:char` | `~C` | Output |
| `:int` | `~D` | Integer |
| `:bin` | `~B` | Integer |
| `:oct` | `~O` | Integer |
| `:hex` | `~X` | Integer |
| `:radix` | `~nR` | Integer |
| `:cardinal` | `~R` | Integer |
| `:ordinal` | `~:R` | Integer |
| `:roman` | `~@R` | Integer |
| `:old-roman` | `~:@R` | Integer |
| `:plural` | `~P` | Integer |
| `:float` | `~F` | Float |
| `:exp` | `~E` | Float |
| `:gfloat` | `~G` | Float |
| `:money` | `~$` | Float |
| `:nl` | `~%` | Layout |
| `:fresh` | `~&` | Layout |
| `:page` | `~\|` | Layout |
| `:tab` | `~T` | Layout |
| `:tilde` | `~~` | Layout |
| `:skip` | `~*` | Navigation |
| `:back` | `~:*` | Navigation |
| `:goto` | `~@*` | Navigation |
| `:recur` | `~?` | Navigation |
| `:each` | `~{...~}` | Iteration |
| `:when` | `~@[...~]` | Conditional |
| `:if` | `~:[...~]` | Conditional |
| `:choose` | `~[...~]` | Conditional |
| `:downcase` | `~(...~)` | Case |
| `:capitalize` | `~:(...~)` | Case |
| `:titlecase` | `~@(...~)` | Case |
| `:upcase` | `~:@(...~)` | Case |
| `:justify` | `~<...~>` | Justification |
| `:logical-block` | `~<...~:>` | Pretty printer |
| `:stop` | `~^` | Control |
| `:break` | `~_` | Control |
| `:indent` | `~I` | Control |

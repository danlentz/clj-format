<!-- Source: HexstreamSoft Common Lisp FORMAT Reference - CLHS Summary
     URL: https://www.hexstreamsoft.com/articles/common-lisp-format-reference/clhs-summary/
     Fetched: 2026-03-27
     Note: The main page is an index; the CLHS Summary sub-page contains the
           bulk of the reference content. Other sub-pages are per-directive
           interactive pages that did not yield additional prose. -->

# Common Lisp FORMAT Reference - CLHS Summary (HexstreamSoft)

## Overview

This page summarizes CLHS 22.3 "Formatted Output" with sections on directive definitions and additional FORMAT operation information. Users can employ `V` (dynamic argument) or `#` (remaining parameters count) as prefix parameters.

## Directive Reference Table

### 22.3.1 Basic Output

| Directive | Name | Prefix Args | : modifier | @ modifier | Consumption |
|-----------|------|-------------|------------|------------|-------------|
| `~C` | Character | -- | Spells out non-printing chars | `#\` reader syntax | 1 arg |
| `~%` | Newline | n (repeat count) | -- | -- | 0 args |
| `~&` | Fresh-Line | n (repeat count) | -- | -- | 0 args |
| `~\|` | Page | n (repeat count) | -- | -- | 0 args |
| `~~` | Tilde | n (repeat count) | -- | -- | 0 args |

### 22.3.2 Radix Control

| Directive | Name | Prefix Args | : modifier | @ modifier | Consumption |
|-----------|------|-------------|------------|------------|-------------|
| `~R` | Radix | radix, mincol, padchar, commachar, comma-interval | With radix: commas; Without: ordinal / old Roman | With radix: sign; Without: cardinal as words / Roman | 1 arg |
| `~D` | Decimal | mincol, padchar, commachar, comma-interval | Commas between groups | Force sign | 1 arg |
| `~B` | Binary | mincol, padchar, commachar, comma-interval | Commas between groups | Force sign | 1 arg |
| `~O` | Octal | mincol, padchar, commachar, comma-interval | Commas between groups | Force sign | 1 arg |
| `~X` | Hexadecimal | mincol, padchar, commachar, comma-interval | Commas between groups | Force sign | 1 arg |

### 22.3.3 Floating-Point Printers

| Directive | Name | Prefix Args | : modifier | @ modifier |
|-----------|------|-------------|------------|------------|
| `~F` | Fixed-Format | w, d, k, overflowchar, padchar | -- | Force sign |
| `~E` | Exponential | w, d, e, k, overflowchar, padchar, exponentchar | -- | Force sign |
| `~G` | General | w, d, e, k, overflowchar, padchar, exponentchar | -- | Force sign |
| `~$` | Monetary | d, n, w, padchar | Sign before padding | Force sign |

### 22.3.4 Printer Operations

| Directive | Name | Prefix Args | : modifier | @ modifier |
|-----------|------|-------------|------------|------------|
| `~A` | Aesthetic (princ) | mincol, colinc, minpad, padchar | nil prints as "()" | Pad on left |
| `~S` | Standard (prin1) | mincol, colinc, minpad, padchar | nil prints as "()" | Pad on left |
| `~W` | Write | -- | `*print-pretty*` = t | `*print-level*` = nil, `*print-length*` = nil |

### 22.3.5 Pretty Printer Operations

| Directive | Name | Notes |
|-----------|------|-------|
| `~_` | Conditional Newline | `:` = fill, `@` = miser, `:@` = mandatory, plain = linear |
| `~<...~:>` | Logical Block | Up to 3 segments (prefix, body, suffix) via `~;` |
| `~I` | Indent | n (default 0); `:` = current mode, plain = block mode |
| `~/name/` | Call Function | Calls named function with (stream arg colon-p at-sign-p &rest params) |

### 22.3.6 Layout Control

| Directive | Name | Prefix Args | Notes |
|-----------|------|-------------|-------|
| `~T` | Tabulate | colnum, colinc | `@` = relative; `:` = section-relative (pretty printer) |
| `~<...~>` | Justification | mincol, colinc, minpad, padchar | `:` = space before first; `@` = space after last |
| `~>` | End Justification | -- | Terminates `~<` |

### 22.3.7 Control Flow

| Directive | Name | Notes |
|-----------|------|-------|
| `~*` | Go-To | Skip n args; `~:*` = back up n; `~n@*` = absolute position |
| `~[...~]` | Conditional | Ordinal selection by index; `~:[false~;true~]`; `~@[if-true~]` |
| `~]` | End Conditional | Terminates `~[` |
| `~{...~}` | Iteration | `:` = sublists; `@` = remaining args; `:@` = remaining args as sublists |
| `~}` | End Iteration | Terminates `~{`; `~:}` forces at least one iteration |
| `~?` | Recursive Processing | Consumes control string + arg list; `~@?` uses remaining args |

### 22.3.8 Miscellaneous Operations

| Directive | Name | Notes |
|-----------|------|-------|
| `~(...~)` | Case Conversion | plain = downcase; `:` = capitalize words; `@` = capitalize first; `:@` = upcase |
| `~)` | End Case Conversion | Terminates `~(` |
| `~P` | Plural | "s"/nothing; `@` = "y"/"ies"; `:` = back up one arg first |

### 22.3.9 Pseudo-Operations

| Directive | Name | Notes |
|-----------|------|-------|
| `~;` | Clause Separator | Used in `~[` and `~<` constructs only |
| `~^` | Escape Upward | 0 params = test remaining args; 1 = zero test; 2 = equal test; 3 = range test |
| `~<newline>` | Ignored Newline | plain = ignore newline+whitespace; `:` = ignore newline only; `@` = keep newline, ignore whitespace |

## Additional Information (22.3.10)

### Nesting Requirements
Directive invocations must maintain proper nesting structure.

### Missing and Additional Arguments
"Consequences are undefined if no arg remains for a directive requiring an argument. However, it is permissible for one or more args to remain unprocessed by a directive; such args are ignored."

### Parameter Limits
"Consequences are undefined if a format directive is given more parameters than described as accepting."

### Modifier Combinations
"Consequences are undefined if colon or at-sign modifiers are given to a directive in a combination not specifically described as meaningful."

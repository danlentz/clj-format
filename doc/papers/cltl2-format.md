# 22.3.3. Formatted Output to Character Streams

**Common Lisp the Language, 2nd Edition**

---

## Overview

The `format` function produces formatted output to character streams or returns formatted strings. Control strings use tildes (~) to introduce directives that specify formatting operations.

## Basic Function

**[Function]**
`format` _destination_ _control-string_ &rest _arguments_

The function outputs characters from _control-string_, with tildes introducing directives. A directive consists of:
- A tilde (~)
- Optional prefix parameters (comma-separated integers)
- Optional colon (:) and at-sign (@) modifiers
- A single character indicating directive type

When _destination_ is `nil`, format returns a string. When _destination_ is `t`, output goes to `*standard-output*`. Otherwise, output is sent to the specified stream or string with fill pointer.

## Common Directives

### ~A (Ascii)
Prints any Lisp object without escape characters (like `princ`). Supports padding parameters: `~mincol_A` adds right spaces; `@` modifier adds left spaces.

### ~S (S-expression)
Similar to ~A but prints with escape characters (like `prin1`), suitable for `read` input.

### ~D (Decimal)
Prints integers in decimal radix. Form: `~mincol_,_padchar_,_commachar_D`. The `@` modifier prints the sign always; `:` adds comma separators.

### ~B, ~O, ~X (Binary, Octal, Hexadecimal)
Print in radix 2, 8, and 16 respectively, with same parameters as ~D.

### ~R (Radix)
With parameter: `~n_R` prints in radix n. Without parameter: `~R` prints English cardinal numbers; `~:R` prints ordinals; `~@R` prints Roman numerals; `~:@R` prints old Roman numerals.

### ~F (Fixed-format floating-point)
Form: `~w_,_d_,_k_,_overflowchar_,_padchar_F`
- _w_: field width
- _d_: digits after decimal point
- _k_: scale factor (default 0)

### ~E (Exponential floating-point)
Form: `~w_,_d_,_e_,_k_,_overflowchar_,_padchar_,_exponentchar_E`

Prints in exponential notation with _e_ digits for exponent (default scale factor is 1).

### ~G (General floating-point)
Automatically chooses between ~F and ~E based on magnitude.

### ~$ (Dollars)
Form: `~d_,_n_,_w_,_padchar_$`

Prints floating-point in fixed notation for currency. Default: 2 digits after decimal, 1 before.

### ~P (Plural)
Prints lowercase 's' if argument is not `eql` to 1. `~:P` backs up one argument; `~@P` prints 'y' or 'ies'.

### ~C (Character)
Prints a character. `~C` uses implementation-dependent format; `~:C` spells out control bits and names; `~@C` uses `#\` syntax.

### ~%, ~&, ~|, ~~ (Whitespace/Special)
- `~%`: outputs newline(s)
- `~&`: outputs newline unless at line start
- `~|`: outputs page separator
- `~~`: outputs tilde(s)

### ~T (Tabulate)
Form: `~colnum_,_colinc_T`

Spaces to column _colnum_. If already past it, spaces to _colnum_+_k_*_colinc_. `~@T` performs relative tabulation.

### ~\* (Argument Processing)
- `~*`: ignores next argument
- `~:*`: backs up one argument
- `~@*`: goes to nth argument (0 = first)

### ~? (Indirection)
Processes next argument as control string using following argument's list as arguments.

## Advanced Directives

### ~(_str_~) (Case Conversion)
- `~(`: lowercase
- `~:(`: capitalize words
- `~@(`: capitalize first word only
- `~:@(`: uppercase

### ~[_str0_~;_str1_~;_...~] (Conditional)
Selects clause based on argument value. `~:;` provides default clause. `~:[` selects based on nil/non-nil.

### ~{_str_~} (Iteration)
Iterates over list. `~:{` iterates over sublists; `~@{` uses remaining arguments; `~:@{` combines both.

### ~<_str_~> (Justification)
Form: `~mincol_,_colinc_,_minpad_,_padchar_<_str_~>`

Justifies text in field. `:` adds spacing before first segment; `@` adds spacing after last.

### ~^ (Up and Out)
Escapes from enclosing `~{` or `~<` construct. Can test for remaining arguments with parameters.

## Printer Control Variables

Most directives bind relevant printer control variables during processing. For example:
- `~A` binds `*print-escape*` to nil
- `~S` binds `*print-escape*` to t
- `~D`, `~B`, `~O`, `~X` bind `*print-base*` appropriately

## Examples

```lisp
(format nil "foo") => "foo"

(format nil "The answer is ~D." 5) => "The answer is 5."

(format nil "The answer is ~3D." 5) => "The answer is   5."

(format nil "~D item~:P found." 3) => "3 items found."

(format nil "~10<foo~;bar~>") => "foo    bar"

(format nil "~{~S~^ ~}" '(a b c)) => "A B C"
```

---

Source: https://www.cs.cmu.edu/Groups/AI/html/cltl/clm/node200.html

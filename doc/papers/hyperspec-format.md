<!-- Source: Common Lisp HyperSpec, Section 22.3 (Formatted Output)
     URLs: http://www.lispworks.com/documentation/HyperSpec/Body/22_c.htm
            and subsections 22_ca.htm through 22_ci.htm (plus leaf pages)
     Fetched: 2026-03-27
     Copyright 1996-2005, LispWorks Ltd. All rights reserved. -->

# CLHS: Section 22.3 - Formatted Output

## 22.3 Overview

The `format` function produces nicely formatted text and messages. It can generate and return a string or output to a specified destination.

The control-string argument to `format` is a format control, which can be either:
- A format string
- A function (such as one returned by the `formatter` macro)

When the control-string is a function, it is called with the output stream as its first argument and the data arguments as remaining arguments. The function performs necessary output and returns any unused arguments.

### Format String Structure

Control-strings consist of simple text (characters) and embedded directives.

A directive structure includes:
- A tilde (`~`)
- Optional prefix parameters (separated by commas)
- Optional colon (`:`) and at-sign (`@`) modifiers
- A single character indicating directive type

Prefix parameters use signed decimal notation or single-quote followed by a character. For example: `~5,'0d` prints an integer with leading zeros in five columns, or `~5,'*d` for leading asterisks.

### Special Parameter Notations

- **V or v**: Takes an argument from args as a directive parameter (integer or character)
- **nil**: When used with V, the parameter is omitted
- **#**: Represents the count of remaining arguments

### Format String Examples

```
"~S"        ;S directive with no parameters or modifiers
"~3,-4:@s"  ;S directive with parameters 3 and -4,
            ;with colon and at-sign flags
"~,+4S"     ;First parameter omitted (default), second is 4
```

### Output Destinations

`format` sends output to the specified destination:

- **nil**: Creates and returns a string containing the formatted output
- **String with fill pointer**: Output appended to the string's end
- **Stream**: Output sent to that stream
- **t**: Output sent to standard output

### Directive Processing

The term "arg" refers to the next item from the arguments to be processed. `format` directives do not bind printer control variables (`*print-...*`) except as specified in individual directive descriptions.

---

## 22.3.1 FORMAT Basic Output

### 22.3.1.1 Tilde C: Character

The `~C` format directive handles character output with various modifier flags controlling the display style.

**~C (Basic)**
Prints a character using `write-char` if it is simple. Non-simple characters use implementation-defined abbreviated formatting.

Examples:
- `(format nil "~C" #\A)` => `"A"`
- `(format nil "~C" #\Space)` => `" "`

**~:C (Pretty)**
Displays printing characters normally, while "spelling out" non-printing characters. For simple non-printing characters, the output shows the character's name via `char-name`. Non-simple non-printing characters use implementation-defined output.

Examples:
- `(format nil "~:C" #\A)` => `"A"`
- `(format nil "~:C" #\Space)` => `"Space"`

**~:@C (Key Description)**
Extends `~:C` output by noting "unusual shift keys on the keyboard." Designed for user prompts about expected keypresses, with output potentially varying by implementation and I/O devices.

**~@C (Lisp Reader Syntax)**
Outputs characters in `#\` syntax readable by the Lisp reader, binding `*print-escape*` to `t`.

### 22.3.1.2 Tilde Percent: Newline

`~%` outputs a `#\Newline` character, thereby terminating the current output line and beginning a new one. `~n%` outputs _n_ newlines. No argument is consumed.

### 22.3.1.3 Tilde Ampersand: Fresh-Line

Unless it can be determined that the output stream is already at the beginning of a line, `~&` outputs a newline. `~n&` calls `fresh-line` and then outputs n-1 additional newlines. `~0&` produces no output.

### 22.3.1.4 Tilde Vertical-Bar: Page

`~|` outputs a page separator character, if possible. `~n|` repeats this _n_ times.

### 22.3.1.5 Tilde Tilde: Tilde

`~~` outputs a tilde. `~n~` outputs _n_ tildes.

---

## 22.3.2 FORMAT Radix Control

### 22.3.2.1 Tilde R: Radix

The `~R` directive prints arguments in various radix formats.

**With Prefix Parameters:**
`~radix,mincol,padchar,commachar,comma-intervalR`

When `~nR` is used with a radix parameter _n_, the argument displays in that base. The `~D` directive is equivalent to `~10R`.

When the first parameter _n_ is supplied, `~R` binds: `*print-escape*` to false, `*print-radix*` to false, `*print-base*` to _n_, and `*print-readably*` to false.

**Without Prefix Parameters:**
The argument must be an integer and supports four distinct formats:

- `~R` - Cardinal English number (example: 4 becomes "four")
- `~:R` - Ordinal English number (example: 4 becomes "fourth")
- `~@R` - Roman numeral (example: 4 becomes "IV")
- `~:@R` - Old Roman numeral (example: 4 becomes "IIII")

When no parameters are supplied, `*print-base*` binds to 10.

**Examples:**
```lisp
(format nil "~,,' ,4:B" 13)           =>  "1101"
(format nil "~,,' ,4:B" 17)           =>  "1 0001"
(format nil "~19,0,' ,4:B" 3333)      =>  "0000 1101 0000 0101"
(format nil "~3,,,' ,2:R" 17)         =>  "1 22"
(format nil "~,,'|,2:D" #xFFFF)       =>  "6|55|35"
```

### 22.3.2.2 Tilde D: Decimal

The `~D` directive prints an integer argument in decimal radix without a decimal point.

**Full form:** `~mincol,padchar,commachar,comma-intervalD`

- `~mincolD` specifies minimum column width, padding left with spaces if needed.
- `~mincol,padcharD` allows substitution of a custom pad character.
- **@ modifier**: Displays the sign unconditionally (positive or negative).
- **: modifier**: Inserts commas between digit groups. The _comma-interval_ parameter (defaulting to 3) controls grouping size, and _commachar_ can replace the standard comma character.

If _arg_ is not an integer, it is printed in `~A` format and decimal base.

Binds `*print-escape*` to false, `*print-radix*` to false, `*print-base*` to 10, `*print-readably*` to false.

### 22.3.2.3 Tilde B: Binary

`~B` is like `~D` but prints in binary radix (base 2).

Full form: `~mincol,padchar,commachar,comma-intervalB`

Binds `*print-escape*` to false, `*print-radix*` to false, `*print-base*` to 2, `*print-readably*` to false.

### 22.3.2.4 Tilde O: Octal

`~O` is like `~D` but prints in octal radix (base 8).

Full form: `~mincol,padchar,commachar,comma-intervalO`

Binds `*print-escape*` to false, `*print-radix*` to false, `*print-base*` to 8, `*print-readably*` to false.

### 22.3.2.5 Tilde X: Hexadecimal

`~X` is like `~D` but prints in hexadecimal radix (base 16).

Full form: `~mincol,padchar,commachar,comma-intervalX`

Binds `*print-escape*` to false, `*print-radix*` to false, `*print-base*` to 16, `*print-readably*` to false.

---

## 22.3.3 FORMAT Floating-Point Printers

### 22.3.3.1 Tilde F: Fixed-Format Floating-Point

Full syntax: `~w,d,k,overflowchar,padcharF`

**Parameters:**
- **w**: Field width (exact number of characters output)
- **d**: Digits after the decimal point
- **k**: Scale factor (defaults to zero); value printed as arg x 10^k
- **overflowchar**: Character to repeat if value cannot fit in width
- **padchar**: Leading padding character (defaults to space)

**Output Formatting:**
The directive outputs exactly `w` characters. Leading padding characters fill the field left side. A minus sign appears for negative values; a plus sign for non-negative values only with the `@` modifier. The output includes one decimal point with `d` fractional digits, rounded appropriately. A single leading zero appears before the decimal point only if the magnitude is less than one and `w` != `d`+1.

**Overflow Handling:**
When the scaled value cannot fit in `w` characters: if `overflowchar` is provided, `w` copies of that character print instead; otherwise, the value prints using additional characters as needed.

**Omitted Parameters:**
- **w omitted**: Variable-width field; no leading padding, exactly `d` digits follow the decimal.
- **d omitted**: No fractional digit constraint; as many digits as possible print within width `w`, with no trailing zeros.
- **Both omitted**: Free-format output resembling `prin1` behavior for numbers with magnitude zero or between 10^-3 and 10^7.

If `w` is omitted and printing would require over 100 digits, implementations may use exponential notation (as `~E`).

**Non-Float Arguments:**
- Rational numbers: Coerced to single float before printing.
- Complex or non-numeric objects: Printed using `~wD`.

Binds `*print-escape*` and `*print-readably*` to false.

### 22.3.3.2 Tilde E: Exponential Floating-Point

Full syntax: `~w,d,e,k,overflowchar,padchar,exponentcharE`

**Parameters:**
- **w**: Field width (total characters output)
- **d**: Digits after the decimal point
- **e**: Digits for the exponent representation
- **k**: Scale factor (defaults to one)
- **overflowchar**: Character to repeat if output cannot fit
- **padchar**: Left-padding character (defaults to space)
- **exponentchar**: Character preceding the exponent

**Formatting Rules:**
The output always contains exactly w characters, padded on the left as needed. For negative arguments, a minus sign prints; for non-negative, a plus sign prints only with the @ modifier.

**Scale Factor Behavior:**
- When k equals zero, d digits appear after the decimal point with a leading zero digit if space permits.
- For positive k (must be less than d+2), k significant digits print before the decimal, with d-k+1 digits after.
- For negative k (must exceed -d), a leading zero appears with -k zeros after the decimal point, followed by d+k significant digits.

**Exponent Representation:**
The exponent character prints first (or the default marker from `prin1`), followed by a sign and e digits for the power of ten.

**Overflow Handling:**
If it is impossible to print the value in the required format in a field of width w, either overflow repetition occurs or the value expands beyond w characters as needed.

**Optional Parameters:**
When w is omitted, field width adjusts to eliminate leading padding. Omitting d removes digit constraints. Omitting e uses minimum necessary digits. Omitting all three produces free-format exponential-notation output.

Rational numbers coerce to single float. Complex numbers or non-numeric objects print using `~wD`.

Binds `*print-escape*` and `*print-readably*` to false.

### 22.3.3.3 Tilde G: General Floating-Point

Full syntax: `~w,d,e,k,overflowchar,padchar,exponentcharG`

**Parameters:**
- **w**: Total field width
- **d**: Number of significant digits
- **e**: Exponent width
- **k**: Scale factor
- **overflowchar**: Character printed if value exceeds field width
- **padchar**: Padding character
- **exponentchar**: Character used for exponent

**Format Selection Logic:**
The directive calculates an integer _n_ where 10^(n-1) <= |arg| < 10^n.

Let _ee_ = _e_ + 2 (or 4 if _e_ is omitted), and _ww_ = _w_ - _ee_ (or nil if _w_ is omitted).

When _d_ is omitted, calculate _q_ (digits needed without information loss or leading/trailing zeros), then _d_ = (max _q_ (min _n_ 7)).

Let _dd_ = _d_ - _n_.

**Conditional Output:**
- If 0 <= _dd_ <= _d_: Print using `~ww,dd,,overflowchar,padcharF~ee@T` (note: scale factor k is not passed to ~F)
- Otherwise: Print using `~w,d,e,k,overflowchar,padchar,exponentcharE`

The `@` modifier applies to both cases only if provided to `~G`.

Binds `*print-escape*` and `*print-readably*` to false.

### 22.3.3.4 Tilde Dollarsign: Monetary Floating-Point

Full syntax: `~d,n,w,padchar$`

**Parameters:**
- **d**: Digits after decimal point (default: 2)
- **n**: Minimum digits before decimal point (default: 1)
- **w**: Minimum total field width (default: 0)
- **padchar**: Padding character (default: space)

**Output Processing:**
1. A minus sign prints for negative arguments; a plus sign prints for non-negative arguments only with the `@` modifier.
2. The `:` modifier places the sign before padding; otherwise it appears after.
3. If `w` is specified and total output characters are fewer than `w`, `padchar` copies fill the gap.
4. The directive outputs `n` digits for the integer portion (with leading zeros if needed), a decimal point, then `d` rounded fraction digits.

If the magnitude of _arg_ is so large that more than _m_ digits would have to be printed, where _m_ is the larger of _w_ and 100, then an implementation is free to print the number using exponential notation instead.

Rational numbers coerce to single float. Complex numbers or non-numeric objects print using `~wD`.

Binds `*print-escape*` and `*print-readably*` to false.

---

## 22.3.4 FORMAT Printer Operations

### 22.3.4.1 Tilde A: Aesthetic

The `~A` directive prints an argument without escape characters, functioning similarly to `princ`.

**Basic Behavior:**
- Objects: Printed without escape characters
- Strings: Characters output verbatim
- nil: Printed as "nil" by default

The colon modifier (`~:A`) changes nil display to "()" for top-level arguments, though nested nil values within composite structures remain as "nil".

**Padding Control:**
`~mincol,colinc,minpad,padcharA`

- _mincol_: Minimum column width (default: 0)
- _colinc_: Increment for additional padding (default: 1)
- _minpad_: Minimum padding characters (default: 0)
- _padchar_: Character used for padding (default: space)

Padding is applied on the right, or left with the `@` modifier.

Binds `*print-escape*` to false, `*print-readably*` to false.

### 22.3.4.2 Tilde S: Standard

`~S` is like `~A` but prints _arg_ with escape characters (as by `prin1` rather than `princ`). The output is therefore suitable for input to `read`.

Accepts all arguments and modifiers that `~A` supports.

Binds `*print-escape*` to `t`.

### 22.3.4.3 Tilde W: Write

The `~W` directive prints any object while respecting all printer control variables, similar to the `write` function. It properly integrates with depth abbreviation by maintaining the depth counter rather than resetting it.

`~W` does not accept parameters.

**Modifiers:**
- **Colon modifier (`:`)**: Binds `*print-pretty*` to true.
- **At-sign modifier (`@`)**: Binds `*print-length*` and `*print-level*` to nil.

`~W` provides automatic support for the detection of circularity and sharing. When `*print-circle*` is not nil and the directive encounters a circular or shared reference, it substitutes an appropriate `#n#` marker.

---

## 22.3.5 FORMAT Pretty Printer Operations

### 22.3.5.1 Tilde Underscore: Conditional Newline

- `~_` (no modifiers): Equivalent to `(pprint-newline :linear)`
- `~@_` (@ modifier): Equivalent to `(pprint-newline :miser)`
- `~:_` (: modifier): Equivalent to `(pprint-newline :fill)`
- `~:@_` (both modifiers): Equivalent to `(pprint-newline :mandatory)`

### 22.3.5.2 Tilde Less-Than-Sign: Logical Block

Syntax: `~<...~:>`

This directive functions as a call to `pprint-logical-block`. The argument is processed similarly to the list parameter of `pprint-logical-block`, automatically handling non-list arguments and detecting circularity, sharing, and depth abbreviation.

**Control String Segmentation:**
The nested control string can be divided into up to three segments using `~;` directives:

`~<prefix~;body~;suffix~:>`

- Using `~@;` specifies a per-line prefix instead of a simple prefix
- Prefix and suffix must be constant strings with no format directives
- Error occurs if more than three segments exist
- Two segments: suffix defaults to null string
- One segment: both prefix and suffix default to null string
- With colon modifier (`~:<...~:>`): prefix and suffix default to `"("` and `")"` respectively

**Body Segment:**
The body accepts any format string applied to list elements. Elements are extracted using `pprint-pop`, supporting malformed lists and detecting circularity, sharing, and length abbreviation. Within the body, `~^` behaves like `pprint-exit-if-list-exhausted`.

**Fill-style formatting:** Using `~:@>` terminator automatically inserts fill-style conditional newlines after blank groups in the body.

**At-sign modifier:** `~@<...~:>` passes the entire remaining argument list to the directive, consuming all arguments regardless of actual use.

An error occurs if `~<...~>` nests directives like `~W`, `~_`, `~<...~:>`, `~I`, or `~:T`. The `~<...~:;...~>` form cannot coexist with these directives in the same format string.

### 22.3.5.3 Tilde I: Indent

- `~nI`: Equivalent to `(pprint-indent :block n)` (n defaults to zero)
- `~n:I`: Equivalent to `(pprint-indent :current n)` (n defaults to zero)

### 22.3.5.4 Tilde Slash: Call Function

Syntax: `~/name/`

User-defined functions can be invoked within format strings. The _name_ follows these rules:
- All characters are treated as uppercase
- If _name_ contains `:` or `::`, the portion before identifies the package, the portion after identifies the symbol
- Without `:` or `::`, the entire _name_ is looked up in the `COMMON-LISP-USER` package
- Forward slashes (`/`) cannot appear in _name_

The directive calls the resolved function with these arguments:
1. Output stream
2. Format argument corresponding to the directive
3. Generalized boolean (true if colon modifier used)
4. Generalized boolean (true if at-sign modifier used)
5. Any additional parameters specified with the directive

The function should print the argument appropriately. Any values returned by the function are ignored.

Three standard functions are designed for use with this directive: `pprint-linear`, `pprint-fill`, and `pprint-tabular`.

---

## 22.3.6 FORMAT Layout Control

### 22.3.6.1 Tilde T: Tabulate

**Basic Tabulation (~T):**
`~colnum,colincT` spaces the cursor to a specified column.
- Outputs spaces to reach column `colnum`
- If already at or past `colnum`, advances to `colnum + k*colinc` (smallest positive integer k)
- If `colinc` is zero and cursor is already at/beyond `colnum`, outputs no spaces
- Both parameters default to 1

**Column Position Detection:**
When the current column position cannot be directly determined, `format` attempts deduction by noting directives that reset position (like `~%`, `~&`), counting characters emitted since the reset, assuming the destination started at column zero, or as a fallback, outputting two spaces.

**Relative Tabulation (~@T):**
`~colrel,colinc@T` performs relative tabulation:
- Outputs `colrel` spaces initially
- Adds minimum additional spaces to reach a column that is a multiple of `colinc`
- Example: `~3,8@T` outputs three spaces then moves to a standard multiple-of-eight tab stop
- If column position cannot be determined, `colinc` is ignored and exactly `colrel` spaces output

**Colon Modifier (~:T):**
Tabbing computes relative to where the containing section begins rather than column zero. Parameters represent units of ems (both defaulting to 1):
- `~n,m:T` equals `(pprint-tab :section n m)`
- `~n,m:@T` equals `(pprint-tab :section-relative n m)`

### 22.3.6.2 Tilde Less-Than-Sign: Justification

Syntax: `~mincol,colinc,minpad,padchar<str~>`

This directive justifies text produced by processing `str` within a field at least `mincol` columns wide. The `str` parameter may be divided into segments using `~;` separators, with spacing distributed evenly between text segments.

- Without modifiers, the leftmost text segment aligns left while the rightmost aligns right.
- When only one text element exists, it right-justifies as a special case.
- **`:` modifier**: Introduces spacing before the first text segment.
- **`@` modifier**: Adds spacing after the last segment.

**Parameters:**
- `mincol`: Minimum field width (default: 0)
- `colinc`: Column increment (default: 1)
- `minpad`: Minimum padding characters between segments (default: 0)
- `padchar`: Padding character used (default: space)

When total required width exceeds `mincol`, the actual width becomes `mincol + k*colinc` for the smallest non-negative integer k.

**~:; Clause Termination:**
When the first clause ends with `~:;` instead of `~;`, it receives special handling: it is processed but excluded from spacing/padding calculations. The padded result outputs if it fits the current line; otherwise, the first clause text outputs first. A prefix parameter `n` with `~:;` requires `n` spare character positions. A second prefix parameter specifies line width, overriding the stream's natural width.

The `~^` directive can prematurely terminate clause processing; only completely processed clauses participate in justification.

If line width cannot be determined, format uses 72 as the default line length.

### 22.3.6.3 Tilde Greater-Than-Sign: End of Justification

`~>` terminates a `~<`. The consequences of using it elsewhere are undefined.

---

## 22.3.7 FORMAT Control-Flow Operations

### 22.3.7.1 Tilde Asterisk: Go-To

**Basic Ignore:** `~*` skips the next argument. `~n*` ignores the following _n_ arguments.

**Backup Navigation:** `~:*` backs up one position in the argument list. `~n:*` backs up by _n_ arguments.

**Absolute Positioning:** `~n@*` jumps to the nth argument (0-indexed). `~@*` returns to the first argument.

Within `~{` iteration constructs, all navigation operations are relative to the arguments being processed within that iteration context.

### 22.3.7.2 Tilde Left-Bracket: Conditional Expression

Syntax: `~[str0~;str1~;...~;strn~]`

Selects and processes one control string from a set of clauses separated by `~;` and terminated by `~]`.

The _arg_th clause is selected (first clause is numbered 0). If a prefix parameter is provided as `~n[`, that parameter determines clause selection instead of consuming an argument. When the argument is out of range, no clause executes and no error occurs.

**Default Case:** Using `~:;` before the final clause creates a default case.

**`~:[alternative~;consequent~]`** selects based on argument truthiness: `alternative` if false, `consequent` otherwise.

**`~@[consequent~]`** tests the argument. If true, the argument remains unconsumed and `consequent` processes. If false, the argument is consumed and nothing executes.

**Examples:**
```lisp
(setq *print-level* nil *print-length* 5)
(format nil
        "~@[ print level = ~D~]~@[ print length = ~D~]"
        *print-level* *print-length*)
=>   " print length = 5"
```

```lisp
(setq foo "Items:~#[ none~; ~S~; ~S and ~S~
           ~:;~@{~#[~; and~] ~S~^ ,~}~].")
(format nil foo)            =>   "Items: none."
(format nil foo 'foo)       =>   "Items: FOO."
(format nil foo 'foo 'bar)  =>   "Items: FOO and BAR."
```

### 22.3.7.3 Tilde Right-Bracket: End of Conditional Expression

`~]` terminates a `~[`. The consequences of using it elsewhere are undefined.

### 22.3.7.4 Tilde Left-Brace: Iteration

**Basic Form: `~{str~}`**
The argument should be a list, which is used as a set of arguments as if for a recursive call to format. The control string `str` repeats over list elements.

```lisp
(format nil "The winners are:~{ ~S~}."
        '(fred harry jill))
=>  "The winners are: FRED HARRY JILL."
```

**Sublist Form: `~:{str~}`**
The argument should be a list of sublists. At each repetition step, one sublist is used as the set of arguments for processing str.

```lisp
(format nil "Pairs:~:{ <~S,~S>~} ."
        '((a 1) (b 2) (c 3)))
=>  "Pairs: <A,1> <B,2> <C,3>."
```

**Arguments Form: `~@{str~}`**
All the remaining arguments are used as the list of arguments for the iteration.

```lisp
(format nil "Pairs:~@{ <~S,~S>~} ." 'a 1 'b 2 'c 3)
=>  "Pairs: <A,1> <B,2> <C,3>."
```

**Combined Form: `~:@{str~}`**
All the remaining arguments are used, and each one must be a list.

```lisp
(format nil "Pairs:~:@{ <~S,~S>~} ."
        '(a 1) '(b 2) '(c 3))
=>  "Pairs: <A,1> <B,2> <C,3>."
```

**Termination:**
- Prefix parameter `n` limits repetitions to at most `n` times
- The `~^` directive terminates iteration prematurely
- Using `~:}` instead of `~}` forces at least one processing iteration
- Empty `str` means an argument provides the control string

### 22.3.7.5 Tilde Right-Brace: End of Iteration

`~}` terminates a `~{`. The consequences of using it elsewhere are undefined.

### 22.3.7.6 Tilde Question-Mark: Recursive Processing

**Basic Syntax (~?):**
Consumes two arguments: a format control string and a list of arguments for that control string.

```lisp
(format nil "~? ~D" "<~A ~D>" '("Foo" 5) 7)
=>  "<Foo 5> 7"
```

When excess arguments exist in the list, they are ignored.

**With @ Modifier (~@?):**
Consumes only one direct argument -- the control string. Arguments for the nested control come from the outer format call's remaining arguments.

```lisp
(format nil "~@? ~D" "<~A ~D>" "Foo" 5 7)
=>  "<Foo 5> 7"

(format nil "~@? ~D" "<~A ~D>" "Foo" 5 14 7)
=>  "<Foo 5> 14"
```

---

## 22.3.8 FORMAT Miscellaneous Operations

### 22.3.8.1 Tilde Left-Paren: Case Conversion

Syntax: `~(str~)`

The contained control string is processed and its output undergoes case conversion.

| Directive | Behavior |
|-----------|----------|
| `~(` | Converts all uppercase characters to lowercase |
| `~:(` | Capitalizes all words, as if by `string-capitalize` |
| `~@(` | Capitalizes the first word and converts remaining text to lowercase |
| `~:@(` | Converts all lowercase characters to uppercase |

**Examples:**
```lisp
(format nil "~@R ~(~@R~)" 14 14)
=>  "XIV xiv"

(defun f (n) (format nil "~@(~R~) error~:P detected." n))
(f 0)  =>  "Zero errors detected."
(f 1)  =>  "One error detected."
(f 23) =>  "Twenty-three errors detected."
```

When case conversions appear nested, the outer conversion dominates.

### 22.3.8.2 Tilde Right-Paren: End of Case Conversion

`~)` terminates a `~(`. The consequences of using it elsewhere are undefined.

### 22.3.8.3 Tilde P: Plural

- `~P`: If the argument is not `eql` to the integer 1, a lowercase "s" is printed. If 1, nothing is printed. Floating-point 1.0 prints the "s".
- `~:P`: Backs up one argument using `~:*`, then applies the same logic.
- `~@P`: Prints "y" if the argument equals 1, or "ies" if it does not.
- `~:@P`: Combines both modifiers -- backs up first, then prints "y" or "ies".

**Examples:**
```lisp
(format nil "~D tr~:@P/~D win~:P" 7 1)  =>  "7 tries/1 win"
(format nil "~D tr~:@P/~D win~:P" 1 0)  =>  "1 try/0 wins"
(format nil "~D tr~:@P/~D win~:P" 1 3)  =>  "1 try/3 wins"
```

---

## 22.3.9 FORMAT Miscellaneous Pseudo-Operations

### 22.3.9.1 Tilde Semicolon: Clause Separator

`~;` separates clauses in `~[` and `~<` constructs. The consequences of using it elsewhere are undefined.

### 22.3.9.2 Tilde Circumflex: Escape Upward

The `~^` directive terminates enclosing constructs based on argument availability or parameter conditions.

**Basic Behavior:** If there are no more arguments remaining to be processed, then the immediately enclosing `~{` or `~<` construct is terminated. If no such construct exists, the entire formatting operation ends.

**Parameter-Based Termination:**
- No parameters: Equivalent to `~#^` (tests for remaining arguments)
- One parameter: Terminates if that parameter equals zero
- Two parameters: Terminates if parameters are equal
- Three parameters: Terminates if first <= second <= third

**Usage in Iteration Constructs:**
Within `~:{` constructs, `~^` terminates only the current iteration step. To end the entire iteration, use `~:^`, which works exclusively with `~:{` or `~:@{` directives.

**Examples:**
```lisp
(format nil "Done.~^ ~D warning~:P.~^ ~D error~:P.")
=>  "Done."

(format nil "Done.~^ ~D warning~:P.~^ ~D error~:P." 3)
=>  "Done. 3 warnings."

(format nil "~15<~S~;~^~S~;~^~S~>" 'foo 'bar 'baz)
=>  "FOO   BAR   BAZ"
```

The directive can appear within `~?`, `~[`, and `~(` constructs, where it terminates processing and searches outward for associated `~{` or `~<` constructs.

### 22.3.9.3 Tilde Newline: Ignored Newline

The tilde character followed immediately by a newline handles whitespace in format control strings:

- **Default (`~<newline>`)**: Ignores the newline and any following non-newline whitespace characters.
- **With colon (`~:<newline>`)**: The newline is ignored, but trailing whitespace remains in the output.
- **With at-sign (`~@<newline>`)**: The newline appears in the output, but any following whitespace is removed.

**Example:**
```lisp
(defun type-clash-error (fn nargs argnum right-type wrong-type)
  (format *error-output*
          "~&~S requires its ~:[~:R~;~*~]~
          argument to be of type ~S,~%but it was called ~
          with an argument of type ~S.~%"
          fn (eql nargs 1) argnum right-type wrong-type))

(type-clash-error 'aref nil 2 'integer 'vector)
;; prints:
;; AREF requires its second argument to be of type INTEGER,
;; but it was called with an argument of type VECTOR.
```

Newlines appear in the output only as specified by the `~&` and `~%` directives; the actual newline characters in the control string are suppressed because each is preceded by a tilde.

# A Few FORMAT Recipes

Copyright (c) 2003-2005, Peter Seibel

## 18. A Few FORMAT Recipes

Common Lisp's `FORMAT` function is--alongside the extended `LOOP` macro--one of two features inspiring strong emotional responses. Enthusiasts appreciate its power and brevity, while critics object to its opacity and potential for misuse.

### The Appeal and Challenge

"Complex FORMAT control strings sometimes bear a suspicious resemblance to line noise," yet many Lispers value generating human-readable output without excessive boilerplate code. Compare verbose iteration:

```lisp
(loop for cons on list
    do (format t "~a" (car cons))
    when (cdr cons) do (format t ", "))
```

With concise formatting: `(format t "~{~a~^, ~}" list)`

The second immediately conveys its purpose--printing list contents--without requiring detailed parsing.

### Scope and Organization

This chapter covers three primary formatting types: tabular data output, pretty-printing s-expressions, and generating human-readable messages with interpolated values. The author focuses on message generation with variable interpolation, noting that "you can get quite far with just a few FORMAT idioms."

### FORMAT Function Basics

`FORMAT` accepts two required arguments: an output destination and a control string containing literals and directives. The destination can be `T` (standard output), `NIL` (returns a string), a stream, or a string with a fill pointer. Format arguments supply values for directive interpolation.

### Directive Syntax

All directives begin with a tilde (`~`) and end with a single identifying character. Directives may include:

- **Prefix parameters**: Numeric or character values controlling formatting details (e.g., decimal places)
- **Modifiers**: Colon (`:`) and at-sign (`@`) markers altering directive behavior
- **Format arguments**: Values consumed and formatted per directive specifications

Some directives (like `~%` for newlines) consume no arguments; others may consume multiple values.

### Basic Directives

**General-purpose directives:**
- `~A`: Aesthetic (human-readable) output for any type
- `~S`: Output readable by `READ`, with quotes and escape characters
- `~%`: Emits newline
- `~&`: Emits fresh line (newline only if not already at line start)
- `~~`: Emits literal tilde

Example outputs:
```lisp
(format nil "~a" 10)           ==> "10"
(format nil "~a" "foo")        ==> "foo"
(format nil "~a" (list 1 2 3)) ==> "(1 2 3)"
```

### Character and Integer Directives

**Character formatting:**
- `~C`: Outputs characters; with `:` modifier shows names like "Space"
- `~@C`: Emits Lisp literal syntax (`#\a`)

**Integer directives:**
- `~D`: Base-10 output with optional formatting
- `~X`, `~O`, `~B`: Hexadecimal, octal, binary output
- `~R`: General radix directive (base 2-36)

Modifiers and parameters control:
- Minimum width padding
- Comma separation (`:` modifier)
- Sign display (`@` modifier)
- Grouping preferences

Examples:
```lisp
(format nil "~d" 1000000)        ==> "1000000"
(format nil "~:d" 1000000)       ==> "1,000,000"
(format nil "~12,'0d" 1000000)   ==> "000001000000"
(format nil "~4,'0d-~2,'0d-~2,'0d" 2005 6 10) ==> "2005-06-10"
```

### Floating-Point Directives

Four directives handle floating-point values:

- `~F`: Decimal format, may use scientific notation for extreme values
- `~E`: Forces scientific notation
- `~G`: General format combining aspects of `~F` and `~E`
- `~$`: Monetary format (two decimal places by default)

The second prefix parameter controls decimal places:
```lisp
(format nil "~f" pi)   ==> "3.141592653589793d0"
(format nil "~,4f" pi) ==> "3.1416"
(format nil "~$" pi)   ==> "3.14"
```

### English-Language Directives

**Number-to-text conversion:**
- `~R`: Converts numbers to English words (cardinal by default)
- `~:R`: Ordinal form
- `~@R`: Roman numerals
- `~:@R`: Old-style Roman numerals (IIII, VIIII)

Examples:
```lisp
(format nil "~r" 1234)  ==> "one thousand two hundred thirty-four"
(format nil "~:r" 1234) ==> "one thousand two hundred thirty-fourth"
(format nil "~@r" 1234) ==> "MCCXXXIV"
```

**Pluralization:**
- `~P`: Emits "s" unless argument is 1
- `~:P`: Reprocesses previous argument
- `~:@P`: Emits "y" or "ies"

Examples:
```lisp
(format nil "file~p" 1)  ==> "file"
(format nil "file~p" 10) ==> "files"
(format nil "~r file~:p" 1)  ==> "one file"
(format nil "~r file~:p" 10) ==> "ten files"
```

**Case conversion:**
- `~(...)~)`: Lowercase
- `~@(...)~)`: Capitalize first word
- `~:(...)~)`: Capitalize all words
- `~:@(...)~)`: Uppercase

### Conditional Formatting

The `~[...~]` directive selects output clauses:

Basic syntax with numeric selection:
```lisp
(format nil "~[cero~;uno~;dos~]" 0) ==> "cero"
(format nil "~[cero~;uno~;dos~]" 1) ==> "uno"
```

Default clause with `~:;`:
```lisp
(format nil "~[cero~;uno~;dos~:;mucho~]" 3) ==> "mucho"
```

The `#` prefix parameter selects by remaining argument count--useful for variable-length formatting:
```lisp
(defparameter *list-etc*
  "~#[NONE~;~a~;~a and ~a~:;~a, ~a~]~#[~; and ~a~:;, ~a, etc~].")

(format nil *list-etc*)                ==> "NONE."
(format nil *list-etc* 'a)             ==> "A."
(format nil *list-etc* 'a 'b)          ==> "A and B."
(format nil *list-etc* 'a 'b 'c)       ==> "A, B and C."
```

Colon modifier (`~:[true~;false~]`): Boolean selection between two clauses.

At-sign modifier (`~@[~a~]`): Processes clause if argument is non-NIL, backing up to reuse the argument.

### Iteration

The `~{...~}` directive iterates over list elements:

```lisp
(format nil "~{~a, ~}" (list 1 2 3)) ==> "1, 2, 3, "
```

The `~^` directive terminates iteration when no elements remain:
```lisp
(format nil "~{~a~^, ~}" (list 1 2 3)) ==> "1, 2, 3"
```

At-sign modifier (`~@{...~}`): Treats remaining format arguments as a list.

Within `~{` blocks, `#` refers to remaining list items rather than format arguments. Complex example for English list formatting:

```lisp
(defparameter *english-list*
  "~{~#[~;~a~;~a and ~a~:;~@{~a~#[~;, and ~:;, ~]~}~]~}")

(format nil *english-list* '())        ==> ""
(format nil *english-list* '(1))       ==> "1"
(format nil *english-list* '(1 2))     ==> "1 and 2"
(format nil *english-list* '(1 2 3))   ==> "1, 2, and 3"
```

Colon modifier on closing `~}` forces at least one iteration, enabling empty list handling:
```lisp
(defparameter *english-list*
  "~{~#[<empty>~;~a~;~a and ~a~:;~@{~a~#[~;, and ~:;, ~]~}~]:}")

(format nil *english-list* '()) ==> "<empty>"
```

### Argument Navigation

The `~*` directive manipulates argument position:

- No modifiers: Skip next argument
- `:` modifier: Back up one argument
- Prefix parameter: Number of positions to move
- `@` modifier: Jump to absolute index

Examples:
```lisp
(format nil "~r ~:*(~d)" 1) ==> "one (1)"
(format nil "I saw ~r el~:*~[ves~;f~:;ves~]." 0) ==> "I saw zero elves."
(format nil "I saw ~r el~:*~[ves~;f~:;ves~]." 1) ==> "I saw one elf."
```

Within `~{` blocks, `~*` manipulates list iteration position.

### Additional Directives

The chapter mentions--without detailed coverage--two advanced directives:

- `~?`: Processes control string snippets from format arguments
- `~/`: Calls arbitrary functions for argument handling

The chapter also notes extensive directives exist for tabular and pretty-printed output.

### Conclusion

"The directives discussed in this chapter should be plenty for the time being." The next chapter addresses Common Lisp's condition system--the language's exception and error handling analog.

---

Source: https://gigamonkeys.com/book/a-few-format-recipes.html

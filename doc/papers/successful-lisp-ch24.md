<!-- Source: Successful Lisp - Chapter 24
     URL: https://dept-info.labri.fr/~strandh/Teaching/MTP/Common/David-Lamkins/chapter24.html
     Fetched: 2026-03-27 -->

# Successful Lisp - Chapter 24: FORMAT Speaks a Different Language

## Overview

This chapter covers Lisp's `FORMAT` function, a text formatting system inspired by FORTRAN that implements its own programming language for output formatting.

## Key Capabilities

The `FORMAT` function can:
- Print numbers as words or Roman numerals
- Generate columnar output
- Create conditional text based on formatted variables
- Handle various numeric representations and formatting styles

## Basic Syntax

`FORMAT` requires three components: a destination argument, a format control string, and optional arguments for the control string.

**Destination options:**
- `T`: sends output to `*STANDARD-OUTPUT*`
- `NIL`: returns formatted output as a string
- String with fill pointer: appends output to the string
- Output stream: directs to specific stream

## Formatting Directives

| Directive | Purpose |
|-----------|---------|
| `~%` | newline |
| `~&` | fresh line (newline if not already at line start) |
| `~\|` | page break |
| `~T` | tab stop |
| `~C` | character |
| `~D` | decimal integer |
| `~B`, `~O`, `~X` | binary, octal, hexadecimal |
| `~R` | spell integer as words |
| `~P` | plural handling |
| `~F`, `~E`, `~G`, `~$` | floating-point formats |
| `~A` | legible output (no escapes) |
| `~S` | readable output (with escapes) |
| `~~` | literal tilde |

## Special Features

### Case conversion

- `~(text~)`: lowercase
- `~:@(text~)`: uppercase
- `~:(text~)`: capitalize each word
- `~@(text~)`: capitalize first word only

### Number formatting modifiers

- `~wD`: right-justify in field width _w_
- `~w,'cD`: pad with character _c_
- `~@D`: include plus sign for positive numbers
- `~:D`: insert commas between digit groups

### Roman numerals

`~@R` converts integers to Roman numeral representation.

### Plurals

`~P` appends "s"; `~@P` handles "y"/"ies"; `~:P` reuses previous argument.

## List Iteration

Use `~{format-control~}` to iterate over list elements. Include `~^` to terminate early if arguments exhaust.

## Conditionals

Three forms exist:

- **Ordinal:** `~[format-0~;format-1~;...~]` selects clause by integer index
- **Binary:** `~:[false~;true~]` tests for `NIL`
- **Conditional:** `~@[format~]` tests argument; processes only if non-`NIL`

## Dynamic Parameters

Use `V` to specify parameters from the argument list at runtime, allowing field widths and other values to vary during execution.

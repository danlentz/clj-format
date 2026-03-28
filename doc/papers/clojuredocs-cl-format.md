# cl-format - clojure.pprint | ClojureDocs

## Overview

**Namespace:** clojure.pprint

**Available since:** 1.2

**Signature:** `(cl-format writer format-in & args)`

## Description

An implementation of a Common Lisp compatible format function. This utility formats arguments to an output stream or string based on a format control string. It supports sophisticated formatting of structured data.

**Parameters:**
- `writer`: A java.io.Writer instance, `true` to output to `*out*`, or `nil` to output to a string
- `format-in`: The format control string containing embedded format directives
- `args`: Data to be formatted

**Return value:** If writer is `nil`, returns the formatted result string. Otherwise, returns `nil`.

## Basic Example

```clojure
(let [results [46 38 22]]
  (cl-format true "There ~[are~;is~:;are~]~:* ~d result~:p: ~{~d~^, ~}~%"
             (count results) results))
```

Output to `*out*`:
```
There are 3 results: 46, 38, 22
```

## Format Directives Examples

**Integer formatting with grouping:**
```clojure
user=> (cl-format nil "~:d" 1234567)
"1,234,567"
```

**Width and padding:**
```clojure
user=> (cl-format nil "~5d" 3)
" 3"

user=> (cl-format nil "Pad with leading zeros ~5,'0d" 3)
"Pad with leading zeros 00003"

user=> (cl-format nil "Pad with leading asterisks ~5,'*d" 3)
"Pad with leading asterisks ****3"
```

**Sign display:**
```clojure
user=> (cl-format nil "Always print the sign ~5@d" 3)
"Always print the sign +3"
```

**Multiple bases:**
```clojure
user=> (cl-format nil "decimal ~d binary ~b octal ~o hex ~x" 63 63 63 63)
"decimal 63 binary 111111 octal 77 hex 3f"
```

**Base conversion with formatting:**
```clojure
user=> (cl-format nil "base 7 ~7r with width and zero pad ~7,15,'0r" 63 63)
"base 7 120 with width and zero pad 000000000000120"
```

**Large number handling:**
```clojure
user=> (cl-format nil "cl-format handles BigInts ~15d" 12345678901234567890)
"cl-format handles BigInts 12345678901234567890"
```

**Type conversion awareness:**
```clojure
user=> (cl-format nil "Be aware of auto-conversion ~8,'0d ~8,'0d" 2.4 -5/4)
"Be aware of auto-conversion 000002.4 0000-5/4"

user=> (cl-format nil "~8,'0d" -2)
"000000-2"
```

**Nil handling difference:**
```clojure
user=> (cl-format nil "~s" nil)
"nil"

user=> (format "%s" nil)
"null"
```

**Text wrapping:**
```clojure
(def word-wrap
  ["This" "sentence" "is" "too" "long" "for" "a" "small" "screen"
   "and" "should" "appear" "in" "multiple" "lines" "no" "longer"
   "than" "20" "characters" "each" "."])

(println (cl-format nil "~{~<~%~1,20:;~A~> ~}" word-wrap))
```

**English word formatting:**
```clojure
user=> (cl-format true "~R~%" 63)
sixty-three

user=> (cl-format false "~R" 635464)
"six hundred thirty-five thousand, four hundred sixty-four"
```

**Newline handling:**
```clojure
;; ~ followed by newline and whitespace ignores the newline and whitespace
;; ~@ followed by newline and whitespace outputs the newline but ignores whitespace
;; ~& outputs a newline unless already at the beginning of a line
```

**Lazy sequence unwinding:**
```clojure
;; Returns string like "hello clojure.lang.LazySeq@3874d01 world\n"
(format "hello %s world\n" (map list '(1 2 3 4 5)))

;; Returns "hello ((1) (2) (3) (4) (5)) world\n"
(cl-format false "hello ~A world~%" (map list '(1 2 3 4 5)))
```

## Documentation References

Detailed documentation on format control strings is available in:
- "Common Lisp the Language, 2nd edition", Chapter 22
- Common Lisp HyperSpec at http://www.lispworks.com/documentation/HyperSpec/Body/22_c.htm

## See Also

- printf - Prints formatted output, as per format
- format - Formats a string using java.lang.String.format

## License

(c) Rich Hickey. All rights reserved. Eclipse Public License 1.0

---

Source: https://clojuredocs.org/clojure.pprint/cl-format

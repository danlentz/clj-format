(ns clj-format.core
  "Drop-in replacement for clojure.pprint/cl-format.

  When the format argument is a string, delegates directly to cl-format.
  When it is a DSL form (vector or keyword), compiles it to a format string
  first, then invokes cl-format with the result."
  (:require [clojure.pprint :as pp]
            [clj-format.compiler :as compiler]))

(defn clj-format
  "Format args according to fmt, writing to writer.

  fmt can be:
    - a string    — passed directly to cl-format (full backward compatibility)
    - a vector    — compiled from DSL to a format string, then passed to cl-format
    - a keyword   — shorthand for a single bare directive (e.g., :str for ~A)

  writer can be:
    - nil/false   — returns the formatted string
    - true        — prints to *out*, returns nil
    - a Writer    — writes to that writer, returns nil

  Examples:
    (clj-format nil \"~D item~:P\" 5)                            ;; => \"5 items\"
    (clj-format nil [:int \" item\" [:plural {:rewind true}]] 5) ;; => \"5 items\"
    (clj-format nil [:each {:sep \", \"} :str] [1 2 3])          ;; => \"1, 2, 3\"
    (clj-format true \"Hello ~A!\" \"world\")                    ;; prints, returns nil"
  [writer fmt & args]
  (let [fmt-str (cond
                  (string? fmt)  fmt
                  (vector? fmt)  (compiler/compile-format fmt)
                  (keyword? fmt) (compiler/compile-format [fmt])
                  :else (throw (ex-info "Format spec must be a string, vector, or keyword"
                                        {:fmt fmt})))]
    (apply pp/cl-format writer fmt-str args)))

(ns clj-format.core
  "Drop-in replacement for host cl-format.

  When the format argument is a string, delegates directly to cl-format.
  When it is a DSL form (vector or keyword), compiles it to a format string
  first, then invokes cl-format with the result."
  (:require #?(:clj  [clojure.pprint :as pp]
               :cljs [cljs.pprint :as pp])
            #?(:cljs [cljs.core :as core])
            [clj-format.compiler :as compiler]
            [clj-format.errors :as err]
            [clj-format.parser :as parser]))

(defn- valid-output-target?
  "True when target is a supported cl-format destination."
  [target]
  (or (nil? target)
      (true? target)
      #?(:clj  (instance? java.io.Writer target)
         :cljs (satisfies? core/IWriter target))))

(defn- normalize-output-target
  "Normalize public output-target shorthands for cl-format."
  [target]
  (if (false? target) nil target))

(defn parse-format
  "Parse a cl-format string into the clj-format DSL.

  Examples:
    (parse-format \"~A\")             ;; => [:str]
    (parse-format \"Hello ~A!\")      ;; => [\"Hello \" :str \"!\"]
    (parse-format \"~R file~:P\")     ;; => [:cardinal \" file\" [:plural {:rewind true}]]"
  [s]
  (parser/parse-format s))

(defn compile-format
  "Compile a clj-format DSL form into a cl-format string.

  Examples:
    (compile-format :str)                                 ;; => \"~A\"
    (compile-format [:str {:width 10}])                   ;; => \"~10A\"
    (compile-format [:cardinal \" file\" [:plural {:rewind true}]])
                                                         ;; => \"~R file~:P\""
  [dsl]
  (compiler/compile-format dsl))

(defn clj-format
  "Format args according to fmt, writing to writer.

  fmt can be:
    - a string    — passed directly to cl-format (full backward compatibility)
    - a vector    — compiled from DSL to a format string, then passed to cl-format
    - a keyword   — shorthand for a single bare directive (e.g., :str for ~A)

  writer can be:
    - nil/false   — returns the formatted string
    - true        — prints to host default output, returns nil
    - a writer    — writes to that writer, returns nil

  Examples:
    (clj-format nil \"~D item~:P\" 5)                            ;; => \"5 items\"
    (clj-format nil [:int \" item\" [:plural {:rewind true}]] 5) ;; => \"5 items\"
    (clj-format nil [:each {:sep \", \"} :str] [1 2 3])          ;; => \"1, 2, 3\"
    (clj-format true \"Hello ~A!\" \"world\")                    ;; prints, returns nil"
  [writer fmt & args]
  (let [target  (normalize-output-target writer)
        fmt-str (cond
                  (string? fmt)  fmt
                  (vector? fmt)  (compile-format fmt)
                  (keyword? fmt) (compile-format [fmt])
                  :else          (throw (err/invalid-format-spec fmt)))]
    (when-not (valid-output-target? target)
      (throw (err/invalid-output-target writer #?(:clj :clj :cljs :cljs))))
    (apply pp/cl-format target fmt-str args)))

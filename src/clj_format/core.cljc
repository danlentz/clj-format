(ns clj-format.core
  "Drop-in replacement for host cl-format, plus tabular formatting.

  When the format argument is a string, delegates directly to cl-format.
  When it is a DSL form (vector or keyword), compiles it to a format string
  first, then invokes cl-format with the result.

  Table specifications of the form [:table opts? & cols] are recognized by
  clj-format and dispatched through the clj-format.table facility. Tables
  honor the same writer semantics as every other format call."
  (:require #?(:clj  [clojure.pprint :as pp]
               :cljs [cljs.pprint :as pp])
            #?(:cljs [cljs.core :as core])
            [clj-format.compiler :as compiler]
            [clj-format.errors :as err]
            [clj-format.parser :as parser]
            [clj-format.table :as table]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Output Target Handling
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Optional DSL Preprocessor
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^{:dynamic true
       :doc "Function applied to every DSL vector before compilation.

  Defaults to identity. Extension namespaces (e.g. clj-format.figlet)
  may install a custom preprocessor by altering the root binding. A
  preprocessor must accept and return a DSL form.

  The preprocessor is only called on vector-shaped fmt arguments, so
  strings and bare keywords pass through untouched."}
  *dsl-preprocessor*
  identity)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DSL Entry Points
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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

(defn- table-form?
  "True when fmt is a [:table ...] spec."
  [fmt]
  (and (vector? fmt) (= :table (first fmt))))

(defn clj-format
  "Format args according to fmt, writing to writer.

  fmt can be:
    - a string           — passed directly to cl-format (full backward compatibility)
    - a vector           — compiled from the clj-format DSL to a format string
    - a keyword          — shorthand for a single bare directive (e.g., :str for ~A)
    - a [:table ...] spec — rendered via the clj-format.table facility

  writer can be:
    - nil/false   — returns the formatted string
    - true        — prints to host default output, returns nil
    - a writer    — writes to that writer, returns nil

  Examples:
    (clj-format nil \"~D item~:P\" 5)                            ;; => \"5 items\"
    (clj-format nil [:int \" item\" [:plural {:rewind true}]] 5) ;; => \"5 items\"
    (clj-format nil [:each {:sep \", \"} :str] [1 2 3])          ;; => \"1, 2, 3\"
    (clj-format true \"Hello ~A!\" \"world\")                    ;; prints, returns nil

    (clj-format nil [:table :name :age]
                [{:name \"Alice\" :age 30} {:name \"Bob\" :age 25}])
    (clj-format true [:table {:style :unicode}] rows)"
  [writer fmt & args]
  (let [target (normalize-output-target writer)
        fmt    (if (vector? fmt) (*dsl-preprocessor* fmt) fmt)]
    (when-not (valid-output-target? target)
      (throw (err/invalid-output-target writer #?(:clj :clj :cljs :cljs))))
    (cond
      (table-form? fmt)
      (table/render-to target fmt (first args))

      (string? fmt)
      (apply pp/cl-format target fmt args)

      (vector? fmt)
      (apply pp/cl-format target (compile-format fmt) args)

      (keyword? fmt)
      (apply pp/cl-format target (compile-format [fmt]) args)

      :else
      (throw (err/invalid-format-spec fmt)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Table Helpers (re-exported from clj-format.table)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn table-dsl
  "Build the table DSL and argument list without rendering.

  Returns a map with :dsl (the clj-format DSL body vector) and :args
  (the argument list). Useful for inspecting or reusing the generated
  DSL.

  Examples:
    (table-dsl [:table :name :age] [{:name \"Alice\" :age 30}])
    ;; => {:dsl [...] :args [\"Name\" \"Age\" [[\"Alice\" 30]]]}"
  [spec rows]
  (table/table-dsl spec rows))

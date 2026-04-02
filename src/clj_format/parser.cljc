(ns clj-format.parser
  "Parses cl-format format strings into the clj-format s-expression DSL.

  Follows the Hiccup convention: [:keyword optional-map & children].
  Bare keywords represent no-opts directives. Strings are literal text.

  Examples:
    (parse-format \"~A\")             ;=> [:str]
    (parse-format \"Hello ~A!\")      ;=> [\"Hello \" :str \"!\"]
     (parse-format \"~{~A~^, ~}\")    ;=> [[:each {:sep \", \"} :str]]
     (parse-format \"~:[no~;yes~]\")  ;=> [[:if \"yes\" \"no\"]]
     (parse-format \"~:(~A~)\")       ;=> [[:str {:case :capitalize}]]"
  (:require [clojure.string :as str]
            [clj-format.directives :as d]
            [clj-format.errors :as err]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; String Scanning Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- string-length
  "Returns the number of characters in s."
  [s]
  #?(:clj (.length ^String s)
     :cljs (.-length s)))

(defn- char-at
  "Returns the character at pos in s, or nil if out of bounds."
  [s pos]
  (when (< pos (string-length s))
    #?(:clj (.charAt ^String s pos)
       :cljs (.charAt s pos))))

(defn- substring
  "Returns substring of s from start to end."
  [s start end]
  #?(:clj (.substring ^String s (int start) (int end))
     :cljs (.substring s start end)))

(defn- digit?
  "True when c is an ASCII digit."
  [c]
  (boolean (re-matches #"\d" (str c))))

(defn- parse-number
  "Parse a decimal integer string."
  [s]
  #?(:clj (Long/parseLong s)
     :cljs (js/parseInt s 10)))

(defn- upper-char
  "Convert an ASCII directive character to uppercase."
  [c]
  #?(:clj (Character/toUpperCase ^char c)
     :cljs (first (str/upper-case (str c)))))

(defn- parse-int
  "Parse a signed integer at pos. Returns [value end-pos] or nil."
  [s pos]
  (when (< pos (string-length s))
    (let [c           (char-at s pos)
          signed?     (or (= c \+) (= c \-))
          digit-start (long (if signed? (inc pos) pos))
          digit-end   (loop [i digit-start]
                        (if (and (< i (string-length s))
                                 (digit? (char-at s i)))
                          (recur (inc i))
                          i))]
      (when (> digit-end digit-start)
        [(parse-number (substring s pos digit-end)) digit-end]))))

(defn- skip-whitespace
  "Advance pos past any spaces and tabs."
  [s pos]
  (loop [p pos]
    (case (char-at s p)
      (\space \tab) (recur (inc p))
      p)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parameter and Flag Parsing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-one-param
  "Parse a single directive parameter at pos.
   Handles integer literals, character literals ('x), V, and #.
   Returns [value end-pos] or nil if no param found."
  [s pos]
  (when-let [c (char-at s pos)]
    (case c
      \'              [(char-at s (inc pos)) (+ pos 2)]
      (\V \v)         [:V (inc pos)]
      \#              [:# (inc pos)]
      (parse-int s pos))))

(defn- parse-params
  "Parse comma-separated directive parameters after a tilde.
   Parameters may be integers, 'char literals, V (arg value), # (arg count),
   or omitted (nil). Returns [param-vec end-pos]."
  [s pos]
  (let [[first-val first-end] (or (parse-one-param s pos)
                                  (when (= (char-at s pos) \,)
                                    [nil pos]))]
    (if (nil? first-end)
      [[] pos]
      (loop [pos (long first-end), params [first-val]]
        (if (= (char-at s pos) \,)
          (let [next (inc pos)
                [v p] (or (parse-one-param s next) [nil next])]
            (recur (long p) (conj params v)))
          [params pos])))))

(defn- parse-flags
  "Parse colon and at-sign modifier flags.
   Returns [flag-map end-pos] where flag-map has only truthy keys."
  [s pos]
  (loop [pos (long pos), colon false, at false]
    (case (char-at s pos)
      \: (recur (inc pos) true at)
      \@ (recur (inc pos) colon true)
      [(cond-> {}
         colon (assoc :colon true)
         at    (assoc :at true))
       pos])))

(defn- colon-at-dispatch
  "Dispatch on :colon/:at flag combination. Returns one of four values."
  [flags both colon at neither]
  (let [c (:colon flags), a (:at flags)]
    (cond
      (and c a) both
      c         colon
      a         at
      :else     neither)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DSL Form Construction
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-simple-directive
  "Build a DSL form for a simple directive using its config entry.
   Returns bare keyword when no opts, or [keyword opts-map] when present."
  [{:keys [kw] :as config} positional-params raw-flags]
  (let [opts (d/raw->opts config positional-params raw-flags)]
    (if (seq opts) [kw opts] kw)))

(defn- inline-clause
  "Convert a body-elements vector to a single inline clause value.
   Follows the Hiccup convention — unwrap single-element bodies:
     []    -> nil  (empty clause)
     [x]   -> x   (bare keyword, string, or directive vector)
     [x y] -> [x y] (multi-element body vector)"
  [body-elements]
  (case (count body-elements)
    0 nil
    1 (first body-elements)
    (vec body-elements)))

(defn- make-body-compound
  "Build compound directive with inline body (Hiccup convention).
   [:keyword opts? & body-elements]"
  [kw opts body-elements]
  (if (seq opts)
    (into [kw opts] body-elements)
    (into [kw] body-elements)))

(defn- make-clause-compound
  "Build multi-clause compound with inline clauses (Hiccup convention).
   [:keyword opts? clause1 clause2 ...]"
  [kw opts clauses]
  (let [inlined (mapv inline-clause clauses)]
    (if (seq opts)
      (into [kw opts] inlined)
      (into [kw] inlined))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Recursive Descent Parser
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare parse-body)

(defn- detect-sep
  "Detect the ~^separator pattern at the end of an iteration body.
   If elements end with bare :stop followed by a literal string, and there
   is exactly one :stop in the body, extract it as a :sep value.
   Returns [cleaned-elements sep-string-or-nil]."
  [elements]
  (let [n (count elements)]
    (if (and (>= n 2)
             (= :stop (nth elements (- n 2)))
             (string? (peek elements))
             (= 1 (count (filter #{:stop} elements))))
      [(subvec elements 0 (- n 2)) (peek elements)]
      [elements nil])))

(defn- merge-case-into
  "Merge a :case option into a single DSL element. Returns the merged form,
   or nil if merging is not possible (element is a string, or already has
   a :case option — avoiding double-wrapping)."
  [element mode]
  (cond
    (keyword? element)
    [element {:case mode}]

    (and (vector? element) (keyword? (first element)))
    (let [[kw & body] element
          [opts children] (if (map? (first body))
                            [(first body) (rest body)]
                            [{} body])]
      (when-not (:case opts)
        (into [kw (assoc opts :case mode)] children)))

    :else nil))

(defn- parse-choose
  "Parse ~[...~;...~] as numeric dispatch."
  [s pos param-opts]
  (loop [pos pos, clauses []]
    (let [[elements pos term-char term-flags] (parse-body s pos #{\] \;})]
      (if (= term-char \;)
        (if (:colon term-flags)
          (let [[default end-pos _ _] (parse-body s pos #{\]})]
            [(make-clause-compound :choose
               (assoc param-opts :default (inline-clause default))
               (conj clauses elements))
             end-pos])
          (recur (long pos) (conj clauses elements)))
        [(make-clause-compound :choose param-opts (conj clauses elements))
         pos]))))

(defn- parse-if
  "Parse ~:[false~;true~] — reverses clause order to true-first."
  [s pos]
  (let [[false-body pos _ _] (parse-body s pos #{\] \;})
        [true-body  pos _ _] (parse-body s pos #{\]})]
    [(into [:if] (mapv inline-clause [true-body false-body])) pos]))

(defn- parse-when
  "Parse ~@[body~] as truthiness guard."
  [s pos]
  (let [[body pos _ _] (parse-body s pos #{\]})]
    [(make-body-compound :when {} body) pos]))

(defn- parse-each
  "Parse ~{...~} as iteration."
  [s pos open-flags open-params]
  (let [[elements pos _ term-flags] (parse-body s pos #{\}})
        [elements sep] (detect-sep elements)
        opts (cond-> {}
               sep                   (assoc :sep sep)
               (seq open-params)     (assoc :max (first open-params))
               (:colon term-flags)   (assoc :min 1))
        opts (if-let [from (colon-at-dispatch open-flags
                             :rest-sublists :sublists :rest nil)]
               (assoc opts :from from)
               opts)]
    [(make-body-compound :each opts elements) pos]))

(defn- parse-case-conversion
  "Parse ~(...~). Flattens to a :case option when the body is a single
   element; falls back to compound form for multi-element bodies."
  [s pos open-flags]
  (let [[elements pos _ _] (parse-body s pos #{\)})
        mode (colon-at-dispatch open-flags :upcase :capitalize :titlecase :downcase)
        merged (when (= 1 (count elements))
                 (merge-case-into (first elements) mode))]
    [(or merged (make-body-compound mode {} elements)) pos]))

(defn- parse-justification
  "Parse ~<...~;...~> as justification or logical block."
  [s pos open-params open-flags]
  (loop [pos pos, clauses []]
    (let [[elements pos term-char term-flags] (parse-body s pos #{\> \;})]
      (if (= term-char \;)
        (recur (long pos) (conj clauses elements))
        (let [all-clauses (conj clauses elements)
              logical?    (:colon term-flags)
              kw          (if logical? :logical-block :justify)
              named       (d/positional->named
                            (if logical? [] [:width :pad-step :min-pad :fill])
                            open-params)
              flag-opts   (if logical?
                            (cond-> {} (:colon open-flags) (assoc :colon true))
                            (cond-> {}
                              (:colon open-flags) (assoc :pad-before true)
                              (:at open-flags)    (assoc :pad-after true)))]
          [(make-clause-compound kw (merge named flag-opts) all-clauses) pos])))))

(defn- parse-compound
  "Parse a compound directive. Returns [dsl-form end-pos]."
  [s pos open-char params flags]
  (let [pos (long pos)]
    (case open-char
      \[ (cond
           (:colon flags) (parse-if s pos)
           (:at flags)    (parse-when s pos)
           :else          (parse-choose s pos (d/positional->named [:selector] params)))
      \{ (parse-each s pos flags params)
      \( (parse-case-conversion s pos flags)
      \< (parse-justification s pos params flags))))

(defn- parse-directive
  "Parse a single directive starting after the tilde.
   Returns [dsl-form end-pos]. dsl-form is nil for format-string
   newlines that produce no output (plain ~\\n and ~:\\n)."
  [s pos]
  (let [[params pos] (parse-params s pos)
        [flags pos]  (parse-flags s pos)
        c            (char-at s pos)
        pos          (inc pos)]
    (cond
      (#{\[ \{ \( \<} c)
      (parse-compound s pos c params flags)

      ;; Tilde-newline: plain/colon produce no output, at emits newline.
      ;; Plain and at consume trailing whitespace; colon preserves it.
      (= c \newline)
      (let [pos (if (:colon flags) pos (skip-whitespace s pos))]
        (if (:at flags) [:nl pos] [nil pos]))

      :else
      (let [uc (upper-char c)]
        (cond
          (d/+special-chars+ uc)
          [(d/parse-special uc params flags) pos]

          :else
          (if-let [config (get d/+char->simple+ uc)]
            [(build-simple-directive config params flags) pos]
            (throw (err/parse-error
                     (str "Unknown directive character: " c)
                     {:kind :unknown-directive
                      :char c
                      :position (dec pos)
                      :format-string s}))))))))

(defn- parse-body
  "Parse a sequence of literal text and directives until end of string
   or a terminating directive character from the terminators set.
   Returns [elements end-pos term-char term-flags]."
  [s pos terminators]
  (loop [pos (long pos), elements []]
    (if (>= pos (string-length s))
      [elements pos nil nil]
      (let [c (char-at s pos)]
        (if (= c \~)
          ;; Peek ahead to check for terminator before full parse
          (let [peek-pos              (inc pos)
                [_params peek-pos]    (parse-params s peek-pos)
                [peek-flags peek-pos] (parse-flags s peek-pos)
                peek-char             (char-at s peek-pos)]
            (if (contains? terminators peek-char)
              [elements (inc peek-pos) peek-char peek-flags]
              (let [[form end-pos] (parse-directive s (inc pos))]
                (recur (long end-pos) (cond-> elements form (conj form))))))
          ;; Literal text — accumulate until next tilde or end
          (let [end (loop [i pos]
                      (if (and (< i (string-length s)) (not= (char-at s i) \~))
                        (recur (inc i))
                        i))]
            (recur (long end) (conj elements (substring s pos end)))))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-format
  "Parse a cl-format format string into the clj-format s-expression DSL.

   Returns a vector of elements: literal strings, bare keywords (simple
   directives), and vectors (directives with opts or compound directives).
   Follows the Hiccup convention: [:keyword optional-map & children].

   Examples:
     (parse-format \"~A\")             ;=> [:str]
     (parse-format \"Hello ~A!\")      ;=> [\"Hello \" :str \"!\"]
     (parse-format \"~R file~:P\")     ;=> [:cardinal \" file\" [:plural {:rewind true}]]
     (parse-format \"~10:D\")          ;=> [[:int {:width 10 :group true}]]
     (parse-format \"~{~A~^, ~}\")    ;=> [[:each {:sep \", \"} :str]]
     (parse-format \"~:[no~;yes~]\")  ;=> [[:if \"yes\" \"no\"]]
     (parse-format \"~:(~A~)\")       ;=> [[:str {:case :capitalize}]]"
  [s]
  (first (parse-body s 0 #{})))

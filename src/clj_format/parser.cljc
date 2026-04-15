(ns clj-format.parser
  "Parses cl-format format strings into the clj-format s-expression DSL.

  Follows the Hiccup convention: [:keyword optional-map & children].
  Bare keywords represent no-opts directives. Strings are literal text.

  Examples:
    (parse-format \"~A\")             ;=> [:str]
    (parse-format \"Hello ~A!\")      ;=> [\"Hello \" :str \"!\"]
    (parse-format \"~{~A~^, ~}\")     ;=> [[:each {:sep \", \"} :str]]
    (parse-format \"~:[no~;yes~]\")   ;=> [[:if \"yes\" \"no\"]]
    (parse-format \"~:(~A~)\")        ;=> [[:str {:case :capitalize}]]"
  (:require [clojure.string :as str]
            [clj-format.directives :as d]
            [clj-format.errors :as err]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; String Scanning Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- char-at
  "Returns the character at pos in s, or nil if out of bounds."
  [s pos]
  (when (< pos (count s))
    (nth s pos)))

(defn- parse-number
  "Parse a decimal integer string."
  [s]
  #?(:clj (Long/parseLong s)
     :cljs (js/parseInt s 10)))

(defn- digit-char?
  "True when c is an ASCII digit."
  [c]
  (contains? #{\0 \1 \2 \3 \4 \5 \6 \7 \8 \9} c))

(defn- parse-int
  "Parse a signed integer at pos. Returns [value end-pos] or nil."
  [s pos]
  (when (< pos (count s))
    (let [c           (char-at s pos)
          signed?     (or (= c \+) (= c \-))
          digit-start (if signed? (inc pos) pos)
          digit-end   (loop [i digit-start]
                        (if (and (< i (count s))
                                 (digit-char? (char-at s i)))
                          (recur (inc i))
                          i))]
      (when (> digit-end digit-start)
        [(parse-number (subs s pos digit-end)) digit-end]))))

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
   or omitted (nil — e.g. `~,3D` has nil as its first parameter and 3 as
   its second). Returns [param-vec end-pos]."
  [s pos]
  (let [[first-val first-end] (or (parse-one-param s pos)
                                  (when (= (char-at s pos) \,)
                                    [nil pos]))]
    (if (nil? first-end)
      [[] pos]
      (loop [pos first-end, params [first-val]]
        (if (= (char-at s pos) \,)
          (let [next (inc pos)
                [v p] (or (parse-one-param s next) [nil next])]
            (recur p (conj params v)))
          [params pos])))))

(defn- parse-flags
  "Parse colon and at-sign modifier flags.
   Returns [flag-map end-pos] where flag-map has only truthy keys."
  [s pos]
  (loop [pos pos, colon false, at false]
    (case (char-at s pos)
      \: (recur (inc pos) true at)
      \@ (recur (inc pos) colon true)
      [(cond-> {}
         colon (assoc :colon true)
         at    (assoc :at true))
       pos])))

(defn- scan-directive-head
  "Scan directive params, flags, and directive char starting at `pos`
   immediately after a tilde. Returns a map with:
   `:params`, `:flags`, `:char`, `:char-pos`, and `:next-pos`."
  [s pos]
  (let [[params pos] (parse-params s pos)
        [flags pos]  (parse-flags s pos)]
    {:params   params
     :flags    flags
     :char     (char-at s pos)
     :char-pos pos
     :next-pos (inc pos)}))

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

(defn- make-compound
  "Build a compound directive form.
   Returns `[:keyword opts? & children]`."
  [kw opts children]
  (if (seq opts)
    (into [kw opts] children)
    (into [kw] children)))

(defn- assoc-some
  "Associate k with v only when v is non-nil."
  [m k v]
  (if (some? v)
    (assoc m k v)
    m))

(defn- wrap-clause
  "Inline a clause body, optionally preserving clause-local separator opts.
   Plain clauses stay as nil/string/keyword/vector. When opts are present, the
   clause is wrapped as [:clause opts & body-elements]."
  [body-elements opts]
  (if (seq opts)
    (into [:clause opts] body-elements)
    (inline-clause body-elements)))

(defn- justify-flag-opts
  "Translate `:justify` colon/at flags into semantic `:pad-before` /
   `:pad-after` options. Shared between the outer `~<...~>` directive
   and its `~;` clause-local separators."
  [flags]
  (cond-> {}
    (:colon flags) (assoc :pad-before true)
    (:at flags)    (assoc :pad-after true)))

(defn- justify-separator-opts
  "Translate raw ~; params/flags inside ~<...~> into named clause opts."
  [params flags]
  (merge (d/positional->named [:width :pad-step :min-pad :fill] params)
         (justify-flag-opts flags)))


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
    (let [{:keys [elements end-pos term-char term-flags]} (parse-body s pos #{\] \;})]
      (if (= term-char \;)
        (if (:colon term-flags)
          (let [default (parse-body s end-pos #{\]})]
            [(make-compound :choose
               (assoc-some param-opts :default (inline-clause (:elements default)))
               (conj clauses (inline-clause elements)))
             (:end-pos default)])
          (recur end-pos (conj clauses (inline-clause elements))))
        [(make-compound :choose param-opts (conj clauses (inline-clause elements)))
         end-pos]))))

(defn- parse-if
  "Parse ~:[false~;true~] — reverses clause order to true-first."
  [s pos]
  (let [false-body (parse-body s pos                 #{\] \;})
        true-body  (parse-body s (:end-pos false-body) #{\]})]
    [(into [:if] (mapv #(inline-clause (:elements %)) [true-body false-body]))
     (:end-pos true-body)]))

(defn- parse-when
  "Parse ~@[body~] as truthiness guard."
  [s pos]
  (let [{:keys [elements end-pos]} (parse-body s pos #{\]})]
    [(make-compound :when {} elements) end-pos]))

(defn- parse-each
  "Parse ~{...~} as iteration."
  [s pos open-flags open-params]
  (let [{:keys [elements end-pos term-flags]} (parse-body s pos #{\}})
        [elements sep] (detect-sep elements)
        opts (cond-> {}
               sep                   (assoc :sep sep)
               (seq open-params)     (assoc :max (first open-params))
               (:colon term-flags)   (assoc :min 1))
        opts (if-let [from (colon-at-dispatch open-flags
                             :rest-sublists :sublists :rest nil)]
               (assoc opts :from from)
               opts)]
    [(make-compound :each opts elements) end-pos]))

(defn- parse-case-conversion
  "Parse ~(...~). Flattens to a :case option when the body is a single
   element; falls back to compound form for multi-element bodies."
  [s pos open-flags]
  (let [{:keys [elements end-pos]} (parse-body s pos #{\)})
        mode (colon-at-dispatch open-flags :upcase :capitalize :titlecase :downcase)
        merged (when (= 1 (count elements))
                 (merge-case-into (first elements) mode))]
    [(or merged (make-compound mode {} elements)) end-pos]))

(defn- parse-justification
  "Parse ~<...~;...~> as justification or logical block."
  [s pos open-params open-flags]
  (loop [pos pos, clauses [], pending-clause-opts nil]
    (let [{:keys [elements end-pos term-char term-params term-flags]}
          (parse-body s pos #{\> \;})
          clause (wrap-clause elements pending-clause-opts)]
      (if (= term-char \;)
        (recur end-pos
               (conj clauses clause)
               (justify-separator-opts term-params term-flags))
        (let [all-clauses (conj clauses clause)
              [kw named flag-opts]
              (if (:colon term-flags)
                [:logical-block
                 {}
                 (cond-> {} (:colon open-flags) (assoc :colon true))]
                [:justify
                 (d/positional->named [:width :pad-step :min-pad :fill] open-params)
                 (justify-flag-opts open-flags)])]
          [(make-compound kw (merge named flag-opts) all-clauses) end-pos])))))

(defn- parse-compound
  "Parse a compound directive. Returns [dsl-form end-pos]."
  [s pos open-char params flags]
  (case open-char
    \[ (cond
         (:colon flags) (parse-if s pos)
         (:at flags)    (parse-when s pos)
         :else          (parse-choose s pos (d/positional->named [:selector] params)))
    \{ (parse-each s pos flags params)
    \( (parse-case-conversion s pos flags)
    \< (parse-justification s pos params flags)))

(defn- parse-directive
  "Parse a single directive starting after the tilde.
   Returns [dsl-form end-pos]. dsl-form is nil for format-string
   newlines that produce no output (plain ~\\n and ~:\\n)."
  [s pos]
  (let [{:keys [params flags char-pos next-pos] dchar :char}
        (scan-directive-head s pos)]
    (cond
      (#{\[ \{ \( \<} dchar)
      (parse-compound s next-pos dchar params flags)

      ;; Tilde-newline: plain/colon produce no output, at emits newline.
      ;; Plain and at consume trailing whitespace; colon preserves it.
      (= dchar \newline)
      (let [after (if (:colon flags) next-pos (skip-whitespace s next-pos))]
        (if (:at flags) [:nl after] [nil after]))

      :else
      (let [uc (first (str/upper-case (str dchar)))]
        (cond
          (d/+special-chars+ uc)
          [(d/parse-special uc params flags) next-pos]

          :else
          (if-let [config (get d/+char->simple+ uc)]
            [(build-simple-directive config params flags) next-pos]
            (throw (err/parse-error
                     (str "Unknown directive character: " dchar)
                     {:kind :unknown-directive
                      :char dchar
                      :position char-pos
                      :format-string s}))))))))

(defn- scan-literal-end
  "Advance past a run of literal text, stopping at the next tilde
   or end-of-string. Returns the end index."
  [s pos]
  (loop [i pos]
    (if (and (< i (count s)) (not= (char-at s i) \~))
      (recur (inc i))
      i)))

(defn- parse-body
  "Parse a sequence of literal text and directives until end of string
   or a terminating directive character from the terminators set.

   Returns a map with `:elements` (the parsed body vector), `:end-pos`
   (the position just past the terminator, or EOF), and — when parsing
   stopped on a terminator — `:term-char`, `:term-params`, and
   `:term-flags` describing that terminator. At EOF those three keys
   are nil. Compound directives peek at `:term-flags` to distinguish
   shapes like `~;` vs `~:;` inside `~[...~]`."
  [s pos terminators]
  (loop [pos pos, elements []]
    (if (>= pos (count s))
      {:elements elements :end-pos pos
       :term-char nil :term-params nil :term-flags nil}
      (if (= (char-at s pos) \~)
        ;; Peek ahead to check for terminator before full parse
        (let [{:keys [params flags char next-pos]}
              (scan-directive-head s (inc pos))]
          (if (contains? terminators char)
            {:elements elements :end-pos next-pos
             :term-char char :term-params params :term-flags flags}
            (let [[form end-pos] (parse-directive s (inc pos))]
              (recur end-pos (cond-> elements form (conj form))))))
        ;; Literal text — accumulate until next tilde or end
        (let [end (scan-literal-end s pos)]
          (recur end (conj elements (subs s pos end))))))))


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
  (:elements (parse-body s 0 #{})))

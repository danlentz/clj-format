(ns clj-format.parser
  "Parses cl-format format strings into the clj-format s-expression DSL.

  Follows the Hiccup convention: [:keyword optional-map & children].
  Bare keywords represent no-opts directives. Strings are literal text.

  Examples:
    (parse-format \"~A\")             ;=> [:str]
    (parse-format \"Hello ~A!\")      ;=> [\"Hello \" :str \"!\"]
    (parse-format \"~{~A~^, ~}\")    ;=> [[:each {:sep \", \"} :str]]
    (parse-format \"~:[no~;yes~]\")  ;=> [[:if \"yes\" \"no\"]]
    (parse-format \"~:(~A~)\")       ;=> [[:capitalize :str]]"
  (:require [clj-format.directives :as d]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; String Scanning Utilities
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- char-at
  "Returns the character at pos in s, or nil if out of bounds."
  [^String s ^long pos]
  (when (< pos (.length s))
    (.charAt s pos)))

(defn- parse-int
  "Parse a signed integer at pos. Returns [value end-pos] or nil."
  [^String s ^long pos]
  (when (< pos (.length s))
    (let [c          (.charAt s pos)
          signed     (or (= c \+) (= c \-))
          digit-start (long (if signed (inc pos) pos))
          end        (loop [i digit-start]
                       (if (and (< i (.length s))
                                (Character/isDigit (.charAt s i)))
                         (recur (inc i))
                         i))]
      (when (> end digit-start)
        [(Long/parseLong (.substring s (int pos) (int end))) end]))))

(defn- skip-whitespace
  "Advance pos past any spaces and tabs."
  ^long [^String s ^long pos]
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
  [^String s ^long pos]
  (when-let [c (char-at s pos)]
    (cond
      (= c \')                  [(.charAt s (inc pos)) (+ pos 2)]
      (or (= c \V) (= c \v))   [:V (inc pos)]
      (= c \#)                  [:# (inc pos)]
      true                      (parse-int s pos))))

(defn- parse-params
  "Parse comma-separated directive parameters after a tilde.
   Parameters may be integers, 'char literals, V (arg value), # (arg count),
   or omitted (nil). Returns [param-vec end-pos]."
  [^String s ^long pos]
  (let [has-param (some? (parse-one-param s pos))
        has-comma (= (char-at s pos) \,)]
    (if-not (or has-param has-comma)
      [[] pos]
      (let [[first-val first-end] (if-let [[v p] (parse-one-param s pos)]
                                    [v p]
                                    [nil pos])]
        (loop [pos (long first-end), params [first-val]]
          (if (= (char-at s pos) \,)
            (let [next-pos (inc pos)]
              (if-let [[v p] (parse-one-param s next-pos)]
                (recur (long p) (conj params v))
                (recur next-pos (conj params nil))))
            [params pos]))))))

(defn- parse-flags
  "Parse colon and at-sign modifier flags.
   Returns [flag-map end-pos] where flag-map has only truthy keys."
  [^String s ^long pos]
  (loop [pos (long pos), colon false, at false]
    (case (char-at s pos)
      \: (recur (inc pos) true at)
      \@ (recur (inc pos) colon true)
      [(cond-> {}
         colon (assoc :colon true)
         at    (assoc :at true))
       pos])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DSL Form Construction
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- positional->named
  "Convert positional params to a named map using the given name list.
   Nil values are omitted."
  [param-names params]
  (into {}
        (keep-indexed (fn [i v]
                        (when (and (some? v) (< i (count param-names)))
                          [(nth param-names i) v])))
        params))

(defn- translate-flags
  "Translate raw colon/at flags to semantic options using a flag-map.
   flag-map is {:colon [key val], :at [key val]}."
  [raw-flags flag-map]
  (reduce-kv (fn [m flag-key [opt-key opt-val]]
               (if (get raw-flags flag-key)
                 (assoc m opt-key opt-val)
                 m))
             {} flag-map))

(defn- build-simple-directive
  "Build a DSL form for a simple directive using its config entry.
   Returns bare keyword when no opts, or [keyword opts-map] when present."
  [config positional-params raw-flags]
  (let [{:keys [kw params flags]} config
        named (positional->named params positional-params)
        flag-opts (translate-flags raw-flags flags)
        opts (merge named flag-opts)]
    (if (seq opts)
      [kw opts]
      kw)))

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
;; Special Directive Dispatch
;;
;; R, *, and _ map to different DSL keywords based on flags/params.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- translate-radix
  "~R dispatches to :cardinal, :ordinal, :roman, :old-roman, or :radix."
  [params raw-flags]
  (if (seq params)
    (let [named (positional->named (d/param-names :radix) params)
          flag-opts (translate-flags raw-flags (d/flag-rules :radix))
          opts (merge named flag-opts)]
      (if (seq opts) [:radix opts] :radix))
    (cond
      (and (:colon raw-flags) (:at raw-flags)) :old-roman
      (:colon raw-flags)                        :ordinal
      (:at raw-flags)                           :roman
      true                                      :cardinal)))

(defn- translate-goto
  "~* dispatches to :skip, :back, or :goto based on flags."
  [params raw-flags]
  (let [kw (cond (:at raw-flags) :goto, (:colon raw-flags) :back, true :skip)
        named (positional->named (d/param-names kw) params)]
    (if (seq named) [kw named] kw)))

(defn- translate-break
  "~_ dispatches to [:break {:mode m}] or bare :break."
  [raw-flags]
  (let [mode (cond
               (and (:colon raw-flags) (:at raw-flags)) :mandatory
               (:colon raw-flags)                        :fill
               (:at raw-flags)                           :miser
               true                                      nil)]
    (if mode [:break {:mode mode}] :break)))


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
             (string? (nth elements (- n 1)))
             (= 1 (count (filter #(= :stop %) elements))))
      [(subvec elements 0 (- n 2)) (nth elements (- n 1))]
      [elements nil])))

(defn- merge-case-into
  "Merge a :case option into a single DSL element. Returns the merged form,
   or nil if merging is not possible (element is a string, or already has
   a :case option — avoiding double-wrapping)."
  [element mode]
  (cond
    ;; Bare keyword like :str -> [:str {:case mode}]
    (keyword? element)
    [element {:case mode}]

    ;; Directive or compound vector — merge :case into opts
    (and (vector? element) (keyword? (first element)))
    (if (and (> (count element) 1) (map? (nth element 1)))
      (if (:case (nth element 1))
        nil ;; already has :case, can't merge
        (assoc element 1 (assoc (nth element 1) :case mode)))
      (into [(first element) {:case mode}] (subvec element 1)))

    ;; String or unknown — can't merge
    true nil))

(defn- parse-choose
  "Parse ~[...~;...~] as numeric dispatch."
  [^String s ^long pos param-opts]
  (loop [pos pos, clauses []]
    (let [[elements pos term-char term-flags] (parse-body s pos #{\] \;})]
      (if (= term-char \;)
        (if (:colon term-flags)
          (let [[default end-pos _ _] (parse-body s pos #{\]})]
            [(make-clause-compound :choose
               (assoc param-opts :default (inline-clause default))
               (conj clauses elements))
             end-pos])
          (recur pos (conj clauses elements)))
        [(make-clause-compound :choose param-opts (conj clauses elements))
         pos]))))

(defn- parse-if
  "Parse ~:[false~;true~] — reverses clause order to true-first."
  [^String s ^long pos]
  (let [[false-body pos _ _] (parse-body s pos #{\] \;})
        [true-body pos _ _]  (parse-body s pos #{\]})
        form (into [:if] (mapv inline-clause [true-body false-body]))]
    [form pos]))

(defn- parse-when
  "Parse ~@[body~] as truthiness guard."
  [^String s ^long pos]
  (let [[body pos _ _] (parse-body s pos #{\]})]
    [(make-body-compound :when {} body) pos]))

(defn- parse-each
  "Parse ~{...~} as iteration."
  [^String s ^long pos open-flags open-params]
  (let [[elements pos _term term-flags] (parse-body s pos #{\}})
        [elements sep] (detect-sep elements)
        from (cond
               (and (:colon open-flags) (:at open-flags)) :rest-sublists
               (:colon open-flags)                         :sublists
               (:at open-flags)                            :rest
               true                                        nil)
        max-val (first open-params)
        force   (:colon term-flags)
        opts (cond-> {}
               sep     (assoc :sep sep)
               from    (assoc :from from)
               max-val (assoc :max max-val)
               force   (assoc :min 1))]
    [(make-body-compound :each opts elements) pos]))

(defn- parse-case-conversion
  "Parse ~(...~). Flattens to a :case option when the body is a single
   element; falls back to compound form for multi-element bodies."
  [^String s ^long pos open-flags]
  (let [[elements pos _ _] (parse-body s pos #{\)})
        mode (cond
               (and (:colon open-flags) (:at open-flags)) :upcase
               (:colon open-flags)                         :capitalize
               (:at open-flags)                            :titlecase
               true                                        :downcase)]
    (if (= 1 (count elements))
      (if-let [merged (merge-case-into (first elements) mode)]
        [merged pos]
        [(make-body-compound mode {} elements) pos])
      [(make-body-compound mode {} elements) pos])))

(defn- parse-justification
  "Parse ~<...~;...~> as justification or logical block."
  [^String s ^long pos open-params open-flags]
  (loop [pos pos, clauses []]
    (let [[elements pos term-char term-flags] (parse-body s pos #{\> \;})]
      (if (= term-char \;)
        (recur pos (conj clauses elements))
        (let [all-clauses    (conj clauses elements)
              logical-block? (:colon term-flags)
              kw             (if logical-block? :logical-block :justify)
              param-names    (if logical-block? [] [:width :pad-step :min-pad :fill])
              named          (positional->named param-names open-params)
              flag-opts      (if logical-block?
                               (cond-> {}
                                 (:colon open-flags) (assoc :colon true))
                               (cond-> {}
                                 (:colon open-flags) (assoc :pad-before true)
                                 (:at open-flags)    (assoc :pad-after true)))
              opts           (merge named flag-opts)]
          [(make-clause-compound kw opts all-clauses) pos])))))

(defn- parse-compound
  "Parse a compound directive. Returns [dsl-form end-pos]."
  [^String s pos open-char params flags]
  (let [pos (long pos)]
    (case open-char
      \[ (cond
           (:colon flags) (parse-if s pos)
           (:at flags)    (parse-when s pos)
           true           (parse-choose s pos (positional->named [:selector] params)))
      \{ (parse-each s pos flags params)
      \( (parse-case-conversion s pos flags)
      \< (parse-justification s pos params flags))))

(defn- parse-directive
  "Parse a single directive starting after the tilde.
   Returns [dsl-form end-pos]. dsl-form is nil for format-string
   newlines that produce no output (plain ~\\n and ~:\\n)."
  [^String s ^long pos]
  (let [[params pos] (parse-params s pos)
        [flags pos]  (parse-flags s pos)
        c            (char-at s pos)
        pos          (inc pos)]
    (cond
      ;; Compound opening bracket
      (#{\[ \{ \( \<} c)
      (parse-compound s pos c params flags)

      ;; Tilde-newline: format string layout control
      ;; Plain and colon variants skip the newline (produce no output).
      ;; At variant emits a newline. Whitespace after is consumed for
      ;; plain and at; preserved for colon.
      (= c \newline)
      (let [pos (if (:colon flags) pos (skip-whitespace s pos))]
        (if (:at flags) [:nl pos] [nil pos]))

      ;; Special dispatch: R, *, _ — keyword depends on flags
      true
      (let [uc (Character/toUpperCase ^char c)]
        (case uc
          \R [(translate-radix params flags) pos]
          \* [(translate-goto params flags) pos]
          \_ [(translate-break flags) pos]
          ;; Standard simple directive via shared config
          (if-let [config (get d/+char->simple+ uc)]
            [(build-simple-directive config params flags) pos]
            (throw (ex-info (str "Unknown directive character: " c)
                            {:char c :position (dec pos)}))))))))

(defn- parse-body
  "Parse a sequence of literal text and directives until end of string
   or a terminating directive character from the terminators set.
   Returns [elements end-pos term-char term-flags]."
  [^String s ^long pos terminators]
  (loop [pos (long pos), elements []]
    (if (>= pos (.length s))
      [elements pos nil nil]
      (let [c (.charAt s pos)]
        (if (= c \~)
          ;; Peek ahead for terminator
          (let [peek-pos              (inc pos)
                [_params peek-pos]    (parse-params s peek-pos)
                [peek-flags peek-pos] (parse-flags s peek-pos)
                peek-char             (char-at s peek-pos)]
            (if (contains? terminators peek-char)
              [elements (inc peek-pos) peek-char peek-flags]
              (let [[form end-pos] (parse-directive s (inc pos))]
                (if (some? form)
                  (recur end-pos (conj elements form))
                  (recur end-pos elements)))))
          ;; Literal text — accumulate until next tilde or end
          (let [end (loop [i pos]
                      (if (and (< i (.length s)) (not= (.charAt s i) \~))
                        (recur (inc i))
                        i))]
            (recur end (conj elements (.substring s (int pos) (int end))))))))))


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
     (parse-format \"~10:D\")          ;=> [[:int {:width 10 :group true}]]
     (parse-format \"~{~A~^, ~}\")    ;=> [[:each {:sep \", \"} :str]]
     (parse-format \"~:[no~;yes~]\")  ;=> [[:if \"yes\" \"no\"]]
     (parse-format \"~:(~A~)\")       ;=> [[:str {:case :capitalize}]]"
  [^String s]
  (let [[elements _ _ _] (parse-body s 0 #{})]
    elements))

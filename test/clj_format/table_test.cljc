(ns clj-format.table-test
  "Tests for the clj-format.table tabular formatting facility.

  The table facility is a Hiccup-style DSL form: [:table opts? & cols].
  Users render tables via clj-format.core/clj-format, not a separate
  entry point. This test file exercises the table facility directly
  via that unified entry point."
  (:require [clj-format.core  :as fmt]
            [clj-format.table :as table]
            [clojure.string   :as str]
            [#?(:clj clojure.test :cljs cljs.test) :refer [deftest is testing]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private staff
  [{:name "Alice" :age 30 :role "Admin"}
   {:name "Bob"   :age 25 :role "User"}
   {:name "Carol" :age 35 :role "Editor"}])

(defn- render
  "Convenience: render a [:table ...] spec with rows, return string."
  [spec rows]
  (fmt/clj-format nil spec rows))

(defn- lines [s] (str/split-lines s))

(defn- line-widths
  "Return the set of line widths, ignoring a trailing empty line."
  [s]
  (let [ls (lines s)]
    (set (map count (if (= "" (last ls)) (butlast ls) ls)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Basic Dispatch
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest infer-columns-test
  (testing "[:table] infers columns from the first row"
    (let [result (render [:table] staff)]
      (is (str/includes? result "Name"))
      (is (str/includes? result "Age"))
      (is (str/includes? result "Role"))
      (is (str/includes? result "Alice")))))

(deftest bare-keyword-columns-test
  (testing "[:table :name :age] with bare keywords"
    (let [result (render [:table :name :age] staff)]
      (is (str/includes? result "Alice"))
      (is (str/includes? result "30"))
      (is (not (str/includes? result "Admin"))))))

(deftest col-form-test
  (testing "[:col :name] is equivalent to :name"
    (let [via-bare (render [:table :name :age] staff)
          via-col  (render [:table [:col :name] [:col :age]] staff)]
      (is (= via-bare via-col)))))

(deftest col-with-opts-test
  (testing "[:col :name {:width 15}] column with options"
    (let [result (render [:table
                          [:col :name {:width 15}]
                          [:col :age  {:width 8 :align :right}]]
                         staff)]
      (is (str/includes? result "Alice"))
      (is (str/includes? result "|"))
      (let [ws (line-widths result)]
        (is (= 1 (count ws)) "all lines same width")))))

(deftest table-options-test
  (testing "Table opts are the second element"
    (let [result (render [:table {:style :unicode :header-case :upcase}
                          :name :age]
                         staff)]
      (is (str/includes? result "┌"))
      (is (str/includes? result "│"))
      (is (str/includes? result "NAME")))))

(deftest raw-map-columns-still-work-test
  (testing "Raw column maps are still accepted as children (after an opts map)"
    ;; Note: the first map in position 1 is table opts, so we use
    ;; an empty opts map before the raw column maps.
    (let [result (render [:table {}
                          {:key :name :width 15}
                          {:key :age  :width 5}]
                         staff)]
      (is (str/includes? result "Alice")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Writer Semantics
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest returns-string-test
  (let [result (fmt/clj-format nil [:table :name] [{:name "A"}])]
    (is (string? result))
    (is (str/includes? result "Name"))))

(deftest returns-string-for-false-test
  (let [result (fmt/clj-format false [:table :name] [{:name "A"}])]
    (is (string? result))))

(deftest prints-for-true-test
  (let [output (with-out-str
                 (fmt/clj-format true [:table :name] [{:name "A"}]))]
    (is (str/includes? output "Name"))
    (is (str/includes? output "A"))))

(deftest writes-to-writer-test
  #?(:clj  (let [sw (java.io.StringWriter.)]
             (fmt/clj-format sw [:table :name] [{:name "A"}])
             (is (str/includes? (str sw) "Name"))
             (is (str/includes? (str sw) "A")))
     :cljs nil))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Border Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest ascii-style-test
  (let [result (render [:table :name] [{:name "A"}])]
    (is (str/includes? result "+"))
    (is (str/includes? result "|"))
    (is (str/includes? result "-"))))

(deftest unicode-style-test
  (let [result (render [:table {:style :unicode} :name] [{:name "A"}])]
    (is (str/includes? result "┌"))
    (is (str/includes? result "│"))
    (is (str/includes? result "└"))))

(deftest rounded-style-test
  (let [result (render [:table {:style :rounded} :name] [{:name "A"}])]
    (is (str/includes? result "╭"))
    (is (str/includes? result "╰"))))

(deftest heavy-style-test
  (let [result (render [:table {:style :heavy} :name] [{:name "A"}])]
    (is (str/includes? result "┏"))
    (is (str/includes? result "┃"))
    (is (str/includes? result "┗"))))

(deftest double-style-test
  (let [result (render [:table {:style :double} :name] [{:name "A"}])]
    (is (str/includes? result "╔"))
    (is (str/includes? result "║"))
    (is (str/includes? result "╚"))))

(deftest org-style-test
  (let [result (render [:table {:style :org} :name] [{:name "A"}])]
    (is (str/includes? result "|---"))
    (is (not (str/includes? result "+"))
        "org uses | on the outside, not +")))

(deftest markdown-style-test
  (let [result (render [:table {:style :markdown}
                        [:col :name {:width 8}]
                        [:col :score {:width 8 :align :right}]]
                       [{:name "Alice" :score 95}])]
    (is (str/includes? result "| :------"))
    (is (str/includes? result "------: |"))))

(deftest simple-style-test
  (let [result (render [:table {:style :simple} :name :age]
                       [{:name "A" :age 1}])]
    (is (not (str/includes? result "|")))
    (is (str/includes? result "----"))))

(deftest none-style-test
  (let [result (render [:table {:style :none} :name :age]
                       [{:name "A" :age 1}])]
    (is (not (str/includes? result "|")))
    (is (not (str/includes? result "---")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Alignment
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest left-align-test
  (let [result (render [:table [:col :name {:width 10 :align :left}]]
                       [{:name "foo"}])]
    (is (str/includes? result "| foo        |"))))

(deftest right-align-test
  (let [result (render [:table [:col :name {:width 10 :align :right}]]
                       [{:name "foo"}])]
    (is (str/includes? result "|        foo |"))))

(deftest center-align-test
  (let [result (render [:table [:col :name {:width 10 :align :center}]]
                       [{:name "foo"}])]
    (is (str/includes? result "foo"))))

(deftest title-align-test
  (testing "Header alignment is independent of data alignment"
    (let [result (render [:table [:col :name {:width 12 :align :left
                                               :title-align :center}]]
                         [{:name "Alice"}])]
      (is (str/includes? result "Alice")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Typed Formats (DSL Showcase)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest int-format-test
  (let [result (render [:table [:col :n {:width 10 :align :right :format :int}]]
                       [{:n 42}])]
    (is (str/includes? result "42"))))

(deftest grouped-int-format-test
  (let [result (render [:table [:col :n {:width 12 :align :right
                                          :format [:int {:group true}]}]]
                       [{:n 1000000}])]
    (is (str/includes? result "1,000,000"))))

(deftest money-format-test
  (let [result (render [:table [:col :price {:width 10 :align :right :format :money}]]
                       [{:price 9.99}])]
    (is (str/includes? result "9.99"))))

(deftest signed-money-test
  (let [result (render [:table [:col :bal {:width 12 :align :right
                                            :format [:money {:sign :always}]}]]
                       [{:bal 50.0} {:bal -12.3}])]
    (is (str/includes? result "+50.00"))
    (is (str/includes? result "-12.30"))))

(deftest roman-format-test
  (let [result (render [:table [:col :rank {:width 8 :align :center :format :roman}]]
                       [{:rank 1} {:rank 2} {:rank 3}])]
    (is (str/includes? result "I"))
    (is (str/includes? result "II"))
    (is (str/includes? result "III"))))

(deftest if-format-test
  (testing "Boolean dispatch with [:if ...]"
    (let [result (render [:table [:col :active {:width 10 :align :center
                                                  :format [:if "Yes" "No"]}]]
                         [{:active true} {:active false}])]
      (is (str/includes? result "Yes"))
      (is (str/includes? result "No")))))

(deftest hex-format-test
  (let [result (render [:table [:col :code {:width 8 :align :center
                                              :format [:hex {:width 4 :fill \0}]}]]
                       [{:code 255} {:code 16}])]
    (is (str/includes? result "00ff"))
    (is (str/includes? result "0010"))))

(deftest function-format-test
  (testing "Custom function format"
    (let [result (render [:table
                          [:col :name {:width 10}]
                          [:col :status {:width 10
                                         :format (fn [v] (if v "Active" "Inactive"))}]]
                         [{:name "Alice" :status true}
                          {:name "Bob"   :status false}])]
      (is (str/includes? result "Active"))
      (is (str/includes? result "Inactive")))))

(deftest computed-column-test
  (testing "Computed column via a fn :key"
    (let [result (render [:table
                          [:col (fn [row] (str (:first row) " " (:last row)))
                           {:title "Full Name" :width 15}]]
                         [{:first "Alice" :last "Smith"}])]
      (is (str/includes? result "Alice Smith"))
      (is (str/includes? result "Full Name")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Elision
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest ellipsis-test
  (testing "Long strings are truncated with ..."
    (let [result (render [:table [:col :title {:width 15 :overflow :ellipsis}]]
                         [{:title "A short title"}
                          {:title "A very long title that exceeds the width"}])]
      (is (str/includes? result "A short title"))
      (is (str/includes? result "..."))
      (is (not (str/includes? result "exceeds the width"))))))

(deftest custom-ellipsis-test
  (let [result (render [:table [:col :s {:width 10 :overflow :ellipsis :ellipsis "~"}]]
                       [{:s "Hello World!"}])]
    (is (str/includes? result "Hello Wor~"))))

(deftest clip-test
  (let [result (render [:table [:col :s {:width 5 :overflow :clip}]]
                       [{:s "Hello World"}])]
    (is (str/includes? result "Hello"))
    (is (not (str/includes? result "...")))))

(deftest no-elision-when-fits-test
  (let [result (render [:table [:col :s {:width 15}]] [{:s "Short"}])]
    (is (str/includes? result "Short"))
    (is (not (str/includes? result "...")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Word Wrapping
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest wrap-basic-test
  (testing "Long text wraps into multiple physical rows"
    (let [result (render [:table
                          [:col :desc {:width 20 :overflow :wrap}]]
                         [{:desc "The quick brown fox jumps over the lazy dog"}])
          row-lines (filter #(str/starts-with? % "| ") (lines result))]
      (is (>= (count row-lines) 3) "wraps to at least 3 lines")
      (is (str/includes? result "quick"))
      (is (str/includes? result "lazy")))))

(deftest wrap-respects-width-test
  (testing "Every wrapped row fits within its column width"
    (let [result (render [:table
                          [:col :desc {:width 15 :overflow :wrap}]]
                         [{:desc "Lorem ipsum dolor sit amet consectetur adipiscing"}])]
      (is (every? #(or (str/starts-with? % "+")
                       (<= (count %) 19)) ; 15 + 4 for "| " and " |"
                  (lines result))))))

(deftest wrap-preserves-other-columns-test
  (testing "Wrap-column expansion keeps other columns empty on continuation rows"
    (let [result (render [:table {:header false}
                          [:col :name {:width 10}]
                          [:col :desc {:width 15 :overflow :wrap}]]
                         [{:name "Alice" :desc "A very long description that spans lines"}])
          data-lines (filter #(str/starts-with? % "| ") (lines result))]
      ;; First data line has "Alice"
      (is (str/includes? (first data-lines) "Alice"))
      ;; Continuation lines do NOT have "Alice"
      (is (every? #(not (str/includes? % "Alice")) (rest data-lines))))))

(deftest wrap-multiple-rows-test
  (testing "Each logical row wraps independently"
    (let [result (render [:table
                          [:col :name {:width 8}]
                          [:col :desc {:width 20 :overflow :wrap}]]
                         [{:name "Alice" :desc "A senior engineer"}
                          {:name "Bob"   :desc "A quiet contributor"}])
          data-lines (filter #(str/starts-with? % "| ") (lines result))]
      (is (some #(str/includes? % "Alice") data-lines))
      (is (some #(str/includes? % "Bob") data-lines))
      (is (some #(str/includes? % "senior") data-lines))
      (is (some #(str/includes? % "quiet") data-lines)))))

(deftest wrap-with-typed-columns-test
  (testing "Wrap mode coexists with typed columns (preprocessed)"
    (let [result (render [:table {:style :unicode}
                          [:col :name {:width 10}]
                          [:col :desc {:width 20 :overflow :wrap}]
                          [:col :price {:width 10 :align :right :format :money}]]
                         [{:name "Widget" :desc "Premium widget with warranty" :price 49.99}])]
      (is (str/includes? result "49.99"))
      (is (str/includes? result "Premium"))
      (is (str/includes? result "warranty")))))

(deftest wrap-long-word-test
  (testing "Words longer than the column width get broken"
    (let [result (render [:table
                          [:col :s {:width 8 :overflow :wrap}]]
                         [{:s "Supercalifragilisticexpialidocious"}])]
      ;; We don't check exact break points, just that the word appears somewhere
      (is (str/includes? result "Super")))))

(deftest wrap-consistent-widths-test
  (testing "All lines in a wrapped table are the same width"
    (let [result (render [:table
                          [:col :name {:width 10}]
                          [:col :desc {:width 15 :overflow :wrap}]]
                         [{:name "A" :desc "word word word word word"}
                          {:name "B" :desc "short"}])]
      (is (= 1 (count (line-widths result)))))))

(deftest wrap-preserves-preformatted-whitespace-test
  (testing "Lines that fit are preserved verbatim — interior whitespace is NOT collapsed"
    ;; Pre-formatted content: each line has meaningful multi-space structure
    (let [art "a   b   c\n  d    e\nf       g"
          result (render [:table {:header false}
                          [:col :v {:width 20 :overflow :wrap
                                    :format (fn [s] s)}]]
                         [{:v art}])]
      (is (str/includes? result "a   b   c")
          "multi-space interior preserved")
      (is (str/includes? result "  d    e")
          "leading and internal whitespace preserved")
      (is (str/includes? result "f       g")
          "very wide whitespace preserved"))))

(deftest wrap-embedded-multiline-test
  (testing "Multi-line cell content is expanded line-by-line preserving structure"
    (let [box "┌───┐\n│ A │\n└───┘"
          result (render [:table {:header false}
                          [:col :v {:width 10 :overflow :wrap
                                    :format (fn [s] s)}]]
                         [{:v box}])]
      (is (str/includes? result "┌───┐"))
      (is (str/includes? result "│ A │"))
      (is (str/includes? result "└───┘")))))

(deftest wrap-nested-table-test
  (testing "A rendered table string can live inside another table cell"
    (let [inner-fn (fn [rows]
                     (fmt/clj-format nil
                                     [:table {:style :ascii :header false}
                                      [:col :k {:width 4}]
                                      [:col :v {:width 4 :align :right}]]
                                     rows))
          result (render [:table {:style :unicode}
                          [:col :group {:width 8}]
                          [:col :stats {:width 20 :overflow :wrap :format inner-fn}]]
                         [{:group "A" :stats [{:k "x" :v 1} {:k "y" :v 2}]}])]
      ;; Outer unicode borders
      (is (str/includes? result "┌"))
      ;; Inner ASCII borders survive inside the cell
      (is (str/includes? result "+------+"))
      ;; Inner data is visible
      (is (str/includes? result "x"))
      (is (str/includes? result "y")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Header Options
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest no-header-test
  (let [result (render [:table {:header false} :name] [{:name "Alice"}])]
    (is (not (str/includes? result "Name")))
    (is (str/includes? result "Alice"))))

(deftest header-case-upcase-test
  (let [result (render [:table {:header-case :upcase} :name] [{:name "x"}])]
    (is (str/includes? result "NAME"))))

(deftest header-case-nil-test
  (let [result (render [:table {:header-case nil}
                        [:col :name {:title "myTitle"}]]
                       [{:name "x"}])]
    (is (str/includes? result "myTitle"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Row Rules
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest row-rules-test
  (let [result (render [:table {:row-rules true} :name] staff)
        rule-lines (filter #(str/starts-with? % "+") (lines result))]
    (is (>= (count rule-lines) 5))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Footer with Aggregation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest footer-sum-test
  (let [result (render [:table {:footer {:label "Total" :fns {:qty :sum}}}
                        [:col :item {:width 10}]
                        [:col :qty  {:width 8 :align :right :format :int}]]
                       [{:item "A" :qty 10} {:item "B" :qty 20} {:item "C" :qty 30}])]
    (is (str/includes? result "Total"))
    (is (str/includes? result "60"))))

(deftest footer-avg-test
  (let [result (render [:table {:footer {:label "Avg" :fns {:score :avg}}}
                        [:col :name  {:width 8}]
                        [:col :score {:width 8 :align :right :format :int}]]
                       [{:name "A" :score 80} {:name "B" :score 100}])]
    (is (str/includes? result "Avg"))
    (is (str/includes? result "90"))))

(deftest footer-count-test
  (let [result (render [:table {:footer {:label "Count" :fns {:n :count}}}
                        [:col :name {:width 8}]
                        [:col :n    {:width 8 :align :right :format :int}]]
                       [{:name "A" :n 1} {:name "B" :n 2} {:name "C" :n 3}])]
    (is (str/includes? result "Count"))
    (is (str/includes? result "3"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Nil Handling
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest nil-default-empty-test
  (let [result (render [:table [:col :x {:width 5}]]
                       [{:x "a"} {:x nil}])]
    (is (str/includes? result "a"))))

(deftest nil-custom-value-test
  (let [result (render [:table {:nil-value "N/A"} [:col :x {:width 5}]]
                       [{:x nil}])]
    (is (str/includes? result "N/A"))))

(deftest nil-in-typed-column-test
  (testing "Nil in typed column falls back to preprocessed mode"
    (let [result (render [:table {:nil-value "-"}
                          [:col :n {:width 8 :format :int}]]
                         [{:n 42} {:n nil}])]
      (is (str/includes? result "42"))
      (is (str/includes? result "-")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Edge Cases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest empty-data-with-columns-test
  (testing "Empty data with explicit columns renders header and rules"
    (let [result (render [:table [:col :name {:width 10}]] [])]
      (is (str/includes? result "Name"))
      (is (str/includes? result "+")))))

(deftest single-row-test
  (let [result (render [:table :x] [{:x "v"}])]
    (is (str/includes? result "v"))))

(deftest single-column-test
  (let [result (render [:table :x] [{:x "a"} {:x "b"}])]
    (is (str/includes? result "a"))
    (is (str/includes? result "b"))))

(deftest consistent-line-widths-test
  (testing "All lines in a bordered table have the same width"
    (let [result (render [:table :name :age] staff)]
      (is (= 1 (count (line-widths result)))))))

(deftest auto-width-respects-data-test
  (let [result (render [:table :name]
                       [{:name "short"} {:name "a longer value"}])]
    (is (str/includes? result "a longer value"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; table-dsl
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest table-dsl-returns-map-test
  (let [result (fmt/table-dsl [:table :name] [{:name "Alice"}])]
    (is (map? result))
    (is (vector? (:dsl result)))
    (is (vector? (:args result)))))

(deftest table-dsl-renderable-test
  (testing "table-dsl output feeds cleanly into clj-format with the raw format string"
    (let [{:keys [dsl args]} (fmt/table-dsl [:table :name :age] staff)
          rendered (apply fmt/clj-format nil dsl args)]
      (is (string? rendered))
      (is (str/includes? rendered "Alice"))
      (is (str/includes? rendered "30")))))

(deftest table-dsl-matches-render-test
  (testing "table-dsl + clj-format equals direct [:table ...] render"
    (let [spec    [:table {:style :unicode :header-case :upcase} :name :age]
          direct  (render spec staff)
          {:keys [dsl args]} (fmt/table-dsl spec staff)
          via-dsl (apply fmt/clj-format nil dsl args)]
      (is (= direct via-dsl)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rejection of Invalid Specs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest invalid-column-spec-test
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core.ExceptionInfo)
               (render [:table 42] [{:x 1}]))))

(deftest humanize-key-test
  (testing "Keyword keys are humanized for titles"
    (let [result (render [:table] [{:first-name "Alice" :last-name "Smith"}])]
      (is (str/includes? result "First Name"))
      (is (str/includes? result "Last Name")))))

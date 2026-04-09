(ns clj-format.table-test
  "Tests for the clj-format.table tabular formatting facility."
  (:require [clj-format.table :as table]
            [clojure.string   :as str]
            [#?(:clj clojure.test :cljs cljs.test) :refer [deftest is testing]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private sample-data
  [{:name "Alice" :age 30 :role "Admin"}
   {:name "Bob"   :age 25 :role "User"}
   {:name "Carol" :age 35 :role "Editor"}])

(def ^:private product-data
  [{:item "Widget"   :qty 1200  :price 9.99}
   {:item "Gadget"   :qty 42    :price 24.50}
   {:item "Sprocket" :qty 85000 :price 3.75}])

(defn- lines
  "Split a rendered table into lines."
  [s]
  (str/split-lines s))

(defn- line-count [s] (count (lines s)))

(defn- line-widths
  "Return the set of line widths, ignoring trailing empty line."
  [s]
  (let [ls (lines s)]
    (set (map count (if (= "" (last ls)) (butlast ls) ls)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Smoke Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest basic-table-test
  (testing "Simple table from seq of maps"
    (let [result (table/format-table sample-data)]
      (is (string? result))
      (is (str/includes? result "Alice"))
      (is (str/includes? result "Bob"))
      (is (str/includes? result "Carol")))))

(deftest explicit-columns-test
  (testing "Explicit column selection"
    (let [result (table/format-table [:name :age] sample-data)]
      (is (str/includes? result "Alice"))
      (is (str/includes? result "30"))
      (is (not (str/includes? result "Admin"))))))

(deftest column-spec-maps-test
  (testing "Column spec maps with options"
    (let [result (table/format-table
                   [{:key :name :width 15 :title "Full Name"}
                    {:key :age  :width 8 :align :right}]
                   sample-data)]
      (is (str/includes? result "Full Name"))
      (is (str/includes? result "Alice")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Border Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest ascii-style-test
  (let [result (table/format-table [:name] [{:name "A"}] {:style :ascii})]
    (is (str/includes? result "+------+"))
    (is (str/includes? result "| Name |"))
    (is (str/includes? result "| A    |"))))

(deftest unicode-style-test
  (let [result (table/format-table [:name] [{:name "A"}] {:style :unicode})]
    (is (str/includes? result "┌"))
    (is (str/includes? result "│"))
    (is (str/includes? result "└"))))

(deftest rounded-style-test
  (let [result (table/format-table [:name] [{:name "A"}] {:style :rounded})]
    (is (str/includes? result "╭"))
    (is (str/includes? result "╰"))))

(deftest heavy-style-test
  (let [result (table/format-table [:name] [{:name "A"}] {:style :heavy})]
    (is (str/includes? result "┏"))
    (is (str/includes? result "┃"))
    (is (str/includes? result "┗"))))

(deftest double-style-test
  (let [result (table/format-table [:name] [{:name "A"}] {:style :double})]
    (is (str/includes? result "╔"))
    (is (str/includes? result "║"))
    (is (str/includes? result "╚"))))

(deftest org-style-test
  (let [result (table/format-table [:name] [{:name "A"}] {:style :org})]
    (is (str/includes? result "|---"))
    (is (str/includes? result "| Name |"))
    (is (not (str/includes? result "+")))))

(deftest markdown-style-test
  (let [result (table/format-table
                 [{:key :name :width 8} {:key :score :width 8 :align :right}]
                 [{:name "Alice" :score 95}]
                 {:style :markdown})]
    (is (str/includes? result "| :------"))
    (is (str/includes? result "------: |"))
    (is (not (str/starts-with? result "+")))))

(deftest simple-style-test
  (let [result (table/format-table [:name :age] [{:name "A" :age 1}]
                                   {:style :simple})]
    (is (not (str/includes? result "|")))
    (is (str/includes? result "----"))
    (is (str/includes? result "  "))))

(deftest none-style-test
  (let [result (table/format-table [:name :age] [{:name "A" :age 1}]
                                   {:style :none})]
    (is (not (str/includes? result "|")))
    (is (not (str/includes? result "---")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Alignment
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest left-align-test
  (let [result (table/format-table
                 [{:key :name :width 10 :align :left}]
                 [{:name "foo"}])]
    (is (str/includes? result "| foo        |"))))

(deftest right-align-test
  (let [result (table/format-table
                 [{:key :name :width 10 :align :right}]
                 [{:name "foo"}])]
    (is (str/includes? result "|        foo |"))))

(deftest center-align-test
  (let [result (table/format-table
                 [{:key :name :width 10 :align :center}]
                 [{:name "foo"}])]
    (is (str/includes? result "foo"))))

(deftest title-align-test
  (testing "Header alignment independent of data alignment"
    (let [result (table/format-table
                   [{:key :name :width 12 :align :left
                     :title-align :center :title "Name"}]
                   [{:name "Alice"}])]
      (is (str/includes? result "Alice")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Typed Formats (DSL Showcase)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest int-format-test
  (let [result (table/format-table
                 [{:key :n :width 10 :align :right :format :int}]
                 [{:n 42}])]
    (is (str/includes? result "42"))))

(deftest grouped-int-format-test
  (let [result (table/format-table
                 [{:key :n :width 12 :align :right
                   :format [:int {:group true}]}]
                 [{:n 1000000}])]
    (is (str/includes? result "1,000,000"))))

(deftest money-format-test
  (let [result (table/format-table
                 [{:key :price :width 10 :align :right :format :money}]
                 [{:price 9.99}])]
    (is (str/includes? result "9.99"))))

(deftest signed-money-test
  (let [result (table/format-table
                 [{:key :bal :width 12 :align :right
                   :format [:money {:sign :always}]}]
                 [{:bal 50.0} {:bal -12.3}])]
    (is (str/includes? result "+50.00"))
    (is (str/includes? result "-12.30"))))

(deftest roman-format-test
  (let [result (table/format-table
                 [{:key :rank :width 8 :align :center :format :roman}]
                 [{:rank 1} {:rank 2} {:rank 3}])]
    (is (str/includes? result "I"))
    (is (str/includes? result "II"))
    (is (str/includes? result "III"))))

(deftest if-format-test
  (testing "Boolean dispatch with [:if ...]"
    (let [result (table/format-table
                   [{:key :active :width 10 :align :center
                     :format [:if "Yes" "No"]}]
                   [{:active true} {:active false}])]
      (is (str/includes? result "Yes"))
      (is (str/includes? result "No")))))

(deftest hex-format-test
  (let [result (table/format-table
                 [{:key :code :width 8 :align :center
                   :format [:hex {:width 4 :fill \0}]}]
                 [{:code 255} {:code 16}])]
    (is (str/includes? result "00ff"))
    (is (str/includes? result "0010"))))

(deftest function-format-test
  (testing "Custom function format"
    (let [result (table/format-table
                   [{:key :name :width 10}
                    {:key :status :width 10
                     :format (fn [v] (if v "Active" "Inactive"))}]
                   [{:name "Alice" :status true}
                    {:name "Bob"   :status false}])]
      (is (str/includes? result "Active"))
      (is (str/includes? result "Inactive")))))

(deftest computed-column-test
  (testing "Computed column via :key function"
    (let [result (table/format-table
                   [{:key (fn [row] (str (:first row) " " (:last row)))
                     :title "Full Name" :width 15}]
                   [{:first "Alice" :last "Smith"}])]
      (is (str/includes? result "Alice Smith"))
      (is (str/includes? result "Full Name")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Elision
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest ellipsis-test
  (testing "Long strings are truncated with ..."
    (let [result (table/format-table
                   [{:key :title :width 15 :overflow :ellipsis}]
                   [{:title "A short title"}
                    {:title "A very long title that exceeds the width"}])]
      (is (str/includes? result "A short title"))
      (is (str/includes? result "..."))
      (is (not (str/includes? result "exceeds the width"))))))

(deftest custom-ellipsis-test
  (testing "Custom ellipsis string"
    (let [result (table/format-table
                   [{:key :s :width 10 :overflow :ellipsis :ellipsis "~"}]
                   [{:s "Hello World!"}])]
      (is (str/includes? result "Hello Wor~")))))

(deftest clip-test
  (testing "Clip truncation without ellipsis"
    (let [result (table/format-table
                   [{:key :s :width 5 :overflow :clip}]
                   [{:s "Hello World"}])]
      (is (str/includes? result "Hello"))
      (is (not (str/includes? result "..."))))))

(deftest no-elision-when-fits-test
  (testing "No truncation when text fits"
    (let [result (table/format-table
                   [{:key :s :width 15}]
                   [{:s "Short"}])]
      (is (str/includes? result "Short"))
      (is (not (str/includes? result "..."))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Header Options
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest no-header-test
  (let [result (table/format-table [:name] [{:name "Alice"}]
                                   {:header false})]
    (is (not (str/includes? result "Name")))
    (is (str/includes? result "Alice"))))

(deftest header-case-upcase-test
  (let [result (table/format-table [:name] [{:name "x"}]
                                   {:header-case :upcase})]
    (is (str/includes? result "NAME"))))

(deftest header-case-nil-test
  (testing "No case conversion when header-case is nil"
    (let [result (table/format-table
                   [{:key :name :title "myTitle"}]
                   [{:name "x"}]
                   {:header-case nil})]
      (is (str/includes? result "myTitle")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Row Rules
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest row-rules-test
  (let [result (table/format-table [:name] sample-data {:row-rules true})
        ls     (lines result)]
    (testing "Has rules between every data row"
      ;; header-rule + 2 inter-row rules = 3 rule lines inside the table
      (let [rule-lines (filter #(str/starts-with? % "+") ls)]
        (is (>= (count rule-lines) 5))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Footer
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest footer-sum-test
  (let [result (table/format-table
                 [{:key :item :width 10}
                  {:key :qty :width 8 :align :right :format :int}]
                 [{:item "A" :qty 10} {:item "B" :qty 20} {:item "C" :qty 30}]
                 {:footer {:label "Total" :fns {:qty :sum}}})]
    (is (str/includes? result "Total"))
    (is (str/includes? result "60"))))

(deftest footer-avg-test
  (let [result (table/format-table
                 [{:key :name :width 8}
                  {:key :score :width 8 :align :right :format :int}]
                 [{:name "A" :score 80} {:name "B" :score 100}]
                 {:footer {:label "Avg" :fns {:score :avg}}})]
    (is (str/includes? result "Avg"))
    (is (str/includes? result "90"))))

(deftest footer-count-test
  (let [result (table/format-table
                 [{:key :name :width 8}
                  {:key :n :width 8 :align :right :format :int}]
                 [{:name "A" :n 1} {:name "B" :n 2} {:name "C" :n 3}]
                 {:footer {:label "Count" :fns {:n :count}}})]
    (is (str/includes? result "Count"))
    (is (str/includes? result "3"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Nil Handling
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest nil-default-empty-test
  (testing "Nil renders as empty by default"
    (let [result (table/format-table
                   [{:key :x :width 5}]
                   [{:x "a"} {:x nil}])]
      (is (str/includes? result "a")))))

(deftest nil-custom-value-test
  (testing "Nil renders as custom nil-value"
    (let [result (table/format-table
                   [{:key :x :width 5}]
                   [{:x nil}]
                   {:nil-value "N/A"})]
      (is (str/includes? result "N/A")))))

(deftest nil-in-typed-column-test
  (testing "Nil in typed column falls back to preprocessed mode"
    (let [result (table/format-table
                   [{:key :n :width 8 :format :int}]
                   [{:n 42} {:n nil}]
                   {:nil-value "-"})]
      (is (str/includes? result "42"))
      (is (str/includes? result "-")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Edge Cases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest empty-data-test
  (testing "Table with no rows renders header and rules"
    (let [result (table/format-table
                   [{:key :name :width 10}]
                   [])]
      (is (str/includes? result "Name"))
      (is (str/includes? result "+")))))

(deftest single-row-test
  (let [result (table/format-table [:x] [{:x "v"}])]
    (is (str/includes? result "v"))))

(deftest single-column-test
  (let [result (table/format-table [:x] [{:x "a"} {:x "b"}])]
    (is (str/includes? result "a"))
    (is (str/includes? result "b"))))

(deftest consistent-line-widths-test
  (testing "All lines in a bordered table have the same width"
    (let [result (table/format-table [:name :age] sample-data)
          ws     (line-widths result)]
      (is (= 1 (count ws))))))

(deftest auto-width-respects-data-test
  (testing "Auto-sized columns accommodate the widest value"
    (let [result (table/format-table [:name]
                   [{:name "short"} {:name "a longer value"}])]
      (is (str/includes? result "a longer value")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; table-dsl
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest table-dsl-returns-map-test
  (let [result (table/table-dsl [:name] [{:name "Alice"}])]
    (is (map? result))
    (is (vector? (:dsl result)))
    (is (vector? (:args result)))))

(deftest table-dsl-renderable-test
  (testing "The returned DSL + args produce valid output via clj-format"
    (let [{:keys [dsl args]} (table/table-dsl [:name :age] sample-data)
          rendered (apply clj-format.core/clj-format nil dsl args)]
      (is (string? rendered))
      (is (str/includes? rendered "Alice"))
      (is (str/includes? rendered "30")))))

(deftest table-dsl-matches-format-table-test
  (testing "table-dsl + clj-format produces same output as format-table"
    (let [opts     {:style :unicode :header-case :upcase}
          direct   (table/format-table [:name :age] sample-data opts)
          {:keys [dsl args]} (table/table-dsl [:name :age] sample-data opts)
          via-dsl  (apply clj-format.core/clj-format nil dsl args)]
      (is (= direct via-dsl)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Column Inference
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest infer-from-maps-test
  (testing "Columns inferred from map keys"
    (let [result (table/format-table [{:a 1 :b 2}])]
      (is (string? result)))))

(deftest humanize-key-test
  (testing "Keyword keys are humanized for titles"
    (let [result (table/format-table [{:first-name "Alice" :last-name "Smith"}])]
      (is (str/includes? result "First Name"))
      (is (str/includes? result "Last Name")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defaults
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest defaults-option-test
  (testing ":defaults applies to all columns"
    (let [result (table/format-table [:name :role] sample-data
                                     {:defaults {:align :right}})]
      ;; Both columns should be right-aligned; check data appears
      (is (str/includes? result "Alice"))
      (is (str/includes? result "Admin")))))

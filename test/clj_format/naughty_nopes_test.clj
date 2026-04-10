(ns clj-format.naughty-nopes-test
  "Smoke-tests clj-format against the Big List of Naughty Strings (BLNS).

  Downloads the BLNS JSON from GitHub at test time — nothing is written
  to disk. All tests skip gracefully when the network is unavailable.

  What we verify:
  - Every BLNS string survives as a ~A argument (no crash)
  - Every BLNS string round-trips through compile-format → cl-format
  - Strings containing ~ either parse or throw structured ExceptionInfo
  - Every BLNS string works as a table cell value (no crash)
  - Tilde escaping doubles every ~ precisely"
  (:require [clojure.test   :refer [deftest is testing]]
            [clojure.string :as str]
            [clj-http.client :as http]
            [clj-format.core :as fmt]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; BLNS Fetcher
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def ^:private +blns-url+
  "https://raw.githubusercontent.com/minimaxir/big-list-of-naughty-strings/master/blns.json")

(defn- fetch-blns
  "Download and parse the BLNS JSON. Returns a vector of non-empty strings,
   or nil on failure."
  []
  (try
    (let [body (:body (http/get +blns-url+
                                {:as :json
                                 :socket-timeout 15000
                                 :connection-timeout 10000}))]
      (filterv #(and (string? %) (seq %)) body))
    (catch Throwable e
      (println "  BLNS download failed — skipping naughty strings tests:"
               (.getMessage e))
      nil)))

(def ^:private blns
  "Lazily-fetched BLNS corpus. nil when network is unavailable."
  (delay (fetch-blns)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Aesthetic Safety — every string through ~A
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest aesthetic-safety-test
  (testing "Every BLNS string survives (clj-format nil :str s)"
    (if-let [strings @blns]
      (let [failures (atom 0)]
        (doseq [s strings]
          (try
            (let [result (fmt/clj-format nil :str s)]
              (when-not (string? result)
                (swap! failures inc)
                (is false (str "non-string result for: " (pr-str (subs s 0 (min 40 (count s))))))))
            (catch Exception e
              (swap! failures inc)
              (is false (str "crashed on: " (pr-str (subs s 0 (min 40 (count s))))
                             " — " (.getMessage e))))))
        (is (zero? @failures)
            (str @failures " of " (count strings) " BLNS strings caused failures")))
      (println "  (skipped — BLNS unavailable)"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Literal Round-Trip — compile as text, then cl-format back
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest literal-round-trip-test
  (testing "BLNS strings round-trip: (cl-format nil (compile-format [s])) = s"
    (if-let [strings @blns]
      (let [failures (atom 0)]
        (doseq [s strings]
          (try
            (let [compiled (fmt/compile-format [s])
                  result   (fmt/clj-format nil compiled)]
              (when-not (= s result)
                (swap! failures inc)
                (is (= s result)
                    (str "round-trip mismatch for: " (pr-str (subs s 0 (min 40 (count s))))))))
            (catch Exception e
              (swap! failures inc)
              (is false (str "round-trip crashed on: " (pr-str (subs s 0 (min 40 (count s))))
                             " — " (.getMessage e))))))
        (is (zero? @failures)
            (str @failures " of " (count strings) " BLNS strings failed round-trip")))
      (println "  (skipped — BLNS unavailable)"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tilde Escaping Precision
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest tilde-escaping-test
  (testing "Compiler doubles every tilde in literal text precisely"
    (if-let [strings @blns]
      (let [tilde-strings (filter #(str/includes? % "~") strings)]
        (doseq [s tilde-strings]
          (let [compiled   (fmt/compile-format [s])
                orig-count (count (filter #(= % \~) s))
                comp-count (count (filter #(= % \~) compiled))]
            (is (= comp-count (* 2 orig-count))
                (str "tilde count mismatch — expected " (* 2 orig-count)
                     " got " comp-count
                     " for: " (pr-str (subs s 0 (min 40 (count s)))))))))
      (println "  (skipped — BLNS unavailable)"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parser Resilience — tilde-bearing strings
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest parser-resilience-test
  (testing "Parser either succeeds or throws ExceptionInfo on strings with ~"
    (if-let [strings @blns]
      (let [tilde-strings (filter #(str/includes? % "~") strings)
            raw-crashes   (atom 0)]
        (doseq [s tilde-strings]
          (try
            (let [parsed (fmt/parse-format s)]
              (is (vector? parsed)))
            (catch clojure.lang.ExceptionInfo _
              ;; Structured parse error — expected and acceptable
              nil)
            (catch Throwable e
              ;; Unstructured crash — this is a bug
              (swap! raw-crashes inc)
              (is false
                  (str "unstructured " (.getName ^Class (type e)) " parsing: "
                       (pr-str (subs s 0 (min 60 (count s))))
                       " — " (.getMessage e))))))
        (is (zero? @raw-crashes)
            (str @raw-crashes " BLNS tilde-strings caused unstructured exceptions")))
      (println "  (skipped — BLNS unavailable)"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Table Cell Safety
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftest table-cell-safety-test
  (testing "BLNS strings as table cell values — no crash"
    (if-let [strings @blns]
      (let [failures (atom 0)]
        (doseq [s strings]
          (try
            (let [result (fmt/clj-format
                           nil
                           [:table {:header false}
                             [:col :v {:width 30 :overflow :ellipsis}]]
                           [{:v s}])]
              (when-not (string? result)
                (swap! failures inc)
                (is false (str "non-string result for: "
                               (pr-str (subs s 0 (min 40 (count s))))))))
            (catch Exception e
              (swap! failures inc)
              (is false (str "table crashed on: " (pr-str (subs s 0 (min 40 (count s))))
                             " — " (.getMessage e))))))
        (is (zero? @failures)
            (str @failures " of " (count strings) " BLNS strings crashed the table")))
      (println "  (skipped — BLNS unavailable)"))))

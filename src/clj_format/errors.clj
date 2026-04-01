(ns clj-format.errors
  "Helpers for consistent structured exception handling.")

(defn invalid-format-spec
  "Build an exception for an invalid public `fmt` argument."
  [fmt]
  (ex-info "Format spec must be a string, vector, or keyword"
           {:library :clj-format
            :phase :api
            :kind :invalid-format-spec
            :fmt fmt}))

(defn parse-error
  "Build an exception for invalid format-string input."
  [message data]
  (ex-info message
           (merge {:library :clj-format
                   :phase :parse}
                  data)))

(defn compile-error
  "Build an exception for invalid DSL input."
  [message data]
  (ex-info message
           (merge {:library :clj-format
                   :phase :compile}
                  data)))

(ns clj-format.test-support
  (:require [clj-format.compiler :refer [compile-format]]
            [clj-format.parser :refer [parse-format]]
            #?(:clj [clojure.pprint :refer [cl-format]]
               :cljs [cljs.core :as core])
            #?(:cljs [cljs.pprint :refer [cl-format]])
            #?(:cljs [goog.string :as gstring])))

(def exception-info-class
  #?(:clj clojure.lang.ExceptionInfo
     :cljs cljs.core.ExceptionInfo))

(def platform
  #?(:clj :clj
     :cljs :cljs))

(defn equiv-data
  "Return host-output comparison data for a format string."
  [fmt-str & args]
  (let [compiled (compile-format (parse-format fmt-str))
        expected (apply cl-format nil fmt-str args)
        actual   (apply cl-format nil compiled args)]
    {:format fmt-str
     :compiled compiled
     :expected expected
     :actual actual}))

(defn make-writer
  "Create a host writer compatible with cl-format."
  []
  #?(:clj (java.io.StringWriter.)
     :cljs (core/StringBufferWriter. (gstring/StringBuffer.))))

(defn writer-content
  "Extract accumulated content from a host writer."
  [writer]
  #?(:clj (.toString ^java.io.StringWriter writer)
     :cljs (str (.-sb writer))))

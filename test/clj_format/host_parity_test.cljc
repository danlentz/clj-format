(ns clj-format.host-parity-test
  "Focused host-parity audit for directives with a higher risk of CLJ/CLJS
   behavioral drift.

   These tests intentionally pin exact JVM cl-format output as the
   compatibility target for CLJS where the host behavior is well-defined
   in plain string-returning mode."
  (:require [clj-format.core :as fmt]
            [#?(:clj clojure.test :cljs cljs.test) :refer [deftest is testing]]))

(def ^:private pi
  #?(:clj Math/PI
     :cljs js/Math.PI))

(deftest floating-point-parity-test
  (testing "~E formatting"
    (is (= "3.1416E+0" (fmt/clj-format nil "~,4E" pi)))
    (is (= "  3.14E+0" (fmt/clj-format nil "~9,2,1E" 3.14159)))
    (is (= "-3.1416E+0" (fmt/clj-format nil "~,4E" -3.14159265))))
  (testing "~G formatting"
    (is (= "3.142    " (fmt/clj-format nil "~,4G" pi)))
    (is (= "  1.2346E+04" (fmt/clj-format nil "~12,4,2G" 12345.678)))
    (is (= "3.1416E-4" (fmt/clj-format nil "~,4G" 0.000314159265)))
    (is (= " -1.2346E+04" (fmt/clj-format nil "~12,4,2G" -12345.678)))))

(deftest monetary-parity-test
  (is (= "3.14" (fmt/clj-format nil "~$" 3.14159265)))
  (is (= "12.3450000" (fmt/clj-format nil "~7,2$" 12.345)))
  (is (= "+12.35" (fmt/clj-format nil "~:@$" 12.345)))
  (is (= "-12.35" (fmt/clj-format nil "~:@$" -12.345)))
  (is (= "1.01" (fmt/clj-format nil "~$" 1.005))))

(deftest justification-parity-test
  (testing "padding before and after segments"
    (is (= "  foo  bar" (fmt/clj-format nil "~10:<foo~;bar~>")))
    (is (= "  foo bar " (fmt/clj-format nil "~10:@<foo~;bar~>"))))
  (testing "multi-segment distribution"
    (is (= "       foo" (fmt/clj-format nil "~10<foo~>")))
    (is (= "alphabetagamma" (fmt/clj-format nil "~12<alpha~;beta~;gamma~>")))
    (is (= "   a   bbbbbb   cc  "
           (fmt/clj-format nil "~20:@<a~;bbbbbb~;cc~>")))
    (is (= "alpha     beta     gamma"
           (fmt/clj-format nil "~24<alpha~;beta~;gamma~>")))
    (is (= "Name        Count        Price"
           (fmt/clj-format nil "~30<Name~;Count~;Price~>")))))

(deftest layout-and-logical-block-parity-test
  (testing "tabulation"
    (is (= "foo       bar" (fmt/clj-format nil "~A~10T~A" "foo" "bar")))
    (is (= "foo    bar" (fmt/clj-format nil "~A~4@T~A" "foo" "bar"))))
  (testing "logical block wrappers"
    (is (= "rgb(255, 140, 0)"
           (fmt/clj-format nil "~<rgb(~;~D, ~D, ~D~;)~:>" [255 140 0])))
    (is (= "range[10, 20]"
           (fmt/clj-format nil "~<range[~;~D, ~D~;]~:>" [10 20])))))

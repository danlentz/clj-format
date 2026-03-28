(defproject clj-format "0.1.0-SNAPSHOT"
  :description "A Clojure DSL for cl-format — makes the power of Common Lisp FORMAT accessible via s-expressions"
  :url "https://github.com/danlentz/clj-format"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]]
  :repl-options {:init-ns clj-format.core})

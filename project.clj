(defproject com.github.danlentz/clj-format "0.1.0"
  :description "A Clojure DSL for cl-format inspired by Hiccup. No dependencies. Drop-in compatibility. The power of FORMAT made easy."
  :author       "Dan Lentz"
  :url "https://github.com/danlentz/clj-format"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.2"]]
  :global-vars  {*warn-on-reflection* true}
  :repl-options {:init-ns clj-format.core})

(defproject synfnetic/symphonic "0.1.0-SNAPSHOT"
  :description "A library for conducting an orchestra of services either locally(eg: core.async) or remotely(eg: kafka)"
  :url "https://github.com/synfnetic/symphonic"
  :license {:name "MIT"}

  :dependencies [[org.clojure/clojure "1.9.0-alpha10"]
                 [org.clojure/core.async "0.2.385"]
                 [com.taoensso/carmine "2.14.0"]
                 [navis/untangled-spec "0.3.8" :scope "test"]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.16.0"]]

  :test-refresh {:report untangled-spec.reporters.terminal/untangled-report
                 :changes-only true
                 :with-repl true})

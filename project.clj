(defproject cljen "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [compojure "1.1.5"]
                 [org.clojure/clojurescript "0.0-1011"]]
  :plugins [[lein-ring "0.8.2"]]
  :ring {:handler handler.handler/app } ;:host "192.168.0.108"
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]
                        [com.cemerick/url "0.0.6"]
                        [com.ashafa/clutch "0.4.0-RC1"]
                        [org.clojure/data.json "0.2.1"]
                        [org.clojure/core.incubator "0.1.2"];strint
                        ]}};db-view
  :main cljen.handler.handler
 )

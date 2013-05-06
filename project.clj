(defproject nerf_server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-ring "0.8.2"]]
  :ring {:handler handler.handler/app}         
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [ring/ring-core "1.1.6"]
                 [org.clojure/java.jdbc "0.3.0-alpha1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [com.ashafa/clutch "0.4.0-RC1"];clutch (or 0.3.1)
                 [clj-http "0.5.5"];clutch
                 [com.cemerick/url "0.0.6"];clutch
                 [noir "1.3.0"]
                 [org.clojure/data.json "0.2.1"]  
                 [org.clojure/core.incubator "0.1.2"] 
                   ]);

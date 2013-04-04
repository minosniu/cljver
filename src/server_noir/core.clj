(ns server_noir.core
  (:require [noir.server :as server]
            [noir.core :refer [defpage defpartial]]
            [noir.response :as response :refer [json]]
            [clojure.data.json :as json])
  (:use [hiccup.core]))

;json/read-str for parsing later
;The request should be like "{\"a\":1,\"b\":2}"
(defpage "/get_lib" []
  "simple example"
  (json/write-str {:a 1 :b 2} ; it shows {"a":1,"b":2}
))

;JSON definition till we apply parsing or communiation between client&server
(def in {
    :length {:type "f",
               :clock "sim_clk"},
    :velocity {:type "f",
                 :clock "sim_clk"},
   :gammaDynamic {:type "f",
                     :clock "sim_clk"},
    :gammaStatic {:type "f32",
                    :clock "sim_clk"}})
(def out  {
    :iaFiringRate {:type "I",
                     :clock "sim_clk"},
    :iiFiringRate {:type "I",
                     :clock "sim_clk"}})
(def spindle {:name "Loebspindle" :in in :out out})


(defpage "/get_lib1" []
  (json/write-str spindle))

(server/start 8080 :jetty-options {:host "192.168.0.114" :join? false})
;(defonce server (run-server #'app {:port 8080 :ip "127.168.0.1" :join? false}))
;(let [jetty (run-jetty app options)] (.stop jetty))


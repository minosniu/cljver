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

;;spindle
(def in1 {
    :length {:type "f",
               :clock "sim_clk"},
    :velocity {:type "f",
                 :clock "sim_clk"},
   :gammaDynamic {:type "f",
                     :clock "sim_clk"},
    :gammaStatic {:type "f32",
                    :clock "sim_clk"}})
(def out1  {
    :iaFiringRate {:type "I",
                     :clock "sim_clk"},
    :iiFiringRate {:type "I",
                     :clock "sim_clk"}})
(def spindle {:name "Loebspindle" :in in1 :out out1})

;; 2nd JSON
(def in2 {
    :current {:type "I",
               :clock "neuron_clk"},
    })
(def out2  {
    :spike {:type "b",
                     :clock "neuron_clk"},
 })
(def neuron {:name "IzhNeuron" :in in2 :out out2})

(def Resp {:spindle spindle :neuron neuron})

(defpage "/get_lib1" []
  (json/write-str spindle))
(defpage "/get_lib2" []
  (json/write-str neuron))
(defpage "/get_lib3" []
  (json/write-str Resp)); combine two JSON into one map

(server/start 8080 :jetty-options {:host "192.168.0.114" :join? false})
;(let [jetty (run-jetty app options)] (.stop jetty))


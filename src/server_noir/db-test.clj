(ns server-noir.db-test ;simple task only inside clojure, not with DB
  (:use [clojure.java.jdbc :exclude [resultset-seq]]))
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
 (def in2 {
    :current {:type "I",
               :clock "neuron_clk"},
    })
(def out2  {
    :spike {:type "b",
                     :clock "neuron_clk"},
 })
(defstruct JSON :name :in :out) 
(defn make-JSON [name in out] 
    (struct JSON name in out))

(def  db (ref #{})) ;reference
(defn add-record [cd] 
    (dosync (alter db conj cd )))
(add-record (make-JSON "IzhNeuron" in2 out2))
(add-record (make-JSON "Loebspindle" in1 out1))
(defn dump-db [] ;print out db
   (doseq [cd @db] 
      (doseq [[key value] cd ] 
      (print (format "%10s: %s \n" (name key) value))) 
      (println)))
(dump-db)


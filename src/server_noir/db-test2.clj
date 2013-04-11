(ns server-noir.db-test2
  (:require [clojure.java.jdbc :as sql]
            [com.ashafa.clutch :as clutch]
            [clj-http.client :as client]
            ;[clojure.contrib.json :as json]
            [clojure.data.json :as json])
  (:use [cemerick.url :only (url)]))

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
(def temp1 (make-JSON "IzhNeuron" in2 out2))
(def temp2 (make-JSON "Loebspindle" in1 out1))

(def DB (ref "user"));for choosing db, it will be changed according to HttpRequest


; DB- user, auth, lib (user, template, project)
(def user-db (assoc (cemerick.url/url "http://127.0.0.1:5984/" @DB)
                    :username "admin"
                    :password "admin"))
;(clutch/create-database user-db) ;if exist, no need to make one
(clutch/with-db user-db
 (clutch/put-document user-db temp1)
  (clutch/put-document user-db temp2)) ; put data into database

(def all-data (clutch/all-documents user-db));save database
(def single-val (clutch/dissoc-meta (clutch/get-document user-db ((first all-data) :id)))) ;read the first data from database

(single-val :name)

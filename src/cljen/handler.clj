(ns cljen.handler
  (:use compojure.core
        [cemerick.url :only (url)])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [com.ashafa.clutch :as clutch]
            [clojure.data.json :as json]))

;usage: in the Postman, 
;key : input
;value: "{\"user\" : \"foo1\", \"project\" : \"proj2\", \"new_block\" : \"spindle1\"}"
;and keep changing the value inside.

;parsing part
(def all-design (ref #{}))
(defn parse_input [request]
  (let [input (json/read-json (json/read-json request))
        user (keyword (input :user)) 
        project (keyword (input :project))
        id {:id (input :new_block)}]
    (if (nil? (all-design user));true-generate new, false-merge
      (dosync (println "new user")
        (ref-set all-design 
                 (into {} [@all-design
                           (zipmap [user] [{project [{:id (input :new_block)}]}])])))
      (if (nil? ((all-design user) project)) ;true- add id, false- add proj
        (dosync (println "user exist and no proj")
          (ref-set all-design 
                   (merge @all-design 
                          {user (into {} [(all-design user) {project [{:id (input :new_block)}]}])})))
        (dosync (println "user exist and proj exist")
          (ref-set all-design 
                   (into {} [@all-design 
                             (zipmap [user]
                                     [(zipmap [project] 
                                              [(conj ((all-design user) project) {:id (input :new_block)})])])])))))))
;DB part
(def DB (ref "nerf-db"))
(def nerf-db (assoc (cemerick.url/url "http://127.0.0.1:5984/" @DB)
                    :username "admin"
                    :password "admin"))
;server part
(defroutes app-routes
  (GET "/" [] "Hello weeeweee")
  (POST "/" [input] (doseq[] 
                                     (parse_input input)
                                     (str @all-design)
                                     )
        )
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))

(ns cljen.handler
  (:use compojure.core
        [cemerick.url :only (url)])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [com.ashafa.clutch :as clutch]
            [clojure.data.json :as json]))
(defstruct JSON :user :project)
(defn make-JSON [user project] 
  (struct JSON user project))

;(defstruct USER-JSON :name :password)
;(defstruct TEMPLATE-JSON :name :in :out)
;(defstruct DESIGN-JSON :project)
;(defn make-JSON [{params :params}] 
;  (case type  
;   "user" (struct USER-JSON name password)
;   "template" (struct TEMPLATE-JSON lib-name in out)
;   "design" (struct DESIGN-JSON project)
;   "test" (struct JSON user template design type))
;  )

;usage: (make-JSON user template design)

(def all-design (ref #{}))
(def test_in {:project "proj1", :user "foo1" :new_block "spindle1"})
(def test_in1 {:project "proj1", :user "foo2" :new_block "spindle2"})
(def test_in2 {:project "proj1", :user "foo1" :new_block "spindle2"})
(def test_in3 {:project "proj2", :user "foo1" :new_block "spindle2"})
(def test_in4 {:project "proj2", :user "foo3" :new_block "spindle2"})
(defn parse_input [request]
  (let [input (json/read-json request)
        user (keyword (input :user)) 
        project (keyword (input :project))
        id {:id (input :new_block)}]
    (if (nil? (all-design user));true-generate new, false-merge
      (dosync (println "new user")
        (ref-set all-design 
                 (into {} [@all-design
                           (zipmap [user] [{project [{:id (str (input :new_block) 1)}]}])])))
              ;(zipmap [(keyword (a :user))] [{(keyword (a :project)) {:id (a :new_block)}}])])))
      (if (nil? ((all-design user) project)) ;true- add id, false- add proj
        (dosync (println "user info exists and no project")
          (ref-set all-design 
                   (merge @all-design 
                          {user (into {} [(all-design user) {project [{:id (str (input :new_block) 1)}]}])})))
        (dosync (println "user and project exist")
          (ref-set all-design 
                   (into {} [@all-design 
                             (zipmap [user]
                                     [(zipmap [project] 
                                              [(conj ((all-design user) project) {:id (str (input :new_block)(+ 1 (alength (into-array((all-design user) project)))))})])])])));assemble again     
        ))))
;(json/read-str "{\"user\" : \"foo1\", \"project\" : \"proj1\", \"new_block\" : \"spindle1\"}")
;{"user" : "foo1", "project" : "proj1", "new_block" : "spindle1"}
;DB part, if DB is not connected, comment these four lines.
;(def DB (ref "nerf-db"))
;(def nerf-db (assoc (cemerick.url/url "http://127.0.0.1:5984/" @DB)
;                    :username "admin"
;                    :password "admin"))
(defroutes app-routes
  (GET "/" [] "Welcome!")
  (POST "/" [input] (doseq[] (let [input_str (json/read-json input)]
                                     
                                     (parse_input input)
                                     ;(clutch/with-db nerf-db (clutch/put-document nerf-db @all-design))
                                     (str @all-design)                             
                                     ;(str (json/read-json (json/read-json input)))
                                     ;(str test_in)
                                     ;(str input)
                                     )))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))



;(json/write-str @all-design)

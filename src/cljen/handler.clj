(ns cljen.handler
  (:use compojure.core
        [cemerick.url :only (url)])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [com.ashafa.clutch :as clutch]
            [clojure.data.json :as json]))


(def all-design (ref #{}))
; user : (:username :password :type)
; template : (:libname :in :out :type)
; design : (:project :username :type)
(def DB (ref "nerf-db"))
(def user-db (assoc (cemerick.url/url "http://localhost:5984/" @DB)
                    :username "admin"
                    :password "admin"))

(def USER_ref (ref {:name "" :password "" :type "user"}))
(def TEMPLATE_ref (ref {:name "" :in "" :out "" :type "template"}))
(def PROJECT_ref (ref {:name "" :project "" :type "project"})) ;will have block info too
(defn design [user project action-data]
  (case action-data
    "new" (dosync (ref-set USER_ref (merge @USER_ref {:name user}))
            (ref-set PROJECT_ref (merge @PROJECT_ref {:name user :project project}))
            (if (nil? (clutch/get-document @DB user))
                        (clutch/put-document @DB (merge @USER_ref {:_id user}))
                        (println "user exists."))
            (if (nil? (clutch/get-document @DB (str user "-" project)))
              (clutch/put-document @DB (merge @PROJECT_ref {:_id (str user "-" project)}))
              (println "project exists."))
            )  
    "save"  (clutch/with-db @DB
            (-> (clutch/get-document user)
                (clutch/update-document @USER_ref))
            (-> (clutch/get-document (str user "-" project))
                (clutch/update-document @PROJECT_ref))
            )
    "load" (dosync (ref-set USER_ref  (clutch/dissoc-meta (clutch/get-document "nerf-db" user)))
             (ref-set PROJECT_ref (clutch/dissoc-meta (clutch/get-document "nerf-db" (str user "-" project)))))            
    )
  ) 
  
(defn block [user project action-data]
  (case action-data
    "new" ;add one, save also inside project_ref
    "delete" ;remove one, also delete inside project_Ref
    "connect" ;change sth
    "disconnect" ;change sth
    )
  )
; input: {"user" : "ZY", "project" : "proj1" , "action" : {"type" : "project", "data" : "new"}}
(defn parse_input [request]
  (let [input (json/read-json request)
        user (input :user)
        project (input :project)
        action-type ((input :action) :type)
        action-data ((input :action) :data)]
    (case action-type
      "block" (block user project action-data)
      "project" (design user project action-data))))
;DB part, if DB is not connected, comment these four lines.
(defroutes app-routes
  (GET "/" [] "Welcome!")
  (POST "/" [input] (doseq[] (let [input_str (json/read-json input)]
                                     
                                     (parse_input input)
                                     ;(clutch/with-db nerf-db (clutch/put-document nerf-db @all-design))
                                     ;(str @all-design)                             
                                     ;(str (json/read-json (json/read-json input)))
                                     (str @USER_ref @PROJECT_ref)
                                     ;(str input)
                                     )))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))



;(json/write-str @all-design)

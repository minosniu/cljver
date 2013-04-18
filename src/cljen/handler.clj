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
(def PROJECT_ref (ref {:user "" :name "" :type "project"})) ;will have block info too
(def ERROR (ref {:result "success" :code 2 :description ""}));result = error -> code., result=success -> ""
(defn design [user project action-data]
  (case action-data
    "new" (dosync (ref-set USER_ref (merge @USER_ref {:name user}))
            (ref-set PROJECT_ref (merge @PROJECT_ref {:user user :name project}))
            (if (nil? (clutch/get-document @DB user))
              (ref-set ERROR (merge @ERROR {:result "error" :code 1 :description "no user exists"}));no user
              (ref-set ERROR (merge @ERROR {:result "success" :code "" :description ""})));user exist
            (if (nil? (clutch/get-document @DB (str user "-" project)))
              (when (= (ERROR :code) "") (clutch/put-document @DB (merge @PROJECT_ref {:_id (str user "-" project)})))
              (when-not (= 1 (ERROR :code)) (ref-set ERROR (merge @ERROR {:result "error" :code 2 :description "the project already exists"})))) ;-> no user error, project exist error, success
            )  
                  ;(clutch/put-document @DB (merge @USER_ref {:_id user})); don't create user! don't use it
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
    "connect" ;change sth, input&output
    "disconnect" ;change sth, output
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
                                     (json/write-str @ERROR)
                                     ;(json/write-json @ERROR); -> error response in JSON
                                     ;(str input)
                                     ))) 
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))



;(json/write-str @all-design)

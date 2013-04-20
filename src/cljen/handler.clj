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
(def PROJECT_ref (ref {:user "" :name "" :type "project" :data []})) ;will have block info too
(def ERROR (ref {:result "success" :code 2 :description "" :project_id ""}));result = error -> code., result=success -> ""
(defn design [user project action-data]
  (case action-data
    "new" (dosync (ref-set USER_ref (merge @USER_ref {:name user}))
            (ref-set PROJECT_ref (merge @PROJECT_ref {:user user :name project}))
            (if (nil? (clutch/get-document @DB user))
              (ref-set ERROR (merge @ERROR {:result "error" :code 1 :description "no user exists" :project_id ""}));no user
              (ref-set ERROR (merge @ERROR {:result "success" :code "" :description "" :project_id (str user "-" project)})));user exist
            (if (nil? (clutch/get-document @DB (str user "-" project)))
              (when (= (ERROR :code) "") (clutch/put-document @DB (merge @PROJECT_ref {:_id (str user "-" project)})))
              (when-not (= 1 (ERROR :code)) (ref-set ERROR (merge @ERROR {:result "error" :code 2 :description "the project already exists" :project_id ""})))) ;-> no user error, project exist error, success
            )  
                  ;(clutch/put-document @DB (merge @USER_ref {:_id user})); don't create user! don't use it
    "save"  (clutch/with-db @DB
            (-> (clutch/get-document user)
                (clutch/update-document @USER_ref))
            (-> (clutch/get-document (str user "-" project))
                (clutch/update-document @PROJECT_ref))
            )
    "load" (dosync (ref-set USER_ref  (clutch/dissoc-meta (clutch/get-document @DB user)))
             (ref-set PROJECT_ref (clutch/dissoc-meta (clutch/get-document @DB (str user "-" project)))))            
    )) 
(defn connect-block [id-list]
(let [in (first id-list) 
      out (second id-list)] 
  (merge 
  (dosync (first (filter #(= (:name %) (first in)) (PROJECT_ref :data)))))
))  
(defn disconnect-block [id-list]
(let [in (first id-list)
      out (second id-list)]
  
)) 
(defn block [user project action-data]
  (let [KEY (first (keys action-data))
        VAL (action-data KEY)
        block_count (alength (into-array (PROJECT_ref :data)))
        alloc_count (if (= block_count 0) 
                      (+ 1 block_count)
                      (+ 1 (Integer. (re-find #"\d+" ((last (PROJECT_ref :data)) :name)))))
        block_data (PROJECT_ref :data)]
  (case KEY
    :new (dosync (ref-set TEMPLATE_ref  (clutch/dissoc-meta (clutch/get-document @DB VAL)))
           (ref-set TEMPLATE_ref (merge @TEMPLATE_ref {:name (str (TEMPLATE_ref :name) alloc_count)}))
           (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) @TEMPLATE_ref)})))
    ;find block from library and save in memory
    :delete (dosync (ref-set PROJECT_ref (merge @PROJECT_ref {:data  
              (into [] (filter #(not= (:name %) VAL) (PROJECT_ref :data)))})))
    ;remove one block from memory
    :connect ( ;input 
               ;output change
               ;ref-set
               )
    :disconnect ;change sth, output
    )))
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
                                     "error msg, block&design _id response"
                                     (parse_input input)
                                     ;(clutch/with-db nerf-db (clutch/put-document nerf-db @all-design))
                                     ;(str @all-design)                             
                                     ;(str (json/read-json (json/read-json input)))
                                     ;(json/write-str @ERROR)
                                     (str @PROJECT_ref (alength (into-array (PROJECT_ref :data))))
                                     ;(str input)
                                     ))) 
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))



;(json/write-str @all-design)

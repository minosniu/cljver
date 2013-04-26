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
(def PROJECT_ref (ref {:user "" :name "" :type "project" :data {}})) ;will have block info too
(def ERROR (ref {:result "success" :content "" :project_id ""}));result = error -> code., result=success -> ""
(def OUTPUT (ref ""))
(def error-code (ref 0)) ; for diverse error with connection&disconnection
;log implementation
(defn new-design [user project]
;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "new", "type" : "project"}}
  (dosync  
    (if (nil? (clutch/get-document @DB user))
      (ref-set ERROR (merge @ERROR {:result "error" :content "no user exists" :project_id ""}));no user
      ;(clutch/put-document @DB (merge @USER_ref {:_id user})); don't create user! don't use it
      (doseq[](ref-set ERROR (merge @ERROR {:result "success" :content "" :project_id (str user "-" project)}))
        (ref-set USER_ref (merge @USER_ref {:name user}))
        (ref-set PROJECT_ref (merge @PROJECT_ref {:user user :name project}))));user exist
    ;blank project ref too 
    (if (nil? (clutch/get-document @DB (str user "-" project)))
      (when (= (ERROR :content) "") (clutch/put-document @DB (merge @PROJECT_ref {:_id (str user "-" project)})))
      (when-not (= 1 (ERROR :code)) (ref-set ERROR (merge @ERROR {:result "error" :content "the project already exists" :project_id ""})))) ;-> no user error, project exist error, success
    (ref-set OUTPUT (conj @ERROR {:project_id (str user "-" project)}))))

(defn save-design [user project]
  ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "save", "type" : "project"}}
  (dosync (clutch/with-db @DB
            (-> (clutch/get-document user)
              (clutch/update-document @USER_ref))
            (-> (clutch/get-document (str user "-" project))
              (clutch/update-document @PROJECT_ref))
            (ref-set OUTPUT {:result "success" :content ""}))))
(defn load-design [user project]
  ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "load", "type" : "project"}}
  (dosync (ref-set USER_ref  (clutch/dissoc-meta (clutch/get-document @DB user)))
    (ref-set PROJECT_ref (clutch/dissoc-meta (clutch/get-document @DB (str user "-" project))))
    (ref-set OUTPUT {:result "success" :content ""}))) 

(defn design [user project action]
  (case action
    "new" (new-design user project)
    "save"  (save-design user project)
    "load" (load-design user project)))

(defn new-block [data alloc_count]
  ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "new", "type" : "block", "data": {"template": "spindle", "position": {"left": 20, "top": 30}}}}
  (if (clutch/document-exists? @DB (data :template))
    (dosync 
      (ref-set TEMPLATE_ref  (clutch/dissoc-meta (clutch/get-document @DB (data :template))))
      (ref-set TEMPLATE_ref (merge @TEMPLATE_ref {:name (str (TEMPLATE_ref :name) alloc_count)}))
      (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) {(keyword (TEMPLATE_ref :name)) {:in (TEMPLATE_ref :in) :out (TEMPLATE_ref :out) :position (data :position)}})}))
      (ref-set OUTPUT {:result "success" :block (TEMPLATE_ref :name)}))
    (dosync (ref-set OUTPUT {:result "error" :content "the template does not exist"}))))
(defn delete-block [data]
  ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "delete", "type" : "block", "data": {"block": "spindle4"}}}
  (if (nil? ((PROJECT_ref :data)(keyword (data :block)))) 
    (dosync(ref-set OUTPUT {:result "error" :content "the block does not exist" }))
    (dosync (ref-set OUTPUT {:result "success"})
      (ref-set PROJECT_ref (merge @PROJECT_ref 
                                  {:data (apply dissoc (PROJECT_ref :data) [(keyword (data :block))])})))))
(defn move-block [data]
  ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "move", "type" : "block", "data":  {"block": "spindle1", "position": {"left": 33, "top": 21}}}}
  (let [block_id (keyword (data :block))]
    (dosync (if (nil? ((PROJECT_ref :data) block_id)) 
              (ref-set OUTPUT {:result "error" :content "the block does not exist" })
              (ref-set OUTPUT {:result "success"}))
      (ref-set PROJECT_ref (merge @PROJECT_ref 
                                  {:data (conj (PROJECT_ref :data) {block_id (merge ((PROJECT_ref :data) block_id) {:position (data :position)})})})))))

(defn connect-block [data] 
  ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "connect", "type" : "block", "data":  {"src":{"block": "spindle1", "port": "out"}, "dest":{"block": "spindle2", "port": "in"}}}}
 (let [out (data :src); {"block": "spindle1", "port": "out"}
        in (data :dest);need to fix
        out_key (keyword (out :block))
        in_key (keyword (in :block))
        out_pin (keyword (out :port))
        in_pin (keyword (in :port))]
    (dosync 
      (if (nil? ((PROJECT_ref :data) out_key)) (ref-set error-code (+ @error-code 1000)); output block exist?
        (if (nil? (((PROJECT_ref :data) out_key) out_pin)) (ref-set error-code (+ @error-code 10)))) ;output pin exist?
      (if (nil? ((PROJECT_ref :data) in_key)) (ref-set error-code (+ @error-code 100)); input block exist?
        (if (nil? (((PROJECT_ref :data) in_key) in_pin)) (ref-set error-code (+ @error-code 1)))) ;input pin exist?
      
      (cond 
        (>= @error-code 1000) (ref-set OUTPUT {:result "error" :content "the output block does not exist" })
        (>= @error-code 100) (ref-set OUTPUT {:result "error" :content "the input block does not exist" })
        (>= @error-code 10) (ref-set OUTPUT {:result "error" :content "the output pin does not exist" })
        (>= @error-code 1) (ref-set OUTPUT {:result "error" :content "the input pin does not exist" })
        (= @error-code 0) (doseq[] (ref-set OUTPUT {:result "success"})    
                            (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) {in_key (merge ((PROJECT_ref :data) in_key) {in_pin (out :block)} )} )}))
                            (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) {out_key (merge ((PROJECT_ref :data) out_key) {out_pin (in :block)})} )}))))
      (ref-set error-code 0))))   
(defn disconnect-block [data]
  ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "connect", "type" : "block", "data":  {"src":{"block": "spindle1", "port": "out"}, "dest":{"block": "spindle2", "port": "in"}}}}
  (let [out (data :src); {"block": "spindle1", "port": "out"}
        in (data :dest);need to fix
        out_key (keyword (out :block))
        in_key (keyword (in :block))
        out_pin (keyword (out :port))
        in_pin (keyword (in :port))] 
    (dosync 
      (if (nil? ((PROJECT_ref :data) out_key)) (ref-set error-code (+ @error-code 1000)); output block exist?
        (if (nil? (((PROJECT_ref :data) out_key) out_pin)) (ref-set error-code (+ @error-code 10)))) ;output pin exist?
      (if (nil? ((PROJECT_ref :data) in_key)) (ref-set error-code (+ @error-code 100)); input block exist?
        (if (nil? (((PROJECT_ref :data) in_key) in_pin)) (ref-set error-code (+ @error-code 1)))) ;input pin exist?
 (cond 
        (>= @error-code 1000) (ref-set OUTPUT {:result "error" :content "the output block does not exist" })
        (>= @error-code 100) (ref-set OUTPUT {:result "error" :content "the input block does not exist" })
        (>= @error-code 10) (ref-set OUTPUT {:result "error" :content "the output pin does not exist" })
        (>= @error-code 1) (ref-set OUTPUT {:result "error" :content "the input pin does not exist" })
        (= @error-code 0) (doseq[] (ref-set OUTPUT {:result "success"})    
                            (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) {in_key (merge ((PROJECT_ref :data) in_key) {in_pin ""} )} )}))
                            (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) {out_key (merge ((PROJECT_ref :data) out_key) {out_pin ""})} )}))))
      (ref-set error-code 0))))  

(defn block [user project action data]
  (let [block_count (alength (into-array (PROJECT_ref :data)))
        block_data (PROJECT_ref :data)
        alloc_count (if (= block_count 0) 
                      (+ 1 block_count)
                      (+ 1 (Integer. (re-find #"\d+" (name (first (keys block_data)))))))]
    (case action
      "new" (new-block data alloc_count) ;find block from library and save in project in memory
      "delete"(delete-block data) ;remove one block from project in memory
      "connect" (connect-block data);change two block pin info in project in memory
      "disconnect" (disconnect-block data);change two block pin info in project in memory
      "move" (move-block data) ; change block position in project in memory 
      )))
(defn parse_input [request]
  (let [input (json/read-json request)
        user (input :user)
        project (input :project)
        action ((input :extra):action)
        type ((input :extra):type)
        data ((input :extra):data)
        ]
    (case type
      "block"  (block user project action data)
      "project" (design user project action))))

;DB part, if DB is not connected, comment these four lines.
(defroutes app-routes
  (GET "/" [] "Welcome!")
  (POST "/" [input] (doseq[] (let [input_str (json/read-json input)]
                               "error msg, block&design _id response"                         
                               (parse_input input)
                               ;(str @PROJECT_ref "\n" @OUTPUT)
                               (json/write-str @OUTPUT)

                               ))) 
  (GET "/sirish" [] (json/write-str @PROJECT_ref))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))

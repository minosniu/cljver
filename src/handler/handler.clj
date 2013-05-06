(ns handler.handler
  (:use cljen.core) ;(create-spindle-from-post a-spindle)
  (:use compojure.core
        [cemerick.url :only (url)])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [com.ashafa.clutch :as clutch]
            [clojure.data.json :as json]))



; user : (:username :password :type)
; template : (:libname :in :out :type)
; design : (:project :username :type)
(def DB (ref "nerf-db"))
(def user-db (assoc (cemerick.url/url "http://localhost:5984/" @DB)
                    :username "admin"
                    :password "admin"))

(def USER_ref (ref {:name "" :password "" :type "user"})) ;delete it!!!!!!
(def TEMPLATE_ref (ref {:name "" :in "" :out "" :type "template"})) ;make big JSON, and remove it1!!!!!!!
(def PROJECT_ref (ref {:user "" :name "" :type "project" :data {}})) ;will have block info too

(def design-hash 
  (ref 
    {:minos {:foo1 (atom ["uuid1"
                          "uuid2"
                          ])}
     :ZY {:proj1 (atom ["uuid7"
                          "uuid8"
                          "uuid9"])}
     :kangwoo {:foo1 (atom ["uuid4"
                            "uuid5"
                            "uuid6"])}}))
;(def design-hash (ref {}))
(def design-content 
  (ref 
    {:uuid1 
     {:in {:gamma_dyn (atom "no_input") :lce (atom "no_input")} 
      :out {:ii "ii_block1" :ia "ia_block1"}
      :position (atom {:left 20 :top 30})
      :template "loeb-spindle"}
     :uuid2
     {:in {:gamma_dyn (atom "no_input") :lce (atom "no_input")} 
      :out {:ii "ii_block2" :ia "ia_block2"}
      :position (atom {:left 50 :top 10})
      :template "loeb-spindle"}}))

(def block-template 
    {:loeb-spindle 
     {:in {:gamma_dyn "" :lce ""} 
      :out {:ii "ii_block1" :ia "ia_block1"}
      :position {:left 0 :top 0}
      :template "loeb-spindle"}
     ;another template
     }) 

(defn uuid-save-db [UUID]
  "Convert atom to value to save in CouchDB as JSON"
  (let [temp-name (-> @design-content UUID :template)
        info (block-template (keyword temp-name))
        temp_in (atom {})
        temp_out (atom {})]
    (doseq [in-port (keys (info :in))] 
      (reset! temp_in (conj @temp_in {in-port @(-> @design-content UUID :in in-port)})))
    (doseq [out-port (keys (info :out))]
      (reset! temp_out (conj @temp_out {out-port (-> @design-content UUID :out out-port)})))
    {:_id (name UUID) :in @temp_in :out @temp_out :position (info :position) :template temp-name}))


(def ERROR (ref {:result "success" :content "" :project_id ""}));result = error -> code., result=success -> ""
(def OUTPUT (ref ""))
(def error-code (ref 0)) ; for diverse error with connection&disconnection
;log implementation
(defn new-design [user project]
;{"user" : "ZY", "project" : "proj21" , "action" : "new"}
  (dosync  
    (if (nil? (clutch/get-document @DB user));user check
      (ref-set ERROR (merge @ERROR {:result "error" :content "no user exists" :project_id ""}));no user
      (doseq[](ref-set ERROR (merge @ERROR {:result "success" :content "" :project_id (str user "-" project)}))
        (ref-set USER_ref (merge @USER_ref {:name user}))
        (ref-set PROJECT_ref (merge @PROJECT_ref {:user user :name project}))));user exist
    ;project check
    (if (nil? (clutch/get-document @DB (str user "-" project)))
      (when (= (ERROR :content) "") (clutch/put-document @DB (conj {:user user :proj project :block_uuid []} {:_id (str user "-" project)})))
      (when-not (= 1 (ERROR :code)) (ref-set ERROR (merge @ERROR {:result "error" :content "the project already exists" :project_id ""})))) ;-> no user error, project exist error, success
    (ref-set OUTPUT (conj @ERROR {:project_id (str user "-" project)}))))


(defn save-design [user project]
;{"user" : "minos", "project" : "foo1" , "action" : "save"}
  (let [USER (keyword user)
        PROJ (keyword project)]
  (dosync (clutch/with-db @DB
            (-> (clutch/get-document (str user "-" project))
              (clutch/update-document {:block_uuid @(-> @design-hash USER PROJ)})) ;save design-hash
            (doseq [uuid @(-> @design-hash USER PROJ)]
              (let [UUID (keyword uuid)]
                (println (str "\n\n" (conj {:_id uuid } (-> @design-content UUID)) "\n\n"))
              (if (nil? (clutch/get-document uuid))
                (clutch/put-document (uuid-save-db UUID));save new block in design-content
                (-> (clutch/get-document uuid)
                  (clutch/update-document (uuid-save-db UUID)))));save changed block in design-content
            (ref-set OUTPUT {:result "success" :content ""}))))))

(defn load-design [user project]
  ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "load", "type" : "project"}}
  (dosync (ref-set USER_ref  (clutch/dissoc-meta (clutch/get-document @DB user)))
    (ref-set PROJECT_ref (clutch/dissoc-meta (clutch/get-document @DB (str user "-" project))))
    (ref-set OUTPUT {:result "success" :content ""}))) 



(defn new-block [user project data]
 ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "new",  "data": {"template": "loeb-spindle", "position": {"left": 20, "top": 30}}}}
 (let [loaded-data (conj data (clutch/dissoc-meta (clutch/get-document @DB (data :template))))
       block-info (create-spindle-from-post loaded-data) ;@TEMPLATE_ref
       USER (keyword user)
       PROJ (keyword project)
       project-info (-> @design-hash USER PROJ)] 
 (if (clutch/document-exists? @DB (data :template))
    (dosync 
      (ref-set TEMPLATE_ref  loaded-data) ;delete later, if I save template here
      (ref-set TEMPLATE_ref (merge @TEMPLATE_ref block-info))  
      (ref-set design-content (conj @design-content block-info)) ;design-content
      (reset! project-info (conj @project-info (name(first(keys block-info))))) ;design-hash
     
      (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) {(keyword (TEMPLATE_ref :id)) {:in (TEMPLATE_ref :in) :out (TEMPLATE_ref :out) :position (data :position)}})}))
      (ref-set OUTPUT {:result "success" :block (TEMPLATE_ref :id)})) ;originally name
    (dosync (ref-set OUTPUT {:result "error" :content "the template does not exist"})))))


;(reset! (-> @design-content :uuid2 :in :gamma_dyn) "some_wierd_input")
; when received USER and PROJ, use (-> @design-hash (keyword USER) (keyword PROJ)) to retrive the uuid list
; Connect POST call should give: uuid_src, port_src, uuid_dest, port_dest
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
(defn clear-block [user project]
  (let [USER (keyword user)
        PROJ (keyword project)]
    (dosync 
      (doseq [uuid @(-> @design-hash USER PROJ)]
        (ref-set design-content (dissoc @design-content (keyword uuid)))))
    (reset! (-> @design-hash USER PROJ) [])))
  
(defn design-handler [request]
  (let [input (json/read-json request)
        user (input :user)
        project (input :project)
        action ((input :extra):action)
        type ((input :extra):type)
        data ((input :extra):data)     
        block_count (alength (into-array (PROJECT_ref :data)))
        block_data (PROJECT_ref :data)]
    (case action
      "new" (new-block user project data) ;find block from library and save in project in memory
      "delete"(delete-block data) ;remove one block from project in memory
      "clear" (clear-block user project);
      "connect" (connect-block data);change two block pin info in project in memory
      "disconnect" (disconnect-block data);change two block pin info in project in memory
      "move" (move-block data) ; change block position in project in memory 
      )))
(defn project-handler [request]
  (let [input (json/read-json request)
        user (input :user)
        project (input :project)
        action (input :action)]
  (case action
    "new" (new-design user project)
    "save"  (save-design user project)
    "load" (load-design user project))))

(defroutes app-routes
  (GET "/" [] "Welcome!")
  (POST "/" [input] (doseq[] (let [input_str (json/read-json input)]
                               "error msg, block&design _id response"                         
                               ;(parse_input input)
                               ;(str (merge @TEMPLATE_ref (create-spindle-from-post @TEMPLATE_ref)))
                               ;(str @TEMPLATE_ref)
                               (json/write-str @OUTPUT)

                               ))) 
  (POST "/design" [input] (doseq[] (let [input_str (json/read-json input)]  
                                     (design-handler input)
                                     (str @design-content "\n\n" @design-hash))) )
  (POST "/project" [input] (doseq[] (let [input_str (json/read-json input)] 
                                      (project-handler input)
                                      (str @design-content "\n\n" @design-hash)
                                      )))
  (GET "/sirish" [] (json/write-str @PROJECT_ref))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))


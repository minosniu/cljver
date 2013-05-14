(ns handler.handler
  (:use cljen.core) ;(create-spindle-from-post a-spindle)
  (:use [com.ashafa.clutch :exclude [assoc! conj! dissoc!]])
  (:use compojure.core
        [cemerick.url :only (url)])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            ;[com.ashafa.clutch :as clutch]
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
(def design-content 
  (ref 
    {:uuid1 
     {:in {:gamma_dyn (atom "no_input") :lce (atom "no_input")} 
      :out {:ii "ii_block1" :ia "ia_block1"}
      :position  {:left (atom 20) :top (atom 30)}
      :template "loeb-spindle"}
     :uuid2
     {:in {:gamma_dyn (atom "no_input") :lce (atom "no_input")} 
      :out {:ii "ii_block2" :ia "ia_block2"}
      :position  {:left (atom 50) :top (atom 10)}
      :template "loeb-spindle"}}))

(def block-template 
    {:loeb-spindle 
     {:in {:gamma_dyn "" :lce ""} 
      :out {:ii "ii_block1" :ia "ia_block1"}
      :position {:left 0 :top 0}
      :template "loeb-spindle"}
     ;another template
     }) 

(defn uuid-save-db [uuid]
  "Convert from atom to value, to save in CouchDB as JSON"
  (let [UUID (keyword uuid)
        temp-name (-> @design-content UUID :template)
        info (block-template (keyword temp-name))
        temp_in (atom {})
        temp_out (atom {})]
    (doseq [in-port (keys (info :in))] 
      (reset! temp_in (conj @temp_in {in-port @(-> @design-content UUID :in in-port)})))
    (doseq [out-port (keys (info :out))]
      (reset! temp_out (conj @temp_out {out-port (-> @design-content UUID :out out-port)})))
    {:uuid (name UUID) :in @temp_in :out @temp_out :position (info :position) :template temp-name :type "design-content"}))


(def ERROR (ref {:result "success" :content "" :project_id ""}));result = error -> code., result=success -> ""
(def OUTPUT (ref ""))
(def error-code (ref 0)) ; for diverse error with connection&disconnection
;log implementation
;
;(with-db user-db
;"save-view: design-view and project-view"
;  (save-view 
;    "design-view"
;    (view-server-fns 
;      :cljs
;      {:design-hash {:map (fn [doc] (when (and (aget doc "user") (aget doc "project") (aget doc "block_uuid"))
;                                      (js/emit (str (aget doc "user") "-" (aget doc "project")) (aget doc "block_uuid")) 
;                                      ))}
;       :design-content {:map (fn [doc] (when (and (aget doc "in") (aget doc "out") (aget doc "uuid"))
;                                         (js/emit (aget doc "uuid") (aget doc "template"))))}  }))
;         ;template, user should be added
;  
;    (save-view 
;      "template-view"
;      (view-server-fns 
;        :cljs
;        {:template-view {:map (fn [doc] (when (and (aget doc "in") (aget doc "out") (aget doc "name"))
;                                      (js/emit  (aget doc "name") (to-array [(aget doc "in") (aget doc "out") (aget doc "name")]) ) 
;                                      ))} }))
;    
;         )

(defn get-view-key [user project type & uuid]
  "get a key of each view"
(let [key_uuid (first uuid)]
  (case type
    "design-hash" (first (get-view "design-view" "design-hash" {:key (str user "-" project)}))
    "design-content" (first (get-view "design-view" "design-content" {:key key_uuid})))))

(defn new-design [user project]
;{"user" : "ZY", "project" : "proj21" , "action" : "new"}
  "create new project only if there is no project exist"
(let [USER (keyword user)
      PROJ (keyword project)
      ]
  (dosync  (with-db @DB
    (if (nil? (get-view-key user project "design-hash"))
      ;(when (= (ERROR :content) "") (put-document @DB (conj {:user user :project project :block_uuid [] :type "design-hash"} {:_id (str user "-" project)})))
      ;(when-not (= 1 (ERROR :code)) (ref-set ERROR (merge @ERROR {:result "error" :content "the project already exists" :project_id ""})))
      (doseq []
        ;create new design and save it into design hash!!!
        ;(ref-set design-hash (assoc-in @design-hash [USER PROJ] (atom "")));;;;;;;;
        (ref-set design-hash (assoc-in @design-hash [(keyword user) (keyword project)] (atom "")))
        (with-db @DB (put-document {:block_uuid [] :user user :project project :type "design-hash"}))
        (ref-set OUTPUT {:result "success" :content ((get-view-key user project "design-hash") :id)})
       
       (println @design-hash))
      (ref-set OUTPUT {:result "error" :content "project exists"})
      ) ;-> no user error, project exist error, success
    ;(ref-set OUTPUT (conj @ERROR {:project_id (str user "-" project)}))
))))

(defn save-design [user project]
;{"user" : "minos", "project" : "foo1" , "action" : "save"}
  (let [USER (keyword user)
        PROJ (keyword project)]
    (println @design-hash)
  (dosync (with-db @DB
            "save design-hash"
            (if (nil?  (get-view-key user project "design-hash"))
            (put-document {:block_uuid @(-> @design-hash USER PROJ) :user user :project project :type "design-hash"})
            (-> (get-document ((get-view-key user project "design-hash") :id)) ;unique id
              (update-document {:block_uuid @(-> @design-hash USER PROJ)})))
            "save design-content"           
            (doseq [uuid @(-> @design-hash USER PROJ)] ; should add specific function for partial saving
              (if (nil? (get-view-key user project "design-content" uuid))
                (put-document (uuid-save-db uuid));save new block in design-content
                (-> (get-document ((get-view-key user project "design-content" uuid) :id))
                  (update-document (uuid-save-db uuid)))))
            "send result msg to front-end"
            (ref-set OUTPUT {:result "success" :content ""})))))

(defn load-design [user project]
  ;{"user" : "ZY", "project" : "proj1" , "action" : "load"}
  (let [USER (keyword user)
        PROJ (keyword project)]
  (dosync (with-db @DB
            "load design-hash"
            (if (nil?  (get-view-key user project "design-hash"))
              (ref-set OUTPUT {:result "Fail" :content "No user data in DB"})
              (dosync (ref-set design-hash (assoc-in @design-hash [USER PROJ] (atom ((get-view-key user project "design-hash") :value))))
              (ref-set OUTPUT {:result "success" :content ((get-view-key user project "design-hash") :id)})
             )
              )
            "load design-content"
            (doseq [uuid @(-> @design-hash USER PROJ)] 
              (if (nil? (get-view-key user project "design-content" uuid))
                (ref-set OUTPUT {:result "Fail" :content "No block data in DB"})
                (doseq [] (ref-set OUTPUT {:result "success"})
                  (ref-set design-content (get-document @DB ((get-view-key user project "design-content" uuid):id))));need to correct!
                ;(ref-set design-content (assoc-in @design-content [(keyword uuid)](atom ((get-view-key user project "design-content" uuid) ))))
                ))
            ))))
    
(comment
    (ref-set USER_ref  (dissoc-meta (get-document @DB (str user "-" project))))
    (ref-set PROJECT_ref (dissoc-meta (get-document @DB (str user "-" project))))
    (ref-set OUTPUT {:result "success" :content ""}) 
)

(defn new-block [user project data]
 ;{"user" : "ZY", "project" : "proj21" , "action" : "new",  "data": {"template": "loeb-spindle", "position": {"left": 20, "top": 30}}}
 (let [loaded-data (conj data (dissoc-meta (get-document @DB (data :template))))
       block-info (create-spindle-from-post loaded-data) ;@TEMPLATE_ref
       USER (keyword user)
       PROJ (keyword project)
       project-info (-> @design-hash USER PROJ)] 
 (if (document-exists? @DB (data :template))
    (dosync 
      (ref-set TEMPLATE_ref  loaded-data) ;delete later, if I save template here
      (ref-set TEMPLATE_ref (merge @TEMPLATE_ref block-info))  
      (ref-set design-content (conj @design-content block-info)) ;design-content
      (reset! project-info (conj @project-info (name(first(keys block-info))))) ;design-hash
     
      (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) {(keyword (TEMPLATE_ref :id)) {:in (TEMPLATE_ref :in) :out (TEMPLATE_ref :out) :position (data :position)}})}))
      (ref-set OUTPUT {:result "success" :block (name(first(keys block-info)))})) ;originally name
    (dosync (ref-set OUTPUT {:result "error" :content "the template does not exist"})))))

;(reset! (-> @design-content :uuid2 :in :gamma_dyn) "some_wierd_input")
; when received USER and PROJ, use (-> @design-hash (keyword USER) (keyword PROJ)) to retrive the uuid list
; Connect POST call should give: uuid_src, port_src, uuid_dest, port_dest
(defn delete-block [user project data]
  ;{"action" : "delete", "type" : "block", "data": {"block": "spindle4"}}
  (let [USER (keyword user)
        PROJ (keyword project)
        block_id (keyword (data :block))]
  (if (nil? (design-content block_id)) 
    (dosync(ref-set OUTPUT {:result "error" :content "the block does not exist" }))
    (dosync (ref-set OUTPUT {:result "success"})
      (println block_id) 
      (ref-set design-content (dissoc @design-content block_id))
      (reset! (-> @design-hash USER PROJ) (into [](filter #(not (= (data :block) %)) @(-> @design-hash USER PROJ))))
      ))))
(defn move-block [data]
  ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "move", "type" : "block", "data":  {"block": "spindle1", "position": {"left": 33, "top": 21}}}}
  (let [block_id (keyword (data :block))
        position (data :position)]
    (dosync (if (nil? (design-content block_id)) 
              (ref-set OUTPUT {:result "error" :content "the block does not exist" })
              (doseq[] (reset! (-> @design-content block_id :position :left)  (position :left)) 
                (reset! (-> @design-content block_id :position :top)  (position :top)) 
                (ref-set OUTPUT {:result "success"}))
              )
      
      ;add put-document!!!!!! (if we want to save it temporarily)
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
      
      
;      (if (nil? ((PROJECT_ref :data) out_key)) (ref-set error-code (+ @error-code 1000)); output block exist?
;        (if (nil? (((PROJECT_ref :data) out_key) out_pin)) (ref-set error-code (+ @error-code 10)))) ;output pin exist?
;      (if (nil? ((PROJECT_ref :data) in_key)) (ref-set error-code (+ @error-code 100)); input block exist?
;        (if (nil? (((PROJECT_ref :data) in_key) in_pin)) (ref-set error-code (+ @error-code 1)))) ;input pin exist?
;      
;      (cond 
;        (>= @error-code 1000) (ref-set OUTPUT {:result "error" :content "the output block does not exist" })
;        (>= @error-code 100) (ref-set OUTPUT {:result "error" :content "the input block does not exist" })
;        (>= @error-code 10) (ref-set OUTPUT {:result "error" :content "the output pin does not exist" })
;        (>= @error-code 1) (ref-set OUTPUT {:result "error" :content "the input pin does not exist" })
;        (= @error-code 0) (doseq[] (ref-set OUTPUT {:result "success"})    
;                            (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) {in_key (merge ((PROJECT_ref :data) in_key) {in_pin (out :block)} )} )}))
;                            (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) {out_key (merge ((PROJECT_ref :data) out_key) {out_pin (in :block)})} )}))))
;      (ref-set error-code 0)
      )))   
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
  
(defn design-handler [user project action data]
  (let [;input (json/read-json request)
        ;user (input :user)
        ;project (input :project)
        ;action (input :action)
        ;data (input :data)     
        ;block_count (alength (into-array (PROJECT_ref :data)))
        ;block_data (PROJECT_ref :data)
        ]
    (case action
      "save" (save-design user project)
      "new" (new-block user project data) ;find block from library and save in project in memory
      "delete"(delete-block user project data) ;remove one block from project in memory
      "clear" (clear-block user project);
      "connect" (connect-block data);change two block pin info in project in memory
      "disconnect" (disconnect-block data);change two block pin info in project in memory
      "move" (move-block data) ; change block position in project in memory 
      )))
(defn project-handler [user project action]
  (let [;input (json/read-json request)
        ;user (input :user)
        ;project (input :project)
        ;action (input :action)
        ]
  (case action
    "new" (new-design user project)
    "save"  (save-design user project) ;should be removed later
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
  
  (POST "/design" [user project action data] 
        (let [keywordized-data (json/read-json data true)]
          (doseq[] 
            (design-handler user project action keywordized-data)
             
            (json/write-str @OUTPUT)
            ;(str @design-content "\n\n" @OUTPUT)
            ))) 
  (POST "/project" [user project action] (doseq[] (let [;input_str (json/read-json input)
                                                        ] 
                                      (project-handler user project action)
                                      ;(str @design-hash "\n\n" @OUTPUT "\n\n" @design-content)
                                       (json/write-str @OUTPUT)
                                      )))
  (GET "/sirish" [] (json/write-str @OUTPUT)); need to be changed
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))

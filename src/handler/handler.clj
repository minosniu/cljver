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
    ; :kangwoo {:foo1 (atom ["uuid4"
    ;                        "uuid5"
     ;                       "uuid6"])}
    }))
(def design-content 
  (ref 
    {:uuid1 
     {:in {:gamma_dyn (atom "no_input") :lce (atom "no_input")} 
      :out {:ii "ii_uuid1" :ia "ia_uuid1"}
      :position  {:left (atom 20) :top (atom 30)}
      :template "loeb-spindle"}
     :uuid2
     {:in {:gamma_dyn (atom "no_input") :lce (atom "no_input")} 
      :out {:ii "ii_uuid2" :ia "ia_uuid2"}
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
        info (first (get-view "template-view" "template" {:key temp-name}))
        in (first (info :value))
        out (second (info :value ))
        position {:left @(-> @design-content UUID :position :left) :top @(-> @design-content UUID :position :top)}
        temp_in (atom {})
        temp_out (atom {})
        temp_position (atom {})]
    (doseq [in-port (keys in)] ;input port
      (reset! temp_in (conj @temp_in {in-port @(-> @design-content UUID :in in-port)}))) 
    (doseq [out-port (keys out)];output port
      (reset! temp_out (conj @temp_out {out-port (-> @design-content UUID :out out-port)}))) 
 ;   (doseq [coord (keys position)];position
 ;     (reset! temp_position (conj @temp_position {coord @(-> @design-content UUID :position coord)})))
    {:uuid (name UUID) :in @temp_in :out @temp_out :position position :template temp-name :type "design-content"}))

(defn uuid-load-db [uuid]
  "Convert DB data to reference and atom, and save them to memory"
  (let [UUID (keyword uuid)
        block-info (first (get-view "design-view" "design-content" {:key uuid}))
        in (first (block-info :value))
        out (second (block-info :value))
        position (second (rest (block-info :value)))
        temp-name (last (block-info :value))
        temp_in (atom {})
        ]
    (doseq [in-port (keys in)] ;input data are converted to atom
      (reset! temp_in (conj @temp_in {in-port (atom (in in-port))}))) 
    {UUID {:in @temp_in :out out :position {:left (atom (position :left)) :top (atom (position :top))} :template temp-name}}  
    ))

(def ERROR (ref {:result "success" :content "" :project_id ""}));result = error -> code., result=success -> ""
(def OUTPUT (ref ""))
(def error-code (ref 0)) ; for diverse error with connection&disconnection

;
(with-db @DB
"save-view: design-view and project-view"
  (save-view 
    "design-view"
    (view-server-fns 
      :cljs
      {:design-hash {:map (fn [doc] (when (and (aget doc "user") (aget doc "project") (aget doc "block_uuid"))
                                      (js/emit (str (aget doc "user") "-" (aget doc "project")) (aget doc "block_uuid")) 
                                      ))}
       :design-content {:map (fn [doc] (when (and (aget doc "in") (aget doc "out") (aget doc "uuid"))
                                         (js/emit (aget doc "uuid") (to-array [(aget doc "in") (aget doc "out")  (aget doc "position") (aget doc "template")]))))}  }))

    (save-view 
      "template-view"
      (view-server-fns 
        :cljs
        {:template {:map  (fn [doc] (when (and (aget doc "in") (aget doc "out") (aget doc "name"))                                     
                                      (js/emit  (aget doc "name") (to-array [(aget doc "in") (aget doc "out") ]) ) 
                                      )) } }))
             ;user should be added
         )

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
      (doseq []
        ;create new design and save it into design hash!!!
        ;(ref-set design-hash (assoc-in @design-hash [USER PROJ] (atom "")));;;;;;;;
        (ref-set design-hash (assoc-in @design-hash [USER PROJ] (atom [])))
        (with-db @DB (put-document {:block_uuid [] :user user :project project :type "design-hash"}))
        (ref-set OUTPUT {:result "success" :content ((get-view-key user project "design-hash") :id)}))
      (ref-set OUTPUT {:result "error" :content "project exists"})
      ) ;-> no user error, project exist error, success
))))

(defn save-design [user project]
;{"user" : "minos", "project" : "foo1" , "action" : "save"}
  (let [USER (keyword user)
        PROJ (keyword project)]
  (dosync (with-db @DB
            "save design-hash"
            (if (nil?  (get-view-key user project "design-hash"))
              (put-document {:block_uuid @(-> @design-hash USER PROJ) :user user :project project :type "design-hash"})
              
              (-> (get-document ((get-view-key user project "design-hash") :id)) ;unique id
                (update-document {:block_uuid @(-> @design-hash USER PROJ)}))
            )
            "save design-content"           
            (doseq [uuid @(-> @design-hash USER PROJ)] ; should add specific function for partial saving
              (let [block_data (uuid-save-db uuid)] ; ordinary format
              (if (nil? (get-view-key user project "design-content" uuid))  
                (put-document block_data);save new block in design-content
                (-> (get-document ((get-view-key user project "design-content" uuid) :id))
                  (update-document {:in (block_data :in) :position (block_data :position)})
                  ))))
            "send result msg to front-end"
            (ref-set OUTPUT {:result "success"})))))

(defn load-design [user project]
  ;"user" : "minos", "project" : "foo1" , "action" : "load"
  (let [USER (keyword user)
        PROJ (keyword project)]
  (dosync (with-db @DB
            "load design-hash"
            (if (nil?  (get-view-key user project "design-hash"))
              (ref-set OUTPUT {:result "Fail" :content "No user and project data in DB"})
              (dosync (ref-set design-hash (assoc-in @design-hash [USER PROJ] (atom ((get-view-key user project "design-hash") :value))))
              (ref-set OUTPUT {:result "success" :content ((get-view-key user project "design-hash") :id)})
              "load design-content"
              (doseq [uuid @(-> @design-hash USER PROJ)] 
                (if (nil? (get-view-key user project "design-content" uuid))
                  ();(ref-set OUTPUT {:result "success"})
                  (doseq [] (ref-set OUTPUT {:result "success" :content ((get-view-key user project "design-content" uuid) :id)})
                    (ref-set design-content (merge @design-content (uuid-load-db uuid)))
                    );need to correct!
                  ;(ref-set design-content (assoc-in @design-content [(keyword uuid)](atom ((get-view-key user project "design-content" uuid) ))))
                  ))
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
       block-id (first (keys block-info))
       USER (keyword user)
       PROJ (keyword project)
      ; project-info @(-> @design-hash USER PROJ)
       ] 
 (if (document-exists? @DB (data :template))
    (dosync 
      "save design-content into memory"
      (ref-set design-content (conj @design-content block-info));design-content
      (reset! (-> @design-content block-id :position :left) (-> data :position :left))  ;position-left
      (reset! (-> @design-content block-id :position :top) (-> data :position :top))   ;position-top
      "save design-hash into memory"
     (swap! (-> @design-hash USER PROJ) #(conj % (name(first(keys block-info))))) ;design-hash

     ; (ref-set PROJECT_ref (merge @PROJECT_ref {:data (conj (PROJECT_ref :data) {(keyword (TEMPLATE_ref :id)) {:in (TEMPLATE_ref :in) :out (TEMPLATE_ref :out) :position (data :position)}})}))
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
 (let [ src (data :src); {"block": "spindle1", "port": "out"}
        dest (data :dest);need to fix
        src_key (keyword (src :block))
        dest_key (keyword (dest :block))
        src_pin (keyword (src :port))
        dest_pin (keyword (dest :port))]
   ;(println (-> @design-content src_key :out))
    (dosync
      (reset! (-> @design-content dest_key :in dest_pin) (-> @design-content src_key :out src_pin))
      (ref-set OUTPUT {:result "success"})
      )))   
(defn disconnect-block [data]
  ;{"user" : "ZY", "project" : "proj21" , "extra":{"action" : "connect", "type" : "block", "data":  {"src":{"block": "spindle1", "port": "out"}, "dest":{"block": "spindle2", "port": "in"}}}}
  (let [src (data :src); {"block": "spindle1", "port": "out"}
        dest (data :dest);need to fix
        src_key (keyword (src :block))
        dest_key (keyword (dest :block))
        src_pin (keyword (src :port))
        dest_pin (keyword (dest :port))] 
    (dosync 
      (reset! (-> @design-content dest_key :in dest_pin) "no input")
      (ref-set OUTPUT {:result "success"})
      )))  
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
             
            ;(json/write-str @OUTPUT)
            (str @design-content "\n\n" @OUTPUT "\n\n" @design-hash)
            ))) 
  (POST "/project" [user project action] (doseq[] (let [;input_str (json/read-json input)
                                                        ] 
                                      (project-handler user project action)
                                      (str @design-hash "\n\n" @OUTPUT "\n\n" @design-content)
                                       ;(json/write-str @OUTPUT)
                                      )))
  (GET "/sirish" [] (json/write-str @OUTPUT)); need to be changed
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))

(ns handler.handler
  (:use cljen.core) ;(create-spindle-from-post a-spindle)
  (:use [com.ashafa.clutch :exclude [assoc! conj! dissoc!]])
  (:use compojure.core
        [cemerick.url :only (url)])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            ;[com.ashafa.clutch :as clutch]
            [clojure.data.json :as json]))
;DB setting
(def DB (ref "nerf-db"))
(def user-db (assoc (cemerick.url/url "http://localhost:5984/" @DB)
                    :username "admin"
                    :password "admin"))
;Memory
(def design-hash 
  (ref 
    {:minos {:foo1 (atom ["uuid1"
                          "uuid2"
                          ])}
     ;more user can be added by default
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
(def OUTPUT (ref ""))
(def verilog (ref ""))

;DB-views
;(with-db @DB
;"save-view: design-view and project-view"
;  (save-view 
;    "design-view"
;    (view-server-fns 
;      :cljs
;      {:design-hash {:map (fn [doc] (when (and (aget doc "user") (aget doc "project") (aget doc "block_uuid"))
;                                      (js/emit (str (aget doc "user") "-" (aget doc "project")) (aget doc "block_uuid")) 
;                                      ))}
;       :design-content {:map (fn [doc] (when (and (aget doc "in") (aget doc "out") (aget doc "uuid"))
;                                         (js/emit (aget doc "uuid") (to-array [(aget doc "in") (aget doc "out")  (aget doc "position") (aget doc "template")]))))}  }))
;
;    (save-view 
;      "template-view"
;      (view-server-fns 
;        :cljs
;        {:template {:map  (fn [doc] (when (and (aget doc "in") (aget doc "out") (aget doc "name"))                                     
;                                      (js/emit  (aget doc "name") (to-array [(aget doc "in") (aget doc "out") ]) ) 
;                                      )) } }))
;             ;user should be added
;         )

;Functions
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
    {:uuid (name UUID) :in @temp_in :out @temp_out :position position :template temp-name :type "design-content"}))

(defn uuid-load-db [uuid]
  "Convert DB data to reference and atom, and save them to memory"
  (let [UUID (keyword uuid)
        block-info (first (get-view "design-view" "design-content" {:key uuid}))
        in (first (block-info :value))
        out (second (block-info :value))
        position (second (rest (block-info :value)))
        temp-name (last (block-info :value))
        temp_in (atom {})]
    (doseq [in-port (keys in)] ;input data are converted to atom
      (reset! temp_in (conj @temp_in {in-port (atom (in in-port))}))) 
    {UUID {:in @temp_in :out out :position {:left (atom (position :left)) :top (atom (position :top))} :template temp-name}}  
    ))

(defn get-view-key [user project type & uuid]
  "get a key of each view"
(let [key_uuid (first uuid)]
  (case type
    "design-hash" (first (get-view "design-view" "design-hash" {:key (str user "-" project)}))
    "design-content" (first (get-view "design-view" "design-content" {:key key_uuid})))))

;project functions
(defn new-design [user project]
; "user" : "kangwoo", "project" : "foo5" , "action" : "new" 
  "create new project only if there is no project exist"
(let [USER (keyword user)
      PROJ (keyword project)]
  (dosync  (with-db @DB
    (if (nil? (get-view-key user project "design-hash"))
      (doseq []
        (ref-set design-hash (assoc-in @design-hash [USER PROJ] (atom [])))
        (with-db @DB (put-document {:block_uuid [] :user user :project project :type "design-hash"}))
        (ref-set OUTPUT {:result "success" :content ((get-view-key user project "design-hash") :id)}))
      (ref-set OUTPUT {:result "error" :content "project exists"})
      ))))) ;-> no user error, project exist error, success

(defn save-design [user project]
; "user" : "kangwoo", "project" : "foo5" , "action" : "save"
  (let [USER (keyword user)
        PROJ (keyword project)]
  (dosync (with-db @DB
            "save design-hash"
            (if (nil?  (get-view-key user project "design-hash"))
              (put-document {:block_uuid @(-> @design-hash USER PROJ) :user user :project project :type "design-hash"})
              (-> (get-document ((get-view-key user project "design-hash") :id)) ;unique id
                (update-document {:block_uuid @(-> @design-hash USER PROJ)})))
            "save design-content"           
            (doseq [uuid @(-> @design-hash USER PROJ)] 
              (let [block_data (uuid-save-db uuid)] 
              (if (nil? (get-view-key user project "design-content" uuid))  
                (put-document block_data);save new block in design-content
                (-> (get-document ((get-view-key user project "design-content" uuid) :id))
                  (update-document {:in (block_data :in) :position (block_data :position)})))))
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
                  ();it shouldn't happen
                  (doseq [] ;(ref-set OUTPUT {:result "success" :content ((get-view-key user project "design-content" uuid) :id)})
                    (ref-set design-content (merge @design-content (uuid-load-db uuid)))
                    )))))))))
 ;Design functions
(defn new-block [user project data]
 ;"user" : "kangwoo", "project" : "foo5" , "action" : "new",  "data": {"template": "loeb-spindle", "position": {"left": 20, "top": 30}}
 (let [loaded-data (conj data (dissoc-meta (get-document @DB (data :template))))
       block-info (create-spindle-from-post loaded-data)
       block-id (first (keys block-info))
       USER (keyword user)
       PROJ (keyword project)] 
 (if (document-exists? @DB (data :template))
    (dosync 
      "save design-content into memory"
      (ref-set design-content (conj @design-content block-info));design-content
      (reset! (-> @design-content block-id :position :left) (-> data :position :left))  ;position-left
      (reset! (-> @design-content block-id :position :top) (-> data :position :top))   ;position-top
      "save design-hash into memory"
      (swap! (-> @design-hash USER PROJ) #(conj % (name(first(keys block-info))))) ;design-hash
      (ref-set OUTPUT {:result "success" :block (name(first(keys block-info)))})) ;originally name
    (dosync (ref-set OUTPUT {:result "error" :content "the template does not exist"})))))

(defn delete-block [user project data]
  ;"user" : "kangwoo", "project" : "foo5" , "action" : "delete", "data": {"block": "uuid1"}
  (let [USER (keyword user)
        PROJ (keyword project)
        block_id (keyword (data :block))]
  (if (nil? (design-content block_id)) 
    (dosync(ref-set OUTPUT {:result "error" :content "the block does not exist" }))
    (dosync (ref-set OUTPUT {:result "success"})
      (ref-set design-content (dissoc @design-content block_id))
      (reset! (-> @design-hash USER PROJ) (into [](filter #(not (= (data :block) %)) @(-> @design-hash USER PROJ))))))))

(defn move-block [data]
  ;"user" : "kangwoo", "project" : "foo5" , "action" : "move", "data":  {"block": "spindle1", "position": {"left": 33, "top": 21}}
  (let [block_id (keyword (data :block))
        position (data :position)]
    (dosync (if (nil? (design-content block_id)) 
              (ref-set OUTPUT {:result "error" :content "the block does not exist" })
              (doseq[] (reset! (-> @design-content block_id :position :left)  (position :left)) 
                (reset! (-> @design-content block_id :position :top)  (position :top)) 
                (ref-set OUTPUT {:result "success"}))))))

(defn connect-block [data] 
  ;"user" : "kangwoo", "project" : "foo5" , "action" : "connect", "data":  {"src":{"block": "spindle1", "port": "out"}, "dest":{"block": "spindle2", "port": "in"}}
 (let [ src (data :src); {"block": "spindle1", "port": "out"}
        dest (data :dest)
        src_key (keyword (src :block))
        dest_key (keyword (dest :block))
        src_pin (keyword (src :port))
        dest_pin (keyword (dest :port))]
    (dosync
      (reset! (-> @design-content dest_key :in dest_pin) (-> @design-content src_key :out src_pin))
      (ref-set OUTPUT {:result "success"}))))   

(defn disconnect-block [data]
  ;"user" : "kangwoo", "project" : "foo5" , "action" : "connect", "data":  {"src":{"block": "spindle1", "port": "out"}, "dest":{"block": "spindle2", "port": "in"}}
  (let [src (data :src); {"block": "spindle1", "port": "out"}
        dest (data :dest)
        src_key (keyword (src :block))
        dest_key (keyword (dest :block))
        src_pin (keyword (src :port))
        dest_pin (keyword (dest :port))] 
    (dosync 
      (reset! (-> @design-content dest_key :in dest_pin) "no input")
      (ref-set OUTPUT {:result "success"}))))  

(defn clear-block [user project]
  (let [USER (keyword user)
        PROJ (keyword project)]
    (dosync 
      (doseq [uuid @(-> @design-hash USER PROJ)]
        (ref-set design-content (dissoc @design-content (keyword uuid)))))
    (reset! (-> @design-hash USER PROJ) [])))

(defn generate-verilog [user project]
  (let [USER (keyword user)
        PROJ (keyword project)]
    (dosync (ref-set verilog {})
    (doseq [uuid @(-> @design-hash USER PROJ)]
      (let [UUID (keyword  uuid)
            code (verilog UUID)]
    (imprint (list-inslot-wire (-> @design-content UUID)))
    (imprint (list-outslot-wire (-> @design-content  UUID)))
    (ref-set OUTPUT {:result "success" :content (imprint (list-inslot-wire (-> @design-content UUID)))})
    )))))

;Handlers
(defn design-handler [user project action data]
    (case action
      "save" (save-design user project)
      "new" (new-block user project data) ;find block from library and save in project in memory
      "delete"(delete-block user project data) ;remove one block from project in memory
      "clear" (clear-block user project);
      "connect" (connect-block data);change two block pin info in project in memory
      "disconnect" (disconnect-block data);change two block pin info in project in memory
      "move" (move-block data) ; change block position in project in memory 
      "generate" (generate-verilog user project)
      ))
(defn project-handler [user project action]
  (case action
    "new" (new-design user project)
    "save"  (save-design user project) ;should be removed later
    "load" (load-design user project)))

(defroutes app-routes
  (GET "/" [] "Welcome!")
;  (POST "/" [input] (doseq[] (let [input_str (json/read-json input)]
;                               "error msg, block&design _id response" 
;                               (json/write-str @OUTPUT))))  
  (POST "/design" [user project action data & debugging] 
        (let [keywordized-data (json/read-json data true)]
          (doseq[] 
            (design-handler user project action keywordized-data)
            (if (empty? debugging)
              (json/write-str @OUTPUT)
              (str @design-hash "\n\n" @design-content "\n\n" @OUTPUT))))) 

  (POST "/project" [user project action & debugging] 
        (doseq[] 
          (project-handler user project action)
          (if (empty? debugging)
            (json/write-str @OUTPUT)
            (str @design-hash "\n\n" @design-content "\n\n" @OUTPUT))))
  (GET "/sirish" [] (json/write-str @OUTPUT)); need to be changed
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))

;example
;(reset! (-> @design-content :uuid2 :in :gamma_dyn) "some_wierd_input")

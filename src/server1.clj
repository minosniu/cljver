(ns nerf_server.server1
  (:use [ring.adapter.jetty])
  (:use [ring.middleware.params])
  (:use [ring.util.response]); imsi(test server)
  (:require [org.httpkit.client :as http]);client imsi
  (:use org.httpkit.server);server imsi
  (:use [clojure.string :only (join split)]) ;future use for parsing
  )
(def Name (ref "default"))
(def ID (ref 0))
(def Rank (ref 0))
(defn handler [{req :params}]
 "this handler gets httpRequest and print the parameters in the request"
  (dosync 
    (when (not (empty? req))
      (println "request: " req)
      (ref-set Name (str (req "name")))
      (ref-set ID (str (req "id")))
      (ref-set Rank (str (req "rank")))
       {:status  200
        :headers {"Content-Type" "text/plain"}
        :body    (str "Name:" (req "name") "\nID: " (req "id") "\nRank: " (req "rank"))        
     })))

(def app 
    (wrap-params handler)  )
(defonce server (run-jetty #'app {:port 8080 :join? false}));prevent locking Clojure REPL
;(.stop server)
;(.start server)

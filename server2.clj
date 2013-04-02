(ns nerf_server.server2
  (:use [ring.adapter.jetty])
  (:use [ring.middleware.params])
  (:use [ring.util.response]); imsi(test server)
  (:require [org.httpkit.client :as http]);client imsi
  (:use org.httpkit.server);server imsi
  (:use [clojure.string :only (join split)]) ;future use for parsing
  )
(def Request (ref "none"))
(def URI (ref "URI"))
(defn handler [req]
  "this handler gets httpRequest and read URI. It doesn't parse parameters yet."
  (condp = (:uri req)
    "/get_lib"
    (dosync 
        (ref-set Request (str req))
      {:status 404
       :headers {"Content-Type" "text/html"}
       :body    (str "Request:\n" req)})
    "/server"
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body    (str "Server name: " (:server-name req))
      }
    "/address"
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (str "Remote address: " (:remote-addr req))
      }
    "/uri"
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body  (dosync (ref-set URI (:uri req)))       
      }
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body    (str "URI doesn't exist: " (:uri req)) }))
(defn app [req]
  (handler req))

(defonce server (run-jetty #'app {:port 8080 :join? false}));prevent locking Clojure REPL

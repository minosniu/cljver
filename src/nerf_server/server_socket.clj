(ns nerf_server.server_socket
  (:use [ring.middleware.params]) ;for parsing, future use
  (:use [ring.middleware.keyword-params]) ;for parsing, future use 
  (:use [ring.util.response]); temporary use
  (:use org.httpkit.server);server for http-kit
  (:use [clojure.string :only (join split)]) ;future use for parsing
 )
(defn handler [req]
  "Unified Async/Websocket With Channel example for server"
  (with-channel req channel              ; get the channel
    ;; communicate with client using method defined above
    (on-close channel (fn [status]
                        (println "channel closed")))
    (if (websocket? channel)
      (println "WebSocket channel")
      (println "HTTP channel"))
    (on-receive channel (fn [data]       ; data received from client
                         (println "Received: " data)
           ;; An optional param can pass to send!: close-after-send?
           ;; When unspecified, `close-after-send?` defaults to true for HTTP channels
           ;; and false for WebSocket.  (send! channel data close-after-send?)
                          (send! channel data))))) ; data is sent directly to the client
(run-server handler {:port 8080 :ip "127.168.0.1" :join? false});run server and prevent locking Clojure REPL

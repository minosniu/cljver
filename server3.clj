(ns nerf_server.server3
  (:use [ring.adapter.jetty])
  (:use [ring.middleware.params])
  (:use [ring.util.response]); (server test)
  (:use [clojure.string :only (join split)]) ;future use for parsing
  )
(def Name (ref "Lee"))
(defn page [name]
  (str "<html><body>"
       (if name
         (dosync (ref-set Name name) (str "done"))
         (str "<form>"
              "Name: <input name='name' type='text'>"
              "<input type='submit'>"
              "</form>"))
       "</body></html>"))

(defn handler [{{name "name"} :params}]
  "This handler gets input from the text box and print a new page"
  (-> (response (page name))
      (content-type "text/html")))
(def app
  (-> handler wrap-params))
 (defonce server (run-jetty #'app {:port 8080 :join? false}))
 

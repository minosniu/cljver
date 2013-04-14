(ns server-noir.post-json
  (:require  [clojure.data.json :as json]))
"usage: (parse_input test_in1) and keep testing test_in~test_in4"
(def all-design (ref #{}))
(def test_in {:project "proj1", :user "foo1" :new_block "spindle1"})
(def test_in1 {:project "proj1", :user "foo2" :new_block "spindle2"})
(def test_in2 {:project "proj1", :user "foo1" :new_block "spindle2"})
(def test_in3 {:project "proj2", :user "foo1" :new_block "spindle2"})
(def test_in4 {:project "proj2", :user "foo3" :new_block "spindle2"})
(def json_test "{\"user\" : \"foo1\", \"project\" : \"proj1\", \"new_block\" : \"spindle1\"}")
;(json/read-json json_test)
(defn parse_input [request]
  (let [input request ; for map (only for now)
        ;input (json/read-json request) ;for JSON input
        user (keyword (input :user)) 
        project (keyword (input :project))
        id {:id (input :new_block)}]
    (if (nil? (all-design user));true-generate new, false-merge
      (dosync (println "no user info")
        (ref-set all-design 
                 (into {} [@all-design
                           (zipmap [user] [{project [{:id (input :new_block)}]}])])))
      (if (nil? ((all-design user) project)) ;true- add id, false- add proj
        (dosync (println "user info exists and no project")
          (ref-set all-design 
                   (merge @all-design 
                          {user (into {} [(all-design user) {project [{:id (input :new_block)}]}])})))
        (dosync (println "user and project exist")
          (ref-set all-design 
                   (into {} [@all-design 
                             (zipmap [user]
                                     [(zipmap [project] 
                                              [(conj ((all-design user) project) {:id (input :new_block)})])])])))))))

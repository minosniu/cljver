(ns server-noir.file_list
  (:use [clojure.java.io])
  (:import java.io.File))


(def directory "C:/") ; need to set directory
(def files 
    (for [file (file-seq (clojure.java.io/file directory))] (.getName file))); save file list
;;print file list
(defn my-ls [d]
  (println "Files in " (.getName d))
  (doseq [f (.listFiles d)]
    (if (.isDirectory f)
      (print "d ")  ; d directory
      (print "- ")) ; - file
    (println (.getName f))))
(-> "/tmp" file .listFiles)
(.listFiles (file "/tmp"))

(my-ls (File. directory))

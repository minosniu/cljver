(ns cljen.core
  (:use [clojure.core.strint] ; Provides Ruby-style string interpolation (<<)
        [clojure.string :only (join split)]))

(def id-counter (java.util.concurrent.atomic.AtomicLong.))

(defn id-gen []
  (cons
   (.getAndIncrement id-counter)
   (lazy-seq
     (id-gen))))

(defn -make-unique-wire [slot board]
  "Return a unique string to identify the wires for each [slot] on [board]"
  (join "@" [(name slot) board]))

(defn -make-unique-id 
  "Return a unique string to identify the Verilog module"
  ([_] (join ["block" (str (first (id-gen)))]))
  ([] (clojure.string/replace (str (java.util.UUID/randomUUID)) "-" "_")) )


(defn create-spindle [& uuid?] 
  "Create a spindle block, duplex, with option of UUID"
  (let [id (if uuid? (-make-unique-id uuid?) (-make-unique-id )) ]
    {:id   id
     :in   {:lce       (atom "no_input")   ; Later for binding with previous block 
            :gamma_dyn (atom "no_input")    }
     :out  {:ia        (-make-unique-wire :ia id) ; immutable  
            :ii        (-make-unique-wire :ii id)}}))

(defn build-a-creator-func [block-json]
  "Return a function that looks like create-spindle"
  ())

;(def a-spindle 
;  "Disposable! Something received from front-end POST:create"
;  {:name "loeb-spindle"
;   :in   {:lce {:type "f" :clock "sim"}
;          :gamma_dyn {:type "f" :clock "sim"}}
;   :out  {:ia  {}
;          :ii  {}}});

(defn -get-ins [key-list]
  (cond 
    (empty? key-list)     {}
    :else (conj {(first key-list) (atom "no_input")}; (atom "no_input")
                 (-get-ins (rest key-list)))))

(defn -get-outs [id key-list]
  (let [first-key (first key-list)]
    (cond 
      (empty? key-list)     {}
      :else (conj {first-key (-make-unique-wire first-key id)}
                  (-get-outs id (rest key-list))))))
  
;(-get-ins (keys (a-spindle :in)))
;(-get-outs "dummy" (keys (a-spindle :out)))

(defn create-spindle-from-post [spindle-json & uuid?] 
  "Receive a JSON, return a Clojure style map structure"
  (let [id (if uuid? (-make-unique-id uuid?) (-make-unique-id )) ] 
    {(keyword id)  {
     :in  (-get-ins (keys (spindle-json :in)))
     :out (-get-outs id (keys 
                          (spindle-json :out)))
     :position (-get-ins (keys (spindle-json :position)))
     :template (spindle-json :template) }}))
  
;(create-spindle-from-post a-spindle)
  
(defn create-signal-source [] 
  (let [id (-make-unique-id)] 
    {:id   id
     :out  {:trig0     (-make-unique-wire :trig0 id) ; immutable  
            :trig1     (-make-unique-wire :trig1 id)}}))

(defn create-signal-monitor [] 
  (let [id (-make-unique-id)] 
    {:id   id
     :in   {
            :viewer0   (atom "no_input")   ; Later for binding with previous block
            :viewer1   (atom "no_input")   } }))

(defn connect-a-b [src dest links]
  (for [[src-slot dest-slot] links] 
;    (reset! (-> dest :in (links src-slot)) (-> src :out src-slot))))
    (reset! (-> dest :in dest-slot) (-> src :out src-slot))))

;(defn imprint [block]
;  (let [in-links (block :in) 
;        out-links (block :out)]
;    (doseq []
;      (for [slot (keys out-links)]
;        (println (<< ".~(name slot)(~(out-links slot))")))
;      (for [slot (keys in-links)]
;        (let [wire @(in-links slot)] 
;          (prn (<< ".~(name slot)(~{wire}))")))))))

(defn list-inslot-wire [block]
  (for [[slot wire] (block :in)]
    (<< ".~(name slot)(~{@wire})")))

(defn list-outslot-wire [block]
  (for [[slot wire] (block :out)]
    (<< ".~(name slot)(~{wire})")))

(defn imprint [lines]
  (doseq [line lines] (println line)))

;(def spindle (create-spindle "uuid"))
(def spindle (create-spindle))
(def spindle-b (create-spindle))
(do 
  (imprint (list-inslot-wire spindle)) 
  (imprint (list-outslot-wire spindle)))

(defn writelines [file-path lines]
  (with-open [wtr (clojure.java.io/writer file-path)]
    (doseq [line lines] (.write wtr line) )))

;(writelines "/Users/minos_niu/foobar.txt" foo-lines)
 

(def data-src (create-signal-source))
(def data-outlet (create-signal-monitor))
;(connect-a-b data-src spindle {:trig0 :lce, :trig1 :gamma_dyn})
;(connect-a-b spindle data-outlet {:ia :viewer0, :ii :viewer1})



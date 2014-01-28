(ns mendocino.mongo
  "Utilities for interacting with MongoDB"
  (:require [monger [core :as mg] [query :as mq]])
  (:import com.mongodb.MongoOptions))

(defn connect [servers]
  (if (> (count servers) 1)
    (mg/connect (map #(apply mg/server-address %) servers) (MongoOptions.))
    (mg/connect servers)))

(defmacro with-mongo
  [connection db collection & forms]
  `(let [conn# (connect ~connection)
         r# (doall
             (mg/with-connection conn#
               (mg/with-db (mg/get-db ~db)
                 (mq/with-collection ~collection
                   ~@forms))))]
     (.close conn#)
     r#))

(defmacro with-mongo-db
  [connection db & forms]
  `(let [conn# (connect ~connection)]
     (try
       (let [v# (mg/with-connection conn#
                 (mg/with-db (mg/get-db ~db)
                   ~@forms))]
         (if (seq? v#) (doall v#) v#))
       (finally
         (.close conn#)))))

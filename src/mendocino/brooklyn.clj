(ns mendocino.brooklyn
  "Utility to create a person record in mysql"
  (:require [clojure.java.jdbc :as db]
            [mendocino.config :as c])
  (:import java.util.Date))

(defn create-person []
  (:generated_key
    (db/with-connection (c/brooklyn)
      (db/insert-record :people {:created_at (Date.) :updated_at (Date.)}))))

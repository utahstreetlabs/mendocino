(ns mendocino.test-support
  (:require [mendocino [config :as c] [mongo :refer [with-mongo-db]] [sync :refer :all]]
           [monger.collection :as mc])
  (:import java.util.Date))

(alter-var-root #'c/env (constantly :test))

(def now (Date.))

(defn clear-db []
  (with-mongo-db (c/rubicon) (c/rubicon-db) (mc/remove "profiles") (mc/remove "follows")))

(defn initialize-db []
  (clear-db)
  (insert-follow-in-mongo now
    (create-profile {:name "Follower One" :uid "222"} "facebook" now)
    (create-profile {:name "Being Synced" :uid "111"} "facebook" now))
  (create-profile {:name "Existing Profile" :uid "333"} "facebook" now)
  (insert-follow-in-mongo now
    (get-profile-id "111" "facebook")
    (get-profile-id "333" "facebook"))
  (insert-follow-in-mongo now
    (get-profile-id "111" "facebook")
    (get-profile-id "333" "facebook")))

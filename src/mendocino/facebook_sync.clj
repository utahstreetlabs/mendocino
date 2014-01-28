(ns mendocino.facebook_sync
  (:require [clojure.data.json :as json]
            [clojure.set :refer [rename-keys]]
            [fb-graph-clj.core :as fb]
            [slingshot.slingshot :refer [throw+ try+]]
            [mendocino.util.logger :as log]
            [mendocino [helpers :refer :all] [network_sync :refer :all]]))

(defmethod friend-rankable? "facebook" [network] true)

(defmethod bi-directional? "facebook" [network] true)

(defmethod auth-always-valid? "facebook" [network] false)

(defmethod redhook-job "facebook" [network] "Redhook::Job::AddFacebookFollow")

(defn profile-url [uid] (str "http://www.facebook.com/" uid))

(defn photo-url [uid] (str "http://graph.facebook.com/" uid "/picture"))

(defn set-profile-url [attrs-map uid]
  (if (:link attrs-map) (rename-keys attrs-map {:link :profile_url}) (assoc attrs-map :profile_url (profile-url uid))))

(defn set-location [attrs-map]
  (assoc attrs-map :location (get-in attrs-map [:location :name])))

(defn set-dates [attrs-map]
  (assoc attrs-map :birthday (format-date (:birthday attrs-map))
                   :updated_at (format-date (:updated_time attrs-map))))

(defn get-attrs [token]
  (try+
    (fb/pull [:me] {:access_token token :date_format "r"})
    (catch Object _
      (log/error "pull user's facebook profile attributes failed with response:" (:body (:object &throw-context)))
      (throw+ (json/read-json (:body (:object &throw-context)))))))

(defmethod normalized-attrs "facebook" [network uid {:keys [token]}]
  (-> (get-attrs token)
      (:body)
      (select-keys [:username :name :first_name :last_name :email :link :gender :location :birthday :updated_time])
      (set-profile-url uid)
      (set-location)
      (assoc :photo_url (photo-url uid))
      (set-dates)))

(defn get-followers [token]
  (try+
    (fb/data-seq (fb/pull [:me :friends] {:access_token token}))
    (catch Object _
      (log/error "get fb follows failed with response:" (:body (:object &throw-context)))
      (throw+ (json/read-json (:body (:object &throw-context)))))))

(defmethod follower-ids "facebook" [network uid {:keys [token]}]
  (build-index (get-followers token) :id))

(defn transform-attrs [attrs-map]
  (-> attrs-map
      (select-keys [:id :name])
      (rename-keys {:id :uid})))

(defn normalize-follower-seq [follower-seq]
  (map transform-attrs follower-seq))

(defmethod lookup-followers "facebook" [network followers-map auth-map]
  (-> followers-map
      (vals)
      (normalize-follower-seq)))

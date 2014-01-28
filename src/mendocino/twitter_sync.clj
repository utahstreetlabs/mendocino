(ns mendocino.twitter_sync
  (:require [clojure.set :refer [rename-keys]]
            [mendocino [config :as c] [helpers :refer :all] [network_sync :refer :all]]
            [twitter [oauth :refer [make-oauth-creds]]]
            [twitter.callbacks.handlers :refer [response-return-body response-throw-error exception-rethrow]]
            [twitter.api.restful :refer [show-followers lookup-users show-user]])
  (:import twitter.callbacks.protocols.SyncSingleCallback))

(defmethod friend-rankable? "twitter" [network] false)

(defmethod bi-directional? "twitter" [network] false)

(defmethod auth-always-valid? "twitter" [network] true)

(defmethod redhook-job "twitter" [network] "Redhook::Job::AddTwitterFollow")

(defn creds [{:keys [secret token]}]
  (make-oauth-creds c/twitter-consumer-key
                    c/twitter-consumer-secret
                    token
                    secret))

(defn profile-url [screen-name] (str "https://twitter.com/" screen-name))

(defn set-profile-url [attrs-map]
  (assoc attrs-map :profile_url (profile-url (:username attrs-map))))

(defn set-type [attrs-map]
  (assoc attrs-map :api_follows_count (Integer. (:api_follows_count attrs-map))))

(defn get-followers [uid creds cursor]
  (show-followers :oauth-creds creds
                  :callbacks (SyncSingleCallback. response-return-body response-throw-error exception-rethrow)
                  :params {:user_id uid
                           :stringify_ids true
                           :cursor cursor}))

(defn follower-ids-seq
  ([uid creds] (follower-ids-seq uid creds (get-followers uid creds nil)))
  ([uid creds response]
     (lazy-cat (:ids response)
               (when (not= (get response :next_cursor 0) 0)
                 (follower-ids-seq uid
                               creds
                               (get-followers uid creds (:next_cursor response)))))))

(defmethod follower-ids "twitter" [network uid auth-map]
  (-> (follower-ids-seq uid (creds auth-map))
      (vec-self-map)))

(defn lookup-user-ids [creds user-ids]
  (lookup-users :oauth-creds creds
                :callbacks (SyncSingleCallback. response-return-body response-throw-error exception-rethrow)
                :params {:user_id user-ids}))

(defn follower-seq [user-ids creds]
  (let [[first-ids rest-ids] (split-at 100 user-ids)]
    (lazy-cat (lookup-user-ids creds first-ids)
              (when (> (count user-ids) 100)
                (follower-seq rest-ids creds)))))

(defn transform-attrs [attrs-map]
  (-> attrs-map
      (select-keys [:id_str :screen_name :name :profile_image_url :location :followers_count])
      (rename-keys {:id_str :uid
                    :screen_name :username
                    :profile_image_url :photo_url
                    :followers_count :api_follows_count})
      (set-type)
      (set-profile-url)))

(defn normalize-follower-seq [follower-seq]
  (map transform-attrs follower-seq))

(defmethod lookup-followers "twitter" [network followers-map auth-map]
  (when (not-empty followers-map)
    (-> (keys followers-map)
        (follower-seq (creds auth-map))
        (normalize-follower-seq))))

(defn get-attrs [creds uid]
  (show-user :oauth-creds creds
             :callbacks (SyncSingleCallback. response-return-body response-throw-error exception-rethrow)
             :params {:user_id uid}))

(defmethod normalized-attrs "twitter" [network uid auth-map]
  (-> (creds auth-map)
      (get-attrs uid)
      (transform-attrs)))

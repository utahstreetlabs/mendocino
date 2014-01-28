(ns mendocino.friend_rank
  (:require [clojure.data.json :as json]
            [clj-time [core :as ct] [coerce :as ctc]]
            [clojure.set :refer [intersection]]
            [fb-graph-clj.core :as fb]
            [slingshot.slingshot :refer [throw+ try+]]
            [mendocino.config :as c]
            [mendocino.util.logger :as log]
            [monger [operators :refer :all]
             [collection :as mc]])
  (:import java.util.Date))

(defn last-ranked [profile-id]
  (:ranked_at (mc/find-map-by-id "profiles" profile-id [:ranked_at])))

(defn ranked-today? [last-ranked]
  (and
    last-ranked
    (ct/after? (ctc/from-date last-ranked) (ct/minus (ct/now) (ct/days 1)))))

(defn update-last-ranked [profile-id]
  (mc/update-by-id "profiles" profile-id {$set {:ranked_at (Date.)}}))

(defn get-posts [type token]
  (try+
    (:data (:body (fb/pull [:me type] {:access_token token :limit c/fr-posts-limit})))
    (catch Object _
      (log/error "FR: pulling user's" type "fb posts failed with response:" (:body (:object &throw-context)))
      (throw+ (json/read-json (:body (:object &throw-context)))))))

(defn ids-from-actions [actions]
  (map :id actions))

(defn ids-from-objects [objects]
  (map (comp :id :from) objects))

(defn parse-post [post]
  (concat
    (list (:id (:from post)))
    (ids-from-objects (:data (:comments post)))
    (ids-from-actions (:data (:likes post)))
    (ids-from-actions (:data (:tags post)))))

(defn ids-from-posts [data]
  "takes in a collection of posts and returns a collection of uids"
  (flatten
    (map parse-post data)))

(defn update-follow [follow-map profile-id follower-id date]
  (log/info "Updating follow with profile-id:" profile-id "and follower-id:" follower-id "with rank:" follow-map)
  (mc/update "follows"
             {:profile_id profile-id :follower_id follower-id}
             {$set (assoc follow-map :updated_at date)}
             :multi true))

(defn common-keys [map1 map2]
  (intersection
    (set (keys map1))
    (set (keys map2))))

(defn get-follower-id [uid]
  (:_id (mc/find-one-as-map "profiles" {:uid uid :network :facebook} [:_id])))

(defn resolve-follower-id [uid uid-profile-map]
  (or
    (:_id (uid-profile-map uid))
    (get-follower-id uid)))

(defn write-ranks-to-mongo [uids fr-map profile-id uid-profile-map]
  (let [now (Date.)]
    (doseq [uid uids]
      (update-follow {:fr (Integer. (fr-map uid))}
                     profile-id
                     (resolve-follower-id uid uid-profile-map)
                     now))))

(defn update-friend-ranks [fr-map profile-id uid-profile-map]
  (-> (common-keys fr-map uid-profile-map)
      (write-ranks-to-mongo fr-map profile-id uid-profile-map)))

(defn friend-ids-seq [token]
  (lazy-cat
    (ids-from-posts (get-posts :photos token))
    (ids-from-posts (get-posts :statuses token))
    (ids-from-posts (get-posts :locations token))))

(defn rank-friends [token profile-id uid-profile-map]
  (when (not (ranked-today? (last-ranked profile-id)))
    (log/info "Starting Friend Rank for profile:" profile-id)
    (-> (friend-ids-seq token)
        (frequencies)
        (update-friend-ranks profile-id uid-profile-map))
    (update-last-ranked profile-id)
    (log/info "Friend Rank finished for profile:" profile-id)))

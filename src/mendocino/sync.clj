(ns mendocino.sync
  (:require [clj-airbrake.core :as airbrake]
            [clj-time [core :as ct] [coerce :as ctc]]
            [clojure.data :as d]
            [copious.auth.core :as auth]
            [mendocino [helpers :refer :all] [friend_rank :refer [rank-friends]]
             [network_sync :refer :all] [mongo :refer [with-mongo-db]]
             [brooklyn :as b] [config :as c] facebook_sync twitter_sync]
            [mendocino.util.logger :as log]
            [monger [operators :refer :all]
             [collection :as mc] [query :as mq]]
            [resque-clojure.core :as resque])
  (:import com.mongodb.ReadPreference org.bson.types.ObjectId java.util.Date))

(defn airbraker [exception]
  (try
    (airbrake/notify c/airbrake-key c/env "mendocino/sync" exception)
    (catch Exception e
      (log/error "Could not notify airbrake: " e))))

(defn update-profile [profile-map profile-id date]
  (log/info "Updating profile:" profile-id "with attributes:" profile-map)
  (mc/update-by-id "profiles" profile-id {$set (assoc profile-map :synced_at date)}))

(defn diff-map-on-key
  "a diff for maps based only on keys"
  [map1 map2]
  (let [[first second _] (d/diff (apply hash-set (keys map1)) (apply hash-set (keys map2)))]
    [(select-keys map1 first) (select-keys map2 second)]))

(defn get-profile
  ([uid network fields] (first (mc/find-maps "profiles" {:uid uid :network network} fields)))
  ([uid network] (get-profile uid network [])))

(defn get-profile-id [uid network]
  (:_id (get-profile uid network [:_id])))

(defn get-follower-profile-ids [profile-id]
  (mq/with-collection "follows"
    (mq/find {:profile_id profile-id})
    (mq/fields [:follower_id])
    (mq/read-preference ReadPreference/SECONDARY)))

(defn get-follower-profile [profile-id]
  (first
   (mq/with-collection "profiles"
     (mq/find {:_id profile-id})
     (mq/fields [:uid])
     (mq/read-preference ReadPreference/SECONDARY))))

(defn get-existing-follows [profile-id]
  "returns a map of exiting folllowers with uid as keys and maps containing profile :_id as values."
  (build-index
   (map
    (fn [profile-map] (get-follower-profile (:follower_id profile-map)))
    (get-follower-profile-ids profile-id))
   :uid))

(defn create-profile [attrs network date]
  (let [profile-id (ObjectId.)]
    (log/info "Creating profile:" profile-id "with attributes:" attrs)
    (mc/insert "profiles" (merge attrs {:_id profile-id
                                        :person_id (Integer. (or (:person_id attrs) (b/create-person)))
                                        :network network
                                        :updated_at date
                                        :created_at date}))
    profile-id))

(defn follow-count-in-mongo [profile-id follower-id]
  (mc/count "follows" {:profile_id profile-id :follower_id follower-id}))

(defn insert-follow-in-mongo [date follower-id profile-id]
  (mc/insert "follows" {:follower_id follower-id
                        :profile_id profile-id
                        :updated_at date
                        :created_at date}))

(defn get-person-id [profile-id]
  (when-let [response
             (mq/with-collection "profiles"
               (mq/find {:_id profile-id})
               (mq/fields [:person_id])
               (mq/read-preference ReadPreference/SECONDARY))]
    (:person_id (first response))))

(defn remove-follow-in-mongo [follower-id profile-id]
  (mc/remove "follows" {:follower_id follower-id :profile_id profile-id}))

(defn clean-insert-follow [date follower-id profile-id]
  (when (> (follow-count-in-mongo profile-id follower-id) 0)
    (remove-follow-in-mongo follower-id profile-id))
  (insert-follow-in-mongo date follower-id profile-id))

(defn find-or-create-profile [attrs network now]
  (or (get-profile-id (attrs :uid) network) (create-profile attrs network now)))

(defn add-follow-in-redhook [followee-person-id follower-person-id redhook-job]
  ; stop enqueuing redhook jobs
  #_(doseq [i (range (c/redhook-instances))]
    (resque/enqueue (str "redhook_sub_" (+ i 1))
                    redhook-job
                    followee-person-id
                    follower-person-id)))

(defn set-api-follows-count [attrs-map follows]
  (if (or (contains? attrs-map :api_follows_count) follows)
    (assoc attrs-map :api_follows_count (Integer. (or (:api_follows_count attrs-map) (count follows))))
    attrs-map))

(defn create-follows
  "iterates over a collection of follower attr maps and creates mongo follows"
  [new-follows network profile-id now bi-directional?]
  (doseq [attrs new-follows]
    (let [follower-id (find-or-create-profile attrs network now)]
      (log/info "Creating follows between profile:" profile-id "and follower:" follower-id)
      (clean-insert-follow now follower-id profile-id)
      (when bi-directional?
        (clean-insert-follow now profile-id follower-id))
      (add-follow-in-redhook (get-person-id profile-id) (get-person-id follower-id) (redhook-job network)))))

(defn remove-follows [old-follows profile-id bi-directional?]
  (doseq [follower old-follows]
    (log/info "Removing follows between profiles:" (follower :_id) "and" profile-id)
    (remove-follow-in-mongo (follower :_id) profile-id)
    (when bi-directional?
      (remove-follow-in-mongo profile-id (follower :_id)))))

(defn token-valid?
  "Ensure that a token hasn't expired.
  NB: token_expires_at is stored in seconds since epoch, while clj-time's `to-long` returns milliseconds since epoch."
  [auth-map network]
  (or (auth-always-valid? network)
      (> (* 1000 (:token_expires_at auth-map)) (ctc/to-long (ct/now)))))

(defn get-auth-unless-expired [uid network]
  (let [auth-map (auth/fetch network uid)]
    (if (token-valid? auth-map network)
      auth-map
      nil)))

(defn sync-attrs [person-id uid network]
  (if-let [auth-map (get-auth-unless-expired uid network)]
    (let [now (Date.)
          profile-id (find-or-create-profile {:person_id person-id :uid uid} network now)]
      (-> (normalized-attrs network uid auth-map)
          (set-api-follows-count nil)
          (update-profile profile-id now)))
    (log/info "Token expired for uid:" uid "on network:" network)))

(defn sync-all [person-id uid network]
  (if-let [auth-map (get-auth-unless-expired uid network)]
    (let [now (Date.)
          profile-id (find-or-create-profile {:person_id person-id :uid uid} network now)
          network-follows (follower-ids network uid auth-map)
          existing-follows (get-existing-follows profile-id)
          [new-follows obsolete-follows] (diff-map-on-key network-follows existing-follows)]
      (-> (lookup-followers network new-follows auth-map)
          (create-follows network profile-id now (bi-directional? network)))
      (-> (vals obsolete-follows)
          (remove-follows profile-id (bi-directional? network)))
      (-> (normalized-attrs network uid auth-map)
          (set-api-follows-count network-follows)
          (update-profile profile-id now))
      (when (and (c/fr-flag) (friend-rankable? network))
        (rank-friends (:token auth-map) profile-id (merge existing-follows new-follows))))
    (log/info "Token expired for uid:" uid "on network:" network)))

(defn- sync-job [description sync-fn person-id uid network]
  "Calls sync-fn with person-id, uid and network as args, wrapping execution in
a try..catch and assorted useful logging. sync-fn must be a function of three
arguments.

This function will queue up a job containing the person id, network and auth map
if sync is successful."
  (log/info "Starting" description "for user with uid:" uid "on network:" network)
  (try
    (with-mongo-db (c/rubicon) (c/rubicon-db) (sync-fn person-id (str uid) (str network)))
    (resque/enqueue "users" c/post-sync-job person-id network)
    (catch slingshot.ExceptionInfo e
      (log/error "While syncing uid:" uid "on network:" network ", exception occured:" e))
    (catch Exception e
      (airbraker e)
      (throw e)))
  (log/info "Finished syncing" description "for user with uid:" uid "on network:" network))

(defn sync-attrs-job [person-id uid network]
  (sync-job "attrs" sync-attrs person-id uid network))

(defn sync-all-job [person-id uid network]
  (sync-job "attrs and follows" sync-all person-id uid network))

(ns mendocino
  (:gen-class)
  (:require [clojure.java.jdbc :as db]
            [clojure.tools.logging :as log]
            [clj-logging-config.log4j :as log-config]
            [monger [core :as mg] [collection :as mc] [operators :as mo] [query :as mq]]
            [resque-clojure [core :as resque] [worker :as worker]]
            [copious.auth.core :as auth]
            [mendocino [jobs :as jobs] [config :as c] [version :refer [version]]]
            [clojure.string :as str]
            [clojurewerkz.welle.core :as wc])
  (:import com.mongodb.ReadPreference [sun.misc Signal SignalHandler]))

(defn strip-nil-values
  [map]
  (into {} (remove (comp nil? second) map)))

(defn legacy-token-expiry
  [profile]
  (when-let [expiry (:oauth_expiry profile)]
    (int (/ (.getTime expiry) 1000))))

(defn identity-from-rubicon [user-id profile]
  (strip-nil-values
   (merge (select-keys profile [:uid :token :secret :scope])
          {:provider (:network profile) :user_id user-id
           :token_expires_at (legacy-token-expiry profile)})))

(defn build-person-id-index [users]
  (reduce (fn [m user] (assoc m (:person_id user) (:id user))) {} users))

(defn store-profile-in-flyingdog
  [person-id-to-user-id profile]
  (let [user-id (person-id-to-user-id (:person_id profile))]
    (if (and (:token profile) user-id)
      (auth/store (identity-from-rubicon user-id profile))
      (log/warn "no user id or token, skipping" profile))))

(defn fetch-profiles-from-rubicon
  [users]
  (mg/with-connection (mg/connect (c/rubicon-slave))
    (mg/with-db (mg/get-db (c/rubicon-db))
      (mq/with-collection "profiles" (mq/find {:person_id {mo/$in (map :person_id users)}})
        (mq/read-preference ReadPreference/SECONDARY)))))

(defmacro time-dorun
  [body]
  `(time (dorun ~body)))

(defn migrate-users! [users]
  (time-dorun
   (map #(store-profile-in-flyingdog
          (build-person-id-index users)
          %)
        (fetch-profiles-from-rubicon users))))

(defn user-ids-in-chunks-of
  [batch-size]
  (db/with-connection (c/brooklyn)
    (db/with-query-results user-count [(str "SELECT count(*) FROM users")]
      (for [offset (range 0 (val (first (first user-count))) batch-size)]
        (db/with-connection (c/brooklyn)
          (db/with-query-results users [(str "SELECT person_id, id FROM users LIMIT ? OFFSET ?") batch-size offset]
            (log/info "processing users from offset" offset)
            (doall users)))))))

(defn run-user-migration!
  [& {:keys [batch-size] :or {batch-size 10000}}]
  (log-config/set-logger! :level :debug :out "log/user-migration.log")
  (auth/set-environment! c/env)
  (log/info "starting at" (java.util.Date.))
  (time-dorun (pmap migrate-users! (user-ids-in-chunks-of batch-size)))
  (log/info "done at" (java.util.Date.))
  (System/exit 0))

(defn safe-print-stack-trace
  [throwable]
  (try (let [w (java.io.PrintWriter. *out*)]
         (.printStackTrace throwable w)
         (.flush w))
    (catch Throwable t (log/error "failed to print stack trace with error" t))))

;; Signal Handling ;;

(def graceful-stop-handler
  (proxy [SignalHandler] []
    (handle [signal]
      (log/info "received" signal)
      (try (resque/stop)
           (catch Throwable t (log/error "error stopping:" t) (safe-print-stack-trace t)))
      (log/info "stopped")
      (.exit (Runtime/getRuntime) 0))))

(defn install-signal-handlers
  []
  (let []
    (Signal/handle (Signal. "INT") graceful-stop-handler)
    (Signal/handle (Signal. "TERM") graceful-stop-handler)))

(defn -main []
  (log-config/set-logger! :level :debug :out :console)
  (log/info "Starting Mendocino version" version)
  (install-signal-handlers)
  (auth/set-environment! c/env)
  (worker/configure {:lookup-fn jobs/lookup-fn})
  (resque/configure (c/resque))
  (resque/start c/queues))

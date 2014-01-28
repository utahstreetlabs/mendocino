(ns mendocino.jobs
  (:require [clojure.string :as s]
            [clojure.data.json :as json]
            [clj-logging-config.log4j :as log-config]
            [fb-graph-clj.core :as fb]
            [resque-clojure.worker :refer [*resque-job-data*]]
            [ring.adapter.jetty :as ring]
            [compojure [core :refer [defroutes POST]] [route :as route] [handler :as handler]]
            [slingshot.slingshot :refer [try+ throw+]]
            [copious.auth.core :as auth]
            [resque-clojure.core :as resque]
            [mendocino.util.logger :as log]
            [mendocino [config :as c] [sync :refer [sync-attrs-job sync-all-job]]]))

(defn assoc-kv [m pair]
  (let [[key value] (s/split pair #"=")]
    (assoc m (keyword key) value)))

(defn query-map [qs]
  (reduce assoc-kv {} (s/split qs #"&")))

(defn now [] (long (/ (.getTime (java.util.Date.)) 1000)))

(defn expires-at [duration]
  (+ (now) duration))

(defn remote-fb-exchange [attr-map]
  "Request a new token from Facebook. Return the Facebook graph API response.

On failure, raise the exception raised by fb-graph-clj."
  (let [attrs (merge c/fb-secrets attr-map)]
    (try+
      (fb/pull [:oauth :access_token] attrs)
      (catch Object e
        (log/error "exchange request failed with response:" (:body (:object &throw-context)))
        (throw+ e)))))

(defn token-invalidated? [token]
  "Returns whether token has been invalidated. Returns true if can't validate with facebook, meaning store new token."
  (try+
    (:error (:data (:body (fb/pull [:debug_token] {:input_token token :access_token c/fb-app-token}))))
    (catch Object e
      (log/error "token-invalidated? check failed with response:" (:body (:object &throw-context)))
      true)))

(defn exchange-token [attr-map]
  "Request a new token from Facebook. Return a tuple of the access
token and its expiry.

On failure, raise the exception raised by fb-graph-clj."
  (let [response (remote-fb-exchange attr-map)
        new-info (query-map (:body response))]
    [(:access_token new-info) (expires-at (Integer/parseInt (:expires new-info)))]))

(defn update-token [iden iden->exchange-params]
  "Given an identity and a function from that identity to facebook API
token exchange query parameters, return a hash containing the most
up-to-date token we can get. This token may be the one we already
know about or it may be a new one we fetch from Facebook.

On FB request failure, raise the exception raised by fb-graph-clj."
  (let [[new-token new-expires-at] (exchange-token (iden->exchange-params iden))
        {old-token :token old-expires-at :token_expires_at} iden]
    (log/debug "comparing new expires-at" new-expires-at "to old expires-at" old-expires-at)
    (when (or (nil? old-token) (nil? old-expires-at) (token-invalidated? old-token) (> new-expires-at old-expires-at))
      (merge iden {:token new-token :token_expires_at new-expires-at}))))

(defn token-exchange-params [iden]
  {:grant_type "fb_exchange_token" :fb_exchange_token (:tmp_token iden)})

(defn code-exchange-params [iden]
  {:code (:code iden) :redirect_uri ""})

(defn update-token-log-failure [iden iden->exchange-params]
  "Try to update a token, return nil if the underlying HTTP request returns a 400"
  (try+ (update-token iden iden->exchange-params)
        (catch [:status 400] e
          (log/error "failed to get updated token for" iden " with error " e)
          nil)))

(defn token-update-job
  ([uid iden->exchange-params after-update]
    (when-let [iden (auth/fetch :facebook uid)]
      (when-let [updated (update-token-log-failure iden iden->exchange-params)]
        (let [writeable (merge updated {:provider :facebook :uid uid})]
          (try
            (auth/store (after-update writeable))
            (catch Exception e
              (.printStackTrace e)))))))
  ([uid iden->exchange-params]
     (token-update-job uid iden->exchange-params identity)))

(defn exchange-token-job [person_id uid network]
  (log/debug "exchanging token for facebook uid" uid)
  (token-update-job uid token-exchange-params)
  (resque/enqueue "profiles" "Profiles::SyncAll" person_id uid network))

(defn exchange-code-job [person_id uid network]
  (log/debug "exchanging code for facebook uid" uid)
  (token-update-job uid code-exchange-params #(assoc %1 :code nil))
  (resque/enqueue "profiles" "Profiles::SyncAll" person_id uid network))

(def ruby-class-to-fn {"FlyingDog::Jobs::ExchangeFacebookToken" exchange-token-job
                       "FlyingDog::Jobs::ExchangeFacebookCode" exchange-code-job
                       "Profiles::SyncAll" sync-all-job
                       "Profiles::SyncAttrs" sync-attrs-job})

(defn weasel-wrapper [weasel-id func]
  (fn [& args]
    (binding [log/*log-weasel-id* weasel-id] (apply func args))))

(defn lookup-fn [ruby-class]
  (log/debug "looking up ruby-class" ruby-class)
  (let [entry (find ruby-class-to-fn ruby-class)
        weasel-id (:log_weasel_id (:context *resque-job-data*))]
    (weasel-wrapper weasel-id (val entry))))

(defn response [status body]
  {:headers {"Content-Type" "application/json; charset=utf-8"}
   :body body
   :status status})

(defn no-content []
  (response 204 nil))

(defroutes job-routes
  (POST "/jobs" {body :body}
    (let [{:keys [args class]} (json/read-json (slurp body))]
      (apply (lookup-fn class) args))
    (no-content)))

(defn app-init []
  (auth/set-environment! c/env)
  (log-config/set-logger! :level :debug :out :console))

(def app
  (handler/site job-routes))

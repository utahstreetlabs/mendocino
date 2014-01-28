(ns mendocino.config)

(def env (keyword (or (System/getenv "MENDOCINO_ENV") (System/getenv "MO_ENV") "development")))

(def fb-secrets
  {:client_id "" :client_secret ""})

(def fb-app-token "")

(def twitter-consumer-key "")

(def twitter-consumer-secret "")

(def airbrake-key "")

(def queues ["auth" "profiles"])

(def post-sync-job "Users::AfterNetworkSyncJob")

(def fr-posts-limit 35)

(def flags
  {:development
   {:friend-rank true}
  :test
   {:friend-rank true}
  :staging
   {:friend-rank true}
  :production
   {:friend-rank true}})

(def db
  {:development
   {:brooklyn {:classname "com.mysql.jdbc.Driver"
               :subprotocol "mysql"
               :subname "//localhost:3306/utah_development"
               :user "utah"
               :password ""}
    :redhook-instances 1
    :resque {}
    :rubicon []
    :rubicon-slave {}
    :rubicon-db "rubicon_development"
    :lagunitas []
    :lagunitas-slave {}
    :lagunitas-db "lagunitas_development"}
   :test
   {:brooklyn {:classname "com.mysql.jdbc.Driver"
               :subprotocol "mysql"
               :subname "//localhost:3306/utah_test"
               :user "utah"
               :password ""}
    :redhook-instances 1
    :resque {}
    :rubicon []
    :rubicon-slave {}
    :rubicon-db "rubicon_test"
    :lagunitas []
    :lagunitas-slave {}
    :lagunitas-db "lagunitas_test"}
   :staging
   {:brooklyn {:classname "com.mysql.jdbc.Driver"
               :subprotocol "mysql"
               :subname "//staging.copious.com:3306/utah_staging"
               :user "utah"
               :password ""}
    :redhook-instances 1
    :resque {:host "staging3.copious.com"}
    :rubicon-slave {:host "staging3.copious.com" :port 27017}
    :rubicon [["staging2.copious.com" 27017], ["staging.copious.com" 27017], ["staging3.copious.com" 27017]]
    :rubicon-db "rubicon_staging"}
   :production
   {:brooklyn {:classname "com.mysql.jdbc.Driver"
               :subprotocol "mysql"
               :subname "//db1.copious.com:3306/utah_production"
               :user "utah"
               :password ""}
    :redhook-instances 2
    :resque {:host "resque-redis-master.copious.com"
             :max-workers 4
             :max-shutdown-wait (* 60 1000) ;; milliseconds
             :poll-interval (* 5 1000)}
    :rubicon-slave {:host "mongo2-5-1.copious.com" :port 27017}
    :rubicon [["mongo2-9-1.copious.com" 27017], ["mongo2-10-1.copious.com" 27017]]
    :rubicon-db "rubicon_production"
    :lagunitas [["lag-mongo-1.copious.com" 27017], ["lag-mongo-2.copious.com" 27017], ["lag-mongo-3.copious.com" 27017]]
    :lagunitas-slave {:host "lag-mongo-2.copious.com" :port 27017}
    :lagunitas-db "lagunitas_production"}})

(defn fr-flag [] (:friend-rank (flags env)))
(defn brooklyn [] (:brooklyn (db env)))
(defn redhook-instances [] (:redhook-instances (db env)))
(defn resque [] (:resque (db env)))
(defn rubicon [] (:rubicon (db env)))
(defn rubicon-slave [] (:rubicon-slave (db env)))
(defn rubicon-db [] (:rubicon-db (db env)))
(defn lagunitas [] (:lagunitas (db env)))
(defn lagunitas-slave [] (:lagunitas-slave (db env)))
(defn lagunitas-db [] (:lagunitas-db (db env)))

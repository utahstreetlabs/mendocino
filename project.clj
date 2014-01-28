(load-file "src/mendocino/version.clj")

(defproject mendocino mendocino.version/version
  :description "A daemon for interacting with external networks"

  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/data.json "0.1.2"]
                 [org.clojure/tools.logging "0.2.3"]

                 [clj-logging-config "1.9.6"]
                 [org.slf4j/slf4j-log4j12 "1.6.4"] ;; needed because twitter-api does something weird

                 [clj-time "0.4.4"]
                 [copious/auth "0.3.1-SNAPSHOT"]
                 [org.clojure/java.jdbc "0.2.3"]
                 [mysql/mysql-connector-java "5.1.20"]

                 [ring/ring-core "1.1.1"]
                 [ring/ring-jetty-adapter "1.1.1"]
                 [compojure "1.1.0"]

                 [clj-airbrake "2.0.0"]
                 [com.novemberain/monger "1.0.1"]
                 [fb-graph-clj "1.0.0-SNAPSHOT"]
                 [clj-http "0.5.3"]
                 [twitter-api "0.6.10"]
                 [utahstreetlabs/resque-clojure "0.2.5"]
                 [fs "1.2.0"]]

  :plugins [[lein-midje "2.0.0-SNAPSHOT"] [lein-ring "0.7.1"]]
  :profiles {:dev {:dependencies [[midje "1.4.0"]]}}

  :aliases {"migrate" ["trampoline" "run" "-m" "mendocino/run-user-migration!"]}

  :repositories {"snapshots"
                 {:url "" :username "" :password ""}
                 "releases"
                 {:url "" :username "" :password ""}}

  :ring {:handler mendocino.jobs/app :init mendocino.jobs/app-init :port 4081}
  :main mendocino)

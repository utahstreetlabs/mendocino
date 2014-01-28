(ns mendocino.util.logger
  (:require [clojure.tools.logging :as log]
            [clj-logging-config.log4j :as log-config]))

(def ^:dynamic *log-weasel-id* nil)

(defn log-prefix []
  (let [weasel-id (or *log-weasel-id* "")]
   (str "[" weasel-id "]")))

(defmacro debug [& args]
  `(log/debug (log-prefix) ~@args))

(defmacro trace [& args]
  `(log/trace (log-prefix) ~@args))

(defmacro info [& args]
  `(log/info (log-prefix) ~@args))

(defmacro warn [& args]
  `(log/warn (log-prefix) ~@args))

(defmacro error [& args]
  `(log/error (log-prefix) ~@args))

(defmacro fatal [& args]
  `(log/fatal (log-prefix) ~@args))

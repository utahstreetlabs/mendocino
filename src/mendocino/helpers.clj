(ns mendocino.helpers
  (:import java.util.Date))

(defn format-date [date] (try (Date. date) (catch IllegalArgumentException e nil)))

(defn build-index [maps k]
  (reduce (fn [index map] (assoc index (get map k) map)) {} maps))

(defn vec-self-map [vec]
  (zipmap vec vec))

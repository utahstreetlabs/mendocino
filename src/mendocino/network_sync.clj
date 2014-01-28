(ns mendocino.network_sync)

;; Defines all network specific methods and dispatches based on network argument. Actual method implementations in
;; respective {network}_sync files.

(defmulti normalized-attrs
  "returns a map of user attrs normalized in format which matches mongo profiles"
  (fn [network uid auth-map] network))

(defmulti follower-ids
  "returns a map with follower-ids as keys and follower data as values"
  (fn [network uid auth-map] network))

(defmulti friend-rankable? (fn [network] network))

(defmulti bi-directional? (fn [network] network))

(defmulti auth-always-valid? (fn [network] network))

(defmulti lookup-followers
  "returns a collection of follower attribute maps"
  (fn [network followers-map auth-map] network))

(defmulti redhook-job (fn [network] network))

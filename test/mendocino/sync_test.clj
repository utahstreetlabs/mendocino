(ns mendocino.sync-test
  (:require [monger.core :as mg]
            [mendocino [config :as c] [helpers :refer :all] [network_sync :refer :all]
             [sync :refer :all] [test-support :refer :all] [friend_rank :refer [rank-friends]]]
            [midje.sweet :refer :all]
            [mendocino.mongo :refer [with-mongo-db]])
  (:import java.util.Date))

(fact "returns different keys"
  (diff-map-on-key {:a "abc" :b "def"} {:a "abc" :c "xyz"})  => [{:b "def"} {:c "xyz"}])

(fact "ignores different values"
  (diff-map-on-key {:a "abd" :b "def"} {:a "abc" :c "xyz"})  => [{:b "def"} {:c "xyz"}])

(fact "builds an index in expected format"
  (build-index (list {:a "1" :b "2"} {:a "3" :b "5"})  :a) => {"3" {:a "3", :b "5"} "1" {:a "1", :b "2"}})

(against-background [(before :contents (initialize-db))
                     (around :contents (with-mongo-db (c/rubicon) (c/rubicon-db) ?form))]

  (fact "ensure there is a duplicate follow in db"
    (follow-count-in-mongo (get-profile-id "333" "facebook") (get-profile-id "111" "facebook"))  => 2)

  (with-redefs [follower-ids (constantly {"333" {:name "Existing Profile" :id "333"}
                                            "444" {:name "Jon Doe" :id "444"}})
                normalized-attrs (constantly {:username "narwhal" :name "New Name" :first_name "New"
                                              :last_name "Name" :email "new@copious.com"
                                              :profile_url "http://www.facebook.com/new" :gender "female"
                                              :photo_url "http://graph.facebook.com/111/picture"
                                              :location "San Francisco" :birthday (Date. "01/01/1988")
                                              :updated_at (Date. "01/01/2012")})
                get-auth-unless-expired (constantly {})
                rank-friends (constantly nil)]
    (sync-all-job "3" "111" "facebook"))

  (fact "removes an old follow"
    (contains? (get-existing-follows (get-profile-id "111" "facebook")) "222") => false)

  (fact "create a new profile for follower without one"
    (get-profile-id "444" "facebook") => truthy)

  (fact "creates a new follow for an existing profile"
    (contains? (get-existing-follows (get-profile-id "111" "facebook")) "333") => true)

  (fact "creates a new follow for a new profile"
    (contains? (get-existing-follows (get-profile-id "111" "facebook")) "444") => true)

  (fact "removes duplicate follows on insert"
    (follow-count-in-mongo (get-profile-id "333" "facebook") (get-profile-id "111" "facebook"))  => 1)

  (fact "updates attributes for a user profile"
    (get-profile "111" :facebook) => (contains {:gender "female" :last_name "Name" :name "New Name"
      :updated_at (Date. "01/01/2012") :photo_url "http://graph.facebook.com/111/picture" :location "San Francisco"
      :profile_url "http://www.facebook.com/new" :username "narwhal" :email "new@copious.com"
      :first_name "New" :birthday (Date. "01/01/1988") :api_follows_count 2})))

(ns mendocino.facebook-sync-test
  (:require [mendocino [facebook_sync :refer :all] [network_sync :refer :all]]
            [midje.sweet :refer :all])
  (:import java.util.Date))

(fact "returns correctly normalized attributes"
  (normalized-attrs "facebook" "123" {:token "a"}) =>
  (contains {:gender "male" :last_name "Name" :name "New Name" :updated_at (Date. "01/01/2012")
             :photo_url "http://graph.facebook.com/123/picture" :location "San Francisco"
             :profile_url "http://www.facebook.com/new" :username "narwhal" :email "new@copious.com"
             :first_name "New" :birthday (Date. "01/01/1988")})
  (provided (get-attrs "a") => {:body {:username "narwhal" :name "New Name" :first_name "New"
                                       :last_name "Name" :email "new@copious.com"
                                       :link "http://www.facebook.com/new" :gender "male"
                                       :location {:name "San Francisco"} :birthday "01/01/1988"
                                       :updated_time "01/01/2012"}}))

(fact "lookup-followers return a collection of normalized maps"
  (lookup-followers "facebook" {:a {:name "a" :id "1" :extra-key "junk"}
                                :b {:name "b" :id "2" :junk-key "blah"}} {}) => (just [{:name "a" :uid "1"}
                                                                                       {:name "b" :uid "2"}]))

(ns mendocino.twitter-sync-test
  (:require [mendocino.twitter_sync :refer :all]
            [midje.sweet :refer :all]))

(fact "returns correctly normalized attributes"
  (transform-attrs {:screen_name "narwhal" :name "Barney Rubble" :followers_count 30 :id_str "777"
                    :location "Vancouver" :profile_image_url "http://www.twitter.com/picture"
                    :updated_time "01/01/2012"}) =>
  (contains {:username "narwhal" :name "Barney Rubble" :api_follows_count 30 :uid "777"
             :location "Vancouver" :photo_url "http://www.twitter.com/picture"
             :profile_url "https://twitter.com/narwhal"}))

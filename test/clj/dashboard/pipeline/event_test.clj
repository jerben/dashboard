(ns clj.dashboard.pipeline.event-test
  (:require [dashboard.pipeline.event :as sut]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest testing is]]))

(testing "event"
  (deftest event-type-timeseries
    (is (= :timeseries (sut/event-type {:name "foo" :time 42 :value 2}))))
  (deftest event-type-gauge
    (is (= :gauge (sut/event-type {:name "foo" :value 2}))))
  (deftest event-type-tuple
    (is (= :tuple (sut/event-type {:name1 "foo" :value1 2 :name2 "foo" :value2 2}))))
  (deftest event-type-invalid
    (is (= :invalid (sut/event-type {:name "foo"}))))
  (deftest fold-events
    (let [fun #'dashboard.pipeline.event/fold-events
          events [{:name "commit" :time 1 :value 0}
                  {:name "commit" :time 2 :value 2}
                  {:name "merge-request" :time 1 :value 1}
                  {:name "merge-request" :time 2 :value 11}
                  {:name "commit" :time 4 :value 4}
                  {:name "commit" :time 5 :value 2}]
          expected [{:category :timeseries
                     :data #{{"time" 1 "commit" 0}
                             {"time" 2 "commit" 2}
                             {"time" 4 "commit" 4}
                             {"time" 5 "commit" 2}}}
                    {:category :timeseries
                     :data #{{"time" 1 "merge-request" 1}
                             {"time" 2 "merge-request" 11}}}]]
      (is (= expected (fun events)))))
  (deftest post->event
    (let [event  (sut/post->event {:foo: 2})]
      (is (> 50 (Math/abs (- (get event :time) (System/currentTimeMillis)))))
      (is (= "foo" (get event :name)))))
  (deftest post->event-with-time
    (let [event  (sut/post->event {:foo 2 :time 42})]
      (is (= 42 (get event :time)))
      (is (= "foo" (get event :name))))))

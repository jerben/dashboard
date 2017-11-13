(ns dashboard.event
  (:require [dashboard.inflater :as inflater]
            [dashboard.transformer :as transformer]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [clojure.spec.alpha :as s]))

(timbre/set-level! :trace) 

(s/def ::time integer?)
(s/def ::label keyword?)
(s/def ::value number?)
(s/def ::type #{:time-point})
(s/def ::event (s/keys :req-un [::type ::time ::label ::value]))

;; atom holding all the state as events
(def events (atom []))

(defn epoch->date
  [millis]
  (str (java.util.Date. millis)))

(defn- conj-metrics [metrics [time value]]
  (update metrics :data
          (fn [old]
            [(conj (first old) time)
             (conj (second old) value)])))

(defn- get-idx [state name]
  (loop [idx 0
         state state]
    (cond
      (empty? state) -1
      (= name ((first state) :name)) idx
      :else (recur (inc idx) (rest state)))))

(defprotocol Event
  (fold-event [x state]))

(defrecord TimeSeriesEvent [name time value]
  Event
  (fold-event [x state]
    (let [idx (get-idx state name)]
      (if (= idx -1)
        (conj state {:category :timeseries
                     :name name
                     :data [[time] [value]]})
        (update state idx conj-metrics [time value])))))

(defrecord GaugeEvent [name value]
  Event
  (fold-event [x state]
    (let [idx (get-idx state name)]
      (if (= idx -1)
        (conj state {:category :gauge
                     :name name
                     :data value})
        (assoc-in state [idx :data] value)))))

(defn- extract-name [post]
  (first (filter #(= % :type) (keys post))))

(defmulti post->event (fn [post] (post :type)))

(defmethod post->event :gauge [post]
  (debugf "Received raw post of type gauge: %s" post)
  (let [name (extract-name post)
        value (read-string (get post name))]
    (GaugeEvent. name value)))

(defmethod post->event :default [post]
  (debugf "Received raw post of type timeseries: %s" post)
  (let [name (extract-name post)
        value (read-string (get post name))
        time (or (post :time) (System/currentTimeMillis))]
    (TimeSeriesEvent. name time value)))

(defn- fold-events
  [events]
  (loop [to-process events
         processed []]
    (if (empty? to-process)
      processed
      (recur (rest to-process) (fold-event (first to-process) processed)))))

(defn- make-renderable
  [data]
  (-> data
      (inflater/inflate)
      (transformer/transform)))

(defn- store-event!
  [event]
  (swap! events conj event)
  (tracef "Stored event: %s" event)
  (make-renderable (fold-events @events)))

(defn store-post!
  [post broadcast-state]
  (let [event (post->event post)]
      (broadcast-state (store-event! event))))

(defn get-state!
  []
  (make-renderable (fold-events @events)))

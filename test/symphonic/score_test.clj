(ns symphonic.score-test
  (:require [untangled-spec.core :refer [specification behavior assertions when-mocking]]
            [clojure.core.async :as async]
            [symphonic.score :as src]))

(def example-config
  {:A {:in  [:A]
       :out [:B]}
   :B {:in  [:B]
       :out [:A]}})

(defrecord TestService [config name peer on-receive]
  src/Service
  (get-name [this] name)
  (receive [this topic msg]
    (prn :test-service/receive name topic msg)
    (async/go (async/>! on-receive {:name (src/get-name this) :topic topic :msg msg})))
  (start [this]
    (doseq [topic (:out config)]
      (src/send peer topic (str "Hello " topic))))
  (stop [this]
    this))

(defn make-test-service [c]
  (fn [opts] (map->TestService (assoc opts :on-receive c))))

(specification "A Test"
  (behavior "FIXME, I fail."
    (let [test-chan (async/chan)
          servs (src/make-services example-config src/make-async-peer (make-test-service test-chan))]
      (src/start-services servs)
      (assertions
        (sort-by :name
                 (async/<!!
                   (let [take-test-chan (async/take 2 test-chan)]
                     (async/go-loop [msgs []]
                       (let [[msg] (async/alts! [take-test-chan (async/timeout 2000)])]
                         (if msg
                           (recur (conj msgs msg))
                           msgs))))))
        => [{:name :A :topic :A :msg "Hello :A"}
            {:name :B :topic :B :msg "Hello :B"}])
      (src/stop-services servs))))

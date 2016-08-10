(ns symphonic.redis.peer-test
  (:require [untangled-spec.core :refer [specification behavior assertions when-mocking]]
            [clojure.core.async :as async]
            [symphonic.score :as src]
            [symphonic.redis.peer :as rp]
            [taoensso.carmine :as car]))

(def example-config
  {:A {:in   [:A]
       :out  [:B]
       :host "localhost"
       :port 6379}
   :B {:in   [:B]
       :out  [:A]
       :host "localhost"
       :port 6379}})

(defrecord TestService [config name peer messages]
  src/Service
  (get-name [this] name)

  (receive [this topic msg]
    (swap! messages conj {:topic topic :msg msg}))

  (start [this]
    (doseq [topic (:out config)]
      (src/send peer topic (str "Hello " topic))))

  (stop [this]
    this))

(defn make-test-service [messages]
  (fn [opts] (map->TestService (assoc opts :messages messages))))

(specification "A Test"
               (behavior "FIXME, I fail."
                         (doseq [_ (range 50)]
                           (let [messages (atom [])
                                 servs    (src/make-services example-config
                                                             rp/make-redis-peer
                                                             (make-test-service messages))]
                             (src/start-services servs)
                             (Thread/sleep 100) ;; needed for initialization.
                             (assertions
                              @messages
                              =fn=> (fn [x] (or (= x [{:topic :A :msg "Hello :A"}
                                                      {:topic :B :msg "Hello :B"}])
                                                (= x [{:topic :B :msg "Hello :B"}
                                                      {:topic :A :msg "Hello :A"}]))))
                             @messages
                             (src/stop-services servs)))))

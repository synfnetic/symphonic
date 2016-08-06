(ns symphonic.core
  (:require [clojure.core.async :as async]))


(defprotocol Peer
  (init [this service]
    "Given a service, initialize peer connection and set up receive.")
  (send [this topic message]
    "Send message on an initialized peer for a topic.")
  (close [this service]
    "Close peer connection. Cleanup resources."))


(defprotocol Service
  (get-name [this]
    "The sqqervices name.")
  (receive [this topic message]
    "Handler for receiving messages.")
  (start [this]
    "Start the service")
  (stop [this]
    "Stop the service"))


(def config {:A {:in  [:A]
                 :out [:B]}
             :B {:in  [:B]
                 :out [:A]}})


(defrecord DevPeer [input pub config close?]
  Peer
  (init [this service]
    (let [serv-name (get-name service)
          serv-conf (get config serv-name)
          chans     (map (fn [topic]
                           (let [out-chan (async/chan)]
                             (async/sub pub topic out-chan)
                             out-chan))
                         (:in serv-conf))]
      (async/go-loop []
        (let [[{:keys [topic msg] :as result}] (async/alts! chans)]
          (prn "From alts!!" result)
          (receive service topic msg))
        (if (get @close? serv-name)
          (doseq [c chans]
            (async/close! c))
          (recur)))))

  (send [this topic message]
    (prn "SENDING " topic message)
    (async/>!! input {:topic topic :msg message}))

  (close [this service]
    (swap! close? assoc (get-name service) true)))

(defn make-dev-peer [config]
  (let [in  (async/chan)
        pub (async/pub in :topic)]
    (map->DevPeer {:input  in
                   :pub    pub
                   :config config
                   :close? (atom {})})))


(defrecord SimpleService [config name peer]
  Service
  (get-name [this] name)
  (receive [this topic message]
    (prn name topic message)
    (doseq [topic (get-in config [name :out])]
      (prn "Receiving " topic message)
      (Thread/sleep 1000)
      (send peer topic message)))
  (start [this]
    (do (init peer this)
        (doseq [topic (get-in config [name :out])]
          (prn "Sending " topic)
          (send peer topic (str "Hello " topic)))))
  (stop [this]
    (close peer this)))


(defn make-dev-services []
  (let [dev-peer (make-dev-peer config)]
    (prn dev-peer)
    (map (fn [[name]]
           (->SimpleService config name dev-peer))
         config)))

(defn start-services [services]
  (doseq [s services]
    (prn s)
    (.start (Thread. (fn [] (start s))))))

(defn stop-services [services]
  (doseq [s services]
    (prn s)
    (stop s)))

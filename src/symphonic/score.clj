(ns symphonic.score
  (:refer-clojure :exclude [send])
  (:require [clojure.core.async :as async]))

(defprotocol Peer
  (init [this]
    "Given a service, initialize peer connection and set up receive.")
  (send [this topic message]
    "Send message on an initialized peer for a topic.")
  (close [this]
    "Close peer connection. Cleanup resources."))

(defprotocol Service
  (get-name [this]
    "The services name.")
  (receive [this topic message]
    "Handler for receiving messages.")
  (start [this]
    "Start the service.")
  (stop [this]
    "Stop the service."))

(defn make-services [config peer-ctor serv-ctor]
  (mapv (fn [[serv-name cfg]]
          (let [serv (serv-ctor {:config cfg :name serv-name})]
            (assoc serv :peer (peer-ctor cfg serv))))
        config))

(defn start-services [services]
  (doseq [s services]
    (async/thread (init (:peer s)) (start s))))

(defn stop-services [services]
  (doseq [s services]
    (close (:peer s))
    (stop s)))

(defrecord AsyncPeer [input pub config close? service]
  Peer
  (init [this]
    (let [chans (mapv (fn [topic]
                        (let [out-chan (async/chan)]
                          (async/sub pub topic out-chan)
                          out-chan))
                      (:in config))]
      (async/go-loop []
        (let [[{:keys [topic msg] :as result}] (async/alts! chans)]
          (when result
            (receive service topic msg)))
        (if @close?
          (doseq [c chans]
            (async/close! c))
          (recur)))
      (Thread/sleep 100)
      this))

  (send [this topic message]
    (async/go (async/>! input {:topic topic :msg message})))

  (close [this]
    (reset! close? true)
    (async/close! input)))

(defn make-async-peer [config service]
  (map->AsyncPeer {:config config, :service service, :close? (atom false)}))

(defn make-async-services [config peer-ctor serv-ctor]
  (let [in (async/chan), pub (async/pub in :topic)]
    (make-services config (comp #(assoc % :input in :pub pub) peer-ctor) serv-ctor)))

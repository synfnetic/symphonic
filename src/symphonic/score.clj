(ns symphonic.score
  (:refer-clojure :exclude [send])
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
    "The services name.")
  (receive [this topic message]
    "Handler for receiving messages.")
  (start [this]
    "Start the service.")
  (stop [this]
    "Stop the service."))

(defrecord AsyncPeer [input pub config close?]
  Peer
  (init [this service]
    (let [serv-name (get-name service)
          serv-conf (get config serv-name)
          chans     (mapv (fn [topic]
                            (let [out-chan (async/chan)]
                              (async/sub pub topic out-chan)
                              out-chan))
                          (:in serv-conf))]
      (async/go-loop []
        (prn :peer.init/go-loop.for serv-name)
        (let [[{:keys [topic msg] :as result}] (async/alts! chans)]
          (prn :peer.init/alts! result)
          (when result
            (receive service topic msg)))
        (if (get @close? serv-name)
          (doseq [c chans]
            (async/close! c))
          (recur)))))

  (send [this topic message]
    (prn :peer/send topic message)
    (async/go (async/>! input {:topic topic :msg message})))

  (close [this service]
    (prn :peer/close)
    (swap! close? assoc (get-name service) true)
    (async/close! input)))

(defn make-async-peer [config]
  (let [in  (async/chan)
        pub (async/pub in :topic)]
    (map->AsyncPeer {:input  in
                     :pub    pub
                     :config config
                     :close? (atom {})})))

(defn make-services [config peer-ctor serv-ctor]
  (let [peer (peer-ctor config)]
    (mapv (fn [[serv-name cfg]]
            (serv-ctor {:config cfg :name serv-name
                        :peer peer}))
          config)))

(defn start-services [services]
  (doseq [s services]
    (prn :starting (get-name s))
    (.start (Thread. (fn [] (init (:peer s) s) (Thread/sleep 250) (start s))))))

(defn stop-services [services]
  (doseq [s services]
    (prn :stopping (get-name s))
    (close (:peer s) s)
    (stop s)))

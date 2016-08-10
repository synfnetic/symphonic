(ns symphonic.redis.peer
  (:require [symphonic.score :refer [Peer] :as score]
            [taoensso.carmine :as car]))


(defrecord RedisPeer [config service listener redis-conn]
  Peer
  (init [this] this)

  (send [this topic message]
    (car/wcar redis-conn
              (car/publish (name topic) message)))

  (close [this]
    (car/with-open-listener listener
      (car/unsubscribe))
    (car/close-listener listener)))

(defn make-redis-peer [config service]
  (let [{:keys [host port in]
         :or   {host "localhost"
                port 6379}} config
        redis-conn          {:pool {} :spec {:host host :port port}}
        listener            (car/with-new-pubsub-listener (:spec redis-conn)
                              (reduce (fn [acc topic]
                                        (assoc acc
                                               (name topic)
                                               (fn [[cmd tpc msg]]
                                                 (when-not (= cmd "subscribe")
                                                  (score/receive service topic msg)))))
                                      {}
                                      in)
                              ;; subscribe to `in`
                              (apply car/subscribe (map name in)))]
    (map->RedisPeer {:config     config
                     :service    service
                     :redis-conn redis-conn
                     :listener   listener})))

(ns math-balancer.system
  (:require
    [com.stuartsierra.component :as component]
    [math-balancer.storage :as storage]
    [math-balancer.authorization :as auth]))

(defn new-system [config]
  (component/system-map
    :storage (storage/new-storage config)
    :auth (component/using
            (auth/new-auth config)
            [:storage])))
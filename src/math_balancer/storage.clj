(ns math-balancer.storage
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [clojurewerkz.cassaforte.client :as cc]
            [clojurewerkz.cassaforte.policies :as cp]
            [clojurewerkz.cassaforte.cql :as cql]
            [clojure.tools.logging :as log]
            [clojurewerkz.cassaforte.query :refer [where columns using]]
            [clojurewerkz.scrypt.core :as sc]
            [clojure.set :refer [rename-keys]])
  (:import (java.util UUID))
  (:import [com.datastax.driver.core.exceptions NoHostAvailableException]
           (java.util UUID)
           (com.datastax.driver.core.policies DCAwareRoundRobinPolicy)))


(defn check-session [{conn :conn :as auth} session-id]
  (-> (cql/get-one conn "sessions"
                   (columns :session_id)
                   (where [[= :session_id (UUID/fromString session-id)]]))
      (boolean)))

(defn check-login-password [{conn :conn } login password]
  (let [{status :status
         phash :password :as user} (cql/get-one conn "users"
                                                (columns :password :status :is_admin :login)
                                                (where [[= :login login]]))]
    (and user
         (= status "active")
         (sc/verify password phash))))

(defn try-connect-times [times delay-ms nodes keyspace opts]
  (let [result (try
                 (cc/connect nodes keyspace opts)
                 (catch NoHostAvailableException ex ex))]
    (cond
      (and (instance? Exception result) (zero? times)) (throw result)
      (instance? Exception result) (do
                                     (log/warnf "Failed to connect to Cassandra, will retry after %d ms" delay-ms)
                                     (Thread/sleep delay-ms)
                                     (recur (dec times) delay-ms nodes keyspace opts))
      :else result)))

(defrecord Storage [conn
                    storage-nodes
                    storage-keyspace
                    storage-user
                    storage-password
                    storage-default-dc]
  component/Lifecycle

  (start [component]
    (let [conn (try-connect-times 1000
                                  1000
                                  storage-nodes
                                  storage-keyspace
                                  {:credentials           {:username storage-user
                                                           :password storage-password}
                                   :reconnection-policy   (cp/constant-reconnection-policy 100)
                                   :load-balancing-policy (DCAwareRoundRobinPolicy. storage-default-dc 2)})]
      (log/info "Storage started")
      (assoc component :conn conn)))

  (stop [component]
    (when conn
      (cc/disconnect conn))
    (log/info "Storage stopped")
    (assoc component
      :conn nil)))


(def StorageSchema
  {:storage-nodes    [s/Str]
   :storage-keyspace s/Str
   :storage-user     s/Str
   :storage-default-dc s/Str
   :storage-password s/Str})

(defn new-storage [m]
  (as-> m $
        (select-keys $ (keys StorageSchema))
        (s/validate StorageSchema $)
        (map->Storage $)))


(ns math-balancer.storage
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [yesql.core :refer [defqueries]]
            [dire.core :refer [with-handler!]]
            [clj-time.core :as time]
            [clj-time.coerce :refer [from-date to-timestamp]]
            [clojurewerkz.scrypt.core :as sc])
  (:import (java.util UUID Date)
           (java.sql SQLException)))

(def sql-exception-handler
  (fn [e & args]
    (log/error e "Exception occured during file writing into db")
    (loop [ne (.getNextException e)]
      (when ne
        (log/error ne "next exception:")
        (recur (.getNextException ne))))))

(defqueries "sql/session.sql")

(defn get-expire [{spec :spec} session-id]
  (-> (get-expire* {:session_id (UUID/fromString session-id)} {:connection spec})
      first
      :expire))

(with-handler! #'get-expire
  SQLException
  sql-exception-handler)

(defn is-valid-session? [st session-id]
  (let [expire (get-expire st session-id)]
    (time/after? (from-date expire) (from-date (Date.)))))

(defn get-user-with-password [{spec :spec} login]
  (first (get-user-with-password* {:login login} {:connection spec})))

(with-handler! #'get-user-with-password
  SQLException
  sql-exception-handler)

(defn check-login-password [storage login password]
  (let [{status :status
         phash :password
         :as user} (get-user-with-password storage login)]
    (and user
         (= status "active")
         (sc/verify password phash))))

(defrecord Storage [storage-host
                    storage-user
                    storage-pass
                    storage-db
                    spec]
  component/Lifecycle

  (start [component]
    (let [spec {:classname   "org.postgresql.Driver"
                :subprotocol "postgresql"
                :subname     (format "//%s/%s" storage-host storage-db)
                :user        storage-user
                :password    storage-pass}]
      (log/info "Storage started")
      (assoc component :spec spec)))

  (stop [component]
    (log/info "Storage stopped")
    (assoc component :spec nil)))


(def StorageSchema
  {:storage-host s/Str
   :storage-user s/Str
   :storage-pass s/Str
   :storage-db   s/Str})

(defn new-storage [m]
  (as-> m $
        (select-keys $ (keys StorageSchema))
        (s/validate StorageSchema $)
        (map->Storage $)))

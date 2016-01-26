(ns math-balancer.authorization
  (require [clojure.string :refer [lower-case]]
           [clojure.data.codec.base64 :as b64]
           [com.stuartsierra.component :as component]
           [clojure.tools.logging :as log]
           [schema.core :as s]
           [math-balancer.storage :as storage]
           [clojure.core.cache :as cache]))

(defn decode-base64-str [base64-str]
  (when (string? base64-str)
    (String. #^bytes (b64/decode (.getBytes base64-str)))))

(defn parse-login-password [credentials]
  (drop 1 (re-matches #"(.*?):(.*)" credentials)))

(defn extract-request-credentials [req]
  (let [auth-token (-> req :headers (get "Authorization"))
        [_ auth-type credentials] (re-matches #"(?i)(betengines|basic) (.*)" (or auth-token ""))
        authorization (some-> auth-type (lower-case) (keyword))]
    [authorization credentials]))

(def async-action? #{"calculate" "profile"})

(defn white-listed? [auth creds]
  (println "white-listed?" creds (get @(:white-list auth) creds) @(:white-list auth))
  (get @(:white-list auth) creds))

(defn black-listed? [auth creds]
  (println "black-listed? STUB" creds))                             ;; guest:guest

(defn black-list! [auth creds]
  (println "black-list! STUB" creds))

(defn white-list! [auth creds]
  (swap! (:white-list auth) assoc creds true)
  (println "white-listing" creds (:white-list auth)))

(defn check-basic-auth [auth creds async?]
  (let [[login password] (-> creds
                             (decode-base64-str)
                             (parse-login-password))
        valid-creds? (storage/check-login-password (:storage auth) login password)]
    (if valid-creds?
      (white-list! auth creds)
      (black-list! auth creds))
    valid-creds?))

(defn check-betengines-auth [auth creds async?]
  (let [session-id (decode-base64-str creds)
        valid-creds? (storage/check-session (:storage auth) session-id)]
    (if valid-creds?
      (white-list! auth creds)
      (black-list! auth creds))
    valid-creds?))

(defn check-credentials [auth action auth-type creds]
  (case auth-type
    :basic (check-basic-auth auth creds (async-action? action))
    :betengines (check-betengines-auth auth creds (async-action? action))
    false))

(defrecord Auth [white-list
                 white-list-ttl
                 black-list
                 black-list-ttl]
  component/Lifecycle

  (start [component]
    (log/info "Auth started")
    (assoc component
      :white-list (atom (cache/ttl-cache-factory {} :ttl white-list-ttl))
      :black-list (atom (cache/ttl-cache-factory {} :ttl black-list-ttl))))

  (stop [component]
    (log/info "Auth stopped")
    (assoc component
      :white-list nil
      :black-list nil)))

(def AuthSchema
  {:white-list-ttl s/Int
   :black-list-ttl s/Int})

(defn new-auth [m]
  (as-> m $
        (select-keys $ (keys AuthSchema))
        (s/validate AuthSchema $)
        (map->Auth $)))




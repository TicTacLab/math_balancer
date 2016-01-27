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
  (contains? @(:white-list auth) creds))

(defn white-list! [auth creds]
  (swap! (:white-list auth) assoc creds true))

(defn black-listed? [auth creds]
  (contains? @(:black-list auth) creds))

(defn black-list! [auth creds]
  (swap! (:black-list auth) assoc creds true))

(defn check-session-creds [auth creds]
  (let [session-id (decode-base64-str creds)]
    (and (seq session-id )
         (storage/check-session (:storage auth) session-id))))

(defn check-user-creds [auth creds]
  (let [[login password] (-> creds
                             (decode-base64-str)
                             (parse-login-password))]
    (and login
         password
         (storage/check-login-password (:storage auth) login password))))

(defn validate-credentials [auth auth-type creds]
  (let [valid-creds? (case auth-type
                       :basic (check-user-creds auth creds)
                       :betengines (check-session-creds auth creds)
                       false)]
    (if valid-creds?
      (white-list! auth creds)
      (black-list! auth creds))
    valid-creds?))

(defn check-credentials [auth action auth-type creds]
  (if (async-action? action)
    (do (.start
          (Thread. (fn [] (validate-credentials auth auth-type creds))))
        true)
    (validate-credentials auth auth-type creds)))


(defrecord Auth [white-list
                 white-list-ttl
                 black-list
                 black-list-ttl]
  component/Lifecycle

  (start [component]
    (log/info "Auth started")
    (assoc component
      :white-list (atom (cache/ttl-cache-factory {} :ttl (* 1000 white-list-ttl)))
      :black-list (atom (cache/ttl-cache-factory {} :ttl (* 1000 black-list-ttl)))))

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




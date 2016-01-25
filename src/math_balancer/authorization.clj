(ns math-balancer.authorization
  (require [clojure.string :refer [lower-case]]
           [clojure.data.codec.base64 :as b64]))

(defn decode-base64-str [base64-str]
  (when (string? base64-str)
    (String. (b64/decode (.getBytes base64-str)))))

(defn parse-user-password [credentials]
  (let [[_ user password] (re-matches #"(.*?):(.*)" credentials)]
    {:user user
     :password password}))

(defn parse-auth-token [auth-token]
  (let [[_ auth-type raw-creds] (re-matches #"(?i)(betengines|basic) (.*)" (or auth-token ""))
        authorization (when auth-type (lower-case auth-type))
        credentials (decode-base64-str raw-creds)]
    (condp = authorization
      "basic" (merge {:authorization :basic} (parse-user-password credentials))
      "betengines" {:authorization :betengines :session credentials}
      nil {:authorization :none})))

(defn check-user-and-password [user password]
  (and (= user "Aladdin")
       (= password "open sesame")))

(defn check-session [session]
  (= session "550e8400-e29b-41d4-a716-446655440000"))

(defn check-credentials [auth-token]
  (let [{auth-type :authorization :as creds} (parse-auth-token auth-token)]
    (condp = auth-type
      :basic (check-user-and-password (:user creds) (:password creds))
      :betengines (check-session (:session creds))
      :none false)))
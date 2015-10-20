(ns math-balancer.sessions
  (:require [cheshire.core :as json]
            [clojure.tools.logging :as log]))

(defn max-sessions-count [cfg engine-addr]
  (->> (:engines cfg)
       (filter #(= engine-addr (:server %)))
       (first)
       (:sessions-limit)))

(defn add-session [state session-id engine-addr]
  (-> state
      (update-in [:sessions] assoc session-id engine-addr)
      (update-in [:counts engine-addr] (fnil inc 0))))

(defn read-engine-response
  [{:keys [body status error]}]
  (cond
    (not (nil? error)) (log/error error "Network error occured")
    (not= status 200) (log/error "Invalid response" body)
    :else (try
            {:session-ids (get (json/parse-string body false) "data")}
            (catch Exception e
              (log/error e "Failed to parse json response" body)))))

(defn sessions-set [state [engine-addr id-resp]]
  (if-let [resp (read-engine-response @id-resp)]
    (let [{:keys [session-ids]} resp
          sessions (into {} (map (fn [id] [id engine-addr]) session-ids))]
      (-> state
          (update-in [:sessions] merge sessions)
          (assoc-in [:counts engine-addr] (count session-ids))))
    state))



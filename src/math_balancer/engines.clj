(ns math-balancer.engines
  (:require [math-balancer.sessions :as sessions]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]))

(defn poll-engines [state cfg]
  (if-let [responses (->> (:engines cfg)
                          (map :server)
                          (mapv (fn [addr]
                                  [addr (http/get (format "http://%s/sessions" addr)
                                                  {:timeout (:poll-timeout-ms cfg)})]))
                          (not-empty))]
    (reduce
      sessions/sessions-set
      (assoc state :sessions {} :counts {})
      responses)
    (log/error "Poll error")))

(defn has-alive-engines? [state]
  (seq (:counts state)))

(defn get-best-engine [state cfg]
  (when (seq (:counts state))
    (let [[engine s-count] (apply min-key val (:counts state))]
      (when (< s-count (sessions/max-sessions-count cfg engine))
        engine))))

(defn get-assigned-engine [state event-id]
  (get-in state [:sessions event-id] nil))

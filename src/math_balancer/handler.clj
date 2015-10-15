(ns math-balancer.handler
  (:require [nginx.clojure.core :as nginx]
            [org.httpkit.client :as http]
            [cheshire.core :as json]))

(def ^:const uri-pattern #"/files/(?<model>.+)/(?<event>.+)/.*")

(def ^:const engines ["192.168.167.2:3000"])

(defn max-sessions-count [engine]
  1)

(def state-atom (atom {:sessions {} :counts {}}))

(defn add-session [state session-id engine]
  (-> state
      (update-in [:sessions] assoc session-id engine)
      (update-in [:counts engine] inc)))

(defn- engine-sessions [engine]
  (http/get (format "http://%s/sessions" engine)))

(defn sessions-check [state engines]
  (let [session-reqs (doall (for [e engines]
                             [e (engine-sessions e)]))
        engine-sessions (for [[engine ids-req] session-reqs]
                          (let [{:keys [body status]} @ids-req]
                            {:engine      engine
                             :session-ids (when (= status 200)
                                            (try
                                              (:data (json/parse-string body true))
                                              (catch Exception _ nil)))}))]
    (assoc state
      :sessions (into {} (for [{:keys [engine session-ids]} engine-sessions
                               session-id session-ids]
                           [session-id engine]))
      :counts (into {} (for [{:keys [engine session-ids]} engine-sessions]
                         [engine (count session-ids)])))))

(defn- get-event-id [uri]
  (let [[_ m-id e-id] (re-matches uri-pattern uri)]
    e-id))

(defn get-best-engine [state]
  ""
  (when (not (empty? (:counts state)))
    (let [[engine s-count] (apply min-key val (:counts state))]
      (when (< s-count (max-sessions-count engine))
        engine))))

(defn get-engine [uri]
  (let [event-id (get-event-id uri)
        engine (get (:sessions @state-atom) event-id nil)]
    (if engine
      engine
      (let [new-engine (get-best-engine @state-atom)]
        (when (not (nil? new-engine))
          (swap! state-atom add-session event-id new-engine))
        new-engine))))

(defn init-handler [_]
  "nginx-clojure jvm init handler"
  (future
    (loop []
      (swap! state-atom sessions-check engines)
      (prn "Health check")
      (Thread/sleep 5000)
      (recur)))
  {:status 200})

(defn handler [req]
  "nginx-clojure rewrite handler"
  (let [engine-addr (get-engine (:uri req))]
    (if (nil? engine-addr)
      {:status 400 :body "No service available"}
      (do
        (nginx/set-ngx-var! req "engine" (format "http://%s" engine-addr))
        nginx/phase-done))))

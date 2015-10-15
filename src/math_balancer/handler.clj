(ns math-balancer.handler
  (:require [nginx.clojure.core :as nginx]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]))

(def ^:const uri-pattern #"/files/(?<model>.+)/(?<event>.+)/.*")

(def state-atom (atom {:sessions {}
                       :counts {}
                       :cfg {:engines []
                             :interval-ms nil}
                       :poll-future nil}))

(defn max-sessions-count [state engine]
  (let [engines (get-in state [:cfg :engines])
        engine-entry (first (filter #(= engine (:server %)) engines))]
    (:sessions-limit engine-entry)))

(defn add-session [state session-id engine]
  (-> state
      (update-in [:sessions] assoc session-id engine)
      (update-in [:counts engine] (fnil inc 0))))

(defn- read-engine-response
  [engine {:keys [body status error]}]
  (if error
    (log/error error "Network error occured")
    {:engine      engine
     :session-ids (if (not= status 200)
                    (log/error "Invalid response" body)
                    (try
                      (get (json/parse-string body false) "data")
                      (catch Exception e
                        (log/error e "Failed to parse json response" body))))}))

(defn sessions-check [state engine-reqs]
  (let [engine-sessions (for [[engine ids-req] engine-reqs]
                          (read-engine-response engine @ids-req))]
    (assoc state
      :sessions (into {} (for [{:keys [engine session-ids]} engine-sessions
                               session-id session-ids]
                           [session-id engine]))
      :counts (into {} (for [{:keys [engine session-ids]} engine-sessions]
                         [engine (count session-ids)])))))

(defn poll-engines [state]
  (let [engines (map :server (:engines (:cfg state)))
        engine-reqs (doall
                      (for [e engines]
                        (let [url (format "http://%s/sessions" e)
                              req (http/get url)]
                          [e req])))]
    (sessions-check state engine-reqs)))

(defn get-event-id [uri]
  (let [[_ m-id e-id] (re-matches uri-pattern uri)]
    e-id))

(defn get-best-engine [state]
  (when (not (empty? (:counts state)))
    (let [[engine s-count] (apply min-key val (:counts state))]
      (when (< s-count (max-sessions-count state engine))
        engine))))

(defn read-cfg [config-file]
  (-> (slurp config-file)
      (json/parse-string true)))

(defn get-engine! [state-atom uri]
  (let [event-id (get-event-id uri)
        engine (get (:sessions @state-atom) event-id nil)]
    (if engine
      engine
      (let [new-engine (get-best-engine @state-atom)]
        (when (not (nil? new-engine))
          (swap! state-atom add-session event-id new-engine))
        new-engine))))

(defn init-handler
  "nginx-clojure jvm init handler"
  [_]
  (let [cfg (read-cfg "conf/math-balancer.json")
        f (future
            (loop []
              (swap! state-atom poll-engines)
              (log/info "Performed engines poll" (:counts @state-atom))
              (Thread/sleep (:poll-interval-ms cfg))
              (recur)))]
    (swap! state-atom assoc :cfg cfg :poll-future f)
    (log/info "Service launched using config" cfg)
    {:status 200}))

(defn handler [req]
  "nginx-clojure rewrite handler"
  (let [engine-addr (get-engine! state-atom (:uri req))]
    (if (nil? engine-addr)
      {:status 400 :body "No service available"}
      (do
        (nginx/set-ngx-var! req "engine" (format "http://%s" engine-addr))
        nginx/phase-done))))

(defn stop-poll-future! []
  (future-cancel (:poll-future @state-atom)))
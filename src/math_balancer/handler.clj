(ns math-balancer.handler
  (:require [nginx.clojure.core :as nginx]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]))

(def ^:const uri-pattern #"/files/(?<model>.+)/(?<event>.+)/.*")

(def state-atom (atom {:sessions {}
                       :counts {}
                       :poll-future nil}))

(def cfg-atom (atom {:engines []
                     :interval-ms nil}))

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
            (get (json/parse-string body false) "data")
            (catch Exception e
              (log/error e "Failed to parse json response" body)))))

(defn sessions-set [state [engine-addr id-resp]]
  (let [session-ids (read-engine-response @id-resp)
        sessions (into {} (map (fn [id] [id engine-addr]) session-ids))]
    (-> state
        (update-in [:sessions] merge sessions)
        (assoc-in [:counts engine-addr] (count session-ids)))))

(defn poll-engines [state cfg]
  (if-let [responses (->> (:engines cfg)
                          (map :server)
                          (mapv (fn [addr]
                                  [addr (http/get (format "http://%s/sessions" addr))]))
                          (not-empty))]
    (reduce
      sessions-set
      (assoc state :sessions {} :counts {})
      responses)
    (log/error "Poll error")))

(defn read-cfg [config-file]
  (-> (slurp config-file)
      (json/parse-string true)))

(defn get-event-id [uri]
  (let [[_ m-id e-id] (re-matches uri-pattern uri)]
    e-id))

(defn get-best-engine [state cfg]
  (when (not (empty? (:counts state)))
    (let [[engine s-count] (apply min-key val (:counts state))]
      (when (< s-count (max-sessions-count cfg engine))
        engine))))

(defn get-assigned-engine [state event-id]
  (get-in state [:sessions event-id] nil))

(defn init-handler
  "nginx-clojure jvm init handler"
  [_]
  (let [cfg (read-cfg "conf/math-balancer.json")
        f (future
            (loop []
              (swap! state-atom poll-engines cfg)
              (log/info "Performed engines poll" (:counts @state-atom))
              (Thread/sleep (:poll-interval-ms cfg))
              (recur)))]
    (reset! cfg-atom cfg)
    (swap! state-atom assoc :poll-future f)
    (log/info "Service launched using config" cfg)
    {:status 200}))

(defn proxy-pass [req engine-addr]
  (nginx/set-ngx-var! req "engine" (format "http://%s" engine-addr))
  nginx/phase-done)

(defn handler [req]
  "nginx-clojure rewrite handler"
  (let [event-id (get-event-id (:uri req))
        engine-addr (get-assigned-engine @state-atom event-id)]
    (if engine-addr
      (proxy-pass req engine-addr)
      (let [best-engine-addr (get-best-engine @state-atom @cfg-atom)]
        (if best-engine-addr
          (do
            (swap! state-atom add-session event-id best-engine-addr)
            (proxy-pass req best-engine-addr))
          {:status 400, :body "No service available"})))))

(defn stop-poll-future! []
  (future-cancel (:poll-future @state-atom)))
(ns math-balancer.handler
  (:import java.util.Timer
           java.util.TimerTask)
  (:require [nginx.clojure.core :as nginx]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [clojure.string :as s]))

(def state-atom (atom {:sessions {}
                       :counts {}}))

(def cfg-atom (atom {:engines []
                     :interval-ms nil}))

(def poll-timer (atom nil))

(defn init-poll-timer! []
  (reset! poll-timer (Timer. true)))

(defn stop-poll-timer! []
  (.cancel @poll-timer)
  (.purge @poll-timer))

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

(defn poll-engines [state cfg]
  (if-let [responses (->> (:engines cfg)
                          (map :server)
                          (mapv (fn [addr]
                                  [addr (http/get (format "http://%s/sessions" addr)
                                                  {:timeout (:poll-timeout-ms cfg)})]))
                          (not-empty))]
    (reduce
      sessions-set
      (assoc state :sessions {} :counts {})
      responses)
    (log/error "Poll error")))

(defn read-cfg [config-file]
  (-> (slurp config-file)
      (json/parse-string true)))

(defn get-url [uri]
  (let [[_ _ files model-id event-id action]
        (s/split uri #"/")]
    (if action
      [files model-id event-id action]
      [files model-id event-id])))

(defn make-url [engine-addr req]
  (let [url (s/join "/" (get-url req))
        path (str "http://" engine-addr "/" url)]
    path))

(defn get-best-engine [state cfg]
  (when (not (empty? (:counts state)))
    (let [[engine s-count] (apply min-key val (:counts state))]
      (when (< s-count (max-sessions-count cfg engine))
        engine))))

(defn get-assigned-engine [state event-id]
  (get-in state [:sessions event-id] nil))

(defn poll-engines-task []
  (proxy [TimerTask] []
    (run []
      (swap! state-atom poll-engines @cfg-atom)
      (log/info "Performed engines poll" (:counts @state-atom)))))

(defn init-handler
  "nginx-clojure jvm init handler"
  [_]
  (let [cfg (read-cfg (get env :balancer-config))
        interval (:poll-interval-ms cfg)]
    (reset! cfg-atom cfg)
    (init-poll-timer!)
    (.scheduleAtFixedRate @poll-timer (poll-engines-task) (long 0) (long interval))
    (log/info "Service launched using config" cfg)
    {:status 200}))



(defn proxy-pass [req engine-addr]
  (nginx/set-ngx-var! req "engine" (make-url engine-addr (:uri req)))
  nginx/phase-done)

(defn handler [req]
  "nginx-clojure rewrite handler"
  (let [url  (->> req :uri get-url )
        [_ _ event-id _] url
        engine-addr (get-assigned-engine @state-atom event-id)]
    (if engine-addr
      (proxy-pass req engine-addr)
      (let [best-engine-addr (get-best-engine @state-atom @cfg-atom)]
        (if best-engine-addr
          (do
            (swap! state-atom add-session event-id best-engine-addr)
            (proxy-pass req best-engine-addr))
          {:status 400, :body "No service available"})))))
(ns math-balancer.handler
  (:require [nginx.clojure.core :as nginx]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [math-balancer.scheduler :as scheduler]
            [math-balancer.sessions :as sessions]
            [math-balancer.tools :as tools]
            [math-balancer.engines :as engines]))

(def cfg-atom (atom {:engines []
                     :interval-ms nil}))

(def state-atom (atom {:sessions {}
                       :counts {}}))

(defn polling-task
  (swap! state-atom engines/poll-engines @cfg-atom)
  (log/info "Performed engines poll" (:counts @state-atom)))

(defn init-handler
  "nginx-clojure jvm init handler"
  [_]
  (let [cfg (tools/read-cfg (get env :balancer-config))
        interval (:poll-interval-ms cfg)]
    (reset! cfg-atom cfg)
    (scheduler/run-scheduler polling-task interval)
    (log/info "Service launched using config" cfg)
    {:status 200}))

(defn proxy-pass [req engine-addr]
  (nginx/set-ngx-var! req "engine" (tools/make-url engine-addr (:uri req)))
  nginx/phase-done)

(defn handler [req]
  "nginx-clojure rewrite handler"
  (let [url  (->> req :uri tools/get-url )
        [_ _ event-id _] url
        engine-addr (engines/get-assigned-engine @state-atom event-id)]
    (if engine-addr
      (proxy-pass req engine-addr)
      (let [best-engine-addr (engines/get-best-engine @state-atom @cfg-atom)]
        (if best-engine-addr
          (do
            (swap! state-atom sessions/add-session event-id best-engine-addr)
            (proxy-pass req best-engine-addr))
          {:status 400, :body "No service available"})))))
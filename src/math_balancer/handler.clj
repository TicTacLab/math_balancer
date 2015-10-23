(ns math-balancer.handler
  (:require [nginx.clojure.core :as nginx]
            [cheshire.core :as json]
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

(defn polling-task []
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

(def ^:const service-unavailable-resp
  (-> {:status  503
       :headers {"Content-Type" "application/json"}
       :body    (json/generate-string {:status 503
                                       :errors [{:code    "STU"
                                                 :message "Service temporarily unavailable"}]})}))

(def ^:const session-limit-exceeded-resp
  (-> {:status  400
       :headers {"Content-Type" "application/json"}
       :body    (json/generate-string {:status 400
                                       :errors [{:code    "SLE"
                                                 :message "Sessions limit exceeded"}]})}))

(defn handler [req]
  "nginx-clojure rewrite handler"
  (let [url  (->> req :uri tools/get-url )
        [_ _ event-id _] url
        engine-addr (engines/get-assigned-engine @state-atom event-id)]
    (if engine-addr
      (proxy-pass req engine-addr)
      (if-not (engines/has-alive-engines? @state-atom)
        service-unavailable-resp
        (if-let [best-engine-addr (engines/get-best-engine @state-atom @cfg-atom)]
          (do
            (swap! state-atom sessions/add-session event-id best-engine-addr)
            (proxy-pass req best-engine-addr))
          session-limit-exceeded-resp)))))
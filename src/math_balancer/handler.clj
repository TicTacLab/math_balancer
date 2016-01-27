(ns math-balancer.handler
  (:require [nginx.clojure.core :as nginx]
            [cheshire.core :as json]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [math-balancer.scheduler :as scheduler]
            [math-balancer.sessions :as sessions]
            [math-balancer.tools :as tools]
            [math-balancer.engines :as engines]
            [math-balancer.authorization :as auth]
            [math-balancer.config :as c]
            [math-balancer.system :as s]
            [com.stuartsierra.component :as component]))

(def cfg-atom (atom {:engines []
                     :interval-ms nil}))

(def state-atom (atom {:sessions {}
                       :counts {}}))

(def system nil)

(defn start-system []
  (c/load-config)
  (alter-var-root #'system (constantly (s/new-system (c/config))))
  (alter-var-root #'system component/start))

(defn stop-system []
  (when system
    (alter-var-root #'system component/stop)))

(defn polling-task []
  (swap! state-atom engines/poll-engines @cfg-atom)
  (log/info "Performed engines poll" (:counts @state-atom)))

#_(defn -main [& _args]
  (try
    (swap! system #(if % % (component/start (s/new-system (c/config)))))
    (println "MathAdmin is started!")
    (catch Exception e
      (println e)
      (log/error e "Exception during startup. Fix configuration and
                    start application using REST configuration interface")))
  (swap! noilly-srv
         (fn [srv]
           (if srv
             srv
             (noilly/start c/cfg
                           #(swap! system
                                   (fn [s]
                                     (when s (component/stop s))
                                     (c/load-config)
                                     (component/start (s/new-system (c/config)))))))))
  (.. Runtime
      (getRuntime)
      (addShutdownHook (Thread. (fn []
                                  (do
                                    (component/stop @system)
                                    (noilly/stop @noilly-srv)))))))



(defn proxy-pass [req engine-addr]
  (nginx/set-ngx-var! req "engine" (tools/make-url engine-addr (:uri req)))
  nginx/phase-done)

(def ^:const service-unavailable-resp
  (-> {:status  503
       :headers {"Content-Type" "application/json"}
       :body    (json/generate-string {:status 503
                                       :errors [{:code    "STU"
                                                 :message "Service temporarily unavailable"}]})}))

(def ^:const unauthorized-resp
  (-> {:status  401
       :headers {"Content-Type" "application/json"
                 "WWW-Authenticate" "Basic realm=\"mengine\""}
       :body    (json/generate-string {:status 401
                                       :errors [{:code    "UAR"
                                                 :message "Unauthorized request"}]})}))

(def ^:const session-limit-exceeded-resp
  (-> {:status  400
       :headers {"Content-Type" "application/json"}
       :body    (json/generate-string {:status 400
                                       :errors [{:code    "SLE"
                                                 :message "Sessions limit exceeded"}]})}))

(defn init-handler
  "nginx-clojure jvm init handler"
  [_]
  (let [cfg (tools/read-cfg (get env :balancer-config))
        interval (:poll-interval-ms cfg)]
    (start-system)
    (reset! cfg-atom cfg)
    (scheduler/run-scheduler polling-task interval)
    (log/info "Service launched using config" cfg)
    {:status 200}))

(defn handle-request [req]
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

(defn handler [req]
  "nginx-clojure rewrite handler"
  (let [url  (->> req :uri tools/get-url )
        [_ _ _ action] url
        [auth-type creds] (auth/extract-request-credentials req)
        auth (:auth system)]
    (cond
      (nil? creds) unauthorized-resp
      (auth/white-listed? auth creds) (handle-request req)
      (auth/black-listed? auth creds) unauthorized-resp
      (auth/check-credentials auth action auth-type creds) (handle-request req)
      :else unauthorized-resp)))
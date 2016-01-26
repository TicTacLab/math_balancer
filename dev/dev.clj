(ns dev
  (:require [ns-tracker.core :refer [ns-tracker]]
            [nginx.clojure.embed :as nginx]
            [math-balancer.scheduler :as scheduler]
            [math-balancer.system :as s]
            [math-balancer.config :as c]
            [com.stuartsierra.component :as component]))

(defonce server nil)
(defonce system nil)

(defn init []
 (c/load-config)
 (alter-var-root #'system (constantly (s/new-system (c/config)))))

(defn start []
  (nginx/run-server "resources/nginx.conf")
  (alter-var-root #'server (fn [_] true))
  (alter-var-root #'system component/start))

(defn go []
  (init)
  (start))

(defn stop []
  (when system
    (alter-var-root #'system component/stop))
  (when server
    (nginx/stop-server)
    (scheduler/stop-poll-timer!)
    (scheduler/init-poll-timer!)
    (alter-var-root #'server (fn [_] false))))

(def ^:private modified-ns
 (ns-tracker ["src"]))

(defn reload-ns []
 (doseq [ns-sym (modified-ns)]
  (require ns-sym :reload)))

(defn reset []
 (stop)
 (reload-ns)
 (go))
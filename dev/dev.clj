(ns dev
 (:require [ns-tracker.core :refer [ns-tracker]]
           [nginx.clojure.embed :as nginx]))

(defonce server nil)

(defn start []
 (nginx/run-server "resources/nginx.conf")
 (alter-var-root #'server (fn [_] true)))

(defn stop []
 (when server
  (nginx/stop-server)
  (alter-var-root #'server (fn [_] false))))

(def ^:private modified-ns
 (ns-tracker ["src"]))

(defn reload-ns []
 (doseq [ns-sym (modified-ns)]
  (require ns-sym :reload)))

(defn reset []
 (stop)
 (reload-ns)
 (start))
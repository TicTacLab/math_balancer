(ns math-balancer.handler
  (:require [nginx.clojure.core :as nginx]))

(defn get-engine [{:keys [uri]}]
  (prn uri))

(defn handler [req]
  "nginx-clojure rewrite handler"
  (let [engine-addr (get-engine req)]
    (nginx/set-ngx-var! req "engine" engine-addr)
    nginx/phase-done))

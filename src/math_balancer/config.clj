(ns math-balancer.config
  (:require [cheshire.core :as json]))

(def cfg (atom nil))

(defn load-config []
  (let [file-path (or (System/getProperty "math_balancer.config_file")
                      "conf/math-balancer.json")]
    (reset! cfg (json/parse-string (slurp file-path) true))))

(defn config []
  (if-let [c @cfg]
    c
    (do
      (load-config)
      @cfg)))

(ns math-balancer.tools
  (:require [clojure.string :as s]
            [cheshire.core :as json]))


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

(defn read-cfg [config-file]
  (-> (slurp config-file)
      (json/parse-string true)))


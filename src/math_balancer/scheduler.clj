(ns math-balancer.scheduler
  (:require [clojure.tools.logging :as log])
  (:import java.util.Timer
           java.util.TimerTask))

(def poll-timer (atom nil))

(defn init-poll-timer! []
  (reset! poll-timer (Timer. true)))

(defn stop-poll-timer! []
  (.cancel @poll-timer)
  (.purge @poll-timer))

(defn poll-engines-task [task]
  (proxy [TimerTask] []
    (run []
      (task))))

(defn run-scheduler [task interval]
  (init-poll-timer!)
  (.scheduleAtFixedRate @poll-timer
                        (poll-engines-task task)
                        (long 0)
                        (long interval)))

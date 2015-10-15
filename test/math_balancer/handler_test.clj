(ns math-balancer.handler-test
  (:use clojure.test
        math-balancer.handler))

(deftest max-sessions-count-test
  (let [cfg {:engines [{:server "one" :sessions-limit 1}]}]
     (is (= 1
           (max-sessions-count {:cfg cfg} "one")))))

(deftest add-session-test
  (let [state {:sessions {} :counts {}}]
    (is (= {:sessions {:id1 :engine1,
                       :id2 :engine2
                       :id3 :engine1}
            :counts {:engine1 2, :engine2 1}}
           (-> state
               (add-session :id1 :engine1)
               (add-session :id2 :engine2)
               (add-session :id3 :engine1))))))

(deftest sessions-check-test
  (is (= {:sessions {"id1" "one", "id2" "two", "id3", "two"}
          :counts   {"one" 1, "two" 2}}
         (sessions-check {}
                         [["one" (future {:status 200
                                          :body "{\"data\":[\"id1\"]}"})]
                          ["two" (future {:status 200
                                          :body "{\"data\":[\"id2\",\"id3\"]}"})]]))))


(deftest get-event-id-test
  (is (= "event-id"
         (get-event-id "/files/model/event-id/path"))))

(deftest get-best-engine-test
  (let [state {:sessions {}
               :counts {"one" 1, "two" 0}
               :cfg {:engines [{:server "one" :sessions-limit 1}
                               {:server "two" :sessions-limit 1}]}}]
    (is (= "two"
           (get-best-engine state)))
    (is (nil? (get-best-engine (add-session state :id1 "two"))))))

(deftest get-engine!-test
  (let [state (atom {:sessions {"id1" "one"}
                     :counts   {"one" 1, "two" 0}
                     :cfg      {:engines [{:server "one" :sessions-limit 1}
                                          {:server "two" :sessions-limit 1}]}})]
    (is (= "one"
           (get-engine! state "/files/model/id1/path")))
    (is (= "two"
           (get-engine! state "/files/model/id2/path")))
    (is (nil? (get-engine! state "/files/model/id3/path")))))
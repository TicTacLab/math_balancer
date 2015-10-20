(ns math-balancer.sessions-test
  (:require
    [clojure.test :refer :all]
    [math-balancer.sessions :refer :all]))


(deftest max-sessions-count-test
         (let [cfg {:engines [{:server "one" :sessions-limit 1}]}]
           (is (= 1
                  (max-sessions-count cfg "one")))))

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

(deftest read-engine-response-test
         (is (nil? (read-engine-response {:error true})))
         (is (nil? (read-engine-response {:status 400})))
         (is (nil? (read-engine-response {:status 200, :body "{\"data\": []"})))
         (is (= {:session-ids [1, 2]}
                (read-engine-response {:status 200, :body "{\"data\": [1, 2]}"}))))

(deftest sessions-set-test
         (is (= {:sessions {"id1" "one", "id2" "two", "id3", "two"}
                 :counts   {"one" 1, "two" 2}}
                (-> {}
                    (sessions-set ["one" (future {:status 200
                                                  :body "{\"data\":[\"id1\"]}"})])
                    (sessions-set ["two" (future {:status 200
                                                  :body   "{\"data\":[\"id2\",\"id3\"]}"})])
                    (sessions-set ["three" (future {:error true
                                                    :body "{\"data\": [\"id4\"]}"})])))))
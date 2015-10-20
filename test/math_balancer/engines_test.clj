(ns math-balancer.engines-test
  (:require [clojure.test :refer :all]
            [math-balancer.engines :refer :all]
            [math-balancer.sessions :refer [add-session]]))

(deftest get-best-engine-test
         (let [state {:sessions {}
                      :counts {"one" 1, "two" 0}}
               cfg {:engines [{:server "one" :sessions-limit 1}
                              {:server "two" :sessions-limit 1}]}]
           (is (= "two"
                  (get-best-engine state cfg)))
           (is (nil? (get-best-engine (add-session state :id1 "two") cfg)))))

(deftest get-assigned-engine-test
         (let [state {:sessions {"id1" "one"}
                      :counts {"one" 1}}]
           (is (= "one"
                  (get-assigned-engine state "id1")))
           (is (nil? (get-assigned-engine state "id2")))))

(ns math-balancer.tools-test
  (:require [clojure.test :refer :all]
            [math-balancer.tools :refer :all]))

(deftest get-event-id-test
         (is (= ["files" "model" "event-id" "path" ]
                (get-url "/api/files/model/event-id/path")))
         (is (= ["files" "model" "event-id" ]
                (get-url "/api/files/model/event-id"))))

(deftest make-path-test
         (is (= "http://some-addr:8080/files/model/event-id/path"
                (make-url "some-addr:8080" "/api/files/model/event-id/path")))
         (is (= "http://some-addr:8080/files/model/event-id"
                (make-url "some-addr:8080" "/api/files/model/event-id"))))


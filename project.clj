(defproject math_balancer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.codec "0.1.0"]
                 [ch.qos.logback/logback-core "1.1.3"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [org.clojure/tools.logging "0.3.1"]
                 [nginx-clojure "0.4.2"]
                 [http-kit "2.1.18"]
                 [cheshire "5.5.0"]
                 [environ "1.0.1"]]
  :main ^:skip-aot math-balancer.core
  :target-path "target/%s"
  :repl-options {:init-ns user}
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[nginx-clojure/nginx-clojure-embed "0.4.2"]
                                  [ns-tracker "0.3.0"]]
                   :plugins [[lein-environ "0.4.0"]]
                   :env {:balancer-config "conf/math-balancer.json"}}
             :uberjar {:aot :all}})

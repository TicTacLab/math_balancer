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
                 [clojurewerkz/cassaforte "2.0.2"]
                 [clojurewerkz/scrypt "1.2.0"]
                 [prismatic/schema "1.0.1"]
                 [com.stuartsierra/component "0.2.3"]
                 [org.clojure/core.cache "0.6.4"]]
  :main ^:skip-aot math-balancer.core
  :target-path "target/%s"
  :repl-options {:init-ns user}
  :profiles {:dev {:source-paths ["dev"]
                   :global-vars  {*warn-on-reflection* false}
                   :dependencies [[nginx-clojure/nginx-clojure-embed "0.4.2"]
                                  [ns-tracker "0.3.0"]
                                  [aprint "0.1.3"]
                                  [criterium "0.4.3"]
                                  [im.chit/vinyasa "0.4.1"]
                                  [org.clojure/tools.trace "0.7.8"]]
                   :injections [(require '[vinyasa.inject :as inject])
                                (require 'aprint.core)
                                (require 'clojure.pprint)
                                (require 'clojure.tools.trace)
                                (require 'criterium.core)
                                (inject/in clojure.core >
                                  [aprint.core aprint]
                                  [clojure.pprint pprint]
                                  [clojure.tools.trace trace]
                                  [criterium.core bench])]}
             :uberjar {:aot :all}})
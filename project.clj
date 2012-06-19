(defproject workflow-runner "0.1-SNAPSHOT"
  :description "Wf4Ever workflow runner service"
  :dependencies [[org.clojure/clojure "1.3.0"]
                  [compojure-rest "0.2.2"]
                  [compojure "1.1.0"]]
  :plugins [[lein-ring "0.7.1"]]
  :ring {:handler workflow-runner.core/app})

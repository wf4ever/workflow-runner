(defproject workflow-runner "0.1-SNAPSHOT"
  :description "Wf4Ever workflow runner service"
  :dependencies [[org.clojure/clojure "1.3.0"]
                  [compojure "1.1.0"]
                  [ring-middleware-format "0.2.0"]
                  [clj-http "0.4.3"]
                  [clj-time "0.4.3"]
                  [stain/plaza "0.1.1-SNAPSHOT"]
                 ]

  :plugins [[lein-ring "0.7.1"]]
  :ring {:handler workflow-runner.core/app})

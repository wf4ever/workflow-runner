(ns workflow-runner.core
  (:import (java.net.URI))
  (:use compojure.core)
  (:use [ring.middleware.format-params :only [wrap-restful-params]]
        [ring.middleware.format-response :only [wrap-restful-response]])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]))

(def jobs (atom {}))`


(defn addjob [jobid]
  jobid)

(defn created [url]
  {:status 201
   :headers {"Location" url}
   :body ""})

(defn full-url [req & relative]
  (let [scheme (:scheme req)
        port (:server-port req)
        default-port {:http 80 :https 443}
        port (if (= (default-port scheme) port) -1 port)
        uri (java.net.URI. (name scheme)
                 nil
                 (:server-name req)
                 port
                 (:uri req)
                 (:query-string req)
                 nil)]
    (.toASCIIString 
        (reduce #(.resolve %1 %2) uri relative))))

(defn new-job [request]
  (created (full-url request "../jobs/" "122")))

(defn all-jobs [request]
   {:body @jobs })

(defn get-job [jobid]
  {:body {:job 1337 
   :id jobid}} )

(defn cancel-job [jobid]
  "OK")


(defroutes main-routes
  (GET "/" [] "Workflow Runner service")
  (GET "/test" [:as request] (str request))
  (GET "/jobs" [:as request] (all-jobs request)) 
  (POST "/jobs" [:as request] (new-job request))
  (GET "/jobs/:jobid" [jobid] (get-job jobid))
  (DELETE "/jobs/:jobid" [jobid] (cancel-job jobid))
  (route/resources "/")
  (route/not-found "Resource not found"))

(def app
  (-> (handler/api main-routes)
      (wrap-restful-params)
      (wrap-restful-response)))
 

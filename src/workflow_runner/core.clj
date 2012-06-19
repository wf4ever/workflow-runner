(ns workflow-runner.core
  (:import (java.net URI) (java.util UUID Date)) 
  (:use compojure.core)
  (:use [ring.middleware.format-params :only [wrap-restful-params]]
        [ring.util.response]
        [ring.middleware.format-response :only [wrap-restful-response]])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]))

(def jobs (atom {}))

(def dummy-req
  {:ssl-client-cert nil, :remote-addr "127.0.0.1", :scheme :http, :query-params {}, :form-params {}, :body-params {"ro" "http://example.com"}, :request-method :post, :query-string nil, :route-params {}, :content-type "application/json", :uri "/jobs/", :server-name "localhost", :params {:ro "http://example.com"}, :headers {"connection" "TE, close", "user-agent" "lwp-request/6.03 libwww-perl/6.03", "te" "deflate,gzip;q=0.3", "content-type" "application/json", "content-length" "29", "host" "localhost:3000"}, :content-length 29, :server-port 3000, :character-encoding nil, :body "{\"ro\": \"http://example.com\"}"})


(defn addjob [jobid]
  jobid)


(defn full-url [req & relative]
  (let [scheme (:scheme req)
        port (:server-port req)
        default-port {:http 80 :https 443}
        port (if (= (default-port scheme) port) -1 port)
        uri (URI. (name scheme)
                 nil
                 (:server-name req)
                 port
                 (:uri req)
                 (:query-string req)
                 nil)]
    (println relative)
    (.toASCIIString
        (reduce #(.resolve %1 %2) uri relative))))

(defn created [url]
  {:status 201
   :headers {"Location" url}
   :body ""})

(defn moved [url & [flag]]
  {:status (if (= :permanently flag) 301 307)
   :headers {"Location" url}
   :body (str "The resource has moved to " url)})

(defn gen-uuid []
  (.toString (UUID/randomUUID)))

(defn new-job [job request]
  (let [jobid (gen-uuid)]
    (prn request)
    (swap! jobs assoc jobid (assoc job 
                              :id jobid
                              :status :new
                              ;; TODO: Ensure job is only the expected
                              ;; keys and value types to prevent abuse
                              :created (Date.)))
    (created (full-url request jobid))))

(defn all-jobs [request]
   (println :all-jobs request)
   (println (keys @jobs))
   (map (partial full-url request) (keys @jobs)))

(defn get-job [jobid]
  (let [job (get @jobs jobid)]
    (if (nil? job) nil
      (if (empty? job) { :status 410
                         :body "The job was cancelled"}
         {:body job}))))


(defn cancel-job [jobid]
  (swap! jobs assoc jobid {}))


(defroutes main-routes
  (GET "/" [] "Workflow Runner service")
  (GET "/test" [:as request] (str request))
  (ANY "/jobs" [:as req] (moved (full-url req "/jobs/") :permanently))
  (GET "/jobs/" [:as request] (all-jobs request)) 
  (POST "/jobs/" {job :body-params :as request} (new-job job request))
  (GET "/jobs/:jobid" [jobid] (get-job jobid))
  (DELETE "/jobs/:jobid" [jobid] (cancel-job jobid))
  (route/resources "/")
  (route/not-found "Resource not found"))

(def app
  (-> (handler/api main-routes)
      (wrap-restful-params)
      (wrap-restful-response)))
 

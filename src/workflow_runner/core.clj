(ns workflow-runner.core
  (:import (java.net URI) (java.util UUID Date)) 
  (:use compojure.core)
  (:use [ring.middleware.format-params :only [wrap-restful-params]]
        [ring.util.response]
        [ring.middleware.format-response :only [wrap-restful-response]]
        [clojure.string :only [trim split-lines join]])
  (:require [compojure.route :as route]
            [workflow-runner.t2server :as t2]
            [compojure.handler :as handler]))

;; TODO: Move to configuration file
(def default-server "http://sandbox.wf4ever-project.org/taverna-server/rest/")

(def ^:dynamic *server* default-server)

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

(defn parse-uri-list [s]
 (map #(.toASCIIString (URI. %))
   (filter #(not (.startsWith %1 "#"))
           (map trim (split-lines s)))))


(defn run-id [server url]
  (key (filter #(= url (val %)) (t2/runs))))

(defn new-job [server workflow request]
  (let [runurl (t2/new-run server workflow)
        jobid (run-id (server runurl))]
    (created (full-url request jobid))))


(defn join-newlines [l]
  (join "\n" l))

(defn as-uri-list [ascii-uris]
  {:status 200
   :headers {"Content-Type" "text/uri-list"}
   :body (join-newlines ascii-uris)})

(defn ro-url [request t2jobid]
  (full-url request (str t2jobid "/")))

(defn all-jobs [request s]
  (as-uri-list
   (map (partial ro-url request) (keys (t2/runs s)))))

(defn jobid-to-url [s jobid]
  (get (t2/runs s) jobid))

(defn job-not-found? [s jobid]
  (nil? (jobid-to-url s jobid)))

(defn manifest [s jobid]
  (let [job (t2/run s (jobid-to-url s jobid))]
    (if job {:body job})))

(defn cancel-job [jobid]
  nil)

(defn server-credentials [url]
  ["taverna" "taverna"])

(defroutes runner-routes
  (let-routes [s (apply (partial t2/connect *server*) (server-credentials *server*))]  
    (GET "/" [:as request] (all-jobs request s))
    (POST "/" [body :as request] (new-job s (parse-uri-list body) request))
    (ANY "/:jobid" [jobid :as req] (moved (full-url req (str jobid "/"))))
    (context "/:jobid" [jobid :as req]
      (ANY "/" [] (if (job-not-found? s jobid)  (route/not-found (str "Unknown job " jobid))))
      (ANY "/" [] (moved (full-url req (str jobid "/" "manifest"))))
      (DELETE "/" [] (cancel-job jobid s))
      (GET "/manifest" [] (manifest s jobid))
             
    (route/not-found "Resource not found"))))


(defroutes main-routes
  (GET "/" [:as req] (moved (full-url req "default/")))
  (ANY "/default" [:as req] (moved (full-url req "default/")))
  (context "/default" [] runner-routes)
           ;; FIXME: This does not work
  ;(context "/t2/{:server}" [server] (binding [*server* server] runner-routes)))
  (route/not-found "Resource not found"))

(def app
  (-> (handler/api main-routes)
      (wrap-restful-params)
      (wrap-restful-response)))
 

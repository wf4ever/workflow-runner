(ns workflow-runner.core
  (:import (java.net URI) (java.util UUID Date))
  (:use compojure.core)
  (:use [ring.middleware.format-params :only [wrap-restful-params]]
;        [ring.util.response]
        [ring.middleware.format-response :only [wrap-restful-response]]
        [clojure.string :only [trim split-lines join]])
  (:require [compojure.route :as route]
            [workflow-runner.t2server :as t2]
            [workflow-runner.rdf :as rdf]
            [compojure.handler :as handler]))

;; TODO: Move to configuration file
(def default-server "http://sandbox.rohub.org/taverna-server/rest")

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
   :body (str (join-newlines ascii-uris)"\n")})

(defn ro-url [request t2jobid]
  (full-url request (str t2jobid "/")))

(defn all-jobs [request s]
  (as-uri-list
   (map (partial ro-url request) (keys (t2/runs s)))))

(defn jobid-to-url [s jobid]
  (get (t2/runs s) jobid))

(defn job-not-found? [s jobid]
  (nil? (jobid-to-url s jobid)))

(defn manifest [ro-url]
    { :content-type "text/turtle"
      :body (rdf/model-as-string (rdf/empty-wfrun-manifest ro-url))})

(defn cancel-job [jobid]
  nil)

(defn server-credentials [url]
  ["taverna" "taverna"])


(def status-t2-to-runner
  "Map from t2 status to Workflow Runner status"
  {:Initialized :Initialized,
   :Operating :Running,
   :Stopped :Failed,
   :Finished :Finished })


(def status-runner-to-t2
  "Map from Workflow Runner status to t2 status"
  { :Initialized :Initialized
    :Queued :Operating
    :Running :Operating
    :Failed :Stopped
    :Finished :Finished
    :Cancelled :Finished
    :Archived :Finished })

(defn t2-status-to-runner-status [t2-status]
  (str "http://purl.org/wf4ever/runner#" (name (status-t2-to-runner t2-status))))

(defn get-job-status [s jobid]
  (as-uri-list [(t2-status-to-runner-status
   (t2/run-status (t2/run s (jobid-to-url s jobid))))]))

(defn find-status [uris]
  (keyword (.getFragment (URI.
         (first (filter #(.startsWith % "http://purl.org/wf4ever/runner#") (parse-uri-list uris)))))))

(defn set-job-status [s jobid body]
  (if (nil? body) {:status 400} (do
    (t2/run-set (t2/run s (jobid-to-url s jobid)) :status (status-runner-to-t2 (find-status body)))
    (get-job-status s jobid))))


(defroutes runner-routes
  (let-routes [s (apply (partial t2/connect *server*) (server-credentials *server*))]
    (GET "/" [:as request] (all-jobs request s))
    (POST "/" [body :as request] (new-job s (parse-uri-list body) request))
    (ANY "/:jobid" [jobid :as req] (moved (full-url req (str jobid "/"))))
    (context "/:jobid" [jobid :as req]
      (ANY "*" [] (if (job-not-found? s jobid)  (route/not-found (str "Unknown job " jobid))))
      (ANY "/" [] (moved (full-url req (str jobid "/" "manifest"))))
      (DELETE "/" [] (cancel-job jobid s))
      (GET "/manifest" [] (manifest (full-url req "./")))
      (GET "/status" [] (get-job-status s jobid))
      (PUT "/status" [body] (set-job-status s jobid body))
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

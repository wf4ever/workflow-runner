(ns workflow-runner.t2server
 (:use [clj-time.core :only [now]])
 (:require [clj-http.client :as client])
 (:require [clj-time.format :as timeformat]))


; Workaround for clojure.walk/keywordize-keys making :@href
(def href (keyword "@href"))

(def t2flow-type "application/vnd.taverna.t2flow+xml")

(def default-req
  {:as :auto
   :headers {"User-Agent" "t2server.clj/0.1-SNAPSHOT clj-http/0.4.3"}
   :accept "application/json;q=1.0, text/plain;q=0.1" })

(defn authenticated [username password]
  (assoc default-req 
    :basic-auth [username password]))

(defn map-value-map [f m] (zipmap (keys m) (map f (vals m))))

(defn connect
  "Connect to t2server and return a map to be used as 'server'"
  [url username password & req]
  (let [req (merge req (authenticated username password))]
  ; We'll include the pre-populated req in the returned map
  (merge
    (map-value-map href (get-in (client/get url req) [:body :serverDescription]))
    {::req req
     :url url})))

(defn runs [server]
  (map href (get-in 
              (client/get (:runs server) (::req server))
              [:body :runList :run])))


(defn new-run [server workflow]
  (get-in (client/post (:runs server) (merge (::req server) 
                                             {:body workflow
                                              :content-type t2flow-type})) 
          [:headers "location"]))

(defn run [server url]
  (merge 
    (select-keys server [::req])
    {:url url}
    (map-value-map href (get-in 
        (client/get url (::req server))
                                [:body :runDescription]))))

(defn- run-get-parse-body [attr {body :body}]
    (cond 
      (= "" body) nil
      (nil? body) nil
      ;(map? body) (first (vals body))
      :else (case attr
              :status (keyword body)
              (:expiry :createTime :finishTime :startTime) 
                (timeformat/parse (:date-time timeformat/formatters) body)
              body)))
 
(defn run-get [run attr]
  (if-let [url (attr run)]
    (run-get-parse-body attr (client/get url (::req run)))))

(defn datetime? [v]
    (instance? (type (now)) v))

(defn run-set [run attr value]
  (let [body (cond 
               (string? value) value
               (keyword? value) (name value)
               (datetime? value) (str value)
               :else value)]
    (run-get-parse-body attr 
      (client/put (attr run) (merge (::req run) 
            {:body body
             :content-type (cond 
                             (string? body) "text/plain"
                             :else "application/json")})))))

(defn run-status [run]
  (run-get run :status))

(defn start-run [run]
  (run-set run :status :Operating))

(defn cancel-run [run]
  (run-set run :status :Finished))

(defn delete-run [run]
  (client/delete (:url run) (::req run)))

  

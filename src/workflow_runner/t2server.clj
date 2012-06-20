(ns workflow-runner.t2server
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

(defn connect
  "Connect to t2server and return a map to be used as 'server'"
  [url username password & req]
  (let [req (merge req (authenticated username password))]
  ; We'll include the pre-populated req in the returned map
  (merge
    (get-in (client/get url req) [:body :serverDescription])
    {::req req
     :url url})))

(defn map-value-map [f m] (zipmap (keys m) (map f (vals m))))


(defn run [server url]
  (merge 
    (select-keys server [::req])
    {:url url}
    (map-value-map href (get-in 
        (client/get url (::req server))
                                [:body :runDescription]))))

(defn runs [server]
  (map href (get-in 
              (client/get (get-in server [:runs href]) (::req server))
              [:body :runList :run])))


(defn new-run [server workflow]
  (client/post (runs-uri server) (merge (::req server) {:body workflow
                                                        :content-type t2flow-type})))

(defn run-get [run attr]
  (if-let [url (attr run)]
    (let [body (:body (client/get url (::req run)))]
      (cond 
        (= "" body) nil)
        ;(map? body) (first (vals body))
        :else (case attr
                :status (keyword body)
                (:expiry :createTime :finishTime :startTime) 
                  (timeformat/parse (:date-time timeformat/formatters) body)
                body))))


(defn run-set [run attr value]
  (client/put (attr run) (merge (::req run) 
            {:body (cond 
                     (string? value) value
                     (keyword? value) (name value)
                     :else value)
             :content-type (cond 
                             (string? value) "text/plain"
                             (keyword? value) "text/plain"
                             :else "application/json")})))

(defn run-status [run]
  (run-get run :status))

(defn start-run [run]
  (run-set run :status :Operating))

(defn cancel-run [run]
  (run-set run :status :Finished))

(defn delete-run [run]
  (client/delete (:url run) (::req run)))

  

(ns workflow-runner.test.t2server
  (:use [clojure.java.io :only [resource input-stream]])
  (:use [workflow-runner.t2server])
  (:use [clojure.test]))

(def ^:dynamic *server* "http://sandbox.wf4ever-project.org/taverna-server/rest/")
(def ^:dynamic *server-user* "taverna")
(def ^:dynamic *server-pw* "taverna")

(deftest test-authenticated
   (let [req (authenticated "fred" "soup")]
     (is ["fred" "soup"] (:basic-auth req))
     (is :auto (:as req))))

(deftest test-map-value-map
  (is {:a "1" :b "{}" :c "[]"}
     (map-value-map str {:a 1 :b {} :c []})))

(deftest test-connect
  (let [s (connect *server* *server-user* *server-pw*)]
    (is true (map? s))
    (is *server* (:url s))
    (is (str *server* "runs/") (:runs s))
    (is true (map? (::workflow-runner.t2server/req *server*)))))

(deftest test-runs
  (let  [s (connect *server* *server-user* *server-pw*)
         r (runs s)]
    (is seq? r)
    ; Should be a list of URIs
    (map #(is true (and (string? %) (.startsWith % *server*))) r)))

(deftest test-new-run
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        r (new-run s wf)]
      (is true (string? r))
      (is true (.startsWith r *server*))))


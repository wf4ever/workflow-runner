(ns workflow-runner.test.t2server
  (:use [clojure.java.io :only [resource input-stream]])
  (:use [workflow-runner.t2server])
  (:use [clj-time.core :only [after? now]])
  (:use [clojure.test]))

(def ^:dynamic *server* "http://sandbox.wf4ever-project.org/taverna-server/rest/")
(def ^:dynamic *server-user* "taverna")
(def ^:dynamic *server-pw* "taverna")

(deftest test-authenticated
  (let [req (authenticated "fred" "soup")]
     (is (= ["fred" "soup"] (:basic-auth req)))
     (is (= :auto (:as req)))))

(deftest test-map-value-map
  (is (= {:a "1" :b "{}" :c "[]"})
         (map-value-map str {:a 1 :b {} :c []})))

(deftest test-connect
  (let [s (connect *server* *server-user* *server-pw*)]
    (is (map? s))
    (is (= *server* (:url s)))
    (is (= (str *server* "runs/") (:runs s)))
    (is (map? (::workflow-runner.t2server/req *server*)))))

(deftest test-runs
  (let  [s (connect *server* *server-user* *server-pw*)
         r (runs s)]
    (is (seq? r))
    ; Should be a list of URIs
    (map #(is (and (string? %) (.startsWith % *server*))) r)))

(deftest test-new-run
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        r-uri (new-run s wf)]
    (is (string? r-uri))
    (is (.startsWith r-uri *server*))
    (is (contains? r-uri (runs s)))))

(deftest test-datetime?
    (is (not (datetime? nil)))
    (is (not (datetime? (str (now)))))
    (is (datetime? (now) (now))))

(deftest test-run
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        r (run s (new-run s wf))]
    (is (map? r))
    (is (= r (:url r)))
    ((map? (::workflow-runner.t2server/req *server*)))
    (is (.startsWith (:status r)))))

(deftest test-run-get
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        r (run s (new-run s wf))]
    (is (= :Initialized (run-get r :status)))
    (is (after? (run-get r :expiry) (run-get r :createTime)))
    (is (nil? (run-get r :finishTime)))
    (is (map? (run-get r :inputs)))
    (is (nil? (run-get r :somethingUnknown)))))

(deftest test-run-set
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        r (run s (new-run s wf))
        created (run-get r :createTime)]
    (is (= :Finished (run-set r :status :Finished)))
    (is (= :Finished (run-get r :status)))
    (println (run-set r :expiry created))
    (is (= created (run-get r :expiry)))))

 

(ns workflow-runner.test.t2server
  (:use [clojure.java.io :only [resource input-stream]])
  (:use [workflow-runner.t2server])
  (:use [clj-time.core :only [before? after? now plus hours]])
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
    (is (.startsWith (:runs s) *server*))
    (is (map? (:workflow-runner.t2server/req s)))))

(deftest test-runs
  (let  [s (connect *server* *server-user* *server-pw*)
         r (runs s)]
    (is (seq? r))
    ; Should be a list of URIs
    (map #(is (and (string? %) (.startsWith % *server*))) r)))

;; http://stackoverflow.com/questions/3249334/test-whether-a-list-contains-a-specific-value-in-clojure
(defn in? 
  "true if seq contains elm"
  [seq elm]  
  (some #(= elm %) seq))


(deftest test-new-run
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        r-uri (new-run s wf)]
    (is (string? r-uri))
    (is (.startsWith r-uri *server*))
    (is (in? (runs s) r-uri))))

(deftest test-datetime?
    (is (not (datetime? nil)))
    (is (not (datetime? (str (now)))))
    (is (datetime? (now))))

(deftest test-run
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        run-uri (new-run s wf)
        r (run s run-uri)]
    (is (map? r))
    (is (= run-uri (:url r)))
    (is (map? (::workflow-runner.t2server/req r)))
    (is (.startsWith (:status r) run-uri))))

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
        old-expiry (run-get r :expiry)
        new-expiry (plus old-expiry (hours 1))]
    (is (= :Finished (run-set r :status :Finished)))
    (is (= :Finished (run-get r :status)))
    (is (= new-expiry (run-set r :expiry new-expiry)))
    (is (before? old-expiry (run-get r :expiry)))))

 

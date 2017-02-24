(ns workflow-runner.test.t2server
  (:use [clojure.java.io :only [input-stream]])
  (:use [workflow-runner.t2server])
  (:use [clj-time.core :only [before? after? now plus hours]])
  (:use [clojure.test]))

(def ^:dynamic *server* "http://sandbox.rohub.org/taverna-server/rest")
(def ^:dynamic *server-user* "taverna")
(def ^:dynamic *server-pw* "taverna")

(defn resource [file]
  (str "test-resources/" file))

(deftest test-authenticated
  (let [req (authenticated "fred" "soup")]
     (is (= ["fred" "soup"] (:basic-auth req)))
     (is (= :auto (:as req)))))

(deftest test-map-value-map
  (is (= {:a "1" :b "{}" :c "[]"})
         (map-value-map str {:a 1 :b {} :c []})))

(deftest test-extract-map
  (let [seq-of-maps [
          {:name "Fred" :tel 3133 :mob 4343}
          {:name "June" :tel 5421 :mob 2141}
          {:name "Alex" :tel 3133 :mob 1234}]]
    (is (= {"Alex" 3133, "June" 5421, "Fred" 3133}
           (extract-map :name :tel seq-of-maps)))))


(deftest test-connect
  (let [s (connect *server* *server-user* *server-pw*)]
    (is (map? s))
    (is (= *server* (:url s)))
    (is (.startsWith (:runs s) *server*))
    (is (map? (:workflow-runner.t2server/req s)))))

(deftest test-runs
  (let  [s (connect *server* *server-user* *server-pw*)
         r (runs s)]
    (is (map? r))
    ; Should be a map of URIs
    (doall (map #(is (and (string? %) (.startsWith % *server*))) (vals r)))))

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
    (is (in? (vals (runs s)) r-uri))))

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

(deftest test-run-status
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        r (run s (new-run s wf))]
    (is (= :Initialized (run-status r)))))


(deftest test-start-run
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        r (run s (new-run s wf))]
    (is (not (= :Initialized (start-run r))))
    (is (get #{:Operating :Stopped :Finished} (run-status r)))))



(deftest test-cancel-run
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        r (run s (new-run s wf))]
    (start-run r)
    (cancel-run r)
    (is (get #{:Stopped :Finished} (run-status r)))))




(deftest test-delete-run
  (let [wf (slurp (resource "helloworld.t2flow") :encoding "utf-8")
        s (connect *server* *server-user* *server-pw*)
        r (run s (new-run s wf))]
    (is (run? r))
    (delete-run r)
    (is (not (run? r)))))

; Disabled as it would also delete other runs!
;(deftest delete-all-runs
;  (let [s (connect *server* *server-user* *server-pw*)]
;         (delete-all-runs s)))

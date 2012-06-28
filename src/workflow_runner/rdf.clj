(ns workflow-runner.rdf
  (:import [java.net URI])
  (:use 
    [plaza.rdf.implementations.jena :only [init-jena-framework]]
    [clojure.string :only [capitalize]])
  (:require [plaza.rdf.core :as plz]))

(def a [:rdf :type])

(defn register-namespaces []
  (doall (for [prefix [:ro :wfdesc :wfprov :wf4ever :runner]]
    (plz/register-rdf-ns prefix (str "http://purl.org/wf4ever/" (name prefix) "#"))))
  (plz/register-rdf-ns :ore "http://www.openarchives.org/ore/")
  (plz/register-rdf-ns :ao "http://purl.org/ao/"))

(defn empty-resourcemap [ro-uri]
  (init-jena-framework)
  (let [manifest (plz/defmodel)]
    (plz/with-model manifest
       (plz/with-rdf-ns ro-uri
       (register-namespaces)
                    (plz/model-add-triples [
     ["" a [:ore :Aggregation]]
     ["" [:ore :isDescribedBy] "manifest"]
     ["manifest" a [:ore :ResourceMap]]
     ["manifest" [:ore :describes] ""]]))
   manifest)))

(defn empty-manifest [ro-uri]
  (let [manifest (empty-resourcemap ro-uri)]
    (plz/with-model manifest
       (plz/with-rdf-ns ro-uri
         (plz/model-add-triples [
     ["" a [:ro :ResearchObject]]
     ["" a [:wf4ever :WorkflowResearchObject]]
     ["manifest" a [:ro :Manifest]]])))
   manifest))

(def runner-resources
  #{:workflow :status :inputs :outputs :logs :workingDirectory :provenance})

(def folder?
  #{:inputs :outputs :logs :workingDirectory :provenance})

(defn aggregate-in-manifest [ro-uri manifest resource]
  (let [resource-name (if (keyword? resource) (name resource) resource)
        resource-uri (str ro-uri resource-name)
        resource-type (keyword (capitalize resource-name))
        proxy-uri (str "manifest#" resource-name)
        folder-uri (str resource-uri "/")]
  (plz/with-model manifest
    (plz/with-rdf-ns ro-uri
        (plz/model-add-triples [
        ["" [:ore :aggregates] resource-uri]
        [proxy-uri a [:ore :Proxy]]
        [proxy-uri [:ore :proxyFor] resource-uri]
        [proxy-uri [:ore :proxyIn] ro-uri]
        [resource-uri a [:ro :Resource]]
        [resource-uri a [:ore :AggregatedResource]] ])
        (when (runner-resources resource)
          (plz/model-add-triples [
            ["" [:runner resource] resource-uri]
            [resource-uri a [:runner resource-type]]]))
        (when (folder? resource) 
          (plz/model-add-triples [
            [resource-uri a [:ro :Folder]]
            [resource-uri a [:ore :Aggregation]]
            [resource-uri [:ore :isDescribedBy] folder-uri]])))))
  manifest)

(defn empty-wfrun-manifest [ro-uri]
  (let [manifest (empty-manifest ro-uri)]
    (plz/with-model manifest
       (plz/with-rdf-ns ro-uri
         (plz/model-add-triples [
     ["" a [:runner :WorkflowRun]]])))
   (last (for [res runner-resources]
     (aggregate-in-manifest ro-uri manifest res)))))

(defn model-as-string [m & [rdf-format]]
  (with-open [writer (java.io.StringWriter.)]
    (plz/output-string m writer (or rdf-format :turtle))
    (str writer)))


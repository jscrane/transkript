(ns transkript.core
  (:import (eu.transkribus.client.connection TrpServerConn)
           (eu.transkribus.core.model.beans TrpTranscriptStatistics TrpTotalTranscriptStatistics CitLabHtrTrainConfig DocumentSelectionDescriptor TrpDoc)
           (java.net URL)
           (java.util Date ArrayList))
  (:require [clojure.edn :as edn])
  (:use [clojure.java.data]))

(def conn (atom nil))
(def collection (atom nil))

(defn load-config
  "loads configuration information"
  [filename]
  (edn/read-string (slurp filename)))

(defn connect
  "connects to remote transkribus server"
  [{#^String user :username #^String pass :password #^String server :server}]
  (reset! conn (TrpServerConn. server user pass))
  @conn)

(defn close
  "closes connection to transkribus server"
  []
  (.close @conn))

(defmethod from-java TrpTranscriptStatistics [_])

(defmethod from-java TrpTotalTranscriptStatistics [_])

(defmethod from-java URL [o] (str o))

(defmethod from-java Date [o] (str o))

(defn- remove-nils [m]
  (apply dissoc m (for [[k v] m :when (nil? v)] k)))

(defn collections
  "gets the user's collections"
  ([{:keys [index number sort-field sort-direction], :or {index 0 number -1}}]
   (->> (.getAllCollections @conn index number sort-field sort-direction)
        (from-java)
        (map remove-nils)))
  ([] (collections {})))

(defn select
  "utility wrapper around select-keys"
  [keys coll]
  (map #(select-keys % keys) coll))

(defn use-collection
  "sets colId as the default collection"
  [colId]
  (reset! collection colId))

(defn documents
  "gets the documents belonging to a collection"
  ([colId]
   (->> colId
        (.getAllDocs @conn)
        (from-java)
        (map remove-nils)
        (map #(dissoc % :colList))))
  ([]
   (documents @collection)))

(defn pages
  "gets a document's pages"
  ([colId docId numTranscripts]
   (->> (.getTrpDoc @conn colId docId numTranscripts)
        (.getPages)
        (from-java)
        (map remove-nils)
        (map #(dissoc % :transcriptsStr :image))))
  ([colId docId]
   (pages colId docId -1))
  ([docId]
   (pages @collection docId)))

(defn models
  "gets the models belonging to a collection"
  ([colId]
   (->> (.getHtrs @conn colId "CITlab")
        (from-java)
        (map remove-nils)
        (map #(dissoc % :cerString :cerTestString))))
  ([]
   (models @collection)))

(defn jobs
  "gets the user's jobs"
  ([{:keys [status type docId index number sort-field sort-direction], :or {index 0 number -1}}]
   (->> (.getJobs @conn true status type docId index number sort-field sort-direction)
        (from-java)
        (map remove-nils)))
  ([] (jobs {})))

(defn job
  "gets a single job's details"
  [jobId]
  (->> jobId
       (str)
       (.getJob @conn)
       (from-java)
       (remove-nils)))

(defn cancel-job
  "cancels a running job"
  [jobId]
  (->> jobId
       (str)
       (.killJob @conn)))

(defn run-model
  "runs a model"
  ([htrId colId docId pages]
   (Integer/parseInt (.runCitLabHtr @conn colId docId pages htrId nil)))
  ([htrId docId pages]
   (run-model htrId @collection docId pages)))

(defn- dsds [coll]
  (doto (ArrayList.)
    (.addAll (map (fn [[docId pageId]] (DocumentSelectionDescriptor. docId pageId)) coll))))

(defn train-model
  "trains a model"
  [colId modelName language description trainDocIds testDocIds & opts]
  (let [tr (dsds trainDocIds) tt (dsds testDocIds)]
    (->> {:colId colId :modelName modelName :train tr :test tt :description description :language language}
         (merge opts)
         (to-java CitLabHtrTrainConfig)
         (.runCitLabHtrTraining @conn)
         (Integer/parseInt))))
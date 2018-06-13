(ns transkript.core
  (:import (eu.transkribus.client.connection TrpServerConn)
           (eu.transkribus.core.model.beans TrpTranscriptStatistics TrpTotalTranscriptStatistics CitLabHtrTrainConfig DocumentSelectionDescriptor DocumentSelectionDescriptor$PageDescriptor)
           (java.net URL)
           (java.util Date ArrayList))
  (:require [clojure.edn :as edn])
  (:use [clojure.java.data]))

(def conn (atom nil))
(def collection (atom nil))
(def model (atom nil))
(def language (atom nil))

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

(defn cancel
  "cancels a running job"
  [jobId]
  (->> jobId
       (str)
       (.killJob @conn)))

(defn status
  "gets a job's status"
  [jobId]
  (keyword (:state (job jobId))))

(defn use-model
  "sets the default model"
  [htrId]
  (reset! model htrId))

; this version of runCitLabHtr isn't working right now
(defn- dsd [docId pages]
  (reduce (fn [dd page]
            (doto dd (.addPage (DocumentSelectionDescriptor$PageDescriptor. page))))
          (DocumentSelectionDescriptor. docId) pages))

(defn run-model
  "runs a model"
  ([colId htrId docId pages]
   (let [pgs (if (string? pages) pages (clojure.string/join "," pages))]
     (Integer/parseInt (.runCitLabHtr @conn colId docId pgs htrId nil))))
  ([htrId docId pages]
   (run-model @collection htrId docId pages))
  ([docId pages]
   (run-model @collection @model docId pages)))

(defn transcripts [docId pgnums]
  "selects transcripts corresponding to the pages in the given document for training"
  (let [ps (set pgnums)
        ts (->> docId
                (pages)
                (filter (comp ps :pageNr))
                (map :currentTranscript))]
    (map (fn [t] (map t [:docId :pageId :tsId])) ts)))

(defn set-language
  "sets the language for training models"
  [lang]
  (reset! language lang))

(defn- dsdt [[docId pageId tsId]]
  (doto (DocumentSelectionDescriptor. docId)
    (.addPage (DocumentSelectionDescriptor$PageDescriptor. pageId tsId))))

(defn train-model
  "trains a model"
  [modelName description train test & opts]
  (let [tr (map dsdt train) ts (map dsdt test)]
    (->> {:colId @collection :language @language :modelName modelName :train tr :test ts :description description}
         (merge opts)
         (to-java CitLabHtrTrainConfig)
         (.runCitLabHtrTraining @conn)
         (Integer/parseInt))))
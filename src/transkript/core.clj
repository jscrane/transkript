(ns transkript.core
  (:import (eu.transkribus.client.connection TrpServerConn)
           (eu.transkribus.core.model.beans TrpTranscriptStatistics TrpTotalTranscriptStatistics CitLabHtrTrainConfig DocumentSelectionDescriptor DocumentSelectionDescriptor$PageDescriptor)
           (eu.transkribus.core.model.beans.rest ParameterMap)
           (java.net URL)
           (java.util Date))
  (:require [clojure.edn :as edn]
            [clojure.string :as str])
  (:use [clojure.java.data]))

(def conn (atom nil))
(def collection (atom nil))
(def model (atom nil))
(def dictionary (atom nil))
(def config (atom {}))

(defn set-config
  "Sets configuration information."
  [m]
  (reset! config m))

(defn load-config
  "Loads configuration information from a file."
  [filename]
  (set-config (edn/read-string (slurp filename))))

(defn connect
  "Connects to remote transkribus server."
  ([params]
   (let [m (merge @config params)
         [#^String s #^String u #^String p] (map m [:server :username :password])]
     (reset! conn (TrpServerConn. s u p))))
  ([]
   (connect @config)))

(defn close
  "Closes connection to transkribus server."
  []
  (.close @conn))

(defmethod from-java TrpTranscriptStatistics [_])

(defmethod from-java TrpTotalTranscriptStatistics [_])

(defmethod from-java URL [o] (str o))

(defmethod from-java Date [o] (str o))

(defn- remove-nils [m]
  (apply dissoc m (for [[k v] m :when (nil? v)] k)))

(defn collections
  "Returns the user's collections."
  ([{:keys [index number sort-field sort-direction], :or {index 0 number -1}}]
   (->> (.getAllCollections @conn index number sort-field sort-direction)
        (from-java)
        (map remove-nils)))
  ([] (collections {})))

(defn select
  "For each map in coll, returns a subset of its keys."
  [k coll]
  (let [keys (if (keyword? k) [k] k)]
    (map #(select-keys % keys) coll)))

(defn use-collection
  "Sets the default collection."
  [colId]
  (reset! collection colId))

(defn documents
  "Gets the documents belonging to a collection."
  ([colId]
   (->> colId
        (.getAllDocs @conn)
        (from-java)
        (map remove-nils)
        (map #(dissoc % :colList))))
  ([]
   (documents @collection)))

(defn pages
  "Gets a document's pages."
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

(defn pages-numbered
  "Selects numbered pages."
  [pgnums pages]
  (let [ps (set pgnums)]
    (filter (comp ps :pageNr) pages)))

(defn dsdt [{:keys [docId pageId tsId], :or {tsId (int -1)}}]
  (doto (DocumentSelectionDescriptor. docId)
    (.addPage (DocumentSelectionDescriptor$PageDescriptor. pageId tsId))))

(defn jobs
  "Gets the user's jobs."
  ([{:keys [status type docId index number sort-field sort-direction], :or {index 0 number -1 sort-field :created}}]
   (->> (.getJobs @conn true status type docId index number (name sort-field) sort-direction)
        (from-java)
        (map remove-nils)))
  ([] (jobs {})))

(defn job
  "Gets a job's details."
  [jobId]
  (->> (str jobId)
       (.getJob @conn)
       (from-java)
       (remove-nils)))

(defn cancel
  "Cancels a running job."
  [jobId]
  (.killJob @conn (str jobId)))

(defn status
  "Gets a job's status."
  [jobId]
  (keyword (:state (job jobId))))

;; method may be one of :CITlabAdvanced, :Cvl or :NcsrOld
(defn analyse-layout
  "Runs layout analysis."
  ([colId method pages {:keys [block-seg line-seg word-seg], :or {block-seg false line-seg false word-seg false}}]
   (let [dsds (map dsdt pages)
         mm (str (name method) "LaJob")]
     (->> (.analyzeLayout @conn colId dsds block-seg line-seg word-seg false false mm (ParameterMap.))
          (from-java)
          (map :jobId)
          (map #(Integer/parseInt %)))))
  ([method pages params]
   (analyse-layout @collection method pages params)))

(defn models
  "Gets the models belonging to a collection."
  ([colId provider]
   (->> (.getHtrs @conn colId provider)
        (from-java)
        (map remove-nils)
        (map #(dissoc % :cerString :cerTestString))))
  ([colId]
   (models colId "CITlab"))
  ([]
   (models @collection)))

(defn use-model
  "Sets the default model."
  [htrId]
  (reset! model htrId))

(defn use-dictionary
  "Sets the default dictionary."
  [dict]
  (reset! dictionary dict))

(defn run-model
  "Runs a model."
  ([colId htrId docId pages]
   (let [pgs (if (string? pages) pages (str/join "," pages))]
     (Integer/parseInt (.runCitLabHtr @conn colId docId pgs htrId @dictionary))))
  ([htrId docId pages]
   (run-model @collection htrId docId pages))
  ([docId pages]
   (run-model @collection @model docId pages)))

; FIXME: test this, and replace run-model above
(comment
  (defn dsd [pages]
    (doto (DocumentSelectionDescriptor. (:docId (first pages)))
      (.setPages (map #(DocumentSelectionDescriptor$PageDescriptor. (:pageId %) -1) pages))))

  (defn run-model
    "Runs a model."
    ([colId htrId pages dict]
     (Integer/parseInt (.runCitLabHtr @conn colId (dsd pages) htrId dict)))
    ([colId htrId pages]
     (run-model colId htrId pages @dictionary))
    ([htrId pages]
     (run-model @collection htrId pages))
    ([pages]
     (run-model @collection @model pages))))

(defn transcripts
  "Selects transcripts corresponding to the pages in the given document."
  [docId pgnums]
  (->> (pages docId)
       (pages-numbered pgnums)
       (map :transcripts)))

(defn with-status
  "Selects transcripts with status."
  [status transcripts]
  (let [s (into #{} (map name (if (keyword? status) [status] status)))]
    (filter (comp s :status) transcripts)))

(defn newest
  "Selects newest transcript."
  [transcripts]
  (last (sort-by :timestamp transcripts)))

(defn gt-transcripts
  "Selects the newest transcripts labelled GT from the pages in the given document."
  [docId pgnums]
  (->> (transcripts docId pgnums)
       (map (partial with-status :GT))
       (map newest)))

(defn set-language
  "Sets the default language for training."
  [lang]
  (reset! config (assoc @config :language lang)))

(defn train-model
  "Trains a model."
  [modelName description train test & opts]
  (let [tr (map dsdt train) ts (map dsdt test)]
    (->> {:colId @collection :language (:language @config) :modelName modelName :train tr :test ts :description description}
         (merge opts)
         (to-java CitLabHtrTrainConfig)
         (.runCitLabHtrTraining @conn)
         (Integer/parseInt))))

(defn accuracy
  "Computes word- and character-error rates between two transcripts."
  [refKey hypKey]
  (->> (str/split (.computeWer @conn refKey hypKey) #"\n")
       (partition 2)
       (reduce (fn [m [a b]] (assoc m (keyword a) (Float/parseFloat b))) {})))
(ns transkript.core
  (:import (eu.transkribus.client.connection TrpServerConn)
           (eu.transkribus.core.model.beans TrpTranscriptStatistics TrpTotalTranscriptStatistics CitLabHtrTrainConfig DocumentSelectionDescriptor DocumentSelectionDescriptor$PageDescriptor)
           (eu.transkribus.core.model.beans.rest ParameterMap)
           (java.net URL)
           (java.util Date))
  (:require [clojure.edn :as edn]
            [clojure.string :as str])
  (:use [clojure.java.data]
        [clojure.java.io :as io]))

(def config (atom {}))

(defn set-config
  "Sets configuration information."
  [m]
  (reset! config m))

(defn load-config
  "Loads configuration information from a file."
  [filename]
  (set-config (edn/read-string (slurp (io/resource filename)))))

(def conn (atom nil))

(defn- get-conn []
  (if @conn
    @conn
    (throw (IllegalStateException. "not logged in"))))

(defn login
  "Login to remote transkribus server."
  ([params]
   (let [m (merge @config params)
         [#^String s #^String u #^String p] (map m [:server :username :password])]
     (reset! conn (TrpServerConn. s u p))))
  ([]
   (login @config)))

(defn logout
  "Logout from remote transkribus server."
  []
  (.close (get-conn))
  (reset! conn nil))

(def collection (atom nil))

(defn- get-collection []
  (if @collection
    @collection
    (throw (IllegalStateException. "no default collection"))))

(defn use-collection
  "Sets the default collection."
  [coll]
  (reset! collection coll))

(def model (atom nil))

(defn- get-model []
  (if @model
    @model
    (throw (IllegalStateException. "no default model"))))

(defn use-model
  "Sets the default model."
  [model]
  (reset! model model))

(def dictionary (atom nil))

(defn use-dictionary
  "Sets the default dictionary."
  [dict]
  (reset! dictionary dict))

(defmethod from-java TrpTranscriptStatistics [_])

(defmethod from-java TrpTotalTranscriptStatistics [_])

(defmethod from-java URL [o] (str o))

(defmethod from-java Date [o] (str o))

(defmethod from-java Boolean [o] (Boolean/valueOf o))

(defn- remove-nils [m]
  (apply dissoc m (for [[k v] m :when (nil? v)] k)))

(defn collections
  "Returns the user's collections."
  ([{:keys [index number sort-field sort-direction], :or {index 0 number -1}}]
   (->> (.getAllCollections (get-conn) index number sort-field sort-direction)
        (from-java)
        (map remove-nils)))
  ([] (collections {})))

(defn select
  "For each map in coll, returns a subset of its keys."
  [k coll]
  (let [keys (if (keyword? k) [k] k)]
    (map #(select-keys % keys) coll)))

(defn- colId [coll]
  (if (integer? coll) coll (:colId coll)))

(defn documents
  "Gets the documents belonging to a collection."
  ([coll]
   (->> (colId coll)
        (.getAllDocs (get-conn))
        (from-java)
        (map remove-nils)
        (map #(dissoc % :colList))))
  ([]
   (documents (get-collection))))

(defn- docId [doc]
  (if (integer? doc) doc (:docId doc)))

(defn pages
  "Gets a document's pages."
  ([coll doc numTranscripts]
   (->> (.getTrpDoc (get-conn) (colId coll) (docId doc) numTranscripts)
        (.getPages)
        (from-java)
        (map remove-nils)
        (map #(dissoc % :transcriptsStr :image))))
  ([coll doc]
   (pages coll doc -1))
  ([doc]
   (pages (get-collection) doc)))

(defn pages-numbered
  "Selects numbered pages."
  [pgnums pages]
  (let [ps (set pgnums)]
    (filter (comp ps :pageNr) pages)))

(defn jobs
  "Gets the user's jobs."
  ([{:keys [status type docId index number sort-field sort-direction], :or {index 0 number -1 sort-field :created}}]
   (->> (.getJobs (get-conn) true status type docId index number (name sort-field) sort-direction)
        (from-java)
        (map remove-nils)))
  ([] (jobs {})))

(defn job
  "Gets a job's details."
  [jobId]
  (->> (str jobId)
       (.getJob (get-conn))
       (from-java)
       (remove-nils)))

(defn cancel
  "Cancels a running job."
  [jobId]
  (.killJob (get-conn) (str jobId)))

(defn status
  "Gets a job's status."
  [jobId]
  (keyword (:state (job jobId))))

(defn wait
  "Waits for jobs to complete."
  ([jobIds millis]
   (loop [jobIds (if (integer? jobIds) [jobIds] jobIds)
          finished []]
     (let [jobs (map job jobIds)
           u (map :jobIdAsInt (filter (comp not :finished) jobs))
           f (concat finished (filter :finished jobs))]
       (if (empty? u)
         f
         (do
           (Thread/sleep millis)
           (recur u f))))))
  ([jobIds]
   (wait jobIds 3000)))

(defn- dsdt [{:keys [docId pageId tsId], :or {tsId (int -1)}}]
  (doto (DocumentSelectionDescriptor. docId)
    (.addPage (DocumentSelectionDescriptor$PageDescriptor. pageId tsId))))

;; method may be one of :CITlabAdvanced, :Cvl or :NcsrOld
(defn analyse-layout
  "Runs layout analysis."
  ([coll method pages {:keys [block-seg line-seg word-seg], :or {block-seg false line-seg false word-seg false}}]
   (let [dsds (map dsdt pages)
         mm (str (name method) "LaJob")]
     (->> (.analyzeLayout (get-conn) (colId coll) dsds block-seg line-seg word-seg false false mm (ParameterMap.))
          (from-java)
          (map :jobId)
          (map #(Integer/parseInt %)))))
  ([method pages params]
   (analyse-layout (get-collection) method pages params)))

(defn models
  "Gets the models belonging to a collection."
  ([coll provider]
   (->> (.getHtrs (get-conn) (colId coll) provider)
        (from-java)
        (map remove-nils)
        (map #(dissoc % :cerString :cerTestString))))
  ([coll]
   (models coll "CITlab"))
  ([]
   (models (get-collection))))

(defn- dsd [pages]
  (doto (DocumentSelectionDescriptor. (:docId (first pages)))
    (.setPages (map #(DocumentSelectionDescriptor$PageDescriptor. (:pageId %) (int -1)) pages))))

(defn- htrId [model]
  (if (integer? model) model (:htrId model)))

(defn run-model
  "Runs a model."
  ([coll model pages dict]
   (Integer/parseInt (.runCitLabHtr (get-conn) (colId coll) (dsd pages) (htrId model) dict)))
  ([coll model pages]
   (run-model coll model pages @dictionary))
  ([model pages]
   (run-model (get-collection) model pages))
  ([pages]
   (run-model (get-collection) (get-model) pages)))

(defn transcripts
  "Selects transcripts corresponding to the pages in the given document."
  [doc pgnums]
  (->> (pages doc)
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
  [doc pgnums]
  (->> (transcripts doc pgnums)
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
    (->> {:colId (colId (get-collection)) :language (:language @config) :modelName modelName :train tr :test ts :description description}
         (merge opts)
         (to-java CitLabHtrTrainConfig)
         (.runCitLabHtrTraining (get-conn))
         (Integer/parseInt))))

(defn accuracy
  "Computes word- and character-error rates between two transcripts."
  [refKey hypKey]
  (->> (str/split (.computeWer (get-conn) refKey hypKey) #"\n")
       (partition 2)
       (reduce (fn [m [a b]] (assoc m (keyword a) (Float/parseFloat b))) {})))
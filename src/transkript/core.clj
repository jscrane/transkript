(ns transkript.core
  (:import (eu.transkribus.client.connection TrpServerConn)
           (eu.transkribus.core.model.beans TrpTranscriptStatistics TrpTotalTranscriptStatistics CitLabHtrTrainConfig DocumentSelectionDescriptor DocumentSelectionDescriptor$PageDescriptor)
           (eu.transkribus.core.model.beans.rest ParameterMap)
           (java.net URL)
           (java.util Date)
           (eu.transkribus.core.model.beans.enums ScriptType)
           (java.io File)
           (eu.transkribus.core.io LocalDocReader LocalDocReader$DocLoadConfig DocExporter)
           (org.dea.fimgstoreclient.beans ImgType))
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
  [m]
  (reset! model m))

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

(defn- document [coll doc num-transcripts]
  (.getTrpDoc (get-conn) (colId coll) (docId doc) num-transcripts))

(defn pages
  "Gets a document's pages."
  ([coll doc num-transcripts]
   (->> (document coll doc num-transcripts)
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

(defn- jobId [job]
  (cond
    (integer? job) (str job)
    (string? job) job
    :else (:jobId job)))

(defn job
  "Gets a job's details."
  [job]
  (->> (jobId job)
       (.getJob (get-conn))
       (from-java)
       (remove-nils)))

(defn cancel
  "Cancels a running job."
  [job]
  (.killJob (get-conn) (jobId job)))

(defn status
  "Gets a job's status."
  [jobId]
  (keyword (:state (job jobId))))

(defn wait
  "Waits for a job to complete."
  ([j millis]
   (loop []
     (let [state (job j)]
       (if (:finished state)
         state
         (do
           (Thread/sleep millis)
           (recur))))))
  ([j]
   (wait j (:poll @config))))

(defn wait-all
  "Waits for multiple jobs to complete.

  if not supplied 'millis' defaults to the value of :poll in the config."
  ([jobs millis]
   (loop [jobs jobs finished []]
     (let [update (map job jobs)
           unfinished (map :jobIdAsInt (filter (comp not :finished) update))
           finished (concat finished (filter :finished update))]
       (if (empty? unfinished)
         finished
         (do
           (Thread/sleep millis)
           (recur unfinished finished))))))
  ([jobIds]
   (wait-all jobIds (:poll @config))))

(defn- dsdt [{:keys [docId pageId tsId], :or {tsId (int -1)}}]
  (doto (DocumentSelectionDescriptor. docId)
    (.addPage (DocumentSelectionDescriptor$PageDescriptor. pageId tsId))))

(defn analyse-layout
  "Runs layout analysis.

  'method' may be one of :CITlabAdvanced, :Cvl or :NcsrOld
  'params' is a map with possible keys :block-seg :line-seg :word-seg"
  ([coll method pages {:keys [block-seg line-seg word-seg], :or {block-seg false line-seg false word-seg false}}]
   (let [dsds (map dsdt pages)
         mm (str (name method) "LaJob")]
     (->> (.analyzeLayout (get-conn) (colId coll) dsds block-seg line-seg word-seg false false mm (ParameterMap.))
          (from-java)
          (map :jobIdAsInt))))
  ([method pages params]
   (analyse-layout (get-collection) method pages params))
  ([method pages]
   (analyse-layout method pages {})))

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
  "Runs an HTR model over a page or pages from a document in a collection, with an optional dictionary."
  ([coll model pages dict]
   (let [pages (if (map? pages) [pages] pages)]
     (Integer/parseInt (.runCitLabHtr (get-conn) (colId coll) (dsd pages) (htrId model) dict))))
  ([coll model pages]
   (run-model coll model pages @dictionary))
  ([model pages]
   (run-model (get-collection) model pages))
  ([pages]
   (run-model (get-collection) (get-model) pages)))

(defn run-ocr
  "Runs OCR on one or more pages from a document in a collection."
  ([coll pages typeface languages]
   (let [pages (if (map? pages) [pages] pages)
         languages (if (coll? languages) (str/join "," languages) languages)
         doc (:docId (first pages))
         pagestr (str/join "," (map :pageNr pages))]
     (Integer/parseInt (.runOcr (get-conn) (colId coll) (docId doc) pagestr (ScriptType/fromString typeface) languages))))
  ([pages typeface languages]
   (run-ocr (get-collection) pages typeface languages))
  ([pages typeface]
   (run-ocr pages typeface (:language @config)))
  ([pages]
   (run-ocr pages (:typeface @config) (:language @config))))

(defn transcripts
  "Selects transcripts corresponding to numbered pages in a document."
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
  (first (sort-by :timestamp > transcripts)))

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
  [name description train test & opts]
  (let [tr (map dsdt train) ts (map dsdt test)]
    (->> {:colId (colId (get-collection)) :language (:language @config) :modelName name :train tr :test ts :description description}
         (merge opts)
         (to-java CitLabHtrTrainConfig)
         (.runCitLabHtrTraining (get-conn))
         (Integer/parseInt))))

(defn- tsKey [ts]
  (if (string? ts) ts (:key ts)))

(defn accuracy
  "Computes word- and character-error rates between two transcripts, the \"ground truth\" and the \"hypothesis\".
  Alternatively if given a sequence of transcripts, picks the newest \"ground truth\" and \"hypothesis\" from them."
  ([gt hyp]
   (let [gtk (tsKey gt) htk (tsKey hyp)]
     (->> (str/split (.computeWer (get-conn) gtk htk) #"\n")
          (partition 2)
          (reduce (fn [m [a b]] (assoc m (keyword a) (Float/parseFloat b))) {:gt gtk, :hyp htk}))))
  ([ts]
   (let [hyp (newest (with-status :IN_PROGRESS ts))
         gt (newest (with-status :GT ts))]
     (accuracy gt hyp))))

(defn import-document
  "Imports a new document from a set of images in a local folder."
  ([coll title ^String folder]
   (LocalDocReader/checkInputDir (File. folder))
   (let [doc (LocalDocReader/load folder (LocalDocReader$DocLoadConfig.) nil)]
     (-> doc (.getMd) (.setTitle title))
     (-> (get-conn) (.uploadTrpDoc (colId coll) doc nil) (.getJobId))))
  ([title folder]
   (import-document (get-collection) title folder)))

(defn export-document
  "Exports a document to the local filesystem."
  ([coll doc folder {:keys [overwrite images pages alto alto-word image-type]
                     :or   {overwrite false images false pages true alto false alto-word false image-type nil}}]
   (let [doc (document coll doc -1)
         pagenums (into #{} (map #(.getPageNr %) (.getPages doc)))]
     (-> (DocExporter.)
         (.writeRawDoc doc folder overwrite pagenums images pages alto alto-word nil image-type)
         (.getAbsolutePath))))
  ([coll doc folder]
   (export-document coll doc folder {:overwrite true :images true :pages true :image-type ImgType/orig}))
  ([doc folder]
   (export-document (get-collection) doc folder)))
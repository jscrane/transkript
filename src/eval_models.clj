(ns eval-models
  (:require [transkript.core :as tk]
            [transkript.util :as tu]
            [clojure.string :as string]
            [cli :refer [validate-args]])
  (:gen-class))

(def opts
  [["-u" "--username NAME" "User's Name"]
   ["-p" "--password PASS" "User's Password"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Runs all of the Models associated with a Collection on the first page of a Document."
        ""
        "Usage: eval-models [options] Collection Document"
        ""
        "Options:"
        options-summary
        ""
        "Collection and Document are Collection and Document names or IDs."]
       (string/join \newline)))

(defn eval-model [model page]
  (let [job (tk/wait (tk/run-model model page))
        res {:model (:name model) :job (:jobIdAsInt job)}]
    (if (:success job)
      (let [ts (first (tk/transcripts (:docId page) [(:pageNr page)]))]
        (merge res (tk/accuracy ts)))
      (assoc res :failed true))))

(defn -main [& args]
  (let [{:keys [arguments options exit-message]} (validate-args args 2 opts usage)]
    (if exit-message
      (println exit-message)
      (let [[coll doc] arguments]
        (tk/load-config "config.edn")
        (tk/login options)
        (tk/use-collection (tu/find-collection coll))
        (let [document (tu/find-document doc)
              page (first (tk/pages-numbered [1] (tk/pages document)))]
          (doseq [m (tk/models)]
            (println (eval-model m page))))
        (tk/logout)))))
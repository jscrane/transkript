(ns run-ocr
  (:require [transkript.core :as tk]
            [transkript.util :as tu]
            [cli :refer [validate-args]]
            [clojure.string :as string])
  (:import (java.io File)))

(def opts
  [["-u" "--username NAME" "User's Name"]
   ["-p" "--password PASS" "User's Password"]
   ["-t" "--typeface FACE" "OCR Typeface to use" :default "Combined"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Creates a new Document in a Collection from a set of images in a Folder,"
        "Runs OCR on it, and downloads the Transcript."
        ""
        "Usage: run-ocr [options] Collection Document Folder"
        ""
        "Options:"
        options-summary
        ""
        "Collection is the name, or ID, of the Collection to use."
        "Document is the name of the Document to create."
        "Folder contains the images on which the new Document is based."]
       (string/join \newline)))

(defn assert-success [job msg]
  (if (:success job)
    job
    (throw (Exception. (str msg (:jobId job))))))

(defn -main [& args]
  (let [{:keys [arguments options exit-message]} (validate-args args 3 opts usage)]
    (if exit-message
      (println exit-message)
      (let [[coll docname folder] arguments
            typeface (:typeface options)]

        (tk/load-config "config.edn")
        (tk/login options)
        (tk/use-collection (tu/find-collection coll))

        (-> (tk/import-document docname folder)
            (tk/wait)
            (assert-success "Import Failed")
            (:docId)
            (tk/pages)
            (tk/run-ocr typeface)
            (tk/wait)
            (assert-success "OCR Failed")
            (:docId)
            (tk/export-text (str folder File/separator typeface ".txt")))

        (tk/logout)))))
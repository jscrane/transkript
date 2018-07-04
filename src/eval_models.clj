(ns eval-models
  (:require [transkript.core :as tk]
            [clojure.string :as string]
            [clojure.tools.cli :as cli :refer [parse-opts]]))

(def opts
  [["-u" "--username NAME" "User's Name"]
   ["-p" "--password PASS" "User's Password"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Usage: program-name [options] Collection Document"
        ""
        "Options:"
        options-summary
        ""
        "Collection and Document are Collection and Document names."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "Errors: \n\n" (string/join \newline errors)))

(defn validate-args [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args opts)]
    (cond
      (:help options) {:exit-message (usage summary)}
      errors {:exit-message (error-msg errors)}
      (and (= 2 (count arguments))) {:arguments arguments :options options}
      :else {:exit-message (usage summary)})))

(defn eval-model [model page]
  (let [job (tk/wait (tk/run-model model page))
        res {:model (:name model) :job (:jobIdAsInt job)}]
    (if (:success job)
      (let [ts (first (tk/transcripts (:docId page) [(:pageNr page)]))]
        (merge res (tk/accuracy ts)))
      (assoc res :failed true))))

(defn -main [& args]
  (let [{:keys [arguments options exit-message]} (validate-args args)]
    (if exit-message
      (println exit-message)
      (let [[colname docname] arguments]
        (tk/load-config "config.edn")
        (tk/login options)
        (tk/use-collection (first (filter #(= colname (:colName %)) (tk/collections))))
        (let [doc (first (filter #(= docname (:title %)) (tk/documents)))
              page (first (tk/pages-numbered [1] (tk/pages doc)))]
          (doseq [m (tk/models)]
            (println (eval-model m page))))
        (tk/logout)))))
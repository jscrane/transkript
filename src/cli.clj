(ns cli
  (:require [clojure.string :as string]
            [clojure.tools.cli :as cli :refer [parse-opts]]))

(defn error-msg [errors]
  (str "Errors: \n\n" (string/join \newline errors)))

(defn validate-args [args nargs opts usage]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args opts)]
    (cond
      (:help options) {:exit-message (usage summary)}
      errors {:exit-message (error-msg errors)}
      (= nargs (count arguments)) {:arguments arguments :options options}
      :else {:exit-message (usage summary)})))
(ns transkript.util
  (:require [transkript.core :as tk]))

(defn find-collection
  "Finds a Collection by Name or ID."
  [coll]
  (first (filter #(or (= coll (:colName %)) (= coll (str (:colId %)))) (tk/collections))))

(defn find-document
  "Finds a Document (in the default collection) by Name or ID"
  [doc]
  (first (filter #(or (= doc (:title %)) (= doc (str (:docId %)))) (tk/documents))))

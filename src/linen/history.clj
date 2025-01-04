(ns linen.history
  (:require [clojure.java.io :as io]))

(def history-file-path "file-history.txt")

(defn read-history []
  (when-let [lines (line-seq (io/reader history-file-path))]
    lines))

(defn append-to-history [file-path]
  (when (not (read-history))
    (spit history-file-path (str file-path "\n") :append true)))
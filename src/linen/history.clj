(ns linen.history
  (:require [clojure.java.io :as io]))


(def history-file-path (str (System/getProperty "user.home") "/file-history.txt"))
;(def history-file-path "/tmp/file-history.txt")

(defn read-history []
  (if (.exists (io/file history-file-path))
    (with-open [reader (io/reader history-file-path)]
      (doall (line-seq reader)))
    []))

(defn append-to-history [file-path]
  (let [history (set (read-history))]
    (when-not (history file-path)
      (spit history-file-path (str file-path "\n") :append true))))
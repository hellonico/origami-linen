(ns excel-viewer.core
  (:require [cljfx.api :as fx]
            [dk.ative.docjure.spreadsheet :as ss]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io])
  (:import (javafx.scene.input DragEvent TransferMode)
           (org.apache.poi.ss.usermodel Cell)))

(def history-file-path "file-history.txt")

;; Read history from a local file
(defn read-history []
  (if (.exists (io/file history-file-path))
    (with-open [reader (io/reader history-file-path)]
      (doall (line-seq reader)))
    []))

;; Append a new file to the history
(defn append-to-history [file-path]
  (let [history (set (read-history))]
    (when-not (history file-path)
      (spit history-file-path (str file-path "\n") :append true))))

;; Parse Excel file
(defn read-excel [file-path]
  (let [workbook (ss/load-workbook file-path)
        sheet (ss/select-sheet "Sheet1" workbook)
        rows (ss/row-seq sheet)
        headers (map #(-> % .getStringCellValue keyword)
                     (filter #(instance? Cell %)
                             (ss/cell-seq (first rows))))
        data (map (fn [row]
                    (zipmap headers (map #(when (instance? Cell %)
                                            (try (.toString %)
                                                 (catch Exception _ nil)))
                                         (ss/cell-seq row))))
                  (rest rows))]
    {:headers headers :rows data}))

;; Parse CSV file
(defn read-csv [file-path]
  (with-open [reader (io/reader file-path)]
    (let [lines (doall (csv/read-csv reader))
          headers (map keyword (first lines))
          rows (map #(zipmap headers %) (rest lines))]
      {:headers headers :rows rows})))

;; App state
(def *state
  (atom {:rows [] :headers [] :history (read-history) :selected-file nil}))

;; Load file into state
(defn load-file [file-path]
  (try
    (let [file-name (.getName (io/file file-path))]
      (cond
        (.endsWith file-name ".xlsx")
        (let [excel-data (read-excel file-path)]
          (swap! *state assoc :headers (:headers excel-data) :rows (:rows excel-data)))

        (.endsWith file-name ".csv")
        (let [csv-data (read-csv file-path)]
          (swap! *state assoc :headers (:headers csv-data) :rows (:rows csv-data)))

        :else
        (println "Unsupported file type:" file-name)))
    (catch Exception e
      (println "Failed to load file:" (.getMessage e)))))

;; Handle drag-and-drop events
(defn handle-drag-dropped [_state event]
  (let [db (.getDragboard event)
        files (.getFiles db)
        file (first files)]
    (when file
      (let [file-path (.getAbsolutePath file)]
        (append-to-history file-path)
        (swap! *state update :history (fn[x] (cons file-path (remove #(= % file-path) x))))
        (swap! *state assoc :selected-file file-path)
        (load-file file-path)))
    (.consume event)))

;; App view
(defn app-view [state]
  {:fx/type :stage
   :showing true
   :title "Excel & CSV Viewer"
   :scene {:fx/type :scene
           :on-drag-over (fn [^DragEvent event]
                           (let [db (.getDragboard event)]
                             (when (.hasFiles db)
                               (doto event
                                 (.acceptTransferModes (into-array TransferMode [TransferMode/COPY]))))))
           :on-drag-dropped #(handle-drag-dropped state %)
           :root {:fx/type :v-box
                  :children [{:fx/type :combo-box
                              :prompt-text "Select a file..."
                              :value (:selected-file state)
                              :on-value-changed (fn [new-file]
                                                  (when new-file
                                                    (swap! *state assoc :selected-file new-file)
                                                    (load-file new-file)))
                              :items (:history state)}
                             {:fx/type :table-view
                              :columns (for [header (:headers state)]
                                         {:fx/type :table-column
                                          :text (name header)
                                          :cell-value-factory header})
                              :items (:rows state)}]}}})

;; Renderer
(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type app-view)
    :opts {:state *state}))

(defn -main []
  (fx/mount-renderer *state renderer))

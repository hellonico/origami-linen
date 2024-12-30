(ns excel-viewer.core
  (:require [cljfx.api :as fx]
            [dk.ative.docjure.spreadsheet :as ss])
  (:import (javafx.scene.input DragEvent TransferMode)))

;; Load Excel data into state
(defn read-excel [file-path]
  (let [workbook (ss/load-workbook file-path)
        sheet (ss/select-sheet "Sheet1" workbook)
        rows (ss/row-seq sheet)
        ;; Extract headers
        headers (map #(-> % .getStringCellValue keyword)
                     (filter #(instance? org.apache.poi.ss.usermodel.Cell %)
                             (ss/cell-seq (first rows))))
        ;; Extract rows and map to headers
        data (map (fn [row]
                    (zipmap headers (map #(when (instance? org.apache.poi.ss.usermodel.Cell %)
                                            (try (.toString %)
                                                 (catch Exception _ nil)))
                                         (ss/cell-seq row))))
                  (rest rows))]
    {:headers headers :rows data}))

;; Initial state with Excel data
(def state
  (atom
    {:rows [] :headers[] }
    ))

(defn handle-drag-dropped [_state event]
  (let [db (.getDragboard event)
        files (.getFiles db)
        file (first files)]
    (when (and file (.endsWith (.getName file) ".xlsx")) ;; Ensure it's an Excel file
      (try
        (let [excel-data (read-excel (.getAbsolutePath file))]
          (swap! state assoc :headers (:headers excel-data) :rows (:rows excel-data)))
        (catch Exception e
          (println "Failed to read Excel file:" (.getMessage e))))))
  (.consume event))

;; Create the Cljfx app view
(defn app-view [state]
  {:fx/type :stage
   :showing true
   :title "Excel Viewer"
   :scene {:fx/type :scene
           :on-drag-over (fn [^DragEvent event]
                           (let [db (.getDragboard event)]
                             (when (.hasFiles db)
                               (doto event
                                 (.acceptTransferModes (into-array TransferMode
                                                                   [TransferMode/COPY]))))))
           :on-drag-dropped #(handle-drag-dropped state %)
           :root {:fx/type  :v-box
                  :children [{:fx/type :table-view
                              :columns (for [header (:headers state)]
                                         {:fx/type            :table-column
                                          :text               (name header)
                                          :cell-value-factory header})
                              :items   (:rows state)}]}}})

;; Create renderer
(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type app-view)
    :opts {:state state}))

;; Mount the renderer
(defn -main []
  (fx/mount-renderer state renderer))

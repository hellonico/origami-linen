(ns linen.core
  (:gen-class)
  (:require [cljfx.api :as fx]
            [clojure.core.async :as async]
            [dk.ative.docjure.spreadsheet :as ss]
            [clojure.data.csv :as csv]
            [pyjama.state]
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

(defn to-markdown [{:keys [headers rows]}]
  (let [header-row (str "| " (clojure.string/join " | " (map name headers)) " |")
        separator-row (str "| " (clojure.string/join " | " (repeat (count headers) "---")) " |")
        data-rows (map (fn [row]
                         (str "| " (clojure.string/join " | " (map #(get row % "") headers)) " |"))
                       rows)]
    (clojure.string/join "\n" (concat [header-row separator-row] data-rows))))


;; Parse CSV file
(defn read-csv [file-path]
  (with-open [reader (io/reader file-path)]
    (let [lines (doall (csv/read-csv reader))
          headers (map keyword (first lines))
          rows (map #(zipmap headers %) (rest lines))]
      {:headers headers :rows rows})))

;; App state
(def *state
  (atom {
         :url      "http://localhost:11434"
         :model    "llama3.2"
         :prompt   ""
         :question ""
         :rows     []
         :headers  []
         :history  (read-history) :selected-file nil}))

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
        (swap! *state update :history (fn [x] (cons file-path (remove #(= % file-path) x))))
        (swap! *state assoc :selected-file file-path)
        (load-file file-path)))
    (.consume event)))

(defn left-panel [state]
  {:fx/type  :v-box
   ;:v-box/vgrow :always
   :children [{:fx/type          :combo-box
               :prompt-text      "Select a file..."
               ;:vbox/vgrow :always
               :value            (:selected-file state)
               :on-value-changed (fn [new-file]
                                   (when new-file
                                     (swap! *state assoc :selected-file new-file)
                                     (load-file new-file)))
               :items            (:history state)}
              {:fx/type     :table-view
               :columns     (for [header (:headers state)]
                              {:fx/type            :table-column
                               :text               (name header)
                               :cell-value-factory header})
               :v-box/vgrow :always
               :items       (:rows state)}]}
  )

(defn get-prompt [question]
  (let [
        prompt (str
                 "This is a markdown formatted table of data you have for analysis:\n"
                 (to-markdown @*state)
                 "\n"
                 question
                 )
        ]
    ;(clojure.pprint/pprint prompt)
    prompt
    )
  )

(defn right-panel [state]
  {:fx/type  :v-box
   :h-box/hgrow :always
   :spacing  10
   :padding  10
   :children [
              {:fx/type  :h-box
               :spacing  10
               :children [{:fx/type :label
                           :text    "URL:"}
                          {:fx/type         :text-field
                           :text            (:url state)
                           :on-text-changed #(do
                                               (swap! *state assoc :url %)
                                               (async/thread (pyjama.state/local-models *state)))}
                          {:fx/type :label
                           :text    "Model:"}
                          {:fx/type          :combo-box
                           :items            (:local-models state)
                           :value            (:model state)
                           :on-value-changed #(swap! *state assoc :model %)}
                          ]}
              {
               :fx/type :label
               :text    "Prompt:"}
              {:fx/type         :text-area
               :text            (:question state)
               :on-text-changed #(do
                                   (swap! *state assoc :question %)
                                   (swap! *state assoc :prompt (get-prompt %))
                                   )}
              (if (not (state :processing))
                {:fx/type   :button
                 :text      "Ask"
                 :on-action (fn [_] (pyjama.state/handle-submit *state))
                 }
                {
                 :fx/type :label
                 :text    "Thinking ..."}
                )
              {
               :fx/type :label
               :text    "Response:"}
              {:fx/type     :text-area
               :wrap-text   true
               :v-box/vgrow :always
               :text        (:response state)
               :editable    false}]})

;; App view
(defn app-view [state]
  {:fx/type :stage
   :showing true
   :title   "Pyjama Linen - Query Your Data"
   :scene   {:fx/type         :scene
             :stylesheets  #{"styles.css"}
             :on-drag-over    (fn [^DragEvent event]
                                (let [db (.getDragboard event)]
                                  (when (.hasFiles db)
                                    (doto event
                                      (.acceptTransferModes (into-array TransferMode [TransferMode/COPY]))))))
             :on-drag-dropped #(handle-drag-dropped state %)
             :root            {
                               :fx/type  :h-box
                               :spacing  10
                               :children [
                                          (left-panel state)
                                          (right-panel state)
                                          ]
                               }
             }})

;; Renderer
(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type app-view)
    :opts {:state *state}))

(defn -main []
  (async/thread (pyjama.state/local-models *state))
  (fx/mount-renderer *state renderer))
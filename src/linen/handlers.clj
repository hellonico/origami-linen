(ns linen.handlers
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [dk.ative.docjure.spreadsheet :as ss])
  (:import (javafx.scene.image Image)
           (org.apache.poi.ss.usermodel Cell)))

(defn extension-group [ext]
  (cond
    (#{:xlsx :xls :csv} ext) :table
    (#{:png :jpg} ext) :image
    (#{:txt :log :md} ext) :text
    (#{:clj :py :go :rs} ext) :code
    :else :unknown))

(defmulti handle-file-action
          (fn [action state]
            (let [file (:selected-file @state)]
              (if (nil? file)
                [action :no-file]
                [action (extension-group (keyword (last (clojure.string/split file #"\."))))]))))

(defmethod handle-file-action [:load :unknown] [_ state]
  (println "Unknown file type" (:selected-file @state)))

(defmethod handle-file-action [:preview :no-file] [_ _]
  {:fx/type     :text-area
   :v-box/vgrow :always})

(defmethod handle-file-action [:load :no-file] [_ _])

(defmethod handle-file-action [:suggest :no-file] [_ _]
  ["biggest waterfalls in the world"
   "best books by Haruki Murakami"
   ;"日本の首相、ラスト１０人のを教えてください"
   "Say hello in 10 different languages"
   ]
  )

(defn read-excel [file-path]
  (let [workbook (ss/load-workbook file-path)
        ; TODO: support for other sheets than the first one
        ;sheet (ss/select-sheet "Sheet1" workbook)
        sheet (first (ss/sheet-seq workbook))
        rows (ss/row-seq sheet)
        headers (map #(-> % .getStringCellValue keyword)
                     (filter #(instance? Cell %)
                             (ss/cell-seq (first rows))))
        data (map (fn [row]
                    (zipmap headers (map #(when (instance? Cell %)
                                            (try
                                              (.toString %)
                                              (catch Exception _ nil)))
                                         (ss/cell-seq row))))
                  (rest rows))]
    {:headers headers :rows data}))


(defn read-csv [file-path]
  (with-open [reader (io/reader file-path)]
    (let [lines (doall (csv/read-csv reader))
          headers (map keyword (first lines))
          rows (map #(zipmap headers %) (rest lines))]
      {:headers headers :rows rows})))

(defmethod handle-file-action [:load :table] [_ state]
  (let [
        file-path (:selected-file @state)
        file-name (.getName (io/file file-path))]
    (cond
      (.endsWith file-name ".xlsx")
      (let [excel-data (read-excel file-path)]
        (swap! state assoc
               :headers (:headers excel-data)
               :rows (:rows excel-data)))

      (.endsWith file-name ".csv")
      (let [csv-data (read-csv file-path)]
        (swap! state assoc
               :headers (:headers csv-data)
               :rows (:rows csv-data))))))


(defn to-markdown [{:keys [headers rows]}]
  (let [header-row (str "| " (clojure.string/join " | " (map name headers)) " |")
        separator-row (str "| " (clojure.string/join " | " (repeat (count headers) "---")) " |")
        data-rows (map (fn [row]
                         (str "| " (clojure.string/join " | " (map #(get row % "") headers)) " |"))
                       rows)]

    (clojure.string/join "\n" (concat [header-row separator-row] data-rows))))

(defmethod handle-file-action [:prompt :table] [_ state]
  (str
    "This is a markdown formatted table of data you have for analysis:\n"
    (to-markdown @state)
    "\n"
    (:question state)))

(defmethod handle-file-action [:preview :table] [_ state]
  {:fx/type     :table-view
   :columns     (for [header (:headers @state)]
                  {:fx/type            :table-column
                   :text               (name header)
                   :cell-value-factory header})
   :v-box/vgrow :always
   :items       (:rows @state)}
  )

(defmethod handle-file-action [:load :image] [_ state]
  (swap! state assoc :images [(:selected-file @state)]))

(defmethod handle-file-action [:preview :image] [_ state]
  {:fx/type
   :image-view
   :fit-width 500.0
   :preserve-ratio true
   ;:v-box/vgrow :always
   ;:fit-width (:fx/bind (fn [node] (* 0.5 (double (.getWidth (.getParent (.getParent node)))))))
   ;:fit-width (fn [node]
   ;             (let [parent (.getParent node)]
   ;               (if parent
   ;                 (* 0.5 (double (.getWidth parent)))
   ;                 500
   ;                 )))
   :image
   (Image. (io/input-stream (first (:images @state))))})


(defmethod handle-file-action [:prompt :image] [_ state]
  (str
    "Look at the image attached. \n"
    (:question @state)))

(defmethod handle-file-action [:load :text] [_ state]
  (println "Loading text file" (:selected-file @state)))

(defmethod handle-file-action [:prompt :no-file] [_ state]
    (:question @state))

(defmethod handle-file-action [:prompt :text] [_ state]
  (str
    "This is a text:\n"
    (slurp (:selected-file @state))
    "\n"
    (:question @state)))

(defmethod handle-file-action [:preview :text] [_ state]
  {:fx/type     :text-area
   :text        (slurp (:selected-file @state))
   :v-box/vgrow :always})

(defmethod handle-file-action [:preview :code] [_ state]
  {:fx/type     :text-area
   :text        (slurp (:selected-file @state))
   :v-box/vgrow :always})

(defmethod handle-file-action [:load :code] [_ state]
  (println "Loading code file" (:selected-file @state)))

(defmethod handle-file-action [:prompt :code] [_ state]
  (str
    "This is a code file:\n"
    (slurp (:selected-file @state))
    "\n"
    (:question @state)))

(defmethod handle-file-action [:suggest :table] [_ _]
  ["Describe the data"
   "Analyse the data"
   "Find some trends"
   "What are good prompts for this data set."
   ]
  )

(defmethod handle-file-action [:suggest :image] [_ _]
  ["Find text in the image."
   "Describe the image."
   "How many colors are in the image"
   "Is it a high resolution image."
   "what is the main season of the image."
   ]
  )

(defmethod handle-file-action [:suggest :text] [_ _]
  ["Summarize."
   "Ask 3 pertinent questions on the text."
   "Reword for a teenager."
   "Find grammatical errors in the text."
   ]
  )

(defmethod handle-file-action [:suggest :code] [_ _]
  ["Simplify the code."
   "What is the code doing."
   "Find obvious errors."
   "Shorten the code as much as possible."
   ]
  )
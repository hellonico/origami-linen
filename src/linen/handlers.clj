(ns linen.handlers
  (:require [clojure.java.io :as io]
            [pyjama.utils]
            [pyjama.io.readers]
            [pyjama.io.core :refer :all]
            )
  (:import (javafx.scene.control TableView)
           (javafx.scene.image Image)))

(defn extension-group [ext]
  (cond
    (#{:xlsx :xls :csv} ext) :table
    (#{:png :jpg :jpeg} ext) :image
    (#{:txt :log :md :doc :docx :pdf :epub} ext) :text
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

(defmethod handle-file-action [:preview :no-file] [_ state]
  {:fx/type          :text-area
   :v-box/vgrow      :always
   :text (@state :freetext)
   :style "-fx-focus-color: transparent; -fx-text-box-border: transparent;"
   :on-text-changed #(do
                       (swap! state assoc :freetext %)
                       ;(prn @state)
                       )
   }
  )


(defmethod handle-file-action [:prompt :no-file] [_ state]
  (let [p (str
    (:freetext @state)
    "\n\n"
    (:question @state))]
    (prn p)
    p)
  )

(defmethod handle-file-action [:load :no-file] [_ _])

(defmethod handle-file-action [:suggest :no-file] [_ _]
  ["biggest waterfalls in the world"
   "best books by Haruki Murakami"
   ;"日本の首相、ラスト１０人のを教えてください"
   "Say hello in 10 different languages"
   "Fibonacci in Clojure"
   "Compute"
   ]
  )


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

(defmethod handle-file-action [:prompt :table] [_ state]
  (str
    "This is a markdown formatted table of data you have for analysis:\n"
    (pyjama.utils/to-markdown @state)
    "\n"
    (:question state)))

(defmethod handle-file-action [:preview :table] [_ state]
  {:fx/type              :table-view
   :column-resize-policy TableView/CONSTRAINED_RESIZE_POLICY ; NICE !
   :columns              (for [header (:headers @state)]
                           {:fx/type            :table-column
                            :text               (name header)
                            :cell-value-factory header})
   :v-box/vgrow          :always
   :items                (:rows @state)}
  )

(defmethod handle-file-action [:load :image] [_ state]
  (swap! state assoc :images [(:selected-file @state)]))

(defmethod handle-file-action [:preview :image] [_ state]
  {:fx/type :stack-pane
   :children [{:fx/type :image-view
               :fit-width 400.0
               :preserve-ratio true
               :image (Image. (io/input-stream (first (:images @state))))}]})
;{:fx/type
  ; :image-view
  ; :fit-width 500.0
  ; :preserve-ratio true
  ; ;:v-box/vgrow :always
  ; ;:fit-width (:fx/bind (fn [node] (* 0.5 (double (.getWidth (.getParent (.getParent node)))))))
  ; ;:fit-width (fn [node]
  ; ;             (let [parent (.getParent node)]
  ; ;               (if parent
  ; ;                 (* 0.5 (double (.getWidth parent)))
  ; ;                 500
  ; ;                 )))
  ; :image
  ; (Image. (io/input-stream (first (:images @state))))})


(defmethod handle-file-action [:prompt :image] [_ state]
  (str
    "Look at the image attached. \n"
    (:question @state)))

(defmethod handle-file-action [:load :text] [_ state]
  (println "Loading text file" (:selected-file @state)))

(defmethod handle-file-action [:prompt :text] [_ state]
  (str
    "This is a text:\n"
    ;(slurp (:selected-file @state))
    (pyjama.io.readers/extract-text (:selected-file @state))
    "\n"
    (:question @state)))

(defmethod handle-file-action [:preview :text] [_ state]
  {:fx/type     :text-area
   :text        (pyjama.io.readers/extract-text (:selected-file @state))
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
   "What are the 3 main colors of the image"
   "create a short poem based on the picture"
   "what kind of atmosphere is represented in the picture."
   "what is the main season of the image."
   "describe the colors and lighting in the photo"
   "Are there any distinctive landmarks visible in the background"
   "describe the overall mood or atmosphere portrayed by this image"
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
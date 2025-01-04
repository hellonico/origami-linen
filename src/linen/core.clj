(ns linen.core
  (:gen-class)
  (:require [cljfx.api :as fx]
            [clojure.core.async :as async]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [dk.ative.docjure.spreadsheet :as ss]
            [linen.handlers]
            [pyjama.state])
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

(defn handle-drag-dropped [_state event]
  (let [db (.getDragboard event)
        files (.getFiles db)
        file (first files)]
    (when file
      (let [file-path (.getAbsolutePath file)]
        (append-to-history file-path)
        (swap! *state update :history (fn [x] (cons file-path (remove #(= % file-path) x))))
        (swap! *state assoc :selected-file file-path)
        (linen.handlers/handle-file-action :load *state)
        ;(load-file file-path)
        ))
    (.consume event)))

(defn left-panel [state]
  {:fx/type  :v-box
   ;:vbox/vgrow :always
   :children [{:fx/type          :combo-box
               :prompt-text      "Select a file..."
               :value            (:selected-file state)
               :on-value-changed (fn [new-file]
                                   (when new-file
                                     (swap! *state assoc :images [] :selected-file new-file)
                                     (linen.handlers/handle-file-action :load *state)))
               :items            (:history state)}

              (linen.handlers/handle-file-action :preview *state)
              ]}
  )

(defn right-panel [state]
  {:fx/type     :v-box
   :h-box/hgrow :always
   :spacing     10
   :padding     10
   :children    [
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
                 {:fx/type          :combo-box
                  :items            (linen.handlers/handle-file-action :suggest *state)
                  :value            ""                      ;(:model state)
                  :on-value-changed #(do
                                       (swap! *state assoc :question %)
                                       (swap! *state assoc :prompt (linen.handlers/handle-file-action :prompt *state))
                                       (pyjama.state/handle-submit *state)
                                       )}
                 {
                  :fx/type :label
                  :text    "Prompt:"}
                 {:fx/type         :text-area
                  :text            (:question state)
                  :on-text-changed #(do
                                      (swap! *state assoc :question %)
                                      (swap! *state assoc :prompt (linen.handlers/handle-file-action :prompt *state)))}
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
             :stylesheets     #{"styles.css"}
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

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type app-view)
    :opts {:state *state}))

(defn -main []
  (async/thread (pyjama.state/local-models *state))
  (fx/mount-renderer *state renderer))
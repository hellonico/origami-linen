(ns linen.core
  (:gen-class)
  (:require [cljfx.api :as fx]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [linen.handlers]
            [linen.history :as h]
            [pyjama.state])
  (:import (javafx.scene.image Image)
           (javafx.scene.input DragEvent TransferMode)))

(def *state
  (atom {
         :url      "http://localhost:11434"
         :model    "llama3.2"
         :prompt   ""
         :question ""
         :rows     []
         :headers  []
         :local-models []
         :history  (h/read-history) :selected-file nil}))

(defn handle-drag-dropped [_state event]
  (let [db (.getDragboard event)
        files (.getFiles db)
        file (first files)]
    (when file
      (let [file-path (.getAbsolutePath file)]
        (h/append-to-history file-path)
        (swap! *state update :history (fn [x] (cons file-path (remove #(= % file-path) x))))
        (swap! *state assoc :selected-file file-path)
        (linen.handlers/handle-file-action :load *state)))
    (.consume event)))

(defn left-panel [state]
  {:fx/type  :v-box
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
   :spacing     5
   :padding     5
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


                             ]
                  }
                 {:fx/type :h-box
                  :spacing  10
                  :children [
                             {:fx/type :label
                              :text    "Suggested Prompts:"}
                             {:fx/type          :combo-box
                              :items            (linen.handlers/handle-file-action :suggest *state)
                              :on-value-changed #(do
                                                   (swap! *state assoc :question %)
                                                   (swap! *state assoc :prompt (linen.handlers/handle-file-action :prompt *state))
                                                   (pyjama.state/handle-submit *state)
                                                   )}]
                  }

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
                    :text    (str (:model @*state) " is thinking ...")}
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
   :on-close-request (fn [_] (System/exit 0))
   :icons   [(Image. (io/input-stream (io/resource "delicious.png")))]
   :scene {:fx/type         :scene
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
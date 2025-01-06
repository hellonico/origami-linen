(ns linen.core
  (:gen-class)
  (:require [cljfx.api :as fx]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [linen.handlers]
            [linen.history :as h]
            [pyjama.state])
  (:import (atlantafx.base.theme NordLight)
           (javafx.scene.image Image)
           (javafx.scene.input DragEvent TransferMode)))

(def *state
  (atom {
         :url          "http://localhost:11434"
         :model        "llama3.2"
         :prompt       ""
         :question     ""
         :rows         []
         :headers      []
         :local-models []
         :history      (h/read-history) :selected-file nil}))

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
  {:fx/type         :v-box
   :on-drag-over    (fn [^DragEvent event]
                      (let [db (.getDragboard event)]
                        (when (.hasFiles db)
                          (doto event
                            (.acceptTransferModes (into-array TransferMode [TransferMode/COPY]))))))
   :on-drag-dropped #(handle-drag-dropped state %)
   :children        [{:fx/type          :combo-box
                      :prompt-text      "Select a file..."
                      :max-width        Double/MAX_VALUE
                      :value            (:selected-file state)
                      :on-value-changed (fn [new-file]
                                          (when new-file
                                            (swap! *state assoc :images [] :selected-file new-file)
                                            (linen.handlers/handle-file-action :load *state)))
                      :items            (:history state)}
                     (linen.handlers/handle-file-action :preview *state)
                     ]}
  )

(defn handle-drag-dropped-format [_ event]
  (let [db (.getDragboard event)
        files (.getFiles db)
        file (.getAbsolutePath (first files))
        content (read-string (slurp file))
        ]
    (swap! *state assoc
           :format-file file
           :format content
           )
    ))
; TODO: does not work for now
;
;(defn pretty-print-json [json-str]
;  "Pretty prints a JSON string using Cheshire."
;  (let [parsed-json (cheshire/parse-string json-str true)]  ; Parse JSON into a Clojure map
;    (cheshire/generate-string parsed-json {:pretty true})))  ; Pretty-print the map back into JSON


(defn right-panel [state]
  {:fx/type   :v-box
   ;:h-box/hgrow :always
   :min-width 400.0
   :spacing   5
   :padding   5
   :children  [
               {:fx/type   :h-box
                :spacing   5
                :alignment :center
                :children  [{:fx/type :label
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
               {:fx/type         :h-box
                :alignment       :center
                :on-drag-over    (fn [^DragEvent event]
                                   (let [db (.getDragboard event)]
                                     (when (.hasFiles db)
                                       (doto event
                                         (.acceptTransferModes (into-array TransferMode [TransferMode/COPY]))))))
                :on-drag-dropped #(handle-drag-dropped-format *state %)
                :children        [
                                  {:fx/type :label
                                   :text    "Format:"}
                                  (if (not (nil? (:format-file @*state)))
                                    {:fx/type  :h-box
                                     :children [

                                                {:fx/type :label
                                                 :text    (.getName (io/as-file (:format-file @*state)))}
                                                {:fx/type          :label
                                                 :on-mouse-clicked (fn [_]
                                                                     (swap! *state assoc
                                                                            :format-file nil
                                                                            :format nil
                                                                            ))
                                                 :text             " ❌ "}
                                                ]
                                     }
                                    {:fx/type :label
                                     :text    "Free format. (Drag an edn file to enforce output.)"}
                                    )

                                  ]
                }
               {:fx/type   :h-box
                :alignment :center
                :children  [
                            {:fx/type :label
                             :text    "Suggested Prompts:"}
                            {:fx/type          :combo-box
                             :items            (linen.handlers/handle-file-action :suggest *state)
                             :on-value-changed #(do
                                                  (swap! *state assoc :question %)
                                                  (swap! *state assoc :prompt (linen.handlers/handle-file-action :prompt *state))
                                                  (pyjama.state/handle-submit *state))}]
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
                 {:fx/type   :h-box
                  :alignment :center
                  :children  [
                              {
                               :fx/type :label
                               :text    (str (:model @*state) " is thinking ...")}
                              {:fx/type   :button
                               :text      "Stop"
                               :on-action (fn [_] (swap! *state assoc :processing false))
                               }
                              ]
                  }
                 )
               {
                :fx/type :label
                :text    "Response:"}
               {:fx/type     :text-area
                :wrap-text   true
                :v-box/vgrow :always
                :text
                ; TODO: format the code even when streaming
                ;(if (:format state)
                ;               (pretty-print-json (:response state))
                (:response state)
                :editable    false}]})

(defn app-view [state]
  {:fx/type          :stage
   :showing          true
   :title            "Pyjama Linen - Query Your Data"
   :on-close-request (fn [_] (System/exit 0))
   :icons            [(Image. (io/input-stream (io/resource "delicious.png")))]
   :scene            {:fx/type     :scene
                      :stylesheets #{(.getUserAgentStylesheet (NordLight.))}
                      :root
                      ;{
                      ;                  :fx/type  :h-box
                      ;                  :spacing  10
                      ;                  :children [
                      ;                             (left-panel state)
                      ;                             (right-panel state)
                      ;                             ]
                      ;                  }
                      {:fx/type           :split-pane
                       :divider-positions [0.5]             ;; Initial position (50% split)
                       :orientation       :horizontal       ;; Horizontal split
                       :items             [(left-panel state) (right-panel state)]
                       }
                      }})

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type app-view)
    :opts {:state *state}))

(defn -main []
  (async/thread (pyjama.state/local-models *state))
  (fx/mount-renderer *state renderer))
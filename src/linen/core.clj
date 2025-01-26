(ns linen.core
  (:gen-class)
  (:require [cljfx.api :as fx]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [linen.handlers]
            [pyjama.components]
            [pyjama.components]
            [pyjama.history :as h]
            [pyjama.state]
            [pyjama.utils])
  (:import (javafx.scene.image Image)
           (javafx.scene.input DragEvent TransferMode)))

(def *state
  (atom {
         :url          "http://localhost:11434"
         :model        "llama3.2"
         :prompt       ""
         :question     ""
         :simple       false
         :rows         []
         :headers      []
         :local-models []
         :history      (h/read-history "linen" "file-history.txt") :selected-file nil}))

(defn handle-drag-dropped [_state event]
  (let [db (.getDragboard event)
        files (.getFiles db)
        file (first files)]
    (when file
      (let [file-path (.getAbsolutePath file)]
        (h/append-to-history "linen" "file-history.txt" file)
        (swap! *state update :history (fn [x] (cons file-path (remove #(= % file-path) x))))
        (swap! *state assoc
               :images []
               :selected-file file-path
               :freetext nil)
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
   :children        [
                     {:fx/type   :h-box
                      :alignment :center-left
                      :children  [
                                  {:fx/type          :combo-box
                                   :prompt-text      "Select a file..."
                                   :max-width        Double/MAX_VALUE
                                   :value            (:selected-file state)
                                   :on-value-changed (fn [new-file]
                                                       (when new-file
                                                         (swap! *state assoc :freetext nil :images [] :selected-file new-file)
                                                         (linen.handlers/handle-file-action :load *state)))
                                   :items            (:history state)}

                                  (if (not (nil? (:selected-file @*state)))
                                    {:fx/type          :label :text " X "
                                     :on-mouse-clicked (fn [_]
                                                         (swap! *state assoc :selected-file nil))
                                     }
                                    {:fx/type :label}
                                    )
                                  ]}
                     (linen.handlers/handle-file-action :preview *state)
                     ]
   }
  )

(defn handle-drag-dropped-format [_ event]
  (let [db (.getDragboard event)
        files (.getFiles db)
        file (.getAbsolutePath (first files))
        content (read-string (slurp file))
        ]
    (swap! *state assoc
           :format-file file
           :format content)
    ))

(defn right-panel [state]
  {:fx/type   :v-box
   :min-width 400.0
   :spacing   5
   :padding   5
   :children  [
               {:fx/type         :v-box
                :spacing         5
                :alignment       :center-left
                :on-drag-over    (fn [^DragEvent event]
                                   (let [db (.getDragboard event)]
                                     (when (.hasFiles db)
                                       (doto event
                                         (.acceptTransferModes (into-array TransferMode [TransferMode/COPY]))))))
                :on-drag-dropped #(handle-drag-dropped-format *state %)
                :children
                (if (:simple state) [] [
                                        {
                                         :fx/type   :h-box
                                         :alignment :center-left
                                         :children  [
                                                     {:fx/type   :label
                                                      :min-width 150
                                                      :text      "Ollama URL"}
                                                     {:fx/type         :text-field
                                                      :text            (:url state)
                                                      ;:max-width       Double/MAX_VALUE
                                                      :h-box/hgrow     :always
                                                      :on-text-changed #(do
                                                                          (swap! *state assoc :url %)
                                                                          (async/thread
                                                                            (pyjama.state/check-connection *state)
                                                                            (pyjama.state/local-models *state)
                                                                            ))}
                                                     (pyjama.components/connected-image state)

                                                     ]}
                                        {
                                         :fx/type   :h-box
                                         :alignment :center-left
                                         :children  [
                                                     {:fx/type   :label
                                                      :min-width 150
                                                      :text      "Model"}
                                                     {:fx/type          :combo-box
                                                      :max-width        Double/MAX_VALUE
                                                      :h-box/hgrow      :always
                                                      :items            (:local-models state)
                                                      :value            (:model state)
                                                      :on-value-changed #(swap! *state assoc :model %)}
                                                     ]}
                                        {
                                         :fx/type   :h-box
                                         :alignment :center-left
                                         :children  [
                                                     {:fx/type   :label
                                                      :min-width 150
                                                      :text      "Format"}
                                                     (if (not (nil? (:format-file @*state)))
                                                       {:fx/type   :h-box
                                                        :alignment :center-left
                                                        :children  [
                                                                    {:fx/type   :label
                                                                     :min-width 100
                                                                     :text      (.getName (io/as-file (:format-file @*state)))}
                                                                    {:fx/type          :label :text " X "
                                                                     :on-mouse-clicked (fn [_]
                                                                                         (swap! *state assoc
                                                                                                :format-file nil
                                                                                                :format nil))}
                                                                    ]
                                                        }
                                                       {:fx/type :label
                                                        :text    "Free. (Drag edn file)"}
                                                       )]}
                                        {
                                         :fx/type   :h-box
                                         :alignment :center-left
                                         :children  [
                                                     {:fx/type   :label
                                                      :min-width 150
                                                      :text      "System "
                                                      }
                                                     {:fx/type         :text-area
                                                      :h-box/hgrow     :always
                                                      :max-height      100
                                                      :text            (if (empty? (:system state)) nil (:system state))
                                                      :prompt-text     "System"
                                                      :on-text-changed #(swap! *state assoc :system %)}
                                                     ]}
                                        {:fx/type   :h-box
                                         :alignment :center-left
                                         :children  [
                                                     {:fx/type   :label
                                                      :min-width 150
                                                      :text      "Suggested Prompts"}
                                                     {:fx/type          :combo-box
                                                      :h-box/hgrow      :always
                                                      :max-width        Double/MAX_VALUE
                                                      :items            (linen.handlers/handle-file-action :suggest *state)
                                                      :on-value-changed #(do
                                                                           (swap! *state assoc :question %)
                                                                           (swap! *state assoc :prompt (linen.handlers/handle-file-action :prompt *state))
                                                                           ;(clojure.pprint/pprint @*state)
                                                                           (pyjama.state/handle-submit *state))}
                                                     ]}
                                        {:fx/type   :h-box
                                         :alignment :center-left
                                         :children  [
                                                     {:fx/type   :label
                                                      :min-width 150
                                                      :text      "Clipboard"}
                                                     {:fx/type          :combo-box
                                                      :h-box/hgrow      :always
                                                      :max-width        Double/MAX_VALUE
                                                      :items            [
                                                                         "Summarize"
                                                                         "Explain"
                                                                         "What is the mood"
                                                                         "Ask 5 questions"
                                                                         "Find the 5 main keywords that describe the text best."
                                                                         ]
                                                      :on-value-changed #(do
                                                                           (swap! *state assoc
                                                                                  :selected-file nil
                                                                                  :freetext (pyjama.utils/get-clipboard-content)
                                                                                  :question %)
                                                                           (swap! *state assoc :prompt (linen.handlers/handle-file-action :prompt *state))
                                                                           (pyjama.state/handle-submit *state))}
                                                     ]
                                         }
                                        ])
                }


               {:fx/type     :v-box
                :v-box/vgrow :always
                :children    [
                              {:fx/type     pyjama.components/my-webview
                               :v-box/vgrow :always
                               :max-height  Double/MAX_VALUE
                               :props       {:html (:response state)}
                               :desc        {:fx/type :web-view}}
                              ]}

               {
                :fx/type  :h-box
                :children [
                           {:fx/type         :text-field
                            :max-width       Double/MAX_VALUE
                            :h-box/hgrow     :always
                            :text            (:question state)
                            :on-text-changed #(do
                                                (swap! *state assoc :question %)
                                                (swap! *state assoc :prompt (linen.handlers/handle-file-action :prompt *state)))}
                           (if (not (state :processing))
                             {:fx/type   :button
                              :text      "Ask"
                              :on-action (fn [_]
                                           (swap! *state assoc :prompt (linen.handlers/handle-file-action :prompt *state))
                                           (pyjama.state/handle-submit *state))
                              }
                             {:fx/type          :image-view
                              :image            (pyjama.fx/rsc-image "spinner.gif")
                              :on-mouse-clicked (fn [_] (swap! *state assoc :processing false))
                              :fit-width        24
                              :fit-height       24}
                             )
                           ]}

               ]})

(defn app-view [state]
  {:fx/type          :stage
   :showing          true
   :width            1200
   :min-width        1000
   :height           800
   :min-height       400
   :title            "Pyjama Linen"
   :on-close-request (fn [_] (System/exit 0))
   :icons            [(Image. (io/input-stream (io/resource "linen.png")))]
   :scene            {:fx/type      :scene
                      :accelerators {[:escape] {:event/type ::simple}}
                      :stylesheets  #{
                                      ;                ;"extra.css"
                                      (.toExternalForm (io/resource "terminal.css"))
                                      }
                      :root
                      {:fx/type           :split-pane
                       :divider-positions [0.5]
                       :orientation       :horizontal
                       :items             (if (:simple state)
                                            [(right-panel state)]
                                            [(left-panel state) (right-panel state)])
                       }
                      }})

(defmulti event-handler :event/type)

(defmethod event-handler :default [e]
  (prn (:event/type e) (:fx/event e) (dissoc e :fx/context :fx/event :event/type)))

(defmethod event-handler ::simple [_]
  (swap! *state assoc :simple (not (:simple @*state)))
  )

(def renderer
  (fx/create-renderer
    :middleware (fx/wrap-map-desc assoc :fx/type app-view)
    :opts {:state                    *state
           :fx.opt/map-event-handler event-handler
           }))

(defn -main []
  (async/thread
    (pyjama.state/local-models *state)
    (pyjama.state/check-version *state))
  (fx/mount-renderer *state renderer))
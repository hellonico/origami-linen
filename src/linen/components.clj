(ns linen.components
  (:require
    [cljfx.api :as fx]
    [cljfx.lifecycle :as fx.lifecycle]
    [cljfx.mutator :as fx.mutator]
    [cljfx.prop :as fx.prop]
    [clojure.java.io :as io]
    [linen.utils])
  (:import (javafx.scene.web WebView)))

(def my-webview
  (fx/make-ext-with-props
    {:html (fx.prop/make
             (fx.mutator/setter #(doto (.getEngine ^WebView %1)
                                   (.setUserStyleSheetLocation (.toExternalForm(io/resource "web.css")))
                                   ;(.setJavaScriptEnabled true)
                                   (.loadContent
                                     ;(str
;"<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>Syntax Highlighting</title>\n    <link href=\"https://cdn.jsdelivr.net/npm/prismjs/themes/prism.css\" rel=\"stylesheet\">\n    <script src=\"https://cdn.jsdelivr.net/npm/prismjs/prism.js\"></script>\n</head>\n<body>"

                                       (linen.utils/markdown->html %2)
;"</body>\n</html>"
;                                       )
                                   )
                                   ;(.executeScript "window.scrollTo(0, document.body.scrollHeight);")
                                   ))
             fx.lifecycle/scalar)}))

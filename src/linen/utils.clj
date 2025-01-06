(ns linen.utils
  (:import (com.vladsch.flexmark.html HtmlRenderer$Builder)
           (com.vladsch.flexmark.parser Parser$Builder)
           (javafx.scene.input Clipboard)))

(defn get-clipboard-content []
  (let [clipboard (Clipboard/getSystemClipboard)]
    (if (.hasString clipboard)
      (.getString clipboard)
      "No text in clipboard")))

;; Flexmark markdown to HTML conversion function
(defn markdown->html [markdown-text]
  (let [parser (.build (Parser$Builder.))
        renderer (.build (HtmlRenderer$Builder.))]
    (.render renderer (.parse parser ^String markdown-text))))
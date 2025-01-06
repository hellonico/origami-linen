(ns linen.utils
  (:import (javafx.scene.input Clipboard)))

(defn get-clipboard-content []
  (let [clipboard (Clipboard/getSystemClipboard)]
    (if (.hasString clipboard)
      (.getString clipboard)
      "No text in clipboard")))
(ns build
  (:require [clojure.tools.build.api :as b]))

;(def lib 'waves-cli)
(def version (format "1.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
;(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
      (b/delete {:path "target"}))

(defn uberize [compile main]
      (let [uber-file
            (format "target/%s-%s.jar" (name main) version)
            ]
           (clean nil)
           (b/copy-dir {:src-dirs ["src" "resources"]
                        :target-dir class-dir})
           (b/compile-clj {:basis @basis
                           :ns-compile compile
                           :class-dir class-dir})
           (b/uber {:class-dir class-dir
                    :uber-file uber-file
                    :basis @basis
                    :main main})
           ))

(defn uber-linen [_]
      (uberize '[linen.core] 'linen.core))

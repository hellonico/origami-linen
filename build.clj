(ns build
  (:require
    [clojure.java.io :as io]
    [clojure.java.shell :as shell]
    [clojure.tools.build.api :as b]))

(def app-name "linen")
(def version (format "1.0.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def uber-file (format "target/%s-%s.jar" app-name version))

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
      (b/delete {:path "target"}))

(defn uberize [compile main]
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
           )


(defn jpackage [_]
      (shell/sh "jpackage"
                "--dest" "output"
                "--name" "linen"
                "--input" (.getParent (io/as-file uber-file))
                "--java-options" "-Xmx2048m"
                "--main-jar" (.getName (io/as-file uber-file))
                "--icon" "flax-seeds-96.icns"
                "--app-version" version))

(defn uber-linen [_]
      (uberize '[linen.core] 'linen.core))

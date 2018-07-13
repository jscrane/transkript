(defproject transkript "0.1.12-SNAPSHOT"
  :description "A Clojure client API for Transkribus"
  :url "https://github.com/jscrane/transkript"
  :license {:name "Eclipse Public License 2.0"
            :url  "http://www.eclipse.org/legal/epl-v20.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.data "0.1.1"]
                 [eu.transkribus/TranskribusClient "0.0.2"]
                 [org.clojure/tools.cli "0.3.7"]]
  :aot [eval-models, run-ocr]
  :manifest {
             "Specification-Title"    "Java Advanced Imaging Image I/O Tools"
             "Specification-Version"  "1.1"
             "Specification-Vendor"   "Sun Microsystems, Inc."
             "Implementation-Title"   "com.sun.media.imageio"
             "Implementation-Version" "1.1"
             "Implementation-Vendor"  "Sun Microsystems, Inc."
             }
  )

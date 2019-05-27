(defproject transkript "0.1.13-SNAPSHOT"
  :description "A Clojure client API for Transkribus"
  :url "https://github.com/jscrane/transkript"
  :license {:name "Eclipse Public License 2.0"
            :url  "http://www.eclipse.org/legal/epl-v20.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/java.data "0.1.1"]
                 [eu.transkribus/TranskribusCore "0.1.9"]
                 [eu.transkribus/TranskribusClient "0.1.0"]
                 [org.clojure/tools.cli "0.4.2"]]
  :repositories [["dbis-halvar.uibk.ac.at" "https://dbis-halvar.uibk.ac.at/artifactory/libs-release/"]]
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

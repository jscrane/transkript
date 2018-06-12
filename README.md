# transkript

A Clojure client library for [Transkribus](https://github.com/Transkribus).

## Usage

```clojure
(require '[transkript.core :as tk])
(tk/connect (tk/load-config "resources/config.edn"))

(tk/select [:colId :colName] (tk/collections))
=>
({:colId 8487, :colName "Renahan Transcripts"}
 {:colId 11986, :colName "Carte"}
 {:colId 14913, :colName "Hore Manuscripts"}
 {:colId 15187, :colName "jscrane@gmail.com Collection"})

(tk/use-collection 8487)
(tk/select [:docId :title] (tk/documents))
=>
({:docId 27808, :title "Test Document"}
 {:docId 38118, :title "tcd (2)"}
 {:docId 38370, :title "tcd (3)"}
 {:docId 39042, :title "tcd (4)"}
 {:docId 40922, :title "tcd(6)"}
 {:docId 40962, :title "tcd (7)"}
 {:docId 44221, :title "tcd (8)"}
 {:docId 44330, :title "v2 start"}
 {:docId 53260, :title "renehan 3 f365 to end"})

(tk/select [:pageId :pageNr] (tk/pages 27808))
=> 
({:pageId 798149, :pageNr 1}
 {:pageId 798150, :pageNr 2}
 {:pageId 798151, :pageNr 3})

(tk/select [:name :htrId] (tk/models))
=>
({:name "English Writing M1", :htrId 133}
 {:name "Konzilsprotokolle v1", :htrId 5}
 {:name "Renehan_M1", :htrId 1776}
 {:name "Renehan_M2", :htrId 2117}
 {:name "German Kurrent (Reichsgericht)", :htrId 78})

(tk/run-model 133 27808 "1-10")

(map :jobId (tk/jobs))
(:state (tk/job 318824))
(tk/cancel-job 318824)
(tk/train-model 8487 "foo" "English" "bar" [[38118 1308829] [38118 1308834]] [[38118 1308841]])

(tk/close)
```

## License

Copyright © 2018 Stephen Crane.

# transkript

A Clojure client library for [Transkribus](https://github.com/Transkribus).

## Usage

```clojure
(require '[transkript.core :as tk])
(tk/load-config "resources/config.edn")
=>
{:username "jscrane@gmail.com", :password "XXXXXXXXX", :server "https://transkribus.eu/TrpServer", :language "English"}
(tk/login {:password "my password"})

(tk/select [:colId :colName] (tk/collections))
=>
({:colId 8487, :colName "Renahan Transcripts"}
 {:colId 11986, :colName "Carte"}
 {:colId 14913, :colName "Hore Manuscripts"}
 {:colId 15187, :colName "jscrane@gmail.com Collection"})

(tk/use-collection 8487)
=> 8487
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

(tk/select :pageId (tk/pages-numbered [1 2 3] (tk/pages 27808)))
=> ({:pageId 798149} {:pageId 798150} {:pageId 798151})

(tk/select [:name :htrId] (tk/models))
=>
({:name "English Writing M1", :htrId 133}
 {:name "Konzilsprotokolle v1", :htrId 5}
 {:name "Renehan_M1", :htrId 1776}
 {:name "Renehan_M2", :htrId 2117}
 {:name "German Kurrent (Reichsgericht)", :htrId 78})

(tk/use-model 133)
=> 133
(tk/run-model (tk/pages-numbered [1 2 3] (tk/pages 27808)))
=> 345885
(tk/status 345885)
=> :RUNNING
(tk/cancel 345885)
=> nil
(tk/status 345885)
=> :CANCELED
(tk/run-model 27808 [1 3 5])
=> 346093

(tk/analyse-layout :CITlabAdvanced (tk/pages 50811) {:block-seg true, :line-seg true})
=> (351020)

(tk/set-language "German")
=>
{:username "jscrane@gmail.com", :password "XXXXXXXXX", :server "https://transkribus.eu/TrpServer", :language "German"}

(tk/select :status (tk/gt-transcripts 38118 (range 1 4)))
=> ({:status "GT"} {:status "GT"} {:status "GT"})
(tk/train-model "new model" "some description" (tk/gt-transcripts 38118 (range 1 4)) (tk/gt-transcripts 38118 [5 6]))
=> 345866
(tk/status 345866)
=> :RUNNING

(tk/logout)
```

## License

Copyright © 2018 Stephen Crane.

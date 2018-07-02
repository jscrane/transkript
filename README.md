# transkript

A Clojure client library for [Transkribus](https://github.com/Transkribus).

## Usage

```clojure
(require '[transkript.core :as tk])
(tk/load-config "config.edn")
=>
{:username "jscrane@gmail.com", :password "XXXXXXXXX", :server "https://transkribus.eu/TrpServer", :language "English"}
(tk/login {:password "my password"})
```

Optionally override settings in the config at login time (or just provide them all).

```clojure
(tk/select [:colId :colName] (tk/collections))
=>
({:colId 8487, :colName "Renahan Transcripts"}
 {:colId 11986, :colName "Carte"}
 {:colId 14913, :colName "Hore Manuscripts"}
 {:colId 15187, :colName "jscrane@gmail.com Collection"})
(tk/use-collection 8487)
=> 8487
(tk/use-collection (first (tk/collections)))
=>
{:role "Editor",
 :description "Copies made of manuscripts in the Record Tower, Dublin, by the Rev. Laurence Renehan, first President of Maynooth College in the mid-nineteenth cenury. The originals were destroyed in the the Fourcourts Fire in Dublin in 1922.",
 :thumbUrl "https://dbis-thure.uibk.ac.at/f/Get?id=FKAYHOLJPWQRUYKMROKQLPEH&fileType=thumb",
 :nrOfDocuments 9,
 :pageId 798149,
 :elearning false,
 :colId 8487,
 :summary "Renahan Transcripts (8487, Editor)",
 :colName "Renahan Transcripts",
 :url "https://dbis-thure.uibk.ac.at/f/Get?id=FKAYHOLJPWQRUYKMROKQLPEH&fileType=view",
 :crowdsourcing false}
```

Entities returned from the server are converted into nested maps. Select returns 
a subset of a map's keys.

Setting a default collection makes future API calls more concise. In either case,
a collection may be specified by its _:colId_ or using the corresponding map. (This
convention also applies to other APIs, see below.)

```clojure
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
```

If no collection is given, ocuments returned are from the default one.
A document's pages may be retrieved (and the document specified either by
_:docId_ or map).

Note that pages have both _:pageId_ and _:pageNr_. (The system prefers the
former and an API is provided to convert to that.)

```clojure
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
(tk/wait (tk/run-model (tk/pages-numbered [1 3 5] (tk/pages 27808))))
=>
{:description "Done",
 :started "Thu Jun 28 08:31:55 IST 2018",
; lots more stuff ...
  :userName "jscrane@gmail.com",
  :failed false}
```

A Text-recognition model is associated with a collection. A default model may also be set.

Running a model is asynchronous and returns a job identifier which may be
used to query the state of the job, cancel it or wait for it to finish.

```clojure
(tk/analyse-layout :CITlabAdvanced (tk/pages 50811) {:block-seg true, :line-seg true})
=> (351020)
(tk/select [:state :jobIdAsInt] (tk/wait-all [351020 350984]))
=> ({:state "FINISHED", :jobIdAsInt 351020} {:state "FAILED", :jobIdAsInt 350984})
```

Layout analysis creates one job per page. An API to wait for all of them to
finish is provided.

```clojure
(tk/set-language "German")
=>
{:username "jscrane@gmail.com", :password "XXXXXXXXX", :server "https://transkribus.eu/TrpServer", :language "German"}
(tk/select :status (tk/gt-transcripts 38118 (range 1 4)))
=> ({:status "GT"} {:status "GT"} {:status "GT"})
(tk/train-model "new model" "some description" (tk/gt-transcripts 38118 (range 1 4)) (tk/gt-transcripts 38118 [5 6]))
=> 345866
(tk/status 345866)
=> :RUNNING
```

New models may be trained using transcripts labelled "ground truth" and an API is provided to find such transcripts in a document's pages. A default language is set in the config, and may be changed.

```clojure
(tk/accuracy (first (tk/transcripts 67884 [1])))
=> {:gt "IUGKFCZKOPZQQSYQTKEMYKZD", :hyp "ZUGLJCDNORQUAVCSSASJKQHE", :WER 135.65573, :CER 95.60117}
```

When a model has been trained, its accuracy may be evaluated by comparing its output ("hypothesis")
with a known transcript ("ground truth"). Word- and character-error
rates are returned, as are the keys of the transcripts compared.

Two forms of this API are provided. In the first one, the transcripts are
explicitly specified (or their keys). In the second, a set of transcripts is
provided, and the most-recent labelled "IN PROGRESS" and "GT" are chosen.
 
```clojure
(tk/logout)
```

## License

See the file [License](https://github.com/jscrane/transkript/blob/master/LICENSE).

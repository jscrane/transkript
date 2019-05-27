# transkript

A Clojure client library for [Transkribus](https://github.com/Transkribus).

This is not an exhaustive implementation of the [Transkribus Client API](https://github.com/Transkribus/TranskribusClient), 
just enough to support some scripting applications.
 
## Usage

```clojure
(require '[transkript.core :as tk])
=> nil
(tk/load-config "config.edn")
=>
{:username "jscrane@gmail.com",
 :password "XXXXXXXXXXXX",
 :server "https://transkribus.eu/TrpServer",
 :language "English",
 :typeface "Combined",
 :poll 3000}
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

If no collection is given, documents returned are from the default one.
A document's pages may be retrieved (and the document specified either by
_:docId_ or map).

Note that pages have both _:pageId_ and _:pageNr_. (The system prefers the
former and an API is provided to convert to that.)

```clojure
(tk/analyse-layout :CITlabAdvanced (tk/pages 27808) {:block-seg true, :line-seg true})
=> (351020)
(tk/select [:state :jobIdAsInt] (tk/wait-all [351020 350984]))
=> ({:state "FINISHED", :jobIdAsInt 351020} {:state "FAILED", :jobIdAsInt 350984})
```

The first step in processing a new document is to run layout analysis on it, in order
to discover text elements on each page.

Layout analysis creates one job per page. An API to wait for all of them to
finish is provided.

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
If the document is handwritten, the next step is to perform transcription
using a handwritten-text recognition model.

An HTR model is associated with a collection. A default model may be set.

Running a model is asynchronous and returns a job identifier which may be
used to query the state of the job, cancel it or wait for it to finish.

```clojure
(tk/run-ocr (tk/pages 68881))
=> 353584
(tk/job 353584)
=>
{:description "OCR: Busy",
 :started "Mon Jul 02 14:01:17 IST 2018",
 :createTimeFormatted "02.07.2018 14:01:17",
; lots more stuff ...
 :jobDataProps {:properties {"typeFace" "COMBINED",
                             "state" "download",
                             "path" "/mnt/dea_scratch/TRP/OCR/ocr-68881-8584653302377025848",
                             "language" "English"}},
 :parent_batchid 0,
  :userName "jscrane@gmail.com",
  :failed false}
```

If the document was typeset, transcription is performed using optical character
recognition, OCR.

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

New HTR models may be trained using transcripts labelled "ground truth" and an API is provided to find such transcripts in a document's pages. A default language is set in the config, and may be changed.

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
(tk/import-document 18751 "foo" "/home/steve/foo")
; lots of chatty debug info
=> 355066
(tk/status 355066)
=> :RUNNING
(tk/wait 355066)
=>
{
 ; abridged list of job properties
 :started "Wed Jul 04 12:49:43 IST 2018",
 :docId 69485,
 :type "Create Document",
 :colId 18751,
 :jobImpl "UploadImportJob",
}
```

A new document may be imported into a collection (or the current 
one) using a set of images in a folder on the local filesystem.

The _:docId_ key in the job description returned after the job
has completed contains the new document's identifier.

```clojure
(tk/export-document 18751 68881 "/tmp/foo" {:pages #{1 3 5} :overwrite true})
; some chatty debug info
=> "/tmp/foo"
```

A document may also be exported to a directory on the local
filesystem. A map containing optional parameters which control 
the output may be passed. If omitted, images and pages are
exported.
 
```clojure
(tk/export-text 18751 68881 "/tmp/foo.txt")
; some chatty debug info
=> nil
```

Transcripted text associated with a document may be exported to
a flat file in the local filesystem.

```clojure
(tk/logout)
```

## License

[Eclipse Public License - v 2.0](https://github.com/jscrane/transkript/blob/master/LICENSE)

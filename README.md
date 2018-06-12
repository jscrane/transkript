# transkript

A Clojure client library for [Transkribus](https://github.com/Transkribus).

## Usage

```clojure
(require '[transkript.core :as tk])
(tk/connect (tk/load-config "resources/config.edn"))

(tk/select [:colId :colName] (tk/collections))
(tk/use-collection 8487)

(tk/select [:docId :title] (tk/documents))
(tk/select [:pageId :pageNr] (tk/pages 27808))

(tk/select [:description :name :htrId] (tk/models))
(tk/run-model 133 53260 "1-10")

(map :jobId (tk/jobs))
(:state (tk/job 318824))
(tk/cancel-job 318824)
(tk/train-model 8487 "foo" "English" "bar" [[38118 1308829] [38118 1308834]] [[38118 1308841]])

(tk/close)
```

## License

Copyright © 2018 Stephen Crane.

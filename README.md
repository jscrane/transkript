# transkript

A Clojure client library for [Transkribus](https://github.com/Transkribus).

## Usage

```clojure
(require '[transkript.core :as tk])
(tk/connect (tk/load-config "resources/config.edn"))

(map :colName (tk/collections))
(map :title (tk/documents 8487))
(map :pageNr (tk/pages 8487 53260))
(map :name (tk/models 8487))
(map :jobId (tk/jobs))
(:state (tk/job 318824)  )

(tk/close)
```

## License

Copyright © 2018 Stephen Crane.

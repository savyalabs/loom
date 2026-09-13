(ns ^{:doc "Output and view graphs in various formats"
      :author "Justin Kramer"}
  loom.io
  (:require [loom.graph :refer [directed? weighted? nodes weight src dest
                                graph digraph weighted-graph weighted-digraph
                                add-nodes add-edges successors]]
            [loom.alg :refer [distinct-edges]]
            [loom.attr :refer [attr? attr attrs add-attr]]
            [clojure.string :refer [escape]]
            [clojure.java.io :refer [file]]
            [clojure.java.shell :refer [sh]]
            [clojure.data.json :as json]))

(defn- dot-esc
  [s]
  (escape s {\" "\\\"" \newline "\\n"}))

(defn- dot-attrs
  [attrs]
  (when (seq attrs)
    (let [sb (StringBuilder. "[")]
      (doseq [[k v] attrs]
        (when (pos? (.length (str v)))
          (when (< 1 (.length sb))
            (.append sb \,))
          (doto sb
            (.append \")
            (.append (dot-esc (if (keyword? k) (name k) (str k))))
            (.append "\"=\"")
            (.append (dot-esc (if (keyword? v) (name v) (str v))))
            (.append \"))))
      (.append sb "]")
      (str sb))))

(defn dot-str
  "Renders graph g as a DOT-format string. It calls (node-label node) and
  (edge-label n1 n2) to get labels for nodes and edges. Weights become edge
  labels unless a label is specified. Labels include attributes when the graph
  satisfies AttrGraph."
  [g & {:keys [graph-name node-label edge-label]
        :or {graph-name "graph"} :as opts }]
  (let [d? (directed? g)
        w? (weighted? g)
        a? (attr? g)
        node-label (or node-label
                       (if a?
                         #(attr g % :label)
                         (constantly nil)))
        edge-label (or edge-label
                       (cond
                         a? #(if-let [a (attr g %1 %2 :label)]
                               a
                               (if w? (weight g %1 %2) nil))
                         w? #(weight g %1 %2)
                         :else (constantly nil)))
        sb (doto (StringBuilder.
                  (if d? "digraph \"" "graph \""))
             (.append (dot-esc graph-name))
             (.append "\" {\n"))]
    (doseq [k [:graph :node :edge]]
      (when (k opts)
        (doto sb
          (.append (str "  " (name k) " "))
          (.append (dot-attrs (k opts))))))
    (doseq [edge (distinct-edges g)]
      (let [n1 (src edge)
            n2 (dest edge)
            el (edge-label n1 n2)
            eattrs (assoc (if a?
                            (attrs g n1 n2) {})
                     :label el)]
        (doto sb
          (.append (str (hash n1)))
          (.append (if d? " -> " " -- "))
          (.append (str (hash n2))))
        (when (or (:label eattrs) (< 1 (count eattrs)))
          (.append sb \space)
          (.append sb (dot-attrs eattrs)))
        (.append sb "\n")))
    (doseq [n (nodes g)]
      (let [nl (dot-esc (str (or (node-label n) n)))]
        (doto sb
          (.append (str (hash n) " [label=\"" nl "\"]"))))
      (when-let [nattrs (when a?
                          (dot-attrs (attrs g n)))]
        (.append sb \space)
        (.append sb nattrs))
      (.append sb "\n"))
    (str (doto sb (.append "}")))))

(defn dot
  "Writes graph g to f (a string or File) in DOT format. It passes args to dot-str."
  [g f & args]
  (spit (str (file f)) (apply dot-str g args)))

;; Graph interchange formats

(defn- encode-value [v] (pr-str v))
(defn- decode-value [s]
  (try
    (read-string {:read-eval false} s)
    (catch RuntimeException _ s)))
(defn- xml-esc [s]
  (-> (str s) (clojure.string/replace "&" "&amp;")
      (clojure.string/replace "<" "&lt;")
      (clojure.string/replace ">" "&gt;")
      (clojure.string/replace "\"" "&quot;")
      (clojure.string/replace "'" "&apos;")))
(defn- attrs-entries [g n1 n2]
  (or (when (attr? g) (attrs g n1 n2)) {}))
(defn- graph-for [directed weighted]
  (cond
    weighted (if directed weighted-digraph weighted-graph)
    directed digraph
    :else graph))
(defn- add-serialized-attrs [g node-attrs edge-attrs]
  (let [g (reduce (fn [g [n as]]
                    (reduce (fn [g [k v]] (add-attr g n k v)) g as))
                  g node-attrs)]
    (reduce (fn [g [[n1 n2] as]]
              (reduce (fn [g [k v]] (add-attr g n1 n2 k v)) g as))
            g edge-attrs)))

(defn write-graphml
  "Writes graph g to GraphML file f. Values and attributes are encoded as EDN."
  [g f]
  (let [ns (vec (nodes g))
        ids (zipmap ns (map #(str "n" %) (range)))
        attr-keys (vec (distinct (concat
                                  (mapcat #(keys (or (attrs g %) {})) ns)
                                  (mapcat #(keys (attrs-entries g (first %) (second %)))
                                          (distinct-edges g)))))
        all-attrs (zipmap attr-keys (map #(str "k" %) (range)))
        keys-xml (apply str (for [[k id] all-attrs]
                              (str "<key id=\"" id "\" for=\"all\" attr.name=\""
                                   (xml-esc (encode-value k)) "\" attr.type=\"string\"/>")))
        node-xml (apply str (for [n ns]
                              (str "<node id=\"" (ids n) "\"><data key=\"loom.value\">"
                                   (xml-esc (encode-value n)) "</data>"
                                   (apply str (for [[k v] (attrs g n)]
                                                (str "<data key=\"" (get all-attrs k) "\">"
                                                     (xml-esc (encode-value v)) "</data>"))) "</node>")))
        edge-xml (apply str (for [[n1 n2] (distinct-edges g)]
                              (let [as (attrs-entries g n1 n2)]
                                (str "<edge source=\"" (ids n1) "\" target=\"" (ids n2) "\""
                                     (when (weighted? g) (str " weight=\"" (xml-esc (encode-value (weight g n1 n2))) "\"")) ">"
                                     (apply str (for [[k v] as]
                                                  (str "<data key=\"" (get all-attrs k) "\">"
                                                       (xml-esc (encode-value v)) "</data>"))) "</edge>"))))]
    (spit (str (file f))
          (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
               "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">"
               "<key id=\"loom.value\" for=\"node\" attr.name=\"value\" attr.type=\"string\"/>"
               keys-xml "<graph id=\"G\" edgedefault=\"" (if (directed? g) "directed" "undirected") "\">"
               "<nodes>" node-xml "</nodes><edges>" edge-xml "</edges></graph></graphml>"))))

(def ^:private xml-parser-logger
  (java.util.logging.Logger/getLogger "loom.io"))

(defn- set-xml-feature [factory feature enabled]
  (try
    (.setFeature factory feature enabled)
    (catch javax.xml.parsers.ParserConfigurationException e
      (.warning xml-parser-logger
                (str "XML parser does not support security feature " feature
                     ": " (.getMessage e)))))
  factory)

(defn- harden-xml-factory [factory]
  (-> factory
      (set-xml-feature "http://apache.org/xml/features/disallow-doctype-decl" true)
      (set-xml-feature "http://xml.org/sax/features/external-general-entities" false)
      (set-xml-feature "http://xml.org/sax/features/external-parameter-entities" false)
      (doto (.setXIncludeAware false)
            (.setExpandEntityReferences false))))

(defn- xml-document [source]
  (let [factory (harden-xml-factory
                 (javax.xml.parsers.DocumentBuilderFactory/newInstance))
        builder (.newDocumentBuilder factory)]
    (.parse builder (cond
                      (instance? java.io.File source) source
                      (and (string? source) (.exists (file source))) (file source)
                      :else (java.io.ByteArrayInputStream. (.getBytes (str source) "UTF-8"))))))
(defn- xml-elements [parent name]
  (let [nl (.getElementsByTagName parent name)]
    (map #(.item nl %) (range (.getLength nl)))))
(defn- xml-attr [e k] (.getAttribute e k))
(defn- xml-text [e] (.getTextContent e))
(defn- parse-xml-graph [source _format]
  (let [doc (xml-document source)
        root (.getDocumentElement doc)
        ge (first (xml-elements root "graph"))
        directed (= "directed" (xml-attr ge "edgedefault"))
        node-es (xml-elements root "node")
        key-names (into {"loom.value" :loom.value}
                        (for [e (xml-elements root "key")]
                          [(xml-attr e "id") (decode-value (xml-attr e "attr.name"))]))
        node-id (into {} (map (fn [e] [(xml-attr e "id")
                                       (if-let [v (first (filter #(= "loom.value" (xml-attr % "key"))
                                                                  (xml-elements e "data")))]
                                         (decode-value (xml-text v))
                                         (or (xml-attr e "label") (xml-attr e "id")))]) node-es))
        edge-es (xml-elements root "edge")
        weighted (some #(seq (xml-attr % "weight")) edge-es)
        ctor (graph-for directed weighted)
        g (apply add-nodes (ctor) (vals node-id))
        [g _node-as] (reduce (fn [[g nas] e]
                              (let [n (node-id (xml-attr e "id"))
                                    as (into {} (for [d (xml-elements e "data")
                                                      :when (not= "loom.value" (xml-attr d "key"))]
                                                  [(get key-names (xml-attr d "key") (decode-value (xml-attr d "key"))) (decode-value (xml-text d))]))]
                                [(reduce (fn [g [k v]] (add-attr g n k v)) g as)
                                 (assoc nas n as)]))
                            [g {}] node-es)
        [g edge-as] (reduce (fn [[g eas] e]
                              (let [n1 (node-id (xml-attr e "source")) n2 (node-id (xml-attr e "target"))
                                    w (when weighted (decode-value (xml-attr e "weight")))
                                    as (into {} (for [d (xml-elements e "data")]
                                                  [(get key-names (xml-attr d "key") (decode-value (xml-attr d "key"))) (decode-value (xml-text d))]))]
                                [(add-edges g (if weighted [n1 n2 w] [n1 n2]))
                                 (assoc eas [n1 n2] as)]))
                            [g {}] edge-es)]
    (add-serialized-attrs g {} edge-as)))

(defn read-graphml [source] (parse-xml-graph source :graphml))

(defn write-gexf
  "Writes graph g to GEXF file f. Values and attributes are encoded as EDN."
  [g f]
  (let [ns (vec (nodes g)) ids (zipmap ns (map #(str "n" %) (range)))
        attr-keys (distinct (mapcat #(keys (or (attrs g %) {})) ns))
        edge-attr-keys (distinct (mapcat #(keys (attrs-entries g (first %) (second %)))
                                        (distinct-edges g)))]
    (spit (str (file f))
          (str "<?xml version=\"1.0\"?><gexf xmlns=\"http://gexf.net/1.2draft\" version=\"1.2\"><graph defaultedgetype=\""
               (if (directed? g) "directed" "undirected") "\" mode=\"static\"><attributes class=\"node\">"
               (apply str (map-indexed #(str "<attribute id=\"a" %1 "\" title=\"" (xml-esc (encode-value %2)) "\" type=\"string\"/>") attr-keys))
               "</attributes><attributes class=\"edge\">"
               (apply str (map-indexed #(str "<attribute id=\"e" %1 "\" title=\"" (xml-esc (encode-value %2)) "\" type=\"string\"/>") edge-attr-keys))
               "</attributes><nodes>"
               (apply str (for [n ns] (str "<node id=\"" (ids n) "\" label=\"" (xml-esc (encode-value n)) "\"><attvalues>"
                                             (apply str (for [[i k] (map-indexed vector attr-keys) :when (contains? (attrs g n) k)]
                                                          (str "<attvalue for=\"a" i "\" value=\"" (xml-esc (encode-value (get (attrs g n) k))) "\"/>")))
                                             "</attvalues></node>"))) "</nodes><edges>"
               (apply str (for [[i [n1 n2]] (map-indexed vector (distinct-edges g))]
                            (str "<edge id=\"e" i "\" source=\"" (ids n1) "\" target=\"" (ids n2) "\""
                                 (when (weighted? g) (str " weight=\"" (xml-esc (encode-value (weight g n1 n2))) "\"")) "><attvalues>"
                                 (apply str (for [[i k] (map-indexed vector edge-attr-keys)
                                                  :when (contains? (attrs-entries g n1 n2) k)]
                                              (str "<attvalue for=\"e" i "\" value=\""
                                                   (xml-esc (encode-value (get (attrs-entries g n1 n2) k))) "\"/>")))
                                 "</attvalues></edge>")))
               "</edges></graph></gexf>"))))

(defn read-gexf [source]
  (let [root (.getDocumentElement (xml-document source))
        directed (= "directed" (xml-attr (first (xml-elements root "graph")) "defaultedgetype"))
        attr-names (into {} (for [e (xml-elements root "attribute")]
                              [(xml-attr e "id") (decode-value (xml-attr e "title"))]))
        node-es (xml-elements root "node")
        node-id (into {} (map (fn [e]
                                [(xml-attr e "id") (decode-value (xml-attr e "label"))]) node-es))
        edge-es (xml-elements root "edge")
        weighted (some #(seq (xml-attr % "weight")) edge-es)
        g (reduce add-nodes ((graph-for directed weighted)) (vals node-id))
        g (reduce (fn [g e]
                    (let [n1 (node-id (xml-attr e "source")) n2 (node-id (xml-attr e "target"))
                          w (when weighted (decode-value (xml-attr e "weight")))]
                      (apply add-edges g [(if weighted [n1 n2 w] [n1 n2])])))
                  g edge-es)
        g (reduce (fn [g e]
                    (let [n1 (node-id (xml-attr e "source")) n2 (node-id (xml-attr e "target"))]
                      (reduce (fn [g a]
                                (add-attr g n1 n2 (get attr-names (xml-attr a "for"))
                                          (decode-value (xml-attr a "value"))))
                              g (xml-elements e "attvalue"))))
                  g edge-es)]
    (reduce (fn [g e]
              (let [n (node-id (xml-attr e "id"))]
                (reduce (fn [g a]
                          (add-attr g n (get attr-names (xml-attr a "for"))
                                    (decode-value (xml-attr a "value"))))
                        g (xml-elements e "attvalue"))))
            g node-es)))

(defn write-edge-list
  "Writes one EDN-encoded edge per line, with a Loom metadata header."
  [g f]
  (spit (str (file f))
        (str "# loom directed=" (directed? g) " weighted=" (weighted? g) "\n"
             (apply str (for [n (nodes g)] (str "# node\t" (encode-value n) "\n")))
             (apply str (for [[n1 n2] (distinct-edges g)]
                          (str (encode-value n1) "\t" (encode-value n2)
                               (when (weighted? g) (str "\t" (encode-value (weight g n1 n2)))) "\n"))))))

(defn read-edge-list [source]
  (let [lines (clojure.string/split-lines (slurp (if (and (string? source) (.exists (file source))) source source)))
        header (first lines) directed (boolean (re-find #"directed=true" header)) weighted (boolean (re-find #"weighted=true" header))
        nodes (for [l lines :when (clojure.string/starts-with? l "# node\t")] (decode-value (subs l 7)))
        es (for [l lines :when (and (seq l) (not (clojure.string/starts-with? l "#")))
                 :let [p (clojure.string/split l #"\t")]]
             [(decode-value (first p)) (decode-value (second p)) (when weighted (decode-value (nth p 2)))])]
    (reduce (fn [g e] (apply add-edges g [(if weighted e (subvec e 0 2))]))
            (reduce add-nodes ((graph-for directed weighted)) nodes) es)))

(defn write-adjacency-json
  "Writes a self-describing adjacency JSON document. Values are EDN strings."
  [g f]
  (let [ns (vec (nodes g))
        ids (zipmap ns (map str (range)))
        node-attrs (into {} (for [n ns]
                              [(ids n) (into {} (map (fn [[k v]]
                                                       [(encode-value k) (encode-value v)])
                                                     (or (attrs g n) {})))]))
        adjacency
        (into {}
              (for [n ns]
                [(ids n)
                 (mapv (fn [n2]
                         (let [as (attrs-entries g n n2)]
                           (cond-> {:node (ids n2)}
                             (weighted? g) (assoc :weight (encode-value (weight g n n2)))
                             (seq as) (assoc :attrs
                                              (into {}
                                                    (map (fn [[k v]]
                                                           [(encode-value k) (encode-value v)]) as))))))
                       (successors g n))]))]
    (spit (str (file f))
          (json/write-str {:directed (directed? g)
                           :weighted (weighted? g)
                           :nodes (mapv encode-value ns)
                           :node-attrs node-attrs
                           :adjacency adjacency}))))

(defn read-adjacency-json [source]
  (let [m (json/read-str (slurp (if (and (string? source) (.exists (file source))) source source)))
        directed (get m "directed") weighted (get m "weighted") ns (mapv decode-value (get m "nodes"))
        ids (zipmap (map str (range)) ns)
        es (for [[id nbrs] (get m "adjacency") n nbrs]
             [(ids id) (ids (get n "node")) (when weighted (decode-value (get n "weight")))])
        g (reduce add-nodes ((graph-for directed weighted)) ns)
        g (reduce (fn [g e] (apply add-edges g [(if weighted e (subvec e 0 2))])) g es)
        g (reduce (fn [g [id as]]
                      (reduce (fn [g [k v]] (add-attr g (ids id) (decode-value k) (decode-value v)))
                              g as))
                    g (get m "node-attrs"))]
    (reduce (fn [g [id nbrs]]
                (reduce (fn [g n]
                        (reduce (fn [g [k v]] (add-attr g (ids id) (ids (get n "node")) (decode-value k) (decode-value v)))
                                g (get n "attrs"))) g nbrs)) g (get m "adjacency"))))

(defn read-dot
  "Reads the node and edge subset emitted by dot-str, plus simple DOT files."
  [source]
  (let [s (if (and (string? source) (.exists (file source))) (slurp source) (str source))
        directed (boolean (re-find #"(?m)^\s*digraph\b" s))
        edges (for [[_ a _op b as] (re-seq #"(?m)^\s*([A-Za-z0-9_\".:-]+)\s*(->|--)\s*([A-Za-z0-9_\".:-]+)(?:\s*\[([^]]*)\])?" s)]
                [a b (or as "")])
        node-lines (for [[_ id as] (re-seq #"(?m)^\s*([A-Za-z0-9_\".:-]+)\s*\[([^]]*)\]" s)]
                     [id (or (second (re-find #"label=\"([^\"]*)\"" as)) id) as])
        labels (into {} (map (fn [[id label _]] [id (clojure.string/replace label #"\\n" "\n")]) node-lines))
        es (map (fn [[a b as]] [(get labels a a) (get labels b b) as]) edges)
        weighted (some #(second (re-find #"label=\"(-?\d+(?:\.\d+)?)\"" (nth % 2))) es)
        ctor (graph-for directed weighted)
        g (apply add-edges (ctor) (map (fn [[a b as]] (if weighted [a b (Double/parseDouble (second (re-find #"label=\"(-?\d+(?:\.\d+)?)\"" as)))] [a b])) es))]
    (apply add-nodes g (vals labels))))

(defn- os
  "Returns :win, :mac, :unix, or nil"
  []
  (condp
      #(<= 0 (.indexOf ^String %2 ^String %1))
      (.toLowerCase (System/getProperty "os.name"))
    "win" :win
    "mac" :mac
    "nix" :unix
    "nux" :unix
    nil))

(defn- open
  "Opens the given file (a string, File, or file URI) in the default application
  for the current desktop environment. Returns nil."
  [f]
  (let [f (file f)]
    ;; java.awt.Desktop has an 'open' method. It hangs on Windows with Clojure
    ;; Box and makes the process a GUI process on Mac OS X.
    (condp = (os)
      :mac (sh "open" (str f))
      :win (sh "cmd" (str "/c start " (-> f .toURI .toURL str)))
      :unix (sh "xdg-open" (str f)))
    nil))

(defn- open-data
  "Writes data (a string or bytes) to a temporary file. The extension can be a
  string or keyword, with or without a dot. Opens the file in the default
  application for that extension. Returns nil."
  [data ext]
  (let [ext (name ext)
        ext (if (= \. (first ext)) ext (str \. ext))
        tmp (java.io.File/createTempFile (subs ext 1) ext)]
    (if (string? data)
      (with-open [w (java.io.FileWriter. tmp)]
        (.write w ^String data))
      (with-open [w (java.io.FileOutputStream. tmp)]
        (.write w ^bytes data)))
    (.deleteOnExit tmp)
    (open tmp)))

(defn render-to-bytes
  "Renders graph g in an image format with GraphViz. Returns data as a byte
  array. GraphViz's 'dot' or a specified algorithm must be in the shell path.
  Algorithms include :dot, :neato, :fdp, :sfdp, :twopi, and :circo. Formats
  include :png, :ps, :pdf, and :svg."
  [g & {:keys [alg fmt] :or {alg "dot" fmt :png} :as opts}]
  (let [dot (apply dot-str g (apply concat opts))
        {:keys [out]} (sh (name alg) (str "-T" (name fmt)) :in dot :out-enc :bytes)]
    out))

(defn view
  "Converts graph g to a temporary image file with GraphViz. Opens the file in
  the default viewer for the current desktop environment. GraphViz's 'dot' or a
  specified algorithm must be in the shell path. Algorithms include :dot,
  :neato, :fdp, :sfdp, :twopi, and :circo. Formats include :png, :ps, :pdf,
  and :svg."
  [g & {:keys [fmt] :or {fmt :png} :as opts}]
    (open-data (apply render-to-bytes g (apply concat opts)) fmt))

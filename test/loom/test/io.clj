(ns loom.test.io
  (:require [clojure.test :refer [deftest is]]
            [loom.graph :refer [weighted-digraph graph weighted-graph
                                nodes edges weight weighted?]]
            [loom.attr :refer [add-attr attr attrs attr?]]
            [loom.io :as loom-io]))

(defn- temp-file [suffix]
  (doto (java.io.File/createTempFile "loom-io-" suffix)
    (.deleteOnExit)))

(defn- assert-graph-equivalent [a b]
  (is (= (set (nodes a)) (set (nodes b))))
  (is (= (set (edges a)) (set (edges b))))
  (doseq [[n1 n2] (edges a)]
    (when (and (weighted? a) (weighted? b))
      (is (= (weight a n1 n2) (weight b n1 n2)))))
  (doseq [n (nodes a)]
    (when (and (attr? a) (attr? b))
      (is (= (attrs a n) (attrs b n))))))

(deftest graphml-round-trip
  (let [g (-> (weighted-digraph ["a" "b" 2.5] ["b" "c" 1])
              (add-attr "a" :color "red")
              (add-attr "a" "b" :kind :road))
        f (temp-file ".graphml")]
    (loom-io/write-graphml g f)
    (assert-graph-equivalent g (loom-io/read-graphml f))
    (is (= "red" (attr (loom-io/read-graphml f) "a" :color)))))

(deftest graphml-rejects-external-entities
  (let [payload (str "<?xml version=\"1.0\"?>"
                    "<!DOCTYPE graphml ["
                    "<!ENTITY xxe SYSTEM \"file:///etc/passwd\">"
                    "]>"
                    "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">"
                    "<graph id=\"G\" edgedefault=\"directed\">"
                    "<node id=\"n0\" label=\"&xxe;\"/>"
                    "</graph>"
                    "</graphml>")]
    (is (thrown? Exception (loom-io/read-graphml payload)))))

(deftest xml-parser-explicitly-disables-external-entities
  (let [harden-xml-factory (ns-resolve 'loom.io 'harden-xml-factory)
        factory (javax.xml.parsers.DocumentBuilderFactory/newInstance)]
    (is (some? harden-xml-factory))
    (when harden-xml-factory
      (let [hardened (harden-xml-factory factory)]
        (is (.getFeature hardened "http://apache.org/xml/features/disallow-doctype-decl"))
        (is (not (.getFeature hardened "http://xml.org/sax/features/external-general-entities")))
        (is (not (.getFeature hardened "http://xml.org/sax/features/external-parameter-entities")))
        (is (false? (.isXIncludeAware hardened)))
        (is (false? (.isExpandEntityReferences hardened)))))))

(deftest gexf-round-trip
  (let [g (-> (weighted-graph ["a" "b" 3] ["b" "c" 4])
              (add-attr "b" :label "middle")
              (add-attr "a" "b" :kind "road"))
        f (temp-file ".gexf")]
    (loom-io/write-gexf g f)
    (assert-graph-equivalent g (loom-io/read-gexf f))
    (is (= "middle" (attr (loom-io/read-gexf f) "b" :label)))))

(deftest edge-list-round-trip
  (let [g (weighted-digraph ["a" "b" 2] ["b" "c" 3])
        f (temp-file ".edgelist")]
    (loom-io/write-edge-list g f)
    (assert-graph-equivalent g (loom-io/read-edge-list f))))

(deftest adjacency-json-round-trip
  (let [g (-> (weighted-digraph ["a" "b" 2] ["b" "c" 3])
              (add-attr "a" :color "red")
              (add-attr "a" "b" :kind "road"))
        f (temp-file ".json")]
    (loom-io/write-adjacency-json g f)
    (assert-graph-equivalent g (loom-io/read-adjacency-json f))
    (is (= "red" (attr (loom-io/read-adjacency-json f) "a" :color)))))

(deftest dot-import
  (let [g (graph ["a" "b"] ["b" "c"])
        imported (loom-io/read-dot (loom-io/dot-str g))]
    (is (= (set (nodes g)) (set (nodes imported))))
    (is (= (set (edges g)) (set (edges imported))))))

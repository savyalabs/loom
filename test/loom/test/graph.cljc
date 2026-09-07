(ns loom.test.graph
  (:require [loom.graph :as graph
            :refer (graph digraph weighted-graph weighted-digraph
                                      multigraph multidigraph graph-from-edges
                                      digraph-from-edges weighted-graph-from-edges
                                      weighted-digraph-from-edges edges-with-ids
                                      out-edges-with-ids edge-key
                                      nodes edges has-node? has-edge? transpose fly-graph
                                      remove-nodes remove-edges
                                      weight graph? Graph directed? Digraph weighted?
                                      WeightedGraph subgraph add-path add-cycle)]
            [loom.attr :as attr]
            #?@(:clj [[clojure.test :refer (deftest testing are is)]])
            [loom.test.compliance-tester :refer [graph-test digraph-test
                                                 weighted-graph-test weighted-digraph-test]])
  #?@(:cljs [(:require-macros [cljs.test :refer (deftest testing are is)])]))

(deftest multigraph-parallel-edge-test
  (let [g (multigraph [1 2 :first 10]
                      [1 2 :second 20])
        es (vec (edges-with-ids g))]
    (is (= 4 (count es)))
    (is (= #{:first :second} (set (map edge-key es))))
    (is (= #{[1 2] [2 1]} (set (edges g))))
    (is (= #{:first :second}
           (set (map edge-key (out-edges-with-ids g 1)))))
    (is (= 10 (weight g (first (filter #(= :first (edge-key %)) es)))))
    (is (= 20 (weight g (first (filter #(= :second (edge-key %)) es)))))
    (let [first-edge (first (filter #(= :first (edge-key %)) es))
          second-edge (first (filter #(= :second (edge-key %)) es))
          g (attr/add-attr g first-edge :label :first-label)]
      (is (= {:label :first-label} (attr/attrs g first-edge)))
      (is (nil? (attr/attr g second-edge :label))))))

(deftest remove-keyed-multigraph-edge-test
  (let [g (remove-edges (multigraph [1 2] [1 2]) [1 2 0])
        es (vec (edges-with-ids g))]
    (is (= 2 (count es)))
    (is (= #{1} (set (map edge-key es))))
    (is (= #{[1 2] [2 1]} (set (edges g))))))

(deftest multidigraph-edge-direction-test
  (let [g (multidigraph [1 2 :a 3] [1 2 :b 4])]
    (is (= 2 (count (out-edges-with-ids g 1))))
    (is (= #{[1 2]} (set (graph/in-edges g 2))))
    (is (= #{[2 1]} (set (edges (transpose g)))))
    (is (= #{:a :b} (set (map edge-key (edges-with-ids (transpose g))))))))

(deftest batch-construction-test
  (let [es (vec (map (fn [n] [n (inc n)]) (range 2000)))
        one-at-a-time (fn [constructor edge-seq]
                        (reduce (fn [g e] (constructor g e)) (constructor) edge-seq))]
    (are [batch one-at-a-time]
         (= (set (edges batch)) (set (edges one-at-a-time)))
      (graph-from-edges es) (one-at-a-time graph es)
      (digraph-from-edges es) (one-at-a-time digraph es)
      (weighted-graph-from-edges (map #(conj % 2) es))
      (one-at-a-time weighted-graph (map #(conj % 2) es))
      (weighted-digraph-from-edges (map #(conj % 2) es))
      (one-at-a-time weighted-digraph (map #(conj % 2) es)))))

(deftest test-default-implementations
  (graph-test (graph))
  (digraph-test (digraph))
  (weighted-graph-test (weighted-graph))
  (weighted-digraph-test (weighted-digraph)))

(deftest build-graph-test
  (let [g1 (graph [1 2] [1 3] [2 3] 4)
        g2 (graph {1 [2 3] 2 [3] 4 []})
        g3 (graph g1)
        g4 (graph g3 (digraph [5 6]) [7 8] 9)
        g5 (graph)]
    (testing "Construction, nodes, edges"
      (are [expected got] (= expected got)
           #{1 2 3 4} (set (nodes g1))
           #{[1 2] [2 1] [1 3] [3 1] [2 3] [3 2]} (set (edges g1))
           (set (nodes g2)) (set (nodes g1))
           (set (edges g2)) (set (edges g1))
           (set (nodes g3)) (set (nodes g1))
           (set (nodes g3)) (set (nodes g1))
           #{1 2 3 4 5 6 7 8 9} (set (nodes g4))
           #{[1 2] [2 1] [1 3] [3 1] [2 3]
             [3 2] [5 6] [6 5] [7 8] [8 7]} (set (edges g4))
             #{} (set (nodes g5))
             #{} (set (edges g5))
             true (has-node? g1 4)
             true (has-edge? g1 1 2)
             false (has-node? g1 5)
             false (has-edge? g1 4 1)))))

(deftest remove-nodes-prunes-attrs-test
  ;; remove-nodes left attribute entries for the removed node and edge attributes
  ;; on other nodes that referenced it (#93).
  (testing "node and outgoing-edge attrs under the removed node"
    (let [g (-> (digraph {:a [:b]})
                (attr/add-attr :a :color :red)
                (attr/add-attr [:a :b] :foo :bar)
                (remove-nodes :a))]
      (is (nil? (get-in g [:attrs :a])))))
  (testing "back-reference edge attr stored on the surviving neighbor"
    (let [g (-> (graph {:a [:b]})
                (attr/add-attr [:a :b] :foo :bar)
                (remove-nodes :a))]
      (is (nil? (get-in g [:attrs :a])))
      (is (empty? (attr/attrs g :b :a))))))

(deftest multigraph-remove-nodes-prunes-attrs-test
  (let [g (multigraph [:a :b :ab 1]
                      [:b :c :bc 1])
        edge (first (filter #(= :ab (edge-key %))
                            (out-edges-with-ids g :a)))
        g (-> g
              (attr/add-attr :a :color :red)
              (attr/add-attr edge :kind :rail))
        pruned (remove-nodes g :a)]
    (is (nil? (get-in pruned [:attrs :a])))
    (is (nil? (try
                (attr/attr pruned :a :color)
                (catch #?(:clj Throwable :cljs :default) _ ::threw))))
    (is (nil? (get-in pruned [:attrs :b :loom.attr/edge-attrs :ab])))
    (is (nil? (attr/attr pruned edge :kind)))))

(deftest subgraph-prunes-attrs-like-remove-nodes-test
  (let [g (-> (graph [:a :b] [:b :c])
              (attr/add-attr :a :color :red)
              (attr/add-attr [:a :b] :kind :rail))
        removed (remove-nodes g :a)
        induced (subgraph g [:b :c])]
    (is (= (:attrs removed) (:attrs induced)))
    (is (nil? (get-in induced [:attrs :a])))
    (is (nil? (attr/attr induced :b :a :kind)))))

(deftest weight-edge-arity-test
  ;; weight on an edge must dispatch to (weight* g e), not (weight* g src dest).
  ;; These differ when an edge is not determined by its endpoints, as in
  ;; multigraphs. #141
  (let [g (reify WeightedGraph
            (weight* [_ _e] :edge-arity)
            (weight* [_ _n1 _n2] :node-arity))]
    (is (= :edge-arity (weight g [1 2])))
    (is (= :node-arity (weight g 1 2)))))

(deftest empty-map-construction-test
  ;; Building from an empty adjacency map caused an NPE on (val (first {})) (#137).
  (are [g] (and (empty? (nodes g)) (empty? (edges g)))
    (graph {})
    (digraph {})
    (weighted-graph {})
    (weighted-digraph {})))

(deftest simple-graph-test
  (let [g1 (graph [1 2] [1 3] [2 3] 4)
        g2 (graph {1 [2 3] 2 [3] 4 []})
        g3 (graph g1)
        g4 (graph g3 (digraph [5 6]) [7 8] 9)
        g5 (graph)]
    (testing "Construction, nodes, edges"
      (are [expected got] (= expected got)
           #{1 2 3 4} (set (nodes g1))
           #{[1 2] [2 1] [1 3] [3 1] [2 3] [3 2]} (set (edges g1))
           (set (nodes g2)) (set (nodes g1))
           (set (edges g2)) (set (edges g1))
           (set (nodes g3)) (set (nodes g1))
           (set (nodes g3)) (set (nodes g1))
           #{1 2 3 4 5 6 7 8 9} (set (nodes g4))
           #{[1 2] [2 1] [1 3] [3 1] [2 3]
             [3 2] [5 6] [6 5] [7 8] [8 7]} (set (edges g4))
             #{} (set (nodes g5))
             #{} (set (edges g5))
             true (has-node? g1 4)
             true (has-edge? g1 1 2)
             false (has-node? g1 5)
             false (has-edge? g1 4 1)))))

(deftest simple-digraph-test
  (let [g1 (digraph [1 2] [1 3] [2 3] 4)
        g2 (digraph {1 [2 3] 2 [3] 4 []})
        g3 (digraph g1)
        g4 (digraph g3 (graph [5 6]) [7 8] 9)
        g5 (digraph)
        g6 (transpose g1)]
    (testing "Construction, nodes, edges"
      (are [expected got] (= expected got)
           #{1 2 3 4} (set (nodes g1))
           #{1 2 3 4} (set (nodes g6))
           #{[1 2] [1 3] [2 3]} (set (edges g1))
           #{[2 1] [3 1] [3 2]} (set (edges g6))
           (set (nodes g2)) (set (nodes g1))
           (set (edges g2)) (set (edges g1))
           (set (nodes g3)) (set (nodes g1))
           (set (nodes g3)) (set (nodes g1))
           #{1 2 3 4 5 6 7 8 9} (set (nodes g4))
           #{[1 2] [1 3] [2 3] [5 6] [6 5] [7 8]} (set (edges g4))
           #{} (set (nodes g5))
           #{} (set (edges g5))
           true (has-node? g1 4)
           true (has-edge? g1 1 2)
           false (has-node? g1 5)
           false (has-edge? g1 2 1)))))

(deftest simple-weighted-graph-test
  (let [g1 (weighted-graph [1 2 77] [1 3 88] [2 3 99] 4)
        g2 (weighted-graph {1 {2 77 3 88} 2 {3 99} 4 []})
        g3 (weighted-graph g1)
        g4 (weighted-graph g3 (weighted-digraph [5 6 88]) [7 8] 9)
        g5 (weighted-graph)]
    (testing "Construction, nodes, edges"
      (are [expected got] (= expected got)
           #{1 2 3 4} (set (nodes g1))
           #{[1 2] [2 1] [1 3] [3 1] [2 3] [3 2]} (set (edges g1))
           (set (nodes g2)) (set (nodes g1))
           (set (edges g2)) (set (edges g1))
           (set (nodes g3)) (set (nodes g1))
           (set (nodes g3)) (set (nodes g1))
           #{1 2 3 4 5 6 7 8 9} (set (nodes g4))
           #{[1 2] [2 1] [1 3] [3 1] [2 3]
             [3 2] [5 6] [6 5] [7 8] [8 7]} (set (edges g4))
             #{} (set (nodes g5))
             #{} (set (edges g5))
             true (has-node? g1 4)
             true (has-edge? g1 1 2)
             false (has-node? g1 5)
             false (has-edge? g1 4 1)))))

(deftest simple-weighted-digraph-test
  (let [g1 (weighted-digraph [1 2 77] [1 3 88] [2 3 99] 4)
        g2 (weighted-digraph {1 {2 77 3 88} 2 {3 99} 4 []})
        g3 (weighted-digraph g1)
        g4 (weighted-digraph g3 (weighted-graph [5 6 88]) [7 8] 9)
        g5 (weighted-digraph)
        g6 (transpose g1)]
    (testing "Construction, nodes, edges"
      (are [expected got] (= expected got)
           #{1 2 3 4} (set (nodes g1))
           #{1 2 3 4} (set (nodes g6))
           #{[1 2] [1 3] [2 3]} (set (edges g1))
           #{[2 1] [3 1] [3 2]} (set (edges g6))
           (set (nodes g2)) (set (nodes g1))
           (set (edges g2)) (set (edges g1))
           (set (nodes g3)) (set (nodes g1))
           (set (nodes g3)) (set (nodes g1))
           #{1 2 3 4 5 6 7 8 9} (set (nodes g4))
           #{[1 2] [1 3] [2 3] [5 6] [6 5] [7 8]} (set (edges g4))
           #{} (set (nodes g5))
           #{} (set (edges g5))
           true (has-node? g1 4)
           true (has-edge? g1 1 2)
           false (has-node? g1 5)
           false (has-edge? g1 2 1)))))

(deftest fly-graph-test
  (let [fg1 (fly-graph :nodes [1 2 3]
                       :successors #(if (= 3 %) [1] [(inc %)])
                       :weight (constantly 88))
        fg2 (fly-graph :successors #(if (= 3 %) [1] [(inc %)])
                       :start 1)]
    (testing "Construction, nodes, edges"
      (are [expected got] (= expected got)
           #{1 2 3} (set (nodes fg1))
           #{1 2 3} (set (nodes fg2))
           #{[1 2] [2 3] [3 1]} (set (edges fg1))
           #{[1 2] [2 3] [3 1]} (set (edges fg2))
           88 (weight fg1 1 2)))
    (testing "Predicates"
      (are [expected got] (= expected got)
           1 (has-node? fg1 1)
           nil (has-node? fg1 11)
           2 (has-node? fg2 2)
           nil (has-node? fg2 11)))
    ;; TODO: finish
    ))

(deftest merge-graph-test
  (testing "two graphs with attributes"
    (let [g1 (attr/add-attr (digraph [1 2] 3 [1 4]) 1 :label "One")
          g2 (attr/add-attr (digraph [1 3] [3 5]) 5 :label "Five")
          merged (digraph g1 g2)]
      (is (= "One"  (attr/attr merged 1 :label)))
      (is (= "Five" (attr/attr merged 5 :label)))))
  (testing "with two weighted graphs"
    (let [g1 (attr/add-attr (weighted-graph [1 2] 3 [1 4]) 1 :label "One")
          g2 (attr/add-attr (weighted-graph [1 3] [3 5]) 5 :label "Five")
          merged (weighted-graph g1 g2)]
      (is (= "One"  (attr/attr merged 1 :label)))
      (is (= "Five" (attr/attr merged 5 :label))))))

(deftest utilities-test
  (testing "Predicates"
    (are [expected got] (= expected got)
         true (every? true? (map graph? [(graph [1 2])
                                         (digraph [1 2])
                                         (weighted-graph [1 2])
                                         (weighted-digraph [1 2])
                                         (fly-graph :successors [1 2])
                                         #_:clj-kondo/ignore (reify Graph)]))
         true (every? true? (map directed? [(digraph [1 2])
                                            (weighted-digraph [1 2])
                                            (fly-graph :predecessors [1 2])
                                            #_:clj-kondo/ignore (reify Digraph)]))
         true (every? true? (map weighted? [(weighted-graph [1 2])
                                            (weighted-digraph [1 2])
                                            (fly-graph :weight (constantly 1))
                                            #_:clj-kondo/ignore (reify WeightedGraph)]))))
  (testing "Adders"
    (let [g (weighted-digraph [1 2] [2 3] [3 1])
          sg (subgraph g [1 2])
          pg (add-path (digraph) 1 2 3 4 5)
          cg (add-cycle (digraph) 1 2 3)]
      (are [expected got] (= expected got)
           #{1 2} (set (nodes sg))
           #{[1 2]} (set (edges sg))
           true (graph? sg)
           true (directed? sg)
           true (weighted? sg)
           #{[1 2] [2 3] [3 4] [4 5]} (set (edges pg))
           #{[1 2] [2 3] [3 1]} (set (edges cg))))))

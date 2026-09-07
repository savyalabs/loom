(ns loom.test.alg
  (:require [loom.graph :refer [graph weighted-graph digraph weighted-digraph
                                multigraph multidigraph nodes successors remove-nodes
                                add-nodes edges edges-with-ids weight add-edges
                                fly-graph]]
            [loom.attr :as attr]
            [loom.alg :refer [pre-traverse post-traverse pre-span topsort
                              bf-traverse bf-span bf-path
                              dijkstra-path dijkstra-path-dist
                              dijkstra-traverse dijkstra-span johnson
                              all-pairs-shortest-paths connected-components
                              connected? scc strongly-connected? connect
                              dag? shortest-path loners bellman-ford
                              density
                              bipartite-color bipartite? bipartite-sets
                              coloring? greedy-coloring prim-mst-edges
                              prim-mst astar-path astar-dist
                              degeneracy-ordering maximal-cliques simple-paths
                              subgraph? eql? isomorphism? digraph-all-cycles
                              clustering-coefficient pagerank degree-centrality
                              closeness-centrality betweenness-centrality
                              eigenvector-centrality hits articulation-points
                              bridges biconnected-components k-core eccentricity
                              radius diameter]]
            [loom.derived :refer [mapped-by]]
            clojure.walk
            #?@(:clj [[clojure.test :refer [are deftest is testing]]]
                :cljs [cljs.test]))
  #_:clj-kondo/ignore
  #?@(:cljs [(:require-macros [cljs.test :refer (deftest testing are is)])]))

(defn- exception-data [f]
  (try
    (f)
    nil
    (catch #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo) e
      (ex-data e))))

;; http://en.wikipedia.org/wiki/Dijkstra's_algorithm
(def g1
  (weighted-graph
   [1 2 7] [1 3 9] [1 6 14] [2 3 10] [2 4 15]
   [3 4 11] [3 6 2] [4 5 6] [5 6 9]))

;; http://www.algolist.com/Dijkstra's_algorithm
(def g2
  (weighted-graph
   [:r :g 10] [:r :b 5] [:r :o 8] [:g :b 3] [:b :p 7] [:p :o 2]))

;; http://fr.wikipedia.org/wiki/Algorithme_de_Dijkstra
(def g4
  (weighted-graph
   [:a :b 85]
   [:b :f 80]
   [:f :i 250]
   [:i :j 84]
   [:a :c 217]
   [:c :g 186]
   [:c :h 103]
   [:d :h 183]
   [:h :j 167]
   [:a :e 173]
   [:e :j 502]))

;; Algorithm Design Manual, p 179
(def g5
  (digraph {:a [:b :c]
            :b [:c :d]
            :c [:e :f]
            :d []
            :e [:d]
            :f [:e]
            :g [:a :f]}))

(def g6 (graph [0 1] [1 2] [1 3] [2 4] [3 4] [0 5]))

(def g7 (digraph [1 2] [2 3] [3 1] [5 6] [6 7]))

(def g8 (graph {1 [2 3 4] 5 [6 7 8]}))

;; Algorithm Design Manual, p 182
(def g9
  (digraph {8 #{6},
            7 #{5},
            6 #{7},
            5 #{6},
            4 #{1 6 8},
            3 #{1},
            2 #{3 4 5},
            1 #{2}}))

;; http://en.wikipedia.org/wiki/Strongly_connected_component
(def g10
  (digraph {:a [:b]
            :b [:c :e :f]
            :c [:d :g]
            :d [:c :h]
            :e [:a :f]
            :f [:g]
            :g [:f]
            :h [:g :d]}))

;; Weighted directed graph with a negative-weight cycle reachable from sources
;; :a, :b, :d, and :e.
;; http://www.seas.gwu.edu/~simhaweb/alg/lectures/module9/module9.html
(def g11
  (weighted-digraph [:a :b 3]
                    [:b :c 4]
                    [:b :d 5]
                    [:d :e 2]
                    [:e :b -8]))

;; Weighted directed graph with a non-negative-weight cycle. It is similar to
;; g11, but the edge [:e :b] has a different weight.
(def g12
  (weighted-digraph [:a :b 3]
                    [:b :c 4]
                    [:b :d 5]
                    [:d :e 2]
                    [:e :b -7]))

;; Directed graph with 4 strongly connected components.
(def g13
  (digraph [1 5]
           [2 4]
           [3 1]
           [3 2]
           [3 6]
           [4 10]
           [5 3]
           [6 1]
           [6 10]
           [7 8]
           [8 9]
           [8 11]
           [9 3]
           [9 5]
           [9 7]
           [10 2]
           [11 2]
           [11 4]))


(def g14
  (digraph [1 2]
           [2 3]
           [2 4]))

(def g15
  (digraph [1 2]
           [3 2]
           [2 4]))

(def g16
  (digraph [:a :e]
           [:a :b]
           [:a :c]
           [:e :d]
           [:d :c]))

;; Simple directed triangle graph.
(def triangle (digraph [:a :b]
                       [:b :c]
                       [:c :a]))

;; Graphs for mst.
;; http://en.wikipedia.org/wiki/Kruskal's_algorithm
(def mst_wt_g1 (weighted-graph '(:a, :e , 1)
                        '(:c, :d ,2)
                        '(:a,:b, 3),
                        '(:b,:e,4),
                        '(:b,:c,5)
                        '(:e,:c,6)
                        '(:e,:d,7)))

;; Graph with 2 components.
(def mst_wt_g2 (weighted-graph [:a :b 2]
                              [:a :d 1]
                              [:b :d 2]
                              [:c :d 3]
                              [:b :c 1]
                              [:e :f 1]
                             ))

(def mst_unweighted_g3 (graph [:a :b] [:a :c] [:a :d] [:b :d] [:c :d]))

(def mst_wt_g4 (weighted-graph [:a :b 1]))

(def mst_wt_g5 (weighted-graph [:a :b 5] [:a :c 2] [:b :c 2]))

;; Graph from Cormen et al.
(def mst_wt_g6 (weighted-graph [:a :b 4] [:a :h 8]
                               [:b :c 8] [:b :h 11]
                               [:c :d 7] [:c :f 4] [:c :i 2]
                               [:d :f 14] [:d :e 9]
                               [:e :f 10]
                               [:f :g 2]
                               [:i :h 7] [:i :g 6]
                               [:h :g 1] ))


;; Graph with 2 components and 2 isolated nodes.
(def mst_wt_g7 (weighted-graph [:a :b 2]
                               [:b :d 2]
                               [:e :f 1]
                               :g :h
                             ))

(def directed-graph1 (digraph [1 2] [2 3] [2 4] [3 1] [4 3]))

(def directed-graph2 (digraph {1 [2 5 8]
                               2 [3 7 9]
                               3 [1 2 4 6]
                               4 [5]
                               5 [2]
                               6 [4]
                               8 [9]
                               9 [8]}))

(def directed-graph3 (digraph [1 2] [1 5] [2 4] [2 5] [2 7] [3 2] [3 7]
                              [4 1] [4 3] [4 7] [5 6] [5 7] [6 1] [6 2]
                              [6 4] [7 1]))

;; No cycles present here
(def directed-graph4 (digraph {1 [2 3]
                               2 [4 5]
                               3 [4 5]
                               6 [1]}))

(deftest depth-first-test
  (are [expected got] (= expected got)
       #{1 2 3 5 6 7} (set (pre-traverse g7))
       #{1 2 3} (set (pre-traverse g7 1))
       #{1 2 3 4 5 6 7 8} (set (pre-traverse g8))
       #{1 2 3 4 5 6 7 8} (set (post-traverse g8))
       [:d :e :f :c :b :a :g] (post-traverse g5 :g)
       false (not (some #{(pre-traverse g16 :a)} [[:a :e :d :c :b]
                                                  [:a :b :c :e :d]
                                                  [:a :b :e :d :c]
                                                  [:a :c :b :e :d]
                                                  [:a :c :e :d :b]]))
       false (not (some #{(post-traverse g7 1)} [[3 2 1] [2 3 1]]))
       #{1 2 3 4 5 6 7 8} (set (nodes (digraph (pre-span g8))))
       #{2 3 4} (set (successors (digraph (pre-span g8 1)) 1))
       #{1 5} (set (successors (digraph (pre-span g6 0)) 0))
       true (let [span (digraph (pre-span g6 0))]
              (and (or (= #{3} (set (successors span 4)))
                       (= #{2} (set (successors span 4))))
                   (or (= #{3} (set (successors span 1)))
                       (= #{2} (set (successors span 1))))))
       [:g :a :b :c :f :e :d] (topsort g5)
       nil (topsort g7)
       [5 6 7] (topsort g7 5)
       
       [1 2 4] (topsort g15 1)))

(deftest depth-first-test-2
  (is (#{[1 2 3 4] [1 2 4 3]} (topsort g14 1))))

(deftest breadth-first-test
  (are [expected got] (= expected got)
       #{1 2 3 5 6 7} (set (bf-traverse g7))
       #{1 2 3} (set (bf-traverse g7 1))
       #{1 2 3 4 5 6 7 8} (set (bf-traverse g8))
       #{1 2 3 4 5 6 7 8} (set (nodes (digraph (bf-span g8))))
       #{2 3} (set (successors (digraph (bf-span g6 0)) 1))
       ;; Start at 0. The one-argument form starts from the first node in (nodes g).
       ;; Hash order could select a nondeterministic start node.
       false (not (some #{(bf-traverse (remove-nodes g6 5) 0)}
                        [[0 1 2 3 4] [0 1 3 2 4]]))
       #{:r} (set (bf-traverse g2 :r :when #(< %3 1)))
       #{:r :o :b :g} (set (bf-traverse g2 :r :when #(< %3 2)))
       #{:r :o :b :g :p} (set (bf-traverse g2 :r :when #(< %3 3)))
       [:a :e :j] (bf-path g4 :a :j)
       [:a :c :h :j] (bf-path g4 :a :j :when (fn [n _ _] (not= :e n)))
       
       #?@(:clj [[:a :e :j] (loom.alg/bf-path-bi g4 :a :j)
                 true (some #(= % (loom.alg/bf-path-bi g5 :g :d)) [[:g :a :b :d] [:g :f :e :d]])])))

(deftest bf-path-start-equals-end-test
  (let [g (graph [1 2])]
    (is (= [1] (bf-path g 1 1)))
    (is (= [1 2] (bf-path g 1 2)))))

(deftest simple-paths-test
  (are [expected got] (= expected got)
       [[0]] (simple-paths g6 0 0)
       [[:a]] (simple-paths g5 :a :a)
       (set [[:a :b :d]
             [:a :c :e :d]
             [:a :c :f :e :d]
             [:a :b :c :e :d]
             [:a :b :c :f :e :d]]) (set (simple-paths g5 :a :d))
       (set [[0 1 3 4]
             [0 1 2 4]]) (set (simple-paths g6 0 4))
       (set [[:a :b :d]]) (set (simple-paths g11 :a :d))
       (set [[:a :b :e :f :g]
             [:a :b :f :g]
             [:a :b :c :g]
             [:a :b :c :d :h :g]]) (set (simple-paths g10 :a :g))
       (set [[:a :b :e :f :g]
             [:a :b :f :g]
             [:a :b :c :g]]) (set (simple-paths g10 :a :g :max-depth 5))
       (set []) (set (simple-paths g10 :a :g :max-depth 2))))

(deftest dijkstra-test
  (are [expected got] (= expected got)
       [:a :c :h :j] (dijkstra-path g4 :a :j)
       [[:a :c :h :j] 487] (dijkstra-path-dist g4 :a :j)
       [[:r :o :p] 10] (dijkstra-path-dist g2 :r :p)
       #{:r :g :b :o :p} (set (map first (dijkstra-traverse g2)))
       {:r {:o 8 :b 5} :b {:g 8} :o {:p 10}} (dijkstra-span g2 :r)))

(deftest multigraph-weight-test
  (let [g (multidigraph [:a :b 100]
                        [:a :b 1]
                        [:b :c 1])]
    (testing "weight supports endpoint and keyed multigraph edges"
      (is (= 1 (weight g (first (edges g)))))
      (is (= #{1 100} (set (map #(weight g %) (edges-with-ids g)))))
      (is (= 1 (weight g :a :b))))
    (testing "weighted algorithms use the cheapest parallel edge"
      (is (= [:a :b :c] (dijkstra-path g :a :c)))
      (is (= 2 (get (first (bellman-ford g :a)) :c))))))

(deftest validation-test
  (testing "path algorithms reject missing nodes with structured errors"
    (is (= {:type :loom.alg/missing-node :node :missing :operation :bf-path}
           (exception-data #(bf-path g4 :a :missing)))))
  (testing "Dijkstra and A* reject negative weights"
    (let [g (weighted-digraph [:a :b -1])]
      (is (= {:type :loom.alg/negative-weight :algorithm :dijkstra}
             (exception-data #(dijkstra-path g :a :b))))
      (is (= {:type :loom.alg/negative-weight :algorithm :astar}
             (exception-data #(astar-path g :a :b nil))))
      (is (vector? (bellman-ford g :a))))))

(deftest johnson-test
  (are [expected got] (= expected got)
       {:p {:p {:o 2, :b 7}
            :o {:r 10}
            :b {:g 10}}
        :o {:o {:p 2, :r 8}
            :p {:b 9}
            :b {:g 12}}
        :g {:g {:b 3}
            :b {:r 8, :p 10}
            :p {:o 12}}
        :b {:b {:p 7, :g 3, :r 5}
            :p {:o 9}}
        :r {:r {:o 8, :b 5}
            :b {:g 8}
            :o {:p 10}}} (johnson g2)

        {1 {1 {5 1}, 5 {3 2}, 3 {2 3, 6 3}, 2 {4 4}, 6 {10 4}}
         2 {2 {4 1}, 4 {10 2}}
         3 {3 {1 1, 2 1, 6 1}, 1 {5 2}, 2 {4 2}, 6 {10 2}}
         4 {4 {10 1}, 10 {2 2}}
         5 {5 {3 1}, 3 {1 2, 2 2, 6 2}, 2 {4 3}, 6 {10 3}}
         6 {6 {1 1, 10 1}, 1 {5 2}, 10 {2 2}, 2 {4 3}, 5 {3 3}}
         7 {4 {10 4}, 8 {11 2, 9 2}, 7 {8 1}, 9 {5 3, 3 3}, 11 {4 3, 2 3}, 3 {6 4, 1 4}}
         8 {4 {10 3}, 8 {11 1, 9 1}, 9 {7 2, 5 2, 3 2}, 11 {4 2, 2 2}, 3 {6 3, 1 3}}
         9 {8 {11 3}, 6 {10 3}, 7 {8 2}, 2 {4 3}, 9 {7 1, 5 1, 3 1}, 3 {6 2, 2 2, 1 2}}
         10 {10 {2 1}, 2 {4 2}}
         11 {11 {2 1, 4 1}, 4 {10 2}}} (johnson g13)

         false (johnson g11)

         {:e {:e {:b -7}
              :b {:d -2, :c -3}}
          :d {:d {:e 2}
              :e {:b -5}
              :b {:c -1}}
          :b {:b {:d 5, :c 4}
              :d {:e 7}}
          :c {}
          :a {:a {:b 3}
              :b {:d 8, :c 7}
              :d {:e 10}}} (johnson g12)))

(deftest johnson-restores-original-distances-test
  (is (= {:b 3 :c 7 :d 8 :e 10}
         (apply merge (vals (:a (johnson g12)))))))

(deftest all-pairs-shortest-paths-test
  (is (= {:p {:p {:o 2, :b 7}
              :o {:r 10}
              :b {:g 10}}
          :o {:o {:p 2, :r 8}
              :p {:b 9}
              :b {:g 12}}
          :g {:g {:b 3}
              :b {:r 8, :p 10}
              :p {:o 12}}
          :b {:b {:p 7, :g 3, :r 5}
              :p {:o 9}}
          :r {:r {:o 8, :b 5}
              :b {:g 8}
              :o {:p 10}}}
         (all-pairs-shortest-paths g2)))

  (let [vecs->sets #(clojure.walk/postwalk
                     (fn [x]
                       (if-not (map? x)
                         x
                         (reduce
                          (fn [m [k v]] (assoc m k (if (vector? v) (set v) v)))
                          {}
                          x)))
                     %)]
    (is (= (vecs->sets
            {1 {1 [5], 5 [3], 3 [6 2], 2 [4], 6 [10]}
             2 {2 [4], 4 [10]}
             3 {3 [1 6 2], 1 [5], 2 [4], 6 [10]}
             4 {4 [10], 10 [2]}
             5 {5 [3], 3 [1 6 2], 2 [4], 6 [10]}
             6 {6 [1 10], 1 [5], 10 [2], 5 [3], 2 [4]}
             7 {4 [10], 8 [11 9], 7 [8], 9 [3 5], 11 [4 2], 3 [1 6]}
             8 {4 [10], 8 [11 9], 9 [7 3 5], 11 [4 2], 3 [1 6]}
             9 {8 [11], 6 [10], 7 [8], 2 [4], 9 [7 3 5], 3 [1 6 2]}
             10 {10 [2], 2 [4]}
             11 {11 [4 2], 4 [10]}})
           (vecs->sets (all-pairs-shortest-paths g13))))))

(deftest connectivity-test
  (are [expected got] (= expected got)
       #{#{5 6 7 8} #{1 2 3 4} #{9}} (set (map set (connected-components
                                                    (add-nodes g8 9))))
       [#{:r :g :b :o :p}] (map set (connected-components g2))
       [#{1 2 3 4 5 6 8 7}] (map set (connected-components g9))
       true (connected? g6)
       false (connected? g7)
       true (connected? g9)
       #{#{2 3 4 1} #{8} #{7 5 6}} (set (map set (scc g9)))
       #{#{:b :e :a} #{:h :d :c} #{:f :g}} (set (map set (scc g10)))
       false (strongly-connected? g9)
       true (strongly-connected? (digraph g2))
       #{1 2 3 4 5 6 7 8} (set (nodes (connect g8)))
       #{:r :g :b :o :p} (set (nodes (connect g2)))))

(deftest other-stuff-test
  (are [expected got] (= expected got)
       false (dag? g2)
       true (dag? (digraph (bf-span g2)))
       true (dag? g5)
       [:a :c :h :j] (shortest-path g4 :a :j)
       [:a :e :j] (shortest-path (graph g4) :a :j)
       #{9 10} (set (loners (add-nodes g8 9 10)))
       ;; TODO: the rest
       ))

(deftest bellman-ford-test
  (are [expected graph start]
       (= expected (bellman-ford graph start))

       false g11 :a
       false g11 :b
       [{:e ##Inf
         :d ##Inf
         :b ##Inf
         :a ##Inf
         :c 0}{:c [:c]}] g11 :c
         false g11 :d
         false g11 :e
         [{:e 10,
           :d 8,
           :b 3,
           :c 7,
           :a 0}
          {:a [:a],
           :c [:a :b :c],
           :b [:a :b],
           :d [:a :b :d],
           :e [:a :b :d :e]}] g12 :a
           [{:e 7,
             :d 5,
             :c 4,
             :a ##Inf,
             :b 0}
            {:b [:b],
             :c [:b :c],
             :d [:b :d],
             :e [:b :d :e]}] g12 :b
             [{:e ##Inf
               :d ##Inf
               :b ##Inf
               :a ##Inf
               :c 0}
              {:c [:c]}] g12 :c
              [{:e 2,
                :b -5,
                :c -1,
                :a ##Inf,
                :d 0}
               {:d [:d],
                :c [:d :e :b :c],
                :b [:d :e :b],
                :e [:d :e]}] g12 :d
                [{:d -2,
                  :b -7,
                  :c -3,
                  :a ##Inf,
                  :e 0}
                 {:e [:e],
                  :c [:e :b :c],
                  :b [:e :b],
                  :d [:e :b :d]}] g12 :e))

(deftest bellman-ford-fly-graph-test
  (let [g (fly-graph :nodes [1 2 3]
                     :successors (fn [node] ({1 [2] 2 [3] 3 []} node))
                     :weight (constantly 1))]
    (is (= [{1 0 2 1 3 2}
            {1 [1] 2 [1 2] 3 [1 2 3]}]
           (bellman-ford g 1)))))

(deftest bipartite-test
  (are [expected got] (= expected got)
    nil (bipartite-color g1)
    true (bipartite? g6)
    true (bipartite? g8)
    false (bipartite? g1))
  (are [options result] (contains? options result)
    #{{0 1, 1 0, 5 0, 2 1, 3 1, 4 0}} (bipartite-color g6)
    #{{1 1, 2 0, 3 0, 4 0, 5 0, 6 1, 7 1, 8 1}
      {1 1, 2 0, 3 0, 4 0, 5 1, 6 0, 7 0, 8 0}} (bipartite-color g8)
    #{#{#{2 3 4 5} #{1 6 7 8}}
      #{#{2 3 4 6 7 8} #{1 5}}} (set (bipartite-sets g8))))

(deftest bipartite-color-digraph-sinks-test
  ;; A 4-cycle digraph has sink nodes 1 and 3. Its undirected projection is
  ;; bipartite. Successors-only coloring missed predecessor edges and returned
  ;; nil (#118).
  (let [di (digraph [2 1] [2 3] [4 3] [4 1])
        coloring (bipartite-color di)]
    (is (some? coloring))
    (is (every? (fn [[u v]] (not= (coloring u) (coloring v))) (edges di)))
    (is (true? (bipartite? di)))))

(deftest coloring?-test
  (are [expected got] (= expected got)
       true (coloring? g1 {1 0, 2 1, 3 2, 4 0, 5 2, 6 1})
       false (coloring? g1 {1 0, 2 1, 3 2, 4 0, 5 1, 6 1})
       true (coloring? g2 {:r 0, :g 1, :b 2, :p 0, :o 1})
       true (coloring? g5 {:a 0, :b 1, :c 2, :d 0, :e 1, :f 0, :g 1})
       false (coloring? g5 {:a 0 :b 1 :c 2 :d 0 :e 1 :f 0 :g nil})))

(deftest greedy-coloring-test
  (are [expected got] (= expected got)
       true (coloring? g1 (greedy-coloring g1))
       true (coloring? g2 (greedy-coloring g2))
       true (coloring? g4 (greedy-coloring g4))
       true (coloring? g5 (greedy-coloring g5))
       true (coloring? g6 (greedy-coloring g6))
       true (coloring? g13 (greedy-coloring g13))
       ; expected colors are 0, 1, and 2
       2 (apply max (vals (greedy-coloring triangle)))))

(deftest scc-test
  (are [expected got] (= expected got)
       #{#{2 4 10} #{1 3 5 6} #{11} #{7 8 9}} (set (map set (scc g13)))))

(deftest prim-mst-edges-weighted-test
  ; The platform can give edges in different orders because priority-map
  ; implementations differ. Test edges as sets.
  (letfn [(edge-set [edge]
            (into [(set (take 2 edge))] (drop 2 edge)))
          (edge-sets [edges] (set (map edge-set edges)))]
    (are [expected got] (= (edge-sets expected) (edge-sets got))
      [[:e :a 1] [:a :b 3] [:b :c 5] [:c :d 2]] (prim-mst-edges mst_wt_g1)
      [[:b :a 1]] (prim-mst-edges mst_wt_g4)
      [[:c :a 2] [:c :b 2]] (prim-mst-edges mst_wt_g5)
      [[:b :a 4] [:c :b 8] [:c :i 2] [:c :f 4] [:f :g 2]
       [:g :h 1] [:d :c 7] [:e :d 9]]  (prim-mst-edges mst_wt_g6))
    
    (are [solutions result] (contains? solutions result)
      #{(edge-sets [[:d :a 1] [:b :d 2] [:c :b 1] [:e :f 1]])
        (edge-sets [[:d :a 1] [:a :b 2] [:c :b 1] [:e :f 1]])}
      (edge-sets (prim-mst-edges mst_wt_g2))

      
      #{(edge-sets [[:c :a] [:d :b] [:c :d]])
        (edge-sets [[:a :b] [:a :c] [:a :d]])}
      (edge-sets (prim-mst-edges mst_unweighted_g3)))))

(deftest prim-mst-test
  (are [expected got] (= expected got)
       [#{:a :b :d :e :f :g :h} (set [[:a :b] [:b :d] [:b :a] [:f :e] [:d :b] [:e :f]])]
       (let [mst (prim-mst mst_wt_g7)]
         [(nodes mst) (set (edges mst))])

       [#{:a :b :c} (set [[:a :c] [:c :b] [:c :a] [:b :c]])]
       (let [mst (prim-mst mst_wt_g5)]
         [(nodes mst) (set (edges mst))])))


;;;; Graphs for A* path.
(def astar-simple-path-g1 (graph [:a :b]
                            [:b :c]
                            [:c :d]
                            [:d :e]))

;; Graph with an unreachable node.
(def astar-with-unreachable-target-g2 (graph [:a :b]
                                              [:b :c]
                                              [:d :e]))

(def astar-with-cycle-g3 (digraph [:a :b]
                             [:b :c]
                             [:c :d]
                             [:d :a]))

(def astar-weighted-graph-g4 (weighted-digraph [:a :b 10]
                                               [:b :c 20]
                                               [:c :d 5]
                                               [:a :e 10]
                                               [:e :d 100]))

(deftest astar-path-test
  (are [expected got](= expected got)
       {:e :d :d :c :c :b :b :a :a nil}
       (astar-path astar-simple-path-g1 :a :e (constantly 0))
       {:a nil :b :a :c :b}
       (astar-path astar-with-cycle-g3 :a :c (constantly 0))
       {:a nil :b :a :c :b :d :c}
       (astar-path astar-with-cycle-g3 :a :d (constantly 0))
       {:a nil :b :a :c :b :d :c}
       (astar-path astar-weighted-graph-g4 :a :d (constantly 0))
       ;; All test graphs for Dijkstra should also work for A*.
       {:a nil, :c :a, :h :c, :j :h} (astar-path g4 :a :j nil)
       {:r nil, :o :r, :p :o} (astar-path g2 :r :p nil))
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (astar-path astar-with-unreachable-target-g2 :a :e nil))))

(deftest astar-reopens-nodes-for-inconsistent-heuristics-test
  (let [g (weighted-digraph [:s :a 2]
                            [:s :b 1]
                            [:b :a 0.5]
                            [:a :t 2])
        heuristic {:a 0 :b 2.5 :t 0}
        heur (fn [node _target] (heuristic node))
        astar-predecessors (astar-path g :s :t heur)
        [dijkstra-path dijkstra-distance] (dijkstra-path-dist g :s :t)]
    (is (= [:s :b :a :t] dijkstra-path))
    (is (= dijkstra-distance (astar-dist g :s :t heur)))
    (is (= {:s nil :b :s :a :b :t :a} astar-predecessors))))

(deftest astar-dist-test
  (are [expected got](= expected got)
       4
       (astar-dist astar-simple-path-g1 :a :e (constantly 0))
       2
       (astar-dist astar-with-cycle-g3 :a :c (constantly 0))
       3
       (astar-dist astar-with-cycle-g3 :a :d (constantly 0))
       35
       (astar-dist astar-weighted-graph-g4 :a :d (constantly 0))
       )
  )

(deftest astar-visit-test
  (let [g (graph [0 1] [1 2] [2 3] [3 4])
        i (atom 0)]
    (astar-path g 2 4 (fn [x y] (swap! i inc) (if (> x y) (- x y) (- y x))))
    (is (= 3 @i) "This implementation of A* is incorrect. It is not optimal.")))

(def degeneracy-g1 (graph {:a [:b]
                           :b [:c :d]}))

(def degeneracy-g2 (graph {:a [:b]
                           :b [:c :d :e :f]
                           :d [:e :f]
                           :e [:f]}))

(deftest degeneracy-ordering-test
  (let [ns (degeneracy-ordering degeneracy-g1)]
    (is (= #{:a :c :b :d} (set ns)))
    (is (contains? (set (drop 2 ns)) :b)))

  (let [ns (degeneracy-ordering degeneracy-g2)]
    (is (= #{:a :c :b :d :e :f} (set ns)))
    (is (= #{:a :c} (set (take 2 ns))))
    (is (contains? (set (drop 2 ns)) :b))))

;; Graph with 4 maximal cliques: [:a :b :c], [:c :d], [:d :e :f :g], [:d :h].
(def maximal-cliques-g1 (graph {:a [:b :c]
                                :b [:c]
                                :c [:d]
                                :d [:e :f :g :h]
                                :e [:f :g]
                                :f [:g]}))

;; Graph with 3 maximal cliques: #{:a :b :c} #{:b :d :e} #{:e :f}
(def maximal-cliques-g2 (weighted-graph [:a :b 1]
                                        [:a :c 1]
                                        [:b :c 1]
                                        [:b :d 1]
                                        [:d :e 1]
                                        [:b :e 1]
                                        [:e :f 1]))

(deftest maximal-cliques-test
  (are [expected got](= expected got)
       #{#{:a :b :c} #{:c :d} #{:d :e :f :g} #{:d :h}}
       (set (maximal-cliques maximal-cliques-g1))
       #{#{:a :b :c} #{:b :d :e} #{:e :f}}
       (set (maximal-cliques maximal-cliques-g2))))

(deftest maximal-cliques-digraph-throws
  ;; Bron-Kerbosch only works with undirected graphs. A digraph returned incorrect
  ;; results without an error (#128). It must throw instead.
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs cljs.core/ExceptionInfo)
               (maximal-cliques (digraph [:a :b] [:b :c])))))


(def subgraph-g6 (graph [0 1] [1 2] [1 3]))
(def subgraph-g7 (digraph [1 2] [2 3] [3 1]))

(deftest subgraph-test
  (are [expected got] (= expected got)
       true (subgraph? subgraph-g6 g6)
       false (subgraph? (add-edges subgraph-g6 [0 3])
                        g6)
       true (subgraph? subgraph-g7 g7)
       false (subgraph? (add-nodes subgraph-g7 0)
                        g7)
       false (subgraph? (digraph [2 1] [2 3] [3 1])
                        g7)))

(deftest eql-test
  (are [expected got] (= expected got)
    true (eql? (graph) (graph))
    true (eql? (digraph) (digraph))

    true (eql? g6 (graph g6))
    true (eql? g7 (digraph g7))

    false (eql? (digraph) (graph))
    false (eql? (graph) (digraph))
    false (eql? g6 (graph 1 2))
    false (eql? g7 (digraph 1 2))
    false (eql? (digraph [1 2]) (graph [1 2]))
    false (eql? g7 g6)))

(deftest isomorphism-test
  (are [expected got] (= expected got)
    true (isomorphism? (graph) (graph) identity)
    true (isomorphism? g6 g6 identity)
    true (isomorphism? g7 g7 identity)
    true (isomorphism? (graph) (graph) identity)
    true (isomorphism? g6 (mapped-by inc g6) inc)
    true (isomorphism? g7 (mapped-by inc g7) inc)

    false (isomorphism? g7 (mapped-by inc g7) dec)
    false (isomorphism? (digraph) (graph) identity)
    false(isomorphism? (digraph [1 2]) (graph [1 2]) identity)))

(deftest digraph-all-cycles-test
  (testing "simple cycles in directed graphs"
    (is (= (sort (map (comp vec sort) (digraph-all-cycles directed-graph1)))
           (sort (map (comp vec sort) [[1 2 3] [1 2 4 3]]))))
    (is (= (sort (map (comp vec sort) (digraph-all-cycles directed-graph2)))
           (sort (map (comp vec sort) [[1 5 2 3] [1 2 3] [4 5 2 3 6]
                                       [4 5 2 3] [3 2] [9 8]]))))
    (is (= (sort (map (comp vec sort) (digraph-all-cycles directed-graph3)))
           (sort (map (comp vec sort) [[7 1 5 6 2 4 3] [7 1 5 6 2 4] [7 1 5 6 2]
                                       [7 1 5 6 4 3 2] [7 1 5 6 4 3] [7 1 5 6 4]
                                       [7 1 5] [7 1 2 5 6 4 3] [7 1 2 5 6 4]
                                       [7 1 2 5] [7 1 2 4 3] [7 1 2 4] [7 1 2]
                                       [1 5 6 2 4] [1 5 6 4] [1 5 6] [1 2 5 6 4]
                                       [1 2 5 6] [1 2 4] [4 3 2 5 6] [4 3 2]
                                       [6 2 5]])))))
  (testing "no cycles present"
    (is (= (digraph-all-cycles directed-graph4) '())))
  (testing "not a directed graph"
    (is (= (digraph-all-cycles g6) :loom.alg/not-a-directed-graph))))

(deftest clustering-coefficient-test
  ;; triangle: every node's neighbors are fully connected -> 1
  (let [tri (graph [:a :b] [:b :c] [:c :a])]
    (is (= 1 (clustering-coefficient tri :a)))
    (is (= 1 (clustering-coefficient tri))))
  ;; path a-b-c: b's neighbors a,c are not connected -> 0; average over a,b,c -> 0
  (let [path (graph [:a :b] [:b :c])]
    (is (= 0 (clustering-coefficient path :b)))
    (is (= 0 (clustering-coefficient path))))
  ;; fewer than two neighbors -> 0
  (is (= 0 (clustering-coefficient (graph [:a :b]) :a))))

(deftest clustering-and-density-edge-cases-test
  (let [weighted-triangle (weighted-graph [:a :b 1] [:b :c 1] [:c :a 1])]
    (is (= 1 (clustering-coefficient weighted-triangle :a))))
  (is (= 0 (clustering-coefficient (graph))))
  (is (= 0 (density (graph))))
  (is (= 0 (density (graph :a)))))

(deftest centrality-algorithms-test
  (let [star (graph [:c :a] [:c :b] [:c :d])
        path (graph [:a :b] [:b :c] [:c :d])
        hits-graph (digraph [:h :a] [:h :b] [:h :c])
        pr (pagerank star :iterations 100 :tol 1e-12)]
    (is (every? #(< (Math/abs (- (get (degree-centrality star) (first %)) (second %))) 1e-12)
                (map vector [:c :a :b :d] [1.0 (/ 1.0 3.0) (/ 1.0 3.0) (/ 1.0 3.0)])))
    (is (= 1 (get (degree-centrality star) :c)))
    (is (> (pr :c) (pr :a)))
    (is (< (Math/abs (- 1.0 (reduce + (vals pr)))) 1e-9))
    (is (= 3 (eccentricity path :a)))
    (is (= {:a 3 :b 2 :c 2 :d 3} (eccentricity path)))
    (is (= 2 (radius path)))
    (is (= 3 (diameter path)))
    (is (= (/ 2 3.0) (get (betweenness-centrality path) :b)))
    (is (= 1/2 (get (closeness-centrality path) :a)))
    (is (= (get (eigenvector-centrality star) :a)
           (get (eigenvector-centrality star) :b)))
    (is (= :c (key (apply max-key val (eigenvector-centrality star)))))
    (is (= :h (key (apply max-key val (:hubs (hits hits-graph))))))
    (is (= (set [:a :b :c :h])
           (set (keys (:authorities (hits hits-graph))))))))

(deftest structural-algorithms-test
  (let [g (graph [:a :b] [:b :c] [:c :a] [:b :d] [:d :e])
        triangle (graph [:a :b] [:b :c] [:c :a])]
    (is (= #{:b :d} (articulation-points g)))
    (is (= #{[:b :d] [:d :e]} (set (bridges g))))
    (is (= #{#{:a :b :c} #{:b :d} #{:d :e}}
           (set (biconnected-components g))))
    (is (= #{:a :b :c} (set (nodes (k-core g 2)))))
    (is (= 2 (get (k-core triangle) :a)))
    (is (= 2 (get (k-core triangle) :b)))))

(deftest k-core-prunes-multigraph-attrs-test
  (let [g (multigraph [:a :b :ab 1]
                      [:b :c :bc 1]
                      [:c :a :ca 1]
                      [:a :leaf :leaf-edge 1])
        leaf-edge (first (filter #(= :leaf-edge (loom.graph/edge-key %))
                                 (edges-with-ids g)))
        core (k-core (-> g
                         (attr/add-attr :leaf :color :red)
                         (attr/add-attr leaf-edge :kind :twig))
                     2)]
    (is (= #{:a :b :c} (set (nodes core))))
    (is (nil? (get-in core [:attrs :leaf])))
    (is (nil? (get-in core [:attrs :a :loom.attr/edge-attrs :leaf-edge])))
    (is (nil? (attr/attr core leaf-edge :kind)))))

(deftest deep-structural-analysis-test
  (let [node-count 20000
        path-edges (mapv (fn [n] [n (inc n)]) (range (dec node-count)))
        path (apply graph path-edges)
        directed-path (apply digraph path-edges)]
    (is (= (- node-count 2) (count (articulation-points path))))
    (is (= (dec node-count) (count (bridges path))))
    (is (= '() (digraph-all-cycles directed-path)))))

(ns ^{:doc "Graph algorithms. Any graph record/type that satisfies the
Graph, Digraph, or WeightedGraph protocols (as appropriate per algorithm)
can use these functions."
      :author "Justin Kramer"}
  loom.alg
  (:require [loom.alg-generic :as gen :refer [trace-path preds->span]]
            [loom.flow :as flow]
            [loom.graph
             :refer [add-nodes add-edges nodes edges successors weight predecessors
                     out-degree in-degree weighted? directed? graph digraph transpose]
             :as graph]
            #?(:clj [clojure.data.priority-map :as pm]
               :cljs [tailrecursion.priority-map :as pm])
            [clojure.set :as clj.set]))

#?(:clj (set! *warn-on-reflection* true))

;;;
;;; Convenience wrappers for loom.alg-generic functions
;;;
(defn- traverse-all
  [nodes traverse]
  (persistent! (second
   (reduce
    (fn [[seen trav] n]
      (if (seen n)
        [seen trav]
        (let [ctrav (traverse n :seen seen)]
          [(into seen ctrav) (reduce conj! trav ctrav)])))
    [#{} (transient [])]
    nodes))))

(defn- validate-node! [g node operation]
  (when-not (graph/has-node? g node)
    (throw (ex-info (str "Node not found: " node)
                    {:type :loom.alg/missing-node
                     :node node
                     :operation operation}))))

(defn- validate-non-negative-weights! [g algorithm]
  (when (and (weighted? g)
             (some (fn [edge] (neg? (graph/weight g edge))) (edges g)))
    (throw (ex-info (str algorithm " requires non-negative edge weights")
                    {:type :loom.alg/negative-weight
                     :algorithm algorithm}))))

(defn pre-traverse
  "Traverses graph g depth-first from start. Returns a lazy seq of nodes.
  When no starting node is provided, traverses the entire graph, connected
  or not."
  ([g]
     (traverse-all (nodes g) (partial gen/pre-traverse (graph/successors g))))
  ([g start]
     (validate-node! g start :pre-traverse)
     (gen/pre-traverse (graph/successors g) start)))

(defn pre-span
  "Returns a depth-first spanning tree of the form {node [successors]}"
  ([g]
     (second
      (reduce
       (fn [[seen span] n]
         (if (seen n)
           [seen span]
           (let [[cspan seen] (gen/pre-span
                               (graph/successors g)
                               n :seen seen :return-seen true)]
             [seen (merge span {n []} cspan)])))
       [#{} {}]
       (nodes g))))
  ([g start]
     (validate-node! g start :pre-span)
     (gen/pre-span (graph/successors g) start)))

(defn post-traverse
  "Traverses graph g depth-first, post-order from start. Returns a
  vector of the nodes."
  ([g]
     (traverse-all (nodes g) (partial gen/post-traverse (graph/successors g))))
  ([g start & opts]
     (validate-node! g start :post-traverse)
     (apply gen/post-traverse (graph/successors g) start opts)))

(defn topsort
  "Topological sort of a directed acyclic graph (DAG). Returns nil if
  g contains any cycles."
  ([g]
     (loop [seen #{}
            result ()
            [n & ns] (seq (nodes g))]
       (if-not n
         result
         (if (seen n)
           (recur seen result ns)
           (when-let [cresult (gen/topsort-component
                               (graph/successors g) n seen seen)]
             (recur (into seen cresult) (concat cresult result) ns))))))
  ([g start]
     (validate-node! g start :topsort)
     (gen/topsort-component (graph/successors g) start)))

(defn bf-traverse
  "Traverses graph g breadth-first from start. When option :f is provided,
  returns a lazy seq of (f node predecessor-map depth) for each node traversed.
  Otherwise, returns a lazy seq of the nodes. When option :when is provided,
  filters successors with (f neighbor predecessor depth)."
  ([g]
     (first
      (reduce
       (fn [[cc predmap] n]
         (if (contains? predmap n)
           [cc predmap]
           (reduce
            (fn [[cc _] [n pm _]]
              [(conj cc n) pm])
            [cc predmap]
            (gen/bf-traverse (graph/successors g) n :f vector :seen predmap))))
       [[] {}]
       (nodes g))))
  ([g start]
     (validate-node! g start :bf-traverse)
     (gen/bf-traverse (graph/successors g) start))
  ([g start & opts]
     (apply gen/bf-traverse (graph/successors g) start opts)))

(defn bf-span
  "Returns a breadth-first spanning tree of the form {node [successors]}"
  ([g]
     (preds->span
      (reduce
       (fn [predmap n]
         (if (contains? predmap n)
           predmap
           (last (gen/bf-traverse (graph/successors g) n
                                  :f (fn [_ pm _] pm)
                                  :seen predmap))))
       {}
       (nodes g))))
  ([g start]
     (gen/bf-span (graph/successors g) start)))

(defn bf-path
  "Returns a path from start to end with the fewest hops (i.e. irrespective
  of edge weights)"
  [g start end & opts]
  (validate-node! g start :bf-path)
  (validate-node! g end :bf-path)
  (apply gen/bf-path (graph/successors g) start end opts))

(defn bf-path-bi
  "Using a bidirectional breadth-first search, finds a path from start to
  end with the fewest hops (i.e. irrespective of edge weights). Can be much
  faster than a unidirectional search on certain types of graphs"
  [g start end]
  (if (directed? g)
    (gen/bf-path-bi (graph/successors g) (predecessors g) start end)
    (gen/bf-path-bi (graph/successors g) (graph/successors g) start end)))

(defn dijkstra-traverse
  "Returns a lazy-seq of [current-node state] where state is a map in
  the format {node [distance predecessor]}. When f is provided,
  returns a lazy-seq of (f node state) for each node"
  ([g]
     (validate-non-negative-weights! g :dijkstra)
     (gen/dijkstra-traverse
      (graph/successors g) (graph/weight g) (first (nodes g))))
  ([g start]
     (validate-node! g start :dijkstra-traverse)
     (validate-non-negative-weights! g :dijkstra)
     (gen/dijkstra-traverse (graph/successors g) (graph/weight g) start vector))
  ([g start f]
     (validate-node! g start :dijkstra-traverse)
     (validate-non-negative-weights! g :dijkstra)
     (gen/dijkstra-traverse (graph/successors g) (graph/weight g) start f)))

(defn dijkstra-span
  "Finds all shortest distances from start. Returns a map in the
  format {node {successor distance}}"
  ([g]
     (validate-non-negative-weights! g :dijkstra)
     (gen/dijkstra-span
      (graph/successors g) (graph/weight g) (first (nodes g))))
  ([g start]
     (validate-node! g start :dijkstra-span)
     (validate-non-negative-weights! g :dijkstra)
     (gen/dijkstra-span (graph/successors g) (graph/weight g) start)))

(defn dijkstra-path-dist
  "Finds the shortest path from start to end. Returns a vector:
  [path distance]"
  [g start end]
  (validate-node! g start :dijkstra-path)
  (validate-node! g end :dijkstra-path)
  (validate-non-negative-weights! g :dijkstra)
  (gen/dijkstra-path-dist (graph/successors g) (graph/weight g) start end))

(defn dijkstra-path
  "Finds the shortest path from start to end"
  [g start end]
  (first (dijkstra-path-dist g start end)))

(defn- can-relax-edge?
  "Tests for whether we can improve the shortest path to v found so far
   by going through u."
  [[u v] weight costs]
  (let [vd (get costs v)
        ud (get costs u)
        sum (+ ud weight)]
    (> vd sum)))

(defn- relax-edge
  "If there's a shorter path from s to v via u,
    update our map of estimated path costs and
   map of paths from source to vertex v"
  [[u v :as edge] weight [costs paths :as estimates]]
  (let [ud (get costs u)
        sum (+ ud weight)]
    (if (can-relax-edge? edge weight costs)
      [(assoc costs v sum) (assoc paths v u)]
      estimates)))

(defn- relax-edges
  "Performs edge relaxation on all edges in weighted directed graph"
  [g _ estimates]
  (->> (edges g)
       (reduce (fn [estimates [u v :as edge]]
                 (relax-edge edge (graph/weight g u v) estimates))
               estimates)))

(defn- init-estimates
  "Initializes path cost estimates and paths from source to all vertices,
   for Bellman-Ford algorithm"
  [graph start]
  (let [nodes (disj (set (nodes graph)) start)
        path-costs {start 0}
        paths {start nil}
        infinities (repeat #?(:clj Double/POSITIVE_INFINITY
                              :cljs js/Infinity))
        nils (repeat nil)
        init-costs (interleave nodes infinities)
        init-paths (interleave nodes nils)]
    [(apply assoc path-costs init-costs)
     (apply assoc paths init-paths)]))


;;;
;;; Graph algorithms
;;;

(defn bellman-ford
  "Given a weighted, directed graph G = (V, E) with source start,
   the Bellman-Ford algorithm produces map of single source shortest
   paths and their costs if no negative-weight cycle that is reachable
   from the source exists, and false otherwise, indicating that no
   solution exists."
  [g start]
  (validate-node! g start :bellman-ford)
  (let [initial-estimates (init-estimates g start)
        ;;relax-edges is calculated for all edges V-1 times
        [costs paths] (reduce (fn [estimates _]
                                (relax-edges g start estimates))
                              initial-estimates
                              (-> g nodes count dec range))
        edges (edges g)]
    (if (some
         (fn [[u v :as edge]]
           (can-relax-edge? edge (graph/weight g u v) costs))
         edges)
      false
      [costs
       (->> (keys paths)
            ;;remove vertices that are unreachable from source
            (remove #(= #?(:clj Double/POSITIVE_INFINITY
                           :cljs js/Infinity)
                        (get costs %)))
            (reduce
             (fn [final-paths v]
               (assoc final-paths v
                      ;; follows the parent pointers
                      ;; to construct path from source to node v
                      (loop [node v
                             path ()]
                        (if node
                          (recur (get paths node) (cons node path))
                          path))))
             {}))])))

(defn dag?
  "Returns true if g is a directed acyclic graph"
  [g]
  (boolean (topsort g)))

(defn shortest-path
  "Finds the shortest path from start to end in graph g, using Dijkstra's
  algorithm if the graph is weighted, breadth-first search otherwise."
  [g start end]
  (if (weighted? g)
    (dijkstra-path g start end)
    (bf-path g start end)))

(defn longest-shortest-path
  "Finds the longest shortest path beginning at start, using Dijkstra's
  algorithm if the graph is weighted, breadth-first search otherwise."
  [g start]
  (reverse
   (if (weighted? g)
     (reduce
      (fn [path1 [n state]]
        (let [path2 (trace-path (comp second state) n)]
          (if (< (count path1) (count path2)) path2 path1)))
      [start]
      (dijkstra-traverse g start vector))
     (reduce
      (fn [path1 [n predmap _]]
        (let [path2 (trace-path predmap n)]
          (if (< (count path1) (count path2)) path2 path1)))
      [start]
      (bf-traverse g start :f vector)))))

(defn simple-paths
  "Finds all simple paths from start node to end node. Paths are represented as
  a collection of nodes in traversal order. With :max-depth, only returns paths
  with fewer than max-depth nodes."
  [g start end & {:keys [max-depth] :or {max-depth nil}}]
  (validate-node! g start :simple-paths)
  (validate-node! g end :simple-paths)
  (if (= start end)
    [[start]]
    (letfn [(create-path-map []
              {:members #{}
               :path []})
            (add-path-node [pm n]
              (-> pm
                  (update :members conj n)
                  (update :path conj n)))]
      (loop [q #?(:clj clojure.lang.PersistentQueue/EMPTY
                  :cljs cljs.core/PersistentQueue.EMPTY)
             completed-paths []
             p (-> (create-path-map)
                   (add-path-node start))]
        (let [p-last (-> p :path peek)
              unseen-succs (filter (comp not (:members p)) (successors g p-last))
              succ-ps (map (partial add-path-node p) unseen-succs)
              updated-q (reduce conj q (filter (fn [succ-p]
                                                 ;; keep extending paths that have
                                                 ;; not yet reached end and are
                                                 ;; under max-depth (when given)
                                                 (and (-> succ-p :path peek (= end) not)
                                                      (if (nil? max-depth)
                                                        true
                                                        (-> succ-p :path count (< max-depth)))))
                                               succ-ps))
              updated-completed-paths (reduce conj
                                              completed-paths
                                              (->> succ-ps
                                                   (filter (comp (partial = end) peek :path))
                                                   (map :path)))]
          (if (-> updated-q empty? not)
            (recur (pop updated-q)
                   updated-completed-paths
                   (peek updated-q))
            updated-completed-paths))))))

(defn- bellman-ford-transform
  "Helper function for Johnson's algorithm. Uses Bellman-Ford to remove negative weights."
  [wg]
  (let [q (first (drop-while (partial graph/has-node? wg) (repeatedly gensym)))
        es (for [v (graph/nodes wg)] [q v 0])
        bf-results (bellman-ford (graph/add-edges* wg es) q)]
    (if bf-results
      (let [[dist-q _] bf-results
            new-es (map (juxt first second (fn [[u v]]
                                             (+ (weight wg u v) (- (dist-q u)
                                                                   (dist-q v)))))
                        (graph/edges wg))]
        [(graph/add-edges* wg new-es) dist-q])
      false)))

(defn johnson
  "Finds all-pairs shortest paths using Bellman-Ford to remove any negative edges before
  using Dijkstra's algorithm to find the shortest paths from each vertex to every other.
  This algorithm is efficient for sparse graphs.

  If the graph is unweighted, a default weight of 1 will be used. Note that it is more efficient
  to use breadth-first spans for a graph with a uniform edge weight rather than Dijkstra's algorithm.
  Most callers should use shortest-paths and allow the most efficient implementation be selected
  for the graph."
  [g]
  (let [transformed (if (and (weighted? g) (some (partial > 0) (map (graph/weight g) (graph/edges g))))
                      (bellman-ford-transform g)
                      [g nil])]
    (if (false? transformed)
      false
      (let [[g potentials] transformed
            dist (if (weighted? g)
                   (weight g)
                   (fn [u v] (when (graph/has-edge? g u v) 1)))]
        (reduce (fn [acc node]
                  (let [span (gen/dijkstra-span (successors g) dist node)
                        span (if potentials
                               (reduce-kv
                                (fn [corrected parent children]
                                  (assoc corrected parent
                                         (reduce-kv
                                          (fn [children target distance]
                                            (assoc children target
                                                   (+ distance
                                                      (- (potentials node))
                                                      (potentials target))))
                                          {}
                                          children)))
                                {}
                                span)
                               span)]
                    (assoc acc node span)))
                {}
                (nodes g))))))

(defn bf-all-pairs-shortest-paths
  "Uses bf-span on each node in the graph."
  [g]
  (reduce (fn [spans node]
            (assoc spans node (bf-span g node)))
          {}
          (nodes g)))

(defn all-pairs-shortest-paths
  "Finds all-pairs shortest paths in a graph. Uses Johnson's algorithm for weighted graphs
  which is efficient for sparse graphs. Breadth-first spans are used for unweighted graphs."
  [g]
  (if (weighted? g)
    (johnson g)
    (bf-all-pairs-shortest-paths g)))

(defn connected-components
  "Returns the connected components of graph g as a vector of vectors. If g
  is directed, returns the weakly-connected components."
  [g]
  (let [nb (if-not (directed? g) (graph/successors g)
                   #(concat (graph/successors g %) (predecessors g %)))]
    (first
     (reduce
      (fn [[cc predmap] n]
        (if (contains? predmap n)
          [cc predmap]
          (let [[c pm] (reduce
                        (fn [[c _] [n pm _]]
                          [(conj c n) pm])
                        [[] nil]
                        (gen/bf-traverse nb n :f vector :seen predmap))]
            [(conj cc c) pm])))
      [[] {}]
      (nodes g)))))

(defn connected?
  "Returns true if g is connected"
  [g]
  (== (count (first (connected-components g))) (count (nodes g))))

(defn scc
  "Returns the strongly-connected components of directed graph g as a vector of
  vectors. Uses Kosaraju's algorithm."
  [g]
  (let [gt (transpose g)]
    (loop [stack (reverse (post-traverse g))
           seen #{}
           cc (transient [])]
      (if (empty? stack)
        (persistent! cc)
        (if (seen (first stack))
          (recur (rest stack) seen cc)
          (let [[c seen] (post-traverse gt (first stack)
                                      :seen seen :return-seen true)]
            (recur (rest stack)
                 seen
                 (conj! cc c))))
        ))))

(defn strongly-connected?
  [g]
  (== (count (first (scc g))) (count (nodes g))))

(defn connect
  "Returns graph g with all connected components connected to each other"
  [g]
  (reduce add-edges g (partition 2 1 (map first (connected-components g)))))

(defn density
  "Return the density of graph g"
  [g & {:keys [loops] :or {loops false}}]
  (let [order (count (nodes g))
        possible-edges (* order (if loops order (dec order)))]
    (if (zero? possible-edges)
      0
      (/ (count (edges g)) possible-edges))))

(defn loners
  "Returns nodes with no connections to other nodes (i.e., isolated nodes)"
  [g]
  (let [degree-total (if (directed? g)
                       #(+ (in-degree g %) (out-degree g %))
                       #(out-degree g %))]
    (filter (comp zero? degree-total) (nodes g))))

(defn distinct-edges
  "Returns the distinct edges of g. Only useful for undirected graphs"
  [g]
  (if (directed? g)
    (edges g)
    (second
     (reduce
      (fn [[seen es] e]
        (let [eset (set (take 2 e))]
          (if (seen eset)
            [seen es]
            [(conj seen eset)
             (conj es e)])))
      [#{} []]
      (edges g)))))

(defn bipartite-color
  "Attempts a two-coloring of graph g. When successful, returns a map of
  nodes to colors (1 or 0). Otherwise, returns nil."
  [g]
  (letfn [(color-component [coloring start]
            (loop [coloring (assoc coloring start 1)
                   queue (conj #?(:clj clojure.lang.PersistentQueue/EMPTY
                                  :cljs cljs.core/PersistentQueue.EMPTY) start)]
              (if (empty? queue)
                coloring
                (let [v (peek queue)
                      color (- 1 (coloring v))
                      nbrs (graph/neighbors g v)]
                  ;; TODO: could be better
                  (if (some #(and (coloring %) (= (coloring v) (coloring %)))
                            nbrs)
                    nil ; graph is not bipartite
                    (let [nbrs (remove coloring nbrs)]
                      (recur (into coloring (for [nbr nbrs] [nbr color]))
                             (into (pop queue) nbrs))))))))]
    (loop [[node & nodes] (seq (nodes g))
           coloring {}]
      (when coloring
        (if (nil? node)
          coloring
          (if (coloring node)
            (recur nodes coloring)
            (recur nodes (color-component coloring node))))))))

(defn bipartite?
  "Returns true if g is bipartite"
  [g]
  (boolean (bipartite-color g)))

(defn bipartite-sets
  "Returns two sets of nodes, one for each color of the bipartite coloring,
  or nil if g is not bipartite"
  [g]
  (when-let [coloring (bipartite-color g)]
    (reduce
     (fn [[s1 s2] [node color]]
       (if (zero? color)
         [(conj s1 node) s2]
         [s1 (conj s2 node)]))
     [#{} #{}]
     coloring)))

(defn- neighbor-colors
  "Given a putative coloring of a graph, returns the colors of all the
  neighbors of a given node."
  [g node coloring]
  (let [successors (graph/successors g node)
        neighbors (if-not (directed? g)
                    successors
                    (concat successors
                            (graph/predecessors g node)))]
    (set (remove nil?
                 (map #(get coloring %)
                      neighbors)))))

(defn coloring?
  "Returns true if a map of nodes to colors is a proper coloring of a graph."
  [g coloring]
  (letfn [(different-colors? [node]
            (not (contains? (neighbor-colors g node coloring)
                            (coloring node))))]
    (and (every? different-colors? (nodes g))
         (every? (complement nil?) (map #(get coloring %)
                                        (nodes g))))))

(defn greedy-coloring
  "Greedily color the vertices of a graph using the first-fit heuristic.
  Returns a map of nodes to colors (0, 1, ...)."
  [g]
  (loop [node-seq (bf-traverse g)
         coloring {}
         colors #{}]
    (if (empty? node-seq)
      coloring
      (let [node (first node-seq)
            possible-colors (clj.set/difference colors
                                                (neighbor-colors g
                                                                 node
                                                                 coloring))
            node-color (if (empty? possible-colors)
                         (count colors)
                         (apply min possible-colors))]
        (recur (rest node-seq)
               (conj coloring [node node-color])
               (conj colors node-color))))))

(defn max-flow
  "Returns [flow-map flow-value], where flow-map is a weighted adjacency map
   representing the maximum flow.  The argument should be a weighted digraph,
   where the edge weights are flow capacities.  Source and sink are the vertices
   representing the flow source and sink vertices.  Optionally, pass in
     :method :algorithm to use.  Currently, the only option is :edmonds-karp ."
  [g source sink & {:keys [method] :or {method :edmonds-karp}}]
  (let [method-set #{:edmonds-karp}
        _ (when-not (weighted? g)
            (throw (ex-info "Maximum flow requires a weighted graph"
                            {:type :loom.flow/malformed-constraint
                             :constraint :weighted-graph})))
        _ (when-not (graph/has-node? g source)
            (throw (ex-info (str "Flow source node not found: " source)
                            {:type :loom.flow/missing-node
                             :node source
                             :role :source})))
        _ (when-not (graph/has-node? g sink)
            (throw (ex-info (str "Flow sink node not found: " sink)
                            {:type :loom.flow/missing-node
                             :node sink
                             :role :sink})))
        _ (when (= source sink)
            (throw (ex-info "Flow source and sink must be different nodes"
                            {:type :loom.flow/malformed-constraint
                             :source source
                             :sink sink})))
        _ (doseq [edge (graph/edges g)
                  :let [capacity (graph/weight g edge)]
                  :when (neg? capacity)]
            (throw (ex-info (str "Flow capacity must be non-negative: " edge)
                            {:type :loom.flow/negative-capacity
                             :edge (vec (take 2 edge))
                             :capacity capacity})))
        n (graph/successors g),
        i (predecessors g),
        c (graph/weight g),
        s source,
        t sink
        [flow-map flow-value] (case method
                                :edmonds-karp (flow/edmonds-karp n i c s t)
                                (throw
                                 (ex-info
                                  (str "Method not found.  Choose from: "
                                       method-set)
                                  {:method-set method-set})))]
    [flow-map flow-value]))



;; mst algorithms
;; convenience functions for mst algo
(defn- edge-weights
  "Wrapper function to return edges along with weights for a given graph.
   For un-weighted graphs a default value of one is produced. The function
   returns values of the form [[[u v] 10] [[x y] 20] ...]"
  [wg v]
  (let [edge-weight (fn [u v]
                      (if (weighted? wg) (weight wg u v) 1))]
    (map #(vec [%1 [v (edge-weight v %1)] ])
         (successors wg v)))
  )

(defn prim-mst-edges
  "An edge-list of an minimum spanning tree along with weights that
  represents an MST of the given graph. Returns the MST edge-list
  for un-weighted graphs."
  ([wg]
     (cond
      (directed? wg) (throw (#?(:clj Exception. :cljs js/Error)
                             "Spanning tree only defined for undirected graphs"))
      :else (let [mst (prim-mst-edges wg (nodes wg) nil #{} [])]
              (if (weighted? wg)
                mst
                (map #(vec [(first %1) (second %1)]) mst)))))
  ([wg n h visited acc]
     (cond
      (empty? n) acc
      (empty? h) (let [v (first n)
                       h  (into (pm/priority-map-keyfn second) (edge-weights wg v))]
                   (recur wg (disj n v) h (conj visited v) acc))
      :else (let [next_edge (peek h)
                  u (first (second next_edge))
                  v (first next_edge)
                  update-dist (fn [h [v [u wt]]]
                                (cond
                                 (nil? (get h v)) (assoc h v [u wt])
                                 (> (second (get h v)) wt) (assoc h v [u wt])
                                 :else h))
                  wt (second (second next_edge))
                  visited (conj visited v)
                  h (reduce update-dist (pop h)
                            (filter #((complement visited) (first %) )
                                    (edge-weights wg v)))]
              (recur wg (disj n v) h (conj visited v)(conj acc [u v wt]))))))

(defn prim-mst
  "Minimum spanning tree of given graph. If the graph contains more than one
   component then returns a spanning forest of minimum spanning trees."
  [wg]
  (let [mst (apply graph/weighted-graph (prim-mst-edges wg))]
    (cond
     (= ((comp count nodes) wg) ((comp count nodes) mst)) mst
     :else (apply add-nodes mst (filter #(zero? (out-degree wg %)) (nodes wg)))
     )))

(defn astar-path
  "Returns the shortest path using A* algorithm. Returns a map of predecessors."
  ([g src target heur]
     (validate-node! g src :astar-path)
     (validate-node! g target :astar-path)
     (validate-non-negative-weights! g :astar)
     (let [heur (if (nil? heur) (constantly 0) heur)
           ;; store in q => {u [heur+dist parent act est]}
           q (pm/priority-map-keyfn first src [0 nil 0 0])
           explored (hash-map)]
       (astar-path g src target heur q explored))
      )
  ([g src target heur q explored]
     (cond
      ;; queue empty, target not reachable
      (empty? q) (throw (ex-info "Target not reachable from source" {}))
      ;; target found, build path and return
      (= (first (peek q)) target) (let [_ (first (peek q))
                                        entry (second (peek q))
                                        explored (assoc explored target entry)
                                        path (loop [s target acc {}]
                                               (cond
                                                (nil? s) acc
                                                (= s src) (assoc acc s nil)
                                                :else (let [parent ((explored s) 1)]
                                                        (recur parent
                                                               (assoc acc s parent)))))
                                        ]
                                    path
                                    )
      ;; continue searching
      :else (let
                [curr-node (first (peek q))
                 curr-entry (second (peek q))
                 curr-dist (curr-entry 2)
                 ;; update path
                 explored (assoc explored curr-node curr-entry)
                 nbrs (successors g curr-node)
                 ;; we do this for following reasons
                 ;; a. avoiding duplicate heuristics computation
                 ;; b. duplicate entries for nodes, which needs to be removed later
                 ;; TODO: this could be sped up if we priority-map supported transients
                 update-dist (fn [curr-node curr-dist q v]
                               (let [act (+ curr-dist
                                            (if (weighted? g) (weight g curr-node v) 1))
                                     known (or (get q v) (get explored v))
                                     est (if known (known 3) (heur v target))
                                  ]
                                 (cond
                                  (or (nil? known)
                                      (> (known 2) act))
                                  (assoc q v [(+ act est ) curr-node act est])
                                  :else q)))
                 q (reduce (partial update-dist curr-node curr-dist) (pop q)
                           nbrs)]
              (recur g src target heur q explored)))))

(defn astar-dist
  "Returns the length of the shortest path between src and target using
    the A* algorithm"
  [g src target heur]
  (let [path (astar-path g src target heur)
        dist (reduce (fn [c [u v]]
                       (if (nil? v)
                         c
                         (+ c (if (weighted? g) (weight g v u) 1))
                         )
                       ) 0 path)]
    dist))

(defn degeneracy-ordering
  "Returns sequence of vertices in degeneracy order."
  [g]
  ;; Repeatedly remove the node with the smallest degree in the remaining
  ;; subgraph, decrementing the degrees of its still-present neighbors.
  (loop [ordered-nodes []
         node-degs (->> (zipmap (nodes g)
                                (map (partial out-degree g) (nodes g)))
                        (into (pm/priority-map)))]
    (if (empty? node-degs)
      ordered-nodes
      (let [[n _] (first node-degs)]
        (recur (conj ordered-nodes n)
               (reduce (fn [m n'] (if (contains? m n') (update m n' dec) m))
                       (dissoc node-degs n)
                       (successors g n)))))))

(defn- bk-gen [g [r p x] stack]
  (let [v-pivot (reduce (partial max-key (partial out-degree g)) p)]
    (loop [v v-pivot
           p (set p)
           x (set x)
           stack stack]
      (if (nil? v)
        stack
        (let [succ-v (set (successors g v))]
          (recur (-> (clj.set/difference (disj p v)
                                         (set (successors g v-pivot)))
                     first)
                 (disj p v)
                 (conj x v)
                 (conj stack [(conj r v)
                              (clj.set/intersection p succ-v)
                              (clj.set/intersection x succ-v)])))))))

(defn- bk
  "An iterative implementation of Bron-Kerbosch using degeneracy ordering
  at the outer loop and max-degree vertex pivoting in the inner loop."
  [g]
  (loop [vs (degeneracy-ordering g)
         max-clqs (seq [])
         p (set (nodes g))
         x #{}
         stack []]
    (cond
     ;; Done
     (and (empty? stack) (empty? vs))
     max-clqs

     ;; Empty stack, create a seed to generate stack items
     (empty? stack)
     (let [v (first vs)
           succ-v (set (successors g v))]
       (recur (rest vs)
              max-clqs
              (disj p v)
              (conj x v)
              [[#{v}
                (clj.set/intersection p succ-v)
                (clj.set/intersection x succ-v)]]))

     ;; Pull the next request off the stack
     :else
     (let [[r s-p s-x] (peek stack)]
       (cond
        ;; Maximal clique found
        (and (empty? s-p) (empty? s-x))
        (recur vs
               (cons r max-clqs)
               p
               x
               (pop stack))
        ;; No maximal clique that excludes x exists
        (empty? s-p)
        (recur vs
               max-clqs
               p
               x
               (pop stack))
        ;; Use this state to generate more states
        :else
        (recur vs
               max-clqs
               p
               x
               (bk-gen g [r s-p s-x] (pop stack))))))))

(defn maximal-cliques
  "Enumerate the maximal cliques using Bron-Kerbosch. Only defined for
  undirected graphs; throws on a directed graph rather than returning the
  silently-wrong results Bron-Kerbosch yields there."
  [g]
  (when (directed? g)
    (throw (ex-info "maximal-cliques is only defined for undirected graphs"
                    {:graph g})))
  (bk g))

;;;
;;; Compare graphs
;;;
(defn subgraph?
  "Returns true iff g1 is a subgraph of g2. An undirected graph is never
  considered as a subgraph of a directed graph and vice versa."
  [g1 g2]
  (and (= (directed? g1) (directed? g2))
       (let [edge-test-fn (if (directed? g1)
                            graph/has-edge?
                            (fn [g x y]
                              (or (graph/has-edge? g x y)
                                  (graph/has-edge? g y x))))]
         (and (every? #(graph/has-node? g2 %) (nodes g1))
              (every? (fn [[x y]] (edge-test-fn g2 x y))
                      (edges g1))))))

(defn eql?
  "Returns true iff g1 is a subgraph of g2 and g2 is a subgraph of g1"
  [g1 g2]
  (and (subgraph? g1 g2)
       (subgraph? g2 g1)))

(defn isomorphism?
  "Given a mapping phi between the vertices of two graphs, determine
  if the mapping is an isomorphism, e.g., {(phi x), (phi y)} connected
  in g2 iff {x, y} are connected in g1."
  [g1 g2 phi]
  (eql? g2 (-> (if (directed? g1) (digraph) (graph))
               (graph/add-nodes* (map phi (nodes g1)))
               (graph/add-edges* (map (fn [[x y]] [(phi x) (phi y)])
                                      (edges g1))))))

(defn- insert-in-blocked-map
  "Helper for digraph-all-cycles. When no cycle is found, block curr by
  recording it against each of its children in bmap."
  [cycle-data curr children]
  (reduce (fn [{:keys [bmap] :as acc} child]
            (if (contains? bmap child)
              (update-in acc [:bmap child] conj curr)
              (assoc-in acc [:bmap child] #{curr})))
          cycle-data children))

(defn- unblock-nodes
  "Helper for digraph-all-cycles. Unblock curr and the nodes
  blocked on it (tracked in bset/bmap)."
  [cycle-data curr unblocked]
  (loop [cycle-data cycle-data
         work [[:visit curr unblocked]]]
    (if-let [[operation node seen] (peek work)]
      (case operation
        :finish (recur (update cycle-data :bmap dissoc node) (pop work))
        :visit (if (contains? seen node)
                 (recur cycle-data (pop work))
                 (let [seen (conj seen node)
                       blocked-nodes (get-in cycle-data [:bmap node])
                       work (into (conj (pop work) [:finish node])
                                  (map #(vector :visit % seen)
                                       (reverse blocked-nodes)))]
                   (recur (update cycle-data :bset disj node) work))))
      cycle-data)))

(defn- find-all-cycles
  "Helper for digraph-all-cycles. Returns all cycles through start reachable
  from curr along path."
  [g start curr cycle path rset bset bmap]
  (let [new-frame (fn [curr cycle path bset bmap]
                    {:curr curr
                     :cycle? cycle
                     :all-cycles []
                     :path path
                     :children (seq (successors g curr))
                     :bset (conj bset curr)
                     :bmap bmap})]
    (loop [frames [(new-frame curr cycle path bset bmap)]]
      (let [{:keys [curr cycle? all-cycles path children bset bmap] :as frame}
            (peek frames)]
        (if-let [child (first children)]
          (let [frames (conj (pop frames) (assoc frame :children (next children)))]
            (cond
              (= child start)
              (recur (conj (pop frames)
                           (-> (peek frames)
                               (assoc :cycle? true)
                               (update :all-cycles conj path))))

              (or (contains? rset child) (contains? bset child))
              (recur frames)

              :else
              (recur (conj frames (new-frame child false (conj path child)
                                            bset bmap)))))
          (let [cycle-data {:cycle? cycle? :all-cycles all-cycles
                            :bset bset :rset rset :bmap bmap}
                cycle-data (if cycle?
                             ;; The empty unblocked set prevents cycles in bmap.
                             (unblock-nodes cycle-data curr #{})
                             (insert-in-blocked-map cycle-data curr
                                                     (successors g curr)))
                frames (pop frames)]
            (if-let [parent (peek frames)]
              (recur (conj (pop frames)
                           (-> cycle-data
                               (assoc :curr (:curr parent)
                                      :path (:path parent)
                                      :children (:children parent)
                                      :cycle? (or (:cycle? cycle-data)
                                                  (:cycle? parent))
                                      :all-cycles (into (:all-cycles cycle-data)
                                                        (:all-cycles parent))))))
              cycle-data)))))))

(defn digraph-all-cycles
  "Returns all simple cycles in a directed graph, each as a vector of nodes.
  Returns ::not-a-directed-graph if g is undirected. Implements Johnson's
  algorithm (https://www.cs.tufts.edu/comp/150GA/homeworks/hw1/Johnson%2075.PDF)."
  [g]
  (if (directed? g)
    (as-> {:ans [] :rset #{}} cycle-data
      (reduce (fn [{:keys [ans rset]} curr]
                (let [{:keys [all-cycles rset]}
                      (find-all-cycles g curr curr false [curr] rset #{} {})]
                  {:ans (into ans all-cycles)
                   :rset (conj rset curr)}))
              cycle-data (nodes g))
      (or (seq (:ans cycle-data)) '()))
    ::not-a-directed-graph))

(defn clustering-coefficient
  "The clustering coefficient (Watts & Strogatz, 1998). With a node, returns its
  local clustering coefficient (the fraction of its neighbors' possible
  connections that exist, 0 when it has fewer than two neighbors). With only a
  graph, returns the average over all nodes."
  ([g node]
   (let [neighbours (set (successors g node))
         potential-connections (/ (* (count neighbours) (dec (count neighbours))) 2)]
     (if (> (count neighbours) 1)
       (let [neighbour-overlaps (for [n neighbours]
                                  (let [potential (clj.set/difference neighbours #{n})
                                        actual (set (successors g n))
                                        overlap (clj.set/intersection potential actual)]
                                    (count overlap)))
             sum-overlaps (reduce + 0 neighbour-overlaps)]
         (/ sum-overlaps potential-connections 2))
       0)))
  ([g]
   (let [nodeset (nodes g)
         sum-coeffs (reduce #(+ %1 (clustering-coefficient g %2)) 0 nodeset)]
     (if (empty? nodeset)
       0
       (/ sum-coeffs (count nodeset))))))

;;;
;;; Centrality and ranking
;;;

(defn- finite-distance-map [g source]
  (if (weighted? g)
    (into {} (map (fn [[n state]] [n (first (state n))])
                  (dijkstra-traverse g source vector)))
    (into {} (map (fn [[n _ depth]] [n depth])
                  (bf-traverse g source :f vector)))))

(defn pagerank
  "Returns PageRank scores as a map from node to score.
  Options are :damping (default 0.85), :iterations (default 100), and :tol
  (default 1e-6)."
  [g & {:keys [damping damping-factor iterations max-iterations tol tolerance]
        :or {damping 0.85 iterations 100 tol 1e-6}}]
  (let [damping (or damping-factor damping)
        iterations (or max-iterations iterations)
        tol (or tolerance tol)
        vs (vec (nodes g))
        out-degrees (into {} (map (fn [v] [v (count (successors g v))]) vs))
        predecessors (reduce (fn [result u]
                               (reduce (fn [result v] (update result v conj u))
                                       result
                                       (successors g u)))
                             (zipmap vs (repeat []))
                             vs)
        n (count vs)]
    (if (zero? n)
      {}
      (loop [scores (zipmap vs (repeat (/ 1.0 n)))
             i 0]
        (let [base (/ (- 1.0 damping) n)
              dangling (* damping
                          (/ (reduce + (for [v vs :when (zero? (out-degrees v))]
                                         (scores v))) n))
              next-scores
              (into {}
                    (for [v vs]
                      [v (+ base dangling
                            (reduce +
                                    (for [u (predecessors v)]
                                      (* damping (scores u) (/ 1.0 (out-degrees u))))))]))
              delta (reduce max 0 (map #(Math/abs (double (- (next-scores %) (scores %)))) vs))]
          (if (or (>= i iterations) (< delta tol))
            next-scores
            (recur next-scores (inc i))))))))

(defn degree-centrality
  "Returns normalized degree centrality for every node."
  [g]
  (let [n (count (nodes g))
        denom (max 1 (dec n))]
    (into {} (for [v (nodes g)]
               [v (/ (if (directed? g)
                       (+ (in-degree g v) (out-degree g v))
                       (out-degree g v))
                     denom)]))))

(defn closeness-centrality
  "Returns closeness centrality for every node. Unreachable nodes are omitted
  from the distance sum and the numerator is the number reachable."
  [g]
  (into {}
        (for [v (nodes g)
              :let [ds (dissoc (finite-distance-map g v) v)
                    reachable (count ds)]]
          [v (if (zero? (reduce + 0 (vals ds)))
               0
               (/ reachable (reduce + (vals ds))))])))

(defn- brandes-bfs [g source]
  (loop [queue (conj #?(:clj clojure.lang.PersistentQueue/EMPTY
                         :cljs cljs.core/PersistentQueue.EMPTY) source)
         stack []
         predecessors {}
         path-counts {source 1.0}
         distances {source 0}]
    (if (empty? queue)
      [stack predecessors path-counts]
      (let [v (peek queue)
            next-distance (inc (get distances v))
            [queue predecessors path-counts distances]
            (reduce
             (fn [[queue predecessors path-counts distances] w]
               (let [unseen? (not (contains? distances w))
                     queue (if unseen? (conj queue w) queue)
                     distances (if unseen?
                                 (assoc distances w next-distance)
                                 distances)]
                 (if (= (get distances w) next-distance)
                   [queue
                    (update predecessors w (fnil conj []) v)
                    (update path-counts w (fnil + 0.0) (get path-counts v))
                    distances]
                   [queue predecessors path-counts distances])))
             [(pop queue) predecessors path-counts distances]
             (successors g v))]
        (recur queue (conj stack v) predecessors path-counts distances)))))

(defn- brandes-dijkstra [g source]
  (loop [queue (pm/priority-map source 0)
         stack []
         predecessors {}
         path-counts {source 1.0}
         distances {source 0}]
    (if (empty? queue)
      [stack predecessors path-counts]
      (let [[v distance-v] (peek queue)
            [queue predecessors path-counts distances]
            (reduce
             (fn [[queue predecessors path-counts distances] w]
               (let [candidate-distance (+ distance-v (weight g v w))
                     known-distance (get distances w)]
                 (cond
                   (or (nil? known-distance)
                       (< candidate-distance known-distance))
                   [(assoc queue w candidate-distance)
                    (assoc predecessors w [v])
                    (assoc path-counts w (get path-counts v))
                    (assoc distances w candidate-distance)]

                   (= candidate-distance known-distance)
                   [queue
                    (update predecessors w (fnil conj []) v)
                    (update path-counts w (fnil + 0.0) (get path-counts v))
                    distances]

                   :else
                   [queue predecessors path-counts distances])))
             [(pop queue) predecessors path-counts distances]
             (successors g v))]
        (recur queue (conj stack v) predecessors path-counts distances)))))

(defn- accumulate-betweenness
  [scores source stack predecessors path-counts]
  (loop [stack stack
         dependencies {}
         scores scores]
    (if (empty? stack)
      scores
      (let [w (peek stack)
            dependency (get dependencies w 0.0)
            coefficient (/ (+ 1.0 dependency) (get path-counts w))
            dependencies
            (reduce (fn [dependencies v]
                      (update dependencies v (fnil + 0.0)
                              (* (get path-counts v) coefficient)))
                    dependencies
                    (get predecessors w []))
            scores (if (= w source)
                     scores
                     (update scores w + dependency))]
        (recur (pop stack) dependencies scores)))))

(defn betweenness-centrality
  "Returns normalized betweenness centrality by shortest-path counting.
  Uses edge weights for weighted graphs."
  [g]
  (let [vs (vec (nodes g))
        weighted-graph? (weighted? g)
        _ (when weighted-graph?
            (validate-non-negative-weights! g :betweenness-centrality))
        shortest-paths (if weighted-graph?
                         (partial brandes-dijkstra g)
                         (partial brandes-bfs g))
        raw (reduce (fn [scores source]
                      (let [[stack predecessors path-counts]
                            (shortest-paths source)]
                        (accumulate-betweenness scores source stack
                                                predecessors path-counts)))
                    (zipmap vs (repeat 0.0))
                    vs)
        denominator (* (max 1 (dec (count vs)))
                       (max 1 (- (count vs) 2)))
        scale (/ 1.0 denominator)]
    (into {} (map (fn [[v score]] [v (* scale score)]) raw))))

(defn- power-iteration [initial next-fn iterations tol]
  (loop [scores initial i 0]
    (let [next (next-fn scores)
          delta (reduce max 0 (map #(Math/abs (- (next %) (scores %))) (keys scores)))]
      (if (or (>= i iterations) (< delta tol)) next (recur next (inc i))))))

(defn eigenvector-centrality
  "Returns eigenvector centrality scores. Options are :iterations and :tol."
  [g & {:keys [iterations max-iterations tol tolerance]
        :or {iterations 100 tol 1e-6}}]
  (let [iterations (or max-iterations iterations)
        tol (or tolerance tol)
        vs (vec (nodes g))]
    (if (empty? vs) {}
        (let [next-fn (fn [scores]
                        (let [raw (into {} (for [v vs]
                                             [v (reduce + (for [u (if (directed? g)
                                                                    (predecessors g v)
                                                                    (successors g v))]
                                                            (scores u))) ]))
                              norm (Math/sqrt (reduce + (map #(* % %) (vals raw))))]
                          (if (zero? norm) raw (into {} (map (fn [[v x]] [v (/ x norm)]) raw)))))]
          (power-iteration (zipmap vs (repeat (/ 1.0 (Math/sqrt (count vs)))))
                           next-fn iterations tol)))))

(defn hits
  "Returns {:hubs ... :authorities ...} for a directed graph."
  [g & {:keys [iterations max-iterations tol tolerance]
        :or {iterations 100 tol 1e-6}}]
  (let [iterations (or max-iterations iterations)
        tol (or tolerance tol)
        vs (vec (nodes g))
        step (fn [{:keys [hubs]}]
               (let [a (into {} (for [v vs] [v (reduce + (map hubs (predecessors g v)))]))
                     an (reduce + (map #(Math/abs %) (vals a)))
                     a (if (zero? an) a (into {} (map (fn [[v x]] [v (/ x an)]) a)))
                     h (into {} (for [v vs] [v (reduce + (map a (successors g v)))]))
                     hn (reduce + (map #(Math/abs %) (vals h)))]
                 {:authorities a :hubs (if (zero? hn) h
                                          (into {} (map (fn [[v x]] [v (/ x hn)]) h)))}))]
    (loop [scores {:hubs (zipmap vs (repeat 1.0))
                   :authorities (zipmap vs (repeat 1.0))}
           i 0]
      (let [next (step scores)
            delta (reduce max 0 (concat
                                (map #(Math/abs (- ((:hubs next) %) ((:hubs scores) %))) vs)
                                (map #(Math/abs (- ((:authorities next) %)
                                                   ((:authorities scores) %))) vs)))]
        (if (or (>= i iterations) (< delta tol)) next
            (recur next (inc i)))))))

;;;
;;; Structural graph analysis
;;;

(defn- require-undirected [g operation]
  (when (directed? g)
    (throw (ex-info (str operation " is only defined for undirected graphs")
                    {:graph g}))))

(defn- tarjan-blocks [g]
  (require-undirected g "structural analysis")
  (let [discovery (atom {}) low (atom {}) parent (atom {}) time (atom 0)
        stack (atom []) articulation (atom #{}) bridges (atom #{})
        components (atom [])]
    (letfn [(pop-component [edge]
              (let [es (loop [es []]
                         (let [e (peek @stack)]
                           (swap! stack pop)
                           (if (= e edge) (conj es e) (recur (conj es e)))))]
                (swap! components conj (set (mapcat identity es)))))
            (discover! [u]
              (swap! time inc)
              (swap! discovery assoc u @time)
              (swap! low assoc u @time))
            (visit [start]
              (discover! start)
              (loop [frames [{:node start :children 0
                              :neighbours (seq (successors g start))}]]
                (let [{:keys [node children neighbours] :as frame} (peek frames)]
                  (if-let [v (first neighbours)]
                    (let [frames (conj (pop frames)
                                       (assoc frame :neighbours (next neighbours)))]
                      (if-not (contains? @discovery v)
                        (do
                          (swap! parent assoc v node)
                          (swap! stack conj [node v])
                          (discover! v)
                          (recur (conj (pop frames)
                                       (update (peek frames) :children inc)
                                       {:node v :children 0
                                        :neighbours (seq (successors g v))})))
                        (do
                          (when (and (not= v (@parent node))
                                     (< (@discovery v) (@discovery node)))
                            (swap! stack conj [node v])
                            (swap! low update node min (@discovery v)))
                          (recur frames))))
                    (let [frames (pop frames)]
                      (if-let [parent-frame (peek frames)]
                        (let [u (:node parent-frame)]
                          (swap! low update u min (@low node))
                          (when (or (and (nil? (@parent u))
                                         (> (:children parent-frame) 1))
                                    (and (some? (@parent u))
                                         (>= (@low node) (@discovery u))))
                            (swap! articulation conj u))
                          (when (> (@low node) (@discovery u))
                            (swap! bridges conj (vec (sort-by str [u node]))))
                          (when (>= (@low node) (@discovery u))
                            (pop-component [u node]))
                          (recur frames))
                        (do
                          (when (= children 1)
                            ;; A root with one child is not an articulation point.
                            (swap! articulation disj node))
                          nil)))))))]
      (doseq [v (nodes g) :when (not (contains? @discovery v))]
        (visit v)
        (when (and (empty? (successors g v)) (not (some #{#{v}} @components)))
          (swap! components conj #{v}))
        (when (seq @stack)
          (let [es @stack]
            (reset! stack [])
            (swap! components conj (set (mapcat identity es))))))
      {:articulation @articulation :bridges @bridges :components @components})))

(defn articulation-points
  "Returns the articulation points of an undirected graph as a set."
  [g]
  (:articulation (tarjan-blocks g)))

(defn bridges
  "Returns the bridges of an undirected graph as [source destination] vectors."
  [g]
  (:bridges (tarjan-blocks g)))

(defn biconnected-components
  "Returns biconnected components as a vector of node sets."
  [g]
  (:components (tarjan-blocks g)))

(defn k-core
  "With k, returns the induced k-core subgraph. Without k, returns node
  coreness values."
  ([g]
   (let [active (set (nodes g))]
    (loop [active active degrees (into {} (map (fn [v] [v (count (successors g v))]) active))
            coreness {} current-k 0]
       (if (empty? active)
         coreness
         (let [[v degree] (apply min-key val (select-keys degrees active))
               current-k (max current-k degree)]
           (recur (disj active v)
                  (reduce (fn [ds n] (if (active n) (update ds n #(max 0 (dec %))) ds))
                          degrees (successors g v))
                  (assoc coreness v current-k) current-k))))))
  ([g k]
   (graph/subgraph g (for [[v core] (k-core g) :when (>= core k)] v))))

(defn eccentricity
  "With a node, returns its greatest finite shortest-path distance. With only
  a graph, returns a map of node eccentricities; disconnected nodes are +Inf."
  ([g node]
   (let [ds (finite-distance-map g node)]
     (if (= (count ds) (count (nodes g)))
       (reduce max 0 (vals ds))
       #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity))))
  ([g]
   (into {} (map (fn [v] [v (eccentricity g v)]) (nodes g)))))

(defn radius
  "Returns the minimum graph eccentricity."
  [g]
  (reduce min #?(:clj Double/POSITIVE_INFINITY :cljs js/Infinity) (vals (eccentricity g))))

(defn diameter
  "Returns the maximum graph eccentricity."
  [g]
  (reduce max 0 (vals (eccentricity g))))

;; ;; Todo: MST, coloring, matching, etc etc

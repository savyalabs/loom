(ns ^{:doc "Defines protocols for graphs, digraphs, and weighted graphs.
Also provides record implementations and constructors for simple graphs --
weighted, unweighted, directed, and undirected. The implementations are based
on adjacency lists."
      :author "Justin Kramer"}
  loom.graph
  (:require [loom.alg-generic :refer [bf-traverse]]
            #?@(:clj [[loom.cljs :refer (def-protocol-impls)]]))
  #?@(:cljs [(:require-macros [loom.cljs :refer [def-protocol-impls extend]])]))

;;;
;;; Protocols
;;;

(defprotocol Graph
  (nodes [g] "Returns a collection of the nodes in graph g")
  (edges [g] "Edges in g. May return each edge twice in an undirected graph")
  (has-node? [g node] "Returns true when node is in g")
  (has-edge? [g n1 n2] "Returns true when edge [n1 n2] is in g")
  (successors* [g node] "Returns direct successors of node")
  (out-degree [g node] "Returns the number of outgoing edges of node")
  (out-edges [g node] "Returns all the outgoing edges of node"))

(defprotocol Digraph
  (predecessors* [g node] "Returns direct predecessors of node")
  (in-degree [g node] "Returns the number of direct predecessors to node")
  (in-edges [g node] "Returns all the incoming edges of node")
  (transpose [g] "Returns a graph with all edges reversed"))

(defprotocol WeightedGraph
  (weight* [g e] [g n1 n2] "Returns the weight of edge e or edge [n1 n2]"))

(defprotocol EditableGraph
  (add-nodes* [g nodes] "Add nodes to graph g. See add-nodes")
  (add-edges* [g edges] "Add edges to graph g. See add-edges")
  (remove-nodes* [g nodes] "Remove nodes from graph g. See remove-nodes")
  (remove-edges* [g edges] "Removes edges from graph g. See remove-edges")
  (remove-all [g] "Removes all nodes and edges from graph g"))

(defprotocol Edge
  (src [edge] "Returns the source node of the edge")
  (dest [edge] "Returns the dest node of the edge"))

(defprotocol MultiGraph
  (edges-with-ids [g] "Returns edge objects, including parallel-edge keys")
  (out-edges-with-ids [g node] "Returns keyed outgoing edge objects"))

(defrecord MultiEdge [src dest key])

(extend-type MultiEdge
  Edge
  (src [edge] (:src edge))
  (dest [edge] (:dest edge)))

(defn edge-key
  "Returns the stable key identifying a keyed multigraph edge."
  [edge]
  (:key edge))

; Default implementation for vectors.
(extend-type #?(:clj clojure.lang.IPersistentVector
                :cljs cljs.core.PersistentVector)
  Edge
  (src [edge] (get edge 0))
  (dest [edge] (get edge 1)))

; Default implementation for maps.
#?(:clj
    (extend-type clojure.lang.IPersistentMap
      Edge
      (src [edge] (:src edge))
      (dest [edge] (:dest edge)))
    :cljs
    (do (extend-type cljs.core.PersistentArrayMap
          Edge
          (src [edge] (:src edge))
          (dest [edge] (:dest edge)))
        (extend-type cljs.core.PersistentHashMap
          Edge
          (src [edge] (:src edge))
          (dest [edge] (:dest edge)))
        (extend-type cljs.core.PersistentTreeMap
          Edge
          (src [edge] (:src edge))
          (dest [edge] (:dest edge)))))

;; Curried wrappers
(defn successors
  "Returns direct successors of node"
  ([g] #(successors g %)) ; faster than partial
  ([g node] (successors* g node)))

(defn predecessors
  "Returns direct predecessors of node"
  ([g] #(predecessors g %))
  ([g node] (predecessors* g node)))

(defn weight
 "Returns the weight of edge e or edge [n1 n2]"
  ([g] (partial weight g))
  ([g e] (weight* g e))
  ([g n1 n2] (weight* g n1 n2)))

;; Variadic wrappers

(defn add-nodes
  "Adds nodes to graph g. Nodes can be any type of object"
  [g & nodes]
  (add-nodes* g nodes))

(defn add-edges
  "Adds edges to graph g. For unweighted graphs, edges take the form [n1 n2].
  For weighted graphs, edges take the form [n1 n2 weight] or [n1 n2], the
  latter defaulting to a weight of 1"
  [g & edges]
  (add-edges* g edges))

(defn- prune-attrs
  "Drop attribute entries for removed nodes: their own node/edge attrs, plus
  back-reference edge attrs that surviving nodes hold toward them. The
  ::loom.attr/edge-attrs key is named as a literal to avoid a cyclic require."
  [g removed]
  (let [removed (set removed)
        multigraph? (satisfies? MultiGraph g)
        removed-edge-keys
        (when multigraph?
          (into #{} (for [[n1 nbrs] (:adj g)
                          [n2 keyed] nbrs
                          :when (or (removed n1) (removed n2))
                          k (keys keyed)]
                      k)))
        edge-attr-keys (if multigraph? removed-edge-keys removed)]
    (persistent!
     (reduce-kv
      (fn [m node amap]
        (if (removed node)
          m
          (let [ea (get amap :loom.attr/edge-attrs)
                ea (when ea (apply dissoc ea edge-attr-keys))]
            (assoc! m node (if (seq ea)
                             (assoc amap :loom.attr/edge-attrs ea)
                             (dissoc amap :loom.attr/edge-attrs))))))
      (transient {})
      (:attrs g)))))

(defn remove-nodes
  "Removes nodes from graph g"
  [g & nodes]
  (let [attrs (when (:attrs g) (prune-attrs g nodes))
        g (remove-nodes* g nodes)]
    (if attrs
      (assoc g :attrs attrs)
      g)))

(defn remove-edges
  "Removes edges from graph g. Do not include weights"
  [g & edges]
  (remove-edges* g edges))

;;;
;;; Records for basic graphs: one edge per vertex pair or direction. Loops are
;;; allowed.
;;;
;; TODO: allow custom weight fn?
;; TODO: preserve metadata?
;; TODO: leverage zippers for faster record updates?

(defrecord BasicEditableGraph [nodeset adj])
(defrecord BasicEditableDigraph [nodeset adj in])
(defrecord BasicEditableWeightedGraph [nodeset adj])
(defrecord BasicEditableWeightedDigraph [nodeset adj in])

(def ^{:dynamic true
       :doc "Weight used when none is given for edges in weighted graphs"}
  *default-weight* 1)

#_:clj-kondo/ignore
(def-protocol-impls ^:private default-all
  {:nodes (fn [g]
            (:nodeset g))
   :edges (fn [g]
            (for [n1 (nodes g)
                  e (out-edges g n1)]
              e))
   :has-node? (fn [g node]
                (contains? (:nodeset g) node))
   :has-edge? (fn [g n1 n2]
                (contains? (get-in g [:adj n1]) n2))
   :out-degree (fn [g node]
                 (count (get-in g [:adj node])))
   :out-edges (fn [g node]
                (for [n2 (successors g node)] [node n2]))})

#_:clj-kondo/ignore
(def-protocol-impls ^:private default-unweighted
  ;; Unweighted graphs store adjacencies as {node #{neighbor}}
  {:successors* (fn [g node] (get-in g [:adj node]))})

#_:clj-kondo/ignore
(def-protocol-impls ^:private default-weighted
  ;; Weighted graphs store adjacencies as {node {neighbor weight}}
  {:successors* (fn [g node] (keys (get-in g [:adj node])))})

;; This map of protocol implementations keeps the existing public var while Loom
;; becomes Clojure[Script]-portable.
;; TODO can this be eliminated?
#?(:clj (def default-graph-impls
          {:all default-all
           :unweighted default-unweighted
           :weighted default-weighted}))

#_:clj-kondo/ignore
(def-protocol-impls default-digraph-impl
  {:predecessors* (fn [g node] (get-in g [:in node]))
   :in-degree (fn [g node]
                (count (get-in g [:in node])))
   :in-edges (fn [g node]
               (for [n2 (predecessors g node)] [n2 node]))})

#_:clj-kondo/ignore
(def-protocol-impls default-weighted-graph-impl
  {:weight* (fn
              ([g e] (weight g (src e) (dest e)))
              ([g n1 n2] (get-in g [:adj n1 n2])))})

(defn- remove-adj-nodes [m nodes adjacents remove-fn]
  (reduce
   (fn [m n]
     (if (m n)
       (update-in m [n] #(apply remove-fn % nodes))
       m))
   (apply dissoc m nodes)
   adjacents))

(extend BasicEditableGraph
  Graph
  (merge default-all default-unweighted)

  EditableGraph
  {:add-nodes*
   (fn [g nodes]
     (reduce
      (fn [g node] (update-in g [:nodeset] conj node))
      g nodes))

   :add-edges*
   (fn [g edges]
     (reduce
      (fn [g [n1 n2]]
        (-> g
            (update-in [:nodeset] conj n1 n2)
            (update-in [:adj n1] (fnil conj #{}) n2)
            (update-in [:adj n2] (fnil conj #{}) n1)))
      g edges))

   :remove-nodes*
   (fn [g nodes]
     (let [nbrs (mapcat #(successors g %) nodes)]
       (-> g
           (update-in [:nodeset] #(apply disj % nodes))
           (assoc :adj (remove-adj-nodes (get g :adj) nodes nbrs disj)))))

   :remove-edges*
   (fn [g edges]
     (reduce
      (fn [g [n1 n2]]
        (-> g
            (update-in [:adj n1] disj n2)
            (update-in [:adj n2] disj n1)))
      g edges))

   :remove-all
   (fn [g]
     (assoc g :nodeset #{} :adj {}))})

(extend BasicEditableDigraph
  Graph
  (merge default-all default-unweighted)

  EditableGraph
  {:add-nodes*
   (fn [g nodes]
     (reduce
      (fn [g node] (update-in g [:nodeset] conj node))
      g nodes))

   :add-edges*
   (fn [g edges]
     (reduce
      (fn [g [n1 n2]]
        (-> g
            (update-in [:nodeset] conj n1 n2)
            (update-in [:adj n1] (fnil conj #{}) n2)
            (update-in [:in n2] (fnil conj #{}) n1)))
      g edges))

   :remove-nodes*
   (fn [g nodes]
     (let [ins (mapcat #(predecessors g %) nodes)
           outs (mapcat #(successors g %) nodes)]
       (-> g
           (update-in [:nodeset] #(apply disj % nodes))
           (assoc :adj (remove-adj-nodes (get g :adj) nodes ins disj))
           (assoc :in (remove-adj-nodes (get g :in) nodes outs disj)))))

   :remove-edges*
   (fn [g edges]
     (reduce
      (fn [g [n1 n2]]
        (-> g
            (update-in [:adj n1] disj n2)
            (update-in [:in n2] disj n1)))
      g edges))

   :remove-all
   (fn [g]
     (assoc g :nodeset #{} :adj {} :in {}))}

  Digraph
  (merge default-digraph-impl
         {:transpose (fn [g]
                       ;; Rebuild from reversed edges. Do not swap the :adj/:in
                       ;; fields. In ClojureScript, this generated extend-type
                       ;; method set :adj to nil when it read the fields (#131).
                       ;; The rebuild form also keeps isolated nodes in :nodeset.
                       (reduce (fn [tg [n1 n2]]
                                 (add-edges* tg [[n2 n1]]))
                               (assoc g :adj {} :in {})
                               (edges g)))}))

(extend BasicEditableWeightedGraph
  Graph
  (merge default-all default-weighted)

  EditableGraph
  {:add-nodes*
   (fn [g nodes]
     (reduce
      (fn [g node] (update-in g [:nodeset] conj node))
      g nodes))

   :add-edges*
   (fn [g edges]
     (reduce
      (fn [g [n1 n2 & [w]]]
        (-> g
            (update-in [:nodeset] conj n1 n2)
            (assoc-in [:adj n1 n2] (or w *default-weight*))
            (assoc-in [:adj n2 n1] (or w *default-weight*))))
      g edges))

   :remove-nodes*
   (fn [g nodes]
     (let [nbrs (mapcat #(successors g %) nodes)]
       (-> g
           (update-in [:nodeset] #(apply disj % nodes))
           (assoc :adj (remove-adj-nodes (get g :adj) nodes nbrs dissoc)))))

   :remove-edges*
   (fn [g edges]
     (reduce
      (fn [g [n1 n2]]
        (-> g
            (update-in [:adj n1] dissoc n2)
            (update-in [:adj n2] dissoc n1)))
      g edges))

   :remove-all
   (fn [g]
     (assoc g :nodeset #{} :adj {}))}

  WeightedGraph
  default-weighted-graph-impl)

(extend BasicEditableWeightedDigraph
  Graph
  (merge default-all default-weighted)

  EditableGraph
  {:add-nodes*
   (fn [g nodes]
     (reduce
      (fn [g node] (update-in g [:nodeset] conj node))
      g nodes))

   :add-edges*
   (fn [g edges]
     (reduce
      (fn [g [n1 n2 & [w]]]
        (-> g
            (update-in [:nodeset] conj n1 n2)
            (assoc-in [:adj n1 n2] (or w *default-weight*))
            (update-in [:in n2] (fnil conj #{}) n1)))
      g edges))

   :remove-nodes*
   (fn [g nodes]
     (let [ins (mapcat #(predecessors g %) nodes)
           outs (mapcat #(successors g %) nodes)]
       (-> g
           (update-in [:nodeset] #(apply disj % nodes))
           (assoc :adj (remove-adj-nodes (get g :adj) nodes ins dissoc))
           (assoc :in (remove-adj-nodes (get g :in) nodes outs disj)))))

   :remove-edges*
   (fn [g edges]
     (reduce
      (fn [g [n1 n2]]
        (-> g
            (update-in [:adj n1] dissoc n2)
            (update-in [:in n2] disj n1)))
      g edges))

   :remove-all
   (fn [g]
     (assoc g :nodeset #{} :adj {} :in {}))}

  Digraph
  (merge default-digraph-impl
         {:transpose (fn [g]
                       (reduce (fn [tg [n1 n2]]
                                 (add-edges* tg [[n2 n1 (weight g n1 n2)]]))
                               (assoc g :adj {} :in {})
                               (edges g)))})

  WeightedGraph
  default-weighted-graph-impl)

;;;
;;; Keyed multigraphs. Adjacency is {source {destination {edge-key weight}}}.
;;; The public `edges` view remains endpoint-shaped for compatibility; keyed
;;; edge objects are available through `edges-with-ids`.
;;;

(defrecord BasicEditableMultiGraph [nodeset adj next-key])
(defrecord BasicEditableMultiDigraph [nodeset adj in next-key])

(defn- fresh-edge-key [g n1 n2]
  (loop [k (:next-key g)]
    (if (contains? (get-in g [:adj n1 n2]) k)
      (recur (inc k))
      k)))

(defn- multi-edge-data [e default-weight]
  (if (instance? MultiEdge e)
    [(src e) (dest e) (edge-key e) default-weight]
    (let [[n1 n2 a b] e]
      (if (nil? b)
        [n1 n2 nil (or a default-weight)]
        [n1 n2 a (or b default-weight)]))))

(defn- multi-edge-objects [g node]
  (for [[n2 keyed] (get-in g [:adj node])
        [k _] keyed]
    (MultiEdge. node n2 k)))

(defn- multi-all-edge-objects [g]
  (mapcat #(multi-edge-objects g %) (nodes g)))

(defn- multi-in-edge-objects [g node]
  (for [[n1 keyed] (get-in g [:in node])
        [k _] keyed]
    (MultiEdge. n1 node k)))

(defn- add-multi-edge [g e directed?]
  (let [[n1 n2 supplied-key w] (multi-edge-data e *default-weight*)
        k (or supplied-key (fresh-edge-key g n1 n2))
        g (-> g
              (update-in [:nodeset] conj n1 n2)
              (assoc-in [:adj n1 n2 k] w)
              (update :next-key max (if (number? k) (inc k) (:next-key g))))]
    (if directed?
      (assoc-in g [:in n2 n1 k] w)
      (assoc-in g [:adj n2 n1 k] w))))

(defn- remove-multi-edge [g e directed?]
  (let [[n1 n2 k] (if (instance? MultiEdge e)
                    [(src e) (dest e) (edge-key e)]
                    e)]
    (if (nil? k)
      (let [g (update-in g [:adj n1] dissoc n2)]
        (if directed? (update-in g [:in n2] dissoc n1)
            (update-in g [:adj n2] dissoc n1)))
      (let [g (update-in g [:adj n1 n2] dissoc k)
            g (if directed? (update-in g [:in n2 n1] dissoc k)
                  (update-in g [:adj n2 n1] dissoc k))]
        g))))

(defn- minimum-multi-weight [g n1 n2]
  (when-let [weights (seq (vals (get-in g [:adj n1 n2])))]
    (apply min weights)))

(defn- multi-weight [g e]
  (if-some [k (edge-key e)]
    (get-in g [:adj (src e) (dest e) k])
    (minimum-multi-weight g (src e) (dest e))))

(defn- remove-multi-nodes [g ns directed?]
  (let [removed (set ns)
        strip (fn [adj]
                (into {} (map (fn [[n nbrs]]
                                [n (apply dissoc nbrs removed)]) adj)))
        g (assoc g
                 :nodeset (apply disj (:nodeset g) removed)
                 :adj (strip (apply dissoc (:adj g) removed)))]
    (if directed?
      (assoc g :in (strip (apply dissoc (:in g) removed)))
      g)))

(extend BasicEditableMultiGraph
  Graph
  {:nodes (fn [g] (:nodeset g))
   :edges (fn [g] (for [e (multi-all-edge-objects g)] [(src e) (dest e)]))
   :has-node? (fn [g node] (contains? (:nodeset g) node))
   :has-edge? (fn [g n1 n2] (seq (get-in g [:adj n1 n2])))
   :successors* (fn [g node] (keys (get-in g [:adj node])))
   :out-degree (fn [g node] (reduce + 0 (map count (vals (get-in g [:adj node])))))
   :out-edges (fn [g node] (for [e (multi-edge-objects g node)] [(src e) (dest e)]))}
  MultiGraph
  {:edges-with-ids multi-all-edge-objects
   :out-edges-with-ids multi-edge-objects}
  WeightedGraph
  {:weight* (fn
              ([g e] (multi-weight g e))
              ([g n1 n2] (minimum-multi-weight g n1 n2)))}
  EditableGraph
  {:add-nodes* (fn [g ns] (update g :nodeset into ns))
   :add-edges* (fn [g es] (reduce #(add-multi-edge %1 %2 false) g es))
   :remove-nodes* (fn [g ns] (remove-multi-nodes g ns false))
   :remove-edges* (fn [g es] (reduce #(remove-multi-edge %1 %2 false) g es))
   :remove-all (fn [g] (assoc g :nodeset #{} :adj {} :next-key 0))})

(extend BasicEditableMultiDigraph
  Graph
  {:nodes (fn [g] (:nodeset g))
          :edges (fn [g] (for [e (multi-all-edge-objects g)] [(src e) (dest e)]))
          :has-node? (fn [g node] (contains? (:nodeset g) node))
          :has-edge? (fn [g n1 n2] (seq (get-in g [:adj n1 n2])))
          :successors* (fn [g node] (keys (get-in g [:adj node])))
          :out-degree (fn [g node] (reduce + 0 (map count (vals (get-in g [:adj node])))))
   :out-edges (fn [g node] (for [e (multi-edge-objects g node)] [(src e) (dest e)]))}
  MultiGraph
  {:edges-with-ids multi-all-edge-objects
   :out-edges-with-ids multi-edge-objects}
  WeightedGraph
  {:weight* (fn
              ([g e] (multi-weight g e))
              ([g n1 n2] (minimum-multi-weight g n1 n2)))}
  EditableGraph
  {:add-nodes* (fn [g ns] (update g :nodeset into ns))
   :add-edges* (fn [g es] (reduce #(add-multi-edge %1 %2 true) g es))
   :remove-nodes* (fn [g ns] (remove-multi-nodes g ns true))
   :remove-edges* (fn [g es] (reduce #(remove-multi-edge %1 %2 true) g es))
   :remove-all (fn [g] (assoc g :nodeset #{} :adj {} :in {} :next-key 0))}
  Digraph
  {:predecessors* (fn [g node] (keys (get-in g [:in node])))
   :in-degree (fn [g node] (reduce + 0 (map count (vals (get-in g [:in node])))))
   :in-edges (fn [g node] (for [e (multi-in-edge-objects g node)] [(src e) (dest e)]))
   :transpose (fn [g]
                (reduce (fn [tg e]
                          (add-edges* tg [[(dest e) (src e) (edge-key e) (weight g e)]]))
                        (BasicEditableMultiDigraph. (:nodeset g) {} {} (:next-key g))
                        (edges-with-ids g)))})

;;;
;;; FlyGraph: a read-only graph that uses provided functions to return values for
;;; nodes and edges. Members that are not functions are returned as-is. Edges can
;;; be inferred when nodes and successors are provided. Nodes and edges can be
;;; inferred when successors and start are provided.
;;;

(defn- call-or-return [f & args]
  (if (fn? f)
    (apply f args)
    f))

#_:clj-kondo/ignore
(def-protocol-impls ^:private default-flygraph-graph-impl
  {:nodes (fn [g]
            (if (or (:fnodes g) (not (:start g)))
              (call-or-return (:fnodes g))
              (bf-traverse (successors g) (:start g))))
   :edges (fn [g]
            (if (:fedges g)
              (call-or-return (:fedges g))
              (for [n (nodes g)
                    nbr (successors g n)]
                [n nbr])))
   :successors* (fn [g node] (call-or-return (:fsuccessors g) node))
   :out-degree (fn [g node]
                 (count (successors g node)))
   :out-edges (get-in default-all [:out-edges])
   :has-node? (fn [g node]
                ;; Do not use contains? here because (nodes g) need not be a set.
                (some #{node} (nodes g)))
   :has-edge? (fn [g n1 n2]
                (some #{[n1 n2]} (edges g)))})

#_:clj-kondo/ignore
(def-protocol-impls ^:private default-flygraph-digraph-impl
  {:predecessors* (fn [g node] (call-or-return (:fpredecessors g) node))
   :in-degree (fn [g node] (count (predecessors g node)))
   :in-edges (get-in default-digraph-impl [:in-edges])})

#_:clj-kondo/ignore
(def-protocol-impls ^:private default-flygraph-weighted-impl
  {:weight* (fn
              ([g e] (weight g (src e) (dest e)))
              ([g n1 n2] (call-or-return (:fweight g) n1 n2)))})

(defrecord FlyGraph [fnodes fedges fsuccessors start])
(defrecord FlyDigraph [fnodes fedges fsuccessors fpredecessors start])
(defrecord WeightedFlyGraph [fnodes fedges fsuccessors fweight start])
(defrecord WeightedFlyDigraph
    [fnodes fedges fsuccessors fpredecessors fweight start])

;; Deprecate the flygraphs?  Instead provide interfaces on algorithms to
;; run the algorithm on

(extend FlyGraph
  Graph default-flygraph-graph-impl)

(extend FlyDigraph
  Graph default-flygraph-graph-impl
  Digraph default-flygraph-digraph-impl)

(extend WeightedFlyGraph
  Graph default-flygraph-graph-impl
  WeightedGraph default-flygraph-weighted-impl)

(extend WeightedFlyDigraph
  Graph default-flygraph-graph-impl
  Digraph default-flygraph-digraph-impl
  WeightedGraph default-flygraph-weighted-impl)

;;;
;;; Utility functions and constructors.
;;;

;; TODO: make this work with read-only graphs?
;; An implementation-specific version could also increase speed.
(defn subgraph
  "Returns a graph with only the given nodes"
  [g ns]
  (apply remove-nodes g (remove (set ns) (nodes g))))

(defn add-path
  "Adds a path of edges connecting the given nodes in order"
  [g & nodes]
  (add-edges* g (partition 2 1 nodes)))

(defn add-cycle
  "Adds a cycle of edges connecting the given nodes in order"
  [g & nodes]
  (add-edges* g (partition 2 1 (concat nodes [(first nodes)]))))

(defn graph?
  "Returns true if g satisfies the Graph protocol"
  [g]
  (satisfies? Graph g))

(defn directed?
  "Returns true if g satisfies the Digraph protocol"
  [g]
  (satisfies? Digraph g))

(defn neighbors
  "Returns all nodes adjacent to node, ignoring edge direction. For an
  undirected graph this is just the successors; for a digraph it is the union
  of predecessors and successors."
  ([g] #(neighbors g %))
  ([g node]
   (if (directed? g)
     (into (set (predecessors g node)) (successors g node))
     (successors g node))))

(defn weighted?
  "Returns true if g satisfies the WeightedGraph protocol"
  [g]
  (satisfies? WeightedGraph g))

(defn editable?
  "Returns true if g satisfies the EditableGraph protocol"
  [g]
  (satisfies? EditableGraph g))

(defn build-graph
  "Builds up a graph (i.e. adds edges and nodes) from any combination of
  other graphs, adjacency maps, edges, or nodes."
  [g & inits]
  (letfn [(build [g init]
            (cond
             ;; graph
             (graph? init)
             (if (and (weighted? g) (weighted? init))
               (assoc
                   (reduce add-edges
                           (add-nodes* g (nodes init))
                           (for [[n1 n2] (edges init)]
                             [n1 n2 (weight init n1 n2)]))
                 :attrs (merge (:attrs g) (:attrs init)))
               (-> g
                   (add-nodes* (nodes init))
                   (add-edges* (edges init))
                   (assoc :attrs (merge (:attrs g) (:attrs init)))))
             ;; adacency map
             (map? init)
             (let [es (if (and (seq init) (map? (val (first init))))
                        (for [[n nbrs] init
                              [nbr wt] nbrs]
                          [n nbr wt])
                        (for [[n nbrs] init
                              nbr nbrs]
                          [n nbr]))]
               (-> g
                   (add-nodes* (keys init))
                   (add-edges* es)))
             ;; edge
             (sequential? init) (add-edges g init)
             ;; node
             :else (add-nodes g init)))]
    (reduce build g inits)))

(defn- batch-build
  [kind es]
  (let [weighted (#{:weighted-graph :weighted-digraph} kind)
        directed (#{:digraph :weighted-digraph} kind)
        [nodes adj in] (reduce
                        (fn [[nodes adj in] [n1 n2 w]]
                          (let [nodes (conj! (conj! nodes n1) n2)
                                nbrs (or (get adj n1) (transient (if weighted {} #{})))
                                nbrs (if weighted
                                       (assoc! nbrs n2 (or w *default-weight*))
                                       (conj! nbrs n2))
                                adj (assoc! adj n1 nbrs)]
                            (if directed
                              (let [preds (or (get in n2) (transient (if weighted {} #{})))
                                    preds (if weighted
                                            (assoc! preds n1 (or w *default-weight*))
                                            (conj! preds n1))]
                                [nodes adj (assoc! in n2 preds)])
                              (let [nbrs (or (get adj n2) (transient (if weighted {} #{})))
                                    nbrs (if weighted
                                           (assoc! nbrs n1 (or w *default-weight*))
                                           (conj! nbrs n1))]
                                [nodes (assoc! adj n2 nbrs) in]))))
                        [(transient #{}) (transient {}) (when directed (transient {}))]
                        es)]
    #_:clj-kondo/ignore
    (let [adj (persistent! adj)
          adj (into {} (map (fn [[n nbrs]] [n (persistent! nbrs)]) adj))
          nodes (persistent! nodes)]
      (if directed
        (let [in (persistent! in)
              in (into {} (map (fn [[n nbrs]] [n (persistent! nbrs)]) in))]
          (case kind
            :digraph (BasicEditableDigraph. nodes adj in)
            :weighted-digraph (BasicEditableWeightedDigraph. nodes adj in)))
        (case kind
          :graph (BasicEditableGraph. nodes adj)
          :weighted-graph (BasicEditableWeightedGraph. nodes adj))))))

(defn graph-from-edges
  "Builds an unweighted undirected graph from a bulk edge sequence."
  [es]
  (batch-build :graph es))

(defn digraph-from-edges
  "Builds an unweighted directed graph from a bulk edge sequence."
  [es]
  (batch-build :digraph es))

(defn weighted-graph-from-edges
  "Builds a weighted undirected graph from [source destination weight] edges."
  [es]
  (batch-build :weighted-graph es))

(defn weighted-digraph-from-edges
  "Builds a weighted directed graph from [source destination weight] edges."
  [es]
  (batch-build :weighted-digraph es))

(defn graph
  "Creates an unweighted, undirected graph. inits can be edges, adjacency maps,
  or graphs"
  [& inits]
  (apply build-graph (BasicEditableGraph. #{} {}) inits))

(defn digraph
  "Creates an unweighted, directed graph. inits can be edges, adjacency maps,
  or graphs"
  [& inits]
  (apply build-graph (BasicEditableDigraph. #{} {} {}) inits))

(defn weighted-graph
  "Creates an weighted, undirected graph. inits can be edges, adjacency maps,
  or graphs"
  [& inits]
  (apply build-graph (BasicEditableWeightedGraph. #{} {}) inits))

(defn weighted-digraph
  "Creates an weighted, directed graph. inits can be edges, adjacency maps,
  or graphs"
  [& inits]
  (apply build-graph (BasicEditableWeightedDigraph. #{} {} {}) inits))

(defn multigraph
  "Creates a keyed, weighted, undirected multigraph. Edges may be [u v],
  [u v weight], or [u v key weight]."
  [& inits]
  (apply build-graph (BasicEditableMultiGraph. #{} {} 0) inits))

(defn multidigraph
  "Creates a keyed, weighted, directed multigraph. Edges may be [u v],
  [u v weight], or [u v key weight]."
  [& inits]
  (apply build-graph (BasicEditableMultiDigraph. #{} {} {} 0) inits))

(defn fly-graph
  "Creates a read-only, ad-hoc graph which uses the provided functions
  to return values for nodes, edges, etc. If any members are not functions,
  they will be returned as-is. Edges can be inferred if nodes and
  successors are provided. Nodes and edges can be inferred if successors and
  start are provided."
  [& {:keys [nodes edges successors predecessors weight start]}]
  (cond
   (and predecessors weight)
   (WeightedFlyDigraph. nodes edges successors predecessors weight start)
   predecessors
   (FlyDigraph. nodes edges successors predecessors start)
   weight
   (WeightedFlyGraph. nodes edges successors weight start)
   :else
   (FlyGraph. nodes edges successors start)))

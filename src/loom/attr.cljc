(ns ^{:doc "Graph attribute protocol and implementations for records from
loom.graph. Attributes can provide labels and styles (color, thickness, and so on)."
      :author "Justin Kramer"}
  loom.attr
  (:require [loom.graph :refer [directed? nodes edges src dest has-node?]
             :as graph]
            #?@(:clj [[loom.cljs :refer (def-protocol-impls)]]))
  #?@(:cljs [(:require-macros [loom.cljs :refer [def-protocol-impls extend]])]))

(defprotocol AttrGraph
  (add-attr [g node-or-edge k v] [g n1 n2 k v] "Add an attribute to node or edge")
  (remove-attr [g node-or-edge k] [g n1 n2 k] "Remove an attribute from a node or edge")
  (attr [g node-or-edge k] [g n1 n2 k] "Return the attribute on a node or edge")
  (attrs [g node-or-edge] [g n1 n2] "Return all attributes on a node or edge"))

#_:clj-kondo/ignore
(def-protocol-impls default-attr-graph-impl
  {:add-attr (fn
               ([g node-or-edge k v]
                 (if (has-node? g node-or-edge)
                   (assoc-in g [:attrs node-or-edge k] v)
                   (add-attr g (src node-or-edge) (dest node-or-edge) k v)))
               ([g n1 n2 k v]
                  (let [g (assoc-in g [:attrs n1 ::edge-attrs n2 k] v)
                        g (if (directed? g) g
                              (assoc-in g [:attrs n2 ::edge-attrs n1 k] v))]
                    g)))
   :remove-attr (fn
                  ([g node-or-edge k]
                    (if (has-node? g node-or-edge)
                      (update-in g [:attrs node-or-edge] dissoc k)
                      (remove-attr g (src node-or-edge) (dest node-or-edge) k)))
                  ([g n1 n2 k]
                   (let [g (update-in g [:attrs n1 ::edge-attrs n2] dissoc k)]
                     (if (directed? g) g
                         (update-in g [:attrs n2 ::edge-attrs n1] dissoc k)))))
   :attr (fn
           ([g node-or-edge k]
             (if (has-node? g node-or-edge)
               (get-in g [:attrs node-or-edge k])
               (attr g (src node-or-edge) (dest node-or-edge) k)))
           ([g n1 n2 k]
              (get-in g [:attrs n1 ::edge-attrs n2 k])))
   :attrs (fn
            ([g node-or-edge]
              (if (has-node? g node-or-edge)
                (dissoc (get-in g [:attrs node-or-edge]) ::edge-attrs)
                (attrs g (src node-or-edge) (dest node-or-edge))))
            ([g n1 n2]
               (let [attributes (get-in g [:attrs n1 ::edge-attrs n2])]
               (when (seq attributes) attributes))))})

#_:clj-kondo/ignore
(def-protocol-impls multi-attr-graph-impl
  {:add-attr (fn
               ([g node-or-edge k v]
                (if (has-node? g node-or-edge)
                  (assoc-in g [:attrs node-or-edge k] v)
                  (let [n1 (src node-or-edge) n2 (dest node-or-edge) ek (graph/edge-key node-or-edge)
                        g (assoc-in g [:attrs n1 ::edge-attrs ek k] v)]
                    (if (directed? g) g
                        (assoc-in g [:attrs n2 ::edge-attrs ek k] v)))))
               ([g n1 n2 k v]
                (assoc-in g [:attrs n1 ::edge-attrs n2 k] v)))
   :remove-attr (fn
                  ([g node-or-edge k]
                   (if (has-node? g node-or-edge)
                     (update-in g [:attrs node-or-edge] dissoc k)
                     (update-in g [:attrs (src node-or-edge) ::edge-attrs (graph/edge-key node-or-edge)] dissoc k)))
                  ([g n1 n2 k]
                   (update-in g [:attrs n1 ::edge-attrs n2] dissoc k)))
   :attr (fn
           ([g node-or-edge k]
            (if (has-node? g node-or-edge)
              (get-in g [:attrs node-or-edge k])
              (get-in g [:attrs (src node-or-edge) ::edge-attrs (graph/edge-key node-or-edge) k])))
           ([g n1 n2 k] (get-in g [:attrs n1 ::edge-attrs n2 k])))
   :attrs (fn
            ([g node-or-edge]
             (if (has-node? g node-or-edge)
               (dissoc (get-in g [:attrs node-or-edge]) ::edge-attrs)
               (get-in g [:attrs (src node-or-edge) ::edge-attrs (graph/edge-key node-or-edge)])))
            ([g n1 n2] (get-in g [:attrs n1 ::edge-attrs n2])))})

(extend loom.graph.BasicEditableGraph
  AttrGraph
  default-attr-graph-impl)

(extend loom.graph.BasicEditableDigraph
  AttrGraph
  default-attr-graph-impl)

(extend loom.graph.BasicEditableWeightedGraph
  AttrGraph
  default-attr-graph-impl)

(extend loom.graph.BasicEditableWeightedDigraph
  AttrGraph
  default-attr-graph-impl)

(extend loom.graph.BasicEditableMultiGraph
  AttrGraph
  multi-attr-graph-impl)

(extend loom.graph.BasicEditableMultiDigraph
  AttrGraph
  multi-attr-graph-impl)

(extend loom.graph.FlyGraph
  AttrGraph
  default-attr-graph-impl)

(extend loom.graph.FlyDigraph
  AttrGraph
  default-attr-graph-impl)

(extend loom.graph.WeightedFlyGraph
  AttrGraph
  default-attr-graph-impl)

(extend loom.graph.WeightedFlyDigraph
  AttrGraph
  default-attr-graph-impl)

(defn attr?
  "Returns true if g satisfies AttrGraph"
  [g]
  (satisfies? AttrGraph g))

(defn add-attr-to-nodes
  "Adds an attribute to the given nodes"
  [g k v nodes]
  (reduce
   (fn [g n]
     (add-attr g n k v))
   g nodes))

(defn add-attr-to-edges
  "Adds an attribute to the given edges"
  [g k v edges]
  (reduce
   (fn [g edge]
     (add-attr g edge k v))
   g edges))

(defn add-attr-to-all
  "Adds an attribute to all nodes and edges"
  [g k v]
  (-> g
      (add-attr-to-nodes k v (nodes g))
      (add-attr-to-edges k v (if (satisfies? graph/MultiGraph g)
                               (graph/edges-with-ids g)
                               (edges g)))))

(defn add-attrs-to-all
  "Adds attributes to all nodes and edges"
  [g & kvs]
  (reduce
   (fn [g [k v]]
     (-> g
         (add-attr-to-nodes k v (nodes g))
         (add-attr-to-edges k v (if (satisfies? graph/MultiGraph g)
                                  (graph/edges-with-ids g)
                                  (edges g)))))
   g (partition 2 kvs)))


(defn hilite
  "Adds a red :color attribute to a node or edge"
  ([g node]
     (-> g
         (add-attr node :color :red)
         (add-attr node :fontcolor :red)
         (add-attr node :fillcolor "#ffeeee")
         (add-attr node :style "filled,bold")))
  ([g n1 n2]
     (-> g
         (add-attr n1 n2 :color :red)
         (add-attr n1 n2 :fontcolor :red)
         (add-attr n1 n2 :style :bold))))

(defn hilite-path
  "Highlights nodes and edges along a path."
  [g path]
  (reduce
   (fn [g [n1 n2]]
     (-> g
         (hilite n1)
         (hilite n2)
         (hilite n1 n2)))
   g (partition 2 1 path)))

![Loom logo](https://raw.github.com/aysylu/loom/master/doc/loom_logo.png "Loom")

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/loom.svg)](https://clojars.org/net.clojars.savya/loom)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/loom)](https://cljdoc.org/d/net.clojars.savya/loom/CURRENT)
[![test](https://github.com/savyalabs/loom/actions/workflows/test.yml/badge.svg)](https://github.com/savyalabs/loom/actions/workflows/test.yml)

**Maintenance fork (2026).** The original [`aysylu/loom`](https://github.com/aysylu/loom)
is no longer maintained. This fork supports current Clojure / ClojureScript and
fixes correctness bugs (see CHANGELOG). It is published to Clojars as
`net.clojars.savya/loom`.

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=fff" alt="Clojure" /></a>
<a href="https://clojurescript.org"><img src="https://img.shields.io/badge/ClojureScript-5881D8?style=flat&logo=clojure&logoColor=fff" alt="ClojureScript" /></a>
<a href="https://clojure.org/guides/deps_and_cli"><img src="https://img.shields.io/badge/deps.edn-5881D8?style=flat&logo=clojure&logoColor=fff" alt="deps.edn" /></a>
<a href="https://clojure.github.io/tools.build/"><img src="https://img.shields.io/badge/tools.build-5881D8?style=flat&logo=clojure&logoColor=fff" alt="tools.build" /></a>

## Video and Slides

Watch the Loom talk [at Clojure/West 2014](https://www.youtube.com/watch?v=wEEutxTYQQU). View the [slides](http://www.slideshare.net/aysylu/loom-at-clojurewest-32794616). Watch the talk at [LispNYC](http://youtu.be/Iev7zavblqg). View the [slides](http://www.slideshare.net/aysylu/aysylu-loom).

## Usage

### Install

deps.edn:

```clojure
net.clojars.savya/loom {:mvn/version "1.4.1"}
```

Leiningen:

```clojure
[net.clojars.savya/loom "1.4.1"]
```

Or use the maintained fork directly as a git dependency (deps.edn):

```clojure
io.github.savyalabs/loom {:git/tag "1.3.0" :git/sha "c666221b9c3ad9e600a48fe4853d9cfbf17c87e5"}
```

### Namespaces

    loom.graph   - records & constructors
    loom.alg     - algorithms (see also loom.alg-generic)
    loom.gen     - graph generators
    loom.attr    - graph attributes
    loom.label   - graph labels
    loom.io      - read, write, and view graphs in external formats
    loom.derived - derive graphs from existing graphs using maps and filters

Graph I/O

`loom.io` supports GraphML, GEXF, EDN-encoded edge lists, adjacency JSON, and
DOT import. Use `write-graphml`, `write-gexf`, `write-edge-list`, and
`write-adjacency-json` with their corresponding `read-*` functions;
`dot-str`/`dot` remain available for DOT export and `read-dot` imports DOT.
Weighted graphs, directedness, node values, and Loom attributes are preserved
by the matching writer/reader pairs.

### Generators and validation

`loom.gen` is portable across Clojure and ClojureScript. The seeded generator
arities of `gen-newman-watts`, `gen-barabasi-albert`, `gen-rand`, and `gen-rand-p`
use the same deterministic PRNG on both platforms, so the same seed produces
the same graph. Omit `:seed` (or the positional seed) for a time-based seed.

Graph algorithms throw `ExceptionInfo`/`ex-info` with structured data for invalid
inputs. Traversal and path functions report `:type :loom.alg/missing-node`;
Dijkstra and A* report `:type :loom.alg/negative-weight` because they require
non-negative edge weights. Bellman-Ford and Johnson continue to accept negative
weights (subject to their negative-cycle rules). Maximum flow reports
`:loom.flow/missing-node`, `:loom.flow/negative-capacity`, or
`:loom.flow/malformed-constraint` for invalid source/sink or capacity inputs.

### Documentation

[API Reference](https://cljdoc.org/d/net.clojars.savya/loom/CURRENT)

[Frequently Asked Questions](http://aysy.lu/loom/faq.html)

Join the [Loom mailing list](https://groups.google.com/forum/#!forum/loom-clj) to ask questions.

### Basics

Create a graph:
```clojure
;; Initialize with any of: edges, adacency lists, nodes, other graphs
(def g (graph [1 2] [2 3] {3 [4] 5 [6 7]} 7 8 9))
(def dg (digraph g))
(def wg (weighted-graph {:a {:b 10 :c 20} :c {:d 30} :e {:b 5 :d 5}}))
(def wdg (weighted-digraph [:a :b 10] [:a :c 20] [:c :d 30] [:d :b 10]))
(def rwg (gen-rand (weighted-graph) 10 20 :max-weight 100))
(def fg (fly-graph :successors range :weight (constantly 77)))
```

For parallel edges, use `multigraph` or `multidigraph`. Each edge receives a
stable key; use `edges-with-ids` (or `out-edges-with-ids`) to address a specific
edge and pass it to `weight` or `loom.attr`:

```clojure
(def mg (multigraph [1 2 :rail 10] [1 2 :road 20]))
(map edge-key (edges-with-ids mg))
;; => (:rail :road :rail :road) ; undirected edges are exposed in both directions
(weight mg (first (filter #(= :rail (edge-key %))
                          (out-edges-with-ids mg 1))))
;; => 10
```

For large bulk edge lists, `graph-from-edges`, `digraph-from-edges`,
`weighted-graph-from-edges`, and `weighted-digraph-from-edges` build adjacency
maps with transients and persist them once at the end.
If you have [GraphViz](http://www.graphviz.org) installed, and its binaries are in the path, you can view graphs with <code>loom.io/view</code>:
```clojure
(view wdg) ;opens image in default image viewer
```

Inspect:
```clojure
(nodes g)
=> #{1 2 3 4 5 6 7 8 9}

(edges wdg)
=> ([:a :c] [:a :b] [:c :d] [:d :b])

(successors g 3)
=> #{2 4}

(predecessors wdg :b)
=> #{:a :d}

(out-degree g 3)
=> 2

(in-degree wdg :b)
=> 2

(weight wg :a :c)
=> 20

(map (juxt graph? directed? weighted?) [g wdg])
=> ([true false false] [true true true])
```
Add or remove items. Graphs are immutable, so these functions return new graphs:
```clojure
(add-nodes g "foobar" {:name "baz"} [1 2 3])

(add-edges g [10 11] ["foobar" {:name "baz"}])

(add-edges wg [:e :f 40] [:f :g 50]) ;weighted edges

(remove-nodes g 1 2 3)

(remove-edges g [1 2] [2 3])

(subgraph g [5 6 7])
```
Traverse a graph:
```clojure
(bf-traverse g) ;lazy
=> (9 8 5 6 7 1 2 3 4)

(bf-traverse g 1)
=> (1 2 3 4)

(pre-traverse wdg) ;lazy
=> (:a :b :c :d)

(post-traverse wdg) ;not lazy
=> (:b :d :c :a)

(topsort wdg)
=> (:a :c :d :b)
```
Pathfinding:
```clojure
(bf-path g 1 4)
=> (1 2 3 4)

(bf-path-bi g 1 4) ;bidirectional, parallel
=> (1 2 3 4)

(dijkstra-path wg :a :d)
=> (:a :b :e :d)

(dijkstra-path-dist wg :a :d)
=> [(:a :b :e :d) 20]
```

Flow:
```clojure
;; max-flow uses weighted edges as capacities and returns [flow-map value]
(max-flow wdg :a :d)

;; min-cost-flow reads :demand from nodes and :capacity/:cost from edges.
;; Negative demand supplies flow; positive demand consumes it. It returns
;; [flow-map total-cost], matching max-flow's result shape.
(min-cost-flow g)

;; Attribute names can be customized with an optional map.
(min-cost-flow g {:capacity :cap :cost :unit-cost :demand :balance})
```
Other stuff:
```clojure
(connected-components g)
=> [[1 2 3 4] [5 6 7] [8] [9]]

(bf-span wg :a)
=> {:c [:d], :b [:e], :a [:b :c]}

(pre-span wg :a)
=> {:a [:b], :b [:e], :e [:d], :d [:c]}

(dijkstra-span wg :a)
=> {:a {:b 10, :c 20}, :b {:e 15}, :e {:d 20}}
```
Graph analysis functions include `pagerank`, `degree-centrality`,
`closeness-centrality`, `betweenness-centrality`, `eigenvector-centrality`,
`hits`, `articulation-points`, `bridges`, `biconnected-components`, `k-core`,
`eccentricity`, `radius`, and `diameter`. `pagerank` accepts `:damping`,
`:iterations`, and `:tol`; iterative centrality algorithms accept `:iterations`
and `:tol`. Structural decomposition functions operate on undirected graphs.

Attributes on nodes and edges:
```clojure
(def attr-graph (-> g
                (add-attr 1 :label "node 1")
                (add-attr 4 :label "node 4")
                (add-attr-to-nodes :parity "even" [2 4])
                (add-attr-to-edges :label "edge from node 5" [[5 6] [5 7]])))

; Return attribute value on node 1 with key :label
(attr attr-graph 1 :label)
=> "node 1"

; Return attribute value on node 2 with key :parity
(attr attr-graph 2 :parity)
=> "even"

; Getting an attribute that doesn't exist returns nil
(attr attr-graph 3 :label)
=> nil

; Return all attributes for node 4
; Two attributes found
(attrs attr-graph 4)
=> {:parity "even", :label "node 4"}

; Return attribute value for edge between nodes 5 and 6 with key :label
(attr attr-graph 5 6 :label)
=> "edge from node 5"

; Return all attributes for edge between nodes 5 and 7
(attrs attr-graph 5 7)
=> {:label "edge from node 5"}

; Getting an attribute that doesn't exist returns nil
(attrs attr-graph 3 4)
=> nil

; Remove the attribute of node 4 with key :label
(def attr-graph (remove-attr attr-graph 4 :label))

; Return all attributes for node 4
; One attribute found because the other has been removed
(attrs attr-graph 4)
=> {:parity "even"}
```
Derived graphs:
```clojure
; Build a derived graph using a node mapping
(nodes (mapped-by #(+ 10 %) g))
=> #{11 12 13 14 15 16 17 18 19}

; Subgraphs of g
(edges (nodes-filtered-by #{1 2 3 5} dg))
=> ([1 2] [2 1] [2 3] [3 2])

(edges (subgraph-reachable-from dg 1))
=> ([1 2] [2 1] [2 3] [3 2] [3 4] [4 3])
```
## Dependencies

Loom uses Clojure. It can use [GraphViz](http://graphviz.org) for visualization.

## TODO

See [Loom TODO board](https://trello.com/b/VgPZkvjP/loom-todo).

## Testing

```bash
clojure -M:test
clojure -T:build jar
clojure -T:build deploy
```

## Contributors

Names are in no order:

* [Justin Kramer](https://github.com/jkk/)
* [Aysylu Greenberg] (https://github.com/aysylu), [aysylu [dot] greenberg [at] gmail [dot] com](mailto:aysylu.greenberg@gmail.com), [@aysylu22](http://twitter.com/aysylu22)
* [Robert Lachlan](https://github.com/heffalump), [robertlachlan@gmail.com](mailto:robertlachlan@gmail.com)
* [Stephen Kockentiedt](https://github.com/s-k)

## Namespaces

The dependency graph of Loom namespaces. [lein-ns-dep-graph](https://github.com/hilverd/lein-ns-dep-graph) generates it.

![Loom namespace dependency graph](./doc/ns-dep-graph.png)

## License

Copyright © 2010-2016 Aysylu Greenberg & Justin Kramer (jkkramer@gmail.com).

Savyasachi maintains this fork (2026). Original: https://github.com/aysylu/loom.
The project uses the [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html). It keeps the original license.

# Change Log

## [1.4.1] - 2026-09-07

### Fixed

Found by two independent senior-model code reviews (Codex + Claude), each
verified by execution against the affected functions before any fix landed.

- `johnson` never applied the Bellman-Ford reweighting correction, silently
  returning wrong all-pairs shortest-path distances for any weighted graph
  (`all-pairs-shortest-paths` dispatches here). The existing test fixture
  asserted the wrong values - corrected alongside the fix.
- `betweenness-centrality` silently ignored edge weights (plain BFS
  hop-counting even on weighted input) and was cubic. Rewritten using
  Brandes' algorithm: correct on weighted graphs, O(VE) instead of O(V^3).
- `weight*` on multigraphs NPE'd on plain `[u v]` edge vectors, or picked an
  arbitrary parallel edge instead of the minimum, breaking every weighted
  algorithm (dijkstra/astar/johnson/bellman-ford/max-flow) on multidigraphs.
- `remove-edges` on a multigraph deleted every parallel edge instead of the
  one targeted by edge-key.
- `astar-path` returned non-shortest paths for admissible-but-inconsistent
  heuristics; fixed via standard node reopening.
- `bf-path` returned `nil` for `start = end` instead of the zero-hop path,
  inconsistent with `bf-path-bi`/`dijkstra-path`.
- `network-simplex/solve` misreported an unbounded negative-cost cycle as a
  finite optimum, and its "infinity" sentinel silently became `0` on an
  all-zero-magnitude graph (Clojure's `0` is truthy, so `(or (* 3 (max ...))
  1)` never fell through).
- `articulation-points`/`bridges`/`tarjan-blocks`/`digraph-all-cycles` used
  direct recursion, stack-overflowing on graphs beyond a few thousand nodes.
  Converted to an explicit-stack iterative walk.
- `remove-attr` was asymmetric on undirected graphs; multigraph edge
  attributes were split-brain across two storage keys; `subgraph` bypassed
  attribute pruning; `remove-multi-nodes` added a spurious `:in` key on
  undirected multigraphs.
- `clustering-coefficient` threw on weighted graphs; `bellman-ford` threw on
  `FlyGraph`; `clustering-coefficient`/`density` divided by zero on
  empty/singleton graphs.
- `pagerank` was O(iterations x V x (V+E)) instead of O(iterations x
  (V+E)) - measured 51-142x speedup after precomputing reverse adjacency.

`compliance_tester.cljc` now also exercises multigraph/multidigraph against
the shared protocol contracts, the root cause behind several of the above.

## [1.4.0] - 2026-08-27

### Added

- `loom.io` readers and writers for GraphML, GEXF, edge lists, and adjacency
  JSON, plus a DOT reader. Matching writer/reader pairs preserve directedness,
  weights, node values, and Loom attributes.
- Keyed `multigraph` and `multidigraph` constructors with stable parallel
  edge identities, per-edge weights, and attribute support through
  `edges-with-ids`.
- Transient-backed bulk constructors for the four simple graph variants.
- PageRank, degree, closeness, betweenness, eigenvector, and HITS centrality
  and ranking algorithms.
- Articulation points, bridges, biconnected components, k-core, eccentricity,
  radius, and diameter graph structure algorithms.
- `loom.flow/min-cost-flow` exposes the existing network-simplex solver as a
  graph-level operation over node demand and edge capacity/cost attributes,
  returning `[flow-map total-cost]` consistently with `max-flow`.
- `loom.gen` is now portable to ClojureScript with a cross-platform seeded
  PRNG; seeded graph generators now produce identical results on the JVM
  and JS.
- Structured `ex-info` validation for missing algorithm nodes, negative
  weights passed to Dijkstra/A*, and malformed maximum-flow capacities and
  source/sink constraints.

This is a backward-compatible minor release: existing simple-graph protocol
implementations and endpoint-shaped `edges` behavior are unchanged.

## [1.3.2] - 2026-08-17

### Fixed

- `add-attrs-to-all` no longer invents bogus attributes: it treated the flat
  key/value list as a sliding window (`partition 2 1`), writing a spurious
  entry keyed by each value. It now pairs keys with values (`partition 2`).

## [1.3.1] - 2026-07-12

### Changed

- Migrate the build to deps.edn and tools.build, with Leiningen supported via lein-tools-deps.

## [1.3.0] - 2026-06-21

**New:**
- `loom.gen/gen-circle` and `loom.gen/gen-newman-watts` - ring and small-world
  (Newman & Watts 1999) graph generators, seeded for reproducibility. Closes #106.
- `loom.gen/gen-barabasi-albert` - scale-free graph generator via preferential
  attachment (Barabasi & Albert 1999), seeded. Reworks #105 (whose attachment
  probability was inverted).
- `loom.alg/clustering-coefficient` - local and average clustering coefficient
  (Watts & Strogatz 1998).

## [1.2.0] - 2026-06-21

**New:**
- `loom.alg/simple-paths` - all simple paths between two nodes, with an optional
  `:max-depth`. Closes #111.
- `loom.alg/digraph-all-cycles` - all simple cycles in a directed graph
  (Johnson's algorithm). Closes #126.

**Performance:**
- `degeneracy-ordering` decrements neighbor degrees directly instead of building
  an intermediate map. Closes #108.
- `pre-traverse` pushes successors lazily, avoiding O(E) stack growth on dense
  graphs (preorder unchanged). Closes #120.

## [1.1.0] - 2026-06-21

First release of the maintained fork, published as `net.clojars.savya/loom`.

**Platform:**
- Default to Clojure 1.12; test matrix on Clojure 1.10/1.11/1.12 and JDK 8/11/17/21.
- Bump data.priority-map to 1.2.1, test.check to 1.1.3, ClojureScript to 1.12.145.
- Add `deps.edn` so loom is usable as a git dependency and via `clojure -X:test`.
- GitHub Actions CI (clj matrix + a ClojureScript/node job).

**Bug fixes:**
- `transpose` returned an empty graph under ClojureScript, breaking `scc`,
  `strongly-connected?`, and every transpose-based operation. Fixes #131.
- `remove-nodes` / `subgraph` threw a null error on digraphs under ClojureScript.
  Fixes #134.
- `bf-path-bi` ran its two searches in racing threads and could return a
  non-shortest path; it is now deterministic.
- `bipartite-color` ignored edge direction on digraphs, giving non-deterministic
  results for nodes with no outgoing edges. Fixes #118.
- `maximal-cliques` on a digraph now throws instead of returning silently-wrong
  results. Fixes #128.
- `remove-nodes` now prunes the removed nodes' attributes. Fixes #93.
- `weight` on an edge dispatches to `(weight* g e)`, honoring the protocol for
  graphs whose edges are not determined by their endpoints. Fixes #141.
- Building a graph from an empty adjacency map (`(graph {})`) no longer throws.
  Fixes #137.

## [1.0.1] - 2018-02-19
[Full Changelog](https://github.com/aysylu/loom/compare/1.0.0...1.0.1)

**Closed issues:**

- Make 1.0.1 release ? [\#103](https://github.com/aysylu/loom/issues/103)
- loom.io/view calls render-to-bytes incorrectly [\#95](https://github.com/aysylu/loom/issues/95)
- :fmt option in render-to-bytes? [\#56](https://github.com/aysylu/loom/issues/56)

**Merged pull requests:**

- highlighting loom code samples [\#99](https://github.com/aysylu/loom/pull/99) ([ertugrulcetin](https://github.com/ertugrulcetin))
- Add network simplex implementation [\#98](https://github.com/aysylu/loom/pull/98) ([drhops](https://github.com/drhops))
- Pass positional keyword arguments correctly [\#96](https://github.com/aysylu/loom/pull/96) ([lvh](https://github.com/lvh))

## [1.0.0] - 2017-02-16
[Full Changelog](https://github.com/aysylu/loom/compare/0.6.0...1.0.0)

**Closed issues:**

- Support for weighted nodes [\#92](https://github.com/aysylu/loom/issues/92)
- default-flygraph-digraph-impl has error, nil protocol method impl [\#90](https://github.com/aysylu/loom/issues/90)
- Graph Attribute [\#89](https://github.com/aysylu/loom/issues/89)
- load graph from disk [\#88](https://github.com/aysylu/loom/issues/88)
- A\* implementation is incorrect. [\#82](https://github.com/aysylu/loom/issues/82)
- clojurescript support [\#45](https://github.com/aysylu/loom/issues/45)

**Merged pull requests:**

- Add support for other formats in loom.io/view [\#94](https://github.com/aysylu/loom/pull/94) ([mikekap](https://github.com/mikekap))
- Make loom Clojure\[Script\] portable [\#91](https://github.com/aysylu/loom/pull/91) ([cemerick](https://github.com/cemerick))
- Fix bugs in A\* implementation. [\#84](https://github.com/aysylu/loom/pull/84) ([tessellator](https://github.com/tessellator))

## [0.6.0] - 2016-04-14
[Full Changelog](https://github.com/aysylu/loom/compare/0.5.4...0.6.0)

**Closed issues:**

- Specify color and shape of the nodes [\#80](https://github.com/aysylu/loom/issues/80)
- when nodes are records, loom.io/view renders incorrectly [\#75](https://github.com/aysylu/loom/issues/75)
- Move dataflow framework from ssa branch into loom.dataflow [\#65](https://github.com/aysylu/loom/issues/65)
- Move to Clojure 1.7 and convert to cljc [\#60](https://github.com/aysylu/loom/issues/60)
- Combining graphs fails to preserve attribute data [\#55](https://github.com/aysylu/loom/issues/55)
- partial application doesn't belong in the loom.graph protocols [\#43](https://github.com/aysylu/loom/issues/43)

**Merged pull requests:**

- Create initial compliance test [\#73](https://github.com/aysylu/loom/pull/73) ([mattrepl](https://github.com/mattrepl))
- derive graphs from existing graphs using maps and filters [\#71](https://github.com/aysylu/loom/pull/71) ([monora](https://github.com/monora))
- Add namespace dependency graph image to README [\#69](https://github.com/aysylu/loom/pull/69) ([danielcompton](https://github.com/danielcompton))
- Move to Clojure 1.7 and cljc [\#61](https://github.com/aysylu/loom/pull/61) ([danielcompton](https://github.com/danielcompton))

## [0.5.4] - 2015-07-11
[Full Changelog](https://github.com/aysylu/loom/compare/0.5.0...0.5.4)

**Closed issues:**

- obsolete API docs [\#68](https://github.com/aysylu/loom/issues/68)
- ClojureScript? [\#67](https://github.com/aysylu/loom/issues/67)
- gen-rand throws exception for weighted graphs unless min-weight and max-weight are explicitly provided [\#51](https://github.com/aysylu/loom/issues/51)
- Several functions have misplaced doc strings [\#46](https://github.com/aysylu/loom/issues/46)
- var defined a second time... [\#36](https://github.com/aysylu/loom/issues/36)
- something's wrong... [\#35](https://github.com/aysylu/loom/issues/35)
- core.matrix support? [\#28](https://github.com/aysylu/loom/issues/28)

**Merged pull requests:**

- Make tense on docstrings consistent [\#63](https://github.com/aysylu/loom/pull/63) ([danielcompton](https://github.com/danielcompton))
- Clean up ns and make code more idiomatic [\#62](https://github.com/aysylu/loom/pull/62) ([danielcompton](https://github.com/danielcompton))
- Fix for \#55 [\#59](https://github.com/aysylu/loom/pull/59) ([AshtonKem](https://github.com/AshtonKem))
- fix typo in bellman-ford docstring [\#58](https://github.com/aysylu/loom/pull/58) ([aw7](https://github.com/aw7))
- Add has-node? to flygraph default implementation. [\#57](https://github.com/aysylu/loom/pull/57) ([monora](https://github.com/monora))
- Ensure that max-weight is greater than min-weight when generating random weighted-graphs [\#53](https://github.com/aysylu/loom/pull/53) ([tihancock](https://github.com/tihancock))
- Fix comment "nodes" -\> "edges" [\#52](https://github.com/aysylu/loom/pull/52) ([semperos](https://github.com/semperos))
- Greedy coloring for graphs/digraphs [\#50](https://github.com/aysylu/loom/pull/50) ([danshapero](https://github.com/danshapero))
- Allow for more global attributes in graphviz [\#48](https://github.com/aysylu/loom/pull/48) ([zmaril](https://github.com/zmaril))
- Patch aysylu/loom/46 [\#47](https://github.com/aysylu/loom/pull/47) ([arrdem](https://github.com/arrdem))
- Maximal cliques and all-pairs shortest path algorithms [\#40](https://github.com/aysylu/loom/pull/40) ([mattrepl](https://github.com/mattrepl))
- Pre traverse fix [\#38](https://github.com/aysylu/loom/pull/38) ([gshopov](https://github.com/gshopov))
- Corrected misplaced docstrings and type hints \(found by eastwood\). [\#37](https://github.com/aysylu/loom/pull/37) ([fmjrey](https://github.com/fmjrey))
- now at version to 0.5.0 [\#33](https://github.com/aysylu/loom/pull/33) ([fmjrey](https://github.com/fmjrey))
- dot-str: edge label, when available, takes precedence over weight. [\#32](https://github.com/aysylu/loom/pull/32) ([fmjrey](https://github.com/fmjrey))
- Edge traverse [\#31](https://github.com/aysylu/loom/pull/31) ([fmjrey](https://github.com/fmjrey))

## [0.5.0] - 2014-06-09
[Full Changelog](https://github.com/aysylu/loom/compare/0.4.2...0.5.0)

**Closed issues:**

- Write FAQ [\#12](https://github.com/aysylu/loom/issues/12)
- Link to autodocs [\#11](https://github.com/aysylu/loom/issues/11)
- `\(scc ...\)` dies with a StackOverflow on large directed graphs [\#5](https://github.com/aysylu/loom/issues/5)
- Add documentation on loom.attr functions [\#4](https://github.com/aysylu/loom/issues/4)

## [0.4.2] - 2014-01-04
[Full Changelog](https://github.com/aysylu/loom/compare/0.4.1...0.4.2)

**Closed issues:**

- attrs gives inconsistent results when no attributes exist [\#9](https://github.com/aysylu/loom/issues/9)

## [0.4.1] - 2013-10-27
**Closed issues:**

- \(scc ...\) doesn't compute the correct components in some cases [\#6](https://github.com/aysylu/loom/issues/6)
- add slides and video from LispNYC to README page [\#3](https://github.com/aysylu/loom/issues/3)
- Should use attr instead of label in visualization [\#2](https://github.com/aysylu/loom/issues/2)



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*

/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_OPTO_GRAPHINVARIANTS_HPP
#define SHARE_OPTO_GRAPHINVARIANTS_HPP

#include "memory/allocation.hpp"
#include "opto/node.hpp"

#ifndef PRODUCT
// An invariant that needs only a local view of the graph, around a given node.
class LocalGraphInvariant : public ResourceObj {
public:
  static constexpr int OutputStep = -1;

  struct LazyReachableCFGNodes {
    bool is_node_dead(const Node*);
  private:
    void fill();
    Unique_Node_List live_nodes;
  };

  enum class CheckResult {
    VALID,          // The check applies, and it is satisfied on the given center
    FAILED,         // The check applies, but finds that the invariant is broken
    NOT_APPLICABLE, // The check has no opinion on the given center
  };

  // For reporting
  virtual const char* name() const = 0;

  /* Check whether the invariant is true around the node [center]. The argument [steps] and [path] are initially empty.
   *
   * If the check fails steps and path must be filled with the path from the center to the failing node (where it's relevant to show).
   * Given a list of node
   * center = N0 --[r1]--> ... --[rk]-> Nk
   * where the ri are the relation between consecutive nodes: either p-th input, or an output,
   * then:
   * - steps must have length k + 1, and contain Nk ... N0
   * - path must have length k, and contain rk ... r1 where ri is:
   *   - a non-negative integer p for each step such that N{i-1} has Ni as p-th input (we need to follow an input edge)
   *   - the OUTPUT_STEP value in case N{i-1} has Ni as an output (we need to follow an output edge)
   * The list are reversed to allow to easily fill them lazily on failure.
   * In addition, if the check fails, it must write its error message in [ss].
   *
   * If the check succeeds or is not applicable, [steps], [path] and [ss] must be untouched.
   *
   * The parameter [live_nodes] is used to share the lazily computed set of CFG nodes reachable from root. This is because some
   * checks don't apply to dead code, suppress their error if a violation is detected in dead code.
   */
  virtual CheckResult check(const Node* center, LazyReachableCFGNodes& live_nodes, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const = 0;
};

/* Checks structural invariants of the graph connected to the root.
 *
 * The checker itself is not node or graph dependent and can be used
 * on any graph, to save memory: one allocation is enough!
 *
 * Local invariants are checked on each node of the graph: the check
 * is overall failing if any invariant doesn't hold on any node.
 *
 * It currently only checks local invariants, but it could be extended
 * to global ones.
 */
class GraphInvariantChecker : public ResourceObj {
  GrowableArray<const LocalGraphInvariant*> _checks;

public:
  static GraphInvariantChecker* make_default();
  bool run() const;
};
#endif

#endif // SHARE_OPTO_GRAPHINVARIANTS_HPP
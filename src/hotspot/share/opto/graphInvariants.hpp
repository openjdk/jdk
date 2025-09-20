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
#include "opto/pattern.hpp"

#ifndef PRODUCT
// An invariant that needs only a local view of the graph, around a given node.
class LocalGraphInvariant : public ResourceObj {
public:
  // See LocalGraphInvariant::check why we need that.
  struct LazyReachableCFGNodes {
    bool is_node_dead(const Node*);
  private:
    void compute();
    Unique_Node_List _reachable_nodes;
  };

  enum class CheckResult {
    VALID,          // The check applies, and it is satisfied on the given center
    FAILED,         // The check applies, but finds that the invariant is broken
    NOT_APPLICABLE, // The check has no opinion on the given center
  };

  // For reporting
  virtual const char* name() const = 0;

  /* Check whether the invariant is true around the node [center].
   *
   * If the check fails steps and path must be filled with the path from the center to the failing node (where it's relevant to
   * show), in reverse order (for filling lazily on failures).
   * In addition, if the check fails, it must write its error message in [ss].
   *
   * If the check succeeds or is not applicable, [path] and [ss] must be untouched.
   *
   * The parameter [live_nodes] is used to share the lazily computed set of CFG nodes reachable from root. This is because some
   * checks don't apply to dead code, and we want to suppress their error if a violation is detected in dead code. Since it's
   * rather unlikely to have such a violation (they are rare overall), and then we won't need to check whether a node is dead,
   * it's better to have this set lazy.
   */
  virtual CheckResult check(const Node* center, LazyReachableCFGNodes& live_nodes, PathInGraph& path, stringStream& ss) const = 0;
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

  // See LocalGraphInvariant::check for the requirements on the arguments.
  // Fills parameter [ss] with pretty print of the path.
  static void print_path(const PathInGraph& path, stringStream& ss);
  bool run() const;
};
#endif

#endif // SHARE_OPTO_GRAPHINVARIANTS_HPP
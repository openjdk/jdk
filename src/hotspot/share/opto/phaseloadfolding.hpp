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

#ifndef SHARE_OPTO_PHASELOADFOLDING_HPP
#define SHARE_OPTO_PHASELOADFOLDING_HPP

#include "libadt/vectset.hpp"
#include "opto/node.hpp"
#include "opto/phase.hpp"
#include "utilities/growableArray.hpp"

class AllocateNode;
class PhaseIterGVN;

// Try to fold loads by finding the corresponding stores. The transformations here inspect the
// graph more aggressively than during IterGVN, so it is a separate phase in the compilation
// process. The loads taken into consideration are:
//
// 1. If an object has not escaped, then all modification must be visible in the graph. As a
//    result, we can follow the memory input, skip through calls and memory fences to find a
//    corresponding store.
class PhaseLoadFolding : public Phase {
private:
  PhaseIterGVN& _igvn;

  class WorkLists {
  public:
    VectorSet may_alias;
    Unique_Node_List escapes;
    Unique_Node_List work_list;
    GrowableArray<Node*> results;
  };

  bool do_optimize();
  bool process_allocate_result(Node* oop);
  void collect_loads(Unique_Node_List& candidates, VectorSet& candidate_mems, Node* oop);
  void process_candidates(VectorSet& candidate_mems, WorkLists& work_lists, Node* oop);
  Node* try_fold_recursive(Node* oop, LoadNode* candidate, Node* mem, WorkLists& work_lists);

public:
  PhaseLoadFolding(PhaseIterGVN& igvn) : Phase(LoadFolding), _igvn(igvn) {}
  void optimize();
};

#endif // SHARE_OPTO_PHASELOADFOLDING_HPP

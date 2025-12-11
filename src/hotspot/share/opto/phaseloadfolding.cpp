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

#include "libadt/vectset.hpp"
#include "memory/resourceArea.hpp"
#include "opto/addnode.hpp"
#include "opto/callnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/compile.hpp"
#include "opto/memnode.hpp"
#include "opto/node.hpp"
#include "opto/phaseloadfolding.hpp"

void PhaseLoadFolding::optimize() {
  ciEnv* env = C->env();
  if (env->should_retain_local_variables() || env->jvmti_can_walk_any_space()) {
    // Give up because JVMTI can do wonders
    return;
  }

  // This phase is very fast, but it is still preferable not to allow potential unbounded
  // iterations
  for (int i = 0; i < 10; i++) {
    bool progress = do_optimize();
    if (!progress) {
      return;
    }
    _igvn.optimize();
    if (C->failing()) {
      return;
    }
  }
}

// The escape status of a node is visible in the memory graph. That is, at runtime, if a load 'l'
// from an object 'o' must be executed after an action 'a' that allows 'o' to escape, and in the
// IR graph, the node 'L' corresponding to 'l' consumes the address 'O' + c, with 'O' being the
// node corresponding to the newly allocated object 'o', then there must be a path along the
// use-def edges from the memory input of 'L' to the node 'A' that corresponds to 'a'.
//
// - If 'a' is a method invocation that receives 'o' as an argument, then in the graph, 'A' kills
//   all memory. As a result, the memory input of 'L' must be a transitive use of 'A'. This is
//   because in a well-behave memory graph, there is always a path of use-def edges from a memory
//   node to the previous bottom memory node. This is important as it ensures memory fences can
//   serialize memory operations by imposing use-def dependencies between the fence and the
//   surrounding memory nodes.
//   Example:
//       Integer o = new Integer(v);
//       int x = o.value;
//       if (flag) {
//         consume(o);
//         int y = o.value;
//       } else {
//         consume(null);
//         int z = o.value;
//       }
//       int t = o.value;
//   The memory graph will then look like:
//        NarrowMemProj (Integer.value)
//          |          |
//          |          |
//     CallJava(o) CallJava(null)
//          |          |
//          |          |
//        Proj1      Proj2
//           \       /
//            \     /
//              Phi
//   We can see that the object can be considered non-escape at NarrowMemProj, CallJava(null), and
//   Proj2, while it is considered escape at CallJava(o), Proj1, Phi. The loads x and z will be
//   from NarrowMemProj and Proj2, respectively, which means they can be considered loads from an
//   object that has not escaped, and we can fold them to v. On the other hand, the loads y and t
//   are from Proj1 and Phi, respectively, which means we cannot assume that the only value they
//   can see is v.
//
// - If 'a' is a store of 'o' into the memory, then 'l' must be executed after a iff:
//   + There is a memory fence that prevents 'l' from being executed before 'a'. Since a memory
//     fence kills all memory, the node 'F' corresponding to that fence must be a transitive use of
//     'A', and 'L' must be a transitive use of 'F', similar to case 1.
//   + There is a data dependency between 'l' and 'a'. In this case, there must be a path of
//     use-def edges from the memory input of 'L' to 'A', since the address input of 'L' only
//     depends on 'O'.
//     For example:
//       Integer o = new Integer(v);
//       *p = o;
//       Integer o_cloned = *p;
//       o_clone.value = u;
//       int x = o.value;
//     Then, there is a path of use-def edges:
//            Load(x = o.value)
//                   | (MemNode::Memory)
//                   v
//         Store(o_clone.value = u)
//                   | (MemNode::Address)
//                   V
//           Load(o_clone = *p)
//                   | (MemNode::Memory)
//                   v
//              Store(*p = o)
//     We can see that, we cannot fold x to v, because it must observe the value u, and we can
//     correcly detect that the object O has escaped by following the outputs of the store that
//     allows o to escape.
//
//   It is important to remind that even if 'l' is scheduled after the store 'a', unless there is a
//   memory fence between 'l' and 'a', it is generally not required that 'l' is executed after 'a'.
//   For example:
//       Integer o = new Integer(v);
//       *p = o;
//       int x = o.value;
//   In this case, even if the load x = o.value is declared after the store of o to p that allows o
//   to escape, it is valid for the load to actually happen before the store. As a result, we can
//   consider x = o.value to be a load from an object that has not escaped, and fold it to v.
bool PhaseLoadFolding::do_optimize() {
  bool progress = false;
  for (int macro_idx = 0; macro_idx < C->macro_count(); macro_idx++) {
    Node* macro = C->macro_node(macro_idx);
    if (!macro->is_Allocate()) {
      continue;
    }

    AllocateNode* alloc = macro->as_Allocate();
    Node* oop = alloc->result_cast();
    if (oop == nullptr) {
      continue;
    }

    if (process_allocate_result(oop)) {
      progress = true;
    }
  }
  return progress;
}

// Find all loads from oop such that their memory inputs have not observed the escape of oop, and
// try to find their corresponding stores
bool PhaseLoadFolding::process_allocate_result(Node* oop) {
  ResourceMark rm;
  Unique_Node_List candidates;
  VectorSet candidate_mems;

  collect_loads(candidates, candidate_mems, oop);
  if (candidate_mems.is_empty()) {
    return false;
  }

  WorkLists work_lists;
  process_candidates(candidate_mems, work_lists, oop);
  if (candidate_mems.is_empty()) {
    return false;
  }

  bool progress = false;
  for (uint candidate_idx = 0; candidate_idx < candidates.size(); candidate_idx++) {
    LoadNode* candidate = candidates.at(candidate_idx)->as_Load();
    if (!candidate_mems.test(candidate->in(MemNode::Memory)->_idx)) {
      continue;
    }

    work_lists.results.clear();
    Node* folded_value = try_fold_recursive(oop, candidate, candidate->in(MemNode::Memory), work_lists);
    if (folded_value != nullptr) {
      progress = true;
      _igvn.replace_node(candidate, folded_value);
    }
  }
  return progress;
}

// Collect all loads from oop
void PhaseLoadFolding::collect_loads(Unique_Node_List& candidates, VectorSet& candidate_mems, Node* oop) {
  assert(candidates.size() == 0 && candidate_mems.is_empty(), "must start with no candidates");
  for (DUIterator_Fast oop_out_max, oop_out_idx = oop->fast_outs(oop_out_max); oop_out_idx < oop_out_max; oop_out_idx++) {
    Node* out = oop->fast_out(oop_out_idx);
    if (!out->is_AddP()) {
      continue;
    }

    if (out->in(AddPNode::Base) != oop || out->in(AddPNode::Address) != oop || !out->in(AddPNode::Offset)->is_Con()) {
      // Only try to fold loads in the form of oop + C
      continue;
    }

    for (DUIterator_Fast addp_out_max, addp_out_idx = out->fast_outs(addp_out_max); addp_out_idx < addp_out_max; addp_out_idx++) {
      Node* addp_out = out->fast_out(addp_out_idx);
      if (addp_out->is_Load() && !addp_out->as_Load()->is_mismatched_access()) {
        candidates.push(addp_out);
      }
    }
  }

  for (uint i = 0; i < candidates.size(); i++) {
    candidate_mems.set(candidates.at(i)->in(MemNode::Memory)->_idx);
  }
}

// Find all nodes that observe the escape of oop. This function also finds stores that may store
// into oop. This is tricky, for example:
//     Integer o = new Integer(v);
//     Integer phi = o;
//     if (b) {
//       phi = new Integer(0);
//     }
//     phi.value = 1;
// Then, the store phi.value = 1 may or may not modify o, this cannot be known at compile time. As
// a result, when we walk the memory graph from a load, if we encounter such a store, we cannot
// know if it is the value we are looking for, and must give up.
void PhaseLoadFolding::process_candidates(VectorSet& candidate_mems, WorkLists& work_lists, Node* oop) {
  assert(work_lists.may_alias.is_empty() && work_lists.escapes.size() == 0 && work_lists.work_list.size() == 0, "must start with empty work lists");
  work_lists.work_list.push(oop);
  for (uint wl_idx = 0; wl_idx < work_lists.work_list.size(); wl_idx++) {
    // At runtime, n may be the same as oop, or may be a different value
    Node* n = work_lists.work_list.at(wl_idx);
    for (DUIterator_Fast out_max, out_idx = n->fast_outs(out_max); out_idx < out_max; out_idx++) {
      Node* out = n->fast_out(out_idx);
      if (out->is_ConstraintCast() || out->is_DecodeN() || out->is_EncodeP() ||
          out->is_Phi() || out->is_CMove()) {
        // All things that can alias n
        work_lists.work_list.push(out);
      } else if (out->is_AddP()) {
        AddPNode* addp = out->as_AddP();

        // A store that may or may not modify a field of oop (e.g. a store into a Phi which has oop
        // as one input, or a store into an element of oop at a variable index). This is
        // conservative, that is it must be true if the store may modify a field of oop but is not
        // in the form oop + C
        bool may_alias = false;
        if (out->in(AddPNode::Base) != oop || out->in(AddPNode::Address) != oop || !out->in(AddPNode::Offset)->is_Con()) {
          // Not an oop + C pointer
          may_alias = true;
        }

        for (DUIterator_Fast addp_out_max, addp_out_idx = addp->fast_outs(addp_out_max); addp_out_idx < addp_out_max; addp_out_idx++) {
          Node* addp_out = addp->fast_out(addp_out_idx);
          if ((addp_out->is_Store() || addp_out->is_LoadStore())) {
            assert(addp == addp_out->in(MemNode::Address), "store a derived pointer?");
            if (may_alias) {
              work_lists.may_alias.set(addp_out->_idx);
            }

            if (addp_out->is_LoadStore() || addp_out->as_Store()->is_mismatched_access()) {
              // Mismatched accesses are especially hard because they may lie in a different alias
              // class, so we may not encounter them when walking the memory graph. As a result, be
              // conservative and give up on all loads that may observe this store. LoadStores are
              // also lumped here because there is no LoadStoreNode::is_mismatched_access.
              work_lists.escapes.push(addp_out);
            }
          } else if (addp_out->is_Mem()) {
            // A load, does not affect the memory
          } else if (addp_out->is_AddP()) {
            // Another AddP, it should share the base with the current addp, so it will be visited
            // later
          } else {
            // Some runtime calls receive the pointer without the base
            work_lists.escapes.push(addp_out);
          }
        }
      } else if (out->is_Mem()) {
        // A store that may allow oop to escape
        if (out->req() > MemNode::ValueIn && n == out->in(MemNode::ValueIn)) {
          work_lists.escapes.push(out);
        }
      } else if (out->is_Call()) {
        // A call that may allow oop to escape
        if (!out->is_AbstractLock() && out->as_Call()->has_non_debug_use(n)) {
          work_lists.escapes.push(out);
        }
      } else if (out->is_SafePoint()) {
        // Non-call safepoints are pure control nodes
        continue;
      } else {
        // Be conservative with everything else
        work_lists.escapes.push(out);
      }
    }
  }

  // Propagate the escape status, if a node observes oop escaping, then all of its users also
  // observe that oop escapes
  for (uint idx = 0; idx < work_lists.escapes.size(); idx++) {
    Node* n = work_lists.escapes.at(idx);
    candidate_mems.remove(n->_idx);
    if (candidate_mems.is_empty()) {
      return;
    }

    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* out = n->fast_out(i);
      if (!out->is_Root()) {
        work_lists.escapes.push(out);
      }
    }
  }
}

// Try to find the store that a load observes. Since we know that oop has not escaped, we can
// inspect the graph aggressively, ignoring calls and memory barriers.
Node* PhaseLoadFolding::try_fold_recursive(Node* oop, LoadNode* candidate, Node* mem, WorkLists& work_lists) {
  Node* ptr = candidate->in(MemNode::Address);
  int alias_idx = C->get_alias_index(_igvn.type(ptr)->is_ptr());
  while (true) {
    // We may encounter a memory loop, so recording Phis are necessary
    if (work_lists.results.length() > int(mem->_idx)) {
      Node* res = work_lists.results.at(mem->_idx);
      if (res != nullptr) {
        return res;
      }
    }

    // If we encounter a store that we cannot decide if it modify the memory candidate loads from,
    // give up
    if (work_lists.may_alias.test(mem->_idx)) {
      return nullptr;
    }

    if (mem->is_MergeMem()) {
      mem = mem->as_MergeMem()->memory_at(alias_idx);
    } else if (mem->is_Phi()) {
      // Create a Phi for the result and store it in work_lists.results, this allows working with
      // cycles
      PhiNode* res = new PhiNode(mem->in(0), candidate->bottom_type());
      _igvn.register_new_node_with_optimizer(res);
      work_lists.results.at_put_grow(mem->_idx, res);
      for (uint i = 1; i < mem->req(); i++) {
        Node* phi_in = try_fold_recursive(oop, candidate, mem->in(i), work_lists);
        if (phi_in == nullptr) {
          return nullptr;
        }

        res->init_req(i, phi_in);
      }
      return res;
    } else if (mem->is_Proj()) {
      mem = mem->in(0);
    } else if (mem->is_MemBar()) {
      // Look through MemBars, only stop at the InitializeNode of oop
      if (!mem->is_Initialize() || mem != oop->in(0)->in(0)) {
        mem = mem->in(TypeFunc::Memory);
        continue;
      }

      InitializeNode* init = mem->as_Initialize();
      assert(ptr->is_AddP() && ptr->in(AddPNode::Base) == oop && ptr->in(AddPNode::Address) == oop && ptr->in(AddPNode::Offset)->is_Con(),
             "invalid pointer into a non-array object");

#ifdef _LP64
      Node* res = init->find_captured_store(ptr->in(AddPNode::Offset)->get_long(), candidate->memory_size(), &_igvn);
#else // _LP64
      Node* res = init->find_captured_store(ptr->in(AddPNode::Offset)->get_int(), candidate->memory_size(), &_igvn);
#endif // _LP64
      if (res == nullptr) {
        return nullptr;
      } else if (res->is_Proj() && res->in(0) == init->allocation()) {
        // Failure to find a captured store will return the memory output of the AllocateNode
        return _igvn.zerocon(candidate->value_basic_type());
      } else {
        return res->in(MemNode::ValueIn);
      }
    } else if (mem->is_SafePoint()) {
      mem = mem->in(TypeFunc::Memory);
    } else if (mem->is_Store()) {
      // We discarded all stores that may write into this field but does not have the form oop + C,
      // so a simple comparison of the address input is enough
      if (ptr == mem->in(MemNode::Address)) {
        return mem->in(MemNode::ValueIn);
      } else {
        mem = mem->in(MemNode::Memory);
      }
    } else {
      return nullptr;
    }
  }
}

/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc. All rights reserved.
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

#ifndef SHARE_NMT_VMATREE_HPP
#define SHARE_NMT_VMATREE_HPP

#include "memory/resourceArea.hpp"
#include "utilities/globalDefinitions.hpp"
#include "nmt/nmtTreap.hpp"
#include "runtime/os.hpp"
#include "utilities/growableArray.hpp"
#include "nmt/nmtNativeCallStackStorage.hpp"


class VMATree {
  static int addr_cmp(size_t a, size_t b);
public:
  enum class StateType : uint8_t {
    Reserved,
    Committed,
    Released
  };

  // Each node has some stack and a flag associated with it.
  struct Metadata {
    NativeCallStackStorage::StackIndex stack_idx;
    MEMFLAGS flag;

    Metadata()
      : stack_idx(),
        flag(mtNone) {
    }
    Metadata(NativeCallStackStorage::StackIndex stack_idx, MEMFLAGS flag)
      : stack_idx(stack_idx),
        flag(flag) {
    }
    static bool equals(const Metadata& a, const Metadata& b) {
      return NativeCallStackStorage::StackIndex::equals(a.stack_idx, b.stack_idx) &&
             a.flag == b.flag;
    }
  };

  struct Arrow {
    StateType type;
    Metadata data;

    void merge(const Arrow& b) {
      if (this->type == StateType::Released) {
        this->data.flag = b.data.flag;
        this->data.stack_idx = b.data.stack_idx;
      } else if (this->type == StateType::Committed) {
        this->data.flag = b.data.flag;
      }
    }
  };
  // A node has an arrow going into it and an arrow going out of it.
  struct NodeState {
    Arrow in;
    Arrow out;

    bool is_noop() {
      if (in.type == out.type) {
        if (out.type == StateType::Released) {
          return true;
        } else if (out.type == StateType::Committed) {
          return NativeCallStackStorage::StackIndex::equals(in.data.stack_idx, out.data.stack_idx);
        } else {
          return Metadata::equals(in.data, out.data);
        }
      } else {
        return false;
      }
    }
  };

  using VTreap = TreapNode<size_t, NodeState, addr_cmp>;
  TreapCHeap<size_t, NodeState, addr_cmp> tree;
  VMATree()
  : tree() {
  }

  struct SingleDiff {
    int64_t reserve;
    int64_t commit;
  };
  struct SummaryDiff {
    SingleDiff flag[mt_number_of_types];
    SummaryDiff() {
      for (int i = 0; i < mt_number_of_types; i++) {
        flag[i] = {0,0};
      }
    }
  };

private:
  // Utilities for the register_mapping function

  // Find the closest node which is LEQ (<=) to A.
  VTreap* closest_leq(size_t A);
  // Find the closest node which is GEQ (>=) to B.
  VTreap* closest_geq(size_t B);

public:
  template<typename Merge>
  SummaryDiff register_mapping(size_t A, size_t B, StateType state, Metadata& metadata, Merge merge) {
    // AddressState saves the necessary information for performing online summary accounting.
    struct AddressState {
      size_t address;
      NodeState state;
      MEMFLAGS flag_out() const {
        return state.out.data.flag;
      }
    };

    // We will need the nodes which are closest to A from the left side and closest to B from the right side.
    // Motivating example: reserve(0,100, mtNMT); reserve(50,75, mtTest);
    // This will require the 2nd call to know which region the second reserve 'smashes' a hole into for proper summary accounting.
    // LEQ_A is figured out a bit later on, as we need to find it for other purposes anyway.
    bool LEQ_A_found = false;
    AddressState LEQ_A;
    bool GEQ_B_found = false;
    AddressState GEQ_B;
    VTreap* geqB_n = closest_geq(B);
    if (geqB_n != nullptr) {
      GEQ_B = {geqB_n->key(), geqB_n->val()};
      GEQ_B_found = true;
    }

    SummaryDiff diff;
    NodeState stA{Arrow{StateType::Released, Metadata{}}, Arrow{state, metadata}};
    NodeState stB{Arrow{state, metadata}, Arrow{StateType::Released, Metadata{}}};
    // First handle A.
    // Find closest node that is LEQ A
    VTreap* leqA_n = closest_leq(A);
    if (leqA_n == nullptr) {
      // No match.
      if (stA.is_noop()) {
        // nothing to do.
      } else {
        // Add new node.
        tree.upsert(A, stA);
      }
    } else {
      LEQ_A_found = true;
      LEQ_A = AddressState{leqA_n->key(), leqA_n->val()};
      // Unless we know better, let B's outgoing state be the outgoing state of the node at or preceding A.
      // Consider the case where the found node is the start of a region enclosing [A,B)
      stB.out = leqA_n->val().out;

      // Direct address match.
      if (leqA_n->key() == A) {
        // Take over in state from old address.
        stA.in = leqA_n->val().in;

        // We may now be able to merge two regions:
        // If the node's old state matches the new, it becomes a noop. That happens, for example,
        // when expanding a committed area: commit [x1, A); ... commit [A, x3)
        // and the result should be a larger area, [x1, x3). In that case, the middle node (A and le_n)
        // is not needed anymore. So we just remove the old node.
        // We can only do this merge if the metadata is considered equivalent after merging.
        stA.out.merge(leqA_n->val().out);
        if (stA.is_noop()) {
          // invalidates leqA_n
          tree.remove(leqA_n->key());
          // Summary accounting: Not needed, we are only expanding
        } else {
          // If the state is not matching then we have different operations, such as:
          // reserve [x1, A); ... commit [A, x2); or
          // reserve [x1, A), flag1; ... reserve [A, x2), flag2; or
          // reserve [A, x1), flag1; ... reserve [A, x2), flag2;
          // then we re-use the existing out node, overwriting its old metadata.
          leqA_n->_value = stA;
        }
      } else {
        // The address must be smaller.
        assert(A > leqA_n->key(), "must be");

        // We add a new node, but only if there would be a state change. If there would not be a
        // state change, we just omit the node.
        // That happens, for example, when reserving within an already reserved region with identical metadata.
        stA.in.merge(leqA_n->val().out); // .. and the region's prior state is the incoming state
        if (stA.is_noop()) {
          // Nothing to do.
        } else {
          // Add new node.
          tree.upsert(A, stA);
        }
      }
    }

    // Now we handle B.
    // We first search all nodes that are (A, B]. All of these nodes
    // need to be deleted and summary accounted for. The last node before B determines B's outgoing state.
    // If there is no node between A and B, its A's incoming state.
    GrowableArrayCHeap<AddressState, mtNMT> to_be_deleted_inbetween_a_b;
    bool B_needs_insert = true;

    // Find all nodes between (A, B] and record their addresses. Also update B's
    // outgoing state.
    { // Iterate over each node which is larger than A
    GrowableArrayCHeap<VTreap*, mtNMT> to_visit;
      to_visit.push(tree.tree);
      VTreap* head = nullptr;
      while (!to_visit.is_empty()) {
        head = to_visit.pop();
        if (head == nullptr) continue;

        int cmp_A = addr_cmp(head->key(), A);
        int cmp_B = addr_cmp(head->key(), B);
        if (cmp_B > 0) {
          to_visit.push(head->left);
        } else if (cmp_A <= 0) {
          to_visit.push(head->right);
        } else if (cmp_A > 0 && cmp_B <= 0) {
          to_visit.push(head->left);
          to_visit.push(head->right);

          stB.out = head->val().out;
          if (cmp_B < 0) {
            // Record all nodes preceding B.
            to_be_deleted_inbetween_a_b.push({head->key(), head->val()});
          } else if (cmp_B == 0) {
            // Re-purpose B node, unless it would result in a noop node, in
            // which case record old node at B for deletion and summary accounting.
            stB.out.merge(head->val().out);
            if (stB.is_noop()) {
              to_be_deleted_inbetween_a_b.push(AddressState{B, head->val()});
            } else {
              head->_value = stB;
            }
            B_needs_insert = false;
          } else { /* Unreachable */}
        }
      }
    }
    // Insert B node if needed
    if (B_needs_insert    && // Was not already inserted
        (!stB.is_noop()     || // The operation is differing Or
         !Metadata::equals(stB.out.data, Metadata{})) // The metadata was changed from empty earlier
        ) {
      tree.upsert(B, stB);
    }

    // Finally, we need to:
    // 1. Perform summary accounting.
    // 2. Delete all nodes between (A, B]. Including B in the case of a noop.

    if (to_be_deleted_inbetween_a_b.length() == 0
        && LEQ_A_found && GEQ_B_found
        && GEQ_B.address >= B) {
      // We have smashed a hole in an existing region (or replaced it entirely).
      // LEQ_A - A - B - GEQ_B
      auto& rescom = diff.flag[NMTUtil::flag_to_index(LEQ_A.flag_out())];
      if (LEQ_A.state.out.type == StateType::Reserved) {
        rescom.reserve -= B - A;
      } else if (LEQ_A.state.out.type == StateType::Committed) {
        rescom.commit -= B - A;
        rescom.reserve -= B - A;
      }
    }

    AddressState prev = {A, stA}; // stA is just filler
    MEMFLAGS flag_in = LEQ_A.flag_out();
    while (to_be_deleted_inbetween_a_b.length() > 0) {
      const AddressState delete_me = to_be_deleted_inbetween_a_b.top();
      to_be_deleted_inbetween_a_b.pop();
      tree.remove(delete_me.address);
      auto& rescom = diff.flag[NMTUtil::flag_to_index(flag_in)];
      if (delete_me.state.in.type == StateType::Reserved) {
        rescom.reserve -= delete_me.address - prev.address;
      } else if (delete_me.state.in.type == StateType::Committed) {
        rescom.commit -= delete_me.address - prev.address;
        rescom.reserve -= delete_me.address - prev.address;
      }
      prev = delete_me;
      flag_in = delete_me.flag_out();
    }
    if (prev.address != A &&
        prev.state.out.type != StateType::Released &&
        GEQ_B.state.in.type != StateType::Released) {
      // There was some node inside of (A, B) and it is connected to GEQ_B
      // A - prev - B - GEQ_B
      // It might be that prev.address == B == GEQ_B.address, this is fine.
      if (prev.state.out.type == StateType::Reserved) {
        auto& rescom = diff.flag[NMTUtil::flag_to_index(prev.flag_out())];
        rescom.reserve -= B - prev.address;
      } else if (prev.state.out.type == StateType::Committed) {
        auto& rescom = diff.flag[NMTUtil::flag_to_index(prev.flag_out())];
        rescom.commit -= B - prev.address;
        rescom.reserve -= B - prev.address;
      }
    }

    // Finally, we can register the new region [A, B)'s summary data.
    auto& rescom = diff.flag[NMTUtil::flag_to_index(metadata.flag)];
    if (state == StateType::Reserved) {
      rescom.reserve += B - A;
    } else if (state == StateType::Committed) {
      rescom.commit += B - A;
      rescom.reserve += B - A;
    }
    return diff;
  }

  static Metadata identity_merge(Metadata& merge_into, const Metadata& existent) {
    return merge_into;
  }

  SummaryDiff reserve_mapping(size_t from, size_t sz, Metadata& metadata) {
    return register_mapping(from, from + sz, StateType::Reserved, metadata, identity_merge);
  }

  SummaryDiff commit_mapping(size_t from, size_t sz, Metadata& metadata) {
    return register_mapping(from, from + sz, StateType::Committed, metadata, [](Metadata& merge_into, const Metadata& existent) {
      // The committing API takes no flag, so we inherit the flag of the reserved region.
      merge_into.flag = existent.flag;
      return merge_into;
    });
  }

  SummaryDiff release_mapping(size_t from, size_t sz) {
    Metadata empty;
    return register_mapping(from, from + sz, StateType::Released, empty, [](Metadata& merge_into, const Metadata& existent) {
      // The releasing API takes no flag, so we inherit the flag of the reserved/committed region.
      // The releasing API also has no call stack, so we inherit the callstack also.
      merge_into.flag = existent.flag;
      merge_into.stack_idx = existent.stack_idx;
      return merge_into;
    });
  }

  // Visit all nodes between [from, to) and call f on them.
  template<typename F>
  void visit(size_t from, size_t to, F f) {
    ResourceArea area(mtNMT);
    ResourceMark rm(&area);
    GrowableArray<VTreap*> to_visit(&area, 16, 0, nullptr);
    to_visit.push(tree.tree);
    VTreap* head = nullptr;
    while (!to_visit.is_empty()) {
      head = to_visit.top();
      to_visit.pop();
      if (head == nullptr) continue;

      int cmp_from = addr_cmp(head->key(), from);
      int cmp_to = addr_cmp(head->key(), to);
      if (cmp_to >= 0) {
        return;
      }
      if (cmp_from >= 0) {
        if (cmp_to < 0) {
          f(head);
        }
        to_visit.push(head->left);
        to_visit.push(head->right);
      } else {
        to_visit.push(head->right);
      }
    }
  }
private:
  template<typename F>
  void in_order_traversal_doer(F f, const VTreap* node) const {
    if (node == nullptr) return;
    in_order_traversal_doer(f, node->left);
    f(node);
    in_order_traversal_doer(f, node->right);
  }
public:
  template<typename F>
  void in_order_traversal(F f) const {
    in_order_traversal_doer(f, tree.tree);
  }
};

#endif

/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "nmt/vmatree.hpp"
#include "utilities/growableArray.hpp"

VMATree::SummaryDiff VMATree::register_mapping(size_t A, size_t B, StateType state,
                                               Metadata& metadata) {
  // AddressState saves the necessary information for performing online summary accounting.
  struct AddressState {
    size_t address;
    IntervalChange state;
    MEMFLAGS flag_out() const {
      return state.out.metadata().flag;
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
  VTreap* geqB_n = tree.closest_geq(B);
  if (geqB_n != nullptr) {
    GEQ_B = {geqB_n->key(), geqB_n->val()};
    GEQ_B_found = true;
  }

  SummaryDiff diff;
  IntervalChange stA{
      IntervalState{StateType::Released, Metadata{}},
      IntervalState{              state,   metadata}
  };
  IntervalChange stB{
      IntervalState{              state,   metadata},
      IntervalState{StateType::Released, Metadata{}}
  };
  // First handle A.
  // Find closest node that is LEQ A
  VTreap* leqA_n = tree.closest_leq(A);
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
      stB.in = stA.out;
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
        leqA_n->val() = stA;
      }
    } else {
      // The address must be smaller.
      assert(A > leqA_n->key(), "must be");

      // We add a new node, but only if there would be a state change. If there would not be a
      // state change, we just omit the node.
      // That happens, for example, when reserving within an already reserved region with identical metadata.
      stA.in = leqA_n->val().out; // .. and the region's prior state is the incoming state
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
    to_visit.push(tree._root);
    VTreap* head = nullptr;
    while (!to_visit.is_empty()) {
      head = to_visit.pop();
      if (head == nullptr) continue;

      int cmp_A = AddressComparator::cmp(head->key(), A);
      int cmp_B = AddressComparator::cmp(head->key(), B);
      if (cmp_B > 0) {
        // head > B
        to_visit.push(head->left());
      } else if (cmp_A <= 0) {
        // head <= A
        to_visit.push(head->right());
      } else if (cmp_A > 0 && cmp_B <= 0) {
        // A < head <= B
        to_visit.push(head->left());
        to_visit.push(head->right());

        stB.out = head->val().out;
        if (cmp_B < 0) {
          // Record all nodes preceding B.
          to_be_deleted_inbetween_a_b.push({head->key(), head->val()});
        } else if (cmp_B == 0) {
          // Re-purpose B node, unless it would result in a noop node, in
          // which case record old node at B for deletion and summary accounting.
          if (stB.is_noop()) {
            to_be_deleted_inbetween_a_b.push(AddressState{B, head->val()});
          } else {
            head->val() = stB;
          }
          B_needs_insert = false;
        } else {
          assert(false, "Cannot happen.");
        }
      } else {
        // Impossible.
        assert(false, "Cannot happen.");
      }
    }
  }

  // Insert B node if needed
  if (B_needs_insert && // Was not already inserted
      !stB.is_noop())   // The operation is differing
    {
    tree.upsert(B, stB);
  }

  // Finally, we need to:
  // 1. Perform summary accounting.
  // 2. Delete all nodes between (A, B]. Including B in the case of a noop.

  if (to_be_deleted_inbetween_a_b.length() == 0 && LEQ_A_found && GEQ_B_found) {
    // We have smashed a hole in an existing region (or replaced it entirely).
    // LEQ_A - A - B - GEQ_B
    auto& rescom = diff.flag[NMTUtil::flag_to_index(LEQ_A.flag_out())];
    if (LEQ_A.state.out.type() == StateType::Reserved) {
      rescom.reserve -= B - A;
    } else if (LEQ_A.state.out.type() == StateType::Committed) {
      rescom.commit -= B - A;
      rescom.reserve -= B - A;
    }
  }

  // Sort them in address order, lowest first. This is for accounting purposes only.
  to_be_deleted_inbetween_a_b.sort([](AddressState* a, AddressState* b) -> int {
    return -AddressComparator::cmp(a->address, b->address);
  });

  AddressState prev = {A, stA}; // stA is just filler
  while (to_be_deleted_inbetween_a_b.length() > 0) {
    const AddressState delete_me = to_be_deleted_inbetween_a_b.top();
    to_be_deleted_inbetween_a_b.pop();
    // Delete node in (A, B]
    tree.remove(delete_me.address);
    // Perform summary accounting
    auto& rescom = diff.flag[NMTUtil::flag_to_index(delete_me.state.in.flag())];
    if (delete_me.state.in.type() == StateType::Reserved) {
      rescom.reserve -= delete_me.address - prev.address;
    } else if (delete_me.state.in.type() == StateType::Committed) {
      rescom.commit -= delete_me.address - prev.address;
      rescom.reserve -= delete_me.address - prev.address;
    }
    prev = delete_me;
  }

  if (prev.address != A && prev.state.out.type() != StateType::Released &&
      GEQ_B.state.in.type() != StateType::Released) {
    // There was some node inside of (A, B) and it is connected to GEQ_B
    // A - prev - B - GEQ_B
    // It might be that prev.address == B == GEQ_B.address, this is fine.
    if (prev.state.out.type() == StateType::Reserved) {
      auto& rescom = diff.flag[NMTUtil::flag_to_index(prev.flag_out())];
      rescom.reserve -= B - prev.address;
    } else if (prev.state.out.type() == StateType::Committed) {
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

/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc.
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

const VMATree::RegionData VMATree::empty_regiondata{NativeCallStackStorage::StackIndex{}, mtNone};

const char* VMATree::statetype_strings[3] = {
  "reserved", "committed", "released",
};

VMATree::SummaryDiff VMATree::register_mapping(position A, position B, StateType state,
                                               const RegionData& metadata) {
  if (A == B) {
    // A 0-sized mapping isn't worth recording.
    return SummaryDiff();
  }

  IntervalChange stA{
      IntervalState{StateType::Released, empty_regiondata},
      IntervalState{              state,   metadata}
  };
  IntervalChange stB{
      IntervalState{              state,   metadata},
      IntervalState{StateType::Released, empty_regiondata}
  };

  // First handle A.
  // Find closest node that is LEQ A
  bool LEQ_A_found = false;
  AddressState LEQ_A;
  TreapNode* leqA_n = _tree.closest_leq(A);
  if (leqA_n == nullptr) {
    // No match. We add the A node directly, unless it would have no effect.
    if (!stA.is_noop()) {
      _tree.upsert(A, stA);
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
        _tree.remove(leqA_n->key());
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
        _tree.upsert(A, stA);
      }
    }
  }

  // Now we handle B.
  // We first search all nodes that are (A, B]. All of these nodes
  // need to be deleted and summary accounted for. The last node before B determines B's outgoing state.
  // If there is no node between A and B, its A's incoming state.
  GrowableArrayCHeap<AddressState, mtNMT> to_be_deleted_inbetween_a_b;
  bool B_needs_insert = true;

  // Find all nodes between (A, B] and record their addresses and values. Also update B's
  // outgoing state.
  _tree.visit_range_in_order(A + 1, B + 1, [&](TreapNode* head) {
    int cmp_B = PositionComparator::cmp(head->key(), B);
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
    }
  });

  // Insert B node if needed
  if (B_needs_insert && // Was not already inserted
      !stB.is_noop())   // The operation is differing
    {
    _tree.upsert(B, stB);
  }

  // We now need to:
  // a) Delete all nodes between (A, B]. Including B in the case of a noop.
  // b) Perform summary accounting
  SummaryDiff diff;

  if (to_be_deleted_inbetween_a_b.length() == 0 && LEQ_A_found) {
    // We must have smashed a hole in an existing region (or replaced it entirely).
    // LEQ_A < A < B <= C
    SingleDiff& rescom = diff.flag[NMTUtil::flag_to_index(LEQ_A.out().flag())];
    if (LEQ_A.out().type() == StateType::Reserved) {
      rescom.reserve -= B - A;
    } else if (LEQ_A.out().type() == StateType::Committed) {
      rescom.commit -= B - A;
      rescom.reserve -= B - A;
    }
  }

  // Track the previous node.
  AddressState prev{A, stA};
  for (int i = 0; i < to_be_deleted_inbetween_a_b.length(); i++) {
    const AddressState delete_me = to_be_deleted_inbetween_a_b.at(i);
    _tree.remove(delete_me.address);

    // Perform summary accounting
    SingleDiff& rescom = diff.flag[NMTUtil::flag_to_index(delete_me.in().flag())];
    if (delete_me.in().type() == StateType::Reserved) {
      rescom.reserve -= delete_me.address - prev.address;
    } else if (delete_me.in().type() == StateType::Committed) {
      rescom.commit -= delete_me.address - prev.address;
      rescom.reserve -= delete_me.address - prev.address;
    }
    prev = delete_me;
  }

  if (prev.address != A && prev.out().type() != StateType::Released) {
    // The last node wasn't released, so it must be connected to a node outside of (A, B)
    // A - prev - B - (some node >= B)
    // It might be that prev.address == B == (some node >= B), this is fine.
    if (prev.out().type() == StateType::Reserved) {
      SingleDiff& rescom = diff.flag[NMTUtil::flag_to_index(prev.out().flag())];
      rescom.reserve -= B - prev.address;
    } else if (prev.out().type() == StateType::Committed) {
      SingleDiff& rescom = diff.flag[NMTUtil::flag_to_index(prev.out().flag())];
      rescom.commit -= B - prev.address;
      rescom.reserve -= B - prev.address;
    }
  }

  // Finally, we can register the new region [A, B)'s summary data.
  SingleDiff& rescom = diff.flag[NMTUtil::flag_to_index(metadata.flag)];
  if (state == StateType::Reserved) {
    rescom.reserve += B - A;
  } else if (state == StateType::Committed) {
    rescom.commit += B - A;
    rescom.reserve += B - A;
  }
  return diff;
}

void VMATree::walk_all_reserved_regions(VMATree::WalkedRegionClosure* closure) const {
  // Here, we walk all reserved regions in ascending order.
  // Starting from the first node, which has to be an opening node (Free->(reserved|committed),
  // we then search for the next node that constitutes a region boundary. A region boundary is
  // any node that either changes state back to Free, or a node that changes region data (new
  // flag or new stack). In other words, we ignore any node that just changes the state between
  // reserved and committed.
  VMATree::TreapNode* start = nullptr;
  auto walker = [&start, closure](TreapNode* n) {
    const StateType state_in = n->val().in.type();
    const StateType state_out = n->val().out.type();
    const RegionData data_in = n->val().in.regiondata();
    const RegionData data_out = n->val().out.regiondata();
    if (start == nullptr) {
      // We expect to be in free area, going into either committed right away, or reserved
      assert(state_in == StateType::Released, "sanity");
      assert(state_out != StateType::Released, "sanity");
      start = n;
    } else {
      // Is this a reserved region boundary?
      if (state_in == StateType::Released || state_out == StateType::Released ||
          !RegionData::equals(data_in, data_out)) {
        const RegionData data = start->val().out.regiondata();
        assert(RegionData::equals(data, n->val().in.regiondata()), "in and out data must match");
        // call closure
        WalkedRegion r;
        // We cannot just copy-assign RegionData because StackIndex is not copyable
        // and its a whole rabbit hole of C++ pain.
        memcpy(&r.data, &data, sizeof(RegionData));
        r.from = start->key();
        r.to = n->key();
        if (!closure->do_region(&r)) { // closure wants us to stop
          return;
        }
        // If the new state is free, we are in free region now, otherwise
        // this node is the end of an old and the start of a new region
        start = (state_out == StateType::Released) ? nullptr : n;
      }
    }
  };
  visit_in_order(walker);
}

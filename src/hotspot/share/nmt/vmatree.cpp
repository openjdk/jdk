/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "logging/log.hpp"
#include "nmt/vmatree.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

const VMATree::RegionData VMATree::empty_regiondata{NativeCallStackStorage::StackIndex{}, mtNone};

const char* VMATree::statetype_strings[3] = {
  "reserved", "committed", "released",
};

VMATree::SummaryDiff VMATree::register_mapping(position A, position B, StateType state,
                                               const RegionData& metadata, bool use_tag_inplace) {
  assert(!use_tag_inplace || metadata.mem_tag == mtNone,
         "If using use_tag_inplace, then the supplied tag should be mtNone, was instead: %s", NMTUtil::tag_to_name(metadata.mem_tag));
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
    assert(!use_tag_inplace, "Cannot use the tag inplace if no pre-existing tag exists. From: " PTR_FORMAT " To: " PTR_FORMAT, A, B);
    if (use_tag_inplace) {
      log_debug(nmt)("Cannot use the tag inplace if no pre-existing tag exists. From: " PTR_FORMAT " To: " PTR_FORMAT, A, B);
    }
    // No match. We add the A node directly, unless it would have no effect.
    if (!stA.is_noop()) {
      _tree.upsert(A, stA);
    }
  } else {
    LEQ_A_found = true;
    LEQ_A = AddressState{leqA_n->key(), leqA_n->val()};
    StateType leqA_state = leqA_n->val().out.type();
    StateType new_state = stA.out.type();
    // If we specify use_tag_inplace then the new region takes over the current tag instead of the tag in metadata.
    // This is important because the VirtualMemoryTracker API doesn't require supplying the tag for some operations.
    if (use_tag_inplace) {
      assert(leqA_n->val().out.type() != StateType::Released, "Should not use inplace the tag of a released region");
      MemTag tag = leqA_n->val().out.mem_tag();
      stA.out.set_tag(tag);
      stB.in.set_tag(tag);
    }

    // Unless we know better, let B's outgoing state be the outgoing state of the node at or preceding A.
    // Consider the case where the found node is the start of a region enclosing [A,B)
    stB.out = out_state(leqA_n);

    // Direct address match.
    if (leqA_n->key() == A) {
      // Take over in state from old address.
      stA.in = in_state(leqA_n);

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
        // reserve [x1, A), mem_tag1; ... reserve [A, x2), mem_tag2; or
        // reserve [A, x1), mem_tag1; ... reserve [A, x2), mem_tag2;
        // then we re-use the existing out node, overwriting its old metadata.
        leqA_n->val() = stA;
      }
    } else {
      // The address must be smaller.
      assert(A > leqA_n->key(), "must be");

      // We add a new node, but only if there would be a state change. If there would not be a
      // state change, we just omit the node.
      // That happens, for example, when reserving within an already reserved region with identical metadata.
      stA.in = out_state(leqA_n); // .. and the region's prior state is the incoming state
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
    stB.out = out_state(head);
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
    SingleDiff& rescom = diff.tag[NMTUtil::tag_to_index(LEQ_A.out().mem_tag())];
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
    SingleDiff& rescom = diff.tag[NMTUtil::tag_to_index(delete_me.in().mem_tag())];
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
      SingleDiff& rescom = diff.tag[NMTUtil::tag_to_index(prev.out().mem_tag())];
      rescom.reserve -= B - prev.address;
    } else if (prev.out().type() == StateType::Committed) {
      SingleDiff& rescom = diff.tag[NMTUtil::tag_to_index(prev.out().mem_tag())];
      rescom.commit -= B - prev.address;
      rescom.reserve -= B - prev.address;
    }
  }

  // Finally, we can register the new region [A, B)'s summary data.
  SingleDiff& rescom = diff.tag[NMTUtil::tag_to_index(stA.out.mem_tag())];
  if (state == StateType::Reserved) {
    rescom.reserve += B - A;
  } else if (state == StateType::Committed) {
    rescom.commit += B - A;
    rescom.reserve += B - A;
  }
  return diff;
}

#ifdef ASSERT
void VMATree::print_on(outputStream* out) {
  visit_in_order([&](TreapNode* current) {
    out->print("%zu (%s) - %s - ", current->key(), NMTUtil::tag_to_name(out_state(current).mem_tag()),
               statetype_to_string(out_state(current).type()));
  });
  out->cr();
}
#endif

VMATree::SummaryDiff VMATree::set_tag(const position start, const size size, const MemTag tag) {
  auto pos = [](TreapNode* n) { return n->key(); };
  position from = start;
  position end  = from+size;
  size_t remsize = size;
  VMATreap::Range range(nullptr, nullptr);

  // Find the next range to adjust and set range, remsize and from
  // appropriately. If it returns false, there is no valid next range.
  auto find_next_range = [&]() -> bool {
    range = _tree.find_enclosing_range(from);
    if ((range.start == nullptr && range.end == nullptr) ||
        (range.start != nullptr && range.end == nullptr)) {
      // There is no range containing the starting address
      assert(range.start->val().out.type() == StateType::Released, "must be");
      return false;
    } else if (range.start == nullptr && range.end != nullptr) {
      position found_end = pos(range.end);
      if (found_end >= end) {
        // The found address is outside of our range, we can end now.
        return false;
      }
      // There is at least one range [found_end, ?) which starts within [start, end)
      // Use this as the range instead.
      range = _tree.find_enclosing_range(found_end);
      remsize = end - found_end;
      from = found_end;
    }
    return true;
  };

  bool success = find_next_range();
  if (!success) return SummaryDiff();
  assert(range.start != nullptr && range.end != nullptr, "must be");

  end = MIN2(from + remsize, pos(range.end));
  IntervalState& out = out_state(range.start);
  StateType type = out.type();

  SummaryDiff diff;
  // Ignore any released ranges, these must be mtNone and have no stack
  if (type != StateType::Released) {
    RegionData new_data = RegionData(out.stack(), tag);
    SummaryDiff result = register_mapping(from, end, type, new_data);
    diff.add(result);
  }

  remsize = remsize - (end - from);
  from = end;

  // If end < from + sz then there are multiple ranges for which to set the flag.
  while (end < from + remsize) {
    // Using register_mapping may invalidate the already found range, so we must
    // use find_next_range repeatedly
    bool success = find_next_range();
    if (!success) return diff;
    assert(range.start != nullptr && range.end != nullptr, "must be");

    end = MIN2(from + remsize, pos(range.end));
    IntervalState& out = out_state(range.start);
    StateType type = out.type();

    if (type != StateType::Released) {
      RegionData new_data = RegionData(out.stack(), tag);
      SummaryDiff result = register_mapping(from, end, type, new_data);
      diff.add(result);
    }
    remsize = remsize - (end - from);
    from = end;
  }

  return diff;
}

#ifdef ASSERT
void VMATree::SummaryDiff::print_on(outputStream* out) {
  for (int i = 0; i < mt_number_of_tags; i++) {
    if (tag[i].reserve == 0 && tag[i].commit == 0) {
      continue;
    }
    out->print_cr("Tag %s R: " INT64_FORMAT " C: " INT64_FORMAT, NMTUtil::tag_to_enum_name((MemTag)i), tag[i].reserve,
                  tag[i].commit);
  }
}
#endif

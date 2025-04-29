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

const VMATree::RegionData VMATree::empty_regiondata{NativeCallStackStorage::invalid, mtNone};

const char* VMATree::statetype_strings[3] = {
  "reserved", "committed", "released",
};

NativeCallStackStorage::StackIndex VMATree::get_new_reserve_callstack(NativeCallStackStorage::StackIndex es, StateType ex, const RequestInfo& req){
  using SIndex = NativeCallStackStorage::StackIndex;
  const SIndex ES = NativeCallStackStorage::invalid; // Empty Stack
  const SIndex rq = req.callstack;
  auto st_to_index = [&](StateType st) -> int {
    return
      st == StateType::Released ? 0 :
      st == StateType::Reserved ? 1 :
      st == StateType::Committed ? 2 : -1;
  };
  const int op = req.op_to_index();
  assert(op >= 0 && op < 4, "should be");
                            // existing state
  SIndex result[4][3] = {// Rl  Rs   C
                           {ES, ES, ES},   // op == Release
                           {rq, rq, rq},   // op == Reserve
                           {es, es, es},   // op == Commit
                           {es, es, es}    // op == Uncommit
                           };
  return result[op][st_to_index(ex)];
}

NativeCallStackStorage::StackIndex VMATree::get_new_commit_callstack(NativeCallStackStorage::StackIndex es, StateType ex, const RequestInfo& req){
  using SIndex = NativeCallStackStorage::StackIndex;
  const SIndex ES = NativeCallStackStorage::invalid; // Empty Stack
  const SIndex rq = req.callstack;
  auto st_to_index = [&](StateType st) -> int {
    return
      st == StateType::Released ? 0 :
      st == StateType::Reserved ? 1 :
      st == StateType::Committed ? 2 : -1;
  };
  const int op = req.op_to_index();
  assert(op >= 0 && op < 4, "should be");
                            // existing state
  SIndex result[4][3] = {// Rl  Rs   C
                           {ES, ES, ES},   // op == Release
                           {ES, ES, ES},   // op == Reserve
                           {rq, rq, rq},   // op == Commit
                           {ES, ES, ES}    // op == Uncommit
                           };
  return result[op][st_to_index(ex)];
}

VMATree::StateType VMATree::get_new_state(StateType ex, const RequestInfo& req) {
  const StateType Rl = StateType::Released;
  const StateType Rs = StateType::Reserved;
  const StateType C = StateType::Committed;
  auto st_to_index = [&](StateType st) -> int {
    return
      st == StateType::Released ? 0 :
      st == StateType::Reserved ? 1 :
      st == StateType::Committed ? 2 : -1;
  };
  const int op = req.op_to_index();
  assert(op >= 0 && op < 4, "should be");
                            // existing state
  StateType result[4][3] = {// Rl  Rs   C
                              {Rl, Rl, Rl},   // op == Release
                              {Rs, Rs, Rs},   // op == Reserve
                              { C,  C,  C},   // op == Commit
                              {Rl, Rs, Rs}    // op == Uncommit
                           };
  return result[op][st_to_index(ex)];
}

void VMATree::compute_summary_diff(SingleDiff::delta region_size, MemTag t1, const StateType& ex, const RequestInfo& req, MemTag t2, SummaryDiff& diff) {
  const StateType Rl = StateType::Released;
  const StateType Rs = StateType::Reserved;
  const StateType C = StateType::Committed;
  auto st_to_index = [&](StateType st) -> int {
    return
      st == StateType::Released ? 0 :
      st == StateType::Reserved ? 1 :
      st == StateType::Committed ? 2 : -1;
  };
  const int op = req.op_to_index();
  assert(op >= 0 && op < 4, "should be");

  SingleDiff::delta a = region_size;
  // A region with size `a` has a state as <column> and an operation is requested as in <row>
  // The region has tag `t1` and the operation has tag `t2`.
  // For each state, we decide how much to be added/subtracted from t1 to t2. Two tables for reserve and commit.
  // Each pair of <x,y> in the table means add `x` to t1 and add `y` to t2. There are 3 pairs in each row for 3 states.
  // For example, `reserve[1][4,5]` says `-a,a` means:
  //    - we are reserving with t2 a region which is already commited with t1
  //    - since we are reserving, then `a` will be added to t2. (`y` is `a`)
  //    - since we uncommitting (by reserving) then `a` is to be subtracted from t1. (`x` is `-a`).
  //    - amount of uncommitted size is in table `commit[1][4,5]` which is `-a,0` that means subtract `a` from t1.
                                       // existing state
  SingleDiff::delta reserve[4][3*2] = {// Rl    Rs     C
                                         {0,0, -a,0, -a,0 },   // op == Release
                                         {0,a, -a,a, -a,a },   // op == Reserve
                                         {0,a,  0,0,  0,0 },   // op == Commit
                                         {0,0,  0,0,  0,0 }    // op == Uncommit
                                      };
  SingleDiff::delta commit[4][3*2] = {// Rl    Rs     C
                                        {0,0,  0,0, -a,0 },   // op == Release
                                        {0,a,  0,a, -a,0 },   // op == Reserve
                                        {0,a,  0,a, -a,a },   // op == Commit
                                        {0,0,  0,0, -a,0 }    // op == Uncommit
                                     };
  SingleDiff& from_rescom = diff.tag[NMTUtil::tag_to_index(t1)];
  SingleDiff&   to_rescom = diff.tag[NMTUtil::tag_to_index(t2)];
  int st = st_to_index(ex);
  tty->print_cr("%d %d %d %d %ld %ld", (int)t1, (int)t2, op, st, from_rescom.reserve, to_rescom.reserve);
  from_rescom.reserve += reserve[op][st * 2    ];
    to_rescom.reserve += reserve[op][st * 2 + 1];
  from_rescom.commit  +=  commit[op][st * 2    ];
    to_rescom.commit  +=  commit[op][st * 2 + 1];

}

void VMATree::update_region(TreapNode* n1, TreapNode* n2, const RequestInfo& req, SummaryDiff& diff) {
  using SIndex = NativeCallStackStorage::StackIndex;
  IntervalState exSt; // existing state info
  assert(n1 != nullptr,"sanity");
  assert(n2 != nullptr,"sanity");
  if (n1->key() == req.A) {
    exSt = n1->val().in;
  } else {
    exSt = n1->val().out;
  }

  StateType existing_state              = exSt.type();
  MemTag    existing_tag                = exSt.mem_tag();
  SIndex    existing_reserve_callstack  = exSt.reserved_stack();
  SIndex    existing_commit_callstack   = exSt.committed_stack();

  StateType new_state                   = get_new_state(existing_state, req);
  MemTag    new_tag                     = req.use_tag_inplace ? existing_tag : req.tag;
  SIndex    new_reserve_callstack       = get_new_reserve_callstack(existing_reserve_callstack, existing_state, req);
  SIndex    new_committ_callstack       = get_new_reserve_callstack(existing_reserve_callstack, existing_state, req);

  n1->val().out.set_tag(new_tag);
  n1->val().out.set_type(new_state);
  n1->val().out.set_commit_stack(new_committ_callstack);
  n1->val().out.set_reserve_stack(new_reserve_callstack);

  n2->val().in.set_tag(new_tag);
  n2->val().in.set_type(new_state);
  n2->val().in.set_commit_stack(new_committ_callstack);
  n2->val().in.set_reserve_stack(new_reserve_callstack);

  SingleDiff::delta region_size = n2->key() - n1->key();
  compute_summary_diff(region_size, existing_tag, existing_state, req, new_tag, diff);
}


VMATree::SummaryDiff VMATree::register_mapping(position A, position B, StateType state,
                                               const RegionData& metadata, bool use_tag_inplace) {

  if (A == B) {
    return SummaryDiff();
  }
  assert(A < B, "should be");
  SummaryDiff diff;
  RequestInfo req{A, B, state, metadata.mem_tag, metadata.stack_idx, use_tag_inplace};
  IntervalChange stA{
      IntervalState{StateType::Released, empty_regiondata},
      IntervalState{              state,   metadata}
  };
  IntervalChange stB{
      IntervalState{              state,   metadata},
      IntervalState{StateType::Released, empty_regiondata}
  };
  VMATreap::Range rA = _tree.find_enclosing_range(A);
  VMATreap::Range rB = _tree.find_enclosing_range(B);
  // nodes:          .....X.......Y...Z......W........U
  // request:                 A------------------B
  // X,Y = enclosing_nodes(A)
  // W,U = enclosing_nodes(B)
  // The cases are whether or not X and Y exists and X == A. (A == Y doesn't happen since it is searched by 'lt' predicate)
  // The cases are whether or not W and U exists and W == B. (B == U doesn't happen since it is searched by 'lt' predicate)

  // We update regions in 3 sections: 1) X..A..Y, 2) Y....W, 3) W..B..U
  // The regons in [Y,W) are updated in a loop. We update X..A..Y before the loop and W..B..U after the loop.
  // The table below summarizes the cases and what to do.
  // 'update'  for a region [a,b) means call 'update_region(node a, node b, req, diff)' to update the region based on existing State and the request.


  //                                                                                              Regions before loop                                         Regions in
  //                                                             X exists     Y exists    X == A   [X,A)         [A,Y)    remove X if        upsert A if       the loop                                    to do after loop
  //                                                             --------     --------    ------   ------        -----    ---------------    --------------   -------------  ----------------------------------------------------------------
  // row  0: nodes:          .........A..................B.....  no             no        --       --            --        --                 !A_is_noop()        --
  // row  1: nodes:          .....X...A..................B.....  yes            no        no       A.in = X.out  --        --                 !A_is_noop()        --
  // row  2: nodes:          .....XA.....................B.....  yes            no        yes      A.in = X.in   --        X.in == A.out      !remove_X           --
  // row  3: nodes:          .........A...Y...Z......W...B....U  no             yes       --       --            update    --                 !A_is_noop()       [Y,W)
  // row  4: nodes:          .....X...A...Y...Z......W...B....U  yes            yes       no       A.in = X.out  update    --                 !A_is_noop()       [Y,W)
  // row  5: nodes:          .....XA......Y...Z......W...B....U  yes            yes       yes      A.in = X.in   update    X.in == A.out      !remove_X          [Y,W)
  //       :
  //       :                                                     W exists     U exists    W == B                                                                             [W,B)         [B,U)            remove W if       upsert B if
  //       :                                                     --------     --------    ------   ------------------------------------------------------------------------  -----         ------          ----------------  ---------------
  // row  6: nodes:          .........A..................B.....   no            no          --                                                                                --            --              --                !B.is_noop()
  // row  7: nodes:          .........A..................B....U   no            yes         --                                                                                --           B.out = U.in     --                !B.is_noop()
  // row  8: nodes:          .....X...A...Y...Z......W...B.....   yes           no          no                                                                                update        --              W.is_noop()       !B.is_noop()
  // row  9: nodes:          .....X...A...Y...Z......WB........   yes           no          yes                                                                               --            --              W.in == B.out     !remove_W
  // row 10: nodes:          .....X...A...Y...Z......W...B....U   yes           yes         no                                                                                update       B.out = U.in     W.is_noop()       !B.is_noop()
  // row 11: nodes:          .....X...A...Y...Z......WB.......U   yes           yes         yes                                                                               --            --              W.in == B.out     !remove_W

  // We intentionally did not summarize/compress the cases to have them as separate. This expanded way of describing the cases helps us to understand/analyze/verify/debug/maintain the corresponding code more easily.
  // Mapping of table to row, row to switch-case, 'what to do' to code should be consistent. If one changes, the others have to be updated accordingly.
  // The sequence of dependecies is: table -> row no -> switch(row)-case -> code. Meaning that whenever any of one item in this sequence is changed, the rest of the consequent items to be checked/changed.

  TreapNode* X = rA.start;
  TreapNode* Y = rA.end;
  TreapNode nA{A, stA, 0}; // the node that represents A
  bool X_exists = X != nullptr;
  bool Y_exists = Y != nullptr;
  bool X_eq_A = X_exists && rA.start->key() == A;
  int row = -1;
  if (!X_exists && !Y_exists           ) { row = 0; }
  if ( X_exists && !Y_exists && !X_eq_A) { row = 1; }
  if ( X_exists && !Y_exists &&  X_eq_A) { row = 2; }
  if (!X_exists &&  Y_exists           ) { row = 3; }
  if (!X_exists &&  Y_exists && !X_eq_A) { row = 4; }
  if ( X_exists &&  Y_exists &&  X_eq_A) { row = 5; }

  // ************************************************************************************ Before loop
  switch(row) {
    case 0:
      if (!stA.is_noop()) { _tree.upsert(A, stA); }
      break;
    case 1:
      stA.in = X->val().out;
      if (!stA.is_noop()) { _tree.upsert(A, stA); }
      break;
    case 2:
      stA.in = X->val().in;
      if (X->val().in.equals(stA.out)) {
        _tree.remove(X->key());
      } else {
        _tree.upsert(A, stA);
      }
      break;
    case 3:
      update_region(&nA, Y, req, diff);
      if (!nA.val().is_noop()) { _tree.upsert(A, nA.val()); }
      break;
    case 4:
      stA.in = X->val().out;
      update_region(&nA, Y, req, diff);
      if (!nA.val().is_noop()) { _tree.upsert(A, nA.val()); }
      break;
    case 5:
      stA.in = X->val().in;
      update_region(&nA, Y, req, diff);
      if (X->val().in.equals(stA.out)) {
        _tree.remove(X->key());
      } else {
        _tree.upsert(A, nA.val());
      }
      break;
    default:
      break;
  }

  // ************************************************************************************ Loop
  GrowableArrayCHeap<position, mtNMT> to_be_removed;
  TreapNode* prev = nullptr;
  _tree.visit_range_in_order(Y->key(), B + 1, [&](TreapNode* curr){
    if (prev != nullptr) {
      update_region(prev, curr, req, diff);
      // during visit, structure of the tree should not be changed
      // keep the keys to be removed, and remove them later
      if (prev->val().is_noop()) {
        to_be_removed.push(prev->key());
      }
    }
    prev = curr;
  });

  // ************************************************************************************ After loop
  TreapNode* W = rB.start;
  TreapNode* U = rB.end;
  TreapNode nB{B, stB, 0}; // the node that represents B
  bool W_exists = W != nullptr;
  bool U_exists = U != nullptr;
  bool W_eq_B = W_exists && W->key() == B;
  if (!W_exists && !U_exists           ) { row = 6; }
  if (!W_exists &&  U_exists           ) { row = 7; }
  if ( W_exists && !U_exists && !W_eq_B) { row = 8; }
  if ( W_exists && !U_exists &&  W_eq_B) { row = 9; }
  if ( W_exists &&  U_exists && !W_eq_B) { row = 10; }
  if ( W_exists &&  U_exists &&  W_eq_B) { row = 11; }
  switch(row) {
    case 6:
      if (!stB.is_noop()) { _tree.upsert(B, stB); }
      break;
    case 7:
      stB.out = U->val().in;
      if (!stB.is_noop()) { _tree.upsert(B, stB); }
      break;
    case 8:
      update_region(W, &nB, req, diff);
      if (W->val().is_noop()) { _tree.remove(W->key()); }
      if (!nB.val().is_noop()) { _tree.upsert(B, nB.val()); }
      break;
    case 9:
      if (W->val().in.equals(stB.out)) {
        _tree.remove(W->key());
      } else {
        _tree.upsert(B, stB);
      }
      break;
    case 10:
      stB.out = U->val().in;
      update_region(W, &nB, req, diff);
      if (W->val().in.equals(stB.out)) {
        _tree.remove(W->key());
      } else {
        _tree.upsert(B, stB);
      }
      break;
    case 11:
      if (W->val().in.equals(stB.out)) {
        _tree.remove(W->key());
      } else {
        _tree.upsert(B, stB);
      }
      break;
    default:
      break;
  }


  // ************************************************************************************ Delete noop nodes found in the loop
  while(to_be_removed.length() != 0) {
    _tree.remove(to_be_removed.pop());
  }

  return diff;
}
VMATree::SummaryDiff VMATree::register_mapping_new(position A, position B, StateType state,
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

  bool is_reserve_operation = state == StateType::Reserved && !use_tag_inplace;
  bool is_uncommit_operation = state == StateType::Reserved && use_tag_inplace;
  bool is_commit_operation = state == StateType::Committed;
  stA.out.set_reserve_stack(NativeCallStackStorage::invalid);
  stB.in.set_reserve_stack(NativeCallStackStorage::invalid);
  stA.out.set_commit_stack(NativeCallStackStorage::invalid);
  stA.in.set_commit_stack(NativeCallStackStorage::invalid);
  if (is_reserve_operation) {
    stA.out.set_reserve_stack(metadata.stack_idx);
    stB.in.set_reserve_stack(metadata.stack_idx);
  }
  if (is_commit_operation) {
    stA.out.set_commit_stack(metadata.stack_idx);
    stB.in.set_commit_stack(metadata.stack_idx);
  }
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
    stA.out.set_reserve_stack(metadata.stack_idx);
    stB.in.set_reserve_stack(metadata.stack_idx);

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
      if (is_commit_operation) {
        if (leqA_n->val().out.has_reserved_stack()) {
          stA.out.set_reserve_stack(leqA_n->val().out.reserved_stack());
        } else {
          stA.out.set_reserve_stack(metadata.stack_idx);
        }
      }
      if (is_uncommit_operation) {
        stA.out.set_reserve_stack(leqA_n->val().out.reserved_stack());
        stA.out.set_commit_stack(NativeCallStackStorage::invalid);
      }
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
      if (is_commit_operation) {
        if (leqA_n->val().out.has_reserved_stack()) {
          stA.out.set_reserve_stack(leqA_n->val().out.reserved_stack());
          stB.in.set_reserve_stack(leqA_n->val().out.reserved_stack());
        } else {
          stA.out.set_reserve_stack(metadata.stack_idx);
          stB.in.set_reserve_stack(metadata.stack_idx);
        }
      }
      if (is_uncommit_operation) {
        stA.out.set_reserve_stack(leqA_n->val().out.reserved_stack());
        stB.in.set_reserve_stack(leqA_n->val().out.reserved_stack());
      }

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
    out->print("%zu (%s) - %s [%d, %d]- ", current->key(), NMTUtil::tag_to_name(out_state(current).mem_tag()),
               statetype_to_string(out_state(current).type()), current->val().out.reserved_stack(), current->val().out.committed_stack());

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
    RegionData new_data = RegionData(out.reserved_stack(), tag);
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
      RegionData new_data = RegionData(out.reserved_stack(), tag);
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

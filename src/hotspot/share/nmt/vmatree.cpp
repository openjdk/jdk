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


// Semantics
// This tree is used to store and track the state of virtual memory regions.
// The nodes in the tree are key-value pairs where the key is the memory address and the value is the State of the memory regions.
// The State of a region describes whether the region is released, reserved or committed, which MemTag it has and where in
// Hotspot (using call-stacks) it is reserved or committed.
// Each node holds the State of the regions to its left and right. Each memory region is described by two
// memory addresses for its start and end.
// For example, to describe the region that starts at memory address 0xA000 with size 0x1000, there will be two nodes
// with the keys 0xA000 (node A) and 0xB000 (node B) in the tree. The value of the key-value pairs of node A and
// node B describe the region's State, using right of A and left of B (<--left--A--right-->.....<--left--B--right-->...).
//
// Virtual memory can be reserved, committed, uncommitted and released. For each operation a request
// (<from-address, to-address, operation, tag, call-stack, which-tag-to-use >) is sent to the tree to handle.
//
// The expected changes are described here for each operation:
//
// ### Reserve a region
// When a region is reserved, all the overlapping regions in the tree should:
//   - be marked as Reserved
//   - take MemTag of the operation
//   - store call-stack of the request to the reserve call-stack
//   - clear commit call-stack
//
// ### Commit a region
// When a region is committed, all the overlapping regions in the tree should:
//   - be marked as Committed
//   - take MemTag of the operation or MemTag of the existing region, depends on which-tag-to-use in the request
//   - if the region is in Released state
//     - mark the region as both Reserved and Committed
//     - store the call-stack of the request to the reserve call-stack
//   - store the call-stack of the request to the commit call-stack
//
// ### Uncommit a region
// When a region is uncommitted, all the overlapping regions in the tree should:
//   - be ignored if the region is in Released state
//   - be marked as Reserved
//   - not change the MemTag
//   - not change the reserve call-stack
//   - clear commit call-stack
//
// ### Release a region
// When a region is released, all the overlapping regions in the tree should:
//   - be marked as Released
//   - set the MemTag to mtNone
//   - clear both reserve and commit call-stack
//
// ---  Accounting
// After each operation, the tree should be able to report how much memory is reserved or committed per MemTag.
// So for each region that changes to a new State, the report should contain (separately for each tag) the amount
// of reserve and commit that are changed (increased or decreased) due to the operation.

const VMATree::RegionData VMATree::empty_regiondata{NativeCallStackStorage::invalid, mtNone};

const char* VMATree::statetype_strings[4] = {
  "released","reserved", "only-committed", "committed",
};

VMATree::SIndex VMATree::get_new_reserve_callstack(const SIndex es, const StateType ex, const RequestInfo& req) const {
  const SIndex ES = NativeCallStackStorage::invalid; // Empty Stack
  const SIndex rq = req.callstack;
  const int op = req.op_to_index();
  const Operation oper = req.op();
  assert(op >= 0 && op < 4, "should be");
  assert(op >= 0 && op < 4, "should be");
                            // existing state
  SIndex result[4][3] = {// Rl  Rs   C
                           {ES, ES, ES},   // op == Release
                           {rq, rq, rq},   // op == Reserve
                           {es, es, es},   // op == Commit
                           {es, es, es}    // op == Uncommit
                           };
  // When committing a Released region, the reserve-call-stack of the region should also be as what is in the request
  if (oper == Operation::Commit && ex == StateType::Released) {
    return rq;
  } else {
    return result[op][state_to_index(ex)];
  }
}

VMATree::SIndex VMATree::get_new_commit_callstack(const SIndex es, const StateType ex, const RequestInfo& req) const {
  const SIndex ES = NativeCallStackStorage::invalid; // Empty Stack
  const SIndex rq = req.callstack;
  const int op_index = req.op_to_index();
  const Operation op = req.op();
  assert(op_index >= 0 && op_index < 4, "should be");
                         // existing state
  SIndex result[4][3] = {// Rl  Rs   C
                           {ES, ES, ES},   // op == Release
                           {ES, ES, ES},   // op == Reserve
                           {rq, rq, rq},   // op == Commit
                           {ES, ES, ES}    // op == Uncommit
                        };
  return result[op_index][state_to_index(ex)];
}

VMATree::StateType VMATree::get_new_state(const StateType ex, const RequestInfo& req) const {
  const StateType Rl = StateType::Released;
  const StateType Rs = StateType::Reserved;
  const StateType C = StateType::Committed;
  const int op = req.op_to_index();
  assert(op >= 0 && op < 4, "should be");
                            // existing state
  StateType result[4][3] = {// Rl  Rs   C
                              {Rl, Rl, Rl},   // op == Release
                              {Rs, Rs, Rs},   // op == Reserve
                              { C,  C,  C},   // op == Commit
                              {Rl, Rs, Rs}    // op == Uncommit
                           };
  return result[op][state_to_index(ex)];
}

MemTag VMATree::get_new_tag(const MemTag ex, const RequestInfo& req) const {
  switch(req.op()) {
    case Operation::Release:
      return mtNone;
    case Operation::Reserve:
      return req.tag;
    case Operation::Commit:
      return req.use_tag_inplace ? ex : req.tag;
    case Operation::Uncommit:
      return ex;
    default:
      break;
  }
  return mtNone;
}

void VMATree::compute_summary_diff(const SingleDiff::delta region_size,
                                   const MemTag current_tag,
                                   const StateType& ex,
                                   const RequestInfo& req,
                                   const MemTag operation_tag,
                                   SummaryDiff& diff) const {
  const StateType Rl = StateType::Released;
  const StateType Rs = StateType::Reserved;
  const StateType C = StateType::Committed;
  const int op = req.op_to_index();
  const Operation oper =  req.op();
  assert(op >= 0 && op < 4, "should be");

  SingleDiff::delta a = region_size;
  // A region with size `a` has a state as <column> and an operation is requested as in <row>
  // The region has tag `current_tag` and the operation has tag `operation_tag`.
  // For each state, we decide how much to be added/subtracted from current_tag to operation_tag. Two tables for reserve and commit.
  // Each pair of <x,y> in the table means add `x` to current_tag and add `y` to operation_tag. There are 3 pairs in each row for 3 states.
  // For example, `reserve[1][4,5]` says `-a,a` means:
  //    - we are reserving with operation_tag a region which is already commited with current_tag
  //    - since we are reserving, then `a` will be added to operation_tag. (`y` is `a`)
  //    - since we uncommitting (by reserving) then `a` is to be subtracted from current_tag. (`x` is `-a`).
  //    - amount of uncommitted size is in table `commit[1][4,5]` which is `-a,0` that means subtract `a` from current_tag.
                                       // existing state
  SingleDiff::delta reserve[4][3*2] = {// Rl    Rs     C
                                         {0,0, -a,0, -a,0 },   // op == Release
                                         {0,a, -a,a, -a,a },   // op == Reserve
                                         {0,a, -a,a, -a,a },   // op == Commit
                                         {0,0,  0,0,  0,0 }    // op == Uncommit
                                      };
  SingleDiff::delta commit[4][3*2] = {// Rl    Rs     C
                                        {0,0,  0,0, -a,0 },    // op == Release
                                        {0,0,  0,0, -a,0 },    // op == Reserve
                                        {0,a,  0,a, -a,a },    // op == Commit
                                        {0,0,  0,0, -a,0 }     // op == Uncommit
                                     };
  SingleDiff& from_rescom = diff.tag[NMTUtil::tag_to_index(current_tag)];
  SingleDiff&   to_rescom = diff.tag[NMTUtil::tag_to_index(operation_tag)];
  int st = state_to_index(ex);
  from_rescom.reserve += reserve[op][st * 2    ];
    to_rescom.reserve += reserve[op][st * 2 + 1];
  from_rescom.commit  +=  commit[op][st * 2    ];
    to_rescom.commit  +=  commit[op][st * 2 + 1];

}
// update the region state between n1 and n2. Since n1 and n2 are pointers, any update of them will be visible from tree.
// If n1 is noop, it can be removed because its left region (n1->val().in) is already decided and its right state (n1->val().out) is decided here.
// The state of right of n2 (n2->val().out) cannot be decided here yet.
void VMATree::update_region(TreapNode* n1, TreapNode* n2, const RequestInfo& req, SummaryDiff& diff) {
  assert(n1 != nullptr,"sanity");
  assert(n2 != nullptr,"sanity");
  //.........n1......n2......
  //          ^------^
  //             |
  IntervalState exSt = n1->val().out; // existing state info


  StateType existing_state              = exSt.type();
  MemTag    existing_tag                = exSt.mem_tag();
  SIndex    existing_reserve_callstack  = exSt.reserved_stack();
  SIndex    existing_commit_callstack   = exSt.committed_stack();

  StateType new_state                   = get_new_state(existing_state, req);
  MemTag    new_tag                     = get_new_tag(n1->val().out.mem_tag(), req);
  SIndex    new_reserve_callstack       = get_new_reserve_callstack(existing_reserve_callstack, existing_state, req);
  SIndex    new_commit_callstack        = get_new_commit_callstack(existing_commit_callstack, existing_state, req);

  //  n1........n2
  // out-->
  n1->val().out.set_tag(new_tag);
  n1->val().out.set_type(new_state);
  n1->val().out.set_reserve_stack(new_reserve_callstack);
  n1->val().out.set_commit_stack(new_commit_callstack);

  //  n1........n2
  //         <--in
  n2->val().in.set_tag(new_tag);
  n2->val().in.set_type(new_state);
  n2->val().in.set_reserve_stack(new_reserve_callstack);
  n2->val().in.set_commit_stack(new_commit_callstack);

  SingleDiff::delta region_size = n2->key() - n1->key();
  compute_summary_diff(region_size, existing_tag, existing_state, req, new_tag, diff);
}


VMATree::SummaryDiff VMATree::register_mapping(position _A, position _B, StateType state,
                                               const RegionData& metadata, bool use_tag_inplace) {

  if (_A == _B) {
    return SummaryDiff();
  }
  assert(_A < _B, "should be");
  SummaryDiff diff;
  RequestInfo req{_A, _B, state, metadata.mem_tag, metadata.stack_idx, use_tag_inplace};
  IntervalChange stA{
      IntervalState{StateType::Released, empty_regiondata},
      IntervalState{              state,   metadata}
  };
  IntervalChange stB{
      IntervalState{              state,   metadata},
      IntervalState{StateType::Released, empty_regiondata}
  };
  stA.out.set_commit_stack(NativeCallStackStorage::invalid);
  stB.in.set_commit_stack(NativeCallStackStorage::invalid);
  VMATreap::Range rA = _tree.find_enclosing_range(_A);
  VMATreap::Range rB = _tree.find_enclosing_range(_B);

  // nodes:          .....X.......Y...Z......W........U
  // request:                 A------------------B
  // X,Y = enclosing_nodes(A)
  // W,U = enclosing_nodes(B)
  // The cases are whether or not X and Y exists and X == A. (A == Y doesn't happen since it is searched by 'lt' predicate)
  // The cases are whether or not W and U exists and W == B. (B == U doesn't happen since it is searched by 'lt' predicate)

  // We update regions in 3 sections: 1) X..A..Y, 2) Y....W, 3) W..B..U
  // Y: is the closest node greater than A, but less than B
  // W: is the closest node less than B, but greater than A
  // The regions in [Y,W) are updated in a loop. We update X..A..Y before the loop and W..B..U after the loop.
  // The table below summarizes the overlap cases. The overlapping case depends on whether X, Y, W and U exist or not,
  // and if they exist whether they are the same or not.
  // In the notations here, when there is not dot ('.') between two nodes it meaans that they are the same. For example,
  // ...XA....Y.... means X == A.


  // row  0:  .........A..................B.....
  // row  1:  .........A...YW.............B.....  // it is impossible, since it means only one node exists in the tree.
  // row  2:  .........A...Y..........W...B.....
  // row  3:  .........A...Y.............WB.....

  // row  4:  .....X...A..................B.....
  // row  5:  .....X...A...YW.............B.....
  // row  6:  .....X...A...Y..........W...B.....
  // row  7:  .....X...A...Y.............WB.....

  // row  8:  ........XA..................B.....
  // row  9:  ........XA...YW.............B.....
  // row 10:  ........XA...Y..........W...B.....
  // row 11:  ........XA...Y.............WB.....

  // row 12:  .........A..................B....U
  // row 13:  .........A...YW.............B....U
  // row 14:  .........A...Y..........W...B....U
  // row 15:  .........A...Y.............WB....U

  // row 16:  .....X...A..................B....U
  // row 17:  .....X...A...YW.............B....U
  // row 18:  .....X...A...Y..........W...B....U
  // row 19:  .....X...A...Y.............WB....U

  // row 20:  ........XA..................B....U
  // row 21:  ........XA...YW.............B....U
  // row 22:  ........XA...Y..........W...B....U
  // row 23:  ........XA...Y.............WB....U


  // We intentionally did not summarize/compress the cases to keep them as separate.
  // This expanded way of describing the cases helps us to understand/analyze/verify/debug/maintain
  // the corresponding code more easily.
  // Mapping of table to row, row to switch-case should be consistent. If one changes, the others have
  // to be updated accordingly. The sequence of dependecies is: table -> row no -> switch(row)-case -> code.
  // Meaning that whenever any of one item in this sequence is changed, the rest of the consequent items to
  // be checked/changed.

  TreapNode* X = rA.start;
  TreapNode* Y = rA.end;
  TreapNode* W = rB.start;
  TreapNode* U = rB.end;
  TreapNode nA{_A, stA, 0}; // the node that represents A
  TreapNode nB{_B, stB, 0}; // the node that represents B
  TreapNode* A = &nA;
  TreapNode* B = &nB;
  auto upsert_if= [&](TreapNode* node) {
    if (!node->val().is_noop()) {
      _tree.upsert(node->key(), node->val());
    }
  };
  // update region between n1 and n2
  auto update = [&](TreapNode* n1, TreapNode* n2) {
    update_region(n1, n2, req, diff);
  };
  auto remove_if = [&](TreapNode* node) -> bool{
    if (node->val().is_noop()) {
      _tree.remove(node->key());
      return true;
    }
    return false;
  };
  GrowableArrayCHeap<position, mtNMT> to_be_removed;
  // update regions in range A to B
  auto update_loop = [&]() {
    TreapNode* prev = nullptr;
    _tree.visit_range_in_order(_A + 1, _B + 1, [&](TreapNode* curr) {
      if (prev != nullptr) {
        update_region(prev, curr, req, diff);
        // during visit, structure of the tree should not be changed
        // keep the keys to be removed, and remove them later
        if (prev->val().is_noop()) {
          to_be_removed.push(prev->key());
        }
      }
      prev = curr;
      return true;
    });
  };
  // update region of [A,T)
  auto update_A = [&](TreapNode* T) {
    A->val().out = A->val().in;
    update(A, T);
  };
  bool X_exists = X != nullptr;
  bool Y_exists = Y != nullptr && Y->key() <= _B;
  bool W_exists = W != nullptr && W->key() > _A;
  bool U_exists = U != nullptr;
  bool X_eq_A = X_exists && X->key() == _A;
  bool W_eq_B = W_exists && W->key() == _B;
  bool Y_eq_W = Y_exists && W_exists && W->key() == Y->key();
  int row = -1;
#ifdef ASSERT
  auto print_case = [&]() {
    log_trace(vmatree)(" req: %4d---%4d", (int)_A, (int)_B);
    log_trace(vmatree)(" row: %2d", row);
    log_trace(vmatree)(" X: %4ld", X_exists ? (long)X->key() : -1);
    log_trace(vmatree)(" Y: %4ld", Y_exists ? (long)Y->key() : -1);
    log_trace(vmatree)(" W: %4ld", W_exists ? (long)W->key() : -1);
    log_trace(vmatree)(" U: %4ld", U_exists ? (long)U->key() : -1);
  };
#endif
  // Order of the nodes if they exist are as: X <= A < Y <= W <= B < U
  //             A---------------------------B
  //       X           Y          YW         WB          U
  //       XA          Y          YW         WB          U
  if (!X_exists && !Y_exists                       && !U_exists) { row =  0; }
  if (!X_exists &&  Y_exists &&  Y_eq_W && !W_eq_B && !U_exists) { row =  1; }
  if (!X_exists &&  Y_exists && !Y_eq_W && !W_eq_B && !U_exists) { row =  2; }
  if (!X_exists &&  Y_exists &&             W_eq_B && !U_exists) { row =  3; }

  if ( X_exists && !Y_exists                       && !U_exists) { row =  4; }
  if ( X_exists &&  Y_exists &&  Y_eq_W && !W_eq_B && !U_exists) { row =  5; }
  if ( X_exists &&  Y_exists && !Y_eq_W && !W_eq_B && !U_exists) { row =  6; }
  if ( X_exists &&  Y_exists &&             W_eq_B && !U_exists) { row =  7; }

  if ( X_eq_A   && !Y_exists                       && !U_exists) { row =  8; }
  if ( X_eq_A   &&  Y_exists &&  Y_eq_W && !W_eq_B && !U_exists) { row =  9; }
  if ( X_eq_A   &&  Y_exists && !Y_eq_W && !W_eq_B && !U_exists) { row = 10; }
  if ( X_eq_A   &&  Y_exists &&             W_eq_B && !U_exists) { row = 11; }

  if (!X_exists && !Y_exists                       &&  U_exists) { row = 12; }
  if (!X_exists &&  Y_exists &&  Y_eq_W && !W_eq_B &&  U_exists) { row = 13; }
  if (!X_exists &&  Y_exists && !Y_eq_W && !W_eq_B &&  U_exists) { row = 14; }
  if (!X_exists &&  Y_exists &&             W_eq_B &&  U_exists) { row = 15; }

  if ( X_exists && !Y_exists                       &&  U_exists) { row = 16; }
  if ( X_exists &&  Y_exists &&  Y_eq_W && !W_eq_B &&  U_exists) { row = 17; }
  if ( X_exists &&  Y_exists && !Y_eq_W && !W_eq_B &&  U_exists) { row = 18; }
  if ( X_exists &&  Y_exists &&             W_eq_B &&  U_exists) { row = 19; }

  if ( X_eq_A   && !Y_exists                       &&  U_exists) { row = 20; }
  if ( X_eq_A   &&  Y_exists &&  Y_eq_W && !W_eq_B &&  U_exists) { row = 21; }
  if ( X_eq_A   &&  Y_exists && !Y_eq_W && !W_eq_B &&  U_exists) { row = 22; }
  if ( X_eq_A   &&  Y_exists &&             W_eq_B &&  U_exists) { row = 23; }

    switch(row) {
    // row  0:  .........A..................B.....
    case 0: {
      update_A(B);
      upsert_if(A);
      upsert_if(B);
      break;
    }
    // row  1:  .........A...YW.............B.....
    case 1: {
      ShouldNotReachHere();
      break;
    }
    // row  2:  .........A...Y..........W...B.....
    case 2: {
      update_A(Y);
      upsert_if(A);
      update_loop();
      remove_if(Y);
      update(W, B);
      remove_if(W);
      upsert_if(B);
      break;
    }
    // row  3:  .........A...Y.............WB.....
    case 3: {
      update_A(Y);
      upsert_if(A);
      update_loop();
      remove_if(W);
      break;
    }
    // row  4:  .....X...A..................B.....
    case 4: {
      A->val().in = X->val().out;
      update_A(B);
      upsert_if(A);
      upsert_if(B);
      break;
    }
    // row  5:  .....X...A...YW.............B.....
    case 5: {
      A->val().in = X->val().out;
      update_A(Y);
      upsert_if(A);
      update(Y, B);
      remove_if(Y);
      upsert_if(B);
      break;
    }
    // row  6:  .....X...A...Y..........W...B.....
    case 6: {
      A->val().in = X->val().out;
      update_A(Y);
      upsert_if(A);
      update_loop();
      update(W, B);
      remove_if(W);
      upsert_if(B);
      break;
    }
    // row  7:  .....X...A...Y.............WB.....
    case 7: {
      A->val().in = X->val().out;
      update_A(Y);
      upsert_if(A);
      update_loop();
      remove_if(W);
      break;
    }
    // row  8:  ........XA..................B.....
    case 8: {
      update(X, B);
      remove_if(X);
      upsert_if(B);
      break;
    }
    // row  9:  ........XA...YW.............B.....
    case 9: {
      update(X, Y);
      remove_if(X);
      update(W, B);
      remove_if(W);
      upsert_if(B);
      break;
    }
    // row 10:  ........XA...Y..........W...B.....
    case 10: {
      update(X, Y);
      remove_if(X);
      update_loop();
      update(W, B);
      remove_if(W);
      upsert_if(B);
      break;
    }
    // row 11:  ........XA...Y.............WB.....
    case 11: {
      update(X, Y);
      remove_if(X);
      update_loop();
      remove_if(W);
      break;
    }
    // row 12:  .........A..................B....U
    case 12: {
      update_A(B);
      upsert_if(A);
      upsert_if(B);
      break;
    }
    // row 13:  .........A...YW.............B....U
    case 13: {
      update_A(Y);
      upsert_if(A);
      update(W, B);
      remove_if(W);
      B->val().out = U->val().in;
      upsert_if(B);
      break;
    }
    // row 14:  .........A...Y..........W...B....U
    case 14: {
      update_A(Y);
      upsert_if(A);
      update_loop();
      update(W, B);
      remove_if(W);
      B->val().out = U->val().in;
      upsert_if(B);
      break;
    }
    // row 15:  .........A...Y.............WB....U
    case 15: {
      update_A(Y);
      upsert_if(A);
      update_loop();
      remove_if(W);
      break;
    }
    // row 16:  .....X...A..................B....U
    case 16: {
      A->val().in = X->val().out;
      update_A(B);
      upsert_if(A);
      B->val().out = U->val().in;
      upsert_if(B);
      break;
    }
    // row 17:  .....X...A...YW.............B....U
    case 17: {
      A->val().in = X->val().out;
      update_A(Y);
      upsert_if(A);
      update(W, B);
      remove_if(W);
      B->val().out = U->val().in;
      upsert_if(B);
      break;
    }
    // row 18:  .....X...A...Y..........W...B....U
    case 18: {
      A->val().in = X->val().out;
      update_A(Y);
      upsert_if(A);
      update_loop();
      update(W, B);
      remove_if(W);
      B->val().out = U->val().in;
      upsert_if(B);
      break;
    }
    // row 19:  .....X...A...Y.............WB....U
    case 19: {
      A->val().in = X->val().out;
      update_A(Y);
      upsert_if(A);
      update_loop();
      remove_if(W);
      break;
    }
    // row 20:  ........XA..................B....U
    case 20: {
      update(X, B);
      remove_if(X);
      B->val().out = U->val().in;
      upsert_if(B);
      break;
    }
    // row 21:  ........XA...YW.............B....U
    case 21: {
      update(X, Y);
      remove_if(X);
      update(W, B);
      remove_if(W);
      B->val().out = U->val().in;
      upsert_if(B);
      break;
    }
    // row 22:  ........XA...Y..........W...B....U
    case 22: {
      update(X, Y);
      remove_if(X);
      update_loop();
      update(W, B);
      remove_if(W);
      B->val().out = U->val().in;
      upsert_if(B);
      break;
    }
    // row 23:  ........XA...Y.............WB....U
    case 23: {
      update(X, Y);
      remove_if(X);
      update_loop();
      remove_if(W);
      break;
    }
    default:
      ShouldNotReachHere();
  }

  // Remove the 'noop' nodes that found inside the loop
  while(to_be_removed.length() != 0) {
    _tree.remove(to_be_removed.pop());
  }

  return diff;
}

#ifdef ASSERT
void VMATree::print_on(outputStream* out) {
  visit_in_order([&](TreapNode* current) {
    out->print("%zu (%s) - %s [%d, %d]-> ", current->key(), NMTUtil::tag_to_name(out_state(current).mem_tag()),
              statetype_to_string(out_state(current).type()), current->val().out.reserved_stack(), current->val().out.committed_stack());
    return true;
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

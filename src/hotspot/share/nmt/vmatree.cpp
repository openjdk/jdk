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
  if (op == 2 && ex == StateType::Released) {
    return rq;
  } else {
    return result[op][st_to_index(ex)];
  }
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
  from_rescom.reserve += reserve[op][st * 2    ];
    to_rescom.reserve += reserve[op][st * 2 + 1];
  from_rescom.commit  +=  commit[op][st * 2    ];
    to_rescom.commit  +=  commit[op][st * 2 + 1];

}

void VMATree::update_region(TreapNode* n1, TreapNode* n2, const RequestInfo& req, SummaryDiff& diff) {
  using SIndex = NativeCallStackStorage::StackIndex;
  IntervalState exSt = n1->val().out; // existing state info
  assert(n1 != nullptr,"sanity");
  assert(n2 != nullptr,"sanity");


  StateType existing_state              = exSt.type();
  MemTag    existing_tag                = exSt.mem_tag();
  SIndex    existing_reserve_callstack  = exSt.reserved_stack();
  SIndex    existing_commit_callstack   = exSt.committed_stack();

  StateType new_state                   = get_new_state(existing_state, req);
  MemTag    new_tag                     = req.use_tag_inplace ? n1->val().out.mem_tag() : req.tag;
  SIndex    new_reserve_callstack       = get_new_reserve_callstack(existing_reserve_callstack, existing_state, req);
  SIndex    new_committ_callstack       = get_new_commit_callstack(existing_commit_callstack, existing_state, req);

  n1->val().out.set_tag(new_tag);
  n1->val().out.set_type(new_state);
  n1->val().out.set_reserve_stack(new_reserve_callstack);
  n1->val().out.set_commit_stack(new_committ_callstack);

  n2->val().in.set_tag(new_tag);
  n2->val().in.set_type(new_state);
  n2->val().in.set_reserve_stack(new_reserve_callstack);
  n2->val().in.set_commit_stack(new_committ_callstack);

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
  auto upsert_if= [&](TreapNode* node) {
    if (!node->val().is_noop()) {
      _tree.upsert(node->key(), node->val());
    }
  };
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
  // update regions in [Y,W)
  auto update_loop = [&]() {
    /*(S,F)
    n1 = S
    while(n2 != F) {
      n2=gt(n1);
      update(n1,n2);
      n1=n2;
    }
    */
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
    });
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
  // The regons in [Y,W) are updated in a loop. We update X..A..Y before the loop and W..B..U after the loop.
  // The table below summarizes the overlap cases.


  // row  0:  .........A..................B.....
  // row  1:  .........A...YW.............B.....
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
  // This expanded way of describing the cases helps us to understand/analyze/verify/debug/maintain the corresponding code more easily.
  // Mapping of table to row, row to switch-case should be consistent. If one changes, the others have to be updated accordingly.
  // The sequence of dependecies is: table -> row no -> switch(row)-case -> code. Meaning that whenever any of one item in this sequence is changed, the rest of the consequent items to be checked/changed.

  TreapNode* X = rA.start;
  TreapNode* Y = rA.end;
  TreapNode* W = rB.start;
  TreapNode* U = rB.end;
  TreapNode nA{_A, stA, 0}; // the node that represents A
  TreapNode nB{_B, stB, 0}; // the node that represents B
  TreapNode* A = &nA;
  TreapNode* B = &nB;
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
  auto print_case = [&](int a = 1) {
    tty->print(" req: %4d---%4d", (int)_A, (int)_B);
    tty->print(" row: %2d", row);
    if (a) {
      tty->print(" X: %4ld", X_exists ? X->key() : -1);
      tty->print(" Y: %4ld", Y_exists ? Y->key() : -1);
      tty->print(" W: %4ld", W_exists ? W->key() : -1);
      tty->print(" U: %4ld", U_exists ? U->key() : -1);
    }
    tty->print_cr("");
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

  DEBUG_ONLY(print_case();)
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
      update_A(Y);
      upsert_if(A);
      update(W, B);
      remove_if(W);
      upsert_if(B);
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
      update(Y, W);
      remove_if(Y);
      remove_if(W);
      break;
    }
    // row  4:  .....X...A..................B.....
    case 4: {
      A->val().in = X->val().out;
      upsert_if(A);
      update(A, B);
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

  // ************************************************************************************ Remove the 'noop' nodes that found inside the loop
  while(to_be_removed.length() != 0) {
    _tree.remove(to_be_removed.pop());
  }

  return diff;
}

#ifdef ASSERT
void VMATree::print_on(outputStream* out) {
  visit_in_order([&](TreapNode* current) {
    if (current->val().out.has_committed_stack()) {
      out->print("%zu (%s) - %s [%d, %d]-> ", current->key(), NMTUtil::tag_to_name(out_state(current).mem_tag()),
                statetype_to_string(out_state(current).type()), current->val().out.reserved_stack(), current->val().out.committed_stack());
    } else {
      out->print("%zu (%s) - %s [%d, --]-> ", current->key(), NMTUtil::tag_to_name(out_state(current).mem_tag()),
                statetype_to_string(out_state(current).type()), current->val().out.reserved_stack());

    }

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

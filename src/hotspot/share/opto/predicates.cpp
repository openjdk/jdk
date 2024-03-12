/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/callnode.hpp"
#include "opto/predicates.hpp"

// Walk over all Initialized Assertion Predicates and return the entry into the first Initialized Assertion Predicate
// (i.e. not belonging to an Initialized Assertion Predicate anymore)
Node* AssertionPredicatesWithHalt::find_entry(Node* start_proj) {
  Node* entry = start_proj;
  while (is_assertion_predicate_success_proj(entry)) {
    entry = entry->in(0)->in(0);
  }
  return entry;
}

bool AssertionPredicatesWithHalt::is_assertion_predicate_success_proj(const Node* predicate_proj) {
  if (predicate_proj == nullptr || !predicate_proj->is_IfProj() || !predicate_proj->in(0)->is_If()) {
    return false;
  }
  return has_opaque4(predicate_proj) && has_halt(predicate_proj);
}

// Check if the If node of `predicate_proj` has an Opaque4 node as input.
bool AssertionPredicatesWithHalt::has_opaque4(const Node* predicate_proj) {
  IfNode* iff = predicate_proj->in(0)->as_If();
  return iff->in(1)->Opcode() == Op_Opaque4;
}

// Check if the other projection (UCT projection) of `success_proj` has a Halt node as output.
bool AssertionPredicatesWithHalt::has_halt(const Node* success_proj) {
  ProjNode* other_proj = success_proj->as_IfProj()->other_if_proj();
  return other_proj->outcnt() == 1 && other_proj->unique_out()->Opcode() == Op_Halt;
}

// Returns the Parse Predicate node if the provided node is a Parse Predicate success proj. Otherwise, return null.
ParsePredicateNode* ParsePredicate::init_parse_predicate(Node* parse_predicate_proj,
                                                         Deoptimization::DeoptReason deopt_reason) {
  assert(parse_predicate_proj != nullptr, "must not be null");
  if (parse_predicate_proj->is_IfTrue() && parse_predicate_proj->in(0)->is_ParsePredicate()) {
    ParsePredicateNode* parse_predicate_node = parse_predicate_proj->in(0)->as_ParsePredicate();
    if (parse_predicate_node->deopt_reason() == deopt_reason) {
      return parse_predicate_node;
    }
  }
  return nullptr;
}

Deoptimization::DeoptReason RuntimePredicate::uncommon_trap_reason(IfProjNode* if_proj) {
    CallStaticJavaNode* uct_call = if_proj->is_uncommon_trap_if_pattern();
    if (uct_call == nullptr) {
      return Deoptimization::Reason_none;
    }
    return Deoptimization::trap_request_reason(uct_call->uncommon_trap_request());
}

bool RuntimePredicate::is_success_proj(Node* node, Deoptimization::DeoptReason deopt_reason) {
  if (may_be_runtime_predicate_if(node)) {
    return deopt_reason == uncommon_trap_reason(node->as_IfProj());
  } else {
    return false;
  }
}

// A Runtime Predicate must have an If or a RangeCheck node, while the If should not be a zero trip guard check.
bool RuntimePredicate::may_be_runtime_predicate_if(Node* node) {
  if (node->is_IfProj()) {
    const IfNode* if_node = node->in(0)->as_If();
    const int opcode_if = if_node->Opcode();
    if ((opcode_if == Op_If && !if_node->is_zero_trip_guard())
        || opcode_if == Op_RangeCheck) {
      return true;
    }
  }
  return false;
}

ParsePredicateIterator::ParsePredicateIterator(const Predicates& predicates) : _current_index(0) {
  const PredicateBlock* loop_limit_check_predicate_block = predicates.loop_limit_check_predicate_block();
  if (loop_limit_check_predicate_block->has_parse_predicate()) {
    _parse_predicates.push(loop_limit_check_predicate_block->parse_predicate());
  }
  if (UseProfiledLoopPredicate) {
    const PredicateBlock* profiled_loop_predicate_block = predicates.profiled_loop_predicate_block();
    if (profiled_loop_predicate_block->has_parse_predicate()) {
      _parse_predicates.push(profiled_loop_predicate_block->parse_predicate());
    }
  }
  if (UseLoopPredicate) {
    const PredicateBlock* loop_predicate_block = predicates.loop_predicate_block();
    if (loop_predicate_block->has_parse_predicate()) {
      _parse_predicates.push(loop_predicate_block->parse_predicate());
    }
  }
}

ParsePredicateNode* ParsePredicateIterator::next() {
  assert(has_next(), "always check has_next() first");
  return _parse_predicates.at(_current_index++);
}

#ifdef ASSERT
// Check that the block has at most one Parse Predicate and that we only find Regular Predicate nodes (i.e. IfProj,
// If, or RangeCheck nodes).
void PredicateBlock::verify_block() {
  Node* next = _parse_predicate.entry(); // Skip unique Parse Predicate of this block if present
  while (next != _entry) {
    assert(!next->is_ParsePredicate(), "can only have one Parse Predicate in a block");
    const int opcode = next->Opcode();
    assert(next->is_IfProj() || opcode == Op_If || opcode == Op_RangeCheck,
           "Regular Predicates consist of an IfProj and an If or RangeCheck node");
    assert(opcode != Op_If || !next->as_If()->is_zero_trip_guard(), "should not be zero trip guard");
    next = next->in(0);
  }
}
#endif // ASSERT

// Walk over all Regular Predicates of this block (if any) and return the first node not belonging to the block
// anymore (i.e. entry to the first Regular Predicate in this block if any or `regular_predicate_proj` otherwise).
Node* PredicateBlock::skip_regular_predicates(Node* regular_predicate_proj, Deoptimization::DeoptReason deopt_reason) {
  Node* entry = regular_predicate_proj;
  while (RuntimePredicate::is_success_proj(entry, deopt_reason)) {
    assert(entry->in(0)->as_If(), "must be If node");
    entry = entry->in(0)->in(0);
  }
  return entry;
}

/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/connode.hpp"
#include "opto/loopnode.hpp"
#include "opto/opaquenode.hpp"
#include "opto/phaseX.hpp"

//=============================================================================
// Do not allow value-numbering
uint Opaque1Node::hash() const { return NO_HASH; }
bool Opaque1Node::cmp( const Node &n ) const {
  return (&n == this);          // Always fail except on self
}

//------------------------------Identity---------------------------------------
// Do NOT remove the opaque node until no more loop opts can happen.
Node* Opaque1Node::Identity(PhaseGVN* phase) {
  if (phase->C->post_loop_opts_phase()) {
    return in(1);
  } else {
    phase->C->record_for_post_loop_opts_igvn(this);
  }
  return this;
}

#ifdef ASSERT
CountedLoopNode* OpaqueZeroTripGuardNode::guarded_loop() const {
  Node* iff = if_node();
  ResourceMark rm;
  Unique_Node_List wq;
  wq.push(iff);
  for (uint i = 0; i < wq.size(); ++i) {
    Node* nn = wq.at(i);
    for (DUIterator_Fast imax, i = nn->fast_outs(imax); i < imax; i++) {
      Node* u = nn->fast_out(i);
      if (u->is_OuterStripMinedLoop()) {
        wq.push(u);
      }
      if (u->is_CountedLoop() && u->as_CountedLoop()->is_canonical_loop_entry() == this) {
        return u->as_CountedLoop();
      }
      if (u->is_Region()) {
        continue;
      }
      if (u->is_CFG()) {
        wq.push(u);
      }
    }
  }
  return nullptr;
}
#endif

IfNode* OpaqueZeroTripGuardNode::if_node() const {
  Node* cmp = unique_out();
  assert(cmp->Opcode() == Op_CmpI, "");
  Node* bol = cmp->unique_out();
  assert(bol->Opcode() == Op_Bool, "");
  Node* iff = bol->unique_out();
  return iff->as_If();
}

void OpaqueMultiversioningNode::mark_useless(PhaseIterGVN& igvn) {
  assert(_is_delayed_slow_loop, "must still be delayed");
  _useless = true;
  igvn._worklist.push(this);
}

Node* OpaqueMultiversioningNode::Identity(PhaseGVN* phase) {
  // Constant fold the multiversion_if. Since the slow_loop is still delayed,
  // i.e. we have not yet added any possibly failing condition, we can just
  // take the true branch in all cases.
  if (_useless) {
    assert(_is_delayed_slow_loop, "the slow_loop should still be delayed");
    return in(1);
  }
  return Opaque1Node::Identity(phase);
}

#ifndef PRODUCT
void OpaqueMultiversioningNode::dump_spec(outputStream *st) const {
  Opaque1Node::dump_spec(st);
  if (_useless) {
    st->print(" #useless");
  }
}
#endif

const Type* OpaqueConstantBoolNode::Value(PhaseGVN* phase) const {
  return phase->type(in(1));
}

#ifndef PRODUCT
void OpaqueConstantBoolNode::dump_spec(outputStream *st) const {
  st->print(_constant ? " #true" : " #false");
}
#endif

OpaqueTemplateAssertionPredicateNode::OpaqueTemplateAssertionPredicateNode(BoolNode* bol,  CountedLoopNode* loop_node)
    : Node(nullptr, bol),
      _loop_node(loop_node),
      _predicate_state(PredicateState::Useful) {
  init_class_id(Class_OpaqueTemplateAssertionPredicate);
}

Node* OpaqueTemplateAssertionPredicateNode::Identity(PhaseGVN* phase) {
  if (!phase->C->post_loop_opts_phase()) {
    // Record Template Assertion Predicates for post loop opts IGVN. We can remove them when there is no more loop
    // splitting possible. This also means that we do not create any new Initialized Assertion Predicates created from
    // these templates.
    phase->C->record_for_post_loop_opts_igvn(this);
  }
  return this;
}

const Type* OpaqueTemplateAssertionPredicateNode::Value(PhaseGVN* phase) const {
  assert(_predicate_state != PredicateState::MaybeUseful, "should only be MaybeUseful when eliminating useless "
                                                          "predicates during loop opts");
  if (is_useless() || phase->C->post_loop_opts_phase()) {
    // Template Assertion Predicates only serve as templates to create Initialized Assertion Predicates when splitting
    // a loop during loop opts. They are not used anymore once loop opts are over and can then be removed. They feed
    // into the bool input of an If node and can thus be replaced by the success path to let the Template Assertion
    // Predicate be folded away (the success path is always the true path by design). We can also fold the Template
    // Assertion Predicate away when it's found to be useless and not used anymore.
    return TypeInt::ONE;
  }
  return phase->type(in(1));
}

void OpaqueTemplateAssertionPredicateNode::mark_useless(PhaseIterGVN& igvn) {
  _predicate_state = PredicateState::Useless;
  igvn._worklist.push(this);
}

#ifndef PRODUCT
void OpaqueTemplateAssertionPredicateNode::dump_spec(outputStream* st) const {
  st->print("loop_idx=%d ", _loop_node->_idx);
  if (is_useless()) {
    st->print("#useless ");
  }
}
#endif // NOT PRODUCT

const Type* OpaqueInitializedAssertionPredicateNode::Value(PhaseGVN* phase) const {
  if (_useless) {
    return TypeInt::ONE;
  }
  return phase->type(in(1));
}

void OpaqueInitializedAssertionPredicateNode::mark_useless(PhaseIterGVN& igvn) {
  _useless = true;
  igvn._worklist.push(this);
}

#ifndef PRODUCT
void OpaqueInitializedAssertionPredicateNode::dump_spec(outputStream* st) const {
  if (_useless) {
    st->print("#useless ");
  }
}
#endif // NOT PRODUCT

uint ProfileBooleanNode::hash() const { return NO_HASH; }
bool ProfileBooleanNode::cmp( const Node &n ) const {
  return (&n == this);
}

Node *ProfileBooleanNode::Ideal(PhaseGVN *phase, bool can_reshape) {
  if (can_reshape && _delay_removal) {
    _delay_removal = false;
    return this;
  } else {
    return nullptr;
  }
}

Node* ProfileBooleanNode::Identity(PhaseGVN* phase) {
  if (_delay_removal) {
    return this;
  } else {
    assert(_consumed, "profile should be consumed before elimination");
    return in(1);
  }
}

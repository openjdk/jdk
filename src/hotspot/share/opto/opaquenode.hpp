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

#ifndef SHARE_OPTO_OPAQUENODE_HPP
#define SHARE_OPTO_OPAQUENODE_HPP

#include "opto/node.hpp"
#include "opto/predicates_enums.hpp"
#include "opto/subnode.hpp"

enum class PredicateState;

//------------------------------Opaque1Node------------------------------------
// A node to prevent unwanted optimizations.  Allows constant folding.
// Stops value-numbering, Ideal calls or Identity functions.
class Opaque1Node : public Node {
  virtual uint hash() const ;                  // { return NO_HASH; }
  virtual bool cmp( const Node &n ) const;
  public:
  Opaque1Node(Compile* C, Node *n) : Node(nullptr, n) {
    // Put it on the Macro nodes list to removed during macro nodes expansion.
    init_flags(Flag_is_macro);
    init_class_id(Class_Opaque1);
    C->add_macro_node(this);
  }
  // Special version for the pre-loop to hold the original loop limit
  // which is consumed by range check elimination.
  Opaque1Node(Compile* C, Node *n, Node* orig_limit) : Node(nullptr, n, orig_limit) {
    // Put it on the Macro nodes list to removed during macro nodes expansion.
    init_flags(Flag_is_macro);
    init_class_id(Class_Opaque1);
    C->add_macro_node(this);
  }
  Node* original_loop_limit() { return req()==3 ? in(2) : nullptr; }
  virtual int Opcode() const;
  virtual const Type *bottom_type() const { return TypeInt::INT; }
  virtual Node* Identity(PhaseGVN* phase);
};

// Opaque nodes specific to range check elimination handling
class OpaqueLoopInitNode : public Opaque1Node {
  public:
  OpaqueLoopInitNode(Compile* C, Node *n) : Opaque1Node(C, n) {
    init_class_id(Class_OpaqueLoopInit);
  }
  virtual int Opcode() const;
};

class OpaqueLoopStrideNode : public Opaque1Node {
  public:
  OpaqueLoopStrideNode(Compile* C, Node *n) : Opaque1Node(C, n) {
    init_class_id(Class_OpaqueLoopStride);
  }
  virtual int Opcode() const;
};

class OpaqueZeroTripGuardNode : public Opaque1Node {
public:
  // This captures the test that returns true when the loop is entered. It depends on whether the loop goes up or down.
  // This is used by CmpINode::Value.
  BoolTest::mask _loop_entered_mask;
  OpaqueZeroTripGuardNode(Compile* C, Node* n, BoolTest::mask loop_entered_test) :
          Opaque1Node(C, n), _loop_entered_mask(loop_entered_test) {
  }

  DEBUG_ONLY(CountedLoopNode* guarded_loop() const);
  virtual int Opcode() const;
  virtual uint size_of() const {
    return sizeof(*this);
  }

  IfNode* if_node() const;
};

// This node is used to mark the auto vectorization Predicate.
// At first, the multiversion_if has its condition set to "true" and we always
// take the fast_loop. Since we do not know if the slow_loop is ever going to
// be used, we delay optimizations for it. Once the fast_loop decides to use
// speculative runtime-checks and adds them to the multiversion_if, the slow_loop
// can now resume optimizations, as it is reachable at runtime.
// See PhaseIdealLoop::maybe_multiversion_for_auto_vectorization_runtime_checks
class OpaqueMultiversioningNode : public Opaque1Node {
private:
  bool _is_delayed_slow_loop;
  bool _useless;

public:
  OpaqueMultiversioningNode(Compile* C, Node* n) :
      Opaque1Node(C, n), _is_delayed_slow_loop(true), _useless(false)
  {
    init_class_id(Class_OpaqueMultiversioning);
  }
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }
  bool is_delayed_slow_loop() const { return _is_delayed_slow_loop; }
  DEBUG_ONLY( bool is_useless() const { return _useless; } )

  void notify_slow_loop_that_it_can_resume_optimizations() {
    assert(!_useless, "must still be useful");
    _is_delayed_slow_loop = false;
  }

  void mark_useless(PhaseIterGVN& igvn);
  NOT_PRODUCT(virtual void dump_spec(outputStream* st) const;)
  virtual uint size_of() const { return sizeof(OpaqueMultiversioningNode); }
};

// This node is used in the context of intrinsics. We sometimes implicitly know that an object is non-null even though
// the compiler cannot prove it. We therefore add a corresponding cast to propagate this implicit knowledge. However,
// this cast could become top during optimizations (input to cast becomes null) and the data path is folded. To ensure
// that the control path is also properly folded, we insert an If node with a OpaqueConstantBoolNode as condition.
// During macro expansion, we replace the OpaqueConstantBoolNodes with true in product builds such that the actually
// unneeded checks are folded and do not end up in the emitted code. In debug builds, we keep the actual checks as
// additional verification code (i.e. removing OpaqueConstantBoolNodes and use the BoolNode inputs instead). For more
// details, also see GraphKit::must_be_not_null().
// Similarly, sometimes we know that a size or limit guard is checked (e.g. there is already a guard in the caller) but
// the compiler cannot prove it. We could in principle avoid adding a guard in the intrinsic but in some cases (e.g.
// when the input is a constant that breaks the guard and the caller guard is not inlined) the input of the intrinsic
// can become top and the data path is folded. To ensure that the control path is also properly folded, we insert an
// OpaqueConstantBoolNode before the If node in the guard. During macro expansion, we replace the OpaqueConstantBoolNode
// with false in product builds such that the actually unneeded guards are folded and do not end up in the emitted code.
// In debug builds, we keep the actual checks as additional verification code (i.e. removing OpaqueConstantBoolNodes and
// use the BoolNode inputs instead).
class OpaqueConstantBoolNode : public Node {
 private:
  const bool _constant;
 public:
  OpaqueConstantBoolNode(Compile* C, Node* tst, bool constant) : Node(nullptr, tst), _constant(constant) {
    assert(tst->is_Bool() || tst->is_Con(), "Test node must be a BoolNode or a constant");
    init_class_id(Class_OpaqueConstantBool);
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }

  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }
  int constant() const { return _constant ? 1 : 0; }
  virtual uint size_of() const { return sizeof(OpaqueConstantBoolNode); }
  NOT_PRODUCT(void dump_spec(outputStream* st) const);
};

// This node is used for Template Assertion Predicate BoolNodes. A Template Assertion Predicate is always removed
// after loop opts and thus is never converted to actual code. In the post loop opts IGVN phase, the
// OpaqueTemplateAssertionPredicateNode is replaced by true in order to fold the Template Assertion Predicate away.
class OpaqueTemplateAssertionPredicateNode : public Node {

  // The counted loop this Template Assertion Predicate is associated with.
  CountedLoopNode* _loop_node;

  // When splitting a loop or when the associated loop dies, the Template Assertion Predicate with this
  // OpaqueTemplateAssertionPredicateNode also needs to be removed. We set this flag and then clean this node up in the
  // next IGVN phase by checking this flag in Value().
  PredicateState _predicate_state;

  // OpaqueTemplateAssertionPredicateNodes are unique to a Template Assertion Predicate expression and should never
  // common up. We still make sure of that by returning NO_HASH here.
  virtual uint hash() const {
    return NO_HASH;
  }

 public:
  OpaqueTemplateAssertionPredicateNode(BoolNode* bol, CountedLoopNode* loop_node);

  virtual int Opcode() const;
  virtual uint size_of() const { return sizeof(*this); }
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }

  CountedLoopNode* loop_node() const {
    return _loop_node;
  }

  // Should only be called during Loop Unrolling when we only update the OpaqueLoopStride input but don't require a full
  // clone of the Template Assertion Expression.
  void update_loop_node(CountedLoopNode* loop_node) {
    _loop_node = loop_node;
  }

  bool is_useless() const {
    return _predicate_state == PredicateState::Useless;
  }

  void mark_useless(PhaseIterGVN& igvn);

  void mark_maybe_useful() {
    _predicate_state = PredicateState::MaybeUseful;
  }

  bool is_useful() const {
    return _predicate_state == PredicateState::Useful;
  }

  void mark_useful() {
    _predicate_state = PredicateState::Useful;
  }

  NOT_PRODUCT(void dump_spec(outputStream* st) const);
};

// This node is used for Initialized Assertion Predicate BoolNodes. Initialized Assertion Predicates must always evaluate
// to true. During macro expansion, we replace the OpaqueInitializedAssertionPredicateNodes with true in product builds
// such that the actually unneeded checks are folded and do not end up in the emitted code. In debug builds, we keep the
// actual checks as additional verification code (i.e. removing OpaqueInitializedAssertionPredicateNodes and use the
// BoolNode inputs instead).
class OpaqueInitializedAssertionPredicateNode : public Node {
  // When updating a loop in Loop Unrolling, we forcefully kill old Initialized Assertion Predicates. We set this flag
  // and then clean this node up in the next IGVN phase by checking this flag in Value().
  bool _useless;

  // OpaqueInitializedAssertionPredicateNode are unique to an Initialized Assertion Predicate expression and should never
  // common up. Thus, we return NO_HASH here.
  virtual uint hash() const {
    return NO_HASH;
  }

 public:
  OpaqueInitializedAssertionPredicateNode(BoolNode* bol, Compile* C) : Node(nullptr, bol),
      _useless(false) {
    init_class_id(Class_OpaqueInitializedAssertionPredicate);
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }

  virtual int Opcode() const;
  virtual uint size_of() const { return sizeof(*this); }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }

  bool is_useless() const {
    return _useless;
  }

  void mark_useless(PhaseIterGVN& igvn);
  NOT_PRODUCT(void dump_spec(outputStream* st) const);
};

//------------------------------ProfileBooleanNode-------------------------------
// A node represents value profile for a boolean during parsing.
// Once parsing is over, the node goes away (during IGVN).
// It is used to override branch frequencies from MDO (see has_injected_profile in parse2.cpp).
class ProfileBooleanNode : public Node {
  uint _false_cnt;
  uint _true_cnt;
  bool _consumed;
  bool _delay_removal;
  virtual uint hash() const ;                  // { return NO_HASH; }
  virtual bool cmp( const Node &n ) const;
  public:
  ProfileBooleanNode(Node *n, uint false_cnt, uint true_cnt) : Node(nullptr, n),
          _false_cnt(false_cnt), _true_cnt(true_cnt), _consumed(false), _delay_removal(true) {}

  uint false_count() const { return _false_cnt; }
  uint  true_count() const { return  _true_cnt; }

  void consume() { _consumed = true;  }

  virtual int Opcode() const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type *bottom_type() const { return TypeInt::BOOL; }
  virtual uint size_of() const { return sizeof(ProfileBooleanNode); }
};

#endif // SHARE_OPTO_OPAQUENODE_HPP

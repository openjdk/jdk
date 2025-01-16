/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/opcodes.hpp"
#include "subnode.hpp"

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

// This node is used in the context of intrinsics. We sometimes implicitly know that an object is non-null even though
// the compiler cannot prove it. We therefore add a corresponding cast to propagate this implicit knowledge. However,
// this cast could become top during optimizations (input to cast becomes null) and the data path is folded. To ensure
// that the control path is also properly folded, we insert an If node with a OpaqueNotNullNode as condition. During
// macro expansion, we replace the OpaqueNotNullNodes with true in product builds such that the actually unneeded checks
// are folded and do not end up in the emitted code. In debug builds, we keep the actual checks as additional
// verification code (i.e. removing OpaqueNotNullNodes and use the BoolNode inputs instead). For more details, also see
// GraphKit::must_be_not_null().
class OpaqueNotNullNode : public Node {
 public:
  OpaqueNotNullNode(Compile* C, Node* tst) : Node(nullptr, tst) {
    init_class_id(Class_OpaqueNotNull);
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }

  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }
};

// This node is used for Template Assertion Predicate BoolNodes. A Template Assertion Predicate is always removed
// after loop opts and thus is never converted to actual code. In the post loop opts IGVN phase, the
// OpaqueTemplateAssertionPredicateNode is replaced by true in order to fold the Template Assertion Predicate away.
class OpaqueTemplateAssertionPredicateNode : public Node {
 public:
  OpaqueTemplateAssertionPredicateNode(BoolNode* bol) : Node(nullptr, bol) {
    init_class_id(Class_OpaqueTemplateAssertionPredicate);
  }

  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }
};

// This node is used for Initialized Assertion Predicate BoolNodes. Initialized Assertion Predicates must always evaluate
// to true. During  macro expansion, we replace the OpaqueInitializedAssertionPredicateNodes with true in product builds
// such that the actually unneeded checks are folded and do not end up in the emitted code. In debug builds, we keep the
// actual checks as additional verification code (i.e. removing OpaqueInitializedAssertionPredicateNodes and use the
// BoolNode inputs instead).
class OpaqueInitializedAssertionPredicateNode : public Node {
 public:
  OpaqueInitializedAssertionPredicateNode(BoolNode* bol, Compile* C) : Node(nullptr, bol) {
    init_class_id(Class_OpaqueInitializedAssertionPredicate);
    init_flags(Flag_is_macro);
    C->add_macro_node(this);
  }

  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual const Type* bottom_type() const { return TypeInt::BOOL; }
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
};

#endif // SHARE_OPTO_OPAQUENODE_HPP

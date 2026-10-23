/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_OPTIZATIONDELAYNODE_HPP
#define SHARE_OPTO_OPTIZATIONDELAYNODE_HPP

#include "opto/node.hpp"

// This node delays optimizations, to verify that different optimization
// order for IGVN and CCP optimizations does not change the behavior of
// the resulting code.
// This node operates as identity-function, and simply passes on the
// input as its output. Eventually, it will be removed and replaced
// by its input node.
// The node progressively reveals more information about its input:
// 1. We can replace the node with its input, see Identity.
// 2. We can initially keep the type of the node wider than the input type.
// 3. We can progressively narrow the type, as long as it is equal to
//    or wider than the input type.
class OptimizationDelayNode : public Node {
  enum class Stage {
    HoldType,
    RevealType,
    RevealIdentity
  };

  const Type* const _bottom_type;
  const Type* _hold_type;
  Stage _stage;

public:
  OptimizationDelayNode(Compile* C, Node* n, const Type* t)
  : Node(nullptr, n),
    _bottom_type(compute_bottom_type(t)),
    _hold_type(t),
    _stage(Stage::HoldType)
  {
    assert(can_delay_type(t), "permissible input type");
    // TODO: what to do with input type? We could widen it.
    // Yes, otherwise for singletons we immediately collapse the node due to sigleton type.
    // TODO: yes, we need some kind of "type picker function" that chooses something between
    // t and bottom type. We will also need that later when we advance the type from some
    // wider type to a narrower type closer to the input type.
    C->add_optimization_delay_node(this);
    init_class_id(Class_OptimizationDelay);
  }

  static bool can_delay_type(const Type* t) {
    return t->isa_int() != nullptr ||
           t->isa_long() != nullptr;
  }

  // TODO: description and improvements
  static const Type* compute_bottom_type(const Type* t) {
    return Type::get_const_basic_type(t->basic_type());
  }

  virtual int Opcode() const;
  virtual uint hash() const { return NO_HASH; }
  virtual bool cmp(const Node& n) const { return this == &n; }
  virtual const Type* bottom_type() const { return _bottom_type; }
  virtual uint size_of() const { return sizeof(OptimizationDelayNode); }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Identity(PhaseGVN* phase);

  void narrow_hold_type(const Type* narrow, Stress& stress);
  void reveal_type();
  void reveal_identity();

#ifndef PRODUCT
  virtual void dump_spec(outputStream* st) const {
    st->print(" bottom_type=");
    _bottom_type->dump_on(st);
    st->print(" hold_type=");
    _hold_type->dump_on(st);
    switch(_stage) {
      case Stage::HoldType:
        st->print(" stage=HoldType");
        break;
      case Stage::RevealType:
        st->print(" stage=RevealType");
        break;
      case Stage::RevealIdentity:
        st->print(" stage=RevealIdentity");
        break;
      default:
        ShouldNotReachHere();
    }
  }
#endif

private:
  static const Type* sample_type_between(const Type* wide, const Type* narrow, Stress& stress);
};

#endif // SHARE_OPTO_OPTIZATIONDELAYNODE_HPP

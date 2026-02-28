/*
* Copyright (c) 2026, IBM and/or its affiliates. All rights reserved.
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

#include "mathnode.hpp"

#include "runtime.hpp"
#include "runtime/stubRoutines.hpp"

PowDNode::PowDNode(Compile* C, Node* base, Node* exp)
    : CallLeafPureNode(
        OptoRuntime::Math_DD_D_Type(),
        StubRoutines::dpow() != nullptr
            ? StubRoutines::dpow()
            : CAST_FROM_FN_PTR(address, SharedRuntime::dpow),
        "pow") {
  add_flag(Flag_is_macro);
  C->add_macro_node(this);

  init_req(TypeFunc::Parms + 0, base);
  init_req(TypeFunc::Parms + 1, C->top());  // double slot padding
  init_req(TypeFunc::Parms + 2, exp);
  init_req(TypeFunc::Parms + 3, C->top());  // double slot padding
}

Node* PowDNode::Ideal(PhaseGVN* phase, bool can_reshape) {
  if (!can_reshape) {
    return nullptr;  // wait for igvn
  }
  PhaseIterGVN* igvn = phase->is_IterGVN();

  Node* base = this->base();
  Node* exp  = this->exp();
  const Type* t_base = phase->type(base);
  const Type* t_exp  = phase->type(exp);

  if (t_base == Type::TOP || t_exp == Type::TOP) {
    return phase->C->top();
  }

  // constant folding: both inputs are constants
  const TypeD* base_con = t_base->isa_double_constant();
  const TypeD* exp_con  = t_exp->isa_double_constant();
  if (base_con != nullptr && exp_con != nullptr) {
    // FIXME: is SharedRuntime::dpow(-0.0, 0.5) spec compliant?
    double result = SharedRuntime::dpow(base_con->getd(), exp_con->getd());
    return make_tuple_of_input_state_and_result(igvn, phase->makecon(TypeD::make(result)));
  }

  // strength reductions: only the exponent is a constant
  if (exp_con != nullptr) {
    double e = exp_con->getd();

    // Special case: pow(x, 2.0) => x * x
    if (e == 2.0) {
      Node* mul = igvn->transform(new MulDNode(base, base));
      return make_tuple_of_input_state_and_result(igvn, mul);
    }

    // Special case: pow(x, 0.5) => sqrt(x)
    if (e == 0.5) {
      // This one is tricker because pow(-0.0, 0.5) => +0.0 but sqrt(-0.0) => -0.0
      // Since we can't build control flow here in Ideal(), we defer this to macro expansion.
      //
      // TODO: actually, is it better to build control flow at parse time (at inlining)? If x becomes a constant later,
      // expanding pow(CON, 0.5) to sqrt(CON) at macro expansion time leaves us no change to fold it. And there is
      // possible regression too.
      //
      // However, with building control flow at parse time, we risk missing optimization opportunity if exp cannot
      // become a constant early enough.
      //
      // Doing both? One at parse time to ensure sqrt(CON) is always folded, one at expansion time ensure exp have
      // enough chance to be propagated.
      //
      // New idea: still delay to macro expansion so base have a change to constant propagate

      // Node* zero = igvn->zerocon(T_DOUBLE);
      // RegionNode* region = new RegionNode(3);
      // Node* phi = new PhiNode(region, Type::DOUBLE);
      //
      // Node* cmp  = igvn->transform(new CmpDNode(base, zero));
      // // According to the API specs, pow(-0.0, 0.5) = 0.0 and sqrt(-0.0) = -0.0.
      // // So pow(-0.0, 0.5) shouldn't be replaced with sqrt(-0.0).
      // // -0.0/+0.0 are both excluded since floating-point comparison doesn't distinguish -0.0 from +0.0.
      // Node* test = igvn->transform(new BoolNode(cmp, BoolTest::le));
      //
      // Node* if_pow = generate_slow_guard(test, nullptr);
      // Node* value_sqrt = igvn->transform(new SqrtDNode(igvn->C, control(), base));
      // phi->init_req(1, value_sqrt);
      // region->init_req(1, control());
      //
      // if (if_pow != nullptr) {
      //
      // }

      return CallLeafPureNode::Ideal(phase, can_reshape);
    }

    // Special case: pow(x, 0.0) => 1
    // FIXME: x^0 => 1 is not in the original code. FP spec compliance reasons?
    // if (e == 0.0) {
    //   return make_result_tuple(igvn, phase->makecon(TypeD::make(1.0)));
    // }

    // Special case: pow(x, 1.0) => x
    // FIXME: x^1 => x is not in the original code. Forgotten or handled somewhere else?
    // if (e == 1.0) {
    //   return make_result_tuple(igvn, base);
    // }

    // Special case: pow(x, -1.0) => 1.0 / x
    // FIXME: x^1 => x is not in the original code. FP sepc compliance reasons?
    // if (e == -1.0) {
    //   Node* one = phase->makecon(TypeD::make(1.0));
    //   Node* div = igvn->transform(new DivDNode(nullptr, one, base));
    //   return make_result_tuple(igvn, div);
    // }
  }

  // FIXME: we could also do the following?
  // Special case: pow(0, y) => 0
  // Special case: pow(1, y) => 1

  return CallLeafPureNode::Ideal(phase, can_reshape);
}

// We can't simply have Ideal() returning a Con or MulNode since the users are still expecting a Call node, but we could
// produce a tuple that follows the same pattern so users can still get control, io, memory, etc..
TupleNode* PowDNode::make_tuple_of_input_state_and_result(PhaseIterGVN* phase, Node* result) const {
  Compile* C = phase->C;
  C->remove_macro_node(const_cast<PowDNode*>(this));
  TupleNode* tuple = TupleNode::make(
      tf()->range(),
      in(TypeFunc::Control),
      in(TypeFunc::I_O),
      in(TypeFunc::Memory),
      in(TypeFunc::FramePtr),
      in(TypeFunc::ReturnAdr),
      result,
      C->top());  // double slot padding
  return tuple;
}
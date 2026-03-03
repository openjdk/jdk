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

#include <math.h>

PowDNode::PowDNode(Compile* C, Node* base, Node* exp)
    : CallLeafPureNode(
        OptoRuntime::Math_DD_D_Type(),
        StubRoutines::dpow() != nullptr ? StubRoutines::dpow() : CAST_FROM_FN_PTR(address, SharedRuntime::dpow),
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
  // TODO: move to Value()
  const TypeD* base_con = t_base->isa_double_constant();
  const TypeD* exp_con  = t_exp->isa_double_constant();
  if (base_con != nullptr && exp_con != nullptr) {
    // FIXME: is SharedRuntime::dpow(-0.0, 0.5) spec compliant?
    double result = SharedRuntime::dpow(base_con->getd(), exp_con->getd());
    return make_tuple_of_input_state_and_result(igvn, phase->makecon(TypeD::make(result)));
  }

  // Special cases when only the exponent is known:
  if (exp_con != nullptr) {
    double e = exp_con->getd();
    // If the second argument is positive or negative zero, then the result is 1.0.
    // i.e., pow(x, +/-0.0D) => 1.0
    if (e == -0.0 || e == +0.0) {
      return make_tuple_of_input_state_and_result(igvn, phase->makecon(TypeD::ONE));
    }

    // If the second argument is 1.0, then the result is the same as the first argument.
    // i.e., pow(x, 1.0) => x
    if (e == 1.0) {
      return make_tuple_of_input_state_and_result(igvn, base);
    }

    // If the second argument is NaN, then the result is NaN.
    // i.e., pow(x, NaN) => NaN
    if (isnan(e)) {
      return make_tuple_of_input_state_and_result(igvn, phase->makecon(TypeD::make(NAN)));
    }

    // If the second argument is 2.0, then strength reduce to multiplications.
    // i.e., pow(x, 2.0) => x * x
    if (e == 2.0) {
      Node* mul = igvn->transform(new MulDNode(base, base));
      return make_tuple_of_input_state_and_result(igvn, mul);
    }

    // If the second argument is 0.5, the strength reduce to saqure roots.
    // i.e., pow(x, 0.5) => sqrt(x)
    // This one is tricker because pow(-0.0, 0.5) => +0.0 but sqrt(-0.0) => -0.0, which rquires building a control flow
    // diamond. We defer this to marcro expansion to give the base more chances to be constant folded.
  }

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
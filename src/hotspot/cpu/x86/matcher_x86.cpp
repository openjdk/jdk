/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifdef COMPILER2

#include "opto/matcher.hpp"
#include "opto/parse.hpp"
#include "opto/node.hpp"
#include "opto/connode.hpp"
#include "opto/divnode.hpp"
#include "opto/subnode.hpp"
#include "opto/cfgnode.hpp"
#include "opto/phase.hpp"
#include "interpreter/bytecodes.hpp"

static void parse_div_mod(Parse& parser);

bool Matcher::parse_one_bytecode(Parse& parser) {
  switch (parser.bc()) {
    case Bytecodes::_idiv: // fallthrough
    case Bytecodes::_ldiv: // fallthrough
    case Bytecodes::_irem: // fallthrough
    case Bytecodes::_lrem:
      parse_div_mod(parser);
      return true;
    default:
      return false;
  }
}

static void parse_div_mod(Parse& parser) {
  Bytecodes::Code bc = parser.bc();
  PhaseGVN& gvn = parser.gvn();
  BasicType bt = (bc == Bytecodes::_idiv || bc == Bytecodes::_irem) ? T_INT : T_LONG;
  bool is_div = (bc == Bytecodes::_idiv || bc == Bytecodes::_ldiv);
  // Operands need to stay in the stack during zero check
  if (bt == T_INT) {
    parser.zero_check_int(parser.peek(0));
  } else {
    parser.zero_check_long(parser.peek(1));
  }
  // Compile time detect of arithmetic exception
  if (parser.stopped()) {
    return;
  }

  Node* in2 = (bt == T_INT) ? parser.pop() : parser.pop_pair();
  Node* in1 = (bt == T_INT) ? parser.pop() : parser.pop_pair();

  auto generate_division = [](PhaseGVN& gvn, Node* control, Node* in1, Node* in2,
                              BasicType bt, bool is_div) {
    if (is_div) {
      return (bt == T_INT)
             ? gvn.transform(new DivINode(control, in1, in2))
             : gvn.transform(new DivLNode(control, in1, in2));
    } else {
      return (bt == T_INT)
             ? gvn.transform(new ModINode(control, in1, in2))
             : gvn.transform(new ModLNode(control, in1, in2));
    }
  };

  auto push_result = [](Parse& parser, Node* res, BasicType bt) {
    if (bt == T_INT) {
      parser.push(res);
    } else {
      parser.push_pair(res);
    }
  };

  if (in1 == in2) {
    Node* res = gvn.integercon(is_div ? 1 : 0, bt);
    push_result(parser, res, bt);
    return;
  }

  // if in1 > min_value then there is no overflow risk
  if ((bt == T_INT  &&  !TypeInt::MIN->higher_equal(gvn.type(in1))) ||
      (bt == T_LONG && !TypeLong::MIN->higher_equal(gvn.type(in1)))) {
    Node* res = generate_division(gvn, parser.control(), in1, in2, bt, is_div);
    push_result(parser, res, bt);
    return;
  }

  // the generated graph is equivalent to (in2 == -1) ? -in1 : (in1 / in2)
  // we need to have a separate branch for in2 == -1 due to overflow error
  // with (min_jint / -1) on x86
  Node* cmp = gvn.transform(CmpNode::make(in2, gvn.integercon(-1, bt), bt));
  Node* bol = parser.Bool(cmp, BoolTest::eq);
  IfNode* iff = parser.create_and_map_if(parser.control(), bol, PROB_UNLIKELY_MAG(3), COUNT_UNKNOWN);
  Node* iff_true = parser.IfTrue(iff);
  Node* iff_false = parser.IfFalse(iff);
  Node* res_fast = is_div
                   ? gvn.transform(SubNode::make(gvn.zerocon(bt), in1, bt))
                   : gvn.zerocon(bt);
  Node* res_slow = generate_division(gvn, iff_false, in1, in2, bt, is_div);
  Node* merge = new RegionNode(3);
  merge->init_req(1, iff_true);
  merge->init_req(2, iff_false);
  parser.record_for_igvn(merge);
  parser.set_control(gvn.transform(merge));
  Node* res = new PhiNode(merge, Type::get_const_basic_type(bt));
  res->init_req(1, res_fast);
  res->init_req(2, res_slow);
  res = gvn.transform(res);
  push_result(parser, res, bt);
}

#endif // COMPILER2

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

bool Matcher::parse_one_bytecode(Parse& parser) {
  Bytecodes::Code bc = parser.bc();
  PhaseGVN& gvn = parser.gvn();
  switch (bc) {
    case Bytecodes::_idiv: // fallthrough
    case Bytecodes::_ldiv: // fallthrough
    case Bytecodes::_irem: // fallthrough
    case Bytecodes::_lrem: {
      BasicType bt;
      if (bc == Bytecodes::_idiv || bc == Bytecodes::_irem) {
        bt = T_INT;
        parser.zero_check_int(parser.peek(0));
      } else {
        bt = T_LONG;
        parser.zero_check_long(parser.peek(1));
      }
      bool is_div = bc == Bytecodes::_idiv || bc == Bytecodes::_ldiv;

      Node* in2 = (bt == T_INT) ? parser.pop() : parser.pop_pair();
      Node* in1 = (bt == T_INT) ? parser.pop() : parser.pop_pair();
      Node* cmp = gvn.transform(CmpNode::make(in2, gvn.integercon(-1, bt), bt));
      Node* bol = parser.Bool(cmp, BoolTest::eq);
      IfNode* iff = parser.create_and_map_if(parser.control(), bol, PROB_UNLIKELY_MAG(3), COUNT_UNKNOWN);
      Node* iff_true = parser.IfTrue(iff);
      Node* iff_false = parser.IfFalse(iff);
      Node* res_fast = is_div
                       ? gvn.transform(SubNode::make(gvn.zerocon(bt), in1, bt))
                       : gvn.zerocon(bt);
      Node* res_slow;
      if (is_div) {
        res_slow = (bt == T_INT)
                   ? gvn.transform(new DivINode(iff_false, in1, in2))
                   : gvn.transform(new DivLNode(iff_false, in1, in2));
      } else {
        res_slow = (bt == T_INT)
                   ? gvn.transform(new ModINode(iff_false, in1, in2))
                   : gvn.transform(new ModLNode(iff_false, in1, in2));
      }
      Node* merge = new RegionNode(3);
      merge->init_req(1, iff_true);
      merge->init_req(2, iff_false);
      parser.record_for_igvn(merge);
      parser.set_control(gvn.transform(merge));
      Node* res = new PhiNode(merge, Type::get_const_basic_type(bt));
      res->init_req(1, res_fast);
      res->init_req(2, res_slow);
      res = gvn.transform(res);

      if (bt == T_INT) {
        parser.push(res);
      } else {
        parser.push_pair(res);
      }
      return true;
    }
    default:
      return false;
  }
}

#endif // COMPILER2

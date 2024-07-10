/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/mempointer.hpp"
#include "utilities/resourceHash.hpp"

// DFS all-path traversal (i.e. with node repetitions), starting at the pointer:
MemPointerSimpleForm MemPointerSimpleFormParser::parse_simple_form() {
  assert(_worklist.is_empty(), "no prior parsing");
  assert(_summands.is_empty(), "no prior parsing");

  Node* pointer = _mem->in(MemNode::Address);
  _worklist.push(MemPointerSummand(pointer, 1 LP64_ONLY( COMMA 1 )));

  int traversal_count = 0;
  while (_worklist.is_nonempty()) {
    if (traversal_count++ > 1000) { return MemPointerSimpleForm(pointer); }
    parse_sub_expression(_worklist.pop());
  }

  for (int i = 0; i < _summands.length(); i++) {
    MemPointerSummand summand = _summands.at(i);
    summand.print();
  }

  tty->print_cr("con: %d", (int)_con);

  return MemPointerSimpleForm::make(pointer, _summands, _con);
}

void MemPointerSimpleFormParser::parse_sub_expression(const MemPointerSummand summand) {
  Node* n = summand.variable();
  LP64_ONLY( const jlong scaleL = summand.scaleL(); )
  const jlong scale = summand.scale();

  n->dump();

  int opc = n->Opcode();
  switch (opc) {
    case Op_ConI:
    case Op_ConL:
    {
      jlong con = (opc == Op_ConI) ? n->get_int() : n->get_long();
      _con += scale * con;
      // TODO problematic: int con and int scale could overflow??? or irrelevant?
      return;
    }
    case Op_AddP:
    case Op_AddL:
    case Op_AddI:
    case Op_SubL:
    case Op_SubI:
    {
      // TODO check if we should decompose or not
      Node* a = n->in((opc == Op_AddP) ? 2 : 1);
      Node* b = n->in((opc == Op_AddP) ? 3 : 2);
      _worklist.push(MemPointerSummand(a, scale LP64_ONLY( COMMA scaleL )));
      // TODO figure out how to do subtraction, which scale to negate
      _worklist.push(MemPointerSummand(b, scale LP64_ONLY( COMMA scaleL )));
      return;
    }
    case Op_MulL:
    case Op_MulI:
    case Op_LShiftL:
    case Op_LShiftI:
    {
      // TODO check if we should decompose or not
      // Form must be linear: only multiplication with constants is allowed.
      Node* in2 = n->in(2);
      if (!in2->is_Con()) { break; }
      jlong factor;
      LP64_ONLY( jlong factorL; )
      switch (opc) {
        case Op_MulL:
          factor = in2->get_long();
          LP64_ONLY( factorL = factor; )
          break;
        case Op_MulI:
          factor = in2->get_int();
          LP64_ONLY( factorL = 1; )
          break;
        case Op_LShiftL:
          factor = 1LL << in2->get_long();
          LP64_ONLY( factorL = factor; )
          break; // TODO check overflow!
        case Op_LShiftI:
          factor = 1LL << in2->get_int();
          LP64_ONLY( factorL = 1; )
          break;
      }
      // Scale cannot be too large: TODO make this a special method, maybe better threshold?
      const jlong max_factor = 1 << 30;
      if (factor > max_factor || factor < -max_factor) { break; }

      Node* a = n->in(1);
      // TODO figure out which scale to change, check for total overflow???
      const jint new_scale = scale * factor; // TODO check overflow
      LP64_ONLY( const jint new_scaleL = scaleL * factorL; )
      _worklist.push(MemPointerSummand(a, new_scale LP64_ONLY( COMMA new_scaleL )));
      return;
    }
    case Op_CastII:
    case Op_CastLL:
    case Op_CastX2P:
    {
      assert(false, "unary");
      break;
    }
    case Op_ConvI2L:
    {
      Node* a = n->in(1);
      _worklist.push(MemPointerSummand(a, scale LP64_ONLY( COMMA scaleL )));
      return;
    }
  }

  // Default: could not parse the "summand" further, take it as one of the
  // "terminal" summands.
  // TODO wording of "terminal summands"?
  _summands.push(summand);
}

bool MemPointer::is_adjacent_to_and_before(const MemPointer& other) const {
  return true; // TODO
}


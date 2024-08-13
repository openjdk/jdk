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

  // pointer->dump_bfs(4,0,"#");

  const NoOverflowInt one(1);
  _worklist.push(MemPointerSummand(pointer, one LP64_ONLY( COMMA one )));

  int traversal_count = 0;
  while (_worklist.is_nonempty()) {
    if (traversal_count++ > 1000) { return MemPointerSimpleForm(pointer); }
    parse_sub_expression(_worklist.pop());
  }

  // for (int i = 0; i < _summands.length(); i++) {
  //   MemPointerSummand summand = _summands.at(i);
  //   summand.print();
  // }

  // tty->print("con: ");
  // _con.print();
  // tty->cr();

  // TODO gtest???
  // NoOverflowInt a(1 << 20);
  // a.print(); tty->cr();
  // NoOverflowInt b(1LL << 33);
  // b.print(); tty->cr();
  // NoOverflowInt c(55);
  // NoOverflowInt d(22);
  // NoOverflowInt e = c + d;
  // e.print(); tty->cr();
  // NoOverflowInt f(max_jint);
  // NoOverflowInt g(max_jint);
  // NoOverflowInt h = f + g;
  // h.print(); tty->cr();

  return MemPointerSimpleForm::make(pointer, _summands, _con);
}

void MemPointerSimpleFormParser::parse_sub_expression(const MemPointerSummand summand) {
  Node* n = summand.variable();
  const NoOverflowInt scale = summand.scale();
  LP64_ONLY( const NoOverflowInt scaleL = summand.scaleL(); )
  const NoOverflowInt one(1);

  int opc = n->Opcode();
  if (is_safe_from_int_overflow(opc LP64_ONLY( COMMA scaleL ))) {
    switch (opc) {
      case Op_ConI:
      case Op_ConL:
      {
        NoOverflowInt con = (opc == Op_ConI) ? NoOverflowInt(n->get_int())
                                             : NoOverflowInt(n->get_long());
        _con = _con + scale * con;
        // TODO problematic: int con and int scale could overflow??? or irrelevant?
        return;
      }
      case Op_AddP:
      case Op_AddL:
      case Op_AddI:
      {
        Node* a = n->in((opc == Op_AddP) ? 2 : 1);
        Node* b = n->in((opc == Op_AddP) ? 3 : 2);
        _worklist.push(MemPointerSummand(a, scale LP64_ONLY( COMMA scaleL )));
        _worklist.push(MemPointerSummand(b, scale LP64_ONLY( COMMA scaleL )));
        return;
      }
      case Op_SubL:
      case Op_SubI:
      {
        Node* a = n->in((opc == Op_AddP) ? 2 : 1);
        Node* b = n->in((opc == Op_AddP) ? 3 : 2);

        NoOverflowInt sub_scale = NoOverflowInt(-1) * scale;
        LP64_ONLY( NoOverflowInt sub_scaleL = (opc == Op_SubL) ? scaleL * NoOverflowInt(-1)
                                                               : scaleL; )

        // If anything went wrong with the scale computation: bailout.
        if (sub_scale.is_NaN()) { break; }
        LP64_ONLY( if (sub_scaleL.is_NaN()) { break; } )

        _worklist.push(MemPointerSummand(a, scale LP64_ONLY( COMMA scaleL )));
        _worklist.push(MemPointerSummand(b, sub_scale LP64_ONLY( COMMA sub_scaleL )));
        return;
      }
      case Op_MulL:
      case Op_MulI:
      case Op_LShiftL:
      case Op_LShiftI:
      {
        // TODO check if we should decompose or not: int-overflow!!!
        // Form must be linear: only multiplication with constants is allowed.
        Node* in1 = n->in(1);
        Node* in2 = n->in(2);
        if (!in2->is_Con()) { break; }
        NoOverflowInt factor;
        LP64_ONLY( NoOverflowInt factorL; )
        switch (opc) {
          case Op_MulL:
            factor = NoOverflowInt(in2->get_long());
            LP64_ONLY( factorL = factor; )
            break;
          case Op_MulI:
            factor = NoOverflowInt(in2->get_int());
            LP64_ONLY( factorL = one; )
            break;
          case Op_LShiftL:
            factor = one << NoOverflowInt(in2->get_int());
            LP64_ONLY( factorL = factor; )
            break;
          case Op_LShiftI:
            factor = one << NoOverflowInt(in2->get_int());
            LP64_ONLY( factorL = one; )
            break;
        }

        // Accumulate scale.
        NoOverflowInt new_scale = scale * factor;
        LP64_ONLY( NoOverflowInt new_scaleL = scaleL * factorL; )

        // Make sure abs(scale) is not larger than "1 << 30".
        new_scale = new_scale.truncate_to_30_bits();
        LP64_ONLY( new_scaleL = new_scaleL.truncate_to_30_bits(); )

        // If anything went wrong with the scale computation: bailout.
        if (new_scale.is_NaN()) { break; }
        LP64_ONLY( if (new_scaleL.is_NaN()) { break; } )

        _worklist.push(MemPointerSummand(in1, new_scale LP64_ONLY( COMMA new_scaleL )));
        return;
      }
      case Op_CastII:
      case Op_CastLL:
      case Op_CastX2P:
      case Op_ConvI2L:
      {
        Node* a = n->in(1);
        _worklist.push(MemPointerSummand(a, scale LP64_ONLY( COMMA scaleL )));
        return;
      }
    }
  }

  // Default: could not parse the "summand" further, take it as one of the
  // "terminal" summands.
  // TODO wording of "terminal summands"?
  _summands.push(summand);
}

bool MemPointerSimpleFormParser::is_safe_from_int_overflow(const int opc LP64_ONLY( COMMA const NoOverflowInt scaleL )) const {
#ifndef _LP64
  // On 32-bit platforms, ... TODO
  return true;
#else

  // TODO: trivially safe ops
  // Not trivially safe: AddI, SubI, MulI, LShiftI
  switch(opc) {
    case Op_ConI:
    case Op_ConL:
    case Op_AddP:
    case Op_AddL:
    case Op_SubL:
    case Op_MulL:
    case Op_LShiftL:
    case Op_CastII:
    case Op_CastLL:
    case Op_CastX2P:
    case Op_ConvI2L:

    // TODO to find some counter-examples:
    case Op_MulI:
    case Op_LShiftI:
      return true;
  }

  // TODO needed?
  if (scaleL.is_NaN()) {
    assert(false, "scaleL must not be NaN");
    return false;
  }

  const TypeAryPtr* ary_ptr_t = _mem->adr_type()->isa_aryptr();
  if (ary_ptr_t != nullptr) {
    // Array accesses that are not Unsafe always have a RangeCheck which ensures
    // that there is no int overflow.
    if (!_mem->is_unsafe_access()) {
      return true;
    }

    // TODO
    BasicType array_element_bt = ary_ptr_t->elem()->array_element_basic_type();
    if (is_java_primitive(array_element_bt)) {
      NoOverflowInt array_element_size_in_bytes = NoOverflowInt(type2aelembytes(array_element_bt));
      if (scaleL.is_multiple_of(array_element_size_in_bytes)) {
        return true;
      }
    }
  }

  return false;
#endif
}

MemPointerAliasing MemPointerSimpleForm::get_aliasing_with(const MemPointerSimpleForm& other) const {
  // Check if all summands are the same:
  for (uint i = 0; i < SUMMANDS_SIZE; i++) {
    const MemPointerSummand s1 = summands_at(i);
    const MemPointerSummand s2 = other.summands_at(i);
    if (s1 != s2) {
      return MemPointerAliasing::make_unknown();
    }
  }

  // Compute distance:
  NoOverflowInt distance = other.con() - con();
  distance = distance.truncate_to_30_bits();
  if (distance.is_NaN()) {
    return MemPointerAliasing::make_unknown();
  }

  return MemPointerAliasing::make_always(distance.value());
}

bool MemPointer::is_adjacent_to_and_before(const MemPointer& other) const {
  const MemPointerAliasing aliasing = simple_form().get_aliasing_with(other.simple_form());
  // tty->print_cr("MemPointer::is_adjacent_to_and_before");
  // simple_form().print();
  // other.simple_form().print();
  // tty->print("Aliasing: "); aliasing.print(); tty->cr();
  return aliasing.is_always_at_distance(mem()->memory_size());
}


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

// Recursively parse the pointer expression with a DFS all-path traversal
// (i.e. with node repetitions), starting at the pointer.
MemPointerLinearForm MemPointerLinearFormParser::parse_linear_form() {
  assert(_worklist.is_empty(), "no prior parsing");
  assert(_summands.is_empty(), "no prior parsing");

  Node* pointer = _mem->in(MemNode::Address);

  // Start with the trivial summand.
  const NoOverflowInt one(1);
  _worklist.push(MemPointerSummand(pointer, one LP64_ONLY( COMMA one )));

  // Decompose the summands until only terminal summands remain. This effectively
  // parses the pointer expression recursively.
  int traversal_count = 0;
  while (_worklist.is_nonempty()) {
    if (traversal_count++ > 1000) { return MemPointerLinearForm(pointer); }
    parse_sub_expression(_worklist.pop());
  }

  // Check for constant overflow.
  if (_con.is_NaN()) { return MemPointerLinearForm(pointer); }

  // Sort summands by variable->_idx
  _summands.sort(MemPointerSummand::cmp_for_sort);

  // Combine summands for the same variable, adding up the scales.
  int pos_put = 0;
  int pos_get = 0;
  while (pos_get < _summands.length()) {
    MemPointerSummand summand = _summands.at(pos_get++);
    Node* variable      = summand.variable();
    NoOverflowInt scale = summand.scale();
    // Add up scale of all summands with the same variable.
    while (pos_get < _summands.length() && _summands.at(pos_get).variable() == variable) {
      MemPointerSummand s = _summands.at(pos_get++);
      scale = scale + s.scale();
    }
    // Bail out if scale is NaN.
    if (scale.is_NaN()) {
      return MemPointerLinearForm(pointer);
    }
    // Keep summands with non-zero scale.
    if (!scale.is_zero()) {
      _summands.at_put(pos_put++, MemPointerSummand(variable, scale LP64_ONLY( COMMA NoOverflowInt(1) )));
    }
  }
  _summands.trunc_to(pos_put);

  return MemPointerLinearForm::make(pointer, _summands, _con);
}

// Parse a sub-expression of the pointer, starting at the current summand. We parse the
// current node, and see if it can be decomposed into further summands, or if the current
// summand is terminal.
void MemPointerLinearFormParser::parse_sub_expression(const MemPointerSummand summand) {
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
        // Terminal: add to constant.
        NoOverflowInt con = (opc == Op_ConI) ? NoOverflowInt(n->get_int())
                                             : NoOverflowInt(n->get_long());
        _con = _con + scale * con;
        return;
      }
      case Op_AddP:
      case Op_AddL:
      case Op_AddI:
      {
        // Decompose addition.
        Node* a = n->in((opc == Op_AddP) ? 2 : 1);
        Node* b = n->in((opc == Op_AddP) ? 3 : 2);
        _worklist.push(MemPointerSummand(a, scale LP64_ONLY( COMMA scaleL )));
        _worklist.push(MemPointerSummand(b, scale LP64_ONLY( COMMA scaleL )));
        return;
      }
      case Op_SubL:
      case Op_SubI:
      {
        // Decompose subtraction.
        Node* a = n->in(1);
        Node* b = n->in(2);

        NoOverflowInt sub_scale = NoOverflowInt(-1) * scale;
        LP64_ONLY( NoOverflowInt sub_scaleL = (opc == Op_SubL) ? scaleL * NoOverflowInt(-1)
                                                               : scaleL; )

        _worklist.push(MemPointerSummand(a, scale LP64_ONLY( COMMA scaleL )));
        _worklist.push(MemPointerSummand(b, sub_scale LP64_ONLY( COMMA sub_scaleL )));
        return;
      }
      case Op_MulL:
      case Op_MulI:
      case Op_LShiftL:
      case Op_LShiftI:
      {
        // Form must be linear: only multiplication with constants can be decomposed.
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

        _worklist.push(MemPointerSummand(in1, new_scale LP64_ONLY( COMMA new_scaleL )));
        return;
      }
      case Op_CastII:
      case Op_CastLL:
      case Op_CastX2P:
      case Op_ConvI2L:
      // On 32bit systems we can also look through ConvI2L, since the final result will always
      // be truncated back with ConvL2I. On 64bit systems this is not linear:
      //
      //   ConvI2L(ConvL2I(max_jint + 1)) = ConvI2L(min_jint) = min_jint
      //
      NOT_LP64( case Op_ConvL2I: )
      {
        // Decompose: look through.
        Node* a = n->in(1);
        _worklist.push(MemPointerSummand(a, scale LP64_ONLY( COMMA scaleL )));
        return;
      }
    }
  }

  // Default: we could not parse the "summand" further, i.e. it is terminal.
  _summands.push(summand);
}

bool MemPointerLinearFormParser::is_safe_from_int_overflow(const int opc LP64_ONLY( COMMA const NoOverflowInt scaleL )) const {
#ifndef _LP64
  // On 32-bit platforms, ... TODO
  return true;
#else

  // Not trivially safe:
  //   AddI:     ConvI2L(a +  b)    != ConvI2L(a) +  ConvI2L(b)
  //   SubI:     ConvI2L(a -  b)    != ConvI2L(a) -  ConvI2L(b)
  //   MulI:     ConvI2L(a *  conI) != ConvI2L(a) *  ConvI2L(conI)
  //   LShiftI:  ConvI2L(a << conI) != ConvI2L(a) << ConvI2L(conI)
  //
  // But these are always safe:
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
    // TODO CastPP ?
    case Op_ConvI2L:
      return true;
  }

  // TODO tests with native memory, etc.

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

MemPointerAliasing MemPointerLinearForm::get_aliasing_with(const MemPointerLinearForm& other
                                                           NOT_PRODUCT( COMMA const TraceMemPointer& trace) ) const {
#ifndef PRODUCT
  if (trace.is_trace_aliasing()) {
    tty->print_cr("MemPointerLinearForm::get_aliasing_with:");
    print_on(tty);
    other.print_on(tty);
  }
#endif

  // Check if all summands are the same:
  for (uint i = 0; i < SUMMANDS_SIZE; i++) {
    const MemPointerSummand s1 = summands_at(i);
    const MemPointerSummand s2 = other.summands_at(i);
    if (s1 != s2) {
#ifndef PRODUCT
      if (trace.is_trace_aliasing()) {
        tty->print_cr("  -> Aliasing unknown, differ on summand %d.", i);
      }
#endif
      return MemPointerAliasing::make_unknown();
    }
  }

  // Compute distance:
  const NoOverflowInt distance = other.con() - con();
  // TODO why 2_to_30 ?
  if (distance.is_NaN() || !distance.is_abs_less_than_2_to_30()) {
#ifndef PRODUCT
    if (trace.is_trace_aliasing()) {
      tty->print("  -> Aliasing unknown, bad distance: ");
      distance.print_on(tty);
      tty->cr();
    }
#endif
    return MemPointerAliasing::make_unknown();
  }

#ifndef PRODUCT
    if (trace.is_trace_aliasing()) {
      tty->print_cr("  -> Aliasing always, distance = %d.", distance.value());
    }
#endif
  return MemPointerAliasing::make_always(distance.value());
}

bool MemPointer::is_adjacent_to_and_before(const MemPointer& other) const {
  const MemPointerLinearForm& s1 = linear_form();
  const MemPointerLinearForm& s2 = other.linear_form();
  const MemPointerAliasing aliasing = s1.get_aliasing_with(s2 NOT_PRODUCT( COMMA _trace ));
  const jint size = mem()->memory_size();
  const bool is_adjacent = aliasing.is_always_at_distance(size);

#ifndef PRODUCT
  if (_trace.is_trace_adjacency()) {
    tty->print("Adjacent: %s, because size = %d and aliasing = ",
               is_adjacent ? "true" : "false", size);
    aliasing.print_on(tty);
    tty->cr();
  }
#endif

  return is_adjacent;
}


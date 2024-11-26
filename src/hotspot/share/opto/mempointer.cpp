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

#include "precompiled.hpp"
#include "opto/mempointer.hpp"
#include "utilities/resourceHash.hpp"

// Recursively parse the pointer expression with a DFS all-path traversal
// (i.e. with node repetitions), starting at the pointer.
MemPointerDecomposedForm MemPointerDecomposedFormParser::parse_decomposed_form() {
  assert(_worklist.is_empty(), "no prior parsing");
  assert(_summands.is_empty(), "no prior parsing");

  Node* pointer = _mem->in(MemNode::Address);

  // Start with the trivial summand.
  _worklist.push(MemPointerSummand(pointer, NoOverflowInt(1)));

  // Decompose the summands until only terminal summands remain. This effectively
  // parses the pointer expression recursively.
  int traversal_count = 0;
  while (_worklist.is_nonempty()) {
    // Bail out if the graph is too complex.
    if (traversal_count++ > 1000) { return MemPointerDecomposedForm::make_trivial(pointer); }
    parse_sub_expression(_worklist.pop());
  }

  // Bail out if there is a constant overflow.
  if (_con.is_NaN()) { return MemPointerDecomposedForm::make_trivial(pointer); }

  // Sorting by variable idx means that all summands with the same variable are consecutive.
  // This simplifies the combining of summands with the same variable below.
  _summands.sort(MemPointerSummand::cmp_by_variable_idx);

  // Combine summands for the same variable, adding up the scales.
  int pos_put = 0;
  int pos_get = 0;
  while (pos_get < _summands.length()) {
    const MemPointerSummand& summand = _summands.at(pos_get++);
    Node* variable      = summand.variable();
    NoOverflowInt scale = summand.scale();
    // Add up scale of all summands with the same variable.
    while (pos_get < _summands.length() && _summands.at(pos_get).variable() == variable) {
      MemPointerSummand s = _summands.at(pos_get++);
      scale = scale + s.scale();
    }
    // Bail out if scale is NaN.
    if (scale.is_NaN()) {
      return MemPointerDecomposedForm::make_trivial(pointer);
    }
    // Keep summands with non-zero scale.
    if (!scale.is_zero()) {
      _summands.at_put(pos_put++, MemPointerSummand(variable, scale));
    }
  }
  _summands.trunc_to(pos_put);

  return MemPointerDecomposedForm::make(pointer, _summands, _con);
}

// Parse a sub-expression of the pointer, starting at the current summand. We parse the
// current node, and see if it can be decomposed into further summands, or if the current
// summand is terminal.
void MemPointerDecomposedFormParser::parse_sub_expression(const MemPointerSummand& summand) {
  Node* n = summand.variable();
  const NoOverflowInt scale = summand.scale();
  const NoOverflowInt one(1);

  int opc = n->Opcode();
  if (is_safe_to_decompose_op(opc, scale)) {
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
        _worklist.push(MemPointerSummand(a, scale));
        _worklist.push(MemPointerSummand(b, scale));
        return;
      }
      case Op_SubL:
      case Op_SubI:
      {
        // Decompose subtraction.
        Node* a = n->in(1);
        Node* b = n->in(2);

        NoOverflowInt sub_scale = NoOverflowInt(-1) * scale;

        _worklist.push(MemPointerSummand(a, scale));
        _worklist.push(MemPointerSummand(b, sub_scale));
        return;
      }
      case Op_MulL:
      case Op_MulI:
      case Op_LShiftL:
      case Op_LShiftI:
      {
        // Only multiplication with constants is allowed: factor * variable
        // IGVN already folds constants to in(2). If we find a variable there
        // instead, we cannot further decompose this summand, and have to add
        // it to the terminal summands.
        Node* variable = n->in(1);
        Node* con      = n->in(2);
        if (!con->is_Con()) { break; }
        NoOverflowInt factor;
        switch (opc) {
          case Op_MulL:    // variable * con
            factor = NoOverflowInt(con->get_long());
            break;
          case Op_MulI:    // variable * con
            factor = NoOverflowInt(con->get_int());
            break;
          case Op_LShiftL: // variable << con = variable * (1 << con)
            factor = one << NoOverflowInt(con->get_int());
            break;
          case Op_LShiftI: // variable << con = variable * (1 << con)
            factor = one << NoOverflowInt(con->get_int());
            break;
        }

        // Accumulate scale.
        NoOverflowInt new_scale = scale * factor;

        _worklist.push(MemPointerSummand(variable, new_scale));
        return;
      }
      case Op_CastII:
      case Op_CastLL:
      case Op_CastX2P:
      case Op_ConvI2L:
      // On 32bit systems we can also look through ConvL2I, since the final result will always
      // be truncated back with ConvL2I. On 64bit systems we cannot decompose ConvL2I because
      // such int values will eventually be expanded to long with a ConvI2L:
      //
      //   valL = max_jint + 1
      //   ConvI2L(ConvL2I(valL)) = ConvI2L(min_jint) = min_jint != max_jint + 1 = valL
      //
      NOT_LP64( case Op_ConvL2I: )
      {
        // Decompose: look through.
        Node* a = n->in(1);
        _worklist.push(MemPointerSummand(a, scale));
        return;
      }
      default:
        // All other operations cannot be further decomposed. We just add them to the
        // terminal summands below.
        break;
    }
  }

  // Default: we could not parse the "summand" further, i.e. it is terminal.
  _summands.push(summand);
}

// Check if the decomposition of operation opc is guaranteed to be safe.
// Please refer to the definition of "safe decomposition" in mempointer.hpp
bool MemPointerDecomposedFormParser::is_safe_to_decompose_op(const int opc, const NoOverflowInt& scale) const {
#ifndef _LP64
  // On 32-bit platforms, the pointer has 32bits, and thus any higher bits will always
  // be truncated. Thus, it does not matter if we have int or long overflows.
  // Simply put: all decompositions are (SAFE1).
  return true;
#else

  switch (opc) {
    // These operations are always safe to decompose, i.e. (SAFE1):
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
    case Op_CastPP:
    case Op_ConvI2L:
      return true;

    // But on 64-bit platforms, these operations are not trivially safe to decompose:
    case Op_AddI:    // ConvI2L(a +  b)    != ConvI2L(a) +  ConvI2L(b)
    case Op_SubI:    // ConvI2L(a -  b)    != ConvI2L(a) -  ConvI2L(b)
    case Op_MulI:    // ConvI2L(a *  conI) != ConvI2L(a) *  ConvI2L(conI)
    case Op_LShiftI: // ConvI2L(a << conI) != ConvI2L(a) << ConvI2L(conI)
      break; // Analysis below.

    // All other operations are assumed not safe to decompose, or simply cannot be decomposed
    default:
      return false;
  }

  const TypeAryPtr* ary_ptr_t = _mem->adr_type()->isa_aryptr();
  if (ary_ptr_t != nullptr) {
    // Array accesses that are not Unsafe always have a RangeCheck which ensures
    // that there is no int overflow. And without overflows, all decompositions
    // are (SAFE1).
    if (!_mem->is_unsafe_access()) {
      return true;
    }

    // Intuition: In general, the decomposition of AddI, SubI, MulI or LShiftI is not safe,
    //            because of overflows. But under some conditions, we can prove that such a
    //            decomposition is (SAFE2). Intuitively, we want to prove that an overflow
    //            would mean that the pointers have such a large distance, that at least one
    //            must lie out of bounds. In the proof of the "MemPointer Lemma", we thus
    //            get a contradiction with the condition that both pointers are in bounds.
    //
    // We prove that the decomposition of AddI, SubI, MulI (with constant) and ShiftI (with
    // constant) is (SAFE2), under the condition:
    //
    //   abs(scale) % array_element_size_in_bytes = 0
    //
    // First, we describe how the decomposition works:
    //
    //   mp_i = con + sum(other_summands) + summand
    //          -------------------------   -------
    //          rest                        scale * ConvI2L(op)
    //
    //  We decompose the summand depending on the op, where we know that there is some
    //  integer y, such that:
    //
    //    scale * ConvI2L(a + b)     =  scale * ConvI2L(a) + scale * ConvI2L(b)  +  scale * y * 2^32
    //    scale * ConvI2L(a - b)     =  scale * ConvI2L(a) - scale * ConvI2L(b)  +  scale * y * 2^32
    //    scale * ConvI2L(a * con)   =  scale * con * ConvI2L(a)                 +  scale * y * 2^32
    //    scale * ConvI2L(a << con)  =  scale * (1 << con) * ConvI2L(a)          +  scale * y * 2^32
    //    \_______________________/     \_____________________________________/     \______________/
    //      before decomposition          after decomposition ("new_summands")     overflow correction
    //
    //  Thus, for AddI and SubI, we get:
    //    summand = new_summand1 + new_summand2 + scale * y * 2^32
    //
    //    mp_{i+1} = con + sum(other_summands) + new_summand1 + new_summand2
    //             = con + sum(other_summands) + summand - scale * y * 2^32
    //             = mp_i                                - scale * y * 2^32
    //
    //  And for MulI and ShiftI we get:
    //    summand = new_summand + scale * y * 2^32
    //
    //    mp_{i+1} = con + sum(other_summands) + new_summand
    //             = con + sum(other_summands) + summand - scale * y * 2^32
    //             = mp_i                                - scale * y * 2^32
    //
    //  Further:
    //    abs(scale) % array_element_size_in_bytes = 0
    //  implies that there is some integer z, such that:
    //    z * array_element_size_in_bytes = scale
    //
    //  And hence, with "x = y * z", the decomposition is (SAFE2) under the assumed condition:
    //    mp_i = mp_{i+1} + scale                           * y * 2^32
    //         = mp_{i+1} + z * array_element_size_in_bytes * y * 2^32
    //         = mp_{i+1} + x * array_element_size_in_bytes     * 2^32
    //
    BasicType array_element_bt = ary_ptr_t->elem()->array_element_basic_type();
    if (is_java_primitive(array_element_bt)) {
      NoOverflowInt array_element_size_in_bytes = NoOverflowInt(type2aelembytes(array_element_bt));
      if (scale.is_multiple_of(array_element_size_in_bytes)) {
        return true;
      }
    }
  }

  return false;
#endif
}

// Compute the aliasing between two MemPointerDecomposedForm. We use the "MemPointer Lemma" to
// prove that the computed aliasing also applies for the underlying pointers. Note that the
// condition (S0) is already given, because the MemPointerDecomposedForm is always constructed
// using only safe decompositions.
//
// Pre-Condition:
//   We assume that both pointers are in-bounds of their respective memory object. If this does
//   not hold, for example, with the use of Unsafe, then we would already have undefined behavior,
//   and we are allowed to do anything.
MemPointerAliasing MemPointerDecomposedForm::get_aliasing_with(const MemPointerDecomposedForm& other
                                                               NOT_PRODUCT( COMMA const TraceMemPointer& trace) ) const {
#ifndef PRODUCT
  if (trace.is_trace_aliasing()) {
    tty->print_cr("MemPointerDecomposedForm::get_aliasing_with:");
    print_on(tty);
    other.print_on(tty);
  }
#endif

  // "MemPointer Lemma" condition (S2): check if all summands are the same:
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

  // "MemPointer Lemma" condition (S3): check that the constants do not differ too much:
  const NoOverflowInt distance = other.con() - con();
  // We must check that: abs(distance) < 2^32
  // However, this is only false if: distance = min_jint
  if (distance.is_NaN() || distance.value() == min_jint) {
#ifndef PRODUCT
    if (trace.is_trace_aliasing()) {
      tty->print("  -> Aliasing unknown, bad distance: ");
      distance.print_on(tty);
      tty->cr();
    }
#endif
    return MemPointerAliasing::make_unknown();
  }

  // "MemPointer Lemma" condition (S1):
  //   Given that all summands are the same, we know that both pointers point into the
  //   same memory object. With the Pre-Condition, we know that both pointers are in
  //   bounds of that same memory object.

  // Hence, all 4 conditions of the "MemoryPointer Lemma" are established, and hence
  // we know that the distance between the underlying pointers is equal to the distance
  // we computed for the MemPointers:
  //   p_other - p_this = distance = other.con - this.con
#ifndef PRODUCT
    if (trace.is_trace_aliasing()) {
      tty->print_cr("  -> Aliasing always, distance = %d.", distance.value());
    }
#endif
  return MemPointerAliasing::make_always(distance.value());
}

bool MemPointer::is_adjacent_to_and_before(const MemPointer& other) const {
  const MemPointerDecomposedForm& s1 = decomposed_form();
  const MemPointerDecomposedForm& s2 = other.decomposed_form();
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

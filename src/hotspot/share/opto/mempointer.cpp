/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmSymbols.hpp"
#include "opto/addnode.hpp"
#include "opto/mempointer.hpp"
#include "utilities/resourceHash.hpp"

MemPointerParserCallback MemPointerParserCallback::_empty;

MemPointer::MemPointer(const MemNode* mem,
                       MemPointerParserCallback& callback
                       NOT_PRODUCT(COMMA const TraceMemPointer& trace)) :
  MemPointer(MemPointerParser::parse(mem,
                                     callback
                                     NOT_PRODUCT(COMMA trace))) {}

// Recursively parse the pointer expression with a DFS all-path traversal
// (i.e. with node repetitions), starting at the pointer.
MemPointer MemPointerParser::parse(MemPointerParserCallback& callback
                                   NOT_PRODUCT(COMMA const TraceMemPointer& trace)) {
  assert(_worklist.is_empty(), "no prior parsing");
  assert(_summands.is_empty(), "no prior parsing");

  Node* pointer = _mem->in(MemNode::Address);
  const jint size = _mem->memory_size();

  // Start with the trivial summand.
  _worklist.push(MemPointerSummand(pointer, NoOverflowInt(1)));

  // Decompose the summands until only terminal summands remain. This effectively
  // parses the pointer expression recursively.
  int traversal_count = 0;
  while (_worklist.is_nonempty()) {
    // Bail out if the graph is too complex.
    if (traversal_count++ > 1000) {
      return MemPointer::make_trivial(pointer, size NOT_PRODUCT(COMMA trace));
    }
    parse_sub_expression(_worklist.pop(), callback);
  }

  // Bail out if there is a constant overflow.
  if (_con.is_NaN()) {
    return MemPointer::make_trivial(pointer, size NOT_PRODUCT(COMMA trace));
  }

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
      return MemPointer::make_trivial(pointer, size NOT_PRODUCT(COMMA trace));
    }
    // Keep summands with non-zero scale.
    if (!scale.is_zero()) {
      _summands.at_put(pos_put++, MemPointerSummand(variable, scale));
    }
  }
  _summands.trunc_to(pos_put);

  return MemPointer::make(pointer, _summands, _con, size NOT_PRODUCT(COMMA trace));
}

// Parse a sub-expression of the pointer, starting at the current summand. We parse the
// current node, and see if it can be decomposed into further summands, or if the current
// summand is terminal.
void MemPointerParser::parse_sub_expression(const MemPointerSummand& summand, MemPointerParserCallback& callback) {
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
        callback.callback(n);
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
        callback.callback(n);
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
        callback.callback(n);
        return;
      }
      case Op_CastX2P:
        // A CastX2P indicates that we are pointing to native memory, where some long is cast to
        // a pointer. In general, we have no guarantees about this long, and just take it as a
        // terminal summand. A CastX2P can also be a good candidate for a native-memory "base".
        if (!sub_expression_has_native_base_candidate(n->in(1))) {
          // General case: take CastX2P as a terminal summand, it is a candidate for the "base".
          break;
        }
        // Fall-through: we can find a more precise native-memory "base". We further decompose
        // the CastX2P to find this "base" and any other offsets from it.
      case Op_CastII:
      case Op_CastLL:
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
          callback.callback(n);
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

bool MemPointerParser::sub_expression_has_native_base_candidate(Node* start) {
  // BFS over the expression.
  // Allocate sufficient space in worklist for 100 limit below.
  ResourceMark rm;
  GrowableArray<Node*> worklist(102);
  worklist.append(start);
  for (int i = 0; i < worklist.length(); i++) {
    Node* n = worklist.at(i);
    switch (n->Opcode()) {
      case Op_AddL:
        // Traverse to both inputs.
        worklist.append(n->in(1));
        worklist.append(n->in(2));
        break;
      case Op_SubL:
      case Op_CastLL:
        // Traverse to the first input. The base cannot be on the rhs of a sub.
        worklist.append(n->in(1));
        break;
      default:
        if (is_native_memory_base_candidate(n)) { return true; }
        break;
    }
    // This is a heuristic, so we are allowed to bail out early if the graph
    // is too deep. The constant is chosen arbitrarily, not too large but big
    // enough for all normal cases.
    if (worklist.length() > 100) { return false; }
  }
  // Parsed over the whole expression, nothing found.
  return false;
}

// Check if the node is a candidate to be a memory segment "base".
// (1) CastX2P: some arbitrary long that is cast to a pointer.
// (2) LoadL from field jdk.internal.foreign.NativeMemorySegmentImpl.min
//     Holds the address() of a native memory segment.
bool MemPointerParser::is_native_memory_base_candidate(Node* n) {
  // (1) CastX2P
  if (n->Opcode() == Op_CastX2P) { return true; }

  // (2) LoadL from field jdk.internal.foreign.NativeMemorySegmentImpl.min
  if (n->Opcode() != Op_LoadL) { return false; }
  LoadNode* load = n->as_Load();

  const TypeInstPtr* inst_ptr = load->adr_type()->isa_instptr();
  if (inst_ptr == nullptr) { return false; }

  ciInstanceKlass* klass = inst_ptr->instance_klass();
  int offset = inst_ptr->offset();
  ciField* field = klass->get_field_by_offset(offset, false);
  if (field == nullptr) { return false; }

  Symbol* field_symbol = field->name()->get_symbol();
  Symbol* holder_symbol = field->holder()->name()->get_symbol();
  return holder_symbol == vmSymbols::jdk_internal_foreign_NativeMemorySegmentImpl() &&
         field_symbol == vmSymbols::min_name();
}

// Check if the decomposition of operation opc is guaranteed to be safe.
// Please refer to the definition of "safe decomposition" in mempointer.hpp
bool MemPointerParser::is_safe_to_decompose_op(const int opc, const NoOverflowInt& scale) const {
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

MemPointer::Base MemPointer::Base::make(Node* pointer, const GrowableArray<MemPointerSummand>& summands) {
  // Bad form -> unknown.
  AddPNode* adr = pointer->isa_AddP();
  if (adr == nullptr) { return Base(); }

  // Non-TOP base -> object.
  Node* maybe_object_base = adr->in(AddPNode::Base);
  bool is_object_base = !maybe_object_base->is_top();

  Node* base = find_base(is_object_base ? maybe_object_base : nullptr, summands);

  if (base == nullptr) {
    // Not found -> unknown.
    return Base();
  } else if (is_object_base) {
    assert(base == maybe_object_base, "we confirmed that it is in summands");
    return Base(Object, base);
  } else {
    return Base(Native, base);
  }
}

Node* MemPointer::Base::find_base(Node* object_base, const GrowableArray<MemPointerSummand>& summands) {
  for (int i = 0; i < summands.length(); i++) {
    const MemPointerSummand& s = summands.at(i);
    assert(s.variable() != nullptr, "no empty summands");
    // Object base.
    if (object_base != nullptr && s.variable() == object_base && s.scale().is_one()) {
      return object_base;
    }
    // Native base.
    if (object_base == nullptr &&
        s.scale().is_one() &&
        MemPointerParser::is_native_memory_base_candidate(s.variable())) {
      return s.variable();
    }
  }
  return nullptr;
}

// Compute the aliasing between two MemPointer. We use the "MemPointer Lemma" to prove that the
// computed aliasing also applies for the underlying pointers. Note that the condition (S0) is
// already given, because the MemPointer is always constructed using only safe decompositions.
//
// Pre-Condition:
//   We assume that both pointers are in-bounds of their respective memory object. If this does
//   not hold, for example, with the use of Unsafe, then we would already have undefined behavior,
//   and we are allowed to do anything.
MemPointerAliasing MemPointer::get_aliasing_with(const MemPointer& other
                                                 NOT_PRODUCT( COMMA const TraceMemPointer& trace) ) const {
#ifndef PRODUCT
  if (trace.is_trace_aliasing()) {
    tty->print_cr("MemPointer::get_aliasing_with:");
    print_on(tty);
    other.print_on(tty);
  }
#endif

  // "MemPointer Lemma" condition (S2): check if all summands are the same:
  bool has_same_base = false;
  if (has_different_object_base_but_otherwise_same_summands_as(other)) {
    // At runtime, the two object bases can be:
    //   (1) different: we have no aliasing, pointers point to different memory objects.
    //   (2) the same:  implies that all summands are the same, (S2) holds.
    has_same_base = false;
  } else if (has_same_summands_as(other)) {
    // (S2) holds. If all summands are the same, also the base must be the same.
    has_same_base = true;
  } else {
#ifndef PRODUCT
    if (trace.is_trace_aliasing()) {
      tty->print_cr("  -> Aliasing unknown, summands are not the same.");
    }
#endif
    return MemPointerAliasing::make_unknown();
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

  if (has_same_base) {
    // "MemPointer Lemma" condition (S1):
    //   Given that all summands are the same, we know that both pointers point into the
    //   same memory object. With the Pre-Condition, we know that both pointers are in
    //   bounds of that same memory object.
    //
    // Hence, all 4 conditions of the "MemPointer Lemma" are established, and hence
    // we know that the distance between the underlying pointers is equal to the distance
    // we computed for the MemPointers:
    //   p_other - p_this = distance = other.con - this.con
#ifndef PRODUCT
    if (trace.is_trace_aliasing()) {
      tty->print_cr("  -> Aliasing always at distance = %d.", distance.value());
    }
#endif
    return MemPointerAliasing::make_always_at_distance(distance.value());
  } else {
    // At runtime, the two object bases can be:
    //   (1) different: pointers do not alias.
    //   (2) the same:  implies that (S2) holds. The summands are all the same, and with
    //                  the Pre-Condition, we know that both pointers are in bounds of the
    //                  same memory object, i.e. (S1) holds. We have already proven (S0)
    //                  and (S3), so all 4 conditions for "MemPointer Lemma" are given.
#ifndef PRODUCT
    if (trace.is_trace_aliasing()) {
      tty->print_cr("  -> Aliasing not or at distance = %d.", distance.value());
    }
#endif
    return MemPointerAliasing::make_not_or_at_distance(distance.value());
  }
}

bool MemPointer::has_same_summands_as(const MemPointer& other, uint start) const {
  for (uint i = start; i < SUMMANDS_SIZE; i++) {
    if (summands_at(i) != other.summands_at(i)) { return false; }
  }
  return true;
}

bool MemPointer::has_different_object_base_but_otherwise_same_summands_as(const MemPointer& other) const {
  if (!base().is_object() ||
      !other.base().is_object() ||
      base().object() == other.base().object()) {
    // The base is the same, or we do not know if the base is different.
    return false;
  }

#ifdef ASSERT
  const MemPointerSummand base1(base().object(),       NoOverflowInt(1));
  const MemPointerSummand base2(other.base().object(), NoOverflowInt(1));
  assert(summands_at(0) == base1 && other.summands_at(0) == base2, "bases in 0th element");
#endif

  // Check if all other summands are the same.
  return has_same_summands_as(other, 1);
}

// Examples:
//   p1 = MemPointer[size=1, base + i + 16]
//   p2 = MemPointer[size=1, base + i + 17]
//   -> Always at distance 1
//   -> p1 always adjacent and before p2 -> return true
//
//   p1 = MemPointer[size=4, x + y + z + 4L * i + 16]
//   p2 = MemPointer[size=4, x + y + z + 4L * i + 20]
//   -> Always at distance 4
//   -> p1 always adjacent and before p2 -> return true
//
//   p1 = MemPointer[size=4, base1 + 4L * i1 + 16]
//   p2 = MemPointer[size=4, base2 + 4L * i2 + 20]
//   -> Have differing summands, distance is unknown
//   -> Unknown if adjacent at runtime -> return false
bool MemPointer::is_adjacent_to_and_before(const MemPointer& other) const {
  const MemPointerAliasing aliasing = get_aliasing_with(other NOT_PRODUCT( COMMA _trace ));
  const bool is_adjacent = aliasing.is_always_at_distance(_size);

#ifndef PRODUCT
  if (_trace.is_trace_adjacency()) {
    tty->print("Adjacent: %s, because size = %d and aliasing = ",
               is_adjacent ? "true" : "false", _size);
    aliasing.print_on(tty);
    tty->cr();
  }
#endif

  return is_adjacent;
}

// Examples:
//   p1 = MemPointer[size=1, base + i + 16]
//   p2 = MemPointer[size=1, base + i + 17]
//   -> Always at distance 1
//   -> Can never overlap -> return true
//
//   p1 = MemPointer[size=1, base + i + 16]
//   p2 = MemPointer[size=1, base + i + 16]
//   -> Always at distance 0
//   -> Always have exact overlap -> return false
//
//   p1 = MemPointer[size=4, x + y + z + 4L * i + 16]
//   p2 = MemPointer[size=4, x + y + z + 4L * i + 56]
//   -> Always at distance 40
//   -> Can never overlap -> return true
//
//   p1 = MemPointer[size=8, x + y + z + 4L * i + 16]
//   p2 = MemPointer[size=8, x + y + z + 4L * i + 20]
//   -> Always at distance 4
//   -> Always have partial overlap -> return false
//
//   p1 = MemPointer[size=4, base1 + 4L * i1 + 16]
//   p2 = MemPointer[size=4, base2 + 4L * i2 + 20]
//   -> Have differing summands, distance is unknown
//   -> Unknown if overlap at runtime -> return false
bool MemPointer::never_overlaps_with(const MemPointer& other) const {
  const MemPointerAliasing aliasing = get_aliasing_with(other NOT_PRODUCT( COMMA _trace ));

  // The aliasing tries to compute:
  //   distance = other - this
  //
  // We know that we have no overlap if we can prove:
  //   this >= other + other.size      ||  this + this.size <= other
  //
  // Which we can restate as:
  //   distance <= -other.size         ||  this.size <= distance
  //
  const jint distance_lo = -other.size();
  const jint distance_hi = size();
  bool is_never_overlap = aliasing.is_never_in_distance_range(distance_lo, distance_hi);

#ifndef PRODUCT
  if (_trace.is_trace_overlap()) {
    tty->print("Never Overlap: %s, distance_lo: %d, distance_hi: %d, aliasing: ",
               is_never_overlap ? "true" : "false", distance_lo, distance_hi);
    aliasing.print_on(tty);
    tty->cr();
  }
#endif

  return is_never_overlap;
}


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

#ifndef SHARE_OPTO_MEMPOINTER_HPP
#define SHARE_OPTO_MEMPOINTER_HPP

#include "opto/memnode.hpp"
#include "opto/noOverflowInt.hpp"

// The MemPointer is a shared facility to parse pointers and check the aliasing of pointers,
// e.g. checking if two stores are adjacent.
//
// -----------------------------------------------------------------------------------------
//
// Intuition and Examples:
//   We parse / decompose pointers into a linear form:
//
//     pointer = SUM(scale_i * variable_i) + con
//
//   where SUM() adds all "scale_i * variable_i" for each i together.
//
//   The con and scale_i are compile-time constants (NoOverflowInt), and the variable_i are
//   compile-time variables (C2 nodes).
//
//   For the MemPointer, we do not explicitly track the base address. For Java heap pointers, the
//   base address is just a variable in a summand with scale == 1. For native memory (C heap)
//   pointers, the base address is null, and is hence implicitly a zero constant.
//
//
//   Example 1: byte array access:
//
//     array[i]
//
//     pointer =           array_base + ARRAY_BYTE_BASE_OFFSET + 1       * i
//             = 1       * array_base + ARRAY_BYTE_BASE_OFFSET + 1       * i
//               --------------------   ----------------------   --------------------
//             = scale_0 * variable_0 + con                    + scale_1 * variable_1
//
//
//   Example 2: int array access
//
//     array[5 + i + 3 * j]
//
//     pointer =           array_base + ARRAY_INT_BASE_OFFSET + 4 * 5 + 4       * i          + 4       * 3 * j
//             = 1       * array_base + ARRAY_INT_BASE_OFFSET + 20    + 4       * i          + 12      * j
//               --------------------   -----------------------------   --------------------   --------------------
//             = scale_0 * variable_0 + con                           + scale_1 * variable_1 + scale_2 * variable_2
//
//
//   Example 3: Unsafe with int array
//
//     UNSAFE.getInt(array, ARRAY_INT_BASE_OFFSET + 4 * i);
//
//     pointer =           array_base + ARRAY_INT_BASE_OFFSET + 4       * i
//             = 1       * array_base + ARRAY_INT_BASE_OFFSET + 4       * i
//               --------------------   ---------------------   --------------------
//             = scale_0 * variable_0 + con                   + scale_1 * variable_1
//
//
//   Example 4: Unsafe with native memory address
//
//     long address;
//     UNSAFE.getInt(null, address + 4 * i);
//
//     pointer =           address          + 4       * i
//             = 1       * address    + 0   + 4       * i
//               --------------------   ---   --------------------
//             = scale_0 * variable_0 + con + scale_1 * variable_1
//
//
//   Example 5: MemorySegment with byte array as backing type
//
//     byte[] array = new byte[1000];
//     MemorySegment ms = MemorySegment.ofArray(array);
//     assert ms.heapBase().get() == array: "array is base";
//     assert ms.address() == 0: "zero offset from base";
//     byte val = ms.get(ValueLayout.JAVA_BYTE, i);
//
//     pointer =           ms.heapBase() + ARRAY_BYTE_BASE_OFFSET + ms.address() +           i
//             = 1       * array_base    + ARRAY_BYTE_BASE_OFFSET + 0            + 1       * i
//               -----------------------   -------------------------------------   --------------------
//             = scale_0 * variable_0    + con                                   + scale_1 * variable_1
//
//
//   Example 6: MemorySegment with native memory
//
//     MemorySegment ms = Arena.ofAuto().allocate(1000, 1);
//     assert ms.heapBase().isEmpty(): "null base";
//     assert ms.address() != 0: "non-zero native memory address";
//     short val = ms.get(ValueLayout.JAVA_SHORT, 2L * i);
//
//     pointer = ms.heapBase() +           ms.address() + 2         i
//             = 0             + 1       * ms.address() + 2       * i
//               ------------    ----------------------   --------------------
//             = con             scale_0 * variable_0   + scale_1 * variable_1
//
//
//   Example 7: Non-linear access to int array
//
//     array[5 + i + j * k]
//
//     pointer =           array_base + ARRAY_INT_BASE_OFFSET + 4 * 5 + 4       * i          + 4       * j * k
//             = 1       * array_base + ARRAY_INT_BASE_OFFSET + 20    + 4       * i          + 4       * j * k
//               --------------------   -----------------------------   --------------------   --------------------
//             = scale_0 * variable_0 + con                           + scale_1 * variable_1 + scale_2 * variable_2
//
//     Note: we simply stop parsing once a term is not linear. We keep "j * k" as its own variable.
//
//
//   Example 8: Unsafe with native memory address, non-linear access
//
//     UNSAFE.getInt(null, i * j);
//
//     pointer =                 i * j
//             = 0   + 1       * i * j
//               ---   --------------------
//             = con + scale_0 * variable_0
//
//     Note: we can always parse a pointer into its trivial linear form:
//
//             pointer = 0 + 1 * pointer.
//
// -----------------------------------------------------------------------------------------
//
// MemPointerDecomposedForm:
//   When the pointer is parsed, it is decomposed into a SUM of summands plus a constant:
//
//     pointer = SUM(summands) + con
//
//   Where each summand_i in summands has the form:
//
//     summand_i = scale_i * variable_i
//
//   Hence, the full decomposed form is:
//
//     pointer = SUM(scale_i * variable_i) + con
//
//   Note: the scale_i are compile-time constants (NoOverflowInt), and the variable_i are
//         compile-time variables (C2 nodes).
//   On 64-bit systems, this decomposed form is computed with long-add/mul, on 32-bit systems
//   it is computed with int-add/mul.
//
// MemPointerAliasing:
//   The decomposed form allows us to determine the aliasing between two pointers easily. For
//   example, if two pointers are identical, except for their constant:
//
//     pointer1 = SUM(summands) + con1
//     pointer2 = SUM(summands) + con2
//
//   then we can easily compute the distance between the pointers (distance = con2 - con1),
//   and determine if they are adjacent.
//
// MemPointerDecomposedFormParser:
//   Any pointer can be parsed into this (default / trivial) decomposed form:
//
//     pointer = 1       * pointer    + 0
//               scale_0 * variable_0 + con
//
//   However, this is not particularly useful to compute aliasing. We would like to decompose
//   the pointer as far as possible, i.e. extract as many summands and add up the constants to
//   a single constant.
//
//   Example (normal int-array access):
//     pointer1 = array[i + 0] = array_base + array_int_base_offset + 4L * ConvI2L(i + 0)
//     pointer2 = array[i + 1] = array_base + array_int_base_offset + 4L * ConvI2L(i + 1)
//
//     At first, computing the aliasing is not immediately straight-forward in the general case because
//     the distance is hidden inside the ConvI2L. We can convert this (with array_int_base_offset = 16)
//     into these decomposed forms:
//
//     pointer1 = 1L * array_base + 4L * i + 16L
//     pointer2 = 1L * array_base + 4L * i + 20L
//
//     This allows us to easily see that these two pointers are adjacent (distance = 4).
//
//   Hence, in MemPointerDecomposedFormParser::parse_decomposed_form, we start with the pointer as
//   a trivial summand. A summand can either be decomposed further or it is terminal (cannot
//   be decomposed further). We decompose the summands recursively until all remaining summands
//   are terminal, see MemPointerDecomposedFormParser::parse_sub_expression. This effectively parses
//   the pointer expression recursively.
//
// -----------------------------------------------------------------------------------------
//
//   We have to be careful on 64-bit systems with ConvI2L: decomposing its input is not
//   correct in general, overflows may not be preserved in the decomposed form:
//
//     AddI:     ConvI2L(a +  b)    != ConvI2L(a) +  ConvI2L(b)
//     SubI:     ConvI2L(a -  b)    != ConvI2L(a) -  ConvI2L(b)
//     MulI:     ConvI2L(a *  conI) != ConvI2L(a) *  ConvI2L(conI)
//     LShiftI:  ConvI2L(a << conI) != ConvI2L(a) << ConvI2L(conI)
//
//   If we want to prove the correctness of MemPointerAliasing, we need some guarantees,
//   that the MemPointers adequately represent the underlying pointers, such that we can
//   compute the aliasing based on the summands and constants.
//
// -----------------------------------------------------------------------------------------
//
//   Below, we will formulate a "MemPointer Lemma" that helps us to prove the correctness of
//   the MemPointerAliasing computations. To prove the "MemPointer Lemma", we need to define
//   the idea of a "safe decomposition", and then prove that all the decompositions we apply
//   are such "safe decompositions".
//
//
// Definition: Safe decomposition
//   Trivial decomposition:
//     (SAFE0) The trivial decomposition from p to mp_0 = 0 + 1 * p is always safe.
//
//   Non-trivial decomposition:
//     We decompose summand in:
//       mp_i     = con + summand                     + SUM(other_summands)
//     resulting in:      +-------------------------+
//       mp_{i+1} = con + dec_con + SUM(dec_summands) + SUM(other_summands)
//                = new_con + SUM(new_summands)
//   where mp_i means that the original pointer p was decomposed i times.
//
//   We call a non-trivial decomposition safe if either:
//     (SAFE1) No matter the values of the summand variables:
//               mp_i = mp_{i+1}
//
//     (SAFE2) The pointer is on an array with a known array_element_size_in_bytes,
//             and there is an integer x, such that:
//               mp_i = mp_{i+1} + x * array_element_size_in_bytes * 2^32
//
//             Note: if "x = 0", we have "mp1 = mp2", and if "x != 0", then mp1 and mp2
//                   have a distance at least twice as large as the array size, and so
//                   at least one of mp1 or mp2 must be out of bounds of the array.
//
// MemPointer Lemma:
//    Given two pointers p1 and p2, and their respective MemPointers mp1 and mp2.
//    If these conditions hold:
//      (S0) mp1 and mp2 are constructed only with safe decompositions (SAFE0, SAFE1, SAFE2)
//           from p1 and p2, respectively.
//      (S1) Both p1 and p2 are within the bounds of the same memory object.
//      (S2) The constants do not differ too much: abs(mp1.con - mp2.con) < 2^31.
//      (S3) All summands of mp1 and mp2 are identical (i.e. only the constants are possibly different).
//
//    then the pointer difference between p1 and p2 is identical to the difference between
//    mp1 and mp2:
//      p1 - p2 = mp1 - mp2
//
//    Note: MemPointerDecomposedForm::get_aliasing_with relies on this MemPointer Lemma to
//          prove the correctness of its aliasing computation between two MemPointers.
//
//
//    Note: MemPointerDecomposedFormParser::is_safe_to_decompose_op checks that all
//          decompositions we apply are safe.
//
//
//  Proof of the "MemPointer Lemma":
//    Assume (S0-S3) and show that
//      p1 - p2 = mp1 - mp2
//
//    We make a case distinction over the types of decompositions used in the construction of mp1 and mp2.
//
//    Trivial Case: Only trivial (SAFE0) decompositions were used:
//      mp1 = 0 + 1 * p1 = p1
//      mp2 = 0 + 1 * p2 = p2
//      =>
//      p1 - p2 = mp1 - mp2
//
//    Unsafe Case: We apply at least one unsafe decomposition:
//      This is a contradiction to (S0) and we are done.
//
//    Case 1: Only decomposition of type (SAFE0) and (SAFE1) are used:
//      We make an induction proof over the decompositions from p1 to mp1, starting with
//      the trivial decomposition (SAFE0):
//        mp1_0 = 0 + 1 * p1 = p1
//      Then for the i-th non-trivial decomposition (SAFE1) we know that
//        mp1_i = mp1_{i+1}
//      and hence, after the n-th non-trivial decomposition from p1:
//        p1 = mp1_0 = mp1_i = mp1_n = mp1
//      Analogously, we can prove:
//        p2 = mp2
//
//      p1 = mp1
//      p2 = mp2
//      =>
//      p1 - p2 = mp1 - mp2
//
//    Case 2: At least one decomposition of type (SAFE2) and no unsafe decomposition is used.
//      Given we have (SAFE2) decompositions, we know that we are operating on an array of
//      known array_element_size_in_bytes. We can weaken the guarantees from (SAFE1)
//      decompositions to the same guarantee as (SAFE2) decompositions. Hence all applied
//      non-trivial decompositions satisfy:
//        mp1_i = mp1_{i+1} + x1_i * array_element_size_in_bytes * 2^32
//      where x1_i = 0 for (SAFE1) decompositions.
//
//      We make an induction proof over the decompositions from p1 to mp1, starting with
//      the trivial decomposition (SAFE0):
//        mp1_0 = 0 + 1 * p1 = p1
//      Then for the i-th non-trivial decomposition (SAFE1) or (SAFE2), we know that
//        mp1_i = mp1_{i+1} + x1_i * array_element_size_in_bytes * 2^32
//      and hence, if mp1 was decomposed with n non-trivial decompositions (SAFE1) or (SAFE2) from p1:
//        p1 = mp1 + x1 * array_element_size_in_bytes * 2^32
//      where
//        x1 = SUM(x1_i)
//      Analogously, we can prove:
//        p2 = mp2 + x2 * array_element_size_in_bytes * 2^32
//
//      And hence, with x = x1 - x2 we have:
//        p1 - p2 = mp1 - mp2 + x * array_element_size_in_bytes * 2^32
//
//      If "x = 0", then it follows:
//        p1 - p2 = mp1 - mp2
//
//      If "x != 0", then:
//        abs(p1 - p2) =  abs(mp1 - mp2 + x * array_element_size_in_bytes * 2^32)
//                     >= abs(x * array_element_size_in_bytes * 2^32) - abs(mp1 - mp2)
//                            -- apply x != 0 --
//                     >= array_element_size_in_bytes * 2^32          - abs(mp1 - mp2)
//                                                                    -- apply (S3) --
//                     =  array_element_size_in_bytes * 2^32          - abs(mp1.con - mp2.con)
//                                                                        -- apply (S2) --
//                     >  array_element_size_in_bytes * 2^32          - 2^31
//                        -- apply array_element_size_in_bytes > 0 --
//                     >= array_element_size_in_bytes * 2^31
//                     >= max_possible_array_size_in_bytes
//                     >= array_size_in_bytes
//
//        This shows that p1 and p2 have a distance greater than the array size, and hence at least one of the two
//        pointers must be out of bounds. This contradicts our assumption (S1) and we are done.


#ifndef PRODUCT
class TraceMemPointer : public StackObj {
private:
  const bool _is_trace_pointer;
  const bool _is_trace_aliasing;
  const bool _is_trace_adjacency;

public:
  TraceMemPointer(const bool is_trace_pointer,
                  const bool is_trace_aliasing,
                  const bool is_trace_adjacency) :
    _is_trace_pointer(  is_trace_pointer),
    _is_trace_aliasing( is_trace_aliasing),
    _is_trace_adjacency(is_trace_adjacency)
    {}

  bool is_trace_pointer()   const { return _is_trace_pointer; }
  bool is_trace_aliasing()  const { return _is_trace_aliasing; }
  bool is_trace_adjacency() const { return _is_trace_adjacency; }
};
#endif

// Class to represent aliasing between two MemPointer.
class MemPointerAliasing {
public:
  enum Aliasing {
    Unknown, // Distance unknown.
             //   Example: two "int[]" with different variable index offsets.
             //            e.g. "array[i]  vs  array[j]".
             //            e.g. "array1[i] vs  array2[j]".
    Always}; // Constant distance = p1 - p2.
             //   Example: The same address expression, except for a constant offset
             //            e.g. "array[i]  vs  array[i+1]".
private:
  const Aliasing _aliasing;
  const jint _distance;

  MemPointerAliasing(const Aliasing aliasing, const jint distance) :
    _aliasing(aliasing),
    _distance(distance)
  {
    assert(_distance != min_jint, "given by condition (S3) of MemPointer Lemma");
  }

public:
  static MemPointerAliasing make_unknown() {
    return MemPointerAliasing(Unknown, 0);
  }

  static MemPointerAliasing make_always(const jint distance) {
    return MemPointerAliasing(Always, distance);
  }

  // Use case: exact aliasing and adjacency.
  bool is_always_at_distance(const jint distance) const {
    return _aliasing == Always && _distance == distance;
  }

#ifndef PRODUCT
  void print_on(outputStream* st) const {
    switch(_aliasing) {
      case Unknown: st->print("Unknown");               break;
      case Always:  st->print("Always(%d)", _distance); break;
      default: ShouldNotReachHere();
    }
  }
#endif
};

// Summand of a MemPointerDecomposedForm:
//
//   summand = scale * variable
//
// where variable is a C2 node.
class MemPointerSummand : public StackObj {
private:
  Node* _variable;
  NoOverflowInt _scale;

public:
  MemPointerSummand() :
      _variable(nullptr),
      _scale(NoOverflowInt::make_NaN()) {}
  MemPointerSummand(Node* variable, const NoOverflowInt& scale) :
      _variable(variable),
      _scale(scale)
  {
    assert(_variable != nullptr, "must have variable");
    assert(!_scale.is_zero(), "non-zero scale");
  }

  Node* variable() const { return _variable; }
  NoOverflowInt scale() const { return _scale; }

  static int cmp_by_variable_idx(MemPointerSummand* p1, MemPointerSummand* p2) {
    if (p1->variable() == nullptr) {
      return (p2->variable() == nullptr) ? 0 : 1;
    } else if (p2->variable() == nullptr) {
      return -1;
    }

    return p1->variable()->_idx - p2->variable()->_idx;
  }

  friend bool operator==(const MemPointerSummand a, const MemPointerSummand b) {
    // Both "null" -> equal.
    if (a.variable() == nullptr && b.variable() == nullptr) { return true; }

    // Same variable and scale?
    if (a.variable() != b.variable()) { return false; }
    return a.scale() == b.scale();
  }

  friend bool operator!=(const MemPointerSummand a, const MemPointerSummand b) {
    return !(a == b);
  }

#ifndef PRODUCT
  void print_on(outputStream* st) const {
    st->print("Summand[");
    _scale.print_on(st);
    tty->print(" * [%d %s]]", _variable->_idx, _variable->Name());
  }
#endif
};

// Decomposed form of the pointer sub-expression of "pointer".
//
//   pointer = SUM(summands) + con
//
class MemPointerDecomposedForm : public StackObj {
private:
  // We limit the number of summands to 10. This is just a best guess, and not at this
  // point supported by evidence. But I think it is reasonable: usually, a pointer
  // contains a base pointer (e.g. array pointer or null for native memory) and a few
  // variables. It should be rare that we have more than 9 variables.
  static const int SUMMANDS_SIZE = 10;

  Node* _pointer; // pointer node associated with this (sub)pointer

  MemPointerSummand _summands[SUMMANDS_SIZE];
  NoOverflowInt _con;

public:
  // Empty
  MemPointerDecomposedForm() : _pointer(nullptr), _con(NoOverflowInt::make_NaN()) {}

private:
  // Default / trivial: pointer = 0 + 1 * pointer
  MemPointerDecomposedForm(Node* pointer) : _pointer(pointer), _con(NoOverflowInt(0)) {
    assert(pointer != nullptr, "pointer must be non-null");
    _summands[0] = MemPointerSummand(pointer, NoOverflowInt(1));
  }

  MemPointerDecomposedForm(Node* pointer, const GrowableArray<MemPointerSummand>& summands, const NoOverflowInt& con)
    : _pointer(pointer), _con(con) {
    assert(!_con.is_NaN(), "non-NaN constant");
    assert(summands.length() <= SUMMANDS_SIZE, "summands must fit");
    for (int i = 0; i < summands.length(); i++) {
      MemPointerSummand s = summands.at(i);
      assert(s.variable() != nullptr, "variable cannot be null");
      assert(!s.scale().is_NaN(), "non-NaN scale");
      _summands[i] = s;
    }
  }

public:
  static MemPointerDecomposedForm make_trivial(Node* pointer) {
    return MemPointerDecomposedForm(pointer);
  }

  static MemPointerDecomposedForm make(Node* pointer, const GrowableArray<MemPointerSummand>& summands, const NoOverflowInt& con) {
    if (summands.length() <= SUMMANDS_SIZE) {
      return MemPointerDecomposedForm(pointer, summands, con);
    } else {
      return MemPointerDecomposedForm::make_trivial(pointer);
    }
  }

  MemPointerAliasing get_aliasing_with(const MemPointerDecomposedForm& other
                                       NOT_PRODUCT( COMMA const TraceMemPointer& trace) ) const;

  const MemPointerSummand summands_at(const uint i) const {
    assert(i < SUMMANDS_SIZE, "in bounds");
    return _summands[i];
  }

  const NoOverflowInt con() const { return _con; }

#ifndef PRODUCT
  void print_on(outputStream* st) const {
    if (_pointer == nullptr) {
      st->print_cr("MemPointerDecomposedForm empty.");
      return;
    }
    st->print("MemPointerDecomposedForm[%d %s:  con = ", _pointer->_idx, _pointer->Name());
    _con.print_on(st);
    for (int i = 0; i < SUMMANDS_SIZE; i++) {
      const MemPointerSummand& summand = _summands[i];
      if (summand.variable() != nullptr) {
        st->print(", ");
        summand.print_on(st);
      }
    }
    st->print_cr("]");
  }
#endif
};

class MemPointerDecomposedFormParser : public StackObj {
private:
  const MemNode* _mem;

  // Internal data-structures for parsing.
  NoOverflowInt _con;
  GrowableArray<MemPointerSummand> _worklist;
  GrowableArray<MemPointerSummand> _summands;

  // Resulting decomposed-form.
  MemPointerDecomposedForm _decomposed_form;

public:
  MemPointerDecomposedFormParser(const MemNode* mem) : _mem(mem), _con(NoOverflowInt(0)) {
    _decomposed_form = parse_decomposed_form();
  }

  const MemPointerDecomposedForm decomposed_form() const { return _decomposed_form; }

private:
  MemPointerDecomposedForm parse_decomposed_form();
  void parse_sub_expression(const MemPointerSummand& summand);

  bool is_safe_to_decompose_op(const int opc, const NoOverflowInt& scale) const;
};

// Facility to parse the pointer of a Load or Store, so that aliasing between two such
// memory operations can be determined (e.g. adjacency).
class MemPointer : public StackObj {
private:
  const MemNode* _mem;
  const MemPointerDecomposedForm _decomposed_form;

  NOT_PRODUCT( const TraceMemPointer& _trace; )

public:
  MemPointer(const MemNode* mem NOT_PRODUCT( COMMA const TraceMemPointer& trace)) :
    _mem(mem),
    _decomposed_form(init_decomposed_form(_mem))
    NOT_PRODUCT( COMMA _trace(trace) )
  {
#ifndef PRODUCT
    if (_trace.is_trace_pointer()) {
      tty->print_cr("MemPointer::MemPointer:");
      tty->print("mem: "); mem->dump();
      _mem->in(MemNode::Address)->dump_bfs(5, nullptr, "d");
      _decomposed_form.print_on(tty);
    }
#endif
  }

  const MemNode* mem() const { return _mem; }
  const MemPointerDecomposedForm decomposed_form() const { return _decomposed_form; }
  bool is_adjacent_to_and_before(const MemPointer& other) const;

private:
  static const MemPointerDecomposedForm init_decomposed_form(const MemNode* mem) {
    assert(mem->is_Store(), "only stores are supported");
    ResourceMark rm;
    MemPointerDecomposedFormParser parser(mem);
    return parser.decomposed_form();
  }
};

#endif // SHARE_OPTO_MEMPOINTER_HPP

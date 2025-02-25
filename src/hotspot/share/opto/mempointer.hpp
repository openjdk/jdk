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

#ifndef SHARE_OPTO_MEMPOINTER_HPP
#define SHARE_OPTO_MEMPOINTER_HPP

#include "opto/memnode.hpp"
#include "opto/noOverflowInt.hpp"

// The MemPointer is a shared facility to parse pointers and check the aliasing of pointers.
//
// A MemPointer points to a region in memory, starting at a "pointer", and extending for "size" bytes:
//   [pointer, pointer + size)
//
// We can check if two loads / two stores:
//  - are adjacent               -> pack multiple memops into a single memop
//  - never overlap              -> independent, can swap order
//
// Other use-cases:
//  - alignment                  -> find an alignment solution for all memops in a vectorized loop
//  - detect partial overlap     -> indicates store-to-load-forwarding failures
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
// MemPointer:
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
//   Hence, in MemPointerParser::parse, we start with the pointer as a trivial summand. A summand can either
//   be decomposed further or it is terminal (cannot be decomposed further). We decompose the summands
//   recursively until all remaining summands are terminal, see MemPointerParser::parse_sub_expression.
//   This effectively parses the pointer expression recursively.
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
// MemPointer::Base
//   The MemPointer is decomposed like this:
//     pointer = SUM(summands) + con
//
//   This is sufficient for simple adjacency checks and we do not need to know if the pointer references
//   native (off-heap) or object (heap) memory. However, in some cases it is necessary or useful to know
//   the object base, or the native pointer's base.
//
//   - Object (heap) base (MemPointer::base().is_object()):
//     Is the base of the Java object, which resides on the Java heap.
//     Guarantees:
//       - Always has an alignment of ObjectAlignmentInBytes.
//       - A MemPointer with a given object base always must point into the memory of that object. Thus,
//         if we have two pointers with two different bases at runtime, we know the two pointers do not
//         alias.
//
//   - Native (off-heap) base (MemPointer::base().is_native()):
//     When we decompose a pointer to native memory, it is at first not clear that there is a base address.
//     Even if we could know that there is some base address to which we add index offsets, we cannot know
//     if this reference address points to the beginning of a native memory allocation or into the middle,
//     or outside it. We also have no guarantee for alignment with such a base address.
//
//     Still: we would like to find such a base if possible, and if two pointers are similar (i.e. have the
//     same summands), we would like to find the same base. Further, it is reasonable to speculatively
//     assume that such base addresses are aligned. We performs such a speculative alignment runtime check
//     in VTransform::add_speculative_alignment_check.
//
//     A base pointer must have scale = 1, and be accepted byMemPointer::is_native_memory_base_candidate.
//     It can thus be one of these:
//      (1) CastX2P
//          This is simply some arbitrary long cast to a pointer. It may be computed as an addition of
//          multiple long and even int values. In some cases this means that we could have further
//          decomposed the CastX2P, but at that point it is even harder to tell what should be a good
//          candidate for a native memory base.
//      (2) LoadL from field jdk.internal.foreign.NativeMemorySegmentImpl.min
//          This would be preferable over CastX2P, because it holds the address() of a native
//          MemorySegment, i.e. we know it points to the beginning of that MemorySegment.
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
//    Note: MemPointer::get_aliasing_with relies on this MemPointer Lemma to prove the correctness of its
//          aliasing computation between two MemPointers.
//
//
//    Note: MemPointerParser::is_safe_to_decompose_op checks that all decompositions we apply are safe.
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
  const bool _is_trace_parsing;
  const bool _is_trace_aliasing;
  const bool _is_trace_adjacency;
  const bool _is_trace_overlap;

public:
  TraceMemPointer(const bool is_trace_parsing,
                  const bool is_trace_aliasing,
                  const bool is_trace_adjacency,
                  const bool is_trace_overlap) :
    _is_trace_parsing(  is_trace_parsing),
    _is_trace_aliasing( is_trace_aliasing),
    _is_trace_adjacency(is_trace_adjacency),
    _is_trace_overlap(is_trace_overlap)
    {}

  bool is_trace_parsing()   const { return _is_trace_parsing; }
  bool is_trace_aliasing()  const { return _is_trace_aliasing; }
  bool is_trace_adjacency() const { return _is_trace_adjacency; }
  bool is_trace_overlap()   const { return _is_trace_overlap; }
};
#endif

// Class to represent aliasing between two MemPointer.
class MemPointerAliasing {
private:
  enum Aliasing {
    Unknown,          // Distance unknown.
                      //   Example: two "int[]" (unknown if the same) with different variable index offsets:
                      //            e.g. "array[i]  vs  array[j]".
                      //            e.g. "array1[i] vs  array2[j]".
    AlwaysAtDistance, // Constant distance = p2 - p1.
                      //   Example: The same address expression, except for a constant offset:
                      //            e.g. "array[i]  vs  array[i+1]".
    NotOrAtDistance}; // At compile-time, we know that at run-time it is either of these:
                      //   (1) Not: The pointers belong to different memory objects. Distance unknown.
                      //   (2) AtConstDistance: distance = p2 - p1.
                      //   Example: two "int[]" (unknown if the same) with indices that only differ by a
                      //            constant offset:
                      //            e.g. "array1[i] vs array2[i+4]":
                      //                 if "array1 == array2": distance = 4.
                      //                 if "array1 != array2": different memory objects.
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

  static MemPointerAliasing make_always_at_distance(const jint distance) {
    return MemPointerAliasing(AlwaysAtDistance, distance);
  }

  static MemPointerAliasing make_not_or_at_distance(const jint distance) {
    return MemPointerAliasing(NotOrAtDistance, distance);
  }

  // Use case: exact aliasing and adjacency.
  bool is_always_at_distance(const jint distance) const {
    return _aliasing == AlwaysAtDistance && _distance == distance;
  }

  // Use case: overlap.
  // Note: the bounds are exclusive: lo < element < hi
  bool is_never_in_distance_range(const jint distance_lo, const jint distance_hi) const {
    return (_aliasing == AlwaysAtDistance || _aliasing == NotOrAtDistance) &&
           (_distance <= distance_lo || distance_hi <= _distance);
  }

#ifndef PRODUCT
  void print_on(outputStream* st) const {
    switch(_aliasing) {
      case Unknown:           st->print("Unknown");                         break;
      case AlwaysAtDistance:  st->print("AlwaysAtDistance(%d)", _distance); break;
      case NotOrAtDistance:   st->print("NotOrAtDistance(%d)",  _distance); break;
      default: ShouldNotReachHere();
    }
  }
#endif
};

// Summand of a MemPointer:
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
    return cmp_by_variable_idx(*p1, *p2);
  }

  static int cmp_by_variable_idx(const MemPointerSummand& p1, const MemPointerSummand& p2) {
    if (p1.variable() == nullptr) {
      return (p2.variable() == nullptr) ? 0 : 1;
    }
    if (p2.variable() == nullptr) {
      return -1;
    }
    return p1.variable()->_idx - p2.variable()->_idx;
  }

  static int cmp(const MemPointerSummand& p1, const MemPointerSummand& p2) {
    int cmp = cmp_by_variable_idx(p1, p2);
    if (cmp != 0) { return cmp; }

    return NoOverflowInt::cmp(p1.scale(), p2.scale());
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
    _scale.print_on(st);
    tty->print(" * [%d %s]", _variable->_idx, _variable->Name());
  }
#endif
};

// Parsing calls the callback on every decomposed node. These are all the
// nodes on the paths from the pointer to the summand variables, i.e. the
// "inner" nodes of the pointer expression. This callback is for example
// used in SuperWord::unrolling_analysis to collect all inner nodes of a
// pointer expression.
class MemPointerParserCallback : public StackObj {
private:
  static MemPointerParserCallback _empty;

public:
  virtual void callback(Node* n) { /* do nothing by default */ }

  // Singleton for default arguments.
  static MemPointerParserCallback& empty() { return _empty; }
};

// A MemPointer points to a region in memory, starting at a "pointer", and extending
// for "size" bytes:
//
//   [pointer, pointer + size)
//
// Where the "pointer" is decomposed into the following form:
//
//   pointer = SUM(summands) + con
//   pointer = SUM(scale_i * variable_i) + con
//
// Where SUM() adds all "scale_i * variable_i" for each i together.
//
// Note: if the base is known, then it is in the 0th summand. A base can be:
//       - on-heap  / object: base().object()
//       - off-heap / native: base().native()
//
//   pointer = scale_0 * variable_0 + scale_1 * scale_1 + ... + con
//   pointer =       1 * base       + scale_1 * scale_1 + ... + con
//
class MemPointer : public StackObj {
public:
  // We limit the number of summands to 10. This is just a best guess, and not at this
  // point supported by evidence. But I think it is reasonable: usually, a pointer
  // contains a base pointer (e.g. array pointer or null for native memory) and a few
  // variables. It should be rare that we have more than 9 variables.
  static const int SUMMANDS_SIZE = 10;

  // A base can be:
  // - Known:
  //   - On-heap: Object
  //   - Off-heap: Native
  // - Unknown
  class Base : public StackObj {
  private:
    enum Kind { Unknown, Object, Native };
    Kind _kind;
    Node* _base;

    Base(Kind kind, Node* base) : _kind(kind), _base(base) {
      assert((kind == Unknown) == (base == nullptr), "known base");
    }

  public:
    Base() : Base(Unknown, nullptr) {}
    static Base make(Node* pointer, const GrowableArray<MemPointerSummand>& summands);

    bool is_known()          const { return _kind != Unknown; }
    bool is_object()         const { return _kind == Object; }
    bool is_native()         const { return _kind == Native; }
    Node* object()           const { assert(is_object(), "unexpected kind"); return _base; }
    Node* native()           const { assert(is_native(), "unexpected kind"); return _base; }
    Node* object_or_native() const { assert(is_known(),  "unexpected kind"); return _base; }
    Node* object_or_native_or_null() const { return _base; }

#ifndef PRODUCT
    void print_on(outputStream* st) const {
      switch (_kind) {
      case Object:
          st->print("object  ");
          st->print("%d %s", _base->_idx, _base->Name());
          break;
      case Native:
          st->print("native  ");
          st->print("%d %s", _base->_idx, _base->Name());
          break;
      default:
          st->print("unknown ");
      };
    }
#endif

  private:
    static Node* find_base(Node* object_base, const GrowableArray<MemPointerSummand>& summands);
  };

private:
  MemPointerSummand _summands[SUMMANDS_SIZE];
  const NoOverflowInt _con;
  const Base _base;
  const jint _size;
  NOT_PRODUCT( const TraceMemPointer& _trace; )

  // Default / trivial: pointer = 0 + 1 * pointer
  MemPointer(Node* pointer,
             const jint size
             NOT_PRODUCT(COMMA const TraceMemPointer& trace)) :
    _con(NoOverflowInt(0)),
    _base(Base()),
    _size(size)
    NOT_PRODUCT(COMMA _trace(trace))
  {
    assert(pointer != nullptr, "pointer must be non-null");
    _summands[0] = MemPointerSummand(pointer, NoOverflowInt(1));
    assert(1 <= _size && _size <= 2048 && is_power_of_2(_size), "sanity: no vector is expected to be larger");
  }

  // pointer = SUM(SUMMANDS) + con
  MemPointer(Node* pointer,
             const GrowableArray<MemPointerSummand>& summands,
             const NoOverflowInt& con,
             const jint size
             NOT_PRODUCT(COMMA const TraceMemPointer& trace)) :
    _con(con),
    _base(Base::make(pointer, summands)),
    _size(size)
    NOT_PRODUCT(COMMA _trace(trace))
  {
    assert(!_con.is_NaN(), "non-NaN constant");
    assert(summands.length() <= SUMMANDS_SIZE, "summands must fit");
#ifdef ASSERT
    for (int i = 0; i < summands.length(); i++) {
      const MemPointerSummand& s = summands.at(i);
      assert(s.variable() != nullptr, "variable cannot be null");
      assert(!s.scale().is_NaN(), "non-NaN scale");
    }
#endif

    // Put the base in the 0th summand.
    Node* base = _base.object_or_native_or_null();
    int pos = 0;
    if (base != nullptr) {
      MemPointerSummand b(base, NoOverflowInt(1));
      _summands[0] = b;
      pos++;
    }
    // Put all other summands afterward.
    for (int i = 0; i < summands.length(); i++) {
      const MemPointerSummand& s = summands.at(i);
      if (s.variable() == base && s.scale().is_one()) { continue; }
      _summands[pos++] = summands.at(i);
    }
    assert(pos == summands.length(), "copied all summands");

    assert(1 <= _size && _size <= 2048 && is_power_of_2(_size), "sanity: no vector is expected to be larger");
  }

  // Mutated copy.
  //   The new MemPointer is identical, except it has a different size and con.
  MemPointer(const MemPointer& old,
             const NoOverflowInt new_con,
             const jint new_size) :
    _con(new_con),
    _base(old.base()),
    _size(new_size)
    NOT_PRODUCT(COMMA _trace(old._trace))
  {
    assert(!_con.is_NaN(), "non-NaN constant");
    for (int i = 0; i < SUMMANDS_SIZE; i++) {
      _summands[i] = old.summands_at(i);
    }
  }

public:
  // Parse pointer of MemNode. Delegates to MemPointerParser::parse.
  // callback: receives a callback for every decomposed (inner) node
  //           of the pointer expression.
  MemPointer(const MemNode* mem,
             MemPointerParserCallback& callback
             NOT_PRODUCT(COMMA const TraceMemPointer& trace));

  // Parse pointer of MemNode. Delegates to MemPointerParser::parse.
  MemPointer(const MemNode* mem
             NOT_PRODUCT(COMMA const TraceMemPointer& trace)) :
    MemPointer(mem, MemPointerParserCallback::empty() NOT_PRODUCT(COMMA trace)) {}

  static MemPointer make_trivial(Node* pointer,
                                 const jint size
                                 NOT_PRODUCT(COMMA const TraceMemPointer& trace)) {
    return MemPointer(pointer, size NOT_PRODUCT(COMMA trace));
  }

  static MemPointer make(Node* pointer,
                         const GrowableArray<MemPointerSummand>& summands,
                         const NoOverflowInt& con,
                         const jint size
                         NOT_PRODUCT(COMMA const TraceMemPointer& trace)) {
    if (summands.length() <= SUMMANDS_SIZE) {
      return MemPointer(pointer, summands, con, size NOT_PRODUCT(COMMA trace));
    } else {
      return MemPointer::make_trivial(pointer, size NOT_PRODUCT(COMMA trace));
    }
  }

  MemPointer make_with_size(const jint new_size) const {
    return MemPointer(*this, this->con(), new_size);
  };

  MemPointer make_with_con(const NoOverflowInt new_con) const {
    return MemPointer(*this, new_con, this->size());
  };

private:
  MemPointerAliasing get_aliasing_with(const MemPointer& other
                                       NOT_PRODUCT(COMMA const TraceMemPointer& trace)) const;

  bool has_same_summands_as(const MemPointer& other, uint start) const;
  bool has_same_summands_as(const MemPointer& other) const { return has_same_summands_as(other, 0); }
  bool has_different_object_base_but_otherwise_same_summands_as(const MemPointer& other) const;

public:
  bool has_same_non_base_summands_as(const MemPointer& other) const {
    if (!base().is_known() || !other.base().is_known()) {
      assert(false, "unknown base case is not answered optimally");
      return false;
    }
    // Known base at 0th summand: all other summands are non-base summands.
    return has_same_summands_as(other, 1);
  }

  const MemPointerSummand& summands_at(const uint i) const {
    assert(i < SUMMANDS_SIZE, "in bounds");
    return _summands[i];
  }

  const NoOverflowInt con() const { return _con; }
  const Base& base() const { return _base; }
  jint size() const { return _size; }

  static int cmp_summands(const MemPointer& a, const MemPointer& b) {
    for (int i = 0; i < SUMMANDS_SIZE; i++) {
      const MemPointerSummand& s_a = a.summands_at(i);
      const MemPointerSummand& s_b = b.summands_at(i);
      int cmp = MemPointerSummand::cmp(s_a, s_b);
      if (cmp != 0) { return cmp;}
    }
    return 0;
  }

  template<typename Callback>
  void for_each_non_empty_summand(Callback callback) const {
    for (int i = 0; i < SUMMANDS_SIZE; i++) {
      const MemPointerSummand& s = summands_at(i);
      if (s.variable() != nullptr) {
        callback(s);
      }
    }
  }

  bool is_adjacent_to_and_before(const MemPointer& other) const;
  bool never_overlaps_with(const MemPointer& other) const;

#ifndef PRODUCT
  void print_form_on(outputStream* st) const {
    if (_con.is_NaN()) {
      st->print_cr("empty");
      return;
    }
    _con.print_on(st);
    for (int i = 0; i < SUMMANDS_SIZE; i++) {
      const MemPointerSummand& summand = _summands[i];
      if (summand.variable() != nullptr) {
        st->print(" + ");
        summand.print_on(st);
      }
    }
  }

  void print_on(outputStream* st, bool end_with_cr = true) const {
    st->print("MemPointer[size: %2d, base: ", size());
    _base.print_on(st);
    st->print(", form: ");
    print_form_on(st);
    st->print("]");
    if (end_with_cr) { st->cr(); }
  }
#endif
};

// Utility class.
// MemPointerParser::parse takes a MemNode (load or store) and computes its MemPointer.
// It temporarily allocates dynamic data structures (GrowableArray) in the resource
// area. This way, the computed MemPointer does not have to have any dynamic data
// structures and can be copied freely by value.
class MemPointerParser : public StackObj {
private:
  const MemNode* _mem;

  // Internal data-structures for parsing.
  NoOverflowInt _con;
  GrowableArray<MemPointerSummand> _worklist;
  GrowableArray<MemPointerSummand> _summands;

  // Resulting decomposed-form.
  MemPointer _mem_pointer;

  MemPointerParser(const MemNode* mem,
                   MemPointerParserCallback& callback
                   NOT_PRODUCT(COMMA const TraceMemPointer& trace)) :
    _mem(mem),
    _con(NoOverflowInt(0)),
    _mem_pointer(parse(callback NOT_PRODUCT(COMMA trace))) {}

public:
  static MemPointer parse(const MemNode* mem,
                          MemPointerParserCallback& callback
                          NOT_PRODUCT(COMMA const TraceMemPointer& trace)) {
    assert(mem->is_Store() || mem->is_Load(), "only stores and loads are allowed");
    ResourceMark rm;
    MemPointerParser parser(mem, callback NOT_PRODUCT(COMMA trace));

#ifndef PRODUCT
    if (trace.is_trace_parsing()) {
      tty->print_cr("\nMemPointerParser::parse:");
      tty->print("  mem: "); mem->dump();
      parser.mem_pointer().print_on(tty);
      mem->in(MemNode::Address)->dump_bfs(7, nullptr, "d");
    }
#endif

    return parser.mem_pointer();
  }

  static bool is_native_memory_base_candidate(Node* n);

private:
  const MemPointer& mem_pointer() const { return _mem_pointer; }

  MemPointer parse(MemPointerParserCallback& callback
                   NOT_PRODUCT(COMMA const TraceMemPointer& trace));

  void parse_sub_expression(const MemPointerSummand& summand, MemPointerParserCallback& callback);
  static bool sub_expression_has_native_base_candidate(Node* n);

  bool is_safe_to_decompose_op(const int opc, const NoOverflowInt& scale) const;
};

#endif // SHARE_OPTO_MEMPOINTER_HPP

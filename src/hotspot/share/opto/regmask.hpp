/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_REGMASK_HPP
#define SHARE_OPTO_REGMASK_HPP

#include "code/vmreg.hpp"
#include "opto/optoreg.hpp"
#include "utilities/count_leading_zeros.hpp"
#include "utilities/count_trailing_zeros.hpp"

//-------------Non-zero bit search methods used by RegMask---------------------
// Find lowest 1, undefined if empty/0
static int find_lowest_bit(uint32_t mask) {
  return count_trailing_zeros(mask);
}
// Find highest 1, undefined if empty/0
static int find_highest_bit(uint32_t mask) {
  return count_leading_zeros(mask) ^ 31;
}

//------------------------------RegMask----------------------------------------
// The ADL file describes how to print the machine-specific registers, as well
// as any notion of register classes.  We provide a register mask, which is
// just a collection of Register numbers.

// The ADLC defines 2 macros, RM_SIZE and FORALL_BODY.
// RM_SIZE is the size of a register mask in words.
// FORALL_BODY replicates a BODY macro once per word in the register mask.
// The usage is somewhat clumsy and limited to the regmask.[h,c]pp files.
// However, it means the ADLC can redefine the unroll macro and all loops
// over register masks will be unrolled by the correct amount.

class RegMask {
  union {
    double _dummy_force_double_alignment[RM_SIZE>>1];
    // Array of Register Mask bits.  This array is large enough to cover
    // all the machine registers and all parameters that need to be passed
    // on the stack (stack registers) up to some interesting limit.  Methods
    // that need more parameters will NOT be compiled.  On Intel, the limit
    // is something like 90+ parameters.
    int _A[RM_SIZE];
  };
  // The low and high water marks represents the lowest and highest word
  // that might contain set register mask bits, respectively. We guarantee
  // that there are no bits in words outside this range, but any word at
  // and between the two marks can still be 0.
  int _lwm;
  int _hwm;

  enum {
    _WordBits    = BitsPerInt,
    _LogWordBits = LogBitsPerInt,
    _RM_SIZE     = RM_SIZE   // local constant, imported, then hidden by #undef
  };

 public:
  enum { CHUNK_SIZE = RM_SIZE*_WordBits };

  // SlotsPerLong is 2, since slots are 32 bits and longs are 64 bits.
  // Also, consider the maximum alignment size for a normally allocated
  // value.  Since we allocate register pairs but not register quads (at
  // present), this alignment is SlotsPerLong (== 2).  A normally
  // aligned allocated register is either a single register, or a pair
  // of adjacent registers, the lower-numbered being even.
  // See also is_aligned_Pairs() below, and the padding added before
  // Matcher::_new_SP to keep allocated pairs aligned properly.
  // If we ever go to quad-word allocations, SlotsPerQuad will become
  // the controlling alignment constraint.  Note that this alignment
  // requirement is internal to the allocator, and independent of any
  // particular platform.
  enum { SlotsPerLong = 2,
         SlotsPerVecS = 1,
         SlotsPerVecD = 2,
         SlotsPerVecX = 4,
         SlotsPerVecY = 8,
         SlotsPerVecZ = 16 };

  // A constructor only used by the ADLC output.  All mask fields are filled
  // in directly.  Calls to this look something like RM(1,2,3,4);
  RegMask(
#   define BODY(I) int a##I,
    FORALL_BODY
#   undef BODY
    int dummy = 0) {
#   define BODY(I) _A[I] = a##I;
    FORALL_BODY
#   undef BODY
    _lwm = 0;
    _hwm = RM_SIZE - 1;
    while (_hwm > 0 && _A[_hwm] == 0) _hwm--;
    while ((_lwm < _hwm) && _A[_lwm] == 0) _lwm++;
    assert(valid_watermarks(), "post-condition");
  }

  // Handy copying constructor
  RegMask(RegMask *rm) {
    _hwm = rm->_hwm;
    _lwm = rm->_lwm;
    for (int i = 0; i < RM_SIZE; i++) {
      _A[i] = rm->_A[i];
    }
    assert(valid_watermarks(), "post-condition");
  }

  // Construct an empty mask
  RegMask() {
    Clear();
  }

  // Construct a mask with a single bit
  RegMask(OptoReg::Name reg) {
    Clear();
    Insert(reg);
  }

  // Check for register being in mask
  int Member(OptoReg::Name reg) const {
    assert(reg < CHUNK_SIZE, "");
    return _A[reg>>_LogWordBits] & (1<<(reg&(_WordBits-1)));
  }

  // The last bit in the register mask indicates that the mask should repeat
  // indefinitely with ONE bits.  Returns TRUE if mask is infinite or
  // unbounded in size.  Returns FALSE if mask is finite size.
  int is_AllStack() const { return _A[RM_SIZE-1] >> (_WordBits-1); }

  // Work around an -xO3 optimization problme in WS6U1. The old way:
  //   void set_AllStack() { _A[RM_SIZE-1] |= (1<<(_WordBits-1)); }
  // will cause _A[RM_SIZE-1] to be clobbered, not updated when set_AllStack()
  // follows an Insert() loop, like the one found in init_spill_mask(). Using
  // Insert() instead works because the index into _A in computed instead of
  // constant.  See bug 4665841.
  void set_AllStack() { Insert(OptoReg::Name(CHUNK_SIZE-1)); }

  // Test for being a not-empty mask.
  int is_NotEmpty() const {
    assert(valid_watermarks(), "sanity");
    int tmp = 0;
    for (int i = _lwm; i <= _hwm; i++) {
      tmp |= _A[i];
    }
    return tmp;
  }

  // Find lowest-numbered register from mask, or BAD if mask is empty.
  OptoReg::Name find_first_elem() const {
    assert(valid_watermarks(), "sanity");
    for (int i = _lwm; i <= _hwm; i++) {
      int bits = _A[i];
      if (bits) {
        return OptoReg::Name((i<<_LogWordBits) + find_lowest_bit(bits));
      }
    }
    return OptoReg::Name(OptoReg::Bad);
  }

  // Get highest-numbered register from mask, or BAD if mask is empty.
  OptoReg::Name find_last_elem() const {
    assert(valid_watermarks(), "sanity");
    for (int i = _hwm; i >= _lwm; i--) {
      int bits = _A[i];
      if (bits) {
        return OptoReg::Name((i<<_LogWordBits) + find_highest_bit(bits));
      }
    }
    return OptoReg::Name(OptoReg::Bad);
  }

  // Clear out partial bits; leave only aligned adjacent bit pairs.
  void clear_to_pairs();

#ifdef ASSERT
  // Verify watermarks are sane, i.e., within bounds and that no
  // register words below or above the watermarks have bits set.
  bool valid_watermarks() const {
    assert(_hwm >= 0 && _hwm < RM_SIZE, "_hwm out of range: %d", _hwm);
    assert(_lwm >= 0 && _lwm < RM_SIZE, "_lwm out of range: %d", _lwm);
    for (int i = 0; i < _lwm; i++) {
      assert(_A[i] == 0, "_lwm too high: %d regs at: %d", _lwm, i);
    }
    for (int i = _hwm + 1; i < RM_SIZE; i++) {
      assert(_A[i] == 0, "_hwm too low: %d regs at: %d", _hwm, i);
    }
    return true;
  }
#endif // !ASSERT

  // Test that the mask contains only aligned adjacent bit pairs
  bool is_aligned_pairs() const;

  // mask is a pair of misaligned registers
  bool is_misaligned_pair() const;
  // Test for single register
  bool is_bound1() const;
  // Test for a single adjacent pair
  bool is_bound_pair() const;
  // Test for a single adjacent set of ideal register's size.
  bool is_bound(uint ireg) const;

  // Find the lowest-numbered register set in the mask.  Return the
  // HIGHEST register number in the set, or BAD if no sets.
  // Assert that the mask contains only bit sets.
  OptoReg::Name find_first_set(const int size) const;

  // Clear out partial bits; leave only aligned adjacent bit sets of size.
  void clear_to_sets(const int size);
  // Smear out partial bits to aligned adjacent bit sets.
  void smear_to_sets(const int size);
  // Test that the mask contains only aligned adjacent bit sets
  bool is_aligned_sets(const int size) const;

  // Test for a single adjacent set
  int is_bound_set(const int size) const;

  static bool is_vector(uint ireg);
  static int num_registers(uint ireg);

  // Fast overlap test.  Non-zero if any registers in common.
  int overlap(const RegMask &rm) const {
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    int hwm = MIN2(_hwm, rm._hwm);
    int lwm = MAX2(_lwm, rm._lwm);
    int result = 0;
    for (int i = lwm; i <= hwm; i++) {
      result |= _A[i] & rm._A[i];
    }
    return result;
  }

  // Special test for register pressure based splitting
  // UP means register only, Register plus stack, or stack only is DOWN
  bool is_UP() const;

  // Clear a register mask
  void Clear() {
    _lwm = RM_SIZE - 1;
    _hwm = 0;
    memset(_A, 0, sizeof(int)*RM_SIZE);
    assert(valid_watermarks(), "sanity");
  }

  // Fill a register mask with 1's
  void Set_All() {
    _lwm = 0;
    _hwm = RM_SIZE - 1;
    memset(_A, 0xFF, sizeof(int)*RM_SIZE);
    assert(valid_watermarks(), "sanity");
  }

  // Insert register into mask
  void Insert(OptoReg::Name reg) {
    assert(reg < CHUNK_SIZE, "sanity");
    assert(valid_watermarks(), "pre-condition");
    int index = reg>>_LogWordBits;
    if (index > _hwm) _hwm = index;
    if (index < _lwm) _lwm = index;
    _A[index] |= (1<<(reg&(_WordBits-1)));
    assert(valid_watermarks(), "post-condition");
  }

  // Remove register from mask
  void Remove(OptoReg::Name reg) {
    assert(reg < CHUNK_SIZE, "");
    _A[reg>>_LogWordBits] &= ~(1<<(reg&(_WordBits-1)));
  }

  // OR 'rm' into 'this'
  void OR(const RegMask &rm) {
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    // OR widens the live range
    if (_lwm > rm._lwm) _lwm = rm._lwm;
    if (_hwm < rm._hwm) _hwm = rm._hwm;
    for (int i = _lwm; i <= _hwm; i++) {
      _A[i] |= rm._A[i];
    }
    assert(valid_watermarks(), "sanity");
  }

  // AND 'rm' into 'this'
  void AND(const RegMask &rm) {
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    // Do not evaluate words outside the current watermark range, as they are
    // already zero and an &= would not change that
    for (int i = _lwm; i <= _hwm; i++) {
      _A[i] &= rm._A[i];
    }
    // Narrow the watermarks if &rm spans a narrower range.
    // Update after to ensure non-overlapping words are zeroed out.
    if (_lwm < rm._lwm) _lwm = rm._lwm;
    if (_hwm > rm._hwm) _hwm = rm._hwm;
  }

  // Subtract 'rm' from 'this'
  void SUBTRACT(const RegMask &rm) {
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    int hwm = MIN2(_hwm, rm._hwm);
    int lwm = MAX2(_lwm, rm._lwm);
    for (int i = lwm; i <= hwm; i++) {
      _A[i] &= ~rm._A[i];
    }
  }

  // Compute size of register mask: number of bits
  uint Size() const;

#ifndef PRODUCT
  void print() const { dump(); }
  void dump(outputStream *st = tty) const; // Print a mask
#endif

  static const RegMask Empty;   // Common empty mask

  static bool can_represent(OptoReg::Name reg) {
    // NOTE: -1 in computation reflects the usage of the last
    //       bit of the regmask as an infinite stack flag and
    //       -7 is to keep mask aligned for largest value (VecZ).
    return (int)reg < (int)(CHUNK_SIZE-1);
  }
  static bool can_represent_arg(OptoReg::Name reg) {
    // NOTE: -SlotsPerVecZ in computation reflects the need
    //       to keep mask aligned for largest value (VecZ).
    return (int)reg < (int)(CHUNK_SIZE-SlotsPerVecZ);
  }
};

// Do not use this constant directly in client code!
#undef RM_SIZE

#endif // SHARE_OPTO_REGMASK_HPP

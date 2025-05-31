/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/globalDefinitions.hpp"
#include "memory/arena.hpp"

class LRG;

// To avoid unbounded RegMask growth and to be able to statically compute a
// register mask size upper bound (see RM_SIZE_MAX below), we need to set some
// form of limit on the number of stack slots used by BoxLockNodes. The limit
// below is rather arbitrary but should be quite generous and cover all
// practical cases. We reach this limit by, e.g., deeply nesting synchronized
// statements in Java.
const int BoxLockNode_SLOT_LIMIT = 200;

//-------------Non-zero bit search methods used by RegMask---------------------
// Find lowest 1, undefined if empty/0
static unsigned int find_lowest_bit(uintptr_t mask) {
  return count_trailing_zeros(mask);
}
// Find highest 1, undefined if empty/0
static unsigned int find_highest_bit(uintptr_t mask) {
  return count_leading_zeros(mask) ^ (BitsPerWord - 1U);
}

//------------------------------RegMask----------------------------------------
// The ADL file describes how to print the machine-specific registers, as well
// as any notion of register classes.  We provide a register mask, which is
// just a collection of Register numbers.

// The ADLC defines 3 macros, RM_SIZE, RM_SIZE_MIN, and FORALL_BODY.
// RM_SIZE is the base size of a register mask in 32-bit words.
// RM_SIZE_MIN is the theoretical minimum size of a register mask in 32-bit
// words.
// FORALL_BODY replicates a BODY macro once per word in the register mask.
// The usage is somewhat clumsy and limited to the regmask.[h,c]pp files.
// However, it means the ADLC can redefine the unroll macro and all loops
// over register masks will be unrolled by the correct amount.

class RegMask {

  friend class RegMaskIterator;

  // The RM_SIZE is aligned to 64-bit - assert that this holds
  LP64_ONLY(STATIC_ASSERT(is_aligned(RM_SIZE, 2)));

  static const unsigned int _WordBitMask = BitsPerWord - 1U;
  static const unsigned int _LogWordBits = LogBitsPerWord;
  static const unsigned int _RM_SIZE     = LP64_ONLY(RM_SIZE >> 1) NOT_LP64(RM_SIZE);
  static const unsigned int _RM_SIZE_MIN =
      LP64_ONLY(((RM_SIZE_MIN + 1) & ~1) >> 1) NOT_LP64(RM_SIZE_MIN);
  static const unsigned int _RM_MAX      = _RM_SIZE - 1U;

  // Compute a best-effort (statically known) upper bound for register mask
  // size in 32-bit words. When extending/growing register masks, we should
  // never grow past this size.
  static const unsigned int RM_SIZE_MAX =
      (((RM_SIZE_MIN << 5) +                // Slots for machine registers
        (max_method_parameter_length * 2) + // Slots for incoming arguments
        (max_method_parameter_length * 2) + // Slots for outgoing arguments
        BoxLockNode_SLOT_LIMIT +            // Slots for locks
        64                                  // Padding, reserved words, etc.
        ) + 31) >> 5; // Number of bits -> number of 32-bit words
  static const unsigned int _RM_SIZE_MAX =
      LP64_ONLY(((RM_SIZE_MAX + 1) & ~1) >> 1) NOT_LP64(RM_SIZE_MAX);

  // Sanity check
  STATIC_ASSERT(RM_SIZE <= RM_SIZE_MAX);

  // Ensure that register masks cannot grow beyond the point at which
  // OptoRegPair can no longer index the whole mask.
  STATIC_ASSERT(OptoRegPair::can_fit((RM_SIZE_MAX << 5) - 1));

  union {
    // Array of Register Mask bits.  This array is large enough to cover all
    // the machine registers and usually all parameters that need to be passed
    // on the stack (stack registers) up to some interesting limit. On Intel,
    // the limit is something like 90+ parameters.
    int       _RM_I[RM_SIZE];
    uintptr_t _RM_UP[_RM_SIZE];
  };

  // In rare situations (e.g., "more than 90+ parameters on Intel"), we need to
  // extend the register mask with dynamically allocated memory. We keep the
  // base statically allocated _RM_UP, and arena allocate the extended mask
  // (RM_UP_EXT) separately. Another, perhaps more elegant, option would be to
  // have two subclasses of RegMask, where one is statically allocated and one
  // is (entirely) dynamically allocated. Given that register mask extension is
  // rare, we decided to use the current approach (_RM_UP and _RM_UP_EXT) to
  // keep the common case fast. Most of the time, we will then not need to
  // dynamically allocate anything.
  //
  // We could use a GrowableArray here, but there are currently some
  // GrowableArray limitations that have a negative performance impact for our
  // use case:
  //
  // - There is no efficient copy/clone operation.
  // - GrowableArray construction currently default-initializes everything
  //   within the array's initial capacity, which is unnecessary in our case.
  //
  // After addressing these limitations, we should consider using a
  // GrowableArray here.
  uintptr_t* _RM_UP_EXT = nullptr;

#ifdef ASSERT
  // Register masks may get shallowly copied without the use of constructors,
  // which is problematic when dealing with the externally allocated memory for
  // _RM_UP_EXT. Therefore, we need some sanity checks to ensure we have not
  // missed any such cases. The below variables enable such checks.
  //
  // The original address of the _RM_UP_EXT variable, set when using
  // constructors. If we get copied/cloned, &_RM_UP_EXT will no longer equal
  // _original_ext_address.
  uintptr_t** _original_ext_address = &_RM_UP_EXT;
  //
  // If the original version, of which we may be a clone, is read-only. In such
  // cases, we can allow read-only sharing.
  bool _read_only = false;
#endif

  // Current total register mask size in words
  unsigned int _rm_size;

  // We support offsetting register masks to present different views of the
  // register space, mainly for use in PhaseChaitin::Select. The _offset
  // variable indicates how many words we offset with. We consider all
  // registers before the offset to not be included in the register mask.
  unsigned int _offset;

  // If _all_stack = true, we consider all registers beyond what the register
  // mask can currently represent to be included. If _all_stack = false, we
  // consider the registers not included.
  bool _all_stack = false;

  // The low and high watermarks represent the lowest and highest word that
  // might contain set register mask bits, respectively. We guarantee that
  // there are no bits in words outside this range, but any word at and between
  // the two marks can still be 0. We do not guarantee that the watermarks are
  // optimal. If _hwm < _lwm, the register mask is necessarily empty. Indeed,
  // when we construct empty register masks, we set _hwm = 0 and _lwm = max.
  unsigned int _lwm;
  unsigned int _hwm;

  // The following diagram illustrates the internal representation of a RegMask
  // (with _offset = 0, for a made-up platform with 10 registers and 4-bit
  // words) that has been extended with two additional words to represent more
  // stack locations:
  //                                            _hwm=3
  //                     _lwm=1                RM_SIZE=3                _rm_size=5
  //                       |                       |                        |
  //            r0 r1 r2 r3 r4 r5 r6 r7 r8 r9 s0 s1   s2 s3 s4 s5 s6 s7 s8 s9 s10 s11 ...
  // Content:  [0  0  0  0 |0  1  1  0 |0  0  1  0 ] [1  1  0  1 |0  0  0  0] as  as  as
  //   Index: [0]         [1]         [2]           [0]         [1]
  //
  //          \____________________________________/ \______________________/
  //                                 |                           |
  //                               RM_UP                     RM_UP_EXT
  //          \_____________________________________________________________/
  //                                          |
  //                                      _rm_size
  //
  // In this example, registers {r5, r6} and stack locations {s0, s2, s3, s5}
  // are included in the register mask. Depending on the value of _all_stack
  // (denoted with as), {s10, s11, ...} are all included (as = 1) or excluded
  // (as = 0). Note that all registers/stack locations under _lwm and over _hwm
  // are excluded. The exception is {s10, s11, ...}, where the value is decided
  // solely by _all_stack, regardless of the value of _hwm.
  //
  // The only operation that may update the _offset attribute is
  // RegMask::rollover(). This operation requires the register mask to be
  // clean/empty (all zeroes), except for _all_stack, which must be true, and
  // has the effect of increasing _offset by _rm_size and setting all bits (now
  // necessarily representing stack locations) to 1. Here is how the above
  // register mask looks like after clearing, setting _all_stack to true, and
  // successfully rolling over:
  //
  //          _lwm=0                                      RM_SIZE=3           _hwm=3      _rm_size=5
  //           |                                              |                 |             |
  //            s10 s11 s12 s13 s14 s15 s16 s17 s18 s19 s20 s21  s22 s23 s24 s25 s26 s27 s28 s29 s30 s31 ...
  // Content:  [1   1   1   1  |1   1   1   1  |1   1   1   1 ] [1   1   1   1  |1   1   1   1]  1   1   1
  //   Index: [0]             [1]             [2]              [0]             [1]
  //
  //          \_______________________________________________/ \_____________________________/
  //                                    |                                     |
  //                                  RM_UP                               RM_UP_EXT
  //          \_______________________________________________________________________________/
  //                                                  |
  //                                              _rm_size

  // Access word i in the register mask.
  const uintptr_t& rm_up(unsigned int i) const {
    assert(_read_only || _original_ext_address == &_RM_UP_EXT, "clone sanity check");
    assert(i < _rm_size, "sanity");
    if (i < _RM_SIZE) {
      return _RM_UP[i];
    } else {
      assert(_RM_UP_EXT != nullptr, "sanity");
      return _RM_UP_EXT[i - _RM_SIZE];
    }
  }

  // Non-const version of the above.
  uintptr_t& rm_up(unsigned int i) {
    assert(_original_ext_address == &_RM_UP_EXT, "clone sanity check");
    return const_cast<uintptr_t&>(const_cast<const RegMask*>(this)->rm_up(i));
  }

  // The maximum word index
  unsigned int rm_max() const {
    return _rm_size - 1U;
  }

  // Where to extend the register mask
  Arena* _arena;

  // Grow the register mask to ensure it can fit at least min_size words.
  void grow(unsigned int min_size, bool init = true) {
    if (min_size > _rm_size) {
      assert(min_size <= _RM_SIZE_MAX, "unexpected register mask growth");
      assert(_arena != nullptr, "register mask not growable");
      min_size = MIN2(_RM_SIZE_MAX, round_up_power_of_2(min_size));
      unsigned int old_size = _rm_size;
      unsigned int old_ext_size = old_size - _RM_SIZE;
      unsigned int new_ext_size = min_size - _RM_SIZE;
      _rm_size = min_size;
      if (_RM_UP_EXT == nullptr) {
        assert(old_ext_size == 0, "sanity");
        _RM_UP_EXT = NEW_ARENA_ARRAY(_arena, uintptr_t, new_ext_size);
      } else {
        assert(_original_ext_address == &_RM_UP_EXT, "clone sanity check");
        _RM_UP_EXT = REALLOC_ARENA_ARRAY(_arena, uintptr_t, _RM_UP_EXT,
                                         old_ext_size, new_ext_size);
      }
      if (init) {
        int fill = 0;
        if (is_AllStack()) {
          fill = 0xFF;
          _hwm = rm_max();
        }
        set_range(old_size, fill, _rm_size - old_size);
      }
    }
  }

  // Make us a copy of src
  void copy(const RegMask& src) {
    assert(_offset == src._offset, "offset mismatch");
    _hwm = src._hwm;
    _lwm = src._lwm;

    // Copy base mask
    memcpy(_RM_UP, src._RM_UP, sizeof(uintptr_t) * _RM_SIZE);
    _all_stack = src._all_stack;

    // Copy extension
    if (src._RM_UP_EXT != nullptr) {
      assert(src._rm_size > _RM_SIZE, "sanity");
      assert(_original_ext_address == &_RM_UP_EXT, "clone sanity check");
      grow(src._rm_size, false);
      memcpy(_RM_UP_EXT, src._RM_UP_EXT,
             sizeof(uintptr_t) * (src._rm_size - _RM_SIZE));
    }

    // If the source is smaller than us, we need to set the gap according to
    // the sources all_stack flag.
    if (src._rm_size < _rm_size) {
      int value = 0;
      if (src.is_AllStack()) {
        value = 0xFF;
        _hwm = rm_max();
      }
      set_range(src._rm_size, value, _rm_size - src._rm_size);
    }

    assert(valid_watermarks(), "post-condition");
  }

  void trim_watermarks() {
    if (_hwm < _lwm) {
      return;
    }
    while ((_hwm > _lwm) && rm_up(_hwm) == 0) {
      _hwm--;
    }
    while ((_lwm < _hwm) && rm_up(_lwm) == 0) {
      _lwm++;
    }
    if ((_lwm == _hwm) && rm_up(_lwm) == 0) {
      _lwm = rm_max();
      _hwm = 0;
    }
  }

  // Set a span of words in the register mask to a given value.
  void set_range(unsigned int start, int value, unsigned int length) {
    if (start < _RM_SIZE) {
      memset(_RM_UP + start, value,
             sizeof(uintptr_t) * MIN2((int)length, (int)_RM_SIZE - (int)start));
    }
    if (start + length > _RM_SIZE) {
      assert(_RM_UP_EXT != nullptr, "sanity");
      assert(_original_ext_address == &_RM_UP_EXT, "clone sanity check");
      memset(_RM_UP_EXT + MAX2((int)start - (int)_RM_SIZE, 0), value,
             sizeof(uintptr_t) *
                 MIN2((int)length, (int)length - ((int)_RM_SIZE - (int)start)));
    }
  }

public:
  unsigned int rm_size() const {
    return _rm_size;
  }
  unsigned int rm_size_bits() const {
    return _rm_size * BitsPerWord;
  }

  bool is_offset() const {
    return _offset > 0;
  }
  unsigned int offset_bits() const {
    return _offset * BitsPerWord;
  };

  bool is_AllStack() const {
    return _all_stack;
  }
  void set_AllStack(bool value) {
    _all_stack = value;
  }

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
         SlotsPerVecA = 4,
         SlotsPerVecS = 1,
         SlotsPerVecD = 2,
         SlotsPerVecX = 4,
         SlotsPerVecY = 8,
         SlotsPerVecZ = 16,
         SlotsPerRegVectMask = X86_ONLY(2) NOT_X86(1)
         };

  // A constructor only used by the ADLC output.  All mask fields are filled
  // in directly.  Calls to this look something like RM(1,2,3,4);
  RegMask(
#   define BODY(I) int a##I,
      FORALL_BODY
#   undef BODY
      bool all_stack)
      : _rm_size(_RM_SIZE), _offset(0), _all_stack(all_stack), _arena(nullptr) {
#if defined(VM_LITTLE_ENDIAN) || !defined(_LP64)
#   define BODY(I) _RM_I[I] = a##I;
#else
    // We need to swap ints.
#   define BODY(I) _RM_I[I ^ 1] = a##I;
#endif
    FORALL_BODY
#   undef BODY
    _lwm = 0;
    _hwm = _RM_MAX;
    while (_hwm > 0      && _RM_UP[_hwm] == 0) _hwm--;
    while ((_lwm < _hwm) && _RM_UP[_lwm] == 0) _lwm++;
    assert(valid_watermarks(), "post-condition");
  }

  // Construct an empty mask
  RegMask(Arena* arena DEBUG_ONLY(COMMA bool read_only = false))
      : _RM_UP() DEBUG_ONLY(COMMA _read_only(read_only)), _rm_size(_RM_SIZE),
        _offset(0), _all_stack(false), _lwm(_RM_MAX), _hwm(0), _arena(arena) {
    assert(valid_watermarks(), "post-condition");
  }
  RegMask() : RegMask(nullptr) {
    assert(valid_watermarks(), "post-condition");
  }

  // Construct a mask with a single bit
  RegMask(OptoReg::Name reg,
          Arena* arena DEBUG_ONLY(COMMA bool read_only = false))
      : RegMask(arena DEBUG_ONLY(COMMA read_only)) {
    Insert(reg);
  }
  RegMask(OptoReg::Name reg) : RegMask(reg, nullptr) {}

  RegMask(const RegMask& rm, Arena* arena)
      : _rm_size(_RM_SIZE), _offset(rm._offset), _arena(arena) {
    copy(rm);
  }

  RegMask(const RegMask& rm) : RegMask(rm, nullptr) {}

  RegMask& operator=(const RegMask& rm) {
    copy(rm);
    return *this;
  }

  bool Member(OptoReg::Name reg) const {
    reg = reg - offset_bits();
    if (reg < 0) {
      return false;
    }
    if (reg >= (int)rm_size_bits()) {
      return is_AllStack();
    }
    unsigned int r = (unsigned int)reg;
    return rm_up(r >> _LogWordBits) & (uintptr_t(1) << (r & _WordBitMask));
  }

  // Empty mask check. Ignores registers included through the all_stack flag.
  bool is_Empty() const {
    assert(valid_watermarks(), "sanity");
    for (unsigned i = _lwm; i <= _hwm; i++) {
      if (rm_up(i)) {
        return false;
      }
    }
    return true;
  }

  // Find lowest-numbered register from mask, or BAD if mask is empty.
  OptoReg::Name find_first_elem() const {
    assert(valid_watermarks(), "sanity");
    for (unsigned i = _lwm; i <= _hwm; i++) {
      uintptr_t bits = rm_up(i);
      if (bits) {
        return OptoReg::Name(offset_bits() + (i << _LogWordBits) +
                             find_lowest_bit(bits));
      }
    }
    return OptoReg::Name(OptoReg::Bad);
  }

  // Get highest-numbered register from mask, or BAD if mask is empty. Ignores
  // registers included through the all_stack flag.
  OptoReg::Name find_last_elem() const {
    assert(valid_watermarks(), "sanity");
    // Careful not to overflow if _lwm == 0
    unsigned i = _hwm + 1;
    while (i > _lwm) {
      uintptr_t bits = rm_up(--i);
      if (bits) {
        return OptoReg::Name(offset_bits() + (i << _LogWordBits) +
                             find_highest_bit(bits));
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
    assert(_hwm < _rm_size, "_hwm out of range: %d", _hwm);
    assert(_lwm < _rm_size, "_lwm out of range: %d", _lwm);
    for (unsigned i = 0; i < _lwm; i++) {
      assert(rm_up(i) == 0, "_lwm too high: %d regs at: %d", _lwm, i);
    }
    for (unsigned i = _hwm + 1; i < _rm_size; i++) {
      assert(rm_up(i) == 0, "_hwm too low: %d regs at: %d", _hwm, i);
    }
    return true;
  }

  bool is_AllStack_only() const {
    assert(valid_watermarks(), "sanity");
    if (!is_AllStack()) {
      return false;
    }
    uintptr_t tmp = 0;
    for (unsigned int i = _lwm; i <= _hwm; i++) {
      if (rm_up(i)) {
        return false;
      }
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

  // Check that whether given reg number with size is valid
  // for current regmask, where reg is the highest number.
  bool is_valid_reg(OptoReg::Name reg, const int size) const;

  // Find the lowest-numbered register set in the mask.  Return the
  // HIGHEST register number in the set, or BAD if no sets.
  // Assert that the mask contains only bit sets.
  OptoReg::Name find_first_set(LRG &lrg, const int size) const;

  // Clear out partial bits; leave only aligned adjacent bit sets of size.
  void clear_to_sets(const unsigned int size);
  // Smear out partial bits to aligned adjacent bit sets.
  void smear_to_sets(const unsigned int size);
  // Test that the mask contains only aligned adjacent bit sets
  bool is_aligned_sets(const unsigned int size) const;

  // Test for a single adjacent set
  bool is_bound_set(const unsigned int size) const;

  static bool is_vector(uint ireg);
  static int num_registers(uint ireg);
  static int num_registers(uint ireg, LRG &lrg);

  // Overlap test. Non-zero if any registers in common, including all_stack.
  bool overlap(const RegMask &rm) const {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");

    // Very common overlap case: _rm_up overlap. Check first to reduce
    // execution time.
    unsigned hwm = MIN2(_hwm, rm._hwm);
    unsigned lwm = MAX2(_lwm, rm._lwm);
    for (unsigned i = lwm; i <= hwm; i++) {
      if (rm_up(i) & rm.rm_up(i)) {
        return true;
      }
    }

    // Very rare overlap cases below.

    // We are both all_stack
    if (is_AllStack() && rm.is_AllStack()) {
      return true;
    }

    // We are all_stack and rm _hwm is bigger than us
    if (is_AllStack() && rm._hwm >= _rm_size) {
      for (unsigned i = MAX2(rm._lwm, _rm_size); i <= rm._hwm; i++) {
        if (rm.rm_up(i)) {
          return true;
        }
      }
    }

    // rm is all_stack and our _hwm is bigger than rm
    if (rm.is_AllStack() && _hwm >= rm._rm_size) {
      for (unsigned i = MAX2(_lwm, rm._rm_size); i <= _hwm; i++) {
        if (rm_up(i)) {
          return true;
        }
      }
    }

    // No overlap (also very common)
    return false;
  }

  // Special test for register pressure based splitting
  // UP means register only, Register plus stack, or stack only is DOWN
  bool is_UP() const;

  // Clear a register mask. Does not clear any offset.
  void Clear() {
    _lwm = rm_max();
    _hwm = 0;
    set_range(0, 0, _rm_size);
    set_AllStack(false);
    assert(valid_watermarks(), "sanity");
  }

  // Fill a register mask with 1's
  void Set_All() {
    assert(_offset == 0, "offset non-zero");
    Set_All_From_Offset();
  }

  // Fill a register mask with 1's from the current offset.
  void Set_All_From_Offset() {
    _lwm = 0;
    _hwm = rm_max();
    set_range(0, 0xFF, _rm_size);
    set_AllStack(true);
    assert(valid_watermarks(), "sanity");
  }

  // Fill a register mask with 1's starting from the given register.
  void Set_All_From(OptoReg::Name reg) {
    reg = reg - offset_bits();
    assert(reg != OptoReg::Bad, "sanity");
    assert(reg != OptoReg::Special, "sanity");
    assert(reg >= 0, "register outside mask");
    assert(valid_watermarks(), "pre-condition");
    unsigned int r = (unsigned int)reg;
    unsigned int index = r >> _LogWordBits;
    unsigned int min_size = index + 1;
    grow(min_size);
    rm_up(index) |= (uintptr_t(-1) << (r & _WordBitMask));
    if (index < rm_max()) {
      set_range(index + 1, 0xFF, rm_max() - index);
    }
    if (index < _lwm) {
      _lwm = index;
    }
    _hwm = rm_max();
    set_AllStack(true);
    assert(valid_watermarks(), "post-condition");
  }

  // Insert register into mask
  void Insert(OptoReg::Name reg) {
    reg = reg - offset_bits();
    assert(reg != OptoReg::Bad, "sanity");
    assert(reg != OptoReg::Special, "sanity");
    assert(reg >= 0, "register outside mask");
    assert(valid_watermarks(), "pre-condition");
    unsigned int r = (unsigned int)reg;
    unsigned int index = r >> _LogWordBits;
    unsigned int min_size = index + 1;
    grow(min_size);
    if (index > _hwm) _hwm = index;
    if (index < _lwm) _lwm = index;
    rm_up(index) |= (uintptr_t(1) << (r & _WordBitMask));
    assert(valid_watermarks(), "post-condition");
  }

  // Remove register from mask
  void Remove(OptoReg::Name reg) {
    reg = reg - offset_bits();
    assert(reg >= 0, "register outside mask");
    assert(reg < (int)rm_size_bits(), "register outside mask");
    unsigned int r = (unsigned int)reg;
    rm_up(r >> _LogWordBits) &= ~(uintptr_t(1) << (r & _WordBitMask));
  }

  // OR 'rm' into 'this'
  void OR(const RegMask &rm) {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    grow(rm._rm_size);
    // OR widens the live range
    if (_lwm > rm._lwm) _lwm = rm._lwm;
    if (_hwm < rm._hwm) _hwm = rm._hwm;
    // Compute OR with all words from rm
    for (unsigned int i = _lwm; i <= _hwm && i < rm._rm_size; i++) {
      rm_up(i) |= rm.rm_up(i);
    }
    // If rm is smaller than us and has the all_stack flag set, we need to set
    // all bits in the gap to 1.
    if (rm.is_AllStack() && rm._rm_size < _rm_size) {
      set_range(rm._rm_size, 0xFF, _rm_size - rm._rm_size);
      _hwm = rm_max();
    }
    set_AllStack(is_AllStack() || rm.is_AllStack());
    assert(valid_watermarks(), "sanity");
  }

  // AND 'rm' into 'this'
  void AND(const RegMask &rm) {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    grow(rm._rm_size);
    // Compute AND with all words from rm. Do not evaluate words outside the
    // current watermark range, as they are already zero and an &= would not
    // change that
    for (unsigned int i = _lwm; i <= _hwm && i < rm._rm_size; i++) {
      rm_up(i) &= rm.rm_up(i);
    }
    // If rm is smaller than our high watermark and has the all_stack flag not
    // set, we need to set all bits in the gap to 0.
    if (!rm.is_AllStack() && _hwm > rm.rm_max()) {
      set_range(rm._rm_size, 0, _hwm - rm.rm_max());
      _hwm = rm.rm_max();
    }
    // Narrow the watermarks if rm spans a narrower range. Update after to
    // ensure non-overlapping words are zeroed out. If rm has the all_stack
    // flag set and is smaller than our high watermark, take care not to
    // incorrectly lower the high watermark according to rm.
    if (_lwm < rm._lwm) {
      _lwm = rm._lwm;
    }
    if (_hwm > rm._hwm && !(rm.is_AllStack() && _hwm > rm.rm_max())) {
      _hwm = rm._hwm;
    }
    set_AllStack(is_AllStack() && rm.is_AllStack());
    assert(valid_watermarks(), "sanity");
  }

  // Subtract 'rm' from 'this'.
  void SUBTRACT(const RegMask &rm) {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    grow(rm._rm_size);
    unsigned int hwm = MIN2(_hwm, rm._hwm);
    unsigned int lwm = MAX2(_lwm, rm._lwm);
    for (unsigned int i = lwm; i <= hwm; i++) {
      rm_up(i) &= ~rm.rm_up(i);
    }
    // If rm is smaller than our high watermark and has the all_stack flag set,
    // we need to set all bits in the gap to 0.
    if (rm.is_AllStack() && _hwm > rm.rm_max()) {
      set_range(rm.rm_size(), 0, _hwm - rm.rm_max());
      _hwm = rm.rm_max();
    }
    set_AllStack(is_AllStack() && !rm.is_AllStack());
    trim_watermarks();
    assert(valid_watermarks(), "sanity");
  }

  // Subtract 'rm' from 'this', but ignore everything in 'rm' that does not
  // overlap with us and do not modify our all_stack flag. Supports masks of
  // differing offsets. Does not support 'rm' with the all_stack flag set.
  void SUBTRACT_inner(const RegMask& rm) {
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    assert(!rm.is_AllStack(), "not supported");
    // Various translations due to differing offsets
    int rm_index_diff = _offset - rm._offset;
    int rm_hwm_tr = (int)rm._hwm - rm_index_diff;
    int rm_lwm_tr = (int)rm._lwm - rm_index_diff;
    int rm_rm_max_tr = (int)rm.rm_max() - rm_index_diff;
    int rm_rm_size_tr = (int)rm._rm_size - rm_index_diff;
    int hwm = MIN2((int)_hwm, rm_hwm_tr);
    int lwm = MAX2((int)_lwm, rm_lwm_tr);
    for (int i = lwm; i <= hwm; i++) {
      assert(i + rm_index_diff < (int)rm._rm_size, "sanity");
      assert(i + rm_index_diff >= 0, "sanity");
      rm_up(i) &= ~rm.rm_up(i + rm_index_diff);
    }
    trim_watermarks();
    assert(valid_watermarks(), "sanity");
  }

  // Roll over the register mask. The main use is to expose a new set of stack
  // slots for the register allocator. Return if the rollover succeeded or not.
  bool rollover() {
    assert(is_AllStack_only(), "rolling over non-empty mask");
    if (!OptoRegPair::can_fit((_rm_size + _offset + _rm_size) * BitsPerWord - 1)) {
      // Ensure that register masks cannot roll over beyond the point at which
      // OptoRegPair can no longer index the whole mask.
      return false;
    }
    _offset += _rm_size;
    Set_All_From_Offset();
    return true;
  }

  // Compute size of register mask: number of bits
  uint Size() const {
    uint sum = 0;
    assert(valid_watermarks(), "sanity");
    for (unsigned i = _lwm; i <= _hwm; i++) {
      sum += population_count(rm_up(i));
    }
    return sum;
  }

#ifndef PRODUCT
private:
  bool dump_end_run(outputStream* st, OptoReg::Name start,
                    OptoReg::Name last) const;

public:
  unsigned int static basic_rm_size() {
    return _RM_SIZE;
  }
  unsigned int static rm_size_max_bits() {
    return _RM_SIZE_MAX * BitsPerWord;
  }
  bool equals(const RegMask& rm) const {
    assert(_offset == rm._offset, "offset mismatch");
    if (_all_stack != rm._all_stack) {
      return false;
    }
    // Shared segment
    for (unsigned int i = 0; i < MIN2(_rm_size, rm._rm_size); i++) {
      if (rm_up(i) != rm.rm_up(i)) {
        return false;
      }
    }
    // If there is a size difference, check the protruding segment against
    // all_stack.
    const unsigned int start = MIN2(_rm_size, rm._rm_size);
    const uintptr_t value = _all_stack ? uintptr_t(-1) : 0;
    for (unsigned int i = start; i < _rm_size; i++) {
      if (rm_up(i) != value) {
        return false;
      }
    }
    for (unsigned int i = start; i < rm._rm_size; i++) {
      if (rm.rm_up(i) != value) {
        return false;
      }
    }
    return true;
  }
  void set_offset(unsigned int offset) {
    _offset = offset;
  }
  void print() const { dump(); }
  void dump(outputStream *st = tty) const; // Print a mask
  void dump_hex(outputStream* st = tty) const; // Print a mask (raw hex)
#endif

  static const RegMask Empty;   // Common empty mask
  static const RegMask All;     // Common all mask

  bool can_represent(OptoReg::Name reg, unsigned int size = 1) const {
    reg = reg - offset_bits();
    return reg >= 0 && reg <= (int)(rm_size_bits() - size);
  }
};

class RegMaskIterator {
 private:
  uintptr_t _current_bits;
  unsigned int _next_index;
  OptoReg::Name _reg;
  const RegMask& _rm;
 public:
  RegMaskIterator(const RegMask& rm) : _current_bits(0), _next_index(rm._lwm), _reg(OptoReg::Bad), _rm(rm) {
    // Calculate the first element
    next();
  }

  bool has_next() {
    return _reg != OptoReg::Bad;
  }

  // Get the current element and calculate the next
  OptoReg::Name next() {
    OptoReg::Name r = _reg;

    // This bit shift scheme, borrowed from IndexSetIterator,
    // shifts the _current_bits down by the number of trailing
    // zeros - which leaves the "current" bit on position zero,
    // then subtracts by 1 to clear it. This quirk avoids the
    // undefined behavior that could arise if trying to shift
    // away the bit with a single >> (next_bit + 1) shift when
    // next_bit is 31/63. It also keeps number of shifts and
    // arithmetic ops to a minimum.

    // We have previously found bits at _next_index - 1, and
    // still have some left at the same index.
    if (_current_bits != 0) {
      unsigned int next_bit = find_lowest_bit(_current_bits);
      assert(_reg != OptoReg::Bad, "can't be in a bad state");
      assert(next_bit > 0, "must be");
      assert(((_current_bits >> next_bit) & 0x1) == 1, "lowest bit must be set after shift");
      _current_bits = (_current_bits >> next_bit) - 1;
      _reg = OptoReg::add(_reg, next_bit);
      return r;
    }

    // Find the next word with bits
    while (_next_index <= _rm._hwm) {
      _current_bits = _rm.rm_up(_next_index++);
      if (_current_bits != 0) {
        // Found a word. Calculate the first register element and
        // prepare _current_bits by shifting it down and clearing
        // the lowest bit
        unsigned int next_bit = find_lowest_bit(_current_bits);
        assert(((_current_bits >> next_bit) & 0x1) == 1, "lowest bit must be set after shift");
        _current_bits = (_current_bits >> next_bit) - 1;
        _reg = OptoReg::Name(_rm.offset_bits() +
                             ((_next_index - 1) << RegMask::_LogWordBits) +
                             next_bit);
        return r;
      }
    }

    // No more bits
    _reg = OptoReg::Name(OptoReg::Bad);
    return r;
  }
};

// Do not use these constants directly in client code!
#undef RM_SIZE
#undef RM_SIZE_MIN

#endif // SHARE_OPTO_REGMASK_HPP

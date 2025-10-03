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
#include "memory/arena.hpp"
#include "opto/optoreg.hpp"
#include "utilities/count_leading_zeros.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/globalDefinitions.hpp"

//------------------------------RegMask----------------------------------------
// The register mask data structure (RegMask) provides a representation
// of sets of OptoReg::Name (i.e., machine registers and stack slots). The data
// structure tracks register availability and allocations during code
// generation, in particular during register allocation. Internally, RegMask
// uses a compact bitset representation. Further documentation, including an
// illustrative example, is available in source code comments throughout this
// file.

// The ADLC defines 3 macros, RM_SIZE_IN_INTS, RM_SIZE_IN_INTS_MIN, and FORALL_BODY.
// RM_SIZE_IN_INTS is the base size of a register mask in 32-bit words.
// RM_SIZE_IN_INTS_MIN is the theoretical minimum size of a register mask in 32-bit
// words.
// FORALL_BODY replicates a BODY macro once per word in the register mask.
// The usage is somewhat clumsy and limited to the regmask.[h,c]pp files.
// However, it means the ADLC can redefine the unroll macro and all loops
// over register masks will be unrolled by the correct amount.
//
// The ADL file describes how to print the machine-specific registers, as well
// as any notion of register classes.

class LRG;

// To avoid unbounded RegMask growth and to be able to statically compute a
// register mask size upper bound (see RM_SIZE_IN_INTS_MAX below), we need to
// set some form of limit on the number of stack slots used by BoxLockNodes. The
// limit below is rather arbitrary but should be quite generous and cover all
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

class RegMask {

  friend class RegMaskIterator;

  // RM_SIZE_IN_INTS is aligned to 64-bit - assert that this holds
  LP64_ONLY(STATIC_ASSERT(is_aligned(RM_SIZE_IN_INTS, 2)));

  static const unsigned int WORD_BIT_MASK = BitsPerWord - 1U;

  // RM_SIZE_IN_INTS, but in number of machine words
  static const unsigned int RM_SIZE_IN_WORDS = LP64_ONLY(RM_SIZE_IN_INTS >> 1) NOT_LP64(RM_SIZE_IN_INTS);

  // The last index (in machine words) of the (static) array of register mask
  // bits
  static const unsigned int RM_WORD_MAX_INDEX = RM_SIZE_IN_WORDS - 1U;

  // Compute a best-effort (statically known) upper bound for register mask
  // size in 32-bit words. When extending/growing register masks, we should
  // never grow past this size.
  static const unsigned int RM_SIZE_IN_INTS_MAX =
      (((RM_SIZE_IN_INTS_MIN << 5) +        // Slots for machine registers
        (max_method_parameter_length * 2) + // Slots for incoming arguments (from caller)
        (max_method_parameter_length * 2) + // Slots for outgoing arguments (to callee)
        BoxLockNode_SLOT_LIMIT +            // Slots for locks
        64                                  // Padding, reserved words, etc.
        ) + 31) >> 5; // Number of bits -> number of 32-bit words

  // RM_SIZE_IN_INTS_MAX, but in number of machine words
  static const unsigned int RM_SIZE_IN_WORDS_MAX =
      LP64_ONLY(((RM_SIZE_IN_INTS_MAX + 1) & ~1) >> 1) NOT_LP64(RM_SIZE_IN_INTS_MAX);

  // Sanity check
  STATIC_ASSERT(RM_SIZE_IN_INTS <= RM_SIZE_IN_INTS_MAX);

  // Ensure that register masks cannot grow beyond the point at which
  // OptoRegPair can no longer index the whole mask
  STATIC_ASSERT(OptoRegPair::can_fit((RM_SIZE_IN_INTS_MAX << 5) - 1));

  union {
    // Array of Register Mask bits. The array should be
    // large enough to cover all the machine registers, as well as a certain
    // number of parameters that need to be passed on the stack (stack
    // registers). The number of parameters that can fit in the mask should be
    // dimensioned to cover most common cases. We handle the uncommon cases by
    // extending register masks dynamically (see below).

    // Viewed as an array of 32-bit words
    int _rm_int[RM_SIZE_IN_INTS];

    // Viewed as an array of machine words
    uintptr_t _rm_word[RM_SIZE_IN_WORDS];
  };

  // In rare situations (e.g., "more than 90+ parameters on Intel"), we need to
  // extend the register mask with dynamically allocated memory. We keep the
  // base statically allocated _rm_word, and arena allocate the extended mask
  // (_rm_word_ext) separately. Another, perhaps more elegant, option would be to
  // have two subclasses of RegMask, where one is statically allocated and one
  // is (entirely) dynamically allocated. Given that register mask extension is
  // rare, we decided to use the current approach (_rm_word and _rm_word_ext) to
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
  uintptr_t* _rm_word_ext = nullptr;

  // Where to extend the register mask
  Arena* _arena;

#ifdef ASSERT
  // Register masks may get shallowly copied without the use of constructors,
  // for example as part of `Node::clone`. This is problematic when dealing with
  // the externally allocated memory for _rm_word_ext. Therefore, we need some
  // sanity checks to ensure we have addressed all such cases. The below
  // variables enable such checks.
  //
  // The original address of the _rm_word_ext variable, set when using
  // constructors. If we get copied/cloned, &_rm_word_ext will no longer equal
  // _original_ext_address.
  uintptr_t** _original_ext_address = &_rm_word_ext;
  //
  // If the original version, of which we may be a clone, is read-only. In such
  // cases, we can allow read-only sharing.
  bool _read_only = false;
#endif

  // Current *total* register mask size in machine words (both static and
  // dynamic parts)
  unsigned int _rm_size_in_words;

  // If _infinite_stack = true, we consider all registers beyond what the register
  // mask can currently represent to be included. If _infinite_stack = false, we
  // consider the registers not included.
  bool _infinite_stack = false;

  // The low and high watermarks represent the lowest and highest word that
  // might contain set register mask bits, respectively. We guarantee that
  // there are no bits in words outside this range, but any word at and between
  // the two marks can still be 0. We only use the watermarks to improve
  // performance, and do not guarantee that the watermarks are optimal. If _hwm
  // < _lwm, the register mask is necessarily empty. Indeed, when we construct
  // empty register masks, we set _hwm = 0 and _lwm = max. The watermarks do not
  // concern _infinite_stack-registers.
  unsigned int _lwm;
  unsigned int _hwm;

  // The following diagram illustrates the internal representation of a RegMask
  // (for a made-up platform with 10 registers and 4-bit words) that has been
  // extended with two additional words to represent more stack locations:
  //
  //                         _lwm=1   RM_SIZE_IN_WORDS=3 _hwm=3      _rm_size_in_words=5
  //                            |                  |      |                 |
  //            r0 r1 r2 r3 r4 r5 r6 r7 r8 r9 s0 s1   s2 s3 s4 s5 s6 s7 s8 s9 s10 s11 ...
  // Content:  [0  0  0  0 |0  1  1  0 |0  0  1  0 ] [1  1  0  1 |0  0  0  0] is  is  is
  //   Index: [0]         [1]         [2]           [0]         [1]
  //
  //          \____________________________________/ \______________________/
  //                                 |                           |
  //                             _rm_word                     _rm_word_ext
  //          \_____________________________________________________________/
  //                                          |
  //                                  _rm_size_in_words=5
  //
  // In this example, registers {r5, r6} and stack locations {s0, s2, s3, s5}
  // are included in the register mask. Depending on the value of
  // _infinite_stack (denoted with is), {s10, s11, ...} are all included (is=1)
  // or excluded (is=0). Note that all registers/stack locations under _lwm
  // and over _hwm are excluded. The exception is {s10, s11, ...}, where the
  // value is decided solely by _infinite_stack, regardless of the value of
  // _hwm.

  // We support offsetting/shifting register masks to make explicit stack
  // slots that originally are implicitly represented by _infinite_stack=true.
  // The main use is in PhaseChaitin::Select, when selecting stack slots for
  // spilled values. Spilled values *must* get a stack slot, and therefore have
  // _infinite_stack=true. If we run out of stack slots in an
  // _infinite_mask=true register mask, we roll over the register mask to make
  // the next set of stack slots available for selection.
  //
  // The _offset variable indicates how many words we offset with.
  // We consider all registers before the offset to not be included in the
  // register mask.
  unsigned int _offset;
  //
  // The only operation that may update the _offset attribute is
  // RegMask::rollover(). This operation requires the register mask to be
  // clean/empty (all zeroes), except for _infinite_stack, which must be true,
  // and has the effect of increasing _offset by _rm_size_in_words and setting
  // all bits (now necessarily representing stack locations) to 1. Here is how
  // the above register mask looks like after clearing, setting _infinite_stack
  // to true, and successfully rolling over:
  //
  //              _lwm=0                             RM_SIZE_IN_WORDS=3              _hwm=4  _rm_size_in_words=5
  //                 |                                        |                        |      |
  //            s10 s11 s12 s13 s14 s15 s16 s17 s18 s19 s20 s21  s22 s23 s24 s25 s26 s27 s28 s29 s30 s31 ...
  // Content:  [1   1   1   1  |1   1   1   1  |1   1   1   1 ] [1   1   1   1  |1   1   1   1]  1   1   1
  //   Index: [0]             [1]             [2]              [0]             [1]
  //
  //          \_______________________________________________/ \_____________________________/
  //                                    |                                     |
  //                                _rm_word                             _rm_word_ext
  //          \_______________________________________________________________________________/
  //                                                  |
  //                                  _rm_size_in_words=_offset=5

  // Access word i in the register mask.
  const uintptr_t& rm_word(unsigned int i) const {
    assert(_read_only || _original_ext_address == &_rm_word_ext, "clone sanity check");
    assert(i < _rm_size_in_words, "sanity");
    if (i < RM_SIZE_IN_WORDS) {
      return _rm_word[i];
    } else {
      assert(_rm_word_ext != nullptr, "sanity");
      return _rm_word_ext[i - RM_SIZE_IN_WORDS];
    }
  }

  // Non-const version of the above.
  uintptr_t& rm_word(unsigned int i) {
    assert(_original_ext_address == &_rm_word_ext, "clone sanity check");
    return const_cast<uintptr_t&>(const_cast<const RegMask*>(this)->rm_word(i));
  }

  // The current maximum word index
  unsigned int rm_word_max_index() const {
    return _rm_size_in_words - 1U;
  }

  // Grow the register mask to ensure it can fit at least min_size words.
  void grow(unsigned int min_size, bool initialize_by_infinite_stack = true) {
    if (min_size > _rm_size_in_words) {
      assert(min_size <= RM_SIZE_IN_WORDS_MAX, "unexpected register mask growth");
      assert(_arena != nullptr, "register mask not growable");
      min_size = MIN2(RM_SIZE_IN_WORDS_MAX, round_up_power_of_2(min_size));
      unsigned int old_size = _rm_size_in_words;
      unsigned int old_ext_size = old_size - RM_SIZE_IN_WORDS;
      unsigned int new_ext_size = min_size - RM_SIZE_IN_WORDS;
      _rm_size_in_words = min_size;
      if (_rm_word_ext == nullptr) {
        assert(old_ext_size == 0, "sanity");
        _rm_word_ext = NEW_ARENA_ARRAY(_arena, uintptr_t, new_ext_size);
      } else {
        assert(_original_ext_address == &_rm_word_ext, "clone sanity check");
        _rm_word_ext = REALLOC_ARENA_ARRAY(_arena, uintptr_t, _rm_word_ext,
                                           old_ext_size, new_ext_size);
      }
      if (initialize_by_infinite_stack) {
        int fill = 0;
        if (is_infinite_stack()) {
          fill = 0xFF;
          _hwm = rm_word_max_index();
        }
        set_range(old_size, fill, _rm_size_in_words - old_size);
      }
    }
  }

  // Make us a copy of src
  void copy(const RegMask& src) {
    assert(_offset == src._offset, "offset mismatch");
    _hwm = src._hwm;
    _lwm = src._lwm;

    // Copy base mask
    memcpy(_rm_word, src._rm_word, sizeof(uintptr_t) * RM_SIZE_IN_WORDS);
    _infinite_stack = src._infinite_stack;

    // Copy extension
    if (src._rm_word_ext != nullptr) {
      assert(src._rm_size_in_words > RM_SIZE_IN_WORDS, "sanity");
      assert(_original_ext_address == &_rm_word_ext, "clone sanity check");
      grow(src._rm_size_in_words, false);
      memcpy(_rm_word_ext, src._rm_word_ext,
             sizeof(uintptr_t) * (src._rm_size_in_words - RM_SIZE_IN_WORDS));
    }

    // If the source is smaller than us, we need to set the gap according to
    // the sources infinite_stack flag.
    if (src._rm_size_in_words < _rm_size_in_words) {
      int value = 0;
      if (src.is_infinite_stack()) {
        value = 0xFF;
        _hwm = rm_word_max_index();
      }
      set_range(src._rm_size_in_words, value, _rm_size_in_words - src._rm_size_in_words);
    }

    assert(valid_watermarks(), "post-condition");
  }

  // Make the watermarks as tight as possible.
  void trim_watermarks() {
    if (_hwm < _lwm) {
      return;
    }
    while ((_hwm > _lwm) && rm_word(_hwm) == 0) {
      _hwm--;
    }
    while ((_lwm < _hwm) && rm_word(_lwm) == 0) {
      _lwm++;
    }
    if ((_lwm == _hwm) && rm_word(_lwm) == 0) {
      _lwm = rm_word_max_index();
      _hwm = 0;
    }
  }

  // Set a span of words in the register mask to a given value.
  void set_range(unsigned int start, int value, unsigned int length) {
    if (start < RM_SIZE_IN_WORDS) {
      memset(_rm_word + start, value,
             sizeof(uintptr_t) * MIN2((int)length, (int)RM_SIZE_IN_WORDS - (int)start));
    }
    if (start + length > RM_SIZE_IN_WORDS) {
      assert(_rm_word_ext != nullptr, "sanity");
      assert(_original_ext_address == &_rm_word_ext, "clone sanity check");
      memset(_rm_word_ext + MAX2((int)start - (int)RM_SIZE_IN_WORDS, 0), value,
             sizeof(uintptr_t) *
                 MIN2((int)length, (int)length - ((int)RM_SIZE_IN_WORDS - (int)start)));
    }
  }

public:
  unsigned int rm_size_in_words() const {
    return _rm_size_in_words;
  }
  unsigned int rm_size_in_bits() const {
    return _rm_size_in_words * BitsPerWord;
  }

  bool is_offset() const {
    return _offset > 0;
  }
  unsigned int offset_bits() const {
    return _offset * BitsPerWord;
  };

  bool is_infinite_stack() const {
    return _infinite_stack;
  }
  void set_infinite_stack(bool value) {
    _infinite_stack = value;
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
  // in directly.  Calls to this look something like RM(0xc0, 0x0, 0x0, false);
  RegMask(
#   define BODY(I) int a##I,
      FORALL_BODY
#   undef BODY
      bool infinite_stack)
      : _arena(nullptr), _rm_size_in_words(RM_SIZE_IN_WORDS), _infinite_stack(infinite_stack), _offset(0) {
#if defined(VM_LITTLE_ENDIAN) || !defined(_LP64)
#   define BODY(I) _rm_int[I] = a##I;
#else
    // We need to swap ints.
#   define BODY(I) _rm_int[I ^ 1] = a##I;
#endif
    FORALL_BODY
#   undef BODY
    _lwm = 0;
    _hwm = RM_WORD_MAX_INDEX;
    while (_hwm > 0 && _rm_word[_hwm] == 0) {
      _hwm--;
    }
    while ((_lwm < _hwm) && _rm_word[_lwm] == 0) {
      _lwm++;
    }
    assert(valid_watermarks(), "post-condition");
  }

  // Construct an empty mask
  explicit RegMask(Arena* arena DEBUG_ONLY(COMMA bool read_only = false))
      : _rm_word(), _arena(arena) DEBUG_ONLY(COMMA _read_only(read_only)),
        _rm_size_in_words(RM_SIZE_IN_WORDS), _infinite_stack(false), _lwm(RM_WORD_MAX_INDEX), _hwm(0), _offset(0) {
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
  explicit RegMask(OptoReg::Name reg) : RegMask(reg, nullptr) {}

  // ----------------------------------------
  // Deep copying constructors and assignment
  // ----------------------------------------

  RegMask(const RegMask& rm, Arena* arena)
      : _arena(arena), _rm_size_in_words(RM_SIZE_IN_WORDS), _offset(rm._offset) {
    copy(rm);
  }

  RegMask(const RegMask& rm) : RegMask(rm, nullptr) {}

  RegMask& operator=(const RegMask& rm) {
    copy(rm);
    return *this;
  }

  // ----------------
  // End deep copying
  // ----------------

  bool Member(OptoReg::Name reg) const {
    reg = reg - offset_bits();
    if (reg < 0) {
      return false;
    }
    if (reg >= (int)rm_size_in_bits()) {
      return is_infinite_stack();
    }
    unsigned int r = (unsigned int)reg;
    return rm_word(r >> LogBitsPerWord) & (uintptr_t(1) << (r & WORD_BIT_MASK));
  }

  // Empty mask check. Ignores registers included through the infinite_stack flag.
  bool is_Empty() const {
    assert(valid_watermarks(), "sanity");
    for (unsigned i = _lwm; i <= _hwm; i++) {
      if (rm_word(i) != 0) {
        return false;
      }
    }
    return true;
  }

  // Find lowest-numbered register from mask, or BAD if mask is empty.
  OptoReg::Name find_first_elem() const {
    assert(valid_watermarks(), "sanity");
    for (unsigned i = _lwm; i <= _hwm; i++) {
      uintptr_t bits = rm_word(i);
      if (bits != 0) {
        return OptoReg::Name(offset_bits() + (i << LogBitsPerWord) +
                             find_lowest_bit(bits));
      }
    }
    return OptoReg::Name(OptoReg::Bad);
  }

  // Get highest-numbered register from mask, or BAD if mask is empty. Ignores
  // registers included through the infinite_stack flag.
  OptoReg::Name find_last_elem() const {
    assert(valid_watermarks(), "sanity");
    // Careful not to overflow if _lwm == 0
    unsigned i = _hwm + 1;
    while (i > _lwm) {
      uintptr_t bits = rm_word(--i);
      if (bits != 0) {
        return OptoReg::Name(offset_bits() + (i << LogBitsPerWord) +
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
    assert(_hwm < _rm_size_in_words, "_hwm out of range: %d", _hwm);
    assert(_lwm < _rm_size_in_words, "_lwm out of range: %d", _lwm);
    for (unsigned i = 0; i < _lwm; i++) {
      assert(rm_word(i) == 0, "_lwm too high: %d regs at: %d", _lwm, i);
    }
    for (unsigned i = _hwm + 1; i < _rm_size_in_words; i++) {
      assert(rm_word(i) == 0, "_hwm too low: %d regs at: %d", _hwm, i);
    }
    return true;
  }

  bool is_infinite_stack_only() const {
    assert(valid_watermarks(), "sanity");
    if (!is_infinite_stack()) {
      return false;
    }
    uintptr_t tmp = 0;
    for (unsigned int i = _lwm; i <= _hwm; i++) {
      if (rm_word(i) != 0) {
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

  // Overlap test. Non-zero if any registers in common, including infinite_stack.
  bool overlap(const RegMask &rm) const {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");

    // Very common overlap case: _rm_word overlap. Check first to reduce
    // execution time.
    unsigned hwm = MIN2(_hwm, rm._hwm);
    unsigned lwm = MAX2(_lwm, rm._lwm);
    for (unsigned i = lwm; i <= hwm; i++) {
      if ((rm_word(i) & rm.rm_word(i)) != 0) {
        return true;
      }
    }

    // Very rare overlap cases below.

    // We are both infinite_stack
    if (is_infinite_stack() && rm.is_infinite_stack()) {
      return true;
    }

    // We are infinite_stack and rm _hwm is bigger than us
    if (is_infinite_stack() && rm._hwm >= _rm_size_in_words) {
      for (unsigned i = MAX2(rm._lwm, _rm_size_in_words); i <= rm._hwm; i++) {
        if (rm.rm_word(i) != 0) {
          return true;
        }
      }
    }

    // rm is infinite_stack and our _hwm is bigger than rm
    if (rm.is_infinite_stack() && _hwm >= rm._rm_size_in_words) {
      for (unsigned i = MAX2(_lwm, rm._rm_size_in_words); i <= _hwm; i++) {
        if (rm_word(i) != 0) {
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
    _lwm = rm_word_max_index();
    _hwm = 0;
    set_range(0, 0, _rm_size_in_words);
    set_infinite_stack(false);
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
    _hwm = rm_word_max_index();
    set_range(0, 0xFF, _rm_size_in_words);
    set_infinite_stack(true);
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
    unsigned int index = r >> LogBitsPerWord;
    unsigned int min_size = index + 1;
    grow(min_size);
    rm_word(index) |= (uintptr_t(-1) << (r & WORD_BIT_MASK));
    if (index < rm_word_max_index()) {
      set_range(index + 1, 0xFF, rm_word_max_index() - index);
    }
    if (index < _lwm) {
      _lwm = index;
    }
    _hwm = rm_word_max_index();
    set_infinite_stack(true);
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
    unsigned int index = r >> LogBitsPerWord;
    unsigned int min_size = index + 1;
    grow(min_size);
    if (index > _hwm) _hwm = index;
    if (index < _lwm) _lwm = index;
    rm_word(index) |= (uintptr_t(1) << (r & WORD_BIT_MASK));
    assert(valid_watermarks(), "post-condition");
  }

  // Remove register from mask
  void Remove(OptoReg::Name reg) {
    reg = reg - offset_bits();
    assert(reg >= 0, "register outside mask");
    assert(reg < (int)rm_size_in_bits(), "register outside mask");
    unsigned int r = (unsigned int)reg;
    rm_word(r >> LogBitsPerWord) &= ~(uintptr_t(1) << (r & WORD_BIT_MASK));
  }

  // OR 'rm' into 'this'
  void OR(const RegMask &rm) {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    grow(rm._rm_size_in_words);
    // OR widens the live range
    if (_lwm > rm._lwm) _lwm = rm._lwm;
    if (_hwm < rm._hwm) _hwm = rm._hwm;
    // Compute OR with all words from rm
    for (unsigned int i = _lwm; i <= _hwm && i < rm._rm_size_in_words; i++) {
      rm_word(i) |= rm.rm_word(i);
    }
    // If rm is smaller than us and has the infinite_stack flag set, we need to set
    // all bits in the gap to 1.
    if (rm.is_infinite_stack() && rm._rm_size_in_words < _rm_size_in_words) {
      set_range(rm._rm_size_in_words, 0xFF, _rm_size_in_words - rm._rm_size_in_words);
      _hwm = rm_word_max_index();
    }
    set_infinite_stack(is_infinite_stack() || rm.is_infinite_stack());
    assert(valid_watermarks(), "sanity");
  }

  // AND 'rm' into 'this'
  void AND(const RegMask &rm) {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    grow(rm._rm_size_in_words);
    // Compute AND with all words from rm. Do not evaluate words outside the
    // current watermark range, as they are already zero and an &= would not
    // change that
    for (unsigned int i = _lwm; i <= _hwm && i < rm._rm_size_in_words; i++) {
      rm_word(i) &= rm.rm_word(i);
    }
    // If rm is smaller than our high watermark and has the infinite_stack flag not
    // set, we need to set all bits in the gap to 0.
    if (!rm.is_infinite_stack() && _hwm > rm.rm_word_max_index()) {
      set_range(rm._rm_size_in_words, 0, _hwm - rm.rm_word_max_index());
      _hwm = rm.rm_word_max_index();
    }
    // Narrow the watermarks if rm spans a narrower range. Update after to
    // ensure non-overlapping words are zeroed out. If rm has the infinite_stack
    // flag set and is smaller than our high watermark, take care not to
    // incorrectly lower the high watermark according to rm.
    if (_lwm < rm._lwm) {
      _lwm = rm._lwm;
    }
    if (_hwm > rm._hwm && !(rm.is_infinite_stack() && _hwm > rm.rm_word_max_index())) {
      _hwm = rm._hwm;
    }
    set_infinite_stack(is_infinite_stack() && rm.is_infinite_stack());
    assert(valid_watermarks(), "sanity");
  }

  // Subtract 'rm' from 'this'.
  void SUBTRACT(const RegMask &rm) {
    assert(_offset == rm._offset, "offset mismatch");
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    grow(rm._rm_size_in_words);
    unsigned int hwm = MIN2(_hwm, rm._hwm);
    unsigned int lwm = MAX2(_lwm, rm._lwm);
    for (unsigned int i = lwm; i <= hwm; i++) {
      rm_word(i) &= ~rm.rm_word(i);
    }
    // If rm is smaller than our high watermark and has the infinite_stack flag set,
    // we need to set all bits in the gap to 0.
    if (rm.is_infinite_stack() && _hwm > rm.rm_word_max_index()) {
      set_range(rm.rm_size_in_words(), 0, _hwm - rm.rm_word_max_index());
      _hwm = rm.rm_word_max_index();
    }
    set_infinite_stack(is_infinite_stack() && !rm.is_infinite_stack());
    trim_watermarks();
    assert(valid_watermarks(), "sanity");
  }

  // Subtract 'rm' from 'this', but ignore everything in 'rm' that does not
  // overlap with us and do not modify our infinite_stack flag. Supports masks of
  // differing offsets. Does not support 'rm' with the infinite_stack flag set.
  void SUBTRACT_inner(const RegMask& rm) {
    assert(valid_watermarks() && rm.valid_watermarks(), "sanity");
    assert(!rm.is_infinite_stack(), "not supported");
    // Various translations due to differing offsets
    int rm_index_diff = _offset - rm._offset;
    int rm_hwm_tr = (int)rm._hwm - rm_index_diff;
    int rm_lwm_tr = (int)rm._lwm - rm_index_diff;
    int rm_rm_max_tr = (int)rm.rm_word_max_index() - rm_index_diff;
    int rm_rm_size_tr = (int)rm._rm_size_in_words - rm_index_diff;
    int hwm = MIN2((int)_hwm, rm_hwm_tr);
    int lwm = MAX2((int)_lwm, rm_lwm_tr);
    for (int i = lwm; i <= hwm; i++) {
      assert(i + rm_index_diff < (int)rm._rm_size_in_words, "sanity");
      assert(i + rm_index_diff >= 0, "sanity");
      rm_word(i) &= ~rm.rm_word(i + rm_index_diff);
    }
    trim_watermarks();
    assert(valid_watermarks(), "sanity");
  }

  // Roll over the register mask. The main use is to expose a new set of stack
  // slots for the register allocator. Return if the rollover succeeded or not.
  bool rollover() {
    assert(is_infinite_stack(), "rolling over non-empty mask");
    if (!OptoRegPair::can_fit((_rm_size_in_words + _offset + _rm_size_in_words) * BitsPerWord - 1)) {
      // Ensure that register masks cannot roll over beyond the point at which
      // OptoRegPair can no longer index the whole mask.
      return false;
    }
    _offset += _rm_size_in_words;
    Set_All_From_Offset();
    return true;
  }

  // Compute size of register mask: number of bits
  uint Size() const {
    uint sum = 0;
    assert(valid_watermarks(), "sanity");
    for (unsigned i = _lwm; i <= _hwm; i++) {
      sum += population_count(rm_word(i));
    }
    return sum;
  }

#ifndef PRODUCT
private:
  bool dump_end_run(outputStream* st, OptoReg::Name start,
                    OptoReg::Name last) const;

public:

  // ----------------------------------------------------------------------
  // The methods below are only for testing purposes (see test_regmask.cpp)
  // ----------------------------------------------------------------------

  unsigned int static gtest_basic_rm_size_in_words() {
    return RM_SIZE_IN_WORDS;
  }

  unsigned int static gtest_rm_size_in_bits_max() {
    return RM_SIZE_IN_WORDS_MAX * BitsPerWord;
  }

  bool gtest_equals(const RegMask& rm) const {
    assert(_offset == rm._offset, "offset mismatch");
    if (_infinite_stack != rm._infinite_stack) {
      return false;
    }
    // Shared segment
    for (unsigned int i = 0; i < MIN2(_rm_size_in_words, rm._rm_size_in_words); i++) {
      if (rm_word(i) != rm.rm_word(i)) {
        return false;
      }
    }
    // If there is a size difference, check the protruding segment against
    // infinite_stack.
    const unsigned int start = MIN2(_rm_size_in_words, rm._rm_size_in_words);
    const uintptr_t value = _infinite_stack ? uintptr_t(-1) : 0;
    for (unsigned int i = start; i < _rm_size_in_words; i++) {
      if (rm_word(i) != value) {
        return false;
      }
    }
    for (unsigned int i = start; i < rm._rm_size_in_words; i++) {
      if (rm.rm_word(i) != value) {
        return false;
      }
    }
    return true;
  }

  void gtest_set_offset(unsigned int offset) {
    _offset = offset;
  }

  // ----------------------
  // End of testing methods
  // ----------------------

  void print() const { dump(); }
  void dump(outputStream *st = tty) const; // Print a mask
  void dump_hex(outputStream* st = tty) const; // Print a mask (raw hex)
#endif

  static const RegMask Empty;   // Common empty mask
  static const RegMask All;     // Common all mask

  bool can_represent(OptoReg::Name reg, unsigned int size = 1) const {
    reg = reg - offset_bits();
    return reg >= 0 && reg <= (int)(rm_size_in_bits() - size);
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
      _current_bits = _rm.rm_word(_next_index++);
      if (_current_bits != 0) {
        // Found a word. Calculate the first register element and
        // prepare _current_bits by shifting it down and clearing
        // the lowest bit
        unsigned int next_bit = find_lowest_bit(_current_bits);
        assert(((_current_bits >> next_bit) & 0x1) == 1, "lowest bit must be set after shift");
        _current_bits = (_current_bits >> next_bit) - 1;
        _reg = OptoReg::Name(_rm.offset_bits() +
                             ((_next_index - 1) << LogBitsPerWord) +
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
#undef RM_SIZE_IN_INTS
#undef RM_SIZE_IN_INTS_MIN

#endif // SHARE_OPTO_REGMASK_HPP

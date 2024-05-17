/*
 * Copyright (c) 2016, 2024, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_G1_G1TASKQUEUEENTRY_HPP
#define SHARE_GC_G1_G1TASKQUEUEENTRY_HPP

// A task queue entry that encodes both regular oops, and the array oops plus sliceing data for
// parallel array processing.
// The design goal is to make the regular oop ops very fast, because that would be the prevailing
// case. On the other hand, it should not block parallel array processing from efficiently dividing
// the array work.
//
// The idea is to steal the bits from the 64-bit oop to encode array data, if needed. For the
// proper divide-and-conquer strategies, we want to encode the "blocking" data. It turns out, the
// most efficient way to do this is to encode the array block as (slice * 2^pow), where it is assumed
// that the block has the size of 2^pow. This requires for pow to have only 5 bits (2^32) to encode
// all possible arrays.
//
//    |xx-------oop---------|-pow-|--slice---|
//    0                    49     54        64
//
// By definition, slice == 0 means "no slice", i.e. sliceing starts from 1.
//
// This encoding gives a few interesting benefits:
//
// a) Encoding/decoding regular oops is very simple, because the upper bits are zero in that task:
//
//    |---------oop---------|00000|0000000000| // no slice data
//
//    This helps the most ubiquitous path. The initialization amounts to putting the oop into the word
//    with zero padding. Testing for "slicedness" is testing for zero with slice mask.
//
// b) Splitting tasks for divide-and-conquer is possible. Suppose we have slice <C, P> that covers
// interval [ (C-1)*2^P; C*2^P ). We can then split it into two slices:
//      <2*C - 1, P-1>, that covers interval [ (2*C - 2)*2^(P-1); (2*C - 1)*2^(P-1) )
//      <2*C, P-1>,     that covers interval [ (2*C - 1)*2^(P-1);       2*C*2^(P-1) )
//
//    Observe that the union of these two intervals is:
//      [ (2*C - 2)*2^(P-1); 2*C*2^(P-1) )
//
//    ...which is the original interval:
//      [ (C-1)*2^P; C*2^P )
//
// c) The divide-and-conquer strategy could even start with slice <1, round-log2-len(arr)>, and split
//    down in the parallel threads, which alleviates the upfront (serial) splitting costs.
//
// Encoding limitations caused by current bitscales mean:
//    10 bits for slice: max 1024 blocks per array
//     5 bits for power: max 2^32 array
//    49 bits for   oop: max 512 TB of addressable space
//
// Stealing bits from oop trims down the addressable space. Stealing too few bits for slice ID limits
// potential parallelism. Stealing too few bits for pow limits the maximum array size that can be handled.
// In future, these might be rebalanced to favor one degree of freedom against another. For example,
// if/when Arrays 2.0 bring 2^64-sized arrays, we might need to steal another bit for power. We could regain
// some bits back if slices are counted in ObjArrayMarkingStride units.
//
// There is also a fallback version that uses plain fields, when we don't have enough space to steal the
// bits from the native pointer. It is useful to debug the optimized version.
//
class G1TaskQueueEntry {
private:
  // Everything is encoded into this field...
  uintptr_t _val;

  // ...with these:
  static const uintptr_t OopTag = 0;
  static const uintptr_t NarrowOopTag = 1;
  static const uintptr_t TagMask = 1;

  static const uint8_t slice_bits  = 10;
  static const uint8_t pow_bits    = 5;
  static const uint8_t oop_bits    = sizeof(uintptr_t)*8 - slice_bits - pow_bits;

  static const uint8_t oop_shift   = 0;
  static const uint8_t pow_shift   = oop_bits;
  static const uint8_t slice_shift = oop_bits + pow_bits;

  static const uintptr_t oop_extract_mask       = right_n_bits(oop_bits) - 3;
  static const uintptr_t slice_pow_extract_mask = ~right_n_bits(oop_bits);

  static const int slice_range_mask = right_n_bits(slice_bits);
  static const int pow_range_mask   = right_n_bits(pow_bits);

  bool has_tag(uintptr_t val, uintptr_t tag) const {
    return (val & TagMask) == tag;
  }

  inline void* decode(uintptr_t val, uintptr_t tag) const {
    STATIC_ASSERT(oop_shift == 0);
    assert(has_tag(val, tag), "precondition");
    return cast_to_oop(val & oop_extract_mask);
  }

  inline bool decode_is_sliced(uintptr_t val) const {
    // No need to shift for a comparison to zero
    return (val & slice_pow_extract_mask) != 0;
  }

  inline int decode_slice(uintptr_t val) const {
    return (int) ((val >> slice_shift) & slice_range_mask);
  }

  inline int decode_pow(uintptr_t val) const {
    return (int) ((val >> pow_shift) & pow_range_mask);
  }

  inline uintptr_t encode_oop(void* p, uintptr_t tag) const {
    STATIC_ASSERT(oop_shift == 0);
    return reinterpret_cast<uintptr_t>(p) + tag;
  }

  inline uintptr_t encode_slice(int slice) const {
    return ((uintptr_t) slice) << slice_shift;
  }

  inline uintptr_t encode_pow(int pow) const {
    return ((uintptr_t) pow) << pow_shift;
  }

public:
  G1TaskQueueEntry() : _val(0) {
  }
  G1TaskQueueEntry(oop o) {
    uintptr_t enc = encode_oop(o, OopTag);
    assert(decode(enc, OopTag) == o, "oop encoding should work: " PTR_FORMAT, p2i(o));
    assert(!decode_is_sliced(enc),  "task should not be sliced");
    _val = enc;
  }
  G1TaskQueueEntry(oop* o) {
    uintptr_t enc = encode_oop(o, OopTag);
    assert(decode(enc, OopTag) == o, "oop encoding should work: " PTR_FORMAT, p2i(o));
    assert(!decode_is_sliced(enc),  "task should not be sliced");
    _val = enc;
  }
  G1TaskQueueEntry(narrowOop* o) {
    uintptr_t enc = encode_oop(o, NarrowOopTag);
    assert(decode(enc, NarrowOopTag) == o, "oop encoding should work: " PTR_FORMAT, p2i(o));
    assert(!decode_is_sliced(enc),  "task should not be sliced");
    _val = enc;
  }

  G1TaskQueueEntry(oop o, int slice, int pow) {
    uintptr_t enc_oop = encode_oop(o, OopTag);
    uintptr_t enc_slice = encode_slice(slice);
    uintptr_t enc_pow = encode_pow(pow);
    uintptr_t enc = enc_oop | enc_slice | enc_pow;
    assert(decode(enc, OopTag) == o,   "oop encoding should work: " PTR_FORMAT, p2i(o));
    assert(decode_slice(enc) == slice, "slice encoding should work: %d", slice);
    assert(decode_pow(enc) == pow,     "pow encoding should work: %d", pow);
    assert(decode_is_sliced(enc),   "task should be sliced");
    _val = enc;
  }

  // Trivially copyable.

public:
  bool is_oop_ptr()            const { return !decode_is_sliced(_val) && (_val & NarrowOopTag) == 0; }
  bool is_narrow_oop_ptr()     const { return !decode_is_sliced(_val) && (_val & NarrowOopTag) != 0; }
  bool is_array_slice()        const { return decode_is_sliced(_val);  }
  bool is_oop()                const { return !decode_is_sliced(_val); }
  bool is_null()               const { return _val == 0; }

  inline oop*       to_oop_ptr()        const { return reinterpret_cast<oop*>(decode(_val, OopTag));   }
  inline narrowOop* to_narrow_oop_ptr() const { return reinterpret_cast<narrowOop*>(decode(_val, NarrowOopTag));   }
  inline oop        to_oop()            const { return cast_to_oop(decode(_val, OopTag));   }
  inline int        slice()             const { return decode_slice(_val); }
  inline int        pow()               const { return decode_pow(_val);   }

  DEBUG_ONLY(bool is_valid() const;) // Tasks to be pushed/popped must be valid.

  static uintptr_t max_addressable() {
    return nth_bit(oop_bits);
  }

  static int slice_size() {
    return nth_bit(slice_bits);
  }
};

#endif // SHARE_GC_G1_G1TASKQUEUEENTRY_HPP

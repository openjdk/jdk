/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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
 */

#ifndef SHARE_GC_SHARED_SLIDINGFORWARDING_INLINE_HPP
#define SHARE_GC_SHARED_SLIDINGFORWARDING_INLINE_HPP

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/slidingForwarding.hpp"
#include "oops/markWord.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/macros.hpp"

inline bool SlidingForwarding::is_forwarded(oop obj) {
  return obj->is_forwarded();
}

inline bool SlidingForwarding::is_not_forwarded(oop obj) {
  return !obj->is_forwarded();
}

size_t SlidingForwarding::biased_region_index_containing(HeapWord* addr) {
  return (uintptr_t)addr >> _region_size_bytes_shift;
}

uintptr_t SlidingForwarding::encode_forwarding(HeapWord* from, HeapWord* to) {
  static_assert(NUM_TARGET_REGIONS == 2, "Only implemented for this amount");

  size_t from_reg_idx = biased_region_index_containing(from);
  HeapWord* to_region_base = (HeapWord*)((uintptr_t)to & _region_mask);

  HeapWord** base = &_biased_bases[0][from_reg_idx];
  uintptr_t alternate = 0;
  if (*base == to_region_base) {
    // Primary is good
  } else if (*base == UNUSED_BASE) {
    // Primary is free
    *base = to_region_base;
  } else {
    base = &_biased_bases[1][from_reg_idx];
    if (*base == to_region_base) {
      // Alternate is good
    } else if (*base == UNUSED_BASE) {
      // Alternate is free
      *base = to_region_base;
    } else {
      // Both primary and alternate are not fitting
      // This happens only in the following rare situations:
      // - In Serial GC, sometimes when compact-top switches spaces, because the
      //   region boudaries are virtual and objects can cross regions
      // - In G1 serial compaction, because tails of various compaction chains
      //   are distributed across the remainders of already compacted regions.
      return (1 << FALLBACK_SHIFT) | markWord::marked_value;
    }
    alternate = 1;
  }

  size_t offset = pointer_delta(to, to_region_base);
  assert(offset < _region_size_words, "Offset should be within the region. from: " PTR_FORMAT
         ", to: " PTR_FORMAT ", to_region_base: " PTR_FORMAT ", offset: " SIZE_FORMAT,
         p2i(from), p2i(to), p2i(to_region_base), offset);

  uintptr_t encoded = (offset << OFFSET_BITS_SHIFT) |
                      (alternate << ALT_REGION_SHIFT) |
                      markWord::marked_value;

  assert(to == decode_forwarding(from, encoded), "must be reversible");
  assert((encoded & ~MARK_LOWER_HALF_MASK) == 0, "must encode to lowest 32 bits");
  return encoded;
}

HeapWord* SlidingForwarding::decode_forwarding(HeapWord* from, uintptr_t encoded) {
  assert((encoded & markWord::lock_mask_in_place) == markWord::marked_value, "must be marked as forwarded");
  assert((encoded & FALLBACK_MASK) == 0, "must not be fallback-forwarded");
  assert((encoded & ~MARK_LOWER_HALF_MASK) == 0, "must decode from lowest 32 bits");
  size_t alternate = (encoded >> ALT_REGION_SHIFT) & right_n_bits(ALT_REGION_BITS);
  assert(alternate < NUM_TARGET_REGIONS, "Sanity");
  uintptr_t offset = (encoded >> OFFSET_BITS_SHIFT);

  size_t from_idx = biased_region_index_containing(from);
  HeapWord* base = _biased_bases[alternate][from_idx];
  assert(base != UNUSED_BASE, "must not be unused base");
  HeapWord* decoded = base + offset;
  assert(decoded >= _heap_start,
         "Address must be above heap start. encoded: " INTPTR_FORMAT ", alt_region: " SIZE_FORMAT ", base: " PTR_FORMAT,
         encoded, alternate, p2i(base));

  return decoded;
}

inline void SlidingForwarding::forward_to_impl(oop from, oop to) {
  assert(_bases_table != nullptr, "call begin() before forwarding");

  markWord from_header = from->mark();
  if (from_header.has_displaced_mark_helper()) {
    from_header = from_header.displaced_mark_helper();
  }

  HeapWord* from_hw = cast_from_oop<HeapWord*>(from);
  HeapWord* to_hw   = cast_from_oop<HeapWord*>(to);
  uintptr_t encoded = encode_forwarding(from_hw, to_hw);
  markWord new_header = markWord((from_header.value() & ~MARK_LOWER_HALF_MASK) | encoded);
  from->set_mark(new_header);

  if ((encoded & FALLBACK_MASK) != 0) {
    fallback_forward_to(from_hw, to_hw);
  }
}

template <bool ALT_FWD>
inline void SlidingForwarding::forward_to(oop obj, oop fwd) {
#ifdef _LP64
  if (ALT_FWD) {
    assert(_bases_table != nullptr, "expect sliding forwarding initialized");
    forward_to_impl(obj, fwd);
    assert(forwardee<ALT_FWD>(obj) == fwd, "must be forwarded to correct forwardee");
  } else
#endif
  {
    obj->forward_to(fwd);
  }
}

inline oop SlidingForwarding::forwardee_impl(oop from) {
  assert(_bases_table != nullptr, "call begin() before asking for forwarding");

  markWord header = from->mark();
  HeapWord* from_hw = cast_from_oop<HeapWord*>(from);
  if ((header.value() & FALLBACK_MASK) != 0) {
    HeapWord* to = fallback_forwardee(from_hw);
    return cast_to_oop(to);
  }
  uintptr_t encoded = header.value() & MARK_LOWER_HALF_MASK;
  HeapWord* to = decode_forwarding(from_hw, encoded);
  return cast_to_oop(to);
}

template <bool ALT_FWD>
inline oop SlidingForwarding::forwardee(oop obj) {
#ifdef _LP64
  if (ALT_FWD) {
    assert(_bases_table != nullptr, "expect sliding forwarding initialized");
    return forwardee_impl(obj);
  } else
#endif
  {
    return obj->forwardee();
  }
}

#endif // SHARE_GC_SHARED_SLIDINGFORWARDING_INLINE_HPP

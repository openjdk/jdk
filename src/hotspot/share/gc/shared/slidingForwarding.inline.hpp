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

size_t SlidingForwarding::region_index_containing(HeapWord* addr) {
  //size_t index = pointer_delta(addr, _heap_start) >> _region_size_words_shift;
  size_t index = (uintptr_t)addr >> _region_size_bytes_shift;

  assert((index - _heap_start_base_idx) == pointer_delta(addr, _heap_start) >> _region_size_words_shift,
         "index %zu heap base idx %zu comp %zu",
          index, _heap_start_base_idx, pointer_delta(addr, _heap_start) >> _region_size_words_shift
         );
  assert(index < (_heap_start_base_idx + _num_regions) && index >= _heap_start_base_idx,
         "Region index is in bounds %zu %zu %zu: " PTR_FORMAT " %zu %zu",
          index, _heap_start_base_idx, _heap_start_base_idx + _num_regions, p2i(addr), pointer_delta(addr, _heap_start) >> _region_size_words_shift, _heap_start_base_idx);
  return index;
}

uintptr_t SlidingForwarding::encode_forwarding(HeapWord* from, HeapWord* to) {
  size_t from_reg_idx = region_index_containing(from) * NUM_TARGET_REGIONS;

  HeapWord* to_region_base = (HeapWord*) ((uintptr_t)to & _region_mask);

  HeapWord** cell = &_biased_bases_table[from_reg_idx];

  uintptr_t alt_region = 0;
  if (*cell == to_region_base) {
    // Primary is good
  } else if (*cell == UNUSED_BASE) {
    // Primary is free
    *cell = to_region_base;
  } else {
    cell++;
    if (*cell == to_region_base) {
      // Alternate is good
    } else if (*cell == UNUSED_BASE) {
      // Alternate is free
      *cell = to_region_base;
    } else {
      // Both primary and alternate are not fitting
      assert(UseG1GC, "Only happens with G1 serial compaction");
      return (1 << FALLBACK_SHIFT) | markWord::marked_value;
    }
    alt_region = 1;
  }

  size_t offset = pointer_delta(to, to_region_base);
  assert(offset < _region_size_words, "Offset should be within the region. from: " PTR_FORMAT
         ", to: " PTR_FORMAT ", to_region_base: " PTR_FORMAT ", offset: " SIZE_FORMAT,
         p2i(from), p2i(to), p2i(to_region_base), offset);

  uintptr_t encoded = (offset << OFFSET_BITS_SHIFT) |
                      (alt_region << ALT_REGION_SHIFT) |
                      markWord::marked_value;

  assert(to == decode_forwarding(from, encoded), "must be reversible");
  assert((encoded & ~MARK_LOWER_HALF_MASK) == 0, "must encode to lowest 32 bits");
  return encoded;
}

HeapWord* SlidingForwarding::decode_forwarding(HeapWord* from, uintptr_t encoded) {
  assert((encoded & markWord::lock_mask_in_place) == markWord::marked_value, "must be marked as forwarded");
  assert((encoded & FALLBACK_MASK) == 0, "must not be fallback-forwarded");
  assert((encoded & ~MARK_LOWER_HALF_MASK) == 0, "must decode from lowest 32 bits");
  size_t alt_region = (encoded >> ALT_REGION_SHIFT) & right_n_bits(ALT_REGION_BITS);
  assert(alt_region < NUM_TARGET_REGIONS, "Sanity");
  uintptr_t offset = (encoded >> OFFSET_BITS_SHIFT);

  size_t from_idx = region_index_containing(from) * NUM_TARGET_REGIONS;
  HeapWord* base = _biased_bases_table[from_idx + alt_region];
  assert(base != UNUSED_BASE, "must not be unused base");
  HeapWord* decoded = base + offset;
  assert(decoded >= _heap_start,
         "Address must be above heap start. encoded: " INTPTR_FORMAT ", alt_region: " SIZE_FORMAT ", base: " PTR_FORMAT,
         encoded, alt_region, p2i(base));

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

inline void SlidingForwarding::forward_to(oop obj, oop fwd) {
#ifdef _LP64
  if (UseAltGCForwarding) {
    assert(_bases_table != nullptr, "expect sliding forwarding initialized");
    forward_to_impl(obj, fwd);
    assert(forwardee(obj) == fwd, "must be forwarded to correct forwardee");
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

inline oop SlidingForwarding::forwardee(oop obj) {
#ifdef _LP64
  if (UseAltGCForwarding) {
    assert(_bases_table != nullptr, "expect sliding forwarding initialized");
    return forwardee_impl(obj);
  } else
#endif
  {
    return obj->forwardee();
  }
}

#endif // SHARE_GC_SHARED_SLIDINGFORWARDING_INLINE_HPP

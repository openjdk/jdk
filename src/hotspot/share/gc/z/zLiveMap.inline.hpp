/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZLIVEMAP_INLINE_HPP
#define SHARE_GC_Z_ZLIVEMAP_INLINE_HPP

#include "gc/z/zLiveMap.hpp"

#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zBitMap.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zMark.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"

inline void ZLiveMap::reset() {
  _seqnum.store_relaxed(0u);
}

inline bool ZLiveMap::is_marked(ZGenerationId id) const {
  return _seqnum.load_acquire() == ZGeneration::generation(id)->seqnum();
}

inline uint32_t ZLiveMap::live_objects() const {
  return _live_objects.load_relaxed();
}

inline size_t ZLiveMap::live_bytes() const {
  return _live_bytes.load_relaxed();
}

inline const BitMapView ZLiveMap::segment_live_bits() const {
  return BitMapView(const_cast<BitMap::bm_word_t*>(&_segment_live_bits), NumSegments);
}

inline const BitMapView ZLiveMap::segment_claim_bits() const {
  return BitMapView(const_cast<BitMap::bm_word_t*>(&_segment_claim_bits), NumSegments);
}

inline BitMapView ZLiveMap::segment_live_bits() {
  return BitMapView(&_segment_live_bits, NumSegments);
}

inline BitMapView ZLiveMap::segment_claim_bits() {
  return BitMapView(&_segment_claim_bits, NumSegments);
}

inline bool ZLiveMap::is_segment_live(BitMap::idx_t segment) const {
  return segment_live_bits().par_at(segment);
}

inline bool ZLiveMap::set_segment_live(BitMap::idx_t segment) {
  return segment_live_bits().par_set_bit(segment, memory_order_release);
}

inline bool ZLiveMap::claim_segment(BitMap::idx_t segment) {
  return segment_claim_bits().par_set_bit(segment, memory_order_acq_rel);
}

inline BitMap::idx_t ZLiveMap::first_live_segment() const {
  return segment_live_bits().find_first_set_bit(0, NumSegments);
}

inline BitMap::idx_t ZLiveMap::next_live_segment(BitMap::idx_t segment) const {
  return segment_live_bits().find_first_set_bit(segment + 1, NumSegments);
}

inline BitMap::idx_t ZLiveMap::index_to_segment(BitMap::idx_t index) const {
  return index >> _segment_shift;
}

inline bool ZLiveMap::get(ZGenerationId id, BitMap::idx_t index) const {
  const BitMap::idx_t segment = index_to_segment(index);
  return is_marked(id) &&                             // Page is marked
         is_segment_live(segment) &&                  // Segment is marked
         _bitmap.par_at(index, memory_order_relaxed); // Object is marked
}

inline bool ZLiveMap::set(ZGenerationId id, BitMap::idx_t index, bool finalizable, bool& inc_live) {
  if (!is_marked(id)) {
    // First object to be marked during this
    // cycle, reset marking information.
    reset(id);
  }

  const BitMap::idx_t segment = index_to_segment(index);
  if (!is_segment_live(segment)) {
    // First object to be marked in this segment during
    // this cycle, reset segment bitmap.
    reset_segment(segment);
  }

  return _bitmap.par_set_bit_pair(index, finalizable, inc_live);
}

inline void ZLiveMap::inc_live(uint32_t objects, size_t bytes) {
  _live_objects.add_then_fetch(objects);
  _live_bytes.add_then_fetch(bytes);
}

inline BitMap::idx_t ZLiveMap::segment_start(BitMap::idx_t segment) const {
  return segment * _segment_size;
}

inline BitMap::idx_t ZLiveMap::segment_end(BitMap::idx_t segment) const {
  return segment_start(segment) + _segment_size;
}

inline size_t ZLiveMap::do_object(ObjectClosure* cl, zaddress addr) const {
  // Get the size of the object before calling the closure, which
  // might overwrite the object in case we are relocating in-place.
  const size_t size = ZUtils::object_size(addr);

  // Apply closure
  cl->do_object(to_oop(addr));

  return size;
}

template <typename Function>
inline void ZLiveMap::iterate_segment(BitMap::idx_t segment, Function function) {
  assert(is_segment_live(segment), "Must be");

  const BitMap::idx_t start_index = segment_start(segment);
  const BitMap::idx_t end_index   = segment_end(segment);

  _bitmap.iterate(function, start_index, end_index);
}

template <typename Function>
inline void ZLiveMap::iterate(ZGenerationId id, Function function) {
  if (!is_marked(id)) {
    return;
  }

  auto live_only = [&](BitMap::idx_t index) -> bool {
    if ((index & 1) == 0) {
      return function(index);
    }
    // Don't visit the finalizable bits
    return true;
  };

  for (BitMap::idx_t segment = first_live_segment(); segment < NumSegments; segment = next_live_segment(segment)) {
    // For each live segment
    iterate_segment(segment, live_only);
  }
}

// Find the bit index that correspond the start of the object that is lower,
// or equal, to the given index (index is inclusive).
//
// Typically used to find the start of an object when there's only a field
// address available. Note that it's not guaranteed that the found index
// corresponds to an object that spans the given index. This function just
// looks at the bits. The calling code is responsible to check the object
// at the returned index.
//
// returns -1 if no bit was found
inline BitMap::idx_t ZLiveMap::find_base_bit(BitMap::idx_t index) {
  // Check first segment
  const BitMap::idx_t start_segment = index_to_segment(index);
  if (is_segment_live(start_segment)) {
    const BitMap::idx_t res = find_base_bit_in_segment(segment_start(start_segment), index);
    if (res != BitMap::idx_t(-1)) {
      return res;
    }
  }

  // Search earlier segments
  for (BitMap::idx_t segment = start_segment; segment-- > 0; ) {
    if (is_segment_live(segment)) {
      const BitMap::idx_t res = find_base_bit_in_segment(segment_start(segment), segment_end(segment) - 1);
      if (res != BitMap::idx_t(-1)) {
        return res;
      }
    }
  }

  // Not found
  return BitMap::idx_t(-1);
}

// Find the bit index that correspond the start of the object that is lower,
// or equal, to the given index (index is inclusive). Stopping when reaching
// start.
inline BitMap::idx_t ZLiveMap::find_base_bit_in_segment(BitMap::idx_t start, BitMap::idx_t index) {
  assert(index_to_segment(start) == index_to_segment(index), "Only supports searches within segments start: %zu index: %zu", start, index);
  assert(is_segment_live(index_to_segment(start)), "Must be live");

  // Search backwards - + 1 to make an exclusive index.
  const BitMap::idx_t end = index + 1;
  const BitMap::idx_t bit = _bitmap.find_last_set_bit(start, end);
  if (bit == end) {
    return BitMap::idx_t(-1);
  }

  // The bitmaps contain pairs of bits to deal with strongly marked vs only
  // finalizable marked. Align down to get the first bit position.
  return bit & ~BitMap::idx_t(1);
}

#endif // SHARE_GC_Z_ZLIVEMAP_INLINE_HPP

/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zCycle.inline.hpp"
#include "gc/z/zMark.hpp"
#include "gc/z/zUtils.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"

inline void ZLiveMap::reset() {
  _seqnum = 0;
}

inline bool ZLiveMap::is_marked(ZGenerationId generation_id) const {
  return Atomic::load_acquire(&_seqnum) == ZHeap::heap()->get_cycle(generation_id)->seqnum();
}

inline uint32_t ZLiveMap::live_objects() const {
  return _live_objects;
}

inline size_t ZLiveMap::live_bytes() const {
  return _live_bytes;
}

inline const BitMapView ZLiveMap::segment_live_bits() const {
  return BitMapView(const_cast<BitMap::bm_word_t*>(&_segment_live_bits), nsegments);
}

inline const BitMapView ZLiveMap::segment_claim_bits() const {
  return BitMapView(const_cast<BitMap::bm_word_t*>(&_segment_claim_bits), nsegments);
}

inline BitMapView ZLiveMap::segment_live_bits() {
  return BitMapView(&_segment_live_bits, nsegments);
}

inline BitMapView ZLiveMap::segment_claim_bits() {
  return BitMapView(&_segment_claim_bits, nsegments);
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
  return segment_live_bits().get_next_one_offset(0, nsegments);
}

inline BitMap::idx_t ZLiveMap::next_live_segment(BitMap::idx_t segment) const {
  return segment_live_bits().get_next_one_offset(segment + 1, nsegments);
}

inline BitMap::idx_t ZLiveMap::segment_size() const {
  return _bitmap.size() / nsegments;
}

inline BitMap::idx_t ZLiveMap::index_to_segment(BitMap::idx_t index) const {
  return index >> _segment_shift;
}

inline bool ZLiveMap::get(ZGenerationId generation_id, size_t index) const {
  BitMap::idx_t segment = index_to_segment(index);
  return is_marked(generation_id) && // Page is marked
         is_segment_live(segment) && // Segment is marked
         _bitmap.at(index);          // Object is marked
}

inline bool ZLiveMap::set(ZGenerationId generation_id, size_t index, bool finalizable, bool& inc_live) {
  if (!is_marked(generation_id)) {
    // First object to be marked during this
    // cycle, reset marking information.
    reset(generation_id, index);
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
  Atomic::add(&_live_objects, objects);
  Atomic::add(&_live_bytes, bytes);
}

inline BitMap::idx_t ZLiveMap::segment_start(BitMap::idx_t segment) const {
  return segment_size() * segment;
}

inline BitMap::idx_t ZLiveMap::segment_end(BitMap::idx_t segment) const {
  return segment_start(segment) + segment_size();
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

  _bitmap.iterate_f(function, start_index, end_index);
}

template <typename Function>
inline void ZLiveMap::iterate(ZGenerationId generation_id, Function function) {
  if (!is_marked(generation_id)) {
    return;
  }

  auto live_only = [&](BitMap::idx_t index) -> bool {
    if ((index & 1) == 0) {
      return function(index);
    }
    // Don't visit the finalizable bits
    return true;
  };

  for (BitMap::idx_t segment = first_live_segment(); segment < nsegments; segment = next_live_segment(segment)) {
    // For each live segment
    iterate_segment(segment, live_only);
  }
}

inline size_t ZLiveMap::find_base_bit(size_t index) {
  // Check first segment
  BitMap::idx_t start_segment = index_to_segment(index);
  if (is_segment_live(start_segment)) {
    size_t res = find_base_bit(segment_start(start_segment), index);
    if (res != size_t(-1)) {
      return res;
    }
  }

  // Search earlier segments
  for (BitMap::idx_t segment = start_segment; segment-- > 0; ) {
    if (is_segment_live(segment)) {
      size_t res = find_base_bit(segment_start(segment), segment_end(segment) - 1);
      if (res != size_t(-1)) {
        return res;
      }
    }
  }

  return size_t(-1);
}

inline size_t ZLiveMap::find_base_bit(size_t start, size_t end) {
  assert(index_to_segment(start) == index_to_segment(end), "Only supports searches within segments");
  assert(is_segment_live(index_to_segment(end)), "Must be live");

  BitMap::idx_t bit = _bitmap.get_prev_one_offset(start, end);
  if (bit == size_t(-1)) {
    return size_t(-1);
  }

  // Align down marked vs strongly marked
  return bit & ~BitMap::idx_t(1);
}

#endif // SHARE_GC_Z_ZLIVEMAP_INLINE_HPP

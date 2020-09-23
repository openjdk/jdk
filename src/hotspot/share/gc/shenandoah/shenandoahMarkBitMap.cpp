/*
 * Copyright (c) 2020, Red Hat, Inc. and/or its affiliates.
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

#include "gc/shenandoah/shenandoahMarkBitMap.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "utilities/globalDefinitions.hpp"

ShenandoahMarkBitMap::ShenandoahMarkBitMap(MemRegion heap, MemRegion storage) :
  _shift(LogMinObjAlignment),
  _covered(heap),
  _bit_map((BitMap::bm_word_t*) storage.start(), (heap.word_size() * 2) >> _shift) {
}

size_t ShenandoahMarkBitMap::compute_size(size_t heap_size) {
  return ReservedSpace::allocation_align_size_up(heap_size / mark_distance());
}

size_t ShenandoahMarkBitMap::mark_distance() {
  return MinObjAlignmentInBytes * BitsPerByte / 2;
}

HeapWord* ShenandoahMarkBitMap::get_next_marked_addr(const HeapWord* addr,
                                                     const HeapWord* limit) const {
  assert(limit != NULL, "limit must not be NULL");
  // Round addr up to a possible object boundary to be safe.
  size_t const addr_offset = address_to_index(align_up(addr, HeapWordSize << LogMinObjAlignment));
  size_t const limit_offset = address_to_index(limit);
  size_t const nextOffset = _bit_map.get_next_one_offset(addr_offset, limit_offset);
  return index_to_address(nextOffset);
}

void ShenandoahMarkBitMap::clear_range_large(MemRegion mr) {
  MemRegion intersection = mr.intersection(_covered);
  assert(!intersection.is_empty(),
         "Given range from " PTR_FORMAT " to " PTR_FORMAT " is completely outside the heap",
          p2i(mr.start()), p2i(mr.end()));
  // convert address range into offset range
  size_t beg = address_to_index(intersection.start());
  size_t end = address_to_index(intersection.end());
  _bit_map.clear_large_range(beg, end);
}

#ifdef ASSERT
void ShenandoahMarkBitMap::check_mark(HeapWord* addr) const {
  assert(ShenandoahHeap::heap()->is_in(addr),
         "Trying to access bitmap " PTR_FORMAT " for address " PTR_FORMAT " not in the heap.",
         p2i(this), p2i(addr));
}
#endif

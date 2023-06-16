/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1FULLGCCOMPACTIONPOINT_INLINE_HPP
#define SHARE_GC_G1_G1FULLGCCOMPACTIONPOINT_INLINE_HPP

#include "gc/g1/g1FullGCCompactionPoint.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/slidingForwarding.inline.hpp"
#include "oops/oop.inline.hpp"

template<bool ALT_FWD>
void G1FullGCCompactionPoint::forward(oop object, size_t size) {
  assert(_current_region != nullptr, "Must have been initialized");

  // Ensure the object fit in the current region.
  while (!object_will_fit(size)) {
    switch_region();
  }

  // Store a forwarding pointer if the object should be moved.
  if (cast_from_oop<HeapWord*>(object) != _compaction_top) {
    SlidingForwarding::forward_to<ALT_FWD>(object, cast_to_oop(_compaction_top));
    assert(SlidingForwarding::is_forwarded(object), "must be forwarded");
  } else {
    assert(SlidingForwarding::is_not_forwarded(object), "must not be forwarded");
  }

  // Update compaction values.
  _compaction_top += size;
  _current_region->update_bot_for_block(_compaction_top - size, _compaction_top);
}

template<bool ALT_FWD>
uint G1FullGCCompactionPoint::forward_humongous(HeapRegion* hr) {
  assert(hr->is_starts_humongous(), "Sanity!");

  oop obj = cast_to_oop(hr->bottom());
  size_t obj_size = obj->size();
  uint num_regions = (uint)G1CollectedHeap::humongous_obj_size_in_regions(obj_size);

  if (!has_regions()) {
    return num_regions;
  }

  // Find contiguous compaction target regions for the humongous object.
  uint range_begin = find_contiguous_before(hr, num_regions);

  if (range_begin == UINT_MAX) {
    // No contiguous compaction target regions found, so the object cannot be moved.
    return num_regions;
  }

  // Preserve the mark for the humongous object as the region was initially not compacting.
  _collector->marker(0)->preserved_stack()->push_if_necessary(obj, obj->mark());

  HeapRegion* dest_hr = _compaction_regions->at(range_begin);
  SlidingForwarding::forward_to<ALT_FWD>(obj, cast_to_oop(dest_hr->bottom()));
  assert(SlidingForwarding::is_forwarded(obj), "Object must be forwarded!");

  // Add the humongous object regions to the compaction point.
  add_humongous(hr);

  // Remove covered regions from compaction target candidates.
  _compaction_regions->remove_range(range_begin, (range_begin + num_regions));

  return num_regions;
}

#endif // SHARE_GC_G1_G1FULLGCCOMPACTIONPOINT_INLINE_HPP

/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1FullCollector.inline.hpp"
#include "gc/g1/g1FullGCCompactionPoint.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/shared/preservedMarks.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"

G1FullGCCompactionPoint::G1FullGCCompactionPoint(G1FullCollector* collector, PreservedMarks* preserved_stack) :
    _collector(collector),
    _current_region(nullptr),
    _compaction_top(nullptr),
    _preserved_stack(preserved_stack) {
  _compaction_regions = new (mtGC) GrowableArray<HeapRegion*>(32, mtGC);
  _compaction_region_iterator = _compaction_regions->begin();
}

G1FullGCCompactionPoint::~G1FullGCCompactionPoint() {
  delete _compaction_regions;
}

void G1FullGCCompactionPoint::update() {
  if (is_initialized()) {
    _collector->set_compaction_top(_current_region, _compaction_top);
  }
}

void G1FullGCCompactionPoint::initialize_values() {
  _compaction_top = _collector->compaction_top(_current_region);
}

bool G1FullGCCompactionPoint::has_regions() {
  return !_compaction_regions->is_empty();
}

bool G1FullGCCompactionPoint::is_initialized() {
  return _current_region != nullptr;
}

void G1FullGCCompactionPoint::initialize(HeapRegion* hr) {
  _current_region = hr;
  initialize_values();
}

HeapRegion* G1FullGCCompactionPoint::current_region() {
  return *_compaction_region_iterator;
}

HeapRegion* G1FullGCCompactionPoint::next_region() {
  HeapRegion* next = *(++_compaction_region_iterator);
  assert(next != nullptr, "Must return valid region");
  return next;
}

GrowableArray<HeapRegion*>* G1FullGCCompactionPoint::regions() {
  return _compaction_regions;
}

bool G1FullGCCompactionPoint::object_will_fit(size_t size) {
  size_t space_left = pointer_delta(_current_region->end(), _compaction_top);
  return size <= space_left;
}

void G1FullGCCompactionPoint::switch_region() {
  // Save compaction top in the region.
  _collector->set_compaction_top(_current_region, _compaction_top);
  // Get the next region and re-initialize the values.
  _current_region = next_region();
  initialize_values();
}

void G1FullGCCompactionPoint::forward(oop object, size_t size) {
  assert(_current_region != nullptr, "Must have been initialized");

  // Ensure the object fit in the current region.
  while (!object_will_fit(size)) {
    switch_region();
  }

  // Store a forwarding pointer if the object should be moved.
  if (cast_from_oop<HeapWord*>(object) != _compaction_top) {
    if (!object->is_forwarded()) {
      preserved_stack()->push_if_necessary(object, object->mark());
    }
    object->forward_to(cast_to_oop(_compaction_top));
    assert(object->is_forwarded(), "must be forwarded");
  } else {
    assert(!object->is_forwarded(), "must not be forwarded");
  }

  // Update compaction values.
  _compaction_top += size;
  _current_region->update_bot_for_block(_compaction_top - size, _compaction_top);
}

void G1FullGCCompactionPoint::add(HeapRegion* hr) {
  _compaction_regions->append(hr);
}

void G1FullGCCompactionPoint::remove_at_or_above(uint bottom) {
  HeapRegion* cur = current_region();
  assert(cur->hrm_index() >= bottom, "Sanity!");

  int start_index = 0;
  for (HeapRegion* r : *_compaction_regions) {
    if (r->hrm_index() < bottom) {
      start_index++;
    }
  }

  assert(start_index >= 0, "Should have at least one region");
  _compaction_regions->trunc_to(start_index);
}

void G1FullGCCompactionPoint::add_humongous(HeapRegion* hr) {
  assert(hr->is_starts_humongous(), "Sanity!");

  _collector->add_humongous_region(hr);

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  g1h->humongous_obj_regions_iterate(hr,
                                     [&] (HeapRegion* r) {
                                       add(r);
                                       _collector->update_from_skip_compacting_to_compacting(r->hrm_index());
                                     });
}

void G1FullGCCompactionPoint::forward_humongous(HeapRegion* hr) {
  assert(hr->is_starts_humongous(), "Sanity!");

  oop obj = cast_to_oop(hr->bottom());
  size_t obj_size = obj->size();
  uint num_regions = (uint)G1CollectedHeap::humongous_obj_size_in_regions(obj_size);

  if (!has_regions()) {
    return;
  }

  // Find contiguous compaction target regions for the humongous object.
  uint range_begin = find_contiguous_before(hr, num_regions);

  if (range_begin == UINT_MAX) {
    // No contiguous compaction target regions found, so the object cannot be moved.
    return;
  }

  // Preserve the mark for the humongous object as the region was initially not compacting.
  preserved_stack()->push_if_necessary(obj, obj->mark());

  HeapRegion* dest_hr = _compaction_regions->at(range_begin);
  obj->forward_to(cast_to_oop(dest_hr->bottom()));
  assert(obj->is_forwarded(), "Object must be forwarded!");

  // Add the humongous object regions to the compaction point.
  add_humongous(hr);

  // Remove covered regions from compaction target candidates.
  _compaction_regions->remove_range(range_begin, (range_begin + num_regions));

  return;
}

uint G1FullGCCompactionPoint::find_contiguous_before(HeapRegion* hr, uint num_regions) {
  assert(num_regions > 0, "Sanity!");
  assert(has_regions(), "Sanity!");

  if (num_regions == 1) {
    // If only one region, return the first region.
    return 0;
  }

  uint contiguous_region_count = 1;

  uint range_end = 1;
  uint range_limit = (uint)_compaction_regions->length();

  for (; range_end < range_limit; range_end++) {
    if (contiguous_region_count == num_regions) {
      break;
    }
    // Check if the current region and the previous region are contiguous.
    bool regions_are_contiguous = (_compaction_regions->at(range_end)->hrm_index() - _compaction_regions->at(range_end - 1)->hrm_index()) == 1;
    contiguous_region_count = regions_are_contiguous ? contiguous_region_count + 1 : 1;
  }

  if (contiguous_region_count < num_regions &&
      hr->hrm_index() - _compaction_regions->at(range_end-1)->hrm_index() != 1) {
    // We reached the end but the final region is not contiguous with the target region;
    // no contiguous regions to move to.
    return UINT_MAX;
  }
  // Return the index of the first region in the range of contiguous regions.
  return range_end - contiguous_region_count;
}

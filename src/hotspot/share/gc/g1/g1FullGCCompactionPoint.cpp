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
#include "gc/g1/heapRegion.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/debug.hpp"

G1FullGCCompactionPoint::G1FullGCCompactionPoint(G1FullCollector* collector) :
    _collector(collector),
    _current_region(nullptr),
    _compaction_top(nullptr) {
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
  return _current_region != NULL;
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
  assert(next != NULL, "Must return valid region");
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
  assert(_current_region != NULL, "Must have been initialized");

  // Ensure the object fit in the current region.
  while (!object_will_fit(size)) {
    switch_region();
  }

  // Store a forwarding pointer if the object should be moved.
  if (cast_from_oop<HeapWord*>(object) != _compaction_top) {
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

int G1FullGCCompactionPoint::compare(HeapRegion* const& hr, HeapRegion* const& e) {
  return compare(hr->hrm_index(), e->hrm_index());
}

void G1FullGCCompactionPoint::sort_regions(){
  regions()->sort(compare_ptr);
}

int G1FullGCCompactionPoint::find_sorted(HeapRegion* hr) {
  bool found = false;
  int pos = _compaction_regions->find_sorted<HeapRegion*, compare>(hr, found);
  if (found) {
    return pos;
  }
  return -1;
}

void G1FullGCCompactionPoint::move_regions_with_higher_hrm_index(G1FullGCCompactionPoint* cp, uint bottom) {
  HeapRegion* cur = current_region();
  assert(cur->hrm_index() >= bottom, "Sanity!");

  HeapRegion* start = nullptr;
  for (HeapRegion* r : *_compaction_regions) {
    if (r->hrm_index() >= bottom) {
      cp->add(r);
      if (start == nullptr) {
        start = r;
      }
    }
  }

  int start_index = find_sorted(start);
  assert(start_index >= 0, "Should have atleast one region");
  _compaction_regions->trunc_to(start_index);
}

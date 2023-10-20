/*
 * Copyright (c) 2016, 2021, Red Hat, Inc. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "gc/shenandoah/shenandoahAffiliation.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/orderAccess.hpp"

ShenandoahSetsOfFree::ShenandoahSetsOfFree(size_t max_regions, ShenandoahFreeSet* free_set) :
    _max(max_regions),
    _free_set(free_set),
    _region_size_bytes(ShenandoahHeapRegion::region_size_bytes())
{
  _membership = NEW_C_HEAP_ARRAY(ShenandoahFreeMemoryType, max_regions, mtGC);
  clear_internal();
}

ShenandoahSetsOfFree::~ShenandoahSetsOfFree() {
  FREE_C_HEAP_ARRAY(ShenandoahFreeMemoryType, _membership);
}


void ShenandoahSetsOfFree::clear_internal() {
  for (size_t idx = 0; idx < _max; idx++) {
    _membership[idx] = NotFree;
  }

  for (size_t idx = 0; idx < NumFreeSets; idx++) {
    _leftmosts[idx] = _max;
    _rightmosts[idx] = 0;
    _leftmosts_empty[idx] = _max;
    _rightmosts_empty[idx] = 0;
    _capacity_of[idx] = 0;
    _used_by[idx] = 0;
  }

  _left_to_right_bias[Mutator] = true;
  _left_to_right_bias[Collector] = false;
  _left_to_right_bias[OldCollector] = false;

  _region_counts[Mutator] = 0;
  _region_counts[Collector] = 0;
  _region_counts[OldCollector] = 0;
  _region_counts[NotFree] = _max;
}

void ShenandoahSetsOfFree::clear_all() {
  clear_internal();
}

void ShenandoahSetsOfFree::increase_used(ShenandoahFreeMemoryType which_set, size_t bytes) {
  assert (which_set > NotFree && which_set < NumFreeSets, "Set must correspond to a valid freeset");
  _used_by[which_set] += bytes;
  assert (_used_by[which_set] <= _capacity_of[which_set],
          "Must not use (" SIZE_FORMAT ") more than capacity (" SIZE_FORMAT ") after increase by " SIZE_FORMAT,
          _used_by[which_set], _capacity_of[which_set], bytes);
}

inline void ShenandoahSetsOfFree::shrink_bounds_if_touched(ShenandoahFreeMemoryType set, size_t idx) {
  if (idx == _leftmosts[set]) {
    while ((_leftmosts[set] < _max) && !in_free_set(_leftmosts[set], set)) {
      _leftmosts[set]++;
    }
    if (_leftmosts_empty[set] < _leftmosts[set]) {
      // This gets us closer to where we need to be; we'll scan further when leftmosts_empty is requested.
      _leftmosts_empty[set] = _leftmosts[set];
    }
  }
  if (idx == _rightmosts[set]) {
    while (_rightmosts[set] > 0 && !in_free_set(_rightmosts[set], set)) {
      _rightmosts[set]--;
    }
    if (_rightmosts_empty[set] > _rightmosts[set]) {
      // This gets us closer to where we need to be; we'll scan further when rightmosts_empty is requested.
      _rightmosts_empty[set] = _rightmosts[set];
    }
  }
}

inline void ShenandoahSetsOfFree::expand_bounds_maybe(ShenandoahFreeMemoryType set, size_t idx, size_t region_capacity) {
  if (region_capacity == _region_size_bytes) {
    if (_leftmosts_empty[set] > idx) {
      _leftmosts_empty[set] = idx;
    }
    if (_rightmosts_empty[set] < idx) {
      _rightmosts_empty[set] = idx;
    }
  }
  if (_leftmosts[set] > idx) {
    _leftmosts[set] = idx;
  }
  if (_rightmosts[set] < idx) {
    _rightmosts[set] = idx;
  }
}

void ShenandoahSetsOfFree::remove_from_free_sets(size_t idx) {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  ShenandoahFreeMemoryType orig_set = membership(idx);
  assert (orig_set > NotFree && orig_set < NumFreeSets, "Cannot remove from free sets if not already free");
  _membership[idx] = NotFree;
  shrink_bounds_if_touched(orig_set, idx);

  _region_counts[orig_set]--;
  _region_counts[NotFree]++;
}


void ShenandoahSetsOfFree::make_free(size_t idx, ShenandoahFreeMemoryType which_set, size_t region_capacity) {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert (_membership[idx] == NotFree, "Cannot make free if already free");
  assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
  _membership[idx] = which_set;
  _capacity_of[which_set] += region_capacity;
  expand_bounds_maybe(which_set, idx, region_capacity);

  _region_counts[NotFree]--;
  _region_counts[which_set]++;
}

void ShenandoahSetsOfFree::move_to_set(size_t idx, ShenandoahFreeMemoryType new_set, size_t region_capacity) {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert ((new_set > NotFree) && (new_set < NumFreeSets), "New set must be valid");
  ShenandoahFreeMemoryType orig_set = _membership[idx];
  assert ((orig_set > NotFree) && (orig_set < NumFreeSets), "Cannot move free unless already free");
  // Expected transitions:
  //  During rebuild: Mutator => Collector
  //                  Mutator empty => Collector
  //  During flip_to_gc:
  //                  Mutator empty => Collector
  //                  Mutator empty => Old Collector
  // At start of update refs:
  //                  Collector => Mutator
  //                  OldCollector Empty => Mutator
  assert (((region_capacity <= _region_size_bytes) &&
           ((orig_set == Mutator) && (new_set == Collector)) ||
           ((orig_set == Collector) && (new_set == Mutator))) ||
          ((region_capacity == _region_size_bytes) &&
           ((orig_set == Mutator) && (new_set == Collector)) ||
           ((orig_set == OldCollector) && (new_set == Mutator)) ||
           (new_set == OldCollector)), "Unexpected movement between sets");

  _membership[idx] = new_set;
  _capacity_of[orig_set] -= region_capacity;
  shrink_bounds_if_touched(orig_set, idx);

  _capacity_of[new_set] += region_capacity;
  expand_bounds_maybe(new_set, idx, region_capacity);

  _region_counts[orig_set]--;
  _region_counts[new_set]++;
}

inline ShenandoahFreeMemoryType ShenandoahSetsOfFree::membership(size_t idx) const {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  return _membership[idx];
}

  // Returns true iff region idx is in the test_set free_set.  Before returning true, asserts that the free
  // set is not empty.  Requires that test_set != NotFree or NumFreeSets.
inline bool ShenandoahSetsOfFree::in_free_set(size_t idx, ShenandoahFreeMemoryType test_set) const {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  if (_membership[idx] == test_set) {
    assert (test_set == NotFree || _free_set->alloc_capacity(idx) > 0, "Free regions must have alloc capacity");
    return true;
  } else {
    return false;
  }
}

inline size_t ShenandoahSetsOfFree::leftmost(ShenandoahFreeMemoryType which_set) const {
  assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
  size_t idx = _leftmosts[which_set];
  if (idx >= _max) {
    return _max;
  } else {
    assert (in_free_set(idx, which_set), "left-most region must be free");
    return idx;
  }
}

inline size_t ShenandoahSetsOfFree::rightmost(ShenandoahFreeMemoryType which_set) const {
  assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
  size_t idx = _rightmosts[which_set];
  assert ((_leftmosts[which_set] == _max) || in_free_set(idx, which_set), "right-most region must be free");
  return idx;
}

size_t ShenandoahSetsOfFree::leftmost_empty(ShenandoahFreeMemoryType which_set) {
  assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
  for (size_t idx = _leftmosts_empty[which_set]; idx < _max; idx++) {
    if ((membership(idx) == which_set) && (_free_set->alloc_capacity(idx) == _region_size_bytes)) {
      _leftmosts_empty[which_set] = idx;
      return idx;
    }
  }
  _leftmosts_empty[which_set] = _max;
  _rightmosts_empty[which_set] = 0;
  return _max;
}

inline size_t ShenandoahSetsOfFree::rightmost_empty(ShenandoahFreeMemoryType which_set) {
  assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
  for (intptr_t idx = _rightmosts_empty[which_set]; idx >= 0; idx--) {
    if ((membership(idx) == which_set) && (_free_set->alloc_capacity(idx) == _region_size_bytes)) {
      _rightmosts_empty[which_set] = idx;
      return idx;
    }
  }
  _leftmosts_empty[which_set] = _max;
  _rightmosts_empty[which_set] = 0;
  return 0;
}

inline bool ShenandoahSetsOfFree::alloc_from_left_bias(ShenandoahFreeMemoryType which_set) {
  assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
  return _left_to_right_bias[which_set];
}

void ShenandoahSetsOfFree::establish_alloc_bias(ShenandoahFreeMemoryType which_set) {
  ShenandoahHeap* heap = ShenandoahHeap::heap();
  shenandoah_assert_heaplocked();
  assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");

  size_t middle = (_leftmosts[which_set] + _rightmosts[which_set]) / 2;
  size_t available_in_first_half = 0;
  size_t available_in_second_half = 0;

  for (size_t index = _leftmosts[which_set]; index < middle; index++) {
    if (in_free_set(index, which_set)) {
      ShenandoahHeapRegion* r = heap->get_region(index);
      available_in_first_half += r->free();
    }
  }
  for (size_t index = middle; index <= _rightmosts[which_set]; index++) {
    if (in_free_set(index, which_set)) {
      ShenandoahHeapRegion* r = heap->get_region(index);
      available_in_second_half += r->free();
    }
  }

  // We desire to first consume the sparsely distributed regions in order that the remaining regions are densely packed.
  // Densely packing regions reduces the effort to search for a region that has sufficient memory to satisfy a new allocation
  // request.  Regions become sparsely distributed following a Full GC, which tends to slide all regions to the front of the
  // heap rather than allowing survivor regions to remain at the high end of the heap where we intend for them to congregate.

  // TODO: In the future, we may modify Full GC so that it slides old objects to the end of the heap and young objects to the
  // front of the heap. If this is done, we can always search survivor Collector and OldCollector regions right to left.
  _left_to_right_bias[which_set] = (available_in_second_half > available_in_first_half);
}

#ifdef ASSERT
void ShenandoahSetsOfFree::assert_bounds() {

  size_t leftmosts[NumFreeSets];
  size_t rightmosts[NumFreeSets];
  size_t empty_leftmosts[NumFreeSets];
  size_t empty_rightmosts[NumFreeSets];

  for (int i = 0; i < NumFreeSets; i++) {
    leftmosts[i] = _max;
    empty_leftmosts[i] = _max;
    rightmosts[i] = 0;
    empty_rightmosts[i] = 0;
  }

  for (size_t i = 0; i < _max; i++) {
    ShenandoahFreeMemoryType set = membership(i);
    switch (set) {
      case NotFree:
        break;

      case Mutator:
      case Collector:
      case OldCollector:
      {
        size_t capacity = _free_set->alloc_capacity(i);
        bool is_empty = (capacity == _region_size_bytes);
        assert(capacity > 0, "free regions must have allocation capacity");
        if (i < leftmosts[set]) {
          leftmosts[set] = i;
        }
        if (is_empty && (i < empty_leftmosts[set])) {
          empty_leftmosts[set] = i;
        }
        if (i > rightmosts[set]) {
          rightmosts[set] = i;
        }
        if (is_empty && (i > empty_rightmosts[set])) {
          empty_rightmosts[set] = i;
        }
        break;
      }

      case NumFreeSets:
      default:
        ShouldNotReachHere();
    }
  }

  // Performance invariants. Failing these would not break the free set, but performance would suffer.
  assert (leftmost(Mutator) <= _max, "leftmost in bounds: "  SIZE_FORMAT " < " SIZE_FORMAT, leftmost(Mutator),  _max);
  assert (rightmost(Mutator) < _max, "rightmost in bounds: "  SIZE_FORMAT " < " SIZE_FORMAT, rightmost(Mutator),  _max);

  assert (leftmost(Mutator) == _max || in_free_set(leftmost(Mutator), Mutator),
          "leftmost region should be free: " SIZE_FORMAT,  leftmost(Mutator));
  assert (leftmost(Mutator) == _max || in_free_set(rightmost(Mutator), Mutator),
          "rightmost region should be free: " SIZE_FORMAT, rightmost(Mutator));

  // If Mutator set is empty, leftmosts will both equal max, rightmosts will both equal zero.  Likewise for empty region sets.
  size_t beg_off = leftmosts[Mutator];
  size_t end_off = rightmosts[Mutator];
  assert (beg_off >= leftmost(Mutator),
          "free regions before the leftmost: " SIZE_FORMAT ", bound " SIZE_FORMAT, beg_off, leftmost(Mutator));
  assert (end_off <= rightmost(Mutator),
          "free regions past the rightmost: " SIZE_FORMAT ", bound " SIZE_FORMAT,  end_off, rightmost(Mutator));

  beg_off = empty_leftmosts[Mutator];
  end_off = empty_rightmosts[Mutator];
  assert (beg_off >= leftmost_empty(Mutator),
          "free empty regions before the leftmost: " SIZE_FORMAT ", bound " SIZE_FORMAT, beg_off, leftmost_empty(Mutator));
  assert (end_off <= rightmost_empty(Mutator),
          "free empty regions past the rightmost: " SIZE_FORMAT ", bound " SIZE_FORMAT,  end_off, rightmost_empty(Mutator));

  // Performance invariants. Failing these would not break the free set, but performance would suffer.
  assert (leftmost(Collector) <= _max, "leftmost in bounds: "  SIZE_FORMAT " < " SIZE_FORMAT, leftmost(Collector),  _max);
  assert (rightmost(Collector) < _max, "rightmost in bounds: "  SIZE_FORMAT " < " SIZE_FORMAT, rightmost(Collector),  _max);

  assert (leftmost(Collector) == _max || in_free_set(leftmost(Collector), Collector),
          "leftmost region should be free: " SIZE_FORMAT,  leftmost(Collector));
  assert (leftmost(Collector) == _max || in_free_set(rightmost(Collector), Collector),
          "rightmost region should be free: " SIZE_FORMAT, rightmost(Collector));

  // If Collector set is empty, leftmosts will both equal max, rightmosts will both equal zero.  Likewise for empty region sets.
  beg_off = leftmosts[Collector];
  end_off = rightmosts[Collector];
  assert (beg_off >= leftmost(Collector),
          "free regions before the leftmost: " SIZE_FORMAT ", bound " SIZE_FORMAT, beg_off, leftmost(Collector));
  assert (end_off <= rightmost(Collector),
          "free regions past the rightmost: " SIZE_FORMAT ", bound " SIZE_FORMAT,  end_off, rightmost(Collector));

  beg_off = empty_leftmosts[Collector];
  end_off = empty_rightmosts[Collector];
  assert (beg_off >= leftmost_empty(Collector),
          "free empty regions before the leftmost: " SIZE_FORMAT ", bound " SIZE_FORMAT, beg_off, leftmost_empty(Collector));
  assert (end_off <= rightmost_empty(Collector),
          "free empty regions past the rightmost: " SIZE_FORMAT ", bound " SIZE_FORMAT,  end_off, rightmost_empty(Collector));

  // Performance invariants. Failing these would not break the free set, but performance would suffer.
  assert (leftmost(OldCollector) <= _max, "leftmost in bounds: "  SIZE_FORMAT " < " SIZE_FORMAT, leftmost(OldCollector),  _max);
  assert (rightmost(OldCollector) < _max, "rightmost in bounds: "  SIZE_FORMAT " < " SIZE_FORMAT, rightmost(OldCollector),  _max);

  assert (leftmost(OldCollector) == _max || in_free_set(leftmost(OldCollector), OldCollector),
          "leftmost region should be free: " SIZE_FORMAT,  leftmost(OldCollector));
  assert (leftmost(OldCollector) == _max || in_free_set(rightmost(OldCollector), OldCollector),
          "rightmost region should be free: " SIZE_FORMAT, rightmost(OldCollector));

  // If OldCollector set is empty, leftmosts will both equal max, rightmosts will both equal zero.  Likewise for empty region sets.
  beg_off = leftmosts[OldCollector];
  end_off = rightmosts[OldCollector];
  assert (beg_off >= leftmost(OldCollector),
          "free regions before the leftmost: " SIZE_FORMAT ", bound " SIZE_FORMAT, beg_off, leftmost(OldCollector));
  assert (end_off <= rightmost(OldCollector),
          "free regions past the rightmost: " SIZE_FORMAT ", bound " SIZE_FORMAT,  end_off, rightmost(OldCollector));

  beg_off = empty_leftmosts[OldCollector];
  end_off = empty_rightmosts[OldCollector];
  assert (beg_off >= leftmost_empty(OldCollector),
          "free empty regions before the leftmost: " SIZE_FORMAT ", bound " SIZE_FORMAT, beg_off, leftmost_empty(OldCollector));
  assert (end_off <= rightmost_empty(OldCollector),
          "free empty regions past the rightmost: " SIZE_FORMAT ", bound " SIZE_FORMAT,  end_off, rightmost_empty(OldCollector));
}
#endif

ShenandoahFreeSet::ShenandoahFreeSet(ShenandoahHeap* heap, size_t max_regions) :
  _heap(heap),
  _free_sets(max_regions, this)
{
  clear_internal();
}

// This allocates from a region within the old_collector_set.  If affiliation equals OLD, the allocation must be taken
// from a region that is_old().  Otherwise, affiliation should be FREE, in which case this will put a previously unaffiliated
// region into service.
HeapWord* ShenandoahFreeSet::allocate_old_with_affiliation(ShenandoahAffiliation affiliation,
                                                           ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();

  size_t rightmost =
    (affiliation == ShenandoahAffiliation::FREE)? _free_sets.rightmost_empty(OldCollector): _free_sets.rightmost(OldCollector);
  size_t leftmost =
    (affiliation == ShenandoahAffiliation::FREE)? _free_sets.leftmost_empty(OldCollector): _free_sets.leftmost(OldCollector);
  if (_free_sets.alloc_from_left_bias(OldCollector)) {
    // This mode picks up stragglers left by a full GC
    for (size_t idx = leftmost; idx <= rightmost; idx++) {
      if (_free_sets.in_free_set(idx, OldCollector)) {
        ShenandoahHeapRegion* r = _heap->get_region(idx);
        assert(r->is_trash() || !r->is_affiliated() || r->is_old(), "old_collector_set region has bad affiliation");
        if (r->affiliation() == affiliation) {
          HeapWord* result = try_allocate_in(r, req, in_new_region);
          if (result != nullptr) {
            return result;
          }
        }
      }
    }
  } else {
    // This mode picks up stragglers left by a previous concurrent GC
    for (size_t count = rightmost + 1; count > leftmost; count--) {
      // size_t is unsigned, need to dodge underflow when _leftmost = 0
      size_t idx = count - 1;
      if (_free_sets.in_free_set(idx, OldCollector)) {
        ShenandoahHeapRegion* r = _heap->get_region(idx);
        assert(r->is_trash() || !r->is_affiliated() || r->is_old(), "old_collector_set region has bad affiliation");
        if (r->affiliation() == affiliation) {
          HeapWord* result = try_allocate_in(r, req, in_new_region);
          if (result != nullptr) {
            return result;
          }
        }
      }
    }
  }
  return nullptr;
}

void ShenandoahFreeSet::add_old_collector_free_region(ShenandoahHeapRegion* region) {
  shenandoah_assert_heaplocked();
  size_t idx = region->index();
  size_t capacity = alloc_capacity(region);
  assert(_free_sets.membership(idx) == NotFree, "Regions promoted in place should not be in any free set");
  if (capacity >= PLAB::min_size() * HeapWordSize) {
    _free_sets.make_free(idx, OldCollector, capacity);
    _heap->augment_promo_reserve(capacity);
  }
}

HeapWord* ShenandoahFreeSet::allocate_with_affiliation(ShenandoahAffiliation affiliation,
                                                       ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();
  size_t rightmost =
    (affiliation == ShenandoahAffiliation::FREE)? _free_sets.rightmost_empty(Collector): _free_sets.rightmost(Collector);
  size_t leftmost =
    (affiliation == ShenandoahAffiliation::FREE)? _free_sets.leftmost_empty(Collector): _free_sets.leftmost(Collector);
  for (size_t c = rightmost + 1; c > leftmost; c--) {
    // size_t is unsigned, need to dodge underflow when _leftmost = 0
    size_t idx = c - 1;
    if (_free_sets.in_free_set(idx, Collector)) {
      ShenandoahHeapRegion* r = _heap->get_region(idx);
      if (r->affiliation() == affiliation) {
        HeapWord* result = try_allocate_in(r, req, in_new_region);
        if (result != nullptr) {
          return result;
        }
      }
    }
  }
  log_debug(gc, free)("Could not allocate collector region with affiliation: %s for request " PTR_FORMAT,
                      shenandoah_affiliation_name(affiliation), p2i(&req));
  return nullptr;
}

HeapWord* ShenandoahFreeSet::allocate_single(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();

  // Scan the bitmap looking for a first fit.
  //
  // Leftmost and rightmost bounds provide enough caching to walk bitmap efficiently. Normally,
  // we would find the region to allocate at right away.
  //
  // Allocations are biased: new application allocs go to beginning of the heap, and GC allocs
  // go to the end. This makes application allocation faster, because we would clear lots
  // of regions from the beginning most of the time.
  //
  // Free set maintains mutator and collector views, and normally they allocate in their views only,
  // unless we special cases for stealing and mixed allocations.

  // Overwrite with non-zero (non-NULL) values only if necessary for allocation bookkeeping.

  bool allow_new_region = true;
  if (_heap->mode()->is_generational()) {
    switch (req.affiliation()) {
      case ShenandoahAffiliation::OLD_GENERATION:
        // Note: unsigned result from free_unaffiliated_regions() will never be less than zero, but it may equal zero.
        if (_heap->old_generation()->free_unaffiliated_regions() <= 0) {
          allow_new_region = false;
        }
        break;

      case ShenandoahAffiliation::YOUNG_GENERATION:
        // Note: unsigned result from free_unaffiliated_regions() will never be less than zero, but it may equal zero.
        if (_heap->young_generation()->free_unaffiliated_regions() <= 0) {
          allow_new_region = false;
        }
        break;

      case ShenandoahAffiliation::FREE:
        fatal("Should request affiliation");

      default:
        ShouldNotReachHere();
        break;
    }
  }
  switch (req.type()) {
    case ShenandoahAllocRequest::_alloc_tlab:
    case ShenandoahAllocRequest::_alloc_shared: {
      // Try to allocate in the mutator view
      for (size_t idx = _free_sets.leftmost(Mutator); idx <= _free_sets.rightmost(Mutator); idx++) {
        ShenandoahHeapRegion* r = _heap->get_region(idx);
        if (_free_sets.in_free_set(idx, Mutator) && (allow_new_region || r->is_affiliated())) {
          // try_allocate_in() increases used if the allocation is successful.
          HeapWord* result;
          size_t min_size = (req.type() == ShenandoahAllocRequest::_alloc_tlab)? req.min_size(): req.size();
          if ((alloc_capacity(r) >= min_size) && ((result = try_allocate_in(r, req, in_new_region)) != nullptr)) {
            return result;
          }
        }
      }
      // There is no recovery. Mutator does not touch collector view at all.
      break;
    }
    case ShenandoahAllocRequest::_alloc_gclab:
      // GCLABs are for evacuation so we must be in evacuation phase.  If this allocation is successful, increment
      // the relevant evac_expended rather than used value.

    case ShenandoahAllocRequest::_alloc_plab:
      // PLABs always reside in old-gen and are only allocated during evacuation phase.

    case ShenandoahAllocRequest::_alloc_shared_gc: {
      if (!_heap->mode()->is_generational()) {
        // size_t is unsigned, need to dodge underflow when _leftmost = 0
        // Fast-path: try to allocate in the collector view first
        for (size_t c = _free_sets.rightmost(Collector) + 1; c > _free_sets.leftmost(Collector); c--) {
          size_t idx = c - 1;
          if (_free_sets.in_free_set(idx, Collector)) {
            HeapWord* result = try_allocate_in(_heap->get_region(idx), req, in_new_region);
            if (result != nullptr) {
              return result;
            }
          }
        }
      } else {
        // First try to fit into a region that is already in use in the same generation.
        HeapWord* result;
        if (req.is_old()) {
          result = allocate_old_with_affiliation(req.affiliation(), req, in_new_region);
        } else {
          result = allocate_with_affiliation(req.affiliation(), req, in_new_region);
        }
        if (result != nullptr) {
          return result;
        }
        if (allow_new_region) {
          // Then try a free region that is dedicated to GC allocations.
          if (req.is_old()) {
            result = allocate_old_with_affiliation(FREE, req, in_new_region);
          } else {
            result = allocate_with_affiliation(FREE, req, in_new_region);
          }
          if (result != nullptr) {
            return result;
          }
        }
      }
      // No dice. Can we borrow space from mutator view?
      if (!ShenandoahEvacReserveOverflow) {
        return nullptr;
      }

      if (!allow_new_region && req.is_old() && (_heap->young_generation()->free_unaffiliated_regions() > 0)) {
        // This allows us to flip a mutator region to old_collector
        allow_new_region = true;
      }

      // We should expand old-gen if this can prevent an old-gen evacuation failure.  We don't care so much about
      // promotion failures since they can be mitigated in a subsequent GC pass.  Would be nice to know if this
      // allocation request is for evacuation or promotion.  Individual threads limit their use of PLAB memory for
      // promotions, so we already have an assurance that any additional memory set aside for old-gen will be used
      // only for old-gen evacuations.

      // Also TODO:
      // if (GC is idle (out of cycle) and mutator allocation fails and there is memory reserved in Collector
      // or OldCollector sets, transfer a region of memory so that we can satisfy the allocation request, and
      // immediately trigger the start of GC.  Is better to satisfy the allocation than to trigger out-of-cycle
      // allocation failure (even if this means we have a little less memory to handle evacuations during the
      // subsequent GC pass).

      if (allow_new_region) {
        // Try to steal an empty region from the mutator view.
        for (size_t c = _free_sets.rightmost_empty(Mutator) + 1; c > _free_sets.leftmost_empty(Mutator); c--) {
          size_t idx = c - 1;
          if (_free_sets.in_free_set(idx, Mutator)) {
            ShenandoahHeapRegion* r = _heap->get_region(idx);
            if (can_allocate_from(r)) {
              if (req.is_old()) {
                flip_to_old_gc(r);
              } else {
                flip_to_gc(r);
              }
              HeapWord *result = try_allocate_in(r, req, in_new_region);
              if (result != nullptr) {
                log_debug(gc, free)("Flipped region " SIZE_FORMAT " to gc for request: " PTR_FORMAT, idx, p2i(&req));
                return result;
              }
            }
          }
        }
      }

      // No dice. Do not try to mix mutator and GC allocations, because
      // URWM moves due to GC allocations would expose unparsable mutator
      // allocations.
      break;
    }
    default:
      ShouldNotReachHere();
  }
  return nullptr;
}

// This work method takes an argument corresponding to the number of bytes
// free in a region, and returns the largest amount in heapwords that can be allocated
// such that both of the following conditions are satisfied:
//
// 1. it is a multiple of card size
// 2. any remaining shard may be filled with a filler object
//
// The idea is that the allocation starts and ends at card boundaries. Because
// a region ('s end) is card-aligned, the remainder shard that must be filled is
// at the start of the free space.
//
// This is merely a helper method to use for the purpose of such a calculation.
size_t get_usable_free_words(size_t free_bytes) {
  // e.g. card_size is 512, card_shift is 9, min_fill_size() is 8
  //      free is 514
  //      usable_free is 512, which is decreased to 0
  size_t usable_free = (free_bytes / CardTable::card_size()) << CardTable::card_shift();
  assert(usable_free <= free_bytes, "Sanity check");
  if ((free_bytes != usable_free) && (free_bytes - usable_free < ShenandoahHeap::min_fill_size() * HeapWordSize)) {
    // After aligning to card multiples, the remainder would be smaller than
    // the minimum filler object, so we'll need to take away another card's
    // worth to construct a filler object.
    if (usable_free >= CardTable::card_size()) {
      usable_free -= CardTable::card_size();
    } else {
      assert(usable_free == 0, "usable_free is a multiple of card_size and card_size > min_fill_size");
    }
  }

  return usable_free / HeapWordSize;
}

// Given a size argument, which is a multiple of card size, a request struct
// for a PLAB, and an old region, return a pointer to the allocated space for
// a PLAB which is card-aligned and where any remaining shard in the region
// has been suitably filled by a filler object.
// It is assumed (and assertion-checked) that such an allocation is always possible.
HeapWord* ShenandoahFreeSet::allocate_aligned_plab(size_t size, ShenandoahAllocRequest& req, ShenandoahHeapRegion* r) {
  assert(_heap->mode()->is_generational(), "PLABs are only for generational mode");
  assert(r->is_old(), "All PLABs reside in old-gen");
  assert(!req.is_mutator_alloc(), "PLABs should not be allocated by mutators.");
  assert(size % CardTable::card_size_in_words() == 0, "size must be multiple of card table size, was " SIZE_FORMAT, size);

  HeapWord* result = r->allocate_aligned(size, req, CardTable::card_size());
  assert(result != nullptr, "Allocation cannot fail");
  assert(r->top() <= r->end(), "Allocation cannot span end of region");
  assert(req.actual_size() == size, "Should not have needed to adjust size for PLAB.");
  assert(((uintptr_t) result) % CardTable::card_size_in_words() == 0, "PLAB start must align with card boundary");

  return result;
}

HeapWord* ShenandoahFreeSet::try_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& in_new_region) {
  assert (has_alloc_capacity(r), "Performance: should avoid full regions on this path: " SIZE_FORMAT, r->index());
  if (_heap->is_concurrent_weak_root_in_progress() && r->is_trash()) {
    return nullptr;
  }

  try_recycle_trashed(r);
  if (!r->is_affiliated()) {
    ShenandoahMarkingContext* const ctx = _heap->complete_marking_context();
    r->set_affiliation(req.affiliation());
    if (r->is_old()) {
      // Any OLD region allocated during concurrent coalesce-and-fill does not need to be coalesced and filled because
      // all objects allocated within this region are above TAMS (and thus are implicitly marked).  In case this is an
      // OLD region and concurrent preparation for mixed evacuations visits this region before the start of the next
      // old-gen concurrent mark (i.e. this region is allocated following the start of old-gen concurrent mark but before
      // concurrent preparations for mixed evacuations are completed), we mark this region as not requiring any
      // coalesce-and-fill processing.
      r->end_preemptible_coalesce_and_fill();
      _heap->clear_cards_for(r);
      _heap->old_generation()->increment_affiliated_region_count();
    } else {
      _heap->young_generation()->increment_affiliated_region_count();
    }

    assert(ctx->top_at_mark_start(r) == r->bottom(), "Newly established allocation region starts with TAMS equal to bottom");
    assert(ctx->is_bitmap_clear_range(ctx->top_bitmap(r), r->end()), "Bitmap above top_bitmap() must be clear");
  } else if (r->affiliation() != req.affiliation()) {
    assert(_heap->mode()->is_generational(), "Request for %s from %s region should only happen in generational mode.",
           req.affiliation_name(), r->affiliation_name());
    return nullptr;
  }

  in_new_region = r->is_empty();
  HeapWord* result = nullptr;

  if (in_new_region) {
    log_debug(gc, free)("Using new region (" SIZE_FORMAT ") for %s (" PTR_FORMAT ").",
                       r->index(), ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(&req));
  }

  // req.size() is in words, r->free() is in bytes.
  if (ShenandoahElasticTLAB && req.is_lab_alloc()) {
    if (req.type() == ShenandoahAllocRequest::_alloc_plab) {
      assert(_heap->mode()->is_generational(), "PLABs are only for generational mode");
      assert(_free_sets.in_free_set(r->index(), OldCollector), "PLABS must be allocated in old_collector_free regions");
      // Need to assure that plabs are aligned on multiple of card region.
      // Since we have Elastic TLABs, align sizes up. They may be decreased to fit in the usable
      // memory remaining in the region (which will also be aligned to cards).
      size_t adjusted_size = align_up(req.size(), CardTable::card_size_in_words());
      size_t adjusted_min_size = align_up(req.min_size(), CardTable::card_size_in_words());
      size_t usable_free = get_usable_free_words(r->free());

      if (adjusted_size > usable_free) {
        adjusted_size = usable_free;
      }

      if (adjusted_size >= adjusted_min_size) {
        result = allocate_aligned_plab(adjusted_size, req, r);
      }
      // Otherwise, leave result == nullptr because the adjusted size is smaller than min size.
    } else {
      // This is a GCLAB or a TLAB allocation
      size_t adjusted_size = req.size();
      size_t free = align_down(r->free() >> LogHeapWordSize, MinObjAlignment);
      if (adjusted_size > free) {
        adjusted_size = free;
      }
      if (adjusted_size >= req.min_size()) {
        result = r->allocate(adjusted_size, req);
        assert (result != nullptr, "Allocation must succeed: free " SIZE_FORMAT ", actual " SIZE_FORMAT, free, adjusted_size);
        req.set_actual_size(adjusted_size);
      } else {
        log_trace(gc, free)("Failed to shrink TLAB or GCLAB request (" SIZE_FORMAT ") in region " SIZE_FORMAT " to " SIZE_FORMAT
                           " because min_size() is " SIZE_FORMAT, req.size(), r->index(), adjusted_size, req.min_size());
      }
    }
  } else if (req.is_lab_alloc() && req.type() == ShenandoahAllocRequest::_alloc_plab) {

    // inelastic PLAB
    size_t size = req.size();
    size_t usable_free = get_usable_free_words(r->free());
    if (size <= usable_free) {
      result = allocate_aligned_plab(size, req, r);
    }
  } else {
    size_t size = req.size();
    result = r->allocate(size, req);
    if (result != nullptr) {
      // Record actual allocation size
      req.set_actual_size(size);
    }
  }

  ShenandoahGeneration* generation = _heap->generation_for(req.affiliation());
  if (result != nullptr) {
    // Allocation successful, bump stats:
    if (req.is_mutator_alloc()) {
      assert(req.is_young(), "Mutator allocations always come from young generation.");
      _free_sets.increase_used(Mutator, req.actual_size() * HeapWordSize);
    } else {
      assert(req.is_gc_alloc(), "Should be gc_alloc since req wasn't mutator alloc");

      // For GC allocations, we advance update_watermark because the objects relocated into this memory during
      // evacuation are not updated during evacuation.  For both young and old regions r, it is essential that all
      // PLABs be made parsable at the end of evacuation.  This is enabled by retiring all plabs at end of evacuation.
      // TODO: Making a PLAB parsable involves placing a filler object in its remnant memory but does not require
      // that the PLAB be disabled for all future purposes.  We may want to introduce a new service to make the
      // PLABs parsable while still allowing the PLAB to serve future allocation requests that arise during the
      // next evacuation pass.
      r->set_update_watermark(r->top());
      if (r->is_old()) {
        assert(req.type() != ShenandoahAllocRequest::_alloc_gclab, "old-gen allocations use PLAB or shared allocation");
        // for plabs, we'll sort the difference between evac and promotion usage when we retire the plab
      }
    }
  }

  if (result == nullptr || alloc_capacity(r) < PLAB::min_size() * HeapWordSize) {
    // Region cannot afford this and is likely to not afford future allocations. Retire it.
    //
    // While this seems a bit harsh, especially in the case when this large allocation does not
    // fit but the next small one would, we are risking to inflate scan times when lots of
    // almost-full regions precede the fully-empty region where we want to allocate the entire TLAB.

    // Record the remainder as allocation waste
    size_t idx = r->index();
    if (req.is_mutator_alloc()) {
      size_t waste = r->free();
      if (waste > 0) {
        _free_sets.increase_used(Mutator, waste);
        // This one request could cause several regions to be "retired", so we must accumulate the waste
        req.set_waste((waste >> LogHeapWordSize) + req.waste());
      }
      assert(_free_sets.membership(idx) == Mutator, "Must be mutator free: " SIZE_FORMAT, idx);
    } else {
      assert(_free_sets.membership(idx) == Collector || _free_sets.membership(idx) == OldCollector,
             "Must be collector or old-collector free: " SIZE_FORMAT, idx);
    }
    // This region is no longer considered free (in any set)
    _free_sets.remove_from_free_sets(idx);
    _free_sets.assert_bounds();
  }
  return result;
}

HeapWord* ShenandoahFreeSet::allocate_contiguous(ShenandoahAllocRequest& req) {
  shenandoah_assert_heaplocked();

  size_t words_size = req.size();
  size_t num = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

  assert(req.is_young(), "Humongous regions always allocated in YOUNG");
  ShenandoahGeneration* generation = _heap->generation_for(req.affiliation());

  // Check if there are enough regions left to satisfy allocation.
  if (_heap->mode()->is_generational()) {
    size_t avail_young_regions = generation->free_unaffiliated_regions();
    if (num > _free_sets.count(Mutator) || (num > avail_young_regions)) {
      return nullptr;
    }
  } else {
    if (num > _free_sets.count(Mutator)) {
      return nullptr;
    }
  }

  // Find the continuous interval of $num regions, starting from $beg and ending in $end,
  // inclusive. Contiguous allocations are biased to the beginning.

  size_t beg = _free_sets.leftmost(Mutator);
  size_t end = beg;

  while (true) {
    if (end >= _free_sets.max()) {
      // Hit the end, goodbye
      return nullptr;
    }

    // If regions are not adjacent, then current [beg; end] is useless, and we may fast-forward.
    // If region is not completely free, the current [beg; end] is useless, and we may fast-forward.
    if (!_free_sets.in_free_set(end, Mutator) || !can_allocate_from(_heap->get_region(end))) {
      end++;
      beg = end;
      continue;
    }

    if ((end - beg + 1) == num) {
      // found the match
      break;
    }

    end++;
  }

  size_t remainder = words_size & ShenandoahHeapRegion::region_size_words_mask();
  ShenandoahMarkingContext* const ctx = _heap->complete_marking_context();

  // Initialize regions:
  for (size_t i = beg; i <= end; i++) {
    ShenandoahHeapRegion* r = _heap->get_region(i);
    try_recycle_trashed(r);

    assert(i == beg || _heap->get_region(i - 1)->index() + 1 == r->index(), "Should be contiguous");
    assert(r->is_empty(), "Should be empty");

    if (i == beg) {
      r->make_humongous_start();
    } else {
      r->make_humongous_cont();
    }

    // Trailing region may be non-full, record the remainder there
    size_t used_words;
    if ((i == end) && (remainder != 0)) {
      used_words = remainder;
    } else {
      used_words = ShenandoahHeapRegion::region_size_words();
    }

    r->set_affiliation(req.affiliation());
    r->set_update_watermark(r->bottom());
    r->set_top(r->bottom() + used_words);

    // While individual regions report their true use, all humongous regions are marked used in the free set.
    _free_sets.remove_from_free_sets(r->index());
  }
  _heap->young_generation()->increase_affiliated_region_count(num);

  size_t total_humongous_size = ShenandoahHeapRegion::region_size_bytes() * num;
  _free_sets.increase_used(Mutator, total_humongous_size);
  _free_sets.assert_bounds();
  req.set_actual_size(words_size);
  if (remainder != 0) {
    req.set_waste(ShenandoahHeapRegion::region_size_words() - remainder);
  }
  return _heap->get_region(beg)->bottom();
}

// Returns true iff this region is entirely available, either because it is empty() or because it has been found to represent
// immediate trash and we'll be able to immediately recycle it.  Note that we cannot recycle immediate trash if
// concurrent weak root processing is in progress.
bool ShenandoahFreeSet::can_allocate_from(ShenandoahHeapRegion *r) const {
  return r->is_empty() || (r->is_trash() && !_heap->is_concurrent_weak_root_in_progress());
}

bool ShenandoahFreeSet::can_allocate_from(size_t idx) const {
  ShenandoahHeapRegion* r = _heap->get_region(idx);
  return can_allocate_from(r);
}

size_t ShenandoahFreeSet::alloc_capacity(size_t idx) const {
  ShenandoahHeapRegion* r = _heap->get_region(idx);
  return alloc_capacity(r);
}

size_t ShenandoahFreeSet::alloc_capacity(ShenandoahHeapRegion *r) const {
  if (r->is_trash()) {
    // This would be recycled on allocation path
    return ShenandoahHeapRegion::region_size_bytes();
  } else {
    return r->free();
  }
}

bool ShenandoahFreeSet::has_alloc_capacity(ShenandoahHeapRegion *r) const {
  return alloc_capacity(r) > 0;
}

void ShenandoahFreeSet::try_recycle_trashed(ShenandoahHeapRegion *r) {
  if (r->is_trash()) {
    r->recycle();
  }
}

void ShenandoahFreeSet::recycle_trash() {
  // lock is not reentrable, check we don't have it
  shenandoah_assert_not_heaplocked();

  for (size_t i = 0; i < _heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = _heap->get_region(i);
    if (r->is_trash()) {
      ShenandoahHeapLocker locker(_heap->lock());
      try_recycle_trashed(r);
    }
    SpinPause(); // allow allocators to take the lock
  }
}

void ShenandoahFreeSet::flip_to_old_gc(ShenandoahHeapRegion* r) {
  size_t idx = r->index();

  assert(_free_sets.in_free_set(idx, Mutator), "Should be in mutator view");
  // Note: can_allocate_from(r) means r is entirely empty
  assert(can_allocate_from(r), "Should not be allocated");

  size_t region_capacity = alloc_capacity(r);
  _free_sets.move_to_set(idx, OldCollector, region_capacity);
  _free_sets.assert_bounds();
  _heap->augment_old_evac_reserve(region_capacity);
  bool transferred = _heap->generation_sizer()->transfer_to_old(1);
  if (!transferred) {
    log_warning(gc, free)("Forcing transfer of " SIZE_FORMAT " to old reserve.", idx);
    _heap->generation_sizer()->force_transfer_to_old(1);
  }
  // We do not ensure that the region is no longer trash, relying on try_allocate_in(), which always comes next,
  // to recycle trash before attempting to allocate anything in the region.
}

void ShenandoahFreeSet::flip_to_gc(ShenandoahHeapRegion* r) {
  size_t idx = r->index();

  assert(_free_sets.in_free_set(idx, Mutator), "Should be in mutator view");
  assert(can_allocate_from(r), "Should not be allocated");

  size_t region_capacity = alloc_capacity(r);
  _free_sets.move_to_set(idx, Collector, region_capacity);
  _free_sets.assert_bounds();

  // We do not ensure that the region is no longer trash, relying on try_allocate_in(), which always comes next,
  // to recycle trash before attempting to allocate anything in the region.
}

void ShenandoahFreeSet::clear() {
  shenandoah_assert_heaplocked();
  clear_internal();
}

void ShenandoahFreeSet::clear_internal() {
  _free_sets.clear_all();
}

// This function places all is_old() regions that have allocation capacity into the old_collector set.  It places
// all other regions (not is_old()) that have allocation capacity into the mutator_set.  Subsequently, we will
// move some of the mutator regions into the collector set or old_collector set with the intent of packing
// old_collector memory into the highest (rightmost) addresses of the heap and the collector memory into the
// next highest addresses of the heap, with mutator memory consuming the lowest addresses of the heap.
void ShenandoahFreeSet::find_regions_with_alloc_capacity(size_t &young_cset_regions, size_t &old_cset_regions) {

  old_cset_regions = 0;
  young_cset_regions = 0;
  for (size_t idx = 0; idx < _heap->num_regions(); idx++) {
    ShenandoahHeapRegion* region = _heap->get_region(idx);
    if (region->is_trash()) {
      // Trashed regions represent regions that had been in the collection set but have not yet been "cleaned up".
      if (region->is_old()) {
        old_cset_regions++;
      } else {
        assert(region->is_young(), "Trashed region should be old or young");
        young_cset_regions++;
      }
    }
    if (region->is_alloc_allowed() || region->is_trash()) {
      assert(!region->is_cset(), "Shouldn't be adding cset regions to the free set");
      assert(_free_sets.in_free_set(idx, NotFree), "We are about to make region free; it should not be free already");

      // Do not add regions that would almost surely fail allocation
      if (alloc_capacity(region) < PLAB::min_size() * HeapWordSize) continue;

      if (region->is_old()) {
        _free_sets.make_free(idx, OldCollector, alloc_capacity(region));
        log_debug(gc, free)(
          "  Adding Region " SIZE_FORMAT  " (Free: " SIZE_FORMAT "%s, Used: " SIZE_FORMAT "%s) to old collector set",
          idx, byte_size_in_proper_unit(region->free()), proper_unit_for_byte_size(region->free()),
          byte_size_in_proper_unit(region->used()), proper_unit_for_byte_size(region->used()));
      } else {
        _free_sets.make_free(idx, Mutator, alloc_capacity(region));
        log_debug(gc, free)(
          "  Adding Region " SIZE_FORMAT " (Free: " SIZE_FORMAT "%s, Used: " SIZE_FORMAT "%s) to mutator set",
          idx, byte_size_in_proper_unit(region->free()), proper_unit_for_byte_size(region->free()),
          byte_size_in_proper_unit(region->used()), proper_unit_for_byte_size(region->used()));
      }
    }
  }
}

// Move no more than cset_regions from the existing Collector and OldCollector free sets to the Mutator free set.
// This is called from outside the heap lock.
void ShenandoahFreeSet::move_collector_sets_to_mutator(size_t max_xfer_regions) {
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t collector_empty_xfer = 0;
  size_t collector_not_empty_xfer = 0;
  size_t old_collector_empty_xfer = 0;

  // Process empty regions within the Collector free set
  if ((max_xfer_regions > 0) && (_free_sets.leftmost_empty(Collector) <= _free_sets.rightmost_empty(Collector))) {
    ShenandoahHeapLocker locker(_heap->lock());
    for (size_t idx = _free_sets.leftmost_empty(Collector);
         (max_xfer_regions > 0) && (idx <= _free_sets.rightmost_empty(Collector)); idx++) {
      if (_free_sets.in_free_set(idx, Collector) && can_allocate_from(idx)) {
        _free_sets.move_to_set(idx, Mutator, region_size_bytes);
        max_xfer_regions--;
        collector_empty_xfer += region_size_bytes;
      }
    }
  }

  // Process empty regions within the OldCollector free set
  size_t old_collector_regions = 0;
  if ((max_xfer_regions > 0) && (_free_sets.leftmost_empty(OldCollector) <= _free_sets.rightmost_empty(OldCollector))) {
    ShenandoahHeapLocker locker(_heap->lock());
    for (size_t idx = _free_sets.leftmost_empty(OldCollector);
         (max_xfer_regions > 0) && (idx <= _free_sets.rightmost_empty(OldCollector)); idx++) {
      if (_free_sets.in_free_set(idx, OldCollector) && can_allocate_from(idx)) {
        _free_sets.move_to_set(idx, Mutator, region_size_bytes);
        max_xfer_regions--;
        old_collector_empty_xfer += region_size_bytes;
        old_collector_regions++;
      }
    }
    if (old_collector_regions > 0) {
      _heap->generation_sizer()->transfer_to_young(old_collector_regions);
    }
  }

  // If there are any non-empty regions within Collector set, we can also move them to the Mutator free set
  if ((max_xfer_regions > 0) && (_free_sets.leftmost(Collector) <= _free_sets.rightmost(Collector))) {
    ShenandoahHeapLocker locker(_heap->lock());
    for (size_t idx = _free_sets.leftmost(Collector); (max_xfer_regions > 0) && (idx <= _free_sets.rightmost(Collector)); idx++) {
      size_t alloc_capacity = this->alloc_capacity(idx);
      if (_free_sets.in_free_set(idx, Collector) && (alloc_capacity > 0)) {
        _free_sets.move_to_set(idx, Mutator, alloc_capacity);
        max_xfer_regions--;
        collector_not_empty_xfer += alloc_capacity;
      }
    }
  }

  size_t collector_xfer = collector_empty_xfer + collector_not_empty_xfer;
  size_t total_xfer = collector_xfer + old_collector_empty_xfer;
  log_info(gc, free)("At start of update refs, moving " SIZE_FORMAT "%s to Mutator free set from Collector Reserve ("
                     SIZE_FORMAT "%s) and from Old Collector Reserve (" SIZE_FORMAT "%s)",
                     byte_size_in_proper_unit(total_xfer), proper_unit_for_byte_size(total_xfer),
                     byte_size_in_proper_unit(collector_xfer), proper_unit_for_byte_size(collector_xfer),
                     byte_size_in_proper_unit(old_collector_empty_xfer), proper_unit_for_byte_size(old_collector_empty_xfer));
}


// Overwrite arguments to represent the amount of memory in each generation that is about to be recycled
void ShenandoahFreeSet::prepare_to_rebuild(size_t &young_cset_regions, size_t &old_cset_regions) {
  shenandoah_assert_heaplocked();
  // This resets all state information, removing all regions from all sets.
  clear();
  log_debug(gc, free)("Rebuilding FreeSet");

  // This places regions that have alloc_capacity into the old_collector set if they identify as is_old() or the
  // mutator set otherwise.
  find_regions_with_alloc_capacity(young_cset_regions, old_cset_regions);
}

void ShenandoahFreeSet::rebuild(size_t young_cset_regions, size_t old_cset_regions) {
  shenandoah_assert_heaplocked();
  size_t young_reserve, old_reserve;
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();

  size_t old_capacity = _heap->old_generation()->max_capacity();
  size_t old_available = _heap->old_generation()->available();
  size_t old_unaffiliated_regions = _heap->old_generation()->free_unaffiliated_regions();
  size_t young_capacity = _heap->young_generation()->max_capacity();
  size_t young_available = _heap->young_generation()->available();
  size_t young_unaffiliated_regions = _heap->young_generation()->free_unaffiliated_regions();

  old_unaffiliated_regions += old_cset_regions;
  old_available += old_cset_regions * region_size_bytes;
  young_unaffiliated_regions += young_cset_regions;
  young_available += young_cset_regions * region_size_bytes;

  // Consult old-region surplus and deficit to make adjustments to current generation capacities and availability.
  // The generation region transfers take place after we rebuild.
  size_t old_region_surplus = _heap->get_old_region_surplus();
  size_t old_region_deficit = _heap->get_old_region_deficit();

  if (old_region_surplus > 0) {
    size_t xfer_bytes = old_region_surplus * region_size_bytes;
    assert(old_region_surplus <= old_unaffiliated_regions, "Cannot transfer regions that are affiliated");
    old_capacity -= xfer_bytes;
    old_available -= xfer_bytes;
    old_unaffiliated_regions -= old_region_surplus;
    young_capacity += xfer_bytes;
    young_available += xfer_bytes;
    young_unaffiliated_regions += old_region_surplus;
  } else if (old_region_deficit > 0) {
    size_t xfer_bytes = old_region_deficit * region_size_bytes;
    assert(old_region_deficit <= young_unaffiliated_regions, "Cannot transfer regions that are affiliated");
    old_capacity += xfer_bytes;
    old_available += xfer_bytes;
    old_unaffiliated_regions += old_region_deficit;
    young_capacity -= xfer_bytes;
    young_available -= xfer_bytes;
    young_unaffiliated_regions -= old_region_deficit;
  }

  // Evac reserve: reserve trailing space for evacuations, with regions reserved for old evacuations placed to the right
  // of regions reserved of young evacuations.
  if (!_heap->mode()->is_generational()) {
    young_reserve = (_heap->max_capacity() / 100) * ShenandoahEvacReserve;
    old_reserve = 0;
  } else {
    // All allocations taken from the old collector set are performed by GC, generally using PLABs for both
    // promotions and evacuations.  The partition between which old memory is reserved for evacuation and
    // which is reserved for promotion is enforced using thread-local variables that prescribe intentons for
    // each PLAB's available memory.
    if (_heap->has_evacuation_reserve_quantities()) {
      // We are rebuilding at the end of final mark, having already established evacuation budgets for this GC pass.
      young_reserve = _heap->get_young_evac_reserve();
      old_reserve = _heap->get_promoted_reserve() + _heap->get_old_evac_reserve();
      assert(old_reserve <= old_available,
             "Cannot reserve (" SIZE_FORMAT " + " SIZE_FORMAT") more OLD than is available: " SIZE_FORMAT,
             _heap->get_promoted_reserve(), _heap->get_old_evac_reserve(), old_available);
    } else {
      // We are rebuilding at end of GC, so we set aside budgets specified on command line (or defaults)
      young_reserve = (young_capacity * ShenandoahEvacReserve) / 100;
      // The auto-sizer has already made old-gen large enough to hold all anticipated evacuations and promotions.
      // Affiliated old-gen regions are already in the OldCollector free set.  Add in the relevant number of
      // unaffiliated regions.
      old_reserve = old_available;
    }
  }

  // Old available regions that have less than PLAB::min_size() of available memory are not placed into the OldCollector
  // free set.  Because of this, old_available may not have enough memory to represent the intended reserve.  Adjust
  // the reserve downward to account for this possibility. This loss is part of the reason why the original budget
  // was adjusted with ShenandoahOldEvacWaste and ShenandoahOldPromoWaste multipliers.
  if (old_reserve > _free_sets.capacity_of(OldCollector) + old_unaffiliated_regions * region_size_bytes) {
    old_reserve = _free_sets.capacity_of(OldCollector) + old_unaffiliated_regions * region_size_bytes;
  }

  if (young_reserve > young_unaffiliated_regions * region_size_bytes) {
    young_reserve = young_unaffiliated_regions * region_size_bytes;
  }

  reserve_regions(young_reserve, old_reserve);
  _free_sets.establish_alloc_bias(OldCollector);
  _free_sets.assert_bounds();
  log_status();
}

// Having placed all regions that have allocation capacity into the mutator set if they identify as is_young()
// or into the old collector set if they identify as is_old(), move some of these regions from the mutator set
// into the collector set or old collector set in order to assure that the memory available for allocations within
// the collector set is at least to_reserve, and the memory available for allocations within the old collector set
// is at least to_reserve_old.
void ShenandoahFreeSet::reserve_regions(size_t to_reserve, size_t to_reserve_old) {
  for (size_t i = _heap->num_regions(); i > 0; i--) {
    size_t idx = i - 1;
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    if (!_free_sets.in_free_set(idx, Mutator)) {
      continue;
    }

    size_t ac = alloc_capacity(r);
    assert (ac > 0, "Membership in free set implies has capacity");
    assert (!r->is_old(), "mutator_is_free regions should not be affiliated OLD");

    bool move_to_old = _free_sets.capacity_of(OldCollector) < to_reserve_old;
    bool move_to_young = _free_sets.capacity_of(Collector) < to_reserve;

    if (!move_to_old && !move_to_young) {
      // We've satisfied both to_reserve and to_reserved_old
      break;
    }

    if (move_to_old) {
      if (r->is_trash() || !r->is_affiliated()) {
        // OLD regions that have available memory are already in the old_collector free set
        _free_sets.move_to_set(idx, OldCollector, ac);
        log_debug(gc, free)("  Shifting region " SIZE_FORMAT " from mutator_free to old_collector_free", idx);
        continue;
      }
    }

    if (move_to_young) {
      // Note: In a previous implementation, regions were only placed into the survivor space (collector_is_free) if
      // they were entirely empty.  I'm not sure I understand the rationale for that.  That alternative behavior would
      // tend to mix survivor objects with ephemeral objects, making it more difficult to reclaim the memory for the
      // ephemeral objects.  It also delays aging of regions, causing promotion in place to be delayed.
      _free_sets.move_to_set(idx, Collector, ac);
      log_debug(gc)("  Shifting region " SIZE_FORMAT " from mutator_free to collector_free", idx);
    }
  }

  if (LogTarget(Info, gc, free)::is_enabled()) {
    size_t old_reserve = _free_sets.capacity_of(OldCollector);
    if (old_reserve < to_reserve_old) {
      log_info(gc, free)("Wanted " PROPERFMT " for old reserve, but only reserved: " PROPERFMT,
                         PROPERFMTARGS(to_reserve_old), PROPERFMTARGS(old_reserve));
    }
    size_t young_reserve = _free_sets.capacity_of(Collector);
    if (young_reserve < to_reserve) {
      log_info(gc, free)("Wanted " PROPERFMT " for young reserve, but only reserved: " PROPERFMT,
                         PROPERFMTARGS(to_reserve), PROPERFMTARGS(young_reserve));
    }
  }
}

void ShenandoahFreeSet::log_status() {
  shenandoah_assert_heaplocked();

#ifdef ASSERT
  // Dump of the FreeSet details is only enabled if assertions are enabled
  if (LogTarget(Debug, gc, free)::is_enabled()) {
#define BUFFER_SIZE 80
    size_t retired_old = 0;
    size_t retired_old_humongous = 0;
    size_t retired_young = 0;
    size_t retired_young_humongous = 0;
    size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
    size_t retired_young_waste = 0;
    size_t retired_old_waste = 0;
    size_t consumed_collector = 0;
    size_t consumed_old_collector = 0;
    size_t consumed_mutator = 0;
    size_t available_old = 0;
    size_t available_young = 0;
    size_t available_mutator = 0;
    size_t available_collector = 0;
    size_t available_old_collector = 0;

    char buffer[BUFFER_SIZE];
    for (uint i = 0; i < BUFFER_SIZE; i++) {
      buffer[i] = '\0';
    }
    log_debug(gc, free)("FreeSet map legend:"
                       " M:mutator_free C:collector_free O:old_collector_free"
                       " H:humongous ~:retired old _:retired young");
    log_debug(gc, free)(" mutator free range [" SIZE_FORMAT ".." SIZE_FORMAT "], "
                       " collector free range [" SIZE_FORMAT ".." SIZE_FORMAT "], "
                       "old collector free range [" SIZE_FORMAT ".." SIZE_FORMAT "] allocates from %s",
                       _free_sets.leftmost(Mutator), _free_sets.rightmost(Mutator),
                       _free_sets.leftmost(Collector), _free_sets.rightmost(Collector),
                       _free_sets.leftmost(OldCollector), _free_sets.rightmost(OldCollector),
                       _free_sets.alloc_from_left_bias(OldCollector)? "left to right": "right to left");

    for (uint i = 0; i < _heap->num_regions(); i++) {
      ShenandoahHeapRegion *r = _heap->get_region(i);
      uint idx = i % 64;
      if ((i != 0) && (idx == 0)) {
        log_debug(gc, free)(" %6u: %s", i-64, buffer);
      }
      if (_free_sets.in_free_set(i, Mutator)) {
        assert(!r->is_old(), "Old regions should not be in mutator_free set");
        size_t capacity = alloc_capacity(r);
        available_mutator += capacity;
        consumed_mutator += region_size_bytes - capacity;
        buffer[idx] = (capacity == region_size_bytes)? 'M': 'm';
      } else if (_free_sets.in_free_set(i, Collector)) {
        assert(!r->is_old(), "Old regions should not be in collector_free set");
        size_t capacity = alloc_capacity(r);
        available_collector += capacity;
        consumed_collector += region_size_bytes - capacity;
        buffer[idx] = (capacity == region_size_bytes)? 'C': 'c';
      } else if (_free_sets.in_free_set(i, OldCollector)) {
        size_t capacity = alloc_capacity(r);
        available_old_collector += capacity;
        consumed_old_collector += region_size_bytes - capacity;
        buffer[idx] = (capacity == region_size_bytes)? 'O': 'o';
      } else if (r->is_humongous()) {
        if (r->is_old()) {
          buffer[idx] = 'H';
          retired_old_humongous += region_size_bytes;
        } else {
          buffer[idx] = 'h';
          retired_young_humongous += region_size_bytes;
        }
      } else {
        if (r->is_old()) {
          buffer[idx] = '~';
          retired_old_waste += alloc_capacity(r);
          retired_old += region_size_bytes;
        } else {
          buffer[idx] = '_';
          retired_young_waste += alloc_capacity(r);
          retired_young += region_size_bytes;
        }
      }
    }
    uint remnant = _heap->num_regions() % 64;
    if (remnant > 0) {
      buffer[remnant] = '\0';
    } else {
      remnant = 64;
    }
    log_debug(gc, free)(" %6u: %s", (uint) (_heap->num_regions() - remnant), buffer);
    size_t total_young = retired_young + retired_young_humongous;
    size_t total_old = retired_old + retired_old_humongous;
  }
#endif

  LogTarget(Info, gc, free) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);

    {
      size_t last_idx = 0;
      size_t max = 0;
      size_t max_contig = 0;
      size_t empty_contig = 0;

      size_t total_used = 0;
      size_t total_free = 0;
      size_t total_free_ext = 0;

      for (size_t idx = _free_sets.leftmost(Mutator); idx <= _free_sets.rightmost(Mutator); idx++) {
        if (_free_sets.in_free_set(idx, Mutator)) {
          ShenandoahHeapRegion *r = _heap->get_region(idx);
          size_t free = alloc_capacity(r);
          max = MAX2(max, free);
          if (r->is_empty()) {
            total_free_ext += free;
            if (last_idx + 1 == idx) {
              empty_contig++;
            } else {
              empty_contig = 1;
            }
          } else {
            empty_contig = 0;
          }
          total_used += r->used();
          total_free += free;
          max_contig = MAX2(max_contig, empty_contig);
          last_idx = idx;
        }
      }

      size_t max_humongous = max_contig * ShenandoahHeapRegion::region_size_bytes();
      size_t free = capacity() - used();

      assert(free == total_free, "Sum of free within mutator regions (" SIZE_FORMAT
             ") should match mutator capacity (" SIZE_FORMAT ") minus mutator used (" SIZE_FORMAT ")",
             total_free, capacity(), used());

      ls.print("Free: " SIZE_FORMAT "%s, Max: " SIZE_FORMAT "%s regular, " SIZE_FORMAT "%s humongous, ",
               byte_size_in_proper_unit(total_free),    proper_unit_for_byte_size(total_free),
               byte_size_in_proper_unit(max),           proper_unit_for_byte_size(max),
               byte_size_in_proper_unit(max_humongous), proper_unit_for_byte_size(max_humongous)
      );

      ls.print("Frag: ");
      size_t frag_ext;
      if (total_free_ext > 0) {
        frag_ext = 100 - (100 * max_humongous / total_free_ext);
      } else {
        frag_ext = 0;
      }
      ls.print(SIZE_FORMAT "%% external, ", frag_ext);

      size_t frag_int;
      if (_free_sets.count(Mutator) > 0) {
        frag_int = (100 * (total_used / _free_sets.count(Mutator)) / ShenandoahHeapRegion::region_size_bytes());
      } else {
        frag_int = 0;
      }
      ls.print(SIZE_FORMAT "%% internal; ", frag_int);
      ls.print("Used: " SIZE_FORMAT "%s, Mutator Free: " SIZE_FORMAT,
               byte_size_in_proper_unit(total_used), proper_unit_for_byte_size(total_used), _free_sets.count(Mutator));
    }

    {
      size_t max = 0;
      size_t total_free = 0;
      size_t total_used = 0;

      for (size_t idx = _free_sets.leftmost(Collector); idx <= _free_sets.rightmost(Collector); idx++) {
        if (_free_sets.in_free_set(idx, Collector)) {
          ShenandoahHeapRegion *r = _heap->get_region(idx);
          size_t free = alloc_capacity(r);
          max = MAX2(max, free);
          total_free += free;
          total_used += r->used();
        }
      }
      ls.print(" Collector Reserve: " SIZE_FORMAT "%s, Max: " SIZE_FORMAT "%s; Used: " SIZE_FORMAT "%s",
               byte_size_in_proper_unit(total_free), proper_unit_for_byte_size(total_free),
               byte_size_in_proper_unit(max),        proper_unit_for_byte_size(max),
               byte_size_in_proper_unit(total_used), proper_unit_for_byte_size(total_used));
    }

    if (_heap->mode()->is_generational()) {
      size_t max = 0;
      size_t total_free = 0;
      size_t total_used = 0;

      for (size_t idx = _free_sets.leftmost(OldCollector); idx <= _free_sets.rightmost(OldCollector); idx++) {
        if (_free_sets.in_free_set(idx, OldCollector)) {
          ShenandoahHeapRegion *r = _heap->get_region(idx);
          size_t free = alloc_capacity(r);
          max = MAX2(max, free);
          total_free += free;
          total_used += r->used();
        }
      }
      ls.print_cr(" Old Collector Reserve: " SIZE_FORMAT "%s, Max: " SIZE_FORMAT "%s; Used: " SIZE_FORMAT "%s",
                  byte_size_in_proper_unit(total_free), proper_unit_for_byte_size(total_free),
                  byte_size_in_proper_unit(max),        proper_unit_for_byte_size(max),
                  byte_size_in_proper_unit(total_used), proper_unit_for_byte_size(total_used));
    }
  }
}

HeapWord* ShenandoahFreeSet::allocate(ShenandoahAllocRequest& req, bool& in_new_region) {
  shenandoah_assert_heaplocked();

  // Allocation request is known to satisfy all memory budgeting constraints.
  if (req.size() > ShenandoahHeapRegion::humongous_threshold_words()) {
    switch (req.type()) {
      case ShenandoahAllocRequest::_alloc_shared:
      case ShenandoahAllocRequest::_alloc_shared_gc:
        in_new_region = true;
        return allocate_contiguous(req);
      case ShenandoahAllocRequest::_alloc_plab:
      case ShenandoahAllocRequest::_alloc_gclab:
      case ShenandoahAllocRequest::_alloc_tlab:
        in_new_region = false;
        assert(false, "Trying to allocate TLAB larger than the humongous threshold: " SIZE_FORMAT " > " SIZE_FORMAT,
               req.size(), ShenandoahHeapRegion::humongous_threshold_words());
        return nullptr;
      default:
        ShouldNotReachHere();
        return nullptr;
    }
  } else {
    return allocate_single(req, in_new_region);
  }
}

size_t ShenandoahFreeSet::unsafe_peek_free() const {
  // Deliberately not locked, this method is unsafe when free set is modified.

  for (size_t index = _free_sets.leftmost(Mutator); index <= _free_sets.rightmost(Mutator); index++) {
    if (index < _free_sets.max() && _free_sets.in_free_set(index, Mutator)) {
      ShenandoahHeapRegion* r = _heap->get_region(index);
      if (r->free() >= MinTLABSize) {
        return r->free();
      }
    }
  }

  // It appears that no regions left
  return 0;
}

void ShenandoahFreeSet::print_on(outputStream* out) const {
  out->print_cr("Mutator Free Set: " SIZE_FORMAT "", _free_sets.count(Mutator));
  for (size_t index = _free_sets.leftmost(Mutator); index <= _free_sets.rightmost(Mutator); index++) {
    if (_free_sets.in_free_set(index, Mutator)) {
      _heap->get_region(index)->print_on(out);
    }
  }
  out->print_cr("Collector Free Set: " SIZE_FORMAT "", _free_sets.count(Collector));
  for (size_t index = _free_sets.leftmost(Collector); index <= _free_sets.rightmost(Collector); index++) {
    if (_free_sets.in_free_set(index, Collector)) {
      _heap->get_region(index)->print_on(out);
    }
  }
  if (_heap->mode()->is_generational()) {
    out->print_cr("Old Collector Free Set: " SIZE_FORMAT "", _free_sets.count(OldCollector));
    for (size_t index = _free_sets.leftmost(OldCollector); index <= _free_sets.rightmost(OldCollector); index++) {
      if (_free_sets.in_free_set(index, OldCollector)) {
        _heap->get_region(index)->print_on(out);
      }
    }
  }
}

/*
 * Internal fragmentation metric: describes how fragmented the heap regions are.
 *
 * It is derived as:
 *
 *               sum(used[i]^2, i=0..k)
 *   IF = 1 - ------------------------------
 *              C * sum(used[i], i=0..k)
 *
 * ...where k is the number of regions in computation, C is the region capacity, and
 * used[i] is the used space in the region.
 *
 * The non-linearity causes IF to be lower for the cases where the same total heap
 * used is densely packed. For example:
 *   a) Heap is completely full  => IF = 0
 *   b) Heap is half full, first 50% regions are completely full => IF = 0
 *   c) Heap is half full, each region is 50% full => IF = 1/2
 *   d) Heap is quarter full, first 50% regions are completely full => IF = 0
 *   e) Heap is quarter full, each region is 25% full => IF = 3/4
 *   f) Heap has one small object per each region => IF =~ 1
 */
double ShenandoahFreeSet::internal_fragmentation() {
  double squared = 0;
  double linear = 0;
  int count = 0;

  for (size_t index = _free_sets.leftmost(Mutator); index <= _free_sets.rightmost(Mutator); index++) {
    if (_free_sets.in_free_set(index, Mutator)) {
      ShenandoahHeapRegion* r = _heap->get_region(index);
      size_t used = r->used();
      squared += used * used;
      linear += used;
      count++;
    }
  }

  if (count > 0) {
    double s = squared / (ShenandoahHeapRegion::region_size_bytes() * linear);
    return 1 - s;
  } else {
    return 0;
  }
}

/*
 * External fragmentation metric: describes how fragmented the heap is.
 *
 * It is derived as:
 *
 *   EF = 1 - largest_contiguous_free / total_free
 *
 * For example:
 *   a) Heap is completely empty => EF = 0
 *   b) Heap is completely full => EF = 0
 *   c) Heap is first-half full => EF = 1/2
 *   d) Heap is half full, full and empty regions interleave => EF =~ 1
 */
double ShenandoahFreeSet::external_fragmentation() {
  size_t last_idx = 0;
  size_t max_contig = 0;
  size_t empty_contig = 0;

  size_t free = 0;

  for (size_t index = _free_sets.leftmost(Mutator); index <= _free_sets.rightmost(Mutator); index++) {
    if (_free_sets.in_free_set(index, Mutator)) {
      ShenandoahHeapRegion* r = _heap->get_region(index);
      if (r->is_empty()) {
        free += ShenandoahHeapRegion::region_size_bytes();
        if (last_idx + 1 == index) {
          empty_contig++;
        } else {
          empty_contig = 1;
        }
      } else {
        empty_contig = 0;
      }

      max_contig = MAX2(max_contig, empty_contig);
      last_idx = index;
    }
  }

  if (free > 0) {
    return 1 - (1.0 * max_contig * ShenandoahHeapRegion::region_size_bytes() / free);
  } else {
    return 0;
  }
}


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
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/orderAccess.hpp"

static const char* free_memory_type_name(ShenandoahFreeMemoryType t) {
  switch (t) {
    case NotFree: return "NotFree";
    case Mutator: return "Mutator";
    case Collector: return "Collector";
    case NumFreeSets: return "NumFreeSets";
    default: return "Unrecognized";
  }
}

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

// Returns true iff this region is entirely available, either because it is empty() or because it has been found to represent
// immediate trash and we'll be able to immediately recycle it.  Note that we cannot recycle immediate trash if
// concurrent weak root processing is in progress.
inline bool ShenandoahFreeSet::can_allocate_from(ShenandoahHeapRegion *r) const {
  return r->is_empty() || (r->is_trash() && !_heap->is_concurrent_weak_root_in_progress());
}

inline bool ShenandoahFreeSet::can_allocate_from(size_t idx) const {
  ShenandoahHeapRegion* r = _heap->get_region(idx);
  return can_allocate_from(r);
}

inline size_t ShenandoahFreeSet::alloc_capacity(ShenandoahHeapRegion *r) const {
  if (r->is_trash()) {
    // This would be recycled on allocation path
    return ShenandoahHeapRegion::region_size_bytes();
  } else {
    return r->free();
  }
}

inline size_t ShenandoahFreeSet::alloc_capacity(size_t idx) const {
  ShenandoahHeapRegion* r = _heap->get_region(idx);
  return alloc_capacity(r);
}

inline bool ShenandoahFreeSet::has_alloc_capacity(ShenandoahHeapRegion *r) const {
  return alloc_capacity(r) > 0;
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

  _region_counts[Mutator] = 0;
  _region_counts[Collector] = 0;
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

inline void ShenandoahSetsOfFree::expand_bounds_maybe(ShenandoahFreeMemoryType set, size_t idx, size_t region_available) {
  if (region_available == _region_size_bytes) {
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

// Remove this region from its free set, but leave its capacity and used as part of the original free set's totals.
// When retiring a region, add any remnant of available memory within the region to the used total for the original free set.
void ShenandoahSetsOfFree::retire_within_free_set(size_t idx, size_t used_bytes) {
  ShenandoahFreeMemoryType orig_set = membership(idx);

  // Note: we may remove from free set even if region is not entirely full, such as when available < PLAB::min_size()
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert (orig_set > NotFree && orig_set < NumFreeSets, "Cannot remove from free sets if not already free");

  if (used_bytes < _region_size_bytes) {
    // Count the alignment pad remnant of memory as used when we retire this region
    increase_used(orig_set, _region_size_bytes - used_bytes);
  }

  _membership[idx] = NotFree;
  shrink_bounds_if_touched(orig_set, idx);

  _region_counts[orig_set]--;
  _region_counts[NotFree]++;
}

void ShenandoahSetsOfFree::make_free(size_t idx, ShenandoahFreeMemoryType which_set, size_t available) {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert (_membership[idx] == NotFree, "Cannot make free if already free");
  assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
  assert (available <= _region_size_bytes, "Available cannot exceed region size");

  _membership[idx] = which_set;
  _capacity_of[which_set] += _region_size_bytes;
  _used_by[which_set] += _region_size_bytes - available;
  expand_bounds_maybe(which_set, idx, available);

  _region_counts[NotFree]--;
  _region_counts[which_set]++;
}

void ShenandoahSetsOfFree::move_to_set(size_t idx, ShenandoahFreeMemoryType new_set, size_t available) {
  assert (idx < _max, "index is sane: " SIZE_FORMAT " < " SIZE_FORMAT, idx, _max);
  assert ((new_set > NotFree) && (new_set < NumFreeSets), "New set must be valid");
  assert (available <= _region_size_bytes, "Available cannot exceed region size");

  ShenandoahFreeMemoryType orig_set = _membership[idx];
  assert ((orig_set > NotFree) && (orig_set < NumFreeSets), "Cannot move free unless already free");

  // Expected transitions:
  //  During rebuild:         Mutator => Collector
  //  During flip_to_gc:      Mutator empty => Collector
  // At start of update refs: Collector => Mutator
  assert (((available <= _region_size_bytes) &&
           (((orig_set == Mutator) && (new_set == Collector)) ||
            ((orig_set == Collector) && (new_set == Mutator)))) ||
          ((available == _region_size_bytes) &&
           ((orig_set == Mutator) && (new_set == Collector))), "Unexpected movement between sets");


  size_t used = _region_size_bytes - available;
  _membership[idx] = new_set;
  _capacity_of[orig_set] -= _region_size_bytes;
  _used_by[orig_set] -= used;
  shrink_bounds_if_touched(orig_set, idx);

  _capacity_of[new_set] += _region_size_bytes;;
  _used_by[new_set] += used;
  expand_bounds_maybe(new_set, idx, available);

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
    assert (test_set == NotFree || _free_set->alloc_capacity(idx) > 0,
            "Free region " SIZE_FORMAT ", belonging to %s free set, must have alloc capacity",
            idx, free_memory_type_name(test_set));
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

inline bool ShenandoahSetsOfFree::is_empty(ShenandoahFreeMemoryType which_set) const {
  assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
  return (leftmost(which_set) > rightmost(which_set));
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
}
#endif

ShenandoahFreeSet::ShenandoahFreeSet(ShenandoahHeap* heap, size_t max_regions) :
  _heap(heap),
  _free_sets(max_regions, this)
{
  clear_internal();
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

  switch (req.type()) {
    case ShenandoahAllocRequest::_alloc_tlab:
    case ShenandoahAllocRequest::_alloc_shared: {
      // Try to allocate in the mutator view
      // Allocate within mutator free from high memory to low so as to preserve low memory for humongous allocations
      if (!_free_sets.is_empty(Mutator)) {
        // Use signed idx.  Otherwise, loop will never terminate.
        int leftmost = (int) _free_sets.leftmost(Mutator);
        for (int idx = (int) _free_sets.rightmost(Mutator); idx >= leftmost; idx--) {
          ShenandoahHeapRegion* r = _heap->get_region(idx);
          if (_free_sets.in_free_set(idx, Mutator)) {
            // try_allocate_in() increases used if the allocation is successful.
            HeapWord* result;
            size_t min_size = (req.type() == ShenandoahAllocRequest::_alloc_tlab)? req.min_size(): req.size();
            if ((alloc_capacity(r) >= min_size) && ((result = try_allocate_in(r, req, in_new_region)) != nullptr)) {
              return result;
            }
          }
        }
      }
      // There is no recovery. Mutator does not touch collector view at all.
      break;
    }
    case ShenandoahAllocRequest::_alloc_gclab:
      // GCLABs are for evacuation so we must be in evacuation phase.  If this allocation is successful, increment
      // the relevant evac_expended rather than used value.

    case ShenandoahAllocRequest::_alloc_shared_gc: {
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

      // No dice. Can we borrow space from mutator view?
      if (!ShenandoahEvacReserveOverflow) {
        return nullptr;
      }

      // Try to steal an empty region from the mutator view.
      for (size_t c = _free_sets.rightmost_empty(Mutator) + 1; c > _free_sets.leftmost_empty(Mutator); c--) {
        size_t idx = c - 1;
        if (_free_sets.in_free_set(idx, Mutator)) {
          ShenandoahHeapRegion* r = _heap->get_region(idx);
          if (can_allocate_from(r)) {
            flip_to_gc(r);
            HeapWord *result = try_allocate_in(r, req, in_new_region);
            if (result != nullptr) {
              log_debug(gc, free)("Flipped region " SIZE_FORMAT " to gc for request: " PTR_FORMAT, idx, p2i(&req));
              return result;
            }
          }
        }
      }

      // No dice. Do not try to mix mutator and GC allocations, because adjusting region UWM
      // due to GC allocations would expose unparsable mutator allocations.
      break;
    }
    default:
      ShouldNotReachHere();
  }
  return nullptr;
}

HeapWord* ShenandoahFreeSet::try_allocate_in(ShenandoahHeapRegion* r, ShenandoahAllocRequest& req, bool& in_new_region) {
  assert (has_alloc_capacity(r), "Performance: should avoid full regions on this path: " SIZE_FORMAT, r->index());
  if (_heap->is_concurrent_weak_root_in_progress() && r->is_trash()) {
    return nullptr;
  }

  HeapWord* result = nullptr;
  try_recycle_trashed(r);
  in_new_region = r->is_empty();

  if (in_new_region) {
    log_debug(gc, free)("Using new region (" SIZE_FORMAT ") for %s (" PTR_FORMAT ").",
                       r->index(), ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(&req));
  }

  // req.size() is in words, r->free() is in bytes.
  if (req.is_lab_alloc()) {
    // This is a GCLAB or a TLAB allocation
    size_t adjusted_size = req.size();
    size_t free = align_down(r->free() >> LogHeapWordSize, MinObjAlignment);
    if (adjusted_size > free) {
      adjusted_size = free;
    }
    if (adjusted_size >= req.min_size()) {
      result = r->allocate(adjusted_size, req.type());
      log_debug(gc, free)("Allocated " SIZE_FORMAT " words (adjusted from " SIZE_FORMAT ") for %s @" PTR_FORMAT
                          " from %s region " SIZE_FORMAT ", free bytes remaining: " SIZE_FORMAT,
                          adjusted_size, req.size(), ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(result),
                          free_memory_type_name(_free_sets.membership(r->index())),  r->index(), r->free());
      assert (result != nullptr, "Allocation must succeed: free " SIZE_FORMAT ", actual " SIZE_FORMAT, free, adjusted_size);
      req.set_actual_size(adjusted_size);
    } else {
      log_trace(gc, free)("Failed to shrink TLAB or GCLAB request (" SIZE_FORMAT ") in region " SIZE_FORMAT " to " SIZE_FORMAT
                          " because min_size() is " SIZE_FORMAT, req.size(), r->index(), adjusted_size, req.min_size());
    }
  } else {
    size_t size = req.size();
    result = r->allocate(size, req.type());
    if (result != nullptr) {
      // Record actual allocation size
      log_debug(gc, free)("Allocated " SIZE_FORMAT " words for %s @" PTR_FORMAT
                          " from %s region " SIZE_FORMAT ", free bytes remaining: " SIZE_FORMAT,
                          size, ShenandoahAllocRequest::alloc_type_to_string(req.type()), p2i(result),
                          free_memory_type_name(_free_sets.membership(r->index())),  r->index(), r->free());
      req.set_actual_size(size);
    }
  }

  if (result != nullptr) {
    // Allocation successful, bump stats:
    if (req.is_mutator_alloc()) {
      _free_sets.increase_used(Mutator, req.actual_size() * HeapWordSize);
    } else {
      assert(req.is_gc_alloc(), "Should be gc_alloc since req wasn't mutator alloc");

      // For GC allocations, we advance update_watermark because the objects relocated into this memory during
      // evacuation are not updated during evacuation.
      r->set_update_watermark(r->top());
    }
  }

  if (alloc_capacity(r) < PLAB::min_size() * HeapWordSize) {
    // Regardless of whether this allocation succeeded, if the remaining memory is less than PLAB:min_size(), retire this region.
    // Note that retire_within_free_set() increased used to account for waste.

    // Note that a previous implementation of this function would retire a region following any failure to
    // allocate within.  This was observed to result in large amounts of available memory being ignored
    // following a failed shared allocation request.  TLAB requests will generally downsize to absorb all
    // memory available within the region even if this is less than the desired size.

    size_t idx = r->index();
    _free_sets.retire_within_free_set(idx, r->used());
    _free_sets.assert_bounds();
  }
  return result;
}

HeapWord* ShenandoahFreeSet::allocate_contiguous(ShenandoahAllocRequest& req) {
  shenandoah_assert_heaplocked();

  size_t words_size = req.size();
  size_t num = ShenandoahHeapRegion::required_regions(words_size * HeapWordSize);

  // Check if there are enough regions left to satisfy allocation.
  if (num > _free_sets.count(Mutator)) {
    return nullptr;
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

    r->set_update_watermark(r->bottom());
    r->set_top(r->bottom() + used_words);

    // While individual regions report their true use, all humongous regions are marked used in the free set.
    _free_sets.retire_within_free_set(r->index(), ShenandoahHeapRegion::region_size_bytes());
  }

  size_t total_humongous_size = ShenandoahHeapRegion::region_size_bytes() * num;
  _free_sets.increase_used(Mutator, total_humongous_size);
  _free_sets.assert_bounds();
  req.set_actual_size(words_size);
  return _heap->get_region(beg)->bottom();
}

void ShenandoahFreeSet::try_recycle_trashed(ShenandoahHeapRegion *r) {
  if (r->is_trash()) {
    _heap->decrease_used(r->used());
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

// This function places all regions that have allocation capacity into the mutator_set.  Subsequently, we will
// move some of the mutator regions into the collector set with the intent of packing collector memory into the
//  highest (rightmost) addresses of the heap, with mutator memory consuming the lowest addresses of the heap.
void ShenandoahFreeSet::find_regions_with_alloc_capacity(size_t &cset_regions) {
  cset_regions = 0;
  for (size_t idx = 0; idx < _heap->num_regions(); idx++) {
    ShenandoahHeapRegion* region = _heap->get_region(idx);
    if (region->is_trash()) {
      // Trashed regions represent regions that had been in the collection set but have not yet been "cleaned up".
      // The cset regions are not "trashed" until we have finished update refs.
      cset_regions++;
    }
    if (region->is_alloc_allowed() || region->is_trash()) {
      assert(!region->is_cset(), "Shouldn't be adding cset regions to the free set");
      assert(_free_sets.in_free_set(idx, NotFree), "We are about to make region free; it should not be free already");

      // Do not add regions that would almost surely fail allocation
      size_t ac = alloc_capacity(region);
      if (ac > PLAB::min_size() * HeapWordSize) {
        _free_sets.make_free(idx, Mutator, ac);
        log_debug(gc, free)(
          "  Adding Region " SIZE_FORMAT " (Free: " SIZE_FORMAT "%s, Used: " SIZE_FORMAT "%s) to mutator set",
          idx, byte_size_in_proper_unit(region->free()), proper_unit_for_byte_size(region->free()),
          byte_size_in_proper_unit(region->used()), proper_unit_for_byte_size(region->used()));
      } else {
        assert(_free_sets.membership(idx) == NotFree,
               "Region " SIZE_FORMAT " should not be in free set because capacity is " SIZE_FORMAT, idx, ac);
      }
    } else {
      assert(_free_sets.membership(idx) == NotFree,
             "Region " SIZE_FORMAT " should not be in free set because alloc is not allowed and not is trash", idx);
    }
  }
}

// Move no more than max_xfer_regions from the existing Collector free sets to the Mutator free set.
// This is called from outside the heap lock at the start of update refs.  At this point, we no longer
// need to reserve memory within for evacuation.  (We will create a new reserve after update refs finishes,
// setting aside some of the memory that was reclaimed by the most recent GC.  This new reserve will satisfy
// the evacuation needs of the next GC pass.)
void ShenandoahFreeSet::move_collector_sets_to_mutator(size_t max_xfer_regions) {
  size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
  size_t collector_empty_xfer = 0;
  size_t collector_not_empty_xfer = 0;

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
  log_info(gc, free)("At start of update refs, moving " SIZE_FORMAT "%s to Mutator free set from Collector Reserve",
                     byte_size_in_proper_unit(collector_xfer), proper_unit_for_byte_size(collector_xfer));
}


// Overwrite arguments to represent the number of regions to be reclaimed from the cset
void ShenandoahFreeSet::prepare_to_rebuild(size_t &cset_regions) {
  shenandoah_assert_heaplocked();
  // This resets all state information, removing all regions from all sets.
  clear();
  log_debug(gc, free)("Rebuilding FreeSet");

  // This places regions that have alloc_capacity into the old_collector set if they identify as is_old() or the
  // mutator set otherwise.
  find_regions_with_alloc_capacity(cset_regions);
}

void ShenandoahFreeSet::finish_rebuild(size_t cset_regions) {
  shenandoah_assert_heaplocked();

  // Our desire is to reserve this much memory for future evacuation.  We may end up reserving less, if
  // memory is in short supply.

  size_t reserve = _heap->max_capacity() * ShenandoahEvacReserve / 100;
  size_t available_in_collector_set = _free_sets.capacity_of(Collector) - _free_sets.used_by(Collector);
  size_t additional_reserve;
  if (available_in_collector_set < reserve) {
    additional_reserve = reserve - available_in_collector_set;
  } else {
    additional_reserve = 0;
  }

  reserve_regions(reserve);
  _free_sets.assert_bounds();
  log_status();
}

void ShenandoahFreeSet::rebuild() {
  size_t cset_regions;
  prepare_to_rebuild(cset_regions);
  finish_rebuild(cset_regions);
}

// Having placed all regions that have allocation capacity into the mutator set, move some of these regions from
// the mutator set into the collector set in order to assure that the memory available for allocations within
// the collector set is at least to_reserve.
void ShenandoahFreeSet::reserve_regions(size_t to_reserve) {
  for (size_t i = _heap->num_regions(); i > 0; i--) {
    size_t idx = i - 1;
    ShenandoahHeapRegion* r = _heap->get_region(idx);
    if (!_free_sets.in_free_set(idx, Mutator)) {
      continue;
    }

    size_t ac = alloc_capacity(r);
    assert (ac > 0, "Membership in free set implies has capacity");

    bool move_to_collector = _free_sets.capacity_of(Collector) < to_reserve;
    if (!move_to_collector) {
      // We've satisfied to_reserve
      break;
    }

    if (move_to_collector) {
      // Note: In a previous implementation, regions were only placed into the survivor space (collector_is_free) if
      // they were entirely empty.  I'm not sure I understand the rationale for that.  That alternative behavior would
      // tend to mix survivor objects with ephemeral objects, making it more difficult to reclaim the memory for the
      // ephemeral objects.
      _free_sets.move_to_set(idx, Collector, ac);
      log_debug(gc,free)("  Shifting region " SIZE_FORMAT " from mutator_free to collector_free", idx);
    }
  }

  if (LogTarget(Info, gc, free)::is_enabled()) {
    size_t reserve = _free_sets.capacity_of(Collector);
    if (reserve < to_reserve) {
      log_info(gc, free)("Wanted " PROPERFMT " for young reserve, but only reserved: " PROPERFMT,
                         PROPERFMTARGS(to_reserve), PROPERFMTARGS(reserve));
    }
  }
}

void ShenandoahFreeSet::log_status() {
  shenandoah_assert_heaplocked();

#ifdef ASSERT
  // Dump of the FreeSet details is only enabled if assertions are enabled
  if (LogTarget(Debug, gc, free)::is_enabled()) {
#define BUFFER_SIZE 80
    size_t region_size_bytes = ShenandoahHeapRegion::region_size_bytes();
    size_t consumed_collector = 0;
    size_t available_collector = 0;
    size_t consumed_mutator = 0;
    size_t available_mutator = 0;

    char buffer[BUFFER_SIZE];
    for (uint i = 0; i < BUFFER_SIZE; i++) {
      buffer[i] = '\0';
    }
    log_debug(gc, free)("FreeSet map legend:"
                       " M:mutator_free C:collector_free H:humongous _:retired");
    log_debug(gc, free)(" mutator free range [" SIZE_FORMAT ".." SIZE_FORMAT "], "
                        " collector free range [" SIZE_FORMAT ".." SIZE_FORMAT "]",
                        _free_sets.leftmost(Mutator), _free_sets.rightmost(Mutator),
                        _free_sets.leftmost(Collector), _free_sets.rightmost(Collector));

    for (uint i = 0; i < _heap->num_regions(); i++) {
      ShenandoahHeapRegion *r = _heap->get_region(i);
      uint idx = i % 64;
      if ((i != 0) && (idx == 0)) {
        log_debug(gc, free)(" %6u: %s", i-64, buffer);
      }
      if (_free_sets.in_free_set(i, Mutator)) {
        size_t capacity = alloc_capacity(r);
        available_mutator += capacity;
        consumed_mutator += region_size_bytes - capacity;
        buffer[idx] = (capacity == region_size_bytes)? 'M': 'm';
      } else if (_free_sets.in_free_set(i, Collector)) {
        size_t capacity = alloc_capacity(r);
        available_collector += capacity;
        consumed_collector += region_size_bytes - capacity;
        buffer[idx] = (capacity == region_size_bytes)? 'C': 'c';
      } else if (r->is_humongous()) {
        buffer[idx] = 'h';
      } else {
        buffer[idx] = '_';
      }
    }
    uint remnant = _heap->num_regions() % 64;
    if (remnant > 0) {
      buffer[remnant] = '\0';
    } else {
      remnant = 64;
    }
    log_debug(gc, free)(" %6u: %s", (uint) (_heap->num_regions() - remnant), buffer);
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

      // Since certain regions that belonged to the Mutator free set at the time of most recent rebuild may have been retired,
      // the sum of used and capacities within regions that are still in the Mutator free set may not match my internally tracked
      // values of used() and free().
      assert(free == total_free, "Free memory should match");

      ls.print("Free: " SIZE_FORMAT "%s, Max: " SIZE_FORMAT "%s regular, " SIZE_FORMAT "%s humongous, ",
               byte_size_in_proper_unit(free),          proper_unit_for_byte_size(free),
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


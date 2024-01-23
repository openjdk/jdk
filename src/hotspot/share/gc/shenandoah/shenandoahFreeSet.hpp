
/*
 * Copyright (c) 2016, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHFREESET_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHFREESET_HPP

#include "gc/shenandoah/shenandoahHeapRegionSet.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"

enum ShenandoahFreeMemoryType : uint8_t {
  NotFree,
  Mutator,
  Collector,
  NumFreeSets
};

class ShenandoahSetsOfFree {

private:
  size_t _max;                  // The maximum number of heap regions
  ShenandoahFreeSet* _free_set;
  size_t _region_size_bytes;
  ShenandoahFreeMemoryType* _membership;
  size_t _leftmosts[NumFreeSets];
  size_t _rightmosts[NumFreeSets];
  size_t _leftmosts_empty[NumFreeSets];
  size_t _rightmosts_empty[NumFreeSets];

  // _capacity_of and _used_by are denoted in bytes
  size_t _capacity_of[NumFreeSets];
  size_t _used_by[NumFreeSets];
  size_t _region_counts[NumFreeSets];

  inline void shrink_bounds_if_touched(ShenandoahFreeMemoryType set, size_t idx);
  inline void expand_bounds_maybe(ShenandoahFreeMemoryType set, size_t idx, size_t capacity);

  // Restore all state variables to initial default state.
  void clear_internal();

public:
  ShenandoahSetsOfFree(size_t max_regions, ShenandoahFreeSet* free_set);
  ~ShenandoahSetsOfFree();

  // Make all regions NotFree and reset all bounds
  void clear_all();

  // Retire region idx from within its free set.  Requires that idx is in a free set.  The free set's original capacity
  // and usage is unaffected, but this region is no longer considered to be part of the free set insofar as future
  // allocation requests are concerned.  
  void retire_within_free_set(size_t idx, size_t used_bytes);

  // Place region idx into free set which_set.  Requires that idx is currently NotFree.
  void make_free(size_t idx, ShenandoahFreeMemoryType which_set, size_t region_capacity);

  // Place region idx into free set new_set.  Requires that idx is currently not NotFree.
  void move_to_set(size_t idx, ShenandoahFreeMemoryType new_set, size_t region_capacity);

  // Returns the ShenandoahFreeMemoryType affiliation of region idx, or NotFree if this region is not currently free.  This does
  // not enforce that free_set membership implies allocation capacity.
  inline ShenandoahFreeMemoryType membership(size_t idx) const;

  // Returns true iff region idx is in the test_set free_set.  Before returning true, asserts that the free
  // set is not empty.  Requires that test_set != NotFree or NumFreeSets.
  inline bool in_free_set(size_t idx, ShenandoahFreeMemoryType which_set) const;

  // The following four methods return the left-most and right-most bounds on ranges of regions representing
  // the requested set.  The _empty variants represent bounds on the range that holds completely empty
  // regions, which are required for humongous allocations and desired for "very large" allocations.  A
  // return value of -1 from leftmost() or leftmost_empty() denotes that the corresponding set is empty.
  // In other words:
  //   if the requested which_set is empty:
  //     leftmost() and leftmost_empty() return _max, rightmost() and rightmost_empty() return 0
  //   otherwise, expect the following:
  //     0 <= leftmost <= leftmost_empty <= rightmost_empty <= rightmost < _max
  inline size_t leftmost(ShenandoahFreeMemoryType which_set) const;
  inline size_t rightmost(ShenandoahFreeMemoryType which_set) const;
  size_t leftmost_empty(ShenandoahFreeMemoryType which_set);
  size_t rightmost_empty(ShenandoahFreeMemoryType which_set);

  inline bool is_empty(ShenandoahFreeMemoryType which_set) const;

  inline void increase_used(ShenandoahFreeMemoryType which_set, size_t bytes);

  inline size_t capacity_of(ShenandoahFreeMemoryType which_set) const {
    assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
    return _capacity_of[which_set];
  }

  inline size_t used_by(ShenandoahFreeMemoryType which_set) const {
    assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
    return _used_by[which_set];
  }

  inline void set_capacity_of(ShenandoahFreeMemoryType which_set, size_t value) {
    assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
    _capacity_of[which_set] = value;
  }

  inline void set_used_by(ShenandoahFreeMemoryType which_set, size_t value) {
    assert (which_set > NotFree && which_set < NumFreeSets, "selected free set must be valid");
    _used_by[which_set] = value;
  }

  inline size_t max() const { return _max; }

  inline size_t count(ShenandoahFreeMemoryType which_set) const { return _region_counts[which_set]; }

  // Assure leftmost, rightmost, leftmost_empty, and rightmost_empty bounds are valid for all free sets.
  // Valid bounds honor all of the following (where max is the number of heap regions):
  //   if the set is empty, leftmost equals max and rightmost equals 0
  //   Otherwise (the set is not empty):
  //     0 <= leftmost < max and 0 <= rightmost < max
  //     the region at leftmost is in the set
  //     the region at rightmost is in the set
  //     rightmost >= leftmost
  //     for every idx that is in the set {
  //       idx >= leftmost &&
  //       idx <= rightmost
  //     }
  //   if the set has no empty regions, leftmost_empty equals max and rightmost_empty equals 0
  //   Otherwise (the region has empty regions):
  //     0 <= lefmost_empty < max and 0 <= rightmost_empty < max
  //     rightmost_empty >= leftmost_empty
  //     for every idx that is in the set and is empty {
  //       idx >= leftmost &&
  //       idx <= rightmost
  //     }
  void assert_bounds() NOT_DEBUG_RETURN;
};

class ShenandoahFreeSet : public CHeapObj<mtGC> {
private:
  ShenandoahHeap* const _heap;
  ShenandoahSetsOfFree _free_sets;

  HeapWord* try_allocate_in(ShenandoahHeapRegion* region, ShenandoahAllocRequest& req, bool& in_new_region);

  // While holding the heap lock, allocate memory for a single object which is to be entirely contained
  // within a single HeapRegion as characterized by req.  The req.size() value is known to be less than or
  // equal to ShenandoahHeapRegion::humongous_threshold_words().  The caller of allocate_single is responsible
  // for registering the resulting object and setting the remembered set card values as appropriate.  The
  // most common case is that we are allocating a PLAB in which case object registering and card dirtying
  // is managed after the PLAB is divided into individual objects.
  HeapWord* allocate_single(ShenandoahAllocRequest& req, bool& in_new_region);
  HeapWord* allocate_contiguous(ShenandoahAllocRequest& req);

  void flip_to_gc(ShenandoahHeapRegion* r);
  void clear_internal();

  void try_recycle_trashed(ShenandoahHeapRegion *r);

  inline bool can_allocate_from(ShenandoahHeapRegion *r) const;
  inline bool can_allocate_from(size_t idx) const;

  inline bool has_alloc_capacity(ShenandoahHeapRegion *r) const;

  void find_regions_with_alloc_capacity(size_t &cset_regions);
  void reserve_regions(size_t to_reserve);

  void prepare_to_rebuild(size_t &cset_regions);
  void finish_rebuild(size_t cset_regions, bool from_corrupted);

public:
  ShenandoahFreeSet(ShenandoahHeap* heap, size_t max_regions);

  // Public because ShenandoahSetsOfFree assertions require access.
  inline size_t alloc_capacity(ShenandoahHeapRegion *r) const;
  inline size_t alloc_capacity(size_t idx) const;

  void clear();
  void rebuild(bool from_corrupted = false);

  // kelvin new method: call this from worker thread 0 at start of
  // update refs.  we no longer need to maintain a collector reserve.
  // At end of update-refs, the cset regions will be added to the
  // free set, and we will rebuild again, at which time we'll set
  // aside the Collector reserve for next GC pass.
  void move_collector_sets_to_mutator(size_t cset_regions);

  void recycle_trash();
  void log_status(bool from_corrupted = false);

  inline size_t capacity()  const { return _free_sets.capacity_of(Mutator); }
  inline size_t used()      const { return _free_sets.used_by(Mutator);     }
  inline size_t available() const {
    assert(used() <= capacity(), "must use less than capacity");
    return capacity() - used();
  }

  HeapWord* allocate(ShenandoahAllocRequest& req, bool& in_new_region);
  size_t unsafe_peek_free() const;

  double internal_fragmentation();
  double external_fragmentation();

  void print_on(outputStream* out) const;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHFREESET_HPP

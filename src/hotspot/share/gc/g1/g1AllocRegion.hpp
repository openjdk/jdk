/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1ALLOCREGION_HPP
#define SHARE_GC_G1_G1ALLOCREGION_HPP

#include "gc/g1/g1EvacStats.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1HeapRegionAttr.hpp"
#include "gc/g1/g1NUMA.hpp"

class G1CollectedHeap;

// A class that holds a region that is active in satisfying allocation
// requests, potentially issued in parallel. When the active region is
// full it will be retired and replaced with a new one. The
// implementation assumes that fast-path allocations will be lock-free
// and a lock will need to be taken when the active region needs to be
// replaced.

class G1AllocRegion : public CHeapObj<mtGC> {

private:
  // The active allocating region we are currently allocating out
  // of. The invariant is that if this object is initialized (i.e.,
  // init() has been called and release() has not) then _alloc_region
  // is either an active allocating region or the dummy region (i.e.,
  // it can never be null) and this object can be used to satisfy
  // allocation requests. If this object is not initialized
  // (i.e. init() has not been called or release() has been called)
  // then _alloc_region is null and this object should not be used to
  // satisfy allocation requests (it was done this way to force the
  // correct use of init() and release()).
  G1HeapRegion* volatile _alloc_region;

  // It keeps track of the distinct number of regions that are used
  // for allocation in the active interval of this object, i.e.,
  // between a call to init() and a call to release(). The count
  // mostly includes regions that are freshly allocated, as well as
  // the region that is re-used using the set() method. This count can
  // be used in any heuristics that might want to bound how many
  // distinct regions this object can used during an active interval.
  uint _count;

  // Useful for debugging and tracing.
  const char* _name;

  // A dummy region (i.e., it's been allocated specially for this
  // purpose and it is not part of the heap) that is full (i.e., top()
  // == end()). When we don't have a valid active region we make
  // _alloc_region point to this. This allows us to skip checking
  // whether the _alloc_region is null or not.
  static G1HeapRegion* _dummy_region;

  // After a region is allocated by alloc_new_region, this
  // method is used to set it as the active alloc_region
  void update_alloc_region(G1HeapRegion* alloc_region);

  // Allocate a new active region and use it to perform a word_size
  // allocation.
  HeapWord* new_alloc_region_and_allocate(size_t word_size);

  // Perform an allocation out of a new allocation region, retiring the current one.
  inline HeapWord* attempt_allocation_using_new_region(size_t min_word_size,
                                                       size_t desired_word_size,
                                                       size_t* actual_word_size);
protected:
  // The memory node index this allocation region belongs to.
  uint _node_index;

  void set(G1HeapRegion* alloc_region);

  // Reset the alloc region to point the dummy region.
  void reset_alloc_region();

  // Perform a MT-safe allocation out of the given region.
  inline HeapWord* par_allocate(G1HeapRegion* alloc_region,
                                size_t word_size);

  // Ensure that the region passed as a parameter has been filled up
  // so that no one else can allocate out of it any more.
  // Returns the number of bytes that have been wasted by filled up
  // the space.
  size_t fill_up_remaining_space(G1HeapRegion* alloc_region);

  // Retire the active allocating region. If fill_up is true then make
  // sure that the region is full before we retire it so that no one
  // else can allocate out of it.
  // Returns the number of bytes that have been filled up during retire.
  virtual size_t retire(bool fill_up);

  size_t retire_internal(G1HeapRegion* alloc_region, bool fill_up);

  // For convenience as subclasses use it.
  static G1CollectedHeap* _g1h;

  virtual G1HeapRegion* allocate_new_region(size_t word_size) = 0;
  virtual void retire_region(G1HeapRegion* alloc_region) = 0;

  G1AllocRegion(const char* name, uint node_index);

public:
  static void setup(G1CollectedHeap* g1h, G1HeapRegion* dummy_region);

  G1HeapRegion* get() const {
    G1HeapRegion * hr = _alloc_region;
    // Make sure that the dummy region does not escape this class.
    return (hr == _dummy_region) ? nullptr : hr;
  }

  uint count() { return _count; }

  // The following two are the building blocks for the allocation method.

  // Perform an allocation out of the current allocation region, with the given
  // minimum and desired size. Returns the actual size allocated (between
  // minimum and desired size) in actual_word_size if the allocation has been
  // successful.
  // Should be called without holding a lock. It will try to allocate lock-free
  // out of the active region, or return null if it was unable to.
  inline HeapWord* attempt_allocation(size_t min_word_size,
                                      size_t desired_word_size,
                                      size_t* actual_word_size);

  inline HeapWord* attempt_allocation_locked(size_t word_size);
  // Second-level allocation: Should be called while holding a
  // lock. We require that the caller takes the appropriate lock
  // before calling this so that it is easier to make it conform
  // to the locking protocol. The min and desired word size allow
  // specifying a minimum and maximum size of the allocation. The
  // actual size of allocation is returned in actual_word_size.
  inline HeapWord* attempt_allocation_locked(size_t min_word_size,
                                             size_t desired_word_size,
                                             size_t* actual_word_size);

  // Should be called before we start using this object.
  virtual void init();

  // Should be called when we want to release the active region which
  // is returned after it's been retired.
  virtual G1HeapRegion* release();

  void trace(const char* str,
             size_t min_word_size = 0,
             size_t desired_word_size = 0,
             size_t actual_word_size = 0,
             HeapWord* result = nullptr) PRODUCT_RETURN;
};

class MutatorAllocRegion : public G1AllocRegion {
private:
  // Keeps track of the total waste generated during the current
  // mutator phase.
  size_t _wasted_bytes;

  // Retained allocation region. Used to lower the waste generated
  // during mutation by having two active regions if the free space
  // in a region about to be retired still could fit a TLAB.
  G1HeapRegion* volatile _retained_alloc_region;

  // Decide if the region should be retained, based on the free size
  // in it and the free size in the currently retained region, if any.
  bool should_retain(G1HeapRegion* region);
protected:
  G1HeapRegion* allocate_new_region(size_t word_size) override;
  void retire_region(G1HeapRegion* alloc_region) override;
  size_t retire(bool fill_up) override;
public:
  MutatorAllocRegion(uint node_index)
    : G1AllocRegion("Mutator Alloc Region", node_index),
      _wasted_bytes(0),
      _retained_alloc_region(nullptr) { }

  // Returns the combined used memory in the current alloc region and
  // the retained alloc region.
  size_t used_in_alloc_regions();

  // Perform an allocation out of the retained allocation region, with the given
  // minimum and desired size. Returns the actual size allocated (between
  // minimum and desired size) in actual_word_size if the allocation has been
  // successful.
  // Should be called without holding a lock. It will try to allocate lock-free
  // out of the retained region, or return null if it was unable to.
  inline HeapWord* attempt_retained_allocation(size_t min_word_size,
                                               size_t desired_word_size,
                                               size_t* actual_word_size);

  // This specialization of release() makes sure that the retained alloc
  // region is retired and set to null.
  G1HeapRegion* release() override;

  void init() override;
};

// Common base class for allocation regions used during GC.
class G1GCAllocRegion : public G1AllocRegion {
  // When we set up a new active region we save its used bytes in this
  // field so that, when we retire it, we can calculate how much space
  // we allocated in it.
  size_t _used_bytes_before;
protected:
  G1EvacStats* _stats;
  G1HeapRegionAttr::region_type_t _purpose;

  G1HeapRegion* allocate_new_region(size_t word_size) override;
  void retire_region(G1HeapRegion* alloc_region) override;

  size_t retire(bool fill_up) override;

  G1GCAllocRegion(const char* name, G1EvacStats* stats,
                  G1HeapRegionAttr::region_type_t purpose, uint node_index = G1NUMA::AnyNodeIndex)
    : G1AllocRegion(name, node_index), _used_bytes_before(0), _stats(stats), _purpose(purpose) {
    assert(stats != nullptr, "Must pass non-null PLAB statistics");
  }
public:
  // This can be used to reuse a specific region. (Use Example: we try to retain the
  // last old GC alloc region that we've used during a GC and we can use reuse() to
  // re-instate it at the beginning of the next GC.)
  void reuse(G1HeapRegion* alloc_region);
};

class SurvivorGCAllocRegion : public G1GCAllocRegion {
public:
  SurvivorGCAllocRegion(G1EvacStats* stats, uint node_index)
  : G1GCAllocRegion("Survivor GC Alloc Region", stats, G1HeapRegionAttr::Young, node_index) { }
};

class OldGCAllocRegion : public G1GCAllocRegion {
public:
  OldGCAllocRegion(G1EvacStats* stats)
  : G1GCAllocRegion("Old GC Alloc Region", stats, G1HeapRegionAttr::Old) { }
};

#endif // SHARE_GC_G1_G1ALLOCREGION_HPP

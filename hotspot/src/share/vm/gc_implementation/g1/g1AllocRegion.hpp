/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1ALLOCREGION_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1ALLOCREGION_HPP

#include "gc_implementation/g1/heapRegion.hpp"

class G1CollectedHeap;

// 0 -> no tracing, 1 -> basic tracing, 2 -> basic + allocation tracing
#define G1_ALLOC_REGION_TRACING 0

class ar_ext_msg;

// A class that holds a region that is active in satisfying allocation
// requests, potentially issued in parallel. When the active region is
// full it will be retired and replaced with a new one. The
// implementation assumes that fast-path allocations will be lock-free
// and a lock will need to be taken when the active region needs to be
// replaced.

class G1AllocRegion VALUE_OBJ_CLASS_SPEC {
  friend class ar_ext_msg;

private:
  // The active allocating region we are currently allocating out
  // of. The invariant is that if this object is initialized (i.e.,
  // init() has been called and release() has not) then _alloc_region
  // is either an active allocating region or the dummy region (i.e.,
  // it can never be NULL) and this object can be used to satisfy
  // allocation requests. If this object is not initialized
  // (i.e. init() has not been called or release() has been called)
  // then _alloc_region is NULL and this object should not be used to
  // satisfy allocation requests (it was done this way to force the
  // correct use of init() and release()).
  HeapRegion* volatile _alloc_region;

  // Allocation context associated with this alloc region.
  AllocationContext_t _allocation_context;

  // It keeps track of the distinct number of regions that are used
  // for allocation in the active interval of this object, i.e.,
  // between a call to init() and a call to release(). The count
  // mostly includes regions that are freshly allocated, as well as
  // the region that is re-used using the set() method. This count can
  // be used in any heuristics that might want to bound how many
  // distinct regions this object can used during an active interval.
  uint _count;

  // When we set up a new active region we save its used bytes in this
  // field so that, when we retire it, we can calculate how much space
  // we allocated in it.
  size_t _used_bytes_before;

  // When true, indicates that allocate calls should do BOT updates.
  const bool _bot_updates;

  // Useful for debugging and tracing.
  const char* _name;

  // A dummy region (i.e., it's been allocated specially for this
  // purpose and it is not part of the heap) that is full (i.e., top()
  // == end()). When we don't have a valid active region we make
  // _alloc_region point to this. This allows us to skip checking
  // whether the _alloc_region is NULL or not.
  static HeapRegion* _dummy_region;

  // Some of the methods below take a bot_updates parameter. Its value
  // should be the same as the _bot_updates field. The idea is that
  // the parameter will be a constant for a particular alloc region
  // and, given that these methods will be hopefully inlined, the
  // compiler should compile out the test.

  // Perform a non-MT-safe allocation out of the given region.
  static inline HeapWord* allocate(HeapRegion* alloc_region,
                                   size_t word_size,
                                   bool bot_updates);

  // Perform a MT-safe allocation out of the given region.
  static inline HeapWord* par_allocate(HeapRegion* alloc_region,
                                       size_t word_size,
                                       bool bot_updates);

  // Ensure that the region passed as a parameter has been filled up
  // so that noone else can allocate out of it any more.
  static void fill_up_remaining_space(HeapRegion* alloc_region,
                                      bool bot_updates);

  // Retire the active allocating region. If fill_up is true then make
  // sure that the region is full before we retire it so that noone
  // else can allocate out of it.
  void retire(bool fill_up);

  // After a region is allocated by alloc_new_region, this
  // method is used to set it as the active alloc_region
  void update_alloc_region(HeapRegion* alloc_region);

  // Allocate a new active region and use it to perform a word_size
  // allocation. The force parameter will be passed on to
  // G1CollectedHeap::allocate_new_alloc_region() and tells it to try
  // to allocate a new region even if the max has been reached.
  HeapWord* new_alloc_region_and_allocate(size_t word_size, bool force);

  void fill_in_ext_msg(ar_ext_msg* msg, const char* message);

protected:
  // For convenience as subclasses use it.
  static G1CollectedHeap* _g1h;

  virtual HeapRegion* allocate_new_region(size_t word_size, bool force) = 0;
  virtual void retire_region(HeapRegion* alloc_region,
                             size_t allocated_bytes) = 0;

  G1AllocRegion(const char* name, bool bot_updates);

public:
  static void setup(G1CollectedHeap* g1h, HeapRegion* dummy_region);

  HeapRegion* get() const {
    HeapRegion * hr = _alloc_region;
    // Make sure that the dummy region does not escape this class.
    return (hr == _dummy_region) ? NULL : hr;
  }

  void set_allocation_context(AllocationContext_t context) { _allocation_context = context; }
  AllocationContext_t  allocation_context() { return _allocation_context; }

  uint count() { return _count; }

  // The following two are the building blocks for the allocation method.

  // First-level allocation: Should be called without holding a
  // lock. It will try to allocate lock-free out of the active region,
  // or return NULL if it was unable to.
  inline HeapWord* attempt_allocation(size_t word_size, bool bot_updates);

  // Second-level allocation: Should be called while holding a
  // lock. It will try to first allocate lock-free out of the active
  // region or, if it's unable to, it will try to replace the active
  // alloc region with a new one. We require that the caller takes the
  // appropriate lock before calling this so that it is easier to make
  // it conform to its locking protocol.
  inline HeapWord* attempt_allocation_locked(size_t word_size,
                                             bool bot_updates);

  // Should be called to allocate a new region even if the max of this
  // type of regions has been reached. Should only be called if other
  // allocation attempts have failed and we are not holding a valid
  // active region.
  inline HeapWord* attempt_allocation_force(size_t word_size,
                                            bool bot_updates);

  // Should be called before we start using this object.
  void init();

  // This can be used to set the active region to a specific
  // region. (Use Example: we try to retain the last old GC alloc
  // region that we've used during a GC and we can use set() to
  // re-instate it at the beginning of the next GC.)
  void set(HeapRegion* alloc_region);

  // Should be called when we want to release the active region which
  // is returned after it's been retired.
  virtual HeapRegion* release();

#if G1_ALLOC_REGION_TRACING
  void trace(const char* str, size_t word_size = 0, HeapWord* result = NULL);
#else // G1_ALLOC_REGION_TRACING
  void trace(const char* str, size_t word_size = 0, HeapWord* result = NULL) { }
#endif // G1_ALLOC_REGION_TRACING
};

class MutatorAllocRegion : public G1AllocRegion {
protected:
  virtual HeapRegion* allocate_new_region(size_t word_size, bool force);
  virtual void retire_region(HeapRegion* alloc_region, size_t allocated_bytes);
public:
  MutatorAllocRegion()
    : G1AllocRegion("Mutator Alloc Region", false /* bot_updates */) { }
};

class SurvivorGCAllocRegion : public G1AllocRegion {
protected:
  virtual HeapRegion* allocate_new_region(size_t word_size, bool force);
  virtual void retire_region(HeapRegion* alloc_region, size_t allocated_bytes);
public:
  SurvivorGCAllocRegion()
  : G1AllocRegion("Survivor GC Alloc Region", false /* bot_updates */) { }
};

class OldGCAllocRegion : public G1AllocRegion {
protected:
  virtual HeapRegion* allocate_new_region(size_t word_size, bool force);
  virtual void retire_region(HeapRegion* alloc_region, size_t allocated_bytes);
public:
  OldGCAllocRegion()
  : G1AllocRegion("Old GC Alloc Region", true /* bot_updates */) { }

  // This specialization of release() makes sure that the last card that has
  // been allocated into has been completely filled by a dummy object.  This
  // avoids races when remembered set scanning wants to update the BOT of the
  // last card in the retained old gc alloc region, and allocation threads
  // allocating into that card at the same time.
  virtual HeapRegion* release();
};

class ar_ext_msg : public err_msg {
public:
  ar_ext_msg(G1AllocRegion* alloc_region, const char *message) : err_msg("%s", "") {
    alloc_region->fill_in_ext_msg(this, message);
  }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1ALLOCREGION_HPP

/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_G1ALLOCATOR_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_G1ALLOCATOR_HPP

#include "gc_implementation/g1/g1AllocationContext.hpp"
#include "gc_implementation/g1/g1AllocRegion.hpp"
#include "gc_implementation/g1/g1InCSetState.hpp"
#include "gc_implementation/shared/parGCAllocBuffer.hpp"

// Base class for G1 allocators.
class G1Allocator : public CHeapObj<mtGC> {
  friend class VMStructs;
protected:
  G1CollectedHeap* _g1h;

  // Outside of GC pauses, the number of bytes used in all regions other
  // than the current allocation region.
  size_t _summary_bytes_used;

public:
   G1Allocator(G1CollectedHeap* heap) :
     _g1h(heap), _summary_bytes_used(0) { }

   static G1Allocator* create_allocator(G1CollectedHeap* g1h);

   virtual void init_mutator_alloc_region() = 0;
   virtual void release_mutator_alloc_region() = 0;

   virtual void init_gc_alloc_regions(EvacuationInfo& evacuation_info) = 0;
   virtual void release_gc_alloc_regions(uint no_of_gc_workers, EvacuationInfo& evacuation_info) = 0;
   virtual void abandon_gc_alloc_regions() = 0;

   virtual MutatorAllocRegion*    mutator_alloc_region(AllocationContext_t context) = 0;
   virtual SurvivorGCAllocRegion* survivor_gc_alloc_region(AllocationContext_t context) = 0;
   virtual OldGCAllocRegion*      old_gc_alloc_region(AllocationContext_t context) = 0;
   virtual size_t                 used() = 0;
   virtual bool                   is_retained_old_region(HeapRegion* hr) = 0;

   void                           reuse_retained_old_region(EvacuationInfo& evacuation_info,
                                                            OldGCAllocRegion* old,
                                                            HeapRegion** retained);

   size_t used_unlocked() const {
     return _summary_bytes_used;
   }

   void increase_used(size_t bytes) {
     _summary_bytes_used += bytes;
   }

   void decrease_used(size_t bytes) {
     assert(_summary_bytes_used >= bytes,
            err_msg("invariant: _summary_bytes_used: "SIZE_FORMAT" should be >= bytes: "SIZE_FORMAT,
                _summary_bytes_used, bytes));
     _summary_bytes_used -= bytes;
   }

   void set_used(size_t bytes) {
     _summary_bytes_used = bytes;
   }

   virtual HeapRegion* new_heap_region(uint hrs_index,
                                       G1BlockOffsetSharedArray* sharedOffsetArray,
                                       MemRegion mr) {
     return new HeapRegion(hrs_index, sharedOffsetArray, mr);
   }
};

// The default allocator for G1.
class G1DefaultAllocator : public G1Allocator {
protected:
  // Alloc region used to satisfy mutator allocation requests.
  MutatorAllocRegion _mutator_alloc_region;

  // Alloc region used to satisfy allocation requests by the GC for
  // survivor objects.
  SurvivorGCAllocRegion _survivor_gc_alloc_region;

  // Alloc region used to satisfy allocation requests by the GC for
  // old objects.
  OldGCAllocRegion _old_gc_alloc_region;

  HeapRegion* _retained_old_gc_alloc_region;
public:
  G1DefaultAllocator(G1CollectedHeap* heap) : G1Allocator(heap), _retained_old_gc_alloc_region(NULL) { }

  virtual void init_mutator_alloc_region();
  virtual void release_mutator_alloc_region();

  virtual void init_gc_alloc_regions(EvacuationInfo& evacuation_info);
  virtual void release_gc_alloc_regions(uint no_of_gc_workers, EvacuationInfo& evacuation_info);
  virtual void abandon_gc_alloc_regions();

  virtual bool is_retained_old_region(HeapRegion* hr) {
    return _retained_old_gc_alloc_region == hr;
  }

  virtual MutatorAllocRegion* mutator_alloc_region(AllocationContext_t context) {
    return &_mutator_alloc_region;
  }

  virtual SurvivorGCAllocRegion* survivor_gc_alloc_region(AllocationContext_t context) {
    return &_survivor_gc_alloc_region;
  }

  virtual OldGCAllocRegion* old_gc_alloc_region(AllocationContext_t context) {
    return &_old_gc_alloc_region;
  }

  virtual size_t used() {
    assert(Heap_lock->owner() != NULL,
           "Should be owned on this thread's behalf.");
    size_t result = _summary_bytes_used;

    // Read only once in case it is set to NULL concurrently
    HeapRegion* hr = mutator_alloc_region(AllocationContext::current())->get();
    if (hr != NULL) {
      result += hr->used();
    }
    return result;
  }
};

class G1ParGCAllocBuffer: public ParGCAllocBuffer {
private:
  bool _retired;

public:
  G1ParGCAllocBuffer(size_t gclab_word_size);
  virtual ~G1ParGCAllocBuffer() {
    guarantee(_retired, "Allocation buffer has not been retired");
  }

  virtual void set_buf(HeapWord* buf) {
    ParGCAllocBuffer::set_buf(buf);
    _retired = false;
  }

  virtual void retire(bool end_of_gc, bool retain) {
    if (_retired) {
      return;
    }
    ParGCAllocBuffer::retire(end_of_gc, retain);
    _retired = true;
  }
};

class G1ParGCAllocator : public CHeapObj<mtGC> {
  friend class G1ParScanThreadState;
protected:
  G1CollectedHeap* _g1h;

  // The survivor alignment in effect in bytes.
  // == 0 : don't align survivors
  // != 0 : align survivors to that alignment
  // These values were chosen to favor the non-alignment case since some
  // architectures have a special compare against zero instructions.
  const uint _survivor_alignment_bytes;

  size_t _alloc_buffer_waste;
  size_t _undo_waste;

  void add_to_alloc_buffer_waste(size_t waste) { _alloc_buffer_waste += waste; }
  void add_to_undo_waste(size_t waste)         { _undo_waste += waste; }

  virtual void retire_alloc_buffers() = 0;
  virtual G1ParGCAllocBuffer* alloc_buffer(InCSetState dest, AllocationContext_t context) = 0;

  // Calculate the survivor space object alignment in bytes. Returns that or 0 if
  // there are no restrictions on survivor alignment.
  static uint calc_survivor_alignment_bytes() {
    assert(SurvivorAlignmentInBytes >= ObjectAlignmentInBytes, "sanity");
    if (SurvivorAlignmentInBytes == ObjectAlignmentInBytes) {
      // No need to align objects in the survivors differently, return 0
      // which means "survivor alignment is not used".
      return 0;
    } else {
      assert(SurvivorAlignmentInBytes > 0, "sanity");
      return SurvivorAlignmentInBytes;
    }
  }

public:
  G1ParGCAllocator(G1CollectedHeap* g1h) :
    _g1h(g1h), _survivor_alignment_bytes(calc_survivor_alignment_bytes()),
    _alloc_buffer_waste(0), _undo_waste(0) {
  }

  static G1ParGCAllocator* create_allocator(G1CollectedHeap* g1h);

  size_t alloc_buffer_waste() { return _alloc_buffer_waste; }
  size_t undo_waste() {return _undo_waste; }

  // Allocate word_sz words in dest, either directly into the regions or by
  // allocating a new PLAB. Returns the address of the allocated memory, NULL if
  // not successful.
  HeapWord* allocate_direct_or_new_plab(InCSetState dest,
                                        size_t word_sz,
                                        AllocationContext_t context);

  // Allocate word_sz words in the PLAB of dest.  Returns the address of the
  // allocated memory, NULL if not successful.
  HeapWord* plab_allocate(InCSetState dest,
                          size_t word_sz,
                          AllocationContext_t context) {
    G1ParGCAllocBuffer* buffer = alloc_buffer(dest, context);
    if (_survivor_alignment_bytes == 0) {
      return buffer->allocate(word_sz);
    } else {
      return buffer->allocate_aligned(word_sz, _survivor_alignment_bytes);
    }
  }

  HeapWord* allocate(InCSetState dest, size_t word_sz,
                     AllocationContext_t context) {
    HeapWord* const obj = plab_allocate(dest, word_sz, context);
    if (obj != NULL) {
      return obj;
    }
    return allocate_direct_or_new_plab(dest, word_sz, context);
  }

  void undo_allocation(InCSetState dest, HeapWord* obj, size_t word_sz, AllocationContext_t context) {
    if (alloc_buffer(dest, context)->contains(obj)) {
      assert(alloc_buffer(dest, context)->contains(obj + word_sz - 1),
             "should contain whole object");
      alloc_buffer(dest, context)->undo_allocation(obj, word_sz);
    } else {
      CollectedHeap::fill_with_object(obj, word_sz);
      add_to_undo_waste(word_sz);
    }
  }
};

class G1DefaultParGCAllocator : public G1ParGCAllocator {
  G1ParGCAllocBuffer  _surviving_alloc_buffer;
  G1ParGCAllocBuffer  _tenured_alloc_buffer;
  G1ParGCAllocBuffer* _alloc_buffers[InCSetState::Num];

public:
  G1DefaultParGCAllocator(G1CollectedHeap* g1h);

  virtual G1ParGCAllocBuffer* alloc_buffer(InCSetState dest, AllocationContext_t context) {
    assert(dest.is_valid(),
           err_msg("Allocation buffer index out-of-bounds: " CSETSTATE_FORMAT, dest.value()));
    assert(_alloc_buffers[dest.value()] != NULL,
           err_msg("Allocation buffer is NULL: " CSETSTATE_FORMAT, dest.value()));
    return _alloc_buffers[dest.value()];
  }

  virtual void retire_alloc_buffers() ;
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_G1ALLOCATOR_HPP

/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1ALLOCATOR_HPP
#define SHARE_GC_G1_G1ALLOCATOR_HPP

#include "gc/g1/g1AllocRegion.hpp"
#include "gc/g1/g1HeapRegionAttr.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/plab.hpp"

class G1EvacInfo;
class G1NUMA;

// Interface to keep track of which regions G1 is currently allocating into. Provides
// some accessors (e.g. allocating into them, or getting their occupancy).
// Also keeps track of retained regions across GCs.
class G1Allocator : public CHeapObj<mtGC> {
  friend class VMStructs;

private:
  G1CollectedHeap* _g1h;
  G1NUMA* _numa;

  bool _survivor_is_full;
  bool _old_is_full;

  // The number of MutatorAllocRegions used, one per memory node.
  size_t _num_alloc_regions;

  // Alloc region used to satisfy mutator allocation requests.
  MutatorAllocRegion* _mutator_alloc_regions;

  // Alloc region used to satisfy allocation requests by the GC for
  // survivor objects.
  SurvivorGCAllocRegion* _survivor_gc_alloc_regions;

  // Alloc region used to satisfy allocation requests by the GC for
  // old objects.
  OldGCAllocRegion _old_gc_alloc_region;

  G1HeapRegion* _retained_old_gc_alloc_region;

  bool survivor_is_full() const;
  bool old_is_full() const;

  void set_survivor_full();
  void set_old_full();

  void reuse_retained_old_region(G1EvacInfo* evacuation_info,
                                 OldGCAllocRegion* old,
                                 G1HeapRegion** retained);

  // Accessors to the allocation regions.
  inline MutatorAllocRegion* mutator_alloc_region(uint node_index);
  inline SurvivorGCAllocRegion* survivor_gc_alloc_region(uint node_index);
  inline OldGCAllocRegion* old_gc_alloc_region();

  // Allocation attempt during GC for a survivor object / PLAB.
  HeapWord* survivor_attempt_allocation(size_t min_word_size,
                                        size_t desired_word_size,
                                        size_t* actual_word_size,
                                        uint node_index);

  // Allocation attempt during GC for an old object / PLAB.
  HeapWord* old_attempt_allocation(size_t min_word_size,
                                   size_t desired_word_size,
                                   size_t* actual_word_size);

  // Node index of current thread.
  inline uint current_node_index() const;

public:
  G1Allocator(G1CollectedHeap* heap);
  ~G1Allocator();

  uint num_nodes() { return (uint)_num_alloc_regions; }

#ifdef ASSERT
  // Do we currently have an active mutator region to allocate into?
  bool has_mutator_alloc_region();
#endif

  void init_mutator_alloc_regions();
  void release_mutator_alloc_regions();

  void init_gc_alloc_regions(G1EvacInfo* evacuation_info);
  void release_gc_alloc_regions(G1EvacInfo* evacuation_info);
  void abandon_gc_alloc_regions();
  bool is_retained_old_region(G1HeapRegion* hr);

  // Allocate blocks of memory during mutator time.

  // Attempt allocation in the current alloc region.
  inline HeapWord* attempt_allocation(size_t min_word_size,
                                      size_t desired_word_size,
                                      size_t* actual_word_size);

  // This is to be called when holding an appropriate lock. It first tries in the
  // current allocation region, and then attempts an allocation using a new region.
  inline HeapWord* attempt_allocation_locked(size_t word_size);

  size_t unsafe_max_tlab_alloc();
  size_t used_in_alloc_regions();

  // Allocate blocks of memory during garbage collection. Will ensure an
  // allocation region, either by picking one or expanding the
  // heap, and then allocate a block of the given size. The block
  // may not be a humongous - it must fit into a single heap region.
  HeapWord* par_allocate_during_gc(G1HeapRegionAttr dest,
                                   size_t word_size,
                                   uint node_index);

  HeapWord* par_allocate_during_gc(G1HeapRegionAttr dest,
                                   size_t min_word_size,
                                   size_t desired_word_size,
                                   size_t* actual_word_size,
                                   uint node_index);
};

// Manages the PLABs used during garbage collection. Interface for allocation from PLABs.
// Needs to handle multiple contexts, extra alignment in any "survivor" area and some
// statistics.
class G1PLABAllocator : public CHeapObj<mtGC> {
  friend class G1ParScanThreadState;
private:
  typedef G1HeapRegionAttr::region_type_t region_type_t;

  G1CollectedHeap* _g1h;
  G1Allocator* _allocator;

  // Collects per-destination information (e.g. young, old gen) about current PLAB
  // and statistics about it.
  struct PLABData {
    PLAB** _alloc_buffer;

    size_t _direct_allocated;             // Number of words allocated directly (not counting PLAB allocation).
    size_t _num_plab_fills;               // Number of PLAB refills experienced so far.
    size_t _num_direct_allocations;       // Number of direct allocations experienced so far.

    size_t _plab_fill_counter;            // How many PLAB refills left until boosting.
    size_t _cur_desired_plab_size;        // Current desired PLAB size incorporating eventual boosting.

    uint _num_alloc_buffers;              // The number of PLABs for this destination.

    PLABData();
    ~PLABData();

    void initialize(uint num_alloc_buffers, size_t desired_plab_size, size_t tolerated_refills);

    // Should we actually boost the PLAB size?
    // The _plab_refill_counter reset value encodes the ResizePLAB flag value already, so no
    // need to check here.
    bool should_boost() const { return _plab_fill_counter == 0; }

    void notify_plab_refill(size_t tolerated_refills, size_t next_plab_size);

  } _dest_data[G1HeapRegionAttr::Num];

  // The amount of PLAB refills tolerated until boosting PLAB size.
  // This value is the same for all generations because they all use the same
  // resizing logic.
  size_t _tolerated_refills;

  void flush_and_retire_stats(uint num_workers);
  inline PLAB* alloc_buffer(G1HeapRegionAttr dest, uint node_index) const;
  inline PLAB* alloc_buffer(region_type_t dest, uint node_index) const;

  // Returns the number of allocation buffers for the given dest.
  // There is only 1 buffer for Old while Young may have multiple buffers depending on
  // active NUMA nodes.
  inline uint alloc_buffers_length(region_type_t dest) const;

  bool may_throw_away_buffer(size_t const allocation_word_sz, size_t const buffer_size) const;
public:
  G1PLABAllocator(G1Allocator* allocator);

  size_t waste() const;
  size_t undo_waste() const;
  size_t plab_size(G1HeapRegionAttr which) const;

  // Allocate word_sz words in dest, either directly into the regions or by
  // allocating a new PLAB. Returns the address of the allocated memory, null if
  // not successful. Plab_refill_failed indicates whether an attempt to refill the
  // PLAB failed or not.
  HeapWord* allocate_direct_or_new_plab(G1HeapRegionAttr dest,
                                        size_t word_sz,
                                        bool* plab_refill_failed,
                                        uint node_index);

  // Allocate word_sz words in the PLAB of dest.  Returns the address of the
  // allocated memory, null if not successful.
  inline HeapWord* plab_allocate(G1HeapRegionAttr dest,
                                 size_t word_sz,
                                 uint node_index);

  inline HeapWord* allocate(G1HeapRegionAttr dest,
                            size_t word_sz,
                            bool* refill_failed,
                            uint node_index);

  void undo_allocation(G1HeapRegionAttr dest, HeapWord* obj, size_t word_sz, uint node_index);
};

#endif // SHARE_GC_G1_G1ALLOCATOR_HPP

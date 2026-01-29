/*
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALHEAP
#define SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALHEAP

#include "gc/shenandoah/shenandoahHeap.hpp"
#include "memory/universe.hpp"
#include "utilities/checkedCast.hpp"

class PLAB;
class ShenandoahRegulatorThread;
class ShenandoahGenerationalControlThread;
class ShenandoahAgeCensus;

class ShenandoahGenerationalHeap : public ShenandoahHeap {
  void stop() override;

public:
  explicit ShenandoahGenerationalHeap(ShenandoahCollectorPolicy* policy);
  void post_initialize() override;
  void initialize_heuristics() override;
  void post_initialize_heuristics() override;

  static ShenandoahGenerationalHeap* heap() {
    assert(ShenandoahCardBarrier, "Should have card barrier to use generational heap");
    CollectedHeap* heap = Universe::heap();
    return cast(heap);
  }

  static ShenandoahGenerationalHeap* cast(CollectedHeap* heap) {
    assert(ShenandoahCardBarrier, "Should have card barrier to use generational heap");
    return checked_cast<ShenandoahGenerationalHeap*>(heap);
  }

  void print_init_logger() const override;

  size_t unsafe_max_tlab_alloc() const override;

private:
  // ---------- Evacuations and Promotions
  //
  // True when regions and objects should be aged during the current cycle
  ShenandoahSharedFlag  _is_aging_cycle;
  // Age census used for adapting tenuring threshold
  ShenandoahAgeCensus* _age_census;

public:
  void set_aging_cycle(bool cond) {
    _is_aging_cycle.set_cond(cond);
  }

  inline bool is_aging_cycle() const {
    return _is_aging_cycle.is_set();
  }

  // Return the age census object for young gen
  ShenandoahAgeCensus* age_census() const {
    return _age_census;
  }

  inline bool is_tenurable(const ShenandoahHeapRegion* r) const;

  // Ages regions that haven't been used for allocations in the current cycle.
  // Resets ages for regions that have been used for allocations.
  void update_region_ages(ShenandoahMarkingContext* ctx);

  oop evacuate_object(oop p, Thread* thread) override;

  template<ShenandoahAffiliation FROM_REGION, ShenandoahAffiliation TO_REGION>
  oop try_evacuate_object(oop p, Thread* thread, uint from_region_age);

  // In the generational mode, we will use these two functions for young, mixed, and global collections.
  // For young and mixed, the generation argument will be the young generation, otherwise it will be the global generation.
  void evacuate_collection_set(ShenandoahGeneration* generation, bool concurrent) override;
  void promote_regions_in_place(ShenandoahGeneration* generation, bool concurrent);

  size_t plab_min_size() const { return _min_plab_size; }
  size_t plab_max_size() const { return _max_plab_size; }

  void retire_plab(PLAB* plab);
  void retire_plab(PLAB* plab, Thread* thread);

  // ---------- Update References
  //
  // In the generational mode, we will use this function for young, mixed, and global collections.
  // For young and mixed, the generation argument will be the young generation, otherwise it will be the global generation.
  void update_heap_references(ShenandoahGeneration* generation, bool concurrent) override;
  void final_update_refs_update_region_states() override;

private:
  HeapWord* allocate_from_plab(Thread* thread, size_t size, bool is_promotion);
  HeapWord* allocate_from_plab_slow(Thread* thread, size_t size, bool is_promotion);
  HeapWord* allocate_new_plab(size_t min_size, size_t word_size, size_t* actual_size);

  const size_t _min_plab_size;
  const size_t _max_plab_size;

  static size_t calculate_min_plab();
  static size_t calculate_max_plab();

public:
  // ---------- Serviceability
  //
  void initialize_serviceability() override;
  GrowableArray<MemoryPool*> memory_pools() override;

  ShenandoahRegulatorThread* regulator_thread() const { return _regulator_thread;  }

  void gc_threads_do(ThreadClosure* tcl) const override;

  bool requires_barriers(stackChunkOop obj) const override;

  // Zeros out the evacuation and promotion reserves
  void reset_generation_reserves();

  // Computes the optimal size for the old generation, represented as a surplus or deficit of old regions
  void compute_old_generation_balance(size_t old_xfer_limit, size_t old_trashed_regions, size_t young_trashed_regions);

  // Balances generations, coalesces and fills old regions if necessary
  void complete_degenerated_cycle();
  void complete_concurrent_cycle();
private:
  void initialize_controller() override;
  void entry_global_coalesce_and_fill();

  // Makes old regions parsable. This will also rebuild card offsets, which is necessary if classes were unloaded
  void coalesce_and_fill_old_regions(bool concurrent);

  ShenandoahRegulatorThread* _regulator_thread;

  MemoryPool* _young_gen_memory_pool;
  MemoryPool* _old_gen_memory_pool;
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHGENERATIONALHEAP

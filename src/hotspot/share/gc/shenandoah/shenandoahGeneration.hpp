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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHGENERATION_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHGENERATION_HPP

#include "memory/allocation.hpp"
#include "gc/shenandoah/heuristics/shenandoahOldHeuristics.hpp"
#include "gc/shenandoah/heuristics/shenandoahSpaceInfo.hpp"
#include "gc/shenandoah/shenandoahGenerationType.hpp"
#include "gc/shenandoah/shenandoahLock.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"

class ShenandoahHeapRegion;
class ShenandoahHeapRegionClosure;
class ShenandoahReferenceProcessor;
class ShenandoahHeap;
class ShenandoahMode;

class ShenandoahGeneration : public CHeapObj<mtGC>, public ShenandoahSpaceInfo {
  friend class VMStructs;
private:
  ShenandoahGenerationType const _type;

  // Marking task queues and completeness
  ShenandoahObjToScanQueueSet* _task_queues;
  ShenandoahSharedFlag _is_marking_complete;

  ShenandoahReferenceProcessor* const _ref_processor;

  size_t _affiliated_region_count;

  // How much free memory is left in the last region of humongous objects.
  // This is _not_ included in used, but it _is_ deducted from available,
  // which gives the heuristics a more accurate view of how much memory remains
  // for allocation. This figure is also included the heap status logging.
  // The units are bytes. The value is only changed on a safepoint or under the
  // heap lock.
  size_t _humongous_waste;

protected:
  // Usage

  volatile size_t _used;
  volatile size_t _bytes_allocated_since_gc_start;
  size_t _max_capacity;
  size_t _soft_max_capacity;

  ShenandoahHeuristics* _heuristics;

private:
  // Compute evacuation budgets prior to choosing collection set.
  void compute_evacuation_budgets(ShenandoahHeap* heap,
                                  bool* preselected_regions,
                                  ShenandoahCollectionSet* collection_set,
                                  size_t& consumed_by_advance_promotion);

  // Adjust evacuation budgets after choosing collection set.
  void adjust_evacuation_budgets(ShenandoahHeap* heap,
                                 ShenandoahCollectionSet* collection_set,
                                 size_t consumed_by_advance_promotion);

  // Preselect for inclusion into the collection set regions whose age is
  // at or above tenure age and which contain more than ShenandoahOldGarbageThreshold
  // amounts of garbage.
  //
  // Returns bytes of old-gen memory consumed by selected aged regions
  size_t select_aged_regions(size_t old_available,
                             size_t num_regions, bool
                             candidate_regions_for_promotion_by_copy[]);

  size_t available(size_t capacity) const;

 public:
  ShenandoahGeneration(ShenandoahGenerationType type,
                       uint max_workers,
                       size_t max_capacity,
                       size_t soft_max_capacity);
  ~ShenandoahGeneration();

  bool is_young() const  { return _type == YOUNG; }
  bool is_old() const    { return _type == OLD; }
  bool is_global() const { return _type == GLOBAL_GEN || _type == GLOBAL_NON_GEN; }

  inline ShenandoahGenerationType type() const { return _type; }

  inline ShenandoahHeuristics* heuristics() const { return _heuristics; }

  ShenandoahReferenceProcessor* ref_processor() { return _ref_processor; }

  virtual ShenandoahHeuristics* initialize_heuristics(ShenandoahMode* gc_mode);

  size_t soft_max_capacity() const override { return _soft_max_capacity; }
  size_t max_capacity() const override      { return _max_capacity; }
  virtual size_t used_regions() const;
  virtual size_t used_regions_size() const;
  virtual size_t free_unaffiliated_regions() const;
  size_t used() const override { return _used; }
  size_t available() const override;
  size_t available_with_reserve() const;

  // Returns the memory available based on the _soft_ max heap capacity (soft_max_heap - used).
  // The soft max heap size may be adjusted lower than the max heap size to cause the trigger
  // to believe it has less memory available than is _really_ available. Lowering the soft
  // max heap size will cause the adaptive heuristic to run more frequent cycles.
  size_t soft_available() const override;

  size_t bytes_allocated_since_gc_start() const override;
  void reset_bytes_allocated_since_gc_start();
  void increase_allocated(size_t bytes);

  // These methods change the capacity of the region by adding or subtracting the given number of bytes from the current
  // capacity.
  void increase_capacity(size_t increment);
  void decrease_capacity(size_t decrement);

  void log_status(const char* msg) const;

  // Used directly by FullGC
  void reset_mark_bitmap();

  // Used by concurrent and degenerated GC to reset remembered set.
  void swap_remembered_set();

  // Update the read cards with the state of the write table (write table is not cleared).
  void merge_write_table();

  // Called before init mark, expected to prepare regions for marking.
  virtual void prepare_gc();

  // Called during final mark, chooses collection set, rebuilds free set.
  virtual void prepare_regions_and_collection_set(bool concurrent);

  // Cancel marking (used by Full collect and when cancelling cycle).
  virtual void cancel_marking();

  // Return true if this region is affiliated with this generation.
  virtual bool contains(ShenandoahHeapRegion* region) const = 0;

  // Return true if this object is affiliated with this generation.
  virtual bool contains(oop obj) const = 0;

  // Apply closure to all regions affiliated with this generation.
  virtual void parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) = 0;

  // Apply closure to all regions affiliated with this generation (single threaded).
  virtual void heap_region_iterate(ShenandoahHeapRegionClosure* cl) = 0;

  // This is public to support cancellation of marking when a Full cycle is started.
  virtual void set_concurrent_mark_in_progress(bool in_progress) = 0;

  // Check the bitmap only for regions belong to this generation.
  bool is_bitmap_clear();

  // We need to track the status of marking for different generations.
  bool is_mark_complete();
  virtual void set_mark_complete();
  virtual void set_mark_incomplete();

  ShenandoahMarkingContext* complete_marking_context();

  // Task queues
  ShenandoahObjToScanQueueSet* task_queues() const { return _task_queues; }
  virtual void reserve_task_queues(uint workers);
  virtual ShenandoahObjToScanQueueSet* old_gen_task_queues() const;

  // Scan remembered set at start of concurrent young-gen marking.
  void scan_remembered_set(bool is_concurrent);

  // Return the updated value of affiliated_region_count
  size_t increment_affiliated_region_count();

  // Return the updated value of affiliated_region_count
  size_t decrement_affiliated_region_count();

  // Return the updated value of affiliated_region_count
  size_t increase_affiliated_region_count(size_t delta);

  // Return the updated value of affiliated_region_count
  size_t decrease_affiliated_region_count(size_t delta);

  void establish_usage(size_t num_regions, size_t num_bytes, size_t humongous_waste);

  void increase_used(size_t bytes);
  void decrease_used(size_t bytes);

  void increase_humongous_waste(size_t bytes);
  void decrease_humongous_waste(size_t bytes);
  size_t get_humongous_waste() const { return _humongous_waste; }

  virtual bool is_concurrent_mark_in_progress() = 0;
  void confirm_heuristics_mode();

  virtual void record_success_concurrent(bool abbreviated);
  virtual void record_success_degenerated();
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHGENERATION_HPP

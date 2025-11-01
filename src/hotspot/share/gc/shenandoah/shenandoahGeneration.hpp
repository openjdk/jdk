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

#include "gc/shenandoah/heuristics/shenandoahSpaceInfo.hpp"
#include "gc/shenandoah/shenandoahAffiliation.hpp"
#include "gc/shenandoah/shenandoahFreeSet.hpp"
#include "gc/shenandoah/shenandoahGenerationType.hpp"
#include "gc/shenandoah/shenandoahLock.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"
#include "memory/allocation.hpp"

class ShenandoahCollectionSet;
class ShenandoahHeap;
class ShenandoahHeapRegion;
class ShenandoahHeapRegionClosure;
class ShenandoahHeuristics;
class ShenandoahMode;
class ShenandoahReferenceProcessor;

class ShenandoahGeneration : public CHeapObj<mtGC>, public ShenandoahSpaceInfo {
  friend class VMStructs;
private:
  ShenandoahGenerationType const _type;

  // Marking task queues and completeness
  ShenandoahObjToScanQueueSet* _task_queues;
  ShenandoahSharedFlag _is_marking_complete;

  ShenandoahReferenceProcessor* const _ref_processor;

  // Bytes reserved within this generation to hold evacuated objects from the collection set
  size_t _evacuation_reserve;

protected:
  ShenandoahFreeSet* _free_set;
  ShenandoahHeuristics* _heuristics;

private:
  // Compute evacuation budgets prior to choosing collection set.
  void compute_evacuation_budgets(ShenandoahHeap* heap);

  // Adjust evacuation budgets after choosing collection set.
  void adjust_evacuation_budgets(ShenandoahHeap* heap,
                                 ShenandoahCollectionSet* collection_set);

  // Preselect for possible inclusion into the collection set exactly the most
  // garbage-dense regions, including those that satisfy criteria 1 & 2 below,
  // and whose live bytes will fit within old_available budget:
  // Criterion 1. region age >= tenuring threshold
  // Criterion 2. region garbage percentage > ShenandoahOldGarbageThreshold
  //
  // Identifies regions eligible for promotion in place,
  // being those of at least tenuring_threshold age that have lower garbage
  // density.
  //
  // Updates promotion_potential and pad_for_promote_in_place fields
  // of the heap. Returns bytes of live object memory in the preselected
  // regions, which are marked in the preselected_regions() indicator
  // array of the heap's collection set, which should be initialized
  // to false.
  size_t select_aged_regions(size_t old_promotion_reserve);

  // Return available assuming that we can allocate no more than capacity bytes within this generation.
  size_t available(size_t capacity) const;

 public:
  ShenandoahGeneration(ShenandoahGenerationType type,
                       uint max_workers);
  ~ShenandoahGeneration();

  inline bool is_young() const                 { return _type == YOUNG; }
  inline bool is_old() const                   { return _type == OLD; }
  inline bool is_global() const                { return _type == GLOBAL || _type == NON_GEN; }
  inline ShenandoahGenerationType type() const { return _type; }

  // see description in field declaration
  void set_evacuation_reserve(size_t new_val);
  size_t get_evacuation_reserve() const;
  void augment_evacuation_reserve(size_t increment);

  virtual ShenandoahHeuristics* heuristics() const { return _heuristics; }

  ShenandoahReferenceProcessor* ref_processor() { return _ref_processor; }

  virtual ShenandoahHeuristics* initialize_heuristics(ShenandoahMode* gc_mode);

  virtual void post_initialize(ShenandoahHeap* heap);

  virtual size_t bytes_allocated_since_gc_start() const override = 0;
  virtual size_t used() const override = 0;
  virtual size_t used_regions() const = 0;
  virtual size_t used_regions_size() const = 0;
  virtual size_t get_humongous_waste() const = 0;
  virtual size_t free_unaffiliated_regions() const = 0;
  virtual size_t get_affiliated_region_count() const = 0;
  virtual size_t max_capacity() const override = 0;

  size_t available() const override;
  size_t available_with_reserve() const;

  // Returns the memory available based on the _soft_ max heap capacity (soft_max_heap - used).
  // The soft max heap size may be adjusted lower than the max heap size to cause the trigger
  // to believe it has less memory available than is _really_ available. Lowering the soft
  // max heap size will cause the adaptive heuristic to run more frequent cycles.
  size_t soft_available() const override;

  void log_status(const char* msg) const;

  // Used directly by FullGC
  template <bool FOR_CURRENT_CYCLE, bool FULL_GC = false>
  void reset_mark_bitmap();

  // Used by concurrent and degenerated GC to reset remembered set.
  void swap_card_tables();

  // Update the read cards with the state of the write table (write table is not cleared).
  void merge_write_table();

  // Called before init mark, expected to prepare regions for marking.
  virtual void prepare_gc();

  // Called during final mark, chooses collection set, rebuilds free set.
  virtual void prepare_regions_and_collection_set(bool concurrent);

  // Cancel marking (used by Full collect and when cancelling cycle).
  virtual void cancel_marking();

  virtual bool contains(ShenandoahAffiliation affiliation) const = 0;

  // Return true if this region is affiliated with this generation.
  virtual bool contains(ShenandoahHeapRegion* region) const = 0;

  // Return true if this object is affiliated with this generation.
  virtual bool contains(oop obj) const = 0;

  // Apply closure to all regions affiliated with this generation.
  virtual void parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) = 0;

  // Apply closure to all regions affiliated with this generation (include free regions);
  virtual void parallel_heap_region_iterate_free(ShenandoahHeapRegionClosure* cl);

  // Apply closure to all regions affiliated with this generation (single threaded).
  virtual void heap_region_iterate(ShenandoahHeapRegionClosure* cl) = 0;

  // This is public to support cancellation of marking when a Full cycle is started.
  virtual void set_concurrent_mark_in_progress(bool in_progress) = 0;

  // Check the bitmap only for regions belong to this generation.
  bool is_bitmap_clear();

  // We need to track the status of marking for different generations.
  bool is_mark_complete() const { return _is_marking_complete.is_set(); }
  virtual void set_mark_complete();
  virtual void set_mark_incomplete();

  ShenandoahMarkingContext* complete_marking_context();

  // Task queues
  ShenandoahObjToScanQueueSet* task_queues() const { return _task_queues; }
  virtual void reserve_task_queues(uint workers);
  virtual ShenandoahObjToScanQueueSet* old_gen_task_queues() const;

  // Scan remembered set at start of concurrent young-gen marking.
  void scan_remembered_set(bool is_concurrent);

  virtual bool is_concurrent_mark_in_progress() = 0;
  void confirm_heuristics_mode();

  virtual void record_success_concurrent(bool abbreviated);
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHGENERATION_HPP

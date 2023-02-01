/*
 * Copyright (c) 2020, 2021 Amazon.com, Inc. and/or its affiliates. All rights reserved.
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
#include "gc/shenandoah/mode/shenandoahGenerationalMode.hpp"
#include "gc/shenandoah/shenandoahLock.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.hpp"

class ShenandoahHeapRegion;
class ShenandoahHeapRegionClosure;
class ShenandoahReferenceProcessor;
class ShenandoahHeap;

class ShenandoahGeneration : public CHeapObj<mtGC> {
private:
  GenerationMode const _generation_mode;

  // Marking task queues and completeness
  ShenandoahObjToScanQueueSet* _task_queues;
  ShenandoahSharedFlag _is_marking_complete;

  ShenandoahReferenceProcessor* const _ref_processor;

  double _collection_thread_time_s;

protected:
  // Usage
  size_t _affiliated_region_count;
  volatile size_t _used;
  volatile size_t _bytes_allocated_since_gc_start;
  size_t _max_capacity;
  size_t _soft_max_capacity;

  size_t _adjusted_capacity;

  ShenandoahHeuristics* _heuristics;

private:
  // Compute evacuation budgets prior to choosing collection set.
  void compute_evacuation_budgets(ShenandoahHeap* heap, bool* preselected_regions, ShenandoahCollectionSet* collection_set,
                                  size_t &consumed_by_advance_promotion);

  // Adjust evacuation budgets after choosing collection set.
  void adjust_evacuation_budgets(ShenandoahHeap* heap, ShenandoahCollectionSet* collection_set,
                                 size_t consumed_by_advance_promotion);

 public:
  ShenandoahGeneration(GenerationMode generation_mode, uint max_workers, size_t max_capacity, size_t soft_max_capacity);
  ~ShenandoahGeneration();

  bool is_young() const  { return _generation_mode == YOUNG; }
  bool is_old() const    { return _generation_mode == OLD; }
  bool is_global() const { return _generation_mode == GLOBAL; }

  inline GenerationMode generation_mode() const { return _generation_mode; }

  inline ShenandoahHeuristics* heuristics() const { return _heuristics; }

  ShenandoahReferenceProcessor* ref_processor() { return _ref_processor; }

  virtual const char* name() const = 0;

  virtual ShenandoahHeuristics* initialize_heuristics(ShenandoahMode* gc_mode);

  virtual size_t soft_max_capacity() const { return _soft_max_capacity; }
  virtual size_t max_capacity() const      { return _max_capacity; }
  virtual size_t used_regions() const;
  virtual size_t used_regions_size() const;
  virtual size_t free_unaffiliated_regions() const;
  virtual size_t used() const { return _used; }
  virtual size_t available() const;

  // During evacuation and update-refs, some memory may be shifted between generations.  In particular, memory
  // may be loaned by old-gen to young-gen based on the promise the loan will be promptly repaid from the memory reclaimed
  // when the current collection set is recycled.  The capacity adjustment also takes into consideration memory that is
  // set aside within each generation to hold the results of evacuation, but not promotion, into that region.  Promotions
  // into old-gen are bounded by adjusted_available() whereas evacuations into old-gen are pre-committed.
  virtual size_t adjusted_available() const;
  virtual size_t adjusted_capacity() const;

  // This is the number of FREE regions that are eligible to be affiliated with this generation according to the current
  // adjusted capacity.
  virtual size_t adjusted_unaffiliated_regions() const;

  // Both of following return new value of available
  virtual size_t adjust_available(intptr_t adjustment);
  virtual size_t unadjust_available();

  size_t bytes_allocated_since_gc_start();
  void reset_bytes_allocated_since_gc_start();
  void increase_allocated(size_t bytes);

  // These methods change the capacity of the region by adding or subtracting the given number of bytes from the current
  // capacity.
  void increase_capacity(size_t increment);
  void decrease_capacity(size_t decrement);

  void set_soft_max_capacity(size_t soft_max_capacity) {
    _soft_max_capacity = soft_max_capacity;
  }

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
  void set_mark_complete();
  void set_mark_incomplete();

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

  void clear_used();
  void increase_used(size_t bytes);
  void decrease_used(size_t bytes);

  virtual bool is_concurrent_mark_in_progress() = 0;
  void confirm_heuristics_mode();

  virtual void record_success_concurrent(bool abbreviated);
  virtual void record_success_degenerated();

  // Record the total on-cpu time a thread has spent collecting this
  // generation. This is only called by the control thread (at the start
  // of a collection) and by the VM thread at the end of the collection,
  // so there are no locking concerns.
  virtual void add_collection_time(double time_seconds);

  // This returns the accumulated collection time and resets it to zero.
  // This is used to decide which generation should be resized.
  double reset_collection_time();
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHGENERATION_HPP

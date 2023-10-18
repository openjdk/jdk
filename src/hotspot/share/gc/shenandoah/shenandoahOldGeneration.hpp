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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHOLDGENERATION_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHOLDGENERATION_HPP

#include "gc/shenandoah/shenandoahGeneration.hpp"

class ShenandoahHeapRegion;
class ShenandoahHeapRegionClosure;
class ShenandoahOldHeuristics;

class ShenandoahOldGeneration : public ShenandoahGeneration {
private:
  ShenandoahHeapRegion** _coalesce_and_fill_region_array;
  ShenandoahOldHeuristics* _old_heuristics;

  bool entry_coalesce_and_fill();
  bool coalesce_and_fill();

public:
  ShenandoahOldGeneration(uint max_queues, size_t max_capacity, size_t soft_max_capacity);

  virtual ShenandoahHeuristics* initialize_heuristics(ShenandoahMode* gc_mode) override;

  const char* name() const override {
    return "OLD";
  }

  void parallel_heap_region_iterate(ShenandoahHeapRegionClosure* cl) override;
  void heap_region_iterate(ShenandoahHeapRegionClosure* cl) override;

  bool contains(ShenandoahHeapRegion* region) const override;
  bool contains(oop obj) const override;

  void set_concurrent_mark_in_progress(bool in_progress) override;
  bool is_concurrent_mark_in_progress() override;

  virtual void prepare_gc() override;
  void prepare_regions_and_collection_set(bool concurrent) override;
  virtual void record_success_concurrent(bool abbreviated) override;
  virtual void cancel_marking() override;

  // We leave the SATB barrier on for the entirety of the old generation
  // marking phase. In some cases, this can cause a write to a perfectly
  // reachable oop to enqueue a pointer that later becomes garbage (because
  // it points at an object that is later chosen for the collection set). There are
  // also cases where the referent of a weak reference ends up in the SATB
  // and is later collected. In these cases the oop in the SATB buffer becomes
  // invalid and the _next_ cycle will crash during its marking phase. To
  // avoid this problem, we "purge" the SATB buffers during the final update
  // references phase if (and only if) an old generation mark is in progress.
  // At this stage we can safely determine if any of the oops in the SATB
  // buffer belong to trashed regions (before they are recycled). As it
  // happens, flushing a SATB queue also filters out oops which have already
  // been marked - which is the case for anything that is being evacuated
  // from the collection set.
  //
  // Alternatively, we could inspect the state of the heap and the age of the
  // object at the barrier, but we reject this approach because it is likely
  // the performance impact would be too severe.
  void transfer_pointers_from_satb();

public:
  enum State {
    IDLE, FILLING, BOOTSTRAPPING, MARKING, WAITING_FOR_EVAC, WAITING_FOR_FILL
  };

private:
  State _state;

  static const size_t FRACTIONAL_DENOMINATOR = 64536;

  // During initialization of the JVM, we search for the correct old-gen size by initally performing old-gen
  // collection when old-gen usage is 50% more (INITIAL_GROWTH_BEFORE_COMPACTION) than the initial old-gen size
  // estimate (3.125% of heap).  The next old-gen trigger occurs when old-gen grows 25% larger than its live
  // memory at the end of the first old-gen collection.  Then we trigger again when old-gen growns 12.5%
  // more than its live memory at the end of the previous old-gen collection.  Thereafter, we trigger each time
  // old-gen grows more than 12.5% following the end of its previous old-gen collection.
  static const size_t INITIAL_GROWTH_BEFORE_COMPACTION = FRACTIONAL_DENOMINATOR / 2;        //  50.0%

  // INITIAL_LIVE_FRACTION represents the initial guess of how large old-gen should be.  We estimate that old-gen
  // needs to consume 6.25% of the total heap size.  And we "pretend" that we start out with this amount of live
  // old-gen memory.  The first old-collection trigger will occur when old-gen occupies 50% more than this initial
  // approximation of the old-gen memory requirement, in other words when old-gen usage is 150% of 6.25%, which
  // is 9.375% of the total heap size.
  static const uint16_t INITIAL_LIVE_FRACTION = FRACTIONAL_DENOMINATOR / 16;                //   6.25%

  size_t _live_bytes_after_last_mark;

  // How much growth in usage before we trigger old collection, per FRACTIONAL_DENOMINATOR (65_536)
  size_t _growth_before_compaction;
  const size_t _min_growth_before_compaction;                                               // Default is 12.5%

  void validate_transition(State new_state) NOT_DEBUG_RETURN;

public:
  State state() const {
    return _state;
  }

  const char* state_name() const {
    return state_name(_state);
  }

  void transition_to(State new_state);

  size_t get_live_bytes_after_last_mark() const;
  void set_live_bytes_after_last_mark(size_t new_live);

  size_t usage_trigger_threshold() const;

  bool can_start_gc() {
    return _state == IDLE || _state == WAITING_FOR_FILL;
  }

  static const char* state_name(State state);
};


#endif //SHARE_VM_GC_SHENANDOAH_SHENANDOAHOLDGENERATION_HPP

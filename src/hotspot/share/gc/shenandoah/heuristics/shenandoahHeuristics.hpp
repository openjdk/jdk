/*
 * Copyright (c) 2018, 2019, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHHEURISTICS_HPP
#define SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHHEURISTICS_HPP

#include "gc/shenandoah/heuristics/shenandoahSpaceInfo.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "memory/allocation.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/numberSeq.hpp"

#define SHENANDOAH_ERGO_DISABLE_FLAG(name)                                  \
  do {                                                                      \
    if (FLAG_IS_DEFAULT(name) && (name)) {                                  \
      log_info(gc)("Heuristics ergonomically sets -XX:-" #name);            \
      FLAG_SET_ERGO(name, false);                                           \
    }                                                                       \
  } while (0)

#define SHENANDOAH_ERGO_ENABLE_FLAG(name)                                   \
  do {                                                                      \
    if (FLAG_IS_DEFAULT(name) && !(name)) {                                 \
      log_info(gc)("Heuristics ergonomically sets -XX:+" #name);            \
      FLAG_SET_ERGO(name, true);                                            \
    }                                                                       \
  } while (0)

#define SHENANDOAH_ERGO_OVERRIDE_DEFAULT(name, value)                       \
  do {                                                                      \
    if (FLAG_IS_DEFAULT(name)) {                                            \
      log_info(gc)("Heuristics ergonomically sets -XX:" #name "=" #value);  \
      FLAG_SET_ERGO(name, value);                                           \
    }                                                                       \
  } while (0)

class ShenandoahCollectionSet;
class ShenandoahHeapRegion;

/*
 * Shenandoah heuristics are primarily responsible for deciding when to start
 * a collection cycle and choosing which regions will be evacuated during the
 * cycle.
 */
class ShenandoahHeuristics : public CHeapObj<mtGC> {
  static const intx Concurrent_Adjust   = -1; // recover from penalties
  static const intx Degenerated_Penalty = 10; // how much to penalize average GC duration history on Degenerated GC
  static const intx Full_Penalty        = 20; // how much to penalize average GC duration history on Full GC

  // How many times can I decline a trigger opportunity without being penalized for excessive idle span before trigger?
  static const size_t Penalty_Free_Declinations = 16;

#ifdef ASSERT
  enum UnionTag {
    is_uninitialized, is_garbage, is_live_data
  };
#endif

protected:
  static const uint Moving_Average_Samples = 10; // Number of samples to store in moving averages

  bool _start_gc_is_pending;              // True denotes that GC has been triggered, so no need to trigger again.
  size_t _declined_trigger_count;         // This counts how many times since previous GC finished that this
                                          //  heuristic has answered false to should_start_gc().
  size_t _most_recent_declined_trigger_count;
                                       ;  // This represents the value of _declined_trigger_count as captured at the
                                          //  moment the most recent GC effort was triggered.  In case the most recent
                                          //  concurrent GC effort degenerates, the value of this variable allows us to
                                          //  differentiate between degeneration because heuristic was overly optimistic
                                          //  in delaying the trigger vs. degeneration for other reasons (such as the
                                          //  most recent GC triggered "immediately" after previous GC finished, but the
                                          //  free headroom has already been depleted).

  class RegionData {
    private:
    ShenandoahHeapRegion* _region;
    union {
      size_t _garbage;          // Not used by old-gen heuristics.
      size_t _live_data;        // Only used for old-gen heuristics, which prioritizes retention of _live_data over garbage reclaim
    } _region_union;
#ifdef ASSERT
    UnionTag _union_tag;
#endif
    public:

    inline void clear() {
      _region = nullptr;
      _region_union._garbage = 0;
#ifdef ASSERT
      _union_tag = is_uninitialized;
#endif
    }

    inline void set_region_and_garbage(ShenandoahHeapRegion* region, size_t garbage) {
      _region = region;
      _region_union._garbage = garbage;
#ifdef ASSERT
      _union_tag = is_garbage;
#endif
    }

    inline void set_region_and_livedata(ShenandoahHeapRegion* region, size_t live) {
      _region = region;
      _region_union._live_data = live;
#ifdef ASSERT
      _union_tag = is_live_data;
#endif
    }

    inline void update_livedata(size_t live) {
      _region_union._live_data = live;
#ifdef ASSERT
      _union_tag = is_live_data;
#endif
    }

    inline ShenandoahHeapRegion* get_region() const {
      assert(_union_tag != is_uninitialized, "Cannot fetch region from uninitialized RegionData");
      return _region;
    }

    inline size_t get_garbage() const {
      assert(_union_tag == is_garbage, "Invalid union fetch");
      return _region_union._garbage;
    }

    inline size_t get_livedata() const {
      assert(_union_tag == is_live_data, "Invalid union fetch");
      return _region_union._live_data;
    }
  };

  // Source of information about the memory space managed by this heuristic
  ShenandoahSpaceInfo* _space_info;

  // Depending on generation mode, region data represents the results of the relevant
  // most recently completed marking pass:
  //   - in GLOBAL mode, global marking pass
  //   - in OLD mode,    old-gen marking pass
  //   - in YOUNG mode,  young-gen marking pass
  //
  // Note that there is some redundancy represented in region data because
  // each instance is an array large enough to hold all regions. However,
  // any region in young-gen is not in old-gen. And any time we are
  // making use of the GLOBAL data, there is no need to maintain the
  // YOUNG or OLD data. Consider this redundancy of data structure to
  // have negligible cost unless proven otherwise.
  RegionData* _region_data;

  size_t _guaranteed_gc_interval;

  double _cycle_start;
  double _last_cycle_end;

  size_t _gc_times_learned;
  intx _gc_time_penalties;
  TruncatedSeq* _gc_cycle_time_history;

  // There may be many threads that contend to set this flag
  ShenandoahSharedFlag _metaspace_oom;

  static int compare_by_garbage(RegionData a, RegionData b);

  // This is a helper function to choose_collection_set()
  virtual void choose_collection_set_from_regiondata(ShenandoahCollectionSet* set,
                                                     RegionData* data, size_t data_size,
                                                     size_t free) = 0;

  void adjust_penalty(intx step);

  inline void accept_trigger() {
    _most_recent_declined_trigger_count = _declined_trigger_count;
    _declined_trigger_count = 0;
    _start_gc_is_pending = true;
  }

  inline void decline_trigger() {
    _declined_trigger_count++;
  }

public:
  ShenandoahHeuristics(ShenandoahSpaceInfo* space_info);
  virtual ~ShenandoahHeuristics();

  void record_metaspace_oom()     { _metaspace_oom.set(); }
  void clear_metaspace_oom()      { _metaspace_oom.unset(); }
  bool has_metaspace_oom() const  { return _metaspace_oom.is_set(); }

  void set_guaranteed_gc_interval(size_t guaranteed_gc_interval) {
    _guaranteed_gc_interval = guaranteed_gc_interval;
  }

  virtual void record_cycle_start();

  virtual void record_cycle_end();

  virtual bool should_start_gc();

  inline void cancel_trigger_request() {
    _start_gc_is_pending = false;
  }

  virtual bool should_degenerate_cycle();

  virtual void record_success_concurrent();

  virtual void record_degenerated();

  virtual void record_success_full();

  virtual void record_allocation_failure_gc();

  virtual void record_requested_gc();

  // Choose the collection set, returning the number of regions that need to be transferred to the old reserve from the young
  // reserve in order to effectively evacuate the chosen collection set.  In non-generational mode, the return value is 0.
  virtual void choose_collection_set(ShenandoahCollectionSet* collection_set);

  virtual bool can_unload_classes();

  // This indicates whether or not the current cycle should unload classes.
  // It does NOT indicate that a cycle should be started.
  virtual bool should_unload_classes();

  virtual const char* name() = 0;
  virtual bool is_diagnostic() = 0;
  virtual bool is_experimental() = 0;
  virtual void initialize();

  double elapsed_cycle_time() const;

  virtual size_t force_alloc_rate_sample(size_t bytes_allocated) {
    // do nothing
    return 0;
  }

  // Format prefix and emit log message indicating a GC cycle hs been triggered
  void log_trigger(const char* fmt, ...) ATTRIBUTE_PRINTF(2, 3);
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHHEURISTICS_HPP

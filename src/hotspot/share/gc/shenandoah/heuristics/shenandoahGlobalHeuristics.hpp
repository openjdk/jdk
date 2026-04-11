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

#ifndef SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHGLOBALHEURISTICS_HPP
#define SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHGLOBALHEURISTICS_HPP

#include "gc/shenandoah/heuristics/shenandoahGenerationalHeuristics.hpp"

class ShenandoahGlobalGeneration;

enum class ShenandoahGlobalRegionDisposition {
  SKIP,
  ADD_OLD_EVAC,
  ADD_PROMO,
  ADD_YOUNG_EVAC
};

// A shared pool of evacuation reserves that can be drawn from by any
// evacuation category. Owned by ShenandoahGlobalCSetBudget; each
// ShenandoahEvacuationBudget holds a pointer to it.
struct ShenandoahSharedEvacReserve {
  size_t limit;
  size_t committed;

  ShenandoahSharedEvacReserve(size_t limit) : limit(limit), committed(0) {}
};

// Tracks the budget for a single evacuation category.
class ShenandoahEvacuationBudget {
  size_t _reserve;
  size_t _consumed;
  size_t _live_bytes;
  size_t _region_count;
  size_t _region_size_bytes;
  double _waste_factor;
  ShenandoahSharedEvacReserve* _shared;

public:
  ShenandoahEvacuationBudget(size_t reserve, double waste_factor,
                             size_t region_size_bytes,
                             ShenandoahSharedEvacReserve* shared)
    : _reserve(reserve), _consumed(0), _live_bytes(0),
      _region_count(0), _region_size_bytes(region_size_bytes),
      _waste_factor(waste_factor), _shared(shared) {}

  size_t anticipated_consumption(size_t live_bytes) const {
    return (size_t)(live_bytes * _waste_factor);
  }

  // Try to reserve 'bytes' from this budget, expanding from the shared
  // pool if necessary. On success, updates _reserve and shared->committed
  // and returns true. On failure, nothing is modified.
  bool try_reserve(size_t bytes);

  // Record that a region was accepted.
  void commit(size_t consumption, size_t live_bytes);

  // Record a raw consumption (e.g. free bytes lost from promo reserve).
  void commit_raw(size_t bytes) { _consumed += bytes; }

  size_t reserve()      const { return _reserve; }
  size_t consumed()     const { return _consumed; }
  size_t live_bytes()   const { return _live_bytes; }
  size_t region_count() const { return _region_count; }
  double waste_factor() const { return _waste_factor; }

  void add_to_reserve(size_t bytes) { _reserve += bytes; }
  void set_reserve(size_t bytes)    { _reserve = bytes; }
};

// These are the attributes of a region required to decide if it can be
// added to the collection set or not.
struct ShenandoahGlobalRegionAttributes {
  size_t garbage;
  size_t live_data_bytes;
  size_t free_bytes;
  bool   is_old;
  bool   is_tenurable;
};

// This class consolidates all of the data required to build a global
// collection set. Critically, it takes no dependencies on any classes
// that themselves depend on ShenandoahHeap. This makes it possible to
// write extensive unit tests for this complex code.
class ShenandoahGlobalCSetBudget {
  size_t _region_size_bytes;
  size_t _garbage_threshold;
  size_t _ignore_threshold;
  size_t _min_garbage;
  size_t _cur_garbage;

  ShenandoahSharedEvacReserve _shared;

public:
  ShenandoahEvacuationBudget young_evac;
  ShenandoahEvacuationBudget old_evac;
  ShenandoahEvacuationBudget promo;

  ShenandoahGlobalCSetBudget(size_t region_size_bytes,
                             size_t shared_reserves,
                             size_t garbage_threshold,
                             size_t ignore_threshold,
                             size_t min_garbage,
                             size_t young_evac_reserve, double young_waste,
                             size_t old_evac_reserve,   double old_waste,
                             size_t promo_reserve,      double promo_waste)
    : _region_size_bytes(region_size_bytes),
      _garbage_threshold(garbage_threshold),
      _ignore_threshold(ignore_threshold),
      _min_garbage(min_garbage),
      _cur_garbage(0),
      _shared(shared_reserves),
      young_evac(young_evac_reserve, young_waste, region_size_bytes, &_shared),
      old_evac(old_evac_reserve, old_waste, region_size_bytes, &_shared),
      promo(promo_reserve, promo_waste, region_size_bytes, &_shared) {}

  ShenandoahGlobalRegionDisposition try_add_region(const ShenandoahGlobalRegionAttributes& region);

  // Any remaining shared budget is given to the promotion reserve.
  void finish() {
    if (_shared.committed < _shared.limit) {
      promo.add_to_reserve(_shared.limit - _shared.committed);
    }
  }

  // Verify that the budget invariants hold after collection set selection.
  // original_total_reserves is the sum of the young, old, and promo evacuation
  // reserves as they were before the budget was constructed.
  DEBUG_ONLY(void assert_budget_constraints_hold(size_t original_total_reserves) const;)

  size_t region_size_bytes()     const { return _region_size_bytes; }
  size_t shared_reserves()       const { return _shared.limit; }
  size_t committed_from_shared() const { return _shared.committed; }
  size_t cur_garbage()           const { return _cur_garbage; }

  void set_cur_garbage(size_t g) { _cur_garbage = g; }
};

class ShenandoahGlobalHeuristics : public ShenandoahGenerationalHeuristics {
public:
  ShenandoahGlobalHeuristics(ShenandoahGlobalGeneration* generation);

  void choose_collection_set_from_regiondata(ShenandoahCollectionSet* cset,
                                             RegionData* data, size_t size,
                                             size_t actual_free) override;

private:
  void choose_global_collection_set(ShenandoahCollectionSet* cset,
                                    const ShenandoahHeuristics::RegionData* data,
                                    size_t size, size_t actual_free,
                                    size_t cur_young_garbage);
};

#endif // SHARE_GC_SHENANDOAH_HEURISTICS_SHENANDOAHGLOBALHEURISTICS_HPP

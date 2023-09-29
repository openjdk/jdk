/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1CollectionSetCandidates.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ParScanThreadState.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.inline.hpp"
#include "gc/g1/heapRegionSet.hpp"
#include "logging/logStream.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/quickSort.hpp"

G1CollectorState* G1CollectionSet::collector_state() const {
  return _g1h->collector_state();
}

G1GCPhaseTimes* G1CollectionSet::phase_times() {
  return _policy->phase_times();
}

G1CollectionSet::G1CollectionSet(G1CollectedHeap* g1h, G1Policy* policy) :
  _g1h(g1h),
  _policy(policy),
  _candidates(),
  _collection_set_regions(nullptr),
  _collection_set_cur_length(0),
  _collection_set_max_length(0),
  _eden_region_length(0),
  _survivor_region_length(0),
  _initial_old_region_length(0),
  _optional_old_regions(),
  _inc_build_state(Inactive),
  _inc_part_start(0) {
}

G1CollectionSet::~G1CollectionSet() {
  FREE_C_HEAP_ARRAY(uint, _collection_set_regions);
  abandon_all_candidates();
}

void G1CollectionSet::init_region_lengths(uint eden_cset_region_length,
                                          uint survivor_cset_region_length) {
  assert_at_safepoint_on_vm_thread();

  _eden_region_length     = eden_cset_region_length;
  _survivor_region_length = survivor_cset_region_length;

  assert((size_t)young_region_length() == _collection_set_cur_length,
         "Young region length %u should match collection set length %u", young_region_length(), _collection_set_cur_length);

  _initial_old_region_length = 0;
  _optional_old_regions.clear();
}

void G1CollectionSet::initialize(uint max_region_length) {
  guarantee(_collection_set_regions == nullptr, "Must only initialize once.");
  _collection_set_max_length = max_region_length;
  _collection_set_regions = NEW_C_HEAP_ARRAY(uint, max_region_length, mtGC);

  _candidates.initialize(max_region_length);
}

void G1CollectionSet::abandon_all_candidates() {
  _candidates.clear();
  _initial_old_region_length = 0;
  _optional_old_regions.clear();
}

void G1CollectionSet::add_old_region(HeapRegion* hr) {
  assert_at_safepoint_on_vm_thread();

  assert(_inc_build_state == Active,
         "Precondition, actively building cset or adding optional later on");
  assert(hr->is_old(), "the region should be old");

  assert(!hr->in_collection_set(), "should not already be in the collection set");
  _g1h->register_old_region_with_region_attr(hr);

  assert(_collection_set_cur_length < _collection_set_max_length, "Collection set now larger than maximum size.");
  _collection_set_regions[_collection_set_cur_length++] = hr->hrm_index();
  _initial_old_region_length++;

  _g1h->old_set_remove(hr);
}

void G1CollectionSet::start_incremental_building() {
  assert(_collection_set_cur_length == 0, "Collection set must be empty before starting a new collection set.");
  assert(_inc_build_state == Inactive, "Precondition");

  update_incremental_marker();
}

void G1CollectionSet::finalize_incremental_building() {
  assert(_inc_build_state == Active, "Precondition");
  assert(SafepointSynchronize::is_at_safepoint(), "should be at a safepoint");
}

void G1CollectionSet::clear() {
  assert_at_safepoint_on_vm_thread();
  _collection_set_cur_length = 0;
}

void G1CollectionSet::iterate(HeapRegionClosure* cl) const {
  size_t len = _collection_set_cur_length;
  OrderAccess::loadload();

  for (uint i = 0; i < len; i++) {
    HeapRegion* r = _g1h->region_at(_collection_set_regions[i]);
    bool result = cl->do_heap_region(r);
    if (result) {
      cl->set_incomplete();
      return;
    }
  }
}

void G1CollectionSet::par_iterate(HeapRegionClosure* cl,
                                  HeapRegionClaimer* hr_claimer,
                                  uint worker_id) const {
  iterate_part_from(cl, hr_claimer, 0, cur_length(), worker_id);
}

void G1CollectionSet::iterate_optional(HeapRegionClosure* cl) const {
  assert_at_safepoint();

  for (HeapRegion* r : _optional_old_regions) {
    bool result = cl->do_heap_region(r);
    guarantee(!result, "Must not cancel iteration");
  }
}

void G1CollectionSet::iterate_incremental_part_from(HeapRegionClosure* cl,
                                                    HeapRegionClaimer* hr_claimer,
                                                    uint worker_id) const {
  iterate_part_from(cl, hr_claimer, _inc_part_start, increment_length(), worker_id);
}

void G1CollectionSet::iterate_part_from(HeapRegionClosure* cl,
                                        HeapRegionClaimer* hr_claimer,
                                        size_t offset,
                                        size_t length,
                                        uint worker_id) const {
  _g1h->par_iterate_regions_array(cl,
                                  hr_claimer,
                                  &_collection_set_regions[offset],
                                  length,
                                  worker_id);
}

void G1CollectionSet::add_young_region_common(HeapRegion* hr) {
  assert(hr->is_young(), "invariant");
  assert(_inc_build_state == Active, "Precondition");

  assert(!hr->in_collection_set(), "invariant");
  _g1h->register_young_region_with_region_attr(hr);

  // We use UINT_MAX as "invalid" marker in verification.
  assert(_collection_set_cur_length < (UINT_MAX - 1),
         "Collection set is too large with %u entries", _collection_set_cur_length);
  hr->set_young_index_in_cset(_collection_set_cur_length + 1);

  assert(_collection_set_cur_length < _collection_set_max_length, "Collection set larger than maximum allowed.");
  _collection_set_regions[_collection_set_cur_length] = hr->hrm_index();
  // Concurrent readers must observe the store of the value in the array before an
  // update to the length field.
  OrderAccess::storestore();
  _collection_set_cur_length++;
}

void G1CollectionSet::add_survivor_regions(HeapRegion* hr) {
  assert(hr->is_survivor(), "Must only add survivor regions, but is %s", hr->get_type_str());
  add_young_region_common(hr);
}

void G1CollectionSet::add_eden_region(HeapRegion* hr) {
  assert(hr->is_eden(), "Must only add eden regions, but is %s", hr->get_type_str());
  add_young_region_common(hr);
}

#ifndef PRODUCT
class G1VerifyYoungAgesClosure : public HeapRegionClosure {
public:
  bool _valid;

  G1VerifyYoungAgesClosure() : HeapRegionClosure(), _valid(true) { }

  virtual bool do_heap_region(HeapRegion* r) {
    guarantee(r->is_young(), "Region must be young but is %s", r->get_type_str());

    if (!r->has_surv_rate_group()) {
      log_error(gc, verify)("## encountered young region without surv_rate_group");
      _valid = false;
    }

    if (!r->has_valid_age_in_surv_rate()) {
      log_error(gc, verify)("## encountered invalid age in young region");
      _valid = false;
    }

    return false;
  }

  bool valid() const { return _valid; }
};

bool G1CollectionSet::verify_young_ages() {
  assert_at_safepoint_on_vm_thread();

  G1VerifyYoungAgesClosure cl;
  iterate(&cl);

  if (!cl.valid()) {
    LogStreamHandle(Error, gc, verify) log;
    print(&log);
  }

  return cl.valid();
}

class G1PrintCollectionSetDetailClosure : public HeapRegionClosure {
  outputStream* _st;
public:
  G1PrintCollectionSetDetailClosure(outputStream* st) : HeapRegionClosure(), _st(st) { }

  virtual bool do_heap_region(HeapRegion* r) {
    assert(r->in_collection_set(), "Region %u should be in collection set", r->hrm_index());
    _st->print_cr("  " HR_FORMAT ", TAMS: " PTR_FORMAT " PB: " PTR_FORMAT ", age: %4d",
                  HR_FORMAT_PARAMS(r),
                  p2i(r->top_at_mark_start()),
                  p2i(r->parsable_bottom()),
                  r->has_surv_rate_group() ? r->age_in_surv_rate_group() : -1);
    return false;
  }
};

void G1CollectionSet::print(outputStream* st) {
  st->print_cr("\nCollection_set:");

  G1PrintCollectionSetDetailClosure cl(st);
  iterate(&cl);
}
#endif // !PRODUCT

double G1CollectionSet::finalize_young_part(double target_pause_time_ms, G1SurvivorRegions* survivors) {
  Ticks start_time = Ticks::now();

  finalize_incremental_building();

  guarantee(target_pause_time_ms > 0.0,
            "target_pause_time_ms = %1.6lf should be positive", target_pause_time_ms);

  size_t pending_cards = _policy->pending_cards_at_gc_start();

  log_trace(gc, ergo, cset)("Start choosing CSet. Pending cards: " SIZE_FORMAT " target pause time: %1.2fms",
                            pending_cards, target_pause_time_ms);

  // The young list is laid with the survivor regions from the previous
  // pause are appended to the RHS of the young list, i.e.
  //   [Newly Young Regions ++ Survivors from last pause].

  uint eden_region_length = _g1h->eden_regions_count();
  uint survivor_region_length = survivors->length();
  init_region_lengths(eden_region_length, survivor_region_length);

  verify_young_cset_indices();

  double predicted_base_time_ms = _policy->predict_base_time_ms(pending_cards);
  // Base time already includes the whole remembered set related time, so do not add that here
  // again.
  double predicted_eden_time = _policy->predict_young_region_other_time_ms(eden_region_length) +
                               _policy->predict_eden_copy_time_ms(eden_region_length);
  double remaining_time_ms = MAX2(target_pause_time_ms - (predicted_base_time_ms + predicted_eden_time), 0.0);

  log_trace(gc, ergo, cset)("Added young regions to CSet. Eden: %u regions, Survivors: %u regions, "
                            "predicted eden time: %1.2fms, predicted base time: %1.2fms, target pause time: %1.2fms, remaining time: %1.2fms",
                            eden_region_length, survivor_region_length,
                            predicted_eden_time, predicted_base_time_ms, target_pause_time_ms, remaining_time_ms);

  // Clear the fields that point to the survivor list - they are all young now.
  survivors->convert_to_eden();

  phase_times()->record_young_cset_choice_time_ms((Ticks::now() - start_time).seconds() * 1000.0);

  return remaining_time_ms;
}

static int compare_region_idx(const uint a, const uint b) {
  return static_cast<int>(a-b);
}

void G1CollectionSet::finalize_old_part(double time_remaining_ms) {
  double non_young_start_time_sec = os::elapsedTime();

  if (!candidates()->is_empty()) {
    candidates()->verify();

    G1CollectionCandidateRegionList initial_old_regions;
    assert(_optional_old_regions.length() == 0, "must be");

    if (collector_state()->in_mixed_phase()) {
      time_remaining_ms = _policy->select_candidates_from_marking(&candidates()->marking_regions(),
                                                                  time_remaining_ms,
                                                                  &initial_old_regions,
                                                                  &_optional_old_regions);
    } else {
      log_debug(gc, ergo, cset)("Do not add marking candidates to collection set due to pause type.");
    }

    _policy->select_candidates_from_retained(&candidates()->retained_regions(),
                                             time_remaining_ms,
                                             &initial_old_regions,
                                             &_optional_old_regions);

    // Move initially selected old regions to collection set directly.
    move_candidates_to_collection_set(&initial_old_regions);
    // Only prepare selected optional regions for now.
    prepare_optional_regions(&_optional_old_regions);

    candidates()->verify();
  } else {
    log_debug(gc, ergo, cset)("No candidates to reclaim.");
  }

  stop_incremental_building();

  double non_young_end_time_sec = os::elapsedTime();
  phase_times()->record_non_young_cset_choice_time_ms((non_young_end_time_sec - non_young_start_time_sec) * 1000.0);

  QuickSort::sort(_collection_set_regions, _collection_set_cur_length, compare_region_idx, true);
}

void G1CollectionSet::move_candidates_to_collection_set(G1CollectionCandidateRegionList* regions) {
  for (HeapRegion* r : *regions) {
    _g1h->clear_region_attr(r);
    add_old_region(r);
  }
  candidates()->remove(regions);
}

void G1CollectionSet::prepare_optional_regions(G1CollectionCandidateRegionList* regions){
  uint cur_index = 0;
  for (HeapRegion* r : *regions) {
    assert(r->is_old(), "the region should be old");
    assert(!r->in_collection_set(), "should not already be in the CSet");

    _g1h->register_optional_region_with_region_attr(r);

    r->set_index_in_opt_cset(cur_index++);
  }
}

void G1CollectionSet::finalize_initial_collection_set(double target_pause_time_ms, G1SurvivorRegions* survivor) {
  double time_remaining_ms = finalize_young_part(target_pause_time_ms, survivor);
  finalize_old_part(time_remaining_ms);
}

bool G1CollectionSet::finalize_optional_for_evacuation(double remaining_pause_time) {
  update_incremental_marker();

  G1CollectionCandidateRegionList selected_regions;
  _policy->calculate_optional_collection_set_regions(&_optional_old_regions,
                                                     remaining_pause_time,
                                                     &selected_regions);

  move_candidates_to_collection_set(&selected_regions);

  _optional_old_regions.remove_prefix(&selected_regions);

  stop_incremental_building();

  _g1h->verify_region_attr_remset_is_tracked();

  return selected_regions.length() > 0;
}

void G1CollectionSet::abandon_optional_collection_set(G1ParScanThreadStateSet* pss) {
  for (HeapRegion* r : _optional_old_regions) {
    pss->record_unused_optional_region(r);
    // Clear collection set marker and make sure that the remembered set information
    // is correct as we still need it later.
    _g1h->clear_region_attr(r);
    _g1h->register_region_with_region_attr(r);
    r->clear_index_in_opt_cset();
  }
  _optional_old_regions.clear();

  _g1h->verify_region_attr_remset_is_tracked();
}

#ifdef ASSERT
class G1VerifyYoungCSetIndicesClosure : public HeapRegionClosure {
private:
  size_t _young_length;
  uint* _heap_region_indices;
public:
  G1VerifyYoungCSetIndicesClosure(size_t young_length) : HeapRegionClosure(), _young_length(young_length) {
    _heap_region_indices = NEW_C_HEAP_ARRAY(uint, young_length + 1, mtGC);
    for (size_t i = 0; i < young_length + 1; i++) {
      _heap_region_indices[i] = UINT_MAX;
    }
  }
  ~G1VerifyYoungCSetIndicesClosure() {
    FREE_C_HEAP_ARRAY(int, _heap_region_indices);
  }

  virtual bool do_heap_region(HeapRegion* r) {
    const uint idx = r->young_index_in_cset();

    assert(idx > 0, "Young index must be set for all regions in the incremental collection set but is not for region %u.", r->hrm_index());
    assert(idx <= _young_length, "Young cset index %u too large for region %u", idx, r->hrm_index());

    assert(_heap_region_indices[idx] == UINT_MAX,
           "Index %d used by multiple regions, first use by region %u, second by region %u",
           idx, _heap_region_indices[idx], r->hrm_index());

    _heap_region_indices[idx] = r->hrm_index();

    return false;
  }
};

void G1CollectionSet::verify_young_cset_indices() const {
  assert_at_safepoint_on_vm_thread();

  G1VerifyYoungCSetIndicesClosure cl(_collection_set_cur_length);
  iterate(&cl);
}
#endif

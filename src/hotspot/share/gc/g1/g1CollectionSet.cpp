/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1CollectionSetCandidates.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ParScanThreadState.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/g1/heapRegionSet.hpp"
#include "logging/logStream.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/quickSort.hpp"

G1CollectorState* G1CollectionSet::collector_state() {
  return _g1h->collector_state();
}

G1GCPhaseTimes* G1CollectionSet::phase_times() {
  return _policy->phase_times();
}

double G1CollectionSet::predict_region_elapsed_time_ms(HeapRegion* hr) {
  return _policy->predict_region_elapsed_time_ms(hr, collector_state()->in_young_only_phase());
}

G1CollectionSet::G1CollectionSet(G1CollectedHeap* g1h, G1Policy* policy) :
  _g1h(g1h),
  _policy(policy),
  _candidates(NULL),
  _eden_region_length(0),
  _survivor_region_length(0),
  _old_region_length(0),
  _collection_set_regions(NULL),
  _collection_set_cur_length(0),
  _collection_set_max_length(0),
  _num_optional_regions(0),
  _bytes_used_before(0),
  _recorded_rs_lengths(0),
  _inc_build_state(Inactive),
  _inc_part_start(0),
  _inc_bytes_used_before(0),
  _inc_recorded_rs_lengths(0),
  _inc_recorded_rs_lengths_diffs(0),
  _inc_predicted_elapsed_time_ms(0.0),
  _inc_predicted_elapsed_time_ms_diffs(0.0) {
}

G1CollectionSet::~G1CollectionSet() {
  if (_collection_set_regions != NULL) {
    FREE_C_HEAP_ARRAY(uint, _collection_set_regions);
  }
  free_optional_regions();
  clear_candidates();
}

void G1CollectionSet::init_region_lengths(uint eden_cset_region_length,
                                          uint survivor_cset_region_length) {
  assert_at_safepoint_on_vm_thread();

  _eden_region_length     = eden_cset_region_length;
  _survivor_region_length = survivor_cset_region_length;

  assert((size_t) young_region_length() == _collection_set_cur_length,
         "Young region length %u should match collection set length " SIZE_FORMAT, young_region_length(), _collection_set_cur_length);

  _old_region_length = 0;
  free_optional_regions();
}

void G1CollectionSet::initialize(uint max_region_length) {
  guarantee(_collection_set_regions == NULL, "Must only initialize once.");
  _collection_set_max_length = max_region_length;
  _collection_set_regions = NEW_C_HEAP_ARRAY(uint, max_region_length, mtGC);
}

void G1CollectionSet::free_optional_regions() {
  _num_optional_regions = 0;
}

void G1CollectionSet::clear_candidates() {
  delete _candidates;
  _candidates = NULL;
}

void G1CollectionSet::set_recorded_rs_lengths(size_t rs_lengths) {
  _recorded_rs_lengths = rs_lengths;
}

// Add the heap region at the head of the non-incremental collection set
void G1CollectionSet::add_old_region(HeapRegion* hr) {
  assert_at_safepoint_on_vm_thread();

  assert(_inc_build_state == Active,
         "Precondition, actively building cset or adding optional later on");
  assert(hr->is_old(), "the region should be old");

  assert(!hr->in_collection_set(), "should not already be in the collection set");
  _g1h->register_old_region_with_cset(hr);

  _collection_set_regions[_collection_set_cur_length++] = hr->hrm_index();
  assert(_collection_set_cur_length <= _collection_set_max_length, "Collection set now larger than maximum size.");

  _bytes_used_before += hr->used();
  _recorded_rs_lengths += hr->rem_set()->occupied();
  _old_region_length++;

  _g1h->old_set_remove(hr);
}

void G1CollectionSet::add_optional_region(HeapRegion* hr) {
  assert(hr->is_old(), "the region should be old");
  assert(!hr->in_collection_set(), "should not already be in the CSet");

  _g1h->register_optional_region_with_cset(hr);

  hr->set_index_in_opt_cset(_num_optional_regions++);
}

void G1CollectionSet::start_incremental_building() {
  assert(_collection_set_cur_length == 0, "Collection set must be empty before starting a new collection set.");
  assert(_inc_build_state == Inactive, "Precondition");

  _inc_bytes_used_before = 0;

  _inc_recorded_rs_lengths = 0;
  _inc_recorded_rs_lengths_diffs = 0;
  _inc_predicted_elapsed_time_ms = 0.0;
  _inc_predicted_elapsed_time_ms_diffs = 0.0;

  update_incremental_marker();
}

void G1CollectionSet::finalize_incremental_building() {
  assert(_inc_build_state == Active, "Precondition");
  assert(SafepointSynchronize::is_at_safepoint(), "should be at a safepoint");

  // The two "main" fields, _inc_recorded_rs_lengths and
  // _inc_predicted_elapsed_time_ms, are updated by the thread
  // that adds a new region to the CSet. Further updates by the
  // concurrent refinement thread that samples the young RSet lengths
  // are accumulated in the *_diffs fields. Here we add the diffs to
  // the "main" fields.

  if (_inc_recorded_rs_lengths_diffs >= 0) {
    _inc_recorded_rs_lengths += _inc_recorded_rs_lengths_diffs;
  } else {
    // This is defensive. The diff should in theory be always positive
    // as RSets can only grow between GCs. However, given that we
    // sample their size concurrently with other threads updating them
    // it's possible that we might get the wrong size back, which
    // could make the calculations somewhat inaccurate.
    size_t diffs = (size_t) (-_inc_recorded_rs_lengths_diffs);
    if (_inc_recorded_rs_lengths >= diffs) {
      _inc_recorded_rs_lengths -= diffs;
    } else {
      _inc_recorded_rs_lengths = 0;
    }
  }
  _inc_predicted_elapsed_time_ms += _inc_predicted_elapsed_time_ms_diffs;

  _inc_recorded_rs_lengths_diffs = 0;
  _inc_predicted_elapsed_time_ms_diffs = 0.0;
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

void G1CollectionSet::iterate_optional(HeapRegionClosure* cl) const {
  assert_at_safepoint();

  for (uint i = 0; i < _num_optional_regions; i++) {
    HeapRegion* r = _candidates->at(i);
    bool result = cl->do_heap_region(r);
    guarantee(!result, "Must not cancel iteration");
  }
}

void G1CollectionSet::iterate_incremental_part_from(HeapRegionClosure* cl, uint worker_id, uint total_workers) const {
  assert_at_safepoint();

  size_t len = _collection_set_cur_length - _inc_part_start;
  if (len == 0) {
    return;
  }

  size_t start_pos = (worker_id * len) / total_workers;
  size_t cur_pos = start_pos;

  do {
    HeapRegion* r = _g1h->region_at(_collection_set_regions[cur_pos + _inc_part_start]);
    bool result = cl->do_heap_region(r);
    guarantee(!result, "Must not cancel iteration");

    cur_pos++;
    if (cur_pos == len) {
      cur_pos = 0;
    }
  } while (cur_pos != start_pos);
}

void G1CollectionSet::update_young_region_prediction(HeapRegion* hr,
                                                     size_t new_rs_length) {
  // Update the CSet information that is dependent on the new RS length
  assert(hr->is_young(), "Precondition");
  assert(!SafepointSynchronize::is_at_safepoint(), "should not be at a safepoint");

  // We could have updated _inc_recorded_rs_lengths and
  // _inc_predicted_elapsed_time_ms directly but we'd need to do
  // that atomically, as this code is executed by a concurrent
  // refinement thread, potentially concurrently with a mutator thread
  // allocating a new region and also updating the same fields. To
  // avoid the atomic operations we accumulate these updates on two
  // separate fields (*_diffs) and we'll just add them to the "main"
  // fields at the start of a GC.

  ssize_t old_rs_length = (ssize_t) hr->recorded_rs_length();
  ssize_t rs_lengths_diff = (ssize_t) new_rs_length - old_rs_length;
  _inc_recorded_rs_lengths_diffs += rs_lengths_diff;

  double old_elapsed_time_ms = hr->predicted_elapsed_time_ms();
  double new_region_elapsed_time_ms = predict_region_elapsed_time_ms(hr);
  double elapsed_ms_diff = new_region_elapsed_time_ms - old_elapsed_time_ms;
  _inc_predicted_elapsed_time_ms_diffs += elapsed_ms_diff;

  hr->set_recorded_rs_length(new_rs_length);
  hr->set_predicted_elapsed_time_ms(new_region_elapsed_time_ms);
}

void G1CollectionSet::add_young_region_common(HeapRegion* hr) {
  assert(hr->is_young(), "invariant");
  assert(_inc_build_state == Active, "Precondition");

  size_t collection_set_length = _collection_set_cur_length;
  assert(collection_set_length <= INT_MAX, "Collection set is too large with %d entries", (int)collection_set_length);
  hr->set_young_index_in_cset((int)collection_set_length);

  _collection_set_regions[collection_set_length] = hr->hrm_index();
  // Concurrent readers must observe the store of the value in the array before an
  // update to the length field.
  OrderAccess::storestore();
  _collection_set_cur_length++;
  assert(_collection_set_cur_length <= _collection_set_max_length, "Collection set larger than maximum allowed.");

  // This routine is used when:
  // * adding survivor regions to the incremental cset at the end of an
  //   evacuation pause or
  // * adding the current allocation region to the incremental cset
  //   when it is retired.
  // Therefore this routine may be called at a safepoint by the
  // VM thread, or in-between safepoints by mutator threads (when
  // retiring the current allocation region)
  // We need to clear and set the cached recorded/cached collection set
  // information in the heap region here (before the region gets added
  // to the collection set). An individual heap region's cached values
  // are calculated, aggregated with the policy collection set info,
  // and cached in the heap region here (initially) and (subsequently)
  // by the Young List sampling code.
  // Ignore calls to this due to retirement during full gc.

  if (!_g1h->collector_state()->in_full_gc()) {
    size_t rs_length = hr->rem_set()->occupied();
    double region_elapsed_time_ms = predict_region_elapsed_time_ms(hr);

    // Cache the values we have added to the aggregated information
    // in the heap region in case we have to remove this region from
    // the incremental collection set, or it is updated by the
    // rset sampling code
    hr->set_recorded_rs_length(rs_length);
    hr->set_predicted_elapsed_time_ms(region_elapsed_time_ms);

    _inc_recorded_rs_lengths += rs_length;
    _inc_predicted_elapsed_time_ms += region_elapsed_time_ms;
    _inc_bytes_used_before += hr->used();
  }

  assert(!hr->in_collection_set(), "invariant");
  _g1h->register_young_region_with_cset(hr);
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
public:
  G1VerifyYoungAgesClosure() : HeapRegionClosure(), _valid(true) { }

  virtual bool do_heap_region(HeapRegion* r) {
    guarantee(r->is_young(), "Region must be young but is %s", r->get_type_str());

    SurvRateGroup* group = r->surv_rate_group();

    if (group == NULL) {
      log_error(gc, verify)("## encountered NULL surv_rate_group in young region");
      _valid = false;
    }

    if (r->age_in_surv_rate_group() < 0) {
      log_error(gc, verify)("## encountered negative age in young region");
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
    _st->print_cr("  " HR_FORMAT ", P: " PTR_FORMAT "N: " PTR_FORMAT ", age: %4d",
                  HR_FORMAT_PARAMS(r),
                  p2i(r->prev_top_at_mark_start()),
                  p2i(r->next_top_at_mark_start()),
                  r->age_in_surv_rate_group_cond());
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
  double young_start_time_sec = os::elapsedTime();

  finalize_incremental_building();

  guarantee(target_pause_time_ms > 0.0,
            "target_pause_time_ms = %1.6lf should be positive", target_pause_time_ms);

  size_t pending_cards = _policy->pending_cards();
  double base_time_ms = _policy->predict_base_elapsed_time_ms(pending_cards);
  double time_remaining_ms = MAX2(target_pause_time_ms - base_time_ms, 0.0);

  log_trace(gc, ergo, cset)("Start choosing CSet. pending cards: " SIZE_FORMAT " predicted base time: %1.2fms remaining time: %1.2fms target pause time: %1.2fms",
                            pending_cards, base_time_ms, time_remaining_ms, target_pause_time_ms);

  // The young list is laid with the survivor regions from the previous
  // pause are appended to the RHS of the young list, i.e.
  //   [Newly Young Regions ++ Survivors from last pause].

  uint survivor_region_length = survivors->length();
  uint eden_region_length = _g1h->eden_regions_count();
  init_region_lengths(eden_region_length, survivor_region_length);

  verify_young_cset_indices();

  // Clear the fields that point to the survivor list - they are all young now.
  survivors->convert_to_eden();

  _bytes_used_before = _inc_bytes_used_before;
  time_remaining_ms = MAX2(time_remaining_ms - _inc_predicted_elapsed_time_ms, 0.0);

  log_trace(gc, ergo, cset)("Add young regions to CSet. eden: %u regions, survivors: %u regions, predicted young region time: %1.2fms, target pause time: %1.2fms",
                            eden_region_length, survivor_region_length, _inc_predicted_elapsed_time_ms, target_pause_time_ms);

  // The number of recorded young regions is the incremental
  // collection set's current size
  set_recorded_rs_lengths(_inc_recorded_rs_lengths);

  double young_end_time_sec = os::elapsedTime();
  phase_times()->record_young_cset_choice_time_ms((young_end_time_sec - young_start_time_sec) * 1000.0);

  return time_remaining_ms;
}

static int compare_region_idx(const uint a, const uint b) {
  if (a > b) {
    return 1;
  } else if (a == b) {
    return 0;
  } else {
    return -1;
  }
}

void G1CollectionSet::finalize_old_part(double time_remaining_ms) {
  double non_young_start_time_sec = os::elapsedTime();

  if (collector_state()->in_mixed_phase()) {
    candidates()->verify();

    uint num_initial_old_regions;
    uint num_optional_old_regions;

    _policy->calculate_old_collection_set_regions(candidates(),
                                                  time_remaining_ms,
                                                  num_initial_old_regions,
                                                  num_optional_old_regions);

    // Prepare initial old regions.
    move_candidates_to_collection_set(num_initial_old_regions);

    // Prepare optional old regions for evacuation.
    uint candidate_idx = candidates()->cur_idx();
    for (uint i = 0; i < num_optional_old_regions; i++) {
      add_optional_region(candidates()->at(candidate_idx + i));
    }

    candidates()->verify();
  }

  stop_incremental_building();

  double non_young_end_time_sec = os::elapsedTime();
  phase_times()->record_non_young_cset_choice_time_ms((non_young_end_time_sec - non_young_start_time_sec) * 1000.0);

  QuickSort::sort(_collection_set_regions, _collection_set_cur_length, compare_region_idx, true);
}

void G1CollectionSet::move_candidates_to_collection_set(uint num_old_candidate_regions) {
  if (num_old_candidate_regions == 0) {
    return;
  }
  uint candidate_idx = candidates()->cur_idx();
  for (uint i = 0; i < num_old_candidate_regions; i++) {
    HeapRegion* r = candidates()->at(candidate_idx + i);
    // This potentially optional candidate region is going to be an actual collection
    // set region. Clear cset marker.
    _g1h->clear_in_cset(r);
    add_old_region(r);
  }
  candidates()->remove(num_old_candidate_regions);

  candidates()->verify();
}

void G1CollectionSet::finalize_initial_collection_set(double target_pause_time_ms, G1SurvivorRegions* survivor) {
  double time_remaining_ms = finalize_young_part(target_pause_time_ms, survivor);
  finalize_old_part(time_remaining_ms);
}

bool G1CollectionSet::finalize_optional_for_evacuation(double remaining_pause_time) {
  update_incremental_marker();

  uint num_selected_regions;
  _policy->calculate_optional_collection_set_regions(candidates(),
                                                     _num_optional_regions,
                                                     remaining_pause_time,
                                                     num_selected_regions);

  move_candidates_to_collection_set(num_selected_regions);

  _num_optional_regions -= num_selected_regions;

  stop_incremental_building();
  return num_selected_regions > 0;
}

void G1CollectionSet::abandon_optional_collection_set(G1ParScanThreadStateSet* pss) {
  for (uint i = 0; i < _num_optional_regions; i++) {
    HeapRegion* r = candidates()->at(candidates()->cur_idx() + i);
    pss->record_unused_optional_region(r);
    _g1h->clear_in_cset(r);
    r->clear_index_in_opt_cset();
  }
  free_optional_regions();
}

#ifdef ASSERT
class G1VerifyYoungCSetIndicesClosure : public HeapRegionClosure {
private:
  size_t _young_length;
  int* _heap_region_indices;
public:
  G1VerifyYoungCSetIndicesClosure(size_t young_length) : HeapRegionClosure(), _young_length(young_length) {
    _heap_region_indices = NEW_C_HEAP_ARRAY(int, young_length, mtGC);
    for (size_t i = 0; i < young_length; i++) {
      _heap_region_indices[i] = -1;
    }
  }
  ~G1VerifyYoungCSetIndicesClosure() {
    FREE_C_HEAP_ARRAY(int, _heap_region_indices);
  }

  virtual bool do_heap_region(HeapRegion* r) {
    const int idx = r->young_index_in_cset();

    assert(idx > -1, "Young index must be set for all regions in the incremental collection set but is not for region %u.", r->hrm_index());
    assert((size_t)idx < _young_length, "Young cset index too large for region %u", r->hrm_index());

    assert(_heap_region_indices[idx] == -1,
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

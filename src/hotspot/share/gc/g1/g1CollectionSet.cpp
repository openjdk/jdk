/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.inline.hpp"
#include "gc/g1/g1CollectionSetCandidates.inline.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1HeapRegionSet.hpp"
#include "gc/g1/g1ParScanThreadState.hpp"
#include "gc/g1/g1Policy.hpp"
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
  _collection_set_groups(),
  _selected_groups_cur_length(0),
  _selected_groups_inc_part_start(0),
  _eden_region_length(0),
  _survivor_region_length(0),
  _initial_old_region_length(0),
  _optional_groups(),
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
  assert(_optional_groups.length() == 0, "Should not have any optional groups yet");
  _optional_groups.clear();
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
}

void G1CollectionSet::prepare_groups_for_scan () {
  collection_set_groups()->prepare_for_scan();
}

void G1CollectionSet::add_old_region(G1HeapRegion* hr) {
  assert_at_safepoint_on_vm_thread();

  assert(_inc_build_state == Active,
         "Precondition, actively building cset or adding optional later on");
  assert(hr->is_old(), "the region should be old");

  assert(!hr->rem_set()->is_added_to_cset_group(), "Should have already uninstalled group remset");

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
  _collection_set_groups.clear();
}

void G1CollectionSet::iterate(G1HeapRegionClosure* cl) const {
  size_t len = _collection_set_cur_length;
  OrderAccess::loadload();

  for (uint i = 0; i < len; i++) {
    G1HeapRegion* r = _g1h->region_at(_collection_set_regions[i]);
    bool result = cl->do_heap_region(r);
    if (result) {
      cl->set_incomplete();
      return;
    }
  }
}

void G1CollectionSet::par_iterate(G1HeapRegionClosure* cl,
                                  G1HeapRegionClaimer* hr_claimer,
                                  uint worker_id) const {
  iterate_part_from(cl, hr_claimer, 0, cur_length(), worker_id);
}

void G1CollectionSet::iterate_optional(G1HeapRegionClosure* cl) const {
  assert_at_safepoint();

  _optional_groups.iterate([&] (G1HeapRegion* r) {
    bool result = cl->do_heap_region(r);
    guarantee(!result, "Must not cancel iteration");
  });
}

void G1CollectionSet::iterate_incremental_part_from(G1HeapRegionClosure* cl,
                                                    G1HeapRegionClaimer* hr_claimer,
                                                    uint worker_id) const {
  iterate_part_from(cl, hr_claimer, _inc_part_start, increment_length(), worker_id);
}

void G1CollectionSet::iterate_part_from(G1HeapRegionClosure* cl,
                                        G1HeapRegionClaimer* hr_claimer,
                                        size_t offset,
                                        size_t length,
                                        uint worker_id) const {
  _g1h->par_iterate_regions_array(cl,
                                  hr_claimer,
                                  &_collection_set_regions[offset],
                                  length,
                                  worker_id);
}

void G1CollectionSet::add_young_region_common(G1HeapRegion* hr) {
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

void G1CollectionSet::add_survivor_regions(G1HeapRegion* hr) {
  assert(hr->is_survivor(), "Must only add survivor regions, but is %s", hr->get_type_str());
  add_young_region_common(hr);
}

void G1CollectionSet::add_eden_region(G1HeapRegion* hr) {
  assert(hr->is_eden(), "Must only add eden regions, but is %s", hr->get_type_str());
  add_young_region_common(hr);
}

#ifndef PRODUCT
class G1VerifyYoungAgesClosure : public G1HeapRegionClosure {
public:
  bool _valid;

  G1VerifyYoungAgesClosure() : G1HeapRegionClosure(), _valid(true) { }

  virtual bool do_heap_region(G1HeapRegion* r) {
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

class G1PrintCollectionSetDetailClosure : public G1HeapRegionClosure {
  outputStream* _st;
public:
  G1PrintCollectionSetDetailClosure(outputStream* st) : G1HeapRegionClosure(), _st(st) { }

  virtual bool do_heap_region(G1HeapRegion* r) {
    assert(r->in_collection_set(), "Region %u should be in collection set", r->hrm_index());
    G1ConcurrentMark* cm = G1CollectedHeap::heap()->concurrent_mark();
    _st->print_cr("  " HR_FORMAT ", TAMS: " PTR_FORMAT " PB: " PTR_FORMAT ", age: %4d",
                  HR_FORMAT_PARAMS(r),
                  p2i(cm->top_at_mark_start(r)),
                  p2i(r->parsable_bottom()),
                  r->has_surv_rate_group() ? checked_cast<int>(r->age_in_surv_rate_group()) : -1);
    return false;
  }
};

void G1CollectionSet::print(outputStream* st) {
  st->print_cr("\nCollection_set:");

  G1PrintCollectionSetDetailClosure cl(st);
  iterate(&cl);
}
#endif // !PRODUCT

// Always evacuate out pinned regions (apart from object types that can actually be
// pinned by JNI) to allow faster future evacuation. We already "paid" for this work
// when sizing the young generation.
double G1CollectionSet::finalize_young_part(double target_pause_time_ms, G1SurvivorRegions* survivors) {
  Ticks start_time = Ticks::now();

  finalize_incremental_building();

  guarantee(target_pause_time_ms > 0.0,
            "target_pause_time_ms = %1.6lf should be positive", target_pause_time_ms);

  size_t pending_cards = _policy->pending_cards_at_gc_start();

  log_trace(gc, ergo, cset)("Start choosing CSet. Pending cards: %zu target pause time: %1.2fms",
                            pending_cards, target_pause_time_ms);

  // The young list is laid with the survivor regions from the previous
  // pause are appended to the RHS of the young list, i.e.
  //   [Newly Young Regions ++ Survivors from last pause].

  uint eden_region_length = _g1h->eden_regions_count();
  uint survivor_region_length = survivors->length();
  init_region_lengths(eden_region_length, survivor_region_length);

  verify_young_cset_indices();

  double predicted_base_time_ms = _policy->predict_base_time_ms(pending_cards, _g1h->young_regions_cardset()->occupied());
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

// The current mechanism for evacuating pinned old regions is as below:
// * pinned regions in the marking collection set candidate list (available during mixed gc) are evacuated like
//   pinned young regions to avoid the complexity of dealing with pinned regions that are part of a
//   collection group sharing a single cardset. These regions will be partially evacuated and added to the
//   retained collection set by the evacuation failure handling mechanism.
// * evacuating pinned regions out of retained collection set candidates would also just take up time
//   with no actual space freed in old gen. Better to concentrate on others. So we skip over pinned
//   regions in retained collection set candidates. Retained collection set candidates are aged out, ie.
//   made to regular old regions without remembered sets after a few attempts to save computation costs
//   of keeping them candidates for very long living pinned regions.
void G1CollectionSet::finalize_old_part(double time_remaining_ms) {
  double non_young_start_time_sec = os::elapsedTime();

  _selected_groups_cur_length = 0;
  _selected_groups_inc_part_start = 0;

  if (!candidates()->is_empty()) {
    candidates()->verify();

    if (collector_state()->in_mixed_phase()) {
      time_remaining_ms = select_candidates_from_marking(time_remaining_ms);
    } else {
      log_debug(gc, ergo, cset)("Do not add marking candidates to collection set due to pause type.");
    }

    if (candidates()->retained_groups().num_regions() > 0) {
      select_candidates_from_retained(time_remaining_ms);
    }
    candidates()->verify();
  } else {
    log_debug(gc, ergo, cset)("No candidates to reclaim.");
  }

  _selected_groups_cur_length = collection_set_groups()->length();
  stop_incremental_building();

  double non_young_end_time_sec = os::elapsedTime();
  phase_times()->record_non_young_cset_choice_time_ms((non_young_end_time_sec - non_young_start_time_sec) * 1000.0);

  QuickSort::sort(_collection_set_regions, _collection_set_cur_length, compare_region_idx);
}

static void print_finish_message(const char* reason, bool from_marking) {
  log_debug(gc, ergo, cset)("Finish adding %s candidates to collection set (%s).",
                            from_marking ? "marking" : "retained", reason);
}

double G1CollectionSet::select_candidates_from_marking(double time_remaining_ms) {
  uint num_expensive_regions = 0;
  uint num_inital_regions = 0;
  uint num_initial_groups = 0;
  uint num_optional_regions = 0;

  assert(_optional_groups.num_regions() == 0, "Optional regions should not already be selected");

  double predicted_initial_time_ms = 0.0;
  double predicted_optional_time_ms = 0.0;

  double optional_threshold_ms = time_remaining_ms * _policy->optional_prediction_fraction();

  uint min_old_cset_length = _policy->calc_min_old_cset_length(candidates()->last_marking_candidates_length());
  uint max_old_cset_length = MAX2(min_old_cset_length, _policy->calc_max_old_cset_length());
  bool check_time_remaining = _policy->use_adaptive_young_list_length();

  G1CSetCandidateGroupList* from_marking_groups = &candidates()->from_marking_groups();

  log_debug(gc, ergo, cset)("Start adding marking candidates to collection set. "
                            "Min %u regions, max %u regions, available %u regions (%u groups), "
                            "time remaining %1.2fms, optional threshold %1.2fms",
                            min_old_cset_length, max_old_cset_length, from_marking_groups->num_regions(), from_marking_groups->length(),
                            time_remaining_ms, optional_threshold_ms);

  G1CSetCandidateGroupList selected_groups;

  for (G1CSetCandidateGroup* group : *from_marking_groups) {
    if (num_inital_regions + num_optional_regions >= max_old_cset_length) {
      // Added maximum number of old regions to the CSet.
      print_finish_message("Maximum number of regions reached", true);
      break;
    }

    double predicted_time_ms = group->predict_group_total_time_ms();

    time_remaining_ms = MAX2(time_remaining_ms - predicted_time_ms, 0.0);
    // Add regions to old set until we reach the minimum amount
    if (num_inital_regions < min_old_cset_length) {

      num_initial_groups++;

      add_group_to_collection_set(group);
      selected_groups.append(group);

      num_inital_regions += group->length();

      predicted_initial_time_ms += predicted_time_ms;
      // Record the number of regions added with no time remaining
      if (time_remaining_ms == 0.0) {
        num_expensive_regions += group->length();
      }
    } else if (!check_time_remaining) {
      // In the non-auto-tuning case, we'll finish adding regions
      // to the CSet if we reach the minimum.
      print_finish_message("Region amount reached min", true);
      break;
    } else {
      // Keep adding regions to old set until we reach the optional threshold
      if (time_remaining_ms > optional_threshold_ms) {
        num_initial_groups++;

        add_group_to_collection_set(group);
        selected_groups.append(group);

        num_inital_regions += group->length();

        predicted_initial_time_ms += predicted_time_ms;

      } else if (time_remaining_ms > 0) {
        // Keep adding optional regions until time is up.
        _optional_groups.append(group);
        prepare_optional_group(group, num_optional_regions);
        num_optional_regions += group->length();
        predicted_optional_time_ms += predicted_time_ms;
      } else {
        print_finish_message("Predicted time too high", true);
        break;
      }
    }
  }

  // Remove selected groups from list of candidate groups.
  if (num_initial_groups > 0) {
    candidates()->remove(&selected_groups);
  }

  if (from_marking_groups->length() == 0) {
    log_debug(gc, ergo, cset)("Marking candidates exhausted.");
  }

  if (num_expensive_regions > 0) {
    log_debug(gc, ergo, cset)("Added %u marking candidates to collection set although the predicted time was too high.",
                              num_expensive_regions);
  }

  log_debug(gc, ergo, cset)("Finish adding marking candidates to collection set. Initial: %u regions (%u groups), optional: %u regions (%u groups), "
                            "predicted initial time: %1.2fms, predicted optional time: %1.2fms, time remaining: %1.2fms",
                            selected_groups.num_regions(), selected_groups.length(), _optional_groups.num_regions(), _optional_groups.length(),
                            predicted_initial_time_ms, predicted_optional_time_ms, time_remaining_ms);

  assert(selected_groups.num_regions() == num_inital_regions, "must be");
  assert(_optional_groups.num_regions() == num_optional_regions, "must be");
  return time_remaining_ms;
}

void G1CollectionSet::select_candidates_from_retained(double time_remaining_ms) {
  uint num_initial_regions = 0;
  uint prev_num_optional_regions = _optional_groups.num_regions();
  uint num_optional_regions = prev_num_optional_regions;
  uint num_expensive_regions = 0;
  uint num_pinned_regions = 0;

  double predicted_initial_time_ms = 0.0;
  double predicted_optional_time_ms = 0.0;

  uint const min_regions = _policy->min_retained_old_cset_length();
  // We want to make sure that on the one hand we process the retained regions asap,
  // but on the other hand do not take too many of them as optional regions.
  // So we split the time budget into budget we will unconditionally take into the
  // initial old regions, and budget for taking optional regions from the retained
  // list.
  double optional_time_remaining_ms = _policy->max_time_for_retaining();
  time_remaining_ms = MIN2(time_remaining_ms, optional_time_remaining_ms);

  G1CSetCandidateGroupList* retained_groups = &candidates()->retained_groups();

  log_debug(gc, ergo, cset)("Start adding retained candidates to collection set. "
                            "Min %u regions, available %u regions (%u groups), "
                            "time remaining %1.2fms, optional remaining %1.2fms",
                            min_regions, retained_groups->num_regions(), retained_groups->length(),
                            time_remaining_ms, optional_time_remaining_ms);

  G1CSetCandidateGroupList remove_from_retained;
  G1CSetCandidateGroupList groups_to_abandon;

  for (G1CSetCandidateGroup* group : *retained_groups) {
    assert(group->length() == 1, "Retained groups should have only 1 region");

    double predicted_time_ms = group->predict_group_total_time_ms();

    bool fits_in_remaining_time = predicted_time_ms <= time_remaining_ms;

    G1CollectionSetCandidateInfo* ci = group->at(0); // We only have one region in the group.
    G1HeapRegion* r = ci->_r;

    // If we can't reclaim that region ignore it for now.
    if (r->has_pinned_objects()) {
      num_pinned_regions++;
      if (ci->update_num_unreclaimed()) {
        log_trace(gc, ergo, cset)("Retained candidate %u can not be reclaimed currently. Skipping.", r->hrm_index());
      } else {
        log_trace(gc, ergo, cset)("Retained candidate %u can not be reclaimed currently. Dropping.", r->hrm_index());
        // Drop pinned retained regions to make progress with retained regions. Regions
        // in that list must have been pinned for at least G1NumCollectionsKeepPinned
        // GCs and hence are considered "long lived".
        _g1h->clear_region_attr(r);
        groups_to_abandon.append(group);
        remove_from_retained.append(group);
      }
      continue;
    }

    if (fits_in_remaining_time || (num_expensive_regions < min_regions)) {
      predicted_initial_time_ms += predicted_time_ms;
      if (!fits_in_remaining_time) {
        num_expensive_regions++;
      }

      add_group_to_collection_set(group);
      remove_from_retained.append(group);

      num_initial_regions += group->length();
    } else if (predicted_time_ms <= optional_time_remaining_ms) {
      // Prepare optional collection region.
      _optional_groups.append(group);
      prepare_optional_group(group, num_optional_regions);
      num_optional_regions += group->length();
      predicted_optional_time_ms += predicted_time_ms;
    } else {
      // Fits neither initial nor optional time limit. Exit.
      break;
    }
    time_remaining_ms = MAX2(0.0, time_remaining_ms - predicted_time_ms);
    optional_time_remaining_ms = MAX2(0.0, optional_time_remaining_ms - predicted_time_ms);
  }

  if (num_initial_regions == retained_groups->num_regions()) {
    log_debug(gc, ergo, cset)("Retained candidates exhausted.");
  }

  if (num_expensive_regions > 0) {
    log_debug(gc, ergo, cset)("Added %u retained candidates to collection set although the predicted time was too high.",
                              num_expensive_regions);
  }

  // Remove groups from retained and also do some bookkeeping on CandidateOrigin
  // for the regions in these groups.
  candidates()->remove(&remove_from_retained);

  groups_to_abandon.clear(true /* uninstall_group_cardset */);

  assert(num_optional_regions >= prev_num_optional_regions, "Sanity");
  uint selected_optional_regions = num_optional_regions - prev_num_optional_regions;

  log_debug(gc, ergo, cset)("Finish adding retained candidates to collection set. Initial: %u, optional: %u, pinned: %u, "
                            "predicted initial time: %1.2fms, predicted optional time: %1.2fms, "
                            "time remaining: %1.2fms optional time remaining %1.2fms",
                            num_initial_regions, selected_optional_regions, num_pinned_regions,
                            predicted_initial_time_ms, predicted_optional_time_ms, time_remaining_ms, optional_time_remaining_ms);
}

double G1CollectionSet::select_candidates_from_optional_groups(double time_remaining_ms, uint& num_regions_selected) {

  assert(_optional_groups.num_regions() > 0,
         "Should only be called when there are optional regions");

  uint num_groups_selected = 0;
  double total_prediction_ms = 0.0;
  G1CSetCandidateGroupList selected;
  for (G1CSetCandidateGroup* group : _optional_groups) {
    double predicted_time_ms = group->predict_group_total_time_ms();

    if (predicted_time_ms > time_remaining_ms) {
      log_debug(gc, ergo, cset)("Prediction %.3fms for group with %u regions does not fit remaining time: %.3fms.",
                                predicted_time_ms, group->length(), time_remaining_ms);
      break;
    }

    total_prediction_ms += predicted_time_ms;
    time_remaining_ms -= predicted_time_ms;

    num_regions_selected += group->length();
    num_groups_selected++;

    add_group_to_collection_set(group);
    selected.append(group);
  }

  log_debug(gc, ergo, cset) ("Completed with groups, selected %u", num_regions_selected);
  // Remove selected groups from candidate list.
  if (num_groups_selected > 0) {
    _optional_groups.remove(&selected);
    candidates()->remove(&selected);
  }
  return total_prediction_ms;
}

uint G1CollectionSet::select_optional_collection_set_regions(double time_remaining_ms) {
  uint optional_regions_count = num_optional_regions();
  assert(optional_regions_count > 0,
         "Should only be called when there are optional regions");

  uint num_regions_selected = 0;

  double total_prediction_ms = select_candidates_from_optional_groups(time_remaining_ms, num_regions_selected);

  time_remaining_ms -= total_prediction_ms;

  log_debug(gc, ergo, cset)("Prepared %u regions out of %u for optional evacuation. Total predicted time: %.3fms",
                            num_regions_selected, optional_regions_count, total_prediction_ms);
  return num_regions_selected;
}

void G1CollectionSet::prepare_optional_group(G1CSetCandidateGroup* gr, uint cur_index) {
  for (G1CollectionSetCandidateInfo ci : *gr) {
    G1HeapRegion* r = ci._r;

    assert(r->is_old(), "the region should be old");
    assert(!r->in_collection_set(), "should not already be in the CSet");

    _g1h->register_optional_region_with_region_attr(r);
    r->set_index_in_opt_cset(cur_index++);
  }
}

void G1CollectionSet::add_group_to_collection_set(G1CSetCandidateGroup* gr) {
  for (G1CollectionSetCandidateInfo ci : *gr) {
    G1HeapRegion* r = ci._r;
    r->uninstall_cset_group();
    assert(r->rem_set()->is_complete(), "must be");
    add_region_to_collection_set(r);
  }
  _collection_set_groups.append(gr);
}

void G1CollectionSet::add_region_to_collection_set(G1HeapRegion* r) {
  _g1h->clear_region_attr(r);
  assert(r->rem_set()->is_complete(), "Remset for region %u complete", r->hrm_index());
  add_old_region(r);
}

void G1CollectionSet::finalize_initial_collection_set(double target_pause_time_ms, G1SurvivorRegions* survivor) {
  double time_remaining_ms = finalize_young_part(target_pause_time_ms, survivor);
  finalize_old_part(time_remaining_ms);
}

bool G1CollectionSet::finalize_optional_for_evacuation(double remaining_pause_time) {
  update_incremental_marker();

  uint num_regions_selected = select_optional_collection_set_regions(remaining_pause_time);

  _selected_groups_cur_length = collection_set_groups()->length();
  stop_incremental_building();

  _g1h->verify_region_attr_remset_is_tracked();

  return num_regions_selected > 0;
}

void G1CollectionSet::abandon_optional_collection_set(G1ParScanThreadStateSet* pss) {
  if (_optional_groups.length() > 0) {
    auto reset = [&] (G1HeapRegion* r) {
      pss->record_unused_optional_region(r);
      // Clear collection set marker and make sure that the remembered set information
      // is correct as we still need it later.
      _g1h->clear_region_attr(r);
      _g1h->register_region_with_region_attr(r);
      r->clear_index_in_opt_cset();
    };

    _optional_groups.iterate(reset);
    // Remove groups from list without deleting the groups or clearing the associated cardsets.
    _optional_groups.remove_selected(_optional_groups.length(), _optional_groups.num_regions());
  }

  _g1h->verify_region_attr_remset_is_tracked();
}

#ifdef ASSERT
class G1VerifyYoungCSetIndicesClosure : public G1HeapRegionClosure {
private:
  size_t _young_length;
  uint* _heap_region_indices;
public:
  G1VerifyYoungCSetIndicesClosure(size_t young_length) : G1HeapRegionClosure(), _young_length(young_length) {
    _heap_region_indices = NEW_C_HEAP_ARRAY(uint, young_length + 1, mtGC);
    for (size_t i = 0; i < young_length + 1; i++) {
      _heap_region_indices[i] = UINT_MAX;
    }
  }
  ~G1VerifyYoungCSetIndicesClosure() {
    FREE_C_HEAP_ARRAY(int, _heap_region_indices);
  }

  virtual bool do_heap_region(G1HeapRegion* r) {
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

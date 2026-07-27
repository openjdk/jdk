/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectorState.inline.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1HeapRegionSet.hpp"
#include "gc/g1/g1ParScanThreadState.hpp"
#include "gc/g1/g1Policy.hpp"
#include "logging/logStream.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

uint G1CollectionSet::num_groups() const {
  assert(_inc_build_state == CSetBuildType::Inactive, "must be");
  return _groups.length();
}

uint G1CollectionSet::num_groups_in_increment() const {
  return num_groups() - _groups_inc_part_start;
}

G1CollectorState* G1CollectionSet::collector_state() const {
  return _g1h->collector_state();
}

G1GCPhaseTimes* G1CollectionSet::phase_times() {
  return _policy->phase_times();
}

static void consume_old_cset_copy_budget(size_t bytes_to_copy, size_t& copy_budget_bytes) {
  copy_budget_bytes = copy_budget_bytes > bytes_to_copy  ?
                      copy_budget_bytes - bytes_to_copy : 0;
}

static double remaining_budget_ms(double budget_ms, double time_ms) {
  return budget_ms > time_ms ? budget_ms - time_ms : 0.0 ;
}

G1CollectionSet::G1CollectionSet(G1CollectedHeap* g1h, G1Policy* policy) :
  _g1h(g1h),
  _policy(policy),
  _candidates(),
  _regions(nullptr),
  _max_num_regions(0),
  _num_regions(0),
  _groups(),
  _num_eden_regions(0),
  _num_survivor_regions(0),
  _num_initial_old_regions(0),
  _optional_groups(),
  DEBUG_ONLY(_inc_build_state(CSetBuildType::Inactive) COMMA)
  _regions_inc_part_start(0),
  _groups_inc_part_start(0) {
}

G1CollectionSet::~G1CollectionSet() {
  FREE_C_HEAP_ARRAY(_regions);
  abandon_all_candidates();
}

void G1CollectionSet::prepare_for_collection(uint num_eden_cset_regions,
                                             uint num_survivor_cset_regions) {
  assert_at_safepoint_on_vm_thread();

  _num_eden_regions     = num_eden_cset_regions;
  _num_survivor_regions = num_survivor_cset_regions;

  assert(num_young_regions() == num_regions(),
         "Young region amount %u should match collection set region amount %u", num_young_regions(), num_regions());

  _num_initial_old_regions = 0;
  assert(_optional_groups.length() == 0, "Should not have any optional groups yet");
  _optional_groups.clear();
}

void G1CollectionSet::initialize(uint max_num_regions) {
  guarantee(_regions == nullptr, "Must only initialize once.");
  _max_num_regions = max_num_regions;
  _regions = NEW_C_HEAP_ARRAY(uint, max_num_regions, mtGC);

  _candidates.initialize(max_num_regions);
}

void G1CollectionSet::abandon() {
  _g1h->young_regions_cset_group()->clear(true /* uninstall_cset_group */);
  clear();
  abandon_all_candidates();

  stop_incremental_building();
}

void G1CollectionSet::abandon_all_candidates() {
  _candidates.clear();
  _num_initial_old_regions = 0;
}

void G1CollectionSet::prepare_for_scan () {
  _g1h->young_regions_cset_group()->card_set()->reset_table_scanner_for_groups();
  _groups.prepare_for_scan();
}

void G1CollectionSet::add_old_region(G1HeapRegion* hr) {
  assert_at_safepoint_on_vm_thread();

  assert(_inc_build_state == CSetBuildType::Active,
         "Precondition, actively building cset or adding optional later on");
  assert(hr->is_old(), "the region should be old");

  assert(!hr->rem_set()->has_cset_group(), "Should have already uninstalled group remset");

  _g1h->register_old_collection_set_region_with_region_attr(hr);

  uint local_num_regions = num_regions();
  assert(local_num_regions < _max_num_regions, "Collection set now larger than maximum size.");
  _regions[local_num_regions] = hr->hrm_index();
  _num_regions.store_relaxed(local_num_regions + 1);

  _num_initial_old_regions++;

  _g1h->old_set_remove(hr);
}

void G1CollectionSet::start() {
  assert(num_regions() == 0, "Collection set must be empty before starting a new collection set.");
  assert(num_groups() == 0, "Collection set groups must be empty before starting a new collection set.");
  assert(_optional_groups.length() == 0,
         "Collection set optional groups must be empty before starting a new collection set.");

  continue_incremental_building();

  G1CSetCandidateGroup* young_group = _g1h->young_regions_cset_group();
  young_group->clear();
}

void G1CollectionSet::continue_incremental_building() {
  assert(_inc_build_state == CSetBuildType::Inactive, "Precondition");

  _regions_inc_part_start = num_regions();
  _groups_inc_part_start = num_groups();

  DEBUG_ONLY(_inc_build_state = CSetBuildType::Active;)
}

void G1CollectionSet::stop_incremental_building() {
  DEBUG_ONLY(_inc_build_state = CSetBuildType::Inactive;)
}

void G1CollectionSet::clear() {
  assert_at_safepoint_on_vm_thread();
  _num_regions.store_relaxed(0);
  _groups.clear();
  assert(_optional_groups.length() == 0, "must be");
}

void G1CollectionSet::iterate(G1HeapRegionClosure* cl) const {
  uint len = _num_regions.load_acquire();

  for (uint i = 0; i < len; i++) {
    G1HeapRegion* r = _g1h->region_at(_regions[i]);
    bool result = cl->do_heap_region(r);
    if (result) {
      return;
    }
  }
}

void G1CollectionSet::par_iterate(G1HeapRegionClosure* cl,
                                  G1HeapRegionClaimer* hr_claimer,
                                  uint worker_id) const {
  iterate_part_from(cl, hr_claimer, 0, num_regions(), worker_id);
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
  iterate_part_from(cl, hr_claimer, _regions_inc_part_start, num_regions_in_increment(), worker_id);
}

void G1CollectionSet::iterate_part_from(G1HeapRegionClosure* cl,
                                        G1HeapRegionClaimer* hr_claimer,
                                        uint offset,
                                        uint length,
                                        uint worker_id) const {
  _g1h->par_iterate_regions_array(cl,
                                  hr_claimer,
                                  &_regions[offset],
                                  length,
                                  worker_id);
}

void G1CollectionSet::add_young_region_common(G1HeapRegion* hr) {
  assert(hr->is_young(), "invariant");
  assert(_inc_build_state == CSetBuildType::Active, "Precondition");

  // Add to remembered set/cardset group.
  _g1h->policy()->remset_tracker()->update_at_allocate(hr);
  _g1h->young_regions_cset_group()->add(hr);

  // Synchronize with the region attribute table.
  _g1h->register_young_region_with_region_attr(hr);

  uint index = num_regions();
  // We use UINT_MAX as "invalid" marker in verification.
  assert(index < (UINT_MAX - 1), "Collection set is too large with %u entries", index);
  hr->set_young_index_in_cset(index + 1);

  assert(index < _max_num_regions, "Collection set larger than maximum allowed.");
  _regions[index] = hr->hrm_index();
  // Concurrent readers must observe the store of the value in the array before an
  // update to the _num_regions field.
  _num_regions.fetch_then_add(1u, memory_order_release);
}

void G1CollectionSet::add_survivor_regions(G1HeapRegion* hr) {
  assert_at_safepoint_on_vm_thread();
  assert(hr->is_survivor(), "Must only add survivor regions, but is %s", hr->get_type_str());
  add_young_region_common(hr);
}

void G1CollectionSet::add_eden_region(G1HeapRegion* hr) {
  assert_heap_locked_or_at_safepoint(true /* should_be_vm_thread */);
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
                  p2i(cm->top_at_mark_start_or_bottom(r)),
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
G1CollectionSet::CSetSelectionBudget
G1CollectionSet::finalize_young_part(double target_pause_time_ms, G1SurvivorRegions* survivors) {
  assert(_inc_build_state == CSetBuildType::Active, "Precondition");
  assert(SafepointSynchronize::is_at_safepoint(), "should be at a safepoint");

  Ticks start_time = Ticks::now();

  guarantee(target_pause_time_ms > 0.0,
            "target_pause_time_ms = %1.6lf should be positive", target_pause_time_ms);

  log_trace(gc, ergo, cset)("Start choosing CSet. Target pause time: %1.2fms",
                            target_pause_time_ms);

  // Young region indexes are assigned with eden regions first, followed by
  // survivor regions from the previous pause:
  //   [Eden regions ++ Survivors from last pause].

  uint num_eden_regions = _g1h->eden_regions_count();
  uint num_survivor_regions = survivors->length();
  prepare_for_collection(num_eden_regions, num_survivor_regions);

  verify_young_cset_indices();

  G1EvacuationPrediction base_prediction = _policy->predict_base_evacuation();

  double predicted_base_time_ms = base_prediction._time_ms;
  size_t predicted_survivor_bytes_to_copy = base_prediction._bytes_to_copy;

  // Base time already includes the whole remembered set related time, so do not add that here
  // again.
  G1EvacuationPrediction eden_prediction = _policy->predict_eden_evacuation(num_eden_regions);

  size_t predicted_eden_bytes_to_copy = eden_prediction._bytes_to_copy;

  double predicted_eden_time = eden_prediction._time_ms;

  double time_budget_ms = remaining_budget_ms(target_pause_time_ms,
                                              predicted_base_time_ms + predicted_eden_time);

  size_t predicted_young_bytes_to_copy = predicted_eden_bytes_to_copy + predicted_survivor_bytes_to_copy;
  size_t young_used = young_used_bytes();
  size_t copy_budget_bytes = old_cset_copy_budget_bytes(predicted_young_bytes_to_copy, young_used);

  log_trace(gc, ergo, cset)("Added young regions to CSet. Eden: %u regions, Survivors: %u regions, "
                            "predicted eden time: %1.2fms, predicted base time: %1.2fms, "
                            "target pause time: %1.2fms, time budget: %1.2fms "
                            "eden bytes to copy: %zu survivor bytes to copy: %zu "
                            "old-region copy budget: %zuB",
                            num_eden_regions, num_survivor_regions,
                            predicted_eden_time, predicted_base_time_ms, target_pause_time_ms,
                            time_budget_ms,
                            predicted_eden_bytes_to_copy, predicted_survivor_bytes_to_copy,
                            copy_budget_bytes);

  // Set survivor regions as eden and clear survivor tracking for this pause.
  survivors->convert_to_eden();

  phase_times()->record_young_cset_choice_time_ms((Ticks::now() - start_time).seconds() * 1000.0);

  return {time_budget_ms, copy_budget_bytes};
}

size_t G1CollectionSet::old_cset_copy_budget_bytes(size_t predicted_young_bytes_to_copy,
                                                   size_t young_used_bytes) const {
  const size_t free_bytes = (size_t)_g1h->num_free_regions() * G1HeapRegion::GrainBytes;
  const size_t young_reserve_bytes =
    G1Policy::young_evacuation_reserve_bytes(predicted_young_bytes_to_copy, young_used_bytes);
  if (young_reserve_bytes >= free_bytes) {
    return 0;
  }
  const size_t available_bytes = free_bytes - young_reserve_bytes;
  const size_t scale = 100 + TargetPLABWastePct;

  size_t scaled_bytes = available_bytes * 100;
  if (scaled_bytes / 100 != available_bytes) {
    scaled_bytes = SIZE_MAX;
  }

  return scaled_bytes / scale;
}

size_t G1CollectionSet::young_used_bytes() const {
  size_t used_bytes = 0;
  for (uint i = 0; i < num_young_regions(); i++) {
    used_bytes += _g1h->region_at(_regions[i])->used();
  }
  return used_bytes;
}

class G1CollectionSet::CandidateSelection {
  uint _num_initial_regions;
  uint _num_optional_regions;
  uint _num_expensive_regions;
  G1EvacuationPrediction _initial_prediction;
  G1EvacuationPrediction _optional_prediction;

protected:
  G1CollectionSet* const _collection_set;

  explicit CandidateSelection(G1CollectionSet* collection_set);

  uint num_initial_regions() const { return _num_initial_regions; }
  uint num_optional_regions() const { return _num_optional_regions; }
  uint num_expensive_regions() const { return _num_expensive_regions; }

  const G1EvacuationPrediction& initial_prediction() const {
    return _initial_prediction;
  }

  const G1EvacuationPrediction& optional_prediction() const {
    return _optional_prediction;
  }

  void add_initial(G1CSetCandidateGroup* group,
                   const G1EvacuationPrediction& prediction,
                   CSetSelectionBudget* budget);

  void add_optional(G1CSetCandidateGroup* group,
                    const G1EvacuationPrediction& prediction);

  // Add a group toward the required minimum even when its prediction exceeds
  // the available time or old-region copy budget.
  void add_for_minimum(G1CSetCandidateGroup* group,
                       const G1EvacuationPrediction& prediction,
                       CSetSelectionBudget* budget,
                       double time_budget_ms,
                       const char* candidate_type);
};

G1CollectionSet::CandidateSelection::CandidateSelection(G1CollectionSet* collection_set)
  : _num_initial_regions(0),
    _num_optional_regions(0),
    _num_expensive_regions(0),
    _initial_prediction{0.0, 0},
    _optional_prediction{0.0, 0},
    _collection_set(collection_set) {
}

void G1CollectionSet::CandidateSelection::add_initial(G1CSetCandidateGroup* group,
                                                      const G1EvacuationPrediction& prediction,
                                                      CSetSelectionBudget* budget) {
  precond(budget != nullptr);

  budget->_time_budget_ms = remaining_budget_ms(budget->_time_budget_ms, prediction._time_ms);
  consume_old_cset_copy_budget(prediction._bytes_to_copy, budget->_copy_budget_bytes);

  _collection_set->add_group_to_collection_set(group);
  _num_initial_regions += group->length();
  _initial_prediction._time_ms += prediction._time_ms;
  _initial_prediction._bytes_to_copy += prediction._bytes_to_copy;
}

void G1CollectionSet::CandidateSelection::add_optional(G1CSetCandidateGroup* group,
                                                       const G1EvacuationPrediction& prediction) {
  _collection_set->add_optional_group(group);
  _num_optional_regions += group->length();
  _optional_prediction._time_ms += prediction._time_ms;
  _optional_prediction._bytes_to_copy += prediction._bytes_to_copy;
}

void G1CollectionSet::CandidateSelection::add_for_minimum(G1CSetCandidateGroup* group,
                                                          const G1EvacuationPrediction& prediction,
                                                          CSetSelectionBudget* budget,
                                                          double time_budget_ms,
                                                          const char* candidate_type) {
  precond(budget != nullptr);

  if (prediction._time_ms > time_budget_ms) {
    _num_expensive_regions += group->length();
  }

  if (prediction._bytes_to_copy > budget->_copy_budget_bytes) {
    log_debug(gc, ergo, cset)("Minimum %s candidate group %u (%u regions) does not fit "
                              "old-region copy budget: predicted %zuB, budget %zuB. "
                              "Adding to initial collection set anyway.",
                              candidate_type, group->group_id(), group->length(),
                              prediction._bytes_to_copy, budget->_copy_budget_bytes);
  }

  add_initial(group, prediction, budget);
}

class G1CollectionSet::RetainedCandidateSelection : public CandidateSelection {
  uint _num_pinned_regions;

public:
  explicit RetainedCandidateSelection(G1CollectionSet* collection_set);

  void select_required(CSetSelectionBudget* budget);
  // Select retained candidates beyond the required minimum using the remaining
  // pause-time and old-region copy budgets.
  void select_additional_candidates(const CSetSelectionBudget& budget);
};

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
void G1CollectionSet::finalize_old_part(CSetSelectionBudget* budget) {
  precond(budget != nullptr);

  Ticks start_time = Ticks::now();

  if (!candidates()->is_empty()) {
    candidates()->verify();

    bool has_retained_candidates = candidates()->retained_groups().num_regions() > 0;
    RetainedCandidateSelection retained_selection(this);

    // Select old candidates in the following order:
    // 1. Retained candidates required for progress.
    // 2. Required marking candidates, followed by any additional marking candidates.
    // 3. Additional retained candidates.
    //
    // Selecting the required retained candidates first charges their cost to
    // the shared pause-time and evacuation-space budgets. This prevents marking
    // candidates beyond the required minimum from using the budget needed for
    // retained-candidate progress.
    if (has_retained_candidates) {
      retained_selection.select_required(budget);
    }

    if (collector_state()->is_in_mixed_phase()) {
      select_candidates_from_marking(budget);
    } else {
      log_debug(gc, ergo, cset)("Do not add marking candidates to collection set due to pause type.");
    }

    if (has_retained_candidates) {
      retained_selection.select_additional_candidates(*budget);
    }
    candidates()->verify();
  } else {
    log_debug(gc, ergo, cset)("No candidates to reclaim.");
  }

  phase_times()->record_non_young_cset_choice_time_ms((Ticks::now() - start_time).seconds() * MILLIUNITS);
}

static void print_finish_message(const char* reason, bool from_marking) {
  log_debug(gc, ergo, cset)("Finish adding %s candidates to collection set (%s).",
                            from_marking ? "marking" : "retained", reason);
}

class G1CollectionSet::MarkingCandidateSelection : public CandidateSelection {
  CSetSelectionBudget* const _budget;
  G1CSetCandidateGroupList _initial_groups;

  void add_initial(G1CSetCandidateGroup* group,
                   const G1EvacuationPrediction& prediction);

public:
  MarkingCandidateSelection(G1CollectionSet* collection_set,
                            CSetSelectionBudget* budget);

  double time_budget_ms() const {
    return _budget->_time_budget_ms;
  }

  size_t copy_budget_bytes() const {
    return _budget->_copy_budget_bytes;
  }

  uint num_initial_regions() const {
    return CandidateSelection::num_initial_regions();
  }

  uint num_selected_regions() const {
    return num_initial_regions() + num_optional_regions();
  }

  // Add a group required to reach the minimum old CSet length. These groups are
  // selected for initial evacuation even if their prediction exceeds a budget.
  void add_for_minimum(G1CSetCandidateGroup* group,
                       const G1EvacuationPrediction& prediction);

  // Add a group directly to optional evacuation for G1ForceOptionalEvacuation,
  // without consuming the initial selection budgets.
  void add_forced_optional(G1CSetCandidateGroup* group,
                           const G1EvacuationPrediction& prediction);

  // Add a group to initial evacuation if above the optional time threshold
  // and within the copy budget; otherwise make it optional if time remains.
  // Returns false when the predicted time exhausts the time budget.
  bool select_within_budget(G1CSetCandidateGroup* group,
                            const G1EvacuationPrediction& prediction,
                            double optional_threshold_ms);

  void finalize();
};

void G1CollectionSet::add_optional_group(G1CSetCandidateGroup* group) {
  uint optional_region_index = _optional_groups.num_regions();
  _optional_groups.append(group);
  prepare_optional_group(group, optional_region_index);
}

G1CollectionSet::MarkingCandidateSelection::MarkingCandidateSelection(G1CollectionSet* collection_set,
                                                                      CSetSelectionBudget* budget)
  : CandidateSelection(collection_set),
    _budget(budget),
    _initial_groups() {
  assert(_budget != nullptr, "must be");
}

void G1CollectionSet::MarkingCandidateSelection::add_initial(G1CSetCandidateGroup* group,
                                                             const G1EvacuationPrediction& prediction) {
  CandidateSelection::add_initial(group, prediction, _budget);
  _initial_groups.append(group);
}

void G1CollectionSet::MarkingCandidateSelection::add_for_minimum(G1CSetCandidateGroup* group,
                                                                 const G1EvacuationPrediction& prediction) {
  CandidateSelection::add_for_minimum(group, prediction, _budget,
                                      _budget->_time_budget_ms, "marking");
  _initial_groups.append(group);
}

void G1CollectionSet::MarkingCandidateSelection::add_forced_optional(G1CSetCandidateGroup* group,
                                                                     const G1EvacuationPrediction& prediction) {
  add_optional(group, prediction);
}

bool G1CollectionSet::MarkingCandidateSelection::select_within_budget(G1CSetCandidateGroup* group,
                                                                      const G1EvacuationPrediction& prediction,
                                                                      double optional_threshold_ms) {
  double time_budget_after_group_ms =
    remaining_budget_ms(_budget->_time_budget_ms, prediction._time_ms);

  if (time_budget_after_group_ms > optional_threshold_ms &&
      prediction._bytes_to_copy <= _budget->_copy_budget_bytes) {
    add_initial(group, prediction);
    return true;
  }

  if (time_budget_after_group_ms > 0.0) {
    // Initial evacuation must reserve space for evacuating the selected regions.
    // Once that work is complete, this group may fit the optional evacuation
    // budget. Add it to the optional set if there is still pause time for it.
    if (time_budget_after_group_ms > optional_threshold_ms) {
      log_debug(gc, ergo, cset)("Prediction %zuB for group with %u regions does not fit "
                                "old regions copy budget: %zuB. Preparing as optional.",
                                prediction._bytes_to_copy, group->length(),
                                _budget->_copy_budget_bytes);
    }
    _budget->_time_budget_ms = time_budget_after_group_ms;
    add_optional(group, prediction);
    return true;
  }

  print_finish_message("Predicted time too high", true);
  return false;
}

void G1CollectionSet::MarkingCandidateSelection::finalize() {
  G1CSetCandidateGroupList* from_marking_groups = &_collection_set->candidates()->from_marking_groups();

  // Remove selected groups from list of candidate groups.
  if (_initial_groups.length() > 0) {
    _collection_set->candidates()->remove(&_initial_groups);
  }

  if (from_marking_groups->length() == 0) {
    log_debug(gc, ergo, cset)("Marking candidates exhausted.");
  }

  if (num_expensive_regions() > 0) {
    log_debug(gc, ergo, cset)("Added %u marking candidates to collection set although the predicted time was too high.",
                              num_expensive_regions());
  }

  log_debug(gc, ergo, cset)("Finish adding marking candidates to collection set. "
                            "Initial: %u regions (%u groups), optional: %u regions (%u groups), "
                            "predicted initial time: %1.2fms, predicted optional time: %1.2fms, "
                            "predicted initial bytes: %zu, predicted optional bytes: %zu, "
                            "time budget: %1.2fms, old-region copy budget: %zuB",
                            _initial_groups.num_regions(), _initial_groups.length(),
                            _collection_set->_optional_groups.num_regions(), _collection_set->_optional_groups.length(),
                            initial_prediction()._time_ms, optional_prediction()._time_ms,
                            initial_prediction()._bytes_to_copy, optional_prediction()._bytes_to_copy,
                            _budget->_time_budget_ms, _budget->_copy_budget_bytes);

  postcond(_collection_set->_optional_groups.num_regions() == num_optional_regions());
  postcond(_initial_groups.num_regions() == num_initial_regions());
}

void G1CollectionSet::select_candidates_from_marking(CSetSelectionBudget* budget) {
  precond(budget != nullptr);
  assert(_optional_groups.num_regions() == 0, "Optional regions should not already be selected");

  MarkingCandidateSelection selection(this, budget);
  double optional_threshold_ms = selection.time_budget_ms() *
                                 _policy->optional_prediction_fraction();

  uint min_old_cset_length = _policy->calc_min_old_cset_length(candidates()->last_marking_candidates_length());
  uint max_old_cset_length = MAX2(min_old_cset_length, _policy->calc_max_old_cset_length());
  bool enforce_time_budget = _policy->use_adaptive_num_young_regions();

  G1CSetCandidateGroupList* from_marking_groups = &candidates()->from_marking_groups();

  bool make_first_group_optional = G1ForceOptionalEvacuation;

  log_debug(gc, ergo, cset)("Start adding marking candidates to collection set. "
                            "Min %u regions, max %u regions, available %u regions (%u groups), "
                            "time budget %1.2fms, optional threshold %1.2fms, "
                            "old-cset copy budget %zuB",
                            min_old_cset_length, max_old_cset_length, from_marking_groups->num_regions(), from_marking_groups->length(),
                            selection.time_budget_ms(), optional_threshold_ms,
                            selection.copy_budget_bytes());

  for (G1CSetCandidateGroup* group : *from_marking_groups) {
    if (selection.num_selected_regions() >= max_old_cset_length) {
      // Added maximum number of old regions to the CSet.
      print_finish_message("Maximum number of regions reached", true);
      break;
    }

    G1EvacuationPrediction prediction = group->predict_group_evacuation();

    if (make_first_group_optional) {
      make_first_group_optional = false;
      selection.add_forced_optional(group, prediction);
      continue;
    }

    // Add regions to old set until we reach the minimum amount
    if (selection.num_initial_regions() < min_old_cset_length) {
      selection.add_for_minimum(group, prediction);
    } else if (!enforce_time_budget) {
      // In the non-auto-tuning case, we'll finish adding regions
      // to the CSet if we reach the minimum.
      print_finish_message("Region amount reached min", true);
      break;
    } else if (!selection.select_within_budget(group,
                                                prediction,
                                                optional_threshold_ms)) {
      break;
    }
  }

  selection.finalize();
}

G1CollectionSet::RetainedCandidateSelection::RetainedCandidateSelection(G1CollectionSet* collection_set)
  : CandidateSelection(collection_set),
    _num_pinned_regions(0) {
}

void G1CollectionSet::RetainedCandidateSelection::select_required(CSetSelectionBudget* budget) {
  precond(budget != nullptr);

  uint min_regions = _collection_set->_policy->min_retained_old_cset_length();
  G1CSetCandidateGroupList* retained_groups = &_collection_set->candidates()->retained_groups();

  double time_budget_ms = MIN2(budget->_time_budget_ms, _collection_set->_policy->max_time_for_retaining());

  log_debug(gc, ergo, cset)("Start adding required retained candidates to collection set. "
                            "Min %u regions, available %u regions (%u groups), "
                            "time budget %1.2fms, old-region copy budget %zuB",
                            min_regions, retained_groups->num_regions(), retained_groups->length(),
                            time_budget_ms, budget->_copy_budget_bytes);

  G1CSetCandidateGroupList selected_groups;
  for (G1CSetCandidateGroup* group : *retained_groups) {
    if (num_initial_regions() >= min_regions) {
      break;
    }

    assert(group->length() == 1, "Retained groups should have only 1 region");

    G1CollectionSetCandidateInfo* ci = group->at(0);
    if (ci->_r->has_pinned_objects()) {
      // The additional-candidate pass updates pinned-region aging exactly once.
      continue;
    }

    G1EvacuationPrediction prediction = group->predict_group_evacuation();
    add_for_minimum(group, prediction, budget, time_budget_ms, "Retained");
    selected_groups.append(group);
    time_budget_ms = remaining_budget_ms(time_budget_ms, prediction._time_ms);
  }

  _collection_set->candidates()->remove(&selected_groups);

  log_debug(gc, ergo, cset)("Finish adding required retained candidates to collection set. "
                            "Initial: %u, time budget: %1.2fms, old-region copy budget: %zuB",
                            num_initial_regions(), budget->_time_budget_ms,
                            budget->_copy_budget_bytes);
}

void G1CollectionSet::RetainedCandidateSelection::select_additional_candidates(const CSetSelectionBudget& budget) {
  double optional_time_budget_ms =
    remaining_budget_ms(_collection_set->_policy->max_time_for_retaining(),
                        initial_prediction()._time_ms);

  CSetSelectionBudget selection_budget = {MIN2(budget._time_budget_ms, optional_time_budget_ms),
                                          budget._copy_budget_bytes};

  G1CSetCandidateGroupList* retained_groups = &_collection_set->candidates()->retained_groups();

  log_debug(gc, ergo, cset)("Start adding additional retained candidates to collection set. "
                            "Available %u regions (%u groups), time budget %1.2fms, "
                            "optional time budget %1.2fms, old-region copy budget %zuB",
                            retained_groups->num_regions(), retained_groups->length(),
                            selection_budget._time_budget_ms, optional_time_budget_ms,
                            selection_budget._copy_budget_bytes);

  G1CSetCandidateGroupList selected_groups;
  G1CSetCandidateGroupList groups_to_abandon;

  for (G1CSetCandidateGroup* group : *retained_groups) {
    assert(group->length() == 1, "Retained groups should have only 1 region");

    G1CollectionSetCandidateInfo* ci = group->at(0);
    G1HeapRegion* r = ci->_r;

    // If we cannot reclaim that region, ignore it for now.
    if (r->has_pinned_objects()) {
      _num_pinned_regions++;
      if (ci->update_num_unreclaimed()) {
        log_trace(gc, ergo, cset)("Retained candidate %u can not be reclaimed currently. Skipping.", r->hrm_index());
      } else {
        log_trace(gc, ergo, cset)("Retained candidate %u can not be reclaimed currently. Dropping.", r->hrm_index());
        // Drop pinned retained regions to make progress with retained regions. Regions
        // in that list must have been pinned for at least G1NumCollectionsKeepPinned
        // GCs and hence are considered "long lived".
        _collection_set->_g1h->clear_region_attr(r);
        groups_to_abandon.append(group);
        selected_groups.append(group);
      }
      continue;
    }

    G1EvacuationPrediction prediction = group->predict_group_evacuation();
    bool fits_time_budget = prediction._time_ms <= selection_budget._time_budget_ms;
    bool fits_copy_budget = prediction._bytes_to_copy <= selection_budget._copy_budget_bytes;

    if (!fits_copy_budget) {
      log_debug(gc, ergo, cset)("Retained candidate group %u does not fit "
                                "old-region copy budget: predicted %zuB, budget %zuB.",
                                group->group_id(), prediction._bytes_to_copy,
                                selection_budget._copy_budget_bytes);
      if (prediction._time_ms <= optional_time_budget_ms) {
        add_optional(group, prediction);
        selection_budget._time_budget_ms =
          remaining_budget_ms(selection_budget._time_budget_ms, prediction._time_ms);
      } else {
        print_finish_message("Predicted copy bytes too high", false);
        break;
      }
    } else if (fits_time_budget) {
      add_initial(group, prediction, &selection_budget);
      selected_groups.append(group);
    } else if (prediction._time_ms <= optional_time_budget_ms) {
      add_optional(group, prediction);
      selection_budget._time_budget_ms =
        remaining_budget_ms(selection_budget._time_budget_ms, prediction._time_ms);
    } else {
      // Fits neither initial nor optional time limit. Exit.
      break;
    }
    optional_time_budget_ms = remaining_budget_ms(optional_time_budget_ms, prediction._time_ms);
  }

  _collection_set->candidates()->remove(&selected_groups);
  groups_to_abandon.clear(true /* uninstall_group_cardset */);

  if (retained_groups->length() == 0) {
    log_debug(gc, ergo, cset)("Retained candidates exhausted.");
  }

  if (num_expensive_regions() > 0) {
    log_debug(gc, ergo, cset)("Added %u retained candidates to collection set "
                              "although the predicted time was too high.",
                              num_expensive_regions());
  }

  log_debug(gc, ergo, cset)("Finish adding retained candidates to collection set. "
                            "Initial: %u, optional: %u, pinned: %u, "
                            "predicted initial time: %1.2fms, predicted optional time: %1.2fms, "
                            "predicted initial bytes: %zu, predicted optional bytes: %zu, "
                            "time budget: %1.2fms, optional time budget %1.2fms, "
                            "old-region copy budget: %zuB",
                            num_initial_regions(), num_optional_regions(), _num_pinned_regions,
                            initial_prediction()._time_ms, optional_prediction()._time_ms,
                            initial_prediction()._bytes_to_copy, optional_prediction()._bytes_to_copy,
                            selection_budget._time_budget_ms, optional_time_budget_ms,
                            selection_budget._copy_budget_bytes);
}

uint G1CollectionSet::select_optional_groups(double time_budget_ms) {
  uint total_optional_regions = num_optional_regions();
  assert(total_optional_regions > 0,
         "Should only be called when there are optional regions");

  uint num_regions_selected = 0;
  // TODO: we do not account for remaining reference processing.
  // So prematurely consider that previous evacuations are completed.
  size_t copy_budget_bytes = old_cset_copy_budget_bytes(0, 0);

  double total_prediction_ms = 0.0;
  size_t total_bytes_to_copy = 0;
  G1CSetCandidateGroupList selected;
  for (G1CSetCandidateGroup* group : _optional_groups) {
    G1EvacuationPrediction prediction = group->predict_group_evacuation();
    double predicted_time_ms = prediction._time_ms;
    size_t predicted_bytes_to_copy = prediction._bytes_to_copy;

    if (predicted_time_ms > time_budget_ms) {
      log_debug(gc, ergo, cset)("Prediction %.3fms for group with %u regions does not fit "
                                "time budget: %.3fms.",
                                predicted_time_ms, group->length(), time_budget_ms);
      break;
    }
    if (predicted_bytes_to_copy > copy_budget_bytes) {
      log_debug(gc, ergo, cset)("Prediction %zuB for group with %u regions does not fit "
                                "old-region copy budget: %zuB.",
                                predicted_bytes_to_copy, group->length(), copy_budget_bytes);
      break;
    }

    total_prediction_ms += predicted_time_ms;
    total_bytes_to_copy += predicted_bytes_to_copy;
    time_budget_ms -= predicted_time_ms;

    consume_old_cset_copy_budget(predicted_bytes_to_copy, copy_budget_bytes);

    num_regions_selected += group->length();

    add_group_to_collection_set(group);
    selected.append(group);
  }

  log_debug(gc, ergo, cset)("Completed with groups, selected %u region in %u groups, "
                            "predicted copy bytes: %zuB, old-region copy budget: %zuB",
                            num_regions_selected, selected.length(), total_bytes_to_copy,
                            copy_budget_bytes);
  // Remove selected groups from candidate list.
  if (selected.length() > 0) {
    _optional_groups.remove(&selected);
    candidates()->remove(&selected);
  }

  log_debug(gc, ergo, cset)("Prepared %u regions out of %u for optional evacuation. "
                            "Total predicted time: %.3fms, old-region copy budget: %zuB",
                            num_regions_selected, total_optional_regions, total_prediction_ms,
                            copy_budget_bytes);

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
  _groups.append(gr);
}

void G1CollectionSet::add_region_to_collection_set(G1HeapRegion* r) {
  _g1h->clear_region_attr(r);
  assert(r->rem_set()->is_complete(), "Remset for region %u complete", r->hrm_index());
  add_old_region(r);
}

void G1CollectionSet::finalize_initial_collection_set(double target_pause_time_ms, G1SurvivorRegions* survivor) {
  assert(_regions_inc_part_start == 0, "must be");
  assert(_groups_inc_part_start == 0, "must be");

  CSetSelectionBudget budget = finalize_young_part(target_pause_time_ms, survivor);
  finalize_old_part(&budget);

  stop_incremental_building();
}

bool G1CollectionSet::finalize_optional_for_evacuation(double time_budget_ms) {
  continue_incremental_building();

  uint num_regions_selected = select_optional_groups(time_budget_ms);

  stop_incremental_building();

  _g1h->verify_region_attr_is_remset_tracked();

  return num_regions_selected > 0;
}

void G1CollectionSet::abandon_optional_collection_set(G1ParScanThreadStateSet* pss) {
  if (_optional_groups.length() > 0) {
    auto reset = [&] (G1HeapRegion* r) {
      pss->record_unused_optional_region(r);
      // Clear collection set marker and make sure that the remembered set information
      // is correct as we still need it later.
      _g1h->clear_region_attr(r);
      _g1h->update_region_attr(r);
      r->clear_index_in_opt_cset();
    };

    _optional_groups.iterate(reset);
    // Remove groups from list without deleting the groups or clearing the associated cardsets.
    _optional_groups.remove_selected(_optional_groups.length(), _optional_groups.num_regions());
  }

  _g1h->verify_region_attr_is_remset_tracked();
}

#ifdef ASSERT
class G1VerifyYoungCSetIndicesClosure : public G1HeapRegionClosure {
  uint _num_young_regions;
  uint* _heap_region_indices;
public:
  G1VerifyYoungCSetIndicesClosure(uint num_young_regions) : G1HeapRegionClosure(), _num_young_regions(num_young_regions) {
    _heap_region_indices = NEW_C_HEAP_ARRAY(uint, num_young_regions + 1, mtGC);
    for (uint i = 0; i < num_young_regions + 1; i++) {
      _heap_region_indices[i] = UINT_MAX;
    }
  }
  ~G1VerifyYoungCSetIndicesClosure() {
    FREE_C_HEAP_ARRAY(_heap_region_indices);
  }

  virtual bool do_heap_region(G1HeapRegion* r) {
    const uint idx = r->young_index_in_cset();

    assert(r->is_young(), "must be, but region %u is not", r->hrm_index());
    assert(idx > 0, "Young index must be set for all regions in the collection set but is not for region %u.", r->hrm_index());
    assert(idx <= _num_young_regions, "Young cset index %u too large for region %u", idx, r->hrm_index());

    assert(_heap_region_indices[idx] == UINT_MAX,
           "Index %d used by multiple regions, first use by region %u, second by region %u",
           idx, _heap_region_indices[idx], r->hrm_index());

    _heap_region_indices[idx] = r->hrm_index();

    return false;
  }
};

void G1CollectionSet::verify_young_cset_indices() const {
  assert_at_safepoint_on_vm_thread();

  G1VerifyYoungCSetIndicesClosure cl(num_regions());
  iterate(&cl);
}
#endif

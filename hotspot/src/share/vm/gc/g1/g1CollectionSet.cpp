/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/g1/heapRegionSet.hpp"
#include "utilities/debug.hpp"

G1CollectorState* G1CollectionSet::collector_state() {
  return _g1->collector_state();
}

G1GCPhaseTimes* G1CollectionSet::phase_times() {
  return _policy->phase_times();
}

CollectionSetChooser* G1CollectionSet::cset_chooser() {
  return _cset_chooser;
}

double G1CollectionSet::predict_region_elapsed_time_ms(HeapRegion* hr) {
  return _policy->predict_region_elapsed_time_ms(hr, collector_state()->gcs_are_young());
}


G1CollectionSet::G1CollectionSet(G1CollectedHeap* g1h, G1Policy* policy) :
  _g1(g1h),
  _policy(policy),
  _cset_chooser(new CollectionSetChooser()),
  _eden_region_length(0),
  _survivor_region_length(0),
  _old_region_length(0),

  _head(NULL),
  _bytes_used_before(0),
  _recorded_rs_lengths(0),
  // Incremental CSet attributes
  _inc_build_state(Inactive),
  _inc_head(NULL),
  _inc_tail(NULL),
  _inc_bytes_used_before(0),
  _inc_recorded_rs_lengths(0),
  _inc_recorded_rs_lengths_diffs(0),
  _inc_predicted_elapsed_time_ms(0.0),
  _inc_predicted_elapsed_time_ms_diffs(0.0) {}

G1CollectionSet::~G1CollectionSet() {
  delete _cset_chooser;
}

void G1CollectionSet::init_region_lengths(uint eden_cset_region_length,
                                          uint survivor_cset_region_length) {
  _eden_region_length     = eden_cset_region_length;
  _survivor_region_length = survivor_cset_region_length;
  _old_region_length      = 0;
}

void G1CollectionSet::set_recorded_rs_lengths(size_t rs_lengths) {
  _recorded_rs_lengths = rs_lengths;
}

// Add the heap region at the head of the non-incremental collection set
void G1CollectionSet::add_old_region(HeapRegion* hr) {
  assert(_inc_build_state == Active, "Precondition");
  assert(hr->is_old(), "the region should be old");

  assert(!hr->in_collection_set(), "should not already be in the CSet");
  _g1->register_old_region_with_cset(hr);
  hr->set_next_in_collection_set(_head);
  _head = hr;
  _bytes_used_before += hr->used();
  size_t rs_length = hr->rem_set()->occupied();
  _recorded_rs_lengths += rs_length;
  _old_region_length += 1;
}

// Initialize the per-collection-set information
void G1CollectionSet::start_incremental_building() {
  assert(_inc_build_state == Inactive, "Precondition");

  _inc_head = NULL;
  _inc_tail = NULL;
  _inc_bytes_used_before = 0;

  _inc_recorded_rs_lengths = 0;
  _inc_recorded_rs_lengths_diffs = 0;
  _inc_predicted_elapsed_time_ms = 0.0;
  _inc_predicted_elapsed_time_ms_diffs = 0.0;
  _inc_build_state = Active;
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
  assert(hr->young_index_in_cset() > -1, "should have already been set");
  assert(_inc_build_state == Active, "Precondition");

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

  size_t rs_length = hr->rem_set()->occupied();
  double region_elapsed_time_ms = predict_region_elapsed_time_ms(hr);

  // Cache the values we have added to the aggregated information
  // in the heap region in case we have to remove this region from
  // the incremental collection set, or it is updated by the
  // rset sampling code
  hr->set_recorded_rs_length(rs_length);
  hr->set_predicted_elapsed_time_ms(region_elapsed_time_ms);

  size_t used_bytes = hr->used();
  _inc_recorded_rs_lengths += rs_length;
  _inc_predicted_elapsed_time_ms += region_elapsed_time_ms;
  _inc_bytes_used_before += used_bytes;

  assert(!hr->in_collection_set(), "invariant");
  _g1->register_young_region_with_cset(hr);
  assert(hr->next_in_collection_set() == NULL, "invariant");
}

// Add the region at the RHS of the incremental cset
void G1CollectionSet::add_survivor_regions(HeapRegion* hr) {
  // We should only ever be appending survivors at the end of a pause
  assert(hr->is_survivor(), "Logic");

  // Do the 'common' stuff
  add_young_region_common(hr);

  // Now add the region at the right hand side
  if (_inc_tail == NULL) {
    assert(_inc_head == NULL, "invariant");
    _inc_head = hr;
  } else {
    _inc_tail->set_next_in_collection_set(hr);
  }
  _inc_tail = hr;
}

// Add the region to the LHS of the incremental cset
void G1CollectionSet::add_eden_region(HeapRegion* hr) {
  // Survivors should be added to the RHS at the end of a pause
  assert(hr->is_eden(), "Logic");

  // Do the 'common' stuff
  add_young_region_common(hr);

  // Add the region at the left hand side
  hr->set_next_in_collection_set(_inc_head);
  if (_inc_head == NULL) {
    assert(_inc_tail == NULL, "Invariant");
    _inc_tail = hr;
  }
  _inc_head = hr;
}

#ifndef PRODUCT
void G1CollectionSet::print(HeapRegion* list_head, outputStream* st) {
  assert(list_head == inc_head() || list_head == head(), "must be");

  st->print_cr("\nCollection_set:");
  HeapRegion* csr = list_head;
  while (csr != NULL) {
    HeapRegion* next = csr->next_in_collection_set();
    assert(csr->in_collection_set(), "bad CS");
    st->print_cr("  " HR_FORMAT ", P: " PTR_FORMAT "N: " PTR_FORMAT ", age: %4d",
                 HR_FORMAT_PARAMS(csr),
                 p2i(csr->prev_top_at_mark_start()), p2i(csr->next_top_at_mark_start()),
                 csr->age_in_surv_rate_group_cond());
    csr = next;
  }
}
#endif // !PRODUCT

double G1CollectionSet::finalize_young_part(double target_pause_time_ms) {
  double young_start_time_sec = os::elapsedTime();

  YoungList* young_list = _g1->young_list();
  finalize_incremental_building();

  guarantee(target_pause_time_ms > 0.0,
            "target_pause_time_ms = %1.6lf should be positive", target_pause_time_ms);
  guarantee(_head == NULL, "Precondition");

  size_t pending_cards = _policy->pending_cards();
  double base_time_ms = _policy->predict_base_elapsed_time_ms(pending_cards);
  double time_remaining_ms = MAX2(target_pause_time_ms - base_time_ms, 0.0);

  log_trace(gc, ergo, cset)("Start choosing CSet. pending cards: " SIZE_FORMAT " predicted base time: %1.2fms remaining time: %1.2fms target pause time: %1.2fms",
                            pending_cards, base_time_ms, time_remaining_ms, target_pause_time_ms);

  collector_state()->set_last_gc_was_young(collector_state()->gcs_are_young());

  // The young list is laid with the survivor regions from the previous
  // pause are appended to the RHS of the young list, i.e.
  //   [Newly Young Regions ++ Survivors from last pause].

  uint survivor_region_length = young_list->survivor_length();
  uint eden_region_length = young_list->eden_length();
  init_region_lengths(eden_region_length, survivor_region_length);

  HeapRegion* hr = young_list->first_survivor_region();
  while (hr != NULL) {
    assert(hr->is_survivor(), "badly formed young list");
    // There is a convention that all the young regions in the CSet
    // are tagged as "eden", so we do this for the survivors here. We
    // use the special set_eden_pre_gc() as it doesn't check that the
    // region is free (which is not the case here).
    hr->set_eden_pre_gc();
    hr = hr->get_next_young_region();
  }

  // Clear the fields that point to the survivor list - they are all young now.
  young_list->clear_survivors();

  _head = _inc_head;
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

void G1CollectionSet::finalize_old_part(double time_remaining_ms) {
  double non_young_start_time_sec = os::elapsedTime();
  double predicted_old_time_ms = 0.0;

  if (!collector_state()->gcs_are_young()) {
    cset_chooser()->verify();
    const uint min_old_cset_length = _policy->calc_min_old_cset_length();
    const uint max_old_cset_length = _policy->calc_max_old_cset_length();

    uint expensive_region_num = 0;
    bool check_time_remaining = _policy->adaptive_young_list_length();

    HeapRegion* hr = cset_chooser()->peek();
    while (hr != NULL) {
      if (old_region_length() >= max_old_cset_length) {
        // Added maximum number of old regions to the CSet.
        log_debug(gc, ergo, cset)("Finish adding old regions to CSet (old CSet region num reached max). old %u regions, max %u regions",
                                  old_region_length(), max_old_cset_length);
        break;
      }

      // Stop adding regions if the remaining reclaimable space is
      // not above G1HeapWastePercent.
      size_t reclaimable_bytes = cset_chooser()->remaining_reclaimable_bytes();
      double reclaimable_perc = _policy->reclaimable_bytes_perc(reclaimable_bytes);
      double threshold = (double) G1HeapWastePercent;
      if (reclaimable_perc <= threshold) {
        // We've added enough old regions that the amount of uncollected
        // reclaimable space is at or below the waste threshold. Stop
        // adding old regions to the CSet.
        log_debug(gc, ergo, cset)("Finish adding old regions to CSet (reclaimable percentage not over threshold). "
                                  "old %u regions, max %u regions, reclaimable: " SIZE_FORMAT "B (%1.2f%%) threshold: " UINTX_FORMAT "%%",
                                  old_region_length(), max_old_cset_length, reclaimable_bytes, reclaimable_perc, G1HeapWastePercent);
        break;
      }

      double predicted_time_ms = predict_region_elapsed_time_ms(hr);
      if (check_time_remaining) {
        if (predicted_time_ms > time_remaining_ms) {
          // Too expensive for the current CSet.

          if (old_region_length() >= min_old_cset_length) {
            // We have added the minimum number of old regions to the CSet,
            // we are done with this CSet.
            log_debug(gc, ergo, cset)("Finish adding old regions to CSet (predicted time is too high). "
                                      "predicted time: %1.2fms, remaining time: %1.2fms old %u regions, min %u regions",
                                      predicted_time_ms, time_remaining_ms, old_region_length(), min_old_cset_length);
            break;
          }

          // We'll add it anyway given that we haven't reached the
          // minimum number of old regions.
          expensive_region_num += 1;
        }
      } else {
        if (old_region_length() >= min_old_cset_length) {
          // In the non-auto-tuning case, we'll finish adding regions
          // to the CSet if we reach the minimum.

          log_debug(gc, ergo, cset)("Finish adding old regions to CSet (old CSet region num reached min). old %u regions, min %u regions",
                                    old_region_length(), min_old_cset_length);
          break;
        }
      }

      // We will add this region to the CSet.
      time_remaining_ms = MAX2(time_remaining_ms - predicted_time_ms, 0.0);
      predicted_old_time_ms += predicted_time_ms;
      cset_chooser()->pop(); // already have region via peek()
      _g1->old_set_remove(hr);
      add_old_region(hr);

      hr = cset_chooser()->peek();
    }
    if (hr == NULL) {
      log_debug(gc, ergo, cset)("Finish adding old regions to CSet (candidate old regions not available)");
    }

    if (expensive_region_num > 0) {
      // We print the information once here at the end, predicated on
      // whether we added any apparently expensive regions or not, to
      // avoid generating output per region.
      log_debug(gc, ergo, cset)("Added expensive regions to CSet (old CSet region num not reached min)."
                                "old: %u regions, expensive: %u regions, min: %u regions, remaining time: %1.2fms",
                                old_region_length(), expensive_region_num, min_old_cset_length, time_remaining_ms);
    }

    cset_chooser()->verify();
  }

  stop_incremental_building();

  log_debug(gc, ergo, cset)("Finish choosing CSet. old: %u regions, predicted old region time: %1.2fms, time remaining: %1.2f",
                            old_region_length(), predicted_old_time_ms, time_remaining_ms);

  double non_young_end_time_sec = os::elapsedTime();
  phase_times()->record_non_young_cset_choice_time_ms((non_young_end_time_sec - non_young_start_time_sec) * 1000.0);
}

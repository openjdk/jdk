/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1ConcurrentRefineThreadsNeeded.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1Policy.hpp"
#include "utilities/globalDefinitions.hpp"
#include <math.h>

G1ConcurrentRefineThreadsNeeded::G1ConcurrentRefineThreadsNeeded(G1Policy* policy,
                                                                 double update_period_ms) :
  _policy(policy),
  _update_period_ms(update_period_ms),
  _predicted_time_until_next_gc_ms(0.0),
  _predicted_cards_at_next_gc(0),
  _threads_needed(0)
{}

// Estimate how many concurrent refinement threads we need to run to achieve
// the target number of card by the time the next GC happens.  There are
// several secondary goals we'd like to achieve while meeting that goal.
//
// 1. Minimize the number of refinement threads running at once.
//
// 2. Minimize the number of activations and deactivations for the
// refinement threads that run.
//
// 3. Delay performing refinement work.  Having more dirty cards waiting to
// be refined can be beneficial, as further writes to the same card don't
// create more work.
void G1ConcurrentRefineThreadsNeeded::update(uint active_threads,
                                             size_t available_bytes,
                                             size_t num_cards,
                                             size_t target_num_cards) {
  const G1Analytics* analytics = _policy->analytics();

  // Estimate time until next GC, based on remaining bytes available for
  // allocation and the allocation rate.
  double alloc_region_rate = analytics->predict_alloc_rate_ms();
  double alloc_bytes_rate = alloc_region_rate * HeapRegion::GrainBytes;
  if (alloc_bytes_rate == 0.0) {
    // A zero rate indicates we don't yet have data to use for predictions.
    // Since we don't have any idea how long until the next GC, use a time of
    // zero.
    _predicted_time_until_next_gc_ms = 0.0;
  } else {
    // If the heap size is large and the allocation rate is small, we can get
    // a predicted time until next GC that is so large it can cause problems
    // (such as overflow) in other calculations.  Limit the prediction to one
    // hour, which is still large in this context.
    const double one_hour_ms = 60.0 * 60.0 * MILLIUNITS;
    double raw_time_ms = available_bytes / alloc_bytes_rate;
    _predicted_time_until_next_gc_ms = MIN2(raw_time_ms, one_hour_ms);
  }

  // Estimate number of cards that need to be processed before next GC.  There
  // are no incoming cards when time is short, because in that case the
  // controller activates refinement by mutator threads to stay on target even
  // if threads deactivate in the meantime.  This also covers the case of not
  // having a real prediction of time until GC.
  size_t incoming_cards = 0;
  if (_predicted_time_until_next_gc_ms > _update_period_ms) {
    double incoming_rate = analytics->predict_dirtied_cards_rate_ms();
    double raw_cards = incoming_rate * _predicted_time_until_next_gc_ms;
    incoming_cards = static_cast<size_t>(raw_cards);
  }
  size_t total_cards = num_cards + incoming_cards;
  _predicted_cards_at_next_gc = total_cards;

  // No concurrent refinement needed.
  if (total_cards <= target_num_cards) {
    // We don't expect to exceed the target before the next GC.
    _threads_needed = 0;
    return;
  }

  // The calculation of the number of threads needed isn't very stable when
  // time is short, and can lead to starting up lots of threads for not much
  // profit.  If we're in the last update period, don't change the number of
  // threads running, other than to treat the current thread as running.  That
  // might not be sufficient, but hopefully we were already reasonably close.
  // We won't accumulate more because mutator refinement will be activated.
  if (_predicted_time_until_next_gc_ms <= _update_period_ms) {
    _threads_needed = MAX2(active_threads, 1u);
    return;
  }

  // Estimate the number of cards that need to be refined before the next GC
  // to meet the goal.
  size_t cards_needed = total_cards - target_num_cards;

  // Estimate the rate at which a thread can refine cards.  If we don't yet
  // have an estimate then only request one running thread, since we do have
  // excess cards to process.  Just one thread might not be sufficient, but
  // we don't have any idea how many we actually need.  Eventually the
  // prediction machinery will warm up and we'll be able to get estimates.
  double refine_rate = analytics->predict_concurrent_refine_rate_ms();
  if (refine_rate == 0.0) {
    _threads_needed = 1;
    return;
  }

  // Estimate the number of refinement threads we need to run in order to
  // reach the goal in time.
  double thread_capacity = refine_rate * _predicted_time_until_next_gc_ms;
  double nthreads = cards_needed / thread_capacity;

  // Decide how to round nthreads to an integral number of threads.  Always
  // rounding up is contrary to delaying refinement work.  But when we're
  // close to the next GC we want to drive toward the target, so round up
  // then.  The rest of the time we round to nearest, trying to remain near
  // the middle of the range.
  if (_predicted_time_until_next_gc_ms <= _update_period_ms * 5.0) {
    nthreads = ::ceil(nthreads);
  } else {
    nthreads = ::round(nthreads);
  }

  _threads_needed = static_cast<uint>(MIN2<size_t>(nthreads, UINT_MAX));
}

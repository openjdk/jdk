/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/pair.hpp"
#include <math.h>

G1ConcurrentRefineThread* G1ConcurrentRefineThreadControl::create_refinement_thread(uint worker_id, bool initializing) {
  G1ConcurrentRefineThread* result = NULL;
  if (initializing || !InjectGCWorkerCreationFailure) {
    result = new G1ConcurrentRefineThread(_cr, worker_id);
  }
  if (result == NULL || result->osthread() == NULL) {
    log_warning(gc)("Failed to create refinement thread %u, no more %s",
                    worker_id,
                    result == NULL ? "memory" : "OS threads");
  }
  return result;
}

G1ConcurrentRefineThreadControl::G1ConcurrentRefineThreadControl() :
  _cr(NULL),
  _threads(NULL),
  _num_max_threads(0)
{
}

G1ConcurrentRefineThreadControl::~G1ConcurrentRefineThreadControl() {
  for (uint i = 0; i < _num_max_threads; i++) {
    G1ConcurrentRefineThread* t = _threads[i];
    if (t != NULL) {
      delete t;
    }
  }
  FREE_C_HEAP_ARRAY(G1ConcurrentRefineThread*, _threads);
}

jint G1ConcurrentRefineThreadControl::initialize(G1ConcurrentRefine* cr, uint num_max_threads) {
  assert(cr != NULL, "G1ConcurrentRefine must not be NULL");
  _cr = cr;
  _num_max_threads = num_max_threads;

  _threads = NEW_C_HEAP_ARRAY_RETURN_NULL(G1ConcurrentRefineThread*, num_max_threads, mtGC);
  if (_threads == NULL) {
    vm_shutdown_during_initialization("Could not allocate thread holder array.");
    return JNI_ENOMEM;
  }

  for (uint i = 0; i < num_max_threads; i++) {
    if (UseDynamicNumberOfGCThreads && i != 0 /* Always start first thread. */) {
      _threads[i] = NULL;
    } else {
      _threads[i] = create_refinement_thread(i, true);
      if (_threads[i] == NULL) {
        vm_shutdown_during_initialization("Could not allocate refinement threads.");
        return JNI_ENOMEM;
      }
    }
  }
  return JNI_OK;
}

void G1ConcurrentRefineThreadControl::maybe_activate_next(uint cur_worker_id) {
  assert(cur_worker_id < _num_max_threads,
         "Activating another thread from %u not allowed since there can be at most %u",
         cur_worker_id, _num_max_threads);
  if (cur_worker_id == (_num_max_threads - 1)) {
    // Already the last thread, there is no more thread to activate.
    return;
  }

  uint worker_id = cur_worker_id + 1;
  G1ConcurrentRefineThread* thread_to_activate = _threads[worker_id];
  if (thread_to_activate == NULL) {
    // Still need to create the thread...
    _threads[worker_id] = create_refinement_thread(worker_id, false);
    thread_to_activate = _threads[worker_id];
  }
  if (thread_to_activate != NULL && !thread_to_activate->is_active()) {
    thread_to_activate->activate();
  }
}

void G1ConcurrentRefineThreadControl::print_on(outputStream* st) const {
  for (uint i = 0; i < _num_max_threads; ++i) {
    if (_threads[i] != NULL) {
      _threads[i]->print_on(st);
      st->cr();
    }
  }
}

void G1ConcurrentRefineThreadControl::worker_threads_do(ThreadClosure* tc) {
  for (uint i = 0; i < _num_max_threads; i++) {
    if (_threads[i] != NULL) {
      tc->do_thread(_threads[i]);
    }
  }
}

void G1ConcurrentRefineThreadControl::stop() {
  for (uint i = 0; i < _num_max_threads; i++) {
    if (_threads[i] != NULL) {
      _threads[i]->stop();
    }
  }
}

// Arbitrary but large limits, to simplify some of the zone calculations.
// The general idea is to allow expressions like
//   MIN2(x OP y, max_XXX_zone)
// without needing to check for overflow in "x OP y", because the
// ranges for x and y have been restricted.
STATIC_ASSERT(sizeof(LP64_ONLY(jint) NOT_LP64(jshort)) <= (sizeof(size_t)/2));
const size_t max_yellow_zone = LP64_ONLY(max_jint) NOT_LP64(max_jshort);
const size_t max_green_zone = max_yellow_zone / 2;
const size_t max_red_zone = INT_MAX; // For dcqs.set_max_completed_queue.
STATIC_ASSERT(max_yellow_zone <= max_red_zone);

// Range check assertions for green zone values.
#define assert_zone_constraints_g(green)                        \
  do {                                                          \
    size_t azc_g_green = (green);                               \
    assert(azc_g_green <= max_green_zone,                       \
           "green exceeds max: " SIZE_FORMAT, azc_g_green);     \
  } while (0)

// Range check assertions for green and yellow zone values.
#define assert_zone_constraints_gy(green, yellow)                       \
  do {                                                                  \
    size_t azc_gy_green = (green);                                      \
    size_t azc_gy_yellow = (yellow);                                    \
    assert_zone_constraints_g(azc_gy_green);                            \
    assert(azc_gy_yellow <= max_yellow_zone,                            \
           "yellow exceeds max: " SIZE_FORMAT, azc_gy_yellow);          \
    assert(azc_gy_green <= azc_gy_yellow,                               \
           "green (" SIZE_FORMAT ") exceeds yellow (" SIZE_FORMAT ")",  \
           azc_gy_green, azc_gy_yellow);                                \
  } while (0)

// Range check assertions for green, yellow, and red zone values.
#define assert_zone_constraints_gyr(green, yellow, red)                 \
  do {                                                                  \
    size_t azc_gyr_green = (green);                                     \
    size_t azc_gyr_yellow = (yellow);                                   \
    size_t azc_gyr_red = (red);                                         \
    assert_zone_constraints_gy(azc_gyr_green, azc_gyr_yellow);          \
    assert(azc_gyr_red <= max_red_zone,                                 \
           "red exceeds max: " SIZE_FORMAT, azc_gyr_red);               \
    assert(azc_gyr_yellow <= azc_gyr_red,                               \
           "yellow (" SIZE_FORMAT ") exceeds red (" SIZE_FORMAT ")",    \
           azc_gyr_yellow, azc_gyr_red);                                \
  } while (0)

// Logging tag sequence for refinement control updates.
#define CTRL_TAGS gc, ergo, refine

// For logging zone values, ensuring consistency of level and tags.
#define LOG_ZONES(...) log_debug( CTRL_TAGS )(__VA_ARGS__)

// Package for pair of refinement thread activation and deactivation
// thresholds.  The activation and deactivation levels are resp. the first
// and second values of the pair.
typedef Pair<size_t, size_t> Thresholds;
inline size_t activation_level(const Thresholds& t) { return t.first; }
inline size_t deactivation_level(const Thresholds& t) { return t.second; }

static Thresholds calc_thresholds(size_t green_zone,
                                  size_t yellow_zone,
                                  uint worker_i) {
  double yellow_size = yellow_zone - green_zone;
  double step = yellow_size / G1ConcurrentRefine::max_num_threads();
  if (worker_i == 0) {
    // Potentially activate worker 0 more aggressively, to keep
    // available buffers near green_zone value.  When yellow_size is
    // large we don't want to allow a full step to accumulate before
    // doing any processing, as that might lead to significantly more
    // than green_zone buffers to be processed by update_rs.
    step = MIN2(step, ParallelGCThreads / 2.0);
  }
  size_t activate_offset = static_cast<size_t>(ceil(step * (worker_i + 1)));
  size_t deactivate_offset = static_cast<size_t>(floor(step * worker_i));
  return Thresholds(green_zone + activate_offset,
                    green_zone + deactivate_offset);
}

G1ConcurrentRefine::G1ConcurrentRefine(size_t green_zone,
                                       size_t yellow_zone,
                                       size_t red_zone,
                                       size_t min_yellow_zone_size) :
  _thread_control(),
  _green_zone(green_zone),
  _yellow_zone(yellow_zone),
  _red_zone(red_zone),
  _min_yellow_zone_size(min_yellow_zone_size)
{
  assert_zone_constraints_gyr(green_zone, yellow_zone, red_zone);
}

jint G1ConcurrentRefine::initialize() {
  return _thread_control.initialize(this, max_num_threads());
}

static size_t calc_min_yellow_zone_size() {
  size_t step = G1ConcRefinementThresholdStep;
  uint n_workers = G1ConcurrentRefine::max_num_threads();
  if ((max_yellow_zone / step) < n_workers) {
    return max_yellow_zone;
  } else {
    return step * n_workers;
  }
}

static size_t calc_init_green_zone() {
  size_t green = G1ConcRefinementGreenZone;
  if (FLAG_IS_DEFAULT(G1ConcRefinementGreenZone)) {
    green = ParallelGCThreads;
  }
  return MIN2(green, max_green_zone);
}

static size_t calc_init_yellow_zone(size_t green, size_t min_size) {
  size_t config = G1ConcRefinementYellowZone;
  size_t size = 0;
  if (FLAG_IS_DEFAULT(G1ConcRefinementYellowZone)) {
    size = green * 2;
  } else if (green < config) {
    size = config - green;
  }
  size = MAX2(size, min_size);
  size = MIN2(size, max_yellow_zone);
  return MIN2(green + size, max_yellow_zone);
}

static size_t calc_init_red_zone(size_t green, size_t yellow) {
  size_t size = yellow - green;
  if (!FLAG_IS_DEFAULT(G1ConcRefinementRedZone)) {
    size_t config = G1ConcRefinementRedZone;
    if (yellow < config) {
      size = MAX2(size, config - yellow);
    }
  }
  return MIN2(yellow + size, max_red_zone);
}

G1ConcurrentRefine* G1ConcurrentRefine::create(jint* ecode) {
  size_t min_yellow_zone_size = calc_min_yellow_zone_size();
  size_t green_zone = calc_init_green_zone();
  size_t yellow_zone = calc_init_yellow_zone(green_zone, min_yellow_zone_size);
  size_t red_zone = calc_init_red_zone(green_zone, yellow_zone);

  LOG_ZONES("Initial Refinement Zones: "
            "green: " SIZE_FORMAT ", "
            "yellow: " SIZE_FORMAT ", "
            "red: " SIZE_FORMAT ", "
            "min yellow size: " SIZE_FORMAT,
            green_zone, yellow_zone, red_zone, min_yellow_zone_size);

  G1ConcurrentRefine* cr = new G1ConcurrentRefine(green_zone,
                                                  yellow_zone,
                                                  red_zone,
                                                  min_yellow_zone_size);

  if (cr == NULL) {
    *ecode = JNI_ENOMEM;
    vm_shutdown_during_initialization("Could not create G1ConcurrentRefine");
    return NULL;
  }

  *ecode = cr->initialize();
  return cr;
}

void G1ConcurrentRefine::stop() {
  _thread_control.stop();
}

G1ConcurrentRefine::~G1ConcurrentRefine() {
}

void G1ConcurrentRefine::threads_do(ThreadClosure *tc) {
  _thread_control.worker_threads_do(tc);
}

uint G1ConcurrentRefine::max_num_threads() {
  return G1ConcRefinementThreads;
}

void G1ConcurrentRefine::print_threads_on(outputStream* st) const {
  _thread_control.print_on(st);
}

static size_t calc_new_green_zone(size_t green,
                                  double update_rs_time,
                                  size_t update_rs_processed_buffers,
                                  double goal_ms) {
  // Adjust green zone based on whether we're meeting the time goal.
  // Limit to max_green_zone.
  const double inc_k = 1.1, dec_k = 0.9;
  if (update_rs_time > goal_ms) {
    if (green > 0) {
      green = static_cast<size_t>(green * dec_k);
    }
  } else if (update_rs_time < goal_ms &&
             update_rs_processed_buffers > green) {
    green = static_cast<size_t>(MAX2(green * inc_k, green + 1.0));
    green = MIN2(green, max_green_zone);
  }
  return green;
}

static size_t calc_new_yellow_zone(size_t green, size_t min_yellow_size) {
  size_t size = green * 2;
  size = MAX2(size, min_yellow_size);
  return MIN2(green + size, max_yellow_zone);
}

static size_t calc_new_red_zone(size_t green, size_t yellow) {
  return MIN2(yellow + (yellow - green), max_red_zone);
}

void G1ConcurrentRefine::update_zones(double update_rs_time,
                                      size_t update_rs_processed_buffers,
                                      double goal_ms) {
  log_trace( CTRL_TAGS )("Updating Refinement Zones: "
                         "update_rs time: %.3fms, "
                         "update_rs buffers: " SIZE_FORMAT ", "
                         "update_rs goal time: %.3fms",
                         update_rs_time,
                         update_rs_processed_buffers,
                         goal_ms);

  _green_zone = calc_new_green_zone(_green_zone,
                                    update_rs_time,
                                    update_rs_processed_buffers,
                                    goal_ms);
  _yellow_zone = calc_new_yellow_zone(_green_zone, _min_yellow_zone_size);
  _red_zone = calc_new_red_zone(_green_zone, _yellow_zone);

  assert_zone_constraints_gyr(_green_zone, _yellow_zone, _red_zone);
  LOG_ZONES("Updated Refinement Zones: "
            "green: " SIZE_FORMAT ", "
            "yellow: " SIZE_FORMAT ", "
            "red: " SIZE_FORMAT,
            _green_zone, _yellow_zone, _red_zone);
}

void G1ConcurrentRefine::adjust(double update_rs_time,
                                size_t update_rs_processed_buffers,
                                double goal_ms) {
  DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();

  if (G1UseAdaptiveConcRefinement) {
    update_zones(update_rs_time, update_rs_processed_buffers, goal_ms);

    // Change the barrier params
    if (max_num_threads() == 0) {
      // Disable dcqs notification when there are no threads to notify.
      dcqs.set_process_completed_threshold(INT_MAX);
    } else {
      // Worker 0 is the primary; wakeup is via dcqs notification.
      STATIC_ASSERT(max_yellow_zone <= INT_MAX);
      size_t activate = activation_threshold(0);
      dcqs.set_process_completed_threshold((int)activate);
    }
    dcqs.set_max_completed_queue((int)red_zone());
  }

  size_t curr_queue_size = dcqs.completed_buffers_num();
  if (curr_queue_size >= yellow_zone()) {
    dcqs.set_completed_queue_padding(curr_queue_size);
  } else {
    dcqs.set_completed_queue_padding(0);
  }
  dcqs.notify_if_necessary();
}

size_t G1ConcurrentRefine::activation_threshold(uint worker_id) const {
  Thresholds thresholds = calc_thresholds(_green_zone, _yellow_zone, worker_id);
  return activation_level(thresholds);
}

size_t G1ConcurrentRefine::deactivation_threshold(uint worker_id) const {
  Thresholds thresholds = calc_thresholds(_green_zone, _yellow_zone, worker_id);
  return deactivation_level(thresholds);
}

uint G1ConcurrentRefine::worker_id_offset() {
  return DirtyCardQueueSet::num_par_ids();
}

void G1ConcurrentRefine::maybe_activate_more_threads(uint worker_id, size_t num_cur_buffers) {
  if (num_cur_buffers > activation_threshold(worker_id + 1)) {
    _thread_control.maybe_activate_next(worker_id);
  }
}

bool G1ConcurrentRefine::do_refinement_step(uint worker_id) {
  DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();

  size_t curr_buffer_num = dcqs.completed_buffers_num();
  // If the number of the buffers falls down into the yellow zone,
  // that means that the transition period after the evacuation pause has ended.
  // Since the value written to the DCQS is the same for all threads, there is no
  // need to synchronize.
  if (dcqs.completed_queue_padding() > 0 && curr_buffer_num <= yellow_zone()) {
    dcqs.set_completed_queue_padding(0);
  }

  maybe_activate_more_threads(worker_id, curr_buffer_num);

  // Process the next buffer, if there are enough left.
  return dcqs.refine_completed_buffer_concurrently(worker_id + worker_id_offset(),
                                                   deactivation_threshold(worker_id));
}

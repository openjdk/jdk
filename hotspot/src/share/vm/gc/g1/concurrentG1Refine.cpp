/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/concurrentG1Refine.hpp"
#include "gc/g1/concurrentG1RefineThread.hpp"
#include "gc/g1/g1YoungRemSetSamplingThread.hpp"
#include "logging/log.hpp"
#include "runtime/java.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/pair.hpp"
#include <math.h>

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
  double step = yellow_size / ConcurrentG1Refine::thread_num();
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

ConcurrentG1Refine::ConcurrentG1Refine(size_t green_zone,
                                       size_t yellow_zone,
                                       size_t red_zone,
                                       size_t min_yellow_zone_size) :
  _threads(NULL),
  _sample_thread(NULL),
  _n_worker_threads(thread_num()),
  _green_zone(green_zone),
  _yellow_zone(yellow_zone),
  _red_zone(red_zone),
  _min_yellow_zone_size(min_yellow_zone_size)
{
  assert_zone_constraints_gyr(green_zone, yellow_zone, red_zone);
}

static size_t calc_min_yellow_zone_size() {
  size_t step = G1ConcRefinementThresholdStep;
  uint n_workers = ConcurrentG1Refine::thread_num();
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

ConcurrentG1Refine* ConcurrentG1Refine::create(CardTableEntryClosure* refine_closure,
                                               jint* ecode) {
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

  ConcurrentG1Refine* cg1r = new ConcurrentG1Refine(green_zone,
                                                    yellow_zone,
                                                    red_zone,
                                                    min_yellow_zone_size);

  if (cg1r == NULL) {
    *ecode = JNI_ENOMEM;
    vm_shutdown_during_initialization("Could not create ConcurrentG1Refine");
    return NULL;
  }

  cg1r->_threads = NEW_C_HEAP_ARRAY_RETURN_NULL(ConcurrentG1RefineThread*, cg1r->_n_worker_threads, mtGC);
  if (cg1r->_threads == NULL) {
    *ecode = JNI_ENOMEM;
    vm_shutdown_during_initialization("Could not allocate an array for ConcurrentG1RefineThread");
    return NULL;
  }

  uint worker_id_offset = DirtyCardQueueSet::num_par_ids();

  ConcurrentG1RefineThread *next = NULL;
  for (uint i = cg1r->_n_worker_threads - 1; i != UINT_MAX; i--) {
    Thresholds thresholds = calc_thresholds(green_zone, yellow_zone, i);
    ConcurrentG1RefineThread* t =
      new ConcurrentG1RefineThread(cg1r,
                                   next,
                                   refine_closure,
                                   worker_id_offset,
                                   i,
                                   activation_level(thresholds),
                                   deactivation_level(thresholds));
    assert(t != NULL, "Conc refine should have been created");
    if (t->osthread() == NULL) {
      *ecode = JNI_ENOMEM;
      vm_shutdown_during_initialization("Could not create ConcurrentG1RefineThread");
      return NULL;
    }

    assert(t->cg1r() == cg1r, "Conc refine thread should refer to this");
    cg1r->_threads[i] = t;
    next = t;
  }

  cg1r->_sample_thread = new G1YoungRemSetSamplingThread();
  if (cg1r->_sample_thread->osthread() == NULL) {
    *ecode = JNI_ENOMEM;
    vm_shutdown_during_initialization("Could not create G1YoungRemSetSamplingThread");
    return NULL;
  }

  *ecode = JNI_OK;
  return cg1r;
}

void ConcurrentG1Refine::stop() {
  for (uint i = 0; i < _n_worker_threads; i++) {
    _threads[i]->stop();
  }
  _sample_thread->stop();
}

void ConcurrentG1Refine::update_thread_thresholds() {
  for (uint i = 0; i < _n_worker_threads; i++) {
    Thresholds thresholds = calc_thresholds(_green_zone, _yellow_zone, i);
    _threads[i]->update_thresholds(activation_level(thresholds),
                                   deactivation_level(thresholds));
  }
}

ConcurrentG1Refine::~ConcurrentG1Refine() {
  for (uint i = 0; i < _n_worker_threads; i++) {
    delete _threads[i];
  }
  FREE_C_HEAP_ARRAY(ConcurrentG1RefineThread*, _threads);

  delete _sample_thread;
}

void ConcurrentG1Refine::threads_do(ThreadClosure *tc) {
  worker_threads_do(tc);
  tc->do_thread(_sample_thread);
}

void ConcurrentG1Refine::worker_threads_do(ThreadClosure * tc) {
  for (uint i = 0; i < _n_worker_threads; i++) {
    tc->do_thread(_threads[i]);
  }
}

uint ConcurrentG1Refine::thread_num() {
  return G1ConcRefinementThreads;
}

void ConcurrentG1Refine::print_worker_threads_on(outputStream* st) const {
  for (uint i = 0; i < _n_worker_threads; ++i) {
    _threads[i]->print_on(st);
    st->cr();
  }
  _sample_thread->print_on(st);
  st->cr();
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

void ConcurrentG1Refine::update_zones(double update_rs_time,
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

void ConcurrentG1Refine::adjust(double update_rs_time,
                                size_t update_rs_processed_buffers,
                                double goal_ms) {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();

  if (G1UseAdaptiveConcRefinement) {
    update_zones(update_rs_time, update_rs_processed_buffers, goal_ms);
    update_thread_thresholds();

    // Change the barrier params
    if (_n_worker_threads == 0) {
      // Disable dcqs notification when there are no threads to notify.
      dcqs.set_process_completed_threshold(INT_MAX);
    } else {
      // Worker 0 is the primary; wakeup is via dcqs notification.
      STATIC_ASSERT(max_yellow_zone <= INT_MAX);
      size_t activate = _threads[0]->activation_threshold();
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

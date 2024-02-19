/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.inline.hpp"
#include "gc/shared/gc_globals.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include <math.h>

G1ConcurrentRefineThread* G1ConcurrentRefineThreadControl::create_refinement_thread(uint worker_id, bool initializing) {
  G1ConcurrentRefineThread* result = nullptr;
  if (initializing || !InjectGCWorkerCreationFailure) {
    result = G1ConcurrentRefineThread::create(_cr, worker_id);
  }
  if (result == nullptr || result->osthread() == nullptr) {
    log_warning(gc)("Failed to create refinement thread %u, no more %s",
                    worker_id,
                    result == nullptr ? "memory" : "OS threads");
    if (result != nullptr) {
      delete result;
      result = nullptr;
    }
  }
  return result;
}

G1ConcurrentRefineThreadControl::G1ConcurrentRefineThreadControl(uint max_num_threads) :
  _cr(nullptr),
  _threads(max_num_threads)
{}

G1ConcurrentRefineThreadControl::~G1ConcurrentRefineThreadControl() {
  while (_threads.is_nonempty()) {
    delete _threads.pop();
  }
}

bool G1ConcurrentRefineThreadControl::ensure_threads_created(uint worker_id, bool initializing) {
  assert(worker_id < max_num_threads(), "precondition");

  while ((uint)_threads.length() <= worker_id) {
    G1ConcurrentRefineThread* rt = create_refinement_thread(_threads.length(), initializing);
    if (rt == nullptr) {
      return false;
    }
    _threads.push(rt);
  }

  return true;
}

jint G1ConcurrentRefineThreadControl::initialize(G1ConcurrentRefine* cr) {
  assert(cr != nullptr, "G1ConcurrentRefine must not be null");
  _cr = cr;

  if (max_num_threads() > 0) {
    _threads.push(create_refinement_thread(0, true));
    if (_threads.at(0) == nullptr) {
      vm_shutdown_during_initialization("Could not allocate primary refinement thread");
      return JNI_ENOMEM;
    }

    if (!UseDynamicNumberOfGCThreads) {
      if (!ensure_threads_created(max_num_threads() - 1, true)) {
        vm_shutdown_during_initialization("Could not allocate refinement threads");
        return JNI_ENOMEM;
      }
    }
  }

  return JNI_OK;
}

#ifdef ASSERT
void G1ConcurrentRefineThreadControl::assert_current_thread_is_primary_refinement_thread() const {
  assert(Thread::current() == _threads.at(0), "Not primary thread");
}
#endif // ASSERT

bool G1ConcurrentRefineThreadControl::activate(uint worker_id) {
  if (ensure_threads_created(worker_id, false)) {
    _threads.at(worker_id)->activate();
    return true;
  }

  return false;
}

void G1ConcurrentRefineThreadControl::worker_threads_do(ThreadClosure* tc) {
  for (G1ConcurrentRefineThread* t : _threads) {
    tc->do_thread(t);
  }
}

void G1ConcurrentRefineThreadControl::stop() {
  for (G1ConcurrentRefineThread* t : _threads) {
    t->stop();
  }
}

uint64_t G1ConcurrentRefine::adjust_threads_period_ms() const {
  // Instead of a fixed value, this could be a command line option.  But then
  // we might also want to allow configuration of adjust_threads_wait_ms().
  return 50;
}

static size_t minimum_pending_cards_target() {
  // One buffer per thread.
  return ParallelGCThreads * G1UpdateBufferSize;
}

G1ConcurrentRefine::G1ConcurrentRefine(G1Policy* policy) :
  _policy(policy),
  _threads_wanted(0),
  _pending_cards_target(PendingCardsTargetUninitialized),
  _last_adjust(),
  _needs_adjust(false),
  _threads_needed(policy, adjust_threads_period_ms()),
  _thread_control(G1ConcRefinementThreads),
  _dcqs(G1BarrierSet::dirty_card_queue_set())
{}

jint G1ConcurrentRefine::initialize() {
  return _thread_control.initialize(this);
}

G1ConcurrentRefine* G1ConcurrentRefine::create(G1Policy* policy, jint* ecode) {
  G1ConcurrentRefine* cr = new G1ConcurrentRefine(policy);
  *ecode = cr->initialize();
  if (*ecode != 0) {
    delete cr;
    cr = nullptr;
  }
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

void G1ConcurrentRefine::update_pending_cards_target(double logged_cards_time_ms,
                                                     size_t processed_logged_cards,
                                                     size_t predicted_thread_buffer_cards,
                                                     double goal_ms) {
  size_t minimum = minimum_pending_cards_target();
  if ((processed_logged_cards < minimum) || (logged_cards_time_ms == 0.0)) {
    log_debug(gc, ergo, refine)("Unchanged pending cards target: %zu",
                                _pending_cards_target);
    return;
  }

  // Base the pending cards budget on the measured rate.
  double rate = processed_logged_cards / logged_cards_time_ms;
  size_t budget = static_cast<size_t>(goal_ms * rate);
  // Deduct predicted cards in thread buffers to get target.
  size_t new_target = budget - MIN2(budget, predicted_thread_buffer_cards);
  // Add some hysteresis with previous values.
  if (is_pending_cards_target_initialized()) {
    new_target = (new_target + _pending_cards_target) / 2;
  }
  // Apply minimum target.
  new_target = MAX2(new_target, minimum_pending_cards_target());
  _pending_cards_target = new_target;
  log_debug(gc, ergo, refine)("New pending cards target: %zu", new_target);
}

void G1ConcurrentRefine::adjust_after_gc(double logged_cards_time_ms,
                                         size_t processed_logged_cards,
                                         size_t predicted_thread_buffer_cards,
                                         double goal_ms) {
  if (!G1UseConcRefinement) return;

  update_pending_cards_target(logged_cards_time_ms,
                              processed_logged_cards,
                              predicted_thread_buffer_cards,
                              goal_ms);
  if (_thread_control.max_num_threads() == 0) {
    // If no refinement threads then the mutator threshold is the target.
    _dcqs.set_mutator_refinement_threshold(_pending_cards_target);
  } else {
    // Provisionally make the mutator threshold unlimited, to be updated by
    // the next periodic adjustment.  Because card state may have changed
    // drastically, record that adjustment is needed and kick the primary
    // thread, in case it is waiting.
    _dcqs.set_mutator_refinement_threshold(SIZE_MAX);
    _needs_adjust = true;
    if (is_pending_cards_target_initialized()) {
      _thread_control.activate(0);
    }
  }
}

// Wake up the primary thread less frequently when the time available until
// the next GC is longer.  But don't increase the wait time too rapidly.
// This reduces the number of primary thread wakeups that just immediately
// go back to waiting, while still being responsive to behavior changes.
static uint64_t compute_adjust_wait_time_ms(double available_ms) {
  return static_cast<uint64_t>(sqrt(available_ms) * 4.0);
}

uint64_t G1ConcurrentRefine::adjust_threads_wait_ms() const {
  assert_current_thread_is_primary_refinement_thread();
  if (is_pending_cards_target_initialized()) {
    double available_ms = _threads_needed.predicted_time_until_next_gc_ms();
    uint64_t wait_time_ms = compute_adjust_wait_time_ms(available_ms);
    return MAX2(wait_time_ms, adjust_threads_period_ms());
  } else {
    // If target not yet initialized then wait forever (until explicitly
    // activated).  This happens during startup, when we don't bother with
    // refinement.
    return 0;
  }
}

class G1ConcurrentRefine::RemSetSamplingClosure : public HeapRegionClosure {
  G1CollectionSet* _cset;
  size_t _sampled_card_rs_length;
  size_t _sampled_code_root_rs_length;

public:
  explicit RemSetSamplingClosure(G1CollectionSet* cset) :
    _cset(cset), _sampled_card_rs_length(0), _sampled_code_root_rs_length(0) {}

  bool do_heap_region(HeapRegion* r) override {
    HeapRegionRemSet* rem_set = r->rem_set();
    _sampled_card_rs_length += rem_set->occupied();
    _sampled_code_root_rs_length += rem_set->code_roots_list_length();
    return false;
  }

  size_t sampled_card_rs_length() const { return _sampled_card_rs_length; }
  size_t sampled_code_root_rs_length() const { return _sampled_code_root_rs_length; }
};

// Adjust the target length (in regions) of the young gen, based on the the
// current length of the remembered sets.
//
// At the end of the GC G1 determines the length of the young gen based on
// how much time the next GC can take, and when the next GC may occur
// according to the MMU.
//
// The assumption is that a significant part of the GC is spent on scanning
// the remembered sets (and many other components), so this thread constantly
// reevaluates the prediction for the remembered set scanning costs, and potentially
// resizes the young gen. This may do a premature GC or even increase the young
// gen size to keep pause time length goal.
void G1ConcurrentRefine::adjust_young_list_target_length() {
  if (_policy->use_adaptive_young_list_length()) {
    G1CollectionSet* cset = G1CollectedHeap::heap()->collection_set();
    RemSetSamplingClosure cl{cset};
    cset->iterate(&cl);
    _policy->revise_young_list_target_length(cl.sampled_card_rs_length(), cl.sampled_code_root_rs_length());
  }
}

bool G1ConcurrentRefine::adjust_threads_periodically() {
  assert_current_thread_is_primary_refinement_thread();

  // Check whether it's time to do a periodic adjustment.
  if (!_needs_adjust) {
    Tickspan since_adjust = Ticks::now() - _last_adjust;
    if (since_adjust.milliseconds() >= adjust_threads_period_ms()) {
      _needs_adjust = true;
    }
  }

  // If needed, try to adjust threads wanted.
  if (_needs_adjust) {
    // Getting used young bytes requires holding Heap_lock.  But we can't use
    // normal lock and block until available.  Blocking on the lock could
    // deadlock with a GC VMOp that is holding the lock and requesting a
    // safepoint.  Instead try to lock, and if fail then skip adjustment for
    // this iteration of the thread, do some refinement work, and retry the
    // adjustment later.
    if (Heap_lock->try_lock()) {
      size_t used_bytes = _policy->estimate_used_young_bytes_locked();
      Heap_lock->unlock();
      adjust_young_list_target_length();
      size_t young_bytes = _policy->young_list_target_length() * HeapRegion::GrainBytes;
      size_t available_bytes = young_bytes - MIN2(young_bytes, used_bytes);
      adjust_threads_wanted(available_bytes);
      _needs_adjust = false;
      _last_adjust = Ticks::now();
      return true;
    }
  }

  return false;
}

bool G1ConcurrentRefine::is_in_last_adjustment_period() const {
  return _threads_needed.predicted_time_until_next_gc_ms() <= adjust_threads_period_ms();
}

void G1ConcurrentRefine::adjust_threads_wanted(size_t available_bytes) {
  assert_current_thread_is_primary_refinement_thread();
  size_t num_cards = _dcqs.num_cards();
  size_t mutator_threshold = SIZE_MAX;
  uint old_wanted = Atomic::load(&_threads_wanted);

  _threads_needed.update(old_wanted,
                         available_bytes,
                         num_cards,
                         _pending_cards_target);
  uint new_wanted = _threads_needed.threads_needed();
  if (new_wanted > _thread_control.max_num_threads()) {
    // If running all the threads can't reach goal, turn on refinement by
    // mutator threads.  Using target as the threshold may be stronger
    // than required, but will do the most to get us under goal, and we'll
    // reevaluate with the next adjustment.
    mutator_threshold = _pending_cards_target;
    new_wanted = _thread_control.max_num_threads();
  } else if (is_in_last_adjustment_period()) {
    // If very little time remains until GC, enable mutator refinement.  If
    // the target has been reached, this keeps the number of pending cards on
    // target even if refinement threads deactivate in the meantime.  And if
    // the target hasn't been reached, this prevents things from getting
    // worse.
    mutator_threshold = _pending_cards_target;
  }
  Atomic::store(&_threads_wanted, new_wanted);
  _dcqs.set_mutator_refinement_threshold(mutator_threshold);
  log_debug(gc, refine)("Concurrent refinement: wanted %u, cards: %zu, "
                        "predicted: %zu, time: %1.2fms",
                        new_wanted,
                        num_cards,
                        _threads_needed.predicted_cards_at_next_gc(),
                        _threads_needed.predicted_time_until_next_gc_ms());
  // Activate newly wanted threads.  The current thread is the primary
  // refinement thread, so is already active.
  for (uint i = MAX2(old_wanted, 1u); i < new_wanted; ++i) {
    if (!_thread_control.activate(i)) {
      // Failed to allocate and activate thread.  Stop trying to activate, and
      // instead use mutator threads to make up the gap.
      Atomic::store(&_threads_wanted, i);
      _dcqs.set_mutator_refinement_threshold(_pending_cards_target);
      break;
    }
  }
}

void G1ConcurrentRefine::reduce_threads_wanted() {
  assert_current_thread_is_primary_refinement_thread();
  if (!_needs_adjust) {         // Defer if adjustment request is active.
    uint wanted = Atomic::load(&_threads_wanted);
    if (wanted > 0) {
      Atomic::store(&_threads_wanted, --wanted);
    }
    // If very little time remains until GC, enable mutator refinement.  If
    // the target has been reached, this keeps the number of pending cards on
    // target even as refinement threads deactivate in the meantime.
    if (is_in_last_adjustment_period()) {
      _dcqs.set_mutator_refinement_threshold(_pending_cards_target);
    }
  }
}

bool G1ConcurrentRefine::is_thread_wanted(uint worker_id) const {
  return worker_id < Atomic::load(&_threads_wanted);
}

bool G1ConcurrentRefine::is_thread_adjustment_needed() const {
  assert_current_thread_is_primary_refinement_thread();
  return _needs_adjust;
}

void G1ConcurrentRefine::record_thread_adjustment_needed() {
  assert_current_thread_is_primary_refinement_thread();
  _needs_adjust = true;
}

G1ConcurrentRefineStats G1ConcurrentRefine::get_and_reset_refinement_stats() {
  struct CollectStats : public ThreadClosure {
    G1ConcurrentRefineStats _total_stats;
    virtual void do_thread(Thread* t) {
      G1ConcurrentRefineThread* crt = static_cast<G1ConcurrentRefineThread*>(t);
      G1ConcurrentRefineStats& stats = *crt->refinement_stats();
      _total_stats += stats;
      stats.reset();
    }
  } collector;
  threads_do(&collector);
  return collector._total_stats;
}

uint G1ConcurrentRefine::worker_id_offset() {
  return G1DirtyCardQueueSet::num_par_ids();
}

bool G1ConcurrentRefine::try_refinement_step(uint worker_id,
                                             size_t stop_at,
                                             G1ConcurrentRefineStats* stats) {
  uint adjusted_id = worker_id + worker_id_offset();
  return _dcqs.refine_completed_buffer_concurrently(adjusted_id, stop_at, stats);
}

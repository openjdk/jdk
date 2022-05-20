/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1ConcurrentRefineStats.hpp"
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/init.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"

G1ConcurrentRefineThread::G1ConcurrentRefineThread(G1ConcurrentRefine* cr, uint worker_id) :
  ConcurrentGCThread(),
  _vtime_start(0.0),
  _vtime_accum(0.0),
  _refinement_stats(new G1ConcurrentRefineStats()),
  _worker_id(worker_id),
  _cr(cr)
{
  // set name
  set_name("G1 Refine#%d", worker_id);
}

G1ConcurrentRefineThread::~G1ConcurrentRefineThread() {
  delete _refinement_stats;
}

void G1ConcurrentRefineThread::run_service() {
  _vtime_start = os::elapsedVTime();

  while (wait_for_completed_buffers()) {
    // For logging.
    G1ConcurrentRefineStats start_stats = *_refinement_stats;
    G1ConcurrentRefineStats total_stats; // Accumulate over activation.

    {
      SuspendibleThreadSetJoiner sts_join;

      log_debug(gc, refine)("Activated worker %d, on threshold: %zu, current: %zu",
                            _worker_id, _cr->activation_threshold(_worker_id),
                            G1BarrierSet::dirty_card_queue_set().num_cards());

      while (!should_terminate()) {
        if (sts_join.should_yield()) {
          // Accumulate changed stats before possible GC that resets stats.
          total_stats += *_refinement_stats - start_stats;
          sts_join.yield();
          // Reinitialize baseline stats after safepoint.
          start_stats = *_refinement_stats;
          continue;             // Re-check for termination after yield delay.
        }

        if (!_cr->do_refinement_step(_worker_id, _refinement_stats)) {
          if (maybe_deactivate()) {
            break;
          }
        }
      }
    }

    total_stats += *_refinement_stats - start_stats;
    log_debug(gc, refine)("Deactivated worker %d, off threshold: %zu, "
                          "cards: %zu, refined %zu, rate %1.2fc/ms",
                          _worker_id, _cr->deactivation_threshold(_worker_id),
                          G1BarrierSet::dirty_card_queue_set().num_cards(),
                          total_stats.refined_cards(),
                          total_stats.refinement_rate_ms());

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - _vtime_start);
    } else {
      _vtime_accum = 0.0;
    }
  }

  log_debug(gc, refine)("Stopping %d", _worker_id);
}

void G1ConcurrentRefineThread::stop_service() {
  activate();
}

G1PrimaryConcurrentRefineThread*
G1PrimaryConcurrentRefineThread::create(G1ConcurrentRefine* cr) {
  G1PrimaryConcurrentRefineThread* crt =
    new (std::nothrow) G1PrimaryConcurrentRefineThread(cr);
  if (crt != nullptr) {
    crt->create_and_start();
  }
  return crt;
}

G1PrimaryConcurrentRefineThread::G1PrimaryConcurrentRefineThread(G1ConcurrentRefine* cr) :
  G1ConcurrentRefineThread(cr, 0),
  _notifier(0),
  _threshold(0)
{}

void G1PrimaryConcurrentRefineThread::stop_service() {
  G1DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
  dcqs.set_refinement_notification_thread(nullptr);
  G1ConcurrentRefineThread::stop_service();
}

// The primary refinement thread is notified when buffers/cards are added to
// the dirty card queue.  That can happen in fairly arbitrary contexts.
// This means there may be arbitrary other locks held when notifying.  We
// also don't want to have to take a lock on the fairly common notification
// path, as contention for that lock can significantly impact performance.
//
// We use a semaphore to implement waiting and unblocking, to avoid
// lock rank checking issues.  (We could alternatively use an
// arbitrarily low ranked mutex.)  The atomic variable _threshold is
// used to decide when to signal the semaphore.  When its value is
// SIZE_MAX then the thread is running.  Otherwise, the thread should
// be requested to run when notified that the number of cards has
// exceeded the threshold value.

bool G1PrimaryConcurrentRefineThread::wait_for_completed_buffers() {
  assert(this == Thread::current(), "precondition");
  _notifier.wait();
  assert(Atomic::load(&_threshold) == SIZE_MAX || should_terminate(), "incorrect state");
  return !should_terminate();
}

bool G1PrimaryConcurrentRefineThread::maybe_deactivate() {
  assert(this == Thread::current(), "precondition");
  assert(Atomic::load(&_threshold) == SIZE_MAX, "incorrect state");
  Atomic::store(&_threshold, cr()->primary_activation_threshold());
  // Always deactivate when no refinement work found.  New refinement
  // work may have arrived after we tried, but checking for that would
  // still be racy.  Instead, the next time additional work is made
  // available we'll get reactivated.
  return true;
}

void G1PrimaryConcurrentRefineThread::activate() {
  assert(this != Thread::current(), "precondition");
  // The thread is running when notifications are disabled, so shouldn't
  // signal is this case.  But there's a race between stop requests and
  // maybe_deactivate, so also signal if stop requested.
  size_t threshold = Atomic::load(&_threshold);
  if (((threshold != SIZE_MAX) &&
       (threshold == Atomic::cmpxchg(&_threshold, threshold, SIZE_MAX))) ||
      should_terminate()) {
    _notifier.signal();
  }
}

void G1PrimaryConcurrentRefineThread::notify(size_t num_cards) {
  // Only activate if the number of pending cards exceeds the activation
  // threshold.  Notification is disabled when the thread is running, by
  // setting _threshold to SIZE_MAX.  A relaxed load is sufficient; we don't
  // need to be precise about this.
  if (num_cards > Atomic::load(&_threshold)) {
    // Discard notifications occurring during a safepoint.  A GC safepoint
    // may dirty some cards (such as during reference processing), possibly
    // leading to notification.  End-of-GC update_notify_threshold activates
    // the primary thread if needed.  Non-GC safepoints are expected to
    // rarely (if ever) dirty cards, so defer activation to a post-safepoint
    // notification.
    if (!SafepointSynchronize::is_at_safepoint()) {
      activate();
    }
  }
}

void G1PrimaryConcurrentRefineThread::update_notify_threshold(size_t threshold) {
#ifdef ASSERT
  if (is_init_completed()) {
    assert_at_safepoint();
    assert(Thread::current()->is_VM_thread(), "precondition");
  }
#endif // ASSERT
  // If _threshold is SIZE_MAX then the thread is active and the value
  // of _threshold shouldn't be changed.
  if (Atomic::load(&_threshold) != SIZE_MAX) {
    Atomic::store(&_threshold, threshold);
    if (G1BarrierSet::dirty_card_queue_set().num_cards() > threshold) {
      activate();
    }
  }
}

class G1SecondaryConcurrentRefineThread final : public G1ConcurrentRefineThread {
  Monitor _notifier;
  bool _requested_active;

  bool wait_for_completed_buffers() override;
  bool maybe_deactivate() override;

public:
  G1SecondaryConcurrentRefineThread(G1ConcurrentRefine* cr, uint worker_id);

  void activate() override;
};

G1SecondaryConcurrentRefineThread::G1SecondaryConcurrentRefineThread(G1ConcurrentRefine* cr,
                                                                     uint worker_id) :
  G1ConcurrentRefineThread(cr, worker_id),
  _notifier(Mutex::nosafepoint, this->name(), true),
  _requested_active(false)
{
  assert(worker_id > 0, "precondition");
}

bool G1SecondaryConcurrentRefineThread::wait_for_completed_buffers() {
  assert(this == Thread::current(), "precondition");
  MonitorLocker ml(&_notifier, Mutex::_no_safepoint_check_flag);
  while (!_requested_active && !should_terminate()) {
    ml.wait();
  }
  return !should_terminate();
}

void G1SecondaryConcurrentRefineThread::activate() {
  assert(this != Thread::current(), "precondition");
  MonitorLocker ml(&_notifier, Mutex::_no_safepoint_check_flag);
  if (!_requested_active || should_terminate()) {
    _requested_active = true;
    ml.notify();
  }
}

bool G1SecondaryConcurrentRefineThread::maybe_deactivate() {
  assert(this == Thread::current(), "precondition");
  MutexLocker ml(&_notifier, Mutex::_no_safepoint_check_flag);
  bool requested = _requested_active;
  _requested_active = false;
  return !requested;            // Deactivate if not recently requested active.
}

G1ConcurrentRefineThread*
G1ConcurrentRefineThread::create(G1ConcurrentRefine* cr, uint worker_id) {
  assert(worker_id > 0, "precondition");
  G1ConcurrentRefineThread* crt =
    new (std::nothrow) G1SecondaryConcurrentRefineThread(cr, worker_id);
  if (crt != nullptr) {
    crt->create_and_start();
  }
  return crt;
}

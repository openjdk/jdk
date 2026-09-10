/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahUncommitThread.hpp"
#include "logging/log.hpp"
#include "memory/allocation.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/events.hpp"
#include "utilities/quickSort.hpp"

ShenandoahUncommitThread::ShenandoahUncommitThread(ShenandoahHeap* heap)
  : _heap(heap),
    _candidates(NEW_C_HEAP_ARRAY(Candidate, _heap->num_regions(), mtGC)),
    _candidates_count(0),
    _uncommit_lock(Mutex::safepoint - 2, "ShenandoahUncommit_lock", true) {
  set_name("ShenUncommit");
  create_and_start();

  // Allow uncommits. This is managed by the control thread during a GC.
  _uncommit_allowed.set();
}

void ShenandoahUncommitThread::run_service() {
  assert(ShenandoahUncommit, "Thread should only run when uncommit is enabled");

  // poll_interval avoids constantly polling regions for shrinking.
  // Having an interval 10x lower than the delay would mean we hit the
  // shrinking with lag of less than 1/10-th of true delay. Poll interval
  // cannot be allowed to decay to zero, which would cause indefinite wait.
  const int64_t poll_interval = MAX2<int64_t>(1, int64_t(ShenandoahUncommitDelay) / 10);

  // ShenandoahUncommitDelay is in millis, but shrink_delay is in seconds.
  const double normal_shrink_delay = double(ShenandoahUncommitDelay) / 1000;

  while (true) {
    {
      MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
      if (_terminating.is_set()) {
        // Terminating already, exit.
        break;
      }

      locker.wait(poll_interval);

      if (_terminating.is_set()) {
        // Wake up for terminating, exit.
        break;
      }

      if (_uncommit_allowed.is_unset()) {
        // Wake up for disallowing uncommits, go back to sleep.
        continue;
      }
    }

    bool soft_max_changed = _soft_max_changed.try_unset();
    bool explicit_gc_requested = _explicit_gc_requested.try_unset();

    // Explicit GC tries to uncommit everything down to min capacity.
    // Soft max change tries to uncommit everything down to target capacity.
    // Periodic uncommit tries to uncommit suitable regions down to min capacity.
    size_t shrink_until = soft_max_changed ? _heap->soft_max_capacity() : _heap->min_capacity();
    double shrink_delay = (soft_max_changed || explicit_gc_requested) ? 0 : normal_shrink_delay;

    if (plan_work(shrink_delay, shrink_until)) {
      uncommit(shrink_delay, shrink_until);
    }
  }
}

// The regions that were freed in the same cycle would have roughly the same empty time.
// Empty time is coarsened to ~100ms window. Within that window, uncommit from higher
// indexes, to allow allocation path to take earlier regions first. The windows themselves
// have higher priority the earlier the empty time was.
int ShenandoahUncommitThread::compare_uncommit_priority(Candidate& a, Candidate& b) {
  if (a._empty_time > b._empty_time) {
    return +1;
  }
  if (a._empty_time < b._empty_time) {
    return -1;
  }
  if (a._region->index() < b._region->index()) {
    return +1;
  }
  if (a._region->index() > b._region->index()) {
    return -1;
  }
  return 0;
}

bool ShenandoahUncommitThread::plan_work(double shrink_delay, size_t shrink_until) {
  _candidates_count = 0;

  if (_heap->committed() <= shrink_until) {
    // Do not uncommit below target.
    return false;
  }

  // Determine if there is work to do. This avoids locking the heap if there is
  // no work available, avoids spamming logs with superfluous logging messages,
  // and minimises the amount of work while locks are held. Fill out all candidates:
  // even if they are currently not targeted, by the time we get to uncommit them,
  // they might become eligible too.
  double shrink_before = os::elapsedTime() - shrink_delay;
  bool has_work = false;
  for (size_t i = 0; i < _heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = _heap->get_region(i);
    if (r->is_empty_committed()) {
      has_work |= (r->empty_time() < shrink_before);
      Candidate& candidate = _candidates[_candidates_count++];
      candidate._region = r;
      candidate._empty_time = (int64_t)(r->empty_time() * 10);
    }
  }

  if (has_work) {
    QuickSort::sort(_candidates, _candidates_count, compare_uncommit_priority);
    return true;
  } else {
    // No regions that match our target at all.
    return false;
  }
}

void ShenandoahUncommitThread::notify_soft_max_changed() {
  assert(is_uncommit_allowed(), "Only notify if uncommit is allowed");
  if (_soft_max_changed.try_set()) {
    MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
    locker.notify_all();
  }
}

void ShenandoahUncommitThread::notify_explicit_gc_requested() {
  assert(is_uncommit_allowed(), "Only notify if uncommit is allowed");
  if (_explicit_gc_requested.try_set()) {
    MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
    locker.notify_all();
  }
}

bool ShenandoahUncommitThread::is_uncommit_allowed() const {
  return _uncommit_allowed.is_set();
}

void ShenandoahUncommitThread::uncommit(double shrink_delay, size_t shrink_until) {
  assert(ShenandoahUncommit, "Should be enabled");
  assert(_uncommit_in_progress.is_unset(), "Uncommit should not be in progress");
  assert(_candidates_count > 0, "Should be");

  const char* msg = "Concurrent uncommit";
  EventMark em("%s", msg);
  log_info(gc, start)("%s", msg);

  double start = os::elapsedTime();
  double ms_time_budget = ShenandoahUncommitGrace;
  double ms_per_candidate = ms_time_budget / _candidates_count;

  size_t uncommitted_count = 0;
  for (size_t i = 0; i < _candidates_count; i++) {
    ShenandoahHeapRegion* r = _candidates[i]._region;

    if (_heap->committed() < shrink_until + ShenandoahHeapRegion::region_size_bytes()) {
      // Do not uncommit below the target.
      break;
    }

    double cur_time = os::elapsedTime();

    // Try to claim progress, gracefully waiting. This allows allocators to proceed
    // taking the heap lock and start using the region. We are not in a hurry to uncommit,
    // otherwise, we will just trip through uncommit-commit wastefully.
    double expected_ts = i * ms_per_candidate;
    double actual_ts = (cur_time - start) * MILLIUNITS;
    int delay_ms = MAX2<int>(0, expected_ts - actual_ts);
    if (!try_set_progress(delay_ms)) {
      // Termination asserted.
      break;
    }

    // Go for uncommit!
    double shrink_before = cur_time - shrink_delay;
    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
      SuspendibleThreadSetJoiner sts_joiner;
      ShenandoahHeapLocker heap_locker(_heap->lock());
      if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
        log_trace(gc)("Uncommitting region %zu, empty_time=%.2f", r->index(), r->empty_time());
        r->make_uncommitted();
        uncommitted_count++;
      }
    }

    // All done, turn the flag down.
    unset_progress();
  }

  double elapsed = os::elapsedTime() - start;

  if (uncommitted_count > 0) {
    _heap->notify_heap_changed();
  }

  log_info(gc)("%s " PROPERFMT " (%zuM) %.3fms",
               msg,
               PROPERFMTARGS(uncommitted_count * ShenandoahHeapRegion::region_size_bytes()),
               _heap->capacity() / M,
               elapsed * MILLIUNITS);

  assert(_uncommit_in_progress.is_unset(), "Uncommit should not be in progress");
}

bool ShenandoahUncommitThread::try_set_progress(int delay_ms) {
  MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
  assert(_uncommit_in_progress.is_unset(), "Should be unset before checks");

  // Optimistic: uncommits are allowed, just wait a bit, if requested.
  while (_uncommit_allowed.is_set() && (delay_ms > 0)) {
    double started = os::elapsedTime();
    locker.wait(delay_ms);
    delay_ms -= (os::elapsedTime() - started) * MILLIUNITS;
  }

  // Pessimistic: uncommits are disallowed. Wait until allowed again or terminated.
  while (_uncommit_allowed.is_unset() && _terminating.is_unset()) {
    locker.wait();
  }

  if (_terminating.is_set()) {
    assert(_uncommit_in_progress.is_unset(), "Should remain unset");
    return false;
  }

  // We are good to enable uncommits.
  _uncommit_in_progress.set();
  return true;
}

void ShenandoahUncommitThread::unset_progress() {
  MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
  _uncommit_in_progress.unset();
  locker.notify_all();
}

void ShenandoahUncommitThread::stop_service() {
  MonitorLocker locker(&_uncommit_lock, Mutex::_safepoint_check_flag);
  _uncommit_allowed.unset();
  _terminating.set();
  locker.notify_all();
}

void ShenandoahUncommitThread::forbid_uncommit() {
  MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
  _uncommit_allowed.unset();
  locker.notify_all();
  while (_uncommit_in_progress.is_set()) {
    locker.wait();
  }
}

void ShenandoahUncommitThread::allow_uncommit() {
  MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
  _uncommit_allowed.set();
  locker.notify_all();
}

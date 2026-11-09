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

ShenandoahUncommitThread::ShenandoahUncommitThread(ShenandoahHeap* heap)
  : _heap(heap),
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
  const int64_t poll_interval = MAX2<int64_t>(10, int64_t(ShenandoahUncommitDelay) / 10);

  // ShenandoahUncommitDelay is in millis, but shrink_delay is in seconds.
  const double normal_shrink_delay = double(ShenandoahUncommitDelay) / 1000;

  while (true) {
    bool soft_max_changed;
    bool explicit_gc_requested;

    {
      MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
      if (should_terminate()) {
        // Already requested termination, exit.
        break;
      }

      locker.wait(poll_interval);

      if (should_terminate()) {
        // Wake up for terminating, exit.
        break;
      }

      if (_uncommit_allowed.is_unset()) {
        // Futile wakeup, go back to sleep.
        continue;
      }

      soft_max_changed = _soft_max_changed.try_unset();
      explicit_gc_requested = _explicit_gc_requested.try_unset();

      _restart.unset();
    }

    // Explicit GC tries to uncommit everything down to min capacity.
    // Soft max change tries to uncommit everything down to target capacity.
    // Periodic uncommit tries to uncommit suitable regions down to min capacity.
    size_t shrink_until = soft_max_changed ? _heap->soft_max_capacity() : _heap->min_capacity();
    double shrink_delay = soft_max_changed || explicit_gc_requested ? 0 : normal_shrink_delay;

    if (has_work(shrink_delay, shrink_until)) {
      uncommit(shrink_delay, shrink_until);
    }
  }
}

bool ShenandoahUncommitThread::has_work(double shrink_delay, size_t shrink_until) {
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
  for (size_t i = 0; i < _heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = _heap->get_region(i);
    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
      return true;
    }
  }

  return false;
}

void ShenandoahUncommitThread::uncommit(double shrink_delay, size_t shrink_until) {
  assert(ShenandoahUncommit, "Should be enabled");
  assert(_uncommit_in_progress.is_unset(), "Uncommit should not be in progress");

  int delay_ms = 10;

  double start = os::elapsedTime();

  // Uncommit from the end, allowing the allocation path that takes the regions from
  // the beginning flip the regions back to use. We are not in a hurry to uncommit,
  // otherwise we will just trip through uncommit-commit wastefully.
  size_t uncommitted_count = 0;
  for (size_t i = _heap->num_regions(); i > 0; i--) {
    if (_heap->committed() < shrink_until + ShenandoahHeapRegion::region_size_bytes()) {
      // Do not uncommit below the target.
      break;
    }

    ShenandoahHeapRegion* r = _heap->get_region(i - 1);

    double shrink_before = os::elapsedTime() - shrink_delay;
    if (!r->is_empty_committed() || (r->empty_time() >= shrink_before)) {
      // Not a candidate, try again.
      continue;
    }

    // Try to claim progress, gracefully waiting.
    if (!try_set_progress(delay_ms)) {
      // Requested to restart.
      break;
    }

    // Go for uncommit!
    {
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

    log_info(gc)("Concurrent uncommit " PROPERFMT " (%zuM) %.3fms",
                 PROPERFMTARGS(uncommitted_count * ShenandoahHeapRegion::region_size_bytes()),
                 _heap->capacity() / M,
                 elapsed * MILLIUNITS);
  }

  assert(_uncommit_in_progress.is_unset(), "Uncommit should not be in progress");
}

bool ShenandoahUncommitThread::try_set_progress(int delay_ms) {
  MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
  assert(_uncommit_in_progress.is_unset(), "Should be unset before checks");

  // Optimistic: uncommits are allowed, just wait a bit, if requested.
  double deadline = os::elapsedTime() + delay_ms * 1.0 / MILLIUNITS;
  while (_uncommit_allowed.is_set() && _restart.is_unset() &&
         os::elapsedTime() < deadline) {
    locker.wait(delay_ms);
  }

  // Pessimistic: uncommits are disallowed. Wait until allowed again.
  while (_uncommit_allowed.is_unset() && _restart.is_unset()) {
    locker.wait();
  }

  if (_restart.is_set()) {
    // Exit if restart is requested.
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
  _restart.set();
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

void ShenandoahUncommitThread::notify_soft_max_changed() {
  MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
  if (_soft_max_changed.try_set()) {
    _restart.set();
    locker.notify_all();
  }
}

void ShenandoahUncommitThread::notify_explicit_gc_requested() {
  MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
  if (_explicit_gc_requested.try_set()) {
    _restart.set();
    locker.notify_all();
  }
}

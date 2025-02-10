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
#include "runtime/mutexLocker.hpp"
#include "utilities/events.hpp"

ShenandoahUncommitThread::ShenandoahUncommitThread(ShenandoahHeap* heap)
  : _heap(heap),
    _stop_lock(Mutex::safepoint - 2, "ShenandoahUncommitStop_lock", true),
    _uncommit_lock(Mutex::safepoint - 2, "ShenandoahUncommitCancel_lock", true) {
  set_name("Shenandoah Uncommit Thread");
  create_and_start();

  // Allow uncommits. This is managed by the control thread during a GC.
  _uncommit_allowed.set();
}

void ShenandoahUncommitThread::run_service() {
  assert(ShenandoahUncommit, "Thread should only run when uncommit is enabled");

  // poll_interval avoids constantly polling regions for shrinking.
  // Having an interval 10x lower than the delay would mean we hit the
  // shrinking with lag of less than 1/10-th of true delay.
  // ShenandoahUncommitDelay is in millis, but shrink_period is in seconds.
  const int64_t poll_interval = int64_t(ShenandoahUncommitDelay) / 10;
  const double shrink_period = double(ShenandoahUncommitDelay) / 1000;
  bool timed_out = false;
  while (!should_terminate()) {
    bool soft_max_changed = _soft_max_changed.try_unset();
    bool explicit_gc_requested = _explicit_gc_requested.try_unset();

    if (soft_max_changed || explicit_gc_requested || timed_out) {
      double current = os::elapsedTime();
      size_t shrink_until = soft_max_changed ? _heap->soft_max_capacity() : _heap->min_capacity();
      double shrink_before = (soft_max_changed || explicit_gc_requested) ?
              current :
              current - shrink_period;

      // Explicit GC tries to uncommit everything down to min capacity.
      // Soft max change tries to uncommit everything down to target capacity.
      // Periodic uncommit tries to uncommit suitable regions down to min capacity.
      if (should_uncommit(shrink_before, shrink_until)) {
        uncommit(shrink_before, shrink_until);
      }
    }
    {
      MonitorLocker locker(&_stop_lock, Mutex::_no_safepoint_check_flag);
      if (!_stop_requested.is_set()) {
        timed_out = locker.wait(poll_interval);
      }
    }
  }
}

bool ShenandoahUncommitThread::should_uncommit(double shrink_before, size_t shrink_until) const {
  // Only start uncommit if the GC is idle, is not trying to run and there is work to do.
  return _heap->is_idle() && is_uncommit_allowed() && has_work(shrink_before, shrink_until);
}

bool ShenandoahUncommitThread::has_work(double shrink_before, size_t shrink_until) const {
  // Determine if there is work to do. This avoids locking the heap if there is
  // no work available, avoids spamming logs with superfluous logging messages,
  // and minimises the amount of work while locks are held.

  if (_heap->committed() <= shrink_until) {
    return false;
  }

  for (size_t i = 0; i < _heap->num_regions(); i++) {
    ShenandoahHeapRegion *r = _heap->get_region(i);
    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
      return true;
    }
  }

  return false;
}

void ShenandoahUncommitThread::notify_soft_max_changed() {
  assert(is_uncommit_allowed(), "Only notify if uncommit is allowed");
  if (_soft_max_changed.try_set()) {
    MonitorLocker locker(&_stop_lock, Mutex::_no_safepoint_check_flag);
    locker.notify_all();
  }
}

void ShenandoahUncommitThread::notify_explicit_gc_requested() {
  assert(is_uncommit_allowed(), "Only notify if uncommit is allowed");
  if (_explicit_gc_requested.try_set()) {
    MonitorLocker locker(&_stop_lock, Mutex::_no_safepoint_check_flag);
    locker.notify_all();
  }
}

bool ShenandoahUncommitThread::is_uncommit_allowed() const {
  return _uncommit_allowed.is_set();
}

void ShenandoahUncommitThread::uncommit(double shrink_before, size_t shrink_until) {
  assert(ShenandoahUncommit, "should be enabled");
  assert(_uncommit_in_progress.is_unset(), "Uncommit should not be in progress");

  if (!is_uncommit_allowed()) {
    return;
  }

  const char* msg = "Concurrent uncommit";
  EventMark em("%s", msg);
  double start = os::elapsedTime();
  log_info(gc, start)("%s", msg);

  _uncommit_in_progress.set();

  // Application allocates from the beginning of the heap, and GC allocates at
  // the end of it. It is more efficient to uncommit from the end, so that applications
  // could enjoy the near committed regions. GC allocations are much less frequent,
  // and therefore can accept the committing costs.
  size_t count = 0;
  for (size_t i = _heap->num_regions(); i > 0; i--) {
    if (!is_uncommit_allowed()) {
      break;
    }

    ShenandoahHeapRegion* r = _heap->get_region(i - 1);
    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
      SuspendibleThreadSetJoiner sts_joiner;
      ShenandoahHeapLocker locker(_heap->lock());
      if (r->is_empty_committed()) {
        if (_heap->committed() < shrink_until + ShenandoahHeapRegion::region_size_bytes()) {
          break;
        }

        r->make_uncommitted();
        count++;
      }
    }
    SpinPause(); // allow allocators to take the lock
  }

  {
    MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
    _uncommit_in_progress.unset();
    locker.notify_all();
  }

  if (count > 0) {
    _heap->notify_heap_changed();
  }

  double elapsed = os::elapsedTime() - start;
  log_info(gc)("%s " PROPERFMT " (" PROPERFMT ") %.3fms",
               msg, PROPERFMTARGS(count * ShenandoahHeapRegion::region_size_bytes()), PROPERFMTARGS(_heap->capacity()),
               elapsed * MILLIUNITS);
}

void ShenandoahUncommitThread::stop_service() {
  MonitorLocker locker(&_stop_lock, Mutex::_safepoint_check_flag);
  _stop_requested.set();
  locker.notify_all();
}

void ShenandoahUncommitThread::forbid_uncommit() {
  MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
  _uncommit_allowed.unset();
  while (_uncommit_in_progress.is_set()) {
    locker.wait();
  }
}

void ShenandoahUncommitThread::allow_uncommit() {
  _uncommit_allowed.set();
}

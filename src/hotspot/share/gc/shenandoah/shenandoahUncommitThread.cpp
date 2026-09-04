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
#include "utilities/quickSort.hpp"

ShenandoahUncommitThread::ShenandoahUncommitThread(ShenandoahHeap* heap)
  : _heap(heap),
    _uncommit_lock(Mutex::safepoint - 2, "ShenandoahUncommit_lock", true) {
  _candidate_regions = NEW_C_HEAP_ARRAY(ShenandoahHeapRegion*, _heap->num_regions(), mtGC);
  _candidate_regions_count = 0;

  set_name("ShenUncommit");
  create_and_start();

  // Allow uncommits. This is managed by the control thread during a GC.
  _uncommit_allowed.set();
}

ShenandoahUncommitThread::~ShenandoahUncommitThread() {
  FREE_C_HEAP_ARRAY(_candidate_regions);
}

void ShenandoahUncommitThread::run_service() {
  assert(ShenandoahUncommit, "Thread should only run when uncommit is enabled");

  // poll_interval avoids constantly polling regions for shrinking.
  // Having an interval 10x lower than the delay would mean we hit the
  // shrinking with lag of less than 1/10-th of true delay.
  // ShenandoahUncommitDelay is in millis, but shrink_period is in seconds.
  const int64_t poll_interval = int64_t(ShenandoahUncommitDelay) / 10;
  const double normal_shrink_delay = double(ShenandoahUncommitDelay) / 1000;
  bool timed_out = false;
  while (!should_terminate()) {
    bool soft_max_changed = _soft_max_changed.try_unset();
    bool explicit_gc_requested = _explicit_gc_requested.try_unset();

    if (soft_max_changed || explicit_gc_requested || timed_out) {
      size_t shrink_until = soft_max_changed ? _heap->soft_max_capacity() : _heap->min_capacity();
      double shrink_delay = (soft_max_changed || explicit_gc_requested) ? 0 : normal_shrink_delay;

      // Explicit GC tries to uncommit everything down to min capacity.
      // Soft max change tries to uncommit everything down to target capacity.
      // Periodic uncommit tries to uncommit suitable regions down to min capacity.
      if (plan_work(shrink_delay, shrink_until)) {
        uncommit(shrink_delay, shrink_until);
      }
    }

    if (!should_terminate()) {
      MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
      timed_out = locker.wait(poll_interval);
    }
  }
}

static int compare_by_empty_time_asc(ShenandoahHeapRegion* a, ShenandoahHeapRegion* b) {
  if (a->empty_time() > b->empty_time()) {
    return +1;
  } else if (a->empty_time() < b->empty_time()) {
    return -1;
  } else {
    return 0;
  }
}

bool ShenandoahUncommitThread::plan_work(double shrink_delay, size_t shrink_until)  {
  _candidate_regions_count = 0;

  if (!_heap->is_idle() || !is_uncommit_allowed()) {
    // Uncommits are not welcome.
    return false;
  }

  if (_heap->committed() <= shrink_until) {
    // Do not uncommit below target.
    return false;
  }

  // Determine if there is work to do. This avoids locking the heap if there is
  // no work available, avoids spamming logs with superfluous logging messages,
  // and minimises the amount of work while locks are held. Fill out all candidates:
  // even if they are currently not targeted, byy the time we get to uncommit them,
  // they might become eligible too.
  double shrink_before = os::elapsedTime() + shrink_delay;
  bool has_work = false;
  for (size_t i = 0; i < _heap->num_regions(); i++) {
    ShenandoahHeapRegion* r = _heap->get_region(i);
    if (r->is_empty_committed()) {
      has_work |= (r->empty_time() < shrink_before);
      _candidate_regions[_candidate_regions_count++] = r;
    }
  }

  if (!has_work) {
    // No regions that match our target at all.
    return false;
  }

  // Sort candidates by earliest empty time.
  QuickSort::sort(_candidate_regions, _candidate_regions_count, compare_by_empty_time_asc);
  return true;
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
  assert(ShenandoahUncommit, "should be enabled");
  assert(_uncommit_in_progress.is_unset(), "Uncommit should not be in progress");

  {
    // Final check, under the lock, if uncommit is allowed.
    MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
    if (is_uncommit_allowed()) {
      _uncommit_in_progress.set();
    }
  }

  // If not allowed to start, do nothing.
  if (!_uncommit_in_progress.is_set()) {
    return;
  }

  // From here on, uncommit is in progress. Attempts to stop the uncommit must wait
  // until the cancellation request is acknowledged and uncommit is no longer in progress.
  const char* msg = "Concurrent uncommit";
  const double start = os::elapsedTime();
  EventMark em("%s", msg);
  log_info(gc, start)("%s", msg);

  // This is the number of regions uncommitted during this increment of uncommit work.
  const size_t uncommitted_region_count = do_uncommit_work(shrink_delay, shrink_until);

  {
    MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
    _uncommit_in_progress.unset();
    locker.notify_all();
  }

  if (uncommitted_region_count > 0) {
    _heap->notify_heap_changed();
  }

  const double elapsed = os::elapsedTime() - start;
  log_info(gc)("%s " PROPERFMT " (" PROPERFMT ") %.3fms",
               msg, PROPERFMTARGS(uncommitted_region_count * ShenandoahHeapRegion::region_size_bytes()), PROPERFMTARGS(_heap->capacity()),
               elapsed * MILLIUNITS);
}

size_t ShenandoahUncommitThread::do_uncommit_work(double shrink_delay, size_t shrink_until) {
  size_t count = 0;
  for (int i = 0; i < _candidate_regions_count; i++) {
    ShenandoahHeapRegion* r = _candidate_regions[i];
    double shrink_before = os::elapsedTime() + shrink_delay;

    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
      // Do not uncommit below the target.
      if (_heap->committed() < shrink_until + ShenandoahHeapRegion::region_size_bytes()) {
        break;
      }

      // Before we go for uncommits, stall here and allow allocators to proceed
      // taking the heap lock and start using the region. We are not in a hurry to uncommit,
      // otherwise, we will just trip through uncommit-commit wastefully.
      // Terminate early if we detect that GC wants to start.
      if (!check_uncommit_or_delay()) {
        break;
      }

      SuspendibleThreadSetJoiner sts_joiner;
      ShenandoahHeapLocker heap_locker(_heap->lock());
      if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
        r->make_uncommitted();
        count++;
      }
    }
  }
  return count;
}


void ShenandoahUncommitThread::stop_service() {
  MonitorLocker locker(&_uncommit_lock, Mutex::_safepoint_check_flag);
  _uncommit_allowed.unset();
  locker.notify_all();
}

bool ShenandoahUncommitThread::check_uncommit_or_delay() {
  MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
  locker.wait(5);
  return _uncommit_allowed.is_set();
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
}

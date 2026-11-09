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
    _uncommit_lock(Mutex::safepoint - 2, "ShenandoahUncommit_lock", true) {
  set_name("ShenUncommit");
  create_and_start();

  // Allow uncommits. This is managed by the control thread during a GC.
  _uncommit_allowed.set();
}

void ShenandoahUncommitThread::run_service() {
  assert(ShenandoahUncommit, "Thread should only run when uncommit is enabled");

  // Poll everything at least every 500ms, this should be prompt enough.
  const int64_t poll_interval_ms = 500;

  while (true) {
    bool soft_max_changed;
    bool explicit_gc_requested;

    {
      MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
      if (should_terminate()) {
        // Already requested termination, exit.
        break;
      }

      locker.wait(poll_interval_ms);

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

    double now = os::elapsedTime();
    size_t plan_prompt = 0;
    size_t plan_deferred = 0;
    size_t plan_uncommit = 0;

    // Categorise the regions and figure out if we have any work.
    for (size_t i = _heap->num_regions(); i > 0; i--) {
      ShenandoahHeapRegion* r = _heap->get_region(i - 1);
      if (r->is_empty_committed()) {
        if (soft_max_changed || explicit_gc_requested) {
          // Either threshold moved: the region is eagerly uncommittable.
          // Balance the uncommits a bit to avoid too eager uncommit.
          r->set_uncommit_time(now + plan_prompt * 0.01);
          plan_prompt++;
        } else if (r->uncommit_time() == 0) {
          // Newly empty region, record it for eventual uncommit.
          r->set_uncommit_time(now + plan_deferred * 0.01 + ShenandoahUncommitDelay / 1000);
          plan_deferred++;
        } else if (r->uncommit_time() <= now) {
          plan_uncommit++;
        }
      }
    }

    // Explicit GC tries to uncommit everything down to min capacity.
    // Soft max change tries to uncommit everything down to target capacity.
    // Periodic uncommit tries to uncommit suitable regions down to min capacity.
    size_t shrink_until = soft_max_changed ? _heap->soft_max_capacity() : _heap->min_capacity();

    if (_heap->committed() <= shrink_until) {
      // Do not uncommit below target.
      continue;
    }

    if (plan_uncommit > 0) {
      uncommit(shrink_until);
    }
  }
}

void ShenandoahUncommitThread::uncommit(size_t shrink_until) {
  assert(ShenandoahUncommit, "Should be enabled");
  assert(_uncommit_in_progress.is_unset(), "Uncommit should not be in progress");

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

    double now = os::elapsedTime();
    if (!r->is_empty_committed() || (r->uncommit_time() > now)) {
      // Not a candidate, try again.
      continue;
    }

    // Try to claim progress, gracefully waiting.
    if (!try_set_progress()) {
      // Requested to restart.
      break;
    }

    // Go for uncommit!
    {
      SuspendibleThreadSetJoiner sts_joiner;
      ShenandoahHeapLocker heap_locker(_heap->lock());
      if (r->is_empty_committed() && (r->uncommit_time() <= now)) {
        log_trace(gc)("Uncommitting region %zu", r->index());
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

bool ShenandoahUncommitThread::try_set_progress() {
  MonitorLocker locker(&_uncommit_lock, Mutex::_no_safepoint_check_flag);
  assert(_uncommit_in_progress.is_unset(), "Should be unset before checks");

  // Uncommits are disallowed. Wait until allowed again.
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

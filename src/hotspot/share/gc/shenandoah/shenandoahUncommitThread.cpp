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

#include "gc/shenandoah/shenandoahControlThread.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahUncommitThread.hpp"
#include "logging/log.hpp"

ShenandoahUncommitThread::ShenandoahUncommitThread(ShenandoahHeap* heap)
  : _heap(heap), _uncommit_requested(false) {}

bool ShenandoahUncommitThread::has_work(double shrink_before, size_t shrink_until) const {
  // Determine if there is work to do. This avoids taking heap lock if there is
  // no work available, avoids spamming logs with superfluous logging messages,
  // and minimises the amount of work while locks are taken.

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

bool ShenandoahUncommitThread::check_soft_max_changed() const {
  size_t new_soft_max = Atomic::load(&SoftMaxHeapSize);
  size_t old_soft_max = _heap->soft_max_capacity();
  if (new_soft_max != old_soft_max) {
    new_soft_max = MAX2(_heap->min_capacity(), new_soft_max);
    new_soft_max = MIN2(_heap->max_capacity(), new_soft_max);
    if (new_soft_max != old_soft_max) {
      log_info(gc)("Soft Max Heap Size: " SIZE_FORMAT "%s -> " SIZE_FORMAT "%s",
                   byte_size_in_proper_unit(old_soft_max), proper_unit_for_byte_size(old_soft_max),
                   byte_size_in_proper_unit(new_soft_max), proper_unit_for_byte_size(new_soft_max)
      );
      _heap->set_soft_max_capacity(new_soft_max);
      return true;
    }
  }
  return false;
}

void ShenandoahUncommitThread::run_service() {
  assert(ShenandoahUncommit, "Thread should only run when uncommit is enabled.");

  // Shrink period avoids constantly polling regions for shrinking.
  // Having a period 10x lower than the delay would mean we hit the
  // shrinking with lag of less than 1/10-th of true delay.
  // ShenandoahUncommitDelay is in millis, but shrink_period is in seconds.
  double shrink_period = (double)ShenandoahUncommitDelay / 1000 / 10;
  double last_shrink_time = os::elapsedTime();
  while (!should_terminate()) {
    double current = os::elapsedTime();
    bool soft_max_changed = check_soft_max_changed();

    if (soft_max_changed || current - last_shrink_time > shrink_period) {
      double shrink_before = soft_max_changed ? current : current - ((double) ShenandoahUncommitDelay / 1000.0);
      size_t shrink_until = soft_max_changed ? _heap->soft_max_capacity() : _heap->min_capacity();

      if (has_work(shrink_before, shrink_until)) {
        uncommit(shrink_before, shrink_until);
      }
    }
  }
}

void ShenandoahUncommitThread::uncommit(double shrink_before, size_t shrink_until) {
  assert (ShenandoahUncommit, "should be enabled");

  // Application allocates from the beginning of the heap, and GC allocates at
  // the end of it. It is more efficient to uncommit from the end, so that applications
  // could enjoy the near committed regions. GC allocations are much less frequent,
  // and therefore can accept the committing costs.

  size_t count = 0;
  for (size_t i = _heap->num_regions(); i > 0; i--) { // care about size_t underflow
    ShenandoahHeapRegion* r = _heap->get_region(i - 1);
    if (r->is_empty_committed() && (r->empty_time() < shrink_before)) {
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

  if (count > 0) {
    _heap->control_thread()->notify_heap_changed();
  }
}
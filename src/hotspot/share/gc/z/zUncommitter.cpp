/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "gc/shared/gc_globals.hpp"
#include "gc/z/zGlobals.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zMappedCache.hpp"
#include "gc/z/zNUMA.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zUncommitter.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

#include <cmath>

static const ZStatCounter ZCounterUncommit("Memory", "Uncommit", ZStatUnitBytesPerSecond);

ZUncommitter::ZUncommitter(uint32_t id, ZPartition* partition)
  : _id(id),
    _partition(partition),
    _lock(),
    _stop(false),
    _cancel_time(0.0),
    _next_cycle_timeout(0),
    _next_uncommit_timeout(0),
    _cycle_start(0.0),
    _to_uncommit(0),
    _uncommitted(0) {
  set_name("ZUncommitter#%u", id);
  create_and_start();
}

bool ZUncommitter::wait(uint64_t timeout) const {
  ZLocker<ZConditionLock> locker(&_lock);
  while (!ZUncommit && !_stop) {
    _lock.wait();
  }

  if (!_stop && timeout > 0) {
    if (!uncommit_cycle_is_finished()) {
      log_trace(gc, heap)("Uncommitter (%u) Timeout: " UINT64_FORMAT "ms left to uncommit: "
                          EXACTFMT, _id, timeout, EXACTFMTARGS(_to_uncommit));
    } else {
      log_debug(gc, heap)("Uncommitter (%u) Timeout: " UINT64_FORMAT "ms", _id, timeout);
    }

    double now = os::elapsedTime();
    const double wait_until = now + double(timeout) / MILLIUNITS;
    do {
      const uint64_t remaining_timeout_ms = to_millis(wait_until - now);
      if (remaining_timeout_ms == 0) {
        // Less than a millisecond left to wait, just return early
        break;
      }

      // Wait
      _lock.wait(remaining_timeout_ms);

      now = os::elapsedTime();
    } while (!_stop && now < wait_until);
  }

  return !_stop;
}

bool ZUncommitter::should_continue() const {
  ZLocker<ZConditionLock> locker(&_lock);
  return !_stop;
}

void ZUncommitter::update_statistics(size_t uncommitted, Ticks start, Tickspan* accumulated_time) const {
  // Update counter
  ZStatInc(ZCounterUncommit, uncommitted);

  Ticks end = Ticks::now();

  // Send event
  EventZUncommit::commit(start, end, uncommitted);

  // Track accumulated time
  *accumulated_time += end - start;
}

void ZUncommitter::run_thread() {
  // Initialize first cycle timeout
  _next_cycle_timeout = to_millis(ZUncommitDelay);

  while (wait(_next_cycle_timeout)) {
    // Counters for event and statistics
    Ticks start = Ticks::now();
    size_t uncommitted_since_last_timeout = 0;
    Tickspan accumulated_time;

    if (!activate_uncommit_cycle()) {
      // We failed activating a new cycle, continue until next cycle
      continue;
    }

    while (should_continue()) {
      // Uncommit chunk
      const size_t uncommitted = uncommit();

      // Update uncommitted counter
      uncommitted_since_last_timeout += uncommitted;

      // 'uncommitted == 0' is a proxy for uncommit_cycle_is_canceled() without
      // having to take the page allocator lock
      if (uncommitted == 0 || uncommit_cycle_is_finished()) {
        // Done
        break;
      }

      if (_next_uncommit_timeout != 0) {
        // Update statistics
        update_statistics(uncommitted_since_last_timeout, start, &accumulated_time);

        // Wait until next uncommit
        wait(_next_uncommit_timeout);

        // Reset event and statistics counters
        start = Ticks::now();
        uncommitted_since_last_timeout = 0;
      }
    }

    if (_uncommitted > 0) {
      if (uncommitted_since_last_timeout > 0) {
        // Update statistics
        update_statistics(uncommitted_since_last_timeout, start, &accumulated_time);
      }

      log_info(gc, heap)("Uncommitter (%u) Uncommitted: %zuM(%.0f%%) in %.3fms",
                         _id, _uncommitted / M, percent_of(_uncommitted, ZHeap::heap()->max_capacity()),
                         accumulated_time.seconds() * MILLIUNITS);
    }

    if (!should_continue()) {
      // We are terminating
      return;
    }

    deactivate_uncommit_cycle();
  }
}

void ZUncommitter::terminate() {
  ZLocker<ZConditionLock> locker(&_lock);
  _stop = true;
  _lock.notify_all();
}

void ZUncommitter::reset_uncommit_cycle() {
  _to_uncommit = 0;
  _uncommitted = 0;
  _cycle_start = 0.0;
  _cancel_time = 0.0;

  postcond(uncommit_cycle_is_finished());
  postcond(!uncommit_cycle_is_canceled());
  postcond(!uncommit_cycle_is_active());
}

void ZUncommitter::deactivate_uncommit_cycle() {
  ZLocker<ZLock> locker(&_partition->_page_allocator->_lock);

  precond(uncommit_cycle_is_active());
  precond(uncommit_cycle_is_finished() || uncommit_cycle_is_canceled());

  // Update the next timeout
  if (uncommit_cycle_is_canceled()) {
    update_next_cycle_timeout_on_cancel();
  } else {
    update_next_cycle_timeout_on_finish();
  }

  // Reset the cycle
  reset_uncommit_cycle();
}

bool ZUncommitter::activate_uncommit_cycle() {
  ZLocker<ZLock> locker(&_partition->_page_allocator->_lock);

  precond(uncommit_cycle_is_finished());
  precond(!uncommit_cycle_is_active());

  if (uncommit_cycle_is_canceled()) {
    // We were canceled before we managed to activate, update the timeout
    update_next_cycle_timeout_on_cancel();

    // Reset the cycle
    reset_uncommit_cycle();

    return false;
  }

  ZMappedCache* const cache = &_partition->_cache;

  // Claim and reset the cache cycle tracking and register the cycle start time.
  _cycle_start = os::elapsedTime();

  // Read watermark from cache
  const size_t uncommit_watermark = cache->min_size_watermark();

  // Keep 10% as a headroom
  const size_t to_uncommit = align_up(size_t(double(uncommit_watermark) * 0.9), ZGranuleSize);

  // Never uncommit below min capacity
  const size_t uncommit_limit = _partition->_capacity - _partition->_min_capacity;

  _to_uncommit = MIN2(uncommit_limit, to_uncommit);
  _uncommitted = 0;

  // Reset watermark for next uncommit cycle
  cache->reset_min_size_watermark();

  postcond(is_aligned(_to_uncommit, ZGranuleSize));

  return true;
}

uint64_t ZUncommitter::to_millis(double seconds) const {
  return uint64_t(std::floor(seconds * double(MILLIUNITS)));
}

void ZUncommitter::update_next_cycle_timeout(double from_time) {
  const double now = os::elapsedTime();

  if (now < from_time + double(ZUncommitDelay)) {
    _next_cycle_timeout = to_millis(ZUncommitDelay) - to_millis(now - from_time);
  } else {
    // ZUncommitDelay has already expired
    _next_cycle_timeout = 0;
  }
}

void ZUncommitter::update_next_cycle_timeout_on_cancel() {
  precond(uncommit_cycle_is_canceled());

  update_next_cycle_timeout(_cancel_time);

  // Skip logging if there is no delay
  if (ZUncommitDelay > 0) {
    log_debug(gc, heap)("Uncommitter (%u) Cancel Next Cycle Timeout: " UINT64_FORMAT "ms",
                        _id, _next_cycle_timeout);
  }
}

void ZUncommitter::update_next_cycle_timeout_on_finish() {
  precond(uncommit_cycle_is_active());
  precond(uncommit_cycle_is_finished());

  update_next_cycle_timeout(_cycle_start);

  // Skip logging if there is no delay
  if (ZUncommitDelay > 0) {
    log_debug(gc, heap)("Uncommitter (%u) Finish Next Cycle Timeout: " UINT64_FORMAT "ms",
                        _id, _next_cycle_timeout);
  }
}

void ZUncommitter::cancel_uncommit_cycle() {
  // Reset the cache cycle tracking and register the cancel time.
  _partition->_cache.reset_min_size_watermark();
  _cancel_time = os::elapsedTime();
}

void ZUncommitter::register_uncommit(size_t size) {
  precond(uncommit_cycle_is_active());
  precond(size > 0);
  precond(size <= _to_uncommit);
  precond(is_aligned(size, ZGranuleSize));

  _to_uncommit -= size;
  _uncommitted += size;

  if (uncommit_cycle_is_canceled()) {
    // Uncommit cycle got canceled while uncommitting.
    return;
  }

  if (uncommit_cycle_is_finished()) {
    // Everything has been uncommitted.
    return;
  }

  const double now = os::elapsedTime();
  const double time_since_start = now - _cycle_start;

  if (time_since_start == 0.0) {
    // Handle degenerate case where no time has elapsed.
    _next_uncommit_timeout = 0;
    return;
  }

  const double uncommit_rate = double(_uncommitted) / time_since_start;
  const double time_to_complete = double(_to_uncommit) / uncommit_rate;
  const double time_left = double(ZUncommitDelay) - time_since_start;

  if (time_left < time_to_complete) {
    // Too slow, work as fast as we can.
    _next_uncommit_timeout = 0;
    return;
  }

  const size_t uncommits_remaining_estimate = _to_uncommit / size + 1;
  const uint64_t millis_left_rounded_down = to_millis(time_left);

  if (uncommits_remaining_estimate < millis_left_rounded_down) {
    // We have at least one millisecond per uncommit, spread them out.
    _next_uncommit_timeout = millis_left_rounded_down / uncommits_remaining_estimate;
    return;
  }

  // Randomly distribute the extra time, one millisecond at a time.
  const double extra_time = time_left - time_to_complete;
  const double random = double(uint32_t(os::random())) / double(std::numeric_limits<uint32_t>::max());

  _next_uncommit_timeout = random < (extra_time / time_left) ? 1 : 0;
}

bool ZUncommitter::uncommit_cycle_is_finished() const {
  return _to_uncommit == 0;
}

bool ZUncommitter::uncommit_cycle_is_active() const {
  return _cycle_start != 0.0;
}

bool ZUncommitter::uncommit_cycle_is_canceled() const {
  return _cancel_time != 0.0;
}

size_t ZUncommitter::uncommit() {
  precond(uncommit_cycle_is_active());

  ZArray<ZVirtualMemory> flushed_vmems;
  size_t flushed = 0;

  {
    // We need to join the suspendible thread set while manipulating capacity
    // and used, to make sure GC safepoints will have a consistent view.
    SuspendibleThreadSetJoiner sts_joiner;
    ZLocker<ZLock> locker(&_partition->_page_allocator->_lock);

    if (uncommit_cycle_is_canceled()) {
      // We have committed within the delay, stop uncommitting.
      return 0;
    }

    // We flush out and uncommit chunks at a time (~0.8% of the max capacity,
    // but at least one granule and at most 256M), in case demand for memory
    // increases while we are uncommitting.
    const size_t current_max_capacity = _partition->_current_max_capacity;
    const size_t limit_upper_bound = MAX2(ZGranuleSize, align_down(256 * M / ZNUMA::count(), ZGranuleSize));
    const size_t limit = MIN2(align_up(current_max_capacity >> 7, ZGranuleSize), limit_upper_bound);

    ZMappedCache& cache = _partition->_cache;

    // Never uncommit more than the current uncommit watermark,
    // (adjusted by what has already been uncommitted).
    const size_t allowed_to_uncommit = MAX2(cache.min_size_watermark(), _uncommitted) - _uncommitted;
    const size_t to_uncommit = MIN2(_to_uncommit, allowed_to_uncommit);

    // Never uncommit below min capacity.
    const size_t retain = MAX2(_partition->_used, _partition->_min_capacity);
    const size_t release = _partition->_capacity - retain;
    const size_t flush = MIN3(release, limit, to_uncommit);

    // Flush memory from the mapped cache for uncommit
    flushed = cache.remove_for_uncommit(flush, &flushed_vmems);
    if (flushed == 0) {
      // Nothing flushed
      cancel_uncommit_cycle();
      return 0;
    }

    // Record flushed memory as claimed and how much we've flushed for this partition
    Atomic::add(&_partition->_claimed, flushed);
  }

  // Unmap and uncommit flushed memory
  for (const ZVirtualMemory vmem : flushed_vmems) {
    _partition->unmap_virtual(vmem);
    _partition->uncommit_physical(vmem);
    _partition->free_physical(vmem);
    _partition->free_virtual(vmem);
  }

  {
    SuspendibleThreadSetJoiner sts_joiner;
    ZLocker<ZLock> locker(&_partition->_page_allocator->_lock);

    // Adjust claimed and capacity to reflect the uncommit
    Atomic::sub(&_partition->_claimed, flushed);
    _partition->decrease_capacity(flushed, false /* set_max_capacity */);
    register_uncommit(flushed);
  }

  return flushed;
}

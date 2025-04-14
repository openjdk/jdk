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
#include "gc/z/zStat.hpp"
#include "gc/z/zUncommitter.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

#include <cmath>
#include <limits>

static const ZStatCounter ZCounterUncommit("Memory", "Uncommit", ZStatUnitBytesPerSecond);

ZUncommitter::ZUncommitter(uint32_t id, ZPartition* partition)
  : _id(id),
    _partition(partition),
    _lock(),
    _stop(false),
    _cancel_time(0.0),
    _next_cycle_timeout(ZUncommitDelay),
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
      log_debug(gc, heap)("Uncommitter (%u) Timeout: " UINT64_FORMAT "s left to uncommit: "
                          EXACTFMT, _id, timeout, EXACTFMTARGS(_to_uncommit));
    } else {
      log_debug(gc, heap)("Uncommitter (%u) Timeout: " UINT64_FORMAT "s", _id, timeout);
    }
    _lock.wait(timeout * MILLIUNITS);
  }

  return !_stop;
}

bool ZUncommitter::should_continue() const {
  ZLocker<ZConditionLock> locker(&_lock);
  return !_stop;
}

void ZUncommitter::run_thread() {
  while (wait(_next_cycle_timeout)) {
    EventZUncommit event;

    while (should_continue()) {
      // Uncommit chunk
      const size_t uncommitted = _partition->uncommit();
      if (uncommitted == 0 || uncommit_cycle_is_finished()) {
        // Done
        break;
      }

      // Wait until next uncommit
      wait(_next_uncommit_timeout);
    }

    if (_uncommitted > 0) {
      // Update statistics
      ZStatInc(ZCounterUncommit, _uncommitted);
      log_info(gc, heap)("Uncommitter (%u) Uncommitted: %zuM(%.0f%%)",
                         _id, _uncommitted / M, percent_of(_uncommitted, ZHeap::heap()->max_capacity()));

      // Send event
      event.commit(_uncommitted);
    }

    deactivate_uncommit_cycle();
  }
}

void ZUncommitter::terminate() {
  ZLocker<ZConditionLock> locker(&_lock);
  _stop = true;
  _lock.notify_all();
}

void ZUncommitter::deactivate_uncommit_cycle() {
  if (!should_continue()) {
    // We are stopping
    return;
  }

  _partition->evaluate_under_lock([&]() {
    precond(uncommit_cycle_is_active() || uncommit_cycle_is_canceled());
    precond(uncommit_cycle_is_finished() || uncommit_cycle_is_canceled());
    // Update the next timeout
    if (uncommit_cycle_is_canceled()) {
      update_next_cycle_timeout_on_cancel();
    } else {
      update_next_cycle_timeout_on_finish();
    }

    // Reset the cycle
    _to_uncommit = 0;
    _uncommitted = 0;
    _cycle_start = 0.0;
    _cancel_time = 0.0;
    postcond(uncommit_cycle_is_finished());
    postcond(!uncommit_cycle_is_canceled());
    postcond(!uncommit_cycle_is_active());
  });
}

void ZUncommitter::activate_uncommit_cycle(size_t to_uncommit) {
  precond(uncommit_cycle_is_finished());
  precond(!uncommit_cycle_is_active());
  precond(!uncommit_cycle_is_canceled());
  precond(is_aligned(to_uncommit, ZGranuleSize));
  _cycle_start = os::elapsedTime();
  _to_uncommit = to_uncommit;
  _uncommitted = 0;
}

size_t ZUncommitter::to_uncommit() const {
  return _to_uncommit;
}

void ZUncommitter::update_next_cycle_timeout_on_cancel() {
  precond(uncommit_cycle_is_canceled());

  const double now = os::elapsedTime();

  if (now < _cancel_time + double(ZUncommitDelay)) {
    _next_cycle_timeout = ZUncommitDelay - uint64_t(std::floor(now - _cancel_time));
  } else {
    // ZUncommitDelay has already expired
    _next_cycle_timeout = 0.0;
  }

  log_debug(gc, heap)("Uncommitter (%u) Cancel Next Cycle Timeout: " UINT64_FORMAT "s",
                      _id, _next_cycle_timeout);
}

void ZUncommitter::update_next_cycle_timeout_on_finish() {
  precond(uncommit_cycle_is_active());
  precond(uncommit_cycle_is_finished());

  const double now = os::elapsedTime();

  if (now < _cycle_start + double(ZUncommitDelay)) {
    _next_cycle_timeout = ZUncommitDelay - uint64_t(std::floor(now - _cycle_start));
  } else {
    // ZUncommitDelay has already expired
    _next_cycle_timeout = 0.0;
  }

  log_debug(gc, heap)("Uncommitter (%u) Finish Next Cycle Timeout: " UINT64_FORMAT "s",
                      _id, _next_cycle_timeout);
}

void ZUncommitter::cancel_uncommit_cycle() {
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
  const double time_to_compleat = double(_to_uncommit) / uncommit_rate;
  const double time_left = double(ZUncommitDelay) - time_since_start;

  if (time_left < time_to_compleat) {
    // To slow, work as fast as we can.
    _next_uncommit_timeout = 0;
    return;
  }

  const size_t uncommits_remaining_estimate = _to_uncommit / size + 1;
  const uint64_t time_left_rounded_down = uint64_t(std::floor(time_left));

  if (uncommits_remaining_estimate < time_left_rounded_down) {
    // We have at least one second per uncommit, spread them out.
    _next_uncommit_timeout = time_left_rounded_down / uncommits_remaining_estimate;
    return;
  }

  // Randomly distribute the extra time, one second at a time.
  const double extra_time = time_left - time_to_compleat;
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

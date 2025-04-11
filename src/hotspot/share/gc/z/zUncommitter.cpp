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
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zUncommitter.hpp"
#include "jfr/jfrEvents.hpp"
#include "logging/log.hpp"

static const ZStatCounter ZCounterUncommit("Memory", "Uncommit", ZStatUnitBytesPerSecond);

ZUncommitter::ZUncommitter(uint32_t id, ZPartition* partition)
  : _id(id),
    _partition(partition),
    _lock(),
    _stop(false) {
  set_name("ZUncommitter#%u", id);
  create_and_start();
}

bool ZUncommitter::wait(uint64_t timeout) const {
  ZLocker<ZConditionLock> locker(&_lock);
  while (!ZUncommit && !_stop) {
    _lock.wait();
  }

  if (!_stop && timeout > 0) {
    log_debug(gc, heap)("Uncommitter (%u) Timeout: " UINT64_FORMAT "s", _id, timeout);
    _lock.wait(timeout * MILLIUNITS);
  }

  return !_stop;
}

bool ZUncommitter::should_continue() const {
  ZLocker<ZConditionLock> locker(&_lock);
  return !_stop;
}

void ZUncommitter::run_thread() {
  uint64_t timeout = 0;

  while (wait(timeout)) {
    EventZUncommit event;
    size_t total_uncommitted = 0;

    while (should_continue()) {
      // Uncommit chunk
      const size_t uncommitted = _partition->uncommit(&timeout);
      if (uncommitted == 0) {
        // Done
        break;
      }

      total_uncommitted += uncommitted;
    }

    if (total_uncommitted > 0) {
      // Update statistics
      ZStatInc(ZCounterUncommit, total_uncommitted);
      log_info(gc, heap)("Uncommitter (%u) Uncommitted: %zuM(%.0f%%)",
                         _id, total_uncommitted / M, percent_of(total_uncommitted, ZHeap::heap()->max_capacity()));

      // Send event
      event.commit(total_uncommitted);
    }
  }
}

void ZUncommitter::terminate() {
  ZLocker<ZConditionLock> locker(&_lock);
  _stop = true;
  _lock.notify_all();
}

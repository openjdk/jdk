/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zUncommitter.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"

ZUncommitter::ZUncommitter() :
    _monitor(Monitor::leaf, "ZUncommitter", false, Monitor::_safepoint_check_never),
    _stop(false) {
  set_name("ZUncommitter");
  create_and_start();
}

bool ZUncommitter::idle(uint64_t timeout) {
  // Idle for at least one second
  const uint64_t expires = os::elapsedTime() + MAX2<uint64_t>(timeout, 1);

  for (;;) {
    // We might wake up spuriously from wait, so always recalculate
    // the timeout after a wakeup to see if we need to wait again.
    const uint64_t now = os::elapsedTime();
    const uint64_t remaining = expires - MIN2(expires, now);

    MonitorLocker ml(&_monitor, Monitor::_no_safepoint_check_flag);
    if (remaining > 0 && !_stop) {
      ml.wait(remaining * MILLIUNITS);
    } else {
      return !_stop;
    }
  }
}

void ZUncommitter::run_service() {
  for (;;) {
    // Try uncommit unused memory
    const uint64_t timeout = ZHeap::heap()->uncommit(ZUncommitDelay);

    log_trace(gc, heap)("Uncommit Timeout: " UINT64_FORMAT "s", timeout);

    // Idle until next attempt
    if (!idle(timeout)) {
      return;
    }
  }
}

void ZUncommitter::stop_service() {
  MonitorLocker ml(&_monitor, Monitor::_no_safepoint_check_flag);
  _stop = true;
  ml.notify();
}

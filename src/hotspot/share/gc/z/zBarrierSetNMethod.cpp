/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "code/nmethod.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/z/zAddress.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zBarrierSetNMethod.hpp"
#include "gc/z/zLock.inline.hpp"
#include "gc/z/zNMethod.hpp"
#include "gc/z/zResurrection.inline.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "logging/log.hpp"
#include "runtime/threadWXSetters.inline.hpp"

bool ZBarrierSetNMethod::nmethod_entry_barrier(nmethod* nm) {
  if (!is_armed(nm)) {
    log_develop_trace(gc, nmethod)("nmethod: " PTR_FORMAT " visited by entry (disarmed before lock)", p2i(nm));
    // Some other thread got here first and healed the oops
    // and disarmed the nmethod. No need to continue.
    return true;
  }

  ZLocker<ZReentrantLock> locker(ZNMethod::lock_for_nmethod(nm));
  log_trace(nmethod, barrier)("Entered critical zone for %p", nm);

  log_develop_trace(gc, nmethod)("nmethod: " PTR_FORMAT " visited by entry (try)", p2i(nm));

  if (!is_armed(nm)) {
    log_develop_trace(gc, nmethod)("nmethod: " PTR_FORMAT " visited by entry (disarmed)", p2i(nm));
    // Some other thread managed to complete while we were
    // waiting for lock. No need to continue.
    return true;
  }

  MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXWrite, Thread::current()));

  if (nm->is_unloading()) {
    log_develop_trace(gc, nmethod)("nmethod: " PTR_FORMAT " visited by entry (unloading)", p2i(nm));
    // We don't need to take the lock when unlinking nmethods from
    // the Method, because it is only concurrently unlinked by
    // the entry barrier, which acquires the per nmethod lock.
    nm->unlink_from_method();

    // We can end up calling nmethods that are unloading
    // since we clear compiled ICs lazily. Returning false
    // will re-resolve the call and update the compiled IC.
    return false;
  }

  // Heal barriers
  ZNMethod::nmethod_patch_barriers(nm);

  // Heal oops
  ZUncoloredRootProcessWeakOopClosure cl(ZNMethod::color(nm));
  ZNMethod::nmethod_oops_do_inner(nm, &cl);

  const uintptr_t prev_color = ZNMethod::color(nm);
  const uintptr_t new_color = *ZPointerStoreGoodMaskLowOrderBitsAddr;
  log_develop_trace(gc, nmethod)("nmethod: " PTR_FORMAT " visited by entry (complete) [" PTR_FORMAT " -> " PTR_FORMAT "]", p2i(nm), prev_color, new_color);

  // CodeCache unloading support
  nm->mark_as_maybe_on_stack();

  // Disarm
  disarm(nm);

  return true;
}

int* ZBarrierSetNMethod::disarmed_guard_value_address() const {
  return (int*)ZPointerStoreGoodMaskLowOrderBitsAddr;
}

ByteSize ZBarrierSetNMethod::thread_disarmed_guard_value_offset() const {
  return ZThreadLocalData::nmethod_disarmed_offset();
}

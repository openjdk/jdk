/*
 * Copyright (c) 2019, 2022, Red Hat, Inc. All rights reserved.
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

#include "precompiled.hpp"

#include "gc/shenandoah/shenandoahBarrierSetNMethod.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahLock.hpp"
#include "gc/shenandoah/shenandoahNMethod.inline.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/threadWXSetters.inline.hpp"

bool ShenandoahBarrierSetNMethod::nmethod_entry_barrier(nmethod* nm) {
  if (!is_armed(nm)) {
    // Some other thread got here first and healed the oops
    // and disarmed the nmethod. No need to continue.
    return true;
  }

  ShenandoahReentrantLock* lock = ShenandoahNMethod::lock_for_nmethod(nm);
  assert(lock != nullptr, "Must be");
  ShenandoahReentrantLocker locker(lock);

  if (!is_armed(nm)) {
    // Some other thread managed to complete while we were
    // waiting for lock. No need to continue.
    return true;
  }

  MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXWrite, Thread::current());)

  if (nm->is_unloading()) {
    // We don't need to take the lock when unlinking nmethods from
    // the Method, because it is only concurrently unlinked by
    // the entry barrier, which acquires the per nmethod lock.
    nm->unlink_from_method();

    // We can end up calling nmethods that are unloading
    // since we clear compiled ICs lazily. Returning false
    // will re-resovle the call and update the compiled IC.
    return false;
  }

  // Heal oops
  ShenandoahNMethod::heal_nmethod(nm);

  // CodeCache unloading support
  nm->mark_as_maybe_on_stack();

  // Disarm
  ShenandoahNMethod::disarm_nmethod(nm);
  return true;
}

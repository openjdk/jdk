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


#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahBarrierSetNMethod.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahCodeRoots.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahLock.hpp"
#include "gc/shenandoah/shenandoahNMethod.inline.hpp"
#include "gc/shenandoah/shenandoahStackWatermark.hpp"
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

  ShenandoahNMethodLock* lock = ShenandoahNMethod::lock_for_nmethod(nm);
  assert(lock != nullptr, "Must be");
  ShenandoahNMethodLocker locker(lock);

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
    // will re-resolve the call and update the compiled IC.
    return false;
  }

  bool changed = false;

  // Handle oops and barriers.
  changed |= ShenandoahNMethod::handle_oops(nm);
  changed |= ShenandoahNMethod::handle_barriers(nm);

  // If any code changed, bulk invalidate the entire nmethod.
  if (changed) {
    ICache::invalidate_range(nm->code_begin(), nm->code_size());
  }

  // CodeCache unloading support
  nm->mark_as_maybe_on_stack();

  // Disarm
  ShenandoahNMethod::disarm_nmethod(nm);
  return true;
}

void ShenandoahBarrierSetNMethod::finalize_relocations(nmethod* nm) {
  RelocIterator iter(nm);
  while (iter.next()) {
    if (iter.type() == relocInfo::patchable_barrier_type) {
      patchable_barrier_Relocation* r = iter.patchable_barrier_reloc();
      if (!r->is_target_offset_resolved()) {
        address pc = r->addr();
        address target = ShenandoahBarrierSetAssembler::parse_jump_address(pc);
        r->set_target_offset(target - nm->code_begin());
      } else {
        // Already set. This is likely nmethod relocation, so just trust
        // the existing relocation data.
      }
    }
  }
}

void ShenandoahBarrierSetNMethod::arm_all_nmethods() {
  BarrierSetNMethod::arm_all_nmethods();

  // Arming should also activate stack watermark machinery.
  // See ShenandoahNMethod::patch_barrier.
  ShenandoahStackWatermark::change_epoch_id();
}

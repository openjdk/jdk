/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
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


#include "compiler/oopMap.hpp"
#include "gc/shared/workerThread.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahGC.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"

void ShenandoahGC::update_roots() {
  assert(ShenandoahSafepoint::is_at_shenandoah_safepoint(), "Must be at a safepoint");
  assert(ShenandoahHeap::heap()->is_full_gc_in_progress(), "Only for full GC");

  ShenandoahGCPhase phase(ShenandoahPhaseTimings::full_gc_update_roots);
#ifdef COMPILER2
  DerivedPointerTable::clear();
#endif // COMPILER2

  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  WorkerThreads* workers = heap->workers();
  uint nworkers = workers->active_workers();

  ShenandoahRootUpdater root_updater(nworkers, ShenandoahPhaseTimings::full_gc_update_roots);
  ShenandoahUpdateRootsTask update_roots(&root_updater, /* check_alive = */ false);
  workers->run_task(&update_roots);

#ifdef COMPILER2
  DerivedPointerTable::update_pointers();
#endif // COMPILER2
}

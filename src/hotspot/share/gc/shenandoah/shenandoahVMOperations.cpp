/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
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

#include "gc/shenandoah/shenandoahConcurrentGC.hpp"
#include "gc/shenandoah/shenandoahDegeneratedGC.hpp"
#include "gc/shenandoah/shenandoahFullGC.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahOopClosures.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "interpreter/oopMapCache.hpp"
#include "memory/universe.hpp"

bool VM_ShenandoahOperation::doit_prologue() {
  assert(!ShenandoahHeap::heap()->has_gc_state_changed(), "GC State can only be changed on a safepoint.");
  return true;
}

void VM_ShenandoahOperation::doit_epilogue() {
  assert(!ShenandoahHeap::heap()->has_gc_state_changed(), "GC State was not synchronized to java threads.");
  // GC thread root traversal likely used OopMapCache a lot, which
  // might have created lots of old entries. Trigger the cleanup now.
  OopMapCache::trigger_cleanup();
}

bool VM_ShenandoahReferenceOperation::doit_prologue() {
  VM_ShenandoahOperation::doit_prologue();
  Heap_lock->lock();
  return true;
}

void VM_ShenandoahReferenceOperation::doit_epilogue() {
  VM_ShenandoahOperation::doit_epilogue();
  if (Universe::has_reference_pending_list()) {
    Heap_lock->notify_all();
  }
  Heap_lock->unlock();
}

void VM_ShenandoahInitMark::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Init Mark", SvcGCMarker::CONCURRENT);
  _gc->entry_init_mark();
  ShenandoahHeap::heap()->propagate_gc_state_to_java_threads();
}

void VM_ShenandoahFinalMarkStartEvac::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Final Mark", SvcGCMarker::CONCURRENT);
  _gc->entry_final_mark();
  ShenandoahHeap::heap()->propagate_gc_state_to_java_threads();
}

void VM_ShenandoahFullGC::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Full GC", SvcGCMarker::FULL);
  _full_gc->entry_full(_gc_cause);
  ShenandoahHeap::heap()->propagate_gc_state_to_java_threads();
}

void VM_ShenandoahDegeneratedGC::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Degenerated GC", SvcGCMarker::CONCURRENT);
  _gc->entry_degenerated();
  ShenandoahHeap::heap()->propagate_gc_state_to_java_threads();
}

void VM_ShenandoahInitUpdateRefs::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Init Update Refs", SvcGCMarker::CONCURRENT);
  _gc->entry_init_updaterefs();
  ShenandoahHeap::heap()->propagate_gc_state_to_java_threads();
}

void VM_ShenandoahFinalUpdateRefs::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Final Update Refs", SvcGCMarker::CONCURRENT);
  _gc->entry_final_updaterefs();
  ShenandoahHeap::heap()->propagate_gc_state_to_java_threads();
}

void VM_ShenandoahFinalRoots::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Final Roots", SvcGCMarker::CONCURRENT);
  _gc->entry_final_roots();
  ShenandoahHeap::heap()->propagate_gc_state_to_java_threads();
}

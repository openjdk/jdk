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


#include "gc/shenandoah/shenandoahConcurrentGC.hpp"
#include "gc/shenandoah/shenandoahDegeneratedGC.hpp"
#include "gc/shenandoah/shenandoahFullGC.hpp"
#include "gc/shenandoah/shenandoahGeneration.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "interpreter/oopMapCache.hpp"
#include "logging/log.hpp"
#include "memory/universe.hpp"

bool VM_ShenandoahOperation::doit_prologue() {
  log_active_generation("Prologue");
  assert(!ShenandoahHeap::heap()->has_gc_state_changed(), "GC State can only be changed on a safepoint.");
  return true;
}

void VM_ShenandoahOperation::doit_epilogue() {
  log_active_generation("Epilogue");
  assert(!ShenandoahHeap::heap()->has_gc_state_changed(), "GC State was not synchronized to java threads.");
  // GC thread root traversal likely used OopMapCache a lot, which
  // might have created lots of old entries. Trigger the cleanup now.
  OopMapCache::try_trigger_cleanup();
}

void VM_ShenandoahOperation::log_active_generation(const char* prefix) {
  ShenandoahGeneration* agen = ShenandoahHeap::heap()->active_generation();
  ShenandoahGeneration* ggen = ShenandoahHeap::heap()->gc_generation();
  log_debug(gc, heap)("%s: active_generation is %s, gc_generation is %s", prefix,
                      agen == nullptr ? "nullptr" : shenandoah_generation_name(agen->type()),
                      ggen == nullptr ? "nullptr" : shenandoah_generation_name(ggen->type()));
}

void VM_ShenandoahOperation::set_active_generation() {
  if (evaluate_at_safepoint()) {
    assert(SafepointSynchronize::is_at_safepoint(), "Error??");
    ShenandoahHeap::heap()->set_active_generation();
  }
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
  set_active_generation();
  _gc->entry_init_mark();
}

void VM_ShenandoahFinalMarkStartEvac::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Final Mark", SvcGCMarker::CONCURRENT);
  set_active_generation();
  _gc->entry_final_mark();
}

void VM_ShenandoahFullGC::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Full GC", SvcGCMarker::FULL);
  set_active_generation();
  _full_gc->entry_full(_gc_cause);
}

void VM_ShenandoahDegeneratedGC::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Degenerated GC", SvcGCMarker::CONCURRENT);
  set_active_generation();
  _gc->entry_degenerated();
}

void VM_ShenandoahInitUpdateRefs::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Init Update Refs", SvcGCMarker::CONCURRENT);
  set_active_generation();
  _gc->entry_init_update_refs();
}

void VM_ShenandoahFinalUpdateRefs::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Final Update Refs", SvcGCMarker::CONCURRENT);
  set_active_generation();
  _gc->entry_final_update_refs();
}

void VM_ShenandoahFinalRoots::doit() {
  ShenandoahGCPauseMark mark(_gc_id, "Final Roots", SvcGCMarker::CONCURRENT);
  set_active_generation();
  _gc->entry_verify_final_roots();
}

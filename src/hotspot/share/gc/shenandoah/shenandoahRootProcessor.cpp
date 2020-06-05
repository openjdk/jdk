/*
 * Copyright (c) 2015, 2020, Red Hat, Inc. All rights reserved.
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

#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "code/codeCache.hpp"
#include "code/nmethod.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahConcurrentRoots.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "jfr/jfr.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/thread.hpp"
#include "services/management.hpp"

ShenandoahSerialRoot::ShenandoahSerialRoot(ShenandoahSerialRoot::OopsDo oops_do,
  ShenandoahPhaseTimings::Phase phase, ShenandoahPhaseTimings::ParPhase par_phase) :
  _oops_do(oops_do), _phase(phase), _par_phase(par_phase) {
}

void ShenandoahSerialRoot::oops_do(OopClosure* cl, uint worker_id) {
  if (_claimed.try_set()) {
    ShenandoahWorkerTimingsTracker timer(_phase, _par_phase, worker_id);
    _oops_do(cl);
  }
}

ShenandoahSerialRoots::ShenandoahSerialRoots(ShenandoahPhaseTimings::Phase phase) :
  _universe_root(&Universe::oops_do, phase, ShenandoahPhaseTimings::UniverseRoots),
  _object_synchronizer_root(&ObjectSynchronizer::oops_do, phase, ShenandoahPhaseTimings::ObjectSynchronizerRoots),
  _management_root(&Management::oops_do, phase, ShenandoahPhaseTimings::ManagementRoots),
  _jvmti_root(&JvmtiExport::oops_do, phase, ShenandoahPhaseTimings::JVMTIRoots) {
}

void ShenandoahSerialRoots::oops_do(OopClosure* cl, uint worker_id) {
  _universe_root.oops_do(cl, worker_id);
  _object_synchronizer_root.oops_do(cl, worker_id);
  _management_root.oops_do(cl, worker_id);
  _jvmti_root.oops_do(cl, worker_id);
}

ShenandoahWeakSerialRoot::ShenandoahWeakSerialRoot(ShenandoahWeakSerialRoot::WeakOopsDo weak_oops_do,
  ShenandoahPhaseTimings::Phase phase, ShenandoahPhaseTimings::ParPhase par_phase) :
  _weak_oops_do(weak_oops_do), _phase(phase), _par_phase(par_phase) {
}

void ShenandoahWeakSerialRoot::weak_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive, uint worker_id) {
  if (_claimed.try_set()) {
    ShenandoahWorkerTimingsTracker timer(_phase, _par_phase, worker_id);
    _weak_oops_do(is_alive, keep_alive);
  }
}

#if INCLUDE_JVMTI
ShenandoahJVMTIWeakRoot::ShenandoahJVMTIWeakRoot(ShenandoahPhaseTimings::Phase phase) :
  ShenandoahWeakSerialRoot(&JvmtiExport::weak_oops_do, phase, ShenandoahPhaseTimings::JVMTIWeakRoots) {
}
#endif // INCLUDE_JVMTI

#if INCLUDE_JFR
ShenandoahJFRWeakRoot::ShenandoahJFRWeakRoot(ShenandoahPhaseTimings::Phase phase) :
  ShenandoahWeakSerialRoot(&Jfr::weak_oops_do, phase, ShenandoahPhaseTimings::JFRWeakRoots) {
}
#endif // INCLUDE_JFR

void ShenandoahSerialWeakRoots::weak_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive, uint worker_id) {
  JVMTI_ONLY(_jvmti_weak_roots.weak_oops_do(is_alive, keep_alive, worker_id);)
  JFR_ONLY(_jfr_weak_roots.weak_oops_do(is_alive, keep_alive, worker_id);)
}

void ShenandoahSerialWeakRoots::weak_oops_do(OopClosure* cl, uint worker_id) {
  AlwaysTrueClosure always_true;
  weak_oops_do(&always_true, cl, worker_id);
}

ShenandoahThreadRoots::ShenandoahThreadRoots(ShenandoahPhaseTimings::Phase phase, bool is_par) :
  _phase(phase), _is_par(is_par) {
  Threads::change_thread_claim_token();
}

void ShenandoahThreadRoots::oops_do(OopClosure* oops_cl, CodeBlobClosure* code_cl, uint worker_id) {
  ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::ThreadRoots, worker_id);
  ResourceMark rm;
  Threads::possibly_parallel_oops_do(_is_par, oops_cl, code_cl);
}

void ShenandoahThreadRoots::threads_do(ThreadClosure* tc, uint worker_id) {
  ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::ThreadRoots, worker_id);
  ResourceMark rm;
  Threads::possibly_parallel_threads_do(_is_par, tc);
}

ShenandoahThreadRoots::~ShenandoahThreadRoots() {
  Threads::assert_all_threads_claimed();
}

ShenandoahStringDedupRoots::ShenandoahStringDedupRoots(ShenandoahPhaseTimings::Phase phase) : _phase(phase) {
  if (ShenandoahStringDedup::is_enabled()) {
    StringDedup::gc_prologue(false);
  }
}

ShenandoahStringDedupRoots::~ShenandoahStringDedupRoots() {
  if (ShenandoahStringDedup::is_enabled()) {
    StringDedup::gc_epilogue();
  }
}

void ShenandoahStringDedupRoots::oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive, uint worker_id) {
  if (ShenandoahStringDedup::is_enabled()) {
    ShenandoahStringDedup::parallel_oops_do(_phase, is_alive, keep_alive, worker_id);
  }
}

ShenandoahConcurrentStringDedupRoots::ShenandoahConcurrentStringDedupRoots(ShenandoahPhaseTimings::Phase phase) :
  _phase(phase) {
  if (ShenandoahStringDedup::is_enabled()) {
    StringDedupTable_lock->lock_without_safepoint_check();
    StringDedupQueue_lock->lock_without_safepoint_check();
    StringDedup::gc_prologue(true);
  }
}

ShenandoahConcurrentStringDedupRoots::~ShenandoahConcurrentStringDedupRoots() {
  if (ShenandoahStringDedup::is_enabled()) {
    StringDedup::gc_epilogue();
    StringDedupQueue_lock->unlock();
    StringDedupTable_lock->unlock();
  }
}

void ShenandoahConcurrentStringDedupRoots::oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive, uint worker_id) {
  if (ShenandoahStringDedup::is_enabled()) {
    assert_locked_or_safepoint_weak(StringDedupQueue_lock);
    assert_locked_or_safepoint_weak(StringDedupTable_lock);

    StringDedupUnlinkOrOopsDoClosure sd_cl(is_alive, keep_alive);
    {
      ShenandoahWorkerTimingsTracker x(_phase, ShenandoahPhaseTimings::StringDedupQueueRoots, worker_id);
      StringDedupQueue::unlink_or_oops_do(&sd_cl);
    }

    {
      ShenandoahWorkerTimingsTracker x(_phase, ShenandoahPhaseTimings::StringDedupTableRoots, worker_id);
      StringDedupTable::unlink_or_oops_do(&sd_cl, worker_id);
    }
  }
}

ShenandoahCodeCacheRoots::ShenandoahCodeCacheRoots(ShenandoahPhaseTimings::Phase phase) : _phase(phase) {
  nmethod::oops_do_marking_prologue();
}

void ShenandoahCodeCacheRoots::code_blobs_do(CodeBlobClosure* blob_cl, uint worker_id) {
  ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::CodeCacheRoots, worker_id);
  _coderoots_iterator.possibly_parallel_blobs_do(blob_cl);
}

ShenandoahCodeCacheRoots::~ShenandoahCodeCacheRoots() {
  nmethod::oops_do_marking_epilogue();
}

ShenandoahRootProcessor::ShenandoahRootProcessor(ShenandoahPhaseTimings::Phase phase) :
  _heap(ShenandoahHeap::heap()),
  _phase(phase),
  _worker_phase(phase) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must at safepoint");
}

ShenandoahRootScanner::ShenandoahRootScanner(uint n_workers, ShenandoahPhaseTimings::Phase phase) :
  ShenandoahRootProcessor(phase),
  _serial_roots(phase),
  _thread_roots(phase, n_workers > 1),
  _dedup_roots(phase) {
  nmethod::oops_do_marking_prologue();
}

ShenandoahRootScanner::~ShenandoahRootScanner() {
  nmethod::oops_do_marking_epilogue();
}

void ShenandoahRootScanner::roots_do(uint worker_id, OopClosure* oops) {
  CLDToOopClosure clds_cl(oops, ClassLoaderData::_claim_strong);
  MarkingCodeBlobClosure blobs_cl(oops, !CodeBlobToOopClosure::FixRelocations);
  roots_do(worker_id, oops, &clds_cl, &blobs_cl);
}

void ShenandoahRootScanner::strong_roots_do(uint worker_id, OopClosure* oops) {
  CLDToOopClosure clds_cl(oops, ClassLoaderData::_claim_strong);
  MarkingCodeBlobClosure blobs_cl(oops, !CodeBlobToOopClosure::FixRelocations);
  strong_roots_do(worker_id, oops, &clds_cl, &blobs_cl);
}

void ShenandoahRootScanner::roots_do(uint worker_id, OopClosure* oops, CLDClosure* clds, CodeBlobClosure* code, ThreadClosure *tc) {
  assert(!ShenandoahSafepoint::is_at_shenandoah_safepoint() ||
         !ShenandoahHeap::heap()->unload_classes(),
          "Expect class unloading when Shenandoah cycle is running");
  assert(clds != NULL, "Only possible with CLD closure");

  AlwaysTrueClosure always_true;
  ShenandoahParallelOopsDoThreadClosure tc_cl(oops, code, tc);

  ResourceMark rm;

  // Process serial-claiming roots first
  _serial_roots.oops_do(oops, worker_id);

   // Process light-weight/limited parallel roots then
  _dedup_roots.oops_do(&always_true, oops, worker_id);

  // Process heavy-weight/fully parallel roots the last
  _thread_roots.threads_do(&tc_cl, worker_id);
}

void ShenandoahRootScanner::strong_roots_do(uint worker_id, OopClosure* oops, CLDClosure* clds, CodeBlobClosure* code, ThreadClosure* tc) {
  assert(ShenandoahHeap::heap()->unload_classes(), "Should be used during class unloading");
  ShenandoahParallelOopsDoThreadClosure tc_cl(oops, code, tc);

  ResourceMark rm;

  // Process serial-claiming roots first
  _serial_roots.oops_do(oops, worker_id);

  // Process heavy-weight/fully parallel roots the last
  _thread_roots.threads_do(&tc_cl, worker_id);
}

ShenandoahRootEvacuator::ShenandoahRootEvacuator(uint n_workers,
                                                 ShenandoahPhaseTimings::Phase phase,
                                                 bool stw_roots_processing,
                                                 bool stw_class_unloading) :
  ShenandoahRootProcessor(phase),
  _serial_roots(phase),
  _vm_roots(phase),
  _cld_roots(phase, n_workers),
  _thread_roots(phase, n_workers > 1),
  _serial_weak_roots(phase),
  _weak_roots(phase),
  _dedup_roots(phase),
  _code_roots(phase),
  _stw_roots_processing(stw_roots_processing),
  _stw_class_unloading(stw_class_unloading) {
}

void ShenandoahRootEvacuator::roots_do(uint worker_id, OopClosure* oops) {
  MarkingCodeBlobClosure blobsCl(oops, CodeBlobToOopClosure::FixRelocations);
  ShenandoahCodeBlobAndDisarmClosure blobs_and_disarm_Cl(oops);
  CodeBlobToOopClosure* codes_cl = ShenandoahConcurrentRoots::can_do_concurrent_class_unloading() ?
                                   static_cast<CodeBlobToOopClosure*>(&blobs_and_disarm_Cl) :
                                   static_cast<CodeBlobToOopClosure*>(&blobsCl);
  AlwaysTrueClosure always_true;

  // Process serial-claiming roots first
  _serial_roots.oops_do(oops, worker_id);
  _serial_weak_roots.weak_oops_do(oops, worker_id);

  // Process light-weight/limited parallel roots then
  if (_stw_roots_processing) {
    _vm_roots.oops_do<OopClosure>(oops, worker_id);
    _weak_roots.oops_do<OopClosure>(oops, worker_id);
    _dedup_roots.oops_do(&always_true, oops, worker_id);
  }
  if (_stw_class_unloading) {
    CLDToOopClosure clds(oops, ClassLoaderData::_claim_strong);
    _cld_roots.cld_do(&clds, worker_id);
  }

  // Process heavy-weight/fully parallel roots the last
  if (_stw_class_unloading) {
    _code_roots.code_blobs_do(codes_cl, worker_id);
    _thread_roots.oops_do(oops, NULL, worker_id);
  } else {
    _thread_roots.oops_do(oops, codes_cl, worker_id);
  }
}

ShenandoahRootUpdater::ShenandoahRootUpdater(uint n_workers, ShenandoahPhaseTimings::Phase phase) :
  ShenandoahRootProcessor(phase),
  _serial_roots(phase),
  _vm_roots(phase),
  _cld_roots(phase, n_workers),
  _thread_roots(phase, n_workers > 1),
  _serial_weak_roots(phase),
  _weak_roots(phase),
  _dedup_roots(phase),
  _code_roots(phase) {
}

ShenandoahRootAdjuster::ShenandoahRootAdjuster(uint n_workers, ShenandoahPhaseTimings::Phase phase) :
  ShenandoahRootProcessor(phase),
  _serial_roots(phase),
  _vm_roots(phase),
  _cld_roots(phase, n_workers),
  _thread_roots(phase, n_workers > 1),
  _serial_weak_roots(phase),
  _weak_roots(phase),
  _dedup_roots(phase),
  _code_roots(phase) {
  assert(ShenandoahHeap::heap()->is_full_gc_in_progress(), "Full GC only");
}

void ShenandoahRootAdjuster::roots_do(uint worker_id, OopClosure* oops) {
  CodeBlobToOopClosure code_blob_cl(oops, CodeBlobToOopClosure::FixRelocations);
  ShenandoahCodeBlobAndDisarmClosure blobs_and_disarm_Cl(oops);
  CodeBlobToOopClosure* adjust_code_closure = ShenandoahConcurrentRoots::can_do_concurrent_class_unloading() ?
                                              static_cast<CodeBlobToOopClosure*>(&blobs_and_disarm_Cl) :
                                              static_cast<CodeBlobToOopClosure*>(&code_blob_cl);
  CLDToOopClosure adjust_cld_closure(oops, ClassLoaderData::_claim_strong);
  AlwaysTrueClosure always_true;

  // Process serial-claiming roots first
  _serial_roots.oops_do(oops, worker_id);
  _serial_weak_roots.weak_oops_do(oops, worker_id);

  // Process light-weight/limited parallel roots then
  _vm_roots.oops_do(oops, worker_id);
  _weak_roots.oops_do<OopClosure>(oops, worker_id);
  _dedup_roots.oops_do(&always_true, oops, worker_id);
  _cld_roots.cld_do(&adjust_cld_closure, worker_id);

  // Process heavy-weight/fully parallel roots the last
  _code_roots.code_blobs_do(adjust_code_closure, worker_id);
  _thread_roots.oops_do(oops, NULL, worker_id);
}

ShenandoahHeapIterationRootScanner::ShenandoahHeapIterationRootScanner() :
   ShenandoahRootProcessor(ShenandoahPhaseTimings::heap_iteration_roots),
   _serial_roots(ShenandoahPhaseTimings::heap_iteration_roots),
   _thread_roots(ShenandoahPhaseTimings::heap_iteration_roots, false /*is par*/),
   _vm_roots(ShenandoahPhaseTimings::heap_iteration_roots),
   _cld_roots(ShenandoahPhaseTimings::heap_iteration_roots, 1),
   _serial_weak_roots(ShenandoahPhaseTimings::heap_iteration_roots),
   _weak_roots(ShenandoahPhaseTimings::heap_iteration_roots),
   _dedup_roots(ShenandoahPhaseTimings::heap_iteration_roots),
   _code_roots(ShenandoahPhaseTimings::heap_iteration_roots) {
 }

 void ShenandoahHeapIterationRootScanner::roots_do(OopClosure* oops) {
   assert(Thread::current()->is_VM_thread(), "Only by VM thread");
   // Must use _claim_none to avoid interfering with concurrent CLDG iteration
   CLDToOopClosure clds(oops, ClassLoaderData::_claim_none);
   MarkingCodeBlobClosure code(oops, !CodeBlobToOopClosure::FixRelocations);
   ShenandoahParallelOopsDoThreadClosure tc_cl(oops, &code, NULL);
   AlwaysTrueClosure always_true;

   ResourceMark rm;

   // Process serial-claiming roots first
   _serial_roots.oops_do(oops, 0);
   _serial_weak_roots.weak_oops_do(oops, 0);

   // Process light-weight/limited parallel roots then
   _vm_roots.oops_do(oops, 0);
   _weak_roots.oops_do<OopClosure>(oops, 0);
   _dedup_roots.oops_do(&always_true, oops, 0);
   _cld_roots.cld_do(&clds, 0);

   // Process heavy-weight/fully parallel roots the last
   _code_roots.code_blobs_do(&code, 0);
   _thread_roots.threads_do(&tc_cl, 0);
 }

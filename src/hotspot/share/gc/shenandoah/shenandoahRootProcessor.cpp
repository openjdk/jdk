/*
 * Copyright (c) 2015, 2019, Red Hat, Inc. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahConcurrentRoots.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahStringDedup.hpp"
#include "gc/shenandoah/shenandoahTimingTracker.hpp"
#include "gc/shenandoah/shenandoahVMOperations.hpp"
#include "jfr/jfr.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "runtime/thread.hpp"
#include "services/management.hpp"

ShenandoahSerialRoot::ShenandoahSerialRoot(ShenandoahSerialRoot::OopsDo oops_do, ShenandoahPhaseTimings::GCParPhases phase) :
  _oops_do(oops_do), _phase(phase) {
}

void ShenandoahSerialRoot::oops_do(OopClosure* cl, uint worker_id) {
  if (_claimed.try_set()) {
    ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();
    ShenandoahWorkerTimingsTracker timer(worker_times, _phase, worker_id);
    _oops_do(cl);
  }
}

// Overwrite the second argument for SD::oops_do, don't include vm global oop storage.
static void system_dictionary_oops_do(OopClosure* cl) {
  SystemDictionary::oops_do(cl, false);
}

ShenandoahSerialRoots::ShenandoahSerialRoots() :
  _universe_root(&Universe::oops_do, ShenandoahPhaseTimings::UniverseRoots),
  _object_synchronizer_root(&ObjectSynchronizer::oops_do, ShenandoahPhaseTimings::ObjectSynchronizerRoots),
  _management_root(&Management::oops_do, ShenandoahPhaseTimings::ManagementRoots),
  _system_dictionary_root(&system_dictionary_oops_do, ShenandoahPhaseTimings::SystemDictionaryRoots),
  _jvmti_root(&JvmtiExport::oops_do, ShenandoahPhaseTimings::JVMTIRoots) {
}

void ShenandoahSerialRoots::oops_do(OopClosure* cl, uint worker_id) {
  _universe_root.oops_do(cl, worker_id);
  _object_synchronizer_root.oops_do(cl, worker_id);
  _management_root.oops_do(cl, worker_id);
  _system_dictionary_root.oops_do(cl, worker_id);
  _jvmti_root.oops_do(cl, worker_id);
}

ShenandoahWeakSerialRoot::ShenandoahWeakSerialRoot(ShenandoahWeakSerialRoot::WeakOopsDo weak_oops_do, ShenandoahPhaseTimings::GCParPhases phase) :
  _weak_oops_do(weak_oops_do), _phase(phase) {
}

void ShenandoahWeakSerialRoot::weak_oops_do(BoolObjectClosure* is_alive, OopClosure* keep_alive, uint worker_id) {
  if (_claimed.try_set()) {
    ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();
    ShenandoahWorkerTimingsTracker timer(worker_times, _phase, worker_id);
    _weak_oops_do(is_alive, keep_alive);
  }
}

#if INCLUDE_JVMTI
ShenandoahJVMTIWeakRoot::ShenandoahJVMTIWeakRoot() :
  ShenandoahWeakSerialRoot(&JvmtiExport::weak_oops_do, ShenandoahPhaseTimings::JVMTIWeakRoots) {
}
#endif // INCLUDE_JVMTI

#if INCLUDE_JFR
ShenandoahJFRWeakRoot::ShenandoahJFRWeakRoot() :
  ShenandoahWeakSerialRoot(&Jfr::weak_oops_do, ShenandoahPhaseTimings::JFRWeakRoots) {
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

ShenandoahThreadRoots::ShenandoahThreadRoots(bool is_par) : _is_par(is_par) {
  Threads::change_thread_claim_token();
}

void ShenandoahThreadRoots::oops_do(OopClosure* oops_cl, CodeBlobClosure* code_cl, uint worker_id) {
  ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();
  ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::ThreadRoots, worker_id);
  ResourceMark rm;
  Threads::possibly_parallel_oops_do(_is_par, oops_cl, code_cl);
}

void ShenandoahThreadRoots::threads_do(ThreadClosure* tc, uint worker_id) {
  ShenandoahWorkerTimings* worker_times = ShenandoahHeap::heap()->phase_timings()->worker_times();
  ShenandoahWorkerTimingsTracker timer(worker_times, ShenandoahPhaseTimings::ThreadRoots, worker_id);
  ResourceMark rm;
  Threads::possibly_parallel_threads_do(_is_par, tc);
}

ShenandoahThreadRoots::~ShenandoahThreadRoots() {
  Threads::assert_all_threads_claimed();
}

ShenandoahStringDedupRoots::ShenandoahStringDedupRoots() {
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
    ShenandoahStringDedup::parallel_oops_do(is_alive, keep_alive, worker_id);
  }
}

ShenandoahRootProcessor::ShenandoahRootProcessor(ShenandoahPhaseTimings::Phase phase) :
  _heap(ShenandoahHeap::heap()),
  _phase(phase) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must at safepoint");
  _heap->phase_timings()->record_workers_start(_phase);
}

ShenandoahRootProcessor::~ShenandoahRootProcessor() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must at safepoint");
  _heap->phase_timings()->record_workers_end(_phase);
}

ShenandoahRootEvacuator::ShenandoahRootEvacuator(uint n_workers,
                                                 ShenandoahPhaseTimings::Phase phase,
                                                 bool include_concurrent_roots,
                                                 bool include_concurrent_code_roots) :
  ShenandoahRootProcessor(phase),
  _thread_roots(n_workers > 1),
  _include_concurrent_roots(include_concurrent_roots),
  _include_concurrent_code_roots(include_concurrent_code_roots) {
}

void ShenandoahRootEvacuator::roots_do(uint worker_id, OopClosure* oops) {
  MarkingCodeBlobClosure blobsCl(oops, CodeBlobToOopClosure::FixRelocations);
  ShenandoahCodeBlobAndDisarmClosure blobs_and_disarm_Cl(oops);
  CodeBlobToOopClosure* codes_cl = ShenandoahConcurrentRoots::can_do_concurrent_class_unloading() ?
                                   static_cast<CodeBlobToOopClosure*>(&blobs_and_disarm_Cl) :
                                   static_cast<CodeBlobToOopClosure*>(&blobsCl);
  AlwaysTrueClosure always_true;

  _serial_roots.oops_do(oops, worker_id);
  _serial_weak_roots.weak_oops_do(oops, worker_id);
  if (_include_concurrent_roots) {
    CLDToOopClosure clds(oops, ClassLoaderData::_claim_strong);
    _vm_roots.oops_do<OopClosure>(oops, worker_id);
    _cld_roots.cld_do(&clds, worker_id);
    _weak_roots.oops_do<OopClosure>(oops, worker_id);
  }

  if (_include_concurrent_code_roots) {
    _code_roots.code_blobs_do(codes_cl, worker_id);
    _thread_roots.oops_do(oops, NULL, worker_id);
  } else {
    _thread_roots.oops_do(oops, codes_cl, worker_id);
  }

  _dedup_roots.oops_do(&always_true, oops, worker_id);
}

ShenandoahRootUpdater::ShenandoahRootUpdater(uint n_workers, ShenandoahPhaseTimings::Phase phase) :
  ShenandoahRootProcessor(phase),
  _thread_roots(n_workers > 1) {
}

ShenandoahRootAdjuster::ShenandoahRootAdjuster(uint n_workers, ShenandoahPhaseTimings::Phase phase) :
  ShenandoahRootProcessor(phase),
  _thread_roots(n_workers > 1) {
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

  _serial_roots.oops_do(oops, worker_id);
  _vm_roots.oops_do(oops, worker_id);

  _thread_roots.oops_do(oops, NULL, worker_id);
  _cld_roots.cld_do(&adjust_cld_closure, worker_id);
  _code_roots.code_blobs_do(adjust_code_closure, worker_id);

  _serial_weak_roots.weak_oops_do(oops, worker_id);
  _weak_roots.oops_do<OopClosure>(oops, worker_id);
  _dedup_roots.oops_do(&always_true, oops, worker_id);
}

 ShenandoahHeapIterationRootScanner::ShenandoahHeapIterationRootScanner() :
   ShenandoahRootProcessor(ShenandoahPhaseTimings::_num_phases),
   _thread_roots(false /*is par*/) {
 }

 void ShenandoahHeapIterationRootScanner::roots_do(OopClosure* oops) {
   assert(Thread::current()->is_VM_thread(), "Only by VM thread");
   // Must use _claim_none to avoid interfering with concurrent CLDG iteration
   CLDToOopClosure clds(oops, ClassLoaderData::_claim_none);
   MarkingCodeBlobClosure code(oops, !CodeBlobToOopClosure::FixRelocations);
   ShenandoahParallelOopsDoThreadClosure tc_cl(oops, &code, NULL);
   AlwaysTrueClosure always_true;
   ResourceMark rm;

   _serial_roots.oops_do(oops, 0);
   _vm_roots.oops_do(oops, 0);
   _cld_roots.cld_do(&clds, 0);
   _thread_roots.threads_do(&tc_cl, 0);
   _code_roots.code_blobs_do(&code, 0);

   _serial_weak_roots.weak_oops_do(oops, 0);
   _weak_roots.oops_do<OopClosure>(oops, 0);
   _dedup_roots.oops_do(&always_true, oops, 0);
 }

 void ShenandoahHeapIterationRootScanner::strong_roots_do(OopClosure* oops) {
   assert(Thread::current()->is_VM_thread(), "Only by VM thread");
   // Must use _claim_none to avoid interfering with concurrent CLDG iteration
   CLDToOopClosure clds(oops, ClassLoaderData::_claim_none);
   MarkingCodeBlobClosure code(oops, !CodeBlobToOopClosure::FixRelocations);
   ShenandoahParallelOopsDoThreadClosure tc_cl(oops, &code, NULL);
   ResourceMark rm;

   _serial_roots.oops_do(oops, 0);
   _vm_roots.oops_do(oops, 0);
   _cld_roots.always_strong_cld_do(&clds, 0);
   _thread_roots.threads_do(&tc_cl, 0);
 }

/*
 * Copyright (c) 2015, 2021, Red Hat, Inc. All rights reserved.
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


#include "classfile/classLoaderData.hpp"
#include "code/nmethod.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.inline.hpp"
#include "gc/shenandoah/shenandoahStackWatermark.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "runtime/threads.hpp"

ShenandoahJavaThreadsIterator::ShenandoahJavaThreadsIterator(ShenandoahPhaseTimings::Phase phase, uint n_workers) :
  _threads(),
  _length(_threads.length()),
  _stride(MAX2(1u, _length / n_workers / _chunks_per_worker)),
  _claimed(0),
  _phase(phase) {
}

uint ShenandoahJavaThreadsIterator::claim() {
  return Atomic::fetch_then_add(&_claimed, _stride, memory_order_relaxed);
}

void ShenandoahJavaThreadsIterator::threads_do(ThreadClosure* cl, uint worker_id) {
  ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::ThreadRoots, worker_id);
  for (uint i = claim(); i < _length; i = claim()) {
    for (uint t = i; t < MIN2(_length, i + _stride); t++) {
      cl->do_thread(thread_at(t));
    }
  }
}

ShenandoahThreadRoots::ShenandoahThreadRoots(ShenandoahPhaseTimings::Phase phase, bool is_par) :
  _phase(phase), _is_par(is_par) {
  Threads::change_thread_claim_token();
}

void ShenandoahThreadRoots::oops_do(OopClosure* oops_cl, NMethodClosure* code_cl, uint worker_id) {
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

ShenandoahCodeCacheRoots::ShenandoahCodeCacheRoots(ShenandoahPhaseTimings::Phase phase) : _phase(phase) {
}

void ShenandoahCodeCacheRoots::nmethods_do(NMethodClosure* nmethod_cl, uint worker_id) {
  ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::CodeCacheRoots, worker_id);
  _coderoots_iterator.possibly_parallel_nmethods_do(nmethod_cl);
}

ShenandoahRootProcessor::ShenandoahRootProcessor(ShenandoahPhaseTimings::Phase phase) :
  _heap(ShenandoahHeap::heap()),
  _worker_phase(phase) {
}

ShenandoahSTWRootScanner::ShenandoahSTWRootScanner(ShenandoahPhaseTimings::Phase phase) :
   ShenandoahRootProcessor(phase),
   _thread_roots(phase, ShenandoahHeap::heap()->workers()->active_workers() > 1),
   _code_roots(phase),
   _cld_roots(phase, ShenandoahHeap::heap()->workers()->active_workers(), false /*heap iteration*/),
   _vm_roots(phase),
   _unload_classes(ShenandoahHeap::heap()->unload_classes()) {
}

class ShenandoahConcurrentMarkThreadClosure : public ThreadClosure {
private:
  OopClosure* const _oops;

public:
  ShenandoahConcurrentMarkThreadClosure(OopClosure* oops);
  void do_thread(Thread* thread);
};

ShenandoahConcurrentMarkThreadClosure::ShenandoahConcurrentMarkThreadClosure(OopClosure* oops) :
  _oops(oops) {
}

void ShenandoahConcurrentMarkThreadClosure::do_thread(Thread* thread) {
  assert(thread->is_Java_thread(), "Must be");
  JavaThread* const jt = JavaThread::cast(thread);

  StackWatermarkSet::finish_processing(jt, _oops, StackWatermarkKind::gc);
}

ShenandoahConcurrentRootScanner::ShenandoahConcurrentRootScanner(uint n_workers,
                                                                 ShenandoahPhaseTimings::Phase phase) :
   ShenandoahRootProcessor(phase),
   _java_threads(phase, n_workers),
  _vm_roots(phase),
  _cld_roots(phase, n_workers, false /*heap iteration*/),
  _codecache_snapshot(nullptr),
  _phase(phase) {
  if (!ShenandoahHeap::heap()->unload_classes()) {
    MutexLocker locker(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    _codecache_snapshot = ShenandoahCodeRoots::table()->snapshot_for_iteration();
  }
  update_tlab_stats();
  assert(!ShenandoahHeap::heap()->has_forwarded_objects(), "Not expecting forwarded pointers during concurrent marking");
}

ShenandoahConcurrentRootScanner::~ShenandoahConcurrentRootScanner() {
  if (!ShenandoahHeap::heap()->unload_classes()) {
    MonitorLocker locker(CodeCache_lock, Mutex::_no_safepoint_check_flag);
    ShenandoahCodeRoots::table()->finish_iteration(_codecache_snapshot);
    locker.notify_all();
  }
}

void ShenandoahConcurrentRootScanner::roots_do(OopClosure* oops, uint worker_id) {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  CLDToOopClosure clds_cl(oops, ClassLoaderData::_claim_strong);

  // Process light-weight/limited parallel roots then
  _vm_roots.oops_do(oops, worker_id);

  if (heap->unload_classes()) {
    _cld_roots.always_strong_cld_do(&clds_cl, worker_id);
  } else {
    _cld_roots.cld_do(&clds_cl, worker_id);

    {
      ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::CodeCacheRoots, worker_id);
      NMethodToOopClosure nmethods(oops, !NMethodToOopClosure::FixRelocations);
      _codecache_snapshot->parallel_nmethods_do(&nmethods);
    }
  }

  // Process heavy-weight/fully parallel roots the last
  ShenandoahConcurrentMarkThreadClosure thr_cl(oops);
  _java_threads.threads_do(&thr_cl, worker_id);
}

void ShenandoahConcurrentRootScanner::update_tlab_stats() {
  if (UseTLAB) {
    ThreadLocalAllocStats total;
    for (uint i = 0; i < _java_threads.length(); i ++) {
      Thread* thr = _java_threads.thread_at(i);
      if (thr->is_Java_thread()) {
        ShenandoahStackWatermark* wm = StackWatermarkSet::get<ShenandoahStackWatermark>(JavaThread::cast(thr), StackWatermarkKind::gc);
        total.update(wm->stats());
      }
    }
    total.publish();
  }
}

ShenandoahRootUpdater::ShenandoahRootUpdater(uint n_workers, ShenandoahPhaseTimings::Phase phase) :
  ShenandoahRootProcessor(phase),
  _vm_roots(phase),
  _cld_roots(phase, n_workers, false /*heap iteration*/),
  _thread_roots(phase, n_workers > 1),
  _weak_roots(phase),
  _code_roots(phase) {
}

ShenandoahRootAdjuster::ShenandoahRootAdjuster(uint n_workers, ShenandoahPhaseTimings::Phase phase) :
  ShenandoahRootProcessor(phase),
  _vm_roots(phase),
  _cld_roots(phase, n_workers, false /*heap iteration*/),
  _thread_roots(phase, n_workers > 1),
  _weak_roots(phase),
  _code_roots(phase) {
  assert(ShenandoahHeap::heap()->is_full_gc_in_progress(), "Full GC only");
}

void ShenandoahRootAdjuster::roots_do(uint worker_id, OopClosure* oops) {
  NMethodToOopClosure code_blob_cl(oops, NMethodToOopClosure::FixRelocations);
  ShenandoahNMethodAndDisarmClosure nmethods_and_disarm_Cl(oops);
  NMethodToOopClosure* adjust_code_closure = ShenandoahCodeRoots::use_nmethod_barriers_for_mark() ?
                                             static_cast<NMethodToOopClosure*>(&nmethods_and_disarm_Cl) :
                                             static_cast<NMethodToOopClosure*>(&code_blob_cl);
  CLDToOopClosure adjust_cld_closure(oops, ClassLoaderData::_claim_strong);

  // Process light-weight/limited parallel roots then
  _vm_roots.oops_do(oops, worker_id);
  _weak_roots.oops_do<OopClosure>(oops, worker_id);
  _cld_roots.cld_do(&adjust_cld_closure, worker_id);

  // Process heavy-weight/fully parallel roots the last
  _code_roots.nmethods_do(adjust_code_closure, worker_id);
  _thread_roots.oops_do(oops, nullptr, worker_id);
}

ShenandoahHeapIterationRootScanner::ShenandoahHeapIterationRootScanner(uint n_workers) :
  ShenandoahRootProcessor(ShenandoahPhaseTimings::heap_iteration_roots),
  _thread_roots(ShenandoahPhaseTimings::heap_iteration_roots, false /*is par*/),
  _vm_roots(ShenandoahPhaseTimings::heap_iteration_roots),
  _cld_roots(ShenandoahPhaseTimings::heap_iteration_roots, n_workers, true /*heap iteration*/),
  _weak_roots(ShenandoahPhaseTimings::heap_iteration_roots),
  _code_roots(ShenandoahPhaseTimings::heap_iteration_roots) {
}

class ShenandoahMarkNMethodClosure : public NMethodClosure {
private:
  OopClosure* const _oops;
  BarrierSetNMethod* const _bs_nm;

public:
  ShenandoahMarkNMethodClosure(OopClosure* oops) :
    _oops(oops),
    _bs_nm(BarrierSet::barrier_set()->barrier_set_nmethod()) {}

  virtual void do_nmethod(nmethod* nm) {
    assert(nm != nullptr, "Sanity");
    if (_bs_nm != nullptr) {
      // Make sure it only sees to-space objects
      _bs_nm->nmethod_entry_barrier(nm);
    }
    ShenandoahNMethod* const snm = ShenandoahNMethod::gc_data(nm);
    assert(snm != nullptr, "Sanity");
    snm->oops_do(_oops, false /*fix_relocations*/);
  }
};

void ShenandoahHeapIterationRootScanner::roots_do(OopClosure* oops) {
  // Must use _claim_other to avoid interfering with concurrent CLDG iteration
  CLDToOopClosure clds(oops, ClassLoaderData::_claim_other);
  ShenandoahMarkNMethodClosure code(oops);
  ShenandoahParallelOopsDoThreadClosure tc_cl(oops, &code, nullptr);

  ResourceMark rm;

  // Process light-weight/limited parallel roots then
  _vm_roots.oops_do(oops, 0);
  _weak_roots.oops_do<OopClosure>(oops, 0);
  _cld_roots.cld_do(&clds, 0);

  // Process heavy-weight/fully parallel roots the last
  _code_roots.nmethods_do(&code, 0);
  _thread_roots.threads_do(&tc_cl, 0);
}

/*
 * Copyright (c) 2019, 2020, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_INLINE_HPP

#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/oopStorageParState.inline.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahConcurrentRoots.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "gc/shenandoah/shenandoahRootProcessor.hpp"
#include "gc/shenandoah/shenandoahUtils.hpp"
#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "memory/resourceArea.hpp"
#include "prims/resolvedMethodTable.hpp"
#include "runtime/safepoint.hpp"

template <bool CONCURRENT>
inline ShenandoahVMRoot<CONCURRENT>::ShenandoahVMRoot(OopStorage* storage,
        ShenandoahPhaseTimings::Phase phase, ShenandoahPhaseTimings::ParPhase par_phase) :
  _itr(storage), _phase(phase), _par_phase(par_phase) {
}

template <bool CONCURRENT>
template <typename Closure>
inline void ShenandoahVMRoot<CONCURRENT>::oops_do(Closure* cl, uint worker_id) {
  ShenandoahWorkerTimingsTracker timer(_phase, _par_phase, worker_id);
  _itr.oops_do(cl);
}

template <bool CONCURRENT>
inline ShenandoahWeakRoot<CONCURRENT>::ShenandoahWeakRoot(OopStorage* storage,
  ShenandoahPhaseTimings::Phase phase, ShenandoahPhaseTimings::ParPhase par_phase) :
  ShenandoahVMRoot<CONCURRENT>(storage, phase, par_phase) {
}

inline ShenandoahWeakRoot<false>::ShenandoahWeakRoot(OopStorage* storage,
  ShenandoahPhaseTimings::Phase phase,  ShenandoahPhaseTimings::ParPhase par_phase) :
  _itr(storage), _phase(phase), _par_phase(par_phase) {
}

template <typename IsAliveClosure, typename KeepAliveClosure>
void ShenandoahWeakRoot<false /* concurrent */>::weak_oops_do(IsAliveClosure* is_alive, KeepAliveClosure* keep_alive, uint worker_id) {
  ShenandoahWorkerTimingsTracker timer(_phase, _par_phase, worker_id);
  _itr.weak_oops_do(is_alive, keep_alive);
}

template <bool CONCURRENT>
ShenandoahWeakRoots<CONCURRENT>::ShenandoahWeakRoots() :
  _jni_roots(OopStorageSet::jni_weak(), ShenandoahPhaseTimings::JNIWeakRoots),
  _string_table_roots(OopStorageSet::string_table_weak(), ShenandoahPhaseTimings::StringTableRoots),
  _resolved_method_table_roots(OopStorageSet::resolved_method_table_weak(), ShenandoahPhaseTimings::ResolvedMethodTableRoots),
  _vm_roots(OopStorageSet::vm_weak(), ShenandoahPhaseTimings::VMWeakRoots) {
}

template <bool CONCURRENT>
template <typename Closure>
void ShenandoahWeakRoots<CONCURRENT>::oops_do(Closure* cl, uint worker_id) {
  _jni_roots.oops_do(cl, worker_id);
  _string_table_roots.oops_do(cl, worker_id);
  _resolved_method_table_roots.oops_do(cl, worker_id);
  _vm_roots.oops_do(cl, worker_id);
}

inline ShenandoahWeakRoots<false /* concurrent */>::ShenandoahWeakRoots(ShenandoahPhaseTimings::Phase phase) :
  _jni_roots(OopStorageSet::jni_weak(), phase, ShenandoahPhaseTimings::JNIWeakRoots),
  _string_table_roots(OopStorageSet::string_table_weak(), phase, ShenandoahPhaseTimings::StringTableRoots),
  _resolved_method_table_roots(OopStorageSet::resolved_method_table_weak(), phase, ShenandoahPhaseTimings::ResolvedMethodTableRoots),
  _vm_roots(OopStorageSet::vm_weak(), phase, ShenandoahPhaseTimings::VMWeakRoots) {
}

template <typename IsAliveClosure, typename KeepAliveClosure>
void ShenandoahWeakRoots<false /* concurrent*/>::weak_oops_do(IsAliveClosure* is_alive, KeepAliveClosure* keep_alive, uint worker_id) {
  _jni_roots.weak_oops_do(is_alive, keep_alive, worker_id);
  _string_table_roots.weak_oops_do(is_alive, keep_alive, worker_id);
  _resolved_method_table_roots.weak_oops_do(is_alive, keep_alive, worker_id);
  _vm_roots.weak_oops_do(is_alive, keep_alive, worker_id);
}

template <typename Closure>
void ShenandoahWeakRoots<false /* concurrent */>::oops_do(Closure* cl, uint worker_id) {
  AlwaysTrueClosure always_true;
  weak_oops_do<AlwaysTrueClosure, Closure>(&always_true, cl, worker_id);
}

template <bool CONCURRENT>
ShenandoahVMRoots<CONCURRENT>::ShenandoahVMRoots(ShenandoahPhaseTimings::Phase phase) :
  _jni_handle_roots(OopStorageSet::jni_global(), phase, ShenandoahPhaseTimings::JNIRoots),
  _vm_global_roots(OopStorageSet::vm_global(), phase, ShenandoahPhaseTimings::VMGlobalRoots) {
}

template <bool CONCURRENT>
template <typename T>
void ShenandoahVMRoots<CONCURRENT>::oops_do(T* cl, uint worker_id) {
  _jni_handle_roots.oops_do(cl, worker_id);
  _vm_global_roots.oops_do(cl, worker_id);
}

template <bool CONCURRENT, bool SINGLE_THREADED>
ShenandoahClassLoaderDataRoots<CONCURRENT, SINGLE_THREADED>::ShenandoahClassLoaderDataRoots(ShenandoahPhaseTimings::Phase phase, uint n_workers) :
  _semaphore(worker_count(n_workers)),
  _phase(phase) {
  if (!SINGLE_THREADED) {
    ClassLoaderDataGraph::clear_claimed_marks();
  }
  if (CONCURRENT) {
    ClassLoaderDataGraph_lock->lock();
  }
}

template <bool CONCURRENT, bool SINGLE_THREADED>
ShenandoahClassLoaderDataRoots<CONCURRENT, SINGLE_THREADED>::~ShenandoahClassLoaderDataRoots() {
  if (CONCURRENT) {
    ClassLoaderDataGraph_lock->unlock();
  }
}


template <bool CONCURRENT, bool SINGLE_THREADED>
void ShenandoahClassLoaderDataRoots<CONCURRENT, SINGLE_THREADED>::always_strong_cld_do(CLDClosure* clds, uint worker_id) {
  if (SINGLE_THREADED) {
    assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
    assert(Thread::current()->is_VM_thread(), "Single threaded CLDG iteration can only be done by VM thread");
    ClassLoaderDataGraph::always_strong_cld_do(clds);
  } else if (_semaphore.try_acquire()) {
    ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::CLDGRoots, worker_id);
    ClassLoaderDataGraph::always_strong_cld_do(clds);
    _semaphore.claim_all();
  }
}

template <bool CONCURRENT, bool SINGLE_THREADED>
void ShenandoahClassLoaderDataRoots<CONCURRENT, SINGLE_THREADED>::cld_do(CLDClosure* clds, uint worker_id) {
  if (SINGLE_THREADED) {
    assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
    assert(Thread::current()->is_VM_thread(), "Single threaded CLDG iteration can only be done by VM thread");
    ClassLoaderDataGraph::cld_do(clds);
  } else if (_semaphore.try_acquire()) {
    ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::CLDGRoots, worker_id);
    ClassLoaderDataGraph::cld_do(clds);
    _semaphore.claim_all();
  }
}

class ShenandoahParallelOopsDoThreadClosure : public ThreadClosure {
private:
  OopClosure* _f;
  CodeBlobClosure* _cf;
  ThreadClosure* _thread_cl;
public:
  ShenandoahParallelOopsDoThreadClosure(OopClosure* f, CodeBlobClosure* cf, ThreadClosure* thread_cl) :
    _f(f), _cf(cf), _thread_cl(thread_cl) {}

  void do_thread(Thread* t) {
    if (_thread_cl != NULL) {
      _thread_cl->do_thread(t);
    }
    t->oops_do(_f, _cf);
  }
};

template <bool CONCURRENT>
ShenandoahConcurrentRootScanner<CONCURRENT>::ShenandoahConcurrentRootScanner(uint n_workers,
                                                                             ShenandoahPhaseTimings::Phase phase) :
  _vm_roots(phase),
  _cld_roots(phase, n_workers),
  _codecache_snapshot(NULL),
  _phase(phase) {
  if (!ShenandoahHeap::heap()->unload_classes()) {
    if (CONCURRENT) {
      CodeCache_lock->lock_without_safepoint_check();
    } else {
      assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
    }
    _codecache_snapshot = ShenandoahCodeRoots::table()->snapshot_for_iteration();
  }
  assert(!CONCURRENT || !ShenandoahHeap::heap()->has_forwarded_objects(), "Not expecting forwarded pointers during concurrent marking");
}

template <bool CONCURRENT>
ShenandoahConcurrentRootScanner<CONCURRENT>::~ShenandoahConcurrentRootScanner() {
  if (!ShenandoahHeap::heap()->unload_classes()) {
    ShenandoahCodeRoots::table()->finish_iteration(_codecache_snapshot);
    if (CONCURRENT) {
      CodeCache_lock->unlock();
    }
  }
}

template <bool CONCURRENT>
void ShenandoahConcurrentRootScanner<CONCURRENT>::oops_do(OopClosure* oops, uint worker_id) {
  ShenandoahHeap* const heap = ShenandoahHeap::heap();
  CLDToOopClosure clds_cl(oops, CONCURRENT ? ClassLoaderData::_claim_strong : ClassLoaderData::_claim_none);
  _vm_roots.oops_do(oops, worker_id);

  if (!heap->unload_classes()) {
    _cld_roots.cld_do(&clds_cl, worker_id);

    ShenandoahWorkerTimingsTracker timer(_phase, ShenandoahPhaseTimings::CodeCacheRoots, worker_id);
    CodeBlobToOopClosure blobs(oops, !CodeBlobToOopClosure::FixRelocations);
    _codecache_snapshot->parallel_blobs_do(&blobs);
  } else {
    _cld_roots.always_strong_cld_do(&clds_cl, worker_id);
  }
}

template <typename IsAlive, typename KeepAlive>
void ShenandoahRootUpdater::roots_do(uint worker_id, IsAlive* is_alive, KeepAlive* keep_alive) {
  CodeBlobToOopClosure update_blobs(keep_alive, CodeBlobToOopClosure::FixRelocations);
  ShenandoahCodeBlobAndDisarmClosure blobs_and_disarm_Cl(keep_alive);
  CodeBlobToOopClosure* codes_cl = ShenandoahConcurrentRoots::can_do_concurrent_class_unloading() ?
                                  static_cast<CodeBlobToOopClosure*>(&blobs_and_disarm_Cl) :
                                  static_cast<CodeBlobToOopClosure*>(&update_blobs);

  CLDToOopClosure clds(keep_alive, ClassLoaderData::_claim_strong);

  // Process serial-claiming roots first
  _serial_roots.oops_do(keep_alive, worker_id);
  _serial_weak_roots.weak_oops_do(is_alive, keep_alive, worker_id);

  // Process light-weight/limited parallel roots then
  _vm_roots.oops_do(keep_alive, worker_id);
  _weak_roots.weak_oops_do(is_alive, keep_alive, worker_id);
  _dedup_roots.oops_do(is_alive, keep_alive, worker_id);
  _cld_roots.cld_do(&clds, worker_id);

  // Process heavy-weight/fully parallel roots the last
  _code_roots.code_blobs_do(codes_cl, worker_id);
  _thread_roots.oops_do(keep_alive, NULL, worker_id);
}

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHROOTPROCESSOR_INLINE_HPP

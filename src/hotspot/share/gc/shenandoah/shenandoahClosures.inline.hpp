/*
 * Copyright (c) 2019, 2020, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCLOSURES_INLINE_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCLOSURES_INLINE_HPP

#include "gc/shenandoah/shenandoahClosures.hpp"

#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahEvacOOMHandler.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMark.inline.hpp"
#include "gc/shenandoah/shenandoahMarkingContext.inline.hpp"
#include "gc/shenandoah/shenandoahNMethod.inline.hpp"
#include "gc/shenandoah/shenandoahReferenceProcessor.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.inline.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/javaThread.hpp"

//
// ========= Super
//

ShenandoahSuperClosure::ShenandoahSuperClosure() :
  MetadataVisitingOopIterateClosure(), _heap(ShenandoahHeap::heap()) {}

ShenandoahSuperClosure::ShenandoahSuperClosure(ShenandoahReferenceProcessor* rp) :
  MetadataVisitingOopIterateClosure(rp), _heap(ShenandoahHeap::heap()) {}

void ShenandoahSuperClosure::do_nmethod(nmethod* nm) {
  nm->run_nmethod_entry_barrier();
}

//
// ========= Marking
//
ShenandoahFlushSATBHandshakeClosure::ShenandoahFlushSATBHandshakeClosure(SATBMarkQueueSet& qset) :
  HandshakeClosure("Shenandoah Flush SATB"),
  _qset(qset) {}

void ShenandoahFlushSATBHandshakeClosure::do_thread(Thread* thread) {
  _qset.flush_queue(ShenandoahThreadLocalData::satb_mark_queue(thread));
}

ShenandoahMarkRefsSuperClosure::ShenandoahMarkRefsSuperClosure(ShenandoahObjToScanQueue* q,
                                                               ShenandoahReferenceProcessor* rp,
                                                               ShenandoahObjToScanQueue* old_q) :
        ShenandoahSuperClosure(rp),
        _queue(q),
        _old_queue(old_q),
        _mark_context(ShenandoahHeap::heap()->marking_context()),
        _weak(false) {}

template<class T, ShenandoahGenerationType GENERATION>
inline void ShenandoahMarkRefsSuperClosure::work(T* p) {
  ShenandoahMark::mark_through_ref<T, GENERATION>(p, _queue, _old_queue, _mark_context, _weak);
}

ShenandoahForwardedIsAliveClosure::ShenandoahForwardedIsAliveClosure() :
  _mark_context(ShenandoahHeap::heap()->marking_context()) {}

bool ShenandoahForwardedIsAliveClosure::do_object_b(oop obj) {
  if (CompressedOops::is_null(obj)) {
    return false;
  }
  obj = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
  shenandoah_assert_not_forwarded_if(nullptr, obj, ShenandoahHeap::heap()->is_concurrent_mark_in_progress());
  return _mark_context->is_marked_or_old(obj);
}

ShenandoahIsAliveClosure::ShenandoahIsAliveClosure() :
  _mark_context(ShenandoahHeap::heap()->marking_context()) {}

bool ShenandoahIsAliveClosure::do_object_b(oop obj) {
  if (CompressedOops::is_null(obj)) {
    return false;
  }
  shenandoah_assert_not_forwarded(nullptr, obj);
  return _mark_context->is_marked_or_old(obj);
}

BoolObjectClosure* ShenandoahIsAliveSelector::is_alive_closure() {
  return ShenandoahHeap::heap()->has_forwarded_objects() ?
         reinterpret_cast<BoolObjectClosure*>(&_fwd_alive_cl) :
         reinterpret_cast<BoolObjectClosure*>(&_alive_cl);
}

ShenandoahKeepAliveClosure::ShenandoahKeepAliveClosure() :
  _bs(ShenandoahBarrierSet::barrier_set()) {}

template <typename T>
void ShenandoahKeepAliveClosure::do_oop_work(T* p) {
  assert(ShenandoahHeap::heap()->is_concurrent_mark_in_progress(), "Only for concurrent marking phase");
  assert(ShenandoahHeap::heap()->is_concurrent_old_mark_in_progress() || !ShenandoahHeap::heap()->has_forwarded_objects(), "Not expected");

  T o = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(o)) {
    oop obj = CompressedOops::decode_not_null(o);
    _bs->enqueue(obj);
  }
}


//
// ========= Evacuating + Roots
//

template <bool CONCURRENT, bool STABLE_THREAD>
void ShenandoahEvacuateUpdateRootClosureBase<CONCURRENT, STABLE_THREAD>::do_oop(oop* p) {
  if (CONCURRENT) {
    ShenandoahEvacOOMScope scope;
    do_oop_work(p);
  } else {
    do_oop_work(p);
  }
}

template <bool CONCURRENT, bool STABLE_THREAD>
void ShenandoahEvacuateUpdateRootClosureBase<CONCURRENT, STABLE_THREAD>::do_oop(narrowOop* p) {
  if (CONCURRENT) {
    ShenandoahEvacOOMScope scope;
    do_oop_work(p);
  } else {
    do_oop_work(p);
  }
}

template <bool CONCURRENT, bool STABLE_THREAD>
template <class T>
void ShenandoahEvacuateUpdateRootClosureBase<CONCURRENT, STABLE_THREAD>::do_oop_work(T* p) {
  assert(_heap->is_concurrent_weak_root_in_progress() ||
         _heap->is_concurrent_strong_root_in_progress(),
         "Only do this in root processing phase");

  T o = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(o)) {
    oop obj = CompressedOops::decode_not_null(o);
    if (_heap->in_collection_set(obj)) {
      assert(_heap->is_evacuation_in_progress(), "Only do this when evacuation is in progress");
      shenandoah_assert_marked(p, obj);
      oop resolved = ShenandoahBarrierSet::resolve_forwarded_not_null(obj);
      if (resolved == obj) {
        Thread* thr = STABLE_THREAD ? _thread : Thread::current();
        assert(thr == Thread::current(), "Wrong thread");

        resolved = _heap->evacuate_object(obj, thr);
      }
      if (CONCURRENT) {
        ShenandoahHeap::atomic_update_oop(resolved, p, o);
      } else {
        RawAccess<IS_NOT_NULL | MO_UNORDERED>::oop_store(p, resolved);
      }
    }
  }
}

template <bool CONCURRENT, typename IsAlive, typename KeepAlive>
ShenandoahCleanUpdateWeakOopsClosure<CONCURRENT, IsAlive, KeepAlive>::ShenandoahCleanUpdateWeakOopsClosure(IsAlive* is_alive, KeepAlive* keep_alive) :
  _is_alive(is_alive), _keep_alive(keep_alive) {
  if (!CONCURRENT) {
    assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  }
}

template <bool CONCURRENT, typename IsAlive, typename KeepAlive>
void ShenandoahCleanUpdateWeakOopsClosure<CONCURRENT, IsAlive, KeepAlive>::do_oop(oop* p) {
  oop obj = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(obj)) {
    if (_is_alive->do_object_b(obj)) {
      _keep_alive->do_oop(p);
    } else {
      if (CONCURRENT) {
        ShenandoahHeap::atomic_clear_oop(p, obj);
      } else {
        RawAccess<IS_NOT_NULL>::oop_store(p, oop());
      }
    }
  }
}

template <bool CONCURRENT, typename IsAlive, typename KeepAlive>
void ShenandoahCleanUpdateWeakOopsClosure<CONCURRENT, IsAlive, KeepAlive>::do_oop(narrowOop* p) {
  ShouldNotReachHere();
}

ShenandoahNMethodAndDisarmClosure::ShenandoahNMethodAndDisarmClosure(OopClosure* cl) :
  NMethodToOopClosure(cl, true /* fix_relocations */),
   _bs(BarrierSet::barrier_set()->barrier_set_nmethod()) {
}

void ShenandoahNMethodAndDisarmClosure::do_nmethod(nmethod* nm) {
  assert(nm != nullptr, "Sanity");
  assert(!ShenandoahNMethod::gc_data(nm)->is_unregistered(), "Should not be here");
  NMethodToOopClosure::do_nmethod(nm);
  _bs->disarm(nm);
}


//
// ========= Update References
//

template <ShenandoahGenerationType GENERATION>
ShenandoahMarkUpdateRefsClosure<GENERATION>::ShenandoahMarkUpdateRefsClosure(ShenandoahObjToScanQueue* q,
                                                                             ShenandoahReferenceProcessor* rp,
                                                                             ShenandoahObjToScanQueue* old_q) :
  ShenandoahMarkRefsSuperClosure(q, rp, old_q) {
  assert(_heap->is_stw_gc_in_progress(), "Can only be used for STW GC");
}

template<ShenandoahGenerationType GENERATION>
template<class T>
inline void ShenandoahMarkUpdateRefsClosure<GENERATION>::work(T* p) {
  // Update the location
  _heap->non_conc_update_with_forwarded(p);

  // ...then do the usual thing
  ShenandoahMarkRefsSuperClosure::work<T, GENERATION>(p);
}

template<class T>
inline void ShenandoahNonConcUpdateRefsClosure::work(T* p) {
  _heap->non_conc_update_with_forwarded(p);
}

template<class T>
inline void ShenandoahConcUpdateRefsClosure::work(T* p) {
  _heap->conc_update_with_forwarded(p);
}


//
// ========= Utilities
//

#ifdef ASSERT
template <class T>
void ShenandoahAssertNotForwardedClosure::do_oop_work(T* p) {
  T o = RawAccess<>::oop_load(p);
  if (!CompressedOops::is_null(o)) {
    oop obj = CompressedOops::decode_not_null(o);
    shenandoah_assert_not_forwarded(p, obj);
  }
}

void ShenandoahAssertNotForwardedClosure::do_oop(narrowOop* p) { do_oop_work(p); }
void ShenandoahAssertNotForwardedClosure::do_oop(oop* p)       { do_oop_work(p); }
#endif

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCLOSURES_INLINE_HPP

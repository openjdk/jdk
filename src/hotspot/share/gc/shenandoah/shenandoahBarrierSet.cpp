/*
 * Copyright (c) 2013, 2019, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahAsserts.hpp"
#include "gc/shenandoah/shenandoahBarrierSet.hpp"
#include "gc/shenandoah/shenandoahBarrierSetClone.inline.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahBarrierSetNMethod.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahConcurrentRoots.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahHeuristics.hpp"
#include "gc/shenandoah/shenandoahTraversalGC.hpp"
#include "memory/iterator.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#ifdef COMPILER1
#include "gc/shenandoah/c1/shenandoahBarrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#endif

class ShenandoahBarrierSetC1;
class ShenandoahBarrierSetC2;

static BarrierSetNMethod* make_barrier_set_nmethod(ShenandoahHeap* heap) {
  // NMethod barriers are only used when concurrent nmethod unloading is enabled
  if (!ShenandoahConcurrentRoots::can_do_concurrent_class_unloading()) {
    return NULL;
  }
  return new ShenandoahBarrierSetNMethod(heap);
}

ShenandoahBarrierSet::ShenandoahBarrierSet(ShenandoahHeap* heap) :
  BarrierSet(make_barrier_set_assembler<ShenandoahBarrierSetAssembler>(),
             make_barrier_set_c1<ShenandoahBarrierSetC1>(),
             make_barrier_set_c2<ShenandoahBarrierSetC2>(),
             make_barrier_set_nmethod(heap),
             BarrierSet::FakeRtti(BarrierSet::ShenandoahBarrierSet)),
  _heap(heap),
  _satb_mark_queue_buffer_allocator("SATB Buffer Allocator", ShenandoahSATBBufferSize),
  _satb_mark_queue_set(&_satb_mark_queue_buffer_allocator)
{
}

ShenandoahBarrierSetAssembler* ShenandoahBarrierSet::assembler() {
  BarrierSetAssembler* const bsa = BarrierSet::barrier_set()->barrier_set_assembler();
  return reinterpret_cast<ShenandoahBarrierSetAssembler*>(bsa);
}

void ShenandoahBarrierSet::print_on(outputStream* st) const {
  st->print("ShenandoahBarrierSet");
}

bool ShenandoahBarrierSet::is_a(BarrierSet::Name bsn) {
  return bsn == BarrierSet::ShenandoahBarrierSet;
}

bool ShenandoahBarrierSet::is_aligned(HeapWord* hw) {
  return true;
}

bool ShenandoahBarrierSet::need_load_reference_barrier(DecoratorSet decorators, BasicType type) {
  if (!ShenandoahLoadRefBarrier) return false;
  // Only needed for references
  return is_reference_type(type);
}

bool ShenandoahBarrierSet::use_load_reference_barrier_native(DecoratorSet decorators, BasicType type) {
  assert(need_load_reference_barrier(decorators, type), "Should be subset of LRB");
  assert(is_reference_type(type), "Why we here?");
  // Native load reference barrier is only needed for concurrent root processing
  if (!ShenandoahConcurrentRoots::can_do_concurrent_roots()) {
    return false;
  }

  return (decorators & IN_NATIVE) != 0;
}

bool ShenandoahBarrierSet::need_keep_alive_barrier(DecoratorSet decorators,BasicType type) {
  if (!ShenandoahKeepAliveBarrier) return false;
  // Only needed for references
  if (!is_reference_type(type)) return false;

  bool keep_alive = (decorators & AS_NO_KEEPALIVE) == 0;
  bool unknown = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool is_traversal_mode = ShenandoahHeap::heap()->is_traversal_mode();
  bool on_weak_ref = (decorators & (ON_WEAK_OOP_REF | ON_PHANTOM_OOP_REF)) != 0;
  return (on_weak_ref || unknown) && (keep_alive || is_traversal_mode);
}

oop ShenandoahBarrierSet::load_reference_barrier_not_null(oop obj) {
  if (ShenandoahLoadRefBarrier && _heap->has_forwarded_objects()) {
    return load_reference_barrier_impl(obj);
  } else {
    return obj;
  }
}

oop ShenandoahBarrierSet::load_reference_barrier(oop obj) {
  if (obj != NULL) {
    return load_reference_barrier_not_null(obj);
  } else {
    return obj;
  }
}

oop ShenandoahBarrierSet::load_reference_barrier_mutator(oop obj, oop* load_addr) {
  return load_reference_barrier_mutator_work(obj, load_addr);
}

oop ShenandoahBarrierSet::load_reference_barrier_mutator(oop obj, narrowOop* load_addr) {
  return load_reference_barrier_mutator_work(obj, load_addr);
}

template <class T>
oop ShenandoahBarrierSet::load_reference_barrier_mutator_work(oop obj, T* load_addr) {
  assert(ShenandoahLoadRefBarrier, "should be enabled");
  shenandoah_assert_in_cset(load_addr, obj);

  oop fwd = resolve_forwarded_not_null(obj);
  if (obj == fwd) {
    assert(_heap->is_gc_in_progress_mask(ShenandoahHeap::EVACUATION | ShenandoahHeap::TRAVERSAL),
           "evac should be in progress");

    ShenandoahEvacOOMScope oom_evac_scope;

    Thread* thread = Thread::current();
    oop res_oop = _heap->evacuate_object(obj, thread);

    // Since we are already here and paid the price of getting through runtime call adapters
    // and acquiring oom-scope, it makes sense to try and evacuate more adjacent objects,
    // thus amortizing the overhead. For sparsely live heaps, scan costs easily dominate
    // total assist costs, and can introduce a lot of evacuation latency. This is why we
    // only scan for _nearest_ N objects, regardless if they are eligible for evac or not.
    // The scan itself should also avoid touching the non-marked objects below TAMS, because
    // their metadata (notably, klasses) may be incorrect already.

    size_t max = ShenandoahEvacAssist;
    if (max > 0) {
      // Traversal is special: it uses incomplete marking context, because it coalesces evac with mark.
      // Other code uses complete marking context, because evac happens after the mark.
      ShenandoahMarkingContext* ctx = _heap->is_concurrent_traversal_in_progress() ?
                                      _heap->marking_context() : _heap->complete_marking_context();

      ShenandoahHeapRegion* r = _heap->heap_region_containing(obj);
      assert(r->is_cset(), "sanity");

      HeapWord* cur = (HeapWord*)obj + obj->size();

      size_t count = 0;
      while ((cur < r->top()) && ctx->is_marked(oop(cur)) && (count++ < max)) {
        oop cur_oop = oop(cur);
        if (cur_oop == resolve_forwarded_not_null(cur_oop)) {
          _heap->evacuate_object(cur_oop, thread);
        }
        cur = cur + cur_oop->size();
      }
    }

    fwd = res_oop;
  }

  if (load_addr != NULL && fwd != obj) {
    // Since we are here and we know the load address, update the reference.
    ShenandoahHeap::cas_oop(fwd, load_addr, obj);
  }

  return fwd;
}

oop ShenandoahBarrierSet::load_reference_barrier_impl(oop obj) {
  assert(ShenandoahLoadRefBarrier, "should be enabled");
  if (!CompressedOops::is_null(obj)) {
    bool evac_in_progress = _heap->is_gc_in_progress_mask(ShenandoahHeap::EVACUATION | ShenandoahHeap::TRAVERSAL);
    oop fwd = resolve_forwarded_not_null(obj);
    if (evac_in_progress &&
        _heap->in_collection_set(obj) &&
        obj == fwd) {
      Thread *t = Thread::current();
      ShenandoahEvacOOMScope oom_evac_scope;
      return _heap->evacuate_object(obj, t);
    } else {
      return fwd;
    }
  } else {
    return obj;
  }
}

void ShenandoahBarrierSet::on_thread_create(Thread* thread) {
  // Create thread local data
  ShenandoahThreadLocalData::create(thread);
}

void ShenandoahBarrierSet::on_thread_destroy(Thread* thread) {
  // Destroy thread local data
  ShenandoahThreadLocalData::destroy(thread);
}

void ShenandoahBarrierSet::on_thread_attach(Thread *thread) {
  assert(!thread->is_Java_thread() || !SafepointSynchronize::is_at_safepoint(),
         "We should not be at a safepoint");
  SATBMarkQueue& queue = ShenandoahThreadLocalData::satb_mark_queue(thread);
  assert(!queue.is_active(), "SATB queue should not be active");
  assert( queue.is_empty(),  "SATB queue should be empty");
  queue.set_active(_satb_mark_queue_set.is_active());
  if (thread->is_Java_thread()) {
    ShenandoahThreadLocalData::set_gc_state(thread, _heap->gc_state());
    ShenandoahThreadLocalData::initialize_gclab(thread);
  }
}

void ShenandoahBarrierSet::on_thread_detach(Thread *thread) {
  SATBMarkQueue& queue = ShenandoahThreadLocalData::satb_mark_queue(thread);
  queue.flush();
  if (thread->is_Java_thread()) {
    PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
    if (gclab != NULL) {
      gclab->retire();
    }
  }
}

oop ShenandoahBarrierSet::load_reference_barrier_native(oop obj, oop* load_addr) {
  return load_reference_barrier_native_impl(obj, load_addr);
}

oop ShenandoahBarrierSet::load_reference_barrier_native(oop obj, narrowOop* load_addr) {
  return load_reference_barrier_native_impl(obj, load_addr);
}

template <class T>
oop ShenandoahBarrierSet::load_reference_barrier_native_impl(oop obj, T* load_addr) {
  if (CompressedOops::is_null(obj)) {
    return NULL;
  }

  ShenandoahMarkingContext* const marking_context = _heap->marking_context();
  if (_heap->is_concurrent_root_in_progress() && !marking_context->is_marked(obj)) {
    Thread* thr = Thread::current();
    if (thr->is_Java_thread()) {
      return NULL;
    } else {
      return obj;
    }
  }

  oop fwd = load_reference_barrier_not_null(obj);
  if (load_addr != NULL && fwd != obj) {
    // Since we are here and we know the load address, update the reference.
    ShenandoahHeap::cas_oop(fwd, load_addr, obj);
  }

  return fwd;
}

void ShenandoahBarrierSet::clone_barrier_runtime(oop src) {
  if (_heap->has_forwarded_objects()) {
    clone_barrier(src);
  }
}


/*
 * Copyright (c) 2013, 2019, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
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

template <bool STOREVAL_WRITE_BARRIER>
class ShenandoahUpdateRefsForOopClosure: public BasicOopIterateClosure {
private:
  ShenandoahHeap* _heap;
  ShenandoahBarrierSet* _bs;

  template <class T>
  inline void do_oop_work(T* p) {
    oop o;
    if (STOREVAL_WRITE_BARRIER) {
      o = _heap->evac_update_with_forwarded(p);
      if (!CompressedOops::is_null(o)) {
        _bs->enqueue(o);
      }
    } else {
      _heap->maybe_update_with_forwarded(p);
    }
  }
public:
  ShenandoahUpdateRefsForOopClosure() : _heap(ShenandoahHeap::heap()), _bs(ShenandoahBarrierSet::barrier_set()) {
    assert(UseShenandoahGC && ShenandoahCloneBarrier, "should be enabled");
  }

  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
};

ShenandoahBarrierSet::ShenandoahBarrierSet(ShenandoahHeap* heap) :
  BarrierSet(make_barrier_set_assembler<ShenandoahBarrierSetAssembler>(),
             make_barrier_set_c1<ShenandoahBarrierSetC1>(),
             make_barrier_set_c2<ShenandoahBarrierSetC2>(),
             NULL /* barrier_set_nmethod */,
             BarrierSet::FakeRtti(BarrierSet::ShenandoahBarrierSet)),
  _heap(heap),
  _satb_mark_queue_set()
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

template <class T, bool STOREVAL_WRITE_BARRIER>
void ShenandoahBarrierSet::write_ref_array_loop(HeapWord* start, size_t count) {
  assert(UseShenandoahGC && ShenandoahCloneBarrier, "should be enabled");
  ShenandoahUpdateRefsForOopClosure<STOREVAL_WRITE_BARRIER> cl;
  T* dst = (T*) start;
  for (size_t i = 0; i < count; i++) {
    cl.do_oop(dst++);
  }
}

void ShenandoahBarrierSet::write_ref_array(HeapWord* start, size_t count) {
  assert(UseShenandoahGC, "should be enabled");
  if (count == 0) return;
  if (!ShenandoahCloneBarrier) return;

  if (!need_update_refs_barrier()) return;

  if (_heap->is_concurrent_traversal_in_progress()) {
    ShenandoahEvacOOMScope oom_evac_scope;
    if (UseCompressedOops) {
      write_ref_array_loop<narrowOop, /* wb = */ true>(start, count);
    } else {
      write_ref_array_loop<oop,       /* wb = */ true>(start, count);
    }
  } else {
    if (UseCompressedOops) {
      write_ref_array_loop<narrowOop, /* wb = */ false>(start, count);
    } else {
      write_ref_array_loop<oop,       /* wb = */ false>(start, count);
    }
  }
}

template <class T>
void ShenandoahBarrierSet::write_ref_array_pre_work(T* dst, size_t count) {
  shenandoah_assert_not_in_cset_loc_except(dst, _heap->cancelled_gc());
  if (ShenandoahSATBBarrier && _heap->is_concurrent_mark_in_progress()) {
    T* elem_ptr = dst;
    for (size_t i = 0; i < count; i++, elem_ptr++) {
      T heap_oop = RawAccess<>::oop_load(elem_ptr);
      if (!CompressedOops::is_null(heap_oop)) {
        enqueue(CompressedOops::decode_not_null(heap_oop));
      }
    }
  }
}

void ShenandoahBarrierSet::write_ref_array_pre(oop* dst, size_t count, bool dest_uninitialized) {
  if (! dest_uninitialized) {
    write_ref_array_pre_work(dst, count);
  }
}

void ShenandoahBarrierSet::write_ref_array_pre(narrowOop* dst, size_t count, bool dest_uninitialized) {
  if (! dest_uninitialized) {
    write_ref_array_pre_work(dst, count);
  }
}

template <class T>
inline void ShenandoahBarrierSet::inline_write_ref_field_pre(T* field, oop new_val) {
  shenandoah_assert_not_in_cset_loc_except(field, _heap->cancelled_gc());
  if (_heap->is_concurrent_mark_in_progress()) {
    T heap_oop = RawAccess<>::oop_load(field);
    if (!CompressedOops::is_null(heap_oop)) {
      enqueue(CompressedOops::decode(heap_oop));
    }
  }
}

// These are the more general virtual versions.
void ShenandoahBarrierSet::write_ref_field_pre_work(oop* field, oop new_val) {
  inline_write_ref_field_pre(field, new_val);
}

void ShenandoahBarrierSet::write_ref_field_pre_work(narrowOop* field, oop new_val) {
  inline_write_ref_field_pre(field, new_val);
}

void ShenandoahBarrierSet::write_ref_field_pre_work(void* field, oop new_val) {
  guarantee(false, "Not needed");
}

void ShenandoahBarrierSet::write_ref_field_work(void* v, oop o, bool release) {
  shenandoah_assert_not_in_cset_loc_except(v, _heap->cancelled_gc());
  shenandoah_assert_not_forwarded_except  (v, o, o == NULL || _heap->cancelled_gc() || !_heap->is_concurrent_mark_in_progress());
  shenandoah_assert_not_in_cset_except    (v, o, o == NULL || _heap->cancelled_gc() || !_heap->is_concurrent_mark_in_progress());
}

void ShenandoahBarrierSet::write_region(MemRegion mr) {
  assert(UseShenandoahGC, "should be enabled");
  if (!ShenandoahCloneBarrier) return;
  if (! need_update_refs_barrier()) return;

  // This is called for cloning an object (see jvm.cpp) after the clone
  // has been made. We are not interested in any 'previous value' because
  // it would be NULL in any case. But we *are* interested in any oop*
  // that potentially need to be updated.

  oop obj = oop(mr.start());
  shenandoah_assert_correct(NULL, obj);
  if (_heap->is_concurrent_traversal_in_progress()) {
    ShenandoahEvacOOMScope oom_evac_scope;
    ShenandoahUpdateRefsForOopClosure</* wb = */ true> cl;
    obj->oop_iterate(&cl);
  } else {
    ShenandoahUpdateRefsForOopClosure</* wb = */ false> cl;
    obj->oop_iterate(&cl);
  }
}

oop ShenandoahBarrierSet::read_barrier(oop src) {
  // Check for forwarded objects, because on Full GC path we might deal with
  // non-trivial fwdptrs that contain Full GC specific metadata. We could check
  // for is_full_gc_in_progress(), but this also covers the case of stable heap,
  // which provides a bit of performance improvement.
  if (ShenandoahReadBarrier && _heap->has_forwarded_objects()) {
    return ShenandoahBarrierSet::resolve_forwarded(src);
  } else {
    return src;
  }
}

bool ShenandoahBarrierSet::obj_equals(oop obj1, oop obj2) {
  bool eq = oopDesc::equals_raw(obj1, obj2);
  if (! eq && ShenandoahAcmpBarrier) {
    OrderAccess::loadload();
    obj1 = resolve_forwarded(obj1);
    obj2 = resolve_forwarded(obj2);
    eq = oopDesc::equals_raw(obj1, obj2);
  }
  return eq;
}

oop ShenandoahBarrierSet::write_barrier_mutator(oop obj) {
  assert(UseShenandoahGC && ShenandoahWriteBarrier, "should be enabled");
  assert(_heap->is_gc_in_progress_mask(ShenandoahHeap::EVACUATION | ShenandoahHeap::TRAVERSAL), "evac should be in progress");
  shenandoah_assert_in_cset(NULL, obj);

  oop fwd = resolve_forwarded_not_null(obj);
  if (oopDesc::equals_raw(obj, fwd)) {
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

      HeapWord* cur = (HeapWord*)obj + obj->size() + ShenandoahBrooksPointer::word_size();

      size_t count = 0;
      while ((cur < r->top()) && ctx->is_marked(oop(cur)) && (count++ < max)) {
        oop cur_oop = oop(cur);
        if (oopDesc::equals_raw(cur_oop, resolve_forwarded_not_null(cur_oop))) {
          _heap->evacuate_object(cur_oop, thread);
        }
        cur = cur + cur_oop->size() + ShenandoahBrooksPointer::word_size();
      }
    }

    return res_oop;
  }
  return fwd;
}

oop ShenandoahBarrierSet::write_barrier_impl(oop obj) {
  assert(UseShenandoahGC && ShenandoahWriteBarrier, "should be enabled");
  if (!CompressedOops::is_null(obj)) {
    bool evac_in_progress = _heap->is_gc_in_progress_mask(ShenandoahHeap::EVACUATION | ShenandoahHeap::TRAVERSAL);
    oop fwd = resolve_forwarded_not_null(obj);
    if (evac_in_progress &&
        _heap->in_collection_set(obj) &&
        oopDesc::equals_raw(obj, fwd)) {
      Thread *t = Thread::current();
      if (t->is_GC_task_thread()) {
        return _heap->evacuate_object(obj, t);
      } else {
        ShenandoahEvacOOMScope oom_evac_scope;
        return _heap->evacuate_object(obj, t);
      }
    } else {
      return fwd;
    }
  } else {
    return obj;
  }
}

oop ShenandoahBarrierSet::write_barrier(oop obj) {
  if (ShenandoahWriteBarrier && _heap->has_forwarded_objects()) {
    return write_barrier_impl(obj);
  } else {
    return obj;
  }
}

oop ShenandoahBarrierSet::storeval_barrier(oop obj) {
  if (ShenandoahStoreValEnqueueBarrier) {
    if (!CompressedOops::is_null(obj)) {
      obj = write_barrier(obj);
      enqueue(obj);
    }
  }
  if (ShenandoahStoreValReadBarrier) {
    obj = resolve_forwarded(obj);
  }
  return obj;
}

void ShenandoahBarrierSet::keep_alive_barrier(oop obj) {
  if (ShenandoahKeepAliveBarrier && _heap->is_concurrent_mark_in_progress()) {
    enqueue(obj);
  }
}

void ShenandoahBarrierSet::enqueue(oop obj) {
  shenandoah_assert_not_forwarded_if(NULL, obj, _heap->is_concurrent_traversal_in_progress());
  if (!_satb_mark_queue_set.is_active()) return;

  // Filter marked objects before hitting the SATB queues. The same predicate would
  // be used by SATBMQ::filter to eliminate already marked objects downstream, but
  // filtering here helps to avoid wasteful SATB queueing work to begin with.
  if (!_heap->requires_marking<false>(obj)) return;

  ShenandoahThreadLocalData::satb_mark_queue(Thread::current()).enqueue(obj);
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

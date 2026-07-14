/*
 * Copyright (c) 2013, 2022, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/barrierSetNMethod.hpp"
#include "gc/shenandoah/shenandoahBarrierSetAssembler.hpp"
#include "gc/shenandoah/shenandoahBarrierSetNMethod.hpp"
#include "gc/shenandoah/shenandoahBarrierSetStackChunk.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahClosures.inline.hpp"
#include "gc/shenandoah/shenandoahForwarding.inline.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahScanRemembered.inline.hpp"
#include "gc/shenandoah/shenandoahStackWatermark.hpp"
#ifdef COMPILER1
#include "gc/shenandoah/c1/shenandoahBarrierSetC1.hpp"
#endif
#ifdef COMPILER2
#include "gc/shenandoah/c2/shenandoahBarrierSetC2.hpp"
#endif

class ShenandoahBarrierSetC1;
class ShenandoahBarrierSetC2;

ShenandoahBarrierSet::ShenandoahBarrierSet(ShenandoahHeap* heap, MemRegion heap_region) :
  BarrierSet(make_barrier_set_assembler<ShenandoahBarrierSetAssembler>(),
             make_barrier_set_c1<ShenandoahBarrierSetC1>(),
             make_barrier_set_c2<ShenandoahBarrierSetC2>(),
             new ShenandoahBarrierSetNMethod(heap),
             new ShenandoahBarrierSetStackChunk(),
             BarrierSet::FakeRtti(BarrierSet::ShenandoahBarrierSet)),
  _heap(heap),
  _card_table(nullptr),
  _satb_mark_queue_buffer_allocator("SATB Buffer Allocator", ShenandoahSATBBufferSize),
  _satb_mark_queue_set(&_satb_mark_queue_buffer_allocator)
{
  if (ShenandoahCardBarrier) {
    _card_table = new ShenandoahCardTable(heap_region);
    _card_table->initialize();
  }
}

ShenandoahBarrierSetAssembler* ShenandoahBarrierSet::assembler() {
  BarrierSetAssembler* const bsa = BarrierSet::barrier_set()->barrier_set_assembler();
  return reinterpret_cast<ShenandoahBarrierSetAssembler*>(bsa);
}

void ShenandoahBarrierSet::print_on(outputStream* st) const {
  st->print("ShenandoahBarrierSet");
}

bool ShenandoahBarrierSet::need_load_reference_barrier(DecoratorSet decorators, BasicType type) {
  if (!ShenandoahLoadRefBarrier) return false;
  return is_reference_type(type);
}

bool ShenandoahBarrierSet::need_keep_alive_barrier(DecoratorSet decorators, BasicType type) {
  if (!ShenandoahSATBBarrier) return false;
  if (!is_reference_type(type)) return false;
  bool keep_alive = (decorators & AS_NO_KEEPALIVE) == 0;
  bool unknown = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool on_weak_ref = (decorators & (ON_WEAK_OOP_REF | ON_PHANTOM_OOP_REF)) != 0;
  return (on_weak_ref || unknown) && keep_alive;
}

bool ShenandoahBarrierSet::need_satb_barrier(DecoratorSet decorators, BasicType type) {
  if (!ShenandoahSATBBarrier) return false;
  if (!is_reference_type(type)) return false;
  bool as_normal = (decorators & AS_NORMAL) != 0;
  bool dest_uninitialized = (decorators & IS_DEST_UNINITIALIZED) != 0;
  return as_normal && !dest_uninitialized;
}

bool ShenandoahBarrierSet::need_card_barrier(DecoratorSet decorators, BasicType type) {
  if (!ShenandoahCardBarrier) return false;
  if (!is_reference_type(type)) return false;
  bool in_heap = (decorators & IN_HEAP) != 0;
  return in_heap;
}

void ShenandoahBarrierSet::on_slowpath_allocation_exit(JavaThread* thread, oop new_obj) {
#ifdef COMPILER2
  if (ReduceInitialCardMarks && ShenandoahCardBarrier && !ShenandoahHeap::heap()->is_in_young(new_obj)) {
    log_debug(gc)("Newly allocated object (" PTR_FORMAT ") is not in the young generation", p2i(new_obj));
    // This can happen when an object is newly allocated, but we come to a safepoint before returning
    // the object. If the safepoint runs a degenerated cycle that is upgraded to a full GC, this object
    // will have survived two GC cycles. If the tenuring age is very low (1), this object may be promoted.
    // In this case, we have an allocated object, but it has received no stores yet. If card marking barriers
    // have been elided, we could end up with an object in old holding pointers to young that won't be in
    // the remembered set. The solution here is conservative, but this problem should be rare, and it will
    // correct itself on subsequent cycles when the remembered set is updated.
    ShenandoahGenerationalHeap::heap()->old_generation()->card_scan()->mark_range_as_dirty(
      cast_from_oop<HeapWord*>(new_obj), new_obj->size()
    );
  }
#endif // COMPILER2
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
  assert(queue.buffer() == nullptr, "SATB queue should not have a buffer");
  assert(queue.index() == 0, "SATB queue index should be zero");
  queue.set_active(_satb_mark_queue_set.is_active());

  if (ShenandoahCardBarrier) {
    // Every thread always have a pointer to the _current_ _write_ version of the card table.
    // The JIT'ed code will use this address (+card entry offset) to mark the card as dirty.
    ShenandoahThreadLocalData::set_card_table(thread, _card_table->write_byte_map_base());
  }
  ShenandoahThreadLocalData::set_gc_state(thread, _heap->gc_state());

  if (thread->is_Java_thread()) {
    ShenandoahThreadLocalData::initialize_gclab(thread);

    BarrierSetNMethod* bs_nm = barrier_set_nmethod();
    thread->set_nmethod_disarmed_guard_value(bs_nm->disarmed_guard_value());

    JavaThread* const jt = JavaThread::cast(thread);
    StackWatermark* const watermark = new ShenandoahStackWatermark(jt);
    StackWatermarkSet::add_watermark(jt, watermark);
  }
}

void ShenandoahBarrierSet::on_thread_detach(Thread *thread) {
  SATBMarkQueue& queue = ShenandoahThreadLocalData::satb_mark_queue(thread);
  _satb_mark_queue_set.flush_queue(queue);
  if (thread->is_Java_thread()) {
    PLAB* gclab = ShenandoahThreadLocalData::gclab(thread);
    if (gclab != nullptr) {
      gclab->retire();
    }

    ShenandoahPLAB* shenandoah_plab = ShenandoahThreadLocalData::shenandoah_plab(thread);
    if (shenandoah_plab != nullptr) {
      shenandoah_plab->retire();
    }

    // SATB protocol requires to keep alive reachable oops from roots at the beginning of GC
    if (_heap->is_concurrent_mark_in_progress()) {
      ShenandoahKeepAliveClosure oops;
      StackWatermarkSet::finish_processing(JavaThread::cast(thread), &oops, StackWatermarkKind::gc);
    } else if (_heap->is_concurrent_weak_root_in_progress() && _heap->is_evacuation_in_progress()) {
      ShenandoahContextEvacuateUpdateRootsClosure oops;
      StackWatermarkSet::finish_processing(JavaThread::cast(thread), &oops, StackWatermarkKind::gc);
    }
  }
}

void ShenandoahBarrierSet::keepalive_barrier_slow(oop obj, Filter filter) {
  if (!ShenandoahSATBBarrier) {
    return;
  }
  assert(obj != nullptr, "Filtered by caller");
  assert(_heap->is_concurrent_mark_in_progress(), "Filtered by caller");

  // Filter marked objects before hitting the SATB queues. The same predicate would
  // be used by SATBMQ::filter to eliminate already marked objects downstream, but
  // filtering here helps to avoid wasteful SATB queueing work to begin with.
  if (((filter & FILTER_MARKED) != 0) && !_heap->requires_marking(obj)) {
    return;
  }

  shenandoah_assert_correct(nullptr, obj);
  assert(_satb_mark_queue_set.is_active(), "only get here when SATB active");

  SATBMarkQueue& queue = ShenandoahThreadLocalData::satb_mark_queue(Thread::current());
  _satb_mark_queue_set.enqueue_known_active(queue, obj);
}

template <class T>
oop ShenandoahBarrierSet::load_reference_barrier_slow(oop obj, T* load_addr) {
  if (!ShenandoahLoadRefBarrier || !_heap->in_collection_set(obj)) {
    return obj;
  }
  assert(_heap->has_forwarded_objects(), "Filtered by caller");
  oop fwd = ShenandoahForwarding::get_forwardee(obj);
  if (obj == fwd && _heap->is_evacuation_in_progress()) {
    Thread* t = Thread::current();
    fwd = _heap->evacuate_object(obj, t);
  }
  if (load_addr != nullptr && fwd != obj) {
    // Since we are here and we know the load address, update the reference.
    ShenandoahHeap::atomic_update_oop(fwd, load_addr, obj);
  }
  return fwd;
}

template oop ShenandoahBarrierSet::load_reference_barrier_slow(oop obj, oop* load_addr);
template oop ShenandoahBarrierSet::load_reference_barrier_slow(oop obj, narrowOop* load_addr);

void ShenandoahBarrierSet::card_barrier_array_slow(HeapWord* start, size_t count) {
  assert(ShenandoahCardBarrier, "Filtered by caller");

  HeapWord* end = (HeapWord*)((char*) start + (count * heapOopSize));
  // In the case of compressed oops, start and end may potentially be misaligned;
  // so we need to conservatively align the first downward (this is not
  // strictly necessary for current uses, but a case of good hygiene and,
  // if you will, aesthetics) and the second upward (this is essential for
  // current uses) to a HeapWord boundary, so we mark all cards overlapping
  // this write.
  HeapWord* aligned_start = align_down(start, HeapWordSize);
  HeapWord* aligned_end   = align_up  (end,   HeapWordSize);
  // If compressed oops were not being used, these should already be aligned
  assert(UseCompressedOops || (aligned_start == start && aligned_end == end),
         "Expected heap word alignment of start and end");
  _heap->old_generation()->card_scan()->mark_range_as_dirty(aligned_start, (aligned_end - aligned_start));
}

// Clone barrier support
template <bool EVAC>
class ShenandoahUpdateEvacForCloneOopClosure : public BasicOopIterateClosure {
private:
  ShenandoahHeap* const _heap;
  const ShenandoahCollectionSet* const _cset;
  Thread* const _thread;

  template <class T>
  inline void do_oop_work(T* p) {
    T o = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      if (_cset->is_in(obj)) {
        oop fwd = ShenandoahForwarding::get_forwardee(obj);
        if (EVAC && obj == fwd) {
          fwd = _heap->evacuate_object(obj, _thread);
        }
        shenandoah_assert_forwarded_except(p, obj, _heap->cancelled_gc());
        ShenandoahHeap::atomic_update_oop(fwd, p, o);
        obj = fwd;
      }
    }
  }

public:
  ShenandoahUpdateEvacForCloneOopClosure() :
          _heap(ShenandoahHeap::heap()),
          _cset(_heap->collection_set()),
          _thread(Thread::current()) {}

  virtual void do_oop(oop* p)       { do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
};

template <bool EVAC>
void ShenandoahBarrierSet::clone_work(oop obj) {
  if (need_bulk_update(cast_from_oop<HeapWord*>(obj))) {
    ShenandoahUpdateEvacForCloneOopClosure<EVAC> cl;
    obj->oop_iterate(&cl);
  }
}

template void ShenandoahBarrierSet::clone_work<false>(oop obj);
template void ShenandoahBarrierSet::clone_work<true>(oop obj);

template <class T, bool HAS_FWD, bool EVAC, bool ENQUEUE>
void ShenandoahBarrierSet::arraycopy_work(T* src, size_t count) {
  // Young cycles are allowed to run when old marking is in progress. When old marking is in progress,
  // this barrier will be called with ENQUEUE=true and HAS_FWD=false, even though the young generation
  // may have forwarded objects.
  assert(HAS_FWD == _heap->has_forwarded_objects() || _heap->is_concurrent_old_mark_in_progress(), "Forwarded object status is sane");
  // This function cannot be called to handle marking and evacuation at the same time (they operate on
  // different sides of the copy).
  static_assert((HAS_FWD || EVAC) != ENQUEUE, "Cannot evacuate and mark both sides of copy.");

  Thread* thread = Thread::current();
  SATBMarkQueue& queue = ShenandoahThreadLocalData::satb_mark_queue(thread);
  ShenandoahMarkingContext* ctx = _heap->marking_context();
  const ShenandoahCollectionSet* const cset = _heap->collection_set();
  T* end = src + count;
  for (T* elem_ptr = src; elem_ptr < end; elem_ptr++) {
    T o = RawAccess<>::oop_load(elem_ptr);
    if (!CompressedOops::is_null(o)) {
      oop obj = CompressedOops::decode_not_null(o);
      if (HAS_FWD && cset->is_in(obj)) {
        oop fwd = ShenandoahForwarding::get_forwardee(obj);
        if (EVAC && obj == fwd) {
          fwd = _heap->evacuate_object(obj, thread);
        }
        shenandoah_assert_forwarded_except(elem_ptr, obj, _heap->cancelled_gc());
        ShenandoahHeap::atomic_update_oop(fwd, elem_ptr, o);
      }
      if (ENQUEUE && !ctx->is_marked_strong(obj)) {
        _satb_mark_queue_set.enqueue_known_active(queue, obj);
      }
    }
  }
}

template <bool IS_GENERATIONAL, class T>
void ShenandoahBarrierSet::arraycopy_marking(T* dst, size_t count) {
  assert(_heap->is_concurrent_mark_in_progress(), "only during marking");
  if (ShenandoahSATBBarrier) {
    if (!_heap->marking_context()->allocated_after_mark_start(reinterpret_cast<HeapWord*>(dst)) ||
        (IS_GENERATIONAL && _heap->heap_region_containing(dst)->is_old() && _heap->is_concurrent_young_mark_in_progress())) {
      arraycopy_work<T, false, false, true>(dst, count);
    }
  }
}

template void ShenandoahBarrierSet::arraycopy_marking<false, oop>(oop* dst, size_t count);
template void ShenandoahBarrierSet::arraycopy_marking<true,  oop>(oop* dst, size_t count);
template void ShenandoahBarrierSet::arraycopy_marking<false, narrowOop>(narrowOop* dst, size_t count);
template void ShenandoahBarrierSet::arraycopy_marking<true,  narrowOop>(narrowOop* dst, size_t count);

bool ShenandoahBarrierSet::need_bulk_update(HeapWord* ary) {
  return ary < _heap->heap_region_containing(ary)->get_update_watermark();
}

template <class T>
void ShenandoahBarrierSet::arraycopy_evacuation(T* src, size_t count) {
  assert(_heap->is_evacuation_in_progress(), "only during evacuation");
  if (need_bulk_update(reinterpret_cast<HeapWord*>(src))) {
    arraycopy_work<T, true, true, false>(src, count);
  }
}

template void ShenandoahBarrierSet::arraycopy_evacuation<oop>(oop* src, size_t count);
template void ShenandoahBarrierSet::arraycopy_evacuation<narrowOop>(narrowOop* src, size_t count);

template <class T>
void ShenandoahBarrierSet::arraycopy_update(T* src, size_t count) {
  assert(_heap->is_update_refs_in_progress(), "only during update-refs");
  if (need_bulk_update(reinterpret_cast<HeapWord*>(src))) {
    arraycopy_work<T, true, false, false>(src, count);
  }
}

template void ShenandoahBarrierSet::arraycopy_update<oop>(oop* src, size_t count);
template void ShenandoahBarrierSet::arraycopy_update<narrowOop>(narrowOop* src, size_t count);


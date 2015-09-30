/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/systemDictionary.hpp"
#include "gc/shared/allocTracer.hpp"
#include "gc/shared/barrierSet.inline.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.hpp"
#include "gc/shared/gcWhen.hpp"
#include "gc/shared/vmGCOperations.hpp"
#include "memory/metaspace.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/thread.inline.hpp"
#include "services/heapDumper.hpp"


#ifdef ASSERT
int CollectedHeap::_fire_out_of_memory_count = 0;
#endif

size_t CollectedHeap::_filler_array_max_size = 0;

template <>
void EventLogBase<GCMessage>::print(outputStream* st, GCMessage& m) {
  st->print_cr("GC heap %s", m.is_before ? "before" : "after");
  st->print_raw(m);
}

void GCHeapLog::log_heap(bool before) {
  if (!should_log()) {
    return;
  }

  double timestamp = fetch_timestamp();
  MutexLockerEx ml(&_mutex, Mutex::_no_safepoint_check_flag);
  int index = compute_log_index();
  _records[index].thread = NULL; // Its the GC thread so it's not that interesting.
  _records[index].timestamp = timestamp;
  _records[index].data.is_before = before;
  stringStream st(_records[index].data.buffer(), _records[index].data.size());
  if (before) {
    Universe::print_heap_before_gc(&st, true);
  } else {
    Universe::print_heap_after_gc(&st, true);
  }
}

VirtualSpaceSummary CollectedHeap::create_heap_space_summary() {
  size_t capacity_in_words = capacity() / HeapWordSize;

  return VirtualSpaceSummary(
    reserved_region().start(), reserved_region().start() + capacity_in_words, reserved_region().end());
}

GCHeapSummary CollectedHeap::create_heap_summary() {
  VirtualSpaceSummary heap_space = create_heap_space_summary();
  return GCHeapSummary(heap_space, used());
}

MetaspaceSummary CollectedHeap::create_metaspace_summary() {
  const MetaspaceSizes meta_space(
      MetaspaceAux::committed_bytes(),
      MetaspaceAux::used_bytes(),
      MetaspaceAux::reserved_bytes());
  const MetaspaceSizes data_space(
      MetaspaceAux::committed_bytes(Metaspace::NonClassType),
      MetaspaceAux::used_bytes(Metaspace::NonClassType),
      MetaspaceAux::reserved_bytes(Metaspace::NonClassType));
  const MetaspaceSizes class_space(
      MetaspaceAux::committed_bytes(Metaspace::ClassType),
      MetaspaceAux::used_bytes(Metaspace::ClassType),
      MetaspaceAux::reserved_bytes(Metaspace::ClassType));

  const MetaspaceChunkFreeListSummary& ms_chunk_free_list_summary =
    MetaspaceAux::chunk_free_list_summary(Metaspace::NonClassType);
  const MetaspaceChunkFreeListSummary& class_chunk_free_list_summary =
    MetaspaceAux::chunk_free_list_summary(Metaspace::ClassType);

  return MetaspaceSummary(MetaspaceGC::capacity_until_GC(), meta_space, data_space, class_space,
                          ms_chunk_free_list_summary, class_chunk_free_list_summary);
}

void CollectedHeap::print_heap_before_gc() {
  if (PrintHeapAtGC) {
    Universe::print_heap_before_gc();
  }
  if (_gc_heap_log != NULL) {
    _gc_heap_log->log_heap_before();
  }
}

void CollectedHeap::print_heap_after_gc() {
  if (PrintHeapAtGC) {
    Universe::print_heap_after_gc();
  }
  if (_gc_heap_log != NULL) {
    _gc_heap_log->log_heap_after();
  }
}

void CollectedHeap::print_on_error(outputStream* st) const {
  st->print_cr("Heap:");
  print_extended_on(st);
  st->cr();

  _barrier_set->print_on(st);
}

void CollectedHeap::register_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
}

void CollectedHeap::unregister_nmethod(nmethod* nm) {
  assert_locked_or_safepoint(CodeCache_lock);
}

void CollectedHeap::trace_heap(GCWhen::Type when, const GCTracer* gc_tracer) {
  const GCHeapSummary& heap_summary = create_heap_summary();
  gc_tracer->report_gc_heap_summary(when, heap_summary);

  const MetaspaceSummary& metaspace_summary = create_metaspace_summary();
  gc_tracer->report_metaspace_summary(when, metaspace_summary);
}

void CollectedHeap::trace_heap_before_gc(const GCTracer* gc_tracer) {
  trace_heap(GCWhen::BeforeGC, gc_tracer);
}

void CollectedHeap::trace_heap_after_gc(const GCTracer* gc_tracer) {
  trace_heap(GCWhen::AfterGC, gc_tracer);
}

// Memory state functions.


CollectedHeap::CollectedHeap() :
  _barrier_set(NULL),
  _is_gc_active(false),
  _total_collections(0),
  _total_full_collections(0),
  _gc_cause(GCCause::_no_gc),
  _gc_lastcause(GCCause::_no_gc),
  _defer_initial_card_mark(false) // strengthened by subclass in pre_initialize() below.
{
  const size_t max_len = size_t(arrayOopDesc::max_array_length(T_INT));
  const size_t elements_per_word = HeapWordSize / sizeof(jint);
  _filler_array_max_size = align_object_size(filler_array_hdr_size() +
                                             max_len / elements_per_word);

  NOT_PRODUCT(_promotion_failure_alot_count = 0;)
  NOT_PRODUCT(_promotion_failure_alot_gc_number = 0;)

  if (UsePerfData) {
    EXCEPTION_MARK;

    // create the gc cause jvmstat counters
    _perf_gc_cause = PerfDataManager::create_string_variable(SUN_GC, "cause",
                             80, GCCause::to_string(_gc_cause), CHECK);

    _perf_gc_lastcause =
                PerfDataManager::create_string_variable(SUN_GC, "lastCause",
                             80, GCCause::to_string(_gc_lastcause), CHECK);
  }

  // Create the ring log
  if (LogEvents) {
    _gc_heap_log = new GCHeapLog();
  } else {
    _gc_heap_log = NULL;
  }
}

// This interface assumes that it's being called by the
// vm thread. It collects the heap assuming that the
// heap lock is already held and that we are executing in
// the context of the vm thread.
void CollectedHeap::collect_as_vm_thread(GCCause::Cause cause) {
  assert(Thread::current()->is_VM_thread(), "Precondition#1");
  assert(Heap_lock->is_locked(), "Precondition#2");
  GCCauseSetter gcs(this, cause);
  switch (cause) {
    case GCCause::_heap_inspection:
    case GCCause::_heap_dump:
    case GCCause::_metadata_GC_threshold : {
      HandleMark hm;
      do_full_collection(false);        // don't clear all soft refs
      break;
    }
    case GCCause::_last_ditch_collection: {
      HandleMark hm;
      do_full_collection(true);         // do clear all soft refs
      break;
    }
    default:
      ShouldNotReachHere(); // Unexpected use of this function
  }
}

void CollectedHeap::set_barrier_set(BarrierSet* barrier_set) {
  _barrier_set = barrier_set;
  oopDesc::set_bs(_barrier_set);
}

void CollectedHeap::pre_initialize() {
  // Used for ReduceInitialCardMarks (when COMPILER2 is used);
  // otherwise remains unused.
#ifdef COMPILER2
  _defer_initial_card_mark =    ReduceInitialCardMarks && can_elide_tlab_store_barriers()
                             && (DeferInitialCardMark || card_mark_must_follow_store());
#else
  assert(_defer_initial_card_mark == false, "Who would set it?");
#endif
}

#ifndef PRODUCT
void CollectedHeap::check_for_bad_heap_word_value(HeapWord* addr, size_t size) {
  if (CheckMemoryInitialization && ZapUnusedHeapArea) {
    for (size_t slot = 0; slot < size; slot += 1) {
      assert((*(intptr_t*) (addr + slot)) != ((intptr_t) badHeapWordVal),
             "Found badHeapWordValue in post-allocation check");
    }
  }
}

void CollectedHeap::check_for_non_bad_heap_word_value(HeapWord* addr, size_t size) {
  if (CheckMemoryInitialization && ZapUnusedHeapArea) {
    for (size_t slot = 0; slot < size; slot += 1) {
      assert((*(intptr_t*) (addr + slot)) == ((intptr_t) badHeapWordVal),
             "Found non badHeapWordValue in pre-allocation check");
    }
  }
}
#endif // PRODUCT

#ifdef ASSERT
void CollectedHeap::check_for_valid_allocation_state() {
  Thread *thread = Thread::current();
  // How to choose between a pending exception and a potential
  // OutOfMemoryError?  Don't allow pending exceptions.
  // This is a VM policy failure, so how do we exhaustively test it?
  assert(!thread->has_pending_exception(),
         "shouldn't be allocating with pending exception");
  if (StrictSafepointChecks) {
    assert(thread->allow_allocation(),
           "Allocation done by thread for which allocation is blocked "
           "by No_Allocation_Verifier!");
    // Allocation of an oop can always invoke a safepoint,
    // hence, the true argument
    thread->check_for_valid_safepoint_state(true);
  }
}
#endif

HeapWord* CollectedHeap::allocate_from_tlab_slow(KlassHandle klass, Thread* thread, size_t size) {

  // Retain tlab and allocate object in shared space if
  // the amount free in the tlab is too large to discard.
  if (thread->tlab().free() > thread->tlab().refill_waste_limit()) {
    thread->tlab().record_slow_allocation(size);
    return NULL;
  }

  // Discard tlab and allocate a new one.
  // To minimize fragmentation, the last TLAB may be smaller than the rest.
  size_t new_tlab_size = thread->tlab().compute_size(size);

  thread->tlab().clear_before_allocation();

  if (new_tlab_size == 0) {
    return NULL;
  }

  // Allocate a new TLAB...
  HeapWord* obj = Universe::heap()->allocate_new_tlab(new_tlab_size);
  if (obj == NULL) {
    return NULL;
  }

  AllocTracer::send_allocation_in_new_tlab_event(klass, new_tlab_size * HeapWordSize, size * HeapWordSize);

  if (ZeroTLAB) {
    // ..and clear it.
    Copy::zero_to_words(obj, new_tlab_size);
  } else {
    // ...and zap just allocated object.
#ifdef ASSERT
    // Skip mangling the space corresponding to the object header to
    // ensure that the returned space is not considered parsable by
    // any concurrent GC thread.
    size_t hdr_size = oopDesc::header_size();
    Copy::fill_to_words(obj + hdr_size, new_tlab_size - hdr_size, badHeapWordVal);
#endif // ASSERT
  }
  thread->tlab().fill(obj, obj + size, new_tlab_size);
  return obj;
}

void CollectedHeap::flush_deferred_store_barrier(JavaThread* thread) {
  MemRegion deferred = thread->deferred_card_mark();
  if (!deferred.is_empty()) {
    assert(_defer_initial_card_mark, "Otherwise should be empty");
    {
      // Verify that the storage points to a parsable object in heap
      DEBUG_ONLY(oop old_obj = oop(deferred.start());)
      assert(is_in(old_obj), "Not in allocated heap");
      assert(!can_elide_initializing_store_barrier(old_obj),
             "Else should have been filtered in new_store_pre_barrier()");
      assert(old_obj->is_oop(true), "Not an oop");
      assert(deferred.word_size() == (size_t)(old_obj->size()),
             "Mismatch: multiple objects?");
    }
    BarrierSet* bs = barrier_set();
    assert(bs->has_write_region_opt(), "No write_region() on BarrierSet");
    bs->write_region(deferred);
    // "Clear" the deferred_card_mark field
    thread->set_deferred_card_mark(MemRegion());
  }
  assert(thread->deferred_card_mark().is_empty(), "invariant");
}

size_t CollectedHeap::max_tlab_size() const {
  // TLABs can't be bigger than we can fill with a int[Integer.MAX_VALUE].
  // This restriction could be removed by enabling filling with multiple arrays.
  // If we compute that the reasonable way as
  //    header_size + ((sizeof(jint) * max_jint) / HeapWordSize)
  // we'll overflow on the multiply, so we do the divide first.
  // We actually lose a little by dividing first,
  // but that just makes the TLAB  somewhat smaller than the biggest array,
  // which is fine, since we'll be able to fill that.
  size_t max_int_size = typeArrayOopDesc::header_size(T_INT) +
              sizeof(jint) *
              ((juint) max_jint / (size_t) HeapWordSize);
  return align_size_down(max_int_size, MinObjAlignment);
}

// Helper for ReduceInitialCardMarks. For performance,
// compiled code may elide card-marks for initializing stores
// to a newly allocated object along the fast-path. We
// compensate for such elided card-marks as follows:
// (a) Generational, non-concurrent collectors, such as
//     GenCollectedHeap(ParNew,DefNew,Tenured) and
//     ParallelScavengeHeap(ParallelGC, ParallelOldGC)
//     need the card-mark if and only if the region is
//     in the old gen, and do not care if the card-mark
//     succeeds or precedes the initializing stores themselves,
//     so long as the card-mark is completed before the next
//     scavenge. For all these cases, we can do a card mark
//     at the point at which we do a slow path allocation
//     in the old gen, i.e. in this call.
// (b) GenCollectedHeap(ConcurrentMarkSweepGeneration) requires
//     in addition that the card-mark for an old gen allocated
//     object strictly follow any associated initializing stores.
//     In these cases, the memRegion remembered below is
//     used to card-mark the entire region either just before the next
//     slow-path allocation by this thread or just before the next scavenge or
//     CMS-associated safepoint, whichever of these events happens first.
//     (The implicit assumption is that the object has been fully
//     initialized by this point, a fact that we assert when doing the
//     card-mark.)
// (c) G1CollectedHeap(G1) uses two kinds of write barriers. When a
//     G1 concurrent marking is in progress an SATB (pre-write-)barrier is
//     is used to remember the pre-value of any store. Initializing
//     stores will not need this barrier, so we need not worry about
//     compensating for the missing pre-barrier here. Turning now
//     to the post-barrier, we note that G1 needs a RS update barrier
//     which simply enqueues a (sequence of) dirty cards which may
//     optionally be refined by the concurrent update threads. Note
//     that this barrier need only be applied to a non-young write,
//     but, like in CMS, because of the presence of concurrent refinement
//     (much like CMS' precleaning), must strictly follow the oop-store.
//     Thus, using the same protocol for maintaining the intended
//     invariants turns out, serendepitously, to be the same for both
//     G1 and CMS.
//
// For any future collector, this code should be reexamined with
// that specific collector in mind, and the documentation above suitably
// extended and updated.
oop CollectedHeap::new_store_pre_barrier(JavaThread* thread, oop new_obj) {
  // If a previous card-mark was deferred, flush it now.
  flush_deferred_store_barrier(thread);
  if (can_elide_initializing_store_barrier(new_obj)) {
    // The deferred_card_mark region should be empty
    // following the flush above.
    assert(thread->deferred_card_mark().is_empty(), "Error");
  } else {
    MemRegion mr((HeapWord*)new_obj, new_obj->size());
    assert(!mr.is_empty(), "Error");
    if (_defer_initial_card_mark) {
      // Defer the card mark
      thread->set_deferred_card_mark(mr);
    } else {
      // Do the card mark
      BarrierSet* bs = barrier_set();
      assert(bs->has_write_region_opt(), "No write_region() on BarrierSet");
      bs->write_region(mr);
    }
  }
  return new_obj;
}

size_t CollectedHeap::filler_array_hdr_size() {
  return size_t(align_object_offset(arrayOopDesc::header_size(T_INT))); // align to Long
}

size_t CollectedHeap::filler_array_min_size() {
  return align_object_size(filler_array_hdr_size()); // align to MinObjAlignment
}

#ifdef ASSERT
void CollectedHeap::fill_args_check(HeapWord* start, size_t words)
{
  assert(words >= min_fill_size(), "too small to fill");
  assert(words % MinObjAlignment == 0, "unaligned size");
  assert(Universe::heap()->is_in_reserved(start), "not in heap");
  assert(Universe::heap()->is_in_reserved(start + words - 1), "not in heap");
}

void CollectedHeap::zap_filler_array(HeapWord* start, size_t words, bool zap)
{
  if (ZapFillerObjects && zap) {
    Copy::fill_to_words(start + filler_array_hdr_size(),
                        words - filler_array_hdr_size(), 0XDEAFBABE);
  }
}
#endif // ASSERT

void
CollectedHeap::fill_with_array(HeapWord* start, size_t words, bool zap)
{
  assert(words >= filler_array_min_size(), "too small for an array");
  assert(words <= filler_array_max_size(), "too big for a single object");

  const size_t payload_size = words - filler_array_hdr_size();
  const size_t len = payload_size * HeapWordSize / sizeof(jint);
  assert((int)len >= 0, "size too large " SIZE_FORMAT " becomes %d", words, (int)len);

  // Set the length first for concurrent GC.
  ((arrayOop)start)->set_length((int)len);
  post_allocation_setup_common(Universe::intArrayKlassObj(), start);
  DEBUG_ONLY(zap_filler_array(start, words, zap);)
}

void
CollectedHeap::fill_with_object_impl(HeapWord* start, size_t words, bool zap)
{
  assert(words <= filler_array_max_size(), "too big for a single object");

  if (words >= filler_array_min_size()) {
    fill_with_array(start, words, zap);
  } else if (words > 0) {
    assert(words == min_fill_size(), "unaligned size");
    post_allocation_setup_common(SystemDictionary::Object_klass(), start);
  }
}

void CollectedHeap::fill_with_object(HeapWord* start, size_t words, bool zap)
{
  DEBUG_ONLY(fill_args_check(start, words);)
  HandleMark hm;  // Free handles before leaving.
  fill_with_object_impl(start, words, zap);
}

void CollectedHeap::fill_with_objects(HeapWord* start, size_t words, bool zap)
{
  DEBUG_ONLY(fill_args_check(start, words);)
  HandleMark hm;  // Free handles before leaving.

  // Multiple objects may be required depending on the filler array maximum size. Fill
  // the range up to that with objects that are filler_array_max_size sized. The
  // remainder is filled with a single object.
  const size_t min = min_fill_size();
  const size_t max = filler_array_max_size();
  while (words > max) {
    const size_t cur = (words - max) >= min ? max : max - min;
    fill_with_array(start, cur, zap);
    start += cur;
    words -= cur;
  }

  fill_with_object_impl(start, words, zap);
}

void CollectedHeap::post_initialize() {
  collector_policy()->post_heap_initialize();
}

HeapWord* CollectedHeap::allocate_new_tlab(size_t size) {
  guarantee(false, "thread-local allocation buffers not supported");
  return NULL;
}

void CollectedHeap::ensure_parsability(bool retire_tlabs) {
  // The second disjunct in the assertion below makes a concession
  // for the start-up verification done while the VM is being
  // created. Callers be careful that you know that mutators
  // aren't going to interfere -- for instance, this is permissible
  // if we are still single-threaded and have either not yet
  // started allocating (nothing much to verify) or we have
  // started allocating but are now a full-fledged JavaThread
  // (and have thus made our TLAB's) available for filling.
  assert(SafepointSynchronize::is_at_safepoint() ||
         !is_init_completed(),
         "Should only be called at a safepoint or at start-up"
         " otherwise concurrent mutator activity may make heap "
         " unparsable again");
  const bool use_tlab = UseTLAB;
  const bool deferred = _defer_initial_card_mark;
  // The main thread starts allocating via a TLAB even before it
  // has added itself to the threads list at vm boot-up.
  assert(!use_tlab || Threads::first() != NULL,
         "Attempt to fill tlabs before main thread has been added"
         " to threads list is doomed to failure!");
  for (JavaThread *thread = Threads::first(); thread; thread = thread->next()) {
     if (use_tlab) thread->tlab().make_parsable(retire_tlabs);
#ifdef COMPILER2
     // The deferred store barriers must all have been flushed to the
     // card-table (or other remembered set structure) before GC starts
     // processing the card-table (or other remembered set).
     if (deferred) flush_deferred_store_barrier(thread);
#else
     assert(!deferred, "Should be false");
     assert(thread->deferred_card_mark().is_empty(), "Should be empty");
#endif
  }
}

void CollectedHeap::accumulate_statistics_all_tlabs() {
  if (UseTLAB) {
    assert(SafepointSynchronize::is_at_safepoint() ||
         !is_init_completed(),
         "should only accumulate statistics on tlabs at safepoint");

    ThreadLocalAllocBuffer::accumulate_statistics_before_gc();
  }
}

void CollectedHeap::resize_all_tlabs() {
  if (UseTLAB) {
    assert(SafepointSynchronize::is_at_safepoint() ||
         !is_init_completed(),
         "should only resize tlabs at safepoint");

    ThreadLocalAllocBuffer::resize_all_tlabs();
  }
}

void CollectedHeap::pre_full_gc_dump(GCTimer* timer) {
  if (HeapDumpBeforeFullGC) {
    GCTraceTime tt("Heap Dump (before full gc): ", PrintGCDetails, false, timer);
    // We are doing a full collection and a heap dump before
    // full collection has been requested.
    HeapDumper::dump_heap();
  }
  if (PrintClassHistogramBeforeFullGC) {
    GCTraceTime tt("Class Histogram (before full gc): ", PrintGCDetails, true, timer);
    VM_GC_HeapInspection inspector(gclog_or_tty, false /* ! full gc */);
    inspector.doit();
  }
}

void CollectedHeap::post_full_gc_dump(GCTimer* timer) {
  if (HeapDumpAfterFullGC) {
    GCTraceTime tt("Heap Dump (after full gc): ", PrintGCDetails, false, timer);
    HeapDumper::dump_heap();
  }
  if (PrintClassHistogramAfterFullGC) {
    GCTraceTime tt("Class Histogram (after full gc): ", PrintGCDetails, true, timer);
    VM_GC_HeapInspection inspector(gclog_or_tty, false /* ! full gc */);
    inspector.doit();
  }
}

void CollectedHeap::initialize_reserved_region(HeapWord *start, HeapWord *end) {
  // It is important to do this in a way such that concurrent readers can't
  // temporarily think something is in the heap.  (Seen this happen in asserts.)
  _reserved.set_word_size(0);
  _reserved.set_start(start);
  _reserved.set_end(end);
}

/////////////// Unit tests ///////////////

#ifndef PRODUCT
void CollectedHeap::test_is_in() {
  CollectedHeap* heap = Universe::heap();

  uintptr_t epsilon    = (uintptr_t) MinObjAlignment;
  uintptr_t heap_start = (uintptr_t) heap->_reserved.start();
  uintptr_t heap_end   = (uintptr_t) heap->_reserved.end();

  // Test that NULL is not in the heap.
  assert(!heap->is_in(NULL), "NULL is unexpectedly in the heap");

  // Test that a pointer to before the heap start is reported as outside the heap.
  assert(heap_start >= ((uintptr_t)NULL + epsilon), "sanity");
  void* before_heap = (void*)(heap_start - epsilon);
  assert(!heap->is_in(before_heap),
         "before_heap: " PTR_FORMAT " is unexpectedly in the heap", p2i(before_heap));

  // Test that a pointer to after the heap end is reported as outside the heap.
  assert(heap_end <= ((uintptr_t)-1 - epsilon), "sanity");
  void* after_heap = (void*)(heap_end + epsilon);
  assert(!heap->is_in(after_heap),
         "after_heap: " PTR_FORMAT " is unexpectedly in the heap", p2i(after_heap));
}
#endif

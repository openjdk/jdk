/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_collectedHeap.cpp.incl"


#ifdef ASSERT
int CollectedHeap::_fire_out_of_memory_count = 0;
#endif

size_t CollectedHeap::_filler_array_max_size = 0;

// Memory state functions.

CollectedHeap::CollectedHeap()
{
  const size_t max_len = size_t(arrayOopDesc::max_array_length(T_INT));
  const size_t elements_per_word = HeapWordSize / sizeof(jint);
  _filler_array_max_size = align_object_size(filler_array_hdr_size() +
                                             max_len * elements_per_word);

  _barrier_set = NULL;
  _is_gc_active = false;
  _total_collections = _total_full_collections = 0;
  _gc_cause = _gc_lastcause = GCCause::_no_gc;
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

void CollectedHeap::check_for_non_bad_heap_word_value(HeapWord* addr, size_t size)
 {
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

HeapWord* CollectedHeap::allocate_from_tlab_slow(Thread* thread, size_t size) {

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
  if (ZeroTLAB) {
    // ..and clear it.
    Copy::zero_to_words(obj, new_tlab_size);
  } else {
    // ...and clear just the allocated object.
    Copy::zero_to_words(obj, size);
  }
  thread->tlab().fill(obj, obj + size, new_tlab_size);
  return obj;
}

void CollectedHeap::flush_deferred_store_barrier(JavaThread* thread) {
  MemRegion deferred = thread->deferred_card_mark();
  if (!deferred.is_empty()) {
    {
      // Verify that the storage points to a parsable object in heap
      DEBUG_ONLY(oop old_obj = oop(deferred.start());)
      assert(is_in(old_obj), "Not in allocated heap");
      assert(!can_elide_initializing_store_barrier(old_obj),
             "Else should have been filtered in defer_store_barrier()");
      assert(!is_in_permanent(old_obj), "Sanity: not expected");
      assert(old_obj->is_oop(true), "Not an oop");
      assert(old_obj->is_parsable(), "Will not be concurrently parsable");
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
//     in the old gen. For uniformity, however, we end
//     up using the same scheme (see below) for all three
//     cases (deferring the card-mark appropriately).
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
//     invariants turns out, serendepitously, to be the same for all
//     three collectors/heap types above.
//
// For each future collector, this should be reexamined with
// that specific collector in mind.
oop CollectedHeap::defer_store_barrier(JavaThread* thread, oop new_obj) {
  // If a previous card-mark was deferred, flush it now.
  flush_deferred_store_barrier(thread);
  if (can_elide_initializing_store_barrier(new_obj)) {
    // The deferred_card_mark region should be empty
    // following the flush above.
    assert(thread->deferred_card_mark().is_empty(), "Error");
  } else {
    // Remember info for the newly deferred store barrier
    MemRegion deferred = MemRegion((HeapWord*)new_obj, new_obj->size());
    assert(!deferred.is_empty(), "Error");
    thread->set_deferred_card_mark(deferred);
  }
  return new_obj;
}

size_t CollectedHeap::filler_array_hdr_size() {
  return size_t(arrayOopDesc::header_size(T_INT));
}

size_t CollectedHeap::filler_array_min_size() {
  return align_object_size(filler_array_hdr_size());
}

size_t CollectedHeap::filler_array_max_size() {
  return _filler_array_max_size;
}

#ifdef ASSERT
void CollectedHeap::fill_args_check(HeapWord* start, size_t words)
{
  assert(words >= min_fill_size(), "too small to fill");
  assert(words % MinObjAlignment == 0, "unaligned size");
  assert(Universe::heap()->is_in_reserved(start), "not in heap");
  assert(Universe::heap()->is_in_reserved(start + words - 1), "not in heap");
}

void CollectedHeap::zap_filler_array(HeapWord* start, size_t words)
{
  if (ZapFillerObjects) {
    Copy::fill_to_words(start + filler_array_hdr_size(),
                        words - filler_array_hdr_size(), 0XDEAFBABE);
  }
}
#endif // ASSERT

void
CollectedHeap::fill_with_array(HeapWord* start, size_t words)
{
  assert(words >= filler_array_min_size(), "too small for an array");
  assert(words <= filler_array_max_size(), "too big for a single object");

  const size_t payload_size = words - filler_array_hdr_size();
  const size_t len = payload_size * HeapWordSize / sizeof(jint);

  // Set the length first for concurrent GC.
  ((arrayOop)start)->set_length((int)len);
  post_allocation_setup_common(Universe::intArrayKlassObj(), start, words);
  DEBUG_ONLY(zap_filler_array(start, words);)
}

void
CollectedHeap::fill_with_object_impl(HeapWord* start, size_t words)
{
  assert(words <= filler_array_max_size(), "too big for a single object");

  if (words >= filler_array_min_size()) {
    fill_with_array(start, words);
  } else if (words > 0) {
    assert(words == min_fill_size(), "unaligned size");
    post_allocation_setup_common(SystemDictionary::object_klass(), start,
                                 words);
  }
}

void CollectedHeap::fill_with_object(HeapWord* start, size_t words)
{
  DEBUG_ONLY(fill_args_check(start, words);)
  HandleMark hm;  // Free handles before leaving.
  fill_with_object_impl(start, words);
}

void CollectedHeap::fill_with_objects(HeapWord* start, size_t words)
{
  DEBUG_ONLY(fill_args_check(start, words);)
  HandleMark hm;  // Free handles before leaving.

#ifdef LP64
  // A single array can fill ~8G, so multiple objects are needed only in 64-bit.
  // First fill with arrays, ensuring that any remaining space is big enough to
  // fill.  The remainder is filled with a single object.
  const size_t min = min_fill_size();
  const size_t max = filler_array_max_size();
  while (words > max) {
    const size_t cur = words - max >= min ? max : max - min;
    fill_with_array(start, cur);
    start += cur;
    words -= cur;
  }
#endif

  fill_with_object_impl(start, words);
}

HeapWord* CollectedHeap::allocate_new_tlab(size_t size) {
  guarantee(false, "thread-local allocation buffers not supported");
  return NULL;
}

void CollectedHeap::fill_all_tlabs(bool retire) {
  assert(UseTLAB, "should not reach here");
  // See note in ensure_parsability() below.
  assert(SafepointSynchronize::is_at_safepoint() ||
         !is_init_completed(),
         "should only fill tlabs at safepoint");
  // The main thread starts allocating via a TLAB even before it
  // has added itself to the threads list at vm boot-up.
  assert(Threads::first() != NULL,
         "Attempt to fill tlabs before main thread has been added"
         " to threads list is doomed to failure!");
  for(JavaThread *thread = Threads::first(); thread; thread = thread->next()) {
     thread->tlab().make_parsable(retire);
  }
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
  if (UseTLAB) {
    fill_all_tlabs(retire_tlabs);
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

void CollectedHeap::pre_full_gc_dump() {
  if (HeapDumpBeforeFullGC) {
    TraceTime tt("Heap Dump: ", PrintGCDetails, false, gclog_or_tty);
    // We are doing a "major" collection and a heap dump before
    // major collection has been requested.
    HeapDumper::dump_heap();
  }
  if (PrintClassHistogramBeforeFullGC) {
    TraceTime tt("Class Histogram: ", PrintGCDetails, true, gclog_or_tty);
    VM_GC_HeapInspection inspector(gclog_or_tty, false /* ! full gc */, false /* ! prologue */);
    inspector.doit();
  }
}

void CollectedHeap::post_full_gc_dump() {
  if (HeapDumpAfterFullGC) {
    TraceTime tt("Heap Dump", PrintGCDetails, false, gclog_or_tty);
    HeapDumper::dump_heap();
  }
  if (PrintClassHistogramAfterFullGC) {
    TraceTime tt("Class Histogram", PrintGCDetails, true, gclog_or_tty);
    VM_GC_HeapInspection inspector(gclog_or_tty, false /* ! full gc */, false /* ! prologue */);
    inspector.doit();
  }
}

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
#include "classfile/metadataOnStackMark.hpp"
#include "classfile/stringTable.hpp"
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "gc/g1/bufferingOopClosure.hpp"
#include "gc/g1/concurrentG1Refine.hpp"
#include "gc/g1/concurrentG1RefineThread.hpp"
#include "gc/g1/concurrentMarkThread.inline.hpp"
#include "gc/g1/g1Allocator.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/g1CollectorState.hpp"
#include "gc/g1/g1ErgoVerbose.hpp"
#include "gc/g1/g1EvacFailure.hpp"
#include "gc/g1/g1EvacStats.inline.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1Log.hpp"
#include "gc/g1/g1MarkSweep.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "gc/g1/g1ParScanThreadState.inline.hpp"
#include "gc/g1/g1RegionToSpaceMapper.hpp"
#include "gc/g1/g1RemSet.inline.hpp"
#include "gc/g1/g1RootClosures.hpp"
#include "gc/g1/g1RootProcessor.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/g1YCTypes.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/g1/heapRegionSet.inline.hpp"
#include "gc/g1/suspendibleThreadSet.hpp"
#include "gc/g1/vm_operations_g1.hpp"
#include "gc/shared/gcHeapSummary.hpp"
#include "gc/shared/gcId.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "gc/shared/gcTimer.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.hpp"
#include "gc/shared/generationSpec.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/taskqueue.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "runtime/init.hpp"
#include "runtime/orderAccess.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/stack.inline.hpp"

size_t G1CollectedHeap::_humongous_object_threshold_in_words = 0;

// INVARIANTS/NOTES
//
// All allocation activity covered by the G1CollectedHeap interface is
// serialized by acquiring the HeapLock.  This happens in mem_allocate
// and allocate_new_tlab, which are the "entry" points to the
// allocation code from the rest of the JVM.  (Note that this does not
// apply to TLAB allocation, which is not part of this interface: it
// is done by clients of this interface.)

// Local to this file.

class RefineCardTableEntryClosure: public CardTableEntryClosure {
  bool _concurrent;
public:
  RefineCardTableEntryClosure() : _concurrent(true) { }

  bool do_card_ptr(jbyte* card_ptr, uint worker_i) {
    bool oops_into_cset = G1CollectedHeap::heap()->g1_rem_set()->refine_card(card_ptr, worker_i, false);
    // This path is executed by the concurrent refine or mutator threads,
    // concurrently, and so we do not care if card_ptr contains references
    // that point into the collection set.
    assert(!oops_into_cset, "should be");

    if (_concurrent && SuspendibleThreadSet::should_yield()) {
      // Caller will actually yield.
      return false;
    }
    // Otherwise, we finished successfully; return true.
    return true;
  }

  void set_concurrent(bool b) { _concurrent = b; }
};


class RedirtyLoggedCardTableEntryClosure : public CardTableEntryClosure {
 private:
  size_t _num_processed;

 public:
  RedirtyLoggedCardTableEntryClosure() : CardTableEntryClosure(), _num_processed(0) { }

  bool do_card_ptr(jbyte* card_ptr, uint worker_i) {
    *card_ptr = CardTableModRefBS::dirty_card_val();
    _num_processed++;
    return true;
  }

  size_t num_processed() const { return _num_processed; }
};


void G1RegionMappingChangedListener::reset_from_card_cache(uint start_idx, size_t num_regions) {
  HeapRegionRemSet::invalidate_from_card_cache(start_idx, num_regions);
}

void G1RegionMappingChangedListener::on_commit(uint start_idx, size_t num_regions, bool zero_filled) {
  // The from card cache is not the memory that is actually committed. So we cannot
  // take advantage of the zero_filled parameter.
  reset_from_card_cache(start_idx, num_regions);
}

void G1CollectedHeap::push_dirty_cards_region(HeapRegion* hr)
{
  // Claim the right to put the region on the dirty cards region list
  // by installing a self pointer.
  HeapRegion* next = hr->get_next_dirty_cards_region();
  if (next == NULL) {
    HeapRegion* res = (HeapRegion*)
      Atomic::cmpxchg_ptr(hr, hr->next_dirty_cards_region_addr(),
                          NULL);
    if (res == NULL) {
      HeapRegion* head;
      do {
        // Put the region to the dirty cards region list.
        head = _dirty_cards_region_list;
        next = (HeapRegion*)
          Atomic::cmpxchg_ptr(hr, &_dirty_cards_region_list, head);
        if (next == head) {
          assert(hr->get_next_dirty_cards_region() == hr,
                 "hr->get_next_dirty_cards_region() != hr");
          if (next == NULL) {
            // The last region in the list points to itself.
            hr->set_next_dirty_cards_region(hr);
          } else {
            hr->set_next_dirty_cards_region(next);
          }
        }
      } while (next != head);
    }
  }
}

HeapRegion* G1CollectedHeap::pop_dirty_cards_region()
{
  HeapRegion* head;
  HeapRegion* hr;
  do {
    head = _dirty_cards_region_list;
    if (head == NULL) {
      return NULL;
    }
    HeapRegion* new_head = head->get_next_dirty_cards_region();
    if (head == new_head) {
      // The last region.
      new_head = NULL;
    }
    hr = (HeapRegion*)Atomic::cmpxchg_ptr(new_head, &_dirty_cards_region_list,
                                          head);
  } while (hr != head);
  assert(hr != NULL, "invariant");
  hr->set_next_dirty_cards_region(NULL);
  return hr;
}

// Returns true if the reference points to an object that
// can move in an incremental collection.
bool G1CollectedHeap::is_scavengable(const void* p) {
  HeapRegion* hr = heap_region_containing(p);
  return !hr->is_pinned();
}

// Private methods.

HeapRegion*
G1CollectedHeap::new_region_try_secondary_free_list(bool is_old) {
  MutexLockerEx x(SecondaryFreeList_lock, Mutex::_no_safepoint_check_flag);
  while (!_secondary_free_list.is_empty() || free_regions_coming()) {
    if (!_secondary_free_list.is_empty()) {
      if (G1ConcRegionFreeingVerbose) {
        gclog_or_tty->print_cr("G1ConcRegionFreeing [region alloc] : "
                               "secondary_free_list has %u entries",
                               _secondary_free_list.length());
      }
      // It looks as if there are free regions available on the
      // secondary_free_list. Let's move them to the free_list and try
      // again to allocate from it.
      append_secondary_free_list();

      assert(_hrm.num_free_regions() > 0, "if the secondary_free_list was not "
             "empty we should have moved at least one entry to the free_list");
      HeapRegion* res = _hrm.allocate_free_region(is_old);
      if (G1ConcRegionFreeingVerbose) {
        gclog_or_tty->print_cr("G1ConcRegionFreeing [region alloc] : "
                               "allocated " HR_FORMAT " from secondary_free_list",
                               HR_FORMAT_PARAMS(res));
      }
      return res;
    }

    // Wait here until we get notified either when (a) there are no
    // more free regions coming or (b) some regions have been moved on
    // the secondary_free_list.
    SecondaryFreeList_lock->wait(Mutex::_no_safepoint_check_flag);
  }

  if (G1ConcRegionFreeingVerbose) {
    gclog_or_tty->print_cr("G1ConcRegionFreeing [region alloc] : "
                           "could not allocate from secondary_free_list");
  }
  return NULL;
}

HeapRegion* G1CollectedHeap::new_region(size_t word_size, bool is_old, bool do_expand) {
  assert(!is_humongous(word_size) || word_size <= HeapRegion::GrainWords,
         "the only time we use this to allocate a humongous region is "
         "when we are allocating a single humongous region");

  HeapRegion* res;
  if (G1StressConcRegionFreeing) {
    if (!_secondary_free_list.is_empty()) {
      if (G1ConcRegionFreeingVerbose) {
        gclog_or_tty->print_cr("G1ConcRegionFreeing [region alloc] : "
                               "forced to look at the secondary_free_list");
      }
      res = new_region_try_secondary_free_list(is_old);
      if (res != NULL) {
        return res;
      }
    }
  }

  res = _hrm.allocate_free_region(is_old);

  if (res == NULL) {
    if (G1ConcRegionFreeingVerbose) {
      gclog_or_tty->print_cr("G1ConcRegionFreeing [region alloc] : "
                             "res == NULL, trying the secondary_free_list");
    }
    res = new_region_try_secondary_free_list(is_old);
  }
  if (res == NULL && do_expand && _expand_heap_after_alloc_failure) {
    // Currently, only attempts to allocate GC alloc regions set
    // do_expand to true. So, we should only reach here during a
    // safepoint. If this assumption changes we might have to
    // reconsider the use of _expand_heap_after_alloc_failure.
    assert(SafepointSynchronize::is_at_safepoint(), "invariant");

    ergo_verbose1(ErgoHeapSizing,
                  "attempt heap expansion",
                  ergo_format_reason("region allocation request failed")
                  ergo_format_byte("allocation request"),
                  word_size * HeapWordSize);
    if (expand(word_size * HeapWordSize)) {
      // Given that expand() succeeded in expanding the heap, and we
      // always expand the heap by an amount aligned to the heap
      // region size, the free list should in theory not be empty.
      // In either case allocate_free_region() will check for NULL.
      res = _hrm.allocate_free_region(is_old);
    } else {
      _expand_heap_after_alloc_failure = false;
    }
  }
  return res;
}

HeapWord*
G1CollectedHeap::humongous_obj_allocate_initialize_regions(uint first,
                                                           uint num_regions,
                                                           size_t word_size,
                                                           AllocationContext_t context) {
  assert(first != G1_NO_HRM_INDEX, "pre-condition");
  assert(is_humongous(word_size), "word_size should be humongous");
  assert(num_regions * HeapRegion::GrainWords >= word_size, "pre-condition");

  // Index of last region in the series.
  uint last = first + num_regions - 1;

  // We need to initialize the region(s) we just discovered. This is
  // a bit tricky given that it can happen concurrently with
  // refinement threads refining cards on these regions and
  // potentially wanting to refine the BOT as they are scanning
  // those cards (this can happen shortly after a cleanup; see CR
  // 6991377). So we have to set up the region(s) carefully and in
  // a specific order.

  // The word size sum of all the regions we will allocate.
  size_t word_size_sum = (size_t) num_regions * HeapRegion::GrainWords;
  assert(word_size <= word_size_sum, "sanity");

  // This will be the "starts humongous" region.
  HeapRegion* first_hr = region_at(first);
  // The header of the new object will be placed at the bottom of
  // the first region.
  HeapWord* new_obj = first_hr->bottom();
  // This will be the new top of the new object.
  HeapWord* obj_top = new_obj + word_size;

  // First, we need to zero the header of the space that we will be
  // allocating. When we update top further down, some refinement
  // threads might try to scan the region. By zeroing the header we
  // ensure that any thread that will try to scan the region will
  // come across the zero klass word and bail out.
  //
  // NOTE: It would not have been correct to have used
  // CollectedHeap::fill_with_object() and make the space look like
  // an int array. The thread that is doing the allocation will
  // later update the object header to a potentially different array
  // type and, for a very short period of time, the klass and length
  // fields will be inconsistent. This could cause a refinement
  // thread to calculate the object size incorrectly.
  Copy::fill_to_words(new_obj, oopDesc::header_size(), 0);

  // How many words we use for filler objects.
  size_t word_fill_size = word_size_sum - word_size;

  // How many words memory we "waste" which cannot hold a filler object.
  size_t words_not_fillable = 0;

  if (word_fill_size >= min_fill_size()) {
    fill_with_objects(obj_top, word_fill_size);
  } else if (word_fill_size > 0) {
    // We have space to fill, but we cannot fit an object there.
    words_not_fillable = word_fill_size;
    word_fill_size = 0;
  }

  // We will set up the first region as "starts humongous". This
  // will also update the BOT covering all the regions to reflect
  // that there is a single object that starts at the bottom of the
  // first region.
  first_hr->set_starts_humongous(obj_top, word_fill_size);
  first_hr->set_allocation_context(context);
  // Then, if there are any, we will set up the "continues
  // humongous" regions.
  HeapRegion* hr = NULL;
  for (uint i = first + 1; i <= last; ++i) {
    hr = region_at(i);
    hr->set_continues_humongous(first_hr);
    hr->set_allocation_context(context);
  }

  // Up to this point no concurrent thread would have been able to
  // do any scanning on any region in this series. All the top
  // fields still point to bottom, so the intersection between
  // [bottom,top] and [card_start,card_end] will be empty. Before we
  // update the top fields, we'll do a storestore to make sure that
  // no thread sees the update to top before the zeroing of the
  // object header and the BOT initialization.
  OrderAccess::storestore();

  // Now, we will update the top fields of the "continues humongous"
  // regions except the last one.
  for (uint i = first; i < last; ++i) {
    hr = region_at(i);
    hr->set_top(hr->end());
  }

  hr = region_at(last);
  // If we cannot fit a filler object, we must set top to the end
  // of the humongous object, otherwise we cannot iterate the heap
  // and the BOT will not be complete.
  hr->set_top(hr->end() - words_not_fillable);

  assert(hr->bottom() < obj_top && obj_top <= hr->end(),
         "obj_top should be in last region");

  check_bitmaps("Humongous Region Allocation", first_hr);

  assert(words_not_fillable == 0 ||
         first_hr->bottom() + word_size_sum - words_not_fillable == hr->top(),
         "Miscalculation in humongous allocation");

  increase_used((word_size_sum - words_not_fillable) * HeapWordSize);

  for (uint i = first; i <= last; ++i) {
    hr = region_at(i);
    _humongous_set.add(hr);
    if (i == first) {
      _hr_printer.alloc(G1HRPrinter::StartsHumongous, hr, hr->top());
    } else {
      _hr_printer.alloc(G1HRPrinter::ContinuesHumongous, hr, hr->top());
    }
  }

  return new_obj;
}

size_t G1CollectedHeap::humongous_obj_size_in_regions(size_t word_size) {
  assert(is_humongous(word_size), "Object of size " SIZE_FORMAT " must be humongous here", word_size);
  return align_size_up_(word_size, HeapRegion::GrainWords) / HeapRegion::GrainWords;
}

// If could fit into free regions w/o expansion, try.
// Otherwise, if can expand, do so.
// Otherwise, if using ex regions might help, try with ex given back.
HeapWord* G1CollectedHeap::humongous_obj_allocate(size_t word_size, AllocationContext_t context) {
  assert_heap_locked_or_at_safepoint(true /* should_be_vm_thread */);

  verify_region_sets_optional();

  uint first = G1_NO_HRM_INDEX;
  uint obj_regions = (uint) humongous_obj_size_in_regions(word_size);

  if (obj_regions == 1) {
    // Only one region to allocate, try to use a fast path by directly allocating
    // from the free lists. Do not try to expand here, we will potentially do that
    // later.
    HeapRegion* hr = new_region(word_size, true /* is_old */, false /* do_expand */);
    if (hr != NULL) {
      first = hr->hrm_index();
    }
  } else {
    // We can't allocate humongous regions spanning more than one region while
    // cleanupComplete() is running, since some of the regions we find to be
    // empty might not yet be added to the free list. It is not straightforward
    // to know in which list they are on so that we can remove them. We only
    // need to do this if we need to allocate more than one region to satisfy the
    // current humongous allocation request. If we are only allocating one region
    // we use the one-region region allocation code (see above), that already
    // potentially waits for regions from the secondary free list.
    wait_while_free_regions_coming();
    append_secondary_free_list_if_not_empty_with_lock();

    // Policy: Try only empty regions (i.e. already committed first). Maybe we
    // are lucky enough to find some.
    first = _hrm.find_contiguous_only_empty(obj_regions);
    if (first != G1_NO_HRM_INDEX) {
      _hrm.allocate_free_regions_starting_at(first, obj_regions);
    }
  }

  if (first == G1_NO_HRM_INDEX) {
    // Policy: We could not find enough regions for the humongous object in the
    // free list. Look through the heap to find a mix of free and uncommitted regions.
    // If so, try expansion.
    first = _hrm.find_contiguous_empty_or_unavailable(obj_regions);
    if (first != G1_NO_HRM_INDEX) {
      // We found something. Make sure these regions are committed, i.e. expand
      // the heap. Alternatively we could do a defragmentation GC.
      ergo_verbose1(ErgoHeapSizing,
                    "attempt heap expansion",
                    ergo_format_reason("humongous allocation request failed")
                    ergo_format_byte("allocation request"),
                    word_size * HeapWordSize);

      _hrm.expand_at(first, obj_regions);
      g1_policy()->record_new_heap_size(num_regions());

#ifdef ASSERT
      for (uint i = first; i < first + obj_regions; ++i) {
        HeapRegion* hr = region_at(i);
        assert(hr->is_free(), "sanity");
        assert(hr->is_empty(), "sanity");
        assert(is_on_master_free_list(hr), "sanity");
      }
#endif
      _hrm.allocate_free_regions_starting_at(first, obj_regions);
    } else {
      // Policy: Potentially trigger a defragmentation GC.
    }
  }

  HeapWord* result = NULL;
  if (first != G1_NO_HRM_INDEX) {
    result = humongous_obj_allocate_initialize_regions(first, obj_regions,
                                                       word_size, context);
    assert(result != NULL, "it should always return a valid result");

    // A successful humongous object allocation changes the used space
    // information of the old generation so we need to recalculate the
    // sizes and update the jstat counters here.
    g1mm()->update_sizes();
  }

  verify_region_sets_optional();

  return result;
}

HeapWord* G1CollectedHeap::allocate_new_tlab(size_t word_size) {
  assert_heap_not_locked_and_not_at_safepoint();
  assert(!is_humongous(word_size), "we do not allow humongous TLABs");

  uint dummy_gc_count_before;
  uint dummy_gclocker_retry_count = 0;
  return attempt_allocation(word_size, &dummy_gc_count_before, &dummy_gclocker_retry_count);
}

HeapWord*
G1CollectedHeap::mem_allocate(size_t word_size,
                              bool*  gc_overhead_limit_was_exceeded) {
  assert_heap_not_locked_and_not_at_safepoint();

  // Loop until the allocation is satisfied, or unsatisfied after GC.
  for (uint try_count = 1, gclocker_retry_count = 0; /* we'll return */; try_count += 1) {
    uint gc_count_before;

    HeapWord* result = NULL;
    if (!is_humongous(word_size)) {
      result = attempt_allocation(word_size, &gc_count_before, &gclocker_retry_count);
    } else {
      result = attempt_allocation_humongous(word_size, &gc_count_before, &gclocker_retry_count);
    }
    if (result != NULL) {
      return result;
    }

    // Create the garbage collection operation...
    VM_G1CollectForAllocation op(gc_count_before, word_size);
    op.set_allocation_context(AllocationContext::current());

    // ...and get the VM thread to execute it.
    VMThread::execute(&op);

    if (op.prologue_succeeded() && op.pause_succeeded()) {
      // If the operation was successful we'll return the result even
      // if it is NULL. If the allocation attempt failed immediately
      // after a Full GC, it's unlikely we'll be able to allocate now.
      HeapWord* result = op.result();
      if (result != NULL && !is_humongous(word_size)) {
        // Allocations that take place on VM operations do not do any
        // card dirtying and we have to do it here. We only have to do
        // this for non-humongous allocations, though.
        dirty_young_block(result, word_size);
      }
      return result;
    } else {
      if (gclocker_retry_count > GCLockerRetryAllocationCount) {
        return NULL;
      }
      assert(op.result() == NULL,
             "the result should be NULL if the VM op did not succeed");
    }

    // Give a warning if we seem to be looping forever.
    if ((QueuedAllocationWarningCount > 0) &&
        (try_count % QueuedAllocationWarningCount == 0)) {
      warning("G1CollectedHeap::mem_allocate retries %d times", try_count);
    }
  }

  ShouldNotReachHere();
  return NULL;
}

HeapWord* G1CollectedHeap::attempt_allocation_slow(size_t word_size,
                                                   AllocationContext_t context,
                                                   uint* gc_count_before_ret,
                                                   uint* gclocker_retry_count_ret) {
  // Make sure you read the note in attempt_allocation_humongous().

  assert_heap_not_locked_and_not_at_safepoint();
  assert(!is_humongous(word_size), "attempt_allocation_slow() should not "
         "be called for humongous allocation requests");

  // We should only get here after the first-level allocation attempt
  // (attempt_allocation()) failed to allocate.

  // We will loop until a) we manage to successfully perform the
  // allocation or b) we successfully schedule a collection which
  // fails to perform the allocation. b) is the only case when we'll
  // return NULL.
  HeapWord* result = NULL;
  for (int try_count = 1; /* we'll return */; try_count += 1) {
    bool should_try_gc;
    uint gc_count_before;

    {
      MutexLockerEx x(Heap_lock);
      result = _allocator->attempt_allocation_locked(word_size, context);
      if (result != NULL) {
        return result;
      }

      if (GC_locker::is_active_and_needs_gc()) {
        if (g1_policy()->can_expand_young_list()) {
          // No need for an ergo verbose message here,
          // can_expand_young_list() does this when it returns true.
          result = _allocator->attempt_allocation_force(word_size, context);
          if (result != NULL) {
            return result;
          }
        }
        should_try_gc = false;
      } else {
        // The GCLocker may not be active but the GCLocker initiated
        // GC may not yet have been performed (GCLocker::needs_gc()
        // returns true). In this case we do not try this GC and
        // wait until the GCLocker initiated GC is performed, and
        // then retry the allocation.
        if (GC_locker::needs_gc()) {
          should_try_gc = false;
        } else {
          // Read the GC count while still holding the Heap_lock.
          gc_count_before = total_collections();
          should_try_gc = true;
        }
      }
    }

    if (should_try_gc) {
      bool succeeded;
      result = do_collection_pause(word_size, gc_count_before, &succeeded,
                                   GCCause::_g1_inc_collection_pause);
      if (result != NULL) {
        assert(succeeded, "only way to get back a non-NULL result");
        return result;
      }

      if (succeeded) {
        // If we get here we successfully scheduled a collection which
        // failed to allocate. No point in trying to allocate
        // further. We'll just return NULL.
        MutexLockerEx x(Heap_lock);
        *gc_count_before_ret = total_collections();
        return NULL;
      }
    } else {
      if (*gclocker_retry_count_ret > GCLockerRetryAllocationCount) {
        MutexLockerEx x(Heap_lock);
        *gc_count_before_ret = total_collections();
        return NULL;
      }
      // The GCLocker is either active or the GCLocker initiated
      // GC has not yet been performed. Stall until it is and
      // then retry the allocation.
      GC_locker::stall_until_clear();
      (*gclocker_retry_count_ret) += 1;
    }

    // We can reach here if we were unsuccessful in scheduling a
    // collection (because another thread beat us to it) or if we were
    // stalled due to the GC locker. In either can we should retry the
    // allocation attempt in case another thread successfully
    // performed a collection and reclaimed enough space. We do the
    // first attempt (without holding the Heap_lock) here and the
    // follow-on attempt will be at the start of the next loop
    // iteration (after taking the Heap_lock).
    result = _allocator->attempt_allocation(word_size, context);
    if (result != NULL) {
      return result;
    }

    // Give a warning if we seem to be looping forever.
    if ((QueuedAllocationWarningCount > 0) &&
        (try_count % QueuedAllocationWarningCount == 0)) {
      warning("G1CollectedHeap::attempt_allocation_slow() "
              "retries %d times", try_count);
    }
  }

  ShouldNotReachHere();
  return NULL;
}

void G1CollectedHeap::begin_archive_alloc_range() {
  assert_at_safepoint(true /* should_be_vm_thread */);
  if (_archive_allocator == NULL) {
    _archive_allocator = G1ArchiveAllocator::create_allocator(this);
  }
}

bool G1CollectedHeap::is_archive_alloc_too_large(size_t word_size) {
  // Allocations in archive regions cannot be of a size that would be considered
  // humongous even for a minimum-sized region, because G1 region sizes/boundaries
  // may be different at archive-restore time.
  return word_size >= humongous_threshold_for(HeapRegion::min_region_size_in_words());
}

HeapWord* G1CollectedHeap::archive_mem_allocate(size_t word_size) {
  assert_at_safepoint(true /* should_be_vm_thread */);
  assert(_archive_allocator != NULL, "_archive_allocator not initialized");
  if (is_archive_alloc_too_large(word_size)) {
    return NULL;
  }
  return _archive_allocator->archive_mem_allocate(word_size);
}

void G1CollectedHeap::end_archive_alloc_range(GrowableArray<MemRegion>* ranges,
                                              size_t end_alignment_in_bytes) {
  assert_at_safepoint(true /* should_be_vm_thread */);
  assert(_archive_allocator != NULL, "_archive_allocator not initialized");

  // Call complete_archive to do the real work, filling in the MemRegion
  // array with the archive regions.
  _archive_allocator->complete_archive(ranges, end_alignment_in_bytes);
  delete _archive_allocator;
  _archive_allocator = NULL;
}

bool G1CollectedHeap::check_archive_addresses(MemRegion* ranges, size_t count) {
  assert(ranges != NULL, "MemRegion array NULL");
  assert(count != 0, "No MemRegions provided");
  MemRegion reserved = _hrm.reserved();
  for (size_t i = 0; i < count; i++) {
    if (!reserved.contains(ranges[i].start()) || !reserved.contains(ranges[i].last())) {
      return false;
    }
  }
  return true;
}

bool G1CollectedHeap::alloc_archive_regions(MemRegion* ranges, size_t count) {
  assert(!is_init_completed(), "Expect to be called at JVM init time");
  assert(ranges != NULL, "MemRegion array NULL");
  assert(count != 0, "No MemRegions provided");
  MutexLockerEx x(Heap_lock);

  MemRegion reserved = _hrm.reserved();
  HeapWord* prev_last_addr = NULL;
  HeapRegion* prev_last_region = NULL;

  // Temporarily disable pretouching of heap pages. This interface is used
  // when mmap'ing archived heap data in, so pre-touching is wasted.
  FlagSetting fs(AlwaysPreTouch, false);

  // Enable archive object checking in G1MarkSweep. We have to let it know
  // about each archive range, so that objects in those ranges aren't marked.
  G1MarkSweep::enable_archive_object_check();

  // For each specified MemRegion range, allocate the corresponding G1
  // regions and mark them as archive regions. We expect the ranges in
  // ascending starting address order, without overlap.
  for (size_t i = 0; i < count; i++) {
    MemRegion curr_range = ranges[i];
    HeapWord* start_address = curr_range.start();
    size_t word_size = curr_range.word_size();
    HeapWord* last_address = curr_range.last();
    size_t commits = 0;

    guarantee(reserved.contains(start_address) && reserved.contains(last_address),
              "MemRegion outside of heap [" PTR_FORMAT ", " PTR_FORMAT "]",
              p2i(start_address), p2i(last_address));
    guarantee(start_address > prev_last_addr,
              "Ranges not in ascending order: " PTR_FORMAT " <= " PTR_FORMAT ,
              p2i(start_address), p2i(prev_last_addr));
    prev_last_addr = last_address;

    // Check for ranges that start in the same G1 region in which the previous
    // range ended, and adjust the start address so we don't try to allocate
    // the same region again. If the current range is entirely within that
    // region, skip it, just adjusting the recorded top.
    HeapRegion* start_region = _hrm.addr_to_region(start_address);
    if ((prev_last_region != NULL) && (start_region == prev_last_region)) {
      start_address = start_region->end();
      if (start_address > last_address) {
        increase_used(word_size * HeapWordSize);
        start_region->set_top(last_address + 1);
        continue;
      }
      start_region->set_top(start_address);
      curr_range = MemRegion(start_address, last_address + 1);
      start_region = _hrm.addr_to_region(start_address);
    }

    // Perform the actual region allocation, exiting if it fails.
    // Then note how much new space we have allocated.
    if (!_hrm.allocate_containing_regions(curr_range, &commits)) {
      return false;
    }
    increase_used(word_size * HeapWordSize);
    if (commits != 0) {
      ergo_verbose1(ErgoHeapSizing,
                    "attempt heap expansion",
                    ergo_format_reason("allocate archive regions")
                    ergo_format_byte("total size"),
                    HeapRegion::GrainWords * HeapWordSize * commits);
    }

    // Mark each G1 region touched by the range as archive, add it to the old set,
    // and set the allocation context and top.
    HeapRegion* curr_region = _hrm.addr_to_region(start_address);
    HeapRegion* last_region = _hrm.addr_to_region(last_address);
    prev_last_region = last_region;

    while (curr_region != NULL) {
      assert(curr_region->is_empty() && !curr_region->is_pinned(),
             "Region already in use (index %u)", curr_region->hrm_index());
      _hr_printer.alloc(curr_region, G1HRPrinter::Archive);
      curr_region->set_allocation_context(AllocationContext::system());
      curr_region->set_archive();
      _old_set.add(curr_region);
      if (curr_region != last_region) {
        curr_region->set_top(curr_region->end());
        curr_region = _hrm.next_region_in_heap(curr_region);
      } else {
        curr_region->set_top(last_address + 1);
        curr_region = NULL;
      }
    }

    // Notify mark-sweep of the archive range.
    G1MarkSweep::set_range_archive(curr_range, true);
  }
  return true;
}

void G1CollectedHeap::fill_archive_regions(MemRegion* ranges, size_t count) {
  assert(!is_init_completed(), "Expect to be called at JVM init time");
  assert(ranges != NULL, "MemRegion array NULL");
  assert(count != 0, "No MemRegions provided");
  MemRegion reserved = _hrm.reserved();
  HeapWord *prev_last_addr = NULL;
  HeapRegion* prev_last_region = NULL;

  // For each MemRegion, create filler objects, if needed, in the G1 regions
  // that contain the address range. The address range actually within the
  // MemRegion will not be modified. That is assumed to have been initialized
  // elsewhere, probably via an mmap of archived heap data.
  MutexLockerEx x(Heap_lock);
  for (size_t i = 0; i < count; i++) {
    HeapWord* start_address = ranges[i].start();
    HeapWord* last_address = ranges[i].last();

    assert(reserved.contains(start_address) && reserved.contains(last_address),
           "MemRegion outside of heap [" PTR_FORMAT ", " PTR_FORMAT "]",
           p2i(start_address), p2i(last_address));
    assert(start_address > prev_last_addr,
           "Ranges not in ascending order: " PTR_FORMAT " <= " PTR_FORMAT ,
           p2i(start_address), p2i(prev_last_addr));

    HeapRegion* start_region = _hrm.addr_to_region(start_address);
    HeapRegion* last_region = _hrm.addr_to_region(last_address);
    HeapWord* bottom_address = start_region->bottom();

    // Check for a range beginning in the same region in which the
    // previous one ended.
    if (start_region == prev_last_region) {
      bottom_address = prev_last_addr + 1;
    }

    // Verify that the regions were all marked as archive regions by
    // alloc_archive_regions.
    HeapRegion* curr_region = start_region;
    while (curr_region != NULL) {
      guarantee(curr_region->is_archive(),
                "Expected archive region at index %u", curr_region->hrm_index());
      if (curr_region != last_region) {
        curr_region = _hrm.next_region_in_heap(curr_region);
      } else {
        curr_region = NULL;
      }
    }

    prev_last_addr = last_address;
    prev_last_region = last_region;

    // Fill the memory below the allocated range with dummy object(s),
    // if the region bottom does not match the range start, or if the previous
    // range ended within the same G1 region, and there is a gap.
    if (start_address != bottom_address) {
      size_t fill_size = pointer_delta(start_address, bottom_address);
      G1CollectedHeap::fill_with_objects(bottom_address, fill_size);
      increase_used(fill_size * HeapWordSize);
    }
  }
}

inline HeapWord* G1CollectedHeap::attempt_allocation(size_t word_size,
                                                     uint* gc_count_before_ret,
                                                     uint* gclocker_retry_count_ret) {
  assert_heap_not_locked_and_not_at_safepoint();
  assert(!is_humongous(word_size), "attempt_allocation() should not "
         "be called for humongous allocation requests");

  AllocationContext_t context = AllocationContext::current();
  HeapWord* result = _allocator->attempt_allocation(word_size, context);

  if (result == NULL) {
    result = attempt_allocation_slow(word_size,
                                     context,
                                     gc_count_before_ret,
                                     gclocker_retry_count_ret);
  }
  assert_heap_not_locked();
  if (result != NULL) {
    dirty_young_block(result, word_size);
  }
  return result;
}

void G1CollectedHeap::dealloc_archive_regions(MemRegion* ranges, size_t count) {
  assert(!is_init_completed(), "Expect to be called at JVM init time");
  assert(ranges != NULL, "MemRegion array NULL");
  assert(count != 0, "No MemRegions provided");
  MemRegion reserved = _hrm.reserved();
  HeapWord* prev_last_addr = NULL;
  HeapRegion* prev_last_region = NULL;
  size_t size_used = 0;
  size_t uncommitted_regions = 0;

  // For each Memregion, free the G1 regions that constitute it, and
  // notify mark-sweep that the range is no longer to be considered 'archive.'
  MutexLockerEx x(Heap_lock);
  for (size_t i = 0; i < count; i++) {
    HeapWord* start_address = ranges[i].start();
    HeapWord* last_address = ranges[i].last();

    assert(reserved.contains(start_address) && reserved.contains(last_address),
           "MemRegion outside of heap [" PTR_FORMAT ", " PTR_FORMAT "]",
           p2i(start_address), p2i(last_address));
    assert(start_address > prev_last_addr,
           "Ranges not in ascending order: " PTR_FORMAT " <= " PTR_FORMAT ,
           p2i(start_address), p2i(prev_last_addr));
    size_used += ranges[i].byte_size();
    prev_last_addr = last_address;

    HeapRegion* start_region = _hrm.addr_to_region(start_address);
    HeapRegion* last_region = _hrm.addr_to_region(last_address);

    // Check for ranges that start in the same G1 region in which the previous
    // range ended, and adjust the start address so we don't try to free
    // the same region again. If the current range is entirely within that
    // region, skip it.
    if (start_region == prev_last_region) {
      start_address = start_region->end();
      if (start_address > last_address) {
        continue;
      }
      start_region = _hrm.addr_to_region(start_address);
    }
    prev_last_region = last_region;

    // After verifying that each region was marked as an archive region by
    // alloc_archive_regions, set it free and empty and uncommit it.
    HeapRegion* curr_region = start_region;
    while (curr_region != NULL) {
      guarantee(curr_region->is_archive(),
                "Expected archive region at index %u", curr_region->hrm_index());
      uint curr_index = curr_region->hrm_index();
      _old_set.remove(curr_region);
      curr_region->set_free();
      curr_region->set_top(curr_region->bottom());
      if (curr_region != last_region) {
        curr_region = _hrm.next_region_in_heap(curr_region);
      } else {
        curr_region = NULL;
      }
      _hrm.shrink_at(curr_index, 1);
      uncommitted_regions++;
    }

    // Notify mark-sweep that this is no longer an archive range.
    G1MarkSweep::set_range_archive(ranges[i], false);
  }

  if (uncommitted_regions != 0) {
    ergo_verbose1(ErgoHeapSizing,
                  "attempt heap shrinking",
                  ergo_format_reason("uncommitted archive regions")
                  ergo_format_byte("total size"),
                  HeapRegion::GrainWords * HeapWordSize * uncommitted_regions);
  }
  decrease_used(size_used);
}

HeapWord* G1CollectedHeap::attempt_allocation_humongous(size_t word_size,
                                                        uint* gc_count_before_ret,
                                                        uint* gclocker_retry_count_ret) {
  // The structure of this method has a lot of similarities to
  // attempt_allocation_slow(). The reason these two were not merged
  // into a single one is that such a method would require several "if
  // allocation is not humongous do this, otherwise do that"
  // conditional paths which would obscure its flow. In fact, an early
  // version of this code did use a unified method which was harder to
  // follow and, as a result, it had subtle bugs that were hard to
  // track down. So keeping these two methods separate allows each to
  // be more readable. It will be good to keep these two in sync as
  // much as possible.

  assert_heap_not_locked_and_not_at_safepoint();
  assert(is_humongous(word_size), "attempt_allocation_humongous() "
         "should only be called for humongous allocations");

  // Humongous objects can exhaust the heap quickly, so we should check if we
  // need to start a marking cycle at each humongous object allocation. We do
  // the check before we do the actual allocation. The reason for doing it
  // before the allocation is that we avoid having to keep track of the newly
  // allocated memory while we do a GC.
  if (g1_policy()->need_to_start_conc_mark("concurrent humongous allocation",
                                           word_size)) {
    collect(GCCause::_g1_humongous_allocation);
  }

  // We will loop until a) we manage to successfully perform the
  // allocation or b) we successfully schedule a collection which
  // fails to perform the allocation. b) is the only case when we'll
  // return NULL.
  HeapWord* result = NULL;
  for (int try_count = 1; /* we'll return */; try_count += 1) {
    bool should_try_gc;
    uint gc_count_before;

    {
      MutexLockerEx x(Heap_lock);

      // Given that humongous objects are not allocated in young
      // regions, we'll first try to do the allocation without doing a
      // collection hoping that there's enough space in the heap.
      result = humongous_obj_allocate(word_size, AllocationContext::current());
      if (result != NULL) {
        size_t size_in_regions = humongous_obj_size_in_regions(word_size);
        g1_policy()->add_bytes_allocated_in_old_since_last_gc(size_in_regions * HeapRegion::GrainBytes);
        return result;
      }

      if (GC_locker::is_active_and_needs_gc()) {
        should_try_gc = false;
      } else {
         // The GCLocker may not be active but the GCLocker initiated
        // GC may not yet have been performed (GCLocker::needs_gc()
        // returns true). In this case we do not try this GC and
        // wait until the GCLocker initiated GC is performed, and
        // then retry the allocation.
        if (GC_locker::needs_gc()) {
          should_try_gc = false;
        } else {
          // Read the GC count while still holding the Heap_lock.
          gc_count_before = total_collections();
          should_try_gc = true;
        }
      }
    }

    if (should_try_gc) {
      // If we failed to allocate the humongous object, we should try to
      // do a collection pause (if we're allowed) in case it reclaims
      // enough space for the allocation to succeed after the pause.

      bool succeeded;
      result = do_collection_pause(word_size, gc_count_before, &succeeded,
                                   GCCause::_g1_humongous_allocation);
      if (result != NULL) {
        assert(succeeded, "only way to get back a non-NULL result");
        return result;
      }

      if (succeeded) {
        // If we get here we successfully scheduled a collection which
        // failed to allocate. No point in trying to allocate
        // further. We'll just return NULL.
        MutexLockerEx x(Heap_lock);
        *gc_count_before_ret = total_collections();
        return NULL;
      }
    } else {
      if (*gclocker_retry_count_ret > GCLockerRetryAllocationCount) {
        MutexLockerEx x(Heap_lock);
        *gc_count_before_ret = total_collections();
        return NULL;
      }
      // The GCLocker is either active or the GCLocker initiated
      // GC has not yet been performed. Stall until it is and
      // then retry the allocation.
      GC_locker::stall_until_clear();
      (*gclocker_retry_count_ret) += 1;
    }

    // We can reach here if we were unsuccessful in scheduling a
    // collection (because another thread beat us to it) or if we were
    // stalled due to the GC locker. In either can we should retry the
    // allocation attempt in case another thread successfully
    // performed a collection and reclaimed enough space.  Give a
    // warning if we seem to be looping forever.

    if ((QueuedAllocationWarningCount > 0) &&
        (try_count % QueuedAllocationWarningCount == 0)) {
      warning("G1CollectedHeap::attempt_allocation_humongous() "
              "retries %d times", try_count);
    }
  }

  ShouldNotReachHere();
  return NULL;
}

HeapWord* G1CollectedHeap::attempt_allocation_at_safepoint(size_t word_size,
                                                           AllocationContext_t context,
                                                           bool expect_null_mutator_alloc_region) {
  assert_at_safepoint(true /* should_be_vm_thread */);
  assert(!_allocator->has_mutator_alloc_region(context) || !expect_null_mutator_alloc_region,
         "the current alloc region was unexpectedly found to be non-NULL");

  if (!is_humongous(word_size)) {
    return _allocator->attempt_allocation_locked(word_size, context);
  } else {
    HeapWord* result = humongous_obj_allocate(word_size, context);
    if (result != NULL && g1_policy()->need_to_start_conc_mark("STW humongous allocation")) {
      collector_state()->set_initiate_conc_mark_if_possible(true);
    }
    return result;
  }

  ShouldNotReachHere();
}

class PostMCRemSetClearClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  ModRefBarrierSet* _mr_bs;
public:
  PostMCRemSetClearClosure(G1CollectedHeap* g1h, ModRefBarrierSet* mr_bs) :
    _g1h(g1h), _mr_bs(mr_bs) {}

  bool doHeapRegion(HeapRegion* r) {
    HeapRegionRemSet* hrrs = r->rem_set();

    _g1h->reset_gc_time_stamps(r);

    if (r->is_continues_humongous()) {
      // We'll assert that the strong code root list and RSet is empty
      assert(hrrs->strong_code_roots_list_length() == 0, "sanity");
      assert(hrrs->occupied() == 0, "RSet should be empty");
    } else {
      hrrs->clear();
    }
    // You might think here that we could clear just the cards
    // corresponding to the used region.  But no: if we leave a dirty card
    // in a region we might allocate into, then it would prevent that card
    // from being enqueued, and cause it to be missed.
    // Re: the performance cost: we shouldn't be doing full GC anyway!
    _mr_bs->clear(MemRegion(r->bottom(), r->end()));

    return false;
  }
};

void G1CollectedHeap::clear_rsets_post_compaction() {
  PostMCRemSetClearClosure rs_clear(this, g1_barrier_set());
  heap_region_iterate(&rs_clear);
}

class RebuildRSOutOfRegionClosure: public HeapRegionClosure {
  G1CollectedHeap*   _g1h;
  UpdateRSOopClosure _cl;
public:
  RebuildRSOutOfRegionClosure(G1CollectedHeap* g1, uint worker_i = 0) :
    _cl(g1->g1_rem_set(), worker_i),
    _g1h(g1)
  { }

  bool doHeapRegion(HeapRegion* r) {
    if (!r->is_continues_humongous()) {
      _cl.set_from(r);
      r->oop_iterate(&_cl);
    }
    return false;
  }
};

class ParRebuildRSTask: public AbstractGangTask {
  G1CollectedHeap* _g1;
  HeapRegionClaimer _hrclaimer;

public:
  ParRebuildRSTask(G1CollectedHeap* g1) :
      AbstractGangTask("ParRebuildRSTask"), _g1(g1), _hrclaimer(g1->workers()->active_workers()) {}

  void work(uint worker_id) {
    RebuildRSOutOfRegionClosure rebuild_rs(_g1, worker_id);
    _g1->heap_region_par_iterate(&rebuild_rs, worker_id, &_hrclaimer);
  }
};

class PostCompactionPrinterClosure: public HeapRegionClosure {
private:
  G1HRPrinter* _hr_printer;
public:
  bool doHeapRegion(HeapRegion* hr) {
    assert(!hr->is_young(), "not expecting to find young regions");
    if (hr->is_free()) {
      // We only generate output for non-empty regions.
    } else if (hr->is_starts_humongous()) {
      _hr_printer->post_compaction(hr, G1HRPrinter::StartsHumongous);
    } else if (hr->is_continues_humongous()) {
      _hr_printer->post_compaction(hr, G1HRPrinter::ContinuesHumongous);
    } else if (hr->is_archive()) {
      _hr_printer->post_compaction(hr, G1HRPrinter::Archive);
    } else if (hr->is_old()) {
      _hr_printer->post_compaction(hr, G1HRPrinter::Old);
    } else {
      ShouldNotReachHere();
    }
    return false;
  }

  PostCompactionPrinterClosure(G1HRPrinter* hr_printer)
    : _hr_printer(hr_printer) { }
};

void G1CollectedHeap::print_hrm_post_compaction() {
  PostCompactionPrinterClosure cl(hr_printer());
  heap_region_iterate(&cl);
}

bool G1CollectedHeap::do_full_collection(bool explicit_gc,
                                         bool clear_all_soft_refs) {
  assert_at_safepoint(true /* should_be_vm_thread */);

  if (GC_locker::check_active_before_gc()) {
    return false;
  }

  STWGCTimer* gc_timer = G1MarkSweep::gc_timer();
  gc_timer->register_gc_start();

  SerialOldTracer* gc_tracer = G1MarkSweep::gc_tracer();
  GCIdMark gc_id_mark;
  gc_tracer->report_gc_start(gc_cause(), gc_timer->gc_start());

  SvcGCMarker sgcm(SvcGCMarker::FULL);
  ResourceMark rm;

  G1Log::update_level();
  print_heap_before_gc();
  trace_heap_before_gc(gc_tracer);

  size_t metadata_prev_used = MetaspaceAux::used_bytes();

  verify_region_sets_optional();

  const bool do_clear_all_soft_refs = clear_all_soft_refs ||
                           collector_policy()->should_clear_all_soft_refs();

  ClearedAllSoftRefs casr(do_clear_all_soft_refs, collector_policy());

  {
    IsGCActiveMark x;

    // Timing
    assert(!GCCause::is_user_requested_gc(gc_cause()) || explicit_gc, "invariant");
    TraceCPUTime tcpu(G1Log::finer(), true, gclog_or_tty);

    {
      GCTraceTime t(GCCauseString("Full GC", gc_cause()), G1Log::fine(), true, NULL);
      TraceCollectorStats tcs(g1mm()->full_collection_counters());
      TraceMemoryManagerStats tms(true /* fullGC */, gc_cause());

      g1_policy()->record_full_collection_start();

      // Note: When we have a more flexible GC logging framework that
      // allows us to add optional attributes to a GC log record we
      // could consider timing and reporting how long we wait in the
      // following two methods.
      wait_while_free_regions_coming();
      // If we start the compaction before the CM threads finish
      // scanning the root regions we might trip them over as we'll
      // be moving objects / updating references. So let's wait until
      // they are done. By telling them to abort, they should complete
      // early.
      _cm->root_regions()->abort();
      _cm->root_regions()->wait_until_scan_finished();
      append_secondary_free_list_if_not_empty_with_lock();

      gc_prologue(true);
      increment_total_collections(true /* full gc */);
      increment_old_marking_cycles_started();

      assert(used() == recalculate_used(), "Should be equal");

      verify_before_gc();

      check_bitmaps("Full GC Start");
      pre_full_gc_dump(gc_timer);

#if defined(COMPILER2) || INCLUDE_JVMCI
      DerivedPointerTable::clear();
#endif

      // Disable discovery and empty the discovered lists
      // for the CM ref processor.
      ref_processor_cm()->disable_discovery();
      ref_processor_cm()->abandon_partial_discovery();
      ref_processor_cm()->verify_no_references_recorded();

      // Abandon current iterations of concurrent marking and concurrent
      // refinement, if any are in progress. We have to do this before
      // wait_until_scan_finished() below.
      concurrent_mark()->abort();

      // Make sure we'll choose a new allocation region afterwards.
      _allocator->release_mutator_alloc_region();
      _allocator->abandon_gc_alloc_regions();
      g1_rem_set()->cleanupHRRS();

      // We should call this after we retire any currently active alloc
      // regions so that all the ALLOC / RETIRE events are generated
      // before the start GC event.
      _hr_printer.start_gc(true /* full */, (size_t) total_collections());

      // We may have added regions to the current incremental collection
      // set between the last GC or pause and now. We need to clear the
      // incremental collection set and then start rebuilding it afresh
      // after this full GC.
      abandon_collection_set(g1_policy()->inc_cset_head());
      g1_policy()->clear_incremental_cset();
      g1_policy()->stop_incremental_cset_building();

      tear_down_region_sets(false /* free_list_only */);
      collector_state()->set_gcs_are_young(true);

      // See the comments in g1CollectedHeap.hpp and
      // G1CollectedHeap::ref_processing_init() about
      // how reference processing currently works in G1.

      // Temporarily make discovery by the STW ref processor single threaded (non-MT).
      ReferenceProcessorMTDiscoveryMutator stw_rp_disc_ser(ref_processor_stw(), false);

      // Temporarily clear the STW ref processor's _is_alive_non_header field.
      ReferenceProcessorIsAliveMutator stw_rp_is_alive_null(ref_processor_stw(), NULL);

      ref_processor_stw()->enable_discovery();
      ref_processor_stw()->setup_policy(do_clear_all_soft_refs);

      // Do collection work
      {
        HandleMark hm;  // Discard invalid handles created during gc
        G1MarkSweep::invoke_at_safepoint(ref_processor_stw(), do_clear_all_soft_refs);
      }

      assert(num_free_regions() == 0, "we should not have added any free regions");
      rebuild_region_sets(false /* free_list_only */);

      // Enqueue any discovered reference objects that have
      // not been removed from the discovered lists.
      ref_processor_stw()->enqueue_discovered_references();

#if defined(COMPILER2) || INCLUDE_JVMCI
      DerivedPointerTable::update_pointers();
#endif

      MemoryService::track_memory_usage();

      assert(!ref_processor_stw()->discovery_enabled(), "Postcondition");
      ref_processor_stw()->verify_no_references_recorded();

      // Delete metaspaces for unloaded class loaders and clean up loader_data graph
      ClassLoaderDataGraph::purge();
      MetaspaceAux::verify_metrics();

      // Note: since we've just done a full GC, concurrent
      // marking is no longer active. Therefore we need not
      // re-enable reference discovery for the CM ref processor.
      // That will be done at the start of the next marking cycle.
      assert(!ref_processor_cm()->discovery_enabled(), "Postcondition");
      ref_processor_cm()->verify_no_references_recorded();

      reset_gc_time_stamp();
      // Since everything potentially moved, we will clear all remembered
      // sets, and clear all cards.  Later we will rebuild remembered
      // sets. We will also reset the GC time stamps of the regions.
      clear_rsets_post_compaction();
      check_gc_time_stamps();

      resize_if_necessary_after_full_collection();

      if (_hr_printer.is_active()) {
        // We should do this after we potentially resize the heap so
        // that all the COMMIT / UNCOMMIT events are generated before
        // the end GC event.

        print_hrm_post_compaction();
        _hr_printer.end_gc(true /* full */, (size_t) total_collections());
      }

      G1HotCardCache* hot_card_cache = _cg1r->hot_card_cache();
      if (hot_card_cache->use_cache()) {
        hot_card_cache->reset_card_counts();
        hot_card_cache->reset_hot_cache();
      }

      // Rebuild remembered sets of all regions.
      uint n_workers =
        AdaptiveSizePolicy::calc_active_workers(workers()->total_workers(),
                                                workers()->active_workers(),
                                                Threads::number_of_non_daemon_threads());
      workers()->set_active_workers(n_workers);

      ParRebuildRSTask rebuild_rs_task(this);
      workers()->run_task(&rebuild_rs_task);

      // Rebuild the strong code root lists for each region
      rebuild_strong_code_roots();

      if (true) { // FIXME
        MetaspaceGC::compute_new_size();
      }

#ifdef TRACESPINNING
      ParallelTaskTerminator::print_termination_counts();
#endif

      // Discard all rset updates
      JavaThread::dirty_card_queue_set().abandon_logs();
      assert(dirty_card_queue_set().completed_buffers_num() == 0, "DCQS should be empty");

      _young_list->reset_sampled_info();
      // At this point there should be no regions in the
      // entire heap tagged as young.
      assert(check_young_list_empty(true /* check_heap */),
             "young list should be empty at this point");

      // Update the number of full collections that have been completed.
      increment_old_marking_cycles_completed(false /* concurrent */);

      _hrm.verify_optional();
      verify_region_sets_optional();

      verify_after_gc();

      // Clear the previous marking bitmap, if needed for bitmap verification.
      // Note we cannot do this when we clear the next marking bitmap in
      // ConcurrentMark::abort() above since VerifyDuringGC verifies the
      // objects marked during a full GC against the previous bitmap.
      // But we need to clear it before calling check_bitmaps below since
      // the full GC has compacted objects and updated TAMS but not updated
      // the prev bitmap.
      if (G1VerifyBitmaps) {
        ((CMBitMap*) concurrent_mark()->prevMarkBitMap())->clearAll();
      }
      check_bitmaps("Full GC End");

      // Start a new incremental collection set for the next pause
      assert(g1_policy()->collection_set() == NULL, "must be");
      g1_policy()->start_incremental_cset_building();

      clear_cset_fast_test();

      _allocator->init_mutator_alloc_region();

      g1_policy()->record_full_collection_end();

      if (G1Log::fine()) {
        g1_policy()->print_heap_transition();
      }

      // We must call G1MonitoringSupport::update_sizes() in the same scoping level
      // as an active TraceMemoryManagerStats object (i.e. before the destructor for the
      // TraceMemoryManagerStats is called) so that the G1 memory pools are updated
      // before any GC notifications are raised.
      g1mm()->update_sizes();

      gc_epilogue(true);
    }

    if (G1Log::finer()) {
      g1_policy()->print_detailed_heap_transition(true /* full */);
    }

    print_heap_after_gc();
    trace_heap_after_gc(gc_tracer);

    post_full_gc_dump(gc_timer);

    gc_timer->register_gc_end();
    gc_tracer->report_gc_end(gc_timer->gc_end(), gc_timer->time_partitions());
  }

  return true;
}

void G1CollectedHeap::do_full_collection(bool clear_all_soft_refs) {
  // Currently, there is no facility in the do_full_collection(bool) API to notify
  // the caller that the collection did not succeed (e.g., because it was locked
  // out by the GC locker). So, right now, we'll ignore the return value.
  bool dummy = do_full_collection(true,                /* explicit_gc */
                                  clear_all_soft_refs);
}

void G1CollectedHeap::resize_if_necessary_after_full_collection() {
  // Include bytes that will be pre-allocated to support collections, as "used".
  const size_t used_after_gc = used();
  const size_t capacity_after_gc = capacity();
  const size_t free_after_gc = capacity_after_gc - used_after_gc;

  // This is enforced in arguments.cpp.
  assert(MinHeapFreeRatio <= MaxHeapFreeRatio,
         "otherwise the code below doesn't make sense");

  // We don't have floating point command-line arguments
  const double minimum_free_percentage = (double) MinHeapFreeRatio / 100.0;
  const double maximum_used_percentage = 1.0 - minimum_free_percentage;
  const double maximum_free_percentage = (double) MaxHeapFreeRatio / 100.0;
  const double minimum_used_percentage = 1.0 - maximum_free_percentage;

  const size_t min_heap_size = collector_policy()->min_heap_byte_size();
  const size_t max_heap_size = collector_policy()->max_heap_byte_size();

  // We have to be careful here as these two calculations can overflow
  // 32-bit size_t's.
  double used_after_gc_d = (double) used_after_gc;
  double minimum_desired_capacity_d = used_after_gc_d / maximum_used_percentage;
  double maximum_desired_capacity_d = used_after_gc_d / minimum_used_percentage;

  // Let's make sure that they are both under the max heap size, which
  // by default will make them fit into a size_t.
  double desired_capacity_upper_bound = (double) max_heap_size;
  minimum_desired_capacity_d = MIN2(minimum_desired_capacity_d,
                                    desired_capacity_upper_bound);
  maximum_desired_capacity_d = MIN2(maximum_desired_capacity_d,
                                    desired_capacity_upper_bound);

  // We can now safely turn them into size_t's.
  size_t minimum_desired_capacity = (size_t) minimum_desired_capacity_d;
  size_t maximum_desired_capacity = (size_t) maximum_desired_capacity_d;

  // This assert only makes sense here, before we adjust them
  // with respect to the min and max heap size.
  assert(minimum_desired_capacity <= maximum_desired_capacity,
         "minimum_desired_capacity = " SIZE_FORMAT ", "
         "maximum_desired_capacity = " SIZE_FORMAT,
         minimum_desired_capacity, maximum_desired_capacity);

  // Should not be greater than the heap max size. No need to adjust
  // it with respect to the heap min size as it's a lower bound (i.e.,
  // we'll try to make the capacity larger than it, not smaller).
  minimum_desired_capacity = MIN2(minimum_desired_capacity, max_heap_size);
  // Should not be less than the heap min size. No need to adjust it
  // with respect to the heap max size as it's an upper bound (i.e.,
  // we'll try to make the capacity smaller than it, not greater).
  maximum_desired_capacity =  MAX2(maximum_desired_capacity, min_heap_size);

  if (capacity_after_gc < minimum_desired_capacity) {
    // Don't expand unless it's significant
    size_t expand_bytes = minimum_desired_capacity - capacity_after_gc;
    ergo_verbose4(ErgoHeapSizing,
                  "attempt heap expansion",
                  ergo_format_reason("capacity lower than "
                                     "min desired capacity after Full GC")
                  ergo_format_byte("capacity")
                  ergo_format_byte("occupancy")
                  ergo_format_byte_perc("min desired capacity"),
                  capacity_after_gc, used_after_gc,
                  minimum_desired_capacity, (double) MinHeapFreeRatio);
    expand(expand_bytes);

    // No expansion, now see if we want to shrink
  } else if (capacity_after_gc > maximum_desired_capacity) {
    // Capacity too large, compute shrinking size
    size_t shrink_bytes = capacity_after_gc - maximum_desired_capacity;
    ergo_verbose4(ErgoHeapSizing,
                  "attempt heap shrinking",
                  ergo_format_reason("capacity higher than "
                                     "max desired capacity after Full GC")
                  ergo_format_byte("capacity")
                  ergo_format_byte("occupancy")
                  ergo_format_byte_perc("max desired capacity"),
                  capacity_after_gc, used_after_gc,
                  maximum_desired_capacity, (double) MaxHeapFreeRatio);
    shrink(shrink_bytes);
  }
}

HeapWord* G1CollectedHeap::satisfy_failed_allocation_helper(size_t word_size,
                                                            AllocationContext_t context,
                                                            bool do_gc,
                                                            bool clear_all_soft_refs,
                                                            bool expect_null_mutator_alloc_region,
                                                            bool* gc_succeeded) {
  *gc_succeeded = true;
  // Let's attempt the allocation first.
  HeapWord* result =
    attempt_allocation_at_safepoint(word_size,
                                    context,
                                    expect_null_mutator_alloc_region);
  if (result != NULL) {
    assert(*gc_succeeded, "sanity");
    return result;
  }

  // In a G1 heap, we're supposed to keep allocation from failing by
  // incremental pauses.  Therefore, at least for now, we'll favor
  // expansion over collection.  (This might change in the future if we can
  // do something smarter than full collection to satisfy a failed alloc.)
  result = expand_and_allocate(word_size, context);
  if (result != NULL) {
    assert(*gc_succeeded, "sanity");
    return result;
  }

  if (do_gc) {
    // Expansion didn't work, we'll try to do a Full GC.
    *gc_succeeded = do_full_collection(false, /* explicit_gc */
                                       clear_all_soft_refs);
  }

  return NULL;
}

HeapWord* G1CollectedHeap::satisfy_failed_allocation(size_t word_size,
                                                     AllocationContext_t context,
                                                     bool* succeeded) {
  assert_at_safepoint(true /* should_be_vm_thread */);

  // Attempts to allocate followed by Full GC.
  HeapWord* result =
    satisfy_failed_allocation_helper(word_size,
                                     context,
                                     true,  /* do_gc */
                                     false, /* clear_all_soft_refs */
                                     false, /* expect_null_mutator_alloc_region */
                                     succeeded);

  if (result != NULL || !*succeeded) {
    return result;
  }

  // Attempts to allocate followed by Full GC that will collect all soft references.
  result = satisfy_failed_allocation_helper(word_size,
                                            context,
                                            true, /* do_gc */
                                            true, /* clear_all_soft_refs */
                                            true, /* expect_null_mutator_alloc_region */
                                            succeeded);

  if (result != NULL || !*succeeded) {
    return result;
  }

  // Attempts to allocate, no GC
  result = satisfy_failed_allocation_helper(word_size,
                                            context,
                                            false, /* do_gc */
                                            false, /* clear_all_soft_refs */
                                            true,  /* expect_null_mutator_alloc_region */
                                            succeeded);

  if (result != NULL) {
    assert(*succeeded, "sanity");
    return result;
  }

  assert(!collector_policy()->should_clear_all_soft_refs(),
         "Flag should have been handled and cleared prior to this point");

  // What else?  We might try synchronous finalization later.  If the total
  // space available is large enough for the allocation, then a more
  // complete compaction phase than we've tried so far might be
  // appropriate.
  assert(*succeeded, "sanity");
  return NULL;
}

// Attempting to expand the heap sufficiently
// to support an allocation of the given "word_size".  If
// successful, perform the allocation and return the address of the
// allocated block, or else "NULL".

HeapWord* G1CollectedHeap::expand_and_allocate(size_t word_size, AllocationContext_t context) {
  assert_at_safepoint(true /* should_be_vm_thread */);

  verify_region_sets_optional();

  size_t expand_bytes = MAX2(word_size * HeapWordSize, MinHeapDeltaBytes);
  ergo_verbose1(ErgoHeapSizing,
                "attempt heap expansion",
                ergo_format_reason("allocation request failed")
                ergo_format_byte("allocation request"),
                word_size * HeapWordSize);
  if (expand(expand_bytes)) {
    _hrm.verify_optional();
    verify_region_sets_optional();
    return attempt_allocation_at_safepoint(word_size,
                                           context,
                                           false /* expect_null_mutator_alloc_region */);
  }
  return NULL;
}

bool G1CollectedHeap::expand(size_t expand_bytes, double* expand_time_ms) {
  size_t aligned_expand_bytes = ReservedSpace::page_align_size_up(expand_bytes);
  aligned_expand_bytes = align_size_up(aligned_expand_bytes,
                                       HeapRegion::GrainBytes);
  ergo_verbose2(ErgoHeapSizing,
                "expand the heap",
                ergo_format_byte("requested expansion amount")
                ergo_format_byte("attempted expansion amount"),
                expand_bytes, aligned_expand_bytes);

  if (is_maximal_no_gc()) {
    ergo_verbose0(ErgoHeapSizing,
                      "did not expand the heap",
                      ergo_format_reason("heap already fully expanded"));
    return false;
  }

  double expand_heap_start_time_sec = os::elapsedTime();
  uint regions_to_expand = (uint)(aligned_expand_bytes / HeapRegion::GrainBytes);
  assert(regions_to_expand > 0, "Must expand by at least one region");

  uint expanded_by = _hrm.expand_by(regions_to_expand);
  if (expand_time_ms != NULL) {
    *expand_time_ms = (os::elapsedTime() - expand_heap_start_time_sec) * MILLIUNITS;
  }

  if (expanded_by > 0) {
    size_t actual_expand_bytes = expanded_by * HeapRegion::GrainBytes;
    assert(actual_expand_bytes <= aligned_expand_bytes, "post-condition");
    g1_policy()->record_new_heap_size(num_regions());
  } else {
    ergo_verbose0(ErgoHeapSizing,
                  "did not expand the heap",
                  ergo_format_reason("heap expansion operation failed"));
    // The expansion of the virtual storage space was unsuccessful.
    // Let's see if it was because we ran out of swap.
    if (G1ExitOnExpansionFailure &&
        _hrm.available() >= regions_to_expand) {
      // We had head room...
      vm_exit_out_of_memory(aligned_expand_bytes, OOM_MMAP_ERROR, "G1 heap expansion");
    }
  }
  return regions_to_expand > 0;
}

void G1CollectedHeap::shrink_helper(size_t shrink_bytes) {
  size_t aligned_shrink_bytes =
    ReservedSpace::page_align_size_down(shrink_bytes);
  aligned_shrink_bytes = align_size_down(aligned_shrink_bytes,
                                         HeapRegion::GrainBytes);
  uint num_regions_to_remove = (uint)(shrink_bytes / HeapRegion::GrainBytes);

  uint num_regions_removed = _hrm.shrink_by(num_regions_to_remove);
  size_t shrunk_bytes = num_regions_removed * HeapRegion::GrainBytes;

  ergo_verbose3(ErgoHeapSizing,
                "shrink the heap",
                ergo_format_byte("requested shrinking amount")
                ergo_format_byte("aligned shrinking amount")
                ergo_format_byte("attempted shrinking amount"),
                shrink_bytes, aligned_shrink_bytes, shrunk_bytes);
  if (num_regions_removed > 0) {
    g1_policy()->record_new_heap_size(num_regions());
  } else {
    ergo_verbose0(ErgoHeapSizing,
                  "did not shrink the heap",
                  ergo_format_reason("heap shrinking operation failed"));
  }
}

void G1CollectedHeap::shrink(size_t shrink_bytes) {
  verify_region_sets_optional();

  // We should only reach here at the end of a Full GC which means we
  // should not not be holding to any GC alloc regions. The method
  // below will make sure of that and do any remaining clean up.
  _allocator->abandon_gc_alloc_regions();

  // Instead of tearing down / rebuilding the free lists here, we
  // could instead use the remove_all_pending() method on free_list to
  // remove only the ones that we need to remove.
  tear_down_region_sets(true /* free_list_only */);
  shrink_helper(shrink_bytes);
  rebuild_region_sets(true /* free_list_only */);

  _hrm.verify_optional();
  verify_region_sets_optional();
}

// Public methods.

G1CollectedHeap::G1CollectedHeap(G1CollectorPolicy* policy_) :
  CollectedHeap(),
  _g1_policy(policy_),
  _dirty_card_queue_set(false),
  _is_alive_closure_cm(this),
  _is_alive_closure_stw(this),
  _ref_processor_cm(NULL),
  _ref_processor_stw(NULL),
  _bot_shared(NULL),
  _cg1r(NULL),
  _g1mm(NULL),
  _refine_cte_cl(NULL),
  _secondary_free_list("Secondary Free List", new SecondaryFreeRegionListMtSafeChecker()),
  _old_set("Old Set", false /* humongous */, new OldRegionSetMtSafeChecker()),
  _humongous_set("Master Humongous Set", true /* humongous */, new HumongousRegionSetMtSafeChecker()),
  _humongous_reclaim_candidates(),
  _has_humongous_reclaim_candidates(false),
  _archive_allocator(NULL),
  _free_regions_coming(false),
  _young_list(new YoungList(this)),
  _gc_time_stamp(0),
  _summary_bytes_used(0),
  _survivor_evac_stats(YoungPLABSize, PLABWeight),
  _old_evac_stats(OldPLABSize, PLABWeight),
  _expand_heap_after_alloc_failure(true),
  _old_marking_cycles_started(0),
  _old_marking_cycles_completed(0),
  _heap_summary_sent(false),
  _in_cset_fast_test(),
  _dirty_cards_region_list(NULL),
  _worker_cset_start_region(NULL),
  _worker_cset_start_region_time_stamp(NULL),
  _gc_timer_stw(new (ResourceObj::C_HEAP, mtGC) STWGCTimer()),
  _gc_timer_cm(new (ResourceObj::C_HEAP, mtGC) ConcurrentGCTimer()),
  _gc_tracer_stw(new (ResourceObj::C_HEAP, mtGC) G1NewTracer()),
  _gc_tracer_cm(new (ResourceObj::C_HEAP, mtGC) G1OldTracer()) {

  _workers = new WorkGang("GC Thread", ParallelGCThreads,
                          /* are_GC_task_threads */true,
                          /* are_ConcurrentGC_threads */false);
  _workers->initialize_workers();

  _allocator = G1Allocator::create_allocator(this);
  _humongous_object_threshold_in_words = humongous_threshold_for(HeapRegion::GrainWords);

  // Override the default _filler_array_max_size so that no humongous filler
  // objects are created.
  _filler_array_max_size = _humongous_object_threshold_in_words;

  uint n_queues = ParallelGCThreads;
  _task_queues = new RefToScanQueueSet(n_queues);

  uint n_rem_sets = HeapRegionRemSet::num_par_rem_sets();
  assert(n_rem_sets > 0, "Invariant.");

  _worker_cset_start_region = NEW_C_HEAP_ARRAY(HeapRegion*, n_queues, mtGC);
  _worker_cset_start_region_time_stamp = NEW_C_HEAP_ARRAY(uint, n_queues, mtGC);
  _evacuation_failed_info_array = NEW_C_HEAP_ARRAY(EvacuationFailedInfo, n_queues, mtGC);

  for (uint i = 0; i < n_queues; i++) {
    RefToScanQueue* q = new RefToScanQueue();
    q->initialize();
    _task_queues->register_queue(i, q);
    ::new (&_evacuation_failed_info_array[i]) EvacuationFailedInfo();
  }
  clear_cset_start_regions();

  // Initialize the G1EvacuationFailureALot counters and flags.
  NOT_PRODUCT(reset_evacuation_should_fail();)

  guarantee(_task_queues != NULL, "task_queues allocation failure.");
}

G1RegionToSpaceMapper* G1CollectedHeap::create_aux_memory_mapper(const char* description,
                                                                 size_t size,
                                                                 size_t translation_factor) {
  size_t preferred_page_size = os::page_size_for_region_unaligned(size, 1);
  // Allocate a new reserved space, preferring to use large pages.
  ReservedSpace rs(size, preferred_page_size);
  G1RegionToSpaceMapper* result  =
    G1RegionToSpaceMapper::create_mapper(rs,
                                         size,
                                         rs.alignment(),
                                         HeapRegion::GrainBytes,
                                         translation_factor,
                                         mtGC);
  if (TracePageSizes) {
    gclog_or_tty->print_cr("G1 '%s': pg_sz=" SIZE_FORMAT " base=" PTR_FORMAT " size=" SIZE_FORMAT " alignment=" SIZE_FORMAT " reqsize=" SIZE_FORMAT,
                           description, preferred_page_size, p2i(rs.base()), rs.size(), rs.alignment(), size);
  }
  return result;
}

jint G1CollectedHeap::initialize() {
  CollectedHeap::pre_initialize();
  os::enable_vtime();

  G1Log::init();

  // Necessary to satisfy locking discipline assertions.

  MutexLocker x(Heap_lock);

  // We have to initialize the printer before committing the heap, as
  // it will be used then.
  _hr_printer.set_active(G1PrintHeapRegions);

  // While there are no constraints in the GC code that HeapWordSize
  // be any particular value, there are multiple other areas in the
  // system which believe this to be true (e.g. oop->object_size in some
  // cases incorrectly returns the size in wordSize units rather than
  // HeapWordSize).
  guarantee(HeapWordSize == wordSize, "HeapWordSize must equal wordSize");

  size_t init_byte_size = collector_policy()->initial_heap_byte_size();
  size_t max_byte_size = collector_policy()->max_heap_byte_size();
  size_t heap_alignment = collector_policy()->heap_alignment();

  // Ensure that the sizes are properly aligned.
  Universe::check_alignment(init_byte_size, HeapRegion::GrainBytes, "g1 heap");
  Universe::check_alignment(max_byte_size, HeapRegion::GrainBytes, "g1 heap");
  Universe::check_alignment(max_byte_size, heap_alignment, "g1 heap");

  _refine_cte_cl = new RefineCardTableEntryClosure();

  jint ecode = JNI_OK;
  _cg1r = ConcurrentG1Refine::create(this, _refine_cte_cl, &ecode);
  if (_cg1r == NULL) {
    return ecode;
  }

  // Reserve the maximum.

  // When compressed oops are enabled, the preferred heap base
  // is calculated by subtracting the requested size from the
  // 32Gb boundary and using the result as the base address for
  // heap reservation. If the requested size is not aligned to
  // HeapRegion::GrainBytes (i.e. the alignment that is passed
  // into the ReservedHeapSpace constructor) then the actual
  // base of the reserved heap may end up differing from the
  // address that was requested (i.e. the preferred heap base).
  // If this happens then we could end up using a non-optimal
  // compressed oops mode.

  ReservedSpace heap_rs = Universe::reserve_heap(max_byte_size,
                                                 heap_alignment);

  initialize_reserved_region((HeapWord*)heap_rs.base(), (HeapWord*)(heap_rs.base() + heap_rs.size()));

  // Create the barrier set for the entire reserved region.
  G1SATBCardTableLoggingModRefBS* bs
    = new G1SATBCardTableLoggingModRefBS(reserved_region());
  bs->initialize();
  assert(bs->is_a(BarrierSet::G1SATBCTLogging), "sanity");
  set_barrier_set(bs);

  // Also create a G1 rem set.
  _g1_rem_set = new G1RemSet(this, g1_barrier_set());

  // Carve out the G1 part of the heap.

  ReservedSpace g1_rs = heap_rs.first_part(max_byte_size);
  size_t page_size = UseLargePages ? os::large_page_size() : os::vm_page_size();
  G1RegionToSpaceMapper* heap_storage =
    G1RegionToSpaceMapper::create_mapper(g1_rs,
                                         g1_rs.size(),
                                         page_size,
                                         HeapRegion::GrainBytes,
                                         1,
                                         mtJavaHeap);
  os::trace_page_sizes("G1 Heap", collector_policy()->min_heap_byte_size(),
                       max_byte_size, page_size,
                       heap_rs.base(),
                       heap_rs.size());
  heap_storage->set_mapping_changed_listener(&_listener);

  // Create storage for the BOT, card table, card counts table (hot card cache) and the bitmaps.
  G1RegionToSpaceMapper* bot_storage =
    create_aux_memory_mapper("Block offset table",
                             G1BlockOffsetSharedArray::compute_size(g1_rs.size() / HeapWordSize),
                             G1BlockOffsetSharedArray::heap_map_factor());

  ReservedSpace cardtable_rs(G1SATBCardTableLoggingModRefBS::compute_size(g1_rs.size() / HeapWordSize));
  G1RegionToSpaceMapper* cardtable_storage =
    create_aux_memory_mapper("Card table",
                             G1SATBCardTableLoggingModRefBS::compute_size(g1_rs.size() / HeapWordSize),
                             G1SATBCardTableLoggingModRefBS::heap_map_factor());

  G1RegionToSpaceMapper* card_counts_storage =
    create_aux_memory_mapper("Card counts table",
                             G1CardCounts::compute_size(g1_rs.size() / HeapWordSize),
                             G1CardCounts::heap_map_factor());

  size_t bitmap_size = CMBitMap::compute_size(g1_rs.size());
  G1RegionToSpaceMapper* prev_bitmap_storage =
    create_aux_memory_mapper("Prev Bitmap", bitmap_size, CMBitMap::heap_map_factor());
  G1RegionToSpaceMapper* next_bitmap_storage =
    create_aux_memory_mapper("Next Bitmap", bitmap_size, CMBitMap::heap_map_factor());

  _hrm.initialize(heap_storage, prev_bitmap_storage, next_bitmap_storage, bot_storage, cardtable_storage, card_counts_storage);
  g1_barrier_set()->initialize(cardtable_storage);
   // Do later initialization work for concurrent refinement.
  _cg1r->init(card_counts_storage);

  // 6843694 - ensure that the maximum region index can fit
  // in the remembered set structures.
  const uint max_region_idx = (1U << (sizeof(RegionIdx_t)*BitsPerByte-1)) - 1;
  guarantee((max_regions() - 1) <= max_region_idx, "too many regions");

  size_t max_cards_per_region = ((size_t)1 << (sizeof(CardIdx_t)*BitsPerByte-1)) - 1;
  guarantee(HeapRegion::CardsPerRegion > 0, "make sure it's initialized");
  guarantee(HeapRegion::CardsPerRegion < max_cards_per_region,
            "too many cards per region");

  FreeRegionList::set_unrealistically_long_length(max_regions() + 1);

  _bot_shared = new G1BlockOffsetSharedArray(reserved_region(), bot_storage);

  {
    HeapWord* start = _hrm.reserved().start();
    HeapWord* end = _hrm.reserved().end();
    size_t granularity = HeapRegion::GrainBytes;

    _in_cset_fast_test.initialize(start, end, granularity);
    _humongous_reclaim_candidates.initialize(start, end, granularity);
  }

  // Create the ConcurrentMark data structure and thread.
  // (Must do this late, so that "max_regions" is defined.)
  _cm = new ConcurrentMark(this, prev_bitmap_storage, next_bitmap_storage);
  if (_cm == NULL || !_cm->completed_initialization()) {
    vm_shutdown_during_initialization("Could not create/initialize ConcurrentMark");
    return JNI_ENOMEM;
  }
  _cmThread = _cm->cmThread();

  // Initialize the from_card cache structure of HeapRegionRemSet.
  HeapRegionRemSet::init_heap(max_regions());

  // Now expand into the initial heap size.
  if (!expand(init_byte_size)) {
    vm_shutdown_during_initialization("Failed to allocate initial heap.");
    return JNI_ENOMEM;
  }

  // Perform any initialization actions delegated to the policy.
  g1_policy()->init();

  JavaThread::satb_mark_queue_set().initialize(SATB_Q_CBL_mon,
                                               SATB_Q_FL_lock,
                                               G1SATBProcessCompletedThreshold,
                                               Shared_SATB_Q_lock);

  JavaThread::dirty_card_queue_set().initialize(_refine_cte_cl,
                                                DirtyCardQ_CBL_mon,
                                                DirtyCardQ_FL_lock,
                                                concurrent_g1_refine()->yellow_zone(),
                                                concurrent_g1_refine()->red_zone(),
                                                Shared_DirtyCardQ_lock);

  dirty_card_queue_set().initialize(NULL, // Should never be called by the Java code
                                    DirtyCardQ_CBL_mon,
                                    DirtyCardQ_FL_lock,
                                    -1, // never trigger processing
                                    -1, // no limit on length
                                    Shared_DirtyCardQ_lock,
                                    &JavaThread::dirty_card_queue_set());

  // Here we allocate the dummy HeapRegion that is required by the
  // G1AllocRegion class.
  HeapRegion* dummy_region = _hrm.get_dummy_region();

  // We'll re-use the same region whether the alloc region will
  // require BOT updates or not and, if it doesn't, then a non-young
  // region will complain that it cannot support allocations without
  // BOT updates. So we'll tag the dummy region as eden to avoid that.
  dummy_region->set_eden();
  // Make sure it's full.
  dummy_region->set_top(dummy_region->end());
  G1AllocRegion::setup(this, dummy_region);

  _allocator->init_mutator_alloc_region();

  // Do create of the monitoring and management support so that
  // values in the heap have been properly initialized.
  _g1mm = new G1MonitoringSupport(this);

  G1StringDedup::initialize();

  _preserved_objs = NEW_C_HEAP_ARRAY(OopAndMarkOopStack, ParallelGCThreads, mtGC);
  for (uint i = 0; i < ParallelGCThreads; i++) {
    new (&_preserved_objs[i]) OopAndMarkOopStack();
  }

  return JNI_OK;
}

void G1CollectedHeap::stop() {
  // Stop all concurrent threads. We do this to make sure these threads
  // do not continue to execute and access resources (e.g. gclog_or_tty)
  // that are destroyed during shutdown.
  _cg1r->stop();
  _cmThread->stop();
  if (G1StringDedup::is_enabled()) {
    G1StringDedup::stop();
  }
}

size_t G1CollectedHeap::conservative_max_heap_alignment() {
  return HeapRegion::max_region_size();
}

void G1CollectedHeap::post_initialize() {
  CollectedHeap::post_initialize();
  ref_processing_init();
}

void G1CollectedHeap::ref_processing_init() {
  // Reference processing in G1 currently works as follows:
  //
  // * There are two reference processor instances. One is
  //   used to record and process discovered references
  //   during concurrent marking; the other is used to
  //   record and process references during STW pauses
  //   (both full and incremental).
  // * Both ref processors need to 'span' the entire heap as
  //   the regions in the collection set may be dotted around.
  //
  // * For the concurrent marking ref processor:
  //   * Reference discovery is enabled at initial marking.
  //   * Reference discovery is disabled and the discovered
  //     references processed etc during remarking.
  //   * Reference discovery is MT (see below).
  //   * Reference discovery requires a barrier (see below).
  //   * Reference processing may or may not be MT
  //     (depending on the value of ParallelRefProcEnabled
  //     and ParallelGCThreads).
  //   * A full GC disables reference discovery by the CM
  //     ref processor and abandons any entries on it's
  //     discovered lists.
  //
  // * For the STW processor:
  //   * Non MT discovery is enabled at the start of a full GC.
  //   * Processing and enqueueing during a full GC is non-MT.
  //   * During a full GC, references are processed after marking.
  //
  //   * Discovery (may or may not be MT) is enabled at the start
  //     of an incremental evacuation pause.
  //   * References are processed near the end of a STW evacuation pause.
  //   * For both types of GC:
  //     * Discovery is atomic - i.e. not concurrent.
  //     * Reference discovery will not need a barrier.

  MemRegion mr = reserved_region();

  // Concurrent Mark ref processor
  _ref_processor_cm =
    new ReferenceProcessor(mr,    // span
                           ParallelRefProcEnabled && (ParallelGCThreads > 1),
                                // mt processing
                           ParallelGCThreads,
                                // degree of mt processing
                           (ParallelGCThreads > 1) || (ConcGCThreads > 1),
                                // mt discovery
                           MAX2(ParallelGCThreads, ConcGCThreads),
                                // degree of mt discovery
                           false,
                                // Reference discovery is not atomic
                           &_is_alive_closure_cm);
                                // is alive closure
                                // (for efficiency/performance)

  // STW ref processor
  _ref_processor_stw =
    new ReferenceProcessor(mr,    // span
                           ParallelRefProcEnabled && (ParallelGCThreads > 1),
                                // mt processing
                           ParallelGCThreads,
                                // degree of mt processing
                           (ParallelGCThreads > 1),
                                // mt discovery
                           ParallelGCThreads,
                                // degree of mt discovery
                           true,
                                // Reference discovery is atomic
                           &_is_alive_closure_stw);
                                // is alive closure
                                // (for efficiency/performance)
}

CollectorPolicy* G1CollectedHeap::collector_policy() const {
  return g1_policy();
}

size_t G1CollectedHeap::capacity() const {
  return _hrm.length() * HeapRegion::GrainBytes;
}

void G1CollectedHeap::reset_gc_time_stamps(HeapRegion* hr) {
  hr->reset_gc_time_stamp();
}

#ifndef PRODUCT

class CheckGCTimeStampsHRClosure : public HeapRegionClosure {
private:
  unsigned _gc_time_stamp;
  bool _failures;

public:
  CheckGCTimeStampsHRClosure(unsigned gc_time_stamp) :
    _gc_time_stamp(gc_time_stamp), _failures(false) { }

  virtual bool doHeapRegion(HeapRegion* hr) {
    unsigned region_gc_time_stamp = hr->get_gc_time_stamp();
    if (_gc_time_stamp != region_gc_time_stamp) {
      gclog_or_tty->print_cr("Region " HR_FORMAT " has GC time stamp = %d, "
                             "expected %d", HR_FORMAT_PARAMS(hr),
                             region_gc_time_stamp, _gc_time_stamp);
      _failures = true;
    }
    return false;
  }

  bool failures() { return _failures; }
};

void G1CollectedHeap::check_gc_time_stamps() {
  CheckGCTimeStampsHRClosure cl(_gc_time_stamp);
  heap_region_iterate(&cl);
  guarantee(!cl.failures(), "all GC time stamps should have been reset");
}
#endif // PRODUCT

void G1CollectedHeap::iterate_hcc_closure(CardTableEntryClosure* cl, uint worker_i) {
  _cg1r->hot_card_cache()->drain(cl, worker_i);
}

void G1CollectedHeap::iterate_dirty_card_closure(CardTableEntryClosure* cl, uint worker_i) {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  size_t n_completed_buffers = 0;
  while (dcqs.apply_closure_to_completed_buffer(cl, worker_i, 0, true)) {
    n_completed_buffers++;
  }
  g1_policy()->phase_times()->record_thread_work_item(G1GCPhaseTimes::UpdateRS, worker_i, n_completed_buffers);
  dcqs.clear_n_completed_buffers();
  assert(!dcqs.completed_buffers_exist_dirty(), "Completed buffers exist!");
}

// Computes the sum of the storage used by the various regions.
size_t G1CollectedHeap::used() const {
  size_t result = _summary_bytes_used + _allocator->used_in_alloc_regions();
  if (_archive_allocator != NULL) {
    result += _archive_allocator->used();
  }
  return result;
}

size_t G1CollectedHeap::used_unlocked() const {
  return _summary_bytes_used;
}

class SumUsedClosure: public HeapRegionClosure {
  size_t _used;
public:
  SumUsedClosure() : _used(0) {}
  bool doHeapRegion(HeapRegion* r) {
    _used += r->used();
    return false;
  }
  size_t result() { return _used; }
};

size_t G1CollectedHeap::recalculate_used() const {
  double recalculate_used_start = os::elapsedTime();

  SumUsedClosure blk;
  heap_region_iterate(&blk);

  g1_policy()->phase_times()->record_evac_fail_recalc_used_time((os::elapsedTime() - recalculate_used_start) * 1000.0);
  return blk.result();
}

bool G1CollectedHeap::should_do_concurrent_full_gc(GCCause::Cause cause) {
  switch (cause) {
    case GCCause::_gc_locker:               return GCLockerInvokesConcurrent;
    case GCCause::_java_lang_system_gc:     return ExplicitGCInvokesConcurrent;
    case GCCause::_dcmd_gc_run:             return ExplicitGCInvokesConcurrent;
    case GCCause::_g1_humongous_allocation: return true;
    case GCCause::_update_allocation_context_stats_inc: return true;
    case GCCause::_wb_conc_mark:            return true;
    default:                                return false;
  }
}

#ifndef PRODUCT
void G1CollectedHeap::allocate_dummy_regions() {
  // Let's fill up most of the region
  size_t word_size = HeapRegion::GrainWords - 1024;
  // And as a result the region we'll allocate will be humongous.
  guarantee(is_humongous(word_size), "sanity");

  // _filler_array_max_size is set to humongous object threshold
  // but temporarily change it to use CollectedHeap::fill_with_object().
  SizeTFlagSetting fs(_filler_array_max_size, word_size);

  for (uintx i = 0; i < G1DummyRegionsPerGC; ++i) {
    // Let's use the existing mechanism for the allocation
    HeapWord* dummy_obj = humongous_obj_allocate(word_size,
                                                 AllocationContext::system());
    if (dummy_obj != NULL) {
      MemRegion mr(dummy_obj, word_size);
      CollectedHeap::fill_with_object(mr);
    } else {
      // If we can't allocate once, we probably cannot allocate
      // again. Let's get out of the loop.
      break;
    }
  }
}
#endif // !PRODUCT

void G1CollectedHeap::increment_old_marking_cycles_started() {
  assert(_old_marking_cycles_started == _old_marking_cycles_completed ||
         _old_marking_cycles_started == _old_marking_cycles_completed + 1,
         "Wrong marking cycle count (started: %d, completed: %d)",
         _old_marking_cycles_started, _old_marking_cycles_completed);

  _old_marking_cycles_started++;
}

void G1CollectedHeap::increment_old_marking_cycles_completed(bool concurrent) {
  MonitorLockerEx x(FullGCCount_lock, Mutex::_no_safepoint_check_flag);

  // We assume that if concurrent == true, then the caller is a
  // concurrent thread that was joined the Suspendible Thread
  // Set. If there's ever a cheap way to check this, we should add an
  // assert here.

  // Given that this method is called at the end of a Full GC or of a
  // concurrent cycle, and those can be nested (i.e., a Full GC can
  // interrupt a concurrent cycle), the number of full collections
  // completed should be either one (in the case where there was no
  // nesting) or two (when a Full GC interrupted a concurrent cycle)
  // behind the number of full collections started.

  // This is the case for the inner caller, i.e. a Full GC.
  assert(concurrent ||
         (_old_marking_cycles_started == _old_marking_cycles_completed + 1) ||
         (_old_marking_cycles_started == _old_marking_cycles_completed + 2),
         "for inner caller (Full GC): _old_marking_cycles_started = %u "
         "is inconsistent with _old_marking_cycles_completed = %u",
         _old_marking_cycles_started, _old_marking_cycles_completed);

  // This is the case for the outer caller, i.e. the concurrent cycle.
  assert(!concurrent ||
         (_old_marking_cycles_started == _old_marking_cycles_completed + 1),
         "for outer caller (concurrent cycle): "
         "_old_marking_cycles_started = %u "
         "is inconsistent with _old_marking_cycles_completed = %u",
         _old_marking_cycles_started, _old_marking_cycles_completed);

  _old_marking_cycles_completed += 1;

  // We need to clear the "in_progress" flag in the CM thread before
  // we wake up any waiters (especially when ExplicitInvokesConcurrent
  // is set) so that if a waiter requests another System.gc() it doesn't
  // incorrectly see that a marking cycle is still in progress.
  if (concurrent) {
    _cmThread->set_idle();
  }

  // This notify_all() will ensure that a thread that called
  // System.gc() with (with ExplicitGCInvokesConcurrent set or not)
  // and it's waiting for a full GC to finish will be woken up. It is
  // waiting in VM_G1IncCollectionPause::doit_epilogue().
  FullGCCount_lock->notify_all();
}

void G1CollectedHeap::register_concurrent_cycle_start(const Ticks& start_time) {
  GCIdMarkAndRestore conc_gc_id_mark;
  collector_state()->set_concurrent_cycle_started(true);
  _gc_timer_cm->register_gc_start(start_time);

  _gc_tracer_cm->report_gc_start(gc_cause(), _gc_timer_cm->gc_start());
  trace_heap_before_gc(_gc_tracer_cm);
  _cmThread->set_gc_id(GCId::current());
}

void G1CollectedHeap::register_concurrent_cycle_end() {
  if (collector_state()->concurrent_cycle_started()) {
    GCIdMarkAndRestore conc_gc_id_mark(_cmThread->gc_id());
    if (_cm->has_aborted()) {
      _gc_tracer_cm->report_concurrent_mode_failure();
    }

    _gc_timer_cm->register_gc_end();
    _gc_tracer_cm->report_gc_end(_gc_timer_cm->gc_end(), _gc_timer_cm->time_partitions());

    // Clear state variables to prepare for the next concurrent cycle.
     collector_state()->set_concurrent_cycle_started(false);
    _heap_summary_sent = false;
  }
}

void G1CollectedHeap::trace_heap_after_concurrent_cycle() {
  if (collector_state()->concurrent_cycle_started()) {
    // This function can be called when:
    //  the cleanup pause is run
    //  the concurrent cycle is aborted before the cleanup pause.
    //  the concurrent cycle is aborted after the cleanup pause,
    //   but before the concurrent cycle end has been registered.
    // Make sure that we only send the heap information once.
    if (!_heap_summary_sent) {
      GCIdMarkAndRestore conc_gc_id_mark(_cmThread->gc_id());
      trace_heap_after_gc(_gc_tracer_cm);
      _heap_summary_sent = true;
    }
  }
}

void G1CollectedHeap::collect(GCCause::Cause cause) {
  assert_heap_not_locked();

  uint gc_count_before;
  uint old_marking_count_before;
  uint full_gc_count_before;
  bool retry_gc;

  do {
    retry_gc = false;

    {
      MutexLocker ml(Heap_lock);

      // Read the GC count while holding the Heap_lock
      gc_count_before = total_collections();
      full_gc_count_before = total_full_collections();
      old_marking_count_before = _old_marking_cycles_started;
    }

    if (should_do_concurrent_full_gc(cause)) {
      // Schedule an initial-mark evacuation pause that will start a
      // concurrent cycle. We're setting word_size to 0 which means that
      // we are not requesting a post-GC allocation.
      VM_G1IncCollectionPause op(gc_count_before,
                                 0,     /* word_size */
                                 true,  /* should_initiate_conc_mark */
                                 g1_policy()->max_pause_time_ms(),
                                 cause);
      op.set_allocation_context(AllocationContext::current());

      VMThread::execute(&op);
      if (!op.pause_succeeded()) {
        if (old_marking_count_before == _old_marking_cycles_started) {
          retry_gc = op.should_retry_gc();
        } else {
          // A Full GC happened while we were trying to schedule the
          // initial-mark GC. No point in starting a new cycle given
          // that the whole heap was collected anyway.
        }

        if (retry_gc) {
          if (GC_locker::is_active_and_needs_gc()) {
            GC_locker::stall_until_clear();
          }
        }
      }
    } else {
      if (cause == GCCause::_gc_locker || cause == GCCause::_wb_young_gc
          DEBUG_ONLY(|| cause == GCCause::_scavenge_alot)) {

        // Schedule a standard evacuation pause. We're setting word_size
        // to 0 which means that we are not requesting a post-GC allocation.
        VM_G1IncCollectionPause op(gc_count_before,
                                   0,     /* word_size */
                                   false, /* should_initiate_conc_mark */
                                   g1_policy()->max_pause_time_ms(),
                                   cause);
        VMThread::execute(&op);
      } else {
        // Schedule a Full GC.
        VM_G1CollectFull op(gc_count_before, full_gc_count_before, cause);
        VMThread::execute(&op);
      }
    }
  } while (retry_gc);
}

bool G1CollectedHeap::is_in(const void* p) const {
  if (_hrm.reserved().contains(p)) {
    // Given that we know that p is in the reserved space,
    // heap_region_containing() should successfully
    // return the containing region.
    HeapRegion* hr = heap_region_containing(p);
    return hr->is_in(p);
  } else {
    return false;
  }
}

#ifdef ASSERT
bool G1CollectedHeap::is_in_exact(const void* p) const {
  bool contains = reserved_region().contains(p);
  bool available = _hrm.is_available(addr_to_region((HeapWord*)p));
  if (contains && available) {
    return true;
  } else {
    return false;
  }
}
#endif

bool G1CollectedHeap::obj_in_cs(oop obj) {
  HeapRegion* r = _hrm.addr_to_region((HeapWord*) obj);
  return r != NULL && r->in_collection_set();
}

// Iteration functions.

// Applies an ExtendedOopClosure onto all references of objects within a HeapRegion.

class IterateOopClosureRegionClosure: public HeapRegionClosure {
  ExtendedOopClosure* _cl;
public:
  IterateOopClosureRegionClosure(ExtendedOopClosure* cl) : _cl(cl) {}
  bool doHeapRegion(HeapRegion* r) {
    if (!r->is_continues_humongous()) {
      r->oop_iterate(_cl);
    }
    return false;
  }
};

// Iterates an ObjectClosure over all objects within a HeapRegion.

class IterateObjectClosureRegionClosure: public HeapRegionClosure {
  ObjectClosure* _cl;
public:
  IterateObjectClosureRegionClosure(ObjectClosure* cl) : _cl(cl) {}
  bool doHeapRegion(HeapRegion* r) {
    if (!r->is_continues_humongous()) {
      r->object_iterate(_cl);
    }
    return false;
  }
};

void G1CollectedHeap::object_iterate(ObjectClosure* cl) {
  IterateObjectClosureRegionClosure blk(cl);
  heap_region_iterate(&blk);
}

void G1CollectedHeap::heap_region_iterate(HeapRegionClosure* cl) const {
  _hrm.iterate(cl);
}

void
G1CollectedHeap::heap_region_par_iterate(HeapRegionClosure* cl,
                                         uint worker_id,
                                         HeapRegionClaimer *hrclaimer,
                                         bool concurrent) const {
  _hrm.par_iterate(cl, worker_id, hrclaimer, concurrent);
}

// Clear the cached CSet starting regions and (more importantly)
// the time stamps. Called when we reset the GC time stamp.
void G1CollectedHeap::clear_cset_start_regions() {
  assert(_worker_cset_start_region != NULL, "sanity");
  assert(_worker_cset_start_region_time_stamp != NULL, "sanity");

  for (uint i = 0; i < ParallelGCThreads; i++) {
    _worker_cset_start_region[i] = NULL;
    _worker_cset_start_region_time_stamp[i] = 0;
  }
}

// Given the id of a worker, obtain or calculate a suitable
// starting region for iterating over the current collection set.
HeapRegion* G1CollectedHeap::start_cset_region_for_worker(uint worker_i) {
  assert(get_gc_time_stamp() > 0, "should have been updated by now");

  HeapRegion* result = NULL;
  unsigned gc_time_stamp = get_gc_time_stamp();

  if (_worker_cset_start_region_time_stamp[worker_i] == gc_time_stamp) {
    // Cached starting region for current worker was set
    // during the current pause - so it's valid.
    // Note: the cached starting heap region may be NULL
    // (when the collection set is empty).
    result = _worker_cset_start_region[worker_i];
    assert(result == NULL || result->in_collection_set(), "sanity");
    return result;
  }

  // The cached entry was not valid so let's calculate
  // a suitable starting heap region for this worker.

  // We want the parallel threads to start their collection
  // set iteration at different collection set regions to
  // avoid contention.
  // If we have:
  //          n collection set regions
  //          p threads
  // Then thread t will start at region floor ((t * n) / p)

  result = g1_policy()->collection_set();
  uint cs_size = g1_policy()->cset_region_length();
  uint active_workers = workers()->active_workers();

  uint end_ind   = (cs_size * worker_i) / active_workers;
  uint start_ind = 0;

  if (worker_i > 0 &&
      _worker_cset_start_region_time_stamp[worker_i - 1] == gc_time_stamp) {
    // Previous workers starting region is valid
    // so let's iterate from there
    start_ind = (cs_size * (worker_i - 1)) / active_workers;
    result = _worker_cset_start_region[worker_i - 1];
  }

  for (uint i = start_ind; i < end_ind; i++) {
    result = result->next_in_collection_set();
  }

  // Note: the calculated starting heap region may be NULL
  // (when the collection set is empty).
  assert(result == NULL || result->in_collection_set(), "sanity");
  assert(_worker_cset_start_region_time_stamp[worker_i] != gc_time_stamp,
         "should be updated only once per pause");
  _worker_cset_start_region[worker_i] = result;
  OrderAccess::storestore();
  _worker_cset_start_region_time_stamp[worker_i] = gc_time_stamp;
  return result;
}

void G1CollectedHeap::collection_set_iterate(HeapRegionClosure* cl) {
  HeapRegion* r = g1_policy()->collection_set();
  while (r != NULL) {
    HeapRegion* next = r->next_in_collection_set();
    if (cl->doHeapRegion(r)) {
      cl->incomplete();
      return;
    }
    r = next;
  }
}

void G1CollectedHeap::collection_set_iterate_from(HeapRegion* r,
                                                  HeapRegionClosure *cl) {
  if (r == NULL) {
    // The CSet is empty so there's nothing to do.
    return;
  }

  assert(r->in_collection_set(),
         "Start region must be a member of the collection set.");
  HeapRegion* cur = r;
  while (cur != NULL) {
    HeapRegion* next = cur->next_in_collection_set();
    if (cl->doHeapRegion(cur) && false) {
      cl->incomplete();
      return;
    }
    cur = next;
  }
  cur = g1_policy()->collection_set();
  while (cur != r) {
    HeapRegion* next = cur->next_in_collection_set();
    if (cl->doHeapRegion(cur) && false) {
      cl->incomplete();
      return;
    }
    cur = next;
  }
}

HeapRegion* G1CollectedHeap::next_compaction_region(const HeapRegion* from) const {
  HeapRegion* result = _hrm.next_region_in_heap(from);
  while (result != NULL && result->is_pinned()) {
    result = _hrm.next_region_in_heap(result);
  }
  return result;
}

HeapWord* G1CollectedHeap::block_start(const void* addr) const {
  HeapRegion* hr = heap_region_containing(addr);
  return hr->block_start(addr);
}

size_t G1CollectedHeap::block_size(const HeapWord* addr) const {
  HeapRegion* hr = heap_region_containing(addr);
  return hr->block_size(addr);
}

bool G1CollectedHeap::block_is_obj(const HeapWord* addr) const {
  HeapRegion* hr = heap_region_containing(addr);
  return hr->block_is_obj(addr);
}

bool G1CollectedHeap::supports_tlab_allocation() const {
  return true;
}

size_t G1CollectedHeap::tlab_capacity(Thread* ignored) const {
  return (_g1_policy->young_list_target_length() - young_list()->survivor_length()) * HeapRegion::GrainBytes;
}

size_t G1CollectedHeap::tlab_used(Thread* ignored) const {
  return young_list()->eden_used_bytes();
}

// For G1 TLABs should not contain humongous objects, so the maximum TLAB size
// must be equal to the humongous object limit.
size_t G1CollectedHeap::max_tlab_size() const {
  return align_size_down(_humongous_object_threshold_in_words, MinObjAlignment);
}

size_t G1CollectedHeap::unsafe_max_tlab_alloc(Thread* ignored) const {
  AllocationContext_t context = AllocationContext::current();
  return _allocator->unsafe_max_tlab_alloc(context);
}

size_t G1CollectedHeap::max_capacity() const {
  return _hrm.reserved().byte_size();
}

jlong G1CollectedHeap::millis_since_last_gc() {
  // assert(false, "NYI");
  return 0;
}

void G1CollectedHeap::prepare_for_verify() {
  if (SafepointSynchronize::is_at_safepoint() || ! UseTLAB) {
    ensure_parsability(false);
  }
  g1_rem_set()->prepare_for_verify();
}

bool G1CollectedHeap::allocated_since_marking(oop obj, HeapRegion* hr,
                                              VerifyOption vo) {
  switch (vo) {
  case VerifyOption_G1UsePrevMarking:
    return hr->obj_allocated_since_prev_marking(obj);
  case VerifyOption_G1UseNextMarking:
    return hr->obj_allocated_since_next_marking(obj);
  case VerifyOption_G1UseMarkWord:
    return false;
  default:
    ShouldNotReachHere();
  }
  return false; // keep some compilers happy
}

HeapWord* G1CollectedHeap::top_at_mark_start(HeapRegion* hr, VerifyOption vo) {
  switch (vo) {
  case VerifyOption_G1UsePrevMarking: return hr->prev_top_at_mark_start();
  case VerifyOption_G1UseNextMarking: return hr->next_top_at_mark_start();
  case VerifyOption_G1UseMarkWord:    return NULL;
  default:                            ShouldNotReachHere();
  }
  return NULL; // keep some compilers happy
}

bool G1CollectedHeap::is_marked(oop obj, VerifyOption vo) {
  switch (vo) {
  case VerifyOption_G1UsePrevMarking: return isMarkedPrev(obj);
  case VerifyOption_G1UseNextMarking: return isMarkedNext(obj);
  case VerifyOption_G1UseMarkWord:    return obj->is_gc_marked();
  default:                            ShouldNotReachHere();
  }
  return false; // keep some compilers happy
}

const char* G1CollectedHeap::top_at_mark_start_str(VerifyOption vo) {
  switch (vo) {
  case VerifyOption_G1UsePrevMarking: return "PTAMS";
  case VerifyOption_G1UseNextMarking: return "NTAMS";
  case VerifyOption_G1UseMarkWord:    return "NONE";
  default:                            ShouldNotReachHere();
  }
  return NULL; // keep some compilers happy
}

class VerifyRootsClosure: public OopClosure {
private:
  G1CollectedHeap* _g1h;
  VerifyOption     _vo;
  bool             _failures;
public:
  // _vo == UsePrevMarking -> use "prev" marking information,
  // _vo == UseNextMarking -> use "next" marking information,
  // _vo == UseMarkWord    -> use mark word from object header.
  VerifyRootsClosure(VerifyOption vo) :
    _g1h(G1CollectedHeap::heap()),
    _vo(vo),
    _failures(false) { }

  bool failures() { return _failures; }

  template <class T> void do_oop_nv(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      if (_g1h->is_obj_dead_cond(obj, _vo)) {
        gclog_or_tty->print_cr("Root location " PTR_FORMAT " "
                               "points to dead obj " PTR_FORMAT, p2i(p), p2i(obj));
        if (_vo == VerifyOption_G1UseMarkWord) {
          gclog_or_tty->print_cr("  Mark word: " INTPTR_FORMAT, (intptr_t)obj->mark());
        }
        obj->print_on(gclog_or_tty);
        _failures = true;
      }
    }
  }

  void do_oop(oop* p)       { do_oop_nv(p); }
  void do_oop(narrowOop* p) { do_oop_nv(p); }
};

class G1VerifyCodeRootOopClosure: public OopClosure {
  G1CollectedHeap* _g1h;
  OopClosure* _root_cl;
  nmethod* _nm;
  VerifyOption _vo;
  bool _failures;

  template <class T> void do_oop_work(T* p) {
    // First verify that this root is live
    _root_cl->do_oop(p);

    if (!G1VerifyHeapRegionCodeRoots) {
      // We're not verifying the code roots attached to heap region.
      return;
    }

    // Don't check the code roots during marking verification in a full GC
    if (_vo == VerifyOption_G1UseMarkWord) {
      return;
    }

    // Now verify that the current nmethod (which contains p) is
    // in the code root list of the heap region containing the
    // object referenced by p.

    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);

      // Now fetch the region containing the object
      HeapRegion* hr = _g1h->heap_region_containing(obj);
      HeapRegionRemSet* hrrs = hr->rem_set();
      // Verify that the strong code root list for this region
      // contains the nmethod
      if (!hrrs->strong_code_roots_list_contains(_nm)) {
        gclog_or_tty->print_cr("Code root location " PTR_FORMAT " "
                               "from nmethod " PTR_FORMAT " not in strong "
                               "code roots for region [" PTR_FORMAT "," PTR_FORMAT ")",
                               p2i(p), p2i(_nm), p2i(hr->bottom()), p2i(hr->end()));
        _failures = true;
      }
    }
  }

public:
  G1VerifyCodeRootOopClosure(G1CollectedHeap* g1h, OopClosure* root_cl, VerifyOption vo):
    _g1h(g1h), _root_cl(root_cl), _vo(vo), _nm(NULL), _failures(false) {}

  void do_oop(oop* p) { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }

  void set_nmethod(nmethod* nm) { _nm = nm; }
  bool failures() { return _failures; }
};

class G1VerifyCodeRootBlobClosure: public CodeBlobClosure {
  G1VerifyCodeRootOopClosure* _oop_cl;

public:
  G1VerifyCodeRootBlobClosure(G1VerifyCodeRootOopClosure* oop_cl):
    _oop_cl(oop_cl) {}

  void do_code_blob(CodeBlob* cb) {
    nmethod* nm = cb->as_nmethod_or_null();
    if (nm != NULL) {
      _oop_cl->set_nmethod(nm);
      nm->oops_do(_oop_cl);
    }
  }
};

class YoungRefCounterClosure : public OopClosure {
  G1CollectedHeap* _g1h;
  int              _count;
 public:
  YoungRefCounterClosure(G1CollectedHeap* g1h) : _g1h(g1h), _count(0) {}
  void do_oop(oop* p)       { if (_g1h->is_in_young(*p)) { _count++; } }
  void do_oop(narrowOop* p) { ShouldNotReachHere(); }

  int count() { return _count; }
  void reset_count() { _count = 0; };
};

class VerifyKlassClosure: public KlassClosure {
  YoungRefCounterClosure _young_ref_counter_closure;
  OopClosure *_oop_closure;
 public:
  VerifyKlassClosure(G1CollectedHeap* g1h, OopClosure* cl) : _young_ref_counter_closure(g1h), _oop_closure(cl) {}
  void do_klass(Klass* k) {
    k->oops_do(_oop_closure);

    _young_ref_counter_closure.reset_count();
    k->oops_do(&_young_ref_counter_closure);
    if (_young_ref_counter_closure.count() > 0) {
      guarantee(k->has_modified_oops(), "Klass " PTR_FORMAT ", has young refs but is not dirty.", p2i(k));
    }
  }
};

class VerifyLivenessOopClosure: public OopClosure {
  G1CollectedHeap* _g1h;
  VerifyOption _vo;
public:
  VerifyLivenessOopClosure(G1CollectedHeap* g1h, VerifyOption vo):
    _g1h(g1h), _vo(vo)
  { }
  void do_oop(narrowOop *p) { do_oop_work(p); }
  void do_oop(      oop *p) { do_oop_work(p); }

  template <class T> void do_oop_work(T *p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    guarantee(obj == NULL || !_g1h->is_obj_dead_cond(obj, _vo),
              "Dead object referenced by a not dead object");
  }
};

class VerifyObjsInRegionClosure: public ObjectClosure {
private:
  G1CollectedHeap* _g1h;
  size_t _live_bytes;
  HeapRegion *_hr;
  VerifyOption _vo;
public:
  // _vo == UsePrevMarking -> use "prev" marking information,
  // _vo == UseNextMarking -> use "next" marking information,
  // _vo == UseMarkWord    -> use mark word from object header.
  VerifyObjsInRegionClosure(HeapRegion *hr, VerifyOption vo)
    : _live_bytes(0), _hr(hr), _vo(vo) {
    _g1h = G1CollectedHeap::heap();
  }
  void do_object(oop o) {
    VerifyLivenessOopClosure isLive(_g1h, _vo);
    assert(o != NULL, "Huh?");
    if (!_g1h->is_obj_dead_cond(o, _vo)) {
      // If the object is alive according to the mark word,
      // then verify that the marking information agrees.
      // Note we can't verify the contra-positive of the
      // above: if the object is dead (according to the mark
      // word), it may not be marked, or may have been marked
      // but has since became dead, or may have been allocated
      // since the last marking.
      if (_vo == VerifyOption_G1UseMarkWord) {
        guarantee(!_g1h->is_obj_dead(o), "mark word and concurrent mark mismatch");
      }

      o->oop_iterate_no_header(&isLive);
      if (!_hr->obj_allocated_since_prev_marking(o)) {
        size_t obj_size = o->size();    // Make sure we don't overflow
        _live_bytes += (obj_size * HeapWordSize);
      }
    }
  }
  size_t live_bytes() { return _live_bytes; }
};

class VerifyArchiveOopClosure: public OopClosure {
public:
  VerifyArchiveOopClosure(HeapRegion *hr) { }
  void do_oop(narrowOop *p) { do_oop_work(p); }
  void do_oop(      oop *p) { do_oop_work(p); }

  template <class T> void do_oop_work(T *p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    guarantee(obj == NULL || G1MarkSweep::in_archive_range(obj),
              "Archive object at " PTR_FORMAT " references a non-archive object at " PTR_FORMAT,
              p2i(p), p2i(obj));
  }
};

class VerifyArchiveRegionClosure: public ObjectClosure {
public:
  VerifyArchiveRegionClosure(HeapRegion *hr) { }
  // Verify that all object pointers are to archive regions.
  void do_object(oop o) {
    VerifyArchiveOopClosure checkOop(NULL);
    assert(o != NULL, "Should not be here for NULL oops");
    o->oop_iterate_no_header(&checkOop);
  }
};

class VerifyRegionClosure: public HeapRegionClosure {
private:
  bool             _par;
  VerifyOption     _vo;
  bool             _failures;
public:
  // _vo == UsePrevMarking -> use "prev" marking information,
  // _vo == UseNextMarking -> use "next" marking information,
  // _vo == UseMarkWord    -> use mark word from object header.
  VerifyRegionClosure(bool par, VerifyOption vo)
    : _par(par),
      _vo(vo),
      _failures(false) {}

  bool failures() {
    return _failures;
  }

  bool doHeapRegion(HeapRegion* r) {
    // For archive regions, verify there are no heap pointers to
    // non-pinned regions. For all others, verify liveness info.
    if (r->is_archive()) {
      VerifyArchiveRegionClosure verify_oop_pointers(r);
      r->object_iterate(&verify_oop_pointers);
      return true;
    }
    if (!r->is_continues_humongous()) {
      bool failures = false;
      r->verify(_vo, &failures);
      if (failures) {
        _failures = true;
      } else if (!r->is_starts_humongous()) {
        VerifyObjsInRegionClosure not_dead_yet_cl(r, _vo);
        r->object_iterate(&not_dead_yet_cl);
        if (_vo != VerifyOption_G1UseNextMarking) {
          if (r->max_live_bytes() < not_dead_yet_cl.live_bytes()) {
            gclog_or_tty->print_cr("[" PTR_FORMAT "," PTR_FORMAT "] "
                                   "max_live_bytes " SIZE_FORMAT " "
                                   "< calculated " SIZE_FORMAT,
                                   p2i(r->bottom()), p2i(r->end()),
                                   r->max_live_bytes(),
                                 not_dead_yet_cl.live_bytes());
            _failures = true;
          }
        } else {
          // When vo == UseNextMarking we cannot currently do a sanity
          // check on the live bytes as the calculation has not been
          // finalized yet.
        }
      }
    }
    return false; // stop the region iteration if we hit a failure
  }
};

// This is the task used for parallel verification of the heap regions

class G1ParVerifyTask: public AbstractGangTask {
private:
  G1CollectedHeap*  _g1h;
  VerifyOption      _vo;
  bool              _failures;
  HeapRegionClaimer _hrclaimer;

public:
  // _vo == UsePrevMarking -> use "prev" marking information,
  // _vo == UseNextMarking -> use "next" marking information,
  // _vo == UseMarkWord    -> use mark word from object header.
  G1ParVerifyTask(G1CollectedHeap* g1h, VerifyOption vo) :
      AbstractGangTask("Parallel verify task"),
      _g1h(g1h),
      _vo(vo),
      _failures(false),
      _hrclaimer(g1h->workers()->active_workers()) {}

  bool failures() {
    return _failures;
  }

  void work(uint worker_id) {
    HandleMark hm;
    VerifyRegionClosure blk(true, _vo);
    _g1h->heap_region_par_iterate(&blk, worker_id, &_hrclaimer);
    if (blk.failures()) {
      _failures = true;
    }
  }
};

void G1CollectedHeap::verify(bool silent, VerifyOption vo) {
  if (SafepointSynchronize::is_at_safepoint()) {
    assert(Thread::current()->is_VM_thread(),
           "Expected to be executed serially by the VM thread at this point");

    if (!silent) { gclog_or_tty->print("Roots "); }
    VerifyRootsClosure rootsCl(vo);
    VerifyKlassClosure klassCl(this, &rootsCl);
    CLDToKlassAndOopClosure cldCl(&klassCl, &rootsCl, false);

    // We apply the relevant closures to all the oops in the
    // system dictionary, class loader data graph, the string table
    // and the nmethods in the code cache.
    G1VerifyCodeRootOopClosure codeRootsCl(this, &rootsCl, vo);
    G1VerifyCodeRootBlobClosure blobsCl(&codeRootsCl);

    {
      G1RootProcessor root_processor(this, 1);
      root_processor.process_all_roots(&rootsCl,
                                       &cldCl,
                                       &blobsCl);
    }

    bool failures = rootsCl.failures() || codeRootsCl.failures();

    if (vo != VerifyOption_G1UseMarkWord) {
      // If we're verifying during a full GC then the region sets
      // will have been torn down at the start of the GC. Therefore
      // verifying the region sets will fail. So we only verify
      // the region sets when not in a full GC.
      if (!silent) { gclog_or_tty->print("HeapRegionSets "); }
      verify_region_sets();
    }

    if (!silent) { gclog_or_tty->print("HeapRegions "); }
    if (GCParallelVerificationEnabled && ParallelGCThreads > 1) {

      G1ParVerifyTask task(this, vo);
      workers()->run_task(&task);
      if (task.failures()) {
        failures = true;
      }

    } else {
      VerifyRegionClosure blk(false, vo);
      heap_region_iterate(&blk);
      if (blk.failures()) {
        failures = true;
      }
    }

    if (G1StringDedup::is_enabled()) {
      if (!silent) gclog_or_tty->print("StrDedup ");
      G1StringDedup::verify();
    }

    if (failures) {
      gclog_or_tty->print_cr("Heap:");
      // It helps to have the per-region information in the output to
      // help us track down what went wrong. This is why we call
      // print_extended_on() instead of print_on().
      print_extended_on(gclog_or_tty);
      gclog_or_tty->cr();
      gclog_or_tty->flush();
    }
    guarantee(!failures, "there should not have been any failures");
  } else {
    if (!silent) {
      gclog_or_tty->print("(SKIPPING Roots, HeapRegionSets, HeapRegions, RemSet");
      if (G1StringDedup::is_enabled()) {
        gclog_or_tty->print(", StrDedup");
      }
      gclog_or_tty->print(") ");
    }
  }
}

void G1CollectedHeap::verify(bool silent) {
  verify(silent, VerifyOption_G1UsePrevMarking);
}

double G1CollectedHeap::verify(bool guard, const char* msg) {
  double verify_time_ms = 0.0;

  if (guard && total_collections() >= VerifyGCStartAt) {
    double verify_start = os::elapsedTime();
    HandleMark hm;  // Discard invalid handles created during verification
    prepare_for_verify();
    Universe::verify(VerifyOption_G1UsePrevMarking, msg);
    verify_time_ms = (os::elapsedTime() - verify_start) * 1000;
  }

  return verify_time_ms;
}

void G1CollectedHeap::verify_before_gc() {
  double verify_time_ms = verify(VerifyBeforeGC, " VerifyBeforeGC:");
  g1_policy()->phase_times()->record_verify_before_time_ms(verify_time_ms);
}

void G1CollectedHeap::verify_after_gc() {
  double verify_time_ms = verify(VerifyAfterGC, " VerifyAfterGC:");
  g1_policy()->phase_times()->record_verify_after_time_ms(verify_time_ms);
}

class PrintRegionClosure: public HeapRegionClosure {
  outputStream* _st;
public:
  PrintRegionClosure(outputStream* st) : _st(st) {}
  bool doHeapRegion(HeapRegion* r) {
    r->print_on(_st);
    return false;
  }
};

bool G1CollectedHeap::is_obj_dead_cond(const oop obj,
                                       const HeapRegion* hr,
                                       const VerifyOption vo) const {
  switch (vo) {
  case VerifyOption_G1UsePrevMarking: return is_obj_dead(obj, hr);
  case VerifyOption_G1UseNextMarking: return is_obj_ill(obj, hr);
  case VerifyOption_G1UseMarkWord:    return !obj->is_gc_marked() && !hr->is_archive();
  default:                            ShouldNotReachHere();
  }
  return false; // keep some compilers happy
}

bool G1CollectedHeap::is_obj_dead_cond(const oop obj,
                                       const VerifyOption vo) const {
  switch (vo) {
  case VerifyOption_G1UsePrevMarking: return is_obj_dead(obj);
  case VerifyOption_G1UseNextMarking: return is_obj_ill(obj);
  case VerifyOption_G1UseMarkWord: {
    HeapRegion* hr = _hrm.addr_to_region((HeapWord*)obj);
    return !obj->is_gc_marked() && !hr->is_archive();
  }
  default:                            ShouldNotReachHere();
  }
  return false; // keep some compilers happy
}

void G1CollectedHeap::print_on(outputStream* st) const {
  st->print(" %-20s", "garbage-first heap");
  st->print(" total " SIZE_FORMAT "K, used " SIZE_FORMAT "K",
            capacity()/K, used_unlocked()/K);
  st->print(" [" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT ")",
            p2i(_hrm.reserved().start()),
            p2i(_hrm.reserved().start() + _hrm.length() + HeapRegion::GrainWords),
            p2i(_hrm.reserved().end()));
  st->cr();
  st->print("  region size " SIZE_FORMAT "K, ", HeapRegion::GrainBytes / K);
  uint young_regions = _young_list->length();
  st->print("%u young (" SIZE_FORMAT "K), ", young_regions,
            (size_t) young_regions * HeapRegion::GrainBytes / K);
  uint survivor_regions = g1_policy()->recorded_survivor_regions();
  st->print("%u survivors (" SIZE_FORMAT "K)", survivor_regions,
            (size_t) survivor_regions * HeapRegion::GrainBytes / K);
  st->cr();
  MetaspaceAux::print_on(st);
}

void G1CollectedHeap::print_extended_on(outputStream* st) const {
  print_on(st);

  // Print the per-region information.
  st->cr();
  st->print_cr("Heap Regions: (E=young(eden), S=young(survivor), O=old, "
               "HS=humongous(starts), HC=humongous(continues), "
               "CS=collection set, F=free, A=archive, TS=gc time stamp, "
               "PTAMS=previous top-at-mark-start, "
               "NTAMS=next top-at-mark-start)");
  PrintRegionClosure blk(st);
  heap_region_iterate(&blk);
}

void G1CollectedHeap::print_on_error(outputStream* st) const {
  this->CollectedHeap::print_on_error(st);

  if (_cm != NULL) {
    st->cr();
    _cm->print_on_error(st);
  }
}

void G1CollectedHeap::print_gc_threads_on(outputStream* st) const {
  workers()->print_worker_threads_on(st);
  _cmThread->print_on(st);
  st->cr();
  _cm->print_worker_threads_on(st);
  _cg1r->print_worker_threads_on(st);
  if (G1StringDedup::is_enabled()) {
    G1StringDedup::print_worker_threads_on(st);
  }
}

void G1CollectedHeap::gc_threads_do(ThreadClosure* tc) const {
  workers()->threads_do(tc);
  tc->do_thread(_cmThread);
  _cg1r->threads_do(tc);
  if (G1StringDedup::is_enabled()) {
    G1StringDedup::threads_do(tc);
  }
}

void G1CollectedHeap::print_tracing_info() const {
  // We'll overload this to mean "trace GC pause statistics."
  if (TraceYoungGenTime || TraceOldGenTime) {
    // The "G1CollectorPolicy" is keeping track of these stats, so delegate
    // to that.
    g1_policy()->print_tracing_info();
  }
  if (G1SummarizeRSetStats) {
    g1_rem_set()->print_summary_info();
  }
  if (G1SummarizeConcMark) {
    concurrent_mark()->print_summary_info();
  }
  g1_policy()->print_yg_surv_rate_info();
}

#ifndef PRODUCT
// Helpful for debugging RSet issues.

class PrintRSetsClosure : public HeapRegionClosure {
private:
  const char* _msg;
  size_t _occupied_sum;

public:
  bool doHeapRegion(HeapRegion* r) {
    HeapRegionRemSet* hrrs = r->rem_set();
    size_t occupied = hrrs->occupied();
    _occupied_sum += occupied;

    gclog_or_tty->print_cr("Printing RSet for region " HR_FORMAT,
                           HR_FORMAT_PARAMS(r));
    if (occupied == 0) {
      gclog_or_tty->print_cr("  RSet is empty");
    } else {
      hrrs->print();
    }
    gclog_or_tty->print_cr("----------");
    return false;
  }

  PrintRSetsClosure(const char* msg) : _msg(msg), _occupied_sum(0) {
    gclog_or_tty->cr();
    gclog_or_tty->print_cr("========================================");
    gclog_or_tty->print_cr("%s", msg);
    gclog_or_tty->cr();
  }

  ~PrintRSetsClosure() {
    gclog_or_tty->print_cr("Occupied Sum: " SIZE_FORMAT, _occupied_sum);
    gclog_or_tty->print_cr("========================================");
    gclog_or_tty->cr();
  }
};

void G1CollectedHeap::print_cset_rsets() {
  PrintRSetsClosure cl("Printing CSet RSets");
  collection_set_iterate(&cl);
}

void G1CollectedHeap::print_all_rsets() {
  PrintRSetsClosure cl("Printing All RSets");;
  heap_region_iterate(&cl);
}
#endif // PRODUCT

G1HeapSummary G1CollectedHeap::create_g1_heap_summary() {
  YoungList* young_list = heap()->young_list();

  size_t eden_used_bytes = young_list->eden_used_bytes();
  size_t survivor_used_bytes = young_list->survivor_used_bytes();

  size_t eden_capacity_bytes =
    (g1_policy()->young_list_target_length() * HeapRegion::GrainBytes) - survivor_used_bytes;

  VirtualSpaceSummary heap_summary = create_heap_space_summary();
  return G1HeapSummary(heap_summary, used(), eden_used_bytes, eden_capacity_bytes, survivor_used_bytes);
}

G1EvacSummary G1CollectedHeap::create_g1_evac_summary(G1EvacStats* stats) {
  return G1EvacSummary(stats->allocated(), stats->wasted(), stats->undo_wasted(),
                       stats->unused(), stats->used(), stats->region_end_waste(),
                       stats->regions_filled(), stats->direct_allocated(),
                       stats->failure_used(), stats->failure_waste());
}

void G1CollectedHeap::trace_heap(GCWhen::Type when, const GCTracer* gc_tracer) {
  const G1HeapSummary& heap_summary = create_g1_heap_summary();
  gc_tracer->report_gc_heap_summary(when, heap_summary);

  const MetaspaceSummary& metaspace_summary = create_metaspace_summary();
  gc_tracer->report_metaspace_summary(when, metaspace_summary);
}


G1CollectedHeap* G1CollectedHeap::heap() {
  CollectedHeap* heap = Universe::heap();
  assert(heap != NULL, "Uninitialized access to G1CollectedHeap::heap()");
  assert(heap->kind() == CollectedHeap::G1CollectedHeap, "Not a G1CollectedHeap");
  return (G1CollectedHeap*)heap;
}

void G1CollectedHeap::gc_prologue(bool full /* Ignored */) {
  // always_do_update_barrier = false;
  assert(InlineCacheBuffer::is_empty(), "should have cleaned up ICBuffer");
  // Fill TLAB's and such
  accumulate_statistics_all_tlabs();
  ensure_parsability(true);

  if (G1SummarizeRSetStats && (G1SummarizeRSetStatsPeriod > 0) &&
      (total_collections() % G1SummarizeRSetStatsPeriod == 0)) {
    g1_rem_set()->print_periodic_summary_info("Before GC RS summary");
  }
}

void G1CollectedHeap::gc_epilogue(bool full) {

  if (G1SummarizeRSetStats &&
      (G1SummarizeRSetStatsPeriod > 0) &&
      // we are at the end of the GC. Total collections has already been increased.
      ((total_collections() - 1) % G1SummarizeRSetStatsPeriod == 0)) {
    g1_rem_set()->print_periodic_summary_info("After GC RS summary");
  }

  // FIXME: what is this about?
  // I'm ignoring the "fill_newgen()" call if "alloc_event_enabled"
  // is set.
#if defined(COMPILER2) || INCLUDE_JVMCI
  assert(DerivedPointerTable::is_empty(), "derived pointer present");
#endif
  // always_do_update_barrier = true;

  resize_all_tlabs();
  allocation_context_stats().update(full);

  // We have just completed a GC. Update the soft reference
  // policy with the new heap occupancy
  Universe::update_heap_info_at_gc();
}

HeapWord* G1CollectedHeap::do_collection_pause(size_t word_size,
                                               uint gc_count_before,
                                               bool* succeeded,
                                               GCCause::Cause gc_cause) {
  assert_heap_not_locked_and_not_at_safepoint();
  g1_policy()->record_stop_world_start();
  VM_G1IncCollectionPause op(gc_count_before,
                             word_size,
                             false, /* should_initiate_conc_mark */
                             g1_policy()->max_pause_time_ms(),
                             gc_cause);

  op.set_allocation_context(AllocationContext::current());
  VMThread::execute(&op);

  HeapWord* result = op.result();
  bool ret_succeeded = op.prologue_succeeded() && op.pause_succeeded();
  assert(result == NULL || ret_succeeded,
         "the result should be NULL if the VM did not succeed");
  *succeeded = ret_succeeded;

  assert_heap_not_locked();
  return result;
}

void
G1CollectedHeap::doConcurrentMark() {
  MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
  if (!_cmThread->in_progress()) {
    _cmThread->set_started();
    CGC_lock->notify();
  }
}

size_t G1CollectedHeap::pending_card_num() {
  size_t extra_cards = 0;
  JavaThread *curr = Threads::first();
  while (curr != NULL) {
    DirtyCardQueue& dcq = curr->dirty_card_queue();
    extra_cards += dcq.size();
    curr = curr->next();
  }
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  size_t buffer_size = dcqs.buffer_size();
  size_t buffer_num = dcqs.completed_buffers_num();

  // PtrQueueSet::buffer_size() and PtrQueue:size() return sizes
  // in bytes - not the number of 'entries'. We need to convert
  // into a number of cards.
  return (buffer_size * buffer_num + extra_cards) / oopSize;
}

class RegisterHumongousWithInCSetFastTestClosure : public HeapRegionClosure {
 private:
  size_t _total_humongous;
  size_t _candidate_humongous;

  DirtyCardQueue _dcq;

  // We don't nominate objects with many remembered set entries, on
  // the assumption that such objects are likely still live.
  bool is_remset_small(HeapRegion* region) const {
    HeapRegionRemSet* const rset = region->rem_set();
    return G1EagerReclaimHumongousObjectsWithStaleRefs
      ? rset->occupancy_less_or_equal_than(G1RSetSparseRegionEntries)
      : rset->is_empty();
  }

  bool is_typeArray_region(HeapRegion* region) const {
    return oop(region->bottom())->is_typeArray();
  }

  bool humongous_region_is_candidate(G1CollectedHeap* heap, HeapRegion* region) const {
    assert(region->is_starts_humongous(), "Must start a humongous object");

    // Candidate selection must satisfy the following constraints
    // while concurrent marking is in progress:
    //
    // * In order to maintain SATB invariants, an object must not be
    // reclaimed if it was allocated before the start of marking and
    // has not had its references scanned.  Such an object must have
    // its references (including type metadata) scanned to ensure no
    // live objects are missed by the marking process.  Objects
    // allocated after the start of concurrent marking don't need to
    // be scanned.
    //
    // * An object must not be reclaimed if it is on the concurrent
    // mark stack.  Objects allocated after the start of concurrent
    // marking are never pushed on the mark stack.
    //
    // Nominating only objects allocated after the start of concurrent
    // marking is sufficient to meet both constraints.  This may miss
    // some objects that satisfy the constraints, but the marking data
    // structures don't support efficiently performing the needed
    // additional tests or scrubbing of the mark stack.
    //
    // However, we presently only nominate is_typeArray() objects.
    // A humongous object containing references induces remembered
    // set entries on other regions.  In order to reclaim such an
    // object, those remembered sets would need to be cleaned up.
    //
    // We also treat is_typeArray() objects specially, allowing them
    // to be reclaimed even if allocated before the start of
    // concurrent mark.  For this we rely on mark stack insertion to
    // exclude is_typeArray() objects, preventing reclaiming an object
    // that is in the mark stack.  We also rely on the metadata for
    // such objects to be built-in and so ensured to be kept live.
    // Frequent allocation and drop of large binary blobs is an
    // important use case for eager reclaim, and this special handling
    // may reduce needed headroom.

    return is_typeArray_region(region) && is_remset_small(region);
  }

 public:
  RegisterHumongousWithInCSetFastTestClosure()
  : _total_humongous(0),
    _candidate_humongous(0),
    _dcq(&JavaThread::dirty_card_queue_set()) {
  }

  virtual bool doHeapRegion(HeapRegion* r) {
    if (!r->is_starts_humongous()) {
      return false;
    }
    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    bool is_candidate = humongous_region_is_candidate(g1h, r);
    uint rindex = r->hrm_index();
    g1h->set_humongous_reclaim_candidate(rindex, is_candidate);
    if (is_candidate) {
      _candidate_humongous++;
      g1h->register_humongous_region_with_cset(rindex);
      // Is_candidate already filters out humongous object with large remembered sets.
      // If we have a humongous object with a few remembered sets, we simply flush these
      // remembered set entries into the DCQS. That will result in automatic
      // re-evaluation of their remembered set entries during the following evacuation
      // phase.
      if (!r->rem_set()->is_empty()) {
        guarantee(r->rem_set()->occupancy_less_or_equal_than(G1RSetSparseRegionEntries),
                  "Found a not-small remembered set here. This is inconsistent with previous assumptions.");
        G1SATBCardTableLoggingModRefBS* bs = g1h->g1_barrier_set();
        HeapRegionRemSetIterator hrrs(r->rem_set());
        size_t card_index;
        while (hrrs.has_next(card_index)) {
          jbyte* card_ptr = (jbyte*)bs->byte_for_index(card_index);
          // The remembered set might contain references to already freed
          // regions. Filter out such entries to avoid failing card table
          // verification.
          if (g1h->is_in_closed_subset(bs->addr_for(card_ptr))) {
            if (*card_ptr != CardTableModRefBS::dirty_card_val()) {
              *card_ptr = CardTableModRefBS::dirty_card_val();
              _dcq.enqueue(card_ptr);
            }
          }
        }
        assert(hrrs.n_yielded() == r->rem_set()->occupied(),
               "Remembered set hash maps out of sync, cur: " SIZE_FORMAT " entries, next: " SIZE_FORMAT " entries",
               hrrs.n_yielded(), r->rem_set()->occupied());
        r->rem_set()->clear_locked();
      }
      assert(r->rem_set()->is_empty(), "At this point any humongous candidate remembered set must be empty.");
    }
    _total_humongous++;

    return false;
  }

  size_t total_humongous() const { return _total_humongous; }
  size_t candidate_humongous() const { return _candidate_humongous; }

  void flush_rem_set_entries() { _dcq.flush(); }
};

void G1CollectedHeap::register_humongous_regions_with_cset() {
  if (!G1EagerReclaimHumongousObjects) {
    g1_policy()->phase_times()->record_fast_reclaim_humongous_stats(0.0, 0, 0);
    return;
  }
  double time = os::elapsed_counter();

  // Collect reclaim candidate information and register candidates with cset.
  RegisterHumongousWithInCSetFastTestClosure cl;
  heap_region_iterate(&cl);

  time = ((double)(os::elapsed_counter() - time) / os::elapsed_frequency()) * 1000.0;
  g1_policy()->phase_times()->record_fast_reclaim_humongous_stats(time,
                                                                  cl.total_humongous(),
                                                                  cl.candidate_humongous());
  _has_humongous_reclaim_candidates = cl.candidate_humongous() > 0;

  // Finally flush all remembered set entries to re-check into the global DCQS.
  cl.flush_rem_set_entries();
}

#ifdef ASSERT
class VerifyCSetClosure: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion* hr) {
    // Here we check that the CSet region's RSet is ready for parallel
    // iteration. The fields that we'll verify are only manipulated
    // when the region is part of a CSet and is collected. Afterwards,
    // we reset these fields when we clear the region's RSet (when the
    // region is freed) so they are ready when the region is
    // re-allocated. The only exception to this is if there's an
    // evacuation failure and instead of freeing the region we leave
    // it in the heap. In that case, we reset these fields during
    // evacuation failure handling.
    guarantee(hr->rem_set()->verify_ready_for_par_iteration(), "verification");

    // Here's a good place to add any other checks we'd like to
    // perform on CSet regions.
    return false;
  }
};
#endif // ASSERT

uint G1CollectedHeap::num_task_queues() const {
  return _task_queues->size();
}

#if TASKQUEUE_STATS
void G1CollectedHeap::print_taskqueue_stats_hdr(outputStream* const st) {
  st->print_raw_cr("GC Task Stats");
  st->print_raw("thr "); TaskQueueStats::print_header(1, st); st->cr();
  st->print_raw("--- "); TaskQueueStats::print_header(2, st); st->cr();
}

void G1CollectedHeap::print_taskqueue_stats(outputStream* const st) const {
  print_taskqueue_stats_hdr(st);

  TaskQueueStats totals;
  const uint n = num_task_queues();
  for (uint i = 0; i < n; ++i) {
    st->print("%3u ", i); task_queue(i)->stats.print(st); st->cr();
    totals += task_queue(i)->stats;
  }
  st->print_raw("tot "); totals.print(st); st->cr();

  DEBUG_ONLY(totals.verify());
}

void G1CollectedHeap::reset_taskqueue_stats() {
  const uint n = num_task_queues();
  for (uint i = 0; i < n; ++i) {
    task_queue(i)->stats.reset();
  }
}
#endif // TASKQUEUE_STATS

void G1CollectedHeap::log_gc_header() {
  if (!G1Log::fine()) {
    return;
  }

  gclog_or_tty->gclog_stamp();

  GCCauseString gc_cause_str = GCCauseString("GC pause", gc_cause())
    .append(collector_state()->gcs_are_young() ? "(young)" : "(mixed)")
    .append(collector_state()->during_initial_mark_pause() ? " (initial-mark)" : "");

  gclog_or_tty->print("[%s", (const char*)gc_cause_str);
}

void G1CollectedHeap::log_gc_footer(double pause_time_sec) {
  if (!G1Log::fine()) {
    return;
  }

  if (G1Log::finer()) {
    if (evacuation_failed()) {
      gclog_or_tty->print(" (to-space exhausted)");
    }
    gclog_or_tty->print_cr(", %3.7f secs]", pause_time_sec);
    g1_policy()->print_phases(pause_time_sec);
    g1_policy()->print_detailed_heap_transition();
  } else {
    if (evacuation_failed()) {
      gclog_or_tty->print("--");
    }
    g1_policy()->print_heap_transition();
    gclog_or_tty->print_cr(", %3.7f secs]", pause_time_sec);
  }
  gclog_or_tty->flush();
}

void G1CollectedHeap::wait_for_root_region_scanning() {
  double scan_wait_start = os::elapsedTime();
  // We have to wait until the CM threads finish scanning the
  // root regions as it's the only way to ensure that all the
  // objects on them have been correctly scanned before we start
  // moving them during the GC.
  bool waited = _cm->root_regions()->wait_until_scan_finished();
  double wait_time_ms = 0.0;
  if (waited) {
    double scan_wait_end = os::elapsedTime();
    wait_time_ms = (scan_wait_end - scan_wait_start) * 1000.0;
  }
  g1_policy()->phase_times()->record_root_region_scan_wait_time(wait_time_ms);
}

bool
G1CollectedHeap::do_collection_pause_at_safepoint(double target_pause_time_ms) {
  assert_at_safepoint(true /* should_be_vm_thread */);
  guarantee(!is_gc_active(), "collection is not reentrant");

  if (GC_locker::check_active_before_gc()) {
    return false;
  }

  _gc_timer_stw->register_gc_start();

  GCIdMark gc_id_mark;
  _gc_tracer_stw->report_gc_start(gc_cause(), _gc_timer_stw->gc_start());

  SvcGCMarker sgcm(SvcGCMarker::MINOR);
  ResourceMark rm;

  wait_for_root_region_scanning();

  G1Log::update_level();
  print_heap_before_gc();
  trace_heap_before_gc(_gc_tracer_stw);

  verify_region_sets_optional();
  verify_dirty_young_regions();

  // This call will decide whether this pause is an initial-mark
  // pause. If it is, during_initial_mark_pause() will return true
  // for the duration of this pause.
  g1_policy()->decide_on_conc_mark_initiation();

  // We do not allow initial-mark to be piggy-backed on a mixed GC.
  assert(!collector_state()->during_initial_mark_pause() ||
          collector_state()->gcs_are_young(), "sanity");

  // We also do not allow mixed GCs during marking.
  assert(!collector_state()->mark_in_progress() || collector_state()->gcs_are_young(), "sanity");

  // Record whether this pause is an initial mark. When the current
  // thread has completed its logging output and it's safe to signal
  // the CM thread, the flag's value in the policy has been reset.
  bool should_start_conc_mark = collector_state()->during_initial_mark_pause();

  // Inner scope for scope based logging, timers, and stats collection
  {
    EvacuationInfo evacuation_info;

    if (collector_state()->during_initial_mark_pause()) {
      // We are about to start a marking cycle, so we increment the
      // full collection counter.
      increment_old_marking_cycles_started();
      register_concurrent_cycle_start(_gc_timer_stw->gc_start());
    }

    _gc_tracer_stw->report_yc_type(collector_state()->yc_type());

    TraceCPUTime tcpu(G1Log::finer(), true, gclog_or_tty);

    uint active_workers = AdaptiveSizePolicy::calc_active_workers(workers()->total_workers(),
                                                                  workers()->active_workers(),
                                                                  Threads::number_of_non_daemon_threads());
    workers()->set_active_workers(active_workers);

    double pause_start_sec = os::elapsedTime();
    g1_policy()->note_gc_start(active_workers);
    log_gc_header();

    TraceCollectorStats tcs(g1mm()->incremental_collection_counters());
    TraceMemoryManagerStats tms(false /* fullGC */, gc_cause());

    // If the secondary_free_list is not empty, append it to the
    // free_list. No need to wait for the cleanup operation to finish;
    // the region allocation code will check the secondary_free_list
    // and wait if necessary. If the G1StressConcRegionFreeing flag is
    // set, skip this step so that the region allocation code has to
    // get entries from the secondary_free_list.
    if (!G1StressConcRegionFreeing) {
      append_secondary_free_list_if_not_empty_with_lock();
    }

    assert(check_young_list_well_formed(), "young list should be well formed");

    // Don't dynamically change the number of GC threads this early.  A value of
    // 0 is used to indicate serial work.  When parallel work is done,
    // it will be set.

    { // Call to jvmpi::post_class_unload_events must occur outside of active GC
      IsGCActiveMark x;

      gc_prologue(false);
      increment_total_collections(false /* full gc */);
      increment_gc_time_stamp();

      verify_before_gc();

      check_bitmaps("GC Start");

#if defined(COMPILER2) || INCLUDE_JVMCI
      DerivedPointerTable::clear();
#endif

      // Please see comment in g1CollectedHeap.hpp and
      // G1CollectedHeap::ref_processing_init() to see how
      // reference processing currently works in G1.

      // Enable discovery in the STW reference processor
      ref_processor_stw()->enable_discovery();

      {
        // We want to temporarily turn off discovery by the
        // CM ref processor, if necessary, and turn it back on
        // on again later if we do. Using a scoped
        // NoRefDiscovery object will do this.
        NoRefDiscovery no_cm_discovery(ref_processor_cm());

        // Forget the current alloc region (we might even choose it to be part
        // of the collection set!).
        _allocator->release_mutator_alloc_region();

        // We should call this after we retire the mutator alloc
        // region(s) so that all the ALLOC / RETIRE events are generated
        // before the start GC event.
        _hr_printer.start_gc(false /* full */, (size_t) total_collections());

        // This timing is only used by the ergonomics to handle our pause target.
        // It is unclear why this should not include the full pause. We will
        // investigate this in CR 7178365.
        //
        // Preserving the old comment here if that helps the investigation:
        //
        // The elapsed time induced by the start time below deliberately elides
        // the possible verification above.
        double sample_start_time_sec = os::elapsedTime();

        g1_policy()->record_collection_pause_start(sample_start_time_sec);

        if (collector_state()->during_initial_mark_pause()) {
          concurrent_mark()->checkpointRootsInitialPre();
        }

        double time_remaining_ms = g1_policy()->finalize_young_cset_part(target_pause_time_ms);
        g1_policy()->finalize_old_cset_part(time_remaining_ms);

        evacuation_info.set_collectionset_regions(g1_policy()->cset_region_length());

        // Make sure the remembered sets are up to date. This needs to be
        // done before register_humongous_regions_with_cset(), because the
        // remembered sets are used there to choose eager reclaim candidates.
        // If the remembered sets are not up to date we might miss some
        // entries that need to be handled.
        g1_rem_set()->cleanupHRRS();

        register_humongous_regions_with_cset();

        assert(check_cset_fast_test(), "Inconsistency in the InCSetState table.");

        _cm->note_start_of_gc();
        // We call this after finalize_cset() to
        // ensure that the CSet has been finalized.
        _cm->verify_no_cset_oops();

        if (_hr_printer.is_active()) {
          HeapRegion* hr = g1_policy()->collection_set();
          while (hr != NULL) {
            _hr_printer.cset(hr);
            hr = hr->next_in_collection_set();
          }
        }

#ifdef ASSERT
        VerifyCSetClosure cl;
        collection_set_iterate(&cl);
#endif // ASSERT

        // Initialize the GC alloc regions.
        _allocator->init_gc_alloc_regions(evacuation_info);

        G1ParScanThreadStateSet per_thread_states(this, workers()->active_workers(), g1_policy()->young_cset_region_length());
        pre_evacuate_collection_set();

        // Actually do the work...
        evacuate_collection_set(evacuation_info, &per_thread_states);

        post_evacuate_collection_set(evacuation_info, &per_thread_states);

        const size_t* surviving_young_words = per_thread_states.surviving_young_words();
        free_collection_set(g1_policy()->collection_set(), evacuation_info, surviving_young_words);

        eagerly_reclaim_humongous_regions();

        g1_policy()->clear_collection_set();

        // Start a new incremental collection set for the next pause.
        g1_policy()->start_incremental_cset_building();

        clear_cset_fast_test();

        _young_list->reset_sampled_info();

        // Don't check the whole heap at this point as the
        // GC alloc regions from this pause have been tagged
        // as survivors and moved on to the survivor list.
        // Survivor regions will fail the !is_young() check.
        assert(check_young_list_empty(false /* check_heap */),
          "young list should be empty");

        g1_policy()->record_survivor_regions(_young_list->survivor_length(),
                                             _young_list->first_survivor_region(),
                                             _young_list->last_survivor_region());

        _young_list->reset_auxilary_lists();

        if (evacuation_failed()) {
          set_used(recalculate_used());
          if (_archive_allocator != NULL) {
            _archive_allocator->clear_used();
          }
          for (uint i = 0; i < ParallelGCThreads; i++) {
            if (_evacuation_failed_info_array[i].has_failed()) {
              _gc_tracer_stw->report_evacuation_failed(_evacuation_failed_info_array[i]);
            }
          }
        } else {
          // The "used" of the the collection set have already been subtracted
          // when they were freed.  Add in the bytes evacuated.
          increase_used(g1_policy()->bytes_copied_during_gc());
        }

        if (collector_state()->during_initial_mark_pause()) {
          // We have to do this before we notify the CM threads that
          // they can start working to make sure that all the
          // appropriate initialization is done on the CM object.
          concurrent_mark()->checkpointRootsInitialPost();
          collector_state()->set_mark_in_progress(true);
          // Note that we don't actually trigger the CM thread at
          // this point. We do that later when we're sure that
          // the current thread has completed its logging output.
        }

        allocate_dummy_regions();

        _allocator->init_mutator_alloc_region();

        {
          size_t expand_bytes = g1_policy()->expansion_amount();
          if (expand_bytes > 0) {
            size_t bytes_before = capacity();
            // No need for an ergo verbose message here,
            // expansion_amount() does this when it returns a value > 0.
            double expand_ms;
            if (!expand(expand_bytes, &expand_ms)) {
              // We failed to expand the heap. Cannot do anything about it.
            }
            g1_policy()->phase_times()->record_expand_heap_time(expand_ms);
          }
        }

        // We redo the verification but now wrt to the new CSet which
        // has just got initialized after the previous CSet was freed.
        _cm->verify_no_cset_oops();
        _cm->note_end_of_gc();

        // This timing is only used by the ergonomics to handle our pause target.
        // It is unclear why this should not include the full pause. We will
        // investigate this in CR 7178365.
        double sample_end_time_sec = os::elapsedTime();
        double pause_time_ms = (sample_end_time_sec - sample_start_time_sec) * MILLIUNITS;
        size_t total_cards_scanned = per_thread_states.total_cards_scanned();
        g1_policy()->record_collection_pause_end(pause_time_ms, total_cards_scanned);

        evacuation_info.set_collectionset_used_before(g1_policy()->collection_set_bytes_used_before());
        evacuation_info.set_bytes_copied(g1_policy()->bytes_copied_during_gc());

        MemoryService::track_memory_usage();

        // In prepare_for_verify() below we'll need to scan the deferred
        // update buffers to bring the RSets up-to-date if
        // G1HRRSFlushLogBuffersOnVerify has been set. While scanning
        // the update buffers we'll probably need to scan cards on the
        // regions we just allocated to (i.e., the GC alloc
        // regions). However, during the last GC we called
        // set_saved_mark() on all the GC alloc regions, so card
        // scanning might skip the [saved_mark_word()...top()] area of
        // those regions (i.e., the area we allocated objects into
        // during the last GC). But it shouldn't. Given that
        // saved_mark_word() is conditional on whether the GC time stamp
        // on the region is current or not, by incrementing the GC time
        // stamp here we invalidate all the GC time stamps on all the
        // regions and saved_mark_word() will simply return top() for
        // all the regions. This is a nicer way of ensuring this rather
        // than iterating over the regions and fixing them. In fact, the
        // GC time stamp increment here also ensures that
        // saved_mark_word() will return top() between pauses, i.e.,
        // during concurrent refinement. So we don't need the
        // is_gc_active() check to decided which top to use when
        // scanning cards (see CR 7039627).
        increment_gc_time_stamp();

        verify_after_gc();
        check_bitmaps("GC End");

        assert(!ref_processor_stw()->discovery_enabled(), "Postcondition");
        ref_processor_stw()->verify_no_references_recorded();

        // CM reference discovery will be re-enabled if necessary.
      }

      // We should do this after we potentially expand the heap so
      // that all the COMMIT events are generated before the end GC
      // event, and after we retire the GC alloc regions so that all
      // RETIRE events are generated before the end GC event.
      _hr_printer.end_gc(false /* full */, (size_t) total_collections());

#ifdef TRACESPINNING
      ParallelTaskTerminator::print_termination_counts();
#endif

      gc_epilogue(false);
    }

    // Print the remainder of the GC log output.
    log_gc_footer(os::elapsedTime() - pause_start_sec);

    // It is not yet to safe to tell the concurrent mark to
    // start as we have some optional output below. We don't want the
    // output from the concurrent mark thread interfering with this
    // logging output either.

    _hrm.verify_optional();
    verify_region_sets_optional();

    TASKQUEUE_STATS_ONLY(if (PrintTaskqueue) print_taskqueue_stats());
    TASKQUEUE_STATS_ONLY(reset_taskqueue_stats());

    print_heap_after_gc();
    trace_heap_after_gc(_gc_tracer_stw);

    // We must call G1MonitoringSupport::update_sizes() in the same scoping level
    // as an active TraceMemoryManagerStats object (i.e. before the destructor for the
    // TraceMemoryManagerStats is called) so that the G1 memory pools are updated
    // before any GC notifications are raised.
    g1mm()->update_sizes();

    _gc_tracer_stw->report_evacuation_info(&evacuation_info);
    _gc_tracer_stw->report_tenuring_threshold(_g1_policy->tenuring_threshold());
    _gc_timer_stw->register_gc_end();
    _gc_tracer_stw->report_gc_end(_gc_timer_stw->gc_end(), _gc_timer_stw->time_partitions());
  }
  // It should now be safe to tell the concurrent mark thread to start
  // without its logging output interfering with the logging output
  // that came from the pause.

  if (should_start_conc_mark) {
    // CAUTION: after the doConcurrentMark() call below,
    // the concurrent marking thread(s) could be running
    // concurrently with us. Make sure that anything after
    // this point does not assume that we are the only GC thread
    // running. Note: of course, the actual marking work will
    // not start until the safepoint itself is released in
    // SuspendibleThreadSet::desynchronize().
    doConcurrentMark();
  }

  return true;
}

void G1CollectedHeap::remove_self_forwarding_pointers() {
  double remove_self_forwards_start = os::elapsedTime();

  G1ParRemoveSelfForwardPtrsTask rsfp_task;
  workers()->run_task(&rsfp_task);

  // Now restore saved marks, if any.
  for (uint i = 0; i < ParallelGCThreads; i++) {
    OopAndMarkOopStack& cur = _preserved_objs[i];
    while (!cur.is_empty()) {
      OopAndMarkOop elem = cur.pop();
      elem.set_mark();
    }
    cur.clear(true);
  }

  g1_policy()->phase_times()->record_evac_fail_remove_self_forwards((os::elapsedTime() - remove_self_forwards_start) * 1000.0);
}

void G1CollectedHeap::preserve_mark_during_evac_failure(uint worker_id, oop obj, markOop m) {
  if (!_evacuation_failed) {
    _evacuation_failed = true;
  }

  _evacuation_failed_info_array[worker_id].register_copy_failure(obj->size());

  // We want to call the "for_promotion_failure" version only in the
  // case of a promotion failure.
  if (m->must_be_preserved_for_promotion_failure(obj)) {
    OopAndMarkOop elem(obj, m);
    _preserved_objs[worker_id].push(elem);
  }
}

class G1ParEvacuateFollowersClosure : public VoidClosure {
private:
  double _start_term;
  double _term_time;
  size_t _term_attempts;

  void start_term_time() { _term_attempts++; _start_term = os::elapsedTime(); }
  void end_term_time() { _term_time += os::elapsedTime() - _start_term; }
protected:
  G1CollectedHeap*              _g1h;
  G1ParScanThreadState*         _par_scan_state;
  RefToScanQueueSet*            _queues;
  ParallelTaskTerminator*       _terminator;

  G1ParScanThreadState*   par_scan_state() { return _par_scan_state; }
  RefToScanQueueSet*      queues()         { return _queues; }
  ParallelTaskTerminator* terminator()     { return _terminator; }

public:
  G1ParEvacuateFollowersClosure(G1CollectedHeap* g1h,
                                G1ParScanThreadState* par_scan_state,
                                RefToScanQueueSet* queues,
                                ParallelTaskTerminator* terminator)
    : _g1h(g1h), _par_scan_state(par_scan_state),
      _queues(queues), _terminator(terminator),
      _start_term(0.0), _term_time(0.0), _term_attempts(0) {}

  void do_void();

  double term_time() const { return _term_time; }
  size_t term_attempts() const { return _term_attempts; }

private:
  inline bool offer_termination();
};

bool G1ParEvacuateFollowersClosure::offer_termination() {
  G1ParScanThreadState* const pss = par_scan_state();
  start_term_time();
  const bool res = terminator()->offer_termination();
  end_term_time();
  return res;
}

void G1ParEvacuateFollowersClosure::do_void() {
  G1ParScanThreadState* const pss = par_scan_state();
  pss->trim_queue();
  do {
    pss->steal_and_trim_queue(queues());
  } while (!offer_termination());
}

class G1ParTask : public AbstractGangTask {
protected:
  G1CollectedHeap*         _g1h;
  G1ParScanThreadStateSet* _pss;
  RefToScanQueueSet*       _queues;
  G1RootProcessor*         _root_processor;
  ParallelTaskTerminator   _terminator;
  uint                     _n_workers;

public:
  G1ParTask(G1CollectedHeap* g1h, G1ParScanThreadStateSet* per_thread_states, RefToScanQueueSet *task_queues, G1RootProcessor* root_processor, uint n_workers)
    : AbstractGangTask("G1 collection"),
      _g1h(g1h),
      _pss(per_thread_states),
      _queues(task_queues),
      _root_processor(root_processor),
      _terminator(n_workers, _queues),
      _n_workers(n_workers)
  {}

  void work(uint worker_id) {
    if (worker_id >= _n_workers) return;  // no work needed this round

    double start_sec = os::elapsedTime();
    _g1h->g1_policy()->phase_times()->record_time_secs(G1GCPhaseTimes::GCWorkerStart, worker_id, start_sec);

    {
      ResourceMark rm;
      HandleMark   hm;

      ReferenceProcessor*             rp = _g1h->ref_processor_stw();

      G1ParScanThreadState*           pss = _pss->state_for_worker(worker_id);
      pss->set_ref_processor(rp);

      double start_strong_roots_sec = os::elapsedTime();

      _root_processor->evacuate_roots(pss->closures(), worker_id);

      G1ParPushHeapRSClosure push_heap_rs_cl(_g1h, pss);

      // We pass a weak code blobs closure to the remembered set scanning because we want to avoid
      // treating the nmethods visited to act as roots for concurrent marking.
      // We only want to make sure that the oops in the nmethods are adjusted with regard to the
      // objects copied by the current evacuation.
      size_t cards_scanned = _g1h->g1_rem_set()->oops_into_collection_set_do(&push_heap_rs_cl,
                                                                             pss->closures()->weak_codeblobs(),
                                                                             worker_id);

      _pss->add_cards_scanned(worker_id, cards_scanned);

      double strong_roots_sec = os::elapsedTime() - start_strong_roots_sec;

      double term_sec = 0.0;
      size_t evac_term_attempts = 0;
      {
        double start = os::elapsedTime();
        G1ParEvacuateFollowersClosure evac(_g1h, pss, _queues, &_terminator);
        evac.do_void();

        evac_term_attempts = evac.term_attempts();
        term_sec = evac.term_time();
        double elapsed_sec = os::elapsedTime() - start;
        _g1h->g1_policy()->phase_times()->add_time_secs(G1GCPhaseTimes::ObjCopy, worker_id, elapsed_sec - term_sec);
        _g1h->g1_policy()->phase_times()->record_time_secs(G1GCPhaseTimes::Termination, worker_id, term_sec);
        _g1h->g1_policy()->phase_times()->record_thread_work_item(G1GCPhaseTimes::Termination, worker_id, evac_term_attempts);
      }

      assert(pss->queue_is_empty(), "should be empty");

      if (PrintTerminationStats) {
        MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
        size_t lab_waste;
        size_t lab_undo_waste;
        pss->waste(lab_waste, lab_undo_waste);
        _g1h->print_termination_stats(gclog_or_tty,
                                      worker_id,
                                      (os::elapsedTime() - start_sec) * 1000.0,   /* elapsed time */
                                      strong_roots_sec * 1000.0,                  /* strong roots time */
                                      term_sec * 1000.0,                          /* evac term time */
                                      evac_term_attempts,                         /* evac term attempts */
                                      lab_waste,                                  /* alloc buffer waste */
                                      lab_undo_waste                              /* undo waste */
                                      );
      }

      // Close the inner scope so that the ResourceMark and HandleMark
      // destructors are executed here and are included as part of the
      // "GC Worker Time".
    }
    _g1h->g1_policy()->phase_times()->record_time_secs(G1GCPhaseTimes::GCWorkerEnd, worker_id, os::elapsedTime());
  }
};

void G1CollectedHeap::print_termination_stats_hdr(outputStream* const st) {
  st->print_raw_cr("GC Termination Stats");
  st->print_raw_cr("     elapsed  --strong roots-- -------termination------- ------waste (KiB)------");
  st->print_raw_cr("thr     ms        ms      %        ms      %    attempts  total   alloc    undo");
  st->print_raw_cr("--- --------- --------- ------ --------- ------ -------- ------- ------- -------");
}

void G1CollectedHeap::print_termination_stats(outputStream* const st,
                                              uint worker_id,
                                              double elapsed_ms,
                                              double strong_roots_ms,
                                              double term_ms,
                                              size_t term_attempts,
                                              size_t alloc_buffer_waste,
                                              size_t undo_waste) const {
  st->print_cr("%3d %9.2f %9.2f %6.2f "
               "%9.2f %6.2f " SIZE_FORMAT_W(8) " "
               SIZE_FORMAT_W(7) " " SIZE_FORMAT_W(7) " " SIZE_FORMAT_W(7),
               worker_id, elapsed_ms, strong_roots_ms, strong_roots_ms * 100 / elapsed_ms,
               term_ms, term_ms * 100 / elapsed_ms, term_attempts,
               (alloc_buffer_waste + undo_waste) * HeapWordSize / K,
               alloc_buffer_waste * HeapWordSize / K,
               undo_waste * HeapWordSize / K);
}

class G1StringSymbolTableUnlinkTask : public AbstractGangTask {
private:
  BoolObjectClosure* _is_alive;
  int _initial_string_table_size;
  int _initial_symbol_table_size;

  bool  _process_strings;
  int _strings_processed;
  int _strings_removed;

  bool  _process_symbols;
  int _symbols_processed;
  int _symbols_removed;

public:
  G1StringSymbolTableUnlinkTask(BoolObjectClosure* is_alive, bool process_strings, bool process_symbols) :
    AbstractGangTask("String/Symbol Unlinking"),
    _is_alive(is_alive),
    _process_strings(process_strings), _strings_processed(0), _strings_removed(0),
    _process_symbols(process_symbols), _symbols_processed(0), _symbols_removed(0) {

    _initial_string_table_size = StringTable::the_table()->table_size();
    _initial_symbol_table_size = SymbolTable::the_table()->table_size();
    if (process_strings) {
      StringTable::clear_parallel_claimed_index();
    }
    if (process_symbols) {
      SymbolTable::clear_parallel_claimed_index();
    }
  }

  ~G1StringSymbolTableUnlinkTask() {
    guarantee(!_process_strings || StringTable::parallel_claimed_index() >= _initial_string_table_size,
              "claim value %d after unlink less than initial string table size %d",
              StringTable::parallel_claimed_index(), _initial_string_table_size);
    guarantee(!_process_symbols || SymbolTable::parallel_claimed_index() >= _initial_symbol_table_size,
              "claim value %d after unlink less than initial symbol table size %d",
              SymbolTable::parallel_claimed_index(), _initial_symbol_table_size);

    if (G1TraceStringSymbolTableScrubbing) {
      gclog_or_tty->print_cr("Cleaned string and symbol table, "
                             "strings: " SIZE_FORMAT " processed, " SIZE_FORMAT " removed, "
                             "symbols: " SIZE_FORMAT " processed, " SIZE_FORMAT " removed",
                             strings_processed(), strings_removed(),
                             symbols_processed(), symbols_removed());
    }
  }

  void work(uint worker_id) {
    int strings_processed = 0;
    int strings_removed = 0;
    int symbols_processed = 0;
    int symbols_removed = 0;
    if (_process_strings) {
      StringTable::possibly_parallel_unlink(_is_alive, &strings_processed, &strings_removed);
      Atomic::add(strings_processed, &_strings_processed);
      Atomic::add(strings_removed, &_strings_removed);
    }
    if (_process_symbols) {
      SymbolTable::possibly_parallel_unlink(&symbols_processed, &symbols_removed);
      Atomic::add(symbols_processed, &_symbols_processed);
      Atomic::add(symbols_removed, &_symbols_removed);
    }
  }

  size_t strings_processed() const { return (size_t)_strings_processed; }
  size_t strings_removed()   const { return (size_t)_strings_removed; }

  size_t symbols_processed() const { return (size_t)_symbols_processed; }
  size_t symbols_removed()   const { return (size_t)_symbols_removed; }
};

class G1CodeCacheUnloadingTask VALUE_OBJ_CLASS_SPEC {
private:
  static Monitor* _lock;

  BoolObjectClosure* const _is_alive;
  const bool               _unloading_occurred;
  const uint               _num_workers;

  // Variables used to claim nmethods.
  nmethod* _first_nmethod;
  volatile nmethod* _claimed_nmethod;

  // The list of nmethods that need to be processed by the second pass.
  volatile nmethod* _postponed_list;
  volatile uint     _num_entered_barrier;

 public:
  G1CodeCacheUnloadingTask(uint num_workers, BoolObjectClosure* is_alive, bool unloading_occurred) :
      _is_alive(is_alive),
      _unloading_occurred(unloading_occurred),
      _num_workers(num_workers),
      _first_nmethod(NULL),
      _claimed_nmethod(NULL),
      _postponed_list(NULL),
      _num_entered_barrier(0)
  {
    nmethod::increase_unloading_clock();
    // Get first alive nmethod
    NMethodIterator iter = NMethodIterator();
    if(iter.next_alive()) {
      _first_nmethod = iter.method();
    }
    _claimed_nmethod = (volatile nmethod*)_first_nmethod;
  }

  ~G1CodeCacheUnloadingTask() {
    CodeCache::verify_clean_inline_caches();

    CodeCache::set_needs_cache_clean(false);
    guarantee(CodeCache::scavenge_root_nmethods() == NULL, "Must be");

    CodeCache::verify_icholder_relocations();
  }

 private:
  void add_to_postponed_list(nmethod* nm) {
      nmethod* old;
      do {
        old = (nmethod*)_postponed_list;
        nm->set_unloading_next(old);
      } while ((nmethod*)Atomic::cmpxchg_ptr(nm, &_postponed_list, old) != old);
  }

  void clean_nmethod(nmethod* nm) {
    bool postponed = nm->do_unloading_parallel(_is_alive, _unloading_occurred);

    if (postponed) {
      // This nmethod referred to an nmethod that has not been cleaned/unloaded yet.
      add_to_postponed_list(nm);
    }

    // Mark that this thread has been cleaned/unloaded.
    // After this call, it will be safe to ask if this nmethod was unloaded or not.
    nm->set_unloading_clock(nmethod::global_unloading_clock());
  }

  void clean_nmethod_postponed(nmethod* nm) {
    nm->do_unloading_parallel_postponed(_is_alive, _unloading_occurred);
  }

  static const int MaxClaimNmethods = 16;

  void claim_nmethods(nmethod** claimed_nmethods, int *num_claimed_nmethods) {
    nmethod* first;
    NMethodIterator last;

    do {
      *num_claimed_nmethods = 0;

      first = (nmethod*)_claimed_nmethod;
      last = NMethodIterator(first);

      if (first != NULL) {

        for (int i = 0; i < MaxClaimNmethods; i++) {
          if (!last.next_alive()) {
            break;
          }
          claimed_nmethods[i] = last.method();
          (*num_claimed_nmethods)++;
        }
      }

    } while ((nmethod*)Atomic::cmpxchg_ptr(last.method(), &_claimed_nmethod, first) != first);
  }

  nmethod* claim_postponed_nmethod() {
    nmethod* claim;
    nmethod* next;

    do {
      claim = (nmethod*)_postponed_list;
      if (claim == NULL) {
        return NULL;
      }

      next = claim->unloading_next();

    } while ((nmethod*)Atomic::cmpxchg_ptr(next, &_postponed_list, claim) != claim);

    return claim;
  }

 public:
  // Mark that we're done with the first pass of nmethod cleaning.
  void barrier_mark(uint worker_id) {
    MonitorLockerEx ml(_lock, Mutex::_no_safepoint_check_flag);
    _num_entered_barrier++;
    if (_num_entered_barrier == _num_workers) {
      ml.notify_all();
    }
  }

  // See if we have to wait for the other workers to
  // finish their first-pass nmethod cleaning work.
  void barrier_wait(uint worker_id) {
    if (_num_entered_barrier < _num_workers) {
      MonitorLockerEx ml(_lock, Mutex::_no_safepoint_check_flag);
      while (_num_entered_barrier < _num_workers) {
          ml.wait(Mutex::_no_safepoint_check_flag, 0, false);
      }
    }
  }

  // Cleaning and unloading of nmethods. Some work has to be postponed
  // to the second pass, when we know which nmethods survive.
  void work_first_pass(uint worker_id) {
    // The first nmethods is claimed by the first worker.
    if (worker_id == 0 && _first_nmethod != NULL) {
      clean_nmethod(_first_nmethod);
      _first_nmethod = NULL;
    }

    int num_claimed_nmethods;
    nmethod* claimed_nmethods[MaxClaimNmethods];

    while (true) {
      claim_nmethods(claimed_nmethods, &num_claimed_nmethods);

      if (num_claimed_nmethods == 0) {
        break;
      }

      for (int i = 0; i < num_claimed_nmethods; i++) {
        clean_nmethod(claimed_nmethods[i]);
      }
    }
  }

  void work_second_pass(uint worker_id) {
    nmethod* nm;
    // Take care of postponed nmethods.
    while ((nm = claim_postponed_nmethod()) != NULL) {
      clean_nmethod_postponed(nm);
    }
  }
};

Monitor* G1CodeCacheUnloadingTask::_lock = new Monitor(Mutex::leaf, "Code Cache Unload lock", false, Monitor::_safepoint_check_never);

class G1KlassCleaningTask : public StackObj {
  BoolObjectClosure*                      _is_alive;
  volatile jint                           _clean_klass_tree_claimed;
  ClassLoaderDataGraphKlassIteratorAtomic _klass_iterator;

 public:
  G1KlassCleaningTask(BoolObjectClosure* is_alive) :
      _is_alive(is_alive),
      _clean_klass_tree_claimed(0),
      _klass_iterator() {
  }

 private:
  bool claim_clean_klass_tree_task() {
    if (_clean_klass_tree_claimed) {
      return false;
    }

    return Atomic::cmpxchg(1, (jint*)&_clean_klass_tree_claimed, 0) == 0;
  }

  InstanceKlass* claim_next_klass() {
    Klass* klass;
    do {
      klass =_klass_iterator.next_klass();
    } while (klass != NULL && !klass->is_instance_klass());

    // this can be null so don't call InstanceKlass::cast
    return static_cast<InstanceKlass*>(klass);
  }

public:

  void clean_klass(InstanceKlass* ik) {
    ik->clean_weak_instanceklass_links(_is_alive);
  }

  void work() {
    ResourceMark rm;

    // One worker will clean the subklass/sibling klass tree.
    if (claim_clean_klass_tree_task()) {
      Klass::clean_subklass_tree(_is_alive);
    }

    // All workers will help cleaning the classes,
    InstanceKlass* klass;
    while ((klass = claim_next_klass()) != NULL) {
      clean_klass(klass);
    }
  }
};

// To minimize the remark pause times, the tasks below are done in parallel.
class G1ParallelCleaningTask : public AbstractGangTask {
private:
  G1StringSymbolTableUnlinkTask _string_symbol_task;
  G1CodeCacheUnloadingTask      _code_cache_task;
  G1KlassCleaningTask           _klass_cleaning_task;

public:
  // The constructor is run in the VMThread.
  G1ParallelCleaningTask(BoolObjectClosure* is_alive, bool process_strings, bool process_symbols, uint num_workers, bool unloading_occurred) :
      AbstractGangTask("Parallel Cleaning"),
      _string_symbol_task(is_alive, process_strings, process_symbols),
      _code_cache_task(num_workers, is_alive, unloading_occurred),
      _klass_cleaning_task(is_alive) {
  }

  // The parallel work done by all worker threads.
  void work(uint worker_id) {
    // Do first pass of code cache cleaning.
    _code_cache_task.work_first_pass(worker_id);

    // Let the threads mark that the first pass is done.
    _code_cache_task.barrier_mark(worker_id);

    // Clean the Strings and Symbols.
    _string_symbol_task.work(worker_id);

    // Wait for all workers to finish the first code cache cleaning pass.
    _code_cache_task.barrier_wait(worker_id);

    // Do the second code cache cleaning work, which realize on
    // the liveness information gathered during the first pass.
    _code_cache_task.work_second_pass(worker_id);

    // Clean all klasses that were not unloaded.
    _klass_cleaning_task.work();
  }
};


void G1CollectedHeap::parallel_cleaning(BoolObjectClosure* is_alive,
                                        bool process_strings,
                                        bool process_symbols,
                                        bool class_unloading_occurred) {
  uint n_workers = workers()->active_workers();

  G1ParallelCleaningTask g1_unlink_task(is_alive, process_strings, process_symbols,
                                        n_workers, class_unloading_occurred);
  workers()->run_task(&g1_unlink_task);
}

void G1CollectedHeap::unlink_string_and_symbol_table(BoolObjectClosure* is_alive,
                                                     bool process_strings, bool process_symbols) {
  {
    G1StringSymbolTableUnlinkTask g1_unlink_task(is_alive, process_strings, process_symbols);
    workers()->run_task(&g1_unlink_task);
  }

  if (G1StringDedup::is_enabled()) {
    G1StringDedup::unlink(is_alive);
  }
}

class G1RedirtyLoggedCardsTask : public AbstractGangTask {
 private:
  DirtyCardQueueSet* _queue;
 public:
  G1RedirtyLoggedCardsTask(DirtyCardQueueSet* queue) : AbstractGangTask("Redirty Cards"), _queue(queue) { }

  virtual void work(uint worker_id) {
    G1GCPhaseTimes* phase_times = G1CollectedHeap::heap()->g1_policy()->phase_times();
    G1GCParPhaseTimesTracker x(phase_times, G1GCPhaseTimes::RedirtyCards, worker_id);

    RedirtyLoggedCardTableEntryClosure cl;
    _queue->par_apply_closure_to_all_completed_buffers(&cl);

    phase_times->record_thread_work_item(G1GCPhaseTimes::RedirtyCards, worker_id, cl.num_processed());
  }
};

void G1CollectedHeap::redirty_logged_cards() {
  double redirty_logged_cards_start = os::elapsedTime();

  G1RedirtyLoggedCardsTask redirty_task(&dirty_card_queue_set());
  dirty_card_queue_set().reset_for_par_iteration();
  workers()->run_task(&redirty_task);

  DirtyCardQueueSet& dcq = JavaThread::dirty_card_queue_set();
  dcq.merge_bufferlists(&dirty_card_queue_set());
  assert(dirty_card_queue_set().completed_buffers_num() == 0, "All should be consumed");

  g1_policy()->phase_times()->record_redirty_logged_cards_time_ms((os::elapsedTime() - redirty_logged_cards_start) * 1000.0);
}

// Weak Reference Processing support

// An always "is_alive" closure that is used to preserve referents.
// If the object is non-null then it's alive.  Used in the preservation
// of referent objects that are pointed to by reference objects
// discovered by the CM ref processor.
class G1AlwaysAliveClosure: public BoolObjectClosure {
  G1CollectedHeap* _g1;
public:
  G1AlwaysAliveClosure(G1CollectedHeap* g1) : _g1(g1) {}
  bool do_object_b(oop p) {
    if (p != NULL) {
      return true;
    }
    return false;
  }
};

bool G1STWIsAliveClosure::do_object_b(oop p) {
  // An object is reachable if it is outside the collection set,
  // or is inside and copied.
  return !_g1->is_in_cset(p) || p->is_forwarded();
}

// Non Copying Keep Alive closure
class G1KeepAliveClosure: public OopClosure {
  G1CollectedHeap* _g1;
public:
  G1KeepAliveClosure(G1CollectedHeap* g1) : _g1(g1) {}
  void do_oop(narrowOop* p) { guarantee(false, "Not needed"); }
  void do_oop(oop* p) {
    oop obj = *p;
    assert(obj != NULL, "the caller should have filtered out NULL values");

    const InCSetState cset_state = _g1->in_cset_state(obj);
    if (!cset_state.is_in_cset_or_humongous()) {
      return;
    }
    if (cset_state.is_in_cset()) {
      assert( obj->is_forwarded(), "invariant" );
      *p = obj->forwardee();
    } else {
      assert(!obj->is_forwarded(), "invariant" );
      assert(cset_state.is_humongous(),
             "Only allowed InCSet state is IsHumongous, but is %d", cset_state.value());
      _g1->set_humongous_is_live(obj);
    }
  }
};

// Copying Keep Alive closure - can be called from both
// serial and parallel code as long as different worker
// threads utilize different G1ParScanThreadState instances
// and different queues.

class G1CopyingKeepAliveClosure: public OopClosure {
  G1CollectedHeap*         _g1h;
  OopClosure*              _copy_non_heap_obj_cl;
  G1ParScanThreadState*    _par_scan_state;

public:
  G1CopyingKeepAliveClosure(G1CollectedHeap* g1h,
                            OopClosure* non_heap_obj_cl,
                            G1ParScanThreadState* pss):
    _g1h(g1h),
    _copy_non_heap_obj_cl(non_heap_obj_cl),
    _par_scan_state(pss)
  {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }

  template <class T> void do_oop_work(T* p) {
    oop obj = oopDesc::load_decode_heap_oop(p);

    if (_g1h->is_in_cset_or_humongous(obj)) {
      // If the referent object has been forwarded (either copied
      // to a new location or to itself in the event of an
      // evacuation failure) then we need to update the reference
      // field and, if both reference and referent are in the G1
      // heap, update the RSet for the referent.
      //
      // If the referent has not been forwarded then we have to keep
      // it alive by policy. Therefore we have copy the referent.
      //
      // If the reference field is in the G1 heap then we can push
      // on the PSS queue. When the queue is drained (after each
      // phase of reference processing) the object and it's followers
      // will be copied, the reference field set to point to the
      // new location, and the RSet updated. Otherwise we need to
      // use the the non-heap or metadata closures directly to copy
      // the referent object and update the pointer, while avoiding
      // updating the RSet.

      if (_g1h->is_in_g1_reserved(p)) {
        _par_scan_state->push_on_queue(p);
      } else {
        assert(!Metaspace::contains((const void*)p),
               "Unexpectedly found a pointer from metadata: " PTR_FORMAT, p2i(p));
        _copy_non_heap_obj_cl->do_oop(p);
      }
    }
  }
};

// Serial drain queue closure. Called as the 'complete_gc'
// closure for each discovered list in some of the
// reference processing phases.

class G1STWDrainQueueClosure: public VoidClosure {
protected:
  G1CollectedHeap* _g1h;
  G1ParScanThreadState* _par_scan_state;

  G1ParScanThreadState*   par_scan_state() { return _par_scan_state; }

public:
  G1STWDrainQueueClosure(G1CollectedHeap* g1h, G1ParScanThreadState* pss) :
    _g1h(g1h),
    _par_scan_state(pss)
  { }

  void do_void() {
    G1ParScanThreadState* const pss = par_scan_state();
    pss->trim_queue();
  }
};

// Parallel Reference Processing closures

// Implementation of AbstractRefProcTaskExecutor for parallel reference
// processing during G1 evacuation pauses.

class G1STWRefProcTaskExecutor: public AbstractRefProcTaskExecutor {
private:
  G1CollectedHeap*          _g1h;
  G1ParScanThreadStateSet*  _pss;
  RefToScanQueueSet*        _queues;
  WorkGang*                 _workers;
  uint                      _active_workers;

public:
  G1STWRefProcTaskExecutor(G1CollectedHeap* g1h,
                           G1ParScanThreadStateSet* per_thread_states,
                           WorkGang* workers,
                           RefToScanQueueSet *task_queues,
                           uint n_workers) :
    _g1h(g1h),
    _pss(per_thread_states),
    _queues(task_queues),
    _workers(workers),
    _active_workers(n_workers)
  {
    assert(n_workers > 0, "shouldn't call this otherwise");
  }

  // Executes the given task using concurrent marking worker threads.
  virtual void execute(ProcessTask& task);
  virtual void execute(EnqueueTask& task);
};

// Gang task for possibly parallel reference processing

class G1STWRefProcTaskProxy: public AbstractGangTask {
  typedef AbstractRefProcTaskExecutor::ProcessTask ProcessTask;
  ProcessTask&     _proc_task;
  G1CollectedHeap* _g1h;
  G1ParScanThreadStateSet* _pss;
  RefToScanQueueSet* _task_queues;
  ParallelTaskTerminator* _terminator;

public:
  G1STWRefProcTaskProxy(ProcessTask& proc_task,
                        G1CollectedHeap* g1h,
                        G1ParScanThreadStateSet* per_thread_states,
                        RefToScanQueueSet *task_queues,
                        ParallelTaskTerminator* terminator) :
    AbstractGangTask("Process reference objects in parallel"),
    _proc_task(proc_task),
    _g1h(g1h),
    _pss(per_thread_states),
    _task_queues(task_queues),
    _terminator(terminator)
  {}

  virtual void work(uint worker_id) {
    // The reference processing task executed by a single worker.
    ResourceMark rm;
    HandleMark   hm;

    G1STWIsAliveClosure is_alive(_g1h);

    G1ParScanThreadState*          pss = _pss->state_for_worker(worker_id);
    pss->set_ref_processor(NULL);

    // Keep alive closure.
    G1CopyingKeepAliveClosure keep_alive(_g1h, pss->closures()->raw_strong_oops(), pss);

    // Complete GC closure
    G1ParEvacuateFollowersClosure drain_queue(_g1h, pss, _task_queues, _terminator);

    // Call the reference processing task's work routine.
    _proc_task.work(worker_id, is_alive, keep_alive, drain_queue);

    // Note we cannot assert that the refs array is empty here as not all
    // of the processing tasks (specifically phase2 - pp2_work) execute
    // the complete_gc closure (which ordinarily would drain the queue) so
    // the queue may not be empty.
  }
};

// Driver routine for parallel reference processing.
// Creates an instance of the ref processing gang
// task and has the worker threads execute it.
void G1STWRefProcTaskExecutor::execute(ProcessTask& proc_task) {
  assert(_workers != NULL, "Need parallel worker threads.");

  ParallelTaskTerminator terminator(_active_workers, _queues);
  G1STWRefProcTaskProxy proc_task_proxy(proc_task, _g1h, _pss, _queues, &terminator);

  _workers->run_task(&proc_task_proxy);
}

// Gang task for parallel reference enqueueing.

class G1STWRefEnqueueTaskProxy: public AbstractGangTask {
  typedef AbstractRefProcTaskExecutor::EnqueueTask EnqueueTask;
  EnqueueTask& _enq_task;

public:
  G1STWRefEnqueueTaskProxy(EnqueueTask& enq_task) :
    AbstractGangTask("Enqueue reference objects in parallel"),
    _enq_task(enq_task)
  { }

  virtual void work(uint worker_id) {
    _enq_task.work(worker_id);
  }
};

// Driver routine for parallel reference enqueueing.
// Creates an instance of the ref enqueueing gang
// task and has the worker threads execute it.

void G1STWRefProcTaskExecutor::execute(EnqueueTask& enq_task) {
  assert(_workers != NULL, "Need parallel worker threads.");

  G1STWRefEnqueueTaskProxy enq_task_proxy(enq_task);

  _workers->run_task(&enq_task_proxy);
}

// End of weak reference support closures

// Abstract task used to preserve (i.e. copy) any referent objects
// that are in the collection set and are pointed to by reference
// objects discovered by the CM ref processor.

class G1ParPreserveCMReferentsTask: public AbstractGangTask {
protected:
  G1CollectedHeap*         _g1h;
  G1ParScanThreadStateSet* _pss;
  RefToScanQueueSet*       _queues;
  ParallelTaskTerminator   _terminator;
  uint                     _n_workers;

public:
  G1ParPreserveCMReferentsTask(G1CollectedHeap* g1h, G1ParScanThreadStateSet* per_thread_states, int workers, RefToScanQueueSet *task_queues) :
    AbstractGangTask("ParPreserveCMReferents"),
    _g1h(g1h),
    _pss(per_thread_states),
    _queues(task_queues),
    _terminator(workers, _queues),
    _n_workers(workers)
  { }

  void work(uint worker_id) {
    ResourceMark rm;
    HandleMark   hm;

    G1ParScanThreadState*          pss = _pss->state_for_worker(worker_id);
    pss->set_ref_processor(NULL);
    assert(pss->queue_is_empty(), "both queue and overflow should be empty");

    // Is alive closure
    G1AlwaysAliveClosure always_alive(_g1h);

    // Copying keep alive closure. Applied to referent objects that need
    // to be copied.
    G1CopyingKeepAliveClosure keep_alive(_g1h, pss->closures()->raw_strong_oops(), pss);

    ReferenceProcessor* rp = _g1h->ref_processor_cm();

    uint limit = ReferenceProcessor::number_of_subclasses_of_ref() * rp->max_num_q();
    uint stride = MIN2(MAX2(_n_workers, 1U), limit);

    // limit is set using max_num_q() - which was set using ParallelGCThreads.
    // So this must be true - but assert just in case someone decides to
    // change the worker ids.
    assert(worker_id < limit, "sanity");
    assert(!rp->discovery_is_atomic(), "check this code");

    // Select discovered lists [i, i+stride, i+2*stride,...,limit)
    for (uint idx = worker_id; idx < limit; idx += stride) {
      DiscoveredList& ref_list = rp->discovered_refs()[idx];

      DiscoveredListIterator iter(ref_list, &keep_alive, &always_alive);
      while (iter.has_next()) {
        // Since discovery is not atomic for the CM ref processor, we
        // can see some null referent objects.
        iter.load_ptrs(DEBUG_ONLY(true));
        oop ref = iter.obj();

        // This will filter nulls.
        if (iter.is_referent_alive()) {
          iter.make_referent_alive();
        }
        iter.move_to_next();
      }
    }

    // Drain the queue - which may cause stealing
    G1ParEvacuateFollowersClosure drain_queue(_g1h, pss, _queues, &_terminator);
    drain_queue.do_void();
    // Allocation buffers were retired at the end of G1ParEvacuateFollowersClosure
    assert(pss->queue_is_empty(), "should be");
  }
};

// Weak Reference processing during an evacuation pause (part 1).
void G1CollectedHeap::process_discovered_references(G1ParScanThreadStateSet* per_thread_states) {
  double ref_proc_start = os::elapsedTime();

  ReferenceProcessor* rp = _ref_processor_stw;
  assert(rp->discovery_enabled(), "should have been enabled");

  // Any reference objects, in the collection set, that were 'discovered'
  // by the CM ref processor should have already been copied (either by
  // applying the external root copy closure to the discovered lists, or
  // by following an RSet entry).
  //
  // But some of the referents, that are in the collection set, that these
  // reference objects point to may not have been copied: the STW ref
  // processor would have seen that the reference object had already
  // been 'discovered' and would have skipped discovering the reference,
  // but would not have treated the reference object as a regular oop.
  // As a result the copy closure would not have been applied to the
  // referent object.
  //
  // We need to explicitly copy these referent objects - the references
  // will be processed at the end of remarking.
  //
  // We also need to do this copying before we process the reference
  // objects discovered by the STW ref processor in case one of these
  // referents points to another object which is also referenced by an
  // object discovered by the STW ref processor.

  uint no_of_gc_workers = workers()->active_workers();

  G1ParPreserveCMReferentsTask keep_cm_referents(this,
                                                 per_thread_states,
                                                 no_of_gc_workers,
                                                 _task_queues);

  workers()->run_task(&keep_cm_referents);

  // Closure to test whether a referent is alive.
  G1STWIsAliveClosure is_alive(this);

  // Even when parallel reference processing is enabled, the processing
  // of JNI refs is serial and performed serially by the current thread
  // rather than by a worker. The following PSS will be used for processing
  // JNI refs.

  // Use only a single queue for this PSS.
  G1ParScanThreadState*          pss = per_thread_states->state_for_worker(0);
  pss->set_ref_processor(NULL);
  assert(pss->queue_is_empty(), "pre-condition");

  // Keep alive closure.
  G1CopyingKeepAliveClosure keep_alive(this, pss->closures()->raw_strong_oops(), pss);

  // Serial Complete GC closure
  G1STWDrainQueueClosure drain_queue(this, pss);

  // Setup the soft refs policy...
  rp->setup_policy(false);

  ReferenceProcessorStats stats;
  if (!rp->processing_is_mt()) {
    // Serial reference processing...
    stats = rp->process_discovered_references(&is_alive,
                                              &keep_alive,
                                              &drain_queue,
                                              NULL,
                                              _gc_timer_stw);
  } else {
    // Parallel reference processing
    assert(rp->num_q() == no_of_gc_workers, "sanity");
    assert(no_of_gc_workers <= rp->max_num_q(), "sanity");

    G1STWRefProcTaskExecutor par_task_executor(this, per_thread_states, workers(), _task_queues, no_of_gc_workers);
    stats = rp->process_discovered_references(&is_alive,
                                              &keep_alive,
                                              &drain_queue,
                                              &par_task_executor,
                                              _gc_timer_stw);
  }

  _gc_tracer_stw->report_gc_reference_stats(stats);

  // We have completed copying any necessary live referent objects.
  assert(pss->queue_is_empty(), "both queue and overflow should be empty");

  double ref_proc_time = os::elapsedTime() - ref_proc_start;
  g1_policy()->phase_times()->record_ref_proc_time(ref_proc_time * 1000.0);
}

// Weak Reference processing during an evacuation pause (part 2).
void G1CollectedHeap::enqueue_discovered_references(G1ParScanThreadStateSet* per_thread_states) {
  double ref_enq_start = os::elapsedTime();

  ReferenceProcessor* rp = _ref_processor_stw;
  assert(!rp->discovery_enabled(), "should have been disabled as part of processing");

  // Now enqueue any remaining on the discovered lists on to
  // the pending list.
  if (!rp->processing_is_mt()) {
    // Serial reference processing...
    rp->enqueue_discovered_references();
  } else {
    // Parallel reference enqueueing

    uint n_workers = workers()->active_workers();

    assert(rp->num_q() == n_workers, "sanity");
    assert(n_workers <= rp->max_num_q(), "sanity");

    G1STWRefProcTaskExecutor par_task_executor(this, per_thread_states, workers(), _task_queues, n_workers);
    rp->enqueue_discovered_references(&par_task_executor);
  }

  rp->verify_no_references_recorded();
  assert(!rp->discovery_enabled(), "should have been disabled");

  // FIXME
  // CM's reference processing also cleans up the string and symbol tables.
  // Should we do that here also? We could, but it is a serial operation
  // and could significantly increase the pause time.

  double ref_enq_time = os::elapsedTime() - ref_enq_start;
  g1_policy()->phase_times()->record_ref_enq_time(ref_enq_time * 1000.0);
}

void G1CollectedHeap::pre_evacuate_collection_set() {
  _expand_heap_after_alloc_failure = true;
  _evacuation_failed = false;

  // Disable the hot card cache.
  G1HotCardCache* hot_card_cache = _cg1r->hot_card_cache();
  hot_card_cache->reset_hot_cache_claimed_index();
  hot_card_cache->set_use_cache(false);

}

void G1CollectedHeap::evacuate_collection_set(EvacuationInfo& evacuation_info, G1ParScanThreadStateSet* per_thread_states) {
  g1_rem_set()->prepare_for_oops_into_collection_set_do();

  // Should G1EvacuationFailureALot be in effect for this GC?
  NOT_PRODUCT(set_evacuation_failure_alot_for_current_gc();)

  assert(dirty_card_queue_set().completed_buffers_num() == 0, "Should be empty");
  double start_par_time_sec = os::elapsedTime();
  double end_par_time_sec;

  {
    const uint n_workers = workers()->active_workers();
    G1RootProcessor root_processor(this, n_workers);
    G1ParTask g1_par_task(this, per_thread_states, _task_queues, &root_processor, n_workers);
    // InitialMark needs claim bits to keep track of the marked-through CLDs.
    if (collector_state()->during_initial_mark_pause()) {
      ClassLoaderDataGraph::clear_claimed_marks();
    }

    // The individual threads will set their evac-failure closures.
    if (PrintTerminationStats) {
      print_termination_stats_hdr(gclog_or_tty);
    }

    workers()->run_task(&g1_par_task);
    end_par_time_sec = os::elapsedTime();

    // Closing the inner scope will execute the destructor
    // for the G1RootProcessor object. We record the current
    // elapsed time before closing the scope so that time
    // taken for the destructor is NOT included in the
    // reported parallel time.
  }

  G1GCPhaseTimes* phase_times = g1_policy()->phase_times();

  double par_time_ms = (end_par_time_sec - start_par_time_sec) * 1000.0;
  phase_times->record_par_time(par_time_ms);

  double code_root_fixup_time_ms =
        (os::elapsedTime() - end_par_time_sec) * 1000.0;
  phase_times->record_code_root_fixup_time(code_root_fixup_time_ms);

  // Process any discovered reference objects - we have
  // to do this _before_ we retire the GC alloc regions
  // as we may have to copy some 'reachable' referent
  // objects (and their reachable sub-graphs) that were
  // not copied during the pause.
  process_discovered_references(per_thread_states);

  if (G1StringDedup::is_enabled()) {
    double fixup_start = os::elapsedTime();

    G1STWIsAliveClosure is_alive(this);
    G1KeepAliveClosure keep_alive(this);
    G1StringDedup::unlink_or_oops_do(&is_alive, &keep_alive, true, phase_times);

    double fixup_time_ms = (os::elapsedTime() - fixup_start) * 1000.0;
    phase_times->record_string_dedup_fixup_time(fixup_time_ms);
  }

  g1_rem_set()->cleanup_after_oops_into_collection_set_do();

  if (evacuation_failed()) {
    remove_self_forwarding_pointers();

    // Reset the G1EvacuationFailureALot counters and flags
    // Note: the values are reset only when an actual
    // evacuation failure occurs.
    NOT_PRODUCT(reset_evacuation_should_fail();)
  }

  // Enqueue any remaining references remaining on the STW
  // reference processor's discovered lists. We need to do
  // this after the card table is cleaned (and verified) as
  // the act of enqueueing entries on to the pending list
  // will log these updates (and dirty their associated
  // cards). We need these updates logged to update any
  // RSets.
  enqueue_discovered_references(per_thread_states);
}

void G1CollectedHeap::post_evacuate_collection_set(EvacuationInfo& evacuation_info, G1ParScanThreadStateSet* per_thread_states) {
  _allocator->release_gc_alloc_regions(evacuation_info);

  per_thread_states->flush();

  record_obj_copy_mem_stats();

  _survivor_evac_stats.adjust_desired_plab_sz();
  _old_evac_stats.adjust_desired_plab_sz();

  // Reset and re-enable the hot card cache.
  // Note the counts for the cards in the regions in the
  // collection set are reset when the collection set is freed.
  G1HotCardCache* hot_card_cache = _cg1r->hot_card_cache();
  hot_card_cache->reset_hot_cache();
  hot_card_cache->set_use_cache(true);

  purge_code_root_memory();

  redirty_logged_cards();
#if defined(COMPILER2) || INCLUDE_JVMCI
  DerivedPointerTable::update_pointers();
#endif
}

void G1CollectedHeap::record_obj_copy_mem_stats() {
  g1_policy()->add_bytes_allocated_in_old_since_last_gc(_old_evac_stats.allocated() * HeapWordSize);

  _gc_tracer_stw->report_evacuation_statistics(create_g1_evac_summary(&_survivor_evac_stats),
                                               create_g1_evac_summary(&_old_evac_stats));
}

void G1CollectedHeap::free_region(HeapRegion* hr,
                                  FreeRegionList* free_list,
                                  bool par,
                                  bool locked) {
  assert(!hr->is_free(), "the region should not be free");
  assert(!hr->is_empty(), "the region should not be empty");
  assert(_hrm.is_available(hr->hrm_index()), "region should be committed");
  assert(free_list != NULL, "pre-condition");

  if (G1VerifyBitmaps) {
    MemRegion mr(hr->bottom(), hr->end());
    concurrent_mark()->clearRangePrevBitmap(mr);
  }

  // Clear the card counts for this region.
  // Note: we only need to do this if the region is not young
  // (since we don't refine cards in young regions).
  if (!hr->is_young()) {
    _cg1r->hot_card_cache()->reset_card_counts(hr);
  }
  hr->hr_clear(par, true /* clear_space */, locked /* locked */);
  free_list->add_ordered(hr);
}

void G1CollectedHeap::free_humongous_region(HeapRegion* hr,
                                            FreeRegionList* free_list,
                                            bool par) {
  assert(hr->is_humongous(), "this is only for humongous regions");
  assert(free_list != NULL, "pre-condition");
  hr->clear_humongous();
  free_region(hr, free_list, par);
}

void G1CollectedHeap::remove_from_old_sets(const HeapRegionSetCount& old_regions_removed,
                                           const HeapRegionSetCount& humongous_regions_removed) {
  if (old_regions_removed.length() > 0 || humongous_regions_removed.length() > 0) {
    MutexLockerEx x(OldSets_lock, Mutex::_no_safepoint_check_flag);
    _old_set.bulk_remove(old_regions_removed);
    _humongous_set.bulk_remove(humongous_regions_removed);
  }

}

void G1CollectedHeap::prepend_to_freelist(FreeRegionList* list) {
  assert(list != NULL, "list can't be null");
  if (!list->is_empty()) {
    MutexLockerEx x(FreeList_lock, Mutex::_no_safepoint_check_flag);
    _hrm.insert_list_into_free_list(list);
  }
}

void G1CollectedHeap::decrement_summary_bytes(size_t bytes) {
  decrease_used(bytes);
}

class G1ParCleanupCTTask : public AbstractGangTask {
  G1SATBCardTableModRefBS* _ct_bs;
  G1CollectedHeap* _g1h;
  HeapRegion* volatile _su_head;
public:
  G1ParCleanupCTTask(G1SATBCardTableModRefBS* ct_bs,
                     G1CollectedHeap* g1h) :
    AbstractGangTask("G1 Par Cleanup CT Task"),
    _ct_bs(ct_bs), _g1h(g1h) { }

  void work(uint worker_id) {
    HeapRegion* r;
    while (r = _g1h->pop_dirty_cards_region()) {
      clear_cards(r);
    }
  }

  void clear_cards(HeapRegion* r) {
    // Cards of the survivors should have already been dirtied.
    if (!r->is_survivor()) {
      _ct_bs->clear(MemRegion(r->bottom(), r->end()));
    }
  }
};

#ifndef PRODUCT
class G1VerifyCardTableCleanup: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  G1SATBCardTableModRefBS* _ct_bs;
public:
  G1VerifyCardTableCleanup(G1CollectedHeap* g1h, G1SATBCardTableModRefBS* ct_bs)
    : _g1h(g1h), _ct_bs(ct_bs) { }
  virtual bool doHeapRegion(HeapRegion* r) {
    if (r->is_survivor()) {
      _g1h->verify_dirty_region(r);
    } else {
      _g1h->verify_not_dirty_region(r);
    }
    return false;
  }
};

void G1CollectedHeap::verify_not_dirty_region(HeapRegion* hr) {
  // All of the region should be clean.
  G1SATBCardTableModRefBS* ct_bs = g1_barrier_set();
  MemRegion mr(hr->bottom(), hr->end());
  ct_bs->verify_not_dirty_region(mr);
}

void G1CollectedHeap::verify_dirty_region(HeapRegion* hr) {
  // We cannot guarantee that [bottom(),end()] is dirty.  Threads
  // dirty allocated blocks as they allocate them. The thread that
  // retires each region and replaces it with a new one will do a
  // maximal allocation to fill in [pre_dummy_top(),end()] but will
  // not dirty that area (one less thing to have to do while holding
  // a lock). So we can only verify that [bottom(),pre_dummy_top()]
  // is dirty.
  G1SATBCardTableModRefBS* ct_bs = g1_barrier_set();
  MemRegion mr(hr->bottom(), hr->pre_dummy_top());
  if (hr->is_young()) {
    ct_bs->verify_g1_young_region(mr);
  } else {
    ct_bs->verify_dirty_region(mr);
  }
}

void G1CollectedHeap::verify_dirty_young_list(HeapRegion* head) {
  G1SATBCardTableModRefBS* ct_bs = g1_barrier_set();
  for (HeapRegion* hr = head; hr != NULL; hr = hr->get_next_young_region()) {
    verify_dirty_region(hr);
  }
}

void G1CollectedHeap::verify_dirty_young_regions() {
  verify_dirty_young_list(_young_list->first_region());
}

bool G1CollectedHeap::verify_no_bits_over_tams(const char* bitmap_name, CMBitMapRO* bitmap,
                                               HeapWord* tams, HeapWord* end) {
  guarantee(tams <= end,
            "tams: " PTR_FORMAT " end: " PTR_FORMAT, p2i(tams), p2i(end));
  HeapWord* result = bitmap->getNextMarkedWordAddress(tams, end);
  if (result < end) {
    gclog_or_tty->cr();
    gclog_or_tty->print_cr("## wrong marked address on %s bitmap: " PTR_FORMAT,
                           bitmap_name, p2i(result));
    gclog_or_tty->print_cr("## %s tams: " PTR_FORMAT " end: " PTR_FORMAT,
                           bitmap_name, p2i(tams), p2i(end));
    return false;
  }
  return true;
}

bool G1CollectedHeap::verify_bitmaps(const char* caller, HeapRegion* hr) {
  CMBitMapRO* prev_bitmap = concurrent_mark()->prevMarkBitMap();
  CMBitMapRO* next_bitmap = (CMBitMapRO*) concurrent_mark()->nextMarkBitMap();

  HeapWord* bottom = hr->bottom();
  HeapWord* ptams  = hr->prev_top_at_mark_start();
  HeapWord* ntams  = hr->next_top_at_mark_start();
  HeapWord* end    = hr->end();

  bool res_p = verify_no_bits_over_tams("prev", prev_bitmap, ptams, end);

  bool res_n = true;
  // We reset mark_in_progress() before we reset _cmThread->in_progress() and in this window
  // we do the clearing of the next bitmap concurrently. Thus, we can not verify the bitmap
  // if we happen to be in that state.
  if (collector_state()->mark_in_progress() || !_cmThread->in_progress()) {
    res_n = verify_no_bits_over_tams("next", next_bitmap, ntams, end);
  }
  if (!res_p || !res_n) {
    gclog_or_tty->print_cr("#### Bitmap verification failed for " HR_FORMAT,
                           HR_FORMAT_PARAMS(hr));
    gclog_or_tty->print_cr("#### Caller: %s", caller);
    return false;
  }
  return true;
}

void G1CollectedHeap::check_bitmaps(const char* caller, HeapRegion* hr) {
  if (!G1VerifyBitmaps) return;

  guarantee(verify_bitmaps(caller, hr), "bitmap verification");
}

class G1VerifyBitmapClosure : public HeapRegionClosure {
private:
  const char* _caller;
  G1CollectedHeap* _g1h;
  bool _failures;

public:
  G1VerifyBitmapClosure(const char* caller, G1CollectedHeap* g1h) :
    _caller(caller), _g1h(g1h), _failures(false) { }

  bool failures() { return _failures; }

  virtual bool doHeapRegion(HeapRegion* hr) {
    bool result = _g1h->verify_bitmaps(_caller, hr);
    if (!result) {
      _failures = true;
    }
    return false;
  }
};

void G1CollectedHeap::check_bitmaps(const char* caller) {
  if (!G1VerifyBitmaps) return;

  G1VerifyBitmapClosure cl(caller, this);
  heap_region_iterate(&cl);
  guarantee(!cl.failures(), "bitmap verification");
}

class G1CheckCSetFastTableClosure : public HeapRegionClosure {
 private:
  bool _failures;
 public:
  G1CheckCSetFastTableClosure() : HeapRegionClosure(), _failures(false) { }

  virtual bool doHeapRegion(HeapRegion* hr) {
    uint i = hr->hrm_index();
    InCSetState cset_state = (InCSetState) G1CollectedHeap::heap()->_in_cset_fast_test.get_by_index(i);
    if (hr->is_humongous()) {
      if (hr->in_collection_set()) {
        gclog_or_tty->print_cr("\n## humongous region %u in CSet", i);
        _failures = true;
        return true;
      }
      if (cset_state.is_in_cset()) {
        gclog_or_tty->print_cr("\n## inconsistent cset state %d for humongous region %u", cset_state.value(), i);
        _failures = true;
        return true;
      }
      if (hr->is_continues_humongous() && cset_state.is_humongous()) {
        gclog_or_tty->print_cr("\n## inconsistent cset state %d for continues humongous region %u", cset_state.value(), i);
        _failures = true;
        return true;
      }
    } else {
      if (cset_state.is_humongous()) {
        gclog_or_tty->print_cr("\n## inconsistent cset state %d for non-humongous region %u", cset_state.value(), i);
        _failures = true;
        return true;
      }
      if (hr->in_collection_set() != cset_state.is_in_cset()) {
        gclog_or_tty->print_cr("\n## in CSet %d / cset state %d inconsistency for region %u",
                               hr->in_collection_set(), cset_state.value(), i);
        _failures = true;
        return true;
      }
      if (cset_state.is_in_cset()) {
        if (hr->is_young() != (cset_state.is_young())) {
          gclog_or_tty->print_cr("\n## is_young %d / cset state %d inconsistency for region %u",
                                 hr->is_young(), cset_state.value(), i);
          _failures = true;
          return true;
        }
        if (hr->is_old() != (cset_state.is_old())) {
          gclog_or_tty->print_cr("\n## is_old %d / cset state %d inconsistency for region %u",
                                 hr->is_old(), cset_state.value(), i);
          _failures = true;
          return true;
        }
      }
    }
    return false;
  }

  bool failures() const { return _failures; }
};

bool G1CollectedHeap::check_cset_fast_test() {
  G1CheckCSetFastTableClosure cl;
  _hrm.iterate(&cl);
  return !cl.failures();
}
#endif // PRODUCT

void G1CollectedHeap::cleanUpCardTable() {
  G1SATBCardTableModRefBS* ct_bs = g1_barrier_set();
  double start = os::elapsedTime();

  {
    // Iterate over the dirty cards region list.
    G1ParCleanupCTTask cleanup_task(ct_bs, this);

    workers()->run_task(&cleanup_task);
#ifndef PRODUCT
    if (G1VerifyCTCleanup || VerifyAfterGC) {
      G1VerifyCardTableCleanup cleanup_verifier(this, ct_bs);
      heap_region_iterate(&cleanup_verifier);
    }
#endif
  }

  double elapsed = os::elapsedTime() - start;
  g1_policy()->phase_times()->record_clear_ct_time(elapsed * 1000.0);
}

void G1CollectedHeap::free_collection_set(HeapRegion* cs_head, EvacuationInfo& evacuation_info, const size_t* surviving_young_words) {
  size_t pre_used = 0;
  FreeRegionList local_free_list("Local List for CSet Freeing");

  double young_time_ms     = 0.0;
  double non_young_time_ms = 0.0;

  // Since the collection set is a superset of the the young list,
  // all we need to do to clear the young list is clear its
  // head and length, and unlink any young regions in the code below
  _young_list->clear();

  G1CollectorPolicy* policy = g1_policy();

  double start_sec = os::elapsedTime();
  bool non_young = true;

  HeapRegion* cur = cs_head;
  int age_bound = -1;
  size_t rs_lengths = 0;

  while (cur != NULL) {
    assert(!is_on_master_free_list(cur), "sanity");
    if (non_young) {
      if (cur->is_young()) {
        double end_sec = os::elapsedTime();
        double elapsed_ms = (end_sec - start_sec) * 1000.0;
        non_young_time_ms += elapsed_ms;

        start_sec = os::elapsedTime();
        non_young = false;
      }
    } else {
      if (!cur->is_young()) {
        double end_sec = os::elapsedTime();
        double elapsed_ms = (end_sec - start_sec) * 1000.0;
        young_time_ms += elapsed_ms;

        start_sec = os::elapsedTime();
        non_young = true;
      }
    }

    rs_lengths += cur->rem_set()->occupied_locked();

    HeapRegion* next = cur->next_in_collection_set();
    assert(cur->in_collection_set(), "bad CS");
    cur->set_next_in_collection_set(NULL);
    clear_in_cset(cur);

    if (cur->is_young()) {
      int index = cur->young_index_in_cset();
      assert(index != -1, "invariant");
      assert((uint) index < policy->young_cset_region_length(), "invariant");
      size_t words_survived = surviving_young_words[index];
      cur->record_surv_words_in_group(words_survived);

      // At this point the we have 'popped' cur from the collection set
      // (linked via next_in_collection_set()) but it is still in the
      // young list (linked via next_young_region()). Clear the
      // _next_young_region field.
      cur->set_next_young_region(NULL);
    } else {
      int index = cur->young_index_in_cset();
      assert(index == -1, "invariant");
    }

    assert( (cur->is_young() && cur->young_index_in_cset() > -1) ||
            (!cur->is_young() && cur->young_index_in_cset() == -1),
            "invariant" );

    if (!cur->evacuation_failed()) {
      MemRegion used_mr = cur->used_region();

      // And the region is empty.
      assert(!used_mr.is_empty(), "Should not have empty regions in a CS.");
      pre_used += cur->used();
      free_region(cur, &local_free_list, false /* par */, true /* locked */);
    } else {
      cur->uninstall_surv_rate_group();
      if (cur->is_young()) {
        cur->set_young_index_in_cset(-1);
      }
      cur->set_evacuation_failed(false);
      // When moving a young gen region to old gen, we "allocate" that whole region
      // there. This is in addition to any already evacuated objects. Notify the
      // policy about that.
      // Old gen regions do not cause an additional allocation: both the objects
      // still in the region and the ones already moved are accounted for elsewhere.
      if (cur->is_young()) {
        policy->add_bytes_allocated_in_old_since_last_gc(HeapRegion::GrainBytes);
      }
      // The region is now considered to be old.
      cur->set_old();
      // Do some allocation statistics accounting. Regions that failed evacuation
      // are always made old, so there is no need to update anything in the young
      // gen statistics, but we need to update old gen statistics.
      size_t used_words = cur->marked_bytes() / HeapWordSize;
      _old_evac_stats.add_failure_used_and_waste(used_words, HeapRegion::GrainWords - used_words);
      _old_set.add(cur);
      evacuation_info.increment_collectionset_used_after(cur->used());
    }
    cur = next;
  }

  evacuation_info.set_regions_freed(local_free_list.length());
  policy->record_max_rs_lengths(rs_lengths);
  policy->cset_regions_freed();

  double end_sec = os::elapsedTime();
  double elapsed_ms = (end_sec - start_sec) * 1000.0;

  if (non_young) {
    non_young_time_ms += elapsed_ms;
  } else {
    young_time_ms += elapsed_ms;
  }

  prepend_to_freelist(&local_free_list);
  decrement_summary_bytes(pre_used);
  policy->phase_times()->record_young_free_cset_time_ms(young_time_ms);
  policy->phase_times()->record_non_young_free_cset_time_ms(non_young_time_ms);
}

class G1FreeHumongousRegionClosure : public HeapRegionClosure {
 private:
  FreeRegionList* _free_region_list;
  HeapRegionSet* _proxy_set;
  HeapRegionSetCount _humongous_regions_removed;
  size_t _freed_bytes;
 public:

  G1FreeHumongousRegionClosure(FreeRegionList* free_region_list) :
    _free_region_list(free_region_list), _humongous_regions_removed(), _freed_bytes(0) {
  }

  virtual bool doHeapRegion(HeapRegion* r) {
    if (!r->is_starts_humongous()) {
      return false;
    }

    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    oop obj = (oop)r->bottom();
    CMBitMap* next_bitmap = g1h->concurrent_mark()->nextMarkBitMap();

    // The following checks whether the humongous object is live are sufficient.
    // The main additional check (in addition to having a reference from the roots
    // or the young gen) is whether the humongous object has a remembered set entry.
    //
    // A humongous object cannot be live if there is no remembered set for it
    // because:
    // - there can be no references from within humongous starts regions referencing
    // the object because we never allocate other objects into them.
    // (I.e. there are no intra-region references that may be missed by the
    // remembered set)
    // - as soon there is a remembered set entry to the humongous starts region
    // (i.e. it has "escaped" to an old object) this remembered set entry will stay
    // until the end of a concurrent mark.
    //
    // It is not required to check whether the object has been found dead by marking
    // or not, in fact it would prevent reclamation within a concurrent cycle, as
    // all objects allocated during that time are considered live.
    // SATB marking is even more conservative than the remembered set.
    // So if at this point in the collection there is no remembered set entry,
    // nobody has a reference to it.
    // At the start of collection we flush all refinement logs, and remembered sets
    // are completely up-to-date wrt to references to the humongous object.
    //
    // Other implementation considerations:
    // - never consider object arrays at this time because they would pose
    // considerable effort for cleaning up the the remembered sets. This is
    // required because stale remembered sets might reference locations that
    // are currently allocated into.
    uint region_idx = r->hrm_index();
    if (!g1h->is_humongous_reclaim_candidate(region_idx) ||
        !r->rem_set()->is_empty()) {

      if (G1TraceEagerReclaimHumongousObjects) {
        gclog_or_tty->print_cr("Live humongous region %u object size " SIZE_FORMAT " start " PTR_FORMAT "  with remset " SIZE_FORMAT " code roots " SIZE_FORMAT " is marked %d reclaim candidate %d type array %d",
                               region_idx,
                               (size_t)obj->size() * HeapWordSize,
                               p2i(r->bottom()),
                               r->rem_set()->occupied(),
                               r->rem_set()->strong_code_roots_list_length(),
                               next_bitmap->isMarked(r->bottom()),
                               g1h->is_humongous_reclaim_candidate(region_idx),
                               obj->is_typeArray()
                              );
      }

      return false;
    }

    guarantee(obj->is_typeArray(),
              "Only eagerly reclaiming type arrays is supported, but the object "
              PTR_FORMAT " is not.", p2i(r->bottom()));

    if (G1TraceEagerReclaimHumongousObjects) {
      gclog_or_tty->print_cr("Dead humongous region %u object size " SIZE_FORMAT " start " PTR_FORMAT " with remset " SIZE_FORMAT " code roots " SIZE_FORMAT " is marked %d reclaim candidate %d type array %d",
                             region_idx,
                             (size_t)obj->size() * HeapWordSize,
                             p2i(r->bottom()),
                             r->rem_set()->occupied(),
                             r->rem_set()->strong_code_roots_list_length(),
                             next_bitmap->isMarked(r->bottom()),
                             g1h->is_humongous_reclaim_candidate(region_idx),
                             obj->is_typeArray()
                            );
    }
    // Need to clear mark bit of the humongous object if already set.
    if (next_bitmap->isMarked(r->bottom())) {
      next_bitmap->clear(r->bottom());
    }
    do {
      HeapRegion* next = g1h->next_region_in_humongous(r);
      _freed_bytes += r->used();
      r->set_containing_set(NULL);
      _humongous_regions_removed.increment(1u, r->capacity());
      g1h->free_humongous_region(r, _free_region_list, false);
      r = next;
    } while (r != NULL);

    return false;
  }

  HeapRegionSetCount& humongous_free_count() {
    return _humongous_regions_removed;
  }

  size_t bytes_freed() const {
    return _freed_bytes;
  }

  size_t humongous_reclaimed() const {
    return _humongous_regions_removed.length();
  }
};

void G1CollectedHeap::eagerly_reclaim_humongous_regions() {
  assert_at_safepoint(true);

  if (!G1EagerReclaimHumongousObjects ||
      (!_has_humongous_reclaim_candidates && !G1TraceEagerReclaimHumongousObjects)) {
    g1_policy()->phase_times()->record_fast_reclaim_humongous_time_ms(0.0, 0);
    return;
  }

  double start_time = os::elapsedTime();

  FreeRegionList local_cleanup_list("Local Humongous Cleanup List");

  G1FreeHumongousRegionClosure cl(&local_cleanup_list);
  heap_region_iterate(&cl);

  HeapRegionSetCount empty_set;
  remove_from_old_sets(empty_set, cl.humongous_free_count());

  G1HRPrinter* hrp = hr_printer();
  if (hrp->is_active()) {
    FreeRegionListIterator iter(&local_cleanup_list);
    while (iter.more_available()) {
      HeapRegion* hr = iter.get_next();
      hrp->cleanup(hr);
    }
  }

  prepend_to_freelist(&local_cleanup_list);
  decrement_summary_bytes(cl.bytes_freed());

  g1_policy()->phase_times()->record_fast_reclaim_humongous_time_ms((os::elapsedTime() - start_time) * 1000.0,
                                                                    cl.humongous_reclaimed());
}

// This routine is similar to the above but does not record
// any policy statistics or update free lists; we are abandoning
// the current incremental collection set in preparation of a
// full collection. After the full GC we will start to build up
// the incremental collection set again.
// This is only called when we're doing a full collection
// and is immediately followed by the tearing down of the young list.

void G1CollectedHeap::abandon_collection_set(HeapRegion* cs_head) {
  HeapRegion* cur = cs_head;

  while (cur != NULL) {
    HeapRegion* next = cur->next_in_collection_set();
    assert(cur->in_collection_set(), "bad CS");
    cur->set_next_in_collection_set(NULL);
    clear_in_cset(cur);
    cur->set_young_index_in_cset(-1);
    cur = next;
  }
}

void G1CollectedHeap::set_free_regions_coming() {
  if (G1ConcRegionFreeingVerbose) {
    gclog_or_tty->print_cr("G1ConcRegionFreeing [cm thread] : "
                           "setting free regions coming");
  }

  assert(!free_regions_coming(), "pre-condition");
  _free_regions_coming = true;
}

void G1CollectedHeap::reset_free_regions_coming() {
  assert(free_regions_coming(), "pre-condition");

  {
    MutexLockerEx x(SecondaryFreeList_lock, Mutex::_no_safepoint_check_flag);
    _free_regions_coming = false;
    SecondaryFreeList_lock->notify_all();
  }

  if (G1ConcRegionFreeingVerbose) {
    gclog_or_tty->print_cr("G1ConcRegionFreeing [cm thread] : "
                           "reset free regions coming");
  }
}

void G1CollectedHeap::wait_while_free_regions_coming() {
  // Most of the time we won't have to wait, so let's do a quick test
  // first before we take the lock.
  if (!free_regions_coming()) {
    return;
  }

  if (G1ConcRegionFreeingVerbose) {
    gclog_or_tty->print_cr("G1ConcRegionFreeing [other] : "
                           "waiting for free regions");
  }

  {
    MutexLockerEx x(SecondaryFreeList_lock, Mutex::_no_safepoint_check_flag);
    while (free_regions_coming()) {
      SecondaryFreeList_lock->wait(Mutex::_no_safepoint_check_flag);
    }
  }

  if (G1ConcRegionFreeingVerbose) {
    gclog_or_tty->print_cr("G1ConcRegionFreeing [other] : "
                           "done waiting for free regions");
  }
}

bool G1CollectedHeap::is_old_gc_alloc_region(HeapRegion* hr) {
  return _allocator->is_retained_old_region(hr);
}

void G1CollectedHeap::set_region_short_lived_locked(HeapRegion* hr) {
  _young_list->push_region(hr);
}

class NoYoungRegionsClosure: public HeapRegionClosure {
private:
  bool _success;
public:
  NoYoungRegionsClosure() : _success(true) { }
  bool doHeapRegion(HeapRegion* r) {
    if (r->is_young()) {
      gclog_or_tty->print_cr("Region [" PTR_FORMAT ", " PTR_FORMAT ") tagged as young",
                             p2i(r->bottom()), p2i(r->end()));
      _success = false;
    }
    return false;
  }
  bool success() { return _success; }
};

bool G1CollectedHeap::check_young_list_empty(bool check_heap, bool check_sample) {
  bool ret = _young_list->check_list_empty(check_sample);

  if (check_heap) {
    NoYoungRegionsClosure closure;
    heap_region_iterate(&closure);
    ret = ret && closure.success();
  }

  return ret;
}

class TearDownRegionSetsClosure : public HeapRegionClosure {
private:
  HeapRegionSet *_old_set;

public:
  TearDownRegionSetsClosure(HeapRegionSet* old_set) : _old_set(old_set) { }

  bool doHeapRegion(HeapRegion* r) {
    if (r->is_old()) {
      _old_set->remove(r);
    } else {
      // We ignore free regions, we'll empty the free list afterwards.
      // We ignore young regions, we'll empty the young list afterwards.
      // We ignore humongous regions, we're not tearing down the
      // humongous regions set.
      assert(r->is_free() || r->is_young() || r->is_humongous(),
             "it cannot be another type");
    }
    return false;
  }

  ~TearDownRegionSetsClosure() {
    assert(_old_set->is_empty(), "post-condition");
  }
};

void G1CollectedHeap::tear_down_region_sets(bool free_list_only) {
  assert_at_safepoint(true /* should_be_vm_thread */);

  if (!free_list_only) {
    TearDownRegionSetsClosure cl(&_old_set);
    heap_region_iterate(&cl);

    // Note that emptying the _young_list is postponed and instead done as
    // the first step when rebuilding the regions sets again. The reason for
    // this is that during a full GC string deduplication needs to know if
    // a collected region was young or old when the full GC was initiated.
  }
  _hrm.remove_all_free_regions();
}

void G1CollectedHeap::increase_used(size_t bytes) {
  _summary_bytes_used += bytes;
}

void G1CollectedHeap::decrease_used(size_t bytes) {
  assert(_summary_bytes_used >= bytes,
         "invariant: _summary_bytes_used: " SIZE_FORMAT " should be >= bytes: " SIZE_FORMAT,
         _summary_bytes_used, bytes);
  _summary_bytes_used -= bytes;
}

void G1CollectedHeap::set_used(size_t bytes) {
  _summary_bytes_used = bytes;
}

class RebuildRegionSetsClosure : public HeapRegionClosure {
private:
  bool            _free_list_only;
  HeapRegionSet*   _old_set;
  HeapRegionManager*   _hrm;
  size_t          _total_used;

public:
  RebuildRegionSetsClosure(bool free_list_only,
                           HeapRegionSet* old_set, HeapRegionManager* hrm) :
    _free_list_only(free_list_only),
    _old_set(old_set), _hrm(hrm), _total_used(0) {
    assert(_hrm->num_free_regions() == 0, "pre-condition");
    if (!free_list_only) {
      assert(_old_set->is_empty(), "pre-condition");
    }
  }

  bool doHeapRegion(HeapRegion* r) {
    if (r->is_empty()) {
      // Add free regions to the free list
      r->set_free();
      r->set_allocation_context(AllocationContext::system());
      _hrm->insert_into_free_list(r);
    } else if (!_free_list_only) {
      assert(!r->is_young(), "we should not come across young regions");

      if (r->is_humongous()) {
        // We ignore humongous regions. We left the humongous set unchanged.
      } else {
        // Objects that were compacted would have ended up on regions
        // that were previously old or free.  Archive regions (which are
        // old) will not have been touched.
        assert(r->is_free() || r->is_old(), "invariant");
        // We now consider them old, so register as such. Leave
        // archive regions set that way, however, while still adding
        // them to the old set.
        if (!r->is_archive()) {
          r->set_old();
        }
        _old_set->add(r);
      }
      _total_used += r->used();
    }

    return false;
  }

  size_t total_used() {
    return _total_used;
  }
};

void G1CollectedHeap::rebuild_region_sets(bool free_list_only) {
  assert_at_safepoint(true /* should_be_vm_thread */);

  if (!free_list_only) {
    _young_list->empty_list();
  }

  RebuildRegionSetsClosure cl(free_list_only, &_old_set, &_hrm);
  heap_region_iterate(&cl);

  if (!free_list_only) {
    set_used(cl.total_used());
    if (_archive_allocator != NULL) {
      _archive_allocator->clear_used();
    }
  }
  assert(used_unlocked() == recalculate_used(),
         "inconsistent used_unlocked(), "
         "value: " SIZE_FORMAT " recalculated: " SIZE_FORMAT,
         used_unlocked(), recalculate_used());
}

void G1CollectedHeap::set_refine_cte_cl_concurrency(bool concurrent) {
  _refine_cte_cl->set_concurrent(concurrent);
}

bool G1CollectedHeap::is_in_closed_subset(const void* p) const {
  HeapRegion* hr = heap_region_containing(p);
  return hr->is_in(p);
}

// Methods for the mutator alloc region

HeapRegion* G1CollectedHeap::new_mutator_alloc_region(size_t word_size,
                                                      bool force) {
  assert_heap_locked_or_at_safepoint(true /* should_be_vm_thread */);
  assert(!force || g1_policy()->can_expand_young_list(),
         "if force is true we should be able to expand the young list");
  bool young_list_full = g1_policy()->is_young_list_full();
  if (force || !young_list_full) {
    HeapRegion* new_alloc_region = new_region(word_size,
                                              false /* is_old */,
                                              false /* do_expand */);
    if (new_alloc_region != NULL) {
      set_region_short_lived_locked(new_alloc_region);
      _hr_printer.alloc(new_alloc_region, G1HRPrinter::Eden, young_list_full);
      check_bitmaps("Mutator Region Allocation", new_alloc_region);
      return new_alloc_region;
    }
  }
  return NULL;
}

void G1CollectedHeap::retire_mutator_alloc_region(HeapRegion* alloc_region,
                                                  size_t allocated_bytes) {
  assert_heap_locked_or_at_safepoint(true /* should_be_vm_thread */);
  assert(alloc_region->is_eden(), "all mutator alloc regions should be eden");

  g1_policy()->add_region_to_incremental_cset_lhs(alloc_region);
  increase_used(allocated_bytes);
  _hr_printer.retire(alloc_region);
  // We update the eden sizes here, when the region is retired,
  // instead of when it's allocated, since this is the point that its
  // used space has been recored in _summary_bytes_used.
  g1mm()->update_eden_size();
}

// Methods for the GC alloc regions

HeapRegion* G1CollectedHeap::new_gc_alloc_region(size_t word_size,
                                                 uint count,
                                                 InCSetState dest) {
  assert(FreeList_lock->owned_by_self(), "pre-condition");

  if (count < g1_policy()->max_regions(dest)) {
    const bool is_survivor = (dest.is_young());
    HeapRegion* new_alloc_region = new_region(word_size,
                                              !is_survivor,
                                              true /* do_expand */);
    if (new_alloc_region != NULL) {
      // We really only need to do this for old regions given that we
      // should never scan survivors. But it doesn't hurt to do it
      // for survivors too.
      new_alloc_region->record_timestamp();
      if (is_survivor) {
        new_alloc_region->set_survivor();
        _hr_printer.alloc(new_alloc_region, G1HRPrinter::Survivor);
        check_bitmaps("Survivor Region Allocation", new_alloc_region);
      } else {
        new_alloc_region->set_old();
        _hr_printer.alloc(new_alloc_region, G1HRPrinter::Old);
        check_bitmaps("Old Region Allocation", new_alloc_region);
      }
      bool during_im = collector_state()->during_initial_mark_pause();
      new_alloc_region->note_start_of_copying(during_im);
      return new_alloc_region;
    }
  }
  return NULL;
}

void G1CollectedHeap::retire_gc_alloc_region(HeapRegion* alloc_region,
                                             size_t allocated_bytes,
                                             InCSetState dest) {
  bool during_im = collector_state()->during_initial_mark_pause();
  alloc_region->note_end_of_copying(during_im);
  g1_policy()->record_bytes_copied_during_gc(allocated_bytes);
  if (dest.is_young()) {
    young_list()->add_survivor_region(alloc_region);
  } else {
    _old_set.add(alloc_region);
  }
  _hr_printer.retire(alloc_region);
}

HeapRegion* G1CollectedHeap::alloc_highest_free_region() {
  bool expanded = false;
  uint index = _hrm.find_highest_free(&expanded);

  if (index != G1_NO_HRM_INDEX) {
    if (expanded) {
      ergo_verbose1(ErgoHeapSizing,
                    "attempt heap expansion",
                    ergo_format_reason("requested address range outside heap bounds")
                    ergo_format_byte("region size"),
                    HeapRegion::GrainWords * HeapWordSize);
    }
    _hrm.allocate_free_regions_starting_at(index, 1);
    return region_at(index);
  }
  return NULL;
}

// Heap region set verification

class VerifyRegionListsClosure : public HeapRegionClosure {
private:
  HeapRegionSet*   _old_set;
  HeapRegionSet*   _humongous_set;
  HeapRegionManager*   _hrm;

public:
  HeapRegionSetCount _old_count;
  HeapRegionSetCount _humongous_count;
  HeapRegionSetCount _free_count;

  VerifyRegionListsClosure(HeapRegionSet* old_set,
                           HeapRegionSet* humongous_set,
                           HeapRegionManager* hrm) :
    _old_set(old_set), _humongous_set(humongous_set), _hrm(hrm),
    _old_count(), _humongous_count(), _free_count(){ }

  bool doHeapRegion(HeapRegion* hr) {
    if (hr->is_young()) {
      // TODO
    } else if (hr->is_humongous()) {
      assert(hr->containing_set() == _humongous_set, "Heap region %u is humongous but not in humongous set.", hr->hrm_index());
      _humongous_count.increment(1u, hr->capacity());
    } else if (hr->is_empty()) {
      assert(_hrm->is_free(hr), "Heap region %u is empty but not on the free list.", hr->hrm_index());
      _free_count.increment(1u, hr->capacity());
    } else if (hr->is_old()) {
      assert(hr->containing_set() == _old_set, "Heap region %u is old but not in the old set.", hr->hrm_index());
      _old_count.increment(1u, hr->capacity());
    } else {
      // There are no other valid region types. Check for one invalid
      // one we can identify: pinned without old or humongous set.
      assert(!hr->is_pinned(), "Heap region %u is pinned but not old (archive) or humongous.", hr->hrm_index());
      ShouldNotReachHere();
    }
    return false;
  }

  void verify_counts(HeapRegionSet* old_set, HeapRegionSet* humongous_set, HeapRegionManager* free_list) {
    guarantee(old_set->length() == _old_count.length(), "Old set count mismatch. Expected %u, actual %u.", old_set->length(), _old_count.length());
    guarantee(old_set->total_capacity_bytes() == _old_count.capacity(), "Old set capacity mismatch. Expected " SIZE_FORMAT ", actual " SIZE_FORMAT,
              old_set->total_capacity_bytes(), _old_count.capacity());

    guarantee(humongous_set->length() == _humongous_count.length(), "Hum set count mismatch. Expected %u, actual %u.", humongous_set->length(), _humongous_count.length());
    guarantee(humongous_set->total_capacity_bytes() == _humongous_count.capacity(), "Hum set capacity mismatch. Expected " SIZE_FORMAT ", actual " SIZE_FORMAT,
              humongous_set->total_capacity_bytes(), _humongous_count.capacity());

    guarantee(free_list->num_free_regions() == _free_count.length(), "Free list count mismatch. Expected %u, actual %u.", free_list->num_free_regions(), _free_count.length());
    guarantee(free_list->total_capacity_bytes() == _free_count.capacity(), "Free list capacity mismatch. Expected " SIZE_FORMAT ", actual " SIZE_FORMAT,
              free_list->total_capacity_bytes(), _free_count.capacity());
  }
};

void G1CollectedHeap::verify_region_sets() {
  assert_heap_locked_or_at_safepoint(true /* should_be_vm_thread */);

  // First, check the explicit lists.
  _hrm.verify();
  {
    // Given that a concurrent operation might be adding regions to
    // the secondary free list we have to take the lock before
    // verifying it.
    MutexLockerEx x(SecondaryFreeList_lock, Mutex::_no_safepoint_check_flag);
    _secondary_free_list.verify_list();
  }

  // If a concurrent region freeing operation is in progress it will
  // be difficult to correctly attributed any free regions we come
  // across to the correct free list given that they might belong to
  // one of several (free_list, secondary_free_list, any local lists,
  // etc.). So, if that's the case we will skip the rest of the
  // verification operation. Alternatively, waiting for the concurrent
  // operation to complete will have a non-trivial effect on the GC's
  // operation (no concurrent operation will last longer than the
  // interval between two calls to verification) and it might hide
  // any issues that we would like to catch during testing.
  if (free_regions_coming()) {
    return;
  }

  // Make sure we append the secondary_free_list on the free_list so
  // that all free regions we will come across can be safely
  // attributed to the free_list.
  append_secondary_free_list_if_not_empty_with_lock();

  // Finally, make sure that the region accounting in the lists is
  // consistent with what we see in the heap.

  VerifyRegionListsClosure cl(&_old_set, &_humongous_set, &_hrm);
  heap_region_iterate(&cl);
  cl.verify_counts(&_old_set, &_humongous_set, &_hrm);
}

// Optimized nmethod scanning

class RegisterNMethodOopClosure: public OopClosure {
  G1CollectedHeap* _g1h;
  nmethod* _nm;

  template <class T> void do_oop_work(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      HeapRegion* hr = _g1h->heap_region_containing(obj);
      assert(!hr->is_continues_humongous(),
             "trying to add code root " PTR_FORMAT " in continuation of humongous region " HR_FORMAT
             " starting at " HR_FORMAT,
             p2i(_nm), HR_FORMAT_PARAMS(hr), HR_FORMAT_PARAMS(hr->humongous_start_region()));

      // HeapRegion::add_strong_code_root_locked() avoids adding duplicate entries.
      hr->add_strong_code_root_locked(_nm);
    }
  }

public:
  RegisterNMethodOopClosure(G1CollectedHeap* g1h, nmethod* nm) :
    _g1h(g1h), _nm(nm) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

class UnregisterNMethodOopClosure: public OopClosure {
  G1CollectedHeap* _g1h;
  nmethod* _nm;

  template <class T> void do_oop_work(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      HeapRegion* hr = _g1h->heap_region_containing(obj);
      assert(!hr->is_continues_humongous(),
             "trying to remove code root " PTR_FORMAT " in continuation of humongous region " HR_FORMAT
             " starting at " HR_FORMAT,
             p2i(_nm), HR_FORMAT_PARAMS(hr), HR_FORMAT_PARAMS(hr->humongous_start_region()));

      hr->remove_strong_code_root(_nm);
    }
  }

public:
  UnregisterNMethodOopClosure(G1CollectedHeap* g1h, nmethod* nm) :
    _g1h(g1h), _nm(nm) {}

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

void G1CollectedHeap::register_nmethod(nmethod* nm) {
  CollectedHeap::register_nmethod(nm);

  guarantee(nm != NULL, "sanity");
  RegisterNMethodOopClosure reg_cl(this, nm);
  nm->oops_do(&reg_cl);
}

void G1CollectedHeap::unregister_nmethod(nmethod* nm) {
  CollectedHeap::unregister_nmethod(nm);

  guarantee(nm != NULL, "sanity");
  UnregisterNMethodOopClosure reg_cl(this, nm);
  nm->oops_do(&reg_cl, true);
}

void G1CollectedHeap::purge_code_root_memory() {
  double purge_start = os::elapsedTime();
  G1CodeRootSet::purge();
  double purge_time_ms = (os::elapsedTime() - purge_start) * 1000.0;
  g1_policy()->phase_times()->record_strong_code_root_purge_time(purge_time_ms);
}

class RebuildStrongCodeRootClosure: public CodeBlobClosure {
  G1CollectedHeap* _g1h;

public:
  RebuildStrongCodeRootClosure(G1CollectedHeap* g1h) :
    _g1h(g1h) {}

  void do_code_blob(CodeBlob* cb) {
    nmethod* nm = (cb != NULL) ? cb->as_nmethod_or_null() : NULL;
    if (nm == NULL) {
      return;
    }

    if (ScavengeRootsInCode) {
      _g1h->register_nmethod(nm);
    }
  }
};

void G1CollectedHeap::rebuild_strong_code_roots() {
  RebuildStrongCodeRootClosure blob_cl(this);
  CodeCache::blobs_do(&blob_cl);
}

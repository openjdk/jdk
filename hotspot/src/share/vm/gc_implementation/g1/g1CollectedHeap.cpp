/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeCache.hpp"
#include "code/icBuffer.hpp"
#include "gc_implementation/g1/bufferingOopClosure.hpp"
#include "gc_implementation/g1/concurrentG1Refine.hpp"
#include "gc_implementation/g1/concurrentG1RefineThread.hpp"
#include "gc_implementation/g1/concurrentMarkThread.inline.hpp"
#include "gc_implementation/g1/g1AllocRegion.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/g1ErgoVerbose.hpp"
#include "gc_implementation/g1/g1EvacFailure.hpp"
#include "gc_implementation/g1/g1GCPhaseTimes.hpp"
#include "gc_implementation/g1/g1Log.hpp"
#include "gc_implementation/g1/g1MarkSweep.hpp"
#include "gc_implementation/g1/g1OopClosures.inline.hpp"
#include "gc_implementation/g1/g1RemSet.inline.hpp"
#include "gc_implementation/g1/g1YCTypes.hpp"
#include "gc_implementation/g1/heapRegion.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "gc_implementation/g1/vm_operations_g1.hpp"
#include "gc_implementation/shared/gcHeapSummary.hpp"
#include "gc_implementation/shared/gcTimer.hpp"
#include "gc_implementation/shared/gcTrace.hpp"
#include "gc_implementation/shared/gcTraceTime.hpp"
#include "gc_implementation/shared/isGCActiveMark.hpp"
#include "memory/gcLocker.inline.hpp"
#include "memory/generationSpec.hpp"
#include "memory/iterator.hpp"
#include "memory/referenceProcessor.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.pcgc.inline.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/ticks.hpp"

size_t G1CollectedHeap::_humongous_object_threshold_in_words = 0;

// turn it on so that the contents of the young list (scan-only /
// to-be-collected) are printed at "strategic" points before / during
// / after the collection --- this is useful for debugging
#define YOUNG_LIST_VERBOSE 0
// CURRENT STATUS
// This file is under construction.  Search for "FIXME".

// INVARIANTS/NOTES
//
// All allocation activity covered by the G1CollectedHeap interface is
// serialized by acquiring the HeapLock.  This happens in mem_allocate
// and allocate_new_tlab, which are the "entry" points to the
// allocation code from the rest of the JVM.  (Note that this does not
// apply to TLAB allocation, which is not part of this interface: it
// is done by clients of this interface.)

// Notes on implementation of parallelism in different tasks.
//
// G1ParVerifyTask uses heap_region_par_iterate_chunked() for parallelism.
// The number of GC workers is passed to heap_region_par_iterate_chunked().
// It does use run_task() which sets _n_workers in the task.
// G1ParTask executes g1_process_strong_roots() ->
// SharedHeap::process_strong_roots() which calls eventually to
// CardTableModRefBS::par_non_clean_card_iterate_work() which uses
// SequentialSubTasksDone.  SharedHeap::process_strong_roots() also
// directly uses SubTasksDone (_process_strong_tasks field in SharedHeap).
//

// Local to this file.

class RefineCardTableEntryClosure: public CardTableEntryClosure {
  SuspendibleThreadSet* _sts;
  G1RemSet* _g1rs;
  ConcurrentG1Refine* _cg1r;
  bool _concurrent;
public:
  RefineCardTableEntryClosure(SuspendibleThreadSet* sts,
                              G1RemSet* g1rs,
                              ConcurrentG1Refine* cg1r) :
    _sts(sts), _g1rs(g1rs), _cg1r(cg1r), _concurrent(true)
  {}
  bool do_card_ptr(jbyte* card_ptr, int worker_i) {
    bool oops_into_cset = _g1rs->refine_card(card_ptr, worker_i, false);
    // This path is executed by the concurrent refine or mutator threads,
    // concurrently, and so we do not care if card_ptr contains references
    // that point into the collection set.
    assert(!oops_into_cset, "should be");

    if (_concurrent && _sts->should_yield()) {
      // Caller will actually yield.
      return false;
    }
    // Otherwise, we finished successfully; return true.
    return true;
  }
  void set_concurrent(bool b) { _concurrent = b; }
};


class ClearLoggedCardTableEntryClosure: public CardTableEntryClosure {
  int _calls;
  G1CollectedHeap* _g1h;
  CardTableModRefBS* _ctbs;
  int _histo[256];
public:
  ClearLoggedCardTableEntryClosure() :
    _calls(0), _g1h(G1CollectedHeap::heap()), _ctbs(_g1h->g1_barrier_set())
  {
    for (int i = 0; i < 256; i++) _histo[i] = 0;
  }
  bool do_card_ptr(jbyte* card_ptr, int worker_i) {
    if (_g1h->is_in_reserved(_ctbs->addr_for(card_ptr))) {
      _calls++;
      unsigned char* ujb = (unsigned char*)card_ptr;
      int ind = (int)(*ujb);
      _histo[ind]++;
      *card_ptr = -1;
    }
    return true;
  }
  int calls() { return _calls; }
  void print_histo() {
    gclog_or_tty->print_cr("Card table value histogram:");
    for (int i = 0; i < 256; i++) {
      if (_histo[i] != 0) {
        gclog_or_tty->print_cr("  %d: %d", i, _histo[i]);
      }
    }
  }
};

class RedirtyLoggedCardTableEntryClosure: public CardTableEntryClosure {
  int _calls;
  G1CollectedHeap* _g1h;
  CardTableModRefBS* _ctbs;
public:
  RedirtyLoggedCardTableEntryClosure() :
    _calls(0), _g1h(G1CollectedHeap::heap()), _ctbs(_g1h->g1_barrier_set()) {}

  bool do_card_ptr(jbyte* card_ptr, int worker_i) {
    if (_g1h->is_in_reserved(_ctbs->addr_for(card_ptr))) {
      _calls++;
      *card_ptr = 0;
    }
    return true;
  }
  int calls() { return _calls; }
};

class RedirtyLoggedCardTableEntryFastClosure : public CardTableEntryClosure {
public:
  bool do_card_ptr(jbyte* card_ptr, int worker_i) {
    *card_ptr = CardTableModRefBS::dirty_card_val();
    return true;
  }
};

YoungList::YoungList(G1CollectedHeap* g1h) :
    _g1h(g1h), _head(NULL), _length(0), _last_sampled_rs_lengths(0),
    _survivor_head(NULL), _survivor_tail(NULL), _survivor_length(0) {
  guarantee(check_list_empty(false), "just making sure...");
}

void YoungList::push_region(HeapRegion *hr) {
  assert(!hr->is_young(), "should not already be young");
  assert(hr->get_next_young_region() == NULL, "cause it should!");

  hr->set_next_young_region(_head);
  _head = hr;

  _g1h->g1_policy()->set_region_eden(hr, (int) _length);
  ++_length;
}

void YoungList::add_survivor_region(HeapRegion* hr) {
  assert(hr->is_survivor(), "should be flagged as survivor region");
  assert(hr->get_next_young_region() == NULL, "cause it should!");

  hr->set_next_young_region(_survivor_head);
  if (_survivor_head == NULL) {
    _survivor_tail = hr;
  }
  _survivor_head = hr;
  ++_survivor_length;
}

void YoungList::empty_list(HeapRegion* list) {
  while (list != NULL) {
    HeapRegion* next = list->get_next_young_region();
    list->set_next_young_region(NULL);
    list->uninstall_surv_rate_group();
    list->set_not_young();
    list = next;
  }
}

void YoungList::empty_list() {
  assert(check_list_well_formed(), "young list should be well formed");

  empty_list(_head);
  _head = NULL;
  _length = 0;

  empty_list(_survivor_head);
  _survivor_head = NULL;
  _survivor_tail = NULL;
  _survivor_length = 0;

  _last_sampled_rs_lengths = 0;

  assert(check_list_empty(false), "just making sure...");
}

bool YoungList::check_list_well_formed() {
  bool ret = true;

  uint length = 0;
  HeapRegion* curr = _head;
  HeapRegion* last = NULL;
  while (curr != NULL) {
    if (!curr->is_young()) {
      gclog_or_tty->print_cr("### YOUNG REGION "PTR_FORMAT"-"PTR_FORMAT" "
                             "incorrectly tagged (y: %d, surv: %d)",
                             curr->bottom(), curr->end(),
                             curr->is_young(), curr->is_survivor());
      ret = false;
    }
    ++length;
    last = curr;
    curr = curr->get_next_young_region();
  }
  ret = ret && (length == _length);

  if (!ret) {
    gclog_or_tty->print_cr("### YOUNG LIST seems not well formed!");
    gclog_or_tty->print_cr("###   list has %u entries, _length is %u",
                           length, _length);
  }

  return ret;
}

bool YoungList::check_list_empty(bool check_sample) {
  bool ret = true;

  if (_length != 0) {
    gclog_or_tty->print_cr("### YOUNG LIST should have 0 length, not %u",
                  _length);
    ret = false;
  }
  if (check_sample && _last_sampled_rs_lengths != 0) {
    gclog_or_tty->print_cr("### YOUNG LIST has non-zero last sampled RS lengths");
    ret = false;
  }
  if (_head != NULL) {
    gclog_or_tty->print_cr("### YOUNG LIST does not have a NULL head");
    ret = false;
  }
  if (!ret) {
    gclog_or_tty->print_cr("### YOUNG LIST does not seem empty");
  }

  return ret;
}

void
YoungList::rs_length_sampling_init() {
  _sampled_rs_lengths = 0;
  _curr               = _head;
}

bool
YoungList::rs_length_sampling_more() {
  return _curr != NULL;
}

void
YoungList::rs_length_sampling_next() {
  assert( _curr != NULL, "invariant" );
  size_t rs_length = _curr->rem_set()->occupied();

  _sampled_rs_lengths += rs_length;

  // The current region may not yet have been added to the
  // incremental collection set (it gets added when it is
  // retired as the current allocation region).
  if (_curr->in_collection_set()) {
    // Update the collection set policy information for this region
    _g1h->g1_policy()->update_incremental_cset_info(_curr, rs_length);
  }

  _curr = _curr->get_next_young_region();
  if (_curr == NULL) {
    _last_sampled_rs_lengths = _sampled_rs_lengths;
    // gclog_or_tty->print_cr("last sampled RS lengths = %d", _last_sampled_rs_lengths);
  }
}

void
YoungList::reset_auxilary_lists() {
  guarantee( is_empty(), "young list should be empty" );
  assert(check_list_well_formed(), "young list should be well formed");

  // Add survivor regions to SurvRateGroup.
  _g1h->g1_policy()->note_start_adding_survivor_regions();
  _g1h->g1_policy()->finished_recalculating_age_indexes(true /* is_survivors */);

  int young_index_in_cset = 0;
  for (HeapRegion* curr = _survivor_head;
       curr != NULL;
       curr = curr->get_next_young_region()) {
    _g1h->g1_policy()->set_region_survivor(curr, young_index_in_cset);

    // The region is a non-empty survivor so let's add it to
    // the incremental collection set for the next evacuation
    // pause.
    _g1h->g1_policy()->add_region_to_incremental_cset_rhs(curr);
    young_index_in_cset += 1;
  }
  assert((uint) young_index_in_cset == _survivor_length, "post-condition");
  _g1h->g1_policy()->note_stop_adding_survivor_regions();

  _head   = _survivor_head;
  _length = _survivor_length;
  if (_survivor_head != NULL) {
    assert(_survivor_tail != NULL, "cause it shouldn't be");
    assert(_survivor_length > 0, "invariant");
    _survivor_tail->set_next_young_region(NULL);
  }

  // Don't clear the survivor list handles until the start of
  // the next evacuation pause - we need it in order to re-tag
  // the survivor regions from this evacuation pause as 'young'
  // at the start of the next.

  _g1h->g1_policy()->finished_recalculating_age_indexes(false /* is_survivors */);

  assert(check_list_well_formed(), "young list should be well formed");
}

void YoungList::print() {
  HeapRegion* lists[] = {_head,   _survivor_head};
  const char* names[] = {"YOUNG", "SURVIVOR"};

  for (unsigned int list = 0; list < ARRAY_SIZE(lists); ++list) {
    gclog_or_tty->print_cr("%s LIST CONTENTS", names[list]);
    HeapRegion *curr = lists[list];
    if (curr == NULL)
      gclog_or_tty->print_cr("  empty");
    while (curr != NULL) {
      gclog_or_tty->print_cr("  "HR_FORMAT", P: "PTR_FORMAT "N: "PTR_FORMAT", age: %4d",
                             HR_FORMAT_PARAMS(curr),
                             curr->prev_top_at_mark_start(),
                             curr->next_top_at_mark_start(),
                             curr->age_in_surv_rate_group_cond());
      curr = curr->get_next_young_region();
    }
  }

  gclog_or_tty->print_cr("");
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

void G1CollectedHeap::stop_conc_gc_threads() {
  _cg1r->stop();
  _cmThread->stop();
}

#ifdef ASSERT
// A region is added to the collection set as it is retired
// so an address p can point to a region which will be in the
// collection set but has not yet been retired.  This method
// therefore is only accurate during a GC pause after all
// regions have been retired.  It is used for debugging
// to check if an nmethod has references to objects that can
// be move during a partial collection.  Though it can be
// inaccurate, it is sufficient for G1 because the conservative
// implementation of is_scavengable() for G1 will indicate that
// all nmethods must be scanned during a partial collection.
bool G1CollectedHeap::is_in_partial_collection(const void* p) {
  HeapRegion* hr = heap_region_containing(p);
  return hr != NULL && hr->in_collection_set();
}
#endif

// Returns true if the reference points to an object that
// can move in an incremental collection.
bool G1CollectedHeap::is_scavengable(const void* p) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1CollectorPolicy* g1p = g1h->g1_policy();
  HeapRegion* hr = heap_region_containing(p);
  if (hr == NULL) {
     // null
     assert(p == NULL, err_msg("Not NULL " PTR_FORMAT ,p));
     return false;
  } else {
    return !hr->isHumongous();
  }
}

void G1CollectedHeap::check_ct_logs_at_safepoint() {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  CardTableModRefBS* ct_bs = g1_barrier_set();

  // Count the dirty cards at the start.
  CountNonCleanMemRegionClosure count1(this);
  ct_bs->mod_card_iterate(&count1);
  int orig_count = count1.n();

  // First clear the logged cards.
  ClearLoggedCardTableEntryClosure clear;
  dcqs.set_closure(&clear);
  dcqs.apply_closure_to_all_completed_buffers();
  dcqs.iterate_closure_all_threads(false);
  clear.print_histo();

  // Now ensure that there's no dirty cards.
  CountNonCleanMemRegionClosure count2(this);
  ct_bs->mod_card_iterate(&count2);
  if (count2.n() != 0) {
    gclog_or_tty->print_cr("Card table has %d entries; %d originally",
                           count2.n(), orig_count);
  }
  guarantee(count2.n() == 0, "Card table should be clean.");

  RedirtyLoggedCardTableEntryClosure redirty;
  JavaThread::dirty_card_queue_set().set_closure(&redirty);
  dcqs.apply_closure_to_all_completed_buffers();
  dcqs.iterate_closure_all_threads(false);
  gclog_or_tty->print_cr("Log entries = %d, dirty cards = %d.",
                         clear.calls(), orig_count);
  guarantee(redirty.calls() == clear.calls(),
            "Or else mechanism is broken.");

  CountNonCleanMemRegionClosure count3(this);
  ct_bs->mod_card_iterate(&count3);
  if (count3.n() != orig_count) {
    gclog_or_tty->print_cr("Should have restored them all: orig = %d, final = %d.",
                           orig_count, count3.n());
    guarantee(count3.n() >= orig_count, "Should have restored them all.");
  }

  JavaThread::dirty_card_queue_set().set_closure(_refine_cte_cl);
}

// Private class members.

G1CollectedHeap* G1CollectedHeap::_g1h;

// Private methods.

HeapRegion*
G1CollectedHeap::new_region_try_secondary_free_list() {
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

      assert(!_free_list.is_empty(), "if the secondary_free_list was not "
             "empty we should have moved at least one entry to the free_list");
      HeapRegion* res = _free_list.remove_head();
      if (G1ConcRegionFreeingVerbose) {
        gclog_or_tty->print_cr("G1ConcRegionFreeing [region alloc] : "
                               "allocated "HR_FORMAT" from secondary_free_list",
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

HeapRegion* G1CollectedHeap::new_region(size_t word_size, bool do_expand) {
  assert(!isHumongous(word_size) || word_size <= HeapRegion::GrainWords,
         "the only time we use this to allocate a humongous region is "
         "when we are allocating a single humongous region");

  HeapRegion* res;
  if (G1StressConcRegionFreeing) {
    if (!_secondary_free_list.is_empty()) {
      if (G1ConcRegionFreeingVerbose) {
        gclog_or_tty->print_cr("G1ConcRegionFreeing [region alloc] : "
                               "forced to look at the secondary_free_list");
      }
      res = new_region_try_secondary_free_list();
      if (res != NULL) {
        return res;
      }
    }
  }
  res = _free_list.remove_head_or_null();
  if (res == NULL) {
    if (G1ConcRegionFreeingVerbose) {
      gclog_or_tty->print_cr("G1ConcRegionFreeing [region alloc] : "
                             "res == NULL, trying the secondary_free_list");
    }
    res = new_region_try_secondary_free_list();
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
      // region size, the free list should in theory not be empty. So
      // it would probably be OK to use remove_head(). But the extra
      // check for NULL is unlikely to be a performance issue here (we
      // just expanded the heap!) so let's just be conservative and
      // use remove_head_or_null().
      res = _free_list.remove_head_or_null();
    } else {
      _expand_heap_after_alloc_failure = false;
    }
  }
  return res;
}

uint G1CollectedHeap::humongous_obj_allocate_find_first(uint num_regions,
                                                        size_t word_size) {
  assert(isHumongous(word_size), "word_size should be humongous");
  assert(num_regions * HeapRegion::GrainWords >= word_size, "pre-condition");

  uint first = G1_NULL_HRS_INDEX;
  if (num_regions == 1) {
    // Only one region to allocate, no need to go through the slower
    // path. The caller will attempt the expansion if this fails, so
    // let's not try to expand here too.
    HeapRegion* hr = new_region(word_size, false /* do_expand */);
    if (hr != NULL) {
      first = hr->hrs_index();
    } else {
      first = G1_NULL_HRS_INDEX;
    }
  } else {
    // We can't allocate humongous regions while cleanupComplete() is
    // running, since some of the regions we find to be empty might not
    // yet be added to the free list and it is not straightforward to
    // know which list they are on so that we can remove them. Note
    // that we only need to do this if we need to allocate more than
    // one region to satisfy the current humongous allocation
    // request. If we are only allocating one region we use the common
    // region allocation code (see above).
    wait_while_free_regions_coming();
    append_secondary_free_list_if_not_empty_with_lock();

    if (free_regions() >= num_regions) {
      first = _hrs.find_contiguous(num_regions);
      if (first != G1_NULL_HRS_INDEX) {
        for (uint i = first; i < first + num_regions; ++i) {
          HeapRegion* hr = region_at(i);
          assert(hr->is_empty(), "sanity");
          assert(is_on_master_free_list(hr), "sanity");
          hr->set_pending_removal(true);
        }
        _free_list.remove_all_pending(num_regions);
      }
    }
  }
  return first;
}

HeapWord*
G1CollectedHeap::humongous_obj_allocate_initialize_regions(uint first,
                                                           uint num_regions,
                                                           size_t word_size) {
  assert(first != G1_NULL_HRS_INDEX, "pre-condition");
  assert(isHumongous(word_size), "word_size should be humongous");
  assert(num_regions * HeapRegion::GrainWords >= word_size, "pre-condition");

  // Index of last region in the series + 1.
  uint last = first + num_regions;

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
  // This will be the new end of the first region in the series that
  // should also match the end of the last region in the series.
  HeapWord* new_end = new_obj + word_size_sum;
  // This will be the new top of the first region that will reflect
  // this allocation.
  HeapWord* new_top = new_obj + word_size;

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

  // We will set up the first region as "starts humongous". This
  // will also update the BOT covering all the regions to reflect
  // that there is a single object that starts at the bottom of the
  // first region.
  first_hr->set_startsHumongous(new_top, new_end);

  // Then, if there are any, we will set up the "continues
  // humongous" regions.
  HeapRegion* hr = NULL;
  for (uint i = first + 1; i < last; ++i) {
    hr = region_at(i);
    hr->set_continuesHumongous(first_hr);
  }
  // If we have "continues humongous" regions (hr != NULL), then the
  // end of the last one should match new_end.
  assert(hr == NULL || hr->end() == new_end, "sanity");

  // Up to this point no concurrent thread would have been able to
  // do any scanning on any region in this series. All the top
  // fields still point to bottom, so the intersection between
  // [bottom,top] and [card_start,card_end] will be empty. Before we
  // update the top fields, we'll do a storestore to make sure that
  // no thread sees the update to top before the zeroing of the
  // object header and the BOT initialization.
  OrderAccess::storestore();

  // Now that the BOT and the object header have been initialized,
  // we can update top of the "starts humongous" region.
  assert(first_hr->bottom() < new_top && new_top <= first_hr->end(),
         "new_top should be in this region");
  first_hr->set_top(new_top);
  if (_hr_printer.is_active()) {
    HeapWord* bottom = first_hr->bottom();
    HeapWord* end = first_hr->orig_end();
    if ((first + 1) == last) {
      // the series has a single humongous region
      _hr_printer.alloc(G1HRPrinter::SingleHumongous, first_hr, new_top);
    } else {
      // the series has more than one humongous regions
      _hr_printer.alloc(G1HRPrinter::StartsHumongous, first_hr, end);
    }
  }

  // Now, we will update the top fields of the "continues humongous"
  // regions. The reason we need to do this is that, otherwise,
  // these regions would look empty and this will confuse parts of
  // G1. For example, the code that looks for a consecutive number
  // of empty regions will consider them empty and try to
  // re-allocate them. We can extend is_empty() to also include
  // !continuesHumongous(), but it is easier to just update the top
  // fields here. The way we set top for all regions (i.e., top ==
  // end for all regions but the last one, top == new_top for the
  // last one) is actually used when we will free up the humongous
  // region in free_humongous_region().
  hr = NULL;
  for (uint i = first + 1; i < last; ++i) {
    hr = region_at(i);
    if ((i + 1) == last) {
      // last continues humongous region
      assert(hr->bottom() < new_top && new_top <= hr->end(),
             "new_top should fall on this region");
      hr->set_top(new_top);
      _hr_printer.alloc(G1HRPrinter::ContinuesHumongous, hr, new_top);
    } else {
      // not last one
      assert(new_top > hr->end(), "new_top should be above this region");
      hr->set_top(hr->end());
      _hr_printer.alloc(G1HRPrinter::ContinuesHumongous, hr, hr->end());
    }
  }
  // If we have continues humongous regions (hr != NULL), then the
  // end of the last one should match new_end and its top should
  // match new_top.
  assert(hr == NULL ||
         (hr->end() == new_end && hr->top() == new_top), "sanity");

  assert(first_hr->used() == word_size * HeapWordSize, "invariant");
  _summary_bytes_used += first_hr->used();
  _humongous_set.add(first_hr);

  return new_obj;
}

// If could fit into free regions w/o expansion, try.
// Otherwise, if can expand, do so.
// Otherwise, if using ex regions might help, try with ex given back.
HeapWord* G1CollectedHeap::humongous_obj_allocate(size_t word_size) {
  assert_heap_locked_or_at_safepoint(true /* should_be_vm_thread */);

  verify_region_sets_optional();

  size_t word_size_rounded = round_to(word_size, HeapRegion::GrainWords);
  uint num_regions = (uint) (word_size_rounded / HeapRegion::GrainWords);
  uint x_num = expansion_regions();
  uint fs = _hrs.free_suffix();
  uint first = humongous_obj_allocate_find_first(num_regions, word_size);
  if (first == G1_NULL_HRS_INDEX) {
    // The only thing we can do now is attempt expansion.
    if (fs + x_num >= num_regions) {
      // If the number of regions we're trying to allocate for this
      // object is at most the number of regions in the free suffix,
      // then the call to humongous_obj_allocate_find_first() above
      // should have succeeded and we wouldn't be here.
      //
      // We should only be trying to expand when the free suffix is
      // not sufficient for the object _and_ we have some expansion
      // room available.
      assert(num_regions > fs, "earlier allocation should have succeeded");

      ergo_verbose1(ErgoHeapSizing,
                    "attempt heap expansion",
                    ergo_format_reason("humongous allocation request failed")
                    ergo_format_byte("allocation request"),
                    word_size * HeapWordSize);
      if (expand((num_regions - fs) * HeapRegion::GrainBytes)) {
        // Even though the heap was expanded, it might not have
        // reached the desired size. So, we cannot assume that the
        // allocation will succeed.
        first = humongous_obj_allocate_find_first(num_regions, word_size);
      }
    }
  }

  HeapWord* result = NULL;
  if (first != G1_NULL_HRS_INDEX) {
    result =
      humongous_obj_allocate_initialize_regions(first, num_regions, word_size);
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
  assert(!isHumongous(word_size), "we do not allow humongous TLABs");

  unsigned int dummy_gc_count_before;
  int dummy_gclocker_retry_count = 0;
  return attempt_allocation(word_size, &dummy_gc_count_before, &dummy_gclocker_retry_count);
}

HeapWord*
G1CollectedHeap::mem_allocate(size_t word_size,
                              bool*  gc_overhead_limit_was_exceeded) {
  assert_heap_not_locked_and_not_at_safepoint();

  // Loop until the allocation is satisfied, or unsatisfied after GC.
  for (int try_count = 1, gclocker_retry_count = 0; /* we'll return */; try_count += 1) {
    unsigned int gc_count_before;

    HeapWord* result = NULL;
    if (!isHumongous(word_size)) {
      result = attempt_allocation(word_size, &gc_count_before, &gclocker_retry_count);
    } else {
      result = attempt_allocation_humongous(word_size, &gc_count_before, &gclocker_retry_count);
    }
    if (result != NULL) {
      return result;
    }

    // Create the garbage collection operation...
    VM_G1CollectForAllocation op(gc_count_before, word_size);
    // ...and get the VM thread to execute it.
    VMThread::execute(&op);

    if (op.prologue_succeeded() && op.pause_succeeded()) {
      // If the operation was successful we'll return the result even
      // if it is NULL. If the allocation attempt failed immediately
      // after a Full GC, it's unlikely we'll be able to allocate now.
      HeapWord* result = op.result();
      if (result != NULL && !isHumongous(word_size)) {
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
                                           unsigned int *gc_count_before_ret,
                                           int* gclocker_retry_count_ret) {
  // Make sure you read the note in attempt_allocation_humongous().

  assert_heap_not_locked_and_not_at_safepoint();
  assert(!isHumongous(word_size), "attempt_allocation_slow() should not "
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
    unsigned int gc_count_before;

    {
      MutexLockerEx x(Heap_lock);

      result = _mutator_alloc_region.attempt_allocation_locked(word_size,
                                                      false /* bot_updates */);
      if (result != NULL) {
        return result;
      }

      // If we reach here, attempt_allocation_locked() above failed to
      // allocate a new region. So the mutator alloc region should be NULL.
      assert(_mutator_alloc_region.get() == NULL, "only way to get here");

      if (GC_locker::is_active_and_needs_gc()) {
        if (g1_policy()->can_expand_young_list()) {
          // No need for an ergo verbose message here,
          // can_expand_young_list() does this when it returns true.
          result = _mutator_alloc_region.attempt_allocation_force(word_size,
                                                      false /* bot_updates */);
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
    result = _mutator_alloc_region.attempt_allocation(word_size,
                                                      false /* bot_updates */);
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

HeapWord* G1CollectedHeap::attempt_allocation_humongous(size_t word_size,
                                          unsigned int * gc_count_before_ret,
                                          int* gclocker_retry_count_ret) {
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
  assert(isHumongous(word_size), "attempt_allocation_humongous() "
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
    unsigned int gc_count_before;

    {
      MutexLockerEx x(Heap_lock);

      // Given that humongous objects are not allocated in young
      // regions, we'll first try to do the allocation without doing a
      // collection hoping that there's enough space in the heap.
      result = humongous_obj_allocate(word_size);
      if (result != NULL) {
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
                                       bool expect_null_mutator_alloc_region) {
  assert_at_safepoint(true /* should_be_vm_thread */);
  assert(_mutator_alloc_region.get() == NULL ||
                                             !expect_null_mutator_alloc_region,
         "the current alloc region was unexpectedly found to be non-NULL");

  if (!isHumongous(word_size)) {
    return _mutator_alloc_region.attempt_allocation_locked(word_size,
                                                      false /* bot_updates */);
  } else {
    HeapWord* result = humongous_obj_allocate(word_size);
    if (result != NULL && g1_policy()->need_to_start_conc_mark("STW humongous allocation")) {
      g1_policy()->set_initiate_conc_mark_if_possible();
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

    if (r->continuesHumongous()) {
      // We'll assert that the strong code root list and RSet is empty
      assert(hrrs->strong_code_roots_list_length() == 0, "sanity");
      assert(hrrs->occupied() == 0, "RSet should be empty");
      return false;
    }

    _g1h->reset_gc_time_stamps(r);
    hrrs->clear();
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
  int                _worker_i;
public:
  RebuildRSOutOfRegionClosure(G1CollectedHeap* g1, int worker_i = 0) :
    _cl(g1->g1_rem_set(), worker_i),
    _worker_i(worker_i),
    _g1h(g1)
  { }

  bool doHeapRegion(HeapRegion* r) {
    if (!r->continuesHumongous()) {
      _cl.set_from(r);
      r->oop_iterate(&_cl);
    }
    return false;
  }
};

class ParRebuildRSTask: public AbstractGangTask {
  G1CollectedHeap* _g1;
public:
  ParRebuildRSTask(G1CollectedHeap* g1)
    : AbstractGangTask("ParRebuildRSTask"),
      _g1(g1)
  { }

  void work(uint worker_id) {
    RebuildRSOutOfRegionClosure rebuild_rs(_g1, worker_id);
    _g1->heap_region_par_iterate_chunked(&rebuild_rs, worker_id,
                                          _g1->workers()->active_workers(),
                                         HeapRegion::RebuildRSClaimValue);
  }
};

class PostCompactionPrinterClosure: public HeapRegionClosure {
private:
  G1HRPrinter* _hr_printer;
public:
  bool doHeapRegion(HeapRegion* hr) {
    assert(!hr->is_young(), "not expecting to find young regions");
    // We only generate output for non-empty regions.
    if (!hr->is_empty()) {
      if (!hr->isHumongous()) {
        _hr_printer->post_compaction(hr, G1HRPrinter::Old);
      } else if (hr->startsHumongous()) {
        if (hr->region_num() == 1) {
          // single humongous region
          _hr_printer->post_compaction(hr, G1HRPrinter::SingleHumongous);
        } else {
          _hr_printer->post_compaction(hr, G1HRPrinter::StartsHumongous);
        }
      } else {
        assert(hr->continuesHumongous(), "only way to get here");
        _hr_printer->post_compaction(hr, G1HRPrinter::ContinuesHumongous);
      }
    }
    return false;
  }

  PostCompactionPrinterClosure(G1HRPrinter* hr_printer)
    : _hr_printer(hr_printer) { }
};

void G1CollectedHeap::print_hrs_post_compaction() {
  PostCompactionPrinterClosure cl(hr_printer());
  heap_region_iterate(&cl);
}

bool G1CollectedHeap::do_collection(bool explicit_gc,
                                    bool clear_all_soft_refs,
                                    size_t word_size) {
  assert_at_safepoint(true /* should_be_vm_thread */);

  if (GC_locker::check_active_before_gc()) {
    return false;
  }

  STWGCTimer* gc_timer = G1MarkSweep::gc_timer();
  gc_timer->register_gc_start();

  SerialOldTracer* gc_tracer = G1MarkSweep::gc_tracer();
  gc_tracer->report_gc_start(gc_cause(), gc_timer->gc_start());

  SvcGCMarker sgcm(SvcGCMarker::FULL);
  ResourceMark rm;

  print_heap_before_gc();
  trace_heap_before_gc(gc_tracer);

  size_t metadata_prev_used = MetaspaceAux::allocated_used_bytes();

  HRSPhaseSetter x(HRSPhaseFullGC);
  verify_region_sets_optional();

  const bool do_clear_all_soft_refs = clear_all_soft_refs ||
                           collector_policy()->should_clear_all_soft_refs();

  ClearedAllSoftRefs casr(do_clear_all_soft_refs, collector_policy());

  {
    IsGCActiveMark x;

    // Timing
    assert(gc_cause() != GCCause::_java_lang_system_gc || explicit_gc, "invariant");
    gclog_or_tty->date_stamp(G1Log::fine() && PrintGCDateStamps);
    TraceCPUTime tcpu(G1Log::finer(), true, gclog_or_tty);

    {
      GCTraceTime t(GCCauseString("Full GC", gc_cause()), G1Log::fine(), true, NULL);
      TraceCollectorStats tcs(g1mm()->full_collection_counters());
      TraceMemoryManagerStats tms(true /* fullGC */, gc_cause());

      double start = os::elapsedTime();
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

      pre_full_gc_dump(gc_timer);

      COMPILER2_PRESENT(DerivedPointerTable::clear());

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
      release_mutator_alloc_region();
      abandon_gc_alloc_regions();
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
      g1_policy()->set_gcs_are_young(true);

      // See the comments in g1CollectedHeap.hpp and
      // G1CollectedHeap::ref_processing_init() about
      // how reference processing currently works in G1.

      // Temporarily make discovery by the STW ref processor single threaded (non-MT).
      ReferenceProcessorMTDiscoveryMutator stw_rp_disc_ser(ref_processor_stw(), false);

      // Temporarily clear the STW ref processor's _is_alive_non_header field.
      ReferenceProcessorIsAliveMutator stw_rp_is_alive_null(ref_processor_stw(), NULL);

      ref_processor_stw()->enable_discovery(true /*verify_disabled*/, true /*verify_no_refs*/);
      ref_processor_stw()->setup_policy(do_clear_all_soft_refs);

      // Do collection work
      {
        HandleMark hm;  // Discard invalid handles created during gc
        G1MarkSweep::invoke_at_safepoint(ref_processor_stw(), do_clear_all_soft_refs);
      }

      assert(free_regions() == 0, "we should not have added any free regions");
      rebuild_region_sets(false /* free_list_only */);

      // Enqueue any discovered reference objects that have
      // not been removed from the discovered lists.
      ref_processor_stw()->enqueue_discovered_references();

      COMPILER2_PRESENT(DerivedPointerTable::update_pointers());

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

      // Resize the heap if necessary.
      resize_if_necessary_after_full_collection(explicit_gc ? 0 : word_size);

      if (_hr_printer.is_active()) {
        // We should do this after we potentially resize the heap so
        // that all the COMMIT / UNCOMMIT events are generated before
        // the end GC event.

        print_hrs_post_compaction();
        _hr_printer.end_gc(true /* full */, (size_t) total_collections());
      }

      G1HotCardCache* hot_card_cache = _cg1r->hot_card_cache();
      if (hot_card_cache->use_cache()) {
        hot_card_cache->reset_card_counts();
        hot_card_cache->reset_hot_cache();
      }

      // Rebuild remembered sets of all regions.
      if (G1CollectedHeap::use_parallel_gc_threads()) {
        uint n_workers =
          AdaptiveSizePolicy::calc_active_workers(workers()->total_workers(),
                                                  workers()->active_workers(),
                                                  Threads::number_of_non_daemon_threads());
        assert(UseDynamicNumberOfGCThreads ||
               n_workers == workers()->total_workers(),
               "If not dynamic should be using all the  workers");
        workers()->set_active_workers(n_workers);
        // Set parallel threads in the heap (_n_par_threads) only
        // before a parallel phase and always reset it to 0 after
        // the phase so that the number of parallel threads does
        // no get carried forward to a serial phase where there
        // may be code that is "possibly_parallel".
        set_par_threads(n_workers);

        ParRebuildRSTask rebuild_rs_task(this);
        assert(check_heap_region_claim_values(
               HeapRegion::InitialClaimValue), "sanity check");
        assert(UseDynamicNumberOfGCThreads ||
               workers()->active_workers() == workers()->total_workers(),
               "Unless dynamic should use total workers");
        // Use the most recent number of  active workers
        assert(workers()->active_workers() > 0,
               "Active workers not properly set");
        set_par_threads(workers()->active_workers());
        workers()->run_task(&rebuild_rs_task);
        set_par_threads(0);
        assert(check_heap_region_claim_values(
               HeapRegion::RebuildRSClaimValue), "sanity check");
        reset_heap_region_claim_values();
      } else {
        RebuildRSOutOfRegionClosure rebuild_rs(this);
        heap_region_iterate(&rebuild_rs);
      }

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
      assert(!G1DeferredRSUpdate
             || (G1DeferredRSUpdate &&
                (dirty_card_queue_set().completed_buffers_num() == 0)), "Should not be any");

      _young_list->reset_sampled_info();
      // At this point there should be no regions in the
      // entire heap tagged as young.
      assert(check_young_list_empty(true /* check_heap */),
             "young list should be empty at this point");

      // Update the number of full collections that have been completed.
      increment_old_marking_cycles_completed(false /* concurrent */);

      _hrs.verify_optional();
      verify_region_sets_optional();

      verify_after_gc();

      // Start a new incremental collection set for the next pause
      assert(g1_policy()->collection_set() == NULL, "must be");
      g1_policy()->start_incremental_cset_building();

      // Clear the _cset_fast_test bitmap in anticipation of adding
      // regions to the incremental collection set for the next
      // evacuation pause.
      clear_cset_fast_test();

      init_mutator_alloc_region();

      double end = os::elapsedTime();
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
  // do_collection() will return whether it succeeded in performing
  // the GC. Currently, there is no facility on the
  // do_full_collection() API to notify the caller than the collection
  // did not succeed (e.g., because it was locked out by the GC
  // locker). So, right now, we'll ignore the return value.
  bool dummy = do_collection(true,                /* explicit_gc */
                             clear_all_soft_refs,
                             0                    /* word_size */);
}

// This code is mostly copied from TenuredGeneration.
void
G1CollectedHeap::
resize_if_necessary_after_full_collection(size_t word_size) {
  // Include the current allocation, if any, and bytes that will be
  // pre-allocated to support collections, as "used".
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
         err_msg("minimum_desired_capacity = "SIZE_FORMAT", "
                 "maximum_desired_capacity = "SIZE_FORMAT,
                 minimum_desired_capacity, maximum_desired_capacity));

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


HeapWord*
G1CollectedHeap::satisfy_failed_allocation(size_t word_size,
                                           bool* succeeded) {
  assert_at_safepoint(true /* should_be_vm_thread */);

  *succeeded = true;
  // Let's attempt the allocation first.
  HeapWord* result =
    attempt_allocation_at_safepoint(word_size,
                                 false /* expect_null_mutator_alloc_region */);
  if (result != NULL) {
    assert(*succeeded, "sanity");
    return result;
  }

  // In a G1 heap, we're supposed to keep allocation from failing by
  // incremental pauses.  Therefore, at least for now, we'll favor
  // expansion over collection.  (This might change in the future if we can
  // do something smarter than full collection to satisfy a failed alloc.)
  result = expand_and_allocate(word_size);
  if (result != NULL) {
    assert(*succeeded, "sanity");
    return result;
  }

  // Expansion didn't work, we'll try to do a Full GC.
  bool gc_succeeded = do_collection(false, /* explicit_gc */
                                    false, /* clear_all_soft_refs */
                                    word_size);
  if (!gc_succeeded) {
    *succeeded = false;
    return NULL;
  }

  // Retry the allocation
  result = attempt_allocation_at_safepoint(word_size,
                                  true /* expect_null_mutator_alloc_region */);
  if (result != NULL) {
    assert(*succeeded, "sanity");
    return result;
  }

  // Then, try a Full GC that will collect all soft references.
  gc_succeeded = do_collection(false, /* explicit_gc */
                               true,  /* clear_all_soft_refs */
                               word_size);
  if (!gc_succeeded) {
    *succeeded = false;
    return NULL;
  }

  // Retry the allocation once more
  result = attempt_allocation_at_safepoint(word_size,
                                  true /* expect_null_mutator_alloc_region */);
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

HeapWord* G1CollectedHeap::expand_and_allocate(size_t word_size) {
  assert_at_safepoint(true /* should_be_vm_thread */);

  verify_region_sets_optional();

  size_t expand_bytes = MAX2(word_size * HeapWordSize, MinHeapDeltaBytes);
  ergo_verbose1(ErgoHeapSizing,
                "attempt heap expansion",
                ergo_format_reason("allocation request failed")
                ergo_format_byte("allocation request"),
                word_size * HeapWordSize);
  if (expand(expand_bytes)) {
    _hrs.verify_optional();
    verify_region_sets_optional();
    return attempt_allocation_at_safepoint(word_size,
                                 false /* expect_null_mutator_alloc_region */);
  }
  return NULL;
}

void G1CollectedHeap::update_committed_space(HeapWord* old_end,
                                             HeapWord* new_end) {
  assert(old_end != new_end, "don't call this otherwise");
  assert((HeapWord*) _g1_storage.high() == new_end, "invariant");

  // Update the committed mem region.
  _g1_committed.set_end(new_end);
  // Tell the card table about the update.
  Universe::heap()->barrier_set()->resize_covered_region(_g1_committed);
  // Tell the BOT about the update.
  _bot_shared->resize(_g1_committed.word_size());
  // Tell the hot card cache about the update
  _cg1r->hot_card_cache()->resize_card_counts(capacity());
}

bool G1CollectedHeap::expand(size_t expand_bytes) {
  size_t aligned_expand_bytes = ReservedSpace::page_align_size_up(expand_bytes);
  aligned_expand_bytes = align_size_up(aligned_expand_bytes,
                                       HeapRegion::GrainBytes);
  ergo_verbose2(ErgoHeapSizing,
                "expand the heap",
                ergo_format_byte("requested expansion amount")
                ergo_format_byte("attempted expansion amount"),
                expand_bytes, aligned_expand_bytes);

  if (_g1_storage.uncommitted_size() == 0) {
    ergo_verbose0(ErgoHeapSizing,
                      "did not expand the heap",
                      ergo_format_reason("heap already fully expanded"));
    return false;
  }

  // First commit the memory.
  HeapWord* old_end = (HeapWord*) _g1_storage.high();
  bool successful = _g1_storage.expand_by(aligned_expand_bytes);
  if (successful) {
    // Then propagate this update to the necessary data structures.
    HeapWord* new_end = (HeapWord*) _g1_storage.high();
    update_committed_space(old_end, new_end);

    FreeRegionList expansion_list("Local Expansion List");
    MemRegion mr = _hrs.expand_by(old_end, new_end, &expansion_list);
    assert(mr.start() == old_end, "post-condition");
    // mr might be a smaller region than what was requested if
    // expand_by() was unable to allocate the HeapRegion instances
    assert(mr.end() <= new_end, "post-condition");

    size_t actual_expand_bytes = mr.byte_size();
    assert(actual_expand_bytes <= aligned_expand_bytes, "post-condition");
    assert(actual_expand_bytes == expansion_list.total_capacity_bytes(),
           "post-condition");
    if (actual_expand_bytes < aligned_expand_bytes) {
      // We could not expand _hrs to the desired size. In this case we
      // need to shrink the committed space accordingly.
      assert(mr.end() < new_end, "invariant");

      size_t diff_bytes = aligned_expand_bytes - actual_expand_bytes;
      // First uncommit the memory.
      _g1_storage.shrink_by(diff_bytes);
      // Then propagate this update to the necessary data structures.
      update_committed_space(new_end, mr.end());
    }
    _free_list.add_as_tail(&expansion_list);

    if (_hr_printer.is_active()) {
      HeapWord* curr = mr.start();
      while (curr < mr.end()) {
        HeapWord* curr_end = curr + HeapRegion::GrainWords;
        _hr_printer.commit(curr, curr_end);
        curr = curr_end;
      }
      assert(curr == mr.end(), "post-condition");
    }
    g1_policy()->record_new_heap_size(n_regions());
  } else {
    ergo_verbose0(ErgoHeapSizing,
                  "did not expand the heap",
                  ergo_format_reason("heap expansion operation failed"));
    // The expansion of the virtual storage space was unsuccessful.
    // Let's see if it was because we ran out of swap.
    if (G1ExitOnExpansionFailure &&
        _g1_storage.uncommitted_size() >= aligned_expand_bytes) {
      // We had head room...
      vm_exit_out_of_memory(aligned_expand_bytes, OOM_MMAP_ERROR, "G1 heap expansion");
    }
  }
  return successful;
}

void G1CollectedHeap::shrink_helper(size_t shrink_bytes) {
  size_t aligned_shrink_bytes =
    ReservedSpace::page_align_size_down(shrink_bytes);
  aligned_shrink_bytes = align_size_down(aligned_shrink_bytes,
                                         HeapRegion::GrainBytes);
  uint num_regions_to_remove = (uint)(shrink_bytes / HeapRegion::GrainBytes);

  uint num_regions_removed = _hrs.shrink_by(num_regions_to_remove);
  HeapWord* old_end = (HeapWord*) _g1_storage.high();
  size_t shrunk_bytes = num_regions_removed * HeapRegion::GrainBytes;

  ergo_verbose3(ErgoHeapSizing,
                "shrink the heap",
                ergo_format_byte("requested shrinking amount")
                ergo_format_byte("aligned shrinking amount")
                ergo_format_byte("attempted shrinking amount"),
                shrink_bytes, aligned_shrink_bytes, shrunk_bytes);
  if (num_regions_removed > 0) {
    _g1_storage.shrink_by(shrunk_bytes);
    HeapWord* new_end = (HeapWord*) _g1_storage.high();

    if (_hr_printer.is_active()) {
      HeapWord* curr = old_end;
      while (curr > new_end) {
        HeapWord* curr_end = curr;
        curr -= HeapRegion::GrainWords;
        _hr_printer.uncommit(curr, curr_end);
      }
    }

    _expansion_regions += num_regions_removed;
    update_committed_space(old_end, new_end);
    HeapRegionRemSet::shrink_heap(n_regions());
    g1_policy()->record_new_heap_size(n_regions());
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
  abandon_gc_alloc_regions();

  // Instead of tearing down / rebuilding the free lists here, we
  // could instead use the remove_all_pending() method on free_list to
  // remove only the ones that we need to remove.
  tear_down_region_sets(true /* free_list_only */);
  shrink_helper(shrink_bytes);
  rebuild_region_sets(true /* free_list_only */);

  _hrs.verify_optional();
  verify_region_sets_optional();
}

// Public methods.

#ifdef _MSC_VER // the use of 'this' below gets a warning, make it go away
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif // _MSC_VER


G1CollectedHeap::G1CollectedHeap(G1CollectorPolicy* policy_) :
  SharedHeap(policy_),
  _g1_policy(policy_),
  _dirty_card_queue_set(false),
  _into_cset_dirty_card_queue_set(false),
  _is_alive_closure_cm(this),
  _is_alive_closure_stw(this),
  _ref_processor_cm(NULL),
  _ref_processor_stw(NULL),
  _process_strong_tasks(new SubTasksDone(G1H_PS_NumElements)),
  _bot_shared(NULL),
  _evac_failure_scan_stack(NULL),
  _mark_in_progress(false),
  _cg1r(NULL), _summary_bytes_used(0),
  _g1mm(NULL),
  _refine_cte_cl(NULL),
  _full_collection(false),
  _free_list("Master Free List"),
  _secondary_free_list("Secondary Free List"),
  _old_set("Old Set"),
  _humongous_set("Master Humongous Set"),
  _free_regions_coming(false),
  _young_list(new YoungList(this)),
  _gc_time_stamp(0),
  _retained_old_gc_alloc_region(NULL),
  _survivor_plab_stats(YoungPLABSize, PLABWeight),
  _old_plab_stats(OldPLABSize, PLABWeight),
  _expand_heap_after_alloc_failure(true),
  _surviving_young_words(NULL),
  _old_marking_cycles_started(0),
  _old_marking_cycles_completed(0),
  _concurrent_cycle_started(false),
  _in_cset_fast_test(NULL),
  _in_cset_fast_test_base(NULL),
  _dirty_cards_region_list(NULL),
  _worker_cset_start_region(NULL),
  _worker_cset_start_region_time_stamp(NULL),
  _gc_timer_stw(new (ResourceObj::C_HEAP, mtGC) STWGCTimer()),
  _gc_timer_cm(new (ResourceObj::C_HEAP, mtGC) ConcurrentGCTimer()),
  _gc_tracer_stw(new (ResourceObj::C_HEAP, mtGC) G1NewTracer()),
  _gc_tracer_cm(new (ResourceObj::C_HEAP, mtGC) G1OldTracer()) {

  _g1h = this;
  if (_process_strong_tasks == NULL || !_process_strong_tasks->valid()) {
    vm_exit_during_initialization("Failed necessary allocation.");
  }

  _humongous_object_threshold_in_words = HeapRegion::GrainWords / 2;

  int n_queues = MAX2((int)ParallelGCThreads, 1);
  _task_queues = new RefToScanQueueSet(n_queues);

  int n_rem_sets = HeapRegionRemSet::num_par_rem_sets();
  assert(n_rem_sets > 0, "Invariant.");

  _worker_cset_start_region = NEW_C_HEAP_ARRAY(HeapRegion*, n_queues, mtGC);
  _worker_cset_start_region_time_stamp = NEW_C_HEAP_ARRAY(unsigned int, n_queues, mtGC);
  _evacuation_failed_info_array = NEW_C_HEAP_ARRAY(EvacuationFailedInfo, n_queues, mtGC);

  for (int i = 0; i < n_queues; i++) {
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

  _cg1r = new ConcurrentG1Refine(this);

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

  // It is important to do this in a way such that concurrent readers can't
  // temporarily think something is in the heap.  (I've actually seen this
  // happen in asserts: DLD.)
  _reserved.set_word_size(0);
  _reserved.set_start((HeapWord*)heap_rs.base());
  _reserved.set_end((HeapWord*)(heap_rs.base() + heap_rs.size()));

  _expansion_regions = (uint) (max_byte_size / HeapRegion::GrainBytes);

  // Create the gen rem set (and barrier set) for the entire reserved region.
  _rem_set = collector_policy()->create_rem_set(_reserved, 2);
  set_barrier_set(rem_set()->bs());
  if (!barrier_set()->is_a(BarrierSet::G1SATBCTLogging)) {
    vm_exit_during_initialization("G1 requires a G1SATBLoggingCardTableModRefBS");
    return JNI_ENOMEM;
  }

  // Also create a G1 rem set.
  _g1_rem_set = new G1RemSet(this, g1_barrier_set());

  // Carve out the G1 part of the heap.

  ReservedSpace g1_rs   = heap_rs.first_part(max_byte_size);
  _g1_reserved = MemRegion((HeapWord*)g1_rs.base(),
                           g1_rs.size()/HeapWordSize);

  _g1_storage.initialize(g1_rs, 0);
  _g1_committed = MemRegion((HeapWord*)_g1_storage.low(), (size_t) 0);
  _hrs.initialize((HeapWord*) _g1_reserved.start(),
                  (HeapWord*) _g1_reserved.end());
  assert(_hrs.max_length() == _expansion_regions,
         err_msg("max length: %u expansion regions: %u",
                 _hrs.max_length(), _expansion_regions));

  // Do later initialization work for concurrent refinement.
  _cg1r->init();

  // 6843694 - ensure that the maximum region index can fit
  // in the remembered set structures.
  const uint max_region_idx = (1U << (sizeof(RegionIdx_t)*BitsPerByte-1)) - 1;
  guarantee((max_regions() - 1) <= max_region_idx, "too many regions");

  size_t max_cards_per_region = ((size_t)1 << (sizeof(CardIdx_t)*BitsPerByte-1)) - 1;
  guarantee(HeapRegion::CardsPerRegion > 0, "make sure it's initialized");
  guarantee(HeapRegion::CardsPerRegion < max_cards_per_region,
            "too many cards per region");

  HeapRegionSet::set_unrealistically_long_length(max_regions() + 1);

  _bot_shared = new G1BlockOffsetSharedArray(_reserved,
                                             heap_word_size(init_byte_size));

  _g1h = this;

  _in_cset_fast_test_length = max_regions();
  _in_cset_fast_test_base =
                   NEW_C_HEAP_ARRAY(bool, (size_t) _in_cset_fast_test_length, mtGC);

  // We're biasing _in_cset_fast_test to avoid subtracting the
  // beginning of the heap every time we want to index; basically
  // it's the same with what we do with the card table.
  _in_cset_fast_test = _in_cset_fast_test_base -
               ((uintx) _g1_reserved.start() >> HeapRegion::LogOfHRGrainBytes);

  // Clear the _cset_fast_test bitmap in anticipation of adding
  // regions to the incremental collection set for the first
  // evacuation pause.
  clear_cset_fast_test();

  // Create the ConcurrentMark data structure and thread.
  // (Must do this late, so that "max_regions" is defined.)
  _cm = new ConcurrentMark(this, heap_rs);
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

  _refine_cte_cl =
    new RefineCardTableEntryClosure(ConcurrentG1RefineThread::sts(),
                                    g1_rem_set(),
                                    concurrent_g1_refine());
  JavaThread::dirty_card_queue_set().set_closure(_refine_cte_cl);

  JavaThread::satb_mark_queue_set().initialize(SATB_Q_CBL_mon,
                                               SATB_Q_FL_lock,
                                               G1SATBProcessCompletedThreshold,
                                               Shared_SATB_Q_lock);

  JavaThread::dirty_card_queue_set().initialize(DirtyCardQ_CBL_mon,
                                                DirtyCardQ_FL_lock,
                                                concurrent_g1_refine()->yellow_zone(),
                                                concurrent_g1_refine()->red_zone(),
                                                Shared_DirtyCardQ_lock);

  if (G1DeferredRSUpdate) {
    dirty_card_queue_set().initialize(DirtyCardQ_CBL_mon,
                                      DirtyCardQ_FL_lock,
                                      -1, // never trigger processing
                                      -1, // no limit on length
                                      Shared_DirtyCardQ_lock,
                                      &JavaThread::dirty_card_queue_set());
  }

  // Initialize the card queue set used to hold cards containing
  // references into the collection set.
  _into_cset_dirty_card_queue_set.initialize(DirtyCardQ_CBL_mon,
                                             DirtyCardQ_FL_lock,
                                             -1, // never trigger processing
                                             -1, // no limit on length
                                             Shared_DirtyCardQ_lock,
                                             &JavaThread::dirty_card_queue_set());

  // In case we're keeping closure specialization stats, initialize those
  // counts and that mechanism.
  SpecializationStats::clear();

  // Here we allocate the dummy full region that is required by the
  // G1AllocRegion class. If we don't pass an address in the reserved
  // space here, lots of asserts fire.

  HeapRegion* dummy_region = new_heap_region(0 /* index of bottom region */,
                                             _g1_reserved.start());
  // We'll re-use the same region whether the alloc region will
  // require BOT updates or not and, if it doesn't, then a non-young
  // region will complain that it cannot support allocations without
  // BOT updates. So we'll tag the dummy region as young to avoid that.
  dummy_region->set_young();
  // Make sure it's full.
  dummy_region->set_top(dummy_region->end());
  G1AllocRegion::setup(this, dummy_region);

  init_mutator_alloc_region();

  // Do create of the monitoring and management support so that
  // values in the heap have been properly initialized.
  _g1mm = new G1MonitoringSupport(this);

  return JNI_OK;
}

size_t G1CollectedHeap::conservative_max_heap_alignment() {
  return HeapRegion::max_region_size();
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

  SharedHeap::ref_processing_init();
  MemRegion mr = reserved_region();

  // Concurrent Mark ref processor
  _ref_processor_cm =
    new ReferenceProcessor(mr,    // span
                           ParallelRefProcEnabled && (ParallelGCThreads > 1),
                                // mt processing
                           (int) ParallelGCThreads,
                                // degree of mt processing
                           (ParallelGCThreads > 1) || (ConcGCThreads > 1),
                                // mt discovery
                           (int) MAX2(ParallelGCThreads, ConcGCThreads),
                                // degree of mt discovery
                           false,
                                // Reference discovery is not atomic
                           &_is_alive_closure_cm,
                                // is alive closure
                                // (for efficiency/performance)
                           true);
                                // Setting next fields of discovered
                                // lists requires a barrier.

  // STW ref processor
  _ref_processor_stw =
    new ReferenceProcessor(mr,    // span
                           ParallelRefProcEnabled && (ParallelGCThreads > 1),
                                // mt processing
                           MAX2((int)ParallelGCThreads, 1),
                                // degree of mt processing
                           (ParallelGCThreads > 1),
                                // mt discovery
                           MAX2((int)ParallelGCThreads, 1),
                                // degree of mt discovery
                           true,
                                // Reference discovery is atomic
                           &_is_alive_closure_stw,
                                // is alive closure
                                // (for efficiency/performance)
                           false);
                                // Setting next fields of discovered
                                // lists does not require a barrier.
}

size_t G1CollectedHeap::capacity() const {
  return _g1_committed.byte_size();
}

void G1CollectedHeap::reset_gc_time_stamps(HeapRegion* hr) {
  assert(!hr->continuesHumongous(), "pre-condition");
  hr->reset_gc_time_stamp();
  if (hr->startsHumongous()) {
    uint first_index = hr->hrs_index() + 1;
    uint last_index = hr->last_hc_index();
    for (uint i = first_index; i < last_index; i += 1) {
      HeapRegion* chr = region_at(i);
      assert(chr->continuesHumongous(), "sanity");
      chr->reset_gc_time_stamp();
    }
  }
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
      gclog_or_tty->print_cr("Region "HR_FORMAT" has GC time stamp = %d, "
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

void G1CollectedHeap::iterate_dirty_card_closure(CardTableEntryClosure* cl,
                                                 DirtyCardQueue* into_cset_dcq,
                                                 bool concurrent,
                                                 int worker_i) {
  // Clean cards in the hot card cache
  G1HotCardCache* hot_card_cache = _cg1r->hot_card_cache();
  hot_card_cache->drain(worker_i, g1_rem_set(), into_cset_dcq);

  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  int n_completed_buffers = 0;
  while (dcqs.apply_closure_to_completed_buffer(cl, worker_i, 0, true)) {
    n_completed_buffers++;
  }
  g1_policy()->phase_times()->record_update_rs_processed_buffers(worker_i, n_completed_buffers);
  dcqs.clear_n_completed_buffers();
  assert(!dcqs.completed_buffers_exist_dirty(), "Completed buffers exist!");
}


// Computes the sum of the storage used by the various regions.

size_t G1CollectedHeap::used() const {
  assert(Heap_lock->owner() != NULL,
         "Should be owned on this thread's behalf.");
  size_t result = _summary_bytes_used;
  // Read only once in case it is set to NULL concurrently
  HeapRegion* hr = _mutator_alloc_region.get();
  if (hr != NULL)
    result += hr->used();
  return result;
}

size_t G1CollectedHeap::used_unlocked() const {
  size_t result = _summary_bytes_used;
  return result;
}

class SumUsedClosure: public HeapRegionClosure {
  size_t _used;
public:
  SumUsedClosure() : _used(0) {}
  bool doHeapRegion(HeapRegion* r) {
    if (!r->continuesHumongous()) {
      _used += r->used();
    }
    return false;
  }
  size_t result() { return _used; }
};

size_t G1CollectedHeap::recalculate_used() const {
  SumUsedClosure blk;
  heap_region_iterate(&blk);
  return blk.result();
}

bool G1CollectedHeap::should_do_concurrent_full_gc(GCCause::Cause cause) {
  switch (cause) {
    case GCCause::_gc_locker:               return GCLockerInvokesConcurrent;
    case GCCause::_java_lang_system_gc:     return ExplicitGCInvokesConcurrent;
    case GCCause::_g1_humongous_allocation: return true;
    default:                                return false;
  }
}

#ifndef PRODUCT
void G1CollectedHeap::allocate_dummy_regions() {
  // Let's fill up most of the region
  size_t word_size = HeapRegion::GrainWords - 1024;
  // And as a result the region we'll allocate will be humongous.
  guarantee(isHumongous(word_size), "sanity");

  for (uintx i = 0; i < G1DummyRegionsPerGC; ++i) {
    // Let's use the existing mechanism for the allocation
    HeapWord* dummy_obj = humongous_obj_allocate(word_size);
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
    err_msg("Wrong marking cycle count (started: %d, completed: %d)",
    _old_marking_cycles_started, _old_marking_cycles_completed));

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
         err_msg("for inner caller (Full GC): _old_marking_cycles_started = %u "
                 "is inconsistent with _old_marking_cycles_completed = %u",
                 _old_marking_cycles_started, _old_marking_cycles_completed));

  // This is the case for the outer caller, i.e. the concurrent cycle.
  assert(!concurrent ||
         (_old_marking_cycles_started == _old_marking_cycles_completed + 1),
         err_msg("for outer caller (concurrent cycle): "
                 "_old_marking_cycles_started = %u "
                 "is inconsistent with _old_marking_cycles_completed = %u",
                 _old_marking_cycles_started, _old_marking_cycles_completed));

  _old_marking_cycles_completed += 1;

  // We need to clear the "in_progress" flag in the CM thread before
  // we wake up any waiters (especially when ExplicitInvokesConcurrent
  // is set) so that if a waiter requests another System.gc() it doesn't
  // incorrectly see that a marking cycle is still in progress.
  if (concurrent) {
    _cmThread->clear_in_progress();
  }

  // This notify_all() will ensure that a thread that called
  // System.gc() with (with ExplicitGCInvokesConcurrent set or not)
  // and it's waiting for a full GC to finish will be woken up. It is
  // waiting in VM_G1IncCollectionPause::doit_epilogue().
  FullGCCount_lock->notify_all();
}

void G1CollectedHeap::register_concurrent_cycle_start(const Ticks& start_time) {
  _concurrent_cycle_started = true;
  _gc_timer_cm->register_gc_start(start_time);

  _gc_tracer_cm->report_gc_start(gc_cause(), _gc_timer_cm->gc_start());
  trace_heap_before_gc(_gc_tracer_cm);
}

void G1CollectedHeap::register_concurrent_cycle_end() {
  if (_concurrent_cycle_started) {
    if (_cm->has_aborted()) {
      _gc_tracer_cm->report_concurrent_mode_failure();
    }

    _gc_timer_cm->register_gc_end();
    _gc_tracer_cm->report_gc_end(_gc_timer_cm->gc_end(), _gc_timer_cm->time_partitions());

    _concurrent_cycle_started = false;
  }
}

void G1CollectedHeap::trace_heap_after_concurrent_cycle() {
  if (_concurrent_cycle_started) {
    trace_heap_after_gc(_gc_tracer_cm);
  }
}

G1YCType G1CollectedHeap::yc_type() {
  bool is_young = g1_policy()->gcs_are_young();
  bool is_initial_mark = g1_policy()->during_initial_mark_pause();
  bool is_during_mark = mark_in_progress();

  if (is_initial_mark) {
    return InitialMark;
  } else if (is_during_mark) {
    return DuringMark;
  } else if (is_young) {
    return Normal;
  } else {
    return Mixed;
  }
}

void G1CollectedHeap::collect(GCCause::Cause cause) {
  assert_heap_not_locked();

  unsigned int gc_count_before;
  unsigned int old_marking_count_before;
  bool retry_gc;

  do {
    retry_gc = false;

    {
      MutexLocker ml(Heap_lock);

      // Read the GC count while holding the Heap_lock
      gc_count_before = total_collections();
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
      if (cause == GCCause::_gc_locker
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
        VM_G1CollectFull op(gc_count_before, old_marking_count_before, cause);
        VMThread::execute(&op);
      }
    }
  } while (retry_gc);
}

bool G1CollectedHeap::is_in(const void* p) const {
  if (_g1_committed.contains(p)) {
    // Given that we know that p is in the committed space,
    // heap_region_containing_raw() should successfully
    // return the containing region.
    HeapRegion* hr = heap_region_containing_raw(p);
    return hr->is_in(p);
  } else {
    return false;
  }
}

// Iteration functions.

// Iterates an OopClosure over all ref-containing fields of objects
// within a HeapRegion.

class IterateOopClosureRegionClosure: public HeapRegionClosure {
  MemRegion _mr;
  ExtendedOopClosure* _cl;
public:
  IterateOopClosureRegionClosure(MemRegion mr, ExtendedOopClosure* cl)
    : _mr(mr), _cl(cl) {}
  bool doHeapRegion(HeapRegion* r) {
    if (!r->continuesHumongous()) {
      r->oop_iterate(_cl);
    }
    return false;
  }
};

void G1CollectedHeap::oop_iterate(ExtendedOopClosure* cl) {
  IterateOopClosureRegionClosure blk(_g1_committed, cl);
  heap_region_iterate(&blk);
}

void G1CollectedHeap::oop_iterate(MemRegion mr, ExtendedOopClosure* cl) {
  IterateOopClosureRegionClosure blk(mr, cl);
  heap_region_iterate(&blk);
}

// Iterates an ObjectClosure over all objects within a HeapRegion.

class IterateObjectClosureRegionClosure: public HeapRegionClosure {
  ObjectClosure* _cl;
public:
  IterateObjectClosureRegionClosure(ObjectClosure* cl) : _cl(cl) {}
  bool doHeapRegion(HeapRegion* r) {
    if (! r->continuesHumongous()) {
      r->object_iterate(_cl);
    }
    return false;
  }
};

void G1CollectedHeap::object_iterate(ObjectClosure* cl) {
  IterateObjectClosureRegionClosure blk(cl);
  heap_region_iterate(&blk);
}

// Calls a SpaceClosure on a HeapRegion.

class SpaceClosureRegionClosure: public HeapRegionClosure {
  SpaceClosure* _cl;
public:
  SpaceClosureRegionClosure(SpaceClosure* cl) : _cl(cl) {}
  bool doHeapRegion(HeapRegion* r) {
    _cl->do_space(r);
    return false;
  }
};

void G1CollectedHeap::space_iterate(SpaceClosure* cl) {
  SpaceClosureRegionClosure blk(cl);
  heap_region_iterate(&blk);
}

void G1CollectedHeap::heap_region_iterate(HeapRegionClosure* cl) const {
  _hrs.iterate(cl);
}

void
G1CollectedHeap::heap_region_par_iterate_chunked(HeapRegionClosure* cl,
                                                 uint worker_id,
                                                 uint no_of_par_workers,
                                                 jint claim_value) {
  const uint regions = n_regions();
  const uint max_workers = (G1CollectedHeap::use_parallel_gc_threads() ?
                             no_of_par_workers :
                             1);
  assert(UseDynamicNumberOfGCThreads ||
         no_of_par_workers == workers()->total_workers(),
         "Non dynamic should use fixed number of workers");
  // try to spread out the starting points of the workers
  const HeapRegion* start_hr =
                        start_region_for_worker(worker_id, no_of_par_workers);
  const uint start_index = start_hr->hrs_index();

  // each worker will actually look at all regions
  for (uint count = 0; count < regions; ++count) {
    const uint index = (start_index + count) % regions;
    assert(0 <= index && index < regions, "sanity");
    HeapRegion* r = region_at(index);
    // we'll ignore "continues humongous" regions (we'll process them
    // when we come across their corresponding "start humongous"
    // region) and regions already claimed
    if (r->claim_value() == claim_value || r->continuesHumongous()) {
      continue;
    }
    // OK, try to claim it
    if (r->claimHeapRegion(claim_value)) {
      // success!
      assert(!r->continuesHumongous(), "sanity");
      if (r->startsHumongous()) {
        // If the region is "starts humongous" we'll iterate over its
        // "continues humongous" first; in fact we'll do them
        // first. The order is important. In on case, calling the
        // closure on the "starts humongous" region might de-allocate
        // and clear all its "continues humongous" regions and, as a
        // result, we might end up processing them twice. So, we'll do
        // them first (notice: most closures will ignore them anyway) and
        // then we'll do the "starts humongous" region.
        for (uint ch_index = index + 1; ch_index < regions; ++ch_index) {
          HeapRegion* chr = region_at(ch_index);

          // if the region has already been claimed or it's not
          // "continues humongous" we're done
          if (chr->claim_value() == claim_value ||
              !chr->continuesHumongous()) {
            break;
          }

          // No one should have claimed it directly. We can given
          // that we claimed its "starts humongous" region.
          assert(chr->claim_value() != claim_value, "sanity");
          assert(chr->humongous_start_region() == r, "sanity");

          if (chr->claimHeapRegion(claim_value)) {
            // we should always be able to claim it; no one else should
            // be trying to claim this region

            bool res2 = cl->doHeapRegion(chr);
            assert(!res2, "Should not abort");

            // Right now, this holds (i.e., no closure that actually
            // does something with "continues humongous" regions
            // clears them). We might have to weaken it in the future,
            // but let's leave these two asserts here for extra safety.
            assert(chr->continuesHumongous(), "should still be the case");
            assert(chr->humongous_start_region() == r, "sanity");
          } else {
            guarantee(false, "we should not reach here");
          }
        }
      }

      assert(!r->continuesHumongous(), "sanity");
      bool res = cl->doHeapRegion(r);
      assert(!res, "Should not abort");
    }
  }
}

class ResetClaimValuesClosure: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion* r) {
    r->set_claim_value(HeapRegion::InitialClaimValue);
    return false;
  }
};

void G1CollectedHeap::reset_heap_region_claim_values() {
  ResetClaimValuesClosure blk;
  heap_region_iterate(&blk);
}

void G1CollectedHeap::reset_cset_heap_region_claim_values() {
  ResetClaimValuesClosure blk;
  collection_set_iterate(&blk);
}

#ifdef ASSERT
// This checks whether all regions in the heap have the correct claim
// value. I also piggy-backed on this a check to ensure that the
// humongous_start_region() information on "continues humongous"
// regions is correct.

class CheckClaimValuesClosure : public HeapRegionClosure {
private:
  jint _claim_value;
  uint _failures;
  HeapRegion* _sh_region;

public:
  CheckClaimValuesClosure(jint claim_value) :
    _claim_value(claim_value), _failures(0), _sh_region(NULL) { }
  bool doHeapRegion(HeapRegion* r) {
    if (r->claim_value() != _claim_value) {
      gclog_or_tty->print_cr("Region " HR_FORMAT ", "
                             "claim value = %d, should be %d",
                             HR_FORMAT_PARAMS(r),
                             r->claim_value(), _claim_value);
      ++_failures;
    }
    if (!r->isHumongous()) {
      _sh_region = NULL;
    } else if (r->startsHumongous()) {
      _sh_region = r;
    } else if (r->continuesHumongous()) {
      if (r->humongous_start_region() != _sh_region) {
        gclog_or_tty->print_cr("Region " HR_FORMAT ", "
                               "HS = "PTR_FORMAT", should be "PTR_FORMAT,
                               HR_FORMAT_PARAMS(r),
                               r->humongous_start_region(),
                               _sh_region);
        ++_failures;
      }
    }
    return false;
  }
  uint failures() { return _failures; }
};

bool G1CollectedHeap::check_heap_region_claim_values(jint claim_value) {
  CheckClaimValuesClosure cl(claim_value);
  heap_region_iterate(&cl);
  return cl.failures() == 0;
}

class CheckClaimValuesInCSetHRClosure: public HeapRegionClosure {
private:
  jint _claim_value;
  uint _failures;

public:
  CheckClaimValuesInCSetHRClosure(jint claim_value) :
    _claim_value(claim_value), _failures(0) { }

  uint failures() { return _failures; }

  bool doHeapRegion(HeapRegion* hr) {
    assert(hr->in_collection_set(), "how?");
    assert(!hr->isHumongous(), "H-region in CSet");
    if (hr->claim_value() != _claim_value) {
      gclog_or_tty->print_cr("CSet Region " HR_FORMAT ", "
                             "claim value = %d, should be %d",
                             HR_FORMAT_PARAMS(hr),
                             hr->claim_value(), _claim_value);
      _failures += 1;
    }
    return false;
  }
};

bool G1CollectedHeap::check_cset_heap_region_claim_values(jint claim_value) {
  CheckClaimValuesInCSetHRClosure cl(claim_value);
  collection_set_iterate(&cl);
  return cl.failures() == 0;
}
#endif // ASSERT

// Clear the cached CSet starting regions and (more importantly)
// the time stamps. Called when we reset the GC time stamp.
void G1CollectedHeap::clear_cset_start_regions() {
  assert(_worker_cset_start_region != NULL, "sanity");
  assert(_worker_cset_start_region_time_stamp != NULL, "sanity");

  int n_queues = MAX2((int)ParallelGCThreads, 1);
  for (int i = 0; i < n_queues; i++) {
    _worker_cset_start_region[i] = NULL;
    _worker_cset_start_region_time_stamp[i] = 0;
  }
}

// Given the id of a worker, obtain or calculate a suitable
// starting region for iterating over the current collection set.
HeapRegion* G1CollectedHeap::start_cset_region_for_worker(int worker_i) {
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
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    uint cs_size = g1_policy()->cset_region_length();
    uint active_workers = workers()->active_workers();
    assert(UseDynamicNumberOfGCThreads ||
             active_workers == workers()->total_workers(),
             "Unless dynamic should use total workers");

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

HeapRegion* G1CollectedHeap::start_region_for_worker(uint worker_i,
                                                     uint no_of_par_workers) {
  uint worker_num =
           G1CollectedHeap::use_parallel_gc_threads() ? no_of_par_workers : 1U;
  assert(UseDynamicNumberOfGCThreads ||
         no_of_par_workers == workers()->total_workers(),
         "Non dynamic should use fixed number of workers");
  const uint start_index = n_regions() * worker_i / worker_num;
  return region_at(start_index);
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

CompactibleSpace* G1CollectedHeap::first_compactible_space() {
  return n_regions() > 0 ? region_at(0) : NULL;
}


Space* G1CollectedHeap::space_containing(const void* addr) const {
  Space* res = heap_region_containing(addr);
  return res;
}

HeapWord* G1CollectedHeap::block_start(const void* addr) const {
  Space* sp = space_containing(addr);
  if (sp != NULL) {
    return sp->block_start(addr);
  }
  return NULL;
}

size_t G1CollectedHeap::block_size(const HeapWord* addr) const {
  Space* sp = space_containing(addr);
  assert(sp != NULL, "block_size of address outside of heap");
  return sp->block_size(addr);
}

bool G1CollectedHeap::block_is_obj(const HeapWord* addr) const {
  Space* sp = space_containing(addr);
  return sp->block_is_obj(addr);
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
// must be smaller than the humongous object limit.
size_t G1CollectedHeap::max_tlab_size() const {
  return align_size_down(_humongous_object_threshold_in_words - 1, MinObjAlignment);
}

size_t G1CollectedHeap::unsafe_max_tlab_alloc(Thread* ignored) const {
  // Return the remaining space in the cur alloc region, but not less than
  // the min TLAB size.

  // Also, this value can be at most the humongous object threshold,
  // since we can't allow tlabs to grow big enough to accommodate
  // humongous objects.

  HeapRegion* hr = _mutator_alloc_region.get();
  size_t max_tlab = max_tlab_size() * wordSize;
  if (hr == NULL) {
    return max_tlab;
  } else {
    return MIN2(MAX2(hr->free(), (size_t) MinTLABSize), max_tlab);
  }
}

size_t G1CollectedHeap::max_capacity() const {
  return _g1_reserved.byte_size();
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
        gclog_or_tty->print_cr("Root location "PTR_FORMAT" "
                              "points to dead obj "PTR_FORMAT, p, (void*) obj);
        if (_vo == VerifyOption_G1UseMarkWord) {
          gclog_or_tty->print_cr("  Mark word: "PTR_FORMAT, (void*)(obj->mark()));
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
        gclog_or_tty->print_cr("Code root location "PTR_FORMAT" "
                              "from nmethod "PTR_FORMAT" not in strong "
                              "code roots for region ["PTR_FORMAT","PTR_FORMAT")",
                              p, _nm, hr->bottom(), hr->end());
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
      guarantee(k->has_modified_oops(), err_msg("Klass %p, has young refs but is not dirty.", k));
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

class PrintObjsInRegionClosure : public ObjectClosure {
  HeapRegion *_hr;
  G1CollectedHeap *_g1;
public:
  PrintObjsInRegionClosure(HeapRegion *hr) : _hr(hr) {
    _g1 = G1CollectedHeap::heap();
  };

  void do_object(oop o) {
    if (o != NULL) {
      HeapWord *start = (HeapWord *) o;
      size_t word_sz = o->size();
      gclog_or_tty->print("\nPrinting obj "PTR_FORMAT" of size " SIZE_FORMAT
                          " isMarkedPrev %d isMarkedNext %d isAllocSince %d\n",
                          (void*) o, word_sz,
                          _g1->isMarkedPrev(o),
                          _g1->isMarkedNext(o),
                          _hr->obj_allocated_since_prev_marking(o));
      HeapWord *end = start + word_sz;
      HeapWord *cur;
      int *val;
      for (cur = start; cur < end; cur++) {
        val = (int *) cur;
        gclog_or_tty->print("\t "PTR_FORMAT":"PTR_FORMAT"\n", val, *val);
      }
    }
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
    if (!r->continuesHumongous()) {
      bool failures = false;
      r->verify(_vo, &failures);
      if (failures) {
        _failures = true;
      } else {
        VerifyObjsInRegionClosure not_dead_yet_cl(r, _vo);
        r->object_iterate(&not_dead_yet_cl);
        if (_vo != VerifyOption_G1UseNextMarking) {
          if (r->max_live_bytes() < not_dead_yet_cl.live_bytes()) {
            gclog_or_tty->print_cr("["PTR_FORMAT","PTR_FORMAT"] "
                                   "max_live_bytes "SIZE_FORMAT" "
                                   "< calculated "SIZE_FORMAT,
                                   r->bottom(), r->end(),
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
  G1CollectedHeap* _g1h;
  VerifyOption     _vo;
  bool             _failures;

public:
  // _vo == UsePrevMarking -> use "prev" marking information,
  // _vo == UseNextMarking -> use "next" marking information,
  // _vo == UseMarkWord    -> use mark word from object header.
  G1ParVerifyTask(G1CollectedHeap* g1h, VerifyOption vo) :
    AbstractGangTask("Parallel verify task"),
    _g1h(g1h),
    _vo(vo),
    _failures(false) { }

  bool failures() {
    return _failures;
  }

  void work(uint worker_id) {
    HandleMark hm;
    VerifyRegionClosure blk(true, _vo);
    _g1h->heap_region_par_iterate_chunked(&blk, worker_id,
                                          _g1h->workers()->active_workers(),
                                          HeapRegion::ParVerifyClaimValue);
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

    // We apply the relevant closures to all the oops in the
    // system dictionary, class loader data graph and the string table.
    // Don't verify the code cache here, since it's verified below.
    const int so = SO_AllClasses | SO_Strings;

    // Need cleared claim bits for the strong roots processing
    ClassLoaderDataGraph::clear_claimed_marks();

    process_strong_roots(true,      // activate StrongRootsScope
                         ScanningOption(so),  // roots scanning options
                         &rootsCl,
                         &klassCl
                         );

    // Verify the nmethods in the code cache.
    G1VerifyCodeRootOopClosure codeRootsCl(this, &rootsCl, vo);
    G1VerifyCodeRootBlobClosure blobsCl(&codeRootsCl);
    CodeCache::blobs_do(&blobsCl);

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
      assert(check_heap_region_claim_values(HeapRegion::InitialClaimValue),
             "sanity check");

      G1ParVerifyTask task(this, vo);
      assert(UseDynamicNumberOfGCThreads ||
        workers()->active_workers() == workers()->total_workers(),
        "If not dynamic should be using all the workers");
      int n_workers = workers()->active_workers();
      set_par_threads(n_workers);
      workers()->run_task(&task);
      set_par_threads(0);
      if (task.failures()) {
        failures = true;
      }

      // Checks that the expected amount of parallel work was done.
      // The implication is that n_workers is > 0.
      assert(check_heap_region_claim_values(HeapRegion::ParVerifyClaimValue),
             "sanity check");

      reset_heap_region_claim_values();

      assert(check_heap_region_claim_values(HeapRegion::InitialClaimValue),
             "sanity check");
    } else {
      VerifyRegionClosure blk(false, vo);
      heap_region_iterate(&blk);
      if (blk.failures()) {
        failures = true;
      }
    }
    if (!silent) gclog_or_tty->print("RemSet ");
    rem_set()->verify();

    if (failures) {
      gclog_or_tty->print_cr("Heap:");
      // It helps to have the per-region information in the output to
      // help us track down what went wrong. This is why we call
      // print_extended_on() instead of print_on().
      print_extended_on(gclog_or_tty);
      gclog_or_tty->print_cr("");
#ifndef PRODUCT
      if (VerifyDuringGC && G1VerifyDuringGCPrintReachable) {
        concurrent_mark()->print_reachable("at-verification-failure",
                                           vo, false /* all */);
      }
#endif
      gclog_or_tty->flush();
    }
    guarantee(!failures, "there should not have been any failures");
  } else {
    if (!silent)
      gclog_or_tty->print("(SKIPPING roots, heapRegionSets, heapRegions, remset) ");
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

void G1CollectedHeap::print_on(outputStream* st) const {
  st->print(" %-20s", "garbage-first heap");
  st->print(" total " SIZE_FORMAT "K, used " SIZE_FORMAT "K",
            capacity()/K, used_unlocked()/K);
  st->print(" [" INTPTR_FORMAT ", " INTPTR_FORMAT ", " INTPTR_FORMAT ")",
            _g1_storage.low_boundary(),
            _g1_storage.high(),
            _g1_storage.high_boundary());
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
  st->print_cr("Heap Regions: (Y=young(eden), SU=young(survivor), "
               "HS=humongous(starts), HC=humongous(continues), "
               "CS=collection set, F=free, TS=gc time stamp, "
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
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    workers()->print_worker_threads_on(st);
  }
  _cmThread->print_on(st);
  st->cr();
  _cm->print_worker_threads_on(st);
  _cg1r->print_worker_threads_on(st);
}

void G1CollectedHeap::gc_threads_do(ThreadClosure* tc) const {
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    workers()->threads_do(tc);
  }
  tc->do_thread(_cmThread);
  _cg1r->threads_do(tc);
}

void G1CollectedHeap::print_tracing_info() const {
  // We'll overload this to mean "trace GC pause statistics."
  if (TraceGen0Time || TraceGen1Time) {
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
  SpecializationStats::print();
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

    gclog_or_tty->print_cr("Printing RSet for region "HR_FORMAT,
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
    gclog_or_tty->print_cr(msg);
    gclog_or_tty->cr();
  }

  ~PrintRSetsClosure() {
    gclog_or_tty->print_cr("Occupied Sum: "SIZE_FORMAT, _occupied_sum);
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

G1CollectedHeap* G1CollectedHeap::heap() {
  assert(_sh->kind() == CollectedHeap::G1CollectedHeap,
         "not a garbage-first heap");
  return _g1h;
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

void G1CollectedHeap::gc_epilogue(bool full /* Ignored */) {

  if (G1SummarizeRSetStats &&
      (G1SummarizeRSetStatsPeriod > 0) &&
      // we are at the end of the GC. Total collections has already been increased.
      ((total_collections() - 1) % G1SummarizeRSetStatsPeriod == 0)) {
    g1_rem_set()->print_periodic_summary_info("After GC RS summary");
  }

  // FIXME: what is this about?
  // I'm ignoring the "fill_newgen()" call if "alloc_event_enabled"
  // is set.
  COMPILER2_PRESENT(assert(DerivedPointerTable::is_empty(),
                        "derived pointer present"));
  // always_do_update_barrier = true;

  resize_all_tlabs();

  // We have just completed a GC. Update the soft reference
  // policy with the new heap occupancy
  Universe::update_heap_info_at_gc();
}

HeapWord* G1CollectedHeap::do_collection_pause(size_t word_size,
                                               unsigned int gc_count_before,
                                               bool* succeeded,
                                               GCCause::Cause gc_cause) {
  assert_heap_not_locked_and_not_at_safepoint();
  g1_policy()->record_stop_world_start();
  VM_G1IncCollectionPause op(gc_count_before,
                             word_size,
                             false, /* should_initiate_conc_mark */
                             g1_policy()->max_pause_time_ms(),
                             gc_cause);
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

size_t G1CollectedHeap::cards_scanned() {
  return g1_rem_set()->cardsScanned();
}

void
G1CollectedHeap::setup_surviving_young_words() {
  assert(_surviving_young_words == NULL, "pre-condition");
  uint array_length = g1_policy()->young_cset_region_length();
  _surviving_young_words = NEW_C_HEAP_ARRAY(size_t, (size_t) array_length, mtGC);
  if (_surviving_young_words == NULL) {
    vm_exit_out_of_memory(sizeof(size_t) * array_length, OOM_MALLOC_ERROR,
                          "Not enough space for young surv words summary.");
  }
  memset(_surviving_young_words, 0, (size_t) array_length * sizeof(size_t));
#ifdef ASSERT
  for (uint i = 0;  i < array_length; ++i) {
    assert( _surviving_young_words[i] == 0, "memset above" );
  }
#endif // !ASSERT
}

void
G1CollectedHeap::update_surviving_young_words(size_t* surv_young_words) {
  MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
  uint array_length = g1_policy()->young_cset_region_length();
  for (uint i = 0; i < array_length; ++i) {
    _surviving_young_words[i] += surv_young_words[i];
  }
}

void
G1CollectedHeap::cleanup_surviving_young_words() {
  guarantee( _surviving_young_words != NULL, "pre-condition" );
  FREE_C_HEAP_ARRAY(size_t, _surviving_young_words, mtGC);
  _surviving_young_words = NULL;
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

#if TASKQUEUE_STATS
void G1CollectedHeap::print_taskqueue_stats_hdr(outputStream* const st) {
  st->print_raw_cr("GC Task Stats");
  st->print_raw("thr "); TaskQueueStats::print_header(1, st); st->cr();
  st->print_raw("--- "); TaskQueueStats::print_header(2, st); st->cr();
}

void G1CollectedHeap::print_taskqueue_stats(outputStream* const st) const {
  print_taskqueue_stats_hdr(st);

  TaskQueueStats totals;
  const int n = workers() != NULL ? workers()->total_workers() : 1;
  for (int i = 0; i < n; ++i) {
    st->print("%3d ", i); task_queue(i)->stats.print(st); st->cr();
    totals += task_queue(i)->stats;
  }
  st->print_raw("tot "); totals.print(st); st->cr();

  DEBUG_ONLY(totals.verify());
}

void G1CollectedHeap::reset_taskqueue_stats() {
  const int n = workers() != NULL ? workers()->total_workers() : 1;
  for (int i = 0; i < n; ++i) {
    task_queue(i)->stats.reset();
  }
}
#endif // TASKQUEUE_STATS

void G1CollectedHeap::log_gc_header() {
  if (!G1Log::fine()) {
    return;
  }

  gclog_or_tty->date_stamp(PrintGCDateStamps);
  gclog_or_tty->stamp(PrintGCTimeStamps);

  GCCauseString gc_cause_str = GCCauseString("GC pause", gc_cause())
    .append(g1_policy()->gcs_are_young() ? "(young)" : "(mixed)")
    .append(g1_policy()->during_initial_mark_pause() ? " (initial-mark)" : "");

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
    g1_policy()->phase_times()->note_gc_end();
    g1_policy()->phase_times()->print(pause_time_sec);
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

bool
G1CollectedHeap::do_collection_pause_at_safepoint(double target_pause_time_ms) {
  assert_at_safepoint(true /* should_be_vm_thread */);
  guarantee(!is_gc_active(), "collection is not reentrant");

  if (GC_locker::check_active_before_gc()) {
    return false;
  }

  _gc_timer_stw->register_gc_start();

  _gc_tracer_stw->report_gc_start(gc_cause(), _gc_timer_stw->gc_start());

  SvcGCMarker sgcm(SvcGCMarker::MINOR);
  ResourceMark rm;

  print_heap_before_gc();
  trace_heap_before_gc(_gc_tracer_stw);

  HRSPhaseSetter x(HRSPhaseEvacuation);
  verify_region_sets_optional();
  verify_dirty_young_regions();

  // This call will decide whether this pause is an initial-mark
  // pause. If it is, during_initial_mark_pause() will return true
  // for the duration of this pause.
  g1_policy()->decide_on_conc_mark_initiation();

  // We do not allow initial-mark to be piggy-backed on a mixed GC.
  assert(!g1_policy()->during_initial_mark_pause() ||
          g1_policy()->gcs_are_young(), "sanity");

  // We also do not allow mixed GCs during marking.
  assert(!mark_in_progress() || g1_policy()->gcs_are_young(), "sanity");

  // Record whether this pause is an initial mark. When the current
  // thread has completed its logging output and it's safe to signal
  // the CM thread, the flag's value in the policy has been reset.
  bool should_start_conc_mark = g1_policy()->during_initial_mark_pause();

  // Inner scope for scope based logging, timers, and stats collection
  {
    EvacuationInfo evacuation_info;

    if (g1_policy()->during_initial_mark_pause()) {
      // We are about to start a marking cycle, so we increment the
      // full collection counter.
      increment_old_marking_cycles_started();
      register_concurrent_cycle_start(_gc_timer_stw->gc_start());
    }

    _gc_tracer_stw->report_yc_type(yc_type());

    TraceCPUTime tcpu(G1Log::finer(), true, gclog_or_tty);

    int active_workers = (G1CollectedHeap::use_parallel_gc_threads() ?
                                workers()->active_workers() : 1);
    double pause_start_sec = os::elapsedTime();
    g1_policy()->phase_times()->note_gc_start(active_workers);
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
    assert(check_heap_region_claim_values(HeapRegion::InitialClaimValue),
           "sanity check");

    // Don't dynamically change the number of GC threads this early.  A value of
    // 0 is used to indicate serial work.  When parallel work is done,
    // it will be set.

    { // Call to jvmpi::post_class_unload_events must occur outside of active GC
      IsGCActiveMark x;

      gc_prologue(false);
      increment_total_collections(false /* full gc */);
      increment_gc_time_stamp();

      verify_before_gc();

      COMPILER2_PRESENT(DerivedPointerTable::clear());

      // Please see comment in g1CollectedHeap.hpp and
      // G1CollectedHeap::ref_processing_init() to see how
      // reference processing currently works in G1.

      // Enable discovery in the STW reference processor
      ref_processor_stw()->enable_discovery(true /*verify_disabled*/,
                                            true /*verify_no_refs*/);

      {
        // We want to temporarily turn off discovery by the
        // CM ref processor, if necessary, and turn it back on
        // on again later if we do. Using a scoped
        // NoRefDiscovery object will do this.
        NoRefDiscovery no_cm_discovery(ref_processor_cm());

        // Forget the current alloc region (we might even choose it to be part
        // of the collection set!).
        release_mutator_alloc_region();

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

#if YOUNG_LIST_VERBOSE
        gclog_or_tty->print_cr("\nBefore recording pause start.\nYoung_list:");
        _young_list->print();
        g1_policy()->print_collection_set(g1_policy()->inc_cset_head(), gclog_or_tty);
#endif // YOUNG_LIST_VERBOSE

        g1_policy()->record_collection_pause_start(sample_start_time_sec);

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

#if YOUNG_LIST_VERBOSE
        gclog_or_tty->print_cr("\nAfter recording pause start.\nYoung_list:");
        _young_list->print();
#endif // YOUNG_LIST_VERBOSE

        if (g1_policy()->during_initial_mark_pause()) {
          concurrent_mark()->checkpointRootsInitialPre();
        }

#if YOUNG_LIST_VERBOSE
        gclog_or_tty->print_cr("\nBefore choosing collection set.\nYoung_list:");
        _young_list->print();
        g1_policy()->print_collection_set(g1_policy()->inc_cset_head(), gclog_or_tty);
#endif // YOUNG_LIST_VERBOSE

        g1_policy()->finalize_cset(target_pause_time_ms, evacuation_info);

        _cm->note_start_of_gc();
        // We should not verify the per-thread SATB buffers given that
        // we have not filtered them yet (we'll do so during the
        // GC). We also call this after finalize_cset() to
        // ensure that the CSet has been finalized.
        _cm->verify_no_cset_oops(true  /* verify_stacks */,
                                 true  /* verify_enqueued_buffers */,
                                 false /* verify_thread_buffers */,
                                 true  /* verify_fingers */);

        if (_hr_printer.is_active()) {
          HeapRegion* hr = g1_policy()->collection_set();
          while (hr != NULL) {
            G1HRPrinter::RegionType type;
            if (!hr->is_young()) {
              type = G1HRPrinter::Old;
            } else if (hr->is_survivor()) {
              type = G1HRPrinter::Survivor;
            } else {
              type = G1HRPrinter::Eden;
            }
            _hr_printer.cset(hr);
            hr = hr->next_in_collection_set();
          }
        }

#ifdef ASSERT
        VerifyCSetClosure cl;
        collection_set_iterate(&cl);
#endif // ASSERT

        setup_surviving_young_words();

        // Initialize the GC alloc regions.
        init_gc_alloc_regions(evacuation_info);

        // Actually do the work...
        evacuate_collection_set(evacuation_info);

        // We do this to mainly verify the per-thread SATB buffers
        // (which have been filtered by now) since we didn't verify
        // them earlier. No point in re-checking the stacks / enqueued
        // buffers given that the CSet has not changed since last time
        // we checked.
        _cm->verify_no_cset_oops(false /* verify_stacks */,
                                 false /* verify_enqueued_buffers */,
                                 true  /* verify_thread_buffers */,
                                 true  /* verify_fingers */);

        free_collection_set(g1_policy()->collection_set(), evacuation_info);
        g1_policy()->clear_collection_set();

        cleanup_surviving_young_words();

        // Start a new incremental collection set for the next pause.
        g1_policy()->start_incremental_cset_building();

        // Clear the _cset_fast_test bitmap in anticipation of adding
        // regions to the incremental collection set for the next
        // evacuation pause.
        clear_cset_fast_test();

        _young_list->reset_sampled_info();

        // Don't check the whole heap at this point as the
        // GC alloc regions from this pause have been tagged
        // as survivors and moved on to the survivor list.
        // Survivor regions will fail the !is_young() check.
        assert(check_young_list_empty(false /* check_heap */),
          "young list should be empty");

#if YOUNG_LIST_VERBOSE
        gclog_or_tty->print_cr("Before recording survivors.\nYoung List:");
        _young_list->print();
#endif // YOUNG_LIST_VERBOSE

        g1_policy()->record_survivor_regions(_young_list->survivor_length(),
                                             _young_list->first_survivor_region(),
                                             _young_list->last_survivor_region());

        _young_list->reset_auxilary_lists();

        if (evacuation_failed()) {
          _summary_bytes_used = recalculate_used();
          uint n_queues = MAX2((int)ParallelGCThreads, 1);
          for (uint i = 0; i < n_queues; i++) {
            if (_evacuation_failed_info_array[i].has_failed()) {
              _gc_tracer_stw->report_evacuation_failed(_evacuation_failed_info_array[i]);
            }
          }
        } else {
          // The "used" of the the collection set have already been subtracted
          // when they were freed.  Add in the bytes evacuated.
          _summary_bytes_used += g1_policy()->bytes_copied_during_gc();
        }

        if (g1_policy()->during_initial_mark_pause()) {
          // We have to do this before we notify the CM threads that
          // they can start working to make sure that all the
          // appropriate initialization is done on the CM object.
          concurrent_mark()->checkpointRootsInitialPost();
          set_marking_started();
          // Note that we don't actually trigger the CM thread at
          // this point. We do that later when we're sure that
          // the current thread has completed its logging output.
        }

        allocate_dummy_regions();

#if YOUNG_LIST_VERBOSE
        gclog_or_tty->print_cr("\nEnd of the pause.\nYoung_list:");
        _young_list->print();
        g1_policy()->print_collection_set(g1_policy()->inc_cset_head(), gclog_or_tty);
#endif // YOUNG_LIST_VERBOSE

        init_mutator_alloc_region();

        {
          size_t expand_bytes = g1_policy()->expansion_amount();
          if (expand_bytes > 0) {
            size_t bytes_before = capacity();
            // No need for an ergo verbose message here,
            // expansion_amount() does this when it returns a value > 0.
            if (!expand(expand_bytes)) {
              // We failed to expand the heap so let's verify that
              // committed/uncommitted amount match the backing store
              assert(capacity() == _g1_storage.committed_size(), "committed size mismatch");
              assert(max_capacity() == _g1_storage.reserved_size(), "reserved size mismatch");
            }
          }
        }

        // We redo the verification but now wrt to the new CSet which
        // has just got initialized after the previous CSet was freed.
        _cm->verify_no_cset_oops(true  /* verify_stacks */,
                                 true  /* verify_enqueued_buffers */,
                                 true  /* verify_thread_buffers */,
                                 true  /* verify_fingers */);
        _cm->note_end_of_gc();

        // This timing is only used by the ergonomics to handle our pause target.
        // It is unclear why this should not include the full pause. We will
        // investigate this in CR 7178365.
        double sample_end_time_sec = os::elapsedTime();
        double pause_time_ms = (sample_end_time_sec - sample_start_time_sec) * MILLIUNITS;
        g1_policy()->record_collection_pause_end(pause_time_ms, evacuation_info);

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

        assert(!ref_processor_stw()->discovery_enabled(), "Postcondition");
        ref_processor_stw()->verify_no_references_recorded();

        // CM reference discovery will be re-enabled if necessary.
      }

      // We should do this after we potentially expand the heap so
      // that all the COMMIT events are generated before the end GC
      // event, and after we retire the GC alloc regions so that all
      // RETIRE events are generated before the end GC event.
      _hr_printer.end_gc(false /* full */, (size_t) total_collections());

      if (mark_in_progress()) {
        concurrent_mark()->update_g1_committed();
      }

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

    _hrs.verify_optional();
    verify_region_sets_optional();

    TASKQUEUE_STATS_ONLY(if (ParallelGCVerbose) print_taskqueue_stats());
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
    // ConcurrentGCThread::safepoint_desynchronize().
    doConcurrentMark();
  }

  return true;
}

size_t G1CollectedHeap::desired_plab_sz(GCAllocPurpose purpose)
{
  size_t gclab_word_size;
  switch (purpose) {
    case GCAllocForSurvived:
      gclab_word_size = _survivor_plab_stats.desired_plab_sz();
      break;
    case GCAllocForTenured:
      gclab_word_size = _old_plab_stats.desired_plab_sz();
      break;
    default:
      assert(false, "unknown GCAllocPurpose");
      gclab_word_size = _old_plab_stats.desired_plab_sz();
      break;
  }

  // Prevent humongous PLAB sizes for two reasons:
  // * PLABs are allocated using a similar paths as oops, but should
  //   never be in a humongous region
  // * Allowing humongous PLABs needlessly churns the region free lists
  return MIN2(_humongous_object_threshold_in_words, gclab_word_size);
}

void G1CollectedHeap::init_mutator_alloc_region() {
  assert(_mutator_alloc_region.get() == NULL, "pre-condition");
  _mutator_alloc_region.init();
}

void G1CollectedHeap::release_mutator_alloc_region() {
  _mutator_alloc_region.release();
  assert(_mutator_alloc_region.get() == NULL, "post-condition");
}

void G1CollectedHeap::init_gc_alloc_regions(EvacuationInfo& evacuation_info) {
  assert_at_safepoint(true /* should_be_vm_thread */);

  _survivor_gc_alloc_region.init();
  _old_gc_alloc_region.init();
  HeapRegion* retained_region = _retained_old_gc_alloc_region;
  _retained_old_gc_alloc_region = NULL;

  // We will discard the current GC alloc region if:
  // a) it's in the collection set (it can happen!),
  // b) it's already full (no point in using it),
  // c) it's empty (this means that it was emptied during
  // a cleanup and it should be on the free list now), or
  // d) it's humongous (this means that it was emptied
  // during a cleanup and was added to the free list, but
  // has been subsequently used to allocate a humongous
  // object that may be less than the region size).
  if (retained_region != NULL &&
      !retained_region->in_collection_set() &&
      !(retained_region->top() == retained_region->end()) &&
      !retained_region->is_empty() &&
      !retained_region->isHumongous()) {
    retained_region->set_saved_mark();
    // The retained region was added to the old region set when it was
    // retired. We have to remove it now, since we don't allow regions
    // we allocate to in the region sets. We'll re-add it later, when
    // it's retired again.
    _old_set.remove(retained_region);
    bool during_im = g1_policy()->during_initial_mark_pause();
    retained_region->note_start_of_copying(during_im);
    _old_gc_alloc_region.set(retained_region);
    _hr_printer.reuse(retained_region);
    evacuation_info.set_alloc_regions_used_before(retained_region->used());
  }
}

void G1CollectedHeap::release_gc_alloc_regions(uint no_of_gc_workers, EvacuationInfo& evacuation_info) {
  evacuation_info.set_allocation_regions(_survivor_gc_alloc_region.count() +
                                         _old_gc_alloc_region.count());
  _survivor_gc_alloc_region.release();
  // If we have an old GC alloc region to release, we'll save it in
  // _retained_old_gc_alloc_region. If we don't
  // _retained_old_gc_alloc_region will become NULL. This is what we
  // want either way so no reason to check explicitly for either
  // condition.
  _retained_old_gc_alloc_region = _old_gc_alloc_region.release();

  if (ResizePLAB) {
    _survivor_plab_stats.adjust_desired_plab_sz(no_of_gc_workers);
    _old_plab_stats.adjust_desired_plab_sz(no_of_gc_workers);
  }
}

void G1CollectedHeap::abandon_gc_alloc_regions() {
  assert(_survivor_gc_alloc_region.get() == NULL, "pre-condition");
  assert(_old_gc_alloc_region.get() == NULL, "pre-condition");
  _retained_old_gc_alloc_region = NULL;
}

void G1CollectedHeap::init_for_evac_failure(OopsInHeapRegionClosure* cl) {
  _drain_in_progress = false;
  set_evac_failure_closure(cl);
  _evac_failure_scan_stack = new (ResourceObj::C_HEAP, mtGC) GrowableArray<oop>(40, true);
}

void G1CollectedHeap::finalize_for_evac_failure() {
  assert(_evac_failure_scan_stack != NULL &&
         _evac_failure_scan_stack->length() == 0,
         "Postcondition");
  assert(!_drain_in_progress, "Postcondition");
  delete _evac_failure_scan_stack;
  _evac_failure_scan_stack = NULL;
}

void G1CollectedHeap::remove_self_forwarding_pointers() {
  assert(check_cset_heap_region_claim_values(HeapRegion::InitialClaimValue), "sanity");

  G1ParRemoveSelfForwardPtrsTask rsfp_task(this);

  if (G1CollectedHeap::use_parallel_gc_threads()) {
    set_par_threads();
    workers()->run_task(&rsfp_task);
    set_par_threads(0);
  } else {
    rsfp_task.work(0);
  }

  assert(check_cset_heap_region_claim_values(HeapRegion::ParEvacFailureClaimValue), "sanity");

  // Reset the claim values in the regions in the collection set.
  reset_cset_heap_region_claim_values();

  assert(check_cset_heap_region_claim_values(HeapRegion::InitialClaimValue), "sanity");

  // Now restore saved marks, if any.
  assert(_objs_with_preserved_marks.size() ==
            _preserved_marks_of_objs.size(), "Both or none.");
  while (!_objs_with_preserved_marks.is_empty()) {
    oop obj = _objs_with_preserved_marks.pop();
    markOop m = _preserved_marks_of_objs.pop();
    obj->set_mark(m);
  }
  _objs_with_preserved_marks.clear(true);
  _preserved_marks_of_objs.clear(true);
}

void G1CollectedHeap::push_on_evac_failure_scan_stack(oop obj) {
  _evac_failure_scan_stack->push(obj);
}

void G1CollectedHeap::drain_evac_failure_scan_stack() {
  assert(_evac_failure_scan_stack != NULL, "precondition");

  while (_evac_failure_scan_stack->length() > 0) {
     oop obj = _evac_failure_scan_stack->pop();
     _evac_failure_closure->set_region(heap_region_containing(obj));
     obj->oop_iterate_backwards(_evac_failure_closure);
  }
}

oop
G1CollectedHeap::handle_evacuation_failure_par(G1ParScanThreadState* _par_scan_state,
                                               oop old) {
  assert(obj_in_cs(old),
         err_msg("obj: "PTR_FORMAT" should still be in the CSet",
                 (HeapWord*) old));
  markOop m = old->mark();
  oop forward_ptr = old->forward_to_atomic(old);
  if (forward_ptr == NULL) {
    // Forward-to-self succeeded.
    assert(_par_scan_state != NULL, "par scan state");
    OopsInHeapRegionClosure* cl = _par_scan_state->evac_failure_closure();
    uint queue_num = _par_scan_state->queue_num();

    _evacuation_failed = true;
    _evacuation_failed_info_array[queue_num].register_copy_failure(old->size());
    if (_evac_failure_closure != cl) {
      MutexLockerEx x(EvacFailureStack_lock, Mutex::_no_safepoint_check_flag);
      assert(!_drain_in_progress,
             "Should only be true while someone holds the lock.");
      // Set the global evac-failure closure to the current thread's.
      assert(_evac_failure_closure == NULL, "Or locking has failed.");
      set_evac_failure_closure(cl);
      // Now do the common part.
      handle_evacuation_failure_common(old, m);
      // Reset to NULL.
      set_evac_failure_closure(NULL);
    } else {
      // The lock is already held, and this is recursive.
      assert(_drain_in_progress, "This should only be the recursive case.");
      handle_evacuation_failure_common(old, m);
    }
    return old;
  } else {
    // Forward-to-self failed. Either someone else managed to allocate
    // space for this object (old != forward_ptr) or they beat us in
    // self-forwarding it (old == forward_ptr).
    assert(old == forward_ptr || !obj_in_cs(forward_ptr),
           err_msg("obj: "PTR_FORMAT" forwarded to: "PTR_FORMAT" "
                   "should not be in the CSet",
                   (HeapWord*) old, (HeapWord*) forward_ptr));
    return forward_ptr;
  }
}

void G1CollectedHeap::handle_evacuation_failure_common(oop old, markOop m) {
  preserve_mark_if_necessary(old, m);

  HeapRegion* r = heap_region_containing(old);
  if (!r->evacuation_failed()) {
    r->set_evacuation_failed(true);
    _hr_printer.evac_failure(r);
  }

  push_on_evac_failure_scan_stack(old);

  if (!_drain_in_progress) {
    // prevent recursion in copy_to_survivor_space()
    _drain_in_progress = true;
    drain_evac_failure_scan_stack();
    _drain_in_progress = false;
  }
}

void G1CollectedHeap::preserve_mark_if_necessary(oop obj, markOop m) {
  assert(evacuation_failed(), "Oversaving!");
  // We want to call the "for_promotion_failure" version only in the
  // case of a promotion failure.
  if (m->must_be_preserved_for_promotion_failure(obj)) {
    _objs_with_preserved_marks.push(obj);
    _preserved_marks_of_objs.push(m);
  }
}

HeapWord* G1CollectedHeap::par_allocate_during_gc(GCAllocPurpose purpose,
                                                  size_t word_size) {
  if (purpose == GCAllocForSurvived) {
    HeapWord* result = survivor_attempt_allocation(word_size);
    if (result != NULL) {
      return result;
    } else {
      // Let's try to allocate in the old gen in case we can fit the
      // object there.
      return old_attempt_allocation(word_size);
    }
  } else {
    assert(purpose ==  GCAllocForTenured, "sanity");
    HeapWord* result = old_attempt_allocation(word_size);
    if (result != NULL) {
      return result;
    } else {
      // Let's try to allocate in the survivors in case we can fit the
      // object there.
      return survivor_attempt_allocation(word_size);
    }
  }

  ShouldNotReachHere();
  // Trying to keep some compilers happy.
  return NULL;
}

G1ParGCAllocBuffer::G1ParGCAllocBuffer(size_t gclab_word_size) :
  ParGCAllocBuffer(gclab_word_size), _retired(false) { }

G1ParScanThreadState::G1ParScanThreadState(G1CollectedHeap* g1h, uint queue_num, ReferenceProcessor* rp)
  : _g1h(g1h),
    _refs(g1h->task_queue(queue_num)),
    _dcq(&g1h->dirty_card_queue_set()),
    _ct_bs(g1h->g1_barrier_set()),
    _g1_rem(g1h->g1_rem_set()),
    _hash_seed(17), _queue_num(queue_num),
    _term_attempts(0),
    _surviving_alloc_buffer(g1h->desired_plab_sz(GCAllocForSurvived)),
    _tenured_alloc_buffer(g1h->desired_plab_sz(GCAllocForTenured)),
    _age_table(false), _scanner(g1h, this, rp),
    _strong_roots_time(0), _term_time(0),
    _alloc_buffer_waste(0), _undo_waste(0) {
  // we allocate G1YoungSurvRateNumRegions plus one entries, since
  // we "sacrifice" entry 0 to keep track of surviving bytes for
  // non-young regions (where the age is -1)
  // We also add a few elements at the beginning and at the end in
  // an attempt to eliminate cache contention
  uint real_length = 1 + _g1h->g1_policy()->young_cset_region_length();
  uint array_length = PADDING_ELEM_NUM +
                      real_length +
                      PADDING_ELEM_NUM;
  _surviving_young_words_base = NEW_C_HEAP_ARRAY(size_t, array_length, mtGC);
  if (_surviving_young_words_base == NULL)
    vm_exit_out_of_memory(array_length * sizeof(size_t), OOM_MALLOC_ERROR,
                          "Not enough space for young surv histo.");
  _surviving_young_words = _surviving_young_words_base + PADDING_ELEM_NUM;
  memset(_surviving_young_words, 0, (size_t) real_length * sizeof(size_t));

  _alloc_buffers[GCAllocForSurvived] = &_surviving_alloc_buffer;
  _alloc_buffers[GCAllocForTenured]  = &_tenured_alloc_buffer;

  _start = os::elapsedTime();
}

void
G1ParScanThreadState::print_termination_stats_hdr(outputStream* const st)
{
  st->print_raw_cr("GC Termination Stats");
  st->print_raw_cr("     elapsed  --strong roots-- -------termination-------"
                   " ------waste (KiB)------");
  st->print_raw_cr("thr     ms        ms      %        ms      %    attempts"
                   "  total   alloc    undo");
  st->print_raw_cr("--- --------- --------- ------ --------- ------ --------"
                   " ------- ------- -------");
}

void
G1ParScanThreadState::print_termination_stats(int i,
                                              outputStream* const st) const
{
  const double elapsed_ms = elapsed_time() * 1000.0;
  const double s_roots_ms = strong_roots_time() * 1000.0;
  const double term_ms    = term_time() * 1000.0;
  st->print_cr("%3d %9.2f %9.2f %6.2f "
               "%9.2f %6.2f " SIZE_FORMAT_W(8) " "
               SIZE_FORMAT_W(7) " " SIZE_FORMAT_W(7) " " SIZE_FORMAT_W(7),
               i, elapsed_ms, s_roots_ms, s_roots_ms * 100 / elapsed_ms,
               term_ms, term_ms * 100 / elapsed_ms, term_attempts(),
               (alloc_buffer_waste() + undo_waste()) * HeapWordSize / K,
               alloc_buffer_waste() * HeapWordSize / K,
               undo_waste() * HeapWordSize / K);
}

#ifdef ASSERT
bool G1ParScanThreadState::verify_ref(narrowOop* ref) const {
  assert(ref != NULL, "invariant");
  assert(UseCompressedOops, "sanity");
  assert(!has_partial_array_mask(ref), err_msg("ref=" PTR_FORMAT, ref));
  oop p = oopDesc::load_decode_heap_oop(ref);
  assert(_g1h->is_in_g1_reserved(p),
         err_msg("ref=" PTR_FORMAT " p=" PTR_FORMAT, ref, (void *)p));
  return true;
}

bool G1ParScanThreadState::verify_ref(oop* ref) const {
  assert(ref != NULL, "invariant");
  if (has_partial_array_mask(ref)) {
    // Must be in the collection set--it's already been copied.
    oop p = clear_partial_array_mask(ref);
    assert(_g1h->obj_in_cs(p),
           err_msg("ref=" PTR_FORMAT " p=" PTR_FORMAT, ref, (void *)p));
  } else {
    oop p = oopDesc::load_decode_heap_oop(ref);
    assert(_g1h->is_in_g1_reserved(p),
           err_msg("ref=" PTR_FORMAT " p=" PTR_FORMAT, ref, (void *)p));
  }
  return true;
}

bool G1ParScanThreadState::verify_task(StarTask ref) const {
  if (ref.is_narrow()) {
    return verify_ref((narrowOop*) ref);
  } else {
    return verify_ref((oop*) ref);
  }
}
#endif // ASSERT

void G1ParScanThreadState::trim_queue() {
  assert(_evac_cl != NULL, "not set");
  assert(_evac_failure_cl != NULL, "not set");
  assert(_partial_scan_cl != NULL, "not set");

  StarTask ref;
  do {
    // Drain the overflow stack first, so other threads can steal.
    while (refs()->pop_overflow(ref)) {
      deal_with_reference(ref);
    }

    while (refs()->pop_local(ref)) {
      deal_with_reference(ref);
    }
  } while (!refs()->is_empty());
}

G1ParClosureSuper::G1ParClosureSuper(G1CollectedHeap* g1,
                                     G1ParScanThreadState* par_scan_state) :
  _g1(g1), _par_scan_state(par_scan_state),
  _worker_id(par_scan_state->queue_num()) { }

void G1ParCopyHelper::mark_object(oop obj) {
#ifdef ASSERT
  HeapRegion* hr = _g1->heap_region_containing(obj);
  assert(hr != NULL, "sanity");
  assert(!hr->in_collection_set(), "should not mark objects in the CSet");
#endif // ASSERT

  // We know that the object is not moving so it's safe to read its size.
  _cm->grayRoot(obj, (size_t) obj->size(), _worker_id);
}

void G1ParCopyHelper::mark_forwarded_object(oop from_obj, oop to_obj) {
#ifdef ASSERT
  assert(from_obj->is_forwarded(), "from obj should be forwarded");
  assert(from_obj->forwardee() == to_obj, "to obj should be the forwardee");
  assert(from_obj != to_obj, "should not be self-forwarded");

  HeapRegion* from_hr = _g1->heap_region_containing(from_obj);
  assert(from_hr != NULL, "sanity");
  assert(from_hr->in_collection_set(), "from obj should be in the CSet");

  HeapRegion* to_hr = _g1->heap_region_containing(to_obj);
  assert(to_hr != NULL, "sanity");
  assert(!to_hr->in_collection_set(), "should not mark objects in the CSet");
#endif // ASSERT

  // The object might be in the process of being copied by another
  // worker so we cannot trust that its to-space image is
  // well-formed. So we have to read its size from its from-space
  // image which we know should not be changing.
  _cm->grayRoot(to_obj, (size_t) from_obj->size(), _worker_id);
}

oop G1ParScanThreadState::copy_to_survivor_space(oop const old) {
  size_t word_sz = old->size();
  HeapRegion* from_region = _g1h->heap_region_containing_raw(old);
  // +1 to make the -1 indexes valid...
  int       young_index = from_region->young_index_in_cset()+1;
  assert( (from_region->is_young() && young_index >  0) ||
         (!from_region->is_young() && young_index == 0), "invariant" );
  G1CollectorPolicy* g1p = _g1h->g1_policy();
  markOop m = old->mark();
  int age = m->has_displaced_mark_helper() ? m->displaced_mark_helper()->age()
                                           : m->age();
  GCAllocPurpose alloc_purpose = g1p->evacuation_destination(from_region, age,
                                                             word_sz);
  HeapWord* obj_ptr = allocate(alloc_purpose, word_sz);
#ifndef PRODUCT
  // Should this evacuation fail?
  if (_g1h->evacuation_should_fail()) {
    if (obj_ptr != NULL) {
      undo_allocation(alloc_purpose, obj_ptr, word_sz);
      obj_ptr = NULL;
    }
  }
#endif // !PRODUCT

  if (obj_ptr == NULL) {
    // This will either forward-to-self, or detect that someone else has
    // installed a forwarding pointer.
    return _g1h->handle_evacuation_failure_par(this, old);
  }

  oop obj = oop(obj_ptr);

  // We're going to allocate linearly, so might as well prefetch ahead.
  Prefetch::write(obj_ptr, PrefetchCopyIntervalInBytes);

  oop forward_ptr = old->forward_to_atomic(obj);
  if (forward_ptr == NULL) {
    Copy::aligned_disjoint_words((HeapWord*) old, obj_ptr, word_sz);
    if (g1p->track_object_age(alloc_purpose)) {
      // We could simply do obj->incr_age(). However, this causes a
      // performance issue. obj->incr_age() will first check whether
      // the object has a displaced mark by checking its mark word;
      // getting the mark word from the new location of the object
      // stalls. So, given that we already have the mark word and we
      // are about to install it anyway, it's better to increase the
      // age on the mark word, when the object does not have a
      // displaced mark word. We're not expecting many objects to have
      // a displaced marked word, so that case is not optimized
      // further (it could be...) and we simply call obj->incr_age().

      if (m->has_displaced_mark_helper()) {
        // in this case, we have to install the mark word first,
        // otherwise obj looks to be forwarded (the old mark word,
        // which contains the forward pointer, was copied)
        obj->set_mark(m);
        obj->incr_age();
      } else {
        m = m->incr_age();
        obj->set_mark(m);
      }
      age_table()->add(obj, word_sz);
    } else {
      obj->set_mark(m);
    }

    size_t* surv_young_words = surviving_young_words();
    surv_young_words[young_index] += word_sz;

    if (obj->is_objArray() && arrayOop(obj)->length() >= ParGCArrayScanChunk) {
      // We keep track of the next start index in the length field of
      // the to-space object. The actual length can be found in the
      // length field of the from-space object.
      arrayOop(obj)->set_length(0);
      oop* old_p = set_partial_array_mask(old);
      push_on_queue(old_p);
    } else {
      // No point in using the slower heap_region_containing() method,
      // given that we know obj is in the heap.
      _scanner.set_region(_g1h->heap_region_containing_raw(obj));
      obj->oop_iterate_backwards(&_scanner);
    }
  } else {
    undo_allocation(alloc_purpose, obj_ptr, word_sz);
    obj = forward_ptr;
  }
  return obj;
}

template <class T>
void G1ParCopyHelper::do_klass_barrier(T* p, oop new_obj) {
  if (_g1->heap_region_containing_raw(new_obj)->is_young()) {
    _scanned_klass->record_modified_oops();
  }
}

template <G1Barrier barrier, bool do_mark_object>
template <class T>
void G1ParCopyClosure<barrier, do_mark_object>::do_oop_work(T* p) {
  T heap_oop = oopDesc::load_heap_oop(p);

  if (oopDesc::is_null(heap_oop)) {
    return;
  }

  oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);

  assert(_worker_id == _par_scan_state->queue_num(), "sanity");

  if (_g1->in_cset_fast_test(obj)) {
    oop forwardee;
    if (obj->is_forwarded()) {
      forwardee = obj->forwardee();
    } else {
      forwardee = _par_scan_state->copy_to_survivor_space(obj);
    }
    assert(forwardee != NULL, "forwardee should not be NULL");
    oopDesc::encode_store_heap_oop(p, forwardee);
    if (do_mark_object && forwardee != obj) {
      // If the object is self-forwarded we don't need to explicitly
      // mark it, the evacuation failure protocol will do so.
      mark_forwarded_object(obj, forwardee);
    }

    if (barrier == G1BarrierKlass) {
      do_klass_barrier(p, forwardee);
    }
  } else {
    // The object is not in collection set. If we're a root scanning
    // closure during an initial mark pause (i.e. do_mark_object will
    // be true) then attempt to mark the object.
    if (do_mark_object) {
      mark_object(obj);
    }
  }

  if (barrier == G1BarrierEvac) {
    _par_scan_state->update_rs(_from, p, _worker_id);
  }
}

template void G1ParCopyClosure<G1BarrierEvac, false>::do_oop_work(oop* p);
template void G1ParCopyClosure<G1BarrierEvac, false>::do_oop_work(narrowOop* p);

template <class T> void G1ParScanPartialArrayClosure::do_oop_nv(T* p) {
  assert(has_partial_array_mask(p), "invariant");
  oop from_obj = clear_partial_array_mask(p);

  assert(Universe::heap()->is_in_reserved(from_obj), "must be in heap.");
  assert(from_obj->is_objArray(), "must be obj array");
  objArrayOop from_obj_array = objArrayOop(from_obj);
  // The from-space object contains the real length.
  int length                 = from_obj_array->length();

  assert(from_obj->is_forwarded(), "must be forwarded");
  oop to_obj                 = from_obj->forwardee();
  assert(from_obj != to_obj, "should not be chunking self-forwarded objects");
  objArrayOop to_obj_array   = objArrayOop(to_obj);
  // We keep track of the next start index in the length field of the
  // to-space object.
  int next_index             = to_obj_array->length();
  assert(0 <= next_index && next_index < length,
         err_msg("invariant, next index: %d, length: %d", next_index, length));

  int start                  = next_index;
  int end                    = length;
  int remainder              = end - start;
  // We'll try not to push a range that's smaller than ParGCArrayScanChunk.
  if (remainder > 2 * ParGCArrayScanChunk) {
    end = start + ParGCArrayScanChunk;
    to_obj_array->set_length(end);
    // Push the remainder before we process the range in case another
    // worker has run out of things to do and can steal it.
    oop* from_obj_p = set_partial_array_mask(from_obj);
    _par_scan_state->push_on_queue(from_obj_p);
  } else {
    assert(length == end, "sanity");
    // We'll process the final range for this object. Restore the length
    // so that the heap remains parsable in case of evacuation failure.
    to_obj_array->set_length(end);
  }
  _scanner.set_region(_g1->heap_region_containing_raw(to_obj));
  // Process indexes [start,end). It will also process the header
  // along with the first chunk (i.e., the chunk with start == 0).
  // Note that at this point the length field of to_obj_array is not
  // correct given that we are using it to keep track of the next
  // start index. oop_iterate_range() (thankfully!) ignores the length
  // field and only relies on the start / end parameters.  It does
  // however return the size of the object which will be incorrect. So
  // we have to ignore it even if we wanted to use it.
  to_obj_array->oop_iterate_range(&_scanner, start, end);
}

class G1ParEvacuateFollowersClosure : public VoidClosure {
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
      _queues(queues), _terminator(terminator) {}

  void do_void();

private:
  inline bool offer_termination();
};

bool G1ParEvacuateFollowersClosure::offer_termination() {
  G1ParScanThreadState* const pss = par_scan_state();
  pss->start_term_time();
  const bool res = terminator()->offer_termination();
  pss->end_term_time();
  return res;
}

void G1ParEvacuateFollowersClosure::do_void() {
  StarTask stolen_task;
  G1ParScanThreadState* const pss = par_scan_state();
  pss->trim_queue();

  do {
    while (queues()->steal(pss->queue_num(), pss->hash_seed(), stolen_task)) {
      assert(pss->verify_task(stolen_task), "sanity");
      if (stolen_task.is_narrow()) {
        pss->deal_with_reference((narrowOop*) stolen_task);
      } else {
        pss->deal_with_reference((oop*) stolen_task);
      }

      // We've just processed a reference and we might have made
      // available new entries on the queues. So we have to make sure
      // we drain the queues as necessary.
      pss->trim_queue();
    }
  } while (!offer_termination());

  pss->retire_alloc_buffers();
}

class G1KlassScanClosure : public KlassClosure {
 G1ParCopyHelper* _closure;
 bool             _process_only_dirty;
 int              _count;
 public:
  G1KlassScanClosure(G1ParCopyHelper* closure, bool process_only_dirty)
      : _process_only_dirty(process_only_dirty), _closure(closure), _count(0) {}
  void do_klass(Klass* klass) {
    // If the klass has not been dirtied we know that there's
    // no references into  the young gen and we can skip it.
   if (!_process_only_dirty || klass->has_modified_oops()) {
      // Clean the klass since we're going to scavenge all the metadata.
      klass->clear_modified_oops();

      // Tell the closure that this klass is the Klass to scavenge
      // and is the one to dirty if oops are left pointing into the young gen.
      _closure->set_scanned_klass(klass);

      klass->oops_do(_closure);

      _closure->set_scanned_klass(NULL);
    }
    _count++;
  }
};

class G1ParTask : public AbstractGangTask {
protected:
  G1CollectedHeap*       _g1h;
  RefToScanQueueSet      *_queues;
  ParallelTaskTerminator _terminator;
  uint _n_workers;

  Mutex _stats_lock;
  Mutex* stats_lock() { return &_stats_lock; }

  size_t getNCards() {
    return (_g1h->capacity() + G1BlockOffsetSharedArray::N_bytes - 1)
      / G1BlockOffsetSharedArray::N_bytes;
  }

public:
  G1ParTask(G1CollectedHeap* g1h,
            RefToScanQueueSet *task_queues)
    : AbstractGangTask("G1 collection"),
      _g1h(g1h),
      _queues(task_queues),
      _terminator(0, _queues),
      _stats_lock(Mutex::leaf, "parallel G1 stats lock", true)
  {}

  RefToScanQueueSet* queues() { return _queues; }

  RefToScanQueue *work_queue(int i) {
    return queues()->queue(i);
  }

  ParallelTaskTerminator* terminator() { return &_terminator; }

  virtual void set_for_termination(int active_workers) {
    // This task calls set_n_termination() in par_non_clean_card_iterate_work()
    // in the young space (_par_seq_tasks) in the G1 heap
    // for SequentialSubTasksDone.
    // This task also uses SubTasksDone in SharedHeap and G1CollectedHeap
    // both of which need setting by set_n_termination().
    _g1h->SharedHeap::set_n_termination(active_workers);
    _g1h->set_n_termination(active_workers);
    terminator()->reset_for_reuse(active_workers);
    _n_workers = active_workers;
  }

  void work(uint worker_id) {
    if (worker_id >= _n_workers) return;  // no work needed this round

    double start_time_ms = os::elapsedTime() * 1000.0;
    _g1h->g1_policy()->phase_times()->record_gc_worker_start_time(worker_id, start_time_ms);

    {
      ResourceMark rm;
      HandleMark   hm;

      ReferenceProcessor*             rp = _g1h->ref_processor_stw();

      G1ParScanThreadState            pss(_g1h, worker_id, rp);
      G1ParScanHeapEvacClosure        scan_evac_cl(_g1h, &pss, rp);
      G1ParScanHeapEvacFailureClosure evac_failure_cl(_g1h, &pss, rp);
      G1ParScanPartialArrayClosure    partial_scan_cl(_g1h, &pss, rp);

      pss.set_evac_closure(&scan_evac_cl);
      pss.set_evac_failure_closure(&evac_failure_cl);
      pss.set_partial_scan_closure(&partial_scan_cl);

      G1ParScanExtRootClosure        only_scan_root_cl(_g1h, &pss, rp);
      G1ParScanMetadataClosure       only_scan_metadata_cl(_g1h, &pss, rp);

      G1ParScanAndMarkExtRootClosure scan_mark_root_cl(_g1h, &pss, rp);
      G1ParScanAndMarkMetadataClosure scan_mark_metadata_cl(_g1h, &pss, rp);

      bool only_young                 = _g1h->g1_policy()->gcs_are_young();
      G1KlassScanClosure              scan_mark_klasses_cl_s(&scan_mark_metadata_cl, false);
      G1KlassScanClosure              only_scan_klasses_cl_s(&only_scan_metadata_cl, only_young);

      OopClosure*                    scan_root_cl = &only_scan_root_cl;
      G1KlassScanClosure*            scan_klasses_cl = &only_scan_klasses_cl_s;

      if (_g1h->g1_policy()->during_initial_mark_pause()) {
        // We also need to mark copied objects.
        scan_root_cl = &scan_mark_root_cl;
        scan_klasses_cl = &scan_mark_klasses_cl_s;
      }

      G1ParPushHeapRSClosure          push_heap_rs_cl(_g1h, &pss);

      // Don't scan the scavengable methods in the code cache as part
      // of strong root scanning. The code roots that point into a
      // region in the collection set are scanned when we scan the
      // region's RSet.
      int so = SharedHeap::SO_AllClasses | SharedHeap::SO_Strings;

      pss.start_strong_roots();
      _g1h->g1_process_strong_roots(/* is scavenging */ true,
                                    SharedHeap::ScanningOption(so),
                                    scan_root_cl,
                                    &push_heap_rs_cl,
                                    scan_klasses_cl,
                                    worker_id);
      pss.end_strong_roots();

      {
        double start = os::elapsedTime();
        G1ParEvacuateFollowersClosure evac(_g1h, &pss, _queues, &_terminator);
        evac.do_void();
        double elapsed_ms = (os::elapsedTime()-start)*1000.0;
        double term_ms = pss.term_time()*1000.0;
        _g1h->g1_policy()->phase_times()->add_obj_copy_time(worker_id, elapsed_ms-term_ms);
        _g1h->g1_policy()->phase_times()->record_termination(worker_id, term_ms, pss.term_attempts());
      }
      _g1h->g1_policy()->record_thread_age_table(pss.age_table());
      _g1h->update_surviving_young_words(pss.surviving_young_words()+1);

      if (ParallelGCVerbose) {
        MutexLocker x(stats_lock());
        pss.print_termination_stats(worker_id);
      }

      assert(pss.refs()->is_empty(), "should be empty");

      // Close the inner scope so that the ResourceMark and HandleMark
      // destructors are executed here and are included as part of the
      // "GC Worker Time".
    }

    double end_time_ms = os::elapsedTime() * 1000.0;
    _g1h->g1_policy()->phase_times()->record_gc_worker_end_time(worker_id, end_time_ms);
  }
};

// *** Common G1 Evacuation Stuff

// This method is run in a GC worker.

void
G1CollectedHeap::
g1_process_strong_roots(bool is_scavenging,
                        ScanningOption so,
                        OopClosure* scan_non_heap_roots,
                        OopsInHeapRegionClosure* scan_rs,
                        G1KlassScanClosure* scan_klasses,
                        int worker_i) {

  // First scan the strong roots
  double ext_roots_start = os::elapsedTime();
  double closure_app_time_sec = 0.0;

  BufferingOopClosure buf_scan_non_heap_roots(scan_non_heap_roots);

  process_strong_roots(false, // no scoping; this is parallel code
                       so,
                       &buf_scan_non_heap_roots,
                       scan_klasses
                       );

  // Now the CM ref_processor roots.
  if (!_process_strong_tasks->is_task_claimed(G1H_PS_refProcessor_oops_do)) {
    // We need to treat the discovered reference lists of the
    // concurrent mark ref processor as roots and keep entries
    // (which are added by the marking threads) on them live
    // until they can be processed at the end of marking.
    ref_processor_cm()->weak_oops_do(&buf_scan_non_heap_roots);
  }

  // Finish up any enqueued closure apps (attributed as object copy time).
  buf_scan_non_heap_roots.done();

  double obj_copy_time_sec = buf_scan_non_heap_roots.closure_app_seconds();

  g1_policy()->phase_times()->record_obj_copy_time(worker_i, obj_copy_time_sec * 1000.0);

  double ext_root_time_ms =
    ((os::elapsedTime() - ext_roots_start) - obj_copy_time_sec) * 1000.0;

  g1_policy()->phase_times()->record_ext_root_scan_time(worker_i, ext_root_time_ms);

  // During conc marking we have to filter the per-thread SATB buffers
  // to make sure we remove any oops into the CSet (which will show up
  // as implicitly live).
  double satb_filtering_ms = 0.0;
  if (!_process_strong_tasks->is_task_claimed(G1H_PS_filter_satb_buffers)) {
    if (mark_in_progress()) {
      double satb_filter_start = os::elapsedTime();

      JavaThread::satb_mark_queue_set().filter_thread_buffers();

      satb_filtering_ms = (os::elapsedTime() - satb_filter_start) * 1000.0;
    }
  }
  g1_policy()->phase_times()->record_satb_filtering_time(worker_i, satb_filtering_ms);

  // If this is an initial mark pause, and we're not scanning
  // the entire code cache, we need to mark the oops in the
  // strong code root lists for the regions that are not in
  // the collection set.
  // Note all threads participate in this set of root tasks.
  double mark_strong_code_roots_ms = 0.0;
  if (g1_policy()->during_initial_mark_pause() && !(so & SO_AllCodeCache)) {
    double mark_strong_roots_start = os::elapsedTime();
    mark_strong_code_roots(worker_i);
    mark_strong_code_roots_ms = (os::elapsedTime() - mark_strong_roots_start) * 1000.0;
  }
  g1_policy()->phase_times()->record_strong_code_root_mark_time(worker_i, mark_strong_code_roots_ms);

  // Now scan the complement of the collection set.
  CodeBlobToOopClosure eager_scan_code_roots(scan_non_heap_roots, true /* do_marking */);
  g1_rem_set()->oops_into_collection_set_do(scan_rs, &eager_scan_code_roots, worker_i);

  _process_strong_tasks->all_tasks_completed();
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

  bool _do_in_parallel;
public:
  G1StringSymbolTableUnlinkTask(BoolObjectClosure* is_alive, bool process_strings, bool process_symbols) :
    AbstractGangTask("Par String/Symbol table unlink"), _is_alive(is_alive),
    _do_in_parallel(G1CollectedHeap::use_parallel_gc_threads()),
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
    guarantee(!_process_strings || !_do_in_parallel || StringTable::parallel_claimed_index() >= _initial_string_table_size,
              err_msg("claim value "INT32_FORMAT" after unlink less than initial string table size "INT32_FORMAT,
                      StringTable::parallel_claimed_index(), _initial_string_table_size));
    guarantee(!_process_symbols || !_do_in_parallel || SymbolTable::parallel_claimed_index() >= _initial_symbol_table_size,
              err_msg("claim value "INT32_FORMAT" after unlink less than initial symbol table size "INT32_FORMAT,
                      SymbolTable::parallel_claimed_index(), _initial_symbol_table_size));
  }

  void work(uint worker_id) {
    if (_do_in_parallel) {
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
    } else {
      if (_process_strings) {
        StringTable::unlink(_is_alive, &_strings_processed, &_strings_removed);
      }
      if (_process_symbols) {
        SymbolTable::unlink(&_symbols_processed, &_symbols_removed);
      }
    }
  }

  size_t strings_processed() const { return (size_t)_strings_processed; }
  size_t strings_removed()   const { return (size_t)_strings_removed; }

  size_t symbols_processed() const { return (size_t)_symbols_processed; }
  size_t symbols_removed()   const { return (size_t)_symbols_removed; }
};

void G1CollectedHeap::unlink_string_and_symbol_table(BoolObjectClosure* is_alive,
                                                     bool process_strings, bool process_symbols) {
  uint n_workers = (G1CollectedHeap::use_parallel_gc_threads() ?
                   _g1h->workers()->active_workers() : 1);

  G1StringSymbolTableUnlinkTask g1_unlink_task(is_alive, process_strings, process_symbols);
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    set_par_threads(n_workers);
    workers()->run_task(&g1_unlink_task);
    set_par_threads(0);
  } else {
    g1_unlink_task.work(0);
  }
  if (G1TraceStringSymbolTableScrubbing) {
    gclog_or_tty->print_cr("Cleaned string and symbol table, "
                           "strings: "SIZE_FORMAT" processed, "SIZE_FORMAT" removed, "
                           "symbols: "SIZE_FORMAT" processed, "SIZE_FORMAT" removed",
                           g1_unlink_task.strings_processed(), g1_unlink_task.strings_removed(),
                           g1_unlink_task.symbols_processed(), g1_unlink_task.symbols_removed());
  }
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
  return !_g1->obj_in_cs(p) || p->is_forwarded();
}

// Non Copying Keep Alive closure
class G1KeepAliveClosure: public OopClosure {
  G1CollectedHeap* _g1;
public:
  G1KeepAliveClosure(G1CollectedHeap* g1) : _g1(g1) {}
  void do_oop(narrowOop* p) { guarantee(false, "Not needed"); }
  void do_oop(      oop* p) {
    oop obj = *p;

    if (_g1->obj_in_cs(obj)) {
      assert( obj->is_forwarded(), "invariant" );
      *p = obj->forwardee();
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
  OopsInHeapRegionClosure* _copy_metadata_obj_cl;
  G1ParScanThreadState*    _par_scan_state;

public:
  G1CopyingKeepAliveClosure(G1CollectedHeap* g1h,
                            OopClosure* non_heap_obj_cl,
                            OopsInHeapRegionClosure* metadata_obj_cl,
                            G1ParScanThreadState* pss):
    _g1h(g1h),
    _copy_non_heap_obj_cl(non_heap_obj_cl),
    _copy_metadata_obj_cl(metadata_obj_cl),
    _par_scan_state(pss)
  {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }

  template <class T> void do_oop_work(T* p) {
    oop obj = oopDesc::load_decode_heap_oop(p);

    if (_g1h->obj_in_cs(obj)) {
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
        assert(!ClassLoaderDataGraph::contains((address)p),
               err_msg("Otherwise need to call _copy_metadata_obj_cl->do_oop(p) "
                              PTR_FORMAT, p));
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
  G1CollectedHeap*   _g1h;
  RefToScanQueueSet* _queues;
  FlexibleWorkGang*  _workers;
  int                _active_workers;

public:
  G1STWRefProcTaskExecutor(G1CollectedHeap* g1h,
                        FlexibleWorkGang* workers,
                        RefToScanQueueSet *task_queues,
                        int n_workers) :
    _g1h(g1h),
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
  RefToScanQueueSet *_task_queues;
  ParallelTaskTerminator* _terminator;

public:
  G1STWRefProcTaskProxy(ProcessTask& proc_task,
                     G1CollectedHeap* g1h,
                     RefToScanQueueSet *task_queues,
                     ParallelTaskTerminator* terminator) :
    AbstractGangTask("Process reference objects in parallel"),
    _proc_task(proc_task),
    _g1h(g1h),
    _task_queues(task_queues),
    _terminator(terminator)
  {}

  virtual void work(uint worker_id) {
    // The reference processing task executed by a single worker.
    ResourceMark rm;
    HandleMark   hm;

    G1STWIsAliveClosure is_alive(_g1h);

    G1ParScanThreadState            pss(_g1h, worker_id, NULL);

    G1ParScanHeapEvacClosure        scan_evac_cl(_g1h, &pss, NULL);
    G1ParScanHeapEvacFailureClosure evac_failure_cl(_g1h, &pss, NULL);
    G1ParScanPartialArrayClosure    partial_scan_cl(_g1h, &pss, NULL);

    pss.set_evac_closure(&scan_evac_cl);
    pss.set_evac_failure_closure(&evac_failure_cl);
    pss.set_partial_scan_closure(&partial_scan_cl);

    G1ParScanExtRootClosure        only_copy_non_heap_cl(_g1h, &pss, NULL);
    G1ParScanMetadataClosure       only_copy_metadata_cl(_g1h, &pss, NULL);

    G1ParScanAndMarkExtRootClosure copy_mark_non_heap_cl(_g1h, &pss, NULL);
    G1ParScanAndMarkMetadataClosure copy_mark_metadata_cl(_g1h, &pss, NULL);

    OopClosure*                    copy_non_heap_cl = &only_copy_non_heap_cl;
    OopsInHeapRegionClosure*       copy_metadata_cl = &only_copy_metadata_cl;

    if (_g1h->g1_policy()->during_initial_mark_pause()) {
      // We also need to mark copied objects.
      copy_non_heap_cl = &copy_mark_non_heap_cl;
      copy_metadata_cl = &copy_mark_metadata_cl;
    }

    // Keep alive closure.
    G1CopyingKeepAliveClosure keep_alive(_g1h, copy_non_heap_cl, copy_metadata_cl, &pss);

    // Complete GC closure
    G1ParEvacuateFollowersClosure drain_queue(_g1h, &pss, _task_queues, _terminator);

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
  G1STWRefProcTaskProxy proc_task_proxy(proc_task, _g1h, _queues, &terminator);

  _g1h->set_par_threads(_active_workers);
  _workers->run_task(&proc_task_proxy);
  _g1h->set_par_threads(0);
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

  _g1h->set_par_threads(_active_workers);
  _workers->run_task(&enq_task_proxy);
  _g1h->set_par_threads(0);
}

// End of weak reference support closures

// Abstract task used to preserve (i.e. copy) any referent objects
// that are in the collection set and are pointed to by reference
// objects discovered by the CM ref processor.

class G1ParPreserveCMReferentsTask: public AbstractGangTask {
protected:
  G1CollectedHeap* _g1h;
  RefToScanQueueSet      *_queues;
  ParallelTaskTerminator _terminator;
  uint _n_workers;

public:
  G1ParPreserveCMReferentsTask(G1CollectedHeap* g1h,int workers, RefToScanQueueSet *task_queues) :
    AbstractGangTask("ParPreserveCMReferents"),
    _g1h(g1h),
    _queues(task_queues),
    _terminator(workers, _queues),
    _n_workers(workers)
  { }

  void work(uint worker_id) {
    ResourceMark rm;
    HandleMark   hm;

    G1ParScanThreadState            pss(_g1h, worker_id, NULL);
    G1ParScanHeapEvacClosure        scan_evac_cl(_g1h, &pss, NULL);
    G1ParScanHeapEvacFailureClosure evac_failure_cl(_g1h, &pss, NULL);
    G1ParScanPartialArrayClosure    partial_scan_cl(_g1h, &pss, NULL);

    pss.set_evac_closure(&scan_evac_cl);
    pss.set_evac_failure_closure(&evac_failure_cl);
    pss.set_partial_scan_closure(&partial_scan_cl);

    assert(pss.refs()->is_empty(), "both queue and overflow should be empty");


    G1ParScanExtRootClosure        only_copy_non_heap_cl(_g1h, &pss, NULL);
    G1ParScanMetadataClosure       only_copy_metadata_cl(_g1h, &pss, NULL);

    G1ParScanAndMarkExtRootClosure copy_mark_non_heap_cl(_g1h, &pss, NULL);
    G1ParScanAndMarkMetadataClosure copy_mark_metadata_cl(_g1h, &pss, NULL);

    OopClosure*                    copy_non_heap_cl = &only_copy_non_heap_cl;
    OopsInHeapRegionClosure*       copy_metadata_cl = &only_copy_metadata_cl;

    if (_g1h->g1_policy()->during_initial_mark_pause()) {
      // We also need to mark copied objects.
      copy_non_heap_cl = &copy_mark_non_heap_cl;
      copy_metadata_cl = &copy_mark_metadata_cl;
    }

    // Is alive closure
    G1AlwaysAliveClosure always_alive(_g1h);

    // Copying keep alive closure. Applied to referent objects that need
    // to be copied.
    G1CopyingKeepAliveClosure keep_alive(_g1h, copy_non_heap_cl, copy_metadata_cl, &pss);

    ReferenceProcessor* rp = _g1h->ref_processor_cm();

    uint limit = ReferenceProcessor::number_of_subclasses_of_ref() * rp->max_num_q();
    uint stride = MIN2(MAX2(_n_workers, 1U), limit);

    // limit is set using max_num_q() - which was set using ParallelGCThreads.
    // So this must be true - but assert just in case someone decides to
    // change the worker ids.
    assert(0 <= worker_id && worker_id < limit, "sanity");
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
    G1ParEvacuateFollowersClosure drain_queue(_g1h, &pss, _queues, &_terminator);
    drain_queue.do_void();
    // Allocation buffers were retired at the end of G1ParEvacuateFollowersClosure
    assert(pss.refs()->is_empty(), "should be");
  }
};

// Weak Reference processing during an evacuation pause (part 1).
void G1CollectedHeap::process_discovered_references(uint no_of_gc_workers) {
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

  assert(!G1CollectedHeap::use_parallel_gc_threads() ||
           no_of_gc_workers == workers()->active_workers(),
           "Need to reset active GC workers");

  set_par_threads(no_of_gc_workers);
  G1ParPreserveCMReferentsTask keep_cm_referents(this,
                                                 no_of_gc_workers,
                                                 _task_queues);

  if (G1CollectedHeap::use_parallel_gc_threads()) {
    workers()->run_task(&keep_cm_referents);
  } else {
    keep_cm_referents.work(0);
  }

  set_par_threads(0);

  // Closure to test whether a referent is alive.
  G1STWIsAliveClosure is_alive(this);

  // Even when parallel reference processing is enabled, the processing
  // of JNI refs is serial and performed serially by the current thread
  // rather than by a worker. The following PSS will be used for processing
  // JNI refs.

  // Use only a single queue for this PSS.
  G1ParScanThreadState            pss(this, 0, NULL);

  // We do not embed a reference processor in the copying/scanning
  // closures while we're actually processing the discovered
  // reference objects.
  G1ParScanHeapEvacClosure        scan_evac_cl(this, &pss, NULL);
  G1ParScanHeapEvacFailureClosure evac_failure_cl(this, &pss, NULL);
  G1ParScanPartialArrayClosure    partial_scan_cl(this, &pss, NULL);

  pss.set_evac_closure(&scan_evac_cl);
  pss.set_evac_failure_closure(&evac_failure_cl);
  pss.set_partial_scan_closure(&partial_scan_cl);

  assert(pss.refs()->is_empty(), "pre-condition");

  G1ParScanExtRootClosure        only_copy_non_heap_cl(this, &pss, NULL);
  G1ParScanMetadataClosure       only_copy_metadata_cl(this, &pss, NULL);

  G1ParScanAndMarkExtRootClosure copy_mark_non_heap_cl(this, &pss, NULL);
  G1ParScanAndMarkMetadataClosure copy_mark_metadata_cl(this, &pss, NULL);

  OopClosure*                    copy_non_heap_cl = &only_copy_non_heap_cl;
  OopsInHeapRegionClosure*       copy_metadata_cl = &only_copy_metadata_cl;

  if (_g1h->g1_policy()->during_initial_mark_pause()) {
    // We also need to mark copied objects.
    copy_non_heap_cl = &copy_mark_non_heap_cl;
    copy_metadata_cl = &copy_mark_metadata_cl;
  }

  // Keep alive closure.
  G1CopyingKeepAliveClosure keep_alive(this, copy_non_heap_cl, copy_metadata_cl, &pss);

  // Serial Complete GC closure
  G1STWDrainQueueClosure drain_queue(this, &pss);

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

    G1STWRefProcTaskExecutor par_task_executor(this, workers(), _task_queues, no_of_gc_workers);
    stats = rp->process_discovered_references(&is_alive,
                                              &keep_alive,
                                              &drain_queue,
                                              &par_task_executor,
                                              _gc_timer_stw);
  }

  _gc_tracer_stw->report_gc_reference_stats(stats);
  // We have completed copying any necessary live referent objects
  // (that were not copied during the actual pause) so we can
  // retire any active alloc buffers
  pss.retire_alloc_buffers();
  assert(pss.refs()->is_empty(), "both queue and overflow should be empty");

  double ref_proc_time = os::elapsedTime() - ref_proc_start;
  g1_policy()->phase_times()->record_ref_proc_time(ref_proc_time * 1000.0);
}

// Weak Reference processing during an evacuation pause (part 2).
void G1CollectedHeap::enqueue_discovered_references(uint no_of_gc_workers) {
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

    assert(no_of_gc_workers == workers()->active_workers(),
           "Need to reset active workers");
    assert(rp->num_q() == no_of_gc_workers, "sanity");
    assert(no_of_gc_workers <= rp->max_num_q(), "sanity");

    G1STWRefProcTaskExecutor par_task_executor(this, workers(), _task_queues, no_of_gc_workers);
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

void G1CollectedHeap::evacuate_collection_set(EvacuationInfo& evacuation_info) {
  _expand_heap_after_alloc_failure = true;
  _evacuation_failed = false;

  // Should G1EvacuationFailureALot be in effect for this GC?
  NOT_PRODUCT(set_evacuation_failure_alot_for_current_gc();)

  g1_rem_set()->prepare_for_oops_into_collection_set_do();

  // Disable the hot card cache.
  G1HotCardCache* hot_card_cache = _cg1r->hot_card_cache();
  hot_card_cache->reset_hot_cache_claimed_index();
  hot_card_cache->set_use_cache(false);

  uint n_workers;
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    n_workers =
      AdaptiveSizePolicy::calc_active_workers(workers()->total_workers(),
                                     workers()->active_workers(),
                                     Threads::number_of_non_daemon_threads());
    assert(UseDynamicNumberOfGCThreads ||
           n_workers == workers()->total_workers(),
           "If not dynamic should be using all the  workers");
    workers()->set_active_workers(n_workers);
    set_par_threads(n_workers);
  } else {
    assert(n_par_threads() == 0,
           "Should be the original non-parallel value");
    n_workers = 1;
  }

  G1ParTask g1_par_task(this, _task_queues);

  init_for_evac_failure(NULL);

  rem_set()->prepare_for_younger_refs_iterate(true);

  assert(dirty_card_queue_set().completed_buffers_num() == 0, "Should be empty");
  double start_par_time_sec = os::elapsedTime();
  double end_par_time_sec;

  {
    StrongRootsScope srs(this);

    if (G1CollectedHeap::use_parallel_gc_threads()) {
      // The individual threads will set their evac-failure closures.
      if (ParallelGCVerbose) G1ParScanThreadState::print_termination_stats_hdr();
      // These tasks use ShareHeap::_process_strong_tasks
      assert(UseDynamicNumberOfGCThreads ||
             workers()->active_workers() == workers()->total_workers(),
             "If not dynamic should be using all the  workers");
      workers()->run_task(&g1_par_task);
    } else {
      g1_par_task.set_for_termination(n_workers);
      g1_par_task.work(0);
    }
    end_par_time_sec = os::elapsedTime();

    // Closing the inner scope will execute the destructor
    // for the StrongRootsScope object. We record the current
    // elapsed time before closing the scope so that time
    // taken for the SRS destructor is NOT included in the
    // reported parallel time.
  }

  double par_time_ms = (end_par_time_sec - start_par_time_sec) * 1000.0;
  g1_policy()->phase_times()->record_par_time(par_time_ms);

  double code_root_fixup_time_ms =
        (os::elapsedTime() - end_par_time_sec) * 1000.0;
  g1_policy()->phase_times()->record_code_root_fixup_time(code_root_fixup_time_ms);

  set_par_threads(0);

  // Process any discovered reference objects - we have
  // to do this _before_ we retire the GC alloc regions
  // as we may have to copy some 'reachable' referent
  // objects (and their reachable sub-graphs) that were
  // not copied during the pause.
  process_discovered_references(n_workers);

  // Weak root processing.
  {
    G1STWIsAliveClosure is_alive(this);
    G1KeepAliveClosure keep_alive(this);
    JNIHandles::weak_oops_do(&is_alive, &keep_alive);
  }

  release_gc_alloc_regions(n_workers, evacuation_info);
  g1_rem_set()->cleanup_after_oops_into_collection_set_do();

  // Reset and re-enable the hot card cache.
  // Note the counts for the cards in the regions in the
  // collection set are reset when the collection set is freed.
  hot_card_cache->reset_hot_cache();
  hot_card_cache->set_use_cache(true);

  // Migrate the strong code roots attached to each region in
  // the collection set. Ideally we would like to do this
  // after we have finished the scanning/evacuation of the
  // strong code roots for a particular heap region.
  migrate_strong_code_roots();

  if (g1_policy()->during_initial_mark_pause()) {
    // Reset the claim values set during marking the strong code roots
    reset_heap_region_claim_values();
  }

  finalize_for_evac_failure();

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
  enqueue_discovered_references(n_workers);

  if (G1DeferredRSUpdate) {
    RedirtyLoggedCardTableEntryFastClosure redirty;
    dirty_card_queue_set().set_closure(&redirty);
    dirty_card_queue_set().apply_closure_to_all_completed_buffers();

    DirtyCardQueueSet& dcq = JavaThread::dirty_card_queue_set();
    dcq.merge_bufferlists(&dirty_card_queue_set());
    assert(dirty_card_queue_set().completed_buffers_num() == 0, "All should be consumed");
  }
  COMPILER2_PRESENT(DerivedPointerTable::update_pointers());
}

void G1CollectedHeap::free_region_if_empty(HeapRegion* hr,
                                     size_t* pre_used,
                                     FreeRegionList* free_list,
                                     OldRegionSet* old_proxy_set,
                                     HumongousRegionSet* humongous_proxy_set,
                                     HRRSCleanupTask* hrrs_cleanup_task,
                                     bool par) {
  if (hr->used() > 0 && hr->max_live_bytes() == 0 && !hr->is_young()) {
    if (hr->isHumongous()) {
      assert(hr->startsHumongous(), "we should only see starts humongous");
      free_humongous_region(hr, pre_used, free_list, humongous_proxy_set, par);
    } else {
      _old_set.remove_with_proxy(hr, old_proxy_set);
      free_region(hr, pre_used, free_list, par);
    }
  } else {
    hr->rem_set()->do_cleanup_work(hrrs_cleanup_task);
  }
}

void G1CollectedHeap::free_region(HeapRegion* hr,
                                  size_t* pre_used,
                                  FreeRegionList* free_list,
                                  bool par) {
  assert(!hr->isHumongous(), "this is only for non-humongous regions");
  assert(!hr->is_empty(), "the region should not be empty");
  assert(free_list != NULL, "pre-condition");

  // Clear the card counts for this region.
  // Note: we only need to do this if the region is not young
  // (since we don't refine cards in young regions).
  if (!hr->is_young()) {
    _cg1r->hot_card_cache()->reset_card_counts(hr);
  }
  *pre_used += hr->used();
  hr->hr_clear(par, true /* clear_space */);
  free_list->add_as_head(hr);
}

void G1CollectedHeap::free_humongous_region(HeapRegion* hr,
                                     size_t* pre_used,
                                     FreeRegionList* free_list,
                                     HumongousRegionSet* humongous_proxy_set,
                                     bool par) {
  assert(hr->startsHumongous(), "this is only for starts humongous regions");
  assert(free_list != NULL, "pre-condition");
  assert(humongous_proxy_set != NULL, "pre-condition");

  size_t hr_used = hr->used();
  size_t hr_capacity = hr->capacity();
  size_t hr_pre_used = 0;
  _humongous_set.remove_with_proxy(hr, humongous_proxy_set);
  // We need to read this before we make the region non-humongous,
  // otherwise the information will be gone.
  uint last_index = hr->last_hc_index();
  hr->set_notHumongous();
  free_region(hr, &hr_pre_used, free_list, par);

  uint i = hr->hrs_index() + 1;
  while (i < last_index) {
    HeapRegion* curr_hr = region_at(i);
    assert(curr_hr->continuesHumongous(), "invariant");
    curr_hr->set_notHumongous();
    free_region(curr_hr, &hr_pre_used, free_list, par);
    i += 1;
  }
  assert(hr_pre_used == hr_used,
         err_msg("hr_pre_used: "SIZE_FORMAT" and hr_used: "SIZE_FORMAT" "
                 "should be the same", hr_pre_used, hr_used));
  *pre_used += hr_pre_used;
}

void G1CollectedHeap::update_sets_after_freeing_regions(size_t pre_used,
                                       FreeRegionList* free_list,
                                       OldRegionSet* old_proxy_set,
                                       HumongousRegionSet* humongous_proxy_set,
                                       bool par) {
  if (pre_used > 0) {
    Mutex* lock = (par) ? ParGCRareEvent_lock : NULL;
    MutexLockerEx x(lock, Mutex::_no_safepoint_check_flag);
    assert(_summary_bytes_used >= pre_used,
           err_msg("invariant: _summary_bytes_used: "SIZE_FORMAT" "
                   "should be >= pre_used: "SIZE_FORMAT,
                   _summary_bytes_used, pre_used));
    _summary_bytes_used -= pre_used;
  }
  if (free_list != NULL && !free_list->is_empty()) {
    MutexLockerEx x(FreeList_lock, Mutex::_no_safepoint_check_flag);
    _free_list.add_as_head(free_list);
  }
  if (old_proxy_set != NULL && !old_proxy_set->is_empty()) {
    MutexLockerEx x(OldSets_lock, Mutex::_no_safepoint_check_flag);
    _old_set.update_from_proxy(old_proxy_set);
  }
  if (humongous_proxy_set != NULL && !humongous_proxy_set->is_empty()) {
    MutexLockerEx x(OldSets_lock, Mutex::_no_safepoint_check_flag);
    _humongous_set.update_from_proxy(humongous_proxy_set);
  }
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
#endif

void G1CollectedHeap::cleanUpCardTable() {
  G1SATBCardTableModRefBS* ct_bs = g1_barrier_set();
  double start = os::elapsedTime();

  {
    // Iterate over the dirty cards region list.
    G1ParCleanupCTTask cleanup_task(ct_bs, this);

    if (G1CollectedHeap::use_parallel_gc_threads()) {
      set_par_threads();
      workers()->run_task(&cleanup_task);
      set_par_threads(0);
    } else {
      while (_dirty_cards_region_list) {
        HeapRegion* r = _dirty_cards_region_list;
        cleanup_task.clear_cards(r);
        _dirty_cards_region_list = r->get_next_dirty_cards_region();
        if (_dirty_cards_region_list == r) {
          // The last region.
          _dirty_cards_region_list = NULL;
        }
        r->set_next_dirty_cards_region(NULL);
      }
    }
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

void G1CollectedHeap::free_collection_set(HeapRegion* cs_head, EvacuationInfo& evacuation_info) {
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

    rs_lengths += cur->rem_set()->occupied();

    HeapRegion* next = cur->next_in_collection_set();
    assert(cur->in_collection_set(), "bad CS");
    cur->set_next_in_collection_set(NULL);
    cur->set_in_collection_set(false);

    if (cur->is_young()) {
      int index = cur->young_index_in_cset();
      assert(index != -1, "invariant");
      assert((uint) index < policy->young_cset_region_length(), "invariant");
      size_t words_survived = _surviving_young_words[index];
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
      free_region(cur, &pre_used, &local_free_list, false /* par */);
    } else {
      cur->uninstall_surv_rate_group();
      if (cur->is_young()) {
        cur->set_young_index_in_cset(-1);
      }
      cur->set_not_young();
      cur->set_evacuation_failed(false);
      // The region is now considered to be old.
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

  update_sets_after_freeing_regions(pre_used, &local_free_list,
                                    NULL /* old_proxy_set */,
                                    NULL /* humongous_proxy_set */,
                                    false /* par */);
  policy->phase_times()->record_young_free_cset_time_ms(young_time_ms);
  policy->phase_times()->record_non_young_free_cset_time_ms(non_young_time_ms);
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
    cur->set_in_collection_set(false);
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

void G1CollectedHeap::set_region_short_lived_locked(HeapRegion* hr) {
  assert(heap_lock_held_for_gc(),
              "the heap lock should already be held by or for this thread");
  _young_list->push_region(hr);
}

class NoYoungRegionsClosure: public HeapRegionClosure {
private:
  bool _success;
public:
  NoYoungRegionsClosure() : _success(true) { }
  bool doHeapRegion(HeapRegion* r) {
    if (r->is_young()) {
      gclog_or_tty->print_cr("Region ["PTR_FORMAT", "PTR_FORMAT") tagged as young",
                             r->bottom(), r->end());
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
  OldRegionSet *_old_set;

public:
  TearDownRegionSetsClosure(OldRegionSet* old_set) : _old_set(old_set) { }

  bool doHeapRegion(HeapRegion* r) {
    if (r->is_empty()) {
      // We ignore empty regions, we'll empty the free list afterwards
    } else if (r->is_young()) {
      // We ignore young regions, we'll empty the young list afterwards
    } else if (r->isHumongous()) {
      // We ignore humongous regions, we're not tearing down the
      // humongous region set
    } else {
      // The rest should be old
      _old_set->remove(r);
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

    // Need to do this after the heap iteration to be able to
    // recognize the young regions and ignore them during the iteration.
    _young_list->empty_list();
  }
  _free_list.remove_all();
}

class RebuildRegionSetsClosure : public HeapRegionClosure {
private:
  bool            _free_list_only;
  OldRegionSet*   _old_set;
  FreeRegionList* _free_list;
  size_t          _total_used;

public:
  RebuildRegionSetsClosure(bool free_list_only,
                           OldRegionSet* old_set, FreeRegionList* free_list) :
    _free_list_only(free_list_only),
    _old_set(old_set), _free_list(free_list), _total_used(0) {
    assert(_free_list->is_empty(), "pre-condition");
    if (!free_list_only) {
      assert(_old_set->is_empty(), "pre-condition");
    }
  }

  bool doHeapRegion(HeapRegion* r) {
    if (r->continuesHumongous()) {
      return false;
    }

    if (r->is_empty()) {
      // Add free regions to the free list
      _free_list->add_as_tail(r);
    } else if (!_free_list_only) {
      assert(!r->is_young(), "we should not come across young regions");

      if (r->isHumongous()) {
        // We ignore humongous regions, we left the humongous set unchanged
      } else {
        // The rest should be old, add them to the old set
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

  RebuildRegionSetsClosure cl(free_list_only, &_old_set, &_free_list);
  heap_region_iterate(&cl);

  if (!free_list_only) {
    _summary_bytes_used = cl.total_used();
  }
  assert(_summary_bytes_used == recalculate_used(),
         err_msg("inconsistent _summary_bytes_used, "
                 "value: "SIZE_FORMAT" recalculated: "SIZE_FORMAT,
                 _summary_bytes_used, recalculate_used()));
}

void G1CollectedHeap::set_refine_cte_cl_concurrency(bool concurrent) {
  _refine_cte_cl->set_concurrent(concurrent);
}

bool G1CollectedHeap::is_in_closed_subset(const void* p) const {
  HeapRegion* hr = heap_region_containing(p);
  if (hr == NULL) {
    return false;
  } else {
    return hr->is_in(p);
  }
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
                                              false /* do_expand */);
    if (new_alloc_region != NULL) {
      set_region_short_lived_locked(new_alloc_region);
      _hr_printer.alloc(new_alloc_region, G1HRPrinter::Eden, young_list_full);
      return new_alloc_region;
    }
  }
  return NULL;
}

void G1CollectedHeap::retire_mutator_alloc_region(HeapRegion* alloc_region,
                                                  size_t allocated_bytes) {
  assert_heap_locked_or_at_safepoint(true /* should_be_vm_thread */);
  assert(alloc_region->is_young(), "all mutator alloc regions should be young");

  g1_policy()->add_region_to_incremental_cset_lhs(alloc_region);
  _summary_bytes_used += allocated_bytes;
  _hr_printer.retire(alloc_region);
  // We update the eden sizes here, when the region is retired,
  // instead of when it's allocated, since this is the point that its
  // used space has been recored in _summary_bytes_used.
  g1mm()->update_eden_size();
}

HeapRegion* MutatorAllocRegion::allocate_new_region(size_t word_size,
                                                    bool force) {
  return _g1h->new_mutator_alloc_region(word_size, force);
}

void G1CollectedHeap::set_par_threads() {
  // Don't change the number of workers.  Use the value previously set
  // in the workgroup.
  assert(G1CollectedHeap::use_parallel_gc_threads(), "shouldn't be here otherwise");
  uint n_workers = workers()->active_workers();
  assert(UseDynamicNumberOfGCThreads ||
           n_workers == workers()->total_workers(),
      "Otherwise should be using the total number of workers");
  if (n_workers == 0) {
    assert(false, "Should have been set in prior evacuation pause.");
    n_workers = ParallelGCThreads;
    workers()->set_active_workers(n_workers);
  }
  set_par_threads(n_workers);
}

void MutatorAllocRegion::retire_region(HeapRegion* alloc_region,
                                       size_t allocated_bytes) {
  _g1h->retire_mutator_alloc_region(alloc_region, allocated_bytes);
}

// Methods for the GC alloc regions

HeapRegion* G1CollectedHeap::new_gc_alloc_region(size_t word_size,
                                                 uint count,
                                                 GCAllocPurpose ap) {
  assert(FreeList_lock->owned_by_self(), "pre-condition");

  if (count < g1_policy()->max_regions(ap)) {
    HeapRegion* new_alloc_region = new_region(word_size,
                                              true /* do_expand */);
    if (new_alloc_region != NULL) {
      // We really only need to do this for old regions given that we
      // should never scan survivors. But it doesn't hurt to do it
      // for survivors too.
      new_alloc_region->set_saved_mark();
      if (ap == GCAllocForSurvived) {
        new_alloc_region->set_survivor();
        _hr_printer.alloc(new_alloc_region, G1HRPrinter::Survivor);
      } else {
        _hr_printer.alloc(new_alloc_region, G1HRPrinter::Old);
      }
      bool during_im = g1_policy()->during_initial_mark_pause();
      new_alloc_region->note_start_of_copying(during_im);
      return new_alloc_region;
    } else {
      g1_policy()->note_alloc_region_limit_reached(ap);
    }
  }
  return NULL;
}

void G1CollectedHeap::retire_gc_alloc_region(HeapRegion* alloc_region,
                                             size_t allocated_bytes,
                                             GCAllocPurpose ap) {
  bool during_im = g1_policy()->during_initial_mark_pause();
  alloc_region->note_end_of_copying(during_im);
  g1_policy()->record_bytes_copied_during_gc(allocated_bytes);
  if (ap == GCAllocForSurvived) {
    young_list()->add_survivor_region(alloc_region);
  } else {
    _old_set.add(alloc_region);
  }
  _hr_printer.retire(alloc_region);
}

HeapRegion* SurvivorGCAllocRegion::allocate_new_region(size_t word_size,
                                                       bool force) {
  assert(!force, "not supported for GC alloc regions");
  return _g1h->new_gc_alloc_region(word_size, count(), GCAllocForSurvived);
}

void SurvivorGCAllocRegion::retire_region(HeapRegion* alloc_region,
                                          size_t allocated_bytes) {
  _g1h->retire_gc_alloc_region(alloc_region, allocated_bytes,
                               GCAllocForSurvived);
}

HeapRegion* OldGCAllocRegion::allocate_new_region(size_t word_size,
                                                  bool force) {
  assert(!force, "not supported for GC alloc regions");
  return _g1h->new_gc_alloc_region(word_size, count(), GCAllocForTenured);
}

void OldGCAllocRegion::retire_region(HeapRegion* alloc_region,
                                     size_t allocated_bytes) {
  _g1h->retire_gc_alloc_region(alloc_region, allocated_bytes,
                               GCAllocForTenured);
}
// Heap region set verification

class VerifyRegionListsClosure : public HeapRegionClosure {
private:
  FreeRegionList*     _free_list;
  OldRegionSet*       _old_set;
  HumongousRegionSet* _humongous_set;
  uint                _region_count;

public:
  VerifyRegionListsClosure(OldRegionSet* old_set,
                           HumongousRegionSet* humongous_set,
                           FreeRegionList* free_list) :
    _old_set(old_set), _humongous_set(humongous_set),
    _free_list(free_list), _region_count(0) { }

  uint region_count() { return _region_count; }

  bool doHeapRegion(HeapRegion* hr) {
    _region_count += 1;

    if (hr->continuesHumongous()) {
      return false;
    }

    if (hr->is_young()) {
      // TODO
    } else if (hr->startsHumongous()) {
      _humongous_set->verify_next_region(hr);
    } else if (hr->is_empty()) {
      _free_list->verify_next_region(hr);
    } else {
      _old_set->verify_next_region(hr);
    }
    return false;
  }
};

HeapRegion* G1CollectedHeap::new_heap_region(uint hrs_index,
                                             HeapWord* bottom) {
  HeapWord* end = bottom + HeapRegion::GrainWords;
  MemRegion mr(bottom, end);
  assert(_g1_reserved.contains(mr), "invariant");
  // This might return NULL if the allocation fails
  return new HeapRegion(hrs_index, _bot_shared, mr);
}

void G1CollectedHeap::verify_region_sets() {
  assert_heap_locked_or_at_safepoint(true /* should_be_vm_thread */);

  // First, check the explicit lists.
  _free_list.verify();
  {
    // Given that a concurrent operation might be adding regions to
    // the secondary free list we have to take the lock before
    // verifying it.
    MutexLockerEx x(SecondaryFreeList_lock, Mutex::_no_safepoint_check_flag);
    _secondary_free_list.verify();
  }
  _old_set.verify();
  _humongous_set.verify();

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
  _old_set.verify_start();
  _humongous_set.verify_start();
  _free_list.verify_start();

  VerifyRegionListsClosure cl(&_old_set, &_humongous_set, &_free_list);
  heap_region_iterate(&cl);

  _old_set.verify_end();
  _humongous_set.verify_end();
  _free_list.verify_end();
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
      assert(!hr->continuesHumongous(),
             err_msg("trying to add code root "PTR_FORMAT" in continuation of humongous region "HR_FORMAT
                     " starting at "HR_FORMAT,
                     _nm, HR_FORMAT_PARAMS(hr), HR_FORMAT_PARAMS(hr->humongous_start_region())));

      // HeapRegion::add_strong_code_root() avoids adding duplicate
      // entries but having duplicates is  OK since we "mark" nmethods
      // as visited when we scan the strong code root lists during the GC.
      hr->add_strong_code_root(_nm);
      assert(hr->rem_set()->strong_code_roots_list_contains(_nm),
             err_msg("failed to add code root "PTR_FORMAT" to remembered set of region "HR_FORMAT,
                     _nm, HR_FORMAT_PARAMS(hr)));
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
      assert(!hr->continuesHumongous(),
             err_msg("trying to remove code root "PTR_FORMAT" in continuation of humongous region "HR_FORMAT
                     " starting at "HR_FORMAT,
                     _nm, HR_FORMAT_PARAMS(hr), HR_FORMAT_PARAMS(hr->humongous_start_region())));

      hr->remove_strong_code_root(_nm);
      assert(!hr->rem_set()->strong_code_roots_list_contains(_nm),
             err_msg("failed to remove code root "PTR_FORMAT" of region "HR_FORMAT,
                     _nm, HR_FORMAT_PARAMS(hr)));
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

class MigrateCodeRootsHeapRegionClosure: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion *hr) {
    assert(!hr->isHumongous(),
           err_msg("humongous region "HR_FORMAT" should not have been added to collection set",
                   HR_FORMAT_PARAMS(hr)));
    hr->migrate_strong_code_roots();
    return false;
  }
};

void G1CollectedHeap::migrate_strong_code_roots() {
  MigrateCodeRootsHeapRegionClosure cl;
  double migrate_start = os::elapsedTime();
  collection_set_iterate(&cl);
  double migration_time_ms = (os::elapsedTime() - migrate_start) * 1000.0;
  g1_policy()->phase_times()->record_strong_code_root_migration_time(migration_time_ms);
}

// Mark all the code roots that point into regions *not* in the
// collection set.
//
// Note we do not want to use a "marking" CodeBlobToOopClosure while
// walking the the code roots lists of regions not in the collection
// set. Suppose we have an nmethod (M) that points to objects in two
// separate regions - one in the collection set (R1) and one not (R2).
// Using a "marking" CodeBlobToOopClosure here would result in "marking"
// nmethod M when walking the code roots for R1. When we come to scan
// the code roots for R2, we would see that M is already marked and it
// would be skipped and the objects in R2 that are referenced from M
// would not be evacuated.

class MarkStrongCodeRootCodeBlobClosure: public CodeBlobClosure {

  class MarkStrongCodeRootOopClosure: public OopClosure {
    ConcurrentMark* _cm;
    HeapRegion* _hr;
    uint _worker_id;

    template <class T> void do_oop_work(T* p) {
      T heap_oop = oopDesc::load_heap_oop(p);
      if (!oopDesc::is_null(heap_oop)) {
        oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
        // Only mark objects in the region (which is assumed
        // to be not in the collection set).
        if (_hr->is_in(obj)) {
          _cm->grayRoot(obj, (size_t) obj->size(), _worker_id);
        }
      }
    }

  public:
    MarkStrongCodeRootOopClosure(ConcurrentMark* cm, HeapRegion* hr, uint worker_id) :
      _cm(cm), _hr(hr), _worker_id(worker_id) {
      assert(!_hr->in_collection_set(), "sanity");
    }

    void do_oop(narrowOop* p) { do_oop_work(p); }
    void do_oop(oop* p)       { do_oop_work(p); }
  };

  MarkStrongCodeRootOopClosure _oop_cl;

public:
  MarkStrongCodeRootCodeBlobClosure(ConcurrentMark* cm, HeapRegion* hr, uint worker_id):
    _oop_cl(cm, hr, worker_id) {}

  void do_code_blob(CodeBlob* cb) {
    nmethod* nm = (cb == NULL) ? NULL : cb->as_nmethod_or_null();
    if (nm != NULL) {
      nm->oops_do(&_oop_cl);
    }
  }
};

class MarkStrongCodeRootsHRClosure: public HeapRegionClosure {
  G1CollectedHeap* _g1h;
  uint _worker_id;

public:
  MarkStrongCodeRootsHRClosure(G1CollectedHeap* g1h, uint worker_id) :
    _g1h(g1h), _worker_id(worker_id) {}

  bool doHeapRegion(HeapRegion *hr) {
    HeapRegionRemSet* hrrs = hr->rem_set();
    if (hr->continuesHumongous()) {
      // Code roots should never be attached to a continuation of a humongous region
      assert(hrrs->strong_code_roots_list_length() == 0,
             err_msg("code roots should never be attached to continuations of humongous region "HR_FORMAT
                     " starting at "HR_FORMAT", but has "INT32_FORMAT,
                     HR_FORMAT_PARAMS(hr), HR_FORMAT_PARAMS(hr->humongous_start_region()),
                     hrrs->strong_code_roots_list_length()));
      return false;
    }

    if (hr->in_collection_set()) {
      // Don't mark code roots into regions in the collection set here.
      // They will be marked when we scan them.
      return false;
    }

    MarkStrongCodeRootCodeBlobClosure cb_cl(_g1h->concurrent_mark(), hr, _worker_id);
    hr->strong_code_roots_do(&cb_cl);
    return false;
  }
};

void G1CollectedHeap::mark_strong_code_roots(uint worker_id) {
  MarkStrongCodeRootsHRClosure cl(this, worker_id);
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    heap_region_par_iterate_chunked(&cl,
                                    worker_id,
                                    workers()->active_workers(),
                                    HeapRegion::ParMarkRootClaimValue);
  } else {
    heap_region_iterate(&cl);
  }
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

    if (ScavengeRootsInCode && nm->detect_scavenge_root_oops()) {
      _g1h->register_nmethod(nm);
    }
  }
};

void G1CollectedHeap::rebuild_strong_code_roots() {
  RebuildStrongCodeRootClosure blob_cl(this);
  CodeCache::blobs_do(&blob_cl);
}

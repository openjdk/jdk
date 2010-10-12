/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_g1CollectedHeap.cpp.incl"

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
//   serialized by acquiring the HeapLock.  This happens in
//   mem_allocate_work, which all such allocation functions call.
//   (Note that this does not apply to TLAB allocation, which is not part
//   of this interface: it is done by clients of this interface.)

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
    bool oops_into_cset = _g1rs->concurrentRefineOneCard(card_ptr, worker_i, false);
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
    _calls(0)
  {
    _g1h = G1CollectedHeap::heap();
    _ctbs = (CardTableModRefBS*)_g1h->barrier_set();
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
    _calls(0)
  {
    _g1h = G1CollectedHeap::heap();
    _ctbs = (CardTableModRefBS*)_g1h->barrier_set();
  }
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

YoungList::YoungList(G1CollectedHeap* g1h)
  : _g1h(g1h), _head(NULL),
    _length(0),
    _last_sampled_rs_lengths(0),
    _survivor_head(NULL), _survivor_tail(NULL), _survivor_length(0)
{
  guarantee( check_list_empty(false), "just making sure..." );
}

void YoungList::push_region(HeapRegion *hr) {
  assert(!hr->is_young(), "should not already be young");
  assert(hr->get_next_young_region() == NULL, "cause it should!");

  hr->set_next_young_region(_head);
  _head = hr;

  hr->set_young();
  double yg_surv_rate = _g1h->g1_policy()->predict_yg_surv_rate((int)_length);
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

  size_t length = 0;
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
    gclog_or_tty->print_cr("###   list has %d entries, _length is %d",
                           length, _length);
  }

  return ret;
}

bool YoungList::check_list_empty(bool check_sample) {
  bool ret = true;

  if (_length != 0) {
    gclog_or_tty->print_cr("### YOUNG LIST should have 0 length, not %d",
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

  for (HeapRegion* curr = _survivor_head;
       curr != NULL;
       curr = curr->get_next_young_region()) {
    _g1h->g1_policy()->set_region_survivors(curr);

    // The region is a non-empty survivor so let's add it to
    // the incremental collection set for the next evacuation
    // pause.
    _g1h->g1_policy()->add_region_to_incremental_cset_rhs(curr);
  }
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
      gclog_or_tty->print_cr("  [%08x-%08x], t: %08x, P: %08x, N: %08x, C: %08x, "
                             "age: %4d, y: %d, surv: %d",
                             curr->bottom(), curr->end(),
                             curr->top(),
                             curr->prev_top_at_mark_start(),
                             curr->next_top_at_mark_start(),
                             curr->top_at_conc_mark_count(),
                             curr->age_in_surv_rate_group_cond(),
                             curr->is_young(),
                             curr->is_survivor());
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
  _czft->stop();
  _cmThread->stop();
}


void G1CollectedHeap::check_ct_logs_at_safepoint() {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  CardTableModRefBS* ct_bs = (CardTableModRefBS*)barrier_set();

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

// Finds a HeapRegion that can be used to allocate a given size of block.


HeapRegion* G1CollectedHeap::newAllocRegion_work(size_t word_size,
                                                 bool do_expand,
                                                 bool zero_filled) {
  ConcurrentZFThread::note_region_alloc();
  HeapRegion* res = alloc_free_region_from_lists(zero_filled);
  if (res == NULL && do_expand) {
    expand(word_size * HeapWordSize);
    res = alloc_free_region_from_lists(zero_filled);
    assert(res == NULL ||
           (!res->isHumongous() &&
            (!zero_filled ||
             res->zero_fill_state() == HeapRegion::Allocated)),
           "Alloc Regions must be zero filled (and non-H)");
  }
  if (res != NULL) {
    if (res->is_empty()) {
      _free_regions--;
    }
    assert(!res->isHumongous() &&
           (!zero_filled || res->zero_fill_state() == HeapRegion::Allocated),
           err_msg("Non-young alloc Regions must be zero filled (and non-H):"
                   " res->isHumongous()=%d, zero_filled=%d, res->zero_fill_state()=%d",
                   res->isHumongous(), zero_filled, res->zero_fill_state()));
    assert(!res->is_on_unclean_list(),
           "Alloc Regions must not be on the unclean list");
    if (G1PrintHeapRegions) {
      gclog_or_tty->print_cr("new alloc region %d:["PTR_FORMAT", "PTR_FORMAT"], "
                             "top "PTR_FORMAT,
                             res->hrs_index(), res->bottom(), res->end(), res->top());
    }
  }
  return res;
}

HeapRegion* G1CollectedHeap::newAllocRegionWithExpansion(int purpose,
                                                         size_t word_size,
                                                         bool zero_filled) {
  HeapRegion* alloc_region = NULL;
  if (_gc_alloc_region_counts[purpose] < g1_policy()->max_regions(purpose)) {
    alloc_region = newAllocRegion_work(word_size, true, zero_filled);
    if (purpose == GCAllocForSurvived && alloc_region != NULL) {
      alloc_region->set_survivor();
    }
    ++_gc_alloc_region_counts[purpose];
  } else {
    g1_policy()->note_alloc_region_limit_reached(purpose);
  }
  return alloc_region;
}

// If could fit into free regions w/o expansion, try.
// Otherwise, if can expand, do so.
// Otherwise, if using ex regions might help, try with ex given back.
HeapWord* G1CollectedHeap::humongousObjAllocate(size_t word_size) {
  assert(regions_accounted_for(), "Region leakage!");

  // We can't allocate H regions while cleanupComplete is running, since
  // some of the regions we find to be empty might not yet be added to the
  // unclean list.  (If we're already at a safepoint, this call is
  // unnecessary, not to mention wrong.)
  if (!SafepointSynchronize::is_at_safepoint())
    wait_for_cleanup_complete();

  size_t num_regions =
    round_to(word_size, HeapRegion::GrainWords) / HeapRegion::GrainWords;

  // Special case if < one region???

  // Remember the ft size.
  size_t x_size = expansion_regions();

  HeapWord* res = NULL;
  bool eliminated_allocated_from_lists = false;

  // Can the allocation potentially fit in the free regions?
  if (free_regions() >= num_regions) {
    res = _hrs->obj_allocate(word_size);
  }
  if (res == NULL) {
    // Try expansion.
    size_t fs = _hrs->free_suffix();
    if (fs + x_size >= num_regions) {
      expand((num_regions - fs) * HeapRegion::GrainBytes);
      res = _hrs->obj_allocate(word_size);
      assert(res != NULL, "This should have worked.");
    } else {
      // Expansion won't help.  Are there enough free regions if we get rid
      // of reservations?
      size_t avail = free_regions();
      if (avail >= num_regions) {
        res = _hrs->obj_allocate(word_size);
        if (res != NULL) {
          remove_allocated_regions_from_lists();
          eliminated_allocated_from_lists = true;
        }
      }
    }
  }
  if (res != NULL) {
    // Increment by the number of regions allocated.
    // FIXME: Assumes regions all of size GrainBytes.
#ifndef PRODUCT
    mr_bs()->verify_clean_region(MemRegion(res, res + num_regions *
                                           HeapRegion::GrainWords));
#endif
    if (!eliminated_allocated_from_lists)
      remove_allocated_regions_from_lists();
    _summary_bytes_used += word_size * HeapWordSize;
    _free_regions -= num_regions;
    _num_humongous_regions += (int) num_regions;
  }
  assert(regions_accounted_for(), "Region Leakage");
  return res;
}

HeapWord*
G1CollectedHeap::attempt_allocation_slow(size_t word_size,
                                         bool permit_collection_pause) {
  HeapWord* res = NULL;
  HeapRegion* allocated_young_region = NULL;

  assert( SafepointSynchronize::is_at_safepoint() ||
          Heap_lock->owned_by_self(), "pre condition of the call" );

  if (isHumongous(word_size)) {
    // Allocation of a humongous object can, in a sense, complete a
    // partial region, if the previous alloc was also humongous, and
    // caused the test below to succeed.
    if (permit_collection_pause)
      do_collection_pause_if_appropriate(word_size);
    res = humongousObjAllocate(word_size);
    assert(_cur_alloc_region == NULL
           || !_cur_alloc_region->isHumongous(),
           "Prevent a regression of this bug.");

  } else {
    // We may have concurrent cleanup working at the time. Wait for it
    // to complete. In the future we would probably want to make the
    // concurrent cleanup truly concurrent by decoupling it from the
    // allocation.
    if (!SafepointSynchronize::is_at_safepoint())
      wait_for_cleanup_complete();
    // If we do a collection pause, this will be reset to a non-NULL
    // value.  If we don't, nulling here ensures that we allocate a new
    // region below.
    if (_cur_alloc_region != NULL) {
      // We're finished with the _cur_alloc_region.
      // As we're builing (at least the young portion) of the collection
      // set incrementally we'll add the current allocation region to
      // the collection set here.
      if (_cur_alloc_region->is_young()) {
        g1_policy()->add_region_to_incremental_cset_lhs(_cur_alloc_region);
      }
      _summary_bytes_used += _cur_alloc_region->used();
      _cur_alloc_region = NULL;
    }
    assert(_cur_alloc_region == NULL, "Invariant.");
    // Completion of a heap region is perhaps a good point at which to do
    // a collection pause.
    if (permit_collection_pause)
      do_collection_pause_if_appropriate(word_size);
    // Make sure we have an allocation region available.
    if (_cur_alloc_region == NULL) {
      if (!SafepointSynchronize::is_at_safepoint())
        wait_for_cleanup_complete();
      bool next_is_young = should_set_young_locked();
      // If the next region is not young, make sure it's zero-filled.
      _cur_alloc_region = newAllocRegion(word_size, !next_is_young);
      if (_cur_alloc_region != NULL) {
        _summary_bytes_used -= _cur_alloc_region->used();
        if (next_is_young) {
          set_region_short_lived_locked(_cur_alloc_region);
          allocated_young_region = _cur_alloc_region;
        }
      }
    }
    assert(_cur_alloc_region == NULL || !_cur_alloc_region->isHumongous(),
           "Prevent a regression of this bug.");

    // Now retry the allocation.
    if (_cur_alloc_region != NULL) {
      if (allocated_young_region != NULL) {
        // We need to ensure that the store to top does not
        // float above the setting of the young type.
        OrderAccess::storestore();
      }
      res = _cur_alloc_region->allocate(word_size);
    }
  }

  // NOTE: fails frequently in PRT
  assert(regions_accounted_for(), "Region leakage!");

  if (res != NULL) {
    if (!SafepointSynchronize::is_at_safepoint()) {
      assert( permit_collection_pause, "invariant" );
      assert( Heap_lock->owned_by_self(), "invariant" );
      Heap_lock->unlock();
    }

    if (allocated_young_region != NULL) {
      HeapRegion* hr = allocated_young_region;
      HeapWord* bottom = hr->bottom();
      HeapWord* end = hr->end();
      MemRegion mr(bottom, end);
      ((CardTableModRefBS*)_g1h->barrier_set())->dirty(mr);
    }
  }

  assert( SafepointSynchronize::is_at_safepoint() ||
          (res == NULL && Heap_lock->owned_by_self()) ||
          (res != NULL && !Heap_lock->owned_by_self()),
          "post condition of the call" );

  return res;
}

HeapWord*
G1CollectedHeap::mem_allocate(size_t word_size,
                              bool   is_noref,
                              bool   is_tlab,
                              bool* gc_overhead_limit_was_exceeded) {
  debug_only(check_for_valid_allocation_state());
  assert(no_gc_in_progress(), "Allocation during gc not allowed");
  HeapWord* result = NULL;

  // Loop until the allocation is satisified,
  // or unsatisfied after GC.
  for (int try_count = 1; /* return or throw */; try_count += 1) {
    int gc_count_before;
    {
      Heap_lock->lock();
      result = attempt_allocation(word_size);
      if (result != NULL) {
        // attempt_allocation should have unlocked the heap lock
        assert(is_in(result), "result not in heap");
        return result;
      }
      // Read the gc count while the heap lock is held.
      gc_count_before = SharedHeap::heap()->total_collections();
      Heap_lock->unlock();
    }

    // Create the garbage collection operation...
    VM_G1CollectForAllocation op(word_size,
                                 gc_count_before);

    // ...and get the VM thread to execute it.
    VMThread::execute(&op);
    if (op.prologue_succeeded()) {
      result = op.result();
      assert(result == NULL || is_in(result), "result not in heap");
      return result;
    }

    // Give a warning if we seem to be looping forever.
    if ((QueuedAllocationWarningCount > 0) &&
        (try_count % QueuedAllocationWarningCount == 0)) {
      warning("G1CollectedHeap::mem_allocate_work retries %d times",
              try_count);
    }
  }
}

void G1CollectedHeap::abandon_cur_alloc_region() {
  if (_cur_alloc_region != NULL) {
    // We're finished with the _cur_alloc_region.
    if (_cur_alloc_region->is_empty()) {
      _free_regions++;
      free_region(_cur_alloc_region);
    } else {
      // As we're builing (at least the young portion) of the collection
      // set incrementally we'll add the current allocation region to
      // the collection set here.
      if (_cur_alloc_region->is_young()) {
        g1_policy()->add_region_to_incremental_cset_lhs(_cur_alloc_region);
      }
      _summary_bytes_used += _cur_alloc_region->used();
    }
    _cur_alloc_region = NULL;
  }
}

void G1CollectedHeap::abandon_gc_alloc_regions() {
  // first, make sure that the GC alloc region list is empty (it should!)
  assert(_gc_alloc_region_list == NULL, "invariant");
  release_gc_alloc_regions(true /* totally */);
}

class PostMCRemSetClearClosure: public HeapRegionClosure {
  ModRefBarrierSet* _mr_bs;
public:
  PostMCRemSetClearClosure(ModRefBarrierSet* mr_bs) : _mr_bs(mr_bs) {}
  bool doHeapRegion(HeapRegion* r) {
    r->reset_gc_time_stamp();
    if (r->continuesHumongous())
      return false;
    HeapRegionRemSet* hrrs = r->rem_set();
    if (hrrs != NULL) hrrs->clear();
    // You might think here that we could clear just the cards
    // corresponding to the used region.  But no: if we leave a dirty card
    // in a region we might allocate into, then it would prevent that card
    // from being enqueued, and cause it to be missed.
    // Re: the performance cost: we shouldn't be doing full GC anyway!
    _mr_bs->clear(MemRegion(r->bottom(), r->end()));
    return false;
  }
};


class PostMCRemSetInvalidateClosure: public HeapRegionClosure {
  ModRefBarrierSet* _mr_bs;
public:
  PostMCRemSetInvalidateClosure(ModRefBarrierSet* mr_bs) : _mr_bs(mr_bs) {}
  bool doHeapRegion(HeapRegion* r) {
    if (r->continuesHumongous()) return false;
    if (r->used_region().word_size() != 0) {
      _mr_bs->invalidate(r->used_region(), true /*whole heap*/);
    }
    return false;
  }
};

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

  void work(int i) {
    RebuildRSOutOfRegionClosure rebuild_rs(_g1, i);
    _g1->heap_region_par_iterate_chunked(&rebuild_rs, i,
                                         HeapRegion::RebuildRSClaimValue);
  }
};

void G1CollectedHeap::do_collection(bool explicit_gc,
                                    bool clear_all_soft_refs,
                                    size_t word_size) {
  if (GC_locker::check_active_before_gc()) {
    return; // GC is disabled (e.g. JNI GetXXXCritical operation)
  }

  ResourceMark rm;

  if (PrintHeapAtGC) {
    Universe::print_heap_before_gc();
  }

  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(Thread::current() == VMThread::vm_thread(), "should be in vm thread");

  const bool do_clear_all_soft_refs = clear_all_soft_refs ||
                           collector_policy()->should_clear_all_soft_refs();

  ClearedAllSoftRefs casr(do_clear_all_soft_refs, collector_policy());

  {
    IsGCActiveMark x;

    // Timing
    bool system_gc = (gc_cause() == GCCause::_java_lang_system_gc);
    assert(!system_gc || explicit_gc, "invariant");
    gclog_or_tty->date_stamp(PrintGC && PrintGCDateStamps);
    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    TraceTime t(system_gc ? "Full GC (System.gc())" : "Full GC",
                PrintGC, true, gclog_or_tty);

    TraceMemoryManagerStats tms(true /* fullGC */);

    double start = os::elapsedTime();
    g1_policy()->record_full_collection_start();

    gc_prologue(true);
    increment_total_collections(true /* full gc */);

    size_t g1h_prev_used = used();
    assert(used() == recalculate_used(), "Should be equal");

    if (VerifyBeforeGC && total_collections() >= VerifyGCStartAt) {
      HandleMark hm;  // Discard invalid handles created during verification
      prepare_for_verify();
      gclog_or_tty->print(" VerifyBeforeGC:");
      Universe::verify(true);
    }
    assert(regions_accounted_for(), "Region leakage!");

    COMPILER2_PRESENT(DerivedPointerTable::clear());

    // We want to discover references, but not process them yet.
    // This mode is disabled in
    // instanceRefKlass::process_discovered_references if the
    // generation does some collection work, or
    // instanceRefKlass::enqueue_discovered_references if the
    // generation returns without doing any work.
    ref_processor()->disable_discovery();
    ref_processor()->abandon_partial_discovery();
    ref_processor()->verify_no_references_recorded();

    // Abandon current iterations of concurrent marking and concurrent
    // refinement, if any are in progress.
    concurrent_mark()->abort();

    // Make sure we'll choose a new allocation region afterwards.
    abandon_cur_alloc_region();
    abandon_gc_alloc_regions();
    assert(_cur_alloc_region == NULL, "Invariant.");
    g1_rem_set()->cleanupHRRS();
    tear_down_region_lists();
    set_used_regions_to_need_zero_fill();

    // We may have added regions to the current incremental collection
    // set between the last GC or pause and now. We need to clear the
    // incremental collection set and then start rebuilding it afresh
    // after this full GC.
    abandon_collection_set(g1_policy()->inc_cset_head());
    g1_policy()->clear_incremental_cset();
    g1_policy()->stop_incremental_cset_building();

    if (g1_policy()->in_young_gc_mode()) {
      empty_young_list();
      g1_policy()->set_full_young_gcs(true);
    }

    // Temporarily make reference _discovery_ single threaded (non-MT).
    ReferenceProcessorMTMutator rp_disc_ser(ref_processor(), false);

    // Temporarily make refs discovery atomic
    ReferenceProcessorAtomicMutator rp_disc_atomic(ref_processor(), true);

    // Temporarily clear _is_alive_non_header
    ReferenceProcessorIsAliveMutator rp_is_alive_null(ref_processor(), NULL);

    ref_processor()->enable_discovery();
    ref_processor()->setup_policy(do_clear_all_soft_refs);

    // Do collection work
    {
      HandleMark hm;  // Discard invalid handles created during gc
      G1MarkSweep::invoke_at_safepoint(ref_processor(), do_clear_all_soft_refs);
    }
    // Because freeing humongous regions may have added some unclean
    // regions, it is necessary to tear down again before rebuilding.
    tear_down_region_lists();
    rebuild_region_lists();

    _summary_bytes_used = recalculate_used();

    ref_processor()->enqueue_discovered_references();

    COMPILER2_PRESENT(DerivedPointerTable::update_pointers());

    MemoryService::track_memory_usage();

    if (VerifyAfterGC && total_collections() >= VerifyGCStartAt) {
      HandleMark hm;  // Discard invalid handles created during verification
      gclog_or_tty->print(" VerifyAfterGC:");
      prepare_for_verify();
      Universe::verify(false);
    }
    NOT_PRODUCT(ref_processor()->verify_no_references_recorded());

    reset_gc_time_stamp();
    // Since everything potentially moved, we will clear all remembered
    // sets, and clear all cards.  Later we will rebuild remebered
    // sets. We will also reset the GC time stamps of the regions.
    PostMCRemSetClearClosure rs_clear(mr_bs());
    heap_region_iterate(&rs_clear);

    // Resize the heap if necessary.
    resize_if_necessary_after_full_collection(explicit_gc ? 0 : word_size);

    if (_cg1r->use_cache()) {
      _cg1r->clear_and_record_card_counts();
      _cg1r->clear_hot_cache();
    }

    // Rebuild remembered sets of all regions.

    if (G1CollectedHeap::use_parallel_gc_threads()) {
      ParRebuildRSTask rebuild_rs_task(this);
      assert(check_heap_region_claim_values(
             HeapRegion::InitialClaimValue), "sanity check");
      set_par_threads(workers()->total_workers());
      workers()->run_task(&rebuild_rs_task);
      set_par_threads(0);
      assert(check_heap_region_claim_values(
             HeapRegion::RebuildRSClaimValue), "sanity check");
      reset_heap_region_claim_values();
    } else {
      RebuildRSOutOfRegionClosure rebuild_rs(this);
      heap_region_iterate(&rebuild_rs);
    }

    if (PrintGC) {
      print_size_transition(gclog_or_tty, g1h_prev_used, used(), capacity());
    }

    if (true) { // FIXME
      // Ask the permanent generation to adjust size for full collections
      perm()->compute_new_size();
    }

    // Start a new incremental collection set for the next pause
    assert(g1_policy()->collection_set() == NULL, "must be");
    g1_policy()->start_incremental_cset_building();

    // Clear the _cset_fast_test bitmap in anticipation of adding
    // regions to the incremental collection set for the next
    // evacuation pause.
    clear_cset_fast_test();

    double end = os::elapsedTime();
    g1_policy()->record_full_collection_end();

#ifdef TRACESPINNING
    ParallelTaskTerminator::print_termination_counts();
#endif

    gc_epilogue(true);

    // Discard all rset updates
    JavaThread::dirty_card_queue_set().abandon_logs();
    assert(!G1DeferredRSUpdate
           || (G1DeferredRSUpdate && (dirty_card_queue_set().completed_buffers_num() == 0)), "Should not be any");
    assert(regions_accounted_for(), "Region leakage!");
  }

  if (g1_policy()->in_young_gc_mode()) {
    _young_list->reset_sampled_info();
    // At this point there should be no regions in the
    // entire heap tagged as young.
    assert( check_young_list_empty(true /* check_heap */),
            "young list should be empty at this point");
  }

  // Update the number of full collections that have been completed.
  increment_full_collections_completed(false /* outer */);

  if (PrintHeapAtGC) {
    Universe::print_heap_after_gc();
  }
}

void G1CollectedHeap::do_full_collection(bool clear_all_soft_refs) {
  do_collection(true,                /* explicit_gc */
                clear_all_soft_refs,
                0                    /* word_size */);
}

// This code is mostly copied from TenuredGeneration.
void
G1CollectedHeap::
resize_if_necessary_after_full_collection(size_t word_size) {
  assert(MinHeapFreeRatio <= MaxHeapFreeRatio, "sanity check");

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

  if (PrintGC && Verbose) {
    const double free_percentage =
      (double) free_after_gc / (double) capacity_after_gc;
    gclog_or_tty->print_cr("Computing new size after full GC ");
    gclog_or_tty->print_cr("  "
                           "  minimum_free_percentage: %6.2f",
                           minimum_free_percentage);
    gclog_or_tty->print_cr("  "
                           "  maximum_free_percentage: %6.2f",
                           maximum_free_percentage);
    gclog_or_tty->print_cr("  "
                           "  capacity: %6.1fK"
                           "  minimum_desired_capacity: %6.1fK"
                           "  maximum_desired_capacity: %6.1fK",
                           (double) capacity_after_gc / (double) K,
                           (double) minimum_desired_capacity / (double) K,
                           (double) maximum_desired_capacity / (double) K);
    gclog_or_tty->print_cr("  "
                           "  free_after_gc: %6.1fK"
                           "  used_after_gc: %6.1fK",
                           (double) free_after_gc / (double) K,
                           (double) used_after_gc / (double) K);
    gclog_or_tty->print_cr("  "
                           "   free_percentage: %6.2f",
                           free_percentage);
  }
  if (capacity_after_gc < minimum_desired_capacity) {
    // Don't expand unless it's significant
    size_t expand_bytes = minimum_desired_capacity - capacity_after_gc;
    expand(expand_bytes);
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("  "
                             "  expanding:"
                             "  max_heap_size: %6.1fK"
                             "  minimum_desired_capacity: %6.1fK"
                             "  expand_bytes: %6.1fK",
                             (double) max_heap_size / (double) K,
                             (double) minimum_desired_capacity / (double) K,
                             (double) expand_bytes / (double) K);
    }

    // No expansion, now see if we want to shrink
  } else if (capacity_after_gc > maximum_desired_capacity) {
    // Capacity too large, compute shrinking size
    size_t shrink_bytes = capacity_after_gc - maximum_desired_capacity;
    shrink(shrink_bytes);
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("  "
                             "  shrinking:"
                             "  min_heap_size: %6.1fK"
                             "  maximum_desired_capacity: %6.1fK"
                             "  shrink_bytes: %6.1fK",
                             (double) min_heap_size / (double) K,
                             (double) maximum_desired_capacity / (double) K,
                             (double) shrink_bytes / (double) K);
    }
  }
}


HeapWord*
G1CollectedHeap::satisfy_failed_allocation(size_t word_size) {
  HeapWord* result = NULL;

  // In a G1 heap, we're supposed to keep allocation from failing by
  // incremental pauses.  Therefore, at least for now, we'll favor
  // expansion over collection.  (This might change in the future if we can
  // do something smarter than full collection to satisfy a failed alloc.)

  result = expand_and_allocate(word_size);
  if (result != NULL) {
    assert(is_in(result), "result not in heap");
    return result;
  }

  // OK, I guess we have to try collection.

  do_collection(false, false, word_size);

  result = attempt_allocation(word_size, /*permit_collection_pause*/false);

  if (result != NULL) {
    assert(is_in(result), "result not in heap");
    return result;
  }

  // Try collecting soft references.
  do_collection(false, true, word_size);
  result = attempt_allocation(word_size, /*permit_collection_pause*/false);
  if (result != NULL) {
    assert(is_in(result), "result not in heap");
    return result;
  }

  assert(!collector_policy()->should_clear_all_soft_refs(),
    "Flag should have been handled and cleared prior to this point");

  // What else?  We might try synchronous finalization later.  If the total
  // space available is large enough for the allocation, then a more
  // complete compaction phase than we've tried so far might be
  // appropriate.
  return NULL;
}

// Attempting to expand the heap sufficiently
// to support an allocation of the given "word_size".  If
// successful, perform the allocation and return the address of the
// allocated block, or else "NULL".

HeapWord* G1CollectedHeap::expand_and_allocate(size_t word_size) {
  size_t expand_bytes = word_size * HeapWordSize;
  if (expand_bytes < MinHeapDeltaBytes) {
    expand_bytes = MinHeapDeltaBytes;
  }
  expand(expand_bytes);
  assert(regions_accounted_for(), "Region leakage!");
  HeapWord* result = attempt_allocation(word_size, false /* permit_collection_pause */);
  return result;
}

size_t G1CollectedHeap::free_region_if_totally_empty(HeapRegion* hr) {
  size_t pre_used = 0;
  size_t cleared_h_regions = 0;
  size_t freed_regions = 0;
  UncleanRegionList local_list;
  free_region_if_totally_empty_work(hr, pre_used, cleared_h_regions,
                                    freed_regions, &local_list);

  finish_free_region_work(pre_used, cleared_h_regions, freed_regions,
                          &local_list);
  return pre_used;
}

void
G1CollectedHeap::free_region_if_totally_empty_work(HeapRegion* hr,
                                                   size_t& pre_used,
                                                   size_t& cleared_h,
                                                   size_t& freed_regions,
                                                   UncleanRegionList* list,
                                                   bool par) {
  assert(!hr->continuesHumongous(), "should have filtered these out");
  size_t res = 0;
  if (hr->used() > 0 && hr->garbage_bytes() == hr->used() &&
      !hr->is_young()) {
    if (G1PolicyVerbose > 0)
      gclog_or_tty->print_cr("Freeing empty region "PTR_FORMAT "(" SIZE_FORMAT " bytes)"
                                                                               " during cleanup", hr, hr->used());
    free_region_work(hr, pre_used, cleared_h, freed_regions, list, par);
  }
}

// FIXME: both this and shrink could probably be more efficient by
// doing one "VirtualSpace::expand_by" call rather than several.
void G1CollectedHeap::expand(size_t expand_bytes) {
  size_t old_mem_size = _g1_storage.committed_size();
  // We expand by a minimum of 1K.
  expand_bytes = MAX2(expand_bytes, (size_t)K);
  size_t aligned_expand_bytes =
    ReservedSpace::page_align_size_up(expand_bytes);
  aligned_expand_bytes = align_size_up(aligned_expand_bytes,
                                       HeapRegion::GrainBytes);
  expand_bytes = aligned_expand_bytes;
  while (expand_bytes > 0) {
    HeapWord* base = (HeapWord*)_g1_storage.high();
    // Commit more storage.
    bool successful = _g1_storage.expand_by(HeapRegion::GrainBytes);
    if (!successful) {
        expand_bytes = 0;
    } else {
      expand_bytes -= HeapRegion::GrainBytes;
      // Expand the committed region.
      HeapWord* high = (HeapWord*) _g1_storage.high();
      _g1_committed.set_end(high);
      // Create a new HeapRegion.
      MemRegion mr(base, high);
      bool is_zeroed = !_g1_max_committed.contains(base);
      HeapRegion* hr = new HeapRegion(_bot_shared, mr, is_zeroed);

      // Now update max_committed if necessary.
      _g1_max_committed.set_end(MAX2(_g1_max_committed.end(), high));

      // Add it to the HeapRegionSeq.
      _hrs->insert(hr);
      // Set the zero-fill state, according to whether it's already
      // zeroed.
      {
        MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
        if (is_zeroed) {
          hr->set_zero_fill_complete();
          put_free_region_on_list_locked(hr);
        } else {
          hr->set_zero_fill_needed();
          put_region_on_unclean_list_locked(hr);
        }
      }
      _free_regions++;
      // And we used up an expansion region to create it.
      _expansion_regions--;
      // Tell the cardtable about it.
      Universe::heap()->barrier_set()->resize_covered_region(_g1_committed);
      // And the offset table as well.
      _bot_shared->resize(_g1_committed.word_size());
    }
  }
  if (Verbose && PrintGC) {
    size_t new_mem_size = _g1_storage.committed_size();
    gclog_or_tty->print_cr("Expanding garbage-first heap from %ldK by %ldK to %ldK",
                           old_mem_size/K, aligned_expand_bytes/K,
                           new_mem_size/K);
  }
}

void G1CollectedHeap::shrink_helper(size_t shrink_bytes)
{
  size_t old_mem_size = _g1_storage.committed_size();
  size_t aligned_shrink_bytes =
    ReservedSpace::page_align_size_down(shrink_bytes);
  aligned_shrink_bytes = align_size_down(aligned_shrink_bytes,
                                         HeapRegion::GrainBytes);
  size_t num_regions_deleted = 0;
  MemRegion mr = _hrs->shrink_by(aligned_shrink_bytes, num_regions_deleted);

  assert(mr.end() == (HeapWord*)_g1_storage.high(), "Bad shrink!");
  if (mr.byte_size() > 0)
    _g1_storage.shrink_by(mr.byte_size());
  assert(mr.start() == (HeapWord*)_g1_storage.high(), "Bad shrink!");

  _g1_committed.set_end(mr.start());
  _free_regions -= num_regions_deleted;
  _expansion_regions += num_regions_deleted;

  // Tell the cardtable about it.
  Universe::heap()->barrier_set()->resize_covered_region(_g1_committed);

  // And the offset table as well.
  _bot_shared->resize(_g1_committed.word_size());

  HeapRegionRemSet::shrink_heap(n_regions());

  if (Verbose && PrintGC) {
    size_t new_mem_size = _g1_storage.committed_size();
    gclog_or_tty->print_cr("Shrinking garbage-first heap from %ldK by %ldK to %ldK",
                           old_mem_size/K, aligned_shrink_bytes/K,
                           new_mem_size/K);
  }
}

void G1CollectedHeap::shrink(size_t shrink_bytes) {
  release_gc_alloc_regions(true /* totally */);
  tear_down_region_lists();  // We will rebuild them in a moment.
  shrink_helper(shrink_bytes);
  rebuild_region_lists();
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
  _ref_processor(NULL),
  _process_strong_tasks(new SubTasksDone(G1H_PS_NumElements)),
  _bot_shared(NULL),
  _par_alloc_during_gc_lock(Mutex::leaf, "par alloc during GC lock"),
  _objs_with_preserved_marks(NULL), _preserved_marks_of_objs(NULL),
  _evac_failure_scan_stack(NULL) ,
  _mark_in_progress(false),
  _cg1r(NULL), _czft(NULL), _summary_bytes_used(0),
  _cur_alloc_region(NULL),
  _refine_cte_cl(NULL),
  _free_region_list(NULL), _free_region_list_size(0),
  _free_regions(0),
  _full_collection(false),
  _unclean_region_list(),
  _unclean_regions_coming(false),
  _young_list(new YoungList(this)),
  _gc_time_stamp(0),
  _surviving_young_words(NULL),
  _full_collections_completed(0),
  _in_cset_fast_test(NULL),
  _in_cset_fast_test_base(NULL),
  _dirty_cards_region_list(NULL) {
  _g1h = this; // To catch bugs.
  if (_process_strong_tasks == NULL || !_process_strong_tasks->valid()) {
    vm_exit_during_initialization("Failed necessary allocation.");
  }

  _humongous_object_threshold_in_words = HeapRegion::GrainWords / 2;

  int n_queues = MAX2((int)ParallelGCThreads, 1);
  _task_queues = new RefToScanQueueSet(n_queues);

  int n_rem_sets = HeapRegionRemSet::num_par_rem_sets();
  assert(n_rem_sets > 0, "Invariant.");

  HeapRegionRemSetIterator** iter_arr =
    NEW_C_HEAP_ARRAY(HeapRegionRemSetIterator*, n_queues);
  for (int i = 0; i < n_queues; i++) {
    iter_arr[i] = new HeapRegionRemSetIterator();
  }
  _rem_set_iterator = iter_arr;

  for (int i = 0; i < n_queues; i++) {
    RefToScanQueue* q = new RefToScanQueue();
    q->initialize();
    _task_queues->register_queue(i, q);
  }

  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    _gc_alloc_regions[ap]          = NULL;
    _gc_alloc_region_counts[ap]    = 0;
    _retained_gc_alloc_regions[ap] = NULL;
    // by default, we do not retain a GC alloc region for each ap;
    // we'll override this, when appropriate, below
    _retain_gc_alloc_region[ap]    = false;
  }

  // We will try to remember the last half-full tenured region we
  // allocated to at the end of a collection so that we can re-use it
  // during the next collection.
  _retain_gc_alloc_region[GCAllocForTenured]  = true;

  guarantee(_task_queues != NULL, "task_queues allocation failure.");
}

jint G1CollectedHeap::initialize() {
  CollectedHeap::pre_initialize();
  os::enable_vtime();

  // Necessary to satisfy locking discipline assertions.

  MutexLocker x(Heap_lock);

  // While there are no constraints in the GC code that HeapWordSize
  // be any particular value, there are multiple other areas in the
  // system which believe this to be true (e.g. oop->object_size in some
  // cases incorrectly returns the size in wordSize units rather than
  // HeapWordSize).
  guarantee(HeapWordSize == wordSize, "HeapWordSize must equal wordSize");

  size_t init_byte_size = collector_policy()->initial_heap_byte_size();
  size_t max_byte_size = collector_policy()->max_heap_byte_size();

  // Ensure that the sizes are properly aligned.
  Universe::check_alignment(init_byte_size, HeapRegion::GrainBytes, "g1 heap");
  Universe::check_alignment(max_byte_size, HeapRegion::GrainBytes, "g1 heap");

  _cg1r = new ConcurrentG1Refine();

  // Reserve the maximum.
  PermanentGenerationSpec* pgs = collector_policy()->permanent_generation();
  // Includes the perm-gen.

  const size_t total_reserved = max_byte_size + pgs->max_size();
  char* addr = Universe::preferred_heap_base(total_reserved, Universe::UnscaledNarrowOop);

  ReservedSpace heap_rs(max_byte_size + pgs->max_size(),
                        HeapRegion::GrainBytes,
                        false /*ism*/, addr);

  if (UseCompressedOops) {
    if (addr != NULL && !heap_rs.is_reserved()) {
      // Failed to reserve at specified address - the requested memory
      // region is taken already, for example, by 'java' launcher.
      // Try again to reserver heap higher.
      addr = Universe::preferred_heap_base(total_reserved, Universe::ZeroBasedNarrowOop);
      ReservedSpace heap_rs0(total_reserved, HeapRegion::GrainBytes,
                             false /*ism*/, addr);
      if (addr != NULL && !heap_rs0.is_reserved()) {
        // Failed to reserve at specified address again - give up.
        addr = Universe::preferred_heap_base(total_reserved, Universe::HeapBasedNarrowOop);
        assert(addr == NULL, "");
        ReservedSpace heap_rs1(total_reserved, HeapRegion::GrainBytes,
                               false /*ism*/, addr);
        heap_rs = heap_rs1;
      } else {
        heap_rs = heap_rs0;
      }
    }
  }

  if (!heap_rs.is_reserved()) {
    vm_exit_during_initialization("Could not reserve enough space for object heap");
    return JNI_ENOMEM;
  }

  // It is important to do this in a way such that concurrent readers can't
  // temporarily think somethings in the heap.  (I've actually seen this
  // happen in asserts: DLD.)
  _reserved.set_word_size(0);
  _reserved.set_start((HeapWord*)heap_rs.base());
  _reserved.set_end((HeapWord*)(heap_rs.base() + heap_rs.size()));

  _expansion_regions = max_byte_size/HeapRegion::GrainBytes;

  _num_humongous_regions = 0;

  // Create the gen rem set (and barrier set) for the entire reserved region.
  _rem_set = collector_policy()->create_rem_set(_reserved, 2);
  set_barrier_set(rem_set()->bs());
  if (barrier_set()->is_a(BarrierSet::ModRef)) {
    _mr_bs = (ModRefBarrierSet*)_barrier_set;
  } else {
    vm_exit_during_initialization("G1 requires a mod ref bs.");
    return JNI_ENOMEM;
  }

  // Also create a G1 rem set.
  if (mr_bs()->is_a(BarrierSet::CardTableModRef)) {
    _g1_rem_set = new G1RemSet(this, (CardTableModRefBS*)mr_bs());
  } else {
    vm_exit_during_initialization("G1 requires a cardtable mod ref bs.");
    return JNI_ENOMEM;
  }

  // Carve out the G1 part of the heap.

  ReservedSpace g1_rs   = heap_rs.first_part(max_byte_size);
  _g1_reserved = MemRegion((HeapWord*)g1_rs.base(),
                           g1_rs.size()/HeapWordSize);
  ReservedSpace perm_gen_rs = heap_rs.last_part(max_byte_size);

  _perm_gen = pgs->init(perm_gen_rs, pgs->init_size(), rem_set());

  _g1_storage.initialize(g1_rs, 0);
  _g1_committed = MemRegion((HeapWord*)_g1_storage.low(), (size_t) 0);
  _g1_max_committed = _g1_committed;
  _hrs = new HeapRegionSeq(_expansion_regions);
  guarantee(_hrs != NULL, "Couldn't allocate HeapRegionSeq");
  guarantee(_cur_alloc_region == NULL, "from constructor");

  // 6843694 - ensure that the maximum region index can fit
  // in the remembered set structures.
  const size_t max_region_idx = ((size_t)1 << (sizeof(RegionIdx_t)*BitsPerByte-1)) - 1;
  guarantee((max_regions() - 1) <= max_region_idx, "too many regions");

  size_t max_cards_per_region = ((size_t)1 << (sizeof(CardIdx_t)*BitsPerByte-1)) - 1;
  guarantee(HeapRegion::CardsPerRegion > 0, "make sure it's initialized");
  guarantee((size_t) HeapRegion::CardsPerRegion < max_cards_per_region,
            "too many cards per region");

  _bot_shared = new G1BlockOffsetSharedArray(_reserved,
                                             heap_word_size(init_byte_size));

  _g1h = this;

   _in_cset_fast_test_length = max_regions();
   _in_cset_fast_test_base = NEW_C_HEAP_ARRAY(bool, _in_cset_fast_test_length);

   // We're biasing _in_cset_fast_test to avoid subtracting the
   // beginning of the heap every time we want to index; basically
   // it's the same with what we do with the card table.
   _in_cset_fast_test = _in_cset_fast_test_base -
                ((size_t) _g1_reserved.start() >> HeapRegion::LogOfHRGrainBytes);

   // Clear the _cset_fast_test bitmap in anticipation of adding
   // regions to the incremental collection set for the first
   // evacuation pause.
   clear_cset_fast_test();

  // Create the ConcurrentMark data structure and thread.
  // (Must do this late, so that "max_regions" is defined.)
  _cm       = new ConcurrentMark(heap_rs, (int) max_regions());
  _cmThread = _cm->cmThread();

  // ...and the concurrent zero-fill thread, if necessary.
  if (G1ConcZeroFill) {
    _czft = new ConcurrentZFThread();
  }

  // Initialize the from_card cache structure of HeapRegionRemSet.
  HeapRegionRemSet::init_heap(max_regions());

  // Now expand into the initial heap size.
  expand(init_byte_size);

  // Perform any initialization actions delegated to the policy.
  g1_policy()->init();

  g1_policy()->note_start_of_mark_thread();

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

  _gc_alloc_region_list = NULL;

  // Do later initialization work for concurrent refinement.
  _cg1r->init();

  return JNI_OK;
}

void G1CollectedHeap::ref_processing_init() {
  SharedHeap::ref_processing_init();
  MemRegion mr = reserved_region();
  _ref_processor = ReferenceProcessor::create_ref_processor(
                                         mr,    // span
                                         false, // Reference discovery is not atomic
                                                // (though it shouldn't matter here.)
                                         true,  // mt_discovery
                                         NULL,  // is alive closure: need to fill this in for efficiency
                                         ParallelGCThreads,
                                         ParallelRefProcEnabled,
                                         true); // Setting next fields of discovered
                                                // lists requires a barrier.
}

size_t G1CollectedHeap::capacity() const {
  return _g1_committed.byte_size();
}

void G1CollectedHeap::iterate_dirty_card_closure(CardTableEntryClosure* cl,
                                                 DirtyCardQueue* into_cset_dcq,
                                                 bool concurrent,
                                                 int worker_i) {
  // Clean cards in the hot card cache
  concurrent_g1_refine()->clean_up_cache(worker_i, g1_rem_set(), into_cset_dcq);

  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  int n_completed_buffers = 0;
  while (dcqs.apply_closure_to_completed_buffer(cl, worker_i, 0, true)) {
    n_completed_buffers++;
  }
  g1_policy()->record_update_rs_processed_buffers(worker_i,
                                                  (double) n_completed_buffers);
  dcqs.clear_n_completed_buffers();
  assert(!dcqs.completed_buffers_exist_dirty(), "Completed buffers exist!");
}


// Computes the sum of the storage used by the various regions.

size_t G1CollectedHeap::used() const {
  assert(Heap_lock->owner() != NULL,
         "Should be owned on this thread's behalf.");
  size_t result = _summary_bytes_used;
  // Read only once in case it is set to NULL concurrently
  HeapRegion* hr = _cur_alloc_region;
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
  _hrs->iterate(&blk);
  return blk.result();
}

#ifndef PRODUCT
class SumUsedRegionsClosure: public HeapRegionClosure {
  size_t _num;
public:
  SumUsedRegionsClosure() : _num(0) {}
  bool doHeapRegion(HeapRegion* r) {
    if (r->continuesHumongous() || r->used() > 0 || r->is_gc_alloc_region()) {
      _num += 1;
    }
    return false;
  }
  size_t result() { return _num; }
};

size_t G1CollectedHeap::recalculate_used_regions() const {
  SumUsedRegionsClosure blk;
  _hrs->iterate(&blk);
  return blk.result();
}
#endif // PRODUCT

size_t G1CollectedHeap::unsafe_max_alloc() {
  if (_free_regions > 0) return HeapRegion::GrainBytes;
  // otherwise, is there space in the current allocation region?

  // We need to store the current allocation region in a local variable
  // here. The problem is that this method doesn't take any locks and
  // there may be other threads which overwrite the current allocation
  // region field. attempt_allocation(), for example, sets it to NULL
  // and this can happen *after* the NULL check here but before the call
  // to free(), resulting in a SIGSEGV. Note that this doesn't appear
  // to be a problem in the optimized build, since the two loads of the
  // current allocation region field are optimized away.
  HeapRegion* car = _cur_alloc_region;

  // FIXME: should iterate over all regions?
  if (car == NULL) {
    return 0;
  }
  return car->free();
}

bool G1CollectedHeap::should_do_concurrent_full_gc(GCCause::Cause cause) {
  return
    ((cause == GCCause::_gc_locker           && GCLockerInvokesConcurrent) ||
     (cause == GCCause::_java_lang_system_gc && ExplicitGCInvokesConcurrent));
}

void G1CollectedHeap::increment_full_collections_completed(bool outer) {
  MonitorLockerEx x(FullGCCount_lock, Mutex::_no_safepoint_check_flag);

  // We have already incremented _total_full_collections at the start
  // of the GC, so total_full_collections() represents how many full
  // collections have been started.
  unsigned int full_collections_started = total_full_collections();

  // Given that this method is called at the end of a Full GC or of a
  // concurrent cycle, and those can be nested (i.e., a Full GC can
  // interrupt a concurrent cycle), the number of full collections
  // completed should be either one (in the case where there was no
  // nesting) or two (when a Full GC interrupted a concurrent cycle)
  // behind the number of full collections started.

  // This is the case for the inner caller, i.e. a Full GC.
  assert(outer ||
         (full_collections_started == _full_collections_completed + 1) ||
         (full_collections_started == _full_collections_completed + 2),
         err_msg("for inner caller: full_collections_started = %u "
                 "is inconsistent with _full_collections_completed = %u",
                 full_collections_started, _full_collections_completed));

  // This is the case for the outer caller, i.e. the concurrent cycle.
  assert(!outer ||
         (full_collections_started == _full_collections_completed + 1),
         err_msg("for outer caller: full_collections_started = %u "
                 "is inconsistent with _full_collections_completed = %u",
                 full_collections_started, _full_collections_completed));

  _full_collections_completed += 1;

  // We need to clear the "in_progress" flag in the CM thread before
  // we wake up any waiters (especially when ExplicitInvokesConcurrent
  // is set) so that if a waiter requests another System.gc() it doesn't
  // incorrectly see that a marking cyle is still in progress.
  if (outer) {
    _cmThread->clear_in_progress();
  }

  // This notify_all() will ensure that a thread that called
  // System.gc() with (with ExplicitGCInvokesConcurrent set or not)
  // and it's waiting for a full GC to finish will be woken up. It is
  // waiting in VM_G1IncCollectionPause::doit_epilogue().
  FullGCCount_lock->notify_all();
}

void G1CollectedHeap::collect_as_vm_thread(GCCause::Cause cause) {
  assert(Thread::current()->is_VM_thread(), "Precondition#1");
  assert(Heap_lock->is_locked(), "Precondition#2");
  GCCauseSetter gcs(this, cause);
  switch (cause) {
    case GCCause::_heap_inspection:
    case GCCause::_heap_dump: {
      HandleMark hm;
      do_full_collection(false);         // don't clear all soft refs
      break;
    }
    default: // XXX FIX ME
      ShouldNotReachHere(); // Unexpected use of this function
  }
}

void G1CollectedHeap::collect(GCCause::Cause cause) {
  // The caller doesn't have the Heap_lock
  assert(!Heap_lock->owned_by_self(), "this thread should not own the Heap_lock");

  unsigned int gc_count_before;
  unsigned int full_gc_count_before;
  {
    MutexLocker ml(Heap_lock);
    // Read the GC count while holding the Heap_lock
    gc_count_before = SharedHeap::heap()->total_collections();
    full_gc_count_before = SharedHeap::heap()->total_full_collections();

    // Don't want to do a GC until cleanup is completed.
    wait_for_cleanup_complete();

    // We give up heap lock; VMThread::execute gets it back below
  }

  if (should_do_concurrent_full_gc(cause)) {
    // Schedule an initial-mark evacuation pause that will start a
    // concurrent cycle.
    VM_G1IncCollectionPause op(gc_count_before,
                               true, /* should_initiate_conc_mark */
                               g1_policy()->max_pause_time_ms(),
                               cause);
    VMThread::execute(&op);
  } else {
    if (cause == GCCause::_gc_locker
        DEBUG_ONLY(|| cause == GCCause::_scavenge_alot)) {

      // Schedule a standard evacuation pause.
      VM_G1IncCollectionPause op(gc_count_before,
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
}

bool G1CollectedHeap::is_in(const void* p) const {
  if (_g1_committed.contains(p)) {
    HeapRegion* hr = _hrs->addr_to_region(p);
    return hr->is_in(p);
  } else {
    return _perm_gen->as_gen()->is_in(p);
  }
}

// Iteration functions.

// Iterates an OopClosure over all ref-containing fields of objects
// within a HeapRegion.

class IterateOopClosureRegionClosure: public HeapRegionClosure {
  MemRegion _mr;
  OopClosure* _cl;
public:
  IterateOopClosureRegionClosure(MemRegion mr, OopClosure* cl)
    : _mr(mr), _cl(cl) {}
  bool doHeapRegion(HeapRegion* r) {
    if (! r->continuesHumongous()) {
      r->oop_iterate(_cl);
    }
    return false;
  }
};

void G1CollectedHeap::oop_iterate(OopClosure* cl, bool do_perm) {
  IterateOopClosureRegionClosure blk(_g1_committed, cl);
  _hrs->iterate(&blk);
  if (do_perm) {
    perm_gen()->oop_iterate(cl);
  }
}

void G1CollectedHeap::oop_iterate(MemRegion mr, OopClosure* cl, bool do_perm) {
  IterateOopClosureRegionClosure blk(mr, cl);
  _hrs->iterate(&blk);
  if (do_perm) {
    perm_gen()->oop_iterate(cl);
  }
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

void G1CollectedHeap::object_iterate(ObjectClosure* cl, bool do_perm) {
  IterateObjectClosureRegionClosure blk(cl);
  _hrs->iterate(&blk);
  if (do_perm) {
    perm_gen()->object_iterate(cl);
  }
}

void G1CollectedHeap::object_iterate_since_last_GC(ObjectClosure* cl) {
  // FIXME: is this right?
  guarantee(false, "object_iterate_since_last_GC not supported by G1 heap");
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
  _hrs->iterate(&blk);
}

void G1CollectedHeap::heap_region_iterate(HeapRegionClosure* cl) {
  _hrs->iterate(cl);
}

void G1CollectedHeap::heap_region_iterate_from(HeapRegion* r,
                                               HeapRegionClosure* cl) {
  _hrs->iterate_from(r, cl);
}

void
G1CollectedHeap::heap_region_iterate_from(int idx, HeapRegionClosure* cl) {
  _hrs->iterate_from(idx, cl);
}

HeapRegion* G1CollectedHeap::region_at(size_t idx) { return _hrs->at(idx); }

void
G1CollectedHeap::heap_region_par_iterate_chunked(HeapRegionClosure* cl,
                                                 int worker,
                                                 jint claim_value) {
  const size_t regions = n_regions();
  const size_t worker_num = (G1CollectedHeap::use_parallel_gc_threads() ? ParallelGCThreads : 1);
  // try to spread out the starting points of the workers
  const size_t start_index = regions / worker_num * (size_t) worker;

  // each worker will actually look at all regions
  for (size_t count = 0; count < regions; ++count) {
    const size_t index = (start_index + count) % regions;
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
        for (size_t ch_index = index + 1; ch_index < regions; ++ch_index) {
          HeapRegion* chr = region_at(ch_index);

          // if the region has already been claimed or it's not
          // "continues humongous" we're done
          if (chr->claim_value() == claim_value ||
              !chr->continuesHumongous()) {
            break;
          }

          // Noone should have claimed it directly. We can given
          // that we claimed its "starts humongous" region.
          assert(chr->claim_value() != claim_value, "sanity");
          assert(chr->humongous_start_region() == r, "sanity");

          if (chr->claimHeapRegion(claim_value)) {
            // we should always be able to claim it; noone else should
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

void
G1CollectedHeap::reset_heap_region_claim_values() {
  ResetClaimValuesClosure blk;
  heap_region_iterate(&blk);
}

#ifdef ASSERT
// This checks whether all regions in the heap have the correct claim
// value. I also piggy-backed on this a check to ensure that the
// humongous_start_region() information on "continues humongous"
// regions is correct.

class CheckClaimValuesClosure : public HeapRegionClosure {
private:
  jint _claim_value;
  size_t _failures;
  HeapRegion* _sh_region;
public:
  CheckClaimValuesClosure(jint claim_value) :
    _claim_value(claim_value), _failures(0), _sh_region(NULL) { }
  bool doHeapRegion(HeapRegion* r) {
    if (r->claim_value() != _claim_value) {
      gclog_or_tty->print_cr("Region ["PTR_FORMAT","PTR_FORMAT"), "
                             "claim value = %d, should be %d",
                             r->bottom(), r->end(), r->claim_value(),
                             _claim_value);
      ++_failures;
    }
    if (!r->isHumongous()) {
      _sh_region = NULL;
    } else if (r->startsHumongous()) {
      _sh_region = r;
    } else if (r->continuesHumongous()) {
      if (r->humongous_start_region() != _sh_region) {
        gclog_or_tty->print_cr("Region ["PTR_FORMAT","PTR_FORMAT"), "
                               "HS = "PTR_FORMAT", should be "PTR_FORMAT,
                               r->bottom(), r->end(),
                               r->humongous_start_region(),
                               _sh_region);
        ++_failures;
      }
    }
    return false;
  }
  size_t failures() {
    return _failures;
  }
};

bool G1CollectedHeap::check_heap_region_claim_values(jint claim_value) {
  CheckClaimValuesClosure cl(claim_value);
  heap_region_iterate(&cl);
  return cl.failures() == 0;
}
#endif // ASSERT

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
  return _hrs->length() > 0 ? _hrs->at(0) : NULL;
}


Space* G1CollectedHeap::space_containing(const void* addr) const {
  Space* res = heap_region_containing(addr);
  if (res == NULL)
    res = perm_gen()->space_containing(addr);
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
  return HeapRegion::GrainBytes;
}

size_t G1CollectedHeap::unsafe_max_tlab_alloc(Thread* ignored) const {
  // Return the remaining space in the cur alloc region, but not less than
  // the min TLAB size.

  // Also, this value can be at most the humongous object threshold,
  // since we can't allow tlabs to grow big enough to accomodate
  // humongous objects.

  // We need to store the cur alloc region locally, since it might change
  // between when we test for NULL and when we use it later.
  ContiguousSpace* cur_alloc_space = _cur_alloc_region;
  size_t max_tlab_size = _humongous_object_threshold_in_words * wordSize;

  if (cur_alloc_space == NULL) {
    return max_tlab_size;
  } else {
    return MIN2(MAX2(cur_alloc_space->free(), (size_t)MinTLABSize),
                max_tlab_size);
  }
}

HeapWord* G1CollectedHeap::allocate_new_tlab(size_t word_size) {
  assert(!isHumongous(word_size),
         err_msg("a TLAB should not be of humongous size, "
                 "word_size = "SIZE_FORMAT, word_size));
  bool dummy;
  return G1CollectedHeap::mem_allocate(word_size, false, true, &dummy);
}

bool G1CollectedHeap::allocs_are_zero_filled() {
  return false;
}

size_t G1CollectedHeap::large_typearray_limit() {
  // FIXME
  return HeapRegion::GrainBytes/HeapWordSize;
}

size_t G1CollectedHeap::max_capacity() const {
  return g1_reserved_obj_bytes();
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

class VerifyLivenessOopClosure: public OopClosure {
  G1CollectedHeap* g1h;
public:
  VerifyLivenessOopClosure(G1CollectedHeap* _g1h) {
    g1h = _g1h;
  }
  void do_oop(narrowOop *p) { do_oop_work(p); }
  void do_oop(      oop *p) { do_oop_work(p); }

  template <class T> void do_oop_work(T *p) {
    oop obj = oopDesc::load_decode_heap_oop(p);
    guarantee(obj == NULL || !g1h->is_obj_dead(obj),
              "Dead object referenced by a not dead object");
  }
};

class VerifyObjsInRegionClosure: public ObjectClosure {
private:
  G1CollectedHeap* _g1h;
  size_t _live_bytes;
  HeapRegion *_hr;
  bool _use_prev_marking;
public:
  // use_prev_marking == true  -> use "prev" marking information,
  // use_prev_marking == false -> use "next" marking information
  VerifyObjsInRegionClosure(HeapRegion *hr, bool use_prev_marking)
    : _live_bytes(0), _hr(hr), _use_prev_marking(use_prev_marking) {
    _g1h = G1CollectedHeap::heap();
  }
  void do_object(oop o) {
    VerifyLivenessOopClosure isLive(_g1h);
    assert(o != NULL, "Huh?");
    if (!_g1h->is_obj_dead_cond(o, _use_prev_marking)) {
      o->oop_iterate(&isLive);
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
  bool _allow_dirty;
  bool _par;
  bool _use_prev_marking;
  bool _failures;
public:
  // use_prev_marking == true  -> use "prev" marking information,
  // use_prev_marking == false -> use "next" marking information
  VerifyRegionClosure(bool allow_dirty, bool par, bool use_prev_marking)
    : _allow_dirty(allow_dirty),
      _par(par),
      _use_prev_marking(use_prev_marking),
      _failures(false) {}

  bool failures() {
    return _failures;
  }

  bool doHeapRegion(HeapRegion* r) {
    guarantee(_par || r->claim_value() == HeapRegion::InitialClaimValue,
              "Should be unclaimed at verify points.");
    if (!r->continuesHumongous()) {
      bool failures = false;
      r->verify(_allow_dirty, _use_prev_marking, &failures);
      if (failures) {
        _failures = true;
      } else {
        VerifyObjsInRegionClosure not_dead_yet_cl(r, _use_prev_marking);
        r->object_iterate(&not_dead_yet_cl);
        if (r->max_live_bytes() < not_dead_yet_cl.live_bytes()) {
          gclog_or_tty->print_cr("["PTR_FORMAT","PTR_FORMAT"] "
                                 "max_live_bytes "SIZE_FORMAT" "
                                 "< calculated "SIZE_FORMAT,
                                 r->bottom(), r->end(),
                                 r->max_live_bytes(),
                                 not_dead_yet_cl.live_bytes());
          _failures = true;
        }
      }
    }
    return false; // stop the region iteration if we hit a failure
  }
};

class VerifyRootsClosure: public OopsInGenClosure {
private:
  G1CollectedHeap* _g1h;
  bool             _use_prev_marking;
  bool             _failures;
public:
  // use_prev_marking == true  -> use "prev" marking information,
  // use_prev_marking == false -> use "next" marking information
  VerifyRootsClosure(bool use_prev_marking) :
    _g1h(G1CollectedHeap::heap()),
    _use_prev_marking(use_prev_marking),
    _failures(false) { }

  bool failures() { return _failures; }

  template <class T> void do_oop_nv(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      if (_g1h->is_obj_dead_cond(obj, _use_prev_marking)) {
        gclog_or_tty->print_cr("Root location "PTR_FORMAT" "
                              "points to dead obj "PTR_FORMAT, p, (void*) obj);
        obj->print_on(gclog_or_tty);
        _failures = true;
      }
    }
  }

  void do_oop(oop* p)       { do_oop_nv(p); }
  void do_oop(narrowOop* p) { do_oop_nv(p); }
};

// This is the task used for parallel heap verification.

class G1ParVerifyTask: public AbstractGangTask {
private:
  G1CollectedHeap* _g1h;
  bool _allow_dirty;
  bool _use_prev_marking;
  bool _failures;

public:
  // use_prev_marking == true  -> use "prev" marking information,
  // use_prev_marking == false -> use "next" marking information
  G1ParVerifyTask(G1CollectedHeap* g1h, bool allow_dirty,
                  bool use_prev_marking) :
    AbstractGangTask("Parallel verify task"),
    _g1h(g1h),
    _allow_dirty(allow_dirty),
    _use_prev_marking(use_prev_marking),
    _failures(false) { }

  bool failures() {
    return _failures;
  }

  void work(int worker_i) {
    HandleMark hm;
    VerifyRegionClosure blk(_allow_dirty, true, _use_prev_marking);
    _g1h->heap_region_par_iterate_chunked(&blk, worker_i,
                                          HeapRegion::ParVerifyClaimValue);
    if (blk.failures()) {
      _failures = true;
    }
  }
};

void G1CollectedHeap::verify(bool allow_dirty, bool silent) {
  verify(allow_dirty, silent, /* use_prev_marking */ true);
}

void G1CollectedHeap::verify(bool allow_dirty,
                             bool silent,
                             bool use_prev_marking) {
  if (SafepointSynchronize::is_at_safepoint() || ! UseTLAB) {
    if (!silent) { gclog_or_tty->print("roots "); }
    VerifyRootsClosure rootsCl(use_prev_marking);
    CodeBlobToOopClosure blobsCl(&rootsCl, /*do_marking=*/ false);
    process_strong_roots(true,  // activate StrongRootsScope
                         false,
                         SharedHeap::SO_AllClasses,
                         &rootsCl,
                         &blobsCl,
                         &rootsCl);
    bool failures = rootsCl.failures();
    rem_set()->invalidate(perm_gen()->used_region(), false);
    if (!silent) { gclog_or_tty->print("heapRegions "); }
    if (GCParallelVerificationEnabled && ParallelGCThreads > 1) {
      assert(check_heap_region_claim_values(HeapRegion::InitialClaimValue),
             "sanity check");

      G1ParVerifyTask task(this, allow_dirty, use_prev_marking);
      int n_workers = workers()->total_workers();
      set_par_threads(n_workers);
      workers()->run_task(&task);
      set_par_threads(0);
      if (task.failures()) {
        failures = true;
      }

      assert(check_heap_region_claim_values(HeapRegion::ParVerifyClaimValue),
             "sanity check");

      reset_heap_region_claim_values();

      assert(check_heap_region_claim_values(HeapRegion::InitialClaimValue),
             "sanity check");
    } else {
      VerifyRegionClosure blk(allow_dirty, false, use_prev_marking);
      _hrs->iterate(&blk);
      if (blk.failures()) {
        failures = true;
      }
    }
    if (!silent) gclog_or_tty->print("remset ");
    rem_set()->verify();

    if (failures) {
      gclog_or_tty->print_cr("Heap:");
      print_on(gclog_or_tty, true /* extended */);
      gclog_or_tty->print_cr("");
#ifndef PRODUCT
      if (VerifyDuringGC && G1VerifyDuringGCPrintReachable) {
        concurrent_mark()->print_reachable("at-verification-failure",
                                           use_prev_marking, false /* all */);
      }
#endif
      gclog_or_tty->flush();
    }
    guarantee(!failures, "there should not have been any failures");
  } else {
    if (!silent) gclog_or_tty->print("(SKIPPING roots, heapRegions, remset) ");
  }
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

void G1CollectedHeap::print() const { print_on(tty); }

void G1CollectedHeap::print_on(outputStream* st) const {
  print_on(st, PrintHeapAtGCExtended);
}

void G1CollectedHeap::print_on(outputStream* st, bool extended) const {
  st->print(" %-20s", "garbage-first heap");
  st->print(" total " SIZE_FORMAT "K, used " SIZE_FORMAT "K",
            capacity()/K, used_unlocked()/K);
  st->print(" [" INTPTR_FORMAT ", " INTPTR_FORMAT ", " INTPTR_FORMAT ")",
            _g1_storage.low_boundary(),
            _g1_storage.high(),
            _g1_storage.high_boundary());
  st->cr();
  st->print("  region size " SIZE_FORMAT "K, ",
            HeapRegion::GrainBytes/K);
  size_t young_regions = _young_list->length();
  st->print(SIZE_FORMAT " young (" SIZE_FORMAT "K), ",
            young_regions, young_regions * HeapRegion::GrainBytes / K);
  size_t survivor_regions = g1_policy()->recorded_survivor_regions();
  st->print(SIZE_FORMAT " survivors (" SIZE_FORMAT "K)",
            survivor_regions, survivor_regions * HeapRegion::GrainBytes / K);
  st->cr();
  perm()->as_gen()->print_on(st);
  if (extended) {
    st->cr();
    print_on_extended(st);
  }
}

void G1CollectedHeap::print_on_extended(outputStream* st) const {
  PrintRegionClosure blk(st);
  _hrs->iterate(&blk);
}

void G1CollectedHeap::print_gc_threads_on(outputStream* st) const {
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    workers()->print_worker_threads_on(st);
  }

  _cmThread->print_on(st);
  st->cr();

  _cm->print_worker_threads_on(st);

  _cg1r->print_worker_threads_on(st);

  _czft->print_on(st);
  st->cr();
}

void G1CollectedHeap::gc_threads_do(ThreadClosure* tc) const {
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    workers()->threads_do(tc);
  }
  tc->do_thread(_cmThread);
  _cg1r->threads_do(tc);
  tc->do_thread(_czft);
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
  if (G1SummarizeZFStats) {
    ConcurrentZFThread::print_summary_info();
  }
  g1_policy()->print_yg_surv_rate_info();

  SpecializationStats::print();
}


int G1CollectedHeap::addr_to_arena_id(void* addr) const {
  HeapRegion* hr = heap_region_containing(addr);
  if (hr == NULL) {
    return 0;
  } else {
    return 1;
  }
}

G1CollectedHeap* G1CollectedHeap::heap() {
  assert(_sh->kind() == CollectedHeap::G1CollectedHeap,
         "not a garbage-first heap");
  return _g1h;
}

void G1CollectedHeap::gc_prologue(bool full /* Ignored */) {
  // always_do_update_barrier = false;
  assert(InlineCacheBuffer::is_empty(), "should have cleaned up ICBuffer");
  // Call allocation profiler
  AllocationProfiler::iterate_since_last_gc();
  // Fill TLAB's and such
  ensure_parsability(true);
}

void G1CollectedHeap::gc_epilogue(bool full /* Ignored */) {
  // FIXME: what is this about?
  // I'm ignoring the "fill_newgen()" call if "alloc_event_enabled"
  // is set.
  COMPILER2_PRESENT(assert(DerivedPointerTable::is_empty(),
                        "derived pointer present"));
  // always_do_update_barrier = true;
}

void G1CollectedHeap::do_collection_pause() {
  assert(Heap_lock->owned_by_self(), "we assume we'reholding the Heap_lock");

  // Read the GC count while holding the Heap_lock
  // we need to do this _before_ wait_for_cleanup_complete(), to
  // ensure that we do not give up the heap lock and potentially
  // pick up the wrong count
  unsigned int gc_count_before = SharedHeap::heap()->total_collections();

  // Don't want to do a GC pause while cleanup is being completed!
  wait_for_cleanup_complete();

  g1_policy()->record_stop_world_start();
  {
    MutexUnlocker mu(Heap_lock);  // give up heap lock, execute gets it back
    VM_G1IncCollectionPause op(gc_count_before,
                               false, /* should_initiate_conc_mark */
                               g1_policy()->max_pause_time_ms(),
                               GCCause::_g1_inc_collection_pause);
    VMThread::execute(&op);
  }
}

void
G1CollectedHeap::doConcurrentMark() {
  MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
  if (!_cmThread->in_progress()) {
    _cmThread->set_started();
    CGC_lock->notify();
  }
}

class VerifyMarkedObjsClosure: public ObjectClosure {
    G1CollectedHeap* _g1h;
    public:
    VerifyMarkedObjsClosure(G1CollectedHeap* g1h) : _g1h(g1h) {}
    void do_object(oop obj) {
      assert(obj->mark()->is_marked() ? !_g1h->is_obj_dead(obj) : true,
             "markandsweep mark should agree with concurrent deadness");
    }
};

void
G1CollectedHeap::checkConcurrentMark() {
    VerifyMarkedObjsClosure verifycl(this);
    //    MutexLockerEx x(getMarkBitMapLock(),
    //              Mutex::_no_safepoint_check_flag);
    object_iterate(&verifycl, false);
}

void G1CollectedHeap::do_sync_mark() {
  _cm->checkpointRootsInitial();
  _cm->markFromRoots();
  _cm->checkpointRootsFinal(false);
}

// <NEW PREDICTION>

double G1CollectedHeap::predict_region_elapsed_time_ms(HeapRegion *hr,
                                                       bool young) {
  return _g1_policy->predict_region_elapsed_time_ms(hr, young);
}

void G1CollectedHeap::check_if_region_is_too_expensive(double
                                                           predicted_time_ms) {
  _g1_policy->check_if_region_is_too_expensive(predicted_time_ms);
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
  return buffer_size * buffer_num + extra_cards;
}

size_t G1CollectedHeap::max_pending_card_num() {
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  size_t buffer_size = dcqs.buffer_size();
  size_t buffer_num  = dcqs.completed_buffers_num();
  int thread_num  = Threads::number_of_threads();
  return (buffer_num + thread_num) * buffer_size;
}

size_t G1CollectedHeap::cards_scanned() {
  return g1_rem_set()->cardsScanned();
}

void
G1CollectedHeap::setup_surviving_young_words() {
  guarantee( _surviving_young_words == NULL, "pre-condition" );
  size_t array_length = g1_policy()->young_cset_length();
  _surviving_young_words = NEW_C_HEAP_ARRAY(size_t, array_length);
  if (_surviving_young_words == NULL) {
    vm_exit_out_of_memory(sizeof(size_t) * array_length,
                          "Not enough space for young surv words summary.");
  }
  memset(_surviving_young_words, 0, array_length * sizeof(size_t));
#ifdef ASSERT
  for (size_t i = 0;  i < array_length; ++i) {
    assert( _surviving_young_words[i] == 0, "memset above" );
  }
#endif // !ASSERT
}

void
G1CollectedHeap::update_surviving_young_words(size_t* surv_young_words) {
  MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
  size_t array_length = g1_policy()->young_cset_length();
  for (size_t i = 0; i < array_length; ++i)
    _surviving_young_words[i] += surv_young_words[i];
}

void
G1CollectedHeap::cleanup_surviving_young_words() {
  guarantee( _surviving_young_words != NULL, "pre-condition" );
  FREE_C_HEAP_ARRAY(size_t, _surviving_young_words);
  _surviving_young_words = NULL;
}

// </NEW PREDICTION>

struct PrepareForRSScanningClosure : public HeapRegionClosure {
  bool doHeapRegion(HeapRegion *r) {
    r->rem_set()->set_iter_claimed(0);
    return false;
  }
};

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

void
G1CollectedHeap::do_collection_pause_at_safepoint(double target_pause_time_ms) {
  if (GC_locker::check_active_before_gc()) {
    return; // GC is disabled (e.g. JNI GetXXXCritical operation)
  }

  if (PrintHeapAtGC) {
    Universe::print_heap_before_gc();
  }

  {
    ResourceMark rm;

    // This call will decide whether this pause is an initial-mark
    // pause. If it is, during_initial_mark_pause() will return true
    // for the duration of this pause.
    g1_policy()->decide_on_conc_mark_initiation();

    char verbose_str[128];
    sprintf(verbose_str, "GC pause ");
    if (g1_policy()->in_young_gc_mode()) {
      if (g1_policy()->full_young_gcs())
        strcat(verbose_str, "(young)");
      else
        strcat(verbose_str, "(partial)");
    }
    if (g1_policy()->during_initial_mark_pause()) {
      strcat(verbose_str, " (initial-mark)");
      // We are about to start a marking cycle, so we increment the
      // full collection counter.
      increment_total_full_collections();
    }

    // if PrintGCDetails is on, we'll print long statistics information
    // in the collector policy code, so let's not print this as the output
    // is messy if we do.
    gclog_or_tty->date_stamp(PrintGC && PrintGCDateStamps);
    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    TraceTime t(verbose_str, PrintGC && !PrintGCDetails, true, gclog_or_tty);

    TraceMemoryManagerStats tms(false /* fullGC */);

    assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
    assert(Thread::current() == VMThread::vm_thread(), "should be in vm thread");
    guarantee(!is_gc_active(), "collection is not reentrant");
    assert(regions_accounted_for(), "Region leakage!");

    increment_gc_time_stamp();

    if (g1_policy()->in_young_gc_mode()) {
      assert(check_young_list_well_formed(),
             "young list should be well formed");
    }

    { // Call to jvmpi::post_class_unload_events must occur outside of active GC
      IsGCActiveMark x;

      gc_prologue(false);
      increment_total_collections(false /* full gc */);

#if G1_REM_SET_LOGGING
      gclog_or_tty->print_cr("\nJust chose CS, heap:");
      print();
#endif

      if (VerifyBeforeGC && total_collections() >= VerifyGCStartAt) {
        HandleMark hm;  // Discard invalid handles created during verification
        prepare_for_verify();
        gclog_or_tty->print(" VerifyBeforeGC:");
        Universe::verify(false);
      }

      COMPILER2_PRESENT(DerivedPointerTable::clear());

      // We want to turn off ref discovery, if necessary, and turn it back on
      // on again later if we do. XXX Dubious: why is discovery disabled?
      bool was_enabled = ref_processor()->discovery_enabled();
      if (was_enabled) ref_processor()->disable_discovery();

      // Forget the current alloc region (we might even choose it to be part
      // of the collection set!).
      abandon_cur_alloc_region();

      // The elapsed time induced by the start time below deliberately elides
      // the possible verification above.
      double start_time_sec = os::elapsedTime();
      size_t start_used_bytes = used();

#if YOUNG_LIST_VERBOSE
      gclog_or_tty->print_cr("\nBefore recording pause start.\nYoung_list:");
      _young_list->print();
      g1_policy()->print_collection_set(g1_policy()->inc_cset_head(), gclog_or_tty);
#endif // YOUNG_LIST_VERBOSE

      g1_policy()->record_collection_pause_start(start_time_sec,
                                                 start_used_bytes);

#if YOUNG_LIST_VERBOSE
      gclog_or_tty->print_cr("\nAfter recording pause start.\nYoung_list:");
      _young_list->print();
#endif // YOUNG_LIST_VERBOSE

      if (g1_policy()->during_initial_mark_pause()) {
        concurrent_mark()->checkpointRootsInitialPre();
      }
      save_marks();

      // We must do this before any possible evacuation that should propagate
      // marks.
      if (mark_in_progress()) {
        double start_time_sec = os::elapsedTime();

        _cm->drainAllSATBBuffers();
        double finish_mark_ms = (os::elapsedTime() - start_time_sec) * 1000.0;
        g1_policy()->record_satb_drain_time(finish_mark_ms);
      }
      // Record the number of elements currently on the mark stack, so we
      // only iterate over these.  (Since evacuation may add to the mark
      // stack, doing more exposes race conditions.)  If no mark is in
      // progress, this will be zero.
      _cm->set_oops_do_bound();

      assert(regions_accounted_for(), "Region leakage.");

      if (mark_in_progress())
        concurrent_mark()->newCSet();

#if YOUNG_LIST_VERBOSE
      gclog_or_tty->print_cr("\nBefore choosing collection set.\nYoung_list:");
      _young_list->print();
      g1_policy()->print_collection_set(g1_policy()->inc_cset_head(), gclog_or_tty);
#endif // YOUNG_LIST_VERBOSE

      g1_policy()->choose_collection_set(target_pause_time_ms);

      // Nothing to do if we were unable to choose a collection set.
#if G1_REM_SET_LOGGING
      gclog_or_tty->print_cr("\nAfter pause, heap:");
      print();
#endif
      PrepareForRSScanningClosure prepare_for_rs_scan;
      collection_set_iterate(&prepare_for_rs_scan);

      setup_surviving_young_words();

      // Set up the gc allocation regions.
      get_gc_alloc_regions();

      // Actually do the work...
      evacuate_collection_set();

      free_collection_set(g1_policy()->collection_set());
      g1_policy()->clear_collection_set();

      cleanup_surviving_young_words();

      // Start a new incremental collection set for the next pause.
      g1_policy()->start_incremental_cset_building();

      // Clear the _cset_fast_test bitmap in anticipation of adding
      // regions to the incremental collection set for the next
      // evacuation pause.
      clear_cset_fast_test();

      if (g1_policy()->in_young_gc_mode()) {
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
      }

      if (evacuation_failed()) {
        _summary_bytes_used = recalculate_used();
      } else {
        // The "used" of the the collection set have already been subtracted
        // when they were freed.  Add in the bytes evacuated.
        _summary_bytes_used += g1_policy()->bytes_in_to_space();
      }

      if (g1_policy()->in_young_gc_mode() &&
          g1_policy()->during_initial_mark_pause()) {
        concurrent_mark()->checkpointRootsInitialPost();
        set_marking_started();
        // CAUTION: after the doConcurrentMark() call below,
        // the concurrent marking thread(s) could be running
        // concurrently with us. Make sure that anything after
        // this point does not assume that we are the only GC thread
        // running. Note: of course, the actual marking work will
        // not start until the safepoint itself is released in
        // ConcurrentGCThread::safepoint_desynchronize().
        doConcurrentMark();
      }

#if YOUNG_LIST_VERBOSE
      gclog_or_tty->print_cr("\nEnd of the pause.\nYoung_list:");
      _young_list->print();
      g1_policy()->print_collection_set(g1_policy()->inc_cset_head(), gclog_or_tty);
#endif // YOUNG_LIST_VERBOSE

      double end_time_sec = os::elapsedTime();
      double pause_time_ms = (end_time_sec - start_time_sec) * MILLIUNITS;
      g1_policy()->record_pause_time_ms(pause_time_ms);
      g1_policy()->record_collection_pause_end();

      assert(regions_accounted_for(), "Region leakage.");

      MemoryService::track_memory_usage();

      if (VerifyAfterGC && total_collections() >= VerifyGCStartAt) {
        HandleMark hm;  // Discard invalid handles created during verification
        gclog_or_tty->print(" VerifyAfterGC:");
        prepare_for_verify();
        Universe::verify(false);
      }

      if (was_enabled) ref_processor()->enable_discovery();

      {
        size_t expand_bytes = g1_policy()->expansion_amount();
        if (expand_bytes > 0) {
          size_t bytes_before = capacity();
          expand(expand_bytes);
        }
      }

      if (mark_in_progress()) {
        concurrent_mark()->update_g1_committed();
      }

#ifdef TRACESPINNING
      ParallelTaskTerminator::print_termination_counts();
#endif

      gc_epilogue(false);
    }

    assert(verify_region_lists(), "Bad region lists.");

    if (ExitAfterGCNum > 0 && total_collections() == ExitAfterGCNum) {
      gclog_or_tty->print_cr("Stopping after GC #%d", ExitAfterGCNum);
      print_tracing_info();
      vm_exit(-1);
    }
  }

  TASKQUEUE_STATS_ONLY(if (ParallelGCVerbose) print_taskqueue_stats());
  TASKQUEUE_STATS_ONLY(reset_taskqueue_stats());

  if (PrintHeapAtGC) {
    Universe::print_heap_after_gc();
  }
  if (G1SummarizeRSetStats &&
      (G1SummarizeRSetStatsPeriod > 0) &&
      (total_collections() % G1SummarizeRSetStatsPeriod == 0)) {
    g1_rem_set()->print_summary_info();
  }
}

size_t G1CollectedHeap::desired_plab_sz(GCAllocPurpose purpose)
{
  size_t gclab_word_size;
  switch (purpose) {
    case GCAllocForSurvived:
      gclab_word_size = YoungPLABSize;
      break;
    case GCAllocForTenured:
      gclab_word_size = OldPLABSize;
      break;
    default:
      assert(false, "unknown GCAllocPurpose");
      gclab_word_size = OldPLABSize;
      break;
  }
  return gclab_word_size;
}


void G1CollectedHeap::set_gc_alloc_region(int purpose, HeapRegion* r) {
  assert(purpose >= 0 && purpose < GCAllocPurposeCount, "invalid purpose");
  // make sure we don't call set_gc_alloc_region() multiple times on
  // the same region
  assert(r == NULL || !r->is_gc_alloc_region(),
         "shouldn't already be a GC alloc region");
  assert(r == NULL || !r->isHumongous(),
         "humongous regions shouldn't be used as GC alloc regions");

  HeapWord* original_top = NULL;
  if (r != NULL)
    original_top = r->top();

  // We will want to record the used space in r as being there before gc.
  // One we install it as a GC alloc region it's eligible for allocation.
  // So record it now and use it later.
  size_t r_used = 0;
  if (r != NULL) {
    r_used = r->used();

    if (G1CollectedHeap::use_parallel_gc_threads()) {
      // need to take the lock to guard against two threads calling
      // get_gc_alloc_region concurrently (very unlikely but...)
      MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
      r->save_marks();
    }
  }
  HeapRegion* old_alloc_region = _gc_alloc_regions[purpose];
  _gc_alloc_regions[purpose] = r;
  if (old_alloc_region != NULL) {
    // Replace aliases too.
    for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
      if (_gc_alloc_regions[ap] == old_alloc_region) {
        _gc_alloc_regions[ap] = r;
      }
    }
  }
  if (r != NULL) {
    push_gc_alloc_region(r);
    if (mark_in_progress() && original_top != r->next_top_at_mark_start()) {
      // We are using a region as a GC alloc region after it has been used
      // as a mutator allocation region during the current marking cycle.
      // The mutator-allocated objects are currently implicitly marked, but
      // when we move hr->next_top_at_mark_start() forward at the the end
      // of the GC pause, they won't be.  We therefore mark all objects in
      // the "gap".  We do this object-by-object, since marking densely
      // does not currently work right with marking bitmap iteration.  This
      // means we rely on TLAB filling at the start of pauses, and no
      // "resuscitation" of filled TLAB's.  If we want to do this, we need
      // to fix the marking bitmap iteration.
      HeapWord* curhw = r->next_top_at_mark_start();
      HeapWord* t = original_top;

      while (curhw < t) {
        oop cur = (oop)curhw;
        // We'll assume parallel for generality.  This is rare code.
        concurrent_mark()->markAndGrayObjectIfNecessary(cur); // can't we just mark them?
        curhw = curhw + cur->size();
      }
      assert(curhw == t, "Should have parsed correctly.");
    }
    if (G1PolicyVerbose > 1) {
      gclog_or_tty->print("New alloc region ["PTR_FORMAT", "PTR_FORMAT", " PTR_FORMAT") "
                          "for survivors:", r->bottom(), original_top, r->end());
      r->print();
    }
    g1_policy()->record_before_bytes(r_used);
  }
}

void G1CollectedHeap::push_gc_alloc_region(HeapRegion* hr) {
  assert(Thread::current()->is_VM_thread() ||
         par_alloc_during_gc_lock()->owned_by_self(), "Precondition");
  assert(!hr->is_gc_alloc_region() && !hr->in_collection_set(),
         "Precondition.");
  hr->set_is_gc_alloc_region(true);
  hr->set_next_gc_alloc_region(_gc_alloc_region_list);
  _gc_alloc_region_list = hr;
}

#ifdef G1_DEBUG
class FindGCAllocRegion: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion* r) {
    if (r->is_gc_alloc_region()) {
      gclog_or_tty->print_cr("Region %d ["PTR_FORMAT"...] is still a gc_alloc_region.",
                             r->hrs_index(), r->bottom());
    }
    return false;
  }
};
#endif // G1_DEBUG

void G1CollectedHeap::forget_alloc_region_list() {
  assert(Thread::current()->is_VM_thread(), "Precondition");
  while (_gc_alloc_region_list != NULL) {
    HeapRegion* r = _gc_alloc_region_list;
    assert(r->is_gc_alloc_region(), "Invariant.");
    // We need HeapRegion::oops_on_card_seq_iterate_careful() to work on
    // newly allocated data in order to be able to apply deferred updates
    // before the GC is done for verification purposes (i.e to allow
    // G1HRRSFlushLogBuffersOnVerify). It's safe thing to do after the
    // collection.
    r->ContiguousSpace::set_saved_mark();
    _gc_alloc_region_list = r->next_gc_alloc_region();
    r->set_next_gc_alloc_region(NULL);
    r->set_is_gc_alloc_region(false);
    if (r->is_survivor()) {
      if (r->is_empty()) {
        r->set_not_young();
      } else {
        _young_list->add_survivor_region(r);
      }
    }
    if (r->is_empty()) {
      ++_free_regions;
    }
  }
#ifdef G1_DEBUG
  FindGCAllocRegion fa;
  heap_region_iterate(&fa);
#endif // G1_DEBUG
}


bool G1CollectedHeap::check_gc_alloc_regions() {
  // TODO: allocation regions check
  return true;
}

void G1CollectedHeap::get_gc_alloc_regions() {
  // First, let's check that the GC alloc region list is empty (it should)
  assert(_gc_alloc_region_list == NULL, "invariant");

  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    assert(_gc_alloc_regions[ap] == NULL, "invariant");
    assert(_gc_alloc_region_counts[ap] == 0, "invariant");

    // Create new GC alloc regions.
    HeapRegion* alloc_region = _retained_gc_alloc_regions[ap];
    _retained_gc_alloc_regions[ap] = NULL;

    if (alloc_region != NULL) {
      assert(_retain_gc_alloc_region[ap], "only way to retain a GC region");

      // let's make sure that the GC alloc region is not tagged as such
      // outside a GC operation
      assert(!alloc_region->is_gc_alloc_region(), "sanity");

      if (alloc_region->in_collection_set() ||
          alloc_region->top() == alloc_region->end() ||
          alloc_region->top() == alloc_region->bottom() ||
          alloc_region->isHumongous()) {
        // we will discard the current GC alloc region if
        // * it's in the collection set (it can happen!),
        // * it's already full (no point in using it),
        // * it's empty (this means that it was emptied during
        // a cleanup and it should be on the free list now), or
        // * it's humongous (this means that it was emptied
        // during a cleanup and was added to the free list, but
        // has been subseqently used to allocate a humongous
        // object that may be less than the region size).

        alloc_region = NULL;
      }
    }

    if (alloc_region == NULL) {
      // we will get a new GC alloc region
      alloc_region = newAllocRegionWithExpansion(ap, 0);
    } else {
      // the region was retained from the last collection
      ++_gc_alloc_region_counts[ap];
      if (G1PrintHeapRegions) {
        gclog_or_tty->print_cr("new alloc region %d:["PTR_FORMAT", "PTR_FORMAT"], "
                               "top "PTR_FORMAT,
                               alloc_region->hrs_index(), alloc_region->bottom(), alloc_region->end(), alloc_region->top());
      }
    }

    if (alloc_region != NULL) {
      assert(_gc_alloc_regions[ap] == NULL, "pre-condition");
      set_gc_alloc_region(ap, alloc_region);
    }

    assert(_gc_alloc_regions[ap] == NULL ||
           _gc_alloc_regions[ap]->is_gc_alloc_region(),
           "the GC alloc region should be tagged as such");
    assert(_gc_alloc_regions[ap] == NULL ||
           _gc_alloc_regions[ap] == _gc_alloc_region_list,
           "the GC alloc region should be the same as the GC alloc list head");
  }
  // Set alternative regions for allocation purposes that have reached
  // their limit.
  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    GCAllocPurpose alt_purpose = g1_policy()->alternative_purpose(ap);
    if (_gc_alloc_regions[ap] == NULL && alt_purpose != ap) {
      _gc_alloc_regions[ap] = _gc_alloc_regions[alt_purpose];
    }
  }
  assert(check_gc_alloc_regions(), "alloc regions messed up");
}

void G1CollectedHeap::release_gc_alloc_regions(bool totally) {
  // We keep a separate list of all regions that have been alloc regions in
  // the current collection pause. Forget that now. This method will
  // untag the GC alloc regions and tear down the GC alloc region
  // list. It's desirable that no regions are tagged as GC alloc
  // outside GCs.
  forget_alloc_region_list();

  // The current alloc regions contain objs that have survived
  // collection. Make them no longer GC alloc regions.
  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    HeapRegion* r = _gc_alloc_regions[ap];
    _retained_gc_alloc_regions[ap] = NULL;
    _gc_alloc_region_counts[ap] = 0;

    if (r != NULL) {
      // we retain nothing on _gc_alloc_regions between GCs
      set_gc_alloc_region(ap, NULL);

      if (r->is_empty()) {
        // we didn't actually allocate anything in it; let's just put
        // it on the free list
        MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
        r->set_zero_fill_complete();
        put_free_region_on_list_locked(r);
      } else if (_retain_gc_alloc_region[ap] && !totally) {
        // retain it so that we can use it at the beginning of the next GC
        _retained_gc_alloc_regions[ap] = r;
      }
    }
  }
}

#ifndef PRODUCT
// Useful for debugging

void G1CollectedHeap::print_gc_alloc_regions() {
  gclog_or_tty->print_cr("GC alloc regions");
  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    HeapRegion* r = _gc_alloc_regions[ap];
    if (r == NULL) {
      gclog_or_tty->print_cr("  %2d : "PTR_FORMAT, ap, NULL);
    } else {
      gclog_or_tty->print_cr("  %2d : "PTR_FORMAT" "SIZE_FORMAT,
                             ap, r->bottom(), r->used());
    }
  }
}
#endif // PRODUCT

void G1CollectedHeap::init_for_evac_failure(OopsInHeapRegionClosure* cl) {
  _drain_in_progress = false;
  set_evac_failure_closure(cl);
  _evac_failure_scan_stack = new (ResourceObj::C_HEAP) GrowableArray<oop>(40, true);
}

void G1CollectedHeap::finalize_for_evac_failure() {
  assert(_evac_failure_scan_stack != NULL &&
         _evac_failure_scan_stack->length() == 0,
         "Postcondition");
  assert(!_drain_in_progress, "Postcondition");
  delete _evac_failure_scan_stack;
  _evac_failure_scan_stack = NULL;
}



// *** Sequential G1 Evacuation

HeapWord* G1CollectedHeap::allocate_during_gc(GCAllocPurpose purpose, size_t word_size) {
  HeapRegion* alloc_region = _gc_alloc_regions[purpose];
  // let the caller handle alloc failure
  if (alloc_region == NULL) return NULL;
  assert(isHumongous(word_size) || !alloc_region->isHumongous(),
         "Either the object is humongous or the region isn't");
  HeapWord* block = alloc_region->allocate(word_size);
  if (block == NULL) {
    block = allocate_during_gc_slow(purpose, alloc_region, false, word_size);
  }
  return block;
}

class G1IsAliveClosure: public BoolObjectClosure {
  G1CollectedHeap* _g1;
public:
  G1IsAliveClosure(G1CollectedHeap* g1) : _g1(g1) {}
  void do_object(oop p) { assert(false, "Do not call."); }
  bool do_object_b(oop p) {
    // It is reachable if it is outside the collection set, or is inside
    // and forwarded.

#ifdef G1_DEBUG
    gclog_or_tty->print_cr("is alive "PTR_FORMAT" in CS %d forwarded %d overall %d",
                           (void*) p, _g1->obj_in_cs(p), p->is_forwarded(),
                           !_g1->obj_in_cs(p) || p->is_forwarded());
#endif // G1_DEBUG

    return !_g1->obj_in_cs(p) || p->is_forwarded();
  }
};

class G1KeepAliveClosure: public OopClosure {
  G1CollectedHeap* _g1;
public:
  G1KeepAliveClosure(G1CollectedHeap* g1) : _g1(g1) {}
  void do_oop(narrowOop* p) { guarantee(false, "Not needed"); }
  void do_oop(      oop* p) {
    oop obj = *p;
#ifdef G1_DEBUG
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr("keep alive *"PTR_FORMAT" = "PTR_FORMAT" "PTR_FORMAT,
                             p, (void*) obj, (void*) *p);
    }
#endif // G1_DEBUG

    if (_g1->obj_in_cs(obj)) {
      assert( obj->is_forwarded(), "invariant" );
      *p = obj->forwardee();
#ifdef G1_DEBUG
      gclog_or_tty->print_cr("     in CSet: moved "PTR_FORMAT" -> "PTR_FORMAT,
                             (void*) obj, (void*) *p);
#endif // G1_DEBUG
    }
  }
};

class UpdateRSetDeferred : public OopsInHeapRegionClosure {
private:
  G1CollectedHeap* _g1;
  DirtyCardQueue *_dcq;
  CardTableModRefBS* _ct_bs;

public:
  UpdateRSetDeferred(G1CollectedHeap* g1, DirtyCardQueue* dcq) :
    _g1(g1), _ct_bs((CardTableModRefBS*)_g1->barrier_set()), _dcq(dcq) {}

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }
  template <class T> void do_oop_work(T* p) {
    assert(_from->is_in_reserved(p), "paranoia");
    if (!_from->is_in_reserved(oopDesc::load_decode_heap_oop(p)) &&
        !_from->is_survivor()) {
      size_t card_index = _ct_bs->index_for(p);
      if (_ct_bs->mark_card_deferred(card_index)) {
        _dcq->enqueue((jbyte*)_ct_bs->byte_for_index(card_index));
      }
    }
  }
};

class RemoveSelfPointerClosure: public ObjectClosure {
private:
  G1CollectedHeap* _g1;
  ConcurrentMark* _cm;
  HeapRegion* _hr;
  size_t _prev_marked_bytes;
  size_t _next_marked_bytes;
  OopsInHeapRegionClosure *_cl;
public:
  RemoveSelfPointerClosure(G1CollectedHeap* g1, OopsInHeapRegionClosure* cl) :
    _g1(g1), _cm(_g1->concurrent_mark()),  _prev_marked_bytes(0),
    _next_marked_bytes(0), _cl(cl) {}

  size_t prev_marked_bytes() { return _prev_marked_bytes; }
  size_t next_marked_bytes() { return _next_marked_bytes; }

  // The original idea here was to coalesce evacuated and dead objects.
  // However that caused complications with the block offset table (BOT).
  // In particular if there were two TLABs, one of them partially refined.
  // |----- TLAB_1--------|----TLAB_2-~~~(partially refined part)~~~|
  // The BOT entries of the unrefined part of TLAB_2 point to the start
  // of TLAB_2. If the last object of the TLAB_1 and the first object
  // of TLAB_2 are coalesced, then the cards of the unrefined part
  // would point into middle of the filler object.
  //
  // The current approach is to not coalesce and leave the BOT contents intact.
  void do_object(oop obj) {
    if (obj->is_forwarded() && obj->forwardee() == obj) {
      // The object failed to move.
      assert(!_g1->is_obj_dead(obj), "We should not be preserving dead objs.");
      _cm->markPrev(obj);
      assert(_cm->isPrevMarked(obj), "Should be marked!");
      _prev_marked_bytes += (obj->size() * HeapWordSize);
      if (_g1->mark_in_progress() && !_g1->is_obj_ill(obj)) {
        _cm->markAndGrayObjectIfNecessary(obj);
      }
      obj->set_mark(markOopDesc::prototype());
      // While we were processing RSet buffers during the
      // collection, we actually didn't scan any cards on the
      // collection set, since we didn't want to update remebered
      // sets with entries that point into the collection set, given
      // that live objects fromthe collection set are about to move
      // and such entries will be stale very soon. This change also
      // dealt with a reliability issue which involved scanning a
      // card in the collection set and coming across an array that
      // was being chunked and looking malformed. The problem is
      // that, if evacuation fails, we might have remembered set
      // entries missing given that we skipped cards on the
      // collection set. So, we'll recreate such entries now.
      obj->oop_iterate(_cl);
      assert(_cm->isPrevMarked(obj), "Should be marked!");
    } else {
      // The object has been either evacuated or is dead. Fill it with a
      // dummy object.
      MemRegion mr((HeapWord*)obj, obj->size());
      CollectedHeap::fill_with_object(mr);
      _cm->clearRangeBothMaps(mr);
    }
  }
};

void G1CollectedHeap::remove_self_forwarding_pointers() {
  UpdateRSetImmediate immediate_update(_g1h->g1_rem_set());
  DirtyCardQueue dcq(&_g1h->dirty_card_queue_set());
  UpdateRSetDeferred deferred_update(_g1h, &dcq);
  OopsInHeapRegionClosure *cl;
  if (G1DeferredRSUpdate) {
    cl = &deferred_update;
  } else {
    cl = &immediate_update;
  }
  HeapRegion* cur = g1_policy()->collection_set();
  while (cur != NULL) {
    assert(g1_policy()->assertMarkedBytesDataOK(), "Should be!");

    RemoveSelfPointerClosure rspc(_g1h, cl);
    if (cur->evacuation_failed()) {
      assert(cur->in_collection_set(), "bad CS");
      cl->set_region(cur);
      cur->object_iterate(&rspc);

      // A number of manipulations to make the TAMS be the current top,
      // and the marked bytes be the ones observed in the iteration.
      if (_g1h->concurrent_mark()->at_least_one_mark_complete()) {
        // The comments below are the postconditions achieved by the
        // calls.  Note especially the last such condition, which says that
        // the count of marked bytes has been properly restored.
        cur->note_start_of_marking(false);
        // _next_top_at_mark_start == top, _next_marked_bytes == 0
        cur->add_to_marked_bytes(rspc.prev_marked_bytes());
        // _next_marked_bytes == prev_marked_bytes.
        cur->note_end_of_marking();
        // _prev_top_at_mark_start == top(),
        // _prev_marked_bytes == prev_marked_bytes
      }
      // If there is no mark in progress, we modified the _next variables
      // above needlessly, but harmlessly.
      if (_g1h->mark_in_progress()) {
        cur->note_start_of_marking(false);
        // _next_top_at_mark_start == top, _next_marked_bytes == 0
        // _next_marked_bytes == next_marked_bytes.
      }

      // Now make sure the region has the right index in the sorted array.
      g1_policy()->note_change_in_marked_bytes(cur);
    }
    cur = cur->next_in_collection_set();
  }
  assert(g1_policy()->assertMarkedBytesDataOK(), "Should be!");

  // Now restore saved marks, if any.
  if (_objs_with_preserved_marks != NULL) {
    assert(_preserved_marks_of_objs != NULL, "Both or none.");
    assert(_objs_with_preserved_marks->length() ==
           _preserved_marks_of_objs->length(), "Both or none.");
    guarantee(_objs_with_preserved_marks->length() ==
              _preserved_marks_of_objs->length(), "Both or none.");
    for (int i = 0; i < _objs_with_preserved_marks->length(); i++) {
      oop obj   = _objs_with_preserved_marks->at(i);
      markOop m = _preserved_marks_of_objs->at(i);
      obj->set_mark(m);
    }
    // Delete the preserved marks growable arrays (allocated on the C heap).
    delete _objs_with_preserved_marks;
    delete _preserved_marks_of_objs;
    _objs_with_preserved_marks = NULL;
    _preserved_marks_of_objs = NULL;
  }
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

void G1CollectedHeap::handle_evacuation_failure(oop old) {
  markOop m = old->mark();
  // forward to self
  assert(!old->is_forwarded(), "precondition");

  old->forward_to(old);
  handle_evacuation_failure_common(old, m);
}

oop
G1CollectedHeap::handle_evacuation_failure_par(OopsInHeapRegionClosure* cl,
                                               oop old) {
  markOop m = old->mark();
  oop forward_ptr = old->forward_to_atomic(old);
  if (forward_ptr == NULL) {
    // Forward-to-self succeeded.
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
    // Someone else had a place to copy it.
    return forward_ptr;
  }
}

void G1CollectedHeap::handle_evacuation_failure_common(oop old, markOop m) {
  set_evacuation_failed(true);

  preserve_mark_if_necessary(old, m);

  HeapRegion* r = heap_region_containing(old);
  if (!r->evacuation_failed()) {
    r->set_evacuation_failed(true);
    if (G1PrintHeapRegions) {
      gclog_or_tty->print("overflow in heap region "PTR_FORMAT" "
                          "["PTR_FORMAT","PTR_FORMAT")\n",
                          r, r->bottom(), r->end());
    }
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
  if (m != markOopDesc::prototype()) {
    if (_objs_with_preserved_marks == NULL) {
      assert(_preserved_marks_of_objs == NULL, "Both or none.");
      _objs_with_preserved_marks =
        new (ResourceObj::C_HEAP) GrowableArray<oop>(40, true);
      _preserved_marks_of_objs =
        new (ResourceObj::C_HEAP) GrowableArray<markOop>(40, true);
    }
    _objs_with_preserved_marks->push(obj);
    _preserved_marks_of_objs->push(m);
  }
}

// *** Parallel G1 Evacuation

HeapWord* G1CollectedHeap::par_allocate_during_gc(GCAllocPurpose purpose,
                                                  size_t word_size) {
  assert(!isHumongous(word_size),
         err_msg("we should not be seeing humongous allocation requests "
                 "during GC, word_size = "SIZE_FORMAT, word_size));

  HeapRegion* alloc_region = _gc_alloc_regions[purpose];
  // let the caller handle alloc failure
  if (alloc_region == NULL) return NULL;

  HeapWord* block = alloc_region->par_allocate(word_size);
  if (block == NULL) {
    MutexLockerEx x(par_alloc_during_gc_lock(),
                    Mutex::_no_safepoint_check_flag);
    block = allocate_during_gc_slow(purpose, alloc_region, true, word_size);
  }
  return block;
}

void G1CollectedHeap::retire_alloc_region(HeapRegion* alloc_region,
                                            bool par) {
  // Another thread might have obtained alloc_region for the given
  // purpose, and might be attempting to allocate in it, and might
  // succeed.  Therefore, we can't do the "finalization" stuff on the
  // region below until we're sure the last allocation has happened.
  // We ensure this by allocating the remaining space with a garbage
  // object.
  if (par) par_allocate_remaining_space(alloc_region);
  // Now we can do the post-GC stuff on the region.
  alloc_region->note_end_of_copying();
  g1_policy()->record_after_bytes(alloc_region->used());
}

HeapWord*
G1CollectedHeap::allocate_during_gc_slow(GCAllocPurpose purpose,
                                         HeapRegion*    alloc_region,
                                         bool           par,
                                         size_t         word_size) {
  assert(!isHumongous(word_size),
         err_msg("we should not be seeing humongous allocation requests "
                 "during GC, word_size = "SIZE_FORMAT, word_size));

  HeapWord* block = NULL;
  // In the parallel case, a previous thread to obtain the lock may have
  // already assigned a new gc_alloc_region.
  if (alloc_region != _gc_alloc_regions[purpose]) {
    assert(par, "But should only happen in parallel case.");
    alloc_region = _gc_alloc_regions[purpose];
    if (alloc_region == NULL) return NULL;
    block = alloc_region->par_allocate(word_size);
    if (block != NULL) return block;
    // Otherwise, continue; this new region is empty, too.
  }
  assert(alloc_region != NULL, "We better have an allocation region");
  retire_alloc_region(alloc_region, par);

  if (_gc_alloc_region_counts[purpose] >= g1_policy()->max_regions(purpose)) {
    // Cannot allocate more regions for the given purpose.
    GCAllocPurpose alt_purpose = g1_policy()->alternative_purpose(purpose);
    // Is there an alternative?
    if (purpose != alt_purpose) {
      HeapRegion* alt_region = _gc_alloc_regions[alt_purpose];
      // Has not the alternative region been aliased?
      if (alloc_region != alt_region && alt_region != NULL) {
        // Try to allocate in the alternative region.
        if (par) {
          block = alt_region->par_allocate(word_size);
        } else {
          block = alt_region->allocate(word_size);
        }
        // Make an alias.
        _gc_alloc_regions[purpose] = _gc_alloc_regions[alt_purpose];
        if (block != NULL) {
          return block;
        }
        retire_alloc_region(alt_region, par);
      }
      // Both the allocation region and the alternative one are full
      // and aliased, replace them with a new allocation region.
      purpose = alt_purpose;
    } else {
      set_gc_alloc_region(purpose, NULL);
      return NULL;
    }
  }

  // Now allocate a new region for allocation.
  alloc_region = newAllocRegionWithExpansion(purpose, word_size, false /*zero_filled*/);

  // let the caller handle alloc failure
  if (alloc_region != NULL) {

    assert(check_gc_alloc_regions(), "alloc regions messed up");
    assert(alloc_region->saved_mark_at_top(),
           "Mark should have been saved already.");
    // We used to assert that the region was zero-filled here, but no
    // longer.

    // This must be done last: once it's installed, other regions may
    // allocate in it (without holding the lock.)
    set_gc_alloc_region(purpose, alloc_region);

    if (par) {
      block = alloc_region->par_allocate(word_size);
    } else {
      block = alloc_region->allocate(word_size);
    }
    // Caller handles alloc failure.
  } else {
    // This sets other apis using the same old alloc region to NULL, also.
    set_gc_alloc_region(purpose, NULL);
  }
  return block;  // May be NULL.
}

void G1CollectedHeap::par_allocate_remaining_space(HeapRegion* r) {
  HeapWord* block = NULL;
  size_t free_words;
  do {
    free_words = r->free()/HeapWordSize;
    // If there's too little space, no one can allocate, so we're done.
    if (free_words < CollectedHeap::min_fill_size()) return;
    // Otherwise, try to claim it.
    block = r->par_allocate(free_words);
  } while (block == NULL);
  fill_with_object(block, free_words);
}

#ifndef PRODUCT
bool GCLabBitMapClosure::do_bit(size_t offset) {
  HeapWord* addr = _bitmap->offsetToHeapWord(offset);
  guarantee(_cm->isMarked(oop(addr)), "it should be!");
  return true;
}
#endif // PRODUCT

G1ParScanThreadState::G1ParScanThreadState(G1CollectedHeap* g1h, int queue_num)
  : _g1h(g1h),
    _refs(g1h->task_queue(queue_num)),
    _dcq(&g1h->dirty_card_queue_set()),
    _ct_bs((CardTableModRefBS*)_g1h->barrier_set()),
    _g1_rem(g1h->g1_rem_set()),
    _hash_seed(17), _queue_num(queue_num),
    _term_attempts(0),
    _surviving_alloc_buffer(g1h->desired_plab_sz(GCAllocForSurvived)),
    _tenured_alloc_buffer(g1h->desired_plab_sz(GCAllocForTenured)),
    _age_table(false),
    _strong_roots_time(0), _term_time(0),
    _alloc_buffer_waste(0), _undo_waste(0)
{
  // we allocate G1YoungSurvRateNumRegions plus one entries, since
  // we "sacrifice" entry 0 to keep track of surviving bytes for
  // non-young regions (where the age is -1)
  // We also add a few elements at the beginning and at the end in
  // an attempt to eliminate cache contention
  size_t real_length = 1 + _g1h->g1_policy()->young_cset_length();
  size_t array_length = PADDING_ELEM_NUM +
                        real_length +
                        PADDING_ELEM_NUM;
  _surviving_young_words_base = NEW_C_HEAP_ARRAY(size_t, array_length);
  if (_surviving_young_words_base == NULL)
    vm_exit_out_of_memory(array_length * sizeof(size_t),
                          "Not enough space for young surv histo.");
  _surviving_young_words = _surviving_young_words_base + PADDING_ELEM_NUM;
  memset(_surviving_young_words, 0, real_length * sizeof(size_t));

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

G1ParClosureSuper::G1ParClosureSuper(G1CollectedHeap* g1, G1ParScanThreadState* par_scan_state) :
  _g1(g1), _g1_rem(_g1->g1_rem_set()), _cm(_g1->concurrent_mark()),
  _par_scan_state(par_scan_state) { }

template <class T> void G1ParCopyHelper::mark_forwardee(T* p) {
  // This is called _after_ do_oop_work has been called, hence after
  // the object has been relocated to its new location and *p points
  // to its new location.

  T heap_oop = oopDesc::load_heap_oop(p);
  if (!oopDesc::is_null(heap_oop)) {
    oop obj = oopDesc::decode_heap_oop(heap_oop);
    assert((_g1->evacuation_failed()) || (!_g1->obj_in_cs(obj)),
           "shouldn't still be in the CSet if evacuation didn't fail.");
    HeapWord* addr = (HeapWord*)obj;
    if (_g1->is_in_g1_reserved(addr))
      _cm->grayRoot(oop(addr));
  }
}

oop G1ParCopyHelper::copy_to_survivor_space(oop old) {
  size_t    word_sz = old->size();
  HeapRegion* from_region = _g1->heap_region_containing_raw(old);
  // +1 to make the -1 indexes valid...
  int       young_index = from_region->young_index_in_cset()+1;
  assert( (from_region->is_young() && young_index > 0) ||
          (!from_region->is_young() && young_index == 0), "invariant" );
  G1CollectorPolicy* g1p = _g1->g1_policy();
  markOop m = old->mark();
  int age = m->has_displaced_mark_helper() ? m->displaced_mark_helper()->age()
                                           : m->age();
  GCAllocPurpose alloc_purpose = g1p->evacuation_destination(from_region, age,
                                                             word_sz);
  HeapWord* obj_ptr = _par_scan_state->allocate(alloc_purpose, word_sz);
  oop       obj     = oop(obj_ptr);

  if (obj_ptr == NULL) {
    // This will either forward-to-self, or detect that someone else has
    // installed a forwarding pointer.
    OopsInHeapRegionClosure* cl = _par_scan_state->evac_failure_closure();
    return _g1->handle_evacuation_failure_par(cl, old);
  }

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
      _par_scan_state->age_table()->add(obj, word_sz);
    } else {
      obj->set_mark(m);
    }

    // preserve "next" mark bit
    if (_g1->mark_in_progress() && !_g1->is_obj_ill(old)) {
      if (!use_local_bitmaps ||
          !_par_scan_state->alloc_buffer(alloc_purpose)->mark(obj_ptr)) {
        // if we couldn't mark it on the local bitmap (this happens when
        // the object was not allocated in the GCLab), we have to bite
        // the bullet and do the standard parallel mark
        _cm->markAndGrayObjectIfNecessary(obj);
      }
#if 1
      if (_g1->isMarkedNext(old)) {
        _cm->nextMarkBitMap()->parClear((HeapWord*)old);
      }
#endif
    }

    size_t* surv_young_words = _par_scan_state->surviving_young_words();
    surv_young_words[young_index] += word_sz;

    if (obj->is_objArray() && arrayOop(obj)->length() >= ParGCArrayScanChunk) {
      arrayOop(old)->set_length(0);
      oop* old_p = set_partial_array_mask(old);
      _par_scan_state->push_on_queue(old_p);
    } else {
      // No point in using the slower heap_region_containing() method,
      // given that we know obj is in the heap.
      _scanner->set_region(_g1->heap_region_containing_raw(obj));
      obj->oop_iterate_backwards(_scanner);
    }
  } else {
    _par_scan_state->undo_allocation(alloc_purpose, obj_ptr, word_sz);
    obj = forward_ptr;
  }
  return obj;
}

template <bool do_gen_barrier, G1Barrier barrier, bool do_mark_forwardee>
template <class T>
void G1ParCopyClosure <do_gen_barrier, barrier, do_mark_forwardee>
::do_oop_work(T* p) {
  oop obj = oopDesc::load_decode_heap_oop(p);
  assert(barrier != G1BarrierRS || obj != NULL,
         "Precondition: G1BarrierRS implies obj is nonNull");

  // here the null check is implicit in the cset_fast_test() test
  if (_g1->in_cset_fast_test(obj)) {
#if G1_REM_SET_LOGGING
    gclog_or_tty->print_cr("Loc "PTR_FORMAT" contains pointer "PTR_FORMAT" "
                           "into CS.", p, (void*) obj);
#endif
    if (obj->is_forwarded()) {
      oopDesc::encode_store_heap_oop(p, obj->forwardee());
    } else {
      oop copy_oop = copy_to_survivor_space(obj);
      oopDesc::encode_store_heap_oop(p, copy_oop);
    }
    // When scanning the RS, we only care about objs in CS.
    if (barrier == G1BarrierRS) {
      _par_scan_state->update_rs(_from, p, _par_scan_state->queue_num());
    }
  }

  if (barrier == G1BarrierEvac && obj != NULL) {
    _par_scan_state->update_rs(_from, p, _par_scan_state->queue_num());
  }

  if (do_gen_barrier && obj != NULL) {
    par_do_barrier(p);
  }
}

template void G1ParCopyClosure<false, G1BarrierEvac, false>::do_oop_work(oop* p);
template void G1ParCopyClosure<false, G1BarrierEvac, false>::do_oop_work(narrowOop* p);

template <class T> void G1ParScanPartialArrayClosure::do_oop_nv(T* p) {
  assert(has_partial_array_mask(p), "invariant");
  oop old = clear_partial_array_mask(p);
  assert(old->is_objArray(), "must be obj array");
  assert(old->is_forwarded(), "must be forwarded");
  assert(Universe::heap()->is_in_reserved(old), "must be in heap.");

  objArrayOop obj = objArrayOop(old->forwardee());
  assert((void*)old != (void*)old->forwardee(), "self forwarding here?");
  // Process ParGCArrayScanChunk elements now
  // and push the remainder back onto queue
  int start     = arrayOop(old)->length();
  int end       = obj->length();
  int remainder = end - start;
  assert(start <= end, "just checking");
  if (remainder > 2 * ParGCArrayScanChunk) {
    // Test above combines last partial chunk with a full chunk
    end = start + ParGCArrayScanChunk;
    arrayOop(old)->set_length(end);
    // Push remainder.
    oop* old_p = set_partial_array_mask(old);
    assert(arrayOop(old)->length() < obj->length(), "Empty push?");
    _par_scan_state->push_on_queue(old_p);
  } else {
    // Restore length so that the heap remains parsable in
    // case of evacuation failure.
    arrayOop(old)->set_length(end);
  }
  _scanner.set_region(_g1->heap_region_containing_raw(obj));
  // process our set of indices (include header in first chunk)
  obj->oop_iterate_range(&_scanner, start, end);
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

  void do_void() {
    G1ParScanThreadState* pss = par_scan_state();
    while (true) {
      pss->trim_queue();

      StarTask stolen_task;
      if (queues()->steal(pss->queue_num(), pss->hash_seed(), stolen_task)) {
        // slightly paranoid tests; I'm trying to catch potential
        // problems before we go into push_on_queue to know where the
        // problem is coming from
        assert((oop*)stolen_task != NULL, "Error");
        if (stolen_task.is_narrow()) {
          assert(UseCompressedOops, "Error");
          narrowOop* p = (narrowOop*) stolen_task;
          assert(has_partial_array_mask(p) ||
                 _g1h->is_in_g1_reserved(oopDesc::load_decode_heap_oop(p)), "Error");
          pss->push_on_queue(p);
        } else {
          oop* p = (oop*) stolen_task;
          assert(has_partial_array_mask(p) || _g1h->is_in_g1_reserved(*p), "Error");
          pss->push_on_queue(p);
        }
        continue;
      }
      pss->start_term_time();
      if (terminator()->offer_termination()) break;
      pss->end_term_time();
    }
    pss->end_term_time();
    pss->retire_alloc_buffers();
  }
};

class G1ParTask : public AbstractGangTask {
protected:
  G1CollectedHeap*       _g1h;
  RefToScanQueueSet      *_queues;
  ParallelTaskTerminator _terminator;
  int _n_workers;

  Mutex _stats_lock;
  Mutex* stats_lock() { return &_stats_lock; }

  size_t getNCards() {
    return (_g1h->capacity() + G1BlockOffsetSharedArray::N_bytes - 1)
      / G1BlockOffsetSharedArray::N_bytes;
  }

public:
  G1ParTask(G1CollectedHeap* g1h, int workers, RefToScanQueueSet *task_queues)
    : AbstractGangTask("G1 collection"),
      _g1h(g1h),
      _queues(task_queues),
      _terminator(workers, _queues),
      _stats_lock(Mutex::leaf, "parallel G1 stats lock", true),
      _n_workers(workers)
  {}

  RefToScanQueueSet* queues() { return _queues; }

  RefToScanQueue *work_queue(int i) {
    return queues()->queue(i);
  }

  void work(int i) {
    if (i >= _n_workers) return;  // no work needed this round

    double start_time_ms = os::elapsedTime() * 1000.0;
    _g1h->g1_policy()->record_gc_worker_start_time(i, start_time_ms);

    ResourceMark rm;
    HandleMark   hm;

    G1ParScanThreadState            pss(_g1h, i);
    G1ParScanHeapEvacClosure        scan_evac_cl(_g1h, &pss);
    G1ParScanHeapEvacFailureClosure evac_failure_cl(_g1h, &pss);
    G1ParScanPartialArrayClosure    partial_scan_cl(_g1h, &pss);

    pss.set_evac_closure(&scan_evac_cl);
    pss.set_evac_failure_closure(&evac_failure_cl);
    pss.set_partial_scan_closure(&partial_scan_cl);

    G1ParScanExtRootClosure         only_scan_root_cl(_g1h, &pss);
    G1ParScanPermClosure            only_scan_perm_cl(_g1h, &pss);
    G1ParScanHeapRSClosure          only_scan_heap_rs_cl(_g1h, &pss);
    G1ParPushHeapRSClosure          push_heap_rs_cl(_g1h, &pss);

    G1ParScanAndMarkExtRootClosure  scan_mark_root_cl(_g1h, &pss);
    G1ParScanAndMarkPermClosure     scan_mark_perm_cl(_g1h, &pss);
    G1ParScanAndMarkHeapRSClosure   scan_mark_heap_rs_cl(_g1h, &pss);

    OopsInHeapRegionClosure        *scan_root_cl;
    OopsInHeapRegionClosure        *scan_perm_cl;

    if (_g1h->g1_policy()->during_initial_mark_pause()) {
      scan_root_cl = &scan_mark_root_cl;
      scan_perm_cl = &scan_mark_perm_cl;
    } else {
      scan_root_cl = &only_scan_root_cl;
      scan_perm_cl = &only_scan_perm_cl;
    }

    pss.start_strong_roots();
    _g1h->g1_process_strong_roots(/* not collecting perm */ false,
                                  SharedHeap::SO_AllClasses,
                                  scan_root_cl,
                                  &push_heap_rs_cl,
                                  scan_perm_cl,
                                  i);
    pss.end_strong_roots();
    {
      double start = os::elapsedTime();
      G1ParEvacuateFollowersClosure evac(_g1h, &pss, _queues, &_terminator);
      evac.do_void();
      double elapsed_ms = (os::elapsedTime()-start)*1000.0;
      double term_ms = pss.term_time()*1000.0;
      _g1h->g1_policy()->record_obj_copy_time(i, elapsed_ms-term_ms);
      _g1h->g1_policy()->record_termination(i, term_ms, pss.term_attempts());
    }
    _g1h->g1_policy()->record_thread_age_table(pss.age_table());
    _g1h->update_surviving_young_words(pss.surviving_young_words()+1);

    // Clean up any par-expanded rem sets.
    HeapRegionRemSet::par_cleanup();

    if (ParallelGCVerbose) {
      MutexLocker x(stats_lock());
      pss.print_termination_stats(i);
    }

    assert(pss.refs_to_scan() == 0, "Task queue should be empty");
    assert(pss.overflowed_refs_to_scan() == 0, "Overflow queue should be empty");
    double end_time_ms = os::elapsedTime() * 1000.0;
    _g1h->g1_policy()->record_gc_worker_end_time(i, end_time_ms);
  }
};

// *** Common G1 Evacuation Stuff

// This method is run in a GC worker.

void
G1CollectedHeap::
g1_process_strong_roots(bool collecting_perm_gen,
                        SharedHeap::ScanningOption so,
                        OopClosure* scan_non_heap_roots,
                        OopsInHeapRegionClosure* scan_rs,
                        OopsInGenClosure* scan_perm,
                        int worker_i) {
  // First scan the strong roots, including the perm gen.
  double ext_roots_start = os::elapsedTime();
  double closure_app_time_sec = 0.0;

  BufferingOopClosure buf_scan_non_heap_roots(scan_non_heap_roots);
  BufferingOopsInGenClosure buf_scan_perm(scan_perm);
  buf_scan_perm.set_generation(perm_gen());

  // Walk the code cache w/o buffering, because StarTask cannot handle
  // unaligned oop locations.
  CodeBlobToOopClosure eager_scan_code_roots(scan_non_heap_roots, /*do_marking=*/ true);

  process_strong_roots(false, // no scoping; this is parallel code
                       collecting_perm_gen, so,
                       &buf_scan_non_heap_roots,
                       &eager_scan_code_roots,
                       &buf_scan_perm);

  // Finish up any enqueued closure apps.
  buf_scan_non_heap_roots.done();
  buf_scan_perm.done();
  double ext_roots_end = os::elapsedTime();
  g1_policy()->reset_obj_copy_time(worker_i);
  double obj_copy_time_sec =
    buf_scan_non_heap_roots.closure_app_seconds() +
    buf_scan_perm.closure_app_seconds();
  g1_policy()->record_obj_copy_time(worker_i, obj_copy_time_sec * 1000.0);
  double ext_root_time_ms =
    ((ext_roots_end - ext_roots_start) - obj_copy_time_sec) * 1000.0;
  g1_policy()->record_ext_root_scan_time(worker_i, ext_root_time_ms);

  // Scan strong roots in mark stack.
  if (!_process_strong_tasks->is_task_claimed(G1H_PS_mark_stack_oops_do)) {
    concurrent_mark()->oops_do(scan_non_heap_roots);
  }
  double mark_stack_scan_ms = (os::elapsedTime() - ext_roots_end) * 1000.0;
  g1_policy()->record_mark_stack_scan_time(worker_i, mark_stack_scan_ms);

  // XXX What should this be doing in the parallel case?
  g1_policy()->record_collection_pause_end_CH_strong_roots();
  // Now scan the complement of the collection set.
  if (scan_rs != NULL) {
    g1_rem_set()->oops_into_collection_set_do(scan_rs, worker_i);
  }
  // Finish with the ref_processor roots.
  if (!_process_strong_tasks->is_task_claimed(G1H_PS_refProcessor_oops_do)) {
    ref_processor()->oops_do(scan_non_heap_roots);
  }
  g1_policy()->record_collection_pause_end_G1_strong_roots();
  _process_strong_tasks->all_tasks_completed();
}

void
G1CollectedHeap::g1_process_weak_roots(OopClosure* root_closure,
                                       OopClosure* non_root_closure) {
  CodeBlobToOopClosure roots_in_blobs(root_closure, /*do_marking=*/ false);
  SharedHeap::process_weak_roots(root_closure, &roots_in_blobs, non_root_closure);
}


class SaveMarksClosure: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion* r) {
    r->save_marks();
    return false;
  }
};

void G1CollectedHeap::save_marks() {
  if (!CollectedHeap::use_parallel_gc_threads()) {
    SaveMarksClosure sm;
    heap_region_iterate(&sm);
  }
  // We do this even in the parallel case
  perm_gen()->save_marks();
}

void G1CollectedHeap::evacuate_collection_set() {
  set_evacuation_failed(false);

  g1_rem_set()->prepare_for_oops_into_collection_set_do();
  concurrent_g1_refine()->set_use_cache(false);
  concurrent_g1_refine()->clear_hot_cache_claimed_index();

  int n_workers = (ParallelGCThreads > 0 ? workers()->total_workers() : 1);
  set_par_threads(n_workers);
  G1ParTask g1_par_task(this, n_workers, _task_queues);

  init_for_evac_failure(NULL);

  rem_set()->prepare_for_younger_refs_iterate(true);

  assert(dirty_card_queue_set().completed_buffers_num() == 0, "Should be empty");
  double start_par = os::elapsedTime();
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    // The individual threads will set their evac-failure closures.
    StrongRootsScope srs(this);
    if (ParallelGCVerbose) G1ParScanThreadState::print_termination_stats_hdr();
    workers()->run_task(&g1_par_task);
  } else {
    StrongRootsScope srs(this);
    g1_par_task.work(0);
  }

  double par_time = (os::elapsedTime() - start_par) * 1000.0;
  g1_policy()->record_par_time(par_time);
  set_par_threads(0);
  // Is this the right thing to do here?  We don't save marks
  // on individual heap regions when we allocate from
  // them in parallel, so this seems like the correct place for this.
  retire_all_alloc_regions();
  {
    G1IsAliveClosure is_alive(this);
    G1KeepAliveClosure keep_alive(this);
    JNIHandles::weak_oops_do(&is_alive, &keep_alive);
  }
  release_gc_alloc_regions(false /* totally */);
  g1_rem_set()->cleanup_after_oops_into_collection_set_do();

  concurrent_g1_refine()->clear_hot_cache();
  concurrent_g1_refine()->set_use_cache(true);

  finalize_for_evac_failure();

  // Must do this before removing self-forwarding pointers, which clears
  // the per-region evac-failure flags.
  concurrent_mark()->complete_marking_in_collection_set();

  if (evacuation_failed()) {
    remove_self_forwarding_pointers();
    if (PrintGCDetails) {
      gclog_or_tty->print(" (to-space overflow)");
    } else if (PrintGC) {
      gclog_or_tty->print("--");
    }
  }

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

void G1CollectedHeap::free_region(HeapRegion* hr) {
  size_t pre_used = 0;
  size_t cleared_h_regions = 0;
  size_t freed_regions = 0;
  UncleanRegionList local_list;

  HeapWord* start = hr->bottom();
  HeapWord* end   = hr->prev_top_at_mark_start();
  size_t used_bytes = hr->used();
  size_t live_bytes = hr->max_live_bytes();
  if (used_bytes > 0) {
    guarantee( live_bytes <= used_bytes, "invariant" );
  } else {
    guarantee( live_bytes == 0, "invariant" );
  }

  size_t garbage_bytes = used_bytes - live_bytes;
  if (garbage_bytes > 0)
    g1_policy()->decrease_known_garbage_bytes(garbage_bytes);

  free_region_work(hr, pre_used, cleared_h_regions, freed_regions,
                   &local_list);
  finish_free_region_work(pre_used, cleared_h_regions, freed_regions,
                          &local_list);
}

void
G1CollectedHeap::free_region_work(HeapRegion* hr,
                                  size_t& pre_used,
                                  size_t& cleared_h_regions,
                                  size_t& freed_regions,
                                  UncleanRegionList* list,
                                  bool par) {
  pre_used += hr->used();
  if (hr->isHumongous()) {
    assert(hr->startsHumongous(),
           "Only the start of a humongous region should be freed.");
    int ind = _hrs->find(hr);
    assert(ind != -1, "Should have an index.");
    // Clear the start region.
    hr->hr_clear(par, true /*clear_space*/);
    list->insert_before_head(hr);
    cleared_h_regions++;
    freed_regions++;
    // Clear any continued regions.
    ind++;
    while ((size_t)ind < n_regions()) {
      HeapRegion* hrc = _hrs->at(ind);
      if (!hrc->continuesHumongous()) break;
      // Otherwise, does continue the H region.
      assert(hrc->humongous_start_region() == hr, "Huh?");
      hrc->hr_clear(par, true /*clear_space*/);
      cleared_h_regions++;
      freed_regions++;
      list->insert_before_head(hrc);
      ind++;
    }
  } else {
    hr->hr_clear(par, true /*clear_space*/);
    list->insert_before_head(hr);
    freed_regions++;
    // If we're using clear2, this should not be enabled.
    // assert(!hr->in_cohort(), "Can't be both free and in a cohort.");
  }
}

void G1CollectedHeap::finish_free_region_work(size_t pre_used,
                                              size_t cleared_h_regions,
                                              size_t freed_regions,
                                              UncleanRegionList* list) {
  if (list != NULL && list->sz() > 0) {
    prepend_region_list_on_unclean_list(list);
  }
  // Acquire a lock, if we're parallel, to update possibly-shared
  // variables.
  Mutex* lock = (n_par_threads() > 0) ? ParGCRareEvent_lock : NULL;
  {
    MutexLockerEx x(lock, Mutex::_no_safepoint_check_flag);
    _summary_bytes_used -= pre_used;
    _num_humongous_regions -= (int) cleared_h_regions;
    _free_regions += freed_regions;
  }
}


void G1CollectedHeap::dirtyCardsForYoungRegions(CardTableModRefBS* ct_bs, HeapRegion* list) {
  while (list != NULL) {
    guarantee( list->is_young(), "invariant" );

    HeapWord* bottom = list->bottom();
    HeapWord* end = list->end();
    MemRegion mr(bottom, end);
    ct_bs->dirty(mr);

    list = list->get_next_young_region();
  }
}


class G1ParCleanupCTTask : public AbstractGangTask {
  CardTableModRefBS* _ct_bs;
  G1CollectedHeap* _g1h;
  HeapRegion* volatile _su_head;
public:
  G1ParCleanupCTTask(CardTableModRefBS* ct_bs,
                     G1CollectedHeap* g1h,
                     HeapRegion* survivor_list) :
    AbstractGangTask("G1 Par Cleanup CT Task"),
    _ct_bs(ct_bs),
    _g1h(g1h),
    _su_head(survivor_list)
  { }

  void work(int i) {
    HeapRegion* r;
    while (r = _g1h->pop_dirty_cards_region()) {
      clear_cards(r);
    }
    // Redirty the cards of the survivor regions.
    dirty_list(&this->_su_head);
  }

  void clear_cards(HeapRegion* r) {
    // Cards for Survivor regions will be dirtied later.
    if (!r->is_survivor()) {
      _ct_bs->clear(MemRegion(r->bottom(), r->end()));
    }
  }

  void dirty_list(HeapRegion* volatile * head_ptr) {
    HeapRegion* head;
    do {
      // Pop region off the list.
      head = *head_ptr;
      if (head != NULL) {
        HeapRegion* r = (HeapRegion*)
          Atomic::cmpxchg_ptr(head->get_next_young_region(), head_ptr, head);
        if (r == head) {
          assert(!r->isHumongous(), "Humongous regions shouldn't be on survivor list");
          _ct_bs->dirty(MemRegion(r->bottom(), r->end()));
        }
      }
    } while (*head_ptr != NULL);
  }
};


#ifndef PRODUCT
class G1VerifyCardTableCleanup: public HeapRegionClosure {
  CardTableModRefBS* _ct_bs;
public:
  G1VerifyCardTableCleanup(CardTableModRefBS* ct_bs)
    : _ct_bs(ct_bs)
  { }
  virtual bool doHeapRegion(HeapRegion* r)
  {
    MemRegion mr(r->bottom(), r->end());
    if (r->is_survivor()) {
      _ct_bs->verify_dirty_region(mr);
    } else {
      _ct_bs->verify_clean_region(mr);
    }
    return false;
  }
};
#endif

void G1CollectedHeap::cleanUpCardTable() {
  CardTableModRefBS* ct_bs = (CardTableModRefBS*) (barrier_set());
  double start = os::elapsedTime();

  // Iterate over the dirty cards region list.
  G1ParCleanupCTTask cleanup_task(ct_bs, this,
                                  _young_list->first_survivor_region());

  if (ParallelGCThreads > 0) {
    set_par_threads(workers()->total_workers());
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
    // now, redirty the cards of the survivor regions
    // (it seemed faster to do it this way, instead of iterating over
    // all regions and then clearing / dirtying as appropriate)
    dirtyCardsForYoungRegions(ct_bs, _young_list->first_survivor_region());
  }

  double elapsed = os::elapsedTime() - start;
  g1_policy()->record_clear_ct_time( elapsed * 1000.0);
#ifndef PRODUCT
  if (G1VerifyCTCleanup || VerifyAfterGC) {
    G1VerifyCardTableCleanup cleanup_verifier(ct_bs);
    heap_region_iterate(&cleanup_verifier);
  }
#endif
}

void G1CollectedHeap::do_collection_pause_if_appropriate(size_t word_size) {
  if (g1_policy()->should_do_collection_pause(word_size)) {
    do_collection_pause();
  }
}

void G1CollectedHeap::free_collection_set(HeapRegion* cs_head) {
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
    if (non_young) {
      if (cur->is_young()) {
        double end_sec = os::elapsedTime();
        double elapsed_ms = (end_sec - start_sec) * 1000.0;
        non_young_time_ms += elapsed_ms;

        start_sec = os::elapsedTime();
        non_young = false;
      }
    } else {
      if (!cur->is_on_free_list()) {
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
      guarantee( index != -1, "invariant" );
      guarantee( (size_t)index < policy->young_cset_length(), "invariant" );
      size_t words_survived = _surviving_young_words[index];
      cur->record_surv_words_in_group(words_survived);

      // At this point the we have 'popped' cur from the collection set
      // (linked via next_in_collection_set()) but it is still in the
      // young list (linked via next_young_region()). Clear the
      // _next_young_region field.
      cur->set_next_young_region(NULL);
    } else {
      int index = cur->young_index_in_cset();
      guarantee( index == -1, "invariant" );
    }

    assert( (cur->is_young() && cur->young_index_in_cset() > -1) ||
            (!cur->is_young() && cur->young_index_in_cset() == -1),
            "invariant" );

    if (!cur->evacuation_failed()) {
      // And the region is empty.
      assert(!cur->is_empty(),
             "Should not have empty regions in a CS.");
      free_region(cur);
    } else {
      cur->uninstall_surv_rate_group();
      if (cur->is_young())
        cur->set_young_index_in_cset(-1);
      cur->set_not_young();
      cur->set_evacuation_failed(false);
    }
    cur = next;
  }

  policy->record_max_rs_lengths(rs_lengths);
  policy->cset_regions_freed();

  double end_sec = os::elapsedTime();
  double elapsed_ms = (end_sec - start_sec) * 1000.0;
  if (non_young)
    non_young_time_ms += elapsed_ms;
  else
    young_time_ms += elapsed_ms;

  policy->record_young_free_cset_time_ms(young_time_ms);
  policy->record_non_young_free_cset_time_ms(non_young_time_ms);
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

HeapRegion*
G1CollectedHeap::alloc_region_from_unclean_list_locked(bool zero_filled) {
  assert(ZF_mon->owned_by_self(), "Precondition");
  HeapRegion* res = pop_unclean_region_list_locked();
  if (res != NULL) {
    assert(!res->continuesHumongous() &&
           res->zero_fill_state() != HeapRegion::Allocated,
           "Only free regions on unclean list.");
    if (zero_filled) {
      res->ensure_zero_filled_locked();
      res->set_zero_fill_allocated();
    }
  }
  return res;
}

HeapRegion* G1CollectedHeap::alloc_region_from_unclean_list(bool zero_filled) {
  MutexLockerEx zx(ZF_mon, Mutex::_no_safepoint_check_flag);
  return alloc_region_from_unclean_list_locked(zero_filled);
}

void G1CollectedHeap::put_region_on_unclean_list(HeapRegion* r) {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  put_region_on_unclean_list_locked(r);
  if (should_zf()) ZF_mon->notify_all(); // Wake up ZF thread.
}

void G1CollectedHeap::set_unclean_regions_coming(bool b) {
  MutexLockerEx x(Cleanup_mon);
  set_unclean_regions_coming_locked(b);
}

void G1CollectedHeap::set_unclean_regions_coming_locked(bool b) {
  assert(Cleanup_mon->owned_by_self(), "Precondition");
  _unclean_regions_coming = b;
  // Wake up mutator threads that might be waiting for completeCleanup to
  // finish.
  if (!b) Cleanup_mon->notify_all();
}

void G1CollectedHeap::wait_for_cleanup_complete() {
  MutexLockerEx x(Cleanup_mon);
  wait_for_cleanup_complete_locked();
}

void G1CollectedHeap::wait_for_cleanup_complete_locked() {
  assert(Cleanup_mon->owned_by_self(), "precondition");
  while (_unclean_regions_coming) {
    Cleanup_mon->wait();
  }
}

void
G1CollectedHeap::put_region_on_unclean_list_locked(HeapRegion* r) {
  assert(ZF_mon->owned_by_self(), "precondition.");
#ifdef ASSERT
  if (r->is_gc_alloc_region()) {
    ResourceMark rm;
    stringStream region_str;
    print_on(&region_str);
    assert(!r->is_gc_alloc_region(), err_msg("Unexpected GC allocation region: %s",
                                             region_str.as_string()));
  }
#endif
  _unclean_region_list.insert_before_head(r);
}

void
G1CollectedHeap::prepend_region_list_on_unclean_list(UncleanRegionList* list) {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  prepend_region_list_on_unclean_list_locked(list);
  if (should_zf()) ZF_mon->notify_all(); // Wake up ZF thread.
}

void
G1CollectedHeap::
prepend_region_list_on_unclean_list_locked(UncleanRegionList* list) {
  assert(ZF_mon->owned_by_self(), "precondition.");
  _unclean_region_list.prepend_list(list);
}

HeapRegion* G1CollectedHeap::pop_unclean_region_list_locked() {
  assert(ZF_mon->owned_by_self(), "precondition.");
  HeapRegion* res = _unclean_region_list.pop();
  if (res != NULL) {
    // Inform ZF thread that there's a new unclean head.
    if (_unclean_region_list.hd() != NULL && should_zf())
      ZF_mon->notify_all();
  }
  return res;
}

HeapRegion* G1CollectedHeap::peek_unclean_region_list_locked() {
  assert(ZF_mon->owned_by_self(), "precondition.");
  return _unclean_region_list.hd();
}


bool G1CollectedHeap::move_cleaned_region_to_free_list_locked() {
  assert(ZF_mon->owned_by_self(), "Precondition");
  HeapRegion* r = peek_unclean_region_list_locked();
  if (r != NULL && r->zero_fill_state() == HeapRegion::ZeroFilled) {
    // Result of below must be equal to "r", since we hold the lock.
    (void)pop_unclean_region_list_locked();
    put_free_region_on_list_locked(r);
    return true;
  } else {
    return false;
  }
}

bool G1CollectedHeap::move_cleaned_region_to_free_list() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  return move_cleaned_region_to_free_list_locked();
}


void G1CollectedHeap::put_free_region_on_list_locked(HeapRegion* r) {
  assert(ZF_mon->owned_by_self(), "precondition.");
  assert(_free_region_list_size == free_region_list_length(), "Inv");
  assert(r->zero_fill_state() == HeapRegion::ZeroFilled,
        "Regions on free list must be zero filled");
  assert(!r->isHumongous(), "Must not be humongous.");
  assert(r->is_empty(), "Better be empty");
  assert(!r->is_on_free_list(),
         "Better not already be on free list");
  assert(!r->is_on_unclean_list(),
         "Better not already be on unclean list");
  r->set_on_free_list(true);
  r->set_next_on_free_list(_free_region_list);
  _free_region_list = r;
  _free_region_list_size++;
  assert(_free_region_list_size == free_region_list_length(), "Inv");
}

void G1CollectedHeap::put_free_region_on_list(HeapRegion* r) {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  put_free_region_on_list_locked(r);
}

HeapRegion* G1CollectedHeap::pop_free_region_list_locked() {
  assert(ZF_mon->owned_by_self(), "precondition.");
  assert(_free_region_list_size == free_region_list_length(), "Inv");
  HeapRegion* res = _free_region_list;
  if (res != NULL) {
    _free_region_list = res->next_from_free_list();
    _free_region_list_size--;
    res->set_on_free_list(false);
    res->set_next_on_free_list(NULL);
    assert(_free_region_list_size == free_region_list_length(), "Inv");
  }
  return res;
}


HeapRegion* G1CollectedHeap::alloc_free_region_from_lists(bool zero_filled) {
  // By self, or on behalf of self.
  assert(Heap_lock->is_locked(), "Precondition");
  HeapRegion* res = NULL;
  bool first = true;
  while (res == NULL) {
    if (zero_filled || !first) {
      MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
      res = pop_free_region_list_locked();
      if (res != NULL) {
        assert(!res->zero_fill_is_allocated(),
               "No allocated regions on free list.");
        res->set_zero_fill_allocated();
      } else if (!first) {
        break;  // We tried both, time to return NULL.
      }
    }

    if (res == NULL) {
      res = alloc_region_from_unclean_list(zero_filled);
    }
    assert(res == NULL ||
           !zero_filled ||
           res->zero_fill_is_allocated(),
           "We must have allocated the region we're returning");
    first = false;
  }
  return res;
}

void G1CollectedHeap::remove_allocated_regions_from_lists() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  {
    HeapRegion* prev = NULL;
    HeapRegion* cur = _unclean_region_list.hd();
    while (cur != NULL) {
      HeapRegion* next = cur->next_from_unclean_list();
      if (cur->zero_fill_is_allocated()) {
        // Remove from the list.
        if (prev == NULL) {
          (void)_unclean_region_list.pop();
        } else {
          _unclean_region_list.delete_after(prev);
        }
        cur->set_on_unclean_list(false);
        cur->set_next_on_unclean_list(NULL);
      } else {
        prev = cur;
      }
      cur = next;
    }
    assert(_unclean_region_list.sz() == unclean_region_list_length(),
           "Inv");
  }

  {
    HeapRegion* prev = NULL;
    HeapRegion* cur = _free_region_list;
    while (cur != NULL) {
      HeapRegion* next = cur->next_from_free_list();
      if (cur->zero_fill_is_allocated()) {
        // Remove from the list.
        if (prev == NULL) {
          _free_region_list = cur->next_from_free_list();
        } else {
          prev->set_next_on_free_list(cur->next_from_free_list());
        }
        cur->set_on_free_list(false);
        cur->set_next_on_free_list(NULL);
        _free_region_list_size--;
      } else {
        prev = cur;
      }
      cur = next;
    }
    assert(_free_region_list_size == free_region_list_length(), "Inv");
  }
}

bool G1CollectedHeap::verify_region_lists() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  return verify_region_lists_locked();
}

bool G1CollectedHeap::verify_region_lists_locked() {
  HeapRegion* unclean = _unclean_region_list.hd();
  while (unclean != NULL) {
    guarantee(unclean->is_on_unclean_list(), "Well, it is!");
    guarantee(!unclean->is_on_free_list(), "Well, it shouldn't be!");
    guarantee(unclean->zero_fill_state() != HeapRegion::Allocated,
              "Everything else is possible.");
    unclean = unclean->next_from_unclean_list();
  }
  guarantee(_unclean_region_list.sz() == unclean_region_list_length(), "Inv");

  HeapRegion* free_r = _free_region_list;
  while (free_r != NULL) {
    assert(free_r->is_on_free_list(), "Well, it is!");
    assert(!free_r->is_on_unclean_list(), "Well, it shouldn't be!");
    switch (free_r->zero_fill_state()) {
    case HeapRegion::NotZeroFilled:
    case HeapRegion::ZeroFilling:
      guarantee(false, "Should not be on free list.");
      break;
    default:
      // Everything else is possible.
      break;
    }
    free_r = free_r->next_from_free_list();
  }
  guarantee(_free_region_list_size == free_region_list_length(), "Inv");
  // If we didn't do an assertion...
  return true;
}

size_t G1CollectedHeap::free_region_list_length() {
  assert(ZF_mon->owned_by_self(), "precondition.");
  size_t len = 0;
  HeapRegion* cur = _free_region_list;
  while (cur != NULL) {
    len++;
    cur = cur->next_from_free_list();
  }
  return len;
}

size_t G1CollectedHeap::unclean_region_list_length() {
  assert(ZF_mon->owned_by_self(), "precondition.");
  return _unclean_region_list.length();
}

size_t G1CollectedHeap::n_regions() {
  return _hrs->length();
}

size_t G1CollectedHeap::max_regions() {
  return
    (size_t)align_size_up(g1_reserved_obj_bytes(), HeapRegion::GrainBytes) /
    HeapRegion::GrainBytes;
}

size_t G1CollectedHeap::free_regions() {
  /* Possibly-expensive assert.
  assert(_free_regions == count_free_regions(),
         "_free_regions is off.");
  */
  return _free_regions;
}

bool G1CollectedHeap::should_zf() {
  return _free_region_list_size < (size_t) G1ConcZFMaxRegions;
}

class RegionCounter: public HeapRegionClosure {
  size_t _n;
public:
  RegionCounter() : _n(0) {}
  bool doHeapRegion(HeapRegion* r) {
    if (r->is_empty()) {
      assert(!r->isHumongous(), "H regions should not be empty.");
      _n++;
    }
    return false;
  }
  int res() { return (int) _n; }
};

size_t G1CollectedHeap::count_free_regions() {
  RegionCounter rc;
  heap_region_iterate(&rc);
  size_t n = rc.res();
  if (_cur_alloc_region != NULL && _cur_alloc_region->is_empty())
    n--;
  return n;
}

size_t G1CollectedHeap::count_free_regions_list() {
  size_t n = 0;
  size_t o = 0;
  ZF_mon->lock_without_safepoint_check();
  HeapRegion* cur = _free_region_list;
  while (cur != NULL) {
    cur = cur->next_from_free_list();
    n++;
  }
  size_t m = unclean_region_list_length();
  ZF_mon->unlock();
  return n + m;
}

bool G1CollectedHeap::should_set_young_locked() {
  assert(heap_lock_held_for_gc(),
              "the heap lock should already be held by or for this thread");
  return  (g1_policy()->in_young_gc_mode() &&
           g1_policy()->should_add_next_region_to_young_list());
}

void G1CollectedHeap::set_region_short_lived_locked(HeapRegion* hr) {
  assert(heap_lock_held_for_gc(),
              "the heap lock should already be held by or for this thread");
  _young_list->push_region(hr);
  g1_policy()->set_region_short_lived(hr);
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

void G1CollectedHeap::empty_young_list() {
  assert(heap_lock_held_for_gc(),
              "the heap lock should already be held by or for this thread");
  assert(g1_policy()->in_young_gc_mode(), "should be in young GC mode");

  _young_list->empty_list();
}

bool G1CollectedHeap::all_alloc_regions_no_allocs_since_save_marks() {
  bool no_allocs = true;
  for (int ap = 0; ap < GCAllocPurposeCount && no_allocs; ++ap) {
    HeapRegion* r = _gc_alloc_regions[ap];
    no_allocs = r == NULL || r->saved_mark_at_top();
  }
  return no_allocs;
}

void G1CollectedHeap::retire_all_alloc_regions() {
  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    HeapRegion* r = _gc_alloc_regions[ap];
    if (r != NULL) {
      // Check for aliases.
      bool has_processed_alias = false;
      for (int i = 0; i < ap; ++i) {
        if (_gc_alloc_regions[i] == r) {
          has_processed_alias = true;
          break;
        }
      }
      if (!has_processed_alias) {
        retire_alloc_region(r, false /* par */);
      }
    }
  }
}


// Done at the start of full GC.
void G1CollectedHeap::tear_down_region_lists() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  while (pop_unclean_region_list_locked() != NULL) ;
  assert(_unclean_region_list.hd() == NULL && _unclean_region_list.sz() == 0,
         "Postconditions of loop.");
  while (pop_free_region_list_locked() != NULL) ;
  assert(_free_region_list == NULL, "Postcondition of loop.");
  if (_free_region_list_size != 0) {
    gclog_or_tty->print_cr("Size is %d.", _free_region_list_size);
    print_on(gclog_or_tty, true /* extended */);
  }
  assert(_free_region_list_size == 0, "Postconditions of loop.");
}


class RegionResetter: public HeapRegionClosure {
  G1CollectedHeap* _g1;
  int _n;
public:
  RegionResetter() : _g1(G1CollectedHeap::heap()), _n(0) {}
  bool doHeapRegion(HeapRegion* r) {
    if (r->continuesHumongous()) return false;
    if (r->top() > r->bottom()) {
      if (r->top() < r->end()) {
        Copy::fill_to_words(r->top(),
                          pointer_delta(r->end(), r->top()));
      }
      r->set_zero_fill_allocated();
    } else {
      assert(r->is_empty(), "tautology");
      _n++;
      switch (r->zero_fill_state()) {
        case HeapRegion::NotZeroFilled:
        case HeapRegion::ZeroFilling:
          _g1->put_region_on_unclean_list_locked(r);
          break;
        case HeapRegion::Allocated:
          r->set_zero_fill_complete();
          // no break; go on to put on free list.
        case HeapRegion::ZeroFilled:
          _g1->put_free_region_on_list_locked(r);
          break;
      }
    }
    return false;
  }

  int getFreeRegionCount() {return _n;}
};

// Done at the end of full GC.
void G1CollectedHeap::rebuild_region_lists() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  // This needs to go at the end of the full GC.
  RegionResetter rs;
  heap_region_iterate(&rs);
  _free_regions = rs.getFreeRegionCount();
  // Tell the ZF thread it may have work to do.
  if (should_zf()) ZF_mon->notify_all();
}

class UsedRegionsNeedZeroFillSetter: public HeapRegionClosure {
  G1CollectedHeap* _g1;
  int _n;
public:
  UsedRegionsNeedZeroFillSetter() : _g1(G1CollectedHeap::heap()), _n(0) {}
  bool doHeapRegion(HeapRegion* r) {
    if (r->continuesHumongous()) return false;
    if (r->top() > r->bottom()) {
      // There are assertions in "set_zero_fill_needed()" below that
      // require top() == bottom(), so this is technically illegal.
      // We'll skirt the law here, by making that true temporarily.
      DEBUG_ONLY(HeapWord* save_top = r->top();
                 r->set_top(r->bottom()));
      r->set_zero_fill_needed();
      DEBUG_ONLY(r->set_top(save_top));
    }
    return false;
  }
};

// Done at the start of full GC.
void G1CollectedHeap::set_used_regions_to_need_zero_fill() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  // This needs to go at the end of the full GC.
  UsedRegionsNeedZeroFillSetter rs;
  heap_region_iterate(&rs);
}

void G1CollectedHeap::set_refine_cte_cl_concurrency(bool concurrent) {
  _refine_cte_cl->set_concurrent(concurrent);
}

#ifndef PRODUCT

class PrintHeapRegionClosure: public HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion *r) {
    gclog_or_tty->print("Region: "PTR_FORMAT":", r);
    if (r != NULL) {
      if (r->is_on_free_list())
        gclog_or_tty->print("Free ");
      if (r->is_young())
        gclog_or_tty->print("Young ");
      if (r->isHumongous())
        gclog_or_tty->print("Is Humongous ");
      r->print();
    }
    return false;
  }
};

class SortHeapRegionClosure : public HeapRegionClosure {
  size_t young_regions,free_regions, unclean_regions;
  size_t hum_regions, count;
  size_t unaccounted, cur_unclean, cur_alloc;
  size_t total_free;
  HeapRegion* cur;
public:
  SortHeapRegionClosure(HeapRegion *_cur) : cur(_cur), young_regions(0),
    free_regions(0), unclean_regions(0),
    hum_regions(0),
    count(0), unaccounted(0),
    cur_alloc(0), total_free(0)
  {}
  bool doHeapRegion(HeapRegion *r) {
    count++;
    if (r->is_on_free_list()) free_regions++;
    else if (r->is_on_unclean_list()) unclean_regions++;
    else if (r->isHumongous())  hum_regions++;
    else if (r->is_young()) young_regions++;
    else if (r == cur) cur_alloc++;
    else unaccounted++;
    return false;
  }
  void print() {
    total_free = free_regions + unclean_regions;
    gclog_or_tty->print("%d regions\n", count);
    gclog_or_tty->print("%d free: free_list = %d unclean = %d\n",
                        total_free, free_regions, unclean_regions);
    gclog_or_tty->print("%d humongous %d young\n",
                        hum_regions, young_regions);
    gclog_or_tty->print("%d cur_alloc\n", cur_alloc);
    gclog_or_tty->print("UHOH unaccounted = %d\n", unaccounted);
  }
};

void G1CollectedHeap::print_region_counts() {
  SortHeapRegionClosure sc(_cur_alloc_region);
  PrintHeapRegionClosure cl;
  heap_region_iterate(&cl);
  heap_region_iterate(&sc);
  sc.print();
  print_region_accounting_info();
};

bool G1CollectedHeap::regions_accounted_for() {
  // TODO: regions accounting for young/survivor/tenured
  return true;
}

bool G1CollectedHeap::print_region_accounting_info() {
  gclog_or_tty->print_cr("Free regions: %d (count: %d count list %d) (clean: %d unclean: %d).",
                         free_regions(),
                         count_free_regions(), count_free_regions_list(),
                         _free_region_list_size, _unclean_region_list.sz());
  gclog_or_tty->print_cr("cur_alloc: %d.",
                         (_cur_alloc_region == NULL ? 0 : 1));
  gclog_or_tty->print_cr("H regions: %d.", _num_humongous_regions);

  // TODO: check regions accounting for young/survivor/tenured
  return true;
}

bool G1CollectedHeap::is_in_closed_subset(const void* p) const {
  HeapRegion* hr = heap_region_containing(p);
  if (hr == NULL) {
    return is_in_permanent(p);
  } else {
    return hr->is_in(p);
  }
}
#endif // !PRODUCT

void G1CollectedHeap::g1_unimplemented() {
  // Unimplemented();
}

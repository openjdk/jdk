/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/concurrentG1Refine.hpp"
#include "gc_implementation/g1/concurrentG1RefineThread.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/g1RemSet.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "memory/space.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/java.hpp"
#include "utilities/copy.hpp"

// Possible sizes for the card counts cache: odd primes that roughly double in size.
// (See jvmtiTagMap.cpp).

#define MAX_SIZE ((size_t) -1)

size_t ConcurrentG1Refine::_cc_cache_sizes[] = {
          16381,    32771,    76831,    150001,   307261,
         614563,  1228891,  2457733,   4915219,  9830479,
       19660831, 39321619, 78643219, 157286461,  MAX_SIZE
  };

ConcurrentG1Refine::ConcurrentG1Refine() :
  _card_counts(NULL), _card_epochs(NULL),
  _n_card_counts(0), _max_cards(0), _max_n_card_counts(0),
  _cache_size_index(0), _expand_card_counts(false),
  _hot_cache(NULL),
  _def_use_cache(false), _use_cache(false),
  // We initialize the epochs of the array to 0. By initializing
  // _n_periods to 1 and not 0 we automatically invalidate all the
  // entries on the array. Otherwise we might accidentally think that
  // we claimed a card that was in fact never set (see CR7033292).
  _n_periods(1),
  _threads(NULL), _n_threads(0)
{

  // Ergomonically select initial concurrent refinement parameters
  if (FLAG_IS_DEFAULT(G1ConcRefinementGreenZone)) {
    FLAG_SET_DEFAULT(G1ConcRefinementGreenZone, MAX2<int>(ParallelGCThreads, 1));
  }
  set_green_zone(G1ConcRefinementGreenZone);

  if (FLAG_IS_DEFAULT(G1ConcRefinementYellowZone)) {
    FLAG_SET_DEFAULT(G1ConcRefinementYellowZone, green_zone() * 3);
  }
  set_yellow_zone(MAX2<int>(G1ConcRefinementYellowZone, green_zone()));

  if (FLAG_IS_DEFAULT(G1ConcRefinementRedZone)) {
    FLAG_SET_DEFAULT(G1ConcRefinementRedZone, yellow_zone() * 2);
  }
  set_red_zone(MAX2<int>(G1ConcRefinementRedZone, yellow_zone()));
  _n_worker_threads = thread_num();
  // We need one extra thread to do the young gen rset size sampling.
  _n_threads = _n_worker_threads + 1;
  reset_threshold_step();

  _threads = NEW_C_HEAP_ARRAY(ConcurrentG1RefineThread*, _n_threads, mtGC);
  int worker_id_offset = (int)DirtyCardQueueSet::num_par_ids();
  ConcurrentG1RefineThread *next = NULL;
  for (int i = _n_threads - 1; i >= 0; i--) {
    ConcurrentG1RefineThread* t = new ConcurrentG1RefineThread(this, next, worker_id_offset, i);
    assert(t != NULL, "Conc refine should have been created");
    assert(t->cg1r() == this, "Conc refine thread should refer to this");
    _threads[i] = t;
    next = t;
  }
}

void ConcurrentG1Refine::reset_threshold_step() {
  if (FLAG_IS_DEFAULT(G1ConcRefinementThresholdStep)) {
    _thread_threshold_step = (yellow_zone() - green_zone()) / (worker_thread_num() + 1);
  } else {
    _thread_threshold_step = G1ConcRefinementThresholdStep;
  }
}

int ConcurrentG1Refine::thread_num() {
  return MAX2<int>((G1ConcRefinementThreads > 0) ? G1ConcRefinementThreads : ParallelGCThreads, 1);
}

void ConcurrentG1Refine::init() {
  if (G1ConcRSLogCacheSize > 0) {
    _g1h = G1CollectedHeap::heap();

    _max_cards = _g1h->max_capacity() >> CardTableModRefBS::card_shift;
    _max_n_card_counts = _max_cards * G1MaxHotCardCountSizePercent / 100;

    size_t max_card_num = ((size_t)1 << (sizeof(unsigned)*BitsPerByte-1)) - 1;
    guarantee(_max_cards < max_card_num, "card_num representation");

    // We need _n_card_counts to be less than _max_n_card_counts here
    // so that the expansion call (below) actually allocates the
    // _counts and _epochs arrays.
    assert(_n_card_counts == 0, "pre-condition");
    assert(_max_n_card_counts > 0, "pre-condition");

    // Find the index into cache size array that is of a size that's
    // large enough to hold desired_sz.
    size_t desired_sz = _max_cards / InitialCacheFraction;
    int desired_sz_index = 0;
    while (_cc_cache_sizes[desired_sz_index] < desired_sz) {
      desired_sz_index += 1;
      assert(desired_sz_index <  MAX_CC_CACHE_INDEX, "invariant");
    }
    assert(desired_sz_index <  MAX_CC_CACHE_INDEX, "invariant");

    // If the desired_sz value is between two sizes then
    // _cc_cache_sizes[desired_sz_index-1] < desired_sz <= _cc_cache_sizes[desired_sz_index]
    // we will start with the lower size in the optimistic expectation that
    // we will not need to expand up. Note desired_sz_index could also be 0.
    if (desired_sz_index > 0 &&
        _cc_cache_sizes[desired_sz_index] > desired_sz) {
      desired_sz_index -= 1;
    }

    if (!expand_card_count_cache(desired_sz_index)) {
      // Allocation was unsuccessful - exit
      vm_exit_during_initialization("Could not reserve enough space for card count cache");
    }
    assert(_n_card_counts > 0, "post-condition");
    assert(_cache_size_index == desired_sz_index, "post-condition");

    Copy::fill_to_bytes(&_card_counts[0],
                        _n_card_counts * sizeof(CardCountCacheEntry));
    Copy::fill_to_bytes(&_card_epochs[0], _n_card_counts * sizeof(CardEpochCacheEntry));

    ModRefBarrierSet* bs = _g1h->mr_bs();
    guarantee(bs->is_a(BarrierSet::CardTableModRef), "Precondition");
    _ct_bs = (CardTableModRefBS*)bs;
    _ct_bot = _ct_bs->byte_for_const(_g1h->reserved_region().start());

    _def_use_cache = true;
    _use_cache = true;
    _hot_cache_size = (1 << G1ConcRSLogCacheSize);
    _hot_cache = NEW_C_HEAP_ARRAY(jbyte*, _hot_cache_size, mtGC);
    _n_hot = 0;
    _hot_cache_idx = 0;

    // For refining the cards in the hot cache in parallel
    int n_workers = (ParallelGCThreads > 0 ?
                        _g1h->workers()->total_workers() : 1);
    _hot_cache_par_chunk_size = MAX2(1, _hot_cache_size / n_workers);
    _hot_cache_par_claimed_idx = 0;
  }
}

void ConcurrentG1Refine::stop() {
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      _threads[i]->stop();
    }
  }
}

void ConcurrentG1Refine::reinitialize_threads() {
  reset_threshold_step();
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      _threads[i]->initialize();
    }
  }
}

ConcurrentG1Refine::~ConcurrentG1Refine() {
  if (G1ConcRSLogCacheSize > 0) {
    // Please see the comment in allocate_card_count_cache
    // for why we call os::malloc() and os::free() directly.
    assert(_card_counts != NULL, "Logic");
    os::free(_card_counts, mtGC);
    assert(_card_epochs != NULL, "Logic");
    os::free(_card_epochs, mtGC);

    assert(_hot_cache != NULL, "Logic");
    FREE_C_HEAP_ARRAY(jbyte*, _hot_cache, mtGC);
  }
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      delete _threads[i];
    }
    FREE_C_HEAP_ARRAY(ConcurrentG1RefineThread*, _threads, mtGC);
  }
}

void ConcurrentG1Refine::threads_do(ThreadClosure *tc) {
  if (_threads != NULL) {
    for (int i = 0; i < _n_threads; i++) {
      tc->do_thread(_threads[i]);
    }
  }
}

bool ConcurrentG1Refine::is_young_card(jbyte* card_ptr) {
  HeapWord* start = _ct_bs->addr_for(card_ptr);
  HeapRegion* r = _g1h->heap_region_containing(start);
  if (r != NULL && r->is_young()) {
    return true;
  }
  // This card is not associated with a heap region
  // so can't be young.
  return false;
}

jbyte* ConcurrentG1Refine::add_card_count(jbyte* card_ptr, int* count, bool* defer) {
  unsigned new_card_num = ptr_2_card_num(card_ptr);
  unsigned bucket = hash(new_card_num);
  assert(0 <= bucket && bucket < _n_card_counts, "Bounds");

  CardCountCacheEntry* count_ptr = &_card_counts[bucket];
  CardEpochCacheEntry* epoch_ptr = &_card_epochs[bucket];

  // We have to construct a new entry if we haven't updated the counts
  // during the current period, or if the count was updated for a
  // different card number.
  unsigned int new_epoch = (unsigned int) _n_periods;
  julong new_epoch_entry = make_epoch_entry(new_card_num, new_epoch);

  while (true) {
    // Fetch the previous epoch value
    julong prev_epoch_entry = epoch_ptr->_value;
    julong cas_res;

    if (extract_epoch(prev_epoch_entry) != new_epoch) {
      // This entry has not yet been updated during this period.
      // Note: we update the epoch value atomically to ensure
      // that there is only one winner that updates the cached
      // card_ptr value even though all the refine threads share
      // the same epoch value.

      cas_res = (julong) Atomic::cmpxchg((jlong) new_epoch_entry,
                                         (volatile jlong*)&epoch_ptr->_value,
                                         (jlong) prev_epoch_entry);

      if (cas_res == prev_epoch_entry) {
        // We have successfully won the race to update the
        // epoch and card_num value. Make it look like the
        // count and eviction count were previously cleared.
        count_ptr->_count = 1;
        count_ptr->_evict_count = 0;
        *count = 0;
        // We can defer the processing of card_ptr
        *defer = true;
        return card_ptr;
      }
      // We did not win the race to update the epoch field, so some other
      // thread must have done it. The value that gets returned by CAS
      // should be the new epoch value.
      assert(extract_epoch(cas_res) == new_epoch, "unexpected epoch");
      // We could 'continue' here or just re-read the previous epoch value
      prev_epoch_entry = epoch_ptr->_value;
    }

    // The epoch entry for card_ptr has been updated during this period.
    unsigned old_card_num = extract_card_num(prev_epoch_entry);

    // The card count that will be returned to caller
    *count = count_ptr->_count;

    // Are we updating the count for the same card?
    if (new_card_num == old_card_num) {
      // Same card - just update the count. We could have more than one
      // thread racing to update count for the current card. It should be
      // OK not to use a CAS as the only penalty should be some missed
      // increments of the count which delays identifying the card as "hot".

      if (*count < max_jubyte) count_ptr->_count++;
      // We can defer the processing of card_ptr
      *defer = true;
      return card_ptr;
    }

    // Different card - evict old card info
    if (count_ptr->_evict_count < max_jubyte) count_ptr->_evict_count++;
    if (count_ptr->_evict_count > G1CardCountCacheExpandThreshold) {
      // Trigger a resize the next time we clear
      _expand_card_counts = true;
    }

    cas_res = (julong) Atomic::cmpxchg((jlong) new_epoch_entry,
                                       (volatile jlong*)&epoch_ptr->_value,
                                       (jlong) prev_epoch_entry);

    if (cas_res == prev_epoch_entry) {
      // We successfully updated the card num value in the epoch entry
      count_ptr->_count = 0; // initialize counter for new card num
      jbyte* old_card_ptr = card_num_2_ptr(old_card_num);

      // Even though the region containg the card at old_card_num was not
      // in the young list when old_card_num was recorded in the epoch
      // cache it could have been added to the free list and subsequently
      // added to the young list in the intervening time. See CR 6817995.
      // We do not deal with this case here - it will be handled in
      // HeapRegion::oops_on_card_seq_iterate_careful after it has been
      // determined that the region containing the card has been allocated
      // to, and it's safe to check the young type of the region.

      // We do not want to defer processing of card_ptr in this case
      // (we need to refine old_card_ptr and card_ptr)
      *defer = false;
      return old_card_ptr;
    }
    // Someone else beat us - try again.
  }
}

jbyte* ConcurrentG1Refine::cache_insert(jbyte* card_ptr, bool* defer) {
  int count;
  jbyte* cached_ptr = add_card_count(card_ptr, &count, defer);
  assert(cached_ptr != NULL, "bad cached card ptr");

  // We've just inserted a card pointer into the card count cache
  // and got back the card that we just inserted or (evicted) the
  // previous contents of that count slot.

  // The card we got back could be in a young region. When the
  // returned card (if evicted) was originally inserted, we had
  // determined that its containing region was not young. However
  // it is possible for the region to be freed during a cleanup
  // pause, then reallocated and tagged as young which will result
  // in the returned card residing in a young region.
  //
  // We do not deal with this case here - the change from non-young
  // to young could be observed at any time - it will be handled in
  // HeapRegion::oops_on_card_seq_iterate_careful after it has been
  // determined that the region containing the card has been allocated
  // to.

  // The card pointer we obtained from card count cache is not hot
  // so do not store it in the cache; return it for immediate
  // refining.
  if (count < G1ConcRSHotCardLimit) {
    return cached_ptr;
  }

  // Otherwise, the pointer we got from the _card_counts cache is hot.
  jbyte* res = NULL;
  MutexLockerEx x(HotCardCache_lock, Mutex::_no_safepoint_check_flag);
  if (_n_hot == _hot_cache_size) {
    res = _hot_cache[_hot_cache_idx];
    _n_hot--;
  }
  // Now _n_hot < _hot_cache_size, and we can insert at _hot_cache_idx.
  _hot_cache[_hot_cache_idx] = cached_ptr;
  _hot_cache_idx++;
  if (_hot_cache_idx == _hot_cache_size) _hot_cache_idx = 0;
  _n_hot++;

  // The card obtained from the hot card cache could be in a young
  // region. See above on how this can happen.

  return res;
}

void ConcurrentG1Refine::clean_up_cache(int worker_i,
                                        G1RemSet* g1rs,
                                        DirtyCardQueue* into_cset_dcq) {
  assert(!use_cache(), "cache should be disabled");
  int start_idx;

  while ((start_idx = _hot_cache_par_claimed_idx) < _n_hot) { // read once
    int end_idx = start_idx + _hot_cache_par_chunk_size;

    if (start_idx ==
        Atomic::cmpxchg(end_idx, &_hot_cache_par_claimed_idx, start_idx)) {
      // The current worker has successfully claimed the chunk [start_idx..end_idx)
      end_idx = MIN2(end_idx, _n_hot);
      for (int i = start_idx; i < end_idx; i++) {
        jbyte* entry = _hot_cache[i];
        if (entry != NULL) {
          if (g1rs->concurrentRefineOneCard(entry, worker_i, true)) {
            // 'entry' contains references that point into the current
            // collection set. We need to record 'entry' in the DCQS
            // that's used for that purpose.
            //
            // The only time we care about recording cards that contain
            // references that point into the collection set is during
            // RSet updating while within an evacuation pause.
            // In this case worker_i should be the id of a GC worker thread
            assert(SafepointSynchronize::is_at_safepoint(), "not during an evacuation pause");
            assert(worker_i < (int) (ParallelGCThreads == 0 ? 1 : ParallelGCThreads), "incorrect worker id");
            into_cset_dcq->enqueue(entry);
          }
        }
      }
    }
  }
}

// The arrays used to hold the card counts and the epochs must have
// a 1:1 correspondence. Hence they are allocated and freed together
// Returns true if the allocations of both the counts and epochs
// were successful; false otherwise.
bool ConcurrentG1Refine::allocate_card_count_cache(size_t n,
                                                   CardCountCacheEntry** counts,
                                                   CardEpochCacheEntry** epochs) {
  // We call the allocation/free routines directly for the counts
  // and epochs arrays. The NEW_C_HEAP_ARRAY/FREE_C_HEAP_ARRAY
  // macros call AllocateHeap and FreeHeap respectively.
  // AllocateHeap will call vm_exit_out_of_memory in the event
  // of an allocation failure and abort the JVM. With the
  // _counts/epochs arrays we only need to abort the JVM if the
  // initial allocation of these arrays fails.
  //
  // Additionally AllocateHeap/FreeHeap do some tracing of
  // allocate/free calls so calling one without calling the
  // other can cause inconsistencies in the tracing. So we
  // call neither.

  assert(*counts == NULL, "out param");
  assert(*epochs == NULL, "out param");

  size_t counts_size = n * sizeof(CardCountCacheEntry);
  size_t epochs_size = n * sizeof(CardEpochCacheEntry);

  *counts = (CardCountCacheEntry*) os::malloc(counts_size, mtGC);
  if (*counts == NULL) {
    // allocation was unsuccessful
    return false;
  }

  *epochs = (CardEpochCacheEntry*) os::malloc(epochs_size, mtGC);
  if (*epochs == NULL) {
    // allocation was unsuccessful - free counts array
    assert(*counts != NULL, "must be");
    os::free(*counts, mtGC);
    *counts = NULL;
    return false;
  }

  // We successfully allocated both counts and epochs
  return true;
}

// Returns true if the card counts/epochs cache was
// successfully expanded; false otherwise.
bool ConcurrentG1Refine::expand_card_count_cache(int cache_size_idx) {
  // Can we expand the card count and epoch tables?
  if (_n_card_counts < _max_n_card_counts) {
    assert(cache_size_idx >= 0 && cache_size_idx  < MAX_CC_CACHE_INDEX, "oob");

    size_t cache_size = _cc_cache_sizes[cache_size_idx];
    // Make sure we don't go bigger than we will ever need
    cache_size = MIN2(cache_size, _max_n_card_counts);

    // Should we expand the card count and card epoch tables?
    if (cache_size > _n_card_counts) {
      // We have been asked to allocate new, larger, arrays for
      // the card counts and the epochs. Attempt the allocation
      // of both before we free the existing arrays in case
      // the allocation is unsuccessful...
      CardCountCacheEntry* counts = NULL;
      CardEpochCacheEntry* epochs = NULL;

      if (allocate_card_count_cache(cache_size, &counts, &epochs)) {
        // Allocation was successful.
        // We can just free the old arrays; we're
        // not interested in preserving the contents
        if (_card_counts != NULL) os::free(_card_counts, mtGC);
        if (_card_epochs != NULL) os::free(_card_epochs, mtGC);

        // Cache the size of the arrays and the index that got us there.
        _n_card_counts = cache_size;
        _cache_size_index = cache_size_idx;

        _card_counts = counts;
        _card_epochs = epochs;

        // We successfully allocated/expanded the caches.
        return true;
      }
    }
  }

  // We did not successfully expand the caches.
  return false;
}

void ConcurrentG1Refine::clear_and_record_card_counts() {
  if (G1ConcRSLogCacheSize == 0) return;

#ifndef PRODUCT
  double start = os::elapsedTime();
#endif

  if (_expand_card_counts) {
    int new_idx = _cache_size_index + 1;

    if (expand_card_count_cache(new_idx)) {
      // Allocation was successful and  _n_card_counts has
      // been updated to the new size. We only need to clear
      // the epochs so we don't read a bogus epoch value
      // when inserting a card into the hot card cache.
      Copy::fill_to_bytes(&_card_epochs[0], _n_card_counts * sizeof(CardEpochCacheEntry));
    }
    _expand_card_counts = false;
  }

  int this_epoch = (int) _n_periods;
  assert((this_epoch+1) <= max_jint, "to many periods");
  // Update epoch
  _n_periods++;

#ifndef PRODUCT
  double elapsed = os::elapsedTime() - start;
  _g1h->g1_policy()->record_cc_clear_time(elapsed * 1000.0);
#endif
}

void ConcurrentG1Refine::print_worker_threads_on(outputStream* st) const {
  for (int i = 0; i < _n_threads; ++i) {
    _threads[i]->print_on(st);
    st->cr();
  }
}

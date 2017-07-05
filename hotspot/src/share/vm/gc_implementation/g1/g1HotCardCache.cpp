/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/dirtyCardQueue.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1HotCardCache.hpp"
#include "gc_implementation/g1/g1RemSet.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#include "runtime/atomic.hpp"

G1HotCardCache::G1HotCardCache(G1CollectedHeap *g1h):
  _g1h(g1h), _hot_cache(NULL), _use_cache(false), _card_counts(g1h) {}

void G1HotCardCache::initialize() {
  if (default_use_cache()) {
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

    _card_counts.initialize();
  }
}

G1HotCardCache::~G1HotCardCache() {
  if (default_use_cache()) {
    assert(_hot_cache != NULL, "Logic");
    FREE_C_HEAP_ARRAY(jbyte*, _hot_cache, mtGC);
  }
}

jbyte* G1HotCardCache::insert(jbyte* card_ptr) {
  uint count = _card_counts.add_card_count(card_ptr);
  if (!_card_counts.is_hot(count)) {
    // The card is not hot so do not store it in the cache;
    // return it for immediate refining.
    return card_ptr;
  }

  // Otherwise, the card is hot.
  jbyte* res = NULL;
  MutexLockerEx x(HotCardCache_lock, Mutex::_no_safepoint_check_flag);
  if (_n_hot == _hot_cache_size) {
    res = _hot_cache[_hot_cache_idx];
    _n_hot--;
  }

  // Now _n_hot < _hot_cache_size, and we can insert at _hot_cache_idx.
  _hot_cache[_hot_cache_idx] = card_ptr;
  _hot_cache_idx++;

  if (_hot_cache_idx == _hot_cache_size) {
    // Wrap around
    _hot_cache_idx = 0;
  }
  _n_hot++;

  return res;
}

void G1HotCardCache::drain(int worker_i,
                           G1RemSet* g1rs,
                           DirtyCardQueue* into_cset_dcq) {
  if (!default_use_cache()) {
    assert(_hot_cache == NULL, "Logic");
    return;
  }

  assert(_hot_cache != NULL, "Logic");
  assert(!use_cache(), "cache should be disabled");
  int start_idx;

  while ((start_idx = _hot_cache_par_claimed_idx) < _n_hot) { // read once
    int end_idx = start_idx + _hot_cache_par_chunk_size;

    if (start_idx ==
        Atomic::cmpxchg(end_idx, &_hot_cache_par_claimed_idx, start_idx)) {
      // The current worker has successfully claimed the chunk [start_idx..end_idx)
      end_idx = MIN2(end_idx, _n_hot);
      for (int i = start_idx; i < end_idx; i++) {
        jbyte* card_ptr = _hot_cache[i];
        if (card_ptr != NULL) {
          if (g1rs->refine_card(card_ptr, worker_i, true)) {
            // The part of the heap spanned by the card contains references
            // that point into the current collection set.
            // We need to record the card pointer in the DirtyCardQueueSet
            // that we use for such cards.
            //
            // The only time we care about recording cards that contain
            // references that point into the collection set is during
            // RSet updating while within an evacuation pause.
            // In this case worker_i should be the id of a GC worker thread
            assert(SafepointSynchronize::is_at_safepoint(), "Should be at a safepoint");
            assert(worker_i < (int) (ParallelGCThreads == 0 ? 1 : ParallelGCThreads),
                   err_msg("incorrect worker id: "INT32_FORMAT, worker_i));

            into_cset_dcq->enqueue(card_ptr);
          }
        }
      }
    }
  }
  // The existing entries in the hot card cache, which were just refined
  // above, are discarded prior to re-enabling the cache near the end of the GC.
}

void G1HotCardCache::resize_card_counts(size_t heap_capacity) {
  _card_counts.resize(heap_capacity);
}

void G1HotCardCache::reset_card_counts(HeapRegion* hr) {
  _card_counts.clear_region(hr);
}

void G1HotCardCache::reset_card_counts() {
  _card_counts.clear_all();
}

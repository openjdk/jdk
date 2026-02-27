/*
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

#include "gc/shared/cardTable.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahPLAB.hpp"
#include "logging/log.hpp"
#include "runtime/javaThread.hpp"

ShenandoahPLAB::ShenandoahPLAB() :
  _plab(nullptr),
  _desired_size(0),
  _actual_size(0),
  _promoted(0),
  _allows_promotion(false),
  _retries_enabled(false) {
  _plab = new PLAB(align_up(PLAB::min_size(), CardTable::card_size_in_words()));
}

ShenandoahPLAB::~ShenandoahPLAB() {
  if (_plab != nullptr) {
    delete _plab;
  }
}

void ShenandoahPLAB::subtract_from_promoted(size_t increment) {
  assert(_promoted >= increment, "Cannot subtract more than remaining promoted");
  _promoted -= increment;
}

HeapWord* ShenandoahPLAB::allocate(size_t size, bool is_promotion) {
  assert(UseTLAB, "TLABs should be enabled");

  if (_plab == nullptr) {
    // No PLABs in this thread, fallback to shared allocation
    return nullptr;
  }

  if (is_promotion && !_allows_promotion) {
    // Thread is not allowed to promote
    return nullptr;
  }

  HeapWord* obj = _plab->allocate(size);
  if (obj == nullptr) {
    auto heap = ShenandoahGenerationalHeap::heap();
    if (plab->words_remaining() < heap->plab_min_size()) {
      // allocate_slow will establish _allows_promotion for future invocations
      obj = allocate_slow(size, is_promotion, heap);
    }
  }

  // if plab->words_remaining() >= ShenGenHeap::heap()->plab_min_size(), just return nullptr so we can use a shared allocation
  if (obj == nullptr) {
    return nullptr;
  }

  if (is_promotion) {
    add_to_promoted(size * HeapWordSize);
  }
  return obj;
}

// Establish a new PLAB and allocate size HeapWords within it.
HeapWord* ShenandoahPLAB::allocate_slow(size_t size, bool is_promotion, ShenandoahGenerationalHeap* heap) {
  assert(heap->mode()->is_generational(), "PLABs only relevant to generational GC");

  // PLABs are aligned to card boundaries to avoid synchronization with concurrent
  // allocations in other PLABs.
  const size_t plab_min_size = heap->plab_min_size();
  const size_t min_size = (size > plab_min_size)? align_up(size, CardTable::card_size_in_words()): plab_min_size;

  // Figure out size of new PLAB, using value determined at last refill.
  size_t cur_size = _desired_size;
  if (cur_size == 0) {
    cur_size = plab_min_size;
  }

  // Expand aggressively, doubling at each refill in this epoch, ceiling at plab_max_size()
  // Doubling, starting at a card-multiple, should give us a card-multiple. (Ceiling and floor
  // are card multiples.)
  const size_t future_size = MIN2(cur_size * 2, heap->plab_max_size());
  assert(is_aligned(future_size, CardTable::card_size_in_words()), "Card multiple by construction, future_size: %zu"
          ", card_size: %u, cur_size: %zu, max: %zu",
         future_size, CardTable::card_size_in_words(), cur_size, heap->plab_max_size());

  // Record new heuristic value even if we take any shortcut. This captures
  // the case when moderately-sized objects always take a shortcut. At some point,
  // heuristics should catch up with them.  Note that the requested cur_size may
  // not be honored, but we remember that this is the preferred size.
  log_debug(gc, plab)("Set next PLAB refill size: %zu bytes", future_size * HeapWordSize);
  set_desired_size(future_size);

  if (cur_size < size) {
    // The PLAB to be allocated is still not large enough to hold the object. Fall back to shared allocation.
    // This avoids retiring perfectly good PLABs in order to represent a single large object allocation.
    log_debug(gc, plab)("Current PLAB size (%zu) is too small for %zu", cur_size * HeapWordSize, size * HeapWordSize);
    return nullptr;
  }

  if (_plab->words_remaining() < plab_min_size) {
    // Retire current PLAB. This takes care of any PLAB book-keeping.
    // retire_plab() registers the remnant filler object with the remembered set scanner without a lock.
    // Since PLABs are card-aligned, concurrent registrations in other PLABs don't interfere.
    retire(heap);

    size_t actual_size = 0;
    HeapWord* plab_buf = heap->allocate_new_plab(min_size, cur_size, &actual_size);
    if (plab_buf == nullptr) {
      if (min_size == plab_min_size) {
        // Disable PLAB promotions for this thread because we cannot even allocate a minimal PLAB. This allows us
        // to fail faster on subsequent promotion attempts.
        disable_promotions();
      }
      return nullptr;
    }

    enable_retries();

    // Since the allocated PLAB may have been down-sized for alignment, plab->allocate(size) below may still fail.
    if (ZeroTLAB) {
      // Skip mangling the space corresponding to the object header to
      // ensure that the returned space is not considered parsable by
      // any concurrent GC thread.
      Copy::zero_to_words(plab_buf, actual_size);
    } else {
#ifdef ASSERT
      size_t hdr_size = oopDesc::header_size();
      Copy::fill_to_words(plab_buf + hdr_size, actual_size - hdr_size, badHeapWordVal);
#endif
    }
    assert(is_aligned(actual_size, CardTable::card_size_in_words()), "Align by design");
    _plab->set_buf(plab_buf, actual_size);
    if (is_promotion && !_allows_promotion) {
      return nullptr;
    }
    return _plab->allocate(size);
  }

  // If there's still at least min_size() words available within the current plab, don't retire it.  Let's nibble
  // away on this plab as long as we can.  Meanwhile, return nullptr to force this particular allocation request
  // to be satisfied with a shared allocation.  By packing more promotions into the previously allocated PLAB, we
  // reduce the likelihood of evacuation failures, and we reduce the need for downsizing our PLABs.
  return nullptr;
}

void ShenandoahPLAB::retire(ShenandoahGenerationalHeap* heap) {
  // We don't enforce limits on plab evacuations.  We let it consume all available old-gen memory in order to reduce
  // probability of an evacuation failure.  We do enforce limits on promotion, to make sure that excessive promotion
  // does not result in an old-gen evacuation failure.  Note that a failed promotion is relatively harmless.  Any
  // object that fails to promote in the current cycle will be eligible for promotion in a subsequent cycle.

  // When the plab was instantiated, its entirety was treated as if the entire buffer was going to be dedicated to
  // promotions.  Now that we are retiring the buffer, we adjust for the reality that the plab is not entirely promotions.
  //  1. Some of the plab may have been dedicated to evacuations.
  //  2. Some of the plab may have been abandoned due to waste (at the end of the plab).
  size_t not_promoted = _actual_size - _promoted;
  reset_promoted();
  set_actual_size(0);
  if (not_promoted > 0) {
    log_debug(gc, plab)("Retire PLAB, unexpend unpromoted: %zu", not_promoted * HeapWordSize);
    heap->old_generation()->unexpend_promoted(not_promoted);
  }
  const size_t original_waste = _plab->waste();
  HeapWord* const top = _plab->top();

  // plab->retire() overwrites unused memory between plab->top() and plab->hard_end() with a dummy object to make memory parsable.
  // It adds the size of this unused memory, in words, to plab->waste().
  _plab->retire();
  if (top != nullptr && _plab->waste() > original_waste && heap->is_in_old(top)) {
    // If retiring the plab created a filler object, then we need to register it with our card scanner so it can
    // safely walk the region backing the plab.
    log_debug(gc, plab)("retire_plab() is registering remnant of size %zu at " PTR_FORMAT,
                        (_plab->waste() - original_waste) * HeapWordSize, p2i(top));
    // No lock is necessary because the PLAB memory is aligned on card boundaries.
    heap->old_generation()->card_scan()->register_object_without_lock(top);
  }
}

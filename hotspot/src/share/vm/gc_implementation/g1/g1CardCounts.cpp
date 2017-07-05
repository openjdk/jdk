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
#include "gc_implementation/g1/g1CardCounts.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/g1GCPhaseTimes.hpp"
#include "memory/cardTableModRefBS.hpp"
#include "services/memTracker.hpp"
#include "utilities/copy.hpp"

void G1CardCounts::clear_range(size_t from_card_num, size_t to_card_num) {
  if (has_count_table()) {
    assert(from_card_num >= 0 && from_card_num < _committed_max_card_num,
           err_msg("from card num out of range: "SIZE_FORMAT, from_card_num));
    assert(from_card_num < to_card_num,
           err_msg("Wrong order? from: " SIZE_FORMAT ", to: "SIZE_FORMAT,
                   from_card_num, to_card_num));
    assert(to_card_num <= _committed_max_card_num,
           err_msg("to card num out of range: "
                   "to: "SIZE_FORMAT ", "
                   "max: "SIZE_FORMAT,
                   to_card_num, _committed_max_card_num));

    to_card_num = MIN2(_committed_max_card_num, to_card_num);

    Copy::fill_to_bytes(&_card_counts[from_card_num], (to_card_num - from_card_num));
  }
}

G1CardCounts::G1CardCounts(G1CollectedHeap *g1h):
  _g1h(g1h), _card_counts(NULL),
  _reserved_max_card_num(0), _committed_max_card_num(0),
  _committed_size(0) {}

void G1CardCounts::initialize() {
  assert(_g1h->max_capacity() > 0, "initialization order");
  assert(_g1h->capacity() == 0, "initialization order");

  if (G1ConcRSHotCardLimit > 0) {
    // The max value we can store in the counts table is
    // max_jubyte. Guarantee the value of the hot
    // threshold limit is no more than this.
    guarantee(G1ConcRSHotCardLimit <= max_jubyte, "sanity");

    _ct_bs = _g1h->g1_barrier_set();
    _ct_bot = _ct_bs->byte_for_const(_g1h->reserved_region().start());

    // Allocate/Reserve the counts table
    size_t reserved_bytes = _g1h->max_capacity();
    _reserved_max_card_num = reserved_bytes >> CardTableModRefBS::card_shift;

    size_t reserved_size = _reserved_max_card_num * sizeof(jbyte);
    ReservedSpace rs(ReservedSpace::allocation_align_size_up(reserved_size));
    if (!rs.is_reserved()) {
      warning("Could not reserve enough space for the card counts table");
      guarantee(!has_reserved_count_table(), "should be NULL");
      return;
    }

    MemTracker::record_virtual_memory_type((address)rs.base(), mtGC);

    _card_counts_storage.initialize(rs, 0);
    _card_counts = (jubyte*) _card_counts_storage.low();
  }
}

void G1CardCounts::resize(size_t heap_capacity) {
  // Expand the card counts table to handle a heap with the given capacity.

  if (!has_reserved_count_table()) {
    // Don't expand if we failed to reserve the card counts table.
    return;
  }

  assert(_committed_size ==
         ReservedSpace::allocation_align_size_up(_committed_size),
         err_msg("Unaligned? committed_size: " SIZE_FORMAT, _committed_size));

  // Verify that the committed space for the card counts matches our
  // committed max card num. Note for some allocation alignments, the
  // amount of space actually committed for the counts table will be able
  // to span more cards than the number spanned by the maximum heap.
  size_t prev_committed_size = _committed_size;
  size_t prev_committed_card_num = committed_to_card_num(prev_committed_size);

  assert(prev_committed_card_num == _committed_max_card_num,
         err_msg("Card mismatch: "
                 "prev: " SIZE_FORMAT ", "
                 "committed: "SIZE_FORMAT", "
                 "reserved: "SIZE_FORMAT,
                 prev_committed_card_num, _committed_max_card_num, _reserved_max_card_num));

  size_t new_size = (heap_capacity >> CardTableModRefBS::card_shift) * sizeof(jbyte);
  size_t new_committed_size = ReservedSpace::allocation_align_size_up(new_size);
  size_t new_committed_card_num = committed_to_card_num(new_committed_size);

  if (_committed_max_card_num < new_committed_card_num) {
    // we need to expand the backing store for the card counts
    size_t expand_size = new_committed_size - prev_committed_size;

    if (!_card_counts_storage.expand_by(expand_size)) {
      warning("Card counts table backing store commit failure");
      return;
    }
    assert(_card_counts_storage.committed_size() == new_committed_size,
           "expansion commit failure");

    _committed_size = new_committed_size;
    _committed_max_card_num = new_committed_card_num;

    clear_range(prev_committed_card_num, _committed_max_card_num);
  }
}

uint G1CardCounts::add_card_count(jbyte* card_ptr) {
  // Returns the number of times the card has been refined.
  // If we failed to reserve/commit the counts table, return 0.
  // If card_ptr is beyond the committed end of the counts table,
  // return 0.
  // Otherwise return the actual count.
  // Unless G1ConcRSHotCardLimit has been set appropriately,
  // returning 0 will result in the card being considered
  // cold and will be refined immediately.
  uint count = 0;
  if (has_count_table()) {
    size_t card_num = ptr_2_card_num(card_ptr);
    if (card_num < _committed_max_card_num) {
      count = (uint) _card_counts[card_num];
      if (count < G1ConcRSHotCardLimit) {
        _card_counts[card_num] =
          (jubyte)(MIN2((uintx)(_card_counts[card_num] + 1), G1ConcRSHotCardLimit));
      }
    }
  }
  return count;
}

bool G1CardCounts::is_hot(uint count) {
  return (count >= G1ConcRSHotCardLimit);
}

void G1CardCounts::clear_region(HeapRegion* hr) {
  assert(!hr->isHumongous(), "Should have been cleared");
  if (has_count_table()) {
    HeapWord* bottom = hr->bottom();

    // We use the last address in hr as hr could be the
    // last region in the heap. In which case trying to find
    // the card for hr->end() will be an OOB accesss to the
    // card table.
    HeapWord* last = hr->end() - 1;
    assert(_g1h->g1_committed().contains(last),
           err_msg("last not in committed: "
                   "last: " PTR_FORMAT ", "
                   "committed: [" PTR_FORMAT ", " PTR_FORMAT ")",
                   last,
                   _g1h->g1_committed().start(),
                   _g1h->g1_committed().end()));

    const jbyte* from_card_ptr = _ct_bs->byte_for_const(bottom);
    const jbyte* last_card_ptr = _ct_bs->byte_for_const(last);

#ifdef ASSERT
    HeapWord* start_addr = _ct_bs->addr_for(from_card_ptr);
    assert(start_addr == hr->bottom(), "alignment");
    HeapWord* last_addr = _ct_bs->addr_for(last_card_ptr);
    assert((last_addr + CardTableModRefBS::card_size_in_words) == hr->end(), "alignment");
#endif // ASSERT

    // Clear the counts for the (exclusive) card range.
    size_t from_card_num = ptr_2_card_num(from_card_ptr);
    size_t to_card_num = ptr_2_card_num(last_card_ptr) + 1;
    clear_range(from_card_num, to_card_num);
  }
}

void G1CardCounts::clear_all() {
  assert(SafepointSynchronize::is_at_safepoint(), "don't call this otherwise");
  clear_range((size_t)0, _committed_max_card_num);
}

G1CardCounts::~G1CardCounts() {
  if (has_reserved_count_table()) {
    _card_counts_storage.release();
  }
}


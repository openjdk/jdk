/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/blockOffsetTable.inline.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/space.inline.hpp"
#include "memory/iterator.hpp"
#include "memory/universe.hpp"
#include "logging/log.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "services/memTracker.hpp"

//////////////////////////////////////////////////////////////////////
// BlockOffsetSharedArray
//////////////////////////////////////////////////////////////////////

BlockOffsetSharedArray::BlockOffsetSharedArray(MemRegion reserved,
                                               size_t init_word_size):
  _reserved(reserved), _end(NULL)
{
  size_t size = compute_size(reserved.word_size());
  ReservedSpace rs(size);
  if (!rs.is_reserved()) {
    vm_exit_during_initialization("Could not reserve enough space for heap offset array");
  }

  MemTracker::record_virtual_memory_type((address)rs.base(), mtGC);

  if (!_vs.initialize(rs, 0)) {
    vm_exit_during_initialization("Could not reserve enough space for heap offset array");
  }
  _offset_array = (u_char*)_vs.low_boundary();
  resize(init_word_size);
  log_trace(gc, bot)("BlockOffsetSharedArray::BlockOffsetSharedArray: ");
  log_trace(gc, bot)("   rs.base(): " INTPTR_FORMAT " rs.size(): " INTPTR_FORMAT " rs end(): " INTPTR_FORMAT,
                     p2i(rs.base()), rs.size(), p2i(rs.base() + rs.size()));
  log_trace(gc, bot)("   _vs.low_boundary(): " INTPTR_FORMAT "  _vs.high_boundary(): " INTPTR_FORMAT,
                     p2i(_vs.low_boundary()), p2i(_vs.high_boundary()));
}

void BlockOffsetSharedArray::resize(size_t new_word_size) {
  assert(new_word_size <= _reserved.word_size(), "Resize larger than reserved");
  size_t new_size = compute_size(new_word_size);
  size_t old_size = _vs.committed_size();
  size_t delta;
  char* high = _vs.high();
  _end = _reserved.start() + new_word_size;
  if (new_size > old_size) {
    delta = ReservedSpace::page_align_size_up(new_size - old_size);
    assert(delta > 0, "just checking");
    if (!_vs.expand_by(delta)) {
      // Do better than this for Merlin
      vm_exit_out_of_memory(delta, OOM_MMAP_ERROR, "offset table expansion");
    }
    assert(_vs.high() == high + delta, "invalid expansion");
  } else {
    delta = ReservedSpace::page_align_size_down(old_size - new_size);
    if (delta == 0) return;
    _vs.shrink_by(delta);
    assert(_vs.high() == high - delta, "invalid expansion");
  }
}

bool BlockOffsetSharedArray::is_card_boundary(HeapWord* p) const {
  assert(p >= _reserved.start(), "just checking");
  size_t delta = pointer_delta(p, _reserved.start());
  return (delta & right_n_bits(LogN_words)) == (size_t)NoBits;
}


//////////////////////////////////////////////////////////////////////
// BlockOffsetArray
//////////////////////////////////////////////////////////////////////

BlockOffsetArray::BlockOffsetArray(BlockOffsetSharedArray* array,
                                   MemRegion mr, bool init_to_zero_) :
  BlockOffsetTable(mr.start(), mr.end()),
  _array(array)
{
  assert(_bottom <= _end, "arguments out of order");
  set_init_to_zero(init_to_zero_);
  if (!init_to_zero_) {
    // initialize cards to point back to mr.start()
    set_remainder_to_point_to_start(mr.start() + N_words, mr.end());
    _array->set_offset_array(0, 0);  // set first card to 0
  }
}


// The arguments follow the normal convention of denoting
// a right-open interval: [start, end)
void
BlockOffsetArray::
set_remainder_to_point_to_start(HeapWord* start, HeapWord* end, bool reducing) {

  check_reducing_assertion(reducing);
  if (start >= end) {
    // The start address is equal to the end address (or to
    // the right of the end address) so there are not cards
    // that need to be updated..
    return;
  }

  // Write the backskip value for each region.
  //
  //    offset
  //    card             2nd                       3rd
  //     | +- 1st        |                         |
  //     v v             v                         v
  //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+-+-+-
  //    |x|0|0|0|0|0|0|0|1|1|1|1|1|1| ... |1|1|1|1|2|2|2|2|2|2| ...
  //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+     +-+-+-+-+-+-+-+-+-+-+-
  //    11              19                        75
  //      12
  //
  //    offset card is the card that points to the start of an object
  //      x - offset value of offset card
  //    1st - start of first logarithmic region
  //      0 corresponds to logarithmic value N_words + 0 and 2**(3 * 0) = 1
  //    2nd - start of second logarithmic region
  //      1 corresponds to logarithmic value N_words + 1 and 2**(3 * 1) = 8
  //    3rd - start of third logarithmic region
  //      2 corresponds to logarithmic value N_words + 2 and 2**(3 * 2) = 64
  //
  //    integer below the block offset entry is an example of
  //    the index of the entry
  //
  //    Given an address,
  //      Find the index for the address
  //      Find the block offset table entry
  //      Convert the entry to a back slide
  //        (e.g., with today's, offset = 0x81 =>
  //          back slip = 2**(3*(0x81 - N_words)) = 2**3) = 8
  //      Move back N (e.g., 8) entries and repeat with the
  //        value of the new entry
  //
  size_t start_card = _array->index_for(start);
  size_t end_card = _array->index_for(end-1);
  assert(start ==_array->address_for_index(start_card), "Precondition");
  assert(end ==_array->address_for_index(end_card)+N_words, "Precondition");
  set_remainder_to_point_to_start_incl(start_card, end_card, reducing); // closed interval
}


// Unlike the normal convention in this code, the argument here denotes
// a closed, inclusive interval: [start_card, end_card], cf set_remainder_to_point_to_start()
// above.
void
BlockOffsetArray::set_remainder_to_point_to_start_incl(size_t start_card, size_t end_card, bool reducing) {

  check_reducing_assertion(reducing);
  if (start_card > end_card) {
    return;
  }
  assert(start_card > _array->index_for(_bottom), "Cannot be first card");
  assert(_array->offset_array(start_card-1) <= N_words,
    "Offset card has an unexpected value");
  size_t start_card_for_region = start_card;
  u_char offset = max_jubyte;
  for (int i = 0; i < N_powers; i++) {
    // -1 so that the the card with the actual offset is counted.  Another -1
    // so that the reach ends in this region and not at the start
    // of the next.
    size_t reach = start_card - 1 + (power_to_cards_back(i+1) - 1);
    offset = N_words + i;
    if (reach >= end_card) {
      _array->set_offset_array(start_card_for_region, end_card, offset, reducing);
      start_card_for_region = reach + 1;
      break;
    }
    _array->set_offset_array(start_card_for_region, reach, offset, reducing);
    start_card_for_region = reach + 1;
  }
  assert(start_card_for_region > end_card, "Sanity check");
  DEBUG_ONLY(check_all_cards(start_card, end_card);)
}

// The card-interval [start_card, end_card] is a closed interval; this
// is an expensive check -- use with care and only under protection of
// suitable flag.
void BlockOffsetArray::check_all_cards(size_t start_card, size_t end_card) const {

  if (end_card < start_card) {
    return;
  }
  guarantee(_array->offset_array(start_card) == N_words, "Wrong value in second card");
  u_char last_entry = N_words;
  for (size_t c = start_card + 1; c <= end_card; c++ /* yeah! */) {
    u_char entry = _array->offset_array(c);
    guarantee(entry >= last_entry, "Monotonicity");
    if (c - start_card > power_to_cards_back(1)) {
      guarantee(entry > N_words, "Should be in logarithmic region");
    }
    size_t backskip = entry_to_cards_back(entry);
    size_t landing_card = c - backskip;
    guarantee(landing_card >= (start_card - 1), "Inv");
    if (landing_card >= start_card) {
      guarantee(_array->offset_array(landing_card) <= entry, "Monotonicity");
    } else {
      guarantee(landing_card == (start_card - 1), "Tautology");
      // Note that N_words is the maximum offset value
      guarantee(_array->offset_array(landing_card) <= N_words, "Offset value");
    }
    last_entry = entry;  // remember for monotonicity test
  }
}


void
BlockOffsetArray::alloc_block(HeapWord* blk_start, HeapWord* blk_end) {
  assert(blk_start != NULL && blk_end > blk_start,
         "phantom block");
  single_block(blk_start, blk_end);
}

// Action_mark - update the BOT for the block [blk_start, blk_end).
//               Current typical use is for splitting a block.
// Action_single - udpate the BOT for an allocation.
// Action_verify - BOT verification.
void
BlockOffsetArray::do_block_internal(HeapWord* blk_start,
                                    HeapWord* blk_end,
                                    Action action, bool reducing) {
  assert(Universe::heap()->is_in_reserved(blk_start),
         "reference must be into the heap");
  assert(Universe::heap()->is_in_reserved(blk_end-1),
         "limit must be within the heap");
  // This is optimized to make the test fast, assuming we only rarely
  // cross boundaries.
  uintptr_t end_ui = (uintptr_t)(blk_end - 1);
  uintptr_t start_ui = (uintptr_t)blk_start;
  // Calculate the last card boundary preceding end of blk
  intptr_t boundary_before_end = (intptr_t)end_ui;
  clear_bits(boundary_before_end, right_n_bits(LogN));
  if (start_ui <= (uintptr_t)boundary_before_end) {
    // blk starts at or crosses a boundary
    // Calculate index of card on which blk begins
    size_t    start_index = _array->index_for(blk_start);
    // Index of card on which blk ends
    size_t    end_index   = _array->index_for(blk_end - 1);
    // Start address of card on which blk begins
    HeapWord* boundary    = _array->address_for_index(start_index);
    assert(boundary <= blk_start, "blk should start at or after boundary");
    if (blk_start != boundary) {
      // blk starts strictly after boundary
      // adjust card boundary and start_index forward to next card
      boundary += N_words;
      start_index++;
    }
    assert(start_index <= end_index, "monotonicity of index_for()");
    assert(boundary <= (HeapWord*)boundary_before_end, "tautology");
    switch (action) {
      case Action_mark: {
        if (init_to_zero()) {
          _array->set_offset_array(start_index, boundary, blk_start, reducing);
          break;
        } // Else fall through to the next case
      }
      case Action_single: {
        _array->set_offset_array(start_index, boundary, blk_start, reducing);
        // We have finished marking the "offset card". We need to now
        // mark the subsequent cards that this blk spans.
        if (start_index < end_index) {
          HeapWord* rem_st = _array->address_for_index(start_index) + N_words;
          HeapWord* rem_end = _array->address_for_index(end_index) + N_words;
          set_remainder_to_point_to_start(rem_st, rem_end, reducing);
        }
        break;
      }
      case Action_check: {
        _array->check_offset_array(start_index, boundary, blk_start);
        // We have finished checking the "offset card". We need to now
        // check the subsequent cards that this blk spans.
        check_all_cards(start_index + 1, end_index);
        break;
      }
      default:
        ShouldNotReachHere();
    }
  }
}

// The range [blk_start, blk_end) represents a single contiguous block
// of storage; modify the block offset table to represent this
// information; Right-open interval: [blk_start, blk_end)
// NOTE: this method does _not_ adjust _unallocated_block.
void
BlockOffsetArray::single_block(HeapWord* blk_start,
                               HeapWord* blk_end) {
  do_block_internal(blk_start, blk_end, Action_single);
}

void BlockOffsetArray::verify() const {
  // For each entry in the block offset table, verify that
  // the entry correctly finds the start of an object at the
  // first address covered by the block or to the left of that
  // first address.

  size_t next_index = 1;
  size_t last_index = last_active_index();

  // Use for debugging.  Initialize to NULL to distinguish the
  // first iteration through the while loop.
  HeapWord* last_p = NULL;
  HeapWord* last_start = NULL;
  oop last_o = NULL;

  while (next_index <= last_index) {
    // Use an address past the start of the address for
    // the entry.
    HeapWord* p = _array->address_for_index(next_index) + 1;
    if (p >= _end) {
      // That's all of the allocated block table.
      return;
    }
    // block_start() asserts that start <= p.
    HeapWord* start = block_start(p);
    // First check if the start is an allocated block and only
    // then if it is a valid object.
    oop o = oop(start);
    assert(!Universe::is_fully_initialized() ||
           _sp->is_free_block(start) ||
           o->is_oop_or_null(), "Bad object was found");
    next_index++;
    last_p = p;
    last_start = start;
    last_o = o;
  }
}

//////////////////////////////////////////////////////////////////////
// BlockOffsetArrayNonContigSpace
//////////////////////////////////////////////////////////////////////

// The block [blk_start, blk_end) has been allocated;
// adjust the block offset table to represent this information;
// NOTE: Clients of BlockOffsetArrayNonContigSpace: consider using
// the somewhat more lightweight split_block() or
// (when init_to_zero()) mark_block() wherever possible.
// right-open interval: [blk_start, blk_end)
void
BlockOffsetArrayNonContigSpace::alloc_block(HeapWord* blk_start,
                                            HeapWord* blk_end) {
  assert(blk_start != NULL && blk_end > blk_start,
         "phantom block");
  single_block(blk_start, blk_end);
  allocated(blk_start, blk_end);
}

// Adjust BOT to show that a previously whole block has been split
// into two.  We verify the BOT for the first part (prefix) and
// update the  BOT for the second part (suffix).
//      blk is the start of the block
//      blk_size is the size of the original block
//      left_blk_size is the size of the first part of the split
void BlockOffsetArrayNonContigSpace::split_block(HeapWord* blk,
                                                 size_t blk_size,
                                                 size_t left_blk_size) {
  // Verify that the BOT shows [blk, blk + blk_size) to be one block.
  verify_single_block(blk, blk_size);
  // Update the BOT to indicate that [blk + left_blk_size, blk + blk_size)
  // is one single block.
  assert(blk_size > 0, "Should be positive");
  assert(left_blk_size > 0, "Should be positive");
  assert(left_blk_size < blk_size, "Not a split");

  // Start addresses of prefix block and suffix block.
  HeapWord* pref_addr = blk;
  HeapWord* suff_addr = blk + left_blk_size;
  HeapWord* end_addr  = blk + blk_size;

  // Indices for starts of prefix block and suffix block.
  size_t pref_index = _array->index_for(pref_addr);
  if (_array->address_for_index(pref_index) != pref_addr) {
    // pref_addr does not begin pref_index
    pref_index++;
  }

  size_t suff_index = _array->index_for(suff_addr);
  if (_array->address_for_index(suff_index) != suff_addr) {
    // suff_addr does not begin suff_index
    suff_index++;
  }

  // Definition: A block B, denoted [B_start, B_end) __starts__
  //     a card C, denoted [C_start, C_end), where C_start and C_end
  //     are the heap addresses that card C covers, iff
  //     B_start <= C_start < B_end.
  //
  //     We say that a card C "is started by" a block B, iff
  //     B "starts" C.
  //
  //     Note that the cardinality of the set of cards {C}
  //     started by a block B can be 0, 1, or more.
  //
  // Below, pref_index and suff_index are, respectively, the
  // first (least) card indices that the prefix and suffix of
  // the split start; end_index is one more than the index of
  // the last (greatest) card that blk starts.
  size_t end_index  = _array->index_for(end_addr - 1) + 1;

  // Calculate the # cards that the prefix and suffix affect.
  size_t num_pref_cards = suff_index - pref_index;

  size_t num_suff_cards = end_index  - suff_index;
  // Change the cards that need changing
  if (num_suff_cards > 0) {
    HeapWord* boundary = _array->address_for_index(suff_index);
    // Set the offset card for suffix block
    _array->set_offset_array(suff_index, boundary, suff_addr, true /* reducing */);
    // Change any further cards that need changing in the suffix
    if (num_pref_cards > 0) {
      if (num_pref_cards >= num_suff_cards) {
        // Unilaterally fix all of the suffix cards: closed card
        // index interval in args below.
        set_remainder_to_point_to_start_incl(suff_index + 1, end_index - 1, true /* reducing */);
      } else {
        // Unilaterally fix the first (num_pref_cards - 1) following
        // the "offset card" in the suffix block.
        const size_t right_most_fixed_index = suff_index + num_pref_cards - 1;
        set_remainder_to_point_to_start_incl(suff_index + 1,
          right_most_fixed_index, true /* reducing */);
        // Fix the appropriate cards in the remainder of the
        // suffix block -- these are the last num_pref_cards
        // cards in each power block of the "new" range plumbed
        // from suff_addr.
        bool more = true;
        uint i = 1;
        // Fix the first power block with  back_by > num_pref_cards.
        while (more && (i < N_powers)) {
          size_t back_by = power_to_cards_back(i);
          size_t right_index = suff_index + back_by - 1;
          size_t left_index  = right_index - num_pref_cards + 1;
          if (right_index >= end_index - 1) { // last iteration
            right_index = end_index - 1;
            more = false;
          }
          if (left_index <= right_most_fixed_index) {
                left_index = right_most_fixed_index + 1;
          }
          if (back_by > num_pref_cards) {
            // Fill in the remainder of this "power block", if it
            // is non-null.
            if (left_index <= right_index) {
              _array->set_offset_array(left_index, right_index,
                                     N_words + i - 1, true /* reducing */);
            } else {
              more = false; // we are done
              assert((end_index - 1) == right_index, "Must be at the end.");
            }
            i++;
            break;
          }
          i++;
        }
        // Fix the rest of the power blocks.
        while (more && (i < N_powers)) {
          size_t back_by = power_to_cards_back(i);
          size_t right_index = suff_index + back_by - 1;
          size_t left_index  = right_index - num_pref_cards + 1;
          if (right_index >= end_index - 1) { // last iteration
            right_index = end_index - 1;
            if (left_index > right_index) {
              break;
            }
            more  = false;
          }
          assert(left_index <= right_index, "Error");
          _array->set_offset_array(left_index, right_index, N_words + i - 1, true /* reducing */);
          i++;
        }
      }
    } // else no more cards to fix in suffix
  } // else nothing needs to be done
  // Verify that we did the right thing
  verify_single_block(pref_addr, left_blk_size);
  verify_single_block(suff_addr, blk_size - left_blk_size);
}


// Mark the BOT such that if [blk_start, blk_end) straddles a card
// boundary, the card following the first such boundary is marked
// with the appropriate offset.
// NOTE: this method does _not_ adjust _unallocated_block or
// any cards subsequent to the first one.
void
BlockOffsetArrayNonContigSpace::mark_block(HeapWord* blk_start,
                                           HeapWord* blk_end, bool reducing) {
  do_block_internal(blk_start, blk_end, Action_mark, reducing);
}

HeapWord* BlockOffsetArrayNonContigSpace::block_start_unsafe(
  const void* addr) const {
  assert(_array->offset_array(0) == 0, "objects can't cross covered areas");
  assert(_bottom <= addr && addr < _end,
         "addr must be covered by this Array");
  // Must read this exactly once because it can be modified by parallel
  // allocation.
  HeapWord* ub = _unallocated_block;
  if (BlockOffsetArrayUseUnallocatedBlock && addr >= ub) {
    assert(ub < _end, "tautology (see above)");
    return ub;
  }

  // Otherwise, find the block start using the table.
  size_t index = _array->index_for(addr);
  HeapWord* q = _array->address_for_index(index);

  uint offset = _array->offset_array(index);    // Extend u_char to uint.
  while (offset >= N_words) {
    // The excess of the offset from N_words indicates a power of Base
    // to go back by.
    size_t n_cards_back = entry_to_cards_back(offset);
    q -= (N_words * n_cards_back);
    assert(q >= _sp->bottom(),
           "q = " PTR_FORMAT " crossed below bottom = " PTR_FORMAT,
           p2i(q), p2i(_sp->bottom()));
    assert(q < _sp->end(),
           "q = " PTR_FORMAT " crossed above end = " PTR_FORMAT,
           p2i(q), p2i(_sp->end()));
    index -= n_cards_back;
    offset = _array->offset_array(index);
  }
  assert(offset < N_words, "offset too large");
  index--;
  q -= offset;
  assert(q >= _sp->bottom(),
         "q = " PTR_FORMAT " crossed below bottom = " PTR_FORMAT,
         p2i(q), p2i(_sp->bottom()));
  assert(q < _sp->end(),
         "q = " PTR_FORMAT " crossed above end = " PTR_FORMAT,
         p2i(q), p2i(_sp->end()));
  HeapWord* n = q;

  while (n <= addr) {
    debug_only(HeapWord* last = q);   // for debugging
    q = n;
    n += _sp->block_size(n);
    assert(n > q,
           "Looping at n = " PTR_FORMAT " with last = " PTR_FORMAT ","
           " while querying blk_start(" PTR_FORMAT ")"
           " on _sp = [" PTR_FORMAT "," PTR_FORMAT ")",
           p2i(n), p2i(last), p2i(addr), p2i(_sp->bottom()), p2i(_sp->end()));
  }
  assert(q <= addr,
         "wrong order for current (" INTPTR_FORMAT ")" " <= arg (" INTPTR_FORMAT ")",
         p2i(q), p2i(addr));
  assert(addr <= n,
         "wrong order for arg (" INTPTR_FORMAT ") <= next (" INTPTR_FORMAT ")",
         p2i(addr), p2i(n));
  return q;
}

HeapWord* BlockOffsetArrayNonContigSpace::block_start_careful(
  const void* addr) const {
  assert(_array->offset_array(0) == 0, "objects can't cross covered areas");

  assert(_bottom <= addr && addr < _end,
         "addr must be covered by this Array");
  // Must read this exactly once because it can be modified by parallel
  // allocation.
  HeapWord* ub = _unallocated_block;
  if (BlockOffsetArrayUseUnallocatedBlock && addr >= ub) {
    assert(ub < _end, "tautology (see above)");
    return ub;
  }

  // Otherwise, find the block start using the table, but taking
  // care (cf block_start_unsafe() above) not to parse any objects/blocks
  // on the cards themselves.
  size_t index = _array->index_for(addr);
  assert(_array->address_for_index(index) == addr,
         "arg should be start of card");

  HeapWord* q = (HeapWord*)addr;
  uint offset;
  do {
    offset = _array->offset_array(index);
    if (offset < N_words) {
      q -= offset;
    } else {
      size_t n_cards_back = entry_to_cards_back(offset);
      q -= (n_cards_back * N_words);
      index -= n_cards_back;
    }
  } while (offset >= N_words);
  assert(q <= addr, "block start should be to left of arg");
  return q;
}

#ifndef PRODUCT
// Verification & debugging - ensure that the offset table reflects the fact
// that the block [blk_start, blk_end) or [blk, blk + size) is a
// single block of storage. NOTE: can't const this because of
// call to non-const do_block_internal() below.
void BlockOffsetArrayNonContigSpace::verify_single_block(
  HeapWord* blk_start, HeapWord* blk_end) {
  if (VerifyBlockOffsetArray) {
    do_block_internal(blk_start, blk_end, Action_check);
  }
}

void BlockOffsetArrayNonContigSpace::verify_single_block(
  HeapWord* blk, size_t size) {
  verify_single_block(blk, blk + size);
}

// Verify that the given block is before _unallocated_block
void BlockOffsetArrayNonContigSpace::verify_not_unallocated(
  HeapWord* blk_start, HeapWord* blk_end) const {
  if (BlockOffsetArrayUseUnallocatedBlock) {
    assert(blk_start < blk_end, "Block inconsistency?");
    assert(blk_end <= _unallocated_block, "_unallocated_block problem");
  }
}

void BlockOffsetArrayNonContigSpace::verify_not_unallocated(
  HeapWord* blk, size_t size) const {
  verify_not_unallocated(blk, blk + size);
}
#endif // PRODUCT

size_t BlockOffsetArrayNonContigSpace::last_active_index() const {
  if (_unallocated_block == _bottom) {
    return 0;
  } else {
    return _array->index_for(_unallocated_block - 1);
  }
}

//////////////////////////////////////////////////////////////////////
// BlockOffsetArrayContigSpace
//////////////////////////////////////////////////////////////////////

HeapWord* BlockOffsetArrayContigSpace::block_start_unsafe(const void* addr) const {
  assert(_array->offset_array(0) == 0, "objects can't cross covered areas");

  // Otherwise, find the block start using the table.
  assert(_bottom <= addr && addr < _end,
         "addr must be covered by this Array");
  size_t index = _array->index_for(addr);
  // We must make sure that the offset table entry we use is valid.  If
  // "addr" is past the end, start at the last known one and go forward.
  index = MIN2(index, _next_offset_index-1);
  HeapWord* q = _array->address_for_index(index);

  uint offset = _array->offset_array(index);    // Extend u_char to uint.
  while (offset > N_words) {
    // The excess of the offset from N_words indicates a power of Base
    // to go back by.
    size_t n_cards_back = entry_to_cards_back(offset);
    q -= (N_words * n_cards_back);
    assert(q >= _sp->bottom(), "Went below bottom!");
    index -= n_cards_back;
    offset = _array->offset_array(index);
  }
  while (offset == N_words) {
    assert(q >= _sp->bottom(), "Went below bottom!");
    q -= N_words;
    index--;
    offset = _array->offset_array(index);
  }
  assert(offset < N_words, "offset too large");
  q -= offset;
  HeapWord* n = q;

  while (n <= addr) {
    debug_only(HeapWord* last = q);   // for debugging
    q = n;
    n += _sp->block_size(n);
  }
  assert(q <= addr, "wrong order for current and arg");
  assert(addr <= n, "wrong order for arg and next");
  return q;
}

//
//              _next_offset_threshold
//              |   _next_offset_index
//              v   v
//      +-------+-------+-------+-------+-------+
//      | i-1   |   i   | i+1   | i+2   | i+3   |
//      +-------+-------+-------+-------+-------+
//       ( ^    ]
//         block-start
//

void BlockOffsetArrayContigSpace::alloc_block_work(HeapWord* blk_start,
                                        HeapWord* blk_end) {
  assert(blk_start != NULL && blk_end > blk_start,
         "phantom block");
  assert(blk_end > _next_offset_threshold,
         "should be past threshold");
  assert(blk_start <= _next_offset_threshold,
         "blk_start should be at or before threshold");
  assert(pointer_delta(_next_offset_threshold, blk_start) <= N_words,
         "offset should be <= BlockOffsetSharedArray::N");
  assert(Universe::heap()->is_in_reserved(blk_start),
         "reference must be into the heap");
  assert(Universe::heap()->is_in_reserved(blk_end-1),
         "limit must be within the heap");
  assert(_next_offset_threshold ==
         _array->_reserved.start() + _next_offset_index*N_words,
         "index must agree with threshold");

  debug_only(size_t orig_next_offset_index = _next_offset_index;)

  // Mark the card that holds the offset into the block.  Note
  // that _next_offset_index and _next_offset_threshold are not
  // updated until the end of this method.
  _array->set_offset_array(_next_offset_index,
                           _next_offset_threshold,
                           blk_start);

  // We need to now mark the subsequent cards that this blk spans.

  // Index of card on which blk ends.
  size_t end_index   = _array->index_for(blk_end - 1);

  // Are there more cards left to be updated?
  if (_next_offset_index + 1 <= end_index) {
    HeapWord* rem_st  = _array->address_for_index(_next_offset_index + 1);
    // Calculate rem_end this way because end_index
    // may be the last valid index in the covered region.
    HeapWord* rem_end = _array->address_for_index(end_index) +  N_words;
    set_remainder_to_point_to_start(rem_st, rem_end);
  }

  // _next_offset_index and _next_offset_threshold updated here.
  _next_offset_index = end_index + 1;
  // Calculate _next_offset_threshold this way because end_index
  // may be the last valid index in the covered region.
  _next_offset_threshold = _array->address_for_index(end_index) + N_words;
  assert(_next_offset_threshold >= blk_end, "Incorrect offset threshold");

#ifdef ASSERT
  // The offset can be 0 if the block starts on a boundary.  That
  // is checked by an assertion above.
  size_t start_index = _array->index_for(blk_start);
  HeapWord* boundary    = _array->address_for_index(start_index);
  assert((_array->offset_array(orig_next_offset_index) == 0 &&
          blk_start == boundary) ||
          (_array->offset_array(orig_next_offset_index) > 0 &&
         _array->offset_array(orig_next_offset_index) <= N_words),
         "offset array should have been set");
  for (size_t j = orig_next_offset_index + 1; j <= end_index; j++) {
    assert(_array->offset_array(j) > 0 &&
           _array->offset_array(j) <= (u_char) (N_words+N_powers-1),
           "offset array should have been set");
  }
#endif
}

HeapWord* BlockOffsetArrayContigSpace::initialize_threshold() {
  assert(!Universe::heap()->is_in_reserved(_array->_offset_array),
         "just checking");
  _next_offset_index = _array->index_for(_bottom);
  _next_offset_index++;
  _next_offset_threshold =
    _array->address_for_index(_next_offset_index);
  return _next_offset_threshold;
}

void BlockOffsetArrayContigSpace::zero_bottom_entry() {
  assert(!Universe::heap()->is_in_reserved(_array->_offset_array),
         "just checking");
  size_t bottom_index = _array->index_for(_bottom);
  _array->set_offset_array(bottom_index, 0);
}

size_t BlockOffsetArrayContigSpace::last_active_index() const {
  return _next_offset_index == 0 ? 0 : _next_offset_index - 1;
}

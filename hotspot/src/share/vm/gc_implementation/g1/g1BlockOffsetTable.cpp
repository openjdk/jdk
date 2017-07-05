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

#include "precompiled.hpp"
#include "gc_implementation/g1/g1BlockOffsetTable.inline.hpp"
#include "memory/space.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"

//////////////////////////////////////////////////////////////////////
// G1BlockOffsetSharedArray
//////////////////////////////////////////////////////////////////////

G1BlockOffsetSharedArray::G1BlockOffsetSharedArray(MemRegion reserved,
                                                   size_t init_word_size) :
  _reserved(reserved), _end(NULL)
{
  size_t size = compute_size(reserved.word_size());
  ReservedSpace rs(ReservedSpace::allocation_align_size_up(size));
  if (!rs.is_reserved()) {
    vm_exit_during_initialization("Could not reserve enough space for heap offset array");
  }
  if (!_vs.initialize(rs, 0)) {
    vm_exit_during_initialization("Could not reserve enough space for heap offset array");
  }
  _offset_array = (u_char*)_vs.low_boundary();
  resize(init_word_size);
  if (TraceBlockOffsetTable) {
    gclog_or_tty->print_cr("G1BlockOffsetSharedArray::G1BlockOffsetSharedArray: ");
    gclog_or_tty->print_cr("  "
                  "  rs.base(): " INTPTR_FORMAT
                  "  rs.size(): " INTPTR_FORMAT
                  "  rs end(): " INTPTR_FORMAT,
                  rs.base(), rs.size(), rs.base() + rs.size());
    gclog_or_tty->print_cr("  "
                  "  _vs.low_boundary(): " INTPTR_FORMAT
                  "  _vs.high_boundary(): " INTPTR_FORMAT,
                  _vs.low_boundary(),
                  _vs.high_boundary());
  }
}

void G1BlockOffsetSharedArray::resize(size_t new_word_size) {
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
      vm_exit_out_of_memory(delta, "offset table expansion");
    }
    assert(_vs.high() == high + delta, "invalid expansion");
    // Initialization of the contents is left to the
    // G1BlockOffsetArray that uses it.
  } else {
    delta = ReservedSpace::page_align_size_down(old_size - new_size);
    if (delta == 0) return;
    _vs.shrink_by(delta);
    assert(_vs.high() == high - delta, "invalid expansion");
  }
}

bool G1BlockOffsetSharedArray::is_card_boundary(HeapWord* p) const {
  assert(p >= _reserved.start(), "just checking");
  size_t delta = pointer_delta(p, _reserved.start());
  return (delta & right_n_bits(LogN_words)) == (size_t)NoBits;
}


//////////////////////////////////////////////////////////////////////
// G1BlockOffsetArray
//////////////////////////////////////////////////////////////////////

G1BlockOffsetArray::G1BlockOffsetArray(G1BlockOffsetSharedArray* array,
                                       MemRegion mr, bool init_to_zero) :
  G1BlockOffsetTable(mr.start(), mr.end()),
  _unallocated_block(_bottom),
  _array(array), _csp(NULL),
  _init_to_zero(init_to_zero) {
  assert(_bottom <= _end, "arguments out of order");
  if (!_init_to_zero) {
    // initialize cards to point back to mr.start()
    set_remainder_to_point_to_start(mr.start() + N_words, mr.end());
    _array->set_offset_array(0, 0);  // set first card to 0
  }
}

void G1BlockOffsetArray::set_space(Space* sp) {
  _sp = sp;
  _csp = sp->toContiguousSpace();
}

// The arguments follow the normal convention of denoting
// a right-open interval: [start, end)
void
G1BlockOffsetArray:: set_remainder_to_point_to_start(HeapWord* start, HeapWord* end) {

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
  set_remainder_to_point_to_start_incl(start_card, end_card); // closed interval
}

// Unlike the normal convention in this code, the argument here denotes
// a closed, inclusive interval: [start_card, end_card], cf set_remainder_to_point_to_start()
// above.
void
G1BlockOffsetArray::set_remainder_to_point_to_start_incl(size_t start_card, size_t end_card) {
  if (start_card > end_card) {
    return;
  }
  assert(start_card > _array->index_for(_bottom), "Cannot be first card");
  assert(_array->offset_array(start_card-1) <= N_words,
         "Offset card has an unexpected value");
  size_t start_card_for_region = start_card;
  u_char offset = max_jubyte;
  for (int i = 0; i < BlockOffsetArray::N_powers; i++) {
    // -1 so that the the card with the actual offset is counted.  Another -1
    // so that the reach ends in this region and not at the start
    // of the next.
    size_t reach = start_card - 1 + (BlockOffsetArray::power_to_cards_back(i+1) - 1);
    offset = N_words + i;
    if (reach >= end_card) {
      _array->set_offset_array(start_card_for_region, end_card, offset);
      start_card_for_region = reach + 1;
      break;
    }
    _array->set_offset_array(start_card_for_region, reach, offset);
    start_card_for_region = reach + 1;
  }
  assert(start_card_for_region > end_card, "Sanity check");
  DEBUG_ONLY(check_all_cards(start_card, end_card);)
}

// The block [blk_start, blk_end) has been allocated;
// adjust the block offset table to represent this information;
// right-open interval: [blk_start, blk_end)
void
G1BlockOffsetArray::alloc_block(HeapWord* blk_start, HeapWord* blk_end) {
  mark_block(blk_start, blk_end);
  allocated(blk_start, blk_end);
}

// Adjust BOT to show that a previously whole block has been split
// into two.
void G1BlockOffsetArray::split_block(HeapWord* blk, size_t blk_size,
                                     size_t left_blk_size) {
  // Verify that the BOT shows [blk, blk + blk_size) to be one block.
  verify_single_block(blk, blk_size);
  // Update the BOT to indicate that [blk + left_blk_size, blk + blk_size)
  // is one single block.
  mark_block(blk + left_blk_size, blk + blk_size);
}


// Action_mark - update the BOT for the block [blk_start, blk_end).
//               Current typical use is for splitting a block.
// Action_single - udpate the BOT for an allocation.
// Action_verify - BOT verification.
void G1BlockOffsetArray::do_block_internal(HeapWord* blk_start,
                                           HeapWord* blk_end,
                                           Action action) {
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
          _array->set_offset_array(start_index, boundary, blk_start);
          break;
        } // Else fall through to the next case
      }
      case Action_single: {
        _array->set_offset_array(start_index, boundary, blk_start);
        // We have finished marking the "offset card". We need to now
        // mark the subsequent cards that this blk spans.
        if (start_index < end_index) {
          HeapWord* rem_st = _array->address_for_index(start_index) + N_words;
          HeapWord* rem_end = _array->address_for_index(end_index) + N_words;
          set_remainder_to_point_to_start(rem_st, rem_end);
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

// The card-interval [start_card, end_card] is a closed interval; this
// is an expensive check -- use with care and only under protection of
// suitable flag.
void G1BlockOffsetArray::check_all_cards(size_t start_card, size_t end_card) const {

  if (end_card < start_card) {
    return;
  }
  guarantee(_array->offset_array(start_card) == N_words, "Wrong value in second card");
  for (size_t c = start_card + 1; c <= end_card; c++ /* yeah! */) {
    u_char entry = _array->offset_array(c);
    if (c - start_card > BlockOffsetArray::power_to_cards_back(1)) {
      guarantee(entry > N_words, "Should be in logarithmic region");
    }
    size_t backskip = BlockOffsetArray::entry_to_cards_back(entry);
    size_t landing_card = c - backskip;
    guarantee(landing_card >= (start_card - 1), "Inv");
    if (landing_card >= start_card) {
      guarantee(_array->offset_array(landing_card) <= entry, "monotonicity");
    } else {
      guarantee(landing_card == start_card - 1, "Tautology");
      guarantee(_array->offset_array(landing_card) <= N_words, "Offset value");
    }
  }
}

// The range [blk_start, blk_end) represents a single contiguous block
// of storage; modify the block offset table to represent this
// information; Right-open interval: [blk_start, blk_end)
// NOTE: this method does _not_ adjust _unallocated_block.
void
G1BlockOffsetArray::single_block(HeapWord* blk_start, HeapWord* blk_end) {
  do_block_internal(blk_start, blk_end, Action_single);
}

// Mark the BOT such that if [blk_start, blk_end) straddles a card
// boundary, the card following the first such boundary is marked
// with the appropriate offset.
// NOTE: this method does _not_ adjust _unallocated_block or
// any cards subsequent to the first one.
void
G1BlockOffsetArray::mark_block(HeapWord* blk_start, HeapWord* blk_end) {
  do_block_internal(blk_start, blk_end, Action_mark);
}

void G1BlockOffsetArray::join_blocks(HeapWord* blk1, HeapWord* blk2) {
  HeapWord* blk1_start = Universe::heap()->block_start(blk1);
  HeapWord* blk2_start = Universe::heap()->block_start(blk2);
  assert(blk1 == blk1_start && blk2 == blk2_start,
         "Must be block starts.");
  assert(blk1 + _sp->block_size(blk1) == blk2, "Must be contiguous.");
  size_t blk1_start_index = _array->index_for(blk1);
  size_t blk2_start_index = _array->index_for(blk2);
  assert(blk1_start_index <= blk2_start_index, "sanity");
  HeapWord* blk2_card_start = _array->address_for_index(blk2_start_index);
  if (blk2 == blk2_card_start) {
    // blk2 starts a card.  Does blk1 start on the prevous card, or futher
    // back?
    assert(blk1_start_index < blk2_start_index, "must be lower card.");
    if (blk1_start_index + 1 == blk2_start_index) {
      // previous card; new value for blk2 card is size of blk1.
      _array->set_offset_array(blk2_start_index, (u_char) _sp->block_size(blk1));
    } else {
      // Earlier card; go back a card.
      _array->set_offset_array(blk2_start_index, N_words);
    }
  } else {
    // blk2 does not start a card.  Does it cross a card?  If not, nothing
    // to do.
    size_t blk2_end_index =
      _array->index_for(blk2 + _sp->block_size(blk2) - 1);
    assert(blk2_end_index >= blk2_start_index, "sanity");
    if (blk2_end_index > blk2_start_index) {
      // Yes, it crosses a card.  The value for the next card must change.
      if (blk1_start_index + 1 == blk2_start_index) {
        // previous card; new value for second blk2 card is size of blk1.
        _array->set_offset_array(blk2_start_index + 1,
                                 (u_char) _sp->block_size(blk1));
      } else {
        // Earlier card; go back a card.
        _array->set_offset_array(blk2_start_index + 1, N_words);
      }
    }
  }
}

HeapWord* G1BlockOffsetArray::block_start_unsafe(const void* addr) {
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
  HeapWord* q = block_at_or_preceding(addr, false, 0);
  return forward_to_block_containing_addr(q, addr);
}

// This duplicates a little code from the above: unavoidable.
HeapWord*
G1BlockOffsetArray::block_start_unsafe_const(const void* addr) const {
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
  HeapWord* q = block_at_or_preceding(addr, false, 0);
  HeapWord* n = q + _sp->block_size(q);
  return forward_to_block_containing_addr_const(q, n, addr);
}


HeapWord*
G1BlockOffsetArray::forward_to_block_containing_addr_slow(HeapWord* q,
                                                          HeapWord* n,
                                                          const void* addr) {
  // We're not in the normal case.  We need to handle an important subcase
  // here: LAB allocation.  An allocation previously recorded in the
  // offset table was actually a lab allocation, and was divided into
  // several objects subsequently.  Fix this situation as we answer the
  // query, by updating entries as we cross them.

  // If the fist object's end q is at the card boundary. Start refining
  // with the corresponding card (the value of the entry will be basically
  // set to 0). If the object crosses the boundary -- start from the next card.
  size_t next_index = _array->index_for(n) + !_array->is_card_boundary(n);
  HeapWord* next_boundary = _array->address_for_index(next_index);
  if (csp() != NULL) {
    if (addr >= csp()->top()) return csp()->top();
    while (next_boundary < addr) {
      while (n <= next_boundary) {
        q = n;
        oop obj = oop(q);
        if (obj->klass_or_null() == NULL) return q;
        n += obj->size();
      }
      assert(q <= next_boundary && n > next_boundary, "Consequence of loop");
      // [q, n) is the block that crosses the boundary.
      alloc_block_work2(&next_boundary, &next_index, q, n);
    }
  } else {
    while (next_boundary < addr) {
      while (n <= next_boundary) {
        q = n;
        oop obj = oop(q);
        if (obj->klass_or_null() == NULL) return q;
        n += _sp->block_size(q);
      }
      assert(q <= next_boundary && n > next_boundary, "Consequence of loop");
      // [q, n) is the block that crosses the boundary.
      alloc_block_work2(&next_boundary, &next_index, q, n);
    }
  }
  return forward_to_block_containing_addr_const(q, n, addr);
}

HeapWord* G1BlockOffsetArray::block_start_careful(const void* addr) const {
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
  // on the cards themsleves.
  size_t index = _array->index_for(addr);
  assert(_array->address_for_index(index) == addr,
         "arg should be start of card");

  HeapWord* q = (HeapWord*)addr;
  uint offset;
  do {
    offset = _array->offset_array(index--);
    q -= offset;
  } while (offset == N_words);
  assert(q <= addr, "block start should be to left of arg");
  return q;
}

// Note that the committed size of the covered space may have changed,
// so the table size might also wish to change.
void G1BlockOffsetArray::resize(size_t new_word_size) {
  HeapWord* new_end = _bottom + new_word_size;
  if (_end < new_end && !init_to_zero()) {
    // verify that the old and new boundaries are also card boundaries
    assert(_array->is_card_boundary(_end),
           "_end not a card boundary");
    assert(_array->is_card_boundary(new_end),
           "new _end would not be a card boundary");
    // set all the newly added cards
    _array->set_offset_array(_end, new_end, N_words);
  }
  _end = new_end;  // update _end
}

void G1BlockOffsetArray::set_region(MemRegion mr) {
  _bottom = mr.start();
  _end = mr.end();
}

//
//              threshold_
//              |   _index_
//              v   v
//      +-------+-------+-------+-------+-------+
//      | i-1   |   i   | i+1   | i+2   | i+3   |
//      +-------+-------+-------+-------+-------+
//       ( ^    ]
//         block-start
//
void G1BlockOffsetArray::alloc_block_work2(HeapWord** threshold_, size_t* index_,
                                           HeapWord* blk_start, HeapWord* blk_end) {
  // For efficiency, do copy-in/copy-out.
  HeapWord* threshold = *threshold_;
  size_t    index = *index_;

  assert(blk_start != NULL && blk_end > blk_start,
         "phantom block");
  assert(blk_end > threshold, "should be past threshold");
  assert(blk_start <= threshold, "blk_start should be at or before threshold");
  assert(pointer_delta(threshold, blk_start) <= N_words,
         "offset should be <= BlockOffsetSharedArray::N");
  assert(Universe::heap()->is_in_reserved(blk_start),
         "reference must be into the heap");
  assert(Universe::heap()->is_in_reserved(blk_end-1),
         "limit must be within the heap");
  assert(threshold == _array->_reserved.start() + index*N_words,
         "index must agree with threshold");

  DEBUG_ONLY(size_t orig_index = index;)

  // Mark the card that holds the offset into the block.  Note
  // that _next_offset_index and _next_offset_threshold are not
  // updated until the end of this method.
  _array->set_offset_array(index, threshold, blk_start);

  // We need to now mark the subsequent cards that this blk spans.

  // Index of card on which blk ends.
  size_t end_index   = _array->index_for(blk_end - 1);

  // Are there more cards left to be updated?
  if (index + 1 <= end_index) {
    HeapWord* rem_st  = _array->address_for_index(index + 1);
    // Calculate rem_end this way because end_index
    // may be the last valid index in the covered region.
    HeapWord* rem_end = _array->address_for_index(end_index) +  N_words;
    set_remainder_to_point_to_start(rem_st, rem_end);
  }

  index = end_index + 1;
  // Calculate threshold_ this way because end_index
  // may be the last valid index in the covered region.
  threshold = _array->address_for_index(end_index) + N_words;
  assert(threshold >= blk_end, "Incorrect offset threshold");

  // index_ and threshold_ updated here.
  *threshold_ = threshold;
  *index_ = index;

#ifdef ASSERT
  // The offset can be 0 if the block starts on a boundary.  That
  // is checked by an assertion above.
  size_t start_index = _array->index_for(blk_start);
  HeapWord* boundary    = _array->address_for_index(start_index);
  assert((_array->offset_array(orig_index) == 0 &&
          blk_start == boundary) ||
          (_array->offset_array(orig_index) > 0 &&
         _array->offset_array(orig_index) <= N_words),
         "offset array should have been set");
  for (size_t j = orig_index + 1; j <= end_index; j++) {
    assert(_array->offset_array(j) > 0 &&
           _array->offset_array(j) <=
             (u_char) (N_words+BlockOffsetArray::N_powers-1),
           "offset array should have been set");
  }
#endif
}

void
G1BlockOffsetArray::set_for_starts_humongous(HeapWord* new_end) {
  assert(_end ==  new_end, "_end should have already been updated");

  // The first BOT entry should have offset 0.
  _array->set_offset_array(_array->index_for(_bottom), 0);
  // The rest should point to the first one.
  set_remainder_to_point_to_start(_bottom + N_words, new_end);
}

//////////////////////////////////////////////////////////////////////
// G1BlockOffsetArrayContigSpace
//////////////////////////////////////////////////////////////////////

HeapWord*
G1BlockOffsetArrayContigSpace::block_start_unsafe(const void* addr) {
  assert(_bottom <= addr && addr < _end,
         "addr must be covered by this Array");
  HeapWord* q = block_at_or_preceding(addr, true, _next_offset_index-1);
  return forward_to_block_containing_addr(q, addr);
}

HeapWord*
G1BlockOffsetArrayContigSpace::
block_start_unsafe_const(const void* addr) const {
  assert(_bottom <= addr && addr < _end,
         "addr must be covered by this Array");
  HeapWord* q = block_at_or_preceding(addr, true, _next_offset_index-1);
  HeapWord* n = q + _sp->block_size(q);
  return forward_to_block_containing_addr_const(q, n, addr);
}

G1BlockOffsetArrayContigSpace::
G1BlockOffsetArrayContigSpace(G1BlockOffsetSharedArray* array,
                              MemRegion mr) :
  G1BlockOffsetArray(array, mr, true)
{
  _next_offset_threshold = NULL;
  _next_offset_index = 0;
}

HeapWord* G1BlockOffsetArrayContigSpace::initialize_threshold() {
  assert(!Universe::heap()->is_in_reserved(_array->_offset_array),
         "just checking");
  _next_offset_index = _array->index_for(_bottom);
  _next_offset_index++;
  _next_offset_threshold =
    _array->address_for_index(_next_offset_index);
  return _next_offset_threshold;
}

void G1BlockOffsetArrayContigSpace::zero_bottom_entry() {
  assert(!Universe::heap()->is_in_reserved(_array->_offset_array),
         "just checking");
  size_t bottom_index = _array->index_for(_bottom);
  assert(_array->address_for_index(bottom_index) == _bottom,
         "Precondition of call");
  _array->set_offset_array(bottom_index, 0);
}

void
G1BlockOffsetArrayContigSpace::set_for_starts_humongous(HeapWord* new_end) {
  G1BlockOffsetArray::set_for_starts_humongous(new_end);

  // Make sure _next_offset_threshold and _next_offset_index point to new_end.
  _next_offset_threshold = new_end;
  _next_offset_index     = _array->index_for(new_end);
}

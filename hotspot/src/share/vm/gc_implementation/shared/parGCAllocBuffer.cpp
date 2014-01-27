/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/parGCAllocBuffer.hpp"
#include "memory/sharedHeap.hpp"
#include "oops/arrayOop.hpp"
#include "oops/oop.inline.hpp"

ParGCAllocBuffer::ParGCAllocBuffer(size_t desired_plab_sz_) :
  _word_sz(desired_plab_sz_), _bottom(NULL), _top(NULL),
  _end(NULL), _hard_end(NULL),
  _retained(false), _retained_filler(),
  _allocated(0), _wasted(0)
{
  assert (min_size() > AlignmentReserve, "Inconsistency!");
  // arrayOopDesc::header_size depends on command line initialization.
  FillerHeaderSize = align_object_size(arrayOopDesc::header_size(T_INT));
  AlignmentReserve = oopDesc::header_size() > MinObjAlignment ? FillerHeaderSize : 0;
}

size_t ParGCAllocBuffer::FillerHeaderSize;

// If the minimum object size is greater than MinObjAlignment, we can
// end up with a shard at the end of the buffer that's smaller than
// the smallest object.  We can't allow that because the buffer must
// look like it's full of objects when we retire it, so we make
// sure we have enough space for a filler int array object.
size_t ParGCAllocBuffer::AlignmentReserve;

void ParGCAllocBuffer::retire(bool end_of_gc, bool retain) {
  assert(!retain || end_of_gc, "Can only retain at GC end.");
  if (_retained) {
    // If the buffer had been retained shorten the previous filler object.
    assert(_retained_filler.end() <= _top, "INVARIANT");
    CollectedHeap::fill_with_object(_retained_filler);
    // Wasted space book-keeping, otherwise (normally) done in invalidate()
    _wasted += _retained_filler.word_size();
    _retained = false;
  }
  assert(!end_of_gc || !_retained, "At this point, end_of_gc ==> !_retained.");
  if (_top < _hard_end) {
    CollectedHeap::fill_with_object(_top, _hard_end);
    if (!retain) {
      invalidate();
    } else {
      // Is there wasted space we'd like to retain for the next GC?
      if (pointer_delta(_end, _top) > FillerHeaderSize) {
        _retained = true;
        _retained_filler = MemRegion(_top, FillerHeaderSize);
        _top = _top + FillerHeaderSize;
      } else {
        invalidate();
      }
    }
  }
}

void ParGCAllocBuffer::flush_stats(PLABStats* stats) {
  assert(ResizePLAB, "Wasted work");
  stats->add_allocated(_allocated);
  stats->add_wasted(_wasted);
  stats->add_unused(pointer_delta(_end, _top));
}

// Compute desired plab size and latch result for later
// use. This should be called once at the end of parallel
// scavenge; it clears the sensor accumulators.
void PLABStats::adjust_desired_plab_sz(uint no_of_gc_workers) {
  assert(ResizePLAB, "Not set");

  assert(is_object_aligned(max_size()) && min_size() <= max_size(),
         "PLAB clipping computation may be incorrect");

  if (_allocated == 0) {
    assert(_unused == 0,
           err_msg("Inconsistency in PLAB stats: "
                   "_allocated: "SIZE_FORMAT", "
                   "_wasted: "SIZE_FORMAT", "
                   "_unused: "SIZE_FORMAT", "
                   "_used  : "SIZE_FORMAT,
                   _allocated, _wasted, _unused, _used));

    _allocated = 1;
  }
  double wasted_frac    = (double)_unused/(double)_allocated;
  size_t target_refills = (size_t)((wasted_frac*TargetSurvivorRatio)/
                                   TargetPLABWastePct);
  if (target_refills == 0) {
    target_refills = 1;
  }
  _used = _allocated - _wasted - _unused;
  size_t plab_sz = _used/(target_refills*no_of_gc_workers);
  if (PrintPLAB) gclog_or_tty->print(" (plab_sz = %d ", plab_sz);
  // Take historical weighted average
  _filter.sample(plab_sz);
  // Clip from above and below, and align to object boundary
  plab_sz = MAX2(min_size(), (size_t)_filter.average());
  plab_sz = MIN2(max_size(), plab_sz);
  plab_sz = align_object_size(plab_sz);
  // Latch the result
  if (PrintPLAB) gclog_or_tty->print(" desired_plab_sz = %d) ", plab_sz);
  _desired_plab_sz = plab_sz;
  // Now clear the accumulators for next round:
  // note this needs to be fixed in the case where we
  // are retaining across scavenges. FIX ME !!! XXX
  _allocated = 0;
  _wasted    = 0;
  _unused    = 0;
}

#ifndef PRODUCT
void ParGCAllocBuffer::print() {
  gclog_or_tty->print("parGCAllocBuffer: _bottom: %p  _top: %p  _end: %p  _hard_end: %p"
             "_retained: %c _retained_filler: [%p,%p)\n",
             _bottom, _top, _end, _hard_end,
             "FT"[_retained], _retained_filler.start(), _retained_filler.end());
}
#endif // !PRODUCT

const size_t ParGCAllocBufferWithBOT::ChunkSizeInWords =
MIN2(CardTableModRefBS::par_chunk_heapword_alignment(),
     ((size_t)Generation::GenGrain)/HeapWordSize);
const size_t ParGCAllocBufferWithBOT::ChunkSizeInBytes =
MIN2(CardTableModRefBS::par_chunk_heapword_alignment() * HeapWordSize,
     (size_t)Generation::GenGrain);

ParGCAllocBufferWithBOT::ParGCAllocBufferWithBOT(size_t word_sz,
                                                 BlockOffsetSharedArray* bsa) :
  ParGCAllocBuffer(word_sz),
  _bsa(bsa),
  _bt(bsa, MemRegion(_bottom, _hard_end)),
  _true_end(_hard_end)
{}

// The buffer comes with its own BOT, with a shared (obviously) underlying
// BlockOffsetSharedArray. We manipulate this BOT in the normal way
// as we would for any contiguous space. However, on occasion we
// need to do some buffer surgery at the extremities before we
// start using the body of the buffer for allocations. Such surgery
// (as explained elsewhere) is to prevent allocation on a card that
// is in the process of being walked concurrently by another GC thread.
// When such surgery happens at a point that is far removed (to the
// right of the current allocation point, top), we use the "contig"
// parameter below to directly manipulate the shared array without
// modifying the _next_threshold state in the BOT.
void ParGCAllocBufferWithBOT::fill_region_with_block(MemRegion mr,
                                                     bool contig) {
  CollectedHeap::fill_with_object(mr);
  if (contig) {
    _bt.alloc_block(mr.start(), mr.end());
  } else {
    _bt.BlockOffsetArray::alloc_block(mr.start(), mr.end());
  }
}

HeapWord* ParGCAllocBufferWithBOT::allocate_slow(size_t word_sz) {
  HeapWord* res = NULL;
  if (_true_end > _hard_end) {
    assert((HeapWord*)align_size_down(intptr_t(_hard_end),
                                      ChunkSizeInBytes) == _hard_end,
           "or else _true_end should be equal to _hard_end");
    assert(_retained, "or else _true_end should be equal to _hard_end");
    assert(_retained_filler.end() <= _top, "INVARIANT");
    CollectedHeap::fill_with_object(_retained_filler);
    if (_top < _hard_end) {
      fill_region_with_block(MemRegion(_top, _hard_end), true);
    }
    HeapWord* next_hard_end = MIN2(_true_end, _hard_end + ChunkSizeInWords);
    _retained_filler = MemRegion(_hard_end, FillerHeaderSize);
    _bt.alloc_block(_retained_filler.start(), _retained_filler.word_size());
    _top      = _retained_filler.end();
    _hard_end = next_hard_end;
    _end      = _hard_end - AlignmentReserve;
    res       = ParGCAllocBuffer::allocate(word_sz);
    if (res != NULL) {
      _bt.alloc_block(res, word_sz);
    }
  }
  return res;
}

void
ParGCAllocBufferWithBOT::undo_allocation(HeapWord* obj, size_t word_sz) {
  ParGCAllocBuffer::undo_allocation(obj, word_sz);
  // This may back us up beyond the previous threshold, so reset.
  _bt.set_region(MemRegion(_top, _hard_end));
  _bt.initialize_threshold();
}

void ParGCAllocBufferWithBOT::retire(bool end_of_gc, bool retain) {
  assert(!retain || end_of_gc, "Can only retain at GC end.");
  if (_retained) {
    // We're about to make the retained_filler into a block.
    _bt.BlockOffsetArray::alloc_block(_retained_filler.start(),
                                      _retained_filler.end());
  }
  // Reset _hard_end to _true_end (and update _end)
  if (retain && _hard_end != NULL) {
    assert(_hard_end <= _true_end, "Invariant.");
    _hard_end = _true_end;
    _end      = MAX2(_top, _hard_end - AlignmentReserve);
    assert(_end <= _hard_end, "Invariant.");
  }
  _true_end = _hard_end;
  HeapWord* pre_top = _top;

  ParGCAllocBuffer::retire(end_of_gc, retain);
  // Now any old _retained_filler is cut back to size, the free part is
  // filled with a filler object, and top is past the header of that
  // object.

  if (retain && _top < _end) {
    assert(end_of_gc && retain, "Or else retain should be false.");
    // If the lab does not start on a card boundary, we don't want to
    // allocate onto that card, since that might lead to concurrent
    // allocation and card scanning, which we don't support.  So we fill
    // the first card with a garbage object.
    size_t first_card_index = _bsa->index_for(pre_top);
    HeapWord* first_card_start = _bsa->address_for_index(first_card_index);
    if (first_card_start < pre_top) {
      HeapWord* second_card_start =
        _bsa->inc_by_region_size(first_card_start);

      // Ensure enough room to fill with the smallest block
      second_card_start = MAX2(second_card_start, pre_top + AlignmentReserve);

      // If the end is already in the first card, don't go beyond it!
      // Or if the remainder is too small for a filler object, gobble it up.
      if (_hard_end < second_card_start ||
          pointer_delta(_hard_end, second_card_start) < AlignmentReserve) {
        second_card_start = _hard_end;
      }
      if (pre_top < second_card_start) {
        MemRegion first_card_suffix(pre_top, second_card_start);
        fill_region_with_block(first_card_suffix, true);
      }
      pre_top = second_card_start;
      _top = pre_top;
      _end = MAX2(_top, _hard_end - AlignmentReserve);
    }

    // If the lab does not end on a card boundary, we don't want to
    // allocate onto that card, since that might lead to concurrent
    // allocation and card scanning, which we don't support.  So we fill
    // the last card with a garbage object.
    size_t last_card_index = _bsa->index_for(_hard_end);
    HeapWord* last_card_start = _bsa->address_for_index(last_card_index);
    if (last_card_start < _hard_end) {

      // Ensure enough room to fill with the smallest block
      last_card_start = MIN2(last_card_start, _hard_end - AlignmentReserve);

      // If the top is already in the last card, don't go back beyond it!
      // Or if the remainder is too small for a filler object, gobble it up.
      if (_top > last_card_start ||
          pointer_delta(last_card_start, _top) < AlignmentReserve) {
        last_card_start = _top;
      }
      if (last_card_start < _hard_end) {
        MemRegion last_card_prefix(last_card_start, _hard_end);
        fill_region_with_block(last_card_prefix, false);
      }
      _hard_end = last_card_start;
      _end      = MAX2(_top, _hard_end - AlignmentReserve);
      _true_end = _hard_end;
      assert(_end <= _hard_end, "Invariant.");
    }

    // At this point:
    //   1) we had a filler object from the original top to hard_end.
    //   2) We've filled in any partial cards at the front and back.
    if (pre_top < _hard_end) {
      // Now we can reset the _bt to do allocation in the given area.
      MemRegion new_filler(pre_top, _hard_end);
      fill_region_with_block(new_filler, false);
      _top = pre_top + ParGCAllocBuffer::FillerHeaderSize;
      // If there's no space left, don't retain.
      if (_top >= _end) {
        _retained = false;
        invalidate();
        return;
      }
      _retained_filler = MemRegion(pre_top, _top);
      _bt.set_region(MemRegion(_top, _hard_end));
      _bt.initialize_threshold();
      assert(_bt.threshold() > _top, "initialize_threshold failed!");

      // There may be other reasons for queries into the middle of the
      // filler object.  When such queries are done in parallel with
      // allocation, bad things can happen, if the query involves object
      // iteration.  So we ensure that such queries do not involve object
      // iteration, by putting another filler object on the boundaries of
      // such queries.  One such is the object spanning a parallel card
      // chunk boundary.

      // "chunk_boundary" is the address of the first chunk boundary less
      // than "hard_end".
      HeapWord* chunk_boundary =
        (HeapWord*)align_size_down(intptr_t(_hard_end-1), ChunkSizeInBytes);
      assert(chunk_boundary < _hard_end, "Or else above did not work.");
      assert(pointer_delta(_true_end, chunk_boundary) >= AlignmentReserve,
             "Consequence of last card handling above.");

      if (_top <= chunk_boundary) {
        assert(_true_end == _hard_end, "Invariant.");
        while (_top <= chunk_boundary) {
          assert(pointer_delta(_hard_end, chunk_boundary) >= AlignmentReserve,
                 "Consequence of last card handling above.");
          _bt.BlockOffsetArray::alloc_block(chunk_boundary, _hard_end);
          CollectedHeap::fill_with_object(chunk_boundary, _hard_end);
          _hard_end = chunk_boundary;
          chunk_boundary -= ChunkSizeInWords;
        }
        _end = _hard_end - AlignmentReserve;
        assert(_top <= _end, "Invariant.");
        // Now reset the initial filler chunk so it doesn't overlap with
        // the one(s) inserted above.
        MemRegion new_filler(pre_top, _hard_end);
        fill_region_with_block(new_filler, false);
      }
    } else {
      _retained = false;
      invalidate();
    }
  } else {
    assert(!end_of_gc ||
           (!_retained && _true_end == _hard_end), "Checking.");
  }
  assert(_end <= _hard_end, "Invariant.");
  assert(_top < _end || _top == _hard_end, "Invariant");
}

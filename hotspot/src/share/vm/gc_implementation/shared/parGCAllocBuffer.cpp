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
#include "gc_implementation/shared/parGCAllocBuffer.hpp"
#include "memory/sharedHeap.hpp"
#include "oops/arrayOop.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/globalDefinitions.hpp"

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

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
  if (PrintPLAB) gclog_or_tty->print(" (plab_sz = " SIZE_FORMAT " ", plab_sz);
  // Take historical weighted average
  _filter.sample(plab_sz);
  // Clip from above and below, and align to object boundary
  plab_sz = MAX2(min_size(), (size_t)_filter.average());
  plab_sz = MIN2(max_size(), plab_sz);
  plab_sz = align_object_size(plab_sz);
  // Latch the result
  if (PrintPLAB) gclog_or_tty->print(" desired_plab_sz = " SIZE_FORMAT ") ", plab_sz);
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
  gclog_or_tty->print("parGCAllocBuffer: _bottom: " PTR_FORMAT "  _top: " PTR_FORMAT
             "  _end: " PTR_FORMAT "  _hard_end: " PTR_FORMAT " _retained: %c"
             " _retained_filler: [" PTR_FORMAT "," PTR_FORMAT ")\n",
             _bottom, _top, _end, _hard_end,
             "FT"[_retained], _retained_filler.start(), _retained_filler.end());
}
#endif // !PRODUCT

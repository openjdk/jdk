/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// Forward decl.

class PLABStats;

// A per-thread allocation buffer used during GC.
class ParGCAllocBuffer: public CHeapObj {
protected:
  char head[32];
  size_t _word_sz;          // in HeapWord units
  HeapWord* _bottom;
  HeapWord* _top;
  HeapWord* _end;       // last allocatable address + 1
  HeapWord* _hard_end;  // _end + AlignmentReserve
  bool      _retained;  // whether we hold a _retained_filler
  MemRegion _retained_filler;
  // In support of ergonomic sizing of PLAB's
  size_t    _allocated;     // in HeapWord units
  size_t    _wasted;        // in HeapWord units
  char tail[32];
  static size_t FillerHeaderSize;
  static size_t AlignmentReserve;

public:
  // Initializes the buffer to be empty, but with the given "word_sz".
  // Must get initialized with "set_buf" for an allocation to succeed.
  ParGCAllocBuffer(size_t word_sz);

  static const size_t min_size() {
    return ThreadLocalAllocBuffer::min_size();
  }

  static const size_t max_size() {
    return ThreadLocalAllocBuffer::max_size();
  }

  // If an allocation of the given "word_sz" can be satisfied within the
  // buffer, do the allocation, returning a pointer to the start of the
  // allocated block.  If the allocation request cannot be satisfied,
  // return NULL.
  HeapWord* allocate(size_t word_sz) {
    HeapWord* res = _top;
    if (pointer_delta(_end, _top) >= word_sz) {
      _top = _top + word_sz;
      return res;
    } else {
      return NULL;
    }
  }

  // Undo the last allocation in the buffer, which is required to be of the
  // "obj" of the given "word_sz".
  void undo_allocation(HeapWord* obj, size_t word_sz) {
    assert(pointer_delta(_top, _bottom) >= word_sz, "Bad undo");
    assert(pointer_delta(_top, obj)     == word_sz, "Bad undo");
    _top = obj;
  }

  // The total (word) size of the buffer, including both allocated and
  // unallocted space.
  size_t word_sz() { return _word_sz; }

  // Should only be done if we are about to reset with a new buffer of the
  // given size.
  void set_word_size(size_t new_word_sz) {
    assert(new_word_sz > AlignmentReserve, "Too small");
    _word_sz = new_word_sz;
  }

  // The number of words of unallocated space remaining in the buffer.
  size_t words_remaining() {
    assert(_end >= _top, "Negative buffer");
    return pointer_delta(_end, _top, HeapWordSize);
  }

  bool contains(void* addr) {
    return (void*)_bottom <= addr && addr < (void*)_hard_end;
  }

  // Sets the space of the buffer to be [buf, space+word_sz()).
  void set_buf(HeapWord* buf) {
    _bottom   = buf;
    _top      = _bottom;
    _hard_end = _bottom + word_sz();
    _end      = _hard_end - AlignmentReserve;
    assert(_end >= _top, "Negative buffer");
    // In support of ergonomic sizing
    _allocated += word_sz();
  }

  // Flush the stats supporting ergonomic sizing of PLAB's
  void flush_stats(PLABStats* stats);
  void flush_stats_and_retire(PLABStats* stats, bool retain) {
    // We flush the stats first in order to get a reading of
    // unused space in the last buffer.
    if (ResizePLAB) {
      flush_stats(stats);
    }
    // Retire the last allocation buffer.
    retire(true, retain);
  }

  // Force future allocations to fail and queries for contains()
  // to return false
  void invalidate() {
    assert(!_retained, "Shouldn't retain an invalidated buffer.");
    _end    = _hard_end;
    _wasted += pointer_delta(_end, _top);  // unused  space
    _top    = _end;      // force future allocations to fail
    _bottom = _end;      // force future contains() queries to return false
  }

  // Fills in the unallocated portion of the buffer with a garbage object.
  // If "end_of_gc" is TRUE, is after the last use in the GC.  IF "retain"
  // is true, attempt to re-use the unused portion in the next GC.
  void retire(bool end_of_gc, bool retain);

  void print() PRODUCT_RETURN;
};

// PLAB stats book-keeping
class PLABStats VALUE_OBJ_CLASS_SPEC {
  size_t _allocated;      // total allocated
  size_t _wasted;         // of which wasted (internal fragmentation)
  size_t _unused;         // Unused in last buffer
  size_t _used;           // derived = allocated - wasted - unused
  size_t _desired_plab_sz;// output of filter (below), suitably trimmed and quantized
  AdaptiveWeightedAverage
         _filter;         // integrator with decay

 public:
  PLABStats(size_t desired_plab_sz_, unsigned wt) :
    _allocated(0),
    _wasted(0),
    _unused(0),
    _used(0),
    _desired_plab_sz(desired_plab_sz_),
    _filter(wt)
  {
    size_t min_sz = min_size();
    size_t max_sz = max_size();
    size_t aligned_min_sz = align_object_size(min_sz);
    size_t aligned_max_sz = align_object_size(max_sz);
    assert(min_sz <= aligned_min_sz && max_sz >= aligned_max_sz &&
           min_sz <= max_sz,
           "PLAB clipping computation in adjust_desired_plab_sz()"
           " may be incorrect");
  }

  static const size_t min_size() {
    return ParGCAllocBuffer::min_size();
  }

  static const size_t max_size() {
    return ParGCAllocBuffer::max_size();
  }

  size_t desired_plab_sz() {
    return _desired_plab_sz;
  }

  void adjust_desired_plab_sz(); // filter computation, latches output to
                                 // _desired_plab_sz, clears sensor accumulators

  void add_allocated(size_t v) {
    Atomic::add_ptr(v, &_allocated);
  }

  void add_unused(size_t v) {
    Atomic::add_ptr(v, &_unused);
  }

  void add_wasted(size_t v) {
    Atomic::add_ptr(v, &_wasted);
  }
};

class ParGCAllocBufferWithBOT: public ParGCAllocBuffer {
  BlockOffsetArrayContigSpace _bt;
  BlockOffsetSharedArray*     _bsa;
  HeapWord*                   _true_end;  // end of the whole ParGCAllocBuffer

  static const size_t ChunkSizeInWords;
  static const size_t ChunkSizeInBytes;
  HeapWord* allocate_slow(size_t word_sz);

  void fill_region_with_block(MemRegion mr, bool contig);

public:
  ParGCAllocBufferWithBOT(size_t word_sz, BlockOffsetSharedArray* bsa);

  HeapWord* allocate(size_t word_sz) {
    HeapWord* res = ParGCAllocBuffer::allocate(word_sz);
    if (res != NULL) {
      _bt.alloc_block(res, word_sz);
    } else {
      res = allocate_slow(word_sz);
    }
    return res;
  }

  void undo_allocation(HeapWord* obj, size_t word_sz);

  void set_buf(HeapWord* buf_start) {
    ParGCAllocBuffer::set_buf(buf_start);
    _true_end = _hard_end;
    _bt.set_region(MemRegion(buf_start, word_sz()));
    _bt.initialize_threshold();
  }

  void retire(bool end_of_gc, bool retain);

  MemRegion range() {
    return MemRegion(_top, _true_end);
  }
};

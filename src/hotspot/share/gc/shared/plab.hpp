/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_PLAB_HPP
#define SHARE_GC_SHARED_PLAB_HPP

#include "gc/shared/collectedHeap.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

// Forward declarations.
class PLABStats;

// A per-thread allocation buffer used during GC.
class PLAB: public CHeapObj<mtGC> {
protected:
  char      head[32];
  size_t    _word_sz;          // In HeapWord units
  HeapWord* _bottom;
  HeapWord* _top;
  HeapWord* _end;           // Last allocatable address + 1
  HeapWord* _hard_end;      // _end + AlignmentReserve
  // In support of ergonomic sizing of PLAB's
  size_t    _allocated;     // in HeapWord units
  size_t    _wasted;        // in HeapWord units
  size_t    _undo_wasted;
  char      tail[32];

  // Force future allocations to fail and queries for contains()
  // to return false. Returns the amount of unused space in this PLAB.
  size_t invalidate() {
    _end    = _hard_end;
    size_t remaining = pointer_delta(_end, _top);  // Calculate remaining space.
    _top    = _end;      // Force future allocations to fail.
    _bottom = _end;      // Force future contains() queries to return false.
    return remaining;
  }

  // Fill in remaining space with a dummy object and invalidate the PLAB. Returns
  // the amount of remaining space.
  size_t retire_internal();

  void add_undo_waste(HeapWord* obj, size_t word_sz);

  // Undo the last allocation in the buffer, which is required to be of the
  // "obj" of the given "word_sz".
  void undo_last_allocation(HeapWord* obj, size_t word_sz);

public:
  static void startup_initialization();

  // Initializes the buffer to be empty, but with the given "word_sz".
  // Must get initialized with "set_buf" for an allocation to succeed.
  PLAB(size_t word_sz);

  static size_t size_required_for_allocation(size_t word_size) { return word_size + CollectedHeap::lab_alignment_reserve(); }

  // Minimum PLAB size.
  static size_t min_size();
  // Maximum PLAB size.
  static size_t max_size();

  // If an allocation of the given "word_sz" can be satisfied within the
  // buffer, do the allocation, returning a pointer to the start of the
  // allocated block.  If the allocation request cannot be satisfied,
  // return null.
  HeapWord* allocate(size_t word_sz) {
    HeapWord* res = _top;
    if (pointer_delta(_end, _top) >= word_sz) {
      _top = _top + word_sz;
      return res;
    } else {
      return nullptr;
    }
  }

  // Undo any allocation in the buffer, which is required to be of the
  // "obj" of the given "word_sz".
  void undo_allocation(HeapWord* obj, size_t word_sz);

  // The total (word) size of the buffer, including both allocated and
  // unallocated space.
  size_t word_sz() { return _word_sz; }

  size_t waste() { return _wasted; }
  size_t undo_waste() { return _undo_wasted; }

  // The number of words of unallocated space remaining in the buffer.
  size_t words_remaining() {
    assert(_end >= _top, "Negative buffer");
    return pointer_delta(_end, _top, HeapWordSize);
  }

  bool contains(void* addr) {
    return (void*)_bottom <= addr && addr < (void*)_hard_end;
  }

  // Sets the space of the buffer to be [buf, space+word_sz()).
  void set_buf(HeapWord* buf, size_t new_word_sz) {
    assert(new_word_sz > CollectedHeap::lab_alignment_reserve(), "Too small");
    _word_sz = new_word_sz;

    _bottom   = buf;
    _top      = _bottom;
    _hard_end = _bottom + word_sz();
    _end      = _hard_end - CollectedHeap::lab_alignment_reserve();
    assert(_end >= _top, "Negative buffer");
    // In support of ergonomic sizing
    _allocated += word_sz();
  }

  // Flush allocation statistics into the given PLABStats supporting ergonomic
  // sizing of PLAB's and retire the current buffer. To be called at the end of
  // GC.
  void flush_and_retire_stats(PLABStats* stats);

  // Fills in the unallocated portion of the buffer with a garbage object and updates
  // statistics. To be called during GC.
  void retire();

  HeapWord* top() const {
    return _top;
  }
};

// PLAB book-keeping.
class PLABStats : public CHeapObj<mtGC> {
protected:
  const char* _description;   // Identifying string.

  Atomic<size_t> _allocated;          // Total allocated
  Atomic<size_t> _wasted;             // of which wasted (internal fragmentation)
  Atomic<size_t> _undo_wasted;        // of which wasted on undo (is not used for calculation of PLAB size)
  Atomic<size_t> _unused;             // Unused in last buffer

  virtual void reset() {
    _allocated.store_relaxed(0);
    _wasted.store_relaxed(0);
    _undo_wasted.store_relaxed(0);
    _unused.store_relaxed(0);
  }

public:
  PLABStats(const char* description) :
    _description(description),
    _allocated(0),
    _wasted(0),
    _undo_wasted(0),
    _unused(0)
  { }

  virtual ~PLABStats() { }

  size_t allocated() const { return _allocated.load_relaxed(); }
  size_t wasted() const { return _wasted.load_relaxed(); }
  size_t undo_wasted() const { return _undo_wasted.load_relaxed(); }
  size_t unused() const { return _unused.load_relaxed(); }
  size_t used() const { return allocated() - (wasted() + unused()); }

  static size_t min_size() {
    return PLAB::min_size();
  }

  static size_t max_size() {
    return PLAB::max_size();
  }

  inline void add_allocated(size_t v);

  inline void add_unused(size_t v);

  inline void add_wasted(size_t v);

  inline void add_undo_wasted(size_t v);
};

#endif // SHARE_GC_SHARED_PLAB_HPP

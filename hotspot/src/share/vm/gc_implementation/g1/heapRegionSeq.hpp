/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSEQ_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSEQ_HPP

#include "gc_implementation/g1/g1BiasedArray.hpp"

class HeapRegion;
class HeapRegionClosure;
class FreeRegionList;

class G1HeapRegionTable : public G1BiasedMappedArray<HeapRegion*> {
 protected:
   virtual HeapRegion* default_value() const { return NULL; }
};

// This class keeps track of the region metadata (i.e., HeapRegion
// instances). They are kept in the _regions array in address
// order. A region's index in the array corresponds to its index in
// the heap (i.e., 0 is the region at the bottom of the heap, 1 is
// the one after it, etc.). Two regions that are consecutive in the
// array should also be adjacent in the address space (i.e.,
// region(i).end() == region(i+1).bottom().
//
// We create a HeapRegion when we commit the region's address space
// for the first time. When we uncommit the address space of a
// region we retain the HeapRegion to be able to re-use it in the
// future (in case we recommit it).
//
// We keep track of three lengths:
//
// * _committed_length (returned by length()) is the number of currently
//   committed regions.
// * _allocated_length (not exposed outside this class) is the
//   number of regions for which we have HeapRegions.
// * max_length() returns the maximum number of regions the heap can have.
//
// and maintain that: _committed_length <= _allocated_length <= max_length()

class HeapRegionSeq: public CHeapObj<mtGC> {
  friend class VMStructs;

  G1HeapRegionTable _regions;

  // The number of regions committed in the heap.
  uint _committed_length;

  // A hint for which index to start searching from for humongous
  // allocations.
  uint _next_search_index;

  // The number of regions for which we have allocated HeapRegions for.
  uint _allocated_length;

  // Find a contiguous set of empty regions of length num, starting
  // from the given index.
  uint find_contiguous_from(uint from, uint num);

  void increment_allocated_length() {
    assert(_allocated_length < max_length(), "pre-condition");
    _allocated_length++;
  }

  void increment_length() {
    assert(length() < max_length(), "pre-condition");
    _committed_length++;
  }

  void decrement_length() {
    assert(length() > 0, "pre-condition");
    _committed_length--;
  }

  HeapWord* heap_bottom() const { return _regions.bottom_address_mapped(); }
  HeapWord* heap_end() const {return _regions.end_address_mapped(); }

 public:
  // Empty contructor, we'll initialize it with the initialize() method.
  HeapRegionSeq() : _regions(), _committed_length(0), _next_search_index(0), _allocated_length(0) { }

  void initialize(HeapWord* bottom, HeapWord* end);

  // Return the HeapRegion at the given index. Assume that the index
  // is valid.
  inline HeapRegion* at(uint index) const;

  // If addr is within the committed space return its corresponding
  // HeapRegion, otherwise return NULL.
  inline HeapRegion* addr_to_region(HeapWord* addr) const;

  // Return the HeapRegion that corresponds to the given
  // address. Assume the address is valid.
  inline HeapRegion* addr_to_region_unsafe(HeapWord* addr) const;

  // Return the number of regions that have been committed in the heap.
  uint length() const { return _committed_length; }

  // Return the maximum number of regions in the heap.
  uint max_length() const { return (uint)_regions.length(); }

  // Expand the sequence to reflect that the heap has grown from
  // old_end to new_end. Either create new HeapRegions, or re-use
  // existing ones, and return them in the given list. Returns the
  // memory region that covers the newly-created regions. If a
  // HeapRegion allocation fails, the result memory region might be
  // smaller than the desired one.
  MemRegion expand_by(HeapWord* old_end, HeapWord* new_end,
                      FreeRegionList* list);

  // Return the number of contiguous regions at the end of the sequence
  // that are available for allocation.
  uint free_suffix();

  // Find a contiguous set of empty regions of length num and return
  // the index of the first region or G1_NULL_HRS_INDEX if the
  // search was unsuccessful.
  uint find_contiguous(uint num);

  // Apply blk->doHeapRegion() on all committed regions in address order,
  // terminating the iteration early if doHeapRegion() returns true.
  void iterate(HeapRegionClosure* blk) const;

  // As above, but start the iteration from hr and loop around. If hr
  // is NULL, we start from the first region in the heap.
  void iterate_from(HeapRegion* hr, HeapRegionClosure* blk) const;

  // Tag as uncommitted as many regions that are completely free as
  // possible, up to num_regions_to_remove, from the suffix of the committed
  // sequence. Return the actual number of removed regions.
  uint shrink_by(uint num_regions_to_remove);

  // Do some sanity checking.
  void verify_optional() PRODUCT_RETURN;
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSEQ_HPP

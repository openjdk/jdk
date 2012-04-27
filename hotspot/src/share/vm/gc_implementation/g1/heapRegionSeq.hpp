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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSEQ_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSEQ_HPP

class HeapRegion;
class HeapRegionClosure;
class FreeRegionList;

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
// * _length (returned by length()) is the number of currently
//   committed regions.
// * _allocated_length (not exposed outside this class) is the
//   number of regions for which we have HeapRegions.
// * _max_length (returned by max_length()) is the maximum number of
//   regions the heap can have.
//
// and maintain that: _length <= _allocated_length <= _max_length

class HeapRegionSeq: public CHeapObj {
  friend class VMStructs;

  // The array that holds the HeapRegions.
  HeapRegion** _regions;

  // Version of _regions biased to address 0
  HeapRegion** _regions_biased;

  // The number of regions committed in the heap.
  uint _length;

  // The address of the first reserved word in the heap.
  HeapWord* _heap_bottom;

  // The address of the last reserved word in the heap - 1.
  HeapWord* _heap_end;

  // The log of the region byte size.
  uint _region_shift;

  // A hint for which index to start searching from for humongous
  // allocations.
  uint _next_search_index;

  // The number of regions for which we have allocated HeapRegions for.
  uint _allocated_length;

  // The maximum number of regions in the heap.
  uint _max_length;

  // Find a contiguous set of empty regions of length num, starting
  // from the given index.
  uint find_contiguous_from(uint from, uint num);

  // Map a heap address to a biased region index. Assume that the
  // address is valid.
  inline uintx addr_to_index_biased(HeapWord* addr) const;

  void increment_length(uint* length) {
    assert(*length < _max_length, "pre-condition");
    *length += 1;
  }

  void decrement_length(uint* length) {
    assert(*length > 0, "pre-condition");
    *length -= 1;
  }

 public:
  // Empty contructor, we'll initialize it with the initialize() method.
  HeapRegionSeq() { }

  void initialize(HeapWord* bottom, HeapWord* end, uint max_length);

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
  uint length() const { return _length; }

  // Return the maximum number of regions in the heap.
  uint max_length() const { return _max_length; }

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
  // possible, up to shrink_bytes, from the suffix of the committed
  // sequence. Return a MemRegion that corresponds to the address
  // range of the uncommitted regions. Assume shrink_bytes is page and
  // heap region aligned.
  MemRegion shrink_by(size_t shrink_bytes, uint* num_regions_deleted);

  // Do some sanity checking.
  void verify_optional() PRODUCT_RETURN;
};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_HEAPREGIONSEQ_HPP

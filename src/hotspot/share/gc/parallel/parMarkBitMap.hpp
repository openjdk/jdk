/*
 * Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PARMARKBITMAP_HPP
#define SHARE_GC_PARALLEL_PARMARKBITMAP_HPP

#include "memory/memRegion.hpp"
#include "oops/oop.hpp"
#include "utilities/bitMap.hpp"

class PSVirtualSpace;

class ParMarkBitMap: public CHeapObj<mtGC> {
  typedef BitMap::idx_t idx_t;

public:
  inline ParMarkBitMap();
  bool initialize(MemRegion covered_region);

  // Atomically mark an object as live.
  inline bool mark_obj(HeapWord* addr);
  inline bool mark_obj(oop obj);

  inline bool is_marked(HeapWord* addr) const;
  inline bool is_marked(oop obj)        const;

  inline bool is_unmarked(HeapWord* addr) const;
  inline bool is_unmarked(oop obj)        const;

  size_t reserved_byte_size() const { return _reserved_byte_size; }

  inline HeapWord* find_obj_beg(HeapWord* beg, HeapWord* end) const;

  // Return the address of the last obj-start in the range [beg, end).  If no
  // object is found, return end.
  inline HeapWord* find_obj_beg_reverse(HeapWord* beg, HeapWord* end) const;
  // Clear a range of bits corresponding to heap address range [beg, end).
  inline void clear_range(HeapWord* beg, HeapWord* end);

  void print_on_error(outputStream* st) const {
    st->print_cr("Marking Bits: (ParMarkBitMap*) " PTR_FORMAT, p2i(this));
    _beg_bits.print_on_error(st, " Begin Bits: ");
  }

#ifdef  ASSERT
  void verify_clear() const;
#endif  // #ifdef ASSERT

private:

  // Each bit in the bitmap represents one unit of 'object granularity.' Objects
  // are double-word aligned in 32-bit VMs, but not in 64-bit VMs, so the 32-bit
  // granularity is 2, 64-bit is 1.
  static inline int obj_granularity_shift() { return LogMinObjAlignment; }

  HeapWord*       _heap_start;
  size_t          _heap_size;
  BitMapView      _beg_bits;
  PSVirtualSpace* _virtual_space;
  size_t          _reserved_byte_size;

  // Convert sizes from bits to HeapWords and back.  An object that is n bits
  // long will be bits_to_words(n) words long.  An object that is m words long
  // will take up words_to_bits(m) bits in the bitmap.
  inline static size_t bits_to_words(idx_t bits);
  inline static idx_t  words_to_bits(size_t words);

  // Return word-aligned up range_end, which must not be greater than size().
  inline idx_t align_range_end(idx_t range_end) const;

  inline HeapWord* heap_start() const;
  inline HeapWord* heap_end() const;
  inline size_t    heap_size() const;
  inline size_t    size() const;

  // Convert a heap address to/from a bit index.
  inline idx_t     addr_to_bit(HeapWord* addr) const;
  inline HeapWord* bit_to_addr(idx_t bit) const;

#ifdef  ASSERT
  inline void verify_bit(idx_t bit) const;
  inline void verify_addr(HeapWord* addr) const;
#endif  // #ifdef ASSERT
};

#endif // SHARE_GC_PARALLEL_PARMARKBITMAP_HPP

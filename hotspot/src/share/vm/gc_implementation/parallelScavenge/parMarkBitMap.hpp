/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PARMARKBITMAP_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PARMARKBITMAP_HPP

#include "memory/memRegion.hpp"
#include "oops/oop.hpp"
#include "utilities/bitMap.hpp"

class ParMarkBitMapClosure;
class PSVirtualSpace;

class ParMarkBitMap: public CHeapObj<mtGC>
{
public:
  typedef BitMap::idx_t idx_t;

  // Values returned by the iterate() methods.
  enum IterationStatus { incomplete, complete, full, would_overflow };

  inline ParMarkBitMap();
  bool initialize(MemRegion covered_region);

  // Atomically mark an object as live.
  bool mark_obj(HeapWord* addr, size_t size);
  inline bool mark_obj(oop obj, int size);

  // Return whether the specified begin or end bit is set.
  inline bool is_obj_beg(idx_t bit) const;
  inline bool is_obj_end(idx_t bit) const;

  // Traditional interface for testing whether an object is marked or not (these
  // test only the begin bits).
  inline bool is_marked(idx_t bit)      const;
  inline bool is_marked(HeapWord* addr) const;
  inline bool is_marked(oop obj)        const;

  inline bool is_unmarked(idx_t bit)      const;
  inline bool is_unmarked(HeapWord* addr) const;
  inline bool is_unmarked(oop obj)        const;

  // Convert sizes from bits to HeapWords and back.  An object that is n bits
  // long will be bits_to_words(n) words long.  An object that is m words long
  // will take up words_to_bits(m) bits in the bitmap.
  inline static size_t bits_to_words(idx_t bits);
  inline static idx_t  words_to_bits(size_t words);

  // Return the size in words of an object given a begin bit and an end bit, or
  // the equivalent beg_addr and end_addr.
  inline size_t obj_size(idx_t beg_bit, idx_t end_bit) const;
  inline size_t obj_size(HeapWord* beg_addr, HeapWord* end_addr) const;

  // Return the size in words of the object (a search is done for the end bit).
  inline size_t obj_size(idx_t beg_bit)  const;
  inline size_t obj_size(HeapWord* addr) const;

  // Apply live_closure to each live object that lies completely within the
  // range [live_range_beg, live_range_end).  This is used to iterate over the
  // compacted region of the heap.  Return values:
  //
  // incomplete         The iteration is not complete.  The last object that
  //                    begins in the range does not end in the range;
  //                    closure->source() is set to the start of that object.
  //
  // complete           The iteration is complete.  All objects in the range
  //                    were processed and the closure is not full;
  //                    closure->source() is set one past the end of the range.
  //
  // full               The closure is full; closure->source() is set to one
  //                    past the end of the last object processed.
  //
  // would_overflow     The next object in the range would overflow the closure;
  //                    closure->source() is set to the start of that object.
  IterationStatus iterate(ParMarkBitMapClosure* live_closure,
                          idx_t range_beg, idx_t range_end) const;
  inline IterationStatus iterate(ParMarkBitMapClosure* live_closure,
                                 HeapWord* range_beg,
                                 HeapWord* range_end) const;

  // Apply live closure as above and additionally apply dead_closure to all dead
  // space in the range [range_beg, dead_range_end).  Note that dead_range_end
  // must be >= range_end.  This is used to iterate over the dense prefix.
  //
  // This method assumes that if the first bit in the range (range_beg) is not
  // marked, then dead space begins at that point and the dead_closure is
  // applied.  Thus callers must ensure that range_beg is not in the middle of a
  // live object.
  IterationStatus iterate(ParMarkBitMapClosure* live_closure,
                          ParMarkBitMapClosure* dead_closure,
                          idx_t range_beg, idx_t range_end,
                          idx_t dead_range_end) const;
  inline IterationStatus iterate(ParMarkBitMapClosure* live_closure,
                                 ParMarkBitMapClosure* dead_closure,
                                 HeapWord* range_beg,
                                 HeapWord* range_end,
                                 HeapWord* dead_range_end) const;

  // Return the number of live words in the range [beg_addr, end_obj) due to
  // objects that start in the range.  If a live object extends onto the range,
  // the caller must detect and account for any live words due to that object.
  // If a live object extends beyond the end of the range, only the words within
  // the range are included in the result. The end of the range must be a live object,
  // which is the case when updating pointers.  This allows a branch to be removed
  // from inside the loop.
  size_t live_words_in_range(HeapWord* beg_addr, oop end_obj) const;

  inline HeapWord* region_start() const;
  inline HeapWord* region_end() const;
  inline size_t    region_size() const;
  inline size_t    size() const;

  size_t reserved_byte_size() const { return _reserved_byte_size; }

  // Convert a heap address to/from a bit index.
  inline idx_t     addr_to_bit(HeapWord* addr) const;
  inline HeapWord* bit_to_addr(idx_t bit) const;

  // Return the bit index of the first marked object that begins (or ends,
  // respectively) in the range [beg, end).  If no object is found, return end.
  inline idx_t find_obj_beg(idx_t beg, idx_t end) const;
  inline idx_t find_obj_end(idx_t beg, idx_t end) const;

  inline HeapWord* find_obj_beg(HeapWord* beg, HeapWord* end) const;
  inline HeapWord* find_obj_end(HeapWord* beg, HeapWord* end) const;

  // Clear a range of bits or the entire bitmap (both begin and end bits are
  // cleared).
  inline void clear_range(idx_t beg, idx_t end);

  // Return the number of bits required to represent the specified number of
  // HeapWords, or the specified region.
  static inline idx_t bits_required(size_t words);
  static inline idx_t bits_required(MemRegion covered_region);

  void print_on_error(outputStream* st) const {
    st->print_cr("Marking Bits: (ParMarkBitMap*) " PTR_FORMAT, this);
    _beg_bits.print_on_error(st, " Begin Bits: ");
    _end_bits.print_on_error(st, " End Bits:   ");
  }

#ifdef  ASSERT
  void verify_clear() const;
  inline void verify_bit(idx_t bit) const;
  inline void verify_addr(HeapWord* addr) const;
#endif  // #ifdef ASSERT

private:
  // Each bit in the bitmap represents one unit of 'object granularity.' Objects
  // are double-word aligned in 32-bit VMs, but not in 64-bit VMs, so the 32-bit
  // granularity is 2, 64-bit is 1.
  static inline size_t obj_granularity() { return size_t(MinObjAlignment); }
  static inline int obj_granularity_shift() { return LogMinObjAlignment; }

  HeapWord*       _region_start;
  size_t          _region_size;
  BitMap          _beg_bits;
  BitMap          _end_bits;
  PSVirtualSpace* _virtual_space;
  size_t          _reserved_byte_size;
};

inline ParMarkBitMap::ParMarkBitMap():
  _beg_bits(), _end_bits(), _region_start(NULL), _region_size(0), _virtual_space(NULL), _reserved_byte_size(0)
{ }

inline void ParMarkBitMap::clear_range(idx_t beg, idx_t end)
{
  _beg_bits.clear_range(beg, end);
  _end_bits.clear_range(beg, end);
}

inline ParMarkBitMap::idx_t
ParMarkBitMap::bits_required(size_t words)
{
  // Need two bits (one begin bit, one end bit) for each unit of 'object
  // granularity' in the heap.
  return words_to_bits(words * 2);
}

inline ParMarkBitMap::idx_t
ParMarkBitMap::bits_required(MemRegion covered_region)
{
  return bits_required(covered_region.word_size());
}

inline HeapWord*
ParMarkBitMap::region_start() const
{
  return _region_start;
}

inline HeapWord*
ParMarkBitMap::region_end() const
{
  return region_start() + region_size();
}

inline size_t
ParMarkBitMap::region_size() const
{
  return _region_size;
}

inline size_t
ParMarkBitMap::size() const
{
  return _beg_bits.size();
}

inline bool ParMarkBitMap::is_obj_beg(idx_t bit) const
{
  return _beg_bits.at(bit);
}

inline bool ParMarkBitMap::is_obj_end(idx_t bit) const
{
  return _end_bits.at(bit);
}

inline bool ParMarkBitMap::is_marked(idx_t bit) const
{
  return is_obj_beg(bit);
}

inline bool ParMarkBitMap::is_marked(HeapWord* addr) const
{
  return is_marked(addr_to_bit(addr));
}

inline bool ParMarkBitMap::is_marked(oop obj) const
{
  return is_marked((HeapWord*)obj);
}

inline bool ParMarkBitMap::is_unmarked(idx_t bit) const
{
  return !is_marked(bit);
}

inline bool ParMarkBitMap::is_unmarked(HeapWord* addr) const
{
  return !is_marked(addr);
}

inline bool ParMarkBitMap::is_unmarked(oop obj) const
{
  return !is_marked(obj);
}

inline size_t
ParMarkBitMap::bits_to_words(idx_t bits)
{
  return bits << obj_granularity_shift();
}

inline ParMarkBitMap::idx_t
ParMarkBitMap::words_to_bits(size_t words)
{
  return words >> obj_granularity_shift();
}

inline size_t ParMarkBitMap::obj_size(idx_t beg_bit, idx_t end_bit) const
{
  DEBUG_ONLY(verify_bit(beg_bit);)
  DEBUG_ONLY(verify_bit(end_bit);)
  return bits_to_words(end_bit - beg_bit + 1);
}

inline size_t
ParMarkBitMap::obj_size(HeapWord* beg_addr, HeapWord* end_addr) const
{
  DEBUG_ONLY(verify_addr(beg_addr);)
  DEBUG_ONLY(verify_addr(end_addr);)
  return pointer_delta(end_addr, beg_addr) + obj_granularity();
}

inline size_t ParMarkBitMap::obj_size(idx_t beg_bit) const
{
  const idx_t end_bit = _end_bits.get_next_one_offset_inline(beg_bit, size());
  assert(is_marked(beg_bit), "obj not marked");
  assert(end_bit < size(), "end bit missing");
  return obj_size(beg_bit, end_bit);
}

inline size_t ParMarkBitMap::obj_size(HeapWord* addr) const
{
  return obj_size(addr_to_bit(addr));
}

inline ParMarkBitMap::IterationStatus
ParMarkBitMap::iterate(ParMarkBitMapClosure* live_closure,
                       HeapWord* range_beg,
                       HeapWord* range_end) const
{
  return iterate(live_closure, addr_to_bit(range_beg), addr_to_bit(range_end));
}

inline ParMarkBitMap::IterationStatus
ParMarkBitMap::iterate(ParMarkBitMapClosure* live_closure,
                       ParMarkBitMapClosure* dead_closure,
                       HeapWord* range_beg,
                       HeapWord* range_end,
                       HeapWord* dead_range_end) const
{
  return iterate(live_closure, dead_closure,
                 addr_to_bit(range_beg), addr_to_bit(range_end),
                 addr_to_bit(dead_range_end));
}

inline bool
ParMarkBitMap::mark_obj(oop obj, int size)
{
  return mark_obj((HeapWord*)obj, (size_t)size);
}

inline BitMap::idx_t
ParMarkBitMap::addr_to_bit(HeapWord* addr) const
{
  DEBUG_ONLY(verify_addr(addr);)
  return words_to_bits(pointer_delta(addr, region_start()));
}

inline HeapWord*
ParMarkBitMap::bit_to_addr(idx_t bit) const
{
  DEBUG_ONLY(verify_bit(bit);)
  return region_start() + bits_to_words(bit);
}

inline ParMarkBitMap::idx_t
ParMarkBitMap::find_obj_beg(idx_t beg, idx_t end) const
{
  return _beg_bits.get_next_one_offset_inline_aligned_right(beg, end);
}

inline ParMarkBitMap::idx_t
ParMarkBitMap::find_obj_end(idx_t beg, idx_t end) const
{
  return _end_bits.get_next_one_offset_inline_aligned_right(beg, end);
}

inline HeapWord*
ParMarkBitMap::find_obj_beg(HeapWord* beg, HeapWord* end) const
{
  const idx_t beg_bit = addr_to_bit(beg);
  const idx_t end_bit = addr_to_bit(end);
  const idx_t search_end = BitMap::word_align_up(end_bit);
  const idx_t res_bit = MIN2(find_obj_beg(beg_bit, search_end), end_bit);
  return bit_to_addr(res_bit);
}

inline HeapWord*
ParMarkBitMap::find_obj_end(HeapWord* beg, HeapWord* end) const
{
  const idx_t beg_bit = addr_to_bit(beg);
  const idx_t end_bit = addr_to_bit(end);
  const idx_t search_end = BitMap::word_align_up(end_bit);
  const idx_t res_bit = MIN2(find_obj_end(beg_bit, search_end), end_bit);
  return bit_to_addr(res_bit);
}

#ifdef  ASSERT
inline void ParMarkBitMap::verify_bit(idx_t bit) const {
  // Allow one past the last valid bit; useful for loop bounds.
  assert(bit <= _beg_bits.size(), "bit out of range");
}

inline void ParMarkBitMap::verify_addr(HeapWord* addr) const {
  // Allow one past the last valid address; useful for loop bounds.
  assert(addr >= region_start(),
      err_msg("addr too small, addr: " PTR_FORMAT " region start: " PTR_FORMAT, addr, region_start()));
  assert(addr <= region_end(),
      err_msg("addr too big, addr: " PTR_FORMAT " region end: " PTR_FORMAT, addr, region_end()));
}
#endif  // #ifdef ASSERT

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_PARMARKBITMAP_HPP

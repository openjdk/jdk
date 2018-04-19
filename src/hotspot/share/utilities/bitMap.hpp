/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_BITMAP_HPP
#define SHARE_VM_UTILITIES_BITMAP_HPP

#include "memory/allocation.hpp"
#include "utilities/align.hpp"

// Forward decl;
class BitMapClosure;

// Operations for bitmaps represented as arrays of unsigned integers.
// Bit offsets are numbered from 0 to size-1.

// The "abstract" base BitMap class.
//
// The constructor and destructor are protected to prevent
// creation of BitMap instances outside of the BitMap class.
//
// The BitMap class doesn't use virtual calls on purpose,
// this ensures that we don't get a vtable unnecessarily.
//
// The allocation of the backing storage for the BitMap are handled by
// the subclasses. BitMap doesn't allocate or delete backing storage.
class BitMap {
  friend class BitMap2D;

 public:
  typedef size_t idx_t;         // Type used for bit and word indices.
  typedef uintptr_t bm_word_t;  // Element type of array that represents
                                // the bitmap.

  // Hints for range sizes.
  typedef enum {
    unknown_range, small_range, large_range
  } RangeSizeHint;

 private:
  bm_word_t* _map;     // First word in bitmap
  idx_t      _size;    // Size of bitmap (in bits)

 protected:
  // Return the position of bit within the word that contains it (e.g., if
  // bitmap words are 32 bits, return a number 0 <= n <= 31).
  static idx_t bit_in_word(idx_t bit) { return bit & (BitsPerWord - 1); }

  // Return a mask that will select the specified bit, when applied to the word
  // containing the bit.
  static bm_word_t bit_mask(idx_t bit) { return (bm_word_t)1 << bit_in_word(bit); }

  // Return the index of the word containing the specified bit.
  static idx_t word_index(idx_t bit)  { return bit >> LogBitsPerWord; }

  // Return the bit number of the first bit in the specified word.
  static idx_t bit_index(idx_t word)  { return word << LogBitsPerWord; }

  // Return the array of bitmap words, or a specific word from it.
  bm_word_t* map()                 { return _map; }
  const bm_word_t* map() const     { return _map; }
  bm_word_t  map(idx_t word) const { return _map[word]; }

  // Return a pointer to the word containing the specified bit.
  bm_word_t* word_addr(idx_t bit)             { return map() + word_index(bit); }
  const bm_word_t* word_addr(idx_t bit) const { return map() + word_index(bit); }

  // Set a word to a specified value or to all ones; clear a word.
  void set_word  (idx_t word, bm_word_t val) { _map[word] = val; }
  void set_word  (idx_t word)            { set_word(word, ~(bm_word_t)0); }
  void clear_word(idx_t word)            { _map[word] = 0; }

  // Utilities for ranges of bits.  Ranges are half-open [beg, end).

  // Ranges within a single word.
  bm_word_t inverted_bit_mask_for_range(idx_t beg, idx_t end) const;
  void  set_range_within_word      (idx_t beg, idx_t end);
  void  clear_range_within_word    (idx_t beg, idx_t end);
  void  par_put_range_within_word  (idx_t beg, idx_t end, bool value);

  // Ranges spanning entire words.
  void      set_range_of_words         (idx_t beg, idx_t end);
  void      clear_range_of_words       (idx_t beg, idx_t end);
  void      set_large_range_of_words   (idx_t beg, idx_t end);
  void      clear_large_range_of_words (idx_t beg, idx_t end);

  static void clear_range_of_words(bm_word_t* map, idx_t beg, idx_t end);

  // The index of the first full word in a range.
  idx_t word_index_round_up(idx_t bit) const;

  // Verification.
  void verify_index(idx_t index) const NOT_DEBUG_RETURN;
  void verify_range(idx_t beg_index, idx_t end_index) const NOT_DEBUG_RETURN;

  // Statistics.
  static const idx_t* _pop_count_table;
  static void init_pop_count_table();
  static idx_t num_set_bits(bm_word_t w);
  static idx_t num_set_bits_from_table(unsigned char c);

  // Allocation Helpers.

  // Allocates and clears the bitmap memory.
  template <class Allocator>
  static bm_word_t* allocate(const Allocator&, idx_t size_in_bits, bool clear = true);

  // Reallocates and clears the new bitmap memory.
  template <class Allocator>
  static bm_word_t* reallocate(const Allocator&, bm_word_t* map, idx_t old_size_in_bits, idx_t new_size_in_bits, bool clear = true);

  // Free the bitmap memory.
  template <class Allocator>
  static void free(const Allocator&, bm_word_t* map, idx_t size_in_bits);

  // Protected functions, that are used by BitMap sub-classes that support them.

  // Resize the backing bitmap memory.
  //
  // Old bits are transfered to the new memory
  // and the extended memory is cleared.
  template <class Allocator>
  void resize(const Allocator& allocator, idx_t new_size_in_bits);

  // Set up and clear the bitmap memory.
  //
  // Precondition: The bitmap was default constructed and has
  // not yet had memory allocated via resize or (re)initialize.
  template <class Allocator>
  void initialize(const Allocator& allocator, idx_t size_in_bits);

  // Set up and clear the bitmap memory.
  //
  // Can be called on previously initialized bitmaps.
  template <class Allocator>
  void reinitialize(const Allocator& allocator, idx_t new_size_in_bits);

  // Set the map and size.
  void update(bm_word_t* map, idx_t size) {
    _map = map;
    _size = size;
  }

  // Protected constructor and destructor.
  BitMap(bm_word_t* map, idx_t size_in_bits) : _map(map), _size(size_in_bits) {}
  ~BitMap() {}

 public:
  // Pretouch the entire range of memory this BitMap covers.
  void pretouch();

  // Accessing
  static idx_t calc_size_in_words(size_t size_in_bits) {
    return word_index(size_in_bits + BitsPerWord - 1);
  }

  static idx_t calc_size_in_bytes(size_t size_in_bits) {
    return calc_size_in_words(size_in_bits) * BytesPerWord;
  }

  idx_t size() const          { return _size; }
  idx_t size_in_words() const { return calc_size_in_words(size()); }
  idx_t size_in_bytes() const { return calc_size_in_bytes(size()); }

  bool at(idx_t index) const {
    verify_index(index);
    return (*word_addr(index) & bit_mask(index)) != 0;
  }

  // Align bit index up or down to the next bitmap word boundary, or check
  // alignment.
  static idx_t word_align_up(idx_t bit) {
    return align_up(bit, BitsPerWord);
  }
  static idx_t word_align_down(idx_t bit) {
    return align_down(bit, BitsPerWord);
  }
  static bool is_word_aligned(idx_t bit) {
    return word_align_up(bit) == bit;
  }

  // Set or clear the specified bit.
  inline void set_bit(idx_t bit);
  inline void clear_bit(idx_t bit);

  // Atomically set or clear the specified bit.
  inline bool par_set_bit(idx_t bit);
  inline bool par_clear_bit(idx_t bit);

  // Put the given value at the given offset. The parallel version
  // will CAS the value into the bitmap and is quite a bit slower.
  // The parallel version also returns a value indicating if the
  // calling thread was the one that changed the value of the bit.
  void at_put(idx_t index, bool value);
  bool par_at_put(idx_t index, bool value);

  // Update a range of bits.  Ranges are half-open [beg, end).
  void set_range   (idx_t beg, idx_t end);
  void clear_range (idx_t beg, idx_t end);
  void set_large_range   (idx_t beg, idx_t end);
  void clear_large_range (idx_t beg, idx_t end);
  void at_put_range(idx_t beg, idx_t end, bool value);
  void par_at_put_range(idx_t beg, idx_t end, bool value);
  void at_put_large_range(idx_t beg, idx_t end, bool value);
  void par_at_put_large_range(idx_t beg, idx_t end, bool value);

  // Update a range of bits, using a hint about the size.  Currently only
  // inlines the predominant case of a 1-bit range.  Works best when hint is a
  // compile-time constant.
  void set_range(idx_t beg, idx_t end, RangeSizeHint hint);
  void clear_range(idx_t beg, idx_t end, RangeSizeHint hint);
  void par_set_range(idx_t beg, idx_t end, RangeSizeHint hint);
  void par_clear_range  (idx_t beg, idx_t end, RangeSizeHint hint);

  // Clearing
  void clear_large();
  inline void clear();

  // Iteration support.  Returns "true" if the iteration completed, false
  // if the iteration terminated early (because the closure "blk" returned
  // false).
  bool iterate(BitMapClosure* blk, idx_t leftIndex, idx_t rightIndex);
  bool iterate(BitMapClosure* blk) {
    // call the version that takes an interval
    return iterate(blk, 0, size());
  }

  // Looking for 1's and 0's at indices equal to or greater than "l_index",
  // stopping if none has been found before "r_index", and returning
  // "r_index" (which must be at most "size") in that case.
  idx_t get_next_one_offset (idx_t l_index, idx_t r_index) const;
  idx_t get_next_zero_offset(idx_t l_index, idx_t r_index) const;

  idx_t get_next_one_offset(idx_t offset) const {
    return get_next_one_offset(offset, size());
  }
  idx_t get_next_zero_offset(idx_t offset) const {
    return get_next_zero_offset(offset, size());
  }

  // Like "get_next_one_offset", except requires that "r_index" is
  // aligned to bitsizeof(bm_word_t).
  idx_t get_next_one_offset_aligned_right(idx_t l_index, idx_t r_index) const;

  // Returns the number of bits set in the bitmap.
  idx_t count_one_bits() const;

  // Set operations.
  void set_union(const BitMap& bits);
  void set_difference(const BitMap& bits);
  void set_intersection(const BitMap& bits);
  // Returns true iff "this" is a superset of "bits".
  bool contains(const BitMap& bits) const;
  // Returns true iff "this and "bits" have a non-empty intersection.
  bool intersects(const BitMap& bits) const;

  // Returns result of whether this map changed
  // during the operation
  bool set_union_with_result(const BitMap& bits);
  bool set_difference_with_result(const BitMap& bits);
  bool set_intersection_with_result(const BitMap& bits);

  void set_from(const BitMap& bits);

  bool is_same(const BitMap& bits) const;

  // Test if all bits are set or cleared
  bool is_full() const;
  bool is_empty() const;

  void print_on_error(outputStream* st, const char* prefix) const;

#ifndef PRODUCT
 public:
  // Printing
  void print_on(outputStream* st) const;
#endif
};

// A concrete implementation of the the "abstract" BitMap class.
//
// The BitMapView is used when the backing storage is managed externally.
class BitMapView : public BitMap {
 public:
  BitMapView() : BitMap(NULL, 0) {}
  BitMapView(bm_word_t* map, idx_t size_in_bits) : BitMap(map, size_in_bits) {}
};

// A BitMap with storage in a ResourceArea.
class ResourceBitMap : public BitMap {

 public:
  ResourceBitMap() : BitMap(NULL, 0) {}
  // Clears the bitmap memory.
  ResourceBitMap(idx_t size_in_bits);

  // Resize the backing bitmap memory.
  //
  // Old bits are transfered to the new memory
  // and the extended memory is cleared.
  void resize(idx_t new_size_in_bits);

  // Set up and clear the bitmap memory.
  //
  // Precondition: The bitmap was default constructed and has
  // not yet had memory allocated via resize or initialize.
  void initialize(idx_t size_in_bits);

  // Set up and clear the bitmap memory.
  //
  // Can be called on previously initialized bitmaps.
  void reinitialize(idx_t size_in_bits);
};

// A BitMap with storage in a specific Arena.
class ArenaBitMap : public BitMap {
 public:
  // Clears the bitmap memory.
  ArenaBitMap(Arena* arena, idx_t size_in_bits);

 private:
  // Don't allow copy or assignment.
  ArenaBitMap(const ArenaBitMap&);
  ArenaBitMap& operator=(const ArenaBitMap&);
};

// A BitMap with storage in the CHeap.
class CHeapBitMap : public BitMap {

 private:
  // Don't allow copy or assignment, to prevent the
  // allocated memory from leaking out to other instances.
  CHeapBitMap(const CHeapBitMap&);
  CHeapBitMap& operator=(const CHeapBitMap&);

  // NMT memory type
  MEMFLAGS _flags;

 public:
  CHeapBitMap(MEMFLAGS flags = mtInternal) : BitMap(NULL, 0), _flags(flags) {}
  // Clears the bitmap memory.
  CHeapBitMap(idx_t size_in_bits, MEMFLAGS flags = mtInternal, bool clear = true);
  ~CHeapBitMap();

  // Resize the backing bitmap memory.
  //
  // Old bits are transfered to the new memory
  // and the extended memory is cleared.
  void resize(idx_t new_size_in_bits);

  // Set up and clear the bitmap memory.
  //
  // Precondition: The bitmap was default constructed and has
  // not yet had memory allocated via resize or initialize.
  void initialize(idx_t size_in_bits);

  // Set up and clear the bitmap memory.
  //
  // Can be called on previously initialized bitmaps.
  void reinitialize(idx_t size_in_bits);
};

// Convenience class wrapping BitMap which provides multiple bits per slot.
class BitMap2D {
 public:
  typedef BitMap::idx_t idx_t;          // Type used for bit and word indices.
  typedef BitMap::bm_word_t bm_word_t;  // Element type of array that
                                        // represents the bitmap.
 private:
  ResourceBitMap _map;
  idx_t          _bits_per_slot;

  idx_t bit_index(idx_t slot_index, idx_t bit_within_slot_index) const {
    return slot_index * _bits_per_slot + bit_within_slot_index;
  }

  void verify_bit_within_slot_index(idx_t index) const {
    assert(index < _bits_per_slot, "bit_within_slot index out of bounds");
  }

 public:
  // Construction. bits_per_slot must be greater than 0.
  BitMap2D(idx_t bits_per_slot) :
      _map(), _bits_per_slot(bits_per_slot) {}

  // Allocates necessary data structure in resource area. bits_per_slot must be greater than 0.
  BitMap2D(idx_t size_in_slots, idx_t bits_per_slot) :
      _map(size_in_slots * bits_per_slot), _bits_per_slot(bits_per_slot) {}

  idx_t size_in_bits() {
    return _map.size();
  }

  // Returns number of full slots that have been allocated
  idx_t size_in_slots() {
    // Round down
    return _map.size() / _bits_per_slot;
  }

  bool is_valid_index(idx_t slot_index, idx_t bit_within_slot_index);
  bool at(idx_t slot_index, idx_t bit_within_slot_index) const;
  void set_bit(idx_t slot_index, idx_t bit_within_slot_index);
  void clear_bit(idx_t slot_index, idx_t bit_within_slot_index);
  void at_put(idx_t slot_index, idx_t bit_within_slot_index, bool value);
  void at_put_grow(idx_t slot_index, idx_t bit_within_slot_index, bool value);
};

// Closure for iterating over BitMaps

class BitMapClosure {
 public:
  // Callback when bit in map is set.  Should normally return "true";
  // return of false indicates that the bitmap iteration should terminate.
  virtual bool do_bit(BitMap::idx_t offset) = 0;
};

#endif // SHARE_VM_UTILITIES_BITMAP_HPP

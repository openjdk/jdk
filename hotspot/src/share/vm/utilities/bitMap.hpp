/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

// Closure for iterating over BitMaps

class BitMapClosure VALUE_OBJ_CLASS_SPEC {
 public:
  // Callback when bit in map is set
  virtual void do_bit(size_t offset) = 0;
};


// Operations for bitmaps represented as arrays of unsigned 32- or 64-bit
// integers (uintptr_t).
//
// Bit offsets are numbered from 0 to size-1

class BitMap VALUE_OBJ_CLASS_SPEC {
  friend class BitMap2D;

 public:
  typedef size_t idx_t;         // Type used for bit and word indices.

  // Hints for range sizes.
  typedef enum {
    unknown_range, small_range, large_range
  } RangeSizeHint;

 private:
  idx_t* _map;     // First word in bitmap
  idx_t  _size;    // Size of bitmap (in bits)

  // Puts the given value at the given offset, using resize() to size
  // the bitmap appropriately if needed using factor-of-two expansion.
  void at_put_grow(idx_t index, bool value);

 protected:
  // Return the position of bit within the word that contains it (e.g., if
  // bitmap words are 32 bits, return a number 0 <= n <= 31).
  static idx_t bit_in_word(idx_t bit) { return bit & (BitsPerWord - 1); }

  // Return a mask that will select the specified bit, when applied to the word
  // containing the bit.
  static idx_t bit_mask(idx_t bit)    { return (idx_t)1 << bit_in_word(bit); }

  // Return the index of the word containing the specified bit.
  static idx_t word_index(idx_t bit)  { return bit >> LogBitsPerWord; }

  // Return the bit number of the first bit in the specified word.
  static idx_t bit_index(idx_t word)  { return word << LogBitsPerWord; }

  // Return the array of bitmap words, or a specific word from it.
  idx_t* map() const           { return _map; }
  idx_t  map(idx_t word) const { return _map[word]; }

  // Return a pointer to the word containing the specified bit.
  idx_t* word_addr(idx_t bit) const { return map() + word_index(bit); }

  // Set a word to a specified value or to all ones; clear a word.
  void set_word  (idx_t word, idx_t val) { _map[word] = val; }
  void set_word  (idx_t word)            { set_word(word, ~(uintptr_t)0); }
  void clear_word(idx_t word)            { _map[word] = 0; }

  // Utilities for ranges of bits.  Ranges are half-open [beg, end).

  // Ranges within a single word.
  inline idx_t inverted_bit_mask_for_range(idx_t beg, idx_t end) const;
  inline void  set_range_within_word      (idx_t beg, idx_t end);
  inline void  clear_range_within_word    (idx_t beg, idx_t end);
  inline void  par_put_range_within_word  (idx_t beg, idx_t end, bool value);

  // Ranges spanning entire words.
  inline void      set_range_of_words         (idx_t beg, idx_t end);
  inline void      clear_range_of_words       (idx_t beg, idx_t end);
  inline void      set_large_range_of_words   (idx_t beg, idx_t end);
  inline void      clear_large_range_of_words (idx_t beg, idx_t end);

  // The index of the first full word in a range.
  inline idx_t word_index_round_up(idx_t bit) const;

  // Verification, statistics.
  void verify_index(idx_t index) const {
    assert(index < _size, "BitMap index out of bounds");
  }

  void verify_range(idx_t beg_index, idx_t end_index) const {
#ifdef ASSERT
    assert(beg_index <= end_index, "BitMap range error");
    // Note that [0,0) and [size,size) are both valid ranges.
    if (end_index != _size)  verify_index(end_index);
#endif
  }

 public:

  // Constructs a bitmap with no map, and size 0.
  BitMap() : _map(NULL), _size(0) {}

  // Construction
  BitMap(idx_t* map, idx_t size_in_bits);

  // Allocates necessary data structure in resource area
  BitMap(idx_t size_in_bits);

  void set_map(idx_t* map)          { _map = map; }
  void set_size(idx_t size_in_bits) { _size = size_in_bits; }

  // Allocates necessary data structure in resource area.
  // Preserves state currently in bit map by copying data.
  // Zeros any newly-addressable bits.
  // Does not perform any frees (i.e., of current _map).
  void resize(idx_t size_in_bits);

  // Accessing
  idx_t size() const                    { return _size; }
  idx_t size_in_words() const           {
    return word_index(size() + BitsPerWord - 1);
  }

  bool at(idx_t index) const {
    verify_index(index);
    return (*word_addr(index) & bit_mask(index)) != 0;
  }

  // Align bit index up or down to the next bitmap word boundary, or check
  // alignment.
  static idx_t word_align_up(idx_t bit) {
    return align_size_up(bit, BitsPerWord);
  }
  static idx_t word_align_down(idx_t bit) {
    return align_size_down(bit, BitsPerWord);
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
  inline void set_range(idx_t beg, idx_t end, RangeSizeHint hint);
  inline void clear_range(idx_t beg, idx_t end, RangeSizeHint hint);
  inline void par_set_range(idx_t beg, idx_t end, RangeSizeHint hint);
  inline void par_clear_range  (idx_t beg, idx_t end, RangeSizeHint hint);

  // Clearing
  void clear();
  void clear_large();

  // Iteration support
  void iterate(BitMapClosure* blk, idx_t leftIndex, idx_t rightIndex);
  inline void iterate(BitMapClosure* blk) {
    // call the version that takes an interval
    iterate(blk, 0, size());
  }

  // Looking for 1's and 0's to the "right"
  idx_t get_next_one_offset (idx_t l_index, idx_t r_index) const;
  idx_t get_next_zero_offset(idx_t l_index, idx_t r_index) const;

  idx_t get_next_one_offset(idx_t offset) const {
    return get_next_one_offset(offset, size());
  }
  idx_t get_next_zero_offset(idx_t offset) const {
    return get_next_zero_offset(offset, size());
  }



  // Find the next one bit in the range [beg_bit, end_bit), or return end_bit if
  // no one bit is found.  Equivalent to get_next_one_offset(), but inline for
  // use in performance-critical code.
  inline idx_t find_next_one_bit(idx_t beg_bit, idx_t end_bit) const;

  // Set operations.
  void set_union(BitMap bits);
  void set_difference(BitMap bits);
  void set_intersection(BitMap bits);
  // Returns true iff "this" is a superset of "bits".
  bool contains(const BitMap bits) const;
  // Returns true iff "this and "bits" have a non-empty intersection.
  bool intersects(const BitMap bits) const;

  // Returns result of whether this map changed
  // during the operation
  bool set_union_with_result(BitMap bits);
  bool set_difference_with_result(BitMap bits);
  bool set_intersection_with_result(BitMap bits);

  void set_from(BitMap bits);

  bool is_same(BitMap bits);

  // Test if all bits are set or cleared
  bool is_full() const;
  bool is_empty() const;


#ifndef PRODUCT
 public:
  // Printing
  void print_on(outputStream* st) const;
#endif
};

inline void BitMap::set_bit(idx_t bit) {
  verify_index(bit);
  *word_addr(bit) |= bit_mask(bit);
}

inline void BitMap::clear_bit(idx_t bit) {
  verify_index(bit);
  *word_addr(bit) &= ~bit_mask(bit);
}

inline void BitMap::set_range(idx_t beg, idx_t end, RangeSizeHint hint) {
  if (hint == small_range && end - beg == 1) {
    set_bit(beg);
  } else {
    if (hint == large_range) {
      set_large_range(beg, end);
    } else {
      set_range(beg, end);
    }
  }
}

inline void BitMap::clear_range(idx_t beg, idx_t end, RangeSizeHint hint) {
  if (hint == small_range && end - beg == 1) {
    clear_bit(beg);
  } else {
    if (hint == large_range) {
      clear_large_range(beg, end);
    } else {
      clear_range(beg, end);
    }
  }
}

inline void BitMap::par_set_range(idx_t beg, idx_t end, RangeSizeHint hint) {
  if (hint == small_range && end - beg == 1) {
    par_at_put(beg, true);
  } else {
    if (hint == large_range) {
      par_at_put_large_range(beg, end, true);
    } else {
      par_at_put_range(beg, end, true);
    }
  }
}


// Convenience class wrapping BitMap which provides multiple bits per slot.
class BitMap2D VALUE_OBJ_CLASS_SPEC {
 public:
  typedef size_t idx_t;         // Type used for bit and word indices.

 private:
  BitMap _map;
  idx_t  _bits_per_slot;

  idx_t bit_index(idx_t slot_index, idx_t bit_within_slot_index) const {
    return slot_index * _bits_per_slot + bit_within_slot_index;
  }

  void verify_bit_within_slot_index(idx_t index) const {
    assert(index < _bits_per_slot, "bit_within_slot index out of bounds");
  }

 public:
  // Construction. bits_per_slot must be greater than 0.
  BitMap2D(uintptr_t* map, idx_t size_in_slots, idx_t bits_per_slot);

  // Allocates necessary data structure in resource area. bits_per_slot must be greater than 0.
  BitMap2D(idx_t size_in_slots, idx_t bits_per_slot);

  idx_t size_in_bits() {
    return _map.size();
  }

  // Returns number of full slots that have been allocated
  idx_t size_in_slots() {
    // Round down
    return _map.size() / _bits_per_slot;
  }

  bool is_valid_index(idx_t slot_index, idx_t bit_within_slot_index) {
    verify_bit_within_slot_index(bit_within_slot_index);
    return (bit_index(slot_index, bit_within_slot_index) < size_in_bits());
  }

  bool at(idx_t slot_index, idx_t bit_within_slot_index) const {
    verify_bit_within_slot_index(bit_within_slot_index);
    return _map.at(bit_index(slot_index, bit_within_slot_index));
  }

  void set_bit(idx_t slot_index, idx_t bit_within_slot_index) {
    verify_bit_within_slot_index(bit_within_slot_index);
    _map.set_bit(bit_index(slot_index, bit_within_slot_index));
  }

  void clear_bit(idx_t slot_index, idx_t bit_within_slot_index) {
    verify_bit_within_slot_index(bit_within_slot_index);
    _map.clear_bit(bit_index(slot_index, bit_within_slot_index));
  }

  void at_put(idx_t slot_index, idx_t bit_within_slot_index, bool value) {
    verify_bit_within_slot_index(bit_within_slot_index);
    _map.at_put(bit_index(slot_index, bit_within_slot_index), value);
  }

  void at_put_grow(idx_t slot_index, idx_t bit_within_slot_index, bool value) {
    verify_bit_within_slot_index(bit_within_slot_index);
    _map.at_put_grow(bit_index(slot_index, bit_within_slot_index), value);
  }

  void clear() {
    _map.clear();
  }
};



inline void BitMap::set_range_of_words(idx_t beg, idx_t end) {
  uintptr_t* map = _map;
  for (idx_t i = beg; i < end; ++i) map[i] = ~(uintptr_t)0;
}


inline void BitMap::clear_range_of_words(idx_t beg, idx_t end) {
  uintptr_t* map = _map;
  for (idx_t i = beg; i < end; ++i) map[i] = 0;
}


inline void BitMap::clear() {
  clear_range_of_words(0, size_in_words());
}


inline void BitMap::par_clear_range(idx_t beg, idx_t end, RangeSizeHint hint) {
  if (hint == small_range && end - beg == 1) {
    par_at_put(beg, false);
  } else {
    if (hint == large_range) {
      par_at_put_large_range(beg, end, false);
    } else {
      par_at_put_range(beg, end, false);
    }
  }
}

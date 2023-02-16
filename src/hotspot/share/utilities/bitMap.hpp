/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_BITMAP_HPP
#define SHARE_UTILITIES_BITMAP_HPP

#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "utilities/globalDefinitions.hpp"

// Forward decl;
class BitMapClosure;

// Operations for bitmaps represented as arrays of unsigned integers.
// Bits are numbered from 0 to size-1.

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
  typedef uintptr_t bm_word_t;  // Element type of array that represents the
                                // bitmap, with BitsPerWord bits per element.
  // If this were to fail, there are lots of places that would need repair.
  STATIC_ASSERT((sizeof(bm_word_t) * BitsPerByte) == BitsPerWord);

  // Hints for range sizes.
  typedef enum {
    unknown_range, small_range, large_range
  } RangeSizeHint;

 private:
  bm_word_t* _map;     // First word in bitmap
  idx_t      _size;    // Size of bitmap (in bits)

 protected:
  // The maximum allowable size of a bitmap, in words or bits.
  // Limit max_size_in_bits so aligning up to a word boundary never overflows.
  static idx_t max_size_in_words() { return raw_to_words_align_down(~idx_t(0)); }
  static idx_t max_size_in_bits() { return max_size_in_words() * BitsPerWord; }

  // Assumes relevant validity checking for bit has already been done.
  static idx_t raw_to_words_align_up(idx_t bit) {
    return raw_to_words_align_down(bit + (BitsPerWord - 1));
  }

  // Assumes relevant validity checking for bit has already been done.
  static idx_t raw_to_words_align_down(idx_t bit) {
    return bit >> LogBitsPerWord;
  }

  // Converts word-aligned bit it to a word offset.
  // precondition: bit <= size()
  idx_t to_words_aligned(idx_t bit) const {
    verify_limit(bit);
    assert(is_aligned(bit, BitsPerWord), "Incorrect alignment");
    return raw_to_words_align_down(bit);
  }

  // Word-aligns bit and converts it to a word offset.
  // precondition: bit <= size()
  idx_t to_words_align_up(idx_t bit) const {
    verify_limit(bit);
    return raw_to_words_align_up(bit);
  }

  // Word-aligns bit and converts it to a word offset.
  // precondition: bit <= size()
  inline idx_t to_words_align_down(idx_t bit) const {
    verify_limit(bit);
    return raw_to_words_align_down(bit);
  }

  // Helper for get_next_{zero,one}_bit variants.
  // - flip designates whether searching for 1s or 0s.  Must be one of
  //   find_{zeros,ones}_flip.
  // - aligned_right is true if end is a priori on a bm_word_t boundary.
  // - returns end if bit not found
  template<bm_word_t flip, bool aligned_right>
  inline idx_t get_next_bit_impl(idx_t beg, idx_t end) const;

  // Helper for get_prev_{zero,one}_bit variants.
  // - flip designates whether searching for 1s or 0s.  Must be one of
  //   find_{zeros,ones}_flip.
  // - aligned_left is true if beg is a priori on a bm_word_t boundary.
  // - returns idx_t(-1) if bit not found
  template<bm_word_t flip, bool aligned_left>
  inline idx_t get_prev_bit_impl(idx_t beg, idx_t end) const;

  // Values for get_next_bit_impl flip parameter.
  static const bm_word_t find_ones_flip = 0;
  static const bm_word_t find_zeros_flip = ~(bm_word_t)0;

  // Threshold for performing small range operation, even when large range
  // operation was requested. Measured in words.
  static const size_t small_range_words = 32;

  static bool is_small_range_of_words(idx_t beg_full_word, idx_t end_full_word);

  // Return the position of bit within the word that contains it (e.g., if
  // bitmap words are 32 bits, return a number 0 <= n <= 31).
  static idx_t bit_in_word(idx_t bit) { return bit & (BitsPerWord - 1); }

  // Return a mask that will select the specified bit, when applied to the word
  // containing the bit.
  static bm_word_t bit_mask(idx_t bit) { return (bm_word_t)1 << bit_in_word(bit); }

  // Return the bit number of the first bit in the specified word.
  static idx_t bit_index(idx_t word)  { return word << LogBitsPerWord; }

  // Return the array of bitmap words, or a specific word from it.
  bm_word_t* map()                 { return _map; }
  const bm_word_t* map() const     { return _map; }

  bm_word_t word(idx_t word_index, bm_word_t flip) const { return _map[word_index] ^ flip; }

  // Return a pointer to the word containing the specified bit.
  bm_word_t* word_addr(idx_t bit) {
    return map() + to_words_align_down(bit);
  }
  const bm_word_t* word_addr(idx_t bit) const {
    return map() + to_words_align_down(bit);
  }

  // Set a word to a specified value or to all ones; clear a word.
  void set_word  (idx_t word, bm_word_t val) { _map[word] = val; }
  void set_word  (idx_t word)            { set_word(word, ~(bm_word_t)0); }
  void clear_word(idx_t word)            { _map[word] = 0; }

  static inline const bm_word_t load_word_ordered(const volatile bm_word_t* const addr, atomic_memory_order memory_order);

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

  idx_t count_one_bits_within_word(idx_t beg, idx_t end) const;
  idx_t count_one_bits_in_range_of_words(idx_t beg_full_word, idx_t end_full_word) const;

  // Set the map and size.
  void update(bm_word_t* map, idx_t size) {
    _map = map;
    _size = size;
  }

  // Protected constructor and destructor.
  BitMap(bm_word_t* map, idx_t size_in_bits) : _map(map), _size(size_in_bits) {
    verify_size(size_in_bits);
  }
  ~BitMap() {}

 public:
  // Pretouch the entire range of memory this BitMap covers.
  void pretouch();

  // Accessing
  static idx_t calc_size_in_words(size_t size_in_bits) {
    verify_size(size_in_bits);
    return raw_to_words_align_up(size_in_bits);
  }

  idx_t size() const          { return _size; }
  idx_t size_in_words() const { return calc_size_in_words(size()); }
  idx_t size_in_bytes() const { return size_in_words() * BytesPerWord; }

  bool at(idx_t index) const {
    verify_index(index);
    return (*word_addr(index) & bit_mask(index)) != 0;
  }

  // memory_order must be memory_order_relaxed or memory_order_acquire.
  bool par_at(idx_t index, atomic_memory_order memory_order = memory_order_acquire) const;

  // Set or clear the specified bit.
  inline void set_bit(idx_t bit);
  inline void clear_bit(idx_t bit);

  // Attempts to change a bit to a desired value. The operation returns true if
  // this thread changed the value of the bit. It was changed with a RMW operation
  // using the specified memory_order. The operation returns false if the change
  // could not be set due to the bit already being observed in the desired state.
  // The atomic access that observed the bit in the desired state has acquire
  // semantics, unless memory_order is memory_order_relaxed or memory_order_release.
  inline bool par_set_bit(idx_t bit, atomic_memory_order memory_order = memory_order_conservative);
  inline bool par_clear_bit(idx_t bit, atomic_memory_order memory_order = memory_order_conservative);

  // Put the given value at the given index. The parallel version
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

  // Verification.

  // Verify size_in_bits does not exceed max_size_in_bits().
  static void verify_size(idx_t size_in_bits) NOT_DEBUG_RETURN;
  // Verify bit is less than size().
  void verify_index(idx_t bit) const NOT_DEBUG_RETURN;
  // Verify bit is not greater than size().
  void verify_limit(idx_t bit) const NOT_DEBUG_RETURN;
  // Verify [beg,end) is a valid range, e.g. beg <= end <= size().
  void verify_range(idx_t beg, idx_t end) const NOT_DEBUG_RETURN;

  // Applies the function to the index for each set bit, starting from the
  // least index in the range to the greatest, in order. The iteration
  // terminates if the closure returns false.
  //
  // If the function modifies the bitmap, modifications to bits at indices
  // greater than the current index will affect which further indices the
  // function will be applied to.
  //
  // precondition: beg and end form a valid range.
  //  beg - inclusive
  //  end - exclusive
  //
  // Function interface: bool function(idx_t index)
  //  index - visited bit
  //  return false if iterations should terminate early
  //
  // Returns true if the iteration completed, false if terminated early because
  // the function returned false.
  template <typename Function>
  bool iterate(Function function, idx_t beg, idx_t end);

  template <typename Function>
  bool iterate(Function function) {
    return iterate(function, 0, _size);
  }

  template <typename BitMapClosureType>
  bool iterate(BitMapClosureType* cl, idx_t beg, idx_t end);

  template <typename BitMapClosureType>
  bool iterate(BitMapClosureType* cl) {
    return iterate(cl, 0, _size);
  }

  // Reverse version of "iterate".
  //  beg - inclusive
  //  end - exclusive
  //  beg <= end
  template <typename Function>
  bool iterate_reverse(Function function, idx_t beg, idx_t end);

  template <typename Function>
  bool iterate_reverse(Function function) {
    return iterate_reverse(function, 0, _size);
  }

  template <typename BitMapClosureType>
  bool iterate_reverse(BitMapClosureType* cl, idx_t beg, idx_t end);

  template <typename BitMapClosureType>
  bool iterate_reverse(BitMapClosureType* cl) {
    return iterate_reverse(cl, 0, _size);
  }

  // Looking for 1's and 0's at indices equal to or greater than "beg",
  // stopping if none has been found before "end", and returning
  // "end" (which must be at most "size") in that case.
  idx_t get_next_one_offset (idx_t beg, idx_t end) const;
  idx_t get_next_zero_offset(idx_t beg, idx_t end) const;

  idx_t get_next_one_offset(idx_t offset) const {
    return get_next_one_offset(offset, size());
  }
  idx_t get_next_zero_offset(idx_t offset) const {
    return get_next_zero_offset(offset, size());
  }

  // Like "get_next_one_offset", except requires that "end" is
  // aligned to bitsizeof(bm_word_t).
  idx_t get_next_one_offset_aligned_right(idx_t beg, idx_t end) const;

  // Looking for 1's and 0's at indices lower than "end",
  // stopping if none has been found before or at "beg", and returning
  // idx_t(-1) in that case.
  idx_t get_prev_one_offset (idx_t beg, idx_t end) const;
  idx_t get_prev_zero_offset(idx_t beg, idx_t end) const;

  idx_t get_prev_one_offset(idx_t offset) const {
    return get_prev_one_offset(0, offset);
  }
  idx_t get_prev_zero_offset(idx_t offset) const {
    return get_prev_zero_offset(0, offset);
  }

  // Like "get_prev_one_offset", except requires that "beg" is
  // aligned to bitsizeof(bm_word_t).
  idx_t get_prev_one_offset_aligned_left(idx_t beg, idx_t end) const;

  // Returns the number of bits set in the bitmap.
  idx_t count_one_bits() const;

  // Returns the number of bits set within  [beg, end).
  idx_t count_one_bits(idx_t beg, idx_t end) const;

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

  void write_to(bm_word_t* buffer, size_t buffer_size_in_bytes) const;
  void print_on_error(outputStream* st, const char* prefix) const;

#ifndef PRODUCT
 public:
  // Printing
  void print_on(outputStream* st) const;
#endif
};

// CRTP: BitmapWithAllocator exposes the following Allocator interfaces upward to GrowableBitMap.
//
//  bm_word_t* allocate(idx_t size_in_words) const;
//  void free(bm_word_t* map, idx_t size_in_words) const
//
template <class BitMapWithAllocator>
class GrowableBitMap : public BitMap {
 protected:
  GrowableBitMap() : GrowableBitMap(nullptr, 0) {}
  GrowableBitMap(bm_word_t* map, idx_t size_in_bits) : BitMap(map, size_in_bits) {}

 public:
  // Set up and optionally clear the bitmap memory.
  //
  // Precondition: The bitmap was default constructed and has
  // not yet had memory allocated via resize or (re)initialize.
  void initialize(idx_t size_in_bits, bool clear = true);

  // Set up and optionally clear the bitmap memory.
  //
  // Can be called on previously initialized bitmaps.
  void reinitialize(idx_t new_size_in_bits, bool clear = true);

  // Protected functions, that are used by BitMap sub-classes that support them.

  // Resize the backing bitmap memory.
  //
  // Old bits are transferred to the new memory
  // and the extended memory is optionally cleared.
  void resize(idx_t new_size_in_bits, bool clear = true);
};

// A concrete implementation of the "abstract" BitMap class.
//
// The BitMapView is used when the backing storage is managed externally.
class BitMapView : public BitMap {
 public:
  BitMapView() : BitMapView(nullptr, 0) {}
  BitMapView(bm_word_t* map, idx_t size_in_bits) : BitMap(map, size_in_bits) {}
};

// A BitMap with storage in a specific Arena.
class ArenaBitMap : public GrowableBitMap<ArenaBitMap> {
  Arena* const _arena;

  NONCOPYABLE(ArenaBitMap);

 public:
  ArenaBitMap(Arena* arena, idx_t size_in_bits, bool clear = true);

  bm_word_t* allocate(idx_t size_in_words) const;
  bm_word_t* reallocate(bm_word_t* old_map, size_t old_size_in_words, size_t new_size_in_words) const;
  void free(bm_word_t* map, idx_t size_in_words) const {
    // ArenaBitMaps don't free memory.
  }
};

// A BitMap with storage in the current threads resource area.
class ResourceBitMap : public GrowableBitMap<ResourceBitMap> {
 public:
  ResourceBitMap() : ResourceBitMap(0) {}
  explicit ResourceBitMap(idx_t size_in_bits, bool clear = true);

  bm_word_t* allocate(idx_t size_in_words) const;
  bm_word_t* reallocate(bm_word_t* old_map, size_t old_size_in_words, size_t new_size_in_words) const;
  void free(bm_word_t* map, idx_t size_in_words) const {
    // ResourceBitMaps don't free memory.
  }
};

// A BitMap with storage in the CHeap.
class CHeapBitMap : public GrowableBitMap<CHeapBitMap> {
  // NMT memory type
  const MEMFLAGS _flags;

  // Don't allow copy or assignment, to prevent the
  // allocated memory from leaking out to other instances.
  NONCOPYABLE(CHeapBitMap);

 public:
  explicit CHeapBitMap(MEMFLAGS flags) : GrowableBitMap(0, false), _flags(flags) {}
  CHeapBitMap(idx_t size_in_bits, MEMFLAGS flags, bool clear = true);
  ~CHeapBitMap();

  bm_word_t* allocate(idx_t size_in_words) const;
  bm_word_t* reallocate(bm_word_t* old_map, size_t old_size_in_words, size_t new_size_in_words) const;
  void free(bm_word_t* map, idx_t size_in_words) const;
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
  virtual bool do_bit(BitMap::idx_t index) = 0;
};

// Stand-alone iterators

// Forward iterator.
//
// Iterates over each set bit, starting from the
// least index in the range to the greatest, in order.
class BitMapIterator {
private:
  BitMap* const _bitmap;
  BitMap::idx_t _pos;
  BitMap::idx_t _end;

public:
  // Iterate over the entire bitmap.
  explicit BitMapIterator(BitMap* bitmap);

  // Iterator for a given range of the bitmap.
  //
  // precondition: beg and end form a valid range.
  //  beg <= end
  //  beg - inclusive
  //  end - exclusive
  BitMapIterator(BitMap* bitmap, BitMap::idx_t beg, BitMap::idx_t end);

  // Search for the next bit and fill in the index of that bit.
  //
  // Returns false iff when there's no more set bits in the range.
  bool next(BitMap::idx_t* index);
};

// Reverse iterator.
//
// Iterates over each set bit, starting from the
// greatest index in the range to the least, in order.
class BitMapReverseIterator {
private:
  BitMap* const _bitmap;
  BitMap::idx_t _beg;
  BitMap::idx_t _pos;

public:
  // Iterate over the entire bitmap.
  explicit BitMapReverseIterator(BitMap* bitmap);

  // Iterator for a given range of the bitmap.
  //
  // precondition: beg and end form a valid range.
  //  beg <= end
  //  beg - inclusive
  //  end - exclusive
  BitMapReverseIterator(BitMap* bitmap, BitMap::idx_t beg, BitMap::idx_t end);

  void reset(BitMap::idx_t start, BitMap::idx_t end);
  void reset(BitMap::idx_t end);

  // Search for the next bit and fill in the index of that bit.
  //
  // Returns false iff when there's no more set bits in the range.
  bool next(BitMap::idx_t* index);
};

#endif // SHARE_UTILITIES_BITMAP_HPP

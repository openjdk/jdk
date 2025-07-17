/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/copy.hpp"
#include "utilities/debug.hpp"
#include "utilities/population_count.hpp"

using bm_word_t = BitMap::bm_word_t;
using idx_t = BitMap::idx_t;

STATIC_ASSERT(sizeof(bm_word_t) == BytesPerWord); // "Implementation assumption."

// For the BitMaps with allocators that don't support reallocate
template <class BitMapWithAllocator>
static bm_word_t* pseudo_reallocate(const BitMapWithAllocator& derived, bm_word_t* old_map, size_t old_size_in_words, size_t new_size_in_words) {
  assert(new_size_in_words > 0, "precondition");

  bm_word_t* map = derived.allocate(new_size_in_words);
  if (old_map != nullptr) {
    Copy::disjoint_words((HeapWord*)old_map, (HeapWord*) map,
        MIN2(old_size_in_words, new_size_in_words));
  }

  derived.free(old_map, old_size_in_words);

  return map;
}

template <class BitMapWithAllocator>
void GrowableBitMap<BitMapWithAllocator>::initialize(idx_t size_in_bits, bool clear) {
  assert(map() == nullptr, "precondition");
  assert(size() == 0,   "precondition");

  resize(size_in_bits, clear);
}

template <class BitMapWithAllocator>
void GrowableBitMap<BitMapWithAllocator>::reinitialize(idx_t new_size_in_bits, bool clear) {
  // Remove previous bits - no need to clear
  resize(0, false /* clear */);

  initialize(new_size_in_bits, clear);
}

template <class BitMapWithAllocator>
void GrowableBitMap<BitMapWithAllocator>::resize(idx_t new_size_in_bits, bool clear) {
  const size_t old_size_in_bits = size();
  bm_word_t* const old_map = map();

  const size_t old_size_in_words = calc_size_in_words(size());
  const size_t new_size_in_words = calc_size_in_words(new_size_in_bits);

  BitMapWithAllocator* derived = static_cast<BitMapWithAllocator*>(this);

  if (new_size_in_words == 0) {
    derived->free(old_map, old_size_in_words);
    update(nullptr, 0);
    return;
  }


  bm_word_t* map = derived->reallocate(old_map, old_size_in_words, new_size_in_words);
  if (clear && (new_size_in_bits > old_size_in_bits)) {
    // If old_size_in_bits is not word-aligned, then the preceding
    // copy can include some trailing bits in the final copied word
    // that also need to be cleared.  See clear_range_within_word.
    bm_word_t mask = bit_mask(old_size_in_bits) - 1;
    map[raw_to_words_align_down(old_size_in_bits)] &= mask;
    // Clear the remaining full words.
    clear_range_of_words(map, old_size_in_words, new_size_in_words);
  }

  update(map, new_size_in_bits);
}

template <class BitMapWithAllocator>
bm_word_t* GrowableBitMap<BitMapWithAllocator>::copy_of_range(idx_t start_bit, idx_t end_bit) {
  assert(start_bit < end_bit, "End bit must come after start bit");
  assert(end_bit <= size(), "End bit not in bitmap");

  // We might have extra bits at the end that we don't want to lose
  const idx_t cutoff = bit_in_word(end_bit);
  const idx_t start_word = to_words_align_down(start_bit);
  const idx_t end_word = to_words_align_up(end_bit);
  const bm_word_t* const old_map = map();

  const BitMapWithAllocator* const derived = static_cast<BitMapWithAllocator*>(this);

  bm_word_t* const new_map = derived->allocate(end_word - start_word);

  // All words need to be shifted by this amount
  const idx_t shift = bit_in_word(start_bit);
  // Bits shifted out by a word need to be passed into the next
  bm_word_t carry = 0;

  // Iterate the map backwards as the shift will result in carry-out bits
  for (idx_t i = end_word; i-- > start_word;) {
    new_map[i-start_word] = old_map[i] >> shift;

    if (shift != 0) {
      new_map[i-start_word] |= carry;
      carry = old_map[i] << (BitsPerWord - shift);
    }
  }

  return new_map;
}

template <class BitMapWithAllocator>
void GrowableBitMap<BitMapWithAllocator>::truncate(idx_t start_bit, idx_t end_bit) {
  const size_t old_size_in_words = calc_size_in_words(size());
  const idx_t new_size_in_bits = end_bit - start_bit;
  bm_word_t* const old_map = map();

  bm_word_t* const new_map = copy_of_range(start_bit, end_bit);

  const BitMapWithAllocator* const derived = static_cast<BitMapWithAllocator*>(this);
  // Free and clear old map to avoid left over bits
  derived->free(old_map, old_size_in_words);
  update(nullptr, 0);
  update(new_map, new_size_in_bits);
}

ArenaBitMap::ArenaBitMap(Arena* arena, idx_t size_in_bits, bool clear)
  : GrowableBitMap<ArenaBitMap>(), _arena(arena) {
  initialize(size_in_bits, clear);
}

bm_word_t* ArenaBitMap::allocate(idx_t size_in_words) const {
  return (bm_word_t*)_arena->Amalloc(size_in_words * BytesPerWord);
}

bm_word_t* ArenaBitMap::reallocate(bm_word_t* old_map, size_t old_size_in_words, size_t new_size_in_words) const {
  return pseudo_reallocate(*this, old_map, old_size_in_words, new_size_in_words);
}

ResourceBitMap::ResourceBitMap(idx_t size_in_bits, bool clear)
  : GrowableBitMap<ResourceBitMap>() {
  initialize(size_in_bits, clear);
}

bm_word_t* ResourceBitMap::allocate(idx_t size_in_words) const {
  return (bm_word_t*)NEW_RESOURCE_ARRAY(bm_word_t, size_in_words);
}

bm_word_t* ResourceBitMap::reallocate(bm_word_t* old_map, size_t old_size_in_words, size_t new_size_in_words) const {
  return pseudo_reallocate(*this, old_map, old_size_in_words, new_size_in_words);
}

CHeapBitMap::CHeapBitMap(idx_t size_in_bits, MemTag mem_tag, bool clear)
  : GrowableBitMap<CHeapBitMap>(), _mem_tag(mem_tag) {
  initialize(size_in_bits, clear);
}

CHeapBitMap::~CHeapBitMap() {
  free(map(), size_in_words());
}

bm_word_t* CHeapBitMap::allocate(idx_t size_in_words) const {
  return MallocArrayAllocator<bm_word_t>::allocate(size_in_words, _mem_tag);
}

// GrowableBitMap<T>::resize uses free(ptr, size) for T as CHeapBitMap, ArenaBitMap and ResourceBitMap allocators.
// The free(ptr, size) signature is kept but the size parameter is ignored.
void CHeapBitMap::free(bm_word_t* map, idx_t size_in_words) const {
  MallocArrayAllocator<bm_word_t>::free(map);
}

bm_word_t* CHeapBitMap::reallocate(bm_word_t* map, size_t old_size_in_words, size_t new_size_in_words) const {
  return MallocArrayAllocator<bm_word_t>::reallocate(map, new_size_in_words, _mem_tag);
}

#ifdef ASSERT
void BitMap::verify_index(idx_t bit) const {
  assert(bit < _size,
         "BitMap index out of bounds: %zu >= %zu",
         bit, _size);
}

void BitMap::verify_limit(idx_t bit) const {
  assert(bit <= _size,
         "BitMap limit out of bounds: %zu > %zu",
         bit, _size);
}

void BitMap::verify_range(idx_t beg, idx_t end) const {
  assert(beg <= end,
         "BitMap range error: %zu > %zu", beg, end);
  verify_limit(end);
}
#endif // #ifdef ASSERT

void BitMap::pretouch() {
  os::pretouch_memory(word_addr(0), word_addr(size()));
}

void BitMap::set_range_within_word(idx_t beg, idx_t end) {
  // With a valid range (beg <= end), this test ensures that end != 0, as
  // required by inverted_bit_mask_for_range.  Also avoids an unnecessary write.
  if (beg != end) {
    bm_word_t mask = inverted_bit_mask_for_range(beg, end);
    *word_addr(beg) |= ~mask;
  }
}

void BitMap::clear_range_within_word(idx_t beg, idx_t end) {
  // With a valid range (beg <= end), this test ensures that end != 0, as
  // required by inverted_bit_mask_for_range.  Also avoids an unnecessary write.
  if (beg != end) {
    bm_word_t mask = inverted_bit_mask_for_range(beg, end);
    *word_addr(beg) &= mask;
  }
}

void BitMap::par_put_range_within_word(idx_t beg, idx_t end, bool value) {
  assert(value == 0 || value == 1, "0 for clear, 1 for set");
  // With a valid range (beg <= end), this test ensures that end != 0, as
  // required by inverted_bit_mask_for_range.  Also avoids an unnecessary write.
  if (beg != end) {
    volatile bm_word_t* pw = word_addr(beg);
    bm_word_t w = Atomic::load(pw);
    bm_word_t mr = inverted_bit_mask_for_range(beg, end);
    bm_word_t nw = value ? (w | ~mr) : (w & mr);
    while (true) {
      bm_word_t res = Atomic::cmpxchg(pw, w, nw);
      if (res == w) break;
      w  = res;
      nw = value ? (w | ~mr) : (w & mr);
    }
  }
}

void BitMap::set_range(idx_t beg, idx_t end) {
  verify_range(beg, end);

  idx_t beg_full_word = to_words_align_up(beg);
  idx_t end_full_word = to_words_align_down(end);

  if (beg_full_word < end_full_word) {
    // The range includes at least one full word.
    set_range_within_word(beg, bit_index(beg_full_word));
    set_range_of_words(beg_full_word, end_full_word);
    set_range_within_word(bit_index(end_full_word), end);
  } else {
    // The range spans at most 2 partial words.
    idx_t boundary = MIN2(bit_index(beg_full_word), end);
    set_range_within_word(beg, boundary);
    set_range_within_word(boundary, end);
  }
}

void BitMap::clear_range(idx_t beg, idx_t end) {
  verify_range(beg, end);

  idx_t beg_full_word = to_words_align_up(beg);
  idx_t end_full_word = to_words_align_down(end);

  if (beg_full_word < end_full_word) {
    // The range includes at least one full word.
    clear_range_within_word(beg, bit_index(beg_full_word));
    clear_range_of_words(beg_full_word, end_full_word);
    clear_range_within_word(bit_index(end_full_word), end);
  } else {
    // The range spans at most 2 partial words.
    idx_t boundary = MIN2(bit_index(beg_full_word), end);
    clear_range_within_word(beg, boundary);
    clear_range_within_word(boundary, end);
  }
}

bool BitMap::is_small_range_of_words(idx_t beg_full_word, idx_t end_full_word) {
  // There is little point to call large version on small ranges.
  // Need to check carefully, keeping potential idx_t over/underflow in mind,
  // because beg_full_word > end_full_word can occur when beg and end are in
  // the same word.
  // The threshold should be at least one word.
  STATIC_ASSERT(small_range_words >= 1);
  return beg_full_word + small_range_words >= end_full_word;
}

void BitMap::set_large_range(idx_t beg, idx_t end) {
  verify_range(beg, end);

  idx_t beg_full_word = to_words_align_up(beg);
  idx_t end_full_word = to_words_align_down(end);

  if (is_small_range_of_words(beg_full_word, end_full_word)) {
    set_range(beg, end);
    return;
  }

  // The range includes at least one full word.
  set_range_within_word(beg, bit_index(beg_full_word));
  set_large_range_of_words(beg_full_word, end_full_word);
  set_range_within_word(bit_index(end_full_word), end);
}

void BitMap::clear_large_range(idx_t beg, idx_t end) {
  verify_range(beg, end);

  idx_t beg_full_word = to_words_align_up(beg);
  idx_t end_full_word = to_words_align_down(end);

  if (is_small_range_of_words(beg_full_word, end_full_word)) {
    clear_range(beg, end);
    return;
  }

  // The range includes at least one full word.
  clear_range_within_word(beg, bit_index(beg_full_word));
  clear_large_range_of_words(beg_full_word, end_full_word);
  clear_range_within_word(bit_index(end_full_word), end);
}

void BitMap::at_put(idx_t bit, bool value) {
  if (value) {
    set_bit(bit);
  } else {
    clear_bit(bit);
  }
}

// Return true to indicate that this thread changed
// the bit, false to indicate that someone else did.
// In either case, the requested bit is in the
// requested state some time during the period that
// this thread is executing this call. More importantly,
// if no other thread is executing an action to
// change the requested bit to a state other than
// the one that this thread is trying to set it to,
// then the bit is in the expected state
// at exit from this method. However, rather than
// make such a strong assertion here, based on
// assuming such constrained use (which though true
// today, could change in the future to service some
// funky parallel algorithm), we encourage callers
// to do such verification, as and when appropriate.
bool BitMap::par_at_put(idx_t bit, bool value) {
  return value ? par_set_bit(bit) : par_clear_bit(bit);
}

void BitMap::at_put_range(idx_t beg, idx_t end, bool value) {
  if (value) {
    set_range(beg, end);
  } else {
    clear_range(beg, end);
  }
}

void BitMap::par_at_put_range(idx_t beg, idx_t end, bool value) {
  verify_range(beg, end);

  idx_t beg_full_word = to_words_align_up(beg);
  idx_t end_full_word = to_words_align_down(end);

  if (beg_full_word < end_full_word) {
    // The range includes at least one full word.
    par_put_range_within_word(beg, bit_index(beg_full_word), value);
    if (value) {
      set_range_of_words(beg_full_word, end_full_word);
    } else {
      clear_range_of_words(beg_full_word, end_full_word);
    }
    par_put_range_within_word(bit_index(end_full_word), end, value);
  } else {
    // The range spans at most 2 partial words.
    idx_t boundary = MIN2(bit_index(beg_full_word), end);
    par_put_range_within_word(beg, boundary, value);
    par_put_range_within_word(boundary, end, value);
  }

}

void BitMap::at_put_large_range(idx_t beg, idx_t end, bool value) {
  if (value) {
    set_large_range(beg, end);
  } else {
    clear_large_range(beg, end);
  }
}

void BitMap::par_at_put_large_range(idx_t beg, idx_t end, bool value) {
  verify_range(beg, end);

  idx_t beg_full_word = to_words_align_up(beg);
  idx_t end_full_word = to_words_align_down(end);

  if (is_small_range_of_words(beg_full_word, end_full_word)) {
    par_at_put_range(beg, end, value);
    return;
  }

  // The range includes at least one full word.
  par_put_range_within_word(beg, bit_index(beg_full_word), value);
  if (value) {
    set_large_range_of_words(beg_full_word, end_full_word);
  } else {
    clear_large_range_of_words(beg_full_word, end_full_word);
  }
  par_put_range_within_word(bit_index(end_full_word), end, value);
}

inline bm_word_t tail_mask(idx_t tail_bits) {
  assert(tail_bits != 0, "precondition"); // Works, but shouldn't be called.
  assert(tail_bits < (idx_t)BitsPerWord, "precondition");
  return (bm_word_t(1) << tail_bits) - 1;
}

// Get the low tail_bits of value, which is the last partial word of a map.
inline bm_word_t tail_of_map(bm_word_t value, idx_t tail_bits) {
  return value & tail_mask(tail_bits);
}

// Compute the new last word of a map with a non-aligned length.
// new_value has the new trailing bits of the map in the low tail_bits.
// old_value is the last word of the map, including bits beyond the end.
// Returns old_value with the low tail_bits replaced by the corresponding
// bits in new_value.
inline bm_word_t merge_tail_of_map(bm_word_t new_value,
                                   bm_word_t old_value,
                                   idx_t tail_bits) {
  bm_word_t mask = tail_mask(tail_bits);
  return (new_value & mask) | (old_value & ~mask);
}

bool BitMap::contains(const BitMap& other) const {
  assert(size() == other.size(), "must have same size");
  const bm_word_t* dest_map = map();
  const bm_word_t* other_map = other.map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    // false if other bitmap has bits set which are clear in this bitmap.
    if ((~dest_map[index] & other_map[index]) != 0) return false;
  }
  idx_t rest = bit_in_word(size());
  // true unless there is a partial-word tail in which the other
  // bitmap has bits set which are clear in this bitmap.
  return (rest == 0) || tail_of_map(~dest_map[limit] & other_map[limit], rest) == 0;
}

bool BitMap::intersects(const BitMap& other) const {
  assert(size() == other.size(), "must have same size");
  const bm_word_t* dest_map = map();
  const bm_word_t* other_map = other.map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    if ((dest_map[index] & other_map[index]) != 0) return true;
  }
  idx_t rest = bit_in_word(size());
  // false unless there is a partial-word tail with non-empty intersection.
  return (rest > 0) && tail_of_map(dest_map[limit] & other_map[limit], rest) != 0;
}

void BitMap::set_union(const BitMap& other) {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  const bm_word_t* other_map = other.map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    dest_map[index] |= other_map[index];
  }
  idx_t rest = bit_in_word(size());
  if (rest > 0) {
    bm_word_t orig = dest_map[limit];
    dest_map[limit] = merge_tail_of_map(orig | other_map[limit], orig, rest);
  }
}

void BitMap::set_difference(const BitMap& other) {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  const bm_word_t* other_map = other.map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    dest_map[index] &= ~other_map[index];
  }
  idx_t rest = bit_in_word(size());
  if (rest > 0) {
    bm_word_t orig = dest_map[limit];
    dest_map[limit] = merge_tail_of_map(orig & ~other_map[limit], orig, rest);
  }
}

void BitMap::set_intersection(const BitMap& other) {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  const bm_word_t* other_map = other.map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    dest_map[index] &= other_map[index];
  }
  idx_t rest = bit_in_word(size());
  if (rest > 0) {
    bm_word_t orig = dest_map[limit];
    dest_map[limit] = merge_tail_of_map(orig & other_map[limit], orig, rest);
  }
}

bool BitMap::set_union_with_result(const BitMap& other) {
  assert(size() == other.size(), "must have same size");
  bool changed = false;
  bm_word_t* dest_map = map();
  const bm_word_t* other_map = other.map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    bm_word_t orig = dest_map[index];
    bm_word_t temp = orig | other_map[index];
    changed = changed || (temp != orig);
    dest_map[index] = temp;
  }
  idx_t rest = bit_in_word(size());
  if (rest > 0) {
    bm_word_t orig = dest_map[limit];
    bm_word_t temp = merge_tail_of_map(orig | other_map[limit], orig, rest);
    changed = changed || (temp != orig);
    dest_map[limit] = temp;
  }
  return changed;
}

bool BitMap::set_difference_with_result(const BitMap& other) {
  assert(size() == other.size(), "must have same size");
  bool changed = false;
  bm_word_t* dest_map = map();
  const bm_word_t* other_map = other.map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    bm_word_t orig = dest_map[index];
    bm_word_t temp = orig & ~other_map[index];
    changed = changed || (temp != orig);
    dest_map[index] = temp;
  }
  idx_t rest = bit_in_word(size());
  if (rest > 0) {
    bm_word_t orig = dest_map[limit];
    bm_word_t temp = merge_tail_of_map(orig & ~other_map[limit], orig, rest);
    changed = changed || (temp != orig);
    dest_map[limit] = temp;
  }
  return changed;
}

bool BitMap::set_intersection_with_result(const BitMap& other) {
  assert(size() == other.size(), "must have same size");
  bool changed = false;
  bm_word_t* dest_map = map();
  const bm_word_t* other_map = other.map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    bm_word_t orig = dest_map[index];
    bm_word_t temp = orig & other_map[index];
    changed = changed || (temp != orig);
    dest_map[index] = temp;
  }
  idx_t rest = bit_in_word(size());
  if (rest > 0) {
    bm_word_t orig = dest_map[limit];
    bm_word_t temp = merge_tail_of_map(orig & other_map[limit], orig, rest);
    changed = changed || (temp != orig);
    dest_map[limit] = temp;
  }
  return changed;
}

void BitMap::set_from(const BitMap& other) {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  const bm_word_t* other_map = other.map();
  idx_t copy_words = to_words_align_down(size());
  Copy::disjoint_words((HeapWord*)other_map, (HeapWord*)dest_map, copy_words);
  idx_t rest = bit_in_word(size());
  if (rest > 0) {
    dest_map[copy_words] = merge_tail_of_map(other_map[copy_words],
                                             dest_map[copy_words],
                                             rest);
  }
}

bool BitMap::is_same(const BitMap& other) const {
  assert(size() == other.size(), "must have same size");
  const bm_word_t* dest_map = map();
  const bm_word_t* other_map = other.map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    if (dest_map[index] != other_map[index]) return false;
  }
  idx_t rest = bit_in_word(size());
  return (rest == 0) || (tail_of_map(dest_map[limit] ^ other_map[limit], rest) == 0);
}

bool BitMap::is_full() const {
  const bm_word_t* words = map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    if (~words[index] != 0) return false;
  }
  idx_t rest = bit_in_word(size());
  return (rest == 0) || (tail_of_map(~words[limit], rest) == 0);
}

bool BitMap::is_empty() const {
  const bm_word_t* words = map();
  idx_t limit = to_words_align_down(size());
  for (idx_t index = 0; index < limit; ++index) {
    if (words[index] != 0) return false;
  }
  idx_t rest = bit_in_word(size());
  return (rest == 0) || (tail_of_map(words[limit], rest) == 0);
}

void BitMap::clear_large() {
  clear_large_range_of_words(0, size_in_words());
}

BitMap::idx_t BitMap::count_one_bits_in_range_of_words(idx_t beg_full_word, idx_t end_full_word) const {
  idx_t sum = 0;
  for (idx_t i = beg_full_word; i < end_full_word; i++) {
    bm_word_t w = map()[i];
    sum += population_count(w);
  }
  return sum;
}

BitMap::idx_t BitMap::count_one_bits_within_word(idx_t beg, idx_t end) const {
  if (beg != end) {
    assert(end > beg, "must be");
    bm_word_t mask = ~inverted_bit_mask_for_range(beg, end);
    bm_word_t w = *word_addr(beg);
    w &= mask;
    return population_count(w);
  }
  return 0;
}

BitMap::idx_t BitMap::count_one_bits() const {
  return count_one_bits(0, size());
}

// Returns the number of bits set within  [beg, end).
BitMap::idx_t BitMap::count_one_bits(idx_t beg, idx_t end) const {
  verify_range(beg, end);

  idx_t beg_full_word = to_words_align_up(beg);
  idx_t end_full_word = to_words_align_down(end);

  idx_t sum = 0;

  if (beg_full_word < end_full_word) {
    // The range includes at least one full word.
    sum += count_one_bits_within_word(beg, bit_index(beg_full_word));
    sum += count_one_bits_in_range_of_words(beg_full_word, end_full_word);
    sum += count_one_bits_within_word(bit_index(end_full_word), end);
  } else {
    // The range spans at most 2 partial words.
    idx_t boundary = MIN2(bit_index(beg_full_word), end);
    sum += count_one_bits_within_word(beg, boundary);
    sum += count_one_bits_within_word(boundary, end);
  }

  assert(sum <= (end - beg), "must be");

  return sum;

}

void BitMap::print_range_on(outputStream* st, const char* prefix) const {
  st->print_cr("%s[" PTR_FORMAT ", " PTR_FORMAT ")",
      prefix, p2i(map()), p2i((char*)map() + (size() >> LogBitsPerByte)));
}

void BitMap::write_to(bm_word_t* buffer, size_t buffer_size_in_bytes) const {
  assert(buffer_size_in_bytes == size_in_bytes(), "must be");
  memcpy(buffer, _map, size_in_bytes());
}

#ifdef ASSERT
void BitMap::IteratorImpl::assert_not_empty() const {
  assert(!is_empty(), "empty iterator");
}
#endif

void BitMap::print_on(outputStream* st) const {
  st->print("Bitmap (%zu bits):", size());
  for (idx_t index = 0; index < size(); index++) {
    if ((index % 64) == 0) {
      st->cr();
      st->print("%5zu:", index);
    }
    if ((index % 8) == 0) {
      st->print(" ");
    }
    st->print("%c", at(index) ? 'S' : '.');
  }
  st->cr();
}

template class GrowableBitMap<ArenaBitMap>;
template class GrowableBitMap<ResourceBitMap>;
template class GrowableBitMap<CHeapBitMap>;

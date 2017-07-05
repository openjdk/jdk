/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.inline.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/copy.hpp"

STATIC_ASSERT(sizeof(BitMap::bm_word_t) == BytesPerWord); // "Implementation assumption."

BitMap::BitMap(idx_t size_in_bits, bool in_resource_area) :
  _map(NULL), _size(0)
{
  resize(size_in_bits, in_resource_area);
}

#ifdef ASSERT
void BitMap::verify_index(idx_t index) const {
  assert(index < _size, "BitMap index out of bounds");
}

void BitMap::verify_range(idx_t beg_index, idx_t end_index) const {
  assert(beg_index <= end_index, "BitMap range error");
  // Note that [0,0) and [size,size) are both valid ranges.
  if (end_index != _size) verify_index(end_index);
}
#endif // #ifdef ASSERT

void BitMap::resize(idx_t size_in_bits, bool in_resource_area) {
  idx_t old_size_in_words = size_in_words();
  bm_word_t* old_map = map();

  _size = size_in_bits;
  idx_t new_size_in_words = size_in_words();
  if (in_resource_area) {
    _map = NEW_RESOURCE_ARRAY(bm_word_t, new_size_in_words);
    Copy::disjoint_words((HeapWord*)old_map, (HeapWord*) _map,
                         MIN2(old_size_in_words, new_size_in_words));
  } else {
    _map = ArrayAllocator<bm_word_t, mtInternal>::reallocate(old_map, old_size_in_words, new_size_in_words);
  }

  if (new_size_in_words > old_size_in_words) {
    clear_range_of_words(old_size_in_words, new_size_in_words);
  }
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
    intptr_t* pw  = (intptr_t*)word_addr(beg);
    intptr_t  w   = *pw;
    intptr_t  mr  = (intptr_t)inverted_bit_mask_for_range(beg, end);
    intptr_t  nw  = value ? (w | ~mr) : (w & mr);
    while (true) {
      intptr_t res = Atomic::cmpxchg_ptr(nw, pw, w);
      if (res == w) break;
      w  = res;
      nw = value ? (w | ~mr) : (w & mr);
    }
  }
}

void BitMap::set_range(idx_t beg, idx_t end) {
  verify_range(beg, end);

  idx_t beg_full_word = word_index_round_up(beg);
  idx_t end_full_word = word_index(end);

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

  idx_t beg_full_word = word_index_round_up(beg);
  idx_t end_full_word = word_index(end);

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

void BitMap::set_large_range(idx_t beg, idx_t end) {
  verify_range(beg, end);

  idx_t beg_full_word = word_index_round_up(beg);
  idx_t end_full_word = word_index(end);

  assert(end_full_word - beg_full_word >= 32,
         "the range must include at least 32 bytes");

  // The range includes at least one full word.
  set_range_within_word(beg, bit_index(beg_full_word));
  set_large_range_of_words(beg_full_word, end_full_word);
  set_range_within_word(bit_index(end_full_word), end);
}

void BitMap::clear_large_range(idx_t beg, idx_t end) {
  verify_range(beg, end);

  idx_t beg_full_word = word_index_round_up(beg);
  idx_t end_full_word = word_index(end);

  if (end_full_word - beg_full_word < 32) {
    clear_range(beg, end);
    return;
  }

  // The range includes at least one full word.
  clear_range_within_word(beg, bit_index(beg_full_word));
  clear_large_range_of_words(beg_full_word, end_full_word);
  clear_range_within_word(bit_index(end_full_word), end);
}

void BitMap::at_put(idx_t offset, bool value) {
  if (value) {
    set_bit(offset);
  } else {
    clear_bit(offset);
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
// then the the bit is in the expected state
// at exit from this method. However, rather than
// make such a strong assertion here, based on
// assuming such constrained use (which though true
// today, could change in the future to service some
// funky parallel algorithm), we encourage callers
// to do such verification, as and when appropriate.
bool BitMap::par_at_put(idx_t bit, bool value) {
  return value ? par_set_bit(bit) : par_clear_bit(bit);
}

void BitMap::at_put_grow(idx_t offset, bool value) {
  if (offset >= size()) {
    resize(2 * MAX2(size(), offset));
  }
  at_put(offset, value);
}

void BitMap::at_put_range(idx_t start_offset, idx_t end_offset, bool value) {
  if (value) {
    set_range(start_offset, end_offset);
  } else {
    clear_range(start_offset, end_offset);
  }
}

void BitMap::par_at_put_range(idx_t beg, idx_t end, bool value) {
  verify_range(beg, end);

  idx_t beg_full_word = word_index_round_up(beg);
  idx_t end_full_word = word_index(end);

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

  idx_t beg_full_word = word_index_round_up(beg);
  idx_t end_full_word = word_index(end);

  assert(end_full_word - beg_full_word >= 32,
         "the range must include at least 32 bytes");

  // The range includes at least one full word.
  par_put_range_within_word(beg, bit_index(beg_full_word), value);
  if (value) {
    set_large_range_of_words(beg_full_word, end_full_word);
  } else {
    clear_large_range_of_words(beg_full_word, end_full_word);
  }
  par_put_range_within_word(bit_index(end_full_word), end, value);
}

bool BitMap::contains(const BitMap other) const {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size_in_words(); index++) {
    bm_word_t word_union = dest_map[index] | other_map[index];
    // If this has more bits set than dest_map[index], then other is not a
    // subset.
    if (word_union != dest_map[index]) return false;
  }
  return true;
}

bool BitMap::intersects(const BitMap other) const {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size_in_words(); index++) {
    if ((dest_map[index] & other_map[index]) != 0) return true;
  }
  // Otherwise, no intersection.
  return false;
}

void BitMap::set_union(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size_in_words(); index++) {
    dest_map[index] = dest_map[index] | other_map[index];
  }
}


void BitMap::set_difference(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size_in_words(); index++) {
    dest_map[index] = dest_map[index] & ~(other_map[index]);
  }
}


void BitMap::set_intersection(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    dest_map[index]  = dest_map[index] & other_map[index];
  }
}


void BitMap::set_intersection_at_offset(BitMap other, idx_t offset) {
  assert(other.size() >= offset, "offset not in range");
  assert(other.size() - offset >= size(), "other not large enough");
  // XXX Ideally, we would remove this restriction.
  guarantee((offset % (sizeof(bm_word_t) * BitsPerByte)) == 0,
            "Only handle aligned cases so far.");
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t offset_word_ind = word_index(offset);
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    dest_map[index] = dest_map[index] & other_map[offset_word_ind + index];
  }
}

bool BitMap::set_union_with_result(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bool changed = false;
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    idx_t temp = dest_map[index] | other_map[index];
    changed = changed || (temp != dest_map[index]);
    dest_map[index] = temp;
  }
  return changed;
}


bool BitMap::set_difference_with_result(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bool changed = false;
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    bm_word_t temp = dest_map[index] & ~(other_map[index]);
    changed = changed || (temp != dest_map[index]);
    dest_map[index] = temp;
  }
  return changed;
}


bool BitMap::set_intersection_with_result(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bool changed = false;
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    bm_word_t orig = dest_map[index];
    bm_word_t temp = orig & other_map[index];
    changed = changed || (temp != orig);
    dest_map[index]  = temp;
  }
  return changed;
}


void BitMap::set_from(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    dest_map[index] = other_map[index];
  }
}


bool BitMap::is_same(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bm_word_t* dest_map = map();
  bm_word_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    if (dest_map[index] != other_map[index]) return false;
  }
  return true;
}

bool BitMap::is_full() const {
  bm_word_t* word = map();
  idx_t rest = size();
  for (; rest >= (idx_t) BitsPerWord; rest -= BitsPerWord) {
    if (*word != ~(bm_word_t)0) return false;
    word++;
  }
  return rest == 0 || (*word | ~right_n_bits((int)rest)) == ~(bm_word_t)0;
}


bool BitMap::is_empty() const {
  bm_word_t* word = map();
  idx_t rest = size();
  for (; rest >= (idx_t) BitsPerWord; rest -= BitsPerWord) {
    if (*word != 0) return false;
    word++;
  }
  return rest == 0 || (*word & right_n_bits((int)rest)) == 0;
}

void BitMap::clear_large() {
  clear_large_range_of_words(0, size_in_words());
}

// Note that if the closure itself modifies the bitmap
// then modifications in and to the left of the _bit_ being
// currently sampled will not be seen. Note also that the
// interval [leftOffset, rightOffset) is right open.
bool BitMap::iterate(BitMapClosure* blk, idx_t leftOffset, idx_t rightOffset) {
  verify_range(leftOffset, rightOffset);

  idx_t startIndex = word_index(leftOffset);
  idx_t endIndex   = MIN2(word_index(rightOffset) + 1, size_in_words());
  for (idx_t index = startIndex, offset = leftOffset;
       offset < rightOffset && index < endIndex;
       offset = (++index) << LogBitsPerWord) {
    idx_t rest = map(index) >> (offset & (BitsPerWord - 1));
    for (; offset < rightOffset && rest != 0; offset++) {
      if (rest & 1) {
        if (!blk->do_bit(offset)) return false;
        //  resample at each closure application
        // (see, for instance, CMS bug 4525989)
        rest = map(index) >> (offset & (BitsPerWord -1));
      }
      rest = rest >> 1;
    }
  }
  return true;
}

BitMap::idx_t* BitMap::_pop_count_table = NULL;

void BitMap::init_pop_count_table() {
  if (_pop_count_table == NULL) {
    BitMap::idx_t *table = NEW_C_HEAP_ARRAY(idx_t, 256, mtInternal);
    for (uint i = 0; i < 256; i++) {
      table[i] = num_set_bits(i);
    }

    intptr_t res = Atomic::cmpxchg_ptr((intptr_t)  table,
                                       (intptr_t*) &_pop_count_table,
                                       (intptr_t)  NULL_WORD);
    if (res != NULL_WORD) {
      guarantee( _pop_count_table == (void*) res, "invariant" );
      FREE_C_HEAP_ARRAY(idx_t, table);
    }
  }
}

BitMap::idx_t BitMap::num_set_bits(bm_word_t w) {
  idx_t bits = 0;

  while (w != 0) {
    while ((w & 1) == 0) {
      w >>= 1;
    }
    bits++;
    w >>= 1;
  }
  return bits;
}

BitMap::idx_t BitMap::num_set_bits_from_table(unsigned char c) {
  assert(_pop_count_table != NULL, "precondition");
  return _pop_count_table[c];
}

BitMap::idx_t BitMap::count_one_bits() const {
  init_pop_count_table(); // If necessary.
  idx_t sum = 0;
  typedef unsigned char uchar;
  for (idx_t i = 0; i < size_in_words(); i++) {
    bm_word_t w = map()[i];
    for (size_t j = 0; j < sizeof(bm_word_t); j++) {
      sum += num_set_bits_from_table(uchar(w & 255));
      w >>= 8;
    }
  }
  return sum;
}

void BitMap::print_on_error(outputStream* st, const char* prefix) const {
  st->print_cr("%s[" PTR_FORMAT ", " PTR_FORMAT ")",
      prefix, p2i(map()), p2i((char*)map() + (size() >> LogBitsPerByte)));
}

#ifndef PRODUCT

void BitMap::print_on(outputStream* st) const {
  tty->print("Bitmap(" SIZE_FORMAT "):", size());
  for (idx_t index = 0; index < size(); index++) {
    tty->print("%c", at(index) ? '1' : '0');
  }
  tty->cr();
}

class TestBitMap : public AllStatic {
  const static BitMap::idx_t BITMAP_SIZE = 1024;
  static void fillBitMap(BitMap& map) {
    map.set_bit(1);
    map.set_bit(3);
    map.set_bit(17);
    map.set_bit(512);
  }

  static void testResize(bool in_resource_area) {
    {
      BitMap map(0, in_resource_area);
      map.resize(BITMAP_SIZE, in_resource_area);
      fillBitMap(map);

      BitMap map2(BITMAP_SIZE, in_resource_area);
      fillBitMap(map2);
      assert(map.is_same(map2), "could be");
    }

    {
      BitMap map(128, in_resource_area);
      map.resize(BITMAP_SIZE, in_resource_area);
      fillBitMap(map);

      BitMap map2(BITMAP_SIZE, in_resource_area);
      fillBitMap(map2);
      assert(map.is_same(map2), "could be");
    }

    {
      BitMap map(BITMAP_SIZE, in_resource_area);
      map.resize(BITMAP_SIZE, in_resource_area);
      fillBitMap(map);

      BitMap map2(BITMAP_SIZE, in_resource_area);
      fillBitMap(map2);
      assert(map.is_same(map2), "could be");
    }
  }

  static void testResizeResource() {
    ResourceMark rm;
    testResize(true);
  }

  static void testResizeNonResource() {
    const size_t bitmap_bytes = BITMAP_SIZE / BitsPerByte;

    // Test the default behavior
    testResize(false);

    {
      // Make sure that AllocatorMallocLimit is larger than our allocation request
      // forcing it to call standard malloc()
      SizeTFlagSetting fs(ArrayAllocatorMallocLimit, bitmap_bytes * 4);
      testResize(false);
    }
    {
      // Make sure that AllocatorMallocLimit is smaller than our allocation request
      // forcing it to call mmap() (or equivalent)
      SizeTFlagSetting fs(ArrayAllocatorMallocLimit, bitmap_bytes / 4);
      testResize(false);
    }
  }

 public:
  static void test() {
    testResizeResource();
    testResizeNonResource();
  }

};

void TestBitMap_test() {
  TestBitMap::test();
}
#endif


BitMap2D::BitMap2D(bm_word_t* map, idx_t size_in_slots, idx_t bits_per_slot)
  : _bits_per_slot(bits_per_slot)
  , _map(map, size_in_slots * bits_per_slot)
{
}


BitMap2D::BitMap2D(idx_t size_in_slots, idx_t bits_per_slot)
  : _bits_per_slot(bits_per_slot)
  , _map(size_in_slots * bits_per_slot)
{
}

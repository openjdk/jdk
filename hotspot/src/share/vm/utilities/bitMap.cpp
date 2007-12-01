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

# include "incls/_precompiled.incl"
# include "incls/_bitMap.cpp.incl"


BitMap::BitMap(idx_t* map, idx_t size_in_bits) {
  assert(size_in_bits >= 0, "just checking");
  _map = map;
  _size = size_in_bits;
}


BitMap::BitMap(idx_t size_in_bits) {
  assert(size_in_bits >= 0, "just checking");
  _size = size_in_bits;
  _map = NEW_RESOURCE_ARRAY(idx_t, size_in_words());
}


void BitMap::resize(idx_t size_in_bits) {
  assert(size_in_bits >= 0, "just checking");
  size_t old_size_in_words = size_in_words();
  uintptr_t* old_map = map();
  _size = size_in_bits;
  size_t new_size_in_words = size_in_words();
  _map = NEW_RESOURCE_ARRAY(idx_t, new_size_in_words);
  Copy::disjoint_words((HeapWord*) old_map, (HeapWord*) _map, MIN2(old_size_in_words, new_size_in_words));
  if (new_size_in_words > old_size_in_words) {
    clear_range_of_words(old_size_in_words, size_in_words());
  }
}

// Returns a bit mask for a range of bits [beg, end) within a single word.  Each
// bit in the mask is 0 if the bit is in the range, 1 if not in the range.  The
// returned mask can be used directly to clear the range, or inverted to set the
// range.  Note:  end must not be 0.
inline BitMap::idx_t
BitMap::inverted_bit_mask_for_range(idx_t beg, idx_t end) const {
  assert(end != 0, "does not work when end == 0");
  assert(beg == end || word_index(beg) == word_index(end - 1),
         "must be a single-word range");
  idx_t mask = bit_mask(beg) - 1;       // low (right) bits
  if (bit_in_word(end) != 0) {
    mask |= ~(bit_mask(end) - 1);       // high (left) bits
  }
  return mask;
}

void BitMap::set_range_within_word(idx_t beg, idx_t end) {
  // With a valid range (beg <= end), this test ensures that end != 0, as
  // required by inverted_bit_mask_for_range.  Also avoids an unnecessary write.
  if (beg != end) {
    idx_t mask = inverted_bit_mask_for_range(beg, end);
    *word_addr(beg) |= ~mask;
  }
}

void BitMap::clear_range_within_word(idx_t beg, idx_t end) {
  // With a valid range (beg <= end), this test ensures that end != 0, as
  // required by inverted_bit_mask_for_range.  Also avoids an unnecessary write.
  if (beg != end) {
    idx_t mask = inverted_bit_mask_for_range(beg, end);
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
      w  = *pw;
      nw = value ? (w | ~mr) : (w & mr);
    }
  }
}

inline void BitMap::set_large_range_of_words(idx_t beg, idx_t end) {
  memset(_map + beg, ~(unsigned char)0, (end - beg) * sizeof(uintptr_t));
}

inline void BitMap::clear_large_range_of_words(idx_t beg, idx_t end) {
  memset(_map + beg, 0, (end - beg) * sizeof(uintptr_t));
}

inline BitMap::idx_t BitMap::word_index_round_up(idx_t bit) const {
  idx_t bit_rounded_up = bit + (BitsPerWord - 1);
  // Check for integer arithmetic overflow.
  return bit_rounded_up > bit ? word_index(bit_rounded_up) : size_in_words();
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

  assert(end_full_word - beg_full_word >= 32,
         "the range must include at least 32 bytes");

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
  uintptr_t* dest_map = map();
  uintptr_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size_in_words(); index++) {
    uintptr_t word_union = dest_map[index] | other_map[index];
    // If this has more bits set than dest_map[index], then other is not a
    // subset.
    if (word_union != dest_map[index]) return false;
  }
  return true;
}

bool BitMap::intersects(const BitMap other) const {
  assert(size() == other.size(), "must have same size");
  uintptr_t* dest_map = map();
  uintptr_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size_in_words(); index++) {
    if ((dest_map[index] & other_map[index]) != 0) return true;
  }
  // Otherwise, no intersection.
  return false;
}

void BitMap::set_union(BitMap other) {
  assert(size() == other.size(), "must have same size");
  idx_t* dest_map = map();
  idx_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size_in_words(); index++) {
    dest_map[index] = dest_map[index] | other_map[index];
  }
}


void BitMap::set_difference(BitMap other) {
  assert(size() == other.size(), "must have same size");
  idx_t* dest_map = map();
  idx_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size_in_words(); index++) {
    dest_map[index] = dest_map[index] & ~(other_map[index]);
  }
}


void BitMap::set_intersection(BitMap other) {
  assert(size() == other.size(), "must have same size");
  idx_t* dest_map = map();
  idx_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    dest_map[index]  = dest_map[index] & other_map[index];
  }
}


bool BitMap::set_union_with_result(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bool changed = false;
  idx_t* dest_map = map();
  idx_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    idx_t temp = map(index) | other_map[index];
    changed = changed || (temp != map(index));
    map()[index] = temp;
  }
  return changed;
}


bool BitMap::set_difference_with_result(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bool changed = false;
  idx_t* dest_map = map();
  idx_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    idx_t temp = dest_map[index] & ~(other_map[index]);
    changed = changed || (temp != dest_map[index]);
    dest_map[index] = temp;
  }
  return changed;
}


bool BitMap::set_intersection_with_result(BitMap other) {
  assert(size() == other.size(), "must have same size");
  bool changed = false;
  idx_t* dest_map = map();
  idx_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    idx_t orig = dest_map[index];
    idx_t temp = orig & other_map[index];
    changed = changed || (temp != orig);
    dest_map[index]  = temp;
  }
  return changed;
}


void BitMap::set_from(BitMap other) {
  assert(size() == other.size(), "must have same size");
  idx_t* dest_map = map();
  idx_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    dest_map[index] = other_map[index];
  }
}


bool BitMap::is_same(BitMap other) {
  assert(size() == other.size(), "must have same size");
  idx_t* dest_map = map();
  idx_t* other_map = other.map();
  idx_t size = size_in_words();
  for (idx_t index = 0; index < size; index++) {
    if (dest_map[index] != other_map[index]) return false;
  }
  return true;
}

bool BitMap::is_full() const {
  uintptr_t* word = map();
  idx_t rest = size();
  for (; rest >= (idx_t) BitsPerWord; rest -= BitsPerWord) {
    if (*word != (uintptr_t) AllBits) return false;
    word++;
  }
  return rest == 0 || (*word | ~right_n_bits((int)rest)) == (uintptr_t) AllBits;
}


bool BitMap::is_empty() const {
  uintptr_t* word = map();
  idx_t rest = size();
  for (; rest >= (idx_t) BitsPerWord; rest -= BitsPerWord) {
    if (*word != (uintptr_t) NoBits) return false;
    word++;
  }
  return rest == 0 || (*word & right_n_bits((int)rest)) == (uintptr_t) NoBits;
}

void BitMap::clear_large() {
  clear_large_range_of_words(0, size_in_words());
}

// Note that if the closure itself modifies the bitmap
// then modifications in and to the left of the _bit_ being
// currently sampled will not be seen. Note also that the
// interval [leftOffset, rightOffset) is right open.
void BitMap::iterate(BitMapClosure* blk, idx_t leftOffset, idx_t rightOffset) {
  verify_range(leftOffset, rightOffset);

  idx_t startIndex = word_index(leftOffset);
  idx_t endIndex   = MIN2(word_index(rightOffset) + 1, size_in_words());
  for (idx_t index = startIndex, offset = leftOffset;
       offset < rightOffset && index < endIndex;
       offset = (++index) << LogBitsPerWord) {
    idx_t rest = map(index) >> (offset & (BitsPerWord - 1));
    for (; offset < rightOffset && rest != (uintptr_t)NoBits; offset++) {
      if (rest & 1) {
        blk->do_bit(offset);
        //  resample at each closure application
        // (see, for instance, CMS bug 4525989)
        rest = map(index) >> (offset & (BitsPerWord -1));
        // XXX debugging: remove
        // The following assertion assumes that closure application
        // doesn't clear bits (may not be true in general, e.g. G1).
        assert(rest & 1,
               "incorrect shift or closure application can clear bits?");
      }
      rest = rest >> 1;
    }
  }
}

BitMap::idx_t BitMap::get_next_one_offset(idx_t l_offset,
                                          idx_t r_offset) const {
  assert(l_offset <= size(), "BitMap index out of bounds");
  assert(r_offset <= size(), "BitMap index out of bounds");
  assert(l_offset <= r_offset, "l_offset > r_offset ?");

  if (l_offset == r_offset) {
    return l_offset;
  }
  idx_t   index = word_index(l_offset);
  idx_t r_index = word_index(r_offset-1) + 1;
  idx_t res_offset = l_offset;

  // check bits including and to the _left_ of offset's position
  idx_t pos = bit_in_word(res_offset);
  idx_t res = map(index) >> pos;
  if (res != (uintptr_t)NoBits) {
    // find the position of the 1-bit
    for (; !(res & 1); res_offset++) {
      res = res >> 1;
    }
    assert(res_offset >= l_offset, "just checking");
    return MIN2(res_offset, r_offset);
  }
  // skip over all word length 0-bit runs
  for (index++; index < r_index; index++) {
    res = map(index);
    if (res != (uintptr_t)NoBits) {
      // found a 1, return the offset
      for (res_offset = index << LogBitsPerWord; !(res & 1);
           res_offset++) {
        res = res >> 1;
      }
      assert(res & 1, "tautology; see loop condition");
      assert(res_offset >= l_offset, "just checking");
      return MIN2(res_offset, r_offset);
    }
  }
  return r_offset;
}

BitMap::idx_t BitMap::get_next_zero_offset(idx_t l_offset,
                                           idx_t r_offset) const {
  assert(l_offset <= size(), "BitMap index out of bounds");
  assert(r_offset <= size(), "BitMap index out of bounds");
  assert(l_offset <= r_offset, "l_offset > r_offset ?");

  if (l_offset == r_offset) {
    return l_offset;
  }
  idx_t   index = word_index(l_offset);
  idx_t r_index = word_index(r_offset-1) + 1;
  idx_t res_offset = l_offset;

  // check bits including and to the _left_ of offset's position
  idx_t pos = res_offset & (BitsPerWord - 1);
  idx_t res = (map(index) >> pos) | left_n_bits((int)pos);

  if (res != (uintptr_t)AllBits) {
    // find the position of the 0-bit
    for (; res & 1; res_offset++) {
      res = res >> 1;
    }
    assert(res_offset >= l_offset, "just checking");
    return MIN2(res_offset, r_offset);
  }
  // skip over all word length 1-bit runs
  for (index++; index < r_index; index++) {
    res = map(index);
    if (res != (uintptr_t)AllBits) {
      // found a 0, return the offset
      for (res_offset = index << LogBitsPerWord; res & 1;
           res_offset++) {
        res = res >> 1;
      }
      assert(!(res & 1), "tautology; see loop condition");
      assert(res_offset >= l_offset, "just checking");
      return MIN2(res_offset, r_offset);
    }
  }
  return r_offset;
}

#ifndef PRODUCT

void BitMap::print_on(outputStream* st) const {
  tty->print("Bitmap(%d):", size());
  for (idx_t index = 0; index < size(); index++) {
    tty->print("%c", at(index) ? '1' : '0');
  }
  tty->cr();
}

#endif


BitMap2D::BitMap2D(uintptr_t* map, idx_t size_in_slots, idx_t bits_per_slot)
  : _bits_per_slot(bits_per_slot)
  , _map(map, size_in_slots * bits_per_slot)
{
}


BitMap2D::BitMap2D(idx_t size_in_slots, idx_t bits_per_slot)
  : _bits_per_slot(bits_per_slot)
  , _map(size_in_slots * bits_per_slot)
{
}

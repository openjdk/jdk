/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

inline bool BitMap::par_set_bit(idx_t bit) {
  verify_index(bit);
  volatile idx_t* const addr = word_addr(bit);
  const idx_t mask = bit_mask(bit);
  idx_t old_val = *addr;

  do {
    const idx_t new_val = old_val | mask;
    if (new_val == old_val) {
      return false;     // Someone else beat us to it.
    }
    const idx_t cur_val = (idx_t) Atomic::cmpxchg_ptr((void*) new_val,
                                                      (volatile void*) addr,
                                                      (void*) old_val);
    if (cur_val == old_val) {
      return true;      // Success.
    }
    old_val = cur_val;  // The value changed, try again.
  } while (true);
}

inline bool BitMap::par_clear_bit(idx_t bit) {
  verify_index(bit);
  volatile idx_t* const addr = word_addr(bit);
  const idx_t mask = ~bit_mask(bit);
  idx_t old_val = *addr;

  do {
    const idx_t new_val = old_val & mask;
    if (new_val == old_val) {
      return false;     // Someone else beat us to it.
    }
    const idx_t cur_val = (idx_t) Atomic::cmpxchg_ptr((void*) new_val,
                                                      (volatile void*) addr,
                                                      (void*) old_val);
    if (cur_val == old_val) {
      return true;      // Success.
    }
    old_val = cur_val;  // The value changed, try again.
  } while (true);
}

inline BitMap::idx_t
BitMap::find_next_one_bit(idx_t beg_bit, idx_t end_bit) const
{
  verify_range(beg_bit, end_bit);
  assert(bit_in_word(end_bit) == 0, "end_bit not word-aligned");

  if (beg_bit == end_bit) {
    return beg_bit;
  }

  idx_t   index = word_index(beg_bit);
  idx_t r_index = word_index(end_bit);
  idx_t res_bit = beg_bit;

  // check bits including and to the _left_ of offset's position
  idx_t res = map(index) >> bit_in_word(res_bit);
  if (res != (uintptr_t) NoBits) {
    // find the position of the 1-bit
    for (; !(res & 1); res_bit++) {
      res = res >> 1;
    }
    assert(res_bit >= beg_bit && res_bit < end_bit, "just checking");
    return res_bit;
  }
  // skip over all word length 0-bit runs
  for (index++; index < r_index; index++) {
    res = map(index);
    if (res != (uintptr_t) NoBits) {
      // found a 1, return the offset
      for (res_bit = bit_index(index); !(res & 1); res_bit++) {
        res = res >> 1;
      }
      assert(res & 1, "tautology; see loop condition");
      assert(res_bit >= beg_bit && res_bit < end_bit, "just checking");
      return res_bit;
    }
  }
  return end_bit;
}

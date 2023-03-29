/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_BITMAP_INLINE_HPP
#define SHARE_UTILITIES_BITMAP_INLINE_HPP

#include "utilities/bitMap.hpp"

#include "runtime/atomic.hpp"
#include "utilities/align.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/powerOfTwo.hpp"

inline void BitMap::set_bit(idx_t bit) {
  verify_index(bit);
  *word_addr(bit) |= bit_mask(bit);
}

inline void BitMap::clear_bit(idx_t bit) {
  verify_index(bit);
  *word_addr(bit) &= ~bit_mask(bit);
}

inline const BitMap::bm_word_t BitMap::load_word_ordered(const volatile bm_word_t* const addr, atomic_memory_order memory_order) {
  if (memory_order == memory_order_relaxed || memory_order == memory_order_release) {
    return Atomic::load(addr);
  } else {
    assert(memory_order == memory_order_acq_rel ||
           memory_order == memory_order_acquire ||
           memory_order == memory_order_conservative,
           "unexpected memory ordering");
    return Atomic::load_acquire(addr);
  }
}

inline bool BitMap::par_at(idx_t bit, atomic_memory_order memory_order) const {
  verify_index(bit);
  assert(memory_order == memory_order_acquire ||
         memory_order == memory_order_relaxed,
         "unexpected memory ordering");
  const volatile bm_word_t* const addr = word_addr(bit);
  return (load_word_ordered(addr, memory_order) & bit_mask(bit)) != 0;
}

inline bool BitMap::par_set_bit(idx_t bit, atomic_memory_order memory_order) {
  verify_index(bit);
  volatile bm_word_t* const addr = word_addr(bit);
  const bm_word_t mask = bit_mask(bit);
  bm_word_t old_val = load_word_ordered(addr, memory_order);

  do {
    const bm_word_t new_val = old_val | mask;
    if (new_val == old_val) {
      return false;     // Someone else beat us to it.
    }
    const bm_word_t cur_val = Atomic::cmpxchg(addr, old_val, new_val, memory_order);
    if (cur_val == old_val) {
      return true;      // Success.
    }
    old_val = cur_val;  // The value changed, try again.
  } while (true);
}

inline bool BitMap::par_clear_bit(idx_t bit, atomic_memory_order memory_order) {
  verify_index(bit);
  volatile bm_word_t* const addr = word_addr(bit);
  const bm_word_t mask = ~bit_mask(bit);
  bm_word_t old_val = load_word_ordered(addr, memory_order);

  do {
    const bm_word_t new_val = old_val & mask;
    if (new_val == old_val) {
      return false;     // Someone else beat us to it.
    }
    const bm_word_t cur_val = Atomic::cmpxchg(addr, old_val, new_val, memory_order);
    if (cur_val == old_val) {
      return true;      // Success.
    }
    old_val = cur_val;  // The value changed, try again.
  } while (true);
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
  if (end - beg == 1) {
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

inline void BitMap::set_range_of_words(idx_t beg, idx_t end) {
  bm_word_t* map = _map;
  for (idx_t i = beg; i < end; ++i) map[i] = ~(bm_word_t)0;
}

inline void BitMap::clear_range_of_words(bm_word_t* map, idx_t beg, idx_t end) {
  for (idx_t i = beg; i < end; ++i) map[i] = 0;
}

inline void BitMap::clear_range_of_words(idx_t beg, idx_t end) {
  clear_range_of_words(_map, beg, end);
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

// General notes regarding find_{first,last}_bit_impl.
//
// The first (last) word often contains an interesting bit, either due to
// density or because of features of the calling algorithm.  So it's important
// to examine that word with a minimum of fuss, minimizing setup time for
// additional words that will be wasted if the that word is indeed
// interesting.
//
// The first (last) bit is similarly often interesting.  When it matters
// (density or features of the calling algorithm make it likely that bit is
// set), going straight to counting bits compares poorly to examining that bit
// first; the counting operations can be relatively expensive, plus there is
// the additional range check (unless aligned).  But when that bit isn't set,
// the cost of having tested for it is relatively small compared to the rest
// of the search.
//
// The benefit from aligned_right being true is relatively small.  It saves an
// operation in the setup of the word search loop.  It also eliminates the
// range check on the final result.  However, callers often have a comparison
// with end, and inlining may allow the two comparisons to be combined.  It is
// important when !aligned_right that return paths either return end or a
// value dominated by a comparison with end.  aligned_right is still helpful
// when the caller doesn't have a range check because features of the calling
// algorithm guarantee an interesting bit will be present.
//
// The benefit from aligned_left is even smaller, as there is no savings in
// the setup of the word search loop.

template<BitMap::bm_word_t flip, bool aligned_right>
inline BitMap::idx_t BitMap::find_first_bit_impl(idx_t beg, idx_t end) const {
  STATIC_ASSERT(flip == find_ones_flip || flip == find_zeros_flip);
  verify_range(beg, end);
  assert(!aligned_right || is_aligned(end, BitsPerWord), "end not aligned");

  if (beg < end) {
    // Get the word containing beg, and shift out low bits.
    idx_t word_index = to_words_align_down(beg);
    bm_word_t cword = flipped_word(word_index, flip) >> bit_in_word(beg);
    if ((cword & 1) != 0) {     // Test the beg bit.
      return beg;
    }
    // Position of bit0 of cword in the bitmap.  Initially for shifted first word.
    idx_t cword_pos = beg;
    if (cword == 0) {           // Test other bits in the first word.
      // First word had no interesting bits.  Word search through
      // aligned up end for a non-zero flipped word.
      idx_t word_limit = aligned_right
        ? to_words_align_down(end) // Minuscule savings when aligned.
        : to_words_align_up(end);
      while (++word_index < word_limit) {
        cword = flipped_word(word_index, flip);
        if (cword != 0) {
          // Update for found non-zero word, and join common tail to compute
          // result from cword_pos and non-zero cword.
          cword_pos = bit_index(word_index);
          break;
        }
      }
    }
    // For all paths reaching here, (cword != 0) is already known, so we
    // expect the compiler to not generate any code for it.  Either first word
    // was non-zero, or found a non-zero word in range, or fully scanned range
    // (so cword is zero).
    if (cword != 0) {
      idx_t result = cword_pos + count_trailing_zeros(cword);
      if (aligned_right || (result < end)) return result;
      // Result is beyond range bound; return end.
    }
  }
  return end;
}

template<BitMap::bm_word_t flip, bool aligned_left>
inline BitMap::idx_t BitMap::find_last_bit_impl(idx_t beg, idx_t end) const {
  STATIC_ASSERT(flip == find_ones_flip || flip == find_zeros_flip);
  verify_range(beg, end);
  assert(!aligned_left || is_aligned(beg, BitsPerWord), "beg not aligned");

  if (beg < end) {
    // Get the last partial and flipped word in the range.
    idx_t last_bit_index = end - 1;
    idx_t word_index = to_words_align_down(last_bit_index);
    bm_word_t cword = flipped_word(word_index, flip);
    // Mask for extracting and testing bits of last word.
    bm_word_t last_bit_mask = bm_word_t(1) << bit_in_word(last_bit_index);
    if ((cword & last_bit_mask) != 0) { // Test last bit.
      return last_bit_index;
    }
    // Extract prior bits, clearing those above last_bit_index.
    cword &= (last_bit_mask - 1);
    if (cword == 0) {           // Test other bits in the last word.
      // Last word had no interesting bits.  Word search through
      // aligned down beg for a non-zero flipped word.
      idx_t word_limit = to_words_align_down(beg);
      while (word_index-- > word_limit) {
        cword = flipped_word(word_index, flip);
        if (cword != 0) break;
      }
    }
    // For all paths reaching here, (cword != 0) is already known, so we
    // expect the compiler to not generate any code for it.  Either last word
    // was non-zero, or found a non-zero word in range, or fully scanned range
    // (so cword is zero).
    if (cword != 0) {
      idx_t result = bit_index(word_index) + log2i(cword);
      if (aligned_left || (result >= beg)) return result;
      // Result is below range bound; return end.
    }
  }
  return end;
}

inline BitMap::idx_t
BitMap::find_first_set_bit(idx_t beg, idx_t end) const {
  return find_first_bit_impl<find_ones_flip, false>(beg, end);
}

inline BitMap::idx_t
BitMap::find_first_clear_bit(idx_t beg, idx_t end) const {
  return find_first_bit_impl<find_zeros_flip, false>(beg, end);
}

inline BitMap::idx_t
BitMap::find_first_set_bit_aligned_right(idx_t beg, idx_t end) const {
  return find_first_bit_impl<find_ones_flip, true>(beg, end);
}

inline BitMap::idx_t
BitMap::find_last_set_bit(idx_t beg, idx_t end) const {
  return find_last_bit_impl<find_ones_flip, false>(beg, end);
}

inline BitMap::idx_t
BitMap::find_last_clear_bit(idx_t beg, idx_t end) const {
  return find_last_bit_impl<find_zeros_flip, false>(beg, end);
}

inline BitMap::idx_t
BitMap::find_last_set_bit_aligned_left(idx_t beg, idx_t end) const {
  return find_last_bit_impl<find_ones_flip, true>(beg, end);
}

// IterateInvoker supports conditionally stopping iteration early.  The
// invoker is called with the function to apply to each set index, along with
// the current index.  If the function returns void then the invoker always
// returns true, so no early stopping.  Otherwise, the result of the function
// is returned by the invoker.  Iteration stops early if conversion of that
// result to bool is false.

template<typename ReturnType>
struct BitMap::IterateInvoker {
  template<typename Function>
  bool operator()(Function function, idx_t index) const {
    return function(index);     // Stop early if converting to bool is false.
  }
};

template<>
struct BitMap::IterateInvoker<void> {
  template<typename Function>
  bool operator()(Function function, idx_t index) const {
    function(index);            // Result is void.
    return true;                // Never stop early.
  }
};

template <typename Function>
inline bool BitMap::iterate(Function function, idx_t beg, idx_t end) const {
  auto invoke = IterateInvoker<decltype(function(beg))>();
  for (idx_t index = beg; true; ++index) {
    index = find_first_set_bit(index, end);
    if (index >= end) {
      return true;
    } else if (!invoke(function, index)) {
      return false;
    }
  }
}

template <typename BitMapClosureType>
inline bool BitMap::iterate(BitMapClosureType* cl, idx_t beg, idx_t end) const {
  auto function = [&](idx_t index) { return cl->do_bit(index); };
  return iterate(function, beg, end);
}

template <typename Function>
inline bool BitMap::reverse_iterate(Function function, idx_t beg, idx_t end) const {
  auto invoke = IterateInvoker<decltype(function(beg))>();
  for (idx_t index; true; end = index) {
    index = find_last_set_bit(beg, end);
    if (index >= end) {
      return true;
    } else if (!invoke(function, index)) {
      return false;
    }
  }
}

template <typename BitMapClosureType>
inline bool BitMap::reverse_iterate(BitMapClosureType* cl, idx_t beg, idx_t end) const {
  auto function = [&](idx_t index) { return cl->do_bit(index); };
  return reverse_iterate(function, beg, end);
}

// Returns a bit mask for a range of bits [beg, end) within a single word.  Each
// bit in the mask is 0 if the bit is in the range, 1 if not in the range.  The
// returned mask can be used directly to clear the range, or inverted to set the
// range.  Note:  end must not be 0.
inline BitMap::bm_word_t
BitMap::inverted_bit_mask_for_range(idx_t beg, idx_t end) const {
  assert(end != 0, "does not work when end == 0");
  assert(beg == end || to_words_align_down(beg) == to_words_align_down(end - 1),
         "must be a single-word range");
  bm_word_t mask = bit_mask(beg) - 1;   // low (right) bits
  if (bit_in_word(end) != 0) {
    mask |= ~(bit_mask(end) - 1);       // high (left) bits
  }
  return mask;
}

inline void BitMap::set_large_range_of_words(idx_t beg, idx_t end) {
  assert(beg <= end, "underflow");
  memset(_map + beg, ~(unsigned char)0, (end - beg) * sizeof(bm_word_t));
}

inline void BitMap::clear_large_range_of_words(idx_t beg, idx_t end) {
  assert(beg <= end, "underflow");
  memset(_map + beg, 0, (end - beg) * sizeof(bm_word_t));
}

inline bool BitMap2D::is_valid_index(idx_t slot_index, idx_t bit_within_slot_index) {
  verify_bit_within_slot_index(bit_within_slot_index);
  return (bit_index(slot_index, bit_within_slot_index) < size_in_bits());
}

inline bool BitMap2D::at(idx_t slot_index, idx_t bit_within_slot_index) const {
  verify_bit_within_slot_index(bit_within_slot_index);
  return _map.at(bit_index(slot_index, bit_within_slot_index));
}

inline void BitMap2D::set_bit(idx_t slot_index, idx_t bit_within_slot_index) {
  verify_bit_within_slot_index(bit_within_slot_index);
  _map.set_bit(bit_index(slot_index, bit_within_slot_index));
}

inline void BitMap2D::clear_bit(idx_t slot_index, idx_t bit_within_slot_index) {
  verify_bit_within_slot_index(bit_within_slot_index);
  _map.clear_bit(bit_index(slot_index, bit_within_slot_index));
}

inline void BitMap2D::at_put(idx_t slot_index, idx_t bit_within_slot_index, bool value) {
  verify_bit_within_slot_index(bit_within_slot_index);
  _map.at_put(bit_index(slot_index, bit_within_slot_index), value);
}

inline void BitMap2D::at_put_grow(idx_t slot_index, idx_t bit_within_slot_index, bool value) {
  verify_bit_within_slot_index(bit_within_slot_index);

  idx_t bit = bit_index(slot_index, bit_within_slot_index);
  if (bit >= _map.size()) {
    _map.resize(2 * MAX2(_map.size(), bit));
  }
  _map.at_put(bit, value);
}

#endif // SHARE_UTILITIES_BITMAP_INLINE_HPP

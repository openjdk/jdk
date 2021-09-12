#ifndef SHARE_GC_G1_G1BOTFIXINGCARDSET_INLINE_HPP
#define SHARE_GC_G1_G1BOTFIXINGCARDSET_INLINE_HPP

#include "gc/g1/g1BlockOffsetTable.hpp"
#include "gc/g1/g1BOTFixingCardSet.hpp"
#include "gc/g1/heapRegion.hpp"

#include "runtime/atomic.hpp"
#include "utilities/bitMap.inline.hpp"

// Set the element at i to v. This must success, assuming no other threads will try to set it.
inline void G1BOTFixingCardSetArray::set_entry(size_t i, CardIndex v) {
  assert(i < _size, "Sanity");
  CardIndex* entry = align_down(_data + i, sizeof(WordType));
  WordType* word_entry = (WordType*)entry;
  size_t offset = i % EntriesPerWord;

  WordType old_val = *word_entry;
  WordType expect = 0;
  do {
    assert(((CardIndex*)(&old_val))[offset] == 0, "Entry have been set");
    expect = old_val;
    WordType new_val = expect;
    ((CardIndex*)(&new_val))[offset] = v;
    old_val = Atomic::cmpxchg(word_entry, expect, new_val, memory_order_relaxed);
  } while (old_val != expect);
}

// Clear the entry at i. Success in clearing will return the original value at this position.
inline G1BOTFixingCardSet::CardIndex G1BOTFixingCardSetArray::try_clear_entry(size_t i) {
  assert(i < _size, "Sanity");
  CardIndex* entry = align_down(_data + i, sizeof(WordType));
  WordType* word_entry = (WordType*)entry;
  size_t offset = i % EntriesPerWord;

  WordType old_val = *word_entry;
  WordType expect = 0;
  do {
    if (((CardIndex*)(&old_val))[offset] == 0) {
      return 0;
    }
    expect = old_val;
    WordType new_val = expect;
    ((CardIndex*)(&new_val))[offset] = 0;
    old_val = Atomic::cmpxchg(word_entry, expect, new_val, memory_order_relaxed);
  } while (old_val != expect);
  return old_val;
}

inline void G1BOTFixingCardSetArray::add_card(size_t position, CardIndex card_index) {
  set_entry(position, card_index);
}

inline G1BOTFixingCardSet::CardIndex G1BOTFixingCardSetArray::claim_card(size_t position) {
  return try_clear_entry(position);
}

inline G1BOTFixingCardSet::CardIndex G1BOTFixingCardSetArray::find_first_card_in(size_t min_pos,
                                                                                 size_t max_pos) {
  assert(min_pos <= max_pos, "Invalid range");
  assert(max_pos < _size, "Range out of bounds");
  for (size_t i = min_pos; i <= max_pos; i++) {
    if (_data[i] != 0) {
      return _data[i];
    }
  }
  return 0;
}

inline void G1BOTFixingCardSetArray::iterate_cards(CardIterator& iter) {
  for (size_t i = 0; i < _size; i++) {
    if (_data[i] != 0) {
      if (claim_card(i)) {
        if (iter.do_card(_data[i]) == false) {
          // Iteration aborts.
          return;
        }
      }
    }
  }
}

inline void G1BOTFixingCardSetBitMap::add_card(CardIndex effect_card_index) {
  BitMapView bm(_bits, _size_in_bits);
  BitMap::idx_t bit_pos = bit_position_for(effect_card_index);
  assert(bit_pos < _size_in_bits, "Out of bounds");
  bool success = bm.par_set_bit(bit_pos, memory_order_relaxed);
  assert(success, "Must success for add card");
}

inline bool G1BOTFixingCardSetBitMap::claim_card(CardIndex effect_card_index) {
  BitMapView bm(_bits, _size_in_bits);
  BitMap::idx_t bit_pos = bit_position_for(effect_card_index);
  if (bm.par_clear_bit(bit_pos, memory_order_relaxed)) {
    return true;
  }
  return false;
}

inline G1BOTFixingCardSet::CardIndex G1BOTFixingCardSetBitMap::find_first_card_in(
    CardIndex min_effect_card_index, CardIndex max_effect_card_index) {
  BitMap::idx_t min_pos = bit_position_for(min_effect_card_index);
  BitMap::idx_t max_pos = bit_position_for(max_effect_card_index);
  assert(min_pos <= max_pos, "Invalid range");
  assert(max_pos < _size_in_bits, "Range out of bounds");
  BitMapView bm(_bits, _size_in_bits);
  BitMapView::idx_t one_position = bm.get_next_one_offset(min_pos, max_pos + 1);
  if (one_position == max_pos + 1) {
    // If this overflows, it will be zero, which is still an invalid value that we can check.
    return max_effect_card_index + 1;
  }
  return card_index_for(one_position);
}

inline void G1BOTFixingCardSetBitMap::iterate_cards(BitMapClosure* cl) {
  BitMapView bm(_bits, _size_in_bits);
  bm.iterate(cl);
}

inline G1BOTFixingCardSet::CardIndex G1BOTFixingCardSet::card_index_for(HeapWord* addr) const {
  assert(_hr->bottom() <= addr && addr < _hr->end(), "Card index would overflow");
  size_t card_index = pointer_delta(addr, _hr->bottom()) >> BOTConstants::LogN_words;
  assert(card_index <= _last_card_index, "Sanity");
  return (CardIndex)card_index;
}

inline HeapWord* G1BOTFixingCardSet::card_boundary_for(CardIndex card_index) const {
  return _hr->bottom() + (((size_t)card_index) << BOTConstants::LogN_words);
}

inline bool G1BOTFixingCardSet::is_below_start(HeapWord* addr) const {
  return card_index_for(addr) < _start_card_index;
}

// Compute the array index for a card index. We will first offset the card index by
// -_start_card_index. Then we will check whether we need to handle the special case of
// _last_card_index.
inline size_t G1BOTFixingCardSet::array_index_for(CardIndex card_index) const {
  assert(card_index >= _start_card_index, "No need to fix");
  card_index -= _start_card_index;
  size_t index_in_array = (((size_t)card_index) << BOTConstants::LogN_words) / _plab_word_size;
  assert(_last_card_index >= _start_card_index, "One of these is not correctly set");
  if (card_index == _last_card_index - _start_card_index) {
    // There is a special case that a small plab can be allocated at the end of the region,
    // possibly making the last two plabs sharing the same array index, if using the above
    // calculation. Because this small plab's last card must also be the last card of the region,
    // we can identify it and store this one in the special slot.
    index_in_array++; // The special slot
  }
  return index_in_array;
}

// Effective card index is card index minus _start_card_index.
// Effective card index is used for bitmap storage.
inline G1BOTFixingCardSet::CardIndex
G1BOTFixingCardSet::bitmap_effect_card_index_for(CardIndex card_index) const {
  assert(card_index >= _start_card_index, "No need to fix");
  return card_index - _start_card_index;
}
inline G1BOTFixingCardSet::CardIndex
G1BOTFixingCardSet::bitmap_card_index_for(CardIndex effect_card_index) const {
  assert(effect_card_index + _start_card_index <= _last_card_index, "Sanity");
  return effect_card_index + _start_card_index;
}

inline G1BOTFixingCardSetArray* G1BOTFixingCardSet::as_array() {
  return (G1BOTFixingCardSetArray*)_dynamic_container;
}

inline G1BOTFixingCardSetBitMap* G1BOTFixingCardSet::as_bitmap() {
  return (G1BOTFixingCardSetBitMap*)_dynamic_container;
}

#endif // SHARE_GC_G1_G1BOTFIXINGCARDSET_INLINE_HPP

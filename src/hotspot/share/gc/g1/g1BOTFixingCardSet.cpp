#include "gc/g1/g1BlockOffsetTable.hpp"
#include "gc/g1/g1BOTFixingCardSet.inline.hpp"
#include "gc/g1/heapRegion.hpp"

#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "utilities/bitMap.hpp"

using CardIndex = G1BOTFixingCardSet::CardIndex;

CardIndex G1BOTFixingCardSet::_last_card_index = 0;
size_t G1BOTFixingCardSet::_plab_word_size = 0;
G1BOTFixingCardSet::ContainerType G1BOTFixingCardSet::_dynamic_container_type = Array;

G1BOTFixingCardSet::G1BOTFixingCardSet(HeapRegion* hr) :
  _type(Static),
  _start_card_index(_first_card_index),
  _num_plabs(0),
  _dynamic_container(NULL),
  _next(NULL),
  _hr(hr) {
  assert((size_t)HeapRegion::LogOfHRGrainBytes - BOTConstants::LogN <=
         sizeof(CardIndex) * BitsPerByte,
         "Unable to encode card with " SIZE_FORMAT " bits", sizeof(CardIndex) * BitsPerByte);
  for (uint i = 0; i < static_container_size; i++) {
    _static_container[i] = 0;
  }
}

// Prepare globals for adding cards.
void G1BOTFixingCardSet::prepare(size_t plab_word_size) {
  // The last word's card.
  _last_card_index = (CardIndex)((HeapRegion::GrainWords - 1) >> BOTConstants::LogN_words);
  _plab_word_size = plab_word_size;
  size_t threshold = sizeof(CardIndex) * BitsPerByte;
  // If plab is smaller than (number of bits x card size).
  if (plab_word_size < (threshold << BOTConstants::LogN_words)) {
    _dynamic_container_type = BitMap;
  }
}

// New plabs are allocated above the current top. So BOT fixing starts at the current top.
// Anything below is considered fixed.
void G1BOTFixingCardSet::set_bot_fixing_start() {
  assert(_hr->is_old(), "Only set for old regions");
  if (_hr->top() == _hr->end()) {
    // Nothing to do.
    return;
  }
  CardIndex card_index_for_top = card_index_for(_hr->top());
  // The card of top() does not need to be fixed. Move to the next one.
  if (card_index_for_top == _last_card_index) {
    return;
  }
  _start_card_index = card_index_for_top + 1;
}

void G1BOTFixingCardSet::transition_to_dynamic() {
  void* container_mem = NULL;
  // Size of the area in the region that needs fixing. We don't need to reserve space for
  // cards that don't need fixing in the container.
  size_t fix_size = HeapRegion::GrainWords -
                    (((size_t)_start_card_index) << BOTConstants::LogN_words);
  if (_dynamic_container_type == Array) {
    // +1 is because when the region is nearly full, there could be some space
    // smaller than _plab_word_size. A plab can still be allocated
    // into that space. We have to take that into account.
    size_t array_size = fix_size / _plab_word_size + (fix_size % _plab_word_size != 0) + 1;
    size_t container_size = G1BOTFixingCardSetArray::size_in_bytes(array_size);
    container_mem = NEW_C_HEAP_ARRAY(jbyte, container_size, mtGC);
    memset(container_mem, 0, container_size);
    new (container_mem) G1BOTFixingCardSetArray(array_size);
  } else {
    assert(_dynamic_container_type == BitMap, "Sanity");
    size_t max_num_cards = (fix_size >> BOTConstants::LogN_words);
    size_t container_size = G1BOTFixingCardSetBitMap::size_in_bytes(max_num_cards);
    container_mem = NEW_C_HEAP_ARRAY(jbyte, container_size, mtGC);
    memset(container_mem, 0, container_size);
    new (container_mem) G1BOTFixingCardSetBitMap(max_num_cards);
  }

  // Guarantees that whoever fails must see the correct dynamic container.
  if (!Atomic::replace_if_null(&_dynamic_container, container_mem, memory_order_acq_rel)) {
    // Someone else replaced before us.
    FREE_C_HEAP_ARRAY(jbyte, container_mem);
    return;
  }

  // Guarantees that whoever reads _type != Static can see the dynamic container.
  ContainerType t = Atomic::cmpxchg(&_type, Static, _dynamic_container_type, memory_order_acq_rel);
  assert(t == Static, "We should be the only one setting the type");

  // Copy content from the static array to the new container.
  assert(_num_plabs >= static_container_size, "Static container must be full");
  for (uint i = 0; i < static_container_size; i++) {
    CardIndex c = (CardIndex)_static_container[i];
    if (_dynamic_container_type == Array) {
      ((G1BOTFixingCardSetArray*)container_mem)->add_card(array_index_for(c), c);
    } else {
      assert(_dynamic_container_type == BitMap, "Sanity");
      ((G1BOTFixingCardSetBitMap*)container_mem)->add_card(bitmap_effect_card_index_for(c));
    }
    _static_container[i] = 0;
  }
}

void G1BOTFixingCardSet::add_card_to_dynamic(CardIndex card_index) {
  if (_dynamic_container_type == Array) {
    as_array()->add_card(array_index_for(card_index), card_index);
  } else {
    assert(_dynamic_container_type == BitMap, "Sanity");
    as_bitmap()->add_card(bitmap_effect_card_index_for(card_index));
  }
}

bool G1BOTFixingCardSet::add_card(HeapWord* addr) {
  CardIndex card_index = card_index_for(addr);
  assert(card_index >= _start_card_index, "No need to fix");
  // Try to add to the static array first.
  if (Atomic::load_acquire(&_type) == Static) {
    uint i = Atomic::fetch_and_add(&_num_plabs, (uint)1, memory_order_relaxed);
    if (i < static_container_size) {
      _static_container[i] = card_index;
      return i == 0; // Is this the first card?
    } else {
      transition_to_dynamic();
    }
  }
  assert(_dynamic_container != NULL, "Must be visible");

  add_card_to_dynamic(card_index);
  return false;
}

bool G1BOTFixingCardSet::claim_card_from_dynamic(CardIndex card_index) {
  if (_dynamic_container_type == Array) {
    return as_array()->claim_card(array_index_for(card_index)) == card_index;
  } else {
    assert(_dynamic_container_type == BitMap, "Sanity");
    CardIndex effect_card_index = bitmap_effect_card_index_for(card_index);
    return as_bitmap()->claim_card(effect_card_index);
  }
}

bool G1BOTFixingCardSet::claim_card(CardIndex card_index) {
  assert(card_index >= _start_card_index, "No need to fix this card");
  if (_type == Static) {
    for (uint i = 0; i < static_container_size; i++) {
      if ((CardIndex)_static_container[i] == card_index) {
        CardIndex c = (CardIndex)Atomic::cmpxchg(&_static_container[i], (WordType)card_index,
                                                 (WordType)0, memory_order_relaxed);
        return c == card_index;
      }
    }
    return false;
  }

  return claim_card_from_dynamic(card_index);
}

CardIndex G1BOTFixingCardSet::find_first_card_in(CardIndex min_card_index,
                                                 CardIndex max_card_index) {
  if (_dynamic_container_type == Array) {
    return as_array()->find_first_card_in(array_index_for(min_card_index),
                                          array_index_for(max_card_index));
  } else {
    assert(_dynamic_container_type == BitMap, "Sanity");
    CardIndex min_effect_card_index = bitmap_effect_card_index_for(min_card_index);
    CardIndex max_effect_card_index = bitmap_effect_card_index_for(max_card_index);
    CardIndex c = as_bitmap()->find_first_card_in(min_effect_card_index, max_effect_card_index);
    if (c == max_effect_card_index + 1) {
      // Not found.
      return 0;
    }
    return bitmap_card_index_for(c);
  }
}

// Given a card boundary, return the card that represents the plab that crosses this boundary.
// This should be used by concurrent refinement to get the covering plab of a card table card.
// A possible plab start will help us narrow down the search range for this plab, where we
// assume the plab starts no later than latest_plab_start.
CardIndex G1BOTFixingCardSet::find_plab_covering(HeapWord* card_boundary,
                                                 HeapWord* latest_plab_start) {
  assert(card_boundary < _hr->top(), "Sanity");
  assert(is_aligned(card_boundary, BOTConstants::N_bytes), "Must be aligned");
  assert(latest_plab_start <= card_boundary, "Not a helpful start addr");
  assert(card_boundary < latest_plab_start + _plab_word_size, "PLAB cannot possibly cover addr");
  // If a plab covers the card boundary, we should be able to find
  // the last card of the plab at [card_boundary, latest_plab_start + _plab_word_size).
  CardIndex min_card_index = card_index_for(card_boundary);
  HeapWord* end_of_search = MIN2(latest_plab_start + _plab_word_size, _hr->top()) - 1;
  CardIndex max_card_index = card_index_for(end_of_search);
  assert(_start_card_index <= min_card_index && min_card_index <= max_card_index, "Sanity");

  if (_type == Static) {
    CardIndex found = 0;
    for (uint i = 0; i < static_container_size; i++) {
      CardIndex c = (CardIndex)_static_container[i];
      if (min_card_index <= c && c <= max_card_index) {
        if (found == 0) {
          found = c;
        } else {
          // Sometimes multiple cards fall in this range (depending the given latest_plab_start),
          // we should use the smaller one. The other one must be a false match.
          found = MIN2(c, found);
        }
      }
    }
    return found;
  }

  return find_first_card_in(min_card_index, max_card_index);
}

void G1BOTFixingCardSet::iterate_cards_in_dynamic(CardIterator& iter) {
  if (_dynamic_container_type == Array) {
    return as_array()->iterate_cards(iter);
  } else {
    assert(_dynamic_container_type == BitMap, "Sanity");

    class BOTFixingBitMapClosure: public BitMapClosure {
      G1BOTFixingCardSet* _card_set;
      CardIterator* _iter;
    public:
      BOTFixingBitMapClosure(G1BOTFixingCardSet* card_set, CardIterator* iter) :
        _card_set(card_set), _iter(iter) {}
      bool do_bit(BitMap::idx_t index) {
        return _iter->do_card(
          _card_set->bitmap_card_index_for((G1BOTFixingCardSetBitMap::card_index_for(index))));
      }
    } cl(this, &iter);

    return as_bitmap()->iterate_cards(&cl);
  }
}

void G1BOTFixingCardSet::iterate_cards(CardIterator& iter) {
  if (_type == Static) {
    for (uint i = 0; i < static_container_size; i++) {
      CardIndex card_index = (CardIndex)_static_container[i];
      if (card_index != 0) {
        if (card_index == Atomic::cmpxchg(&_static_container[i], (WordType)card_index, (WordType)0,
                                          memory_order_relaxed)) {
          if (!iter.do_card(card_index)) {
            return;
          }
        }
      }
    }
  } else {
    iterate_cards_in_dynamic(iter);
  }
}

void G1BOTFixingCardSet::clear() {
  if (_type != Static) {
    // First transition back to static.
    _type = Static;
    FREE_C_HEAP_ARRAY(jbyte, _dynamic_container);
    _dynamic_container = NULL;
  }

  _start_card_index = _first_card_index;
  _num_plabs = 0;
  for (uint i = 0; i < static_container_size; i++) {
    _static_container[i] = 0;
  }
}

void G1BOTFixingCardSet::print_stats() {
  log_info(gc, bot)("BOT Fixing Card Set: region=%s, type=%d, start/last=%d/%d, n=%d",
                    _hr->get_type_str(), _type, _start_card_index, _last_card_index, _num_plabs);
}

void G1BOTFixingCardSet::verify() {
  assert(_type == Static, "Type incorrect");
  // An old region might not have its card set cleared since last gc, because it's never enlisted.
  assert(_start_card_index == _first_card_index || _hr->is_old(),
         "Start card incorrect");
  assert(_num_plabs == 0, "Size not zero");
  for (uint i = 0; i < static_container_size; i++) {
    assert(_static_container[i] == 0, "Static container not zero");
  }
  assert(_dynamic_container == NULL, "Dynamic container not cleared");
  // _next can be whatever (managed externally).
}

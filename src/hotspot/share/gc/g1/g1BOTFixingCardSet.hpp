#ifndef SHARE_GC_G1_G1BOTFIXINGCARDSET_HPP
#define SHARE_GC_G1_G1BOTFIXINGCARDSET_HPP

#include "utilities/bitMap.hpp"
#include "utilities/globalDefinitions.hpp"

class HeapRegion;
class G1BOTFixingCardSetArray;
class G1BOTFixingCardSetBitMap;

// This card set contains the BOT entries (cards) that need to be fixed in a region.
// Each member uniquely identifies a plab by being the last card covered by the plab.
// Every card covered by a plab (except for the first one) need to be fixed.
// Knowing the last card of a plab is the same as knowing the cards it covers,
// because BOT can return the start of the plab given its last card, then we know
// what's in between. If BOT cannot precisely return the start of the plab (this happens when
// it gets fixed, probably by concurrent refinement), it will still return up to which point
// it has been fixed. Then we can fixed from there.
//
// We chose to use the last card of a plab instead of the first card, because otherwise we cannot
// take advantage of this partially fixed case. However, if there is no partial fixing, i.e.,
// every plab gets fixed before we visit BOT for the area it covers, then there is not much
// difference.
//
// This card set uses three types of containers. There could be either an array or a bitmap,
// depending on the plab size:
// Suppose the card size is 512 bytes and the largest region is 32m. A card can be represented
// using an offset with under 16 bits. The number of entries we need in an array is around
// region_size/plab_size, which gives us array_size=region_size/plab_size*16 bits.
// The size of a bitmap, using 1 bit for 1 card, for the same region would be region_size/512 bits.
// So, using a bitmap is more worthwhile (in terms of space)
// than an array only when the plab size is smaller than 16x512 bytes, or 16 cards.
// This card set chooses the data structure accordingly given a plab_size.
//
// Sometimes we know that the plabs are allocated above an address (e.g., region top before gc).
// We will use this information to reduce the required size.
//
// The above two containers are dynamically allocated. To prevent too many dynamic allocations,
// there is also a fixed-sized array, which is supposed to handle most of the cases.
class G1BOTFixingCardSet {
public:
  typedef uint16_t CardIndex;
  typedef uint32_t WordType; // Atomic operations work with this granularity
  static_assert(sizeof(WordType) >= sizeof(CardIndex), "Must be able to hold a card index");

  class CardIterator: public StackObj {
  public:
    // Return false to abort iteration.
    virtual bool do_card(CardIndex card_index) = 0;
  };

private:
  enum ContainerType {
    Static,
    Array,
    BitMap
  };
  ContainerType _type;

  // CardIndex 0 is considered an invalid card, because we never need to fix the first BOT entry.
  static const CardIndex _first_card_index = 1;
  // Fixing starts from this card. This should be set to the first card
  // after region top (not including region top) before gc.
  // This card is in [_first_card_index, _last_card_index].
  CardIndex _start_card_index;
  // The last card in a region. It's relative to the max size of the region.
  // This card might need special handling if we use an array container.
  static CardIndex _last_card_index;

  // PLAB size recorded before each gc.
  static size_t _plab_word_size;

  // Number of plabs recorded. Also a pointer into _static_container.
  // When we transition to using the dynamic array or bitmap, this stops updating.
  // So it's only good for is_empty() after that.
  uint _num_plabs;

  // The statically allocated container.
  static constexpr uint static_container_size = 4; // Preferably at least the number of gc threads
  WordType _static_container[static_container_size];

  // A type decided before each gc according to the plab size.
  static ContainerType _dynamic_container_type;
  // The dynamically allocated container.
  void* _dynamic_container;

  // To form a list of card sets. Used in job dispatching and cleaning up.
  G1BOTFixingCardSet* _next;

  // The owner heap region.
  HeapRegion* _hr;

  size_t array_index_for(CardIndex card_index) const;
  CardIndex bitmap_effect_card_index_for(CardIndex card_index) const;
  CardIndex bitmap_card_index_for(CardIndex effect_card_index) const;

  // Transition from static to dynamic container.
  void transition_to_dynamic();

  G1BOTFixingCardSetArray* as_array();
  G1BOTFixingCardSetBitMap* as_bitmap();

  void add_card_to_dynamic(CardIndex card_index);

  bool claim_card_from_dynamic(CardIndex card_index);

  // Find the first card in the range [min_card_index, max_card_index] in the dynamic container.
  CardIndex find_first_card_in(CardIndex min_card_index, CardIndex max_card_index);

  void iterate_cards_in_dynamic(CardIterator& iter);

public:
  G1BOTFixingCardSet(HeapRegion* hr);

  static void prepare(size_t plab_word_size);

  G1BOTFixingCardSet* next() const { return _next; }
  void set_next(G1BOTFixingCardSet* next) {
    _next = next;
  }

  HeapRegion* hr() const { return _hr; }

  bool is_empty() const { return _num_plabs == 0; }
  void mark_as_done() { _num_plabs = 0; }

  CardIndex card_index_for(HeapWord* addr) const;

  HeapWord* card_boundary_for(CardIndex card_index) const;

  void set_bot_fixing_start();
  bool is_below_start(HeapWord* addr) const;

  // Add the card of this address to the set. Return whether the container was empty.
  bool add_card(HeapWord* addr);

  // Claim the card of this index and return whether it's successful.
  bool claim_card(CardIndex card_index);

  // Find the last card of the plab that covers the given card boundary.
  // latest_plab_start specifies the latest point where the plab starts (say, given by
  // _hr->need_fixing(card_boundary)).
  // Return the card index. Note that this is not always accurate. The caller
  // might need to check whether this plab really covers the card boundary.
  CardIndex find_plab_covering(HeapWord* card_boundary, HeapWord* latest_plab_start);

  // Iterate the cards.
  void iterate_cards(CardIterator& iter);

  void clear();

  void print_stats();
  void verify();
};

class G1BOTFixingCardSetArray {
private:
  typedef G1BOTFixingCardSet::CardIndex CardIndex;
  typedef G1BOTFixingCardSet::WordType WordType;
  typedef G1BOTFixingCardSet::CardIterator CardIterator;
  static constexpr size_t EntriesPerWord = sizeof(WordType) / sizeof(CardIndex);

  const size_t _size;
  CardIndex _data[1];

  template<typename Derived>
  static size_t header_size_in_bytes_internal() {
    return offset_of(Derived, _data);
  }

  inline void set_entry(size_t i, CardIndex v);
  inline CardIndex try_clear_entry(size_t i);

public:
  G1BOTFixingCardSetArray(size_t num_elems) : _size(num_elems) {
    assert(_size > 0, "Sanity");
  }

  static size_t size_in_bytes(size_t num_elems) {
    return header_size_in_bytes_internal<G1BOTFixingCardSetArray>() +
           align_up(num_elems, EntriesPerWord) * sizeof(CardIndex);
  }

  void add_card(size_t position, CardIndex card_index);

  CardIndex claim_card(size_t position);
  CardIndex find_first_card_in(size_t min_pos, size_t max_pos);

  void iterate_cards(CardIterator& iter);
};

class G1BOTFixingCardSetBitMap {
private:
  typedef G1BOTFixingCardSet::CardIndex CardIndex;
  typedef G1BOTFixingCardSet::CardIterator CardIterator;

  const size_t _size_in_bits;
  BitMap::bm_word_t _bits[1];

  template<typename Derived>
  static size_t header_size_in_bytes_internal() {
    return offset_of(Derived, _bits);
  }

public:
  G1BOTFixingCardSetBitMap(size_t size_in_bits) : _size_in_bits(size_in_bits) {
    assert(_size_in_bits > 0, "Sanity");
  }

  static size_t size_in_bytes(size_t size_in_bits) {
    return header_size_in_bytes_internal<G1BOTFixingCardSetBitMap>() +
           BitMap::calc_size_in_words(size_in_bits) * BytesPerWord;
  }

  static BitMap::idx_t bit_position_for(CardIndex card_index) {
    assert(sizeof(CardIndex) <= sizeof(BitMap::idx_t), "Sanity");
    return card_index;
  }
  static CardIndex card_index_for(BitMap::idx_t bit_position) {
    assert(bit_position <= (BitMap::idx_t)(CardIndex)-1, "Overflow");
    return (CardIndex)bit_position;
  }

  void add_card(CardIndex effect_card_index);

  bool claim_card(CardIndex effect_card_index);
  CardIndex find_first_card_in(CardIndex min_card_index, CardIndex max_card_index);

  void iterate_cards(BitMapClosure* cl);
};

#endif // SHARE_GC_G1_G1BOTFIXINGCARDSET_HPP

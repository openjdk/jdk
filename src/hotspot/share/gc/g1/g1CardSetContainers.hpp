/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1CARDSETCONTAINERS_HPP
#define SHARE_GC_G1_G1CARDSETCONTAINERS_HPP

#include "gc/g1/g1CardSet.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/spinYield.hpp"

#include "logging/log.hpp"

#include "runtime/thread.inline.hpp"

// A helper class to encode a few card indexes within a CardSetPtr.
//
// The pointer value (either 32 or 64 bits) is split into two areas:
//
// - Header containing identifying tag and number of encoded cards.
// - Data area containing the card indexes themselves
//
// The header starts (from LSB) with the identifying tag (two bits,
// always 00), and three bits size. The size stores the number of
// valid card indexes after the header.
//
// The data area makes up the remainder of the word, with card indexes
// put one after another at increasing bit positions. The separate
// card indexes use just enough space (bits) to represent the whole
// range of cards needed for covering the whole range of values
// (typically in a region). There may be unused space at the top of
// the word.
//
// Example:
//
//   64 bit pointer size, with 8M-size regions (8M == 2^23)
// -> 2^14 (2^23 / 2^9) cards; each card represents 512 bytes in a region
// -> 14 bits per card; must have enough bits to hold the max card index
// -> may encode up to 4 cards into it, using 61 bits (5 bits header + 4 * 14)
//
// M                                                     L
// S                                                     S
// B                                                     B
// +------+         +---------------+--------------+-----+
// |unused|   ...   |  card_index1  | card_index0  |SSS00|
// +------+         +---------------+--------------+-----+
class G1CardSetInlinePtr : public StackObj {
  friend class G1CardSetContainersTest;

  typedef G1CardSet::CardSetPtr CardSetPtr;

  CardSetPtr volatile * _value_addr;
  CardSetPtr _value;

  static const uint SizeFieldLen = 3;
  static const uint SizeFieldPos = 2;
  static const uint HeaderSize = G1CardSet::CardSetPtrHeaderSize + SizeFieldLen;

  static const uint BitsInValue = sizeof(CardSetPtr) * BitsPerByte;

  static const uintptr_t SizeFieldMask = (((uint)1 << SizeFieldLen) - 1) << SizeFieldPos;

  static uint8_t card_pos_for(uint const idx, uint const bits_per_card) {
    return (idx * bits_per_card + HeaderSize);
  }

  static CardSetPtr merge(CardSetPtr orig_value, uint card_in_region, uint idx, uint bits_per_card);

  static uint card_at(CardSetPtr value, uint const idx, uint const bits_per_card) {
    uint8_t card_pos = card_pos_for(idx, bits_per_card);
    uint result = ((uintptr_t)value >> card_pos) & (((uintptr_t)1 << bits_per_card) - 1);
    return result;
  }

  uint find(uint const card_idx, uint const bits_per_card, uint start_at, uint num_elems);

public:
  G1CardSetInlinePtr() : _value_addr(nullptr), _value((CardSetPtr)G1CardSet::CardSetInlinePtr) { }

  G1CardSetInlinePtr(CardSetPtr value) : _value_addr(nullptr), _value(value) {
    assert(G1CardSet::card_set_type(_value) == G1CardSet::CardSetInlinePtr, "Value " PTR_FORMAT " is not a valid G1CardSetInPtr.", p2i(_value));
  }

  G1CardSetInlinePtr(CardSetPtr volatile* value_addr, CardSetPtr value) : _value_addr(value_addr), _value(value) {
    assert(G1CardSet::card_set_type(_value) == G1CardSet::CardSetInlinePtr, "Value " PTR_FORMAT " is not a valid G1CardSetInPtr.", p2i(_value));
  }

  G1AddCardResult add(uint const card_idx, uint const bits_per_card, uint const max_cards_in_inline_ptr);

  bool contains(uint const card_idx, uint const bits_per_card);

  template <class CardVisitor>
  void iterate(CardVisitor& found, uint const bits_per_card);

  operator CardSetPtr () { return _value; }

  static uint max_cards_in_inline_ptr(uint bits_per_card) {
    return (BitsInValue - HeaderSize) / bits_per_card;
  }

  static uint num_cards_in(CardSetPtr value) {
    return ((uintptr_t)value & SizeFieldMask) >> SizeFieldPos;
  }
};


// Common base class for card set containers where the memory for the entries is
// managed on the (C-)heap. Depending on the current use, one of the two overlapping
// members are used:
//
// While such an object is assigned to a card set container, we utilize the
// reference count for memory management.
//
// In this case the object is one of three states:
// 1: Live: The object is visible to other threads, thus can
//    safely be accessed by other threads (_ref_count >= 3).
// 2: Dead: The object is visible to only a single thread and may be
//    safely reclaimed (_ref_count == 1).
// 3: Reclaimed: The object's memory has been reclaimed ((_ref_count & 0x1) == 0).
// To maintain these constraints, live objects should have ((_ref_count & 0x1) == 1),
// which requires that we increment the reference counts by 2 starting at _ref_count = 3.
//
// When such an object is on a free list, we reuse the same field for linking
// together those free objects.
//
// All but inline pointers are of this kind. For those, card entries are stored
// directly in the CardSetPtr of the ConcurrentHashTable node.
class G1CardSetContainer {
private:
  union {
    G1CardSetContainer* _next;
    uintptr_t _ref_count;
  };

public:
  G1CardSetContainer() : _ref_count(3) { }

  uintptr_t refcount() const { return Atomic::load_acquire(&_ref_count); }

  bool try_increment_refcount();

  // Decrement refcount potentially while racing increment, so we need
  // to check the value after attempting to decrement.
  uintptr_t decrement_refcount();

  G1CardSetContainer* next() {
    return _next;
  }

  G1CardSetContainer** next_addr() {
    return &_next;
  }

  void set_next(G1CardSetContainer* next) {
    _next = next;
  }

  // Log of largest card index that can be stored in any G1CardSetContainer
  static uint LogCardsPerRegionLimit;
};

class G1CardSetArray : public G1CardSetContainer {
public:
  typedef uint16_t EntryDataType;
  typedef uint EntryCountType;
  using CardSetPtr = G1CardSet::CardSetPtr;
private:
  EntryCountType _size;
  EntryCountType volatile _num_entries;
  EntryDataType _data[2];

  static const EntryCountType LockBitMask = (EntryCountType)1 << (sizeof(EntryCountType) * BitsPerByte - 1);
  static const EntryCountType EntryMask = LockBitMask - 1;

  class G1CardSetArrayLocker : public StackObj {
    EntryCountType volatile* _num_entries_addr;
    EntryCountType _local_num_entries;
  public:
    G1CardSetArrayLocker(EntryCountType volatile* value);

    EntryCountType num_entries() const { return _local_num_entries; }
    void inc_num_entries() {
      assert(((_local_num_entries + 1) & EntryMask) == (EntryCountType)(_local_num_entries + 1), "no overflow" );
      _local_num_entries++;
    }

    ~G1CardSetArrayLocker() {
      Atomic::release_store(_num_entries_addr, _local_num_entries);
    }
  };

  template<typename Derived>
  static size_t header_size_in_bytes_internal() {
    return offset_of(Derived, _data);
  }

public:
  G1CardSetArray(uint const card_in_region, EntryCountType num_elems);

  G1AddCardResult add(uint card_idx);

  bool contains(uint card_idx);

  template <class CardVisitor>
  void iterate(CardVisitor& found);

  size_t num_entries() const { return _num_entries & EntryMask; }
  size_t max_entries() const { return _size; }

  static size_t header_size_in_bytes() { return header_size_in_bytes_internal<G1CardSetArray>(); }

  static size_t size_in_bytes(size_t num_cards) {
    return header_size_in_bytes() + sizeof(EntryDataType) * num_cards;
  }
};

class G1CardSetBitMap : public G1CardSetContainer {
  size_t _num_bits_set;
  BitMap::bm_word_t _bits[1];

  using CardSetPtr = G1CardSet::CardSetPtr;

  template<typename Derived>
  static size_t header_size_in_bytes_internal() {
    return offset_of(Derived, _bits);
  }

public:
  G1CardSetBitMap(uint const card_in_region, uint const size_in_bits);

  G1AddCardResult add(uint card_idx, size_t threshold, size_t size_in_bits);

  bool contains(uint card_idx, size_t size_in_bits) {
    BitMapView bm(_bits, size_in_bits);
    return bm.at(card_idx);
  }

  uint num_bits_set() const { return (uint)_num_bits_set; }

  template <class CardVisitor>
  void iterate(CardVisitor& found, size_t const size_in_bits, uint offset);

  uint next(uint const idx, size_t const size_in_bits) {
    BitMapView bm(_bits, size_in_bits);
    return static_cast<uint>(bm.get_next_one_offset(idx));
  }

  static size_t header_size_in_bytes() { return header_size_in_bytes_internal<G1CardSetBitMap>(); }

  static size_t size_in_bytes(size_t size_in_bits) { return header_size_in_bytes() + BitMap::calc_size_in_words(size_in_bits) * BytesPerWord; }
};

class G1CardSetHowl : public G1CardSetContainer {
public:
  typedef uint EntryCountType;
  using CardSetPtr = G1CardSet::CardSetPtr;
  EntryCountType volatile _num_entries;
private:
  CardSetPtr _buckets[2];
  // Do not add class member variables beyond this point

  template<typename Derived>
  static size_t header_size_in_bytes_internal() {
    return offset_of(Derived, _buckets);
  }

  // Iterates over the given CardSetPtr with at index in this Howl card set,
  // applying a CardOrRangeVisitor on it.
  template <class CardOrRangeVisitor>
  void iterate_cardset(CardSetPtr const card_set, uint index, CardOrRangeVisitor& found, G1CardSetConfiguration* config);

public:
  G1CardSetHowl(EntryCountType card_in_region, G1CardSetConfiguration* config);

  CardSetPtr* get_card_set_addr(EntryCountType index) {
    return &_buckets[index];
  }

  bool contains(uint card_idx, G1CardSetConfiguration* config);

  // Iterates over all CardSetPtrs in this Howl card set, applying a CardOrRangeVisitor
  // on it.
  template <class CardOrRangeVisitor>
  void iterate(CardOrRangeVisitor& found, G1CardSetConfiguration* config);

  // Iterates over all CardSetPtrs in this Howl card set. Calls
  //
  //   void operator ()(CardSetPtr* card_set_addr);
  //
  // on all of them.
  template <class CardSetPtrVisitor>
  void iterate(CardSetPtrVisitor& found, uint num_card_sets);

  static EntryCountType num_buckets(size_t size_in_bits, size_t num_cards_in_array, size_t max_buckets);

  static EntryCountType bitmap_size(size_t size_in_bits, uint num_buckets) {
    EntryCountType num_cards = (EntryCountType)size_in_bits / num_buckets;
    return round_up_power_of_2(num_cards);
  }

  static size_t header_size_in_bytes() { return header_size_in_bytes_internal<G1CardSetHowl>(); }

  static size_t size_in_bytes(size_t num_arrays) {
    return header_size_in_bytes() + sizeof(CardSetPtr) * num_arrays;
  }
};

#endif // SHARE_GC_G1_G1CARDSETCONTAINERS_HPP

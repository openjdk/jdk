/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/bitMap.hpp"
#include "utilities/globalDefinitions.hpp"

// A helper class to encode a few card indexes within a ContainerPtr.
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

  using ContainerPtr = G1CardSet::ContainerPtr;

  ContainerPtr volatile * _value_addr;
  ContainerPtr _value;

  static const uint SizeFieldLen = 3;
  static const uint SizeFieldPos = 2;
  static const uint HeaderSize = G1CardSet::ContainerPtrHeaderSize + SizeFieldLen;

  static const uint BitsInValue = sizeof(ContainerPtr) * BitsPerByte;

  static const uintptr_t SizeFieldMask = (((uint)1 << SizeFieldLen) - 1) << SizeFieldPos;

  static uint card_pos_for(uint const idx, uint const bits_per_card) {
    return (idx * bits_per_card + HeaderSize);
  }

  static ContainerPtr merge(ContainerPtr orig_value, uint card_in_region, uint idx, uint bits_per_card);

  uint find(uint const card_idx, uint const bits_per_card, uint start_at, uint num_cards);

  static ContainerPtr empty_card_set() {
    // Work around https://gcc.gnu.org/bugzilla/show_bug.cgi?id=114573
    // gcc issues -Wzero-as-null-pointer-constant here, even though
    // ContainerInlinePtr is a *non-literal* constant 0.  We cast a non-const
    // copy, and let the compiler's constant propagation optimize into
    // equivalent code.
    static_assert(G1CardSet::ContainerInlinePtr == 0, "unnecessary warning dodge");
    auto value = G1CardSet::ContainerInlinePtr;
    return reinterpret_cast<ContainerPtr>(value);
  }

public:
  G1CardSetInlinePtr() : G1CardSetInlinePtr(empty_card_set()) {}

  explicit G1CardSetInlinePtr(ContainerPtr value) :
    G1CardSetInlinePtr(nullptr, value) {}

  G1CardSetInlinePtr(ContainerPtr volatile* value_addr, ContainerPtr value) : _value_addr(value_addr), _value(value) {
    assert(G1CardSet::container_type(_value) == G1CardSet::ContainerInlinePtr, "Value " PTR_FORMAT " is not a valid G1CardSetInlinePtr.", p2i(_value));
  }

  G1AddCardResult add(uint const card_idx, uint const bits_per_card, uint const max_cards_in_inline_ptr);

  bool contains(uint const card_idx, uint const bits_per_card);

  template <class CardVisitor>
  void iterate(CardVisitor& found, uint const bits_per_card);

  operator ContainerPtr () { return _value; }

  static uint max_cards_in_inline_ptr(uint bits_per_card) {
    return (BitsInValue - HeaderSize) / bits_per_card;
  }

  static uint num_cards_in(ContainerPtr value) {
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
// All but inline pointers are of this kind. For those, card entries are stored
// directly in the ContainerPtr of the ConcurrentHashTable node.
class G1CardSetContainer {
  uintptr_t _ref_count;
protected:
  ~G1CardSetContainer() = default;
public:
  G1CardSetContainer() : _ref_count(3) { }

  uintptr_t refcount() const { return Atomic::load_acquire(&_ref_count); }

  bool try_increment_refcount();

  // Decrement refcount potentially while racing increment, so we need
  // to check the value after attempting to decrement.
  uintptr_t decrement_refcount();

  // Log of largest card index that can be stored in any G1CardSetContainer
  static uint LogCardsPerRegionLimit;

  static uint cards_per_region_limit() { return 1u << LogCardsPerRegionLimit; }
};

class G1CardSetArray : public G1CardSetContainer {
public:
  typedef uint16_t EntryDataType;
  typedef uint EntryCountType;
  using ContainerPtr = G1CardSet::ContainerPtr;
private:
  EntryCountType _size;
  EntryCountType volatile _num_entries;
  // VLA implementation.
  EntryDataType _data[1];

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

  EntryDataType const* base_addr() const;

  EntryDataType const* entry_addr(EntryCountType index) const;

  EntryDataType* entry_addr(EntryCountType index);

  EntryDataType at(EntryCountType index) const;
public:
  G1CardSetArray(uint const card_in_region, EntryCountType num_cards);

  G1AddCardResult add(uint card_idx);

  bool contains(uint card_idx);

  template <class CardVisitor>
  void iterate(CardVisitor& found);

  size_t num_entries() const { return _num_entries & EntryMask; }

  static size_t header_size_in_bytes();

  static size_t size_in_bytes(size_t num_cards) {
    return header_size_in_bytes() + sizeof(EntryDataType) * num_cards;
  }
};

class G1CardSetBitMap : public G1CardSetContainer {
  size_t _num_bits_set;
  BitMap::bm_word_t _bits[1];

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
    return static_cast<uint>(bm.find_first_set_bit(idx));
  }

  static size_t header_size_in_bytes();

  static size_t size_in_bytes(size_t size_in_bits) { return header_size_in_bytes() + BitMap::calc_size_in_words(size_in_bits) * BytesPerWord; }
};

class G1CardSetHowl : public G1CardSetContainer {
public:
  typedef uint EntryCountType;
  using ContainerPtr = G1CardSet::ContainerPtr;
  EntryCountType volatile _num_entries;
private:
  // VLA implementation.
  ContainerPtr _buckets[1];
  // Do not add class member variables beyond this point.

  // Iterates over the given ContainerPtr with at index in this Howl card set,
  // applying a CardOrRangeVisitor on it.
  template <class CardOrRangeVisitor>
  void iterate_cardset(ContainerPtr const container, uint index, CardOrRangeVisitor& found, G1CardSetConfiguration* config);

  ContainerPtr at(EntryCountType index) const;

  ContainerPtr const* buckets() const;

public:
  G1CardSetHowl(EntryCountType card_in_region, G1CardSetConfiguration* config);

  ContainerPtr const* container_addr(EntryCountType index) const;

  ContainerPtr* container_addr(EntryCountType index);

  bool contains(uint card_idx, G1CardSetConfiguration* config);
  // Iterates over all ContainerPtrs in this Howl card set, applying a CardOrRangeVisitor
  // on it.
  template <class CardOrRangeVisitor>
  void iterate(CardOrRangeVisitor& found, G1CardSetConfiguration* config);

  // Iterates over all ContainerPtrs in this Howl card set. Calls
  //
  //   void operator ()(ContainerPtr* card_set_addr);
  //
  // on all of them.
  template <class ContainerPtrVisitor>
  void iterate(ContainerPtrVisitor& found, uint num_card_sets);

  static EntryCountType num_buckets(size_t size_in_bits, size_t num_cards_in_array, size_t max_buckets);

  static EntryCountType bitmap_size(size_t size_in_bits, uint num_buckets) {
    EntryCountType num_cards = (EntryCountType)size_in_bits / num_buckets;
    return round_up_power_of_2(num_cards);
  }

  static size_t header_size_in_bytes();

  static size_t size_in_bytes(size_t num_arrays) {
    return header_size_in_bytes() + sizeof(ContainerPtr) * num_arrays;
  }
};

#endif // SHARE_GC_G1_G1CARDSETCONTAINERS_HPP

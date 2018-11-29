/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_SPARSEPRT_HPP
#define SHARE_VM_GC_G1_SPARSEPRT_HPP

#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "memory/allocation.hpp"
#include "runtime/mutex.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

// Sparse remembered set for a heap region (the "owning" region).  Maps
// indices of other regions to short sequences of cards in the other region
// that might contain pointers into the owner region.

class SparsePRTEntry: public CHeapObj<mtGC> {
private:
  // The type of a card entry.
  typedef uint16_t card_elem_t;

  // We need to make sizeof(SparsePRTEntry) an even multiple of maximum member size,
  // in order to force correct alignment that could otherwise cause SIGBUS errors
  // when reading the member variables. This calculates the minimum number of card
  // array elements required to get that alignment.
  static const size_t card_array_alignment = sizeof(int) / sizeof(card_elem_t);

  RegionIdx_t _region_ind;
  int         _next_index;
  int         _next_null;
  // The actual cards stored in this array.
  // WARNING: Don't put any data members beyond this line. Card array has, in fact, variable length.
  // It should always be the last data member.
  card_elem_t _cards[card_array_alignment];

  // Copy the current entry's cards into "cards".
  inline void copy_cards(card_elem_t* cards) const;
public:
  // Returns the size of the entry, used for entry allocation.
  static size_t size() { return sizeof(SparsePRTEntry) + sizeof(card_elem_t) * (cards_num() - card_array_alignment); }
  // Returns the size of the card array.
  static int cards_num() {
    return align_up((int)G1RSetSparseRegionEntries, (int)card_array_alignment);
  }

  // Set the region_ind to the given value, and delete all cards.
  inline void init(RegionIdx_t region_ind);

  RegionIdx_t r_ind() const { return _region_ind; }
  bool valid_entry() const { return r_ind() >= 0; }
  void set_r_ind(RegionIdx_t rind) { _region_ind = rind; }

  int next_index() const { return _next_index; }
  int* next_index_addr() { return &_next_index; }
  void set_next_index(int ni) { _next_index = ni; }

  // Returns "true" iff the entry contains the given card index.
  inline bool contains_card(CardIdx_t card_index) const;

  // Returns the number of non-NULL card entries.
  inline int num_valid_cards() const { return _next_null; }

  // Requires that the entry not contain the given card index.  If there is
  // space available, add the given card index to the entry and return
  // "true"; otherwise, return "false" to indicate that the entry is full.
  enum AddCardResult {
    overflow,
    found,
    added
  };
  inline AddCardResult add_card(CardIdx_t card_index);

  // Copy the current entry's cards into the "_card" array of "e."
  inline void copy_cards(SparsePRTEntry* e) const;

  inline CardIdx_t card(int i) const {
    assert(i >= 0, "must be nonnegative");
    assert(i < cards_num(), "range checking");
    return (CardIdx_t)_cards[i];
  }
};

class RSHashTable : public CHeapObj<mtGC> {

  friend class RSHashTableIter;


  // Inverse maximum hash table occupancy used.
  static float TableOccupancyFactor;

  size_t _num_entries;

  size_t _capacity;
  size_t _capacity_mask;
  size_t _occupied_entries;
  size_t _occupied_cards;

  SparsePRTEntry* _entries;
  int* _buckets;
  int  _free_region;
  int  _free_list;

  // Requires that the caller hold a lock preventing parallel modifying
  // operations, and that the the table be less than completely full.  If
  // an entry for "region_ind" is already in the table, finds it and
  // returns its address; otherwise allocates, initializes, inserts and
  // returns a new entry for "region_ind".
  SparsePRTEntry* entry_for_region_ind_create(RegionIdx_t region_ind);

  // Returns the index of the next free entry in "_entries".
  int alloc_entry();
  // Declares the entry "fi" to be free.  (It must have already been
  // deleted from any bucket lists.
  void free_entry(int fi);

public:
  RSHashTable(size_t capacity);
  ~RSHashTable();

  static const int NullEntry = -1;

  bool should_expand() const { return _occupied_entries == _num_entries; }

  // Attempts to ensure that the given card_index in the given region is in
  // the sparse table.  If successful (because the card was already
  // present, or because it was successfully added) returns "true".
  // Otherwise, returns "false" to indicate that the addition would
  // overflow the entry for the region.  The caller must transfer these
  // entries to a larger-capacity representation.
  bool add_card(RegionIdx_t region_id, CardIdx_t card_index);

  bool delete_entry(RegionIdx_t region_id);

  bool contains_card(RegionIdx_t region_id, CardIdx_t card_index) const;

  void add_entry(SparsePRTEntry* e);

  SparsePRTEntry* get_entry(RegionIdx_t region_id) const;

  void clear();

  size_t capacity() const      { return _capacity; }
  size_t capacity_mask() const { return _capacity_mask;  }
  size_t occupied_entries() const { return _occupied_entries; }
  size_t occupied_cards() const   { return _occupied_cards; }
  size_t mem_size() const;
  // The number of SparsePRTEntry instances available.
  size_t num_entries() const { return _num_entries; }

  SparsePRTEntry* entry(int i) const {
    assert(i >= 0 && (size_t)i < _num_entries, "precondition");
    return (SparsePRTEntry*)((char*)_entries + SparsePRTEntry::size() * i);
  }

  void print();
};

// This is embedded in HRRS iterator.
class RSHashTableIter {
  // Return value indicating "invalid/no card".
  static const int NoCardFound = -1;

  int _tbl_ind;         // [-1, 0.._rsht->_capacity)
  int _bl_ind;          // [-1, 0.._rsht->_capacity)
  short _card_ind;      // [0..SparsePRTEntry::cards_num())
  RSHashTable* _rsht;

  // If the bucket list pointed to by _bl_ind contains a card, sets
  // _bl_ind to the index of that entry,
  // Returns the card found if there is, otherwise returns InvalidCard.
  CardIdx_t find_first_card_in_list();

  // Computes the proper card index for the card whose offset in the
  // current region (as indicated by _bl_ind) is "ci".
  // This is subject to errors when there is iteration concurrent with
  // modification, but these errors should be benign.
  size_t compute_card_ind(CardIdx_t ci);

public:
  RSHashTableIter(RSHashTable* rsht) :
    _tbl_ind(RSHashTable::NullEntry), // So that first increment gets to 0.
    _bl_ind(RSHashTable::NullEntry),
    _card_ind((SparsePRTEntry::cards_num() - 1)),
    _rsht(rsht) {}

  bool has_next(size_t& card_index);
};

// Concurrent access to a SparsePRT must be serialized by some external mutex.

class SparsePRTIter;

class SparsePRT {
  friend class SparsePRTIter;

  RSHashTable* _table;

  enum SomeAdditionalPrivateConstants {
    InitialCapacity = 16
  };

  void expand();

public:
  SparsePRT();
  ~SparsePRT();

  size_t occupied() const { return _table->occupied_cards(); }
  size_t mem_size() const;

  // Attempts to ensure that the given card_index in the given region is in
  // the sparse table.  If successful (because the card was already
  // present, or because it was successfully added) returns "true".
  // Otherwise, returns "false" to indicate that the addition would
  // overflow the entry for the region.  The caller must transfer these
  // entries to a larger-capacity representation.
  bool add_card(RegionIdx_t region_id, CardIdx_t card_index);

  // Return the pointer to the entry associated with the given region.
  SparsePRTEntry* get_entry(RegionIdx_t region_ind);

  // If there is an entry for "region_ind", removes it and return "true";
  // otherwise returns "false."
  bool delete_entry(RegionIdx_t region_ind);

  // Clear the table, and reinitialize to initial capacity.
  void clear();

  bool contains_card(RegionIdx_t region_id, CardIdx_t card_index) const {
    return _table->contains_card(region_id, card_index);
  }
};

class SparsePRTIter: public RSHashTableIter {
public:
  SparsePRTIter(const SparsePRT* sprt) :
    RSHashTableIter(sprt->_table) { }

  bool has_next(size_t& card_index) {
    return RSHashTableIter::has_next(card_index);
  }
};

#endif // SHARE_VM_GC_G1_SPARSEPRT_HPP

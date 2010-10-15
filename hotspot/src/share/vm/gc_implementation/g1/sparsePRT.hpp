/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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

// Sparse remembered set for a heap region (the "owning" region).  Maps
// indices of other regions to short sequences of cards in the other region
// that might contain pointers into the owner region.

// These tables only expand while they are accessed in parallel --
// deletions may be done in single-threaded code.  This allows us to allow
// unsynchronized reads/iterations, as long as expansions caused by
// insertions only enqueue old versions for deletions, but do not delete
// old versions synchronously.

class SparsePRTEntry: public CHeapObj {
public:
  enum SomePublicConstants {
    NullEntry     = -1,
    UnrollFactor  =  4
  };
private:
  RegionIdx_t _region_ind;
  int         _next_index;
  CardIdx_t   _cards[1];
  // WARNING: Don't put any data members beyond this line. Card array has, in fact, variable length.
  // It should always be the last data member.
public:
  // Returns the size of the entry, used for entry allocation.
  static size_t size() { return sizeof(SparsePRTEntry) + sizeof(CardIdx_t) * (cards_num() - 1); }
  // Returns the size of the card array.
  static int cards_num() {
    // The number of cards should be a multiple of 4, because that's our current
    // unrolling factor.
    static const int s = MAX2<int>(G1RSetSparseRegionEntries & ~(UnrollFactor - 1), UnrollFactor);
    return s;
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
  inline int num_valid_cards() const;

  // Requires that the entry not contain the given card index.  If there is
  // space available, add the given card index to the entry and return
  // "true"; otherwise, return "false" to indicate that the entry is full.
  enum AddCardResult {
    overflow,
    found,
    added
  };
  inline AddCardResult add_card(CardIdx_t card_index);

  // Copy the current entry's cards into "cards".
  inline void copy_cards(CardIdx_t* cards) const;
  // Copy the current entry's cards into the "_card" array of "e."
  inline void copy_cards(SparsePRTEntry* e) const;

  inline CardIdx_t card(int i) const { return _cards[i]; }
};


class RSHashTable : public CHeapObj {

  friend class RSHashTableIter;

  enum SomePrivateConstants {
    NullEntry = -1
  };

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
  // returns its address; otherwise returns "NULL."
  SparsePRTEntry* entry_for_region_ind(RegionIdx_t region_ind) const;

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

  // Attempts to ensure that the given card_index in the given region is in
  // the sparse table.  If successful (because the card was already
  // present, or because it was successfullly added) returns "true".
  // Otherwise, returns "false" to indicate that the addition would
  // overflow the entry for the region.  The caller must transfer these
  // entries to a larger-capacity representation.
  bool add_card(RegionIdx_t region_id, CardIdx_t card_index);

  bool get_cards(RegionIdx_t region_id, CardIdx_t* cards);

  bool delete_entry(RegionIdx_t region_id);

  bool contains_card(RegionIdx_t region_id, CardIdx_t card_index) const;

  void add_entry(SparsePRTEntry* e);

  SparsePRTEntry* get_entry(RegionIdx_t region_id);

  void clear();

  size_t capacity() const      { return _capacity;       }
  size_t capacity_mask() const { return _capacity_mask;  }
  size_t occupied_entries() const { return _occupied_entries; }
  size_t occupied_cards() const   { return _occupied_cards;   }
  size_t mem_size() const;

  SparsePRTEntry* entry(int i) const { return (SparsePRTEntry*)((char*)_entries + SparsePRTEntry::size() * i); }

  void print();
};

// ValueObj because will be embedded in HRRS iterator.
class RSHashTableIter VALUE_OBJ_CLASS_SPEC {
  int _tbl_ind;         // [-1, 0.._rsht->_capacity)
  int _bl_ind;          // [-1, 0.._rsht->_capacity)
  short _card_ind;      // [0..SparsePRTEntry::cards_num())
  RSHashTable* _rsht;

  // If the bucket list pointed to by _bl_ind contains a card, sets
  // _bl_ind to the index of that entry, and returns the card.
  // Otherwise, returns SparseEntry::NullEntry.
  CardIdx_t find_first_card_in_list();

  // Computes the proper card index for the card whose offset in the
  // current region (as indicated by _bl_ind) is "ci".
  // This is subject to errors when there is iteration concurrent with
  // modification, but these errors should be benign.
  size_t compute_card_ind(CardIdx_t ci);

public:
  RSHashTableIter() :
    _tbl_ind(RSHashTable::NullEntry),
    _bl_ind(RSHashTable::NullEntry),
    _card_ind((SparsePRTEntry::cards_num() - 1)),
    _rsht(NULL) {}

  void init(RSHashTable* rsht) {
    _rsht = rsht;
    _tbl_ind = -1; // So that first increment gets to 0.
    _bl_ind = RSHashTable::NullEntry;
    _card_ind = (SparsePRTEntry::cards_num() - 1);
  }

  bool has_next(size_t& card_index);
};

// Concurrent accesss to a SparsePRT must be serialized by some external
// mutex.

class SparsePRTIter;

class SparsePRT VALUE_OBJ_CLASS_SPEC {
  //  Iterations are done on the _cur hash table, since they only need to
  //  see entries visible at the start of a collection pause.
  //  All other operations are done using the _next hash table.
  RSHashTable* _cur;
  RSHashTable* _next;

  HeapRegion* _hr;

  enum SomeAdditionalPrivateConstants {
    InitialCapacity = 16
  };

  void expand();

  bool _expanded;

  bool expanded() { return _expanded; }
  void set_expanded(bool b) { _expanded = b; }

  SparsePRT* _next_expanded;

  SparsePRT* next_expanded() { return _next_expanded; }
  void set_next_expanded(SparsePRT* nxt) { _next_expanded = nxt; }

  static SparsePRT* _head_expanded_list;

public:
  SparsePRT(HeapRegion* hr);

  ~SparsePRT();

  size_t occupied() const { return _next->occupied_cards(); }
  size_t mem_size() const;

  // Attempts to ensure that the given card_index in the given region is in
  // the sparse table.  If successful (because the card was already
  // present, or because it was successfullly added) returns "true".
  // Otherwise, returns "false" to indicate that the addition would
  // overflow the entry for the region.  The caller must transfer these
  // entries to a larger-capacity representation.
  bool add_card(RegionIdx_t region_id, CardIdx_t card_index);

  // If the table hold an entry for "region_ind",  Copies its
  // cards into "cards", which must be an array of length at least
  // "SparePRTEntry::cards_num()", and returns "true"; otherwise,
  // returns "false".
  bool get_cards(RegionIdx_t region_ind, CardIdx_t* cards);

  // Return the pointer to the entry associated with the given region.
  SparsePRTEntry* get_entry(RegionIdx_t region_ind);

  // If there is an entry for "region_ind", removes it and return "true";
  // otherwise returns "false."
  bool delete_entry(RegionIdx_t region_ind);

  // Clear the table, and reinitialize to initial capacity.
  void clear();

  // Ensure that "_cur" and "_next" point to the same table.
  void cleanup();

  // Clean up all tables on the expanded list.  Called single threaded.
  static void cleanup_all();
  RSHashTable* cur() const { return _cur; }

  void init_iterator(SparsePRTIter* sprt_iter);

  static void add_to_expanded_list(SparsePRT* sprt);
  static SparsePRT* get_from_expanded_list();

  bool contains_card(RegionIdx_t region_id, CardIdx_t card_index) const {
    return _next->contains_card(region_id, card_index);
  }
};


class SparsePRTIter: public RSHashTableIter {
public:
  SparsePRTIter() : RSHashTableIter() { }

  void init(const SparsePRT* sprt) {
    RSHashTableIter::init(sprt->cur());
  }
  bool has_next(size_t& card_index) {
    return RSHashTableIter::has_next(card_index);
  }
};

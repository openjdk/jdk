/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
    CardsPerEntry = (short)4,
    NullEntry = (short)-1,
    DeletedEntry = (short)-2
  };

private:
  short _region_ind;
  short _next_index;
  short _cards[CardsPerEntry];

public:

  // Set the region_ind to the given value, and delete all cards.
  inline void init(short region_ind);

  short r_ind() const { return _region_ind; }
  bool valid_entry() const { return r_ind() >= 0; }
  void set_r_ind(short rind) { _region_ind = rind; }

  short next_index() const { return _next_index; }
  short* next_index_addr() { return &_next_index; }
  void set_next_index(short ni) { _next_index = ni; }

  // Returns "true" iff the entry contains the given card index.
  inline bool contains_card(short card_index) const;

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
  inline AddCardResult add_card(short card_index);

  // Copy the current entry's cards into "cards".
  inline void copy_cards(short* cards) const;
  // Copy the current entry's cards into the "_card" array of "e."
  inline void copy_cards(SparsePRTEntry* e) const;

  inline short card(int i) const { return _cards[i]; }
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
  short* _buckets;
  short  _free_region;
  short  _free_list;

  static RSHashTable* _head_deleted_list;
  RSHashTable* _next_deleted;
  RSHashTable* next_deleted() { return _next_deleted; }
  void set_next_deleted(RSHashTable* rsht) { _next_deleted = rsht; }
  bool _deleted;
  void set_deleted(bool b) { _deleted = b; }

  // Requires that the caller hold a lock preventing parallel modifying
  // operations, and that the the table be less than completely full.  If
  // an entry for "region_ind" is already in the table, finds it and
  // returns its address; otherwise returns "NULL."
  SparsePRTEntry* entry_for_region_ind(short region_ind) const;

  // Requires that the caller hold a lock preventing parallel modifying
  // operations, and that the the table be less than completely full.  If
  // an entry for "region_ind" is already in the table, finds it and
  // returns its address; otherwise allocates, initializes, inserts and
  // returns a new entry for "region_ind".
  SparsePRTEntry* entry_for_region_ind_create(short region_ind);

  // Returns the index of the next free entry in "_entries".
  short alloc_entry();
  // Declares the entry "fi" to be free.  (It must have already been
  // deleted from any bucket lists.
  void free_entry(short fi);

public:
  RSHashTable(size_t capacity);
  ~RSHashTable();

  // Attempts to ensure that the given card_index in the given region is in
  // the sparse table.  If successful (because the card was already
  // present, or because it was successfullly added) returns "true".
  // Otherwise, returns "false" to indicate that the addition would
  // overflow the entry for the region.  The caller must transfer these
  // entries to a larger-capacity representation.
  bool add_card(short region_id, short card_index);

  bool get_cards(short region_id, short* cards);
  bool delete_entry(short region_id);

  bool contains_card(short region_id, short card_index) const;

  void add_entry(SparsePRTEntry* e);

  void clear();

  size_t capacity() const      { return _capacity;       }
  size_t capacity_mask() const { return _capacity_mask;  }
  size_t occupied_entries() const { return _occupied_entries; }
  size_t occupied_cards() const   { return _occupied_cards;   }
  size_t mem_size() const;
  bool deleted() { return _deleted; }

  SparsePRTEntry* entry(int i) const { return &_entries[i]; }

  void print();

  static void add_to_deleted_list(RSHashTable* rsht);
  static RSHashTable* get_from_deleted_list();


};

  // ValueObj because will be embedded in HRRS iterator.
class RSHashTableIter VALUE_OBJ_CLASS_SPEC {
    short _tbl_ind;
    short _bl_ind;
    short _card_ind;
    RSHashTable* _rsht;
    size_t _heap_bot_card_ind;

    enum SomePrivateConstants {
      CardsPerRegion = HeapRegion::GrainBytes >> CardTableModRefBS::card_shift
    };

    // If the bucket list pointed to by _bl_ind contains a card, sets
    // _bl_ind to the index of that entry, and returns the card.
    // Otherwise, returns SparseEntry::NullEnty.
    short find_first_card_in_list();
    // Computes the proper card index for the card whose offset in the
    // current region (as indicated by _bl_ind) is "ci".
    // This is subject to errors when there is iteration concurrent with
    // modification, but these errors should be benign.
    size_t compute_card_ind(short ci);

  public:
    RSHashTableIter(size_t heap_bot_card_ind) :
      _tbl_ind(RSHashTable::NullEntry),
      _bl_ind(RSHashTable::NullEntry),
      _card_ind((SparsePRTEntry::CardsPerEntry-1)),
      _rsht(NULL),
      _heap_bot_card_ind(heap_bot_card_ind)
    {}

    void init(RSHashTable* rsht) {
      _rsht = rsht;
      _tbl_ind = -1; // So that first increment gets to 0.
      _bl_ind = RSHashTable::NullEntry;
      _card_ind = (SparsePRTEntry::CardsPerEntry-1);
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
  bool add_card(short region_id, short card_index);

  // If the table hold an entry for "region_ind",  Copies its
  // cards into "cards", which must be an array of length at least
  // "CardsPerEntry", and returns "true"; otherwise, returns "false".
  bool get_cards(short region_ind, short* cards);

  // If there is an entry for "region_ind", removes it and return "true";
  // otherwise returns "false."
  bool delete_entry(short region_ind);

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

  bool contains_card(short region_id, short card_index) const {
    return _next->contains_card(region_id, card_index);
  }

#if 0
  void verify_is_cleared();
  void print();
#endif
};


class SparsePRTIter: public /* RSHashTable:: */RSHashTableIter {
public:
  SparsePRTIter(size_t heap_bot_card_ind) :
    /* RSHashTable:: */RSHashTableIter(heap_bot_card_ind)
  {}

  void init(const SparsePRT* sprt) {
    RSHashTableIter::init(sprt->cur());
  }
  bool has_next(size_t& card_index) {
    return RSHashTableIter::has_next(card_index);
  }
};

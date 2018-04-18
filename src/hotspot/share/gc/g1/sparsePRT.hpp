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

// These tables only expand while they are accessed in parallel --
// deletions may be done in single-threaded code.  This allows us to allow
// unsynchronized reads/iterations, as long as expansions caused by
// insertions only enqueue old versions for deletions, but do not delete
// old versions synchronously.

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

  bool get_cards(RegionIdx_t region_id, CardIdx_t* cards);

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
class SparsePRTCleanupTask;

class SparsePRT {
  friend class SparsePRTCleanupTask;

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

  bool should_be_on_expanded_list();

  static SparsePRT* volatile _head_expanded_list;

public:
  SparsePRT(HeapRegion* hr);

  ~SparsePRT();

  size_t occupied() const { return _next->occupied_cards(); }
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

  // Ensure that "_cur" and "_next" point to the same table.
  void cleanup();

  // Clean up all tables on the expanded list.  Called single threaded.
  static void cleanup_all();
  RSHashTable* cur() const { return _cur; }

  static void add_to_expanded_list(SparsePRT* sprt);
  static SparsePRT* get_from_expanded_list();

  // The purpose of these three methods is to help the GC workers
  // during the cleanup pause to recreate the expanded list, purging
  // any tables from it that belong to regions that are freed during
  // cleanup (if we don't purge those tables, there is a race that
  // causes various crashes; see CR 7014261).
  //
  // We chose to recreate the expanded list, instead of purging
  // entries from it by iterating over it, to avoid this serial phase
  // at the end of the cleanup pause.
  //
  // The three methods below work as follows:
  // * reset_for_cleanup_tasks() : Nulls the expanded list head at the
  //   start of the cleanup pause.
  // * do_cleanup_work() : Called by the cleanup workers for every
  //   region that is not free / is being freed by the cleanup
  //   pause. It creates a list of expanded tables whose head / tail
  //   are on the thread-local SparsePRTCleanupTask object.
  // * finish_cleanup_task() : Called by the cleanup workers after
  //   they complete their cleanup task. It adds the local list into
  //   the global expanded list. It assumes that the
  //   ParGCRareEvent_lock is being held to ensure MT-safety.
  static void reset_for_cleanup_tasks();
  void do_cleanup_work(SparsePRTCleanupTask* sprt_cleanup_task);
  static void finish_cleanup_task(SparsePRTCleanupTask* sprt_cleanup_task);

  bool contains_card(RegionIdx_t region_id, CardIdx_t card_index) const {
    return _next->contains_card(region_id, card_index);
  }
};

class SparsePRTIter: public RSHashTableIter {
public:
  SparsePRTIter(const SparsePRT* sprt) :
    RSHashTableIter(sprt->cur()) {}

  bool has_next(size_t& card_index) {
    return RSHashTableIter::has_next(card_index);
  }
};

// This allows each worker during a cleanup pause to create a
// thread-local list of sparse tables that have been expanded and need
// to be processed at the beginning of the next GC pause. This lists
// are concatenated into the single expanded list at the end of the
// cleanup pause.
class SparsePRTCleanupTask {
private:
  SparsePRT* _head;
  SparsePRT* _tail;

public:
  SparsePRTCleanupTask() : _head(NULL), _tail(NULL) { }

  void add(SparsePRT* sprt);
  SparsePRT* head() { return _head; }
  SparsePRT* tail() { return _tail; }
};

#endif // SHARE_VM_GC_G1_SPARSEPRT_HPP

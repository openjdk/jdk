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

#include "precompiled.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionBounds.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/g1/sparsePRT.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/space.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/mutexLocker.hpp"

// Check that the size of the SparsePRTEntry is evenly divisible by the maximum
// member type to avoid SIGBUS when accessing them.
STATIC_ASSERT(sizeof(SparsePRTEntry) % sizeof(int) == 0);

void SparsePRTEntry::init(RegionIdx_t region_ind) {
  // Check that the card array element type can represent all cards in the region.
  // Choose a large SparsePRTEntry::card_elem_t (e.g. CardIdx_t) if required.
  assert(((size_t)1 << (sizeof(SparsePRTEntry::card_elem_t) * BitsPerByte)) *
         G1CardTable::card_size >= HeapRegionBounds::max_size(), "precondition");
  assert(G1RSetSparseRegionEntries > 0, "precondition");
  _region_ind = region_ind;
  _next_index = RSHashTable::NullEntry;
  _next_null = 0;
}

bool SparsePRTEntry::contains_card(CardIdx_t card_index) const {
  for (int i = 0; i < num_valid_cards(); i++) {
    if (card(i) == card_index) {
      return true;
    }
  }
  return false;
}

SparsePRTEntry::AddCardResult SparsePRTEntry::add_card(CardIdx_t card_index) {
  for (int i = 0; i < num_valid_cards(); i++) {
    if (card(i) == card_index) {
      return found;
    }
  }
  if (num_valid_cards() < cards_num() - 1) {
    _cards[_next_null] = (card_elem_t)card_index;
    _next_null++;
    return added;
   }
  // Otherwise, we're full.
  return overflow;
}

void SparsePRTEntry::copy_cards(card_elem_t* cards) const {
  memcpy(cards, _cards, cards_num() * sizeof(card_elem_t));
}

void SparsePRTEntry::copy_cards(SparsePRTEntry* e) const {
  copy_cards(e->_cards);
  assert(_next_null >= 0, "invariant");
  assert(_next_null <= cards_num(), "invariant");
  e->_next_null = _next_null;
}

// ----------------------------------------------------------------------

float RSHashTable::TableOccupancyFactor = 0.5f;

RSHashTable::RSHashTable(size_t capacity) :
  _num_entries(0),
  _capacity(capacity),
  _capacity_mask(capacity-1),
  _occupied_entries(0),
  _occupied_cards(0),
  _entries(NULL),
  _buckets(NEW_C_HEAP_ARRAY(int, capacity, mtGC)),
  _free_region(0),
  _free_list(NullEntry)
{
  _num_entries = (capacity * TableOccupancyFactor) + 1;
  _entries = (SparsePRTEntry*)NEW_C_HEAP_ARRAY(char, _num_entries * SparsePRTEntry::size(), mtGC);
  clear();
}

RSHashTable::~RSHashTable() {
  if (_entries != NULL) {
    FREE_C_HEAP_ARRAY(SparsePRTEntry, _entries);
    _entries = NULL;
  }
  if (_buckets != NULL) {
    FREE_C_HEAP_ARRAY(int, _buckets);
    _buckets = NULL;
  }
}

void RSHashTable::clear() {
  _occupied_entries = 0;
  _occupied_cards = 0;
  guarantee(_entries != NULL, "INV");
  guarantee(_buckets != NULL, "INV");

  guarantee(_capacity <= ((size_t)1 << (sizeof(int)*BitsPerByte-1)) - 1,
                "_capacity too large");

  // This will put -1 == NullEntry in the key field of all entries.
  memset((void*)_entries, NullEntry, _num_entries * SparsePRTEntry::size());
  memset((void*)_buckets, NullEntry, _capacity * sizeof(int));
  _free_list = NullEntry;
  _free_region = 0;
}

bool RSHashTable::add_card(RegionIdx_t region_ind, CardIdx_t card_index) {
  SparsePRTEntry* e = entry_for_region_ind_create(region_ind);
  assert(e != NULL && e->r_ind() == region_ind,
         "Postcondition of call above.");
  SparsePRTEntry::AddCardResult res = e->add_card(card_index);
  if (res == SparsePRTEntry::added) _occupied_cards++;
  assert(e->num_valid_cards() > 0, "Postcondition");
  return res != SparsePRTEntry::overflow;
}

SparsePRTEntry* RSHashTable::get_entry(RegionIdx_t region_ind) const {
  int ind = (int) (region_ind & capacity_mask());
  int cur_ind = _buckets[ind];
  SparsePRTEntry* cur;
  while (cur_ind != NullEntry &&
         (cur = entry(cur_ind))->r_ind() != region_ind) {
    cur_ind = cur->next_index();
  }

  if (cur_ind == NullEntry) return NULL;
  // Otherwise...
  assert(cur->r_ind() == region_ind, "Postcondition of loop + test above.");
  assert(cur->num_valid_cards() > 0, "Inv");
  return cur;
}

bool RSHashTable::delete_entry(RegionIdx_t region_ind) {
  int ind = (int) (region_ind & capacity_mask());
  int* prev_loc = &_buckets[ind];
  int cur_ind = *prev_loc;
  SparsePRTEntry* cur;
  while (cur_ind != NullEntry &&
         (cur = entry(cur_ind))->r_ind() != region_ind) {
    prev_loc = cur->next_index_addr();
    cur_ind = *prev_loc;
  }

  if (cur_ind == NullEntry) return false;
  // Otherwise, splice out "cur".
  *prev_loc = cur->next_index();
  _occupied_cards -= cur->num_valid_cards();
  free_entry(cur_ind);
  _occupied_entries--;
  return true;
}

SparsePRTEntry*
RSHashTable::entry_for_region_ind_create(RegionIdx_t region_ind) {
  SparsePRTEntry* res = get_entry(region_ind);
  if (res == NULL) {
    int new_ind = alloc_entry();
    res = entry(new_ind);
    res->init(region_ind);
    // Insert at front.
    int ind = (int) (region_ind & capacity_mask());
    res->set_next_index(_buckets[ind]);
    _buckets[ind] = new_ind;
    _occupied_entries++;
  }
  return res;
}

int RSHashTable::alloc_entry() {
  int res;
  if (_free_list != NullEntry) {
    res = _free_list;
    _free_list = entry(res)->next_index();
    return res;
  } else if ((size_t)_free_region < _num_entries) {
    res = _free_region;
    _free_region++;
    return res;
  } else {
    return NullEntry;
  }
}

void RSHashTable::free_entry(int fi) {
  entry(fi)->set_next_index(_free_list);
  _free_list = fi;
}

void RSHashTable::add_entry(SparsePRTEntry* e) {
  assert(e->num_valid_cards() > 0, "Precondition.");
  SparsePRTEntry* e2 = entry_for_region_ind_create(e->r_ind());
  e->copy_cards(e2);
  _occupied_cards += e2->num_valid_cards();
  assert(e2->num_valid_cards() > 0, "Postcondition.");
}

CardIdx_t RSHashTableIter::find_first_card_in_list() {
  while (_bl_ind != RSHashTable::NullEntry) {
    SparsePRTEntry* sparse_entry = _rsht->entry(_bl_ind);
    if (sparse_entry->num_valid_cards() > 0) {
      return sparse_entry->card(0);
    } else {
      _bl_ind = sparse_entry->next_index();
    }
  }
  // Otherwise, none found:
  return NoCardFound;
}

size_t RSHashTableIter::compute_card_ind(CardIdx_t ci) {
  return (_rsht->entry(_bl_ind)->r_ind() * HeapRegion::CardsPerRegion) + ci;
}

bool RSHashTableIter::has_next(size_t& card_index) {
  _card_ind++;
  if (_bl_ind >= 0) {
    SparsePRTEntry* e = _rsht->entry(_bl_ind);
    if (_card_ind < e->num_valid_cards()) {
      CardIdx_t ci = e->card(_card_ind);
      card_index = compute_card_ind(ci);
      return true;
    }
  }

  // Otherwise, must find the next valid entry.
  _card_ind = 0;

  if (_bl_ind != RSHashTable::NullEntry) {
      _bl_ind = _rsht->entry(_bl_ind)->next_index();
      CardIdx_t ci = find_first_card_in_list();
      if (ci != NoCardFound) {
        card_index = compute_card_ind(ci);
        return true;
      }
  }
  // If we didn't return above, must go to the next non-null table index.
  _tbl_ind++;
  while ((size_t)_tbl_ind < _rsht->capacity()) {
    _bl_ind = _rsht->_buckets[_tbl_ind];
    CardIdx_t ci = find_first_card_in_list();
    if (ci != NoCardFound) {
      card_index = compute_card_ind(ci);
      return true;
    }
    // Otherwise, try next entry.
    _tbl_ind++;
  }
  // Otherwise, there were no entry.
  return false;
}

bool RSHashTable::contains_card(RegionIdx_t region_index, CardIdx_t card_index) const {
  SparsePRTEntry* e = get_entry(region_index);
  return (e != NULL && e->contains_card(card_index));
}

size_t RSHashTable::mem_size() const {
  return sizeof(RSHashTable) +
    _num_entries * (SparsePRTEntry::size() + sizeof(int));
}

// ----------------------------------------------------------------------

SparsePRT::SparsePRT() :
  _table(new RSHashTable(InitialCapacity)) {
}


SparsePRT::~SparsePRT() {
  delete _table;
}


size_t SparsePRT::mem_size() const {
  // We ignore "_cur" here, because it either = _next, or else it is
  // on the deleted list.
  return sizeof(SparsePRT) + _table->mem_size();
}

bool SparsePRT::add_card(RegionIdx_t region_id, CardIdx_t card_index) {
  if (_table->should_expand()) {
    expand();
  }
  return _table->add_card(region_id, card_index);
}

SparsePRTEntry* SparsePRT::get_entry(RegionIdx_t region_id) {
  return _table->get_entry(region_id);
}

bool SparsePRT::delete_entry(RegionIdx_t region_id) {
  return _table->delete_entry(region_id);
}

void SparsePRT::clear() {
  // If the entry table is not at initial capacity, just create a new one.
  if (_table->capacity() != InitialCapacity) {
    delete _table;
    _table = new RSHashTable(InitialCapacity);
  } else {
    _table->clear();
  }
}

void SparsePRT::expand() {
  RSHashTable* last = _table;
  _table = new RSHashTable(last->capacity() * 2);
  for (size_t i = 0; i < last->num_entries(); i++) {
    SparsePRTEntry* e = last->entry((int)i);
    if (e->valid_entry()) {
      _table->add_entry(e);
    }
  }
  delete last;
}

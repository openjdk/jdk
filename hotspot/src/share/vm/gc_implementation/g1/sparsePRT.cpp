/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_sparsePRT.cpp.incl"

#define SPARSE_PRT_VERBOSE 0

#define UNROLL_CARD_LOOPS 1

void SparsePRT::init_iterator(SparsePRTIter* sprt_iter) {
    sprt_iter->init(this);
}

void SparsePRTEntry::init(RegionIdx_t region_ind) {
  _region_ind = region_ind;
  _next_index = NullEntry;
#if UNROLL_CARD_LOOPS
  assert(CardsPerEntry == 4, "Assumption.  If changes, un-unroll.");
  _cards[0] = NullEntry;
  _cards[1] = NullEntry;
  _cards[2] = NullEntry;
  _cards[3] = NullEntry;
#else
  for (int i = 0; i < CardsPerEntry; i++)
    _cards[i] = NullEntry;
#endif
}

bool SparsePRTEntry::contains_card(CardIdx_t card_index) const {
#if UNROLL_CARD_LOOPS
  assert(CardsPerEntry == 4, "Assumption.  If changes, un-unroll.");
  if (_cards[0] == card_index) return true;
  if (_cards[1] == card_index) return true;
  if (_cards[2] == card_index) return true;
  if (_cards[3] == card_index) return true;
#else
  for (int i = 0; i < CardsPerEntry; i++) {
    if (_cards[i] == card_index) return true;
  }
#endif
  // Otherwise, we're full.
  return false;
}

int SparsePRTEntry::num_valid_cards() const {
  int sum = 0;
#if UNROLL_CARD_LOOPS
  assert(CardsPerEntry == 4, "Assumption.  If changes, un-unroll.");
  if (_cards[0] != NullEntry) sum++;
  if (_cards[1] != NullEntry) sum++;
  if (_cards[2] != NullEntry) sum++;
  if (_cards[3] != NullEntry) sum++;
#else
  for (int i = 0; i < CardsPerEntry; i++) {
    if (_cards[i] != NulLEntry) sum++;
  }
#endif
  // Otherwise, we're full.
  return sum;
}

SparsePRTEntry::AddCardResult SparsePRTEntry::add_card(CardIdx_t card_index) {
#if UNROLL_CARD_LOOPS
  assert(CardsPerEntry == 4, "Assumption.  If changes, un-unroll.");
  CardIdx_t c = _cards[0];
  if (c == card_index) return found;
  if (c == NullEntry) { _cards[0] = card_index; return added; }
  c = _cards[1];
  if (c == card_index) return found;
  if (c == NullEntry) { _cards[1] = card_index; return added; }
  c = _cards[2];
  if (c == card_index) return found;
  if (c == NullEntry) { _cards[2] = card_index; return added; }
  c = _cards[3];
  if (c == card_index) return found;
  if (c == NullEntry) { _cards[3] = card_index; return added; }
#else
  for (int i = 0; i < CardsPerEntry; i++) {
    CardIdx_t c = _cards[i];
    if (c == card_index) return found;
    if (c == NullEntry) {
      _cards[i] = card_index;
      return added;
    }
  }
#endif
  // Otherwise, we're full.
  return overflow;
}

void SparsePRTEntry::copy_cards(CardIdx_t* cards) const {
#if UNROLL_CARD_LOOPS
  assert(CardsPerEntry == 4, "Assumption.  If changes, un-unroll.");
  cards[0] = _cards[0];
  cards[1] = _cards[1];
  cards[2] = _cards[2];
  cards[3] = _cards[3];
#else
  for (int i = 0; i < CardsPerEntry; i++) {
    cards[i] = _cards[i];
  }
#endif
}

void SparsePRTEntry::copy_cards(SparsePRTEntry* e) const {
  copy_cards(&e->_cards[0]);
}

// ----------------------------------------------------------------------

RSHashTable::RSHashTable(size_t capacity) :
  _capacity(capacity), _capacity_mask(capacity-1),
  _occupied_entries(0), _occupied_cards(0),
  _entries(NEW_C_HEAP_ARRAY(SparsePRTEntry, capacity)),
  _buckets(NEW_C_HEAP_ARRAY(int, capacity)),
  _next_deleted(NULL), _deleted(false),
  _free_list(NullEntry), _free_region(0)
{
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
  memset(_entries, -1, _capacity * sizeof(SparsePRTEntry));
  memset(_buckets, -1, _capacity * sizeof(int));
  _free_list = NullEntry;
  _free_region = 0;
}

bool RSHashTable::add_card(RegionIdx_t region_ind, CardIdx_t card_index) {
  SparsePRTEntry* e = entry_for_region_ind_create(region_ind);
  assert(e != NULL && e->r_ind() == region_ind,
         "Postcondition of call above.");
  SparsePRTEntry::AddCardResult res = e->add_card(card_index);
  if (res == SparsePRTEntry::added) _occupied_cards++;
#if SPARSE_PRT_VERBOSE
  gclog_or_tty->print_cr("       after add_card[%d]: valid-cards = %d.",
                pointer_delta(e, _entries, sizeof(SparsePRTEntry)),
                e->num_valid_cards());
#endif
  assert(e->num_valid_cards() > 0, "Postcondition");
  return res != SparsePRTEntry::overflow;
}

bool RSHashTable::get_cards(RegionIdx_t region_ind, CardIdx_t* cards) {
  int ind = (int) (region_ind & capacity_mask());
  int cur_ind = _buckets[ind];
  SparsePRTEntry* cur;
  while (cur_ind != NullEntry &&
         (cur = entry(cur_ind))->r_ind() != region_ind) {
    cur_ind = cur->next_index();
  }

  if (cur_ind == NullEntry) return false;
  // Otherwise...
  assert(cur->r_ind() == region_ind, "Postcondition of loop + test above.");
  assert(cur->num_valid_cards() > 0, "Inv");
  cur->copy_cards(cards);
  return true;
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
RSHashTable::entry_for_region_ind(RegionIdx_t region_ind) const {
  assert(occupied_entries() < capacity(), "Precondition");
  int ind = (int) (region_ind & capacity_mask());
  int cur_ind = _buckets[ind];
  SparsePRTEntry* cur;
  // XXX
  // int k = 0;
  while (cur_ind != NullEntry &&
         (cur = entry(cur_ind))->r_ind() != region_ind) {
    /*
    k++;
    if (k > 10) {
      gclog_or_tty->print_cr("RSHashTable::entry_for_region_ind(%d): "
                    "k = %d, cur_ind = %d.", region_ind, k, cur_ind);
      if (k >= 1000) {
        while (1) ;
      }
    }
    */
    cur_ind = cur->next_index();
  }

  if (cur_ind != NullEntry) {
    assert(cur->r_ind() == region_ind, "Loop postcondition + test");
    return cur;
  } else {
    return NULL;
  }
}

SparsePRTEntry*
RSHashTable::entry_for_region_ind_create(RegionIdx_t region_ind) {
  SparsePRTEntry* res = entry_for_region_ind(region_ind);
  if (res == NULL) {
    int new_ind = alloc_entry();
    assert(0 <= new_ind && (size_t)new_ind < capacity(), "There should be room.");
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
  } else if ((size_t) _free_region+1 < capacity()) {
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

RSHashTable* RSHashTable::_head_deleted_list = NULL;

void RSHashTable::add_to_deleted_list(RSHashTable* rsht) {
  assert(!rsht->deleted(), "Should delete only once.");
  rsht->set_deleted(true);
  RSHashTable* hd = _head_deleted_list;
  while (true) {
    rsht->_next_deleted = hd;
    RSHashTable* res =
      (RSHashTable*)
      Atomic::cmpxchg_ptr(rsht, &_head_deleted_list, hd);
    if (res == hd) return;
    else hd = res;
  }
}

RSHashTable* RSHashTable::get_from_deleted_list() {
  RSHashTable* hd = _head_deleted_list;
  while (hd != NULL) {
    RSHashTable* next = hd->next_deleted();
    RSHashTable* res =
      (RSHashTable*)
      Atomic::cmpxchg_ptr(next, &_head_deleted_list, hd);
    if (res == hd) {
      hd->set_next_deleted(NULL);
      hd->set_deleted(false);
      return hd;
    } else {
      hd = res;
    }
  }
  return NULL;
}

CardIdx_t /* RSHashTable:: */ RSHashTableIter::find_first_card_in_list() {
  CardIdx_t res;
  while (_bl_ind != RSHashTable::NullEntry) {
    res = _rsht->entry(_bl_ind)->card(0);
    if (res != SparsePRTEntry::NullEntry) {
      return res;
    } else {
      _bl_ind = _rsht->entry(_bl_ind)->next_index();
    }
  }
  // Otherwise, none found:
  return SparsePRTEntry::NullEntry;
}

size_t /* RSHashTable:: */ RSHashTableIter::compute_card_ind(CardIdx_t ci) {
  return
    _heap_bot_card_ind
    + (_rsht->entry(_bl_ind)->r_ind() * CardsPerRegion)
    + ci;
}

bool /* RSHashTable:: */ RSHashTableIter::has_next(size_t& card_index) {
  _card_ind++;
  CardIdx_t ci;
  if (_card_ind < SparsePRTEntry::CardsPerEntry &&
      ((ci = _rsht->entry(_bl_ind)->card(_card_ind)) !=
       SparsePRTEntry::NullEntry)) {
    card_index = compute_card_ind(ci);
    return true;
  }
  // Otherwise, must find the next valid entry.
  _card_ind = 0;

  if (_bl_ind != RSHashTable::NullEntry) {
      _bl_ind = _rsht->entry(_bl_ind)->next_index();
      ci = find_first_card_in_list();
      if (ci != SparsePRTEntry::NullEntry) {
        card_index = compute_card_ind(ci);
        return true;
      }
  }
  // If we didn't return above, must go to the next non-null table index.
  _tbl_ind++;
  while ((size_t)_tbl_ind < _rsht->capacity()) {
    _bl_ind = _rsht->_buckets[_tbl_ind];
    ci = find_first_card_in_list();
    if (ci != SparsePRTEntry::NullEntry) {
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
  SparsePRTEntry* e = entry_for_region_ind(region_index);
  return (e != NULL && e->contains_card(card_index));
}

size_t RSHashTable::mem_size() const {
  return sizeof(this) +
    capacity() * (sizeof(SparsePRTEntry) + sizeof(int));
}

// ----------------------------------------------------------------------

SparsePRT* SparsePRT::_head_expanded_list = NULL;

void SparsePRT::add_to_expanded_list(SparsePRT* sprt) {
  // We could expand multiple times in a pause -- only put on list once.
  if (sprt->expanded()) return;
  sprt->set_expanded(true);
  SparsePRT* hd = _head_expanded_list;
  while (true) {
    sprt->_next_expanded = hd;
    SparsePRT* res =
      (SparsePRT*)
      Atomic::cmpxchg_ptr(sprt, &_head_expanded_list, hd);
    if (res == hd) return;
    else hd = res;
  }
}


SparsePRT* SparsePRT::get_from_expanded_list() {
  SparsePRT* hd = _head_expanded_list;
  while (hd != NULL) {
    SparsePRT* next = hd->next_expanded();
    SparsePRT* res =
      (SparsePRT*)
      Atomic::cmpxchg_ptr(next, &_head_expanded_list, hd);
    if (res == hd) {
      hd->set_next_expanded(NULL);
      return hd;
    } else {
      hd = res;
    }
  }
  return NULL;
}


void SparsePRT::cleanup_all() {
  // First clean up all expanded tables so they agree on next and cur.
  SparsePRT* sprt = get_from_expanded_list();
  while (sprt != NULL) {
    sprt->cleanup();
    sprt = get_from_expanded_list();
  }
  // Now delete all deleted RSHashTables.
  RSHashTable* rsht = RSHashTable::get_from_deleted_list();
  while (rsht != NULL) {
#if SPARSE_PRT_VERBOSE
    gclog_or_tty->print_cr("About to delete RSHT " PTR_FORMAT ".", rsht);
#endif
    delete rsht;
    rsht = RSHashTable::get_from_deleted_list();
  }
}


SparsePRT::SparsePRT(HeapRegion* hr) :
  _expanded(false), _next_expanded(NULL)
{
  _cur = new RSHashTable(InitialCapacity);
  _next = _cur;
}


SparsePRT::~SparsePRT() {
  assert(_next != NULL && _cur != NULL, "Inv");
  if (_cur != _next) { delete _cur; }
  delete _next;
}


size_t SparsePRT::mem_size() const {
  // We ignore "_cur" here, because it either = _next, or else it is
  // on the deleted list.
  return sizeof(this) + _next->mem_size();
}

bool SparsePRT::add_card(RegionIdx_t region_id, CardIdx_t card_index) {
#if SPARSE_PRT_VERBOSE
  gclog_or_tty->print_cr("  Adding card %d from region %d to region %d sparse.",
                card_index, region_id, _hr->hrs_index());
#endif
  if (_next->occupied_entries() * 2 > _next->capacity()) {
    expand();
  }
  return _next->add_card(region_id, card_index);
}

bool SparsePRT::get_cards(RegionIdx_t region_id, CardIdx_t* cards) {
  return _next->get_cards(region_id, cards);
}

bool SparsePRT::delete_entry(RegionIdx_t region_id) {
  return _next->delete_entry(region_id);
}

void SparsePRT::clear() {
  // If they differ, _next is bigger then cur, so next has no chance of
  // being the initial size.
  if (_next != _cur) {
    delete _next;
  }

  if (_cur->capacity() != InitialCapacity) {
    delete _cur;
    _cur = new RSHashTable(InitialCapacity);
  } else {
    _cur->clear();
  }
  _next = _cur;
}

void SparsePRT::cleanup() {
  // Make sure that the current and next tables agree.  (Another mechanism
  // takes care of deleting now-unused tables.)
  _cur = _next;
  set_expanded(false);
}

void SparsePRT::expand() {
  RSHashTable* last = _next;
  _next = new RSHashTable(last->capacity() * 2);

#if SPARSE_PRT_VERBOSE
  gclog_or_tty->print_cr("  Expanded sparse table for %d to %d.",
                _hr->hrs_index(), _next->capacity());
#endif
  for (size_t i = 0; i < last->capacity(); i++) {
    SparsePRTEntry* e = last->entry((int)i);
    if (e->valid_entry()) {
#if SPARSE_PRT_VERBOSE
      gclog_or_tty->print_cr("    During expansion, transferred entry for %d.",
                    e->r_ind());
#endif
      _next->add_entry(e);
    }
  }
  if (last != _cur)
    RSHashTable::add_to_deleted_list(last);
  add_to_expanded_list(this);
}

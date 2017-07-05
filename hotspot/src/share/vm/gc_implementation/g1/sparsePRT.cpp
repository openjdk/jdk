/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/heapRegion.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "gc_implementation/g1/sparsePRT.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/cardTableModRefBS.hpp"
#include "memory/space.inline.hpp"
#include "runtime/mutexLocker.hpp"

#define SPARSE_PRT_VERBOSE 0

#define UNROLL_CARD_LOOPS  1

void SparsePRTEntry::init(RegionIdx_t region_ind) {
  _region_ind = region_ind;
  _next_index = NullEntry;

#if UNROLL_CARD_LOOPS
  assert((cards_num() & (UnrollFactor - 1)) == 0, "Invalid number of cards in the entry");
  for (int i = 0; i < cards_num(); i += UnrollFactor) {
    _cards[i] = NullEntry;
    _cards[i + 1] = NullEntry;
    _cards[i + 2] = NullEntry;
    _cards[i + 3] = NullEntry;
  }
#else
  for (int i = 0; i < cards_num(); i++)
    _cards[i] = NullEntry;
#endif
}

bool SparsePRTEntry::contains_card(CardIdx_t card_index) const {
#if UNROLL_CARD_LOOPS
  assert((cards_num() & (UnrollFactor - 1)) == 0, "Invalid number of cards in the entry");
  for (int i = 0; i < cards_num(); i += UnrollFactor) {
    if (_cards[i] == card_index ||
        _cards[i + 1] == card_index ||
        _cards[i + 2] == card_index ||
        _cards[i + 3] == card_index) return true;
  }
#else
  for (int i = 0; i < cards_num(); i++) {
    if (_cards[i] == card_index) return true;
  }
#endif
  // Otherwise, we're full.
  return false;
}

int SparsePRTEntry::num_valid_cards() const {
  int sum = 0;
#if UNROLL_CARD_LOOPS
  assert((cards_num() & (UnrollFactor - 1)) == 0, "Invalid number of cards in the entry");
  for (int i = 0; i < cards_num(); i += UnrollFactor) {
    sum += (_cards[i] != NullEntry);
    sum += (_cards[i + 1] != NullEntry);
    sum += (_cards[i + 2] != NullEntry);
    sum += (_cards[i + 3] != NullEntry);
  }
#else
  for (int i = 0; i < cards_num(); i++) {
    sum += (_cards[i] != NullEntry);
  }
#endif
  // Otherwise, we're full.
  return sum;
}

SparsePRTEntry::AddCardResult SparsePRTEntry::add_card(CardIdx_t card_index) {
#if UNROLL_CARD_LOOPS
  assert((cards_num() & (UnrollFactor - 1)) == 0, "Invalid number of cards in the entry");
  CardIdx_t c;
  for (int i = 0; i < cards_num(); i += UnrollFactor) {
    c = _cards[i];
    if (c == card_index) return found;
    if (c == NullEntry) { _cards[i] = card_index; return added; }
    c = _cards[i + 1];
    if (c == card_index) return found;
    if (c == NullEntry) { _cards[i + 1] = card_index; return added; }
    c = _cards[i + 2];
    if (c == card_index) return found;
    if (c == NullEntry) { _cards[i + 2] = card_index; return added; }
    c = _cards[i + 3];
    if (c == card_index) return found;
    if (c == NullEntry) { _cards[i + 3] = card_index; return added; }
  }
#else
  for (int i = 0; i < cards_num(); i++) {
    CardIdx_t c = _cards[i];
    if (c == card_index) return found;
    if (c == NullEntry) { _cards[i] = card_index; return added; }
  }
#endif
  // Otherwise, we're full.
  return overflow;
}

void SparsePRTEntry::copy_cards(CardIdx_t* cards) const {
#if UNROLL_CARD_LOOPS
  assert((cards_num() & (UnrollFactor - 1)) == 0, "Invalid number of cards in the entry");
  for (int i = 0; i < cards_num(); i += UnrollFactor) {
    cards[i] = _cards[i];
    cards[i + 1] = _cards[i + 1];
    cards[i + 2] = _cards[i + 2];
    cards[i + 3] = _cards[i + 3];
  }
#else
  for (int i = 0; i < cards_num(); i++) {
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
  _entries((SparsePRTEntry*)NEW_C_HEAP_ARRAY(char, SparsePRTEntry::size() * capacity, mtGC)),
  _buckets(NEW_C_HEAP_ARRAY(int, capacity, mtGC)),
  _free_list(NullEntry), _free_region(0)
{
  clear();
}

RSHashTable::~RSHashTable() {
  if (_entries != NULL) {
    FREE_C_HEAP_ARRAY(SparsePRTEntry, _entries, mtGC);
    _entries = NULL;
  }
  if (_buckets != NULL) {
    FREE_C_HEAP_ARRAY(int, _buckets, mtGC);
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
  memset(_entries, NullEntry, _capacity * SparsePRTEntry::size());
  memset(_buckets, NullEntry, _capacity * sizeof(int));
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
                         pointer_delta(e, _entries, SparsePRTEntry::size()),
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

SparsePRTEntry* RSHashTable::get_entry(RegionIdx_t region_ind) {
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
RSHashTable::entry_for_region_ind(RegionIdx_t region_ind) const {
  assert(occupied_entries() < capacity(), "Precondition");
  int ind = (int) (region_ind & capacity_mask());
  int cur_ind = _buckets[ind];
  SparsePRTEntry* cur;
  while (cur_ind != NullEntry &&
         (cur = entry(cur_ind))->r_ind() != region_ind) {
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

CardIdx_t RSHashTableIter::find_first_card_in_list() {
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

size_t RSHashTableIter::compute_card_ind(CardIdx_t ci) {
  return (_rsht->entry(_bl_ind)->r_ind() * HeapRegion::CardsPerRegion) + ci;
}

bool RSHashTableIter::has_next(size_t& card_index) {
  _card_ind++;
  CardIdx_t ci;
  if (_card_ind < SparsePRTEntry::cards_num() &&
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
  return sizeof(RSHashTable) +
    capacity() * (SparsePRTEntry::size() + sizeof(int));
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

void SparsePRT::reset_for_cleanup_tasks() {
  _head_expanded_list = NULL;
}

void SparsePRT::do_cleanup_work(SparsePRTCleanupTask* sprt_cleanup_task) {
  if (should_be_on_expanded_list()) {
    sprt_cleanup_task->add(this);
  }
}

void SparsePRT::finish_cleanup_task(SparsePRTCleanupTask* sprt_cleanup_task) {
  assert(ParGCRareEvent_lock->owned_by_self(), "pre-condition");
  SparsePRT* head = sprt_cleanup_task->head();
  SparsePRT* tail = sprt_cleanup_task->tail();
  if (head != NULL) {
    assert(tail != NULL, "if head is not NULL, so should tail");

    tail->set_next_expanded(_head_expanded_list);
    _head_expanded_list = head;
  } else {
    assert(tail == NULL, "if head is NULL, so should tail");
  }
}

bool SparsePRT::should_be_on_expanded_list() {
  if (_expanded) {
    assert(_cur != _next, "if _expanded is true, cur should be != _next");
  } else {
    assert(_cur == _next, "if _expanded is false, cur should be == _next");
  }
  return expanded();
}

void SparsePRT::cleanup_all() {
  // First clean up all expanded tables so they agree on next and cur.
  SparsePRT* sprt = get_from_expanded_list();
  while (sprt != NULL) {
    sprt->cleanup();
    sprt = get_from_expanded_list();
  }
}


SparsePRT::SparsePRT(HeapRegion* hr) :
  _hr(hr), _expanded(false), _next_expanded(NULL)
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
  return sizeof(SparsePRT) + _next->mem_size();
}

bool SparsePRT::add_card(RegionIdx_t region_id, CardIdx_t card_index) {
#if SPARSE_PRT_VERBOSE
  gclog_or_tty->print_cr("  Adding card %d from region %d to region %u sparse.",
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

SparsePRTEntry* SparsePRT::get_entry(RegionIdx_t region_id) {
  return _next->get_entry(region_id);
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
  _expanded = false;
}

void SparsePRT::cleanup() {
  // Make sure that the current and next tables agree.
  if (_cur != _next) {
    delete _cur;
  }
  _cur = _next;
  set_expanded(false);
}

void SparsePRT::expand() {
  RSHashTable* last = _next;
  _next = new RSHashTable(last->capacity() * 2);

#if SPARSE_PRT_VERBOSE
  gclog_or_tty->print_cr("  Expanded sparse table for %u to %d.",
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
  if (last != _cur) {
    delete last;
  }
  add_to_expanded_list(this);
}

void SparsePRTCleanupTask::add(SparsePRT* sprt) {
  assert(sprt->should_be_on_expanded_list(), "pre-condition");

  sprt->set_next_expanded(NULL);
  if (_tail != NULL) {
    _tail->set_next_expanded(sprt);
  } else {
    _head = sprt;
  }
  _tail = sprt;
}

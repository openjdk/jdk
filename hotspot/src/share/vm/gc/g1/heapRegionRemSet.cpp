/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/concurrentG1Refine.hpp"
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/heapRegionManager.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/space.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/padded.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.inline.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

class PerRegionTable: public CHeapObj<mtGC> {
  friend class OtherRegionsTable;
  friend class HeapRegionRemSetIterator;

  HeapRegion*     _hr;
  BitMap          _bm;
  jint            _occupied;

  // next pointer for free/allocated 'all' list
  PerRegionTable* _next;

  // prev pointer for the allocated 'all' list
  PerRegionTable* _prev;

  // next pointer in collision list
  PerRegionTable * _collision_list_next;

  // Global free list of PRTs
  static PerRegionTable* _free_list;

protected:
  // We need access in order to union things into the base table.
  BitMap* bm() { return &_bm; }

  void recount_occupied() {
    _occupied = (jint) bm()->count_one_bits();
  }

  PerRegionTable(HeapRegion* hr) :
    _hr(hr),
    _occupied(0),
    _bm(HeapRegion::CardsPerRegion, false /* in-resource-area */),
    _collision_list_next(NULL), _next(NULL), _prev(NULL)
  {}

  void add_card_work(CardIdx_t from_card, bool par) {
    if (!_bm.at(from_card)) {
      if (par) {
        if (_bm.par_at_put(from_card, 1)) {
          Atomic::inc(&_occupied);
        }
      } else {
        _bm.at_put(from_card, 1);
        _occupied++;
      }
    }
  }

  void add_reference_work(OopOrNarrowOopStar from, bool par) {
    // Must make this robust in case "from" is not in "_hr", because of
    // concurrency.

    HeapRegion* loc_hr = hr();
    // If the test below fails, then this table was reused concurrently
    // with this operation.  This is OK, since the old table was coarsened,
    // and adding a bit to the new table is never incorrect.
    // If the table used to belong to a continues humongous region and is
    // now reused for the corresponding start humongous region, we need to
    // make sure that we detect this. Thus, we call is_in_reserved_raw()
    // instead of just is_in_reserved() here.
    if (loc_hr->is_in_reserved(from)) {
      size_t hw_offset = pointer_delta((HeapWord*)from, loc_hr->bottom());
      CardIdx_t from_card = (CardIdx_t)
          hw_offset >> (CardTableModRefBS::card_shift - LogHeapWordSize);

      assert(0 <= from_card && (size_t)from_card < HeapRegion::CardsPerRegion,
             "Must be in range.");
      add_card_work(from_card, par);
    }
  }

public:

  HeapRegion* hr() const { return _hr; }

  jint occupied() const {
    // Overkill, but if we ever need it...
    // guarantee(_occupied == _bm.count_one_bits(), "Check");
    return _occupied;
  }

  void init(HeapRegion* hr, bool clear_links_to_all_list) {
    if (clear_links_to_all_list) {
      set_next(NULL);
      set_prev(NULL);
    }
    _hr = hr;
    _collision_list_next = NULL;
    _occupied = 0;
    _bm.clear();
  }

  void add_reference(OopOrNarrowOopStar from) {
    add_reference_work(from, /*parallel*/ true);
  }

  void seq_add_reference(OopOrNarrowOopStar from) {
    add_reference_work(from, /*parallel*/ false);
  }

  void scrub(CardTableModRefBS* ctbs, BitMap* card_bm) {
    HeapWord* hr_bot = hr()->bottom();
    size_t hr_first_card_index = ctbs->index_for(hr_bot);
    bm()->set_intersection_at_offset(*card_bm, hr_first_card_index);
    recount_occupied();
  }

  void add_card(CardIdx_t from_card_index) {
    add_card_work(from_card_index, /*parallel*/ true);
  }

  void seq_add_card(CardIdx_t from_card_index) {
    add_card_work(from_card_index, /*parallel*/ false);
  }

  // (Destructively) union the bitmap of the current table into the given
  // bitmap (which is assumed to be of the same size.)
  void union_bitmap_into(BitMap* bm) {
    bm->set_union(_bm);
  }

  // Mem size in bytes.
  size_t mem_size() const {
    return sizeof(PerRegionTable) + _bm.size_in_words() * HeapWordSize;
  }

  // Requires "from" to be in "hr()".
  bool contains_reference(OopOrNarrowOopStar from) const {
    assert(hr()->is_in_reserved(from), "Precondition.");
    size_t card_ind = pointer_delta(from, hr()->bottom(),
                                    CardTableModRefBS::card_size);
    return _bm.at(card_ind);
  }

  // Bulk-free the PRTs from prt to last, assumes that they are
  // linked together using their _next field.
  static void bulk_free(PerRegionTable* prt, PerRegionTable* last) {
    while (true) {
      PerRegionTable* fl = _free_list;
      last->set_next(fl);
      PerRegionTable* res = (PerRegionTable*) Atomic::cmpxchg_ptr(prt, &_free_list, fl);
      if (res == fl) {
        return;
      }
    }
    ShouldNotReachHere();
  }

  static void free(PerRegionTable* prt) {
    bulk_free(prt, prt);
  }

  // Returns an initialized PerRegionTable instance.
  static PerRegionTable* alloc(HeapRegion* hr) {
    PerRegionTable* fl = _free_list;
    while (fl != NULL) {
      PerRegionTable* nxt = fl->next();
      PerRegionTable* res =
        (PerRegionTable*)
        Atomic::cmpxchg_ptr(nxt, &_free_list, fl);
      if (res == fl) {
        fl->init(hr, true);
        return fl;
      } else {
        fl = _free_list;
      }
    }
    assert(fl == NULL, "Loop condition.");
    return new PerRegionTable(hr);
  }

  PerRegionTable* next() const { return _next; }
  void set_next(PerRegionTable* next) { _next = next; }
  PerRegionTable* prev() const { return _prev; }
  void set_prev(PerRegionTable* prev) { _prev = prev; }

  // Accessor and Modification routines for the pointer for the
  // singly linked collision list that links the PRTs within the
  // OtherRegionsTable::_fine_grain_regions hash table.
  //
  // It might be useful to also make the collision list doubly linked
  // to avoid iteration over the collisions list during scrubbing/deletion.
  // OTOH there might not be many collisions.

  PerRegionTable* collision_list_next() const {
    return _collision_list_next;
  }

  void set_collision_list_next(PerRegionTable* next) {
    _collision_list_next = next;
  }

  PerRegionTable** collision_list_next_addr() {
    return &_collision_list_next;
  }

  static size_t fl_mem_size() {
    PerRegionTable* cur = _free_list;
    size_t res = 0;
    while (cur != NULL) {
      res += cur->mem_size();
      cur = cur->next();
    }
    return res;
  }

  static void test_fl_mem_size();
};

PerRegionTable* PerRegionTable::_free_list = NULL;

size_t OtherRegionsTable::_max_fine_entries = 0;
size_t OtherRegionsTable::_mod_max_fine_entries_mask = 0;
size_t OtherRegionsTable::_fine_eviction_stride = 0;
size_t OtherRegionsTable::_fine_eviction_sample_size = 0;

OtherRegionsTable::OtherRegionsTable(HeapRegion* hr, Mutex* m) :
  _g1h(G1CollectedHeap::heap()),
  _hr(hr), _m(m),
  _coarse_map(G1CollectedHeap::heap()->max_regions(),
              false /* in-resource-area */),
  _fine_grain_regions(NULL),
  _first_all_fine_prts(NULL), _last_all_fine_prts(NULL),
  _n_fine_entries(0), _n_coarse_entries(0),
  _fine_eviction_start(0),
  _sparse_table(hr)
{
  typedef PerRegionTable* PerRegionTablePtr;

  if (_max_fine_entries == 0) {
    assert(_mod_max_fine_entries_mask == 0, "Both or none.");
    size_t max_entries_log = (size_t)log2_long((jlong)G1RSetRegionEntries);
    _max_fine_entries = (size_t)1 << max_entries_log;
    _mod_max_fine_entries_mask = _max_fine_entries - 1;

    assert(_fine_eviction_sample_size == 0
           && _fine_eviction_stride == 0, "All init at same time.");
    _fine_eviction_sample_size = MAX2((size_t)4, max_entries_log);
    _fine_eviction_stride = _max_fine_entries / _fine_eviction_sample_size;
  }

  _fine_grain_regions = NEW_C_HEAP_ARRAY3(PerRegionTablePtr, _max_fine_entries,
                        mtGC, CURRENT_PC, AllocFailStrategy::RETURN_NULL);

  if (_fine_grain_regions == NULL) {
    vm_exit_out_of_memory(sizeof(void*)*_max_fine_entries, OOM_MALLOC_ERROR,
                          "Failed to allocate _fine_grain_entries.");
  }

  for (size_t i = 0; i < _max_fine_entries; i++) {
    _fine_grain_regions[i] = NULL;
  }
}

void OtherRegionsTable::link_to_all(PerRegionTable* prt) {
  // We always append to the beginning of the list for convenience;
  // the order of entries in this list does not matter.
  if (_first_all_fine_prts != NULL) {
    assert(_first_all_fine_prts->prev() == NULL, "invariant");
    _first_all_fine_prts->set_prev(prt);
    prt->set_next(_first_all_fine_prts);
  } else {
    // this is the first element we insert. Adjust the "last" pointer
    _last_all_fine_prts = prt;
    assert(prt->next() == NULL, "just checking");
  }
  // the new element is always the first element without a predecessor
  prt->set_prev(NULL);
  _first_all_fine_prts = prt;

  assert(prt->prev() == NULL, "just checking");
  assert(_first_all_fine_prts == prt, "just checking");
  assert((_first_all_fine_prts == NULL && _last_all_fine_prts == NULL) ||
         (_first_all_fine_prts != NULL && _last_all_fine_prts != NULL),
         "just checking");
  assert(_last_all_fine_prts == NULL || _last_all_fine_prts->next() == NULL,
         "just checking");
  assert(_first_all_fine_prts == NULL || _first_all_fine_prts->prev() == NULL,
         "just checking");
}

void OtherRegionsTable::unlink_from_all(PerRegionTable* prt) {
  if (prt->prev() != NULL) {
    assert(_first_all_fine_prts != prt, "just checking");
    prt->prev()->set_next(prt->next());
    // removing the last element in the list?
    if (_last_all_fine_prts == prt) {
      _last_all_fine_prts = prt->prev();
    }
  } else {
    assert(_first_all_fine_prts == prt, "just checking");
    _first_all_fine_prts = prt->next();
    // list is empty now?
    if (_first_all_fine_prts == NULL) {
      _last_all_fine_prts = NULL;
    }
  }

  if (prt->next() != NULL) {
    prt->next()->set_prev(prt->prev());
  }

  prt->set_next(NULL);
  prt->set_prev(NULL);

  assert((_first_all_fine_prts == NULL && _last_all_fine_prts == NULL) ||
         (_first_all_fine_prts != NULL && _last_all_fine_prts != NULL),
         "just checking");
  assert(_last_all_fine_prts == NULL || _last_all_fine_prts->next() == NULL,
         "just checking");
  assert(_first_all_fine_prts == NULL || _first_all_fine_prts->prev() == NULL,
         "just checking");
}

int**  FromCardCache::_cache = NULL;
uint   FromCardCache::_max_regions = 0;
size_t FromCardCache::_static_mem_size = 0;

void FromCardCache::initialize(uint n_par_rs, uint max_num_regions) {
  guarantee(_cache == NULL, "Should not call this multiple times");

  _max_regions = max_num_regions;
  _cache = Padded2DArray<int, mtGC>::create_unfreeable(n_par_rs,
                                                       _max_regions,
                                                       &_static_mem_size);

  invalidate(0, _max_regions);
}

void FromCardCache::invalidate(uint start_idx, size_t new_num_regions) {
  guarantee((size_t)start_idx + new_num_regions <= max_uintx,
            "Trying to invalidate beyond maximum region, from %u size " SIZE_FORMAT,
            start_idx, new_num_regions);
  for (uint i = 0; i < HeapRegionRemSet::num_par_rem_sets(); i++) {
    uint end_idx = (start_idx + (uint)new_num_regions);
    assert(end_idx <= _max_regions, "Must be within max.");
    for (uint j = start_idx; j < end_idx; j++) {
      set(i, j, InvalidCard);
    }
  }
}

#ifndef PRODUCT
void FromCardCache::print(outputStream* out) {
  for (uint i = 0; i < HeapRegionRemSet::num_par_rem_sets(); i++) {
    for (uint j = 0; j < _max_regions; j++) {
      out->print_cr("_from_card_cache[%u][%u] = %d.",
                    i, j, at(i, j));
    }
  }
}
#endif

void FromCardCache::clear(uint region_idx) {
  uint num_par_remsets = HeapRegionRemSet::num_par_rem_sets();
  for (uint i = 0; i < num_par_remsets; i++) {
    set(i, region_idx, InvalidCard);
  }
}

void OtherRegionsTable::add_reference(OopOrNarrowOopStar from, uint tid) {
  uint cur_hrm_ind = _hr->hrm_index();

  int from_card = (int)(uintptr_t(from) >> CardTableModRefBS::card_shift);

  if (FromCardCache::contains_or_replace(tid, cur_hrm_ind, from_card)) {
    assert(contains_reference(from), "We just added it!");
    return;
  }

  // Note that this may be a continued H region.
  HeapRegion* from_hr = _g1h->heap_region_containing(from);
  RegionIdx_t from_hrm_ind = (RegionIdx_t) from_hr->hrm_index();

  // If the region is already coarsened, return.
  if (_coarse_map.at(from_hrm_ind)) {
    assert(contains_reference(from), "We just added it!");
    return;
  }

  // Otherwise find a per-region table to add it to.
  size_t ind = from_hrm_ind & _mod_max_fine_entries_mask;
  PerRegionTable* prt = find_region_table(ind, from_hr);
  if (prt == NULL) {
    MutexLockerEx x(_m, Mutex::_no_safepoint_check_flag);
    // Confirm that it's really not there...
    prt = find_region_table(ind, from_hr);
    if (prt == NULL) {

      uintptr_t from_hr_bot_card_index =
        uintptr_t(from_hr->bottom())
          >> CardTableModRefBS::card_shift;
      CardIdx_t card_index = from_card - from_hr_bot_card_index;
      assert(0 <= card_index && (size_t)card_index < HeapRegion::CardsPerRegion,
             "Must be in range.");
      if (G1HRRSUseSparseTable &&
          _sparse_table.add_card(from_hrm_ind, card_index)) {
        assert(contains_reference_locked(from), "We just added it!");
        return;
      }

      if (_n_fine_entries == _max_fine_entries) {
        prt = delete_region_table();
        // There is no need to clear the links to the 'all' list here:
        // prt will be reused immediately, i.e. remain in the 'all' list.
        prt->init(from_hr, false /* clear_links_to_all_list */);
      } else {
        prt = PerRegionTable::alloc(from_hr);
        link_to_all(prt);
      }

      PerRegionTable* first_prt = _fine_grain_regions[ind];
      prt->set_collision_list_next(first_prt);
      // The assignment into _fine_grain_regions allows the prt to
      // start being used concurrently. In addition to
      // collision_list_next which must be visible (else concurrent
      // parsing of the list, if any, may fail to see other entries),
      // the content of the prt must be visible (else for instance
      // some mark bits may not yet seem cleared or a 'later' update
      // performed by a concurrent thread could be undone when the
      // zeroing becomes visible). This requires store ordering.
      OrderAccess::release_store_ptr((volatile PerRegionTable*)&_fine_grain_regions[ind], prt);
      _n_fine_entries++;

      if (G1HRRSUseSparseTable) {
        // Transfer from sparse to fine-grain.
        SparsePRTEntry *sprt_entry = _sparse_table.get_entry(from_hrm_ind);
        assert(sprt_entry != NULL, "There should have been an entry");
        for (int i = 0; i < SparsePRTEntry::cards_num(); i++) {
          CardIdx_t c = sprt_entry->card(i);
          if (c != SparsePRTEntry::NullEntry) {
            prt->add_card(c);
          }
        }
        // Now we can delete the sparse entry.
        bool res = _sparse_table.delete_entry(from_hrm_ind);
        assert(res, "It should have been there.");
      }
    }
    assert(prt != NULL && prt->hr() == from_hr, "consequence");
  }
  // Note that we can't assert "prt->hr() == from_hr", because of the
  // possibility of concurrent reuse.  But see head comment of
  // OtherRegionsTable for why this is OK.
  assert(prt != NULL, "Inv");

  prt->add_reference(from);
  assert(contains_reference(from), "We just added it!");
}

PerRegionTable*
OtherRegionsTable::find_region_table(size_t ind, HeapRegion* hr) const {
  assert(ind < _max_fine_entries, "Preconditions.");
  PerRegionTable* prt = _fine_grain_regions[ind];
  while (prt != NULL && prt->hr() != hr) {
    prt = prt->collision_list_next();
  }
  // Loop postcondition is the method postcondition.
  return prt;
}

jint OtherRegionsTable::_n_coarsenings = 0;

PerRegionTable* OtherRegionsTable::delete_region_table() {
  assert(_m->owned_by_self(), "Precondition");
  assert(_n_fine_entries == _max_fine_entries, "Precondition");
  PerRegionTable* max = NULL;
  jint max_occ = 0;
  PerRegionTable** max_prev = NULL;
  size_t max_ind;

  size_t i = _fine_eviction_start;
  for (size_t k = 0; k < _fine_eviction_sample_size; k++) {
    size_t ii = i;
    // Make sure we get a non-NULL sample.
    while (_fine_grain_regions[ii] == NULL) {
      ii++;
      if (ii == _max_fine_entries) ii = 0;
      guarantee(ii != i, "We must find one.");
    }
    PerRegionTable** prev = &_fine_grain_regions[ii];
    PerRegionTable* cur = *prev;
    while (cur != NULL) {
      jint cur_occ = cur->occupied();
      if (max == NULL || cur_occ > max_occ) {
        max = cur;
        max_prev = prev;
        max_ind = i;
        max_occ = cur_occ;
      }
      prev = cur->collision_list_next_addr();
      cur = cur->collision_list_next();
    }
    i = i + _fine_eviction_stride;
    if (i >= _n_fine_entries) i = i - _n_fine_entries;
  }

  _fine_eviction_start++;

  if (_fine_eviction_start >= _n_fine_entries) {
    _fine_eviction_start -= _n_fine_entries;
  }

  guarantee(max != NULL, "Since _n_fine_entries > 0");
  guarantee(max_prev != NULL, "Since max != NULL.");

  // Set the corresponding coarse bit.
  size_t max_hrm_index = (size_t) max->hr()->hrm_index();
  if (!_coarse_map.at(max_hrm_index)) {
    _coarse_map.at_put(max_hrm_index, true);
    _n_coarse_entries++;
  }

  // Unsplice.
  *max_prev = max->collision_list_next();
  Atomic::inc(&_n_coarsenings);
  _n_fine_entries--;
  return max;
}

void OtherRegionsTable::scrub(CardTableModRefBS* ctbs,
                              BitMap* region_bm, BitMap* card_bm) {
  // First eliminated garbage regions from the coarse map.
  if (G1RSScrubVerbose) {
    gclog_or_tty->print_cr("Scrubbing region %u:", _hr->hrm_index());
  }

  assert(_coarse_map.size() == region_bm->size(), "Precondition");
  if (G1RSScrubVerbose) {
    gclog_or_tty->print("   Coarse map: before = " SIZE_FORMAT "...",
                        _n_coarse_entries);
  }
  _coarse_map.set_intersection(*region_bm);
  _n_coarse_entries = _coarse_map.count_one_bits();
  if (G1RSScrubVerbose) {
    gclog_or_tty->print_cr("   after = " SIZE_FORMAT ".", _n_coarse_entries);
  }

  // Now do the fine-grained maps.
  for (size_t i = 0; i < _max_fine_entries; i++) {
    PerRegionTable* cur = _fine_grain_regions[i];
    PerRegionTable** prev = &_fine_grain_regions[i];
    while (cur != NULL) {
      PerRegionTable* nxt = cur->collision_list_next();
      // If the entire region is dead, eliminate.
      if (G1RSScrubVerbose) {
        gclog_or_tty->print_cr("     For other region %u:",
                               cur->hr()->hrm_index());
      }
      if (!region_bm->at((size_t) cur->hr()->hrm_index())) {
        *prev = nxt;
        cur->set_collision_list_next(NULL);
        _n_fine_entries--;
        if (G1RSScrubVerbose) {
          gclog_or_tty->print_cr("          deleted via region map.");
        }
        unlink_from_all(cur);
        PerRegionTable::free(cur);
      } else {
        // Do fine-grain elimination.
        if (G1RSScrubVerbose) {
          gclog_or_tty->print("          occ: before = %4d.", cur->occupied());
        }
        cur->scrub(ctbs, card_bm);
        if (G1RSScrubVerbose) {
          gclog_or_tty->print_cr("          after = %4d.", cur->occupied());
        }
        // Did that empty the table completely?
        if (cur->occupied() == 0) {
          *prev = nxt;
          cur->set_collision_list_next(NULL);
          _n_fine_entries--;
          unlink_from_all(cur);
          PerRegionTable::free(cur);
        } else {
          prev = cur->collision_list_next_addr();
        }
      }
      cur = nxt;
    }
  }
  // Since we may have deleted a from_card_cache entry from the RS, clear
  // the FCC.
  clear_fcc();
}

bool OtherRegionsTable::occupancy_less_or_equal_than(size_t limit) const {
  if (limit <= (size_t)G1RSetSparseRegionEntries) {
    return occ_coarse() == 0 && _first_all_fine_prts == NULL && occ_sparse() <= limit;
  } else {
    // Current uses of this method may only use values less than G1RSetSparseRegionEntries
    // for the limit. The solution, comparing against occupied() would be too slow
    // at this time.
    Unimplemented();
    return false;
  }
}

bool OtherRegionsTable::is_empty() const {
  return occ_sparse() == 0 && occ_coarse() == 0 && _first_all_fine_prts == NULL;
}

size_t OtherRegionsTable::occupied() const {
  size_t sum = occ_fine();
  sum += occ_sparse();
  sum += occ_coarse();
  return sum;
}

size_t OtherRegionsTable::occ_fine() const {
  size_t sum = 0;

  size_t num = 0;
  PerRegionTable * cur = _first_all_fine_prts;
  while (cur != NULL) {
    sum += cur->occupied();
    cur = cur->next();
    num++;
  }
  guarantee(num == _n_fine_entries, "just checking");
  return sum;
}

size_t OtherRegionsTable::occ_coarse() const {
  return (_n_coarse_entries * HeapRegion::CardsPerRegion);
}

size_t OtherRegionsTable::occ_sparse() const {
  return _sparse_table.occupied();
}

size_t OtherRegionsTable::mem_size() const {
  size_t sum = 0;
  // all PRTs are of the same size so it is sufficient to query only one of them.
  if (_first_all_fine_prts != NULL) {
    assert(_last_all_fine_prts != NULL &&
      _first_all_fine_prts->mem_size() == _last_all_fine_prts->mem_size(), "check that mem_size() is constant");
    sum += _first_all_fine_prts->mem_size() * _n_fine_entries;
  }
  sum += (sizeof(PerRegionTable*) * _max_fine_entries);
  sum += (_coarse_map.size_in_words() * HeapWordSize);
  sum += (_sparse_table.mem_size());
  sum += sizeof(OtherRegionsTable) - sizeof(_sparse_table); // Avoid double counting above.
  return sum;
}

size_t OtherRegionsTable::static_mem_size() {
  return FromCardCache::static_mem_size();
}

size_t OtherRegionsTable::fl_mem_size() {
  return PerRegionTable::fl_mem_size();
}

void OtherRegionsTable::clear_fcc() {
  FromCardCache::clear(_hr->hrm_index());
}

void OtherRegionsTable::clear() {
  // if there are no entries, skip this step
  if (_first_all_fine_prts != NULL) {
    guarantee(_first_all_fine_prts != NULL && _last_all_fine_prts != NULL, "just checking");
    PerRegionTable::bulk_free(_first_all_fine_prts, _last_all_fine_prts);
    memset(_fine_grain_regions, 0, _max_fine_entries * sizeof(_fine_grain_regions[0]));
  } else {
    guarantee(_first_all_fine_prts == NULL && _last_all_fine_prts == NULL, "just checking");
  }

  _first_all_fine_prts = _last_all_fine_prts = NULL;
  _sparse_table.clear();
  _coarse_map.clear();
  _n_fine_entries = 0;
  _n_coarse_entries = 0;

  clear_fcc();
}

bool OtherRegionsTable::contains_reference(OopOrNarrowOopStar from) const {
  // Cast away const in this case.
  MutexLockerEx x((Mutex*)_m, Mutex::_no_safepoint_check_flag);
  return contains_reference_locked(from);
}

bool OtherRegionsTable::contains_reference_locked(OopOrNarrowOopStar from) const {
  HeapRegion* hr = _g1h->heap_region_containing(from);
  RegionIdx_t hr_ind = (RegionIdx_t) hr->hrm_index();
  // Is this region in the coarse map?
  if (_coarse_map.at(hr_ind)) return true;

  PerRegionTable* prt = find_region_table(hr_ind & _mod_max_fine_entries_mask,
                                     hr);
  if (prt != NULL) {
    return prt->contains_reference(from);

  } else {
    uintptr_t from_card =
      (uintptr_t(from) >> CardTableModRefBS::card_shift);
    uintptr_t hr_bot_card_index =
      uintptr_t(hr->bottom()) >> CardTableModRefBS::card_shift;
    assert(from_card >= hr_bot_card_index, "Inv");
    CardIdx_t card_index = from_card - hr_bot_card_index;
    assert(0 <= card_index && (size_t)card_index < HeapRegion::CardsPerRegion,
           "Must be in range.");
    return _sparse_table.contains_card(hr_ind, card_index);
  }
}

void
OtherRegionsTable::do_cleanup_work(HRRSCleanupTask* hrrs_cleanup_task) {
  _sparse_table.do_cleanup_work(hrrs_cleanup_task);
}

// Determines how many threads can add records to an rset in parallel.
// This can be done by either mutator threads together with the
// concurrent refinement threads or GC threads.
uint HeapRegionRemSet::num_par_rem_sets() {
  return MAX2(DirtyCardQueueSet::num_par_ids() + ConcurrentG1Refine::thread_num(), ParallelGCThreads);
}

HeapRegionRemSet::HeapRegionRemSet(G1BlockOffsetSharedArray* bosa,
                                   HeapRegion* hr)
  : _bosa(bosa),
    _m(Mutex::leaf, FormatBuffer<128>("HeapRegionRemSet lock #%u", hr->hrm_index()), true, Monitor::_safepoint_check_never),
    _code_roots(), _other_regions(hr, &_m), _iter_state(Unclaimed), _iter_claimed(0) {
  reset_for_par_iteration();
}

void HeapRegionRemSet::setup_remset_size() {
  // Setup sparse and fine-grain tables sizes.
  // table_size = base * (log(region_size / 1M) + 1)
  const int LOG_M = 20;
  int region_size_log_mb = MAX2(HeapRegion::LogOfHRGrainBytes - LOG_M, 0);
  if (FLAG_IS_DEFAULT(G1RSetSparseRegionEntries)) {
    G1RSetSparseRegionEntries = G1RSetSparseRegionEntriesBase * (region_size_log_mb + 1);
  }
  if (FLAG_IS_DEFAULT(G1RSetRegionEntries)) {
    G1RSetRegionEntries = G1RSetRegionEntriesBase * (region_size_log_mb + 1);
  }
  guarantee(G1RSetSparseRegionEntries > 0 && G1RSetRegionEntries > 0 , "Sanity");
}

bool HeapRegionRemSet::claim_iter() {
  if (_iter_state != Unclaimed) return false;
  jint res = Atomic::cmpxchg(Claimed, (jint*)(&_iter_state), Unclaimed);
  return (res == Unclaimed);
}

void HeapRegionRemSet::set_iter_complete() {
  _iter_state = Complete;
}

bool HeapRegionRemSet::iter_is_complete() {
  return _iter_state == Complete;
}

#ifndef PRODUCT
void HeapRegionRemSet::print() {
  HeapRegionRemSetIterator iter(this);
  size_t card_index;
  while (iter.has_next(card_index)) {
    HeapWord* card_start =
      G1CollectedHeap::heap()->bot_shared()->address_for_index(card_index);
    gclog_or_tty->print_cr("  Card " PTR_FORMAT, p2i(card_start));
  }
  if (iter.n_yielded() != occupied()) {
    gclog_or_tty->print_cr("Yielded disagrees with occupied:");
    gclog_or_tty->print_cr("  " SIZE_FORMAT_W(6) " yielded (" SIZE_FORMAT_W(6)
                  " coarse, " SIZE_FORMAT_W(6) " fine).",
                  iter.n_yielded(),
                  iter.n_yielded_coarse(), iter.n_yielded_fine());
    gclog_or_tty->print_cr("  " SIZE_FORMAT_W(6) " occ     (" SIZE_FORMAT_W(6)
                           " coarse, " SIZE_FORMAT_W(6) " fine).",
                  occupied(), occ_coarse(), occ_fine());
  }
  guarantee(iter.n_yielded() == occupied(),
            "We should have yielded all the represented cards.");
}
#endif

void HeapRegionRemSet::cleanup() {
  SparsePRT::cleanup_all();
}

void HeapRegionRemSet::clear() {
  MutexLockerEx x(&_m, Mutex::_no_safepoint_check_flag);
  clear_locked();
}

void HeapRegionRemSet::clear_locked() {
  _code_roots.clear();
  _other_regions.clear();
  assert(occupied_locked() == 0, "Should be clear.");
  reset_for_par_iteration();
}

void HeapRegionRemSet::reset_for_par_iteration() {
  _iter_state = Unclaimed;
  _iter_claimed = 0;
  // It's good to check this to make sure that the two methods are in sync.
  assert(verify_ready_for_par_iteration(), "post-condition");
}

void HeapRegionRemSet::scrub(CardTableModRefBS* ctbs,
                             BitMap* region_bm, BitMap* card_bm) {
  _other_regions.scrub(ctbs, region_bm, card_bm);
}

// Code roots support
//
// The code root set is protected by two separate locking schemes
// When at safepoint the per-hrrs lock must be held during modifications
// except when doing a full gc.
// When not at safepoint the CodeCache_lock must be held during modifications.
// When concurrent readers access the contains() function
// (during the evacuation phase) no removals are allowed.

void HeapRegionRemSet::add_strong_code_root(nmethod* nm) {
  assert(nm != NULL, "sanity");
  // Optimistic unlocked contains-check
  if (!_code_roots.contains(nm)) {
    MutexLockerEx ml(&_m, Mutex::_no_safepoint_check_flag);
    add_strong_code_root_locked(nm);
  }
}

void HeapRegionRemSet::add_strong_code_root_locked(nmethod* nm) {
  assert(nm != NULL, "sanity");
  _code_roots.add(nm);
}

void HeapRegionRemSet::remove_strong_code_root(nmethod* nm) {
  assert(nm != NULL, "sanity");
  assert_locked_or_safepoint(CodeCache_lock);

  MutexLockerEx ml(CodeCache_lock->owned_by_self() ? NULL : &_m, Mutex::_no_safepoint_check_flag);
  _code_roots.remove(nm);

  // Check that there were no duplicates
  guarantee(!_code_roots.contains(nm), "duplicate entry found");
}

void HeapRegionRemSet::strong_code_roots_do(CodeBlobClosure* blk) const {
  _code_roots.nmethods_do(blk);
}

void HeapRegionRemSet::clean_strong_code_roots(HeapRegion* hr) {
  _code_roots.clean(hr);
}

size_t HeapRegionRemSet::strong_code_roots_mem_size() {
  return _code_roots.mem_size();
}

HeapRegionRemSetIterator:: HeapRegionRemSetIterator(HeapRegionRemSet* hrrs) :
  _hrrs(hrrs),
  _g1h(G1CollectedHeap::heap()),
  _coarse_map(&hrrs->_other_regions._coarse_map),
  _bosa(hrrs->_bosa),
  _is(Sparse),
  // Set these values so that we increment to the first region.
  _coarse_cur_region_index(-1),
  _coarse_cur_region_cur_card(HeapRegion::CardsPerRegion-1),
  _cur_card_in_prt(HeapRegion::CardsPerRegion),
  _fine_cur_prt(NULL),
  _n_yielded_coarse(0),
  _n_yielded_fine(0),
  _n_yielded_sparse(0),
  _sparse_iter(&hrrs->_other_regions._sparse_table) {}

bool HeapRegionRemSetIterator::coarse_has_next(size_t& card_index) {
  if (_hrrs->_other_regions._n_coarse_entries == 0) return false;
  // Go to the next card.
  _coarse_cur_region_cur_card++;
  // Was the last the last card in the current region?
  if (_coarse_cur_region_cur_card == HeapRegion::CardsPerRegion) {
    // Yes: find the next region.  This may leave _coarse_cur_region_index
    // Set to the last index, in which case there are no more coarse
    // regions.
    _coarse_cur_region_index =
      (int) _coarse_map->get_next_one_offset(_coarse_cur_region_index + 1);
    if ((size_t)_coarse_cur_region_index < _coarse_map->size()) {
      _coarse_cur_region_cur_card = 0;
      HeapWord* r_bot =
        _g1h->region_at((uint) _coarse_cur_region_index)->bottom();
      _cur_region_card_offset = _bosa->index_for(r_bot);
    } else {
      return false;
    }
  }
  // If we didn't return false above, then we can yield a card.
  card_index = _cur_region_card_offset + _coarse_cur_region_cur_card;
  return true;
}

bool HeapRegionRemSetIterator::fine_has_next(size_t& card_index) {
  if (fine_has_next()) {
    _cur_card_in_prt =
      _fine_cur_prt->_bm.get_next_one_offset(_cur_card_in_prt + 1);
  }
  if (_cur_card_in_prt == HeapRegion::CardsPerRegion) {
    // _fine_cur_prt may still be NULL in case if there are not PRTs at all for
    // the remembered set.
    if (_fine_cur_prt == NULL || _fine_cur_prt->next() == NULL) {
      return false;
    }
    PerRegionTable* next_prt = _fine_cur_prt->next();
    switch_to_prt(next_prt);
    _cur_card_in_prt = _fine_cur_prt->_bm.get_next_one_offset(_cur_card_in_prt + 1);
  }

  card_index = _cur_region_card_offset + _cur_card_in_prt;
  guarantee(_cur_card_in_prt < HeapRegion::CardsPerRegion,
            "Card index " SIZE_FORMAT " must be within the region", _cur_card_in_prt);
  return true;
}

bool HeapRegionRemSetIterator::fine_has_next() {
  return _cur_card_in_prt != HeapRegion::CardsPerRegion;
}

void HeapRegionRemSetIterator::switch_to_prt(PerRegionTable* prt) {
  assert(prt != NULL, "Cannot switch to NULL prt");
  _fine_cur_prt = prt;

  HeapWord* r_bot = _fine_cur_prt->hr()->bottom();
  _cur_region_card_offset = _bosa->index_for(r_bot);

  // The bitmap scan for the PRT always scans from _cur_region_cur_card + 1.
  // To avoid special-casing this start case, and not miss the first bitmap
  // entry, initialize _cur_region_cur_card with -1 instead of 0.
  _cur_card_in_prt = (size_t)-1;
}

bool HeapRegionRemSetIterator::has_next(size_t& card_index) {
  switch (_is) {
  case Sparse: {
    if (_sparse_iter.has_next(card_index)) {
      _n_yielded_sparse++;
      return true;
    }
    // Otherwise, deliberate fall-through
    _is = Fine;
    PerRegionTable* initial_fine_prt = _hrrs->_other_regions._first_all_fine_prts;
    if (initial_fine_prt != NULL) {
      switch_to_prt(_hrrs->_other_regions._first_all_fine_prts);
    }
  }
  case Fine:
    if (fine_has_next(card_index)) {
      _n_yielded_fine++;
      return true;
    }
    // Otherwise, deliberate fall-through
    _is = Coarse;
  case Coarse:
    if (coarse_has_next(card_index)) {
      _n_yielded_coarse++;
      return true;
    }
    // Otherwise...
    break;
  }
  assert(ParallelGCThreads > 1 ||
         n_yielded() == _hrrs->occupied(),
         "Should have yielded all the cards in the rem set "
         "(in the non-par case).");
  return false;
}

void HeapRegionRemSet::reset_for_cleanup_tasks() {
  SparsePRT::reset_for_cleanup_tasks();
}

void HeapRegionRemSet::do_cleanup_work(HRRSCleanupTask* hrrs_cleanup_task) {
  _other_regions.do_cleanup_work(hrrs_cleanup_task);
}

void
HeapRegionRemSet::finish_cleanup_task(HRRSCleanupTask* hrrs_cleanup_task) {
  SparsePRT::finish_cleanup_task(hrrs_cleanup_task);
}

#ifndef PRODUCT
void PerRegionTable::test_fl_mem_size() {
  PerRegionTable* dummy = alloc(NULL);

  size_t min_prt_size = sizeof(void*) + dummy->bm()->size_in_words() * HeapWordSize;
  assert(dummy->mem_size() > min_prt_size,
         "PerRegionTable memory usage is suspiciously small, only has " SIZE_FORMAT " bytes. "
         "Should be at least " SIZE_FORMAT " bytes.", dummy->mem_size(), min_prt_size);
  free(dummy);
  guarantee(dummy->mem_size() == fl_mem_size(), "fl_mem_size() does not return the correct element size");
  // try to reset the state
  _free_list = NULL;
  delete dummy;
}

void HeapRegionRemSet::test_prt() {
  PerRegionTable::test_fl_mem_size();
}

void HeapRegionRemSet::test() {
  os::sleep(Thread::current(), (jlong)5000, false);
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // Run with "-XX:G1LogRSetRegionEntries=2", so that 1 and 5 end up in same
  // hash bucket.
  HeapRegion* hr0 = g1h->region_at(0);
  HeapRegion* hr1 = g1h->region_at(1);
  HeapRegion* hr2 = g1h->region_at(5);
  HeapRegion* hr3 = g1h->region_at(6);
  HeapRegion* hr4 = g1h->region_at(7);
  HeapRegion* hr5 = g1h->region_at(8);

  HeapWord* hr1_start = hr1->bottom();
  HeapWord* hr1_mid = hr1_start + HeapRegion::GrainWords/2;
  HeapWord* hr1_last = hr1->end() - 1;

  HeapWord* hr2_start = hr2->bottom();
  HeapWord* hr2_mid = hr2_start + HeapRegion::GrainWords/2;
  HeapWord* hr2_last = hr2->end() - 1;

  HeapWord* hr3_start = hr3->bottom();
  HeapWord* hr3_mid = hr3_start + HeapRegion::GrainWords/2;
  HeapWord* hr3_last = hr3->end() - 1;

  HeapRegionRemSet* hrrs = hr0->rem_set();

  // Make three references from region 0x101...
  hrrs->add_reference((OopOrNarrowOopStar)hr1_start);
  hrrs->add_reference((OopOrNarrowOopStar)hr1_mid);
  hrrs->add_reference((OopOrNarrowOopStar)hr1_last);

  hrrs->add_reference((OopOrNarrowOopStar)hr2_start);
  hrrs->add_reference((OopOrNarrowOopStar)hr2_mid);
  hrrs->add_reference((OopOrNarrowOopStar)hr2_last);

  hrrs->add_reference((OopOrNarrowOopStar)hr3_start);
  hrrs->add_reference((OopOrNarrowOopStar)hr3_mid);
  hrrs->add_reference((OopOrNarrowOopStar)hr3_last);

  // Now cause a coarsening.
  hrrs->add_reference((OopOrNarrowOopStar)hr4->bottom());
  hrrs->add_reference((OopOrNarrowOopStar)hr5->bottom());

  // Now, does iteration yield these three?
  HeapRegionRemSetIterator iter(hrrs);
  size_t sum = 0;
  size_t card_index;
  while (iter.has_next(card_index)) {
    HeapWord* card_start =
      G1CollectedHeap::heap()->bot_shared()->address_for_index(card_index);
    gclog_or_tty->print_cr("  Card " PTR_FORMAT ".", p2i(card_start));
    sum++;
  }
  guarantee(sum == 11 - 3 + 2048, "Failure");
  guarantee(sum == hrrs->occupied(), "Failure");
}
#endif

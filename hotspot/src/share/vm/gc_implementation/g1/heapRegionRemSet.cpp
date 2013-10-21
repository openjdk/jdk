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
#include "gc_implementation/g1/concurrentG1Refine.hpp"
#include "gc_implementation/g1/g1BlockOffsetTable.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/space.inline.hpp"
#include "oops/oop.inline.hpp"
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

    if (G1TraceHeapRegionRememberedSet) {
      gclog_or_tty->print_cr("    PRT::Add_reference_work(" PTR_FORMAT "->" PTR_FORMAT").",
                             from,
                             UseCompressedOops
                             ? (void *)oopDesc::load_decode_heap_oop((narrowOop*)from)
                             : (void *)oopDesc::load_decode_heap_oop((oop*)from));
    }

    HeapRegion* loc_hr = hr();
    // If the test below fails, then this table was reused concurrently
    // with this operation.  This is OK, since the old table was coarsened,
    // and adding a bit to the new table is never incorrect.
    // If the table used to belong to a continues humongous region and is
    // now reused for the corresponding start humongous region, we need to
    // make sure that we detect this. Thus, we call is_in_reserved_raw()
    // instead of just is_in_reserved() here.
    if (loc_hr->is_in_reserved_raw(from)) {
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
    return sizeof(this) + _bm.size_in_words() * HeapWordSize;
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

OtherRegionsTable::OtherRegionsTable(HeapRegion* hr) :
  _g1h(G1CollectedHeap::heap()),
  _m(Mutex::leaf, "An OtherRegionsTable lock", true),
  _hr(hr),
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
                        mtGC, 0, AllocFailStrategy::RETURN_NULL);

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

int**  OtherRegionsTable::_from_card_cache = NULL;
size_t OtherRegionsTable::_from_card_cache_max_regions = 0;
size_t OtherRegionsTable::_from_card_cache_mem_size = 0;

void OtherRegionsTable::init_from_card_cache(size_t max_regions) {
  _from_card_cache_max_regions = max_regions;

  int n_par_rs = HeapRegionRemSet::num_par_rem_sets();
  _from_card_cache = NEW_C_HEAP_ARRAY(int*, n_par_rs, mtGC);
  for (int i = 0; i < n_par_rs; i++) {
    _from_card_cache[i] = NEW_C_HEAP_ARRAY(int, max_regions, mtGC);
    for (size_t j = 0; j < max_regions; j++) {
      _from_card_cache[i][j] = -1;  // An invalid value.
    }
  }
  _from_card_cache_mem_size = n_par_rs * max_regions * sizeof(int);
}

void OtherRegionsTable::shrink_from_card_cache(size_t new_n_regs) {
  for (int i = 0; i < HeapRegionRemSet::num_par_rem_sets(); i++) {
    assert(new_n_regs <= _from_card_cache_max_regions, "Must be within max.");
    for (size_t j = new_n_regs; j < _from_card_cache_max_regions; j++) {
      _from_card_cache[i][j] = -1;  // An invalid value.
    }
  }
}

#ifndef PRODUCT
void OtherRegionsTable::print_from_card_cache() {
  for (int i = 0; i < HeapRegionRemSet::num_par_rem_sets(); i++) {
    for (size_t j = 0; j < _from_card_cache_max_regions; j++) {
      gclog_or_tty->print_cr("_from_card_cache[%d][%d] = %d.",
                    i, j, _from_card_cache[i][j]);
    }
  }
}
#endif

void OtherRegionsTable::add_reference(OopOrNarrowOopStar from, int tid) {
  size_t cur_hrs_ind = (size_t) hr()->hrs_index();

  if (G1TraceHeapRegionRememberedSet) {
    gclog_or_tty->print_cr("ORT::add_reference_work(" PTR_FORMAT "->" PTR_FORMAT ").",
                                                    from,
                                                    UseCompressedOops
                                                    ? (void *)oopDesc::load_decode_heap_oop((narrowOop*)from)
                                                    : (void *)oopDesc::load_decode_heap_oop((oop*)from));
  }

  int from_card = (int)(uintptr_t(from) >> CardTableModRefBS::card_shift);

  if (G1TraceHeapRegionRememberedSet) {
    gclog_or_tty->print_cr("Table for [" PTR_FORMAT "...): card %d (cache = %d)",
                  hr()->bottom(), from_card,
                  _from_card_cache[tid][cur_hrs_ind]);
  }

  if (from_card == _from_card_cache[tid][cur_hrs_ind]) {
    if (G1TraceHeapRegionRememberedSet) {
      gclog_or_tty->print_cr("  from-card cache hit.");
    }
    assert(contains_reference(from), "We just added it!");
    return;
  } else {
    _from_card_cache[tid][cur_hrs_ind] = from_card;
  }

  // Note that this may be a continued H region.
  HeapRegion* from_hr = _g1h->heap_region_containing_raw(from);
  RegionIdx_t from_hrs_ind = (RegionIdx_t) from_hr->hrs_index();

  // If the region is already coarsened, return.
  if (_coarse_map.at(from_hrs_ind)) {
    if (G1TraceHeapRegionRememberedSet) {
      gclog_or_tty->print_cr("  coarse map hit.");
    }
    assert(contains_reference(from), "We just added it!");
    return;
  }

  // Otherwise find a per-region table to add it to.
  size_t ind = from_hrs_ind & _mod_max_fine_entries_mask;
  PerRegionTable* prt = find_region_table(ind, from_hr);
  if (prt == NULL) {
    MutexLockerEx x(&_m, Mutex::_no_safepoint_check_flag);
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
          _sparse_table.add_card(from_hrs_ind, card_index)) {
        if (G1RecordHRRSOops) {
          HeapRegionRemSet::record(hr(), from);
          if (G1TraceHeapRegionRememberedSet) {
            gclog_or_tty->print("   Added card " PTR_FORMAT " to region "
                                "[" PTR_FORMAT "...) for ref " PTR_FORMAT ".\n",
                                align_size_down(uintptr_t(from),
                                                CardTableModRefBS::card_size),
                                hr()->bottom(), from);
          }
        }
        if (G1TraceHeapRegionRememberedSet) {
          gclog_or_tty->print_cr("   added card to sparse table.");
        }
        assert(contains_reference_locked(from), "We just added it!");
        return;
      } else {
        if (G1TraceHeapRegionRememberedSet) {
          gclog_or_tty->print_cr("   [tid %d] sparse table entry "
                        "overflow(f: %d, t: %d)",
                        tid, from_hrs_ind, cur_hrs_ind);
        }
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
      _fine_grain_regions[ind] = prt;
      _n_fine_entries++;

      if (G1HRRSUseSparseTable) {
        // Transfer from sparse to fine-grain.
        SparsePRTEntry *sprt_entry = _sparse_table.get_entry(from_hrs_ind);
        assert(sprt_entry != NULL, "There should have been an entry");
        for (int i = 0; i < SparsePRTEntry::cards_num(); i++) {
          CardIdx_t c = sprt_entry->card(i);
          if (c != SparsePRTEntry::NullEntry) {
            prt->add_card(c);
          }
        }
        // Now we can delete the sparse entry.
        bool res = _sparse_table.delete_entry(from_hrs_ind);
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

  if (G1RecordHRRSOops) {
    HeapRegionRemSet::record(hr(), from);
    if (G1TraceHeapRegionRememberedSet) {
      gclog_or_tty->print("Added card " PTR_FORMAT " to region "
                          "[" PTR_FORMAT "...) for ref " PTR_FORMAT ".\n",
                          align_size_down(uintptr_t(from),
                                          CardTableModRefBS::card_size),
                          hr()->bottom(), from);
    }
  }
  assert(contains_reference(from), "We just added it!");
}

PerRegionTable*
OtherRegionsTable::find_region_table(size_t ind, HeapRegion* hr) const {
  assert(0 <= ind && ind < _max_fine_entries, "Preconditions.");
  PerRegionTable* prt = _fine_grain_regions[ind];
  while (prt != NULL && prt->hr() != hr) {
    prt = prt->collision_list_next();
  }
  // Loop postcondition is the method postcondition.
  return prt;
}

jint OtherRegionsTable::_n_coarsenings = 0;

PerRegionTable* OtherRegionsTable::delete_region_table() {
  assert(_m.owned_by_self(), "Precondition");
  assert(_n_fine_entries == _max_fine_entries, "Precondition");
  PerRegionTable* max = NULL;
  jint max_occ = 0;
  PerRegionTable** max_prev;
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

  // Set the corresponding coarse bit.
  size_t max_hrs_index = (size_t) max->hr()->hrs_index();
  if (!_coarse_map.at(max_hrs_index)) {
    _coarse_map.at_put(max_hrs_index, true);
    _n_coarse_entries++;
    if (G1TraceHeapRegionRememberedSet) {
      gclog_or_tty->print("Coarsened entry in region [" PTR_FORMAT "...] "
                 "for region [" PTR_FORMAT "...] (%d coarse entries).\n",
                 hr()->bottom(),
                 max->hr()->bottom(),
                 _n_coarse_entries);
    }
  }

  // Unsplice.
  *max_prev = max->collision_list_next();
  Atomic::inc(&_n_coarsenings);
  _n_fine_entries--;
  return max;
}


// At present, this must be called stop-world single-threaded.
void OtherRegionsTable::scrub(CardTableModRefBS* ctbs,
                              BitMap* region_bm, BitMap* card_bm) {
  // First eliminated garbage regions from the coarse map.
  if (G1RSScrubVerbose) {
    gclog_or_tty->print_cr("Scrubbing region %u:", hr()->hrs_index());
  }

  assert(_coarse_map.size() == region_bm->size(), "Precondition");
  if (G1RSScrubVerbose) {
    gclog_or_tty->print("   Coarse map: before = "SIZE_FORMAT"...",
                        _n_coarse_entries);
  }
  _coarse_map.set_intersection(*region_bm);
  _n_coarse_entries = _coarse_map.count_one_bits();
  if (G1RSScrubVerbose) {
    gclog_or_tty->print_cr("   after = "SIZE_FORMAT".", _n_coarse_entries);
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
                               cur->hr()->hrs_index());
      }
      if (!region_bm->at((size_t) cur->hr()->hrs_index())) {
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


size_t OtherRegionsTable::occupied() const {
  // Cast away const in this case.
  MutexLockerEx x((Mutex*)&_m, Mutex::_no_safepoint_check_flag);
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
  // Cast away const in this case.
  MutexLockerEx x((Mutex*)&_m, Mutex::_no_safepoint_check_flag);
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
  sum += sizeof(*this) - sizeof(_sparse_table); // Avoid double counting above.
  return sum;
}

size_t OtherRegionsTable::static_mem_size() {
  return _from_card_cache_mem_size;
}

size_t OtherRegionsTable::fl_mem_size() {
  return PerRegionTable::fl_mem_size();
}

void OtherRegionsTable::clear_fcc() {
  size_t hrs_idx = hr()->hrs_index();
  for (int i = 0; i < HeapRegionRemSet::num_par_rem_sets(); i++) {
    _from_card_cache[i][hrs_idx] = -1;
  }
}

void OtherRegionsTable::clear() {
  MutexLockerEx x(&_m, Mutex::_no_safepoint_check_flag);
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

void OtherRegionsTable::clear_incoming_entry(HeapRegion* from_hr) {
  MutexLockerEx x(&_m, Mutex::_no_safepoint_check_flag);
  size_t hrs_ind = (size_t) from_hr->hrs_index();
  size_t ind = hrs_ind & _mod_max_fine_entries_mask;
  if (del_single_region_table(ind, from_hr)) {
    assert(!_coarse_map.at(hrs_ind), "Inv");
  } else {
    _coarse_map.par_at_put(hrs_ind, 0);
  }
  // Check to see if any of the fcc entries come from here.
  size_t hr_ind = (size_t) hr()->hrs_index();
  for (int tid = 0; tid < HeapRegionRemSet::num_par_rem_sets(); tid++) {
    int fcc_ent = _from_card_cache[tid][hr_ind];
    if (fcc_ent != -1) {
      HeapWord* card_addr = (HeapWord*)
        (uintptr_t(fcc_ent) << CardTableModRefBS::card_shift);
      if (hr()->is_in_reserved(card_addr)) {
        // Clear the from card cache.
        _from_card_cache[tid][hr_ind] = -1;
      }
    }
  }
}

bool OtherRegionsTable::del_single_region_table(size_t ind,
                                                HeapRegion* hr) {
  assert(0 <= ind && ind < _max_fine_entries, "Preconditions.");
  PerRegionTable** prev_addr = &_fine_grain_regions[ind];
  PerRegionTable* prt = *prev_addr;
  while (prt != NULL && prt->hr() != hr) {
    prev_addr = prt->collision_list_next_addr();
    prt = prt->collision_list_next();
  }
  if (prt != NULL) {
    assert(prt->hr() == hr, "Loop postcondition.");
    *prev_addr = prt->collision_list_next();
    unlink_from_all(prt);
    PerRegionTable::free(prt);
    _n_fine_entries--;
    return true;
  } else {
    return false;
  }
}

bool OtherRegionsTable::contains_reference(OopOrNarrowOopStar from) const {
  // Cast away const in this case.
  MutexLockerEx x((Mutex*)&_m, Mutex::_no_safepoint_check_flag);
  return contains_reference_locked(from);
}

bool OtherRegionsTable::contains_reference_locked(OopOrNarrowOopStar from) const {
  HeapRegion* hr = _g1h->heap_region_containing_raw(from);
  if (hr == NULL) return false;
  RegionIdx_t hr_ind = (RegionIdx_t) hr->hrs_index();
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
int HeapRegionRemSet::num_par_rem_sets() {
  return (int)MAX2(DirtyCardQueueSet::num_par_ids() + ConcurrentG1Refine::thread_num(), ParallelGCThreads);
}

HeapRegionRemSet::HeapRegionRemSet(G1BlockOffsetSharedArray* bosa,
                                   HeapRegion* hr)
  : _bosa(bosa), _strong_code_roots_list(NULL), _other_regions(hr) {
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
void HeapRegionRemSet::print() const {
  HeapRegionRemSetIterator iter(this);
  size_t card_index;
  while (iter.has_next(card_index)) {
    HeapWord* card_start =
      G1CollectedHeap::heap()->bot_shared()->address_for_index(card_index);
    gclog_or_tty->print_cr("  Card " PTR_FORMAT, card_start);
  }
  if (iter.n_yielded() != occupied()) {
    gclog_or_tty->print_cr("Yielded disagrees with occupied:");
    gclog_or_tty->print_cr("  %6d yielded (%6d coarse, %6d fine).",
                  iter.n_yielded(),
                  iter.n_yielded_coarse(), iter.n_yielded_fine());
    gclog_or_tty->print_cr("  %6d occ     (%6d coarse, %6d fine).",
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
  if (_strong_code_roots_list != NULL) {
    delete _strong_code_roots_list;
  }
  _strong_code_roots_list = new (ResourceObj::C_HEAP, mtGC)
                                GrowableArray<nmethod*>(10, 0, NULL, true);

  _other_regions.clear();
  assert(occupied() == 0, "Should be clear.");
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

void HeapRegionRemSet::add_strong_code_root(nmethod* nm) {
  assert(nm != NULL, "sanity");
  // Search for the code blob from the RHS to avoid
  // duplicate entries as much as possible
  if (_strong_code_roots_list->find_from_end(nm) < 0) {
    // Code blob isn't already in the list
    _strong_code_roots_list->push(nm);
  }
}

void HeapRegionRemSet::remove_strong_code_root(nmethod* nm) {
  assert(nm != NULL, "sanity");
  int idx = _strong_code_roots_list->find(nm);
  if (idx >= 0) {
    _strong_code_roots_list->remove_at(idx);
  }
  // Check that there were no duplicates
  guarantee(_strong_code_roots_list->find(nm) < 0, "duplicate entry found");
}

class NMethodMigrationOopClosure : public OopClosure {
  G1CollectedHeap* _g1h;
  HeapRegion* _from;
  nmethod* _nm;

  uint _num_self_forwarded;

  template <class T> void do_oop_work(T* p) {
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      if (_from->is_in(obj)) {
        // Reference still points into the source region.
        // Since roots are immediately evacuated this means that
        // we must have self forwarded the object
        assert(obj->is_forwarded(),
               err_msg("code roots should be immediately evacuated. "
                       "Ref: "PTR_FORMAT", "
                       "Obj: "PTR_FORMAT", "
                       "Region: "HR_FORMAT,
                       p, (void*) obj, HR_FORMAT_PARAMS(_from)));
        assert(obj->forwardee() == obj,
               err_msg("not self forwarded? obj = "PTR_FORMAT, (void*)obj));

        // The object has been self forwarded.
        // Note, if we're during an initial mark pause, there is
        // no need to explicitly mark object. It will be marked
        // during the regular evacuation failure handling code.
        _num_self_forwarded++;
      } else {
        // The reference points into a promotion or to-space region
        HeapRegion* to = _g1h->heap_region_containing(obj);
        to->rem_set()->add_strong_code_root(_nm);
      }
    }
  }

public:
  NMethodMigrationOopClosure(G1CollectedHeap* g1h, HeapRegion* from, nmethod* nm):
    _g1h(g1h), _from(from), _nm(nm), _num_self_forwarded(0) {}

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }

  uint retain() { return _num_self_forwarded > 0; }
};

void HeapRegionRemSet::migrate_strong_code_roots() {
  assert(hr()->in_collection_set(), "only collection set regions");
  assert(!hr()->isHumongous(), "not humongous regions");

  ResourceMark rm;

  // List of code blobs to retain for this region
  GrowableArray<nmethod*> to_be_retained(10);
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  while (_strong_code_roots_list->is_nonempty()) {
    nmethod *nm = _strong_code_roots_list->pop();
    if (nm != NULL) {
      NMethodMigrationOopClosure oop_cl(g1h, hr(), nm);
      nm->oops_do(&oop_cl);
      if (oop_cl.retain()) {
        to_be_retained.push(nm);
      }
    }
  }

  // Now push any code roots we need to retain
  assert(to_be_retained.is_empty() || hr()->evacuation_failed(),
         "Retained nmethod list must be empty or "
         "evacuation of this region failed");

  while (to_be_retained.is_nonempty()) {
    nmethod* nm = to_be_retained.pop();
    assert(nm != NULL, "sanity");
    add_strong_code_root(nm);
  }
}

void HeapRegionRemSet::strong_code_roots_do(CodeBlobClosure* blk) const {
  for (int i = 0; i < _strong_code_roots_list->length(); i += 1) {
    nmethod* nm = _strong_code_roots_list->at(i);
    blk->do_code_blob(nm);
  }
}

size_t HeapRegionRemSet::strong_code_roots_mem_size() {
  return sizeof(GrowableArray<nmethod*>) +
         _strong_code_roots_list->max_length() * sizeof(nmethod*);
}

//-------------------- Iteration --------------------

HeapRegionRemSetIterator:: HeapRegionRemSetIterator(const HeapRegionRemSet* hrrs) :
  _hrrs(hrrs),
  _g1h(G1CollectedHeap::heap()),
  _coarse_map(&hrrs->_other_regions._coarse_map),
  _fine_grain_regions(hrrs->_other_regions._fine_grain_regions),
  _bosa(hrrs->bosa()),
  _is(Sparse),
  // Set these values so that we increment to the first region.
  _coarse_cur_region_index(-1),
  _coarse_cur_region_cur_card(HeapRegion::CardsPerRegion-1),
  _cur_region_cur_card(0),
  _fine_array_index(-1),
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

void HeapRegionRemSetIterator::fine_find_next_non_null_prt() {
  // Otherwise, find the next bucket list in the array.
  _fine_array_index++;
  while (_fine_array_index < (int) OtherRegionsTable::_max_fine_entries) {
    _fine_cur_prt = _fine_grain_regions[_fine_array_index];
    if (_fine_cur_prt != NULL) return;
    else _fine_array_index++;
  }
  assert(_fine_cur_prt == NULL, "Loop post");
}

bool HeapRegionRemSetIterator::fine_has_next(size_t& card_index) {
  if (fine_has_next()) {
    _cur_region_cur_card =
      _fine_cur_prt->_bm.get_next_one_offset(_cur_region_cur_card + 1);
  }
  while (!fine_has_next()) {
    if (_cur_region_cur_card == (size_t) HeapRegion::CardsPerRegion) {
      _cur_region_cur_card = 0;
      _fine_cur_prt = _fine_cur_prt->collision_list_next();
    }
    if (_fine_cur_prt == NULL) {
      fine_find_next_non_null_prt();
      if (_fine_cur_prt == NULL) return false;
    }
    assert(_fine_cur_prt != NULL && _cur_region_cur_card == 0,
           "inv.");
    HeapWord* r_bot =
      _fine_cur_prt->hr()->bottom();
    _cur_region_card_offset = _bosa->index_for(r_bot);
    _cur_region_cur_card = _fine_cur_prt->_bm.get_next_one_offset(0);
  }
  assert(fine_has_next(), "Or else we exited the loop via the return.");
  card_index = _cur_region_card_offset + _cur_region_cur_card;
  return true;
}

bool HeapRegionRemSetIterator::fine_has_next() {
  return
    _fine_cur_prt != NULL &&
    _cur_region_cur_card < HeapRegion::CardsPerRegion;
}

bool HeapRegionRemSetIterator::has_next(size_t& card_index) {
  switch (_is) {
  case Sparse:
    if (_sparse_iter.has_next(card_index)) {
      _n_yielded_sparse++;
      return true;
    }
    // Otherwise, deliberate fall-through
    _is = Fine;
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



OopOrNarrowOopStar* HeapRegionRemSet::_recorded_oops = NULL;
HeapWord**          HeapRegionRemSet::_recorded_cards = NULL;
HeapRegion**        HeapRegionRemSet::_recorded_regions = NULL;
int                 HeapRegionRemSet::_n_recorded = 0;

HeapRegionRemSet::Event* HeapRegionRemSet::_recorded_events = NULL;
int*         HeapRegionRemSet::_recorded_event_index = NULL;
int          HeapRegionRemSet::_n_recorded_events = 0;

void HeapRegionRemSet::record(HeapRegion* hr, OopOrNarrowOopStar f) {
  if (_recorded_oops == NULL) {
    assert(_n_recorded == 0
           && _recorded_cards == NULL
           && _recorded_regions == NULL,
           "Inv");
    _recorded_oops    = NEW_C_HEAP_ARRAY(OopOrNarrowOopStar, MaxRecorded, mtGC);
    _recorded_cards   = NEW_C_HEAP_ARRAY(HeapWord*,          MaxRecorded, mtGC);
    _recorded_regions = NEW_C_HEAP_ARRAY(HeapRegion*,        MaxRecorded, mtGC);
  }
  if (_n_recorded == MaxRecorded) {
    gclog_or_tty->print_cr("Filled up 'recorded' (%d).", MaxRecorded);
  } else {
    _recorded_cards[_n_recorded] =
      (HeapWord*)align_size_down(uintptr_t(f),
                                 CardTableModRefBS::card_size);
    _recorded_oops[_n_recorded] = f;
    _recorded_regions[_n_recorded] = hr;
    _n_recorded++;
  }
}

void HeapRegionRemSet::record_event(Event evnt) {
  if (!G1RecordHRRSEvents) return;

  if (_recorded_events == NULL) {
    assert(_n_recorded_events == 0
           && _recorded_event_index == NULL,
           "Inv");
    _recorded_events = NEW_C_HEAP_ARRAY(Event, MaxRecordedEvents, mtGC);
    _recorded_event_index = NEW_C_HEAP_ARRAY(int, MaxRecordedEvents, mtGC);
  }
  if (_n_recorded_events == MaxRecordedEvents) {
    gclog_or_tty->print_cr("Filled up 'recorded_events' (%d).", MaxRecordedEvents);
  } else {
    _recorded_events[_n_recorded_events] = evnt;
    _recorded_event_index[_n_recorded_events] = _n_recorded;
    _n_recorded_events++;
  }
}

void HeapRegionRemSet::print_event(outputStream* str, Event evnt) {
  switch (evnt) {
  case Event_EvacStart:
    str->print("Evac Start");
    break;
  case Event_EvacEnd:
    str->print("Evac End");
    break;
  case Event_RSUpdateEnd:
    str->print("RS Update End");
    break;
  }
}

void HeapRegionRemSet::print_recorded() {
  int cur_evnt = 0;
  Event cur_evnt_kind;
  int cur_evnt_ind = 0;
  if (_n_recorded_events > 0) {
    cur_evnt_kind = _recorded_events[cur_evnt];
    cur_evnt_ind = _recorded_event_index[cur_evnt];
  }

  for (int i = 0; i < _n_recorded; i++) {
    while (cur_evnt < _n_recorded_events && i == cur_evnt_ind) {
      gclog_or_tty->print("Event: ");
      print_event(gclog_or_tty, cur_evnt_kind);
      gclog_or_tty->print_cr("");
      cur_evnt++;
      if (cur_evnt < MaxRecordedEvents) {
        cur_evnt_kind = _recorded_events[cur_evnt];
        cur_evnt_ind = _recorded_event_index[cur_evnt];
      }
    }
    gclog_or_tty->print("Added card " PTR_FORMAT " to region [" PTR_FORMAT "...]"
                        " for ref " PTR_FORMAT ".\n",
                        _recorded_cards[i], _recorded_regions[i]->bottom(),
                        _recorded_oops[i]);
  }
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
    gclog_or_tty->print_cr("  Card " PTR_FORMAT ".", card_start);
    sum++;
  }
  guarantee(sum == 11 - 3 + 2048, "Failure");
  guarantee(sum == hrrs->occupied(), "Failure");
}
#endif

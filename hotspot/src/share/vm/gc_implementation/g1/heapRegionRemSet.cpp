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
#include "incls/_heapRegionRemSet.cpp.incl"

#define HRRS_VERBOSE 0

#define PRT_COUNT_OCCUPIED 1

// OtherRegionsTable

class PerRegionTable: public CHeapObj {
  friend class OtherRegionsTable;
  friend class HeapRegionRemSetIterator;

  HeapRegion*     _hr;
  BitMap          _bm;
#if PRT_COUNT_OCCUPIED
  jint            _occupied;
#endif
  PerRegionTable* _next_free;

  PerRegionTable* next_free() { return _next_free; }
  void set_next_free(PerRegionTable* prt) { _next_free = prt; }


  static PerRegionTable* _free_list;

#ifdef _MSC_VER
  // For some reason even though the classes are marked as friend they are unable
  // to access CardsPerRegion when private/protected. Only the windows c++ compiler
  // says this Sun CC and linux gcc don't have a problem with access when private

  public:

#endif // _MSC_VER

  enum SomePrivateConstants {
    CardsPerRegion = HeapRegion::GrainBytes >> CardTableModRefBS::card_shift
  };

protected:
  // We need access in order to union things into the base table.
  BitMap* bm() { return &_bm; }

#if PRT_COUNT_OCCUPIED
  void recount_occupied() {
    _occupied = (jint) bm()->count_one_bits();
  }
#endif

  PerRegionTable(HeapRegion* hr) :
    _hr(hr),
#if PRT_COUNT_OCCUPIED
    _occupied(0),
#endif
    _bm(CardsPerRegion, false /* in-resource-area */)
  {}

  static void free(PerRegionTable* prt) {
    while (true) {
      PerRegionTable* fl = _free_list;
      prt->set_next_free(fl);
      PerRegionTable* res =
        (PerRegionTable*)
        Atomic::cmpxchg_ptr(prt, &_free_list, fl);
      if (res == fl) return;
    }
    ShouldNotReachHere();
  }

  static PerRegionTable* alloc(HeapRegion* hr) {
    PerRegionTable* fl = _free_list;
    while (fl != NULL) {
      PerRegionTable* nxt = fl->next_free();
      PerRegionTable* res =
        (PerRegionTable*)
        Atomic::cmpxchg_ptr(nxt, &_free_list, fl);
      if (res == fl) {
        fl->init(hr);
        return fl;
      } else {
        fl = _free_list;
      }
    }
    assert(fl == NULL, "Loop condition.");
    return new PerRegionTable(hr);
  }

  void add_card_work(short from_card, bool par) {
    if (!_bm.at(from_card)) {
      if (par) {
        if (_bm.par_at_put(from_card, 1)) {
#if PRT_COUNT_OCCUPIED
          Atomic::inc(&_occupied);
#endif
        }
      } else {
        _bm.at_put(from_card, 1);
#if PRT_COUNT_OCCUPIED
        _occupied++;
#endif
      }
    }
  }

  void add_reference_work(oop* from, bool par) {
    // Must make this robust in case "from" is not in "_hr", because of
    // concurrency.

#if HRRS_VERBOSE
    gclog_or_tty->print_cr("    PRT::Add_reference_work(" PTR_FORMAT "->" PTR_FORMAT").",
                           from, *from);
#endif

    HeapRegion* loc_hr = hr();
    // If the test below fails, then this table was reused concurrently
    // with this operation.  This is OK, since the old table was coarsened,
    // and adding a bit to the new table is never incorrect.
    if (loc_hr->is_in_reserved(from)) {
      size_t hw_offset = pointer_delta((HeapWord*)from, loc_hr->bottom());
      size_t from_card =
        hw_offset >>
        (CardTableModRefBS::card_shift - LogHeapWordSize);

      add_card_work((short) from_card, par);
    }
  }

public:

  HeapRegion* hr() const { return _hr; }

#if PRT_COUNT_OCCUPIED
  jint occupied() const {
    // Overkill, but if we ever need it...
    // guarantee(_occupied == _bm.count_one_bits(), "Check");
    return _occupied;
  }
#else
  jint occupied() const {
    return _bm.count_one_bits();
  }
#endif

  void init(HeapRegion* hr) {
    _hr = hr;
#if PRT_COUNT_OCCUPIED
    _occupied = 0;
#endif
    _bm.clear();
  }

  void add_reference(oop* from) {
    add_reference_work(from, /*parallel*/ true);
  }

  void seq_add_reference(oop* from) {
    add_reference_work(from, /*parallel*/ false);
  }

  void scrub(CardTableModRefBS* ctbs, BitMap* card_bm) {
    HeapWord* hr_bot = hr()->bottom();
    size_t hr_first_card_index = ctbs->index_for(hr_bot);
    bm()->set_intersection_at_offset(*card_bm, hr_first_card_index);
#if PRT_COUNT_OCCUPIED
    recount_occupied();
#endif
  }

  void add_card(short from_card_index) {
    add_card_work(from_card_index, /*parallel*/ true);
  }

  void seq_add_card(short from_card_index) {
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

  static size_t fl_mem_size() {
    PerRegionTable* cur = _free_list;
    size_t res = 0;
    while (cur != NULL) {
      res += sizeof(PerRegionTable);
      cur = cur->next_free();
    }
    return res;
  }

  // Requires "from" to be in "hr()".
  bool contains_reference(oop* from) const {
    assert(hr()->is_in_reserved(from), "Precondition.");
    size_t card_ind = pointer_delta(from, hr()->bottom(),
                                    CardTableModRefBS::card_size);
    return _bm.at(card_ind);
  }
};

PerRegionTable* PerRegionTable::_free_list = NULL;


#define COUNT_PAR_EXPANDS 0

#if COUNT_PAR_EXPANDS
static jint n_par_expands = 0;
static jint n_par_contracts = 0;
static jint par_expand_list_len = 0;
static jint max_par_expand_list_len = 0;

static void print_par_expand() {
  Atomic::inc(&n_par_expands);
  Atomic::inc(&par_expand_list_len);
  if (par_expand_list_len > max_par_expand_list_len) {
    max_par_expand_list_len = par_expand_list_len;
  }
  if ((n_par_expands % 10) == 0) {
    gclog_or_tty->print_cr("\n\n%d par expands: %d contracts, "
                  "len = %d, max_len = %d\n.",
                  n_par_expands, n_par_contracts, par_expand_list_len,
                  max_par_expand_list_len);
  }
}
#endif

class PosParPRT: public PerRegionTable {
  PerRegionTable** _par_tables;

  enum SomePrivateConstants {
    ReserveParTableExpansion = 1
  };

  void par_expand() {
    int n = HeapRegionRemSet::num_par_rem_sets()-1;
    if (n <= 0) return;
    if (_par_tables == NULL) {
      PerRegionTable* res =
        (PerRegionTable*)
        Atomic::cmpxchg_ptr((PerRegionTable*)ReserveParTableExpansion,
                            &_par_tables, NULL);
      if (res != NULL) return;
      // Otherwise, we reserved the right to do the expansion.

      PerRegionTable** ptables = NEW_C_HEAP_ARRAY(PerRegionTable*, n);
      for (int i = 0; i < n; i++) {
        PerRegionTable* ptable = PerRegionTable::alloc(hr());
        ptables[i] = ptable;
      }
      // Here we do not need an atomic.
      _par_tables = ptables;
#if COUNT_PAR_EXPANDS
      print_par_expand();
#endif
      // We must put this table on the expanded list.
      PosParPRT* exp_head = _par_expanded_list;
      while (true) {
        set_next_par_expanded(exp_head);
        PosParPRT* res =
          (PosParPRT*)
          Atomic::cmpxchg_ptr(this, &_par_expanded_list, exp_head);
        if (res == exp_head) return;
        // Otherwise.
        exp_head = res;
      }
      ShouldNotReachHere();
    }
  }

  void par_contract() {
    assert(_par_tables != NULL, "Precondition.");
    int n = HeapRegionRemSet::num_par_rem_sets()-1;
    for (int i = 0; i < n; i++) {
      _par_tables[i]->union_bitmap_into(bm());
      PerRegionTable::free(_par_tables[i]);
      _par_tables[i] = NULL;
    }
#if PRT_COUNT_OCCUPIED
    // We must recount the "occupied."
    recount_occupied();
#endif
    FREE_C_HEAP_ARRAY(PerRegionTable*, _par_tables);
    _par_tables = NULL;
#if COUNT_PAR_EXPANDS
    Atomic::inc(&n_par_contracts);
    Atomic::dec(&par_expand_list_len);
#endif
  }

  static PerRegionTable** _par_table_fl;

  PosParPRT* _next;

  static PosParPRT* _free_list;

  PerRegionTable** par_tables() const {
    assert(uintptr_t(NULL) == 0, "Assumption.");
    if (uintptr_t(_par_tables) <= ReserveParTableExpansion)
      return NULL;
    else
      return _par_tables;
  }

  PosParPRT* _next_par_expanded;
  PosParPRT* next_par_expanded() { return _next_par_expanded; }
  void set_next_par_expanded(PosParPRT* ppprt) { _next_par_expanded = ppprt; }
  static PosParPRT* _par_expanded_list;

public:

  PosParPRT(HeapRegion* hr) : PerRegionTable(hr), _par_tables(NULL) {}

  jint occupied() const {
    jint res = PerRegionTable::occupied();
    if (par_tables() != NULL) {
      for (int i = 0; i < HeapRegionRemSet::num_par_rem_sets()-1; i++) {
        res += par_tables()[i]->occupied();
      }
    }
    return res;
  }

  void init(HeapRegion* hr) {
    PerRegionTable::init(hr);
    _next = NULL;
    if (par_tables() != NULL) {
      for (int i = 0; i < HeapRegionRemSet::num_par_rem_sets()-1; i++) {
        par_tables()[i]->init(hr);
      }
    }
  }

  static void free(PosParPRT* prt) {
    while (true) {
      PosParPRT* fl = _free_list;
      prt->set_next(fl);
      PosParPRT* res =
        (PosParPRT*)
        Atomic::cmpxchg_ptr(prt, &_free_list, fl);
      if (res == fl) return;
    }
    ShouldNotReachHere();
  }

  static PosParPRT* alloc(HeapRegion* hr) {
    PosParPRT* fl = _free_list;
    while (fl != NULL) {
      PosParPRT* nxt = fl->next();
      PosParPRT* res =
        (PosParPRT*)
        Atomic::cmpxchg_ptr(nxt, &_free_list, fl);
      if (res == fl) {
        fl->init(hr);
        return fl;
      } else {
        fl = _free_list;
      }
    }
    assert(fl == NULL, "Loop condition.");
    return new PosParPRT(hr);
  }

  PosParPRT* next() const { return _next; }
  void set_next(PosParPRT* nxt) { _next = nxt; }
  PosParPRT** next_addr() { return &_next; }

  void add_reference(oop* from, int tid) {
    // Expand if necessary.
    PerRegionTable** pt = par_tables();
    if (par_tables() == NULL && tid > 0 && hr()->is_gc_alloc_region()) {
      par_expand();
      pt = par_tables();
    }
    if (pt != NULL) {
      // We always have to assume that mods to table 0 are in parallel,
      // because of the claiming scheme in parallel expansion.  A thread
      // with tid != 0 that finds the table to be NULL, but doesn't succeed
      // in claiming the right of expanding it, will end up in the else
      // clause of the above if test.  That thread could be delayed, and a
      // thread 0 add reference could see the table expanded, and come
      // here.  Both threads would be adding in parallel.  But we get to
      // not use atomics for tids > 0.
      if (tid == 0) {
        PerRegionTable::add_reference(from);
      } else {
        pt[tid-1]->seq_add_reference(from);
      }
    } else {
      // Not expanded -- add to the base table.
      PerRegionTable::add_reference(from);
    }
  }

  void scrub(CardTableModRefBS* ctbs, BitMap* card_bm) {
    assert(_par_tables == NULL, "Precondition");
    PerRegionTable::scrub(ctbs, card_bm);
  }

  size_t mem_size() const {
    size_t res =
      PerRegionTable::mem_size() + sizeof(this) - sizeof(PerRegionTable);
    if (_par_tables != NULL) {
      for (int i = 0; i < HeapRegionRemSet::num_par_rem_sets()-1; i++) {
        res += _par_tables[i]->mem_size();
      }
    }
    return res;
  }

  static size_t fl_mem_size() {
    PosParPRT* cur = _free_list;
    size_t res = 0;
    while (cur != NULL) {
      res += sizeof(PosParPRT);
      cur = cur->next();
    }
    return res;
  }

  bool contains_reference(oop* from) const {
    if (PerRegionTable::contains_reference(from)) return true;
    if (_par_tables != NULL) {
      for (int i = 0; i < HeapRegionRemSet::num_par_rem_sets()-1; i++) {
        if (_par_tables[i]->contains_reference(from)) return true;
      }
    }
    return false;
  }

  static void par_contract_all();

};

void PosParPRT::par_contract_all() {
  PosParPRT* hd = _par_expanded_list;
  while (hd != NULL) {
    PosParPRT* nxt = hd->next_par_expanded();
    PosParPRT* res =
      (PosParPRT*)
      Atomic::cmpxchg_ptr(nxt, &_par_expanded_list, hd);
    if (res == hd) {
      // We claimed the right to contract this table.
      hd->set_next_par_expanded(NULL);
      hd->par_contract();
      hd = _par_expanded_list;
    } else {
      hd = res;
    }
  }
}

PosParPRT* PosParPRT::_free_list = NULL;
PosParPRT* PosParPRT::_par_expanded_list = NULL;

jint OtherRegionsTable::_cache_probes = 0;
jint OtherRegionsTable::_cache_hits = 0;

size_t OtherRegionsTable::_max_fine_entries = 0;
size_t OtherRegionsTable::_mod_max_fine_entries_mask = 0;
#if SAMPLE_FOR_EVICTION
size_t OtherRegionsTable::_fine_eviction_stride = 0;
size_t OtherRegionsTable::_fine_eviction_sample_size = 0;
#endif

OtherRegionsTable::OtherRegionsTable(HeapRegion* hr) :
  _g1h(G1CollectedHeap::heap()),
  _m(Mutex::leaf, "An OtherRegionsTable lock", true),
  _hr(hr),
  _coarse_map(G1CollectedHeap::heap()->max_regions(),
              false /* in-resource-area */),
  _fine_grain_regions(NULL),
  _n_fine_entries(0), _n_coarse_entries(0),
#if SAMPLE_FOR_EVICTION
  _fine_eviction_start(0),
#endif
  _sparse_table(hr)
{
  typedef PosParPRT* PosParPRTPtr;
  if (_max_fine_entries == 0) {
    assert(_mod_max_fine_entries_mask == 0, "Both or none.");
    _max_fine_entries = (1 << G1LogRSRegionEntries);
    _mod_max_fine_entries_mask = _max_fine_entries - 1;
#if SAMPLE_FOR_EVICTION
    assert(_fine_eviction_sample_size == 0
           && _fine_eviction_stride == 0, "All init at same time.");
    _fine_eviction_sample_size = MAX2((size_t)4, (size_t)G1LogRSRegionEntries);
    _fine_eviction_stride = _max_fine_entries / _fine_eviction_sample_size;
#endif
  }
  _fine_grain_regions = new PosParPRTPtr[_max_fine_entries];
  if (_fine_grain_regions == NULL)
    vm_exit_out_of_memory(sizeof(void*)*_max_fine_entries,
                          "Failed to allocate _fine_grain_entries.");
  for (size_t i = 0; i < _max_fine_entries; i++) {
    _fine_grain_regions[i] = NULL;
  }
}

int** OtherRegionsTable::_from_card_cache = NULL;
size_t OtherRegionsTable::_from_card_cache_max_regions = 0;
size_t OtherRegionsTable::_from_card_cache_mem_size = 0;

void OtherRegionsTable::init_from_card_cache(size_t max_regions) {
  _from_card_cache_max_regions = max_regions;

  int n_par_rs = HeapRegionRemSet::num_par_rem_sets();
  _from_card_cache = NEW_C_HEAP_ARRAY(int*, n_par_rs);
  for (int i = 0; i < n_par_rs; i++) {
    _from_card_cache[i] = NEW_C_HEAP_ARRAY(int, max_regions);
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

void OtherRegionsTable::add_reference(oop* from, int tid) {
  size_t cur_hrs_ind = hr()->hrs_index();

#if HRRS_VERBOSE
  gclog_or_tty->print_cr("ORT::add_reference_work(" PTR_FORMAT "->" PTR_FORMAT ").",
                                                  from, *from);
#endif

  int from_card = (int)(uintptr_t(from) >> CardTableModRefBS::card_shift);

#if HRRS_VERBOSE
  gclog_or_tty->print_cr("Table for [" PTR_FORMAT "...): card %d (cache = %d)",
                hr()->bottom(), from_card,
                _from_card_cache[tid][cur_hrs_ind]);
#endif

#define COUNT_CACHE 0
#if COUNT_CACHE
  jint p = Atomic::add(1, &_cache_probes);
  if ((p % 10000) == 0) {
    jint hits = _cache_hits;
    gclog_or_tty->print_cr("%d/%d = %5.2f%% RS cache hits.",
                  _cache_hits, p, 100.0* (float)hits/(float)p);
  }
#endif
  if (from_card == _from_card_cache[tid][cur_hrs_ind]) {
#if HRRS_VERBOSE
    gclog_or_tty->print_cr("  from-card cache hit.");
#endif
#if COUNT_CACHE
    Atomic::inc(&_cache_hits);
#endif
    assert(contains_reference(from), "We just added it!");
    return;
  } else {
    _from_card_cache[tid][cur_hrs_ind] = from_card;
  }

  // Note that this may be a continued H region.
  HeapRegion* from_hr = _g1h->heap_region_containing_raw(from);
  size_t from_hrs_ind = (size_t)from_hr->hrs_index();

  // If the region is already coarsened, return.
  if (_coarse_map.at(from_hrs_ind)) {
#if HRRS_VERBOSE
    gclog_or_tty->print_cr("  coarse map hit.");
#endif
    assert(contains_reference(from), "We just added it!");
    return;
  }

  // Otherwise find a per-region table to add it to.
  size_t ind = from_hrs_ind & _mod_max_fine_entries_mask;
  PosParPRT* prt = find_region_table(ind, from_hr);
  if (prt == NULL) {
    MutexLockerEx x(&_m, Mutex::_no_safepoint_check_flag);
    // Confirm that it's really not there...
    prt = find_region_table(ind, from_hr);
    if (prt == NULL) {

      uintptr_t from_hr_bot_card_index =
        uintptr_t(from_hr->bottom())
          >> CardTableModRefBS::card_shift;
      int card_index = from_card - from_hr_bot_card_index;
      assert(0 <= card_index && card_index < PosParPRT::CardsPerRegion,
             "Must be in range.");
      if (G1HRRSUseSparseTable &&
          _sparse_table.add_card((short) from_hrs_ind, card_index)) {
        if (G1RecordHRRSOops) {
          HeapRegionRemSet::record(hr(), from);
#if HRRS_VERBOSE
          gclog_or_tty->print("   Added card " PTR_FORMAT " to region "
                              "[" PTR_FORMAT "...) for ref " PTR_FORMAT ".\n",
                              align_size_down(uintptr_t(from),
                                              CardTableModRefBS::card_size),
                              hr()->bottom(), from);
#endif
        }
#if HRRS_VERBOSE
        gclog_or_tty->print_cr("   added card to sparse table.");
#endif
        assert(contains_reference_locked(from), "We just added it!");
        return;
      } else {
#if HRRS_VERBOSE
        gclog_or_tty->print_cr("   [tid %d] sparse table entry "
                      "overflow(f: %d, t: %d)",
                      tid, from_hrs_ind, cur_hrs_ind);
#endif
      }

      // Otherwise, transfer from sparse to fine-grain.
      short cards[SparsePRTEntry::CardsPerEntry];
      if (G1HRRSUseSparseTable) {
        bool res = _sparse_table.get_cards((short) from_hrs_ind, &cards[0]);
        assert(res, "There should have been an entry");
      }

      if (_n_fine_entries == _max_fine_entries) {
        prt = delete_region_table();
      } else {
        prt = PosParPRT::alloc(from_hr);
      }
      prt->init(from_hr);
      // Record the outgoing pointer in the from_region's outgoing bitmap.
      from_hr->rem_set()->add_outgoing_reference(hr());

      PosParPRT* first_prt = _fine_grain_regions[ind];
      prt->set_next(first_prt);  // XXX Maybe move to init?
      _fine_grain_regions[ind] = prt;
      _n_fine_entries++;

      // Add in the cards from the sparse table.
      if (G1HRRSUseSparseTable) {
        for (int i = 0; i < SparsePRTEntry::CardsPerEntry; i++) {
          short c = cards[i];
          if (c != SparsePRTEntry::NullEntry) {
            prt->add_card(c);
          }
        }
        // Now we can delete the sparse entry.
        bool res = _sparse_table.delete_entry((short) from_hrs_ind);
        assert(res, "It should have been there.");
      }
    }
    assert(prt != NULL && prt->hr() == from_hr, "consequence");
  }
  // Note that we can't assert "prt->hr() == from_hr", because of the
  // possibility of concurrent reuse.  But see head comment of
  // OtherRegionsTable for why this is OK.
  assert(prt != NULL, "Inv");

  prt->add_reference(from, tid);
  if (G1RecordHRRSOops) {
    HeapRegionRemSet::record(hr(), from);
#if HRRS_VERBOSE
    gclog_or_tty->print("Added card " PTR_FORMAT " to region "
                        "[" PTR_FORMAT "...) for ref " PTR_FORMAT ".\n",
                        align_size_down(uintptr_t(from),
                                        CardTableModRefBS::card_size),
                        hr()->bottom(), from);
#endif
  }
  assert(contains_reference(from), "We just added it!");
}

PosParPRT*
OtherRegionsTable::find_region_table(size_t ind, HeapRegion* hr) const {
  assert(0 <= ind && ind < _max_fine_entries, "Preconditions.");
  PosParPRT* prt = _fine_grain_regions[ind];
  while (prt != NULL && prt->hr() != hr) {
    prt = prt->next();
  }
  // Loop postcondition is the method postcondition.
  return prt;
}


#define DRT_CENSUS 0

#if DRT_CENSUS
static const int HistoSize = 6;
static int global_histo[HistoSize] = { 0, 0, 0, 0, 0, 0 };
static int coarsenings = 0;
static int occ_sum = 0;
#endif

jint OtherRegionsTable::_n_coarsenings = 0;

PosParPRT* OtherRegionsTable::delete_region_table() {
#if DRT_CENSUS
  int histo[HistoSize] = { 0, 0, 0, 0, 0, 0 };
  const int histo_limits[] = { 1, 4, 16, 64, 256, 2048 };
#endif

  assert(_m.owned_by_self(), "Precondition");
  assert(_n_fine_entries == _max_fine_entries, "Precondition");
  PosParPRT* max = NULL;
  jint max_occ = 0;
  PosParPRT** max_prev;
  size_t max_ind;

#if SAMPLE_FOR_EVICTION
  size_t i = _fine_eviction_start;
  for (size_t k = 0; k < _fine_eviction_sample_size; k++) {
    size_t ii = i;
    // Make sure we get a non-NULL sample.
    while (_fine_grain_regions[ii] == NULL) {
      ii++;
      if (ii == _max_fine_entries) ii = 0;
      guarantee(ii != i, "We must find one.");
    }
    PosParPRT** prev = &_fine_grain_regions[ii];
    PosParPRT* cur = *prev;
    while (cur != NULL) {
      jint cur_occ = cur->occupied();
      if (max == NULL || cur_occ > max_occ) {
        max = cur;
        max_prev = prev;
        max_ind = i;
        max_occ = cur_occ;
      }
      prev = cur->next_addr();
      cur = cur->next();
    }
    i = i + _fine_eviction_stride;
    if (i >= _n_fine_entries) i = i - _n_fine_entries;
  }
  _fine_eviction_start++;
  if (_fine_eviction_start >= _n_fine_entries)
    _fine_eviction_start -= _n_fine_entries;
#else
  for (int i = 0; i < _max_fine_entries; i++) {
    PosParPRT** prev = &_fine_grain_regions[i];
    PosParPRT* cur = *prev;
    while (cur != NULL) {
      jint cur_occ = cur->occupied();
#if DRT_CENSUS
      for (int k = 0; k < HistoSize; k++) {
        if (cur_occ <= histo_limits[k]) {
          histo[k]++; global_histo[k]++; break;
        }
      }
#endif
      if (max == NULL || cur_occ > max_occ) {
        max = cur;
        max_prev = prev;
        max_ind = i;
        max_occ = cur_occ;
      }
      prev = cur->next_addr();
      cur = cur->next();
    }
  }
#endif
  // XXX
  guarantee(max != NULL, "Since _n_fine_entries > 0");
#if DRT_CENSUS
  gclog_or_tty->print_cr("In a coarsening: histo of occs:");
  for (int k = 0; k < HistoSize; k++) {
    gclog_or_tty->print_cr("  <= %4d: %5d.", histo_limits[k], histo[k]);
  }
  coarsenings++;
  occ_sum += max_occ;
  if ((coarsenings % 100) == 0) {
    gclog_or_tty->print_cr("\ncoarsenings = %d; global summary:", coarsenings);
    for (int k = 0; k < HistoSize; k++) {
      gclog_or_tty->print_cr("  <= %4d: %5d.", histo_limits[k], global_histo[k]);
    }
    gclog_or_tty->print_cr("Avg occ of deleted region = %6.2f.",
                  (float)occ_sum/(float)coarsenings);
  }
#endif

  // Set the corresponding coarse bit.
  int max_hrs_index = max->hr()->hrs_index();
  if (!_coarse_map.at(max_hrs_index)) {
    _coarse_map.at_put(max_hrs_index, true);
    _n_coarse_entries++;
#if 0
    gclog_or_tty->print("Coarsened entry in region [" PTR_FORMAT "...] "
               "for region [" PTR_FORMAT "...] (%d coarse entries).\n",
               hr()->bottom(),
               max->hr()->bottom(),
               _n_coarse_entries);
#endif
  }

  // Unsplice.
  *max_prev = max->next();
  Atomic::inc(&_n_coarsenings);
  _n_fine_entries--;
  return max;
}


// At present, this must be called stop-world single-threaded.
void OtherRegionsTable::scrub(CardTableModRefBS* ctbs,
                              BitMap* region_bm, BitMap* card_bm) {
  // First eliminated garbage regions from the coarse map.
  if (G1RSScrubVerbose)
    gclog_or_tty->print_cr("Scrubbing region %d:", hr()->hrs_index());

  assert(_coarse_map.size() == region_bm->size(), "Precondition");
  if (G1RSScrubVerbose)
    gclog_or_tty->print("   Coarse map: before = %d...", _n_coarse_entries);
  _coarse_map.set_intersection(*region_bm);
  _n_coarse_entries = _coarse_map.count_one_bits();
  if (G1RSScrubVerbose)
    gclog_or_tty->print_cr("   after = %d.", _n_coarse_entries);

  // Now do the fine-grained maps.
  for (size_t i = 0; i < _max_fine_entries; i++) {
    PosParPRT* cur = _fine_grain_regions[i];
    PosParPRT** prev = &_fine_grain_regions[i];
    while (cur != NULL) {
      PosParPRT* nxt = cur->next();
      // If the entire region is dead, eliminate.
      if (G1RSScrubVerbose)
        gclog_or_tty->print_cr("     For other region %d:", cur->hr()->hrs_index());
      if (!region_bm->at(cur->hr()->hrs_index())) {
        *prev = nxt;
        cur->set_next(NULL);
        _n_fine_entries--;
        if (G1RSScrubVerbose)
          gclog_or_tty->print_cr("          deleted via region map.");
        PosParPRT::free(cur);
      } else {
        // Do fine-grain elimination.
        if (G1RSScrubVerbose)
          gclog_or_tty->print("          occ: before = %4d.", cur->occupied());
        cur->scrub(ctbs, card_bm);
        if (G1RSScrubVerbose)
          gclog_or_tty->print_cr("          after = %4d.", cur->occupied());
        // Did that empty the table completely?
        if (cur->occupied() == 0) {
          *prev = nxt;
          cur->set_next(NULL);
          _n_fine_entries--;
          PosParPRT::free(cur);
        } else {
          prev = cur->next_addr();
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
  for (size_t i = 0; i < _max_fine_entries; i++) {
    PosParPRT* cur = _fine_grain_regions[i];
    while (cur != NULL) {
      sum += cur->occupied();
      cur = cur->next();
    }
  }
  return sum;
}

size_t OtherRegionsTable::occ_coarse() const {
  return (_n_coarse_entries * PosParPRT::CardsPerRegion);
}

size_t OtherRegionsTable::occ_sparse() const {
  return _sparse_table.occupied();
}

size_t OtherRegionsTable::mem_size() const {
  // Cast away const in this case.
  MutexLockerEx x((Mutex*)&_m, Mutex::_no_safepoint_check_flag);
  size_t sum = 0;
  for (size_t i = 0; i < _max_fine_entries; i++) {
    PosParPRT* cur = _fine_grain_regions[i];
    while (cur != NULL) {
      sum += cur->mem_size();
      cur = cur->next();
    }
  }
  sum += (sizeof(PosParPRT*) * _max_fine_entries);
  sum += (_coarse_map.size_in_words() * HeapWordSize);
  sum += (_sparse_table.mem_size());
  sum += sizeof(*this) - sizeof(_sparse_table); // Avoid double counting above.
  return sum;
}

size_t OtherRegionsTable::static_mem_size() {
  return _from_card_cache_mem_size;
}

size_t OtherRegionsTable::fl_mem_size() {
  return PerRegionTable::fl_mem_size() + PosParPRT::fl_mem_size();
}

void OtherRegionsTable::clear_fcc() {
  for (int i = 0; i < HeapRegionRemSet::num_par_rem_sets(); i++) {
    _from_card_cache[i][hr()->hrs_index()] = -1;
  }
}

void OtherRegionsTable::clear() {
  MutexLockerEx x(&_m, Mutex::_no_safepoint_check_flag);
  for (size_t i = 0; i < _max_fine_entries; i++) {
    PosParPRT* cur = _fine_grain_regions[i];
    while (cur != NULL) {
      PosParPRT* nxt = cur->next();
      PosParPRT::free(cur);
      cur = nxt;
    }
    _fine_grain_regions[i] = NULL;
  }
  _sparse_table.clear();
  _coarse_map.clear();
  _n_fine_entries = 0;
  _n_coarse_entries = 0;

  clear_fcc();
}

void OtherRegionsTable::clear_incoming_entry(HeapRegion* from_hr) {
  MutexLockerEx x(&_m, Mutex::_no_safepoint_check_flag);
  size_t hrs_ind = (size_t)from_hr->hrs_index();
  size_t ind = hrs_ind & _mod_max_fine_entries_mask;
  if (del_single_region_table(ind, from_hr)) {
    assert(!_coarse_map.at(hrs_ind), "Inv");
  } else {
    _coarse_map.par_at_put(hrs_ind, 0);
  }
  // Check to see if any of the fcc entries come from here.
  int hr_ind = hr()->hrs_index();
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
  PosParPRT** prev_addr = &_fine_grain_regions[ind];
  PosParPRT* prt = *prev_addr;
  while (prt != NULL && prt->hr() != hr) {
    prev_addr = prt->next_addr();
    prt = prt->next();
  }
  if (prt != NULL) {
    assert(prt->hr() == hr, "Loop postcondition.");
    *prev_addr = prt->next();
    PosParPRT::free(prt);
    _n_fine_entries--;
    return true;
  } else {
    return false;
  }
}

bool OtherRegionsTable::contains_reference(oop* from) const {
  // Cast away const in this case.
  MutexLockerEx x((Mutex*)&_m, Mutex::_no_safepoint_check_flag);
  return contains_reference_locked(from);
}

bool OtherRegionsTable::contains_reference_locked(oop* from) const {
  HeapRegion* hr = _g1h->heap_region_containing_raw(from);
  if (hr == NULL) return false;
  size_t hr_ind = hr->hrs_index();
  // Is this region in the coarse map?
  if (_coarse_map.at(hr_ind)) return true;

  PosParPRT* prt = find_region_table(hr_ind & _mod_max_fine_entries_mask,
                                     hr);
  if (prt != NULL) {
    return prt->contains_reference(from);

  } else {
    uintptr_t from_card =
      (uintptr_t(from) >> CardTableModRefBS::card_shift);
    uintptr_t hr_bot_card_index =
      uintptr_t(hr->bottom()) >> CardTableModRefBS::card_shift;
    assert(from_card >= hr_bot_card_index, "Inv");
    int card_index = from_card - hr_bot_card_index;
    return _sparse_table.contains_card((short)hr_ind, card_index);
  }


}


bool HeapRegionRemSet::_par_traversal = false;

void HeapRegionRemSet::set_par_traversal(bool b) {
  assert(_par_traversal != b, "Proper alternation...");
  _par_traversal = b;
}

int HeapRegionRemSet::num_par_rem_sets() {
  // We always have at least two, so that a mutator thread can claim an
  // id and add to a rem set.
  return (int) MAX2(ParallelGCThreads, (size_t)2);
}

HeapRegionRemSet::HeapRegionRemSet(G1BlockOffsetSharedArray* bosa,
                                   HeapRegion* hr)
    : _bosa(bosa), _other_regions(hr),
      _outgoing_region_map(G1CollectedHeap::heap()->max_regions(),
                           false /* in-resource-area */),
      _iter_state(Unclaimed)
{}


void HeapRegionRemSet::init_for_par_iteration() {
  _iter_state = Unclaimed;
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


void HeapRegionRemSet::init_iterator(HeapRegionRemSetIterator* iter) const {
  iter->initialize(this);
}

#ifndef PRODUCT
void HeapRegionRemSet::print() const {
  HeapRegionRemSetIterator iter;
  init_iterator(&iter);
  size_t card_index;
  while (iter.has_next(card_index)) {
    HeapWord* card_start =
      G1CollectedHeap::heap()->bot_shared()->address_for_index(card_index);
    gclog_or_tty->print_cr("  Card " PTR_FORMAT ".", card_start);
  }
  // XXX
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

void HeapRegionRemSet::par_cleanup() {
  PosParPRT::par_contract_all();
}

void HeapRegionRemSet::add_outgoing_reference(HeapRegion* to_hr) {
  _outgoing_region_map.par_at_put(to_hr->hrs_index(), 1);
}

void HeapRegionRemSet::clear() {
  clear_outgoing_entries();
  _outgoing_region_map.clear();
  _other_regions.clear();
  assert(occupied() == 0, "Should be clear.");
}

void HeapRegionRemSet::clear_outgoing_entries() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  size_t i = _outgoing_region_map.get_next_one_offset(0);
  while (i < _outgoing_region_map.size()) {
    HeapRegion* to_region = g1h->region_at(i);
    if (!to_region->in_collection_set()) {
      to_region->rem_set()->clear_incoming_entry(hr());
    }
    i = _outgoing_region_map.get_next_one_offset(i+1);
  }
}


void HeapRegionRemSet::scrub(CardTableModRefBS* ctbs,
                             BitMap* region_bm, BitMap* card_bm) {
  _other_regions.scrub(ctbs, region_bm, card_bm);
}

//-------------------- Iteration --------------------

HeapRegionRemSetIterator::
HeapRegionRemSetIterator() :
  _hrrs(NULL),
  _g1h(G1CollectedHeap::heap()),
  _bosa(NULL),
  _sparse_iter(size_t(G1CollectedHeap::heap()->reserved_region().start())
               >> CardTableModRefBS::card_shift)
{}

void HeapRegionRemSetIterator::initialize(const HeapRegionRemSet* hrrs) {
  _hrrs = hrrs;
  _coarse_map = &_hrrs->_other_regions._coarse_map;
  _fine_grain_regions = _hrrs->_other_regions._fine_grain_regions;
  _bosa = _hrrs->bosa();

  _is = Sparse;
  // Set these values so that we increment to the first region.
  _coarse_cur_region_index = -1;
  _coarse_cur_region_cur_card = (PosParPRT::CardsPerRegion-1);;

  _cur_region_cur_card = 0;

  _fine_array_index = -1;
  _fine_cur_prt = NULL;

  _n_yielded_coarse = 0;
  _n_yielded_fine = 0;
  _n_yielded_sparse = 0;

  _sparse_iter.init(&hrrs->_other_regions._sparse_table);
}

bool HeapRegionRemSetIterator::coarse_has_next(size_t& card_index) {
  if (_hrrs->_other_regions._n_coarse_entries == 0) return false;
  // Go to the next card.
  _coarse_cur_region_cur_card++;
  // Was the last the last card in the current region?
  if (_coarse_cur_region_cur_card == PosParPRT::CardsPerRegion) {
    // Yes: find the next region.  This may leave _coarse_cur_region_index
    // Set to the last index, in which case there are no more coarse
    // regions.
    _coarse_cur_region_index =
      (int) _coarse_map->get_next_one_offset(_coarse_cur_region_index + 1);
    if ((size_t)_coarse_cur_region_index < _coarse_map->size()) {
      _coarse_cur_region_cur_card = 0;
      HeapWord* r_bot =
        _g1h->region_at(_coarse_cur_region_index)->bottom();
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
    if (_cur_region_cur_card == PosParPRT::CardsPerRegion) {
      _cur_region_cur_card = 0;
      _fine_cur_prt = _fine_cur_prt->next();
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
    _cur_region_cur_card < PosParPRT::CardsPerRegion;
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



oop**        HeapRegionRemSet::_recorded_oops = NULL;
HeapWord**   HeapRegionRemSet::_recorded_cards = NULL;
HeapRegion** HeapRegionRemSet::_recorded_regions = NULL;
int          HeapRegionRemSet::_n_recorded = 0;

HeapRegionRemSet::Event* HeapRegionRemSet::_recorded_events = NULL;
int*         HeapRegionRemSet::_recorded_event_index = NULL;
int          HeapRegionRemSet::_n_recorded_events = 0;

void HeapRegionRemSet::record(HeapRegion* hr, oop* f) {
  if (_recorded_oops == NULL) {
    assert(_n_recorded == 0
           && _recorded_cards == NULL
           && _recorded_regions == NULL,
           "Inv");
    _recorded_oops = NEW_C_HEAP_ARRAY(oop*, MaxRecorded);
    _recorded_cards = NEW_C_HEAP_ARRAY(HeapWord*, MaxRecorded);
    _recorded_regions = NEW_C_HEAP_ARRAY(HeapRegion*, MaxRecorded);
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
    _recorded_events = NEW_C_HEAP_ARRAY(Event, MaxRecordedEvents);
    _recorded_event_index = NEW_C_HEAP_ARRAY(int, MaxRecordedEvents);
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

#ifndef PRODUCT
void HeapRegionRemSet::test() {
  os::sleep(Thread::current(), (jlong)5000, false);
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // Run with "-XX:G1LogRSRegionEntries=2", so that 1 and 5 end up in same
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
  hrrs->add_reference((oop*)hr1_start);
  hrrs->add_reference((oop*)hr1_mid);
  hrrs->add_reference((oop*)hr1_last);

  hrrs->add_reference((oop*)hr2_start);
  hrrs->add_reference((oop*)hr2_mid);
  hrrs->add_reference((oop*)hr2_last);

  hrrs->add_reference((oop*)hr3_start);
  hrrs->add_reference((oop*)hr3_mid);
  hrrs->add_reference((oop*)hr3_last);

  // Now cause a coarsening.
  hrrs->add_reference((oop*)hr4->bottom());
  hrrs->add_reference((oop*)hr5->bottom());

  // Now, does iteration yield these three?
  HeapRegionRemSetIterator iter;
  hrrs->init_iterator(&iter);
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

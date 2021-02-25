/*
 * Copyright (c) 2001, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/heapRegionManager.inline.hpp"
#include "gc/g1/heapRegionRemSet.inline.hpp"
#include "gc/g1/sparsePRT.inline.hpp"
#include "memory/allocation.hpp"
#include "memory/padded.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/powerOfTwo.hpp"

const char* HeapRegionRemSet::_state_strings[] =  {"Untracked", "Updating", "Complete"};
const char* HeapRegionRemSet::_short_state_strings[] =  {"UNTRA", "UPDAT", "CMPLT"};

PerRegionTable* PerRegionTable::alloc(HeapRegion* hr) {
  PerRegionTable* fl = _free_list;
  while (fl != NULL) {
    PerRegionTable* nxt = fl->next();
    PerRegionTable* res = Atomic::cmpxchg(&_free_list, fl, nxt);
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

PerRegionTable* volatile PerRegionTable::_free_list = NULL;

size_t OtherRegionsTable::_max_fine_entries = 0;
size_t OtherRegionsTable::_mod_max_fine_entries_mask = 0;
size_t OtherRegionsTable::_fine_eviction_stride = 0;
size_t OtherRegionsTable::_fine_eviction_sample_size = 0;

OtherRegionsTable::OtherRegionsTable(Mutex* m) :
  _g1h(G1CollectedHeap::heap()),
  _m(m),
  _num_occupied(0),
  _coarse_map(mtGC),
  _has_coarse_entries(false),
  _fine_grain_regions(NULL),
  _n_fine_entries(0),
  _first_all_fine_prts(NULL),
  _last_all_fine_prts(NULL),
  _fine_eviction_start(0),
  _sparse_table()
{
  typedef PerRegionTable* PerRegionTablePtr;

  if (_max_fine_entries == 0) {
    assert(_mod_max_fine_entries_mask == 0, "Both or none.");
    size_t max_entries_log = (size_t)log2i(G1RSetRegionEntries);
    _max_fine_entries = (size_t)1 << max_entries_log;
    _mod_max_fine_entries_mask = _max_fine_entries - 1;

    assert(_fine_eviction_sample_size == 0
           && _fine_eviction_stride == 0, "All init at same time.");
    _fine_eviction_sample_size = MAX2((size_t)4, max_entries_log);
    _fine_eviction_stride = _max_fine_entries / _fine_eviction_sample_size;
  }

  _fine_grain_regions = NEW_C_HEAP_ARRAY(PerRegionTablePtr, _max_fine_entries, mtGC);
  for (size_t i = 0; i < _max_fine_entries; i++) {
    _fine_grain_regions[i] = NULL;
  }
}

void OtherRegionsTable::link_to_all(PerRegionTable* prt) {
  // We always append to the beginning of the list for convenience;
  // the order of entries in this list does not matter.
  if (_first_all_fine_prts != NULL) {
    prt->set_next(_first_all_fine_prts);
  } else {
    // this is the first element we insert. Adjust the "last" pointer
    _last_all_fine_prts = prt;
    assert(prt->next() == NULL, "just checking");
  }
  _first_all_fine_prts = prt;

  assert(_first_all_fine_prts == prt, "just checking");
  assert((_first_all_fine_prts == NULL && _last_all_fine_prts == NULL) ||
         (_first_all_fine_prts != NULL && _last_all_fine_prts != NULL),
         "just checking");
  assert(_last_all_fine_prts == NULL || _last_all_fine_prts->next() == NULL,
         "just checking");
}

CardIdx_t OtherRegionsTable::card_within_region(OopOrNarrowOopStar within_region, HeapRegion* hr) {
  assert(hr->is_in_reserved(within_region),
         "HeapWord " PTR_FORMAT " is outside of region %u [" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(within_region), hr->hrm_index(), p2i(hr->bottom()), p2i(hr->end()));
  CardIdx_t result = (CardIdx_t)(pointer_delta((HeapWord*)within_region, hr->bottom()) >> (CardTable::card_shift - LogHeapWordSize));
  return result;
}

void OtherRegionsTable::add_reference(OopOrNarrowOopStar from, uint tid) {
  // Note that this may be a continued H region.
  HeapRegion* from_hr = _g1h->heap_region_containing(from);
  RegionIdx_t from_hrm_ind = (RegionIdx_t) from_hr->hrm_index();

  // If the region is already coarsened, return.
  if (is_region_coarsened(from_hrm_ind)) {
    assert(contains_reference(from), "We just found " PTR_FORMAT " in the Coarse table", p2i(from));
    return;
  }

  size_t num_added_by_coarsening = 0;
  // Otherwise find a per-region table to add it to.
  size_t ind = from_hrm_ind & _mod_max_fine_entries_mask;
  PerRegionTable* prt = find_region_table(ind, from_hr);
  if (prt == NULL) {
    MutexLocker x(_m, Mutex::_no_safepoint_check_flag);

    // Rechecking if the region is coarsened, while holding the lock.
    if (is_region_coarsened(from_hrm_ind)) {
      assert(contains_reference_locked(from), "We just found " PTR_FORMAT " in the Coarse table", p2i(from));
      return;
    }

    // Confirm that it's really not there...
    prt = find_region_table(ind, from_hr);
    if (prt == NULL) {

      CardIdx_t card_index = card_within_region(from, from_hr);

      SparsePRT::AddCardResult result = _sparse_table.add_card(from_hrm_ind, card_index);
      if (result != SparsePRT::overflow) {
        if (result == SparsePRT::added) {
          Atomic::inc(&_num_occupied, memory_order_relaxed);
        }
        assert(contains_reference_locked(from), "We just added " PTR_FORMAT " to the Sparse table", p2i(from));
        return;
      }

      // Sparse PRT returned overflow (sparse table is full)

      if (_n_fine_entries == _max_fine_entries) {
        prt = delete_region_table(num_added_by_coarsening);
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
      Atomic::release_store(&_fine_grain_regions[ind], prt);
      _n_fine_entries++;

      // Transfer from sparse to fine-grain. The cards from the sparse table
      // were already added to the total in _num_occupied.
      SparsePRTEntry *sprt_entry = _sparse_table.get_entry(from_hrm_ind);
      assert(sprt_entry != NULL, "There should have been an entry");
      for (int i = 0; i < sprt_entry->num_valid_cards(); i++) {
        CardIdx_t c = sprt_entry->card(i);
        prt->add_card(c);
      }
      // Now we can delete the sparse entry.
      bool res = _sparse_table.delete_entry(from_hrm_ind);
      assert(res, "It should have been there.");
    }
    assert(prt != NULL && prt->hr() == from_hr, "consequence");
  }
  // Note that we can't assert "prt->hr() == from_hr", because of the
  // possibility of concurrent reuse.  But see head comment of
  // OtherRegionsTable for why this is OK.
  assert(prt != NULL, "Inv");

  if (prt->add_reference(from)) {
    num_added_by_coarsening++;
  }
  Atomic::add(&_num_occupied, num_added_by_coarsening, memory_order_relaxed);
  assert(contains_reference(from), "We just added " PTR_FORMAT " to the PRT (%d)", p2i(from), prt->contains_reference(from));
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

PerRegionTable* OtherRegionsTable::delete_region_table(size_t& added_by_deleted) {
  assert(_m->owned_by_self(), "Precondition");
  assert(_n_fine_entries == _max_fine_entries, "Precondition");
  PerRegionTable* max = NULL;
  jint max_occ = 0;
  PerRegionTable** max_prev = NULL;

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

  // Ensure the corresponding coarse bit is set.
  size_t max_hrm_index = (size_t) max->hr()->hrm_index();
  if (Atomic::load(&_has_coarse_entries)) {
    _coarse_map.at_put(max_hrm_index, true);
  } else {
    // This will lazily initialize an uninitialized bitmap
    _coarse_map.reinitialize(G1CollectedHeap::heap()->max_reserved_regions());
    assert(!_coarse_map.at(max_hrm_index), "No coarse entries");
    _coarse_map.at_put(max_hrm_index, true);
    // Release store guarantees that the bitmap has initialized before any
    // concurrent reader will ever see _has_coarse_entries is true
    // (when read with load_acquire)
    Atomic::release_store(&_has_coarse_entries, true);
  }

  added_by_deleted = HeapRegion::CardsPerRegion - max_occ;
  // Unsplice.
  *max_prev = max->collision_list_next();
  Atomic::inc(&_n_coarsenings);
  _n_fine_entries--;
  return max;
}

bool OtherRegionsTable::occupancy_less_or_equal_than(size_t limit) const {
  return occupied() <= limit;
}

bool OtherRegionsTable::is_empty() const {
  return occupied() == 0;
}

size_t OtherRegionsTable::occupied() const {
  return _num_occupied;
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
  return G1FromCardCache::static_mem_size();
}

size_t OtherRegionsTable::fl_mem_size() {
  return PerRegionTable::fl_mem_size();
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
  if (Atomic::load(&_has_coarse_entries)) {
    _coarse_map.clear();
  }
  _n_fine_entries = 0;
  Atomic::store(&_has_coarse_entries, false);

  _num_occupied = 0;
}

bool OtherRegionsTable::contains_reference(OopOrNarrowOopStar from) const {
  // Cast away const in this case.
  MutexLocker x((Mutex*)_m, Mutex::_no_safepoint_check_flag);
  return contains_reference_locked(from);
}

bool OtherRegionsTable::contains_reference_locked(OopOrNarrowOopStar from) const {
  HeapRegion* hr = _g1h->heap_region_containing(from);
  RegionIdx_t hr_ind = (RegionIdx_t) hr->hrm_index();
  // Is this region in the coarse map?
  if (is_region_coarsened(hr_ind)) return true;

  PerRegionTable* prt = find_region_table(hr_ind & _mod_max_fine_entries_mask,
                                          hr);
  if (prt != NULL) {
    return prt->contains_reference(from);
  } else {
    CardIdx_t card_index = card_within_region(from, hr);
    return _sparse_table.contains_card(hr_ind, card_index);
  }
}

// A load_acquire on _has_coarse_entries - coupled with the release_store in
// delete_region_table - guarantees we don't access _coarse_map before
// it's been properly initialized.
bool OtherRegionsTable::is_region_coarsened(RegionIdx_t from_hrm_ind) const {
  return Atomic::load_acquire(&_has_coarse_entries) && _coarse_map.at(from_hrm_ind);
}

HeapRegionRemSet::HeapRegionRemSet(G1BlockOffsetTable* bot,
                                   HeapRegion* hr)
  : _bot(bot),
    _code_roots(),
    _m(Mutex::leaf, FormatBuffer<128>("HeapRegionRemSet lock #%u", hr->hrm_index()), true, Mutex::_safepoint_check_never),
    _other_regions(&_m),
    _hr(hr),
    _state(Untracked)
{
}

void HeapRegionRemSet::clear_fcc() {
  G1FromCardCache::clear(_hr->hrm_index());
}

void HeapRegionRemSet::setup_remset_size() {
  const int LOG_M = 20;
  guarantee(HeapRegion::LogOfHRGrainBytes >= LOG_M, "Code assumes the region size >= 1M, but is " SIZE_FORMAT "B", HeapRegion::GrainBytes);

  int region_size_log_mb = HeapRegion::LogOfHRGrainBytes - LOG_M;
  if (FLAG_IS_DEFAULT(G1RSetSparseRegionEntries)) {
    G1RSetSparseRegionEntries = G1RSetSparseRegionEntriesBase * ((size_t)1 << (region_size_log_mb + 1));
  }
  if (FLAG_IS_DEFAULT(G1RSetRegionEntries)) {
    G1RSetRegionEntries = G1RSetRegionEntriesBase * (region_size_log_mb + 1);
  }
  guarantee(G1RSetSparseRegionEntries > 0 && G1RSetRegionEntries > 0 , "Sanity");
}

void HeapRegionRemSet::clear(bool only_cardset) {
  MutexLocker x(&_m, Mutex::_no_safepoint_check_flag);
  clear_locked(only_cardset);
}

void HeapRegionRemSet::clear_locked(bool only_cardset) {
  if (!only_cardset) {
    _code_roots.clear();
  }
  clear_fcc();
  _other_regions.clear();
  set_state_empty();
  assert(occupied() == 0, "Should be clear.");
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
  assert((!CodeCache_lock->owned_by_self() || SafepointSynchronize::is_at_safepoint()),
          "should call add_strong_code_root_locked instead. CodeCache_lock->owned_by_self(): %s, is_at_safepoint(): %s",
          BOOL_TO_STR(CodeCache_lock->owned_by_self()), BOOL_TO_STR(SafepointSynchronize::is_at_safepoint()));
  // Optimistic unlocked contains-check
  if (!_code_roots.contains(nm)) {
    MutexLocker ml(&_m, Mutex::_no_safepoint_check_flag);
    add_strong_code_root_locked(nm);
  }
}

void HeapRegionRemSet::add_strong_code_root_locked(nmethod* nm) {
  assert(nm != NULL, "sanity");
  assert((CodeCache_lock->owned_by_self() ||
         (SafepointSynchronize::is_at_safepoint() &&
          (_m.owned_by_self() || Thread::current()->is_VM_thread()))),
          "not safely locked. CodeCache_lock->owned_by_self(): %s, is_at_safepoint(): %s, _m.owned_by_self(): %s, Thread::current()->is_VM_thread(): %s",
          BOOL_TO_STR(CodeCache_lock->owned_by_self()), BOOL_TO_STR(SafepointSynchronize::is_at_safepoint()),
          BOOL_TO_STR(_m.owned_by_self()), BOOL_TO_STR(Thread::current()->is_VM_thread()));
  _code_roots.add(nm);
}

void HeapRegionRemSet::remove_strong_code_root(nmethod* nm) {
  assert(nm != NULL, "sanity");
  assert_locked_or_safepoint(CodeCache_lock);

  MutexLocker ml(CodeCache_lock->owned_by_self() ? NULL : &_m, Mutex::_no_safepoint_check_flag);
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

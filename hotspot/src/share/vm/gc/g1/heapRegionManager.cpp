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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionManager.inline.hpp"
#include "gc/g1/heapRegionSet.inline.hpp"
#include "memory/allocation.hpp"

void HeapRegionManager::initialize(G1RegionToSpaceMapper* heap_storage,
                               G1RegionToSpaceMapper* prev_bitmap,
                               G1RegionToSpaceMapper* next_bitmap,
                               G1RegionToSpaceMapper* bot,
                               G1RegionToSpaceMapper* cardtable,
                               G1RegionToSpaceMapper* card_counts) {
  _allocated_heapregions_length = 0;

  _heap_mapper = heap_storage;

  _prev_bitmap_mapper = prev_bitmap;
  _next_bitmap_mapper = next_bitmap;

  _bot_mapper = bot;
  _cardtable_mapper = cardtable;

  _card_counts_mapper = card_counts;

  MemRegion reserved = heap_storage->reserved();
  _regions.initialize(reserved.start(), reserved.end(), HeapRegion::GrainBytes);

  _available_map.resize(_regions.length(), false);
  _available_map.clear();
}

bool HeapRegionManager::is_available(uint region) const {
  return _available_map.at(region);
}

#ifdef ASSERT
bool HeapRegionManager::is_free(HeapRegion* hr) const {
  return _free_list.contains(hr);
}
#endif

HeapRegion* HeapRegionManager::new_heap_region(uint hrm_index) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  HeapWord* bottom = g1h->bottom_addr_for_region(hrm_index);
  MemRegion mr(bottom, bottom + HeapRegion::GrainWords);
  assert(reserved().contains(mr), "invariant");
  return g1h->new_heap_region(hrm_index, mr);
}

void HeapRegionManager::commit_regions(uint index, size_t num_regions) {
  guarantee(num_regions > 0, "Must commit more than zero regions");
  guarantee(_num_committed + num_regions <= max_length(), "Cannot commit more than the maximum amount of regions");

  _num_committed += (uint)num_regions;

  _heap_mapper->commit_regions(index, num_regions);

  // Also commit auxiliary data
  _prev_bitmap_mapper->commit_regions(index, num_regions);
  _next_bitmap_mapper->commit_regions(index, num_regions);

  _bot_mapper->commit_regions(index, num_regions);
  _cardtable_mapper->commit_regions(index, num_regions);

  _card_counts_mapper->commit_regions(index, num_regions);
}

void HeapRegionManager::uncommit_regions(uint start, size_t num_regions) {
  guarantee(num_regions >= 1, err_msg("Need to specify at least one region to uncommit, tried to uncommit zero regions at %u", start));
  guarantee(_num_committed >= num_regions, "pre-condition");

  // Print before uncommitting.
  if (G1CollectedHeap::heap()->hr_printer()->is_active()) {
    for (uint i = start; i < start + num_regions; i++) {
      HeapRegion* hr = at(i);
      G1CollectedHeap::heap()->hr_printer()->uncommit(hr->bottom(), hr->end());
    }
  }

  _num_committed -= (uint)num_regions;

  _available_map.par_clear_range(start, start + num_regions, BitMap::unknown_range);
  _heap_mapper->uncommit_regions(start, num_regions);

  // Also uncommit auxiliary data
  _prev_bitmap_mapper->uncommit_regions(start, num_regions);
  _next_bitmap_mapper->uncommit_regions(start, num_regions);

  _bot_mapper->uncommit_regions(start, num_regions);
  _cardtable_mapper->uncommit_regions(start, num_regions);

  _card_counts_mapper->uncommit_regions(start, num_regions);
}

void HeapRegionManager::make_regions_available(uint start, uint num_regions) {
  guarantee(num_regions > 0, "No point in calling this for zero regions");
  commit_regions(start, num_regions);
  for (uint i = start; i < start + num_regions; i++) {
    if (_regions.get_by_index(i) == NULL) {
      HeapRegion* new_hr = new_heap_region(i);
      _regions.set_by_index(i, new_hr);
      _allocated_heapregions_length = MAX2(_allocated_heapregions_length, i + 1);
    }
  }

  _available_map.par_set_range(start, start + num_regions, BitMap::unknown_range);

  for (uint i = start; i < start + num_regions; i++) {
    assert(is_available(i), err_msg("Just made region %u available but is apparently not.", i));
    HeapRegion* hr = at(i);
    if (G1CollectedHeap::heap()->hr_printer()->is_active()) {
      G1CollectedHeap::heap()->hr_printer()->commit(hr->bottom(), hr->end());
    }
    HeapWord* bottom = G1CollectedHeap::heap()->bottom_addr_for_region(i);
    MemRegion mr(bottom, bottom + HeapRegion::GrainWords);

    hr->initialize(mr);
    insert_into_free_list(at(i));
  }
}

MemoryUsage HeapRegionManager::get_auxiliary_data_memory_usage() const {
  size_t used_sz =
    _prev_bitmap_mapper->committed_size() +
    _next_bitmap_mapper->committed_size() +
    _bot_mapper->committed_size() +
    _cardtable_mapper->committed_size() +
    _card_counts_mapper->committed_size();

  size_t committed_sz =
    _prev_bitmap_mapper->reserved_size() +
    _next_bitmap_mapper->reserved_size() +
    _bot_mapper->reserved_size() +
    _cardtable_mapper->reserved_size() +
    _card_counts_mapper->reserved_size();

  return MemoryUsage(0, used_sz, committed_sz, committed_sz);
}

uint HeapRegionManager::expand_by(uint num_regions) {
  return expand_at(0, num_regions);
}

uint HeapRegionManager::expand_at(uint start, uint num_regions) {
  if (num_regions == 0) {
    return 0;
  }

  uint cur = start;
  uint idx_last_found = 0;
  uint num_last_found = 0;

  uint expanded = 0;

  while (expanded < num_regions &&
         (num_last_found = find_unavailable_from_idx(cur, &idx_last_found)) > 0) {
    uint to_expand = MIN2(num_regions - expanded, num_last_found);
    make_regions_available(idx_last_found, to_expand);
    expanded += to_expand;
    cur = idx_last_found + num_last_found + 1;
  }

  verify_optional();
  return expanded;
}

uint HeapRegionManager::find_contiguous(size_t num, bool empty_only) {
  uint found = 0;
  size_t length_found = 0;
  uint cur = 0;

  while (length_found < num && cur < max_length()) {
    HeapRegion* hr = _regions.get_by_index(cur);
    if ((!empty_only && !is_available(cur)) || (is_available(cur) && hr != NULL && hr->is_empty())) {
      // This region is a potential candidate for allocation into.
      length_found++;
    } else {
      // This region is not a candidate. The next region is the next possible one.
      found = cur + 1;
      length_found = 0;
    }
    cur++;
  }

  if (length_found == num) {
    for (uint i = found; i < (found + num); i++) {
      HeapRegion* hr = _regions.get_by_index(i);
      // sanity check
      guarantee((!empty_only && !is_available(i)) || (is_available(i) && hr != NULL && hr->is_empty()),
                err_msg("Found region sequence starting at " UINT32_FORMAT ", length " SIZE_FORMAT
                        " that is not empty at " UINT32_FORMAT ". Hr is " PTR_FORMAT, found, num, i, p2i(hr)));
    }
    return found;
  } else {
    return G1_NO_HRM_INDEX;
  }
}

HeapRegion* HeapRegionManager::next_region_in_heap(const HeapRegion* r) const {
  guarantee(r != NULL, "Start region must be a valid region");
  guarantee(is_available(r->hrm_index()), err_msg("Trying to iterate starting from region %u which is not in the heap", r->hrm_index()));
  for (uint i = r->hrm_index() + 1; i < _allocated_heapregions_length; i++) {
    HeapRegion* hr = _regions.get_by_index(i);
    if (is_available(i)) {
      return hr;
    }
  }
  return NULL;
}

void HeapRegionManager::iterate(HeapRegionClosure* blk) const {
  uint len = max_length();

  for (uint i = 0; i < len; i++) {
    if (!is_available(i)) {
      continue;
    }
    guarantee(at(i) != NULL, err_msg("Tried to access region %u that has a NULL HeapRegion*", i));
    bool res = blk->doHeapRegion(at(i));
    if (res) {
      blk->incomplete();
      return;
    }
  }
}

uint HeapRegionManager::find_unavailable_from_idx(uint start_idx, uint* res_idx) const {
  guarantee(res_idx != NULL, "checking");
  guarantee(start_idx <= (max_length() + 1), "checking");

  uint num_regions = 0;

  uint cur = start_idx;
  while (cur < max_length() && is_available(cur)) {
    cur++;
  }
  if (cur == max_length()) {
    return num_regions;
  }
  *res_idx = cur;
  while (cur < max_length() && !is_available(cur)) {
    cur++;
  }
  num_regions = cur - *res_idx;
#ifdef ASSERT
  for (uint i = *res_idx; i < (*res_idx + num_regions); i++) {
    assert(!is_available(i), "just checking");
  }
  assert(cur == max_length() || num_regions == 0 || is_available(cur),
         err_msg("The region at the current position %u must be available or at the end of the heap.", cur));
#endif
  return num_regions;
}

uint HeapRegionManager::find_highest_free(bool* expanded) {
  // Loop downwards from the highest region index, looking for an
  // entry which is either free or not yet committed.  If not yet
  // committed, expand_at that index.
  uint curr = max_length() - 1;
  while (true) {
    HeapRegion *hr = _regions.get_by_index(curr);
    if (hr == NULL) {
      uint res = expand_at(curr, 1);
      if (res == 1) {
        *expanded = true;
        return curr;
      }
    } else {
      if (hr->is_free()) {
        *expanded = false;
        return curr;
      }
    }
    if (curr == 0) {
      return G1_NO_HRM_INDEX;
    }
    curr--;
  }
}

bool HeapRegionManager::allocate_containing_regions(MemRegion range, size_t* commit_count) {
  size_t commits = 0;
  uint start_index = (uint)_regions.get_index_by_address(range.start());
  uint last_index = (uint)_regions.get_index_by_address(range.last());

  // Ensure that each G1 region in the range is free, returning false if not.
  // Commit those that are not yet available, and keep count.
  for (uint curr_index = start_index; curr_index <= last_index; curr_index++) {
    if (!is_available(curr_index)) {
      commits++;
      expand_at(curr_index, 1);
    }
    HeapRegion* curr_region  = _regions.get_by_index(curr_index);
    if (!curr_region->is_free()) {
      return false;
    }
  }

  allocate_free_regions_starting_at(start_index, (last_index - start_index) + 1);
  *commit_count = commits;
  return true;
}

void HeapRegionManager::par_iterate(HeapRegionClosure* blk, uint worker_id, HeapRegionClaimer* hrclaimer, bool concurrent) const {
  const uint start_index = hrclaimer->start_region_for_worker(worker_id);

  // Every worker will actually look at all regions, skipping over regions that
  // are currently not committed.
  // This also (potentially) iterates over regions newly allocated during GC. This
  // is no problem except for some extra work.
  const uint n_regions = hrclaimer->n_regions();
  for (uint count = 0; count < n_regions; count++) {
    const uint index = (start_index + count) % n_regions;
    assert(index < n_regions, "sanity");
    // Skip over unavailable regions
    if (!is_available(index)) {
      continue;
    }
    HeapRegion* r = _regions.get_by_index(index);
    // We'll ignore "continues humongous" regions (we'll process them
    // when we come across their corresponding "start humongous"
    // region) and regions already claimed.
    // However, if the iteration is specified as concurrent, the values for
    // is_starts_humongous and is_continues_humongous can not be trusted,
    // and we should just blindly iterate over regions regardless of their
    // humongous status.
    if (hrclaimer->is_region_claimed(index) || (!concurrent && r->is_continues_humongous())) {
      continue;
    }
    // OK, try to claim it
    if (!hrclaimer->claim_region(index)) {
      continue;
    }
    // Success!
    // As mentioned above, special treatment of humongous regions can only be
    // done if we are iterating non-concurrently.
    if (!concurrent && r->is_starts_humongous()) {
      // If the region is "starts humongous" we'll iterate over its
      // "continues humongous" first; in fact we'll do them
      // first. The order is important. In one case, calling the
      // closure on the "starts humongous" region might de-allocate
      // and clear all its "continues humongous" regions and, as a
      // result, we might end up processing them twice. So, we'll do
      // them first (note: most closures will ignore them anyway) and
      // then we'll do the "starts humongous" region.
      for (uint ch_index = index + 1; ch_index < index + r->region_num(); ch_index++) {
        HeapRegion* chr = _regions.get_by_index(ch_index);

        assert(chr->is_continues_humongous(), "Must be humongous region");
        assert(chr->humongous_start_region() == r,
               err_msg("Must work on humongous continuation of the original start region "
                       PTR_FORMAT ", but is " PTR_FORMAT, p2i(r), p2i(chr)));
        assert(!hrclaimer->is_region_claimed(ch_index),
               "Must not have been claimed yet because claiming of humongous continuation first claims the start region");

        // Claim the region so no other worker tries to process the region. When a worker processes a
        // starts_humongous region it may also process the associated continues_humongous regions.
        // The continues_humongous regions can be changed to free regions. Unless this worker claims
        // all of these regions, other workers might try claim and process these newly free regions.
        bool claim_result = hrclaimer->claim_region(ch_index);
        guarantee(claim_result, "We should always be able to claim the continuesHumongous part of the humongous object");

        bool res2 = blk->doHeapRegion(chr);
        if (res2) {
          return;
        }

        // Right now, this holds (i.e., no closure that actually
        // does something with "continues humongous" regions
        // clears them). We might have to weaken it in the future,
        // but let's leave these two asserts here for extra safety.
        assert(chr->is_continues_humongous(), "should still be the case");
        assert(chr->humongous_start_region() == r, "sanity");
      }
    }

    bool res = blk->doHeapRegion(r);
    if (res) {
      return;
    }
  }
}

uint HeapRegionManager::shrink_by(uint num_regions_to_remove) {
  assert(length() > 0, "the region sequence should not be empty");
  assert(length() <= _allocated_heapregions_length, "invariant");
  assert(_allocated_heapregions_length > 0, "we should have at least one region committed");
  assert(num_regions_to_remove < length(), "We should never remove all regions");

  if (num_regions_to_remove == 0) {
    return 0;
  }

  uint removed = 0;
  uint cur = _allocated_heapregions_length - 1;
  uint idx_last_found = 0;
  uint num_last_found = 0;

  while ((removed < num_regions_to_remove) &&
      (num_last_found = find_empty_from_idx_reverse(cur, &idx_last_found)) > 0) {
    uint to_remove = MIN2(num_regions_to_remove - removed, num_last_found);

    shrink_at(idx_last_found + num_last_found - to_remove, to_remove);

    cur = idx_last_found;
    removed += to_remove;
  }

  verify_optional();

  return removed;
}

void HeapRegionManager::shrink_at(uint index, size_t num_regions) {
#ifdef ASSERT
  for (uint i = index; i < (index + num_regions); i++) {
    assert(is_available(i), err_msg("Expected available region at index %u", i));
    assert(at(i)->is_empty(), err_msg("Expected empty region at index %u", i));
    assert(at(i)->is_free(), err_msg("Expected free region at index %u", i));
  }
#endif
  uncommit_regions(index, num_regions);
}

uint HeapRegionManager::find_empty_from_idx_reverse(uint start_idx, uint* res_idx) const {
  guarantee(start_idx < _allocated_heapregions_length, "checking");
  guarantee(res_idx != NULL, "checking");

  uint num_regions_found = 0;

  jlong cur = start_idx;
  while (cur != -1 && !(is_available(cur) && at(cur)->is_empty())) {
    cur--;
  }
  if (cur == -1) {
    return num_regions_found;
  }
  jlong old_cur = cur;
  // cur indexes the first empty region
  while (cur != -1 && is_available(cur) && at(cur)->is_empty()) {
    cur--;
  }
  *res_idx = cur + 1;
  num_regions_found = old_cur - cur;

#ifdef ASSERT
  for (uint i = *res_idx; i < (*res_idx + num_regions_found); i++) {
    assert(at(i)->is_empty(), "just checking");
  }
#endif
  return num_regions_found;
}

void HeapRegionManager::verify() {
  guarantee(length() <= _allocated_heapregions_length,
            err_msg("invariant: _length: %u _allocated_length: %u",
                    length(), _allocated_heapregions_length));
  guarantee(_allocated_heapregions_length <= max_length(),
            err_msg("invariant: _allocated_length: %u _max_length: %u",
                    _allocated_heapregions_length, max_length()));

  bool prev_committed = true;
  uint num_committed = 0;
  HeapWord* prev_end = heap_bottom();
  for (uint i = 0; i < _allocated_heapregions_length; i++) {
    if (!is_available(i)) {
      prev_committed = false;
      continue;
    }
    num_committed++;
    HeapRegion* hr = _regions.get_by_index(i);
    guarantee(hr != NULL, err_msg("invariant: i: %u", i));
    guarantee(!prev_committed || hr->bottom() == prev_end,
              err_msg("invariant i: %u " HR_FORMAT " prev_end: " PTR_FORMAT,
                      i, HR_FORMAT_PARAMS(hr), p2i(prev_end)));
    guarantee(hr->hrm_index() == i,
              err_msg("invariant: i: %u hrm_index(): %u", i, hr->hrm_index()));
    // Asserts will fire if i is >= _length
    HeapWord* addr = hr->bottom();
    guarantee(addr_to_region(addr) == hr, "sanity");
    // We cannot check whether the region is part of a particular set: at the time
    // this method may be called, we have only completed allocation of the regions,
    // but not put into a region set.
    prev_committed = true;
    if (hr->is_starts_humongous()) {
      prev_end = hr->orig_end();
    } else {
      prev_end = hr->end();
    }
  }
  for (uint i = _allocated_heapregions_length; i < max_length(); i++) {
    guarantee(_regions.get_by_index(i) == NULL, err_msg("invariant i: %u", i));
  }

  guarantee(num_committed == _num_committed, err_msg("Found %u committed regions, but should be %u", num_committed, _num_committed));
  _free_list.verify();
}

#ifndef PRODUCT
void HeapRegionManager::verify_optional() {
  verify();
}
#endif // PRODUCT

HeapRegionClaimer::HeapRegionClaimer(uint n_workers) :
    _n_workers(n_workers), _n_regions(G1CollectedHeap::heap()->_hrm._allocated_heapregions_length), _claims(NULL) {
  assert(n_workers > 0, "Need at least one worker.");
  _claims = NEW_C_HEAP_ARRAY(uint, _n_regions, mtGC);
  memset(_claims, Unclaimed, sizeof(*_claims) * _n_regions);
}

HeapRegionClaimer::~HeapRegionClaimer() {
  if (_claims != NULL) {
    FREE_C_HEAP_ARRAY(uint, _claims);
  }
}

uint HeapRegionClaimer::start_region_for_worker(uint worker_id) const {
  assert(worker_id < _n_workers, "Invalid worker_id.");
  return _n_regions * worker_id / _n_workers;
}

bool HeapRegionClaimer::is_region_claimed(uint region_index) const {
  assert(region_index < _n_regions, "Invalid index.");
  return _claims[region_index] == Claimed;
}

bool HeapRegionClaimer::claim_region(uint region_index) {
  assert(region_index < _n_regions, "Invalid index.");
  uint old_val = Atomic::cmpxchg(Claimed, &_claims[region_index], Unclaimed);
  return old_val == Unclaimed;
}

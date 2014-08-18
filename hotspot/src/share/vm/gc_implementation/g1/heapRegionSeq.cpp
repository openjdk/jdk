/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "gc_implementation/g1/heapRegionSet.inline.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/concurrentG1Refine.hpp"
#include "memory/allocation.hpp"

void HeapRegionSeq::initialize(ReservedSpace reserved) {
  _reserved = reserved;
  _storage.initialize(reserved, 0);

  _num_committed = 0;

  _allocated_heapregions_length = 0;

  _regions.initialize((HeapWord*)_storage.low_boundary(), (HeapWord*)_storage.high_boundary(), HeapRegion::GrainBytes);
}

bool HeapRegionSeq::is_available(uint region) const {
  return region <  _num_committed;
}

#ifdef ASSERT
bool HeapRegionSeq::is_free(HeapRegion* hr) const {
  return _free_list.contains(hr);
}
#endif

HeapRegion* HeapRegionSeq::new_heap_region(uint hrs_index) {
  HeapWord* bottom = G1CollectedHeap::heap()->bottom_addr_for_region(hrs_index);
  MemRegion mr(bottom, bottom + HeapRegion::GrainWords);
  assert(reserved().contains(mr), "invariant");
  return new HeapRegion(hrs_index, G1CollectedHeap::heap()->bot_shared(), mr);
}

void HeapRegionSeq::update_committed_space(HeapWord* old_end,
                                           HeapWord* new_end) {
  assert(old_end != new_end, "don't call this otherwise");
  // We may not have officially committed the area. So construct and use a separate one.
  MemRegion new_committed(heap_bottom(), new_end);
  // Tell the card table about the update.
  Universe::heap()->barrier_set()->resize_covered_region(new_committed);
  // Tell the BOT about the update.
  G1CollectedHeap::heap()->bot_shared()->resize(new_committed.word_size());
  // Tell the hot card cache about the update
  G1CollectedHeap::heap()->concurrent_g1_refine()->hot_card_cache()->resize_card_counts(new_committed.byte_size());
}

void HeapRegionSeq::commit_regions(uint index, size_t num_regions) {
  guarantee(num_regions > 0, "Must commit more than zero regions");
  guarantee(_num_committed + num_regions <= max_length(), "Cannot commit more than the maximum amount of regions");

  _storage.expand_by(num_regions * HeapRegion::GrainBytes);
  update_committed_space(heap_top(), heap_top() + num_regions * HeapRegion::GrainWords);
}

void HeapRegionSeq::uncommit_regions(uint start, size_t num_regions) {
  guarantee(num_regions >= 1, "Need to specify at least one region to uncommit");
  guarantee(_num_committed >= num_regions, "pre-condition");

  // Print before uncommitting.
  if (G1CollectedHeap::heap()->hr_printer()->is_active()) {
    for (uint i = start; i < start + num_regions; i++) {
      HeapRegion* hr = at(i);
      G1CollectedHeap::heap()->hr_printer()->uncommit(hr->bottom(), hr->end());
    }
  }

  HeapWord* old_end = heap_top();
  _num_committed -= (uint)num_regions;
  OrderAccess::fence();

  _storage.shrink_by(num_regions * HeapRegion::GrainBytes);
  update_committed_space(old_end, heap_top());
}

void HeapRegionSeq::make_regions_available(uint start, uint num_regions) {
  guarantee(num_regions > 0, "No point in calling this for zero regions");
  commit_regions(start, num_regions);
  for (uint i = start; i < start + num_regions; i++) {
    if (_regions.get_by_index(i) == NULL) {
      HeapRegion* new_hr = new_heap_region(i);
      _regions.set_by_index(i, new_hr);
      _allocated_heapregions_length = MAX2(_allocated_heapregions_length, i + 1);
    }
  }

  _num_committed += (size_t)num_regions;

  OrderAccess::fence();

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

uint HeapRegionSeq::expand_by(uint num_regions) {
  // Only ever expand from the end of the heap.
  return expand_at(_num_committed, num_regions);
}

uint HeapRegionSeq::expand_at(uint start, uint num_regions) {
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

uint HeapRegionSeq::find_contiguous(size_t num, bool empty_only) {
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
    return G1_NO_HRS_INDEX;
  }
}

HeapRegion* HeapRegionSeq::next_region_in_heap(const HeapRegion* r) const {
  guarantee(r != NULL, "Start region must be a valid region");
  guarantee(is_available(r->hrs_index()), err_msg("Trying to iterate starting from region %u which is not in the heap", r->hrs_index()));
  for (uint i = r->hrs_index() + 1; i < _allocated_heapregions_length; i++) {
    HeapRegion* hr = _regions.get_by_index(i);
    if (is_available(i)) {
      return hr;
    }
  }
  return NULL;
}

void HeapRegionSeq::iterate(HeapRegionClosure* blk) const {
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

uint HeapRegionSeq::find_unavailable_from_idx(uint start_idx, uint* res_idx) const {
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

uint HeapRegionSeq::start_region_for_worker(uint worker_i, uint num_workers, uint num_regions) const {
  return num_regions * worker_i / num_workers;
}

void HeapRegionSeq::par_iterate(HeapRegionClosure* blk, uint worker_id, uint num_workers, jint claim_value) const {
  const uint start_index = start_region_for_worker(worker_id, num_workers, _allocated_heapregions_length);

  // Every worker will actually look at all regions, skipping over regions that
  // are currently not committed.
  // This also (potentially) iterates over regions newly allocated during GC. This
  // is no problem except for some extra work.
  for (uint count = 0; count < _allocated_heapregions_length; count++) {
    const uint index = (start_index + count) % _allocated_heapregions_length;
    assert(0 <= index && index < _allocated_heapregions_length, "sanity");
    // Skip over unavailable regions
    if (!is_available(index)) {
      continue;
    }
    HeapRegion* r = _regions.get_by_index(index);
    // We'll ignore "continues humongous" regions (we'll process them
    // when we come across their corresponding "start humongous"
    // region) and regions already claimed.
    if (r->claim_value() == claim_value || r->continuesHumongous()) {
      continue;
    }
    // OK, try to claim it
    if (!r->claimHeapRegion(claim_value)) {
      continue;
    }
    // Success!
    if (r->startsHumongous()) {
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

        assert(chr->continuesHumongous(), "Must be humongous region");
        assert(chr->humongous_start_region() == r,
               err_msg("Must work on humongous continuation of the original start region "
                       PTR_FORMAT ", but is " PTR_FORMAT, p2i(r), p2i(chr)));
        assert(chr->claim_value() != claim_value,
               "Must not have been claimed yet because claiming of humongous continuation first claims the start region");

        bool claim_result = chr->claimHeapRegion(claim_value);
        // We should always be able to claim it; no one else should
        // be trying to claim this region.
        guarantee(claim_result, "We should always be able to claim the continuesHumongous part of the humongous object");

        bool res2 = blk->doHeapRegion(chr);
        if (res2) {
          return;
        }

        // Right now, this holds (i.e., no closure that actually
        // does something with "continues humongous" regions
        // clears them). We might have to weaken it in the future,
        // but let's leave these two asserts here for extra safety.
        assert(chr->continuesHumongous(), "should still be the case");
        assert(chr->humongous_start_region() == r, "sanity");
      }
    }

    bool res = blk->doHeapRegion(r);
    if (res) {
      return;
    }
  }
}

uint HeapRegionSeq::shrink_by(uint num_regions_to_remove) {
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

  if ((num_last_found = find_empty_from_idx_reverse(cur, &idx_last_found)) > 0) {
    // Only allow uncommit from the end of the heap.
    if ((idx_last_found + num_last_found) != _allocated_heapregions_length) {
      return 0;
    }
    uint to_remove = MIN2(num_regions_to_remove - removed, num_last_found);

    uncommit_regions(idx_last_found + num_last_found - to_remove, to_remove);

    cur -= num_last_found;
    removed += to_remove;
  }

  verify_optional();

  return removed;
}

uint HeapRegionSeq::find_empty_from_idx_reverse(uint start_idx, uint* res_idx) const {
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

void HeapRegionSeq::verify() {
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
              err_msg("invariant i: %u "HR_FORMAT" prev_end: "PTR_FORMAT,
                      i, HR_FORMAT_PARAMS(hr), p2i(prev_end)));
    guarantee(hr->hrs_index() == i,
              err_msg("invariant: i: %u hrs_index(): %u", i, hr->hrs_index()));
    // Asserts will fire if i is >= _length
    HeapWord* addr = hr->bottom();
    guarantee(addr_to_region(addr) == hr, "sanity");
    // We cannot check whether the region is part of a particular set: at the time
    // this method may be called, we have only completed allocation of the regions,
    // but not put into a region set.
    prev_committed = true;
    if (hr->startsHumongous()) {
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
void HeapRegionSeq::verify_optional() {
  verify();
}
#endif // PRODUCT


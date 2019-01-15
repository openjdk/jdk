/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionManager.inline.hpp"
#include "gc/g1/heapRegionSet.inline.hpp"
#include "gc/g1/heterogeneousHeapRegionManager.hpp"
#include "memory/allocation.hpp"


HeterogeneousHeapRegionManager* HeterogeneousHeapRegionManager::manager() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  assert(g1h != NULL, "Uninitialized access to HeterogeneousHeapRegionManager::manager()");

  HeapRegionManager* hrm = g1h->hrm();
  assert(hrm != NULL, "Uninitialized access to HeterogeneousHeapRegionManager::manager()");
  return (HeterogeneousHeapRegionManager*)hrm;
}

void HeterogeneousHeapRegionManager::initialize(G1RegionToSpaceMapper* heap_storage,
                                                G1RegionToSpaceMapper* prev_bitmap,
                                                G1RegionToSpaceMapper* next_bitmap,
                                                G1RegionToSpaceMapper* bot,
                                                G1RegionToSpaceMapper* cardtable,
                                                G1RegionToSpaceMapper* card_counts) {
  HeapRegionManager::initialize(heap_storage, prev_bitmap, next_bitmap, bot, cardtable, card_counts);

  // We commit bitmap for all regions during initialization and mark the bitmap space as special.
  // This allows regions to be un-committed while concurrent-marking threads are accessing the bitmap concurrently.
  _prev_bitmap_mapper->commit_and_set_special();
  _next_bitmap_mapper->commit_and_set_special();
}

// expand_by() is called to grow the heap. We grow into nvdimm now.
// Dram regions are committed later as needed during mutator region allocation or
// when young list target length is determined after gc cycle.
uint HeterogeneousHeapRegionManager::expand_by(uint num_regions, WorkGang* pretouch_workers) {
  uint num_regions_possible = total_regions_committed() >= max_expandable_length() ? 0 : max_expandable_length() - total_regions_committed();
  uint num_expanded = expand_nvdimm(MIN2(num_regions, num_regions_possible), pretouch_workers);
  return num_expanded;
}

// Expands heap starting from 'start' index. The question is should we expand from one memory (e.g. nvdimm) to another (e.g. dram).
// Looking at the code, expand_at() is called for humongous allocation where 'start' is in nv-dimm.
// So we only allocate regions in the same kind of memory as 'start'.
uint HeterogeneousHeapRegionManager::expand_at(uint start, uint num_regions, WorkGang* pretouch_workers) {
  if (num_regions == 0) {
    return 0;
  }
  uint target_num_regions = MIN2(num_regions, max_expandable_length() - total_regions_committed());
  uint end = is_in_nvdimm(start) ? end_index_of_nvdimm() : end_index_of_dram();

  uint num_expanded = expand_in_range(start, end, target_num_regions, pretouch_workers);
  assert(total_regions_committed() <= max_expandable_length(), "must be");
  return num_expanded;
}

// This function ensures that there are 'expected_num_regions' committed regions in dram.
// If new regions are committed, it un-commits that many regions from nv-dimm.
// If there are already more regions committed in dram, extra regions are un-committed.
void HeterogeneousHeapRegionManager::adjust_dram_regions(uint expected_num_regions, WorkGang* pretouch_workers) {

  // Release back the extra regions allocated in evacuation failure scenario.
  if(_no_borrowed_regions > 0) {
    _no_borrowed_regions -= shrink_dram(_no_borrowed_regions);
    _no_borrowed_regions -= shrink_nvdimm(_no_borrowed_regions);
  }

  if(expected_num_regions > free_list_dram_length()) {
    // If we are going to expand DRAM, we expand a little more so that we can absorb small variations in Young gen sizing.
    uint targeted_dram_regions = expected_num_regions * (1 + (double)G1YoungExpansionBufferPercent / 100);
    uint to_be_made_available = targeted_dram_regions - free_list_dram_length();

#ifdef ASSERT
    uint total_committed_before = total_regions_committed();
#endif
    uint can_be_made_available = shrink_nvdimm(to_be_made_available);
    uint ret = expand_dram(can_be_made_available, pretouch_workers);
#ifdef ASSERT
    assert(ret == can_be_made_available, "should be equal");
    assert(total_committed_before == total_regions_committed(), "invariant not met");
#endif
  } else {
    uint to_be_released = free_list_dram_length() - expected_num_regions;
    // if number of extra DRAM regions is small, do not shrink.
    if (to_be_released < expected_num_regions * G1YoungExpansionBufferPercent / 100) {
      return;
    }

#ifdef ASSERT
    uint total_committed_before = total_regions_committed();
#endif
    uint ret = shrink_dram(to_be_released);
    assert(ret == to_be_released, "Should be able to shrink by given amount");
    ret = expand_nvdimm(to_be_released, pretouch_workers);
#ifdef ASSERT
    assert(ret == to_be_released, "Should be able to expand by given amount");
    assert(total_committed_before == total_regions_committed(), "invariant not met");
#endif
  }
}

uint HeterogeneousHeapRegionManager::total_regions_committed() const {
  return num_committed_dram() + num_committed_nvdimm();
}

uint HeterogeneousHeapRegionManager::num_committed_dram() const {
  // This class does not keep count of committed regions in dram and nv-dimm.
  // G1RegionToHeteroSpaceMapper keeps this information.
  return static_cast<G1RegionToHeteroSpaceMapper*>(_heap_mapper)->num_committed_dram();
}

uint HeterogeneousHeapRegionManager::num_committed_nvdimm() const {
  // See comment for num_committed_dram()
  return static_cast<G1RegionToHeteroSpaceMapper*>(_heap_mapper)->num_committed_nvdimm();
}

// Return maximum number of regions that heap can expand to.
uint HeterogeneousHeapRegionManager::max_expandable_length() const {
  return _max_regions;
}

uint HeterogeneousHeapRegionManager::find_unavailable_in_range(uint start_idx, uint end_idx, uint* res_idx) const {
  guarantee(res_idx != NULL, "checking");
  guarantee(start_idx <= (max_length() + 1), "checking");

  uint num_regions = 0;

  uint cur = start_idx;
  while (cur <= end_idx && is_available(cur)) {
    cur++;
  }
  if (cur == end_idx + 1) {
    return num_regions;
  }
  *res_idx = cur;
  while (cur <= end_idx && !is_available(cur)) {
    cur++;
  }
  num_regions = cur - *res_idx;

#ifdef ASSERT
  for (uint i = *res_idx; i < (*res_idx + num_regions); i++) {
    assert(!is_available(i), "just checking");
  }
  assert(cur == end_idx + 1 || num_regions == 0 || is_available(cur),
    "The region at the current position %u must be available or at the end", cur);
#endif
  return num_regions;
}

uint HeterogeneousHeapRegionManager::expand_dram(uint num_regions, WorkGang* pretouch_workers) {
  return expand_in_range(start_index_of_dram(), end_index_of_dram(), num_regions, pretouch_workers);
}

uint HeterogeneousHeapRegionManager::expand_nvdimm(uint num_regions, WorkGang* pretouch_workers) {
  return expand_in_range(start_index_of_nvdimm(), end_index_of_nvdimm(), num_regions, pretouch_workers);
}

// Follows same logic as expand_at() form HeapRegionManager.
uint HeterogeneousHeapRegionManager::expand_in_range(uint start, uint end, uint num_regions, WorkGang* pretouch_gang) {

  uint so_far = 0;
  uint chunk_start = 0;
  uint num_last_found = 0;
  while (so_far < num_regions &&
         (num_last_found = find_unavailable_in_range(start, end, &chunk_start)) > 0) {
    uint to_commit = MIN2(num_regions - so_far, num_last_found);
    make_regions_available(chunk_start, to_commit, pretouch_gang);
    so_far += to_commit;
    start = chunk_start + to_commit + 1;
  }

  return so_far;
}

// Shrink in the range of indexes which are reserved for dram.
uint HeterogeneousHeapRegionManager::shrink_dram(uint num_regions, bool update_free_list) {
  return shrink_in_range(start_index_of_dram(), end_index_of_dram(), num_regions, update_free_list);
}

// Shrink in the range of indexes which are reserved for nv-dimm.
uint HeterogeneousHeapRegionManager::shrink_nvdimm(uint num_regions, bool update_free_list) {
  return shrink_in_range(start_index_of_nvdimm(), end_index_of_nvdimm(), num_regions, update_free_list);
}

// Find empty regions in given range, un-commit them and return the count.
uint HeterogeneousHeapRegionManager::shrink_in_range(uint start, uint end, uint num_regions, bool update_free_list) {

  if (num_regions == 0) {
    return 0;
  }
  uint so_far = 0;
  uint idx_last_found = 0;
  uint num_last_found;
  while (so_far < num_regions &&
         (num_last_found = find_empty_in_range_reverse(start, end, &idx_last_found)) > 0) {
    uint to_uncommit = MIN2(num_regions - so_far, num_last_found);
    if(update_free_list) {
      _free_list.remove_starting_at(at(idx_last_found + num_last_found - to_uncommit), to_uncommit);
    }
    uncommit_regions(idx_last_found + num_last_found - to_uncommit, to_uncommit);
    so_far += to_uncommit;
    end = idx_last_found;
  }
  return so_far;
}

uint HeterogeneousHeapRegionManager::find_empty_in_range_reverse(uint start_idx, uint end_idx, uint* res_idx) {
  guarantee(res_idx != NULL, "checking");
  guarantee(start_idx < max_length(), "checking");
  guarantee(end_idx < max_length(), "checking");
  if(start_idx > end_idx) {
    return 0;
  }

  uint num_regions_found = 0;

  jlong cur = end_idx;
  while (cur >= start_idx && !(is_available(cur) && at(cur)->is_empty())) {
    cur--;
  }
  if (cur == start_idx - 1) {
    return num_regions_found;
  }
  jlong old_cur = cur;
  // cur indexes the first empty region
  while (cur >= start_idx && is_available(cur) && at(cur)->is_empty()) {
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

HeapRegion* HeterogeneousHeapRegionManager::allocate_free_region(HeapRegionType type) {

  // We want to prevent mutators from proceeding when we have borrowed regions from the last collection. This
  // will force a full collection to remedy the situation.
  // Free region requests from GC threads can proceed.
  if(type.is_eden() || type.is_humongous()) {
    if(has_borrowed_regions()) {
      return NULL;
    }
  }

  // old and humongous regions are allocated from nv-dimm; eden and survivor regions are allocated from dram
  // assumption: dram regions take higher indexes
  bool from_nvdimm = (type.is_old() || type.is_humongous()) ? true : false;
  bool from_head = from_nvdimm;
  HeapRegion* hr = _free_list.remove_region(from_head);

  if (hr != NULL && ( (from_nvdimm && !is_in_nvdimm(hr->hrm_index())) || (!from_nvdimm && !is_in_dram(hr->hrm_index())) ) ) {
    _free_list.add_ordered(hr);
    hr = NULL;
  }

#ifdef ASSERT
  uint total_committed_before = total_regions_committed();
#endif

  if (hr == NULL) {
    if (!from_nvdimm) {
      uint ret = shrink_nvdimm(1);
      if (ret == 1) {
        ret = expand_dram(1, NULL);
        assert(ret == 1, "We should be able to commit one region");
        hr = _free_list.remove_region(from_head);
      }
    }
    else { /*is_old*/
      uint ret = shrink_dram(1);
      if (ret == 1) {
        ret = expand_nvdimm(1, NULL);
        assert(ret == 1, "We should be able to commit one region");
        hr = _free_list.remove_region(from_head);
      }
    }
  }
#ifdef ASSERT
  assert(total_committed_before == total_regions_committed(), "invariant not met");
#endif

  // When an old region is requested (which happens during collection pause) and we can't find any empty region
  // in the set of available regions (which is an evacuation failure scenario), we borrow (or pre-allocate) an unavailable region
  // from nv-dimm. This region is used to evacuate surviving objects from eden, survivor or old.
  if(hr == NULL && type.is_old()) {
    hr = borrow_old_region_for_gc();
  }

  if (hr != NULL) {
    assert(hr->next() == NULL, "Single region should not have next");
    assert(is_available(hr->hrm_index()), "Must be committed");
  }
  return hr;
}

uint HeterogeneousHeapRegionManager::find_contiguous_only_empty(size_t num) {
  if (has_borrowed_regions()) {
      return G1_NO_HRM_INDEX;
  }
  return find_contiguous(start_index_of_nvdimm(), end_index_of_nvdimm(), num, true);
}

uint HeterogeneousHeapRegionManager::find_contiguous_empty_or_unavailable(size_t num) {
  if (has_borrowed_regions()) {
    return G1_NO_HRM_INDEX;
  }
  return find_contiguous(start_index_of_nvdimm(), end_index_of_nvdimm(), num, false);
}

uint HeterogeneousHeapRegionManager::find_contiguous(size_t start, size_t end, size_t num, bool empty_only) {
  uint found = 0;
  size_t length_found = 0;
  uint cur = (uint)start;
  uint length_unavailable = 0;

  while (length_found < num && cur <= end) {
    HeapRegion* hr = _regions.get_by_index(cur);
    if ((!empty_only && !is_available(cur)) || (is_available(cur) && hr != NULL && hr->is_empty())) {
      // This region is a potential candidate for allocation into.
      if (!is_available(cur)) {
        if(shrink_dram(1) == 1) {
          uint ret = expand_in_range(cur, cur, 1, NULL);
          assert(ret == 1, "We should be able to expand at this index");
        } else {
          length_unavailable++;
        }
      }
      length_found++;
    }
    else {
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
                "Found region sequence starting at " UINT32_FORMAT ", length " SIZE_FORMAT
                " that is not empty at " UINT32_FORMAT ". Hr is " PTR_FORMAT, found, num, i, p2i(hr));
    }
    if (!empty_only && length_unavailable > (max_expandable_length() - total_regions_committed())) {
      // if 'length_unavailable' number of regions will be made available, we will exceed max regions.
      return G1_NO_HRM_INDEX;
    }
    return found;
  }
  else {
    return G1_NO_HRM_INDEX;
  }
}

uint HeterogeneousHeapRegionManager::find_highest_free(bool* expanded) {
  // Loop downwards from the highest dram region index, looking for an
  // entry which is either free or not yet committed.  If not yet
  // committed, expand_at that index.
  uint curr = end_index_of_dram();
  while (true) {
    HeapRegion *hr = _regions.get_by_index(curr);
    if (hr == NULL && !(total_regions_committed() < _max_regions)) {
      uint res = shrink_nvdimm(1);
      if (res == 1) {
        res = expand_in_range(curr, curr, 1, NULL);
        assert(res == 1, "We should be able to expand since shrink was successful");
        *expanded = true;
        return curr;
      }
    }
    else {
      if (hr->is_free()) {
        *expanded = false;
        return curr;
      }
    }
    if (curr == start_index_of_dram()) {
      return G1_NO_HRM_INDEX;
    }
    curr--;
  }
}

// We need to override this since region 0 which serves are dummy region in base class may not be available here.
// This is a corner condition when either number of regions is small. When adaptive sizing is used, initial heap size
// could be just one region.  This region is commited in dram to be used for young generation, leaving region 0 (which is in nvdimm)
// unavailable.
HeapRegion* HeterogeneousHeapRegionManager::get_dummy_region() {
  uint curr = 0;

  while (curr < _regions.length()) {
    if (is_available(curr)) {
      return new_heap_region(curr);
    }
    curr++;
  }
  assert(false, "We should always find a region available for dummy region");
  return NULL;
}

// First shrink in dram, then in nv-dimm.
uint HeterogeneousHeapRegionManager::shrink_by(uint num_regions) {
  // This call is made at end of full collection. Before making this call the region sets are tore down (tear_down_region_sets()).
  // So shrink() calls below do not need to remove uncomitted regions from free list.
  uint ret = shrink_dram(num_regions, false /* update_free_list */);
  ret += shrink_nvdimm(num_regions - ret, false /* update_free_list */);
  return ret;
}

void HeterogeneousHeapRegionManager::verify() {
  HeapRegionManager::verify();
}

uint HeterogeneousHeapRegionManager::free_list_dram_length() const {
  return _free_list.num_of_regions_in_range(start_index_of_dram(), end_index_of_dram());
}

uint HeterogeneousHeapRegionManager::free_list_nvdimm_length() const {
  return _free_list.num_of_regions_in_range(start_index_of_nvdimm(), end_index_of_nvdimm());
}

bool HeterogeneousHeapRegionManager::is_in_nvdimm(uint index) const {
  return index >= start_index_of_nvdimm() && index <= end_index_of_nvdimm();
}

bool HeterogeneousHeapRegionManager::is_in_dram(uint index) const {
  return index >= start_index_of_dram() && index <= end_index_of_dram();
}

// We have to make sure full collection copies all surviving objects to NV-DIMM.
// We might not have enough regions in nvdimm_set, so we need to make more regions on NV-DIMM available for full collection.
// Note: by doing this we are breaking the in-variant that total number of committed regions is equal to current heap size.
// After full collection ends, we will re-establish this in-variant by freeing DRAM regions.
void HeterogeneousHeapRegionManager::prepare_for_full_collection_start() {
  _total_commited_before_full_gc = total_regions_committed() - _no_borrowed_regions;
  _no_borrowed_regions = 0;
  expand_nvdimm(num_committed_dram(), NULL);
  remove_all_free_regions();
}

// We need to bring back the total committed regions to before full collection start.
// Unless we are close to OOM, all regular (not pinned) regions in DRAM should be free.
// We shrink all free regions in DRAM and if needed from NV-DIMM (when there are pinned DRAM regions)
// If we can't bring back committed regions count to _total_commited_before_full_gc, we keep the extra count in _no_borrowed_regions.
// When this GC finishes, new regions won't be allocated since has_borrowed_regions() is true. VM will be forced to re-try GC
// with clear soft references followed by OOM error in worst case.
void HeterogeneousHeapRegionManager::prepare_for_full_collection_end() {
  uint shrink_size = total_regions_committed() - _total_commited_before_full_gc;
  uint so_far = 0;
  uint idx_last_found = 0;
  uint num_last_found;
  uint end = (uint)_regions.length() - 1;
  while (so_far < shrink_size &&
         (num_last_found = find_empty_in_range_reverse(0, end, &idx_last_found)) > 0) {
    uint to_uncommit = MIN2(shrink_size - so_far, num_last_found);
    uncommit_regions(idx_last_found + num_last_found - to_uncommit, to_uncommit);
    so_far += to_uncommit;
    end = idx_last_found;
  }
  // See comment above the function.
  _no_borrowed_regions = shrink_size - so_far;
}

uint HeterogeneousHeapRegionManager::start_index_of_dram() const { return _max_regions;}

uint HeterogeneousHeapRegionManager::end_index_of_dram() const { return 2*_max_regions - 1; }

uint HeterogeneousHeapRegionManager::start_index_of_nvdimm() const { return 0; }

uint HeterogeneousHeapRegionManager::end_index_of_nvdimm() const { return _max_regions - 1; }

// This function is called when there are no free nv-dimm regions.
// It borrows a region from the set of unavailable regions in nv-dimm for GC purpose.
HeapRegion* HeterogeneousHeapRegionManager::borrow_old_region_for_gc() {
  assert(free_list_nvdimm_length() == 0, "this function should be called only when there are no nv-dimm regions in free list");

  uint ret = expand_nvdimm(1, NULL);
  if(ret != 1) {
    return NULL;
  }
  HeapRegion* hr = _free_list.remove_region(true /*from_head*/);
  assert(is_in_nvdimm(hr->hrm_index()), "allocated region should be in nv-dimm");
  _no_borrowed_regions++;
  return hr;
}

bool HeterogeneousHeapRegionManager::has_borrowed_regions() const {
  return _no_borrowed_regions > 0;
}

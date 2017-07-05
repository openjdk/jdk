/*
 * Copyright (c) 2001, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/collectionSetChooser.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
#include "gc_implementation/g1/g1ErgoVerbose.hpp"
#include "memory/space.inline.hpp"

// Even though we don't use the GC efficiency in our heuristics as
// much as we used to, we still order according to GC efficiency. This
// will cause regions with a lot of live objects and large RSets to
// end up at the end of the array. Given that we might skip collecting
// the last few old regions, if after a few mixed GCs the remaining
// have reclaimable bytes under a certain threshold, the hope is that
// the ones we'll skip are ones with both large RSets and a lot of
// live objects, not the ones with just a lot of live objects if we
// ordered according to the amount of reclaimable bytes per region.
static int order_regions(HeapRegion* hr1, HeapRegion* hr2) {
  if (hr1 == NULL) {
    if (hr2 == NULL) {
      return 0;
    } else {
      return 1;
    }
  } else if (hr2 == NULL) {
    return -1;
  }

  double gc_eff1 = hr1->gc_efficiency();
  double gc_eff2 = hr2->gc_efficiency();
  if (gc_eff1 > gc_eff2) {
    return -1;
  } if (gc_eff1 < gc_eff2) {
    return 1;
  } else {
    return 0;
  }
}

static int order_regions(HeapRegion** hr1p, HeapRegion** hr2p) {
  return order_regions(*hr1p, *hr2p);
}

CollectionSetChooser::CollectionSetChooser() :
  // The line below is the worst bit of C++ hackery I've ever written
  // (Detlefs, 11/23).  You should think of it as equivalent to
  // "_regions(100, true)": initialize the growable array and inform it
  // that it should allocate its elem array(s) on the C heap.
  //
  // The first argument, however, is actually a comma expression
  // (set_allocation_type(this, C_HEAP), 100). The purpose of the
  // set_allocation_type() call is to replace the default allocation
  // type for embedded objects STACK_OR_EMBEDDED with C_HEAP. It will
  // allow to pass the assert in GenericGrowableArray() which checks
  // that a growable array object must be on C heap if elements are.
  //
  // Note: containing object is allocated on C heap since it is CHeapObj.
  //
  _regions((ResourceObj::set_allocation_type((address) &_regions,
                                             ResourceObj::C_HEAP),
                  100), true /* C_Heap */),
    _curr_index(0), _length(0), _first_par_unreserved_idx(0),
    _region_live_threshold_bytes(0), _remaining_reclaimable_bytes(0) {
  _region_live_threshold_bytes =
    HeapRegion::GrainBytes * (size_t) G1MixedGCLiveThresholdPercent / 100;
}

#ifndef PRODUCT
void CollectionSetChooser::verify() {
  guarantee(_length <= regions_length(),
         err_msg("_length: %u regions length: %u", _length, regions_length()));
  guarantee(_curr_index <= _length,
            err_msg("_curr_index: %u _length: %u", _curr_index, _length));
  uint index = 0;
  size_t sum_of_reclaimable_bytes = 0;
  while (index < _curr_index) {
    guarantee(regions_at(index) == NULL,
              "all entries before _curr_index should be NULL");
    index += 1;
  }
  HeapRegion *prev = NULL;
  while (index < _length) {
    HeapRegion *curr = regions_at(index++);
    guarantee(curr != NULL, "Regions in _regions array cannot be NULL");
    guarantee(!curr->is_young(), "should not be young!");
    guarantee(!curr->isHumongous(), "should not be humongous!");
    if (prev != NULL) {
      guarantee(order_regions(prev, curr) != 1,
                err_msg("GC eff prev: %1.4f GC eff curr: %1.4f",
                        prev->gc_efficiency(), curr->gc_efficiency()));
    }
    sum_of_reclaimable_bytes += curr->reclaimable_bytes();
    prev = curr;
  }
  guarantee(sum_of_reclaimable_bytes == _remaining_reclaimable_bytes,
            err_msg("reclaimable bytes inconsistent, "
                    "remaining: "SIZE_FORMAT" sum: "SIZE_FORMAT,
                    _remaining_reclaimable_bytes, sum_of_reclaimable_bytes));
}
#endif // !PRODUCT

void CollectionSetChooser::sort_regions() {
  // First trim any unused portion of the top in the parallel case.
  if (_first_par_unreserved_idx > 0) {
    assert(_first_par_unreserved_idx <= regions_length(),
           "Or we didn't reserved enough length");
    regions_trunc_to(_first_par_unreserved_idx);
  }
  _regions.sort(order_regions);
  assert(_length <= regions_length(), "Requirement");
#ifdef ASSERT
  for (uint i = 0; i < _length; i++) {
    assert(regions_at(i) != NULL, "Should be true by sorting!");
  }
#endif // ASSERT
  if (G1PrintRegionLivenessInfo) {
    G1PrintRegionLivenessInfoClosure cl(gclog_or_tty, "Post-Sorting");
    for (uint i = 0; i < _length; ++i) {
      HeapRegion* r = regions_at(i);
      cl.doHeapRegion(r);
    }
  }
  verify();
}


void CollectionSetChooser::add_region(HeapRegion* hr) {
  assert(!hr->isHumongous(),
         "Humongous regions shouldn't be added to the collection set");
  assert(!hr->is_young(), "should not be young!");
  _regions.append(hr);
  _length++;
  _remaining_reclaimable_bytes += hr->reclaimable_bytes();
  hr->calc_gc_efficiency();
}

void CollectionSetChooser::prepare_for_par_region_addition(uint n_regions,
                                                           uint chunk_size) {
  _first_par_unreserved_idx = 0;
  uint n_threads = (uint) ParallelGCThreads;
  if (UseDynamicNumberOfGCThreads) {
    assert(G1CollectedHeap::heap()->workers()->active_workers() > 0,
      "Should have been set earlier");
    // This is defensive code. As the assertion above says, the number
    // of active threads should be > 0, but in case there is some path
    // or some improperly initialized variable with leads to no
    // active threads, protect against that in a product build.
    n_threads = MAX2(G1CollectedHeap::heap()->workers()->active_workers(),
                     1U);
  }
  uint max_waste = n_threads * chunk_size;
  // it should be aligned with respect to chunk_size
  uint aligned_n_regions = (n_regions + chunk_size - 1) / chunk_size * chunk_size;
  assert(aligned_n_regions % chunk_size == 0, "should be aligned");
  regions_at_put_grow(aligned_n_regions + max_waste - 1, NULL);
}

uint CollectionSetChooser::claim_array_chunk(uint chunk_size) {
  uint res = (uint) Atomic::add((jint) chunk_size,
                                (volatile jint*) &_first_par_unreserved_idx);
  assert(regions_length() > res + chunk_size - 1,
         "Should already have been expanded");
  return res - chunk_size;
}

void CollectionSetChooser::set_region(uint index, HeapRegion* hr) {
  assert(regions_at(index) == NULL, "precondition");
  assert(!hr->is_young(), "should not be young!");
  regions_at_put(index, hr);
  hr->calc_gc_efficiency();
}

void CollectionSetChooser::update_totals(uint region_num,
                                         size_t reclaimable_bytes) {
  // Only take the lock if we actually need to update the totals.
  if (region_num > 0) {
    assert(reclaimable_bytes > 0, "invariant");
    // We could have just used atomics instead of taking the
    // lock. However, we currently don't have an atomic add for size_t.
    MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
    _length += region_num;
    _remaining_reclaimable_bytes += reclaimable_bytes;
  } else {
    assert(reclaimable_bytes == 0, "invariant");
  }
}

void CollectionSetChooser::clear() {
  _regions.clear();
  _curr_index = 0;
  _length = 0;
  _remaining_reclaimable_bytes = 0;
};

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

CSetChooserCache::CSetChooserCache() {
  for (int i = 0; i < CacheLength; ++i)
    _cache[i] = NULL;
  clear();
}

void CSetChooserCache::clear() {
  _occupancy = 0;
  _first = 0;
  for (int i = 0; i < CacheLength; ++i) {
    HeapRegion *hr = _cache[i];
    if (hr != NULL)
      hr->set_sort_index(-1);
    _cache[i] = NULL;
  }
}

#ifndef PRODUCT
bool CSetChooserCache::verify() {
  guarantee(false, "CSetChooserCache::verify(): don't call this any more");

  int index = _first;
  HeapRegion *prev = NULL;
  for (int i = 0; i < _occupancy; ++i) {
    guarantee(_cache[index] != NULL, "cache entry should not be empty");
    HeapRegion *hr = _cache[index];
    guarantee(!hr->is_young(), "should not be young!");
    if (prev != NULL) {
      guarantee(prev->gc_efficiency() >= hr->gc_efficiency(),
                "cache should be correctly ordered");
    }
    guarantee(hr->sort_index() == get_sort_index(index),
              "sort index should be correct");
    index = trim_index(index + 1);
    prev = hr;
  }

  for (int i = 0; i < (CacheLength - _occupancy); ++i) {
    guarantee(_cache[index] == NULL, "cache entry should be empty");
    index = trim_index(index + 1);
  }

  guarantee(index == _first, "we should have reached where we started from");
  return true;
}
#endif // PRODUCT

void CSetChooserCache::insert(HeapRegion *hr) {
  guarantee(false, "CSetChooserCache::insert(): don't call this any more");

  assert(!is_full(), "cache should not be empty");
  hr->calc_gc_efficiency();

  int empty_index;
  if (_occupancy == 0) {
    empty_index = _first;
  } else {
    empty_index = trim_index(_first + _occupancy);
    assert(_cache[empty_index] == NULL, "last slot should be empty");
    int last_index = trim_index(empty_index - 1);
    HeapRegion *last = _cache[last_index];
    assert(last != NULL,"as the cache is not empty, last should not be empty");
    while (empty_index != _first &&
           last->gc_efficiency() < hr->gc_efficiency()) {
      _cache[empty_index] = last;
      last->set_sort_index(get_sort_index(empty_index));
      empty_index = last_index;
      last_index = trim_index(last_index - 1);
      last = _cache[last_index];
    }
  }
  _cache[empty_index] = hr;
  hr->set_sort_index(get_sort_index(empty_index));

  ++_occupancy;
  assert(verify(), "cache should be consistent");
}

HeapRegion *CSetChooserCache::remove_first() {
  guarantee(false, "CSetChooserCache::remove_first(): "
                   "don't call this any more");

  if (_occupancy > 0) {
    assert(_cache[_first] != NULL, "cache should have at least one region");
    HeapRegion *ret = _cache[_first];
    _cache[_first] = NULL;
    ret->set_sort_index(-1);
    --_occupancy;
    _first = trim_index(_first + 1);
    assert(verify(), "cache should be consistent");
    return ret;
  } else {
    return NULL;
  }
}

// Even though we don't use the GC efficiency in our heuristics as
// much as we used to, we still order according to GC efficiency. This
// will cause regions with a lot of live objects and large RSets to
// end up at the end of the array. Given that we might skip collecting
// the last few old regions, if after a few mixed GCs the remaining
// have reclaimable bytes under a certain threshold, the hope is that
// the ones we'll skip are ones with both large RSets and a lot of
// live objects, not the ones with just a lot of live objects if we
// ordered according to the amount of reclaimable bytes per region.
static int orderRegions(HeapRegion* hr1, HeapRegion* hr2) {
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

static int orderRegions(HeapRegion** hr1p, HeapRegion** hr2p) {
  return orderRegions(*hr1p, *hr2p);
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
  _markedRegions((ResourceObj::set_allocation_type((address)&_markedRegions,
                                             ResourceObj::C_HEAP),
                  100), true /* C_Heap */),
    _curr_index(0), _length(0),
    _regionLiveThresholdBytes(0), _remainingReclaimableBytes(0),
    _first_par_unreserved_idx(0) {
  _regionLiveThresholdBytes =
    HeapRegion::GrainBytes * (size_t) G1OldCSetRegionLiveThresholdPercent / 100;
}

#ifndef PRODUCT
bool CollectionSetChooser::verify() {
  guarantee(_length >= 0, err_msg("_length: %d", _length));
  guarantee(0 <= _curr_index && _curr_index <= _length,
            err_msg("_curr_index: %d _length: %d", _curr_index, _length));
  int index = 0;
  size_t sum_of_reclaimable_bytes = 0;
  while (index < _curr_index) {
    guarantee(_markedRegions.at(index) == NULL,
              "all entries before _curr_index should be NULL");
    index += 1;
  }
  HeapRegion *prev = NULL;
  while (index < _length) {
    HeapRegion *curr = _markedRegions.at(index++);
    guarantee(curr != NULL, "Regions in _markedRegions array cannot be NULL");
    int si = curr->sort_index();
    guarantee(!curr->is_young(), "should not be young!");
    guarantee(!curr->isHumongous(), "should not be humongous!");
    guarantee(si > -1 && si == (index-1), "sort index invariant");
    if (prev != NULL) {
      guarantee(orderRegions(prev, curr) != 1,
                err_msg("GC eff prev: %1.4f GC eff curr: %1.4f",
                        prev->gc_efficiency(), curr->gc_efficiency()));
    }
    sum_of_reclaimable_bytes += curr->reclaimable_bytes();
    prev = curr;
  }
  guarantee(sum_of_reclaimable_bytes == _remainingReclaimableBytes,
            err_msg("reclaimable bytes inconsistent, "
                    "remaining: "SIZE_FORMAT" sum: "SIZE_FORMAT,
                    _remainingReclaimableBytes, sum_of_reclaimable_bytes));
  return true;
}
#endif

void CollectionSetChooser::fillCache() {
  guarantee(false, "fillCache: don't call this any more");

  while (!_cache.is_full() && (_curr_index < _length)) {
    HeapRegion* hr = _markedRegions.at(_curr_index);
    assert(hr != NULL,
           err_msg("Unexpected NULL hr in _markedRegions at index %d",
                   _curr_index));
    _curr_index += 1;
    assert(!hr->is_young(), "should not be young!");
    assert(hr->sort_index() == _curr_index-1, "sort_index invariant");
    _markedRegions.at_put(hr->sort_index(), NULL);
    _cache.insert(hr);
    assert(!_cache.is_empty(), "cache should not be empty");
  }
  assert(verify(), "cache should be consistent");
}

void CollectionSetChooser::sortMarkedHeapRegions() {
  // First trim any unused portion of the top in the parallel case.
  if (_first_par_unreserved_idx > 0) {
    if (G1PrintParCleanupStats) {
      gclog_or_tty->print("     Truncating _markedRegions from %d to %d.\n",
                          _markedRegions.length(), _first_par_unreserved_idx);
    }
    assert(_first_par_unreserved_idx <= _markedRegions.length(),
           "Or we didn't reserved enough length");
    _markedRegions.trunc_to(_first_par_unreserved_idx);
  }
  _markedRegions.sort(orderRegions);
  assert(_length <= _markedRegions.length(), "Requirement");
  assert(_length == 0 || _markedRegions.at(_length - 1) != NULL,
         "Testing _length");
  assert(_length == _markedRegions.length() ||
                        _markedRegions.at(_length) == NULL, "Testing _length");
  if (G1PrintParCleanupStats) {
    gclog_or_tty->print_cr("     Sorted %d marked regions.", _length);
  }
  for (int i = 0; i < _length; i++) {
    assert(_markedRegions.at(i) != NULL, "Should be true by sorting!");
    _markedRegions.at(i)->set_sort_index(i);
  }
  if (G1PrintRegionLivenessInfo) {
    G1PrintRegionLivenessInfoClosure cl(gclog_or_tty, "Post-Sorting");
    for (int i = 0; i < _length; ++i) {
      HeapRegion* r = _markedRegions.at(i);
      cl.doHeapRegion(r);
    }
  }
  assert(verify(), "CSet chooser verification");
}

size_t CollectionSetChooser::calcMinOldCSetLength() {
  // The min old CSet region bound is based on the maximum desired
  // number of mixed GCs after a cycle. I.e., even if some old regions
  // look expensive, we should add them to the CSet anyway to make
  // sure we go through the available old regions in no more than the
  // maximum desired number of mixed GCs.
  //
  // The calculation is based on the number of marked regions we added
  // to the CSet chooser in the first place, not how many remain, so
  // that the result is the same during all mixed GCs that follow a cycle.

  const size_t region_num = (size_t) _length;
  const size_t gc_num = (size_t) G1MaxMixedGCNum;
  size_t result = region_num / gc_num;
  // emulate ceiling
  if (result * gc_num < region_num) {
    result += 1;
  }
  return result;
}

size_t CollectionSetChooser::calcMaxOldCSetLength() {
  // The max old CSet region bound is based on the threshold expressed
  // as a percentage of the heap size. I.e., it should bound the
  // number of old regions added to the CSet irrespective of how many
  // of them are available.

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  const size_t region_num = g1h->n_regions();
  const size_t perc = (size_t) G1OldCSetRegionThresholdPercent;
  size_t result = region_num * perc / 100;
  // emulate ceiling
  if (100 * result < region_num * perc) {
    result += 1;
  }
  return result;
}

void CollectionSetChooser::addMarkedHeapRegion(HeapRegion* hr) {
  assert(!hr->isHumongous(),
         "Humongous regions shouldn't be added to the collection set");
  assert(!hr->is_young(), "should not be young!");
  _markedRegions.append(hr);
  _length++;
  _remainingReclaimableBytes += hr->reclaimable_bytes();
  hr->calc_gc_efficiency();
}

void CollectionSetChooser::prepareForAddMarkedHeapRegionsPar(size_t n_regions,
                                                             size_t chunkSize) {
  _first_par_unreserved_idx = 0;
  int n_threads = ParallelGCThreads;
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
  size_t max_waste = n_threads * chunkSize;
  // it should be aligned with respect to chunkSize
  size_t aligned_n_regions =
                     (n_regions + (chunkSize - 1)) / chunkSize * chunkSize;
  assert( aligned_n_regions % chunkSize == 0, "should be aligned" );
  _markedRegions.at_put_grow((int)(aligned_n_regions + max_waste - 1), NULL);
}

jint CollectionSetChooser::getParMarkedHeapRegionChunk(jint n_regions) {
  // Don't do this assert because this can be called at a point
  // where the loop up stream will not execute again but might
  // try to claim more chunks (loop test has not been done yet).
  // assert(_markedRegions.length() > _first_par_unreserved_idx,
  //  "Striding beyond the marked regions");
  jint res = Atomic::add(n_regions, &_first_par_unreserved_idx);
  assert(_markedRegions.length() > res + n_regions - 1,
         "Should already have been expanded");
  return res - n_regions;
}

void CollectionSetChooser::setMarkedHeapRegion(jint index, HeapRegion* hr) {
  assert(_markedRegions.at(index) == NULL, "precondition");
  assert(!hr->is_young(), "should not be young!");
  _markedRegions.at_put(index, hr);
  hr->calc_gc_efficiency();
}

void CollectionSetChooser::updateTotals(jint region_num,
                                        size_t reclaimable_bytes) {
  // Only take the lock if we actually need to update the totals.
  if (region_num > 0) {
    assert(reclaimable_bytes > 0, "invariant");
    // We could have just used atomics instead of taking the
    // lock. However, we currently don't have an atomic add for size_t.
    MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
    _length += (int) region_num;
    _remainingReclaimableBytes += reclaimable_bytes;
  } else {
    assert(reclaimable_bytes == 0, "invariant");
  }
}

void CollectionSetChooser::clearMarkedHeapRegions() {
  for (int i = 0; i < _markedRegions.length(); i++) {
    HeapRegion* r = _markedRegions.at(i);
    if (r != NULL) {
      r->set_sort_index(-1);
    }
  }
  _markedRegions.clear();
  _curr_index = 0;
  _length = 0;
  _remainingReclaimableBytes = 0;
};

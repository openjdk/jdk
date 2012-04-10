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

#ifndef SHARE_VM_GC_IMPLEMENTATION_G1_COLLECTIONSETCHOOSER_HPP
#define SHARE_VM_GC_IMPLEMENTATION_G1_COLLECTIONSETCHOOSER_HPP

#include "gc_implementation/g1/heapRegion.hpp"
#include "utilities/growableArray.hpp"

class CSetChooserCache VALUE_OBJ_CLASS_SPEC {
private:
  enum {
    CacheLength = 16
  } PrivateConstants;

  HeapRegion*  _cache[CacheLength];
  int          _occupancy; // number of regions in cache
  int          _first;     // (index of) "first" region in the cache

  // adding CacheLength to deal with negative values
  inline int trim_index(int index) {
    return (index + CacheLength) % CacheLength;
  }

  inline int get_sort_index(int index) {
    return -index-2;
  }
  inline int get_index(int sort_index) {
    return -sort_index-2;
  }

public:
  CSetChooserCache(void);

  inline int occupancy(void) { return _occupancy; }
  inline bool is_full()      { return _occupancy == CacheLength; }
  inline bool is_empty()     { return _occupancy == 0; }

  void clear(void);
  void insert(HeapRegion *hr);
  HeapRegion *remove_first(void);
  inline HeapRegion *get_first(void) {
    return _cache[_first];
  }

#ifndef PRODUCT
  bool verify (void);
  bool region_in_cache(HeapRegion *hr) {
    int sort_index = hr->sort_index();
    if (sort_index < -1) {
      int index = get_index(sort_index);
      guarantee(index < CacheLength, "should be within bounds");
      return _cache[index] == hr;
    } else
      return 0;
  }
#endif // PRODUCT
};

class CollectionSetChooser: public CHeapObj {

  GrowableArray<HeapRegion*> _markedRegions;

  // The index of the next candidate old region to be considered for
  // addition to the CSet.
  int _curr_index;

  // The number of candidate old regions added to the CSet chooser.
  int _length;

  CSetChooserCache _cache;
  jint _first_par_unreserved_idx;

  // If a region has more live bytes than this threshold, it will not
  // be added to the CSet chooser and will not be a candidate for
  // collection.
  size_t _regionLiveThresholdBytes;

  // The sum of reclaimable bytes over all the regions in the CSet chooser.
  size_t _remainingReclaimableBytes;

public:

  // Return the current candidate region to be considered for
  // collection without removing it from the CSet chooser.
  HeapRegion* peek() {
    HeapRegion* res = NULL;
    if (_curr_index < _length) {
      res = _markedRegions.at(_curr_index);
      assert(res != NULL,
             err_msg("Unexpected NULL hr in _markedRegions at index %d",
                     _curr_index));
    }
    return res;
  }

  // Remove the given region from the CSet chooser and move to the
  // next one. The given region should be the current candidate region
  // in the CSet chooser.
  void remove_and_move_to_next(HeapRegion* hr) {
    assert(hr != NULL, "pre-condition");
    assert(_curr_index < _length, "pre-condition");
    assert(_markedRegions.at(_curr_index) == hr, "pre-condition");
    hr->set_sort_index(-1);
    _markedRegions.at_put(_curr_index, NULL);
    assert(hr->reclaimable_bytes() <= _remainingReclaimableBytes,
           err_msg("remaining reclaimable bytes inconsistent "
                   "from region: "SIZE_FORMAT" remaining: "SIZE_FORMAT,
                   hr->reclaimable_bytes(), _remainingReclaimableBytes));
    _remainingReclaimableBytes -= hr->reclaimable_bytes();
    _curr_index += 1;
  }

  CollectionSetChooser();

  void sortMarkedHeapRegions();
  void fillCache();

  // Determine whether to add the given region to the CSet chooser or
  // not. Currently, we skip humongous regions (we never add them to
  // the CSet, we only reclaim them during cleanup) and regions whose
  // live bytes are over the threshold.
  bool shouldAdd(HeapRegion* hr) {
    assert(hr->is_marked(), "pre-condition");
    assert(!hr->is_young(), "should never consider young regions");
    return !hr->isHumongous() &&
            hr->live_bytes() < _regionLiveThresholdBytes;
  }

  // Calculate the minimum number of old regions we'll add to the CSet
  // during a mixed GC.
  size_t calcMinOldCSetLength();

  // Calculate the maximum number of old regions we'll add to the CSet
  // during a mixed GC.
  size_t calcMaxOldCSetLength();

  // Serial version.
  void addMarkedHeapRegion(HeapRegion *hr);

  // Must be called before calls to getParMarkedHeapRegionChunk.
  // "n_regions" is the number of regions, "chunkSize" the chunk size.
  void prepareForAddMarkedHeapRegionsPar(size_t n_regions, size_t chunkSize);
  // Returns the first index in a contiguous chunk of "n_regions" indexes
  // that the calling thread has reserved.  These must be set by the
  // calling thread using "setMarkedHeapRegion" (to NULL if necessary).
  jint getParMarkedHeapRegionChunk(jint n_regions);
  // Set the marked array entry at index to hr.  Careful to claim the index
  // first if in parallel.
  void setMarkedHeapRegion(jint index, HeapRegion* hr);
  // Atomically increment the number of added regions by region_num
  // and the amount of reclaimable bytes by reclaimable_bytes.
  void updateTotals(jint region_num, size_t reclaimable_bytes);

  void clearMarkedHeapRegions();

  // Return the number of candidate regions that remain to be collected.
  size_t remainingRegions() { return _length - _curr_index; }

  // Determine whether the CSet chooser has more candidate regions or not.
  bool isEmpty() { return remainingRegions() == 0; }

  // Return the reclaimable bytes that remain to be collected on
  // all the candidate regions in the CSet chooser.
  size_t remainingReclaimableBytes () { return _remainingReclaimableBytes; }

  // Returns true if the used portion of "_markedRegions" is properly
  // sorted, otherwise asserts false.
#ifndef PRODUCT
  bool verify(void);
  bool regionProperlyOrdered(HeapRegion* r) {
    int si = r->sort_index();
    if (si > -1) {
      guarantee(_curr_index <= si && si < _length,
                err_msg("curr: %d sort index: %d: length: %d",
                        _curr_index, si, _length));
      guarantee(_markedRegions.at(si) == r,
                err_msg("sort index: %d at: "PTR_FORMAT" r: "PTR_FORMAT,
                        si, _markedRegions.at(si), r));
    } else {
      guarantee(si == -1, err_msg("sort index: %d", si));
    }
    return true;
  }
#endif

};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_COLLECTIONSETCHOOSER_HPP

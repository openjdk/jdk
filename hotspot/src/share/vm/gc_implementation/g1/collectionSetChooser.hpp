/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

// We need to sort heap regions by collection desirability.

class CSetChooserCache VALUE_OBJ_CLASS_SPEC {
private:
  enum {
    CacheLength = 16
  } PrivateConstants;

  HeapRegion*  _cache[CacheLength];
  int          _occupancy; // number of region in cache
  int          _first; // "first" region in the cache

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
  void remove (HeapRegion *hr);
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
  int _curMarkedIndex;
  int _numMarkedRegions;
  CSetChooserCache _cache;

  // True iff last collection pause ran of out new "age 0" regions, and
  // returned an "age 1" region.
  bool _unmarked_age_1_returned_as_new;

  jint _first_par_unreserved_idx;

public:

  HeapRegion* getNextMarkedRegion(double time_so_far, double avg_prediction);

  CollectionSetChooser();

  void printSortedHeapRegions();

  void sortMarkedHeapRegions();
  void fillCache();
  bool addRegionToCache(void);
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
  // Atomically increment the number of claimed regions by "inc_by".
  void incNumMarkedHeapRegions(jint inc_by);

  void clearMarkedHeapRegions();

  void updateAfterFullCollection();

  // Ensure that "hr" is not a member of the marked region array or the cache
  void removeRegion(HeapRegion* hr);

  bool unmarked_age_1_returned_as_new() { return _unmarked_age_1_returned_as_new; }

  // Returns true if the used portion of "_markedRegions" is properly
  // sorted, otherwise asserts false.
#ifndef PRODUCT
  bool verify(void);
  bool regionProperlyOrdered(HeapRegion* r) {
    int si = r->sort_index();
    return (si == -1) ||
      (si > -1 && _markedRegions.at(si) == r) ||
      (si < -1 && _cache.region_in_cache(r));
  }
#endif

};

#endif // SHARE_VM_GC_IMPLEMENTATION_G1_COLLECTIONSETCHOOSER_HPP

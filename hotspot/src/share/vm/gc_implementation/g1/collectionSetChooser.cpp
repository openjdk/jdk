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

#include "precompiled.hpp"
#include "gc_implementation/g1/collectionSetChooser.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1CollectorPolicy.hpp"
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

// this is a bit expensive... but we expect that it should not be called
// to often.
void CSetChooserCache::remove(HeapRegion *hr) {
  assert(_occupancy > 0, "cache should not be empty");
  assert(hr->sort_index() < -1, "should already be in the cache");
  int index = get_index(hr->sort_index());
  assert(_cache[index] == hr, "index should be correct");
  int next_index = trim_index(index + 1);
  int last_index = trim_index(_first + _occupancy - 1);
  while (index != last_index) {
    assert(_cache[next_index] != NULL, "should not be null");
    _cache[index] = _cache[next_index];
    _cache[index]->set_sort_index(get_sort_index(index));

    index = next_index;
    next_index = trim_index(next_index+1);
  }
  assert(index == last_index, "should have reached the last one");
  _cache[index] = NULL;
  hr->set_sort_index(-1);
  --_occupancy;
  assert(verify(), "cache should be consistent");
}

static inline int orderRegions(HeapRegion* hr1, HeapRegion* hr2) {
  if (hr1 == NULL) {
    if (hr2 == NULL) return 0;
    else return 1;
  } else if (hr2 == NULL) {
    return -1;
  }
  if (hr2->gc_efficiency() < hr1->gc_efficiency()) return -1;
  else if (hr1->gc_efficiency() < hr2->gc_efficiency()) return 1;
  else return 0;
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
                  100),
                 true),
  _curMarkedIndex(0),
  _numMarkedRegions(0),
  _unmarked_age_1_returned_as_new(false),
  _first_par_unreserved_idx(0)
{}



#ifndef PRODUCT
bool CollectionSetChooser::verify() {
  int index = 0;
  guarantee(_curMarkedIndex <= _numMarkedRegions,
            "_curMarkedIndex should be within bounds");
  while (index < _curMarkedIndex) {
    guarantee(_markedRegions.at(index++) == NULL,
              "all entries before _curMarkedIndex should be NULL");
  }
  HeapRegion *prev = NULL;
  while (index < _numMarkedRegions) {
    HeapRegion *curr = _markedRegions.at(index++);
    if (curr != NULL) {
      int si = curr->sort_index();
      guarantee(!curr->is_young(), "should not be young!");
      guarantee(si > -1 && si == (index-1), "sort index invariant");
      if (prev != NULL) {
        guarantee(orderRegions(prev, curr) != 1, "regions should be sorted");
      }
      prev = curr;
    }
  }
  return _cache.verify();
}
#endif

bool
CollectionSetChooser::addRegionToCache() {
  assert(!_cache.is_full(), "cache should not be full");

  HeapRegion *hr = NULL;
  while (hr == NULL && _curMarkedIndex < _numMarkedRegions) {
    hr = _markedRegions.at(_curMarkedIndex++);
  }
  if (hr == NULL)
    return false;
  assert(!hr->is_young(), "should not be young!");
  assert(hr->sort_index() == _curMarkedIndex-1, "sort_index invariant");
  _markedRegions.at_put(hr->sort_index(), NULL);
  _cache.insert(hr);
  assert(!_cache.is_empty(), "cache should not be empty");
  assert(verify(), "cache should be consistent");
  return false;
}

void
CollectionSetChooser::fillCache() {
  while (!_cache.is_full() && addRegionToCache()) {
  }
}

void
CollectionSetChooser::sortMarkedHeapRegions() {
  guarantee(_cache.is_empty(), "cache should be empty");
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
  assert(_numMarkedRegions <= _markedRegions.length(), "Requirement");
  assert(_numMarkedRegions == 0
         || _markedRegions.at(_numMarkedRegions-1) != NULL,
         "Testing _numMarkedRegions");
  assert(_numMarkedRegions == _markedRegions.length()
         || _markedRegions.at(_numMarkedRegions) == NULL,
         "Testing _numMarkedRegions");
  if (G1PrintParCleanupStats) {
    gclog_or_tty->print_cr("     Sorted %d marked regions.", _numMarkedRegions);
  }
  for (int i = 0; i < _numMarkedRegions; i++) {
    assert(_markedRegions.at(i) != NULL, "Should be true by sorting!");
    _markedRegions.at(i)->set_sort_index(i);
    if (G1PrintRegionLivenessInfo > 0) {
      if (i == 0) gclog_or_tty->print_cr("Sorted marked regions:");
      if (i < G1PrintRegionLivenessInfo ||
          (_numMarkedRegions-i) < G1PrintRegionLivenessInfo) {
        HeapRegion* hr = _markedRegions.at(i);
        size_t u = hr->used();
        gclog_or_tty->print_cr("  Region %d: %d used, %d max live, %5.2f%%.",
                      i, u, hr->max_live_bytes(),
                      100.0*(float)hr->max_live_bytes()/(float)u);
      }
    }
  }
  if (G1PolicyVerbose > 1)
    printSortedHeapRegions();
  assert(verify(), "should now be sorted");
}

void
printHeapRegion(HeapRegion *hr) {
  if (hr->isHumongous())
    gclog_or_tty->print("H: ");
  if (hr->in_collection_set())
    gclog_or_tty->print("CS: ");
  gclog_or_tty->print_cr("Region " PTR_FORMAT " (%s%s) "
                         "[" PTR_FORMAT ", " PTR_FORMAT"] "
                         "Used: " SIZE_FORMAT "K, garbage: " SIZE_FORMAT "K.",
                         hr, hr->is_young() ? "Y " : "  ",
                         hr->is_marked()? "M1" : "M0",
                         hr->bottom(), hr->end(),
                         hr->used()/K, hr->garbage_bytes()/K);
}

void
CollectionSetChooser::addMarkedHeapRegion(HeapRegion* hr) {
  assert(!hr->isHumongous(),
         "Humongous regions shouldn't be added to the collection set");
  assert(!hr->is_young(), "should not be young!");
  _markedRegions.append(hr);
  _numMarkedRegions++;
  hr->calc_gc_efficiency();
}

void
CollectionSetChooser::
prepareForAddMarkedHeapRegionsPar(size_t n_regions, size_t chunkSize) {
  _first_par_unreserved_idx = 0;
  size_t max_waste = ParallelGCThreads * chunkSize;
  // it should be aligned with respect to chunkSize
  size_t aligned_n_regions =
                     (n_regions + (chunkSize - 1)) / chunkSize * chunkSize;
  assert( aligned_n_regions % chunkSize == 0, "should be aligned" );
  _markedRegions.at_put_grow((int)(aligned_n_regions + max_waste - 1), NULL);
}

jint
CollectionSetChooser::getParMarkedHeapRegionChunk(jint n_regions) {
  jint res = Atomic::add(n_regions, &_first_par_unreserved_idx);
  assert(_markedRegions.length() > res + n_regions - 1,
         "Should already have been expanded");
  return res - n_regions;
}

void
CollectionSetChooser::setMarkedHeapRegion(jint index, HeapRegion* hr) {
  assert(_markedRegions.at(index) == NULL, "precondition");
  assert(!hr->is_young(), "should not be young!");
  _markedRegions.at_put(index, hr);
  hr->calc_gc_efficiency();
}

void
CollectionSetChooser::incNumMarkedHeapRegions(jint inc_by) {
  (void)Atomic::add(inc_by, &_numMarkedRegions);
}

void
CollectionSetChooser::clearMarkedHeapRegions(){
  for (int i = 0; i < _markedRegions.length(); i++) {
    HeapRegion* r =   _markedRegions.at(i);
    if (r != NULL) r->set_sort_index(-1);
  }
  _markedRegions.clear();
  _curMarkedIndex = 0;
  _numMarkedRegions = 0;
  _cache.clear();
};

void
CollectionSetChooser::updateAfterFullCollection() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  clearMarkedHeapRegions();
}

void
CollectionSetChooser::printSortedHeapRegions() {
  gclog_or_tty->print_cr("Printing %d Heap Regions sorted by amount of known garbage",
                _numMarkedRegions);

  DEBUG_ONLY(int marked_count = 0;)
  for (int i = 0; i < _markedRegions.length(); i++) {
    HeapRegion* r = _markedRegions.at(i);
    if (r != NULL) {
      printHeapRegion(r);
      DEBUG_ONLY(marked_count++;)
    }
  }
  assert(marked_count == _numMarkedRegions, "must be");
  gclog_or_tty->print_cr("Done sorted heap region print");
}

void CollectionSetChooser::removeRegion(HeapRegion *hr) {
  int si = hr->sort_index();
  assert(si == -1 || hr->is_marked(), "Sort index not valid.");
  if (si > -1) {
    assert(_markedRegions.at(si) == hr, "Sort index not valid." );
    _markedRegions.at_put(si, NULL);
  } else if (si < -1) {
    assert(_cache.region_in_cache(hr), "should be in the cache");
    _cache.remove(hr);
    assert(hr->sort_index() == -1, "sort index invariant");
  }
  hr->set_sort_index(-1);
}

// if time_remaining < 0.0, then this method should try to return
// a region, whether it fits within the remaining time or not
HeapRegion*
CollectionSetChooser::getNextMarkedRegion(double time_remaining,
                                          double avg_prediction) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1CollectorPolicy* g1p = g1h->g1_policy();
  fillCache();
  if (_cache.is_empty()) {
    assert(_curMarkedIndex == _numMarkedRegions,
           "if cache is empty, list should also be empty");
    return NULL;
  }

  HeapRegion *hr = _cache.get_first();
  assert(hr != NULL, "if cache not empty, first entry should be non-null");
  double predicted_time = g1h->predict_region_elapsed_time_ms(hr, false);

  if (g1p->adaptive_young_list_length()) {
    if (time_remaining - predicted_time < 0.0) {
      g1h->check_if_region_is_too_expensive(predicted_time);
      return NULL;
    }
  } else {
    if (predicted_time > 2.0 * avg_prediction) {
      return NULL;
    }
  }

  HeapRegion *hr2 = _cache.remove_first();
  assert(hr == hr2, "cache contents should not have changed");

  return hr;
}

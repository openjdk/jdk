/*
 * Copyright (c) 2001, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/heapRegionSeq.hpp"
#include "memory/allocation.hpp"

// Local to this file.

static int orderRegions(HeapRegion** hr1p, HeapRegion** hr2p) {
  if ((*hr1p)->end() <= (*hr2p)->bottom()) return -1;
  else if ((*hr2p)->end() <= (*hr1p)->bottom()) return 1;
  else if (*hr1p == *hr2p) return 0;
  else {
    assert(false, "We should never compare distinct overlapping regions.");
  }
  return 0;
}

HeapRegionSeq::HeapRegionSeq(const size_t max_size) :
  _alloc_search_start(0),
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
  _regions((ResourceObj::set_allocation_type((address)&_regions,
                                             ResourceObj::C_HEAP),
            (int)max_size),
           true),
  _next_rr_candidate(0),
  _seq_bottom(NULL)
{}

// Private methods.

void HeapRegionSeq::print_empty_runs() {
  int empty_run = 0;
  int n_empty = 0;
  int empty_run_start;
  for (int i = 0; i < _regions.length(); i++) {
    HeapRegion* r = _regions.at(i);
    if (r->continuesHumongous()) continue;
    if (r->is_empty()) {
      assert(!r->isHumongous(), "H regions should not be empty.");
      if (empty_run == 0) empty_run_start = i;
      empty_run++;
      n_empty++;
    } else {
      if (empty_run > 0) {
        gclog_or_tty->print("  %d:%d", empty_run_start, empty_run);
        empty_run = 0;
      }
    }
  }
  if (empty_run > 0) {
    gclog_or_tty->print(" %d:%d", empty_run_start, empty_run);
  }
  gclog_or_tty->print_cr(" [tot = %d]", n_empty);
}

int HeapRegionSeq::find(HeapRegion* hr) {
  // FIXME: optimized for adjacent regions of fixed size.
  int ind = hr->hrs_index();
  if (ind != -1) {
    assert(_regions.at(ind) == hr, "Mismatch");
  }
  return ind;
}


// Public methods.

void HeapRegionSeq::insert(HeapRegion* hr) {
  assert(!_regions.is_full(), "Too many elements in HeapRegionSeq");
  if (_regions.length() == 0
      || _regions.top()->end() <= hr->bottom()) {
    hr->set_hrs_index(_regions.length());
    _regions.append(hr);
  } else {
    _regions.append(hr);
    _regions.sort(orderRegions);
    for (int i = 0; i < _regions.length(); i++) {
      _regions.at(i)->set_hrs_index(i);
    }
  }
  char* bot = (char*)_regions.at(0)->bottom();
  if (_seq_bottom == NULL || bot < _seq_bottom) _seq_bottom = bot;
}

size_t HeapRegionSeq::length() {
  return _regions.length();
}

size_t HeapRegionSeq::free_suffix() {
  size_t res = 0;
  int first = _regions.length() - 1;
  int cur = first;
  while (cur >= 0 &&
         (_regions.at(cur)->is_empty()
          && (first == cur
              || (_regions.at(cur+1)->bottom() ==
                  _regions.at(cur)->end())))) {
      res++;
      cur--;
  }
  return res;
}

int HeapRegionSeq::find_contiguous_from(int from, size_t num) {
  assert(num > 1, "pre-condition");
  assert(0 <= from && from <= _regions.length(),
         err_msg("from: %d should be valid and <= than %d",
                 from, _regions.length()));

  int curr = from;
  int first = -1;
  size_t num_so_far = 0;
  while (curr < _regions.length() && num_so_far < num) {
    HeapRegion* curr_hr = _regions.at(curr);
    if (curr_hr->is_empty()) {
      if (first == -1) {
        first = curr;
        num_so_far = 1;
      } else {
        num_so_far += 1;
      }
    } else {
      first = -1;
      num_so_far = 0;
    }
    curr += 1;
  }

  assert(num_so_far <= num, "post-condition");
  if (num_so_far == num) {
    // we found enough space for the humongous object
    assert(from <= first && first < _regions.length(), "post-condition");
    assert(first < curr && (curr - first) == (int) num, "post-condition");
    for (int i = first; i < first + (int) num; ++i) {
      assert(_regions.at(i)->is_empty(), "post-condition");
    }
    return first;
  } else {
    // we failed to find enough space for the humongous object
    return -1;
  }
}

int HeapRegionSeq::find_contiguous(size_t num) {
  assert(num > 1, "otherwise we should not be calling this");
  assert(0 <= _alloc_search_start && _alloc_search_start <= _regions.length(),
         err_msg("_alloc_search_start: %d should be valid and <= than %d",
                 _alloc_search_start, _regions.length()));

  int start = _alloc_search_start;
  int res = find_contiguous_from(start, num);
  if (res == -1 && start != 0) {
    // Try starting from the beginning. If _alloc_search_start was 0,
    // no point in doing this again.
    res = find_contiguous_from(0, num);
  }
  if (res != -1) {
    assert(0 <= res && res < _regions.length(),
           err_msg("res: %d should be valid", res));
    _alloc_search_start = res + (int) num;
    assert(0 < _alloc_search_start && _alloc_search_start <= _regions.length(),
           err_msg("_alloc_search_start: %d should be valid",
                   _alloc_search_start));
  }
  return res;
}

void HeapRegionSeq::iterate(HeapRegionClosure* blk) {
  iterate_from((HeapRegion*)NULL, blk);
}

// The first argument r is the heap region at which iteration begins.
// This operation runs fastest when r is NULL, or the heap region for
// which a HeapRegionClosure most recently returned true, or the
// heap region immediately to its right in the sequence.  In all
// other cases a linear search is required to find the index of r.

void HeapRegionSeq::iterate_from(HeapRegion* r, HeapRegionClosure* blk) {

  // :::: FIXME ::::
  // Static cache value is bad, especially when we start doing parallel
  // remembered set update. For now just don't cache anything (the
  // code in the def'd out blocks).

#if 0
  static int cached_j = 0;
#endif
  int len = _regions.length();
  int j = 0;
  // Find the index of r.
  if (r != NULL) {
#if 0
    assert(cached_j >= 0, "Invariant.");
    if ((cached_j < len) && (r == _regions.at(cached_j))) {
      j = cached_j;
    } else if ((cached_j + 1 < len) && (r == _regions.at(cached_j + 1))) {
      j = cached_j + 1;
    } else {
      j = find(r);
#endif
      if (j < 0) {
        j = 0;
      }
#if 0
    }
#endif
  }
  int i;
  for (i = j; i < len; i += 1) {
    int res = blk->doHeapRegion(_regions.at(i));
    if (res) {
#if 0
      cached_j = i;
#endif
      blk->incomplete();
      return;
    }
  }
  for (i = 0; i < j; i += 1) {
    int res = blk->doHeapRegion(_regions.at(i));
    if (res) {
#if 0
      cached_j = i;
#endif
      blk->incomplete();
      return;
    }
  }
}

void HeapRegionSeq::iterate_from(int idx, HeapRegionClosure* blk) {
  int len = _regions.length();
  int i;
  for (i = idx; i < len; i++) {
    if (blk->doHeapRegion(_regions.at(i))) {
      blk->incomplete();
      return;
    }
  }
  for (i = 0; i < idx; i++) {
    if (blk->doHeapRegion(_regions.at(i))) {
      blk->incomplete();
      return;
    }
  }
}

MemRegion HeapRegionSeq::shrink_by(size_t shrink_bytes,
                                   size_t& num_regions_deleted) {
  // Reset this in case it's currently pointing into the regions that
  // we just removed.
  _alloc_search_start = 0;

  assert(shrink_bytes % os::vm_page_size() == 0, "unaligned");
  assert(shrink_bytes % HeapRegion::GrainBytes == 0, "unaligned");

  if (_regions.length() == 0) {
    num_regions_deleted = 0;
    return MemRegion();
  }
  int j = _regions.length() - 1;
  HeapWord* end = _regions.at(j)->end();
  HeapWord* last_start = end;
  while (j >= 0 && shrink_bytes > 0) {
    HeapRegion* cur = _regions.at(j);
    // We have to leave humongous regions where they are,
    // and work around them.
    if (cur->isHumongous()) {
      return MemRegion(last_start, end);
    }
    assert(cur == _regions.top(), "Should be top");
    if (!cur->is_empty()) break;
    shrink_bytes -= cur->capacity();
    num_regions_deleted++;
    _regions.pop();
    last_start = cur->bottom();
    // We need to delete these somehow, but can't currently do so here: if
    // we do, the ZF thread may still access the deleted region.  We'll
    // leave this here as a reminder that we have to do something about
    // this.
    // delete cur;
    j--;
  }
  return MemRegion(last_start, end);
}

class PrintHeapRegionClosure : public  HeapRegionClosure {
public:
  bool doHeapRegion(HeapRegion* r) {
    gclog_or_tty->print(PTR_FORMAT ":", r);
    r->print();
    return false;
  }
};

void HeapRegionSeq::print() {
  PrintHeapRegionClosure cl;
  iterate(&cl);
}

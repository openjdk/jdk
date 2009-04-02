/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_heapRegionSeq.cpp.incl"

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
  // that it should allocate its elem array(s) on the C heap.  The first
  // argument, however, is actually a comma expression (new-expr, 100).
  // The purpose of the new_expr is to inform the growable array that it
  // is *already* allocated on the C heap: it uses the placement syntax to
  // keep it from actually doing any allocation.
  _regions((ResourceObj::operator new (sizeof(GrowableArray<HeapRegion*>),
                                       (void*)&_regions,
                                       ResourceObj::C_HEAP),
            (int)max_size),
           true),
  _next_rr_candidate(0),
  _seq_bottom(NULL)
{}

// Private methods.

HeapWord*
HeapRegionSeq::alloc_obj_from_region_index(int ind, size_t word_size) {
  assert(G1CollectedHeap::isHumongous(word_size),
         "Allocation size should be humongous");
  int cur = ind;
  int first = cur;
  size_t sumSizes = 0;
  while (cur < _regions.length() && sumSizes < word_size) {
    // Loop invariant:
    //  For all i in [first, cur):
    //       _regions.at(i)->is_empty()
    //    && _regions.at(i) is contiguous with its predecessor, if any
    //  && sumSizes is the sum of the sizes of the regions in the interval
    //       [first, cur)
    HeapRegion* curhr = _regions.at(cur);
    if (curhr->is_empty()
        && (first == cur
            || (_regions.at(cur-1)->end() ==
                curhr->bottom()))) {
      sumSizes += curhr->capacity() / HeapWordSize;
    } else {
      first = cur + 1;
      sumSizes = 0;
    }
    cur++;
  }
  if (sumSizes >= word_size) {
    _alloc_search_start = cur;
    // Mark the allocated regions as allocated.
    bool zf = G1CollectedHeap::heap()->allocs_are_zero_filled();
    HeapRegion* first_hr = _regions.at(first);
    for (int i = first; i < cur; i++) {
      HeapRegion* hr = _regions.at(i);
      if (zf)
        hr->ensure_zero_filled();
      {
        MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
        hr->set_zero_fill_allocated();
      }
      size_t sz = hr->capacity() / HeapWordSize;
      HeapWord* tmp = hr->allocate(sz);
      assert(tmp != NULL, "Humongous allocation failure");
      MemRegion mr = MemRegion(tmp, sz);
      CollectedHeap::fill_with_object(mr);
      hr->declare_filled_region_to_BOT(mr);
      if (i == first) {
        first_hr->set_startsHumongous();
      } else {
        assert(i > first, "sanity");
        hr->set_continuesHumongous(first_hr);
      }
    }
    HeapWord* first_hr_bot = first_hr->bottom();
    HeapWord* obj_end = first_hr_bot + word_size;
    first_hr->set_top(obj_end);
    return first_hr_bot;
  } else {
    // If we started from the beginning, we want to know why we can't alloc.
    return NULL;
  }
}

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

HeapWord* HeapRegionSeq::obj_allocate(size_t word_size) {
  int cur = _alloc_search_start;
  // Make sure "cur" is a valid index.
  assert(cur >= 0, "Invariant.");
  HeapWord* res = alloc_obj_from_region_index(cur, word_size);
  if (res == NULL)
    res = alloc_obj_from_region_index(0, word_size);
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
    cur->reset_zero_fill();
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

/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/concurrentG1Refine.hpp"
#include "gc_implementation/g1/concurrentG1RefineThread.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1RemSet.inline.hpp"
#include "gc_implementation/g1/g1RemSetSummary.hpp"
#include "gc_implementation/g1/heapRegionRemSet.hpp"
#include "runtime/thread.inline.hpp"

class GetRSThreadVTimeClosure : public ThreadClosure {
private:
  G1RemSetSummary* _summary;
  uint _counter;

public:
  GetRSThreadVTimeClosure(G1RemSetSummary * summary) : ThreadClosure(), _summary(summary), _counter(0) {
    assert(_summary != NULL, "just checking");
  }

  virtual void do_thread(Thread* t) {
    ConcurrentG1RefineThread* crt = (ConcurrentG1RefineThread*) t;
    _summary->set_rs_thread_vtime(_counter, crt->vtime_accum());
    _counter++;
  }
};

void G1RemSetSummary::update() {
  _num_refined_cards = remset()->conc_refine_cards();
  DirtyCardQueueSet& dcqs = JavaThread::dirty_card_queue_set();
  _num_processed_buf_mutator = dcqs.processed_buffers_mut();
  _num_processed_buf_rs_threads = dcqs.processed_buffers_rs_thread();

  _num_coarsenings = HeapRegionRemSet::n_coarsenings();

  ConcurrentG1Refine * cg1r = G1CollectedHeap::heap()->concurrent_g1_refine();
  if (_rs_threads_vtimes != NULL) {
    GetRSThreadVTimeClosure p(this);
    cg1r->worker_threads_do(&p);
  }
  set_sampling_thread_vtime(cg1r->sampling_thread()->vtime_accum());
}

void G1RemSetSummary::set_rs_thread_vtime(uint thread, double value) {
  assert(_rs_threads_vtimes != NULL, "just checking");
  assert(thread < _num_vtimes, "just checking");
  _rs_threads_vtimes[thread] = value;
}

double G1RemSetSummary::rs_thread_vtime(uint thread) const {
  assert(_rs_threads_vtimes != NULL, "just checking");
  assert(thread < _num_vtimes, "just checking");
  return _rs_threads_vtimes[thread];
}

void G1RemSetSummary::initialize(G1RemSet* remset) {
  assert(_rs_threads_vtimes == NULL, "just checking");
  assert(remset != NULL, "just checking");

  _remset = remset;
  _num_vtimes = ConcurrentG1Refine::thread_num();
  _rs_threads_vtimes = NEW_C_HEAP_ARRAY(double, _num_vtimes, mtGC);
  memset(_rs_threads_vtimes, 0, sizeof(double) * _num_vtimes);

  update();
}

void G1RemSetSummary::set(G1RemSetSummary* other) {
  assert(other != NULL, "just checking");
  assert(remset() == other->remset(), "just checking");
  assert(_num_vtimes == other->_num_vtimes, "just checking");

  _num_refined_cards = other->num_concurrent_refined_cards();

  _num_processed_buf_mutator = other->num_processed_buf_mutator();
  _num_processed_buf_rs_threads = other->num_processed_buf_rs_threads();

  _num_coarsenings = other->_num_coarsenings;

  memcpy(_rs_threads_vtimes, other->_rs_threads_vtimes, sizeof(double) * _num_vtimes);

  set_sampling_thread_vtime(other->sampling_thread_vtime());
}

void G1RemSetSummary::subtract_from(G1RemSetSummary* other) {
  assert(other != NULL, "just checking");
  assert(remset() == other->remset(), "just checking");
  assert(_num_vtimes == other->_num_vtimes, "just checking");

  _num_refined_cards = other->num_concurrent_refined_cards() - _num_refined_cards;

  _num_processed_buf_mutator = other->num_processed_buf_mutator() - _num_processed_buf_mutator;
  _num_processed_buf_rs_threads = other->num_processed_buf_rs_threads() - _num_processed_buf_rs_threads;

  _num_coarsenings = other->num_coarsenings() - _num_coarsenings;

  for (uint i = 0; i < _num_vtimes; i++) {
    set_rs_thread_vtime(i, other->rs_thread_vtime(i) - rs_thread_vtime(i));
  }

  _sampling_thread_vtime = other->sampling_thread_vtime() - _sampling_thread_vtime;
}

static double percent_of(size_t numerator, size_t denominator) {
  if (denominator != 0) {
    return (double)numerator / denominator * 100.0f;
  } else {
    return 0.0f;
  }
}

static size_t round_to_K(size_t value) {
  return value / K;
}

class RegionTypeCounter VALUE_OBJ_CLASS_SPEC {
private:
  const char* _name;

  size_t _rs_mem_size;
  size_t _cards_occupied;
  size_t _amount;

  size_t _code_root_mem_size;
  size_t _code_root_elems;

  double rs_mem_size_percent_of(size_t total) {
    return percent_of(_rs_mem_size, total);
  }

  double cards_occupied_percent_of(size_t total) {
    return percent_of(_cards_occupied, total);
  }

  double code_root_mem_size_percent_of(size_t total) {
    return percent_of(_code_root_mem_size, total);
  }

  double code_root_elems_percent_of(size_t total) {
    return percent_of(_code_root_elems, total);
  }

  size_t amount() const { return _amount; }

public:

  RegionTypeCounter(const char* name) : _name(name), _rs_mem_size(0), _cards_occupied(0),
    _amount(0), _code_root_mem_size(0), _code_root_elems(0) { }

  void add(size_t rs_mem_size, size_t cards_occupied, size_t code_root_mem_size,
    size_t code_root_elems) {
    _rs_mem_size += rs_mem_size;
    _cards_occupied += cards_occupied;
    _code_root_mem_size += code_root_mem_size;
    _code_root_elems += code_root_elems;
    _amount++;
  }

  size_t rs_mem_size() const { return _rs_mem_size; }
  size_t cards_occupied() const { return _cards_occupied; }

  size_t code_root_mem_size() const { return _code_root_mem_size; }
  size_t code_root_elems() const { return _code_root_elems; }

  void print_rs_mem_info_on(outputStream * out, size_t total) {
    out->print_cr("    "SIZE_FORMAT_W(8)"K (%5.1f%%) by "SIZE_FORMAT" %s regions",
        round_to_K(rs_mem_size()), rs_mem_size_percent_of(total), amount(), _name);
  }

  void print_cards_occupied_info_on(outputStream * out, size_t total) {
    out->print_cr("     "SIZE_FORMAT_W(8)" (%5.1f%%) entries by "SIZE_FORMAT" %s regions",
        cards_occupied(), cards_occupied_percent_of(total), amount(), _name);
  }

  void print_code_root_mem_info_on(outputStream * out, size_t total) {
    out->print_cr("    "SIZE_FORMAT_W(8)"K (%5.1f%%) by "SIZE_FORMAT" %s regions",
        round_to_K(code_root_mem_size()), code_root_mem_size_percent_of(total), amount(), _name);
  }

  void print_code_root_elems_info_on(outputStream * out, size_t total) {
    out->print_cr("     "SIZE_FORMAT_W(8)" (%5.1f%%) elements by "SIZE_FORMAT" %s regions",
        code_root_elems(), code_root_elems_percent_of(total), amount(), _name);
  }
};


class HRRSStatsIter: public HeapRegionClosure {
private:
  RegionTypeCounter _young;
  RegionTypeCounter _humonguous;
  RegionTypeCounter _free;
  RegionTypeCounter _old;
  RegionTypeCounter _all;

  size_t _max_rs_mem_sz;
  HeapRegion* _max_rs_mem_sz_region;

  size_t total_rs_mem_sz() const            { return _all.rs_mem_size(); }
  size_t total_cards_occupied() const       { return _all.cards_occupied(); }

  size_t max_rs_mem_sz() const              { return _max_rs_mem_sz; }
  HeapRegion* max_rs_mem_sz_region() const  { return _max_rs_mem_sz_region; }

  size_t _max_code_root_mem_sz;
  HeapRegion* _max_code_root_mem_sz_region;

  size_t total_code_root_mem_sz() const     { return _all.code_root_mem_size(); }
  size_t total_code_root_elems() const      { return _all.code_root_elems(); }

  size_t max_code_root_mem_sz() const       { return _max_code_root_mem_sz; }
  HeapRegion* max_code_root_mem_sz_region() const { return _max_code_root_mem_sz_region; }

public:
  HRRSStatsIter() : _all("All"), _young("Young"), _humonguous("Humonguous"),
    _free("Free"), _old("Old"), _max_code_root_mem_sz_region(NULL), _max_rs_mem_sz_region(NULL),
    _max_rs_mem_sz(0), _max_code_root_mem_sz(0)
  {}

  bool doHeapRegion(HeapRegion* r) {
    HeapRegionRemSet* hrrs = r->rem_set();

    // HeapRegionRemSet::mem_size() includes the
    // size of the strong code roots
    size_t rs_mem_sz = hrrs->mem_size();
    if (rs_mem_sz > _max_rs_mem_sz) {
      _max_rs_mem_sz = rs_mem_sz;
      _max_rs_mem_sz_region = r;
    }
    size_t occupied_cards = hrrs->occupied();
    size_t code_root_mem_sz = hrrs->strong_code_roots_mem_size();
    if (code_root_mem_sz > max_code_root_mem_sz()) {
      _max_code_root_mem_sz = code_root_mem_sz;
      _max_code_root_mem_sz_region = r;
    }
    size_t code_root_elems = hrrs->strong_code_roots_list_length();

    RegionTypeCounter* current = NULL;
    if (r->is_free()) {
      current = &_free;
    } else if (r->is_young()) {
      current = &_young;
    } else if (r->isHumongous()) {
      current = &_humonguous;
    } else if (r->is_old()) {
      current = &_old;
    } else {
      ShouldNotReachHere();
    }
    current->add(rs_mem_sz, occupied_cards, code_root_mem_sz, code_root_elems);
    _all.add(rs_mem_sz, occupied_cards, code_root_mem_sz, code_root_elems);

    return false;
  }

  void print_summary_on(outputStream* out) {
    RegionTypeCounter* counters[] = { &_young, &_humonguous, &_free, &_old, NULL };

    out->print_cr("\n Current rem set statistics");
    out->print_cr("  Total per region rem sets sizes = "SIZE_FORMAT"K."
                  " Max = "SIZE_FORMAT"K.",
                  round_to_K(total_rs_mem_sz()), round_to_K(max_rs_mem_sz()));
    for (RegionTypeCounter** current = &counters[0]; *current != NULL; current++) {
      (*current)->print_rs_mem_info_on(out, total_rs_mem_sz());
    }

    out->print_cr("   Static structures = "SIZE_FORMAT"K,"
                  " free_lists = "SIZE_FORMAT"K.",
                  round_to_K(HeapRegionRemSet::static_mem_size()),
                  round_to_K(HeapRegionRemSet::fl_mem_size()));

    out->print_cr("    "SIZE_FORMAT" occupied cards represented.",
                  total_cards_occupied());
    for (RegionTypeCounter** current = &counters[0]; *current != NULL; current++) {
      (*current)->print_cards_occupied_info_on(out, total_cards_occupied());
    }

    // Largest sized rem set region statistics
    HeapRegionRemSet* rem_set = max_rs_mem_sz_region()->rem_set();
    out->print_cr("    Region with largest rem set = "HR_FORMAT", "
                  "size = "SIZE_FORMAT "K, occupied = "SIZE_FORMAT"K.",
                  HR_FORMAT_PARAMS(max_rs_mem_sz_region()),
                  round_to_K(rem_set->mem_size()),
                  round_to_K(rem_set->occupied()));

    // Strong code root statistics
    HeapRegionRemSet* max_code_root_rem_set = max_code_root_mem_sz_region()->rem_set();
    out->print_cr("  Total heap region code root sets sizes = "SIZE_FORMAT"K."
                  "  Max = "SIZE_FORMAT"K.",
                  round_to_K(total_code_root_mem_sz()),
                  round_to_K(max_code_root_rem_set->strong_code_roots_mem_size()));
    for (RegionTypeCounter** current = &counters[0]; *current != NULL; current++) {
      (*current)->print_code_root_mem_info_on(out, total_code_root_mem_sz());
    }

    out->print_cr("    "SIZE_FORMAT" code roots represented.",
                  total_code_root_elems());
    for (RegionTypeCounter** current = &counters[0]; *current != NULL; current++) {
      (*current)->print_code_root_elems_info_on(out, total_code_root_elems());
    }

    out->print_cr("    Region with largest amount of code roots = "HR_FORMAT", "
                  "size = "SIZE_FORMAT "K, num_elems = "SIZE_FORMAT".",
                  HR_FORMAT_PARAMS(max_code_root_mem_sz_region()),
                  round_to_K(max_code_root_rem_set->strong_code_roots_mem_size()),
                  round_to_K(max_code_root_rem_set->strong_code_roots_list_length()));
  }
};

void G1RemSetSummary::print_on(outputStream* out) {
  out->print_cr("\n Recent concurrent refinement statistics");
  out->print_cr("  Processed "SIZE_FORMAT" cards",
                num_concurrent_refined_cards());
  out->print_cr("  Of "SIZE_FORMAT" completed buffers:", num_processed_buf_total());
  out->print_cr("     "SIZE_FORMAT_W(8)" (%5.1f%%) by concurrent RS threads.",
                num_processed_buf_total(),
                percent_of(num_processed_buf_rs_threads(), num_processed_buf_total()));
  out->print_cr("     "SIZE_FORMAT_W(8)" (%5.1f%%) by mutator threads.",
                num_processed_buf_mutator(),
                percent_of(num_processed_buf_mutator(), num_processed_buf_total()));
  out->print_cr("  Did "SIZE_FORMAT" coarsenings.", num_coarsenings());
  out->print_cr("  Concurrent RS threads times (s)");
  out->print("     ");
  for (uint i = 0; i < _num_vtimes; i++) {
    out->print("    %5.2f", rs_thread_vtime(i));
  }
  out->cr();
  out->print_cr("  Concurrent sampling threads times (s)");
  out->print_cr("         %5.2f", sampling_thread_vtime());

  HRRSStatsIter blk;
  G1CollectedHeap::heap()->heap_region_iterate(&blk);
  blk.print_summary_on(out);
}

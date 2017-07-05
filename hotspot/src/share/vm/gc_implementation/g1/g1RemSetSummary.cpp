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

void G1RemSetSummary::initialize(G1RemSet* remset, uint num_workers) {
  assert(_rs_threads_vtimes == NULL, "just checking");
  assert(remset != NULL, "just checking");

  _remset = remset;
  _num_vtimes = num_workers;
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

class HRRSStatsIter: public HeapRegionClosure {
  size_t _occupied;

  size_t _total_rs_mem_sz;
  size_t _max_rs_mem_sz;
  HeapRegion* _max_rs_mem_sz_region;

  size_t _total_code_root_mem_sz;
  size_t _max_code_root_mem_sz;
  HeapRegion* _max_code_root_mem_sz_region;
public:
  HRRSStatsIter() :
    _occupied(0),
    _total_rs_mem_sz(0),
    _max_rs_mem_sz(0),
    _max_rs_mem_sz_region(NULL),
    _total_code_root_mem_sz(0),
    _max_code_root_mem_sz(0),
    _max_code_root_mem_sz_region(NULL)
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
    _total_rs_mem_sz += rs_mem_sz;

    size_t code_root_mem_sz = hrrs->strong_code_roots_mem_size();
    if (code_root_mem_sz > _max_code_root_mem_sz) {
      _max_code_root_mem_sz = code_root_mem_sz;
      _max_code_root_mem_sz_region = r;
    }
    _total_code_root_mem_sz += code_root_mem_sz;

    size_t occ = hrrs->occupied();
    _occupied += occ;
    return false;
  }
  size_t total_rs_mem_sz() { return _total_rs_mem_sz; }
  size_t max_rs_mem_sz() { return _max_rs_mem_sz; }
  HeapRegion* max_rs_mem_sz_region() { return _max_rs_mem_sz_region; }
  size_t total_code_root_mem_sz() { return _total_code_root_mem_sz; }
  size_t max_code_root_mem_sz() { return _max_code_root_mem_sz; }
  HeapRegion* max_code_root_mem_sz_region() { return _max_code_root_mem_sz_region; }
  size_t occupied() { return _occupied; }
};

double calc_percentage(size_t numerator, size_t denominator) {
  if (denominator != 0) {
    return (double)numerator / denominator * 100.0;
  } else {
    return 0.0f;
  }
}

void G1RemSetSummary::print_on(outputStream* out) {
  out->print_cr("\n Concurrent RS processed "SIZE_FORMAT" cards",
                num_concurrent_refined_cards());
  out->print_cr("  Of %d completed buffers:", num_processed_buf_total());
  out->print_cr("     %8d (%5.1f%%) by concurrent RS threads.",
                num_processed_buf_total(),
                calc_percentage(num_processed_buf_rs_threads(), num_processed_buf_total()));
  out->print_cr("     %8d (%5.1f%%) by mutator threads.",
                num_processed_buf_mutator(),
                calc_percentage(num_processed_buf_mutator(), num_processed_buf_total()));
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
  // RemSet stats
  out->print_cr("  Total heap region rem set sizes = "SIZE_FORMAT"K."
                "  Max = "SIZE_FORMAT"K.",
                blk.total_rs_mem_sz()/K, blk.max_rs_mem_sz()/K);
  out->print_cr("  Static structures = "SIZE_FORMAT"K,"
                " free_lists = "SIZE_FORMAT"K.",
                HeapRegionRemSet::static_mem_size() / K,
                HeapRegionRemSet::fl_mem_size() / K);
  out->print_cr("    "SIZE_FORMAT" occupied cards represented.",
                blk.occupied());
  HeapRegion* max_rs_mem_sz_region = blk.max_rs_mem_sz_region();
  HeapRegionRemSet* max_rs_rem_set = max_rs_mem_sz_region->rem_set();
  out->print_cr("    Max size region = "HR_FORMAT", "
                "size = "SIZE_FORMAT "K, occupied = "SIZE_FORMAT"K.",
                HR_FORMAT_PARAMS(max_rs_mem_sz_region),
                (max_rs_rem_set->mem_size() + K - 1)/K,
                (max_rs_rem_set->occupied() + K - 1)/K);
  out->print_cr("    Did %d coarsenings.", num_coarsenings());
  // Strong code root stats
  out->print_cr("  Total heap region code-root set sizes = "SIZE_FORMAT"K."
                "  Max = "SIZE_FORMAT"K.",
                blk.total_code_root_mem_sz()/K, blk.max_code_root_mem_sz()/K);
  HeapRegion* max_code_root_mem_sz_region = blk.max_code_root_mem_sz_region();
  HeapRegionRemSet* max_code_root_rem_set = max_code_root_mem_sz_region->rem_set();
  out->print_cr("    Max size region = "HR_FORMAT", "
                "size = "SIZE_FORMAT "K, num_elems = "SIZE_FORMAT".",
                HR_FORMAT_PARAMS(max_code_root_mem_sz_region),
                (max_code_root_rem_set->strong_code_roots_mem_size() + K - 1)/K,
                (max_code_root_rem_set->strong_code_roots_list_length()));
}

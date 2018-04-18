/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1RemSetSummary.hpp"
#include "gc/g1/g1YoungRemSetSamplingThread.hpp"
#include "gc/g1/heapRegion.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "memory/allocation.inline.hpp"
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
    G1ConcurrentRefineThread* crt = (G1ConcurrentRefineThread*) t;
    _summary->set_rs_thread_vtime(_counter, crt->vtime_accum());
    _counter++;
  }
};

void G1RemSetSummary::update() {
  _num_conc_refined_cards = _rem_set->num_conc_refined_cards();
  DirtyCardQueueSet& dcqs = G1BarrierSet::dirty_card_queue_set();
  _num_processed_buf_mutator = dcqs.processed_buffers_mut();
  _num_processed_buf_rs_threads = dcqs.processed_buffers_rs_thread();

  _num_coarsenings = HeapRegionRemSet::n_coarsenings();

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1ConcurrentRefine* cg1r = g1h->concurrent_refine();
  if (_rs_threads_vtimes != NULL) {
    GetRSThreadVTimeClosure p(this);
    cg1r->threads_do(&p);
  }
  set_sampling_thread_vtime(g1h->sampling_thread()->vtime_accum());
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

G1RemSetSummary::G1RemSetSummary() :
  _rem_set(NULL),
  _num_conc_refined_cards(0),
  _num_processed_buf_mutator(0),
  _num_processed_buf_rs_threads(0),
  _num_coarsenings(0),
  _num_vtimes(G1ConcurrentRefine::max_num_threads()),
  _rs_threads_vtimes(NEW_C_HEAP_ARRAY(double, _num_vtimes, mtGC)),
  _sampling_thread_vtime(0.0f) {

  memset(_rs_threads_vtimes, 0, sizeof(double) * _num_vtimes);
}

G1RemSetSummary::G1RemSetSummary(G1RemSet* rem_set) :
  _rem_set(rem_set),
  _num_conc_refined_cards(0),
  _num_processed_buf_mutator(0),
  _num_processed_buf_rs_threads(0),
  _num_coarsenings(0),
  _num_vtimes(G1ConcurrentRefine::max_num_threads()),
  _rs_threads_vtimes(NEW_C_HEAP_ARRAY(double, _num_vtimes, mtGC)),
  _sampling_thread_vtime(0.0f) {
  update();
}

G1RemSetSummary::~G1RemSetSummary() {
  if (_rs_threads_vtimes) {
    FREE_C_HEAP_ARRAY(double, _rs_threads_vtimes);
  }
}

void G1RemSetSummary::set(G1RemSetSummary* other) {
  assert(other != NULL, "just checking");
  assert(_num_vtimes == other->_num_vtimes, "just checking");

  _num_conc_refined_cards = other->num_conc_refined_cards();

  _num_processed_buf_mutator = other->num_processed_buf_mutator();
  _num_processed_buf_rs_threads = other->num_processed_buf_rs_threads();

  _num_coarsenings = other->_num_coarsenings;

  memcpy(_rs_threads_vtimes, other->_rs_threads_vtimes, sizeof(double) * _num_vtimes);

  set_sampling_thread_vtime(other->sampling_thread_vtime());
}

void G1RemSetSummary::subtract_from(G1RemSetSummary* other) {
  assert(other != NULL, "just checking");
  assert(_num_vtimes == other->_num_vtimes, "just checking");

  _num_conc_refined_cards = other->num_conc_refined_cards() - _num_conc_refined_cards;

  _num_processed_buf_mutator = other->num_processed_buf_mutator() - _num_processed_buf_mutator;
  _num_processed_buf_rs_threads = other->num_processed_buf_rs_threads() - _num_processed_buf_rs_threads;

  _num_coarsenings = other->num_coarsenings() - _num_coarsenings;

  for (uint i = 0; i < _num_vtimes; i++) {
    set_rs_thread_vtime(i, other->rs_thread_vtime(i) - rs_thread_vtime(i));
  }

  _sampling_thread_vtime = other->sampling_thread_vtime() - _sampling_thread_vtime;
}

class RegionTypeCounter {
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
    out->print_cr("    " SIZE_FORMAT_W(8) "%s (%5.1f%%) by " SIZE_FORMAT " %s regions",
        byte_size_in_proper_unit(rs_mem_size()),
        proper_unit_for_byte_size(rs_mem_size()),
        rs_mem_size_percent_of(total), amount(), _name);
  }

  void print_cards_occupied_info_on(outputStream * out, size_t total) {
    out->print_cr("     " SIZE_FORMAT_W(8) " (%5.1f%%) entries by " SIZE_FORMAT " %s regions",
        cards_occupied(), cards_occupied_percent_of(total), amount(), _name);
  }

  void print_code_root_mem_info_on(outputStream * out, size_t total) {
    out->print_cr("    " SIZE_FORMAT_W(8) "%s (%5.1f%%) by " SIZE_FORMAT " %s regions",
        byte_size_in_proper_unit(code_root_mem_size()),
        proper_unit_for_byte_size(code_root_mem_size()),
        code_root_mem_size_percent_of(total), amount(), _name);
  }

  void print_code_root_elems_info_on(outputStream * out, size_t total) {
    out->print_cr("     " SIZE_FORMAT_W(8) " (%5.1f%%) elements by " SIZE_FORMAT " %s regions",
        code_root_elems(), code_root_elems_percent_of(total), amount(), _name);
  }
};


class HRRSStatsIter: public HeapRegionClosure {
private:
  RegionTypeCounter _young;
  RegionTypeCounter _humongous;
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
  HRRSStatsIter() : _all("All"), _young("Young"), _humongous("Humongous"),
    _free("Free"), _old("Old"), _max_code_root_mem_sz_region(NULL), _max_rs_mem_sz_region(NULL),
    _max_rs_mem_sz(0), _max_code_root_mem_sz(0)
  {}

  bool do_heap_region(HeapRegion* r) {
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
    } else if (r->is_humongous()) {
      current = &_humongous;
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
    RegionTypeCounter* counters[] = { &_young, &_humongous, &_free, &_old, NULL };

    out->print_cr(" Current rem set statistics");
    out->print_cr("  Total per region rem sets sizes = " SIZE_FORMAT "%s."
                  " Max = " SIZE_FORMAT "%s.",
                  byte_size_in_proper_unit(total_rs_mem_sz()),
                  proper_unit_for_byte_size(total_rs_mem_sz()),
                  byte_size_in_proper_unit(max_rs_mem_sz()),
                  proper_unit_for_byte_size(max_rs_mem_sz()));
    for (RegionTypeCounter** current = &counters[0]; *current != NULL; current++) {
      (*current)->print_rs_mem_info_on(out, total_rs_mem_sz());
    }

    out->print_cr("   Static structures = " SIZE_FORMAT "%s,"
                  " free_lists = " SIZE_FORMAT "%s.",
                  byte_size_in_proper_unit(HeapRegionRemSet::static_mem_size()),
                  proper_unit_for_byte_size(HeapRegionRemSet::static_mem_size()),
                  byte_size_in_proper_unit(HeapRegionRemSet::fl_mem_size()),
                  proper_unit_for_byte_size(HeapRegionRemSet::fl_mem_size()));

    out->print_cr("    " SIZE_FORMAT " occupied cards represented.",
                  total_cards_occupied());
    for (RegionTypeCounter** current = &counters[0]; *current != NULL; current++) {
      (*current)->print_cards_occupied_info_on(out, total_cards_occupied());
    }

    // Largest sized rem set region statistics
    HeapRegionRemSet* rem_set = max_rs_mem_sz_region()->rem_set();
    out->print_cr("    Region with largest rem set = " HR_FORMAT ", "
                  "size = " SIZE_FORMAT "%s, occupied = " SIZE_FORMAT "%s.",
                  HR_FORMAT_PARAMS(max_rs_mem_sz_region()),
                  byte_size_in_proper_unit(rem_set->mem_size()),
                  proper_unit_for_byte_size(rem_set->mem_size()),
                  byte_size_in_proper_unit(rem_set->occupied()),
                  proper_unit_for_byte_size(rem_set->occupied()));
    // Strong code root statistics
    HeapRegionRemSet* max_code_root_rem_set = max_code_root_mem_sz_region()->rem_set();
    out->print_cr("  Total heap region code root sets sizes = " SIZE_FORMAT "%s."
                  "  Max = " SIZE_FORMAT "%s.",
                  byte_size_in_proper_unit(total_code_root_mem_sz()),
                  proper_unit_for_byte_size(total_code_root_mem_sz()),
                  byte_size_in_proper_unit(max_code_root_rem_set->strong_code_roots_mem_size()),
                  proper_unit_for_byte_size(max_code_root_rem_set->strong_code_roots_mem_size()));
    for (RegionTypeCounter** current = &counters[0]; *current != NULL; current++) {
      (*current)->print_code_root_mem_info_on(out, total_code_root_mem_sz());
    }

    out->print_cr("    " SIZE_FORMAT " code roots represented.",
                  total_code_root_elems());
    for (RegionTypeCounter** current = &counters[0]; *current != NULL; current++) {
      (*current)->print_code_root_elems_info_on(out, total_code_root_elems());
    }

    out->print_cr("    Region with largest amount of code roots = " HR_FORMAT ", "
                  "size = " SIZE_FORMAT "%s, num_elems = " SIZE_FORMAT ".",
                  HR_FORMAT_PARAMS(max_code_root_mem_sz_region()),
                  byte_size_in_proper_unit(max_code_root_rem_set->strong_code_roots_mem_size()),
                  proper_unit_for_byte_size(max_code_root_rem_set->strong_code_roots_mem_size()),
                  max_code_root_rem_set->strong_code_roots_list_length());
  }
};

void G1RemSetSummary::print_on(outputStream* out) {
  out->print_cr(" Recent concurrent refinement statistics");
  out->print_cr("  Processed " SIZE_FORMAT " cards concurrently", num_conc_refined_cards());
  out->print_cr("  Of " SIZE_FORMAT " completed buffers:", num_processed_buf_total());
  out->print_cr("     " SIZE_FORMAT_W(8) " (%5.1f%%) by concurrent RS threads.",
                num_processed_buf_total(),
                percent_of(num_processed_buf_rs_threads(), num_processed_buf_total()));
  out->print_cr("     " SIZE_FORMAT_W(8) " (%5.1f%%) by mutator threads.",
                num_processed_buf_mutator(),
                percent_of(num_processed_buf_mutator(), num_processed_buf_total()));
  out->print_cr("  Did " SIZE_FORMAT " coarsenings.", num_coarsenings());
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

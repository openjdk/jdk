/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "gc/g1/g1DirtyCardQueue.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1RemSetSummary.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "runtime/javaThread.hpp"

void G1RemSetSummary::update() {
  class CollectData : public ThreadClosure {
    G1RemSetSummary* _summary;
    uint _counter;
  public:
    CollectData(G1RemSetSummary * summary) : _summary(summary),  _counter(0) {}
    virtual void do_thread(Thread* t) {
      G1ConcurrentRefineThread* crt = static_cast<G1ConcurrentRefineThread*>(t);
      _summary->set_rs_thread_vtime(_counter, crt->vtime_accum());
      _counter++;
    }
  } collector(this);

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  g1h->concurrent_refine()->threads_do(&collector);
}

void G1RemSetSummary::set_rs_thread_vtime(uint thread, double value) {
  assert(_rs_threads_vtimes != nullptr, "just checking");
  assert(thread < _num_vtimes, "just checking");
  _rs_threads_vtimes[thread] = value;
}

double G1RemSetSummary::rs_thread_vtime(uint thread) const {
  assert(_rs_threads_vtimes != nullptr, "just checking");
  assert(thread < _num_vtimes, "just checking");
  return _rs_threads_vtimes[thread];
}

G1RemSetSummary::G1RemSetSummary(bool should_update) :
  _num_vtimes(G1ConcRefinementThreads),
  _rs_threads_vtimes(NEW_C_HEAP_ARRAY(double, _num_vtimes, mtGC)) {

  memset(_rs_threads_vtimes, 0, sizeof(double) * _num_vtimes);

  if (should_update) {
    update();
  }
}

G1RemSetSummary::~G1RemSetSummary() {
  FREE_C_HEAP_ARRAY(double, _rs_threads_vtimes);
}

void G1RemSetSummary::set(G1RemSetSummary* other) {
  assert(other != nullptr, "just checking");
  assert(_num_vtimes == other->_num_vtimes, "just checking");

  memcpy(_rs_threads_vtimes, other->_rs_threads_vtimes, sizeof(double) * _num_vtimes);
}

void G1RemSetSummary::subtract_from(G1RemSetSummary* other) {
  assert(other != nullptr, "just checking");
  assert(_num_vtimes == other->_num_vtimes, "just checking");

  for (uint i = 0; i < _num_vtimes; i++) {
    set_rs_thread_vtime(i, other->rs_thread_vtime(i) - rs_thread_vtime(i));
  }
}

class RegionTypeCounter {
private:
  const char* _name;

  size_t _rs_unused_mem_size;
  size_t _rs_mem_size;
  size_t _cards_occupied;
  size_t _amount;
  size_t _amount_tracked;

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
  size_t amount_tracked() const { return _amount_tracked; }

public:

  RegionTypeCounter(const char* name) : _name(name), _rs_unused_mem_size(0), _rs_mem_size(0), _cards_occupied(0),
    _amount(0), _amount_tracked(0), _code_root_mem_size(0), _code_root_elems(0) { }

  void add(size_t rs_unused_mem_size, size_t rs_mem_size, size_t cards_occupied,
           size_t code_root_mem_size, size_t code_root_elems, bool tracked) {
    _rs_unused_mem_size += rs_unused_mem_size;
    _rs_mem_size += rs_mem_size;
    _cards_occupied += cards_occupied;
    _code_root_mem_size += code_root_mem_size;
    _code_root_elems += code_root_elems;
    _amount++;
    _amount_tracked += tracked ? 1 : 0;
  }

  size_t rs_unused_mem_size() const { return _rs_unused_mem_size; }
  size_t rs_mem_size() const { return _rs_mem_size; }
  size_t cards_occupied() const { return _cards_occupied; }

  size_t code_root_mem_size() const { return _code_root_mem_size; }
  size_t code_root_elems() const { return _code_root_elems; }

  void print_rs_mem_info_on(outputStream * out, size_t total) {
    out->print_cr("    " SIZE_FORMAT_W(8) " (%5.1f%%) by " SIZE_FORMAT " "
                  "(" SIZE_FORMAT ") %s regions unused " SIZE_FORMAT,
                  rs_mem_size(), rs_mem_size_percent_of(total),
                  amount_tracked(), amount(),
                  _name, rs_unused_mem_size());
  }

  void print_cards_occupied_info_on(outputStream * out, size_t total) {
    out->print_cr("     " SIZE_FORMAT_W(8) " (%5.1f%%) entries by " SIZE_FORMAT " "
                  "(" SIZE_FORMAT ") %s regions",
                  cards_occupied(), cards_occupied_percent_of(total),
                  amount_tracked(), amount(), _name);
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
  G1HeapRegion* _max_rs_mem_sz_region;

  size_t total_rs_unused_mem_sz() const     { return _all.rs_unused_mem_size(); }
  size_t total_rs_mem_sz() const            { return _all.rs_mem_size(); }
  size_t total_cards_occupied() const       { return _all.cards_occupied(); }

  size_t max_rs_mem_sz() const              { return _max_rs_mem_sz; }
  G1HeapRegion* max_rs_mem_sz_region() const  { return _max_rs_mem_sz_region; }

  size_t _max_code_root_mem_sz;
  G1HeapRegion* _max_code_root_mem_sz_region;

  size_t total_code_root_mem_sz() const     { return _all.code_root_mem_size(); }
  size_t total_code_root_elems() const      { return _all.code_root_elems(); }

  size_t max_code_root_mem_sz() const       { return _max_code_root_mem_sz; }
  G1HeapRegion* max_code_root_mem_sz_region() const { return _max_code_root_mem_sz_region; }

public:
  HRRSStatsIter() : _young("Young"), _humongous("Humongous"),
    _free("Free"), _old("Old"), _all("All"),
    _max_rs_mem_sz(0), _max_rs_mem_sz_region(nullptr),
    _max_code_root_mem_sz(0), _max_code_root_mem_sz_region(nullptr)
  {}

  bool do_heap_region(G1HeapRegion* r) {
    HeapRegionRemSet* hrrs = r->rem_set();

    // HeapRegionRemSet::mem_size() includes the
    // size of the code roots
    size_t rs_unused_mem_sz = hrrs->unused_mem_size();
    size_t rs_mem_sz = hrrs->mem_size();
    if (rs_mem_sz > _max_rs_mem_sz) {
      _max_rs_mem_sz = rs_mem_sz;
      _max_rs_mem_sz_region = r;
    }
    size_t occupied_cards = hrrs->occupied();
    size_t code_root_mem_sz = hrrs->code_roots_mem_size();
    if (code_root_mem_sz > max_code_root_mem_sz()) {
      _max_code_root_mem_sz = code_root_mem_sz;
      _max_code_root_mem_sz_region = r;
    }
    size_t code_root_elems = hrrs->code_roots_list_length();

    RegionTypeCounter* current = nullptr;
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
    current->add(rs_unused_mem_sz, rs_mem_sz, occupied_cards,
                 code_root_mem_sz, code_root_elems, r->rem_set()->is_tracked());
    _all.add(rs_unused_mem_sz, rs_mem_sz, occupied_cards,
             code_root_mem_sz, code_root_elems, r->rem_set()->is_tracked());

    return false;
  }

  void print_summary_on(outputStream* out) {
    RegionTypeCounter* counters[] = { &_young, &_humongous, &_free, &_old, nullptr };

    out->print_cr(" Current rem set statistics");
    out->print_cr("  Total per region rem sets sizes = " SIZE_FORMAT
                  " Max = " SIZE_FORMAT " unused = " SIZE_FORMAT,
                  total_rs_mem_sz(),
                  max_rs_mem_sz(),
                  total_rs_unused_mem_sz());
    for (RegionTypeCounter** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_rs_mem_info_on(out, total_rs_mem_sz());
    }

    out->print_cr("    " SIZE_FORMAT " occupied cards represented.",
                  total_cards_occupied());
    for (RegionTypeCounter** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_cards_occupied_info_on(out, total_cards_occupied());
    }

    // Largest sized rem set region statistics
    HeapRegionRemSet* rem_set = max_rs_mem_sz_region()->rem_set();
    out->print_cr("    Region with largest rem set = " HR_FORMAT ", "
                  "size = " SIZE_FORMAT " occupied = " SIZE_FORMAT,
                  HR_FORMAT_PARAMS(max_rs_mem_sz_region()),
                  rem_set->mem_size(),
                  rem_set->occupied());

    HeapRegionRemSet::print_static_mem_size(out);
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    g1h->card_set_freelist_pool()->print_on(out);

    // Code root statistics
    HeapRegionRemSet* max_code_root_rem_set = max_code_root_mem_sz_region()->rem_set();
    out->print_cr("  Total heap region code root sets sizes = " SIZE_FORMAT "%s."
                  "  Max = " SIZE_FORMAT "%s.",
                  byte_size_in_proper_unit(total_code_root_mem_sz()),
                  proper_unit_for_byte_size(total_code_root_mem_sz()),
                  byte_size_in_proper_unit(max_code_root_rem_set->code_roots_mem_size()),
                  proper_unit_for_byte_size(max_code_root_rem_set->code_roots_mem_size()));
    for (RegionTypeCounter** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_code_root_mem_info_on(out, total_code_root_mem_sz());
    }

    out->print_cr("    " SIZE_FORMAT " code roots represented.",
                  total_code_root_elems());
    for (RegionTypeCounter** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_code_root_elems_info_on(out, total_code_root_elems());
    }

    out->print_cr("    Region with largest amount of code roots = " HR_FORMAT ", "
                  "size = " SIZE_FORMAT "%s, num_slots = " SIZE_FORMAT ".",
                  HR_FORMAT_PARAMS(max_code_root_mem_sz_region()),
                  byte_size_in_proper_unit(max_code_root_rem_set->code_roots_mem_size()),
                  proper_unit_for_byte_size(max_code_root_rem_set->code_roots_mem_size()),
                  max_code_root_rem_set->code_roots_list_length());
  }
};

void G1RemSetSummary::print_on(outputStream* out, bool show_thread_times) {
  if (show_thread_times) {
    out->print_cr(" Concurrent refinement threads times (s)");
    out->print("     ");
    for (uint i = 0; i < _num_vtimes; i++) {
      out->print("    %5.2f", rs_thread_vtime(i));
    }
    out->cr();
  }

  HRRSStatsIter blk;
  G1CollectedHeap::heap()->heap_region_iterate(&blk);
  blk.print_summary_on(out);
}

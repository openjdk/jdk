/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
      _summary->set_refine_thread_cpu_time(_counter, crt->cpu_time());
      _counter++;
    }
  } collector(this);

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  g1h->concurrent_refine()->threads_do(&collector);
}

void G1RemSetSummary::set_refine_thread_cpu_time(uint thread, jlong value) {
  assert(_refine_threads_cpu_times != nullptr, "just checking");
  assert(thread < _num_refine_threads, "just checking");
  _refine_threads_cpu_times[thread] = value;
}

jlong G1RemSetSummary::refine_thread_cpu_time(uint thread) const {
  assert(_refine_threads_cpu_times != nullptr, "just checking");
  assert(thread < _num_refine_threads, "just checking");
  return _refine_threads_cpu_times[thread];
}

G1RemSetSummary::G1RemSetSummary(bool should_update) :
  _num_refine_threads(G1ConcRefinementThreads),
  _refine_threads_cpu_times(NEW_C_HEAP_ARRAY(jlong, _num_refine_threads, mtGC)) {

  memset(_refine_threads_cpu_times, 0, sizeof(jlong) * _num_refine_threads);

  if (should_update) {
    update();
  }
}

G1RemSetSummary::~G1RemSetSummary() {
  FREE_C_HEAP_ARRAY(jlong, _refine_threads_cpu_times);
}

void G1RemSetSummary::set(G1RemSetSummary* other) {
  assert(other != nullptr, "just checking");
  assert(_num_refine_threads == other->_num_refine_threads, "just checking");

  memcpy(_refine_threads_cpu_times, other->_refine_threads_cpu_times, sizeof(jlong) * _num_refine_threads);
}

void G1RemSetSummary::subtract_from(G1RemSetSummary* other) {
  assert(other != nullptr, "just checking");
  assert(_num_refine_threads == other->_num_refine_threads, "just checking");

  for (uint i = 0; i < _num_refine_threads; i++) {
    set_refine_thread_cpu_time(i, other->refine_thread_cpu_time(i) - refine_thread_cpu_time(i));
  }
}

class G1PerRegionTypeRemSetCounters {
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

  G1PerRegionTypeRemSetCounters(const char* name) : _name(name), _rs_unused_mem_size(0), _rs_mem_size(0), _cards_occupied(0),
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
    out->print_cr("    %8zu (%5.1f%%) by %zu "
                  "(%zu) %s regions unused %zu",
                  rs_mem_size(), rs_mem_size_percent_of(total),
                  amount_tracked(), amount(),
                  _name, rs_unused_mem_size());
  }

  void print_cards_occupied_info_on(outputStream * out, size_t total) {
    out->print_cr("     %8zu (%5.1f%%) entries by %zu "
                  "(%zu) %s regions",
                  cards_occupied(), cards_occupied_percent_of(total),
                  amount_tracked(), amount(), _name);
  }

  void print_code_root_mem_info_on(outputStream * out, size_t total) {
    out->print_cr("    %8zu%s (%5.1f%%) by %zu %s regions",
        byte_size_in_proper_unit(code_root_mem_size()),
        proper_unit_for_byte_size(code_root_mem_size()),
        code_root_mem_size_percent_of(total), amount(), _name);
  }

  void print_code_root_elems_info_on(outputStream * out, size_t total) {
    out->print_cr("     %8zu (%5.1f%%) elements by %zu %s regions",
        code_root_elems(), code_root_elems_percent_of(total), amount(), _name);
  }
};


class G1HeapRegionStatsClosure: public G1HeapRegionClosure {
  G1PerRegionTypeRemSetCounters _young;
  G1PerRegionTypeRemSetCounters _humongous;
  G1PerRegionTypeRemSetCounters _free;
  G1PerRegionTypeRemSetCounters _old;
  G1PerRegionTypeRemSetCounters _all;

  size_t _max_rs_mem_sz;
  G1HeapRegion* _max_rs_mem_sz_region;

  size_t _max_code_root_mem_sz;
  G1HeapRegion* _max_code_root_mem_sz_region;

  size_t _max_group_cardset_mem_sz;
  G1CSetCandidateGroup* _max_cardset_mem_sz_group;

  size_t total_rs_unused_mem_sz() const     { return _all.rs_unused_mem_size(); }
  size_t total_rs_mem_sz() const            { return _all.rs_mem_size(); }
  size_t total_cards_occupied() const       { return _all.cards_occupied(); }

  size_t max_rs_mem_sz() const              { return _max_rs_mem_sz; }
  G1HeapRegion* max_rs_mem_sz_region() const  { return _max_rs_mem_sz_region; }

  size_t max_group_cardset_mem_sz() const                 { return _max_group_cardset_mem_sz; }
  G1CSetCandidateGroup* max_cardset_mem_sz_group() const  { return _max_cardset_mem_sz_group; }

  size_t total_code_root_mem_sz() const     { return _all.code_root_mem_size(); }
  size_t total_code_root_elems() const      { return _all.code_root_elems(); }

  size_t max_code_root_mem_sz() const       { return _max_code_root_mem_sz; }
  G1HeapRegion* max_code_root_mem_sz_region() const { return _max_code_root_mem_sz_region; }

public:
  G1HeapRegionStatsClosure() : _young("Young"), _humongous("Humongous"),
    _free("Free"), _old("Old"), _all("All"),
    _max_rs_mem_sz(0), _max_rs_mem_sz_region(nullptr),
    _max_code_root_mem_sz(0), _max_code_root_mem_sz_region(nullptr),
    _max_group_cardset_mem_sz(0), _max_cardset_mem_sz_group(nullptr)
  {}

  bool do_heap_region(G1HeapRegion* r) {
    G1HeapRegionRemSet* hrrs = r->rem_set();
    size_t rs_mem_sz = 0;
    size_t rs_unused_mem_sz = 0;
    size_t occupied_cards = 0;

    // Accumulate card set details for regions that are assigned to single region
    // groups. G1HeapRegionRemSet::mem_size() includes the size of the code roots
    if (hrrs->is_added_to_cset_group() && hrrs->cset_group()->length() == 1) {
      G1CardSet* card_set = hrrs->cset_group()->card_set();

      rs_mem_sz = hrrs->mem_size() + card_set->mem_size();
      rs_unused_mem_sz = card_set->unused_mem_size();
      occupied_cards = hrrs->occupied();

      if (rs_mem_sz > _max_rs_mem_sz) {
        _max_rs_mem_sz = rs_mem_sz;
        _max_rs_mem_sz_region = r;
      }
    }

    size_t code_root_mem_sz = hrrs->code_roots_mem_size();
    if (code_root_mem_sz > max_code_root_mem_sz()) {
      _max_code_root_mem_sz = code_root_mem_sz;
      _max_code_root_mem_sz_region = r;
    }
    size_t code_root_elems = hrrs->code_roots_list_length();

    G1PerRegionTypeRemSetCounters* current = nullptr;
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

  void do_cset_groups() {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    G1CSetCandidateGroup* young_only_cset_group = g1h->young_regions_cset_group();

    // If the group has only a single region, then stats were accumulated
    // during region iteration.
    if (young_only_cset_group->length() > 1) {
      G1CardSet* young_only_card_set = young_only_cset_group->card_set();
      size_t rs_mem_sz = young_only_card_set->mem_size();
      size_t rs_unused_mem_sz = young_only_card_set->unused_mem_size();
      size_t occupied_cards = young_only_card_set->occupied();

      _max_group_cardset_mem_sz = rs_mem_sz;
      _max_cardset_mem_sz_group = young_only_cset_group;

      // Only update cardset details
      _young.add(rs_unused_mem_sz, rs_mem_sz, occupied_cards, 0, 0, false);
      _all.add(rs_unused_mem_sz, rs_mem_sz, occupied_cards, 0, 0, false);
    }


    G1PerRegionTypeRemSetCounters* current = &_old;
    for (G1CSetCandidateGroup* group : g1h->policy()->candidates()->from_marking_groups()) {
      if (group->length() > 1) {
        G1CardSet* group_card_set = group->card_set();
        size_t rs_mem_sz = group_card_set->mem_size();
        size_t rs_unused_mem_sz = group_card_set->unused_mem_size();
        size_t occupied_cards = group_card_set->occupied();

        if (rs_mem_sz > _max_group_cardset_mem_sz) {
          _max_group_cardset_mem_sz = rs_mem_sz;
          _max_cardset_mem_sz_group = group;
        }

        // Only update cardset details
        _old.add(rs_unused_mem_sz, rs_mem_sz, occupied_cards, 0, 0, false);
        _all.add(rs_unused_mem_sz, rs_mem_sz, occupied_cards, 0, 0, false);
      }
    }
  }

  void print_summary_on(outputStream* out) {
    G1PerRegionTypeRemSetCounters* counters[] = { &_young, &_humongous, &_free, &_old, nullptr };

    out->print_cr(" Current rem set statistics");
    out->print_cr("  Total per region rem sets sizes = %zu"
                  " Max = %zu unused = %zu",
                  total_rs_mem_sz(),
                  max_rs_mem_sz(),
                  total_rs_unused_mem_sz());
    for (G1PerRegionTypeRemSetCounters** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_rs_mem_info_on(out, total_rs_mem_sz());
    }

    out->print_cr("    %zu occupied cards represented.",
                  total_cards_occupied());
    for (G1PerRegionTypeRemSetCounters** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_cards_occupied_info_on(out, total_cards_occupied());
    }

    // Largest sized single region rem set statistics
    if (max_rs_mem_sz_region() != nullptr) {
      G1HeapRegionRemSet* rem_set = max_rs_mem_sz_region()->rem_set();
      out->print_cr("    Region with largest rem set = " HR_FORMAT ", "
                    "size = %zu occupied = %zu",
                    HR_FORMAT_PARAMS(max_rs_mem_sz_region()),
                    rem_set->mem_size(),
                    rem_set->occupied());
    }

    if (max_cardset_mem_sz_group() != nullptr) {
      G1CSetCandidateGroup* cset_group = max_cardset_mem_sz_group();
      out->print_cr("    Collectionset Candidate Group with largest cardset = %u:(%u regions), "
                    "size = %zu occupied = %zu",
                    cset_group->group_id(), cset_group->length(),
                    cset_group->card_set()->mem_size(),
                    cset_group->card_set()->occupied());
    }

    G1HeapRegionRemSet::print_static_mem_size(out);
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    g1h->card_set_freelist_pool()->print_on(out);

    // Code root statistics
    G1HeapRegionRemSet* max_code_root_rem_set = max_code_root_mem_sz_region()->rem_set();
    out->print_cr("  Total heap region code root sets sizes = %zu%s."
                  "  Max = %zu%s.",
                  byte_size_in_proper_unit(total_code_root_mem_sz()),
                  proper_unit_for_byte_size(total_code_root_mem_sz()),
                  byte_size_in_proper_unit(max_code_root_rem_set->code_roots_mem_size()),
                  proper_unit_for_byte_size(max_code_root_rem_set->code_roots_mem_size()));
    for (G1PerRegionTypeRemSetCounters** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_code_root_mem_info_on(out, total_code_root_mem_sz());
    }

    out->print_cr("    %zu code roots represented.",
                  total_code_root_elems());
    for (G1PerRegionTypeRemSetCounters** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_code_root_elems_info_on(out, total_code_root_elems());
    }

    out->print_cr("    Region with largest amount of code roots = " HR_FORMAT ", "
                  "size = %zu%s, num_slots = %zu.",
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
    for (uint i = 0; i < _num_refine_threads; i++) {
      out->print("    %5.2f", (double)refine_thread_cpu_time(i) / NANOSECS_PER_SEC);
    }
    out->cr();
  }
  G1HeapRegionStatsClosure blk;
  G1CollectedHeap::heap()->heap_region_iterate(&blk);
  blk.do_cset_groups();
  blk.print_summary_on(out);
}

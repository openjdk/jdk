/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CardSetGroup.hpp"
#include "gc/g1/g1CardSetMemory.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "gc/g1/g1HeapRegion.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1HumongousCardSetGroups.inline.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "gc/g1/g1RemSetSummary.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "runtime/javaThread.hpp"

void G1RemSetSummary::update() {
  G1ConcurrentRefine* refine = G1CollectedHeap::heap()->concurrent_refine();

  class CollectWorkerData : public ThreadClosure {
    G1RemSetSummary* _summary;
    uint _counter;
  public:
    CollectWorkerData(G1RemSetSummary* summary) : _summary(summary),  _counter(0) {}
    virtual void do_thread(Thread* t) {
      G1ConcurrentRefineThread* crt = static_cast<G1ConcurrentRefineThread*>(t);
      _summary->set_worker_thread_cpu_time(_counter, crt->cpu_time());
      _counter++;
    }
  } collector(this);

  refine->worker_threads_do(&collector);

  class CollectControlData : public ThreadClosure {
    G1RemSetSummary* _summary;
  public:
    CollectControlData(G1RemSetSummary* summary) : _summary(summary) {}
    virtual void do_thread(Thread* t) {
      G1ConcurrentRefineThread* crt = static_cast<G1ConcurrentRefineThread*>(t);
      _summary->set_control_thread_cpu_time(crt->cpu_time());
    }
  } control(this);

  refine->control_thread_do(&control);
}

void G1RemSetSummary::set_worker_thread_cpu_time(uint thread, jlong value) {
  assert(_worker_threads_cpu_times != nullptr, "just checking");
  assert(thread < _num_worker_threads, "just checking");
  _worker_threads_cpu_times[thread] = value;
}

void G1RemSetSummary::set_control_thread_cpu_time(jlong value) {
  _control_thread_cpu_time = value;
}

jlong G1RemSetSummary::worker_thread_cpu_time(uint thread) const {
  assert(_worker_threads_cpu_times != nullptr, "just checking");
  assert(thread < _num_worker_threads, "just checking");
  return _worker_threads_cpu_times[thread];
}

jlong G1RemSetSummary::control_thread_cpu_time() const {
  return _control_thread_cpu_time;
}

G1RemSetSummary::G1RemSetSummary(bool should_update) :
  _num_worker_threads(G1ConcRefinementThreads),
  _worker_threads_cpu_times(NEW_C_HEAP_ARRAY(jlong, _num_worker_threads, mtGC)),
  _control_thread_cpu_time(0) {

  memset(_worker_threads_cpu_times, 0, sizeof(jlong) * _num_worker_threads);

  if (should_update) {
    update();
  }
}

G1RemSetSummary::~G1RemSetSummary() {
  FREE_C_HEAP_ARRAY(_worker_threads_cpu_times);
}

void G1RemSetSummary::set(G1RemSetSummary* other) {
  assert(other != nullptr, "just checking");
  assert(_num_worker_threads == other->_num_worker_threads, "just checking");

  memcpy(_worker_threads_cpu_times, other->_worker_threads_cpu_times, sizeof(jlong) * _num_worker_threads);
  _control_thread_cpu_time = other->_control_thread_cpu_time;
}

void G1RemSetSummary::subtract_from(G1RemSetSummary* other) {
  assert(other != nullptr, "just checking");
  assert(_num_worker_threads == other->_num_worker_threads, "just checking");

  for (uint i = 0; i < _num_worker_threads; i++) {
    set_worker_thread_cpu_time(i, other->worker_thread_cpu_time(i) - worker_thread_cpu_time(i));
  }
  _control_thread_cpu_time = other->_control_thread_cpu_time - _control_thread_cpu_time;
}

class G1PerRegionTypeRemSetCounters {
  const char* _name;

  size_t _card_set_unused_bytes;
  size_t _card_set_bytes;
  size_t _card_set_num_cards;

  size_t _num_regions;
  size_t _num_tracked_regions;

  size_t _hrrs_bytes;

  size_t _code_roots_bytes;
  size_t _code_roots_length;

  size_t num_regions() const { return _num_regions; }
  size_t num_tracked_regions() const { return _num_tracked_regions; }

public:
  G1PerRegionTypeRemSetCounters(const char* name) :
    _name(name),
    _card_set_unused_bytes(0),
    _card_set_bytes(0),
    _card_set_num_cards(0),
    _num_regions(0),
    _num_tracked_regions(0),
    _hrrs_bytes(0),
    _code_roots_bytes(0),
    _code_roots_length(0)
  { }

  void add_hrrs_stats(size_t hrrs_bytes, size_t code_roots_bytes, size_t code_roots_length, bool tracked) {
    _hrrs_bytes += hrrs_bytes;
    _code_roots_bytes += code_roots_bytes;
    _code_roots_length += code_roots_length;
    _num_regions++;
    _num_tracked_regions += tracked ? 1 : 0;
  }

  void add_card_set_stats(size_t card_set_unused_bytes,
                          size_t card_set_bytes,
                          size_t card_set_num_cards) {
    _card_set_unused_bytes += card_set_unused_bytes;
    _card_set_bytes += card_set_bytes;
    _card_set_num_cards += card_set_num_cards;
  }

  size_t card_set_unused_bytes() const { return _card_set_unused_bytes; }
  size_t card_set_bytes() const { return _card_set_bytes; }
  size_t cards_occupied() const { return _card_set_num_cards; }

  size_t hrrs_bytes() const { return _hrrs_bytes; }

  size_t code_roots_bytes() const { return _code_roots_bytes; }
  size_t code_roots_length() const { return _code_roots_length; }

  void print_card_set_memory_usage_on(outputStream * out, size_t total) {
    out->print_cr("    %8zu (%5.1f%%) by %zu (%zu) %s regions unused %zu",
                  card_set_bytes(), percent_of(card_set_bytes(), total),
                  num_tracked_regions(), num_regions(),
                  _name, card_set_unused_bytes());
  }

  void print_card_set_cards_occupied_on(outputStream * out, size_t total) {
    out->print_cr("     %8zu (%5.1f%%) entries by %zu (%zu) %s regions",
                  cards_occupied(), percent_of(cards_occupied(), total),
                  num_tracked_regions(), num_regions(), _name);
  }

  void print_hrrs_memory_usage_on(outputStream * out, size_t total_hrrs_bytes, size_t total_code_roots_bytes) {
    out->print_cr("    %8zu%s (%5.1f%%) (Code roots %8zu%s (%5.1f%%)) by %zu %s regions",
        byte_size_in_proper_unit(hrrs_bytes()),
        proper_unit_for_byte_size(hrrs_bytes()),
        percent_of(hrrs_bytes(), total_hrrs_bytes),
        byte_size_in_proper_unit(code_roots_bytes()),
        proper_unit_for_byte_size(code_roots_bytes()),
        percent_of(code_roots_bytes(), total_code_roots_bytes),
        num_regions(), _name);
  }

  void print_code_roots_length_on(outputStream * out, size_t total_code_roots_length) {
    out->print_cr("     %8zu (%5.1f%%) elements by %zu %s regions",
        code_roots_length(), percent_of(code_roots_length(), total_code_roots_length), num_regions(), _name);
  }
};

class G1HeapRegionStatsClosure: public G1HeapRegionClosure {
  G1PerRegionTypeRemSetCounters _young;
  G1PerRegionTypeRemSetCounters _humongous;
  G1PerRegionTypeRemSetCounters _free;
  G1PerRegionTypeRemSetCounters _old;
  G1PerRegionTypeRemSetCounters _all;

  G1HeapRegion* _max_hrrs_bytes_region;

  G1CardSetGroup* _max_card_set_bytes_group;

  size_t total_card_set_unused_bytes() const     { return _all.card_set_unused_bytes(); }
  size_t total_card_set_bytes() const            { return _all.card_set_bytes(); }
  size_t total_cards_occupied() const                  { return _all.cards_occupied(); }

  size_t max_card_set_bytes() const {
    G1CardSetGroup* gr = max_card_set_bytes_group();
    return gr != nullptr ? gr->card_set()->mem_size() : 0;
  }
  G1CardSetGroup* max_card_set_bytes_group() const { return _max_card_set_bytes_group; }

  size_t max_hrrs_bytes() const {
    G1HeapRegion* r = max_hrrs_bytes_region();
    return r != nullptr ? r->rem_set()->mem_size() : 0;
  }
  G1HeapRegion* max_hrrs_bytes_region() const { return _max_hrrs_bytes_region; }

  size_t max_code_roots_bytes() const {
    G1HeapRegion* r = max_hrrs_bytes_region();
    return r != nullptr ? r->rem_set()->code_roots_mem_size() : 0;
  }

  size_t total_hrrs_bytes() const             { return _all.hrrs_bytes(); }
  size_t total_code_roots_bytes() const       { return _all.code_roots_bytes(); }
  size_t total_code_roots_length() const      { return _all.code_roots_length(); }

public:
  G1HeapRegionStatsClosure() :
    _young("Young"),
    _humongous("Humongous"),
    _free("Free"),
    _old("Old"),
    _all("All"),
    _max_hrrs_bytes_region(nullptr),
    _max_card_set_bytes_group(nullptr)
  {
    do_card_set_groups();
  }

  bool do_heap_region(G1HeapRegion* r) {
    G1HeapRegionRemSet* hrrs = r->rem_set();

    size_t hrrs_bytes = hrrs->mem_size();
    if (hrrs_bytes > max_hrrs_bytes()) {
      _max_hrrs_bytes_region = r;
    }

    size_t code_roots_bytes = hrrs->code_roots_mem_size();
    size_t code_roots_length = hrrs->code_roots_length();

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
    current->add_hrrs_stats(hrrs_bytes, code_roots_bytes, code_roots_length, r->rem_set()->is_tracked());
    _all.add_hrrs_stats(hrrs_bytes, code_roots_bytes, code_roots_length, r->rem_set()->is_tracked());

    return false;
  }

  void add_group_stats(G1CardSetGroup* gr, G1PerRegionTypeRemSetCounters* gen_counter) {
    G1CardSet* card_set = gr->card_set();
    size_t card_set_bytes = card_set->mem_size();
    size_t card_set_unused_bytes = card_set->unused_mem_size();
    size_t card_set_num_cards = card_set->occupied();

    if (card_set_bytes > max_card_set_bytes()) {
      _max_card_set_bytes_group = gr;
    }

    gen_counter->add_card_set_stats(card_set_unused_bytes,
                                    card_set_bytes,
                                    card_set_num_cards);
    _all.add_card_set_stats(card_set_unused_bytes,
                            card_set_bytes,
                            card_set_num_cards);
  }

  void do_card_set_groups() {
    G1CollectedHeap* g1h = G1CollectedHeap::heap();

    add_group_stats(g1h->collection_set()->young_regions_card_set_group(), &_young);

    G1CollectionSetCandidates* candidates = g1h->collection_set_candidates();
    for (G1CardSetGroup* gr : candidates->from_marking_groups()) {
      add_group_stats(gr, &_old);
    }
    for (G1CardSetGroup* gr : candidates->retained_groups()) {
      add_group_stats(gr, &_old);
    }

    g1h->humongous_card_set_groups()->iterate([&] (G1CardSetGroup* gr) {
                                                add_group_stats(gr, &_humongous);
                                              });
  }

  void print_summary_on(outputStream* out) {
    G1PerRegionTypeRemSetCounters* counters[] = { &_young, &_humongous, &_free, &_old, nullptr };

    out->print_cr(" Current heap region remembered set statistics");
    out->print_cr("  Total card set memory usage = %zu unused = %zu Max individual = %zu (%u)",
                  total_card_set_bytes(),
                  total_card_set_unused_bytes(),
                  max_card_set_bytes(),
                  max_card_set_bytes_group() != nullptr ? max_card_set_bytes_group()->group_id() : G1CardSetGroup::NoGroupId
                  );
    for (G1PerRegionTypeRemSetCounters** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_card_set_memory_usage_on(out, total_card_set_bytes());
    }

    out->print_cr("    %zu occupied cards.",
                  total_cards_occupied());
    for (G1PerRegionTypeRemSetCounters** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_card_set_cards_occupied_on(out, total_cards_occupied());
    }

    // Largest single region HRRS statistics
    if (max_hrrs_bytes_region() != nullptr) {
      G1HeapRegionRemSet* rem_set = max_hrrs_bytes_region()->rem_set();
      out->print_cr("    Region with largest memory usage = " HR_FORMAT ", "
                    "size = %zu code roots length = %zu code root size = %zu",
                    HR_FORMAT_PARAMS(max_hrrs_bytes_region()),
                    rem_set->mem_size(),
                    rem_set->code_roots_length(),
                    rem_set->code_roots_mem_size());
    }

    if (max_card_set_bytes_group() != nullptr) {
      G1CardSetGroup* gr = max_card_set_bytes_group();
      out->print_cr("    Card Set Group with largest card set = %u:(%u regions), "
                    "size = %zu occupied = %zu",
                    gr->group_id(), gr->num_regions(),
                    max_card_set_bytes(),
                    gr->card_set()->occupied());
    }

    G1HeapRegionRemSet::print_static_mem_size(out);
    G1CollectedHeap* g1h = G1CollectedHeap::heap();
    g1h->card_set_freelist_pool()->print_on(out);

    // Overall root statistics
    out->print_cr("  Total heap region rem set sizes = %zu%s."
                  "  Max = %zu%s (Code roots %zu%s Max = %zu%s).",
                  byte_size_in_proper_unit(total_hrrs_bytes()),
                  proper_unit_for_byte_size(total_hrrs_bytes()),
                  byte_size_in_proper_unit(max_hrrs_bytes()),
                  proper_unit_for_byte_size(max_hrrs_bytes()),
                  byte_size_in_proper_unit(total_code_roots_bytes()),
                  proper_unit_for_byte_size(total_code_roots_bytes()),
                  byte_size_in_proper_unit(max_code_roots_bytes()),
                  proper_unit_for_byte_size(max_code_roots_bytes()));

    for (G1PerRegionTypeRemSetCounters** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_hrrs_memory_usage_on(out, total_hrrs_bytes(), total_code_roots_bytes());
    }

    out->print_cr("    %zu code roots represented.",
                  total_code_roots_length());
    for (G1PerRegionTypeRemSetCounters** current = &counters[0]; *current != nullptr; current++) {
      (*current)->print_code_roots_length_on(out, total_code_roots_length());
    }
  }
};

void G1RemSetSummary::print_on(outputStream* out, bool show_thread_times) {
  if (show_thread_times) {
    out->print_cr(" Concurrent refinement threads times (s)");
    out->print_cr(" Control %5.2f Workers", (double)control_thread_cpu_time() / NANOSECS_PER_SEC);
    out->print("     ");
    for (uint i = 0; i < _num_worker_threads; i++) {
      out->print("    %5.2f", (double)worker_thread_cpu_time(i) / NANOSECS_PER_SEC);
    }
    out->cr();
  }
  G1HeapRegionStatsClosure blk;
  G1CollectedHeap::heap()->heap_region_iterate(&blk);
  blk.print_summary_on(out);
}

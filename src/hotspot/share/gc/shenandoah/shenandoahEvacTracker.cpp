/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shenandoah/shenandoahAgeCensus.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahEvacTracker.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "runtime/thread.hpp"

ShenandoahEvacuationStats::ShenandoahEvacuationStats()
  : _evacuations_completed(0), _bytes_completed(0),
    _evacuations_attempted(0), _bytes_attempted(0),
    _use_age_table(ShenandoahGenerationalCensusAtEvac || !ShenandoahGenerationalAdaptiveTenuring),
    _age_table(nullptr) {
  if (_use_age_table) {
    _age_table = new AgeTable(false);
  }
}

AgeTable* ShenandoahEvacuationStats::age_table() const {
  assert(_use_age_table, "Don't call");
  return _age_table;
}

void ShenandoahEvacuationStats::begin_evacuation(size_t bytes) {
  ++_evacuations_attempted;
  _bytes_attempted += bytes;
}

void ShenandoahEvacuationStats::end_evacuation(size_t bytes) {
  ++_evacuations_completed;
  _bytes_completed += bytes;
}

void ShenandoahEvacuationStats::record_age(size_t bytes, uint age) {
  assert(_use_age_table, "Don't call!");
  if (age <= markWord::max_age) { // Filter age sentinel.
    _age_table->add(age, bytes >> LogBytesPerWord);
  }
}

void ShenandoahEvacuationStats::accumulate(const ShenandoahEvacuationStats* other) {
  _evacuations_completed += other->_evacuations_completed;
  _bytes_completed += other->_bytes_completed;
  _evacuations_attempted += other->_evacuations_attempted;
  _bytes_attempted += other->_bytes_attempted;
  if (_use_age_table) {
    _age_table->merge(other->age_table());
  }
}

void ShenandoahEvacuationStats::reset() {
  _evacuations_completed = _evacuations_attempted = 0;
  _bytes_completed = _bytes_attempted = 0;
  if (_use_age_table) {
    _age_table->clear();
  }
}

void ShenandoahEvacuationStats::print_on(outputStream* st) {
#ifndef PRODUCT
  size_t abandoned_size = _bytes_attempted - _bytes_completed;
  size_t abandoned_count = _evacuations_attempted - _evacuations_completed;
  st->print_cr("Evacuated %zu%s across %zu objects, "
            "abandoned %zu%s across %zu objects.",
            byte_size_in_proper_unit(_bytes_completed), proper_unit_for_byte_size(_bytes_completed),
            _evacuations_completed,
            byte_size_in_proper_unit(abandoned_size),   proper_unit_for_byte_size(abandoned_size),
            abandoned_count);
#endif
  if (_use_age_table) {
    _age_table->print_on(st);
  }
}

void ShenandoahEvacuationTracker::print_global_on(outputStream* st) {
  print_evacuations_on(st, &_workers_global, &_mutators_global);
}

void ShenandoahEvacuationTracker::print_evacuations_on(outputStream* st,
                                                       ShenandoahEvacuationStats* workers,
                                                       ShenandoahEvacuationStats* mutators) {
  st->print("Workers: ");
  workers->print_on(st);
  st->cr();
  st->print("Mutators: ");
  mutators->print_on(st);
  st->cr();

  ShenandoahHeap* heap = ShenandoahHeap::heap();

  AgeTable young_region_ages(false);
  for (uint i = 0; i < heap->num_regions(); ++i) {
    ShenandoahHeapRegion* r = heap->get_region(i);
    if (r->is_young()) {
      young_region_ages.add(r->age(), r->get_live_data_words());
    }
  }
  st->print("Young regions: ");
  young_region_ages.print_on(st);
  st->cr();
}

class ShenandoahStatAggregator : public ThreadClosure {
public:
  ShenandoahEvacuationStats* _target;
  explicit ShenandoahStatAggregator(ShenandoahEvacuationStats* target) : _target(target) {}
  void do_thread(Thread* thread) override {
    ShenandoahEvacuationStats* local = ShenandoahThreadLocalData::evacuation_stats(thread);
    _target->accumulate(local);
    local->reset();
  }
};

ShenandoahCycleStats ShenandoahEvacuationTracker::flush_cycle_to_global() {
  ShenandoahEvacuationStats mutators, workers;

  ThreadsListHandle java_threads_iterator;
  ShenandoahStatAggregator aggregate_mutators(&mutators);
  java_threads_iterator.list()->threads_do(&aggregate_mutators);

  ShenandoahStatAggregator aggregate_workers(&workers);
  ShenandoahHeap::heap()->gc_threads_do(&aggregate_workers);

  _mutators_global.accumulate(&mutators);
  _workers_global.accumulate(&workers);

  if (ShenandoahGenerationalCensusAtEvac || !ShenandoahGenerationalAdaptiveTenuring) {
    // Ingest mutator & worker collected population vectors into the heap's
    // global census data, and use it to compute an appropriate tenuring threshold
    // for use in the next cycle.
    // The first argument is used for any age 0 cohort population that we may otherwise have
    // missed during the census. This is non-zero only when census happens at marking.
    ShenandoahGenerationalHeap::heap()->age_census()->update_census(0, _mutators_global.age_table(), _workers_global.age_table());
  }

  return {workers, mutators};
}

void ShenandoahEvacuationTracker::begin_evacuation(Thread* thread, size_t bytes) {
  ShenandoahThreadLocalData::begin_evacuation(thread, bytes);
}

void ShenandoahEvacuationTracker::end_evacuation(Thread* thread, size_t bytes) {
  ShenandoahThreadLocalData::end_evacuation(thread, bytes);
}

void ShenandoahEvacuationTracker::record_age(Thread* thread, size_t bytes, uint age) {
  ShenandoahThreadLocalData::record_age(thread, bytes, age);
}

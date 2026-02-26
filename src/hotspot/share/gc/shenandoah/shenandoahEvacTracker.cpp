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

#include "gc/shenandoah/shenandoahEvacTracker.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "runtime/thread.hpp"
#include "runtime/threadSMR.inline.hpp"

ShenandoahEvacuationStats::ShenandoahEvacuations* ShenandoahEvacuationStats::get_category(
  ShenandoahAffiliation from,
  ShenandoahAffiliation to) {
  if (from == YOUNG_GENERATION) {
    if (to == YOUNG_GENERATION) {
      return &_young;
    }
    assert(to == OLD_GENERATION, "If not evacuating to young, must be promotion to old");
    return &_promotion;
  }
  assert(from == OLD_GENERATION, "If not evacuating from young, then must be from old");
  return &_old;
}

void ShenandoahEvacuationStats::begin_evacuation(size_t bytes, ShenandoahAffiliation from, ShenandoahAffiliation to) {
  ShenandoahEvacuations* category = get_category(from, to);
  category->_evacuations_attempted++;
  category->_bytes_attempted += bytes;

}

void ShenandoahEvacuationStats::end_evacuation(size_t bytes, ShenandoahAffiliation from, ShenandoahAffiliation to) {
  ShenandoahEvacuations* category = get_category(from, to);
  category->_evacuations_completed++;
  category->_bytes_completed += bytes;
}

void ShenandoahEvacuationStats::accumulate(const ShenandoahEvacuationStats* other) {
  _young.accumulate(other->_young);
  _old.accumulate(other->_old);
  _promotion.accumulate(other->_promotion);
}

void ShenandoahEvacuationStats::reset() {
  _young.reset();
  _old.reset();
  _promotion.reset();
}

void ShenandoahEvacuationStats::ShenandoahEvacuations::print_on(outputStream* st) const {
  size_t abandoned_size = _bytes_attempted - _bytes_completed;
  size_t abandoned_count = _evacuations_attempted - _evacuations_completed;
  st->print_cr("Evacuated " PROPERFMT" across %zu objects, "
            "abandoned " PROPERFMT " across %zu objects.",
            PROPERFMTARGS(_bytes_completed), _evacuations_completed,
            PROPERFMTARGS(abandoned_size), abandoned_count);
}

void ShenandoahEvacuationStats::print_on(outputStream* st) const {
  st->print("Young: "); _young.print_on(st);
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    st->print("Promotion: "); _promotion.print_on(st);
    st->print("Old: "); _old.print_on(st);
  }
}

void ShenandoahEvacuationTracker::print_global_on(outputStream* st) {
  print_evacuations_on(st, &_workers_global, &_mutators_global);
}

void ShenandoahEvacuationTracker::print_evacuations_on(outputStream* st,
                                                       ShenandoahEvacuationStats* workers,
                                                       ShenandoahEvacuationStats* mutators) {
  assert(ShenandoahEvacTracking, "Only when evac tracking is enabled");
  st->print_cr("Workers: ");
  workers->print_on(st);
  st->cr();
  st->print_cr("Mutators: ");
  mutators->print_on(st);
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

  return {workers, mutators};
}

void ShenandoahEvacuationTracker::begin_evacuation(Thread* thread, size_t bytes, ShenandoahAffiliation from, ShenandoahAffiliation to) {
  ShenandoahThreadLocalData::begin_evacuation(thread, bytes, from, to);
}

void ShenandoahEvacuationTracker::end_evacuation(Thread* thread, size_t bytes, ShenandoahAffiliation from, ShenandoahAffiliation to) {
  ShenandoahThreadLocalData::end_evacuation(thread, bytes, from, to);
}

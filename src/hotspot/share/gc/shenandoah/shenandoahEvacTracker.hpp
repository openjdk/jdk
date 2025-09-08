/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHEVACTRACKER_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHEVACTRACKER_HPP

#include "gc/shared/ageTable.hpp"
#include "gc/shenandoah/shenandoahAffiliation.hpp"
#include "utilities/ostream.hpp"

class ShenandoahEvacuationStats : public CHeapObj<mtGC> {
private:
  struct ShenandoahEvacuations {
    size_t _evacuations_completed;
    size_t _bytes_completed;
    size_t _evacuations_attempted;
    size_t _bytes_attempted;
    ShenandoahEvacuations()
      : _evacuations_completed(0)
      , _bytes_completed(0)
      , _evacuations_attempted(0)
      , _bytes_attempted(0) {
    }

    void accumulate(const ShenandoahEvacuations& other) {
      _evacuations_completed += other._evacuations_completed;
      _bytes_completed += other._bytes_completed;
      _evacuations_attempted += other._evacuations_attempted;
      _bytes_attempted += other._bytes_attempted;
    }

    void reset() {
      _evacuations_completed = 0;
      _bytes_completed = 0;
      _evacuations_attempted = 0;
      _bytes_attempted = 0;
    }

    void print_on(outputStream* st) const;
  };

  ShenandoahEvacuations* get_category(ShenandoahAffiliation from, ShenandoahAffiliation to);

  ShenandoahEvacuations _young;
  ShenandoahEvacuations _old;
  ShenandoahEvacuations _promotion;

  bool      _use_age_table;
  AgeTable* _age_table;

 public:
  ShenandoahEvacuationStats();

  AgeTable* age_table() const;

  // Record that the current thread is attempting to copy this many bytes.
  void begin_evacuation(size_t bytes, ShenandoahAffiliation from, ShenandoahAffiliation to);

  // Record that the current thread has completed copying this many bytes.
  void end_evacuation(size_t bytes, ShenandoahAffiliation from, ShenandoahAffiliation to);
  void record_age(size_t bytes, uint age);

  void print_on(outputStream* st) const;
  void accumulate(const ShenandoahEvacuationStats* other);
  void reset();
};

struct ShenandoahCycleStats {
  ShenandoahEvacuationStats workers;
  ShenandoahEvacuationStats mutators;
};

class ShenandoahEvacuationTracker : public CHeapObj<mtGC> {
private:

  ShenandoahEvacuationStats _workers_global;
  ShenandoahEvacuationStats _mutators_global;

public:
  ShenandoahEvacuationTracker() = default;

  // Record that the given thread has begun to evacuate an object of this size.
  void begin_evacuation(Thread* thread, size_t bytes, ShenandoahAffiliation from, ShenandoahAffiliation to);

  // Multiple threads may attempt to evacuate the same object, but only the successful thread will end the evacuation.
  // Evacuations that were begun, but not ended are considered 'abandoned'.
  void end_evacuation(Thread* thread, size_t bytes, ShenandoahAffiliation from, ShenandoahAffiliation to);
  void record_age(Thread* thread, size_t bytes, uint age);

  void print_global_on(outputStream* st);
  void print_evacuations_on(outputStream* st,
                            ShenandoahEvacuationStats* workers,
                            ShenandoahEvacuationStats* mutators);

  ShenandoahCycleStats flush_cycle_to_global();
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHEVACTRACKER_HPP

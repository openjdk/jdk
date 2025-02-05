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
#include "utilities/ostream.hpp"

class ShenandoahEvacuationStats : public CHeapObj<mtGC> {
private:
  size_t _evacuations_completed;
  size_t _bytes_completed;
  size_t _evacuations_attempted;
  size_t _bytes_attempted;

  bool      _use_age_table;
  AgeTable* _age_table;

 public:
  ShenandoahEvacuationStats();

  AgeTable* age_table() const;

  void begin_evacuation(size_t bytes);
  void end_evacuation(size_t bytes);
  void record_age(size_t bytes, uint age);

  void print_on(outputStream* st);
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

  void begin_evacuation(Thread* thread, size_t bytes);
  void end_evacuation(Thread* thread, size_t bytes);
  void record_age(Thread* thread, size_t bytes, uint age);

  void print_global_on(outputStream* st);
  void print_evacuations_on(outputStream* st,
                            ShenandoahEvacuationStats* workers,
                            ShenandoahEvacuationStats* mutators);

  ShenandoahCycleStats flush_cycle_to_global();
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHEVACTRACKER_HPP

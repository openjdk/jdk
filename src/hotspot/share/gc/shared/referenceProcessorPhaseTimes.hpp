/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_REFERENCEPROCESSORPHASETIMES_HPP
#define SHARE_VM_GC_SHARED_REFERENCEPROCESSORPHASETIMES_HPP

#include "gc/shared/referenceProcessorStats.hpp"
#include "gc/shared/workerDataArray.hpp"
#include "memory/referenceType.hpp"
#include "utilities/ticks.hpp"

class DiscoveredList;
class GCTimer;

class ReferenceProcessorPhaseTimes : public CHeapObj<mtGC> {
public:
  // Detailed phases that has parallel work.
  enum RefProcParPhases {
    SoftRefPhase1,
    SoftRefPhase2,
    SoftRefPhase3,
    WeakRefPhase2,
    WeakRefPhase3,
    FinalRefPhase2,
    FinalRefPhase3,
    PhantomRefPhase2,
    PhantomRefPhase3,
    RefParPhaseMax
  };

  // Sub-phases that are used when processing each j.l.Reference types.
  // Only SoftReference has RefPhase1.
  enum RefProcPhaseNumbers {
    RefPhase1,
    RefPhase2,
    RefPhase3,
    RefPhaseMax
  };

private:
  static const int number_of_subclasses_of_ref = REF_PHANTOM - REF_OTHER; // 5 - 1 = 4

  // Records per thread information of each phase.
  WorkerDataArray<double>* _worker_time_sec[RefParPhaseMax];
  // Records elapsed time of each phase.
  double                   _par_phase_time_ms[RefParPhaseMax];

  // Total spent time for references.
  // e.g. _ref_proc_time_ms[0] = _par_phase_time_ms[SoftRefPhase1] +
  //                             _par_phase_time_ms[SoftRefPhase2] +
  //                             _par_phase_time_ms[SoftRefPhase3] + extra time.
  double                   _ref_proc_time_ms[number_of_subclasses_of_ref];

  double                   _total_time_ms;

  size_t                   _ref_cleared[number_of_subclasses_of_ref];
  size_t                   _ref_discovered[number_of_subclasses_of_ref];
  size_t                   _ref_enqueued[number_of_subclasses_of_ref];
  double                   _balance_queues_time_ms[number_of_subclasses_of_ref];

  bool                     _processing_is_mt;

  // Currently processing reference type.
  ReferenceType            _processing_ref_type;

  GCTimer*                 _gc_timer;

  double par_phase_time_ms(RefProcParPhases phase) const;
  double ref_proc_time_ms(ReferenceType ref_type) const;

  double total_time_ms() const { return _total_time_ms; }

  size_t ref_cleared(ReferenceType ref_type) const;
  size_t ref_enqueued(ReferenceType ref_type) const;

  double balance_queues_time_ms(ReferenceType ref_type) const;

  void print_reference(ReferenceType ref_type, uint base_indent) const;
  void print_phase(RefProcParPhases phase, uint indent) const;

public:
  ReferenceProcessorPhaseTimes(GCTimer* gc_timer, uint max_gc_threads);
  ~ReferenceProcessorPhaseTimes();

  static double uninitialized() { return -1.0; }

  WorkerDataArray<double>* worker_time_sec(RefProcParPhases phase) const;
  void set_par_phase_time_ms(RefProcParPhases phase, double par_phase_time_ms);

  void set_ref_proc_time_ms(ReferenceType ref_type, double ref_proc_time_ms);

  void set_total_time_ms(double total_time_ms) { _total_time_ms = total_time_ms; }

  void set_ref_cleared(ReferenceType ref_type, size_t count);
  size_t ref_discovered(ReferenceType ref_type) const;
  void set_ref_discovered(ReferenceType ref_type, size_t count);
  void set_ref_enqueued(ReferenceType ref_type, size_t count);

  void set_balance_queues_time_ms(ReferenceType ref_type, double time_ms);

  void set_processing_is_mt(bool processing_is_mt) { _processing_is_mt = processing_is_mt; }

  ReferenceType processing_ref_type() const { return _processing_ref_type; }
  void set_processing_ref_type(ReferenceType processing_ref_type) { _processing_ref_type = processing_ref_type; }

  // Returns RefProcParPhases calculated from phase_number and _processing_ref_type.
  RefProcParPhases par_phase(RefProcPhaseNumbers phase_number) const;

  GCTimer* gc_timer() const { return _gc_timer; }

  // Reset all fields. If not reset at next cycle, an assertion will fail.
  void reset();

  void print_all_references(uint base_indent = 0, bool print_total = true) const;
};

// Updates working time of each worker thread.
class RefProcWorkerTimeTracker : public StackObj {
protected:
  WorkerDataArray<double>* _worker_time;
  double                   _start_time;
  uint                     _worker_id;

public:
  RefProcWorkerTimeTracker(ReferenceProcessorPhaseTimes::RefProcPhaseNumbers number,
                           ReferenceProcessorPhaseTimes* phase_times,
                           uint worker_id);
  RefProcWorkerTimeTracker(ReferenceProcessorPhaseTimes::RefProcParPhases phase,
                           ReferenceProcessorPhaseTimes* phase_times,
                           uint worker_id);
  ~RefProcWorkerTimeTracker();
};

class RefProcPhaseTimeBaseTracker : public StackObj {
protected:
  const char*                   _title;
  ReferenceProcessorPhaseTimes* _phase_times;
  Ticks                         _start_ticks;
  Ticks                         _end_ticks;

  Ticks end_ticks();
  double elapsed_time();
  ReferenceProcessorPhaseTimes* phase_times() const { return _phase_times; }
  // Print phase elapsed time with each worker information if MT processed.
  void print_phase(ReferenceProcessorPhaseTimes::RefProcParPhases phase, uint indent);

public:
  RefProcPhaseTimeBaseTracker(const char* title,
                              ReferenceProcessorPhaseTimes* phase_times);
  ~RefProcPhaseTimeBaseTracker();
};

// Updates queue balance time at ReferenceProcessorPhaseTimes and
// save it into GCTimer.
class RefProcBalanceQueuesTimeTracker : public RefProcPhaseTimeBaseTracker {
public:
  RefProcBalanceQueuesTimeTracker(ReferenceProcessorPhaseTimes* phase_times);
  ~RefProcBalanceQueuesTimeTracker();
};

// Updates phase time at ReferenceProcessorPhaseTimes and save it into GCTimer.
class RefProcParPhaseTimeTracker : public RefProcPhaseTimeBaseTracker {
  ReferenceProcessorPhaseTimes::RefProcPhaseNumbers _phase_number;

public:
  RefProcParPhaseTimeTracker(ReferenceProcessorPhaseTimes::RefProcPhaseNumbers phase_number,
                             ReferenceProcessorPhaseTimes* phase_times);
  ~RefProcParPhaseTimeTracker();
};

// Updates phase time related information.
// - Each phase processing time, cleared/discovered reference counts and stats for each working threads if MT processed.
class RefProcPhaseTimesTracker : public RefProcPhaseTimeBaseTracker {
  ReferenceProcessor* _rp;

public:
  RefProcPhaseTimesTracker(ReferenceType ref_type,
                           ReferenceProcessorPhaseTimes* phase_times,
                           ReferenceProcessor* rp);
  ~RefProcPhaseTimesTracker();
};

#endif // SHARE_VM_GC_SHARED_REFERENCEPROCESSORPHASETIMES_HPP

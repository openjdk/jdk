/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHTIMINGTRACKER_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHTIMINGTRACKER_HPP

#include "jfr/jfrEvents.hpp"
#include "gc/shenandoah/shenandoahPhaseTimings.hpp"
#include "memory/allocation.hpp"

class ShenandoahWorkerTimingsTracker : public StackObj {
private:
  double _start_time;
  ShenandoahPhaseTimings::GCParPhases _phase;
  ShenandoahWorkerTimings* _worker_times;
  uint _worker_id;

  EventGCPhaseParallel _event;
public:
    ShenandoahWorkerTimingsTracker(ShenandoahWorkerTimings* worker_times, ShenandoahPhaseTimings::GCParPhases phase, uint worker_id);
    ~ShenandoahWorkerTimingsTracker();
};


class ShenandoahTerminationTimingsTracker : public StackObj {
private:
  double _start_time;
  uint   _worker_id;

public:
  ShenandoahTerminationTimingsTracker(uint worker_id);
  ~ShenandoahTerminationTimingsTracker();
};

// Tracking termination time in specific GC phase
class ShenandoahTerminationTracker : public StackObj {
private:
  ShenandoahPhaseTimings::Phase _phase;

  static ShenandoahPhaseTimings::Phase _current_termination_phase;
public:
  ShenandoahTerminationTracker(ShenandoahPhaseTimings::Phase phase);
  ~ShenandoahTerminationTracker();

  static ShenandoahPhaseTimings::Phase current_termination_phase() { return _current_termination_phase; }
};

#endif // SHARE_VM_GC_SHENANDOAH_SHENANDOAHTIMINGTRACKER_HPP


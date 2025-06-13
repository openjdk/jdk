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
#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHREGULATORTHREAD_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHREGULATORTHREAD_HPP

#include "gc/shared/concurrentGCThread.hpp"

class ShenandoahHeap;
class ShenandoahHeuristics;
class ShenandoahGeneration;
class ShenandoahGenerationalControlThread;
class ShenandoahOldHeuristics;

/*
 * The purpose of this class (and thread) is to allow us to continue
 * to evaluate heuristics during a garbage collection. This is necessary
 * to allow young generation collections to interrupt an old generation
 * collection which is in-progress. This puts heuristic triggers on the
 * same footing as other gc requests (alloc failure, System.gc, etc.).
 * However, this regulator does not block after submitting a gc request.
 *
 * We could use a PeriodicTask for this, but this thread will sleep longer
 * when the allocation rate is lower and PeriodicTasks cannot adjust their
 * sleep time.
 */
class ShenandoahRegulatorThread: public ConcurrentGCThread {
  friend class VMStructs;

 public:
  explicit ShenandoahRegulatorThread(ShenandoahGenerationalControlThread* control_thread);

 protected:
  void run_service() override;
  void stop_service() override;

 private:
  // When mode is generational
  void regulate_young_and_old_cycles();
  // When mode is generational, but ShenandoahAllowOldMarkingPreemption is false
  void regulate_young_and_global_cycles();

  // These return true if a cycle was started.
  bool start_old_cycle() const;
  bool start_young_cycle() const;
  bool start_global_cycle() const;
  bool resume_old_cycle();

  // The generational mode can only unload classes in a global cycle. The regulator
  // thread itself will trigger a global cycle if metaspace is out of memory.
  bool should_start_metaspace_gc();

  // Regulator will sleep longer when the allocation rate is lower.
  void regulator_sleep();

  // Provides instrumentation to track how long it takes to acknowledge a request.
  bool request_concurrent_gc(ShenandoahGeneration* generation) const;

  ShenandoahHeap* _heap;
  ShenandoahGenerationalControlThread* _control_thread;
  ShenandoahHeuristics* _young_heuristics;
  ShenandoahOldHeuristics* _old_heuristics;
  ShenandoahHeuristics* _global_heuristics;

  uint _sleep;
  double _last_sleep_adjust_time;
};


#endif // SHARE_GC_SHENANDOAH_SHENANDOAHREGULATORTHREAD_HPP

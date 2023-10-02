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
#include "gc/shared/gcCause.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "runtime/mutex.hpp"

class ShenandoahHeuristics;
class ShenandoahControlThread;

/*
 * The purpose of this class (and thread) is to allow us to continue
 * to evaluate heuristics during a garbage collection. This is necessary
 * to allow young generation collections to interrupt and old generation
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
  explicit ShenandoahRegulatorThread(ShenandoahControlThread* control_thread);

  const char* name() const { return "ShenandoahRegulatorThread";}

  // This is called from allocation path, and thus should be fast.
  void notify_heap_changed() {
    // Notify that something had changed.
    if (_heap_changed.is_unset()) {
      _heap_changed.set();
    }
  }

 protected:
  void run_service();
  void stop_service();

 private:
  void regulate_interleaved_cycles();
  void regulate_concurrent_cycles();
  void regulate_heap();

  bool start_old_cycle();
  bool start_young_cycle();
  bool start_global_cycle();

  bool should_unload_classes();

  ShenandoahSharedFlag _heap_changed;
  ShenandoahControlThread* _control_thread;
  ShenandoahHeuristics* _young_heuristics;
  ShenandoahHeuristics* _old_heuristics;
  ShenandoahHeuristics* _global_heuristics;

  int _sleep;
  double _last_sleep_adjust_time;

  void regulator_sleep();

  bool request_concurrent_gc(ShenandoahGenerationType generation);
};


#endif // SHARE_GC_SHENANDOAH_SHENANDOAHREGULATORTHREAD_HPP

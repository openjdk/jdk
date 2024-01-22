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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHPERIODICTASKS_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHPERIODICTASKS_HPP

#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "runtime/task.hpp"

// Periodic task is useful for doing asynchronous things that do not require (heap) locks,
// or synchronization with other parts of collector. These could run even when ShenandoahConcurrentThread
// is busy driving the GC cycle.
class ShenandoahPeriodicTask : public PeriodicTask {
private:
  ShenandoahSharedFlag _do_counters_update;
  ShenandoahSharedFlag _force_counters_update;
public:
  ShenandoahPeriodicTask() : PeriodicTask(100) {}
  void task() override;

  void handle_counters_update();
  void handle_force_counters_update();
  void set_forced_counters_update(bool value);
  void notify_heap_changed();
};

// Periodic task to notify blocked paced waiters.
class ShenandoahPeriodicPacerNotify : public PeriodicTask {
public:
  ShenandoahPeriodicPacerNotify() : PeriodicTask(PeriodicTask::min_interval) {}
  void task() override;
};

#endif //SHARE_GC_SHENANDOAH_SHENANDOAHPERIODICTASKS_HPP

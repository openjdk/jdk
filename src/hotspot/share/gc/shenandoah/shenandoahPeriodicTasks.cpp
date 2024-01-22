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
#include "precompiled.hpp"

#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahMonitoringSupport.hpp"
#include "gc/shenandoah/shenandoahPacer.hpp"
#include "gc/shenandoah/shenandoahPeriodicTasks.hpp"

void ShenandoahPeriodicTask::task() {
  handle_force_counters_update();
  handle_counters_update();
}

void ShenandoahPeriodicTask::handle_counters_update() {
  if (_do_counters_update.is_set()) {
    _do_counters_update.unset();
    ShenandoahHeap::heap()->monitoring_support()->update_counters();
  }
}

void ShenandoahPeriodicTask::handle_force_counters_update() {
  if (_force_counters_update.is_set()) {
    _do_counters_update.unset(); // reset these too, we do update now!
    ShenandoahHeap::heap()->monitoring_support()->update_counters();
  }
}

void ShenandoahPeriodicTask::notify_heap_changed() {
  if (_do_counters_update.is_unset()) {
    _do_counters_update.set();
  }
}

void ShenandoahPeriodicTask::set_forced_counters_update(bool value) {
  _force_counters_update.set_cond(value);
}

void ShenandoahPeriodicPacerNotify::task() {
  assert(ShenandoahPacing, "Should not be here otherwise");
  ShenandoahHeap::heap()->pacer()->notify_waiters();
}



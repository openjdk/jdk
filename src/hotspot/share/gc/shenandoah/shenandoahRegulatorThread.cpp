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

#include "gc/shenandoah/heuristics/shenandoahHeuristics.hpp"
#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahControlThread.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahOldGeneration.hpp"
#include "gc/shenandoah/shenandoahRegulatorThread.hpp"
#include "gc/shenandoah/shenandoahYoungGeneration.hpp"
#include "logging/log.hpp"

static ShenandoahHeuristics* get_heuristics(ShenandoahGeneration* nullable) {
  return nullable != nullptr ? nullable->heuristics() : nullptr;
}

ShenandoahRegulatorThread::ShenandoahRegulatorThread(ShenandoahControlThread* control_thread) :
  ConcurrentGCThread(),
  _control_thread(control_thread),
  _sleep(ShenandoahControlIntervalMin),
  _last_sleep_adjust_time(os::elapsedTime()) {

  ShenandoahHeap* heap = ShenandoahHeap::heap();
  _old_heuristics = get_heuristics(heap->old_generation());
  _young_heuristics = get_heuristics(heap->young_generation());
  _global_heuristics = get_heuristics(heap->global_generation());

  create_and_start();
}

void ShenandoahRegulatorThread::run_service() {
  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    if (ShenandoahAllowOldMarkingPreemption) {
      regulate_young_and_old_cycles();
    } else {
      regulate_young_and_global_cycles();
    }
  } else {
    regulate_global_cycles();
  }

  log_info(gc)("%s: Done.", name());
}

void ShenandoahRegulatorThread::regulate_young_and_old_cycles() {
  assert(_young_heuristics != nullptr, "Need young heuristics.");
  assert(_old_heuristics != nullptr, "Need old heuristics.");

  while (!should_terminate()) {
    ShenandoahControlThread::GCMode mode = _control_thread->gc_mode();
    if (mode == ShenandoahControlThread::none) {
      if (should_start_metaspace_gc()) {
        if (request_concurrent_gc(ShenandoahControlThread::select_global_generation())) {
          log_info(gc)("Heuristics request for global (unload classes) accepted.");
        }
      } else {
        if (_young_heuristics->should_start_gc()) {
          // Give the old generation a chance to run. The old generation cycle
          // begins with a 'bootstrap' cycle that will also collect young.
          if (start_old_cycle()) {
            log_info(gc)("Heuristics request for old collection accepted");
          } else if (request_concurrent_gc(YOUNG)) {
            log_info(gc)("Heuristics request for young collection accepted");
          }
        }
      }
    } else if (mode == ShenandoahControlThread::servicing_old) {
      if (start_young_cycle()) {
        log_info(gc)("Heuristics request to interrupt old for young collection accepted");
      }
    }

    regulator_sleep();
  }
}


void ShenandoahRegulatorThread::regulate_young_and_global_cycles() {
  assert(_young_heuristics != nullptr, "Need young heuristics.");
  assert(_global_heuristics != nullptr, "Need global heuristics.");

  while (!should_terminate()) {
    if (_control_thread->gc_mode() == ShenandoahControlThread::none) {
      if (start_global_cycle()) {
        log_info(gc)("Heuristics request for global collection accepted.");
      } else if (start_young_cycle()) {
        log_info(gc)("Heuristics request for young collection accepted.");
      }
    }

    regulator_sleep();
  }
}

void ShenandoahRegulatorThread::regulate_global_cycles() {
  assert(_global_heuristics != nullptr, "Need global heuristics.");

  while (!should_terminate()) {
    if (_control_thread->gc_mode() == ShenandoahControlThread::none) {
      if (start_global_cycle()) {
        log_info(gc)("Heuristics request for global collection accepted.");
      }
    }

    regulator_sleep();
  }
}

void ShenandoahRegulatorThread::regulator_sleep() {
  // Wait before performing the next action. If allocation happened during this wait,
  // we exit sooner, to let heuristics re-evaluate new conditions. If we are at idle,
  // back off exponentially.
  double current = os::elapsedTime();

  if (_heap_changed.try_unset()) {
    _sleep = ShenandoahControlIntervalMin;
  } else if ((current - _last_sleep_adjust_time) * 1000 > ShenandoahControlIntervalAdjustPeriod){
    _sleep = MIN2<int>(ShenandoahControlIntervalMax, MAX2(1, _sleep * 2));
    _last_sleep_adjust_time = current;
  }

  os::naked_short_sleep(_sleep);
  if (LogTarget(Debug, gc, thread)::is_enabled()) {
    double elapsed = os::elapsedTime() - current;
    double hiccup = elapsed - double(_sleep);
    if (hiccup > 0.001) {
      log_debug(gc, thread)("Regulator hiccup time: %.3fs", hiccup);
    }
  }
}

bool ShenandoahRegulatorThread::start_old_cycle() {
  return _old_heuristics->should_start_gc() && request_concurrent_gc(OLD);
}

bool ShenandoahRegulatorThread::start_young_cycle() {
  return _young_heuristics->should_start_gc() && request_concurrent_gc(YOUNG);
}

bool ShenandoahRegulatorThread::start_global_cycle() {
  return _global_heuristics->should_start_gc() && request_concurrent_gc(ShenandoahControlThread::select_global_generation());
}

bool ShenandoahRegulatorThread::request_concurrent_gc(ShenandoahGenerationType generation) {
  double now = os::elapsedTime();
  bool accepted = _control_thread->request_concurrent_gc(generation);
  if (LogTarget(Debug, gc, thread)::is_enabled() && accepted) {
    double wait_time = os::elapsedTime() - now;
    if (wait_time > 0.001) {
      log_debug(gc, thread)("Regulator waited %.3fs for control thread to acknowledge request.", wait_time);
    }
  }
  return accepted;
}

void ShenandoahRegulatorThread::stop_service() {
  log_info(gc)("%s: Stop requested.", name());
}

bool ShenandoahRegulatorThread::should_start_metaspace_gc() {
  // The generational mode can, at present, only unload classes during a global
  // cycle. For this reason, we treat an oom in metaspace as a _trigger_ for a
  // global cycle. But, we check other prerequisites before starting a gc that won't
  // unload anything.
  return ClassUnloadingWithConcurrentMark
      && _global_heuristics->can_unload_classes()
      && _global_heuristics->has_metaspace_oom();
}


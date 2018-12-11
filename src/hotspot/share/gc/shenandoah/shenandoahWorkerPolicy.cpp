/*
 * Copyright (c) 2017, 2018, Red Hat, Inc. All rights reserved.
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

#include "gc/shared/workerPolicy.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"
#include "runtime/thread.hpp"

uint ShenandoahWorkerPolicy::_prev_par_marking     = 0;
uint ShenandoahWorkerPolicy::_prev_conc_marking    = 0;
uint ShenandoahWorkerPolicy::_prev_conc_evac       = 0;
uint ShenandoahWorkerPolicy::_prev_fullgc          = 0;
uint ShenandoahWorkerPolicy::_prev_degengc         = 0;
uint ShenandoahWorkerPolicy::_prev_stw_traversal   = 0;
uint ShenandoahWorkerPolicy::_prev_conc_traversal  = 0;
uint ShenandoahWorkerPolicy::_prev_conc_update_ref = 0;
uint ShenandoahWorkerPolicy::_prev_par_update_ref  = 0;
uint ShenandoahWorkerPolicy::_prev_conc_cleanup    = 0;
uint ShenandoahWorkerPolicy::_prev_conc_reset      = 0;

uint ShenandoahWorkerPolicy::calc_workers_for_init_marking() {
  uint active_workers = (_prev_par_marking == 0) ? ParallelGCThreads : _prev_par_marking;

  _prev_par_marking =
    WorkerPolicy::calc_active_workers(ParallelGCThreads,
                                      active_workers,
                                      Threads::number_of_non_daemon_threads());
  return _prev_par_marking;
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_marking() {
  uint active_workers = (_prev_conc_marking == 0) ?  ConcGCThreads : _prev_conc_marking;
  _prev_conc_marking =
    WorkerPolicy::calc_active_conc_workers(ConcGCThreads,
                                           active_workers,
                                           Threads::number_of_non_daemon_threads());
  return _prev_conc_marking;
}

// Reuse the calculation result from init marking
uint ShenandoahWorkerPolicy::calc_workers_for_final_marking() {
  return _prev_par_marking;
}

// Calculate workers for concurrent evacuation (concurrent GC)
uint ShenandoahWorkerPolicy::calc_workers_for_conc_evac() {
  uint active_workers = (_prev_conc_evac == 0) ? ConcGCThreads : _prev_conc_evac;
  _prev_conc_evac =
    WorkerPolicy::calc_active_conc_workers(ConcGCThreads,
                                           active_workers,
                                           Threads::number_of_non_daemon_threads());
  return _prev_conc_evac;
}

// Calculate workers for parallel fullgc
uint ShenandoahWorkerPolicy::calc_workers_for_fullgc() {
  uint active_workers = (_prev_fullgc == 0) ?  ParallelGCThreads : _prev_fullgc;
  _prev_fullgc =
    WorkerPolicy::calc_active_workers(ParallelGCThreads,
                                      active_workers,
                                      Threads::number_of_non_daemon_threads());
  return _prev_fullgc;
}

// Calculate workers for parallel degenerated gc
uint ShenandoahWorkerPolicy::calc_workers_for_stw_degenerated() {
  uint active_workers = (_prev_degengc == 0) ?  ParallelGCThreads : _prev_degengc;
  _prev_degengc =
    WorkerPolicy::calc_active_workers(ParallelGCThreads,
                                      active_workers,
                                      Threads::number_of_non_daemon_threads());
  return _prev_degengc;
}

// Calculate workers for Stop-the-world traversal GC
uint ShenandoahWorkerPolicy::calc_workers_for_stw_traversal() {
  uint active_workers = (_prev_stw_traversal == 0) ? ParallelGCThreads : _prev_stw_traversal;
  _prev_stw_traversal =
    WorkerPolicy::calc_active_workers(ParallelGCThreads,
                                      active_workers,
                                      Threads::number_of_non_daemon_threads());
  return _prev_stw_traversal;
}

// Calculate workers for concurent traversal GC
uint ShenandoahWorkerPolicy::calc_workers_for_conc_traversal() {
  uint active_workers = (_prev_conc_traversal == 0) ? ConcGCThreads : _prev_conc_traversal;
  _prev_conc_traversal =
    WorkerPolicy::calc_active_conc_workers(ConcGCThreads,
                                           active_workers,
                                           Threads::number_of_non_daemon_threads());
  return _prev_conc_traversal;
}

// Calculate workers for concurrent reference update
uint ShenandoahWorkerPolicy::calc_workers_for_conc_update_ref() {
  uint active_workers = (_prev_conc_update_ref == 0) ? ConcGCThreads : _prev_conc_update_ref;
  _prev_conc_update_ref =
    WorkerPolicy::calc_active_conc_workers(ConcGCThreads,
                                           active_workers,
                                           Threads::number_of_non_daemon_threads());
  return _prev_conc_update_ref;
}

// Calculate workers for parallel reference update
uint ShenandoahWorkerPolicy::calc_workers_for_final_update_ref() {
  uint active_workers = (_prev_par_update_ref == 0) ? ParallelGCThreads : _prev_par_update_ref;
  _prev_par_update_ref =
    WorkerPolicy::calc_active_workers(ParallelGCThreads,
                                      active_workers,
                                      Threads::number_of_non_daemon_threads());
  return _prev_par_update_ref;
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_preclean() {
  // Precleaning is single-threaded
  return 1;
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_cleanup() {
  uint active_workers = (_prev_conc_cleanup == 0) ? ConcGCThreads : _prev_conc_cleanup;
  _prev_conc_cleanup =
          WorkerPolicy::calc_active_conc_workers(ConcGCThreads,
                                                 active_workers,
                                                 Threads::number_of_non_daemon_threads());
  return _prev_conc_cleanup;
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_reset() {
  uint active_workers = (_prev_conc_reset == 0) ? ConcGCThreads : _prev_conc_reset;
  _prev_conc_reset =
          WorkerPolicy::calc_active_conc_workers(ConcGCThreads,
                                                 active_workers,
                                                 Threads::number_of_non_daemon_threads());
  return _prev_conc_reset;
}

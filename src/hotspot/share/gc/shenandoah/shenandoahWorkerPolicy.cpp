/*
 * Copyright (c) 2017, 2019, Red Hat, Inc. All rights reserved.
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


#include "gc/shared/gc_globals.hpp"
#include "gc/shenandoah/shenandoahWorkerPolicy.hpp"

uint ShenandoahWorkerPolicy::calc_workers_for_init_marking() {
  return ParallelGCThreads;
}

uint ShenandoahWorkerPolicy::calc_workers_for_any_concurrent_phase(ShenandoahHeuristics* heuristics) {
  uint surge_level = heuristics->get_surge_level();
  uint active_workers;
  assert (surge_level <= ShenandoahHeuristics::max_surge_level(), "sanity");

  active_workers = (uint) (ConcGCThreads * (1.0 + surge_level * 0.25));
  if (active_workers > ParallelGCThreads) {
    active_workers = ParallelGCThreads;
  }
  if (surge_level > 0) {
    log_info(gc)("Surging to level %u, workers: %u", surge_level, active_workers);
#undef KELVIN_DEBUG
#ifdef KELVIN_DEBUG
    log_info(gc)("  got the surge level from %s heuristics", heuristics->name());
#endif
  }
  return active_workers;
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_marking(ShenandoahGeneration* generation) {
#undef KELVIN_DEBUG
#ifdef KELVIN_DEBUG
  ShenandoahGenerationType t = generation->type();
  log_info(gc)("calc_workers_for_conc_marking(generation: %s)", shenandoah_generation_name(t));
#endif
  return calc_workers_for_any_concurrent_phase(generation->heuristics());
}

uint ShenandoahWorkerPolicy::calc_workers_for_rs_scanning(ShenandoahGeneration* generation) {
#ifdef KELVIN_DEBUG
  ShenandoahGenerationType t = generation->type();
  log_info(gc)("calc_workers_for_rs_scanning(generation: %s)", shenandoah_generation_name(t));
#endif
  return calc_workers_for_any_concurrent_phase(generation->heuristics());
}

uint ShenandoahWorkerPolicy::calc_workers_for_final_marking() {
  return ParallelGCThreads;
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_refs_processing(ShenandoahGeneration* generation) {
#ifdef KELVIN_DEBUG
  ShenandoahGenerationType t = generation->type();
  log_info(gc)("calc_workers_for_conc_refs_processing(generation: %s)", shenandoah_generation_name(t));
#endif
  return calc_workers_for_any_concurrent_phase(generation->heuristics());
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_root_processing(ShenandoahGeneration* generation) {
#ifdef KELVIN_DEBUG
  ShenandoahGenerationType t = generation->type();
  log_info(gc)("calc_workers_for_conc_root_processing(generation: %s)", shenandoah_generation_name(t));
#endif
  return calc_workers_for_any_concurrent_phase(generation->heuristics());
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_evac(ShenandoahGeneration* generation) {
#ifdef KELVIN_DEBUG
  ShenandoahGenerationType t = generation->type();
  log_info(gc)("calc_workers_for_conc_evac(generation: %s)", shenandoah_generation_name(t));
#endif
  return calc_workers_for_any_concurrent_phase(generation->heuristics());
}

uint ShenandoahWorkerPolicy::calc_workers_for_fullgc() {
  return ParallelGCThreads;
}

uint ShenandoahWorkerPolicy::calc_workers_for_stw_degenerated() {
  return ParallelGCThreads;
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_update_ref(ShenandoahGeneration* generation) {
#ifdef KELVIN_DEBUG
  ShenandoahGenerationType t = generation->type();
  log_info(gc)("calc_workers_for_conc_update_ref(generation: %s)", shenandoah_generation_name(t));
#endif
  return calc_workers_for_any_concurrent_phase(generation->heuristics());
}

uint ShenandoahWorkerPolicy::calc_workers_for_final_update_ref() {
  return ParallelGCThreads;
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_reset(ShenandoahGeneration* generation) {
#ifdef KELVIN_DEBUG
  ShenandoahGenerationType t = generation->type();
  log_info(gc)("calc_workers_for_conc_reset(generation: %s)", shenandoah_generation_name(t));
#endif
  return calc_workers_for_any_concurrent_phase(generation->heuristics());
}

uint ShenandoahWorkerPolicy::calc_workers_for_conc_cleanup(ShenandoahGeneration* generation) {
#ifdef KELVIN_DEBUG
  ShenandoahGenerationType t = generation->type();
  log_info(gc)("calc_workers_for_conc_cleanup(generation: %s)", shenandoah_generation_name(t));
#endif
  return calc_workers_for_any_concurrent_phase(generation->heuristics());
}

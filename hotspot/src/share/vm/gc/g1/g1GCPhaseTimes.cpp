/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/concurrentG1Refine.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/workerDataArray.inline.hpp"
#include "memory/resourceArea.hpp"
#include "logging/log.hpp"
#include "runtime/timer.hpp"
#include "runtime/os.hpp"

static const char* Indents[5] = {"", "  ", "    ", "      ", "        "};

G1GCPhaseTimes::G1GCPhaseTimes(uint max_gc_threads) :
  _max_gc_threads(max_gc_threads)
{
  assert(max_gc_threads > 0, "Must have some GC threads");

  _gc_par_phases[GCWorkerStart] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Start (ms):");
  _gc_par_phases[ExtRootScan] = new WorkerDataArray<double>(max_gc_threads, "Ext Root Scanning (ms):");

  // Root scanning phases
  _gc_par_phases[ThreadRoots] = new WorkerDataArray<double>(max_gc_threads, "Thread Roots (ms):");
  _gc_par_phases[StringTableRoots] = new WorkerDataArray<double>(max_gc_threads, "StringTable Roots (ms):");
  _gc_par_phases[UniverseRoots] = new WorkerDataArray<double>(max_gc_threads, "Universe Roots (ms):");
  _gc_par_phases[JNIRoots] = new WorkerDataArray<double>(max_gc_threads, "JNI Handles Roots (ms):");
  _gc_par_phases[ObjectSynchronizerRoots] = new WorkerDataArray<double>(max_gc_threads, "ObjectSynchronizer Roots (ms):");
  _gc_par_phases[FlatProfilerRoots] = new WorkerDataArray<double>(max_gc_threads, "FlatProfiler Roots (ms):");
  _gc_par_phases[ManagementRoots] = new WorkerDataArray<double>(max_gc_threads, "Management Roots (ms):");
  _gc_par_phases[SystemDictionaryRoots] = new WorkerDataArray<double>(max_gc_threads, "SystemDictionary Roots (ms):");
  _gc_par_phases[CLDGRoots] = new WorkerDataArray<double>(max_gc_threads, "CLDG Roots (ms):");
  _gc_par_phases[JVMTIRoots] = new WorkerDataArray<double>(max_gc_threads, "JVMTI Roots (ms):");
  _gc_par_phases[CMRefRoots] = new WorkerDataArray<double>(max_gc_threads, "CM RefProcessor Roots (ms):");
  _gc_par_phases[WaitForStrongCLD] = new WorkerDataArray<double>(max_gc_threads, "Wait For Strong CLD (ms):");
  _gc_par_phases[WeakCLDRoots] = new WorkerDataArray<double>(max_gc_threads, "Weak CLD Roots (ms):");
  _gc_par_phases[SATBFiltering] = new WorkerDataArray<double>(max_gc_threads, "SATB Filtering (ms):");

  _gc_par_phases[UpdateRS] = new WorkerDataArray<double>(max_gc_threads, "Update RS (ms):");
  if (ConcurrentG1Refine::hot_card_cache_enabled()) {
    _gc_par_phases[ScanHCC] = new WorkerDataArray<double>(max_gc_threads, "Scan HCC (ms):");
  } else {
    _gc_par_phases[ScanHCC] = NULL;
  }
  _gc_par_phases[ScanRS] = new WorkerDataArray<double>(max_gc_threads, "Scan RS (ms):");
  _gc_par_phases[CodeRoots] = new WorkerDataArray<double>(max_gc_threads, "Code Root Scanning (ms):");
  _gc_par_phases[ObjCopy] = new WorkerDataArray<double>(max_gc_threads, "Object Copy (ms):");
  _gc_par_phases[Termination] = new WorkerDataArray<double>(max_gc_threads, "Termination (ms):");
  _gc_par_phases[GCWorkerTotal] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Total (ms):");
  _gc_par_phases[GCWorkerEnd] = new WorkerDataArray<double>(max_gc_threads, "GC Worker End (ms):");
  _gc_par_phases[Other] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Other (ms):");

  _update_rs_processed_buffers = new WorkerDataArray<size_t>(max_gc_threads, "Processed Buffers:");
  _gc_par_phases[UpdateRS]->link_thread_work_items(_update_rs_processed_buffers);

  _termination_attempts = new WorkerDataArray<size_t>(max_gc_threads, "Termination Attempts:");
  _gc_par_phases[Termination]->link_thread_work_items(_termination_attempts);

  if (UseStringDeduplication) {
    _gc_par_phases[StringDedupQueueFixup] = new WorkerDataArray<double>(max_gc_threads, "Queue Fixup (ms):");
    _gc_par_phases[StringDedupTableFixup] = new WorkerDataArray<double>(max_gc_threads, "Table Fixup (ms):");
  } else {
    _gc_par_phases[StringDedupQueueFixup] = NULL;
    _gc_par_phases[StringDedupTableFixup] = NULL;
  }

  _gc_par_phases[RedirtyCards] = new WorkerDataArray<double>(max_gc_threads, "Parallel Redirty (ms):");
  _redirtied_cards = new WorkerDataArray<size_t>(max_gc_threads, "Redirtied Cards:");
  _gc_par_phases[RedirtyCards]->link_thread_work_items(_redirtied_cards);

  _gc_par_phases[PreserveCMReferents] = new WorkerDataArray<double>(max_gc_threads, "Parallel Preserve CM Refs (ms):");
}

void G1GCPhaseTimes::note_gc_start() {
  _gc_start_counter = os::elapsed_counter();
  _cur_expand_heap_time_ms = 0.0;
  _external_accounted_time_ms = 0.0;

  for (int i = 0; i < GCParPhasesSentinel; i++) {
    if (_gc_par_phases[i] != NULL) {
      _gc_par_phases[i]->reset();
    }
  }
}

#define ASSERT_PHASE_UNINITIALIZED(phase) \
    assert(_gc_par_phases[phase]->get(i) == uninitialized, "Phase " #phase " reported for thread that was not started");

double G1GCPhaseTimes::worker_time(GCParPhases phase, uint worker) {
  double value = _gc_par_phases[phase]->get(worker);
  if (value != WorkerDataArray<double>::uninitialized()) {
    return value;
  }
  return 0.0;
}

void G1GCPhaseTimes::note_gc_end() {
  _gc_pause_time_ms = TimeHelper::counter_to_millis(os::elapsed_counter() - _gc_start_counter);

  double uninitialized = WorkerDataArray<double>::uninitialized();

  for (uint i = 0; i < _max_gc_threads; i++) {
    double worker_start = _gc_par_phases[GCWorkerStart]->get(i);
    if (worker_start != uninitialized) {
      assert(_gc_par_phases[GCWorkerEnd]->get(i) != uninitialized, "Worker started but not ended.");
      double total_worker_time = _gc_par_phases[GCWorkerEnd]->get(i) - _gc_par_phases[GCWorkerStart]->get(i);
      record_time_secs(GCWorkerTotal, i , total_worker_time);

      double worker_known_time =
          worker_time(ExtRootScan, i)
          + worker_time(SATBFiltering, i)
          + worker_time(UpdateRS, i)
          + worker_time(ScanRS, i)
          + worker_time(CodeRoots, i)
          + worker_time(ObjCopy, i)
          + worker_time(Termination, i);

      record_time_secs(Other, i, total_worker_time - worker_known_time);
    } else {
      // Make sure all slots are uninitialized since this thread did not seem to have been started
      ASSERT_PHASE_UNINITIALIZED(GCWorkerEnd);
      ASSERT_PHASE_UNINITIALIZED(ExtRootScan);
      ASSERT_PHASE_UNINITIALIZED(SATBFiltering);
      ASSERT_PHASE_UNINITIALIZED(UpdateRS);
      ASSERT_PHASE_UNINITIALIZED(ScanRS);
      ASSERT_PHASE_UNINITIALIZED(CodeRoots);
      ASSERT_PHASE_UNINITIALIZED(ObjCopy);
      ASSERT_PHASE_UNINITIALIZED(Termination);
    }
  }
}

#undef ASSERT_PHASE_UNINITIALIZED

// record the time a phase took in seconds
void G1GCPhaseTimes::record_time_secs(GCParPhases phase, uint worker_i, double secs) {
  _gc_par_phases[phase]->set(worker_i, secs);
}

// add a number of seconds to a phase
void G1GCPhaseTimes::add_time_secs(GCParPhases phase, uint worker_i, double secs) {
  _gc_par_phases[phase]->add(worker_i, secs);
}

void G1GCPhaseTimes::record_thread_work_item(GCParPhases phase, uint worker_i, size_t count) {
  _gc_par_phases[phase]->set_thread_work_item(worker_i, count);
}

// return the average time for a phase in milliseconds
double G1GCPhaseTimes::average_time_ms(GCParPhases phase) {
  return _gc_par_phases[phase]->average() * 1000.0;
}

size_t G1GCPhaseTimes::sum_thread_work_items(GCParPhases phase) {
  assert(_gc_par_phases[phase]->thread_work_items() != NULL, "No sub count");
  return _gc_par_phases[phase]->thread_work_items()->sum();
}

template <class T>
void G1GCPhaseTimes::details(T* phase, const char* indent) {
  Log(gc, phases, task) log;
  if (log.is_level(LogLevel::Trace)) {
    outputStream* trace_out = log.trace_stream();
    trace_out->print("%s", indent);
    phase->print_details_on(trace_out);
  }
}

void G1GCPhaseTimes::log_phase(WorkerDataArray<double>* phase, uint indent, outputStream* out, bool print_sum) {
  out->print("%s", Indents[indent]);
  phase->print_summary_on(out, print_sum);
  details(phase, Indents[indent]);

  WorkerDataArray<size_t>* work_items = phase->thread_work_items();
  if (work_items != NULL) {
    out->print("%s", Indents[indent + 1]);
    work_items->print_summary_on(out, true);
    details(work_items, Indents[indent + 1]);
  }
}

void G1GCPhaseTimes::debug_phase(WorkerDataArray<double>* phase) {
  Log(gc, phases) log;
  if (log.is_level(LogLevel::Debug)) {
    ResourceMark rm;
    log_phase(phase, 2, log.debug_stream(), true);
  }
}

void G1GCPhaseTimes::trace_phase(WorkerDataArray<double>* phase, bool print_sum) {
  Log(gc, phases) log;
  if (log.is_level(LogLevel::Trace)) {
    ResourceMark rm;
    log_phase(phase, 3, log.trace_stream(), print_sum);
  }
}

#define PHASE_DOUBLE_FORMAT "%s%s: %.1lfms"
#define PHASE_SIZE_FORMAT "%s%s: " SIZE_FORMAT

#define info_line(str, value) \
  log_info(gc, phases)(PHASE_DOUBLE_FORMAT, Indents[1], str, value);

#define debug_line(str, value) \
  log_debug(gc, phases)(PHASE_DOUBLE_FORMAT, Indents[2], str, value);

#define trace_line(str, value) \
  log_trace(gc, phases)(PHASE_DOUBLE_FORMAT, Indents[3], str, value);

#define trace_line_sz(str, value) \
  log_trace(gc, phases)(PHASE_SIZE_FORMAT, Indents[3], str, value);

#define trace_line_ms(str, value) \
  log_trace(gc, phases)(PHASE_SIZE_FORMAT, Indents[3], str, value);

#define info_line_and_account(str, value) \
  info_line(str, value);                  \
  accounted_time_ms += value;

void G1GCPhaseTimes::print() {
  note_gc_end();

  double accounted_time_ms = _external_accounted_time_ms;
  if (_root_region_scan_wait_time_ms > 0.0) {
    info_line_and_account("Root Region Scan Waiting", _root_region_scan_wait_time_ms);
  }

  info_line_and_account("Evacuate Collection Set", _cur_collection_par_time_ms);
  trace_phase(_gc_par_phases[GCWorkerStart], false);
  debug_phase(_gc_par_phases[ExtRootScan]);
  for (int i = ThreadRoots; i <= SATBFiltering; i++) {
    trace_phase(_gc_par_phases[i]);
  }
  debug_phase(_gc_par_phases[UpdateRS]);
  if (ConcurrentG1Refine::hot_card_cache_enabled()) {
    trace_phase(_gc_par_phases[ScanHCC]);
  }
  debug_phase(_gc_par_phases[ScanRS]);
  debug_phase(_gc_par_phases[CodeRoots]);
  debug_phase(_gc_par_phases[ObjCopy]);
  debug_phase(_gc_par_phases[Termination]);
  debug_phase(_gc_par_phases[Other]);
  debug_phase(_gc_par_phases[GCWorkerTotal]);
  trace_phase(_gc_par_phases[GCWorkerEnd], false);

  info_line_and_account("Code Roots", _cur_collection_code_root_fixup_time_ms + _cur_strong_code_root_purge_time_ms);
  debug_line("Code Roots Fixup", _cur_collection_code_root_fixup_time_ms);
  debug_line("Code Roots Purge", _cur_strong_code_root_purge_time_ms);

  if (G1StringDedup::is_enabled()) {
    info_line_and_account("String Dedup Fixup", _cur_string_dedup_fixup_time_ms);
    debug_phase(_gc_par_phases[StringDedupQueueFixup]);
    debug_phase(_gc_par_phases[StringDedupTableFixup]);
  }
  info_line_and_account("Clear Card Table", _cur_clear_ct_time_ms);
  info_line_and_account("Expand Heap After Collection", _cur_expand_heap_time_ms);

  double free_cset_time = _recorded_young_free_cset_time_ms + _recorded_non_young_free_cset_time_ms;
  info_line_and_account("Free Collection Set", free_cset_time);
  debug_line("Young Free Collection Set", _recorded_young_free_cset_time_ms);
  debug_line("Non-Young Free Collection Set", _recorded_non_young_free_cset_time_ms);
  info_line_and_account("Merge Per-Thread State", _recorded_merge_pss_time_ms);

  info_line("Other", _gc_pause_time_ms - accounted_time_ms);
  if (_cur_verify_before_time_ms > 0.0) {
    debug_line("Verify Before", _cur_verify_before_time_ms);
  }
  if (G1CollectedHeap::heap()->evacuation_failed()) {
    double evac_fail_handling = _cur_evac_fail_recalc_used + _cur_evac_fail_remove_self_forwards +
      _cur_evac_fail_restore_remsets;
    debug_line("Evacuation Failure", evac_fail_handling);
    trace_line("Recalculate Used", _cur_evac_fail_recalc_used);
    trace_line("Remove Self Forwards",_cur_evac_fail_remove_self_forwards);
    trace_line("Restore RemSet", _cur_evac_fail_restore_remsets);
  }
  debug_line("Choose CSet", (_recorded_young_cset_choice_time_ms + _recorded_non_young_cset_choice_time_ms));
  debug_line("Preserve CM Refs", _recorded_preserve_cm_referents_time_ms);
  trace_phase(_gc_par_phases[PreserveCMReferents]);
  debug_line("Reference Processing", _cur_ref_proc_time_ms);
  debug_line("Reference Enqueuing", _cur_ref_enq_time_ms);
  debug_line("Redirty Cards", _recorded_redirty_logged_cards_time_ms);
  trace_phase(_gc_par_phases[RedirtyCards]);
  if (G1EagerReclaimHumongousObjects) {
    debug_line("Humongous Register", _cur_fast_reclaim_humongous_register_time_ms);
    trace_line_sz("Humongous Total", _cur_fast_reclaim_humongous_total);
    trace_line_sz("Humongous Candidate", _cur_fast_reclaim_humongous_candidates);
    debug_line("Humongous Reclaim", _cur_fast_reclaim_humongous_time_ms);
    trace_line_sz("Humongous Reclaimed", _cur_fast_reclaim_humongous_reclaimed);
  }
  if (_cur_verify_after_time_ms > 0.0) {
    debug_line("Verify After", _cur_verify_after_time_ms);
  }
}

G1GCParPhaseTimesTracker::G1GCParPhaseTimesTracker(G1GCPhaseTimes* phase_times, G1GCPhaseTimes::GCParPhases phase, uint worker_id) :
    _phase_times(phase_times), _phase(phase), _worker_id(worker_id) {
  if (_phase_times != NULL) {
    _start_time = os::elapsedTime();
  }
}

G1GCParPhaseTimesTracker::~G1GCParPhaseTimesTracker() {
  if (_phase_times != NULL) {
    _phase_times->record_time_secs(_phase, _worker_id, os::elapsedTime() - _start_time);
  }
}


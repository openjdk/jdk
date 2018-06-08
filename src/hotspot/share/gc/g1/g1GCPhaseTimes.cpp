/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1GCPhaseTimes.hpp"
#include "gc/g1/g1HotCardCache.hpp"
#include "gc/g1/g1ParScanThreadState.inline.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/shared/workerDataArray.inline.hpp"
#include "memory/resourceArea.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "runtime/timer.hpp"
#include "runtime/os.hpp"
#include "utilities/macros.hpp"

static const char* Indents[5] = {"", "  ", "    ", "      ", "        "};

G1GCPhaseTimes::G1GCPhaseTimes(STWGCTimer* gc_timer, uint max_gc_threads) :
  _max_gc_threads(max_gc_threads),
  _gc_start_counter(0),
  _gc_pause_time_ms(0.0),
  _ref_phase_times((GCTimer*)gc_timer, max_gc_threads)
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
  _gc_par_phases[ManagementRoots] = new WorkerDataArray<double>(max_gc_threads, "Management Roots (ms):");
  _gc_par_phases[SystemDictionaryRoots] = new WorkerDataArray<double>(max_gc_threads, "SystemDictionary Roots (ms):");
  _gc_par_phases[CLDGRoots] = new WorkerDataArray<double>(max_gc_threads, "CLDG Roots (ms):");
  _gc_par_phases[JVMTIRoots] = new WorkerDataArray<double>(max_gc_threads, "JVMTI Roots (ms):");
  _gc_par_phases[CMRefRoots] = new WorkerDataArray<double>(max_gc_threads, "CM RefProcessor Roots (ms):");
  _gc_par_phases[WaitForStrongCLD] = new WorkerDataArray<double>(max_gc_threads, "Wait For Strong CLD (ms):");
  _gc_par_phases[WeakCLDRoots] = new WorkerDataArray<double>(max_gc_threads, "Weak CLD Roots (ms):");
  _gc_par_phases[SATBFiltering] = new WorkerDataArray<double>(max_gc_threads, "SATB Filtering (ms):");

  _gc_par_phases[UpdateRS] = new WorkerDataArray<double>(max_gc_threads, "Update RS (ms):");
  if (G1HotCardCache::default_use_cache()) {
    _gc_par_phases[ScanHCC] = new WorkerDataArray<double>(max_gc_threads, "Scan HCC (ms):");
  } else {
    _gc_par_phases[ScanHCC] = NULL;
  }
  _gc_par_phases[ScanRS] = new WorkerDataArray<double>(max_gc_threads, "Scan RS (ms):");
  _gc_par_phases[CodeRoots] = new WorkerDataArray<double>(max_gc_threads, "Code Root Scanning (ms):");
#if INCLUDE_AOT
  _gc_par_phases[AOTCodeRoots] = new WorkerDataArray<double>(max_gc_threads, "AOT Root Scanning (ms):");
#endif
  _gc_par_phases[ObjCopy] = new WorkerDataArray<double>(max_gc_threads, "Object Copy (ms):");
  _gc_par_phases[Termination] = new WorkerDataArray<double>(max_gc_threads, "Termination (ms):");
  _gc_par_phases[GCWorkerTotal] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Total (ms):");
  _gc_par_phases[GCWorkerEnd] = new WorkerDataArray<double>(max_gc_threads, "GC Worker End (ms):");
  _gc_par_phases[Other] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Other (ms):");

  _scan_rs_scanned_cards = new WorkerDataArray<size_t>(max_gc_threads, "Scanned Cards:");
  _gc_par_phases[ScanRS]->link_thread_work_items(_scan_rs_scanned_cards, ScanRSScannedCards);
  _scan_rs_claimed_cards = new WorkerDataArray<size_t>(max_gc_threads, "Claimed Cards:");
  _gc_par_phases[ScanRS]->link_thread_work_items(_scan_rs_claimed_cards, ScanRSClaimedCards);
  _scan_rs_skipped_cards = new WorkerDataArray<size_t>(max_gc_threads, "Skipped Cards:");
  _gc_par_phases[ScanRS]->link_thread_work_items(_scan_rs_skipped_cards, ScanRSSkippedCards);

  _update_rs_processed_buffers = new WorkerDataArray<size_t>(max_gc_threads, "Processed Buffers:");
  _gc_par_phases[UpdateRS]->link_thread_work_items(_update_rs_processed_buffers, UpdateRSProcessedBuffers);
  _update_rs_scanned_cards = new WorkerDataArray<size_t>(max_gc_threads, "Scanned Cards:");
  _gc_par_phases[UpdateRS]->link_thread_work_items(_update_rs_scanned_cards, UpdateRSScannedCards);
  _update_rs_skipped_cards = new WorkerDataArray<size_t>(max_gc_threads, "Skipped Cards:");
  _gc_par_phases[UpdateRS]->link_thread_work_items(_update_rs_skipped_cards, ScanRSSkippedCards);

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

  _gc_par_phases[YoungFreeCSet] = new WorkerDataArray<double>(max_gc_threads, "Young Free Collection Set (ms):");
  _gc_par_phases[NonYoungFreeCSet] = new WorkerDataArray<double>(max_gc_threads, "Non-Young Free Collection Set (ms):");

  reset();
}

void G1GCPhaseTimes::reset() {
  _cur_collection_par_time_ms = 0.0;
  _cur_collection_code_root_fixup_time_ms = 0.0;
  _cur_strong_code_root_purge_time_ms = 0.0;
  _cur_evac_fail_recalc_used = 0.0;
  _cur_evac_fail_remove_self_forwards = 0.0;
  _cur_string_dedup_fixup_time_ms = 0.0;
  _cur_prepare_tlab_time_ms = 0.0;
  _cur_resize_tlab_time_ms = 0.0;
  _cur_derived_pointer_table_update_time_ms = 0.0;
  _cur_clear_ct_time_ms = 0.0;
  _cur_expand_heap_time_ms = 0.0;
  _cur_ref_proc_time_ms = 0.0;
  _cur_weak_ref_proc_time_ms = 0.0;
  _cur_collection_start_sec = 0.0;
  _root_region_scan_wait_time_ms = 0.0;
  _external_accounted_time_ms = 0.0;
  _recorded_clear_claimed_marks_time_ms = 0.0;
  _recorded_young_cset_choice_time_ms = 0.0;
  _recorded_non_young_cset_choice_time_ms = 0.0;
  _recorded_redirty_logged_cards_time_ms = 0.0;
  _recorded_preserve_cm_referents_time_ms = 0.0;
  _recorded_merge_pss_time_ms = 0.0;
  _recorded_start_new_cset_time_ms = 0.0;
  _recorded_total_free_cset_time_ms = 0.0;
  _recorded_serial_free_cset_time_ms = 0.0;
  _cur_fast_reclaim_humongous_time_ms = 0.0;
  _cur_fast_reclaim_humongous_register_time_ms = 0.0;
  _cur_fast_reclaim_humongous_total = 0;
  _cur_fast_reclaim_humongous_candidates = 0;
  _cur_fast_reclaim_humongous_reclaimed = 0;
  _cur_verify_before_time_ms = 0.0;
  _cur_verify_after_time_ms = 0.0;

  for (int i = 0; i < GCParPhasesSentinel; i++) {
    if (_gc_par_phases[i] != NULL) {
      _gc_par_phases[i]->reset();
    }
  }

  _ref_phase_times.reset();
}

void G1GCPhaseTimes::note_gc_start() {
  _gc_start_counter = os::elapsed_counter();
  reset();
}

#define ASSERT_PHASE_UNINITIALIZED(phase) \
    assert(_gc_par_phases[phase] == NULL || _gc_par_phases[phase]->get(i) == uninitialized, "Phase " #phase " reported for thread that was not started");

double G1GCPhaseTimes::worker_time(GCParPhases phase, uint worker) {
  if (_gc_par_phases[phase] == NULL) {
    return 0.0;
  }
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

      double worker_known_time = worker_time(ExtRootScan, i) +
                                 worker_time(ScanHCC, i) +
                                 worker_time(UpdateRS, i) +
                                 worker_time(ScanRS, i) +
                                 worker_time(CodeRoots, i) +
                                 worker_time(ObjCopy, i) +
                                 worker_time(Termination, i);

      record_time_secs(Other, i, total_worker_time - worker_known_time);
    } else {
      // Make sure all slots are uninitialized since this thread did not seem to have been started
      ASSERT_PHASE_UNINITIALIZED(GCWorkerEnd);
      ASSERT_PHASE_UNINITIALIZED(ExtRootScan);
      ASSERT_PHASE_UNINITIALIZED(ScanHCC);
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

void G1GCPhaseTimes::record_or_add_objcopy_time_secs(uint worker_i, double secs) {
  if (_gc_par_phases[ObjCopy]->get(worker_i) == _gc_par_phases[ObjCopy]->uninitialized()) {
    record_time_secs(ObjCopy, worker_i, secs);
  } else {
    add_time_secs(ObjCopy, worker_i, secs);
  }
}

void G1GCPhaseTimes::record_thread_work_item(GCParPhases phase, uint worker_i, size_t count, uint index) {
  _gc_par_phases[phase]->set_thread_work_item(worker_i, count, index);
}

// return the average time for a phase in milliseconds
double G1GCPhaseTimes::average_time_ms(GCParPhases phase) {
  return _gc_par_phases[phase]->average() * 1000.0;
}

size_t G1GCPhaseTimes::sum_thread_work_items(GCParPhases phase, uint index) {
  assert(_gc_par_phases[phase]->thread_work_items(index) != NULL, "No sub count");
  return _gc_par_phases[phase]->thread_work_items(index)->sum();
}

template <class T>
void G1GCPhaseTimes::details(T* phase, const char* indent) const {
  LogTarget(Trace, gc, phases, task) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print("%s", indent);
    phase->print_details_on(&ls);
  }
}

void G1GCPhaseTimes::log_phase(WorkerDataArray<double>* phase, uint indent, outputStream* out, bool print_sum) const {
  out->print("%s", Indents[indent]);
  phase->print_summary_on(out, print_sum);
  details(phase, Indents[indent]);

  for (uint i = 0; i < phase->MaxThreadWorkItems; i++) {
    WorkerDataArray<size_t>* work_items = phase->thread_work_items(i);
    if (work_items != NULL) {
      out->print("%s", Indents[indent + 1]);
      work_items->print_summary_on(out, true);
      details(work_items, Indents[indent + 1]);
    }
  }
}

void G1GCPhaseTimes::debug_phase(WorkerDataArray<double>* phase) const {
  LogTarget(Debug, gc, phases) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    log_phase(phase, 2, &ls, true);
  }
}

void G1GCPhaseTimes::trace_phase(WorkerDataArray<double>* phase, bool print_sum) const {
  LogTarget(Trace, gc, phases) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    log_phase(phase, 3, &ls, print_sum);
  }
}

#define TIME_FORMAT "%.1lfms"

void G1GCPhaseTimes::info_time(const char* name, double value) const {
  log_info(gc, phases)("%s%s: " TIME_FORMAT, Indents[1], name, value);
}

void G1GCPhaseTimes::debug_time(const char* name, double value) const {
  log_debug(gc, phases)("%s%s: " TIME_FORMAT, Indents[2], name, value);
}

void G1GCPhaseTimes::debug_time_for_reference(const char* name, double value) const {
  LogTarget(Debug, gc, phases) lt;
  LogTarget(Debug, gc, phases, ref) lt2;

  if (lt.is_enabled()) {
    LogStream ls(lt);
    ls.print_cr("%s%s: " TIME_FORMAT, Indents[2], name, value);
  } else if (lt2.is_enabled()) {
    LogStream ls(lt2);
    ls.print_cr("%s%s: " TIME_FORMAT, Indents[2], name, value);
  }
}

void G1GCPhaseTimes::trace_time(const char* name, double value) const {
  log_trace(gc, phases)("%s%s: " TIME_FORMAT, Indents[3], name, value);
}

void G1GCPhaseTimes::trace_count(const char* name, size_t value) const {
  log_trace(gc, phases)("%s%s: " SIZE_FORMAT, Indents[3], name, value);
}

double G1GCPhaseTimes::print_pre_evacuate_collection_set() const {
  const double sum_ms = _root_region_scan_wait_time_ms +
                        _recorded_young_cset_choice_time_ms +
                        _recorded_non_young_cset_choice_time_ms +
                        _cur_fast_reclaim_humongous_register_time_ms +
                        _recorded_clear_claimed_marks_time_ms;

  info_time("Pre Evacuate Collection Set", sum_ms);

  if (_root_region_scan_wait_time_ms > 0.0) {
    debug_time("Root Region Scan Waiting", _root_region_scan_wait_time_ms);
  }
  debug_time("Prepare TLABs", _cur_prepare_tlab_time_ms);
  debug_time("Choose Collection Set", (_recorded_young_cset_choice_time_ms + _recorded_non_young_cset_choice_time_ms));
  if (G1EagerReclaimHumongousObjects) {
    debug_time("Humongous Register", _cur_fast_reclaim_humongous_register_time_ms);
    trace_count("Humongous Total", _cur_fast_reclaim_humongous_total);
    trace_count("Humongous Candidate", _cur_fast_reclaim_humongous_candidates);
  }

  if (_recorded_clear_claimed_marks_time_ms > 0.0) {
    debug_time("Clear Claimed Marks", _recorded_clear_claimed_marks_time_ms);
  }
  return sum_ms;
}

double G1GCPhaseTimes::print_evacuate_collection_set() const {
  const double sum_ms = _cur_collection_par_time_ms;

  info_time("Evacuate Collection Set", sum_ms);

  trace_phase(_gc_par_phases[GCWorkerStart], false);
  debug_phase(_gc_par_phases[ExtRootScan]);
  for (int i = ThreadRoots; i <= SATBFiltering; i++) {
    trace_phase(_gc_par_phases[i]);
  }
  debug_phase(_gc_par_phases[UpdateRS]);
  if (G1HotCardCache::default_use_cache()) {
    trace_phase(_gc_par_phases[ScanHCC]);
  }
  debug_phase(_gc_par_phases[ScanRS]);
  debug_phase(_gc_par_phases[CodeRoots]);
#if INCLUDE_AOT
  debug_phase(_gc_par_phases[AOTCodeRoots]);
#endif
  debug_phase(_gc_par_phases[ObjCopy]);
  debug_phase(_gc_par_phases[Termination]);
  debug_phase(_gc_par_phases[Other]);
  debug_phase(_gc_par_phases[GCWorkerTotal]);
  trace_phase(_gc_par_phases[GCWorkerEnd], false);

  return sum_ms;
}

double G1GCPhaseTimes::print_post_evacuate_collection_set() const {
  const double evac_fail_handling = _cur_evac_fail_recalc_used +
                                    _cur_evac_fail_remove_self_forwards;
  const double sum_ms = evac_fail_handling +
                        _cur_collection_code_root_fixup_time_ms +
                        _recorded_preserve_cm_referents_time_ms +
                        _cur_ref_proc_time_ms +
                        _cur_weak_ref_proc_time_ms +
                        _cur_clear_ct_time_ms +
                        _recorded_merge_pss_time_ms +
                        _cur_strong_code_root_purge_time_ms +
                        _recorded_redirty_logged_cards_time_ms +
                        _recorded_total_free_cset_time_ms +
                        _cur_fast_reclaim_humongous_time_ms +
                        _cur_expand_heap_time_ms +
                        _cur_string_dedup_fixup_time_ms;

  info_time("Post Evacuate Collection Set", sum_ms);

  debug_time("Code Roots Fixup", _cur_collection_code_root_fixup_time_ms);

  debug_time("Clear Card Table", _cur_clear_ct_time_ms);

  debug_time_for_reference("Reference Processing", _cur_ref_proc_time_ms);
  _ref_phase_times.print_all_references(2, false);

  debug_time("Weak Processing", _cur_weak_ref_proc_time_ms);

  if (G1StringDedup::is_enabled()) {
    debug_time("String Dedup Fixup", _cur_string_dedup_fixup_time_ms);
    debug_phase(_gc_par_phases[StringDedupQueueFixup]);
    debug_phase(_gc_par_phases[StringDedupTableFixup]);
  }

  if (G1CollectedHeap::heap()->evacuation_failed()) {
    debug_time("Evacuation Failure", evac_fail_handling);
    trace_time("Recalculate Used", _cur_evac_fail_recalc_used);
    trace_time("Remove Self Forwards",_cur_evac_fail_remove_self_forwards);
  }

  debug_time("Merge Per-Thread State", _recorded_merge_pss_time_ms);
  debug_time("Code Roots Purge", _cur_strong_code_root_purge_time_ms);

  debug_time("Redirty Cards", _recorded_redirty_logged_cards_time_ms);
  trace_phase(_gc_par_phases[RedirtyCards]);
#if COMPILER2_OR_JVMCI
  debug_time("DerivedPointerTable Update", _cur_derived_pointer_table_update_time_ms);
#endif

  debug_time("Free Collection Set", _recorded_total_free_cset_time_ms);
  trace_time("Free Collection Set Serial", _recorded_serial_free_cset_time_ms);
  trace_phase(_gc_par_phases[YoungFreeCSet]);
  trace_phase(_gc_par_phases[NonYoungFreeCSet]);

  if (G1EagerReclaimHumongousObjects) {
    debug_time("Humongous Reclaim", _cur_fast_reclaim_humongous_time_ms);
    trace_count("Humongous Reclaimed", _cur_fast_reclaim_humongous_reclaimed);
  }
  debug_time("Start New Collection Set", _recorded_start_new_cset_time_ms);
  if (UseTLAB && ResizeTLAB) {
    debug_time("Resize TLABs", _cur_resize_tlab_time_ms);
  }
  debug_time("Expand Heap After Collection", _cur_expand_heap_time_ms);


  return sum_ms;
}

void G1GCPhaseTimes::print_other(double accounted_ms) const {
  info_time("Other", _gc_pause_time_ms - accounted_ms);
}

void G1GCPhaseTimes::print() {
  note_gc_end();

  if (_cur_verify_before_time_ms > 0.0) {
    debug_time("Verify Before", _cur_verify_before_time_ms);
  }

  double accounted_ms = 0.0;
  accounted_ms += print_pre_evacuate_collection_set();
  accounted_ms += print_evacuate_collection_set();
  accounted_ms += print_post_evacuate_collection_set();
  print_other(accounted_ms);

  if (_cur_verify_after_time_ms > 0.0) {
    debug_time("Verify After", _cur_verify_after_time_ms);
  }
}

G1EvacPhaseWithTrimTimeTracker::G1EvacPhaseWithTrimTimeTracker(G1ParScanThreadState* pss, Tickspan& total_time, Tickspan& trim_time) :
  _pss(pss),
  _start(Ticks::now()),
  _total_time(total_time),
  _trim_time(trim_time),
  _stopped(false) {

  assert(_pss->trim_ticks().value() == 0, "Possibly remaining trim ticks left over from previous use");
}

G1EvacPhaseWithTrimTimeTracker::~G1EvacPhaseWithTrimTimeTracker() {
  if (!_stopped) {
    stop();
  }
}

void G1EvacPhaseWithTrimTimeTracker::stop() {
  assert(!_stopped, "Should only be called once");
  _total_time += (Ticks::now() - _start) - _pss->trim_ticks();
  _trim_time += _pss->trim_ticks();
  _pss->reset_trim_ticks();
  _stopped = true;
}

G1GCParPhaseTimesTracker::G1GCParPhaseTimesTracker(G1GCPhaseTimes* phase_times, G1GCPhaseTimes::GCParPhases phase, uint worker_id) :
    _phase_times(phase_times), _phase(phase), _worker_id(worker_id) {
  if (_phase_times != NULL) {
    _start_time = Ticks::now();
  }
}

G1GCParPhaseTimesTracker::~G1GCParPhaseTimesTracker() {
  if (_phase_times != NULL) {
    _phase_times->record_time_secs(_phase, _worker_id, (Ticks::now() - _start_time).seconds());
  }
}

G1EvacPhaseTimesTracker::G1EvacPhaseTimesTracker(G1GCPhaseTimes* phase_times,
                                                 G1ParScanThreadState* pss,
                                                 G1GCPhaseTimes::GCParPhases phase,
                                                 uint worker_id) :
  G1GCParPhaseTimesTracker(phase_times, phase, worker_id),
  _total_time(),
  _trim_time(),
  _trim_tracker(pss, _total_time, _trim_time) {
}

G1EvacPhaseTimesTracker::~G1EvacPhaseTimesTracker() {
  if (_phase_times != NULL) {
    // Explicitly stop the trim tracker since it's not yet destructed.
    _trim_tracker.stop();
    // Exclude trim time by increasing the start time.
    _start_time += _trim_time;
    _phase_times->record_or_add_objcopy_time_secs(_worker_id, _trim_time.seconds());
  }
}


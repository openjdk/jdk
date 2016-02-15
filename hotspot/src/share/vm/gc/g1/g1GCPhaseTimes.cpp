/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "logging/log.hpp"
#include "runtime/timer.hpp"
#include "runtime/os.hpp"

// Helper class for avoiding interleaved logging
class LineBuffer: public StackObj {

private:
  static const int BUFFER_LEN = 1024;
  static const int INDENT_CHARS = 3;
  char _buffer[BUFFER_LEN];
  int _indent_level;
  int _cur;

  void vappend(const char* format, va_list ap)  ATTRIBUTE_PRINTF(2, 0) {
    int res = vsnprintf(&_buffer[_cur], BUFFER_LEN - _cur, format, ap);
    if (res != -1) {
      _cur += res;
    } else {
      DEBUG_ONLY(warning("buffer too small in LineBuffer");)
      _buffer[BUFFER_LEN -1] = 0;
      _cur = BUFFER_LEN; // vsnprintf above should not add to _buffer if we are called again
    }
  }

public:
  explicit LineBuffer(int indent_level): _indent_level(indent_level), _cur(0) {
    for (; (_cur < BUFFER_LEN && _cur < (_indent_level * INDENT_CHARS)); _cur++) {
      _buffer[_cur] = ' ';
    }
  }

#ifndef PRODUCT
  ~LineBuffer() {
    assert(_cur == _indent_level * INDENT_CHARS, "pending data in buffer - append_and_print_cr() not called?");
  }
#endif

  void append(const char* format, ...)  ATTRIBUTE_PRINTF(2, 3) {
    va_list ap;
    va_start(ap, format);
    vappend(format, ap);
    va_end(ap);
  }

  const char* to_string() {
    _cur = _indent_level * INDENT_CHARS;
    return _buffer;
  }
};

static const char* Indents[4] = {"", "   ", "      ", "         "};

G1GCPhaseTimes::G1GCPhaseTimes(uint max_gc_threads) :
  _max_gc_threads(max_gc_threads)
{
  assert(max_gc_threads > 0, "Must have some GC threads");

  _gc_par_phases[GCWorkerStart] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Start:", false, 2);
  _gc_par_phases[ExtRootScan] = new WorkerDataArray<double>(max_gc_threads, "Ext Root Scanning:", true, 2);

  // Root scanning phases
  _gc_par_phases[ThreadRoots] = new WorkerDataArray<double>(max_gc_threads, "Thread Roots:", true, 3);
  _gc_par_phases[StringTableRoots] = new WorkerDataArray<double>(max_gc_threads, "StringTable Roots:", true, 3);
  _gc_par_phases[UniverseRoots] = new WorkerDataArray<double>(max_gc_threads, "Universe Roots:", true, 3);
  _gc_par_phases[JNIRoots] = new WorkerDataArray<double>(max_gc_threads, "JNI Handles Roots:", true, 3);
  _gc_par_phases[ObjectSynchronizerRoots] = new WorkerDataArray<double>(max_gc_threads, "ObjectSynchronizer Roots:", true, 3);
  _gc_par_phases[FlatProfilerRoots] = new WorkerDataArray<double>(max_gc_threads, "FlatProfiler Roots:", true, 3);
  _gc_par_phases[ManagementRoots] = new WorkerDataArray<double>(max_gc_threads, "Management Roots:", true, 3);
  _gc_par_phases[SystemDictionaryRoots] = new WorkerDataArray<double>(max_gc_threads, "SystemDictionary Roots:", true, 3);
  _gc_par_phases[CLDGRoots] = new WorkerDataArray<double>(max_gc_threads, "CLDG Roots:", true, 3);
  _gc_par_phases[JVMTIRoots] = new WorkerDataArray<double>(max_gc_threads, "JVMTI Roots:", true, 3);
  _gc_par_phases[CMRefRoots] = new WorkerDataArray<double>(max_gc_threads, "CM RefProcessor Roots:", true, 3);
  _gc_par_phases[WaitForStrongCLD] = new WorkerDataArray<double>(max_gc_threads, "Wait For Strong CLD:", true, 3);
  _gc_par_phases[WeakCLDRoots] = new WorkerDataArray<double>(max_gc_threads, "Weak CLD Roots:", true, 3);
  _gc_par_phases[SATBFiltering] = new WorkerDataArray<double>(max_gc_threads, "SATB Filtering:", true, 3);

  _gc_par_phases[UpdateRS] = new WorkerDataArray<double>(max_gc_threads, "Update RS:", true, 2);
  _gc_par_phases[ScanHCC] = new WorkerDataArray<double>(max_gc_threads, "Scan HCC:", true, 3);
  _gc_par_phases[ScanHCC]->set_enabled(ConcurrentG1Refine::hot_card_cache_enabled());
  _gc_par_phases[ScanRS] = new WorkerDataArray<double>(max_gc_threads, "Scan RS:", true, 2);
  _gc_par_phases[CodeRoots] = new WorkerDataArray<double>(max_gc_threads, "Code Root Scanning:", true, 2);
  _gc_par_phases[ObjCopy] = new WorkerDataArray<double>(max_gc_threads, "Object Copy:", true, 2);
  _gc_par_phases[Termination] = new WorkerDataArray<double>(max_gc_threads, "Termination:", true, 2);
  _gc_par_phases[GCWorkerTotal] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Total:", true, 2);
  _gc_par_phases[GCWorkerEnd] = new WorkerDataArray<double>(max_gc_threads, "GC Worker End:", false, 2);
  _gc_par_phases[Other] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Other:", true, 2);

  _update_rs_processed_buffers = new WorkerDataArray<size_t>(max_gc_threads, "Processed Buffers:", true, 3);
  _gc_par_phases[UpdateRS]->link_thread_work_items(_update_rs_processed_buffers);

  _termination_attempts = new WorkerDataArray<size_t>(max_gc_threads, "Termination Attempts:", true, 3);
  _gc_par_phases[Termination]->link_thread_work_items(_termination_attempts);

  _gc_par_phases[StringDedupQueueFixup] = new WorkerDataArray<double>(max_gc_threads, "Queue Fixup:", true, 2);
  _gc_par_phases[StringDedupTableFixup] = new WorkerDataArray<double>(max_gc_threads, "Table Fixup:", true, 2);

  _gc_par_phases[RedirtyCards] = new WorkerDataArray<double>(max_gc_threads, "Parallel Redirty:", true, 3);
  _redirtied_cards = new WorkerDataArray<size_t>(max_gc_threads, "Redirtied Cards:", true, 3);
  _gc_par_phases[RedirtyCards]->link_thread_work_items(_redirtied_cards);
}

void G1GCPhaseTimes::note_gc_start(uint active_gc_threads) {
  assert(active_gc_threads > 0, "The number of threads must be > 0");
  assert(active_gc_threads <= _max_gc_threads, "The number of active threads must be <= the max number of threads");
  _gc_start_counter = os::elapsed_counter();
  _active_gc_threads = active_gc_threads;
  _cur_expand_heap_time_ms = 0.0;
  _external_accounted_time_ms = 0.0;

  for (int i = 0; i < GCParPhasesSentinel; i++) {
    _gc_par_phases[i]->reset();
  }

  _gc_par_phases[StringDedupQueueFixup]->set_enabled(G1StringDedup::is_enabled());
  _gc_par_phases[StringDedupTableFixup]->set_enabled(G1StringDedup::is_enabled());
}

void G1GCPhaseTimes::note_gc_end() {
  _gc_pause_time_ms = TimeHelper::counter_to_millis(os::elapsed_counter() - _gc_start_counter);
  for (uint i = 0; i < _active_gc_threads; i++) {
    double worker_time = _gc_par_phases[GCWorkerEnd]->get(i) - _gc_par_phases[GCWorkerStart]->get(i);
    record_time_secs(GCWorkerTotal, i , worker_time);

    double worker_known_time =
        _gc_par_phases[ExtRootScan]->get(i) +
        _gc_par_phases[SATBFiltering]->get(i) +
        _gc_par_phases[UpdateRS]->get(i) +
        _gc_par_phases[ScanRS]->get(i) +
        _gc_par_phases[CodeRoots]->get(i) +
        _gc_par_phases[ObjCopy]->get(i) +
        _gc_par_phases[Termination]->get(i);

    record_time_secs(Other, i, worker_time - worker_known_time);
  }

  for (int i = 0; i < GCParPhasesSentinel; i++) {
    _gc_par_phases[i]->verify(_active_gc_threads);
  }
}

void G1GCPhaseTimes::print_stats(const char* indent, const char* str, double value) {
  log_debug(gc, phases)("%s%s: %.1lf ms", indent, str, value);
}

double G1GCPhaseTimes::accounted_time_ms() {
    // First subtract any externally accounted time
    double misc_time_ms = _external_accounted_time_ms;

    // Subtract the root region scanning wait time. It's initialized to
    // zero at the start of the pause.
    misc_time_ms += _root_region_scan_wait_time_ms;

    misc_time_ms += _cur_collection_par_time_ms;

    // Now subtract the time taken to fix up roots in generated code
    misc_time_ms += _cur_collection_code_root_fixup_time_ms;

    // Strong code root purge time
    misc_time_ms += _cur_strong_code_root_purge_time_ms;

    if (G1StringDedup::is_enabled()) {
      // String dedup fixup time
      misc_time_ms += _cur_string_dedup_fixup_time_ms;
    }

    // Subtract the time taken to clean the card table from the
    // current value of "other time"
    misc_time_ms += _cur_clear_ct_time_ms;

    // Remove expand heap time from "other time"
    misc_time_ms += _cur_expand_heap_time_ms;

    return misc_time_ms;
}

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
  return _gc_par_phases[phase]->average(_active_gc_threads) * 1000.0;
}

double G1GCPhaseTimes::get_time_ms(GCParPhases phase, uint worker_i) {
  return _gc_par_phases[phase]->get(worker_i) * 1000.0;
}

double G1GCPhaseTimes::sum_time_ms(GCParPhases phase) {
  return _gc_par_phases[phase]->sum(_active_gc_threads) * 1000.0;
}

double G1GCPhaseTimes::min_time_ms(GCParPhases phase) {
  return _gc_par_phases[phase]->minimum(_active_gc_threads) * 1000.0;
}

double G1GCPhaseTimes::max_time_ms(GCParPhases phase) {
  return _gc_par_phases[phase]->maximum(_active_gc_threads) * 1000.0;
}

size_t G1GCPhaseTimes::get_thread_work_item(GCParPhases phase, uint worker_i) {
  assert(_gc_par_phases[phase]->thread_work_items() != NULL, "No sub count");
  return _gc_par_phases[phase]->thread_work_items()->get(worker_i);
}

size_t G1GCPhaseTimes::sum_thread_work_items(GCParPhases phase) {
  assert(_gc_par_phases[phase]->thread_work_items() != NULL, "No sub count");
  return _gc_par_phases[phase]->thread_work_items()->sum(_active_gc_threads);
}

double G1GCPhaseTimes::average_thread_work_items(GCParPhases phase) {
  assert(_gc_par_phases[phase]->thread_work_items() != NULL, "No sub count");
  return _gc_par_phases[phase]->thread_work_items()->average(_active_gc_threads);
}

size_t G1GCPhaseTimes::min_thread_work_items(GCParPhases phase) {
  assert(_gc_par_phases[phase]->thread_work_items() != NULL, "No sub count");
  return _gc_par_phases[phase]->thread_work_items()->minimum(_active_gc_threads);
}

size_t G1GCPhaseTimes::max_thread_work_items(GCParPhases phase) {
  assert(_gc_par_phases[phase]->thread_work_items() != NULL, "No sub count");
  return _gc_par_phases[phase]->thread_work_items()->maximum(_active_gc_threads);
}

class G1GCParPhasePrinter : public StackObj {
  G1GCPhaseTimes* _phase_times;
 public:
  G1GCParPhasePrinter(G1GCPhaseTimes* phase_times) : _phase_times(phase_times) {}

  void print(G1GCPhaseTimes::GCParPhases phase_id) {
    WorkerDataArray<double>* phase = _phase_times->_gc_par_phases[phase_id];

    if (phase->_length == 1) {
      print_single_length(phase_id, phase);
    } else {
      print_multi_length(phase_id, phase);
    }
  }


 private:
  void print_single_length(G1GCPhaseTimes::GCParPhases phase_id, WorkerDataArray<double>* phase) {
    // No need for min, max, average and sum for only one worker
    log_debug(gc, phases)("%s%s:  %.1lf", Indents[phase->_indent_level], phase->_title, _phase_times->get_time_ms(phase_id, 0));

    WorkerDataArray<size_t>* work_items = phase->_thread_work_items;
    if (work_items != NULL) {
      log_debug(gc, phases)("%s%s:  " SIZE_FORMAT, Indents[work_items->_indent_level], work_items->_title, _phase_times->sum_thread_work_items(phase_id));
    }
  }

  void print_time_values(const char* indent, G1GCPhaseTimes::GCParPhases phase_id) {
    if (log_is_enabled(Trace, gc)) {
      LineBuffer buf(0);
      uint active_length = _phase_times->_active_gc_threads;
      for (uint i = 0; i < active_length; ++i) {
        buf.append(" %4.1lf", _phase_times->get_time_ms(phase_id, i));
      }
      const char* line = buf.to_string();
      log_trace(gc, phases)("%s%-25s%s", indent, "", line);
    }
  }

  void print_count_values(const char* indent, G1GCPhaseTimes::GCParPhases phase_id, WorkerDataArray<size_t>* thread_work_items) {
    if (log_is_enabled(Trace, gc)) {
      LineBuffer buf(0);
      uint active_length = _phase_times->_active_gc_threads;
      for (uint i = 0; i < active_length; ++i) {
        buf.append("  " SIZE_FORMAT, _phase_times->get_thread_work_item(phase_id, i));
      }
      const char* line = buf.to_string();
      log_trace(gc, phases)("%s%-25s%s", indent, "", line);
    }
  }

  void print_thread_work_items(G1GCPhaseTimes::GCParPhases phase_id, WorkerDataArray<size_t>* thread_work_items) {
    const char* indent = Indents[thread_work_items->_indent_level];

    assert(thread_work_items->_print_sum, "%s does not have print sum true even though it is a count", thread_work_items->_title);

    log_debug(gc, phases)("%s%-25s Min: " SIZE_FORMAT ", Avg: %4.1lf, Max: " SIZE_FORMAT ", Diff: " SIZE_FORMAT ", Sum: " SIZE_FORMAT,
        indent, thread_work_items->_title,
        _phase_times->min_thread_work_items(phase_id), _phase_times->average_thread_work_items(phase_id), _phase_times->max_thread_work_items(phase_id),
        _phase_times->max_thread_work_items(phase_id) - _phase_times->min_thread_work_items(phase_id), _phase_times->sum_thread_work_items(phase_id));

    print_count_values(indent, phase_id, thread_work_items);
  }

  void print_multi_length(G1GCPhaseTimes::GCParPhases phase_id, WorkerDataArray<double>* phase) {
    const char* indent = Indents[phase->_indent_level];

    if (phase->_print_sum) {
      log_debug(gc, phases)("%s%-25s Min: %4.1lf, Avg: %4.1lf, Max: %4.1lf, Diff: %4.1lf, Sum: %4.1lf",
          indent, phase->_title,
          _phase_times->min_time_ms(phase_id), _phase_times->average_time_ms(phase_id), _phase_times->max_time_ms(phase_id),
          _phase_times->max_time_ms(phase_id) - _phase_times->min_time_ms(phase_id), _phase_times->sum_time_ms(phase_id));
    } else {
      log_debug(gc, phases)("%s%-25s Min: %4.1lf, Avg: %4.1lf, Max: %4.1lf, Diff: %4.1lf",
          indent, phase->_title,
          _phase_times->min_time_ms(phase_id), _phase_times->average_time_ms(phase_id), _phase_times->max_time_ms(phase_id),
          _phase_times->max_time_ms(phase_id) - _phase_times->min_time_ms(phase_id));
    }

    print_time_values(indent, phase_id);

    if (phase->_thread_work_items != NULL) {
      print_thread_work_items(phase_id, phase->_thread_work_items);
    }
  }
};

void G1GCPhaseTimes::print() {
  note_gc_end();

  G1GCParPhasePrinter par_phase_printer(this);

  if (_root_region_scan_wait_time_ms > 0.0) {
    print_stats(Indents[1], "Root Region Scan Waiting", _root_region_scan_wait_time_ms);
  }

  print_stats(Indents[1], "Parallel Time", _cur_collection_par_time_ms);
  for (int i = 0; i <= GCMainParPhasesLast; i++) {
    par_phase_printer.print((GCParPhases) i);
  }

  print_stats(Indents[1], "Code Root Fixup", _cur_collection_code_root_fixup_time_ms);
  print_stats(Indents[1], "Code Root Purge", _cur_strong_code_root_purge_time_ms);
  if (G1StringDedup::is_enabled()) {
    print_stats(Indents[1], "String Dedup Fixup", _cur_string_dedup_fixup_time_ms);
    for (int i = StringDedupPhasesFirst; i <= StringDedupPhasesLast; i++) {
      par_phase_printer.print((GCParPhases) i);
    }
  }
  print_stats(Indents[1], "Clear CT", _cur_clear_ct_time_ms);
  print_stats(Indents[1], "Expand Heap After Collection", _cur_expand_heap_time_ms);
  double misc_time_ms = _gc_pause_time_ms - accounted_time_ms();
  print_stats(Indents[1], "Other", misc_time_ms);
  if (_cur_verify_before_time_ms > 0.0) {
    print_stats(Indents[2], "Verify Before", _cur_verify_before_time_ms);
  }
  if (G1CollectedHeap::heap()->evacuation_failed()) {
    double evac_fail_handling = _cur_evac_fail_recalc_used + _cur_evac_fail_remove_self_forwards +
      _cur_evac_fail_restore_remsets;
    print_stats(Indents[2], "Evacuation Failure", evac_fail_handling);
    log_trace(gc, phases)("%sRecalculate Used: %.1lf ms", Indents[3], _cur_evac_fail_recalc_used);
    log_trace(gc, phases)("%sRemove Self Forwards: %.1lf ms", Indents[3], _cur_evac_fail_remove_self_forwards);
    log_trace(gc, phases)("%sRestore RemSet: %.1lf ms", Indents[3], _cur_evac_fail_restore_remsets);
  }
  print_stats(Indents[2], "Choose CSet",
    (_recorded_young_cset_choice_time_ms +
    _recorded_non_young_cset_choice_time_ms));
  print_stats(Indents[2], "Ref Proc", _cur_ref_proc_time_ms);
  print_stats(Indents[2], "Ref Enq", _cur_ref_enq_time_ms);
  print_stats(Indents[2], "Redirty Cards", _recorded_redirty_logged_cards_time_ms);
  par_phase_printer.print(RedirtyCards);
  if (G1EagerReclaimHumongousObjects) {
    print_stats(Indents[2], "Humongous Register", _cur_fast_reclaim_humongous_register_time_ms);

    log_trace(gc, phases)("%sHumongous Total: " SIZE_FORMAT, Indents[3], _cur_fast_reclaim_humongous_total);
    log_trace(gc, phases)("%sHumongous Candidate: " SIZE_FORMAT, Indents[3], _cur_fast_reclaim_humongous_candidates);
    print_stats(Indents[2], "Humongous Reclaim", _cur_fast_reclaim_humongous_time_ms);
    log_trace(gc, phases)("%sHumongous Reclaimed: " SIZE_FORMAT, Indents[3], _cur_fast_reclaim_humongous_reclaimed);
  }
  print_stats(Indents[2], "Free CSet",
    (_recorded_young_free_cset_time_ms +
    _recorded_non_young_free_cset_time_ms));
  log_trace(gc, phases)("%sYoung Free CSet: %.1lf ms", Indents[3], _recorded_young_free_cset_time_ms);
  log_trace(gc, phases)("%sNon-Young Free CSet: %.1lf ms", Indents[3], _recorded_non_young_free_cset_time_ms);
  if (_cur_verify_after_time_ms > 0.0) {
    print_stats(Indents[2], "Verify After", _cur_verify_after_time_ms);
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


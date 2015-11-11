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
#include "gc/g1/g1Log.hpp"
#include "gc/g1/g1StringDedup.hpp"
#include "gc/g1/workerDataArray.inline.hpp"
#include "memory/allocation.hpp"
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

  void print_cr() {
    gclog_or_tty->print_cr("%s", _buffer);
    _cur = _indent_level * INDENT_CHARS;
  }

  void append_and_print_cr(const char* format, ...)  ATTRIBUTE_PRINTF(2, 3) {
    va_list ap;
    va_start(ap, format);
    vappend(format, ap);
    va_end(ap);
    print_cr();
  }
};

G1GCPhaseTimes::G1GCPhaseTimes(uint max_gc_threads) :
  _max_gc_threads(max_gc_threads)
{
  assert(max_gc_threads > 0, "Must have some GC threads");

  _gc_par_phases[GCWorkerStart] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Start (ms)", false, G1Log::LevelFiner, 2);
  _gc_par_phases[ExtRootScan] = new WorkerDataArray<double>(max_gc_threads, "Ext Root Scanning (ms)", true, G1Log::LevelFiner, 2);

  // Root scanning phases
  _gc_par_phases[ThreadRoots] = new WorkerDataArray<double>(max_gc_threads, "Thread Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[StringTableRoots] = new WorkerDataArray<double>(max_gc_threads, "StringTable Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[UniverseRoots] = new WorkerDataArray<double>(max_gc_threads, "Universe Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[JNIRoots] = new WorkerDataArray<double>(max_gc_threads, "JNI Handles Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[ObjectSynchronizerRoots] = new WorkerDataArray<double>(max_gc_threads, "ObjectSynchronizer Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[FlatProfilerRoots] = new WorkerDataArray<double>(max_gc_threads, "FlatProfiler Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[ManagementRoots] = new WorkerDataArray<double>(max_gc_threads, "Management Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[SystemDictionaryRoots] = new WorkerDataArray<double>(max_gc_threads, "SystemDictionary Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[CLDGRoots] = new WorkerDataArray<double>(max_gc_threads, "CLDG Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[JVMTIRoots] = new WorkerDataArray<double>(max_gc_threads, "JVMTI Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[CMRefRoots] = new WorkerDataArray<double>(max_gc_threads, "CM RefProcessor Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[WaitForStrongCLD] = new WorkerDataArray<double>(max_gc_threads, "Wait For Strong CLD (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[WeakCLDRoots] = new WorkerDataArray<double>(max_gc_threads, "Weak CLD Roots (ms)", true, G1Log::LevelFinest, 3);
  _gc_par_phases[SATBFiltering] = new WorkerDataArray<double>(max_gc_threads, "SATB Filtering (ms)", true, G1Log::LevelFinest, 3);

  _gc_par_phases[UpdateRS] = new WorkerDataArray<double>(max_gc_threads, "Update RS (ms)", true, G1Log::LevelFiner, 2);
  _gc_par_phases[ScanHCC] = new WorkerDataArray<double>(max_gc_threads, "Scan HCC (ms)", true, G1Log::LevelFiner, 3);
  _gc_par_phases[ScanHCC]->set_enabled(ConcurrentG1Refine::hot_card_cache_enabled());
  _gc_par_phases[ScanRS] = new WorkerDataArray<double>(max_gc_threads, "Scan RS (ms)", true, G1Log::LevelFiner, 2);
  _gc_par_phases[CodeRoots] = new WorkerDataArray<double>(max_gc_threads, "Code Root Scanning (ms)", true, G1Log::LevelFiner, 2);
  _gc_par_phases[ObjCopy] = new WorkerDataArray<double>(max_gc_threads, "Object Copy (ms)", true, G1Log::LevelFiner, 2);
  _gc_par_phases[Termination] = new WorkerDataArray<double>(max_gc_threads, "Termination (ms)", true, G1Log::LevelFiner, 2);
  _gc_par_phases[GCWorkerTotal] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Total (ms)", true, G1Log::LevelFiner, 2);
  _gc_par_phases[GCWorkerEnd] = new WorkerDataArray<double>(max_gc_threads, "GC Worker End (ms)", false, G1Log::LevelFiner, 2);
  _gc_par_phases[Other] = new WorkerDataArray<double>(max_gc_threads, "GC Worker Other (ms)", true, G1Log::LevelFiner, 2);

  _update_rs_processed_buffers = new WorkerDataArray<size_t>(max_gc_threads, "Processed Buffers", true, G1Log::LevelFiner, 3);
  _gc_par_phases[UpdateRS]->link_thread_work_items(_update_rs_processed_buffers);

  _termination_attempts = new WorkerDataArray<size_t>(max_gc_threads, "Termination Attempts", true, G1Log::LevelFinest, 3);
  _gc_par_phases[Termination]->link_thread_work_items(_termination_attempts);

  _gc_par_phases[StringDedupQueueFixup] = new WorkerDataArray<double>(max_gc_threads, "Queue Fixup (ms)", true, G1Log::LevelFiner, 2);
  _gc_par_phases[StringDedupTableFixup] = new WorkerDataArray<double>(max_gc_threads, "Table Fixup (ms)", true, G1Log::LevelFiner, 2);

  _gc_par_phases[RedirtyCards] = new WorkerDataArray<double>(max_gc_threads, "Parallel Redirty", true, G1Log::LevelFinest, 3);
  _redirtied_cards = new WorkerDataArray<size_t>(max_gc_threads, "Redirtied Cards", true, G1Log::LevelFinest, 3);
  _gc_par_phases[RedirtyCards]->link_thread_work_items(_redirtied_cards);
}

void G1GCPhaseTimes::note_gc_start(uint active_gc_threads) {
  assert(active_gc_threads > 0, "The number of threads must be > 0");
  assert(active_gc_threads <= _max_gc_threads, "The number of active threads must be <= the max number of threads");
  _active_gc_threads = active_gc_threads;
  _cur_expand_heap_time_ms = 0.0;

  for (int i = 0; i < GCParPhasesSentinel; i++) {
    _gc_par_phases[i]->reset();
  }

  _gc_par_phases[StringDedupQueueFixup]->set_enabled(G1StringDedup::is_enabled());
  _gc_par_phases[StringDedupTableFixup]->set_enabled(G1StringDedup::is_enabled());
}

void G1GCPhaseTimes::note_gc_end() {
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

void G1GCPhaseTimes::print_stats(int level, const char* str, double value) {
  LineBuffer(level).append_and_print_cr("[%s: %.1lf ms]", str, value);
}

void G1GCPhaseTimes::print_stats(int level, const char* str, size_t value) {
  LineBuffer(level).append_and_print_cr("[%s: " SIZE_FORMAT "]", str, value);
}

void G1GCPhaseTimes::print_stats(int level, const char* str, double value, uint workers) {
  LineBuffer(level).append_and_print_cr("[%s: %.1lf ms, GC Workers: %u]", str, value, workers);
}

double G1GCPhaseTimes::accounted_time_ms() {
    // Subtract the root region scanning wait time. It's initialized to
    // zero at the start of the pause.
    double misc_time_ms = _root_region_scan_wait_time_ms;

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

    if (phase->_log_level > G1Log::level() || !phase->_enabled) {
      return;
    }

    if (phase->_length == 1) {
      print_single_length(phase_id, phase);
    } else {
      print_multi_length(phase_id, phase);
    }
  }

 private:

  void print_single_length(G1GCPhaseTimes::GCParPhases phase_id, WorkerDataArray<double>* phase) {
    // No need for min, max, average and sum for only one worker
    LineBuffer buf(phase->_indent_level);
    buf.append_and_print_cr("[%s:  %.1lf]", phase->_title, _phase_times->get_time_ms(phase_id, 0));

    if (phase->_thread_work_items != NULL) {
      LineBuffer buf2(phase->_thread_work_items->_indent_level);
      buf2.append_and_print_cr("[%s:  " SIZE_FORMAT "]", phase->_thread_work_items->_title, _phase_times->sum_thread_work_items(phase_id));
    }
  }

  void print_time_values(LineBuffer& buf, G1GCPhaseTimes::GCParPhases phase_id, WorkerDataArray<double>* phase) {
    uint active_length = _phase_times->_active_gc_threads;
    for (uint i = 0; i < active_length; ++i) {
      buf.append("  %.1lf", _phase_times->get_time_ms(phase_id, i));
    }
    buf.print_cr();
  }

  void print_count_values(LineBuffer& buf, G1GCPhaseTimes::GCParPhases phase_id, WorkerDataArray<size_t>* thread_work_items) {
    uint active_length = _phase_times->_active_gc_threads;
    for (uint i = 0; i < active_length; ++i) {
      buf.append("  " SIZE_FORMAT, _phase_times->get_thread_work_item(phase_id, i));
    }
    buf.print_cr();
  }

  void print_thread_work_items(G1GCPhaseTimes::GCParPhases phase_id, WorkerDataArray<size_t>* thread_work_items) {
    LineBuffer buf(thread_work_items->_indent_level);
    buf.append("[%s:", thread_work_items->_title);

    if (G1Log::finest()) {
      print_count_values(buf, phase_id, thread_work_items);
    }

    assert(thread_work_items->_print_sum, "%s does not have print sum true even though it is a count", thread_work_items->_title);

    buf.append_and_print_cr(" Min: " SIZE_FORMAT ", Avg: %.1lf, Max: " SIZE_FORMAT ", Diff: " SIZE_FORMAT ", Sum: " SIZE_FORMAT "]",
        _phase_times->min_thread_work_items(phase_id), _phase_times->average_thread_work_items(phase_id), _phase_times->max_thread_work_items(phase_id),
        _phase_times->max_thread_work_items(phase_id) - _phase_times->min_thread_work_items(phase_id), _phase_times->sum_thread_work_items(phase_id));
  }

  void print_multi_length(G1GCPhaseTimes::GCParPhases phase_id, WorkerDataArray<double>* phase) {
    LineBuffer buf(phase->_indent_level);
    buf.append("[%s:", phase->_title);

    if (G1Log::finest()) {
      print_time_values(buf, phase_id, phase);
    }

    buf.append(" Min: %.1lf, Avg: %.1lf, Max: %.1lf, Diff: %.1lf",
        _phase_times->min_time_ms(phase_id), _phase_times->average_time_ms(phase_id), _phase_times->max_time_ms(phase_id),
        _phase_times->max_time_ms(phase_id) - _phase_times->min_time_ms(phase_id));

    if (phase->_print_sum) {
      // for things like the start and end times the sum is not
      // that relevant
      buf.append(", Sum: %.1lf", _phase_times->sum_time_ms(phase_id));
    }

    buf.append_and_print_cr("]");

    if (phase->_thread_work_items != NULL) {
      print_thread_work_items(phase_id, phase->_thread_work_items);
    }
  }
};

void G1GCPhaseTimes::print(double pause_time_sec) {
  note_gc_end();

  G1GCParPhasePrinter par_phase_printer(this);

  if (_root_region_scan_wait_time_ms > 0.0) {
    print_stats(1, "Root Region Scan Waiting", _root_region_scan_wait_time_ms);
  }

  print_stats(1, "Parallel Time", _cur_collection_par_time_ms, _active_gc_threads);
  for (int i = 0; i <= GCMainParPhasesLast; i++) {
    par_phase_printer.print((GCParPhases) i);
  }

  print_stats(1, "Code Root Fixup", _cur_collection_code_root_fixup_time_ms);
  print_stats(1, "Code Root Purge", _cur_strong_code_root_purge_time_ms);
  if (G1StringDedup::is_enabled()) {
    print_stats(1, "String Dedup Fixup", _cur_string_dedup_fixup_time_ms, _active_gc_threads);
    for (int i = StringDedupPhasesFirst; i <= StringDedupPhasesLast; i++) {
      par_phase_printer.print((GCParPhases) i);
    }
  }
  print_stats(1, "Clear CT", _cur_clear_ct_time_ms);
  print_stats(1, "Expand Heap After Collection", _cur_expand_heap_time_ms);

  double misc_time_ms = pause_time_sec * MILLIUNITS - accounted_time_ms();
  print_stats(1, "Other", misc_time_ms);
  if (_cur_verify_before_time_ms > 0.0) {
    print_stats(2, "Verify Before", _cur_verify_before_time_ms);
  }
  if (G1CollectedHeap::heap()->evacuation_failed()) {
    double evac_fail_handling = _cur_evac_fail_recalc_used + _cur_evac_fail_remove_self_forwards +
      _cur_evac_fail_restore_remsets;
    print_stats(2, "Evacuation Failure", evac_fail_handling);
    if (G1Log::finest()) {
      print_stats(3, "Recalculate Used", _cur_evac_fail_recalc_used);
      print_stats(3, "Remove Self Forwards", _cur_evac_fail_remove_self_forwards);
      print_stats(3, "Restore RemSet", _cur_evac_fail_restore_remsets);
    }
  }
  print_stats(2, "Choose CSet",
    (_recorded_young_cset_choice_time_ms +
    _recorded_non_young_cset_choice_time_ms));
  print_stats(2, "Ref Proc", _cur_ref_proc_time_ms);
  print_stats(2, "Ref Enq", _cur_ref_enq_time_ms);
  print_stats(2, "Redirty Cards", _recorded_redirty_logged_cards_time_ms);
  par_phase_printer.print(RedirtyCards);
  if (G1EagerReclaimHumongousObjects) {
    print_stats(2, "Humongous Register", _cur_fast_reclaim_humongous_register_time_ms);
    if (G1Log::finest()) {
      print_stats(3, "Humongous Total", _cur_fast_reclaim_humongous_total);
      print_stats(3, "Humongous Candidate", _cur_fast_reclaim_humongous_candidates);
    }
    print_stats(2, "Humongous Reclaim", _cur_fast_reclaim_humongous_time_ms);
    if (G1Log::finest()) {
      print_stats(3, "Humongous Reclaimed", _cur_fast_reclaim_humongous_reclaimed);
    }
  }
  print_stats(2, "Free CSet",
    (_recorded_young_free_cset_time_ms +
    _recorded_non_young_free_cset_time_ms));
  if (G1Log::finest()) {
    print_stats(3, "Young Free CSet", _recorded_young_free_cset_time_ms);
    print_stats(3, "Non-Young Free CSet", _recorded_non_young_free_cset_time_ms);
  }
  if (_cur_verify_after_time_ms > 0.0) {
    print_stats(2, "Verify After", _cur_verify_after_time_ms);
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


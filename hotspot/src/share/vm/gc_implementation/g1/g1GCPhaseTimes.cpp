/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1GCPhaseTimes.hpp"
#include "gc_implementation/g1/g1Log.hpp"

// Helper class for avoiding interleaved logging
class LineBuffer: public StackObj {

private:
  static const int BUFFER_LEN = 1024;
  static const int INDENT_CHARS = 3;
  char _buffer[BUFFER_LEN];
  int _indent_level;
  int _cur;

  void vappend(const char* format, va_list ap) {
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

  void append(const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    vappend(format, ap);
    va_end(ap);
  }

  void append_and_print_cr(const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    vappend(format, ap);
    va_end(ap);
    gclog_or_tty->print_cr("%s", _buffer);
    _cur = _indent_level * INDENT_CHARS;
  }
};

G1GCPhaseTimes::G1GCPhaseTimes(uint max_gc_threads) :
  _max_gc_threads(max_gc_threads),
  _min_clear_cc_time_ms(-1.0),
  _max_clear_cc_time_ms(-1.0),
  _cur_clear_cc_time_ms(0.0),
  _cum_clear_cc_time_ms(0.0),
  _num_cc_clears(0L)
{
  assert(max_gc_threads > 0, "Must have some GC threads");
  _par_last_gc_worker_start_times_ms = new double[_max_gc_threads];
  _par_last_ext_root_scan_times_ms = new double[_max_gc_threads];
  _par_last_satb_filtering_times_ms = new double[_max_gc_threads];
  _par_last_update_rs_times_ms = new double[_max_gc_threads];
  _par_last_update_rs_processed_buffers = new double[_max_gc_threads];
  _par_last_scan_rs_times_ms = new double[_max_gc_threads];
  _par_last_obj_copy_times_ms = new double[_max_gc_threads];
  _par_last_termination_times_ms = new double[_max_gc_threads];
  _par_last_termination_attempts = new double[_max_gc_threads];
  _par_last_gc_worker_end_times_ms = new double[_max_gc_threads];
  _par_last_gc_worker_times_ms = new double[_max_gc_threads];
  _par_last_gc_worker_other_times_ms = new double[_max_gc_threads];
}

void G1GCPhaseTimes::note_gc_start(double pause_start_time_sec, uint active_gc_threads,
  bool is_young_gc, bool is_initial_mark_gc, GCCause::Cause gc_cause) {
  assert(active_gc_threads > 0, "The number of threads must be > 0");
  assert(active_gc_threads <= _max_gc_threads, "The number of active threads must be <= the max nubmer of threads");
  _active_gc_threads = active_gc_threads;
  _pause_start_time_sec = pause_start_time_sec;
  _is_young_gc = is_young_gc;
  _is_initial_mark_gc = is_initial_mark_gc;
  _gc_cause = gc_cause;

#ifdef ASSERT
  // initialise the timing data to something well known so that we can spot
  // if something is not set properly

  for (uint i = 0; i < _max_gc_threads; ++i) {
    _par_last_gc_worker_start_times_ms[i] = -1234.0;
    _par_last_ext_root_scan_times_ms[i] = -1234.0;
    _par_last_satb_filtering_times_ms[i] = -1234.0;
    _par_last_update_rs_times_ms[i] = -1234.0;
    _par_last_update_rs_processed_buffers[i] = -1234.0;
    _par_last_scan_rs_times_ms[i] = -1234.0;
    _par_last_obj_copy_times_ms[i] = -1234.0;
    _par_last_termination_times_ms[i] = -1234.0;
    _par_last_termination_attempts[i] = -1234.0;
    _par_last_gc_worker_end_times_ms[i] = -1234.0;
    _par_last_gc_worker_times_ms[i] = -1234.0;
    _par_last_gc_worker_other_times_ms[i] = -1234.0;
  }
#endif
}

void G1GCPhaseTimes::note_gc_end(double pause_end_time_sec) {
  if (G1Log::fine()) {
    double pause_time_ms = (pause_end_time_sec - _pause_start_time_sec) * MILLIUNITS;

    for (uint i = 0; i < _active_gc_threads; i++) {
      _par_last_gc_worker_times_ms[i] = _par_last_gc_worker_end_times_ms[i] -
        _par_last_gc_worker_start_times_ms[i];

      double worker_known_time = _par_last_ext_root_scan_times_ms[i] +
        _par_last_satb_filtering_times_ms[i] +
        _par_last_update_rs_times_ms[i] +
        _par_last_scan_rs_times_ms[i] +
        _par_last_obj_copy_times_ms[i] +
        _par_last_termination_times_ms[i];

      _par_last_gc_worker_other_times_ms[i] = _par_last_gc_worker_times_ms[i] -
        worker_known_time;
    }

    print(pause_time_ms);
  }

}

void G1GCPhaseTimes::print_par_stats(int level,
                                        const char* str,
                                        double* data,
                                        bool showDecimals) {
  double min = data[0], max = data[0];
  double total = 0.0;
  LineBuffer buf(level);
  buf.append("[%s (ms):", str);
  for (uint i = 0; i < _active_gc_threads; ++i) {
    double val = data[i];
    if (val < min)
      min = val;
    if (val > max)
      max = val;
    total += val;
    if (G1Log::finest()) {
      if (showDecimals) {
        buf.append("  %.1lf", val);
      } else {
        buf.append("  %d", (int)val);
      }
    }
  }

  if (G1Log::finest()) {
    buf.append_and_print_cr("");
  }
  double avg = total / (double) _active_gc_threads;
  if (showDecimals) {
    buf.append_and_print_cr(" Min: %.1lf, Avg: %.1lf, Max: %.1lf, Diff: %.1lf, Sum: %.1lf]",
      min, avg, max, max - min, total);
  } else {
    buf.append_and_print_cr(" Min: %d, Avg: %d, Max: %d, Diff: %d, Sum: %d]",
      (int)min, (int)avg, (int)max, (int)max - (int)min, (int)total);
  }
}

void G1GCPhaseTimes::print_stats(int level, const char* str, double value) {
  LineBuffer(level).append_and_print_cr("[%s: %.1lf ms]", str, value);
}

void G1GCPhaseTimes::print_stats(int level, const char* str, double value, int workers) {
  LineBuffer(level).append_and_print_cr("[%s: %.1lf ms, GC Workers: %d]", str, value, workers);
}

void G1GCPhaseTimes::print_stats(int level, const char* str, int value) {
  LineBuffer(level).append_and_print_cr("[%s: %d]", str, value);
}

double G1GCPhaseTimes::avg_value(double* data) {
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    double ret = 0.0;
    for (uint i = 0; i < _active_gc_threads; ++i) {
      ret += data[i];
    }
    return ret / (double) _active_gc_threads;
  } else {
    return data[0];
  }
}

double G1GCPhaseTimes::max_value(double* data) {
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    double ret = data[0];
    for (uint i = 1; i < _active_gc_threads; ++i) {
      if (data[i] > ret) {
        ret = data[i];
      }
    }
    return ret;
  } else {
    return data[0];
  }
}

double G1GCPhaseTimes::sum_of_values(double* data) {
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    double sum = 0.0;
    for (uint i = 0; i < _active_gc_threads; i++) {
      sum += data[i];
    }
    return sum;
  } else {
    return data[0];
  }
}

double G1GCPhaseTimes::max_sum(double* data1, double* data2) {
  double ret = data1[0] + data2[0];

  if (G1CollectedHeap::use_parallel_gc_threads()) {
    for (uint i = 1; i < _active_gc_threads; ++i) {
      double data = data1[i] + data2[i];
      if (data > ret) {
        ret = data;
      }
    }
  }
  return ret;
}

void G1GCPhaseTimes::collapse_par_times() {
    _ext_root_scan_time = avg_value(_par_last_ext_root_scan_times_ms);
    _satb_filtering_time = avg_value(_par_last_satb_filtering_times_ms);
    _update_rs_time = avg_value(_par_last_update_rs_times_ms);
    _update_rs_processed_buffers =
      sum_of_values(_par_last_update_rs_processed_buffers);
    _scan_rs_time = avg_value(_par_last_scan_rs_times_ms);
    _obj_copy_time = avg_value(_par_last_obj_copy_times_ms);
    _termination_time = avg_value(_par_last_termination_times_ms);
}

double G1GCPhaseTimes::accounted_time_ms() {
    // Subtract the root region scanning wait time. It's initialized to
    // zero at the start of the pause.
    double misc_time_ms = _root_region_scan_wait_time_ms;

    misc_time_ms += _cur_collection_par_time_ms;

    // Now subtract the time taken to fix up roots in generated code
    misc_time_ms += _cur_collection_code_root_fixup_time_ms;

    // Subtract the time taken to clean the card table from the
    // current value of "other time"
    misc_time_ms += _cur_clear_ct_time_ms;

    return misc_time_ms;
}

void G1GCPhaseTimes::print(double pause_time_ms) {

  if (PrintGCTimeStamps) {
    gclog_or_tty->stamp();
    gclog_or_tty->print(": ");
  }

  GCCauseString gc_cause_str = GCCauseString("GC pause", _gc_cause)
    .append(_is_young_gc ? " (young)" : " (mixed)")
    .append(_is_initial_mark_gc ? " (initial-mark)" : "");
  gclog_or_tty->print_cr("[%s, %3.7f secs]", (const char*)gc_cause_str, pause_time_ms / 1000.0);

  if (!G1Log::finer()) {
    return;
  }

  if (_root_region_scan_wait_time_ms > 0.0) {
    print_stats(1, "Root Region Scan Waiting", _root_region_scan_wait_time_ms);
  }
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    print_stats(1, "Parallel Time", _cur_collection_par_time_ms, _active_gc_threads);
    print_par_stats(2, "GC Worker Start", _par_last_gc_worker_start_times_ms);
    print_par_stats(2, "Ext Root Scanning", _par_last_ext_root_scan_times_ms);
    if (_satb_filtering_time > 0.0) {
      print_par_stats(2, "SATB Filtering", _par_last_satb_filtering_times_ms);
    }
    print_par_stats(2, "Update RS", _par_last_update_rs_times_ms);
    if (G1Log::finest()) {
      print_par_stats(3, "Processed Buffers", _par_last_update_rs_processed_buffers,
        false /* showDecimals */);
    }
    print_par_stats(2, "Scan RS", _par_last_scan_rs_times_ms);
    print_par_stats(2, "Object Copy", _par_last_obj_copy_times_ms);
    print_par_stats(2, "Termination", _par_last_termination_times_ms);
    if (G1Log::finest()) {
      print_par_stats(3, "Termination Attempts", _par_last_termination_attempts,
        false /* showDecimals */);
    }
    print_par_stats(2, "GC Worker Other", _par_last_gc_worker_other_times_ms);
    print_par_stats(2, "GC Worker Total", _par_last_gc_worker_times_ms);
    print_par_stats(2, "GC Worker End", _par_last_gc_worker_end_times_ms);
  } else {
    print_stats(1, "Ext Root Scanning", _ext_root_scan_time);
    if (_satb_filtering_time > 0.0) {
      print_stats(1, "SATB Filtering", _satb_filtering_time);
    }
    print_stats(1, "Update RS", _update_rs_time);
    if (G1Log::finest()) {
      print_stats(2, "Processed Buffers", (int)_update_rs_processed_buffers);
    }
    print_stats(1, "Scan RS", _scan_rs_time);
    print_stats(1, "Object Copying", _obj_copy_time);
  }
  print_stats(1, "Code Root Fixup", _cur_collection_code_root_fixup_time_ms);
  print_stats(1, "Clear CT", _cur_clear_ct_time_ms);
  if (Verbose && G1Log::finest()) {
    print_stats(1, "Cur Clear CC", _cur_clear_cc_time_ms);
    print_stats(1, "Cum Clear CC", _cum_clear_cc_time_ms);
    print_stats(1, "Min Clear CC", _min_clear_cc_time_ms);
    print_stats(1, "Max Clear CC", _max_clear_cc_time_ms);
    if (_num_cc_clears > 0) {
      print_stats(1, "Avg Clear CC", _cum_clear_cc_time_ms / ((double)_num_cc_clears));
    }
  }
  double misc_time_ms = pause_time_ms - accounted_time_ms();
  print_stats(1, "Other", misc_time_ms);
  print_stats(2, "Choose CSet",
    (_recorded_young_cset_choice_time_ms +
    _recorded_non_young_cset_choice_time_ms));
  print_stats(2, "Ref Proc", _cur_ref_proc_time_ms);
  print_stats(2, "Ref Enq", _cur_ref_enq_time_ms);
  print_stats(2, "Free CSet",
    (_recorded_young_free_cset_time_ms +
    _recorded_non_young_free_cset_time_ms));
}

void G1GCPhaseTimes::record_cc_clear_time_ms(double ms) {
  if (!(Verbose && G1Log::finest())) {
    return;
  }

  if (_min_clear_cc_time_ms < 0.0 || ms <= _min_clear_cc_time_ms) {
    _min_clear_cc_time_ms = ms;
  }
  if (_max_clear_cc_time_ms < 0.0 || ms >= _max_clear_cc_time_ms) {
    _max_clear_cc_time_ms = ms;
  }
  _cur_clear_cc_time_ms = ms;
  _cum_clear_cc_time_ms += ms;
  _num_cc_clears++;
}

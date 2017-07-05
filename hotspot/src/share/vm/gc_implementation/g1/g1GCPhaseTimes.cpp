/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

template <class T>
void WorkerDataArray<T>::print(int level, const char* title) {
  if (_length == 1) {
    // No need for min, max, average and sum for only one worker
    LineBuffer buf(level);
    buf.append("[%s:  ", title);
    buf.append(_print_format, _data[0]);
    buf.append_and_print_cr("]");
    return;
  }

  T min = _data[0];
  T max = _data[0];
  T sum = 0;

  LineBuffer buf(level);
  buf.append("[%s:", title);
  for (uint i = 0; i < _length; ++i) {
    T val = _data[i];
    min = MIN2(val, min);
    max = MAX2(val, max);
    sum += val;
    if (G1Log::finest()) {
      buf.append("  ");
      buf.append(_print_format, val);
    }
  }

  if (G1Log::finest()) {
    buf.append_and_print_cr("");
  }

  double avg = (double)sum / (double)_length;
  buf.append(" Min: ");
  buf.append(_print_format, min);
  buf.append(", Avg: ");
  buf.append("%.1lf", avg); // Always print average as a double
  buf.append(", Max: ");
  buf.append(_print_format, max);
  buf.append(", Diff: ");
  buf.append(_print_format, max - min);
  if (_print_sum) {
    // for things like the start and end times the sum is not
    // that relevant
    buf.append(", Sum: ");
    buf.append(_print_format, sum);
  }
  buf.append_and_print_cr("]");
}

#ifndef PRODUCT

template <> const int WorkerDataArray<int>::_uninitialized = -1;
template <> const double WorkerDataArray<double>::_uninitialized = -1.0;
template <> const size_t WorkerDataArray<size_t>::_uninitialized = (size_t)-1;

template <class T>
void WorkerDataArray<T>::reset() {
  for (uint i = 0; i < _length; i++) {
    _data[i] = (T)_uninitialized;
  }
}

template <class T>
void WorkerDataArray<T>::verify() {
  for (uint i = 0; i < _length; i++) {
    assert(_data[i] != _uninitialized,
        err_msg("Invalid data for worker " UINT32_FORMAT ", data: %lf, uninitialized: %lf",
            i, (double)_data[i], (double)_uninitialized));
  }
}

#endif

G1GCPhaseTimes::G1GCPhaseTimes(uint max_gc_threads) :
  _max_gc_threads(max_gc_threads),
  _last_gc_worker_start_times_ms(_max_gc_threads, "%.1lf", false),
  _last_ext_root_scan_times_ms(_max_gc_threads, "%.1lf"),
  _last_satb_filtering_times_ms(_max_gc_threads, "%.1lf"),
  _last_update_rs_times_ms(_max_gc_threads, "%.1lf"),
  _last_update_rs_processed_buffers(_max_gc_threads, "%d"),
  _last_scan_rs_times_ms(_max_gc_threads, "%.1lf"),
  _last_strong_code_root_scan_times_ms(_max_gc_threads, "%.1lf"),
  _last_strong_code_root_mark_times_ms(_max_gc_threads, "%.1lf"),
  _last_obj_copy_times_ms(_max_gc_threads, "%.1lf"),
  _last_termination_times_ms(_max_gc_threads, "%.1lf"),
  _last_termination_attempts(_max_gc_threads, SIZE_FORMAT),
  _last_gc_worker_end_times_ms(_max_gc_threads, "%.1lf", false),
  _last_gc_worker_times_ms(_max_gc_threads, "%.1lf"),
  _last_gc_worker_other_times_ms(_max_gc_threads, "%.1lf")
{
  assert(max_gc_threads > 0, "Must have some GC threads");
}

void G1GCPhaseTimes::note_gc_start(uint active_gc_threads) {
  assert(active_gc_threads > 0, "The number of threads must be > 0");
  assert(active_gc_threads <= _max_gc_threads, "The number of active threads must be <= the max nubmer of threads");
  _active_gc_threads = active_gc_threads;

  _last_gc_worker_start_times_ms.reset();
  _last_ext_root_scan_times_ms.reset();
  _last_satb_filtering_times_ms.reset();
  _last_update_rs_times_ms.reset();
  _last_update_rs_processed_buffers.reset();
  _last_scan_rs_times_ms.reset();
  _last_strong_code_root_scan_times_ms.reset();
  _last_strong_code_root_mark_times_ms.reset();
  _last_obj_copy_times_ms.reset();
  _last_termination_times_ms.reset();
  _last_termination_attempts.reset();
  _last_gc_worker_end_times_ms.reset();
  _last_gc_worker_times_ms.reset();
  _last_gc_worker_other_times_ms.reset();
}

void G1GCPhaseTimes::note_gc_end() {
  _last_gc_worker_start_times_ms.verify();
  _last_ext_root_scan_times_ms.verify();
  _last_satb_filtering_times_ms.verify();
  _last_update_rs_times_ms.verify();
  _last_update_rs_processed_buffers.verify();
  _last_scan_rs_times_ms.verify();
  _last_strong_code_root_scan_times_ms.verify();
  _last_strong_code_root_mark_times_ms.verify();
  _last_obj_copy_times_ms.verify();
  _last_termination_times_ms.verify();
  _last_termination_attempts.verify();
  _last_gc_worker_end_times_ms.verify();

  for (uint i = 0; i < _active_gc_threads; i++) {
    double worker_time = _last_gc_worker_end_times_ms.get(i) - _last_gc_worker_start_times_ms.get(i);
    _last_gc_worker_times_ms.set(i, worker_time);

    double worker_known_time = _last_ext_root_scan_times_ms.get(i) +
                               _last_satb_filtering_times_ms.get(i) +
                               _last_update_rs_times_ms.get(i) +
                               _last_scan_rs_times_ms.get(i) +
                               _last_strong_code_root_scan_times_ms.get(i) +
                               _last_strong_code_root_mark_times_ms.get(i) +
                               _last_obj_copy_times_ms.get(i) +
                               _last_termination_times_ms.get(i);

    double worker_other_time = worker_time - worker_known_time;
    _last_gc_worker_other_times_ms.set(i, worker_other_time);
  }

  _last_gc_worker_times_ms.verify();
  _last_gc_worker_other_times_ms.verify();
}

void G1GCPhaseTimes::print_stats(int level, const char* str, double value) {
  LineBuffer(level).append_and_print_cr("[%s: %.1lf ms]", str, value);
}

void G1GCPhaseTimes::print_stats(int level, const char* str, double value, int workers) {
  LineBuffer(level).append_and_print_cr("[%s: %.1lf ms, GC Workers: %d]", str, value, workers);
}

double G1GCPhaseTimes::accounted_time_ms() {
    // Subtract the root region scanning wait time. It's initialized to
    // zero at the start of the pause.
    double misc_time_ms = _root_region_scan_wait_time_ms;

    misc_time_ms += _cur_collection_par_time_ms;

    // Now subtract the time taken to fix up roots in generated code
    misc_time_ms += _cur_collection_code_root_fixup_time_ms;

    // Strong code root migration time
    misc_time_ms += _cur_strong_code_root_migration_time_ms;

    // Subtract the time taken to clean the card table from the
    // current value of "other time"
    misc_time_ms += _cur_clear_ct_time_ms;

    return misc_time_ms;
}

void G1GCPhaseTimes::print(double pause_time_sec) {
  if (_root_region_scan_wait_time_ms > 0.0) {
    print_stats(1, "Root Region Scan Waiting", _root_region_scan_wait_time_ms);
  }
  if (G1CollectedHeap::use_parallel_gc_threads()) {
    print_stats(1, "Parallel Time", _cur_collection_par_time_ms, _active_gc_threads);
    _last_gc_worker_start_times_ms.print(2, "GC Worker Start (ms)");
    _last_ext_root_scan_times_ms.print(2, "Ext Root Scanning (ms)");
    if (_last_satb_filtering_times_ms.sum() > 0.0) {
      _last_satb_filtering_times_ms.print(2, "SATB Filtering (ms)");
    }
    if (_last_strong_code_root_mark_times_ms.sum() > 0.0) {
     _last_strong_code_root_mark_times_ms.print(2, "Code Root Marking (ms)");
    }
    _last_update_rs_times_ms.print(2, "Update RS (ms)");
      _last_update_rs_processed_buffers.print(3, "Processed Buffers");
    _last_scan_rs_times_ms.print(2, "Scan RS (ms)");
    _last_strong_code_root_scan_times_ms.print(2, "Code Root Scanning (ms)");
    _last_obj_copy_times_ms.print(2, "Object Copy (ms)");
    _last_termination_times_ms.print(2, "Termination (ms)");
    if (G1Log::finest()) {
      _last_termination_attempts.print(3, "Termination Attempts");
    }
    _last_gc_worker_other_times_ms.print(2, "GC Worker Other (ms)");
    _last_gc_worker_times_ms.print(2, "GC Worker Total (ms)");
    _last_gc_worker_end_times_ms.print(2, "GC Worker End (ms)");
  } else {
    _last_ext_root_scan_times_ms.print(1, "Ext Root Scanning (ms)");
    if (_last_satb_filtering_times_ms.sum() > 0.0) {
      _last_satb_filtering_times_ms.print(1, "SATB Filtering (ms)");
    }
    if (_last_strong_code_root_mark_times_ms.sum() > 0.0) {
      _last_strong_code_root_mark_times_ms.print(1, "Code Root Marking (ms)");
    }
    _last_update_rs_times_ms.print(1, "Update RS (ms)");
      _last_update_rs_processed_buffers.print(2, "Processed Buffers");
    _last_scan_rs_times_ms.print(1, "Scan RS (ms)");
    _last_strong_code_root_scan_times_ms.print(1, "Code Root Scanning (ms)");
    _last_obj_copy_times_ms.print(1, "Object Copy (ms)");
  }
  print_stats(1, "Code Root Fixup", _cur_collection_code_root_fixup_time_ms);
  print_stats(1, "Code Root Migration", _cur_strong_code_root_migration_time_ms);
  print_stats(1, "Clear CT", _cur_clear_ct_time_ms);
  double misc_time_ms = pause_time_sec * MILLIUNITS - accounted_time_ms();
  print_stats(1, "Other", misc_time_ms);
  if (_cur_verify_before_time_ms > 0.0) {
    print_stats(2, "Verify Before", _cur_verify_before_time_ms);
  }
  print_stats(2, "Choose CSet",
    (_recorded_young_cset_choice_time_ms +
    _recorded_non_young_cset_choice_time_ms));
  print_stats(2, "Ref Proc", _cur_ref_proc_time_ms);
  print_stats(2, "Ref Enq", _cur_ref_enq_time_ms);
  print_stats(2, "Free CSet",
    (_recorded_young_free_cset_time_ms +
    _recorded_non_young_free_cset_time_ms));
  if (_cur_verify_after_time_ms > 0.0) {
    print_stats(2, "Verify After", _cur_verify_after_time_ms);
  }
}

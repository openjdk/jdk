/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_coTracker.cpp.incl"

COTracker* COTracker::_head = NULL;
double COTracker::_cpu_number = -1.0;

void
COTracker::resetPeriod(double now_sec, double vnow_sec) {
  guarantee( _enabled, "invariant" );
  _period_start_time_sec  = now_sec;
  _period_start_vtime_sec = vnow_sec;
}

void
COTracker::setConcOverhead(double time_stamp_sec,
                           double conc_overhead) {
  guarantee( _enabled, "invariant" );
  _conc_overhead  = conc_overhead;
  _time_stamp_sec = time_stamp_sec;
  if (conc_overhead > 0.001)
    _conc_overhead_seq.add(conc_overhead);
}

void
COTracker::reset(double starting_conc_overhead) {
  guarantee( _enabled, "invariant" );
  double now_sec = os::elapsedTime();
  setConcOverhead(now_sec, starting_conc_overhead);
}

void
COTracker::start() {
  guarantee( _enabled, "invariant" );
  resetPeriod(os::elapsedTime(), os::elapsedVTime());
}

void
COTracker::update(bool force_end) {
  assert( _enabled, "invariant" );
  double end_time_sec = os::elapsedTime();
  double elapsed_time_sec = end_time_sec - _period_start_time_sec;
  if (force_end || elapsed_time_sec > _update_period_sec) {
    // reached the end of the period
    double end_vtime_sec = os::elapsedVTime();
    double elapsed_vtime_sec = end_vtime_sec - _period_start_vtime_sec;

    double conc_overhead = elapsed_vtime_sec / elapsed_time_sec;

    setConcOverhead(end_time_sec, conc_overhead);
    resetPeriod(end_time_sec, end_vtime_sec);
  }
}

void
COTracker::updateForSTW(double start_sec, double end_sec) {
  if (!_enabled)
    return;

  // During a STW pause, no concurrent GC thread has done any
  // work. So, we can safely adjust the start of the current period by
  // adding the duration of the STW pause to it, so that the STW pause
  // doesn't affect the reading of the concurrent overhead (it's
  // basically like excluding the time of the STW pause from the
  // concurrent overhead calculation).

  double stw_duration_sec = end_sec - start_sec;
  guarantee( stw_duration_sec > 0.0, "invariant" );

  if (outOfDate(start_sec))
    _conc_overhead = 0.0;
  else
    _time_stamp_sec = end_sec;
  _period_start_time_sec += stw_duration_sec;
  _conc_overhead_seq = NumberSeq();

  guarantee( os::elapsedTime() > _period_start_time_sec, "invariant" );
}

double
COTracker::predConcOverhead() {
  if (_enabled) {
    // tty->print(" %1.2lf", _conc_overhead_seq.maximum());
    return _conc_overhead_seq.maximum();
  } else {
    // tty->print(" DD");
    return 0.0;
  }
}

void
COTracker::resetPred() {
  _conc_overhead_seq = NumberSeq();
}

COTracker::COTracker(int group)
    : _enabled(false),
      _group(group),
      _period_start_time_sec(-1.0),
      _period_start_vtime_sec(-1.0),
      _conc_overhead(-1.0),
      _time_stamp_sec(-1.0),
      _next(NULL) {
  // GCOverheadReportingPeriodMS indicates how frequently the
  // concurrent overhead will be recorded by the GC Overhead
  // Reporter. We want to take readings less often than that. If we
  // took readings more often than some of them might be lost.
  _update_period_sec = ((double) GCOverheadReportingPeriodMS) / 1000.0 * 1.25;
  _next = _head;
  _head = this;

  if (_cpu_number < 0.0)
    _cpu_number = (double) os::processor_count();
}

// statics

void
COTracker::updateAllForSTW(double start_sec, double end_sec) {
  for (COTracker* curr = _head; curr != NULL; curr = curr->_next) {
    curr->updateForSTW(start_sec, end_sec);
  }
}

double
COTracker::totalConcOverhead(double now_sec) {
  double total_conc_overhead = 0.0;

  for (COTracker* curr = _head; curr != NULL; curr = curr->_next) {
    double conc_overhead = curr->concOverhead(now_sec);
    total_conc_overhead += conc_overhead;
  }

  return total_conc_overhead;
}

double
COTracker::totalConcOverhead(double now_sec,
                             size_t group_num,
                             double* co_per_group) {
  double total_conc_overhead = 0.0;

  for (size_t i = 0; i < group_num; ++i)
    co_per_group[i] = 0.0;

  for (COTracker* curr = _head; curr != NULL; curr = curr->_next) {
    size_t group = curr->_group;
    assert( 0 <= group && group < group_num, "invariant" );
    double conc_overhead = curr->concOverhead(now_sec);

    co_per_group[group] += conc_overhead;
    total_conc_overhead += conc_overhead;
  }

  return total_conc_overhead;
}

double
COTracker::totalPredConcOverhead() {
  double total_pred_conc_overhead = 0.0;
  for (COTracker* curr = _head; curr != NULL; curr = curr->_next) {
    total_pred_conc_overhead += curr->predConcOverhead();
    curr->resetPred();
  }
  return total_pred_conc_overhead / _cpu_number;
}

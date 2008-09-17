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
# include "incls/_gcOverheadReporter.cpp.incl"

class COReportingThread : public ConcurrentGCThread {
private:
  GCOverheadReporter* _reporter;

public:
  COReportingThread(GCOverheadReporter* reporter) : _reporter(reporter) {
    guarantee( _reporter != NULL, "precondition" );
    create_and_start();
  }

  virtual void run() {
    initialize_in_thread();
    wait_for_universe_init();

    int period_ms = GCOverheadReportingPeriodMS;

    while ( true ) {
      os::sleep(Thread::current(), period_ms, false);

      _sts.join();
      double now_sec = os::elapsedTime();
      _reporter->collect_and_record_conc_overhead(now_sec);
      _sts.leave();
    }

    terminate();
  }
};

GCOverheadReporter* GCOverheadReporter::_reporter = NULL;

GCOverheadReporter::GCOverheadReporter(size_t group_num,
                                       const char* group_names[],
                                       size_t length)
    : _group_num(group_num), _prev_end_sec(0.0) {
  guarantee( 0 <= group_num && group_num <= MaxGCOverheadGroupNum,
             "precondition" );

  _base = NEW_C_HEAP_ARRAY(GCOverheadReporterEntry, length);
  _top  = _base + length;
  _curr = _base;

  for (size_t i = 0; i < group_num; ++i) {
    guarantee( group_names[i] != NULL, "precondition" );
    _group_names[i] = group_names[i];
  }
}

void
GCOverheadReporter::add(double start_sec, double end_sec,
                        double* conc_overhead,
                        double stw_overhead) {
  assert( _curr <= _top, "invariant" );

  if (_curr == _top) {
    guarantee( false, "trace full" );
    return;
  }

  _curr->_start_sec       = start_sec;
  _curr->_end_sec         = end_sec;
  for (size_t i = 0; i < _group_num; ++i) {
    _curr->_conc_overhead[i] =
      (conc_overhead != NULL) ? conc_overhead[i] : 0.0;
  }
  _curr->_stw_overhead    = stw_overhead;

  ++_curr;
}

void
GCOverheadReporter::collect_and_record_conc_overhead(double end_sec) {
  double start_sec = _prev_end_sec;
  guarantee( end_sec > start_sec, "invariant" );

  double conc_overhead[MaxGCOverheadGroupNum];
  COTracker::totalConcOverhead(end_sec, _group_num, conc_overhead);
  add_conc_overhead(start_sec, end_sec, conc_overhead);
  _prev_end_sec = end_sec;
}

void
GCOverheadReporter::record_stw_start(double start_sec) {
  guarantee( start_sec > _prev_end_sec, "invariant" );
  collect_and_record_conc_overhead(start_sec);
}

void
GCOverheadReporter::record_stw_end(double end_sec) {
  double start_sec = _prev_end_sec;
  COTracker::updateAllForSTW(start_sec, end_sec);
  add_stw_overhead(start_sec, end_sec, 1.0);

  _prev_end_sec = end_sec;
}

void
GCOverheadReporter::print() const {
  tty->print_cr("");
  tty->print_cr("GC Overhead (%d entries)", _curr - _base);
  tty->print_cr("");
  GCOverheadReporterEntry* curr = _base;
  while (curr < _curr) {
    double total = curr->_stw_overhead;
    for (size_t i = 0; i < _group_num; ++i)
      total += curr->_conc_overhead[i];

    tty->print("OVERHEAD %12.8lf %12.8lf ",
               curr->_start_sec, curr->_end_sec);

    for (size_t i = 0; i < _group_num; ++i)
      tty->print("%s %12.8lf ", _group_names[i], curr->_conc_overhead[i]);

    tty->print_cr("STW %12.8lf TOT %12.8lf", curr->_stw_overhead, total);
    ++curr;
  }
  tty->print_cr("");
}

// statics

void
GCOverheadReporter::initGCOverheadReporter(size_t group_num,
                                           const char* group_names[]) {
  guarantee( _reporter == NULL, "should only be called once" );
  guarantee( 0 <= group_num && group_num <= MaxGCOverheadGroupNum,
             "precondition" );
  guarantee( group_names != NULL, "pre-condition" );

  if (GCOverheadReporting) {
    _reporter = new GCOverheadReporter(group_num, group_names);
    new COReportingThread(_reporter);
  }
}

void
GCOverheadReporter::recordSTWStart(double start_sec) {
  if (_reporter != NULL)
    _reporter->record_stw_start(start_sec);
}

void
GCOverheadReporter::recordSTWEnd(double end_sec) {
  if (_reporter != NULL)
    _reporter->record_stw_end(end_sec);
}

void
GCOverheadReporter::printGCOverhead() {
  if (_reporter != NULL)
    _reporter->print();
}

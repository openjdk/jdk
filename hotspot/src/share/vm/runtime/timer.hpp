/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_TIMER_HPP
#define SHARE_VM_RUNTIME_TIMER_HPP

#include "utilities/globalDefinitions.hpp"

// Timers for simple measurement.

class elapsedTimer VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;
 private:
  jlong _counter;
  jlong _start_counter;
  bool  _active;
 public:
  elapsedTimer()             { _active = false; reset(); }
  elapsedTimer(jlong time, jlong timeUnitsPerSecond);
  void add(elapsedTimer t);
  void start();
  void stop();
  void reset()               { _counter = 0; }
  double seconds() const;
  jlong milliseconds() const;
  jlong ticks() const        { return _counter; }
  jlong active_ticks() const;
  bool  is_active() const { return _active; }
};

// TimeStamp is used for recording when an event took place.
class TimeStamp VALUE_OBJ_CLASS_SPEC {
 private:
  jlong _counter;
 public:
  TimeStamp()  { _counter = 0; }
  void clear() { _counter = 0; }
  // has the timestamp been updated since being created or cleared?
  bool is_updated() const { return _counter != 0; }
  // update to current elapsed time
  void update();
  // update to given elapsed time
  void update_to(jlong ticks);
  // returns seconds since updated
  // (must not be in a cleared state:  must have been previously updated)
  double seconds() const;
  jlong milliseconds() const;
  // ticks elapsed between VM start and last update
  jlong ticks() const { return _counter; }
  // ticks elapsed since last update
  jlong ticks_since_update() const;
};

// TraceTime is used for tracing the execution time of a block
// Usage:
//  { TraceTime t("block time")
//    some_code();
//  }
//

class TraceTime: public StackObj {
 private:
  bool          _active;    // do timing
  bool          _verbose;   // report every timing
  elapsedTimer  _t;         // timer
  elapsedTimer* _accum;     // accumulator
 public:
  // Constructors
  TraceTime(const char* title,
            bool doit = true);
  TraceTime(const char* title,
            elapsedTimer* accumulator,
            bool doit = true,
            bool verbose = false);
  ~TraceTime();

  // Accessors
  void set_verbose(bool verbose)  { _verbose = verbose; }
  bool verbose() const            { return _verbose;    }

  // Activation
  void suspend()  { if (_active) _t.stop();  }
  void resume()   { if (_active) _t.start(); }
};

class TraceCPUTime: public StackObj {
 private:
  bool _active;                 // true if times will be measured and printed
  bool _print_cr;               // if true print carriage return at end
  double _starting_user_time;   // user time at start of measurement
  double _starting_system_time; // system time at start of measurement
  double _starting_real_time;   // real time at start of measurement
  outputStream* _logfile;       // output is printed to this stream
  bool _error;                  // true if an error occurred, turns off output

 public:
  TraceCPUTime(bool doit = true,
               bool print_cr = true,
               outputStream *logfile = NULL);
  ~TraceCPUTime();
};

class TimeHelper {
 public:
  static double counter_to_seconds(jlong counter);
  static double counter_to_millis(jlong counter);
};

#endif // SHARE_VM_RUNTIME_TIMER_HPP

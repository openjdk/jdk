/*
 * Copyright (c) 2001, 2009, Oracle and/or its affiliates. All rights reserved.
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

// Keeps track of the GC work and decides when it is OK to do GC work
// and for how long so that the MMU invariants are maintained.

/***** ALL TIMES ARE IN SECS!!!!!!! *****/

// this is the "interface"
class G1MMUTracker: public CHeapObj {
protected:
  double          _time_slice;
  double          _max_gc_time; // this is per time slice

public:
  G1MMUTracker(double time_slice, double max_gc_time);

  virtual void add_pause(double start, double end, bool gc_thread) = 0;
  virtual double longest_pause(double current_time) = 0;
  virtual double when_sec(double current_time, double pause_time) = 0;

  double max_gc_time() {
    return _max_gc_time;
  }

  inline bool now_max_gc(double current_time) {
    return when_sec(current_time, max_gc_time()) < 0.00001;
  }

  inline double when_max_gc_sec(double current_time) {
    return when_sec(current_time, max_gc_time());
  }

  inline jlong when_max_gc_ms(double current_time) {
    double when = when_max_gc_sec(current_time);
    return (jlong) (when * 1000.0);
  }

  inline jlong when_ms(double current_time, double pause_time) {
    double when = when_sec(current_time, pause_time);
    return (jlong) (when * 1000.0);
  }
};

class G1MMUTrackerQueueElem VALUE_OBJ_CLASS_SPEC {
private:
  double _start_time;
  double _end_time;

public:
  inline double start_time() { return _start_time; }
  inline double end_time()   { return _end_time; }
  inline double duration()   { return _end_time - _start_time; }

  G1MMUTrackerQueueElem() {
    _start_time = 0.0;
    _end_time   = 0.0;
  }

  G1MMUTrackerQueueElem(double start_time, double end_time) {
    _start_time = start_time;
    _end_time   = end_time;
  }
};

// this is an implementation of the MMUTracker using a (fixed-size) queue
// that keeps track of all the recent pause times
class G1MMUTrackerQueue: public G1MMUTracker {
private:
  enum PrivateConstants {
    QueueLength = 64
  };

  // The array keeps track of all the pauses that fall within a time
  // slice (the last time slice during which pauses took place).
  // The data structure implemented is a circular queue.
  // Head "points" to the most recent addition, tail to the oldest one.
  // The array is of fixed size and I don't think we'll need more than
  // two or three entries with the current behaviour of G1 pauses.
  // If the array is full, an easy fix is to look for the pauses with
  // the shortest gap between them and consolidate them.
  // For now, we have taken the expedient alternative of forgetting
  // the oldest entry in the event that +G1UseFixedWindowMMUTracker, thus
  // potentially violating MMU specs for some time thereafter.

  G1MMUTrackerQueueElem _array[QueueLength];
  int                   _head_index;
  int                   _tail_index;
  int                   _no_entries;

  inline int trim_index(int index) {
    return (index + QueueLength) % QueueLength;
  }

  void remove_expired_entries(double current_time);
  double calculate_gc_time(double current_time);

  double longest_pause_internal(double current_time);
  double when_internal(double current_time, double pause_time);

public:
  G1MMUTrackerQueue(double time_slice, double max_gc_time);

  virtual void add_pause(double start, double end, bool gc_thread);

  virtual double longest_pause(double current_time);
  virtual double when_sec(double current_time, double pause_time);
};

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

// COTracker keeps track of the concurrent overhead of a GC thread.

// A thread that needs to be tracked must, itself, start up its
// tracker with the start() method and then call the update() method
// at regular intervals. What the tracker does is to calculate the
// concurrent overhead of a process at a given update period. The
// tracker starts and when is detects that it has exceeded the given
// period, it calculates the duration of the period in wall-clock time
// and the duration of the period in vtime (i.e. how much time the
// concurrent processes really took up during this period). The ratio
// of the latter over the former is the concurrent overhead of that
// process for that period over a single CPU. This overhead is stored
// on the tracker, "timestamped" with the wall-clock time of the end
// of the period. When the concurrent overhead of this process needs
// to be queried, this last "reading" provides a good approximation
// (we assume that the concurrent overhead of a particular thread
// stays largely constant over time). The timestamp is necessary to
// detect when the process has stopped working and the recorded
// reading hasn't been updated for some time.

// Each concurrent GC thread is considered to be part of a "group"
// (i.e. any available concurrent marking threads are part of the
// "concurrent marking thread group"). A COTracker is associated with
// a single group at construction-time. It's up to each collector to
// decide how groups will be mapped to such an id (ids should start
// from 0 and be consecutive; there's a hardcoded max group num
// defined on the GCOverheadTracker class). The notion of a group has
// been introduced to be able to identify how much overhead was
// imposed by each group, instead of getting a single value that
// covers all concurrent overhead.

class COTracker {
private:
  // It indicates whether this tracker is enabled or not. When the
  // tracker is disabled, then it returns 0.0 as the latest concurrent
  // overhead and several methods (reset, start, and update) are not
  // supposed to be called on it. This enabling / disabling facility
  // is really provided to make a bit more explicit in the code when a
  // particulary tracker of a processes that doesn't run all the time
  // (e.g. concurrent marking) is supposed to be used and not it's not.
  bool               _enabled;

  // The ID of the group associated with this tracker.
  int                _group;

  // The update period of the tracker. A new value for the concurrent
  // overhead of the associated process will be made at intervals no
  // smaller than this.
  double             _update_period_sec;

  // The start times (both wall-block time and vtime) of the current
  // interval.
  double             _period_start_time_sec;
  double             _period_start_vtime_sec;

  // Number seq of the concurrent overhead readings within a period
  NumberSeq          _conc_overhead_seq;

  // The latest reading of the concurrent overhead (over a single CPU)
  // imposed by the associated concurrent thread, made available at
  // the indicated wall-clock time.
  double             _conc_overhead;
  double             _time_stamp_sec;

  // The number of CPUs that the host machine has (for convenience
  // really, as we'd have to keep translating it into a double)
  static double      _cpu_number;

  // Fields that keep a list of all trackers created. This is useful,
  // since it allows us to sum up the concurrent overhead without
  // having to write code for a specific collector to broadcast a
  // request to all its concurrent processes.
  COTracker*         _next;
  static COTracker*  _head;

  // It indicates that a new period is starting by updating the
  // _period_start_time_sec and _period_start_vtime_sec fields.
  void resetPeriod(double now_sec, double vnow_sec);
  // It updates the latest concurrent overhead reading, taken at a
  // given wall-clock time.
  void setConcOverhead(double time_stamp_sec, double conc_overhead);

  // It determines whether the time stamp of the latest concurrent
  // overhead reading is out of date or not.
  bool outOfDate(double now_sec) {
    // The latest reading is considered out of date, if it was taken
    // 1.2x the update period.
    return (now_sec - _time_stamp_sec) > 1.2 * _update_period_sec;
  }

public:
  // The constructor which associates the tracker with a group ID.
  COTracker(int group);

  // Methods to enable / disable the tracker and query whether it is enabled.
  void enable()  { _enabled = true;  }
  void disable() { _enabled = false; }
  bool enabled() { return _enabled;  }

  // It resets the tracker and sets concurrent overhead reading to be
  // the given parameter and the associated time stamp to be now.
  void reset(double starting_conc_overhead = 0.0);
  // The tracker starts tracking. IT should only be called from the
  // concurrent thread that is tracked by this tracker.
  void start();
  // It updates the tracker and, if the current period is longer than
  // the update period, the concurrent overhead reading will be
  // updated. force_end being true indicates that it's the last call
  // to update() by this process before the tracker is disabled (the
  // tracker can be re-enabled later if necessary).  It should only be
  // called from the concurrent thread that is tracked by this tracker
  // and while the thread has joined the STS.
  void update(bool force_end = false);
  // It adjusts the contents of the tracker to take into account a STW
  // pause.
  void updateForSTW(double start_sec, double end_sec);

  // It returns the last concurrent overhead reading over a single
  // CPU. If the reading is out of date, or the tracker is disabled,
  // it returns 0.0.
  double concCPUOverhead(double now_sec) {
    if (!_enabled || outOfDate(now_sec))
      return 0.0;
    else
      return _conc_overhead;
  }

  // It returns the last concurrent overhead reading over all CPUs
  // that the host machine has. If the reading is out of date, or the
  // tracker is disabled, it returns 0.0.
  double concOverhead(double now_sec) {
    return concCPUOverhead(now_sec) / _cpu_number;
  }

  double predConcOverhead();

  void resetPred();

  // statics

  // It notifies all trackers about a STW pause.
  static void updateAllForSTW(double start_sec, double end_sec);

  // It returns the sum of the concurrent overhead readings of all
  // available (and enabled) trackers for the given time stamp. The
  // overhead is over all the CPUs of the host machine.

  static double totalConcOverhead(double now_sec);
  // Like the previous method, but it also sums up the overheads per
  // group number. The length of the co_per_group array must be at
  // least as large group_num
  static double totalConcOverhead(double now_sec,
                                  size_t group_num,
                                  double* co_per_group);

  static double totalPredConcOverhead();
};

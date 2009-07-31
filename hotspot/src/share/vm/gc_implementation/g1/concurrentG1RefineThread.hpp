/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

// Forward Decl.
class ConcurrentG1Refine;

// The G1 Concurrent Refinement Thread (could be several in the future).

class ConcurrentG1RefineThread: public ConcurrentGCThread {
  friend class VMStructs;
  friend class G1CollectedHeap;

  double _vtime_start;  // Initial virtual time.
  double _vtime_accum;  // Initial virtual time.
  int _worker_id;
  int _worker_id_offset;

  // The refinement threads collection is linked list. A predecessor can activate a successor
  // when the number of the rset update buffer crosses a certain threshold. A successor
  // would self-deactivate when the number of the buffers falls below the threshold.
  bool _active;
  ConcurrentG1RefineThread *       _next;
 public:
  virtual void run();

  bool is_active()  { return _active;  }
  void activate()   { _active = true;  }
  void deactivate() { _active = false; }

 private:
  ConcurrentG1Refine*              _cg1r;

  COTracker                        _co_tracker;
  double                           _interval_ms;

  void decreaseInterval(int processing_time_ms) {
    double min_interval_ms = (double) processing_time_ms;
    _interval_ms = 0.8 * _interval_ms;
    if (_interval_ms < min_interval_ms)
      _interval_ms = min_interval_ms;
  }
  void increaseInterval(int processing_time_ms) {
    double max_interval_ms = 9.0 * (double) processing_time_ms;
    _interval_ms = 1.1 * _interval_ms;
    if (max_interval_ms > 0 && _interval_ms > max_interval_ms)
      _interval_ms = max_interval_ms;
  }

  void sleepBeforeNextCycle();

  // For use by G1CollectedHeap, which is a friend.
  static SuspendibleThreadSet* sts() { return &_sts; }

 public:
  // Constructor
  ConcurrentG1RefineThread(ConcurrentG1Refine* cg1r, ConcurrentG1RefineThread* next,
                           int worker_id_offset, int worker_id);

  // Printing
  void print();

  // Total virtual time so far.
  double vtime_accum() { return _vtime_accum; }

  ConcurrentG1Refine* cg1r()                     { return _cg1r;     }

  void            sample_young_list_rs_lengths();

  // Yield for GC
  void            yield();

  // shutdown
  void stop();
};

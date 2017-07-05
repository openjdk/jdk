/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectorPolicy.hpp"
#include "gc/g1/g1YoungRemSetSamplingThread.hpp"
#include "gc/g1/suspendibleThreadSet.hpp"
#include "runtime/mutexLocker.hpp"

void G1YoungRemSetSamplingThread::run() {
  initialize_in_thread();
  wait_for_universe_init();

  run_service();

  terminate();
}

void G1YoungRemSetSamplingThread::stop() {
  // it is ok to take late safepoints here, if needed
  {
    MutexLockerEx mu(Terminator_lock);
    _should_terminate = true;
  }

  stop_service();

  {
    MutexLockerEx mu(Terminator_lock);
    while (!_has_terminated) {
      Terminator_lock->wait();
    }
  }
}

G1YoungRemSetSamplingThread::G1YoungRemSetSamplingThread() : ConcurrentGCThread() {
  _monitor = new Monitor(Mutex::nonleaf,
                         "G1YoungRemSetSamplingThread monitor",
                         true,
                         Monitor::_safepoint_check_never);

  set_name("G1 Young RemSet Sampling");
  create_and_start();
}

void G1YoungRemSetSamplingThread::sleep_before_next_cycle() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  if (!_should_terminate) {
    intx waitms = G1ConcRefinementServiceIntervalMillis; // 300, really should be?
    _monitor->wait(Mutex::_no_safepoint_check_flag, waitms);
  }
}

void G1YoungRemSetSamplingThread::run_service() {
  double vtime_start = os::elapsedVTime();

  while (!_should_terminate) {
    sample_young_list_rs_lengths();

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - vtime_start);
    } else {
      _vtime_accum = 0.0;
    }

    sleep_before_next_cycle();
  }
}

void G1YoungRemSetSamplingThread::stop_service() {
  MutexLockerEx x(_monitor, Mutex::_no_safepoint_check_flag);
  _monitor->notify();
}

void G1YoungRemSetSamplingThread::sample_young_list_rs_lengths() {
  SuspendibleThreadSetJoiner sts;
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1CollectorPolicy* g1p = g1h->g1_policy();
  if (g1p->adaptive_young_list_length()) {
    int regions_visited = 0;
    g1h->young_list()->rs_length_sampling_init();
    while (g1h->young_list()->rs_length_sampling_more()) {
      g1h->young_list()->rs_length_sampling_next();
      ++regions_visited;

      // we try to yield every time we visit 10 regions
      if (regions_visited == 10) {
        if (sts.should_yield()) {
          sts.yield();
          // we just abandon the iteration
          break;
        }
        regions_visited = 0;
      }
    }

    g1p->revise_young_list_target_length_if_necessary();
  }
}

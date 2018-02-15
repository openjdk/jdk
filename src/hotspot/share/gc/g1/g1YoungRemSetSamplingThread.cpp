/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1YoungRemSetSamplingThread.hpp"
#include "gc/g1/heapRegion.inline.hpp"
#include "gc/g1/heapRegionRemSet.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"
#include "runtime/mutexLocker.hpp"

G1YoungRemSetSamplingThread::G1YoungRemSetSamplingThread() :
    ConcurrentGCThread(),
    _monitor(Mutex::nonleaf,
             "G1YoungRemSetSamplingThread monitor",
             true,
             Monitor::_safepoint_check_never) {
  set_name("G1 Young RemSet Sampling");
  create_and_start();
}

void G1YoungRemSetSamplingThread::sleep_before_next_cycle() {
  MutexLockerEx x(&_monitor, Mutex::_no_safepoint_check_flag);
  if (!should_terminate()) {
    uintx waitms = G1ConcRefinementServiceIntervalMillis; // 300, really should be?
    _monitor.wait(Mutex::_no_safepoint_check_flag, waitms);
  }
}

void G1YoungRemSetSamplingThread::run_service() {
  double vtime_start = os::elapsedVTime();

  while (!should_terminate()) {
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
  MutexLockerEx x(&_monitor, Mutex::_no_safepoint_check_flag);
  _monitor.notify();
}

class G1YoungRemSetSamplingClosure : public HeapRegionClosure {
  SuspendibleThreadSetJoiner* _sts;
  size_t _regions_visited;
  size_t _sampled_rs_lengths;
public:
  G1YoungRemSetSamplingClosure(SuspendibleThreadSetJoiner* sts) :
    HeapRegionClosure(), _sts(sts), _regions_visited(0), _sampled_rs_lengths(0) { }

  virtual bool do_heap_region(HeapRegion* r) {
    size_t rs_length = r->rem_set()->occupied();
    _sampled_rs_lengths += rs_length;

    // Update the collection set policy information for this region
    G1CollectedHeap::heap()->collection_set()->update_young_region_prediction(r, rs_length);

    _regions_visited++;

    if (_regions_visited == 10) {
      if (_sts->should_yield()) {
        _sts->yield();
        // A gc may have occurred and our sampling data is stale and further
        // traversal of the collection set is unsafe
        return true;
      }
      _regions_visited = 0;
    }
    return false;
  }

  size_t sampled_rs_lengths() const { return _sampled_rs_lengths; }
};

void G1YoungRemSetSamplingThread::sample_young_list_rs_lengths() {
  SuspendibleThreadSetJoiner sts;
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1Policy* g1p = g1h->g1_policy();

  if (g1p->adaptive_young_list_length()) {
    G1YoungRemSetSamplingClosure cl(&sts);

    G1CollectionSet* g1cs = g1h->collection_set();
    g1cs->iterate(&cl);

    if (cl.is_complete()) {
      g1p->revise_young_list_target_length_if_necessary(cl.sampled_rs_lengths());
    }
  }
}

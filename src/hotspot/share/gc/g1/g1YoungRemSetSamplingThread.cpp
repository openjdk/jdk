/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/g1ConcurrentMark.inline.hpp"
#include "gc/g1/g1ConcurrentMarkThread.inline.hpp"
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
             Monitor::_safepoint_check_never),
    _last_periodic_gc_attempt_s(os::elapsedTime()),
    _vtime_accum(0) {
  set_name("G1 Young RemSet Sampling");
  create_and_start();
}

void G1YoungRemSetSamplingThread::sleep_before_next_cycle() {
  MutexLockerEx x(&_monitor, Mutex::_no_safepoint_check_flag);
  if (!should_terminate()) {
    uintx waitms = G1ConcRefinementServiceIntervalMillis;
    _monitor.wait(Mutex::_no_safepoint_check_flag, waitms);
  }
}

bool G1YoungRemSetSamplingThread::should_start_periodic_gc() {
  // If we are currently in a concurrent mark we are going to uncommit memory soon.
  if (G1CollectedHeap::heap()->concurrent_mark()->cm_thread()->during_cycle()) {
    log_debug(gc, periodic)("Concurrent cycle in progress. Skipping.");
    return false;
  }

  // Check if enough time has passed since the last GC.
  uintx time_since_last_gc = (uintx)Universe::heap()->millis_since_last_gc();
  if ((time_since_last_gc < G1PeriodicGCInterval)) {
    log_debug(gc, periodic)("Last GC occurred " UINTX_FORMAT "ms before which is below threshold " UINTX_FORMAT "ms. Skipping.",
                            time_since_last_gc, G1PeriodicGCInterval);
    return false;
  }

  // Check if load is lower than max.
  double recent_load;
  if ((G1PeriodicGCSystemLoadThreshold > 0.0f) &&
      (os::loadavg(&recent_load, 1) == -1 || recent_load > G1PeriodicGCSystemLoadThreshold)) {
    log_debug(gc, periodic)("Load %1.2f is higher than threshold %1.2f. Skipping.",
                            recent_load, G1PeriodicGCSystemLoadThreshold);
    return false;
  }

  return true;
}

void G1YoungRemSetSamplingThread::check_for_periodic_gc(){
  // If disabled, just return.
  if (G1PeriodicGCInterval == 0) {
    return;
  }
  if ((os::elapsedTime() - _last_periodic_gc_attempt_s) > (G1PeriodicGCInterval / 1000.0)) {
    log_debug(gc, periodic)("Checking for periodic GC.");
    if (should_start_periodic_gc()) {
      if (!G1CollectedHeap::heap()->try_collect(GCCause::_g1_periodic_collection,
                                                    false /* retry_on_vmop_failure */)) {
        log_debug(gc, periodic)("GC request denied. Skipping.");
      }
    }
    _last_periodic_gc_attempt_s = os::elapsedTime();
  }
}

void G1YoungRemSetSamplingThread::run_service() {
  double vtime_start = os::elapsedVTime();

  // Print a message about periodic GC configuration.
  if (G1PeriodicGCInterval != 0) {
    log_info(gc)("Periodic GC enabled with interval " UINTX_FORMAT "ms", G1PeriodicGCInterval);
  } else {
    log_info(gc)("Periodic GC disabled");
  }

  while (!should_terminate()) {
    sample_young_list_rs_lengths();

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - vtime_start);
    } else {
      _vtime_accum = 0.0;
    }

    check_for_periodic_gc();

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
  G1Policy* policy = g1h->policy();

  if (policy->use_adaptive_young_list_length()) {
    G1YoungRemSetSamplingClosure cl(&sts);

    G1CollectionSet* g1cs = g1h->collection_set();
    g1cs->iterate(&cl);

    if (cl.is_complete()) {
      policy->revise_young_list_target_length_if_necessary(cl.sampled_rs_lengths());
    }
  }
}

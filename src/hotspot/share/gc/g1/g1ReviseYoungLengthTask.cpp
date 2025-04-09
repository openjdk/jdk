/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/g1/g1ReviseYoungLengthTask.hpp"
#include "gc/g1/g1ServiceThread.hpp"
#include "gc/shared/suspendibleThreadSet.hpp"


jlong G1ReviseYoungLengthTask::reschedule_delay_ms() const {
  G1Policy* policy = G1CollectedHeap::heap()->policy();
  size_t available_bytes;
  if (policy->try_get_available_bytes_estimate(available_bytes)) {
    double predicted_time_to_next_gc_ms = policy->predict_time_to_next_gc_ms(available_bytes);

    // Use a prime number close to 50ms as minimum time, different to other components
    // that derive their wait time from the try_get_available_bytes_estimate() call
    // to minimize interference.
    uint64_t const min_wait_time_ms = 47;

    return policy->adjust_wait_time_ms(predicted_time_to_next_gc_ms, min_wait_time_ms);
  } else {
    // Failed to get estimate of available bytes. Try again asap.
    return 1;
  }
}

class G1ReviseYoungLengthTask::RemSetSamplingClosure : public G1HeapRegionClosure {
  size_t _sampled_code_root_rs_length;

public:
  RemSetSamplingClosure() : _sampled_code_root_rs_length(0) { }

  bool do_heap_region(G1HeapRegion* r) override {
    G1HeapRegionRemSet* rem_set = r->rem_set();
    _sampled_code_root_rs_length += rem_set->code_roots_list_length();
    return false;
  }

  size_t sampled_code_root_rs_length() const { return _sampled_code_root_rs_length; }
};

void G1ReviseYoungLengthTask::adjust_young_list_target_length() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1Policy* policy = g1h->policy();

  assert(policy->use_adaptive_young_list_length(), "should not call otherwise");

  size_t pending_cards;
  size_t current_to_collection_set_cards;
  {
    MutexLocker x(G1ReviseYoungLength_lock, Mutex::_no_safepoint_check_flag);
    pending_cards = policy->current_pending_cards();
    current_to_collection_set_cards = policy->current_to_collection_set_cards();
  }

  RemSetSamplingClosure cl;
  g1h->collection_set()->iterate(&cl);

  policy->revise_young_list_target_length(pending_cards,
                                          current_to_collection_set_cards,
                                          cl.sampled_code_root_rs_length());
}

G1ReviseYoungLengthTask::G1ReviseYoungLengthTask(const char* name) :
  G1ServiceTask(name) { }

void G1ReviseYoungLengthTask::execute() {
  SuspendibleThreadSetJoiner sts;

  adjust_young_list_target_length();

  schedule(reschedule_delay_ms());
}

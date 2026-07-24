/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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


#include "gc/shared/gc_globals.hpp"
#include "gc/shared/plab.hpp"
#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahController.hpp"
#include "gc/shenandoah/shenandoahHeapRegion.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"
#include "logging/log.hpp"
#include "utilities/copy.hpp"
#include "utilities/ostream.hpp"

ShenandoahCollectorPolicy::ShenandoahCollectorPolicy() :
  _success_concurrent_gcs(0),
  _abbreviated_concurrent_gcs(0),
  _success_full_gcs(0),
  _consecutive_young_gcs(0),
  _mixed_gcs(0),
  _success_old_gcs(0),
  _interrupted_old_gcs(0),
  _alloc_failure_full(0) {

  Copy::zero_to_bytes(_stall_counts, sizeof(size_t) * ShenandoahController::PHASE_LIMIT);
  Copy::zero_to_bytes(_collection_cause_counts, sizeof(size_t) * GCCause::_last_gc_cause);

  _tracer = new ShenandoahTracer();
}

void ShenandoahCollectorPolicy::record_collection_cause(GCCause::Cause cause) {
  assert(cause < GCCause::_last_gc_cause, "Invalid GCCause");
  _collection_cause_counts[cause]++;
}

void ShenandoahCollectorPolicy::record_alloc_failure_to_full() {
  _alloc_failure_full++;
}

void ShenandoahCollectorPolicy::record_allocation_stall(ShenandoahController::ShenandoahCollectorPhase phase) {
  assert(phase < ShenandoahController::PHASE_LIMIT, "Invalid phase: %d", phase);
  assert(ShenandoahController::UNSET <= phase, "Invalid phase: %d", phase);
  _stall_counts[phase].add_then_fetch(1UL);
}

void ShenandoahCollectorPolicy::record_success_concurrent(bool is_young, bool is_abbreviated) {
  update_young(is_young);

  _success_concurrent_gcs++;
  if (is_abbreviated) {
    _abbreviated_concurrent_gcs++;
  }
}

void ShenandoahCollectorPolicy::record_mixed_cycle() {
  _mixed_gcs++;
}

void ShenandoahCollectorPolicy::record_success_old() {
  _consecutive_young_gcs = 0;
  _success_old_gcs++;
}

void ShenandoahCollectorPolicy::record_interrupted_old() {
  _consecutive_young_gcs = 0;
  _interrupted_old_gcs++;
}

void ShenandoahCollectorPolicy::update_young(bool is_young) {
  if (is_young) {
    _consecutive_young_gcs++;
  } else {
    _consecutive_young_gcs = 0;
  }
}

void ShenandoahCollectorPolicy::record_success_full() {
  _consecutive_young_gcs = 0;
  _success_full_gcs++;
}

void ShenandoahCollectorPolicy::record_shutdown() {
  _in_shutdown.set();
}

bool ShenandoahCollectorPolicy::is_at_shutdown() const {
  return _in_shutdown.is_set();
}

bool ShenandoahCollectorPolicy::is_explicit_gc(GCCause::Cause cause) {
  return GCCause::is_user_requested_gc(cause)
      || GCCause::is_serviceability_requested_gc(cause)
      || cause == GCCause::_wb_full_gc
      || cause == GCCause::_wb_young_gc;
}

bool is_implicit_gc(GCCause::Cause cause) {
  return cause != GCCause::_no_gc
      && cause != GCCause::_shenandoah_concurrent_gc
      && cause != GCCause::_allocation_failure
      && !ShenandoahCollectorPolicy::is_explicit_gc(cause);
}

#ifdef ASSERT
bool is_valid_request(GCCause::Cause cause) {
  return ShenandoahCollectorPolicy::is_explicit_gc(cause)
      || ShenandoahCollectorPolicy::is_shenandoah_gc(cause)
      || cause == GCCause::_metadata_GC_clear_soft_refs
      || cause == GCCause::_codecache_GC_aggressive
      || cause == GCCause::_codecache_GC_threshold
      || cause == GCCause::_full_gc_alot
      || cause == GCCause::_wb_young_gc
      || cause == GCCause::_wb_full_gc
      || cause == GCCause::_wb_breakpoint
      || cause == GCCause::_scavenge_alot;
}
#endif

bool ShenandoahCollectorPolicy::is_shenandoah_gc(GCCause::Cause cause) {
  return cause == GCCause::_allocation_failure
      || cause == GCCause::_shenandoah_stop_vm
      || cause == GCCause::_shenandoah_allocation_failure_evac
      || cause == GCCause::_shenandoah_humongous_allocation_failure
      || cause == GCCause::_shenandoah_concurrent_gc
      || cause == GCCause::_shenandoah_upgrade_to_full_gc;
}


bool ShenandoahCollectorPolicy::is_allocation_failure(GCCause::Cause cause) {
  return cause == GCCause::_allocation_failure
      || cause == GCCause::_shenandoah_allocation_failure_evac
      || cause == GCCause::_shenandoah_humongous_allocation_failure;
}

bool ShenandoahCollectorPolicy::is_requested_gc(GCCause::Cause cause) {
  return is_explicit_gc(cause) || is_implicit_gc(cause);
}

bool ShenandoahCollectorPolicy::should_run_full_gc(GCCause::Cause cause) {
  if (cause == GCCause::_shenandoah_upgrade_to_full_gc || cause == GCCause::_shenandoah_humongous_allocation_failure) {
    return true;
  }
  return is_explicit_gc(cause) ? !ExplicitGCInvokesConcurrent : !ShenandoahImplicitGCInvokesConcurrent;
}

bool ShenandoahCollectorPolicy::should_handle_requested_gc(GCCause::Cause cause) {
  assert(is_valid_request(cause), "only requested GCs here: %s", GCCause::to_string(cause));

  if (DisableExplicitGC) {
    return !is_explicit_gc(cause);
  }
  return true;
}

bool ShenandoahCollectorPolicy::should_abandon_evacuations(ShenandoahHeapRegion* region) {
  if (region->has_self_forwards()) {
    PLAB* gclab = ShenandoahThreadLocalData::gclab(Thread::current());
    if (gclab->words_remaining() < PLAB::min_size() / HeapWordSize) {
      // This region and this thread are lost. This thread has evacuated all it can. If
      // we let it continue on to other regions, it will only fail those as well. We want
      // to let other threads try the regions that this thread could not.
      log_debug(gc, thread)("Region (%zu) has self-forwards and labs are exhausted (remaining words: %zu)",
                            region->index(), gclab->words_remaining());
      return true;
    }
  }
  return false;
}

// Some causes should not be allowed to preempt others. We must make sure that
// regulator requests and allocation failures do not preempt a shutdown request.
int ShenandoahCollectorPolicy::cause_priority(GCCause::Cause cause) {
  if (cause == GCCause::_shenandoah_stop_vm)               return 5;
  if (cause == GCCause::_shenandoah_upgrade_to_full_gc)    return 4;
  // Explicit gc will escalate an allocation failure from a young to global cycle
  if (is_explicit_gc(cause))                               return 3;
  if (is_allocation_failure(cause))                        return 2;
  if (cause == GCCause::_shenandoah_concurrent_gc)         return 1;
  if (cause == GCCause::_no_gc)                            return 0;
  // Unanticipated gc causes are treated as an allocation failure and cannot be
  // preempted by regulator requests
  return 2;
}

template<typename T>
size_t shenandoah_sum_array(T* a, size_t length) {
  size_t sum = 0;
  for (size_t i = 0; i < length; i++) {
    sum += a[i].load_relaxed();
  }
  return sum;
}

void ShenandoahCollectorPolicy::print_gc_stats(outputStream* out) const {
  out->print_cr("Under allocation pressure, concurrent cycles may cancel, and either continue cycle");
  out->print_cr("under stop-the-world pause or result in stop-the-world Full GC. Increase heap size,");
  out->print_cr("tune GC heuristics, or lower allocation rate");
  out->print_cr("to avoid Degenerated and Full GC cycles. Abbreviated cycles are those which found");
  out->print_cr("enough regions with no live objects to skip evacuation.");
  out->cr();

  size_t gc_attempts = 0;
  for (int c = 0; c < GCCause::_last_gc_cause; c++) {
    gc_attempts += _collection_cause_counts[c];
  }

  size_t completed_gcs = _success_full_gcs + _success_concurrent_gcs + _success_old_gcs;
  out->print_cr("%5zu GC attempts. %zu Completed GCs (%.2f%%).",
    gc_attempts, completed_gcs, percent_of(completed_gcs, gc_attempts));

  size_t explicit_requests = 0;
  size_t implicit_requests = 0;
  for (int c = 0; c < GCCause::_last_gc_cause; c++) {
    size_t cause_count = _collection_cause_counts[c];
    if (cause_count > 0) {
      auto cause = (GCCause::Cause) c;
      if (is_explicit_gc(cause)) {
        explicit_requests += cause_count;
      } else if (is_implicit_gc(cause)) {
        implicit_requests += cause_count;
      }
      const char* desc = GCCause::to_string(cause);
      out->print_cr("  %5zu caused by %s (%.2f%%)", cause_count, desc, percent_of(cause_count, gc_attempts));
    }
  }

  out->cr();
  out->print_cr("%5zu Successful Concurrent GCs (%.2f%%)", _success_concurrent_gcs, percent_of(_success_concurrent_gcs, completed_gcs));
  if (ExplicitGCInvokesConcurrent) {
    out->print_cr("  %5zu invoked explicitly (%.2f%%)", explicit_requests, percent_of(explicit_requests, _success_concurrent_gcs));
  }
  if (ShenandoahImplicitGCInvokesConcurrent) {
    out->print_cr("  %5zu invoked implicitly (%.2f%%)", implicit_requests, percent_of(implicit_requests, _success_concurrent_gcs));
  }
  out->print_cr("  %5zu abbreviated (%.2f%%)",  _abbreviated_concurrent_gcs, percent_of(_abbreviated_concurrent_gcs, _success_concurrent_gcs));
  out->cr();

  if (_success_old_gcs > 0) {
    out->print_cr("%5zu Completed Old GCs (%.2f%%)",        _success_old_gcs, percent_of(_success_old_gcs, completed_gcs));
    out->print_cr("  %5zu mixed",                        _mixed_gcs);
    out->print_cr("  %5zu interruptions",                _interrupted_old_gcs);
    out->cr();
  }

  const size_t total_stalls = shenandoah_sum_array(_stall_counts, ShenandoahController::PHASE_LIMIT);
  out->print_cr("%5zu Stalls", total_stalls);
  for (int c = 0; c < ShenandoahController::PHASE_LIMIT; c++) {
    const size_t stall_count = _stall_counts[c].load_relaxed();
    if (stall_count > 0) {
      const auto phase = static_cast<ShenandoahController::ShenandoahCollectorPhase>(c);
      const char* desc = ShenandoahController::collector_phase_to_string(phase);
      out->print_cr("    %5zu happened at %s", stall_count, desc);
    }
  }
  out->cr();

  out->print_cr("%5zu Full GCs (%.2f%%)", _success_full_gcs, percent_of(_success_full_gcs, completed_gcs));
  if (!ExplicitGCInvokesConcurrent) {
    out->print_cr("  %5zu invoked explicitly (%.2f%%)", explicit_requests, percent_of(explicit_requests, _success_full_gcs));
  }
  if (!ShenandoahImplicitGCInvokesConcurrent) {
    out->print_cr("  %5zu invoked implicitly (%.2f%%)", implicit_requests, percent_of(implicit_requests, _success_full_gcs));
  }
  out->print_cr("  %5zu caused by allocation failure (%.2f%%)", _alloc_failure_full, percent_of(_alloc_failure_full, _success_full_gcs));
}

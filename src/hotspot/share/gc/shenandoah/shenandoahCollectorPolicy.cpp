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


#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "gc/shenandoah/shenandoahGC.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "runtime/os.hpp"

ShenandoahCollectorPolicy::ShenandoahCollectorPolicy() :
  _success_concurrent_gcs(0),
  _abbreviated_concurrent_gcs(0),
  _success_degenerated_gcs(0),
  _abbreviated_degenerated_gcs(0),
  _success_full_gcs(0),
  _consecutive_degenerated_gcs(0),
  _consecutive_young_gcs(0),
  _mixed_gcs(0),
  _success_old_gcs(0),
  _interrupted_old_gcs(0),
  _alloc_failure_degenerated(0),
  _alloc_failure_degenerated_upgrade_to_full(0),
  _alloc_failure_full(0) {

  Copy::zero_to_bytes(_degen_point_counts, sizeof(size_t) * ShenandoahGC::_DEGENERATED_LIMIT);
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

void ShenandoahCollectorPolicy::record_alloc_failure_to_degenerated(ShenandoahGC::ShenandoahDegenPoint point) {
  assert(point < ShenandoahGC::_DEGENERATED_LIMIT, "sanity");
  _alloc_failure_degenerated++;
  _degen_point_counts[point]++;
}

void ShenandoahCollectorPolicy::record_degenerated_upgrade_to_full() {
  _consecutive_degenerated_gcs = 0;
  _alloc_failure_degenerated_upgrade_to_full++;
}

void ShenandoahCollectorPolicy::record_success_concurrent(bool is_young, bool is_abbreviated) {
  update_young(is_young);

  _consecutive_degenerated_gcs = 0;
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

void ShenandoahCollectorPolicy::record_success_degenerated(bool is_young, bool is_abbreviated) {
  update_young(is_young);

  _success_degenerated_gcs++;
  _consecutive_degenerated_gcs++;
  if (is_abbreviated) {
    _abbreviated_degenerated_gcs++;
  }
}

void ShenandoahCollectorPolicy::update_young(bool is_young) {
  if (is_young) {
    _consecutive_young_gcs++;
  } else {
    _consecutive_young_gcs = 0;
  }
}

void ShenandoahCollectorPolicy::record_success_full() {
  _consecutive_degenerated_gcs = 0;
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
  return is_explicit_gc(cause) ? !ExplicitGCInvokesConcurrent : !ShenandoahImplicitGCInvokesConcurrent;
}

bool ShenandoahCollectorPolicy::should_handle_requested_gc(GCCause::Cause cause) {
  assert(is_valid_request(cause), "only requested GCs here: %s", GCCause::to_string(cause));

  if (DisableExplicitGC) {
    return !is_explicit_gc(cause);
  }
  return true;
}

void ShenandoahCollectorPolicy::print_gc_stats(outputStream* out) const {
  out->print_cr("Under allocation pressure, concurrent cycles may cancel, and either continue cycle");
  out->print_cr("under stop-the-world pause or result in stop-the-world Full GC. Increase heap size,");
  out->print_cr("tune GC heuristics, or lower allocation rate");
  out->print_cr("to avoid Degenerated and Full GC cycles. Abbreviated cycles are those which found");
  out->print_cr("enough regions with no live objects to skip evacuation.");
  out->cr();

  size_t completed_gcs = _success_full_gcs + _success_degenerated_gcs + _success_concurrent_gcs + _success_old_gcs;
  out->print_cr("%5zu Completed GCs", completed_gcs);

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
      out->print_cr("  %5zu caused by %s (%.2f%%)", cause_count, desc, percent_of(cause_count, completed_gcs));
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

  if (ShenandoahHeap::heap()->mode()->is_generational()) {
    out->print_cr("%5zu Completed Old GCs (%.2f%%)",        _success_old_gcs, percent_of(_success_old_gcs, completed_gcs));
    out->print_cr("  %5zu mixed",                        _mixed_gcs);
    out->print_cr("  %5zu interruptions",                _interrupted_old_gcs);
    out->cr();
  }

  size_t degenerated_gcs = _alloc_failure_degenerated_upgrade_to_full + _success_degenerated_gcs;
  out->print_cr("%5zu Degenerated GCs (%.2f%%)", degenerated_gcs, percent_of(degenerated_gcs, completed_gcs));
  out->print_cr("  %5zu upgraded to Full GC (%.2f%%)",          _alloc_failure_degenerated_upgrade_to_full, percent_of(_alloc_failure_degenerated_upgrade_to_full, degenerated_gcs));
  out->print_cr("  %5zu caused by allocation failure (%.2f%%)", _alloc_failure_degenerated, percent_of(_alloc_failure_degenerated, degenerated_gcs));
  out->print_cr("  %5zu abbreviated (%.2f%%)",                  _abbreviated_degenerated_gcs, percent_of(_abbreviated_degenerated_gcs, degenerated_gcs));
  for (int c = 0; c < ShenandoahGC::_DEGENERATED_LIMIT; c++) {
    if (_degen_point_counts[c] > 0) {
      const char* desc = ShenandoahGC::degen_point_to_string((ShenandoahGC::ShenandoahDegenPoint)c);
      out->print_cr("    %5zu happened at %s", _degen_point_counts[c], desc);
    }
  }
  out->cr();

  out->print_cr("%5zu Full GCs (%.2f%%)", _success_full_gcs, percent_of(_success_full_gcs, completed_gcs));
  if (!ExplicitGCInvokesConcurrent) {
    out->print_cr("  %5zu invoked explicitly (%.2f%%)", explicit_requests, percent_of(explicit_requests, _success_concurrent_gcs));
  }
  if (!ShenandoahImplicitGCInvokesConcurrent) {
    out->print_cr("  %5zu invoked implicitly (%.2f%%)", implicit_requests, percent_of(implicit_requests, _success_concurrent_gcs));
  }
  out->print_cr("  %5zu caused by allocation failure (%.2f%%)", _alloc_failure_full, percent_of(_alloc_failure_full, _success_full_gcs));
  out->print_cr("  %5zu upgraded from Degenerated GC (%.2f%%)", _alloc_failure_degenerated_upgrade_to_full, percent_of(_alloc_failure_degenerated_upgrade_to_full, _success_full_gcs));
}

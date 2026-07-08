/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHCOLLECTORPOLICY_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHCOLLECTORPOLICY_HPP

#include "gc/shared/gcTrace.hpp"
#include "gc/shenandoah/shenandoahController.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "gc/shenandoah/shenandoahTrace.hpp"
#include "memory/allocation.hpp"

class ShenandoahCollectorPolicy : public CHeapObj<mtGC> {
private:
  size_t _success_concurrent_gcs;
  size_t _abbreviated_concurrent_gcs;
  // Written by control thread, read by mutators
  volatile size_t _success_full_gcs;
  volatile size_t _consecutive_young_gcs;
  size_t _mixed_gcs;
  size_t _success_old_gcs;
  size_t _interrupted_old_gcs;
  size_t _alloc_failure_full;
  size_t _collection_cause_counts[GCCause::_last_gc_cause];
  size_t _stall_counts[ShenandoahController::PHASE_LIMIT];

  ShenandoahSharedFlag _in_shutdown;
  ShenandoahTracer* _tracer;

public:

  ShenandoahCollectorPolicy();

  void record_mixed_cycle();
  void record_success_old();
  void record_interrupted_old();

  // A collection cycle may be "abbreviated" if Shenandoah finds a sufficient percentage
  // of regions that contain no live objects (ShenandoahImmediateThreshold). These cycles
  // end after final mark, skipping the evacuation and reference-updating phases. Such
  // cycles are very efficient and are worth tracking. Note that both degenerated and
  // concurrent cycles can be abbreviated.
  void record_success_concurrent(bool is_young, bool is_abbreviated);

  void record_success_full();
  void record_allocation_stall(ShenandoahController::ShenandoahCollectorPhase phase);
  void record_alloc_failure_to_full();
  void record_collection_cause(GCCause::Cause cause);

  void record_shutdown();
  bool is_at_shutdown() const;

  ShenandoahTracer* tracer() const {return _tracer;}

  void print_gc_stats(outputStream* out) const;

  size_t full_gc_count() const {
    return _success_full_gcs;
  }

  static bool is_allocation_failure(GCCause::Cause cause);
  static bool is_shenandoah_gc(GCCause::Cause cause);
  static bool is_requested_gc(GCCause::Cause cause);
  static bool is_explicit_gc(GCCause::Cause cause);
  static bool should_run_full_gc(GCCause::Cause cause);
  static bool should_handle_requested_gc(GCCause::Cause cause);

  size_t consecutive_young_gc_count() const {
    return _consecutive_young_gcs;
  }

private:
  void update_young(bool is_young);
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCOLLECTORPOLICY_HPP

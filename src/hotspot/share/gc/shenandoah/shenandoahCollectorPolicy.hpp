/*
 * Copyright (c) 2013, 2021, Red Hat, Inc. All rights reserved.
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
#include "gc/shenandoah/shenandoahGC.hpp"
#include "gc/shenandoah/shenandoahSharedVariables.hpp"
#include "memory/allocation.hpp"
#include "utilities/ostream.hpp"

class ShenandoahTracer : public GCTracer, public CHeapObj<mtGC> {
public:
  ShenandoahTracer() : GCTracer(Shenandoah) {}
};

class ShenandoahCollectorPolicy : public CHeapObj<mtGC> {
private:
  size_t _success_concurrent_gcs;
  size_t _abbreviated_concurrent_gcs;
  size_t _success_degenerated_gcs;
  size_t _abbreviated_degenerated_gcs;
  // Written by control thread, read by mutators
  volatile size_t _success_full_gcs;
  uint _consecutive_degenerated_gcs;
  size_t _alloc_failure_degenerated;
  size_t _alloc_failure_degenerated_upgrade_to_full;
  size_t _alloc_failure_full;
  size_t _explicit_concurrent;
  size_t _explicit_full;
  size_t _implicit_concurrent;
  size_t _implicit_full;
  size_t _degen_points[ShenandoahGC::_DEGENERATED_LIMIT];

  ShenandoahSharedFlag _in_shutdown;
  ShenandoahTracer* _tracer;


public:
  ShenandoahCollectorPolicy();

  // A collection cycle may be "abbreviated" if Shenandoah finds a sufficient percentage
  // of regions that contain no live objects (ShenandoahImmediateThreshold). These cycles
  // end after final mark, skipping the evacuation and reference-updating phases. Such
  // cycles are very efficient and are worth tracking. Note that both degenerated and
  // concurrent cycles can be abbreviated.
  void record_success_concurrent(bool is_abbreviated);
  void record_success_degenerated(bool is_abbreviated);
  void record_success_full();
  void record_alloc_failure_to_degenerated(ShenandoahGC::ShenandoahDegenPoint point);
  void record_alloc_failure_to_full();
  void record_degenerated_upgrade_to_full();
  void record_explicit_to_concurrent();
  void record_explicit_to_full();
  void record_implicit_to_concurrent();
  void record_implicit_to_full();

  void record_shutdown();
  bool is_at_shutdown();

  ShenandoahTracer* tracer() {return _tracer;}

  void print_gc_stats(outputStream* out) const;

  size_t full_gc_count() const {
    return _success_full_gcs + _alloc_failure_degenerated_upgrade_to_full;
  }

  // If the heuristics find that the number of consecutive degenerated cycles is above
  // ShenandoahFullGCThreshold, then they will initiate a Full GC upon an allocation
  // failure.
  inline size_t consecutive_degenerated_gc_count() const {
    return _consecutive_degenerated_gcs;
  }
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHCOLLECTORPOLICY_HPP

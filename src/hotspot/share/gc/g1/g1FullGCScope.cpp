/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1FullGCScope.hpp"
#include "gc/shared/gc_globals.hpp"

G1FullGCJFRTracerMark::G1FullGCJFRTracerMark(STWGCTimer* timer, GCTracer* tracer)
  : G1JFRTracerMark(timer, tracer) {

  G1CollectedHeap::heap()->pre_full_gc_dump(_timer);
}

G1FullGCJFRTracerMark::~G1FullGCJFRTracerMark() {
  G1CollectedHeap::heap()->post_full_gc_dump(_timer);
}

G1FullGCScope::G1FullGCScope(G1MonitoringSupport* monitoring_support,
                             bool clear_soft,
                             bool do_maximal_compaction,
                             GCTracer* tracer) :
    _should_clear_soft_refs(clear_soft),
    _do_maximal_compaction(do_maximal_compaction),
    _timer(),
    _tracer(tracer),
    _tracer_mark(&_timer, _tracer),
    _monitoring_scope(monitoring_support),
    _heap_printer(G1CollectedHeap::heap()),
    _region_compaction_threshold(do_maximal_compaction ?
                                 G1HeapRegion::GrainWords :
                                 (1 - MarkSweepDeadRatio / 100.0) * G1HeapRegion::GrainWords) { }

STWGCTimer* G1FullGCScope::timer() {
  return &_timer;
}

GCTracer* G1FullGCScope::tracer() {
  return _tracer;
}

size_t G1FullGCScope::region_compaction_threshold() const {
  return _region_compaction_threshold;
}

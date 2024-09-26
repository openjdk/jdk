/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/psParallelCompact.inline.hpp"
#include "gc/parallel/parallelScavengeHeap.inline.hpp"
#include "gc/parallel/psScavenge.hpp"
#include "gc/parallel/psVMOperations.hpp"
#include "gc/shared/gcLocker.hpp"
#include "utilities/dtrace.hpp"

// The following methods are used by the parallel scavenge collector
VM_ParallelCollectForAllocation::VM_ParallelCollectForAllocation(size_t word_size,
                                                                 bool is_tlab,
                                                                 uint gc_count) :
  VM_CollectForAllocation(word_size, gc_count, GCCause::_allocation_failure),
  _is_tlab(is_tlab) {
  assert(word_size != 0, "An allocation should always be requested with this operation.");
}

void VM_ParallelCollectForAllocation::doit() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  GCCauseSetter gccs(heap, _gc_cause);
  _result = heap->satisfy_failed_allocation(_word_size, _is_tlab);

  if (_result == nullptr && GCLocker::is_active_and_needs_gc()) {
    set_gc_locked();
  }
}

static bool is_cause_full(GCCause::Cause cause) {
  return (cause != GCCause::_gc_locker) && (cause != GCCause::_wb_young_gc)
         DEBUG_ONLY(&& (cause != GCCause::_scavenge_alot));
}

// Only used for System.gc() calls
VM_ParallelGCCollect::VM_ParallelGCCollect(uint gc_count,
                                             uint full_gc_count,
                                             GCCause::Cause gc_cause) :
  VM_GC_Operation(gc_count, gc_cause, full_gc_count, is_cause_full(gc_cause)) {}

void VM_ParallelGCCollect::doit() {
  ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();

  GCCauseSetter gccs(heap, _gc_cause);
  heap->try_collect_at_safepoint(_full);
}

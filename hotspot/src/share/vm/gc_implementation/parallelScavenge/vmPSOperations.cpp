/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/parallelScavenge/parallelScavengeHeap.inline.hpp"
#include "gc_implementation/parallelScavenge/psMarkSweep.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.inline.hpp"
#include "gc_implementation/parallelScavenge/vmPSOperations.hpp"
#include "memory/gcLocker.inline.hpp"
#include "utilities/dtrace.hpp"

// The following methods are used by the parallel scavenge collector
VM_ParallelGCFailedAllocation::VM_ParallelGCFailedAllocation(size_t size,
                                                      unsigned int gc_count) :
  VM_GC_Operation(gc_count, GCCause::_allocation_failure),
  _size(size),
  _result(NULL)
{
}

void VM_ParallelGCFailedAllocation::doit() {
  SvcGCMarker sgcm(SvcGCMarker::MINOR);

  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap, "must be a ParallelScavengeHeap");

  GCCauseSetter gccs(heap, _gc_cause);
  _result = heap->failed_mem_allocate(_size);

  if (_result == NULL && GC_locker::is_active_and_needs_gc()) {
    set_gc_locked();
  }
}

// Only used for System.gc() calls
VM_ParallelGCSystemGC::VM_ParallelGCSystemGC(unsigned int gc_count,
                                             unsigned int full_gc_count,
                                             GCCause::Cause gc_cause) :
  VM_GC_Operation(gc_count, gc_cause, full_gc_count, true /* full */)
{
}

void VM_ParallelGCSystemGC::doit() {
  SvcGCMarker sgcm(SvcGCMarker::FULL);

  ParallelScavengeHeap* heap = (ParallelScavengeHeap*)Universe::heap();
  assert(heap->kind() == CollectedHeap::ParallelScavengeHeap,
    "must be a ParallelScavengeHeap");

  GCCauseSetter gccs(heap, _gc_cause);
  if (_gc_cause == GCCause::_gc_locker || _gc_cause == GCCause::_wb_young_gc
      DEBUG_ONLY(|| _gc_cause == GCCause::_scavenge_alot)) {
    // If (and only if) the scavenge fails, this will invoke a full gc.
    heap->invoke_scavenge();
  } else {
    heap->do_full_collection(false);
  }
}

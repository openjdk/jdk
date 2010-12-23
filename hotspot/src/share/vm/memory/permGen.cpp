/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "gc_implementation/shared/cSpaceCounters.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "memory/blockOffsetTable.inline.hpp"
#include "memory/compactPermGen.hpp"
#include "memory/gcLocker.hpp"
#include "memory/gcLocker.inline.hpp"
#include "memory/genCollectedHeap.hpp"
#include "memory/generation.inline.hpp"
#include "memory/permGen.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/vmThread.hpp"

HeapWord* PermGen::request_expand_and_allocate(Generation* gen, size_t size,
                                               GCCause::Cause prev_cause) {
  if (gen->capacity() < _capacity_expansion_limit ||
      prev_cause != GCCause::_no_gc || UseG1GC) {  // last disjunct is a temporary hack for G1
    return gen->expand_and_allocate(size, false);
  }
  // We have reached the limit of capacity expansion where
  // we will not expand further until a GC is done; request denied.
  return NULL;
}

HeapWord* PermGen::mem_allocate_in_gen(size_t size, Generation* gen) {
  GCCause::Cause next_cause = GCCause::_permanent_generation_full;
  GCCause::Cause prev_cause = GCCause::_no_gc;
  unsigned int gc_count_before, full_gc_count_before;
  HeapWord* obj;

  for (;;) {
    {
      MutexLocker ml(Heap_lock);
      if ((obj = gen->allocate(size, false)) != NULL) {
        return obj;
      }
      // Attempt to expand and allocate the requested space:
      // specific subtypes may use specific policy to either expand
      // or not. The default policy (see above) is to expand until
      // _capacity_expansion_limit, and no further unless a GC is done.
      // Concurrent collectors may decide to kick off a concurrent
      // collection under appropriate conditions.
      obj = request_expand_and_allocate(gen, size, prev_cause);

      if (obj != NULL || prev_cause == GCCause::_last_ditch_collection) {
        return obj;
      }
      if (GC_locker::is_active_and_needs_gc()) {
        // If this thread is not in a jni critical section, we stall
        // the requestor until the critical section has cleared and
        // GC allowed. When the critical section clears, a GC is
        // initiated by the last thread exiting the critical section; so
        // we retry the allocation sequence from the beginning of the loop,
        // rather than causing more, now probably unnecessary, GC attempts.
        JavaThread* jthr = JavaThread::current();
        if (!jthr->in_critical()) {
          MutexUnlocker mul(Heap_lock);
          // Wait for JNI critical section to be exited
          GC_locker::stall_until_clear();
          continue;
        } else {
          if (CheckJNICalls) {
            fatal("Possible deadlock due to allocating while"
                  " in jni critical section");
          }
          return NULL;
        }
      }
      // Read the GC count while holding the Heap_lock
      gc_count_before      = SharedHeap::heap()->total_collections();
      full_gc_count_before = SharedHeap::heap()->total_full_collections();
    }

    // Give up heap lock above, VMThread::execute below gets it back
    VM_GenCollectForPermanentAllocation op(size, gc_count_before, full_gc_count_before,
                                           next_cause);
    VMThread::execute(&op);
    if (!op.prologue_succeeded() || op.gc_locked()) {
      assert(op.result() == NULL, "must be NULL if gc_locked() is true");
      continue;  // retry and/or stall as necessary
    }
    obj = op.result();
    assert(obj == NULL || SharedHeap::heap()->is_in_reserved(obj),
           "result not in heap");
    if (obj != NULL) {
      return obj;
    }
    prev_cause = next_cause;
    next_cause = GCCause::_last_ditch_collection;
  }
}

CompactingPermGen::CompactingPermGen(ReservedSpace rs,
                                     ReservedSpace shared_rs,
                                     size_t initial_byte_size,
                                     GenRemSet* remset,
                                     PermanentGenerationSpec* perm_spec)
{
  CompactingPermGenGen* g =
    new CompactingPermGenGen(rs, shared_rs, initial_byte_size, -1, remset,
                             NULL, perm_spec);
  if (g == NULL)
    vm_exit_during_initialization("Could not allocate a CompactingPermGen");
  _gen = g;

  g->initialize_performance_counters();

  _capacity_expansion_limit = g->capacity() + MaxPermHeapExpansion;
}

HeapWord* CompactingPermGen::mem_allocate(size_t size) {
  return mem_allocate_in_gen(size, _gen);
}

void CompactingPermGen::compute_new_size() {
  size_t desired_capacity = align_size_up(_gen->used(), MinPermHeapExpansion);
  if (desired_capacity < PermSize) {
    desired_capacity = PermSize;
  }
  if (_gen->capacity() > desired_capacity) {
    _gen->shrink(_gen->capacity() - desired_capacity);
  }
  set_capacity_expansion_limit(_gen->capacity() + MaxPermHeapExpansion);
}

/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/allocTracer.hpp"
#include "jfr/jfrEvents.hpp"
#include "runtime/handles.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JFR
#include "jfr/support/jfrAllocationTracer.hpp"
#endif

static THREAD_LOCAL size_t _last_allocated_bytes = 0;

static bool send_allocation_sample(Klass* klass, size_t allocated_bytes, Thread* thread) {
  assert(allocated_bytes > 0, "invariant");
  EventObjectAllocationSample event;
  if (event.should_commit()) {
    const size_t weight = allocated_bytes - _last_allocated_bytes;
    assert(weight > 0, "invariant");
    event.set_objectClass(klass);
    event.set_weight(weight);
    event.commit();
    _last_allocated_bytes = allocated_bytes;
    return true;
  }
  return false;
}

// The data amount of a large object is normalized into a frequency of sampling attempts
// to avoid large objects from being undersampled compared to the regular TLAB samples.
static void normalize_as_tlab_and_send_allocation_samples(Klass* klass, size_t alloc_size, Thread* thread) {
  const size_t allocated_bytes = thread->allocated_bytes(); // alloc_size is already attributed at this point
  assert(allocated_bytes > 0, "invariant");
  if (!UseTLAB) {
    send_allocation_sample(klass, allocated_bytes, thread);
    return;
  }
  const size_t desired_tlab_size = thread->tlab().desired_size() * HeapWordSize;
  const size_t reservation = thread->tlab().alignment_reserve_in_bytes();
  assert(desired_tlab_size > reservation, "invariant");
  const size_t min_weight = desired_tlab_size - reservation;
  if (allocated_bytes - _last_allocated_bytes < min_weight) {
    return;
  }
  assert(min_weight != 0, "invariant");
  size_t alloc_size_as_tlab_multiples = alloc_size / min_weight;
  if (alloc_size % min_weight != 0) {
    // add the remainder as another attempt
    ++alloc_size_as_tlab_multiples;
  }
  for (size_t i = 0; i < alloc_size_as_tlab_multiples; ++i) {
    if (send_allocation_sample(klass, allocated_bytes, thread)) {
      return;
    }
  }
}

void AllocTracer::send_allocation_outside_tlab(Klass* klass, HeapWord* obj, size_t alloc_size, Thread* thread) {
  JFR_ONLY(JfrAllocationTracer tracer(obj, alloc_size, thread);)
  EventObjectAllocationOutsideTLAB event;
  if (event.should_commit()) {
    event.set_objectClass(klass);
    event.set_allocationSize(alloc_size);
    event.commit();
  }
  normalize_as_tlab_and_send_allocation_samples(klass, alloc_size, thread);
}

void AllocTracer::send_allocation_in_new_tlab(Klass* klass, HeapWord* obj, size_t tlab_size, size_t alloc_size, Thread* thread) {
  JFR_ONLY(JfrAllocationTracer tracer(obj, alloc_size, thread);)
  EventObjectAllocationInNewTLAB event;
  if (event.should_commit()) {
    event.set_objectClass(klass);
    event.set_allocationSize(alloc_size);
    event.set_tlabSize(tlab_size);
    event.commit();
  }
  const size_t allocated_bytes = thread->allocated_bytes(); // what is already committed
  if (allocated_bytes == _last_allocated_bytes) return;
  send_allocation_sample(klass, allocated_bytes, thread);
}

void AllocTracer::send_allocation_requiring_gc_event(size_t size, uint gcId) {
  EventAllocationRequiringGC event;
  if (event.should_commit()) {
    event.set_gcId(gcId);
    event.set_size(size);
    event.commit();
  }
}

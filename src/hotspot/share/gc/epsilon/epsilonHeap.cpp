/*
 * Copyright (c) 2017, 2018, Red Hat, Inc. All rights reserved.
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
#include "gc/epsilon/epsilonHeap.hpp"
#include "gc/epsilon/epsilonMemoryPool.hpp"
#include "gc/epsilon/epsilonThreadLocalData.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"

jint EpsilonHeap::initialize() {
  size_t align = _policy->heap_alignment();
  size_t init_byte_size = align_up(_policy->initial_heap_byte_size(), align);
  size_t max_byte_size  = align_up(_policy->max_heap_byte_size(), align);

  // Initialize backing storage
  ReservedSpace heap_rs = Universe::reserve_heap(max_byte_size, align);
  _virtual_space.initialize(heap_rs, init_byte_size);

  MemRegion committed_region((HeapWord*)_virtual_space.low(),          (HeapWord*)_virtual_space.high());
  MemRegion  reserved_region((HeapWord*)_virtual_space.low_boundary(), (HeapWord*)_virtual_space.high_boundary());

  initialize_reserved_region(reserved_region.start(), reserved_region.end());

  _space = new ContiguousSpace();
  _space->initialize(committed_region, /* clear_space = */ true, /* mangle_space = */ true);

  // Precompute hot fields
  _max_tlab_size = MIN2(CollectedHeap::max_tlab_size(), align_object_size(EpsilonMaxTLABSize / HeapWordSize));
  _step_counter_update = MIN2<size_t>(max_byte_size / 16, EpsilonUpdateCountersStep);
  _step_heap_print = (EpsilonPrintHeapSteps == 0) ? SIZE_MAX : (max_byte_size / EpsilonPrintHeapSteps);
  _decay_time_ns = (int64_t) EpsilonTLABDecayTime * NANOSECS_PER_MILLISEC;

  // Enable monitoring
  _monitoring_support = new EpsilonMonitoringSupport(this);
  _last_counter_update = 0;
  _last_heap_print = 0;

  // Install barrier set
  BarrierSet::set_barrier_set(new EpsilonBarrierSet());

  // All done, print out the configuration
  if (init_byte_size != max_byte_size) {
    log_info(gc)("Resizeable heap; starting at " SIZE_FORMAT "M, max: " SIZE_FORMAT "M, step: " SIZE_FORMAT "M",
                 init_byte_size / M, max_byte_size / M, EpsilonMinHeapExpand / M);
  } else {
    log_info(gc)("Non-resizeable heap; start/max: " SIZE_FORMAT "M", init_byte_size / M);
  }

  if (UseTLAB) {
    log_info(gc)("Using TLAB allocation; max: " SIZE_FORMAT "K", _max_tlab_size * HeapWordSize / K);
    if (EpsilonElasticTLAB) {
      log_info(gc)("Elastic TLABs enabled; elasticity: %.2fx", EpsilonTLABElasticity);
    }
    if (EpsilonElasticTLABDecay) {
      log_info(gc)("Elastic TLABs decay enabled; decay time: " SIZE_FORMAT "ms", EpsilonTLABDecayTime);
    }
  } else {
    log_info(gc)("Not using TLAB allocation");
  }

  return JNI_OK;
}

void EpsilonHeap::post_initialize() {
  CollectedHeap::post_initialize();
}

void EpsilonHeap::initialize_serviceability() {
  _pool = new EpsilonMemoryPool(this);
  _memory_manager.add_pool(_pool);
}

GrowableArray<GCMemoryManager*> EpsilonHeap::memory_managers() {
  GrowableArray<GCMemoryManager*> memory_managers(1);
  memory_managers.append(&_memory_manager);
  return memory_managers;
}

GrowableArray<MemoryPool*> EpsilonHeap::memory_pools() {
  GrowableArray<MemoryPool*> memory_pools(1);
  memory_pools.append(_pool);
  return memory_pools;
}

size_t EpsilonHeap::unsafe_max_tlab_alloc(Thread* thr) const {
  // Return max allocatable TLAB size, and let allocation path figure out
  // the actual TLAB allocation size.
  return _max_tlab_size;
}

EpsilonHeap* EpsilonHeap::heap() {
  CollectedHeap* heap = Universe::heap();
  assert(heap != NULL, "Uninitialized access to EpsilonHeap::heap()");
  assert(heap->kind() == CollectedHeap::Epsilon, "Not an Epsilon heap");
  return (EpsilonHeap*)heap;
}

HeapWord* EpsilonHeap::allocate_work(size_t size) {
  assert(is_object_aligned(size), "Allocation size should be aligned: " SIZE_FORMAT, size);

  HeapWord* res = _space->par_allocate(size);

  while (res == NULL) {
    // Allocation failed, attempt expansion, and retry:
    MutexLockerEx ml(Heap_lock);

    size_t space_left = max_capacity() - capacity();
    size_t want_space = MAX2(size, EpsilonMinHeapExpand);

    if (want_space < space_left) {
      // Enough space to expand in bulk:
      bool expand = _virtual_space.expand_by(want_space);
      assert(expand, "Should be able to expand");
    } else if (size < space_left) {
      // No space to expand in bulk, and this allocation is still possible,
      // take all the remaining space:
      bool expand = _virtual_space.expand_by(space_left);
      assert(expand, "Should be able to expand");
    } else {
      // No space left:
      return NULL;
    }

    _space->set_end((HeapWord *) _virtual_space.high());
    res = _space->par_allocate(size);
  }

  size_t used = _space->used();

  // Allocation successful, update counters
  {
    size_t last = _last_counter_update;
    if ((used - last >= _step_counter_update) && Atomic::cmpxchg(used, &_last_counter_update, last) == last) {
      _monitoring_support->update_counters();
    }
  }

  // ...and print the occupancy line, if needed
  {
    size_t last = _last_heap_print;
    if ((used - last >= _step_heap_print) && Atomic::cmpxchg(used, &_last_heap_print, last) == last) {
      log_info(gc)("Heap: " SIZE_FORMAT "M reserved, " SIZE_FORMAT "M (%.2f%%) committed, " SIZE_FORMAT "M (%.2f%%) used",
                   max_capacity() / M,
                   capacity() / M,
                   capacity() * 100.0 / max_capacity(),
                   used / M,
                   used * 100.0 / max_capacity());
    }
  }

  assert(is_object_aligned(res), "Object should be aligned: " PTR_FORMAT, p2i(res));
  return res;
}

HeapWord* EpsilonHeap::allocate_new_tlab(size_t min_size,
                                         size_t requested_size,
                                         size_t* actual_size) {
  Thread* thread = Thread::current();

  // Defaults in case elastic paths are not taken
  bool fits = true;
  size_t size = requested_size;
  size_t ergo_tlab = requested_size;
  int64_t time = 0;

  if (EpsilonElasticTLAB) {
    ergo_tlab = EpsilonThreadLocalData::ergo_tlab_size(thread);

    if (EpsilonElasticTLABDecay) {
      int64_t last_time = EpsilonThreadLocalData::last_tlab_time(thread);
      time = (int64_t) os::javaTimeNanos();

      assert(last_time <= time, "time should be monotonic");

      // If the thread had not allocated recently, retract the ergonomic size.
      // This conserves memory when the thread had initial burst of allocations,
      // and then started allocating only sporadically.
      if (last_time != 0 && (time - last_time > _decay_time_ns)) {
        ergo_tlab = 0;
        EpsilonThreadLocalData::set_ergo_tlab_size(thread, 0);
      }
    }

    // If we can fit the allocation under current TLAB size, do so.
    // Otherwise, we want to elastically increase the TLAB size.
    fits = (requested_size <= ergo_tlab);
    if (!fits) {
      size = (size_t) (ergo_tlab * EpsilonTLABElasticity);
    }
  }

  // Always honor boundaries
  size = MAX2(min_size, MIN2(_max_tlab_size, size));

  // Always honor alignment
  size = align_up(size, MinObjAlignment);

  // Check that adjustments did not break local and global invariants
  assert(is_object_aligned(size),
         "Size honors object alignment: " SIZE_FORMAT, size);
  assert(min_size <= size,
         "Size honors min size: "  SIZE_FORMAT " <= " SIZE_FORMAT, min_size, size);
  assert(size <= _max_tlab_size,
         "Size honors max size: "  SIZE_FORMAT " <= " SIZE_FORMAT, size, _max_tlab_size);
  assert(size <= CollectedHeap::max_tlab_size(),
         "Size honors global max size: "  SIZE_FORMAT " <= " SIZE_FORMAT, size, CollectedHeap::max_tlab_size());

  if (log_is_enabled(Trace, gc)) {
    ResourceMark rm;
    log_trace(gc)("TLAB size for \"%s\" (Requested: " SIZE_FORMAT "K, Min: " SIZE_FORMAT
                          "K, Max: " SIZE_FORMAT "K, Ergo: " SIZE_FORMAT "K) -> " SIZE_FORMAT "K",
                  thread->name(),
                  requested_size * HeapWordSize / K,
                  min_size * HeapWordSize / K,
                  _max_tlab_size * HeapWordSize / K,
                  ergo_tlab * HeapWordSize / K,
                  size * HeapWordSize / K);
  }

  // All prepared, let's do it!
  HeapWord* res = allocate_work(size);

  if (res != NULL) {
    // Allocation successful
    *actual_size = size;
    if (EpsilonElasticTLABDecay) {
      EpsilonThreadLocalData::set_last_tlab_time(thread, time);
    }
    if (EpsilonElasticTLAB && !fits) {
      // If we requested expansion, this is our new ergonomic TLAB size
      EpsilonThreadLocalData::set_ergo_tlab_size(thread, size);
    }
  } else {
    // Allocation failed, reset ergonomics to try and fit smaller TLABs
    if (EpsilonElasticTLAB) {
      EpsilonThreadLocalData::set_ergo_tlab_size(thread, 0);
    }
  }

  return res;
}

HeapWord* EpsilonHeap::mem_allocate(size_t size, bool *gc_overhead_limit_was_exceeded) {
  *gc_overhead_limit_was_exceeded = false;
  return allocate_work(size);
}

void EpsilonHeap::collect(GCCause::Cause cause) {
  log_info(gc)("GC request for \"%s\" is ignored", GCCause::to_string(cause));
  _monitoring_support->update_counters();
}

void EpsilonHeap::do_full_collection(bool clear_all_soft_refs) {
  log_info(gc)("Full GC request for \"%s\" is ignored", GCCause::to_string(gc_cause()));
  _monitoring_support->update_counters();
}

void EpsilonHeap::safe_object_iterate(ObjectClosure *cl) {
  _space->safe_object_iterate(cl);
}

void EpsilonHeap::print_on(outputStream *st) const {
  st->print_cr("Epsilon Heap");

  // Cast away constness:
  ((VirtualSpace)_virtual_space).print_on(st);

  st->print_cr("Allocation space:");
  _space->print_on(st);
}

void EpsilonHeap::print_tracing_info() const {
  Log(gc) log;
  size_t allocated_kb = used() / K;
  log.info("Total allocated: " SIZE_FORMAT " KB",
           allocated_kb);
  log.info("Average allocation rate: " SIZE_FORMAT " KB/sec",
           (size_t)(allocated_kb * NANOSECS_PER_SEC / os::elapsed_counter()));
}

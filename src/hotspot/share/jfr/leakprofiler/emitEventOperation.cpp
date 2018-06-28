/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/collectedHeap.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/leakprofiler/utilities/granularTimer.hpp"
#include "jfr/leakprofiler/chains/rootSetClosure.hpp"
#include "jfr/leakprofiler/chains/edge.hpp"
#include "jfr/leakprofiler/chains/edgeQueue.hpp"
#include "jfr/leakprofiler/chains/edgeStore.hpp"
#include "jfr/leakprofiler/chains/bitset.hpp"
#include "jfr/leakprofiler/sampling/objectSample.hpp"
#include "jfr/leakprofiler/leakProfiler.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/leakprofiler/sampling/objectSampler.hpp"
#include "jfr/leakprofiler/emitEventOperation.hpp"
#include "jfr/leakprofiler/chains/bfsClosure.hpp"
#include "jfr/leakprofiler/chains/dfsClosure.hpp"
#include "jfr/leakprofiler/chains/objectSampleMarker.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointWriter.hpp"
#include "jfr/support/jfrThreadId.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/markOop.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/globalDefinitions.hpp"

/* The EdgeQueue is backed by directly managed virtual memory.
 * We will attempt to dimension an initial reservation
 * in proportion to the size of the heap (represented by heap_region).
 * Initial memory reservation: 5% of the heap OR at least 32 Mb
 * Commit ratio: 1 : 10 (subject to allocation granularties)
 */
static size_t edge_queue_memory_reservation(const MemRegion& heap_region) {
  const size_t memory_reservation_bytes = MAX2(heap_region.byte_size() / 20, 32*M);
  assert(memory_reservation_bytes >= (size_t)32*M, "invariant");
  return memory_reservation_bytes;
}

static size_t edge_queue_memory_commit_size(size_t memory_reservation_bytes) {
  const size_t memory_commit_block_size_bytes = memory_reservation_bytes / 10;
  assert(memory_commit_block_size_bytes >= (size_t)3*M, "invariant");
  return memory_commit_block_size_bytes;
}

static void log_edge_queue_summary(const EdgeQueue& edge_queue) {
  log_trace(jfr, system)("EdgeQueue reserved size total: " SIZE_FORMAT " [KB]", edge_queue.reserved_size() / K);
  log_trace(jfr, system)("EdgeQueue edges total: " SIZE_FORMAT, edge_queue.top());
  log_trace(jfr, system)("EdgeQueue liveset total: " SIZE_FORMAT " [KB]", edge_queue.live_set() / K);
  if (edge_queue.reserved_size() > 0) {
    log_trace(jfr, system)("EdgeQueue commit reserve ratio: %f\n",
      ((double)edge_queue.live_set() / (double)edge_queue.reserved_size()));
  }
}

void EmitEventOperation::doit() {
  assert(LeakProfiler::is_running(), "invariant");
  _object_sampler = LeakProfiler::object_sampler();
  assert(_object_sampler != NULL, "invariant");

  _vm_thread = VMThread::vm_thread();
  assert(_vm_thread == Thread::current(), "invariant");
  _vm_thread_local = _vm_thread->jfr_thread_local();
  assert(_vm_thread_local != NULL, "invariant");
  assert(_vm_thread->jfr_thread_local()->thread_id() == JFR_THREAD_ID(_vm_thread), "invariant");

  // The VM_Operation::evaluate() which invoked doit()
  // contains a top level ResourceMark

  // save the original markWord for the potential leak objects
  // to be restored on function exit
  ObjectSampleMarker marker;
  if (ObjectSampleCheckpoint::mark(marker, _emit_all) == 0) {
    return;
  }

  EdgeStore edge_store;

  GranularTimer::start(_cutoff_ticks, 1000000);
  if (_cutoff_ticks <= 0) {
    // no chains
    write_events(&edge_store);
    return;
  }

  assert(_cutoff_ticks > 0, "invariant");

  // The bitset used for marking is dimensioned as a function of the heap size
  const MemRegion heap_region = Universe::heap()->reserved_region();
  BitSet mark_bits(heap_region);

  // The edge queue is dimensioned as a fraction of the heap size
  const size_t edge_queue_reservation_size = edge_queue_memory_reservation(heap_region);
  EdgeQueue edge_queue(edge_queue_reservation_size, edge_queue_memory_commit_size(edge_queue_reservation_size));

  // The initialize() routines will attempt to reserve and allocate backing storage memory.
  // Failure to accommodate will render root chain processing impossible.
  // As a fallback on failure, just write out the existing samples, flat, without chains.
  if (!(mark_bits.initialize() && edge_queue.initialize())) {
    log_warning(jfr)("Unable to allocate memory for root chain processing");
    write_events(&edge_store);
    return;
  }

  // necessary condition for attempting a root set iteration
  Universe::heap()->ensure_parsability(false);

  RootSetClosure::add_to_queue(&edge_queue);
  if (edge_queue.is_full()) {
    // Pathological case where roots don't fit in queue
    // Do a depth-first search, but mark roots first
    // to avoid walking sideways over roots
    DFSClosure::find_leaks_from_root_set(&edge_store, &mark_bits);
  } else {
    BFSClosure bfs(&edge_queue, &edge_store, &mark_bits);
    bfs.process();
  }
  GranularTimer::stop();
  write_events(&edge_store);
  log_edge_queue_summary(edge_queue);
}

int EmitEventOperation::write_events(EdgeStore* edge_store) {
  assert(_object_sampler != NULL, "invariant");
  assert(edge_store != NULL, "invariant");
  assert(_vm_thread != NULL, "invariant");
  assert(_vm_thread_local != NULL, "invariant");
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");

  // save thread id in preparation for thread local trace data manipulations
  const traceid vmthread_id = _vm_thread_local->thread_id();
  assert(_vm_thread_local->thread_id() == JFR_THREAD_ID(_vm_thread), "invariant");

  const jlong last_sweep = _emit_all ? max_jlong : _object_sampler->last_sweep().value();
  int count = 0;

  for (int i = 0; i < _object_sampler->item_count(); ++i) {
    const ObjectSample* sample = _object_sampler->item_at(i);
    if (sample->is_alive_and_older_than(last_sweep)) {
      write_event(sample, edge_store);
      ++count;
    }
  }

  // restore thread local stack trace and thread id
  _vm_thread_local->set_thread_id(vmthread_id);
  _vm_thread_local->clear_cached_stack_trace();
  assert(_vm_thread_local->thread_id() == JFR_THREAD_ID(_vm_thread), "invariant");

  if (count > 0) {
    // serialize assoicated checkpoints
    ObjectSampleCheckpoint::write(edge_store, _emit_all, _vm_thread);
  }
  return count;
}

static int array_size(const oop object) {
  assert(object != NULL, "invariant");
  if (object->is_array()) {
    return arrayOop(object)->length();
  }
  return -1;
}

void EmitEventOperation::write_event(const ObjectSample* sample, EdgeStore* edge_store) {
  assert(sample != NULL, "invariant");
  assert(!sample->is_dead(), "invariant");
  assert(edge_store != NULL, "invariant");
  assert(_vm_thread_local != NULL, "invariant");
  const oop* object_addr = sample->object_addr();
  assert(*object_addr != NULL, "invariant");

  const Edge* edge = (const Edge*)(*object_addr)->mark();
  traceid gc_root_id = 0;
  if (edge == NULL) {
    // In order to dump out a representation of the event
    // even though it was not reachable / too long to reach,
    // we need to register a top level edge for this object
    Edge e(NULL, object_addr);
    edge_store->add_chain(&e, 1);
    edge = (const Edge*)(*object_addr)->mark();
  } else {
    gc_root_id = edge_store->get_root_id(edge);
  }

  assert(edge != NULL, "invariant");
  assert(edge->pointee() == *object_addr, "invariant");
  const traceid object_id = edge_store->get_id(edge);
  assert(object_id != 0, "invariant");

  EventOldObjectSample e(UNTIMED);
  e.set_starttime(GranularTimer::start_time());
  e.set_endtime(GranularTimer::end_time());
  e.set_allocationTime(sample->allocation_time());
  e.set_lastKnownHeapUsage(sample->heap_used_at_last_gc());
  e.set_object(object_id);
  e.set_arrayElements(array_size(*object_addr));
  e.set_root(gc_root_id);

  // Temporarily assigning both the stack trace id and thread id
  // onto the thread local data structure of the VMThread (for the duration
  // of the commit() call). This trick provides a means to override
  // the event generation mechanism by injecting externally provided id's.
  // Here, in particular, this allows us to emit an old object event
  // supplying information from where the actual sampling occurred.
  _vm_thread_local->set_cached_stack_trace_id(sample->stack_trace_id());
  assert(sample->has_thread(), "invariant");
  _vm_thread_local->set_thread_id(sample->thread_id());
  e.commit();
}

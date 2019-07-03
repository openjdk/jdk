/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfrEvents.hpp"
#include "jfr/leakprofiler/chains/edgeStore.hpp"
#include "jfr/leakprofiler/chains/pathToGcRootsOperation.hpp"
#include "jfr/leakprofiler/checkpoint/eventEmitter.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/leakprofiler/sampling/objectSample.hpp"
#include "jfr/leakprofiler/sampling/objectSampler.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "oops/markOop.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/vmThread.hpp"

EventEmitter::EventEmitter(const JfrTicks& start_time, const JfrTicks& end_time) :
  _start_time(start_time),
  _end_time(end_time),
  _thread(Thread::current()),
  _jfr_thread_local(_thread->jfr_thread_local()),
  _thread_id(_thread->jfr_thread_local()->thread_id()) {}

EventEmitter::~EventEmitter() {
  // restore / reset thread local stack trace and thread id
  _jfr_thread_local->set_thread_id(_thread_id);
  _jfr_thread_local->clear_cached_stack_trace();
}

void EventEmitter::emit(ObjectSampler* sampler, int64_t cutoff_ticks, bool emit_all) {
  assert(sampler != NULL, "invariant");

  ResourceMark rm;
  EdgeStore edge_store;
  if (cutoff_ticks <= 0) {
    // no reference chains
    JfrTicks time_stamp = JfrTicks::now();
    EventEmitter emitter(time_stamp, time_stamp);
    emitter.write_events(sampler, &edge_store, emit_all);
    return;
  }
  // events emitted with reference chains require a safepoint operation
  PathToGcRootsOperation op(sampler, &edge_store, cutoff_ticks, emit_all);
  VMThread::execute(&op);
}

size_t EventEmitter::write_events(ObjectSampler* object_sampler, EdgeStore* edge_store, bool emit_all) {
  assert(_thread == Thread::current(), "invariant");
  assert(_thread->jfr_thread_local() == _jfr_thread_local, "invariant");
  assert(object_sampler != NULL, "invariant");
  assert(edge_store != NULL, "invariant");

  const jlong last_sweep = emit_all ? max_jlong : object_sampler->last_sweep().value();
  size_t count = 0;

  const ObjectSample* current = object_sampler->first();
  while (current != NULL) {
    ObjectSample* prev = current->prev();
    if (current->is_alive_and_older_than(last_sweep)) {
      write_event(current, edge_store);
      ++count;
    }
    current = prev;
  }

  if (count > 0) {
    // serialize associated checkpoints and potential chains
    ObjectSampleCheckpoint::write(object_sampler, edge_store, emit_all, _thread);
  }
  return count;
}

static int array_size(const oop object) {
  assert(object != NULL, "invariant");
  if (object->is_array()) {
    return arrayOop(object)->length();
  }
  return min_jint;
}

void EventEmitter::write_event(const ObjectSample* sample, EdgeStore* edge_store) {
  assert(sample != NULL, "invariant");
  assert(!sample->is_dead(), "invariant");
  assert(edge_store != NULL, "invariant");
  assert(_jfr_thread_local != NULL, "invariant");

  const oop* object_addr = sample->object_addr();
  traceid gc_root_id = 0;
  const Edge* edge = NULL;
  if (SafepointSynchronize::is_at_safepoint()) {
    edge = (const Edge*)(*object_addr)->mark();
  }
  if (edge == NULL) {
    // In order to dump out a representation of the event
    // even though it was not reachable / too long to reach,
    // we need to register a top level edge for this object.
    edge = edge_store->put(object_addr);
  } else {
    gc_root_id = edge_store->gc_root_id(edge);
  }

  assert(edge != NULL, "invariant");
  const traceid object_id = edge_store->get_id(edge);
  assert(object_id != 0, "invariant");

  EventOldObjectSample e(UNTIMED);
  e.set_starttime(_start_time);
  e.set_endtime(_end_time);
  e.set_allocationTime(sample->allocation_time());
  e.set_lastKnownHeapUsage(sample->heap_used_at_last_gc());
  e.set_object(object_id);
  e.set_arrayElements(array_size(edge->pointee()));
  e.set_root(gc_root_id);

  // Temporarily assigning both the stack trace id and thread id
  // onto the thread local data structure of the emitter thread (for the duration
  // of the commit() call). This trick provides a means to override
  // the event generation mechanism by injecting externally provided id's.
  // At this particular location, it allows us to emit an old object event
  // supplying information from where the actual sampling occurred.
  _jfr_thread_local->set_cached_stack_trace_id(sample->stack_trace_id());
  assert(sample->has_thread(), "invariant");
  _jfr_thread_local->set_thread_id(sample->thread_id());
  e.commit();
}

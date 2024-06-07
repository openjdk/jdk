/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/oopStorage.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/leakprofiler/checkpoint/objectSampleCheckpoint.hpp"
#include "jfr/leakprofiler/sampling/objectSample.hpp"
#include "jfr/leakprofiler/sampling/objectSampler.hpp"
#include "jfr/leakprofiler/sampling/sampleList.hpp"
#include "jfr/leakprofiler/sampling/samplePriorityQueue.hpp"
#include "jfr/recorder/jfrEventSetting.inline.hpp"
#include "jfr/recorder/checkpoint/jfrCheckpointManager.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/utilities/jfrSignal.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTryLock.hpp"
#include "logging/log.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/safepoint.hpp"

// Timestamp of when the gc last processed the set of sampled objects.
// Atomic access to prevent word tearing on 32-bit platforms.
static volatile int64_t _last_sweep;

// Condition variable to communicate that some sampled objects have been cleared by the gc
// and can therefore be removed from the sample priority queue.
static bool volatile _dead_samples = false;

// The OopStorage instance is used to hold weak references to sampled objects.
// It is constructed and registered during VM initialization. This is a singleton
// that persist independent of the state of the ObjectSampler.
static OopStorage* _oop_storage = nullptr;

// A notification mechanism to let class unloading determine if to save unloaded typesets.
static JfrSignal _unresolved_entry;

static inline void signal_unresolved_entry() {
  _unresolved_entry.signal_if_not_set();
}

static inline void clear_unresolved_entry() {
  _unresolved_entry.reset();
}

static inline void signal_resolved() {
  clear_unresolved_entry();
}

bool ObjectSampler::has_unresolved_entry() {
  return _unresolved_entry.is_signaled();
}

OopStorage* ObjectSampler::oop_storage() { return _oop_storage; }

// Callback invoked by the GC after an iteration over the oop storage
// that may have cleared dead referents. num_dead is the number of entries
// already nullptr or cleared by the iteration.
void ObjectSampler::oop_storage_gc_notification(size_t num_dead) {
  if (num_dead != 0) {
    // The ObjectSampler instance may have already been cleaned or a new
    // instance was created concurrently.  This allows for a small race where cleaning
    // could be done again.
    Atomic::store(&_dead_samples, true);
    Atomic::store(&_last_sweep, (int64_t)JfrTicks::now().value());
  }
}

bool ObjectSampler::create_oop_storage() {
  _oop_storage = OopStorageSet::create_weak("Weak JFR Old Object Samples", mtTracing);
  assert(_oop_storage != nullptr, "invariant");
  _oop_storage->register_num_dead_callback(&oop_storage_gc_notification);
  return true;
}

static ObjectSampler* _instance = nullptr;

static ObjectSampler& instance() {
  assert(_instance != nullptr, "invariant");
  return *_instance;
}

ObjectSampler::ObjectSampler(size_t size) :
        _priority_queue(new SamplePriorityQueue(size)),
        _list(new SampleList(size)),
        _total_allocated(0),
        _threshold(0),
        _size(size) {
  Atomic::store(&_dead_samples, false);
  Atomic::store(&_last_sweep, (int64_t)JfrTicks::now().value());
}

ObjectSampler::~ObjectSampler() {
  delete _priority_queue;
  _priority_queue = nullptr;
  delete _list;
  _list = nullptr;
}

bool ObjectSampler::create(size_t size) {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  assert(_oop_storage != nullptr, "should be already created");
  clear_unresolved_entry();
  assert(!has_unresolved_entry(), "invariant");
  ObjectSampleCheckpoint::clear();
  assert(_instance == nullptr, "invariant");
  _instance = new ObjectSampler(size);
  return _instance != nullptr;
}

bool ObjectSampler::is_created() {
  return _instance != nullptr;
}

ObjectSampler* ObjectSampler::sampler() {
  assert(is_created(), "invariant");
  return _instance;
}

void ObjectSampler::destroy() {
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");
  if (_instance != nullptr) {
    ObjectSampler* const sampler = _instance;
    _instance = nullptr;
    delete sampler;
  }
}

static volatile int _lock = 0;

ObjectSampler* ObjectSampler::acquire() {
  while (Atomic::cmpxchg(&_lock, 0, 1) == 1) {}
  return _instance;
}

void ObjectSampler::release() {
  OrderAccess::fence();
  _lock = 0;
}

static traceid get_thread_id(JavaThread* thread, bool* virtual_thread) {
  assert(thread != nullptr, "invariant");
  assert(virtual_thread != nullptr, "invariant");
  if (thread->threadObj() == nullptr) {
    return 0;
  }
  const JfrThreadLocal* const tl = thread->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  if (tl->is_excluded()) {
    return 0;
  }
  *virtual_thread = tl->is_vthread(thread);
  return JfrThreadLocal::thread_id(thread);
}

static JfrBlobHandle get_thread_blob(JavaThread* thread, traceid tid, bool virtual_thread) {
  assert(thread != nullptr, "invariant");
  JfrThreadLocal* const tl = thread->jfr_thread_local();
  assert(tl != nullptr, "invariant");
  assert(!tl->is_excluded(), "invariant");
  if (virtual_thread) {
    // TODO: blob cache for virtual threads
    return JfrCheckpointManager::create_thread_blob(thread, tid, thread->vthread());
  }
  if (!tl->has_thread_blob()) {
    // for regular threads, the blob is cached in the thread local data structure
    tl->set_thread_blob(JfrCheckpointManager::create_thread_blob(thread, tid));
    assert(tl->has_thread_blob(), "invariant");
  }
  return tl->thread_blob();
}

class RecordStackTrace {
 private:
  JavaThread* _jt;
  bool _enabled;
 public:
  RecordStackTrace(JavaThread* jt) : _jt(jt),
    _enabled(JfrEventSetting::has_stacktrace(EventOldObjectSample::eventId)) {
    if (_enabled) {
      JfrStackTraceRepository::record_for_leak_profiler(jt);
    }
  }
  ~RecordStackTrace() {
    if (_enabled) {
      _jt->jfr_thread_local()->clear_cached_stack_trace();
    }
  }
};

void ObjectSampler::sample(HeapWord* obj, size_t allocated, JavaThread* thread) {
  assert(thread != nullptr, "invariant");
  assert(is_created(), "invariant");
  bool virtual_thread = false;
  const traceid thread_id = get_thread_id(thread, &virtual_thread);
  if (thread_id == 0) {
    return;
  }
  const JfrBlobHandle bh = get_thread_blob(thread, thread_id, virtual_thread);
  assert(bh.valid(), "invariant");
  RecordStackTrace rst(thread);
  // try enter critical section
  JfrTryLock tryLock(&_lock);
  if (!tryLock.acquired()) {
    log_trace(jfr, oldobject, sampling)("Skipping old object sample due to lock contention");
    return;
  }
  instance().add(obj, allocated, thread_id, virtual_thread, bh, thread);
}

void ObjectSampler::add(HeapWord* obj, size_t allocated, traceid thread_id, bool virtual_thread, const JfrBlobHandle& bh, JavaThread* thread) {
  assert(obj != nullptr, "invariant");
  assert(thread_id != 0, "invariant");
  assert(thread != nullptr, "invariant");

  if (Atomic::load(&_dead_samples)) {
    // There's a small race where a GC scan might reset this to true, potentially
    // causing a back-to-back scavenge.
    Atomic::store(&_dead_samples, false);
    scavenge();
  }

  _total_allocated += allocated;
  const size_t span = _total_allocated - _priority_queue->total();
  ObjectSample* sample;
  if ((size_t)_priority_queue->count() == _size) {
    assert(_list->count() == _size, "invariant");
    const ObjectSample* peek = _priority_queue->peek();
    if (peek->span() > span) {
      // quick reject, will not fit
      return;
    }
    sample = _list->reuse(_priority_queue->pop());
  } else {
    sample = _list->get();
  }

  assert(sample != nullptr, "invariant");
  signal_unresolved_entry();
  sample->set_thread_id(thread_id);
  if (virtual_thread) {
    sample->set_thread_is_virtual();
  }
  sample->set_thread(bh);

  const JfrThreadLocal* const tl = thread->jfr_thread_local();
  const traceid stacktrace_hash = tl->cached_stack_trace_hash();
  if (stacktrace_hash != 0) {
    sample->set_stack_trace_id(tl->cached_stack_trace_id());
    sample->set_stack_trace_hash(stacktrace_hash);
  }

  sample->set_span(allocated);
  sample->set_object(cast_to_oop(obj));
  sample->set_allocated(allocated);
  sample->set_allocation_time(JfrTicks::now());
  sample->set_heap_used_at_last_gc(Universe::heap()->used_at_last_gc());
  _priority_queue->push(sample);
}

void ObjectSampler::scavenge() {
  ObjectSample* current = _list->last();
  while (current != nullptr) {
    ObjectSample* next = current->next();
    if (current->is_dead()) {
      remove_dead(current);
    }
    current = next;
  }
}

void ObjectSampler::remove_dead(ObjectSample* sample) {
  assert(sample != nullptr, "invariant");
  assert(sample->is_dead(), "invariant");
  sample->release();

  ObjectSample* const previous = sample->prev();
  // push span onto previous
  if (previous != nullptr) {
    _priority_queue->remove(previous);
    previous->add_span(sample->span());
    _priority_queue->push(previous);
  }
  _priority_queue->remove(sample);
  _list->release(sample);
}

ObjectSample* ObjectSampler::last() const {
  return _list->last();
}

const ObjectSample* ObjectSampler::first() const {
  return _list->first();
}

const ObjectSample* ObjectSampler::last_resolved() const {
  return _list->last_resolved();
}

void ObjectSampler::set_last_resolved(const ObjectSample* sample) {
  _list->set_last_resolved(sample);
  signal_resolved();
}

int ObjectSampler::item_count() const {
  return _priority_queue->count();
}

const ObjectSample* ObjectSampler::item_at(int index) const {
  return _priority_queue->item_at(index);
}

ObjectSample* ObjectSampler::item_at(int index) {
  return const_cast<ObjectSample*>(
    const_cast<const ObjectSampler*>(this)->item_at(index)
                                  );
}

int64_t ObjectSampler::last_sweep() {
  return Atomic::load(&_last_sweep);
}

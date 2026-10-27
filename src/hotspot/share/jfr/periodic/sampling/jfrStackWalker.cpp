/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "utilities/macros.hpp"

#if INCLUDE_JFR

#include "jfr/periodic/sampling/jfrSampleRequest.hpp"
#include "jfr/periodic/sampling/jfrStackWalker.hpp"
#include "jfr/periodic/sampling/jfrThreadSampling.hpp"
#include "jfr/recorder/stacktrace/jfrStackTrace.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/frame.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/threadSMR.hpp"
#include "utilities/spinYield.hpp"

u4 JfrStackWalkerRequestQueue::size() const {
  return AtomicAccess::load_acquire(&_head);
}

JfrStackWalkerRequestQueue::JfrStackWalkerRequestQueue():
   _data(nullptr), _capacity(0), _head(0), _lost_requests_due_to_queue_full(0) {
}

JfrStackWalkerRequestQueue::~JfrStackWalkerRequestQueue() {
  if (_data != nullptr) {
    assert(_capacity != 0, "invariant");
    FREE_C_HEAP_ARRAY(_data);
  }
}

bool JfrStackWalkerRequestQueue::enqueue(const JfrStackWalkRequest& request) {
  static_assert(std::is_trivially_copyable<JfrStackWalkRequest>::value,
                "JfrStackWalkRequest must be trivially copyable for queue storage");
  // Note: assertions about JavaThread::current() are not valid here because
  // cross-thread enqueuing (e.g., RequestJFRStackTrace for another thread)
  // calls this on behalf of the target thread.
  // Acquire pairs with the release in init() and set_capacity(), so that a
  // non-zero capacity is never seen together with a stale _data.
  const u4 capacity = AtomicAccess::load_acquire(&_capacity);
  u4 elementIndex;
  do {
    elementIndex = AtomicAccess::load_acquire(&_head);
    if (elementIndex >= capacity) {
      return false;
    }
  } while (AtomicAccess::cmpxchg(&_head, elementIndex, elementIndex + 1) != elementIndex);
  _data[elementIndex] = request;
  return true;
}

JfrStackWalkRequest& JfrStackWalkerRequestQueue::at(u4 index) const {
  assert(index < _head, "invariant");
  return _data[index];
}

void JfrStackWalkerRequestQueue::set_capacity(u4 capacity) {
  if (capacity == AtomicAccess::load(&_capacity)) {
    return;
  }
  _head = 0;
  if (_data != nullptr) {
    assert(_capacity != 0, "invariant");
    FREE_C_HEAP_ARRAY(_data);
  }
  if (capacity != 0) {
    _data = NEW_C_HEAP_ARRAY(JfrStackWalkRequest, capacity, mtOther);
  } else {
    _data = nullptr;
  }
  AtomicAccess::release_store(&_capacity, capacity);
}

bool JfrStackWalkerRequestQueue::is_empty() const {
  return AtomicAccess::load_acquire(&_head) == 0;
}

void JfrStackWalkerRequestQueue::increment_lost_requests_due_to_queue_full() {
  AtomicAccess::inc(&_lost_requests_due_to_queue_full);
}

u4 JfrStackWalkerRequestQueue::get_and_reset_lost_requests_due_to_queue_full() {
  return AtomicAccess::xchg(&_lost_requests_due_to_queue_full, (u4)0);
}

void JfrStackWalkerRequestQueue::init() {
  // Keep an existing queue: it may have grown, and may still hold requests
  // from a previous use of the stack walker that its thread is about to process.
  if (AtomicAccess::load_acquire(&_capacity) != 0) {
    return;
  }
  // A starting thread and JfrStackWalker::initialize() can race to initialize
  // the same queue. Only the first to publish its array wins.
  JfrStackWalkRequest* const data = NEW_C_HEAP_ARRAY(JfrStackWalkRequest, INITIAL_CAPACITY, mtOther);
  if (AtomicAccess::cmpxchg(&_data, (JfrStackWalkRequest*)nullptr, data) != nullptr) {
    FREE_C_HEAP_ARRAY(data);
    return;
  }
  AtomicAccess::release_store(&_capacity, INITIAL_CAPACITY);
}

void JfrStackWalkerRequestQueue::clear() {
  AtomicAccess::release_store(&_head, (u4)0);
}

void JfrStackWalkerRequestQueue::resize_if_needed() {
  u4 lost_requests_due_to_queue_full = get_and_reset_lost_requests_due_to_queue_full();
  if (lost_requests_due_to_queue_full == 0) {
    return;
  }
  // Acquire pairs with the release in init(), which may race with us if the
  // drop we are reacting to hit a queue that was not yet initialized.
  u4 capacity = AtomicAccess::load_acquire(&_capacity);
  if (capacity == 0) {
    return; // Not initialized, nothing to grow.
  }
  if (capacity < MAX_CAPACITY) {
    float ratio = (float)lost_requests_due_to_queue_full / (float)capacity;
    int factor = 1;
    if (ratio > 8) { // idea is to quickly scale the queue in the worst case
      factor = static_cast<int>(ratio);
    } else if (ratio > 2) {
      factor = 8;
    } else if (ratio > 0.5) {
      factor = 4;
    } else if (ratio > 0.01) {
      factor = 2;
    }
    if (factor > 1) {
      u4 new_capacity = MIN2(MAX_CAPACITY, capacity * factor);
      set_capacity(new_capacity);
    }
  }
}

bool JfrStackWalkerThreadLocal::try_acquire_enqueue_lock() {
  return AtomicAccess::cmpxchg(&_lock, UNLOCKED, ENQUEUE) == UNLOCKED;
}

bool JfrStackWalkerThreadLocal::try_acquire_dequeue_lock() {
  SpinYield s;
  while (true)  {
    JfrStackWalkerLockState got = AtomicAccess::cmpxchg(&_lock, UNLOCKED, DEQUEUE);
    if (got == UNLOCKED) {
      return true; // successfully locked for dequeue
    }
    if (got == DEQUEUE) {
      return false; // already locked for dequeue
    }
    // ENQUEUE: held by a signal handler, spin-yield until released
    s.wait();
  }
}

void JfrStackWalkerThreadLocal::acquire_dequeue_lock() {
  SpinYield s;
  while (AtomicAccess::cmpxchg(&_lock, UNLOCKED, DEQUEUE) != UNLOCKED) {
    s.wait();
  }
}

void JfrStackWalkerThreadLocal::release_queue_lock() {
  AtomicAccess::release_store(&_lock, UNLOCKED);
}

void JfrStackWalkerThreadLocal::set_has_requests(bool has_requests) {
  AtomicAccess::release_store(&_has_requests, has_requests);
}

bool JfrStackWalkerThreadLocal::has_requests() const {
  return AtomicAccess::load_acquire(&_has_requests);
}

void JfrStackWalkerThreadLocal::set_do_async_processing_of_requests(bool wants) {
  AtomicAccess::release_store(&_do_async_processing_of_requests, wants);
}

bool JfrStackWalkerThreadLocal::wants_async_processing_of_requests() const {
  return AtomicAccess::load_acquire(&_do_async_processing_of_requests);
}

void JfrStackWalkerThreadLocal::set_processing_requests(bool processing) {
  _processing_requests = processing;
}

bool JfrStackWalkerThreadLocal::is_processing_requests() const {
  return _processing_requests;
}

class JfrStackWalkDequeueLocker {
  JfrStackWalkerThreadLocal& _tl;
public:
  JfrStackWalkDequeueLocker(JfrStackWalkerThreadLocal& tl) : _tl(tl) {
    _tl.acquire_dequeue_lock();
  }

  ~JfrStackWalkDequeueLocker() {
    _tl.release_queue_lock();
  }
};

class JfrStackWalkTryDequeueLocker {
  JfrStackWalkerThreadLocal& _tl;
  bool _locked;
public:
  JfrStackWalkTryDequeueLocker(JfrStackWalkerThreadLocal& tl) : _tl(tl), _locked(false) {
    _locked = _tl.try_acquire_dequeue_lock();
  }

  ~JfrStackWalkTryDequeueLocker() {
    if (_locked) {
      _tl.release_queue_lock();
    }
  }

  bool is_locked() const {
    return _locked;
  }
};

class JfrStackWalkTryEnqueueLocker {
  JfrStackWalkerThreadLocal& _tl;
  bool _locked;
public:
  JfrStackWalkTryEnqueueLocker(JfrStackWalkerThreadLocal& tl) : _tl(tl), _locked(false) {
    _locked = _tl.try_acquire_enqueue_lock();
  }

  ~JfrStackWalkTryEnqueueLocker() {
    if (_locked) {
      _tl.release_queue_lock();
    }
  }

  bool is_locked() const {
    return _locked;
  }
};

/**
 * When a stack-trace is requested from a thread that is currently in native
 * state, we can not walk the thread immediately, because that would not be
 * signal-safe. We can also not rely on walking the thread at a safepoint/handshake
 * because native threads do not participate in handshaking as long as they
 * are in native state - they might actually never get out of native.
 *
 * For these reasons, we use a separate thread to walk stacks of threads that
 * are in-native state. This thread periodically checks for native stack-walk
 * requests, and walk stacks of in-native-threads.
 */
class JfrNativeStackWalkerThread : public NonJavaThread {
  Semaphore _sample;
  volatile bool _disenrolled;
  volatile bool _is_async_processing_of_stackwalk_requests_triggered;
  DEBUG_ONLY(volatile bool _out_of_stack_walking_enabled = true;)
public:
  JfrNativeStackWalkerThread() :
    _disenrolled(true),
    _is_async_processing_of_stackwalk_requests_triggered(false) {
  }

  void start_thread() {
    if (os::create_thread(this, os::os_thread)) {
      os::start_thread(this);
    } else {
      log_error(jfr)("Failed to create thread for thread sampling");
    }
  }

  void enroll() {
    if (AtomicAccess::cmpxchg(&_disenrolled, true, false)) {
      _sample.signal();
      log_trace(jfr)("Enrolled Native stack walker");
    }
  }

  void disenroll() {
    if (!AtomicAccess::cmpxchg(&_disenrolled, false, true)) {
      _sample.wait();
      log_trace(jfr)("Disenrolled Native stack walker");
    }
  }

  // process the queues for all threads that are in native state (and requested to be processed)
  void stackwalk_threads_in_native()  {
    ResourceMark rm;
    // Prevent native stack walker from running through an ongoing safepoint.
    MutexLocker tlock(Threads_lock);
    ThreadsListHandle tlh;
    for (uint i = 0; i < tlh.list()->length(); i++) {
      JavaThread* jt = tlh.list()->thread_at(i);
      JfrStackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
      // First check if the thread has requested native stack walking.
      if (tl.wants_async_processing_of_requests()) {
        // Only consider threads that are in_native - if it isn't, then it must have
        // gone over a safepoint and have already done the stackwalk.
        // If we can't acquire the dequeue-lock, then the thread itself must have
        // acquired the dequeue-lock and will process the stackwalk by itself.
        // In both cases, we don't need to do it here.
        JfrStackWalkTryDequeueLocker lock(tl);
        if (!lock.is_locked() || jt->thread_state() != _thread_in_native) {
          tl.set_do_async_processing_of_requests(false);
          continue;
        }
        // We start stack-walking only at the last Java frame. It would not be
        // safe to walk the native frames from a foreign thread. Note that if
        // the foreign thread would get to mess with Java frame, it would first
        // have to cross a safepoint, which we prevent by holding the Threads_lock
        // above. Therefore it's safe to walk the stack from the last Java frame
        // downwards.
        // NOTE: we could walk the top native frames if we could do that
        // in the request-routine, and that would have to be made signal-safe
        // (e.g. no blocking, no allocation, etc), which seems very difficult.
        if (jt->has_last_Java_frame()) {
          // The dequeue lock is already held by the JfrStackWalkTryDequeueLocker above,
          // so call process_requests_locked to avoid re-acquiring it.
          JfrStackWalker::process_requests_locked(jt);
        } else {
          tl.set_do_async_processing_of_requests(false);
        }
      }
    }
  }


protected:
  virtual void post_run() {
    this->NonJavaThread::post_run();
    delete this;
  }

public:
  virtual const char* name() const { return "Native Stack-Walker Thread"; }
  virtual const char* type_name() const { return "NativeStackWalker"; }
  void run() {
    while (true) {
      if (!_sample.trywait()) {
        // disenrolled
        _sample.wait();
      }
      _sample.signal();

      DEBUG_ONLY(if (AtomicAccess::load_acquire(&_out_of_stack_walking_enabled)) {)
        if (AtomicAccess::cmpxchg(&_is_async_processing_of_stackwalk_requests_triggered, true, false)) {
          stackwalk_threads_in_native();
        }
      DEBUG_ONLY(})
      os::naked_sleep(100);
    }
  }

  virtual void print_on(outputStream* st) const {
    st->print("\"%s\" ", name());
    Thread::print_on(st);
    st->cr();
  }

  void trigger_async_processing_of_stackwalk_requests() {
    AtomicAccess::release_store(&_is_async_processing_of_stackwalk_requests_triggered, true);
  }

  #ifdef ASSERT
  void set_out_of_stack_walking_enabled(bool runnable) {
    AtomicAccess::release_store(&_out_of_stack_walking_enabled, runnable);
  }
  #endif
};

JfrNativeStackWalkerThread* JfrStackWalker::_native_stackwalker_thread = nullptr;
static volatile int _stackwalker_use_count = 0;

#ifdef ASSERT
// Verifies that initialize() and teardown() are not called concurrently.
class JfrStackWalkerLifecycleMark : public StackObj {
  static volatile bool _active;
 public:
  JfrStackWalkerLifecycleMark() {
    assert(!AtomicAccess::cmpxchg(&_active, false, true), "initialize() and teardown() must be serialized");
  }
  ~JfrStackWalkerLifecycleMark() {
    AtomicAccess::release_store(&_active, false);
  }
};
volatile bool JfrStackWalkerLifecycleMark::_active = false;
#endif

static bool check_state(const JavaThread* thread) {
  switch (thread->thread_state()) {
    case _thread_in_Java:
    case _thread_in_native:
      return true;
    default:
      return false;
  }
}

bool JfrStackWalker::request_stack_trace(JfrStackWalkRequest& request, JavaThread* jt, const void* context, bool thread_is_suspended) {
  JfrStackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
  JfrStackWalkerRequestQueue& queue = tl.queue();
  if (thread_is_suspended && !check_state(jt)) {
    return false;
  }

  JfrStackWalkTryEnqueueLocker lock(tl);
  if (!lock.is_locked()) {
    return false;
  }

  // When the thread is suspended, we try to build an unbiased request from the
  // thread context. Otherwise we only enqueue the biased request.
  if (thread_is_suspended) {
    // For foreign threads, assume biased stack-walking and don't even attempt
    // to build an unbiased request.
    JfrSampleRequestBuilder::build_unbiased_sample_request(request.sample_request(), context, jt);
  }

  const bool enqueued = queue.enqueue(request);
  if (enqueued) {
    if (queue.size() == 1) {
      tl.set_has_requests(true);
      SafepointMechanism::arm_local_poll_release(jt);
    }
  } else {
    queue.increment_lost_requests_due_to_queue_full();
  }

  if (jt->thread_state() == _thread_in_native) {
    if (!tl.wants_async_processing_of_requests()) {
      tl.set_do_async_processing_of_requests(true);
      trigger_async_processing_of_requests();
    }
  } else {
    tl.set_do_async_processing_of_requests(false);
  }
  return enqueued;
}

void JfrStackWalker::trigger_async_processing_of_requests() {
  // The thread is created once and never freed, but a request can race with its
  // creation. Skipping the trigger is harmless: the request stays queued and is
  // processed when its thread next polls.
  JfrNativeStackWalkerThread* const thread = AtomicAccess::load_acquire(&_native_stackwalker_thread);
  if (thread != nullptr) {
    thread->trigger_async_processing_of_stackwalk_requests();
  }
}

static traceid compute_tid(JavaThread* jt, bool in_continuation) {
  JfrThreadLocal* const tl = jt->jfr_thread_local();
  return in_continuation ? tl->vthread_id_with_epoch_update(jt)
                         : JfrThreadLocal::jvm_thread_id(jt);
}

bool JfrStackWalker::walk(JfrStackWalkRequest& request, JavaThread* jt, traceid& sid, traceid& tid, bool& biased) {
  assert(jt != nullptr, "invariant");
  sid = 0;
  biased = false;
  frame top_frame;
  bool in_continuation = false;
  if (!JfrThreadSampling::compute_top_frame(request.sample_request(), top_frame, in_continuation, jt, biased)) {
    tid = JfrThreadLocal::jvm_thread_id(jt);
    return false;
  }
  tid = compute_tid(jt, in_continuation);

  // JfrStackTrace allocates its frames in the ResourceArea.
  ResourceMark rm;
  JfrStackTrace stack_trace;
  if (!stack_trace.record(jt, top_frame, in_continuation, request.sample_request())) {
    return false;
  }
  sid = JfrStackTraceRepository::add(stack_trace);
  assert(sid != 0, "invariant");
  return true;
}

/**
 * Process queued-up stack-walk requests of the specified thread. The thread is
 * typically processed by itself (e.g. jt == JavaThread::current()). The exception
 * is when a stack-walk is requested for a thread that is in-native: in that case
 * the stack-walk-request may be processed by the JfrNativeStackWalkerThread.
 *
 * @param jt the thread to process stack-walk requests for
 */
// Process queued requests with the dequeue lock already held by the caller.
void JfrStackWalker::process_requests_locked(JavaThread* jt) {
  assert(jt != nullptr, "Java thread should not be null");

  JfrStackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
  tl.set_do_async_processing_of_requests(false);
  JfrStackWalkerRequestQueue& queue = tl.queue();
  u4 count = queue.size();
  for (u4 i = 0; i < count; i++) {
    JfrStackWalkRequest& request = queue.at(i);
    request.callback()->process(jt, request);
    request.destroy_callback();
  }
  queue.clear();
  assert(queue.is_empty(), "invariant");
  tl.set_has_requests(false);
  queue.resize_if_needed();
}

void JfrStackWalker::on_javathread_terminate(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  JfrStackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
  JfrStackWalkDequeueLocker lock(tl);
  JfrStackWalkerRequestQueue& queue = tl.queue();
  const u4 count = queue.size();
  for (u4 i = 0; i < count; i++) {
    JfrStackWalkRequest& request = queue.at(i);
    request.callback()->on_dropped(jt);
    request.destroy_callback();
  }
  queue.clear();
  tl.set_has_requests(false);
}

void JfrStackWalker::process_requests(JavaThread* jt) {
  assert(jt != nullptr, "Java thread should not be null");
  JfrStackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
  JfrStackWalkDequeueLocker lock(tl);
  process_requests_locked(jt);
}

void JfrStackWalker::initialize() {
  DEBUG_ONLY(JfrStackWalkerLifecycleMark mark;)
  // Ensure the native thread is created and published before incrementing the
  // use count. Any thread observing count > 0 via load_acquire is then
  // guaranteed to see a non-null _native_stackwalker_thread pointer.
  if (_native_stackwalker_thread == nullptr) {
    JfrNativeStackWalkerThread* thread = new JfrNativeStackWalkerThread();
    thread->start_thread();
    AtomicAccess::release_store(&_native_stackwalker_thread, thread);
  }
  // The first caller (0->1) performs enrollment and queue initialization.
  // Subsequent callers simply bump the reference count and return.
  if (AtomicAccess::fetch_then_add(&_stackwalker_use_count, 1) > 0) {
    return;
  }
  _native_stackwalker_thread->enroll();

  // Initialize queues for all existing threads. No safepoint needed here because
  // no profiling signals are active yet (timers/signal handlers are installed later).
  // JavaThreadIteratorWithHandle uses Thread-SMR for safe iteration.
  JavaThreadIteratorWithHandle jtiwh;
  for (JavaThread* jt = jtiwh.next(); jt != nullptr; jt = jtiwh.next()) {
    on_javathread_create(jt);
  }
}

void JfrStackWalker::teardown() {
  DEBUG_ONLY(JfrStackWalkerLifecycleMark mark;)
  // Decrement the use count. The last caller (1->0) disenrolls the native thread.
  if (AtomicAccess::fetch_then_add(&_stackwalker_use_count, -1) == 1) {
    assert(_native_stackwalker_thread != nullptr, "invariant");
    _native_stackwalker_thread->disenroll();
  }
}

#ifdef ASSERT
bool JfrStackWalker::set_out_of_stack_walking_enabled(bool enabled) {
  if (_native_stackwalker_thread != nullptr) {
    _native_stackwalker_thread->set_out_of_stack_walking_enabled(enabled);
    return true;
  }
  return false;
}
#endif

void JfrStackWalker::on_javathread_create(JavaThread* thread) {
  if (AtomicAccess::load_acquire(&_stackwalker_use_count) == 0) {
    return;  // JfrStackWalker not initialized yet
  }
  if (thread->is_hidden_from_external_view()) {
    return;
  }
  thread->stackwalker_thread_local().queue().init();
}

#endif // INCLUDE_JFR

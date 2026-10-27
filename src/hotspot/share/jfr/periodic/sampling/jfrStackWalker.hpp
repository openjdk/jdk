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

#ifndef SHARE_JFR_PERIODIC_SAMPLING_JFRSTACKWALKER_HPP
#define SHARE_JFR_PERIODIC_SAMPLING_JFRSTACKWALKER_HPP

#include "utilities/macros.hpp"

#if INCLUDE_JFR

#include "cppstdlib/type_traits.hpp"
#include "jfr/periodic/sampling/jfrSampleRequest.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "memory/allStatic.hpp"

class JavaThread;
class JfrNativeStackWalkerThread;
class JfrStackWalkRequest;

// The handlers of one consumer of the stack walker, e.g. JFR CPU-time sampling.
// Each consumer defines one static instance and passes it with its requests.
struct JfrStackWalkerConsumer {
  // Called to process a queued request, either by the requested thread itself at
  // a safepoint poll, or by the native stack walker thread while the requested
  // thread is in native. Implementations walk the stack with JfrStackWalker::walk(),
  // or decide not to.
  void (*process)(JavaThread* jt, JfrStackWalkRequest& request);

  // Called instead of process() when a queued request is discarded, e.g.
  // because its thread terminates.
  void (*on_dropped)(JavaThread* jt, const JfrStackWalkRequest& request);
};

// A stack-walk request: the consumer that handles it, the consumer's payload,
// and the description of the top frame. Requests are built in signal handlers
// and copied into the queue, so they must be plain data throughout.
class JfrStackWalkRequest {
  static constexpr size_t PAYLOAD_SIZE = 24;

  const JfrStackWalkerConsumer* _consumer;
  JfrSampleRequest _sample_request;
  alignas(8) u1 _payload[PAYLOAD_SIZE];

  template <typename T>
  static void check_payload() {
    static_assert(std::is_trivially_copyable<T>::value, "payload must be trivially copyable");
    static_assert(sizeof(T) <= PAYLOAD_SIZE, "payload too large");
    static_assert(alignof(T) <= 8, "payload alignment too large");
  }

public:
  template <typename T>
  JfrStackWalkRequest(const JfrStackWalkerConsumer* consumer, const T& payload) :
    _consumer(consumer),
    _sample_request() {
    check_payload<T>();
    memcpy(_payload, &payload, sizeof(T));
  }

  const JfrStackWalkerConsumer* consumer() const { return _consumer; }

  JfrSampleRequest& sample_request() { return _sample_request; }

  template <typename T>
  T payload() const {
    check_payload<T>();
    T payload;
    memcpy(&payload, _payload, sizeof(T));
    return payload;
  }
};

// Fixed size async-signal-safe SPSC linear queue backed by an array.
// Designed to be only used under lock and read linearly
class JfrStackWalkerRequestQueue {

  static constexpr u4 INITIAL_CAPACITY = 20;
  static constexpr u4 MAX_CAPACITY     = 2000;

  JfrStackWalkRequest* _data;
  volatile u4 _capacity;
  // next unfilled index
  volatile u4 _head;

  volatile u4 _lost_requests_due_to_queue_full;

public:
  JfrStackWalkerRequestQueue();

  ~JfrStackWalkerRequestQueue();

  // signal safe, but can't be interleaved with dequeue
  bool enqueue(const JfrStackWalkRequest& trace);

  JfrStackWalkRequest& at(u4 index) const;

  u4 size() const;

  // deletes all samples in the queue
  void set_capacity(u4 capacity);

  bool is_empty() const;

  void increment_lost_requests_due_to_queue_full();

  u4 get_and_reset_lost_requests_due_to_queue_full();

  void resize_if_needed();

  // init the queue capacity
  void init();

  // Frees the queue. The caller holds the dequeue lock and the queue is empty.
  void release();

  void clear();
};

class JfrStackWalkerThreadLocal {
  friend class JfrStackWalkDequeueLocker;
  friend class JfrStackWalkTryDequeueLocker;
  friend class JfrStackWalkTryEnqueueLocker;

  JfrStackWalkerRequestQueue _queue;

  // The stack-walker lock has three different states:
  // - ENQUEUE: lock for enqueuing stack-walk requests
  // - DEQUEUE: lock for dequeuing stack-walk requests
  // - UNLOCKED: no lock held
  // This ensures that we can safely enqueue and dequeue stack-walk requests,
  // without interleaving
  enum JfrStackWalkerLockState {
    UNLOCKED,
    // locked for enqueuing
    ENQUEUE,
    // locked for dequeuing
    DEQUEUE
  };
  volatile JfrStackWalkerLockState _lock;

  volatile bool _has_requests;

  volatile bool _do_async_processing_of_requests;

  bool _processing_requests;

  bool try_acquire_enqueue_lock();
  bool try_acquire_dequeue_lock();
  void acquire_dequeue_lock();
  void release_queue_lock();

public:

  JfrStackWalkerThreadLocal() :
    _lock(UNLOCKED),
    _has_requests(false),
    _do_async_processing_of_requests(false),
    _processing_requests(false) {}

  JfrStackWalkerRequestQueue& queue() {
    return _queue;
  }

  void set_has_requests(bool has_events);
  bool has_requests() const;

  void set_do_async_processing_of_requests(bool wants);
  bool wants_async_processing_of_requests() const;

  void set_processing_requests(bool processing);
  bool is_processing_requests() const;

};

/**
 * A facility to get unbiased, precise stack-traces from a running
 * thread.
 */
class JfrStackWalker : public AllStatic {
  friend class JfrNativeStackWalkerThread;

  static JfrNativeStackWalkerThread* _native_stackwalker_thread;

  static void trigger_async_processing_of_requests();
  static void process_requests(JavaThread* jt);
  // Process requests with the dequeue lock already held by the caller.
  static void process_requests_locked(JavaThread* jt);

public:
  // Reference-counted start and stop of the stack walker, one pair per user.
  // Callers must be serialized; JFR applies event settings, which drive both
  // users, under the MetadataRepository lock.
  static void initialize();
  static void teardown();

  // Called when a new Java thread is created to initialize its queue.
  static void on_javathread_create(JavaThread* thread);

  // Called when a Java thread terminates to discard its pending requests.
  static void on_javathread_terminate(JavaThread* thread);

  // Request a stack trace. Returns false if the request was dropped, in which
  // case its consumer is never called. Accounting for dropped requests is up
  // to the caller.
  // This API is signal-safe (no allocations).
  static bool request_stack_trace(JfrStackWalkRequest& request, JavaThread* jt, const void* context, bool thread_is_suspended);

  // Walks the stack described by a request and adds the trace to the stack-trace
  // repository. Only valid from JfrStackWalkerConsumer::process. Returns false if
  // no trace could be obtained, in which case sid is 0. tid is the thread id to
  // report: the vthread id if the trace is in a continuation, else the jvm thread id.
  static bool walk(JfrStackWalkRequest& request, JavaThread* jt, traceid& sid, traceid& tid, bool& biased);

  // Entry point for the runtime to trigger stack-walk processing.
  static inline void check_and_process_requests(JavaThread* jt);

  DEBUG_ONLY(static bool set_out_of_stack_walking_enabled(bool enabled);)
};

#endif // INCLUDE_JFR
#endif // SHARE_JFR_PERIODIC_SAMPLING_JFRSTACKWALKER_HPP

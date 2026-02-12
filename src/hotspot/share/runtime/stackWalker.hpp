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

#ifndef SHARE_RUNTIME_STACKWALKER_HPP
#define SHARE_RUNTIME_STACKWALKER_HPP

#include "memory/allStatic.hpp"

class JavaThread;
class NativeStackWalkerThread;

enum class StackWalkerFrameType {
  FRAME_INTERPRETER,
  FRAME_JIT,
  FRAME_INLINE,
  FRAME_NATIVE,
};

class StackWalkerCallback {
public:
  // Prevent heap allocation - only embedded storage via placement new is allowed.
  void* operator new(size_t size) = delete;
  void* operator new[](size_t size) = delete;
  void operator delete[](void* p) = delete;
  // Placement new for embedded storage in StackWalkRequest.
  void* operator new(size_t size, void* ptr) { return ptr; }
  // No-op: destructor is called explicitly, never via delete.
  void operator delete(void* p) {}

  virtual ~StackWalkerCallback() = default;

  virtual void begin_stacktrace(JavaThread* jt, bool continuation, bool biased) = 0;
  virtual void end_stacktrace(bool truncated) = 0;
  virtual void stack_frame(const Method* method, int bci, int line_no, StackWalkerFrameType type) = 0;
  virtual void failure() = 0;
};

class StackWalkRequest {
  // Embedded storage for the callback object to avoid heap allocation in signal handlers.
  // Size must accommodate both JfrCPUTimeStackWalkerCallback and JvmtiStackWalkerCallback.
  static constexpr size_t CALLBACK_STORAGE_SIZE = 64;
  alignas(16) char _callback_storage[CALLBACK_STORAGE_SIZE];
  u4 _max_frames;
public:
  void* _sample_sp;
  void* _sample_pc;
  void* _sample_bcp;

  StackWalkRequest() :
    _max_frames(0),
    _sample_sp(nullptr),
    _sample_pc(nullptr),
    _sample_bcp(nullptr) {}

  void set_max_frames(u4 max_frames) { _max_frames = max_frames; }
  u4 max_frames() const { return _max_frames; }

  // Returns raw storage for placement new of callback objects.
  void* callback_storage() { return _callback_storage; }

  // Returns the callback pointer (assumes callback was constructed in storage).
  StackWalkerCallback* callback() const {
    return reinterpret_cast<StackWalkerCallback*>(const_cast<char*>(_callback_storage));
  }

  // Call destructor on the callback (must be called after processing).
  void destroy_callback() {
    callback()->~StackWalkerCallback();
  }
};

// Fixed size async-signal-safe SPSC linear queue backed by an array.
// Designed to be only used under lock and read linearly
class StackWalkerRequestQueue {

  static constexpr u4 INITIAL_CAPACITY = 20;
  static constexpr u4 MAX_CAPACITY     = 2000;

  StackWalkRequest* _data;
  volatile u4 _capacity;
  // next unfilled index
  volatile u4 _head;

  volatile u4 _lost_requests;
  volatile u4 _lost_requests_due_to_queue_full;

  static volatile u4 _lost_requests_sum;

public:
  StackWalkerRequestQueue();

  ~StackWalkerRequestQueue();

  // signal safe, but can't be interleaved with dequeue
  bool enqueue(const StackWalkRequest& trace);

  StackWalkRequest& at(u4 index);

  u4 size() const;

  void set_size(u4 size);

  u4 capacity() const;

  // deletes all samples in the queue
  void set_capacity(u4 capacity);

  bool is_empty() const;

  u4 lost_requests() const;

  void increment_lost_requests();

  void increment_lost_requests_due_to_queue_full();

  // returns the previous lost samples count
  u4 get_and_reset_lost_requests();

  u4 get_and_reset_lost_requests_due_to_queue_full();

  void resize_if_needed();

  // init the queue capacity
  void init();

  void clear();
};

class StackWalkerThreadLocal {
  StackWalkerRequestQueue _queue;

  enum StackWalkerLockState {
    UNLOCKED,
    // locked for enqueuing
    ENQUEUE,
    // locked for dequeuing
    DEQUEUE
  };
  volatile StackWalkerLockState _lock;

  volatile bool _has_requests;

  volatile bool _do_async_processing_of_requests;

  bool _critical_section;

  bool _processing_requests;

  bool _vthread;

public:

  StackWalkerThreadLocal() :
    _lock(UNLOCKED),
    _has_requests(false),
    _do_async_processing_of_requests(false),
    _critical_section(false),
    _processing_requests(false),
    _vthread(false) {}

  static void on_set_current_thread(JavaThread* thread, oop thread_obj);
  static bool is_vthread(JavaThread* jt);

  bool in_critical_section() const {
    return _critical_section;
  }

  static ByteSize critical_section_offset() {
    return byte_offset_of(StackWalkerThreadLocal, _critical_section);
  }

  StackWalkerRequestQueue& queue() {
    return _queue;
  }

  // The stack-walker lock has three different states:
  // - ENQUEUE: lock for enqueuing stack-walk requests
  // - DEQUEUE: lock for dequeuing stack-walk requests
  // - UNLOCKED: no lock held
  // This ensures that we can safely enqueue and dequeue stack-walk requests,
  // without interleaving

  bool is_enqueue_locked() const;
  bool is_dequeue_locked() const;

  bool try_acquire_enqueue_lock();
  bool try_acquire_dequeue_lock();
  void acquire_dequeue_lock();
  void release_queue_lock();

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
class StackWalker : public AllStatic {
  friend class NativeStackWalkerThread;

  static NativeStackWalkerThread* _native_stackwalker_thread;

  static void build_stack_walk_request(StackWalkRequest& request, const void* ucontext, JavaThread* java_thread);
  static void trigger_async_processing_of_requests();
  static void process_requests(const Thread* current, JavaThread* jt, bool lock);

public:
  static void initialize();
  // Called when a new Java thread is created to initialize its queue.
  static void on_javathread_create(JavaThread* thread);
  // Request a stack trace. The caller must prepare the request by:
  // 1. Constructing a callback in request.callback_storage() using placement new
  // 2. Setting request.set_max_frames()
  // This API is signal-safe (no allocations).
  static void request_stack_trace(StackWalkRequest& request, JavaThread* jt, const void* context);

  // Entry point for the runtime to trigger stack-walk processing.
  static inline void check_and_process_requests(JavaThread* jt);

  DEBUG_ONLY(static bool set_out_of_stack_walking_enabled(bool enabled);)
};

#endif // SHARE_RUNTIME_STACKWALKER_HPP
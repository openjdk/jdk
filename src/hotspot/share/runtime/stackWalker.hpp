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

#include "utilities/macros.hpp"

#if INCLUDE_STACKWALKER

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

  // Called to report that requests were lost (e.g., due to lock contention
  // or a full queue). The count is the number of lost requests since the
  // last successful drain. Default is a no-op; subclasses that care about
  // lost request diagnostics (e.g., JFR CPU-time sampling) should override.
  virtual void report_lost_requests(JavaThread* jt, u4 count) {}
};

class StackWalkRequest {
public:
  // Embedded storage constants for the callback object.
  // Avoids heap allocation in signal handlers.
  // Size/alignment are enforced at compile time by construct_callback().
  static constexpr size_t CALLBACK_STORAGE_SIZE = 64;
  static constexpr size_t CALLBACK_STORAGE_ALIGNMENT = 16;

private:
  alignas(CALLBACK_STORAGE_ALIGNMENT) char _callback_storage[CALLBACK_STORAGE_SIZE];
  u4 _max_frames;
  void* _sample_sp;
  void* _sample_pc;
  void* _sample_bcp;

public:
  StackWalkRequest() :
    _max_frames(0),
    _sample_sp(nullptr),
    _sample_pc(nullptr),
    _sample_bcp(nullptr) {}

  void set_max_frames(u4 max_frames) { _max_frames = max_frames; }
  u4 max_frames() const { return _max_frames; }

  void* sample_sp() const { return _sample_sp; }
  void set_sample_sp(void* sp) { _sample_sp = sp; }
  void* sample_pc() const { return _sample_pc; }
  void set_sample_pc(void* pc) { _sample_pc = pc; }
  void* sample_bcp() const { return _sample_bcp; }
  void set_sample_bcp(void* bcp) { _sample_bcp = bcp; }

  // Construct a callback in the embedded storage.
  // Enforces size and alignment constraints at compile time.
  template <typename T, typename... Args>
  T* construct_callback(Args&&... args) {
    static_assert(sizeof(T) <= CALLBACK_STORAGE_SIZE,
                  "Callback too large for StackWalkRequest embedded storage");
    static_assert(alignof(T) <= CALLBACK_STORAGE_ALIGNMENT,
                  "Callback alignment exceeds StackWalkRequest embedded storage alignment");
    return new (_callback_storage) T(static_cast<Args&&>(args)...);
  }

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

  StackWalkRequest& at(u4 index) const;

  u4 size() const;

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
  friend class StackWalkDequeueLocker;
  friend class StackWalkTryDequeueLocker;
  friend class StackWalkTryEnqueueLocker;

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

  volatile bool _vthread;

  bool try_acquire_enqueue_lock();
  bool try_acquire_dequeue_lock();
  void acquire_dequeue_lock();
  void release_queue_lock();

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
  static void process_requests(JavaThread* jt);
  // Process requests with the dequeue lock already held by the caller.
  static void process_requests_locked(JavaThread* jt);

public:
  static void initialize();

  // Called when a new Java thread is created to initialize its queue.
  static void on_javathread_create(JavaThread* thread);

  // Request a stack trace. The caller must prepare the request by:
  // 1. Constructing a callback via request.construct_callback<T>(...)
  // 2. Setting request.set_max_frames()
  // This API is signal-safe (no allocations).
  static void request_stack_trace(StackWalkRequest& request, JavaThread* jt, const void* context, bool thread_is_suspended);

  // Entry point for the runtime to trigger stack-walk processing.
  static inline void check_and_process_requests(JavaThread* jt);

  DEBUG_ONLY(static bool set_out_of_stack_walking_enabled(bool enabled);)
};

#endif // INCLUDE_STACKWALKER
#endif // SHARE_RUNTIME_STACKWALKER_HPP
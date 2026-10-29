/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

#include "classfile/javaClasses.inline.hpp"
#include "jfr/periodic/sampling/jfrStackWalker.hpp"
#include "jfr/recorder/stacktrace/jfrStackFrame.hpp"
#include "jfr/recorder/stacktrace/jfrStackTrace.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "oops/method.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiStackTraceRequest.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.hpp"
#include "utilities/spinYield.hpp"

// The state of one JVMTI environment. Environments are independent: each has
// its own completion list, and disabling one does not affect the others.
class JvmtiStackTraceRequestState : public CHeapObj<mtServiceability> {
 public:
  volatile bool _enabled;
  bool _enable_at_vm_init;
  // The agent's completion list, or null if the agent polls the buffers.
  jvmtiStackTraceBuffer** volatile _completed;
  jvmtiStackTraceBuffer** _completed_at_vm_init;
  // Requests between their enabled check and their enqueueing, so that disable()
  // can wait for them before discarding what is queued.
  volatile int _submitting;

  JvmtiStackTraceRequestState() :
    _enabled(false), _enable_at_vm_init(false), _completed(nullptr),
    _completed_at_vm_init(nullptr), _submitting(0) {}
};

struct JvmtiStackTracePayload {
  jvmtiStackTraceBuffer* buffer;
  JvmtiStackTraceRequestState* state;
};

// Serializes enable(), disable() and their variants.
static volatile int _lock = 0;

class JvmtiStackTraceRequestLocker : public StackObj {
 public:
  JvmtiStackTraceRequestLocker() {
    SpinYield s;
    while (AtomicAccess::cmpxchg(&_lock, 0, 1) != 0) {
      s.wait();
    }
  }
  ~JvmtiStackTraceRequestLocker() {
    AtomicAccess::release_store(&_lock, 0);
  }
};

static jint frame_type(u1 type) {
  switch (type) {
    case JfrStackFrame::FRAME_INTERPRETER: return JVMTI_STACK_FRAME_INTERPRETED;
    case JfrStackFrame::FRAME_JIT:         return JVMTI_STACK_FRAME_COMPILED;
    case JfrStackFrame::FRAME_INLINE:      return JVMTI_STACK_FRAME_INLINED;
    case JfrStackFrame::FRAME_NATIVE:      return JVMTI_STACK_FRAME_NATIVE;
    default: ShouldNotReachHere(); return JVMTI_STACK_FRAME_COMPILED;
  }
}

// Writes the visited frames into the agent's buffer: inline first, then into an
// overflow array that grows as needed, allocated like JVMTI Allocate does so
// that the agent can release it with Deallocate.
class JvmtiBufferCollector : public JfrStackFrameVisitor {
  jvmtiStackTraceBuffer* const _buffer;
  const jint _max_frames;
  jint _count;
  jint _overflow_capacity;
  jvmtiStackTraceFrame* _overflow;
  bool _truncated;

  bool store(const jvmtiStackTraceFrame& frame) {
    if (_count < _buffer->frame_capacity) {
      _buffer->frames[_count] = frame;
      return true;
    }
    const jint index = _count - _buffer->frame_capacity;
    if (index == _overflow_capacity) {
      const jint capacity = MIN2(MAX2(2 * _overflow_capacity, (jint)64), _max_frames - _buffer->frame_capacity);
      void* const overflow = os::realloc(_overflow, capacity * sizeof(jvmtiStackTraceFrame), mtServiceability);
      if (overflow == nullptr) {
        return false;
      }
      _overflow = static_cast<jvmtiStackTraceFrame*>(overflow);
      _overflow_capacity = capacity;
    }
    _overflow[index] = frame;
    return true;
  }

 public:
  JvmtiBufferCollector(jvmtiStackTraceBuffer* buffer) :
    _buffer(buffer),
    _max_frames(buffer->max_depth),
    _count(0), _overflow_capacity(0), _overflow(nullptr), _truncated(false) {}

  ~JvmtiBufferCollector() {
    os::free(_overflow); // only if not handed over
  }

  bool has_room() override {
    if (_count >= _max_frames) {
      _truncated = true;
      return false;
    }
    return true;
  }

  bool visit(const Method* method, int bci, u1 type) override {
    if (!has_room()) {
      return false;
    }
    // jmethodIDs stay safe to use after class unloading, unlike Method*.
    const jvmtiStackTraceFrame frame = {
      const_cast<Method*>(method)->jmethod_id(),
      type == JfrStackFrame::FRAME_NATIVE ? (jlocation)-1 : (jlocation)bci,
      frame_type(type)
    };
    if (!store(frame)) {
      _truncated = true; // out of memory for the overflow array
      return false;
    }
    _count++;
    return true;
  }

  jint count() const { return _count; }
  bool truncated() const { return _truncated; }

  jvmtiStackTraceFrame* release_overflow() {
    jvmtiStackTraceFrame* const overflow = _overflow;
    _overflow = nullptr;
    return overflow;
  }
};

// Hands the buffer back to the agent: publishes the result through status and,
// if the agent registered one, pushes the buffer onto its completion list. With
// a list, the agent owns the buffer once it popped it from the list.
static void complete(const JvmtiStackTracePayload& payload, jint status) {
  jvmtiStackTraceBuffer* const buffer = payload.buffer;
  AtomicAccess::release_store(&buffer->status, status);
  jvmtiStackTraceBuffer** const head = AtomicAccess::load_acquire(&payload.state->_completed);
  if (head != nullptr) {
    jvmtiStackTraceBuffer* old_head;
    do {
      old_head = AtomicAccess::load(head);
      buffer->next = old_head;
    } while (AtomicAccess::cmpxchg(head, old_head, buffer) != old_head);
  }
}

static jlong thread_id(JavaThread* jt, bool in_continuation) {
  if (!in_continuation) {
    return (jlong)JfrThreadLocal::jvm_thread_id(jt);
  }
  // Only the thread itself may read its virtual thread; the native stack walker
  // thread must not touch oops. Prototype limitation: unknown in that case.
  return Thread::current() == jt ? java_lang_Thread::thread_id(jt->vthread()) : 0;
}

// Runs where the stack walker processes requests: the requested thread at a
// safepoint poll, possibly in a leaf context, or the native stack walker thread.
// Writes only into the agent's buffer and, for deep stacks, allocates.
static void process_stack_trace(JavaThread* jt, JfrStackWalkRequest& request) {
  const JvmtiStackTracePayload payload = request.payload<JvmtiStackTracePayload>();
  jvmtiStackTraceBuffer* const buffer = payload.buffer;
  JvmtiBufferCollector collector(buffer);
  bool biased;
  bool in_continuation;
  // A walk that stopped at max_depth, even at 0 frames, succeeded; one that
  // found no frames at all did not.
  const bool walked = JfrStackWalker::walk_frames(request, jt, collector, biased, in_continuation) &&
                      (collector.count() > 0 || collector.truncated());
  buffer->count = walked ? collector.count() : 0;
  buffer->biased = walked && biased;
  buffer->truncated = walked && collector.truncated();
  buffer->thread_id = thread_id(jt, walked && in_continuation);
  buffer->overflow = walked ? collector.release_overflow() : nullptr;
  complete(payload, walked ? JVMTI_STACK_TRACE_DONE : JVMTI_STACK_TRACE_FAILED);
}

static void drop_stack_trace(JavaThread* jt, const JfrStackWalkRequest& request) {
  const JvmtiStackTracePayload payload = request.payload<JvmtiStackTracePayload>();
  jvmtiStackTraceBuffer* const buffer = payload.buffer;
  buffer->count = 0;
  buffer->biased = false;
  buffer->truncated = false;
  buffer->thread_id = (jlong)JfrThreadLocal::jvm_thread_id(jt);
  buffer->overflow = nullptr;
  complete(payload, JVMTI_STACK_TRACE_DROPPED);
}

static const JfrStackWalkerConsumer stack_trace_consumer = {
  process_stack_trace,
  drop_stack_trace
};

static bool is_requested_by(const JfrStackWalkRequest& request, const void* state) {
  return request.payload<JvmtiStackTracePayload>().state == state;
}

static void start(JvmtiStackTraceRequestState* state, jvmtiStackTraceBuffer** completed) {
  JfrStackWalker::initialize();
  AtomicAccess::release_store(&state->_completed, completed);
  AtomicAccess::release_store(&state->_enabled, true);
}

static void stop(JvmtiStackTraceRequestState* state) {
  AtomicAccess::release_store(&state->_enabled, false);
  OrderAccess::fence();
  // Wait for this environment's requests that passed their enabled check, then
  // complete every one of them still queued as dropped. Requests being processed
  // hold their thread's queue lock, so discarding waits for them too. Afterwards
  // the VM never touches this environment's buffers or list again.
  SpinYield s;
  while (AtomicAccess::load_acquire(&state->_submitting) != 0) {
    s.wait();
  }
  JfrStackWalker::discard_requests(&stack_trace_consumer, is_requested_by, state);
  AtomicAccess::release_store(&state->_completed, (jvmtiStackTraceBuffer**)nullptr);
  JfrStackWalker::teardown();
}

bool JvmtiStackTraceRequest::is_enabled(JvmtiEnv* env) {
  JvmtiStackTraceRequestState* const state = env->stack_trace_requests();
  return state != nullptr && AtomicAccess::load_acquire(&state->_enabled);
}

jvmtiError JvmtiStackTraceRequest::enable(JvmtiEnv* env, jvmtiStackTraceBuffer** completed) {
  JvmtiStackTraceRequestLocker lock;
  JvmtiStackTraceRequestState* state = env->stack_trace_requests();
  if (state == nullptr) {
    state = new JvmtiStackTraceRequestState();
    env->set_stack_trace_requests(state);
  }
  if (AtomicAccess::load(&state->_enabled) || state->_enable_at_vm_init) {
    return JVMTI_ERROR_NONE;
  }
  if (JvmtiEnv::get_phase() != JVMTI_PHASE_LIVE) {
    // Typically called from Agent_OnLoad, before threads can be started.
    state->_enable_at_vm_init = true;
    state->_completed_at_vm_init = completed;
    return JVMTI_ERROR_NONE;
  }
  start(state, completed);
  return JVMTI_ERROR_NONE;
}

void JvmtiStackTraceRequest::on_vm_initialized() {
  JvmtiStackTraceRequestLocker lock;
  JvmtiEnvIterator it;
  for (JvmtiEnvBase* env = it.first(); env != nullptr; env = it.next(env)) {
    JvmtiStackTraceRequestState* const state = env->stack_trace_requests();
    if (state != nullptr && state->_enable_at_vm_init) {
      state->_enable_at_vm_init = false;
      start(state, state->_completed_at_vm_init);
    }
  }
}

jvmtiError JvmtiStackTraceRequest::disable(JvmtiEnv* env) {
  JvmtiStackTraceRequestLocker lock;
  JvmtiStackTraceRequestState* const state = env->stack_trace_requests();
  if (state == nullptr) {
    return JVMTI_ERROR_NONE;
  }
  if (state->_enable_at_vm_init) {
    state->_enable_at_vm_init = false;
    return JVMTI_ERROR_NONE;
  }
  if (AtomicAccess::load(&state->_enabled)) {
    stop(state);
  }
  return JVMTI_ERROR_NONE;
}

void JvmtiStackTraceRequest::on_env_dispose(JvmtiEnv* env) {
  if (env->stack_trace_requests() == nullptr) {
    return;
  }
  // Disabling waits for other threads, so do it in native state, like an agent would.
  Thread* const current = Thread::current_or_null();
  if (current != nullptr && current->is_Java_thread() && JavaThread::cast(current)->thread_state() == _thread_in_vm) {
    ThreadToNativeFromVM transition(JavaThread::cast(current));
    disable(env);
  } else {
    disable(env);
  }
  JvmtiStackTraceRequestLocker lock;
  JvmtiStackTraceRequestState* const state = env->stack_trace_requests();
  env->set_stack_trace_requests(nullptr);
  delete state;
}

bool JvmtiStackTraceRequest::request(JvmtiEnv* env, jvmtiStackTraceBuffer* buffer, JavaThread* target,
                                     const void* ucontext, bool thread_is_suspended) {
  assert(target != nullptr, "invariant");
  JvmtiStackTraceRequestState* const state = env->stack_trace_requests();
  if (state == nullptr) {
    return false;
  }
  AtomicAccess::inc(&state->_submitting);
  bool accepted = false;
  if (AtomicAccess::load_acquire(&state->_enabled)) {
    // Before enqueueing: another thread may complete the request right away.
    buffer->status = JVMTI_STACK_TRACE_PENDING;
    const JvmtiStackTracePayload payload = { buffer, state };
    JfrStackWalkRequest request(&stack_trace_consumer, payload);
    accepted = JfrStackWalker::request_stack_trace(request, target, ucontext, thread_is_suspended);
  }
  AtomicAccess::dec(&state->_submitting);
  return accepted;
}

#endif // INCLUDE_JFR

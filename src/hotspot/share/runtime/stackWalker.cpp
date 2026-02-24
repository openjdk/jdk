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

#if INCLUDE_STACKWALKER

#include "code/codeCache.hpp"
#include "code/debugInfoRec.hpp"
#include "gc/shared/gc_globals.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/continuation.hpp"
#include "runtime/frame.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/stackWalker.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vframe.inline.hpp"
#include "utilities/spinYield.hpp"

volatile u4 StackWalkerRequestQueue::_lost_requests_sum = 0;

class StackWalkerVframeStream : public vframeStreamCommon {
  bool _vthread;

  void next_frame() {
    do {
      if (_vthread && Continuation::is_continuation_enterSpecial(_frame)) {
        if (_cont_entry->is_virtual_thread()) {
          // An entry of a vthread continuation is a termination point.
          _mode = at_end_mode;
          break;
        }
        _cont_entry = _cont_entry->parent();
      }

      _frame = _frame.sender(&_reg_map);

    } while (!fill_from_frame());
  }

  static RegisterMap::WalkContinuation walk_continuation(JavaThread* jt) {
    // NOTE: WalkContinuation::skip, because of interactions with ZGC relocation
    //       and load barriers. This code is run while generating stack traces for
    //       the ZPage allocation event, even when ZGC is relocating  objects.
    //       When ZGC is relocating, it is forbidden to run code that performs
    //       load barriers. With WalkContinuation::include, we visit heap stack
    //       chunks and could be using load barriers.
    //
    // NOTE: Shenandoah GC also seems to require this check - actual details as to why
    //       is unknown but to be filled in by others.
    return (UseZGC || UseShenandoahGC) && !StackWatermarkSet::processing_started(jt)
      ? RegisterMap::WalkContinuation::skip
      : RegisterMap::WalkContinuation::include;
  }
public:
  StackWalkerVframeStream(JavaThread* jt, const frame& fr, bool in_continuation, bool stop_at_java_call_stub) :
    vframeStreamCommon(jt, RegisterMap::UpdateMap::skip, RegisterMap::ProcessFrames::skip, walk_continuation(jt)),
    _vthread(in_continuation) {
    assert(!_vthread || StackWalkerThreadLocal::is_vthread(jt), "invariant");
    if (in_continuation) {
      _cont_entry = jt->last_continuation();
      assert(_cont_entry != nullptr, "invariant");
    }
    _frame = fr;
    _stop_at_java_call_stub = stop_at_java_call_stub;
    while (!fill_from_frame()) {
      _frame = _frame.sender(&_reg_map);
    }
  }

  void next_vframe() {
    // handle frames with inlining
    if (_mode == compiled_mode && fill_in_compiled_inlined_sender()) {
      return;
    }
    next_frame();
  }
};

u4 StackWalkerRequestQueue::size() const {
  return AtomicAccess::load_acquire(&_head);
}

StackWalkerRequestQueue::StackWalkerRequestQueue():
   _data(nullptr), _capacity(0), _head(0), _lost_requests(0), _lost_requests_due_to_queue_full(0) {
}

StackWalkerRequestQueue::~StackWalkerRequestQueue() {
  if (_data != nullptr) {
    assert(_capacity != 0, "invariant");
    FREE_C_HEAP_ARRAY(StackWalkRequest, _data);
  }
}

bool StackWalkerRequestQueue::enqueue(const StackWalkRequest& request) {
  static_assert(std::is_trivially_copyable<StackWalkRequest>::value,
                "StackWalkRequest must be trivially copyable for queue storage");
  // Note: assertions about JavaThread::current() are not valid here because
  // cross-thread enqueuing (e.g., from JFR wall-clock sampler) calls this
  // from a non-JavaThread on behalf of the target thread.
  u4 elementIndex;
  do {
    elementIndex = AtomicAccess::load_acquire(&_head);
    if (elementIndex >= _capacity) {
      return false;
    }
  } while (AtomicAccess::cmpxchg(&_head, elementIndex, elementIndex + 1) != elementIndex);
  _data[elementIndex] = request;
  return true;
}

StackWalkRequest& StackWalkerRequestQueue::at(u4 index) const {
  assert(index < _head, "invariant");
  return _data[index];
}

void StackWalkerRequestQueue::set_capacity(u4 capacity) {
  if (capacity == AtomicAccess::load(&_capacity)) {
    return;
  }
  _head = 0;
  if (_data != nullptr) {
    assert(_capacity != 0, "invariant");
    FREE_C_HEAP_ARRAY(StackWalkRequest, _data);
  }
  if (capacity != 0) {
    _data = NEW_C_HEAP_ARRAY(StackWalkRequest, capacity, mtOther);
  } else {
    _data = nullptr;
  }
  AtomicAccess::release_store(&_capacity, capacity);
}

bool StackWalkerRequestQueue::is_empty() const {
  return AtomicAccess::load_acquire(&_head) == 0;
}

u4 StackWalkerRequestQueue::lost_requests() const {
  return AtomicAccess::load(&_lost_requests);
}

void StackWalkerRequestQueue::increment_lost_requests() {
  AtomicAccess::inc(&_lost_requests_sum);
  AtomicAccess::inc(&_lost_requests);
}

void StackWalkerRequestQueue::increment_lost_requests_due_to_queue_full() {
  AtomicAccess::inc(&_lost_requests_due_to_queue_full);
}

u4 StackWalkerRequestQueue::get_and_reset_lost_requests() {
  return AtomicAccess::xchg(&_lost_requests, (u4)0);
}

u4 StackWalkerRequestQueue::get_and_reset_lost_requests_due_to_queue_full() {
  return AtomicAccess::xchg(&_lost_requests_due_to_queue_full, (u4)0);
}

void StackWalkerRequestQueue::init() {
  set_capacity(INITIAL_CAPACITY);
}

void StackWalkerRequestQueue::clear() {
  AtomicAccess::release_store(&_head, (u4)0);
}

void StackWalkerRequestQueue::resize_if_needed() {
  u4 lost_requests_due_to_queue_full = get_and_reset_lost_requests_due_to_queue_full();
  if (lost_requests_due_to_queue_full == 0) {
    return;
  }
  u4 capacity = AtomicAccess::load(&_capacity);
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

bool StackWalkerThreadLocal::is_enqueue_locked() const {
  return AtomicAccess::load_acquire(&_lock) == ENQUEUE;
}

bool StackWalkerThreadLocal::is_dequeue_locked() const {
  return AtomicAccess::load_acquire(&_lock) == DEQUEUE;
}

bool StackWalkerThreadLocal::try_acquire_enqueue_lock() {
  return AtomicAccess::cmpxchg(&_lock, UNLOCKED, ENQUEUE) == UNLOCKED;
}

bool StackWalkerThreadLocal::try_acquire_dequeue_lock() {
  SpinYield s;
  while (true)  {
    StackWalkerLockState got = AtomicAccess::cmpxchg(&_lock, UNLOCKED, DEQUEUE);
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

void StackWalkerThreadLocal::acquire_dequeue_lock() {
  SpinYield s;
  while (AtomicAccess::cmpxchg(&_lock, UNLOCKED, DEQUEUE) != UNLOCKED) {
    s.wait();
  }
}

void StackWalkerThreadLocal::release_queue_lock() {
  AtomicAccess::release_store(&_lock, UNLOCKED);
}

void StackWalkerThreadLocal::set_has_requests(bool has_requests) {
  AtomicAccess::release_store(&_has_requests, has_requests);
}

bool StackWalkerThreadLocal::has_requests() const {
  return AtomicAccess::load_acquire(&_has_requests);
}

void StackWalkerThreadLocal::set_do_async_processing_of_requests(bool wants) {
  AtomicAccess::release_store(&_do_async_processing_of_requests, wants);
}

bool StackWalkerThreadLocal::wants_async_processing_of_requests() const {
  return AtomicAccess::load_acquire(&_do_async_processing_of_requests);
}

void StackWalkerThreadLocal::set_processing_requests(bool processing) {
  _processing_requests = processing;
}

bool StackWalkerThreadLocal::is_processing_requests() const {
  return _processing_requests;
}

bool StackWalkerThreadLocal::is_vthread(JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  return AtomicAccess::load_acquire(&jt->stackwalker_thread_local()._vthread) && jt->last_continuation() != nullptr;
}

class StackWalkDequeueLocker {
  StackWalkerThreadLocal& _tl;
public:
  StackWalkDequeueLocker(StackWalkerThreadLocal& tl) : _tl(tl) {
    _tl.acquire_dequeue_lock();
  }

  ~StackWalkDequeueLocker() {
    _tl.release_queue_lock();
  }
};

class StackWalkTryDequeueLocker {
  StackWalkerThreadLocal& _tl;
  bool _locked;
public:
  StackWalkTryDequeueLocker(StackWalkerThreadLocal& tl) : _tl(tl), _locked(false) {
    _locked = _tl.try_acquire_dequeue_lock();
  }

  ~StackWalkTryDequeueLocker() {
    if (_locked) {
      _tl.release_queue_lock();
    }
  }

  bool is_locked() const {
    return _locked;
  }
};

class StackWalkTryEnqueueLocker {
  StackWalkerThreadLocal& _tl;
  bool _locked;
public:
  StackWalkTryEnqueueLocker(StackWalkerThreadLocal& tl) : _tl(tl), _locked(false) {
    _locked = _tl.try_acquire_enqueue_lock();
  }

  ~StackWalkTryEnqueueLocker() {
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
class NativeStackWalkerThread : public NonJavaThread {
  Semaphore _sample;
  volatile bool _disenrolled;
  volatile bool _is_async_processing_of_stackwalk_requests_triggered;
  DEBUG_ONLY(volatile bool _out_of_stack_walking_enabled = true;)
public:
  NativeStackWalkerThread() :
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
    for (size_t i = 0; i < tlh.list()->length(); i++) {
      JavaThread* jt = tlh.list()->thread_at(i);
      StackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
      // First check if the thread has requested native stack walking.
      if (tl.wants_async_processing_of_requests()) {
        // Only consider threads that are in_native - if it isn't, then it must have
        // gone over a safepoint and have already done the stackwalk.
        // If we can't acquire the dequeue-lock, then the thread itself must have
        // acquired the dequeue-lock and will process the stackwalk by itself.
        // In both cases, we don't need to do it here.
        StackWalkTryDequeueLocker lock(tl);
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
          // The dequeue lock is already held by the StackWalkTryDequeueLocker above,
          // so call process_requests_locked to avoid re-acquiring it.
          StackWalker::process_requests_locked(jt);
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

NativeStackWalkerThread* StackWalker::_native_stackwalker_thread = nullptr;
static volatile int _stackwalker_initialized = 0;

static bool is_entry_frame(address pc) {
  return StubRoutines::returns_to_call_stub(pc);
}

static bool is_entry_frame(const StackWalkRequest& request) {
  return is_entry_frame(static_cast<address>(request.sample_pc()));
}

static bool is_interpreter(address pc) {
  return Interpreter::contains(pc);
}

static bool is_interpreter(const StackWalkRequest& request) {
  return is_interpreter(static_cast<address>(request.sample_pc()));
}

static address interpreter_frame_bcp(const StackWalkRequest& request) {
  assert(is_interpreter(request), "invariant");
  return frame::interpreter_bcp(static_cast<intptr_t*>(request.sample_bcp()));
}

static bool in_stack(intptr_t* ptr, const JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  return jt->is_in_full_stack_checked(reinterpret_cast<address>(ptr));
}

#ifdef ASSERT
static bool sp_in_stack(const StackWalkRequest& request, const JavaThread* jt) {
  return in_stack(static_cast<intptr_t*>(request.sample_sp()), jt);
}
#endif // ASSERT

static bool fp_in_stack(const StackWalkRequest& request, const JavaThread* jt) {
  return in_stack(static_cast<intptr_t*>(request.sample_bcp()), jt);
}

static intptr_t* frame_sender_sp(const StackWalkRequest& request, const JavaThread* jt) {
  assert(fp_in_stack(request, jt), "invariant");
  return frame::sender_sp(static_cast<intptr_t*>(request.sample_bcp()));
}

static void update_interpreter_frame_pc(StackWalkRequest& request, const JavaThread* jt) {
  assert(fp_in_stack(request, jt), "invariant");
  assert(is_interpreter(request), "invariant");
  request.set_sample_pc(frame::interpreter_return_address(static_cast<intptr_t*>(request.sample_bcp())));
}

static void update_frame_sender_sp(StackWalkRequest& request, const JavaThread* jt) {
  request.set_sample_sp(frame_sender_sp(request, jt));
}

static intptr_t* frame_link(const StackWalkRequest& request) {
  return frame::link(static_cast<intptr_t*>(request.sample_bcp()));
}

// Less extensive sanity checks for an interpreter frame.
static bool is_valid_interpreter_frame(const StackWalkRequest& request, const JavaThread* jt) {
  assert(sp_in_stack(request, jt), "invariant");
  assert(fp_in_stack(request, jt), "invariant");
  return frame::is_interpreter_frame_setup_at(static_cast<intptr_t*>(request.sample_bcp()), request.sample_sp());
}

static bool is_continuation_frame(address pc) {
  return ContinuationEntry::return_pc() == pc;
}

static bool is_continuation_frame(const StackWalkRequest& request) {
  return is_continuation_frame(static_cast<address>(request.sample_pc()));
}

static intptr_t* sender_for_interpreter_frame(StackWalkRequest& request, const JavaThread* jt) {
  update_interpreter_frame_pc(request, jt); // pick up return address
  if (is_continuation_frame(request) || is_entry_frame(request)) {
    request.set_sample_pc(nullptr);
    return nullptr;
  }
  update_frame_sender_sp(request, jt);
  intptr_t* fp = nullptr;
  if (is_interpreter(request)) {
    fp = frame_link(request);
  }
  request.set_sample_bcp(nullptr);
  return fp;
}

static bool build(StackWalkRequest& request, intptr_t* fp, JavaThread* jt);

static bool build_for_interpreter(StackWalkRequest& request, JavaThread* jt) {
  assert(is_interpreter(request), "invariant");
  assert(jt != nullptr, "invariant");
  if (!fp_in_stack(request, jt)) {
    return false;
  }
  if (is_valid_interpreter_frame(request, jt)) {
    // Set fp as sp for interpreter frames.
    request.set_sample_sp(request.sample_bcp());
    // Get real bcp.
    void* const bcp = interpreter_frame_bcp(request);
    // Setting bcp = 1 marks the sample request to represent a native method.
    request.set_sample_bcp(bcp != nullptr ? bcp : reinterpret_cast<address>(1));
    return true;
  }
  intptr_t* fp = sender_for_interpreter_frame(request, jt);
  if (request.sample_pc() == nullptr || request.sample_sp() == nullptr) {
    return false;
  }
  return build(request, fp, jt);
}

// Attempt to build a stack-walk request.
static bool build(StackWalkRequest& request, intptr_t* fp, JavaThread* jt) {
  assert(request.sample_sp() != nullptr, "invariant");
  assert(request.sample_pc() != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  assert(jt->thread_state() == _thread_in_Java || jt->thread_state() == _thread_in_native, "invariant");

  // 1. Interpreter frame?
  if (is_interpreter(request)) {
    request.set_sample_bcp(fp);
    return build_for_interpreter(request, jt);
  }
  const CodeBlob* const cb = CodeCache::find_blob(request.sample_pc());
  if (cb != nullptr) {
    // 2. Is nmethod?
    return cb->is_nmethod();
    // 3. What kind of CodeBlob or Stub?
    // Longer plan is to make stubs and blobs parsable,
    // and we will have a list of cases here for each blob type
    // describing how to locate the sender. We can't get to the
    // sender of a blob or stub until they have a standardized
    // layout and proper metadata descriptions.
  }
  return false;
}

static bool build_from_ljf(StackWalkRequest& request,
                           JavaThread* jt) {
  assert(jt != nullptr, "invariant");
  assert(sp_in_stack(request, jt), "invariant");
  // Last Java frame is available, but might not be walkable, fix it.
  address last_pc = jt->last_Java_pc();
  if (last_pc == nullptr) {
    last_pc = frame::return_address(static_cast<intptr_t*>(request.sample_sp()));
    if (last_pc == nullptr) {
      return false;
    }
  }
  assert(last_pc != nullptr, "invariant");
  if (is_interpreter(last_pc)) {
    const StackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
    if (tl.in_critical_section()) {
      return false;
    }
    request.set_sample_pc(last_pc);
    request.set_sample_bcp(jt->frame_anchor()->last_Java_fp());
    return build_for_interpreter(request, jt);
  }
  request.set_sample_pc(last_pc);
  return build(request, nullptr, jt);
}

static bool build_from_context(StackWalkRequest& request,
                               const void* ucontext,
                               JavaThread* jt) {
  assert(ucontext != nullptr, "invariant");
  assert(jt != nullptr, "invariant");
  intptr_t* fp;
  intptr_t* sp;
  request.set_sample_pc(os::fetch_frame_from_context(ucontext, &sp, &fp));
  request.set_sample_sp(sp);
  assert(sp_in_stack(request, jt), "invariant");
  if (is_interpreter(request)) {
    const StackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
    if (tl.in_critical_section() || !in_stack(fp, jt)) {
      return false;
    }
    if (frame::is_interpreter_frame_setup_at(fp, request.sample_sp())) {
      // Set fp as sp for interpreter frames.
      request.set_sample_sp(fp);
      void* bcp = os::fetch_bcp_from_context(ucontext);
      // Setting bcp = 1 marks the sample request to represent a native method.
      request.set_sample_bcp(bcp != nullptr ? bcp : reinterpret_cast<void*>(1));
      return true;
    }
    request.set_sample_bcp(fp);
    fp = sender_for_interpreter_frame(request, jt);
    if (request.sample_pc() == nullptr || request.sample_sp() == nullptr) {
      return false;
    }
  }
  return build(request, fp, jt);
}

// A biased stack-walk request is denoted by an empty bcp and an empty pc.
static void set_biased(StackWalkRequest& request, JavaThread* jt) {
  request.set_sample_bcp(nullptr);
  request.set_sample_pc(nullptr);
}

static void set_unbiased(StackWalkRequest& request, JavaThread* jt) {
  assert(request.sample_sp() != nullptr, "invariant");
  assert(sp_in_stack(request, jt), "invariant");
  assert(request.sample_bcp() != nullptr || !is_interpreter(request), "invariant");
}

void StackWalker::build_stack_walk_request(StackWalkRequest& request, const void* ucontext, JavaThread* java_thread) {
  assert(java_thread != nullptr, "invariant");

  // Prioritize the ljf, if one exists.
  request.set_sample_sp(java_thread->last_Java_sp());
  if (request.sample_sp() != nullptr && build_from_ljf(request, java_thread)) {
    set_unbiased(request, java_thread);
  } else if (ucontext != nullptr && build_from_context(request, ucontext, java_thread)) {
    set_unbiased(request, java_thread);
  } else {
    set_biased(request, java_thread);
  }
}

static bool check_state(const JavaThread* thread) {
  switch (thread->thread_state()) {
    case _thread_in_Java:
    case _thread_in_native:
      return true;
    default:
      return false;
  }
}

void StackWalker::request_stack_trace(StackWalkRequest& request, JavaThread* jt, const void* context, bool thread_is_suspended) {
  StackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
  StackWalkerRequestQueue& queue = tl.queue();
  if (thread_is_suspended && !check_state(jt)) {
    queue.increment_lost_requests();
    return;
  }

  StackWalkTryEnqueueLocker lock(tl);
  if (!lock.is_locked()) {
    queue.increment_lost_requests();
    return;
  }

  // When the thread is suspended, we try to build an unbiased request from the
  // thread context. Otherwise we only enqueue the biased request.
  if (thread_is_suspended) {
    // For foreign threads, assume biased stack-walking and don't even attempt
    // to build an unbiased request.
    build_stack_walk_request(request, context, jt);
  }

  if (queue.enqueue(request)) {
    if (queue.size() == 1) {
      tl.set_has_requests(true);
      SafepointMechanism::arm_local_poll_release(jt);
    }
  } else {
    queue.increment_lost_requests();
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
}

void StackWalker::trigger_async_processing_of_requests() {
  _native_stackwalker_thread->trigger_async_processing_of_stackwalk_requests();
}

static bool is_in_continuation(const frame& frame, JavaThread* jt) {
  return StackWalkerThreadLocal::is_vthread(jt) &&
         (Continuation::is_frame_in_continuation(jt, frame) || Continuation::is_continuation_enterSpecial(frame));
}

// An interpreter frame is handled differently from a compiler frame.
//
// The StackWalkRequest description partially describes a _potential_ interpreter Java frame.
// It's partial because the requester thread only sets the fp and bcp fields.
//
// We want to ensure that what we discovered inside interpreter code _really_ is what we assume, a valid interpreter frame.
//
// Therefore, instead of letting the requester thread read what it believes to be a Method*, we delay until we are at a safepoint to ensure the Method* is valid.
//
// If the StackWalkRequest represents a valid interpreter frame, the Method* is retrieved and the sender frame is returned per the sender_frame.
//
// If it is not a valid interpreter frame, then the StackWalkRequest is invalidated, and the current frame is returned per the sender frame.
//
static bool compute_sender_frame(StackWalkRequest& request, frame& sender_frame, bool& in_continuation, JavaThread* jt) {
  assert(request.sample_bcp() != nullptr /*is_interpreter(request)*/, "invariant");
  assert(jt != nullptr, "invariant");
  assert(jt->has_last_Java_frame(), "invariant");

  // For a request representing an interpreter frame, request.sample_sp() is actually the frame pointer, fp.
  const void* const sampled_fp = request.sample_sp();

  StackFrameStream stream(jt, false, false);

  // Search for the sampled interpreter frame and get its Method*.

  while (!stream.is_done()) {
    const frame* const frame = stream.current();
    assert(frame != nullptr, "invariant");
    const intptr_t* const real_fp = frame->real_fp();
    assert(real_fp != nullptr, "invariant");
    if (real_fp == sampled_fp && frame->is_interpreted_frame()) {
      Method* const method = frame->interpreter_frame_method();
      assert(method != nullptr, "invariant");
      request.set_sample_pc(method);
      // Got the Method*. Validate bcp.
      if (!method->is_native() &&  !method->contains(static_cast<address>(request.sample_bcp()))) {
        request.set_sample_bcp(frame->interpreter_frame_bcp());
      }
      in_continuation = is_in_continuation(*frame, jt);
      break;
    }
    if (real_fp >= sampled_fp) {
      // What we sampled is not an official interpreter frame.
      // Invalidate the sample request and use current.
      request.set_sample_bcp(nullptr);
      sender_frame = *stream.current();
      in_continuation = is_in_continuation(sender_frame, jt);
      return true;
    }
    stream.next();
  }

  assert(!stream.is_done(), "invariant");

  // Step to sender.
  stream.next();

  // If the top frame is in a continuation, check that the sender frame is too.
  if (in_continuation && !is_in_continuation(*stream.current(), jt)) {
    // Leave sender frame empty.
    return true;
  }

  sender_frame = *stream.current();

  assert(request.sample_pc() != nullptr, "invariant");
  assert(request.sample_bcp() != nullptr, "invariant");
  assert(Method::is_valid_method(static_cast<const Method*>(request.sample_pc())), "invariant");
  assert(static_cast<const Method*>(request.sample_pc())->is_native() ||
         static_cast<const Method*>(request.sample_pc())->contains(static_cast<address>(request.sample_bcp())), "invariant");
  return true;
}

static const PcDesc* get_pc_desc(nmethod* nm, void* pc) {
  assert(nm != nullptr, "invariant");
  assert(pc != nullptr, "invariant");
  return nm->pc_desc_near(static_cast<address>(pc));
}

static bool is_valid(const PcDesc* pc_desc) {
  return pc_desc != nullptr && pc_desc->scope_decode_offset() != DebugInformationRecorder::serialized_null;
}

static bool compute_top_frame(StackWalkRequest& request, frame& top_frame, bool& in_continuation, JavaThread* jt, bool& biased) {
  assert(jt != nullptr, "invariant");

  if (!jt->has_last_Java_frame()) {
    return false;
  }

  if (request.sample_bcp() != nullptr /*is_interpreter(request)*/) {
    return compute_sender_frame(request, top_frame, in_continuation, jt);
  }

  void* const sampled_pc = request.sample_pc();
  CodeBlob* sampled_cb;
  if (sampled_pc == nullptr || (sampled_cb = CodeCache::find_blob(sampled_pc)) == nullptr) {
    // A biased sample is requested or no code blob.
    top_frame = jt->last_frame();
    in_continuation = is_in_continuation(top_frame, jt);
    biased = true;
    return true;
  }

  // We will never describe a sample request that represents an unparsable stub or blob.
  assert(sampled_cb->frame_complete_offset() != CodeOffsets::frame_never_safe, "invariant");

  const void* const sampled_sp = request.sample_sp();
  assert(sampled_sp != nullptr, "invariant");

  nmethod* const sampled_nm = sampled_cb->as_nmethod_or_null();

  StackFrameStream stream(jt, false /* update registers */, false /* process frames */);

  if (stream.current()->is_safepoint_blob_frame()) {
    if (sampled_nm != nullptr) {
      // Move to the physical sender frame of the SafepointBlob stub frame using the frame size, not the logical iterator.
      const int safepoint_blob_stub_frame_size = stream.current()->cb()->frame_size();
      intptr_t* const sender_sp = stream.current()->unextended_sp() + safepoint_blob_stub_frame_size;
      if (sender_sp > sampled_sp) {
        const address saved_exception_pc = jt->saved_exception_pc();
        assert(saved_exception_pc != nullptr, "invariant");
        const nmethod* const exception_nm = CodeCache::find_blob(saved_exception_pc)->as_nmethod();
        assert(exception_nm != nullptr, "invariant");
        if (exception_nm == sampled_nm && sampled_nm->is_at_poll_return(saved_exception_pc)) {
          // We sit at the poll return site in the sampled compiled nmethod with only the return address on the stack.
          // The sampled_nm compiled frame is no longer extant, but we might be able to reconstruct a synthetic
          // compiled frame at this location. We do this by overlaying a reconstructed frame on top of
          // the huge SafepointBlob stub frame. Of course, the synthetic frame only contains random stack memory,
          // but it is safe because stack walking cares only about the form of the frame (i.e., an sp and a pc).
          // We also do not have to worry about stackbanging because we currently have a huge SafepointBlob stub frame
          // on the stack. For extra assurance, we know that we can create this frame size at this
          // very location because we just popped such a frame before we hit the return poll site.
          //
          // Let's attempt to correct for the safepoint bias.
          const PcDesc* const pc_desc = get_pc_desc(sampled_nm, sampled_pc);
          if (is_valid(pc_desc)) {
            intptr_t* const synthetic_sp = sender_sp - sampled_nm->frame_size();
            intptr_t* const synthetic_fp = sender_sp AARCH64_ONLY( - frame::sender_sp_offset);
            top_frame = frame(synthetic_sp, synthetic_sp, synthetic_fp, pc_desc->real_pc(sampled_nm), sampled_nm);
            in_continuation = is_in_continuation(top_frame, jt);
            return true;
          }
        }
      }
    }
    stream.next(); // skip the SafepointBlob stub frame
  }

  assert(!stream.current()->is_safepoint_blob_frame(), "invariant");

  biased = true;

  // Search the first frame that is above the sampled sp.
  for (; !stream.is_done(); stream.next()) {
    frame* const current = stream.current();

    if (current->real_fp() <= sampled_sp) {
      // Continue searching for a matching frame.
      continue;
    }

    if (sampled_nm == nullptr) {
      // The sample didn't have an nmethod; we decide to trace from its sender.
      // Another instance of safepoint bias.
      top_frame = *current;
      break;
    }

    // Check for a matching compiled method.
    if (current->cb()->as_nmethod_or_null() == sampled_nm) {
      if (current->pc() != sampled_pc) {
        // Let's adjust for the safepoint bias if we can.
        const PcDesc* const pc_desc = get_pc_desc(sampled_nm, sampled_pc);
        if (is_valid(pc_desc)) {
          current->adjust_pc(pc_desc->real_pc(sampled_nm));
          biased = false;
        }
      }
    }
    // Either a hit or a mismatched sample in which case we trace from the sender.
    // Yet another instance of safepoint bias,to be addressed with
    // more exact and stricter versions when parsable blobs become available.
    top_frame = *current;
    break;
  }

  in_continuation = is_in_continuation(top_frame, jt);
  return true;
}

class StackWalkState {
public:
  StackWalkRequest& _request;
  u4 _count;
  bool _truncated;
  StackWalkState(StackWalkRequest& request) : _request(request), _count(0), _truncated(false) {}

  u4 max_frames() const { return _request.max_frames(); }

  void report_frame(const Method* method, int bci, StackWalkerFrameType type) const {
    int line_no = method->line_number_from_bci(bci);
    _request.callback()->stack_frame(method, bci, line_no, type);
  }
};

static void report_interpreter_top_frame(const StackWalkState& state, const StackWalkRequest& request) {
  const Method* method = static_cast<Method*>(request.sample_pc());
  assert(method != nullptr, "invariant");
  const int bci = method->is_native() ? 0 : method->bci_from(static_cast<address>(request.sample_bcp()));
  StackWalkerFrameType type = method->is_native() ? StackWalkerFrameType::FRAME_NATIVE : StackWalkerFrameType::FRAME_INTERPRETER;
  state.report_frame(method, bci, type);
}

static bool report_inner(StackWalkState& state, JavaThread* jt, const frame& frame, bool in_continuation, int skip) {
  assert(jt != nullptr, "invariant");
  assert(!in_continuation || is_in_continuation(frame, jt), "invariant");
  Thread* const current_thread = Thread::current();
  HandleMark hm(current_thread); // RegisterMap uses Handles to support continuations.
  StackWalkerVframeStream vfs(jt, frame, in_continuation, false);
  state._truncated = false;
  for (int i = 0; i < skip; ++i) {
    if (vfs.at_end()) {
      break;
    }
    vfs.next_vframe();
  }
  while (!vfs.at_end()) {
    if (state._count >= state.max_frames()) {
      state._truncated = true;
      break;
    }
    const Method* method = vfs.method();
    StackWalkerFrameType type = vfs.is_interpreted_frame() ? StackWalkerFrameType::FRAME_INTERPRETER : StackWalkerFrameType::FRAME_JIT;
    int bci = 0;
    if (method->is_native()) {
      type = StackWalkerFrameType::FRAME_NATIVE;
    } else {
      bci = vfs.bci();
    }

    const intptr_t* const frame_id = vfs.frame_id();
    vfs.next_vframe();
    if (type == StackWalkerFrameType::FRAME_JIT && !vfs.at_end() && frame_id == vfs.frame_id()) {
      // This frame and the caller frame are both the same physical
      // frame, so this frame is inlined into the caller.
      type = StackWalkerFrameType::FRAME_INLINE;
    }
    state.report_frame(method, bci, type);
    state._count++;
  }
  return state._count > 0;
}

static bool report(StackWalkState& state, JavaThread* jt, const frame& frame, bool in_continuation, int skip) {
  // Must use ResetNoHandleMark here to bypass if any NoHandleMark exist on stack.
  // This is because RegisterMap uses Handles to support continuations.
  ResetNoHandleMark rnhm;
  return report_inner(state, jt, frame, in_continuation, skip);
}

static bool report(StackWalkState& state, JavaThread* jt, const frame& frame, bool in_continuation, const StackWalkRequest& request) {
  if (request.sample_bcp() != nullptr /* is_interpreter(request) */) {
    report_interpreter_top_frame(state, request);
    if (frame.pc() == nullptr) {
      // No sender frame. Done.
      return true;
    }
  }
  return report(state, jt, frame, in_continuation, 0);
}

static void report_thread(StackWalkRequest& request, const StackWalkerThreadLocal& tl, JavaThread* jt, const Thread* current) {
  assert(jt != nullptr, "invariant");
  assert(current != nullptr, "invariant");
  frame top_frame;
  bool biased = false;
  bool in_continuation = false;
  bool could_compute_top_frame = compute_top_frame(request, top_frame, in_continuation, jt, biased);

  if (!could_compute_top_frame) {
    request.callback()->failure();
    return;
  }

  // The callback might be resource-allocating stuff, e.g. in JfrStackTrace.
  ResourceMark rm;
  request.callback()->begin_stacktrace(jt, in_continuation, biased);
  StackWalkState state(request);
  if (!report(state, jt, top_frame, in_continuation, request)) {
    request.callback()->failure();
    return;
  }
  request.callback()->end_stacktrace(state._truncated);
}

/**
 * Process queued-up stack-walk requests of the specified thread. The thread is
 * typically processed by itself (e.g. jt == JavaThread::current()). The exception
 * is when a stack-walk is requested for a thread that is in-native: in that case
 * the stack-walk-request may be processed by the NativeStackWalkerThread.
 *
 * @param jt the thread to process stack-walk requests for
 */
// Process queued requests with the dequeue lock already held by the caller.
void StackWalker::process_requests_locked(JavaThread* jt) {
  const Thread* current = Thread::current();
  assert(current != nullptr, "current should not be null");
  assert(jt != nullptr, "Java thread should not be null");

  StackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
  tl.set_do_async_processing_of_requests(false);
  StackWalkerRequestQueue& queue = tl.queue();
  u4 lost = queue.get_and_reset_lost_requests();
  u4 count = queue.size();
  for (u4 i = 0; i < count; i++) {
    StackWalkRequest& request = queue.at(i);
    report_thread(request, tl, jt, current);
    // Report lost requests through the last callback before destroying it.
    if (lost > 0 && i == count - 1) {
      request.callback()->report_lost_requests(jt, lost);
    }
    request.destroy_callback();
  }
  queue.clear();
  assert(queue.is_empty(), "invariant");
  tl.set_has_requests(false);
  if (lost > 0) {
    queue.resize_if_needed();
  }
}

void StackWalker::process_requests(JavaThread* jt) {
  assert(jt != nullptr, "Java thread should not be null");
  StackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
  StackWalkDequeueLocker lock(tl);
  process_requests_locked(jt);
}

static bool is_virtual(const JavaThread* jt, oop thread) {
  assert(jt != nullptr, "invariant");
  return thread != jt->threadObj();
}

void StackWalkerThreadLocal::on_set_current_thread(JavaThread* jt, oop thread) {
  assert(jt != nullptr, "invariant");
  assert(thread != nullptr, "invariant");
  StackWalkerThreadLocal& tl = jt->stackwalker_thread_local();
  if (!is_virtual(jt, thread)) {
    AtomicAccess::release_store(&tl._vthread, false);
    return;
  }
  AtomicAccess::release_store(&tl._vthread, true);
}

void StackWalker::initialize() {
  // Thread-safe initialization: use atomic CAS to ensure only one thread initializes.
  // This allows both JFR and JVMTI to call initialize() without coordination.
  if (AtomicAccess::cmpxchg(&_stackwalker_initialized, 0, 1) != 0) {
    // Already initialized by another thread
    return;
  }
  _native_stackwalker_thread = new NativeStackWalkerThread();
  _native_stackwalker_thread->start_thread();
  _native_stackwalker_thread->enroll();

  // Initialize queues for all existing threads. No safepoint needed here because
  // no profiling signals are active yet (timers/signal handlers are installed later).
  // JavaThreadIteratorWithHandle uses Thread-SMR for safe iteration.
  JavaThreadIteratorWithHandle jtiwh;
  for (JavaThread* jt = jtiwh.next(); jt != nullptr; jt = jtiwh.next()) {
    on_javathread_create(jt);
  }
}

#ifdef ASSERT
bool StackWalker::set_out_of_stack_walking_enabled(bool enabled) {
  if (_native_stackwalker_thread != nullptr) {
    _native_stackwalker_thread->set_out_of_stack_walking_enabled(enabled);
    return true;
  }
  return false;
}
#endif

void StackWalker::on_javathread_create(JavaThread* thread) {
  if (AtomicAccess::load_acquire(&_stackwalker_initialized) == 0) {
    return;  // StackWalker not initialized yet
  }
  if (thread->is_hidden_from_external_view()) {
    return;
  }
  thread->stackwalker_thread_local().queue().init();
}

#endif // INCLUDE_STACKWALKER

/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/handshake.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/semaphore.inline.hpp"
#include "runtime/task.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/preserveException.hpp"


class HandshakeOperation: public StackObj {
  HandshakeClosure* _handshake_cl;
  int64_t _pending_threads;
  bool _executed;
  bool _is_direct;
public:
  HandshakeOperation(HandshakeClosure* cl, bool is_direct = false) :
    _handshake_cl(cl),
    _pending_threads(1),
    _executed(false),
    _is_direct(is_direct) {}

  void do_handshake(JavaThread* thread);
  bool is_completed() {
    int64_t val = Atomic::load(&_pending_threads);
    assert(val >= 0, "_pending_threads cannot be negative");
    return val == 0;
  }
  void add_target_count(int count) { Atomic::add(&_pending_threads, count); }
  bool executed() const { return _executed; }
  const char* name() { return _handshake_cl->name(); }

  bool is_direct() { return _is_direct; }

#ifdef ASSERT
  void check_state() {
    assert(_pending_threads == 0, "Must be zero");
  }
#endif
};

class VM_Handshake: public VM_Operation {
  const jlong _handshake_timeout;
 public:
  bool evaluate_at_safepoint() const { return false; }

 protected:
  HandshakeOperation* const _op;

  VM_Handshake(HandshakeOperation* op) :
      _handshake_timeout(TimeHelper::millis_to_counter(HandshakeTimeout)), _op(op) {}

  bool handshake_has_timed_out(jlong start_time);
  static void handle_timeout();
};

bool VM_Handshake::handshake_has_timed_out(jlong start_time) {
  // Check if handshake operation has timed out
  if (_handshake_timeout > 0) {
    return os::elapsed_counter() >= (start_time + _handshake_timeout);
  }
  return false;
}

void VM_Handshake::handle_timeout() {
  LogStreamHandle(Warning, handshake) log_stream;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *thr = jtiwh.next(); ) {
    if (thr->has_handshake()) {
      log_stream.print("Thread " PTR_FORMAT " has not cleared its handshake op", p2i(thr));
      thr->print_thread_state_on(&log_stream);
    }
  }
  log_stream.flush();
  fatal("Handshake operation timed out");
}

static void log_handshake_info(jlong start_time_ns, const char* name, int targets, int vmt_executed, const char* extra = NULL) {
  if (start_time_ns != 0) {
    jlong completion_time = os::javaTimeNanos() - start_time_ns;
    log_info(handshake)("Handshake \"%s\", Targeted threads: %d, Executed by targeted threads: %d, Total completion time: " JLONG_FORMAT " ns%s%s",
                        name, targets,
                        targets - vmt_executed,
                        completion_time,
                        extra != NULL ? ", " : "",
                        extra != NULL ? extra : "");
  }
}

class VM_HandshakeOneThread: public VM_Handshake {
  JavaThread* _target;
 public:
  VM_HandshakeOneThread(HandshakeOperation* op, JavaThread* target) :
    VM_Handshake(op), _target(target) {}

  void doit() {
    jlong start_time_ns = 0;
    if (log_is_enabled(Info, handshake)) {
      start_time_ns = os::javaTimeNanos();
    }

    ThreadsListHandle tlh;
    if (tlh.includes(_target)) {
      _target->set_handshake_operation(_op);
    } else {
      log_handshake_info(start_time_ns, _op->name(), 0, 0, "(thread dead)");
      return;
    }

    log_trace(handshake)("JavaThread " INTPTR_FORMAT " signaled, begin attempt to process by VMThtread", p2i(_target));
    jlong timeout_start_time = os::elapsed_counter();
    bool by_vm_thread = false;
    do {
      if (handshake_has_timed_out(timeout_start_time)) {
        handle_timeout();
      }
      by_vm_thread = _target->handshake_try_process(_op);
    } while (!_op->is_completed());
    DEBUG_ONLY(_op->check_state();)
    log_handshake_info(start_time_ns, _op->name(), 1, by_vm_thread ? 1 : 0);
  }

  VMOp_Type type() const { return VMOp_HandshakeOneThread; }

  bool executed() const { return _op->executed(); }
};

class VM_HandshakeAllThreads: public VM_Handshake {
 public:
  VM_HandshakeAllThreads(HandshakeOperation* op) : VM_Handshake(op) {}

  void doit() {
    jlong start_time_ns = 0;
    if (log_is_enabled(Info, handshake)) {
      start_time_ns = os::javaTimeNanos();
    }
    int handshake_executed_by_vm_thread = 0;

    JavaThreadIteratorWithHandle jtiwh;
    int number_of_threads_issued = 0;
    for (JavaThread *thr = jtiwh.next(); thr != NULL; thr = jtiwh.next()) {
      thr->set_handshake_operation(_op);
      number_of_threads_issued++;
    }

    if (number_of_threads_issued < 1) {
      log_handshake_info(start_time_ns, _op->name(), 0, 0);
      return;
    }
    // _op was created with a count == 1 so don't double count.
    _op->add_target_count(number_of_threads_issued - 1);

    log_trace(handshake)("Threads signaled, begin processing blocked threads by VMThread");
    const jlong start_time = os::elapsed_counter();
    do {
      // Check if handshake operation has timed out
      if (handshake_has_timed_out(start_time)) {
        handle_timeout();
      }

      // Have VM thread perform the handshake operation for blocked threads.
      // Observing a blocked state may of course be transient but the processing is guarded
      // by semaphores and we optimistically begin by working on the blocked threads
      jtiwh.rewind();
      for (JavaThread *thr = jtiwh.next(); thr != NULL; thr = jtiwh.next()) {
        // A new thread on the ThreadsList will not have an operation,
        // hence it is skipped in handshake_try_process.
        if (thr->handshake_try_process(_op)) {
          handshake_executed_by_vm_thread++;
        }
      }
    } while (!_op->is_completed());
    DEBUG_ONLY(_op->check_state();)

    log_handshake_info(start_time_ns, _op->name(), number_of_threads_issued, handshake_executed_by_vm_thread);
  }

  VMOp_Type type() const { return VMOp_HandshakeAllThreads; }
};

class VM_HandshakeFallbackOperation : public VM_Operation {
  HandshakeClosure* _handshake_cl;
  Thread* _target_thread;
  bool _all_threads;
  bool _executed;
public:
  VM_HandshakeFallbackOperation(HandshakeClosure* cl) :
      _handshake_cl(cl), _target_thread(NULL), _all_threads(true), _executed(false) {}
  VM_HandshakeFallbackOperation(HandshakeClosure* cl, Thread* target) :
      _handshake_cl(cl), _target_thread(target), _all_threads(false), _executed(false) {}

  void doit() {
    log_trace(handshake)("VMThread executing VM_HandshakeFallbackOperation, operation: %s", name());
    for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
      if (_all_threads || t == _target_thread) {
        if (t == _target_thread) {
          _executed = true;
        }
        _handshake_cl->do_thread(t);
      }
    }
  }

  VMOp_Type type() const { return VMOp_HandshakeFallback; }
  bool executed() const { return _executed; }
};

void HandshakeOperation::do_handshake(JavaThread* thread) {
  jlong start_time_ns = 0;
  if (log_is_enabled(Debug, handshake, task)) {
    start_time_ns = os::javaTimeNanos();
  }

  // Only actually execute the operation for non terminated threads.
  if (!thread->is_terminated()) {
    _handshake_cl->do_thread(thread);
    _executed = true;
  }

  if (start_time_ns != 0) {
    jlong completion_time = os::javaTimeNanos() - start_time_ns;
    log_debug(handshake, task)("Operation: %s for thread " PTR_FORMAT ", is_vm_thread: %s, completed in " JLONG_FORMAT " ns",
                               name(), p2i(thread), BOOL_TO_STR(Thread::current()->is_VM_thread()), completion_time);
  }

  // Inform VMThread/Handshaker that we have completed the operation
  Atomic::dec(&_pending_threads);

  // It is no longer safe to refer to 'this' as the VMThread/Handshaker may have destroyed this operation
}

void Handshake::execute(HandshakeClosure* thread_cl) {
  if (SafepointMechanism::uses_thread_local_poll()) {
    HandshakeOperation ho(thread_cl);
    VM_HandshakeAllThreads handshake(&ho);
    VMThread::execute(&handshake);
  } else {
    VM_HandshakeFallbackOperation op(thread_cl);
    VMThread::execute(&op);
  }
}

bool Handshake::execute(HandshakeClosure* thread_cl, JavaThread* target) {
  if (SafepointMechanism::uses_thread_local_poll()) {
    HandshakeOperation ho(thread_cl);
    VM_HandshakeOneThread handshake(&ho, target);
    VMThread::execute(&handshake);
    return handshake.executed();
  } else {
    VM_HandshakeFallbackOperation op(thread_cl, target);
    VMThread::execute(&op);
    return op.executed();
  }
}

bool Handshake::execute_direct(HandshakeClosure* thread_cl, JavaThread* target) {
  if (!SafepointMechanism::uses_thread_local_poll()) {
    VM_HandshakeFallbackOperation op(thread_cl, target);
    VMThread::execute(&op);
    return op.executed();
  }
  JavaThread* self = JavaThread::current();
  HandshakeOperation op(thread_cl, /*is_direct*/ true);

  jlong start_time_ns = 0;
  if (log_is_enabled(Info, handshake)) {
    start_time_ns = os::javaTimeNanos();
  }

  ThreadsListHandle tlh;
  if (tlh.includes(target)) {
    target->set_handshake_operation(&op);
  } else {
    log_handshake_info(start_time_ns, op.name(), 0, 0, "(thread dead)");
    return false;
  }

  bool by_handshaker = false;
  while (!op.is_completed()) {
    by_handshaker = target->handshake_try_process(&op);
    // Check for pending handshakes to avoid possible deadlocks where our
    // target is trying to handshake us.
    if (SafepointMechanism::should_block(self)) {
      ThreadBlockInVM tbivm(self);
    }
  }
  DEBUG_ONLY(op.check_state();)
  log_handshake_info(start_time_ns, op.name(), 1, by_handshaker ? 1 : 0);

  return op.executed();
}

HandshakeState::HandshakeState() :
  _operation(NULL),
  _operation_direct(NULL),
  _handshake_turn_sem(1),
  _processing_sem(1),
  _thread_in_process_handshake(false)
{
  DEBUG_ONLY(_active_handshaker = NULL;)
}

void HandshakeState::set_operation(HandshakeOperation* op) {
  if (!op->is_direct()) {
    assert(Thread::current()->is_VM_thread(), "should be the VMThread");
    _operation = op;
  } else {
    assert(Thread::current()->is_Java_thread(), "should be a JavaThread");
    // Serialize direct handshakes so that only one proceeds at a time for a given target
    _handshake_turn_sem.wait_with_safepoint_check(JavaThread::current());
    _operation_direct = op;
  }
  SafepointMechanism::arm_local_poll_release(_handshakee);
}

void HandshakeState::clear_handshake(bool is_direct) {
  if (!is_direct) {
    _operation = NULL;
  } else {
    _operation_direct = NULL;
    _handshake_turn_sem.signal();
  }
}

void HandshakeState::process_self_inner() {
  assert(Thread::current() == _handshakee, "should call from _handshakee");
  assert(!_handshakee->is_terminated(), "should not be a terminated thread");
  assert(_handshakee->thread_state() != _thread_blocked, "should not be in a blocked state");
  assert(_handshakee->thread_state() != _thread_in_native, "should not be in native");
  JavaThread* self = _handshakee;

  do {
    ThreadInVMForHandshake tivm(self);
    if (!_processing_sem.trywait()) {
      _processing_sem.wait_with_safepoint_check(self);
    }
    if (has_operation()) {
      HandleMark hm(self);
      CautiouslyPreserveExceptionMark pem(self);
      HandshakeOperation * op = _operation;
      if (op != NULL) {
        // Disarm before executing the operation
        clear_handshake(/*is_direct*/ false);
        op->do_handshake(self);
      }
      op = _operation_direct;
      if (op != NULL) {
        // Disarm before executing the operation
        clear_handshake(/*is_direct*/ true);
        op->do_handshake(self);
      }
    }
    _processing_sem.signal();
  } while (has_operation());
}

bool HandshakeState::can_process_handshake() {
  // handshake_safe may only be called with polls armed.
  // Handshaker controls this by first claiming the handshake via claim_handshake().
  return SafepointSynchronize::handshake_safe(_handshakee);
}

bool HandshakeState::possibly_can_process_handshake() {
  // Note that this method is allowed to produce false positives.
  if (_handshakee->is_ext_suspended()) {
    return true;
  }
  if (_handshakee->is_terminated()) {
    return true;
  }
  switch (_handshakee->thread_state()) {
  case _thread_in_native:
    // native threads are safe if they have no java stack or have walkable stack
    return !_handshakee->has_last_Java_frame() || _handshakee->frame_anchor()->walkable();

  case _thread_blocked:
    return true;

  default:
    return false;
  }
}

bool HandshakeState::claim_handshake(bool is_direct) {
  if (!_processing_sem.trywait()) {
    return false;
  }
  if (has_specific_operation(is_direct)){
    return true;
  }
  _processing_sem.signal();
  return false;
}

bool HandshakeState::try_process(HandshakeOperation* op) {
  bool is_direct = op->is_direct();

  if (!has_specific_operation(is_direct)){
    // JT has already cleared its handshake
    return false;
  }

  if (!possibly_can_process_handshake()) {
    // JT is observed in an unsafe state, it must notice the handshake itself
    return false;
  }

  // Claim the semaphore if there still an operation to be executed.
  if (!claim_handshake(is_direct)) {
    return false;
  }

  // Check if the handshake operation is the same as the one we meant to execute. The
  // handshake could have been already processed by the handshakee and a new handshake
  // by another JavaThread might be in progress.
  if ( (is_direct && op != _operation_direct)) {
    _processing_sem.signal();
    return false;
  }

  // If we own the semaphore at this point and while owning the semaphore
  // can observe a safe state the thread cannot possibly continue without
  // getting caught by the semaphore.
  bool executed = false;
  if (can_process_handshake()) {
    guarantee(!_processing_sem.trywait(), "we should already own the semaphore");
    log_trace(handshake)("Processing handshake by %s", Thread::current()->is_VM_thread() ? "VMThread" : "Handshaker");
    DEBUG_ONLY(_active_handshaker = Thread::current();)
    op->do_handshake(_handshakee);
    DEBUG_ONLY(_active_handshaker = NULL;)
    // Disarm after we have executed the operation.
    clear_handshake(is_direct);
    executed = true;
  }

  // Release the thread
  _processing_sem.signal();

  return executed;
}

/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/handshake.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/osThread.hpp"
#include "runtime/semaphore.inline.hpp"
#include "runtime/task.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/thread.hpp"
#include "runtime/vmThread.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/preserveException.hpp"

class HandshakeOperation: public StackObj {
public:
  virtual void do_handshake(JavaThread* thread) = 0;
  virtual void cancel_handshake(JavaThread* thread) = 0;
};

class HandshakeThreadsOperation: public HandshakeOperation {
  static Semaphore _done;
  ThreadClosure* _thread_cl;

public:
  HandshakeThreadsOperation(ThreadClosure* cl) : _thread_cl(cl) {}
  void do_handshake(JavaThread* thread);
  void cancel_handshake(JavaThread* thread) { _done.signal(); };

  bool thread_has_completed() { return _done.trywait(); }

#ifdef ASSERT
  void check_state() {
    assert(!_done.trywait(), "Must be zero");
  }
#endif
};

Semaphore HandshakeThreadsOperation::_done(0);

class VM_Handshake: public VM_Operation {
  const jlong _handshake_timeout;
 public:
  bool evaluate_at_safepoint() const { return false; }

  bool evaluate_concurrently() const { return false; }

 protected:
  HandshakeThreadsOperation* const _op;

  VM_Handshake(HandshakeThreadsOperation* op) :
      _op(op),
      _handshake_timeout(TimeHelper::millis_to_counter(HandshakeTimeout)) {}

  void set_handshake(JavaThread* target) {
    target->set_handshake_operation(_op);
  }

  // This method returns true for threads completed their operation
  // and true for threads canceled their operation.
  // A cancellation can happen if the thread is exiting.
  bool poll_for_completed_thread() { return _op->thread_has_completed(); }

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

class VM_HandshakeOneThread: public VM_Handshake {
  JavaThread* _target;
  bool _thread_alive;
 public:
  VM_HandshakeOneThread(HandshakeThreadsOperation* op, JavaThread* target) :
    VM_Handshake(op), _target(target), _thread_alive(false) {}

  void doit() {
    DEBUG_ONLY(_op->check_state();)
    TraceTime timer("Performing single-target operation (vmoperation doit)", TRACETIME_LOG(Info, handshake));

    {
      ThreadsListHandle tlh;
      if (tlh.includes(_target)) {
        set_handshake(_target);
        _thread_alive = true;
      }
    }

    if (!_thread_alive) {
      return;
    }

    if (!UseMembar) {
      os::serialize_thread_states();
    }

    log_trace(handshake)("Thread signaled, begin processing by VMThtread");
    jlong start_time = os::elapsed_counter();
    do {
      if (handshake_has_timed_out(start_time)) {
        handle_timeout();
      }

      // We need to re-think this with SMR ThreadsList.
      // There is an assumption in the code that the Threads_lock should be
      // locked during certain phases.
      MutexLockerEx ml(Threads_lock, Mutex::_no_safepoint_check_flag);
      ThreadsListHandle tlh;
      if (tlh.includes(_target)) {
        // Warning _target's address might be re-used.
        // handshake_process_by_vmthread will check the semaphore for us again.
        // Since we can't have more then one handshake in flight a reuse of
        // _target's address should be okay since the new thread will not have
        // an operation.
        _target->handshake_process_by_vmthread();
      } else {
        // We can't warn here since the thread does cancel_handshake after
        // it has been removed from the ThreadsList. So we should just keep
        // looping here until while below returns false. If we have a bug,
        // then we hang here, which is good for debugging.
      }
    } while (!poll_for_completed_thread());
    DEBUG_ONLY(_op->check_state();)
  }

  VMOp_Type type() const { return VMOp_HandshakeOneThread; }

  bool thread_alive() const { return _thread_alive; }
};

class VM_HandshakeAllThreads: public VM_Handshake {
 public:
  VM_HandshakeAllThreads(HandshakeThreadsOperation* op) : VM_Handshake(op) {}

  void doit() {
    DEBUG_ONLY(_op->check_state();)
    TraceTime timer("Performing operation (vmoperation doit)", TRACETIME_LOG(Info, handshake));

    int number_of_threads_issued = 0;
    for (JavaThreadIteratorWithHandle jtiwh; JavaThread *thr = jtiwh.next(); ) {
      set_handshake(thr);
      number_of_threads_issued++;
    }

    if (number_of_threads_issued < 1) {
      log_debug(handshake)("No threads to handshake.");
      return;
    }

    if (!UseMembar) {
      os::serialize_thread_states();
    }

    log_debug(handshake)("Threads signaled, begin processing blocked threads by VMThtread");
    const jlong start_time = os::elapsed_counter();
    int number_of_threads_completed = 0;
    do {
      // Check if handshake operation has timed out
      if (handshake_has_timed_out(start_time)) {
        handle_timeout();
      }

      // Have VM thread perform the handshake operation for blocked threads.
      // Observing a blocked state may of course be transient but the processing is guarded
      // by semaphores and we optimistically begin by working on the blocked threads
      {
          // We need to re-think this with SMR ThreadsList.
          // There is an assumption in the code that the Threads_lock should
          // be locked during certain phases.
          MutexLockerEx ml(Threads_lock, Mutex::_no_safepoint_check_flag);
          for (JavaThreadIteratorWithHandle jtiwh; JavaThread *thr = jtiwh.next(); ) {
            // A new thread on the ThreadsList will not have an operation,
            // hence it is skipped in handshake_process_by_vmthread.
            thr->handshake_process_by_vmthread();
          }
      }

      while (poll_for_completed_thread()) {
        // Includes canceled operations by exiting threads.
        number_of_threads_completed++;
      }

    } while (number_of_threads_issued > number_of_threads_completed);
    assert(number_of_threads_issued == number_of_threads_completed, "Must be the same");
    DEBUG_ONLY(_op->check_state();)
  }

  VMOp_Type type() const { return VMOp_HandshakeAllThreads; }
};

class VM_HandshakeFallbackOperation : public VM_Operation {
  ThreadClosure* _thread_cl;
  Thread* _target_thread;
  bool _all_threads;
  bool _thread_alive;
public:
  VM_HandshakeFallbackOperation(ThreadClosure* cl) :
      _thread_cl(cl), _target_thread(NULL), _all_threads(true), _thread_alive(true) {}
  VM_HandshakeFallbackOperation(ThreadClosure* cl, Thread* target) :
      _thread_cl(cl), _target_thread(target), _all_threads(false), _thread_alive(false) {}

  void doit() {
    for (JavaThreadIteratorWithHandle jtiwh; JavaThread *t = jtiwh.next(); ) {
      if (_all_threads || t == _target_thread) {
        if (t == _target_thread) {
          _thread_alive = true;
        }
        _thread_cl->do_thread(t);
      }
    }
  }

  VMOp_Type type() const { return VMOp_HandshakeFallback; }
  bool thread_alive() const { return _thread_alive; }
};

void HandshakeThreadsOperation::do_handshake(JavaThread* thread) {
  ResourceMark rm;
  FormatBufferResource message("Operation for thread " PTR_FORMAT ", is_vm_thread: %s",
                               p2i(thread), BOOL_TO_STR(Thread::current()->is_VM_thread()));
  TraceTime timer(message, TRACETIME_LOG(Debug, handshake, task));
  _thread_cl->do_thread(thread);

  // Use the semaphore to inform the VM thread that we have completed the operation
  _done.signal();
}

void Handshake::execute(ThreadClosure* thread_cl) {
  if (ThreadLocalHandshakes) {
    HandshakeThreadsOperation cto(thread_cl);
    VM_HandshakeAllThreads handshake(&cto);
    VMThread::execute(&handshake);
  } else {
    VM_HandshakeFallbackOperation op(thread_cl);
    VMThread::execute(&op);
  }
}

bool Handshake::execute(ThreadClosure* thread_cl, JavaThread* target) {
  if (ThreadLocalHandshakes) {
    HandshakeThreadsOperation cto(thread_cl);
    VM_HandshakeOneThread handshake(&cto, target);
    VMThread::execute(&handshake);
    return handshake.thread_alive();
  } else {
    VM_HandshakeFallbackOperation op(thread_cl, target);
    VMThread::execute(&op);
    return op.thread_alive();
  }
}

HandshakeState::HandshakeState() : _operation(NULL), _semaphore(1), _thread_in_process_handshake(false) {}

void HandshakeState::set_operation(JavaThread* target, HandshakeOperation* op) {
  _operation = op;
  SafepointMechanism::arm_local_poll_release(target);
}

void HandshakeState::clear_handshake(JavaThread* target) {
  _operation = NULL;
  SafepointMechanism::disarm_local_poll_release(target);
}

void HandshakeState::process_self_inner(JavaThread* thread) {
  assert(Thread::current() == thread, "should call from thread");

  if (thread->is_terminated()) {
    // If thread is not on threads list but armed, cancel.
    thread->cancel_handshake();
    return;
  }

  CautiouslyPreserveExceptionMark pem(thread);
  ThreadInVMForHandshake tivm(thread);
  if (!_semaphore.trywait()) {
    _semaphore.wait_with_safepoint_check(thread);
  }
  HandshakeOperation* op = OrderAccess::load_acquire(&_operation);
  if (op != NULL) {
    // Disarm before execute the operation
    clear_handshake(thread);
    op->do_handshake(thread);
  }
  _semaphore.signal();
}

void HandshakeState::cancel_inner(JavaThread* thread) {
  assert(Thread::current() == thread, "should call from thread");
  assert(thread->thread_state() == _thread_in_vm, "must be in vm state");
  HandshakeOperation* op = _operation;
  clear_handshake(thread);
  if (op != NULL) {
    op->cancel_handshake(thread);
  }
}

bool HandshakeState::vmthread_can_process_handshake(JavaThread* target) {
  return SafepointSynchronize::safepoint_safe(target, target->thread_state());
}

bool HandshakeState::claim_handshake_for_vmthread() {
  if (!_semaphore.trywait()) {
    return false;
  }
  if (has_operation()) {
    return true;
  }
  _semaphore.signal();
  return false;
}

void HandshakeState::process_by_vmthread(JavaThread* target) {
  assert(Thread::current()->is_VM_thread(), "should call from vm thread");

  if (!has_operation()) {
    // JT has already cleared its handshake
    return;
  }

  if (!vmthread_can_process_handshake(target)) {
    // JT is observed in an unsafe state, it must notice the handshake itself
    return;
  }

  // Claim the semaphore if there still an operation to be executed.
  if (!claim_handshake_for_vmthread()) {
    return;
  }

  // If we own the semaphore at this point and while owning the semaphore
  // can observe a safe state the thread cannot possibly continue without
  // getting caught by the semaphore.
  if (vmthread_can_process_handshake(target)) {
    guarantee(!_semaphore.trywait(), "we should already own the semaphore");

    _operation->do_handshake(target);
    // Disarm after VM thread have executed the operation.
    clear_handshake(target);
    // Release the thread
  }

  _semaphore.signal();
}

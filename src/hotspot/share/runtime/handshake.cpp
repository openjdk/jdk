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
  int32_t _pending_threads;
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
    int32_t val = Atomic::load(&_pending_threads);
    assert(val >= 0, "_pending_threads=%d cannot be negative", val);
    return val == 0;
  }
  void add_target_count(int count) { Atomic::add(&_pending_threads, count, memory_order_relaxed); }
  bool executed() const { return _executed; }
  const char* name() { return _handshake_cl->name(); }

  bool is_direct() { return _is_direct; }
};

// Performing handshakes requires a custom yielding strategy because without it
// there is a clear performance regression vs plain spinning. We keep track of
// when we last saw progress by looking at why each targeted thread has not yet
// completed its handshake. After spinning for a while with no progress we will
// yield, but as long as there is progress, we keep spinning. Thus we avoid
// yielding when there is potential work to be done or the handshake is close
// to being finished.
class HandshakeSpinYield : public StackObj {
 private:
  jlong _start_time_ns;
  jlong _last_spin_start_ns;
  jlong _spin_time_ns;

  int _result_count[2][HandshakeState::_number_states];
  int _prev_result_pos;

  int prev_result_pos() { return _prev_result_pos & 0x1; }
  int current_result_pos() { return (_prev_result_pos + 1) & 0x1; }

  void wait_raw(jlong now) {
    // We start with fine-grained nanosleeping until a millisecond has
    // passed, at which point we resort to plain naked_short_sleep.
    if (now - _start_time_ns < NANOSECS_PER_MILLISEC) {
      os::naked_short_nanosleep(10 * (NANOUNITS / MICROUNITS));
    } else {
      os::naked_short_sleep(1);
    }
  }

  void wait_blocked(JavaThread* self, jlong now) {
    ThreadBlockInVM tbivm(self);
    wait_raw(now);
  }

  bool state_changed() {
    for (int i = 0; i < HandshakeState::_number_states; i++) {
      if (_result_count[0][i] != _result_count[1][i]) {
        return true;
      }
    }
    return false;
  }

  void reset_state() {
    _prev_result_pos++;
    for (int i = 0; i < HandshakeState::_number_states; i++) {
      _result_count[current_result_pos()][i] = 0;
    }
  }

 public:
  HandshakeSpinYield(jlong start_time) :
    _start_time_ns(start_time), _last_spin_start_ns(start_time),
    _spin_time_ns(0), _result_count(), _prev_result_pos(0) {

    const jlong max_spin_time_ns = 100 /* us */ * (NANOUNITS / MICROUNITS);
    int free_cpus = os::active_processor_count() - 1;
    _spin_time_ns = (5 /* us */ * (NANOUNITS / MICROUNITS)) * free_cpus; // zero on UP
    _spin_time_ns = _spin_time_ns > max_spin_time_ns ? max_spin_time_ns : _spin_time_ns;
  }

  void add_result(HandshakeState::ProcessResult pr) {
    _result_count[current_result_pos()][pr]++;
  }

  void process() {
    jlong now = os::javaTimeNanos();
    if (state_changed()) {
      reset_state();
      // We spin for x amount of time since last state change.
      _last_spin_start_ns = now;
      return;
    }
    jlong wait_target = _last_spin_start_ns + _spin_time_ns;
    if (wait_target < now) {
      // On UP this is always true.
      Thread* self = Thread::current();
      if (self->is_Java_thread()) {
        wait_blocked((JavaThread*)self, now);
      } else {
        wait_raw(now);
      }
      _last_spin_start_ns = os::javaTimeNanos();
    }
    reset_state();
  }
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
    return os::javaTimeNanos() >= (start_time + _handshake_timeout);
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
    jlong start_time_ns = os::javaTimeNanos();

    ThreadsListHandle tlh;
    if (tlh.includes(_target)) {
      _target->set_handshake_operation(_op);
    } else {
      log_handshake_info(start_time_ns, _op->name(), 0, 0, "(thread dead)");
      return;
    }

    log_trace(handshake)("JavaThread " INTPTR_FORMAT " signaled, begin attempt to process by VMThtread", p2i(_target));
    HandshakeState::ProcessResult pr = HandshakeState::_no_operation;
    HandshakeSpinYield hsy(start_time_ns);
    do {
      if (handshake_has_timed_out(start_time_ns)) {
        handle_timeout();
      }
      pr = _target->handshake_try_process(_op);
      hsy.add_result(pr);
      hsy.process();
    } while (!_op->is_completed());

    // This pairs up with the release store in do_handshake(). It prevents future
    // loads from floating above the load of _pending_threads in is_completed()
    // and thus prevents reading stale data modified in the handshake closure
    // by the Handshakee.
    OrderAccess::acquire();

    log_handshake_info(start_time_ns, _op->name(), 1, (pr == HandshakeState::_success) ? 1 : 0);
  }

  VMOp_Type type() const { return VMOp_HandshakeOneThread; }

  bool executed() const { return _op->executed(); }
};

class VM_HandshakeAllThreads: public VM_Handshake {
 public:
  VM_HandshakeAllThreads(HandshakeOperation* op) : VM_Handshake(op) {}

  void doit() {
    jlong start_time_ns = os::javaTimeNanos();
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
    HandshakeSpinYield hsy(start_time_ns);
    do {
      // Check if handshake operation has timed out
      if (handshake_has_timed_out(start_time_ns)) {
        handle_timeout();
      }

      // Have VM thread perform the handshake operation for blocked threads.
      // Observing a blocked state may of course be transient but the processing is guarded
      // by semaphores and we optimistically begin by working on the blocked threads
      jtiwh.rewind();
      for (JavaThread *thr = jtiwh.next(); thr != NULL; thr = jtiwh.next()) {
        // A new thread on the ThreadsList will not have an operation,
        // hence it is skipped in handshake_try_process.
        HandshakeState::ProcessResult pr = thr->handshake_try_process(_op);
        if (pr == HandshakeState::_success) {
          handshake_executed_by_vm_thread++;
        }
        hsy.add_result(pr);
      }
      hsy.process();
    } while (!_op->is_completed());

    // This pairs up with the release store in do_handshake(). It prevents future
    // loads from floating above the load of _pending_threads in is_completed()
    // and thus prevents reading stale data modified in the handshake closure
    // by the Handshakee.
    OrderAccess::acquire();

    log_handshake_info(start_time_ns, _op->name(), number_of_threads_issued, handshake_executed_by_vm_thread);
  }

  VMOp_Type type() const { return VMOp_HandshakeAllThreads; }
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

  // Inform VMThread/Handshaker that we have completed the operation.
  // When this is executed by the Handshakee we need a release store
  // here to make sure memory operations executed in the handshake
  // closure are visible to the VMThread/Handshaker after it reads
  // that the operation has completed.
  Atomic::dec(&_pending_threads, memory_order_release);

  // It is no longer safe to refer to 'this' as the VMThread/Handshaker may have destroyed this operation
}

void Handshake::execute(HandshakeClosure* thread_cl) {
  HandshakeOperation cto(thread_cl);
  VM_HandshakeAllThreads handshake(&cto);
  VMThread::execute(&handshake);
}

bool Handshake::execute(HandshakeClosure* thread_cl, JavaThread* target) {
  HandshakeOperation cto(thread_cl);
  VM_HandshakeOneThread handshake(&cto, target);
  VMThread::execute(&handshake);
  return handshake.executed();
}

bool Handshake::execute_direct(HandshakeClosure* thread_cl, JavaThread* target) {
  JavaThread* self = JavaThread::current();
  HandshakeOperation op(thread_cl, /*is_direct*/ true);

  jlong start_time_ns = os::javaTimeNanos();

  ThreadsListHandle tlh;
  if (tlh.includes(target)) {
    target->set_handshake_operation(&op);
  } else {
    log_handshake_info(start_time_ns, op.name(), 0, 0, "(thread dead)");
    return false;
  }

  HandshakeState::ProcessResult pr =  HandshakeState::_no_operation;
  HandshakeSpinYield hsy(start_time_ns);
  while (!op.is_completed()) {
    HandshakeState::ProcessResult pr = target->handshake_try_process(&op);
    hsy.add_result(pr);
    // Check for pending handshakes to avoid possible deadlocks where our
    // target is trying to handshake us.
    if (SafepointMechanism::should_block(self)) {
      ThreadBlockInVM tbivm(self);
    }
    hsy.process();
  }

  // This pairs up with the release store in do_handshake(). It prevents future
  // loads from floating above the load of _pending_threads in is_completed()
  // and thus prevents reading stale data modified in the handshake closure
  // by the Handshakee.
  OrderAccess::acquire();

  log_handshake_info(start_time_ns, op.name(), 1,  (pr == HandshakeState::_success) ? 1 : 0);

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

HandshakeState::ProcessResult HandshakeState::try_process(HandshakeOperation* op) {
  bool is_direct = op->is_direct();

  if (!has_specific_operation(is_direct)){
    // JT has already cleared its handshake
    return _no_operation;
  }

  if (!possibly_can_process_handshake()) {
    // JT is observed in an unsafe state, it must notice the handshake itself
    return _not_safe;
  }

  // Claim the semaphore if there still an operation to be executed.
  if (!claim_handshake(is_direct)) {
    return _state_busy;
  }

  // Check if the handshake operation is the same as the one we meant to execute. The
  // handshake could have been already processed by the handshakee and a new handshake
  // by another JavaThread might be in progress.
  if (is_direct && op != _operation_direct) {
    _processing_sem.signal();
    return _no_operation;
  }

  // If we own the semaphore at this point and while owning the semaphore
  // can observe a safe state the thread cannot possibly continue without
  // getting caught by the semaphore.
  ProcessResult pr = _not_safe;
  if (can_process_handshake()) {
    guarantee(!_processing_sem.trywait(), "we should already own the semaphore");
    log_trace(handshake)("Processing handshake by %s", Thread::current()->is_VM_thread() ? "VMThread" : "Handshaker");
    DEBUG_ONLY(_active_handshaker = Thread::current();)
    op->do_handshake(_handshakee);
    DEBUG_ONLY(_active_handshaker = NULL;)
    // Disarm after we have executed the operation.
    clear_handshake(is_direct);
    pr = _success;
  }

  // Release the thread
  _processing_sem.signal();

  return pr;
}

/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "compiler/compileBroker.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/vmThreadCpuTimeScope.inline.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/support/jfrThreadId.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "logging/logConfiguration.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/oop.inline.hpp"
#include "oops/verifyOopClosure.hpp"
#include "runtime/atomic.hpp"
#include "runtime/cpuTimeCounters.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "runtime/perfData.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/vmOperations.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#include "utilities/vmError.hpp"


//------------------------------------------------------------------------------------------------------------------
// Timeout machinery

void VMOperationTimeoutTask::task() {
  assert(AbortVMOnVMOperationTimeout, "only if enabled");
  if (is_armed()) {
    jlong delay = nanos_to_millis(os::javaTimeNanos() - _arm_time);
    if (delay > AbortVMOnVMOperationTimeoutDelay) {
      fatal("%s VM operation took too long: " JLONG_FORMAT " ms elapsed since VM-op start (timeout: %zd ms)",
            _vm_op_name, delay, AbortVMOnVMOperationTimeoutDelay);
    }
  }
}

bool VMOperationTimeoutTask::is_armed() {
  return Atomic::load_acquire(&_armed) != 0;
}

void VMOperationTimeoutTask::arm(const char* vm_op_name) {
  _vm_op_name = vm_op_name;
  _arm_time = os::javaTimeNanos();
  Atomic::release_store_fence(&_armed, 1);
}

void VMOperationTimeoutTask::disarm() {
  Atomic::release_store_fence(&_armed, 0);

  // The two stores to `_armed` are counted in VM-op, but they should be
  // insignificant compared to the actual VM-op duration.
  jlong vm_op_duration = nanos_to_millis(os::javaTimeNanos() - _arm_time);

  // Repeat the timeout-check logic on the VM thread, because
  // VMOperationTimeoutTask might miss the arm-disarm window depending on
  // the scheduling.
  if (vm_op_duration > AbortVMOnVMOperationTimeoutDelay) {
    fatal("%s VM operation took too long: completed in " JLONG_FORMAT " ms (timeout: %zd ms)",
          _vm_op_name, vm_op_duration, AbortVMOnVMOperationTimeoutDelay);
  }
  _vm_op_name = nullptr;
}

//------------------------------------------------------------------------------------------------------------------
// Implementation of VMThread stuff

static VM_SafepointALot safepointALot_op;
static VM_ForceSafepoint no_op;

bool              VMThread::_should_terminate   = false;
bool              VMThread::_terminated         = false;
Monitor*          VMThread::_terminate_lock     = nullptr;
VMThread*         VMThread::_vm_thread          = nullptr;
VM_Operation*     VMThread::_cur_vm_operation   = nullptr;
VM_Operation*     VMThread::_next_vm_operation  = &no_op; // Prevent any thread from setting an operation until VM thread is ready.
PerfCounter*      VMThread::_perf_accumulated_vm_operation_time = nullptr;
VMOperationTimeoutTask* VMThread::_timeout_task = nullptr;


void VMThread::create() {
  assert(vm_thread() == nullptr, "we can only allocate one VMThread");
  _vm_thread = new VMThread();

  if (AbortVMOnVMOperationTimeout) {
    // Make sure we call the timeout task frequently enough, but not too frequent.
    // Try to make the interval 10% of the timeout delay, so that we miss the timeout
    // by those 10% at max. Periodic task also expects it to fit min/max intervals.
    size_t interval = (size_t)AbortVMOnVMOperationTimeoutDelay / 10;
    interval = interval / PeriodicTask::interval_gran * PeriodicTask::interval_gran;
    interval = MAX2<size_t>(interval, PeriodicTask::min_interval);
    interval = MIN2<size_t>(interval, PeriodicTask::max_interval);

    _timeout_task = new VMOperationTimeoutTask(interval);
    _timeout_task->enroll();
  } else {
    assert(_timeout_task == nullptr, "sanity");
  }

  _terminate_lock = new Monitor(Mutex::nosafepoint, "VMThreadTerminate_lock");

  if (UsePerfData) {
    // jvmstat performance counters
    JavaThread* THREAD = JavaThread::current(); // For exception macros.
    _perf_accumulated_vm_operation_time =
                 PerfDataManager::create_counter(SUN_THREADS, "vmOperationTime",
                                                 PerfData::U_Ticks, CHECK);
    CPUTimeCounters::create_counter(CPUTimeGroups::CPUTimeType::vm);
  }
}

VMThread::VMThread() : NamedThread(), _is_running(false) {
  set_name("VM Thread");
}

void VMThread::destroy() {
  _vm_thread = nullptr;      // VM thread is gone
}

static VM_Halt halt_op;

void VMThread::run() {
  assert(this == vm_thread(), "check");

  // Notify_lock wait checks on is_running() to rewait in
  // case of spurious wakeup, it should wait on the last
  // value set prior to the notify
  Atomic::store(&_is_running, true);

  {
    MutexLocker ml(Notify_lock);
    Notify_lock->notify();
  }
  // Notify_lock is destroyed by Threads::create_vm()

  int prio = (VMThreadPriority == -1)
    ? os::java_to_os_priority[NearMaxPriority]
    : VMThreadPriority;
  // Note that I cannot call os::set_priority because it expects Java
  // priorities and I am *explicitly* using OS priorities so that it's
  // possible to set the VM thread priority higher than any Java thread.
  os::set_native_priority( this, prio );

  // Wait for VM_Operations until termination
  this->loop();

  // Note the intention to exit before safepointing.
  // 6295565  This has the effect of waiting for any large tty
  // outputs to finish.
  if (xtty != nullptr) {
    ttyLocker ttyl;
    xtty->begin_elem("destroy_vm");
    xtty->stamp();
    xtty->end_elem();
    assert(should_terminate(), "termination flag must be set");
  }

  // 4526887 let VM thread exit at Safepoint
  _cur_vm_operation = &halt_op;
  SafepointSynchronize::begin();

  if (VerifyBeforeExit) {
    HandleMark hm(VMThread::vm_thread());
    // Among other things, this ensures that Eden top is correct.
    Universe::heap()->prepare_for_verify();
    // Silent verification so as not to pollute normal output,
    // unless we really asked for it.
    Universe::verify();
  }

  CompileBroker::set_should_block();

  // wait for threads (compiler threads or daemon threads) in the
  // _thread_in_native state to block.
  VM_Exit::wait_for_threads_in_native_to_block();

  // The ObjectMonitor subsystem uses perf counters so do this before
  // we signal that the VM thread is gone. We don't want to run afoul
  // of perfMemory_exit() in exit_globals().
  ObjectSynchronizer::do_final_audit_and_print_stats();

  // signal other threads that VM process is gone
  {
    // Note: we must have the _no_safepoint_check_flag. Mutex::lock() allows
    // VM thread to enter any lock at Safepoint as long as its _owner is null.
    // If that happens after _terminate_lock->wait() has unset _owner
    // but before it actually drops the lock and waits, the notification below
    // may get lost and we will have a hang. To avoid this, we need to use
    // Mutex::lock_without_safepoint_check().
    MonitorLocker ml(_terminate_lock, Mutex::_no_safepoint_check_flag);
    _terminated = true;
    ml.notify();
  }

  // We are now racing with the VM termination being carried out in
  // another thread, so we don't "delete this". Numerous threads don't
  // get deleted when the VM terminates

}


// Notify the VMThread that the last non-daemon JavaThread has terminated,
// and wait until operation is performed.
void VMThread::wait_for_vm_thread_exit() {
  assert(JavaThread::current()->is_terminated(), "Should be terminated");
  {
    MonitorLocker mu(VMOperation_lock);
    _should_terminate = true;
    mu.notify_all();
  }

  // Note: VM thread leaves at Safepoint. We are not stopped by Safepoint
  // because this thread has been removed from the threads list. But anything
  // that could get blocked by Safepoint should not be used after this point,
  // otherwise we will hang, since there is no one can end the safepoint.

  // Wait until VM thread is terminated
  // Note: it should be OK to use Terminator_lock here. But this is called
  // at a very delicate time (VM shutdown) and we are operating in non- VM
  // thread at Safepoint. It's safer to not share lock with other threads.
  {
    MonitorLocker ml(_terminate_lock, Mutex::_no_safepoint_check_flag);
    while (!VMThread::is_terminated()) {
      ml.wait();
    }
  }
}

static void post_vm_operation_event(EventExecuteVMOperation* event, VM_Operation* op) {
  assert(event != nullptr, "invariant");
  assert(op != nullptr, "invariant");
  const bool evaluate_at_safepoint = op->evaluate_at_safepoint();
  event->set_operation(op->type());
  event->set_safepoint(evaluate_at_safepoint);
  event->set_blocking(true);
  event->set_caller(JFR_THREAD_ID(op->calling_thread()));
  event->set_safepointId(evaluate_at_safepoint ? SafepointSynchronize::safepoint_id() : 0);
  event->commit();
}

void VMThread::evaluate_operation(VM_Operation* op) {
  ResourceMark rm;

  {
    PerfTraceTime vm_op_timer(perf_accumulated_vm_operation_time());
    HOTSPOT_VMOPS_BEGIN(
                     (char*) op->name(), strlen(op->name()),
                     op->evaluate_at_safepoint() ? 0 : 1);

    EventExecuteVMOperation event;
    VMThreadCPUTimeScope CPUTimeScope(this, op->is_gc_operation());
    op->evaluate();
    if (event.should_commit()) {
      post_vm_operation_event(&event, op);
    }

    HOTSPOT_VMOPS_END(
                     (char*) op->name(), strlen(op->name()),
                     op->evaluate_at_safepoint() ? 0 : 1);
  }
}

class ALotOfHandshakeClosure : public HandshakeClosure {
 public:
  ALotOfHandshakeClosure() : HandshakeClosure("ALotOfHandshakeClosure") {}
  void do_thread(Thread* thread) {
#ifdef ASSERT
    JavaThread::cast(thread)->verify_states_for_handshake();
#endif
  }
};

bool VMThread::handshake_or_safepoint_alot() {
  assert(_cur_vm_operation == nullptr, "should not have an op yet");
  assert(_next_vm_operation == nullptr, "should not have an op yet");
  if (!HandshakeALot && !SafepointALot) {
    return false;
  }
  static jlong last_alot_ms = 0;
  jlong now_ms = nanos_to_millis(os::javaTimeNanos());
  // If HandshakeALot or SafepointALot are set, but GuaranteedSafepointInterval is explicitly
  // set to 0 on the command line, we emit the operation if it's been more than a second
  // since the last one.
  jlong interval = GuaranteedSafepointInterval != 0 ? GuaranteedSafepointInterval : 1000;
  jlong deadline_ms = interval + last_alot_ms;
  if (now_ms > deadline_ms) {
    last_alot_ms = now_ms;
    return true;
  }
  return false;
}

bool VMThread::set_next_operation(VM_Operation *op) {
  if (_next_vm_operation != nullptr) {
    return false;
  }
  log_debug(vmthread)("Adding VM operation: %s", op->name());

  _next_vm_operation = op;

  HOTSPOT_VMOPS_REQUEST(
                   (char *) op->name(), strlen(op->name()),
                   op->evaluate_at_safepoint() ? 0 : 1);
  return true;
}

void VMThread::wait_until_executed(VM_Operation* op) {
  MonitorLocker ml(VMOperation_lock,
                   Thread::current()->is_Java_thread() ?
                     Mutex::_safepoint_check_flag :
                     Mutex::_no_safepoint_check_flag);
  {
    TraceTime timer("Installing VM operation", TRACETIME_LOG(Trace, vmthread));
    while (true) {
      if (VMThread::vm_thread()->set_next_operation(op)) {
        ml.notify_all();
        break;
      }
      // Wait to install this operation as the next operation in the VM Thread
      log_trace(vmthread)("A VM operation already set, waiting");
      ml.wait();
    }
  }
  {
    // Wait until the operation has been processed
    TraceTime timer("Waiting for VM operation to be completed", TRACETIME_LOG(Trace, vmthread));
    // _next_vm_operation is cleared holding VMOperation_lock after it has been
    // executed. We wait until _next_vm_operation is not our op.
    while (_next_vm_operation == op) {
      // VM Thread can process it once we unlock the mutex on wait.
      ml.wait();
    }
  }
}

static void self_destruct_if_needed() {
  // Support for self destruction
  if ((SelfDestructTimer != 0.0) && !VMError::is_error_reported() &&
      (os::elapsedTime() > SelfDestructTimer * 60.0)) {
    tty->print_cr("VM self-destructed");
    os::exit(-1);
  }
}

void VMThread::inner_execute(VM_Operation* op) {
  assert(Thread::current()->is_VM_thread(), "Must be the VM thread");

  VM_Operation* prev_vm_operation = nullptr;
  if (_cur_vm_operation != nullptr) {
    // Check that the VM operation allows nested VM operation.
    // This is normally not the case, e.g., the compiler
    // does not allow nested scavenges or compiles.
    if (!_cur_vm_operation->allow_nested_vm_operations()) {
      fatal("Unexpected nested VM operation %s requested by operation %s",
            op->name(), _cur_vm_operation->name());
    }
    op->set_calling_thread(_cur_vm_operation->calling_thread());
    prev_vm_operation = _cur_vm_operation;
  }

  _cur_vm_operation = op;

  HandleMark hm(VMThread::vm_thread());

  const char* const cause = op->cause();
  stringStream ss;
  ss.print("Executing%s%s VM operation: %s",
           prev_vm_operation != nullptr ? " nested" : "",
           op->evaluate_at_safepoint() ? " safepoint" : " non-safepoint",
           op->name());
  if (cause != nullptr) {
    ss.print(" (%s)", cause);
  }

  EventMarkVMOperation em("%s", ss.freeze());
  log_debug(vmthread)("%s", ss.freeze());

  bool end_safepoint = false;
  bool has_timeout_task = (_timeout_task != nullptr);
  if (_cur_vm_operation->evaluate_at_safepoint() &&
      !SafepointSynchronize::is_at_safepoint()) {
    SafepointSynchronize::begin();
    if (has_timeout_task) {
      _timeout_task->arm(_cur_vm_operation->name());
    }
    end_safepoint = true;
  }

  evaluate_operation(_cur_vm_operation);

  if (end_safepoint) {
    if (has_timeout_task) {
      _timeout_task->disarm();
    }
    SafepointSynchronize::end();
  }

  _cur_vm_operation = prev_vm_operation;
}

void VMThread::wait_for_operation() {
  assert(Thread::current()->is_VM_thread(), "Must be the VM thread");
  MonitorLocker ml_op_lock(VMOperation_lock, Mutex::_no_safepoint_check_flag);

  // Clear previous operation.
  // On first call this clears a dummy place-holder.
  _next_vm_operation = nullptr;
  // Notify operation is done and notify a next operation can be installed.
  ml_op_lock.notify_all();

  while (!should_terminate()) {
    self_destruct_if_needed();
    if (_next_vm_operation != nullptr) {
      return;
    }
    if (handshake_or_safepoint_alot()) {
      if (HandshakeALot) {
        MutexUnlocker mul(VMOperation_lock);
        ALotOfHandshakeClosure aohc;
        Handshake::execute(&aohc);
      }
      // When we unlocked above someone might have setup a new op.
      if (_next_vm_operation != nullptr) {
        return;
      }
      if (SafepointALot) {
        _next_vm_operation = &safepointALot_op;
        return;
      }
    }
    assert(_next_vm_operation == nullptr, "Must be");
    assert(_cur_vm_operation  == nullptr, "Must be");

    // We didn't find anything to execute, notify any waiter so they can install an op.
    ml_op_lock.notify_all();
    ml_op_lock.wait(GuaranteedSafepointInterval);
  }
}

void VMThread::loop() {
  assert(_cur_vm_operation == nullptr, "no current one should be executing");

  SafepointSynchronize::init(_vm_thread);

  // Need to set a calling thread for ops not passed
  // via the normal way.
  no_op.set_calling_thread(_vm_thread);
  safepointALot_op.set_calling_thread(_vm_thread);

  while (true) {
    if (should_terminate()) break;
    wait_for_operation();
    if (should_terminate()) break;
    assert(_next_vm_operation != nullptr, "Must have one");
    inner_execute(_next_vm_operation);
  }
}

// A SkipGCALot object is used to elide the usual effect of gc-a-lot
// over a section of execution by a thread. Currently, it's used only to
// prevent re-entrant calls to GC.
class SkipGCALot : public StackObj {
  private:
   bool _saved;
   Thread* _t;

  public:
#ifdef ASSERT
    SkipGCALot(Thread* t) : _t(t) {
      _saved = _t->skip_gcalot();
      _t->set_skip_gcalot(true);
    }

    ~SkipGCALot() {
      assert(_t->skip_gcalot(), "Save-restore protocol invariant");
      _t->set_skip_gcalot(_saved);
    }
#else
    SkipGCALot(Thread* t) { }
    ~SkipGCALot() { }
#endif
};

void VMThread::execute(VM_Operation* op) {
  Thread* t = Thread::current();

  if (t->is_VM_thread()) {
    op->set_calling_thread(t);
    ((VMThread*)t)->inner_execute(op);
    return;
  }

  // The current thread must not belong to the SuspendibleThreadSet, because an
  // on-the-fly safepoint can be waiting for the current thread, and the
  // current thread will be blocked in wait_until_executed, resulting in
  // deadlock.
  assert(!t->is_suspendible_thread(), "precondition");
  assert(!t->is_indirectly_suspendible_thread(), "precondition");

  // Avoid re-entrant attempts to gc-a-lot
  SkipGCALot sgcalot(t);

  // JavaThread or WatcherThread
  if (t->is_Java_thread()) {
    JavaThread::cast(t)->check_for_valid_safepoint_state();
  }

  // New request from Java thread, evaluate prologue
  if (!op->doit_prologue()) {
    return;   // op was cancelled
  }

  op->set_calling_thread(t);

  wait_until_executed(op);

  op->doit_epilogue();
}

void VMThread::verify() {
  oops_do(&VerifyOopClosure::verify_oop, nullptr);
}

/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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

#include "cds/dynamicArchive.hpp"
#include "ci/ciEnv.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/javaThreadStatus.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/scopeDesc.hpp"
#include "compiler/compilerThread.hpp"
#include "compiler/compileTask.hpp"
#include "gc/shared/oopStorage.hpp"
#include "gc/shared/oopStorageSet.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "jfr/jfrEvents.hpp"
#include "jvm.h"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "logging/log.hpp"
#include "logging/logAsyncWriter.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "memory/universe.hpp"
#include "oops/access.inline.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/verifyOopClosure.hpp"
#include "prims/jvm_misc.hpp"
#include "prims/jvmtiDeferredUpdates.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.inline.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/continuation.hpp"
#include "runtime/continuationEntry.inline.hpp"
#include "runtime/continuationHelper.inline.hpp"
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/handshake.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/lockStack.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/safepointMechanism.inline.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/serviceThread.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/stackWatermarkSet.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/threadIdentifier.hpp"
#include "runtime/threadSMR.inline.hpp"
#include "runtime/threadStatisticalInfo.hpp"
#include "runtime/threadWXSetters.inline.hpp"
#include "runtime/timer.hpp"
#include "runtime/timerTrace.hpp"
#include "runtime/vframe.inline.hpp"
#include "runtime/vframe_hp.hpp"
#include "runtime/vframeArray.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "services/threadService.hpp"
#include "utilities/copy.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/dtrace.hpp"
#include "utilities/events.hpp"
#include "utilities/macros.hpp"
#include "utilities/nativeStackPrinter.hpp"
#include "utilities/preserveException.hpp"
#include "utilities/spinYield.hpp"
#include "utilities/vmError.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#include "jvmci/jvmciEnv.hpp"
#endif
#if INCLUDE_JFR
#include "jfr/jfr.hpp"
#endif

// Set by os layer.
size_t      JavaThread::_stack_size_at_create = 0;

#ifdef DTRACE_ENABLED

// Only bother with this argument setup if dtrace is available

  #define HOTSPOT_THREAD_PROBE_start HOTSPOT_THREAD_START
  #define HOTSPOT_THREAD_PROBE_stop HOTSPOT_THREAD_STOP

  #define DTRACE_THREAD_PROBE(probe, javathread)                           \
    if (!javathread->is_aot_thread()) {                                    \
      ResourceMark rm(this);                                               \
      int len = 0;                                                         \
      const char* name = (javathread)->name();                             \
      len = strlen(name);                                                  \
      HOTSPOT_THREAD_PROBE_##probe(/* probe = start, stop */               \
        (char *) name, len,                                                \
        java_lang_Thread::thread_id((javathread)->threadObj()),            \
        (uintptr_t) (javathread)->osthread()->thread_id(),                 \
        java_lang_Thread::is_daemon((javathread)->threadObj()));           \
    }

#else //  ndef DTRACE_ENABLED

  #define DTRACE_THREAD_PROBE(probe, javathread)

#endif // ndef DTRACE_ENABLED

void JavaThread::smr_delete() {
  if (_on_thread_list) {
    ThreadsSMRSupport::smr_delete(this);
  } else {
    delete this;
  }
}

// Initialized by VMThread at vm_global_init
OopStorage* JavaThread::_thread_oop_storage = nullptr;

OopStorage* JavaThread::thread_oop_storage() {
  assert(_thread_oop_storage != nullptr, "not yet initialized");
  return _thread_oop_storage;
}

void JavaThread::set_threadOopHandles(oop p) {
  assert(_thread_oop_storage != nullptr, "not yet initialized");
  _threadObj   = OopHandle(_thread_oop_storage, p);
  _vthread     = OopHandle(_thread_oop_storage, p);
  _jvmti_vthread = OopHandle(_thread_oop_storage, p->is_a(vmClasses::BoundVirtualThread_klass()) ? p : nullptr);
  _scopedValueCache = OopHandle(_thread_oop_storage, nullptr);
}

oop JavaThread::threadObj() const {
  // Ideally we would verify the current thread is oop_safe when this is called, but as we can
  // be called from a signal handler we would have to use Thread::current_or_null_safe(). That
  // has overhead and also interacts poorly with GetLastError on Windows due to the use of TLS.
  // Instead callers must verify oop safe access.
  return _threadObj.resolve();
}

oop JavaThread::vthread() const {
  return _vthread.resolve();
}

void JavaThread::set_vthread(oop p) {
  assert(_thread_oop_storage != nullptr, "not yet initialized");
  _vthread.replace(p);
}

oop JavaThread::jvmti_vthread() const {
  return _jvmti_vthread.resolve();
}

void JavaThread::set_jvmti_vthread(oop p) {
  assert(_thread_oop_storage != nullptr, "not yet initialized");
  _jvmti_vthread.replace(p);
}

// If there is a virtual thread mounted then return vthread() oop.
// Otherwise, return threadObj().
oop JavaThread::vthread_or_thread() const {
  oop result = vthread();
  if (result == nullptr) {
    result = threadObj();
  }
  return result;
}

oop JavaThread::scopedValueCache() const {
  return _scopedValueCache.resolve();
}

void JavaThread::set_scopedValueCache(oop p) {
  if (!_scopedValueCache.is_empty()) { // i.e. if the OopHandle has been allocated
    _scopedValueCache.replace(p);
  } else {
    assert(p == nullptr, "not yet initialized");
  }
}

void JavaThread::clear_scopedValueBindings() {
  set_scopedValueCache(nullptr);
  oop vthread_oop = vthread();
  // vthread may be null here if we get a VM error during startup,
  // before the java.lang.Thread instance has been created.
  if (vthread_oop != nullptr) {
    java_lang_Thread::clear_scopedValueBindings(vthread_oop);
  }
}

void JavaThread::allocate_threadObj(Handle thread_group, const char* thread_name,
                                    bool daemon, TRAPS) {
  assert(thread_group.not_null(), "thread group should be specified");
  assert(threadObj() == nullptr, "should only create Java thread object once");

  InstanceKlass* ik = vmClasses::Thread_klass();
  assert(ik->is_initialized(), "must be");
  instanceHandle thread_oop = ik->allocate_instance_handle(CHECK);

  // We are called from jni_AttachCurrentThread/jni_AttachCurrentThreadAsDaemon.
  // We cannot use JavaCalls::construct_new_instance because the java.lang.Thread
  // constructor calls Thread.current(), which must be set here.
  java_lang_Thread::set_thread(thread_oop(), this);
  set_threadOopHandles(thread_oop());

  JavaValue result(T_VOID);
  if (thread_name != nullptr) {
    Handle name = java_lang_String::create_from_str(thread_name, CHECK);
    // Thread gets assigned specified name and null target
    JavaCalls::call_special(&result,
                            thread_oop,
                            ik,
                            vmSymbols::object_initializer_name(),
                            vmSymbols::threadgroup_string_void_signature(),
                            thread_group,
                            name,
                            CHECK);
  } else {
    // Thread gets assigned name "Thread-nnn" and null target
    // (java.lang.Thread doesn't have a constructor taking only a ThreadGroup argument)
    JavaCalls::call_special(&result,
                            thread_oop,
                            ik,
                            vmSymbols::object_initializer_name(),
                            vmSymbols::threadgroup_runnable_void_signature(),
                            thread_group,
                            Handle(),
                            CHECK);
  }

  os::set_priority(this, NormPriority);

  if (daemon) {
    java_lang_Thread::set_daemon(thread_oop());
  }
}

// ======= JavaThread ========

#if INCLUDE_JVMCI

jlong* JavaThread::_jvmci_old_thread_counters;

static bool jvmci_counters_include(JavaThread* thread) {
  return !JVMCICountersExcludeCompiler || !thread->is_Compiler_thread();
}

void JavaThread::collect_counters(jlong* array, int length) {
  assert(length == JVMCICounterSize, "wrong value");
  for (int i = 0; i < length; i++) {
    array[i] = _jvmci_old_thread_counters[i];
  }
  for (JavaThread* tp : ThreadsListHandle()) {
    if (jvmci_counters_include(tp)) {
      for (int i = 0; i < length; i++) {
        array[i] += tp->_jvmci_counters[i];
      }
    }
  }
}

// Attempt to enlarge the array for per thread counters.
static jlong* resize_counters_array(jlong* old_counters, int current_size, int new_size) {
  jlong* new_counters = NEW_C_HEAP_ARRAY_RETURN_NULL(jlong, new_size, mtJVMCI);
  if (new_counters == nullptr) {
    return nullptr;
  }
  if (old_counters == nullptr) {
    old_counters = new_counters;
    memset(old_counters, 0, sizeof(jlong) * new_size);
  } else {
    for (int i = 0; i < MIN2((int) current_size, new_size); i++) {
      new_counters[i] = old_counters[i];
    }
    if (new_size > current_size) {
      memset(new_counters + current_size, 0, sizeof(jlong) * (new_size - current_size));
    }
    FREE_C_HEAP_ARRAY(jlong, old_counters);
  }
  return new_counters;
}

// Attempt to enlarge the array for per thread counters.
bool JavaThread::resize_counters(int current_size, int new_size) {
  jlong* new_counters = resize_counters_array(_jvmci_counters, current_size, new_size);
  if (new_counters == nullptr) {
    return false;
  } else {
    _jvmci_counters = new_counters;
    return true;
  }
}

class VM_JVMCIResizeCounters : public VM_Operation {
 private:
  int _new_size;
  bool _failed;

 public:
  VM_JVMCIResizeCounters(int new_size) : _new_size(new_size), _failed(false) { }
  VMOp_Type type()                  const        { return VMOp_JVMCIResizeCounters; }
  bool allow_nested_vm_operations() const        { return true; }
  void doit() {
    // Resize the old thread counters array
    jlong* new_counters = resize_counters_array(JavaThread::_jvmci_old_thread_counters, JVMCICounterSize, _new_size);
    if (new_counters == nullptr) {
      _failed = true;
      return;
    } else {
      JavaThread::_jvmci_old_thread_counters = new_counters;
    }

    // Now resize each threads array
    for (JavaThread* tp : ThreadsListHandle()) {
      if (!tp->resize_counters(JVMCICounterSize, _new_size)) {
        _failed = true;
        break;
      }
    }
    if (!_failed) {
      JVMCICounterSize = _new_size;
    }
  }

  bool failed() { return _failed; }
};

bool JavaThread::resize_all_jvmci_counters(int new_size) {
  VM_JVMCIResizeCounters op(new_size);
  VMThread::execute(&op);
  return !op.failed();
}

#endif // INCLUDE_JVMCI

#ifdef ASSERT
// Checks safepoint allowed and clears unhandled oops at potential safepoints.
void JavaThread::check_possible_safepoint() {
  if (_no_safepoint_count > 0) {
    print_owned_locks();
    assert(false, "Possible safepoint reached by thread that does not allow it");
  }
#ifdef CHECK_UNHANDLED_OOPS
  // Clear unhandled oops in JavaThreads so we get a crash right away.
  clear_unhandled_oops();
#endif // CHECK_UNHANDLED_OOPS
}

void JavaThread::check_for_valid_safepoint_state() {
  // Don't complain if running a debugging command.
  if (DebuggingContext::is_enabled()) return;

  // Check NoSafepointVerifier, which is implied by locks taken that can be
  // shared with the VM thread.  This makes sure that no locks with allow_vm_block
  // are held.
  check_possible_safepoint();

  if (thread_state() != _thread_in_vm) {
    fatal("LEAF method calling lock?");
  }

  if (GCALotAtAllSafepoints) {
    // We could enter a safepoint here and thus have a gc
    InterfaceSupport::check_gc_alot();
  }
}
#endif // ASSERT

// A JavaThread is a normal Java thread

JavaThread::JavaThread(MemTag mem_tag) :
  Thread(mem_tag),
  // Initialize fields
  _on_thread_list(false),
  DEBUG_ONLY(_java_call_counter(0) COMMA)
  _entry_point(nullptr),
  _deopt_mark(nullptr),
  _deopt_nmethod(nullptr),
  _vframe_array_head(nullptr),
  _vframe_array_last(nullptr),
  _jvmti_deferred_updates(nullptr),
  _callee_target(nullptr),
  _vm_result_oop(nullptr),
  _vm_result_metadata(nullptr),

  _current_pending_monitor(nullptr),
  _current_pending_monitor_is_from_java(true),
  _current_waiting_monitor(nullptr),
  _active_handles(nullptr),
  _free_handle_block(nullptr),
  _monitor_owner_id(0),

  _suspend_flags(0),

  _thread_state(_thread_new),
  _saved_exception_pc(nullptr),
#ifdef ASSERT
  _no_safepoint_count(0),
  _visited_for_critical_count(false),
#endif

  _terminated(_not_terminated),
  _in_deopt_handler(0),
  _doing_unsafe_access(false),
  _throwing_unsafe_access_error(false),
  _do_not_unlock_if_synchronized(false),
#if INCLUDE_JVMTI
  _carrier_thread_suspended(false),
  _is_disable_suspend(false),
  _is_in_java_upcall(false),
  _jvmti_events_disabled(0),
  _on_monitor_waited_event(false),
  _contended_entered_monitor(nullptr),
#endif
  _jni_attach_state(_not_attaching_via_jni),
  _is_in_internal_oome_mark(false),
#if INCLUDE_JVMCI
  _pending_deoptimization(-1),
  _pending_monitorenter(false),
  _pending_transfer_to_interpreter(false),
  _pending_failed_speculation(0),
  _jvmci{nullptr},
  _libjvmci_runtime(nullptr),
  _jvmci_counters(nullptr),
  _jvmci_reserved0(0),
  _jvmci_reserved1(0),
  _jvmci_reserved_oop0(nullptr),
  _live_nmethod(nullptr),
#endif // INCLUDE_JVMCI

  _exception_oop(oop()),
  _exception_pc(nullptr),
  _exception_handler_pc(nullptr),

  _jni_active_critical(0),
  _pending_jni_exception_check_fn(nullptr),
  _depth_first_number(0),

  // JVMTI PopFrame support
  _popframe_condition(popframe_inactive),
  _frames_to_pop_failed_realloc(0),

  _cont_entry(nullptr),
  _cont_fastpath(nullptr),
  _cont_fastpath_thread_state(1),
  _unlocked_inflated_monitor(nullptr),

  _preempt_alternate_return(nullptr),
  _preemption_cancelled(false),
  _pending_interrupted_exception(false),
  _at_preemptable_init(false),
  DEBUG_ONLY(_preempt_init_klass(nullptr) COMMA)
  DEBUG_ONLY(_interp_at_preemptable_vmcall_cnt(0) COMMA)
  DEBUG_ONLY(_interp_redoing_vm_call(false) COMMA)

  _handshake(this),
  _suspend_resume_manager(this, &_handshake._lock),

  _is_in_vthread_transition(false),
  JVMTI_ONLY(_is_vthread_transition_disabler(false) COMMA)
  DEBUG_ONLY(_is_disabler_at_start(false) COMMA)

  _popframe_preserved_args(nullptr),
  _popframe_preserved_args_size(0),

  _jvmti_thread_state(nullptr),
  _interp_only_mode(0),
  _should_post_on_exceptions_flag(JNI_FALSE),
  _thread_stat(new ThreadStatistics()),

  _parker(),

  _class_to_be_initialized(nullptr),
  _class_being_initialized(nullptr),

  _SleepEvent(ParkEvent::Allocate(this)),

#if INCLUDE_JFR
  _last_freeze_fail_result(freeze_ok),
#endif

#ifdef MACOS_AARCH64
  _cur_wx_enable(nullptr),
  _cur_wx_mode(nullptr),
#endif

  _lock_stack(this),
  _om_cache(this) {
  set_jni_functions(jni_functions());

#if INCLUDE_JVMCI
  assert(_jvmci._implicit_exception_pc == nullptr, "must be");
  if (JVMCICounterSize > 0) {
    resize_counters(0, (int) JVMCICounterSize);
  }
#endif // INCLUDE_JVMCI

  // Setup safepoint state info for this thread
  ThreadSafepointState::create(this);

  SafepointMechanism::initialize_header(this);

  set_requires_cross_modify_fence(false);

  pd_initialize();
}

JavaThread* JavaThread::create_attaching_thread() {
  JavaThread* jt = new JavaThread();
  jt->_jni_attach_state = _attaching_via_jni;
  return jt;
}

// interrupt support

void JavaThread::interrupt() {
  // All callers should have 'this' thread protected by a
  // ThreadsListHandle so that it cannot terminate and deallocate
  // itself.
  DEBUG_ONLY(check_for_dangling_thread_pointer(this);)

  // For Windows _interrupt_event
  WINDOWS_ONLY(osthread()->set_interrupted(true);)

  // For Thread.sleep
  _SleepEvent->unpark();

  // For JSR166 LockSupport.park
  parker()->unpark();

  // For ObjectMonitor and JvmtiRawMonitor
  _ParkEvent->unpark();
}

bool JavaThread::is_interrupted(bool clear_interrupted) {
  DEBUG_ONLY(check_for_dangling_thread_pointer(this);)

  if (_threadObj.peek() == nullptr) {
    // If there is no j.l.Thread then it is impossible to have
    // been interrupted. We can find null during VM initialization
    // or when a JNI thread is still in the process of attaching.
    // In such cases this must be the current thread.
    assert(this == Thread::current(), "invariant");
    return false;
  }

  bool interrupted = java_lang_Thread::interrupted(threadObj());

  // NOTE that since there is no "lock" around the interrupt and
  // is_interrupted operations, there is the possibility that the
  // interrupted flag will be "false" but that the
  // low-level events will be in the signaled state. This is
  // intentional. The effect of this is that Object.wait() and
  // LockSupport.park() will appear to have a spurious wakeup, which
  // is allowed and not harmful, and the possibility is so rare that
  // it is not worth the added complexity to add yet another lock.
  // For the sleep event an explicit reset is performed on entry
  // to JavaThread::sleep, so there is no early return. It has also been
  // recommended not to put the interrupted flag into the "event"
  // structure because it hides the issue.
  // Also, because there is no lock, we must only clear the interrupt
  // state if we are going to report that we were interrupted; otherwise
  // an interrupt that happens just after we read the field would be lost.
  if (interrupted && clear_interrupted) {
    assert(this == Thread::current(), "only the current thread can clear");
    java_lang_Thread::set_interrupted(threadObj(), false);
    WINDOWS_ONLY(osthread()->set_interrupted(false);)
  }
  return interrupted;
}

// This is only for use by JVMTI RawMonitorWait. It emulates the actions of
// the Java code in Object::wait which are not present in RawMonitorWait.
bool JavaThread::get_and_clear_interrupted() {
  if (!is_interrupted(false)) {
    return false;
  }
  oop thread_oop = vthread_or_thread();
  bool is_virtual = java_lang_VirtualThread::is_instance(thread_oop);

  if (!is_virtual) {
    return is_interrupted(true);
  }
  // Virtual thread: clear interrupt status for both virtual and
  // carrier threads under the interruptLock protection.
  JavaThread* current = JavaThread::current();
  HandleMark hm(current);
  Handle thread_h(current, thread_oop);
  ObjectLocker lock(Handle(current, java_lang_Thread::interrupt_lock(thread_h())), current);

  // re-check the interrupt status under the interruptLock protection
  bool interrupted = java_lang_Thread::interrupted(thread_h());

  if (interrupted) {
    assert(this == Thread::current(), "only the current thread can clear");
    java_lang_Thread::set_interrupted(thread_h(), false);  // clear for virtual
    java_lang_Thread::set_interrupted(threadObj(), false); // clear for carrier
    WINDOWS_ONLY(osthread()->set_interrupted(false);)
  }
  return interrupted;
}

void JavaThread::block_if_vm_exited() {
  if (_terminated == _vm_exited) {
    // _vm_exited is set at safepoint, and Threads_lock is never released
    // so we will block here forever.
    // Here we can be doing a jump from a safe state to an unsafe state without
    // proper transition, but it happens after the final safepoint has begun so
    // this jump won't cause any safepoint problems.
    set_thread_state(_thread_in_vm);
    Threads_lock->lock();
    ShouldNotReachHere();
  }
}

JavaThread::JavaThread(ThreadFunction entry_point, size_t stack_sz, MemTag mem_tag) : JavaThread(mem_tag) {
  set_entry_point(entry_point);
  // Create the native thread itself.
  // %note runtime_23
  os::ThreadType thr_type = os::java_thread;
  thr_type = entry_point == &CompilerThread::thread_entry ? os::compiler_thread :
                                                            os::java_thread;
  os::create_thread(this, thr_type, stack_sz);
  // The _osthread may be null here because we ran out of memory (too many threads active).
  // We need to throw and OutOfMemoryError - however we cannot do this here because the caller
  // may hold a lock and all locks must be unlocked before throwing the exception (throwing
  // the exception consists of creating the exception object & initializing it, initialization
  // will leave the VM via a JavaCall and then all locks must be unlocked).
  //
  // The thread is still suspended when we reach here. Thread must be explicit started
  // by creator! Furthermore, the thread must also explicitly be added to the Threads list
  // by calling Threads:add. The reason why this is not done here, is because the thread
  // object must be fully initialized (take a look at JVM_Start)
}

JavaThread::~JavaThread() {

  // Enqueue OopHandles for release by the service thread.
  add_oop_handles_for_release();

  // Return the sleep event to the free list
  ParkEvent::Release(_SleepEvent);
  _SleepEvent = nullptr;

  // Free any remaining  previous UnrollBlock
  vframeArray* old_array = vframe_array_last();

  if (old_array != nullptr) {
    Deoptimization::UnrollBlock* old_info = old_array->unroll_block();
    old_array->set_unroll_block(nullptr);
    delete old_info;
    delete old_array;
  }

  JvmtiDeferredUpdates* updates = deferred_updates();
  if (updates != nullptr) {
    // This can only happen if thread is destroyed before deoptimization occurs.
    assert(updates->count() > 0, "Updates holder not deleted");
    // free deferred updates.
    delete updates;
    set_deferred_updates(nullptr);
  }

  // All Java related clean up happens in exit
  ThreadSafepointState::destroy(this);
  if (_thread_stat != nullptr) delete _thread_stat;

#if INCLUDE_JVMCI
  if (JVMCICounterSize > 0) {
    FREE_C_HEAP_ARRAY(jlong, _jvmci_counters);
  }
#endif // INCLUDE_JVMCI
}


// First JavaThread specific code executed by a new Java thread.
void JavaThread::pre_run() {
  // empty - see comments in run()
}

// The main routine called by a new Java thread. This isn't overridden
// by subclasses, instead different subclasses define a different "entry_point"
// which defines the actual logic for that kind of thread.
void JavaThread::run() {
  // initialize thread-local alloc buffer related fields
  initialize_tlab();

  _stack_overflow_state.create_stack_guard_pages();

  cache_global_variables();

  // Thread is now sufficiently initialized to be handled by the safepoint code as being
  // in the VM. Change thread state from _thread_new to _thread_in_vm
  assert(this->thread_state() == _thread_new, "wrong thread state");
  set_thread_state(_thread_in_vm);

  // Before a thread is on the threads list it is always safe, so after leaving the
  // _thread_new we should emit a instruction barrier. The distance to modified code
  // from here is probably far enough, but this is consistent and safe.
  OrderAccess::cross_modify_fence();

  assert(JavaThread::current() == this, "sanity check");
  assert(!Thread::current()->owns_locks(), "sanity check");

  JFR_ONLY(Jfr::on_thread_start(this);)

  DTRACE_THREAD_PROBE(start, this);

  // This operation might block. We call that after all safepoint checks for a new thread has
  // been completed.
  set_active_handles(JNIHandleBlock::allocate_block());

  if (JvmtiExport::should_post_thread_life()) {
    JvmtiExport::post_thread_start(this);

  }

  if (AlwaysPreTouchStacks) {
    pretouch_stack();
  }

  // We call another function to do the rest so we are sure that the stack addresses used
  // from there will be lower than the stack base just computed.
  thread_main_inner();
}

void JavaThread::thread_main_inner() {
  assert(JavaThread::current() == this, "sanity check");
  assert(_threadObj.peek() != nullptr || is_aot_thread(), "just checking");

  // Execute thread entry point unless this thread has a pending exception.
  // Note: Due to JVMTI StopThread we can have pending exceptions already!
  if (!this->has_pending_exception()) {
    {
      ResourceMark rm(this);
      this->set_native_thread_name(this->name());
    }
    HandleMark hm(this);
    this->entry_point()(this, this);
  }

  DTRACE_THREAD_PROBE(stop, this);

  // Cleanup is handled in post_run()
}

// Shared teardown for all JavaThreads
void JavaThread::post_run() {
  this->exit(false);
  this->unregister_thread_stack_with_NMT();
  // Defer deletion to here to ensure 'this' is still referenceable in call_run
  // for any shared tear-down.
  this->smr_delete();
}

static void ensure_join(JavaThread* thread) {
  // We do not need to grab the Threads_lock, since we are operating on ourself.
  Handle threadObj(thread, thread->threadObj());
  assert(threadObj.not_null(), "java thread object must exist");
  ObjectLocker lock(threadObj, thread);
  // Thread is exiting. So set thread_status field in  java.lang.Thread class to TERMINATED.
  java_lang_Thread::set_thread_status(threadObj(), JavaThreadStatus::TERMINATED);
  // Clear the native thread instance - this makes isAlive return false and allows the join()
  // to complete once we've done the notify_all below. Needs a release() to obey Java Memory Model
  // requirements.
  assert(java_lang_Thread::thread(threadObj()) == thread, "must be alive");
  java_lang_Thread::release_set_thread(threadObj(), nullptr);
  lock.notify_all(thread);
  // Ignore pending exception, since we are exiting anyway
  thread->clear_pending_exception();
}

static bool is_daemon(oop threadObj) {
  return (threadObj != nullptr && java_lang_Thread::is_daemon(threadObj));
}

// For any new cleanup additions, please check to see if they need to be applied to
// cleanup_failed_attach_current_thread as well.
void JavaThread::exit(bool destroy_vm, ExitType exit_type) {
  assert(this == JavaThread::current(), "thread consistency check");
  assert(!is_exiting(), "should not be exiting or terminated already");

  elapsedTimer _timer_exit_phase1;
  elapsedTimer _timer_exit_phase2;
  elapsedTimer _timer_exit_phase3;
  elapsedTimer _timer_exit_phase4;

  om_clear_monitor_cache();

  if (log_is_enabled(Debug, os, thread, timer)) {
    _timer_exit_phase1.start();
  }

  HandleMark hm(this);
  Handle uncaught_exception(this, this->pending_exception());
  this->clear_pending_exception();
  Handle threadObj(this, this->threadObj());
  assert(threadObj.not_null(), "Java thread object should be created");

  if (!destroy_vm) {
    if (uncaught_exception.not_null()) {
      EXCEPTION_MARK;
      // Call method Thread.dispatchUncaughtException().
      Klass* thread_klass = vmClasses::Thread_klass();
      JavaValue result(T_VOID);
      JavaCalls::call_virtual(&result,
                              threadObj, thread_klass,
                              vmSymbols::dispatchUncaughtException_name(),
                              vmSymbols::throwable_void_signature(),
                              uncaught_exception,
                              THREAD);
      if (HAS_PENDING_EXCEPTION) {
        ResourceMark rm(this);
        jio_fprintf(defaultStream::error_stream(),
                    "\nException: %s thrown from the UncaughtExceptionHandler"
                    " in thread \"%s\"\n",
                    pending_exception()->klass()->external_name(),
                    name());
        CLEAR_PENDING_EXCEPTION;
      }
    }

    if (!is_Compiler_thread()) {
      // We have finished executing user-defined Java code and now have to do the
      // implementation specific clean-up by calling Thread.exit(). We prevent any
      // asynchronous exceptions from being delivered while in Thread.exit()
      // to ensure the clean-up is not corrupted.
      NoAsyncExceptionDeliveryMark _no_async(this);

      EXCEPTION_MARK;
      JavaValue result(T_VOID);
      Klass* thread_klass = vmClasses::Thread_klass();
      JavaCalls::call_virtual(&result,
                              threadObj, thread_klass,
                              vmSymbols::exit_method_name(),
                              vmSymbols::void_method_signature(),
                              THREAD);
      CLEAR_PENDING_EXCEPTION;
    }

    // notify JVMTI
    if (JvmtiExport::should_post_thread_life()) {
      JvmtiExport::post_thread_end(this);
    }
  } else {
    // before_exit() has already posted JVMTI THREAD_END events
  }

  // Cleanup any pending async exception now since we cannot access oops after
  // BarrierSet::barrier_set()->on_thread_detach() has been executed.
  if (has_async_exception_condition()) {
    handshake_state()->clean_async_exception_operation();
  }

  // The careful dance between thread suspension and exit is handled here.
  // Since we are in thread_in_vm state and suspension is done with handshakes,
  // we can just put in the exiting state and it will be correctly handled.
  // Also, no more async exceptions will be added to the queue after this point.
  set_terminated(_thread_exiting);
  ThreadService::current_thread_exiting(this, is_daemon(threadObj()));

  if (log_is_enabled(Debug, os, thread, timer)) {
    _timer_exit_phase1.stop();
    _timer_exit_phase2.start();
  }

  // Capture daemon status before the thread is marked as terminated.
  bool daemon = is_daemon(threadObj());

  // Notify waiters on thread object. This has to be done after exit() is called
  // on the thread (if the thread is the last thread in a daemon ThreadGroup the
  // group should have the destroyed bit set before waiters are notified).
  ensure_join(this);
  assert(!this->has_pending_exception(), "ensure_join should have cleared");

  if (log_is_enabled(Debug, os, thread, timer)) {
    _timer_exit_phase2.stop();
    _timer_exit_phase3.start();
  }
  // 6282335 JNI DetachCurrentThread spec states that all Java monitors
  // held by this thread must be released. The spec does not distinguish
  // between JNI-acquired and regular Java monitors. We can only see
  // regular Java monitors here if monitor enter-exit matching is broken.
  //
  // ensure_join() ignores IllegalThreadStateExceptions, and so does
  // ObjectSynchronizer::release_monitors_owned_by_thread().
  if (exit_type == jni_detach) {
    // Sanity check even though JNI DetachCurrentThread() would have
    // returned JNI_ERR if there was a Java frame. JavaThread exit
    // should be done executing Java code by the time we get here.
    assert(!this->has_last_Java_frame(),
           "should not have a Java frame when detaching or exiting");
    ObjectSynchronizer::release_monitors_owned_by_thread(this);
    assert(!this->has_pending_exception(), "release_monitors should have cleared");
  }

  // These things needs to be done while we are still a Java Thread. Make sure that thread
  // is in a consistent state, in case GC happens
  JFR_ONLY(Jfr::on_thread_exit(this);)

  if (active_handles() != nullptr) {
    JNIHandleBlock* block = active_handles();
    set_active_handles(nullptr);
    JNIHandleBlock::release_block(block);
  }

  if (free_handle_block() != nullptr) {
    JNIHandleBlock* block = free_handle_block();
    set_free_handle_block(nullptr);
    JNIHandleBlock::release_block(block);
  }

  // These have to be removed while this is still a valid thread.
  _stack_overflow_state.remove_stack_guard_pages();

  if (UseTLAB) {
    retire_tlab();
  }

  if (JvmtiEnv::environments_might_exist()) {
    JvmtiExport::cleanup_thread(this);
  }

  // We need to cache the thread name for logging purposes below as once
  // we have called on_thread_detach this thread must not access any oops.
  char* thread_name = nullptr;
  if (log_is_enabled(Debug, os, thread, timer)) {
    ResourceMark rm(this);
    thread_name = os::strdup(name());
  }

  if (log_is_enabled(Info, os, thread)) {
    ResourceMark rm(this);
    log_info(os, thread)("JavaThread %s (name: \"%s\", tid: %zu).",
                         exit_type == JavaThread::normal_exit ? "exiting" : "detaching",
                         name(), os::current_thread_id());
  }

  if (log_is_enabled(Debug, os, thread, timer)) {
    _timer_exit_phase3.stop();
    _timer_exit_phase4.start();
  }

#if INCLUDE_JVMCI
  if (JVMCICounterSize > 0) {
    if (jvmci_counters_include(this)) {
      for (int i = 0; i < JVMCICounterSize; i++) {
        _jvmci_old_thread_counters[i] += _jvmci_counters[i];
      }
    }
  }
#endif // INCLUDE_JVMCI

  // Remove from list of active threads list, and notify VM thread if we are the last non-daemon thread.
  // We call BarrierSet::barrier_set()->on_thread_detach() here so no touching of oops after this point.
  Threads::remove(this, daemon);

  if (log_is_enabled(Debug, os, thread, timer)) {
    _timer_exit_phase4.stop();
    log_debug(os, thread, timer)("name='%s'"
                                 ", exit-phase1=" JLONG_FORMAT
                                 ", exit-phase2=" JLONG_FORMAT
                                 ", exit-phase3=" JLONG_FORMAT
                                 ", exit-phase4=" JLONG_FORMAT,
                                 thread_name,
                                 _timer_exit_phase1.milliseconds(),
                                 _timer_exit_phase2.milliseconds(),
                                 _timer_exit_phase3.milliseconds(),
                                 _timer_exit_phase4.milliseconds());
    os::free(thread_name);
  }
}

void JavaThread::cleanup_failed_attach_current_thread(bool is_daemon) {
  if (active_handles() != nullptr) {
    JNIHandleBlock* block = active_handles();
    set_active_handles(nullptr);
    JNIHandleBlock::release_block(block);
  }

  if (free_handle_block() != nullptr) {
    JNIHandleBlock* block = free_handle_block();
    set_free_handle_block(nullptr);
    JNIHandleBlock::release_block(block);
  }

  // These have to be removed while this is still a valid thread.
  _stack_overflow_state.remove_stack_guard_pages();

  if (UseTLAB) {
    retire_tlab();
  }

  Threads::remove(this, is_daemon);
}

JavaThread* JavaThread::active() {
  Thread* thread = Thread::current();
  if (thread->is_Java_thread()) {
    return JavaThread::cast(thread);
  } else {
    assert(thread->is_VM_thread(), "this must be a vm thread");
    VM_Operation* op = ((VMThread*) thread)->vm_operation();
    JavaThread *ret = op == nullptr ? nullptr : JavaThread::cast(op->calling_thread());
    return ret;
  }
}

oop JavaThread::exception_oop() const {
  return AtomicAccess::load(&_exception_oop);
}

void JavaThread::set_exception_oop(oop o) {
  AtomicAccess::store(&_exception_oop, o);
}

void JavaThread::handle_special_runtime_exit_condition() {
  // We mustn't block for object deopt if the thread is
  // currently executing in a JNI critical region, as that
  // can cause deadlock because allocation may be locked out
  // and the object deopt suspender may try to allocate.
  if (is_obj_deopt_suspend() && !in_critical()) {
    frame_anchor()->make_walkable();
    wait_for_object_deoptimization();
  }
}


// Asynchronous exceptions support
//
void JavaThread::handle_async_exception(oop java_throwable) {
  assert(java_throwable != nullptr, "should have an _async_exception to throw");
  assert(!is_at_poll_safepoint(), "should have never called this method");

  if (has_last_Java_frame()) {
    frame f = last_frame();
    if (f.is_runtime_frame()) {
      // If the topmost frame is a runtime stub, then we are calling into
      // OptoRuntime from compiled code. Some runtime stubs (new, monitor_exit..)
      // must deoptimize the caller before continuing, as the compiled exception
      // handler table may not be valid.
      RegisterMap reg_map(this,
                          RegisterMap::UpdateMap::skip,
                          RegisterMap::ProcessFrames::include,
                          RegisterMap::WalkContinuation::skip);
      frame compiled_frame = f.sender(&reg_map);
      if (!StressCompiledExceptionHandlers && compiled_frame.can_be_deoptimized()) {
        Deoptimization::deoptimize(this, compiled_frame);
      }
    }
  }

  // We cannot call Exceptions::_throw(...) here because we cannot block
  set_pending_exception(java_throwable, __FILE__, __LINE__);

  clear_scopedValueBindings();

  LogTarget(Info, exceptions) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    ls.print("Async. exception installed at runtime exit (" INTPTR_FORMAT ")", p2i(this));
    if (has_last_Java_frame()) {
      frame f = last_frame();
      ls.print(" (pc: " INTPTR_FORMAT " sp: " INTPTR_FORMAT " )", p2i(f.pc()), p2i(f.sp()));
    }
    ls.print_cr(" of type: %s", java_throwable->klass()->external_name());
  }
}

void JavaThread::install_async_exception(AsyncExceptionHandshakeClosure* aehc) {
  // Do not throw asynchronous exceptions against the compiler thread
  // or if the thread is already exiting.
  if (!can_call_java() || is_exiting()) {
    delete aehc;
    return;
  }

  oop exception = aehc->exception();
  Handshake::execute(aehc, this);  // Install asynchronous handshake

  ResourceMark rm;
  if (log_is_enabled(Info, exceptions)) {
    log_info(exceptions)("Pending Async. exception installed of type: %s",
                         exception->klass()->external_name());
  }
  // for AbortVMOnException flag
  Exceptions::debug_check_abort(exception->klass()->external_name());

  oop vt_oop = vthread();
  if (vt_oop == nullptr || !vt_oop->is_a(vmClasses::BaseVirtualThread_klass())) {
    // Interrupt thread so it will wake up from a potential wait()/sleep()/park()
    this->interrupt();
  }
}

class InstallAsyncExceptionHandshakeClosure : public HandshakeClosure {
  AsyncExceptionHandshakeClosure* _aehc;
public:
  InstallAsyncExceptionHandshakeClosure(AsyncExceptionHandshakeClosure* aehc) :
    HandshakeClosure("InstallAsyncException"), _aehc(aehc) {}
  ~InstallAsyncExceptionHandshakeClosure() {
    // If InstallAsyncExceptionHandshakeClosure was never executed we need to clean up _aehc.
    delete _aehc;
  }
  void do_thread(Thread* thr) {
    JavaThread* target = JavaThread::cast(thr);
    target->install_async_exception(_aehc);
    _aehc = nullptr;
  }
};

void JavaThread::send_async_exception(JavaThread* target, oop java_throwable) {
  OopHandle e(Universe::vm_global(), java_throwable);
  InstallAsyncExceptionHandshakeClosure iaeh(new AsyncExceptionHandshakeClosure(e));
  Handshake::execute(&iaeh, target);
}

bool JavaThread::is_in_vthread_transition() const {
  DEBUG_ONLY(Thread* current = Thread::current();)
  assert(is_handshake_safe_for(current) || SafepointSynchronize::is_at_safepoint()
         || JavaThread::cast(current)->is_disabler_at_start(), "not safe");
  return AtomicAccess::load(&_is_in_vthread_transition);
}

void JavaThread::set_is_in_vthread_transition(bool val) {
  assert(is_in_vthread_transition() != val, "already %s transition", val ? "inside" : "outside");
  AtomicAccess::store(&_is_in_vthread_transition, val);
}

#if INCLUDE_JVMTI
void JavaThread::set_is_vthread_transition_disabler(bool val) {
  _is_vthread_transition_disabler = val;
}
#endif

#ifdef ASSERT
void JavaThread::set_is_disabler_at_start(bool val) {
  _is_disabler_at_start = val;
}
#endif

// External suspension mechanism.
//
// Guarantees on return (for a valid target thread):
//   - Target thread will not execute any new bytecode.
//   - Target thread will not enter any new monitors.
//
bool JavaThread::java_suspend(bool register_vthread_SR) {
  // Suspending a vthread transition disabler can cause deadlocks.
  // The HandshakeState::has_operation does not allow such suspends.
  // But the suspender thread is an exclusive transition disablers, so there can't be other disabers here.
  JVMTI_ONLY(assert(!is_vthread_transition_disabler(), "suspender thread is an exclusive transition disabler");)

  guarantee(Thread::is_JavaThread_protected(/* target */ this),
            "target JavaThread is not protected in calling context.");
  return this->suspend_resume_manager()->suspend(register_vthread_SR);
}

bool JavaThread::java_resume(bool register_vthread_SR) {
  guarantee(Thread::is_JavaThread_protected_by_TLH(/* target */ this),
            "missing ThreadsListHandle in calling context.");
  return this->suspend_resume_manager()->resume(register_vthread_SR);
}

// Wait for another thread to perform object reallocation and relocking on behalf of
// this thread. The current thread is required to change to _thread_blocked in order
// to be seen to be safepoint/handshake safe whilst suspended and only after becoming
// handshake safe, the other thread can complete the handshake used to synchronize
// with this thread and then perform the reallocation and relocking.
// See EscapeBarrier::sync_and_suspend_*()

void JavaThread::wait_for_object_deoptimization() {
  assert(!has_last_Java_frame() || frame_anchor()->walkable(), "should have walkable stack");
  assert(this == Thread::current(), "invariant");

  bool spin_wait = os::is_MP();
  do {
    ThreadBlockInVM tbivm(this, true /* allow_suspend */);
    // Wait for object deoptimization if requested.
    if (spin_wait) {
      // A single deoptimization is typically very short. Microbenchmarks
      // showed 5% better performance when spinning.
      const uint spin_limit = 10 * SpinYield::default_spin_limit;
      SpinYield spin(spin_limit);
      for (uint i = 0; is_obj_deopt_suspend() && i < spin_limit; i++) {
        spin.wait();
      }
      // Spin just once
      spin_wait = false;
    } else {
      MonitorLocker ml(this, EscapeBarrier_lock, Monitor::_no_safepoint_check_flag);
      if (is_obj_deopt_suspend()) {
        ml.wait();
      }
    }
    // A handshake for obj. deoptimization suspend could have been processed so
    // we must check after processing.
  } while (is_obj_deopt_suspend());
}

#ifdef ASSERT
// Verify the JavaThread has not yet been published in the Threads::list, and
// hence doesn't need protection from concurrent access at this stage.
void JavaThread::verify_not_published() {
  // Cannot create a ThreadsListHandle here and check !tlh.includes(this)
  // since an unpublished JavaThread doesn't participate in the
  // Thread-SMR protocol for keeping a ThreadsList alive.
  assert(!on_thread_list(), "JavaThread shouldn't have been published yet!");
}
#endif

// Slow path when the native==>Java barriers detect a safepoint/handshake is
// pending, when _suspend_flags is non-zero or when we need to process a stack
// watermark. Also check for pending async exceptions (except unsafe access error).
// Note only the native==>Java barriers can call this function when thread state
// is _thread_in_native_trans.
void JavaThread::check_special_condition_for_native_trans(JavaThread *thread) {
  assert(thread->thread_state() == _thread_in_native_trans, "wrong state");
  assert(!thread->has_last_Java_frame() || thread->frame_anchor()->walkable(), "Unwalkable stack in native->Java transition");

  thread->set_thread_state(_thread_in_vm);

  // Enable WXWrite: called directly from interpreter native wrapper.
  MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXWrite, thread));

  SafepointMechanism::process_if_requested_with_exit_check(thread, true /* check asyncs */);

  // After returning from native, it could be that the stack frames are not
  // yet safe to use. We catch such situations in the subsequent stack watermark
  // barrier, which will trap unsafe stack frames.
  StackWatermarkSet::before_unwind(thread);
}

#ifndef PRODUCT
// Deoptimization
// Function for testing deoptimization
void JavaThread::deoptimize() {
  StackFrameStream fst(this, false /* update */, true /* process_frames */);
  bool deopt = false;           // Dump stack only if a deopt actually happens.
  bool only_at = strlen(DeoptimizeOnlyAt) > 0;
  // Iterate over all frames in the thread and deoptimize
  for (; !fst.is_done(); fst.next()) {
    if (fst.current()->can_be_deoptimized()) {

      if (only_at) {
        // Deoptimize only at particular bcis.  DeoptimizeOnlyAt
        // consists of comma or carriage return separated numbers so
        // search for the current bci in that string.
        address    pc = fst.current()->pc();
        nmethod*   nm = fst.current()->cb()->as_nmethod();
        ScopeDesc* sd = nm->scope_desc_at(pc);
        char buffer[8];
        jio_snprintf(buffer, sizeof(buffer), "%d", sd->bci());
        size_t len = strlen(buffer);
        const char * found = strstr(DeoptimizeOnlyAt, buffer);
        while (found != nullptr) {
          if ((found[len] == ',' || found[len] == '\n' || found[len] == '\0') &&
              (found == DeoptimizeOnlyAt || found[-1] == ',' || found[-1] == '\n')) {
            // Check that the bci found is bracketed by terminators.
            break;
          }
          found = strstr(found + 1, buffer);
        }
        if (!found) {
          continue;
        }
      }

      if (DebugDeoptimization && !deopt) {
        deopt = true; // One-time only print before deopt
        tty->print_cr("[BEFORE Deoptimization]");
        trace_frames();
        trace_stack();
      }
      Deoptimization::deoptimize(this, *fst.current());
    }
  }

  if (DebugDeoptimization && deopt) {
    tty->print_cr("[AFTER Deoptimization]");
    trace_frames();
  }
}


// Make zombies
void JavaThread::make_zombies() {
  for (StackFrameStream fst(this, true /* update */, true /* process_frames */); !fst.is_done(); fst.next()) {
    if (fst.current()->can_be_deoptimized()) {
      // it is a Java nmethod
      nmethod* nm = CodeCache::find_nmethod(fst.current()->pc());
      assert(nm != nullptr, "did not find nmethod");
      nm->make_not_entrant(nmethod::InvalidationReason::ZOMBIE);
    }
  }
}
#endif // PRODUCT


void JavaThread::deoptimize_marked_methods() {
  if (!has_last_Java_frame()) return;
  StackFrameStream fst(this, false /* update */, true /* process_frames */);
  for (; !fst.is_done(); fst.next()) {
    if (fst.current()->should_be_deoptimized()) {
      Deoptimization::deoptimize(this, *fst.current());
    }
  }
}

#ifdef ASSERT
void JavaThread::verify_frame_info() {
  assert((!has_last_Java_frame() && java_call_counter() == 0) ||
         (has_last_Java_frame() && java_call_counter() > 0),
         "unexpected frame info: has_last_frame=%s, java_call_counter=%d",
         has_last_Java_frame() ? "true" : "false", java_call_counter());
}
#endif

// Push on a new block of JNI handles.
void JavaThread::push_jni_handle_block() {
  // Allocate a new block for JNI handles.
  // Inlined code from jni_PushLocalFrame()
  JNIHandleBlock* old_handles = active_handles();
  JNIHandleBlock* new_handles = JNIHandleBlock::allocate_block(this);
  assert(old_handles != nullptr && new_handles != nullptr, "should not be null");
  new_handles->set_pop_frame_link(old_handles);  // make sure java handles get gc'd.
  set_active_handles(new_handles);
}

// Pop off the current block of JNI handles.
void JavaThread::pop_jni_handle_block() {
  // Release our JNI handle block
  JNIHandleBlock* old_handles = active_handles();
  JNIHandleBlock* new_handles = old_handles->pop_frame_link();
  assert(new_handles != nullptr, "should never set active handles to null");
  set_active_handles(new_handles);
  old_handles->set_pop_frame_link(nullptr);
  JNIHandleBlock::release_block(old_handles, this);
}

void JavaThread::oops_do_no_frames(OopClosure* f, NMethodClosure* cf) {
  // Traverse the GCHandles
  Thread::oops_do_no_frames(f, cf);

  if (active_handles() != nullptr) {
    active_handles()->oops_do(f);
  }

  DEBUG_ONLY(verify_frame_info();)

  assert(vframe_array_head() == nullptr, "deopt in progress at a safepoint!");
  // If we have deferred set_locals there might be oops waiting to be
  // written
  GrowableArray<jvmtiDeferredLocalVariableSet*>* list = JvmtiDeferredUpdates::deferred_locals(this);
  if (list != nullptr) {
    for (int i = 0; i < list->length(); i++) {
      list->at(i)->oops_do(f);
    }
  }

  // Traverse instance variables at the end since the GC may be moving things
  // around using this function
  f->do_oop((oop*) &_vm_result_oop);
  f->do_oop((oop*) &_exception_oop);
#if INCLUDE_JVMCI
  f->do_oop((oop*) &_jvmci_reserved_oop0);

  if (_live_nmethod != nullptr && cf != nullptr) {
    cf->do_nmethod(_live_nmethod);
  }
#endif

  if (jvmti_thread_state() != nullptr) {
    jvmti_thread_state()->oops_do(f, cf);
  }

  // The continuation oops are really on the stack. But there is typically at most
  // one of those per thread, so we handle them here in the oops_do_no_frames part
  // so that we don't have to sprinkle as many stack watermark checks where these
  // oops are used. We just need to make sure the thread has started processing.
  ContinuationEntry* entry = _cont_entry;
  while (entry != nullptr) {
    f->do_oop((oop*)entry->cont_addr());
    f->do_oop((oop*)entry->chunk_addr());
    entry = entry->parent();
  }

  // Due to fast locking
  lock_stack().oops_do(f);
}

void JavaThread::oops_do_frames(OopClosure* f, NMethodClosure* cf) {
  if (!has_last_Java_frame()) {
    return;
  }
  // Finish any pending lazy GC activity for the frames
  StackWatermarkSet::finish_processing(this, nullptr /* context */, StackWatermarkKind::gc);
  // Traverse the execution stack
  for (StackFrameStream fst(this, true /* update */, false /* process_frames */); !fst.is_done(); fst.next()) {
    fst.current()->oops_do(f, cf, fst.register_map());
  }
}

#ifdef ASSERT
void JavaThread::verify_states_for_handshake() {
  // This checks that the thread has a correct frame state during a handshake.
  verify_frame_info();
}
#endif

void JavaThread::nmethods_do(NMethodClosure* cf) {
  DEBUG_ONLY(verify_frame_info();)
  MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXWrite, Thread::current());)

  if (has_last_Java_frame()) {
    // Traverse the execution stack
    for (StackFrameStream fst(this, true /* update */, true /* process_frames */); !fst.is_done(); fst.next()) {
      fst.current()->nmethod_do(cf);
    }
  }

  if (jvmti_thread_state() != nullptr) {
    jvmti_thread_state()->nmethods_do(cf);
  }

#if INCLUDE_JVMCI
  if (_live_nmethod != nullptr) {
    cf->do_nmethod(_live_nmethod);
  }
#endif
}

void JavaThread::metadata_do(MetadataClosure* f) {
  if (has_last_Java_frame()) {
    // Traverse the execution stack to call f() on the methods in the stack
    for (StackFrameStream fst(this, true /* update */, true /* process_frames */); !fst.is_done(); fst.next()) {
      fst.current()->metadata_do(f);
    }
  } else if (is_Compiler_thread()) {
    // need to walk ciMetadata in current compile tasks to keep alive.
    CompilerThread* ct = (CompilerThread*)this;
    if (ct->env() != nullptr) {
      ct->env()->metadata_do(f);
    }
    CompileTask* task = ct->task();
    if (task != nullptr) {
      task->metadata_do(f);
    }
  }
}

// Printing
static const char* _get_thread_state_name(JavaThreadState _thread_state) {
  switch (_thread_state) {
  case _thread_uninitialized:     return "_thread_uninitialized";
  case _thread_new:               return "_thread_new";
  case _thread_new_trans:         return "_thread_new_trans";
  case _thread_in_native:         return "_thread_in_native";
  case _thread_in_native_trans:   return "_thread_in_native_trans";
  case _thread_in_vm:             return "_thread_in_vm";
  case _thread_in_vm_trans:       return "_thread_in_vm_trans";
  case _thread_in_Java:           return "_thread_in_Java";
  case _thread_in_Java_trans:     return "_thread_in_Java_trans";
  case _thread_blocked:           return "_thread_blocked";
  case _thread_blocked_trans:     return "_thread_blocked_trans";
  default:                        return "unknown thread state";
  }
}

void JavaThread::print_thread_state_on(outputStream *st) const {
  st->print_cr("   JavaThread state: %s", _get_thread_state_name(_thread_state));
}

// Called by Threads::print() for VM_PrintThreads operation
void JavaThread::print_on(outputStream *st, bool print_extended_info) const {
  st->print_raw("\"");
  st->print_raw(name());
  st->print_raw("\" ");
  oop thread_oop = threadObj();
  if (thread_oop != nullptr) {
    st->print("#" INT64_FORMAT " [%ld] ", (int64_t)java_lang_Thread::thread_id(thread_oop), (long) osthread()->thread_id());
    if (java_lang_Thread::is_daemon(thread_oop))  st->print("daemon ");
    st->print("prio=%d ", java_lang_Thread::priority(thread_oop));
  }
  Thread::print_on(st, print_extended_info);
  // print guess for valid stack memory region (assume 4K pages); helps lock debugging
  st->print_cr("[" INTPTR_FORMAT "]", (intptr_t)last_Java_sp() & ~right_n_bits(12));
  if (thread_oop != nullptr) {
    if (is_vthread_mounted()) {
      st->print_cr("   Carrying virtual thread #" INT64_FORMAT, java_lang_Thread::thread_id(vthread()));
    } else {
      st->print_cr("   java.lang.Thread.State: %s", java_lang_Thread::thread_status_name(thread_oop));
    }
  }
#ifndef PRODUCT
  _safepoint_state->print_on(st);
#endif // PRODUCT
  if (is_Compiler_thread()) {
    CompileTask *task = ((CompilerThread*)this)->task();
    if (task != nullptr) {
      st->print("   Compiling: ");
      task->print(st, nullptr, true, false);
    } else {
      st->print("   No compile task");
    }
    st->cr();
  }
}

void JavaThread::print() const { print_on(tty); }

void JavaThread::print_name_on_error(outputStream* st, char *buf, int buflen) const {
  st->print("%s", get_thread_name_string(buf, buflen));
}

// Called by fatal error handler. The difference between this and
// JavaThread::print() is that we can't grab lock or allocate memory.
void JavaThread::print_on_error(outputStream* st, char *buf, int buflen) const {
  st->print("%s \"%s\"", type_name(), get_thread_name_string(buf, buflen));
  Thread* current = Thread::current_or_null_safe();
  assert(current != nullptr, "cannot be called by a detached thread");
  st->fill_to(60);
  if (!current->is_Java_thread() || JavaThread::cast(current)->is_oop_safe()) {
    // Only access threadObj() if current thread is not a JavaThread
    // or if it is a JavaThread that can safely access oops.
    oop thread_obj = threadObj();
    if (thread_obj != nullptr) {
      st->print(java_lang_Thread::is_daemon(thread_obj) ? " daemon" : "       ");
    }
  }
  st->print(" [");
  st->print("%s", _get_thread_state_name(_thread_state));
  if (osthread()) {
    st->print(", id=%d", osthread()->thread_id());
  }
  // Use raw field members for stack base/size as this could be
  // called before a thread has run enough to initialize them.
  st->print(", stack(" PTR_FORMAT "," PTR_FORMAT ") (" PROPERFMT ")",
            p2i(_stack_base - _stack_size), p2i(_stack_base),
            PROPERFMTARGS(_stack_size));
  st->print("]");

  ThreadsSMRSupport::print_info_on(this, st);
  return;
}


// Verification

void JavaThread::frames_do(void f(frame*, const RegisterMap* map)) {
  // ignore if there is no stack
  if (!has_last_Java_frame()) return;
  // traverse the stack frames. Starts from top frame.
  for (StackFrameStream fst(this, true /* update_map */, true /* process_frames */, false /* walk_cont */); !fst.is_done(); fst.next()) {
    frame* fr = fst.current();
    f(fr, fst.register_map());
  }
}

static void frame_verify(frame* f, const RegisterMap *map) { f->verify(map); }

void JavaThread::verify() {
  // Verify oops in the thread.
  oops_do(&VerifyOopClosure::verify_oop, nullptr);

  // Verify the stack frames.
  frames_do(frame_verify);
}

// CR 6300358 (sub-CR 2137150)
// Most callers of this method assume that it can't return null but a
// thread may not have a name whilst it is in the process of attaching to
// the VM - see CR 6412693, and there are places where a JavaThread can be
// seen prior to having its threadObj set (e.g., JNI attaching threads and
// if vm exit occurs during initialization). These cases can all be accounted
// for such that this method never returns null.
const char* JavaThread::name() const  {
  if (Thread::is_JavaThread_protected(/* target */ this)) {
    // The target JavaThread is protected so get_thread_name_string() is safe:
    return get_thread_name_string();
  }

  // The target JavaThread is not protected so we return the default:
  return Thread::name();
}

// Like name() but doesn't include the protection check. This must only be
// called when it is known to be safe, even though the protection check can't tell
// that e.g. when this thread is the init_thread() - see instanceKlass.cpp.
const char* JavaThread::name_raw() const  {
  return get_thread_name_string();
}

// Returns a non-null representation of this thread's name, or a suitable
// descriptive string if there is no set name.
const char* JavaThread::get_thread_name_string(char* buf, int buflen) const {
  const char* name_str;
#ifdef ASSERT
  Thread* current = Thread::current_or_null_safe();
  assert(current != nullptr, "cannot be called by a detached thread");
  if (!current->is_Java_thread() || JavaThread::cast(current)->is_oop_safe()) {
    // Only access threadObj() if current thread is not a JavaThread
    // or if it is a JavaThread that can safely access oops.
#endif
    oop thread_obj = threadObj();
    if (thread_obj != nullptr) {
      oop name = java_lang_Thread::name(thread_obj);
      if (name != nullptr) {
        if (buf == nullptr) {
          name_str = java_lang_String::as_utf8_string(name);
        } else {
          name_str = java_lang_String::as_utf8_string(name, buf, buflen);
        }
      } else if (is_attaching_via_jni()) { // workaround for 6412693 - see 6404306
        name_str = "<no-name - thread is attaching>";
      } else {
        name_str = "<un-named>";
      }
    } else {
      name_str = Thread::name();
    }
#ifdef ASSERT
  } else {
    // Current JavaThread has exited...
    if (current == this) {
      // ... and is asking about itself:
      name_str = "<no-name - current JavaThread has exited>";
    } else {
      // ... and it can't safely determine this JavaThread's name so
      // use the default thread name.
      name_str = Thread::name();
    }
  }
#endif
  assert(name_str != nullptr, "unexpected null thread name");
  return name_str;
}

// Helper to extract the name from the thread oop for logging.
const char* JavaThread::name_for(oop thread_obj) {
  assert(thread_obj != nullptr, "precondition");
  oop name = java_lang_Thread::name(thread_obj);
  const char* name_str;
  if (name != nullptr) {
    name_str = java_lang_String::as_utf8_string(name);
  } else {
    name_str = "<un-named>";
  }
  return name_str;
}

void JavaThread::prepare(jobject jni_thread, ThreadPriority prio) {

  assert(Threads_lock->owner() == Thread::current(), "must have threads lock");
  assert(NoPriority <= prio && prio <= MaxPriority, "sanity check");
  // Link Java Thread object <-> C++ Thread

  // Get the C++ thread object (an oop) from the JNI handle (a jthread)
  // and put it into a new Handle.  The Handle "thread_oop" can then
  // be used to pass the C++ thread object to other methods.

  // Set the Java level thread object (jthread) field of the
  // new thread (a JavaThread *) to C++ thread object using the
  // "thread_oop" handle.

  // Set the thread field (a JavaThread *) of the
  // oop representing the java_lang_Thread to the new thread (a JavaThread *).

  Handle thread_oop(Thread::current(),
                    JNIHandles::resolve_non_null(jni_thread));
  assert(InstanceKlass::cast(thread_oop->klass())->is_linked(),
         "must be initialized");
  set_threadOopHandles(thread_oop());
  set_monitor_owner_id(java_lang_Thread::thread_id(thread_oop()));

  if (prio == NoPriority) {
    prio = java_lang_Thread::priority(thread_oop());
    assert(prio != NoPriority, "A valid priority should be present");
  }

  // Push the Java priority down to the native thread; needs Threads_lock
  Thread::set_priority(this, prio);

  // Add the new thread to the Threads list and set it in motion.
  // We must have threads lock in order to call Threads::add.
  // It is crucial that we do not block before the thread is
  // added to the Threads list for if a GC happens, then the java_thread oop
  // will not be visited by GC.
  Threads::add(this);
  // Publish the JavaThread* in java.lang.Thread after the JavaThread* is
  // on a ThreadsList. We don't want to wait for the release when the
  // Theads_lock is dropped somewhere in the caller since the JavaThread*
  // is already visible to JVM/TI via the ThreadsList.
  java_lang_Thread::release_set_thread(thread_oop(), this);
}

oop JavaThread::current_park_blocker() {
  // Support for JSR-166 locks
  oop thread_oop = threadObj();
  if (thread_oop != nullptr) {
    return java_lang_Thread::park_blocker(thread_oop);
  }
  return nullptr;
}

// Print current stack trace for checked JNI warnings and JNI fatal errors.
// This is the external format, selecting the platform or vthread
// as applicable, and allowing for a native-only stack.
void JavaThread::print_jni_stack() {
  assert(this == JavaThread::current(), "Can't print stack of other threads");
  if (!has_last_Java_frame()) {
    ResourceMark rm(this);
    char* buf = NEW_RESOURCE_ARRAY_RETURN_NULL(char, O_BUFLEN);
    if (buf == nullptr) {
      tty->print_cr("Unable to print native stack - out of memory");
      return;
    }
    NativeStackPrinter nsp(this);
    address lastpc = nullptr;
    nsp.print_stack(tty, buf, O_BUFLEN, lastpc,
                    true /*print_source_info */, -1 /* max stack */ );
  } else {
    print_active_stack_on(tty);
  }
}

void JavaThread::print_stack_on(outputStream* st) {
  if (!has_last_Java_frame()) return;

  Thread* current_thread = Thread::current();
  ResourceMark rm(current_thread);
  HandleMark hm(current_thread);

  RegisterMap reg_map(this,
                      RegisterMap::UpdateMap::include,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::skip);
  vframe* start_vf = platform_thread_last_java_vframe(&reg_map);
  int count = 0;
  for (vframe* f = start_vf; f != nullptr; f = f->sender()) {
    if (f->is_java_frame()) {
      javaVFrame* jvf = javaVFrame::cast(f);
      java_lang_Throwable::print_stack_element(st, jvf->method(), jvf->bci());

      // Print out lock information
      if (JavaMonitorsInStackTrace) {
        jvf->print_lock_info_on(st, false/*is_virtual*/, count);
      }
    } else {
      // Ignore non-Java frames
    }

    // Bail-out case for too deep stacks if MaxJavaStackTraceDepth > 0
    count++;
    if (MaxJavaStackTraceDepth > 0 && MaxJavaStackTraceDepth == count) return;
  }
}

void JavaThread::print_vthread_stack_on(outputStream* st) {
  assert(is_vthread_mounted(), "Caller should have checked this");
  assert(has_last_Java_frame(), "must be");

  Thread* current_thread = Thread::current();
  ResourceMark rm(current_thread);
  HandleMark hm(current_thread);

  RegisterMap reg_map(this,
                      RegisterMap::UpdateMap::include,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::include);
  ContinuationEntry* cont_entry = last_continuation();
  vframe* start_vf = last_java_vframe(&reg_map);
  int count = 0;
  for (vframe* f = start_vf; f != nullptr; f = f->sender()) {
    // Watch for end of vthread stack
    if (Continuation::is_continuation_enterSpecial(f->fr())) {
      assert(cont_entry == Continuation::get_continuation_entry_for_entry_frame(this, f->fr()), "");
      if (cont_entry->is_virtual_thread()) {
        break;
      }
      cont_entry = cont_entry->parent();
    }
    if (f->is_java_frame()) {
      javaVFrame* jvf = javaVFrame::cast(f);
      java_lang_Throwable::print_stack_element(st, jvf->method(), jvf->bci());

      // Print out lock information
      if (JavaMonitorsInStackTrace) {
        jvf->print_lock_info_on(st, true/*is_virtual*/, count);
      }
    } else {
      // Ignore non-Java frames
    }

    // Bail-out case for too deep stacks if MaxJavaStackTraceDepth > 0
    count++;
    if (MaxJavaStackTraceDepth > 0 && MaxJavaStackTraceDepth == count) return;
  }
}

void JavaThread::print_active_stack_on(outputStream* st) {
  if (is_vthread_mounted()) {
    print_vthread_stack_on(st);
  } else {
    print_stack_on(st);
  }
}

#if INCLUDE_JVMTI
// Rebind JVMTI thread state from carrier to virtual or from virtual to carrier.
JvmtiThreadState* JavaThread::rebind_to_jvmti_thread_state_of(oop thread_oop) {
  set_jvmti_vthread(thread_oop);

  // unbind current JvmtiThreadState from JavaThread
  JvmtiThreadState::unbind_from(jvmti_thread_state(), this);

  // bind new JvmtiThreadState to JavaThread
  JvmtiThreadState::bind_to(java_lang_Thread::jvmti_thread_state(thread_oop), this);

  // enable interp_only_mode for virtual or carrier thread if it has pending bit
  JvmtiThreadState::process_pending_interp_only(this);

  return jvmti_thread_state();
}
#endif

// JVMTI PopFrame support
void JavaThread::popframe_preserve_args(ByteSize size_in_bytes, void* start) {
  assert(_popframe_preserved_args == nullptr, "should not wipe out old PopFrame preserved arguments");
  if (in_bytes(size_in_bytes) != 0) {
    _popframe_preserved_args = NEW_C_HEAP_ARRAY(char, in_bytes(size_in_bytes), mtThread);
    _popframe_preserved_args_size = in_bytes(size_in_bytes);
    Copy::conjoint_jbytes(start, _popframe_preserved_args, _popframe_preserved_args_size);
  }
}

void* JavaThread::popframe_preserved_args() {
  return _popframe_preserved_args;
}

ByteSize JavaThread::popframe_preserved_args_size() {
  return in_ByteSize(_popframe_preserved_args_size);
}

WordSize JavaThread::popframe_preserved_args_size_in_words() {
  int sz = in_bytes(popframe_preserved_args_size());
  assert(sz % wordSize == 0, "argument size must be multiple of wordSize");
  return in_WordSize(sz / wordSize);
}

void JavaThread::popframe_free_preserved_args() {
  assert(_popframe_preserved_args != nullptr, "should not free PopFrame preserved arguments twice");
  FREE_C_HEAP_ARRAY(char, (char*)_popframe_preserved_args);
  _popframe_preserved_args = nullptr;
  _popframe_preserved_args_size = 0;
}

#ifndef PRODUCT

void JavaThread::trace_frames() {
  tty->print_cr("[Describe stack]");
  int frame_no = 1;
  for (StackFrameStream fst(this, true /* update */, true /* process_frames */); !fst.is_done(); fst.next()) {
    tty->print("  %d. ", frame_no++);
    fst.current()->print_value_on(tty);
    tty->cr();
  }
}

class PrintAndVerifyOopClosure: public OopClosure {
 protected:
  template <class T> inline void do_oop_work(T* p) {
    oop obj = RawAccess<>::oop_load(p);
    if (obj == nullptr) return;
    tty->print(INTPTR_FORMAT ": ", p2i(p));
    if (oopDesc::is_oop_or_null(obj)) {
      if (obj->is_objArray()) {
        tty->print_cr("valid objArray: " INTPTR_FORMAT, p2i(obj));
      } else {
        obj->print();
      }
    } else {
      tty->print_cr("invalid oop: " INTPTR_FORMAT, p2i(obj));
    }
    tty->cr();
  }
 public:
  virtual void do_oop(oop* p) { do_oop_work(p); }
  virtual void do_oop(narrowOop* p)  { do_oop_work(p); }
};

#ifdef ASSERT
// Print or validate the layout of stack frames
void JavaThread::print_frame_layout(int depth, bool validate_only) {
  ResourceMark rm;
  PreserveExceptionMark pm(this);
  FrameValues values;
  int frame_no = 0;
  for (StackFrameStream fst(this, true, true, true); !fst.is_done(); fst.next()) {
    fst.current()->describe(values, ++frame_no, fst.register_map());
    if (depth == frame_no) break;
  }
  Continuation::describe(values);
  if (validate_only) {
    values.validate();
  } else {
    tty->print_cr("[Describe stack layout]");
    values.print(this);
  }
}
#endif

void JavaThread::trace_stack_from(vframe* start_vf) {
  ResourceMark rm;
  int vframe_no = 1;
  for (vframe* f = start_vf; f; f = f->sender()) {
    if (f->is_java_frame()) {
      javaVFrame::cast(f)->print_activation(vframe_no++);
    } else {
      f->print();
    }
    if (vframe_no > StackPrintLimit) {
      tty->print_cr("...<more frames>...");
      return;
    }
  }
}


void JavaThread::trace_stack() {
  if (!has_last_Java_frame()) return;
  Thread* current_thread = Thread::current();
  ResourceMark rm(current_thread);
  HandleMark hm(current_thread);
  RegisterMap reg_map(this,
                      RegisterMap::UpdateMap::include,
                      RegisterMap::ProcessFrames::include,
                      RegisterMap::WalkContinuation::skip);
  trace_stack_from(last_java_vframe(&reg_map));
}


#endif // PRODUCT

frame JavaThread::vthread_last_frame() {
  assert (is_vthread_mounted(), "Virtual thread not mounted");
  return last_frame();
}

frame JavaThread::carrier_last_frame(RegisterMap* reg_map) {
  const ContinuationEntry* entry = vthread_continuation();
  guarantee (entry != nullptr, "Not a carrier thread");
  frame f = entry->to_frame();
  if (reg_map->process_frames()) {
    entry->flush_stack_processing(this);
  }
  entry->update_register_map(reg_map);
  return f.sender(reg_map);
}

frame JavaThread::platform_thread_last_frame(RegisterMap* reg_map) {
  return is_vthread_mounted() ? carrier_last_frame(reg_map) : last_frame();
}

javaVFrame* JavaThread::last_java_vframe(const frame f, RegisterMap *reg_map) {
  assert(reg_map != nullptr, "a map must be given");
  for (vframe* vf = vframe::new_vframe(&f, reg_map, this); vf; vf = vf->sender()) {
    if (vf->is_java_frame()) return javaVFrame::cast(vf);
  }
  return nullptr;
}

Klass* JavaThread::security_get_caller_class(int depth) {
  ResetNoHandleMark rnhm;
  HandleMark hm(Thread::current());

  vframeStream vfst(this);
  vfst.security_get_caller_frame(depth);
  if (!vfst.at_end()) {
    return vfst.method()->method_holder();
  }
  return nullptr;
}

// Internal convenience function for millisecond resolution sleeps.
bool JavaThread::sleep(jlong millis) {
  jlong nanos;
  if (millis > max_jlong / NANOUNITS_PER_MILLIUNIT) {
    // Conversion to nanos would overflow, saturate at max
    nanos = max_jlong;
  } else {
    nanos = millis * NANOUNITS_PER_MILLIUNIT;
  }
  return sleep_nanos(nanos);
}

// java.lang.Thread.sleep support
// Returns true if sleep time elapsed as expected, and false
// if the thread was interrupted or async exception was installed.
bool JavaThread::sleep_nanos(jlong nanos) {
  assert(this == Thread::current(),  "thread consistency check");
  assert(nanos >= 0, "nanos are in range");

  ParkEvent * const slp = this->_SleepEvent;
  // Because there can be races with thread interruption sending an unpark()
  // to the event, we explicitly reset it here to avoid an immediate return.
  // The actual interrupt state will be checked before we park().
  slp->reset();
  // Thread interruption establishes a happens-before ordering in the
  // Java Memory Model, so we need to ensure we synchronize with the
  // interrupt state.
  OrderAccess::fence();

  jlong prevtime = os::javaTimeNanos();

  jlong nanos_remaining = nanos;

  for (;;) {
    if (has_async_exception_condition()) {
      return false;
    }
    // interruption has precedence over timing out
    if (this->is_interrupted(true)) {
      return false;
    }

    if (nanos_remaining <= 0) {
      return true;
    }

    {
      ThreadBlockInVM tbivm(this);
      OSThreadWaitState osts(this->osthread(), false /* not Object.wait() */);
      slp->park_nanos(nanos_remaining);
    }

    // Update elapsed time tracking
    jlong newtime = os::javaTimeNanos();
    if (newtime - prevtime < 0) {
      // time moving backwards, should only happen if no monotonic clock
      // not a guarantee() because JVM should not abort on kernel/glibc bugs
      assert(false,
             "unexpected time moving backwards detected in JavaThread::sleep()");
    } else {
      nanos_remaining -= (newtime - prevtime);
    }
    prevtime = newtime;
  }
}

// Last thread running calls java.lang.Shutdown.shutdown()
void JavaThread::invoke_shutdown_hooks() {
  HandleMark hm(this);

  // We could get here with a pending exception, if so clear it now.
  if (this->has_pending_exception()) {
    this->clear_pending_exception();
  }

  EXCEPTION_MARK;
  Klass* shutdown_klass =
    SystemDictionary::resolve_or_null(vmSymbols::java_lang_Shutdown(),
                                      THREAD);
  if (shutdown_klass != nullptr) {
    // SystemDictionary::resolve_or_null will return null if there was
    // an exception.  If we cannot load the Shutdown class, just don't
    // call Shutdown.shutdown() at all.  This will mean the shutdown hooks
    // won't be run.  Note that if a shutdown hook was registered,
    // the Shutdown class would have already been loaded
    // (Runtime.addShutdownHook will load it).
    JavaValue result(T_VOID);
    JavaCalls::call_static(&result,
                           shutdown_klass,
                           vmSymbols::shutdown_name(),
                           vmSymbols::void_method_signature(),
                           THREAD);
  }
  CLEAR_PENDING_EXCEPTION;
}

#ifndef PRODUCT
void JavaThread::verify_cross_modify_fence_failure(JavaThread *thread) {
   report_vm_error(__FILE__, __LINE__, "Cross modify fence failure", "%p", thread);
}
#endif

// Helper function to create the java.lang.Thread object for a
// VM-internal thread. The thread will have the given name, and be
// a member of the "system" ThreadGroup.
Handle JavaThread::create_system_thread_object(const char* name, TRAPS) {
  Handle string = java_lang_String::create_from_str(name, CHECK_NH);

  // Initialize thread_oop to put it into the system threadGroup.
  // This is done by calling the Thread(ThreadGroup group, String name) constructor.
  Handle thread_group(THREAD, Universe::system_thread_group());
  Handle thread_oop =
    JavaCalls::construct_new_instance(vmClasses::Thread_klass(),
                                      vmSymbols::threadgroup_string_void_signature(),
                                      thread_group,
                                      string,
                                      CHECK_NH);

  return thread_oop;
}

// Starts the target JavaThread as a daemon of the given priority, and
// bound to the given java.lang.Thread instance.
// The Threads_lock is held for the duration.
void JavaThread::start_internal_daemon(JavaThread* current, JavaThread* target,
                                       Handle thread_oop, ThreadPriority prio) {

  assert(target->osthread() != nullptr, "target thread is not properly initialized");

  MutexLocker mu(current, Threads_lock);

  // Initialize the fields of the thread_oop first.
  if (prio != NoPriority) {
    java_lang_Thread::set_priority(thread_oop(), prio);
    // Note: we don't call os::set_priority here. Possibly we should,
    // else all threads should call it themselves when they first run.
  }

  java_lang_Thread::set_daemon(thread_oop());

  // Now bind the thread_oop to the target JavaThread.
  target->set_threadOopHandles(thread_oop());
  target->set_monitor_owner_id(java_lang_Thread::thread_id(thread_oop()));

  Threads::add(target); // target is now visible for safepoint/handshake
  // Publish the JavaThread* in java.lang.Thread after the JavaThread* is
  // on a ThreadsList. We don't want to wait for the release when the
  // Theads_lock is dropped when the 'mu' destructor is run since the
  // JavaThread* is already visible to JVM/TI via the ThreadsList.

  assert(java_lang_Thread::thread(thread_oop()) == nullptr, "must not be alive");
  java_lang_Thread::release_set_thread(thread_oop(), target); // isAlive == true now
  Thread::start(target);
}

void JavaThread::vm_exit_on_osthread_failure(JavaThread* thread) {
  // At this point it may be possible that no osthread was created for the
  // JavaThread due to lack of resources. However, since this must work
  // for critical system threads just check and abort if this fails.
  if (thread->osthread() == nullptr) {
    // This isn't really an OOM condition, but historically this is what
    // we report.
    vm_exit_during_initialization("java.lang.OutOfMemoryError",
                                  os::native_thread_creation_failed_msg());
  }
}

void JavaThread::pretouch_stack() {
  // Given an established java thread stack with usable area followed by
  // shadow zone and reserved/yellow/red zone, pretouch the usable area ranging
  // from the current frame down to the start of the shadow zone.
  const address end = _stack_overflow_state.shadow_zone_safe_limit();
  if (is_in_full_stack(end)) {
    char* p1 = (char*) alloca(1);
    address here = (address) &p1;
    if (is_in_full_stack(here) && here > end) {
      size_t to_alloc = here - end;
      char* p2 = (char*) alloca(to_alloc);
      log_trace(os, thread)("Pretouching thread stack for %zu: " RANGEFMT ".",
                            (uintx) osthread()->thread_id(), RANGEFMTARGS(p2, to_alloc));
      os::pretouch_memory(p2, p2 + to_alloc,
                          NOT_AIX(os::vm_page_size()) AIX_ONLY(4096));
    }
  }
}

// Deferred OopHandle release support.

class OopHandleList : public CHeapObj<mtInternal> {
  static const int _count = 4;
  OopHandle _handles[_count];
  OopHandleList* _next;
  int _index;
 public:
  OopHandleList(OopHandleList* next) : _next(next), _index(0) {}
  void add(OopHandle h) {
    assert(_index < _count, "too many additions");
    _handles[_index++] = h;
  }
  ~OopHandleList() {
    assert(_index == _count, "usage error");
    for (int i = 0; i < _index; i++) {
      _handles[i].release(JavaThread::thread_oop_storage());
    }
  }
  OopHandleList* next() const { return _next; }
};

OopHandleList* JavaThread::_oop_handle_list = nullptr;

// Called by the ServiceThread to do the work of releasing
// the OopHandles.
void JavaThread::release_oop_handles() {
  OopHandleList* list;
  {
    MutexLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
    list = _oop_handle_list;
    _oop_handle_list = nullptr;
  }
  assert(!SafepointSynchronize::is_at_safepoint(), "cannot be called at a safepoint");

  while (list != nullptr) {
    OopHandleList* l = list;
    list = l->next();
    delete l;
  }
}

// Add our OopHandles for later release.
void JavaThread::add_oop_handles_for_release() {
  MutexLocker ml(Service_lock, Mutex::_no_safepoint_check_flag);
  OopHandleList* new_head = new OopHandleList(_oop_handle_list);
  new_head->add(_threadObj);
  new_head->add(_vthread);
  new_head->add(_jvmti_vthread);
  new_head->add(_scopedValueCache);
  _oop_handle_list = new_head;
  Service_lock->notify_all();
}

#if INCLUDE_JFR
void JavaThread::set_last_freeze_fail_result(freeze_result result) {
  assert(result != freeze_ok, "sanity check");
  _last_freeze_fail_result = result;
  _last_freeze_fail_time = Ticks::now();
}

// Post jdk.VirtualThreadPinned event
void JavaThread::post_vthread_pinned_event(EventVirtualThreadPinned* event, const char* op, freeze_result result) {
  assert(result != freeze_ok, "sanity check");
  if (event->should_commit()) {
    char reason[256];
    if (class_to_be_initialized() != nullptr) {
      ResourceMark rm(this);
      jio_snprintf(reason, sizeof reason, "Waited for initialization of %s by another thread",
                   class_to_be_initialized()->external_name());
      event->set_pinnedReason(reason);
    } else if (class_being_initialized() != nullptr) {
      ResourceMark rm(this);
      jio_snprintf(reason, sizeof(reason), "VM call to %s.<clinit> on stack",
                   class_being_initialized()->external_name());
      event->set_pinnedReason(reason);
    } else if (result == freeze_pinned_native) {
      event->set_pinnedReason("Native or VM frame on stack");
    } else {
      jio_snprintf(reason, sizeof(reason), "Freeze or preempt failed (%d)", result);
      event->set_pinnedReason(reason);
    }
    event->set_blockingOperation(op);
    event->set_carrierThread(JFR_JVM_THREAD_ID(this));
    event->commit();
  }
}
#endif

/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_JAVATHREAD_HPP
#define SHARE_RUNTIME_JAVATHREAD_HPP

#include "jni.h"
#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "oops/oopHandle.hpp"
#include "runtime/frame.hpp"
#include "runtime/globals.hpp"
#include "runtime/handshake.hpp"
#include "runtime/javaFrameAnchor.hpp"
#include "runtime/lockStack.hpp"
#include "runtime/park.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/stackWatermarkSet.hpp"
#include "runtime/stackOverflow.hpp"
#include "runtime/thread.hpp"
#include "runtime/threadHeapSampler.hpp"
#include "runtime/threadStatisticalInfo.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_JFR
#include "jfr/support/jfrThreadExtension.hpp"
#endif

class AsyncExceptionHandshake;
class ContinuationEntry;
class DeoptResourceMark;
class JNIHandleBlock;
class JVMCIRuntime;

class JvmtiDeferredUpdates;
class JvmtiSampledObjectAllocEventCollector;
class JvmtiThreadState;

class Metadata;
class OopHandleList;
class OopStorage;
class OSThread;

class ThreadsList;
class ThreadSafepointState;
class ThreadStatistics;

class vframeArray;
class vframe;
class javaVFrame;

class JavaThread;
typedef void (*ThreadFunction)(JavaThread*, TRAPS);

class JavaThread: public Thread {
  friend class VMStructs;
  friend class JVMCIVMStructs;
  friend class WhiteBox;
  friend class ThreadsSMRSupport; // to access _threadObj for exiting_threads_oops_do
  friend class HandshakeState;
  friend class Continuation;
  friend class Threads;
  friend class ServiceThread; // for deferred OopHandle release access
 private:
  bool           _on_thread_list;                // Is set when this JavaThread is added to the Threads list

  // All references to Java objects managed via OopHandles. These
  // have to be released by the ServiceThread after the JavaThread has
  // terminated - see add_oop_handles_for_release().
  OopHandle      _threadObj;                     // The Java level thread object
  OopHandle      _vthread; // the value returned by Thread.currentThread(): the virtual thread, if mounted, otherwise _threadObj
  OopHandle      _jvmti_vthread;
  OopHandle      _scopedValueCache;

  static OopStorage* _thread_oop_storage;

#ifdef ASSERT
 private:
  int _java_call_counter;

 public:
  int  java_call_counter()                       { return _java_call_counter; }
  void inc_java_call_counter()                   { _java_call_counter++; }
  void dec_java_call_counter() {
    assert(_java_call_counter > 0, "Invalid nesting of JavaCallWrapper");
    _java_call_counter--;
  }
 private:  // restore original namespace restriction
#endif  // ifdef ASSERT

  JavaFrameAnchor _anchor;                       // Encapsulation of current java frame and it state

  ThreadFunction _entry_point;

  JNIEnv        _jni_environment;

  // Deopt support
  DeoptResourceMark*  _deopt_mark;               // Holds special ResourceMark for deoptimization

  CompiledMethod*       _deopt_nmethod;         // CompiledMethod that is currently being deoptimized
  vframeArray*  _vframe_array_head;              // Holds the heap of the active vframeArrays
  vframeArray*  _vframe_array_last;              // Holds last vFrameArray we popped
  // Holds updates by JVMTI agents for compiled frames that cannot be performed immediately. They
  // will be carried out as soon as possible which, in most cases, is just before deoptimization of
  // the frame, when control returns to it.
  JvmtiDeferredUpdates* _jvmti_deferred_updates;

  // Handshake value for fixing 6243940. We need a place for the i2c
  // adapter to store the callee Method*. This value is NEVER live
  // across a gc point so it does NOT have to be gc'd
  // The handshake is open ended since we can't be certain that it will
  // be nulled. This is because we rarely ever see the race and end up
  // in handle_wrong_method which is the backend of the handshake. See
  // code in i2c adapters and handle_wrong_method.

  Method*       _callee_target;

  // Used to pass back results to the interpreter or generated code running Java code.
  oop           _vm_result;    // oop result is GC-preserved
  Metadata*     _vm_result_2;  // non-oop result

  // See ReduceInitialCardMarks: this holds the precise space interval of
  // the most recent slow path allocation for which compiled code has
  // elided card-marks for performance along the fast-path.
  MemRegion     _deferred_card_mark;

  ObjectMonitor* volatile _current_pending_monitor;     // ObjectMonitor this thread is waiting to lock
  bool           _current_pending_monitor_is_from_java; // locking is from Java code
  ObjectMonitor* volatile _current_waiting_monitor;     // ObjectMonitor on which this thread called Object.wait()

  // Active_handles points to a block of handles
  JNIHandleBlock* _active_handles;

  // One-element thread local free list
  JNIHandleBlock* _free_handle_block;

 public:
  volatile intptr_t _Stalled;

  // For tracking the heavyweight monitor the thread is pending on.
  ObjectMonitor* current_pending_monitor() {
    // Use Atomic::load() to prevent data race between concurrent modification and
    // concurrent readers, e.g. ThreadService::get_current_contended_monitor().
    // Especially, reloading pointer from thread after null check must be prevented.
    return Atomic::load(&_current_pending_monitor);
  }
  void set_current_pending_monitor(ObjectMonitor* monitor) {
    Atomic::store(&_current_pending_monitor, monitor);
  }
  void set_current_pending_monitor_is_from_java(bool from_java) {
    _current_pending_monitor_is_from_java = from_java;
  }
  bool current_pending_monitor_is_from_java() {
    return _current_pending_monitor_is_from_java;
  }
  ObjectMonitor* current_waiting_monitor() {
    // See the comment in current_pending_monitor() above.
    return Atomic::load(&_current_waiting_monitor);
  }
  void set_current_waiting_monitor(ObjectMonitor* monitor) {
    Atomic::store(&_current_waiting_monitor, monitor);
  }

  // JNI handle support
  JNIHandleBlock* active_handles() const         { return _active_handles; }
  void set_active_handles(JNIHandleBlock* block) { _active_handles = block; }
  JNIHandleBlock* free_handle_block() const      { return _free_handle_block; }
  void set_free_handle_block(JNIHandleBlock* block) { _free_handle_block = block; }

  void push_jni_handle_block();
  void pop_jni_handle_block();

 private:
  MonitorChunk* _monitor_chunks;              // Contains the off stack monitors
                                              // allocated during deoptimization
                                              // and by JNI_MonitorEnter/Exit

  enum SuspendFlags {
    // NOTE: avoid using the sign-bit as cc generates different test code
    //       when the sign-bit is used, and sometimes incorrectly - see CR 6398077
    _trace_flag             = 0x00000004U, // call tracing backend
    _obj_deopt              = 0x00000008U  // suspend for object reallocation and relocking for JVMTI agent
  };

  // various suspension related flags - atomically updated
  volatile uint32_t _suspend_flags;

  inline void set_suspend_flag(SuspendFlags f);
  inline void clear_suspend_flag(SuspendFlags f);

 public:
  inline void set_trace_flag();
  inline void clear_trace_flag();
  inline void set_obj_deopt_flag();
  inline void clear_obj_deopt_flag();
  bool is_trace_suspend()      { return (_suspend_flags & _trace_flag) != 0; }
  bool is_obj_deopt_suspend()  { return (_suspend_flags & _obj_deopt) != 0; }

  // Asynchronous exception support
 private:
  friend class InstallAsyncExceptionHandshake;
  friend class AsyncExceptionHandshake;
  friend class HandshakeState;

  void handle_async_exception(oop java_throwable);
 public:
  void install_async_exception(AsyncExceptionHandshake* aec = nullptr);
  bool has_async_exception_condition();
  inline void set_pending_unsafe_access_error();
  static void send_async_exception(JavaThread* jt, oop java_throwable);

  class NoAsyncExceptionDeliveryMark : public StackObj {
    friend JavaThread;
    JavaThread *_target;
    inline NoAsyncExceptionDeliveryMark(JavaThread *t);
    inline ~NoAsyncExceptionDeliveryMark();
  };

  // Safepoint support
 public:                                                        // Expose _thread_state for SafeFetchInt()
  volatile JavaThreadState _thread_state;
 private:
  SafepointMechanism::ThreadData _poll_data;
  ThreadSafepointState*          _safepoint_state;              // Holds information about a thread during a safepoint
  address                        _saved_exception_pc;           // Saved pc of instruction where last implicit exception happened
  NOT_PRODUCT(bool               _requires_cross_modify_fence;) // State used by VerifyCrossModifyFence
#ifdef ASSERT
  // Debug support for checking if code allows safepoints or not.
  // Safepoints in the VM can happen because of allocation, invoking a VM operation, or blocking on
  // mutex, or blocking on an object synchronizer (Java locking).
  // If _no_safepoint_count is non-zero, then an assertion failure will happen in any of
  // the above cases. The class NoSafepointVerifier is used to set this counter.
  int _no_safepoint_count;                             // If 0, thread allow a safepoint to happen

 public:
  void inc_no_safepoint_count() { _no_safepoint_count++; }
  void dec_no_safepoint_count() { _no_safepoint_count--; }
  bool is_in_no_safepoint_scope() { return _no_safepoint_count > 0; }
#endif // ASSERT
 public:
  // These functions check conditions before possibly going to a safepoint.
  // including NoSafepointVerifier.
  void check_for_valid_safepoint_state() NOT_DEBUG_RETURN;
  void check_possible_safepoint()        NOT_DEBUG_RETURN;

#ifdef ASSERT
 private:
  volatile uint64_t _visited_for_critical_count;

 public:
  void set_visited_for_critical_count(uint64_t safepoint_id) {
    assert(_visited_for_critical_count == 0, "Must be reset before set");
    assert((safepoint_id & 0x1) == 1, "Must be odd");
    _visited_for_critical_count = safepoint_id;
  }
  void reset_visited_for_critical_count(uint64_t safepoint_id) {
    assert(_visited_for_critical_count == safepoint_id, "Was not visited");
    _visited_for_critical_count = 0;
  }
  bool was_visited_for_critical_count(uint64_t safepoint_id) const {
    return _visited_for_critical_count == safepoint_id;
  }
#endif // ASSERT

  // JavaThread termination support
 public:
  enum TerminatedTypes {
    _not_terminated = 0xDEAD - 3,
    _thread_exiting,                             // JavaThread::exit() has been called for this thread
    _thread_gc_barrier_detached,                 // thread's GC barrier has been detached
    _thread_terminated,                          // JavaThread is removed from thread list
    _vm_exited                                   // JavaThread is still executing native code, but VM is terminated
                                                 // only VM_Exit can set _vm_exited
  };

 private:
  // In general a JavaThread's _terminated field transitions as follows:
  //
  //   _not_terminated => _thread_exiting => _thread_gc_barrier_detached => _thread_terminated
  //
  // _vm_exited is a special value to cover the case of a JavaThread
  // executing native code after the VM itself is terminated.
  //
  // A JavaThread that fails to JNI attach has these _terminated field transitions:
  //   _not_terminated => _thread_terminated
  //
  volatile TerminatedTypes _terminated;

  jint                  _in_deopt_handler;       // count of deoptimization
                                                 // handlers thread is in
  volatile bool         _doing_unsafe_access;    // Thread may fault due to unsafe access
  bool                  _do_not_unlock_if_synchronized;  // Do not unlock the receiver of a synchronized method (since it was
                                                         // never locked) when throwing an exception. Used by interpreter only.
#if INCLUDE_JVMTI
  volatile bool         _carrier_thread_suspended;       // Carrier thread is externally suspended
  bool                  _is_in_VTMS_transition;          // thread is in virtual thread mount state transition
  bool                  _is_in_tmp_VTMS_transition;      // thread is in temporary virtual thread mount state transition
  bool                  _is_disable_suspend;             // JVMTI suspend is temporarily disabled; used on current thread only
#ifdef ASSERT
  bool                  _is_VTMS_transition_disabler;    // thread currently disabled VTMS transitions
#endif
#endif

  // JNI attach states:
  enum JNIAttachStates {
    _not_attaching_via_jni = 1,  // thread is not attaching via JNI
    _attaching_via_jni,          // thread is attaching via JNI
    _attached_via_jni            // thread has attached via JNI
  };

  // A regular JavaThread's _jni_attach_state is _not_attaching_via_jni.
  // A native thread that is attaching via JNI starts with a value
  // of _attaching_via_jni and transitions to _attached_via_jni.
  volatile JNIAttachStates _jni_attach_state;


#if INCLUDE_JVMCI
  // The _pending_* fields below are used to communicate extra information
  // from an uncommon trap in JVMCI compiled code to the uncommon trap handler.

  // Communicates the DeoptReason and DeoptAction of the uncommon trap
  int       _pending_deoptimization;

  // Specifies whether the uncommon trap is to bci 0 of a synchronized method
  // before the monitor has been acquired.
  bool      _pending_monitorenter;

  // Specifies if the DeoptReason for the last uncommon trap was Reason_transfer_to_interpreter
  bool      _pending_transfer_to_interpreter;

  // True if in a runtime call from compiled code that will deoptimize
  // and re-execute a failed heap allocation in the interpreter.
  bool      _in_retryable_allocation;

  // An id of a speculation that JVMCI compiled code can use to further describe and
  // uniquely identify the speculative optimization guarded by an uncommon trap.
  // See JVMCINMethodData::SPECULATION_LENGTH_BITS for further details.
  jlong     _pending_failed_speculation;

  // These fields are mutually exclusive in terms of live ranges.
  union {
    // Communicates the pc at which the most recent implicit exception occurred
    // from the signal handler to a deoptimization stub.
    address   _implicit_exception_pc;

    // Communicates an alternative call target to an i2c stub from a JavaCall .
    address   _alternate_call_target;
  } _jvmci;

  // The JVMCIRuntime in a JVMCI shared library
  JVMCIRuntime* _libjvmci_runtime;

  // Support for high precision, thread sensitive counters in JVMCI compiled code.
  jlong*    _jvmci_counters;

  // Fast thread locals for use by JVMCI
  jlong      _jvmci_reserved0;
  jlong      _jvmci_reserved1;
  oop        _jvmci_reserved_oop0;

 public:
  static jlong* _jvmci_old_thread_counters;
  static void collect_counters(jlong* array, int length);

  bool resize_counters(int current_size, int new_size);

  static bool resize_all_jvmci_counters(int new_size);

  void set_jvmci_reserved_oop0(oop value) {
    _jvmci_reserved_oop0 = value;
  }

  oop get_jvmci_reserved_oop0() {
    return _jvmci_reserved_oop0;
  }

  void set_jvmci_reserved0(jlong value) {
    _jvmci_reserved0 = value;
  }

  jlong get_jvmci_reserved0() {
    return _jvmci_reserved0;
  }

  void set_jvmci_reserved1(jlong value) {
    _jvmci_reserved1 = value;
  }

  jlong get_jvmci_reserved1() {
    return _jvmci_reserved1;
  }

 private:
#endif // INCLUDE_JVMCI

  StackOverflow    _stack_overflow_state;

  void pretouch_stack();

  // Compiler exception handling (NOTE: The _exception_oop is *NOT* the same as _pending_exception. It is
  // used to temp. parsing values into and out of the runtime system during exception handling for compiled
  // code)
  volatile oop     _exception_oop;               // Exception thrown in compiled code
  volatile address _exception_pc;                // PC where exception happened
  volatile address _exception_handler_pc;        // PC for handler of exception
  volatile int     _is_method_handle_return;     // true (== 1) if the current exception PC is a MethodHandle call site.

 private:
  // support for JNI critical regions
  jint    _jni_active_critical;                  // count of entries into JNI critical region

  // Checked JNI: function name requires exception check
  char* _pending_jni_exception_check_fn;

  // For deadlock detection.
  int _depth_first_number;

  // JVMTI PopFrame support
  // This is set to popframe_pending to signal that top Java frame should be popped immediately
  int _popframe_condition;

  // If reallocation of scalar replaced objects fails, we throw OOM
  // and during exception propagation, pop the top
  // _frames_to_pop_failed_realloc frames, the ones that reference
  // failed reallocations.
  int _frames_to_pop_failed_realloc;

  ContinuationEntry* _cont_entry;
  intptr_t* _cont_fastpath; // the sp of the oldest known interpreted/call_stub frame inside the
                            // continuation that we know about
  int _cont_fastpath_thread_state; // whether global thread state allows continuation fastpath (JVMTI)

  // It's signed for error detection.
  intx _held_monitor_count;  // used by continuations for fast lock detection
  intx _jni_monitor_count;

private:

  friend class VMThread;
  friend class ThreadWaitTransition;
  friend class VM_Exit;

  // Stack watermark barriers.
  StackWatermarks _stack_watermarks;

 public:
  inline StackWatermarks* stack_watermarks() { return &_stack_watermarks; }

 public:
  // Constructor
  JavaThread();                            // delegating constructor
  JavaThread(bool is_attaching_via_jni);   // for main thread and JNI attached threads
  JavaThread(ThreadFunction entry_point, size_t stack_size = 0);
  ~JavaThread();

#ifdef ASSERT
  // verify this JavaThread hasn't be published in the Threads::list yet
  void verify_not_published();
#endif // ASSERT

  StackOverflow* stack_overflow_state() { return &_stack_overflow_state; }

  //JNI functiontable getter/setter for JVMTI jni function table interception API.
  void set_jni_functions(struct JNINativeInterface_* functionTable) {
    _jni_environment.functions = functionTable;
  }
  struct JNINativeInterface_* get_jni_functions() {
    return (struct JNINativeInterface_ *)_jni_environment.functions;
  }

  // This function is called at thread creation to allow
  // platform specific thread variables to be initialized.
  void cache_global_variables();

  // Executes Shutdown.shutdown()
  void invoke_shutdown_hooks();

  // Cleanup on thread exit
  enum ExitType {
    normal_exit,
    jni_detach
  };
  void exit(bool destroy_vm, ExitType exit_type = normal_exit);

  void cleanup_failed_attach_current_thread(bool is_daemon);

  // Testers
  virtual bool is_Java_thread() const            { return true;  }
  virtual bool can_call_java() const             { return true; }

  virtual bool is_active_Java_thread() const;

  // Thread oop. threadObj() can be null for initial JavaThread
  // (or for threads attached via JNI)
  oop threadObj() const;
  void set_threadOopHandles(oop p);
  oop vthread() const;
  void set_vthread(oop p);
  oop scopedValueCache() const;
  void set_scopedValueCache(oop p);
  void clear_scopedValueBindings();
  oop jvmti_vthread() const;
  void set_jvmti_vthread(oop p);

  // Prepare thread and add to priority queue.  If a priority is
  // not specified, use the priority of the thread object. Threads_lock
  // must be held while this function is called.
  void prepare(jobject jni_thread, ThreadPriority prio=NoPriority);

  void set_saved_exception_pc(address pc)        { _saved_exception_pc = pc; }
  address saved_exception_pc()                   { return _saved_exception_pc; }

  ThreadFunction entry_point() const             { return _entry_point; }

  // Allocates a new Java level thread object for this thread. thread_name may be null.
  void allocate_threadObj(Handle thread_group, const char* thread_name, bool daemon, TRAPS);

  // Last frame anchor routines

  JavaFrameAnchor* frame_anchor(void)            { return &_anchor; }

  // last_Java_sp
  bool has_last_Java_frame() const               { return _anchor.has_last_Java_frame(); }
  intptr_t* last_Java_sp() const                 { return _anchor.last_Java_sp(); }

  // last_Java_pc

  address last_Java_pc(void)                     { return _anchor.last_Java_pc(); }

  // Safepoint support
  inline JavaThreadState thread_state() const;
  inline void set_thread_state(JavaThreadState s);
  inline void set_thread_state_fence(JavaThreadState s);  // fence after setting thread state
  inline ThreadSafepointState* safepoint_state() const;
  inline void set_safepoint_state(ThreadSafepointState* state);
  inline bool is_at_poll_safepoint();

  // JavaThread termination and lifecycle support:
  void smr_delete();
  bool on_thread_list() const { return _on_thread_list; }
  void set_on_thread_list() { _on_thread_list = true; }

  // thread has called JavaThread::exit(), thread's GC barrier is detached
  // or thread is terminated
  bool is_exiting() const;
  // thread's GC barrier is NOT detached and thread is NOT terminated
  bool is_oop_safe() const;
  // thread is terminated (no longer on the threads list); the thread must
  // be protected by a ThreadsListHandle to avoid potential crashes.
  bool check_is_terminated(TerminatedTypes l_terminated) const {
    return l_terminated == _thread_terminated || l_terminated == _vm_exited;
  }
  bool is_terminated() const;
  void set_terminated(TerminatedTypes t);

  void block_if_vm_exited();

  bool doing_unsafe_access()                     { return _doing_unsafe_access; }
  void set_doing_unsafe_access(bool val)         { _doing_unsafe_access = val; }

  bool do_not_unlock_if_synchronized()             { return _do_not_unlock_if_synchronized; }
  void set_do_not_unlock_if_synchronized(bool val) { _do_not_unlock_if_synchronized = val; }

  SafepointMechanism::ThreadData* poll_data() { return &_poll_data; }

  void set_requires_cross_modify_fence(bool val) PRODUCT_RETURN NOT_PRODUCT({ _requires_cross_modify_fence = val; })

  // Continuation support
  ContinuationEntry* last_continuation() const { return _cont_entry; }
  void set_cont_fastpath(intptr_t* x)          { _cont_fastpath = x; }
  void push_cont_fastpath(intptr_t* sp)        { if (sp > _cont_fastpath) _cont_fastpath = sp; }
  void set_cont_fastpath_thread_state(bool x)  { _cont_fastpath_thread_state = (int)x; }
  intptr_t* raw_cont_fastpath() const          { return _cont_fastpath; }
  bool cont_fastpath() const                   { return _cont_fastpath == nullptr && _cont_fastpath_thread_state != 0; }
  bool cont_fastpath_thread_state() const      { return _cont_fastpath_thread_state != 0; }

  void inc_held_monitor_count(intx i = 1, bool jni = false);
  void dec_held_monitor_count(intx i = 1, bool jni = false);

  intx held_monitor_count() { return _held_monitor_count; }
  intx jni_monitor_count()  { return _jni_monitor_count;  }
  void clear_jni_monitor_count() { _jni_monitor_count = 0;   }

  inline bool is_vthread_mounted() const;
  inline const ContinuationEntry* vthread_continuation() const;

 private:
  DEBUG_ONLY(void verify_frame_info();)

  // Support for thread handshake operations
  HandshakeState _handshake;
 public:
  HandshakeState* handshake_state() { return &_handshake; }

  // A JavaThread can always safely operate on it self and other threads
  // can do it safely if they are the active handshaker.
  bool is_handshake_safe_for(Thread* th) const {
    return _handshake.active_handshaker() == th || this == th;
  }

  // Suspend/resume support for JavaThread
  // higher-level suspension/resume logic called by the public APIs
  bool java_suspend();
  bool java_resume();
  bool is_suspended()     { return _handshake.is_suspended(); }

  // Check for async exception in addition to safepoint.
  static void check_special_condition_for_native_trans(JavaThread *thread);

  // Synchronize with another thread that is deoptimizing objects of the
  // current thread, i.e. reverts optimizations based on escape analysis.
  void wait_for_object_deoptimization();

#if INCLUDE_JVMTI
  inline void set_carrier_thread_suspended();
  inline void clear_carrier_thread_suspended();

  bool is_carrier_thread_suspended() const {
    return _carrier_thread_suspended;
  }

  bool is_in_VTMS_transition() const             { return _is_in_VTMS_transition; }
  bool is_in_tmp_VTMS_transition() const         { return _is_in_tmp_VTMS_transition; }
  bool is_in_any_VTMS_transition() const         { return _is_in_VTMS_transition || _is_in_tmp_VTMS_transition; }

  void set_is_in_VTMS_transition(bool val);
  void toggle_is_in_tmp_VTMS_transition()        { _is_in_tmp_VTMS_transition = !_is_in_tmp_VTMS_transition; };

  bool is_disable_suspend() const                { return _is_disable_suspend; }
  void toggle_is_disable_suspend()               { _is_disable_suspend = !_is_disable_suspend; };

#ifdef ASSERT
  bool is_VTMS_transition_disabler() const       { return _is_VTMS_transition_disabler; }
  void set_is_VTMS_transition_disabler(bool val);
#endif
#endif

  // Support for object deoptimization and JFR suspension
  void handle_special_runtime_exit_condition();
  bool has_special_runtime_exit_condition() {
    return (_suspend_flags & (_obj_deopt JFR_ONLY(| _trace_flag))) != 0;
  }

  // Fast-locking support
  bool is_lock_owned(address adr) const;

  // Accessors for vframe array top
  // The linked list of vframe arrays are sorted on sp. This means when we
  // unpack the head must contain the vframe array to unpack.
  void set_vframe_array_head(vframeArray* value) { _vframe_array_head = value; }
  vframeArray* vframe_array_head() const         { return _vframe_array_head;  }

  // Side structure for deferring update of java frame locals until deopt occurs
  JvmtiDeferredUpdates* deferred_updates() const      { return _jvmti_deferred_updates; }
  void set_deferred_updates(JvmtiDeferredUpdates* du) { _jvmti_deferred_updates = du; }

  // These only really exist to make debugging deopt problems simpler

  void set_vframe_array_last(vframeArray* value) { _vframe_array_last = value; }
  vframeArray* vframe_array_last() const         { return _vframe_array_last;  }

  // The special resourceMark used during deoptimization

  void set_deopt_mark(DeoptResourceMark* value)  { _deopt_mark = value; }
  DeoptResourceMark* deopt_mark(void)            { return _deopt_mark; }

  void set_deopt_compiled_method(CompiledMethod* nm)  { _deopt_nmethod = nm; }
  CompiledMethod* deopt_compiled_method()        { return _deopt_nmethod; }

  Method*    callee_target() const               { return _callee_target; }
  void set_callee_target  (Method* x)            { _callee_target   = x; }

  // Oop results of vm runtime calls
  oop  vm_result() const                         { return _vm_result; }
  void set_vm_result  (oop x)                    { _vm_result   = x; }

  void set_vm_result_2  (Metadata* x)            { _vm_result_2   = x; }

  MemRegion deferred_card_mark() const           { return _deferred_card_mark; }
  void set_deferred_card_mark(MemRegion mr)      { _deferred_card_mark = mr;   }

#if INCLUDE_JVMCI
  jlong pending_failed_speculation() const        { return _pending_failed_speculation; }
  void set_pending_monitorenter(bool b)           { _pending_monitorenter = b; }
  void set_pending_deoptimization(int reason)     { _pending_deoptimization = reason; }
  void set_pending_failed_speculation(jlong failed_speculation) { _pending_failed_speculation = failed_speculation; }
  void set_pending_transfer_to_interpreter(bool b) { _pending_transfer_to_interpreter = b; }
  void set_jvmci_alternate_call_target(address a) { assert(_jvmci._alternate_call_target == nullptr, "must be"); _jvmci._alternate_call_target = a; }
  void set_jvmci_implicit_exception_pc(address a) { assert(_jvmci._implicit_exception_pc == nullptr, "must be"); _jvmci._implicit_exception_pc = a; }

  virtual bool in_retryable_allocation() const    { return _in_retryable_allocation; }
  void set_in_retryable_allocation(bool b)        { _in_retryable_allocation = b; }

  JVMCIRuntime* libjvmci_runtime() const          { return _libjvmci_runtime; }
  void set_libjvmci_runtime(JVMCIRuntime* rt) {
    assert((_libjvmci_runtime == nullptr && rt != nullptr) || (_libjvmci_runtime != nullptr && rt == nullptr), "must be");
    _libjvmci_runtime = rt;
  }
#endif // INCLUDE_JVMCI

  // Exception handling for compiled methods
  oop      exception_oop() const;
  address  exception_pc() const                  { return _exception_pc; }

  void set_exception_oop(oop o);
  void set_exception_pc(address a)               { _exception_pc = a; }
  void set_exception_handler_pc(address a)       { _exception_handler_pc = a; }
  void set_is_method_handle_return(bool value)   { _is_method_handle_return = value ? 1 : 0; }

  void clear_exception_oop_and_pc() {
    set_exception_oop(nullptr);
    set_exception_pc(nullptr);
  }

  // Check if address is in the usable part of the stack (excludes protected
  // guard pages). Can be applied to any thread and is an approximation for
  // using is_in_live_stack when the query has to happen from another thread.
  bool is_in_usable_stack(address adr) const {
    return is_in_stack_range_incl(adr, _stack_overflow_state.stack_reserved_zone_base());
  }

  // Misc. accessors/mutators
  static ByteSize scopedValueCache_offset()       { return byte_offset_of(JavaThread, _scopedValueCache); }

  // For assembly stub generation
  static ByteSize threadObj_offset()             { return byte_offset_of(JavaThread, _threadObj); }
  static ByteSize vthread_offset()               { return byte_offset_of(JavaThread, _vthread); }
  static ByteSize jni_environment_offset()       { return byte_offset_of(JavaThread, _jni_environment); }
  static ByteSize pending_jni_exception_check_fn_offset() {
    return byte_offset_of(JavaThread, _pending_jni_exception_check_fn);
  }
  static ByteSize last_Java_sp_offset() {
    return byte_offset_of(JavaThread, _anchor) + JavaFrameAnchor::last_Java_sp_offset();
  }
  static ByteSize last_Java_pc_offset() {
    return byte_offset_of(JavaThread, _anchor) + JavaFrameAnchor::last_Java_pc_offset();
  }
  static ByteSize frame_anchor_offset() {
    return byte_offset_of(JavaThread, _anchor);
  }
  static ByteSize callee_target_offset()         { return byte_offset_of(JavaThread, _callee_target); }
  static ByteSize vm_result_offset()             { return byte_offset_of(JavaThread, _vm_result); }
  static ByteSize vm_result_2_offset()           { return byte_offset_of(JavaThread, _vm_result_2); }
  static ByteSize thread_state_offset()          { return byte_offset_of(JavaThread, _thread_state); }
  static ByteSize polling_word_offset()          { return byte_offset_of(JavaThread, _poll_data) + byte_offset_of(SafepointMechanism::ThreadData, _polling_word);}
  static ByteSize polling_page_offset()          { return byte_offset_of(JavaThread, _poll_data) + byte_offset_of(SafepointMechanism::ThreadData, _polling_page);}
  static ByteSize saved_exception_pc_offset()    { return byte_offset_of(JavaThread, _saved_exception_pc); }
  static ByteSize osthread_offset()              { return byte_offset_of(JavaThread, _osthread); }
#if INCLUDE_JVMCI
  static ByteSize pending_deoptimization_offset() { return byte_offset_of(JavaThread, _pending_deoptimization); }
  static ByteSize pending_monitorenter_offset()  { return byte_offset_of(JavaThread, _pending_monitorenter); }
  static ByteSize jvmci_alternate_call_target_offset() { return byte_offset_of(JavaThread, _jvmci._alternate_call_target); }
  static ByteSize jvmci_implicit_exception_pc_offset() { return byte_offset_of(JavaThread, _jvmci._implicit_exception_pc); }
  static ByteSize jvmci_counters_offset()        { return byte_offset_of(JavaThread, _jvmci_counters); }
#endif // INCLUDE_JVMCI
  static ByteSize exception_oop_offset()         { return byte_offset_of(JavaThread, _exception_oop); }
  static ByteSize exception_pc_offset()          { return byte_offset_of(JavaThread, _exception_pc); }
  static ByteSize exception_handler_pc_offset()  { return byte_offset_of(JavaThread, _exception_handler_pc); }
  static ByteSize is_method_handle_return_offset() { return byte_offset_of(JavaThread, _is_method_handle_return); }

  static ByteSize active_handles_offset()        { return byte_offset_of(JavaThread, _active_handles); }

  // StackOverflow offsets
  static ByteSize stack_overflow_limit_offset()  {
    return byte_offset_of(JavaThread, _stack_overflow_state._stack_overflow_limit);
  }
  static ByteSize stack_guard_state_offset()     {
    return byte_offset_of(JavaThread, _stack_overflow_state._stack_guard_state);
  }
  static ByteSize reserved_stack_activation_offset() {
    return byte_offset_of(JavaThread, _stack_overflow_state._reserved_stack_activation);
  }
  static ByteSize shadow_zone_safe_limit()  {
    return byte_offset_of(JavaThread, _stack_overflow_state._shadow_zone_safe_limit);
  }
  static ByteSize shadow_zone_growth_watermark()  {
    return byte_offset_of(JavaThread, _stack_overflow_state._shadow_zone_growth_watermark);
  }

  static ByteSize suspend_flags_offset()         { return byte_offset_of(JavaThread, _suspend_flags); }

  static ByteSize do_not_unlock_if_synchronized_offset() { return byte_offset_of(JavaThread, _do_not_unlock_if_synchronized); }
  static ByteSize should_post_on_exceptions_flag_offset() {
    return byte_offset_of(JavaThread, _should_post_on_exceptions_flag);
  }
  static ByteSize doing_unsafe_access_offset() { return byte_offset_of(JavaThread, _doing_unsafe_access); }
  NOT_PRODUCT(static ByteSize requires_cross_modify_fence_offset()  { return byte_offset_of(JavaThread, _requires_cross_modify_fence); })

  static ByteSize cont_entry_offset()         { return byte_offset_of(JavaThread, _cont_entry); }
  static ByteSize cont_fastpath_offset()      { return byte_offset_of(JavaThread, _cont_fastpath); }
  static ByteSize held_monitor_count_offset() { return byte_offset_of(JavaThread, _held_monitor_count); }

#if INCLUDE_JVMTI
  static ByteSize is_in_VTMS_transition_offset()     { return byte_offset_of(JavaThread, _is_in_VTMS_transition); }
  static ByteSize is_in_tmp_VTMS_transition_offset() { return byte_offset_of(JavaThread, _is_in_tmp_VTMS_transition); }
  static ByteSize is_disable_suspend_offset()        { return byte_offset_of(JavaThread, _is_disable_suspend); }
#endif

  // Returns the jni environment for this thread
  JNIEnv* jni_environment()                      { return &_jni_environment; }

  // Returns the current thread as indicated by the given JNIEnv.
  // We don't assert it is Thread::current here as that is done at the
  // external JNI entry points where the JNIEnv is passed into the VM.
  static JavaThread* thread_from_jni_environment(JNIEnv* env) {
    JavaThread* current = reinterpret_cast<JavaThread*>(((intptr_t)env - in_bytes(jni_environment_offset())));
    // We can't normally get here in a thread that has completed its
    // execution and so "is_terminated", except when the call is from
    // AsyncGetCallTrace, which can be triggered by a signal at any point in
    // a thread's lifecycle. A thread is also considered terminated if the VM
    // has exited, so we have to check this and block in case this is a daemon
    // thread returning to the VM (the JNI DirectBuffer entry points rely on
    // this).
    if (current->is_terminated()) {
      current->block_if_vm_exited();
    }
    return current;
  }

  // JNI critical regions. These can nest.
  bool in_critical()    { return _jni_active_critical > 0; }
  bool in_last_critical()  { return _jni_active_critical == 1; }
  inline void enter_critical();
  void exit_critical() {
    assert(Thread::current() == this, "this must be current thread");
    _jni_active_critical--;
    assert(_jni_active_critical >= 0, "JNI critical nesting problem?");
  }

  // Checked JNI: is the programmer required to check for exceptions, if so specify
  // which function name. Returning to a Java frame should implicitly clear the
  // pending check, this is done for Native->Java transitions (i.e. user JNI code).
  // VM->Java transitions are not cleared, it is expected that JNI code enclosed
  // within ThreadToNativeFromVM makes proper exception checks (i.e. VM internal).
  bool is_pending_jni_exception_check() const { return _pending_jni_exception_check_fn != nullptr; }
  void clear_pending_jni_exception_check() { _pending_jni_exception_check_fn = nullptr; }
  const char* get_pending_jni_exception_check() const { return _pending_jni_exception_check_fn; }
  void set_pending_jni_exception_check(const char* fn_name) { _pending_jni_exception_check_fn = (char*) fn_name; }

  // For deadlock detection
  int depth_first_number() { return _depth_first_number; }
  void set_depth_first_number(int dfn) { _depth_first_number = dfn; }

 private:
  void set_monitor_chunks(MonitorChunk* monitor_chunks) { _monitor_chunks = monitor_chunks; }

 public:
  MonitorChunk* monitor_chunks() const           { return _monitor_chunks; }
  void add_monitor_chunk(MonitorChunk* chunk);
  void remove_monitor_chunk(MonitorChunk* chunk);
  bool in_deopt_handler() const                  { return _in_deopt_handler > 0; }
  void inc_in_deopt_handler()                    { _in_deopt_handler++; }
  void dec_in_deopt_handler() {
    assert(_in_deopt_handler > 0, "mismatched deopt nesting");
    if (_in_deopt_handler > 0) { // robustness
      _in_deopt_handler--;
    }
  }

 private:
  void set_entry_point(ThreadFunction entry_point) { _entry_point = entry_point; }

  // factor out low-level mechanics for use in both normal and error cases
  const char* get_thread_name_string(char* buf = nullptr, int buflen = 0) const;

 public:

  // Frame iteration; calls the function f for all frames on the stack
  void frames_do(void f(frame*, const RegisterMap*));

  // Memory operations
  void oops_do_frames(OopClosure* f, CodeBlobClosure* cf);
  void oops_do_no_frames(OopClosure* f, CodeBlobClosure* cf);

  // GC operations
  virtual void nmethods_do(CodeBlobClosure* cf);

  // RedefineClasses Support
  void metadata_do(MetadataClosure* f);

  // Debug method asserting thread states are correct during a handshake operation.
  DEBUG_ONLY(void verify_states_for_handshake();)

  // Misc. operations
  const char* name() const;
  const char* name_raw() const;
  const char* type_name() const { return "JavaThread"; }
  static const char* name_for(oop thread_obj);

  void print_on(outputStream* st, bool print_extended_info) const;
  void print_on(outputStream* st) const { print_on(st, false); }
  void print() const;
  void print_thread_state_on(outputStream*) const;
  void print_on_error(outputStream* st, char* buf, int buflen) const;
  void print_name_on_error(outputStream* st, char* buf, int buflen) const;
  void verify();

  // Accessing frames
  frame last_frame() {
    _anchor.make_walkable();
    return pd_last_frame();
  }
  javaVFrame* last_java_vframe(RegisterMap* reg_map) { return last_java_vframe(last_frame(), reg_map); }

  frame carrier_last_frame(RegisterMap* reg_map);
  javaVFrame* carrier_last_java_vframe(RegisterMap* reg_map) { return last_java_vframe(carrier_last_frame(reg_map), reg_map); }

  frame vthread_last_frame();
  javaVFrame* vthread_last_java_vframe(RegisterMap* reg_map) { return last_java_vframe(vthread_last_frame(), reg_map); }

  frame platform_thread_last_frame(RegisterMap* reg_map);
  javaVFrame*  platform_thread_last_java_vframe(RegisterMap* reg_map) {
    return last_java_vframe(platform_thread_last_frame(reg_map), reg_map);
  }

  javaVFrame* last_java_vframe(const frame f, RegisterMap* reg_map);

  // Returns method at 'depth' java or native frames down the stack
  // Used for security checks
  Klass* security_get_caller_class(int depth);

  // Print stack trace in external format
  // These variants print carrier/platform thread information only.
  void print_stack_on(outputStream* st);
  void print_stack() { print_stack_on(tty); }
  // This prints the currently mounted virtual thread.
  void print_vthread_stack_on(outputStream* st);
  // This prints the active stack: either carrier/platform or virtual.
  void print_active_stack_on(outputStream* st);
  // Print current stack trace for checked JNI warnings and JNI fatal errors.
  // This is the external format from above, but selecting the platform
  // or vthread as applicable.
  void print_jni_stack();

  // Print stack traces in various internal formats
  void trace_stack()                             PRODUCT_RETURN;
  void trace_stack_from(vframe* start_vf)        PRODUCT_RETURN;
  void trace_frames()                            PRODUCT_RETURN;

  // Print an annotated view of the stack frames
  void print_frame_layout(int depth = 0, bool validate_only = false) NOT_DEBUG_RETURN;
  void validate_frame_layout() {
    print_frame_layout(0, true);
  }

  // Function for testing deoptimization
  void deoptimize();
  void make_zombies();

  void deoptimize_marked_methods();

 public:
  // Returns the running thread as a JavaThread
  static JavaThread* current() {
    return JavaThread::cast(Thread::current());
  }

  // Returns the current thread as a JavaThread, or nullptr if not attached
  static inline JavaThread* current_or_null();

  // Casts
  static JavaThread* cast(Thread* t) {
    assert(t->is_Java_thread(), "incorrect cast to JavaThread");
    return static_cast<JavaThread*>(t);
  }

  static const JavaThread* cast(const Thread* t) {
    assert(t->is_Java_thread(), "incorrect cast to const JavaThread");
    return static_cast<const JavaThread*>(t);
  }

  // Returns the active Java thread.  Do not use this if you know you are calling
  // from a JavaThread, as it's slower than JavaThread::current.  If called from
  // the VMThread, it also returns the JavaThread that instigated the VMThread's
  // operation.  You may not want that either.
  static JavaThread* active();

 protected:
  virtual void pre_run();
  virtual void run();
  void thread_main_inner();
  virtual void post_run();

 public:
  // Thread local information maintained by JVMTI.
  void set_jvmti_thread_state(JvmtiThreadState *value)                           { _jvmti_thread_state = value; }
  // A JvmtiThreadState is lazily allocated. This jvmti_thread_state()
  // getter is used to get this JavaThread's JvmtiThreadState if it has
  // one which means null can be returned. JvmtiThreadState::state_for()
  // is used to get the specified JavaThread's JvmtiThreadState if it has
  // one or it allocates a new JvmtiThreadState for the JavaThread and
  // returns it. JvmtiThreadState::state_for() will return null only if
  // the specified JavaThread is exiting.
  JvmtiThreadState *jvmti_thread_state() const                                   { return _jvmti_thread_state; }
  static ByteSize jvmti_thread_state_offset()                                    { return byte_offset_of(JavaThread, _jvmti_thread_state); }

#if INCLUDE_JVMTI
  // Rebind JVMTI thread state from carrier to virtual or from virtual to carrier.
  JvmtiThreadState *rebind_to_jvmti_thread_state_of(oop thread_oop);
#endif

  // JVMTI PopFrame support
  // Setting and clearing popframe_condition
  // All of these enumerated values are bits. popframe_pending
  // indicates that a PopFrame() has been requested and not yet been
  // completed. popframe_processing indicates that that PopFrame() is in
  // the process of being completed. popframe_force_deopt_reexecution_bit
  // indicates that special handling is required when returning to a
  // deoptimized caller.
  enum PopCondition {
    popframe_inactive                      = 0x00,
    popframe_pending_bit                   = 0x01,
    popframe_processing_bit                = 0x02,
    popframe_force_deopt_reexecution_bit   = 0x04
  };
  PopCondition popframe_condition()                   { return (PopCondition) _popframe_condition; }
  void set_popframe_condition(PopCondition c)         { _popframe_condition = c; }
  void set_popframe_condition_bit(PopCondition c)     { _popframe_condition |= c; }
  void clear_popframe_condition()                     { _popframe_condition = popframe_inactive; }
  static ByteSize popframe_condition_offset()         { return byte_offset_of(JavaThread, _popframe_condition); }
  bool has_pending_popframe()                         { return (popframe_condition() & popframe_pending_bit) != 0; }
  bool popframe_forcing_deopt_reexecution()           { return (popframe_condition() & popframe_force_deopt_reexecution_bit) != 0; }

  bool pop_frame_in_process(void)                     { return ((_popframe_condition & popframe_processing_bit) != 0); }
  void set_pop_frame_in_process(void)                 { _popframe_condition |= popframe_processing_bit; }
  void clr_pop_frame_in_process(void)                 { _popframe_condition &= ~popframe_processing_bit; }

  int frames_to_pop_failed_realloc() const            { return _frames_to_pop_failed_realloc; }
  void set_frames_to_pop_failed_realloc(int nb)       { _frames_to_pop_failed_realloc = nb; }
  void dec_frames_to_pop_failed_realloc()             { _frames_to_pop_failed_realloc--; }

 private:
  // Saved incoming arguments to popped frame.
  // Used only when popped interpreted frame returns to deoptimized frame.
  void*    _popframe_preserved_args;
  int      _popframe_preserved_args_size;

 public:
  void  popframe_preserve_args(ByteSize size_in_bytes, void* start);
  void* popframe_preserved_args();
  ByteSize popframe_preserved_args_size();
  WordSize popframe_preserved_args_size_in_words();
  void  popframe_free_preserved_args();


 private:
  JvmtiThreadState *_jvmti_thread_state;

  // Used by the interpreter in fullspeed mode for frame pop, method
  // entry, method exit and single stepping support. This field is
  // only set to non-zero at a safepoint or using a direct handshake
  // (see EnterInterpOnlyModeClosure).
  // It can be set to zero asynchronously to this threads execution (i.e., without
  // safepoint/handshake or a lock) so we have to be very careful.
  // Accesses by other threads are synchronized using JvmtiThreadState_lock though.
  int               _interp_only_mode;

 public:
  // used by the interpreter for fullspeed debugging support (see above)
  static ByteSize interp_only_mode_offset() { return byte_offset_of(JavaThread, _interp_only_mode); }
  bool is_interp_only_mode()                { return (_interp_only_mode != 0); }
  int get_interp_only_mode()                { return _interp_only_mode; }
  int set_interp_only_mode(int val)         { return _interp_only_mode = val; }
  void increment_interp_only_mode()         { ++_interp_only_mode; }
  void decrement_interp_only_mode()         { --_interp_only_mode; }

  // support for cached flag that indicates whether exceptions need to be posted for this thread
  // if this is false, we can avoid deoptimizing when events are thrown
  // this gets set to reflect whether jvmtiExport::post_exception_throw would actually do anything
 private:
  int    _should_post_on_exceptions_flag;

 public:
  void  set_should_post_on_exceptions_flag(int val)  { _should_post_on_exceptions_flag = val; }

 private:
  ThreadStatistics *_thread_stat;

 public:
  ThreadStatistics* get_thread_stat() const    { return _thread_stat; }

  // Return a blocker object for which this thread is blocked parking.
  oop current_park_blocker();

 private:
  static size_t _stack_size_at_create;

 public:
  static inline size_t stack_size_at_create(void) {
    return _stack_size_at_create;
  }
  static inline void set_stack_size_at_create(size_t value) {
    _stack_size_at_create = value;
  }

  // Machine dependent stuff
#include OS_CPU_HEADER(javaThread)

  // JSR166 per-thread parker
 private:
  Parker _parker;
 public:
  Parker* parker() { return &_parker; }

 public:
  // clearing/querying jni attach status
  bool is_attaching_via_jni() const { return _jni_attach_state == _attaching_via_jni; }
  bool has_attached_via_jni() const { return is_attaching_via_jni() || _jni_attach_state == _attached_via_jni; }
  inline void set_done_attaching_via_jni();

  // Stack dump assistance:
  // Track the class we want to initialize but for which we have to wait
  // on its init_lock() because it is already being initialized.
  void set_class_to_be_initialized(InstanceKlass* k);
  InstanceKlass* class_to_be_initialized() const;

private:
  InstanceKlass* _class_to_be_initialized;

  // java.lang.Thread.sleep support
  ParkEvent * _SleepEvent;
public:
  bool sleep(jlong millis);
  bool sleep_nanos(jlong nanos);

  // java.lang.Thread interruption support
  void interrupt();
  bool is_interrupted(bool clear_interrupted);

private:
  LockStack _lock_stack;

public:
  LockStack& lock_stack() { return _lock_stack; }

  static ByteSize lock_stack_offset()      { return byte_offset_of(JavaThread, _lock_stack); }
  // Those offsets are used in code generators to access the LockStack that is embedded in this
  // JavaThread structure. Those accesses are relative to the current thread, which
  // is typically in a dedicated register.
  static ByteSize lock_stack_top_offset()  { return lock_stack_offset() + LockStack::top_offset(); }
  static ByteSize lock_stack_base_offset() { return lock_stack_offset() + LockStack::base_offset(); }

  static OopStorage* thread_oop_storage();

  static void verify_cross_modify_fence_failure(JavaThread *thread) PRODUCT_RETURN;

  // Helper function to create the java.lang.Thread object for a
  // VM-internal thread. The thread will have the given name and be
  // part of the System ThreadGroup.
  static Handle create_system_thread_object(const char* name, TRAPS);

  // Helper function to start a VM-internal daemon thread.
  // E.g. ServiceThread, NotificationThread, CompilerThread etc.
  static void start_internal_daemon(JavaThread* current, JavaThread* target,
                                    Handle thread_oop, ThreadPriority prio);

  // Helper function to do vm_exit_on_initialization for osthread
  // resource allocation failure.
  static void vm_exit_on_osthread_failure(JavaThread* thread);

  // Deferred OopHandle release support
 private:
  // List of OopHandles to be released - guarded by the Service_lock.
  static OopHandleList* _oop_handle_list;
  // Add our OopHandles to the list for the service thread to release.
  void add_oop_handles_for_release();
  // Called by the ServiceThread to release the OopHandles.
  static void release_oop_handles();
  // Called by the ServiceThread to poll if there are any OopHandles to release.
  // Called when holding the Service_lock.
  static bool has_oop_handles_to_release() {
    return _oop_handle_list != nullptr;
  }
};

inline JavaThread* JavaThread::current_or_null() {
  Thread* current = Thread::current_or_null();
  return current != nullptr ? JavaThread::cast(current) : nullptr;
}

class UnlockFlagSaver {
  private:
    JavaThread* _thread;
    bool _do_not_unlock;
  public:
    UnlockFlagSaver(JavaThread* t) {
      _thread = t;
      _do_not_unlock = t->do_not_unlock_if_synchronized();
      t->set_do_not_unlock_if_synchronized(false);
    }
    ~UnlockFlagSaver() {
      _thread->set_do_not_unlock_if_synchronized(_do_not_unlock);
    }
};

class JNIHandleMark : public StackObj {
  JavaThread* _thread;
 public:
  JNIHandleMark(JavaThread* thread) : _thread(thread) {
    thread->push_jni_handle_block();
  }
  ~JNIHandleMark() { _thread->pop_jni_handle_block(); }
};

#endif // SHARE_RUNTIME_JAVATHREAD_HPP

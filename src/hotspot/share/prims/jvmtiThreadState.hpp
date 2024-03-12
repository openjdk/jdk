/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_PRIMS_JVMTITHREADSTATE_HPP
#define SHARE_PRIMS_JVMTITHREADSTATE_HPP

#include "jvmtifiles/jvmti.h"
#include "memory/allocation.hpp"
#include "oops/oopHandle.hpp"
#include "prims/jvmtiEventController.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/threads.hpp"
#include "utilities/growableArray.hpp"

//
// Forward Declarations
//

class JvmtiEnvBase;
class JvmtiEnvThreadState;
class JvmtiDynamicCodeEventCollector;

class JvmtiDeferredEvent;
class JvmtiDeferredEventQueue;

enum JvmtiClassLoadKind {
  jvmti_class_load_kind_load = 100,
  jvmti_class_load_kind_retransform,
  jvmti_class_load_kind_redefine
};

///////////////////////////////////////////////////////////////
//
// class JvmtiEnvThreadStateIterator
//
// The only safe means of iterating through the JvmtiEnvThreadStates
// in a JvmtiThreadState.
// Note that this iteratation includes invalid environments pending
// deallocation -- in fact, some uses depend on this behavior.
//
class JvmtiEnvThreadStateIterator : public StackObj {
 private:
  JvmtiThreadState* state;
 public:
  JvmtiEnvThreadStateIterator(JvmtiThreadState* thread_state);
  ~JvmtiEnvThreadStateIterator();
  JvmtiEnvThreadState* first();
  JvmtiEnvThreadState* next(JvmtiEnvThreadState* ets);
};

///////////////////////////////////////////////////////////////
//
// class JvmtiVTMSTransitionDisabler
//
// Virtual Thread Mount State Transition (VTMS transition) mechanism
//
class JvmtiVTMSTransitionDisabler {
 private:
  static volatile int _VTMS_transition_disable_for_one_count; // transitions for one virtual thread are disabled while it is positive
  static volatile int _VTMS_transition_disable_for_all_count; // transitions for all virtual threads are disabled while it is positive
  static volatile bool _SR_mode;                         // there is an active suspender or resumer
  static volatile int _VTMS_transition_count;            // current number of VTMS transitions
  static volatile int _sync_protocol_enabled_count;      // current number of JvmtiVTMSTransitionDisablers enabled sync protocol
  static volatile bool _sync_protocol_enabled_permanently; // seen a suspender: JvmtiVTMSTraansitionDisabler protocol is enabled permanently

  bool _is_SR;                                           // is suspender or resumer
  jthread _thread;                                       // virtual thread to disable transitions for, no-op if it is a platform thread

  DEBUG_ONLY(static void print_info();)
  void VTMS_transition_disable_for_one();
  void VTMS_transition_disable_for_all();
  void VTMS_transition_enable_for_one();
  void VTMS_transition_enable_for_all();

 public:
  static bool _VTMS_notify_jvmti_events;                 // enable notifications from VirtualThread about VTMS events
  static bool VTMS_notify_jvmti_events()             { return _VTMS_notify_jvmti_events; }
  static void set_VTMS_notify_jvmti_events(bool val) { _VTMS_notify_jvmti_events = val; }

  static void set_VTMS_transition_count(bool val)    { _VTMS_transition_count = val; }

  static void inc_sync_protocol_enabled_count()      { Atomic::inc(&_sync_protocol_enabled_count); }
  static void dec_sync_protocol_enabled_count()      { Atomic::dec(&_sync_protocol_enabled_count); }
  static int  sync_protocol_enabled_count()          { return Atomic::load(&_sync_protocol_enabled_count); }
  static bool sync_protocol_enabled_permanently()    { return Atomic::load(&_sync_protocol_enabled_permanently); }

  static bool sync_protocol_enabled()                { return sync_protocol_enabled_permanently() || sync_protocol_enabled_count() > 0; }

  // parameter is_SR: suspender or resumer
  JvmtiVTMSTransitionDisabler(bool is_SR = false);
  JvmtiVTMSTransitionDisabler(jthread thread);
  ~JvmtiVTMSTransitionDisabler();

  // set VTMS transition bit value in JavaThread and java.lang.VirtualThread object
  static void set_is_in_VTMS_transition(JavaThread* thread, jobject vthread, bool in_trans);

  static void start_VTMS_transition(jthread vthread, bool is_mount);
  static void finish_VTMS_transition(jthread vthread, bool is_mount);

  static void VTMS_vthread_start(jobject vthread);
  static void VTMS_vthread_end(jobject vthread);

  static void VTMS_vthread_mount(jobject vthread, bool hide);
  static void VTMS_vthread_unmount(jobject vthread, bool hide);

  static void VTMS_mount_begin(jobject vthread);
  static void VTMS_mount_end(jobject vthread);

  static void VTMS_unmount_begin(jobject vthread, bool last_unmount);
  static void VTMS_unmount_end(jobject vthread);
};

///////////////////////////////////////////////////////////////
//
// class VirtualThreadList
//
// Used for Virtual Threads Suspend/Resume management.
// It's a list of thread IDs.
//
class VirtualThreadList : public GrowableArrayCHeap<int64_t, mtServiceability> {
 public:
  VirtualThreadList() : GrowableArrayCHeap<int64_t, mtServiceability>(0) {}
  void invalidate() { clear(); }
};

///////////////////////////////////////////////////////////////
//
// class JvmtiVTSuspender
//
// Virtual Threads Suspend/Resume management
//
class JvmtiVTSuspender : AllStatic {
 private:
  // Suspend modes for virtual threads
  typedef enum SR_Mode {
    SR_none = 0,
    SR_ind  = 1,
    SR_all  = 2
  } SR_Mode;

  static SR_Mode _SR_mode;
  static VirtualThreadList* _suspended_list;
  static VirtualThreadList* _not_suspended_list;

 public:
  static void register_all_vthreads_suspend();
  static void register_all_vthreads_resume();
  static void register_vthread_suspend(oop vt);
  static void register_vthread_resume(oop vt);
  static bool is_vthread_suspended(oop vt);
  static bool is_vthread_suspended(int64_t thread_id);
};

///////////////////////////////////////////////////////////////
//
// class JvmtiThreadState
//
// The Jvmti state for each thread (across all JvmtiEnv):
// 1. Local table of enabled events.
class JvmtiThreadState : public CHeapObj<mtInternal> {
 private:
  friend class JvmtiEnv;
  JavaThread        *_thread;
  JavaThread        *_thread_saved;
  OopHandle         _thread_oop_h;
  // Jvmti Events that cannot be posted in their current context.
  JvmtiDeferredEventQueue* _jvmti_event_queue;
  bool              _is_virtual;            // state belongs to a virtual thread
  bool              _hide_single_stepping;
  bool              _pending_interp_only_mode;
  bool              _pending_step_for_popframe;
  bool              _pending_step_for_earlyret;
  int               _hide_level;

 public:
  enum ExceptionState {
    ES_CLEARED,
    ES_DETECTED,
    ES_CAUGHT
  };

 private:
  ExceptionState _exception_state;

  // Used to send class being redefined/retransformed and kind of transform
  // info to the class file load hook event handler.
  Klass*                _class_being_redefined;
  JvmtiClassLoadKind    _class_load_kind;
  GrowableArray<Klass*>* _classes_being_redefined;

  // This is only valid when is_interp_only_mode() returns true
  int               _cur_stack_depth;
  int               _saved_interp_only_mode;

  JvmtiThreadEventEnable _thread_event_enable;

  // for support of JvmtiEnvThreadState
  JvmtiEnvThreadState*   _head_env_thread_state;

  // doubly-linked linear list of active thread state
  // needed in order to iterate the list without holding Threads_lock
  static JvmtiThreadState *_head;
  JvmtiThreadState *_next;
  JvmtiThreadState *_prev;

  // holds the current dynamic code event collector, null if no event collector in use
  JvmtiDynamicCodeEventCollector* _dynamic_code_event_collector;
  // holds the current vm object alloc event collector, null if no event collector in use
  JvmtiVMObjectAllocEventCollector* _vm_object_alloc_event_collector;
  // holds the current sampled object alloc event collector, null if no event collector in use
  JvmtiSampledObjectAllocEventCollector* _sampled_object_alloc_event_collector;

  // Should only be created by factory methods
  JvmtiThreadState(JavaThread *thread, oop thread_oop);

  friend class JvmtiEnvThreadStateIterator;
  inline JvmtiEnvThreadState* head_env_thread_state();
  inline void set_head_env_thread_state(JvmtiEnvThreadState* ets);

  static bool _seen_interp_only_mode; // interp_only_mode was requested at least once

 public:
  ~JvmtiThreadState();

  // is event_type enabled and usable for this thread in any environment?
  bool is_enabled(jvmtiEvent event_type) {
    return _thread_event_enable.is_enabled(event_type);
  }

  JvmtiThreadEventEnable *thread_event_enable() {
    return &_thread_event_enable;
  }

  // Must only be called in situations where the state is for the current thread and
  // the environment can not go away.  To be safe, the returned JvmtiEnvThreadState
  // must be used in such a way as there can be no intervening safepoints.
  inline JvmtiEnvThreadState* env_thread_state(JvmtiEnvBase *env);

  static void periodic_clean_up();

  // Return true if any thread has entered interp_only_mode at any point during the JVMs execution.
  static bool seen_interp_only_mode() {
    return _seen_interp_only_mode;
  }

  void add_env(JvmtiEnvBase *env);

  // The pending_interp_only_mode is set when the interp_only_mode is triggered.
  // It is cleared by EnterInterpOnlyModeClosure handshake.
  bool is_pending_interp_only_mode() { return _pending_interp_only_mode; }
  void set_pending_interp_only_mode(bool val) { _pending_interp_only_mode = val; }

  // Used by the interpreter for fullspeed debugging support
  bool is_interp_only_mode()                {
    return _thread == nullptr ?  _saved_interp_only_mode != 0 : _thread->is_interp_only_mode();
  }
  void enter_interp_only_mode();
  void leave_interp_only_mode();

  static void unbind_from(JvmtiThreadState* state, JavaThread* thread);
  static void bind_to(JvmtiThreadState* state, JavaThread* thread);

  // access to the linked list of all JVMTI thread states
  static JvmtiThreadState *first() {
    assert(Threads::number_of_threads() == 0 || JvmtiThreadState_lock->is_locked(), "sanity check");
    return _head;
  }

  JvmtiThreadState *next()                  {
    return _next;
  }

  // Current stack depth is only valid when is_interp_only_mode() returns true.
  // These functions should only be called at a safepoint - usually called from same thread.
  // Returns the number of Java activations on the stack.
  int cur_stack_depth();
  void invalidate_cur_stack_depth();
  void incr_cur_stack_depth();
  void decr_cur_stack_depth();

  int count_frames();

  inline JavaThread *get_thread()      { return _thread;              }
  inline JavaThread *get_thread_or_saved(); // return _thread_saved if _thread is null

  // Needed for virtual threads as they can migrate to different JavaThread's.
  // Also used for carrier threads to clear/restore _thread.
  void set_thread(JavaThread* thread);
  oop get_thread_oop();

  inline bool is_virtual() { return _is_virtual; } // the _thread is virtual

  inline bool is_exception_detected()  { return _exception_state == ES_DETECTED;  }
  inline bool is_exception_caught()    { return _exception_state == ES_CAUGHT;  }

  inline void set_exception_detected() { _exception_state = ES_DETECTED; }
  inline void set_exception_caught()   { _exception_state = ES_CAUGHT; }

  inline void clear_exception_state() { _exception_state = ES_CLEARED; }

  // We need to save and restore exception state inside JvmtiEventMark
  inline ExceptionState get_exception_state() { return _exception_state; }
  inline void restore_exception_state(ExceptionState state) { _exception_state = state; }

  inline void clear_hide_single_stepping() {
    if (_hide_level > 0) {
      _hide_level--;
    } else {
      assert(_hide_single_stepping, "hide_single_stepping is out of phase");
      _hide_single_stepping = false;
    }
  }
  inline bool hide_single_stepping() { return _hide_single_stepping; }
  inline void set_hide_single_stepping() {
    if (_hide_single_stepping) {
      _hide_level++;
    } else {
      assert(_hide_level == 0, "hide_level is out of phase");
      _hide_single_stepping = true;
    }
  }

  // Step pending flag is set when PopFrame is called and it is cleared
  // when step for the Pop Frame is completed.
  // This logic is used to distinguish b/w step for pop frame and repeat step.
  void set_pending_step_for_popframe() { _pending_step_for_popframe = true;  }
  void clr_pending_step_for_popframe() { _pending_step_for_popframe = false; }
  bool is_pending_step_for_popframe()  { return _pending_step_for_popframe;  }
  void process_pending_step_for_popframe();

  // Step pending flag is set when ForceEarlyReturn is called and it is cleared
  // when step for the ForceEarlyReturn is completed.
  // This logic is used to distinguish b/w step for early return and repeat step.
  void set_pending_step_for_earlyret() { _pending_step_for_earlyret = true;  }
  void clr_pending_step_for_earlyret() { _pending_step_for_earlyret = false; }
  bool is_pending_step_for_earlyret()  { return _pending_step_for_earlyret;  }
  void process_pending_step_for_earlyret();

  // Setter and getter method is used to send redefined class info
  // when class file load hook event is posted.
  // It is set while loading redefined class and cleared before the
  // class file load hook event is posted.
  inline void set_class_being_redefined(Klass* k, JvmtiClassLoadKind kind) {
    _class_being_redefined = k;
    _class_load_kind = kind;
  }

  inline void clear_class_being_redefined() {
    _class_being_redefined = nullptr;
    _class_load_kind = jvmti_class_load_kind_load;
  }

  inline Klass* get_class_being_redefined() {
    return _class_being_redefined;
  }

  inline JvmtiClassLoadKind get_class_load_kind() {
    return _class_load_kind;
  }

  // Get the classes that are currently being redefined by this thread.
  inline GrowableArray<Klass*>* get_classes_being_redefined() {
    return _classes_being_redefined;
  }

  inline void set_classes_being_redefined(GrowableArray<Klass*>* redef_classes) {
    _classes_being_redefined = redef_classes;
  }

  // RedefineClasses support
  // The bug 6214132 caused the verification to fail.
  //
  // What is done at verification:
  //   (This seems to only apply to the old verifier.)
  //   When the verifier makes calls into the VM to ask questions about
  //   the class being verified, it will pass the jclass to JVM_* functions.
  //   The jclass is always pointing to the mirror of _the_class.
  //   ~28 JVM_* functions called by the verifier for the information
  //   about CP entries and klass structure should check the jvmtiThreadState
  //   info about equivalent klass versions and use it to replace a Klass*
  //   of _the_class with a Klass* of _scratch_class. The function
  //   class_to_verify_considering_redefinition() must be called for it.
  //
  //   Note again, that this redirection happens only for the verifier thread.
  //   Other threads have very small overhead by checking the existence
  //   of the jvmtiThreadSate and the information about klasses equivalence.
  //   No JNI functions need to be changed, they don't reference the klass guts.
  //   The JavaThread pointer is already available in all JVM_* functions
  //   used by the verifier, so there is no extra performance issue with it.

 private:
  Klass* _the_class_for_redefinition_verification;
  Klass* _scratch_class_for_redefinition_verification;

 public:
  inline void set_class_versions_map(Klass* the_class,
                                     Klass* scratch_class) {
    _the_class_for_redefinition_verification = the_class;
    _scratch_class_for_redefinition_verification = scratch_class;
  }

  inline void clear_class_versions_map() { set_class_versions_map(nullptr, nullptr); }

  static inline
  Klass* class_to_verify_considering_redefinition(Klass* klass,
                                                    JavaThread *thread) {
    JvmtiThreadState *state = thread->jvmti_thread_state();
    if (state != nullptr && state->_the_class_for_redefinition_verification != nullptr) {
      if (state->_the_class_for_redefinition_verification == klass) {
        klass = state->_scratch_class_for_redefinition_verification;
      }
    }
    return klass;
  }

  // Todo: get rid of this!
 private:
  bool _debuggable;
 public:
  // Should the thread be enumerated by jvmtiInternal::GetAllThreads?
  bool is_debuggable()                 { return _debuggable; }
  // If a thread cannot be suspended (has no valid last_java_frame) then it gets marked !debuggable
  void set_debuggable(bool debuggable) { _debuggable = debuggable; }

 public:

  // Thread local event collector setter and getter methods.
  JvmtiDynamicCodeEventCollector* get_dynamic_code_event_collector() {
    return _dynamic_code_event_collector;
  }
  JvmtiVMObjectAllocEventCollector* get_vm_object_alloc_event_collector() {
    return _vm_object_alloc_event_collector;
  }
  JvmtiSampledObjectAllocEventCollector* get_sampled_object_alloc_event_collector() {
    return _sampled_object_alloc_event_collector;
  }
  void set_dynamic_code_event_collector(JvmtiDynamicCodeEventCollector* collector) {
    _dynamic_code_event_collector = collector;
  }
  void set_vm_object_alloc_event_collector(JvmtiVMObjectAllocEventCollector* collector) {
    _vm_object_alloc_event_collector = collector;
  }
  void set_sampled_object_alloc_event_collector(JvmtiSampledObjectAllocEventCollector* collector) {
    _sampled_object_alloc_event_collector = collector;
  }


  //
  // Frame routines
  //

 public:

  //  true when the thread was suspended with a pointer to the last Java frame.
  bool has_last_frame()                     { return _thread->has_last_Java_frame(); }

  void update_for_pop_top_frame();

  // already holding JvmtiThreadState_lock - retrieve or create JvmtiThreadState
  // Can return null if JavaThread is exiting.
  // Callers are responsible to call recompute_thread_filtered() to update event bits
  // if thread-filtered events are enabled globally.
  static JvmtiThreadState *state_for_while_locked(JavaThread *thread, oop thread_oop = nullptr);
  // retrieve or create JvmtiThreadState
  // Can return null if JavaThread is exiting.
  // Calls recompute_thread_filtered() to update event bits if thread-filtered events are enabled globally.
  static JvmtiThreadState *state_for(JavaThread *thread, Handle thread_handle = Handle());

  // JVMTI ForceEarlyReturn support

  // This is set to earlyret_pending to signal that top Java frame
  // should be returned immediately
 public:
  int           _earlyret_state;
  TosState      _earlyret_tos;
  jvalue        _earlyret_value;
  oop           _earlyret_oop;         // Used to return an oop result into Java code from
                                       // ForceEarlyReturnObject, GC-preserved

  // Setting and clearing earlyret_state
  // earlyret_pending indicates that a ForceEarlyReturn() has been
  // requested and not yet been completed.
 public:
  enum EarlyretState {
    earlyret_inactive = 0,
    earlyret_pending  = 1
  };

  void set_earlyret_pending(void) { _earlyret_state = earlyret_pending;  }
  void clr_earlyret_pending(void) { _earlyret_state = earlyret_inactive; }
  bool is_earlyret_pending(void)  { return (_earlyret_state == earlyret_pending);  }

  TosState earlyret_tos()                            { return _earlyret_tos; }
  oop  earlyret_oop() const                          { return _earlyret_oop; }
  void set_earlyret_oop (oop x)                      { _earlyret_oop = x;    }
  jvalue earlyret_value()                            { return _earlyret_value; }
  void set_earlyret_value(jvalue val, TosState tos)  { _earlyret_tos = tos;  _earlyret_value = val;  }
  void clr_earlyret_value()                          { _earlyret_tos = ilgl; _earlyret_value.j = 0L; }

  static ByteSize earlyret_state_offset() { return byte_offset_of(JvmtiThreadState, _earlyret_state); }
  static ByteSize earlyret_tos_offset()   { return byte_offset_of(JvmtiThreadState, _earlyret_tos); }
  static ByteSize earlyret_oop_offset()   { return byte_offset_of(JvmtiThreadState, _earlyret_oop); }
  static ByteSize earlyret_value_offset() { return byte_offset_of(JvmtiThreadState, _earlyret_value); }

  void oops_do(OopClosure* f, CodeBlobClosure* cf) NOT_JVMTI_RETURN; // GC support
  void nmethods_do(CodeBlobClosure* cf) NOT_JVMTI_RETURN;

public:
  void set_should_post_on_exceptions(bool val);

  // Thread local event queue, which doesn't require taking the Service_lock.
  void enqueue_event(JvmtiDeferredEvent* event) NOT_JVMTI_RETURN;
  void post_events(JvmtiEnv* env);
  void run_nmethod_entry_barriers();
};

#endif // SHARE_PRIMS_JVMTITHREADSTATE_HPP

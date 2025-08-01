/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/javaClasses.inline.hpp"
#include "jvmtifiles/jvmtiEnv.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oopHandle.inline.hpp"
#include "prims/jvmtiEnvBase.hpp"
#include "prims/jvmtiEventController.inline.hpp"
#include "prims/jvmtiImpl.hpp"
#include "prims/jvmtiThreadState.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/vframe.hpp"

// marker for when the stack depth has been reset and is now unknown.
// any negative number would work but small ones might obscure an
// underrun error.
static const int UNKNOWN_STACK_DEPTH = -99;

///////////////////////////////////////////////////////////////
//
// class JvmtiThreadState
//
// Instances of JvmtiThreadState hang off of each thread.
// Thread local storage for JVMTI.
//

JvmtiThreadState *JvmtiThreadState::_head = nullptr;
bool JvmtiThreadState::_seen_interp_only_mode = false;

JvmtiThreadState::JvmtiThreadState(JavaThread* thread, oop thread_oop)
  : _thread_event_enable() {
  assert(JvmtiThreadState_lock->is_locked(), "sanity check");
  _thread               = thread;
  _thread_saved         = nullptr;
  _exception_state      = ES_CLEARED;
  _debuggable           = true;
  _hide_single_stepping = false;
  _pending_interp_only_mode = false;
  _hide_level           = 0;
  _pending_step_for_popframe = false;
  _class_being_redefined = nullptr;
  _class_load_kind = jvmti_class_load_kind_load;
  _classes_being_redefined = nullptr;
  _head_env_thread_state = nullptr;
  _dynamic_code_event_collector = nullptr;
  _vm_object_alloc_event_collector = nullptr;
  _sampled_object_alloc_event_collector = nullptr;
  _the_class_for_redefinition_verification = nullptr;
  _scratch_class_for_redefinition_verification = nullptr;
  _cur_stack_depth = UNKNOWN_STACK_DEPTH;
  _saved_interp_only_mode = false;

  // JVMTI ForceEarlyReturn support
  _pending_step_for_earlyret = false;
  _earlyret_state = earlyret_inactive;
  _earlyret_tos = ilgl;
  _earlyret_value.j = 0L;
  _earlyret_oop = nullptr;
  _jvmti_event_queue = nullptr;
  _top_frame_is_exiting = false;
  _is_virtual = false;

  _thread_oop_h = OopHandle(JvmtiExport::jvmti_oop_storage(), thread_oop);

  // add all the JvmtiEnvThreadState to the new JvmtiThreadState
  {
    JvmtiEnvIterator it;
    for (JvmtiEnvBase* env = it.first(); env != nullptr; env = it.next(env)) {
      if (env->is_valid()) {
        add_env(env);
      }
    }
  }

  // link us into the list
  {
    // The thread state list manipulation code must not have safepoints.
    // See periodic_clean_up().
    DEBUG_ONLY(NoSafepointVerifier nosafepoint;)

    _prev = nullptr;
    _next = _head;
    if (_head != nullptr) {
      _head->_prev = this;
    }
    _head = this;
  }

  if (thread_oop != nullptr) {
    java_lang_Thread::set_jvmti_thread_state(thread_oop, this);
    _is_virtual = java_lang_VirtualThread::is_instance(thread_oop);
  }

  if (thread != nullptr) {
    if (thread_oop == nullptr || thread->jvmti_vthread() == nullptr || thread->jvmti_vthread() == thread_oop) {
      // The JavaThread for carrier or mounted virtual thread case.
      // Set this only if thread_oop is current thread->jvmti_vthread().
      thread->set_jvmti_thread_state(this);
    }
    thread->set_interp_only_mode(false);
  }
}


JvmtiThreadState::~JvmtiThreadState()   {
  assert(JvmtiThreadState_lock->is_locked(), "sanity check");

  if (_classes_being_redefined != nullptr) {
    delete _classes_being_redefined; // free the GrowableArray on C heap
  }

  // clear this as the state for the thread
  get_thread()->set_jvmti_thread_state(nullptr);

  // zap our env thread states
  {
    JvmtiEnvBase::entering_dying_thread_env_iteration();
    JvmtiEnvThreadStateIterator it(this);
    for (JvmtiEnvThreadState* ets = it.first(); ets != nullptr; ) {
      JvmtiEnvThreadState* zap = ets;
      ets = it.next(ets);
      delete zap;
    }
    JvmtiEnvBase::leaving_dying_thread_env_iteration();
  }

  // remove us from the list
  {
    // The thread state list manipulation code must not have safepoints.
    // See periodic_clean_up().
    DEBUG_ONLY(NoSafepointVerifier nosafepoint;)

    if (_prev == nullptr) {
      assert(_head == this, "sanity check");
      _head = _next;
    } else {
      assert(_head != this, "sanity check");
      _prev->_next = _next;
    }
    if (_next != nullptr) {
      _next->_prev = _prev;
    }
    _next = nullptr;
    _prev = nullptr;
  }
  if (get_thread_oop() != nullptr) {
    java_lang_Thread::set_jvmti_thread_state(get_thread_oop(), nullptr);
  }
  _thread_oop_h.release(JvmtiExport::jvmti_oop_storage());
}


void
JvmtiThreadState::periodic_clean_up() {
  assert(SafepointSynchronize::is_at_safepoint(), "at safepoint");

  // This iteration is initialized with "_head" instead of "JvmtiThreadState::first()"
  // because the latter requires the JvmtiThreadState_lock.
  // This iteration is safe at a safepoint as well, see the NoSafepointVerifier
  // asserts at all list manipulation sites.
  for (JvmtiThreadState *state = _head; state != nullptr; state = state->next()) {
    // For each environment thread state corresponding to an invalid environment
    // unlink it from the list and deallocate it.
    JvmtiEnvThreadStateIterator it(state);
    JvmtiEnvThreadState* previous_ets = nullptr;
    JvmtiEnvThreadState* ets = it.first();
    while (ets != nullptr) {
      if (ets->get_env()->is_valid()) {
        previous_ets = ets;
        ets = it.next(ets);
      } else {
        // This one isn't valid, remove it from the list and deallocate it
        JvmtiEnvThreadState* defunct_ets = ets;
        ets = ets->next();
        if (previous_ets == nullptr) {
          assert(state->head_env_thread_state() == defunct_ets, "sanity check");
          state->set_head_env_thread_state(ets);
        } else {
          previous_ets->set_next(ets);
        }
        delete defunct_ets;
      }
    }
  }
}

//
// Virtual Threads Mount State transition (VTMS transition) mechanism
//

// VTMS transitions for one virtual thread are disabled while it is positive
volatile int JvmtiVTMSTransitionDisabler::_VTMS_transition_disable_for_one_count = 0;

// VTMS transitions for all virtual threads are disabled while it is positive
volatile int JvmtiVTMSTransitionDisabler::_VTMS_transition_disable_for_all_count = 0;

// There is an active suspender or resumer.
volatile bool JvmtiVTMSTransitionDisabler::_SR_mode = false;

// Notifications from VirtualThread about VTMS events are enabled.
bool JvmtiVTMSTransitionDisabler::_VTMS_notify_jvmti_events = false;

// The JvmtiVTMSTransitionDisabler sync protocol is enabled if this count > 0.
volatile int JvmtiVTMSTransitionDisabler::_sync_protocol_enabled_count = 0;

// JvmtiVTMSTraansitionDisabler sync protocol is enabled permanently after seeing a suspender.
volatile bool JvmtiVTMSTransitionDisabler::_sync_protocol_enabled_permanently = false;

#ifdef ASSERT
void
JvmtiVTMSTransitionDisabler::print_info() {
  log_error(jvmti)("_VTMS_transition_disable_for_one_count: %d\n", _VTMS_transition_disable_for_one_count);
  log_error(jvmti)("_VTMS_transition_disable_for_all_count: %d\n\n", _VTMS_transition_disable_for_all_count);
  int attempts = 10000;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *java_thread = jtiwh.next(); ) {
    if (java_thread->VTMS_transition_mark()) {
      log_error(jvmti)("jt: %p VTMS_transition_mark: %d\n",
                       (void*)java_thread, java_thread->VTMS_transition_mark());
    }
    ResourceMark rm;
    // Handshake with target.
    PrintStackTraceClosure pstc;
    Handshake::execute(&pstc, java_thread);
  }
}
#endif

// disable VTMS transitions for one virtual thread
// disable VTMS transitions for all threads if thread is nullptr or a platform thread
JvmtiVTMSTransitionDisabler::JvmtiVTMSTransitionDisabler(jthread thread)
  : _is_SR(false),
    _is_virtual(false),
    _is_self(false),
    _thread(thread)
{
  if (!Continuations::enabled()) {
    return; // JvmtiVTMSTransitionDisabler is no-op without virtual threads
  }
  if (Thread::current_or_null() == nullptr) {
    return;  // Detached thread, can be a call from Agent_OnLoad.
  }
  JavaThread* current = JavaThread::current();
  oop thread_oop = JNIHandles::resolve_external_guard(thread);
  _is_virtual = java_lang_VirtualThread::is_instance(thread_oop);

  if (thread == nullptr ||
      (!_is_virtual && thread_oop == current->threadObj()) ||
      (_is_virtual && thread_oop == current->vthread())) {
    _is_self = true;
    return; // no need for current thread to disable and enable transitions for itself
  }
  if (!sync_protocol_enabled_permanently()) {
    JvmtiVTMSTransitionDisabler::inc_sync_protocol_enabled_count();
  }

  // Target can be virtual or platform thread.
  // If target is a platform thread then we have to disable VTMS transitions for all threads.
  // It is by several reasons:
  // - carrier threads can mount virtual threads which may cause incorrect behavior
  // - there is no mechanism to disable transitions for a specific carrier thread yet
  if (_is_virtual) {
    VTMS_transition_disable_for_one(); // disable VTMS transitions for one virtual thread
  } else {
    VTMS_transition_disable_for_all(); // disable VTMS transitions for all virtual threads
  }
}

// disable VTMS transitions for all virtual threads
JvmtiVTMSTransitionDisabler::JvmtiVTMSTransitionDisabler(bool is_SR)
  : _is_SR(is_SR),
    _is_virtual(false),
    _is_self(false),
    _thread(nullptr)
{
  if (!Continuations::enabled()) {
    return; // JvmtiVTMSTransitionDisabler is no-op without virtual threads
  }
  if (Thread::current_or_null() == nullptr) {
    return;  // Detached thread, can be a call from Agent_OnLoad.
  }
  if (!sync_protocol_enabled_permanently()) {
    JvmtiVTMSTransitionDisabler::inc_sync_protocol_enabled_count();
    if (is_SR) {
      Atomic::store(&_sync_protocol_enabled_permanently, true);
    }
  }
  VTMS_transition_disable_for_all();
}

JvmtiVTMSTransitionDisabler::~JvmtiVTMSTransitionDisabler() {
  if (!Continuations::enabled()) {
    return; // JvmtiVTMSTransitionDisabler is a no-op without virtual threads
  }
  if (Thread::current_or_null() == nullptr) {
    return;  // Detached thread, can be a call from Agent_OnLoad.
  }
  if (_is_self) {
    return; // no need for current thread to disable and enable transitions for itself
  }
  if (_is_virtual) {
    VTMS_transition_enable_for_one(); // enable VTMS transitions for one virtual thread
  } else {
    VTMS_transition_enable_for_all(); // enable VTMS transitions for all virtual threads
  }
  if (!sync_protocol_enabled_permanently()) {
    JvmtiVTMSTransitionDisabler::dec_sync_protocol_enabled_count();
  }
}

// disable VTMS transitions for one virtual thread
void
JvmtiVTMSTransitionDisabler::VTMS_transition_disable_for_one() {
  assert(_thread != nullptr, "sanity check");
  JavaThread* thread = JavaThread::current();
  HandleMark hm(thread);
  Handle vth = Handle(thread, JNIHandles::resolve_external_guard(_thread));
  assert(java_lang_VirtualThread::is_instance(vth()), "sanity check");

  MonitorLocker ml(JvmtiVTMSTransition_lock);

  while (_SR_mode) { // suspender or resumer is a JvmtiVTMSTransitionDisabler monopolist
    ml.wait(10); // wait while there is an active suspender or resumer
  }
  Atomic::inc(&_VTMS_transition_disable_for_one_count);
  java_lang_Thread::inc_VTMS_transition_disable_count(vth());

  while (java_lang_Thread::is_in_VTMS_transition(vth())) {
    ml.wait(10); // wait while the virtual thread is in transition
  }
#ifdef ASSERT
  thread->set_is_VTMS_transition_disabler(true);
#endif
}

// disable VTMS transitions for all virtual threads
void
JvmtiVTMSTransitionDisabler::VTMS_transition_disable_for_all() {
  JavaThread* thread = JavaThread::current();
  int attempts = 50000;
  {
    MonitorLocker ml(JvmtiVTMSTransition_lock);

    assert(!thread->is_in_VTMS_transition(), "VTMS_transition sanity check");
    while (_SR_mode) { // Suspender or resumer is a JvmtiVTMSTransitionDisabler monopolist.
      ml.wait(10);     // Wait while there is an active suspender or resumer.
    }
    if (_is_SR) {
      _SR_mode = true;
      while (_VTMS_transition_disable_for_all_count > 0 ||
             _VTMS_transition_disable_for_one_count > 0) {
        ml.wait(10);   // Wait while there is any active jvmtiVTMSTransitionDisabler.
      }
    }
    Atomic::inc(&_VTMS_transition_disable_for_all_count);

    // Block while some mount/unmount transitions are in progress.
    // Debug version fails and prints diagnostic information.
    for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
      while (jt->VTMS_transition_mark()) {
        if (ml.wait(10)) {
          attempts--;
        }
        DEBUG_ONLY(if (attempts == 0) break;)
      }
    }
    assert(!thread->is_VTMS_transition_disabler(), "VTMS_transition sanity check");
#ifdef ASSERT
    if (attempts > 0) {
      thread->set_is_VTMS_transition_disabler(true);
    }
#endif
  }
#ifdef ASSERT
    if (attempts == 0) {
      print_info();
      fatal("stuck in JvmtiVTMSTransitionDisabler::VTMS_transition_disable");
    }
#endif
}

// enable VTMS transitions for one virtual thread
void
JvmtiVTMSTransitionDisabler::VTMS_transition_enable_for_one() {
  JavaThread* thread = JavaThread::current();
  HandleMark hm(thread);
  Handle vth = Handle(thread, JNIHandles::resolve_external_guard(_thread));
  if (!java_lang_VirtualThread::is_instance(vth())) {
    return; // no-op if _thread is not a virtual thread
  }
  MonitorLocker ml(JvmtiVTMSTransition_lock);
  java_lang_Thread::dec_VTMS_transition_disable_count(vth());
  Atomic::dec(&_VTMS_transition_disable_for_one_count);
  if (_VTMS_transition_disable_for_one_count == 0) {
    ml.notify_all();
  }
#ifdef ASSERT
  thread->set_is_VTMS_transition_disabler(false);
#endif
}

// enable VTMS transitions for all virtual threads
void
JvmtiVTMSTransitionDisabler::VTMS_transition_enable_for_all() {
  JavaThread* current = JavaThread::current();
  {
    MonitorLocker ml(JvmtiVTMSTransition_lock);
    assert(_VTMS_transition_disable_for_all_count > 0, "VTMS_transition sanity check");

    if (_is_SR) {  // Disabler is suspender or resumer.
      _SR_mode = false;
    }
    Atomic::dec(&_VTMS_transition_disable_for_all_count);
    if (_VTMS_transition_disable_for_all_count == 0 || _is_SR) {
      ml.notify_all();
    }
#ifdef ASSERT
    current->set_is_VTMS_transition_disabler(false);
#endif
  }
}

void
JvmtiVTMSTransitionDisabler::start_VTMS_transition(jthread vthread, bool is_mount) {
  JavaThread* thread = JavaThread::current();
  oop vt = JNIHandles::resolve_external_guard(vthread);
  assert(!thread->is_in_VTMS_transition(), "VTMS_transition sanity check");

  // Avoid using MonitorLocker on performance critical path, use
  // two-level synchronization with lock-free operations on state bits.
  assert(!thread->VTMS_transition_mark(), "sanity check");
  thread->set_VTMS_transition_mark(true); // Try to enter VTMS transition section optmistically.
  java_lang_Thread::set_is_in_VTMS_transition(vt, true);

  if (!sync_protocol_enabled()) {
    thread->set_is_in_VTMS_transition(true);
    return;
  }
  HandleMark hm(thread);
  Handle vth = Handle(thread, vt);
  int attempts = 50000;

  // Do not allow suspends inside VTMS transitions.
  // Block while transitions are disabled or there are suspend requests.
  int64_t thread_id = java_lang_Thread::thread_id(vth());  // Cannot use oops while blocked.

  if (_VTMS_transition_disable_for_all_count > 0 ||
      java_lang_Thread::VTMS_transition_disable_count(vth()) > 0 ||
      thread->is_suspended() ||
      JvmtiVTSuspender::is_vthread_suspended(thread_id)
  ) {
    // Slow path: undo unsuccessful optimistic set of the VTMS_transition_mark.
    // It can cause an extra waiting cycle for VTMS transition disablers.
    thread->set_VTMS_transition_mark(false);
    java_lang_Thread::set_is_in_VTMS_transition(vth(), false);

    while (true) {
      MonitorLocker ml(JvmtiVTMSTransition_lock);

      // Do not allow suspends inside VTMS transitions.
      // Block while transitions are disabled or there are suspend requests.
      if (_VTMS_transition_disable_for_all_count > 0 ||
          java_lang_Thread::VTMS_transition_disable_count(vth()) > 0 ||
          thread->is_suspended() ||
          JvmtiVTSuspender::is_vthread_suspended(thread_id)
      ) {
        // Block while transitions are disabled or there are suspend requests.
        if (ml.wait(200)) {
          attempts--;
        }
        DEBUG_ONLY(if (attempts == 0) break;)
        continue;  // ~ThreadBlockInVM has handshake-based suspend point.
      }
      thread->set_VTMS_transition_mark(true);
      java_lang_Thread::set_is_in_VTMS_transition(vth(), true);
      break;
    }
  }
#ifdef ASSERT
  if (attempts == 0) {
    log_error(jvmti)("start_VTMS_transition: thread->is_suspended: %d is_vthread_suspended: %d\n\n",
                     thread->is_suspended(), JvmtiVTSuspender::is_vthread_suspended(thread_id));
    print_info();
    fatal("stuck in JvmtiVTMSTransitionDisabler::start_VTMS_transition");
  }
#endif
  // Enter VTMS transition section.
  thread->set_is_in_VTMS_transition(true);
}

void
JvmtiVTMSTransitionDisabler::finish_VTMS_transition(jthread vthread, bool is_mount) {
  JavaThread* thread = JavaThread::current();

  assert(thread->is_in_VTMS_transition(), "sanity check");
  thread->set_is_in_VTMS_transition(false);
  oop vt = JNIHandles::resolve_external_guard(vthread);
  java_lang_Thread::set_is_in_VTMS_transition(vt, false);
  assert(thread->VTMS_transition_mark(), "sanity check");
  thread->set_VTMS_transition_mark(false);

  if (!sync_protocol_enabled()) {
    return;
  }
  int64_t thread_id = java_lang_Thread::thread_id(vt);

  // Unblock waiting VTMS transition disablers.
  if (_VTMS_transition_disable_for_one_count > 0 ||
      _VTMS_transition_disable_for_all_count > 0) {
    MonitorLocker ml(JvmtiVTMSTransition_lock);
    ml.notify_all();
  }
  // In unmount case the carrier thread is attached after unmount transition.
  // Check and block it if there was external suspend request.
  int attempts = 10000;
  if (!is_mount && thread->is_carrier_thread_suspended()) {
    while (true) {
      MonitorLocker ml(JvmtiVTMSTransition_lock);

      // Block while there are suspend requests.
      if ((!is_mount && thread->is_carrier_thread_suspended()) ||
          (is_mount && JvmtiVTSuspender::is_vthread_suspended(thread_id))
      ) {
        // Block while there are suspend requests.
        if (ml.wait(200)) {
          attempts--;
        }
        DEBUG_ONLY(if (attempts == 0) break;)
        continue;
      }
      break;
    }
  }
#ifdef ASSERT
  if (attempts == 0) {
    log_error(jvmti)("finish_VTMS_transition: thread->is_suspended: %d is_vthread_suspended: %d\n\n",
                     thread->is_suspended(), JvmtiVTSuspender::is_vthread_suspended(thread_id));
    print_info();
    fatal("stuck in JvmtiVTMSTransitionDisabler::finish_VTMS_transition");
  }
#endif
}

// set VTMS transition bit value in JavaThread and java.lang.VirtualThread object
void JvmtiVTMSTransitionDisabler::set_is_in_VTMS_transition(JavaThread* thread, jobject vthread, bool in_trans) {
  oop vt = JNIHandles::resolve_external_guard(vthread);
  java_lang_Thread::set_is_in_VTMS_transition(vt, in_trans);
  thread->set_is_in_VTMS_transition(in_trans);
}

void
JvmtiVTMSTransitionDisabler::VTMS_vthread_start(jobject vthread) {
  VTMS_mount_end(vthread);
  JavaThread* thread = JavaThread::current();

  assert(!thread->is_in_VTMS_transition(), "sanity check");

  // If interp_only_mode has been enabled then we must eagerly create JvmtiThreadState
  // objects for globally enabled virtual thread filtered events. Otherwise,
  // it is an important optimization to create JvmtiThreadState objects lazily.
  // This optimization is disabled when watchpoint capabilities are present. It is to
  // work around a bug with virtual thread frames which can be not deoptimized in time.
  if (JvmtiThreadState::seen_interp_only_mode() ||
      JvmtiExport::should_post_field_access() ||
      JvmtiExport::should_post_field_modification()){
    JvmtiEventController::thread_started(thread);
  }
  if (JvmtiExport::should_post_vthread_start()) {
    JvmtiExport::post_vthread_start(vthread);
  }
  // post VirtualThreadMount event after VirtualThreadStart
  if (JvmtiExport::should_post_vthread_mount()) {
    JvmtiExport::post_vthread_mount(vthread);
  }
}

void
JvmtiVTMSTransitionDisabler::VTMS_vthread_end(jobject vthread) {
  JavaThread* thread = JavaThread::current();

  assert(!thread->is_in_VTMS_transition(), "sanity check");

  // post VirtualThreadUnmount event before VirtualThreadEnd
  if (JvmtiExport::should_post_vthread_unmount()) {
    JvmtiExport::post_vthread_unmount(vthread);
  }
  if (JvmtiExport::should_post_vthread_end()) {
    JvmtiExport::post_vthread_end(vthread);
  }
  VTMS_unmount_begin(vthread, /* last_unmount */ true);
  if (thread->jvmti_thread_state() != nullptr) {
    JvmtiExport::cleanup_thread(thread);
    assert(thread->jvmti_thread_state() == nullptr, "should be null");
    assert(java_lang_Thread::jvmti_thread_state(JNIHandles::resolve(vthread)) == nullptr, "should be null");
  }
  thread->rebind_to_jvmti_thread_state_of(thread->threadObj());
}

void
JvmtiVTMSTransitionDisabler::VTMS_vthread_mount(jobject vthread, bool hide) {
  if (hide) {
    VTMS_mount_begin(vthread);
  } else {
    VTMS_mount_end(vthread);
    if (JvmtiExport::should_post_vthread_mount()) {
      JvmtiExport::post_vthread_mount(vthread);
    }
  }
}

void
JvmtiVTMSTransitionDisabler::VTMS_vthread_unmount(jobject vthread, bool hide) {
  if (hide) {
    if (JvmtiExport::should_post_vthread_unmount()) {
      JvmtiExport::post_vthread_unmount(vthread);
    }
    VTMS_unmount_begin(vthread, /* last_unmount */ false);
  } else {
    VTMS_unmount_end(vthread);
  }
}

void
JvmtiVTMSTransitionDisabler::VTMS_mount_begin(jobject vthread) {
  JavaThread* thread = JavaThread::current();
  assert(!thread->is_in_VTMS_transition(), "sanity check");
  start_VTMS_transition(vthread, /* is_mount */ true);
}

void
JvmtiVTMSTransitionDisabler::VTMS_mount_end(jobject vthread) {
  JavaThread* thread = JavaThread::current();
  oop vt = JNIHandles::resolve(vthread);

  thread->rebind_to_jvmti_thread_state_of(vt);

  assert(thread->is_in_VTMS_transition(), "sanity check");
  finish_VTMS_transition(vthread, /* is_mount */ true);
}

void
JvmtiVTMSTransitionDisabler::VTMS_unmount_begin(jobject vthread, bool last_unmount) {
  JavaThread* thread = JavaThread::current();

  assert(!thread->is_in_VTMS_transition(), "sanity check");

  start_VTMS_transition(vthread, /* is_mount */ false);
  if (!last_unmount) {
    thread->rebind_to_jvmti_thread_state_of(thread->threadObj());
  }
}

void
JvmtiVTMSTransitionDisabler::VTMS_unmount_end(jobject vthread) {
  JavaThread* thread = JavaThread::current();
  assert(thread->is_in_VTMS_transition(), "sanity check");
  finish_VTMS_transition(vthread, /* is_mount */ false);
}


//
// Virtual Threads Suspend/Resume management
//

JvmtiVTSuspender::SR_Mode
JvmtiVTSuspender::_SR_mode = SR_none;

VirtualThreadList*
JvmtiVTSuspender::_suspended_list = new VirtualThreadList();

VirtualThreadList*
JvmtiVTSuspender::_not_suspended_list = new VirtualThreadList();

void
JvmtiVTSuspender::register_all_vthreads_suspend() {
  MutexLocker ml(JvmtiVThreadSuspend_lock, Mutex::_no_safepoint_check_flag);

  _SR_mode = SR_all;
  _suspended_list->invalidate();
  _not_suspended_list->invalidate();
}

void
JvmtiVTSuspender::register_all_vthreads_resume() {
  MutexLocker ml(JvmtiVThreadSuspend_lock, Mutex::_no_safepoint_check_flag);

  _SR_mode = SR_none;
  _suspended_list->invalidate();
  _not_suspended_list->invalidate();
}

void
JvmtiVTSuspender::register_vthread_suspend(oop vt) {
  int64_t id = java_lang_Thread::thread_id(vt);
  MutexLocker ml(JvmtiVThreadSuspend_lock, Mutex::_no_safepoint_check_flag);

  if (_SR_mode == SR_all) {
    assert(_not_suspended_list->contains(id),
           "register_vthread_suspend sanity check");
    _not_suspended_list->remove(id);
  } else {
    assert(!_suspended_list->contains(id),
           "register_vthread_suspend sanity check");
    _SR_mode = SR_ind;
    _suspended_list->append(id);
  }
}

void
JvmtiVTSuspender::register_vthread_resume(oop vt) {
  int64_t id = java_lang_Thread::thread_id(vt);
  MutexLocker ml(JvmtiVThreadSuspend_lock, Mutex::_no_safepoint_check_flag);

  if (_SR_mode == SR_all) {
    assert(!_not_suspended_list->contains(id),
           "register_vthread_resume sanity check");
    _not_suspended_list->append(id);
  } else if (_SR_mode == SR_ind) {
    assert(_suspended_list->contains(id),
           "register_vthread_resume check");
    _suspended_list->remove(id);
    if (_suspended_list->length() == 0) {
      _SR_mode = SR_none;
    }
  } else {
    assert(false, "register_vthread_resume: no suspend mode enabled");
  }
}

bool
JvmtiVTSuspender::is_vthread_suspended(int64_t thread_id) {
  bool suspend_is_needed =
   (_SR_mode == SR_all && !_not_suspended_list->contains(thread_id)) ||
   (_SR_mode == SR_ind && _suspended_list->contains(thread_id));

  return suspend_is_needed;
}

bool
JvmtiVTSuspender::is_vthread_suspended(oop vt) {
  return is_vthread_suspended(java_lang_Thread::thread_id(vt));
}

void JvmtiThreadState::add_env(JvmtiEnvBase *env) {
  assert(JvmtiThreadState_lock->is_locked(), "sanity check");

  JvmtiEnvThreadState *new_ets = new JvmtiEnvThreadState(this, env);
  // add this environment thread state to the end of the list (order is important)
  {
    // list deallocation (which occurs at a safepoint) cannot occur simultaneously
    DEBUG_ONLY(NoSafepointVerifier nosafepoint;)

    JvmtiEnvThreadStateIterator it(this);
    JvmtiEnvThreadState* previous_ets = nullptr;
    for (JvmtiEnvThreadState* ets = it.first(); ets != nullptr; ets = it.next(ets)) {
      previous_ets = ets;
    }
    if (previous_ets == nullptr) {
      set_head_env_thread_state(new_ets);
    } else {
      previous_ets->set_next(new_ets);
    }
  }
}

void JvmtiThreadState::enter_interp_only_mode() {
  assert(_thread != nullptr, "sanity check");
  assert(!is_interp_only_mode(), "entering interp only when in interp only mode");
  _seen_interp_only_mode = true;
  _thread->set_interp_only_mode(true);
  invalidate_cur_stack_depth();
}

void JvmtiThreadState::leave_interp_only_mode() {
  assert(is_interp_only_mode(), "leaving interp only when not in interp only mode");
  if (_thread == nullptr) {
    // Unmounted virtual thread updates the saved value.
    _saved_interp_only_mode = false;
  } else {
    _thread->set_interp_only_mode(false);
  }
}


// Helper routine used in several places
int JvmtiThreadState::count_frames() {
  JavaThread* thread = get_thread_or_saved();
  javaVFrame *jvf;
  ResourceMark rm;
  if (thread == nullptr) {
    oop thread_obj = get_thread_oop();
    jvf = JvmtiEnvBase::get_vthread_jvf(thread_obj);
  } else {
#ifdef ASSERT
    Thread *current_thread = Thread::current();
#endif
    assert(SafepointSynchronize::is_at_safepoint() ||
           thread->is_handshake_safe_for(current_thread),
           "call by myself / at safepoint / at handshake");
    if (!thread->has_last_Java_frame()) return 0;  // No Java frames.
    // TBD: This might need to be corrected for detached carrier threads.
    RegisterMap reg_map(thread,
                        RegisterMap::UpdateMap::skip,
                        RegisterMap::ProcessFrames::skip,
                        RegisterMap::WalkContinuation::include);
    jvf = thread->last_java_vframe(&reg_map);
    jvf = JvmtiEnvBase::check_and_skip_hidden_frames(thread, jvf);
  }
  return (int)JvmtiEnvBase::get_frame_count(jvf);
}


void JvmtiThreadState::invalidate_cur_stack_depth() {
  assert(SafepointSynchronize::is_at_safepoint() ||
         get_thread()->is_handshake_safe_for(Thread::current()),
         "bad synchronization with owner thread");

  _cur_stack_depth = UNKNOWN_STACK_DEPTH;
}

void JvmtiThreadState::incr_cur_stack_depth() {
  guarantee(JavaThread::current() == get_thread(), "must be current thread");

  if (!is_interp_only_mode()) {
    _cur_stack_depth = UNKNOWN_STACK_DEPTH;
  }
  if (_cur_stack_depth != UNKNOWN_STACK_DEPTH) {
    ++_cur_stack_depth;
  }
}

void JvmtiThreadState::decr_cur_stack_depth() {
  guarantee(JavaThread::current() == get_thread(), "must be current thread");

  if (!is_interp_only_mode()) {
    _cur_stack_depth = UNKNOWN_STACK_DEPTH;
  }
  if (_cur_stack_depth != UNKNOWN_STACK_DEPTH) {
    --_cur_stack_depth;
    assert(_cur_stack_depth >= 0, "incr/decr_cur_stack_depth mismatch");
  }
}

int JvmtiThreadState::cur_stack_depth() {
  Thread *current = Thread::current();
  guarantee(get_thread()->is_handshake_safe_for(current),
            "must be current thread or direct handshake");

  if (!is_interp_only_mode() || _cur_stack_depth == UNKNOWN_STACK_DEPTH) {
    _cur_stack_depth = count_frames();
  } else {
#ifdef ASSERT
    if (EnableJVMTIStackDepthAsserts) {
      // heavy weight assert
      jint num_frames = count_frames();
      assert(_cur_stack_depth == num_frames, "cur_stack_depth out of sync _cur_stack_depth: %d num_frames: %d", _cur_stack_depth, num_frames);
    }
#endif
  }
  return _cur_stack_depth;
}

void JvmtiThreadState::process_pending_step_for_popframe() {
  // We are single stepping as the last part of the PopFrame() dance
  // so we have some house keeping to do.

  JavaThread *thr = get_thread();
  if (thr->popframe_condition() != JavaThread::popframe_inactive) {
    // If the popframe_condition field is not popframe_inactive, then
    // we missed all of the popframe_field cleanup points:
    //
    // - unpack_frames() was not called (nothing to deopt)
    // - remove_activation_preserving_args_entry() was not called
    //   (did not get suspended in a call_vm() family call and did
    //   not complete a call_vm() family call on the way here)
    thr->clear_popframe_condition();
  }

  // clearing the flag indicates we are done with the PopFrame() dance
  clr_pending_step_for_popframe();

  // If exception was thrown in this frame, need to reset jvmti thread state.
  // Single stepping may not get enabled correctly by the agent since
  // exception state is passed in MethodExit event which may be sent at some
  // time in the future. JDWP agent ignores MethodExit events if caused by
  // an exception.
  //
  if (is_exception_detected()) {
    clear_exception_state();
  }
  // If step is pending for popframe then it may not be
  // a repeat step. The new_bci and method_id is same as current_bci
  // and current method_id after pop and step for recursive calls.
  // Force the step by clearing the last location.
  JvmtiEnvThreadStateIterator it(this);
  for (JvmtiEnvThreadState* ets = it.first(); ets != nullptr; ets = it.next(ets)) {
    ets->clear_current_location();
  }
}


// Class:     JvmtiThreadState
// Function:  update_for_pop_top_frame
// Description:
//   This function removes any frame pop notification request for
//   the top frame and invalidates both the current stack depth and
//   all cached frameIDs.
//
// Called by: PopFrame
//
void JvmtiThreadState::update_for_pop_top_frame() {
  if (is_interp_only_mode()) {
    // remove any frame pop notification request for the top frame
    // in any environment
    int popframe_number = cur_stack_depth();
    {
      JvmtiEnvThreadStateIterator it(this);
      for (JvmtiEnvThreadState* ets = it.first(); ets != nullptr; ets = it.next(ets)) {
        if (ets->is_frame_pop(popframe_number)) {
          ets->clear_frame_pop(popframe_number);
        }
      }
    }
    // force stack depth to be recalculated
    invalidate_cur_stack_depth();
  } else {
    assert(!is_enabled(JVMTI_EVENT_FRAME_POP), "Must have no framepops set");
  }
}


void JvmtiThreadState::process_pending_step_for_earlyret() {
  // We are single stepping as the last part of the ForceEarlyReturn
  // dance so we have some house keeping to do.

  if (is_earlyret_pending()) {
    // If the earlyret_state field is not earlyret_inactive, then
    // we missed all of the earlyret_field cleanup points:
    //
    // - remove_activation() was not called
    //   (did not get suspended in a call_vm() family call and did
    //   not complete a call_vm() family call on the way here)
    //
    // One legitimate way for us to miss all the cleanup points is
    // if we got here right after handling a compiled return. If that
    // is the case, then we consider our return from compiled code to
    // complete the ForceEarlyReturn request and we clear the condition.
    clr_earlyret_pending();
    set_earlyret_oop(nullptr);
    clr_earlyret_value();
  }

  // clearing the flag indicates we are done with
  // the ForceEarlyReturn() dance
  clr_pending_step_for_earlyret();

  // If exception was thrown in this frame, need to reset jvmti thread state.
  // Single stepping may not get enabled correctly by the agent since
  // exception state is passed in MethodExit event which may be sent at some
  // time in the future. JDWP agent ignores MethodExit events if caused by
  // an exception.
  //
  if (is_exception_detected()) {
    clear_exception_state();
  }
  // If step is pending for earlyret then it may not be a repeat step.
  // The new_bci and method_id is same as current_bci and current
  // method_id after earlyret and step for recursive calls.
  // Force the step by clearing the last location.
  JvmtiEnvThreadStateIterator it(this);
  for (JvmtiEnvThreadState* ets = it.first(); ets != nullptr; ets = it.next(ets)) {
    ets->clear_current_location();
  }
}

void JvmtiThreadState::oops_do(OopClosure* f, NMethodClosure* cf) {
  f->do_oop((oop*) &_earlyret_oop);

  // Keep nmethods from unloading on the event queue
  if (_jvmti_event_queue != nullptr) {
    _jvmti_event_queue->oops_do(f, cf);
  }
}

void JvmtiThreadState::nmethods_do(NMethodClosure* cf) {
  // Keep nmethods from unloading on the event queue
  if (_jvmti_event_queue != nullptr) {
    _jvmti_event_queue->nmethods_do(cf);
  }
}

// Thread local event queue.
void JvmtiThreadState::enqueue_event(JvmtiDeferredEvent* event) {
  if (_jvmti_event_queue == nullptr) {
    _jvmti_event_queue = new JvmtiDeferredEventQueue();
  }
  // copy the event
  _jvmti_event_queue->enqueue(*event);
}

void JvmtiThreadState::post_events(JvmtiEnv* env) {
  if (_jvmti_event_queue != nullptr) {
    _jvmti_event_queue->post(env);  // deletes each queue node
    delete _jvmti_event_queue;
    _jvmti_event_queue = nullptr;
  }
}

void JvmtiThreadState::run_nmethod_entry_barriers() {
  if (_jvmti_event_queue != nullptr) {
    _jvmti_event_queue->run_nmethod_entry_barriers();
  }
}

oop JvmtiThreadState::get_thread_oop() {
  return _thread_oop_h.resolve();
}

void JvmtiThreadState::update_thread_oop_during_vm_start() {
  assert(_thread->threadObj() != nullptr, "santity check");
  if (get_thread_oop() == nullptr) {
    _thread_oop_h.replace(_thread->threadObj());
  }
}

void JvmtiThreadState::set_thread(JavaThread* thread) {
  _thread_saved = nullptr;  // Common case.
  if (!_is_virtual && thread == nullptr) {
    // Save JavaThread* if carrier thread is being detached.
    _thread_saved = _thread;
  }
  _thread = thread;
}

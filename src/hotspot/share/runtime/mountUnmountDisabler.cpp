/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "prims/jvmtiEventController.hpp"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/mountUnmountDisabler.hpp"
#include "runtime/threadSMR.hpp"

volatile int MountUnmountDisabler::_global_vthread_transition_disable_count = 0;
volatile int MountUnmountDisabler::_active_disablers = 0;
bool MountUnmountDisabler::_exclusive_operation_ongoing = false;
bool MountUnmountDisabler::_notify_jvmti_events = false;

#if INCLUDE_JVMTI
class JVMTIStartTransition : public StackObj {
  JavaThread* _current;
  Handle _vthread;
  bool _is_mount;
  bool _is_thread_end;
 public:
  JVMTIStartTransition(JavaThread* current, oop vthread, bool is_mount, bool is_thread_end) :
    _current(current), _vthread(current, vthread), _is_mount(is_mount), _is_thread_end(is_thread_end) {
    assert(DoJVMTIVirtualThreadTransitions || !JvmtiExport::can_support_virtual_threads(), "sanity check");
    if (DoJVMTIVirtualThreadTransitions && MountUnmountDisabler::notify_jvmti_events()) {
      // post VirtualThreadUnmount event before VirtualThreadEnd
      if (!_is_mount && JvmtiExport::should_post_vthread_unmount()) {
        JvmtiExport::post_vthread_unmount((jthread)_vthread.raw_value());
      }
      if (_is_thread_end && JvmtiExport::should_post_vthread_end()) {
        JvmtiExport::post_vthread_end((jthread)_vthread.raw_value());
      }
    }
  }
  ~JVMTIStartTransition() {
    if (DoJVMTIVirtualThreadTransitions && MountUnmountDisabler::notify_jvmti_events()) {
      if (_is_thread_end && _current->jvmti_thread_state() != nullptr) {
        JvmtiExport::cleanup_thread(_current);
        assert(_current->jvmti_thread_state() == nullptr, "should be null");
        assert(java_lang_Thread::jvmti_thread_state(_vthread()) == nullptr, "should be null");
      }
      if (!_is_mount) {
        _current->rebind_to_jvmti_thread_state_of(_current->threadObj());
      }
    }
  }
};

class JVMTIEndTransition : public StackObj {
  JavaThread* _current;
  Handle _vthread;
  bool _is_mount;
  bool _is_thread_start;
 public:
  JVMTIEndTransition(JavaThread* current, oop vthread, bool is_mount, bool is_thread_start) :
    _current(current), _vthread(current, vthread), _is_mount(is_mount), _is_thread_start(is_thread_start) {
    assert(DoJVMTIVirtualThreadTransitions || !JvmtiExport::can_support_virtual_threads(), "sanity check");
    if (DoJVMTIVirtualThreadTransitions && MountUnmountDisabler::notify_jvmti_events()) {
      if (_is_mount) {
        _current->rebind_to_jvmti_thread_state_of(_vthread());
      }
      DEBUG_ONLY(bool is_virtual = java_lang_VirtualThread::is_instance(_current->jvmti_vthread()));
      assert(_is_mount == is_virtual, "wrong identity");
    }
  }
  ~JVMTIEndTransition() {
    if (DoJVMTIVirtualThreadTransitions && MountUnmountDisabler::notify_jvmti_events()) {
      if (!_is_mount && _current->is_carrier_thread_suspended()) {
        MonitorLocker ml(VThreadTransition_lock);
        while (_current->is_carrier_thread_suspended()) {
          ml.wait(200);
        }
      }

      if (_is_thread_start) {
        // If interp_only_mode has been enabled then we must eagerly create JvmtiThreadState
        // objects for globally enabled virtual thread filtered events. Otherwise,
        // it is an important optimization to create JvmtiThreadState objects lazily.
        // This optimization is disabled when watchpoint capabilities are present. It is to
        // work around a bug with virtual thread frames which can be not deoptimized in time.
        if (JvmtiThreadState::seen_interp_only_mode() ||
            JvmtiExport::should_post_field_access() ||
            JvmtiExport::should_post_field_modification()){
          JvmtiEventController::thread_started(_current);
        }
        if (JvmtiExport::should_post_vthread_start()) {
          JvmtiExport::post_vthread_start((jthread)_vthread.raw_value());
        }
      }
      if (_is_mount && JvmtiExport::should_post_vthread_mount()) {
        JvmtiExport::post_vthread_mount((jthread)_vthread.raw_value());
      }
    }
  }
};
#endif // INCLUDE_JVMTI

bool MountUnmountDisabler::is_start_transition_disabled(JavaThread* thread, oop vthread) {
  // We need to read the per-vthread and global counters to check if transitions are disabled.
  // In case of JVMTI present, the global counter will always be at least 1. This is to force
  // the slow path and check for possible event posting. Here we need to check if transitions
  // are actually disabled, so we compare the global counter against 1 or 0 accordingly.
  // In case of JVMTI we also need to check for suspension.
  int base_disable_count = notify_jvmti_events() ? 1 : 0;
  return java_lang_Thread::vthread_transition_disable_count(vthread) > 0
         || global_vthread_transition_disable_count() > base_disable_count
         JVMTI_ONLY(|| (!thread->is_vthread_transition_disabler() &&
                        (JvmtiVTSuspender::is_vthread_suspended(java_lang_Thread::thread_id(vthread)) || thread->is_suspended())));
}

void MountUnmountDisabler::start_transition(JavaThread* current, oop vthread, bool is_mount, bool is_thread_end) {
  assert(!java_lang_Thread::is_in_vthread_transition(vthread), "");
  assert(!current->is_in_vthread_transition(), "");
  Handle vth = Handle(current, vthread);
  JVMTI_ONLY(JVMTIStartTransition jst(current, vthread, is_mount, is_thread_end);)

  java_lang_Thread::set_is_in_vthread_transition(vth(), true);
  current->set_is_in_vthread_transition(true);

  // Prevent loads of disable conditions from floating up.
  OrderAccess::storeload();

  while (is_start_transition_disabled(current, vth())) {
    java_lang_Thread::set_is_in_vthread_transition(vth(), false);
    current->set_is_in_vthread_transition(false);
    {
      // Block while transitions are disabled
      MonitorLocker ml(VThreadTransition_lock);
      while (is_start_transition_disabled(current, vth())) {
        ml.wait(200);
      }
    }

    // Try to start transition again...
    java_lang_Thread::set_is_in_vthread_transition(vth(), true);
    current->set_is_in_vthread_transition(true);
    OrderAccess::storeload();
  }

  // Start of the critical section. If this is a mount, we need an acquire barrier to
  // synchronize with a possible disabler that executed an operation while this thread
  // was unmounted. We make VirtualThread.mount guarantee such ordering and avoid barriers
  // here. If this is an unmount, the handshake that the disabler executed against this
  // thread already provided the needed synchronization.
  // This pairs with the release barrier in xx_enable_for_one()/xx_enable_for_all().
}

void MountUnmountDisabler::end_transition(JavaThread* current, oop vthread, bool is_mount, bool is_thread_start) {
  assert(java_lang_Thread::is_in_vthread_transition(vthread), "");
  assert(current->is_in_vthread_transition(), "");
  Handle vth = Handle(current, vthread);
  JVMTI_ONLY(JVMTIEndTransition jst(current, vthread, is_mount, is_thread_start);)

  // End of the critical section. If this is an unmount, we need a release barrier before
  // clearing the in_transition flags to make sure any memory operations executed in the
  // transition are visible to a possible disabler that executes while this thread is unmounted.
  // We make VirtualThread.unmount guarantee such ordering and avoid barriers here. If this is
  // a mount, the only thing that needs to be published is the setting of carrierThread, since
  // the handshake that the disabler will execute against it already provides the needed
  // synchronization. This order is already guaranteed by the barriers in VirtualThread.mount.
  // This pairs with the acquire barrier in xx_disable_for_one()/xx_disable_for_all().

  java_lang_Thread::set_is_in_vthread_transition(vth(), false);
  current->set_is_in_vthread_transition(false);

  // Unblock waiting transition disablers.
  if (active_disablers() > 0) {
    MonitorLocker ml(VThreadTransition_lock);
    ml.notify_all();
  }
}

// disable transitions for one virtual thread
// disable transitions for all threads if thread is nullptr or a platform thread
MountUnmountDisabler::MountUnmountDisabler(jthread thread)
  : MountUnmountDisabler(JNIHandles::resolve_external_guard(thread))
{
}

// disable transitions for one virtual thread
// disable transitions for all threads if thread is nullptr or a platform thread
MountUnmountDisabler::MountUnmountDisabler(oop thread_oop)
  : _is_exclusive(false),
    _is_self(false)
{
  if (!Continuations::enabled()) {
    return; // MountUnmountDisabler is no-op without virtual threads
  }
  if (Thread::current_or_null() == nullptr) {
    return;  // Detached thread, can be a call from Agent_OnLoad.
  }
  JavaThread* current = JavaThread::current();
  assert(!current->is_in_vthread_transition(), "");

  bool is_virtual = java_lang_VirtualThread::is_instance(thread_oop);
  if (thread_oop == nullptr ||
      (!is_virtual && thread_oop == current->threadObj()) ||
      (is_virtual && thread_oop == current->vthread())) {
    _is_self = true;
    return; // no need for current thread to disable and enable transitions for itself
  }

  // Target can be virtual or platform thread.
  // If target is a platform thread then we have to disable transitions for all threads.
  // It is by several reasons:
  // - carrier threads can mount virtual threads which may cause incorrect behavior
  // - there is no mechanism to disable transitions for a specific carrier thread yet
  if (is_virtual) {
    _vthread = Handle(current, thread_oop);
    disable_transition_for_one(); // disable transitions for one virtual thread
  } else {
    disable_transition_for_all(); // disable transitions for all virtual threads
  }
}

// disable transitions for all virtual threads
MountUnmountDisabler::MountUnmountDisabler(bool exclusive)
  : _is_exclusive(exclusive),
    _is_self(false)
{
  if (!Continuations::enabled()) {
    return; // MountUnmountDisabler is no-op without virtual threads
  }
  if (Thread::current_or_null() == nullptr) {
    return;  // Detached thread, can be a call from Agent_OnLoad.
  }
  assert(!JavaThread::current()->is_in_vthread_transition(), "");
  disable_transition_for_all();
}

MountUnmountDisabler::~MountUnmountDisabler() {
  if (!Continuations::enabled()) {
    return; // MountUnmountDisabler is a no-op without virtual threads
  }
  if (Thread::current_or_null() == nullptr) {
    return;  // Detached thread, can be a call from Agent_OnLoad.
  }
  if (_is_self) {
    return; // no need for current thread to disable and enable transitions for itself
  }
  if (_vthread() != nullptr) {
    enable_transition_for_one(); // enable transitions for one virtual thread
  } else {
    enable_transition_for_all(); // enable transitions for all virtual threads
  }
}

// disable transitions for one virtual thread
void
MountUnmountDisabler::disable_transition_for_one() {
  MonitorLocker ml(VThreadTransition_lock);
  while (exclusive_operation_ongoing()) {
    ml.wait(10);
  }

  inc_active_disablers();
  java_lang_Thread::inc_vthread_transition_disable_count(_vthread());

  // Prevent load of transition flag from floating up.
  OrderAccess::storeload();

  while (java_lang_Thread::is_in_vthread_transition(_vthread())) {
    ml.wait(10); // wait while the virtual thread is in transition
  }

  // Start of the critical section. If the target is unmounted, we need an acquire
  // barrier to make sure memory operations executed in the last transition are visible.
  // If the target is mounted, although the handshake that will be executed against it
  // already provides the needed synchronization, we still need to prevent the load of
  // carrierThread to float up.
  // This pairs with the release barrier in end_transition().
  OrderAccess::acquire();
  JVMTI_ONLY(JavaThread::current()->set_is_vthread_transition_disabler(true);)
}

// disable transitions for all virtual threads
void
MountUnmountDisabler::disable_transition_for_all() {
  DEBUG_ONLY(JavaThread* thread = JavaThread::current();)
  DEBUG_ONLY(thread->set_is_disabler_at_start(true);)

  MonitorLocker ml(VThreadTransition_lock);
  while (exclusive_operation_ongoing()) {
    ml.wait(10);
  }
  if (_is_exclusive) {
    set_exclusive_operation_ongoing(true);
    while (active_disablers() > 0) {
      ml.wait(10);
    }
  }
  inc_active_disablers();
  inc_global_vthread_transition_disable_count();

  // Prevent loads of transition flag from floating up. Technically not
  // required since JavaThreadIteratorWithHandle includes full fence.
  OrderAccess::storeload();

  // Block while some mount/unmount transitions are in progress.
  // Debug version fails and prints diagnostic information.
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *jt = jtiwh.next(); ) {
    while (jt->is_in_vthread_transition()) {
      ml.wait(10);
    }
  }

  // Start of the critical section. If some target is unmounted, we need an acquire
  // barrier to make sure memory operations executed in the last transition are visible.
  // If a target is mounted, although the handshake that will be executed against it
  // already provides the needed synchronization, we still need to prevent the load of
  // carrierThread to float up.
  // This pairs with the release barrier in end_transition().
  OrderAccess::acquire();
  JVMTI_ONLY(JavaThread::current()->set_is_vthread_transition_disabler(true);)
  DEBUG_ONLY(thread->set_is_disabler_at_start(false);)
}

// enable transitions for one virtual thread
void
MountUnmountDisabler::enable_transition_for_one() {
  assert(java_lang_VirtualThread::is_instance(_vthread()), "");

  // End of the critical section. If the target was unmounted, we need a
  // release barrier before decrementing _vthread_transition_disable_count to
  // make sure any memory operations executed by the disabler are visible to
  // the target once it mounts again. If the target was mounted, the handshake
  // executed against it already provided the needed synchronization.
  // This pairs with the equivalent acquire barrier in start_transition().
  OrderAccess::release();

  MonitorLocker ml(VThreadTransition_lock);
  dec_active_disablers();
  java_lang_Thread::dec_vthread_transition_disable_count(_vthread());
  if (java_lang_Thread::vthread_transition_disable_count(_vthread()) == 0) {
    ml.notify_all();
  }
  JVMTI_ONLY(JavaThread::current()->set_is_vthread_transition_disabler(false);)
}

// enable transitions for all virtual threads
void
MountUnmountDisabler::enable_transition_for_all() {
  // End of the critical section. If some target was unmounted, we need a
  // release barrier before decrementing _global_vthread_transition_disable_count
  // to make sure any memory operations executed by the disabler are visible to
  // the target once it mounts again. If a target was mounted, the handshake
  // executed against it already provided the needed synchronization.
  // This pairs with the equivalent acquire barrier in start_transition().
  OrderAccess::release();

  MonitorLocker ml(VThreadTransition_lock);
  if (_is_exclusive) {
    set_exclusive_operation_ongoing(false);
  }
  dec_active_disablers();
  dec_global_vthread_transition_disable_count();
  int base_disable_count = notify_jvmti_events() ? 1 : 0;
  if (global_vthread_transition_disable_count() == base_disable_count || _is_exclusive) {
    ml.notify_all();
  }
  JVMTI_ONLY(JavaThread::current()->set_is_vthread_transition_disabler(false);)
}

int MountUnmountDisabler::global_vthread_transition_disable_count() {
  assert(_global_vthread_transition_disable_count >= 0, "");
  return AtomicAccess::load(&_global_vthread_transition_disable_count);
}

void MountUnmountDisabler::inc_global_vthread_transition_disable_count() {
  assert(VThreadTransition_lock->owned_by_self() || SafepointSynchronize::is_at_safepoint(), "Must be locked");
  assert(_global_vthread_transition_disable_count >= 0, "");
  AtomicAccess::store(&_global_vthread_transition_disable_count, _global_vthread_transition_disable_count + 1);
}

void MountUnmountDisabler::dec_global_vthread_transition_disable_count() {
  assert(VThreadTransition_lock->owned_by_self() || SafepointSynchronize::is_at_safepoint(), "Must be locked");
  assert(_global_vthread_transition_disable_count > 0, "");
  AtomicAccess::store(&_global_vthread_transition_disable_count, _global_vthread_transition_disable_count - 1);
}

bool MountUnmountDisabler::exclusive_operation_ongoing() {
  assert(VThreadTransition_lock->owned_by_self(), "Must be locked");
  return _exclusive_operation_ongoing;
}

void MountUnmountDisabler::set_exclusive_operation_ongoing(bool val) {
  assert(VThreadTransition_lock->owned_by_self(), "Must be locked");
  assert(_exclusive_operation_ongoing != val, "");
  _exclusive_operation_ongoing = val;
}

int MountUnmountDisabler::active_disablers() {
  assert(_active_disablers >= 0, "");
  return AtomicAccess::load(&_active_disablers);
}

void MountUnmountDisabler::inc_active_disablers() {
  assert(VThreadTransition_lock->owned_by_self(), "Must be locked");
  assert(_active_disablers >= 0, "");
  _active_disablers++;
}

void MountUnmountDisabler::dec_active_disablers() {
  assert(VThreadTransition_lock->owned_by_self(), "Must be locked");
  assert(_active_disablers > 0, "");
  _active_disablers--;
}

bool MountUnmountDisabler::notify_jvmti_events() {
  return _notify_jvmti_events;
}

void MountUnmountDisabler::set_notify_jvmti_events(bool val, bool is_onload) {
  if (val == _notify_jvmti_events || !DoJVMTIVirtualThreadTransitions) return;

  // Force slow path on start/end vthread transitions for JVMTI bookkeeping.
  // 'val' is always true except with WhiteBox methods for testing purposes.
  if (is_onload) {
    // Skip existing increment methods since asserts will fail.
    assert(val && _global_vthread_transition_disable_count == 0, "");
    AtomicAccess::inc(&_global_vthread_transition_disable_count);
  } else {
    assert(SafepointSynchronize::is_at_safepoint(), "");
    if (val) {
      inc_global_vthread_transition_disable_count();
    } else {
      dec_global_vthread_transition_disable_count();
    }
  }
  log_trace(continuations,tracking)("%s _notify_jvmti_events, _global_vthread_transition_disable_count=%d", val ? "enabling" : "disabling", _global_vthread_transition_disable_count);
  _notify_jvmti_events = val;
}

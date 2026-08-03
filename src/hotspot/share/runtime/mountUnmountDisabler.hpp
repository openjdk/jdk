/*
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

#ifndef SHARE_RUNTIME_MOUNTUNMOUNTDISABLER_HPP
#define SHARE_RUNTIME_MOUNTUNMOUNTDISABLER_HPP

#include "memory/allocation.hpp"
#include "runtime/handles.hpp"

class JavaThread;

// This class adds support to disable virtual thread transitions (mount/unmount).
// This is needed to safely execute operations that access virtual thread state.
// Users should use the Handshake class when possible instead of using this directly.
class MountUnmountDisabler : public AnyObj {
  // The global counter is used for operations that require disabling
  // transitions for all virtual threads. Currently this is only used
  // by some JVMTI operations. We also increment this counter when the
  // first JVMTI agent attaches to always force the slowpath when starting
  // a transition. This is needed because if JVMTI is present we need to
  // check for possible event posting.
  static volatile int _global_vthread_transition_disable_count;
  static volatile int _active_disablers;
  static bool    _exclusive_operation_ongoing;

  bool _is_exclusive;    // currently only for suspender or resumer
  bool _is_virtual;      // target thread is virtual
  bool _is_self;         // MountUnmountDisabler is a no-op for current platform, carrier or virtual thread
  Handle _vthread;       // virtual thread to disable transitions for, no-op if it is a platform thread

  //DEBUG_ONLY(static void print_info();)
  void disable_transition_for_one();
  void disable_transition_for_all();
  void enable_transition_for_one();
  void enable_transition_for_all();

 public:
  MountUnmountDisabler(bool exlusive = false);
  MountUnmountDisabler(oop thread_oop);
  MountUnmountDisabler(jthread thread);
  ~MountUnmountDisabler();

  static int global_vthread_transition_disable_count();
  static void inc_global_vthread_transition_disable_count();
  static void dec_global_vthread_transition_disable_count();

  static volatile int* global_vthread_transition_disable_count_address() {
    return &_global_vthread_transition_disable_count;
  }

  static bool exclusive_operation_ongoing();
  static void set_exclusive_operation_ongoing(bool val);

  static int active_disablers();
  static void inc_active_disablers();
  static void dec_active_disablers();

  static void start_transition(JavaThread* thread, oop vthread, bool is_mount, bool is_thread_end);
  static void end_transition(JavaThread* thread, oop vthread, bool is_mount, bool is_thread_start);

  static bool is_start_transition_disabled(JavaThread* thread, oop vthread);

  // enable notifications from VirtualThread about Mount/Unmount events
  static bool _notify_jvmti_events;
  static bool notify_jvmti_events();
  static void set_notify_jvmti_events(bool val, bool is_onload = false);
  static bool* notify_jvmti_events_address() {
    return &_notify_jvmti_events;
  }
};

#endif // SHARE_RUNTIME_MOUNTUNMOUNTDISABLER_HPP

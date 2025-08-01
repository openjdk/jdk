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

#ifndef SHARE_RUNTIME_SUSPENDRESUMEMANAGER_HPP
#define SHARE_RUNTIME_SUSPENDRESUMEMANAGER_HPP

class SuspendThreadHandshakeClosure;
class ThreadSelfSuspensionHandshakeClosure;

class SuspendResumeManager {
  friend SuspendThreadHandshakeClosure;
  friend ThreadSelfSuspensionHandshakeClosure;
  friend JavaThread;

  JavaThread* _target;
  Monitor* _state_lock;

  SuspendResumeManager(JavaThread* thread, Monitor* state_lock);

  // This flag is true when the thread owning this
  // SuspendResumeManager (the _target) is suspended.
  volatile bool _suspended;
  // This flag is true while there is async handshake (trap)
  // on queue. Since we do only need one, we can reuse it if
  // thread gets suspended again (after a resume)
  // and we have not yet processed it.
  bool _async_suspend_handshake;

  bool suspend(bool register_vthread_SR);
  bool resume(bool register_vthread_SR);

  // Called from the async handshake (the trap)
  // to stop a thread from continuing execution when suspended.
  void do_owner_suspend();

  // Called from the suspend handshake.
  bool suspend_with_handshake(bool register_vthread_SR);

  void set_suspended(bool to, bool register_vthread_SR);

  bool is_suspended() {
    return Atomic::load(&_suspended);
  }

  bool has_async_suspend_handshake() { return _async_suspend_handshake; }
  void set_async_suspend_handshake(bool to) { _async_suspend_handshake = to; }
};

#endif // SHARE_RUNTIME_SUSPENDRESUMEMANAGER_HPP

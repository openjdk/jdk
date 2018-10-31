/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_HANDSHAKE_HPP
#define SHARE_VM_RUNTIME_HANDSHAKE_HPP

#include "memory/allocation.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/semaphore.hpp"

class ThreadClosure;
class JavaThread;

// A handshake operation is a callback that is executed for each JavaThread
// while that thread is in a safepoint safe state. The callback is executed
// either by the thread itself or by the VM thread while keeping the thread
// in a blocked state. A handshake can be performed with a single
// JavaThread as well.
class Handshake : public AllStatic {
 public:
  // Execution of handshake operation
  static void execute(ThreadClosure* thread_cl);
  static bool execute(ThreadClosure* thread_cl, JavaThread* target);
};

class HandshakeOperation;

// The HandshakeState keep tracks of an ongoing handshake for one JavaThread.
// VM thread and JavaThread are serialized with the semaphore making sure
// the operation is only done by either VM thread on behalf of the JavaThread
// or the JavaThread itself.
class HandshakeState {
  HandshakeOperation* volatile _operation;

  Semaphore _semaphore;
  bool _thread_in_process_handshake;

  bool claim_handshake_for_vmthread();
  bool vmthread_can_process_handshake(JavaThread* target);

  void clear_handshake(JavaThread* thread);

  void process_self_inner(JavaThread* thread);
public:
  HandshakeState();

  void set_operation(JavaThread* thread, HandshakeOperation* op);

  bool has_operation() const {
    return _operation != NULL;
  }

  void process_by_self(JavaThread* thread) {
    if (!_thread_in_process_handshake) {
      FlagSetting fs(_thread_in_process_handshake, true);
      process_self_inner(thread);
    }
  }

  void process_by_vmthread(JavaThread* target);
};

#endif // SHARE_VM_RUNTIME_HANDSHAKE_HPP

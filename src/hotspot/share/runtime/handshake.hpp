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

#ifndef SHARE_RUNTIME_HANDSHAKE_HPP
#define SHARE_RUNTIME_HANDSHAKE_HPP

#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "runtime/flags/flagSetting.hpp"
#include "runtime/semaphore.hpp"

class HandshakeOperation;
class JavaThread;

// A handshake closure is a callback that is executed for each JavaThread
// while that thread is in a safepoint safe state. The callback is executed
// either by the target JavaThread itself or by the VMThread while keeping
// the target thread in a blocked state. A handshake can be performed with a
// single JavaThread as well. In that case, the callback is executed either
// by the target JavaThread itself or, depending on whether the operation is
// a direct handshake or not, by the JavaThread that requested the handshake
// or the VMThread respectively.
class HandshakeClosure : public ThreadClosure {
  const char* const _name;
 public:
  HandshakeClosure(const char* name) : _name(name) {}
  const char* name() const {
    return _name;
  }
  virtual void do_thread(Thread* thread) = 0;
};

class Handshake : public AllStatic {
 public:
  // Execution of handshake operation
  static void execute(HandshakeClosure* hs_cl);
  static bool execute(HandshakeClosure* hs_cl, JavaThread* target);
  static bool execute_direct(HandshakeClosure* hs_cl, JavaThread* target);
};

// The HandshakeState keeps track of an ongoing handshake for this JavaThread.
// VMThread/Handshaker and JavaThread are serialized with semaphore _processing_sem
// making sure the operation is only done by either VMThread/Handshaker on behalf
// of the JavaThread or by the target JavaThread itself.
class HandshakeState {
  JavaThread* _handshakee;
  HandshakeOperation* volatile _operation;
  HandshakeOperation* volatile _operation_direct;

  Semaphore _handshake_turn_sem;  // Used to serialize direct handshakes for this JavaThread.
  Semaphore _processing_sem;
  bool _thread_in_process_handshake;

  bool claim_handshake(bool is_direct);
  bool possibly_can_process_handshake();
  bool can_process_handshake();
  void clear_handshake(bool is_direct);

  void process_self_inner();

public:
  HandshakeState();

  void set_thread(JavaThread* thread) { _handshakee = thread; }

  void set_operation(HandshakeOperation* op);
  bool has_operation() const { return _operation != NULL || _operation_direct != NULL; }
  bool has_specific_operation(bool is_direct) const {
    return is_direct ? _operation_direct != NULL : _operation != NULL;
  }

  void process_by_self() {
    if (!_thread_in_process_handshake) {
      FlagSetting fs(_thread_in_process_handshake, true);
      process_self_inner();
    }
  }
  bool try_process(HandshakeOperation* op);

#ifdef ASSERT
  Thread* _active_handshaker;
  Thread* get_active_handshaker() const { return _active_handshaker; }
#endif

};

#endif // SHARE_RUNTIME_HANDSHAKE_HPP

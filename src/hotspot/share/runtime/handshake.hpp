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
#include "runtime/mutex.hpp"
#include "utilities/filterQueue.hpp"

class HandshakeOperation;
class JavaThread;

// A handshake closure is a callback that is executed for each JavaThread
// while that thread is in a safepoint safe state. The callback is executed
// either by the target JavaThread itself or by the VMThread while keeping
// the target thread in a blocked state. A handshake can be performed with a
// single JavaThread as well. In that case, the callback is executed either
// by the target JavaThread itself or, depending on whether the operation is
// a single target/direct handshake or not, by the JavaThread that requested the
// handshake or the VMThread respectively.
class HandshakeClosure : public ThreadClosure, public CHeapObj<mtThread> {
  const char* const _name;
 public:
  HandshakeClosure(const char* name) : _name(name) {}
  virtual ~HandshakeClosure() {}
  const char* name() const    { return _name; }
  virtual bool is_asynch()    { return false; };
  virtual void do_thread(Thread* thread) = 0;
};

class AsynchHandshakeClosure : public HandshakeClosure {
 public:
   AsynchHandshakeClosure(const char* name) : HandshakeClosure(name) {}
   virtual ~AsynchHandshakeClosure() {}
   virtual bool is_asynch()          { return true; }
};

class Handshake : public AllStatic {
 public:
  // Execution of handshake operation
  static void execute(HandshakeClosure*       hs_cl);
  static void execute(HandshakeClosure*       hs_cl, JavaThread* target);
  static void execute(AsynchHandshakeClosure* hs_cl, JavaThread* target);
};

// The HandshakeState keeps track of an ongoing handshake for this JavaThread.
// VMThread/Handshaker and JavaThread are serialized with _lock making sure the
// operation is only done by either VMThread/Handshaker on behalf of the
// JavaThread or by the target JavaThread itself.
class HandshakeState {
  JavaThread* _handshakee;
  FilterQueue<HandshakeOperation*> _queue;
  Mutex _lock;
  Thread* _active_handshaker;

  bool claim_handshake();
  bool possibly_can_process_handshake();
  bool can_process_handshake();
  void process_self_inner();

 public:
  HandshakeState(JavaThread* thread);

  void add_operation(HandshakeOperation* op);
  HandshakeOperation* pop_for_self();
  HandshakeOperation* pop_for_processor();

  bool has_operation_for_processor();
  bool has_operation() {
    return !_queue.is_empty();
  }
  bool block_for_operation() {
    return !_queue.is_empty() || _lock.is_locked();
  }

  void process_by_self();

  enum ProcessResult {
    _no_operation = 0,
    _not_safe,
    _claim_failed,
    _processed,
    _succeeded,
    _number_states
  };
  ProcessResult try_process(HandshakeOperation* match_op);

  Thread* active_handshaker() const { return _active_handshaker; }
};

#endif // SHARE_RUNTIME_HANDSHAKE_HPP

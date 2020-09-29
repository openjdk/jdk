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

// A handshake closure is a callback that is executed for a JavaThread
// while it is in a safepoint/handshake-safe state. Depending on the
// nature of the closure, the callback may be executed by the initiating
// thread, the target thread, or the VMThread. If the callback is not executed
// by the target thread it will remain in a blocked state until the callback completes.
class HandshakeClosure : public ThreadClosure, public CHeapObj<mtThread> {
  const char* const _name;
 public:
  HandshakeClosure(const char* name) : _name(name) {}
  virtual ~HandshakeClosure() {}
  const char* name() const    { return _name; }
  virtual bool is_async()     { return false; }
  virtual void do_thread(Thread* thread) = 0;
};

class AsyncHandshakeClosure : public HandshakeClosure {
 public:
   AsyncHandshakeClosure(const char* name) : HandshakeClosure(name) {}
   virtual ~AsyncHandshakeClosure() {}
   virtual bool is_async()          { return true; }
};

class Handshake : public AllStatic {
 public:
  // Execution of handshake operation
  static void execute(HandshakeClosure*       hs_cl);
  static void execute(HandshakeClosure*       hs_cl, JavaThread* target);
  static void execute(AsyncHandshakeClosure* hs_cl, JavaThread* target);
};

// The HandshakeState keeps track of an ongoing handshake for this JavaThread.
// VMThread/Handshaker and JavaThread are serialized with _lock making sure the
// operation is only done by either VMThread/Handshaker on behalf of the
// JavaThread or by the target JavaThread itself.
class HandshakeState {
  // This a back reference to the JavaThread,
  // the target for all operation in the queue.
  JavaThread* _handshakee;
  // The queue containing handshake operations to be performed on _handshakee.
  FilterQueue<HandshakeOperation*> _queue;
  // Provides mutual exclusion to this state and queue.
  Mutex   _lock;
  // Set to the thread executing the handshake operation.
  Thread* _active_handshaker;

  bool claim_handshake();
  bool possibly_can_process_handshake();
  bool can_process_handshake();
  void process_self_inner();

  bool have_non_self_executable_operation();
  HandshakeOperation* pop_for_self();
  HandshakeOperation* pop();

 public:
  HandshakeState(JavaThread* thread);

  void add_operation(HandshakeOperation* op);

  bool has_operation() {
    return !_queue.is_empty();
  }

  // Both _queue and _lock must be checked. If a thread has seen this _handshakee
  // as safe it will execute all possible handshake operations in a loop while
  // holding _lock. We use lock free addition to the queue, which means it is
  // possible for the queue to be seen as empty by _handshakee but as non-empty
  // by the thread executing in the loop. To avoid the _handshakee continuing
  // while handshake operations are being executed, the _handshakee
  // must take slow path, process_by_self(), if _lock is held.
  bool should_process() {
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

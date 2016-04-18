/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_REFERENCEPENDINGLISTLOCKER_HPP
#define SHARE_VM_GC_SHARED_REFERENCEPENDINGLISTLOCKER_HPP

#include "memory/allocation.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/mutex.hpp"
#include "runtime/thread.hpp"
#include "utilities/exceptions.hpp"

//
// The ReferencePendingListLockerThread locks and unlocks the reference
// pending list lock on behalf a non-Java thread, typically a concurrent
// GC thread. This interface should not be directly accessed. All uses
// should instead go through the ReferencePendingListLocker, which calls
// this thread if needed.
//
class ReferencePendingListLockerThread : public JavaThread {
private:
  enum Message {
    NONE,
    LOCK,
    UNLOCK
  };

  Monitor _monitor;
  Message _message;

  ReferencePendingListLockerThread();

  static void start(JavaThread* thread, TRAPS);

  void send_message(Message message);
  void receive_and_handle_messages();

public:
  static ReferencePendingListLockerThread* create(TRAPS);

  virtual bool is_hidden_from_external_view() const;

  void lock();
  void unlock();
};

//
// The ReferencePendingListLocker is the main interface for locking and
// unlocking the reference pending list lock, which needs to be held by
// the GC when adding references to the pending list. Since this is a
// Java-level monitor it can only be locked/unlocked by a Java thread.
// For this reason there is an option to spawn a helper thread, the
// ReferencePendingListLockerThread, during initialization. If a helper
// thread is spawned all lock operations from non-Java threads will be
// delegated to the helper thread. The helper thread is typically needed
// by concurrent GCs.
//
class ReferencePendingListLocker VALUE_OBJ_CLASS_SPEC {
private:
  static bool                              _is_initialized;
  static ReferencePendingListLockerThread* _locker_thread;
  BasicLock                                _basic_lock;

public:
  static void initialize(bool needs_locker_thread, TRAPS);
  static bool is_initialized();

  static bool is_locked_by_self();

  void lock();
  void unlock();
};

#endif // SHARE_VM_GC_SHARED_REFERENCEPENDINGLISTLOCKER_HPP

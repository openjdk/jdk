/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_OSTHREAD_BASE_HPP
#define SHARE_RUNTIME_OSTHREAD_BASE_HPP

#include "memory/allocation.hpp"

class Monitor;

// The OSThread class holds OS-specific thread information.  It is equivalent
// to the sys_thread_t structure of the classic JVM implementation.

// The thread states represented by the ThreadState values are platform-specific
// and are likely to be only approximate, because most OSes don't give you access
// to precise thread state information.

// Note: the ThreadState is legacy code and is not correctly implemented.
// Uses of ThreadState need to be replaced by the state in the JavaThread.

enum ThreadState {
  ALLOCATED,                    // Memory has been allocated but not initialized
  INITIALIZED,                  // The thread has been initialized but yet started
  RUNNABLE,                     // Has been started and is runnable, but not necessarily running
  MONITOR_WAIT,                 // Waiting on a contended monitor lock
  CONDVAR_WAIT,                 // Waiting on a condition variable
  OBJECT_WAIT,                  // Waiting on an Object.wait() call
  BREAKPOINTED,                 // Suspended at breakpoint
  SLEEPING,                     // Thread.sleep()
  ZOMBIE                        // All done, but not reclaimed yet
};

typedef int (*OSThreadStartFunc)(void*);

class OSThreadBase: public CHeapObj<mtThread> {
  friend class VMStructs;
  friend class JVMCIVMStructs;
 private:
  volatile ThreadState _state;    // Thread state *hint*

  // Methods
 public:
  OSThreadBase() {}
  virtual ~OSThreadBase() {}
  NONCOPYABLE(OSThreadBase);

  void set_state(ThreadState state)                { _state = state; }
  ThreadState get_state()                          { return _state; }


  virtual uintx thread_id_for_printing() const = 0;

  // Printing
  void print_on(outputStream* st) const;
  void print() const;
};


// Utility class for use with condition variables:
class OSThreadWaitState : public StackObj {
  OSThreadBase* _osthread;
  ThreadState _old_state;
 public:
  OSThreadWaitState(OSThreadBase* osthread, bool is_object_wait) {
    _osthread  = osthread;
    _old_state = osthread->get_state();
    if (is_object_wait) {
      osthread->set_state(OBJECT_WAIT);
    } else {
      osthread->set_state(CONDVAR_WAIT);
    }
  }
  ~OSThreadWaitState() {
    _osthread->set_state(_old_state);
  }
};


// Utility class for use with contended monitors:
class OSThreadContendState : public StackObj {
  OSThreadBase* _osthread;
  ThreadState _old_state;
 public:
  OSThreadContendState(OSThreadBase* osthread) {
    _osthread  = osthread;
    _old_state = osthread->get_state();
    osthread->set_state(MONITOR_WAIT);
  }
  ~OSThreadContendState() {
    _osthread->set_state(_old_state);
  }
};

#endif // SHARE_RUNTIME_OSTHREAD_BASE_HPP

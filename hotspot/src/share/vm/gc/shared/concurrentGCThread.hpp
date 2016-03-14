/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_SHARED_CONCURRENTGCTHREAD_HPP
#define SHARE_VM_GC_SHARED_CONCURRENTGCTHREAD_HPP

#include "runtime/thread.hpp"
#include "utilities/macros.hpp"

class ConcurrentGCThread: public NamedThread {
  friend class VMStructs;

  bool volatile _should_terminate;
  bool _has_terminated;

  // Do initialization steps in the thread: record stack base and size,
  // init thread local storage, set JNI handle block.
  void initialize_in_thread();

  // Wait until Universe::is_fully_initialized();
  void wait_for_universe_init();

  // Record that the current thread is terminating, and will do more
  // concurrent work.
  void terminate();

protected:
  // Create and start the thread (setting it's priority.)
  void create_and_start(ThreadPriority prio = NearMaxPriority);

  // Do the specific GC work. Called by run() after initialization complete.
  virtual void run_service() = 0;

  // Shut down the specific GC work. Called by stop() as part of termination protocol.
  virtual void stop_service()  = 0;

public:
  ConcurrentGCThread();

  // Tester
  bool is_ConcurrentGC_thread() const { return true; }

  virtual void run();

  // shutdown following termination protocol
  virtual void stop();

  bool should_terminate() { return _should_terminate; }
  bool has_terminated()   { return _has_terminated; }
};

// The SurrogateLockerThread is used by concurrent GC threads for
// manipulating Java monitors, in particular, currently for
// manipulating the pending_list_lock. XXX
class SurrogateLockerThread: public JavaThread {
  friend class VMStructs;
 public:
  enum SLT_msg_type {
    empty = 0,           // no message
    acquirePLL,          // acquire pending list lock
    releaseAndNotifyPLL  // notify and release pending list lock
  };
 private:
  // the following are shared with the CMSThread
  SLT_msg_type  _buffer;  // communication buffer
  Monitor       _monitor; // monitor controlling buffer
  BasicLock     _basicLock; // used for PLL locking

 public:
  static SurrogateLockerThread* make(TRAPS);

  // Terminate VM with error message that SLT needed but not yet created.
  static void report_missing_slt();

  SurrogateLockerThread();

  bool is_hidden_from_external_view() const     { return true; }

  void loop(); // main method

  void manipulatePLL(SLT_msg_type msg);

};

#endif // SHARE_VM_GC_SHARED_CONCURRENTGCTHREAD_HPP

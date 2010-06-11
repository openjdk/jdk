/*
 * Copyright (c) 1997, 2001, Oracle and/or its affiliates. All rights reserved.
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

typedef void* HANDLE;

 private:
  // Win32-specific thread information
  HANDLE _thread_handle;        // Win32 thread handle
  unsigned long _thread_id;     // Win32 thread id
  HANDLE _interrupt_event;      // Event signalled on thread interrupt
  ThreadState _last_state;

 public:
  // The following will only apply in the Win32 implementation, and should only
  // be visible in the concrete class, not this which should be an abstract base class
  HANDLE thread_handle() const                     { return _thread_handle; }
  void set_thread_handle(HANDLE handle)            { _thread_handle = handle; }
  HANDLE interrupt_event() const                   { return _interrupt_event; }
  void set_interrupt_event(HANDLE interrupt_event) { _interrupt_event = interrupt_event; }

  unsigned long thread_id() const                  { return _thread_id; }
#ifndef PRODUCT
  // Used for debugging, return a unique integer for each thread.
  int thread_identifier() const                    { return _thread_id; }
#endif
#ifdef ASSERT
  // We expect no reposition failures so kill vm if we get one
  //
  bool valid_reposition_failure() {
    return false;
  }
#endif // ASSERT
  void set_thread_id(unsigned long thread_id)      { _thread_id = thread_id; }

  bool is_try_mutex_enter()                        { return false; }

  // This is a temporary fix for the thread states during
  // suspend/resume until we throw away OSThread completely.
  // NEEDS_CLEANUP
  void set_last_state(ThreadState state)           { _last_state = state; }
  ThreadState get_last_state()                     { return _last_state; }

 private:
  void pd_initialize();
  void pd_destroy();

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

#ifndef OS_WINDOWS_OSTHREAD_WINDOWS_HPP
#define OS_WINDOWS_OSTHREAD_WINDOWS_HPP

#include "runtime/osThreadBase.hpp"
#include "utilities/globalDefinitions.hpp"

class OSThread : public OSThreadBase {
  friend class VMStructs;

  typedef unsigned long thread_id_t;
  typedef void* HANDLE;

  thread_id_t _thread_id;

  // Win32-specific thread information
  HANDLE _thread_handle;        // Win32 thread handle
  HANDLE _interrupt_event;      // Event signalled on thread interrupt for use by
                                // Process.waitFor().

 public:
  OSThread();
  ~OSThread();

  thread_id_t thread_id() const                    { return _thread_id; }
  void set_thread_id(thread_id_t id)               { _thread_id = id; }

  // The following will only apply in the Win32 implementation, and should only
  // be visible in the concrete class, not this which should be an abstract base class
  HANDLE thread_handle() const                     { return _thread_handle; }
  void set_thread_handle(HANDLE handle)            { _thread_handle = handle; }
  HANDLE interrupt_event() const                   { return _interrupt_event; }
  void set_interrupt_event(HANDLE interrupt_event) { _interrupt_event = interrupt_event; }
  // This is specialized on Windows to interact with the _interrupt_event.
  void set_interrupted(bool z);

  uintx thread_id_for_printing() const override {
    return (uintx)_thread_id;
  }
};

#endif // OS_WINDOWS_OSTHREAD_WINDOWS_HPP

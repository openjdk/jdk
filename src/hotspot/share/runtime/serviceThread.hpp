/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_SERVICETHREAD_HPP
#define SHARE_VM_RUNTIME_SERVICETHREAD_HPP

#include "runtime/thread.hpp"

// A JavaThread for low memory detection support and JVMTI
// compiled-method-load events.
class ServiceThread : public JavaThread {
  friend class VMStructs;
 private:

  static ServiceThread* _instance;

  static void service_thread_entry(JavaThread* thread, TRAPS);
  ServiceThread(ThreadFunction entry_point) : JavaThread(entry_point) {};

 public:
  static void initialize();

  // Hide this thread from external view.
  bool is_hidden_from_external_view() const      { return true; }

  // Returns true if the passed thread is the service thread.
  static bool is_service_thread(Thread* thread);
};

#endif // SHARE_VM_RUNTIME_SERVICETHREAD_HPP

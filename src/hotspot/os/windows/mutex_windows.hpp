/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_WINDOWS_MUTEX_WINDOWS_HPP
#define OS_WINDOWS_MUTEX_WINDOWS_HPP

#include "memory/allocation.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

// Platform specific implementations that underpin VM Mutex/Monitor classes.
// Note that CRITICAL_SECTION supports recursive locking, while the semantics
// of the VM Mutex class does not. It is up to the Mutex class to hide this
// difference in behaviour.

class PlatformMutex : public CHeapObj<mtSynchronizer> {
  NONCOPYABLE(PlatformMutex);

 protected:
  CRITICAL_SECTION   _mutex; // Native mutex for locking

 public:
  PlatformMutex();
  ~PlatformMutex();
  void lock();
  void unlock();
  bool try_lock();
};

class PlatformMonitor : public PlatformMutex {
 private:
  CONDITION_VARIABLE _cond;  // Native condition variable for blocking
  NONCOPYABLE(PlatformMonitor);

 public:
  PlatformMonitor();
  ~PlatformMonitor();
  int wait(jlong millis);
  void notify();
  void notify_all();
};

#endif // OS_WINDOWS_MUTEX_WINDOWS_HPP

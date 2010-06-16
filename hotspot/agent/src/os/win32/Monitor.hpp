/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _MONITOR_
#define _MONITOR_

#include <windows.h>

class Monitor {
public:
  Monitor();
  ~Monitor();

  void lock();
  void unlock();
  // Default time is forever (i.e, zero). Returns true if it times-out, otherwise
  // false.
  bool wait(long timeout = 0);
  bool notify();
  bool notifyAll();

private:
  HANDLE owner();
  void setOwner(HANDLE owner);
  bool ownedBySelf();

  HANDLE _owner;
  long   _lock_count;
  HANDLE _lock_event;   // Auto-reset event for blocking in lock()
  HANDLE _wait_event;   // Manual-reset event for notifications
  long _counter;        // Current number of notifications
  long _waiters;        // Number of threads waiting for notification
  long _tickets;        // Number of waiters to be notified
};


#endif  // #defined _MONITOR_

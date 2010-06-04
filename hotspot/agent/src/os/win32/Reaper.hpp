/*
 * Copyright (c) 2000, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _REAPER_
#define _REAPER_

#include <vector>
#include <windows.h>

typedef void ReaperCB(void* userData);

/** A Reaper maintains a thread which waits for child processes to
    terminate; upon termination it calls a user-specified ReaperCB to
    clean up resources associated with those child processes. */

class Reaper {
private:
  Reaper& operator=(const Reaper&);
  Reaper(const Reaper&);

public:
  Reaper(ReaperCB*);
  ~Reaper();

  // Start the reaper thread.
  bool start();

  // Stop the reaper thread. This is called automatically in the
  // reaper's destructor. It is not thread safe and should be called
  // by at most one thread at a time.
  bool stop();

  // Register a given child process with the reaper. This should be
  // called by the application's main thread. When that process
  // terminates, the cleanup callback will be called with the
  // specified userData in the context of the reaper thread. Callbacks
  // are guaranteed to be called serially, so they can safely refer to
  // static data as well as the given user data.
  void registerProcess(HANDLE processHandle, void* userData);

private:
  // For thread safety of register()
  CRITICAL_SECTION crit;

  ReaperCB* cb;

  // State variables
  volatile bool active;
  volatile bool shouldShutDown;

  struct ProcessInfo {
    HANDLE handle;
    void* userData;
  };

  // Bookkeeping
  std::vector<ProcessInfo> procInfo;

  // Synchronization between application thread and reaper thread
  HANDLE event;

  // Entry point for reaper thread
  void reaperThread();

  // Static function which is actual thread entry point
  static DWORD WINAPI reaperThreadEntry(LPVOID data);
};

#endif  // #defined _REAPER_

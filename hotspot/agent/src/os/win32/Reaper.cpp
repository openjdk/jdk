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

#include <iostream>
#include "Reaper.hpp"

using namespace std;

Reaper::Reaper(ReaperCB* cb) {
  InitializeCriticalSection(&crit);
  event = CreateEvent(NULL, TRUE, FALSE, NULL);
  this->cb = cb;

  active = false;
  shouldShutDown = false;
}

bool
Reaper::start() {
  bool result = false;

  EnterCriticalSection(&crit);

  if (!active) {
    DWORD id;
    HANDLE reaper = CreateThread(NULL, 0, &Reaper::reaperThreadEntry,
                                 this, 0, &id);
    if (reaper != NULL) {
      result = true;
    }
  }

  LeaveCriticalSection(&crit);

  return result;
}

bool
Reaper::stop() {
  bool result = false;

  EnterCriticalSection(&crit);

  if (active) {
    shouldShutDown = true;
    SetEvent(event);
    while (active) {
      Sleep(1);
    }
    shouldShutDown = false;
    result = true;
  }

  LeaveCriticalSection(&crit);

  return result;
}

void
Reaper::registerProcess(HANDLE processHandle, void* userData) {
  ProcessInfo info;

  info.handle = processHandle;
  info.userData = userData;

  EnterCriticalSection(&crit);

  procInfo.push_back(info);
  SetEvent(event);

  LeaveCriticalSection(&crit);
}

void
Reaper::reaperThread() {
  while (!shouldShutDown) {
    // Take atomic snapshot of the current process list and user data
    EnterCriticalSection(&crit);

    int num = procInfo.size();
    HANDLE* handleList = new HANDLE[1 + num];
    void**  dataList   = new void*[num];
    for (int i = 0; i < num; i++) {
      handleList[i] = procInfo[i].handle;
      dataList[i]   = procInfo[i].userData;
    }

    LeaveCriticalSection(&crit);

    // Topmost handle becomes the event object, so other threads can
    // signal this one to notice differences in the above list (or
    // shut down)
    handleList[num] = event;

    // Wait for these objects
    DWORD idx = WaitForMultipleObjects(1 + num, handleList,
                                       FALSE, INFINITE);
    if ((idx >= WAIT_OBJECT_0) && (idx <= WAIT_OBJECT_0 + num)) {
      idx -= WAIT_OBJECT_0;
      if (idx < num) {
        // A process exited (i.e., it wasn't that we were woken up
        // just because the event went off)
        (*cb)(dataList[idx]);
        // Remove this process from the list (NOTE: requires that
        // ordering does not change, i.e., that all additions are to
        // the back of the process list)
        EnterCriticalSection(&crit);

        std::vector<ProcessInfo>::iterator iter = procInfo.begin();
        iter += idx;
        procInfo.erase(iter);

        LeaveCriticalSection(&crit);
      } else {
        // Notification from other thread
        ResetEvent(event);
      }
    } else {
      // Unexpected return value. For now, warn.
      cerr << "Reaper::reaperThread(): unexpected return value "
           << idx << " from WaitForMultipleObjects" << endl;
    }

    // Clean up these lists
    delete[] handleList;
    delete[] dataList;
  }

  // Time to shut down
  active = false;
}

DWORD WINAPI
Reaper::reaperThreadEntry(LPVOID data) {
  Reaper* reaper = (Reaper*) data;
  reaper->reaperThread();
  return 0;
}

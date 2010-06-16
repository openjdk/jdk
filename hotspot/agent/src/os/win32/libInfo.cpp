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

// Disable too-long symbol warnings
#pragma warning ( disable : 4786 )

#include "libInfo.hpp"
#include "nt4internals.hpp"
#include "isNT4.hpp"
#include "toolHelp.hpp"
#include <assert.h>

using namespace std;

typedef void LibInfoImplFunc(DWORD pid, vector<LibInfo>& info);

static void libInfoImplNT4(DWORD pid, vector<LibInfo>& info);
static void libInfoImplToolHelp(DWORD pid, vector<LibInfo>& info);

void
libInfo(DWORD pid, vector<LibInfo>& info) {
  static LibInfoImplFunc* impl = NULL;

  if (impl == NULL) {
    // See which operating system we're on
    impl = (isNT4() ? &libInfoImplNT4 : &libInfoImplToolHelp);
  }

  assert(impl != NULL);

  (*impl)(pid, info);
}

static ULONG
ModuleCount(NT4::PDEBUG_BUFFER db) {
  return db->ModuleInformation ? *PULONG(db->ModuleInformation) : 0;
}

#define MAX2(a, b) (((a) < (b)) ? (b) : (a))

static void
libInfoImplNT4(DWORD pid, vector<LibInfo>& info) {
  static EnumProcessModulesFunc*   enumFunc = NULL;
  static GetModuleFileNameExFunc*  fnFunc   = NULL;
  static GetModuleInformationFunc* infoFunc = NULL;

  if (enumFunc == NULL) {
    HMODULE dll = loadPSAPIDLL();

    enumFunc = (EnumProcessModulesFunc*)   GetProcAddress(dll, "EnumProcessModules");
    fnFunc   = (GetModuleFileNameExFunc*)  GetProcAddress(dll, "GetModuleFileNameExA");
    infoFunc = (GetModuleInformationFunc*) GetProcAddress(dll, "GetModuleInformation");

    assert(enumFunc != NULL);
    assert(fnFunc   != NULL);
    assert(infoFunc != NULL);
  }

  static HMODULE* mods = new HMODULE[256];
  static int      numMods = 256;

  if (mods == NULL) {
    mods = new HMODULE[numMods];
    if (mods == NULL) {
      return;
    }
  }

  bool done = false;

  HANDLE proc = OpenProcess(PROCESS_ALL_ACCESS, FALSE, pid);
  if (proc == NULL) {
    return;
  }

  do {
    DWORD bufSize = numMods * sizeof(HMODULE);
    DWORD neededSize;

    if (!(*enumFunc)(proc, mods, bufSize, &neededSize)) {
      // Enum failed
      CloseHandle(proc);
      return;
    }

    int numFetched = neededSize / sizeof(HMODULE);

    if (numMods < numFetched) {
      // Grow buffer
      numMods = MAX2(numFetched, 2 * numMods);
      delete[] mods;
      mods = new HMODULE[numMods];
      if (mods == NULL) {
        CloseHandle(proc);
        return;
      }
    } else {
      char filename[MAX_PATH];
      MODULEINFO modInfo;

      // Iterate through and fetch each one's info
      for (int i = 0; i < numFetched; i++) {
        if (!(*fnFunc)(proc, mods[i], filename, MAX_PATH)) {
          CloseHandle(proc);
          return;
        }

        if (!(*infoFunc)(proc, mods[i], &modInfo, sizeof(MODULEINFO))) {
          CloseHandle(proc);
          return;
        }

        info.push_back(LibInfo(string(filename), (void*) modInfo.lpBaseOfDll));
      }

      done = true;
    }
  } while (!done);

  CloseHandle(proc);
  return;
}

void
libInfoImplToolHelp(DWORD pid, vector<LibInfo>& info) {
  using namespace ToolHelp;

  static CreateToolhelp32SnapshotFunc* snapshotFunc = NULL;
  static Module32FirstFunc*            firstFunc    = NULL;
  static Module32NextFunc*             nextFunc     = NULL;

  if (snapshotFunc == NULL) {
    HMODULE dll = loadDLL();

    snapshotFunc =
      (CreateToolhelp32SnapshotFunc*) GetProcAddress(dll,
                                                     "CreateToolhelp32Snapshot");

    firstFunc = (Module32FirstFunc*) GetProcAddress(dll,
                                                    "Module32First");

    nextFunc = (Module32NextFunc*) GetProcAddress(dll,
                                                  "Module32Next");

    assert(snapshotFunc != NULL);
    assert(firstFunc    != NULL);
    assert(nextFunc     != NULL);
  }

  HANDLE snapshot = (*snapshotFunc)(TH32CS_SNAPMODULE, pid);
  if (snapshot == (HANDLE) -1) {
    // Error occurred during snapshot
    return;
  }

  // Iterate
  MODULEENTRY32 module;
  if ((*firstFunc)(snapshot, &module)) {
    do {
      info.push_back(LibInfo(string(module.szExePath), (void*) module.modBaseAddr));
    } while ((*nextFunc)(snapshot, &module));
  }

  CloseHandle(snapshot);
}

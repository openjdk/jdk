/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

#include "procList.hpp"
#include "nt4internals.hpp"
#include "isNT4.hpp"
#include "toolHelp.hpp"
#include <assert.h>

using namespace std;
using namespace NT4;

typedef void ProcListImplFunc(ProcEntryList& processes);

void procListImplNT4(ProcEntryList& processes);
void procListImplToolHelp(ProcEntryList& processes);

ProcEntry::ProcEntry(ULONG pid, USHORT nameLength, WCHAR* name) {
  this->pid = pid;
  this->nameLength = nameLength;
  this->name = new WCHAR[nameLength];
  memcpy(this->name, name, nameLength * sizeof(WCHAR));
}

ProcEntry::ProcEntry(ULONG pid, USHORT nameLength, char* name) {
  this->pid = pid;
  this->nameLength = nameLength;
  this->name = new WCHAR[nameLength];
  int j = 0;
  for (int i = 0; i < nameLength; i++) {
    // FIXME: what is the proper promotion from ASCII to UNICODE?
    this->name[i] = name[i] & 0xFF;
  }
}

ProcEntry::ProcEntry(const ProcEntry& arg) {
  name = NULL;
  copyFrom(arg);
}

ProcEntry&
ProcEntry::operator=(const ProcEntry& arg) {
  copyFrom(arg);
  return *this;
}

ProcEntry::~ProcEntry() {
  delete[] name;
}

void
ProcEntry::copyFrom(const ProcEntry& arg) {
  if (name != NULL) {
    delete[] name;
  }
  pid = arg.pid;
  nameLength = arg.nameLength;
  name = new WCHAR[nameLength];
  memcpy(name, arg.name, nameLength * sizeof(WCHAR));
}

ULONG
ProcEntry::getPid() {
  return pid;
}

USHORT
ProcEntry::getNameLength() {
  return nameLength;
}

WCHAR*
ProcEntry::getName() {
  return name;
}

void
procList(ProcEntryList& processes) {
  static ProcListImplFunc* impl = NULL;

  if (impl == NULL) {
    // See which operating system we're on
    impl = (isNT4() ? &procListImplNT4 : &procListImplToolHelp);
  }

  assert(impl != NULL);

  (*impl)(processes);
}

void
procListImplNT4(ProcEntryList& processes) {
  using namespace NT4;

  static ZwQuerySystemInformationFunc* query = NULL;

  if (query == NULL) {
    HMODULE ntDLL = loadNTDLL();
    query =
      (ZwQuerySystemInformationFunc*) GetProcAddress(ntDLL,
                                                     "ZwQuerySystemInformation");
    assert(query != NULL);
  }

  ULONG n = 0x100;
  PSYSTEM_PROCESSES sp = new SYSTEM_PROCESSES[n];
  while ((*query)(SystemProcessesAndThreadsInformation,
                  sp, n * sizeof(SYSTEM_PROCESSES), 0) == STATUS_INFO_LENGTH_MISMATCH) {
    delete[] sp;
    n *= 2;
    sp = new SYSTEM_PROCESSES[n];
  }

  bool done = false;
  for (PSYSTEM_PROCESSES p = sp; !done;
       p = PSYSTEM_PROCESSES(PCHAR(p) + p->NextEntryDelta)) {
    processes.push_back(ProcEntry(p->ProcessId,
                                  p->ProcessName.Length / 2,
                                  p->ProcessName.Buffer));
    done = p->NextEntryDelta == 0;
  }
}

void
procListImplToolHelp(ProcEntryList& processes) {
  using namespace ToolHelp;

  static CreateToolhelp32SnapshotFunc* snapshotFunc = NULL;
  static Process32FirstFunc*           firstFunc    = NULL;
  static Process32NextFunc*            nextFunc     = NULL;

  if (snapshotFunc == NULL) {
    HMODULE dll = loadDLL();

    snapshotFunc =
      (CreateToolhelp32SnapshotFunc*) GetProcAddress(dll,
                                                     "CreateToolhelp32Snapshot");

    firstFunc = (Process32FirstFunc*) GetProcAddress(dll,
                                                     "Process32First");

    nextFunc = (Process32NextFunc*) GetProcAddress(dll,
                                                   "Process32Next");

    assert(snapshotFunc != NULL);
    assert(firstFunc    != NULL);
    assert(nextFunc     != NULL);
  }

  HANDLE snapshot = (*snapshotFunc)(TH32CS_SNAPPROCESS, 0 /* ignored */);
  if (snapshot == (HANDLE) -1) {
    // Error occurred during snapshot
    return;
  }

  // Iterate
  PROCESSENTRY32 proc;
  if ((*firstFunc)(snapshot, &proc)) {
    do {
      // FIXME: could make this uniform to the NT version by cutting
      // off the path name just before the executable name
      processes.push_back(ProcEntry(proc.th32ProcessID,
                                    strlen(proc.szExeFile),
                                    proc.szExeFile));
    } while ((*nextFunc)(snapshot, &proc));
  }

  CloseHandle(snapshot);
}

/*
 * Copyright (c) 1998, 1999, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

#include <string.h>

#include "hpi_impl.h"
#include "threads_md.h"

int logging_level = 0;
bool_t profiler_on = FALSE;

int sysSetLoggingLevel(int level)
{
    int old = logging_level;
    logging_level = level;
    return old;
}

bool_t sysSetMonitoringOn(bool_t s)
{
    bool_t old = profiler_on;
    profiler_on = s;
    return old;
}

int nReservedBytes;

sys_thread_t *allocThreadBlock()
{
    char *p = sysCalloc(nReservedBytes + sizeof(sys_thread_t), 1);
    if (p == NULL) {
        return NULL;
    }
    return (sys_thread_t *)(p + nReservedBytes);
}

void freeThreadBlock(sys_thread_t *tid)
{
    sysFree((char *)tid - nReservedBytes);
}

vm_calls_t *vm_calls = NULL;

static HPI_MemoryInterface hpi_memory_interface = {
  sysMalloc,
  sysRealloc,
  sysFree,
  sysCalloc,
  sysStrdup,
  sysMapMem,
  sysUnmapMem,
  sysCommitMem,
  sysDecommitMem,
  sysAllocBlock,
  sysFreeBlock,
};

static HPI_LibraryInterface hpi_library_interface = {
  sysBuildLibName,
  sysBuildFunName,
  sysLoadLibrary,
  sysUnloadLibrary,
  sysFindLibraryEntry,
};

static HPI_SystemInterface hpi_system_interface = {
  sysGetSysInfo,
  sysGetMilliTicks,
  sysTimeMillis,
  sysSignal,
  sysRaise,
  sysSignalNotify,
  sysSignalWait,
  sysShutdown,
  sysSetLoggingLevel,
  sysSetMonitoringOn,
  sysGetLastErrorString
};

static HPI_ThreadInterface hpi_thread_interface = {
  sysThreadBootstrap,
  sysThreadCreate,
  sysThreadSelf,
  sysThreadYield,
  sysThreadSuspend,
  sysThreadResume,
  sysThreadSetPriority,
  sysThreadGetPriority,
  sysThreadStackPointer,
  sysThreadStackTop,
  sysThreadRegs,
  sysThreadSingle,
  sysThreadMulti,
  sysThreadEnumerateOver,
  sysThreadCheckStack,
  sysThreadPostException,
  sysThreadInterrupt,
  sysThreadIsInterrupted,
  sysThreadAlloc,
  sysThreadFree,
  sysThreadCPUTime,
  sysThreadGetStatus,
  sysThreadInterruptEvent,
  sysThreadNativeID,
  sysThreadIsRunning,
  sysThreadProfSuspend,
  sysThreadProfResume,
  sysAdjustTimeSlice,
  sysMonitorSizeof,
  sysMonitorInit,
  sysMonitorDestroy,
  sysMonitorEnter,
  sysMonitorEntered,
  sysMonitorExit,
  sysMonitorNotify,
  sysMonitorNotifyAll,
  sysMonitorWait,
  sysMonitorInUse,
  sysMonitorOwner,
  sysMonitorGetInfo,
};

static HPI_FileInterface hpi_file_interface = {
  sysNativePath,
  sysFileType,
  sysOpen,
  sysClose,
  sysSeek,
  sysSetLength,
  sysSync,
  sysAvailable,
  sysRead,
  sysWrite,
  sysFileSizeFD
};

static HPI_SocketInterface hpi_socket_interface = {
  sysSocketClose,
  sysSocketAvailable,
  sysConnect,
  sysAccept,
  sysSendTo,
  sysRecvFrom,
  sysListen,
  sysRecv,
  sysSend,
  sysTimeout,
  sysGetHostByName,
  sysSocket,
  sysSocketShutdown,
  sysBind,
  sysGetSockName,
  sysGetHostName,
  sysGetHostByAddr,
  sysGetSockOpt,
  sysSetSockOpt,
  sysGetProtoByName,
};

static jint JNICALL
GetInterface(void **intfP, const char *name, jint version)
{
    *intfP = NULL;
    if (version != 1) {
        return -1;
    }
    if (strcmp(name, "Memory") == 0) {
        *intfP = &hpi_memory_interface;
        return 0;
    }
    if (strcmp(name, "Library") == 0) {
        *intfP = &hpi_library_interface;
        return 0;
    }
    if (strcmp(name, "System") == 0) {
        *intfP = &hpi_system_interface;
        return 0;
    }
    if (strcmp(name, "Thread") == 0) {
        *intfP = &hpi_thread_interface;
        return 0;
    }
    if (strcmp(name, "File") == 0) {
        *intfP = &hpi_file_interface;
        return 0;
    }
    if (strcmp(name, "Socket") == 0) {
        *intfP = &hpi_socket_interface;
        return 0;
    }
    return -2;
}

jint JNICALL
DLL_Initialize(GetInterfaceFunc *gi, void *args)
{
    vm_calls = args;
    *gi = GetInterface;
    return SYS_OK;
}

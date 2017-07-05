/*
 * Copyright 1994-1999 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

#ifndef _JAVASOFT_SYS_API_H_
#define _JAVASOFT_SYS_API_H_

#include "hpi.h"

extern HPI_MemoryInterface  *hpi_memory_interface;
extern HPI_LibraryInterface *hpi_library_interface;
extern HPI_SystemInterface  *hpi_system_interface;
extern HPI_ThreadInterface  *hpi_thread_interface;
extern HPI_FileInterface    *hpi_file_interface;
extern HPI_SocketInterface  *hpi_socket_interface;

#define sysMalloc(x)          hpi_memory_interface->Malloc(x)
#define sysRealloc(x,y)       hpi_memory_interface->Realloc(x,y)
#define sysFree(x)            hpi_memory_interface->Free(x)
#define sysCalloc(x,y)        hpi_memory_interface->Calloc(x,y)
#define sysStrdup(x)          hpi_memory_interface->Strdup(x)
#define sysMapMem(x,y)        hpi_memory_interface->MapMem(x,y)
#define sysUnmapMem(x,y,z)    hpi_memory_interface->UnmapMem(x,y,z)
#define sysCommitMem(x,y,z)   hpi_memory_interface->CommitMem(x,y,z)
#define sysDecommitMem(x,y,z) hpi_memory_interface->DecommitMem(x,y,z)
#define sysAllocBlock(x,y)    hpi_memory_interface->AllocBlock(x,y)
#define sysFreeBlock(x)       hpi_memory_interface->FreeBlock(x)

#define sysBuildLibName(a,b,c,d) hpi_library_interface->BuildLibName(a,b,c,d)
#define sysBuildFunName(a,b,c,d) hpi_library_interface->BuildFunName(a,b,c,d)
#define sysLoadLibrary(a,b,c)        hpi_library_interface->LoadLibrary(a,b,c)
#define sysUnloadLibrary(a)      hpi_library_interface->UnloadLibrary(a)
#define sysFindLibraryEntry(a,b) hpi_library_interface->FindLibraryEntry(a,b)

#define sysGetSysInfo()          hpi_system_interface->GetSysInfo()
#define sysGetMilliTicks()       hpi_system_interface->GetMilliTicks()
#define sysTimeMillis()          hpi_system_interface->TimeMillis()

#define sysSignal(a,b)             hpi_system_interface->Signal(a,b)
#define sysRaise(a)                hpi_system_interface->Raise(a)
#define sysSignalNotify(a)         hpi_system_interface->SignalNotify(a)
#define sysSignalWait()            hpi_system_interface->SignalWait()
#define sysShutdown()              hpi_system_interface->Shutdown()
#define sysSetLoggingLevel(a)      hpi_system_interface->SetLoggingLevel(a)
#define sysSetMonitoringOn(a)      hpi_system_interface->SetMonitoringOn(a)
#define sysGetLastErrorString(a,b) hpi_system_interface->GetLastErrorString(a,b)

#define sysThreadBootstrap(a,b,c)  hpi_thread_interface->ThreadBootstrap(a,b,c)
#define sysThreadCreate(a,b,c,d)   hpi_thread_interface->ThreadCreate(a,b,c,d)
#define sysThreadSelf()            hpi_thread_interface->ThreadSelf()
#define sysThreadYield()           hpi_thread_interface->ThreadYield()
#define sysThreadSuspend(a)        hpi_thread_interface->ThreadSuspend(a)
#define sysThreadResume(a)         hpi_thread_interface->ThreadResume(a)
#define sysThreadSetPriority(a,b)  hpi_thread_interface->ThreadSetPriority(a,b)
#define sysThreadGetPriority(a,b)  hpi_thread_interface->ThreadGetPriority(a,b)
#define sysThreadStackPointer(a)   hpi_thread_interface->ThreadStackPointer(a)
#define sysThreadStackTop(a)       hpi_thread_interface->ThreadStackTop(a)
#define sysThreadRegs(a,b)         hpi_thread_interface->ThreadRegs(a,b)
#define sysThreadSingle()          hpi_thread_interface->ThreadSingle()
#define sysThreadMulti()           hpi_thread_interface->ThreadMulti()
#define sysThreadCheckStack()      hpi_thread_interface->ThreadCheckStack()
#define sysThreadPostException(a,b) \
    hpi_thread_interface->ThreadPostException(a,b)
#define sysThreadInterrupt(a)      hpi_thread_interface->ThreadInterrupt(a)
#define sysThreadIsInterrupted(a,b) \
    hpi_thread_interface->ThreadIsInterrupted(a,b)
#define sysThreadAlloc(a)          hpi_thread_interface->ThreadAlloc(a)
#define sysThreadFree()            hpi_thread_interface->ThreadFree()
#define sysThreadCPUTime()         hpi_thread_interface->ThreadCPUTime()
#define sysThreadGetStatus(a,b)    hpi_thread_interface->ThreadGetStatus(a,b)
#define sysThreadEnumerateOver(a,b) \
    hpi_thread_interface->ThreadEnumerateOver(a,b)
#define sysThreadIsRunning(a)      hpi_thread_interface->ThreadIsRunning(a)
#define sysThreadProfSuspend(a)    hpi_thread_interface->ThreadProfSuspend(a)
#define sysThreadProfResume(a)     hpi_thread_interface->ThreadProfResume(a)
#define sysAdjustTimeSlice(a)      hpi_thread_interface->AdjustTimeSlice(a)

#define sysMonitorSizeof()         hpi_thread_interface->MonitorSizeof()
#define sysMonitorInit(a)          hpi_thread_interface->MonitorInit(a)
#define sysMonitorDestroy(a)       hpi_thread_interface->MonitorDestroy(a)
#define sysMonitorEnter(a,b)       hpi_thread_interface->MonitorEnter(a,b)
#define sysMonitorEntered(a,b)     hpi_thread_interface->MonitorEntered(a,b)
#define sysMonitorExit(a,b)        hpi_thread_interface->MonitorExit(a,b)
#define sysMonitorNotify(a,b)      hpi_thread_interface->MonitorNotify(a,b)
#define sysMonitorNotifyAll(a,b)   hpi_thread_interface->MonitorNotifyAll(a,b)
#define sysMonitorWait(a,b,c)      hpi_thread_interface->MonitorWait(a,b,c)
#define sysMonitorInUse(a)         hpi_thread_interface->MonitorInUse(a)
#define sysMonitorOwner(a)         hpi_thread_interface->MonitorOwner(a)
#define sysMonitorGetInfo(a,b)     hpi_thread_interface->MonitorGetInfo(a,b)

#define sysThreadInterruptEvent()  hpi_thread_interface->ThreadInterruptEvent()
#define sysThreadNativeID(a)       hpi_thread_interface->ThreadNativeID(a)

#define sysNativePath(a)        hpi_file_interface->NativePath(a)
#define sysFileType(a)          hpi_file_interface->FileType(a)
#define sysOpen(a,b,c)          hpi_file_interface->Open(a,b,c)
#define sysClose(a)             hpi_file_interface->Close(a)
#define sysSeek(a,b,c)          hpi_file_interface->Seek(a,b,c)
#define sysSetLength(a,b)       hpi_file_interface->SetLength(a,b)
#define sysSync(a)              hpi_file_interface->Sync(a)
#define sysAvailable(a,b)       hpi_file_interface->Available(a,b)
#define sysRead(a,b,c)          hpi_file_interface->Read(a,b,c)
#define sysWrite(a,b,c)         hpi_file_interface->Write(a,b,c)
#define sysFileSizeFD(a,b)      hpi_file_interface->FileSizeFD(a,b)

#define sysSocketClose(a)        hpi_socket_interface->Close(a)
#define sysSocketShutdown(a,b)   hpi_socket_interface->SocketShutdown(a,b)
#define sysSocketAvailable(a,b)  hpi_socket_interface->Available(a,b)
#define sysConnect(a,b,c)        hpi_socket_interface->Connect(a,b,c)
#define sysBind(a,b,c)           hpi_socket_interface->Bind(a,b,c)
#define sysAccept(a,b,c)         hpi_socket_interface->Accept(a,b,c)
#define sysGetSockName(a,b,c)  hpi_socket_interface->GetSocketName(a,b,c)
#define sysSendTo(a,b,c,d,e,f)   hpi_socket_interface->SendTo(a,b,c,d,e,f)
#define sysRecvFrom(a,b,c,d,e,f) hpi_socket_interface->RecvFrom(a,b,c,d,e,f)
#define sysListen(a,b)           hpi_socket_interface->Listen(a,b)
#define sysRecv(a,b,c,d)         hpi_socket_interface->Recv(a,b,c,d)
#define sysSend(a,b,c,d)         hpi_socket_interface->Send(a,b,c,d)
#define sysTimeout(a,b)          hpi_socket_interface->Timeout(a,b)
#define sysGetHostName(a, b)     hpi_socket_interface->GetHostName(a, b)
#define sysGetHostByAddr(a, b, c) hpi_socket_interface->GetHostByAddr(a, b, c)
#define sysGetHostByName(a)      hpi_socket_interface->GetHostByName(a)
#define sysSocket(a,b,c)         hpi_socket_interface->Socket(a,b,c)
#define sysGetSockOpt(a, b, c, d, e) hpi_socket_interface->SocketGetOption(a, b, c, d, e)
#define sysSetSockOpt(a, b, c, d, e) hpi_socket_interface->SocketSetOption(a, b, c, d, e)
#define sysGetProtoByName(a) hpi_socket_interface->GetProtoByName(a)

#define SYS_SIG_DFL HPI_SIG_DFL
#define SYS_SIG_ERR HPI_SIG_ERR
#define SYS_SIG_IGN HPI_SIG_IGN

#define SYS_OK         HPI_OK
#define SYS_ERR        HPI_ERR
#define SYS_INTRPT     HPI_INTRPT
#define SYS_TIMEOUT    HPI_TIMEOUT
#define SYS_NOMEM      HPI_NOMEM
#define SYS_NORESOURCE HPI_NORESOURCE

#define SYS_THREAD_RUNNABLE     HPI_THREAD_RUNNABLE
#define SYS_THREAD_MONITOR_WAIT HPI_THREAD_MONITOR_WAIT
#define SYS_THREAD_CONDVAR_WAIT HPI_THREAD_CONDVAR_WAIT

#define MinimumPriority     HPI_MINIMUM_PRIORITY
#define MaximumPriority     HPI_MAXIMUM_PRIORITY
#define NormalPriority      HPI_NORMAL_PRIORITY

#define SYS_THREAD_SUSPENDED   HPI_THREAD_SUSPENDED
#define SYS_THREAD_INTERRUPTED HPI_THREAD_INTERRUPTED

#define PAGE_ALIGNMENT      HPI_PAGE_ALIGNMENT

#define SYS_TIMEOUT_INFINITY HPI_TIMEOUT_INFINITY

#define SYS_FILETYPE_REGULAR      HPI_FILETYPE_REGULAR
#define SYS_FILETYPE_DIRECTORY    HPI_FILETYPE_DIRECTORY
#define SYS_FILETYPE_OTHER        HPI_FILETYPE_OTHER

#endif /* !_JAVASOFT_SYS_API_H_ */

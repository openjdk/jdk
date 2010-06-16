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

#ifndef _NT4INTERNALS_H_
#define _NT4INTERNALS_H_

#include <windows.h>

namespace NT4 {
extern "C" {

// Data structures and constants required to be able to get necessary
// debugging-related information on Windows NT 4.0 through internal
// (i.e., non-public) APIs. These are adapted from those in the
// _Windows NT/2000 Native API Reference_ by Gary Nebbett, Macmillan
// Technical Publishing, 201 West 103rd Street, Indianapolis, IN
// 46290, 2000.

typedef LONG NTSTATUS;
typedef LONG KPRIORITY;

#if (_MSC_VER >= 800) || defined(_STDCALL_SUPPORTED)
#define NTAPI __stdcall
#else
#define _cdecl
#define NTAPI
#endif

#define STATUS_INFO_LENGTH_MISMATCH ((NTSTATUS)0xC0000004L)

typedef enum _SYSTEM_INFORMATION_CLASS {
  SystemProcessesAndThreadsInformation = 5
} SYSTEM_INFORMATION_CLASS;

typedef struct _UNICODE_STRING {
  USHORT Length;
  USHORT MaximumLength;
  PWSTR  Buffer;
} UNICODE_STRING;

typedef struct _VM_COUNTERS {
  ULONG PeakVirtualSize;
  ULONG VirtualSize;
  ULONG PageFaultCount;
  ULONG PeakWorkingSetSize;
  ULONG WorkingSetSize;
  ULONG QuotaPeakPagedPoolUsage;
  ULONG QuotaPagedPoolUsage;
  ULONG QuotaPeakNonPagedPoolUsage;
  ULONG QuotaNonPagedPoolUsage;
  ULONG PagefileUsage;
  ULONG PeakPagefileUsage;
} VM_COUNTERS, *PVM_COUNTERS;

typedef struct _IO_COUNTERS {
  LARGE_INTEGER ReadOperationCount;
  LARGE_INTEGER WriteOperationCount;
  LARGE_INTEGER OtherOperationCount;
  LARGE_INTEGER ReadTransferCount;
  LARGE_INTEGER WriteTransferCount;
  LARGE_INTEGER OtherTransferCount;
} IO_COUNTERS, *PIO_COUNTERS;

typedef struct _CLIENT_ID {
  HANDLE UniqueProcess;
  HANDLE UniqueThread;
} CLIENT_ID, *PCLIENT_ID;

typedef enum {
  StateInitialized,
  StateReady,
  StateRunning,
  StateStandby,
  StateTerminated,
  StateWait,
  StateTransition,
  StateUnknown
} THREAD_STATE;

typedef enum {
  Executive,
  FreePage,
  PageIn,
  PoolAllocation,
  DelayExecution,
  Suspended,
  UserRequest,
  WrExecutive,
  WrFreePage,
  WrPageIn,
  WrPoolAllocation,
  WrDelayExecution,
  WrSuspended,
  WrUserRequest,
  WrEventPair,
  WrQueue,
  WrLpcReceive,
  WrLpcReply,
  WrVirtualMemory,
  WrPageOut,
  WrRendezvous,
  Spare2,
  Spare3,
  Spare4,
  Spare5,
  Spare6,
  WrKernel
} KWAIT_REASON;

typedef struct _SYSTEM_THREADS {
  LARGE_INTEGER KernelTime;
  LARGE_INTEGER UserTime;
  LARGE_INTEGER CreateTime;
  ULONG WaitTime;
  PVOID StartAddress;
  CLIENT_ID ClientId;
  KPRIORITY Priority;
  KPRIORITY BasePriority;
  ULONG ContextSwitchCount;
  THREAD_STATE State;
  KWAIT_REASON WaitReason;
} SYSTEM_THREADS, *PSYSTEM_THREADS;

typedef struct _SYSTEM_PROCESSES { // Information class 5
  ULONG NextEntryDelta;
  ULONG ThreadCount;
  ULONG Reserved1[6];
  LARGE_INTEGER CreateTime;
  LARGE_INTEGER UserTime;
  LARGE_INTEGER KernelTime;
  UNICODE_STRING ProcessName;
  KPRIORITY BasePriority;
  ULONG ProcessId;
  ULONG InheritedFromProcessId;
  ULONG HandleCount;
  ULONG Reserved2[2];
  ULONG PrivatePageCount;
  VM_COUNTERS VmCounters;
  IO_COUNTERS IoCounters; // Windows 2000 only
  SYSTEM_THREADS Threads[1];
} SYSTEM_PROCESSES, *PSYSTEM_PROCESSES;

typedef NTSTATUS NTAPI
ZwQuerySystemInformationFunc(IN SYSTEM_INFORMATION_CLASS SystemInformationClass,
                             IN OUT PVOID SystemInformation,
                             IN ULONG SystemInformationLength,
                             OUT PULONG ReturnLength OPTIONAL
                             );

typedef struct _DEBUG_BUFFER {
  HANDLE SectionHandle;
  PVOID  SectionBase;
  PVOID  RemoteSectionBase;
  ULONG  SectionBaseDelta;
  HANDLE EventPairHandle;
  ULONG  Unknown[2];
  HANDLE RemoteThreadHandle;
  ULONG  InfoClassMask;
  ULONG  SizeOfInfo;
  ULONG  AllocatedSize;
  ULONG  SectionSize;
  PVOID  ModuleInformation;
  PVOID  BackTraceInformation;
  PVOID  HeapInformation;
  PVOID  LockInformation;
  PVOID  Reserved[8];
} DEBUG_BUFFER, *PDEBUG_BUFFER;

typedef PDEBUG_BUFFER NTAPI
RtlCreateQueryDebugBufferFunc(IN ULONG Size,
                              IN BOOLEAN EventPair);

#define PDI_MODULES     0x01 // The loaded modules of the process
#define PDI_BACKTRACE   0x02 // The heap stack back traces
#define PDI_HEAPS       0x04 // The heaps of the process
#define PDI_HEAP_TAGS   0x08 // The heap tags
#define PDI_HEAP_BLOCKS 0x10 // The heap blocks
#define PDI_LOCKS       0x20 // The locks created by the process

typedef struct _DEBUG_MODULE_INFORMATION { // c.f. SYSTEM_MODULE_INFORMATION
  ULONG  Reserved[2];
  ULONG  Base;
  ULONG  Size;
  ULONG  Flags;
  USHORT Index;
  USHORT Unknown;
  USHORT LoadCount;
  USHORT ModuleNameOffset;
  CHAR   ImageName[256];
} DEBUG_MODULE_INFORMATION, *PDEBUG_MODULE_INFORMATION;

// Flags
#define LDRP_STATIC_LINK             0x00000002
#define LDRP_IMAGE_DLL               0x00000004
#define LDRP_LOAD_IN_PROGRESS        0x00001000
#define LDRP_UNLOAD_IN_PROGRESS      0x00002000
#define LDRP_ENTRY_PROCESSED         0x00004000
#define LDRP_ENTRY_INSERTED          0x00008000
#define LDRP_CURRENT_LOAD            0x00010000
#define LDRP_FAILED_BUILTIN_LOAD     0x00020000
#define LDRP_DONT_CALL_FOR_THREADS   0x00040000
#define LDRP_PROCESS_ATTACH_CALLED   0x00080000
#define LDRP_DEBUG_SYMBOLS_LOADED    0x00100000
#define LDRP_IMAGE_NOT_AT_BASE       0x00200000
#define LDRP_WX86_IGNORE_MACHINETYPE 0x00400000

// NOTE that this will require creating a thread in the target
// process, implying that we can not call this while the process is
// suspended. May have to run this command in the child processes
// rather than the server.

typedef NTSTATUS NTAPI
RtlQueryProcessDebugInformationFunc(IN ULONG ProcessId,
                                    IN ULONG DebugInfoClassMask,
                                    IN OUT PDEBUG_BUFFER DebugBuffer);

typedef NTSTATUS NTAPI
RtlDestroyQueryDebugBufferFunc(IN PDEBUG_BUFFER DebugBuffer);

// Routines to load and unload NTDLL.DLL.
HMODULE loadNTDLL();
// Safe to call even if has not been loaded
void    unloadNTDLL();

} // extern "C"
} // namespace NT4

//----------------------------------------------------------------------

// On NT 4 only, we now use PSAPI to enumerate the loaded modules in
// the target processes. RtlQueryProcessDebugInformation creates a
// thread in the target process, which causes problems when we are
// handling events like breakpoints in the debugger. The dependence on
// an external DLL which might not be present is unfortunate, but we
// can either redistribute this DLL (if allowed) or refuse to start on
// NT 4 if it is not present.

typedef struct _MODULEINFO {
    LPVOID lpBaseOfDll;
    DWORD SizeOfImage;
    LPVOID EntryPoint;
} MODULEINFO, *LPMODULEINFO;

typedef BOOL (WINAPI EnumProcessModulesFunc)(HANDLE, HMODULE *, DWORD, LPDWORD);
typedef DWORD (WINAPI GetModuleFileNameExFunc)(HANDLE, HMODULE, LPTSTR, DWORD);
typedef BOOL (WINAPI GetModuleInformationFunc)(HANDLE, HMODULE, LPMODULEINFO, DWORD);
// Routines to load and unload PSAPI.DLL.
HMODULE loadPSAPIDLL();
// Safe to call even if has not been loaded
void    unloadPSAPIDLL();

#endif // #defined _NT4INTERNALS_H_

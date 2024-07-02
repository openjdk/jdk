/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

// API level must be at least Windows Vista or Server 2008 to use InitOnceExecuteOnce

// no precompiled headers
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/nativeInst.hpp"
#include "code/vtableStubs.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm.h"
#include "jvmtifiles/jvmti.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "nmt/memTracker.hpp"
#include "oops/oop.inline.hpp"
#include "os_windows.inline.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/orderAccess.hpp"
#include "runtime/osInfo.hpp"
#include "runtime/osThread.hpp"
#include "runtime/park.hpp"
#include "runtime/perfMemory.hpp"
#include "runtime/safefetch.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/semaphore.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/statSampler.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/threadCritical.hpp"
#include "runtime/threads.hpp"
#include "runtime/timer.hpp"
#include "runtime/vm_version.hpp"
#include "services/attachListener.hpp"
#include "services/runtimeService.hpp"
#include "symbolengine.hpp"
#include "utilities/align.hpp"
#include "utilities/decoder.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/events.hpp"
#include "utilities/macros.hpp"
#include "utilities/population_count.hpp"
#include "utilities/vmError.hpp"
#include "windbghelp.hpp"
#if INCLUDE_JFR
#include "jfr/jfrEvents.hpp"
#include "jfr/support/jfrNativeLibraryLoadEvent.hpp"
#endif

#ifdef _DEBUG
#include <crtdbg.h>
#endif

#include <windows.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/timeb.h>
#include <objidl.h>
#include <shlobj.h>

#include <malloc.h>
#include <powerbase.h>
#include <signal.h>
#include <direct.h>
#include <errno.h>
#include <fcntl.h>
#include <io.h>
#include <process.h>              // For _beginthreadex(), _endthreadex()
#include <imagehlp.h>             // For os::dll_address_to_function_name
// for enumerating dll libraries
#include <vdmdbg.h>
#include <psapi.h>
#include <mmsystem.h>
#include <winsock2.h>
#include <versionhelpers.h>

// for timer info max values which include all bits
#define ALL_64_BITS CONST64(-1)

// For DLL loading/load error detection
// Values of PE COFF
#define IMAGE_FILE_PTR_TO_SIGNATURE 0x3c
#define IMAGE_FILE_SIGNATURE_LENGTH 4

static HANDLE main_process;
static HANDLE main_thread;
static int    main_thread_id;

static FILETIME process_creation_time;
static FILETIME process_exit_time;
static FILETIME process_user_time;
static FILETIME process_kernel_time;

#if defined(_M_ARM64)
  #define __CPU__ aarch64
#elif defined(_M_AMD64)
  #define __CPU__ amd64
#else
  #define __CPU__ i486
#endif

#if defined(USE_VECTORED_EXCEPTION_HANDLING)
PVOID  topLevelVectoredExceptionHandler = nullptr;
LPTOP_LEVEL_EXCEPTION_FILTER previousUnhandledExceptionFilter = nullptr;
#endif

// save DLL module handle, used by GetModuleFileName

HINSTANCE vm_lib_handle;

BOOL WINAPI DllMain(HINSTANCE hinst, DWORD reason, LPVOID reserved) {
  switch (reason) {
  case DLL_PROCESS_ATTACH:
    vm_lib_handle = hinst;
    if (ForceTimeHighResolution) {
      timeBeginPeriod(1L);
    }
    WindowsDbgHelp::pre_initialize();
    SymbolEngine::pre_initialize();
    break;
  case DLL_PROCESS_DETACH:
    if (ForceTimeHighResolution) {
      timeEndPeriod(1L);
    }
#if defined(USE_VECTORED_EXCEPTION_HANDLING)
    if (topLevelVectoredExceptionHandler != nullptr) {
      RemoveVectoredExceptionHandler(topLevelVectoredExceptionHandler);
      topLevelVectoredExceptionHandler = nullptr;
    }
#endif
    break;
  default:
    break;
  }
  return true;
}

static inline double fileTimeAsDouble(FILETIME* time) {
  const double high  = (double) ((unsigned int) ~0);
  const double split = 10000000.0;
  double result = (time->dwLowDateTime / split) +
                   time->dwHighDateTime * (high/split);
  return result;
}

// Implementation of os

#define RANGE_FORMAT                "[" PTR_FORMAT "-" PTR_FORMAT ")"
#define RANGE_FORMAT_ARGS(p, len)   p2i(p), p2i((address)p + len)

// A number of wrappers for more frequently used system calls, to add standard logging.

struct PreserveLastError {
  const DWORD v;
  PreserveLastError() : v(::GetLastError()) {}
  ~PreserveLastError() { ::SetLastError(v); }
};

// Logging wrapper for VirtualAlloc
static LPVOID virtualAlloc(LPVOID lpAddress, SIZE_T dwSize, DWORD flAllocationType, DWORD flProtect) {
  LPVOID result = ::VirtualAlloc(lpAddress, dwSize, flAllocationType, flProtect);
  if (result != nullptr) {
    log_trace(os)("VirtualAlloc(" PTR_FORMAT ", " SIZE_FORMAT ", %x, %x) returned " PTR_FORMAT "%s.",
                  p2i(lpAddress), dwSize, flAllocationType, flProtect, p2i(result),
                  ((lpAddress != nullptr && result != lpAddress) ? " <different base!>" : ""));
  } else {
    PreserveLastError ple;
    log_info(os)("VirtualAlloc(" PTR_FORMAT ", " SIZE_FORMAT ", %x, %x) failed (%u).",
                  p2i(lpAddress), dwSize, flAllocationType, flProtect, ple.v);
  }
  return result;
}

// Logging wrapper for VirtualFree
static BOOL virtualFree(LPVOID lpAddress, SIZE_T dwSize, DWORD  dwFreeType) {
  BOOL result = ::VirtualFree(lpAddress, dwSize, dwFreeType);
  if (result != FALSE) {
    log_trace(os)("VirtualFree(" PTR_FORMAT ", " SIZE_FORMAT ", %x) succeeded",
                  p2i(lpAddress), dwSize, dwFreeType);
  } else {
    PreserveLastError ple;
    log_info(os)("VirtualFree(" PTR_FORMAT ", " SIZE_FORMAT ", %x) failed (%u).",
                 p2i(lpAddress), dwSize, dwFreeType, ple.v);
  }
  return result;
}

// Logging wrapper for VirtualAllocExNuma
static LPVOID virtualAllocExNuma(HANDLE hProcess, LPVOID lpAddress, SIZE_T dwSize, DWORD  flAllocationType,
                                 DWORD  flProtect, DWORD  nndPreferred) {
  LPVOID result = ::VirtualAllocExNuma(hProcess, lpAddress, dwSize, flAllocationType, flProtect, nndPreferred);
  if (result != nullptr) {
    log_trace(os)("VirtualAllocExNuma(" PTR_FORMAT ", " SIZE_FORMAT ", %x, %x, %x) returned " PTR_FORMAT "%s.",
                  p2i(lpAddress), dwSize, flAllocationType, flProtect, nndPreferred, p2i(result),
                  ((lpAddress != nullptr && result != lpAddress) ? " <different base!>" : ""));
  } else {
    PreserveLastError ple;
    log_info(os)("VirtualAllocExNuma(" PTR_FORMAT ", " SIZE_FORMAT ", %x, %x, %x) failed (%u).",
                 p2i(lpAddress), dwSize, flAllocationType, flProtect, nndPreferred, ple.v);
  }
  return result;
}

// Logging wrapper for MapViewOfFileEx
static LPVOID mapViewOfFileEx(HANDLE hFileMappingObject, DWORD  dwDesiredAccess, DWORD  dwFileOffsetHigh,
                              DWORD  dwFileOffsetLow, SIZE_T dwNumberOfBytesToMap, LPVOID lpBaseAddress) {
  LPVOID result = ::MapViewOfFileEx(hFileMappingObject, dwDesiredAccess, dwFileOffsetHigh,
                                    dwFileOffsetLow, dwNumberOfBytesToMap, lpBaseAddress);
  if (result != nullptr) {
    log_trace(os)("MapViewOfFileEx(" PTR_FORMAT ", " SIZE_FORMAT ") returned " PTR_FORMAT "%s.",
                  p2i(lpBaseAddress), dwNumberOfBytesToMap, p2i(result),
                  ((lpBaseAddress != nullptr && result != lpBaseAddress) ? " <different base!>" : ""));
  } else {
    PreserveLastError ple;
    log_info(os)("MapViewOfFileEx(" PTR_FORMAT ", " SIZE_FORMAT ") failed (%u).",
                 p2i(lpBaseAddress), dwNumberOfBytesToMap, ple.v);
  }
  return result;
}

// Logging wrapper for UnmapViewOfFile
static BOOL unmapViewOfFile(LPCVOID lpBaseAddress) {
  BOOL result = ::UnmapViewOfFile(lpBaseAddress);
  if (result != FALSE) {
    log_trace(os)("UnmapViewOfFile(" PTR_FORMAT ") succeeded", p2i(lpBaseAddress));
  } else {
    PreserveLastError ple;
    log_info(os)("UnmapViewOfFile(" PTR_FORMAT ") failed (%u).",  p2i(lpBaseAddress), ple.v);
  }
  return result;
}

char** os::get_environ() { return _environ; }

// No setuid programs under Windows.
bool os::have_special_privileges() {
  return false;
}


// This method is a periodic task to check for misbehaving JNI applications
// under CheckJNI, we can add any periodic checks here.
// For Windows at the moment does nothing
void os::run_periodic_checks(outputStream* st) {
  return;
}

#ifndef _WIN64
// previous UnhandledExceptionFilter, if there is one
static LPTOP_LEVEL_EXCEPTION_FILTER prev_uef_handler = nullptr;
#endif

static LONG WINAPI Uncaught_Exception_Handler(struct _EXCEPTION_POINTERS* exceptionInfo);

void os::init_system_properties_values() {
  // sysclasspath, java_home, dll_dir
  {
    char *home_path;
    char *dll_path;
    char *pslash;
    const char *bin = "\\bin";
    char home_dir[MAX_PATH + 1];
    char *alt_home_dir = ::getenv("_ALT_JAVA_HOME_DIR");

    if (alt_home_dir != nullptr)  {
      strncpy(home_dir, alt_home_dir, MAX_PATH + 1);
      home_dir[MAX_PATH] = '\0';
    } else {
      os::jvm_path(home_dir, sizeof(home_dir));
      // Found the full path to jvm.dll.
      // Now cut the path to <java_home>/jre if we can.
      *(strrchr(home_dir, '\\')) = '\0';  // get rid of \jvm.dll
      pslash = strrchr(home_dir, '\\');
      if (pslash != nullptr) {
        *pslash = '\0';                   // get rid of \{client|server}
        pslash = strrchr(home_dir, '\\');
        if (pslash != nullptr) {
          *pslash = '\0';                 // get rid of \bin
        }
      }
    }

    home_path = NEW_C_HEAP_ARRAY(char, strlen(home_dir) + 1, mtInternal);
    strcpy(home_path, home_dir);
    Arguments::set_java_home(home_path);
    FREE_C_HEAP_ARRAY(char, home_path);

    dll_path = NEW_C_HEAP_ARRAY(char, strlen(home_dir) + strlen(bin) + 1,
                                mtInternal);
    strcpy(dll_path, home_dir);
    strcat(dll_path, bin);
    Arguments::set_dll_dir(dll_path);
    FREE_C_HEAP_ARRAY(char, dll_path);

    if (!set_boot_path('\\', ';')) {
      vm_exit_during_initialization("Failed setting boot class path.", nullptr);
    }
  }

// library_path
#define EXT_DIR "\\lib\\ext"
#define BIN_DIR "\\bin"
#define PACKAGE_DIR "\\Sun\\Java"
  {
    // Win32 library search order (See the documentation for LoadLibrary):
    //
    // 1. The directory from which application is loaded.
    // 2. The system wide Java Extensions directory (Java only)
    // 3. System directory (GetSystemDirectory)
    // 4. Windows directory (GetWindowsDirectory)
    // 5. The PATH environment variable
    // 6. The current directory

    char *library_path;
    char tmp[MAX_PATH];
    char *path_str = ::getenv("PATH");

    library_path = NEW_C_HEAP_ARRAY(char, MAX_PATH * 5 + sizeof(PACKAGE_DIR) +
                                    sizeof(BIN_DIR) + (path_str ? strlen(path_str) : 0) + 10, mtInternal);

    library_path[0] = '\0';

    GetModuleFileName(nullptr, tmp, sizeof(tmp));
    *(strrchr(tmp, '\\')) = '\0';
    strcat(library_path, tmp);

    GetWindowsDirectory(tmp, sizeof(tmp));
    strcat(library_path, ";");
    strcat(library_path, tmp);
    strcat(library_path, PACKAGE_DIR BIN_DIR);

    GetSystemDirectory(tmp, sizeof(tmp));
    strcat(library_path, ";");
    strcat(library_path, tmp);

    GetWindowsDirectory(tmp, sizeof(tmp));
    strcat(library_path, ";");
    strcat(library_path, tmp);

    if (path_str) {
      strcat(library_path, ";");
      strcat(library_path, path_str);
    }

    strcat(library_path, ";.");

    Arguments::set_library_path(library_path);
    FREE_C_HEAP_ARRAY(char, library_path);
  }

  // Default extensions directory
  {
    char path[MAX_PATH];
    char buf[2 * MAX_PATH + 2 * sizeof(EXT_DIR) + sizeof(PACKAGE_DIR) + 1];
    GetWindowsDirectory(path, MAX_PATH);
    os::snprintf_checked(buf, sizeof(buf), "%s%s;%s%s%s",
                         Arguments::get_java_home(), EXT_DIR,
                         path, PACKAGE_DIR, EXT_DIR);
    Arguments::set_ext_dirs(buf);
  }
  #undef EXT_DIR
  #undef BIN_DIR
  #undef PACKAGE_DIR

#ifndef _WIN64
  // set our UnhandledExceptionFilter and save any previous one
  prev_uef_handler = SetUnhandledExceptionFilter(Uncaught_Exception_Handler);
#endif

  // Done
  return;
}

void os::breakpoint() {
  DebugBreak();
}

// Invoked from the BREAKPOINT Macro
extern "C" void breakpoint() {
  os::breakpoint();
}

// RtlCaptureStackBackTrace Windows API may not exist prior to Windows XP.
// So far, this method is only used by Native Memory Tracking, which is
// only supported on Windows XP or later.
//
int os::get_native_stack(address* stack, int frames, int toSkip) {
  int captured = RtlCaptureStackBackTrace(toSkip + 1, frames, (PVOID*)stack, nullptr);
  for (int index = captured; index < frames; index ++) {
    stack[index] = nullptr;
  }
  return captured;
}

//   Returns the base of the stack, which is the stack's
//   starting address.  This function must be called
//   while running on the stack of the thread being queried.

void os::current_stack_base_and_size(address* stack_base, size_t* stack_size) {
  MEMORY_BASIC_INFORMATION minfo;
  address stack_bottom;
  size_t size;

  VirtualQuery(&minfo, &minfo, sizeof(minfo));
  stack_bottom = (address)minfo.AllocationBase;
  size = minfo.RegionSize;

  // Add up the sizes of all the regions with the same
  // AllocationBase.
  while (1) {
    VirtualQuery(stack_bottom + size, &minfo, sizeof(minfo));
    if (stack_bottom == (address)minfo.AllocationBase) {
      size += minfo.RegionSize;
    } else {
      break;
    }
  }
  *stack_base = stack_bottom + size;
  *stack_size = size;
}

bool os::committed_in_range(address start, size_t size, address& committed_start, size_t& committed_size) {
  MEMORY_BASIC_INFORMATION minfo;
  committed_start = nullptr;
  committed_size = 0;
  address top = start + size;
  const address start_addr = start;
  while (start < top) {
    VirtualQuery(start, &minfo, sizeof(minfo));
    if ((minfo.State & MEM_COMMIT) == 0) {  // not committed
      if (committed_start != nullptr) {
        break;
      }
    } else {  // committed
      if (committed_start == nullptr) {
        committed_start = start;
      }
      size_t offset = start - (address)minfo.BaseAddress;
      committed_size += minfo.RegionSize - offset;
    }
    start = (address)minfo.BaseAddress + minfo.RegionSize;
  }

  if (committed_start == nullptr) {
    assert(committed_size == 0, "Sanity");
    return false;
  } else {
    assert(committed_start >= start_addr && committed_start < top, "Out of range");
    // current region may go beyond the limit, trim to the limit
    committed_size = MIN2(committed_size, size_t(top - committed_start));
    return true;
  }
}

struct tm* os::localtime_pd(const time_t* clock, struct tm* res) {
  const struct tm* time_struct_ptr = localtime(clock);
  if (time_struct_ptr != nullptr) {
    *res = *time_struct_ptr;
    return res;
  }
  return nullptr;
}

struct tm* os::gmtime_pd(const time_t* clock, struct tm* res) {
  const struct tm* time_struct_ptr = gmtime(clock);
  if (time_struct_ptr != nullptr) {
    *res = *time_struct_ptr;
    return res;
  }
  return nullptr;
}

enum Ept { EPT_THREAD, EPT_PROCESS, EPT_PROCESS_DIE };
// Wrapper around _endthreadex(), exit() and _exit()
[[noreturn]]
static void exit_process_or_thread(Ept what, int code);

JNIEXPORT
LONG WINAPI topLevelExceptionFilter(struct _EXCEPTION_POINTERS* exceptionInfo);

// Thread start routine for all newly created threads.
// Called with the associated Thread* as the argument.
static unsigned __stdcall thread_native_entry(void* t) {
  Thread* thread = static_cast<Thread*>(t);

  thread->record_stack_base_and_size();
  thread->initialize_thread_current();

  OSThread* osthr = thread->osthread();
  assert(osthr->get_state() == RUNNABLE, "invalid os thread state");

  if (UseNUMA) {
    int lgrp_id = os::numa_get_group_id();
    if (lgrp_id != -1) {
      thread->set_lgrp_id(lgrp_id);
    }
  }

  // Diagnostic code to investigate JDK-6573254
  int res = 30115;  // non-java thread
  if (thread->is_Java_thread()) {
    res = 20115;    // java thread
  }

  log_info(os, thread)("Thread is alive (tid: " UINTX_FORMAT ", stacksize: " SIZE_FORMAT "k).", os::current_thread_id(), thread->stack_size() / K);

#ifdef USE_VECTORED_EXCEPTION_HANDLING
  // Any exception is caught by the Vectored Exception Handler, so VM can
  // generate error dump when an exception occurred in non-Java thread
  // (e.g. VM thread).
  thread->call_run();
#else
  // Install a win32 structured exception handler around every thread created
  // by VM, so VM can generate error dump when an exception occurred in non-
  // Java thread (e.g. VM thread).
  __try {
    thread->call_run();
  } __except(topLevelExceptionFilter(
                                     (_EXCEPTION_POINTERS*)_exception_info())) {
    // Nothing to do.
  }
#endif

  // Note: at this point the thread object may already have deleted itself.
  // Do not dereference it from here on out.

  log_info(os, thread)("Thread finished (tid: " UINTX_FORMAT ").", os::current_thread_id());

  // Thread must not return from exit_process_or_thread(), but if it does,
  // let it proceed to exit normally
  exit_process_or_thread(EPT_THREAD, res);
  return res;
}

static OSThread* create_os_thread(Thread* thread, HANDLE thread_handle,
                                  int thread_id) {
  // Allocate the OSThread object
  OSThread* osthread = new (std::nothrow) OSThread();
  if (osthread == nullptr) return nullptr;

  // Initialize the JDK library's interrupt event.
  // This should really be done when OSThread is constructed,
  // but there is no way for a constructor to report failure to
  // allocate the event.
  HANDLE interrupt_event = CreateEvent(nullptr, true, false, nullptr);
  if (interrupt_event == nullptr) {
    delete osthread;
    return nullptr;
  }
  osthread->set_interrupt_event(interrupt_event);

  // Store info on the Win32 thread into the OSThread
  osthread->set_thread_handle(thread_handle);
  osthread->set_thread_id(thread_id);

  if (UseNUMA) {
    int lgrp_id = os::numa_get_group_id();
    if (lgrp_id != -1) {
      thread->set_lgrp_id(lgrp_id);
    }
  }

  // Initial thread state is INITIALIZED, not SUSPENDED
  osthread->set_state(INITIALIZED);

  return osthread;
}


bool os::create_attached_thread(JavaThread* thread) {
#ifdef ASSERT
  thread->verify_not_published();
#endif
  HANDLE thread_h;
  if (!DuplicateHandle(main_process, GetCurrentThread(), GetCurrentProcess(),
                       &thread_h, THREAD_ALL_ACCESS, false, 0)) {
    fatal("DuplicateHandle failed\n");
  }
  OSThread* osthread = create_os_thread(thread, thread_h,
                                        (int)current_thread_id());
  if (osthread == nullptr) {
    return false;
  }

  // Initial thread state is RUNNABLE
  osthread->set_state(RUNNABLE);

  thread->set_osthread(osthread);

  log_info(os, thread)("Thread attached (tid: " UINTX_FORMAT ", stack: "
                       PTR_FORMAT " - " PTR_FORMAT " (" SIZE_FORMAT "K) ).",
                       os::current_thread_id(), p2i(thread->stack_base()),
                       p2i(thread->stack_end()), thread->stack_size() / K);

  return true;
}

bool os::create_main_thread(JavaThread* thread) {
#ifdef ASSERT
  thread->verify_not_published();
#endif
  if (_starting_thread == nullptr) {
    _starting_thread = create_os_thread(thread, main_thread, main_thread_id);
    if (_starting_thread == nullptr) {
      return false;
    }
  }

  // The primordial thread is runnable from the start)
  _starting_thread->set_state(RUNNABLE);

  thread->set_osthread(_starting_thread);
  return true;
}

// Helper function to trace _beginthreadex attributes,
//  similar to os::Posix::describe_pthread_attr()
static char* describe_beginthreadex_attributes(char* buf, size_t buflen,
                                               size_t stacksize, unsigned initflag) {
  stringStream ss(buf, buflen);
  if (stacksize == 0) {
    ss.print("stacksize: default, ");
  } else {
    ss.print("stacksize: " SIZE_FORMAT "k, ", stacksize / K);
  }
  ss.print("flags: ");
  #define PRINT_FLAG(f) if (initflag & f) ss.print( #f " ");
  #define ALL(X) \
    X(CREATE_SUSPENDED) \
    X(STACK_SIZE_PARAM_IS_A_RESERVATION)
  ALL(PRINT_FLAG)
  #undef ALL
  #undef PRINT_FLAG
  return buf;
}

// Allocate and initialize a new OSThread
bool os::create_thread(Thread* thread, ThreadType thr_type,
                       size_t stack_size) {
  unsigned thread_id;

  // Allocate the OSThread object
  OSThread* osthread = new (std::nothrow) OSThread();
  if (osthread == nullptr) {
    return false;
  }

  // Initial state is ALLOCATED but not INITIALIZED
  osthread->set_state(ALLOCATED);

  // Initialize the JDK library's interrupt event.
  // This should really be done when OSThread is constructed,
  // but there is no way for a constructor to report failure to
  // allocate the event.
  HANDLE interrupt_event = CreateEvent(nullptr, true, false, nullptr);
  if (interrupt_event == nullptr) {
    delete osthread;
    return false;
  }
  osthread->set_interrupt_event(interrupt_event);
  // We don't call set_interrupted(false) as it will trip the assert in there
  // as we are not operating on the current thread. We don't need to call it
  // because the initial state is already correct.

  thread->set_osthread(osthread);

  if (stack_size == 0) {
    switch (thr_type) {
    case os::java_thread:
      // Java threads use ThreadStackSize which default value can be changed with the flag -Xss
      if (JavaThread::stack_size_at_create() > 0) {
        stack_size = JavaThread::stack_size_at_create();
      }
      break;
    case os::compiler_thread:
      if (CompilerThreadStackSize > 0) {
        stack_size = (size_t)(CompilerThreadStackSize * K);
        break;
      } // else fall through:
        // use VMThreadStackSize if CompilerThreadStackSize is not defined
    case os::vm_thread:
    case os::gc_thread:
    case os::asynclog_thread:
    case os::watcher_thread:
    default:  // presume the unknown thread type is an internal VM one
      if (VMThreadStackSize > 0) stack_size = (size_t)(VMThreadStackSize * K);
      break;
    }
  }

  // Create the Win32 thread
  //
  // Contrary to what MSDN document says, "stack_size" in _beginthreadex()
  // does not specify stack size. Instead, it specifies the size of
  // initially committed space. The stack size is determined by
  // PE header in the executable. If the committed "stack_size" is larger
  // than default value in the PE header, the stack is rounded up to the
  // nearest multiple of 1MB. For example if the launcher has default
  // stack size of 320k, specifying any size less than 320k does not
  // affect the actual stack size at all, it only affects the initial
  // commitment. On the other hand, specifying 'stack_size' larger than
  // default value may cause significant increase in memory usage, because
  // not only the stack space will be rounded up to MB, but also the
  // entire space is committed upfront.
  //
  // Finally Windows XP added a new flag 'STACK_SIZE_PARAM_IS_A_RESERVATION'
  // for CreateThread() that can treat 'stack_size' as stack size. However we
  // are not supposed to call CreateThread() directly according to MSDN
  // document because JVM uses C runtime library. The good news is that the
  // flag appears to work with _beginthredex() as well.

  const unsigned initflag = CREATE_SUSPENDED | STACK_SIZE_PARAM_IS_A_RESERVATION;
  HANDLE thread_handle;
  int limit = 3;
  do {
    thread_handle =
      (HANDLE)_beginthreadex(nullptr,
                             (unsigned)stack_size,
                             &thread_native_entry,
                             thread,
                             initflag,
                             &thread_id);
  } while (thread_handle == nullptr && errno == EAGAIN && limit-- > 0);

  ResourceMark rm;
  char buf[64];
  if (thread_handle != nullptr) {
    log_info(os, thread)("Thread \"%s\" started (tid: %u, attributes: %s)",
                         thread->name(), thread_id,
                         describe_beginthreadex_attributes(buf, sizeof(buf), stack_size, initflag));
  } else {
    log_warning(os, thread)("Failed to start thread \"%s\" - _beginthreadex failed (%s) for attributes: %s.",
                            thread->name(), os::errno_name(errno), describe_beginthreadex_attributes(buf, sizeof(buf), stack_size, initflag));
    // Log some OS information which might explain why creating the thread failed.
    log_info(os, thread)("Number of threads approx. running in the VM: %d", Threads::number_of_threads());
    LogStream st(Log(os, thread)::info());
    os::print_memory_info(&st);
  }

  if (thread_handle == nullptr) {
    // Need to clean up stuff we've allocated so far
    thread->set_osthread(nullptr);
    delete osthread;
    return false;
  }

  // Store info on the Win32 thread into the OSThread
  osthread->set_thread_handle(thread_handle);
  osthread->set_thread_id(thread_id);

  // Thread state now is INITIALIZED, not SUSPENDED
  osthread->set_state(INITIALIZED);

  // The thread is returned suspended (in state INITIALIZED), and is started higher up in the call chain
  return true;
}


// Free Win32 resources related to the OSThread
void os::free_thread(OSThread* osthread) {
  assert(osthread != nullptr, "osthread not set");

  // We are told to free resources of the argument thread,
  // but we can only really operate on the current thread.
  assert(Thread::current()->osthread() == osthread,
         "os::free_thread but not current thread");

  CloseHandle(osthread->thread_handle());
  delete osthread;
}

static jlong first_filetime;
static jlong initial_performance_count;
static jlong performance_frequency;
static double nanos_per_count; // NANOSECS_PER_SEC / performance_frequency

jlong os::elapsed_counter() {
  LARGE_INTEGER count;
  QueryPerformanceCounter(&count);
  return count.QuadPart - initial_performance_count;
}


jlong os::elapsed_frequency() {
  return performance_frequency;
}


julong os::available_memory() {
  return win32::available_memory();
}

julong os::free_memory() {
  return win32::available_memory();
}

julong os::win32::available_memory() {
  // Use GlobalMemoryStatusEx() because GlobalMemoryStatus() may return incorrect
  // value if total memory is larger than 4GB
  MEMORYSTATUSEX ms;
  ms.dwLength = sizeof(ms);
  GlobalMemoryStatusEx(&ms);

  return (julong)ms.ullAvailPhys;
}

jlong os::total_swap_space() {
  MEMORYSTATUSEX ms;
  ms.dwLength = sizeof(ms);
  GlobalMemoryStatusEx(&ms);
  return (jlong) ms.ullTotalPageFile;
}

jlong os::free_swap_space() {
  MEMORYSTATUSEX ms;
  ms.dwLength = sizeof(ms);
  GlobalMemoryStatusEx(&ms);
  return (jlong) ms.ullAvailPageFile;
}

julong os::physical_memory() {
  return win32::physical_memory();
}

size_t os::rss() {
  size_t rss = 0;
  PROCESS_MEMORY_COUNTERS_EX pmex;
  ZeroMemory(&pmex, sizeof(PROCESS_MEMORY_COUNTERS_EX));
  pmex.cb = sizeof(pmex);
  BOOL ret = GetProcessMemoryInfo(
      GetCurrentProcess(), (PROCESS_MEMORY_COUNTERS *)&pmex, sizeof(pmex));
  if (ret) {
    rss = pmex.WorkingSetSize;
  }
  return rss;
}

bool os::has_allocatable_memory_limit(size_t* limit) {
  MEMORYSTATUSEX ms;
  ms.dwLength = sizeof(ms);
  GlobalMemoryStatusEx(&ms);
#ifdef _LP64
  *limit = (size_t)ms.ullAvailVirtual;
  return true;
#else
  // Limit to 1400m because of the 2gb address space wall
  *limit = MIN2((size_t)1400*M, (size_t)ms.ullAvailVirtual);
  return true;
#endif
}

int os::active_processor_count() {
  // User has overridden the number of active processors
  if (ActiveProcessorCount > 0) {
    log_trace(os)("active_processor_count: "
                  "active processor count set by user : %d",
                  ActiveProcessorCount);
    return ActiveProcessorCount;
  }

  bool schedules_all_processor_groups = win32::is_windows_11_or_greater() || win32::is_windows_server_2022_or_greater();
  if (UseAllWindowsProcessorGroups && !schedules_all_processor_groups && !win32::processor_group_warning_displayed()) {
    win32::set_processor_group_warning_displayed(true);
    FLAG_SET_DEFAULT(UseAllWindowsProcessorGroups, false);
    warning("The UseAllWindowsProcessorGroups flag is not supported on this Windows version and will be ignored.");
  }

  DWORD active_processor_groups = 0;
  DWORD processors_in_job_object = win32::active_processors_in_job_object(&active_processor_groups);

  if (processors_in_job_object > 0) {
    if (schedules_all_processor_groups) {
      // If UseAllWindowsProcessorGroups is enabled then all the processors in the job object
      // can be used. Otherwise, we will fall through to inspecting the process affinity mask.
      // This will result in using only the subset of the processors in the default processor
      // group allowed by the job object i.e. only 1 processor group will be used and only
      // the processors in that group that are allowed by the job object will be used.
      // This preserves the behavior where older OpenJDK versions always used one processor
      // group regardless of whether they were launched in a job object.
      if (!UseAllWindowsProcessorGroups && active_processor_groups > 1) {
        if (!win32::job_object_processor_group_warning_displayed()) {
          win32::set_job_object_processor_group_warning_displayed(true);
          warning("The Windows job object has enabled multiple processor groups (%d) but the UseAllWindowsProcessorGroups flag is off. Some processors might not be used.", active_processor_groups);
        }
      } else {
        return processors_in_job_object;
      }
    } else {
      if (active_processor_groups > 1 && !win32::job_object_processor_group_warning_displayed()) {
        win32::set_job_object_processor_group_warning_displayed(true);
        warning("The Windows job object has enabled multiple processor groups (%d) but only 1 is supported on this Windows version. Some processors might not be used.", active_processor_groups);
      }
      return processors_in_job_object;
    }
  }

  DWORD logical_processors = 0;
  SYSTEM_INFO si;
  GetSystemInfo(&si);

  USHORT group_count = 0;
  bool use_process_affinity_mask = false;
  bool got_process_group_affinity = false;

  if (GetProcessGroupAffinity(GetCurrentProcess(), &group_count, nullptr) == 0) {
    DWORD last_error = GetLastError();
    if (last_error == ERROR_INSUFFICIENT_BUFFER) {
      if (group_count > 0) {
        got_process_group_affinity = true;

        if (group_count == 1) {
          use_process_affinity_mask = true;
        }
      } else {
        warning("Unexpected group count of 0 from GetProcessGroupAffinity.");
        assert(false, "Group count must not be 0.");
      }
    } else {
      char buf[512];
      size_t buf_len = os::lasterror(buf, sizeof(buf));
      warning("Attempt to get process group affinity failed: %s", buf_len != 0 ? buf : "<unknown error>");
    }
  } else {
    warning("Unexpected GetProcessGroupAffinity success result.");
    assert(false, "Unexpected GetProcessGroupAffinity success result");
  }

  // Fall back to SYSTEM_INFO.dwNumberOfProcessors if the process group affinity could not be determined.
  if (!got_process_group_affinity) {
    return si.dwNumberOfProcessors;
  }

  // If the process it not in a job and the process group affinity is exactly 1 group
  // then get the number of available logical processors from the process affinity mask
  if (use_process_affinity_mask) {
    DWORD_PTR lpProcessAffinityMask = 0;
    DWORD_PTR lpSystemAffinityMask = 0;
    if (GetProcessAffinityMask(GetCurrentProcess(), &lpProcessAffinityMask, &lpSystemAffinityMask) != 0) {
      // Number of active processors is number of bits in process affinity mask
      logical_processors = population_count(lpProcessAffinityMask);

      if (logical_processors > 0) {
        return logical_processors;
      } else {
        // We only check the process affinity mask if GetProcessGroupAffinity determined that there was
        // only 1 active group. In this case, GetProcessAffinityMask will not set the affinity mask to 0.
        warning("Unexpected process affinity mask of 0 from GetProcessAffinityMask.");
        assert(false, "Found unexpected process affinity mask: 0");
      }
    } else {
      char buf[512];
      size_t buf_len = os::lasterror(buf, sizeof(buf));
      warning("Attempt to get the process affinity mask failed: %s", buf_len != 0 ? buf : "<unknown error>");
    }

    // Fall back to SYSTEM_INFO.dwNumberOfProcessors if the process affinity mask could not be determined.
    return si.dwNumberOfProcessors;
  }

  if (UseAllWindowsProcessorGroups) {
    // There are no processor affinity restrictions at this point so we can return
    // the overall processor count if the OS automatically schedules threads across
    // all processors on the system. Note that older operating systems can
    // correctly report processor count but will not schedule threads across
    // processor groups unless the application explicitly uses group affinity APIs
    // to assign threads to processor groups. On these older operating systems, we
    // will continue to use the dwNumberOfProcessors field.
    if (schedules_all_processor_groups) {
      logical_processors = processor_count();
    }
  }

  return logical_processors == 0 ? si.dwNumberOfProcessors : logical_processors;
}

uint os::processor_id() {
  return (uint)GetCurrentProcessorNumber();
}

// For dynamic lookup of SetThreadDescription API
typedef HRESULT (WINAPI *SetThreadDescriptionFnPtr)(HANDLE, PCWSTR);
typedef HRESULT (WINAPI *GetThreadDescriptionFnPtr)(HANDLE, PWSTR*);
static SetThreadDescriptionFnPtr _SetThreadDescription = nullptr;
DEBUG_ONLY(static GetThreadDescriptionFnPtr _GetThreadDescription = nullptr;)

// forward decl.
static errno_t convert_to_unicode(char const* char_path, LPWSTR* unicode_path);

void os::set_native_thread_name(const char *name) {

  // From Windows 10 and Windows 2016 server, we have a direct API
  // for setting the thread name/description:
  // https://docs.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-setthreaddescription

  if (_SetThreadDescription != nullptr) {
    // SetThreadDescription takes a PCWSTR but we have conversion routines that produce
    // LPWSTR. The only difference is that PCWSTR is a pointer to const WCHAR.
    LPWSTR unicode_name;
    errno_t err = convert_to_unicode(name, &unicode_name);
    if (err == ERROR_SUCCESS) {
      HANDLE current = GetCurrentThread();
      HRESULT hr = _SetThreadDescription(current, unicode_name);
      if (FAILED(hr)) {
        log_debug(os, thread)("set_native_thread_name: SetThreadDescription failed - falling back to debugger method");
        FREE_C_HEAP_ARRAY(WCHAR, unicode_name);
      } else {
        log_trace(os, thread)("set_native_thread_name: SetThreadDescription succeeded - new name: %s", name);

#ifdef ASSERT
        // For verification purposes in a debug build we read the thread name back and check it.
        PWSTR thread_name;
        HRESULT hr2 = _GetThreadDescription(current, &thread_name);
        if (FAILED(hr2)) {
          log_debug(os, thread)("set_native_thread_name: GetThreadDescription failed!");
        } else {
          int res = CompareStringW(LOCALE_USER_DEFAULT,
                                   0, // no special comparison rules
                                   unicode_name,
                                   -1, // null-terminated
                                   thread_name,
                                   -1  // null-terminated
                                   );
          assert(res == CSTR_EQUAL,
                 "Name strings were not the same - set: %ls, but read: %ls", unicode_name, thread_name);
          LocalFree(thread_name);
        }
#endif
        FREE_C_HEAP_ARRAY(WCHAR, unicode_name);
        return;
      }
    } else {
      log_debug(os, thread)("set_native_thread_name: convert_to_unicode failed - falling back to debugger method");
    }
  }

  // See: http://msdn.microsoft.com/en-us/library/xcb2z8hs.aspx
  //
  // Note that unfortunately this only works if the process
  // is already attached to a debugger; debugger must observe
  // the exception below to show the correct name.

  // If there is no debugger attached skip raising the exception
  if (!IsDebuggerPresent()) {
    log_debug(os, thread)("set_native_thread_name: no debugger present so unable to set thread name");
    return;
  }

  const DWORD MS_VC_EXCEPTION = 0x406D1388;
  struct {
    DWORD dwType;     // must be 0x1000
    LPCSTR szName;    // pointer to name (in user addr space)
    DWORD dwThreadID; // thread ID (-1=caller thread)
    DWORD dwFlags;    // reserved for future use, must be zero
  } info;

  info.dwType = 0x1000;
  info.szName = name;
  info.dwThreadID = -1;
  info.dwFlags = 0;

  __try {
    RaiseException (MS_VC_EXCEPTION, 0, sizeof(info)/sizeof(DWORD), (const ULONG_PTR*)&info );
  } __except(EXCEPTION_EXECUTE_HANDLER) {}
}

void os::win32::initialize_performance_counter() {
  LARGE_INTEGER count;
  QueryPerformanceFrequency(&count);
  performance_frequency = count.QuadPart;
  nanos_per_count = NANOSECS_PER_SEC / (double)performance_frequency;
  QueryPerformanceCounter(&count);
  initial_performance_count = count.QuadPart;
}


double os::elapsedTime() {
  return (double) elapsed_counter() / (double) elapsed_frequency();
}


// Windows format:
//   The FILETIME structure is a 64-bit value representing the number of 100-nanosecond intervals since January 1, 1601.
// Java format:
//   Java standards require the number of milliseconds since 1/1/1970

// Constant offset - calculated using offset()
static jlong  _offset   = 116444736000000000;
// Fake time counter for reproducible results when debugging
static jlong  fake_time = 0;

#ifdef ASSERT
// Just to be safe, recalculate the offset in debug mode
static jlong _calculated_offset = 0;
static int   _has_calculated_offset = 0;

jlong offset() {
  if (_has_calculated_offset) return _calculated_offset;
  SYSTEMTIME java_origin;
  java_origin.wYear          = 1970;
  java_origin.wMonth         = 1;
  java_origin.wDayOfWeek     = 0; // ignored
  java_origin.wDay           = 1;
  java_origin.wHour          = 0;
  java_origin.wMinute        = 0;
  java_origin.wSecond        = 0;
  java_origin.wMilliseconds  = 0;
  FILETIME jot;
  if (!SystemTimeToFileTime(&java_origin, &jot)) {
    fatal("Error = %d\nWindows error", GetLastError());
  }
  _calculated_offset = jlong_from(jot.dwHighDateTime, jot.dwLowDateTime);
  _has_calculated_offset = 1;
  assert(_calculated_offset == _offset, "Calculated and constant time offsets must be equal");
  return _calculated_offset;
}
#else
jlong offset() {
  return _offset;
}
#endif

jlong windows_to_java_time(FILETIME wt) {
  jlong a = jlong_from(wt.dwHighDateTime, wt.dwLowDateTime);
  return (a - offset()) / 10000;
}

// Returns time ticks in (10th of micro seconds)
jlong windows_to_time_ticks(FILETIME wt) {
  jlong a = jlong_from(wt.dwHighDateTime, wt.dwLowDateTime);
  return (a - offset());
}

FILETIME java_to_windows_time(jlong l) {
  jlong a = (l * 10000) + offset();
  FILETIME result;
  result.dwHighDateTime = high(a);
  result.dwLowDateTime  = low(a);
  return result;
}

bool os::supports_vtime() { return true; }

double os::elapsedVTime() {
  FILETIME created;
  FILETIME exited;
  FILETIME kernel;
  FILETIME user;
  if (GetThreadTimes(GetCurrentThread(), &created, &exited, &kernel, &user) != 0) {
    // the resolution of windows_to_java_time() should be sufficient (ms)
    return (double) (windows_to_java_time(kernel) + windows_to_java_time(user)) / MILLIUNITS;
  } else {
    return elapsedTime();
  }
}

jlong os::javaTimeMillis() {
  FILETIME wt;
  GetSystemTimeAsFileTime(&wt);
  return windows_to_java_time(wt);
}

void os::javaTimeSystemUTC(jlong &seconds, jlong &nanos) {
  FILETIME wt;
  GetSystemTimeAsFileTime(&wt);
  jlong ticks = windows_to_time_ticks(wt); // 10th of micros
  jlong secs = jlong(ticks / 10000000); // 10000 * 1000
  seconds = secs;
  nanos = jlong(ticks - (secs*10000000)) * 100;
}

jlong os::javaTimeNanos() {
    LARGE_INTEGER current_count;
    QueryPerformanceCounter(&current_count);
    double current = current_count.QuadPart;
    jlong time = (jlong)(current * nanos_per_count);
    return time;
}

void os::javaTimeNanos_info(jvmtiTimerInfo *info_ptr) {
  jlong freq = performance_frequency;
  if (freq < NANOSECS_PER_SEC) {
    // the performance counter is 64 bits and we will
    // be multiplying it -- so no wrap in 64 bits
    info_ptr->max_value = ALL_64_BITS;
  } else if (freq > NANOSECS_PER_SEC) {
    // use the max value the counter can reach to
    // determine the max value which could be returned
    julong max_counter = (julong)ALL_64_BITS;
    info_ptr->max_value = (jlong)(max_counter / (freq / NANOSECS_PER_SEC));
  } else {
    // the performance counter is 64 bits and we will
    // be using it directly -- so no wrap in 64 bits
    info_ptr->max_value = ALL_64_BITS;
  }

  // using a counter, so no skipping
  info_ptr->may_skip_backward = false;
  info_ptr->may_skip_forward = false;

  info_ptr->kind = JVMTI_TIMER_ELAPSED;                // elapsed not CPU time
}

char* os::local_time_string(char *buf, size_t buflen) {
  SYSTEMTIME st;
  GetLocalTime(&st);
  jio_snprintf(buf, buflen, "%d-%02d-%02d %02d:%02d:%02d",
               st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
  return buf;
}

bool os::getTimesSecs(double* process_real_time,
                      double* process_user_time,
                      double* process_system_time) {
  HANDLE h_process = GetCurrentProcess();
  FILETIME create_time, exit_time, kernel_time, user_time;
  BOOL result = GetProcessTimes(h_process,
                                &create_time,
                                &exit_time,
                                &kernel_time,
                                &user_time);
  if (result != 0) {
    FILETIME wt;
    GetSystemTimeAsFileTime(&wt);
    jlong rtc_millis = windows_to_java_time(wt);
    *process_real_time = ((double) rtc_millis) / ((double) MILLIUNITS);
    *process_user_time =
      (double) jlong_from(user_time.dwHighDateTime, user_time.dwLowDateTime) / (10 * MICROUNITS);
    *process_system_time =
      (double) jlong_from(kernel_time.dwHighDateTime, kernel_time.dwLowDateTime) / (10 * MICROUNITS);
    return true;
  } else {
    return false;
  }
}

void os::shutdown() {
  // allow PerfMemory to attempt cleanup of any persistent resources
  perfMemory_exit();

  // flush buffered output, finish log files
  ostream_abort();

  // Check for abort hook
  abort_hook_t abort_hook = Arguments::abort_hook();
  if (abort_hook != nullptr) {
    abort_hook();
  }
}


static HANDLE dumpFile = nullptr;

// Check if dump file can be created.
void os::check_dump_limit(char* buffer, size_t buffsz) {
  bool status = true;
  if (!FLAG_IS_DEFAULT(CreateCoredumpOnCrash) && !CreateCoredumpOnCrash) {
    jio_snprintf(buffer, buffsz, "CreateCoredumpOnCrash is disabled from command line");
    status = false;
  }

#ifndef ASSERT
  if (!os::win32::is_windows_server() && FLAG_IS_DEFAULT(CreateCoredumpOnCrash)) {
    jio_snprintf(buffer, buffsz, "Minidumps are not enabled by default on client versions of Windows");
    status = false;
  }
#endif

  if (status) {
    const char* cwd = get_current_directory(nullptr, 0);
    int pid = current_process_id();
    if (cwd != nullptr) {
      jio_snprintf(buffer, buffsz, "%s\\hs_err_pid%u.mdmp", cwd, pid);
    } else {
      jio_snprintf(buffer, buffsz, ".\\hs_err_pid%u.mdmp", pid);
    }

    if (dumpFile == nullptr &&
       (dumpFile = CreateFile(buffer, GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr))
                 == INVALID_HANDLE_VALUE) {
      jio_snprintf(buffer, buffsz, "Failed to create minidump file (0x%x).", GetLastError());
      status = false;
    }
  }
  VMError::record_coredump_status(buffer, status);
}

void os::abort(bool dump_core, void* siginfo, const void* context) {
  EXCEPTION_POINTERS ep;
  MINIDUMP_EXCEPTION_INFORMATION mei;
  MINIDUMP_EXCEPTION_INFORMATION* pmei;

  HANDLE hProcess = GetCurrentProcess();
  DWORD processId = GetCurrentProcessId();
  MINIDUMP_TYPE dumpType;

  shutdown();
  if (!dump_core || dumpFile == nullptr) {
    if (dumpFile != nullptr) {
      CloseHandle(dumpFile);
    }
    exit_process_or_thread(EPT_PROCESS, 1);
  }

  dumpType = (MINIDUMP_TYPE)(MiniDumpWithFullMemory | MiniDumpWithHandleData |
    MiniDumpWithFullMemoryInfo | MiniDumpWithThreadInfo | MiniDumpWithUnloadedModules);

  if (siginfo != nullptr && context != nullptr) {
    ep.ContextRecord = (PCONTEXT) context;
    ep.ExceptionRecord = (PEXCEPTION_RECORD) siginfo;

    mei.ThreadId = GetCurrentThreadId();
    mei.ExceptionPointers = &ep;
    pmei = &mei;
  } else {
    pmei = nullptr;
  }

  // Older versions of dbghelp.dll (the one shipped with Win2003 for example) may not support all
  // the dump types we really want. If first call fails, lets fall back to just use MiniDumpWithFullMemory then.
  if (!WindowsDbgHelp::miniDumpWriteDump(hProcess, processId, dumpFile, dumpType, pmei, nullptr, nullptr) &&
      !WindowsDbgHelp::miniDumpWriteDump(hProcess, processId, dumpFile, (MINIDUMP_TYPE)MiniDumpWithFullMemory, pmei, nullptr, nullptr)) {
    jio_fprintf(stderr, "Call to MiniDumpWriteDump() failed (Error 0x%x)\n", GetLastError());
  }
  CloseHandle(dumpFile);
  exit_process_or_thread(EPT_PROCESS, 1);
}

// Die immediately, no exit hook, no abort hook, no cleanup.
void os::die() {
  exit_process_or_thread(EPT_PROCESS_DIE, -1);
}

void  os::dll_unload(void *lib) {
  char name[MAX_PATH];
  if (::GetModuleFileName((HMODULE)lib, name, sizeof(name)) == 0) {
    snprintf(name, MAX_PATH, "<not available>");
  }

  JFR_ONLY(NativeLibraryUnloadEvent unload_event(name);)

  if (::FreeLibrary((HMODULE)lib)) {
    Events::log_dll_message(nullptr, "Unloaded dll \"%s\" [" INTPTR_FORMAT "]", name, p2i(lib));
    log_info(os)("Unloaded dll \"%s\" [" INTPTR_FORMAT "]", name, p2i(lib));
    JFR_ONLY(unload_event.set_result(true);)
  } else {
    const DWORD errcode = ::GetLastError();
    char buf[500];
    size_t tl = os::lasterror(buf, sizeof(buf));
    Events::log_dll_message(nullptr, "Attempt to unload dll \"%s\" [" INTPTR_FORMAT "] failed (error code %d)", name, p2i(lib), errcode);
    log_info(os)("Attempt to unload dll \"%s\" [" INTPTR_FORMAT "] failed (error code %d)", name, p2i(lib), errcode);
    if (tl == 0) {
      os::snprintf(buf, sizeof(buf), "Attempt to unload dll failed (error code %d)", (int) errcode);
    }
    JFR_ONLY(unload_event.set_error_msg(buf);)
  }
}

void* os::dll_lookup(void *lib, const char *name) {
  ::SetLastError(0); // Clear old pending errors
  void* ret = ::GetProcAddress((HMODULE)lib, name);
  if (ret == nullptr) {
    char buf[512];
    if (os::lasterror(buf, sizeof(buf)) > 0) {
      log_debug(os)("Symbol %s not found in dll: %s", name, buf);
    }
  }
  return ret;
}

// Directory routines copied from src/win32/native/java/io/dirent_md.c
//  * dirent_md.c       1.15 00/02/02
//
// The declarations for DIR and struct dirent are in jvm_win32.h.

// Caller must have already run dirname through JVM_NativePath, which removes
// duplicate slashes and converts all instances of '/' into '\\'.

DIR * os::opendir(const char *dirname) {
  assert(dirname != nullptr, "just checking");   // hotspot change
  DIR *dirp = (DIR *)malloc(sizeof(DIR), mtInternal);
  DWORD fattr;                                // hotspot change
  char alt_dirname[4] = { 0, 0, 0, 0 };

  if (dirp == 0) {
    errno = ENOMEM;
    return 0;
  }

  // Win32 accepts "\" in its POSIX stat(), but refuses to treat it
  // as a directory in FindFirstFile().  We detect this case here and
  // prepend the current drive name.
  //
  if (dirname[1] == '\0' && dirname[0] == '\\') {
    alt_dirname[0] = _getdrive() + 'A' - 1;
    alt_dirname[1] = ':';
    alt_dirname[2] = '\\';
    alt_dirname[3] = '\0';
    dirname = alt_dirname;
  }

  dirp->path = (char *)malloc(strlen(dirname) + 5, mtInternal);
  if (dirp->path == 0) {
    free(dirp);
    errno = ENOMEM;
    return 0;
  }
  strcpy(dirp->path, dirname);

  fattr = GetFileAttributes(dirp->path);
  if (fattr == 0xffffffff) {
    free(dirp->path);
    free(dirp);
    errno = ENOENT;
    return 0;
  } else if ((fattr & FILE_ATTRIBUTE_DIRECTORY) == 0) {
    free(dirp->path);
    free(dirp);
    errno = ENOTDIR;
    return 0;
  }

  // Append "*.*", or possibly "\\*.*", to path
  if (dirp->path[1] == ':' &&
      (dirp->path[2] == '\0' ||
      (dirp->path[2] == '\\' && dirp->path[3] == '\0'))) {
    // No '\\' needed for cases like "Z:" or "Z:\"
    strcat(dirp->path, "*.*");
  } else {
    strcat(dirp->path, "\\*.*");
  }

  dirp->handle = FindFirstFile(dirp->path, &dirp->find_data);
  if (dirp->handle == INVALID_HANDLE_VALUE) {
    if (GetLastError() != ERROR_FILE_NOT_FOUND) {
      free(dirp->path);
      free(dirp);
      errno = EACCES;
      return 0;
    }
  }
  return dirp;
}

struct dirent * os::readdir(DIR *dirp) {
  assert(dirp != nullptr, "just checking");      // hotspot change
  if (dirp->handle == INVALID_HANDLE_VALUE) {
    return nullptr;
  }

  strcpy(dirp->dirent.d_name, dirp->find_data.cFileName);

  if (!FindNextFile(dirp->handle, &dirp->find_data)) {
    if (GetLastError() == ERROR_INVALID_HANDLE) {
      errno = EBADF;
      return nullptr;
    }
    FindClose(dirp->handle);
    dirp->handle = INVALID_HANDLE_VALUE;
  }

  return &dirp->dirent;
}

int os::closedir(DIR *dirp) {
  assert(dirp != nullptr, "just checking");      // hotspot change
  if (dirp->handle != INVALID_HANDLE_VALUE) {
    if (!FindClose(dirp->handle)) {
      errno = EBADF;
      return -1;
    }
    dirp->handle = INVALID_HANDLE_VALUE;
  }
  free(dirp->path);
  free(dirp);
  return 0;
}

// This must be hard coded because it's the system's temporary
// directory not the java application's temp directory, ala java.io.tmpdir.
const char* os::get_temp_directory() {
  static char path_buf[MAX_PATH];
  if (GetTempPath(MAX_PATH, path_buf) > 0) {
    return path_buf;
  } else {
    path_buf[0] = '\0';
    return path_buf;
  }
}

// Needs to be in os specific directory because windows requires another
// header file <direct.h>
const char* os::get_current_directory(char *buf, size_t buflen) {
  int n = static_cast<int>(buflen);
  if (buflen > INT_MAX)  n = INT_MAX;
  return _getcwd(buf, n);
}

void os::prepare_native_symbols() {
}

//-----------------------------------------------------------
// Helper functions for fatal error handler
#ifdef _WIN64
// Helper routine which returns true if address in
// within the NTDLL address space.
//
static bool _addr_in_ntdll(address addr) {
  HMODULE hmod;
  MODULEINFO minfo;

  hmod = GetModuleHandle("NTDLL.DLL");
  if (hmod == nullptr) return false;
  if (!GetModuleInformation(GetCurrentProcess(), hmod,
                                          &minfo, sizeof(MODULEINFO))) {
    return false;
  }

  if ((addr >= minfo.lpBaseOfDll) &&
      (addr < (address)((uintptr_t)minfo.lpBaseOfDll + (uintptr_t)minfo.SizeOfImage))) {
    return true;
  } else {
    return false;
  }
}
#endif

struct _modinfo {
  address addr;
  char*   full_path;   // point to a char buffer
  int     buflen;      // size of the buffer
  address base_addr;
};

static int _locate_module_by_addr(const char * mod_fname, address base_addr,
                                  address top_address, void * param) {
  struct _modinfo *pmod = (struct _modinfo *)param;
  if (!pmod) return -1;

  if (base_addr   <= pmod->addr &&
      top_address > pmod->addr) {
    // if a buffer is provided, copy path name to the buffer
    if (pmod->full_path) {
      jio_snprintf(pmod->full_path, pmod->buflen, "%s", mod_fname);
    }
    pmod->base_addr = base_addr;
    return 1;
  }
  return 0;
}

bool os::dll_address_to_library_name(address addr, char* buf,
                                     int buflen, int* offset) {
  // buf is not optional, but offset is optional
  assert(buf != nullptr, "sanity check");

// NOTE: the reason we don't use SymGetModuleInfo() is it doesn't always
//       return the full path to the DLL file, sometimes it returns path
//       to the corresponding PDB file (debug info); sometimes it only
//       returns partial path, which makes life painful.

  struct _modinfo mi;
  mi.addr      = addr;
  mi.full_path = buf;
  mi.buflen    = buflen;
  if (get_loaded_modules_info(_locate_module_by_addr, (void *)&mi)) {
    // buf already contains path name
    if (offset) *offset = addr - mi.base_addr;
    return true;
  }

  buf[0] = '\0';
  if (offset) *offset = -1;
  return false;
}

bool os::dll_address_to_function_name(address addr, char *buf,
                                      int buflen, int *offset,
                                      bool demangle) {
  // buf is not optional, but offset is optional
  assert(buf != nullptr, "sanity check");

  if (Decoder::decode(addr, buf, buflen, offset, demangle)) {
    return true;
  }
  if (offset != nullptr)  *offset  = -1;
  buf[0] = '\0';
  return false;
}

// save the start and end address of jvm.dll into param[0] and param[1]
static int _locate_jvm_dll(const char* mod_fname, address base_addr,
                           address top_address, void * param) {
  if (!param) return -1;

  if (base_addr   <= (address)_locate_jvm_dll &&
      top_address > (address)_locate_jvm_dll) {
    ((address*)param)[0] = base_addr;
    ((address*)param)[1] = top_address;
    return 1;
  }
  return 0;
}

address vm_lib_location[2];    // start and end address of jvm.dll

// check if addr is inside jvm.dll
bool os::address_is_in_vm(address addr) {
  if (!vm_lib_location[0] || !vm_lib_location[1]) {
    if (!get_loaded_modules_info(_locate_jvm_dll, (void *)vm_lib_location)) {
      assert(false, "Can't find jvm module.");
      return false;
    }
  }

  return (vm_lib_location[0] <= addr) && (addr < vm_lib_location[1]);
}

// print module info; param is outputStream*
static int _print_module(const char* fname, address base_address,
                         address top_address, void* param) {
  if (!param) return -1;

  outputStream* st = (outputStream*)param;

  st->print(PTR_FORMAT " - " PTR_FORMAT " \t%s\n", base_address, top_address, fname);
  return 0;
}

// Loads .dll/.so and
// in case of error it checks if .dll/.so was built for the
// same architecture as Hotspot is running on
void * os::dll_load(const char *name, char *ebuf, int ebuflen) {
  log_info(os)("attempting shared library load of %s", name);
  void* result;
  JFR_ONLY(NativeLibraryLoadEvent load_event(name, &result);)
  result = LoadLibrary(name);
  if (result != nullptr) {
    Events::log_dll_message(nullptr, "Loaded shared library %s", name);
    // Recalculate pdb search path if a DLL was loaded successfully.
    SymbolEngine::recalc_search_path();
    log_info(os)("shared library load of %s was successful", name);
    return result;
  }
  DWORD errcode = GetLastError();
  // Read system error message into ebuf
  // It may or may not be overwritten below (in the for loop and just above)
  lasterror(ebuf, (size_t) ebuflen);
  ebuf[ebuflen - 1] = '\0';
  Events::log_dll_message(nullptr, "Loading shared library %s failed, error code %lu", name, errcode);
  log_info(os)("shared library load of %s failed, error code %lu", name, errcode);

  if (errcode == ERROR_MOD_NOT_FOUND) {
    strncpy(ebuf, "Can't find dependent libraries", ebuflen - 1);
    ebuf[ebuflen - 1] = '\0';
    JFR_ONLY(load_event.set_error_msg(ebuf);)
    return nullptr;
  }

  // Parsing dll below
  // If we can read dll-info and find that dll was built
  // for an architecture other than Hotspot is running in
  // - then print to buffer "DLL was built for a different architecture"
  // else call os::lasterror to obtain system error message
  int fd = ::open(name, O_RDONLY | O_BINARY, 0);
  if (fd < 0) {
    JFR_ONLY(load_event.set_error_msg("open on dll file did not work");)
    return nullptr;
  }

  uint32_t signature_offset;
  uint16_t lib_arch = 0;
  bool failed_to_get_lib_arch =
    ( // Go to position 3c in the dll
     (os::seek_to_file_offset(fd, IMAGE_FILE_PTR_TO_SIGNATURE) < 0)
     ||
     // Read location of signature
     (sizeof(signature_offset) !=
     (::read(fd, (void*)&signature_offset, sizeof(signature_offset))))
     ||
     // Go to COFF File Header in dll
     // that is located after "signature" (4 bytes long)
     (os::seek_to_file_offset(fd,
     signature_offset + IMAGE_FILE_SIGNATURE_LENGTH) < 0)
     ||
     // Read field that contains code of architecture
     // that dll was built for
     (sizeof(lib_arch) != (::read(fd, (void*)&lib_arch, sizeof(lib_arch))))
    );

  ::close(fd);
  if (failed_to_get_lib_arch) {
    // file i/o error - report os::lasterror(...) msg
    JFR_ONLY(load_event.set_error_msg("failed to get lib architecture");)
    return nullptr;
  }

  typedef struct {
    uint16_t arch_code;
    char* arch_name;
  } arch_t;

  static const arch_t arch_array[] = {
    {IMAGE_FILE_MACHINE_I386,      (char*)"IA 32"},
    {IMAGE_FILE_MACHINE_AMD64,     (char*)"AMD 64"},
    {IMAGE_FILE_MACHINE_ARM64,     (char*)"ARM 64"}
  };
#if (defined _M_ARM64)
  static const uint16_t running_arch = IMAGE_FILE_MACHINE_ARM64;
#elif (defined _M_AMD64)
  static const uint16_t running_arch = IMAGE_FILE_MACHINE_AMD64;
#elif (defined _M_IX86)
  static const uint16_t running_arch = IMAGE_FILE_MACHINE_I386;
#else
  #error Method os::dll_load requires that one of following \
         is defined :_M_AMD64 or _M_IX86 or _M_ARM64
#endif


  // Obtain a string for printf operation
  // lib_arch_str shall contain string what platform this .dll was built for
  // running_arch_str shall string contain what platform Hotspot was built for
  char *running_arch_str = nullptr, *lib_arch_str = nullptr;
  for (unsigned int i = 0; i < ARRAY_SIZE(arch_array); i++) {
    if (lib_arch == arch_array[i].arch_code) {
      lib_arch_str = arch_array[i].arch_name;
    }
    if (running_arch == arch_array[i].arch_code) {
      running_arch_str = arch_array[i].arch_name;
    }
  }

  assert(running_arch_str,
         "Didn't find running architecture code in arch_array");

  // If the architecture is right
  // but some other error took place - report os::lasterror(...) msg
  if (lib_arch == running_arch) {
    JFR_ONLY(load_event.set_error_msg("lib architecture matches, but other error occured");)
    return nullptr;
  }

  if (lib_arch_str != nullptr) {
    ::_snprintf(ebuf, ebuflen - 1,
                "Can't load %s-bit .dll on a %s-bit platform",
                lib_arch_str, running_arch_str);
  } else {
    // don't know what architecture this dll was build for
    ::_snprintf(ebuf, ebuflen - 1,
                "Can't load this .dll (machine code=0x%x) on a %s-bit platform",
                lib_arch, running_arch_str);
  }
  JFR_ONLY(load_event.set_error_msg(ebuf);)
  return nullptr;
}

void os::print_dll_info(outputStream *st) {
  st->print_cr("Dynamic libraries:");
  get_loaded_modules_info(_print_module, (void *)st);
}

int os::get_loaded_modules_info(os::LoadedModulesCallbackFunc callback, void *param) {
  HANDLE   hProcess;

# define MAX_NUM_MODULES 128
  HMODULE     modules[MAX_NUM_MODULES];
  static char filename[MAX_PATH];
  int         result = 0;

  int pid = os::current_process_id();
  hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ,
                         FALSE, pid);
  if (hProcess == nullptr) return 0;

  DWORD size_needed;
  if (!EnumProcessModules(hProcess, modules, sizeof(modules), &size_needed)) {
    CloseHandle(hProcess);
    return 0;
  }

  // number of modules that are currently loaded
  int num_modules = size_needed / sizeof(HMODULE);

  for (int i = 0; i < MIN2(num_modules, MAX_NUM_MODULES); i++) {
    // Get Full pathname:
    if (!GetModuleFileNameEx(hProcess, modules[i], filename, sizeof(filename))) {
      filename[0] = '\0';
    }

    MODULEINFO modinfo;
    if (!GetModuleInformation(hProcess, modules[i], &modinfo, sizeof(modinfo))) {
      modinfo.lpBaseOfDll = nullptr;
      modinfo.SizeOfImage = 0;
    }

    // Invoke callback function
    result = callback(filename, (address)modinfo.lpBaseOfDll,
                      (address)((u8)modinfo.lpBaseOfDll + (u8)modinfo.SizeOfImage), param);
    if (result) break;
  }

  CloseHandle(hProcess);
  return result;
}

bool os::get_host_name(char* buf, size_t buflen) {
  DWORD size = (DWORD)buflen;
  return (GetComputerNameEx(ComputerNameDnsHostname, buf, &size) == TRUE);
}

void os::get_summary_os_info(char* buf, size_t buflen) {
  stringStream sst(buf, buflen);
  os::win32::print_windows_version(&sst);
  // chop off newline character
  char* nl = strchr(buf, '\n');
  if (nl != nullptr) *nl = '\0';
}

static inline time_t get_mtime(const char* filename) {
  struct stat st;
  int ret = os::stat(filename, &st);
  assert(ret == 0, "failed to stat() file '%s': %s", filename, os::strerror(errno));
  return st.st_mtime;
}

int os::compare_file_modified_times(const char* file1, const char* file2) {
  time_t t1 = get_mtime(file1);
  time_t t2 = get_mtime(file2);
  return primitive_compare(t1, t2);
}

void os::print_os_info_brief(outputStream* st) {
  os::print_os_info(st);
}

void os::win32::print_uptime_info(outputStream* st) {
  unsigned long long ticks = GetTickCount64();
  os::print_dhm(st, "OS uptime:", ticks/1000);
}

void os::print_os_info(outputStream* st) {
#ifdef ASSERT
  char buffer[1024];
  st->print("HostName: ");
  if (get_host_name(buffer, sizeof(buffer))) {
    st->print_cr(buffer);
  } else {
    st->print_cr("N/A");
  }
#endif
  st->print_cr("OS:");
  os::win32::print_windows_version(st);

  os::win32::print_uptime_info(st);

  VM_Version::print_platform_virtualization_info(st);
}

void os::win32::print_windows_version(outputStream* st) {
  bool is_workstation = !IsWindowsServer();

  int major_version = windows_major_version();
  int minor_version = windows_minor_version();
  int build_number = windows_build_number();
  int build_minor = windows_build_minor();
  int os_vers = major_version * 1000 + minor_version;

  st->print(" Windows ");
  switch (os_vers) {

  case 6000:
    if (is_workstation) {
      st->print("Vista");
    } else {
      st->print("Server 2008");
    }
    break;

  case 6001:
    if (is_workstation) {
      st->print("7");
    } else {
      st->print("Server 2008 R2");
    }
    break;

  case 6002:
    if (is_workstation) {
      st->print("8");
    } else {
      st->print("Server 2012");
    }
    break;

  case 6003:
    if (is_workstation) {
      st->print("8.1");
    } else {
      st->print("Server 2012 R2");
    }
    break;

  case 10000:
    if (is_workstation) {
      if (build_number >= 22000) {
        st->print("11");
      } else {
        st->print("10");
      }
    } else {
      // distinguish Windows Server by build number
      // - 2016 GA 10/2016 build: 14393
      // - 2019 GA 11/2018 build: 17763
      // - 2022 GA 08/2021 build: 20348
      if (build_number > 20347) {
        st->print("Server 2022");
      } else if (build_number > 17762) {
        st->print("Server 2019");
      } else {
        st->print("Server 2016");
      }
    }
    break;

  default:
    // Unrecognized windows, print out its major and minor versions
    st->print("%d.%d", major_version, minor_version);
    break;
  }

  // Retrieve SYSTEM_INFO from GetNativeSystemInfo call so that we could
  // find out whether we are running on 64 bit processor or not
  SYSTEM_INFO si;
  ZeroMemory(&si, sizeof(SYSTEM_INFO));
  GetNativeSystemInfo(&si);
  if ((si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64) ||
      (si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_ARM64)) {
    st->print(" , 64 bit");
  }

  st->print(" Build %d", build_number);
  st->print(" (%d.%d.%d.%d)", major_version, minor_version, build_number, build_minor);
  st->cr();
}

// Processor Power Information; missing from Windows headers
typedef struct _PROCESSOR_POWER_INFORMATION {
    ULONG Number;
    ULONG MaxMhz;     // max specified clock frequency of the system processor
    ULONG CurrentMhz; // max specified processor clock frequency mult. by current processor throttle
    ULONG MhzLimit;   // max specified processor clock frequency mult. by current processor thermal throttle limit
    ULONG MaxIdleState;
    ULONG CurrentIdleState;
} PROCESSOR_POWER_INFORMATION;

void os::pd_print_cpu_info(outputStream* st, char* buf, size_t buflen) {
  int proc_count = os::processor_count();
  // handle potential early cases where processor count is not yet set
  if (proc_count < 1) {
    SYSTEM_INFO si;
    GetSystemInfo(&si);

    // This is the number of logical processors in the current processor group only and is therefore
    // at most 64. The GetLogicalProcessorInformation function is used to compute the total number
    // of processors. However, it requires memory to be allocated for the processor information buffer.
    // Since this method is used in paths where memory allocation should not be done (i.e. after a crash),
    // only the number of processors in the current group will be returned.
    proc_count = si.dwNumberOfProcessors;
  }

  size_t sz_check = sizeof(PROCESSOR_POWER_INFORMATION) * (size_t)proc_count;
  NTSTATUS status = ::CallNtPowerInformation(ProcessorInformation, nullptr, 0, buf, (ULONG) buflen);
  int max_mhz = -1, current_mhz = -1, mhz_limit = -1;
  bool same_vals_for_all_cpus = true;

  if (status == ERROR_SUCCESS) {
    PROCESSOR_POWER_INFORMATION* pppi = (PROCESSOR_POWER_INFORMATION*) buf;
    for (int i = 0; i < proc_count; i++) {
      if (i == 0) {
        max_mhz = (int) pppi->MaxMhz;
        current_mhz = (int) pppi->CurrentMhz;
        mhz_limit = (int) pppi->MhzLimit;
      } else {
        if (max_mhz != (int) pppi->MaxMhz ||
            current_mhz != (int) pppi->CurrentMhz ||
            mhz_limit != (int) pppi->MhzLimit) {
          same_vals_for_all_cpus = false;
          break;
        }
      }
      // avoid iteration in case buf is too small to hold all proc infos
      if (sz_check > buflen) break;
      pppi++;
    }

    if (same_vals_for_all_cpus && max_mhz != -1) {
      st->print_cr("Processor Information for the first %d processors :", proc_count);
      st->print_cr("  Max Mhz: %d, Current Mhz: %d, Mhz Limit: %d", max_mhz, current_mhz, mhz_limit);
      return;
    }
    // differing values, iterate again
    pppi = (PROCESSOR_POWER_INFORMATION*) buf;
    for (int i = 0; i < proc_count; i++) {
      st->print_cr("Processor Information for processor %d", (int) pppi->Number);
      st->print_cr("  Max Mhz: %d, Current Mhz: %d, Mhz Limit: %d",
                     (int) pppi->MaxMhz, (int) pppi->CurrentMhz, (int) pppi->MhzLimit);
      if (sz_check > buflen) break;
      pppi++;
    }
  }
}

void os::get_summary_cpu_info(char* buf, size_t buflen) {
  HKEY key;
  DWORD status = RegOpenKey(HKEY_LOCAL_MACHINE,
               "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", &key);
  if (status == ERROR_SUCCESS) {
    DWORD size = (DWORD)buflen;
    status = RegQueryValueEx(key, "ProcessorNameString", nullptr, nullptr, (byte*)buf, &size);
    if (status != ERROR_SUCCESS) {
        strncpy(buf, "## __CPU__", buflen);
    } else {
      if (size < buflen) {
        buf[size] = '\0';
      }
    }
    RegCloseKey(key);
  } else {
    // Put generic cpu info to return
    strncpy(buf, "## __CPU__", buflen);
  }
}

void os::print_memory_info(outputStream* st) {
  st->print("Memory:");
  st->print(" " SIZE_FORMAT "k page", os::vm_page_size()>>10);

  // Use GlobalMemoryStatusEx() because GlobalMemoryStatus() may return incorrect
  // value if total memory is larger than 4GB
  MEMORYSTATUSEX ms;
  ms.dwLength = sizeof(ms);
  int r1 = GlobalMemoryStatusEx(&ms);

  if (r1 != 0) {
    st->print(", system-wide physical " INT64_FORMAT "M ",
             (int64_t) ms.ullTotalPhys >> 20);
    st->print("(" INT64_FORMAT "M free)\n", (int64_t) ms.ullAvailPhys >> 20);

    st->print("TotalPageFile size " INT64_FORMAT "M ",
             (int64_t) ms.ullTotalPageFile >> 20);
    st->print("(AvailPageFile size " INT64_FORMAT "M)",
             (int64_t) ms.ullAvailPageFile >> 20);

    // on 32bit Total/AvailVirtual are interesting (show us how close we get to 2-4 GB per process borders)
#if defined(_M_IX86)
    st->print(", user-mode portion of virtual address-space " INT64_FORMAT "M ",
             (int64_t) ms.ullTotalVirtual >> 20);
    st->print("(" INT64_FORMAT "M free)", (int64_t) ms.ullAvailVirtual >> 20);
#endif
  } else {
    st->print(", GlobalMemoryStatusEx did not succeed so we miss some memory values.");
  }

  // extended memory statistics for a process
  PROCESS_MEMORY_COUNTERS_EX pmex;
  ZeroMemory(&pmex, sizeof(PROCESS_MEMORY_COUNTERS_EX));
  pmex.cb = sizeof(pmex);
  int r2 = GetProcessMemoryInfo(GetCurrentProcess(), (PROCESS_MEMORY_COUNTERS*) &pmex, sizeof(pmex));

  if (r2 != 0) {
    st->print("\ncurrent process WorkingSet (physical memory assigned to process): " INT64_FORMAT "M, ",
             (int64_t) pmex.WorkingSetSize >> 20);
    st->print("peak: " INT64_FORMAT "M\n", (int64_t) pmex.PeakWorkingSetSize >> 20);

    st->print("current process commit charge (\"private bytes\"): " INT64_FORMAT "M, ",
             (int64_t) pmex.PrivateUsage >> 20);
    st->print("peak: " INT64_FORMAT "M", (int64_t) pmex.PeakPagefileUsage >> 20);
  } else {
    st->print("\nGetProcessMemoryInfo did not succeed so we miss some memory values.");
  }

  st->cr();
}

bool os::signal_sent_by_kill(const void* siginfo) {
  // TODO: Is this possible?
  return false;
}

void os::print_siginfo(outputStream *st, const void* siginfo) {
  const EXCEPTION_RECORD* const er = (EXCEPTION_RECORD*)siginfo;
  st->print("siginfo:");

  char tmp[64];
  if (os::exception_name(er->ExceptionCode, tmp, sizeof(tmp)) == nullptr) {
    strcpy(tmp, "EXCEPTION_??");
  }
  st->print(" %s (0x%x)", tmp, er->ExceptionCode);

  if ((er->ExceptionCode == EXCEPTION_ACCESS_VIOLATION ||
       er->ExceptionCode == EXCEPTION_IN_PAGE_ERROR) &&
       er->NumberParameters >= 2) {
    switch (er->ExceptionInformation[0]) {
    case 0: st->print(", reading address"); break;
    case 1: st->print(", writing address"); break;
    case 8: st->print(", data execution prevention violation at address"); break;
    default: st->print(", ExceptionInformation=" INTPTR_FORMAT,
                       er->ExceptionInformation[0]);
    }
    st->print(" " INTPTR_FORMAT, er->ExceptionInformation[1]);
  } else {
    int num = er->NumberParameters;
    if (num > 0) {
      st->print(", ExceptionInformation=");
      for (int i = 0; i < num; i++) {
        st->print(INTPTR_FORMAT " ", er->ExceptionInformation[i]);
      }
    }
  }
  st->cr();
}

bool os::signal_thread(Thread* thread, int sig, const char* reason) {
  // TODO: Can we kill thread?
  return false;
}

void os::print_signal_handlers(outputStream* st, char* buf, size_t buflen) {
  // do nothing
}

static char saved_jvm_path[MAX_PATH] = {0};

// Find the full path to the current module, jvm.dll
void os::jvm_path(char *buf, jint buflen) {
  // Error checking.
  if (buflen < MAX_PATH) {
    assert(false, "must use a large-enough buffer");
    buf[0] = '\0';
    return;
  }
  // Lazy resolve the path to current module.
  if (saved_jvm_path[0] != 0) {
    strcpy(buf, saved_jvm_path);
    return;
  }

  buf[0] = '\0';
  if (Arguments::sun_java_launcher_is_altjvm()) {
    // Support for the java launcher's '-XXaltjvm=<path>' option. Check
    // for a JAVA_HOME environment variable and fix up the path so it
    // looks like jvm.dll is installed there (append a fake suffix
    // hotspot/jvm.dll).
    char* java_home_var = ::getenv("JAVA_HOME");
    if (java_home_var != nullptr && java_home_var[0] != 0 &&
        strlen(java_home_var) < (size_t)buflen) {
      strncpy(buf, java_home_var, buflen);

      // determine if this is a legacy image or modules image
      // modules image doesn't have "jre" subdirectory
      size_t len = strlen(buf);
      char* jrebin_p = buf + len;
      jio_snprintf(jrebin_p, buflen-len, "\\jre\\bin\\");
      if (0 != _access(buf, 0)) {
        jio_snprintf(jrebin_p, buflen-len, "\\bin\\");
      }
      len = strlen(buf);
      jio_snprintf(buf + len, buflen-len, "hotspot\\jvm.dll");
    }
  }

  if (buf[0] == '\0') {
    GetModuleFileName(vm_lib_handle, buf, buflen);
  }
  strncpy(saved_jvm_path, buf, MAX_PATH);
  saved_jvm_path[MAX_PATH - 1] = '\0';
}


void os::print_jni_name_prefix_on(outputStream* st, int args_size) {
#ifndef _WIN64
  st->print("_");
#endif
}


void os::print_jni_name_suffix_on(outputStream* st, int args_size) {
#ifndef _WIN64
  st->print("@%d", args_size  * sizeof(int));
#endif
}

// This method is a copy of JDK's sysGetLastErrorString
// from src/windows/hpi/src/system_md.c

size_t os::lasterror(char* buf, size_t len) {
  DWORD errval;

  if ((errval = GetLastError()) != 0) {
    // DOS error
    size_t n = (size_t)FormatMessage(
                                     FORMAT_MESSAGE_FROM_SYSTEM|FORMAT_MESSAGE_IGNORE_INSERTS,
                                     nullptr,
                                     errval,
                                     0,
                                     buf,
                                     (DWORD)len,
                                     nullptr);
    if (n > 3) {
      // Drop final '.', CR, LF
      if (buf[n - 1] == '\n') n--;
      if (buf[n - 1] == '\r') n--;
      if (buf[n - 1] == '.') n--;
      buf[n] = '\0';
    }
    return n;
  }

  if (errno != 0) {
    // C runtime error that has no corresponding DOS error code
    const char* s = os::strerror(errno);
    size_t n = strlen(s);
    if (n >= len) n = len - 1;
    strncpy(buf, s, n);
    buf[n] = '\0';
    return n;
  }

  return 0;
}

int os::get_last_error() {
  DWORD error = GetLastError();
  if (error == 0) {
    error = errno;
  }
  return (int)error;
}

// sun.misc.Signal
// NOTE that this is a workaround for an apparent kernel bug where if
// a signal handler for SIGBREAK is installed then that signal handler
// takes priority over the console control handler for CTRL_CLOSE_EVENT.
// See bug 4416763.
static signal_handler_t sigbreakHandler = nullptr;

static void UserHandler(int sig) {
  os::signal_notify(sig);
  // We need to reinstate the signal handler each time...
  os::win32::install_signal_handler(sig, UserHandler);
}

void* os::win32::user_handler() {
  return CAST_FROM_FN_PTR(void*, UserHandler);
}

// Used mainly by JVM_RegisterSignal to install a signal handler,
// but also to install the VM's BREAK_HANDLER. However, due to
// the way Windows signals work we also have to reinstall each
// handler at the end of its own execution.
// The allowed set of signals is restricted by the caller.
// The incoming handler is one of:
// - psuedo-handler: SIG_IGN or SIG_DFL
// - VM defined signal handling function of type signal_handler_t
// - unknown signal handling function which we expect* is also
//   of type signal_handler_t
//
// * win32 defines a two-arg signal handling function for use solely with
//   SIGFPE. As we don't allow that to be set via the Java API we know we
//   only have the single arg version.
// Returns the currently installed handler.
void* os::win32::install_signal_handler(int sig, signal_handler_t handler) {
  if ((sig == SIGBREAK) && (!ReduceSignalUsage)) {
    void* oldHandler = CAST_FROM_FN_PTR(void*, sigbreakHandler);
    sigbreakHandler = handler;
    return oldHandler;
  } else {
    return CAST_FROM_FN_PTR(void*, ::signal(sig, handler));
  }
}

// The Win32 C runtime library maps all console control events other than ^C
// into SIGBREAK, which makes it impossible to distinguish ^BREAK from close,
// logoff, and shutdown events.  We therefore install our own console handler
// that raises SIGTERM for the latter cases.
//
static BOOL WINAPI consoleHandler(DWORD event) {
  switch (event) {
  case CTRL_C_EVENT:
    if (VMError::is_error_reported()) {
      // Ctrl-C is pressed during error reporting, likely because the error
      // handler fails to abort. Let VM die immediately.
      os::die();
    }

    ::raise(SIGINT);
    return TRUE;
    break;
  case CTRL_BREAK_EVENT:
    if (sigbreakHandler != nullptr) {
      (*sigbreakHandler)(SIGBREAK);
    }
    return TRUE;
    break;
  case CTRL_LOGOFF_EVENT: {
    // Don't terminate JVM if it is running in a non-interactive session,
    // such as a service process.
    USEROBJECTFLAGS flags;
    HANDLE handle = GetProcessWindowStation();
    if (handle != nullptr &&
        GetUserObjectInformation(handle, UOI_FLAGS, &flags,
        sizeof(USEROBJECTFLAGS), nullptr)) {
      // If it is a non-interactive session, let next handler to deal
      // with it.
      if ((flags.dwFlags & WSF_VISIBLE) == 0) {
        return FALSE;
      }
    }
  }
  case CTRL_CLOSE_EVENT:
  case CTRL_SHUTDOWN_EVENT:
    ::raise(SIGTERM);
    return TRUE;
    break;
  default:
    break;
  }
  return FALSE;
}

// The following code is moved from os.cpp for making this
// code platform specific, which it is by its very nature.

// Return maximum OS signal used + 1 for internal use only
// Used as exit signal for signal_thread
int os::sigexitnum_pd() {
  return NSIG;
}

// a counter for each possible signal value, including signal_thread exit signal
static volatile jint pending_signals[NSIG+1] = { 0 };
static Semaphore* sig_sem = nullptr;

static void jdk_misc_signal_init() {
  // Initialize signal structures
  memset((void*)pending_signals, 0, sizeof(pending_signals));

  // Initialize signal semaphore
  sig_sem = new Semaphore();

  // Programs embedding the VM do not want it to attempt to receive
  // events like CTRL_LOGOFF_EVENT, which are used to implement the
  // shutdown hooks mechanism introduced in 1.3.  For example, when
  // the VM is run as part of a Windows NT service (i.e., a servlet
  // engine in a web server), the correct behavior is for any console
  // control handler to return FALSE, not TRUE, because the OS's
  // "final" handler for such events allows the process to continue if
  // it is a service (while terminating it if it is not a service).
  // To make this behavior uniform and the mechanism simpler, we
  // completely disable the VM's usage of these console events if -Xrs
  // (=ReduceSignalUsage) is specified.  This means, for example, that
  // the CTRL-BREAK thread dump mechanism is also disabled in this
  // case.  See bugs 4323062, 4345157, and related bugs.

  // Add a CTRL-C handler
  SetConsoleCtrlHandler(consoleHandler, TRUE);

  // Initialize sigbreakHandler.
  // The actual work for handling CTRL-BREAK is performed by the Signal
  // Dispatcher thread, which is created and started at a much later point,
  // see os::initialize_jdk_signal_support(). Any CTRL-BREAK received
  // before the Signal Dispatcher thread is started is queued up via the
  // pending_signals[SIGBREAK] counter, and will be processed by the
  // Signal Dispatcher thread in a delayed fashion.
  os::win32::install_signal_handler(SIGBREAK, UserHandler);
}

void os::signal_notify(int sig) {
  if (sig_sem != nullptr) {
    Atomic::inc(&pending_signals[sig]);
    sig_sem->signal();
  } else {
    // Signal thread is not created with ReduceSignalUsage and jdk_misc_signal_init
    // initialization isn't called.
    assert(ReduceSignalUsage, "signal semaphore should be created");
  }
}

static int check_pending_signals() {
  while (true) {
    for (int i = 0; i < NSIG + 1; i++) {
      jint n = pending_signals[i];
      if (n > 0 && n == Atomic::cmpxchg(&pending_signals[i], n, n - 1)) {
        return i;
      }
    }
    sig_sem->wait_with_safepoint_check(JavaThread::current());
  }
  ShouldNotReachHere();
  return 0; // Satisfy compiler
}

int os::signal_wait() {
  return check_pending_signals();
}

// Implicit OS exception handling

LONG Handle_Exception(struct _EXCEPTION_POINTERS* exceptionInfo,
                      address handler) {
  Thread* thread = Thread::current_or_null();

#if defined(_M_ARM64)
  #define PC_NAME Pc
#elif defined(_M_AMD64)
  #define PC_NAME Rip
#elif defined(_M_IX86)
  #define PC_NAME Eip
#else
  #error unknown architecture
#endif

  // Save pc in thread
  if (thread != nullptr && thread->is_Java_thread()) {
    JavaThread::cast(thread)->set_saved_exception_pc((address)(DWORD_PTR)exceptionInfo->ContextRecord->PC_NAME);
  }

  // Set pc to handler
  exceptionInfo->ContextRecord->PC_NAME = (DWORD64)handler;

  // Continue the execution
  return EXCEPTION_CONTINUE_EXECUTION;
}


// Used for PostMortemDump
extern "C" void safepoints();
extern "C" void find(int x);
extern "C" void events();

// According to Windows API documentation, an illegal instruction sequence should generate
// the 0xC000001C exception code. However, real world experience shows that occasionnaly
// the execution of an illegal instruction can generate the exception code 0xC000001E. This
// seems to be an undocumented feature of Win NT 4.0 (and probably other Windows systems).

#define EXCEPTION_ILLEGAL_INSTRUCTION_2 0xC000001E

// From "Execution Protection in the Windows Operating System" draft 0.35
// Once a system header becomes available, the "real" define should be
// included or copied here.
#define EXCEPTION_INFO_EXEC_VIOLATION 0x08

// Windows Vista/2008 heap corruption check
#define EXCEPTION_HEAP_CORRUPTION        0xC0000374

// All Visual C++ exceptions thrown from code generated by the Microsoft Visual
// C++ compiler contain this error code. Because this is a compiler-generated
// error, the code is not listed in the Win32 API header files.
// The code is actually a cryptic mnemonic device, with the initial "E"
// standing for "exception" and the final 3 bytes (0x6D7363) representing the
// ASCII values of "msc".

#define EXCEPTION_UNCAUGHT_CXX_EXCEPTION    0xE06D7363

#define def_excpt(val) { #val, (val) }

static const struct { const char* name; uint number; } exceptlabels[] = {
    def_excpt(EXCEPTION_ACCESS_VIOLATION),
    def_excpt(EXCEPTION_DATATYPE_MISALIGNMENT),
    def_excpt(EXCEPTION_BREAKPOINT),
    def_excpt(EXCEPTION_SINGLE_STEP),
    def_excpt(EXCEPTION_ARRAY_BOUNDS_EXCEEDED),
    def_excpt(EXCEPTION_FLT_DENORMAL_OPERAND),
    def_excpt(EXCEPTION_FLT_DIVIDE_BY_ZERO),
    def_excpt(EXCEPTION_FLT_INEXACT_RESULT),
    def_excpt(EXCEPTION_FLT_INVALID_OPERATION),
    def_excpt(EXCEPTION_FLT_OVERFLOW),
    def_excpt(EXCEPTION_FLT_STACK_CHECK),
    def_excpt(EXCEPTION_FLT_UNDERFLOW),
    def_excpt(EXCEPTION_INT_DIVIDE_BY_ZERO),
    def_excpt(EXCEPTION_INT_OVERFLOW),
    def_excpt(EXCEPTION_PRIV_INSTRUCTION),
    def_excpt(EXCEPTION_IN_PAGE_ERROR),
    def_excpt(EXCEPTION_ILLEGAL_INSTRUCTION),
    def_excpt(EXCEPTION_ILLEGAL_INSTRUCTION_2),
    def_excpt(EXCEPTION_NONCONTINUABLE_EXCEPTION),
    def_excpt(EXCEPTION_STACK_OVERFLOW),
    def_excpt(EXCEPTION_INVALID_DISPOSITION),
    def_excpt(EXCEPTION_GUARD_PAGE),
    def_excpt(EXCEPTION_INVALID_HANDLE),
    def_excpt(EXCEPTION_UNCAUGHT_CXX_EXCEPTION),
    def_excpt(EXCEPTION_HEAP_CORRUPTION)
};

#undef def_excpt

const char* os::exception_name(int exception_code, char *buf, size_t size) {
  uint code = static_cast<uint>(exception_code);
  for (uint i = 0; i < ARRAY_SIZE(exceptlabels); ++i) {
    if (exceptlabels[i].number == code) {
      jio_snprintf(buf, size, "%s", exceptlabels[i].name);
      return buf;
    }
  }

  return nullptr;
}

//-----------------------------------------------------------------------------
LONG Handle_IDiv_Exception(struct _EXCEPTION_POINTERS* exceptionInfo) {
  // handle exception caused by idiv; should only happen for -MinInt/-1
  // (division by zero is handled explicitly)
#if defined(_M_ARM64)
  PCONTEXT ctx = exceptionInfo->ContextRecord;
  address pc = (address)ctx->Sp;
  guarantee(pc[0] == 0x83, "not an sdiv opcode(0x83), the actual value = 0x%x", pc[0]); //Fixme did i get the right opcode?
  guarantee(ctx->X4 == min_jint, "unexpected idiv exception, the actual value = %d while the expected is %d", ctx->X4, min_jint);
  // set correct result values and continue after idiv instruction
  ctx->Pc = (uint64_t)pc + 4;        // idiv reg, reg, reg  is 4 bytes
  ctx->X4 = (uint64_t)min_jint;      // result
  ctx->X5 = (uint64_t)0;             // remainder
  // Continue the execution
#elif defined(_M_AMD64)
  PCONTEXT ctx = exceptionInfo->ContextRecord;
  address pc = (address)ctx->Rip;
  guarantee((pc[0] >= Assembler::REX && pc[0] <= Assembler::REX_WRXB && pc[1] == 0xF7) || pc[0] == 0xF7,
            "not an idiv opcode, pc[0] = 0x%x and pc[1] = 0x%x", pc[0], pc[1]);
  guarantee((pc[0] >= Assembler::REX && pc[0] <= Assembler::REX_WRXB && (pc[2] & ~0x7) == 0xF8) || (pc[1] & ~0x7) == 0xF8,
            "cannot handle non-register operands, pc[0] = 0x%x, pc[1] = 0x%x and pc[2] = 0x%x", pc[0], pc[1], pc[2]);
  if (pc[0] == 0xF7) {
    // set correct result values and continue after idiv instruction
    ctx->Rip = (DWORD64)pc + 2;        // idiv reg, reg  is 2 bytes
  } else {
    ctx->Rip = (DWORD64)pc + 3;        // REX idiv reg, reg  is 3 bytes
  }
  // Do not set ctx->Rax as it already contains the correct value (either 32 or 64 bit, depending on the operation)
  // this is the case because the exception only happens for -MinValue/-1 and -MinValue is always in rax because of the
  // idiv opcode (0xF7).
  ctx->Rdx = (DWORD)0;             // remainder
  // Continue the execution
#else
  PCONTEXT ctx = exceptionInfo->ContextRecord;
  address pc = (address)ctx->Eip;
  guarantee(pc[0] == 0xF7, "not an idiv opcode(0xF7), the actual value = 0x%x", pc[1]);
  guarantee((pc[1] & ~0x7) == 0xF8, "cannot handle non-register operands, the actual value = 0x%x", pc[1]);
  guarantee(ctx->Eax == min_jint, "unexpected idiv exception, the actual value = %d while the expected is %d", ctx->Eax, min_jint);
  // set correct result values and continue after idiv instruction
  ctx->Eip = (DWORD)pc + 2;        // idiv reg, reg  is 2 bytes
  ctx->Eax = (DWORD)min_jint;      // result
  ctx->Edx = (DWORD)0;             // remainder
  // Continue the execution
#endif
  return EXCEPTION_CONTINUE_EXECUTION;
}

#if defined(_M_AMD64) || defined(_M_IX86)
//-----------------------------------------------------------------------------
static bool handle_FLT_exception(struct _EXCEPTION_POINTERS* exceptionInfo) {
  // handle exception caused by native method modifying control word
  DWORD exception_code = exceptionInfo->ExceptionRecord->ExceptionCode;

  switch (exception_code) {
  case EXCEPTION_FLT_DENORMAL_OPERAND:
  case EXCEPTION_FLT_DIVIDE_BY_ZERO:
  case EXCEPTION_FLT_INEXACT_RESULT:
  case EXCEPTION_FLT_INVALID_OPERATION:
  case EXCEPTION_FLT_OVERFLOW:
  case EXCEPTION_FLT_STACK_CHECK:
  case EXCEPTION_FLT_UNDERFLOW: {
    PCONTEXT ctx = exceptionInfo->ContextRecord;
#ifndef  _WIN64
    jint fp_control_word = (* (jint*) StubRoutines::x86::addr_fpu_cntrl_wrd_std());
    if (fp_control_word != ctx->FloatSave.ControlWord) {
      // Restore FPCW and mask out FLT exceptions
      ctx->FloatSave.ControlWord = fp_control_word | 0xffffffc0;
      // Mask out pending FLT exceptions
      ctx->FloatSave.StatusWord &=  0xffffff00;
      return true;
    }
#else // !_WIN64
    // On Windows, the mxcsr control bits are non-volatile across calls
    // See also CR 6192333
    //
    jint MxCsr = INITIAL_MXCSR;
    // we can't use StubRoutines::x86::addr_mxcsr_std()
    // because in Win64 mxcsr is not saved there
    if (MxCsr != ctx->MxCsr) {
      ctx->MxCsr = MxCsr;
      return true;
    }
#endif // !_WIN64
  }
  }

  return false;
}
#endif

#ifndef _WIN64
static LONG WINAPI Uncaught_Exception_Handler(struct _EXCEPTION_POINTERS* exceptionInfo) {
  if (handle_FLT_exception(exceptionInfo)) {
    return EXCEPTION_CONTINUE_EXECUTION;
  }

  // we only override this on 32 bits, so only check it there
  if (prev_uef_handler != nullptr) {
    // We didn't handle this exception so pass it to the previous
    // UnhandledExceptionFilter.
    return (prev_uef_handler)(exceptionInfo);
  }

  return EXCEPTION_CONTINUE_SEARCH;
}
#endif

static inline void report_error(Thread* t, DWORD exception_code,
                                address addr, void* siginfo, void* context) {
  VMError::report_and_die(t, exception_code, addr, siginfo, context);

  // If UseOSErrorReporting, this will return here and save the error file
  // somewhere where we can find it in the minidump.
}

//-----------------------------------------------------------------------------
JNIEXPORT
LONG WINAPI topLevelExceptionFilter(struct _EXCEPTION_POINTERS* exceptionInfo) {
  if (InterceptOSException) return EXCEPTION_CONTINUE_SEARCH;
  PEXCEPTION_RECORD exception_record = exceptionInfo->ExceptionRecord;
  DWORD exception_code = exception_record->ExceptionCode;
#if defined(_M_ARM64)
  address pc = (address) exceptionInfo->ContextRecord->Pc;
#elif defined(_M_AMD64)
  address pc = (address) exceptionInfo->ContextRecord->Rip;
#else
  address pc = (address) exceptionInfo->ContextRecord->Eip;
#endif
  Thread* t = Thread::current_or_null_safe();

#ifndef _WIN64
  // Execution protection violation - win32 running on AMD64 only
  // Handled first to avoid misdiagnosis as a "normal" access violation;
  // This is safe to do because we have a new/unique ExceptionInformation
  // code for this condition.
  if (exception_code == EXCEPTION_ACCESS_VIOLATION) {
    int exception_subcode = (int) exception_record->ExceptionInformation[0];
    address addr = (address) exception_record->ExceptionInformation[1];

    if (exception_subcode == EXCEPTION_INFO_EXEC_VIOLATION) {
      size_t page_size = os::vm_page_size();

      // Make sure the pc and the faulting address are sane.
      //
      // If an instruction spans a page boundary, and the page containing
      // the beginning of the instruction is executable but the following
      // page is not, the pc and the faulting address might be slightly
      // different - we still want to unguard the 2nd page in this case.
      //
      // 15 bytes seems to be a (very) safe value for max instruction size.
      bool pc_is_near_addr =
        (pointer_delta((void*) addr, (void*) pc, sizeof(char)) < 15);
      bool instr_spans_page_boundary =
        (align_down((intptr_t) pc ^ (intptr_t) addr,
                         (intptr_t) page_size) > 0);

      if (pc == addr || (pc_is_near_addr && instr_spans_page_boundary)) {
        static volatile address last_addr =
          (address) os::non_memory_address_word();

        // In conservative mode, don't unguard unless the address is in the VM
        if (UnguardOnExecutionViolation > 0 && addr != last_addr &&
            (UnguardOnExecutionViolation > 1 || os::address_is_in_vm(addr))) {

          // Set memory to RWX and retry
          address page_start = align_down(addr, page_size);
          bool res = os::protect_memory((char*) page_start, page_size,
                                        os::MEM_PROT_RWX);

          log_debug(os)("Execution protection violation "
                        "at " INTPTR_FORMAT
                        ", unguarding " INTPTR_FORMAT ": %s", p2i(addr),
                        p2i(page_start), (res ? "success" : os::strerror(errno)));

          // Set last_addr so if we fault again at the same address, we don't
          // end up in an endless loop.
          //
          // There are two potential complications here.  Two threads trapping
          // at the same address at the same time could cause one of the
          // threads to think it already unguarded, and abort the VM.  Likely
          // very rare.
          //
          // The other race involves two threads alternately trapping at
          // different addresses and failing to unguard the page, resulting in
          // an endless loop.  This condition is probably even more unlikely
          // than the first.
          //
          // Although both cases could be avoided by using locks or thread
          // local last_addr, these solutions are unnecessary complication:
          // this handler is a best-effort safety net, not a complete solution.
          // It is disabled by default and should only be used as a workaround
          // in case we missed any no-execute-unsafe VM code.

          last_addr = addr;

          return EXCEPTION_CONTINUE_EXECUTION;
        }
      }

      // Last unguard failed or not unguarding
      tty->print_raw_cr("Execution protection violation");
#if !defined(USE_VECTORED_EXCEPTION_HANDLING)
      report_error(t, exception_code, addr, exception_record,
                   exceptionInfo->ContextRecord);
#endif
      return EXCEPTION_CONTINUE_SEARCH;
    }
  }
#endif // _WIN64

#if defined(_M_AMD64) || defined(_M_IX86)
  if ((exception_code == EXCEPTION_ACCESS_VIOLATION) &&
      VM_Version::is_cpuinfo_segv_addr(pc)) {
    // Verify that OS save/restore AVX registers.
    return Handle_Exception(exceptionInfo, VM_Version::cpuinfo_cont_addr());
  }

#if !defined(PRODUCT) && defined(_LP64)
  if ((exception_code == EXCEPTION_ACCESS_VIOLATION) &&
      VM_Version::is_cpuinfo_segv_addr_apx(pc)) {
    // Verify that OS save/restore APX registers.
    VM_Version::clear_apx_test_state();
    return Handle_Exception(exceptionInfo, VM_Version::cpuinfo_cont_addr_apx());
  }
#endif
#endif

  if (t != nullptr && t->is_Java_thread()) {
    JavaThread* thread = JavaThread::cast(t);
    bool in_java = thread->thread_state() == _thread_in_Java;
    bool in_native = thread->thread_state() == _thread_in_native;
    bool in_vm = thread->thread_state() == _thread_in_vm;

    // Handle potential stack overflows up front.
    if (exception_code == EXCEPTION_STACK_OVERFLOW) {
      StackOverflow* overflow_state = thread->stack_overflow_state();
      if (overflow_state->stack_guards_enabled()) {
        if (in_java) {
          frame fr;
          if (os::win32::get_frame_at_stack_banging_point(thread, exceptionInfo, pc, &fr)) {
            assert(fr.is_java_frame(), "Must be a Java frame");
            SharedRuntime::look_for_reserved_stack_annotated_method(thread, fr);
          }
        }
        // Yellow zone violation.  The o/s has unprotected the first yellow
        // zone page for us.  Note:  must call disable_stack_yellow_zone to
        // update the enabled status, even if the zone contains only one page.
        assert(!in_vm, "Undersized StackShadowPages");
        overflow_state->disable_stack_yellow_reserved_zone();
        // If not in java code, return and hope for the best.
        return in_java
            ? Handle_Exception(exceptionInfo, SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW))
            :  EXCEPTION_CONTINUE_EXECUTION;
      } else {
        // Fatal red zone violation.
        overflow_state->disable_stack_red_zone();
        tty->print_raw_cr("An unrecoverable stack overflow has occurred.");
#if !defined(USE_VECTORED_EXCEPTION_HANDLING)
        report_error(t, exception_code, pc, exception_record,
                      exceptionInfo->ContextRecord);
#endif
        return EXCEPTION_CONTINUE_SEARCH;
      }
    } else if (exception_code == EXCEPTION_ACCESS_VIOLATION) {
      if (in_java) {
        // Either stack overflow or null pointer exception.
        address addr = (address) exception_record->ExceptionInformation[1];
        address stack_end = thread->stack_end();
        if (addr < stack_end && addr >= stack_end - os::vm_page_size()) {
          // Stack overflow.
          assert(!os::uses_stack_guard_pages(),
                 "should be caught by red zone code above.");
          return Handle_Exception(exceptionInfo,
                                  SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW));
        }
        // Check for safepoint polling and implicit null
        // We only expect null pointers in the stubs (vtable)
        // the rest are checked explicitly now.
        CodeBlob* cb = CodeCache::find_blob(pc);
        if (cb != nullptr) {
          if (SafepointMechanism::is_poll_address(addr)) {
            address stub = SharedRuntime::get_poll_stub(pc);
            return Handle_Exception(exceptionInfo, stub);
          }
        }
#ifdef _WIN64
        // If it's a legal stack address map the entire region in
        if (thread->is_in_usable_stack(addr)) {
          addr = (address)((uintptr_t)addr &
                            (~((uintptr_t)os::vm_page_size() - (uintptr_t)1)));
          os::commit_memory((char *)addr, thread->stack_base() - addr,
                            !ExecMem);
          return EXCEPTION_CONTINUE_EXECUTION;
        }
#endif
        // Null pointer exception.
        if (MacroAssembler::uses_implicit_null_check((void*)addr)) {
          address stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
          if (stub != nullptr) return Handle_Exception(exceptionInfo, stub);
        }
        report_error(t, exception_code, pc, exception_record,
                      exceptionInfo->ContextRecord);
        return EXCEPTION_CONTINUE_SEARCH;
      }

#ifdef _WIN64
      // Special care for fast JNI field accessors.
      // jni_fast_Get<Primitive>Field can trap at certain pc's if a GC kicks
      // in and the heap gets shrunk before the field access.
      address slowcase_pc = JNI_FastGetField::find_slowcase_pc(pc);
      if (slowcase_pc != (address)-1) {
        return Handle_Exception(exceptionInfo, slowcase_pc);
      }
#endif

      // Stack overflow or null pointer exception in native code.
#if !defined(USE_VECTORED_EXCEPTION_HANDLING)
      report_error(t, exception_code, pc, exception_record,
                   exceptionInfo->ContextRecord);
#endif
      return EXCEPTION_CONTINUE_SEARCH;
    } // /EXCEPTION_ACCESS_VIOLATION
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    if (exception_code == EXCEPTION_IN_PAGE_ERROR) {
      nmethod* nm = nullptr;
      if (in_java) {
        CodeBlob* cb = CodeCache::find_blob(pc);
        nm = (cb != nullptr) ? cb->as_nmethod_or_null() : nullptr;
      }

      bool is_unsafe_memory_access = (in_native || in_java) && UnsafeMemoryAccess::contains_pc(pc);
      if (((in_vm || in_native || is_unsafe_memory_access) && thread->doing_unsafe_access()) ||
          (nm != nullptr && nm->has_unsafe_access())) {
        address next_pc =  Assembler::locate_next_instruction(pc);
        if (is_unsafe_memory_access) {
          next_pc = UnsafeMemoryAccess::page_error_continue_pc(pc);
        }
        return Handle_Exception(exceptionInfo, SharedRuntime::handle_unsafe_access(thread, next_pc));
      }
    }

#ifdef _M_ARM64
    if (in_java &&
        (exception_code == EXCEPTION_ILLEGAL_INSTRUCTION ||
          exception_code == EXCEPTION_ILLEGAL_INSTRUCTION_2)) {
      if (nativeInstruction_at(pc)->is_sigill_not_entrant()) {
        if (TraceTraps) {
          tty->print_cr("trap: not_entrant");
        }
        return Handle_Exception(exceptionInfo, SharedRuntime::get_handle_wrong_method_stub());
      }
    }
#endif

    if (in_java) {
      switch (exception_code) {
      case EXCEPTION_INT_DIVIDE_BY_ZERO:
        return Handle_Exception(exceptionInfo, SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_DIVIDE_BY_ZERO));

      case EXCEPTION_INT_OVERFLOW:
        return Handle_IDiv_Exception(exceptionInfo);

      } // switch
    }

#if defined(_M_AMD64) || defined(_M_IX86)
    if ((in_java || in_native) && handle_FLT_exception(exceptionInfo)) {
      return EXCEPTION_CONTINUE_EXECUTION;
    }
#endif

    if (in_java && (exception_code == EXCEPTION_ILLEGAL_INSTRUCTION || exception_code == EXCEPTION_ILLEGAL_INSTRUCTION_2)) {
      // Check for UD trap caused by NOP patching.
      // If it is, patch return address to be deopt handler.
      if (NativeDeoptInstruction::is_deopt_at(pc)) {
        CodeBlob* cb = CodeCache::find_blob(pc);
        if (cb != nullptr && cb->is_nmethod()) {
          nmethod* nm = cb->as_nmethod();
          frame fr = os::fetch_frame_from_context((void*)exceptionInfo->ContextRecord);
          address deopt = nm->is_method_handle_return(pc) ?
            nm->deopt_mh_handler_begin() :
            nm->deopt_handler_begin();
          assert(nm->insts_contains_inclusive(pc), "");
          nm->set_original_pc(&fr, pc);
          // Set pc to handler
          exceptionInfo->ContextRecord->PC_NAME = (DWORD64)deopt;
          return EXCEPTION_CONTINUE_EXECUTION;
        }
      }
    }
  }

#if !defined(USE_VECTORED_EXCEPTION_HANDLING)
  if (exception_code != EXCEPTION_BREAKPOINT) {
    report_error(t, exception_code, pc, exception_record,
                 exceptionInfo->ContextRecord);
  }
#endif
  return EXCEPTION_CONTINUE_SEARCH;
}

#if defined(USE_VECTORED_EXCEPTION_HANDLING)
LONG WINAPI topLevelVectoredExceptionFilter(struct _EXCEPTION_POINTERS* exceptionInfo) {
  PEXCEPTION_RECORD exceptionRecord = exceptionInfo->ExceptionRecord;
#if defined(_M_ARM64)
  address pc = (address) exceptionInfo->ContextRecord->Pc;
#elif defined(_M_AMD64)
  address pc = (address) exceptionInfo->ContextRecord->Rip;
#else
  address pc = (address) exceptionInfo->ContextRecord->Eip;
#endif

  // Fast path for code part of the code cache
  if (CodeCache::low_bound() <= pc && pc < CodeCache::high_bound()) {
    return topLevelExceptionFilter(exceptionInfo);
  }

  // If the exception occurred in the codeCache, pass control
  // to our normal exception handler.
  CodeBlob* cb = CodeCache::find_blob(pc);
  if (cb != nullptr) {
    return topLevelExceptionFilter(exceptionInfo);
  }

  return EXCEPTION_CONTINUE_SEARCH;
}
#endif

#if defined(USE_VECTORED_EXCEPTION_HANDLING)
LONG WINAPI topLevelUnhandledExceptionFilter(struct _EXCEPTION_POINTERS* exceptionInfo) {
  if (!InterceptOSException) {
    DWORD exceptionCode = exceptionInfo->ExceptionRecord->ExceptionCode;
#if defined(_M_ARM64)
    address pc = (address) exceptionInfo->ContextRecord->Pc;
#elif defined(_M_AMD64)
    address pc = (address) exceptionInfo->ContextRecord->Rip;
#else
    address pc = (address) exceptionInfo->ContextRecord->Eip;
#endif
    Thread* thread = Thread::current_or_null_safe();

    if (exceptionCode != EXCEPTION_BREAKPOINT) {
      report_error(thread, exceptionCode, pc, exceptionInfo->ExceptionRecord,
                  exceptionInfo->ContextRecord);
    }
  }

  return previousUnhandledExceptionFilter ? previousUnhandledExceptionFilter(exceptionInfo) : EXCEPTION_CONTINUE_SEARCH;
}
#endif

#ifndef _WIN64
// Special care for fast JNI accessors.
// jni_fast_Get<Primitive>Field can trap at certain pc's if a GC kicks in and
// the heap gets shrunk before the field access.
// Need to install our own structured exception handler since native code may
// install its own.
LONG WINAPI fastJNIAccessorExceptionFilter(struct _EXCEPTION_POINTERS* exceptionInfo) {
  DWORD exception_code = exceptionInfo->ExceptionRecord->ExceptionCode;
  if (exception_code == EXCEPTION_ACCESS_VIOLATION) {
    address pc = (address) exceptionInfo->ContextRecord->Eip;
    address addr = JNI_FastGetField::find_slowcase_pc(pc);
    if (addr != (address)-1) {
      return Handle_Exception(exceptionInfo, addr);
    }
  }
  return EXCEPTION_CONTINUE_SEARCH;
}

#define DEFINE_FAST_GETFIELD(Return, Fieldname, Result)                     \
  Return JNICALL jni_fast_Get##Result##Field_wrapper(JNIEnv *env,           \
                                                     jobject obj,           \
                                                     jfieldID fieldID) {    \
    __try {                                                                 \
      return (*JNI_FastGetField::jni_fast_Get##Result##Field_fp)(env,       \
                                                                 obj,       \
                                                                 fieldID);  \
    } __except(fastJNIAccessorExceptionFilter((_EXCEPTION_POINTERS*)        \
                                              _exception_info())) {         \
    }                                                                       \
    return 0;                                                               \
  }

DEFINE_FAST_GETFIELD(jboolean, bool,   Boolean)
DEFINE_FAST_GETFIELD(jbyte,    byte,   Byte)
DEFINE_FAST_GETFIELD(jchar,    char,   Char)
DEFINE_FAST_GETFIELD(jshort,   short,  Short)
DEFINE_FAST_GETFIELD(jint,     int,    Int)
DEFINE_FAST_GETFIELD(jlong,    long,   Long)
DEFINE_FAST_GETFIELD(jfloat,   float,  Float)
DEFINE_FAST_GETFIELD(jdouble,  double, Double)

address os::win32::fast_jni_accessor_wrapper(BasicType type) {
  switch (type) {
  case T_BOOLEAN: return (address)jni_fast_GetBooleanField_wrapper;
  case T_BYTE:    return (address)jni_fast_GetByteField_wrapper;
  case T_CHAR:    return (address)jni_fast_GetCharField_wrapper;
  case T_SHORT:   return (address)jni_fast_GetShortField_wrapper;
  case T_INT:     return (address)jni_fast_GetIntField_wrapper;
  case T_LONG:    return (address)jni_fast_GetLongField_wrapper;
  case T_FLOAT:   return (address)jni_fast_GetFloatField_wrapper;
  case T_DOUBLE:  return (address)jni_fast_GetDoubleField_wrapper;
  default:        ShouldNotReachHere();
  }
  return (address)-1;
}
#endif

// Virtual Memory

// Windows large page support is available on Windows 2003. In order to use
// large page memory, the administrator must first assign additional privilege
// to the user:
//   + select Control Panel -> Administrative Tools -> Local Security Policy
//   + select Local Policies -> User Rights Assignment
//   + double click "Lock pages in memory", add users and/or groups
//   + reboot
// Note the above steps are needed for administrator as well, as administrators
// by default do not have the privilege to lock pages in memory.
//
// Note about Windows 2003: although the API supports committing large page
// memory on a page-by-page basis and VirtualAlloc() returns success under this
// scenario, I found through experiment it only uses large page if the entire
// memory region is reserved and committed in a single VirtualAlloc() call.
// This makes Windows large page support more or less like Solaris ISM, in
// that the entire heap must be committed upfront. This probably will change
// in the future, if so the code below needs to be revisited.

#ifndef MEM_LARGE_PAGES
  #define MEM_LARGE_PAGES 0x20000000
#endif

// Container for NUMA node list info
class NUMANodeListHolder {
 private:
  int *_numa_used_node_list;  // allocated below
  int _numa_used_node_count;

  void free_node_list() {
    FREE_C_HEAP_ARRAY(int, _numa_used_node_list);
  }

 public:
  NUMANodeListHolder() {
    _numa_used_node_count = 0;
    _numa_used_node_list = nullptr;
    // do rest of initialization in build routine (after function pointers are set up)
  }

  ~NUMANodeListHolder() {
    free_node_list();
  }

  bool build() {
    DWORD_PTR proc_aff_mask;
    DWORD_PTR sys_aff_mask;
    if (!GetProcessAffinityMask(GetCurrentProcess(), &proc_aff_mask, &sys_aff_mask)) return false;
    ULONG highest_node_number;
    if (!GetNumaHighestNodeNumber(&highest_node_number)) return false;
    free_node_list();
    _numa_used_node_list = NEW_C_HEAP_ARRAY(int, highest_node_number + 1, mtInternal);
    for (unsigned int i = 0; i <= highest_node_number; i++) {
      ULONGLONG proc_mask_numa_node;
      if (!GetNumaNodeProcessorMask(i, &proc_mask_numa_node)) return false;
      if ((proc_aff_mask & proc_mask_numa_node)!=0) {
        _numa_used_node_list[_numa_used_node_count++] = i;
      }
    }
    return (_numa_used_node_count > 1);
  }

  int get_count() { return _numa_used_node_count; }
  int get_node_list_entry(int n) {
    // for indexes out of range, returns -1
    return (n < _numa_used_node_count ? _numa_used_node_list[n] : -1);
  }

} numa_node_list_holder;

static size_t _large_page_size = 0;

static bool request_lock_memory_privilege() {
  HANDLE hProcess = OpenProcess(PROCESS_QUERY_INFORMATION, FALSE,
                                os::current_process_id());

  bool success = false;
  HANDLE hToken = nullptr;
  LUID luid;
  if (hProcess != nullptr &&
      OpenProcessToken(hProcess, TOKEN_ADJUST_PRIVILEGES, &hToken) &&
      LookupPrivilegeValue(nullptr, "SeLockMemoryPrivilege", &luid)) {

    TOKEN_PRIVILEGES tp;
    tp.PrivilegeCount = 1;
    tp.Privileges[0].Luid = luid;
    tp.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;

    // AdjustTokenPrivileges() may return TRUE even when it couldn't change the
    // privilege. Check GetLastError() too. See MSDN document.
    if (AdjustTokenPrivileges(hToken, false, &tp, sizeof(tp), nullptr, nullptr) &&
        (GetLastError() == ERROR_SUCCESS)) {
      success = true;
    }
  }

  // Cleanup
  if (hProcess != nullptr) {
    CloseHandle(hProcess);
  }
  if (hToken != nullptr) {
    CloseHandle(hToken);
  }

  return success;
}

static bool numa_interleaving_init() {
  bool success = false;

  // print a warning if UseNUMAInterleaving flag is specified on command line
  bool warn_on_failure = !FLAG_IS_DEFAULT(UseNUMAInterleaving);

#define WARN(msg) if (warn_on_failure) { warning(msg); }

  // NUMAInterleaveGranularity cannot be less than vm_allocation_granularity (or _large_page_size if using large pages)
  size_t min_interleave_granularity = UseLargePages ? _large_page_size : os::vm_allocation_granularity();
  NUMAInterleaveGranularity = align_up(NUMAInterleaveGranularity, min_interleave_granularity);

  if (!numa_node_list_holder.build()) {
    WARN("Process does not cover multiple NUMA nodes.");
    WARN("...Ignoring UseNUMAInterleaving flag.");
    return false;
  }

  if (log_is_enabled(Debug, os, cpu)) {
    Log(os, cpu) log;
    log.debug("NUMA UsedNodeCount=%d, namely ", numa_node_list_holder.get_count());
    for (int i = 0; i < numa_node_list_holder.get_count(); i++) {
      log.debug("  %d ", numa_node_list_holder.get_node_list_entry(i));
    }
  }

#undef WARN

  return true;
}

// this routine is used whenever we need to reserve a contiguous VA range
// but we need to make separate VirtualAlloc calls for each piece of the range
// Reasons for doing this:
//  * UseLargePagesIndividualAllocation was set (normally only needed on WS2003 but possible to be set otherwise)
//  * UseNUMAInterleaving requires a separate node for each piece
static char* allocate_pages_individually(size_t bytes, char* addr, DWORD flags,
                                         DWORD prot,
                                         bool should_inject_error = false) {
  char * p_buf;
  // note: at setup time we guaranteed that NUMAInterleaveGranularity was aligned up to a page size
  size_t page_size = UseLargePages ? _large_page_size : os::vm_allocation_granularity();
  size_t chunk_size = UseNUMAInterleaving ? NUMAInterleaveGranularity : page_size;

  // first reserve enough address space in advance since we want to be
  // able to break a single contiguous virtual address range into multiple
  // large page commits but WS2003 does not allow reserving large page space
  // so we just use 4K pages for reserve, this gives us a legal contiguous
  // address space. then we will deallocate that reservation, and re alloc
  // using large pages
  const size_t size_of_reserve = bytes + chunk_size;
  if (bytes > size_of_reserve) {
    // Overflowed.
    return nullptr;
  }
  p_buf = (char *) virtualAlloc(addr,
                                size_of_reserve,  // size of Reserve
                                MEM_RESERVE,
                                PAGE_READWRITE);
  // If reservation failed, return null
  if (p_buf == nullptr) return nullptr;
  MemTracker::record_virtual_memory_reserve((address)p_buf, size_of_reserve, CALLER_PC);
  os::release_memory(p_buf, bytes + chunk_size);

  // we still need to round up to a page boundary (in case we are using large pages)
  // but not to a chunk boundary (in case InterleavingGranularity doesn't align with page size)
  // instead we handle this in the bytes_to_rq computation below
  p_buf = align_up(p_buf, page_size);

  // now go through and allocate one chunk at a time until all bytes are
  // allocated
  size_t  bytes_remaining = bytes;
  // An overflow of align_up() would have been caught above
  // in the calculation of size_of_reserve.
  char * next_alloc_addr = p_buf;
  HANDLE hProc = GetCurrentProcess();

#ifdef ASSERT
  // Variable for the failure injection
  int ran_num = os::random();
  size_t fail_after = ran_num % bytes;
#endif

  int count=0;
  while (bytes_remaining) {
    // select bytes_to_rq to get to the next chunk_size boundary

    size_t bytes_to_rq = MIN2(bytes_remaining, chunk_size - ((size_t)next_alloc_addr % chunk_size));
    // Note allocate and commit
    char * p_new;

#ifdef ASSERT
    bool inject_error_now = should_inject_error && (bytes_remaining <= fail_after);
#else
    const bool inject_error_now = false;
#endif

    if (inject_error_now) {
      p_new = nullptr;
    } else {
      if (!UseNUMAInterleaving) {
        p_new = (char *) virtualAlloc(next_alloc_addr,
                                      bytes_to_rq,
                                      flags,
                                      prot);
      } else {
        // get the next node to use from the used_node_list
        assert(numa_node_list_holder.get_count() > 0, "Multiple NUMA nodes expected");
        DWORD node = numa_node_list_holder.get_node_list_entry(count % numa_node_list_holder.get_count());
        p_new = (char *)virtualAllocExNuma(hProc, next_alloc_addr, bytes_to_rq, flags, prot, node);
      }
    }

    if (p_new == nullptr) {
      // Free any allocated pages
      if (next_alloc_addr > p_buf) {
        // Some memory was committed so release it.
        size_t bytes_to_release = bytes - bytes_remaining;
        // NMT has yet to record any individual blocks, so it
        // need to create a dummy 'reserve' record to match
        // the release.
        MemTracker::record_virtual_memory_reserve((address)p_buf,
                                                  bytes_to_release, CALLER_PC);
        os::release_memory(p_buf, bytes_to_release);
      }
#ifdef ASSERT
      if (should_inject_error) {
        log_develop_debug(pagesize)("Reserving pages individually failed.");
      }
#endif
      return nullptr;
    }

    bytes_remaining -= bytes_to_rq;
    next_alloc_addr += bytes_to_rq;
    count++;
  }
  // Although the memory is allocated individually, it is returned as one.
  // NMT records it as one block.
  if ((flags & MEM_COMMIT) != 0) {
    MemTracker::record_virtual_memory_reserve_and_commit((address)p_buf, bytes, CALLER_PC);
  } else {
    MemTracker::record_virtual_memory_reserve((address)p_buf, bytes, CALLER_PC);
  }

  // made it this far, success
  return p_buf;
}

static size_t large_page_init_decide_size() {
  // print a warning if any large page related flag is specified on command line
  bool warn_on_failure = !FLAG_IS_DEFAULT(UseLargePages) ||
                         !FLAG_IS_DEFAULT(LargePageSizeInBytes);

#define WARN(msg) if (warn_on_failure) { warning(msg); }

  if (!request_lock_memory_privilege()) {
    WARN("JVM cannot use large page memory because it does not have enough privilege to lock pages in memory.");
    return 0;
  }

  size_t size = GetLargePageMinimum();
  if (size == 0) {
    WARN("Large page is not supported by the processor.");
    return 0;
  }

#if defined(IA32) || defined(AMD64)
  if (size > 4*M || LargePageSizeInBytes > 4*M) {
    WARN("JVM cannot use large pages bigger than 4mb.");
    return 0;
  }
#endif

  if (LargePageSizeInBytes > 0 && LargePageSizeInBytes % size == 0) {
    size = LargePageSizeInBytes;
  }

#undef WARN

  return size;
}

void os::large_page_init() {
  if (!UseLargePages) {
    return;
  }

  _large_page_size = large_page_init_decide_size();
  const size_t default_page_size = os::vm_page_size();
  if (_large_page_size > default_page_size) {
    _page_sizes.add(_large_page_size);
  }

  UseLargePages = _large_page_size != 0;
}

int os::create_file_for_heap(const char* dir) {

  const char name_template[] = "/jvmheap.XXXXXX";

  size_t fullname_len = strlen(dir) + strlen(name_template);
  char *fullname = (char*)os::malloc(fullname_len + 1, mtInternal);
  if (fullname == nullptr) {
    vm_exit_during_initialization(err_msg("Malloc failed during creation of backing file for heap (%s)", os::strerror(errno)));
    return -1;
  }
  int n = snprintf(fullname, fullname_len + 1, "%s%s", dir, name_template);
  assert((size_t)n == fullname_len, "Unexpected number of characters in string");

  os::native_path(fullname);

  char *path = _mktemp(fullname);
  if (path == nullptr) {
    warning("_mktemp could not create file name from template %s (%s)", fullname, os::strerror(errno));
    os::free(fullname);
    return -1;
  }

  int fd = _open(path, O_RDWR | O_CREAT | O_TEMPORARY | O_EXCL, S_IWRITE | S_IREAD);

  os::free(fullname);
  if (fd < 0) {
    warning("Problem opening file for heap (%s)", os::strerror(errno));
    return -1;
  }
  return fd;
}

// If 'base' is not null, function will return null if it cannot get 'base'
char* os::map_memory_to_file(char* base, size_t size, int fd) {
  assert(fd != -1, "File descriptor is not valid");

  HANDLE fh = (HANDLE)_get_osfhandle(fd);
#ifdef _LP64
  HANDLE fileMapping = CreateFileMapping(fh, nullptr, PAGE_READWRITE,
    (DWORD)(size >> 32), (DWORD)(size & 0xFFFFFFFF), nullptr);
#else
  HANDLE fileMapping = CreateFileMapping(fh, nullptr, PAGE_READWRITE,
    0, (DWORD)size, nullptr);
#endif
  if (fileMapping == nullptr) {
    if (GetLastError() == ERROR_DISK_FULL) {
      vm_exit_during_initialization(err_msg("Could not allocate sufficient disk space for Java heap"));
    }
    else {
      vm_exit_during_initialization(err_msg("Error in mapping Java heap at the given filesystem directory"));
    }

    return nullptr;
  }

  LPVOID addr = mapViewOfFileEx(fileMapping, FILE_MAP_WRITE, 0, 0, size, base);

  CloseHandle(fileMapping);

  return (char*)addr;
}

char* os::replace_existing_mapping_with_file_mapping(char* base, size_t size, int fd) {
  assert(fd != -1, "File descriptor is not valid");
  assert(base != nullptr, "Base address cannot be null");

  release_memory(base, size);
  return map_memory_to_file(base, size, fd);
}

// Multiple threads can race in this code but it's not possible to unmap small sections of
// virtual space to get requested alignment, like posix-like os's.
// Windows prevents multiple thread from remapping over each other so this loop is thread-safe.
static char* map_or_reserve_memory_aligned(size_t size, size_t alignment, int file_desc, MEMFLAGS flag = mtNone) {
  assert(is_aligned(alignment, os::vm_allocation_granularity()),
      "Alignment must be a multiple of allocation granularity (page size)");
  assert(is_aligned(size, os::vm_allocation_granularity()),
      "Size must be a multiple of allocation granularity (page size)");

  size_t extra_size = size + alignment;
  assert(extra_size >= size, "overflow, size is too large to allow alignment");

  char* aligned_base = nullptr;
  static const int max_attempts = 20;

  for (int attempt = 0; attempt < max_attempts && aligned_base == nullptr; attempt ++) {
    char* extra_base = file_desc != -1 ? os::map_memory_to_file(extra_size, file_desc, flag) :
                                         os::reserve_memory(extra_size, false, flag);
    if (extra_base == nullptr) {
      return nullptr;
    }
    // Do manual alignment
    aligned_base = align_up(extra_base, alignment);

    bool rc = (file_desc != -1) ? os::unmap_memory(extra_base, extra_size) :
                                  os::release_memory(extra_base, extra_size);
    assert(rc, "release failed");
    if (!rc) {
      return nullptr;
    }

    // Attempt to map, into the just vacated space, the slightly smaller aligned area.
    // Which may fail, hence the loop.
    aligned_base = file_desc != -1 ? os::attempt_map_memory_to_file_at(aligned_base, size, file_desc, flag) :
                                     os::attempt_reserve_memory_at(aligned_base, size, false, flag);
  }

  assert(aligned_base != nullptr, "Did not manage to re-map after %d attempts?", max_attempts);

  return aligned_base;
}

char* os::reserve_memory_aligned(size_t size, size_t alignment, bool exec) {
  // exec can be ignored
  return map_or_reserve_memory_aligned(size, alignment, -1 /* file_desc */);
}

char* os::map_memory_to_file_aligned(size_t size, size_t alignment, int fd, MEMFLAGS flag) {
  return map_or_reserve_memory_aligned(size, alignment, fd, flag);
}

char* os::pd_reserve_memory(size_t bytes, bool exec) {
  return pd_attempt_reserve_memory_at(nullptr /* addr */, bytes, exec);
}

// Reserve memory at an arbitrary address, only if that area is
// available (and not reserved for something else).
char* os::pd_attempt_reserve_memory_at(char* addr, size_t bytes, bool exec) {
  assert((size_t)addr % os::vm_allocation_granularity() == 0,
         "reserve alignment");
  assert(bytes % os::vm_page_size() == 0, "reserve page size");
  char* res;
  // note that if UseLargePages is on, all the areas that require interleaving
  // will go thru reserve_memory_special rather than thru here.
  bool use_individual = (UseNUMAInterleaving && !UseLargePages);
  if (!use_individual) {
    res = (char*)virtualAlloc(addr, bytes, MEM_RESERVE, PAGE_READWRITE);
  } else {
    elapsedTimer reserveTimer;
    if (Verbose && PrintMiscellaneous) reserveTimer.start();
    // in numa interleaving, we have to allocate pages individually
    // (well really chunks of NUMAInterleaveGranularity size)
    res = allocate_pages_individually(bytes, addr, MEM_RESERVE, PAGE_READWRITE);
    if (res == nullptr) {
      warning("NUMA page allocation failed");
    }
    if (Verbose && PrintMiscellaneous) {
      reserveTimer.stop();
      tty->print_cr("reserve_memory of %Ix bytes took " JLONG_FORMAT " ms (" JLONG_FORMAT " ticks)", bytes,
                    reserveTimer.milliseconds(), reserveTimer.ticks());
    }
  }
  assert(res == nullptr || addr == nullptr || addr == res,
         "Unexpected address from reserve.");

  return res;
}

size_t os::vm_min_address() {
  assert(is_aligned(_vm_min_address_default, os::vm_allocation_granularity()), "Sanity");
  return _vm_min_address_default;
}

char* os::pd_attempt_map_memory_to_file_at(char* requested_addr, size_t bytes, int file_desc) {
  assert(file_desc >= 0, "file_desc is not valid");
  return map_memory_to_file(requested_addr, bytes, file_desc);
}

size_t os::large_page_size() {
  return _large_page_size;
}

bool os::can_commit_large_page_memory() {
  // Windows only uses large page memory when the entire region is reserved
  // and committed in a single VirtualAlloc() call. This may change in the
  // future, but with Windows 2003 it's not possible to commit on demand.
  return false;
}

static char* reserve_large_pages_individually(size_t size, char* req_addr, bool exec) {
  log_debug(pagesize)("Reserving large pages individually.");

  const DWORD prot = exec ? PAGE_EXECUTE_READWRITE : PAGE_READWRITE;
  const DWORD flags = MEM_RESERVE | MEM_COMMIT | MEM_LARGE_PAGES;

  char * p_buf = allocate_pages_individually(size, req_addr, flags, prot, LargePagesIndividualAllocationInjectError);
  if (p_buf == nullptr) {
    // give an appropriate warning message
    if (UseNUMAInterleaving) {
      warning("NUMA large page allocation failed, UseLargePages flag ignored");
    }
    if (UseLargePagesIndividualAllocation) {
      warning("Individually allocated large pages failed, "
              "use -XX:-UseLargePagesIndividualAllocation to turn off");
    }
    return nullptr;
  }
  return p_buf;
}

static char* reserve_large_pages_single_range(size_t size, char* req_addr, bool exec) {
  log_debug(pagesize)("Reserving large pages in a single large chunk.");

  const DWORD prot = exec ? PAGE_EXECUTE_READWRITE : PAGE_READWRITE;
  const DWORD flags = MEM_RESERVE | MEM_COMMIT | MEM_LARGE_PAGES;

  return (char *) virtualAlloc(req_addr, size, flags, prot);
}

static char* reserve_large_pages(size_t size, char* req_addr, bool exec) {
  // with large pages, there are two cases where we need to use Individual Allocation
  // 1) the UseLargePagesIndividualAllocation flag is set (set by default on WS2003)
  // 2) NUMA Interleaving is enabled, in which case we use a different node for each page
  if (UseLargePagesIndividualAllocation || UseNUMAInterleaving) {
    return reserve_large_pages_individually(size, req_addr, exec);
  }
  return reserve_large_pages_single_range(size, req_addr, exec);
}

static char* find_aligned_address(size_t size, size_t alignment) {
  // Temporary reserve memory large enough to ensure we can get the requested
  // alignment and still fit the reservation.
  char* addr = (char*) virtualAlloc(nullptr, size + alignment, MEM_RESERVE, PAGE_NOACCESS);
  // Align the address to the requested alignment.
  char* aligned_addr = align_up(addr, alignment);
  // Free the temporary reservation.
  virtualFree(addr, 0, MEM_RELEASE);

  return aligned_addr;
}

static char* reserve_large_pages_aligned(size_t size, size_t alignment, bool exec) {
  log_debug(pagesize)("Reserving large pages at an aligned address, alignment=" SIZE_FORMAT "%s",
                      byte_size_in_exact_unit(alignment), exact_unit_for_byte_size(alignment));

  // Will try to find a suitable address at most 20 times. The reason we need to try
  // multiple times is that between finding the aligned address and trying to commit
  // the large pages another thread might have reserved an overlapping region.
  const int attempts_limit = 20;
  for (int attempts = 0; attempts < attempts_limit; attempts++)  {
    // Find aligned address.
    char* aligned_address = find_aligned_address(size, alignment);

    // Try to do the large page reservation using the aligned address.
    aligned_address = reserve_large_pages(size, aligned_address, exec);
    if (aligned_address != nullptr) {
      // Reservation at the aligned address succeeded.
      guarantee(is_aligned(aligned_address, alignment), "Must be aligned");
      return aligned_address;
    }
  }

  log_debug(pagesize)("Failed reserving large pages at aligned address");
  return nullptr;
}

char* os::pd_reserve_memory_special(size_t bytes, size_t alignment, size_t page_size, char* addr,
                                    bool exec) {
  assert(UseLargePages, "only for large pages");
  assert(page_size == os::large_page_size(), "Currently only support one large page size on Windows");
  assert(is_aligned(addr, alignment), "Must be");
  assert(is_aligned(addr, page_size), "Must be");

  if (!is_aligned(bytes, page_size)) {
    // Fallback to small pages, Windows does not support mixed mappings.
    return nullptr;
  }

  // The requested alignment can be larger than the page size, for example with G1
  // the alignment is bound to the heap region size. So this reservation needs to
  // ensure that the requested alignment is met. When there is a requested address
  // this solves it self, since it must be properly aligned already.
  if (addr == nullptr && alignment > page_size) {
    return reserve_large_pages_aligned(bytes, alignment, exec);
  }

  // No additional requirements, just reserve the large pages.
  return reserve_large_pages(bytes, addr, exec);
}

bool os::pd_release_memory_special(char* base, size_t bytes) {
  assert(base != nullptr, "Sanity check");
  return pd_release_memory(base, bytes);
}

static void warn_fail_commit_memory(char* addr, size_t bytes, bool exec) {
  int err = os::get_last_error();
  char buf[256];
  size_t buf_len = os::lasterror(buf, sizeof(buf));
  warning("INFO: os::commit_memory(" PTR_FORMAT ", " SIZE_FORMAT
          ", %d) failed; error='%s' (DOS error/errno=%d)", addr, bytes,
          exec, buf_len != 0 ? buf : "<no_error_string>", err);
}

bool os::pd_commit_memory(char* addr, size_t bytes, bool exec) {
  if (bytes == 0) {
    // Don't bother the OS with noops.
    return true;
  }
  assert((size_t) addr % os::vm_page_size() == 0, "commit on page boundaries");
  assert(bytes % os::vm_page_size() == 0, "commit in page-sized chunks");
  // Don't attempt to print anything if the OS call fails. We're
  // probably low on resources, so the print itself may cause crashes.

  // unless we have NUMAInterleaving enabled, the range of a commit
  // is always within a reserve covered by a single VirtualAlloc
  // in that case we can just do a single commit for the requested size
  if (!UseNUMAInterleaving) {
    if (virtualAlloc(addr, bytes, MEM_COMMIT, PAGE_READWRITE) == nullptr) {
      NOT_PRODUCT(warn_fail_commit_memory(addr, bytes, exec);)
      return false;
    }
    if (exec) {
      DWORD oldprot;
      // Windows doc says to use VirtualProtect to get execute permissions
      if (!VirtualProtect(addr, bytes, PAGE_EXECUTE_READWRITE, &oldprot)) {
        NOT_PRODUCT(warn_fail_commit_memory(addr, bytes, exec);)
        return false;
      }
    }
    return true;
  } else {

    // when NUMAInterleaving is enabled, the commit might cover a range that
    // came from multiple VirtualAlloc reserves (using allocate_pages_individually).
    // VirtualQuery can help us determine that.  The RegionSize that VirtualQuery
    // returns represents the number of bytes that can be committed in one step.
    size_t bytes_remaining = bytes;
    char * next_alloc_addr = addr;
    while (bytes_remaining > 0) {
      MEMORY_BASIC_INFORMATION alloc_info;
      VirtualQuery(next_alloc_addr, &alloc_info, sizeof(alloc_info));
      size_t bytes_to_rq = MIN2(bytes_remaining, (size_t)alloc_info.RegionSize);
      if (virtualAlloc(next_alloc_addr, bytes_to_rq, MEM_COMMIT,
                       PAGE_READWRITE) == nullptr) {
        NOT_PRODUCT(warn_fail_commit_memory(next_alloc_addr, bytes_to_rq,
                                            exec);)
        return false;
      }
      if (exec) {
        DWORD oldprot;
        if (!VirtualProtect(next_alloc_addr, bytes_to_rq,
                            PAGE_EXECUTE_READWRITE, &oldprot)) {
          NOT_PRODUCT(warn_fail_commit_memory(next_alloc_addr, bytes_to_rq,
                                              exec);)
          return false;
        }
      }
      bytes_remaining -= bytes_to_rq;
      next_alloc_addr += bytes_to_rq;
    }
  }
  // if we made it this far, return true
  return true;
}

bool os::pd_commit_memory(char* addr, size_t size, size_t alignment_hint,
                          bool exec) {
  // alignment_hint is ignored on this OS
  return pd_commit_memory(addr, size, exec);
}

void os::pd_commit_memory_or_exit(char* addr, size_t size, bool exec,
                                  const char* mesg) {
  assert(mesg != nullptr, "mesg must be specified");
  if (!pd_commit_memory(addr, size, exec)) {
    warn_fail_commit_memory(addr, size, exec);
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "%s", mesg);
  }
}

void os::pd_commit_memory_or_exit(char* addr, size_t size,
                                  size_t alignment_hint, bool exec,
                                  const char* mesg) {
  // alignment_hint is ignored on this OS
  pd_commit_memory_or_exit(addr, size, exec, mesg);
}

bool os::pd_uncommit_memory(char* addr, size_t bytes, bool exec) {
  if (bytes == 0) {
    // Don't bother the OS with noops.
    return true;
  }
  assert((size_t) addr % os::vm_page_size() == 0, "uncommit on page boundaries");
  assert(bytes % os::vm_page_size() == 0, "uncommit in page-sized chunks");
  return (virtualFree(addr, bytes, MEM_DECOMMIT) == TRUE);
}

bool os::pd_release_memory(char* addr, size_t bytes) {
  // Given a range we are to release, we require a mapping to start at the beginning of that range;
  //  if NUMA or LP we allow the range to contain multiple mappings, which have to cover the range
  //  completely; otherwise the range must match an OS mapping exactly.
  address start = (address)addr;
  address end = start + bytes;
  os::win32::mapping_info_t mi;
  const bool multiple_mappings_allowed = UseLargePagesIndividualAllocation || UseNUMAInterleaving;
  address p = start;
  bool first_mapping = true;

  do {
    // Find mapping and check it
    const char* err = nullptr;
    if (!os::win32::find_mapping(p, &mi)) {
      err = "no mapping found";
    } else {
      if (first_mapping) {
        if (mi.base != start) {
          err = "base address mismatch";
        }
        if (multiple_mappings_allowed ? (mi.size > bytes) : (mi.size != bytes)) {
          err = "size mismatch";
        }
      } else {
        assert(p == mi.base && mi.size > 0, "Sanity");
        if (mi.base + mi.size > end) {
          err = "mapping overlaps end";
        }
        if (mi.size == 0) {
          err = "zero length mapping?"; // Should never happen; just to prevent endlessly looping in release.
        }
      }
    }
    // Handle mapping error. We assert in debug, unconditionally print a warning in release.
    if (err != nullptr) {
      log_warning(os)("bad release: [" PTR_FORMAT "-" PTR_FORMAT "): %s", p2i(start), p2i(end), err);
#ifdef ASSERT
      os::print_memory_mappings((char*)start, bytes, tty);
      assert(false, "bad release: [" PTR_FORMAT "-" PTR_FORMAT "): %s", p2i(start), p2i(end), err);
#endif
      return false;
    }
    // Free this range
    if (virtualFree(p, 0, MEM_RELEASE) == FALSE) {
      return false;
    }
    first_mapping = false;
    p = mi.base + mi.size;
  } while (p < end);

  return true;
}

bool os::pd_create_stack_guard_pages(char* addr, size_t size) {
  return os::commit_memory(addr, size, !ExecMem);
}

bool os::remove_stack_guard_pages(char* addr, size_t size) {
  return os::uncommit_memory(addr, size);
}

static bool protect_pages_individually(char* addr, size_t bytes, unsigned int p, DWORD *old_status) {
  uint count = 0;
  bool ret = false;
  size_t bytes_remaining = bytes;
  char * next_protect_addr = addr;

  // Use VirtualQuery() to get the chunk size.
  while (bytes_remaining) {
    MEMORY_BASIC_INFORMATION alloc_info;
    if (VirtualQuery(next_protect_addr, &alloc_info, sizeof(alloc_info)) == 0) {
      return false;
    }

    size_t bytes_to_protect = MIN2(bytes_remaining, (size_t)alloc_info.RegionSize);
    // We used different API at allocate_pages_individually() based on UseNUMAInterleaving,
    // but we don't distinguish here as both cases are protected by same API.
    ret = VirtualProtect(next_protect_addr, bytes_to_protect, p, old_status) != 0;
    warning("Failed protecting pages individually for chunk #%u", count);
    if (!ret) {
      return false;
    }

    bytes_remaining -= bytes_to_protect;
    next_protect_addr += bytes_to_protect;
    count++;
  }
  return ret;
}

// Set protections specified
bool os::protect_memory(char* addr, size_t bytes, ProtType prot,
                        bool is_committed) {
  unsigned int p = 0;
  switch (prot) {
  case MEM_PROT_NONE: p = PAGE_NOACCESS; break;
  case MEM_PROT_READ: p = PAGE_READONLY; break;
  case MEM_PROT_RW:   p = PAGE_READWRITE; break;
  case MEM_PROT_RWX:  p = PAGE_EXECUTE_READWRITE; break;
  default:
    ShouldNotReachHere();
  }

  DWORD old_status;

  // Strange enough, but on Win32 one can change protection only for committed
  // memory, not a big deal anyway, as bytes less or equal than 64K
  if (!is_committed) {
    commit_memory_or_exit(addr, bytes, prot == MEM_PROT_RWX,
                          "cannot commit protection page");
  }
  // One cannot use os::guard_memory() here, as on Win32 guard page
  // have different (one-shot) semantics, from MSDN on PAGE_GUARD:
  //
  // Pages in the region become guard pages. Any attempt to access a guard page
  // causes the system to raise a STATUS_GUARD_PAGE exception and turn off
  // the guard page status. Guard pages thus act as a one-time access alarm.
  bool ret;
  if (UseNUMAInterleaving) {
    // If UseNUMAInterleaving is enabled, the pages may have been allocated a chunk at a time,
    // so we must protect the chunks individually.
    ret = protect_pages_individually(addr, bytes, p, &old_status);
  } else {
    ret = VirtualProtect(addr, bytes, p, &old_status) != 0;
  }
#ifdef ASSERT
  if (!ret) {
    int err = os::get_last_error();
    char buf[256];
    size_t buf_len = os::lasterror(buf, sizeof(buf));
    warning("INFO: os::protect_memory(" PTR_FORMAT ", " SIZE_FORMAT
          ") failed; error='%s' (DOS error/errno=%d)", addr, bytes,
          buf_len != 0 ? buf : "<no_error_string>", err);
  }
#endif
  return ret;
}

bool os::guard_memory(char* addr, size_t bytes) {
  DWORD old_status;
  return VirtualProtect(addr, bytes, PAGE_READWRITE | PAGE_GUARD, &old_status) != 0;
}

bool os::unguard_memory(char* addr, size_t bytes) {
  DWORD old_status;
  return VirtualProtect(addr, bytes, PAGE_READWRITE, &old_status) != 0;
}

void os::pd_realign_memory(char *addr, size_t bytes, size_t alignment_hint) { }
void os::pd_free_memory(char *addr, size_t bytes, size_t alignment_hint) { }

size_t os::pd_pretouch_memory(void* first, void* last, size_t page_size) {
  return page_size;
}

void os::numa_make_global(char *addr, size_t bytes)    { }
void os::numa_make_local(char *addr, size_t bytes, int lgrp_hint)    { }
bool os::numa_topology_changed()                       { return false; }
size_t os::numa_get_groups_num()                       { return MAX2(numa_node_list_holder.get_count(), 1); }
int os::numa_get_group_id()                            { return 0; }
size_t os::numa_get_leaf_groups(uint *ids, size_t size) {
  if (numa_node_list_holder.get_count() == 0 && size > 0) {
    // Provide an answer for UMA systems
    ids[0] = 0;
    return 1;
  } else {
    // check for size bigger than actual groups_num
    size = MIN2(size, numa_get_groups_num());
    for (int i = 0; i < (int)size; i++) {
      int node_id = numa_node_list_holder.get_node_list_entry(i);
      ids[i] = checked_cast<uint>(node_id);
    }
    return size;
  }
}

int os::numa_get_group_id_for_address(const void* address) {
  return 0;
}

bool os::numa_get_group_ids_for_range(const void** addresses, int* lgrp_ids, size_t count) {
  return false;
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).
#ifdef _M_ARM64
  // AArch64 has a maximum addressable space of 48-bits
  return (char*)((1ull << 48) - 1);
#else
  return (char*)-1;
#endif
}

#define MAX_ERROR_COUNT 100
#define SYS_THREAD_ERROR 0xffffffffUL

void os::pd_start_thread(Thread* thread) {
  DWORD ret = ResumeThread(thread->osthread()->thread_handle());
  // Returns previous suspend state:
  // 0:  Thread was not suspended
  // 1:  Thread is running now
  // >1: Thread is still suspended.
  assert(ret != SYS_THREAD_ERROR, "StartThread failed"); // should propagate back
}


// Short sleep, direct OS call.
//
// ms = 0, means allow others (if any) to run.
//
void os::naked_short_sleep(jlong ms) {
  assert(ms < 1000, "Un-interruptable sleep, short time use only");
  Sleep(ms);
}

// Windows does not provide sleep functionality with nanosecond resolution, so we
// try to approximate this with spinning combined with yielding if another thread
// is ready to run on the current processor.
void os::naked_short_nanosleep(jlong ns) {
  assert(ns > -1 && ns < NANOUNITS, "Un-interruptable sleep, short time use only");

  int64_t start = os::javaTimeNanos();
  do {
    if (SwitchToThread() == 0) {
      // Nothing else is ready to run on this cpu, spin a little
      SpinPause();
    }
  } while (os::javaTimeNanos() - start < ns);
}

// Sleep forever; naked call to OS-specific sleep; use with CAUTION
void os::infinite_sleep() {
  while (true) {    // sleep forever ...
    Sleep(100000);  // ... 100 seconds at a time
  }
}

typedef BOOL (WINAPI * STTSignature)(void);

void os::naked_yield() {
  // Consider passing back the return value from SwitchToThread().
  SwitchToThread();
}

// Win32 only gives you access to seven real priorities at a time,
// so we compress Java's ten down to seven.  It would be better
// if we dynamically adjusted relative priorities.

int os::java_to_os_priority[CriticalPriority + 1] = {
  THREAD_PRIORITY_IDLE,                         // 0  Entry should never be used
  THREAD_PRIORITY_LOWEST,                       // 1  MinPriority
  THREAD_PRIORITY_LOWEST,                       // 2
  THREAD_PRIORITY_BELOW_NORMAL,                 // 3
  THREAD_PRIORITY_BELOW_NORMAL,                 // 4
  THREAD_PRIORITY_NORMAL,                       // 5  NormPriority
  THREAD_PRIORITY_NORMAL,                       // 6
  THREAD_PRIORITY_ABOVE_NORMAL,                 // 7
  THREAD_PRIORITY_ABOVE_NORMAL,                 // 8
  THREAD_PRIORITY_HIGHEST,                      // 9  NearMaxPriority
  THREAD_PRIORITY_HIGHEST,                      // 10 MaxPriority
  THREAD_PRIORITY_HIGHEST                       // 11 CriticalPriority
};

int prio_policy1[CriticalPriority + 1] = {
  THREAD_PRIORITY_IDLE,                         // 0  Entry should never be used
  THREAD_PRIORITY_LOWEST,                       // 1  MinPriority
  THREAD_PRIORITY_LOWEST,                       // 2
  THREAD_PRIORITY_BELOW_NORMAL,                 // 3
  THREAD_PRIORITY_BELOW_NORMAL,                 // 4
  THREAD_PRIORITY_NORMAL,                       // 5  NormPriority
  THREAD_PRIORITY_ABOVE_NORMAL,                 // 6
  THREAD_PRIORITY_ABOVE_NORMAL,                 // 7
  THREAD_PRIORITY_HIGHEST,                      // 8
  THREAD_PRIORITY_HIGHEST,                      // 9  NearMaxPriority
  THREAD_PRIORITY_TIME_CRITICAL,                // 10 MaxPriority
  THREAD_PRIORITY_TIME_CRITICAL                 // 11 CriticalPriority
};

static int prio_init() {
  // If ThreadPriorityPolicy is 1, switch tables
  if (ThreadPriorityPolicy == 1) {
    int i;
    for (i = 0; i < CriticalPriority + 1; i++) {
      os::java_to_os_priority[i] = prio_policy1[i];
    }
  }
  if (UseCriticalJavaThreadPriority) {
    os::java_to_os_priority[MaxPriority] = os::java_to_os_priority[CriticalPriority];
  }
  return 0;
}

OSReturn os::set_native_priority(Thread* thread, int priority) {
  if (!UseThreadPriorities) return OS_OK;
  bool ret = SetThreadPriority(thread->osthread()->thread_handle(), priority) != 0;
  return ret ? OS_OK : OS_ERR;
}

OSReturn os::get_native_priority(const Thread* const thread,
                                 int* priority_ptr) {
  if (!UseThreadPriorities) {
    *priority_ptr = java_to_os_priority[NormPriority];
    return OS_OK;
  }
  int os_prio = GetThreadPriority(thread->osthread()->thread_handle());
  if (os_prio == THREAD_PRIORITY_ERROR_RETURN) {
    assert(false, "GetThreadPriority failed");
    return OS_ERR;
  }
  *priority_ptr = os_prio;
  return OS_OK;
}

// GetCurrentThreadId() returns DWORD
intx os::current_thread_id()  { return GetCurrentThreadId(); }

static int _initial_pid = 0;

int os::current_process_id() {
  return (_initial_pid ? _initial_pid : _getpid());
}

int    os::win32::_processor_type            = 0;
// Processor level is not available on non-NT systems, use vm_version instead
int    os::win32::_processor_level           = 0;
julong os::win32::_physical_memory           = 0;

bool   os::win32::_is_windows_server         = false;

// 6573254
// Currently, the bug is observed across all the supported Windows releases,
// including the latest one (as of this writing - Windows Server 2012 R2)
bool   os::win32::_has_exit_bug              = true;

int    os::win32::_major_version             = 0;
int    os::win32::_minor_version             = 0;
int    os::win32::_build_number              = 0;
int    os::win32::_build_minor               = 0;

bool   os::win32::_processor_group_warning_displayed = false;
bool   os::win32::_job_object_processor_group_warning_displayed = false;

void os::win32::initialize_windows_version() {
  assert(_major_version == 0, "windows version already initialized.");

  VS_FIXEDFILEINFO *file_info;
  TCHAR kernel32_path[MAX_PATH];
  UINT len, ret;
  char error_msg_buffer[512];

  // Get the full path to \Windows\System32\kernel32.dll and use that for
  // determining what version of Windows we're running on.
  len = MAX_PATH - (UINT)strlen("\\kernel32.dll") - 1;
  ret = GetSystemDirectory(kernel32_path, len);
  if (ret == 0 || ret > len) {
    size_t buf_len = os::lasterror(error_msg_buffer, sizeof(error_msg_buffer));
    warning("Attempt to determine system directory failed: %s", buf_len != 0 ? error_msg_buffer : "<unknown error>");
    return;
  }
  strncat(kernel32_path, "\\kernel32.dll", MAX_PATH - ret);

  DWORD version_size = GetFileVersionInfoSize(kernel32_path, nullptr);
  if (version_size == 0) {
    size_t buf_len = os::lasterror(error_msg_buffer, sizeof(error_msg_buffer));
    warning("Failed to determine whether the OS can retrieve version information from kernel32.dll: %s", buf_len != 0 ? error_msg_buffer : "<unknown error>");
    return;
  }

  LPTSTR version_info = (LPTSTR)os::malloc(version_size, mtInternal);
  if (version_info == nullptr) {
    warning("os::malloc() failed to allocate %ld bytes for GetFileVersionInfo buffer", version_size);
    return;
  }

  if (GetFileVersionInfo(kernel32_path, 0, version_size, version_info) == 0) {
    os::free(version_info);
    size_t buf_len = os::lasterror(error_msg_buffer, sizeof(error_msg_buffer));
    warning("Attempt to retrieve version information from kernel32.dll failed: %s", buf_len != 0 ? error_msg_buffer : "<unknown error>");
    return;
  }

  if (VerQueryValue(version_info, TEXT("\\"), (LPVOID*)&file_info, &len) == 0) {
    os::free(version_info);
    size_t buf_len = os::lasterror(error_msg_buffer, sizeof(error_msg_buffer));
    warning("Attempt to determine Windows version from kernel32.dll failed: %s", buf_len != 0 ? error_msg_buffer : "<unknown error>");
    return;
  }

  _major_version = HIWORD(file_info->dwProductVersionMS);
  _minor_version = LOWORD(file_info->dwProductVersionMS);
  _build_number  = HIWORD(file_info->dwProductVersionLS);
  _build_minor   = LOWORD(file_info->dwProductVersionLS);

  os::free(version_info);
}

bool os::win32::is_windows_11_or_greater() {
  if (IsWindowsServer()) {
    return false;
  }

  // Windows 11 starts at build 22000 (Version 21H2)
  return (windows_major_version() == 10 && windows_build_number() >= 22000) || (windows_major_version() > 10);
}

bool os::win32::is_windows_server_2022_or_greater() {
  if (!IsWindowsServer()) {
    return false;
  }

  // Windows Server 2022 starts at build 20348.169
  return (windows_major_version() == 10 && windows_build_number() >= 20348) || (windows_major_version() > 10);
}

DWORD os::win32::active_processors_in_job_object(DWORD* active_processor_groups) {
  if (active_processor_groups != nullptr) {
    *active_processor_groups = 0;
  }
  BOOL is_in_job_object = false;
  if (IsProcessInJob(GetCurrentProcess(), nullptr, &is_in_job_object) == 0) {
    char buf[512];
    size_t buf_len = os::lasterror(buf, sizeof(buf));
    warning("Attempt to determine whether the process is running in a job failed: %s", buf_len != 0 ? buf : "<unknown error>");
    return 0;
  }

  if (!is_in_job_object) {
    return 0;
  }

  DWORD processors = 0;

  LPVOID job_object_information = nullptr;
  DWORD job_object_information_length = 0;

  if (QueryInformationJobObject(nullptr, JobObjectGroupInformationEx, nullptr, 0, &job_object_information_length) != 0) {
    warning("Unexpected QueryInformationJobObject success result.");
    assert(false, "Unexpected QueryInformationJobObject success result");
    return 0;
  }

  DWORD last_error = GetLastError();
  if (last_error == ERROR_INSUFFICIENT_BUFFER) {
    DWORD group_count = job_object_information_length / sizeof(GROUP_AFFINITY);

    job_object_information = os::malloc(job_object_information_length, mtInternal);
    if (job_object_information != nullptr) {
        if (QueryInformationJobObject(nullptr, JobObjectGroupInformationEx, job_object_information, job_object_information_length, &job_object_information_length) != 0) {
          DWORD groups_found = job_object_information_length / sizeof(GROUP_AFFINITY);
          if (groups_found != group_count) {
            warning("Unexpected processor group count: %ld. Expected %ld processor groups.", groups_found, group_count);
            assert(false, "Unexpected group count");
          }

          GROUP_AFFINITY* group_affinity_data = ((GROUP_AFFINITY*)job_object_information);
          for (DWORD i = 0; i < groups_found; i++, group_affinity_data++) {
            DWORD processors_in_group = population_count(group_affinity_data->Mask);
            processors += processors_in_group;
            if (active_processor_groups != nullptr && processors_in_group > 0) {
              (*active_processor_groups)++;
            }
          }

          if (processors == 0) {
            warning("Could not determine processor count from the job object.");
            assert(false, "Must find at least 1 logical processor");
          }
        } else {
          char buf[512];
          size_t buf_len = os::lasterror(buf, sizeof(buf));
          warning("Attempt to query job object information failed: %s", buf_len != 0 ? buf : "<unknown error>");
        }

        os::free(job_object_information);
    } else {
        warning("os::malloc() failed to allocate %ld bytes for QueryInformationJobObject", job_object_information_length);
    }
  } else {
    char buf[512];
    size_t buf_len = os::lasterror(buf, sizeof(buf));
    warning("Attempt to query job object information failed: %s", buf_len != 0 ? buf : "<unknown error>");
    assert(false, "Unexpected QueryInformationJobObject error code");
    return 0;
  }

  log_debug(os)("Process is running in a job with %d active processors.", processors);
  return processors;
}

void os::win32::initialize_system_info() {
  SYSTEM_INFO si;
  GetSystemInfo(&si);
  OSInfo::set_vm_page_size(si.dwPageSize);
  OSInfo::set_vm_allocation_granularity(si.dwAllocationGranularity);
  _processor_type  = si.dwProcessorType;
  _processor_level = si.wProcessorLevel;

  DWORD processors = 0;
  bool schedules_all_processor_groups = win32::is_windows_11_or_greater() || win32::is_windows_server_2022_or_greater();
  if (schedules_all_processor_groups) {
    processors = GetActiveProcessorCount(ALL_PROCESSOR_GROUPS);
    if (processors == 0) {
      char buf[512];
      size_t buf_len = os::lasterror(buf, sizeof(buf));
      warning("Attempt to determine the processor count from GetActiveProcessorCount() failed: %s", buf_len != 0 ? buf : "<unknown error>");
      assert(false, "Must find at least 1 logical processor");
    }
  }

  set_processor_count(processors > 0 ? processors : si.dwNumberOfProcessors);

  MEMORYSTATUSEX ms;
  ms.dwLength = sizeof(ms);

  // also returns dwAvailPhys (free physical memory bytes), dwTotalVirtual, dwAvailVirtual,
  // dwMemoryLoad (% of memory in use)
  GlobalMemoryStatusEx(&ms);
  _physical_memory = ms.ullTotalPhys;

  if (FLAG_IS_DEFAULT(MaxRAM)) {
    // Adjust MaxRAM according to the maximum virtual address space available.
    FLAG_SET_DEFAULT(MaxRAM, MIN2(MaxRAM, (uint64_t) ms.ullTotalVirtual));
  }

  _is_windows_server = IsWindowsServer();

  initialize_performance_counter();
}


HINSTANCE os::win32::load_Windows_dll(const char* name, char *ebuf,
                                      int ebuflen) {
  char path[MAX_PATH];
  DWORD size;
  DWORD pathLen = (DWORD)sizeof(path);
  HINSTANCE result = nullptr;

  // only allow library name without path component
  assert(strchr(name, '\\') == nullptr, "path not allowed");
  assert(strchr(name, ':') == nullptr, "path not allowed");
  if (strchr(name, '\\') != nullptr || strchr(name, ':') != nullptr) {
    jio_snprintf(ebuf, ebuflen,
                 "Invalid parameter while calling os::win32::load_windows_dll(): cannot take path: %s", name);
    return nullptr;
  }

  // search system directory
  if ((size = GetSystemDirectory(path, pathLen)) > 0) {
    if (size >= pathLen) {
      return nullptr; // truncated
    }
    if (jio_snprintf(path + size, pathLen - size, "\\%s", name) == -1) {
      return nullptr; // truncated
    }
    if ((result = (HINSTANCE)os::dll_load(path, ebuf, ebuflen)) != nullptr) {
      return result;
    }
  }

  // try Windows directory
  if ((size = GetWindowsDirectory(path, pathLen)) > 0) {
    if (size >= pathLen) {
      return nullptr; // truncated
    }
    if (jio_snprintf(path + size, pathLen - size, "\\%s", name) == -1) {
      return nullptr; // truncated
    }
    if ((result = (HINSTANCE)os::dll_load(path, ebuf, ebuflen)) != nullptr) {
      return result;
    }
  }

  jio_snprintf(ebuf, ebuflen,
               "os::win32::load_windows_dll() cannot load %s from system directories.", name);
  return nullptr;
}

#define MAXIMUM_THREADS_TO_KEEP (16 * MAXIMUM_WAIT_OBJECTS)
#define EXIT_TIMEOUT 300000 /* 5 minutes */

static BOOL CALLBACK init_crit_sect_call(PINIT_ONCE, PVOID pcrit_sect, PVOID*) {
  InitializeCriticalSection((CRITICAL_SECTION*)pcrit_sect);
  return TRUE;
}

static void exit_process_or_thread(Ept what, int exit_code) {
  // Basic approach:
  //  - Each exiting thread registers its intent to exit and then does so.
  //  - A thread trying to terminate the process must wait for all
  //    threads currently exiting to complete their exit.

  if (os::win32::has_exit_bug()) {
    // The array holds handles of the threads that have started exiting by calling
    // _endthreadex().
    // Should be large enough to avoid blocking the exiting thread due to lack of
    // a free slot.
    static HANDLE handles[MAXIMUM_THREADS_TO_KEEP];
    static int handle_count = 0;

    static INIT_ONCE init_once_crit_sect = INIT_ONCE_STATIC_INIT;
    static CRITICAL_SECTION crit_sect;
    static volatile DWORD process_exiting = 0;
    int i, j;
    DWORD res;
    HANDLE hproc, hthr;

    // We only attempt to register threads until a process exiting
    // thread manages to set the process_exiting flag. Any threads
    // that come through here after the process_exiting flag is set
    // are unregistered and will be caught in the SuspendThread()
    // infinite loop below.
    bool registered = false;

    // The first thread that reached this point, initializes the critical section.
    if (!InitOnceExecuteOnce(&init_once_crit_sect, init_crit_sect_call, &crit_sect, nullptr)) {
      warning("crit_sect initialization failed in %s: %d\n", __FILE__, __LINE__);
    } else if (Atomic::load_acquire(&process_exiting) == 0) {
      if (what != EPT_THREAD) {
        // Atomically set process_exiting before the critical section
        // to increase the visibility between racing threads.
        Atomic::cmpxchg(&process_exiting, (DWORD)0, GetCurrentThreadId());
      }
      EnterCriticalSection(&crit_sect);

      if (what == EPT_THREAD && Atomic::load_acquire(&process_exiting) == 0) {
        // Remove from the array those handles of the threads that have completed exiting.
        for (i = 0, j = 0; i < handle_count; ++i) {
          res = WaitForSingleObject(handles[i], 0 /* don't wait */);
          if (res == WAIT_TIMEOUT) {
            handles[j++] = handles[i];
          } else {
            if (res == WAIT_FAILED) {
              warning("WaitForSingleObject failed (%u) in %s: %d\n",
                      GetLastError(), __FILE__, __LINE__);
            }
            // Don't keep the handle, if we failed waiting for it.
            CloseHandle(handles[i]);
          }
        }

        // If there's no free slot in the array of the kept handles, we'll have to
        // wait until at least one thread completes exiting.
        if ((handle_count = j) == MAXIMUM_THREADS_TO_KEEP) {
          // Raise the priority of the oldest exiting thread to increase its chances
          // to complete sooner.
          SetThreadPriority(handles[0], THREAD_PRIORITY_ABOVE_NORMAL);
          res = WaitForMultipleObjects(MAXIMUM_WAIT_OBJECTS, handles, FALSE, EXIT_TIMEOUT);
          if (res >= WAIT_OBJECT_0 && res < (WAIT_OBJECT_0 + MAXIMUM_WAIT_OBJECTS)) {
            i = (res - WAIT_OBJECT_0);
            handle_count = MAXIMUM_THREADS_TO_KEEP - 1;
            for (; i < handle_count; ++i) {
              handles[i] = handles[i + 1];
            }
          } else {
            warning("WaitForMultipleObjects %s (%u) in %s: %d\n",
                    (res == WAIT_FAILED ? "failed" : "timed out"),
                    GetLastError(), __FILE__, __LINE__);
            // Don't keep handles, if we failed waiting for them.
            for (i = 0; i < MAXIMUM_THREADS_TO_KEEP; ++i) {
              CloseHandle(handles[i]);
            }
            handle_count = 0;
          }
        }

        // Store a duplicate of the current thread handle in the array of handles.
        hproc = GetCurrentProcess();
        hthr = GetCurrentThread();
        if (!DuplicateHandle(hproc, hthr, hproc, &handles[handle_count],
                             0, FALSE, DUPLICATE_SAME_ACCESS)) {
          warning("DuplicateHandle failed (%u) in %s: %d\n",
                  GetLastError(), __FILE__, __LINE__);

          // We can't register this thread (no more handles) so this thread
          // may be racing with a thread that is calling exit(). If the thread
          // that is calling exit() has managed to set the process_exiting
          // flag, then this thread will be caught in the SuspendThread()
          // infinite loop below which closes that race. A small timing
          // window remains before the process_exiting flag is set, but it
          // is only exposed when we are out of handles.
        } else {
          ++handle_count;
          registered = true;

          // The current exiting thread has stored its handle in the array, and now
          // should leave the critical section before calling _endthreadex().
        }

      } else if (what != EPT_THREAD && handle_count > 0) {
        jlong start_time, finish_time, timeout_left;
        // Before ending the process, make sure all the threads that had called
        // _endthreadex() completed.

        // Set the priority level of the current thread to the same value as
        // the priority level of exiting threads.
        // This is to ensure it will be given a fair chance to execute if
        // the timeout expires.
        hthr = GetCurrentThread();
        SetThreadPriority(hthr, THREAD_PRIORITY_ABOVE_NORMAL);
        start_time = os::javaTimeNanos();
        finish_time = start_time + ((jlong)EXIT_TIMEOUT * 1000000L);
        for (i = 0; ; ) {
          int portion_count = handle_count - i;
          if (portion_count > MAXIMUM_WAIT_OBJECTS) {
            portion_count = MAXIMUM_WAIT_OBJECTS;
          }
          for (j = 0; j < portion_count; ++j) {
            SetThreadPriority(handles[i + j], THREAD_PRIORITY_ABOVE_NORMAL);
          }
          timeout_left = (finish_time - start_time) / 1000000L;
          if (timeout_left < 0) {
            timeout_left = 0;
          }
          res = WaitForMultipleObjects(portion_count, handles + i, TRUE, timeout_left);
          if (res == WAIT_FAILED || res == WAIT_TIMEOUT) {
            warning("WaitForMultipleObjects %s (%u) in %s: %d\n",
                    (res == WAIT_FAILED ? "failed" : "timed out"),
                    GetLastError(), __FILE__, __LINE__);
            // Reset portion_count so we close the remaining
            // handles due to this error.
            portion_count = handle_count - i;
          }
          for (j = 0; j < portion_count; ++j) {
            CloseHandle(handles[i + j]);
          }
          if ((i += portion_count) >= handle_count) {
            break;
          }
          start_time = os::javaTimeNanos();
        }
        handle_count = 0;
      }

      LeaveCriticalSection(&crit_sect);
    }

    if (!registered &&
        Atomic::load_acquire(&process_exiting) != 0 &&
        process_exiting != GetCurrentThreadId()) {
      // Some other thread is about to call exit(), so we don't let
      // the current unregistered thread proceed to exit() or _endthreadex()
      while (true) {
        SuspendThread(GetCurrentThread());
        // Avoid busy-wait loop, if SuspendThread() failed.
        Sleep(EXIT_TIMEOUT);
      }
    }
  }

  // We are here if either
  // - there's no 'race at exit' bug on this OS release;
  // - initialization of the critical section failed (unlikely);
  // - the current thread has registered itself and left the critical section;
  // - the process-exiting thread has raised the flag and left the critical section.
  if (what == EPT_THREAD) {
    _endthreadex((unsigned)exit_code);
  } else if (what == EPT_PROCESS) {
    ALLOW_C_FUNCTION(::exit, ::exit(exit_code);)
  } else { // EPT_PROCESS_DIE
    ALLOW_C_FUNCTION(::_exit, ::_exit(exit_code);)
  }

  // Should not reach here
  ::abort();
}

#undef EXIT_TIMEOUT

void os::win32::setmode_streams() {
  _setmode(_fileno(stdin), _O_BINARY);
  _setmode(_fileno(stdout), _O_BINARY);
  _setmode(_fileno(stderr), _O_BINARY);
}

void os::wait_for_keypress_at_exit(void) {
  if (PauseAtExit) {
    fprintf(stderr, "Press any key to continue...\n");
    fgetc(stdin);
  }
}


bool os::message_box(const char* title, const char* message) {
  int result = MessageBox(nullptr, message, title,
                          MB_YESNO | MB_ICONERROR | MB_SYSTEMMODAL | MB_DEFAULT_DESKTOP_ONLY);
  return result == IDYES;
}

#ifndef PRODUCT
#ifndef _WIN64
// Helpers to check whether NX protection is enabled
int nx_exception_filter(_EXCEPTION_POINTERS *pex) {
  if (pex->ExceptionRecord->ExceptionCode == EXCEPTION_ACCESS_VIOLATION &&
      pex->ExceptionRecord->NumberParameters > 0 &&
      pex->ExceptionRecord->ExceptionInformation[0] ==
      EXCEPTION_INFO_EXEC_VIOLATION) {
    return EXCEPTION_EXECUTE_HANDLER;
  }
  return EXCEPTION_CONTINUE_SEARCH;
}

void nx_check_protection() {
  // If NX is enabled we'll get an exception calling into code on the stack
  char code[] = { (char)0xC3 }; // ret
  void *code_ptr = (void *)code;
  __try {
    __asm call code_ptr
  } __except(nx_exception_filter((_EXCEPTION_POINTERS*)_exception_info())) {
    tty->print_raw_cr("NX protection detected.");
  }
}
#endif // _WIN64
#endif // PRODUCT

// This is called _before_ the global arguments have been parsed
void os::init(void) {
  _initial_pid = _getpid();

  win32::initialize_windows_version();
  win32::initialize_system_info();
  win32::setmode_streams();
  _page_sizes.add(os::vm_page_size());

  // This may be overridden later when argument processing is done.
  FLAG_SET_ERGO(UseLargePagesIndividualAllocation, false);

  // Initialize main_process and main_thread
  main_process = GetCurrentProcess();  // Remember main_process is a pseudo handle
  if (!DuplicateHandle(main_process, GetCurrentThread(), main_process,
                       &main_thread, THREAD_ALL_ACCESS, false, 0)) {
    fatal("DuplicateHandle failed\n");
  }
  main_thread_id = (int) GetCurrentThreadId();

  // initialize fast thread access - only used for 32-bit
  win32::initialize_thread_ptr_offset();
}

// To install functions for atexit processing
extern "C" {
  static void perfMemory_exit_helper() {
    perfMemory_exit();
  }
}

static jint initSock();

// Minimum usable stack sizes required to get to user code. Space for
// HotSpot guard pages is added later.
size_t os::_compiler_thread_min_stack_allowed = 48 * K;
size_t os::_java_thread_min_stack_allowed = 40 * K;
#ifdef _LP64
size_t os::_vm_internal_thread_min_stack_allowed = 64 * K;
#else
size_t os::_vm_internal_thread_min_stack_allowed = (48 DEBUG_ONLY(+ 4)) * K;
#endif // _LP64

// If stack_commit_size is 0, windows will reserve the default size,
// but only commit a small portion of it.  This stack size is the size of this
// current thread but is larger than we need for Java threads.
// If -Xss is given to the launcher, it will pick 64K as default stack size and pass that.
size_t os::_os_min_stack_allowed = 64 * K;

// this is called _after_ the global arguments have been parsed
jint os::init_2(void) {
  const char* auto_schedules_message = "Host Windows OS automatically schedules threads across all processor groups.";
  const char* no_auto_schedules_message = "Host Windows OS does not automatically schedule threads across all processor groups.";

  bool schedules_all_processor_groups = win32::is_windows_11_or_greater() || win32::is_windows_server_2022_or_greater();
  log_debug(os)(schedules_all_processor_groups ? auto_schedules_message : no_auto_schedules_message);
  log_debug(os)("%d logical processors found.", processor_count());

  // This could be set any time but all platforms
  // have to set it the same so we have to mirror Solaris.
  DEBUG_ONLY(os::set_mutex_init_done();)

  // Setup Windows Exceptions

#if defined(USE_VECTORED_EXCEPTION_HANDLING)
  topLevelVectoredExceptionHandler = AddVectoredExceptionHandler(1, topLevelVectoredExceptionFilter);
  previousUnhandledExceptionFilter = SetUnhandledExceptionFilter(topLevelUnhandledExceptionFilter);
#endif

  // for debugging float code generation bugs
#if defined(ASSERT) && !defined(_WIN64)
  static long fp_control_word = 0;
  __asm { fstcw fp_control_word }
  // see Intel PPro Manual, Vol. 2, p 7-16
  const long invalid   = 0x01;
  fp_control_word |= invalid;
  __asm { fldcw fp_control_word }
#endif

  // Check and sets minimum stack sizes against command line options
  if (set_minimum_stack_sizes() == JNI_ERR) {
    return JNI_ERR;
  }

  // at exit methods are called in the reverse order of their registration.
  // there is no limit to the number of functions registered. atexit does
  // not set errno.

  if (PerfAllowAtExitRegistration) {
    // only register atexit functions if PerfAllowAtExitRegistration is set.
    // atexit functions can be delayed until process exit time, which
    // can be problematic for embedded VM situations. Embedded VMs should
    // call DestroyJavaVM() to assure that VM resources are released.

    // note: perfMemory_exit_helper atexit function may be removed in
    // the future if the appropriate cleanup code can be added to the
    // VM_Exit VMOperation's doit method.
    if (atexit(perfMemory_exit_helper) != 0) {
      warning("os::init_2 atexit(perfMemory_exit_helper) failed");
    }
  }

#ifndef _WIN64
  // Print something if NX is enabled (win32 on AMD64)
  NOT_PRODUCT(if (PrintMiscellaneous && Verbose) nx_check_protection());
#endif

  // initialize thread priority policy
  prio_init();

  UseNUMA = false; // We don't fully support this yet

  if (UseNUMAInterleaving || (UseNUMA && FLAG_IS_DEFAULT(UseNUMAInterleaving))) {
    if (!numa_interleaving_init()) {
      FLAG_SET_ERGO(UseNUMAInterleaving, false);
    } else if (!UseNUMAInterleaving) {
      // When NUMA requested, not-NUMA-aware allocations default to interleaving.
      FLAG_SET_ERGO(UseNUMAInterleaving, true);
    }
  }

  if (initSock() != JNI_OK) {
    return JNI_ERR;
  }

  SymbolEngine::recalc_search_path();

  // Initialize data for jdk.internal.misc.Signal, and install CTRL-C and
  // CTRL-BREAK handlers.
  if (!ReduceSignalUsage) {
    jdk_misc_signal_init();
  }

  // Lookup SetThreadDescription - the docs state we must use runtime-linking of
  // kernelbase.dll, so that is what we do.
  HINSTANCE _kernelbase = LoadLibrary(TEXT("kernelbase.dll"));
  if (_kernelbase != nullptr) {
    _SetThreadDescription =
      reinterpret_cast<SetThreadDescriptionFnPtr>(
                                                  GetProcAddress(_kernelbase,
                                                                 "SetThreadDescription"));
#ifdef ASSERT
    _GetThreadDescription =
      reinterpret_cast<GetThreadDescriptionFnPtr>(
                                                  GetProcAddress(_kernelbase,
                                                                 "GetThreadDescription"));
#endif
  }
  log_info(os, thread)("The SetThreadDescription API is%s available.", _SetThreadDescription == nullptr ? " not" : "");


  return JNI_OK;
}

// combine the high and low DWORD into a ULONGLONG
static ULONGLONG make_double_word(DWORD high_word, DWORD low_word) {
  ULONGLONG value = high_word;
  value <<= sizeof(high_word) * 8;
  value |= low_word;
  return value;
}

// Transfers data from WIN32_FILE_ATTRIBUTE_DATA structure to struct stat
static void file_attribute_data_to_stat(struct stat* sbuf, WIN32_FILE_ATTRIBUTE_DATA file_data) {
  ::memset((void*)sbuf, 0, sizeof(struct stat));
  sbuf->st_size = (_off_t)make_double_word(file_data.nFileSizeHigh, file_data.nFileSizeLow);
  sbuf->st_mtime = make_double_word(file_data.ftLastWriteTime.dwHighDateTime,
                                  file_data.ftLastWriteTime.dwLowDateTime);
  sbuf->st_ctime = make_double_word(file_data.ftCreationTime.dwHighDateTime,
                                  file_data.ftCreationTime.dwLowDateTime);
  sbuf->st_atime = make_double_word(file_data.ftLastAccessTime.dwHighDateTime,
                                  file_data.ftLastAccessTime.dwLowDateTime);
  if ((file_data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) != 0) {
    sbuf->st_mode |= S_IFDIR;
  } else {
    sbuf->st_mode |= S_IFREG;
  }
}

static errno_t convert_to_unicode(char const* char_path, LPWSTR* unicode_path) {
  // Get required buffer size to convert to Unicode
  int unicode_path_len = MultiByteToWideChar(CP_ACP,
                                             MB_ERR_INVALID_CHARS,
                                             char_path, -1,
                                             nullptr, 0);
  if (unicode_path_len == 0) {
    return EINVAL;
  }

  *unicode_path = NEW_C_HEAP_ARRAY(WCHAR, unicode_path_len, mtInternal);

  int result = MultiByteToWideChar(CP_ACP,
                                   MB_ERR_INVALID_CHARS,
                                   char_path, -1,
                                   *unicode_path, unicode_path_len);
  assert(result == unicode_path_len, "length already checked above");

  return ERROR_SUCCESS;
}

static errno_t get_full_path(LPCWSTR unicode_path, LPWSTR* full_path) {
  // Get required buffer size to convert to full path. The return
  // value INCLUDES the terminating null character.
  DWORD full_path_len = GetFullPathNameW(unicode_path, 0, nullptr, nullptr);
  if (full_path_len == 0) {
    return EINVAL;
  }

  *full_path = NEW_C_HEAP_ARRAY(WCHAR, full_path_len, mtInternal);

  // When the buffer has sufficient size, the return value EXCLUDES the
  // terminating null character
  DWORD result = GetFullPathNameW(unicode_path, full_path_len, *full_path, nullptr);
  assert(result <= full_path_len, "length already checked above");

  return ERROR_SUCCESS;
}

static void set_path_prefix(char* buf, LPCWSTR* prefix, int* prefix_off, bool* needs_fullpath) {
  *prefix_off = 0;
  *needs_fullpath = true;

  if (::isalpha(buf[0]) && !::IsDBCSLeadByte(buf[0]) && buf[1] == ':' && buf[2] == '\\') {
    *prefix = L"\\\\?\\";
  } else if (buf[0] == '\\' && buf[1] == '\\') {
    if (buf[2] == '?' && buf[3] == '\\') {
      *prefix = L"";
      *needs_fullpath = false;
    } else {
      *prefix = L"\\\\?\\UNC";
      *prefix_off = 1; // Overwrite the first char with the prefix, so \\share\path becomes \\?\UNC\share\path
    }
  } else {
    *prefix = L"\\\\?\\";
  }
}

// Returns the given path as an absolute wide path in unc format. The returned path is null
// on error (with err being set accordingly) and should be freed via os::free() otherwise.
// additional_space is the size of space, in wchar_t, the function will additionally add to
// the allocation of return buffer (such that the size of the returned buffer is at least
// wcslen(buf) + 1 + additional_space).
static wchar_t* wide_abs_unc_path(char const* path, errno_t & err, int additional_space = 0) {
  if ((path == nullptr) || (path[0] == '\0')) {
    err = ENOENT;
    return nullptr;
  }

  // Need to allocate at least room for 3 characters, since os::native_path transforms C: to C:.
  size_t buf_len = 1 + MAX2((size_t)3, strlen(path));
  char* buf = NEW_C_HEAP_ARRAY(char, buf_len, mtInternal);
  strncpy(buf, path, buf_len);
  os::native_path(buf);

  LPCWSTR prefix = nullptr;
  int prefix_off = 0;
  bool needs_fullpath = true;
  set_path_prefix(buf, &prefix, &prefix_off, &needs_fullpath);

  LPWSTR unicode_path = nullptr;
  err = convert_to_unicode(buf, &unicode_path);
  FREE_C_HEAP_ARRAY(char, buf);
  if (err != ERROR_SUCCESS) {
    return nullptr;
  }

  LPWSTR converted_path = nullptr;
  if (needs_fullpath) {
    err = get_full_path(unicode_path, &converted_path);
  } else {
    converted_path = unicode_path;
  }

  LPWSTR result = nullptr;
  if (converted_path != nullptr) {
    size_t prefix_len = wcslen(prefix);
    size_t result_len = prefix_len - prefix_off + wcslen(converted_path) + additional_space + 1;
    result = NEW_C_HEAP_ARRAY(WCHAR, result_len, mtInternal);
    _snwprintf(result, result_len, L"%s%s", prefix, &converted_path[prefix_off]);

    // Remove trailing pathsep (not for \\?\<DRIVE>:\, since it would make it relative)
    result_len = wcslen(result);
    if ((result[result_len - 1] == L'\\') &&
        !(::iswalpha(result[4]) && result[5] == L':' && result_len == 7)) {
      result[result_len - 1] = L'\0';
    }
  }

  if (converted_path != unicode_path) {
    FREE_C_HEAP_ARRAY(WCHAR, converted_path);
  }
  FREE_C_HEAP_ARRAY(WCHAR, unicode_path);

  return static_cast<wchar_t*>(result); // LPWSTR and wchat_t* are the same type on Windows.
}

int os::stat(const char *path, struct stat *sbuf) {
  errno_t err;
  wchar_t* wide_path = wide_abs_unc_path(path, err);

  if (wide_path == nullptr) {
    errno = err;
    return -1;
  }

  WIN32_FILE_ATTRIBUTE_DATA file_data;;
  BOOL bret = ::GetFileAttributesExW(wide_path, GetFileExInfoStandard, &file_data);
  os::free(wide_path);

  if (!bret) {
    errno = ::GetLastError();
    return -1;
  }

  file_attribute_data_to_stat(sbuf, file_data);
  return 0;
}

static HANDLE create_read_only_file_handle(const char* file) {
  errno_t err;
  wchar_t* wide_path = wide_abs_unc_path(file, err);

  if (wide_path == nullptr) {
    errno = err;
    return INVALID_HANDLE_VALUE;
  }

  HANDLE handle = ::CreateFileW(wide_path, 0, FILE_SHARE_READ,
                                nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
  os::free(wide_path);

  return handle;
}

bool os::same_files(const char* file1, const char* file2) {

  if (file1 == nullptr && file2 == nullptr) {
    return true;
  }

  if (file1 == nullptr || file2 == nullptr) {
    return false;
  }

  if (strcmp(file1, file2) == 0) {
    return true;
  }

  char* native_file1 = os::strdup_check_oom(file1);
  native_file1 = os::native_path(native_file1);
  char* native_file2 = os::strdup_check_oom(file2);
  native_file2 = os::native_path(native_file2);
  if (strcmp(native_file1, native_file2) == 0) {
    os::free(native_file1);
    os::free(native_file2);
    return true;
  }

  HANDLE handle1 = create_read_only_file_handle(native_file1);
  HANDLE handle2 = create_read_only_file_handle(native_file2);
  bool result = false;

  // if we could open both paths...
  if (handle1 != INVALID_HANDLE_VALUE && handle2 != INVALID_HANDLE_VALUE) {
    BY_HANDLE_FILE_INFORMATION fileInfo1;
    BY_HANDLE_FILE_INFORMATION fileInfo2;
    if (::GetFileInformationByHandle(handle1, &fileInfo1) &&
      ::GetFileInformationByHandle(handle2, &fileInfo2)) {
      // the paths are the same if they refer to the same file (fileindex) on the same volume (volume serial number)
      if (fileInfo1.dwVolumeSerialNumber == fileInfo2.dwVolumeSerialNumber &&
        fileInfo1.nFileIndexHigh == fileInfo2.nFileIndexHigh &&
        fileInfo1.nFileIndexLow == fileInfo2.nFileIndexLow) {
        result = true;
      }
    }
  }

  //free the handles
  if (handle1 != INVALID_HANDLE_VALUE) {
    ::CloseHandle(handle1);
  }

  if (handle2 != INVALID_HANDLE_VALUE) {
    ::CloseHandle(handle2);
  }

  os::free(native_file1);
  os::free(native_file2);

  return result;
}

#define FT2INT64(ft) \
  ((jlong)((jlong)(ft).dwHighDateTime << 32 | (julong)(ft).dwLowDateTime))


// current_thread_cpu_time(bool) and thread_cpu_time(Thread*, bool)
// are used by JVM M&M and JVMTI to get user+sys or user CPU time
// of a thread.
//
// current_thread_cpu_time() and thread_cpu_time(Thread*) returns
// the fast estimate available on the platform.

// current_thread_cpu_time() is not optimized for Windows yet
jlong os::current_thread_cpu_time() {
  // return user + sys since the cost is the same
  return os::thread_cpu_time(Thread::current(), true /* user+sys */);
}

jlong os::thread_cpu_time(Thread* thread) {
  // consistent with what current_thread_cpu_time() returns.
  return os::thread_cpu_time(thread, true /* user+sys */);
}

jlong os::current_thread_cpu_time(bool user_sys_cpu_time) {
  return os::thread_cpu_time(Thread::current(), user_sys_cpu_time);
}

jlong os::thread_cpu_time(Thread* thread, bool user_sys_cpu_time) {
  // This code is copy from classic VM -> hpi::sysThreadCPUTime
  // If this function changes, os::is_thread_cpu_time_supported() should too
  FILETIME CreationTime;
  FILETIME ExitTime;
  FILETIME KernelTime;
  FILETIME UserTime;

  if (GetThreadTimes(thread->osthread()->thread_handle(), &CreationTime,
                      &ExitTime, &KernelTime, &UserTime) == 0) {
    return -1;
  } else if (user_sys_cpu_time) {
    return (FT2INT64(UserTime) + FT2INT64(KernelTime)) * 100;
  } else {
    return FT2INT64(UserTime) * 100;
  }
}

void os::current_thread_cpu_time_info(jvmtiTimerInfo *info_ptr) {
  info_ptr->max_value = ALL_64_BITS;        // the max value -- all 64 bits
  info_ptr->may_skip_backward = false;      // GetThreadTimes returns absolute time
  info_ptr->may_skip_forward = false;       // GetThreadTimes returns absolute time
  info_ptr->kind = JVMTI_TIMER_TOTAL_CPU;   // user+system time is returned
}

void os::thread_cpu_time_info(jvmtiTimerInfo *info_ptr) {
  info_ptr->max_value = ALL_64_BITS;        // the max value -- all 64 bits
  info_ptr->may_skip_backward = false;      // GetThreadTimes returns absolute time
  info_ptr->may_skip_forward = false;       // GetThreadTimes returns absolute time
  info_ptr->kind = JVMTI_TIMER_TOTAL_CPU;   // user+system time is returned
}

bool os::is_thread_cpu_time_supported() {
  // see os::thread_cpu_time
  FILETIME CreationTime;
  FILETIME ExitTime;
  FILETIME KernelTime;
  FILETIME UserTime;

  if (GetThreadTimes(GetCurrentThread(), &CreationTime, &ExitTime,
                      &KernelTime, &UserTime) == 0) {
    return false;
  } else {
    return true;
  }
}

// Windows doesn't provide a loadavg primitive so this is stubbed out for now.
// It does have primitives (PDH API) to get CPU usage and run queue length.
// "\\Processor(_Total)\\% Processor Time", "\\System\\Processor Queue Length"
// If we wanted to implement loadavg on Windows, we have a few options:
//
// a) Query CPU usage and run queue length and "fake" an answer by
//    returning the CPU usage if it's under 100%, and the run queue
//    length otherwise.  It turns out that querying is pretty slow
//    on Windows, on the order of 200 microseconds on a fast machine.
//    Note that on the Windows the CPU usage value is the % usage
//    since the last time the API was called (and the first call
//    returns 100%), so we'd have to deal with that as well.
//
// b) Sample the "fake" answer using a sampling thread and store
//    the answer in a global variable.  The call to loadavg would
//    just return the value of the global, avoiding the slow query.
//
// c) Sample a better answer using exponential decay to smooth the
//    value.  This is basically the algorithm used by UNIX kernels.
//
// Note that sampling thread starvation could affect both (b) and (c).
int os::loadavg(double loadavg[], int nelem) {
  return -1;
}


// DontYieldALot=false by default: dutifully perform all yields as requested by JVM_Yield()
bool os::dont_yield() {
  return DontYieldALot;
}

int os::open(const char *path, int oflag, int mode) {
  errno_t err;
  wchar_t* wide_path = wide_abs_unc_path(path, err);

  if (wide_path == nullptr) {
    errno = err;
    return -1;
  }
  int fd = ::_wopen(wide_path, oflag | O_BINARY | O_NOINHERIT, mode);
  os::free(wide_path);

  if (fd == -1) {
    errno = ::GetLastError();
  }

  return fd;
}

FILE* os::fdopen(int fd, const char* mode) {
  return ::_fdopen(fd, mode);
}

ssize_t os::pd_write(int fd, const void *buf, size_t nBytes) {
  ssize_t original_len = (ssize_t)nBytes;
  while (nBytes > 0) {
    unsigned int len = nBytes > INT_MAX ? INT_MAX : (unsigned int)nBytes;
    // On Windows, ::write takes 'unsigned int' no of bytes, so nBytes should be split if larger.
    ssize_t written_bytes = ::write(fd, buf, len);
    if (written_bytes < 0) {
      return OS_ERR;
    }
    nBytes -= written_bytes;
    buf = (char *)buf + written_bytes;
  }
  return original_len;
}

void os::exit(int num) {
  exit_process_or_thread(EPT_PROCESS, num);
}

void os::_exit(int num) {
  exit_process_or_thread(EPT_PROCESS_DIE, num);
}

// Is a (classpath) directory empty?
bool os::dir_is_empty(const char* path) {
  errno_t err;
  wchar_t* wide_path = wide_abs_unc_path(path, err, 2);

  if (wide_path == nullptr) {
    errno = err;
    return false;
  }

  // Make sure we end with "\\*"
  if (wide_path[wcslen(wide_path) - 1] == L'\\') {
    wcscat(wide_path, L"*");
  } else {
    wcscat(wide_path, L"\\*");
  }

  WIN32_FIND_DATAW fd;
  HANDLE f = ::FindFirstFileW(wide_path, &fd);
  os::free(wide_path);
  bool is_empty = true;

  if (f != INVALID_HANDLE_VALUE) {
    while (is_empty && ::FindNextFileW(f, &fd)) {
      // An empty directory contains only the current directory file
      // and the previous directory file.
      if ((wcscmp(fd.cFileName, L".") != 0) &&
          (wcscmp(fd.cFileName, L"..") != 0)) {
        is_empty = false;
      }
    }
    FindClose(f);
  } else {
    errno = ::GetLastError();
  }

  return is_empty;
}

// return current position of file pointer
jlong os::current_file_offset(int fd) {
  return (jlong)::_lseeki64(fd, (__int64)0L, SEEK_CUR);
}

// move file pointer to the specified offset
jlong os::seek_to_file_offset(int fd, jlong offset) {
  return (jlong)::_lseeki64(fd, (__int64)offset, SEEK_SET);
}


jlong os::lseek(int fd, jlong offset, int whence) {
  return (jlong) ::_lseeki64(fd, offset, whence);
}

ssize_t os::read_at(int fd, void *buf, unsigned int nBytes, jlong offset) {
  OVERLAPPED ov;
  DWORD nread;
  BOOL result;

  ZeroMemory(&ov, sizeof(ov));
  ov.Offset = (DWORD)offset;
  ov.OffsetHigh = (DWORD)(offset >> 32);

  HANDLE h = (HANDLE)::_get_osfhandle(fd);

  result = ReadFile(h, (LPVOID)buf, nBytes, &nread, &ov);

  return result ? nread : 0;
}


// This method is a slightly reworked copy of JDK's sysNativePath
// from src/windows/hpi/src/path_md.c

// Convert a pathname to native format.  On win32, this involves forcing all
// separators to be '\\' rather than '/' (both are legal inputs, but Win95
// sometimes rejects '/') and removing redundant separators.  The input path is
// assumed to have been converted into the character encoding used by the local
// system.  Because this might be a double-byte encoding, care is taken to
// treat double-byte lead characters correctly.
//
// This procedure modifies the given path in place, as the result is never
// longer than the original.  There is no error return; this operation always
// succeeds.
char * os::native_path(char *path) {
  char *src = path, *dst = path, *end = path;
  char *colon = nullptr;  // If a drive specifier is found, this will
                       // point to the colon following the drive letter

  // Assumption: '/', '\\', ':', and drive letters are never lead bytes
  assert(((!::IsDBCSLeadByte('/')) && (!::IsDBCSLeadByte('\\'))
          && (!::IsDBCSLeadByte(':'))), "Illegal lead byte");

  // Check for leading separators
#define isfilesep(c) ((c) == '/' || (c) == '\\')
  while (isfilesep(*src)) {
    src++;
  }

  if (::isalpha(*src) && !::IsDBCSLeadByte(*src) && src[1] == ':') {
    // Remove leading separators if followed by drive specifier.  This
    // hack is necessary to support file URLs containing drive
    // specifiers (e.g., "file://c:/path").  As a side effect,
    // "/c:/path" can be used as an alternative to "c:/path".
    *dst++ = *src++;
    colon = dst;
    *dst++ = ':';
    src++;
  } else {
    src = path;
    if (isfilesep(src[0]) && isfilesep(src[1])) {
      // UNC pathname: Retain first separator; leave src pointed at
      // second separator so that further separators will be collapsed
      // into the second separator.  The result will be a pathname
      // beginning with "\\\\" followed (most likely) by a host name.
      src = dst = path + 1;
      path[0] = '\\';     // Force first separator to '\\'
    }
  }

  end = dst;

  // Remove redundant separators from remainder of path, forcing all
  // separators to be '\\' rather than '/'. Also, single byte space
  // characters are removed from the end of the path because those
  // are not legal ending characters on this operating system.
  //
  while (*src != '\0') {
    if (isfilesep(*src)) {
      *dst++ = '\\'; src++;
      while (isfilesep(*src)) src++;
      if (*src == '\0') {
        // Check for trailing separator
        end = dst;
        if (colon == dst - 2) break;  // "z:\\"
        if (dst == path + 1) break;   // "\\"
        if (dst == path + 2 && isfilesep(path[0])) {
          // "\\\\" is not collapsed to "\\" because "\\\\" marks the
          // beginning of a UNC pathname.  Even though it is not, by
          // itself, a valid UNC pathname, we leave it as is in order
          // to be consistent with the path canonicalizer as well
          // as the win32 APIs, which treat this case as an invalid
          // UNC pathname rather than as an alias for the root
          // directory of the current drive.
          break;
        }
        end = --dst;  // Path does not denote a root directory, so
                      // remove trailing separator
        break;
      }
      end = dst;
    } else {
      if (::IsDBCSLeadByte(*src)) {  // Copy a double-byte character
        *dst++ = *src++;
        if (*src) *dst++ = *src++;
        end = dst;
      } else {  // Copy a single-byte character
        char c = *src++;
        *dst++ = c;
        // Space is not a legal ending character
        if (c != ' ') end = dst;
      }
    }
  }

  *end = '\0';

  // For "z:", add "." to work around a bug in the C runtime library
  if (colon == dst - 1) {
    path[2] = '.';
    path[3] = '\0';
  }

  return path;
}

// This code is a copy of JDK's sysSetLength
// from src/windows/hpi/src/sys_api_md.c

int os::ftruncate(int fd, jlong length) {
  HANDLE h = (HANDLE)::_get_osfhandle(fd);
  long high = (long)(length >> 32);
  DWORD ret;

  if (h == (HANDLE)(-1)) {
    return -1;
  }

  ret = ::SetFilePointer(h, (long)(length), &high, FILE_BEGIN);
  if ((ret == 0xFFFFFFFF) && (::GetLastError() != NO_ERROR)) {
    return -1;
  }

  if (::SetEndOfFile(h) == FALSE) {
    return -1;
  }

  return 0;
}

int os::get_fileno(FILE* fp) {
  return _fileno(fp);
}

void os::flockfile(FILE* fp) {
  _lock_file(fp);
}

void os::funlockfile(FILE* fp) {
  _unlock_file(fp);
}

// Map a block of memory.
char* os::pd_map_memory(int fd, const char* file_name, size_t file_offset,
                        char *addr, size_t bytes, bool read_only,
                        bool allow_exec) {

  errno_t err;
  wchar_t* wide_path = wide_abs_unc_path(file_name, err);

  if (wide_path == nullptr) {
    return nullptr;
  }

  HANDLE hFile;
  char* base;

  hFile = CreateFileW(wide_path, GENERIC_READ, FILE_SHARE_READ, nullptr,
                     OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
  if (hFile == INVALID_HANDLE_VALUE) {
    log_info(os)("CreateFileW() failed: GetLastError->%ld.", GetLastError());
    os::free(wide_path);
    return nullptr;
  }
  os::free(wide_path);

  if (allow_exec) {
    // CreateFileMapping/MapViewOfFileEx can't map executable memory
    // unless it comes from a PE image (which the shared archive is not.)
    // Even VirtualProtect refuses to give execute access to mapped memory
    // that was not previously executable.
    //
    // Instead, stick the executable region in anonymous memory.  Yuck.
    // Penalty is that ~4 pages will not be shareable - in the future
    // we might consider DLLizing the shared archive with a proper PE
    // header so that mapping executable + sharing is possible.

    base = (char*) virtualAlloc(addr, bytes, MEM_COMMIT | MEM_RESERVE,
                                PAGE_READWRITE);
    if (base == nullptr) {
      CloseHandle(hFile);
      return nullptr;
    }

    // Record virtual memory allocation
    MemTracker::record_virtual_memory_reserve_and_commit((address)addr, bytes, CALLER_PC);

    DWORD bytes_read;
    OVERLAPPED overlapped;
    overlapped.Offset = (DWORD)file_offset;
    overlapped.OffsetHigh = 0;
    overlapped.hEvent = nullptr;
    // ReadFile guarantees that if the return value is true, the requested
    // number of bytes were read before returning.
    bool res = ReadFile(hFile, base, (DWORD)bytes, &bytes_read, &overlapped) != 0;
    if (!res) {
      log_info(os)("ReadFile() failed: GetLastError->%ld.", GetLastError());
      release_memory(base, bytes);
      CloseHandle(hFile);
      return nullptr;
    }
  } else {
    HANDLE hMap = CreateFileMapping(hFile, nullptr, PAGE_WRITECOPY, 0, 0,
                                    nullptr /* file_name */);
    if (hMap == nullptr) {
      log_info(os)("CreateFileMapping() failed: GetLastError->%ld.", GetLastError());
      CloseHandle(hFile);
      return nullptr;
    }

    DWORD access = read_only ? FILE_MAP_READ : FILE_MAP_COPY;
    base = (char*)mapViewOfFileEx(hMap, access, 0, (DWORD)file_offset,
                                  (DWORD)bytes, addr);
    if (base == nullptr) {
      CloseHandle(hMap);
      CloseHandle(hFile);
      return nullptr;
    }

    if (CloseHandle(hMap) == 0) {
      log_info(os)("CloseHandle(hMap) failed: GetLastError->%ld.", GetLastError());
      CloseHandle(hFile);
      return base;
    }
  }

  if (allow_exec) {
    DWORD old_protect;
    DWORD exec_access = read_only ? PAGE_EXECUTE_READ : PAGE_EXECUTE_READWRITE;
    bool res = VirtualProtect(base, bytes, exec_access, &old_protect) != 0;

    if (!res) {
      log_info(os)("VirtualProtect() failed: GetLastError->%ld.", GetLastError());
      // Don't consider this a hard error, on IA32 even if the
      // VirtualProtect fails, we should still be able to execute
      CloseHandle(hFile);
      return base;
    }
  }

  if (CloseHandle(hFile) == 0) {
    log_info(os)("CloseHandle(hFile) failed: GetLastError->%ld.", GetLastError());
    return base;
  }

  return base;
}

// Unmap a block of memory.
// Returns true=success, otherwise false.

bool os::pd_unmap_memory(char* addr, size_t bytes) {
  MEMORY_BASIC_INFORMATION mem_info;
  if (VirtualQuery(addr, &mem_info, sizeof(mem_info)) == 0) {
    log_info(os)("VirtualQuery() failed: GetLastError->%ld.", GetLastError());
    return false;
  }

  // Executable memory was not mapped using CreateFileMapping/MapViewOfFileEx.
  // Instead, executable region was allocated using VirtualAlloc(). See
  // pd_map_memory() above.
  //
  // The following flags should match the 'exec_access' flags used for
  // VirtualProtect() in pd_map_memory().
  if (mem_info.Protect == PAGE_EXECUTE_READ ||
      mem_info.Protect == PAGE_EXECUTE_READWRITE) {
    return pd_release_memory(addr, bytes);
  }

  BOOL result = unmapViewOfFile(addr);
  if (result == 0) {
    return false;
  }
  return true;
}

class HighResolutionInterval : public CHeapObj<mtThread> {
  // The default timer resolution seems to be 10 milliseconds.
  // (Where is this written down?)
  // If someone wants to sleep for only a fraction of the default,
  // then we set the timer resolution down to 1 millisecond for
  // the duration of their interval.
  // We carefully set the resolution back, since otherwise we
  // seem to incur an overhead (3%?) that we don't need.
  // CONSIDER: if ms is small, say 3, then we should run with a high resolution time.
  // Buf if ms is large, say 500, or 503, we should avoid the call to timeBeginPeriod().
  // Alternatively, we could compute the relative error (503/500 = .6%) and only use
  // timeBeginPeriod() if the relative error exceeded some threshold.
  // timeBeginPeriod() has been linked to problems with clock drift on win32 systems and
  // to decreased efficiency related to increased timer "tick" rates.  We want to minimize
  // (a) calls to timeBeginPeriod() and timeEndPeriod() and (b) time spent with high
  // resolution timers running.
 private:
  jlong resolution;
 public:
  HighResolutionInterval(jlong ms) {
    resolution = ms % 10L;
    if (resolution != 0) {
      MMRESULT result = timeBeginPeriod(1L);
    }
  }
  ~HighResolutionInterval() {
    if (resolution != 0) {
      MMRESULT result = timeEndPeriod(1L);
    }
    resolution = 0L;
  }
};

// An Event wraps a win32 "CreateEvent" kernel handle.
//
// We have a number of choices regarding "CreateEvent" win32 handle leakage:
//
// 1:  When a thread dies return the Event to the EventFreeList, clear the ParkHandle
//     field, and call CloseHandle() on the win32 event handle.  Unpark() would
//     need to be modified to tolerate finding a null (invalid) win32 event handle.
//     In addition, an unpark() operation might fetch the handle field, but the
//     event could recycle between the fetch and the SetEvent() operation.
//     SetEvent() would either fail because the handle was invalid, or inadvertently work,
//     as the win32 handle value had been recycled.  In an ideal world calling SetEvent()
//     on an stale but recycled handle would be harmless, but in practice this might
//     confuse other non-Sun code, so it's not a viable approach.
//
// 2:  Once a win32 event handle is associated with an Event, it remains associated
//     with the Event.  The event handle is never closed.  This could be construed
//     as handle leakage, but only up to the maximum # of threads that have been extant
//     at any one time.  This shouldn't be an issue, as windows platforms typically
//     permit a process to have hundreds of thousands of open handles.
//
// 3:  Same as (1), but periodically, at stop-the-world time, rundown the EventFreeList
//     and release unused handles.
//
// 4:  Add a CRITICAL_SECTION to the Event to protect LD+SetEvent from LD;ST(null);CloseHandle.
//     It's not clear, however, that we wouldn't be trading one type of leak for another.
//
// 5.  Use an RCU-like mechanism (Read-Copy Update).
//     Or perhaps something similar to Maged Michael's "Hazard pointers".
//
// We use (2).
//
// TODO-FIXME:
// 1.  Reconcile Doug's JSR166 j.u.c park-unpark with the objectmonitor implementation.
// 2.  Consider wrapping the WaitForSingleObject(Ex) calls in SEH try/finally blocks
//     to recover from (or at least detect) the dreaded Windows 841176 bug.
// 3.  Collapse the JSR166 parker event, and the objectmonitor ParkEvent
//     into a single win32 CreateEvent() handle.
//
// Assumption:
//    Only one parker can exist on an event, which is why we allocate
//    them per-thread. Multiple unparkers can coexist.
//
// _Event transitions in park()
//   -1 => -1 : illegal
//    1 =>  0 : pass - return immediately
//    0 => -1 : block; then set _Event to 0 before returning
//
// _Event transitions in unpark()
//    0 => 1 : just return
//    1 => 1 : just return
//   -1 => either 0 or 1; must signal target thread
//         That is, we can safely transition _Event from -1 to either
//         0 or 1.
//
// _Event serves as a restricted-range semaphore.
//   -1 : thread is blocked, i.e. there is a waiter
//    0 : neutral: thread is running or ready,
//        could have been signaled after a wait started
//    1 : signaled - thread is running or ready
//
// Another possible encoding of _Event would be with
// explicit "PARKED" == 01b and "SIGNALED" == 10b bits.
//

int PlatformEvent::park_nanos(jlong nanos) {
  assert(nanos > 0, "nanos are positive");

  // Windows timers are still quite unpredictable to handle sub-millisecond granularity.
  // Instead of implementing sub-millisecond sleeps, fall back to the usual behavior of
  // rounding up any excess requested nanos to the full millisecond. This is how
  // Thread.sleep(millis, nanos) has always behaved with only millisecond granularity.
  jlong millis = nanos / NANOSECS_PER_MILLISEC;
  if (nanos > millis * NANOSECS_PER_MILLISEC) {
    millis++;
  }
  assert(millis > 0, "should always be positive");
  return park(millis);
}

int PlatformEvent::park(jlong Millis) {
  // Transitions for _Event:
  //   -1 => -1 : illegal
  //    1 =>  0 : pass - return immediately
  //    0 => -1 : block; then set _Event to 0 before returning

  guarantee(_ParkHandle != nullptr , "Invariant");
  guarantee(Millis > 0          , "Invariant");

  // CONSIDER: defer assigning a CreateEvent() handle to the Event until
  // the initial park() operation.
  // Consider: use atomic decrement instead of CAS-loop

  int v;
  for (;;) {
    v = _Event;
    if (Atomic::cmpxchg(&_Event, v, v-1) == v) break;
  }
  guarantee((v == 0) || (v == 1), "invariant");
  if (v != 0) return OS_OK;

  // Do this the hard way by blocking ...
  // TODO: consider a brief spin here, gated on the success of recent
  // spin attempts by this thread.
  //
  // We decompose long timeouts into series of shorter timed waits.
  // Evidently large timo values passed in WaitForSingleObject() are problematic on some
  // versions of Windows.  See EventWait() for details.  This may be superstition.  Or not.
  // We trust the WAIT_TIMEOUT indication and don't track the elapsed wait time
  // with os::javaTimeNanos().  Furthermore, we assume that spurious returns from
  // ::WaitForSingleObject() caused by latent ::setEvent() operations will tend
  // to happen early in the wait interval.  Specifically, after a spurious wakeup (rv ==
  // WAIT_OBJECT_0 but _Event is still < 0) we don't bother to recompute Millis to compensate
  // for the already waited time.  This policy does not admit any new outcomes.
  // In the future, however, we might want to track the accumulated wait time and
  // adjust Millis accordingly if we encounter a spurious wakeup.

  const int MAXTIMEOUT = 0x10000000;
  DWORD rv = WAIT_TIMEOUT;
  while (_Event < 0 && Millis > 0) {
    DWORD prd = Millis;     // set prd = MAX (Millis, MAXTIMEOUT)
    if (Millis > MAXTIMEOUT) {
      prd = MAXTIMEOUT;
    }
    HighResolutionInterval *phri = nullptr;
    if (!ForceTimeHighResolution) {
      phri = new HighResolutionInterval(prd);
    }
    rv = ::WaitForSingleObject(_ParkHandle, prd);
    assert(rv != WAIT_FAILED,   "WaitForSingleObject failed with error code: %lu", GetLastError());
    assert(rv == WAIT_OBJECT_0 || rv == WAIT_TIMEOUT, "WaitForSingleObject failed with return value: %lu", rv);
    if (rv == WAIT_TIMEOUT) {
      Millis -= prd;
    }
    delete phri; // if it is null, harmless
  }
  v = _Event;
  _Event = 0;
  // see comment at end of PlatformEvent::park() below:
  OrderAccess::fence();
  // If we encounter a nearly simultaneous timeout expiry and unpark()
  // we return OS_OK indicating we awoke via unpark().
  // Implementor's license -- returning OS_TIMEOUT would be equally valid, however.
  return (v >= 0) ? OS_OK : OS_TIMEOUT;
}

void PlatformEvent::park() {
  // Transitions for _Event:
  //   -1 => -1 : illegal
  //    1 =>  0 : pass - return immediately
  //    0 => -1 : block; then set _Event to 0 before returning

  guarantee(_ParkHandle != nullptr, "Invariant");
  // Invariant: Only the thread associated with the Event/PlatformEvent
  // may call park().
  // Consider: use atomic decrement instead of CAS-loop
  int v;
  for (;;) {
    v = _Event;
    if (Atomic::cmpxchg(&_Event, v, v-1) == v) break;
  }
  guarantee((v == 0) || (v == 1), "invariant");
  if (v != 0) return;

  // Do this the hard way by blocking ...
  // TODO: consider a brief spin here, gated on the success of recent
  // spin attempts by this thread.
  while (_Event < 0) {
    DWORD rv = ::WaitForSingleObject(_ParkHandle, INFINITE);
    assert(rv != WAIT_FAILED,   "WaitForSingleObject failed with error code: %lu", GetLastError());
    assert(rv == WAIT_OBJECT_0, "WaitForSingleObject failed with return value: %lu", rv);
  }

  // Usually we'll find _Event == 0 at this point, but as
  // an optional optimization we clear it, just in case can
  // multiple unpark() operations drove _Event up to 1.
  _Event = 0;
  OrderAccess::fence();
  guarantee(_Event >= 0, "invariant");
}

void PlatformEvent::unpark() {
  guarantee(_ParkHandle != nullptr, "Invariant");

  // Transitions for _Event:
  //    0 => 1 : just return
  //    1 => 1 : just return
  //   -1 => either 0 or 1; must signal target thread
  //         That is, we can safely transition _Event from -1 to either
  //         0 or 1.
  // See also: "Semaphores in Plan 9" by Mullender & Cox
  //
  // Note: Forcing a transition from "-1" to "1" on an unpark() means
  // that it will take two back-to-back park() calls for the owning
  // thread to block. This has the benefit of forcing a spurious return
  // from the first park() call after an unpark() call which will help
  // shake out uses of park() and unpark() without condition variables.

  if (Atomic::xchg(&_Event, 1) >= 0) return;

  ::SetEvent(_ParkHandle);
}


// JSR166
// -------------------------------------------------------

// The Windows implementation of Park is very straightforward: Basic
// operations on Win32 Events turn out to have the right semantics to
// use them directly.

void Parker::park(bool isAbsolute, jlong time) {
  guarantee(_ParkHandle != nullptr, "invariant");
  // First, demultiplex/decode time arguments
  if (time < 0) { // don't wait
    return;
  } else if (time == 0 && !isAbsolute) {
    time = INFINITE;
  } else if (isAbsolute) {
    time -= os::javaTimeMillis(); // convert to relative time
    if (time <= 0) {  // already elapsed
      return;
    }
  } else { // relative
    time /= 1000000;  // Must coarsen from nanos to millis
    if (time == 0) {  // Wait for the minimal time unit if zero
      time = 1;
    }
  }

  JavaThread* thread = JavaThread::current();

  // Don't wait if interrupted or already triggered
  if (thread->is_interrupted(false)) {
    ResetEvent(_ParkHandle);
    return;
  } else {
    DWORD rv = WaitForSingleObject(_ParkHandle, 0);
    assert(rv != WAIT_FAILED,   "WaitForSingleObject failed with error code: %lu", GetLastError());
    assert(rv == WAIT_OBJECT_0 || rv == WAIT_TIMEOUT, "WaitForSingleObject failed with return value: %lu", rv);
    if (rv == WAIT_OBJECT_0) {
      ResetEvent(_ParkHandle);
      return;
    } else {
      ThreadBlockInVM tbivm(thread);
      OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);

      rv = WaitForSingleObject(_ParkHandle, time);
      assert(rv != WAIT_FAILED,   "WaitForSingleObject failed with error code: %lu", GetLastError());
      assert(rv == WAIT_OBJECT_0 || rv == WAIT_TIMEOUT, "WaitForSingleObject failed with return value: %lu", rv);
      ResetEvent(_ParkHandle);
    }
  }
}

void Parker::unpark() {
  guarantee(_ParkHandle != nullptr, "invariant");
  SetEvent(_ParkHandle);
}

PlatformMutex::~PlatformMutex() {
  DeleteCriticalSection(&_mutex);
}

// Platform Monitor implementation

// Must already be locked
int PlatformMonitor::wait(uint64_t millis) {
  int ret = OS_TIMEOUT;
  // The timeout parameter for SleepConditionVariableCS is a DWORD
  if (millis > UINT_MAX) {
    millis = UINT_MAX;
  }
  int status = SleepConditionVariableCS(&_cond, &_mutex,
                                        millis == 0 ? INFINITE : (DWORD)millis);
  if (status != 0) {
    ret = OS_OK;
  }
  #ifndef PRODUCT
  else {
    DWORD err = GetLastError();
    assert(err == ERROR_TIMEOUT, "SleepConditionVariableCS: %ld:", err);
  }
  #endif
  return ret;
}

// Run the specified command in a separate process. Return its exit value,
// or -1 on failure (e.g. can't create a new process).
int os::fork_and_exec(const char* cmd) {
  STARTUPINFO si;
  PROCESS_INFORMATION pi;
  DWORD exit_code;

  char * cmd_string;
  const char * cmd_prefix = "cmd /C ";
  size_t len = strlen(cmd) + strlen(cmd_prefix) + 1;
  cmd_string = NEW_C_HEAP_ARRAY_RETURN_NULL(char, len, mtInternal);
  if (cmd_string == nullptr) {
    return -1;
  }
  cmd_string[0] = '\0';
  strcat(cmd_string, cmd_prefix);
  strcat(cmd_string, cmd);

  // now replace all '\n' with '&'
  char * substring = cmd_string;
  while ((substring = strchr(substring, '\n')) != nullptr) {
    substring[0] = '&';
    substring++;
  }
  memset(&si, 0, sizeof(si));
  si.cb = sizeof(si);
  memset(&pi, 0, sizeof(pi));
  BOOL rslt = CreateProcess(nullptr,   // executable name - use command line
                            cmd_string,    // command line
                            nullptr,   // process security attribute
                            nullptr,   // thread security attribute
                            TRUE,   // inherits system handles
                            0,      // no creation flags
                            nullptr,   // use parent's environment block
                            nullptr,   // use parent's starting directory
                            &si,    // (in) startup information
                            &pi);   // (out) process information

  if (rslt) {
    // Wait until child process exits.
    DWORD rv = WaitForSingleObject(pi.hProcess, INFINITE);
    assert(rv != WAIT_FAILED,   "WaitForSingleObject failed with error code: %lu", GetLastError());
    assert(rv == WAIT_OBJECT_0, "WaitForSingleObject failed with return value: %lu", rv);

    GetExitCodeProcess(pi.hProcess, &exit_code);

    // Close process and thread handles.
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
  } else {
    exit_code = -1;
  }

  FREE_C_HEAP_ARRAY(char, cmd_string);
  return (int)exit_code;
}

bool os::find(address addr, outputStream* st) {
  int offset = -1;
  bool result = false;
  char buf[256];
  if (os::dll_address_to_library_name(addr, buf, sizeof(buf), &offset)) {
    st->print(PTR_FORMAT " ", addr);
    if (strlen(buf) < sizeof(buf) - 1) {
      char* p = strrchr(buf, '\\');
      if (p) {
        st->print("%s", p + 1);
      } else {
        st->print("%s", buf);
      }
    } else {
        // The library name is probably truncated. Let's omit the library name.
        // See also JDK-8147512.
    }
    if (os::dll_address_to_function_name(addr, buf, sizeof(buf), &offset)) {
      st->print("::%s + 0x%x", buf, offset);
    }
    st->cr();
    result = true;
  }
  return result;
}

static jint initSock() {
  WSADATA wsadata;

  if (WSAStartup(MAKEWORD(2,2), &wsadata) != 0) {
    jio_fprintf(stderr, "Could not initialize Winsock (error: %d)\n",
                ::GetLastError());
    return JNI_ERR;
  }
  return JNI_OK;
}

int os::socket_close(int fd) {
  return ::closesocket(fd);
}

ssize_t os::connect(int fd, struct sockaddr* him, socklen_t len) {
  return ::connect(fd, him, len);
}

ssize_t os::recv(int fd, char* buf, size_t nBytes, uint flags) {
  return ::recv(fd, buf, (int)nBytes, flags);
}

ssize_t os::send(int fd, char* buf, size_t nBytes, uint flags) {
  return ::send(fd, buf, (int)nBytes, flags);
}

ssize_t os::raw_send(int fd, char* buf, size_t nBytes, uint flags) {
  return ::send(fd, buf, (int)nBytes, flags);
}

// WINDOWS CONTEXT Flags for THREAD_SAMPLING
#if defined(IA32)
  #define sampling_context_flags (CONTEXT_FULL | CONTEXT_FLOATING_POINT | CONTEXT_EXTENDED_REGISTERS)
#elif defined(AMD64) || defined(_M_ARM64)
  #define sampling_context_flags (CONTEXT_FULL | CONTEXT_FLOATING_POINT)
#endif

// returns true if thread could be suspended,
// false otherwise
static bool do_suspend(HANDLE* h) {
  if (h != nullptr) {
    if (SuspendThread(*h) != ~0) {
      return true;
    }
  }
  return false;
}

// resume the thread
// calling resume on an active thread is a no-op
static void do_resume(HANDLE* h) {
  if (h != nullptr) {
    ResumeThread(*h);
  }
}

// retrieve a suspend/resume context capable handle
// from the tid. Caller validates handle return value.
void get_thread_handle_for_extended_context(HANDLE* h,
                                            OSThread::thread_id_t tid) {
  if (h != nullptr) {
    *h = OpenThread(THREAD_SUSPEND_RESUME | THREAD_GET_CONTEXT | THREAD_QUERY_INFORMATION, FALSE, tid);
  }
}

// Thread sampling implementation
//
void SuspendedThreadTask::internal_do_task() {
  CONTEXT    ctxt;
  HANDLE     h = nullptr;

  // get context capable handle for thread
  get_thread_handle_for_extended_context(&h, _thread->osthread()->thread_id());

  // sanity
  if (h == nullptr || h == INVALID_HANDLE_VALUE) {
    return;
  }

  // suspend the thread
  if (do_suspend(&h)) {
    ctxt.ContextFlags = sampling_context_flags;
    // get thread context
    GetThreadContext(h, &ctxt);
    SuspendedThreadTaskContext context(_thread, &ctxt);
    // pass context to Thread Sampling impl
    do_task(context);
    // resume thread
    do_resume(&h);
  }

  // close handle
  CloseHandle(h);
}

bool os::start_debugging(char *buf, int buflen) {
  int len = (int)strlen(buf);
  char *p = &buf[len];

  jio_snprintf(p, buflen-len,
             "\n\n"
             "Do you want to debug the problem?\n\n"
             "To debug, attach Visual Studio to process %d; then switch to thread 0x%x\n"
             "Select 'Yes' to launch Visual Studio automatically (PATH must include msdev)\n"
             "Otherwise, select 'No' to abort...",
             os::current_process_id(), os::current_thread_id());

  bool yes = os::message_box("Unexpected Error", buf);

  if (yes) {
    // os::breakpoint() calls DebugBreak(), which causes a breakpoint
    // exception. If VM is running inside a debugger, the debugger will
    // catch the exception. Otherwise, the breakpoint exception will reach
    // the default windows exception handler, which can spawn a debugger and
    // automatically attach to the dying VM.
    os::breakpoint();
    yes = false;
  }
  return yes;
}

void* os::get_default_process_handle() {
  return (void*)GetModuleHandle(nullptr);
}

// Builds a platform dependent Agent_OnLoad_<lib_name> function name
// which is used to find statically linked in agents.
// Additionally for windows, takes into account __stdcall names.
// Parameters:
//            sym_name: Symbol in library we are looking for
//            lib_name: Name of library to look in, null for shared libs.
//            is_absolute_path == true if lib_name is absolute path to agent
//                                     such as "C:/a/b/L.dll"
//            == false if only the base name of the library is passed in
//               such as "L"
char* os::build_agent_function_name(const char *sym_name, const char *lib_name,
                                    bool is_absolute_path) {
  char *agent_entry_name;
  size_t len;
  size_t name_len;
  size_t prefix_len = strlen(JNI_LIB_PREFIX);
  size_t suffix_len = strlen(JNI_LIB_SUFFIX);
  const char *start;

  if (lib_name != nullptr) {
    len = name_len = strlen(lib_name);
    if (is_absolute_path) {
      // Need to strip path, prefix and suffix
      if ((start = strrchr(lib_name, *os::file_separator())) != nullptr) {
        lib_name = ++start;
      } else {
        // Need to check for drive prefix
        if ((start = strchr(lib_name, ':')) != nullptr) {
          lib_name = ++start;
        }
      }
      if (len <= (prefix_len + suffix_len)) {
        return nullptr;
      }
      lib_name += prefix_len;
      name_len = strlen(lib_name) - suffix_len;
    }
  }
  len = (lib_name != nullptr ? name_len : 0) + strlen(sym_name) + 2;
  agent_entry_name = NEW_C_HEAP_ARRAY_RETURN_NULL(char, len, mtThread);
  if (agent_entry_name == nullptr) {
    return nullptr;
  }
  if (lib_name != nullptr) {
    const char *p = strrchr(sym_name, '@');
    if (p != nullptr && p != sym_name) {
      // sym_name == _Agent_OnLoad@XX
      strncpy(agent_entry_name, sym_name, (p - sym_name));
      agent_entry_name[(p-sym_name)] = '\0';
      // agent_entry_name == _Agent_OnLoad
      strcat(agent_entry_name, "_");
      strncat(agent_entry_name, lib_name, name_len);
      strcat(agent_entry_name, p);
      // agent_entry_name == _Agent_OnLoad_lib_name@XX
    } else {
      strcpy(agent_entry_name, sym_name);
      strcat(agent_entry_name, "_");
      strncat(agent_entry_name, lib_name, name_len);
    }
  } else {
    strcpy(agent_entry_name, sym_name);
  }
  return agent_entry_name;
}

/*
  All the defined signal names for Windows.

  NOTE that not all of these names are accepted by FindSignal!

  For various reasons some of these may be rejected at runtime.

  Here are the names currently accepted by a user of sun.misc.Signal with
  1.4.1 (ignoring potential interaction with use of chaining, etc):

     (LIST TBD)

*/
int os::get_signal_number(const char* name) {
  static const struct {
    const char* name;
    int         number;
  } siglabels [] =
    // derived from version 6.0 VC98/include/signal.h
  {"ABRT",      SIGABRT,        // abnormal termination triggered by abort cl
  "FPE",        SIGFPE,         // floating point exception
  "SEGV",       SIGSEGV,        // segment violation
  "INT",        SIGINT,         // interrupt
  "TERM",       SIGTERM,        // software term signal from kill
  "BREAK",      SIGBREAK,       // Ctrl-Break sequence
  "ILL",        SIGILL};        // illegal instruction
  for (unsigned i = 0; i < ARRAY_SIZE(siglabels); ++i) {
    if (strcmp(name, siglabels[i].name) == 0) {
      return siglabels[i].number;
    }
  }
  return -1;
}

// Fast current thread access

int os::win32::_thread_ptr_offset = 0;

static void call_wrapper_dummy() {}

// We need to call the os_exception_wrapper once so that it sets
// up the offset from FS of the thread pointer.
void os::win32::initialize_thread_ptr_offset() {
  os::os_exception_wrapper((java_call_t)call_wrapper_dummy,
                           nullptr, methodHandle(), nullptr, nullptr);
}

bool os::supports_map_sync() {
  return false;
}

#ifdef ASSERT
static void check_meminfo(MEMORY_BASIC_INFORMATION* minfo) {
  assert(minfo->State == MEM_FREE || minfo->State == MEM_COMMIT || minfo->State == MEM_RESERVE, "Invalid state");
  if (minfo->State != MEM_FREE) {
    assert(minfo->AllocationBase != nullptr && minfo->BaseAddress >= minfo->AllocationBase, "Invalid pointers");
    assert(minfo->RegionSize > 0, "Invalid region size");
  }
}
#endif


static bool checkedVirtualQuery(address addr, MEMORY_BASIC_INFORMATION* minfo) {
  ZeroMemory(minfo, sizeof(MEMORY_BASIC_INFORMATION));
  if (::VirtualQuery(addr, minfo, sizeof(MEMORY_BASIC_INFORMATION)) == sizeof(MEMORY_BASIC_INFORMATION)) {
    DEBUG_ONLY(check_meminfo(minfo);)
    return true;
  }
  return false;
}

// Given a pointer pointing into an allocation (an area allocated with VirtualAlloc),
//  return information about that allocation.
bool os::win32::find_mapping(address addr, mapping_info_t* mi) {
  // Query at addr to find allocation base; then, starting at allocation base,
  //  query all regions, until we either find the next allocation or a free area.
  ZeroMemory(mi, sizeof(mapping_info_t));
  MEMORY_BASIC_INFORMATION minfo;
  address allocation_base = nullptr;
  address allocation_end = nullptr;
  bool rc = false;
  if (checkedVirtualQuery(addr, &minfo)) {
    if (minfo.State != MEM_FREE) {
      allocation_base = (address)minfo.AllocationBase;
      allocation_end = allocation_base;
      // Iterate through all regions in this allocation to find its end. While we are here, also count things.
      for (;;) {
        bool rc = checkedVirtualQuery(allocation_end, &minfo);
        if (rc == false ||                                       // VirtualQuery error, end of allocation?
           minfo.State == MEM_FREE ||                            // end of allocation, free memory follows
           (address)minfo.AllocationBase != allocation_base)     // end of allocation, a new one starts
        {
          break;
        }
        const size_t region_size = minfo.RegionSize;
        mi->regions ++;
        if (minfo.State == MEM_COMMIT) {
          mi->committed_size += minfo.RegionSize;
        }
        allocation_end += region_size;
      }
      if (allocation_base != nullptr && allocation_end > allocation_base) {
        mi->base = allocation_base;
        mi->size = allocation_end - allocation_base;
        rc = true;
      }
    }
  }
#ifdef ASSERT
  if (rc) {
    assert(mi->size > 0 && mi->size >= mi->committed_size, "Sanity");
    assert(addr >= mi->base && addr < mi->base + mi->size, "Sanity");
    assert(mi->regions > 0, "Sanity");
  }
#endif
  return rc;
}

// Helper for print_one_mapping: print n words, both as hex and ascii.
// Use Safefetch for all values.
static void print_snippet(const void* p, outputStream* st) {
  static const int num_words = LP64_ONLY(3) NOT_LP64(6);
  static const int num_bytes = num_words * sizeof(int);
  intptr_t v[num_words];
  const int errval = 0xDE210244;
  for (int i = 0; i < num_words; i++) {
    v[i] = SafeFetchN((intptr_t*)p + i, errval);
    if (v[i] == errval &&
        SafeFetchN((intptr_t*)p + i, ~errval) == ~errval) {
      return;
    }
  }
  st->put('[');
  for (int i = 0; i < num_words; i++) {
    st->print(INTPTR_FORMAT " ", v[i]);
  }
  const char* b = (char*)v;
  st->put('\"');
  for (int i = 0; i < num_bytes; i++) {
    st->put(::isgraph(b[i]) ? b[i] : '.');
  }
  st->put('\"');
  st->put(']');
}

// Helper function for print_memory_mappings:
//  Given a MEMORY_BASIC_INFORMATION, containing information about a non-free region:
//  print out all regions in that allocation. If any of those regions
//  fall outside the given range [start, end), indicate that in the output.
// Return the pointer to the end of the allocation.
static address print_one_mapping(MEMORY_BASIC_INFORMATION* minfo, address start, address end, outputStream* st) {
  // Print it like this:
  //
  // Base: <xxxxx>: [xxxx - xxxx], state=MEM_xxx, prot=x, type=MEM_xxx       (region 1)
  //                [xxxx - xxxx], state=MEM_xxx, prot=x, type=MEM_xxx       (region 2)
  assert(minfo->State != MEM_FREE, "Not inside an allocation.");
  address allocation_base = (address)minfo->AllocationBase;
  #define IS_IN(p) (p >= start && p < end)
  bool first_line = true;
  bool is_dll = false;
  for(;;) {
    if (first_line) {
      st->print("Base " PTR_FORMAT ": ", p2i(allocation_base));
    } else {
      st->print_raw(NOT_LP64 ("                 ")
                    LP64_ONLY("                         "));
    }
    address region_start = (address)minfo->BaseAddress;
    address region_end = region_start + minfo->RegionSize;
    assert(region_end > region_start, "Sanity");
    if (region_end <= start) {
      st->print("<outside range> ");
    } else if (region_start >= end) {
      st->print("<outside range> ");
    } else if (!IS_IN(region_start) || !IS_IN(region_end - 1)) {
      st->print("<partly outside range> ");
    }
    st->print("[" PTR_FORMAT "-" PTR_FORMAT "), state=", p2i(region_start), p2i(region_end));
    switch (minfo->State) {
      case MEM_COMMIT:  st->print_raw("MEM_COMMIT "); break;
      case MEM_FREE:    st->print_raw("MEM_FREE   "); break;
      case MEM_RESERVE: st->print_raw("MEM_RESERVE"); break;
      default: st->print("%x?", (unsigned)minfo->State);
    }
    st->print(", prot=%3x, type=", (unsigned)minfo->Protect);
    switch (minfo->Type) {
      case MEM_IMAGE:   st->print_raw("MEM_IMAGE  "); break;
      case MEM_MAPPED:  st->print_raw("MEM_MAPPED "); break;
      case MEM_PRIVATE: st->print_raw("MEM_PRIVATE"); break;
      default: st->print("%x?", (unsigned)minfo->State);
    }
    // At the start of every allocation, print some more information about this mapping.
    // Notes:
    //  - this could be beefed up a lot, similar to os::print_location
    //  - for now we just query the allocation start point. This may be confusing for cases where
    //    the kernel merges multiple mappings.
    if (first_line) {
      char buf[MAX_PATH];
      if (os::dll_address_to_library_name(allocation_base, buf, sizeof(buf), nullptr)) {
        st->print(", %s", buf);
        is_dll = true;
      }
    }
    // If memory is accessible, and we do not know anything else about it, print a snippet
    if (!is_dll &&
        minfo->State == MEM_COMMIT &&
        !(minfo->Protect & PAGE_NOACCESS || minfo->Protect & PAGE_GUARD)) {
      st->print_raw(", ");
      print_snippet(region_start, st);
    }
    st->cr();
    // Next region...
    bool rc = checkedVirtualQuery(region_end, minfo);
    if (rc == false ||                                         // VirtualQuery error, end of allocation?
       (minfo->State == MEM_FREE) ||                           // end of allocation, free memory follows
       ((address)minfo->AllocationBase != allocation_base) ||  // end of allocation, a new one starts
       (region_end > end))                                     // end of range to print.
    {
      return region_end;
    }
    first_line = false;
  }
  #undef IS_IN
  ShouldNotReachHere();
  return nullptr;
}

void os::print_memory_mappings(char* addr, size_t bytes, outputStream* st) {
  MEMORY_BASIC_INFORMATION minfo;
  address start = (address)addr;
  address end = start + bytes;
  address p = start;
  if (p == nullptr) { // Lets skip the zero pages.
    p += os::vm_allocation_granularity();
  }
  address p2 = p; // guard against wraparounds
  int fuse = 0;

  while (p < end && p >= p2) {
    p2 = p;
    // Probe for the next mapping.
    if (checkedVirtualQuery(p, &minfo)) {
      if (minfo.State != MEM_FREE) {
        // Found one. Print it out.
        address p2 = print_one_mapping(&minfo, start, end, st);
        assert(p2 > p, "Sanity");
        p = p2;
      } else {
        // Note: for free regions, most of MEMORY_BASIC_INFORMATION is undefined.
        //  Only region dimensions are not: use those to jump to the end of
        //  the free range.
        address region_start = (address)minfo.BaseAddress;
        address region_end = region_start + minfo.RegionSize;
        assert(p >= region_start && p < region_end, "Sanity");
        p = region_end;
      }
    } else {
      // MSDN doc on VirtualQuery is unclear about what it means if it returns an error.
      //  In particular, whether querying an address outside any mappings would report
      //  a MEM_FREE region or just return an error. From experiments, it seems to return
      //  a MEM_FREE region for unmapped areas in valid address space and an error if we
      //  are outside valid address space.
      // Here, we advance the probe pointer by alloc granularity. But if the range to print
      //  is large, this may take a long time. Therefore lets stop right away if the address
      //  is outside of what we know are valid addresses on Windows. Also, add a loop fuse.
      static const address end_virt = (address)(LP64_ONLY(0x7ffffffffffULL) NOT_LP64(3*G));
      if (p >= end_virt) {
        break;
      } else {
        // Advance probe pointer, but with a fuse to break long loops.
        if (fuse++ == 100000) {
          break;
        }
        p += os::vm_allocation_granularity();
      }
    }
  }
}

#if INCLUDE_JFR

void os::jfr_report_memory_info() {
  PROCESS_MEMORY_COUNTERS_EX pmex;
  ZeroMemory(&pmex, sizeof(PROCESS_MEMORY_COUNTERS_EX));
  pmex.cb = sizeof(pmex);

  BOOL ret = GetProcessMemoryInfo(GetCurrentProcess(), (PROCESS_MEMORY_COUNTERS*) &pmex, sizeof(pmex));
  if (ret != 0) {
    // Send the RSS JFR event
    EventResidentSetSize event;
    event.set_size(pmex.WorkingSetSize);
    event.set_peak(pmex.PeakWorkingSetSize);
    event.commit();
  } else {
    // Log a warning
    static bool first_warning = true;
    if (first_warning) {
      log_warning(jfr)("Error fetching RSS values: GetProcessMemoryInfo failed");
      first_warning = false;
    }
  }
}

#endif // INCLUDE_JFR


// File conventions
const char* os::file_separator() { return "\\"; }
const char* os::line_separator() { return "\r\n"; }
const char* os::path_separator() { return ";"; }

void os::print_user_info(outputStream* st) {
  // not implemented yet
}

void os::print_active_locale(outputStream* st) {
  // not implemented yet
}

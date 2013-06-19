/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

// Must be at least Windows 2000 or XP to use IsDebuggerPresent
#define _WIN32_WINNT 0x500

// no precompiled headers
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm_windows.h"
#include "memory/allocation.inline.hpp"
#include "memory/filemap.hpp"
#include "mutex_windows.inline.hpp"
#include "oops/oop.inline.hpp"
#include "os_share_windows.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm.h"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/extendedPC.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/osThread.hpp"
#include "runtime/perfMemory.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/statSampler.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadCritical.hpp"
#include "runtime/timer.hpp"
#include "services/attachListener.hpp"
#include "services/memTracker.hpp"
#include "services/runtimeService.hpp"
#include "utilities/decoder.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/events.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/vmError.hpp"

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
#include <signal.h>
#include <direct.h>
#include <errno.h>
#include <fcntl.h>
#include <io.h>
#include <process.h>              // For _beginthreadex(), _endthreadex()
#include <imagehlp.h>             // For os::dll_address_to_function_name
/* for enumerating dll libraries */
#include <vdmdbg.h>

// for timer info max values which include all bits
#define ALL_64_BITS CONST64(0xFFFFFFFFFFFFFFFF)

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

#ifdef _M_IA64
#define __CPU__ ia64
#elif _M_AMD64
#define __CPU__ amd64
#else
#define __CPU__ i486
#endif

// save DLL module handle, used by GetModuleFileName

HINSTANCE vm_lib_handle;

BOOL WINAPI DllMain(HINSTANCE hinst, DWORD reason, LPVOID reserved) {
  switch (reason) {
    case DLL_PROCESS_ATTACH:
      vm_lib_handle = hinst;
      if(ForceTimeHighResolution)
        timeBeginPeriod(1L);
      break;
    case DLL_PROCESS_DETACH:
      if(ForceTimeHighResolution)
        timeEndPeriod(1L);
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

bool os::getenv(const char* name, char* buffer, int len) {
 int result = GetEnvironmentVariable(name, buffer, len);
 return result > 0 && result < len;
}


// No setuid programs under Windows.
bool os::have_special_privileges() {
  return false;
}


// This method is  a periodic task to check for misbehaving JNI applications
// under CheckJNI, we can add any periodic checks here.
// For Windows at the moment does nothing
void os::run_periodic_checks() {
  return;
}

#ifndef _WIN64
// previous UnhandledExceptionFilter, if there is one
static LPTOP_LEVEL_EXCEPTION_FILTER prev_uef_handler = NULL;

LONG WINAPI Handle_FLT_Exception(struct _EXCEPTION_POINTERS* exceptionInfo);
#endif
void os::init_system_properties_values() {
  /* sysclasspath, java_home, dll_dir */
  {
      char *home_path;
      char *dll_path;
      char *pslash;
      char *bin = "\\bin";
      char home_dir[MAX_PATH];

      if (!getenv("_ALT_JAVA_HOME_DIR", home_dir, MAX_PATH)) {
          os::jvm_path(home_dir, sizeof(home_dir));
          // Found the full path to jvm.dll.
          // Now cut the path to <java_home>/jre if we can.
          *(strrchr(home_dir, '\\')) = '\0';  /* get rid of \jvm.dll */
          pslash = strrchr(home_dir, '\\');
          if (pslash != NULL) {
              *pslash = '\0';                 /* get rid of \{client|server} */
              pslash = strrchr(home_dir, '\\');
              if (pslash != NULL)
                  *pslash = '\0';             /* get rid of \bin */
          }
      }

      home_path = NEW_C_HEAP_ARRAY(char, strlen(home_dir) + 1, mtInternal);
      if (home_path == NULL)
          return;
      strcpy(home_path, home_dir);
      Arguments::set_java_home(home_path);

      dll_path = NEW_C_HEAP_ARRAY(char, strlen(home_dir) + strlen(bin) + 1, mtInternal);
      if (dll_path == NULL)
          return;
      strcpy(dll_path, home_dir);
      strcat(dll_path, bin);
      Arguments::set_dll_dir(dll_path);

      if (!set_boot_path('\\', ';'))
          return;
  }

  /* library_path */
  #define EXT_DIR "\\lib\\ext"
  #define BIN_DIR "\\bin"
  #define PACKAGE_DIR "\\Sun\\Java"
  {
    /* Win32 library search order (See the documentation for LoadLibrary):
     *
     * 1. The directory from which application is loaded.
     * 2. The system wide Java Extensions directory (Java only)
     * 3. System directory (GetSystemDirectory)
     * 4. Windows directory (GetWindowsDirectory)
     * 5. The PATH environment variable
     * 6. The current directory
     */

    char *library_path;
    char tmp[MAX_PATH];
    char *path_str = ::getenv("PATH");

    library_path = NEW_C_HEAP_ARRAY(char, MAX_PATH * 5 + sizeof(PACKAGE_DIR) +
        sizeof(BIN_DIR) + (path_str ? strlen(path_str) : 0) + 10, mtInternal);

    library_path[0] = '\0';

    GetModuleFileName(NULL, tmp, sizeof(tmp));
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
    FREE_C_HEAP_ARRAY(char, library_path, mtInternal);
  }

  /* Default extensions directory */
  {
    char path[MAX_PATH];
    char buf[2 * MAX_PATH + 2 * sizeof(EXT_DIR) + sizeof(PACKAGE_DIR) + 1];
    GetWindowsDirectory(path, MAX_PATH);
    sprintf(buf, "%s%s;%s%s%s", Arguments::get_java_home(), EXT_DIR,
        path, PACKAGE_DIR, EXT_DIR);
    Arguments::set_ext_dirs(buf);
  }
  #undef EXT_DIR
  #undef BIN_DIR
  #undef PACKAGE_DIR

  /* Default endorsed standards directory. */
  {
    #define ENDORSED_DIR "\\lib\\endorsed"
    size_t len = strlen(Arguments::get_java_home()) + sizeof(ENDORSED_DIR);
    char * buf = NEW_C_HEAP_ARRAY(char, len, mtInternal);
    sprintf(buf, "%s%s", Arguments::get_java_home(), ENDORSED_DIR);
    Arguments::set_endorsed_dirs(buf);
    #undef ENDORSED_DIR
  }

#ifndef _WIN64
  // set our UnhandledExceptionFilter and save any previous one
  prev_uef_handler = SetUnhandledExceptionFilter(Handle_FLT_Exception);
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

/*
 * RtlCaptureStackBackTrace Windows API may not exist prior to Windows XP.
 * So far, this method is only used by Native Memory Tracking, which is
 * only supported on Windows XP or later.
 */
address os::get_caller_pc(int n) {
#ifdef _NMT_NOINLINE_
  n ++;
#endif
  address pc;
  if (os::Kernel32Dll::RtlCaptureStackBackTrace(n + 1, 1, (PVOID*)&pc, NULL) == 1) {
    return pc;
  }
  return NULL;
}


// os::current_stack_base()
//
//   Returns the base of the stack, which is the stack's
//   starting address.  This function must be called
//   while running on the stack of the thread being queried.

address os::current_stack_base() {
  MEMORY_BASIC_INFORMATION minfo;
  address stack_bottom;
  size_t stack_size;

  VirtualQuery(&minfo, &minfo, sizeof(minfo));
  stack_bottom =  (address)minfo.AllocationBase;
  stack_size = minfo.RegionSize;

  // Add up the sizes of all the regions with the same
  // AllocationBase.
  while( 1 )
  {
    VirtualQuery(stack_bottom+stack_size, &minfo, sizeof(minfo));
    if ( stack_bottom == (address)minfo.AllocationBase )
      stack_size += minfo.RegionSize;
    else
      break;
  }

#ifdef _M_IA64
  // IA64 has memory and register stacks
  //
  // This is the stack layout you get on NT/IA64 if you specify 1MB stack limit
  // at thread creation (1MB backing store growing upwards, 1MB memory stack
  // growing downwards, 2MB summed up)
  //
  // ...
  // ------- top of stack (high address) -----
  // |
  // |      1MB
  // |      Backing Store (Register Stack)
  // |
  // |         / \
  // |          |
  // |          |
  // |          |
  // ------------------------ stack base -----
  // |      1MB
  // |      Memory Stack
  // |
  // |          |
  // |          |
  // |          |
  // |         \ /
  // |
  // ----- bottom of stack (low address) -----
  // ...

  stack_size = stack_size / 2;
#endif
  return stack_bottom + stack_size;
}

size_t os::current_stack_size() {
  size_t sz;
  MEMORY_BASIC_INFORMATION minfo;
  VirtualQuery(&minfo, &minfo, sizeof(minfo));
  sz = (size_t)os::current_stack_base() - (size_t)minfo.AllocationBase;
  return sz;
}

struct tm* os::localtime_pd(const time_t* clock, struct tm* res) {
  const struct tm* time_struct_ptr = localtime(clock);
  if (time_struct_ptr != NULL) {
    *res = *time_struct_ptr;
    return res;
  }
  return NULL;
}

LONG WINAPI topLevelExceptionFilter(struct _EXCEPTION_POINTERS* exceptionInfo);

// Thread start routine for all new Java threads
static unsigned __stdcall java_start(Thread* thread) {
  // Try to randomize the cache line index of hot stack frames.
  // This helps when threads of the same stack traces evict each other's
  // cache lines. The threads can be either from the same JVM instance, or
  // from different JVM instances. The benefit is especially true for
  // processors with hyperthreading technology.
  static int counter = 0;
  int pid = os::current_process_id();
  _alloca(((pid ^ counter++) & 7) * 128);

  OSThread* osthr = thread->osthread();
  assert(osthr->get_state() == RUNNABLE, "invalid os thread state");

  if (UseNUMA) {
    int lgrp_id = os::numa_get_group_id();
    if (lgrp_id != -1) {
      thread->set_lgrp_id(lgrp_id);
    }
  }


  // Install a win32 structured exception handler around every thread created
  // by VM, so VM can genrate error dump when an exception occurred in non-
  // Java thread (e.g. VM thread).
  __try {
     thread->run();
  } __except(topLevelExceptionFilter(
             (_EXCEPTION_POINTERS*)_exception_info())) {
      // Nothing to do.
  }

  // One less thread is executing
  // When the VMThread gets here, the main thread may have already exited
  // which frees the CodeHeap containing the Atomic::add code
  if (thread != VMThread::vm_thread() && VMThread::vm_thread() != NULL) {
    Atomic::dec_ptr((intptr_t*)&os::win32::_os_thread_count);
  }

  return 0;
}

static OSThread* create_os_thread(Thread* thread, HANDLE thread_handle, int thread_id) {
  // Allocate the OSThread object
  OSThread* osthread = new OSThread(NULL, NULL);
  if (osthread == NULL) return NULL;

  // Initialize support for Java interrupts
  HANDLE interrupt_event = CreateEvent(NULL, true, false, NULL);
  if (interrupt_event == NULL) {
    delete osthread;
    return NULL;
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
  if (osthread == NULL) {
     return false;
  }

  // Initial thread state is RUNNABLE
  osthread->set_state(RUNNABLE);

  thread->set_osthread(osthread);
  return true;
}

bool os::create_main_thread(JavaThread* thread) {
#ifdef ASSERT
  thread->verify_not_published();
#endif
  if (_starting_thread == NULL) {
    _starting_thread = create_os_thread(thread, main_thread, main_thread_id);
     if (_starting_thread == NULL) {
        return false;
     }
  }

  // The primordial thread is runnable from the start)
  _starting_thread->set_state(RUNNABLE);

  thread->set_osthread(_starting_thread);
  return true;
}

// Allocate and initialize a new OSThread
bool os::create_thread(Thread* thread, ThreadType thr_type, size_t stack_size) {
  unsigned thread_id;

  // Allocate the OSThread object
  OSThread* osthread = new OSThread(NULL, NULL);
  if (osthread == NULL) {
    return false;
  }

  // Initialize support for Java interrupts
  HANDLE interrupt_event = CreateEvent(NULL, true, false, NULL);
  if (interrupt_event == NULL) {
    delete osthread;
    return NULL;
  }
  osthread->set_interrupt_event(interrupt_event);
  osthread->set_interrupted(false);

  thread->set_osthread(osthread);

  if (stack_size == 0) {
    switch (thr_type) {
    case os::java_thread:
      // Java threads use ThreadStackSize which default value can be changed with the flag -Xss
      if (JavaThread::stack_size_at_create() > 0)
        stack_size = JavaThread::stack_size_at_create();
      break;
    case os::compiler_thread:
      if (CompilerThreadStackSize > 0) {
        stack_size = (size_t)(CompilerThreadStackSize * K);
        break;
      } // else fall through:
        // use VMThreadStackSize if CompilerThreadStackSize is not defined
    case os::vm_thread:
    case os::pgc_thread:
    case os::cgc_thread:
    case os::watcher_thread:
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

#ifndef STACK_SIZE_PARAM_IS_A_RESERVATION
#define STACK_SIZE_PARAM_IS_A_RESERVATION  (0x10000)
#endif

  HANDLE thread_handle =
    (HANDLE)_beginthreadex(NULL,
                           (unsigned)stack_size,
                           (unsigned (__stdcall *)(void*)) java_start,
                           thread,
                           CREATE_SUSPENDED | STACK_SIZE_PARAM_IS_A_RESERVATION,
                           &thread_id);
  if (thread_handle == NULL) {
    // perhaps STACK_SIZE_PARAM_IS_A_RESERVATION is not supported, try again
    // without the flag.
    thread_handle =
    (HANDLE)_beginthreadex(NULL,
                           (unsigned)stack_size,
                           (unsigned (__stdcall *)(void*)) java_start,
                           thread,
                           CREATE_SUSPENDED,
                           &thread_id);
  }
  if (thread_handle == NULL) {
    // Need to clean up stuff we've allocated so far
    CloseHandle(osthread->interrupt_event());
    thread->set_osthread(NULL);
    delete osthread;
    return NULL;
  }

  Atomic::inc_ptr((intptr_t*)&os::win32::_os_thread_count);

  // Store info on the Win32 thread into the OSThread
  osthread->set_thread_handle(thread_handle);
  osthread->set_thread_id(thread_id);

  // Initial thread state is INITIALIZED, not SUSPENDED
  osthread->set_state(INITIALIZED);

  // The thread is returned suspended (in state INITIALIZED), and is started higher up in the call chain
  return true;
}


// Free Win32 resources related to the OSThread
void os::free_thread(OSThread* osthread) {
  assert(osthread != NULL, "osthread not set");
  CloseHandle(osthread->thread_handle());
  CloseHandle(osthread->interrupt_event());
  delete osthread;
}


static int    has_performance_count = 0;
static jlong first_filetime;
static jlong initial_performance_count;
static jlong performance_frequency;


jlong as_long(LARGE_INTEGER x) {
  jlong result = 0; // initialization to avoid warning
  set_high(&result, x.HighPart);
  set_low(&result,  x.LowPart);
  return result;
}


jlong os::elapsed_counter() {
  LARGE_INTEGER count;
  if (has_performance_count) {
    QueryPerformanceCounter(&count);
    return as_long(count) - initial_performance_count;
  } else {
    FILETIME wt;
    GetSystemTimeAsFileTime(&wt);
    return (jlong_from(wt.dwHighDateTime, wt.dwLowDateTime) - first_filetime);
  }
}


jlong os::elapsed_frequency() {
  if (has_performance_count) {
    return performance_frequency;
  } else {
   // the FILETIME time is the number of 100-nanosecond intervals since January 1,1601.
   return 10000000;
  }
}


julong os::available_memory() {
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

julong os::physical_memory() {
  return win32::physical_memory();
}

bool os::has_allocatable_memory_limit(julong* limit) {
  MEMORYSTATUSEX ms;
  ms.dwLength = sizeof(ms);
  GlobalMemoryStatusEx(&ms);
#ifdef _LP64
  *limit = (julong)ms.ullAvailVirtual;
  return true;
#else
  // Limit to 1400m because of the 2gb address space wall
  *limit = MIN2((julong)1400*M, (julong)ms.ullAvailVirtual);
  return true;
#endif
}

// VC6 lacks DWORD_PTR
#if _MSC_VER < 1300
typedef UINT_PTR DWORD_PTR;
#endif

int os::active_processor_count() {
  DWORD_PTR lpProcessAffinityMask = 0;
  DWORD_PTR lpSystemAffinityMask = 0;
  int proc_count = processor_count();
  if (proc_count <= sizeof(UINT_PTR) * BitsPerByte &&
      GetProcessAffinityMask(GetCurrentProcess(), &lpProcessAffinityMask, &lpSystemAffinityMask)) {
    // Nof active processors is number of bits in process affinity mask
    int bitcount = 0;
    while (lpProcessAffinityMask != 0) {
      lpProcessAffinityMask = lpProcessAffinityMask & (lpProcessAffinityMask-1);
      bitcount++;
    }
    return bitcount;
  } else {
    return proc_count;
  }
}

void os::set_native_thread_name(const char *name) {
  // Not yet implemented.
  return;
}

bool os::distribute_processes(uint length, uint* distribution) {
  // Not yet implemented.
  return false;
}

bool os::bind_to_processor(uint processor_id) {
  // Not yet implemented.
  return false;
}

static void initialize_performance_counter() {
  LARGE_INTEGER count;
  if (QueryPerformanceFrequency(&count)) {
    has_performance_count = 1;
    performance_frequency = as_long(count);
    QueryPerformanceCounter(&count);
    initial_performance_count = as_long(count);
  } else {
    has_performance_count = 0;
    FILETIME wt;
    GetSystemTimeAsFileTime(&wt);
    first_filetime = jlong_from(wt.dwHighDateTime, wt.dwLowDateTime);
  }
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
    fatal(err_msg("Error = %d\nWindows error", GetLastError()));
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

FILETIME java_to_windows_time(jlong l) {
  jlong a = (l * 10000) + offset();
  FILETIME result;
  result.dwHighDateTime = high(a);
  result.dwLowDateTime  = low(a);
  return result;
}

bool os::supports_vtime() { return true; }
bool os::enable_vtime() { return false; }
bool os::vtime_enabled() { return false; }

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
  if (UseFakeTimers) {
    return fake_time++;
  } else {
    FILETIME wt;
    GetSystemTimeAsFileTime(&wt);
    return windows_to_java_time(wt);
  }
}

jlong os::javaTimeNanos() {
  if (!has_performance_count) {
    return javaTimeMillis() * NANOSECS_PER_MILLISEC; // the best we can do.
  } else {
    LARGE_INTEGER current_count;
    QueryPerformanceCounter(&current_count);
    double current = as_long(current_count);
    double freq = performance_frequency;
    jlong time = (jlong)((current/freq) * NANOSECS_PER_SEC);
    return time;
  }
}

void os::javaTimeNanos_info(jvmtiTimerInfo *info_ptr) {
  if (!has_performance_count) {
    // javaTimeMillis() doesn't have much percision,
    // but it is not going to wrap -- so all 64 bits
    info_ptr->max_value = ALL_64_BITS;

    // this is a wall clock timer, so may skip
    info_ptr->may_skip_backward = true;
    info_ptr->may_skip_forward = true;
  } else {
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
  }
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
    jlong user_millis = windows_to_java_time(user_time);
    jlong system_millis = windows_to_java_time(kernel_time);
    *process_real_time = ((double) rtc_millis) / ((double) MILLIUNITS);
    *process_user_time = ((double) user_millis) / ((double) MILLIUNITS);
    *process_system_time = ((double) system_millis) / ((double) MILLIUNITS);
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
  if (abort_hook != NULL) {
    abort_hook();
  }
}


static BOOL  (WINAPI *_MiniDumpWriteDump)  ( HANDLE, DWORD, HANDLE, MINIDUMP_TYPE, PMINIDUMP_EXCEPTION_INFORMATION,
                                            PMINIDUMP_USER_STREAM_INFORMATION, PMINIDUMP_CALLBACK_INFORMATION);

void os::check_or_create_dump(void* exceptionRecord, void* contextRecord, char* buffer, size_t bufferSize) {
  HINSTANCE dbghelp;
  EXCEPTION_POINTERS ep;
  MINIDUMP_EXCEPTION_INFORMATION mei;
  MINIDUMP_EXCEPTION_INFORMATION* pmei;

  HANDLE hProcess = GetCurrentProcess();
  DWORD processId = GetCurrentProcessId();
  HANDLE dumpFile;
  MINIDUMP_TYPE dumpType;
  static const char* cwd;

// Default is to always create dump for debug builds, on product builds only dump on server versions of Windows.
#ifndef ASSERT
  // If running on a client version of Windows and user has not explicitly enabled dumping
  if (!os::win32::is_windows_server() && !CreateMinidumpOnCrash) {
    VMError::report_coredump_status("Minidumps are not enabled by default on client versions of Windows", false);
    return;
    // If running on a server version of Windows and user has explictly disabled dumping
  } else if (os::win32::is_windows_server() && !FLAG_IS_DEFAULT(CreateMinidumpOnCrash) && !CreateMinidumpOnCrash) {
    VMError::report_coredump_status("Minidump has been disabled from the command line", false);
    return;
  }
#else
  if (!FLAG_IS_DEFAULT(CreateMinidumpOnCrash) && !CreateMinidumpOnCrash) {
    VMError::report_coredump_status("Minidump has been disabled from the command line", false);
    return;
  }
#endif

  dbghelp = os::win32::load_Windows_dll("DBGHELP.DLL", NULL, 0);

  if (dbghelp == NULL) {
    VMError::report_coredump_status("Failed to load dbghelp.dll", false);
    return;
  }

  _MiniDumpWriteDump = CAST_TO_FN_PTR(
    BOOL(WINAPI *)( HANDLE, DWORD, HANDLE, MINIDUMP_TYPE, PMINIDUMP_EXCEPTION_INFORMATION,
    PMINIDUMP_USER_STREAM_INFORMATION, PMINIDUMP_CALLBACK_INFORMATION),
    GetProcAddress(dbghelp, "MiniDumpWriteDump"));

  if (_MiniDumpWriteDump == NULL) {
    VMError::report_coredump_status("Failed to find MiniDumpWriteDump() in module dbghelp.dll", false);
    return;
  }

  dumpType = (MINIDUMP_TYPE)(MiniDumpWithFullMemory | MiniDumpWithHandleData);

// Older versions of dbghelp.h doesn't contain all the dumptypes we want, dbghelp.h with
// API_VERSION_NUMBER 11 or higher contains the ones we want though
#if API_VERSION_NUMBER >= 11
  dumpType = (MINIDUMP_TYPE)(dumpType | MiniDumpWithFullMemoryInfo | MiniDumpWithThreadInfo |
    MiniDumpWithUnloadedModules);
#endif

  cwd = get_current_directory(NULL, 0);
  jio_snprintf(buffer, bufferSize, "%s\\hs_err_pid%u.mdmp",cwd, current_process_id());
  dumpFile = CreateFile(buffer, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);

  if (dumpFile == INVALID_HANDLE_VALUE) {
    VMError::report_coredump_status("Failed to create file for dumping", false);
    return;
  }
  if (exceptionRecord != NULL && contextRecord != NULL) {
    ep.ContextRecord = (PCONTEXT) contextRecord;
    ep.ExceptionRecord = (PEXCEPTION_RECORD) exceptionRecord;

    mei.ThreadId = GetCurrentThreadId();
    mei.ExceptionPointers = &ep;
    pmei = &mei;
  } else {
    pmei = NULL;
  }


  // Older versions of dbghelp.dll (the one shipped with Win2003 for example) may not support all
  // the dump types we really want. If first call fails, lets fall back to just use MiniDumpWithFullMemory then.
  if (_MiniDumpWriteDump(hProcess, processId, dumpFile, dumpType, pmei, NULL, NULL) == false &&
      _MiniDumpWriteDump(hProcess, processId, dumpFile, (MINIDUMP_TYPE)MiniDumpWithFullMemory, pmei, NULL, NULL) == false) {
        DWORD error = GetLastError();
        LPTSTR msgbuf = NULL;

        if (FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER |
                      FORMAT_MESSAGE_FROM_SYSTEM |
                      FORMAT_MESSAGE_IGNORE_INSERTS,
                      NULL, error, 0, (LPTSTR)&msgbuf, 0, NULL) != 0) {

          jio_snprintf(buffer, bufferSize, "Call to MiniDumpWriteDump() failed (Error 0x%x: %s)", error, msgbuf);
          LocalFree(msgbuf);
        } else {
          // Call to FormatMessage failed, just include the result from GetLastError
          jio_snprintf(buffer, bufferSize, "Call to MiniDumpWriteDump() failed (Error 0x%x)", error);
        }
        VMError::report_coredump_status(buffer, false);
  } else {
    VMError::report_coredump_status(buffer, true);
  }

  CloseHandle(dumpFile);
}



void os::abort(bool dump_core)
{
  os::shutdown();
  // no core dump on Windows
  ::exit(1);
}

// Die immediately, no exit hook, no abort hook, no cleanup.
void os::die() {
  _exit(-1);
}

// Directory routines copied from src/win32/native/java/io/dirent_md.c
//  * dirent_md.c       1.15 00/02/02
//
// The declarations for DIR and struct dirent are in jvm_win32.h.

/* Caller must have already run dirname through JVM_NativePath, which removes
   duplicate slashes and converts all instances of '/' into '\\'. */

DIR *
os::opendir(const char *dirname)
{
    assert(dirname != NULL, "just checking");   // hotspot change
    DIR *dirp = (DIR *)malloc(sizeof(DIR), mtInternal);
    DWORD fattr;                                // hotspot change
    char alt_dirname[4] = { 0, 0, 0, 0 };

    if (dirp == 0) {
        errno = ENOMEM;
        return 0;
    }

    /*
     * Win32 accepts "\" in its POSIX stat(), but refuses to treat it
     * as a directory in FindFirstFile().  We detect this case here and
     * prepend the current drive name.
     */
    if (dirname[1] == '\0' && dirname[0] == '\\') {
        alt_dirname[0] = _getdrive() + 'A' - 1;
        alt_dirname[1] = ':';
        alt_dirname[2] = '\\';
        alt_dirname[3] = '\0';
        dirname = alt_dirname;
    }

    dirp->path = (char *)malloc(strlen(dirname) + 5, mtInternal);
    if (dirp->path == 0) {
        free(dirp, mtInternal);
        errno = ENOMEM;
        return 0;
    }
    strcpy(dirp->path, dirname);

    fattr = GetFileAttributes(dirp->path);
    if (fattr == 0xffffffff) {
        free(dirp->path, mtInternal);
        free(dirp, mtInternal);
        errno = ENOENT;
        return 0;
    } else if ((fattr & FILE_ATTRIBUTE_DIRECTORY) == 0) {
        free(dirp->path, mtInternal);
        free(dirp, mtInternal);
        errno = ENOTDIR;
        return 0;
    }

    /* Append "*.*", or possibly "\\*.*", to path */
    if (dirp->path[1] == ':'
        && (dirp->path[2] == '\0'
            || (dirp->path[2] == '\\' && dirp->path[3] == '\0'))) {
        /* No '\\' needed for cases like "Z:" or "Z:\" */
        strcat(dirp->path, "*.*");
    } else {
        strcat(dirp->path, "\\*.*");
    }

    dirp->handle = FindFirstFile(dirp->path, &dirp->find_data);
    if (dirp->handle == INVALID_HANDLE_VALUE) {
        if (GetLastError() != ERROR_FILE_NOT_FOUND) {
            free(dirp->path, mtInternal);
            free(dirp, mtInternal);
            errno = EACCES;
            return 0;
        }
    }
    return dirp;
}

/* parameter dbuf unused on Windows */

struct dirent *
os::readdir(DIR *dirp, dirent *dbuf)
{
    assert(dirp != NULL, "just checking");      // hotspot change
    if (dirp->handle == INVALID_HANDLE_VALUE) {
        return 0;
    }

    strcpy(dirp->dirent.d_name, dirp->find_data.cFileName);

    if (!FindNextFile(dirp->handle, &dirp->find_data)) {
        if (GetLastError() == ERROR_INVALID_HANDLE) {
            errno = EBADF;
            return 0;
        }
        FindClose(dirp->handle);
        dirp->handle = INVALID_HANDLE_VALUE;
    }

    return &dirp->dirent;
}

int
os::closedir(DIR *dirp)
{
    assert(dirp != NULL, "just checking");      // hotspot change
    if (dirp->handle != INVALID_HANDLE_VALUE) {
        if (!FindClose(dirp->handle)) {
            errno = EBADF;
            return -1;
        }
        dirp->handle = INVALID_HANDLE_VALUE;
    }
    free(dirp->path, mtInternal);
    free(dirp, mtInternal);
    return 0;
}

// This must be hard coded because it's the system's temporary
// directory not the java application's temp directory, ala java.io.tmpdir.
const char* os::get_temp_directory() {
  static char path_buf[MAX_PATH];
  if (GetTempPath(MAX_PATH, path_buf)>0)
    return path_buf;
  else{
    path_buf[0]='\0';
    return path_buf;
  }
}

static bool file_exists(const char* filename) {
  if (filename == NULL || strlen(filename) == 0) {
    return false;
  }
  return GetFileAttributes(filename) != INVALID_FILE_ATTRIBUTES;
}

bool os::dll_build_name(char *buffer, size_t buflen,
                        const char* pname, const char* fname) {
  bool retval = false;
  const size_t pnamelen = pname ? strlen(pname) : 0;
  const char c = (pnamelen > 0) ? pname[pnamelen-1] : 0;

  // Return error on buffer overflow.
  if (pnamelen + strlen(fname) + 10 > buflen) {
    return retval;
  }

  if (pnamelen == 0) {
    jio_snprintf(buffer, buflen, "%s.dll", fname);
    retval = true;
  } else if (c == ':' || c == '\\') {
    jio_snprintf(buffer, buflen, "%s%s.dll", pname, fname);
    retval = true;
  } else if (strchr(pname, *os::path_separator()) != NULL) {
    int n;
    char** pelements = split_path(pname, &n);
    if (pelements == NULL) {
      return false;
    }
    for (int i = 0 ; i < n ; i++) {
      char* path = pelements[i];
      // Really shouldn't be NULL, but check can't hurt
      size_t plen = (path == NULL) ? 0 : strlen(path);
      if (plen == 0) {
        continue; // skip the empty path values
      }
      const char lastchar = path[plen - 1];
      if (lastchar == ':' || lastchar == '\\') {
        jio_snprintf(buffer, buflen, "%s%s.dll", path, fname);
      } else {
        jio_snprintf(buffer, buflen, "%s\\%s.dll", path, fname);
      }
      if (file_exists(buffer)) {
        retval = true;
        break;
      }
    }
    // release the storage
    for (int i = 0 ; i < n ; i++) {
      if (pelements[i] != NULL) {
        FREE_C_HEAP_ARRAY(char, pelements[i], mtInternal);
      }
    }
    if (pelements != NULL) {
      FREE_C_HEAP_ARRAY(char*, pelements, mtInternal);
    }
  } else {
    jio_snprintf(buffer, buflen, "%s\\%s.dll", pname, fname);
    retval = true;
  }
  return retval;
}

// Needs to be in os specific directory because windows requires another
// header file <direct.h>
const char* os::get_current_directory(char *buf, size_t buflen) {
  int n = static_cast<int>(buflen);
  if (buflen > INT_MAX)  n = INT_MAX;
  return _getcwd(buf, n);
}

//-----------------------------------------------------------
// Helper functions for fatal error handler
#ifdef _WIN64
// Helper routine which returns true if address in
// within the NTDLL address space.
//
static bool _addr_in_ntdll( address addr )
{
  HMODULE hmod;
  MODULEINFO minfo;

  hmod = GetModuleHandle("NTDLL.DLL");
  if ( hmod == NULL ) return false;
  if ( !os::PSApiDll::GetModuleInformation( GetCurrentProcess(), hmod,
                               &minfo, sizeof(MODULEINFO)) )
    return false;

  if ( (addr >= minfo.lpBaseOfDll) &&
       (addr < (address)((uintptr_t)minfo.lpBaseOfDll + (uintptr_t)minfo.SizeOfImage)))
    return true;
  else
    return false;
}
#endif


// Enumerate all modules for a given process ID
//
// Notice that Windows 95/98/Me and Windows NT/2000/XP have
// different API for doing this. We use PSAPI.DLL on NT based
// Windows and ToolHelp on 95/98/Me.

// Callback function that is called by enumerate_modules() on
// every DLL module.
// Input parameters:
//    int       pid,
//    char*     module_file_name,
//    address   module_base_addr,
//    unsigned  module_size,
//    void*     param
typedef int (*EnumModulesCallbackFunc)(int, char *, address, unsigned, void *);

// enumerate_modules for Windows NT, using PSAPI
static int _enumerate_modules_winnt( int pid, EnumModulesCallbackFunc func, void * param)
{
  HANDLE   hProcess ;

# define MAX_NUM_MODULES 128
  HMODULE     modules[MAX_NUM_MODULES];
  static char filename[ MAX_PATH ];
  int         result = 0;

  if (!os::PSApiDll::PSApiAvailable()) {
    return 0;
  }

  hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ,
                         FALSE, pid ) ;
  if (hProcess == NULL) return 0;

  DWORD size_needed;
  if (!os::PSApiDll::EnumProcessModules(hProcess, modules,
                           sizeof(modules), &size_needed)) {
      CloseHandle( hProcess );
      return 0;
  }

  // number of modules that are currently loaded
  int num_modules = size_needed / sizeof(HMODULE);

  for (int i = 0; i < MIN2(num_modules, MAX_NUM_MODULES); i++) {
    // Get Full pathname:
    if(!os::PSApiDll::GetModuleFileNameEx(hProcess, modules[i],
                             filename, sizeof(filename))) {
        filename[0] = '\0';
    }

    MODULEINFO modinfo;
    if (!os::PSApiDll::GetModuleInformation(hProcess, modules[i],
                               &modinfo, sizeof(modinfo))) {
        modinfo.lpBaseOfDll = NULL;
        modinfo.SizeOfImage = 0;
    }

    // Invoke callback function
    result = func(pid, filename, (address)modinfo.lpBaseOfDll,
                  modinfo.SizeOfImage, param);
    if (result) break;
  }

  CloseHandle( hProcess ) ;
  return result;
}


// enumerate_modules for Windows 95/98/ME, using TOOLHELP
static int _enumerate_modules_windows( int pid, EnumModulesCallbackFunc func, void *param)
{
  HANDLE                hSnapShot ;
  static MODULEENTRY32  modentry ;
  int                   result = 0;

  if (!os::Kernel32Dll::HelpToolsAvailable()) {
    return 0;
  }

  // Get a handle to a Toolhelp snapshot of the system
  hSnapShot = os::Kernel32Dll::CreateToolhelp32Snapshot(TH32CS_SNAPMODULE, pid ) ;
  if( hSnapShot == INVALID_HANDLE_VALUE ) {
      return FALSE ;
  }

  // iterate through all modules
  modentry.dwSize = sizeof(MODULEENTRY32) ;
  bool not_done = os::Kernel32Dll::Module32First( hSnapShot, &modentry ) != 0;

  while( not_done ) {
    // invoke the callback
    result=func(pid, modentry.szExePath, (address)modentry.modBaseAddr,
                modentry.modBaseSize, param);
    if (result) break;

    modentry.dwSize = sizeof(MODULEENTRY32) ;
    not_done = os::Kernel32Dll::Module32Next( hSnapShot, &modentry ) != 0;
  }

  CloseHandle(hSnapShot);
  return result;
}

int enumerate_modules( int pid, EnumModulesCallbackFunc func, void * param )
{
  // Get current process ID if caller doesn't provide it.
  if (!pid) pid = os::current_process_id();

  if (os::win32::is_nt()) return _enumerate_modules_winnt  (pid, func, param);
  else                    return _enumerate_modules_windows(pid, func, param);
}

struct _modinfo {
   address addr;
   char*   full_path;   // point to a char buffer
   int     buflen;      // size of the buffer
   address base_addr;
};

static int _locate_module_by_addr(int pid, char * mod_fname, address base_addr,
                                  unsigned size, void * param) {
   struct _modinfo *pmod = (struct _modinfo *)param;
   if (!pmod) return -1;

   if (base_addr     <= pmod->addr &&
       base_addr+size > pmod->addr) {
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
// NOTE: the reason we don't use SymGetModuleInfo() is it doesn't always
//       return the full path to the DLL file, sometimes it returns path
//       to the corresponding PDB file (debug info); sometimes it only
//       returns partial path, which makes life painful.

   struct _modinfo mi;
   mi.addr      = addr;
   mi.full_path = buf;
   mi.buflen    = buflen;
   int pid = os::current_process_id();
   if (enumerate_modules(pid, _locate_module_by_addr, (void *)&mi)) {
      // buf already contains path name
      if (offset) *offset = addr - mi.base_addr;
      return true;
   } else {
      if (buf) buf[0] = '\0';
      if (offset) *offset = -1;
      return false;
   }
}

bool os::dll_address_to_function_name(address addr, char *buf,
                                      int buflen, int *offset) {
  if (Decoder::decode(addr, buf, buflen, offset)) {
    return true;
  }
  if (offset != NULL)  *offset  = -1;
  if (buf != NULL) buf[0] = '\0';
  return false;
}

// save the start and end address of jvm.dll into param[0] and param[1]
static int _locate_jvm_dll(int pid, char* mod_fname, address base_addr,
                    unsigned size, void * param) {
   if (!param) return -1;

   if (base_addr     <= (address)_locate_jvm_dll &&
       base_addr+size > (address)_locate_jvm_dll) {
         ((address*)param)[0] = base_addr;
         ((address*)param)[1] = base_addr + size;
         return 1;
   }
   return 0;
}

address vm_lib_location[2];    // start and end address of jvm.dll

// check if addr is inside jvm.dll
bool os::address_is_in_vm(address addr) {
  if (!vm_lib_location[0] || !vm_lib_location[1]) {
    int pid = os::current_process_id();
    if (!enumerate_modules(pid, _locate_jvm_dll, (void *)vm_lib_location)) {
      assert(false, "Can't find jvm module.");
      return false;
    }
  }

  return (vm_lib_location[0] <= addr) && (addr < vm_lib_location[1]);
}

// print module info; param is outputStream*
static int _print_module(int pid, char* fname, address base,
                         unsigned size, void* param) {
   if (!param) return -1;

   outputStream* st = (outputStream*)param;

   address end_addr = base + size;
   st->print(PTR_FORMAT " - " PTR_FORMAT " \t%s\n", base, end_addr, fname);
   return 0;
}

// Loads .dll/.so and
// in case of error it checks if .dll/.so was built for the
// same architecture as Hotspot is running on
void * os::dll_load(const char *name, char *ebuf, int ebuflen)
{
  void * result = LoadLibrary(name);
  if (result != NULL)
  {
    return result;
  }

  DWORD errcode = GetLastError();
  if (errcode == ERROR_MOD_NOT_FOUND) {
    strncpy(ebuf, "Can't find dependent libraries", ebuflen-1);
    ebuf[ebuflen-1]='\0';
    return NULL;
  }

  // Parsing dll below
  // If we can read dll-info and find that dll was built
  // for an architecture other than Hotspot is running in
  // - then print to buffer "DLL was built for a different architecture"
  // else call os::lasterror to obtain system error message

  // Read system error message into ebuf
  // It may or may not be overwritten below (in the for loop and just above)
  lasterror(ebuf, (size_t) ebuflen);
  ebuf[ebuflen-1]='\0';
  int file_descriptor=::open(name, O_RDONLY | O_BINARY, 0);
  if (file_descriptor<0)
  {
    return NULL;
  }

  uint32_t signature_offset;
  uint16_t lib_arch=0;
  bool failed_to_get_lib_arch=
  (
    //Go to position 3c in the dll
    (os::seek_to_file_offset(file_descriptor,IMAGE_FILE_PTR_TO_SIGNATURE)<0)
    ||
    // Read loacation of signature
    (sizeof(signature_offset)!=
      (os::read(file_descriptor, (void*)&signature_offset,sizeof(signature_offset))))
    ||
    //Go to COFF File Header in dll
    //that is located after"signature" (4 bytes long)
    (os::seek_to_file_offset(file_descriptor,
      signature_offset+IMAGE_FILE_SIGNATURE_LENGTH)<0)
    ||
    //Read field that contains code of architecture
    // that dll was build for
    (sizeof(lib_arch)!=
      (os::read(file_descriptor, (void*)&lib_arch,sizeof(lib_arch))))
  );

  ::close(file_descriptor);
  if (failed_to_get_lib_arch)
  {
    // file i/o error - report os::lasterror(...) msg
    return NULL;
  }

  typedef struct
  {
    uint16_t arch_code;
    char* arch_name;
  } arch_t;

  static const arch_t arch_array[]={
    {IMAGE_FILE_MACHINE_I386,      (char*)"IA 32"},
    {IMAGE_FILE_MACHINE_AMD64,     (char*)"AMD 64"},
    {IMAGE_FILE_MACHINE_IA64,      (char*)"IA 64"}
  };
  #if   (defined _M_IA64)
    static const uint16_t running_arch=IMAGE_FILE_MACHINE_IA64;
  #elif (defined _M_AMD64)
    static const uint16_t running_arch=IMAGE_FILE_MACHINE_AMD64;
  #elif (defined _M_IX86)
    static const uint16_t running_arch=IMAGE_FILE_MACHINE_I386;
  #else
    #error Method os::dll_load requires that one of following \
           is defined :_M_IA64,_M_AMD64 or _M_IX86
  #endif


  // Obtain a string for printf operation
  // lib_arch_str shall contain string what platform this .dll was built for
  // running_arch_str shall string contain what platform Hotspot was built for
  char *running_arch_str=NULL,*lib_arch_str=NULL;
  for (unsigned int i=0;i<ARRAY_SIZE(arch_array);i++)
  {
    if (lib_arch==arch_array[i].arch_code)
      lib_arch_str=arch_array[i].arch_name;
    if (running_arch==arch_array[i].arch_code)
      running_arch_str=arch_array[i].arch_name;
  }

  assert(running_arch_str,
    "Didn't find runing architecture code in arch_array");

  // If the architure is right
  // but some other error took place - report os::lasterror(...) msg
  if (lib_arch == running_arch)
  {
    return NULL;
  }

  if (lib_arch_str!=NULL)
  {
    ::_snprintf(ebuf, ebuflen-1,
      "Can't load %s-bit .dll on a %s-bit platform",
      lib_arch_str,running_arch_str);
  }
  else
  {
    // don't know what architecture this dll was build for
    ::_snprintf(ebuf, ebuflen-1,
      "Can't load this .dll (machine code=0x%x) on a %s-bit platform",
      lib_arch,running_arch_str);
  }

  return NULL;
}


void os::print_dll_info(outputStream *st) {
   int pid = os::current_process_id();
   st->print_cr("Dynamic libraries:");
   enumerate_modules(pid, _print_module, (void *)st);
}

void os::print_os_info_brief(outputStream* st) {
  os::print_os_info(st);
}

void os::print_os_info(outputStream* st) {
  st->print("OS:");

  os::win32::print_windows_version(st);
}

void os::win32::print_windows_version(outputStream* st) {
  OSVERSIONINFOEX osvi;
  ZeroMemory(&osvi, sizeof(OSVERSIONINFOEX));
  osvi.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);

  if (!GetVersionEx((OSVERSIONINFO *)&osvi)) {
    st->print_cr("N/A");
    return;
  }

  int os_vers = osvi.dwMajorVersion * 1000 + osvi.dwMinorVersion;
  if (osvi.dwPlatformId == VER_PLATFORM_WIN32_NT) {
    switch (os_vers) {
    case 3051: st->print(" Windows NT 3.51"); break;
    case 4000: st->print(" Windows NT 4.0"); break;
    case 5000: st->print(" Windows 2000"); break;
    case 5001: st->print(" Windows XP"); break;
    case 5002:
    case 6000:
    case 6001:
    case 6002: {
      // Retrieve SYSTEM_INFO from GetNativeSystemInfo call so that we could
      // find out whether we are running on 64 bit processor or not.
      SYSTEM_INFO si;
      ZeroMemory(&si, sizeof(SYSTEM_INFO));
        if (!os::Kernel32Dll::GetNativeSystemInfoAvailable()){
          GetSystemInfo(&si);
      } else {
        os::Kernel32Dll::GetNativeSystemInfo(&si);
      }
      if (os_vers == 5002) {
        if (osvi.wProductType == VER_NT_WORKSTATION &&
            si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64)
          st->print(" Windows XP x64 Edition");
        else
            st->print(" Windows Server 2003 family");
      } else if (os_vers == 6000) {
        if (osvi.wProductType == VER_NT_WORKSTATION)
            st->print(" Windows Vista");
        else
            st->print(" Windows Server 2008");
        if (si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64)
            st->print(" , 64 bit");
      } else if (os_vers == 6001) {
        if (osvi.wProductType == VER_NT_WORKSTATION) {
            st->print(" Windows 7");
        } else {
            // Unrecognized windows, print out its major and minor versions
            st->print(" Windows NT %d.%d", osvi.dwMajorVersion, osvi.dwMinorVersion);
        }
        if (si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64)
            st->print(" , 64 bit");
      } else if (os_vers == 6002) {
        if (osvi.wProductType == VER_NT_WORKSTATION) {
            st->print(" Windows 8");
        } else {
            st->print(" Windows Server 2012");
        }
        if (si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64)
            st->print(" , 64 bit");
      } else { // future os
        // Unrecognized windows, print out its major and minor versions
        st->print(" Windows NT %d.%d", osvi.dwMajorVersion, osvi.dwMinorVersion);
        if (si.wProcessorArchitecture == PROCESSOR_ARCHITECTURE_AMD64)
            st->print(" , 64 bit");
      }
      break;
    }
    default: // future windows, print out its major and minor versions
      st->print(" Windows NT %d.%d", osvi.dwMajorVersion, osvi.dwMinorVersion);
    }
  } else {
    switch (os_vers) {
    case 4000: st->print(" Windows 95"); break;
    case 4010: st->print(" Windows 98"); break;
    case 4090: st->print(" Windows Me"); break;
    default: // future windows, print out its major and minor versions
      st->print(" Windows %d.%d", osvi.dwMajorVersion, osvi.dwMinorVersion);
    }
  }
  st->print(" Build %d", osvi.dwBuildNumber);
  st->print(" %s", osvi.szCSDVersion);           // service pack
  st->cr();
}

void os::pd_print_cpu_info(outputStream* st) {
  // Nothing to do for now.
}

void os::print_memory_info(outputStream* st) {
  st->print("Memory:");
  st->print(" %dk page", os::vm_page_size()>>10);

  // Use GlobalMemoryStatusEx() because GlobalMemoryStatus() may return incorrect
  // value if total memory is larger than 4GB
  MEMORYSTATUSEX ms;
  ms.dwLength = sizeof(ms);
  GlobalMemoryStatusEx(&ms);

  st->print(", physical %uk", os::physical_memory() >> 10);
  st->print("(%uk free)", os::available_memory() >> 10);

  st->print(", swap %uk", ms.ullTotalPageFile >> 10);
  st->print("(%uk free)", ms.ullAvailPageFile >> 10);
  st->cr();
}

void os::print_siginfo(outputStream *st, void *siginfo) {
  EXCEPTION_RECORD* er = (EXCEPTION_RECORD*)siginfo;
  st->print("siginfo:");
  st->print(" ExceptionCode=0x%x", er->ExceptionCode);

  if (er->ExceptionCode == EXCEPTION_ACCESS_VIOLATION &&
      er->NumberParameters >= 2) {
      switch (er->ExceptionInformation[0]) {
      case 0: st->print(", reading address"); break;
      case 1: st->print(", writing address"); break;
      default: st->print(", ExceptionInformation=" INTPTR_FORMAT,
                            er->ExceptionInformation[0]);
      }
      st->print(" " INTPTR_FORMAT, er->ExceptionInformation[1]);
  } else if (er->ExceptionCode == EXCEPTION_IN_PAGE_ERROR &&
             er->NumberParameters >= 2 && UseSharedSpaces) {
    FileMapInfo* mapinfo = FileMapInfo::current_info();
    if (mapinfo->is_in_shared_space((void*)er->ExceptionInformation[1])) {
      st->print("\n\nError accessing class data sharing archive."       \
                " Mapped file inaccessible during execution, "          \
                " possible disk/network problem.");
    }
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
  if (Arguments::created_by_gamma_launcher()) {
     // Support for the gamma launcher. Check for an
     // JAVA_HOME environment variable
     // and fix up the path so it looks like
     // libjvm.so is installed there (append a fake suffix
     // hotspot/libjvm.so).
     char* java_home_var = ::getenv("JAVA_HOME");
     if (java_home_var != NULL && java_home_var[0] != 0) {

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

  if(buf[0] == '\0') {
  GetModuleFileName(vm_lib_handle, buf, buflen);
  }
  strcpy(saved_jvm_path, buf);
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
          NULL,
          errval,
          0,
          buf,
          (DWORD)len,
          NULL);
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
    const char* s = strerror(errno);
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
  if (error == 0)
    error = errno;
  return (int)error;
}

// sun.misc.Signal
// NOTE that this is a workaround for an apparent kernel bug where if
// a signal handler for SIGBREAK is installed then that signal handler
// takes priority over the console control handler for CTRL_CLOSE_EVENT.
// See bug 4416763.
static void (*sigbreakHandler)(int) = NULL;

static void UserHandler(int sig, void *siginfo, void *context) {
  os::signal_notify(sig);
  // We need to reinstate the signal handler each time...
  os::signal(sig, (void*)UserHandler);
}

void* os::user_handler() {
  return (void*) UserHandler;
}

void* os::signal(int signal_number, void* handler) {
  if ((signal_number == SIGBREAK) && (!ReduceSignalUsage)) {
    void (*oldHandler)(int) = sigbreakHandler;
    sigbreakHandler = (void (*)(int)) handler;
    return (void*) oldHandler;
  } else {
    return (void*)::signal(signal_number, (void (*)(int))handler);
  }
}

void os::signal_raise(int signal_number) {
  raise(signal_number);
}

// The Win32 C runtime library maps all console control events other than ^C
// into SIGBREAK, which makes it impossible to distinguish ^BREAK from close,
// logoff, and shutdown events.  We therefore install our own console handler
// that raises SIGTERM for the latter cases.
//
static BOOL WINAPI consoleHandler(DWORD event) {
  switch(event) {
    case CTRL_C_EVENT:
      if (is_error_reported()) {
        // Ctrl-C is pressed during error reporting, likely because the error
        // handler fails to abort. Let VM die immediately.
        os::die();
      }

      os::signal_raise(SIGINT);
      return TRUE;
      break;
    case CTRL_BREAK_EVENT:
      if (sigbreakHandler != NULL) {
        (*sigbreakHandler)(SIGBREAK);
      }
      return TRUE;
      break;
    case CTRL_LOGOFF_EVENT: {
      // Don't terminate JVM if it is running in a non-interactive session,
      // such as a service process.
      USEROBJECTFLAGS flags;
      HANDLE handle = GetProcessWindowStation();
      if (handle != NULL &&
          GetUserObjectInformation(handle, UOI_FLAGS, &flags,
            sizeof( USEROBJECTFLAGS), NULL)) {
        // If it is a non-interactive session, let next handler to deal
        // with it.
        if ((flags.dwFlags & WSF_VISIBLE) == 0) {
          return FALSE;
        }
      }
    }
    case CTRL_CLOSE_EVENT:
    case CTRL_SHUTDOWN_EVENT:
      os::signal_raise(SIGTERM);
      return TRUE;
      break;
    default:
      break;
  }
  return FALSE;
}

/*
 * The following code is moved from os.cpp for making this
 * code platform specific, which it is by its very nature.
 */

// Return maximum OS signal used + 1 for internal use only
// Used as exit signal for signal_thread
int os::sigexitnum_pd(){
  return NSIG;
}

// a counter for each possible signal value, including signal_thread exit signal
static volatile jint pending_signals[NSIG+1] = { 0 };
static HANDLE sig_sem = NULL;

void os::signal_init_pd() {
  // Initialize signal structures
  memset((void*)pending_signals, 0, sizeof(pending_signals));

  sig_sem = ::CreateSemaphore(NULL, 0, NSIG+1, NULL);

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

  if (!ReduceSignalUsage) {
    // Add a CTRL-C handler
    SetConsoleCtrlHandler(consoleHandler, TRUE);
  }
}

void os::signal_notify(int signal_number) {
  BOOL ret;
  if (sig_sem != NULL) {
    Atomic::inc(&pending_signals[signal_number]);
    ret = ::ReleaseSemaphore(sig_sem, 1, NULL);
    assert(ret != 0, "ReleaseSemaphore() failed");
  }
}

static int check_pending_signals(bool wait_for_signal) {
  DWORD ret;
  while (true) {
    for (int i = 0; i < NSIG + 1; i++) {
      jint n = pending_signals[i];
      if (n > 0 && n == Atomic::cmpxchg(n - 1, &pending_signals[i], n)) {
        return i;
      }
    }
    if (!wait_for_signal) {
      return -1;
    }

    JavaThread *thread = JavaThread::current();

    ThreadBlockInVM tbivm(thread);

    bool threadIsSuspended;
    do {
      thread->set_suspend_equivalent();
      // cleared by handle_special_suspend_equivalent_condition() or java_suspend_self()
      ret = ::WaitForSingleObject(sig_sem, INFINITE);
      assert(ret == WAIT_OBJECT_0, "WaitForSingleObject() failed");

      // were we externally suspended while we were waiting?
      threadIsSuspended = thread->handle_special_suspend_equivalent_condition();
      if (threadIsSuspended) {
        //
        // The semaphore has been incremented, but while we were waiting
        // another thread suspended us. We don't want to continue running
        // while suspended because that would surprise the thread that
        // suspended us.
        //
        ret = ::ReleaseSemaphore(sig_sem, 1, NULL);
        assert(ret != 0, "ReleaseSemaphore() failed");

        thread->java_suspend_self();
      }
    } while (threadIsSuspended);
  }
}

int os::signal_lookup() {
  return check_pending_signals(false);
}

int os::signal_wait() {
  return check_pending_signals(true);
}

// Implicit OS exception handling

LONG Handle_Exception(struct _EXCEPTION_POINTERS* exceptionInfo, address handler) {
  JavaThread* thread = JavaThread::current();
  // Save pc in thread
#ifdef _M_IA64
  // Do not blow up if no thread info available.
  if (thread) {
    // Saving PRECISE pc (with slot information) in thread.
    uint64_t precise_pc = (uint64_t) exceptionInfo->ExceptionRecord->ExceptionAddress;
    // Convert precise PC into "Unix" format
    precise_pc = (precise_pc & 0xFFFFFFFFFFFFFFF0) | ((precise_pc & 0xF) >> 2);
    thread->set_saved_exception_pc((address)precise_pc);
  }
  // Set pc to handler
  exceptionInfo->ContextRecord->StIIP = (DWORD64)handler;
  // Clear out psr.ri (= Restart Instruction) in order to continue
  // at the beginning of the target bundle.
  exceptionInfo->ContextRecord->StIPSR &= 0xFFFFF9FFFFFFFFFF;
  assert(((DWORD64)handler & 0xF) == 0, "Target address must point to the beginning of a bundle!");
#elif _M_AMD64
  // Do not blow up if no thread info available.
  if (thread) {
    thread->set_saved_exception_pc((address)(DWORD_PTR)exceptionInfo->ContextRecord->Rip);
  }
  // Set pc to handler
  exceptionInfo->ContextRecord->Rip = (DWORD64)handler;
#else
  // Do not blow up if no thread info available.
  if (thread) {
    thread->set_saved_exception_pc((address)(DWORD_PTR)exceptionInfo->ContextRecord->Eip);
  }
  // Set pc to handler
  exceptionInfo->ContextRecord->Eip = (DWORD)(DWORD_PTR)handler;
#endif

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

// Handle NAT Bit consumption on IA64.
#ifdef _M_IA64
#define EXCEPTION_REG_NAT_CONSUMPTION    STATUS_REG_NAT_CONSUMPTION
#endif

// Windows Vista/2008 heap corruption check
#define EXCEPTION_HEAP_CORRUPTION        0xC0000374

#define def_excpt(val) #val, val

struct siglabel {
  char *name;
  int   number;
};

// All Visual C++ exceptions thrown from code generated by the Microsoft Visual
// C++ compiler contain this error code. Because this is a compiler-generated
// error, the code is not listed in the Win32 API header files.
// The code is actually a cryptic mnemonic device, with the initial "E"
// standing for "exception" and the final 3 bytes (0x6D7363) representing the
// ASCII values of "msc".

#define EXCEPTION_UNCAUGHT_CXX_EXCEPTION    0xE06D7363


struct siglabel exceptlabels[] = {
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
    def_excpt(EXCEPTION_HEAP_CORRUPTION),
#ifdef _M_IA64
    def_excpt(EXCEPTION_REG_NAT_CONSUMPTION),
#endif
    NULL, 0
};

const char* os::exception_name(int exception_code, char *buf, size_t size) {
  for (int i = 0; exceptlabels[i].name != NULL; i++) {
    if (exceptlabels[i].number == exception_code) {
       jio_snprintf(buf, size, "%s", exceptlabels[i].name);
       return buf;
    }
  }

  return NULL;
}

//-----------------------------------------------------------------------------
LONG Handle_IDiv_Exception(struct _EXCEPTION_POINTERS* exceptionInfo) {
  // handle exception caused by idiv; should only happen for -MinInt/-1
  // (division by zero is handled explicitly)
#ifdef _M_IA64
  assert(0, "Fix Handle_IDiv_Exception");
#elif _M_AMD64
  PCONTEXT ctx = exceptionInfo->ContextRecord;
  address pc = (address)ctx->Rip;
  assert(pc[0] == 0xF7, "not an idiv opcode");
  assert((pc[1] & ~0x7) == 0xF8, "cannot handle non-register operands");
  assert(ctx->Rax == min_jint, "unexpected idiv exception");
  // set correct result values and continue after idiv instruction
  ctx->Rip = (DWORD)pc + 2;        // idiv reg, reg  is 2 bytes
  ctx->Rax = (DWORD)min_jint;      // result
  ctx->Rdx = (DWORD)0;             // remainder
  // Continue the execution
#else
  PCONTEXT ctx = exceptionInfo->ContextRecord;
  address pc = (address)ctx->Eip;
  assert(pc[0] == 0xF7, "not an idiv opcode");
  assert((pc[1] & ~0x7) == 0xF8, "cannot handle non-register operands");
  assert(ctx->Eax == min_jint, "unexpected idiv exception");
  // set correct result values and continue after idiv instruction
  ctx->Eip = (DWORD)pc + 2;        // idiv reg, reg  is 2 bytes
  ctx->Eax = (DWORD)min_jint;      // result
  ctx->Edx = (DWORD)0;             // remainder
  // Continue the execution
#endif
  return EXCEPTION_CONTINUE_EXECUTION;
}

#ifndef  _WIN64
//-----------------------------------------------------------------------------
LONG WINAPI Handle_FLT_Exception(struct _EXCEPTION_POINTERS* exceptionInfo) {
  // handle exception caused by native method modifying control word
  PCONTEXT ctx = exceptionInfo->ContextRecord;
  DWORD exception_code = exceptionInfo->ExceptionRecord->ExceptionCode;

  switch (exception_code) {
    case EXCEPTION_FLT_DENORMAL_OPERAND:
    case EXCEPTION_FLT_DIVIDE_BY_ZERO:
    case EXCEPTION_FLT_INEXACT_RESULT:
    case EXCEPTION_FLT_INVALID_OPERATION:
    case EXCEPTION_FLT_OVERFLOW:
    case EXCEPTION_FLT_STACK_CHECK:
    case EXCEPTION_FLT_UNDERFLOW:
      jint fp_control_word = (* (jint*) StubRoutines::addr_fpu_cntrl_wrd_std());
      if (fp_control_word != ctx->FloatSave.ControlWord) {
        // Restore FPCW and mask out FLT exceptions
        ctx->FloatSave.ControlWord = fp_control_word | 0xffffffc0;
        // Mask out pending FLT exceptions
        ctx->FloatSave.StatusWord &=  0xffffff00;
        return EXCEPTION_CONTINUE_EXECUTION;
      }
  }

  if (prev_uef_handler != NULL) {
    // We didn't handle this exception so pass it to the previous
    // UnhandledExceptionFilter.
    return (prev_uef_handler)(exceptionInfo);
  }

  return EXCEPTION_CONTINUE_SEARCH;
}
#else //_WIN64
/*
  On Windows, the mxcsr control bits are non-volatile across calls
  See also CR 6192333
  If EXCEPTION_FLT_* happened after some native method modified
  mxcsr - it is not a jvm fault.
  However should we decide to restore of mxcsr after a faulty
  native method we can uncomment following code
      jint MxCsr = INITIAL_MXCSR;
        // we can't use StubRoutines::addr_mxcsr_std()
        // because in Win64 mxcsr is not saved there
      if (MxCsr != ctx->MxCsr) {
        ctx->MxCsr = MxCsr;
        return EXCEPTION_CONTINUE_EXECUTION;
      }

*/
#endif //_WIN64


// Fatal error reporting is single threaded so we can make this a
// static and preallocated.  If it's more than MAX_PATH silently ignore
// it.
static char saved_error_file[MAX_PATH] = {0};

void os::set_error_file(const char *logfile) {
  if (strlen(logfile) <= MAX_PATH) {
    strncpy(saved_error_file, logfile, MAX_PATH);
  }
}

static inline void report_error(Thread* t, DWORD exception_code,
                                address addr, void* siginfo, void* context) {
  VMError err(t, exception_code, addr, siginfo, context);
  err.report_and_die();

  // If UseOsErrorReporting, this will return here and save the error file
  // somewhere where we can find it in the minidump.
}

//-----------------------------------------------------------------------------
LONG WINAPI topLevelExceptionFilter(struct _EXCEPTION_POINTERS* exceptionInfo) {
  if (InterceptOSException) return EXCEPTION_CONTINUE_SEARCH;
  DWORD exception_code = exceptionInfo->ExceptionRecord->ExceptionCode;
#ifdef _M_IA64
  // On Itanium, we need the "precise pc", which has the slot number coded
  // into the least 4 bits: 0000=slot0, 0100=slot1, 1000=slot2 (Windows format).
  address pc = (address) exceptionInfo->ExceptionRecord->ExceptionAddress;
  // Convert the pc to "Unix format", which has the slot number coded
  // into the least 2 bits: 0000=slot0, 0001=slot1, 0010=slot2
  // This is needed for IA64 because "relocation" / "implicit null check" / "poll instruction"
  // information is saved in the Unix format.
  address pc_unix_format = (address) ((((uint64_t)pc) & 0xFFFFFFFFFFFFFFF0) | ((((uint64_t)pc) & 0xF) >> 2));
#elif _M_AMD64
  address pc = (address) exceptionInfo->ContextRecord->Rip;
#else
  address pc = (address) exceptionInfo->ContextRecord->Eip;
#endif
  Thread* t = ThreadLocalStorage::get_thread_slow();          // slow & steady

#ifndef _WIN64
  // Execution protection violation - win32 running on AMD64 only
  // Handled first to avoid misdiagnosis as a "normal" access violation;
  // This is safe to do because we have a new/unique ExceptionInformation
  // code for this condition.
  if (exception_code == EXCEPTION_ACCESS_VIOLATION) {
    PEXCEPTION_RECORD exceptionRecord = exceptionInfo->ExceptionRecord;
    int exception_subcode = (int) exceptionRecord->ExceptionInformation[0];
    address addr = (address) exceptionRecord->ExceptionInformation[1];

    if (exception_subcode == EXCEPTION_INFO_EXEC_VIOLATION) {
      int page_size = os::vm_page_size();

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
        (align_size_down((intptr_t) pc ^ (intptr_t) addr,
                         (intptr_t) page_size) > 0);

      if (pc == addr || (pc_is_near_addr && instr_spans_page_boundary)) {
        static volatile address last_addr =
          (address) os::non_memory_address_word();

        // In conservative mode, don't unguard unless the address is in the VM
        if (UnguardOnExecutionViolation > 0 && addr != last_addr &&
            (UnguardOnExecutionViolation > 1 || os::address_is_in_vm(addr))) {

          // Set memory to RWX and retry
          address page_start =
            (address) align_size_down((intptr_t) addr, (intptr_t) page_size);
          bool res = os::protect_memory((char*) page_start, page_size,
                                        os::MEM_PROT_RWX);

          if (PrintMiscellaneous && Verbose) {
            char buf[256];
            jio_snprintf(buf, sizeof(buf), "Execution protection violation "
                         "at " INTPTR_FORMAT
                         ", unguarding " INTPTR_FORMAT ": %s", addr,
                         page_start, (res ? "success" : strerror(errno)));
            tty->print_raw_cr(buf);
          }

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
      report_error(t, exception_code, addr, exceptionInfo->ExceptionRecord,
                   exceptionInfo->ContextRecord);
      return EXCEPTION_CONTINUE_SEARCH;
    }
  }
#endif // _WIN64

  // Check to see if we caught the safepoint code in the
  // process of write protecting the memory serialization page.
  // It write enables the page immediately after protecting it
  // so just return.
  if ( exception_code == EXCEPTION_ACCESS_VIOLATION ) {
    JavaThread* thread = (JavaThread*) t;
    PEXCEPTION_RECORD exceptionRecord = exceptionInfo->ExceptionRecord;
    address addr = (address) exceptionRecord->ExceptionInformation[1];
    if ( os::is_memory_serialize_page(thread, addr) ) {
      // Block current thread until the memory serialize page permission restored.
      os::block_on_serialize_page_trap();
      return EXCEPTION_CONTINUE_EXECUTION;
    }
  }

  if (t != NULL && t->is_Java_thread()) {
    JavaThread* thread = (JavaThread*) t;
    bool in_java = thread->thread_state() == _thread_in_Java;

    // Handle potential stack overflows up front.
    if (exception_code == EXCEPTION_STACK_OVERFLOW) {
      if (os::uses_stack_guard_pages()) {
#ifdef _M_IA64
        // Use guard page for register stack.
        PEXCEPTION_RECORD exceptionRecord = exceptionInfo->ExceptionRecord;
        address addr = (address) exceptionRecord->ExceptionInformation[1];
        // Check for a register stack overflow on Itanium
        if (thread->addr_inside_register_stack_red_zone(addr)) {
          // Fatal red zone violation happens if the Java program
          // catches a StackOverflow error and does so much processing
          // that it runs beyond the unprotected yellow guard zone. As
          // a result, we are out of here.
          fatal("ERROR: Unrecoverable stack overflow happened. JVM will exit.");
        } else if(thread->addr_inside_register_stack(addr)) {
          // Disable the yellow zone which sets the state that
          // we've got a stack overflow problem.
          if (thread->stack_yellow_zone_enabled()) {
            thread->disable_stack_yellow_zone();
          }
          // Give us some room to process the exception.
          thread->disable_register_stack_guard();
          // Tracing with +Verbose.
          if (Verbose) {
            tty->print_cr("SOF Compiled Register Stack overflow at " INTPTR_FORMAT " (SIGSEGV)", pc);
            tty->print_cr("Register Stack access at " INTPTR_FORMAT, addr);
            tty->print_cr("Register Stack base " INTPTR_FORMAT, thread->register_stack_base());
            tty->print_cr("Register Stack [" INTPTR_FORMAT "," INTPTR_FORMAT "]",
                          thread->register_stack_base(),
                          thread->register_stack_base() + thread->stack_size());
          }

          // Reguard the permanent register stack red zone just to be sure.
          // We saw Windows silently disabling this without telling us.
          thread->enable_register_stack_red_zone();

          return Handle_Exception(exceptionInfo,
            SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW));
        }
#endif
        if (thread->stack_yellow_zone_enabled()) {
          // Yellow zone violation.  The o/s has unprotected the first yellow
          // zone page for us.  Note:  must call disable_stack_yellow_zone to
          // update the enabled status, even if the zone contains only one page.
          thread->disable_stack_yellow_zone();
          // If not in java code, return and hope for the best.
          return in_java ? Handle_Exception(exceptionInfo,
            SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW))
            :  EXCEPTION_CONTINUE_EXECUTION;
        } else {
          // Fatal red zone violation.
          thread->disable_stack_red_zone();
          tty->print_raw_cr("An unrecoverable stack overflow has occurred.");
          report_error(t, exception_code, pc, exceptionInfo->ExceptionRecord,
                       exceptionInfo->ContextRecord);
          return EXCEPTION_CONTINUE_SEARCH;
        }
      } else if (in_java) {
        // JVM-managed guard pages cannot be used on win95/98.  The o/s provides
        // a one-time-only guard page, which it has released to us.  The next
        // stack overflow on this thread will result in an ACCESS_VIOLATION.
        return Handle_Exception(exceptionInfo,
          SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW));
      } else {
        // Can only return and hope for the best.  Further stack growth will
        // result in an ACCESS_VIOLATION.
        return EXCEPTION_CONTINUE_EXECUTION;
      }
    } else if (exception_code == EXCEPTION_ACCESS_VIOLATION) {
      // Either stack overflow or null pointer exception.
      if (in_java) {
        PEXCEPTION_RECORD exceptionRecord = exceptionInfo->ExceptionRecord;
        address addr = (address) exceptionRecord->ExceptionInformation[1];
        address stack_end = thread->stack_base() - thread->stack_size();
        if (addr < stack_end && addr >= stack_end - os::vm_page_size()) {
          // Stack overflow.
          assert(!os::uses_stack_guard_pages(),
            "should be caught by red zone code above.");
          return Handle_Exception(exceptionInfo,
            SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::STACK_OVERFLOW));
        }
        //
        // Check for safepoint polling and implicit null
        // We only expect null pointers in the stubs (vtable)
        // the rest are checked explicitly now.
        //
        CodeBlob* cb = CodeCache::find_blob(pc);
        if (cb != NULL) {
          if (os::is_poll_address(addr)) {
            address stub = SharedRuntime::get_poll_stub(pc);
            return Handle_Exception(exceptionInfo, stub);
          }
        }
        {
#ifdef _WIN64
          //
          // If it's a legal stack address map the entire region in
          //
          PEXCEPTION_RECORD exceptionRecord = exceptionInfo->ExceptionRecord;
          address addr = (address) exceptionRecord->ExceptionInformation[1];
          if (addr > thread->stack_yellow_zone_base() && addr < thread->stack_base() ) {
                  addr = (address)((uintptr_t)addr &
                         (~((uintptr_t)os::vm_page_size() - (uintptr_t)1)));
                  os::commit_memory((char *)addr, thread->stack_base() - addr,
                                    !ExecMem);
                  return EXCEPTION_CONTINUE_EXECUTION;
          }
          else
#endif
          {
            // Null pointer exception.
#ifdef _M_IA64
            // Process implicit null checks in compiled code. Note: Implicit null checks
            // can happen even if "ImplicitNullChecks" is disabled, e.g. in vtable stubs.
            if (CodeCache::contains((void*) pc_unix_format) && !MacroAssembler::needs_explicit_null_check((intptr_t) addr)) {
              CodeBlob *cb = CodeCache::find_blob_unsafe(pc_unix_format);
              // Handle implicit null check in UEP method entry
              if (cb && (cb->is_frame_complete_at(pc) ||
                         (cb->is_nmethod() && ((nmethod *)cb)->inlinecache_check_contains(pc)))) {
                if (Verbose) {
                  intptr_t *bundle_start = (intptr_t*) ((intptr_t) pc_unix_format & 0xFFFFFFFFFFFFFFF0);
                  tty->print_cr("trap: null_check at " INTPTR_FORMAT " (SIGSEGV)", pc_unix_format);
                  tty->print_cr("      to addr " INTPTR_FORMAT, addr);
                  tty->print_cr("      bundle is " INTPTR_FORMAT " (high), " INTPTR_FORMAT " (low)",
                                *(bundle_start + 1), *bundle_start);
                }
                return Handle_Exception(exceptionInfo,
                  SharedRuntime::continuation_for_implicit_exception(thread, pc_unix_format, SharedRuntime::IMPLICIT_NULL));
              }
            }

            // Implicit null checks were processed above.  Hence, we should not reach
            // here in the usual case => die!
            if (Verbose) tty->print_raw_cr("Access violation, possible null pointer exception");
            report_error(t, exception_code, pc, exceptionInfo->ExceptionRecord,
                         exceptionInfo->ContextRecord);
            return EXCEPTION_CONTINUE_SEARCH;

#else // !IA64

            // Windows 98 reports faulting addresses incorrectly
            if (!MacroAssembler::needs_explicit_null_check((intptr_t)addr) ||
                !os::win32::is_nt()) {
              address stub = SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_NULL);
              if (stub != NULL) return Handle_Exception(exceptionInfo, stub);
            }
            report_error(t, exception_code, pc, exceptionInfo->ExceptionRecord,
                         exceptionInfo->ContextRecord);
            return EXCEPTION_CONTINUE_SEARCH;
#endif
          }
        }
      }

#ifdef _WIN64
      // Special care for fast JNI field accessors.
      // jni_fast_Get<Primitive>Field can trap at certain pc's if a GC kicks
      // in and the heap gets shrunk before the field access.
      if (exception_code == EXCEPTION_ACCESS_VIOLATION) {
        address addr = JNI_FastGetField::find_slowcase_pc(pc);
        if (addr != (address)-1) {
          return Handle_Exception(exceptionInfo, addr);
        }
      }
#endif

      // Stack overflow or null pointer exception in native code.
      report_error(t, exception_code, pc, exceptionInfo->ExceptionRecord,
                   exceptionInfo->ContextRecord);
      return EXCEPTION_CONTINUE_SEARCH;
    } // /EXCEPTION_ACCESS_VIOLATION
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
#if defined _M_IA64
    else if ((exception_code == EXCEPTION_ILLEGAL_INSTRUCTION ||
              exception_code == EXCEPTION_ILLEGAL_INSTRUCTION_2)) {
      M37 handle_wrong_method_break(0, NativeJump::HANDLE_WRONG_METHOD, PR0);

      // Compiled method patched to be non entrant? Following conditions must apply:
      // 1. must be first instruction in bundle
      // 2. must be a break instruction with appropriate code
      if((((uint64_t) pc & 0x0F) == 0) &&
         (((IPF_Bundle*) pc)->get_slot0() == handle_wrong_method_break.bits())) {
        return Handle_Exception(exceptionInfo,
                                (address)SharedRuntime::get_handle_wrong_method_stub());
      }
    } // /EXCEPTION_ILLEGAL_INSTRUCTION
#endif


    if (in_java) {
      switch (exception_code) {
      case EXCEPTION_INT_DIVIDE_BY_ZERO:
        return Handle_Exception(exceptionInfo, SharedRuntime::continuation_for_implicit_exception(thread, pc, SharedRuntime::IMPLICIT_DIVIDE_BY_ZERO));

      case EXCEPTION_INT_OVERFLOW:
        return Handle_IDiv_Exception(exceptionInfo);

      } // switch
    }
#ifndef _WIN64
    if (((thread->thread_state() == _thread_in_Java) ||
        (thread->thread_state() == _thread_in_native)) &&
        exception_code != EXCEPTION_UNCAUGHT_CXX_EXCEPTION)
    {
      LONG result=Handle_FLT_Exception(exceptionInfo);
      if (result==EXCEPTION_CONTINUE_EXECUTION) return result;
    }
#endif //_WIN64
  }

  if (exception_code != EXCEPTION_BREAKPOINT) {
    report_error(t, exception_code, pc, exceptionInfo->ExceptionRecord,
                 exceptionInfo->ContextRecord);
  }
  return EXCEPTION_CONTINUE_SEARCH;
}

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

#define DEFINE_FAST_GETFIELD(Return,Fieldname,Result) \
Return JNICALL jni_fast_Get##Result##Field_wrapper(JNIEnv *env, jobject obj, jfieldID fieldID) { \
  __try { \
    return (*JNI_FastGetField::jni_fast_Get##Result##Field_fp)(env, obj, fieldID); \
  } __except(fastJNIAccessorExceptionFilter((_EXCEPTION_POINTERS*)_exception_info())) { \
  } \
  return 0; \
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

int os::vm_page_size() { return os::win32::vm_page_size(); }
int os::vm_allocation_granularity() {
  return os::win32::vm_allocation_granularity();
}

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

static HANDLE    _hProcess;
static HANDLE    _hToken;

// Container for NUMA node list info
class NUMANodeListHolder {
private:
  int *_numa_used_node_list;  // allocated below
  int _numa_used_node_count;

  void free_node_list() {
    if (_numa_used_node_list != NULL) {
      FREE_C_HEAP_ARRAY(int, _numa_used_node_list, mtInternal);
    }
  }

public:
  NUMANodeListHolder() {
    _numa_used_node_count = 0;
    _numa_used_node_list = NULL;
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
    if (!os::Kernel32Dll::GetNumaHighestNodeNumber(&highest_node_number)) return false;
    free_node_list();
    _numa_used_node_list = NEW_C_HEAP_ARRAY(int, highest_node_number + 1, mtInternal);
    for (unsigned int i = 0; i <= highest_node_number; i++) {
      ULONGLONG proc_mask_numa_node;
      if (!os::Kernel32Dll::GetNumaNodeProcessorMask(i, &proc_mask_numa_node)) return false;
      if ((proc_aff_mask & proc_mask_numa_node)!=0) {
        _numa_used_node_list[_numa_used_node_count++] = i;
      }
    }
    return (_numa_used_node_count > 1);
  }

  int get_count() {return _numa_used_node_count;}
  int get_node_list_entry(int n) {
    // for indexes out of range, returns -1
    return (n < _numa_used_node_count ? _numa_used_node_list[n] : -1);
  }

} numa_node_list_holder;



static size_t _large_page_size = 0;

static bool resolve_functions_for_large_page_init() {
  return os::Kernel32Dll::GetLargePageMinimumAvailable() &&
    os::Advapi32Dll::AdvapiAvailable();
}

static bool request_lock_memory_privilege() {
  _hProcess = OpenProcess(PROCESS_QUERY_INFORMATION, FALSE,
                                os::current_process_id());

  LUID luid;
  if (_hProcess != NULL &&
      os::Advapi32Dll::OpenProcessToken(_hProcess, TOKEN_ADJUST_PRIVILEGES, &_hToken) &&
      os::Advapi32Dll::LookupPrivilegeValue(NULL, "SeLockMemoryPrivilege", &luid)) {

    TOKEN_PRIVILEGES tp;
    tp.PrivilegeCount = 1;
    tp.Privileges[0].Luid = luid;
    tp.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;

    // AdjustTokenPrivileges() may return TRUE even when it couldn't change the
    // privilege. Check GetLastError() too. See MSDN document.
    if (os::Advapi32Dll::AdjustTokenPrivileges(_hToken, false, &tp, sizeof(tp), NULL, NULL) &&
        (GetLastError() == ERROR_SUCCESS)) {
      return true;
    }
  }

  return false;
}

static void cleanup_after_large_page_init() {
  if (_hProcess) CloseHandle(_hProcess);
  _hProcess = NULL;
  if (_hToken) CloseHandle(_hToken);
  _hToken = NULL;
}

static bool numa_interleaving_init() {
  bool success = false;
  bool use_numa_interleaving_specified = !FLAG_IS_DEFAULT(UseNUMAInterleaving);

  // print a warning if UseNUMAInterleaving flag is specified on command line
  bool warn_on_failure = use_numa_interleaving_specified;
# define WARN(msg) if (warn_on_failure) { warning(msg); }

  // NUMAInterleaveGranularity cannot be less than vm_allocation_granularity (or _large_page_size if using large pages)
  size_t min_interleave_granularity = UseLargePages ? _large_page_size : os::vm_allocation_granularity();
  NUMAInterleaveGranularity = align_size_up(NUMAInterleaveGranularity, min_interleave_granularity);

  if (os::Kernel32Dll::NumaCallsAvailable()) {
    if (numa_node_list_holder.build()) {
      if (PrintMiscellaneous && Verbose) {
        tty->print("NUMA UsedNodeCount=%d, namely ", numa_node_list_holder.get_count());
        for (int i = 0; i < numa_node_list_holder.get_count(); i++) {
          tty->print("%d ", numa_node_list_holder.get_node_list_entry(i));
        }
        tty->print("\n");
      }
      success = true;
    } else {
      WARN("Process does not cover multiple NUMA nodes.");
    }
  } else {
    WARN("NUMA Interleaving is not supported by the operating system.");
  }
  if (!success) {
    if (use_numa_interleaving_specified) WARN("...Ignoring UseNUMAInterleaving flag.");
  }
  return success;
#undef WARN
}

// this routine is used whenever we need to reserve a contiguous VA range
// but we need to make separate VirtualAlloc calls for each piece of the range
// Reasons for doing this:
//  * UseLargePagesIndividualAllocation was set (normally only needed on WS2003 but possible to be set otherwise)
//  * UseNUMAInterleaving requires a separate node for each piece
static char* allocate_pages_individually(size_t bytes, char* addr, DWORD flags, DWORD prot,
                                         bool should_inject_error=false) {
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
    return NULL;
  }
  p_buf = (char *) VirtualAlloc(addr,
                                size_of_reserve,  // size of Reserve
                                MEM_RESERVE,
                                PAGE_READWRITE);
  // If reservation failed, return NULL
  if (p_buf == NULL) return NULL;
  MemTracker::record_virtual_memory_reserve((address)p_buf, size_of_reserve, mtNone, CALLER_PC);
  os::release_memory(p_buf, bytes + chunk_size);

  // we still need to round up to a page boundary (in case we are using large pages)
  // but not to a chunk boundary (in case InterleavingGranularity doesn't align with page size)
  // instead we handle this in the bytes_to_rq computation below
  p_buf = (char *) align_size_up((size_t)p_buf, page_size);

  // now go through and allocate one chunk at a time until all bytes are
  // allocated
  size_t  bytes_remaining = bytes;
  // An overflow of align_size_up() would have been caught above
  // in the calculation of size_of_reserve.
  char * next_alloc_addr = p_buf;
  HANDLE hProc = GetCurrentProcess();

#ifdef ASSERT
  // Variable for the failure injection
  long ran_num = os::random();
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
      p_new = NULL;
    } else {
      if (!UseNUMAInterleaving) {
        p_new = (char *) VirtualAlloc(next_alloc_addr,
                                      bytes_to_rq,
                                      flags,
                                      prot);
      } else {
        // get the next node to use from the used_node_list
        assert(numa_node_list_holder.get_count() > 0, "Multiple NUMA nodes expected");
        DWORD node = numa_node_list_holder.get_node_list_entry(count % numa_node_list_holder.get_count());
        p_new = (char *)os::Kernel32Dll::VirtualAllocExNuma(hProc,
                                                            next_alloc_addr,
                                                            bytes_to_rq,
                                                            flags,
                                                            prot,
                                                            node);
      }
    }

    if (p_new == NULL) {
      // Free any allocated pages
      if (next_alloc_addr > p_buf) {
        // Some memory was committed so release it.
        size_t bytes_to_release = bytes - bytes_remaining;
        // NMT has yet to record any individual blocks, so it
        // need to create a dummy 'reserve' record to match
        // the release.
        MemTracker::record_virtual_memory_reserve((address)p_buf,
          bytes_to_release, mtNone, CALLER_PC);
        os::release_memory(p_buf, bytes_to_release);
      }
#ifdef ASSERT
      if (should_inject_error) {
        if (TracePageSizes && Verbose) {
          tty->print_cr("Reserving pages individually failed.");
        }
      }
#endif
      return NULL;
    }

    bytes_remaining -= bytes_to_rq;
    next_alloc_addr += bytes_to_rq;
    count++;
  }
  // Although the memory is allocated individually, it is returned as one.
  // NMT records it as one block.
  address pc = CALLER_PC;
  if ((flags & MEM_COMMIT) != 0) {
    MemTracker::record_virtual_memory_reserve_and_commit((address)p_buf, bytes, mtNone, pc);
  } else {
    MemTracker::record_virtual_memory_reserve((address)p_buf, bytes, mtNone, pc);
  }

  // made it this far, success
  return p_buf;
}



void os::large_page_init() {
  if (!UseLargePages) return;

  // print a warning if any large page related flag is specified on command line
  bool warn_on_failure = !FLAG_IS_DEFAULT(UseLargePages) ||
                         !FLAG_IS_DEFAULT(LargePageSizeInBytes);
  bool success = false;

# define WARN(msg) if (warn_on_failure) { warning(msg); }
  if (resolve_functions_for_large_page_init()) {
    if (request_lock_memory_privilege()) {
      size_t s = os::Kernel32Dll::GetLargePageMinimum();
      if (s) {
#if defined(IA32) || defined(AMD64)
        if (s > 4*M || LargePageSizeInBytes > 4*M) {
          WARN("JVM cannot use large pages bigger than 4mb.");
        } else {
#endif
          if (LargePageSizeInBytes && LargePageSizeInBytes % s == 0) {
            _large_page_size = LargePageSizeInBytes;
          } else {
            _large_page_size = s;
          }
          success = true;
#if defined(IA32) || defined(AMD64)
        }
#endif
      } else {
        WARN("Large page is not supported by the processor.");
      }
    } else {
      WARN("JVM cannot use large page memory because it does not have enough privilege to lock pages in memory.");
    }
  } else {
    WARN("Large page is not supported by the operating system.");
  }
#undef WARN

  const size_t default_page_size = (size_t) vm_page_size();
  if (success && _large_page_size > default_page_size) {
    _page_sizes[0] = _large_page_size;
    _page_sizes[1] = default_page_size;
    _page_sizes[2] = 0;
  }

  cleanup_after_large_page_init();
  UseLargePages = success;
}

// On win32, one cannot release just a part of reserved memory, it's an
// all or nothing deal.  When we split a reservation, we must break the
// reservation into two reservations.
void os::pd_split_reserved_memory(char *base, size_t size, size_t split,
                              bool realloc) {
  if (size > 0) {
    release_memory(base, size);
    if (realloc) {
      reserve_memory(split, base);
    }
    if (size != split) {
      reserve_memory(size - split, base + split);
    }
  }
}

// Multiple threads can race in this code but it's not possible to unmap small sections of
// virtual space to get requested alignment, like posix-like os's.
// Windows prevents multiple thread from remapping over each other so this loop is thread-safe.
char* os::reserve_memory_aligned(size_t size, size_t alignment) {
  assert((alignment & (os::vm_allocation_granularity() - 1)) == 0,
      "Alignment must be a multiple of allocation granularity (page size)");
  assert((size & (alignment -1)) == 0, "size must be 'alignment' aligned");

  size_t extra_size = size + alignment;
  assert(extra_size >= size, "overflow, size is too large to allow alignment");

  char* aligned_base = NULL;

  do {
    char* extra_base = os::reserve_memory(extra_size, NULL, alignment);
    if (extra_base == NULL) {
      return NULL;
    }
    // Do manual alignment
    aligned_base = (char*) align_size_up((uintptr_t) extra_base, alignment);

    os::release_memory(extra_base, extra_size);

    aligned_base = os::reserve_memory(size, aligned_base);

  } while (aligned_base == NULL);

  return aligned_base;
}

char* os::pd_reserve_memory(size_t bytes, char* addr, size_t alignment_hint) {
  assert((size_t)addr % os::vm_allocation_granularity() == 0,
         "reserve alignment");
  assert(bytes % os::vm_allocation_granularity() == 0, "reserve block size");
  char* res;
  // note that if UseLargePages is on, all the areas that require interleaving
  // will go thru reserve_memory_special rather than thru here.
  bool use_individual = (UseNUMAInterleaving && !UseLargePages);
  if (!use_individual) {
    res = (char*)VirtualAlloc(addr, bytes, MEM_RESERVE, PAGE_READWRITE);
  } else {
    elapsedTimer reserveTimer;
    if( Verbose && PrintMiscellaneous ) reserveTimer.start();
    // in numa interleaving, we have to allocate pages individually
    // (well really chunks of NUMAInterleaveGranularity size)
    res = allocate_pages_individually(bytes, addr, MEM_RESERVE, PAGE_READWRITE);
    if (res == NULL) {
      warning("NUMA page allocation failed");
    }
    if( Verbose && PrintMiscellaneous ) {
      reserveTimer.stop();
      tty->print_cr("reserve_memory of %Ix bytes took " JLONG_FORMAT " ms (" JLONG_FORMAT " ticks)", bytes,
                    reserveTimer.milliseconds(), reserveTimer.ticks());
    }
  }
  assert(res == NULL || addr == NULL || addr == res,
         "Unexpected address from reserve.");

  return res;
}

// Reserve memory at an arbitrary address, only if that area is
// available (and not reserved for something else).
char* os::pd_attempt_reserve_memory_at(size_t bytes, char* requested_addr) {
  // Windows os::reserve_memory() fails of the requested address range is
  // not avilable.
  return reserve_memory(bytes, requested_addr);
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

bool os::can_execute_large_page_memory() {
  return true;
}

char* os::reserve_memory_special(size_t bytes, char* addr, bool exec) {

  const DWORD prot = exec ? PAGE_EXECUTE_READWRITE : PAGE_READWRITE;
  const DWORD flags = MEM_RESERVE | MEM_COMMIT | MEM_LARGE_PAGES;

  // with large pages, there are two cases where we need to use Individual Allocation
  // 1) the UseLargePagesIndividualAllocation flag is set (set by default on WS2003)
  // 2) NUMA Interleaving is enabled, in which case we use a different node for each page
  if (UseLargePagesIndividualAllocation || UseNUMAInterleaving) {
    if (TracePageSizes && Verbose) {
       tty->print_cr("Reserving large pages individually.");
    }
    char * p_buf = allocate_pages_individually(bytes, addr, flags, prot, LargePagesIndividualAllocationInjectError);
    if (p_buf == NULL) {
      // give an appropriate warning message
      if (UseNUMAInterleaving) {
        warning("NUMA large page allocation failed, UseLargePages flag ignored");
      }
      if (UseLargePagesIndividualAllocation) {
        warning("Individually allocated large pages failed, "
                "use -XX:-UseLargePagesIndividualAllocation to turn off");
      }
      return NULL;
    }

    return p_buf;

  } else {
    // normal policy just allocate it all at once
    DWORD flag = MEM_RESERVE | MEM_COMMIT | MEM_LARGE_PAGES;
    char * res = (char *)VirtualAlloc(NULL, bytes, flag, prot);
    if (res != NULL) {
      address pc = CALLER_PC;
      MemTracker::record_virtual_memory_reserve_and_commit((address)res, bytes, mtNone, pc);
    }

    return res;
  }
}

bool os::release_memory_special(char* base, size_t bytes) {
  assert(base != NULL, "Sanity check");
  return release_memory(base, bytes);
}

void os::print_statistics() {
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
    if (VirtualAlloc(addr, bytes, MEM_COMMIT, PAGE_READWRITE) == NULL) {
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
      if (VirtualAlloc(next_alloc_addr, bytes_to_rq, MEM_COMMIT,
                       PAGE_READWRITE) == NULL) {
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
  assert(mesg != NULL, "mesg must be specified");
  if (!pd_commit_memory(addr, size, exec)) {
    warn_fail_commit_memory(addr, size, exec);
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, mesg);
  }
}

void os::pd_commit_memory_or_exit(char* addr, size_t size,
                                  size_t alignment_hint, bool exec,
                                  const char* mesg) {
  // alignment_hint is ignored on this OS
  pd_commit_memory_or_exit(addr, size, exec, mesg);
}

bool os::pd_uncommit_memory(char* addr, size_t bytes) {
  if (bytes == 0) {
    // Don't bother the OS with noops.
    return true;
  }
  assert((size_t) addr % os::vm_page_size() == 0, "uncommit on page boundaries");
  assert(bytes % os::vm_page_size() == 0, "uncommit in page-sized chunks");
  return (VirtualFree(addr, bytes, MEM_DECOMMIT) != 0);
}

bool os::pd_release_memory(char* addr, size_t bytes) {
  return VirtualFree(addr, 0, MEM_RELEASE) != 0;
}

bool os::pd_create_stack_guard_pages(char* addr, size_t size) {
  return os::commit_memory(addr, size, !ExecMem);
}

bool os::remove_stack_guard_pages(char* addr, size_t size) {
  return os::uncommit_memory(addr, size);
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
  return VirtualProtect(addr, bytes, p, &old_status) != 0;
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
void os::numa_make_global(char *addr, size_t bytes)    { }
void os::numa_make_local(char *addr, size_t bytes, int lgrp_hint)    { }
bool os::numa_topology_changed()                       { return false; }
size_t os::numa_get_groups_num()                       { return MAX2(numa_node_list_holder.get_count(), 1); }
int os::numa_get_group_id()                            { return 0; }
size_t os::numa_get_leaf_groups(int *ids, size_t size) {
  if (numa_node_list_holder.get_count() == 0 && size > 0) {
    // Provide an answer for UMA systems
    ids[0] = 0;
    return 1;
  } else {
    // check for size bigger than actual groups_num
    size = MIN2(size, numa_get_groups_num());
    for (int i = 0; i < (int)size; i++) {
      ids[i] = numa_node_list_holder.get_node_list_entry(i);
    }
    return size;
  }
}

bool os::get_page_info(char *start, page_info* info) {
  return false;
}

char *os::scan_pages(char *start, char* end, page_info* page_expected, page_info* page_found) {
  return end;
}

char* os::non_memory_address_word() {
  // Must never look like an address returned by reserve_memory,
  // even in its subfields (as defined by the CPU immediate fields,
  // if the CPU splits constants across multiple instructions).
  return (char*)-1;
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

int os::sleep(Thread* thread, jlong ms, bool interruptable) {
  jlong limit = (jlong) MAXDWORD;

  while(ms > limit) {
    int res;
    if ((res = sleep(thread, limit, interruptable)) != OS_TIMEOUT)
      return res;
    ms -= limit;
  }

  assert(thread == Thread::current(),  "thread consistency check");
  OSThread* osthread = thread->osthread();
  OSThreadWaitState osts(osthread, false /* not Object.wait() */);
  int result;
  if (interruptable) {
    assert(thread->is_Java_thread(), "must be java thread");
    JavaThread *jt = (JavaThread *) thread;
    ThreadBlockInVM tbivm(jt);

    jt->set_suspend_equivalent();
    // cleared by handle_special_suspend_equivalent_condition() or
    // java_suspend_self() via check_and_wait_while_suspended()

    HANDLE events[1];
    events[0] = osthread->interrupt_event();
    HighResolutionInterval *phri=NULL;
    if(!ForceTimeHighResolution)
      phri = new HighResolutionInterval( ms );
    if (WaitForMultipleObjects(1, events, FALSE, (DWORD)ms) == WAIT_TIMEOUT) {
      result = OS_TIMEOUT;
    } else {
      ResetEvent(osthread->interrupt_event());
      osthread->set_interrupted(false);
      result = OS_INTRPT;
    }
    delete phri; //if it is NULL, harmless

    // were we externally suspended while we were waiting?
    jt->check_and_wait_while_suspended();
  } else {
    assert(!thread->is_Java_thread(), "must not be java thread");
    Sleep((long) ms);
    result = OS_TIMEOUT;
  }
  return result;
}

// Sleep forever; naked call to OS-specific sleep; use with CAUTION
void os::infinite_sleep() {
  while (true) {    // sleep forever ...
    Sleep(100000);  // ... 100 seconds at a time
  }
}

typedef BOOL (WINAPI * STTSignature)(void) ;

os::YieldResult os::NakedYield() {
  // Use either SwitchToThread() or Sleep(0)
  // Consider passing back the return value from SwitchToThread().
  if (os::Kernel32Dll::SwitchToThreadAvailable()) {
    return SwitchToThread() ? os::YIELD_SWITCHED : os::YIELD_NONEREADY ;
  } else {
    Sleep(0);
  }
  return os::YIELD_UNKNOWN ;
}

void os::yield() {  os::NakedYield(); }

void os::yield_all(int attempts) {
  // Yields to all threads, including threads with lower priorities
  Sleep(1);
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
    os::java_to_os_priority[MaxPriority] = os::java_to_os_priority[CriticalPriority] ;
  }
  return 0;
}

OSReturn os::set_native_priority(Thread* thread, int priority) {
  if (!UseThreadPriorities) return OS_OK;
  bool ret = SetThreadPriority(thread->osthread()->thread_handle(), priority) != 0;
  return ret ? OS_OK : OS_ERR;
}

OSReturn os::get_native_priority(const Thread* const thread, int* priority_ptr) {
  if ( !UseThreadPriorities ) {
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


// Hint to the underlying OS that a task switch would not be good.
// Void return because it's a hint and can fail.
void os::hint_no_preempt() {}

void os::interrupt(Thread* thread) {
  assert(!thread->is_Java_thread() || Thread::current() == thread || Threads_lock->owned_by_self(),
         "possibility of dangling Thread pointer");

  OSThread* osthread = thread->osthread();
  osthread->set_interrupted(true);
  // More than one thread can get here with the same value of osthread,
  // resulting in multiple notifications.  We do, however, want the store
  // to interrupted() to be visible to other threads before we post
  // the interrupt event.
  OrderAccess::release();
  SetEvent(osthread->interrupt_event());
  // For JSR166:  unpark after setting status
  if (thread->is_Java_thread())
    ((JavaThread*)thread)->parker()->unpark();

  ParkEvent * ev = thread->_ParkEvent ;
  if (ev != NULL) ev->unpark() ;

}


bool os::is_interrupted(Thread* thread, bool clear_interrupted) {
  assert(!thread->is_Java_thread() || Thread::current() == thread || Threads_lock->owned_by_self(),
         "possibility of dangling Thread pointer");

  OSThread* osthread = thread->osthread();
  bool interrupted = osthread->interrupted();
  // There is no synchronization between the setting of the interrupt
  // and it being cleared here. It is critical - see 6535709 - that
  // we only clear the interrupt state, and reset the interrupt event,
  // if we are going to report that we were indeed interrupted - else
  // an interrupt can be "lost", leading to spurious wakeups or lost wakeups
  // depending on the timing
  if (interrupted && clear_interrupted) {
    osthread->set_interrupted(false);
    ResetEvent(osthread->interrupt_event());
  } // Otherwise leave the interrupted state alone

  return interrupted;
}

// Get's a pc (hint) for a running thread. Currently used only for profiling.
ExtendedPC os::get_thread_pc(Thread* thread) {
  CONTEXT context;
  context.ContextFlags = CONTEXT_CONTROL;
  HANDLE handle = thread->osthread()->thread_handle();
#ifdef _M_IA64
  assert(0, "Fix get_thread_pc");
  return ExtendedPC(NULL);
#else
  if (GetThreadContext(handle, &context)) {
#ifdef _M_AMD64
    return ExtendedPC((address) context.Rip);
#else
    return ExtendedPC((address) context.Eip);
#endif
  } else {
    return ExtendedPC(NULL);
  }
#endif
}

// GetCurrentThreadId() returns DWORD
intx os::current_thread_id()          { return GetCurrentThreadId(); }

static int _initial_pid = 0;

int os::current_process_id()
{
  return (_initial_pid ? _initial_pid : _getpid());
}

int    os::win32::_vm_page_size       = 0;
int    os::win32::_vm_allocation_granularity = 0;
int    os::win32::_processor_type     = 0;
// Processor level is not available on non-NT systems, use vm_version instead
int    os::win32::_processor_level    = 0;
julong os::win32::_physical_memory    = 0;
size_t os::win32::_default_stack_size = 0;

         intx os::win32::_os_thread_limit    = 0;
volatile intx os::win32::_os_thread_count    = 0;

bool   os::win32::_is_nt              = false;
bool   os::win32::_is_windows_2003    = false;
bool   os::win32::_is_windows_server  = false;

void os::win32::initialize_system_info() {
  SYSTEM_INFO si;
  GetSystemInfo(&si);
  _vm_page_size    = si.dwPageSize;
  _vm_allocation_granularity = si.dwAllocationGranularity;
  _processor_type  = si.dwProcessorType;
  _processor_level = si.wProcessorLevel;
  set_processor_count(si.dwNumberOfProcessors);

  MEMORYSTATUSEX ms;
  ms.dwLength = sizeof(ms);

  // also returns dwAvailPhys (free physical memory bytes), dwTotalVirtual, dwAvailVirtual,
  // dwMemoryLoad (% of memory in use)
  GlobalMemoryStatusEx(&ms);
  _physical_memory = ms.ullTotalPhys;

  OSVERSIONINFOEX oi;
  oi.dwOSVersionInfoSize = sizeof(OSVERSIONINFOEX);
  GetVersionEx((OSVERSIONINFO*)&oi);
  switch(oi.dwPlatformId) {
    case VER_PLATFORM_WIN32_WINDOWS: _is_nt = false; break;
    case VER_PLATFORM_WIN32_NT:
      _is_nt = true;
      {
        int os_vers = oi.dwMajorVersion * 1000 + oi.dwMinorVersion;
        if (os_vers == 5002) {
          _is_windows_2003 = true;
        }
        if (oi.wProductType == VER_NT_DOMAIN_CONTROLLER ||
          oi.wProductType == VER_NT_SERVER) {
            _is_windows_server = true;
        }
      }
      break;
    default: fatal("Unknown platform");
  }

  _default_stack_size = os::current_stack_size();
  assert(_default_stack_size > (size_t) _vm_page_size, "invalid stack size");
  assert((_default_stack_size & (_vm_page_size - 1)) == 0,
    "stack size not a multiple of page size");

  initialize_performance_counter();

  // Win95/Win98 scheduler bug work-around. The Win95/98 scheduler is
  // known to deadlock the system, if the VM issues to thread operations with
  // a too high frequency, e.g., such as changing the priorities.
  // The 6000 seems to work well - no deadlocks has been notices on the test
  // programs that we have seen experience this problem.
  if (!os::win32::is_nt()) {
    StarvationMonitorInterval = 6000;
  }
}


HINSTANCE os::win32::load_Windows_dll(const char* name, char *ebuf, int ebuflen) {
  char path[MAX_PATH];
  DWORD size;
  DWORD pathLen = (DWORD)sizeof(path);
  HINSTANCE result = NULL;

  // only allow library name without path component
  assert(strchr(name, '\\') == NULL, "path not allowed");
  assert(strchr(name, ':') == NULL, "path not allowed");
  if (strchr(name, '\\') != NULL || strchr(name, ':') != NULL) {
    jio_snprintf(ebuf, ebuflen,
      "Invalid parameter while calling os::win32::load_windows_dll(): cannot take path: %s", name);
    return NULL;
  }

  // search system directory
  if ((size = GetSystemDirectory(path, pathLen)) > 0) {
    strcat(path, "\\");
    strcat(path, name);
    if ((result = (HINSTANCE)os::dll_load(path, ebuf, ebuflen)) != NULL) {
      return result;
    }
  }

  // try Windows directory
  if ((size = GetWindowsDirectory(path, pathLen)) > 0) {
    strcat(path, "\\");
    strcat(path, name);
    if ((result = (HINSTANCE)os::dll_load(path, ebuf, ebuflen)) != NULL) {
      return result;
    }
  }

  jio_snprintf(ebuf, ebuflen,
    "os::win32::load_windows_dll() cannot load %s from system directories.", name);
  return NULL;
}

void os::win32::setmode_streams() {
  _setmode(_fileno(stdin), _O_BINARY);
  _setmode(_fileno(stdout), _O_BINARY);
  _setmode(_fileno(stderr), _O_BINARY);
}


bool os::is_debugger_attached() {
  return IsDebuggerPresent() ? true : false;
}


void os::wait_for_keypress_at_exit(void) {
  if (PauseAtExit) {
    fprintf(stderr, "Press any key to continue...\n");
    fgetc(stdin);
  }
}


int os::message_box(const char* title, const char* message) {
  int result = MessageBox(NULL, message, title,
                          MB_YESNO | MB_ICONERROR | MB_SYSTEMMODAL | MB_DEFAULT_DESKTOP_ONLY);
  return result == IDYES;
}

int os::allocate_thread_local_storage() {
  return TlsAlloc();
}


void os::free_thread_local_storage(int index) {
  TlsFree(index);
}


void os::thread_local_storage_at_put(int index, void* value) {
  TlsSetValue(index, value);
  assert(thread_local_storage_at(index) == value, "Just checking");
}


void* os::thread_local_storage_at(int index) {
  return TlsGetValue(index);
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

// this is called _before_ the global arguments have been parsed
void os::init(void) {
  _initial_pid = _getpid();

  init_random(1234567);

  win32::initialize_system_info();
  win32::setmode_streams();
  init_page_sizes((size_t) win32::vm_page_size());

  // For better scalability on MP systems (must be called after initialize_system_info)
#ifndef PRODUCT
  if (is_MP()) {
    NoYieldsInMicrolock = true;
  }
#endif
  // This may be overridden later when argument processing is done.
  FLAG_SET_ERGO(bool, UseLargePagesIndividualAllocation,
    os::win32::is_windows_2003());

  // Initialize main_process and main_thread
  main_process = GetCurrentProcess();  // Remember main_process is a pseudo handle
 if (!DuplicateHandle(main_process, GetCurrentThread(), main_process,
                       &main_thread, THREAD_ALL_ACCESS, false, 0)) {
    fatal("DuplicateHandle failed\n");
  }
  main_thread_id = (int) GetCurrentThreadId();
}

// To install functions for atexit processing
extern "C" {
  static void perfMemory_exit_helper() {
    perfMemory_exit();
  }
}

static jint initSock();

// this is called _after_ the global arguments have been parsed
jint os::init_2(void) {
  // Allocate a single page and mark it as readable for safepoint polling
  address polling_page = (address)VirtualAlloc(NULL, os::vm_page_size(), MEM_RESERVE, PAGE_READONLY);
  guarantee( polling_page != NULL, "Reserve Failed for polling page");

  address return_page  = (address)VirtualAlloc(polling_page, os::vm_page_size(), MEM_COMMIT, PAGE_READONLY);
  guarantee( return_page != NULL, "Commit Failed for polling page");

  os::set_polling_page( polling_page );

#ifndef PRODUCT
  if( Verbose && PrintMiscellaneous )
    tty->print("[SafePoint Polling address: " INTPTR_FORMAT "]\n", (intptr_t)polling_page);
#endif

  if (!UseMembar) {
    address mem_serialize_page = (address)VirtualAlloc(NULL, os::vm_page_size(), MEM_RESERVE, PAGE_READWRITE);
    guarantee( mem_serialize_page != NULL, "Reserve Failed for memory serialize page");

    return_page  = (address)VirtualAlloc(mem_serialize_page, os::vm_page_size(), MEM_COMMIT, PAGE_READWRITE);
    guarantee( return_page != NULL, "Commit Failed for memory serialize page");

    os::set_memory_serialize_page( mem_serialize_page );

#ifndef PRODUCT
    if(Verbose && PrintMiscellaneous)
      tty->print("[Memory Serialize  Page address: " INTPTR_FORMAT "]\n", (intptr_t)mem_serialize_page);
#endif
  }

  os::large_page_init();

  // Setup Windows Exceptions

  // for debugging float code generation bugs
  if (ForceFloatExceptions) {
#ifndef  _WIN64
    static long fp_control_word = 0;
    __asm { fstcw fp_control_word }
    // see Intel PPro Manual, Vol. 2, p 7-16
    const long precision = 0x20;
    const long underflow = 0x10;
    const long overflow  = 0x08;
    const long zero_div  = 0x04;
    const long denorm    = 0x02;
    const long invalid   = 0x01;
    fp_control_word |= invalid;
    __asm { fldcw fp_control_word }
#endif
  }

  // If stack_commit_size is 0, windows will reserve the default size,
  // but only commit a small portion of it.
  size_t stack_commit_size = round_to(ThreadStackSize*K, os::vm_page_size());
  size_t default_reserve_size = os::win32::default_stack_size();
  size_t actual_reserve_size = stack_commit_size;
  if (stack_commit_size < default_reserve_size) {
    // If stack_commit_size == 0, we want this too
    actual_reserve_size = default_reserve_size;
  }

  // Check minimum allowable stack size for thread creation and to initialize
  // the java system classes, including StackOverflowError - depends on page
  // size.  Add a page for compiler2 recursion in main thread.
  // Add in 2*BytesPerWord times page size to account for VM stack during
  // class initialization depending on 32 or 64 bit VM.
  size_t min_stack_allowed =
            (size_t)(StackYellowPages+StackRedPages+StackShadowPages+
            2*BytesPerWord COMPILER2_PRESENT(+1)) * os::vm_page_size();
  if (actual_reserve_size < min_stack_allowed) {
    tty->print_cr("\nThe stack size specified is too small, "
                  "Specify at least %dk",
                  min_stack_allowed / K);
    return JNI_ERR;
  }

  JavaThread::set_stack_size_at_create(stack_commit_size);

  // Calculate theoretical max. size of Threads to guard gainst artifical
  // out-of-memory situations, where all available address-space has been
  // reserved by thread stacks.
  assert(actual_reserve_size != 0, "Must have a stack");

  // Calculate the thread limit when we should start doing Virtual Memory
  // banging. Currently when the threads will have used all but 200Mb of space.
  //
  // TODO: consider performing a similar calculation for commit size instead
  // as reserve size, since on a 64-bit platform we'll run into that more
  // often than running out of virtual memory space.  We can use the
  // lower value of the two calculations as the os_thread_limit.
  size_t max_address_space = ((size_t)1 << (BitsPerWord - 1)) - (200 * K * K);
  win32::_os_thread_limit = (intx)(max_address_space / actual_reserve_size);

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

  if (UseNUMA && !ForceNUMA) {
    UseNUMA = false; // We don't fully support this yet
  }

  if (UseNUMAInterleaving) {
    // first check whether this Windows OS supports VirtualAllocExNuma, if not ignore this flag
    bool success = numa_interleaving_init();
    if (!success) UseNUMAInterleaving = false;
  }

  if (initSock() != JNI_OK) {
    return JNI_ERR;
  }

  return JNI_OK;
}

void os::init_3(void) {
  return;
}

// Mark the polling page as unreadable
void os::make_polling_page_unreadable(void) {
  DWORD old_status;
  if( !VirtualProtect((char *)_polling_page, os::vm_page_size(), PAGE_NOACCESS, &old_status) )
    fatal("Could not disable polling page");
};

// Mark the polling page as readable
void os::make_polling_page_readable(void) {
  DWORD old_status;
  if( !VirtualProtect((char *)_polling_page, os::vm_page_size(), PAGE_READONLY, &old_status) )
    fatal("Could not enable polling page");
};


int os::stat(const char *path, struct stat *sbuf) {
  char pathbuf[MAX_PATH];
  if (strlen(path) > MAX_PATH - 1) {
    errno = ENAMETOOLONG;
    return -1;
  }
  os::native_path(strcpy(pathbuf, path));
  int ret = ::stat(pathbuf, sbuf);
  if (sbuf != NULL && UseUTCFileTimestamp) {
    // Fix for 6539723.  st_mtime returned from stat() is dependent on
    // the system timezone and so can return different values for the
    // same file if/when daylight savings time changes.  This adjustment
    // makes sure the same timestamp is returned regardless of the TZ.
    //
    // See:
    // http://msdn.microsoft.com/library/
    //   default.asp?url=/library/en-us/sysinfo/base/
    //   time_zone_information_str.asp
    // and
    // http://msdn.microsoft.com/library/default.asp?url=
    //   /library/en-us/sysinfo/base/settimezoneinformation.asp
    //
    // NOTE: there is a insidious bug here:  If the timezone is changed
    // after the call to stat() but before 'GetTimeZoneInformation()', then
    // the adjustment we do here will be wrong and we'll return the wrong
    // value (which will likely end up creating an invalid class data
    // archive).  Absent a better API for this, or some time zone locking
    // mechanism, we'll have to live with this risk.
    TIME_ZONE_INFORMATION tz;
    DWORD tzid = GetTimeZoneInformation(&tz);
    int daylightBias =
      (tzid == TIME_ZONE_ID_DAYLIGHT) ?  tz.DaylightBias : tz.StandardBias;
    sbuf->st_mtime += (tz.Bias + daylightBias) * 60;
  }
  return ret;
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
  // This code is copy from clasic VM -> hpi::sysThreadCPUTime
  // If this function changes, os::is_thread_cpu_time_supported() should too
  if (os::win32::is_nt()) {
    FILETIME CreationTime;
    FILETIME ExitTime;
    FILETIME KernelTime;
    FILETIME UserTime;

    if ( GetThreadTimes(thread->osthread()->thread_handle(),
                    &CreationTime, &ExitTime, &KernelTime, &UserTime) == 0)
      return -1;
    else
      if (user_sys_cpu_time) {
        return (FT2INT64(UserTime) + FT2INT64(KernelTime)) * 100;
      } else {
        return FT2INT64(UserTime) * 100;
      }
  } else {
    return (jlong) timeGetTime() * 1000000;
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
  if (os::win32::is_nt()) {
    FILETIME CreationTime;
    FILETIME ExitTime;
    FILETIME KernelTime;
    FILETIME UserTime;

    if ( GetThreadTimes(GetCurrentThread(),
                    &CreationTime, &ExitTime, &KernelTime, &UserTime) == 0)
      return false;
    else
      return true;
  } else {
    return false;
  }
}

// Windows does't provide a loadavg primitive so this is stubbed out for now.
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

// This method is a slightly reworked copy of JDK's sysOpen
// from src/windows/hpi/src/sys_api_md.c

int os::open(const char *path, int oflag, int mode) {
  char pathbuf[MAX_PATH];

  if (strlen(path) > MAX_PATH - 1) {
    errno = ENAMETOOLONG;
          return -1;
  }
  os::native_path(strcpy(pathbuf, path));
  return ::open(pathbuf, oflag | O_BINARY | O_NOINHERIT, mode);
}

FILE* os::open(int fd, const char* mode) {
  return ::_fdopen(fd, mode);
}

// Is a (classpath) directory empty?
bool os::dir_is_empty(const char* path) {
  WIN32_FIND_DATA fd;
  HANDLE f = FindFirstFile(path, &fd);
  if (f == INVALID_HANDLE_VALUE) {
    return true;
  }
  FindClose(f);
  return false;
}

// create binary file, rewriting existing file if required
int os::create_binary_file(const char* path, bool rewrite_existing) {
  int oflags = _O_CREAT | _O_WRONLY | _O_BINARY;
  if (!rewrite_existing) {
    oflags |= _O_EXCL;
  }
  return ::open(path, oflags, _S_IREAD | _S_IWRITE);
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

// This method is a slightly reworked copy of JDK's sysNativePath
// from src/windows/hpi/src/path_md.c

/* Convert a pathname to native format.  On win32, this involves forcing all
   separators to be '\\' rather than '/' (both are legal inputs, but Win95
   sometimes rejects '/') and removing redundant separators.  The input path is
   assumed to have been converted into the character encoding used by the local
   system.  Because this might be a double-byte encoding, care is taken to
   treat double-byte lead characters correctly.

   This procedure modifies the given path in place, as the result is never
   longer than the original.  There is no error return; this operation always
   succeeds. */
char * os::native_path(char *path) {
  char *src = path, *dst = path, *end = path;
  char *colon = NULL;           /* If a drive specifier is found, this will
                                        point to the colon following the drive
                                        letter */

  /* Assumption: '/', '\\', ':', and drive letters are never lead bytes */
  assert(((!::IsDBCSLeadByte('/'))
    && (!::IsDBCSLeadByte('\\'))
    && (!::IsDBCSLeadByte(':'))),
    "Illegal lead byte");

  /* Check for leading separators */
#define isfilesep(c) ((c) == '/' || (c) == '\\')
  while (isfilesep(*src)) {
    src++;
  }

  if (::isalpha(*src) && !::IsDBCSLeadByte(*src) && src[1] == ':') {
    /* Remove leading separators if followed by drive specifier.  This
      hack is necessary to support file URLs containing drive
      specifiers (e.g., "file://c:/path").  As a side effect,
      "/c:/path" can be used as an alternative to "c:/path". */
    *dst++ = *src++;
    colon = dst;
    *dst++ = ':';
    src++;
  } else {
    src = path;
    if (isfilesep(src[0]) && isfilesep(src[1])) {
      /* UNC pathname: Retain first separator; leave src pointed at
         second separator so that further separators will be collapsed
         into the second separator.  The result will be a pathname
         beginning with "\\\\" followed (most likely) by a host name. */
      src = dst = path + 1;
      path[0] = '\\';     /* Force first separator to '\\' */
    }
  }

  end = dst;

  /* Remove redundant separators from remainder of path, forcing all
      separators to be '\\' rather than '/'. Also, single byte space
      characters are removed from the end of the path because those
      are not legal ending characters on this operating system.
  */
  while (*src != '\0') {
    if (isfilesep(*src)) {
      *dst++ = '\\'; src++;
      while (isfilesep(*src)) src++;
      if (*src == '\0') {
        /* Check for trailing separator */
        end = dst;
        if (colon == dst - 2) break;                      /* "z:\\" */
        if (dst == path + 1) break;                       /* "\\" */
        if (dst == path + 2 && isfilesep(path[0])) {
          /* "\\\\" is not collapsed to "\\" because "\\\\" marks the
            beginning of a UNC pathname.  Even though it is not, by
            itself, a valid UNC pathname, we leave it as is in order
            to be consistent with the path canonicalizer as well
            as the win32 APIs, which treat this case as an invalid
            UNC pathname rather than as an alias for the root
            directory of the current drive. */
          break;
        }
        end = --dst;  /* Path does not denote a root directory, so
                                    remove trailing separator */
        break;
      }
      end = dst;
    } else {
      if (::IsDBCSLeadByte(*src)) { /* Copy a double-byte character */
        *dst++ = *src++;
        if (*src) *dst++ = *src++;
        end = dst;
      } else {         /* Copy a single-byte character */
        char c = *src++;
        *dst++ = c;
        /* Space is not a legal ending character */
        if (c != ' ') end = dst;
      }
    }
  }

  *end = '\0';

  /* For "z:", add "." to work around a bug in the C runtime library */
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


// This code is a copy of JDK's sysSync
// from src/windows/hpi/src/sys_api_md.c
// except for the legacy workaround for a bug in Win 98

int os::fsync(int fd) {
  HANDLE handle = (HANDLE)::_get_osfhandle(fd);

  if ( (!::FlushFileBuffers(handle)) &&
         (GetLastError() != ERROR_ACCESS_DENIED) ) {
    /* from winerror.h */
    return -1;
  }
  return 0;
}

static int nonSeekAvailable(int, long *);
static int stdinAvailable(int, long *);

#define S_ISCHR(mode)   (((mode) & _S_IFCHR) == _S_IFCHR)
#define S_ISFIFO(mode)  (((mode) & _S_IFIFO) == _S_IFIFO)

// This code is a copy of JDK's sysAvailable
// from src/windows/hpi/src/sys_api_md.c

int os::available(int fd, jlong *bytes) {
  jlong cur, end;
  struct _stati64 stbuf64;

  if (::_fstati64(fd, &stbuf64) >= 0) {
    int mode = stbuf64.st_mode;
    if (S_ISCHR(mode) || S_ISFIFO(mode)) {
      int ret;
      long lpbytes;
      if (fd == 0) {
        ret = stdinAvailable(fd, &lpbytes);
      } else {
        ret = nonSeekAvailable(fd, &lpbytes);
      }
      (*bytes) = (jlong)(lpbytes);
      return ret;
    }
    if ((cur = ::_lseeki64(fd, 0L, SEEK_CUR)) == -1) {
      return FALSE;
    } else if ((end = ::_lseeki64(fd, 0L, SEEK_END)) == -1) {
      return FALSE;
    } else if (::_lseeki64(fd, cur, SEEK_SET) == -1) {
      return FALSE;
    }
    *bytes = end - cur;
    return TRUE;
  } else {
    return FALSE;
  }
}

// This code is a copy of JDK's nonSeekAvailable
// from src/windows/hpi/src/sys_api_md.c

static int nonSeekAvailable(int fd, long *pbytes) {
  /* This is used for available on non-seekable devices
    * (like both named and anonymous pipes, such as pipes
    *  connected to an exec'd process).
    * Standard Input is a special case.
    *
    */
  HANDLE han;

  if ((han = (HANDLE) ::_get_osfhandle(fd)) == (HANDLE)(-1)) {
    return FALSE;
  }

  if (! ::PeekNamedPipe(han, NULL, 0, NULL, (LPDWORD)pbytes, NULL)) {
        /* PeekNamedPipe fails when at EOF.  In that case we
         * simply make *pbytes = 0 which is consistent with the
         * behavior we get on Solaris when an fd is at EOF.
         * The only alternative is to raise an Exception,
         * which isn't really warranted.
         */
    if (::GetLastError() != ERROR_BROKEN_PIPE) {
      return FALSE;
    }
    *pbytes = 0;
  }
  return TRUE;
}

#define MAX_INPUT_EVENTS 2000

// This code is a copy of JDK's stdinAvailable
// from src/windows/hpi/src/sys_api_md.c

static int stdinAvailable(int fd, long *pbytes) {
  HANDLE han;
  DWORD numEventsRead = 0;      /* Number of events read from buffer */
  DWORD numEvents = 0;  /* Number of events in buffer */
  DWORD i = 0;          /* Loop index */
  DWORD curLength = 0;  /* Position marker */
  DWORD actualLength = 0;       /* Number of bytes readable */
  BOOL error = FALSE;         /* Error holder */
  INPUT_RECORD *lpBuffer;     /* Pointer to records of input events */

  if ((han = ::GetStdHandle(STD_INPUT_HANDLE)) == INVALID_HANDLE_VALUE) {
        return FALSE;
  }

  /* Construct an array of input records in the console buffer */
  error = ::GetNumberOfConsoleInputEvents(han, &numEvents);
  if (error == 0) {
    return nonSeekAvailable(fd, pbytes);
  }

  /* lpBuffer must fit into 64K or else PeekConsoleInput fails */
  if (numEvents > MAX_INPUT_EVENTS) {
    numEvents = MAX_INPUT_EVENTS;
  }

  lpBuffer = (INPUT_RECORD *)os::malloc(numEvents * sizeof(INPUT_RECORD), mtInternal);
  if (lpBuffer == NULL) {
    return FALSE;
  }

  error = ::PeekConsoleInput(han, lpBuffer, numEvents, &numEventsRead);
  if (error == 0) {
    os::free(lpBuffer, mtInternal);
    return FALSE;
  }

  /* Examine input records for the number of bytes available */
  for(i=0; i<numEvents; i++) {
    if (lpBuffer[i].EventType == KEY_EVENT) {

      KEY_EVENT_RECORD *keyRecord = (KEY_EVENT_RECORD *)
                                      &(lpBuffer[i].Event);
      if (keyRecord->bKeyDown == TRUE) {
        CHAR *keyPressed = (CHAR *) &(keyRecord->uChar);
        curLength++;
        if (*keyPressed == '\r') {
          actualLength = curLength;
        }
      }
    }
  }

  if(lpBuffer != NULL) {
    os::free(lpBuffer, mtInternal);
  }

  *pbytes = (long) actualLength;
  return TRUE;
}

// Map a block of memory.
char* os::pd_map_memory(int fd, const char* file_name, size_t file_offset,
                     char *addr, size_t bytes, bool read_only,
                     bool allow_exec) {
  HANDLE hFile;
  char* base;

  hFile = CreateFile(file_name, GENERIC_READ, FILE_SHARE_READ, NULL,
                     OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
  if (hFile == NULL) {
    if (PrintMiscellaneous && Verbose) {
      DWORD err = GetLastError();
      tty->print_cr("CreateFile() failed: GetLastError->%ld.", err);
    }
    return NULL;
  }

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

    base = (char*) VirtualAlloc(addr, bytes, MEM_COMMIT | MEM_RESERVE,
                                PAGE_READWRITE);
    if (base == NULL) {
      if (PrintMiscellaneous && Verbose) {
        DWORD err = GetLastError();
        tty->print_cr("VirtualAlloc() failed: GetLastError->%ld.", err);
      }
      CloseHandle(hFile);
      return NULL;
    }

    DWORD bytes_read;
    OVERLAPPED overlapped;
    overlapped.Offset = (DWORD)file_offset;
    overlapped.OffsetHigh = 0;
    overlapped.hEvent = NULL;
    // ReadFile guarantees that if the return value is true, the requested
    // number of bytes were read before returning.
    bool res = ReadFile(hFile, base, (DWORD)bytes, &bytes_read, &overlapped) != 0;
    if (!res) {
      if (PrintMiscellaneous && Verbose) {
        DWORD err = GetLastError();
        tty->print_cr("ReadFile() failed: GetLastError->%ld.", err);
      }
      release_memory(base, bytes);
      CloseHandle(hFile);
      return NULL;
    }
  } else {
    HANDLE hMap = CreateFileMapping(hFile, NULL, PAGE_WRITECOPY, 0, 0,
                                    NULL /*file_name*/);
    if (hMap == NULL) {
      if (PrintMiscellaneous && Verbose) {
        DWORD err = GetLastError();
        tty->print_cr("CreateFileMapping() failed: GetLastError->%ld.", err);
      }
      CloseHandle(hFile);
      return NULL;
    }

    DWORD access = read_only ? FILE_MAP_READ : FILE_MAP_COPY;
    base = (char*)MapViewOfFileEx(hMap, access, 0, (DWORD)file_offset,
                                  (DWORD)bytes, addr);
    if (base == NULL) {
      if (PrintMiscellaneous && Verbose) {
        DWORD err = GetLastError();
        tty->print_cr("MapViewOfFileEx() failed: GetLastError->%ld.", err);
      }
      CloseHandle(hMap);
      CloseHandle(hFile);
      return NULL;
    }

    if (CloseHandle(hMap) == 0) {
      if (PrintMiscellaneous && Verbose) {
        DWORD err = GetLastError();
        tty->print_cr("CloseHandle(hMap) failed: GetLastError->%ld.", err);
      }
      CloseHandle(hFile);
      return base;
    }
  }

  if (allow_exec) {
    DWORD old_protect;
    DWORD exec_access = read_only ? PAGE_EXECUTE_READ : PAGE_EXECUTE_READWRITE;
    bool res = VirtualProtect(base, bytes, exec_access, &old_protect) != 0;

    if (!res) {
      if (PrintMiscellaneous && Verbose) {
        DWORD err = GetLastError();
        tty->print_cr("VirtualProtect() failed: GetLastError->%ld.", err);
      }
      // Don't consider this a hard error, on IA32 even if the
      // VirtualProtect fails, we should still be able to execute
      CloseHandle(hFile);
      return base;
    }
  }

  if (CloseHandle(hFile) == 0) {
    if (PrintMiscellaneous && Verbose) {
      DWORD err = GetLastError();
      tty->print_cr("CloseHandle(hFile) failed: GetLastError->%ld.", err);
    }
    return base;
  }

  return base;
}


// Remap a block of memory.
char* os::pd_remap_memory(int fd, const char* file_name, size_t file_offset,
                       char *addr, size_t bytes, bool read_only,
                       bool allow_exec) {
  // This OS does not allow existing memory maps to be remapped so we
  // have to unmap the memory before we remap it.
  if (!os::unmap_memory(addr, bytes)) {
    return NULL;
  }

  // There is a very small theoretical window between the unmap_memory()
  // call above and the map_memory() call below where a thread in native
  // code may be able to access an address that is no longer mapped.

  return os::map_memory(fd, file_name, file_offset, addr, bytes,
           read_only, allow_exec);
}


// Unmap a block of memory.
// Returns true=success, otherwise false.

bool os::pd_unmap_memory(char* addr, size_t bytes) {
  BOOL result = UnmapViewOfFile(addr);
  if (result == 0) {
    if (PrintMiscellaneous && Verbose) {
      DWORD err = GetLastError();
      tty->print_cr("UnmapViewOfFile() failed: GetLastError->%ld.", err);
    }
    return false;
  }
  return true;
}

void os::pause() {
  char filename[MAX_PATH];
  if (PauseAtStartupFile && PauseAtStartupFile[0]) {
    jio_snprintf(filename, MAX_PATH, PauseAtStartupFile);
  } else {
    jio_snprintf(filename, MAX_PATH, "./vm.paused.%d", current_process_id());
  }

  int fd = ::open(filename, O_WRONLY | O_CREAT | O_TRUNC, 0666);
  if (fd != -1) {
    struct stat buf;
    ::close(fd);
    while (::stat(filename, &buf) == 0) {
      Sleep(100);
    }
  } else {
    jio_fprintf(stderr,
      "Could not open pause file '%s', continuing immediately.\n", filename);
  }
}

// An Event wraps a win32 "CreateEvent" kernel handle.
//
// We have a number of choices regarding "CreateEvent" win32 handle leakage:
//
// 1:  When a thread dies return the Event to the EventFreeList, clear the ParkHandle
//     field, and call CloseHandle() on the win32 event handle.  Unpark() would
//     need to be modified to tolerate finding a NULL (invalid) win32 event handle.
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
// 3.  Collapse the interrupt_event, the JSR166 parker event, and the objectmonitor ParkEvent
//     into a single win32 CreateEvent() handle.
//
// _Event transitions in park()
//   -1 => -1 : illegal
//    1 =>  0 : pass - return immediately
//    0 => -1 : block
//
// _Event serves as a restricted-range semaphore :
//    -1 : thread is blocked
//     0 : neutral  - thread is running or ready
//     1 : signaled - thread is running or ready
//
// Another possible encoding of _Event would be
// with explicit "PARKED" and "SIGNALED" bits.

int os::PlatformEvent::park (jlong Millis) {
    guarantee (_ParkHandle != NULL , "Invariant") ;
    guarantee (Millis > 0          , "Invariant") ;
    int v ;

    // CONSIDER: defer assigning a CreateEvent() handle to the Event until
    // the initial park() operation.

    for (;;) {
        v = _Event ;
        if (Atomic::cmpxchg (v-1, &_Event, v) == v) break ;
    }
    guarantee ((v == 0) || (v == 1), "invariant") ;
    if (v != 0) return OS_OK ;

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

    const int MAXTIMEOUT = 0x10000000 ;
    DWORD rv = WAIT_TIMEOUT ;
    while (_Event < 0 && Millis > 0) {
       DWORD prd = Millis ;     // set prd = MAX (Millis, MAXTIMEOUT)
       if (Millis > MAXTIMEOUT) {
          prd = MAXTIMEOUT ;
       }
       rv = ::WaitForSingleObject (_ParkHandle, prd) ;
       assert (rv == WAIT_OBJECT_0 || rv == WAIT_TIMEOUT, "WaitForSingleObject failed") ;
       if (rv == WAIT_TIMEOUT) {
           Millis -= prd ;
       }
    }
    v = _Event ;
    _Event = 0 ;
    // see comment at end of os::PlatformEvent::park() below:
    OrderAccess::fence() ;
    // If we encounter a nearly simultanous timeout expiry and unpark()
    // we return OS_OK indicating we awoke via unpark().
    // Implementor's license -- returning OS_TIMEOUT would be equally valid, however.
    return (v >= 0) ? OS_OK : OS_TIMEOUT ;
}

void os::PlatformEvent::park () {
    guarantee (_ParkHandle != NULL, "Invariant") ;
    // Invariant: Only the thread associated with the Event/PlatformEvent
    // may call park().
    int v ;
    for (;;) {
        v = _Event ;
        if (Atomic::cmpxchg (v-1, &_Event, v) == v) break ;
    }
    guarantee ((v == 0) || (v == 1), "invariant") ;
    if (v != 0) return ;

    // Do this the hard way by blocking ...
    // TODO: consider a brief spin here, gated on the success of recent
    // spin attempts by this thread.
    while (_Event < 0) {
       DWORD rv = ::WaitForSingleObject (_ParkHandle, INFINITE) ;
       assert (rv == WAIT_OBJECT_0, "WaitForSingleObject failed") ;
    }

    // Usually we'll find _Event == 0 at this point, but as
    // an optional optimization we clear it, just in case can
    // multiple unpark() operations drove _Event up to 1.
    _Event = 0 ;
    OrderAccess::fence() ;
    guarantee (_Event >= 0, "invariant") ;
}

void os::PlatformEvent::unpark() {
  guarantee (_ParkHandle != NULL, "Invariant") ;

  // Transitions for _Event:
  //    0 :=> 1
  //    1 :=> 1
  //   -1 :=> either 0 or 1; must signal target thread
  //          That is, we can safely transition _Event from -1 to either
  //          0 or 1. Forcing 1 is slightly more efficient for back-to-back
  //          unpark() calls.
  // See also: "Semaphores in Plan 9" by Mullender & Cox
  //
  // Note: Forcing a transition from "-1" to "1" on an unpark() means
  // that it will take two back-to-back park() calls for the owning
  // thread to block. This has the benefit of forcing a spurious return
  // from the first park() call after an unpark() call which will help
  // shake out uses of park() and unpark() without condition variables.

  if (Atomic::xchg(1, &_Event) >= 0) return;

  ::SetEvent(_ParkHandle);
}


// JSR166
// -------------------------------------------------------

/*
 * The Windows implementation of Park is very straightforward: Basic
 * operations on Win32 Events turn out to have the right semantics to
 * use them directly. We opportunistically resuse the event inherited
 * from Monitor.
 */


void Parker::park(bool isAbsolute, jlong time) {
  guarantee (_ParkEvent != NULL, "invariant") ;
  // First, demultiplex/decode time arguments
  if (time < 0) { // don't wait
    return;
  }
  else if (time == 0 && !isAbsolute) {
    time = INFINITE;
  }
  else if  (isAbsolute) {
    time -= os::javaTimeMillis(); // convert to relative time
    if (time <= 0) // already elapsed
      return;
  }
  else { // relative
    time /= 1000000; // Must coarsen from nanos to millis
    if (time == 0)   // Wait for the minimal time unit if zero
      time = 1;
  }

  JavaThread* thread = (JavaThread*)(Thread::current());
  assert(thread->is_Java_thread(), "Must be JavaThread");
  JavaThread *jt = (JavaThread *)thread;

  // Don't wait if interrupted or already triggered
  if (Thread::is_interrupted(thread, false) ||
    WaitForSingleObject(_ParkEvent, 0) == WAIT_OBJECT_0) {
    ResetEvent(_ParkEvent);
    return;
  }
  else {
    ThreadBlockInVM tbivm(jt);
    OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
    jt->set_suspend_equivalent();

    WaitForSingleObject(_ParkEvent,  time);
    ResetEvent(_ParkEvent);

    // If externally suspended while waiting, re-suspend
    if (jt->handle_special_suspend_equivalent_condition()) {
      jt->java_suspend_self();
    }
  }
}

void Parker::unpark() {
  guarantee (_ParkEvent != NULL, "invariant") ;
  SetEvent(_ParkEvent);
}

// Run the specified command in a separate process. Return its exit value,
// or -1 on failure (e.g. can't create a new process).
int os::fork_and_exec(char* cmd) {
  STARTUPINFO si;
  PROCESS_INFORMATION pi;

  memset(&si, 0, sizeof(si));
  si.cb = sizeof(si);
  memset(&pi, 0, sizeof(pi));
  BOOL rslt = CreateProcess(NULL,   // executable name - use command line
                            cmd,    // command line
                            NULL,   // process security attribute
                            NULL,   // thread security attribute
                            TRUE,   // inherits system handles
                            0,      // no creation flags
                            NULL,   // use parent's environment block
                            NULL,   // use parent's starting directory
                            &si,    // (in) startup information
                            &pi);   // (out) process information

  if (rslt) {
    // Wait until child process exits.
    WaitForSingleObject(pi.hProcess, INFINITE);

    DWORD exit_code;
    GetExitCodeProcess(pi.hProcess, &exit_code);

    // Close process and thread handles.
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);

    return (int)exit_code;
  } else {
    return -1;
  }
}

//--------------------------------------------------------------------------------------------------
// Non-product code

static int mallocDebugIntervalCounter = 0;
static int mallocDebugCounter = 0;
bool os::check_heap(bool force) {
  if (++mallocDebugCounter < MallocVerifyStart && !force) return true;
  if (++mallocDebugIntervalCounter >= MallocVerifyInterval || force) {
    // Note: HeapValidate executes two hardware breakpoints when it finds something
    // wrong; at these points, eax contains the address of the offending block (I think).
    // To get to the exlicit error message(s) below, just continue twice.
    HANDLE heap = GetProcessHeap();
    { HeapLock(heap);
      PROCESS_HEAP_ENTRY phe;
      phe.lpData = NULL;
      while (HeapWalk(heap, &phe) != 0) {
        if ((phe.wFlags & PROCESS_HEAP_ENTRY_BUSY) &&
            !HeapValidate(heap, 0, phe.lpData)) {
          tty->print_cr("C heap has been corrupted (time: %d allocations)", mallocDebugCounter);
          tty->print_cr("corrupted block near address %#x, length %d", phe.lpData, phe.cbData);
          fatal("corrupted C heap");
        }
      }
      DWORD err = GetLastError();
      if (err != ERROR_NO_MORE_ITEMS && err != ERROR_CALL_NOT_IMPLEMENTED) {
        fatal(err_msg("heap walk aborted with error %d", err));
      }
      HeapUnlock(heap);
    }
    mallocDebugIntervalCounter = 0;
  }
  return true;
}


bool os::find(address addr, outputStream* st) {
  // Nothing yet
  return false;
}

LONG WINAPI os::win32::serialize_fault_filter(struct _EXCEPTION_POINTERS* e) {
  DWORD exception_code = e->ExceptionRecord->ExceptionCode;

  if ( exception_code == EXCEPTION_ACCESS_VIOLATION ) {
    JavaThread* thread = (JavaThread*)ThreadLocalStorage::get_thread_slow();
    PEXCEPTION_RECORD exceptionRecord = e->ExceptionRecord;
    address addr = (address) exceptionRecord->ExceptionInformation[1];

    if (os::is_memory_serialize_page(thread, addr))
      return EXCEPTION_CONTINUE_EXECUTION;
  }

  return EXCEPTION_CONTINUE_SEARCH;
}

// We don't build a headless jre for Windows
bool os::is_headless_jre() { return false; }

static jint initSock() {
  WSADATA wsadata;

  if (!os::WinSock2Dll::WinSock2Available()) {
    jio_fprintf(stderr, "Could not load Winsock (error: %d)\n",
      ::GetLastError());
    return JNI_ERR;
  }

  if (os::WinSock2Dll::WSAStartup(MAKEWORD(2,2), &wsadata) != 0) {
    jio_fprintf(stderr, "Could not initialize Winsock (error: %d)\n",
      ::GetLastError());
    return JNI_ERR;
  }
  return JNI_OK;
}

struct hostent* os::get_host_by_name(char* name) {
  return (struct hostent*)os::WinSock2Dll::gethostbyname(name);
}

int os::socket_close(int fd) {
  return ::closesocket(fd);
}

int os::socket_available(int fd, jint *pbytes) {
  int ret = ::ioctlsocket(fd, FIONREAD, (u_long*)pbytes);
  return (ret < 0) ? 0 : 1;
}

int os::socket(int domain, int type, int protocol) {
  return ::socket(domain, type, protocol);
}

int os::listen(int fd, int count) {
  return ::listen(fd, count);
}

int os::connect(int fd, struct sockaddr* him, socklen_t len) {
  return ::connect(fd, him, len);
}

int os::accept(int fd, struct sockaddr* him, socklen_t* len) {
  return ::accept(fd, him, len);
}

int os::sendto(int fd, char* buf, size_t len, uint flags,
               struct sockaddr* to, socklen_t tolen) {

  return ::sendto(fd, buf, (int)len, flags, to, tolen);
}

int os::recvfrom(int fd, char *buf, size_t nBytes, uint flags,
                 sockaddr* from, socklen_t* fromlen) {

  return ::recvfrom(fd, buf, (int)nBytes, flags, from, fromlen);
}

int os::recv(int fd, char* buf, size_t nBytes, uint flags) {
  return ::recv(fd, buf, (int)nBytes, flags);
}

int os::send(int fd, char* buf, size_t nBytes, uint flags) {
  return ::send(fd, buf, (int)nBytes, flags);
}

int os::raw_send(int fd, char* buf, size_t nBytes, uint flags) {
  return ::send(fd, buf, (int)nBytes, flags);
}

int os::timeout(int fd, long timeout) {
  fd_set tbl;
  struct timeval t;

  t.tv_sec  = timeout / 1000;
  t.tv_usec = (timeout % 1000) * 1000;

  tbl.fd_count    = 1;
  tbl.fd_array[0] = fd;

  return ::select(1, &tbl, 0, 0, &t);
}

int os::get_host_name(char* name, int namelen) {
  return ::gethostname(name, namelen);
}

int os::socket_shutdown(int fd, int howto) {
  return ::shutdown(fd, howto);
}

int os::bind(int fd, struct sockaddr* him, socklen_t len) {
  return ::bind(fd, him, len);
}

int os::get_sock_name(int fd, struct sockaddr* him, socklen_t* len) {
  return ::getsockname(fd, him, len);
}

int os::get_sock_opt(int fd, int level, int optname,
                     char* optval, socklen_t* optlen) {
  return ::getsockopt(fd, level, optname, optval, optlen);
}

int os::set_sock_opt(int fd, int level, int optname,
                     const char* optval, socklen_t optlen) {
  return ::setsockopt(fd, level, optname, optval, optlen);
}

// WINDOWS CONTEXT Flags for THREAD_SAMPLING
#if defined(IA32)
#  define sampling_context_flags (CONTEXT_FULL | CONTEXT_FLOATING_POINT | CONTEXT_EXTENDED_REGISTERS)
#elif defined (AMD64)
#  define sampling_context_flags (CONTEXT_FULL | CONTEXT_FLOATING_POINT)
#endif

// returns true if thread could be suspended,
// false otherwise
static bool do_suspend(HANDLE* h) {
  if (h != NULL) {
    if (SuspendThread(*h) != ~0) {
      return true;
    }
  }
  return false;
}

// resume the thread
// calling resume on an active thread is a no-op
static void do_resume(HANDLE* h) {
  if (h != NULL) {
    ResumeThread(*h);
  }
}

// retrieve a suspend/resume context capable handle
// from the tid. Caller validates handle return value.
void get_thread_handle_for_extended_context(HANDLE* h, OSThread::thread_id_t tid) {
  if (h != NULL) {
    *h = OpenThread(THREAD_SUSPEND_RESUME | THREAD_GET_CONTEXT | THREAD_QUERY_INFORMATION, FALSE, tid);
  }
}

//
// Thread sampling implementation
//
void os::SuspendedThreadTask::internal_do_task() {
  CONTEXT    ctxt;
  HANDLE     h = NULL;

  // get context capable handle for thread
  get_thread_handle_for_extended_context(&h, _thread->osthread()->thread_id());

  // sanity
  if (h == NULL || h == INVALID_HANDLE_VALUE) {
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


// Kernel32 API
typedef SIZE_T (WINAPI* GetLargePageMinimum_Fn)(void);
typedef LPVOID (WINAPI *VirtualAllocExNuma_Fn) (HANDLE, LPVOID, SIZE_T, DWORD, DWORD, DWORD);
typedef BOOL (WINAPI *GetNumaHighestNodeNumber_Fn) (PULONG);
typedef BOOL (WINAPI *GetNumaNodeProcessorMask_Fn) (UCHAR, PULONGLONG);
typedef USHORT (WINAPI* RtlCaptureStackBackTrace_Fn)(ULONG, ULONG, PVOID*, PULONG);

GetLargePageMinimum_Fn      os::Kernel32Dll::_GetLargePageMinimum = NULL;
VirtualAllocExNuma_Fn       os::Kernel32Dll::_VirtualAllocExNuma = NULL;
GetNumaHighestNodeNumber_Fn os::Kernel32Dll::_GetNumaHighestNodeNumber = NULL;
GetNumaNodeProcessorMask_Fn os::Kernel32Dll::_GetNumaNodeProcessorMask = NULL;
RtlCaptureStackBackTrace_Fn os::Kernel32Dll::_RtlCaptureStackBackTrace = NULL;


BOOL                        os::Kernel32Dll::initialized = FALSE;
SIZE_T os::Kernel32Dll::GetLargePageMinimum() {
  assert(initialized && _GetLargePageMinimum != NULL,
    "GetLargePageMinimumAvailable() not yet called");
  return _GetLargePageMinimum();
}

BOOL os::Kernel32Dll::GetLargePageMinimumAvailable() {
  if (!initialized) {
    initialize();
  }
  return _GetLargePageMinimum != NULL;
}

BOOL os::Kernel32Dll::NumaCallsAvailable() {
  if (!initialized) {
    initialize();
  }
  return _VirtualAllocExNuma != NULL;
}

LPVOID os::Kernel32Dll::VirtualAllocExNuma(HANDLE hProc, LPVOID addr, SIZE_T bytes, DWORD flags, DWORD prot, DWORD node) {
  assert(initialized && _VirtualAllocExNuma != NULL,
    "NUMACallsAvailable() not yet called");

  return _VirtualAllocExNuma(hProc, addr, bytes, flags, prot, node);
}

BOOL os::Kernel32Dll::GetNumaHighestNodeNumber(PULONG ptr_highest_node_number) {
  assert(initialized && _GetNumaHighestNodeNumber != NULL,
    "NUMACallsAvailable() not yet called");

  return _GetNumaHighestNodeNumber(ptr_highest_node_number);
}

BOOL os::Kernel32Dll::GetNumaNodeProcessorMask(UCHAR node, PULONGLONG proc_mask) {
  assert(initialized && _GetNumaNodeProcessorMask != NULL,
    "NUMACallsAvailable() not yet called");

  return _GetNumaNodeProcessorMask(node, proc_mask);
}

USHORT os::Kernel32Dll::RtlCaptureStackBackTrace(ULONG FrameToSkip,
  ULONG FrameToCapture, PVOID* BackTrace, PULONG BackTraceHash) {
    if (!initialized) {
      initialize();
    }

    if (_RtlCaptureStackBackTrace != NULL) {
      return _RtlCaptureStackBackTrace(FrameToSkip, FrameToCapture,
        BackTrace, BackTraceHash);
    } else {
      return 0;
    }
}

void os::Kernel32Dll::initializeCommon() {
  if (!initialized) {
    HMODULE handle = ::GetModuleHandle("Kernel32.dll");
    assert(handle != NULL, "Just check");
    _GetLargePageMinimum = (GetLargePageMinimum_Fn)::GetProcAddress(handle, "GetLargePageMinimum");
    _VirtualAllocExNuma = (VirtualAllocExNuma_Fn)::GetProcAddress(handle, "VirtualAllocExNuma");
    _GetNumaHighestNodeNumber = (GetNumaHighestNodeNumber_Fn)::GetProcAddress(handle, "GetNumaHighestNodeNumber");
    _GetNumaNodeProcessorMask = (GetNumaNodeProcessorMask_Fn)::GetProcAddress(handle, "GetNumaNodeProcessorMask");
    _RtlCaptureStackBackTrace = (RtlCaptureStackBackTrace_Fn)::GetProcAddress(handle, "RtlCaptureStackBackTrace");
    initialized = TRUE;
  }
}



#ifndef JDK6_OR_EARLIER

void os::Kernel32Dll::initialize() {
  initializeCommon();
}


// Kernel32 API
inline BOOL os::Kernel32Dll::SwitchToThread() {
  return ::SwitchToThread();
}

inline BOOL os::Kernel32Dll::SwitchToThreadAvailable() {
  return true;
}

  // Help tools
inline BOOL os::Kernel32Dll::HelpToolsAvailable() {
  return true;
}

inline HANDLE os::Kernel32Dll::CreateToolhelp32Snapshot(DWORD dwFlags,DWORD th32ProcessId) {
  return ::CreateToolhelp32Snapshot(dwFlags, th32ProcessId);
}

inline BOOL os::Kernel32Dll::Module32First(HANDLE hSnapshot,LPMODULEENTRY32 lpme) {
  return ::Module32First(hSnapshot, lpme);
}

inline BOOL os::Kernel32Dll::Module32Next(HANDLE hSnapshot,LPMODULEENTRY32 lpme) {
  return ::Module32Next(hSnapshot, lpme);
}


inline BOOL os::Kernel32Dll::GetNativeSystemInfoAvailable() {
  return true;
}

inline void os::Kernel32Dll::GetNativeSystemInfo(LPSYSTEM_INFO lpSystemInfo) {
  ::GetNativeSystemInfo(lpSystemInfo);
}

// PSAPI API
inline BOOL os::PSApiDll::EnumProcessModules(HANDLE hProcess, HMODULE *lpModule, DWORD cb, LPDWORD lpcbNeeded) {
  return ::EnumProcessModules(hProcess, lpModule, cb, lpcbNeeded);
}

inline DWORD os::PSApiDll::GetModuleFileNameEx(HANDLE hProcess, HMODULE hModule, LPTSTR lpFilename, DWORD nSize) {
  return ::GetModuleFileNameEx(hProcess, hModule, lpFilename, nSize);
}

inline BOOL os::PSApiDll::GetModuleInformation(HANDLE hProcess, HMODULE hModule, LPMODULEINFO lpmodinfo, DWORD cb) {
  return ::GetModuleInformation(hProcess, hModule, lpmodinfo, cb);
}

inline BOOL os::PSApiDll::PSApiAvailable() {
  return true;
}


// WinSock2 API
inline BOOL os::WinSock2Dll::WSAStartup(WORD wVersionRequested, LPWSADATA lpWSAData) {
  return ::WSAStartup(wVersionRequested, lpWSAData);
}

inline struct hostent* os::WinSock2Dll::gethostbyname(const char *name) {
  return ::gethostbyname(name);
}

inline BOOL os::WinSock2Dll::WinSock2Available() {
  return true;
}

// Advapi API
inline BOOL os::Advapi32Dll::AdjustTokenPrivileges(HANDLE TokenHandle,
   BOOL DisableAllPrivileges, PTOKEN_PRIVILEGES NewState, DWORD BufferLength,
   PTOKEN_PRIVILEGES PreviousState, PDWORD ReturnLength) {
     return ::AdjustTokenPrivileges(TokenHandle, DisableAllPrivileges, NewState,
       BufferLength, PreviousState, ReturnLength);
}

inline BOOL os::Advapi32Dll::OpenProcessToken(HANDLE ProcessHandle, DWORD DesiredAccess,
  PHANDLE TokenHandle) {
    return ::OpenProcessToken(ProcessHandle, DesiredAccess, TokenHandle);
}

inline BOOL os::Advapi32Dll::LookupPrivilegeValue(LPCTSTR lpSystemName, LPCTSTR lpName, PLUID lpLuid) {
  return ::LookupPrivilegeValue(lpSystemName, lpName, lpLuid);
}

inline BOOL os::Advapi32Dll::AdvapiAvailable() {
  return true;
}

#else
// Kernel32 API
typedef BOOL (WINAPI* SwitchToThread_Fn)(void);
typedef HANDLE (WINAPI* CreateToolhelp32Snapshot_Fn)(DWORD,DWORD);
typedef BOOL (WINAPI* Module32First_Fn)(HANDLE,LPMODULEENTRY32);
typedef BOOL (WINAPI* Module32Next_Fn)(HANDLE,LPMODULEENTRY32);
typedef void (WINAPI* GetNativeSystemInfo_Fn)(LPSYSTEM_INFO);

SwitchToThread_Fn           os::Kernel32Dll::_SwitchToThread = NULL;
CreateToolhelp32Snapshot_Fn os::Kernel32Dll::_CreateToolhelp32Snapshot = NULL;
Module32First_Fn            os::Kernel32Dll::_Module32First = NULL;
Module32Next_Fn             os::Kernel32Dll::_Module32Next = NULL;
GetNativeSystemInfo_Fn      os::Kernel32Dll::_GetNativeSystemInfo = NULL;

void os::Kernel32Dll::initialize() {
  if (!initialized) {
    HMODULE handle = ::GetModuleHandle("Kernel32.dll");
    assert(handle != NULL, "Just check");

    _SwitchToThread = (SwitchToThread_Fn)::GetProcAddress(handle, "SwitchToThread");
    _CreateToolhelp32Snapshot = (CreateToolhelp32Snapshot_Fn)
      ::GetProcAddress(handle, "CreateToolhelp32Snapshot");
    _Module32First = (Module32First_Fn)::GetProcAddress(handle, "Module32First");
    _Module32Next = (Module32Next_Fn)::GetProcAddress(handle, "Module32Next");
    _GetNativeSystemInfo = (GetNativeSystemInfo_Fn)::GetProcAddress(handle, "GetNativeSystemInfo");
    initializeCommon();  // resolve the functions that always need resolving

    initialized = TRUE;
  }
}

BOOL os::Kernel32Dll::SwitchToThread() {
  assert(initialized && _SwitchToThread != NULL,
    "SwitchToThreadAvailable() not yet called");
  return _SwitchToThread();
}


BOOL os::Kernel32Dll::SwitchToThreadAvailable() {
  if (!initialized) {
    initialize();
  }
  return _SwitchToThread != NULL;
}

// Help tools
BOOL os::Kernel32Dll::HelpToolsAvailable() {
  if (!initialized) {
    initialize();
  }
  return _CreateToolhelp32Snapshot != NULL &&
         _Module32First != NULL &&
         _Module32Next != NULL;
}

HANDLE os::Kernel32Dll::CreateToolhelp32Snapshot(DWORD dwFlags,DWORD th32ProcessId) {
  assert(initialized && _CreateToolhelp32Snapshot != NULL,
    "HelpToolsAvailable() not yet called");

  return _CreateToolhelp32Snapshot(dwFlags, th32ProcessId);
}

BOOL os::Kernel32Dll::Module32First(HANDLE hSnapshot,LPMODULEENTRY32 lpme) {
  assert(initialized && _Module32First != NULL,
    "HelpToolsAvailable() not yet called");

  return _Module32First(hSnapshot, lpme);
}

inline BOOL os::Kernel32Dll::Module32Next(HANDLE hSnapshot,LPMODULEENTRY32 lpme) {
  assert(initialized && _Module32Next != NULL,
    "HelpToolsAvailable() not yet called");

  return _Module32Next(hSnapshot, lpme);
}


BOOL os::Kernel32Dll::GetNativeSystemInfoAvailable() {
  if (!initialized) {
    initialize();
  }
  return _GetNativeSystemInfo != NULL;
}

void os::Kernel32Dll::GetNativeSystemInfo(LPSYSTEM_INFO lpSystemInfo) {
  assert(initialized && _GetNativeSystemInfo != NULL,
    "GetNativeSystemInfoAvailable() not yet called");

  _GetNativeSystemInfo(lpSystemInfo);
}

// PSAPI API


typedef BOOL (WINAPI *EnumProcessModules_Fn)(HANDLE, HMODULE *, DWORD, LPDWORD);
typedef BOOL (WINAPI *GetModuleFileNameEx_Fn)(HANDLE, HMODULE, LPTSTR, DWORD);;
typedef BOOL (WINAPI *GetModuleInformation_Fn)(HANDLE, HMODULE, LPMODULEINFO, DWORD);

EnumProcessModules_Fn   os::PSApiDll::_EnumProcessModules = NULL;
GetModuleFileNameEx_Fn  os::PSApiDll::_GetModuleFileNameEx = NULL;
GetModuleInformation_Fn os::PSApiDll::_GetModuleInformation = NULL;
BOOL                    os::PSApiDll::initialized = FALSE;

void os::PSApiDll::initialize() {
  if (!initialized) {
    HMODULE handle = os::win32::load_Windows_dll("PSAPI.DLL", NULL, 0);
    if (handle != NULL) {
      _EnumProcessModules = (EnumProcessModules_Fn)::GetProcAddress(handle,
        "EnumProcessModules");
      _GetModuleFileNameEx = (GetModuleFileNameEx_Fn)::GetProcAddress(handle,
        "GetModuleFileNameExA");
      _GetModuleInformation = (GetModuleInformation_Fn)::GetProcAddress(handle,
        "GetModuleInformation");
    }
    initialized = TRUE;
  }
}



BOOL os::PSApiDll::EnumProcessModules(HANDLE hProcess, HMODULE *lpModule, DWORD cb, LPDWORD lpcbNeeded) {
  assert(initialized && _EnumProcessModules != NULL,
    "PSApiAvailable() not yet called");
  return _EnumProcessModules(hProcess, lpModule, cb, lpcbNeeded);
}

DWORD os::PSApiDll::GetModuleFileNameEx(HANDLE hProcess, HMODULE hModule, LPTSTR lpFilename, DWORD nSize) {
  assert(initialized && _GetModuleFileNameEx != NULL,
    "PSApiAvailable() not yet called");
  return _GetModuleFileNameEx(hProcess, hModule, lpFilename, nSize);
}

BOOL os::PSApiDll::GetModuleInformation(HANDLE hProcess, HMODULE hModule, LPMODULEINFO lpmodinfo, DWORD cb) {
  assert(initialized && _GetModuleInformation != NULL,
    "PSApiAvailable() not yet called");
  return _GetModuleInformation(hProcess, hModule, lpmodinfo, cb);
}

BOOL os::PSApiDll::PSApiAvailable() {
  if (!initialized) {
    initialize();
  }
  return _EnumProcessModules != NULL &&
    _GetModuleFileNameEx != NULL &&
    _GetModuleInformation != NULL;
}


// WinSock2 API
typedef int (PASCAL FAR* WSAStartup_Fn)(WORD, LPWSADATA);
typedef struct hostent *(PASCAL FAR *gethostbyname_Fn)(...);

WSAStartup_Fn    os::WinSock2Dll::_WSAStartup = NULL;
gethostbyname_Fn os::WinSock2Dll::_gethostbyname = NULL;
BOOL             os::WinSock2Dll::initialized = FALSE;

void os::WinSock2Dll::initialize() {
  if (!initialized) {
    HMODULE handle = os::win32::load_Windows_dll("ws2_32.dll", NULL, 0);
    if (handle != NULL) {
      _WSAStartup = (WSAStartup_Fn)::GetProcAddress(handle, "WSAStartup");
      _gethostbyname = (gethostbyname_Fn)::GetProcAddress(handle, "gethostbyname");
    }
    initialized = TRUE;
  }
}


BOOL os::WinSock2Dll::WSAStartup(WORD wVersionRequested, LPWSADATA lpWSAData) {
  assert(initialized && _WSAStartup != NULL,
    "WinSock2Available() not yet called");
  return _WSAStartup(wVersionRequested, lpWSAData);
}

struct hostent* os::WinSock2Dll::gethostbyname(const char *name) {
  assert(initialized && _gethostbyname != NULL,
    "WinSock2Available() not yet called");
  return _gethostbyname(name);
}

BOOL os::WinSock2Dll::WinSock2Available() {
  if (!initialized) {
    initialize();
  }
  return _WSAStartup != NULL &&
    _gethostbyname != NULL;
}

typedef BOOL (WINAPI *AdjustTokenPrivileges_Fn)(HANDLE, BOOL, PTOKEN_PRIVILEGES, DWORD, PTOKEN_PRIVILEGES, PDWORD);
typedef BOOL (WINAPI *OpenProcessToken_Fn)(HANDLE, DWORD, PHANDLE);
typedef BOOL (WINAPI *LookupPrivilegeValue_Fn)(LPCTSTR, LPCTSTR, PLUID);

AdjustTokenPrivileges_Fn os::Advapi32Dll::_AdjustTokenPrivileges = NULL;
OpenProcessToken_Fn      os::Advapi32Dll::_OpenProcessToken = NULL;
LookupPrivilegeValue_Fn  os::Advapi32Dll::_LookupPrivilegeValue = NULL;
BOOL                     os::Advapi32Dll::initialized = FALSE;

void os::Advapi32Dll::initialize() {
  if (!initialized) {
    HMODULE handle = os::win32::load_Windows_dll("advapi32.dll", NULL, 0);
    if (handle != NULL) {
      _AdjustTokenPrivileges = (AdjustTokenPrivileges_Fn)::GetProcAddress(handle,
        "AdjustTokenPrivileges");
      _OpenProcessToken = (OpenProcessToken_Fn)::GetProcAddress(handle,
        "OpenProcessToken");
      _LookupPrivilegeValue = (LookupPrivilegeValue_Fn)::GetProcAddress(handle,
        "LookupPrivilegeValueA");
    }
    initialized = TRUE;
  }
}

BOOL os::Advapi32Dll::AdjustTokenPrivileges(HANDLE TokenHandle,
   BOOL DisableAllPrivileges, PTOKEN_PRIVILEGES NewState, DWORD BufferLength,
   PTOKEN_PRIVILEGES PreviousState, PDWORD ReturnLength) {
   assert(initialized && _AdjustTokenPrivileges != NULL,
     "AdvapiAvailable() not yet called");
   return _AdjustTokenPrivileges(TokenHandle, DisableAllPrivileges, NewState,
       BufferLength, PreviousState, ReturnLength);
}

BOOL os::Advapi32Dll::OpenProcessToken(HANDLE ProcessHandle, DWORD DesiredAccess,
  PHANDLE TokenHandle) {
   assert(initialized && _OpenProcessToken != NULL,
     "AdvapiAvailable() not yet called");
    return _OpenProcessToken(ProcessHandle, DesiredAccess, TokenHandle);
}

BOOL os::Advapi32Dll::LookupPrivilegeValue(LPCTSTR lpSystemName, LPCTSTR lpName, PLUID lpLuid) {
   assert(initialized && _LookupPrivilegeValue != NULL,
     "AdvapiAvailable() not yet called");
  return _LookupPrivilegeValue(lpSystemName, lpName, lpLuid);
}

BOOL os::Advapi32Dll::AdvapiAvailable() {
  if (!initialized) {
    initialize();
  }
  return _AdjustTokenPrivileges != NULL &&
    _OpenProcessToken != NULL &&
    _LookupPrivilegeValue != NULL;
}

#endif

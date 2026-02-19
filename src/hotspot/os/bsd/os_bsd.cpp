/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/vmSymbols.hpp"
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
#include "os_bsd.inline.hpp"
#include "os_posix.inline.hpp"
#include "prims/jniFastGetField.hpp"
#include "prims/jvm_misc.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/globals.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/osInfo.hpp"
#include "runtime/osThread.hpp"
#include "runtime/perfMemory.hpp"
#include "runtime/semaphore.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "runtime/threads.hpp"
#include "runtime/timer.hpp"
#include "services/attachListener.hpp"
#include "services/runtimeService.hpp"
#include "signals_posix.hpp"
#include "utilities/align.hpp"
#include "utilities/decoder.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/events.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/vmError.hpp"
#if INCLUDE_JFR
#include "jfr/jfrEvents.hpp"
#include "jfr/support/jfrNativeLibraryLoadEvent.hpp"
#endif

// put OS-includes here
# include <dlfcn.h>
# include <errno.h>
# include <fcntl.h>
# include <fenv.h>
# include <inttypes.h>
# include <poll.h>
# include <pthread.h>
# include <pwd.h>
# include <signal.h>
# include <stdint.h>
# include <stdio.h>
# include <string.h>
# include <sys/ioctl.h>
# include <sys/mman.h>
# include <sys/param.h>
# include <sys/resource.h>
# include <sys/socket.h>
# include <sys/stat.h>
# include <sys/syscall.h>
# include <sys/sysctl.h>
# include <sys/time.h>
# include <sys/times.h>
# include <sys/types.h>
# include <time.h>
# include <unistd.h>

#if defined(__FreeBSD__) || defined(__NetBSD__)
  #include <elf.h>
#endif

#ifdef __APPLE__
  #include <mach/task_info.h>
  #include <mach-o/dyld.h>
#endif

#ifndef MAP_ANONYMOUS
  #define MAP_ANONYMOUS MAP_ANON
#endif

#define MAX_PATH    (2 * K)

////////////////////////////////////////////////////////////////////////////////
// global variables
physical_memory_size_type os::Bsd::_physical_memory = 0;

#ifdef __APPLE__
mach_timebase_info_data_t os::Bsd::_timebase_info = {0, 0};
volatile uint64_t         os::Bsd::_max_abstime   = 0;
#endif
pthread_t os::Bsd::_main_thread;

#if defined(__APPLE__) && defined(__x86_64__)
static const int processor_id_unassigned = -1;
static const int processor_id_assigning = -2;
static const int processor_id_map_size = 256;
static volatile int processor_id_map[processor_id_map_size];
static volatile int processor_id_next = 0;
#endif

////////////////////////////////////////////////////////////////////////////////
// utility functions

bool os::available_memory(physical_memory_size_type& value) {
  return Bsd::available_memory(value);
}

bool os::Machine::available_memory(physical_memory_size_type& value) {
  return Bsd::available_memory(value);
}

bool os::free_memory(physical_memory_size_type& value) {
  return Bsd::available_memory(value);
}

bool os::Machine::free_memory(physical_memory_size_type& value) {
  return Bsd::available_memory(value);
}

// Available here means free. Note that this number is of no much use. As an estimate
// for future memory pressure it is far too conservative, since MacOS will use a lot
// of unused memory for caches, and return it willingly in case of needs.
bool os::Bsd::available_memory(physical_memory_size_type& value) {
  physical_memory_size_type available = physical_memory() >> 2;
#ifdef __APPLE__
  mach_msg_type_number_t count = HOST_VM_INFO64_COUNT;
  vm_statistics64_data_t vmstat;
  kern_return_t kerr = host_statistics64(mach_host_self(), HOST_VM_INFO64,
                                         (host_info64_t)&vmstat, &count);
  assert(kerr == KERN_SUCCESS,
         "host_statistics64 failed - check mach_host_self() and count");
  if (kerr == KERN_SUCCESS) {
    // free_count is just a lowerbound, other page categories can be freed too and make memory available
    available = (vmstat.free_count + vmstat.inactive_count + vmstat.purgeable_count) * os::vm_page_size();
  } else {
    return false;
  }
#endif
  value = available;
  return true;
}

// for more info see :
// https://man.openbsd.org/sysctl.2
void os::Bsd::print_uptime_info(outputStream* st) {
  struct timeval boottime;
  size_t len = sizeof(boottime);
  int mib[2];
  mib[0] = CTL_KERN;
  mib[1] = KERN_BOOTTIME;

  if (sysctl(mib, 2, &boottime, &len, nullptr, 0) >= 0) {
    time_t bootsec = boottime.tv_sec;
    time_t currsec = time(nullptr);
    os::print_dhm(st, "OS uptime:", (long) difftime(currsec, bootsec));
  }
}

bool os::total_swap_space(physical_memory_size_type& value) {
  return Machine::total_swap_space(value);
}

bool os::Machine::total_swap_space(physical_memory_size_type& value) {
#if defined(__APPLE__)
  struct xsw_usage vmusage;
  size_t size = sizeof(vmusage);
  if (sysctlbyname("vm.swapusage", &vmusage, &size, nullptr, 0) != 0) {
    return false;
  }
  value = static_cast<physical_memory_size_type>(vmusage.xsu_total);
  return true;
#else
  return false;
#endif
}

bool os::free_swap_space(physical_memory_size_type& value) {
  return Machine::free_swap_space(value);
}

bool os::Machine::free_swap_space(physical_memory_size_type& value) {
#if defined(__APPLE__)
  struct xsw_usage vmusage;
  size_t size = sizeof(vmusage);
  if (sysctlbyname("vm.swapusage", &vmusage, &size, nullptr, 0) != 0) {
    return false;
  }
  value = static_cast<physical_memory_size_type>(vmusage.xsu_avail);
  return true;
#else
  return false;
#endif
}

physical_memory_size_type os::physical_memory() {
  return Bsd::physical_memory();
}

physical_memory_size_type os::Machine::physical_memory() {
  return Bsd::physical_memory();
}

size_t os::rss() {
  size_t rss = 0;
#ifdef __APPLE__
  mach_task_basic_info info;
  mach_msg_type_number_t count = MACH_TASK_BASIC_INFO_COUNT;

  kern_return_t ret = task_info(mach_task_self(), MACH_TASK_BASIC_INFO,
                                (task_info_t)&info, &count);
  if (ret == KERN_SUCCESS) {
    rss = info.resident_size;
  }
#endif // __APPLE__

  return rss;
}

// Cpu architecture string
#if   defined(ZERO)
static char cpu_arch[] = ZERO_LIBARCH;
#elif defined(AMD64)
static char cpu_arch[] = "amd64";
#elif defined(ARM)
static char cpu_arch[] = "arm";
#elif defined(AARCH64)
static char cpu_arch[] = "aarch64";
#elif defined(PPC32)
static char cpu_arch[] = "ppc";
#else
  #error Add appropriate cpu_arch setting
#endif

void os::Bsd::initialize_system_info() {
  int mib[2];
  size_t len;
  int cpu_val;
  julong mem_val;

  // get processors count via hw.ncpus sysctl
  mib[0] = CTL_HW;
  mib[1] = HW_NCPU;
  len = sizeof(cpu_val);
  if (sysctl(mib, 2, &cpu_val, &len, nullptr, 0) != -1 && cpu_val >= 1) {
    assert(len == sizeof(cpu_val), "unexpected data size");
    set_processor_count(cpu_val);
  } else {
    set_processor_count(1);   // fallback
  }

#if defined(__APPLE__) && defined(__x86_64__)
  // initialize processor id map
  for (int i = 0; i < processor_id_map_size; i++) {
    processor_id_map[i] = processor_id_unassigned;
  }
#endif

  // get physical memory via hw.memsize sysctl (hw.memsize is used
  // since it returns a 64 bit value)
  mib[0] = CTL_HW;

#if defined (HW_MEMSIZE) // Apple
  mib[1] = HW_MEMSIZE;
#elif defined(HW_PHYSMEM) // Most of BSD
  mib[1] = HW_PHYSMEM;
#elif defined(HW_REALMEM) // Old FreeBSD
  mib[1] = HW_REALMEM;
#else
  #error No ways to get physmem
#endif

  len = sizeof(mem_val);
  if (sysctl(mib, 2, &mem_val, &len, nullptr, 0) != -1) {
    assert(len == sizeof(mem_val), "unexpected data size");
    _physical_memory = static_cast<physical_memory_size_type>(mem_val);
  } else {
    _physical_memory = 256 * 1024 * 1024;       // fallback (XXXBSD?)
  }

#ifdef __OpenBSD__
  {
    // limit _physical_memory memory view on OpenBSD since
    // datasize rlimit restricts us anyway.
    struct rlimit limits;
    getrlimit(RLIMIT_DATA, &limits);
    _physical_memory = MIN2(_physical_memory, static_cast<physical_memory_size_type>(limits.rlim_cur));
  }
#endif
}

#ifdef __APPLE__
static const char *get_home() {
  const char *home_dir = ::getenv("HOME");
  if ((home_dir == nullptr) || (*home_dir == '\0')) {
    struct passwd *passwd_info = getpwuid(geteuid());
    if (passwd_info != nullptr) {
      home_dir = passwd_info->pw_dir;
    }
  }

  return home_dir;
}
#endif

void os::init_system_properties_values() {
  // The next steps are taken in the product version:
  //
  // Obtain the JAVA_HOME value from the location of libjvm.so.
  // This library should be located at:
  // <JAVA_HOME>/jre/lib/<arch>/{client|server}/libjvm.so.
  //
  // If "/jre/lib/" appears at the right place in the path, then we
  // assume libjvm.so is installed in a JDK and we use this path.
  //
  // Otherwise exit with message: "Could not create the Java virtual machine."
  //
  // The following extra steps are taken in the debugging version:
  //
  // If "/jre/lib/" does NOT appear at the right place in the path
  // instead of exit check for $JAVA_HOME environment variable.
  //
  // If it is defined and we are able to locate $JAVA_HOME/jre/lib/<arch>,
  // then we append a fake suffix "hotspot/libjvm.so" to this path so
  // it looks like libjvm.so is installed there
  // <JAVA_HOME>/jre/lib/<arch>/hotspot/libjvm.so.
  //
  // Otherwise exit.
  //
  // Important note: if the location of libjvm.so changes this
  // code needs to be changed accordingly.

  // See ld(1):
  //      The linker uses the following search paths to locate required
  //      shared libraries:
  //        1: ...
  //        ...
  //        7: The default directories, normally /lib and /usr/lib.
#ifndef DEFAULT_LIBPATH
  #ifndef OVERRIDE_LIBPATH
    #define DEFAULT_LIBPATH "/lib:/usr/lib"
  #else
    #define DEFAULT_LIBPATH OVERRIDE_LIBPATH
  #endif
#endif

// Base path of extensions installed on the system.
#define SYS_EXT_DIR     "/usr/java/packages"
#define EXTENSIONS_DIR  "/lib/ext"

#ifndef __APPLE__

  // Buffer that fits several snprintfs.
  // Note that the space for the colon and the trailing null are provided
  // by the nulls included by the sizeof operator.
  const size_t bufsize =
    MAX2((size_t)MAXPATHLEN,  // For dll_dir & friends.
         (size_t)MAXPATHLEN + sizeof(EXTENSIONS_DIR) + sizeof(SYS_EXT_DIR) + sizeof(EXTENSIONS_DIR)); // extensions dir
  char *buf = NEW_C_HEAP_ARRAY(char, bufsize, mtInternal);

  // sysclasspath, java_home, dll_dir
  {
    char *pslash;
    os::jvm_path(buf, bufsize);

    // Found the full path to libjvm.so.
    // Now cut the path to <java_home>/jre if we can.
    *(strrchr(buf, '/')) = '\0'; // Get rid of /libjvm.so.
    pslash = strrchr(buf, '/');
    if (pslash != nullptr) {
      *pslash = '\0';            // Get rid of /{client|server|hotspot}.
    }
    Arguments::set_dll_dir(buf);

    if (pslash != nullptr) {
      pslash = strrchr(buf, '/');
      if (pslash != nullptr) {
        *pslash = '\0';          // Get rid of /<arch>.
        pslash = strrchr(buf, '/');
        if (pslash != nullptr) {
          *pslash = '\0';        // Get rid of /lib.
        }
      }
    }
    Arguments::set_java_home(buf);
    if (!set_boot_path('/', ':')) {
      vm_exit_during_initialization("Failed setting boot class path.", nullptr);
    }
  }

  // Where to look for native libraries.
  //
  // Note: Due to a legacy implementation, most of the library path
  // is set in the launcher. This was to accommodate linking restrictions
  // on legacy Bsd implementations (which are no longer supported).
  // Eventually, all the library path setting will be done here.
  //
  // However, to prevent the proliferation of improperly built native
  // libraries, the new path component /usr/java/packages is added here.
  // Eventually, all the library path setting will be done here.
  {
    // Get the user setting of LD_LIBRARY_PATH, and prepended it. It
    // should always exist (until the legacy problem cited above is
    // addressed).
    const char *v = ::getenv("LD_LIBRARY_PATH");
    const char *v_colon = ":";
    if (v == nullptr) { v = ""; v_colon = ""; }
    // That's +1 for the colon and +1 for the trailing '\0'.
    const size_t ld_library_path_size = strlen(v) + 1 + sizeof(SYS_EXT_DIR) +
            sizeof("/lib/") + strlen(cpu_arch) + sizeof(DEFAULT_LIBPATH) + 1;
    char *ld_library_path = NEW_C_HEAP_ARRAY(char, ld_library_path_size, mtInternal);
    os::snprintf_checked(ld_library_path, ld_library_path_size, "%s%s" SYS_EXT_DIR "/lib/%s:" DEFAULT_LIBPATH, v, v_colon, cpu_arch);
    Arguments::set_library_path(ld_library_path);
    FREE_C_HEAP_ARRAY(char, ld_library_path);
  }

  // Extensions directories.
  os::snprintf_checked(buf, bufsize, "%s" EXTENSIONS_DIR ":" SYS_EXT_DIR EXTENSIONS_DIR, Arguments::get_java_home());
  Arguments::set_ext_dirs(buf);

  FREE_C_HEAP_ARRAY(char, buf);

#else // __APPLE__

  #define SYS_EXTENSIONS_DIR   "/Library/Java/Extensions"
  #define SYS_EXTENSIONS_DIRS  SYS_EXTENSIONS_DIR ":/Network" SYS_EXTENSIONS_DIR ":/System" SYS_EXTENSIONS_DIR ":/usr/lib/java"

  const char *user_home_dir = get_home();
  // The null in SYS_EXTENSIONS_DIRS counts for the size of the colon after user_home_dir.
  size_t system_ext_size = strlen(user_home_dir) + sizeof(SYS_EXTENSIONS_DIR) +
    sizeof(SYS_EXTENSIONS_DIRS);

  // Buffer that fits several snprintfs.
  // Note that the space for the colon and the trailing null are provided
  // by the nulls included by the sizeof operator.
  const size_t bufsize =
    MAX2((size_t)MAXPATHLEN,  // for dll_dir & friends.
         (size_t)MAXPATHLEN + sizeof(EXTENSIONS_DIR) + system_ext_size); // extensions dir
  char *buf = NEW_C_HEAP_ARRAY(char, bufsize, mtInternal);

  // sysclasspath, java_home, dll_dir
  {
    char *pslash;
    os::jvm_path(buf, bufsize);

    // Found the full path to libjvm.so.
    // Now cut the path to <java_home>/jre if we can.
    *(strrchr(buf, '/')) = '\0'; // Get rid of /libjvm.so.
    pslash = strrchr(buf, '/');
    if (pslash != nullptr) {
      *pslash = '\0';            // Get rid of /{client|server|hotspot}.
    }
    if (is_vm_statically_linked()) {
      strcat(buf, "/lib");
    }

    Arguments::set_dll_dir(buf);

    if (pslash != nullptr) {
      pslash = strrchr(buf, '/');
      if (pslash != nullptr) {
        *pslash = '\0';          // Get rid of /lib.
      }
    }
    Arguments::set_java_home(buf);
    if (!set_boot_path('/', ':')) {
        vm_exit_during_initialization("Failed setting boot class path.", nullptr);
    }
  }

  // Where to look for native libraries.
  //
  // Note: Due to a legacy implementation, most of the library path
  // is set in the launcher. This was to accommodate linking restrictions
  // on legacy Bsd implementations (which are no longer supported).
  // Eventually, all the library path setting will be done here.
  //
  // However, to prevent the proliferation of improperly built native
  // libraries, the new path component /usr/java/packages is added here.
  // Eventually, all the library path setting will be done here.
  {
    // Get the user setting of LD_LIBRARY_PATH, and prepended it. It
    // should always exist (until the legacy problem cited above is
    // addressed).
    // Prepend the default path with the JAVA_LIBRARY_PATH so that the app launcher code
    // can specify a directory inside an app wrapper
    const char *l = ::getenv("JAVA_LIBRARY_PATH");
    const char *l_colon = ":";
    if (l == nullptr) { l = ""; l_colon = ""; }

    const char *v = ::getenv("DYLD_LIBRARY_PATH");
    const char *v_colon = ":";
    if (v == nullptr) { v = ""; v_colon = ""; }

    // Apple's Java6 has "." at the beginning of java.library.path.
    // OpenJDK on Windows has "." at the end of java.library.path.
    // OpenJDK on Linux and Solaris don't have "." in java.library.path
    // at all. To ease the transition from Apple's Java6 to OpenJDK7,
    // "." is appended to the end of java.library.path. Yes, this
    // could cause a change in behavior, but Apple's Java6 behavior
    // can be achieved by putting "." at the beginning of the
    // JAVA_LIBRARY_PATH environment variable.
    const size_t ld_library_path_size = strlen(v) + 1 + strlen(l) + 1 + system_ext_size + 3;
    char *ld_library_path = NEW_C_HEAP_ARRAY(char, ld_library_path_size, mtInternal);
    os::snprintf_checked(ld_library_path, ld_library_path_size, "%s%s%s%s%s" SYS_EXTENSIONS_DIR ":" SYS_EXTENSIONS_DIRS ":.",
            v, v_colon, l, l_colon, user_home_dir);
    Arguments::set_library_path(ld_library_path);
    FREE_C_HEAP_ARRAY(char, ld_library_path);
  }

  // Extensions directories.
  //
  // Note that the space for the colon and the trailing null are provided
  // by the nulls included by the sizeof operator (so actually one byte more
  // than necessary is allocated).
  os::snprintf_checked(buf, bufsize, "%s" SYS_EXTENSIONS_DIR ":%s" EXTENSIONS_DIR ":" SYS_EXTENSIONS_DIRS,
                       user_home_dir, Arguments::get_java_home());
  Arguments::set_ext_dirs(buf);

  FREE_C_HEAP_ARRAY(char, buf);

#undef SYS_EXTENSIONS_DIR
#undef SYS_EXTENSIONS_DIRS

#endif // __APPLE__

#undef SYS_EXT_DIR
#undef EXTENSIONS_DIR
}

//////////////////////////////////////////////////////////////////////////////
// create new thread

#ifdef __APPLE__
// library handle for calling objc_registerThreadWithCollector()
// without static linking to the libobjc library
  #define OBJC_LIB "/usr/lib/libobjc.dylib"
  #define OBJC_GCREGISTER "objc_registerThreadWithCollector"
typedef void (*objc_registerThreadWithCollector_t)();
extern "C" objc_registerThreadWithCollector_t objc_registerThreadWithCollectorFunction;
objc_registerThreadWithCollector_t objc_registerThreadWithCollectorFunction = nullptr;
#endif

// Thread start routine for all newly created threads
static void *thread_native_entry(Thread *thread) {

  thread->record_stack_base_and_size();
  thread->initialize_thread_current();

  OSThread* osthread = thread->osthread();
  Monitor* sync = osthread->startThread_lock();

  osthread->set_thread_id(os::Bsd::gettid());

#ifdef __APPLE__
  // Store unique OS X thread id used by SA
  osthread->set_unique_thread_id();
#endif

  // initialize signal mask for this thread
  PosixSignals::hotspot_sigmask(thread);

  // initialize floating point control register
  os::Bsd::init_thread_fpu_state();

#ifdef __APPLE__
  // register thread with objc gc
  if (objc_registerThreadWithCollectorFunction != nullptr) {
    objc_registerThreadWithCollectorFunction();
  }
#endif

  // handshaking with parent thread
  {
    MutexLocker ml(sync, Mutex::_no_safepoint_check_flag);

    // notify parent thread
    osthread->set_state(INITIALIZED);
    sync->notify_all();

    // wait until os::start_thread()
    while (osthread->get_state() == INITIALIZED) {
      sync->wait_without_safepoint_check();
    }
  }

  log_info(os, thread)("Thread is alive (tid: %zu, pthread id: %zu).",
    os::current_thread_id(), (uintx) pthread_self());

  // call one more level start routine
  thread->call_run();

  // Note: at this point the thread object may already have deleted itself.
  // Prevent dereferencing it from here on out.
  thread = nullptr;

  log_info(os, thread)("Thread finished (tid: %zu, pthread id: %zu).",
    os::current_thread_id(), (uintx) pthread_self());

  return nullptr;
}

bool os::create_thread(Thread* thread, ThreadType thr_type,
                       size_t req_stack_size) {
  assert(thread->osthread() == nullptr, "caller responsible");

  // Allocate the OSThread object
  OSThread* osthread = new (std::nothrow) OSThread();
  if (osthread == nullptr) {
    return false;
  }

  // Initial state is ALLOCATED but not INITIALIZED
  osthread->set_state(ALLOCATED);

  thread->set_osthread(osthread);

  // init thread attributes
  pthread_attr_t attr;
  int rslt = pthread_attr_init(&attr);
  if (rslt != 0) {
    thread->set_osthread(nullptr);
    delete osthread;
    return false;
  }
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

  // calculate stack size if it's not specified by caller
  size_t stack_size = os::Posix::get_initial_stack_size(thr_type, req_stack_size);
  int status = pthread_attr_setstacksize(&attr, stack_size);
  assert_status(status == 0, status, "pthread_attr_setstacksize");

  ThreadState state;

  {

    ResourceMark rm;
    pthread_t tid;
    int ret = 0;
    int trials_remaining = 4;
    useconds_t next_delay = 1000;
    while (true) {
      ret = pthread_create(&tid, &attr, (void* (*)(void*)) thread_native_entry, thread);

      if (ret != EAGAIN) {
        break;
      }

      if (--trials_remaining <= 0) {
        break;
      }

      log_debug(os, thread)("Failed to start native thread (%s), retrying after %dus.", os::errno_name(ret), next_delay);
      ::usleep(next_delay);
      next_delay *= 2;
    }

    char buf[64];
    if (ret == 0) {
      log_info(os, thread)("Thread \"%s\" started (pthread id: %zu, attributes: %s). ",
                           thread->name(), (uintx) tid, os::Posix::describe_pthread_attr(buf, sizeof(buf), &attr));
    } else {
      log_warning(os, thread)("Failed to start thread \"%s\" - pthread_create failed (%s) for attributes: %s.",
                              thread->name(), os::errno_name(ret), os::Posix::describe_pthread_attr(buf, sizeof(buf), &attr));
      // Log some OS information which might explain why creating the thread failed.
      log_info(os, thread)("Number of threads approx. running in the VM: %d", Threads::number_of_threads());
      LogStream st(Log(os, thread)::info());
      os::Posix::print_rlimit_info(&st);
      os::print_memory_info(&st);
    }

    pthread_attr_destroy(&attr);

    if (ret != 0) {
      // Need to clean up stuff we've allocated so far
      thread->set_osthread(nullptr);
      delete osthread;
      return false;
    }

    // Store pthread info into the OSThread
    osthread->set_pthread_id(tid);

    // Wait until child thread is either initialized or aborted
    {
      Monitor* sync_with_child = osthread->startThread_lock();
      MutexLocker ml(sync_with_child, Mutex::_no_safepoint_check_flag);
      while ((state = osthread->get_state()) == ALLOCATED) {
        sync_with_child->wait_without_safepoint_check();
      }
    }

  }

  // The thread is returned suspended (in state INITIALIZED),
  // and is started higher up in the call chain
  assert(state == INITIALIZED, "race condition");
  return true;
}

/////////////////////////////////////////////////////////////////////////////
// attach existing thread

// bootstrap the main thread
bool os::create_main_thread(JavaThread* thread) {
  assert(os::Bsd::_main_thread == pthread_self(), "should be called inside main thread");
  return create_attached_thread(thread);
}

bool os::create_attached_thread(JavaThread* thread) {
#ifdef ASSERT
  thread->verify_not_published();
#endif

  // Allocate the OSThread object
  OSThread* osthread = new (std::nothrow) OSThread();

  if (osthread == nullptr) {
    return false;
  }

  osthread->set_thread_id(os::Bsd::gettid());

#ifdef __APPLE__
  // Store unique OS X thread id used by SA
  osthread->set_unique_thread_id();
#endif

  // Store pthread info into the OSThread
  osthread->set_pthread_id(::pthread_self());

  // initialize floating point control register
  os::Bsd::init_thread_fpu_state();

  // Initial thread state is RUNNABLE
  osthread->set_state(RUNNABLE);

  thread->set_osthread(osthread);

  // initialize signal mask for this thread
  // and save the caller's signal mask
  PosixSignals::hotspot_sigmask(thread);

  log_info(os, thread)("Thread attached (tid: %zu, pthread id: %zu"
                       ", stack: " PTR_FORMAT " - " PTR_FORMAT " (%zuK) ).",
                       os::current_thread_id(), (uintx) pthread_self(),
                       p2i(thread->stack_base()), p2i(thread->stack_end()), thread->stack_size() / K);
  return true;
}

void os::pd_start_thread(Thread* thread) {
  OSThread * osthread = thread->osthread();
  assert(osthread->get_state() != INITIALIZED, "just checking");
  Monitor* sync_with_child = osthread->startThread_lock();
  MutexLocker ml(sync_with_child, Mutex::_no_safepoint_check_flag);
  sync_with_child->notify();
}

// Free Bsd resources related to the OSThread
void os::free_thread(OSThread* osthread) {
  assert(osthread != nullptr, "osthread not set");

  // We are told to free resources of the argument thread, but we can only really operate
  // on the current thread. The current thread may be already detached at this point.
  assert(Thread::current_or_null() == nullptr || Thread::current()->osthread() == osthread,
         "os::free_thread but not current thread");

  // Restore caller's signal mask
  sigset_t sigmask = osthread->caller_sigmask();
  pthread_sigmask(SIG_SETMASK, &sigmask, nullptr);

  delete osthread;
}

////////////////////////////////////////////////////////////////////////////////
// time support

#ifdef __APPLE__
void os::Bsd::clock_init() {
  mach_timebase_info(&_timebase_info);
}
#else
void os::Bsd::clock_init() {
  // Nothing to do
}
#endif



#ifdef __APPLE__

jlong os::javaTimeNanos() {
  const uint64_t tm = mach_absolute_time();
  const uint64_t now = (tm * Bsd::_timebase_info.numer) / Bsd::_timebase_info.denom;
  const uint64_t prev = Bsd::_max_abstime;
  if (now <= prev) {
    return prev;   // same or retrograde time;
  }
  const uint64_t obsv = AtomicAccess::cmpxchg(&Bsd::_max_abstime, prev, now);
  assert(obsv >= prev, "invariant");   // Monotonicity
  // If the CAS succeeded then we're done and return "now".
  // If the CAS failed and the observed value "obsv" is >= now then
  // we should return "obsv".  If the CAS failed and now > obsv > prv then
  // some other thread raced this thread and installed a new value, in which case
  // we could either (a) retry the entire operation, (b) retry trying to install now
  // or (c) just return obsv.  We use (c).   No loop is required although in some cases
  // we might discard a higher "now" value in deference to a slightly lower but freshly
  // installed obsv value.   That's entirely benign -- it admits no new orderings compared
  // to (a) or (b) -- and greatly reduces coherence traffic.
  // We might also condition (c) on the magnitude of the delta between obsv and now.
  // Avoiding excessive CAS operations to hot RW locations is critical.
  // See https://blogs.oracle.com/dave/entry/cas_and_cache_trivia_invalidate
  // https://web.archive.org/web/20131214182431/https://blogs.oracle.com/dave/entry/cas_and_cache_trivia_invalidate
  return (prev == obsv) ? now : obsv;
}

void os::javaTimeNanos_info(jvmtiTimerInfo *info_ptr) {
  info_ptr->max_value = all_bits_jlong;
  info_ptr->may_skip_backward = false;      // not subject to resetting or drifting
  info_ptr->may_skip_forward = false;       // not subject to resetting or drifting
  info_ptr->kind = JVMTI_TIMER_ELAPSED;     // elapsed not CPU time
}
#endif // __APPLE__

// Information of current thread in variety of formats
pid_t os::Bsd::gettid() {
  int retval = -1;

#ifdef __APPLE__ // XNU kernel
  mach_port_t port = mach_thread_self();
  guarantee(MACH_PORT_VALID(port), "just checking");
  mach_port_deallocate(mach_task_self(), port);
  return (pid_t)port;

#else
  #ifdef __FreeBSD__
  retval = syscall(SYS_thr_self);
  #else
    #ifdef __OpenBSD__
  retval = syscall(SYS_getthrid);
    #else
      #ifdef __NetBSD__
  retval = (pid_t) syscall(SYS__lwp_self);
      #endif
    #endif
  #endif
#endif

  if (retval == -1) {
    return getpid();
  }
}

// Returns the uid of a process or -1 on error.
uid_t os::Bsd::get_process_uid(pid_t pid) {
  struct kinfo_proc kp;
  size_t size = sizeof kp;
  int mib_kern[4] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, pid};
  if (sysctl(mib_kern, 4, &kp, &size, nullptr, 0) == 0) {
    if (size > 0 && kp.kp_proc.p_pid == pid) {
      return kp.kp_eproc.e_ucred.cr_uid;
    }
  }
  return (uid_t)-1;
}

// Returns true if the process is running as root.
bool os::Bsd::is_process_root(pid_t pid) {
  uid_t uid = get_process_uid(pid);
  return (uid != (uid_t)-1) ? os::Posix::is_root(uid) : false;
}

#ifdef __APPLE__

// macOS has a secure per-user temporary directory.
// Root can attach to a non-root process, hence it needs
// to lookup /var/folders for the user specific temporary directory
// of the form /var/folders/*/*/T, that contains PERFDATA_NAME_user
// directory.
static const char VAR_FOLDERS[] = "/var/folders/";
int os::Bsd::get_user_tmp_dir_macos(const char* user, int vmid, char* output_path, int output_size) {

  // read the var/folders directory
  DIR* varfolders_dir = os::opendir(VAR_FOLDERS);
  if (varfolders_dir != nullptr) {

    // var/folders directory contains 2-characters subdirectories (buckets)
    struct dirent* bucket_de;

    // loop until the PERFDATA_NAME_user directory has been found
    while ((bucket_de = os::readdir(varfolders_dir)) != nullptr) {
      // skip over files and special "." and ".."
      if (bucket_de->d_type != DT_DIR || bucket_de->d_name[0] == '.') {
        continue;
      }
      // absolute path to the bucket
      char bucket[PATH_MAX];
      int b = os::snprintf(bucket, PATH_MAX, "%s%s/", VAR_FOLDERS, bucket_de->d_name);

      // the total length of the absolute path must not exceed the buffer size
      if (b >= PATH_MAX || b < 0) {
        continue;
      }
      // each bucket contains next level subdirectories
      DIR* bucket_dir = os::opendir(bucket);
      if (bucket_dir == nullptr) {
        continue;
      }
      // read each subdirectory, skipping over regular files
      struct dirent* subbucket_de;
      while ((subbucket_de = os::readdir(bucket_dir)) != nullptr) {
        if (subbucket_de->d_type != DT_DIR || subbucket_de->d_name[0] == '.') {
          continue;
        }
        // If the PERFDATA_NAME_user directory exists in the T subdirectory,
        // this means the subdirectory is the temporary directory of the user.
        char perfdata_path[PATH_MAX];
        int p = os::snprintf(perfdata_path, PATH_MAX, "%s%s/T/%s_%s/", bucket, subbucket_de->d_name, PERFDATA_NAME, user);

        // the total length must not exceed the output buffer size
        if (p >= PATH_MAX || p < 0) {
          continue;
        }
        // check if the subdirectory exists
        if (os::file_exists(perfdata_path)) {
          // the return value of snprintf is not checked for the second time
          return os::snprintf(output_path, output_size, "%s%s/T", bucket, subbucket_de->d_name);
        }
      }
      os::closedir(bucket_dir);
    }
    os::closedir(varfolders_dir);
  }
  return -1;
}
#endif

intx os::current_thread_id() {
#ifdef __APPLE__
  return (intx)os::Bsd::gettid();
#else
  return (intx)::pthread_self();
#endif
}

int os::current_process_id() {
  return (int)(getpid());
}

// DLL functions
static int local_dladdr(const void* addr, Dl_info* info) {
#ifdef __APPLE__
  if (addr == (void*)-1) {
    // dladdr() in macOS12/Monterey returns success for -1, but that addr
    // value should not be allowed to work to avoid confusion.
    return 0;
  }
#endif
  return dladdr(addr, info);
}

// This must be hard coded because it's the system's temporary
// directory not the java application's temp directory, ala java.io.tmpdir.
#ifdef __APPLE__
// macosx has a secure per-user temporary directory
char temp_path_storage[PATH_MAX];
const char* os::get_temp_directory() {
  static char *temp_path = nullptr;
  if (temp_path == nullptr) {
    int pathSize = confstr(_CS_DARWIN_USER_TEMP_DIR, temp_path_storage, PATH_MAX);
    if (pathSize == 0 || pathSize > PATH_MAX) {
      strlcpy(temp_path_storage, "/tmp/", sizeof(temp_path_storage));
    }
    temp_path = temp_path_storage;
  }
  return temp_path;
}
#else // __APPLE__
const char* os::get_temp_directory() { return "/tmp"; }
#endif // __APPLE__

// check if addr is inside libjvm.so
bool os::address_is_in_vm(address addr) {
  static address libjvm_base_addr;
  Dl_info dlinfo;

  if (libjvm_base_addr == nullptr) {
    if (dladdr(CAST_FROM_FN_PTR(void *, os::address_is_in_vm), &dlinfo) != 0) {
      libjvm_base_addr = (address)dlinfo.dli_fbase;
    }
    assert(libjvm_base_addr !=nullptr, "Cannot obtain base address for libjvm");
  }

  if (dladdr((void *)addr, &dlinfo) != 0) {
    if (libjvm_base_addr == (address)dlinfo.dli_fbase) return true;
  }

  return false;
}

void os::prepare_native_symbols() {
}

bool os::dll_address_to_function_name(address addr, char *buf,
                                      int buflen, int *offset,
                                      bool demangle) {
  // buf is not optional, but offset is optional
  assert(buf != nullptr, "sanity check");

  Dl_info dlinfo;

  if (local_dladdr((void*)addr, &dlinfo) != 0) {
    // see if we have a matching symbol
    if (dlinfo.dli_saddr != nullptr && dlinfo.dli_sname != nullptr) {
      if (!(demangle && Decoder::demangle(dlinfo.dli_sname, buf, buflen))) {
        jio_snprintf(buf, buflen, "%s", dlinfo.dli_sname);
      }
      if (offset != nullptr) *offset = addr - (address)dlinfo.dli_saddr;
      return true;
    }

#ifndef __APPLE__
    // The 6-parameter Decoder::decode() function is not implemented on macOS.
    // The Mach-O binary format does not contain a "list of files" with address
    // ranges like ELF. That makes sense since Mach-O can contain binaries for
    // than one instruction set so there can be more than one address range for
    // each "file".

    // no matching symbol so try for just file info
    if (dlinfo.dli_fname != nullptr && dlinfo.dli_fbase != nullptr) {
      if (Decoder::decode((address)(addr - (address)dlinfo.dli_fbase),
                          buf, buflen, offset, dlinfo.dli_fname, demangle)) {
        return true;
      }
    }

#else  // __APPLE__
    #define MACH_MAXSYMLEN 256

    char localbuf[MACH_MAXSYMLEN];
    // Handle non-dynamic manually:
    if (dlinfo.dli_fbase != nullptr &&
        Decoder::decode(addr, localbuf, MACH_MAXSYMLEN, offset,
                        dlinfo.dli_fbase)) {
      if (!(demangle && Decoder::demangle(localbuf, buf, buflen))) {
        jio_snprintf(buf, buflen, "%s", localbuf);
      }
      return true;
    }

    #undef MACH_MAXSYMLEN
#endif  // __APPLE__
  }
  buf[0] = '\0';
  if (offset != nullptr) *offset = -1;
  return false;
}

bool os::dll_address_to_library_name(address addr, char* buf,
                                     int buflen, int* offset) {
  // buf is not optional, but offset is optional
  assert(buf != nullptr, "sanity check");

  Dl_info dlinfo;

  if (local_dladdr((void*)addr, &dlinfo) != 0) {
    if (dlinfo.dli_fname != nullptr) {
      jio_snprintf(buf, buflen, "%s", dlinfo.dli_fname);
    }
    if (dlinfo.dli_fbase != nullptr && offset != nullptr) {
      *offset = addr - (address)dlinfo.dli_fbase;
    }
    return true;
  }

  buf[0] = '\0';
  if (offset) *offset = -1;
  return false;
}

// Loads .dll/.so and
// in case of error it checks if .dll/.so was built for the
// same architecture as Hotspot is running on

void *os::Bsd::dlopen_helper(const char *filename, int mode, char *ebuf, int ebuflen) {
  bool ieee_handling = IEEE_subnormal_handling_OK();
  if (!ieee_handling) {
    Events::log_dll_message(nullptr, "IEEE subnormal handling check failed before loading %s", filename);
    log_info(os)("IEEE subnormal handling check failed before loading %s", filename);
    if (CheckJNICalls) {
      tty->print_cr("WARNING: IEEE subnormal handling check failed before loading %s", filename);
      Thread* current = Thread::current();
      if (current->is_Java_thread()) {
        JavaThread::cast(current)->print_jni_stack();
      }
    }
  }

  // Save and restore the floating-point environment around dlopen().
  // There are known cases where global library initialization sets
  // FPU flags that affect computation accuracy, for example, enabling
  // Flush-To-Zero and Denormals-Are-Zero. Do not let those libraries
  // break Java arithmetic. Unfortunately, this might affect libraries
  // that might depend on these FPU features for performance and/or
  // numerical "accuracy", but we need to protect Java semantics first
  // and foremost. See JDK-8295159.

  fenv_t default_fenv;
  int rtn = fegetenv(&default_fenv);
  assert(rtn == 0, "fegetenv must succeed");

  Events::log_dll_message(nullptr, "Attempting to load shared library %s", filename);

  void* result;
  JFR_ONLY(NativeLibraryLoadEvent load_event(filename, &result);)
  result = ::dlopen(filename, RTLD_LAZY);
  if (result == nullptr) {
    const char* error_report = ::dlerror();
    if (error_report == nullptr) {
      error_report = "dlerror returned no error description";
    }
    if (ebuf != nullptr && ebuflen > 0) {
      ::strncpy(ebuf, error_report, ebuflen-1);
      ebuf[ebuflen-1]='\0';
    }
    Events::log_dll_message(nullptr, "Loading shared library %s failed, %s", filename, error_report);
    log_info(os)("shared library load of %s failed, %s", filename, error_report);
    JFR_ONLY(load_event.set_error_msg(error_report);)
  } else {
    Events::log_dll_message(nullptr, "Loaded shared library %s", filename);
    log_info(os)("shared library load of %s was successful", filename);
    if (! IEEE_subnormal_handling_OK()) {
      // We just dlopen()ed a library that mangled the floating-point
      // flags. Silently fix things now.
      JFR_ONLY(load_event.set_fp_env_correction_attempt(true);)
      int rtn = fesetenv(&default_fenv);
      assert(rtn == 0, "fesetenv must succeed");

      if (IEEE_subnormal_handling_OK()) {
        Events::log_dll_message(nullptr, "IEEE subnormal handling had to be corrected after loading %s", filename);
        log_info(os)("IEEE subnormal handling had to be corrected after loading %s", filename);
        JFR_ONLY(load_event.set_fp_env_correction_success(true);)
      } else {
        Events::log_dll_message(nullptr, "IEEE subnormal handling could not be corrected after loading %s", filename);
        log_info(os)("IEEE subnormal handling could not be corrected after loading %s", filename);
        if (CheckJNICalls) {
          tty->print_cr("WARNING: IEEE subnormal handling could not be corrected after loading %s", filename);
          Thread* current = Thread::current();
          if (current->is_Java_thread()) {
            JavaThread::cast(current)->print_jni_stack();
          }
        }
        assert(false, "fesetenv didn't work");
      }
    }
  }

  return result;
}

#ifdef __APPLE__
void * os::dll_load(const char *filename, char *ebuf, int ebuflen) {
  if (is_vm_statically_linked()) {
    return os::get_default_process_handle();
  }

  log_info(os)("attempting shared library load of %s", filename);

  return os::Bsd::dlopen_helper(filename, RTLD_LAZY, ebuf, ebuflen);
}
#else
void * os::dll_load(const char *filename, char *ebuf, int ebuflen) {
  if (is_vm_statically_linked()) {
    return os::get_default_process_handle();
  }

  log_info(os)("attempting shared library load of %s", filename);

  void* result;
  result = os::Bsd::dlopen_helper(filename, RTLD_LAZY, ebuf, ebuflen);
  if (result != nullptr) {
    return result;
  }
  if (ebuf == nullptr || ebuflen < 1) {
    // no error reporting requested
    return nullptr;
  }
  Events::log_dll_message(nullptr, "Loading shared library %s failed, %s", filename, error_report);
  log_info(os)("shared library load of %s failed, %s", filename, error_report);
  int diag_msg_max_length=ebuflen-strlen(ebuf);
  char* diag_msg_buf=ebuf+strlen(ebuf);

  if (diag_msg_max_length==0) {
    // No more space in ebuf for additional diagnostics message
    return nullptr;
  }

  int file_descriptor= ::open(filename, O_RDONLY | O_NONBLOCK);

  if (file_descriptor < 0) {
    // Can't open library, report dlerror() message
    return nullptr;
  }

  Elf32_Ehdr elf_head;
  bool failed_to_read_elf_head=
    (sizeof(elf_head)!=
     (::read(file_descriptor, &elf_head,sizeof(elf_head))));

  ::close(file_descriptor);
  if (failed_to_read_elf_head) {
    // file i/o error - report dlerror() msg
    return nullptr;
  }

  typedef struct {
    Elf32_Half  code;         // Actual value as defined in elf.h
    Elf32_Half  compat_class; // Compatibility of archs at VM's sense
    char        elf_class;    // 32 or 64 bit
    char        endianess;    // MSB or LSB
    char*       name;         // String representation
  } arch_t;

  #ifndef EM_486
    #define EM_486          6               /* Intel 80486 */
  #endif

  #ifndef EM_MIPS_RS3_LE
    #define EM_MIPS_RS3_LE  10              /* MIPS */
  #endif

  #ifndef EM_PPC64
    #define EM_PPC64        21              /* PowerPC64 */
  #endif

  #ifndef EM_S390
    #define EM_S390         22              /* IBM System/390 */
  #endif

  #ifndef EM_IA_64
    #define EM_IA_64        50              /* HP/Intel IA-64 */
  #endif

  #ifndef EM_X86_64
    #define EM_X86_64       62              /* AMD x86-64 */
  #endif

  static const arch_t arch_array[]={
    {EM_386,         EM_386,     ELFCLASS32, ELFDATA2LSB, (char*)"IA 32"},
    {EM_486,         EM_386,     ELFCLASS32, ELFDATA2LSB, (char*)"IA 32"},
    {EM_IA_64,       EM_IA_64,   ELFCLASS64, ELFDATA2LSB, (char*)"IA 64"},
    {EM_X86_64,      EM_X86_64,  ELFCLASS64, ELFDATA2LSB, (char*)"AMD 64"},
    {EM_PPC,         EM_PPC,     ELFCLASS32, ELFDATA2MSB, (char*)"Power PC 32"},
    {EM_PPC64,       EM_PPC64,   ELFCLASS64, ELFDATA2MSB, (char*)"Power PC 64"},
    {EM_ARM,         EM_ARM,     ELFCLASS32,   ELFDATA2LSB, (char*)"ARM"},
    {EM_S390,        EM_S390,    ELFCLASSNONE, ELFDATA2MSB, (char*)"IBM System/390"},
    {EM_ALPHA,       EM_ALPHA,   ELFCLASS64, ELFDATA2LSB, (char*)"Alpha"},
    {EM_MIPS_RS3_LE, EM_MIPS_RS3_LE, ELFCLASS32, ELFDATA2LSB, (char*)"MIPSel"},
    {EM_MIPS,        EM_MIPS,    ELFCLASS32, ELFDATA2MSB, (char*)"MIPS"},
    {EM_PARISC,      EM_PARISC,  ELFCLASS32, ELFDATA2MSB, (char*)"PARISC"},
    {EM_68K,         EM_68K,     ELFCLASS32, ELFDATA2MSB, (char*)"M68k"}
  };

  #if    (defined AMD64)
  static  Elf32_Half running_arch_code=EM_X86_64;
  #elif  (defined __powerpc64__)
  static  Elf32_Half running_arch_code=EM_PPC64;
  #elif  (defined __powerpc__)
  static  Elf32_Half running_arch_code=EM_PPC;
  #elif  (defined ARM)
  static  Elf32_Half running_arch_code=EM_ARM;
  #elif  (defined S390)
  static  Elf32_Half running_arch_code=EM_S390;
  #elif  (defined ALPHA)
  static  Elf32_Half running_arch_code=EM_ALPHA;
  #elif  (defined MIPSEL)
  static  Elf32_Half running_arch_code=EM_MIPS_RS3_LE;
  #elif  (defined PARISC)
  static  Elf32_Half running_arch_code=EM_PARISC;
  #elif  (defined MIPS)
  static  Elf32_Half running_arch_code=EM_MIPS;
  #elif  (defined M68K)
  static  Elf32_Half running_arch_code=EM_68K;
  #else
    #error Method os::dll_load requires that one of following is defined:\
         AMD64, __powerpc__, ARM, S390, ALPHA, MIPS, MIPSEL, PARISC, M68K
  #endif

  // Identify compatibility class for VM's architecture and library's architecture
  // Obtain string descriptions for architectures

  arch_t lib_arch={elf_head.e_machine,0,elf_head.e_ident[EI_CLASS], elf_head.e_ident[EI_DATA], nullptr};
  int running_arch_index=-1;

  for (unsigned int i=0; i < ARRAY_SIZE(arch_array); i++) {
    if (running_arch_code == arch_array[i].code) {
      running_arch_index    = i;
    }
    if (lib_arch.code == arch_array[i].code) {
      lib_arch.compat_class = arch_array[i].compat_class;
      lib_arch.name         = arch_array[i].name;
    }
  }

  assert(running_arch_index != -1,
         "Didn't find running architecture code (running_arch_code) in arch_array");
  if (running_arch_index == -1) {
    // Even though running architecture detection failed
    // we may still continue with reporting dlerror() message
    return nullptr;
  }

  if (lib_arch.endianess != arch_array[running_arch_index].endianess) {
    os::snprintf_checked(diag_msg_buf, diag_msg_max_length-1," (Possible cause: endianness mismatch)");
    return nullptr;
  }

#ifndef S390
  if (lib_arch.elf_class != arch_array[running_arch_index].elf_class) {
    os::snprintf_checked(diag_msg_buf, diag_msg_max_length-1," (Possible cause: architecture word width mismatch)");
    return nullptr;
  }
#endif // !S390

  if (lib_arch.compat_class != arch_array[running_arch_index].compat_class) {
    if (lib_arch.name!=nullptr) {
      os::snprintf_checked(diag_msg_buf, diag_msg_max_length-1,
                           " (Possible cause: can't load %s-bit .so on a %s-bit platform)",
                           lib_arch.name, arch_array[running_arch_index].name);
    } else {
      os::snprintf_checked(diag_msg_buf, diag_msg_max_length-1,
                           " (Possible cause: can't load this .so (machine code=0x%x) on a %s-bit platform)",
                           lib_arch.code,
                           arch_array[running_arch_index].name);
    }
  }

  return nullptr;
}
#endif // !__APPLE__

static int _print_dll_info_cb(const char * name, address base_address,
                              address top_address, void * param) {
  outputStream * out = (outputStream *) param;
  out->print_cr(INTPTR_FORMAT " \t%s", (intptr_t)base_address, name);
  return 0;
}

void os::print_dll_info(outputStream *st) {
  st->print_cr("Dynamic libraries:");
  if (get_loaded_modules_info(_print_dll_info_cb, (void *)st)) {
    st->print_cr("Error: Cannot print dynamic libraries.");
  }
}

int os::get_loaded_modules_info(os::LoadedModulesCallbackFunc callback, void *param) {
#ifdef RTLD_DI_LINKMAP
  Dl_info dli;
  void *handle;
  Link_map *map;
  Link_map *p;

  if (dladdr(CAST_FROM_FN_PTR(void *, os::print_dll_info), &dli) == 0 ||
      dli.dli_fname == nullptr) {
    return 1;
  }
  handle = dlopen(dli.dli_fname, RTLD_LAZY);
  if (handle == nullptr) {
    return 1;
  }
  dlinfo(handle, RTLD_DI_LINKMAP, &map);
  if (map == nullptr) {
    dlclose(handle);
    return 1;
  }

  while (map->l_prev != nullptr)
    map = map->l_prev;

  while (map != nullptr) {
    // Value for top_address is returned as 0 since we don't have any information about module size
    if (callback(map->l_name, (address)map->l_addr, (address)0, param)) {
      dlclose(handle);
      return 1;
    }
    map = map->l_next;
  }

  dlclose(handle);
#elif defined(__APPLE__)
  for (uint32_t i = 1; i < _dyld_image_count(); i++) {
    // Value for top_address is returned as 0 since we don't have any information about module size
    if (callback(_dyld_get_image_name(i), (address)_dyld_get_image_header(i), nullptr, param)) {
      return 1;
    }
  }
  return 0;
#else
  return 1;
#endif
}

void os::get_summary_os_info(char* buf, size_t buflen) {
  // These buffers are small because we want this to be brief
  // and not use a lot of stack while generating the hs_err file.
  char os[100];
  size_t size = sizeof(os);
  int mib_kern[] = { CTL_KERN, KERN_OSTYPE };
  if (sysctl(mib_kern, 2, os, &size, nullptr, 0) < 0) {
#ifdef __APPLE__
    strncpy(os, "Darwin", sizeof(os));
#elif __OpenBSD__
    strncpy(os, "OpenBSD", sizeof(os));
#else
    strncpy(os, "BSD", sizeof(os));
#endif
  }

  char release[100];
  size = sizeof(release);
  int mib_release[] = { CTL_KERN, KERN_OSRELEASE };
  if (sysctl(mib_release, 2, release, &size, nullptr, 0) < 0) {
    // if error, leave blank
    strncpy(release, "", sizeof(release));
  }

#ifdef __APPLE__
  char osproductversion[100];
  size_t sz = sizeof(osproductversion);
  int ret = sysctlbyname("kern.osproductversion", osproductversion, &sz, nullptr, 0);
  if (ret == 0) {
    char build[100];
    size = sizeof(build);
    int mib_build[] = { CTL_KERN, KERN_OSVERSION };
    if (sysctl(mib_build, 2, build, &size, nullptr, 0) < 0) {
      os::snprintf_checked(buf, buflen, "%s %s, macOS %s", os, release, osproductversion);
    } else {
      os::snprintf_checked(buf, buflen, "%s %s, macOS %s (%s)", os, release, osproductversion, build);
    }
  } else
#endif
  os::snprintf_checked(buf, buflen, "%s %s", os, release);
}

void os::print_os_info_brief(outputStream* st) {
  os::Posix::print_uname_info(st);
}

void os::print_os_info(outputStream* st) {
  st->print_cr("OS:");

  os::Posix::print_uname_info(st);

  os::Bsd::print_uptime_info(st);

  os::Posix::print_rlimit_info(st);

  os::Posix::print_load_average(st);

  VM_Version::print_platform_virtualization_info(st);
}

#ifdef __APPLE__
static void print_sysctl_info_string(const char* sysctlkey, outputStream* st, char* buf, size_t size) {
  if (sysctlbyname(sysctlkey, buf, &size, nullptr, 0) >= 0) {
    st->print_cr("%s:%s", sysctlkey, buf);
  }
}

static void print_sysctl_info_uint64(const char* sysctlkey, outputStream* st) {
  uint64_t val;
  size_t size=sizeof(uint64_t);
  if (sysctlbyname(sysctlkey, &val, &size, nullptr, 0) >= 0) {
    st->print_cr("%s:%llu", sysctlkey, val);
  }
}
#endif

void os::pd_print_cpu_info(outputStream* st, char* buf, size_t buflen) {
#ifdef __APPLE__
  print_sysctl_info_string("machdep.cpu.brand_string", st, buf, buflen);
  print_sysctl_info_uint64("hw.cpufrequency", st);
  print_sysctl_info_uint64("hw.cpufrequency_min", st);
  print_sysctl_info_uint64("hw.cpufrequency_max", st);
  print_sysctl_info_uint64("hw.cachelinesize", st);
  print_sysctl_info_uint64("hw.l1icachesize", st);
  print_sysctl_info_uint64("hw.l1dcachesize", st);
  print_sysctl_info_uint64("hw.l2cachesize", st);
  print_sysctl_info_uint64("hw.l3cachesize", st);
#endif
}

void os::get_summary_cpu_info(char* buf, size_t buflen) {
  unsigned int mhz;
  size_t size = sizeof(mhz);
  int mib[] = { CTL_HW, HW_CPU_FREQ };
  if (sysctl(mib, 2, &mhz, &size, nullptr, 0) < 0) {
    mhz = 1;  // looks like an error but can be divided by
  } else {
    mhz /= 1000000;  // reported in millions
  }

  char model[100];
  size = sizeof(model);
  int mib_model[] = { CTL_HW, HW_MODEL };
  if (sysctl(mib_model, 2, model, &size, nullptr, 0) < 0) {
    strncpy(model, cpu_arch, sizeof(model));
  }

  char machine[100];
  size = sizeof(machine);
  int mib_machine[] = { CTL_HW, HW_MACHINE };
  if (sysctl(mib_machine, 2, machine, &size, nullptr, 0) < 0) {
      strncpy(machine, "", sizeof(machine));
  }

#if defined(__APPLE__) && !defined(ZERO)
  if (VM_Version::is_cpu_emulated()) {
    os::snprintf_checked(buf, buflen, "\"%s\" %s (EMULATED) %d MHz", model, machine, mhz);
  } else {
    NOT_AARCH64(os::snprintf_checked(buf, buflen, "\"%s\" %s %d MHz", model, machine, mhz));
    // aarch64 CPU doesn't report its speed
    AARCH64_ONLY(os::snprintf_checked(buf, buflen, "\"%s\" %s", model, machine));
  }
#else
  os::snprintf_checked(buf, buflen, "\"%s\" %s %d MHz", model, machine, mhz);
#endif
}

void os::print_memory_info(outputStream* st) {
  xsw_usage swap_usage;
  size_t size = sizeof(swap_usage);

  st->print("Memory:");
  st->print(" %zuk page", os::vm_page_size()>>10);
  physical_memory_size_type phys_mem = os::physical_memory();
  st->print(", physical " PHYS_MEM_TYPE_FORMAT "k",
            phys_mem >> 10);
  physical_memory_size_type avail_mem = 0;
  (void)os::available_memory(avail_mem);
  st->print("(" PHYS_MEM_TYPE_FORMAT "k free)",
            avail_mem >> 10);

  if((sysctlbyname("vm.swapusage", &swap_usage, &size, nullptr, 0) == 0) || (errno == ENOMEM)) {
    if (size >= offset_of(xsw_usage, xsu_used)) {
      st->print(", swap " UINT64_FORMAT "k",
                ((julong) swap_usage.xsu_total) >> 10);
      st->print("(" UINT64_FORMAT "k free)",
                ((julong) swap_usage.xsu_avail) >> 10);
    }
  }

  st->cr();
}

////////////////////////////////////////////////////////////////////////////////
// Virtual Memory

static void warn_fail_commit_memory(char* addr, size_t size, bool exec,
                                    int err) {
  warning("INFO: os::commit_memory(" INTPTR_FORMAT ", %zu"
          ", %d) failed; error='%s' (errno=%d)", (intptr_t)addr, size, exec,
           os::errno_name(err), err);
}

// NOTE: Bsd kernel does not really reserve the pages for us.
//       All it does is to check if there are enough free pages
//       left at the time of mmap(). This could be a potential
//       problem.
bool os::pd_commit_memory(char* addr, size_t size, bool exec) {
  int prot = exec ? PROT_READ|PROT_WRITE|PROT_EXEC : PROT_READ|PROT_WRITE;
#if defined(__OpenBSD__)
  // XXX: Work-around mmap/MAP_FIXED bug temporarily on OpenBSD
  Events::log_memprotect(nullptr, "Protecting memory [" INTPTR_FORMAT "," INTPTR_FORMAT "] with protection modes %x", p2i(addr), p2i(addr+size), prot);
  if (::mprotect(addr, size, prot) == 0) {
    return true;
  } else {
    ErrnoPreserver ep;
    log_trace(os, map)("mprotect failed: " RANGEFMT " errno=(%s)",
                       RANGEFMTARGS(addr, size),
                       os::strerror(ep.saved_errno()));
  }
#elif defined(__APPLE__)
  if (exec) {
    // Do not replace MAP_JIT mappings, see JDK-8234930
    if (::mprotect(addr, size, prot) == 0) {
      return true;
    } else {
      ErrnoPreserver ep;
      log_trace(os, map)("mprotect failed: " RANGEFMT " errno=(%s)",
                         RANGEFMTARGS(addr, size),
                         os::strerror(ep.saved_errno()));
    }
  } else {
    uintptr_t res = (uintptr_t) ::mmap(addr, size, prot,
                                       MAP_PRIVATE|MAP_FIXED|MAP_ANONYMOUS, -1, 0);
    if (res != (uintptr_t) MAP_FAILED) {
      return true;
    } else {
      ErrnoPreserver ep;
      log_trace(os, map)("mmap failed: " RANGEFMT " errno=(%s)",
                         RANGEFMTARGS(addr, size),
                         os::strerror(ep.saved_errno()));
    }
  }
#else
  uintptr_t res = (uintptr_t) ::mmap(addr, size, prot,
                                     MAP_PRIVATE|MAP_FIXED|MAP_ANONYMOUS, -1, 0);
  if (res != (uintptr_t) MAP_FAILED) {
    return true;
  } else {
    ErrnoPreserver ep;
    log_trace(os, map)("mmap failed: " RANGEFMT " errno=(%s)",
                       RANGEFMTARGS(addr, size),
                       os::strerror(ep.saved_errno()));
  }
#endif

  // Warn about any commit errors we see in non-product builds just
  // in case mmap() doesn't work as described on the man page.
  NOT_PRODUCT(warn_fail_commit_memory(addr, size, exec, errno);)

  return false;
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
    // add extra info in product mode for vm_exit_out_of_memory():
    PRODUCT_ONLY(warn_fail_commit_memory(addr, size, exec, errno);)
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, "%s", mesg);
  }
}

void os::pd_commit_memory_or_exit(char* addr, size_t size,
                                  size_t alignment_hint, bool exec,
                                  const char* mesg) {
  // alignment_hint is ignored on this OS
  pd_commit_memory_or_exit(addr, size, exec, mesg);
}

void os::pd_realign_memory(char *addr, size_t bytes, size_t alignment_hint) {
}

void os::pd_disclaim_memory(char *addr, size_t bytes) {
  ::madvise(addr, bytes, MADV_DONTNEED);
}

size_t os::pd_pretouch_memory(void* first, void* last, size_t page_size) {
  return page_size;
}

void os::numa_set_thread_affinity(Thread *thread, int node) {
}

void os::numa_make_global(char *addr, size_t bytes) {
}

void os::numa_make_local(char *addr, size_t bytes, int lgrp_hint) {
}

size_t os::numa_get_groups_num() {
  return 1;
}

int os::numa_get_group_id() {
  return 0;
}

size_t os::numa_get_leaf_groups(uint *ids, size_t size) {
  if (size > 0) {
    ids[0] = 0;
    return 1;
  }
  return 0;
}

int os::numa_get_group_id_for_address(const void* address) {
  return 0;
}

bool os::numa_get_group_ids_for_range(const void** addresses, int* lgrp_ids, size_t count) {
  return false;
}

bool os::pd_uncommit_memory(char* addr, size_t size, bool exec) {
#if defined(__OpenBSD__)
  // XXX: Work-around mmap/MAP_FIXED bug temporarily on OpenBSD
  Events::log_memprotect(nullptr, "Protecting memory [" INTPTR_FORMAT "," INTPTR_FORMAT "] with PROT_NONE", p2i(addr), p2i(addr+size));
  if (::mprotect(addr, size, PROT_NONE) == 0) {
    return true;
  } else {
    ErrnoPreserver ep;
    log_trace(os, map)("mprotect failed: " RANGEFMT " errno=(%s)",
                       RANGEFMTARGS(addr, size),
                       os::strerror(ep.saved_errno()));
    return false;
  }
#elif defined(__APPLE__)
  if (exec) {
    if (::madvise(addr, size, MADV_FREE) != 0) {
      ErrnoPreserver ep;
      log_trace(os, map)("madvise failed: " RANGEFMT " errno=(%s)",
                         RANGEFMTARGS(addr, size),
                         os::strerror(ep.saved_errno()));
      return false;
    }
    if (::mprotect(addr, size, PROT_NONE) == 0) {
      return true;
    } else {
      ErrnoPreserver ep;
      log_trace(os, map)("mprotect failed: " RANGEFMT " errno=(%s)",
                         RANGEFMTARGS(addr, size),
                         os::strerror(ep.saved_errno()));
      return false;
    }
  } else {
    uintptr_t res = (uintptr_t) ::mmap(addr, size, PROT_NONE,
        MAP_PRIVATE|MAP_FIXED|MAP_NORESERVE|MAP_ANONYMOUS, -1, 0);
    if (res == (uintptr_t) MAP_FAILED) {
      ErrnoPreserver ep;
      log_trace(os, map)("mmap failed: " RANGEFMT " errno=(%s)",
                         RANGEFMTARGS(addr, size),
                         os::strerror(ep.saved_errno()));
      return false;
    }
    return true;
  }
#else
  uintptr_t res = (uintptr_t) ::mmap(addr, size, PROT_NONE,
                                     MAP_PRIVATE|MAP_FIXED|MAP_NORESERVE|MAP_ANONYMOUS, -1, 0);
  if (res == (uintptr_t) MAP_FAILED) {
    ErrnoPreserver ep;
    log_trace(os, map)("mmap failed: " RANGEFMT " errno=(%s)",
                       RANGEFMTARGS(addr, size),
                       os::strerror(ep.saved_errno()));
    return false;
  }
  return true;
#endif
}

bool os::pd_create_stack_guard_pages(char* addr, size_t size) {
  return os::commit_memory(addr, size, !ExecMem);
}

void os::remove_stack_guard_pages(char* addr, size_t size) {
  os::uncommit_memory(addr, size);
}

// 'requested_addr' is only treated as a hint, the return value may or
// may not start from the requested address. Unlike Bsd mmap(), this
// function returns null to indicate failure.
static char* anon_mmap(char* requested_addr, size_t bytes, bool exec) {
  // MAP_FIXED is intentionally left out, to leave existing mappings intact.
  const int flags = MAP_PRIVATE | MAP_NORESERVE | MAP_ANONYMOUS
      MACOS_ONLY(| (exec ? MAP_JIT : 0));

  // Map reserved/uncommitted pages PROT_NONE so we fail early if we
  // touch an uncommitted page. Otherwise, the read/write might
  // succeed if we have enough swap space to back the physical page.
  char* addr = (char*)::mmap(requested_addr, bytes, PROT_NONE, flags, -1, 0);
  if (addr == MAP_FAILED) {
    ErrnoPreserver ep;
    log_trace(os, map)("mmap failed: " RANGEFMT " errno=(%s)",
                       RANGEFMTARGS(requested_addr, bytes),
                       os::strerror(ep.saved_errno()));
    return nullptr;
  }
  return addr;
}

static int anon_munmap(char * addr, size_t size) {
  if (::munmap(addr, size) == 0) {
    return 1;
  } else {
    ErrnoPreserver ep;
    log_trace(os, map)("munmap failed: " RANGEFMT " errno=(%s)",
                       RANGEFMTARGS(addr, size),
                       os::strerror(ep.saved_errno()));
    return 0;
  }
}

char* os::pd_reserve_memory(size_t bytes, bool exec) {
  return anon_mmap(nullptr /* addr */, bytes, exec);
}

bool os::pd_release_memory(char* addr, size_t size) {
  return anon_munmap(addr, size);
}

static bool bsd_mprotect(char* addr, size_t size, int prot) {
  // Bsd wants the mprotect address argument to be page aligned.
  char* bottom = (char*)align_down((intptr_t)addr, os::vm_page_size());

  // According to SUSv3, mprotect() should only be used with mappings
  // established by mmap(), and mmap() always maps whole pages. Unaligned
  // 'addr' likely indicates problem in the VM (e.g. trying to change
  // protection of malloc'ed or statically allocated memory). Check the
  // caller if you hit this assert.
  assert(addr == bottom, "sanity check");

  size = align_up(pointer_delta(addr, bottom, 1) + size, os::vm_page_size());
  Events::log_memprotect(nullptr, "Protecting memory [" INTPTR_FORMAT "," INTPTR_FORMAT "] with protection modes %x", p2i(bottom), p2i(bottom+size), prot);
  return ::mprotect(bottom, size, prot) == 0;
}

// Set protections specified
bool os::protect_memory(char* addr, size_t bytes, ProtType prot,
                        bool is_committed) {
  unsigned int p = 0;
  switch (prot) {
  case MEM_PROT_NONE: p = PROT_NONE; break;
  case MEM_PROT_READ: p = PROT_READ; break;
  case MEM_PROT_RW:   p = PROT_READ|PROT_WRITE; break;
  case MEM_PROT_RWX:  p = PROT_READ|PROT_WRITE|PROT_EXEC; break;
  default:
    ShouldNotReachHere();
  }
  // is_committed is unused.
  return bsd_mprotect(addr, bytes, p);
}

bool os::guard_memory(char* addr, size_t size) {
  return bsd_mprotect(addr, size, PROT_NONE);
}

bool os::unguard_memory(char* addr, size_t size) {
  return bsd_mprotect(addr, size, PROT_READ|PROT_WRITE);
}

bool os::Bsd::hugetlbfs_sanity_check(bool warn, size_t page_size) {
  return false;
}

// Large page support

static size_t _large_page_size = 0;

void os::large_page_init() {
}


char* os::pd_reserve_memory_special(size_t bytes, size_t alignment, size_t page_size, char* req_addr, bool exec) {
  fatal("os::reserve_memory_special should not be called on BSD.");
  return nullptr;
}

bool os::pd_release_memory_special(char* base, size_t bytes) {
  fatal("os::release_memory_special should not be called on BSD.");
  return false;
}

size_t os::large_page_size() {
  return _large_page_size;
}

bool os::can_commit_large_page_memory() {
  // Does not matter, we do not support huge pages.
  return false;
}

char* os::pd_attempt_map_memory_to_file_at(char* requested_addr, size_t bytes, int file_desc) {
  assert(file_desc >= 0, "file_desc is not valid");
  char* result = pd_attempt_reserve_memory_at(requested_addr, bytes, !ExecMem);
  if (result != nullptr) {
    if (replace_existing_mapping_with_file_mapping(result, bytes, file_desc) == nullptr) {
      vm_exit_during_initialization(err_msg("Error in mapping Java heap at the given filesystem directory"));
    }
  }
  return result;
}

// Reserve memory at an arbitrary address, only if that area is
// available (and not reserved for something else).

char* os::pd_attempt_reserve_memory_at(char* requested_addr, size_t bytes, bool exec) {
  // Assert only that the size is a multiple of the page size, since
  // that's all that mmap requires, and since that's all we really know
  // about at this low abstraction level.  If we need higher alignment,
  // we can either pass an alignment to this method or verify alignment
  // in one of the methods further up the call chain.  See bug 5044738.
  assert(bytes % os::vm_page_size() == 0, "reserving unexpected size block");

  // Bsd mmap allows caller to pass an address as hint; give it a try first,
  // if kernel honors the hint then we can return immediately.
  char * addr = anon_mmap(requested_addr, bytes, exec);
  if (addr == requested_addr) {
    return requested_addr;
  }

  if (addr != nullptr) {
    // mmap() is successful but it fails to reserve at the requested address
    anon_munmap(addr, bytes);
  }

  return nullptr;
}

size_t os::vm_min_address() {
#ifdef __APPLE__
  // On MacOS, the lowest 4G are denied to the application (see "PAGEZERO" resp.
  // -pagezero_size linker option).
  return 4 * G;
#else
  assert(is_aligned(_vm_min_address_default, os::vm_allocation_granularity()), "Sanity");
  return _vm_min_address_default;
#endif
}

////////////////////////////////////////////////////////////////////////////////
// thread priority support

// Note: Normal Bsd applications are run with SCHED_OTHER policy. SCHED_OTHER
// only supports dynamic priority, static priority must be zero. For real-time
// applications, Bsd supports SCHED_RR which allows static priority (1-99).
// However, for large multi-threaded applications, SCHED_RR is not only slower
// than SCHED_OTHER, but also very unstable (my volano tests hang hard 4 out
// of 5 runs - Sep 2005).
//
// The following code actually changes the niceness of kernel-thread/LWP. It
// has an assumption that setpriority() only modifies one kernel-thread/LWP,
// not the entire user process, and user level threads are 1:1 mapped to kernel
// threads. It has always been the case, but could change in the future. For
// this reason, the code should not be used as default (ThreadPriorityPolicy=0).
// It is only used when ThreadPriorityPolicy=1 and may require system level permission
// (e.g., root privilege or CAP_SYS_NICE capability).

#if !defined(__APPLE__)
int os::java_to_os_priority[CriticalPriority + 1] = {
  19,              // 0 Entry should never be used

   0,              // 1 MinPriority
   3,              // 2
   6,              // 3

  10,              // 4
  15,              // 5 NormPriority
  18,              // 6

  21,              // 7
  25,              // 8
  28,              // 9 NearMaxPriority

  31,              // 10 MaxPriority

  31               // 11 CriticalPriority
};
#else
// Using Mach high-level priority assignments
int os::java_to_os_priority[CriticalPriority + 1] = {
   0,              // 0 Entry should never be used (MINPRI_USER)

  27,              // 1 MinPriority
  28,              // 2
  29,              // 3

  30,              // 4
  31,              // 5 NormPriority (BASEPRI_DEFAULT)
  32,              // 6

  33,              // 7
  34,              // 8
  35,              // 9 NearMaxPriority

  36,              // 10 MaxPriority

  36               // 11 CriticalPriority
};
#endif

static int prio_init() {
  if (ThreadPriorityPolicy == 1) {
    if (geteuid() != 0) {
      if (!FLAG_IS_DEFAULT(ThreadPriorityPolicy) && !FLAG_IS_JIMAGE_RESOURCE(ThreadPriorityPolicy)) {
        warning("-XX:ThreadPriorityPolicy=1 may require system level permission, " \
                "e.g., being the root user. If the necessary permission is not " \
                "possessed, changes to priority will be silently ignored.");
      }
    }
  }
  if (UseCriticalJavaThreadPriority) {
    os::java_to_os_priority[MaxPriority] = os::java_to_os_priority[CriticalPriority];
  }
  return 0;
}

OSReturn os::set_native_priority(Thread* thread, int newpri) {
  if (!UseThreadPriorities || ThreadPriorityPolicy == 0) return OS_OK;

#ifdef __OpenBSD__
  // OpenBSD pthread_setprio starves low priority threads
  return OS_OK;
#elif defined(__FreeBSD__)
  int ret = pthread_setprio(thread->osthread()->pthread_id(), newpri);
  return (ret == 0) ? OS_OK : OS_ERR;
#elif defined(__APPLE__) || defined(__NetBSD__)
  struct sched_param sp;
  int policy;

  if (pthread_getschedparam(thread->osthread()->pthread_id(), &policy, &sp) != 0) {
    return OS_ERR;
  }

  sp.sched_priority = newpri;
  if (pthread_setschedparam(thread->osthread()->pthread_id(), policy, &sp) != 0) {
    return OS_ERR;
  }

  return OS_OK;
#else
  int ret = setpriority(PRIO_PROCESS, thread->osthread()->thread_id(), newpri);
  return (ret == 0) ? OS_OK : OS_ERR;
#endif
}

OSReturn os::get_native_priority(const Thread* const thread, int *priority_ptr) {
  if (!UseThreadPriorities || ThreadPriorityPolicy == 0) {
    *priority_ptr = java_to_os_priority[NormPriority];
    return OS_OK;
  }

  errno = 0;
#if defined(__OpenBSD__) || defined(__FreeBSD__)
  *priority_ptr = pthread_getprio(thread->osthread()->pthread_id());
#elif defined(__APPLE__) || defined(__NetBSD__)
  int policy;
  struct sched_param sp;

  int res = pthread_getschedparam(thread->osthread()->pthread_id(), &policy, &sp);
  if (res != 0) {
    *priority_ptr = -1;
    return OS_ERR;
  } else {
    *priority_ptr = sp.sched_priority;
    return OS_OK;
  }
#else
  *priority_ptr = getpriority(PRIO_PROCESS, thread->osthread()->thread_id());
#endif
  return (*priority_ptr != -1 || errno == 0 ? OS_OK : OS_ERR);
}

extern void report_error(char* file_name, int line_no, char* title,
                         char* format, ...);

// this is called _before_ the most of global arguments have been parsed
void os::init(void) {
  char dummy;   // used to get a guess on initial stack address

  size_t page_size = (size_t)getpagesize();
  OSInfo::set_vm_page_size(page_size);
  OSInfo::set_vm_allocation_granularity(page_size);
  if (os::vm_page_size() == 0) {
    fatal("os_bsd.cpp: os::init: getpagesize() failed (%s)", os::strerror(errno));
  }
  _page_sizes.add(os::vm_page_size());

  Bsd::initialize_system_info();

  // _main_thread points to the thread that created/loaded the JVM.
  Bsd::_main_thread = pthread_self();

  Bsd::clock_init();

  os::Posix::init();
}

// To install functions for atexit system call
extern "C" {
  static void perfMemory_exit_helper() {
    perfMemory_exit();
  }
}

// this is called _after_ the global arguments have been parsed
jint os::init_2(void) {

  // This could be set after os::Posix::init() but all platforms
  // have to set it the same so we have to mirror Solaris.
  DEBUG_ONLY(os::set_mutex_init_done();)

  os::Posix::init_2();

  if (PosixSignals::init() == JNI_ERR) {
    return JNI_ERR;
  }

  // Check and sets minimum stack sizes against command line options
  if (set_minimum_stack_sizes() == JNI_ERR) {
    return JNI_ERR;
  }

  // Not supported.
  FLAG_SET_ERGO(UseNUMA, false);
  FLAG_SET_ERGO(UseNUMAInterleaving, false);

  if (MaxFDLimit) {
    // set the number of file descriptors to max. print out error
    // if getrlimit/setrlimit fails but continue regardless.
    struct rlimit nbr_files;
    int status = getrlimit(RLIMIT_NOFILE, &nbr_files);
    if (status != 0) {
      log_info(os)("os::init_2 getrlimit failed: %s", os::strerror(errno));
    } else {
      rlim_t rlim_original = nbr_files.rlim_cur;

      // On macOS according to setrlimit(2), OPEN_MAX must be used instead
      // of RLIM_INFINITY, but testing on macOS >= 10.6, reveals that
      // we can, in fact, use even RLIM_INFINITY.
      // However, we need to limit the value to 0x100000 (which is the max value
      // allowed on Linux) so that any existing code that iterates over all allowed
      // file descriptors, finishes in a reasonable time, without appearing
      // to hang.
      nbr_files.rlim_cur = MIN(0x100000, nbr_files.rlim_max);

      status = setrlimit(RLIMIT_NOFILE, &nbr_files);
      if (status != 0) {
        // If that fails then try lowering the limit to either OPEN_MAX
        // (which is safe) or the original limit, whichever was greater.
        nbr_files.rlim_cur = MAX(OPEN_MAX, rlim_original);
        status = setrlimit(RLIMIT_NOFILE, &nbr_files);
      }
      if (status != 0) {
        log_info(os)("os::init_2 setrlimit failed: %s", os::strerror(errno));
      }
    }
  }

  // at-exit methods are called in the reverse order of their registration.
  // atexit functions are called on return from main or as a result of a
  // call to exit(3C). There can be only 32 of these functions registered
  // and atexit() does not set errno.

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

  // initialize thread priority policy
  prio_init();

#ifdef __APPLE__
  // dynamically link to objective c gc registration
  void *handleLibObjc = dlopen(OBJC_LIB, RTLD_LAZY);
  if (handleLibObjc != nullptr) {
    objc_registerThreadWithCollectorFunction = (objc_registerThreadWithCollector_t) dlsym(handleLibObjc, OBJC_GCREGISTER);
  }
#endif

  return JNI_OK;
}

int os::active_processor_count() {
  // User has overridden the number of active processors
  if (ActiveProcessorCount > 0) {
    log_trace(os)("active_processor_count: "
                  "active processor count set by user : %d",
                  ActiveProcessorCount);
    return ActiveProcessorCount;
  }

  return Machine::active_processor_count();
}

int os::Machine::active_processor_count() {
  return _processor_count;
}

uint os::processor_id() {
#if defined(__APPLE__) && defined(__x86_64__)
  // Get the initial APIC id and return the associated processor id. The initial APIC
  // id is limited to 8-bits, which means we can have at most 256 unique APIC ids. If
  // the system has more processors (or the initial APIC ids are discontiguous) the
  // APIC id will be truncated and more than one processor will potentially share the
  // same processor id. This is not optimal, but unlikely to happen in practice. Should
  // this become a real problem we could switch to using x2APIC ids, which are 32-bit
  // wide. However, note that x2APIC is Intel-specific, and the wider number space
  // would require a more complicated mapping approach.
  uint eax = 0x1;
  uint ebx;
  uint ecx = 0;
  uint edx;

  __asm__ ("cpuid\n\t" : "+a" (eax), "+b" (ebx), "+c" (ecx), "+d" (edx) : );

  uint apic_id = (ebx >> 24) & (processor_id_map_size - 1);
  int processor_id = AtomicAccess::load(&processor_id_map[apic_id]);

  while (processor_id < 0) {
    // Assign processor id to APIC id
    processor_id = AtomicAccess::cmpxchg(&processor_id_map[apic_id], processor_id_unassigned, processor_id_assigning);
    if (processor_id == processor_id_unassigned) {
      processor_id = AtomicAccess::fetch_then_add(&processor_id_next, 1) % os::processor_count();
      AtomicAccess::store(&processor_id_map[apic_id], processor_id);
    }
  }

  assert(processor_id >= 0 && processor_id < os::processor_count(), "invalid processor id");

  return (uint)processor_id;
#else // defined(__APPLE__) && defined(__x86_64__)
  // Return 0 until we find a good way to get the current processor id on
  // the platform. Returning 0 is safe, since there is always at least one
  // processor, but might not be optimal for performance in some cases.
  return 0;
#endif
}

void os::set_native_thread_name(const char *name) {
#if defined(__APPLE__) && MAC_OS_X_VERSION_MIN_REQUIRED > MAC_OS_X_VERSION_10_5
  // This is only supported in Snow Leopard and beyond
  if (name != nullptr) {
    // Add a "Java: " prefix to the name
    char buf[MAXTHREADNAMESIZE];
    (void) os::snprintf(buf, sizeof(buf), "Java: %s", name);
    pthread_setname_np(buf);
  }
#endif
}

////////////////////////////////////////////////////////////////////////////////
// debug support

bool os::find(address addr, outputStream* st) {
  Dl_info dlinfo;
  memset(&dlinfo, 0, sizeof(dlinfo));
  if (dladdr(addr, &dlinfo) != 0) {
    st->print(INTPTR_FORMAT ": ", (intptr_t)addr);
    if (dlinfo.dli_sname != nullptr && dlinfo.dli_saddr != nullptr) {
      st->print("%s+%#x", dlinfo.dli_sname,
                (uint)((uintptr_t)addr - (uintptr_t)dlinfo.dli_saddr));
    } else if (dlinfo.dli_fbase != nullptr) {
      st->print("<offset %#x>", (uint)((uintptr_t)addr - (uintptr_t)dlinfo.dli_fbase));
    } else {
      st->print("<absolute address>");
    }
    if (dlinfo.dli_fname != nullptr) {
      st->print(" in %s", dlinfo.dli_fname);
    }
    if (dlinfo.dli_fbase != nullptr) {
      st->print(" at " INTPTR_FORMAT, (intptr_t)dlinfo.dli_fbase);
    }
    st->cr();

    if (Verbose) {
      // decode some bytes around the PC
      address begin = clamp_address_in_page(addr-40, addr, os::vm_page_size());
      address end   = clamp_address_in_page(addr+40, addr, os::vm_page_size());
      address       lowest = (address) dlinfo.dli_sname;
      if (!lowest)  lowest = (address) dlinfo.dli_fbase;
      if (begin < lowest)  begin = lowest;
      Dl_info dlinfo2;
      if (dladdr(end, &dlinfo2) != 0 && dlinfo2.dli_saddr != dlinfo.dli_saddr
          && end > dlinfo2.dli_saddr && dlinfo2.dli_saddr > begin) {
        end = (address) dlinfo2.dli_saddr;
      }
      Disassembler::decode(begin, end, st);
    }
    return true;
  }
  return false;
}

////////////////////////////////////////////////////////////////////////////////
// misc

// This does not do anything on Bsd. This is basically a hook for being
// able to use structured exception handling (thread-local exception filters)
// on, e.g., Win32.
void os::os_exception_wrapper(java_call_t f, JavaValue* value,
                              const methodHandle& method, JavaCallArguments* args,
                              JavaThread* thread) {
  f(value, method, args, thread);
}

static inline struct timespec get_mtime(const char* filename) {
  struct stat st;
  int ret = os::stat(filename, &st);
  assert(ret == 0, "failed to stat() file '%s': %s", filename, os::strerror(errno));
#ifdef __APPLE__
  return st.st_mtimespec;
#else
  return st.st_mtim;
#endif
}

int os::compare_file_modified_times(const char* file1, const char* file2) {
  struct timespec filetime1 = get_mtime(file1);
  struct timespec filetime2 = get_mtime(file2);
  int diff = primitive_compare(filetime1.tv_sec, filetime2.tv_sec);
  if (diff == 0) {
    diff = primitive_compare(filetime1.tv_nsec, filetime2.tv_nsec);
  }
  return diff;
}

// This code originates from JDK's sysOpen and open64_w
// from src/solaris/hpi/src/system_md.c

int os::open(const char *path, int oflag, int mode) {
  if (strlen(path) > MAX_PATH - 1) {
    errno = ENAMETOOLONG;
    return -1;
  }

  // All file descriptors that are opened in the JVM and not
  // specifically destined for a subprocess should have the
  // close-on-exec flag set.  If we don't set it, then careless 3rd
  // party native code might fork and exec without closing all
  // appropriate file descriptors, and this in turn might:
  //
  // - cause end-of-file to fail to be detected on some file
  //   descriptors, resulting in mysterious hangs, or
  //
  // - might cause an fopen in the subprocess to fail on a system
  //   suffering from bug 1085341.
  //
  // (Yes, the default setting of the close-on-exec flag is a Unix
  // design flaw)
  //
  // See:
  // 1085341: 32-bit stdio routines should support file descriptors >255
  // 4843136: (process) pipe file descriptor from Runtime.exec not being closed
  // 6339493: (process) Runtime.exec does not close all file descriptors on Solaris 9
  //

  int fd = ::open(path, oflag | O_CLOEXEC, mode);
  if (fd == -1) return -1;

  // If the open succeeded, the file might still be a directory
  {
    struct stat buf;
    int ret = ::fstat(fd, &buf);
    int st_mode = buf.st_mode;

    if (ret != -1) {
      if ((st_mode & S_IFMT) == S_IFDIR) {
        ::close(fd);
        errno = EISDIR;
        return -1;
      }
    } else {
      ::close(fd);
      return -1;
    }
  }

  return fd;
}

// current_thread_cpu_time(bool) and thread_cpu_time(Thread*, bool)
// are used by JVM M&M and JVMTI to get user+sys or user CPU time
// of a thread.
//
// current_thread_cpu_time() and thread_cpu_time(Thread*) returns
// the fast estimate available on the platform.

jlong os::current_thread_cpu_time() {
#ifdef __APPLE__
  return os::thread_cpu_time(Thread::current(), true /* user + sys */);
#else
  Unimplemented();
  return 0;
#endif
}

jlong os::thread_cpu_time(Thread* thread) {
#ifdef __APPLE__
  return os::thread_cpu_time(thread, true /* user + sys */);
#else
  Unimplemented();
  return 0;
#endif
}

jlong os::current_thread_cpu_time(bool user_sys_cpu_time) {
#ifdef __APPLE__
  return os::thread_cpu_time(Thread::current(), user_sys_cpu_time);
#else
  Unimplemented();
  return 0;
#endif
}

jlong os::thread_cpu_time(Thread *thread, bool user_sys_cpu_time) {
#ifdef __APPLE__
  struct thread_basic_info tinfo;
  mach_msg_type_number_t tcount = THREAD_INFO_MAX;
  kern_return_t kr;
  thread_t mach_thread;

  mach_thread = thread->osthread()->thread_id();
  kr = thread_info(mach_thread, THREAD_BASIC_INFO, (thread_info_t)&tinfo, &tcount);
  if (kr != KERN_SUCCESS) {
    return -1;
  }

  if (user_sys_cpu_time) {
    jlong nanos;
    nanos = ((jlong) tinfo.system_time.seconds + tinfo.user_time.seconds) * (jlong)1000000000;
    nanos += ((jlong) tinfo.system_time.microseconds + (jlong) tinfo.user_time.microseconds) * (jlong)1000;
    return nanos;
  } else {
    return ((jlong)tinfo.user_time.seconds * 1000000000) + ((jlong)tinfo.user_time.microseconds * (jlong)1000);
  }
#else
  Unimplemented();
  return 0;
#endif
}


void os::current_thread_cpu_time_info(jvmtiTimerInfo *info_ptr) {
  info_ptr->max_value = all_bits_jlong;    // will not wrap in less than 64 bits
  info_ptr->may_skip_backward = false;     // elapsed time not wall time
  info_ptr->may_skip_forward = false;      // elapsed time not wall time
  info_ptr->kind = JVMTI_TIMER_TOTAL_CPU;  // user+system time is returned
}

void os::thread_cpu_time_info(jvmtiTimerInfo *info_ptr) {
  info_ptr->max_value = all_bits_jlong;    // will not wrap in less than 64 bits
  info_ptr->may_skip_backward = false;     // elapsed time not wall time
  info_ptr->may_skip_forward = false;      // elapsed time not wall time
  info_ptr->kind = JVMTI_TIMER_TOTAL_CPU;  // user+system time is returned
}

bool os::is_thread_cpu_time_supported() {
#ifdef __APPLE__
  return true;
#else
  return false;
#endif
}

// System loadavg support.  Returns -1 if load average cannot be obtained.
// Bsd doesn't yet have a (official) notion of processor sets,
// so just return the system wide load average.
int os::loadavg(double loadavg[], int nelem) {
  return ::getloadavg(loadavg, nelem);
}

// Get the kern.corefile setting, or otherwise the default path to the core file
// Returns the length of the string
int os::get_core_path(char* buffer, size_t bufferSize) {
  int n = 0;
#ifdef __APPLE__
  char coreinfo[MAX_PATH];
  size_t sz = sizeof(coreinfo);
  int ret = sysctlbyname("kern.corefile", coreinfo, &sz, nullptr, 0);
  if (ret == 0) {
    char *pid_pos = strstr(coreinfo, "%P");
    // skip over the "%P" to preserve any optional custom user pattern
    const char* tail = (pid_pos != nullptr) ? (pid_pos + 2) : "";

    if (pid_pos != nullptr) {
      *pid_pos = '\0';
      n = jio_snprintf(buffer, bufferSize, "%s%d%s", coreinfo, os::current_process_id(), tail);
    } else {
      n = jio_snprintf(buffer, bufferSize, "%s", coreinfo);
    }
  } else
#endif
  {
    n = jio_snprintf(buffer, bufferSize, "/cores/core.%d", os::current_process_id());
  }
  // Truncate if theoretical string was longer than bufferSize
  n = MIN2(n, (int)bufferSize);

  return n;
}

bool os::supports_map_sync() {
  return false;
}

bool os::start_debugging(char *buf, int buflen) {
  int len = (int)strlen(buf);
  char *p = &buf[len];

  jio_snprintf(p, buflen-len,
             "\n\n"
             "Do you want to debug the problem?\n\n"
             "To debug, run 'gdb /proc/%d/exe %d'; then switch to thread %zd (" INTPTR_FORMAT ")\n"
             "Enter 'yes' to launch gdb automatically (PATH must include gdb)\n"
             "Otherwise, press RETURN to abort...",
             os::current_process_id(), os::current_process_id(),
             os::current_thread_id(), os::current_thread_id());

  bool yes = os::message_box("Unexpected Error", buf);

  if (yes) {
    // yes, user asked VM to launch debugger
    jio_snprintf(buf, sizeof(buf), "gdb /proc/%d/exe %d",
                     os::current_process_id(), os::current_process_id());

    os::fork_and_exec(buf);
    yes = false;
  }
  return yes;
}

void os::print_memory_mappings(char* addr, size_t bytes, outputStream* st) {}

#if INCLUDE_JFR

void os::jfr_report_memory_info() {
#ifdef __APPLE__
  mach_task_basic_info info;
  mach_msg_type_number_t count = MACH_TASK_BASIC_INFO_COUNT;

  kern_return_t ret = task_info(mach_task_self(), MACH_TASK_BASIC_INFO, (task_info_t)&info, &count);
  if (ret == KERN_SUCCESS) {
    // Send the RSS JFR event
    EventResidentSetSize event;
    event.set_size(info.resident_size);
    // We've seen that resident_size_max sometimes trails resident_size with one page.
    // Make sure we always report size <= peak
    event.set_peak(MAX2(info.resident_size_max, info.resident_size));
    event.commit();
  } else {
    // Log a warning
    static bool first_warning = true;
    if (first_warning) {
      log_warning(jfr)("Error fetching RSS values: task_info failed");
      first_warning = false;
    }
  }

#endif // __APPLE__
}

#endif // INCLUDE_JFR

bool os::pd_dll_unload(void* libhandle, char* ebuf, int ebuflen) {

  if (ebuf && ebuflen > 0) {
    ebuf[0] = '\0';
    ebuf[ebuflen - 1] = '\0';
  }

  bool res = (0 == ::dlclose(libhandle));
  if (!res) {
    // error analysis when dlopen fails
    const char* error_report = ::dlerror();
    if (error_report == nullptr) {
      error_report = "dlerror returned no error description";
    }
    if (ebuf != nullptr && ebuflen > 0) {
      os::snprintf_checked(ebuf, ebuflen, "%s", error_report);
    }
  }

  return res;
} // end: os::pd_dll_unload()

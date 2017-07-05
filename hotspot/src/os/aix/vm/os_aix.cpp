/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2012, 2013 SAP AG. All rights reserved.
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

// According to the AIX OS doc #pragma alloca must be used
// with C++ compiler before referencing the function alloca()
#pragma alloca

// no precompiled headers
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "compiler/compileBroker.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm_aix.h"
#include "libperfstat_aix.hpp"
#include "loadlib_aix.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/filemap.hpp"
#include "mutex_aix.inline.hpp"
#include "oops/oop.inline.hpp"
#include "os_share_aix.hpp"
#include "porting_aix.hpp"
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
#include "runtime/threadCritical.hpp"
#include "runtime/timer.hpp"
#include "services/attachListener.hpp"
#include "services/runtimeService.hpp"
#include "thread_aix.inline.hpp"
#include "utilities/decoder.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/events.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/vmError.hpp"
#ifdef TARGET_ARCH_ppc
# include "assembler_ppc.inline.hpp"
# include "nativeInst_ppc.hpp"
#endif
#ifdef COMPILER1
#include "c1/c1_Runtime1.hpp"
#endif
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

// put OS-includes here (sorted alphabetically)
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <poll.h>
#include <procinfo.h>
#include <pthread.h>
#include <pwd.h>
#include <semaphore.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/ipc.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/select.h>
#include <sys/shm.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/sysinfo.h>
#include <sys/systemcfg.h>
#include <sys/time.h>
#include <sys/times.h>
#include <sys/types.h>
#include <sys/utsname.h>
#include <sys/vminfo.h>
#include <sys/wait.h>

// Add missing declarations (should be in procinfo.h but isn't until AIX 6.1).
#if !defined(_AIXVERSION_610)
extern "C" {
  int getthrds64(pid_t ProcessIdentifier,
                 struct thrdentry64* ThreadBuffer,
                 int ThreadSize,
                 tid64_t* IndexPointer,
                 int Count);
}
#endif

// Excerpts from systemcfg.h definitions newer than AIX 5.3
#ifndef PV_7
# define PV_7 0x200000          // Power PC 7
# define PV_7_Compat 0x208000   // Power PC 7
#endif

#define MAX_PATH (2 * K)

// for timer info max values which include all bits
#define ALL_64_BITS CONST64(0xFFFFFFFFFFFFFFFF)
// for multipage initialization error analysis (in 'g_multipage_error')
#define ERROR_MP_OS_TOO_OLD                          100
#define ERROR_MP_EXTSHM_ACTIVE                       101
#define ERROR_MP_VMGETINFO_FAILED                    102
#define ERROR_MP_VMGETINFO_CLAIMS_NO_SUPPORT_FOR_64K 103

// the semantics in this file are thus that codeptr_t is a *real code ptr*
// This means that any function taking codeptr_t as arguments will assume
// a real codeptr and won't handle function descriptors (eg getFuncName),
// whereas functions taking address as args will deal with function
// descriptors (eg os::dll_address_to_library_name)
typedef unsigned int* codeptr_t;

// typedefs for stackslots, stack pointers, pointers to op codes
typedef unsigned long stackslot_t;
typedef stackslot_t* stackptr_t;

// query dimensions of the stack of the calling thread
static void query_stack_dimensions(address* p_stack_base, size_t* p_stack_size);

// function to check a given stack pointer against given stack limits
inline bool is_valid_stackpointer(stackptr_t sp, stackptr_t stack_base, size_t stack_size) {
  if (((uintptr_t)sp) & 0x7) {
    return false;
  }
  if (sp > stack_base) {
    return false;
  }
  if (sp < (stackptr_t) ((address)stack_base - stack_size)) {
    return false;
  }
  return true;
}

// returns true if function is a valid codepointer
inline bool is_valid_codepointer(codeptr_t p) {
  if (!p) {
    return false;
  }
  if (((uintptr_t)p) & 0x3) {
    return false;
  }
  if (LoadedLibraries::find_for_text_address((address)p) == NULL) {
    return false;
  }
  return true;
}

// macro to check a given stack pointer against given stack limits and to die if test fails
#define CHECK_STACK_PTR(sp, stack_base, stack_size) { \
    guarantee(is_valid_stackpointer((stackptr_t)(sp), (stackptr_t)(stack_base), stack_size), "Stack Pointer Invalid"); \
}

// macro to check the current stack pointer against given stacklimits
#define CHECK_CURRENT_STACK_PTR(stack_base, stack_size) { \
  address sp; \
  sp = os::current_stack_pointer(); \
  CHECK_STACK_PTR(sp, stack_base, stack_size); \
}

////////////////////////////////////////////////////////////////////////////////
// global variables (for a description see os_aix.hpp)

julong    os::Aix::_physical_memory = 0;
pthread_t os::Aix::_main_thread = ((pthread_t)0);
int       os::Aix::_page_size = -1;
int       os::Aix::_on_pase = -1;
int       os::Aix::_os_version = -1;
int       os::Aix::_stack_page_size = -1;
size_t    os::Aix::_shm_default_page_size = -1;
int       os::Aix::_can_use_64K_pages = -1;
int       os::Aix::_can_use_16M_pages = -1;
int       os::Aix::_xpg_sus_mode = -1;
int       os::Aix::_extshm = -1;
int       os::Aix::_logical_cpus = -1;

////////////////////////////////////////////////////////////////////////////////
// local variables

static int      g_multipage_error  = -1;   // error analysis for multipage initialization
static jlong    initial_time_count = 0;
static int      clock_tics_per_sec = 100;
static sigset_t check_signal_done;         // For diagnostics to print a message once (see run_periodic_checks)
static bool     check_signals      = true;
static pid_t    _initial_pid       = 0;
static int      SR_signum          = SIGUSR2; // Signal used to suspend/resume a thread (must be > SIGSEGV, see 4355769)
static sigset_t SR_sigset;
static pthread_mutex_t dl_mutex;           // Used to protect dlsym() calls */

julong os::available_memory() {
  return Aix::available_memory();
}

julong os::Aix::available_memory() {
  os::Aix::meminfo_t mi;
  if (os::Aix::get_meminfo(&mi)) {
    return mi.real_free;
  } else {
    return 0xFFFFFFFFFFFFFFFFLL;
  }
}

julong os::physical_memory() {
  return Aix::physical_memory();
}

////////////////////////////////////////////////////////////////////////////////
// environment support

bool os::getenv(const char* name, char* buf, int len) {
  const char* val = ::getenv(name);
  if (val != NULL && strlen(val) < (size_t)len) {
    strcpy(buf, val);
    return true;
  }
  if (len > 0) buf[0] = 0;  // return a null string
  return false;
}


// Return true if user is running as root.

bool os::have_special_privileges() {
  static bool init = false;
  static bool privileges = false;
  if (!init) {
    privileges = (getuid() != geteuid()) || (getgid() != getegid());
    init = true;
  }
  return privileges;
}

// Helper function, emulates disclaim64 using multiple 32bit disclaims
// because we cannot use disclaim64() on AS/400 and old AIX releases.
static bool my_disclaim64(char* addr, size_t size) {

  if (size == 0) {
    return true;
  }

  // Maximum size 32bit disclaim() accepts. (Theoretically 4GB, but I just do not trust that.)
  const unsigned int maxDisclaimSize = 0x80000000;

  const unsigned int numFullDisclaimsNeeded = (size / maxDisclaimSize);
  const unsigned int lastDisclaimSize = (size % maxDisclaimSize);

  char* p = addr;

  for (int i = 0; i < numFullDisclaimsNeeded; i ++) {
    if (::disclaim(p, maxDisclaimSize, DISCLAIM_ZEROMEM) != 0) {
      //if (Verbose)
      fprintf(stderr, "Cannot disclaim %p - %p (errno %d)\n", p, p + maxDisclaimSize, errno);
      return false;
    }
    p += maxDisclaimSize;
  }

  if (lastDisclaimSize > 0) {
    if (::disclaim(p, lastDisclaimSize, DISCLAIM_ZEROMEM) != 0) {
      //if (Verbose)
        fprintf(stderr, "Cannot disclaim %p - %p (errno %d)\n", p, p + lastDisclaimSize, errno);
      return false;
    }
  }

  return true;
}

// Cpu architecture string
#if defined(PPC32)
static char cpu_arch[] = "ppc";
#elif defined(PPC64)
static char cpu_arch[] = "ppc64";
#else
#error Add appropriate cpu_arch setting
#endif


// Given an address, returns the size of the page backing that address.
size_t os::Aix::query_pagesize(void* addr) {

  vm_page_info pi;
  pi.addr = (uint64_t)addr;
  if (::vmgetinfo(&pi, VM_PAGE_INFO, sizeof(pi)) == 0) {
    return pi.pagesize;
  } else {
    fprintf(stderr, "vmgetinfo failed to retrieve page size for address %p (errno %d).\n", addr, errno);
    assert(false, "vmgetinfo failed to retrieve page size");
    return SIZE_4K;
  }

}

// Returns the kernel thread id of the currently running thread.
pid_t os::Aix::gettid() {
  return (pid_t) thread_self();
}

void os::Aix::initialize_system_info() {

  // get the number of online(logical) cpus instead of configured
  os::_processor_count = sysconf(_SC_NPROCESSORS_ONLN);
  assert(_processor_count > 0, "_processor_count must be > 0");

  // retrieve total physical storage
  os::Aix::meminfo_t mi;
  if (!os::Aix::get_meminfo(&mi)) {
    fprintf(stderr, "os::Aix::get_meminfo failed.\n"); fflush(stderr);
    assert(false, "os::Aix::get_meminfo failed.");
  }
  _physical_memory = (julong) mi.real_total;
}

// Helper function for tracing page sizes.
static const char* describe_pagesize(size_t pagesize) {
  switch (pagesize) {
    case SIZE_4K : return "4K";
    case SIZE_64K: return "64K";
    case SIZE_16M: return "16M";
    case SIZE_16G: return "16G";
    default:
      assert(false, "surprise");
      return "??";
  }
}

// Retrieve information about multipage size support. Will initialize
// Aix::_page_size, Aix::_stack_page_size, Aix::_can_use_64K_pages,
// Aix::_can_use_16M_pages.
// Must be called before calling os::large_page_init().
void os::Aix::query_multipage_support() {

  guarantee(_page_size == -1 &&
            _stack_page_size == -1 &&
            _can_use_64K_pages == -1 &&
            _can_use_16M_pages == -1 &&
            g_multipage_error == -1,
            "do not call twice");

  _page_size = ::sysconf(_SC_PAGESIZE);

  // This really would surprise me.
  assert(_page_size == SIZE_4K, "surprise!");


  // query default data page size (default page size for C-Heap, pthread stacks and .bss).
  // Default data page size is influenced either by linker options (-bdatapsize)
  // or by environment variable LDR_CNTRL (suboption DATAPSIZE). If none is given,
  // default should be 4K.
  size_t data_page_size = SIZE_4K;
  {
    void* p = ::malloc(SIZE_16M);
    data_page_size = os::Aix::query_pagesize(p);
    ::free(p);
  }

  // query default shm page size (LDR_CNTRL SHMPSIZE)
  {
    const int shmid = ::shmget(IPC_PRIVATE, 1, IPC_CREAT | S_IRUSR | S_IWUSR);
    guarantee(shmid != -1, "shmget failed");
    void* p = ::shmat(shmid, NULL, 0);
    ::shmctl(shmid, IPC_RMID, NULL);
    guarantee(p != (void*) -1, "shmat failed");
    _shm_default_page_size = os::Aix::query_pagesize(p);
    ::shmdt(p);
  }

  // before querying the stack page size, make sure we are not running as primordial
  // thread (because primordial thread's stack may have different page size than
  // pthread thread stacks). Running a VM on the primordial thread won't work for a
  // number of reasons so we may just as well guarantee it here
  guarantee(!os::Aix::is_primordial_thread(), "Must not be called for primordial thread");

  // query stack page size
  {
    int dummy = 0;
    _stack_page_size = os::Aix::query_pagesize(&dummy);
    // everything else would surprise me and should be looked into
    guarantee(_stack_page_size == SIZE_4K || _stack_page_size == SIZE_64K, "Wrong page size");
    // also, just for completeness: pthread stacks are allocated from C heap, so
    // stack page size should be the same as data page size
    guarantee(_stack_page_size == data_page_size, "stack page size should be the same as data page size");
  }

  // EXTSHM is bad: among other things, it prevents setting pagesize dynamically
  // for system V shm.
  if (Aix::extshm()) {
    if (Verbose) {
      fprintf(stderr, "EXTSHM is active - will disable large page support.\n"
                      "Please make sure EXTSHM is OFF for large page support.\n");
    }
    g_multipage_error = ERROR_MP_EXTSHM_ACTIVE;
    _can_use_64K_pages = _can_use_16M_pages = 0;
    goto query_multipage_support_end;
  }

  // now check which page sizes the OS claims it supports, and of those, which actually can be used.
  {
    const int MAX_PAGE_SIZES = 4;
    psize_t sizes[MAX_PAGE_SIZES];
    const int num_psizes = ::vmgetinfo(sizes, VMINFO_GETPSIZES, MAX_PAGE_SIZES);
    if (num_psizes == -1) {
      if (Verbose) {
        fprintf(stderr, "vmgetinfo(VMINFO_GETPSIZES) failed (errno: %d)\n", errno);
        fprintf(stderr, "disabling multipage support.\n");
      }
      g_multipage_error = ERROR_MP_VMGETINFO_FAILED;
      _can_use_64K_pages = _can_use_16M_pages = 0;
      goto query_multipage_support_end;
    }
    guarantee(num_psizes > 0, "vmgetinfo(.., VMINFO_GETPSIZES, ...) failed.");
    assert(num_psizes <= MAX_PAGE_SIZES, "Surprise! more than 4 page sizes?");
    if (Verbose) {
      fprintf(stderr, "vmgetinfo(.., VMINFO_GETPSIZES, ...) returns %d supported page sizes: ", num_psizes);
      for (int i = 0; i < num_psizes; i ++) {
        fprintf(stderr, " %s ", describe_pagesize(sizes[i]));
      }
      fprintf(stderr, " .\n");
    }

    // Can we use 64K, 16M pages?
    _can_use_64K_pages = 0;
    _can_use_16M_pages = 0;
    for (int i = 0; i < num_psizes; i ++) {
      if (sizes[i] == SIZE_64K) {
        _can_use_64K_pages = 1;
      } else if (sizes[i] == SIZE_16M) {
        _can_use_16M_pages = 1;
      }
    }

    if (!_can_use_64K_pages) {
      g_multipage_error = ERROR_MP_VMGETINFO_CLAIMS_NO_SUPPORT_FOR_64K;
    }

    // Double-check for 16M pages: Even if AIX claims to be able to use 16M pages,
    // there must be an actual 16M page pool, and we must run with enough rights.
    if (_can_use_16M_pages) {
      const int shmid = ::shmget(IPC_PRIVATE, SIZE_16M, IPC_CREAT | S_IRUSR | S_IWUSR);
      guarantee(shmid != -1, "shmget failed");
      struct shmid_ds shm_buf = { 0 };
      shm_buf.shm_pagesize = SIZE_16M;
      const bool can_set_pagesize = ::shmctl(shmid, SHM_PAGESIZE, &shm_buf) == 0 ? true : false;
      const int en = errno;
      ::shmctl(shmid, IPC_RMID, NULL);
      if (!can_set_pagesize) {
        if (Verbose) {
          fprintf(stderr, "Failed to allocate even one misely 16M page. shmctl failed with %d (%s).\n"
                          "Will deactivate 16M support.\n", en, strerror(en));
        }
        _can_use_16M_pages = 0;
      }
    }

  } // end: check which pages can be used for shared memory

query_multipage_support_end:

  guarantee(_page_size != -1 &&
            _stack_page_size != -1 &&
            _can_use_64K_pages != -1 &&
            _can_use_16M_pages != -1, "Page sizes not properly initialized");

  if (_can_use_64K_pages) {
    g_multipage_error = 0;
  }

  if (Verbose) {
    fprintf(stderr, "Data page size (C-Heap, bss, etc): %s\n", describe_pagesize(data_page_size));
    fprintf(stderr, "Thread stack page size (pthread): %s\n", describe_pagesize(_stack_page_size));
    fprintf(stderr, "Default shared memory page size: %s\n", describe_pagesize(_shm_default_page_size));
    fprintf(stderr, "Can use 64K pages dynamically with shared meory: %s\n", (_can_use_64K_pages ? "yes" :"no"));
    fprintf(stderr, "Can use 16M pages dynamically with shared memory: %s\n", (_can_use_16M_pages ? "yes" :"no"));
    fprintf(stderr, "Multipage error details: %d\n", g_multipage_error);
  }

} // end os::Aix::query_multipage_support()


// The code for this method was initially derived from the version in os_linux.cpp
void os::init_system_properties_values() {
  // The next few definitions allow the code to be verbatim:
#define malloc(n) (char*)NEW_C_HEAP_ARRAY(char, (n), mtInternal)
#define DEFAULT_LIBPATH "/usr/lib:/lib"
#define EXTENSIONS_DIR  "/lib/ext"
#define ENDORSED_DIR    "/lib/endorsed"

  // sysclasspath, java_home, dll_dir
  char *home_path;
  char *dll_path;
  char *pslash;
  char buf[MAXPATHLEN];
  os::jvm_path(buf, sizeof(buf));

  // Found the full path to libjvm.so.
  // Now cut the path to <java_home>/jre if we can.
  *(strrchr(buf, '/')) = '\0'; // get rid of /libjvm.so
  pslash = strrchr(buf, '/');
  if (pslash != NULL) {
    *pslash = '\0';            // get rid of /{client|server|hotspot}
  }

  dll_path = malloc(strlen(buf) + 1);
  strcpy(dll_path, buf);
  Arguments::set_dll_dir(dll_path);

  if (pslash != NULL) {
    pslash = strrchr(buf, '/');
    if (pslash != NULL) {
      *pslash = '\0';          // get rid of /<arch>
      pslash = strrchr(buf, '/');
      if (pslash != NULL) {
        *pslash = '\0';        // get rid of /lib
      }
    }
  }

  home_path = malloc(strlen(buf) + 1);
  strcpy(home_path, buf);
  Arguments::set_java_home(home_path);

  if (!set_boot_path('/', ':')) return;

  // Where to look for native libraries

  // On Aix we get the user setting of LIBPATH
  // Eventually, all the library path setting will be done here.
  char *ld_library_path;

  // Construct the invariant part of ld_library_path.
  ld_library_path = (char *) malloc(sizeof(DEFAULT_LIBPATH));
  sprintf(ld_library_path, DEFAULT_LIBPATH);

  // Get the user setting of LIBPATH, and prepended it.
  char *v = ::getenv("LIBPATH");
  if (v == NULL) {
    v = "";
  }

  char *t = ld_library_path;
  // That's +1 for the colon and +1 for the trailing '\0'
  ld_library_path = (char *) malloc(strlen(v) + 1 + strlen(t) + 1);
  sprintf(ld_library_path, "%s:%s", v, t);

  Arguments::set_library_path(ld_library_path);

  // Extensions directories
  char* cbuf = malloc(strlen(Arguments::get_java_home()) + sizeof(EXTENSIONS_DIR));
  sprintf(cbuf, "%s" EXTENSIONS_DIR, Arguments::get_java_home());
  Arguments::set_ext_dirs(cbuf);

  // Endorsed standards default directory.
  cbuf = malloc(strlen(Arguments::get_java_home()) + sizeof(ENDORSED_DIR));
  sprintf(cbuf, "%s" ENDORSED_DIR, Arguments::get_java_home());
  Arguments::set_endorsed_dirs(cbuf);

#undef malloc
#undef DEFAULT_LIBPATH
#undef EXTENSIONS_DIR
#undef ENDORSED_DIR
}

////////////////////////////////////////////////////////////////////////////////
// breakpoint support

void os::breakpoint() {
  BREAKPOINT;
}

extern "C" void breakpoint() {
  // use debugger to set breakpoint here
}

////////////////////////////////////////////////////////////////////////////////
// signal support

debug_only(static bool signal_sets_initialized = false);
static sigset_t unblocked_sigs, vm_sigs, allowdebug_blocked_sigs;

bool os::Aix::is_sig_ignored(int sig) {
  struct sigaction oact;
  sigaction(sig, (struct sigaction*)NULL, &oact);
  void* ohlr = oact.sa_sigaction ? CAST_FROM_FN_PTR(void*, oact.sa_sigaction)
    : CAST_FROM_FN_PTR(void*, oact.sa_handler);
  if (ohlr == CAST_FROM_FN_PTR(void*, SIG_IGN))
    return true;
  else
    return false;
}

void os::Aix::signal_sets_init() {
  // Should also have an assertion stating we are still single-threaded.
  assert(!signal_sets_initialized, "Already initialized");
  // Fill in signals that are necessarily unblocked for all threads in
  // the VM. Currently, we unblock the following signals:
  // SHUTDOWN{1,2,3}_SIGNAL: for shutdown hooks support (unless over-ridden
  //                         by -Xrs (=ReduceSignalUsage));
  // BREAK_SIGNAL which is unblocked only by the VM thread and blocked by all
  // other threads. The "ReduceSignalUsage" boolean tells us not to alter
  // the dispositions or masks wrt these signals.
  // Programs embedding the VM that want to use the above signals for their
  // own purposes must, at this time, use the "-Xrs" option to prevent
  // interference with shutdown hooks and BREAK_SIGNAL thread dumping.
  // (See bug 4345157, and other related bugs).
  // In reality, though, unblocking these signals is really a nop, since
  // these signals are not blocked by default.
  sigemptyset(&unblocked_sigs);
  sigemptyset(&allowdebug_blocked_sigs);
  sigaddset(&unblocked_sigs, SIGILL);
  sigaddset(&unblocked_sigs, SIGSEGV);
  sigaddset(&unblocked_sigs, SIGBUS);
  sigaddset(&unblocked_sigs, SIGFPE);
  sigaddset(&unblocked_sigs, SIGTRAP);
  sigaddset(&unblocked_sigs, SIGDANGER);
  sigaddset(&unblocked_sigs, SR_signum);

  if (!ReduceSignalUsage) {
   if (!os::Aix::is_sig_ignored(SHUTDOWN1_SIGNAL)) {
     sigaddset(&unblocked_sigs, SHUTDOWN1_SIGNAL);
     sigaddset(&allowdebug_blocked_sigs, SHUTDOWN1_SIGNAL);
   }
   if (!os::Aix::is_sig_ignored(SHUTDOWN2_SIGNAL)) {
     sigaddset(&unblocked_sigs, SHUTDOWN2_SIGNAL);
     sigaddset(&allowdebug_blocked_sigs, SHUTDOWN2_SIGNAL);
   }
   if (!os::Aix::is_sig_ignored(SHUTDOWN3_SIGNAL)) {
     sigaddset(&unblocked_sigs, SHUTDOWN3_SIGNAL);
     sigaddset(&allowdebug_blocked_sigs, SHUTDOWN3_SIGNAL);
   }
  }
  // Fill in signals that are blocked by all but the VM thread.
  sigemptyset(&vm_sigs);
  if (!ReduceSignalUsage)
    sigaddset(&vm_sigs, BREAK_SIGNAL);
  debug_only(signal_sets_initialized = true);
}

// These are signals that are unblocked while a thread is running Java.
// (For some reason, they get blocked by default.)
sigset_t* os::Aix::unblocked_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &unblocked_sigs;
}

// These are the signals that are blocked while a (non-VM) thread is
// running Java. Only the VM thread handles these signals.
sigset_t* os::Aix::vm_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &vm_sigs;
}

// These are signals that are blocked during cond_wait to allow debugger in
sigset_t* os::Aix::allowdebug_blocked_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &allowdebug_blocked_sigs;
}

void os::Aix::hotspot_sigmask(Thread* thread) {

  //Save caller's signal mask before setting VM signal mask
  sigset_t caller_sigmask;
  pthread_sigmask(SIG_BLOCK, NULL, &caller_sigmask);

  OSThread* osthread = thread->osthread();
  osthread->set_caller_sigmask(caller_sigmask);

  pthread_sigmask(SIG_UNBLOCK, os::Aix::unblocked_signals(), NULL);

  if (!ReduceSignalUsage) {
    if (thread->is_VM_thread()) {
      // Only the VM thread handles BREAK_SIGNAL ...
      pthread_sigmask(SIG_UNBLOCK, vm_signals(), NULL);
    } else {
      // ... all other threads block BREAK_SIGNAL
      pthread_sigmask(SIG_BLOCK, vm_signals(), NULL);
    }
  }
}

// retrieve memory information.
// Returns false if something went wrong;
// content of pmi undefined in this case.
bool os::Aix::get_meminfo(meminfo_t* pmi) {

  assert(pmi, "get_meminfo: invalid parameter");

  memset(pmi, 0, sizeof(meminfo_t));

  if (os::Aix::on_pase()) {

    Unimplemented();
    return false;

  } else {

    // On AIX, I use the (dynamically loaded) perfstat library to retrieve memory statistics
    // See:
    // http://publib.boulder.ibm.com/infocenter/systems/index.jsp
    //        ?topic=/com.ibm.aix.basetechref/doc/basetrf1/perfstat_memtot.htm
    // http://publib.boulder.ibm.com/infocenter/systems/index.jsp
    //        ?topic=/com.ibm.aix.files/doc/aixfiles/libperfstat.h.htm

    perfstat_memory_total_t psmt;
    memset (&psmt, '\0', sizeof(psmt));
    const int rc = libperfstat::perfstat_memory_total(NULL, &psmt, sizeof(psmt), 1);
    if (rc == -1) {
      fprintf(stderr, "perfstat_memory_total() failed (errno=%d)\n", errno);
      assert(0, "perfstat_memory_total() failed");
      return false;
    }

    assert(rc == 1, "perfstat_memory_total() - weird return code");

    // excerpt from
    // http://publib.boulder.ibm.com/infocenter/systems/index.jsp
    //        ?topic=/com.ibm.aix.files/doc/aixfiles/libperfstat.h.htm
    // The fields of perfstat_memory_total_t:
    // u_longlong_t virt_total         Total virtual memory (in 4 KB pages).
    // u_longlong_t real_total         Total real memory (in 4 KB pages).
    // u_longlong_t real_free          Free real memory (in 4 KB pages).
    // u_longlong_t pgsp_total         Total paging space (in 4 KB pages).
    // u_longlong_t pgsp_free          Free paging space (in 4 KB pages).

    pmi->virt_total = psmt.virt_total * 4096;
    pmi->real_total = psmt.real_total * 4096;
    pmi->real_free = psmt.real_free * 4096;
    pmi->pgsp_total = psmt.pgsp_total * 4096;
    pmi->pgsp_free = psmt.pgsp_free * 4096;

    return true;

  }
} // end os::Aix::get_meminfo

// Retrieve global cpu information.
// Returns false if something went wrong;
// the content of pci is undefined in this case.
bool os::Aix::get_cpuinfo(cpuinfo_t* pci) {
  assert(pci, "get_cpuinfo: invalid parameter");
  memset(pci, 0, sizeof(cpuinfo_t));

  perfstat_cpu_total_t psct;
  memset (&psct, '\0', sizeof(psct));

  if (-1 == libperfstat::perfstat_cpu_total(NULL, &psct, sizeof(perfstat_cpu_total_t), 1)) {
    fprintf(stderr, "perfstat_cpu_total() failed (errno=%d)\n", errno);
    assert(0, "perfstat_cpu_total() failed");
    return false;
  }

  // global cpu information
  strcpy (pci->description, psct.description);
  pci->processorHZ = psct.processorHZ;
  pci->ncpus = psct.ncpus;
  os::Aix::_logical_cpus = psct.ncpus;
  for (int i = 0; i < 3; i++) {
    pci->loadavg[i] = (double) psct.loadavg[i] / (1 << SBITS);
  }

  // get the processor version from _system_configuration
  switch (_system_configuration.version) {
  case PV_7:
    strcpy(pci->version, "Power PC 7");
    break;
  case PV_6_1:
    strcpy(pci->version, "Power PC 6 DD1.x");
    break;
  case PV_6:
    strcpy(pci->version, "Power PC 6");
    break;
  case PV_5:
    strcpy(pci->version, "Power PC 5");
    break;
  case PV_5_2:
    strcpy(pci->version, "Power PC 5_2");
    break;
  case PV_5_3:
    strcpy(pci->version, "Power PC 5_3");
    break;
  case PV_5_Compat:
    strcpy(pci->version, "PV_5_Compat");
    break;
  case PV_6_Compat:
    strcpy(pci->version, "PV_6_Compat");
    break;
  case PV_7_Compat:
    strcpy(pci->version, "PV_7_Compat");
    break;
  default:
    strcpy(pci->version, "unknown");
  }

  return true;

} //end os::Aix::get_cpuinfo

//////////////////////////////////////////////////////////////////////////////
// detecting pthread library

void os::Aix::libpthread_init() {
  return;
}

//////////////////////////////////////////////////////////////////////////////
// create new thread

// Thread start routine for all newly created threads
static void *java_start(Thread *thread) {

  // find out my own stack dimensions
  {
    // actually, this should do exactly the same as thread->record_stack_base_and_size...
    address base = 0;
    size_t size = 0;
    query_stack_dimensions(&base, &size);
    thread->set_stack_base(base);
    thread->set_stack_size(size);
  }

  // Do some sanity checks.
  CHECK_CURRENT_STACK_PTR(thread->stack_base(), thread->stack_size());

  // Try to randomize the cache line index of hot stack frames.
  // This helps when threads of the same stack traces evict each other's
  // cache lines. The threads can be either from the same JVM instance, or
  // from different JVM instances. The benefit is especially true for
  // processors with hyperthreading technology.

  static int counter = 0;
  int pid = os::current_process_id();
  alloca(((pid ^ counter++) & 7) * 128);

  ThreadLocalStorage::set_thread(thread);

  OSThread* osthread = thread->osthread();

  // thread_id is kernel thread id (similar to Solaris LWP id)
  osthread->set_thread_id(os::Aix::gettid());

  // initialize signal mask for this thread
  os::Aix::hotspot_sigmask(thread);

  // initialize floating point control register
  os::Aix::init_thread_fpu_state();

  assert(osthread->get_state() == RUNNABLE, "invalid os thread state");

  // call one more level start routine
  thread->run();

  return 0;
}

bool os::create_thread(Thread* thread, ThreadType thr_type, size_t stack_size) {

  // We want the whole function to be synchronized.
  ThreadCritical cs;

  assert(thread->osthread() == NULL, "caller responsible");

  // Allocate the OSThread object
  OSThread* osthread = new OSThread(NULL, NULL);
  if (osthread == NULL) {
    return false;
  }

  // set the correct thread state
  osthread->set_thread_type(thr_type);

  // Initial state is ALLOCATED but not INITIALIZED
  osthread->set_state(ALLOCATED);

  thread->set_osthread(osthread);

  // init thread attributes
  pthread_attr_t attr;
  pthread_attr_init(&attr);
  guarantee(pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED) == 0, "???");

  // Make sure we run in 1:1 kernel-user-thread mode.
  if (os::Aix::on_aix()) {
    guarantee(pthread_attr_setscope(&attr, PTHREAD_SCOPE_SYSTEM) == 0, "???");
    guarantee(pthread_attr_setinheritsched(&attr, PTHREAD_EXPLICIT_SCHED) == 0, "???");
  } // end: aix

  // Start in suspended state, and in os::thread_start, wake the thread up.
  guarantee(pthread_attr_setsuspendstate_np(&attr, PTHREAD_CREATE_SUSPENDED_NP) == 0, "???");

  // calculate stack size if it's not specified by caller
  if (os::Aix::supports_variable_stack_size()) {
    if (stack_size == 0) {
      stack_size = os::Aix::default_stack_size(thr_type);

      switch (thr_type) {
      case os::java_thread:
        // Java threads use ThreadStackSize whose default value can be changed with the flag -Xss.
        assert(JavaThread::stack_size_at_create() > 0, "this should be set");
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

    stack_size = MAX2(stack_size, os::Aix::min_stack_allowed);
    pthread_attr_setstacksize(&attr, stack_size);
  } //else let thread_create() pick the default value (96 K on AIX)

  pthread_t tid;
  int ret = pthread_create(&tid, &attr, (void* (*)(void*)) java_start, thread);

  pthread_attr_destroy(&attr);

  if (ret != 0) {
    if (PrintMiscellaneous && (Verbose || WizardMode)) {
      perror("pthread_create()");
    }
    // Need to clean up stuff we've allocated so far
    thread->set_osthread(NULL);
    delete osthread;
    return false;
  }

  // Store pthread info into the OSThread
  osthread->set_pthread_id(tid);

  return true;
}

/////////////////////////////////////////////////////////////////////////////
// attach existing thread

// bootstrap the main thread
bool os::create_main_thread(JavaThread* thread) {
  assert(os::Aix::_main_thread == pthread_self(), "should be called inside main thread");
  return create_attached_thread(thread);
}

bool os::create_attached_thread(JavaThread* thread) {
#ifdef ASSERT
    thread->verify_not_published();
#endif

  // Allocate the OSThread object
  OSThread* osthread = new OSThread(NULL, NULL);

  if (osthread == NULL) {
    return false;
  }

  // Store pthread info into the OSThread
  osthread->set_thread_id(os::Aix::gettid());
  osthread->set_pthread_id(::pthread_self());

  // initialize floating point control register
  os::Aix::init_thread_fpu_state();

  // some sanity checks
  CHECK_CURRENT_STACK_PTR(thread->stack_base(), thread->stack_size());

  // Initial thread state is RUNNABLE
  osthread->set_state(RUNNABLE);

  thread->set_osthread(osthread);

  if (UseNUMA) {
    int lgrp_id = os::numa_get_group_id();
    if (lgrp_id != -1) {
      thread->set_lgrp_id(lgrp_id);
    }
  }

  // initialize signal mask for this thread
  // and save the caller's signal mask
  os::Aix::hotspot_sigmask(thread);

  return true;
}

void os::pd_start_thread(Thread* thread) {
  int status = pthread_continue_np(thread->osthread()->pthread_id());
  assert(status == 0, "thr_continue failed");
}

// Free OS resources related to the OSThread
void os::free_thread(OSThread* osthread) {
  assert(osthread != NULL, "osthread not set");

  if (Thread::current()->osthread() == osthread) {
    // Restore caller's signal mask
    sigset_t sigmask = osthread->caller_sigmask();
    pthread_sigmask(SIG_SETMASK, &sigmask, NULL);
   }

  delete osthread;
}

//////////////////////////////////////////////////////////////////////////////
// thread local storage

int os::allocate_thread_local_storage() {
  pthread_key_t key;
  int rslt = pthread_key_create(&key, NULL);
  assert(rslt == 0, "cannot allocate thread local storage");
  return (int)key;
}

// Note: This is currently not used by VM, as we don't destroy TLS key
// on VM exit.
void os::free_thread_local_storage(int index) {
  int rslt = pthread_key_delete((pthread_key_t)index);
  assert(rslt == 0, "invalid index");
}

void os::thread_local_storage_at_put(int index, void* value) {
  int rslt = pthread_setspecific((pthread_key_t)index, value);
  assert(rslt == 0, "pthread_setspecific failed");
}

extern "C" Thread* get_thread() {
  return ThreadLocalStorage::thread();
}

////////////////////////////////////////////////////////////////////////////////
// time support

// Time since start-up in seconds to a fine granularity.
// Used by VMSelfDestructTimer and the MemProfiler.
double os::elapsedTime() {
  return (double)(os::elapsed_counter()) * 0.000001;
}

jlong os::elapsed_counter() {
  timeval time;
  int status = gettimeofday(&time, NULL);
  return jlong(time.tv_sec) * 1000 * 1000 + jlong(time.tv_usec) - initial_time_count;
}

jlong os::elapsed_frequency() {
  return (1000 * 1000);
}

// For now, we say that linux does not support vtime. I have no idea
// whether it can actually be made to (DLD, 9/13/05).

bool os::supports_vtime() { return false; }
bool os::enable_vtime()   { return false; }
bool os::vtime_enabled()  { return false; }
double os::elapsedVTime() {
  // better than nothing, but not much
  return elapsedTime();
}

jlong os::javaTimeMillis() {
  timeval time;
  int status = gettimeofday(&time, NULL);
  assert(status != -1, "aix error at gettimeofday()");
  return jlong(time.tv_sec) * 1000 + jlong(time.tv_usec / 1000);
}

// We need to manually declare mread_real_time,
// because IBM didn't provide a prototype in time.h.
// (they probably only ever tested in C, not C++)
extern "C"
int mread_real_time(timebasestruct_t *t, size_t size_of_timebasestruct_t);

jlong os::javaTimeNanos() {
  if (os::Aix::on_pase()) {
    Unimplemented();
    return 0;
  }
  else {
    // On AIX use the precision of processors real time clock
    // or time base registers.
    timebasestruct_t time;
    int rc;

    // If the CPU has a time register, it will be used and
    // we have to convert to real time first. After convertion we have following data:
    // time.tb_high [seconds since 00:00:00 UTC on 1.1.1970]
    // time.tb_low  [nanoseconds after the last full second above]
    // We better use mread_real_time here instead of read_real_time
    // to ensure that we will get a monotonic increasing time.
    if (mread_real_time(&time, TIMEBASE_SZ) != RTC_POWER) {
      rc = time_base_to_time(&time, TIMEBASE_SZ);
      assert(rc != -1, "aix error at time_base_to_time()");
    }
    return jlong(time.tb_high) * (1000 * 1000 * 1000) + jlong(time.tb_low);
  }
}

void os::javaTimeNanos_info(jvmtiTimerInfo *info_ptr) {
  {
    // gettimeofday - based on time in seconds since the Epoch thus does not wrap
    info_ptr->max_value = ALL_64_BITS;

    // gettimeofday is a real time clock so it skips
    info_ptr->may_skip_backward = true;
    info_ptr->may_skip_forward = true;
  }

  info_ptr->kind = JVMTI_TIMER_ELAPSED;    // elapsed not CPU time
}

// Return the real, user, and system times in seconds from an
// arbitrary fixed point in the past.
bool os::getTimesSecs(double* process_real_time,
                      double* process_user_time,
                      double* process_system_time) {
  struct tms ticks;
  clock_t real_ticks = times(&ticks);

  if (real_ticks == (clock_t) (-1)) {
    return false;
  } else {
    double ticks_per_second = (double) clock_tics_per_sec;
    *process_user_time = ((double) ticks.tms_utime) / ticks_per_second;
    *process_system_time = ((double) ticks.tms_stime) / ticks_per_second;
    *process_real_time = ((double) real_ticks) / ticks_per_second;

    return true;
  }
}


char * os::local_time_string(char *buf, size_t buflen) {
  struct tm t;
  time_t long_time;
  time(&long_time);
  localtime_r(&long_time, &t);
  jio_snprintf(buf, buflen, "%d-%02d-%02d %02d:%02d:%02d",
               t.tm_year + 1900, t.tm_mon + 1, t.tm_mday,
               t.tm_hour, t.tm_min, t.tm_sec);
  return buf;
}

struct tm* os::localtime_pd(const time_t* clock, struct tm* res) {
  return localtime_r(clock, res);
}

////////////////////////////////////////////////////////////////////////////////
// runtime exit support

// Note: os::shutdown() might be called very early during initialization, or
// called from signal handler. Before adding something to os::shutdown(), make
// sure it is async-safe and can handle partially initialized VM.
void os::shutdown() {

  // allow PerfMemory to attempt cleanup of any persistent resources
  perfMemory_exit();

  // needs to remove object in file system
  AttachListener::abort();

  // flush buffered output, finish log files
  ostream_abort();

  // Check for abort hook
  abort_hook_t abort_hook = Arguments::abort_hook();
  if (abort_hook != NULL) {
    abort_hook();
  }

}

// Note: os::abort() might be called very early during initialization, or
// called from signal handler. Before adding something to os::abort(), make
// sure it is async-safe and can handle partially initialized VM.
void os::abort(bool dump_core) {
  os::shutdown();
  if (dump_core) {
#ifndef PRODUCT
    fdStream out(defaultStream::output_fd());
    out.print_raw("Current thread is ");
    char buf[16];
    jio_snprintf(buf, sizeof(buf), UINTX_FORMAT, os::current_thread_id());
    out.print_raw_cr(buf);
    out.print_raw_cr("Dumping core ...");
#endif
    ::abort(); // dump core
  }

  ::exit(1);
}

// Die immediately, no exit hook, no abort hook, no cleanup.
void os::die() {
  ::abort();
}

// Unused on Aix for now.
void os::set_error_file(const char *logfile) {}


// This method is a copy of JDK's sysGetLastErrorString
// from src/solaris/hpi/src/system_md.c

size_t os::lasterror(char *buf, size_t len) {

  if (errno == 0)  return 0;

  const char *s = ::strerror(errno);
  size_t n = ::strlen(s);
  if (n >= len) {
    n = len - 1;
  }
  ::strncpy(buf, s, n);
  buf[n] = '\0';
  return n;
}

intx os::current_thread_id() { return (intx)pthread_self(); }
int os::current_process_id() {

  // This implementation returns a unique pid, the pid of the
  // launcher thread that starts the vm 'process'.

  // Under POSIX, getpid() returns the same pid as the
  // launcher thread rather than a unique pid per thread.
  // Use gettid() if you want the old pre NPTL behaviour.

  // if you are looking for the result of a call to getpid() that
  // returns a unique pid for the calling thread, then look at the
  // OSThread::thread_id() method in osThread_linux.hpp file

  return (int)(_initial_pid ? _initial_pid : getpid());
}

// DLL functions

const char* os::dll_file_extension() { return ".so"; }

// This must be hard coded because it's the system's temporary
// directory not the java application's temp directory, ala java.io.tmpdir.
const char* os::get_temp_directory() { return "/tmp"; }

static bool file_exists(const char* filename) {
  struct stat statbuf;
  if (filename == NULL || strlen(filename) == 0) {
    return false;
  }
  return os::stat(filename, &statbuf) == 0;
}

bool os::dll_build_name(char* buffer, size_t buflen,
                        const char* pname, const char* fname) {
  bool retval = false;
  // Copied from libhpi
  const size_t pnamelen = pname ? strlen(pname) : 0;

  // Return error on buffer overflow.
  if (pnamelen + strlen(fname) + 10 > (size_t) buflen) {
    *buffer = '\0';
    return retval;
  }

  if (pnamelen == 0) {
    snprintf(buffer, buflen, "lib%s.so", fname);
    retval = true;
  } else if (strchr(pname, *os::path_separator()) != NULL) {
    int n;
    char** pelements = split_path(pname, &n);
    for (int i = 0; i < n; i++) {
      // Really shouldn't be NULL, but check can't hurt
      if (pelements[i] == NULL || strlen(pelements[i]) == 0) {
        continue; // skip the empty path values
      }
      snprintf(buffer, buflen, "%s/lib%s.so", pelements[i], fname);
      if (file_exists(buffer)) {
        retval = true;
        break;
      }
    }
    // release the storage
    for (int i = 0; i < n; i++) {
      if (pelements[i] != NULL) {
        FREE_C_HEAP_ARRAY(char, pelements[i], mtInternal);
      }
    }
    if (pelements != NULL) {
      FREE_C_HEAP_ARRAY(char*, pelements, mtInternal);
    }
  } else {
    snprintf(buffer, buflen, "%s/lib%s.so", pname, fname);
    retval = true;
  }
  return retval;
}

// Check if addr is inside libjvm.so.
bool os::address_is_in_vm(address addr) {

  // Input could be a real pc or a function pointer literal. The latter
  // would be a function descriptor residing in the data segment of a module.

  const LoadedLibraryModule* lib = LoadedLibraries::find_for_text_address(addr);
  if (lib) {
    if (strcmp(lib->get_shortname(), "libjvm.so") == 0) {
      return true;
    } else {
      return false;
    }
  } else {
    lib = LoadedLibraries::find_for_data_address(addr);
    if (lib) {
      if (strcmp(lib->get_shortname(), "libjvm.so") == 0) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }
}

// Resolve an AIX function descriptor literal to a code pointer.
// If the input is a valid code pointer to a text segment of a loaded module,
//   it is returned unchanged.
// If the input is a valid AIX function descriptor, it is resolved to the
//   code entry point.
// If the input is neither a valid function descriptor nor a valid code pointer,
//   NULL is returned.
static address resolve_function_descriptor_to_code_pointer(address p) {

  const LoadedLibraryModule* lib = LoadedLibraries::find_for_text_address(p);
  if (lib) {
    // its a real code pointer
    return p;
  } else {
    lib = LoadedLibraries::find_for_data_address(p);
    if (lib) {
      // pointer to data segment, potential function descriptor
      address code_entry = (address)(((FunctionDescriptor*)p)->entry());
      if (LoadedLibraries::find_for_text_address(code_entry)) {
        // Its a function descriptor
        return code_entry;
      }
    }
  }
  return NULL;
}

bool os::dll_address_to_function_name(address addr, char *buf,
                                      int buflen, int *offset) {
  if (offset) {
    *offset = -1;
  }
  if (buf) {
    buf[0] = '\0';
  }

  // Resolve function ptr literals first.
  addr = resolve_function_descriptor_to_code_pointer(addr);
  if (!addr) {
    return false;
  }

  // Go through Decoder::decode to call getFuncName which reads the name from the traceback table.
  return Decoder::decode(addr, buf, buflen, offset);
}

static int getModuleName(codeptr_t pc,                    // [in] program counter
                         char* p_name, size_t namelen,    // [out] optional: function name
                         char* p_errmsg, size_t errmsglen // [out] optional: user provided buffer for error messages
                         ) {

  // initialize output parameters
  if (p_name && namelen > 0) {
    *p_name = '\0';
  }
  if (p_errmsg && errmsglen > 0) {
    *p_errmsg = '\0';
  }

  const LoadedLibraryModule* const lib = LoadedLibraries::find_for_text_address((address)pc);
  if (lib) {
    if (p_name && namelen > 0) {
      sprintf(p_name, "%.*s", namelen, lib->get_shortname());
    }
    return 0;
  }

  if (Verbose) {
    fprintf(stderr, "pc outside any module");
  }

  return -1;

}

bool os::dll_address_to_library_name(address addr, char* buf,
                                     int buflen, int* offset) {
  if (offset) {
    *offset = -1;
  }
  if (buf) {
      buf[0] = '\0';
  }

  // Resolve function ptr literals first.
  addr = resolve_function_descriptor_to_code_pointer(addr);
  if (!addr) {
    return false;
  }

  if (::getModuleName((codeptr_t) addr, buf, buflen, 0, 0) == 0) {
    return true;
  }
  return false;
}

// Loads .dll/.so and in case of error it checks if .dll/.so was built
// for the same architecture as Hotspot is running on
void *os::dll_load(const char *filename, char *ebuf, int ebuflen) {

  if (ebuf && ebuflen > 0) {
    ebuf[0] = '\0';
    ebuf[ebuflen - 1] = '\0';
  }

  if (!filename || strlen(filename) == 0) {
    ::strncpy(ebuf, "dll_load: empty filename specified", ebuflen - 1);
    return NULL;
  }

  // RTLD_LAZY is currently not implemented. The dl is loaded immediately with all its dependants.
  void * result= ::dlopen(filename, RTLD_LAZY);
  if (result != NULL) {
    // Reload dll cache. Don't do this in signal handling.
    LoadedLibraries::reload();
    return result;
  } else {
    // error analysis when dlopen fails
    const char* const error_report = ::dlerror();
    if (error_report && ebuf && ebuflen > 0) {
      snprintf(ebuf, ebuflen - 1, "%s, LIBPATH=%s, LD_LIBRARY_PATH=%s : %s",
               filename, ::getenv("LIBPATH"), ::getenv("LD_LIBRARY_PATH"), error_report);
    }
  }
  return NULL;
}

// Glibc-2.0 libdl is not MT safe. If you are building with any glibc,
// chances are you might want to run the generated bits against glibc-2.0
// libdl.so, so always use locking for any version of glibc.
void* os::dll_lookup(void* handle, const char* name) {
  pthread_mutex_lock(&dl_mutex);
  void* res = dlsym(handle, name);
  pthread_mutex_unlock(&dl_mutex);
  return res;
}

void* os::get_default_process_handle() {
  return (void*)::dlopen(NULL, RTLD_LAZY);
}

void os::print_dll_info(outputStream *st) {
  st->print_cr("Dynamic libraries:");
  LoadedLibraries::print(st);
}

void os::print_os_info(outputStream* st) {
  st->print("OS:");

  st->print("uname:");
  struct utsname name;
  uname(&name);
  st->print(name.sysname); st->print(" ");
  st->print(name.nodename); st->print(" ");
  st->print(name.release); st->print(" ");
  st->print(name.version); st->print(" ");
  st->print(name.machine);
  st->cr();

  // rlimit
  st->print("rlimit:");
  struct rlimit rlim;

  st->print(" STACK ");
  getrlimit(RLIMIT_STACK, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%uk", rlim.rlim_cur >> 10);

  st->print(", CORE ");
  getrlimit(RLIMIT_CORE, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%uk", rlim.rlim_cur >> 10);

  st->print(", NPROC ");
  st->print("%d", sysconf(_SC_CHILD_MAX));

  st->print(", NOFILE ");
  getrlimit(RLIMIT_NOFILE, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%d", rlim.rlim_cur);

  st->print(", AS ");
  getrlimit(RLIMIT_AS, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%uk", rlim.rlim_cur >> 10);

  // Print limits on DATA, because it limits the C-heap.
  st->print(", DATA ");
  getrlimit(RLIMIT_DATA, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%uk", rlim.rlim_cur >> 10);
  st->cr();

  // load average
  st->print("load average:");
  double loadavg[3] = {-1.L, -1.L, -1.L};
  os::loadavg(loadavg, 3);
  st->print("%0.02f %0.02f %0.02f", loadavg[0], loadavg[1], loadavg[2]);
  st->cr();
}

void os::print_memory_info(outputStream* st) {

  st->print_cr("Memory:");

  st->print_cr("  default page size: %s", describe_pagesize(os::vm_page_size()));
  st->print_cr("  default stack page size: %s", describe_pagesize(os::vm_page_size()));
  st->print_cr("  default shm page size: %s", describe_pagesize(os::Aix::shm_default_page_size()));
  st->print_cr("  can use 64K pages dynamically: %s", (os::Aix::can_use_64K_pages() ? "yes" :"no"));
  st->print_cr("  can use 16M pages dynamically: %s", (os::Aix::can_use_16M_pages() ? "yes" :"no"));
  if (g_multipage_error != 0) {
    st->print_cr("  multipage error: %d", g_multipage_error);
  }

  // print out LDR_CNTRL because it affects the default page sizes
  const char* const ldr_cntrl = ::getenv("LDR_CNTRL");
  st->print_cr("  LDR_CNTRL=%s.", ldr_cntrl ? ldr_cntrl : "<unset>");

  const char* const extshm = ::getenv("EXTSHM");
  st->print_cr("  EXTSHM=%s.", extshm ? extshm : "<unset>");

  // Call os::Aix::get_meminfo() to retrieve memory statistics.
  os::Aix::meminfo_t mi;
  if (os::Aix::get_meminfo(&mi)) {
    char buffer[256];
    if (os::Aix::on_aix()) {
      jio_snprintf(buffer, sizeof(buffer),
                   "  physical total : %llu\n"
                   "  physical free  : %llu\n"
                   "  swap total     : %llu\n"
                   "  swap free      : %llu\n",
                   mi.real_total,
                   mi.real_free,
                   mi.pgsp_total,
                   mi.pgsp_free);
    } else {
      Unimplemented();
    }
    st->print_raw(buffer);
  } else {
    st->print_cr("  (no more information available)");
  }
}

void os::pd_print_cpu_info(outputStream* st) {
  // cpu
  st->print("CPU:");
  st->print("total %d", os::processor_count());
  // It's not safe to query number of active processors after crash
  // st->print("(active %d)", os::active_processor_count());
  st->print(" %s", VM_Version::cpu_features());
  st->cr();
}

void os::print_siginfo(outputStream* st, void* siginfo) {
  // Use common posix version.
  os::Posix::print_siginfo_brief(st, (const siginfo_t*) siginfo);
  st->cr();
}


static void print_signal_handler(outputStream* st, int sig,
                                 char* buf, size_t buflen);

void os::print_signal_handlers(outputStream* st, char* buf, size_t buflen) {
  st->print_cr("Signal Handlers:");
  print_signal_handler(st, SIGSEGV, buf, buflen);
  print_signal_handler(st, SIGBUS , buf, buflen);
  print_signal_handler(st, SIGFPE , buf, buflen);
  print_signal_handler(st, SIGPIPE, buf, buflen);
  print_signal_handler(st, SIGXFSZ, buf, buflen);
  print_signal_handler(st, SIGILL , buf, buflen);
  print_signal_handler(st, INTERRUPT_SIGNAL, buf, buflen);
  print_signal_handler(st, SR_signum, buf, buflen);
  print_signal_handler(st, SHUTDOWN1_SIGNAL, buf, buflen);
  print_signal_handler(st, SHUTDOWN2_SIGNAL , buf, buflen);
  print_signal_handler(st, SHUTDOWN3_SIGNAL , buf, buflen);
  print_signal_handler(st, BREAK_SIGNAL, buf, buflen);
  print_signal_handler(st, SIGTRAP, buf, buflen);
  print_signal_handler(st, SIGDANGER, buf, buflen);
}

static char saved_jvm_path[MAXPATHLEN] = {0};

// Find the full path to the current module, libjvm.so or libjvm_g.so
void os::jvm_path(char *buf, jint buflen) {
  // Error checking.
  if (buflen < MAXPATHLEN) {
    assert(false, "must use a large-enough buffer");
    buf[0] = '\0';
    return;
  }
  // Lazy resolve the path to current module.
  if (saved_jvm_path[0] != 0) {
    strcpy(buf, saved_jvm_path);
    return;
  }

  Dl_info dlinfo;
  int ret = dladdr(CAST_FROM_FN_PTR(void *, os::jvm_path), &dlinfo);
  assert(ret != 0, "cannot locate libjvm");
  char* rp = realpath((char *)dlinfo.dli_fname, buf);
  assert(rp != NULL, "error in realpath(): maybe the 'path' argument is too long?");

  strcpy(saved_jvm_path, buf);
}

void os::print_jni_name_prefix_on(outputStream* st, int args_size) {
  // no prefix required, not even "_"
}

void os::print_jni_name_suffix_on(outputStream* st, int args_size) {
  // no suffix required
}

////////////////////////////////////////////////////////////////////////////////
// sun.misc.Signal support

static volatile jint sigint_count = 0;

static void
UserHandler(int sig, void *siginfo, void *context) {
  // 4511530 - sem_post is serialized and handled by the manager thread. When
  // the program is interrupted by Ctrl-C, SIGINT is sent to every thread. We
  // don't want to flood the manager thread with sem_post requests.
  if (sig == SIGINT && Atomic::add(1, &sigint_count) > 1)
    return;

  // Ctrl-C is pressed during error reporting, likely because the error
  // handler fails to abort. Let VM die immediately.
  if (sig == SIGINT && is_error_reported()) {
    os::die();
  }

  os::signal_notify(sig);
}

void* os::user_handler() {
  return CAST_FROM_FN_PTR(void*, UserHandler);
}

extern "C" {
  typedef void (*sa_handler_t)(int);
  typedef void (*sa_sigaction_t)(int, siginfo_t *, void *);
}

void* os::signal(int signal_number, void* handler) {
  struct sigaction sigAct, oldSigAct;

  sigfillset(&(sigAct.sa_mask));

  // Do not block out synchronous signals in the signal handler.
  // Blocking synchronous signals only makes sense if you can really
  // be sure that those signals won't happen during signal handling,
  // when the blocking applies.  Normal signal handlers are lean and
  // do not cause signals. But our signal handlers tend to be "risky"
  // - secondary SIGSEGV, SIGILL, SIGBUS' may and do happen.
  // On AIX, PASE there was a case where a SIGSEGV happened, followed
  // by a SIGILL, which was blocked due to the signal mask. The process
  // just hung forever. Better to crash from a secondary signal than to hang.
  sigdelset(&(sigAct.sa_mask), SIGSEGV);
  sigdelset(&(sigAct.sa_mask), SIGBUS);
  sigdelset(&(sigAct.sa_mask), SIGILL);
  sigdelset(&(sigAct.sa_mask), SIGFPE);
  sigdelset(&(sigAct.sa_mask), SIGTRAP);

  sigAct.sa_flags   = SA_RESTART|SA_SIGINFO;

  sigAct.sa_handler = CAST_TO_FN_PTR(sa_handler_t, handler);

  if (sigaction(signal_number, &sigAct, &oldSigAct)) {
    // -1 means registration failed
    return (void *)-1;
  }

  return CAST_FROM_FN_PTR(void*, oldSigAct.sa_handler);
}

void os::signal_raise(int signal_number) {
  ::raise(signal_number);
}

//
// The following code is moved from os.cpp for making this
// code platform specific, which it is by its very nature.
//

// Will be modified when max signal is changed to be dynamic
int os::sigexitnum_pd() {
  return NSIG;
}

// a counter for each possible signal value
static volatile jint pending_signals[NSIG+1] = { 0 };

// Linux(POSIX) specific hand shaking semaphore.
static sem_t sig_sem;

void os::signal_init_pd() {
  // Initialize signal structures
  ::memset((void*)pending_signals, 0, sizeof(pending_signals));

  // Initialize signal semaphore
  int rc = ::sem_init(&sig_sem, 0, 0);
  guarantee(rc != -1, "sem_init failed");
}

void os::signal_notify(int sig) {
  Atomic::inc(&pending_signals[sig]);
  ::sem_post(&sig_sem);
}

static int check_pending_signals(bool wait) {
  Atomic::store(0, &sigint_count);
  for (;;) {
    for (int i = 0; i < NSIG + 1; i++) {
      jint n = pending_signals[i];
      if (n > 0 && n == Atomic::cmpxchg(n - 1, &pending_signals[i], n)) {
        return i;
      }
    }
    if (!wait) {
      return -1;
    }
    JavaThread *thread = JavaThread::current();
    ThreadBlockInVM tbivm(thread);

    bool threadIsSuspended;
    do {
      thread->set_suspend_equivalent();
      // cleared by handle_special_suspend_equivalent_condition() or java_suspend_self()

      ::sem_wait(&sig_sem);

      // were we externally suspended while we were waiting?
      threadIsSuspended = thread->handle_special_suspend_equivalent_condition();
      if (threadIsSuspended) {
        //
        // The semaphore has been incremented, but while we were waiting
        // another thread suspended us. We don't want to continue running
        // while suspended because that would surprise the thread that
        // suspended us.
        //
        ::sem_post(&sig_sem);

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

////////////////////////////////////////////////////////////////////////////////
// Virtual Memory

// AddrRange describes an immutable address range
//
// This is a helper class for the 'shared memory bookkeeping' below.
class AddrRange {
  friend class ShmBkBlock;

  char* _start;
  size_t _size;

public:

  AddrRange(char* start, size_t size)
    : _start(start), _size(size)
  {}

  AddrRange(const AddrRange& r)
    : _start(r.start()), _size(r.size())
  {}

  char* start() const { return _start; }
  size_t size() const { return _size; }
  char* end() const { return _start + _size; }
  bool is_empty() const { return _size == 0 ? true : false; }

  static AddrRange empty_range() { return AddrRange(NULL, 0); }

  bool contains(const char* p) const {
    return start() <= p && end() > p;
  }

  bool contains(const AddrRange& range) const {
    return start() <= range.start() && end() >= range.end();
  }

  bool intersects(const AddrRange& range) const {
    return (range.start() <= start() && range.end() > start()) ||
           (range.start() < end() && range.end() >= end()) ||
           contains(range);
  }

  bool is_same_range(const AddrRange& range) const {
    return start() == range.start() && size() == range.size();
  }

  // return the closest inside range consisting of whole pages
  AddrRange find_closest_aligned_range(size_t pagesize) const {
    if (pagesize == 0 || is_empty()) {
      return empty_range();
    }
    char* const from = (char*)align_size_up((intptr_t)_start, pagesize);
    char* const to = (char*)align_size_down((intptr_t)end(), pagesize);
    if (from > to) {
      return empty_range();
    }
    return AddrRange(from, to - from);
  }
};

////////////////////////////////////////////////////////////////////////////
// shared memory bookkeeping
//
// the os::reserve_memory() API and friends hand out different kind of memory, depending
// on need and circumstances. Memory may be allocated with mmap() or with shmget/shmat.
//
// But these memory types have to be treated differently. For example, to uncommit
// mmap-based memory, msync(MS_INVALIDATE) is needed, to uncommit shmat-based memory,
// disclaim64() is needed.
//
// Therefore we need to keep track of the allocated memory segments and their
// properties.

// ShmBkBlock: base class for all blocks in the shared memory bookkeeping
class ShmBkBlock {

  ShmBkBlock* _next;

protected:

  AddrRange _range;
  const size_t _pagesize;
  const bool _pinned;

public:

  ShmBkBlock(AddrRange range, size_t pagesize, bool pinned)
    : _range(range), _pagesize(pagesize), _pinned(pinned) , _next(NULL) {

    assert(_pagesize == SIZE_4K || _pagesize == SIZE_64K || _pagesize == SIZE_16M, "invalid page size");
    assert(!_range.is_empty(), "invalid range");
  }

  virtual void print(outputStream* st) const {
    st->print("0x%p ... 0x%p (%llu) - %d %s pages - %s",
              _range.start(), _range.end(), _range.size(),
              _range.size() / _pagesize, describe_pagesize(_pagesize),
              _pinned ? "pinned" : "");
  }

  enum Type { MMAP, SHMAT };
  virtual Type getType() = 0;

  char* base() const { return _range.start(); }
  size_t size() const { return _range.size(); }

  void setAddrRange(AddrRange range) {
    _range = range;
  }

  bool containsAddress(const char* p) const {
    return _range.contains(p);
  }

  bool containsRange(const char* p, size_t size) const {
    return _range.contains(AddrRange((char*)p, size));
  }

  bool isSameRange(const char* p, size_t size) const {
    return _range.is_same_range(AddrRange((char*)p, size));
  }

  virtual bool disclaim(char* p, size_t size) = 0;
  virtual bool release() = 0;

  // blocks live in a list.
  ShmBkBlock* next() const { return _next; }
  void set_next(ShmBkBlock* blk) { _next = blk; }

}; // end: ShmBkBlock


// ShmBkMappedBlock: describes an block allocated with mmap()
class ShmBkMappedBlock : public ShmBkBlock {
public:

  ShmBkMappedBlock(AddrRange range)
    : ShmBkBlock(range, SIZE_4K, false) {} // mmap: always 4K, never pinned

  void print(outputStream* st) const {
    ShmBkBlock::print(st);
    st->print_cr(" - mmap'ed");
  }

  Type getType() {
    return MMAP;
  }

  bool disclaim(char* p, size_t size) {

    AddrRange r(p, size);

    guarantee(_range.contains(r), "invalid disclaim");

    // only disclaim whole ranges.
    const AddrRange r2 = r.find_closest_aligned_range(_pagesize);
    if (r2.is_empty()) {
      return true;
    }

    const int rc = ::msync(r2.start(), r2.size(), MS_INVALIDATE);

    if (rc != 0) {
      warning("msync(0x%p, %llu, MS_INVALIDATE) failed (%d)\n", r2.start(), r2.size(), errno);
    }

    return rc == 0 ? true : false;
  }

  bool release() {
    // mmap'ed blocks are released using munmap
    if (::munmap(_range.start(), _range.size()) != 0) {
      warning("munmap(0x%p, %llu) failed (%d)\n", _range.start(), _range.size(), errno);
      return false;
    }
    return true;
  }
}; // end: ShmBkMappedBlock

// ShmBkShmatedBlock: describes an block allocated with shmget/shmat()
class ShmBkShmatedBlock : public ShmBkBlock {
public:

  ShmBkShmatedBlock(AddrRange range, size_t pagesize, bool pinned)
    : ShmBkBlock(range, pagesize, pinned) {}

  void print(outputStream* st) const {
    ShmBkBlock::print(st);
    st->print_cr(" - shmat'ed");
  }

  Type getType() {
    return SHMAT;
  }

  bool disclaim(char* p, size_t size) {

    AddrRange r(p, size);

    if (_pinned) {
      return true;
    }

    // shmat'ed blocks are disclaimed using disclaim64
    guarantee(_range.contains(r), "invalid disclaim");

    // only disclaim whole ranges.
    const AddrRange r2 = r.find_closest_aligned_range(_pagesize);
    if (r2.is_empty()) {
      return true;
    }

    const bool rc = my_disclaim64(r2.start(), r2.size());

    if (Verbose && !rc) {
      warning("failed to disclaim shm %p-%p\n", r2.start(), r2.end());
    }

    return rc;
  }

  bool release() {
    bool rc = false;
    if (::shmdt(_range.start()) != 0) {
      warning("shmdt(0x%p) failed (%d)\n", _range.start(), errno);
    } else {
      rc = true;
    }
    return rc;
  }

}; // end: ShmBkShmatedBlock

static ShmBkBlock* g_shmbk_list = NULL;
static volatile jint g_shmbk_table_lock = 0;

// keep some usage statistics
static struct {
  int nodes;    // number of nodes in list
  size_t bytes; // reserved - not committed - bytes.
  int reserves; // how often reserve was called
  int lookups;  // how often a lookup was made
} g_shmbk_stats = { 0, 0, 0, 0 };

// add information about a shared memory segment to the bookkeeping
static void shmbk_register(ShmBkBlock* p_block) {
  guarantee(p_block, "logic error");
  p_block->set_next(g_shmbk_list);
  g_shmbk_list = p_block;
  g_shmbk_stats.reserves ++;
  g_shmbk_stats.bytes += p_block->size();
  g_shmbk_stats.nodes ++;
}

// remove information about a shared memory segment by its starting address
static void shmbk_unregister(ShmBkBlock* p_block) {
  ShmBkBlock* p = g_shmbk_list;
  ShmBkBlock* prev = NULL;
  while (p) {
    if (p == p_block) {
      if (prev) {
        prev->set_next(p->next());
      } else {
        g_shmbk_list = p->next();
      }
      g_shmbk_stats.nodes --;
      g_shmbk_stats.bytes -= p->size();
      return;
    }
    prev = p;
    p = p->next();
  }
  assert(false, "should not happen");
}

// given a pointer, return shared memory bookkeeping record for the segment it points into
// using the returned block info must happen under lock protection
static ShmBkBlock* shmbk_find_by_containing_address(const char* addr) {
  g_shmbk_stats.lookups ++;
  ShmBkBlock* p = g_shmbk_list;
  while (p) {
    if (p->containsAddress(addr)) {
      return p;
    }
    p = p->next();
  }
  return NULL;
}

// dump all information about all memory segments allocated with os::reserve_memory()
void shmbk_dump_info() {
  tty->print_cr("-- shared mem bookkeeping (alive: %d segments, %llu bytes, "
    "total reserves: %d total lookups: %d)",
    g_shmbk_stats.nodes, g_shmbk_stats.bytes, g_shmbk_stats.reserves, g_shmbk_stats.lookups);
  const ShmBkBlock* p = g_shmbk_list;
  int i = 0;
  while (p) {
    p->print(tty);
    p = p->next();
    i ++;
  }
}

#define LOCK_SHMBK     { ThreadCritical _LOCK_SHMBK;
#define UNLOCK_SHMBK   }

// End: shared memory bookkeeping
////////////////////////////////////////////////////////////////////////////////////////////////////

int os::vm_page_size() {
  // Seems redundant as all get out
  assert(os::Aix::page_size() != -1, "must call os::init");
  return os::Aix::page_size();
}

// Aix allocates memory by pages.
int os::vm_allocation_granularity() {
  assert(os::Aix::page_size() != -1, "must call os::init");
  return os::Aix::page_size();
}

int os::Aix::commit_memory_impl(char* addr, size_t size, bool exec) {

  // Commit is a noop. There is no explicit commit
  // needed on AIX. Memory is committed when touched.
  //
  // Debug : check address range for validity
#ifdef ASSERT
  LOCK_SHMBK
    ShmBkBlock* const block = shmbk_find_by_containing_address(addr);
    if (!block) {
      fprintf(stderr, "invalid pointer: " INTPTR_FORMAT "\n", addr);
      shmbk_dump_info();
      assert(false, "invalid pointer");
      return false;
    } else if (!block->containsRange(addr, size)) {
      fprintf(stderr, "invalid range: " INTPTR_FORMAT " .. " INTPTR_FORMAT "\n", addr, addr + size);
      shmbk_dump_info();
      assert(false, "invalid range");
      return false;
    }
  UNLOCK_SHMBK
#endif // ASSERT

  return 0;
}

bool os::pd_commit_memory(char* addr, size_t size, bool exec) {
  return os::Aix::commit_memory_impl(addr, size, exec) == 0;
}

void os::pd_commit_memory_or_exit(char* addr, size_t size, bool exec,
                                  const char* mesg) {
  assert(mesg != NULL, "mesg must be specified");
  os::Aix::commit_memory_impl(addr, size, exec);
}

int os::Aix::commit_memory_impl(char* addr, size_t size,
                                size_t alignment_hint, bool exec) {
  return os::Aix::commit_memory_impl(addr, size, exec);
}

bool os::pd_commit_memory(char* addr, size_t size, size_t alignment_hint,
                          bool exec) {
  return os::Aix::commit_memory_impl(addr, size, alignment_hint, exec) == 0;
}

void os::pd_commit_memory_or_exit(char* addr, size_t size,
                                  size_t alignment_hint, bool exec,
                                  const char* mesg) {
  os::Aix::commit_memory_impl(addr, size, alignment_hint, exec);
}

bool os::pd_uncommit_memory(char* addr, size_t size) {

  // Delegate to ShmBkBlock class which knows how to uncommit its memory.

  bool rc = false;
  LOCK_SHMBK
    ShmBkBlock* const block = shmbk_find_by_containing_address(addr);
    if (!block) {
      fprintf(stderr, "invalid pointer: 0x%p.\n", addr);
      shmbk_dump_info();
      assert(false, "invalid pointer");
      return false;
    } else if (!block->containsRange(addr, size)) {
      fprintf(stderr, "invalid range: 0x%p .. 0x%p.\n", addr, addr + size);
      shmbk_dump_info();
      assert(false, "invalid range");
      return false;
    }
    rc = block->disclaim(addr, size);
  UNLOCK_SHMBK

  if (Verbose && !rc) {
    warning("failed to disclaim 0x%p .. 0x%p (0x%llX bytes).", addr, addr + size, size);
  }
  return rc;
}

bool os::pd_create_stack_guard_pages(char* addr, size_t size) {
  return os::guard_memory(addr, size);
}

bool os::remove_stack_guard_pages(char* addr, size_t size) {
  return os::unguard_memory(addr, size);
}

void os::pd_realign_memory(char *addr, size_t bytes, size_t alignment_hint) {
}

void os::pd_free_memory(char *addr, size_t bytes, size_t alignment_hint) {
}

void os::numa_make_global(char *addr, size_t bytes) {
}

void os::numa_make_local(char *addr, size_t bytes, int lgrp_hint) {
}

bool os::numa_topology_changed() {
  return false;
}

size_t os::numa_get_groups_num() {
  return 1;
}

int os::numa_get_group_id() {
  return 0;
}

size_t os::numa_get_leaf_groups(int *ids, size_t size) {
  if (size > 0) {
    ids[0] = 0;
    return 1;
  }
  return 0;
}

bool os::get_page_info(char *start, page_info* info) {
  return false;
}

char *os::scan_pages(char *start, char* end, page_info* page_expected, page_info* page_found) {
  return end;
}

// Flags for reserve_shmatted_memory:
#define RESSHM_WISHADDR_OR_FAIL                     1
#define RESSHM_TRY_16M_PAGES                        2
#define RESSHM_16M_PAGES_OR_FAIL                    4

// Result of reserve_shmatted_memory:
struct shmatted_memory_info_t {
  char* addr;
  size_t pagesize;
  bool pinned;
};

// Reserve a section of shmatted memory.
// params:
// bytes [in]: size of memory, in bytes
// requested_addr [in]: wish address.
//                      NULL = no wish.
//                      If RESSHM_WISHADDR_OR_FAIL is set in flags and wish address cannot
//                      be obtained, function will fail. Otherwise wish address is treated as hint and
//                      another pointer is returned.
// flags [in]:          some flags. Valid flags are:
//                      RESSHM_WISHADDR_OR_FAIL - fail if wish address is given and cannot be obtained.
//                      RESSHM_TRY_16M_PAGES - try to allocate from 16M page pool
//                          (requires UseLargePages and Use16MPages)
//                      RESSHM_16M_PAGES_OR_FAIL - if you cannot allocate from 16M page pool, fail.
//                          Otherwise any other page size will do.
// p_info [out] :       holds information about the created shared memory segment.
static bool reserve_shmatted_memory(size_t bytes, char* requested_addr, int flags, shmatted_memory_info_t* p_info) {

  assert(p_info, "parameter error");

  // init output struct.
  p_info->addr = NULL;

  // neither should we be here for EXTSHM=ON.
  if (os::Aix::extshm()) {
    ShouldNotReachHere();
  }

  // extract flags. sanity checks.
  const bool wishaddr_or_fail =
    flags & RESSHM_WISHADDR_OR_FAIL;
  const bool try_16M_pages =
    flags & RESSHM_TRY_16M_PAGES;
  const bool f16M_pages_or_fail =
    flags & RESSHM_16M_PAGES_OR_FAIL;

  // first check: if a wish address is given and it is mandatory, but not aligned to segment boundary,
  // shmat will fail anyway, so save some cycles by failing right away
  if (requested_addr && ((uintptr_t)requested_addr % SIZE_256M == 0)) {
    if (wishaddr_or_fail) {
      return false;
    } else {
      requested_addr = NULL;
    }
  }

  char* addr = NULL;

  // Align size of shm up to the largest possible page size, to avoid errors later on when we try to change
  // pagesize dynamically.
  const size_t size = align_size_up(bytes, SIZE_16M);

  // reserve the shared segment
  int shmid = shmget(IPC_PRIVATE, size, IPC_CREAT | S_IRUSR | S_IWUSR);
  if (shmid == -1) {
    warning("shmget(.., %lld, ..) failed (errno: %d).", size, errno);
    return false;
  }

  // Important note:
  // It is very important that we, upon leaving this function, do not leave a shm segment alive.
  // We must right after attaching it remove it from the system. System V shm segments are global and
  // survive the process.
  // So, from here on: Do not assert. Do not return. Always do a "goto cleanup_shm".

  // try forcing the page size
  size_t pagesize = -1; // unknown so far

  if (UseLargePages) {

    struct shmid_ds shmbuf;
    memset(&shmbuf, 0, sizeof(shmbuf));

    // First, try to take from 16M page pool if...
    if (os::Aix::can_use_16M_pages()  // we can ...
        && Use16MPages                // we are not explicitly forbidden to do so (-XX:-Use16MPages)..
        && try_16M_pages) {           // caller wants us to.
      shmbuf.shm_pagesize = SIZE_16M;
      if (shmctl(shmid, SHM_PAGESIZE, &shmbuf) == 0) {
        pagesize = SIZE_16M;
      } else {
        warning("Failed to allocate %d 16M pages. 16M page pool might be exhausted. (shmctl failed with %d)",
                size / SIZE_16M, errno);
        if (f16M_pages_or_fail) {
          goto cleanup_shm;
        }
      }
    }

    // Nothing yet? Try setting 64K pages. Note that I never saw this fail, but in theory it might,
    // because the 64K page pool may also be exhausted.
    if (pagesize == -1) {
      shmbuf.shm_pagesize = SIZE_64K;
      if (shmctl(shmid, SHM_PAGESIZE, &shmbuf) == 0) {
        pagesize = SIZE_64K;
      } else {
        warning("Failed to allocate %d 64K pages. (shmctl failed with %d)",
                size / SIZE_64K, errno);
        // here I give up. leave page_size -1 - later, after attaching, we will query the
        // real page size of the attached memory. (in theory, it may be something different
        // from 4K if LDR_CNTRL SHM_PSIZE is set)
      }
    }
  }

  // sanity point
  assert(pagesize == -1 || pagesize == SIZE_16M || pagesize == SIZE_64K, "wrong page size");

  // Now attach the shared segment.
  addr = (char*) shmat(shmid, requested_addr, 0);
  if (addr == (char*)-1) {
    // How to handle attach failure:
    // If it failed for a specific wish address, tolerate this: in that case, if wish address was
    // mandatory, fail, if not, retry anywhere.
    // If it failed for any other reason, treat that as fatal error.
    addr = NULL;
    if (requested_addr) {
      if (wishaddr_or_fail) {
        goto cleanup_shm;
      } else {
        addr = (char*) shmat(shmid, NULL, 0);
        if (addr == (char*)-1) { // fatal
          addr = NULL;
          warning("shmat failed (errno: %d)", errno);
          goto cleanup_shm;
        }
      }
    } else { // fatal
      addr = NULL;
      warning("shmat failed (errno: %d)", errno);
      goto cleanup_shm;
    }
  }

  // sanity point
  assert(addr && addr != (char*) -1, "wrong address");

  // after successful Attach remove the segment - right away.
  if (::shmctl(shmid, IPC_RMID, NULL) == -1) {
    warning("shmctl(%u, IPC_RMID) failed (%d)\n", shmid, errno);
    guarantee(false, "failed to remove shared memory segment!");
  }
  shmid = -1;

  // query the real page size. In case setting the page size did not work (see above), the system
  // may have given us something other then 4K (LDR_CNTRL)
  {
    const size_t real_pagesize = os::Aix::query_pagesize(addr);
    if (pagesize != -1) {
      assert(pagesize == real_pagesize, "unexpected pagesize after shmat");
    } else {
      pagesize = real_pagesize;
    }
  }

  // Now register the reserved block with internal book keeping.
  LOCK_SHMBK
    const bool pinned = pagesize >= SIZE_16M ? true : false;
    ShmBkShmatedBlock* const p_block = new ShmBkShmatedBlock(AddrRange(addr, size), pagesize, pinned);
    assert(p_block, "");
    shmbk_register(p_block);
  UNLOCK_SHMBK

cleanup_shm:

  // if we have not done so yet, remove the shared memory segment. This is very important.
  if (shmid != -1) {
    if (::shmctl(shmid, IPC_RMID, NULL) == -1) {
      warning("shmctl(%u, IPC_RMID) failed (%d)\n", shmid, errno);
      guarantee(false, "failed to remove shared memory segment!");
    }
    shmid = -1;
  }

  // trace
  if (Verbose && !addr) {
    if (requested_addr != NULL) {
      warning("failed to shm-allocate 0x%llX bytes at wish address 0x%p.", size, requested_addr);
    } else {
      warning("failed to shm-allocate 0x%llX bytes at any address.", size);
    }
  }

  // hand info to caller
  if (addr) {
    p_info->addr = addr;
    p_info->pagesize = pagesize;
    p_info->pinned = pagesize == SIZE_16M ? true : false;
  }

  // sanity test:
  if (requested_addr && addr && wishaddr_or_fail) {
    guarantee(addr == requested_addr, "shmat error");
  }

  // just one more test to really make sure we have no dangling shm segments.
  guarantee(shmid == -1, "dangling shm segments");

  return addr ? true : false;

} // end: reserve_shmatted_memory

// Reserve memory using mmap. Behaves the same as reserve_shmatted_memory():
// will return NULL in case of an error.
static char* reserve_mmaped_memory(size_t bytes, char* requested_addr) {

  // if a wish address is given, but not aligned to 4K page boundary, mmap will fail.
  if (requested_addr && ((uintptr_t)requested_addr % os::vm_page_size() != 0)) {
    warning("Wish address 0x%p not aligned to page boundary.", requested_addr);
    return NULL;
  }

  const size_t size = align_size_up(bytes, SIZE_4K);

  // Note: MAP_SHARED (instead of MAP_PRIVATE) needed to be able to
  // msync(MS_INVALIDATE) (see os::uncommit_memory)
  int flags = MAP_ANONYMOUS | MAP_SHARED;

  // MAP_FIXED is needed to enforce requested_addr - manpage is vague about what
  // it means if wishaddress is given but MAP_FIXED is not set.
  //
  // Note however that this changes semantics in SPEC1170 mode insofar as MAP_FIXED
  // clobbers the address range, which is probably not what the caller wants. That's
  // why I assert here (again) that the SPEC1170 compat mode is off.
  // If we want to be able to run under SPEC1170, we have to do some porting and
  // testing.
  if (requested_addr != NULL) {
    assert(!os::Aix::xpg_sus_mode(), "SPEC1170 mode not allowed.");
    flags |= MAP_FIXED;
  }

  char* addr = (char*)::mmap(requested_addr, size, PROT_READ|PROT_WRITE|PROT_EXEC, flags, -1, 0);

  if (addr == MAP_FAILED) {
    // attach failed: tolerate for specific wish addresses. Not being able to attach
    // anywhere is a fatal error.
    if (requested_addr == NULL) {
      // It's ok to fail here if the machine has not enough memory.
      warning("mmap(NULL, 0x%llX, ..) failed (%d)", size, errno);
    }
    addr = NULL;
    goto cleanup_mmap;
  }

  // If we did request a specific address and that address was not available, fail.
  if (addr && requested_addr) {
    guarantee(addr == requested_addr, "unexpected");
  }

  // register this mmap'ed segment with book keeping
  LOCK_SHMBK
    ShmBkMappedBlock* const p_block = new ShmBkMappedBlock(AddrRange(addr, size));
    assert(p_block, "");
    shmbk_register(p_block);
  UNLOCK_SHMBK

cleanup_mmap:

  // trace
  if (Verbose) {
    if (addr) {
      fprintf(stderr, "mmap-allocated 0x%p .. 0x%p (0x%llX bytes)\n", addr, addr + bytes, bytes);
    }
    else {
      if (requested_addr != NULL) {
        warning("failed to mmap-allocate 0x%llX bytes at wish address 0x%p.", bytes, requested_addr);
      } else {
        warning("failed to mmap-allocate 0x%llX bytes at any address.", bytes);
      }
    }
  }

  return addr;

} // end: reserve_mmaped_memory

// Reserves and attaches a shared memory segment.
// Will assert if a wish address is given and could not be obtained.
char* os::pd_reserve_memory(size_t bytes, char* requested_addr, size_t alignment_hint) {
  return os::attempt_reserve_memory_at(bytes, requested_addr);
}

bool os::pd_release_memory(char* addr, size_t size) {

  // delegate to ShmBkBlock class which knows how to uncommit its memory.

  bool rc = false;
  LOCK_SHMBK
    ShmBkBlock* const block = shmbk_find_by_containing_address(addr);
    if (!block) {
      fprintf(stderr, "invalid pointer: 0x%p.\n", addr);
      shmbk_dump_info();
      assert(false, "invalid pointer");
      return false;
    }
    else if (!block->isSameRange(addr, size)) {
      if (block->getType() == ShmBkBlock::MMAP) {
        // Release only the same range or a the beginning or the end of a range.
        if (block->base() == addr && size < block->size()) {
          ShmBkMappedBlock* const b = new ShmBkMappedBlock(AddrRange(block->base() + size, block->size() - size));
          assert(b, "");
          shmbk_register(b);
          block->setAddrRange(AddrRange(addr, size));
        }
        else if (addr > block->base() && addr + size == block->base() + block->size()) {
          ShmBkMappedBlock* const b = new ShmBkMappedBlock(AddrRange(block->base(), block->size() - size));
          assert(b, "");
          shmbk_register(b);
          block->setAddrRange(AddrRange(addr, size));
        }
        else {
          fprintf(stderr, "invalid mmap range: 0x%p .. 0x%p.\n", addr, addr + size);
          shmbk_dump_info();
          assert(false, "invalid mmap range");
          return false;
        }
      }
      else {
        // Release only the same range. No partial release allowed.
        // Soften the requirement a bit, because the user may think he owns a smaller size
        // than the block is due to alignment etc.
        if (block->base() != addr || block->size() < size) {
          fprintf(stderr, "invalid shmget range: 0x%p .. 0x%p.\n", addr, addr + size);
          shmbk_dump_info();
          assert(false, "invalid shmget range");
          return false;
        }
      }
    }
    rc = block->release();
    assert(rc, "release failed");
    // remove block from bookkeeping
    shmbk_unregister(block);
    delete block;
  UNLOCK_SHMBK

  if (!rc) {
    warning("failed to released %lu bytes at 0x%p", size, addr);
  }

  return rc;
}

static bool checked_mprotect(char* addr, size_t size, int prot) {

  // Little problem here: if SPEC1170 behaviour is off, mprotect() on AIX will
  // not tell me if protection failed when trying to protect an un-protectable range.
  //
  // This means if the memory was allocated using shmget/shmat, protection wont work
  // but mprotect will still return 0:
  //
  // See http://publib.boulder.ibm.com/infocenter/pseries/v5r3/index.jsp?topic=/com.ibm.aix.basetechref/doc/basetrf1/mprotect.htm

  bool rc = ::mprotect(addr, size, prot) == 0 ? true : false;

  if (!rc) {
    const char* const s_errno = strerror(errno);
    warning("mprotect(" PTR_FORMAT "-" PTR_FORMAT ", 0x%X) failed (%s).", addr, addr + size, prot, s_errno);
    return false;
  }

  // mprotect success check
  //
  // Mprotect said it changed the protection but can I believe it?
  //
  // To be sure I need to check the protection afterwards. Try to
  // read from protected memory and check whether that causes a segfault.
  //
  if (!os::Aix::xpg_sus_mode()) {

    if (StubRoutines::SafeFetch32_stub()) {

      const bool read_protected =
        (SafeFetch32((int*)addr, 0x12345678) == 0x12345678 &&
         SafeFetch32((int*)addr, 0x76543210) == 0x76543210) ? true : false;

      if (prot & PROT_READ) {
        rc = !read_protected;
      } else {
        rc = read_protected;
      }
    }
  }
  if (!rc) {
    assert(false, "mprotect failed.");
  }
  return rc;
}

// Set protections specified
bool os::protect_memory(char* addr, size_t size, ProtType prot, bool is_committed) {
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
  return checked_mprotect(addr, size, p);
}

bool os::guard_memory(char* addr, size_t size) {
  return checked_mprotect(addr, size, PROT_NONE);
}

bool os::unguard_memory(char* addr, size_t size) {
  return checked_mprotect(addr, size, PROT_READ|PROT_WRITE|PROT_EXEC);
}

// Large page support

static size_t _large_page_size = 0;

// Enable large page support if OS allows that.
void os::large_page_init() {

  // Note: os::Aix::query_multipage_support must run first.

  if (!UseLargePages) {
    return;
  }

  if (!Aix::can_use_64K_pages()) {
    assert(!Aix::can_use_16M_pages(), "64K is a precondition for 16M.");
    UseLargePages = false;
    return;
  }

  if (!Aix::can_use_16M_pages() && Use16MPages) {
    fprintf(stderr, "Cannot use 16M pages. Please ensure that there is a 16M page pool "
            " and that the VM runs with CAP_BYPASS_RAC_VMM and CAP_PROPAGATE capabilities.\n");
  }

  // Do not report 16M page alignment as part of os::_page_sizes if we are
  // explicitly forbidden from using 16M pages. Doing so would increase the
  // alignment the garbage collector calculates with, slightly increasing
  // heap usage. We should only pay for 16M alignment if we really want to
  // use 16M pages.
  if (Use16MPages && Aix::can_use_16M_pages()) {
    _large_page_size = SIZE_16M;
    _page_sizes[0] = SIZE_16M;
    _page_sizes[1] = SIZE_64K;
    _page_sizes[2] = SIZE_4K;
    _page_sizes[3] = 0;
  } else if (Aix::can_use_64K_pages()) {
    _large_page_size = SIZE_64K;
    _page_sizes[0] = SIZE_64K;
    _page_sizes[1] = SIZE_4K;
    _page_sizes[2] = 0;
  }

  if (Verbose) {
    ("Default large page size is 0x%llX.", _large_page_size);
  }
} // end: os::large_page_init()

char* os::reserve_memory_special(size_t bytes, size_t alignment, char* req_addr, bool exec) {
  // "exec" is passed in but not used. Creating the shared image for
  // the code cache doesn't have an SHM_X executable permission to check.
  Unimplemented();
  return 0;
}

bool os::release_memory_special(char* base, size_t bytes) {
  // detaching the SHM segment will also delete it, see reserve_memory_special()
  Unimplemented();
  return false;
}

size_t os::large_page_size() {
  return _large_page_size;
}

bool os::can_commit_large_page_memory() {
  // Well, sadly we cannot commit anything at all (see comment in
  // os::commit_memory) but we claim to so we can make use of large pages
  return true;
}

bool os::can_execute_large_page_memory() {
  // We can do that
  return true;
}

// Reserve memory at an arbitrary address, only if that area is
// available (and not reserved for something else).
char* os::pd_attempt_reserve_memory_at(size_t bytes, char* requested_addr) {

  bool use_mmap = false;

  // mmap: smaller graining, no large page support
  // shm: large graining (256M), large page support, limited number of shm segments
  //
  // Prefer mmap wherever we either do not need large page support or have OS limits

  if (!UseLargePages || bytes < SIZE_16M) {
    use_mmap = true;
  }

  char* addr = NULL;
  if (use_mmap) {
    addr = reserve_mmaped_memory(bytes, requested_addr);
  } else {
    // shmat: wish address is mandatory, and do not try 16M pages here.
    shmatted_memory_info_t info;
    const int flags = RESSHM_WISHADDR_OR_FAIL;
    if (reserve_shmatted_memory(bytes, requested_addr, flags, &info)) {
      addr = info.addr;
    }
  }

  return addr;
}

size_t os::read(int fd, void *buf, unsigned int nBytes) {
  return ::read(fd, buf, nBytes);
}

#define NANOSECS_PER_MILLISEC 1000000

int os::sleep(Thread* thread, jlong millis, bool interruptible) {
  assert(thread == Thread::current(), "thread consistency check");

  // Prevent nasty overflow in deadline calculation
  // by handling long sleeps similar to solaris or windows.
  const jlong limit = INT_MAX;
  int result;
  while (millis > limit) {
    if ((result = os::sleep(thread, limit, interruptible)) != OS_OK) {
      return result;
    }
    millis -= limit;
  }

  ParkEvent * const slp = thread->_SleepEvent;
  slp->reset();
  OrderAccess::fence();

  if (interruptible) {
    jlong prevtime = javaTimeNanos();

    // Prevent precision loss and too long sleeps
    jlong deadline = prevtime + millis * NANOSECS_PER_MILLISEC;

    for (;;) {
      if (os::is_interrupted(thread, true)) {
        return OS_INTRPT;
      }

      jlong newtime = javaTimeNanos();

      assert(newtime >= prevtime, "time moving backwards");
      // Doing prevtime and newtime in microseconds doesn't help precision,
      // and trying to round up to avoid lost milliseconds can result in a
      // too-short delay.
      millis -= (newtime - prevtime) / NANOSECS_PER_MILLISEC;

      if (millis <= 0) {
        return OS_OK;
      }

      // Stop sleeping if we passed the deadline
      if (newtime >= deadline) {
        return OS_OK;
      }

      prevtime = newtime;

      {
        assert(thread->is_Java_thread(), "sanity check");
        JavaThread *jt = (JavaThread *) thread;
        ThreadBlockInVM tbivm(jt);
        OSThreadWaitState osts(jt->osthread(), false /* not Object.wait() */);

        jt->set_suspend_equivalent();

        slp->park(millis);

        // were we externally suspended while we were waiting?
        jt->check_and_wait_while_suspended();
      }
    }
  } else {
    OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
    jlong prevtime = javaTimeNanos();

    // Prevent precision loss and too long sleeps
    jlong deadline = prevtime + millis * NANOSECS_PER_MILLISEC;

    for (;;) {
      // It'd be nice to avoid the back-to-back javaTimeNanos() calls on
      // the 1st iteration ...
      jlong newtime = javaTimeNanos();

      if (newtime - prevtime < 0) {
        // time moving backwards, should only happen if no monotonic clock
        // not a guarantee() because JVM should not abort on kernel/glibc bugs
        // - HS14 Commented out as not implemented.
        // - TODO Maybe we should implement it?
        //assert(!Aix::supports_monotonic_clock(), "time moving backwards");
      } else {
        millis -= (newtime - prevtime) / NANOSECS_PER_MILLISEC;
      }

      if (millis <= 0) break;

      if (newtime >= deadline) {
        break;
      }

      prevtime = newtime;
      slp->park(millis);
    }
    return OS_OK;
  }
}

void os::naked_short_sleep(jlong ms) {
  struct timespec req;

  assert(ms < 1000, "Un-interruptable sleep, short time use only");
  req.tv_sec = 0;
  if (ms > 0) {
    req.tv_nsec = (ms % 1000) * 1000000;
  }
  else {
    req.tv_nsec = 1;
  }

  nanosleep(&req, NULL);

  return;
}

// Sleep forever; naked call to OS-specific sleep; use with CAUTION
void os::infinite_sleep() {
  while (true) {    // sleep forever ...
    ::sleep(100);   // ... 100 seconds at a time
  }
}

// Used to convert frequent JVM_Yield() to nops
bool os::dont_yield() {
  return DontYieldALot;
}

void os::yield() {
  sched_yield();
}

os::YieldResult os::NakedYield() { sched_yield(); return os::YIELD_UNKNOWN; }

void os::yield_all(int attempts) {
  // Yields to all threads, including threads with lower priorities
  // Threads on Linux are all with same priority. The Solaris style
  // os::yield_all() with nanosleep(1ms) is not necessary.
  sched_yield();
}

// Called from the tight loops to possibly influence time-sharing heuristics
void os::loop_breaker(int attempts) {
  os::yield_all(attempts);
}

////////////////////////////////////////////////////////////////////////////////
// thread priority support

// From AIX manpage to pthread_setschedparam
// (see: http://publib.boulder.ibm.com/infocenter/pseries/v5r3/index.jsp?
//    topic=/com.ibm.aix.basetechref/doc/basetrf1/pthread_setschedparam.htm):
//
// "If schedpolicy is SCHED_OTHER, then sched_priority must be in the
// range from 40 to 80, where 40 is the least favored priority and 80
// is the most favored."
//
// (Actually, I doubt this even has an impact on AIX, as we do kernel
// scheduling there; however, this still leaves iSeries.)
//
// We use the same values for AIX and PASE.
int os::java_to_os_priority[CriticalPriority + 1] = {
  54,             // 0 Entry should never be used

  55,             // 1 MinPriority
  55,             // 2
  56,             // 3

  56,             // 4
  57,             // 5 NormPriority
  57,             // 6

  58,             // 7
  58,             // 8
  59,             // 9 NearMaxPriority

  60,             // 10 MaxPriority

  60              // 11 CriticalPriority
};

OSReturn os::set_native_priority(Thread* thread, int newpri) {
  if (!UseThreadPriorities) return OS_OK;
  pthread_t thr = thread->osthread()->pthread_id();
  int policy = SCHED_OTHER;
  struct sched_param param;
  param.sched_priority = newpri;
  int ret = pthread_setschedparam(thr, policy, &param);

  if (Verbose) {
    if (ret == 0) {
      fprintf(stderr, "changed priority of thread %d to %d\n", (int)thr, newpri);
    } else {
      fprintf(stderr, "Could not changed priority for thread %d to %d (error %d, %s)\n",
              (int)thr, newpri, ret, strerror(ret));
    }
  }
  return (ret == 0) ? OS_OK : OS_ERR;
}

OSReturn os::get_native_priority(const Thread* const thread, int *priority_ptr) {
  if (!UseThreadPriorities) {
    *priority_ptr = java_to_os_priority[NormPriority];
    return OS_OK;
  }
  pthread_t thr = thread->osthread()->pthread_id();
  int policy = SCHED_OTHER;
  struct sched_param param;
  int ret = pthread_getschedparam(thr, &policy, &param);
  *priority_ptr = param.sched_priority;

  return (ret == 0) ? OS_OK : OS_ERR;
}

// Hint to the underlying OS that a task switch would not be good.
// Void return because it's a hint and can fail.
void os::hint_no_preempt() {}

////////////////////////////////////////////////////////////////////////////////
// suspend/resume support

//  the low-level signal-based suspend/resume support is a remnant from the
//  old VM-suspension that used to be for java-suspension, safepoints etc,
//  within hotspot. Now there is a single use-case for this:
//    - calling get_thread_pc() on the VMThread by the flat-profiler task
//      that runs in the watcher thread.
//  The remaining code is greatly simplified from the more general suspension
//  code that used to be used.
//
//  The protocol is quite simple:
//  - suspend:
//      - sends a signal to the target thread
//      - polls the suspend state of the osthread using a yield loop
//      - target thread signal handler (SR_handler) sets suspend state
//        and blocks in sigsuspend until continued
//  - resume:
//      - sets target osthread state to continue
//      - sends signal to end the sigsuspend loop in the SR_handler
//
//  Note that the SR_lock plays no role in this suspend/resume protocol.
//

static void resume_clear_context(OSThread *osthread) {
  osthread->set_ucontext(NULL);
  osthread->set_siginfo(NULL);
}

static void suspend_save_context(OSThread *osthread, siginfo_t* siginfo, ucontext_t* context) {
  osthread->set_ucontext(context);
  osthread->set_siginfo(siginfo);
}

//
// Handler function invoked when a thread's execution is suspended or
// resumed. We have to be careful that only async-safe functions are
// called here (Note: most pthread functions are not async safe and
// should be avoided.)
//
// Note: sigwait() is a more natural fit than sigsuspend() from an
// interface point of view, but sigwait() prevents the signal hander
// from being run. libpthread would get very confused by not having
// its signal handlers run and prevents sigwait()'s use with the
// mutex granting granting signal.
//
// Currently only ever called on the VMThread and JavaThreads (PC sampling).
//
static void SR_handler(int sig, siginfo_t* siginfo, ucontext_t* context) {
  // Save and restore errno to avoid confusing native code with EINTR
  // after sigsuspend.
  int old_errno = errno;

  Thread* thread = Thread::current();
  OSThread* osthread = thread->osthread();
  assert(thread->is_VM_thread() || thread->is_Java_thread(), "Must be VMThread or JavaThread");

  os::SuspendResume::State current = osthread->sr.state();
  if (current == os::SuspendResume::SR_SUSPEND_REQUEST) {
    suspend_save_context(osthread, siginfo, context);

    // attempt to switch the state, we assume we had a SUSPEND_REQUEST
    os::SuspendResume::State state = osthread->sr.suspended();
    if (state == os::SuspendResume::SR_SUSPENDED) {
      sigset_t suspend_set;  // signals for sigsuspend()

      // get current set of blocked signals and unblock resume signal
      pthread_sigmask(SIG_BLOCK, NULL, &suspend_set);
      sigdelset(&suspend_set, SR_signum);

      // wait here until we are resumed
      while (1) {
        sigsuspend(&suspend_set);

        os::SuspendResume::State result = osthread->sr.running();
        if (result == os::SuspendResume::SR_RUNNING) {
          break;
        }
      }

    } else if (state == os::SuspendResume::SR_RUNNING) {
      // request was cancelled, continue
    } else {
      ShouldNotReachHere();
    }

    resume_clear_context(osthread);
  } else if (current == os::SuspendResume::SR_RUNNING) {
    // request was cancelled, continue
  } else if (current == os::SuspendResume::SR_WAKEUP_REQUEST) {
    // ignore
  } else {
    ShouldNotReachHere();
  }

  errno = old_errno;
}


static int SR_initialize() {
  struct sigaction act;
  char *s;
  // Get signal number to use for suspend/resume
  if ((s = ::getenv("_JAVA_SR_SIGNUM")) != 0) {
    int sig = ::strtol(s, 0, 10);
    if (sig > 0 || sig < NSIG) {
      SR_signum = sig;
    }
  }

  assert(SR_signum > SIGSEGV && SR_signum > SIGBUS,
        "SR_signum must be greater than max(SIGSEGV, SIGBUS), see 4355769");

  sigemptyset(&SR_sigset);
  sigaddset(&SR_sigset, SR_signum);

  // Set up signal handler for suspend/resume.
  act.sa_flags = SA_RESTART|SA_SIGINFO;
  act.sa_handler = (void (*)(int)) SR_handler;

  // SR_signum is blocked by default.
  // 4528190 - We also need to block pthread restart signal (32 on all
  // supported Linux platforms). Note that LinuxThreads need to block
  // this signal for all threads to work properly. So we don't have
  // to use hard-coded signal number when setting up the mask.
  pthread_sigmask(SIG_BLOCK, NULL, &act.sa_mask);

  if (sigaction(SR_signum, &act, 0) == -1) {
    return -1;
  }

  // Save signal flag
  os::Aix::set_our_sigflags(SR_signum, act.sa_flags);
  return 0;
}

static int SR_finalize() {
  return 0;
}

static int sr_notify(OSThread* osthread) {
  int status = pthread_kill(osthread->pthread_id(), SR_signum);
  assert_status(status == 0, status, "pthread_kill");
  return status;
}

// "Randomly" selected value for how long we want to spin
// before bailing out on suspending a thread, also how often
// we send a signal to a thread we want to resume
static const int RANDOMLY_LARGE_INTEGER = 1000000;
static const int RANDOMLY_LARGE_INTEGER2 = 100;

// returns true on success and false on error - really an error is fatal
// but this seems the normal response to library errors
static bool do_suspend(OSThread* osthread) {
  assert(osthread->sr.is_running(), "thread should be running");
  // mark as suspended and send signal

  if (osthread->sr.request_suspend() != os::SuspendResume::SR_SUSPEND_REQUEST) {
    // failed to switch, state wasn't running?
    ShouldNotReachHere();
    return false;
  }

  if (sr_notify(osthread) != 0) {
    // try to cancel, switch to running

    os::SuspendResume::State result = osthread->sr.cancel_suspend();
    if (result == os::SuspendResume::SR_RUNNING) {
      // cancelled
      return false;
    } else if (result == os::SuspendResume::SR_SUSPENDED) {
      // somehow managed to suspend
      return true;
    } else {
      ShouldNotReachHere();
      return false;
    }
  }

  // managed to send the signal and switch to SUSPEND_REQUEST, now wait for SUSPENDED

  for (int n = 0; !osthread->sr.is_suspended(); n++) {
    for (int i = 0; i < RANDOMLY_LARGE_INTEGER2 && !osthread->sr.is_suspended(); i++) {
      os::yield_all(i);
    }

    // timeout, try to cancel the request
    if (n >= RANDOMLY_LARGE_INTEGER) {
      os::SuspendResume::State cancelled = osthread->sr.cancel_suspend();
      if (cancelled == os::SuspendResume::SR_RUNNING) {
        return false;
      } else if (cancelled == os::SuspendResume::SR_SUSPENDED) {
        return true;
      } else {
        ShouldNotReachHere();
        return false;
      }
    }
  }

  guarantee(osthread->sr.is_suspended(), "Must be suspended");
  return true;
}

static void do_resume(OSThread* osthread) {
  //assert(osthread->sr.is_suspended(), "thread should be suspended");

  if (osthread->sr.request_wakeup() != os::SuspendResume::SR_WAKEUP_REQUEST) {
    // failed to switch to WAKEUP_REQUEST
    ShouldNotReachHere();
    return;
  }

  while (!osthread->sr.is_running()) {
    if (sr_notify(osthread) == 0) {
      for (int n = 0; n < RANDOMLY_LARGE_INTEGER && !osthread->sr.is_running(); n++) {
        for (int i = 0; i < 100 && !osthread->sr.is_running(); i++) {
          os::yield_all(i);
        }
      }
    } else {
      ShouldNotReachHere();
    }
  }

  guarantee(osthread->sr.is_running(), "Must be running!");
}

////////////////////////////////////////////////////////////////////////////////
// interrupt support

void os::interrupt(Thread* thread) {
  assert(Thread::current() == thread || Threads_lock->owned_by_self(),
    "possibility of dangling Thread pointer");

  OSThread* osthread = thread->osthread();

  if (!osthread->interrupted()) {
    osthread->set_interrupted(true);
    // More than one thread can get here with the same value of osthread,
    // resulting in multiple notifications.  We do, however, want the store
    // to interrupted() to be visible to other threads before we execute unpark().
    OrderAccess::fence();
    ParkEvent * const slp = thread->_SleepEvent;
    if (slp != NULL) slp->unpark();
  }

  // For JSR166. Unpark even if interrupt status already was set
  if (thread->is_Java_thread())
    ((JavaThread*)thread)->parker()->unpark();

  ParkEvent * ev = thread->_ParkEvent;
  if (ev != NULL) ev->unpark();

}

bool os::is_interrupted(Thread* thread, bool clear_interrupted) {
  assert(Thread::current() == thread || Threads_lock->owned_by_self(),
    "possibility of dangling Thread pointer");

  OSThread* osthread = thread->osthread();

  bool interrupted = osthread->interrupted();

  if (interrupted && clear_interrupted) {
    osthread->set_interrupted(false);
    // consider thread->_SleepEvent->reset() ... optional optimization
  }

  return interrupted;
}

///////////////////////////////////////////////////////////////////////////////////
// signal handling (except suspend/resume)

// This routine may be used by user applications as a "hook" to catch signals.
// The user-defined signal handler must pass unrecognized signals to this
// routine, and if it returns true (non-zero), then the signal handler must
// return immediately. If the flag "abort_if_unrecognized" is true, then this
// routine will never retun false (zero), but instead will execute a VM panic
// routine kill the process.
//
// If this routine returns false, it is OK to call it again. This allows
// the user-defined signal handler to perform checks either before or after
// the VM performs its own checks. Naturally, the user code would be making
// a serious error if it tried to handle an exception (such as a null check
// or breakpoint) that the VM was generating for its own correct operation.
//
// This routine may recognize any of the following kinds of signals:
//   SIGBUS, SIGSEGV, SIGILL, SIGFPE, SIGQUIT, SIGPIPE, SIGXFSZ, SIGUSR1.
// It should be consulted by handlers for any of those signals.
//
// The caller of this routine must pass in the three arguments supplied
// to the function referred to in the "sa_sigaction" (not the "sa_handler")
// field of the structure passed to sigaction(). This routine assumes that
// the sa_flags field passed to sigaction() includes SA_SIGINFO and SA_RESTART.
//
// Note that the VM will print warnings if it detects conflicting signal
// handlers, unless invoked with the option "-XX:+AllowUserSignalHandlers".
//
extern "C" JNIEXPORT int
JVM_handle_aix_signal(int signo, siginfo_t* siginfo, void* ucontext, int abort_if_unrecognized);

// Set thread signal mask (for some reason on AIX sigthreadmask() seems
// to be the thing to call; documentation is not terribly clear about whether
// pthread_sigmask also works, and if it does, whether it does the same.
bool set_thread_signal_mask(int how, const sigset_t* set, sigset_t* oset) {
  const int rc = ::pthread_sigmask(how, set, oset);
  // return value semantics differ slightly for error case:
  // pthread_sigmask returns error number, sigthreadmask -1 and sets global errno
  // (so, pthread_sigmask is more theadsafe for error handling)
  // But success is always 0.
  return rc == 0 ? true : false;
}

// Function to unblock all signals which are, according
// to POSIX, typical program error signals. If they happen while being blocked,
// they typically will bring down the process immediately.
bool unblock_program_error_signals() {
  sigset_t set;
  ::sigemptyset(&set);
  ::sigaddset(&set, SIGILL);
  ::sigaddset(&set, SIGBUS);
  ::sigaddset(&set, SIGFPE);
  ::sigaddset(&set, SIGSEGV);
  return set_thread_signal_mask(SIG_UNBLOCK, &set, NULL);
}

// Renamed from 'signalHandler' to avoid collision with other shared libs.
void javaSignalHandler(int sig, siginfo_t* info, void* uc) {
  assert(info != NULL && uc != NULL, "it must be old kernel");

  // Never leave program error signals blocked;
  // on all our platforms they would bring down the process immediately when
  // getting raised while being blocked.
  unblock_program_error_signals();

  JVM_handle_aix_signal(sig, info, uc, true);
}


// This boolean allows users to forward their own non-matching signals
// to JVM_handle_aix_signal, harmlessly.
bool os::Aix::signal_handlers_are_installed = false;

// For signal-chaining
struct sigaction os::Aix::sigact[MAXSIGNUM];
unsigned int os::Aix::sigs = 0;
bool os::Aix::libjsig_is_loaded = false;
typedef struct sigaction *(*get_signal_t)(int);
get_signal_t os::Aix::get_signal_action = NULL;

struct sigaction* os::Aix::get_chained_signal_action(int sig) {
  struct sigaction *actp = NULL;

  if (libjsig_is_loaded) {
    // Retrieve the old signal handler from libjsig
    actp = (*get_signal_action)(sig);
  }
  if (actp == NULL) {
    // Retrieve the preinstalled signal handler from jvm
    actp = get_preinstalled_handler(sig);
  }

  return actp;
}

static bool call_chained_handler(struct sigaction *actp, int sig,
                                 siginfo_t *siginfo, void *context) {
  // Call the old signal handler
  if (actp->sa_handler == SIG_DFL) {
    // It's more reasonable to let jvm treat it as an unexpected exception
    // instead of taking the default action.
    return false;
  } else if (actp->sa_handler != SIG_IGN) {
    if ((actp->sa_flags & SA_NODEFER) == 0) {
      // automaticlly block the signal
      sigaddset(&(actp->sa_mask), sig);
    }

    sa_handler_t hand = NULL;
    sa_sigaction_t sa = NULL;
    bool siginfo_flag_set = (actp->sa_flags & SA_SIGINFO) != 0;
    // retrieve the chained handler
    if (siginfo_flag_set) {
      sa = actp->sa_sigaction;
    } else {
      hand = actp->sa_handler;
    }

    if ((actp->sa_flags & SA_RESETHAND) != 0) {
      actp->sa_handler = SIG_DFL;
    }

    // try to honor the signal mask
    sigset_t oset;
    pthread_sigmask(SIG_SETMASK, &(actp->sa_mask), &oset);

    // call into the chained handler
    if (siginfo_flag_set) {
      (*sa)(sig, siginfo, context);
    } else {
      (*hand)(sig);
    }

    // restore the signal mask
    pthread_sigmask(SIG_SETMASK, &oset, 0);
  }
  // Tell jvm's signal handler the signal is taken care of.
  return true;
}

bool os::Aix::chained_handler(int sig, siginfo_t* siginfo, void* context) {
  bool chained = false;
  // signal-chaining
  if (UseSignalChaining) {
    struct sigaction *actp = get_chained_signal_action(sig);
    if (actp != NULL) {
      chained = call_chained_handler(actp, sig, siginfo, context);
    }
  }
  return chained;
}

struct sigaction* os::Aix::get_preinstalled_handler(int sig) {
  if ((((unsigned int)1 << sig) & sigs) != 0) {
    return &sigact[sig];
  }
  return NULL;
}

void os::Aix::save_preinstalled_handler(int sig, struct sigaction& oldAct) {
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  sigact[sig] = oldAct;
  sigs |= (unsigned int)1 << sig;
}

// for diagnostic
int os::Aix::sigflags[MAXSIGNUM];

int os::Aix::get_our_sigflags(int sig) {
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  return sigflags[sig];
}

void os::Aix::set_our_sigflags(int sig, int flags) {
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  sigflags[sig] = flags;
}

void os::Aix::set_signal_handler(int sig, bool set_installed) {
  // Check for overwrite.
  struct sigaction oldAct;
  sigaction(sig, (struct sigaction*)NULL, &oldAct);

  void* oldhand = oldAct.sa_sigaction
    ? CAST_FROM_FN_PTR(void*, oldAct.sa_sigaction)
    : CAST_FROM_FN_PTR(void*, oldAct.sa_handler);
  // Renamed 'signalHandler' to avoid collision with other shared libs.
  if (oldhand != CAST_FROM_FN_PTR(void*, SIG_DFL) &&
      oldhand != CAST_FROM_FN_PTR(void*, SIG_IGN) &&
      oldhand != CAST_FROM_FN_PTR(void*, (sa_sigaction_t)javaSignalHandler)) {
    if (AllowUserSignalHandlers || !set_installed) {
      // Do not overwrite; user takes responsibility to forward to us.
      return;
    } else if (UseSignalChaining) {
      // save the old handler in jvm
      save_preinstalled_handler(sig, oldAct);
      // libjsig also interposes the sigaction() call below and saves the
      // old sigaction on it own.
    } else {
      fatal(err_msg("Encountered unexpected pre-existing sigaction handler "
                    "%#lx for signal %d.", (long)oldhand, sig));
    }
  }

  struct sigaction sigAct;
  sigfillset(&(sigAct.sa_mask));
  if (!set_installed) {
    sigAct.sa_handler = SIG_DFL;
    sigAct.sa_flags = SA_RESTART;
  } else {
    // Renamed 'signalHandler' to avoid collision with other shared libs.
    sigAct.sa_sigaction = javaSignalHandler;
    sigAct.sa_flags = SA_SIGINFO|SA_RESTART;
  }
  // Save flags, which are set by ours
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  sigflags[sig] = sigAct.sa_flags;

  int ret = sigaction(sig, &sigAct, &oldAct);
  assert(ret == 0, "check");

  void* oldhand2 = oldAct.sa_sigaction
                 ? CAST_FROM_FN_PTR(void*, oldAct.sa_sigaction)
                 : CAST_FROM_FN_PTR(void*, oldAct.sa_handler);
  assert(oldhand2 == oldhand, "no concurrent signal handler installation");
}

// install signal handlers for signals that HotSpot needs to
// handle in order to support Java-level exception handling.
void os::Aix::install_signal_handlers() {
  if (!signal_handlers_are_installed) {
    signal_handlers_are_installed = true;

    // signal-chaining
    typedef void (*signal_setting_t)();
    signal_setting_t begin_signal_setting = NULL;
    signal_setting_t end_signal_setting = NULL;
    begin_signal_setting = CAST_TO_FN_PTR(signal_setting_t,
                             dlsym(RTLD_DEFAULT, "JVM_begin_signal_setting"));
    if (begin_signal_setting != NULL) {
      end_signal_setting = CAST_TO_FN_PTR(signal_setting_t,
                             dlsym(RTLD_DEFAULT, "JVM_end_signal_setting"));
      get_signal_action = CAST_TO_FN_PTR(get_signal_t,
                            dlsym(RTLD_DEFAULT, "JVM_get_signal_action"));
      libjsig_is_loaded = true;
      assert(UseSignalChaining, "should enable signal-chaining");
    }
    if (libjsig_is_loaded) {
      // Tell libjsig jvm is setting signal handlers
      (*begin_signal_setting)();
    }

    set_signal_handler(SIGSEGV, true);
    set_signal_handler(SIGPIPE, true);
    set_signal_handler(SIGBUS, true);
    set_signal_handler(SIGILL, true);
    set_signal_handler(SIGFPE, true);
    set_signal_handler(SIGTRAP, true);
    set_signal_handler(SIGXFSZ, true);
    set_signal_handler(SIGDANGER, true);

    if (libjsig_is_loaded) {
      // Tell libjsig jvm finishes setting signal handlers
      (*end_signal_setting)();
    }

    // We don't activate signal checker if libjsig is in place, we trust ourselves
    // and if UserSignalHandler is installed all bets are off.
    // Log that signal checking is off only if -verbose:jni is specified.
    if (CheckJNICalls) {
      if (libjsig_is_loaded) {
        tty->print_cr("Info: libjsig is activated, all active signal checking is disabled");
        check_signals = false;
      }
      if (AllowUserSignalHandlers) {
        tty->print_cr("Info: AllowUserSignalHandlers is activated, all active signal checking is disabled");
        check_signals = false;
      }
      // need to initialize check_signal_done
      ::sigemptyset(&check_signal_done);
    }
  }
}

static const char* get_signal_handler_name(address handler,
                                           char* buf, int buflen) {
  int offset;
  bool found = os::dll_address_to_library_name(handler, buf, buflen, &offset);
  if (found) {
    // skip directory names
    const char *p1, *p2;
    p1 = buf;
    size_t len = strlen(os::file_separator());
    while ((p2 = strstr(p1, os::file_separator())) != NULL) p1 = p2 + len;
    // The way os::dll_address_to_library_name is implemented on Aix
    // right now, it always returns -1 for the offset which is not
    // terribly informative.
    // Will fix that. For now, omit the offset.
    jio_snprintf(buf, buflen, "%s", p1);
  } else {
    jio_snprintf(buf, buflen, PTR_FORMAT, handler);
  }
  return buf;
}

static void print_signal_handler(outputStream* st, int sig,
                                 char* buf, size_t buflen) {
  struct sigaction sa;
  sigaction(sig, NULL, &sa);

  st->print("%s: ", os::exception_name(sig, buf, buflen));

  address handler = (sa.sa_flags & SA_SIGINFO)
    ? CAST_FROM_FN_PTR(address, sa.sa_sigaction)
    : CAST_FROM_FN_PTR(address, sa.sa_handler);

  if (handler == CAST_FROM_FN_PTR(address, SIG_DFL)) {
    st->print("SIG_DFL");
  } else if (handler == CAST_FROM_FN_PTR(address, SIG_IGN)) {
    st->print("SIG_IGN");
  } else {
    st->print("[%s]", get_signal_handler_name(handler, buf, buflen));
  }

  // Print readable mask.
  st->print(", sa_mask[0]=");
  os::Posix::print_signal_set_short(st, &sa.sa_mask);

  address rh = VMError::get_resetted_sighandler(sig);
  // May be, handler was resetted by VMError?
  if (rh != NULL) {
    handler = rh;
    sa.sa_flags = VMError::get_resetted_sigflags(sig);
  }

  // Print textual representation of sa_flags.
  st->print(", sa_flags=");
  os::Posix::print_sa_flags(st, sa.sa_flags);

  // Check: is it our handler?
  if (handler == CAST_FROM_FN_PTR(address, (sa_sigaction_t)javaSignalHandler) ||
      handler == CAST_FROM_FN_PTR(address, (sa_sigaction_t)SR_handler)) {
    // It is our signal handler.
    // Check for flags, reset system-used one!
    if ((int)sa.sa_flags != os::Aix::get_our_sigflags(sig)) {
      st->print(", flags was changed from " PTR32_FORMAT ", consider using jsig library",
                os::Aix::get_our_sigflags(sig));
    }
  }
  st->cr();
}


#define DO_SIGNAL_CHECK(sig) \
  if (!sigismember(&check_signal_done, sig)) \
    os::Aix::check_signal_handler(sig)

// This method is a periodic task to check for misbehaving JNI applications
// under CheckJNI, we can add any periodic checks here

void os::run_periodic_checks() {

  if (check_signals == false) return;

  // SEGV and BUS if overridden could potentially prevent
  // generation of hs*.log in the event of a crash, debugging
  // such a case can be very challenging, so we absolutely
  // check the following for a good measure:
  DO_SIGNAL_CHECK(SIGSEGV);
  DO_SIGNAL_CHECK(SIGILL);
  DO_SIGNAL_CHECK(SIGFPE);
  DO_SIGNAL_CHECK(SIGBUS);
  DO_SIGNAL_CHECK(SIGPIPE);
  DO_SIGNAL_CHECK(SIGXFSZ);
  if (UseSIGTRAP) {
    DO_SIGNAL_CHECK(SIGTRAP);
  }
  DO_SIGNAL_CHECK(SIGDANGER);

  // ReduceSignalUsage allows the user to override these handlers
  // see comments at the very top and jvm_solaris.h
  if (!ReduceSignalUsage) {
    DO_SIGNAL_CHECK(SHUTDOWN1_SIGNAL);
    DO_SIGNAL_CHECK(SHUTDOWN2_SIGNAL);
    DO_SIGNAL_CHECK(SHUTDOWN3_SIGNAL);
    DO_SIGNAL_CHECK(BREAK_SIGNAL);
  }

  DO_SIGNAL_CHECK(SR_signum);
  DO_SIGNAL_CHECK(INTERRUPT_SIGNAL);
}

typedef int (*os_sigaction_t)(int, const struct sigaction *, struct sigaction *);

static os_sigaction_t os_sigaction = NULL;

void os::Aix::check_signal_handler(int sig) {
  char buf[O_BUFLEN];
  address jvmHandler = NULL;

  struct sigaction act;
  if (os_sigaction == NULL) {
    // only trust the default sigaction, in case it has been interposed
    os_sigaction = (os_sigaction_t)dlsym(RTLD_DEFAULT, "sigaction");
    if (os_sigaction == NULL) return;
  }

  os_sigaction(sig, (struct sigaction*)NULL, &act);

  address thisHandler = (act.sa_flags & SA_SIGINFO)
    ? CAST_FROM_FN_PTR(address, act.sa_sigaction)
    : CAST_FROM_FN_PTR(address, act.sa_handler);


  switch(sig) {
  case SIGSEGV:
  case SIGBUS:
  case SIGFPE:
  case SIGPIPE:
  case SIGILL:
  case SIGXFSZ:
    // Renamed 'signalHandler' to avoid collision with other shared libs.
    jvmHandler = CAST_FROM_FN_PTR(address, (sa_sigaction_t)javaSignalHandler);
    break;

  case SHUTDOWN1_SIGNAL:
  case SHUTDOWN2_SIGNAL:
  case SHUTDOWN3_SIGNAL:
  case BREAK_SIGNAL:
    jvmHandler = (address)user_handler();
    break;

  case INTERRUPT_SIGNAL:
    jvmHandler = CAST_FROM_FN_PTR(address, SIG_DFL);
    break;

  default:
    if (sig == SR_signum) {
      jvmHandler = CAST_FROM_FN_PTR(address, (sa_sigaction_t)SR_handler);
    } else {
      return;
    }
    break;
  }

  if (thisHandler != jvmHandler) {
    tty->print("Warning: %s handler ", exception_name(sig, buf, O_BUFLEN));
    tty->print("expected:%s", get_signal_handler_name(jvmHandler, buf, O_BUFLEN));
    tty->print_cr("  found:%s", get_signal_handler_name(thisHandler, buf, O_BUFLEN));
    // No need to check this sig any longer
    sigaddset(&check_signal_done, sig);
  } else if (os::Aix::get_our_sigflags(sig) != 0 && (int)act.sa_flags != os::Aix::get_our_sigflags(sig)) {
    tty->print("Warning: %s handler flags ", exception_name(sig, buf, O_BUFLEN));
    tty->print("expected:" PTR32_FORMAT, os::Aix::get_our_sigflags(sig));
    tty->print_cr("  found:" PTR32_FORMAT, act.sa_flags);
    // No need to check this sig any longer
    sigaddset(&check_signal_done, sig);
  }

  // Dump all the signal
  if (sigismember(&check_signal_done, sig)) {
    print_signal_handlers(tty, buf, O_BUFLEN);
  }
}

extern bool signal_name(int signo, char* buf, size_t len);

const char* os::exception_name(int exception_code, char* buf, size_t size) {
  if (0 < exception_code && exception_code <= SIGRTMAX) {
    // signal
    if (!signal_name(exception_code, buf, size)) {
      jio_snprintf(buf, size, "SIG%d", exception_code);
    }
    return buf;
  } else {
    return NULL;
  }
}

// To install functions for atexit system call
extern "C" {
  static void perfMemory_exit_helper() {
    perfMemory_exit();
  }
}

// This is called _before_ the most of global arguments have been parsed.
void os::init(void) {
  // This is basic, we want to know if that ever changes.
  // (shared memory boundary is supposed to be a 256M aligned)
  assert(SHMLBA == ((uint64_t)0x10000000ULL)/*256M*/, "unexpected");

  // First off, we need to know whether we run on AIX or PASE, and
  // the OS level we run on.
  os::Aix::initialize_os_info();

  // Scan environment (SPEC1170 behaviour, etc)
  os::Aix::scan_environment();

  // Check which pages are supported by AIX.
  os::Aix::query_multipage_support();

  // Next, we need to initialize libo4 and libperfstat libraries.
  if (os::Aix::on_pase()) {
    os::Aix::initialize_libo4();
  } else {
    os::Aix::initialize_libperfstat();
  }

  // Reset the perfstat information provided by ODM.
  if (os::Aix::on_aix()) {
    libperfstat::perfstat_reset();
  }

  // Now initialze basic system properties. Note that for some of the values we
  // need libperfstat etc.
  os::Aix::initialize_system_info();

  // Initialize large page support.
  if (UseLargePages) {
    os::large_page_init();
    if (!UseLargePages) {
      // initialize os::_page_sizes
      _page_sizes[0] = Aix::page_size();
      _page_sizes[1] = 0;
      if (Verbose) {
        fprintf(stderr, "Large Page initialization failed: setting UseLargePages=0.\n");
      }
    }
  } else {
    // initialize os::_page_sizes
    _page_sizes[0] = Aix::page_size();
    _page_sizes[1] = 0;
  }

  // debug trace
  if (Verbose) {
    fprintf(stderr, "os::vm_page_size 0x%llX\n", os::vm_page_size());
    fprintf(stderr, "os::large_page_size 0x%llX\n", os::large_page_size());
    fprintf(stderr, "os::_page_sizes = ( ");
    for (int i = 0; _page_sizes[i]; i ++) {
      fprintf(stderr, " %s ", describe_pagesize(_page_sizes[i]));
    }
    fprintf(stderr, ")\n");
  }

  _initial_pid = getpid();

  clock_tics_per_sec = sysconf(_SC_CLK_TCK);

  init_random(1234567);

  ThreadCritical::initialize();

  // Main_thread points to the aboriginal thread.
  Aix::_main_thread = pthread_self();

  initial_time_count = os::elapsed_counter();
  pthread_mutex_init(&dl_mutex, NULL);
}

// this is called _after_ the global arguments have been parsed
jint os::init_2(void) {

  if (Verbose) {
    fprintf(stderr, "processor count: %d\n", os::_processor_count);
    fprintf(stderr, "physical memory: %lu\n", Aix::_physical_memory);
  }

  // initially build up the loaded dll map
  LoadedLibraries::reload();

  const int page_size = Aix::page_size();
  const int map_size = page_size;

  address map_address = (address) MAP_FAILED;
  const int prot  = PROT_READ;
  const int flags = MAP_PRIVATE|MAP_ANONYMOUS;

  // use optimized addresses for the polling page,
  // e.g. map it to a special 32-bit address.
  if (OptimizePollingPageLocation) {
    // architecture-specific list of address wishes:
    address address_wishes[] = {
      // AIX: addresses lower than 0x30000000 don't seem to work on AIX.
      // PPC64: all address wishes are non-negative 32 bit values where
      // the lower 16 bits are all zero. we can load these addresses
      // with a single ppc_lis instruction.
      (address) 0x30000000, (address) 0x31000000,
      (address) 0x32000000, (address) 0x33000000,
      (address) 0x40000000, (address) 0x41000000,
      (address) 0x42000000, (address) 0x43000000,
      (address) 0x50000000, (address) 0x51000000,
      (address) 0x52000000, (address) 0x53000000,
      (address) 0x60000000, (address) 0x61000000,
      (address) 0x62000000, (address) 0x63000000
    };
    int address_wishes_length = sizeof(address_wishes)/sizeof(address);

    // iterate over the list of address wishes:
    for (int i=0; i<address_wishes_length; i++) {
      // try to map with current address wish.
      // AIX: AIX needs MAP_FIXED if we provide an address and mmap will
      // fail if the address is already mapped.
      map_address = (address) ::mmap(address_wishes[i] - (ssize_t)page_size,
                                     map_size, prot,
                                     flags | MAP_FIXED,
                                     -1, 0);
      if (Verbose) {
        fprintf(stderr, "SafePoint Polling Page address: %p (wish) => %p\n",
                address_wishes[i], map_address + (ssize_t)page_size);
      }

      if (map_address + (ssize_t)page_size == address_wishes[i]) {
        // map succeeded and map_address is at wished address, exit loop.
        break;
      }

      if (map_address != (address) MAP_FAILED) {
        // map succeeded, but polling_page is not at wished address, unmap and continue.
        ::munmap(map_address, map_size);
        map_address = (address) MAP_FAILED;
      }
      // map failed, continue loop.
    }
  } // end OptimizePollingPageLocation

  if (map_address == (address) MAP_FAILED) {
    map_address = (address) ::mmap(NULL, map_size, prot, flags, -1, 0);
  }
  guarantee(map_address != MAP_FAILED, "os::init_2: failed to allocate polling page");
  os::set_polling_page(map_address);

  if (!UseMembar) {
    address mem_serialize_page = (address) ::mmap(NULL, Aix::page_size(), PROT_READ | PROT_WRITE, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0);
    guarantee(mem_serialize_page != NULL, "mmap Failed for memory serialize page");
    os::set_memory_serialize_page(mem_serialize_page);

#ifndef PRODUCT
    if (Verbose && PrintMiscellaneous)
      tty->print("[Memory Serialize Page address: " INTPTR_FORMAT "]\n", (intptr_t)mem_serialize_page);
#endif
  }

  // initialize suspend/resume support - must do this before signal_sets_init()
  if (SR_initialize() != 0) {
    perror("SR_initialize failed");
    return JNI_ERR;
  }

  Aix::signal_sets_init();
  Aix::install_signal_handlers();

  // Check minimum allowable stack size for thread creation and to initialize
  // the java system classes, including StackOverflowError - depends on page
  // size. Add a page for compiler2 recursion in main thread.
  // Add in 2*BytesPerWord times page size to account for VM stack during
  // class initialization depending on 32 or 64 bit VM.
  os::Aix::min_stack_allowed = MAX2(os::Aix::min_stack_allowed,
            (size_t)(StackYellowPages+StackRedPages+StackShadowPages +
                     2*BytesPerWord COMPILER2_PRESENT(+1)) * Aix::page_size());

  size_t threadStackSizeInBytes = ThreadStackSize * K;
  if (threadStackSizeInBytes != 0 &&
      threadStackSizeInBytes < os::Aix::min_stack_allowed) {
        tty->print_cr("\nThe stack size specified is too small, "
                      "Specify at least %dk",
                      os::Aix::min_stack_allowed / K);
        return JNI_ERR;
  }

  // Make the stack size a multiple of the page size so that
  // the yellow/red zones can be guarded.
  // note that this can be 0, if no default stacksize was set
  JavaThread::set_stack_size_at_create(round_to(threadStackSizeInBytes, vm_page_size()));

  Aix::libpthread_init();

  if (MaxFDLimit) {
    // set the number of file descriptors to max. print out error
    // if getrlimit/setrlimit fails but continue regardless.
    struct rlimit nbr_files;
    int status = getrlimit(RLIMIT_NOFILE, &nbr_files);
    if (status != 0) {
      if (PrintMiscellaneous && (Verbose || WizardMode))
        perror("os::init_2 getrlimit failed");
    } else {
      nbr_files.rlim_cur = nbr_files.rlim_max;
      status = setrlimit(RLIMIT_NOFILE, &nbr_files);
      if (status != 0) {
        if (PrintMiscellaneous && (Verbose || WizardMode))
          perror("os::init_2 setrlimit failed");
      }
    }
  }

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

  return JNI_OK;
}

// this is called at the end of vm_initialization
void os::init_3(void) {
  return;
}

// Mark the polling page as unreadable
void os::make_polling_page_unreadable(void) {
  if (!guard_memory((char*)_polling_page, Aix::page_size())) {
    fatal("Could not disable polling page");
  }
};

// Mark the polling page as readable
void os::make_polling_page_readable(void) {
  // Changed according to os_linux.cpp.
  if (!checked_mprotect((char *)_polling_page, Aix::page_size(), PROT_READ)) {
    fatal(err_msg("Could not enable polling page at " PTR_FORMAT, _polling_page));
  }
};

int os::active_processor_count() {
  int online_cpus = ::sysconf(_SC_NPROCESSORS_ONLN);
  assert(online_cpus > 0 && online_cpus <= processor_count(), "sanity check");
  return online_cpus;
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

void os::SuspendedThreadTask::internal_do_task() {
  if (do_suspend(_thread->osthread())) {
    SuspendedThreadTaskContext context(_thread, _thread->osthread()->ucontext());
    do_task(context);
    do_resume(_thread->osthread());
  }
}

class PcFetcher : public os::SuspendedThreadTask {
public:
  PcFetcher(Thread* thread) : os::SuspendedThreadTask(thread) {}
  ExtendedPC result();
protected:
  void do_task(const os::SuspendedThreadTaskContext& context);
private:
  ExtendedPC _epc;
};

ExtendedPC PcFetcher::result() {
  guarantee(is_done(), "task is not done yet.");
  return _epc;
}

void PcFetcher::do_task(const os::SuspendedThreadTaskContext& context) {
  Thread* thread = context.thread();
  OSThread* osthread = thread->osthread();
  if (osthread->ucontext() != NULL) {
    _epc = os::Aix::ucontext_get_pc((ucontext_t *) context.ucontext());
  } else {
    // NULL context is unexpected, double-check this is the VMThread.
    guarantee(thread->is_VM_thread(), "can only be called for VMThread");
  }
}

// Suspends the target using the signal mechanism and then grabs the PC before
// resuming the target. Used by the flat-profiler only
ExtendedPC os::get_thread_pc(Thread* thread) {
  // Make sure that it is called by the watcher for the VMThread.
  assert(Thread::current()->is_Watcher_thread(), "Must be watcher");
  assert(thread->is_VM_thread(), "Can only be called for VMThread");

  PcFetcher fetcher(thread);
  fetcher.run();
  return fetcher.result();
}

// Not neede on Aix.
// int os::Aix::safe_cond_timedwait(pthread_cond_t *_cond, pthread_mutex_t *_mutex, const struct timespec *_abstime) {
// }

////////////////////////////////////////////////////////////////////////////////
// debug support

static address same_page(address x, address y) {
  intptr_t page_bits = -os::vm_page_size();
  if ((intptr_t(x) & page_bits) == (intptr_t(y) & page_bits))
    return x;
  else if (x > y)
    return (address)(intptr_t(y) | ~page_bits) + 1;
  else
    return (address)(intptr_t(y) & page_bits);
}

bool os::find(address addr, outputStream* st) {

  st->print(PTR_FORMAT ": ", addr);

  const LoadedLibraryModule* lib = LoadedLibraries::find_for_text_address(addr);
  if (lib) {
    lib->print(st);
    return true;
  } else {
    lib = LoadedLibraries::find_for_data_address(addr);
    if (lib) {
      lib->print(st);
      return true;
    } else {
      st->print_cr("(outside any module)");
    }
  }

  return false;
}

////////////////////////////////////////////////////////////////////////////////
// misc

// This does not do anything on Aix. This is basically a hook for being
// able to use structured exception handling (thread-local exception filters)
// on, e.g., Win32.
void
os::os_exception_wrapper(java_call_t f, JavaValue* value, methodHandle* method,
                         JavaCallArguments* args, Thread* thread) {
  f(value, method, args, thread);
}

void os::print_statistics() {
}

int os::message_box(const char* title, const char* message) {
  int i;
  fdStream err(defaultStream::error_fd());
  for (i = 0; i < 78; i++) err.print_raw("=");
  err.cr();
  err.print_raw_cr(title);
  for (i = 0; i < 78; i++) err.print_raw("-");
  err.cr();
  err.print_raw_cr(message);
  for (i = 0; i < 78; i++) err.print_raw("=");
  err.cr();

  char buf[16];
  // Prevent process from exiting upon "read error" without consuming all CPU
  while (::read(0, buf, sizeof(buf)) <= 0) { ::sleep(100); }

  return buf[0] == 'y' || buf[0] == 'Y';
}

int os::stat(const char *path, struct stat *sbuf) {
  char pathbuf[MAX_PATH];
  if (strlen(path) > MAX_PATH - 1) {
    errno = ENAMETOOLONG;
    return -1;
  }
  os::native_path(strcpy(pathbuf, path));
  return ::stat(pathbuf, sbuf);
}

bool os::check_heap(bool force) {
  return true;
}

// int local_vsnprintf(char* buf, size_t count, const char* format, va_list args) {
//   return ::vsnprintf(buf, count, format, args);
// }

// Is a (classpath) directory empty?
bool os::dir_is_empty(const char* path) {
  DIR *dir = NULL;
  struct dirent *ptr;

  dir = opendir(path);
  if (dir == NULL) return true;

  /* Scan the directory */
  bool result = true;
  char buf[sizeof(struct dirent) + MAX_PATH];
  while (result && (ptr = ::readdir(dir)) != NULL) {
    if (strcmp(ptr->d_name, ".") != 0 && strcmp(ptr->d_name, "..") != 0) {
      result = false;
    }
  }
  closedir(dir);
  return result;
}

// This code originates from JDK's sysOpen and open64_w
// from src/solaris/hpi/src/system_md.c

#ifndef O_DELETE
#define O_DELETE 0x10000
#endif

// Open a file. Unlink the file immediately after open returns
// if the specified oflag has the O_DELETE flag set.
// O_DELETE is used only in j2se/src/share/native/java/util/zip/ZipFile.c

int os::open(const char *path, int oflag, int mode) {

  if (strlen(path) > MAX_PATH - 1) {
    errno = ENAMETOOLONG;
    return -1;
  }
  int fd;
  int o_delete = (oflag & O_DELETE);
  oflag = oflag & ~O_DELETE;

  fd = ::open64(path, oflag, mode);
  if (fd == -1) return -1;

  // If the open succeeded, the file might still be a directory.
  {
    struct stat64 buf64;
    int ret = ::fstat64(fd, &buf64);
    int st_mode = buf64.st_mode;

    if (ret != -1) {
      if ((st_mode & S_IFMT) == S_IFDIR) {
        errno = EISDIR;
        ::close(fd);
        return -1;
      }
    } else {
      ::close(fd);
      return -1;
    }
  }

  // All file descriptors that are opened in the JVM and not
  // specifically destined for a subprocess should have the
  // close-on-exec flag set. If we don't set it, then careless 3rd
  // party native code might fork and exec without closing all
  // appropriate file descriptors (e.g. as we do in closeDescriptors in
  // UNIXProcess.c), and this in turn might:
  //
  // - cause end-of-file to fail to be detected on some file
  //   descriptors, resulting in mysterious hangs, or
  //
  // - might cause an fopen in the subprocess to fail on a system
  //   suffering from bug 1085341.
  //
  // (Yes, the default setting of the close-on-exec flag is a Unix
  // design flaw.)
  //
  // See:
  // 1085341: 32-bit stdio routines should support file descriptors >255
  // 4843136: (process) pipe file descriptor from Runtime.exec not being closed
  // 6339493: (process) Runtime.exec does not close all file descriptors on Solaris 9
#ifdef FD_CLOEXEC
  {
    int flags = ::fcntl(fd, F_GETFD);
    if (flags != -1)
      ::fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
  }
#endif

  if (o_delete != 0) {
    ::unlink(path);
  }
  return fd;
}


// create binary file, rewriting existing file if required
int os::create_binary_file(const char* path, bool rewrite_existing) {
  int oflags = O_WRONLY | O_CREAT;
  if (!rewrite_existing) {
    oflags |= O_EXCL;
  }
  return ::open64(path, oflags, S_IREAD | S_IWRITE);
}

// return current position of file pointer
jlong os::current_file_offset(int fd) {
  return (jlong)::lseek64(fd, (off64_t)0, SEEK_CUR);
}

// move file pointer to the specified offset
jlong os::seek_to_file_offset(int fd, jlong offset) {
  return (jlong)::lseek64(fd, (off64_t)offset, SEEK_SET);
}

// This code originates from JDK's sysAvailable
// from src/solaris/hpi/src/native_threads/src/sys_api_td.c

int os::available(int fd, jlong *bytes) {
  jlong cur, end;
  int mode;
  struct stat64 buf64;

  if (::fstat64(fd, &buf64) >= 0) {
    mode = buf64.st_mode;
    if (S_ISCHR(mode) || S_ISFIFO(mode) || S_ISSOCK(mode)) {
      // XXX: is the following call interruptible? If so, this might
      // need to go through the INTERRUPT_IO() wrapper as for other
      // blocking, interruptible calls in this file.
      int n;
      if (::ioctl(fd, FIONREAD, &n) >= 0) {
        *bytes = n;
        return 1;
      }
    }
  }
  if ((cur = ::lseek64(fd, 0L, SEEK_CUR)) == -1) {
    return 0;
  } else if ((end = ::lseek64(fd, 0L, SEEK_END)) == -1) {
    return 0;
  } else if (::lseek64(fd, cur, SEEK_SET) == -1) {
    return 0;
  }
  *bytes = end - cur;
  return 1;
}

int os::socket_available(int fd, jint *pbytes) {
  // Linux doc says EINTR not returned, unlike Solaris
  int ret = ::ioctl(fd, FIONREAD, pbytes);

  //%% note ioctl can return 0 when successful, JVM_SocketAvailable
  // is expected to return 0 on failure and 1 on success to the jdk.
  return (ret < 0) ? 0 : 1;
}

// Map a block of memory.
char* os::pd_map_memory(int fd, const char* file_name, size_t file_offset,
                        char *addr, size_t bytes, bool read_only,
                        bool allow_exec) {
  Unimplemented();
  return NULL;
}


// Remap a block of memory.
char* os::pd_remap_memory(int fd, const char* file_name, size_t file_offset,
                          char *addr, size_t bytes, bool read_only,
                          bool allow_exec) {
  // same as map_memory() on this OS
  return os::map_memory(fd, file_name, file_offset, addr, bytes, read_only,
                        allow_exec);
}

// Unmap a block of memory.
bool os::pd_unmap_memory(char* addr, size_t bytes) {
  return munmap(addr, bytes) == 0;
}

// current_thread_cpu_time(bool) and thread_cpu_time(Thread*, bool)
// are used by JVM M&M and JVMTI to get user+sys or user CPU time
// of a thread.
//
// current_thread_cpu_time() and thread_cpu_time(Thread*) returns
// the fast estimate available on the platform.

jlong os::current_thread_cpu_time() {
  // return user + sys since the cost is the same
  const jlong n = os::thread_cpu_time(Thread::current(), true /* user + sys */);
  assert(n >= 0, "negative CPU time");
  return n;
}

jlong os::thread_cpu_time(Thread* thread) {
  // consistent with what current_thread_cpu_time() returns
  const jlong n = os::thread_cpu_time(thread, true /* user + sys */);
  assert(n >= 0, "negative CPU time");
  return n;
}

jlong os::current_thread_cpu_time(bool user_sys_cpu_time) {
  const jlong n = os::thread_cpu_time(Thread::current(), user_sys_cpu_time);
  assert(n >= 0, "negative CPU time");
  return n;
}

static bool thread_cpu_time_unchecked(Thread* thread, jlong* p_sys_time, jlong* p_user_time) {
  bool error = false;

  jlong sys_time = 0;
  jlong user_time = 0;

  // reimplemented using getthrds64().
  //
  // goes like this:
  // For the thread in question, get the kernel thread id. Then get the
  // kernel thread statistics using that id.
  //
  // This only works of course when no pthread scheduling is used,
  // ie there is a 1:1 relationship to kernel threads.
  // On AIX, see AIXTHREAD_SCOPE variable.

  pthread_t pthtid = thread->osthread()->pthread_id();

  // retrieve kernel thread id for the pthread:
  tid64_t tid = 0;
  struct __pthrdsinfo pinfo;
  // I just love those otherworldly IBM APIs which force me to hand down
  // dummy buffers for stuff I dont care for...
  char dummy[1];
  int dummy_size = sizeof(dummy);
  if (pthread_getthrds_np(&pthtid, PTHRDSINFO_QUERY_TID, &pinfo, sizeof(pinfo),
                          dummy, &dummy_size) == 0) {
    tid = pinfo.__pi_tid;
  } else {
    tty->print_cr("pthread_getthrds_np failed.");
    error = true;
  }

  // retrieve kernel timing info for that kernel thread
  if (!error) {
    struct thrdentry64 thrdentry;
    if (getthrds64(getpid(), &thrdentry, sizeof(thrdentry), &tid, 1) == 1) {
      sys_time = thrdentry.ti_ru.ru_stime.tv_sec * 1000000000LL + thrdentry.ti_ru.ru_stime.tv_usec * 1000LL;
      user_time = thrdentry.ti_ru.ru_utime.tv_sec * 1000000000LL + thrdentry.ti_ru.ru_utime.tv_usec * 1000LL;
    } else {
      tty->print_cr("pthread_getthrds_np failed.");
      error = true;
    }
  }

  if (p_sys_time) {
    *p_sys_time = sys_time;
  }

  if (p_user_time) {
    *p_user_time = user_time;
  }

  if (error) {
    return false;
  }

  return true;
}

jlong os::thread_cpu_time(Thread *thread, bool user_sys_cpu_time) {
  jlong sys_time;
  jlong user_time;

  if (!thread_cpu_time_unchecked(thread, &sys_time, &user_time)) {
    return -1;
  }

  return user_sys_cpu_time ? sys_time + user_time : user_time;
}

void os::current_thread_cpu_time_info(jvmtiTimerInfo *info_ptr) {
  info_ptr->max_value = ALL_64_BITS;       // will not wrap in less than 64 bits
  info_ptr->may_skip_backward = false;     // elapsed time not wall time
  info_ptr->may_skip_forward = false;      // elapsed time not wall time
  info_ptr->kind = JVMTI_TIMER_TOTAL_CPU;  // user+system time is returned
}

void os::thread_cpu_time_info(jvmtiTimerInfo *info_ptr) {
  info_ptr->max_value = ALL_64_BITS;       // will not wrap in less than 64 bits
  info_ptr->may_skip_backward = false;     // elapsed time not wall time
  info_ptr->may_skip_forward = false;      // elapsed time not wall time
  info_ptr->kind = JVMTI_TIMER_TOTAL_CPU;  // user+system time is returned
}

bool os::is_thread_cpu_time_supported() {
  return true;
}

// System loadavg support. Returns -1 if load average cannot be obtained.
// For now just return the system wide load average (no processor sets).
int os::loadavg(double values[], int nelem) {

  // Implemented using libperfstat on AIX.

  guarantee(nelem >= 0 && nelem <= 3, "argument error");
  guarantee(values, "argument error");

  if (os::Aix::on_pase()) {
    Unimplemented();
    return -1;
  } else {
    // AIX: use libperfstat
    //
    // See also:
    // http://publib.boulder.ibm.com/infocenter/pseries/v5r3/index.jsp?topic=/com.ibm.aix.basetechref/doc/basetrf1/perfstat_cputot.htm
    // /usr/include/libperfstat.h:

    // Use the already AIX version independent get_cpuinfo.
    os::Aix::cpuinfo_t ci;
    if (os::Aix::get_cpuinfo(&ci)) {
      for (int i = 0; i < nelem; i++) {
        values[i] = ci.loadavg[i];
      }
    } else {
      return -1;
    }
    return nelem;
  }
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
      (void)::poll(NULL, 0, 100);
    }
  } else {
    jio_fprintf(stderr,
      "Could not open pause file '%s', continuing immediately.\n", filename);
  }
}

bool os::Aix::is_primordial_thread() {
  if (pthread_self() == (pthread_t)1) {
    return true;
  } else {
    return false;
  }
}

// OS recognitions (PASE/AIX, OS level) call this before calling any
// one of Aix::on_pase(), Aix::os_version() static
void os::Aix::initialize_os_info() {

  assert(_on_pase == -1 && _os_version == -1, "already called.");

  struct utsname uts;
  memset(&uts, 0, sizeof(uts));
  strcpy(uts.sysname, "?");
  if (::uname(&uts) == -1) {
    fprintf(stderr, "uname failed (%d)\n", errno);
    guarantee(0, "Could not determine whether we run on AIX or PASE");
  } else {
    if (Verbose) {
      fprintf(stderr,"uname says: sysname \"%s\" version \"%s\" release \"%s\" "
              "node \"%s\" machine \"%s\"\n",
              uts.sysname, uts.version, uts.release, uts.nodename, uts.machine);
    }
    const int major = atoi(uts.version);
    assert(major > 0, "invalid OS version");
    const int minor = atoi(uts.release);
    assert(minor > 0, "invalid OS release");
    _os_version = (major << 8) | minor;
    if (strcmp(uts.sysname, "OS400") == 0) {
      Unimplemented();
    } else if (strcmp(uts.sysname, "AIX") == 0) {
      // We run on AIX. We do not support versions older than AIX 5.3.
      _on_pase = 0;
      if (_os_version < 0x0503) {
        fprintf(stderr, "AIX release older than AIX 5.3 not supported.\n");
        assert(false, "AIX release too old.");
      } else {
        if (Verbose) {
          fprintf(stderr, "We run on AIX %d.%d\n", major, minor);
        }
      }
    } else {
      assert(false, "unknown OS");
    }
  }

  guarantee(_on_pase != -1 && _os_version, "Could not determine AIX/OS400 release");

} // end: os::Aix::initialize_os_info()

// Scan environment for important settings which might effect the VM.
// Trace out settings. Warn about invalid settings and/or correct them.
//
// Must run after os::Aix::initialue_os_info().
void os::Aix::scan_environment() {

  char* p;
  int rc;

  // Warn explicity if EXTSHM=ON is used. That switch changes how
  // System V shared memory behaves. One effect is that page size of
  // shared memory cannot be change dynamically, effectivly preventing
  // large pages from working.
  // This switch was needed on AIX 32bit, but on AIX 64bit the general
  // recommendation is (in OSS notes) to switch it off.
  p = ::getenv("EXTSHM");
  if (Verbose) {
    fprintf(stderr, "EXTSHM=%s.\n", p ? p : "<unset>");
  }
  if (p && strcmp(p, "ON") == 0) {
    fprintf(stderr, "Unsupported setting: EXTSHM=ON. Large Page support will be disabled.\n");
    _extshm = 1;
  } else {
    _extshm = 0;
  }

  // SPEC1170 behaviour: will change the behaviour of a number of POSIX APIs.
  // Not tested, not supported.
  //
  // Note that it might be worth the trouble to test and to require it, if only to
  // get useful return codes for mprotect.
  //
  // Note: Setting XPG_SUS_ENV in the process is too late. Must be set earlier (before
  // exec() ? before loading the libjvm ? ....)
  p = ::getenv("XPG_SUS_ENV");
  if (Verbose) {
    fprintf(stderr, "XPG_SUS_ENV=%s.\n", p ? p : "<unset>");
  }
  if (p && strcmp(p, "ON") == 0) {
    _xpg_sus_mode = 1;
    fprintf(stderr, "Unsupported setting: XPG_SUS_ENV=ON\n");
    // This is not supported. Worst of all, it changes behaviour of mmap MAP_FIXED to
    // clobber address ranges. If we ever want to support that, we have to do some
    // testing first.
    guarantee(false, "XPG_SUS_ENV=ON not supported");
  } else {
    _xpg_sus_mode = 0;
  }

  // Switch off AIX internal (pthread) guard pages. This has
  // immediate effect for any pthread_create calls which follow.
  p = ::getenv("AIXTHREAD_GUARDPAGES");
  if (Verbose) {
    fprintf(stderr, "AIXTHREAD_GUARDPAGES=%s.\n", p ? p : "<unset>");
    fprintf(stderr, "setting AIXTHREAD_GUARDPAGES=0.\n");
  }
  rc = ::putenv("AIXTHREAD_GUARDPAGES=0");
  guarantee(rc == 0, "");

} // end: os::Aix::scan_environment()

// PASE: initialize the libo4 library (AS400 PASE porting library).
void os::Aix::initialize_libo4() {
  Unimplemented();
}

// AIX: initialize the libperfstat library (we load this dynamically
// because it is only available on AIX.
void os::Aix::initialize_libperfstat() {

  assert(os::Aix::on_aix(), "AIX only");

  if (!libperfstat::init()) {
    fprintf(stderr, "libperfstat initialization failed.\n");
    assert(false, "libperfstat initialization failed");
  } else {
    if (Verbose) {
      fprintf(stderr, "libperfstat initialized.\n");
    }
  }
} // end: os::Aix::initialize_libperfstat

/////////////////////////////////////////////////////////////////////////////
// thread stack

// function to query the current stack size using pthread_getthrds_np
//
// ! do not change anything here unless you know what you are doing !
static void query_stack_dimensions(address* p_stack_base, size_t* p_stack_size) {

  // This only works when invoked on a pthread. As we agreed not to use
  // primordial threads anyway, I assert here
  guarantee(!os::Aix::is_primordial_thread(), "not allowed on the primordial thread");

  // information about this api can be found (a) in the pthread.h header and
  // (b) in http://publib.boulder.ibm.com/infocenter/pseries/v5r3/index.jsp?topic=/com.ibm.aix.basetechref/doc/basetrf1/pthread_getthrds_np.htm
  //
  // The use of this API to find out the current stack is kind of undefined.
  // But after a lot of tries and asking IBM about it, I concluded that it is safe
  // enough for cases where I let the pthread library create its stacks. For cases
  // where I create an own stack and pass this to pthread_create, it seems not to
  // work (the returned stack size in that case is 0).

  pthread_t tid = pthread_self();
  struct __pthrdsinfo pinfo;
  char dummy[1]; // we only need this to satisfy the api and to not get E
  int dummy_size = sizeof(dummy);

  memset(&pinfo, 0, sizeof(pinfo));

  const int rc = pthread_getthrds_np (&tid, PTHRDSINFO_QUERY_ALL, &pinfo,
                                      sizeof(pinfo), dummy, &dummy_size);

  if (rc != 0) {
    fprintf(stderr, "pthread_getthrds_np failed (%d)\n", rc);
    guarantee(0, "pthread_getthrds_np failed");
  }

  guarantee(pinfo.__pi_stackend, "returned stack base invalid");

  // the following can happen when invoking pthread_getthrds_np on a pthread running on a user provided stack
  // (when handing down a stack to pthread create, see pthread_attr_setstackaddr).
  // Not sure what to do here - I feel inclined to forbid this use case completely.
  guarantee(pinfo.__pi_stacksize, "returned stack size invalid");

  // On AIX, stacks are not necessarily page aligned so round the base and size accordingly
  if (p_stack_base) {
    (*p_stack_base) = (address) align_size_up((intptr_t)pinfo.__pi_stackend, os::Aix::stack_page_size());
  }

  if (p_stack_size) {
    (*p_stack_size) = pinfo.__pi_stacksize - os::Aix::stack_page_size();
  }

#ifndef PRODUCT
  if (Verbose) {
    fprintf(stderr,
            "query_stack_dimensions() -> real stack_base=" INTPTR_FORMAT ", real stack_addr=" INTPTR_FORMAT
            ", real stack_size=" INTPTR_FORMAT
            ", stack_base=" INTPTR_FORMAT ", stack_size=" INTPTR_FORMAT "\n",
            (intptr_t)pinfo.__pi_stackend, (intptr_t)pinfo.__pi_stackaddr, pinfo.__pi_stacksize,
            (intptr_t)align_size_up((intptr_t)pinfo.__pi_stackend, os::Aix::stack_page_size()),
            pinfo.__pi_stacksize - os::Aix::stack_page_size());
  }
#endif

} // end query_stack_dimensions

// get the current stack base from the OS (actually, the pthread library)
address os::current_stack_base() {
  address p;
  query_stack_dimensions(&p, 0);
  return p;
}

// get the current stack size from the OS (actually, the pthread library)
size_t os::current_stack_size() {
  size_t s;
  query_stack_dimensions(0, &s);
  return s;
}

// Refer to the comments in os_solaris.cpp park-unpark.
//
// Beware -- Some versions of NPTL embody a flaw where pthread_cond_timedwait() can
// hang indefinitely. For instance NPTL 0.60 on 2.4.21-4ELsmp is vulnerable.
// For specifics regarding the bug see GLIBC BUGID 261237 :
//    http://www.mail-archive.com/debian-glibc@lists.debian.org/msg10837.html.
// Briefly, pthread_cond_timedwait() calls with an expiry time that's not in the future
// will either hang or corrupt the condvar, resulting in subsequent hangs if the condvar
// is used. (The simple C test-case provided in the GLIBC bug report manifests the
// hang). The JVM is vulernable via sleep(), Object.wait(timo), LockSupport.parkNanos()
// and monitorenter when we're using 1-0 locking. All those operations may result in
// calls to pthread_cond_timedwait(). Using LD_ASSUME_KERNEL to use an older version
// of libpthread avoids the problem, but isn't practical.
//
// Possible remedies:
//
// 1.   Establish a minimum relative wait time. 50 to 100 msecs seems to work.
//      This is palliative and probabilistic, however. If the thread is preempted
//      between the call to compute_abstime() and pthread_cond_timedwait(), more
//      than the minimum period may have passed, and the abstime may be stale (in the
//      past) resultin in a hang. Using this technique reduces the odds of a hang
//      but the JVM is still vulnerable, particularly on heavily loaded systems.
//
// 2.   Modify park-unpark to use per-thread (per ParkEvent) pipe-pairs instead
//      of the usual flag-condvar-mutex idiom. The write side of the pipe is set
//      NDELAY. unpark() reduces to write(), park() reduces to read() and park(timo)
//      reduces to poll()+read(). This works well, but consumes 2 FDs per extant
//      thread.
//
// 3.   Embargo pthread_cond_timedwait() and implement a native "chron" thread
//      that manages timeouts. We'd emulate pthread_cond_timedwait() by enqueuing
//      a timeout request to the chron thread and then blocking via pthread_cond_wait().
//      This also works well. In fact it avoids kernel-level scalability impediments
//      on certain platforms that don't handle lots of active pthread_cond_timedwait()
//      timers in a graceful fashion.
//
// 4.   When the abstime value is in the past it appears that control returns
//      correctly from pthread_cond_timedwait(), but the condvar is left corrupt.
//      Subsequent timedwait/wait calls may hang indefinitely. Given that, we
//      can avoid the problem by reinitializing the condvar -- by cond_destroy()
//      followed by cond_init() -- after all calls to pthread_cond_timedwait().
//      It may be possible to avoid reinitialization by checking the return
//      value from pthread_cond_timedwait(). In addition to reinitializing the
//      condvar we must establish the invariant that cond_signal() is only called
//      within critical sections protected by the adjunct mutex. This prevents
//      cond_signal() from "seeing" a condvar that's in the midst of being
//      reinitialized or that is corrupt. Sadly, this invariant obviates the
//      desirable signal-after-unlock optimization that avoids futile context switching.
//
//      I'm also concerned that some versions of NTPL might allocate an auxilliary
//      structure when a condvar is used or initialized. cond_destroy() would
//      release the helper structure. Our reinitialize-after-timedwait fix
//      put excessive stress on malloc/free and locks protecting the c-heap.
//
// We currently use (4). See the WorkAroundNTPLTimedWaitHang flag.
// It may be possible to refine (4) by checking the kernel and NTPL verisons
// and only enabling the work-around for vulnerable environments.

// utility to compute the abstime argument to timedwait:
// millis is the relative timeout time
// abstime will be the absolute timeout time
// TODO: replace compute_abstime() with unpackTime()

static struct timespec* compute_abstime(timespec* abstime, jlong millis) {
  if (millis < 0) millis = 0;
  struct timeval now;
  int status = gettimeofday(&now, NULL);
  assert(status == 0, "gettimeofday");
  jlong seconds = millis / 1000;
  millis %= 1000;
  if (seconds > 50000000) { // see man cond_timedwait(3T)
    seconds = 50000000;
  }
  abstime->tv_sec = now.tv_sec  + seconds;
  long       usec = now.tv_usec + millis * 1000;
  if (usec >= 1000000) {
    abstime->tv_sec += 1;
    usec -= 1000000;
  }
  abstime->tv_nsec = usec * 1000;
  return abstime;
}


// Test-and-clear _Event, always leaves _Event set to 0, returns immediately.
// Conceptually TryPark() should be equivalent to park(0).

int os::PlatformEvent::TryPark() {
  for (;;) {
    const int v = _Event;
    guarantee ((v == 0) || (v == 1), "invariant");
    if (Atomic::cmpxchg (0, &_Event, v) == v) return v;
  }
}

void os::PlatformEvent::park() {       // AKA "down()"
  // Invariant: Only the thread associated with the Event/PlatformEvent
  // may call park().
  // TODO: assert that _Assoc != NULL or _Assoc == Self
  int v;
  for (;;) {
    v = _Event;
    if (Atomic::cmpxchg (v-1, &_Event, v) == v) break;
  }
  guarantee (v >= 0, "invariant");
  if (v == 0) {
    // Do this the hard way by blocking ...
    int status = pthread_mutex_lock(_mutex);
    assert_status(status == 0, status, "mutex_lock");
    guarantee (_nParked == 0, "invariant");
    ++ _nParked;
    while (_Event < 0) {
      status = pthread_cond_wait(_cond, _mutex);
      assert_status(status == 0 || status == ETIMEDOUT, status, "cond_timedwait");
    }
    -- _nParked;

    // In theory we could move the ST of 0 into _Event past the unlock(),
    // but then we'd need a MEMBAR after the ST.
    _Event = 0;
    status = pthread_mutex_unlock(_mutex);
    assert_status(status == 0, status, "mutex_unlock");
  }
  guarantee (_Event >= 0, "invariant");
}

int os::PlatformEvent::park(jlong millis) {
  guarantee (_nParked == 0, "invariant");

  int v;
  for (;;) {
    v = _Event;
    if (Atomic::cmpxchg (v-1, &_Event, v) == v) break;
  }
  guarantee (v >= 0, "invariant");
  if (v != 0) return OS_OK;

  // We do this the hard way, by blocking the thread.
  // Consider enforcing a minimum timeout value.
  struct timespec abst;
  compute_abstime(&abst, millis);

  int ret = OS_TIMEOUT;
  int status = pthread_mutex_lock(_mutex);
  assert_status(status == 0, status, "mutex_lock");
  guarantee (_nParked == 0, "invariant");
  ++_nParked;

  // Object.wait(timo) will return because of
  // (a) notification
  // (b) timeout
  // (c) thread.interrupt
  //
  // Thread.interrupt and object.notify{All} both call Event::set.
  // That is, we treat thread.interrupt as a special case of notification.
  // The underlying Solaris implementation, cond_timedwait, admits
  // spurious/premature wakeups, but the JLS/JVM spec prevents the
  // JVM from making those visible to Java code. As such, we must
  // filter out spurious wakeups. We assume all ETIME returns are valid.
  //
  // TODO: properly differentiate simultaneous notify+interrupt.
  // In that case, we should propagate the notify to another waiter.

  while (_Event < 0) {
    status = pthread_cond_timedwait(_cond, _mutex, &abst);
    assert_status(status == 0 || status == ETIMEDOUT,
          status, "cond_timedwait");
    if (!FilterSpuriousWakeups) break;         // previous semantics
    if (status == ETIMEDOUT) break;
    // We consume and ignore EINTR and spurious wakeups.
  }
  --_nParked;
  if (_Event >= 0) {
     ret = OS_OK;
  }
  _Event = 0;
  status = pthread_mutex_unlock(_mutex);
  assert_status(status == 0, status, "mutex_unlock");
  assert (_nParked == 0, "invariant");
  return ret;
}

void os::PlatformEvent::unpark() {
  int v, AnyWaiters;
  for (;;) {
    v = _Event;
    if (v > 0) {
      // The LD of _Event could have reordered or be satisfied
      // by a read-aside from this processor's write buffer.
      // To avoid problems execute a barrier and then
      // ratify the value.
      OrderAccess::fence();
      if (_Event == v) return;
      continue;
    }
    if (Atomic::cmpxchg (v+1, &_Event, v) == v) break;
  }
  if (v < 0) {
    // Wait for the thread associated with the event to vacate
    int status = pthread_mutex_lock(_mutex);
    assert_status(status == 0, status, "mutex_lock");
    AnyWaiters = _nParked;

    if (AnyWaiters != 0) {
      // We intentional signal *after* dropping the lock
      // to avoid a common class of futile wakeups.
      status = pthread_cond_signal(_cond);
      assert_status(status == 0, status, "cond_signal");
    }
    // Mutex should be locked for pthread_cond_signal(_cond).
    status = pthread_mutex_unlock(_mutex);
    assert_status(status == 0, status, "mutex_unlock");
  }

  // Note that we signal() _after dropping the lock for "immortal" Events.
  // This is safe and avoids a common class of futile wakeups. In rare
  // circumstances this can cause a thread to return prematurely from
  // cond_{timed}wait() but the spurious wakeup is benign and the victim will
  // simply re-test the condition and re-park itself.
}


// JSR166
// -------------------------------------------------------

//
// The solaris and linux implementations of park/unpark are fairly
// conservative for now, but can be improved. They currently use a
// mutex/condvar pair, plus a a count.
// Park decrements count if > 0, else does a condvar wait. Unpark
// sets count to 1 and signals condvar. Only one thread ever waits
// on the condvar. Contention seen when trying to park implies that someone
// is unparking you, so don't wait. And spurious returns are fine, so there
// is no need to track notifications.
//

#define MAX_SECS 100000000
//
// This code is common to linux and solaris and will be moved to a
// common place in dolphin.
//
// The passed in time value is either a relative time in nanoseconds
// or an absolute time in milliseconds. Either way it has to be unpacked
// into suitable seconds and nanoseconds components and stored in the
// given timespec structure.
// Given time is a 64-bit value and the time_t used in the timespec is only
// a signed-32-bit value (except on 64-bit Linux) we have to watch for
// overflow if times way in the future are given. Further on Solaris versions
// prior to 10 there is a restriction (see cond_timedwait) that the specified
// number of seconds, in abstime, is less than current_time + 100,000,000.
// As it will be 28 years before "now + 100000000" will overflow we can
// ignore overflow and just impose a hard-limit on seconds using the value
// of "now + 100,000,000". This places a limit on the timeout of about 3.17
// years from "now".
//

static void unpackTime(timespec* absTime, bool isAbsolute, jlong time) {
  assert (time > 0, "convertTime");

  struct timeval now;
  int status = gettimeofday(&now, NULL);
  assert(status == 0, "gettimeofday");

  time_t max_secs = now.tv_sec + MAX_SECS;

  if (isAbsolute) {
    jlong secs = time / 1000;
    if (secs > max_secs) {
      absTime->tv_sec = max_secs;
    }
    else {
      absTime->tv_sec = secs;
    }
    absTime->tv_nsec = (time % 1000) * NANOSECS_PER_MILLISEC;
  }
  else {
    jlong secs = time / NANOSECS_PER_SEC;
    if (secs >= MAX_SECS) {
      absTime->tv_sec = max_secs;
      absTime->tv_nsec = 0;
    }
    else {
      absTime->tv_sec = now.tv_sec + secs;
      absTime->tv_nsec = (time % NANOSECS_PER_SEC) + now.tv_usec*1000;
      if (absTime->tv_nsec >= NANOSECS_PER_SEC) {
        absTime->tv_nsec -= NANOSECS_PER_SEC;
        ++absTime->tv_sec; // note: this must be <= max_secs
      }
    }
  }
  assert(absTime->tv_sec >= 0, "tv_sec < 0");
  assert(absTime->tv_sec <= max_secs, "tv_sec > max_secs");
  assert(absTime->tv_nsec >= 0, "tv_nsec < 0");
  assert(absTime->tv_nsec < NANOSECS_PER_SEC, "tv_nsec >= nanos_per_sec");
}

void Parker::park(bool isAbsolute, jlong time) {
  // Optional fast-path check:
  // Return immediately if a permit is available.
  if (_counter > 0) {
      _counter = 0;
      OrderAccess::fence();
      return;
  }

  Thread* thread = Thread::current();
  assert(thread->is_Java_thread(), "Must be JavaThread");
  JavaThread *jt = (JavaThread *)thread;

  // Optional optimization -- avoid state transitions if there's an interrupt pending.
  // Check interrupt before trying to wait
  if (Thread::is_interrupted(thread, false)) {
    return;
  }

  // Next, demultiplex/decode time arguments
  timespec absTime;
  if (time < 0 || (isAbsolute && time == 0)) { // don't wait at all
    return;
  }
  if (time > 0) {
    unpackTime(&absTime, isAbsolute, time);
  }


  // Enter safepoint region
  // Beware of deadlocks such as 6317397.
  // The per-thread Parker:: mutex is a classic leaf-lock.
  // In particular a thread must never block on the Threads_lock while
  // holding the Parker:: mutex. If safepoints are pending both the
  // the ThreadBlockInVM() CTOR and DTOR may grab Threads_lock.
  ThreadBlockInVM tbivm(jt);

  // Don't wait if cannot get lock since interference arises from
  // unblocking. Also. check interrupt before trying wait
  if (Thread::is_interrupted(thread, false) || pthread_mutex_trylock(_mutex) != 0) {
    return;
  }

  int status;
  if (_counter > 0) { // no wait needed
    _counter = 0;
    status = pthread_mutex_unlock(_mutex);
    assert (status == 0, "invariant");
    OrderAccess::fence();
    return;
  }

#ifdef ASSERT
  // Don't catch signals while blocked; let the running threads have the signals.
  // (This allows a debugger to break into the running thread.)
  sigset_t oldsigs;
  sigset_t* allowdebug_blocked = os::Aix::allowdebug_blocked_signals();
  pthread_sigmask(SIG_BLOCK, allowdebug_blocked, &oldsigs);
#endif

  OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
  jt->set_suspend_equivalent();
  // cleared by handle_special_suspend_equivalent_condition() or java_suspend_self()

  if (time == 0) {
    status = pthread_cond_wait (_cond, _mutex);
  } else {
    status = pthread_cond_timedwait (_cond, _mutex, &absTime);
    if (status != 0 && WorkAroundNPTLTimedWaitHang) {
      pthread_cond_destroy (_cond);
      pthread_cond_init    (_cond, NULL);
    }
  }
  assert_status(status == 0 || status == EINTR ||
                status == ETIME || status == ETIMEDOUT,
                status, "cond_timedwait");

#ifdef ASSERT
  pthread_sigmask(SIG_SETMASK, &oldsigs, NULL);
#endif

  _counter = 0;
  status = pthread_mutex_unlock(_mutex);
  assert_status(status == 0, status, "invariant");
  // If externally suspended while waiting, re-suspend
  if (jt->handle_special_suspend_equivalent_condition()) {
    jt->java_suspend_self();
  }

  OrderAccess::fence();
}

void Parker::unpark() {
  int s, status;
  status = pthread_mutex_lock(_mutex);
  assert (status == 0, "invariant");
  s = _counter;
  _counter = 1;
  if (s < 1) {
    if (WorkAroundNPTLTimedWaitHang) {
      status = pthread_cond_signal (_cond);
      assert (status == 0, "invariant");
      status = pthread_mutex_unlock(_mutex);
      assert (status == 0, "invariant");
    } else {
      status = pthread_mutex_unlock(_mutex);
      assert (status == 0, "invariant");
      status = pthread_cond_signal (_cond);
      assert (status == 0, "invariant");
    }
  } else {
    pthread_mutex_unlock(_mutex);
    assert (status == 0, "invariant");
  }
}


extern char** environ;

// Run the specified command in a separate process. Return its exit value,
// or -1 on failure (e.g. can't fork a new process).
// Unlike system(), this function can be called from signal handler. It
// doesn't block SIGINT et al.
int os::fork_and_exec(char* cmd) {
  char * argv[4] = {"sh", "-c", cmd, NULL};

  pid_t pid = fork();

  if (pid < 0) {
    // fork failed
    return -1;

  } else if (pid == 0) {
    // child process

    // try to be consistent with system(), which uses "/usr/bin/sh" on AIX
    execve("/usr/bin/sh", argv, environ);

    // execve failed
    _exit(-1);

  } else  {
    // copied from J2SE ..._waitForProcessExit() in UNIXProcess_md.c; we don't
    // care about the actual exit code, for now.

    int status;

    // Wait for the child process to exit.  This returns immediately if
    // the child has already exited. */
    while (waitpid(pid, &status, 0) < 0) {
        switch (errno) {
        case ECHILD: return 0;
        case EINTR: break;
        default: return -1;
        }
    }

    if (WIFEXITED(status)) {
       // The child exited normally; get its exit code.
       return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
       // The child exited because of a signal
       // The best value to return is 0x80 + signal number,
       // because that is what all Unix shells do, and because
       // it allows callers to distinguish between process exit and
       // process death by signal.
       return 0x80 + WTERMSIG(status);
    } else {
       // Unknown exit code; pass it through
       return status;
    }
  }
  // Remove warning.
  return -1;
}

// is_headless_jre()
//
// Test for the existence of xawt/libmawt.so or libawt_xawt.so
// in order to report if we are running in a headless jre.
//
// Since JDK8 xawt/libmawt.so is moved into the same directory
// as libawt.so, and renamed libawt_xawt.so
bool os::is_headless_jre() {
  struct stat statbuf;
  char buf[MAXPATHLEN];
  char libmawtpath[MAXPATHLEN];
  const char *xawtstr  = "/xawt/libmawt.so";
  const char *new_xawtstr = "/libawt_xawt.so";

  char *p;

  // Get path to libjvm.so
  os::jvm_path(buf, sizeof(buf));

  // Get rid of libjvm.so
  p = strrchr(buf, '/');
  if (p == NULL) return false;
  else *p = '\0';

  // Get rid of client or server
  p = strrchr(buf, '/');
  if (p == NULL) return false;
  else *p = '\0';

  // check xawt/libmawt.so
  strcpy(libmawtpath, buf);
  strcat(libmawtpath, xawtstr);
  if (::stat(libmawtpath, &statbuf) == 0) return false;

  // check libawt_xawt.so
  strcpy(libmawtpath, buf);
  strcat(libmawtpath, new_xawtstr);
  if (::stat(libmawtpath, &statbuf) == 0) return false;

  return true;
}

// Get the default path to the core file
// Returns the length of the string
int os::get_core_path(char* buffer, size_t bufferSize) {
  const char* p = get_current_directory(buffer, bufferSize);

  if (p == NULL) {
    assert(p != NULL, "failed to get current directory");
    return 0;
  }

  return strlen(buffer);
}

#ifndef PRODUCT
void TestReserveMemorySpecial_test() {
  // No tests available for this platform
}
#endif

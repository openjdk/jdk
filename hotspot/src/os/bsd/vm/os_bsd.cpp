/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

// no precompiled headers
#include "classfile/classLoader.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/icBuffer.hpp"
#include "code/vtableStubs.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm_bsd.h"
#include "memory/allocation.inline.hpp"
#include "memory/filemap.hpp"
#include "mutex_bsd.inline.hpp"
#include "oops/oop.inline.hpp"
#include "os_share_bsd.hpp"
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

// put OS-includes here
# include <sys/types.h>
# include <sys/mman.h>
# include <sys/stat.h>
# include <sys/select.h>
# include <pthread.h>
# include <signal.h>
# include <errno.h>
# include <dlfcn.h>
# include <stdio.h>
# include <unistd.h>
# include <sys/resource.h>
# include <pthread.h>
# include <sys/stat.h>
# include <sys/time.h>
# include <sys/times.h>
# include <sys/utsname.h>
# include <sys/socket.h>
# include <sys/wait.h>
# include <time.h>
# include <pwd.h>
# include <poll.h>
# include <semaphore.h>
# include <fcntl.h>
# include <string.h>
# include <sys/param.h>
# include <sys/sysctl.h>
# include <sys/ipc.h>
# include <sys/shm.h>
#ifndef __APPLE__
# include <link.h>
#endif
# include <stdint.h>
# include <inttypes.h>
# include <sys/ioctl.h>

#if defined(__FreeBSD__) || defined(__NetBSD__)
# include <elf.h>
#endif

#ifdef __APPLE__
# include <mach/mach.h> // semaphore_* API
# include <mach-o/dyld.h>
# include <sys/proc_info.h>
# include <objc/objc-auto.h>
#endif

#ifndef MAP_ANONYMOUS
#define MAP_ANONYMOUS MAP_ANON
#endif

#define MAX_PATH    (2 * K)

// for timer info max values which include all bits
#define ALL_64_BITS CONST64(0xFFFFFFFFFFFFFFFF)

#define LARGEPAGES_BIT (1 << 6)
////////////////////////////////////////////////////////////////////////////////
// global variables
julong os::Bsd::_physical_memory = 0;


int (*os::Bsd::_clock_gettime)(clockid_t, struct timespec *) = NULL;
pthread_t os::Bsd::_main_thread;
int os::Bsd::_page_size = -1;

static jlong initial_time_count=0;

static int clock_tics_per_sec = 100;

// For diagnostics to print a message once. see run_periodic_checks
static sigset_t check_signal_done;
static bool check_signals = true;

static pid_t _initial_pid = 0;

/* Signal number used to suspend/resume a thread */

/* do not use any signal number less than SIGSEGV, see 4355769 */
static int SR_signum = SIGUSR2;
sigset_t SR_sigset;


////////////////////////////////////////////////////////////////////////////////
// utility functions

static int SR_initialize();

julong os::available_memory() {
  return Bsd::available_memory();
}

julong os::Bsd::available_memory() {
  // XXXBSD: this is just a stopgap implementation
  return physical_memory() >> 2;
}

julong os::physical_memory() {
  return Bsd::physical_memory();
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



// Cpu architecture string
#if   defined(ZERO)
static char cpu_arch[] = ZERO_LIBARCH;
#elif defined(IA64)
static char cpu_arch[] = "ia64";
#elif defined(IA32)
static char cpu_arch[] = "i386";
#elif defined(AMD64)
static char cpu_arch[] = "amd64";
#elif defined(ARM)
static char cpu_arch[] = "arm";
#elif defined(PPC)
static char cpu_arch[] = "ppc";
#elif defined(SPARC)
#  ifdef _LP64
static char cpu_arch[] = "sparcv9";
#  else
static char cpu_arch[] = "sparc";
#  endif
#else
#error Add appropriate cpu_arch setting
#endif

// Compiler variant
#ifdef COMPILER2
#define COMPILER_VARIANT "server"
#else
#define COMPILER_VARIANT "client"
#endif


void os::Bsd::initialize_system_info() {
  int mib[2];
  size_t len;
  int cpu_val;
  julong mem_val;

  /* get processors count via hw.ncpus sysctl */
  mib[0] = CTL_HW;
  mib[1] = HW_NCPU;
  len = sizeof(cpu_val);
  if (sysctl(mib, 2, &cpu_val, &len, NULL, 0) != -1 && cpu_val >= 1) {
       assert(len == sizeof(cpu_val), "unexpected data size");
       set_processor_count(cpu_val);
  }
  else {
       set_processor_count(1);   // fallback
  }

  /* get physical memory via hw.memsize sysctl (hw.memsize is used
   * since it returns a 64 bit value)
   */
  mib[0] = CTL_HW;
  mib[1] = HW_MEMSIZE;
  len = sizeof(mem_val);
  if (sysctl(mib, 2, &mem_val, &len, NULL, 0) != -1) {
       assert(len == sizeof(mem_val), "unexpected data size");
       _physical_memory = mem_val;
  } else {
       _physical_memory = 256*1024*1024;       // fallback (XXXBSD?)
  }

#ifdef __OpenBSD__
  {
       // limit _physical_memory memory view on OpenBSD since
       // datasize rlimit restricts us anyway.
       struct rlimit limits;
       getrlimit(RLIMIT_DATA, &limits);
       _physical_memory = MIN2(_physical_memory, (julong)limits.rlim_cur);
  }
#endif
}

#ifdef __APPLE__
static const char *get_home() {
  const char *home_dir = ::getenv("HOME");
  if ((home_dir == NULL) || (*home_dir == '\0')) {
    struct passwd *passwd_info = getpwuid(geteuid());
    if (passwd_info != NULL) {
      home_dir = passwd_info->pw_dir;
    }
  }

  return home_dir;
}
#endif

void os::init_system_properties_values() {
//  char arch[12];
//  sysinfo(SI_ARCHITECTURE, arch, sizeof(arch));

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

  // The next few definitions allow the code to be verbatim:
#define malloc(n) (char*)NEW_C_HEAP_ARRAY(char, (n), mtInternal)
#define getenv(n) ::getenv(n)

/*
 * See ld(1):
 *      The linker uses the following search paths to locate required
 *      shared libraries:
 *        1: ...
 *        ...
 *        7: The default directories, normally /lib and /usr/lib.
 */
#ifndef DEFAULT_LIBPATH
#define DEFAULT_LIBPATH "/lib:/usr/lib"
#endif

#define EXTENSIONS_DIR  "/lib/ext"
#define ENDORSED_DIR    "/lib/endorsed"
#define REG_DIR         "/usr/java/packages"

#ifdef __APPLE__
#define SYS_EXTENSIONS_DIR   "/Library/Java/Extensions"
#define SYS_EXTENSIONS_DIRS  SYS_EXTENSIONS_DIR ":/Network" SYS_EXTENSIONS_DIR ":/System" SYS_EXTENSIONS_DIR ":/usr/lib/java"
        const char *user_home_dir = get_home();
        // the null in SYS_EXTENSIONS_DIRS counts for the size of the colon after user_home_dir
        int system_ext_size = strlen(user_home_dir) + sizeof(SYS_EXTENSIONS_DIR) +
            sizeof(SYS_EXTENSIONS_DIRS);
#endif

  {
    /* sysclasspath, java_home, dll_dir */
    {
        char *home_path;
        char *dll_path;
        char *pslash;
        char buf[MAXPATHLEN];
        os::jvm_path(buf, sizeof(buf));

        // Found the full path to libjvm.so.
        // Now cut the path to <java_home>/jre if we can.
        *(strrchr(buf, '/')) = '\0';  /* get rid of /libjvm.so */
        pslash = strrchr(buf, '/');
        if (pslash != NULL)
            *pslash = '\0';           /* get rid of /{client|server|hotspot} */
        dll_path = malloc(strlen(buf) + 1);
        if (dll_path == NULL)
            return;
        strcpy(dll_path, buf);
        Arguments::set_dll_dir(dll_path);

        if (pslash != NULL) {
            pslash = strrchr(buf, '/');
            if (pslash != NULL) {
                *pslash = '\0';       /* get rid of /<arch> (/lib on macosx) */
#ifndef __APPLE__
                pslash = strrchr(buf, '/');
                if (pslash != NULL)
                    *pslash = '\0';   /* get rid of /lib */
#endif
            }
        }

        home_path = malloc(strlen(buf) + 1);
        if (home_path == NULL)
            return;
        strcpy(home_path, buf);
        Arguments::set_java_home(home_path);

        if (!set_boot_path('/', ':'))
            return;
    }

    /*
     * Where to look for native libraries
     *
     * Note: Due to a legacy implementation, most of the library path
     * is set in the launcher.  This was to accomodate linking restrictions
     * on legacy Bsd implementations (which are no longer supported).
     * Eventually, all the library path setting will be done here.
     *
     * However, to prevent the proliferation of improperly built native
     * libraries, the new path component /usr/java/packages is added here.
     * Eventually, all the library path setting will be done here.
     */
    {
        char *ld_library_path;

        /*
         * Construct the invariant part of ld_library_path. Note that the
         * space for the colon and the trailing null are provided by the
         * nulls included by the sizeof operator (so actually we allocate
         * a byte more than necessary).
         */
#ifdef __APPLE__
        ld_library_path = (char *) malloc(system_ext_size);
        sprintf(ld_library_path, "%s" SYS_EXTENSIONS_DIR ":" SYS_EXTENSIONS_DIRS, user_home_dir);
#else
        ld_library_path = (char *) malloc(sizeof(REG_DIR) + sizeof("/lib/") +
            strlen(cpu_arch) + sizeof(DEFAULT_LIBPATH));
        sprintf(ld_library_path, REG_DIR "/lib/%s:" DEFAULT_LIBPATH, cpu_arch);
#endif

        /*
         * Get the user setting of LD_LIBRARY_PATH, and prepended it.  It
         * should always exist (until the legacy problem cited above is
         * addressed).
         */
#ifdef __APPLE__
        // Prepend the default path with the JAVA_LIBRARY_PATH so that the app launcher code can specify a directory inside an app wrapper
        char *l = getenv("JAVA_LIBRARY_PATH");
        if (l != NULL) {
            char *t = ld_library_path;
            /* That's +1 for the colon and +1 for the trailing '\0' */
            ld_library_path = (char *) malloc(strlen(l) + 1 + strlen(t) + 1);
            sprintf(ld_library_path, "%s:%s", l, t);
            free(t);
        }

        char *v = getenv("DYLD_LIBRARY_PATH");
#else
        char *v = getenv("LD_LIBRARY_PATH");
#endif
        if (v != NULL) {
            char *t = ld_library_path;
            /* That's +1 for the colon and +1 for the trailing '\0' */
            ld_library_path = (char *) malloc(strlen(v) + 1 + strlen(t) + 1);
            sprintf(ld_library_path, "%s:%s", v, t);
            free(t);
        }

#ifdef __APPLE__
        // Apple's Java6 has "." at the beginning of java.library.path.
        // OpenJDK on Windows has "." at the end of java.library.path.
        // OpenJDK on Linux and Solaris don't have "." in java.library.path
        // at all. To ease the transition from Apple's Java6 to OpenJDK7,
        // "." is appended to the end of java.library.path. Yes, this
        // could cause a change in behavior, but Apple's Java6 behavior
        // can be achieved by putting "." at the beginning of the
        // JAVA_LIBRARY_PATH environment variable.
        {
            char *t = ld_library_path;
            // that's +3 for appending ":." and the trailing '\0'
            ld_library_path = (char *) malloc(strlen(t) + 3);
            sprintf(ld_library_path, "%s:%s", t, ".");
            free(t);
        }
#endif

        Arguments::set_library_path(ld_library_path);
    }

    /*
     * Extensions directories.
     *
     * Note that the space for the colon and the trailing null are provided
     * by the nulls included by the sizeof operator (so actually one byte more
     * than necessary is allocated).
     */
    {
#ifdef __APPLE__
        char *buf = malloc(strlen(Arguments::get_java_home()) +
            sizeof(EXTENSIONS_DIR) + system_ext_size);
        sprintf(buf, "%s" SYS_EXTENSIONS_DIR ":%s" EXTENSIONS_DIR ":"
            SYS_EXTENSIONS_DIRS, user_home_dir, Arguments::get_java_home());
#else
        char *buf = malloc(strlen(Arguments::get_java_home()) +
            sizeof(EXTENSIONS_DIR) + sizeof(REG_DIR) + sizeof(EXTENSIONS_DIR));
        sprintf(buf, "%s" EXTENSIONS_DIR ":" REG_DIR EXTENSIONS_DIR,
            Arguments::get_java_home());
#endif

        Arguments::set_ext_dirs(buf);
    }

    /* Endorsed standards default directory. */
    {
        char * buf;
        buf = malloc(strlen(Arguments::get_java_home()) + sizeof(ENDORSED_DIR));
        sprintf(buf, "%s" ENDORSED_DIR, Arguments::get_java_home());
        Arguments::set_endorsed_dirs(buf);
    }
  }

#ifdef __APPLE__
#undef SYS_EXTENSIONS_DIR
#endif
#undef malloc
#undef getenv
#undef EXTENSIONS_DIR
#undef ENDORSED_DIR

  // Done
  return;
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

bool os::Bsd::is_sig_ignored(int sig) {
      struct sigaction oact;
      sigaction(sig, (struct sigaction*)NULL, &oact);
      void* ohlr = oact.sa_sigaction ? CAST_FROM_FN_PTR(void*,  oact.sa_sigaction)
                                     : CAST_FROM_FN_PTR(void*,  oact.sa_handler);
      if (ohlr == CAST_FROM_FN_PTR(void*, SIG_IGN))
           return true;
      else
           return false;
}

void os::Bsd::signal_sets_init() {
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
  sigaddset(&unblocked_sigs, SR_signum);

  if (!ReduceSignalUsage) {
   if (!os::Bsd::is_sig_ignored(SHUTDOWN1_SIGNAL)) {
      sigaddset(&unblocked_sigs, SHUTDOWN1_SIGNAL);
      sigaddset(&allowdebug_blocked_sigs, SHUTDOWN1_SIGNAL);
   }
   if (!os::Bsd::is_sig_ignored(SHUTDOWN2_SIGNAL)) {
      sigaddset(&unblocked_sigs, SHUTDOWN2_SIGNAL);
      sigaddset(&allowdebug_blocked_sigs, SHUTDOWN2_SIGNAL);
   }
   if (!os::Bsd::is_sig_ignored(SHUTDOWN3_SIGNAL)) {
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
sigset_t* os::Bsd::unblocked_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &unblocked_sigs;
}

// These are the signals that are blocked while a (non-VM) thread is
// running Java. Only the VM thread handles these signals.
sigset_t* os::Bsd::vm_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &vm_sigs;
}

// These are signals that are blocked during cond_wait to allow debugger in
sigset_t* os::Bsd::allowdebug_blocked_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &allowdebug_blocked_sigs;
}

void os::Bsd::hotspot_sigmask(Thread* thread) {

  //Save caller's signal mask before setting VM signal mask
  sigset_t caller_sigmask;
  pthread_sigmask(SIG_BLOCK, NULL, &caller_sigmask);

  OSThread* osthread = thread->osthread();
  osthread->set_caller_sigmask(caller_sigmask);

  pthread_sigmask(SIG_UNBLOCK, os::Bsd::unblocked_signals(), NULL);

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


//////////////////////////////////////////////////////////////////////////////
// create new thread

// check if it's safe to start a new thread
static bool _thread_safety_check(Thread* thread) {
  return true;
}

#ifdef __APPLE__
// library handle for calling objc_registerThreadWithCollector()
// without static linking to the libobjc library
#define OBJC_LIB "/usr/lib/libobjc.dylib"
#define OBJC_GCREGISTER "objc_registerThreadWithCollector"
typedef void (*objc_registerThreadWithCollector_t)();
extern "C" objc_registerThreadWithCollector_t objc_registerThreadWithCollectorFunction;
objc_registerThreadWithCollector_t objc_registerThreadWithCollectorFunction = NULL;
#endif

#ifdef __APPLE__
static uint64_t locate_unique_thread_id(mach_port_t mach_thread_port) {
  // Additional thread_id used to correlate threads in SA
  thread_identifier_info_data_t     m_ident_info;
  mach_msg_type_number_t            count = THREAD_IDENTIFIER_INFO_COUNT;

  thread_info(mach_thread_port, THREAD_IDENTIFIER_INFO,
              (thread_info_t) &m_ident_info, &count);

  return m_ident_info.thread_id;
}
#endif

// Thread start routine for all newly created threads
static void *java_start(Thread *thread) {
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
  Monitor* sync = osthread->startThread_lock();

  // non floating stack BsdThreads needs extra check, see above
  if (!_thread_safety_check(thread)) {
    // notify parent thread
    MutexLockerEx ml(sync, Mutex::_no_safepoint_check_flag);
    osthread->set_state(ZOMBIE);
    sync->notify_all();
    return NULL;
  }

#ifdef __APPLE__
  // thread_id is mach thread on macos, which pthreads graciously caches and provides for us
  mach_port_t thread_id = ::pthread_mach_thread_np(::pthread_self());
  guarantee(thread_id != 0, "thread id missing from pthreads");
  osthread->set_thread_id(thread_id);

  uint64_t unique_thread_id = locate_unique_thread_id(thread_id);
  guarantee(unique_thread_id != 0, "unique thread id was not found");
  osthread->set_unique_thread_id(unique_thread_id);
#else
  // thread_id is pthread_id on BSD
  osthread->set_thread_id(::pthread_self());
#endif
  // initialize signal mask for this thread
  os::Bsd::hotspot_sigmask(thread);

  // initialize floating point control register
  os::Bsd::init_thread_fpu_state();

#ifdef __APPLE__
  // register thread with objc gc
  if (objc_registerThreadWithCollectorFunction != NULL) {
    objc_registerThreadWithCollectorFunction();
  }
#endif

  // handshaking with parent thread
  {
    MutexLockerEx ml(sync, Mutex::_no_safepoint_check_flag);

    // notify parent thread
    osthread->set_state(INITIALIZED);
    sync->notify_all();

    // wait until os::start_thread()
    while (osthread->get_state() == INITIALIZED) {
      sync->wait(Mutex::_no_safepoint_check_flag);
    }
  }

  // call one more level start routine
  thread->run();

  return 0;
}

bool os::create_thread(Thread* thread, ThreadType thr_type, size_t stack_size) {
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
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

  // stack size
  if (os::Bsd::supports_variable_stack_size()) {
    // calculate stack size if it's not specified by caller
    if (stack_size == 0) {
      stack_size = os::Bsd::default_stack_size(thr_type);

      switch (thr_type) {
      case os::java_thread:
        // Java threads use ThreadStackSize which default value can be
        // changed with the flag -Xss
        assert (JavaThread::stack_size_at_create() > 0, "this should be set");
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

    stack_size = MAX2(stack_size, os::Bsd::min_stack_allowed);
    pthread_attr_setstacksize(&attr, stack_size);
  } else {
    // let pthread_create() pick the default value.
  }

  ThreadState state;

  {
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

    // Wait until child thread is either initialized or aborted
    {
      Monitor* sync_with_child = osthread->startThread_lock();
      MutexLockerEx ml(sync_with_child, Mutex::_no_safepoint_check_flag);
      while ((state = osthread->get_state()) == ALLOCATED) {
        sync_with_child->wait(Mutex::_no_safepoint_check_flag);
      }
    }

  }

  // Aborted due to thread limit being reached
  if (state == ZOMBIE) {
      thread->set_osthread(NULL);
      delete osthread;
      return false;
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
  OSThread* osthread = new OSThread(NULL, NULL);

  if (osthread == NULL) {
    return false;
  }

  // Store pthread info into the OSThread
#ifdef __APPLE__
  // thread_id is mach thread on macos, which pthreads graciously caches and provides for us
  mach_port_t thread_id = ::pthread_mach_thread_np(::pthread_self());
  guarantee(thread_id != 0, "just checking");
  osthread->set_thread_id(thread_id);

  uint64_t unique_thread_id = locate_unique_thread_id(thread_id);
  guarantee(unique_thread_id != 0, "just checking");
  osthread->set_unique_thread_id(unique_thread_id);
#else
  osthread->set_thread_id(::pthread_self());
#endif
  osthread->set_pthread_id(::pthread_self());

  // initialize floating point control register
  os::Bsd::init_thread_fpu_state();

  // Initial thread state is RUNNABLE
  osthread->set_state(RUNNABLE);

  thread->set_osthread(osthread);

  // initialize signal mask for this thread
  // and save the caller's signal mask
  os::Bsd::hotspot_sigmask(thread);

  return true;
}

void os::pd_start_thread(Thread* thread) {
  OSThread * osthread = thread->osthread();
  assert(osthread->get_state() != INITIALIZED, "just checking");
  Monitor* sync_with_child = osthread->startThread_lock();
  MutexLockerEx ml(sync_with_child, Mutex::_no_safepoint_check_flag);
  sync_with_child->notify();
}

// Free Bsd resources related to the OSThread
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

bool os::supports_vtime() { return true; }
bool os::enable_vtime()   { return false; }
bool os::vtime_enabled()  { return false; }

double os::elapsedVTime() {
  // better than nothing, but not much
  return elapsedTime();
}

jlong os::javaTimeMillis() {
  timeval time;
  int status = gettimeofday(&time, NULL);
  assert(status != -1, "bsd error");
  return jlong(time.tv_sec) * 1000  +  jlong(time.tv_usec / 1000);
}

#ifndef CLOCK_MONOTONIC
#define CLOCK_MONOTONIC (1)
#endif

#ifdef __APPLE__
void os::Bsd::clock_init() {
        // XXXDARWIN: Investigate replacement monotonic clock
}
#else
void os::Bsd::clock_init() {
  struct timespec res;
  struct timespec tp;
  if (::clock_getres(CLOCK_MONOTONIC, &res) == 0 &&
      ::clock_gettime(CLOCK_MONOTONIC, &tp)  == 0) {
    // yes, monotonic clock is supported
    _clock_gettime = ::clock_gettime;
  }
}
#endif


jlong os::javaTimeNanos() {
  if (Bsd::supports_monotonic_clock()) {
    struct timespec tp;
    int status = Bsd::clock_gettime(CLOCK_MONOTONIC, &tp);
    assert(status == 0, "gettime error");
    jlong result = jlong(tp.tv_sec) * (1000 * 1000 * 1000) + jlong(tp.tv_nsec);
    return result;
  } else {
    timeval time;
    int status = gettimeofday(&time, NULL);
    assert(status != -1, "bsd error");
    jlong usecs = jlong(time.tv_sec) * (1000 * 1000) + jlong(time.tv_usec);
    return 1000 * usecs;
  }
}

void os::javaTimeNanos_info(jvmtiTimerInfo *info_ptr) {
  if (Bsd::supports_monotonic_clock()) {
    info_ptr->max_value = ALL_64_BITS;

    // CLOCK_MONOTONIC - amount of time since some arbitrary point in the past
    info_ptr->may_skip_backward = false;      // not subject to resetting or drifting
    info_ptr->may_skip_forward = false;       // not subject to resetting or drifting
  } else {
    // gettimeofday - based on time in seconds since the Epoch thus does not wrap
    info_ptr->max_value = ALL_64_BITS;

    // gettimeofday is a real time clock so it skips
    info_ptr->may_skip_backward = true;
    info_ptr->may_skip_forward = true;
  }

  info_ptr->kind = JVMTI_TIMER_ELAPSED;                // elapsed not CPU time
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

struct tm* os::localtime_pd(const time_t* clock, struct tm*  res) {
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
  // _exit() on BsdThreads only kills current thread
  ::abort();
}

// unused on bsd for now.
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

intx os::current_thread_id() {
#ifdef __APPLE__
  return (intx)::pthread_mach_thread_np(::pthread_self());
#else
  return (intx)::pthread_self();
#endif
}
int os::current_process_id() {

  // Under the old bsd thread library, bsd gives each thread
  // its own process id. Because of this each thread will return
  // a different pid if this method were to return the result
  // of getpid(2). Bsd provides no api that returns the pid
  // of the launcher thread for the vm. This implementation
  // returns a unique pid, the pid of the launcher thread
  // that starts the vm 'process'.

  // Under the NPTL, getpid() returns the same pid as the
  // launcher thread rather than a unique pid per thread.
  // Use gettid() if you want the old pre NPTL behaviour.

  // if you are looking for the result of a call to getpid() that
  // returns a unique pid for the calling thread, then look at the
  // OSThread::thread_id() method in osThread_bsd.hpp file

  return (int)(_initial_pid ? _initial_pid : getpid());
}

// DLL functions

#define JNI_LIB_PREFIX "lib"
#ifdef __APPLE__
#define JNI_LIB_SUFFIX ".dylib"
#else
#define JNI_LIB_SUFFIX ".so"
#endif

const char* os::dll_file_extension() { return JNI_LIB_SUFFIX; }

// This must be hard coded because it's the system's temporary
// directory not the java application's temp directory, ala java.io.tmpdir.
#ifdef __APPLE__
// macosx has a secure per-user temporary directory
char temp_path_storage[PATH_MAX];
const char* os::get_temp_directory() {
  static char *temp_path = NULL;
  if (temp_path == NULL) {
    int pathSize = confstr(_CS_DARWIN_USER_TEMP_DIR, temp_path_storage, PATH_MAX);
    if (pathSize == 0 || pathSize > PATH_MAX) {
      strlcpy(temp_path_storage, "/tmp/", sizeof(temp_path_storage));
    }
    temp_path = temp_path_storage;
  }
  return temp_path;
}
#else /* __APPLE__ */
const char* os::get_temp_directory() { return "/tmp"; }
#endif /* __APPLE__ */

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
  if (pnamelen + strlen(fname) + strlen(JNI_LIB_PREFIX) + strlen(JNI_LIB_SUFFIX) + 2 > buflen) {
    return retval;
  }

  if (pnamelen == 0) {
    snprintf(buffer, buflen, JNI_LIB_PREFIX "%s" JNI_LIB_SUFFIX, fname);
    retval = true;
  } else if (strchr(pname, *os::path_separator()) != NULL) {
    int n;
    char** pelements = split_path(pname, &n);
    if (pelements == NULL) {
      return false;
    }
    for (int i = 0 ; i < n ; i++) {
      // Really shouldn't be NULL, but check can't hurt
      if (pelements[i] == NULL || strlen(pelements[i]) == 0) {
        continue; // skip the empty path values
      }
      snprintf(buffer, buflen, "%s/" JNI_LIB_PREFIX "%s" JNI_LIB_SUFFIX,
          pelements[i], fname);
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
    snprintf(buffer, buflen, "%s/" JNI_LIB_PREFIX "%s" JNI_LIB_SUFFIX, pname, fname);
    retval = true;
  }
  return retval;
}

// check if addr is inside libjvm.so
bool os::address_is_in_vm(address addr) {
  static address libjvm_base_addr;
  Dl_info dlinfo;

  if (libjvm_base_addr == NULL) {
    if (dladdr(CAST_FROM_FN_PTR(void *, os::address_is_in_vm), &dlinfo) != 0) {
      libjvm_base_addr = (address)dlinfo.dli_fbase;
    }
    assert(libjvm_base_addr !=NULL, "Cannot obtain base address for libjvm");
  }

  if (dladdr((void *)addr, &dlinfo) != 0) {
    if (libjvm_base_addr == (address)dlinfo.dli_fbase) return true;
  }

  return false;
}


#define MACH_MAXSYMLEN 256

bool os::dll_address_to_function_name(address addr, char *buf,
                                      int buflen, int *offset) {
  // buf is not optional, but offset is optional
  assert(buf != NULL, "sanity check");

  Dl_info dlinfo;
  char localbuf[MACH_MAXSYMLEN];

  if (dladdr((void*)addr, &dlinfo) != 0) {
    // see if we have a matching symbol
    if (dlinfo.dli_saddr != NULL && dlinfo.dli_sname != NULL) {
      if (!Decoder::demangle(dlinfo.dli_sname, buf, buflen)) {
        jio_snprintf(buf, buflen, "%s", dlinfo.dli_sname);
      }
      if (offset != NULL) *offset = addr - (address)dlinfo.dli_saddr;
      return true;
    }
    // no matching symbol so try for just file info
    if (dlinfo.dli_fname != NULL && dlinfo.dli_fbase != NULL) {
      if (Decoder::decode((address)(addr - (address)dlinfo.dli_fbase),
                          buf, buflen, offset, dlinfo.dli_fname)) {
         return true;
      }
    }

    // Handle non-dynamic manually:
    if (dlinfo.dli_fbase != NULL &&
        Decoder::decode(addr, localbuf, MACH_MAXSYMLEN, offset,
                        dlinfo.dli_fbase)) {
      if (!Decoder::demangle(localbuf, buf, buflen)) {
        jio_snprintf(buf, buflen, "%s", localbuf);
      }
      return true;
    }
  }
  buf[0] = '\0';
  if (offset != NULL) *offset = -1;
  return false;
}

// ported from solaris version
bool os::dll_address_to_library_name(address addr, char* buf,
                                     int buflen, int* offset) {
  // buf is not optional, but offset is optional
  assert(buf != NULL, "sanity check");

  Dl_info dlinfo;

  if (dladdr((void*)addr, &dlinfo) != 0) {
    if (dlinfo.dli_fname != NULL) {
      jio_snprintf(buf, buflen, "%s", dlinfo.dli_fname);
    }
    if (dlinfo.dli_fbase != NULL && offset != NULL) {
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

#ifdef __APPLE__
void * os::dll_load(const char *filename, char *ebuf, int ebuflen) {
  void * result= ::dlopen(filename, RTLD_LAZY);
  if (result != NULL) {
    // Successful loading
    return result;
  }

  // Read system error message into ebuf
  ::strncpy(ebuf, ::dlerror(), ebuflen-1);
  ebuf[ebuflen-1]='\0';

  return NULL;
}
#else
void * os::dll_load(const char *filename, char *ebuf, int ebuflen)
{
  void * result= ::dlopen(filename, RTLD_LAZY);
  if (result != NULL) {
    // Successful loading
    return result;
  }

  Elf32_Ehdr elf_head;

  // Read system error message into ebuf
  // It may or may not be overwritten below
  ::strncpy(ebuf, ::dlerror(), ebuflen-1);
  ebuf[ebuflen-1]='\0';
  int diag_msg_max_length=ebuflen-strlen(ebuf);
  char* diag_msg_buf=ebuf+strlen(ebuf);

  if (diag_msg_max_length==0) {
    // No more space in ebuf for additional diagnostics message
    return NULL;
  }


  int file_descriptor= ::open(filename, O_RDONLY | O_NONBLOCK);

  if (file_descriptor < 0) {
    // Can't open library, report dlerror() message
    return NULL;
  }

  bool failed_to_read_elf_head=
    (sizeof(elf_head)!=
        (::read(file_descriptor, &elf_head,sizeof(elf_head)))) ;

  ::close(file_descriptor);
  if (failed_to_read_elf_head) {
    // file i/o error - report dlerror() msg
    return NULL;
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
    {EM_SPARC,       EM_SPARC,   ELFCLASS32, ELFDATA2MSB, (char*)"Sparc 32"},
    {EM_SPARC32PLUS, EM_SPARC,   ELFCLASS32, ELFDATA2MSB, (char*)"Sparc 32"},
    {EM_SPARCV9,     EM_SPARCV9, ELFCLASS64, ELFDATA2MSB, (char*)"Sparc v9 64"},
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

  #if  (defined IA32)
    static  Elf32_Half running_arch_code=EM_386;
  #elif   (defined AMD64)
    static  Elf32_Half running_arch_code=EM_X86_64;
  #elif  (defined IA64)
    static  Elf32_Half running_arch_code=EM_IA_64;
  #elif  (defined __sparc) && (defined _LP64)
    static  Elf32_Half running_arch_code=EM_SPARCV9;
  #elif  (defined __sparc) && (!defined _LP64)
    static  Elf32_Half running_arch_code=EM_SPARC;
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
         IA32, AMD64, IA64, __sparc, __powerpc__, ARM, S390, ALPHA, MIPS, MIPSEL, PARISC, M68K
  #endif

  // Identify compatability class for VM's architecture and library's architecture
  // Obtain string descriptions for architectures

  arch_t lib_arch={elf_head.e_machine,0,elf_head.e_ident[EI_CLASS], elf_head.e_ident[EI_DATA], NULL};
  int running_arch_index=-1;

  for (unsigned int i=0 ; i < ARRAY_SIZE(arch_array) ; i++ ) {
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
    return NULL;
  }

  if (lib_arch.endianess != arch_array[running_arch_index].endianess) {
    ::snprintf(diag_msg_buf, diag_msg_max_length-1," (Possible cause: endianness mismatch)");
    return NULL;
  }

#ifndef S390
  if (lib_arch.elf_class != arch_array[running_arch_index].elf_class) {
    ::snprintf(diag_msg_buf, diag_msg_max_length-1," (Possible cause: architecture word width mismatch)");
    return NULL;
  }
#endif // !S390

  if (lib_arch.compat_class != arch_array[running_arch_index].compat_class) {
    if ( lib_arch.name!=NULL ) {
      ::snprintf(diag_msg_buf, diag_msg_max_length-1,
        " (Possible cause: can't load %s-bit .so on a %s-bit platform)",
        lib_arch.name, arch_array[running_arch_index].name);
    } else {
      ::snprintf(diag_msg_buf, diag_msg_max_length-1,
      " (Possible cause: can't load this .so (machine code=0x%x) on a %s-bit platform)",
        lib_arch.code,
        arch_array[running_arch_index].name);
    }
  }

  return NULL;
}
#endif /* !__APPLE__ */

// XXX: Do we need a lock around this as per Linux?
void* os::dll_lookup(void* handle, const char* name) {
  return dlsym(handle, name);
}


static bool _print_ascii_file(const char* filename, outputStream* st) {
  int fd = ::open(filename, O_RDONLY);
  if (fd == -1) {
     return false;
  }

  char buf[32];
  int bytes;
  while ((bytes = ::read(fd, buf, sizeof(buf))) > 0) {
    st->print_raw(buf, bytes);
  }

  ::close(fd);

  return true;
}

void os::print_dll_info(outputStream *st) {
  st->print_cr("Dynamic libraries:");
#ifdef RTLD_DI_LINKMAP
  Dl_info dli;
  void *handle;
  Link_map *map;
  Link_map *p;

  if (dladdr(CAST_FROM_FN_PTR(void *, os::print_dll_info), &dli) == 0 ||
      dli.dli_fname == NULL) {
    st->print_cr("Error: Cannot print dynamic libraries.");
    return;
  }
  handle = dlopen(dli.dli_fname, RTLD_LAZY);
  if (handle == NULL) {
    st->print_cr("Error: Cannot print dynamic libraries.");
    return;
  }
  dlinfo(handle, RTLD_DI_LINKMAP, &map);
  if (map == NULL) {
    st->print_cr("Error: Cannot print dynamic libraries.");
    return;
  }

  while (map->l_prev != NULL)
    map = map->l_prev;

  while (map != NULL) {
    st->print_cr(PTR_FORMAT " \t%s", map->l_addr, map->l_name);
    map = map->l_next;
  }

  dlclose(handle);
#elif defined(__APPLE__)
  uint32_t count;
  uint32_t i;

  count = _dyld_image_count();
  for (i = 1; i < count; i++) {
    const char *name = _dyld_get_image_name(i);
    intptr_t slide = _dyld_get_image_vmaddr_slide(i);
    st->print_cr(PTR_FORMAT " \t%s", slide, name);
  }
#else
  st->print_cr("Error: Cannot print dynamic libraries.");
#endif
}

void os::print_os_info_brief(outputStream* st) {
  st->print("Bsd");

  os::Posix::print_uname_info(st);
}

void os::print_os_info(outputStream* st) {
  st->print("OS:");
  st->print("Bsd");

  os::Posix::print_uname_info(st);

  os::Posix::print_rlimit_info(st);

  os::Posix::print_load_average(st);
}

void os::pd_print_cpu_info(outputStream* st) {
  // Nothing to do for now.
}

void os::print_memory_info(outputStream* st) {

  st->print("Memory:");
  st->print(" %dk page", os::vm_page_size()>>10);

  st->print(", physical " UINT64_FORMAT "k",
            os::physical_memory() >> 10);
  st->print("(" UINT64_FORMAT "k free)",
            os::available_memory() >> 10);
  st->cr();

  // meminfo
  st->print("\n/proc/meminfo:\n");
  _print_ascii_file("/proc/meminfo", st);
  st->cr();
}

// Taken from /usr/include/bits/siginfo.h  Supposed to be architecture specific
// but they're the same for all the bsd arch that we support
// and they're the same for solaris but there's no common place to put this.
const char *ill_names[] = { "ILL0", "ILL_ILLOPC", "ILL_ILLOPN", "ILL_ILLADR",
                          "ILL_ILLTRP", "ILL_PRVOPC", "ILL_PRVREG",
                          "ILL_COPROC", "ILL_BADSTK" };

const char *fpe_names[] = { "FPE0", "FPE_INTDIV", "FPE_INTOVF", "FPE_FLTDIV",
                          "FPE_FLTOVF", "FPE_FLTUND", "FPE_FLTRES",
                          "FPE_FLTINV", "FPE_FLTSUB", "FPE_FLTDEN" };

const char *segv_names[] = { "SEGV0", "SEGV_MAPERR", "SEGV_ACCERR" };

const char *bus_names[] = { "BUS0", "BUS_ADRALN", "BUS_ADRERR", "BUS_OBJERR" };

void os::print_siginfo(outputStream* st, void* siginfo) {
  st->print("siginfo:");

  const int buflen = 100;
  char buf[buflen];
  siginfo_t *si = (siginfo_t*)siginfo;
  st->print("si_signo=%s: ", os::exception_name(si->si_signo, buf, buflen));
  if (si->si_errno != 0 && strerror_r(si->si_errno, buf, buflen) == 0) {
    st->print("si_errno=%s", buf);
  } else {
    st->print("si_errno=%d", si->si_errno);
  }
  const int c = si->si_code;
  assert(c > 0, "unexpected si_code");
  switch (si->si_signo) {
  case SIGILL:
    st->print(", si_code=%d (%s)", c, c > 8 ? "" : ill_names[c]);
    st->print(", si_addr=" PTR_FORMAT, si->si_addr);
    break;
  case SIGFPE:
    st->print(", si_code=%d (%s)", c, c > 9 ? "" : fpe_names[c]);
    st->print(", si_addr=" PTR_FORMAT, si->si_addr);
    break;
  case SIGSEGV:
    st->print(", si_code=%d (%s)", c, c > 2 ? "" : segv_names[c]);
    st->print(", si_addr=" PTR_FORMAT, si->si_addr);
    break;
  case SIGBUS:
    st->print(", si_code=%d (%s)", c, c > 3 ? "" : bus_names[c]);
    st->print(", si_addr=" PTR_FORMAT, si->si_addr);
    break;
  default:
    st->print(", si_code=%d", si->si_code);
    // no si_addr
  }

  if ((si->si_signo == SIGBUS || si->si_signo == SIGSEGV) &&
      UseSharedSpaces) {
    FileMapInfo* mapinfo = FileMapInfo::current_info();
    if (mapinfo->is_in_shared_space(si->si_addr)) {
      st->print("\n\nError accessing class data sharing archive."   \
                " Mapped file inaccessible during execution, "      \
                " possible disk/network problem.");
    }
  }
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
}

static char saved_jvm_path[MAXPATHLEN] = {0};

// Find the full path to the current module, libjvm
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

  char dli_fname[MAXPATHLEN];
  bool ret = dll_address_to_library_name(
                CAST_FROM_FN_PTR(address, os::jvm_path),
                dli_fname, sizeof(dli_fname), NULL);
  assert(ret, "cannot locate libjvm");
  char *rp = NULL;
  if (ret && dli_fname[0] != '\0') {
    rp = realpath(dli_fname, buf);
  }
  if (rp == NULL)
    return;

  if (Arguments::created_by_gamma_launcher()) {
    // Support for the gamma launcher.  Typical value for buf is
    // "<JAVA_HOME>/jre/lib/<arch>/<vmtype>/libjvm".  If "/jre/lib/" appears at
    // the right place in the string, then assume we are installed in a JDK and
    // we're done.  Otherwise, check for a JAVA_HOME environment variable and
    // construct a path to the JVM being overridden.

    const char *p = buf + strlen(buf) - 1;
    for (int count = 0; p > buf && count < 5; ++count) {
      for (--p; p > buf && *p != '/'; --p)
        /* empty */ ;
    }

    if (strncmp(p, "/jre/lib/", 9) != 0) {
      // Look for JAVA_HOME in the environment.
      char* java_home_var = ::getenv("JAVA_HOME");
      if (java_home_var != NULL && java_home_var[0] != 0) {
        char* jrelib_p;
        int len;

        // Check the current module name "libjvm"
        p = strrchr(buf, '/');
        assert(strstr(p, "/libjvm") == p, "invalid library name");

        rp = realpath(java_home_var, buf);
        if (rp == NULL)
          return;

        // determine if this is a legacy image or modules image
        // modules image doesn't have "jre" subdirectory
        len = strlen(buf);
        jrelib_p = buf + len;

        // Add the appropriate library subdir
        snprintf(jrelib_p, buflen-len, "/jre/lib");
        if (0 != access(buf, F_OK)) {
          snprintf(jrelib_p, buflen-len, "/lib");
        }

        // Add the appropriate client or server subdir
        len = strlen(buf);
        jrelib_p = buf + len;
        snprintf(jrelib_p, buflen-len, "/%s", COMPILER_VARIANT);
        if (0 != access(buf, F_OK)) {
          snprintf(jrelib_p, buflen-len, "");
        }

        // If the path exists within JAVA_HOME, add the JVM library name
        // to complete the path to JVM being overridden.  Otherwise fallback
        // to the path to the current library.
        if (0 == access(buf, F_OK)) {
          // Use current module name "libjvm"
          len = strlen(buf);
          snprintf(buf + len, buflen-len, "/libjvm%s", JNI_LIB_SUFFIX);
        } else {
          // Fall back to path of current library
          rp = realpath(dli_fname, buf);
          if (rp == NULL)
            return;
        }
      }
    }
  }

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

/*
 * The following code is moved from os.cpp for making this
 * code platform specific, which it is by its very nature.
 */

// Will be modified when max signal is changed to be dynamic
int os::sigexitnum_pd() {
  return NSIG;
}

// a counter for each possible signal value
static volatile jint pending_signals[NSIG+1] = { 0 };

// Bsd(POSIX) specific hand shaking semaphore.
#ifdef __APPLE__
typedef semaphore_t os_semaphore_t;
#define SEM_INIT(sem, value)    semaphore_create(mach_task_self(), &sem, SYNC_POLICY_FIFO, value)
#define SEM_WAIT(sem)           semaphore_wait(sem)
#define SEM_POST(sem)           semaphore_signal(sem)
#define SEM_DESTROY(sem)        semaphore_destroy(mach_task_self(), sem)
#else
typedef sem_t os_semaphore_t;
#define SEM_INIT(sem, value)    sem_init(&sem, 0, value)
#define SEM_WAIT(sem)           sem_wait(&sem)
#define SEM_POST(sem)           sem_post(&sem)
#define SEM_DESTROY(sem)        sem_destroy(&sem)
#endif

class Semaphore : public StackObj {
  public:
    Semaphore();
    ~Semaphore();
    void signal();
    void wait();
    bool trywait();
    bool timedwait(unsigned int sec, int nsec);
  private:
    jlong currenttime() const;
    semaphore_t _semaphore;
};

Semaphore::Semaphore() : _semaphore(0) {
  SEM_INIT(_semaphore, 0);
}

Semaphore::~Semaphore() {
  SEM_DESTROY(_semaphore);
}

void Semaphore::signal() {
  SEM_POST(_semaphore);
}

void Semaphore::wait() {
  SEM_WAIT(_semaphore);
}

jlong Semaphore::currenttime() const {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (tv.tv_sec * NANOSECS_PER_SEC) + (tv.tv_usec * 1000);
}

#ifdef __APPLE__
bool Semaphore::trywait() {
  return timedwait(0, 0);
}

bool Semaphore::timedwait(unsigned int sec, int nsec) {
  kern_return_t kr = KERN_ABORTED;
  mach_timespec_t waitspec;
  waitspec.tv_sec = sec;
  waitspec.tv_nsec = nsec;

  jlong starttime = currenttime();

  kr = semaphore_timedwait(_semaphore, waitspec);
  while (kr == KERN_ABORTED) {
    jlong totalwait = (sec * NANOSECS_PER_SEC) + nsec;

    jlong current = currenttime();
    jlong passedtime = current - starttime;

    if (passedtime >= totalwait) {
      waitspec.tv_sec = 0;
      waitspec.tv_nsec = 0;
    } else {
      jlong waittime = totalwait - (current - starttime);
      waitspec.tv_sec = waittime / NANOSECS_PER_SEC;
      waitspec.tv_nsec = waittime % NANOSECS_PER_SEC;
    }

    kr = semaphore_timedwait(_semaphore, waitspec);
  }

  return kr == KERN_SUCCESS;
}

#else

bool Semaphore::trywait() {
  return sem_trywait(&_semaphore) == 0;
}

bool Semaphore::timedwait(unsigned int sec, int nsec) {
  struct timespec ts;
  jlong endtime = unpackTime(&ts, false, (sec * NANOSECS_PER_SEC) + nsec);

  while (1) {
    int result = sem_timedwait(&_semaphore, &ts);
    if (result == 0) {
      return true;
    } else if (errno == EINTR) {
      continue;
    } else if (errno == ETIMEDOUT) {
      return false;
    } else {
      return false;
    }
  }
}

#endif // __APPLE__

static os_semaphore_t sig_sem;
static Semaphore sr_semaphore;

void os::signal_init_pd() {
  // Initialize signal structures
  ::memset((void*)pending_signals, 0, sizeof(pending_signals));

  // Initialize signal semaphore
  ::SEM_INIT(sig_sem, 0);
}

void os::signal_notify(int sig) {
  Atomic::inc(&pending_signals[sig]);
  ::SEM_POST(sig_sem);
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
      ::SEM_WAIT(sig_sem);

      // were we externally suspended while we were waiting?
      threadIsSuspended = thread->handle_special_suspend_equivalent_condition();
      if (threadIsSuspended) {
        //
        // The semaphore has been incremented, but while we were waiting
        // another thread suspended us. We don't want to continue running
        // while suspended because that would surprise the thread that
        // suspended us.
        //
        ::SEM_POST(sig_sem);

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

int os::vm_page_size() {
  // Seems redundant as all get out
  assert(os::Bsd::page_size() != -1, "must call os::init");
  return os::Bsd::page_size();
}

// Solaris allocates memory by pages.
int os::vm_allocation_granularity() {
  assert(os::Bsd::page_size() != -1, "must call os::init");
  return os::Bsd::page_size();
}

// Rationale behind this function:
//  current (Mon Apr 25 20:12:18 MSD 2005) oprofile drops samples without executable
//  mapping for address (see lookup_dcookie() in the kernel module), thus we cannot get
//  samples for JITted code. Here we create private executable mapping over the code cache
//  and then we can use standard (well, almost, as mapping can change) way to provide
//  info for the reporting script by storing timestamp and location of symbol
void bsd_wrap_code(char* base, size_t size) {
  static volatile jint cnt = 0;

  if (!UseOprofile) {
    return;
  }

  char buf[PATH_MAX + 1];
  int num = Atomic::add(1, &cnt);

  snprintf(buf, PATH_MAX + 1, "%s/hs-vm-%d-%d",
           os::get_temp_directory(), os::current_process_id(), num);
  unlink(buf);

  int fd = ::open(buf, O_CREAT | O_RDWR, S_IRWXU);

  if (fd != -1) {
    off_t rv = ::lseek(fd, size-2, SEEK_SET);
    if (rv != (off_t)-1) {
      if (::write(fd, "", 1) == 1) {
        mmap(base, size,
             PROT_READ|PROT_WRITE|PROT_EXEC,
             MAP_PRIVATE|MAP_FIXED|MAP_NORESERVE, fd, 0);
      }
    }
    ::close(fd);
    unlink(buf);
  }
}

static void warn_fail_commit_memory(char* addr, size_t size, bool exec,
                                    int err) {
  warning("INFO: os::commit_memory(" PTR_FORMAT ", " SIZE_FORMAT
          ", %d) failed; error='%s' (errno=%d)", addr, size, exec,
          strerror(err), err);
}

// NOTE: Bsd kernel does not really reserve the pages for us.
//       All it does is to check if there are enough free pages
//       left at the time of mmap(). This could be a potential
//       problem.
bool os::pd_commit_memory(char* addr, size_t size, bool exec) {
  int prot = exec ? PROT_READ|PROT_WRITE|PROT_EXEC : PROT_READ|PROT_WRITE;
#ifdef __OpenBSD__
  // XXX: Work-around mmap/MAP_FIXED bug temporarily on OpenBSD
  if (::mprotect(addr, size, prot) == 0) {
    return true;
  }
#else
  uintptr_t res = (uintptr_t) ::mmap(addr, size, prot,
                                   MAP_PRIVATE|MAP_FIXED|MAP_ANONYMOUS, -1, 0);
  if (res != (uintptr_t) MAP_FAILED) {
    return true;
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
  assert(mesg != NULL, "mesg must be specified");
  if (!pd_commit_memory(addr, size, exec)) {
    // add extra info in product mode for vm_exit_out_of_memory():
    PRODUCT_ONLY(warn_fail_commit_memory(addr, size, exec, errno);)
    vm_exit_out_of_memory(size, OOM_MMAP_ERROR, mesg);
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

void os::pd_free_memory(char *addr, size_t bytes, size_t alignment_hint) {
  ::madvise(addr, bytes, MADV_DONTNEED);
}

void os::numa_make_global(char *addr, size_t bytes) {
}

void os::numa_make_local(char *addr, size_t bytes, int lgrp_hint) {
}

bool os::numa_topology_changed()   { return false; }

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


bool os::pd_uncommit_memory(char* addr, size_t size) {
#ifdef __OpenBSD__
  // XXX: Work-around mmap/MAP_FIXED bug temporarily on OpenBSD
  return ::mprotect(addr, size, PROT_NONE) == 0;
#else
  uintptr_t res = (uintptr_t) ::mmap(addr, size, PROT_NONE,
                MAP_PRIVATE|MAP_FIXED|MAP_NORESERVE|MAP_ANONYMOUS, -1, 0);
  return res  != (uintptr_t) MAP_FAILED;
#endif
}

bool os::pd_create_stack_guard_pages(char* addr, size_t size) {
  return os::commit_memory(addr, size, !ExecMem);
}

// If this is a growable mapping, remove the guard pages entirely by
// munmap()ping them.  If not, just call uncommit_memory().
bool os::remove_stack_guard_pages(char* addr, size_t size) {
  return os::uncommit_memory(addr, size);
}

static address _highest_vm_reserved_address = NULL;

// If 'fixed' is true, anon_mmap() will attempt to reserve anonymous memory
// at 'requested_addr'. If there are existing memory mappings at the same
// location, however, they will be overwritten. If 'fixed' is false,
// 'requested_addr' is only treated as a hint, the return value may or
// may not start from the requested address. Unlike Bsd mmap(), this
// function returns NULL to indicate failure.
static char* anon_mmap(char* requested_addr, size_t bytes, bool fixed) {
  char * addr;
  int flags;

  flags = MAP_PRIVATE | MAP_NORESERVE | MAP_ANONYMOUS;
  if (fixed) {
    assert((uintptr_t)requested_addr % os::Bsd::page_size() == 0, "unaligned address");
    flags |= MAP_FIXED;
  }

  // Map reserved/uncommitted pages PROT_NONE so we fail early if we
  // touch an uncommitted page. Otherwise, the read/write might
  // succeed if we have enough swap space to back the physical page.
  addr = (char*)::mmap(requested_addr, bytes, PROT_NONE,
                       flags, -1, 0);

  if (addr != MAP_FAILED) {
    // anon_mmap() should only get called during VM initialization,
    // don't need lock (actually we can skip locking even it can be called
    // from multiple threads, because _highest_vm_reserved_address is just a
    // hint about the upper limit of non-stack memory regions.)
    if ((address)addr + bytes > _highest_vm_reserved_address) {
      _highest_vm_reserved_address = (address)addr + bytes;
    }
  }

  return addr == MAP_FAILED ? NULL : addr;
}

// Don't update _highest_vm_reserved_address, because there might be memory
// regions above addr + size. If so, releasing a memory region only creates
// a hole in the address space, it doesn't help prevent heap-stack collision.
//
static int anon_munmap(char * addr, size_t size) {
  return ::munmap(addr, size) == 0;
}

char* os::pd_reserve_memory(size_t bytes, char* requested_addr,
                         size_t alignment_hint) {
  return anon_mmap(requested_addr, bytes, (requested_addr != NULL));
}

bool os::pd_release_memory(char* addr, size_t size) {
  return anon_munmap(addr, size);
}

static bool bsd_mprotect(char* addr, size_t size, int prot) {
  // Bsd wants the mprotect address argument to be page aligned.
  char* bottom = (char*)align_size_down((intptr_t)addr, os::Bsd::page_size());

  // According to SUSv3, mprotect() should only be used with mappings
  // established by mmap(), and mmap() always maps whole pages. Unaligned
  // 'addr' likely indicates problem in the VM (e.g. trying to change
  // protection of malloc'ed or statically allocated memory). Check the
  // caller if you hit this assert.
  assert(addr == bottom, "sanity check");

  size = align_size_up(pointer_delta(addr, bottom, 1) + size, os::Bsd::page_size());
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


char* os::reserve_memory_special(size_t bytes, size_t alignment, char* req_addr, bool exec) {
  fatal("This code is not used or maintained.");

  // "exec" is passed in but not used.  Creating the shared image for
  // the code cache doesn't have an SHM_X executable permission to check.
  assert(UseLargePages && UseSHM, "only for SHM large pages");

  key_t key = IPC_PRIVATE;
  char *addr;

  bool warn_on_failure = UseLargePages &&
                        (!FLAG_IS_DEFAULT(UseLargePages) ||
                         !FLAG_IS_DEFAULT(LargePageSizeInBytes)
                        );
  char msg[128];

  // Create a large shared memory region to attach to based on size.
  // Currently, size is the total size of the heap
  int shmid = shmget(key, bytes, IPC_CREAT|SHM_R|SHM_W);
  if (shmid == -1) {
     // Possible reasons for shmget failure:
     // 1. shmmax is too small for Java heap.
     //    > check shmmax value: cat /proc/sys/kernel/shmmax
     //    > increase shmmax value: echo "0xffffffff" > /proc/sys/kernel/shmmax
     // 2. not enough large page memory.
     //    > check available large pages: cat /proc/meminfo
     //    > increase amount of large pages:
     //          echo new_value > /proc/sys/vm/nr_hugepages
     //      Note 1: different Bsd may use different name for this property,
     //            e.g. on Redhat AS-3 it is "hugetlb_pool".
     //      Note 2: it's possible there's enough physical memory available but
     //            they are so fragmented after a long run that they can't
     //            coalesce into large pages. Try to reserve large pages when
     //            the system is still "fresh".
     if (warn_on_failure) {
       jio_snprintf(msg, sizeof(msg), "Failed to reserve shared memory (errno = %d).", errno);
       warning(msg);
     }
     return NULL;
  }

  // attach to the region
  addr = (char*)shmat(shmid, req_addr, 0);
  int err = errno;

  // Remove shmid. If shmat() is successful, the actual shared memory segment
  // will be deleted when it's detached by shmdt() or when the process
  // terminates. If shmat() is not successful this will remove the shared
  // segment immediately.
  shmctl(shmid, IPC_RMID, NULL);

  if ((intptr_t)addr == -1) {
     if (warn_on_failure) {
       jio_snprintf(msg, sizeof(msg), "Failed to attach shared memory (errno = %d).", err);
       warning(msg);
     }
     return NULL;
  }

  // The memory is committed
  MemTracker::record_virtual_memory_reserve_and_commit((address)addr, bytes, mtNone, CALLER_PC);

  return addr;
}

bool os::release_memory_special(char* base, size_t bytes) {
  MemTracker::Tracker tkr = MemTracker::get_virtual_memory_release_tracker();
  // detaching the SHM segment will also delete it, see reserve_memory_special()
  int rslt = shmdt(base);
  if (rslt == 0) {
    tkr.record((address)base, bytes);
    return true;
  } else {
    tkr.discard();
    return false;
  }

}

size_t os::large_page_size() {
  return _large_page_size;
}

// HugeTLBFS allows application to commit large page memory on demand;
// with SysV SHM the entire memory region must be allocated as shared
// memory.
bool os::can_commit_large_page_memory() {
  return UseHugeTLBFS;
}

bool os::can_execute_large_page_memory() {
  return UseHugeTLBFS;
}

// Reserve memory at an arbitrary address, only if that area is
// available (and not reserved for something else).

char* os::pd_attempt_reserve_memory_at(size_t bytes, char* requested_addr) {
  const int max_tries = 10;
  char* base[max_tries];
  size_t size[max_tries];
  const size_t gap = 0x000000;

  // Assert only that the size is a multiple of the page size, since
  // that's all that mmap requires, and since that's all we really know
  // about at this low abstraction level.  If we need higher alignment,
  // we can either pass an alignment to this method or verify alignment
  // in one of the methods further up the call chain.  See bug 5044738.
  assert(bytes % os::vm_page_size() == 0, "reserving unexpected size block");

  // Repeatedly allocate blocks until the block is allocated at the
  // right spot. Give up after max_tries. Note that reserve_memory() will
  // automatically update _highest_vm_reserved_address if the call is
  // successful. The variable tracks the highest memory address every reserved
  // by JVM. It is used to detect heap-stack collision if running with
  // fixed-stack BsdThreads. Because here we may attempt to reserve more
  // space than needed, it could confuse the collision detecting code. To
  // solve the problem, save current _highest_vm_reserved_address and
  // calculate the correct value before return.
  address old_highest = _highest_vm_reserved_address;

  // Bsd mmap allows caller to pass an address as hint; give it a try first,
  // if kernel honors the hint then we can return immediately.
  char * addr = anon_mmap(requested_addr, bytes, false);
  if (addr == requested_addr) {
     return requested_addr;
  }

  if (addr != NULL) {
     // mmap() is successful but it fails to reserve at the requested address
     anon_munmap(addr, bytes);
  }

  int i;
  for (i = 0; i < max_tries; ++i) {
    base[i] = reserve_memory(bytes);

    if (base[i] != NULL) {
      // Is this the block we wanted?
      if (base[i] == requested_addr) {
        size[i] = bytes;
        break;
      }

      // Does this overlap the block we wanted? Give back the overlapped
      // parts and try again.

      size_t top_overlap = requested_addr + (bytes + gap) - base[i];
      if (top_overlap >= 0 && top_overlap < bytes) {
        unmap_memory(base[i], top_overlap);
        base[i] += top_overlap;
        size[i] = bytes - top_overlap;
      } else {
        size_t bottom_overlap = base[i] + bytes - requested_addr;
        if (bottom_overlap >= 0 && bottom_overlap < bytes) {
          unmap_memory(requested_addr, bottom_overlap);
          size[i] = bytes - bottom_overlap;
        } else {
          size[i] = bytes;
        }
      }
    }
  }

  // Give back the unused reserved pieces.

  for (int j = 0; j < i; ++j) {
    if (base[j] != NULL) {
      unmap_memory(base[j], size[j]);
    }
  }

  if (i < max_tries) {
    _highest_vm_reserved_address = MAX2(old_highest, (address)requested_addr + bytes);
    return requested_addr;
  } else {
    _highest_vm_reserved_address = old_highest;
    return NULL;
  }
}

size_t os::read(int fd, void *buf, unsigned int nBytes) {
  RESTARTABLE_RETURN_INT(::read(fd, buf, nBytes));
}

// TODO-FIXME: reconcile Solaris' os::sleep with the bsd variation.
// Solaris uses poll(), bsd uses park().
// Poll() is likely a better choice, assuming that Thread.interrupt()
// generates a SIGUSRx signal. Note that SIGUSR1 can interfere with
// SIGSEGV, see 4355769.

int os::sleep(Thread* thread, jlong millis, bool interruptible) {
  assert(thread == Thread::current(),  "thread consistency check");

  ParkEvent * const slp = thread->_SleepEvent ;
  slp->reset() ;
  OrderAccess::fence() ;

  if (interruptible) {
    jlong prevtime = javaTimeNanos();

    for (;;) {
      if (os::is_interrupted(thread, true)) {
        return OS_INTRPT;
      }

      jlong newtime = javaTimeNanos();

      if (newtime - prevtime < 0) {
        // time moving backwards, should only happen if no monotonic clock
        // not a guarantee() because JVM should not abort on kernel/glibc bugs
        assert(!Bsd::supports_monotonic_clock(), "time moving backwards");
      } else {
        millis -= (newtime - prevtime) / NANOSECS_PER_MILLISEC;
      }

      if(millis <= 0) {
        return OS_OK;
      }

      prevtime = newtime;

      {
        assert(thread->is_Java_thread(), "sanity check");
        JavaThread *jt = (JavaThread *) thread;
        ThreadBlockInVM tbivm(jt);
        OSThreadWaitState osts(jt->osthread(), false /* not Object.wait() */);

        jt->set_suspend_equivalent();
        // cleared by handle_special_suspend_equivalent_condition() or
        // java_suspend_self() via check_and_wait_while_suspended()

        slp->park(millis);

        // were we externally suspended while we were waiting?
        jt->check_and_wait_while_suspended();
      }
    }
  } else {
    OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
    jlong prevtime = javaTimeNanos();

    for (;;) {
      // It'd be nice to avoid the back-to-back javaTimeNanos() calls on
      // the 1st iteration ...
      jlong newtime = javaTimeNanos();

      if (newtime - prevtime < 0) {
        // time moving backwards, should only happen if no monotonic clock
        // not a guarantee() because JVM should not abort on kernel/glibc bugs
        assert(!Bsd::supports_monotonic_clock(), "time moving backwards");
      } else {
        millis -= (newtime - prevtime) / NANOSECS_PER_MILLISEC;
      }

      if(millis <= 0) break ;

      prevtime = newtime;
      slp->park(millis);
    }
    return OS_OK ;
  }
}

int os::naked_sleep() {
  // %% make the sleep time an integer flag. for now use 1 millisec.
  return os::sleep(Thread::current(), 1, false);
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

os::YieldResult os::NakedYield() { sched_yield(); return os::YIELD_UNKNOWN ;}

void os::yield_all(int attempts) {
  // Yields to all threads, including threads with lower priorities
  // Threads on Bsd are all with same priority. The Solaris style
  // os::yield_all() with nanosleep(1ms) is not necessary.
  sched_yield();
}

// Called from the tight loops to possibly influence time-sharing heuristics
void os::loop_breaker(int attempts) {
  os::yield_all(attempts);
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
// It is only used when ThreadPriorityPolicy=1 and requires root privilege.

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
/* Using Mach high-level priority assignments */
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
    // Only root can raise thread priority. Don't allow ThreadPriorityPolicy=1
    // if effective uid is not root. Perhaps, a more elegant way of doing
    // this is to test CAP_SYS_NICE capability, but that will require libcap.so
    if (geteuid() != 0) {
      if (!FLAG_IS_DEFAULT(ThreadPriorityPolicy)) {
        warning("-XX:ThreadPriorityPolicy requires root privilege on Bsd");
      }
      ThreadPriorityPolicy = 0;
    }
  }
  if (UseCriticalJavaThreadPriority) {
    os::java_to_os_priority[MaxPriority] = os::java_to_os_priority[CriticalPriority];
  }
  return 0;
}

OSReturn os::set_native_priority(Thread* thread, int newpri) {
  if ( !UseThreadPriorities || ThreadPriorityPolicy == 0 ) return OS_OK;

#ifdef __OpenBSD__
  // OpenBSD pthread_setprio starves low priority threads
  return OS_OK;
#elif defined(__FreeBSD__)
  int ret = pthread_setprio(thread->osthread()->pthread_id(), newpri);
#elif defined(__APPLE__) || defined(__NetBSD__)
  struct sched_param sp;
  int policy;
  pthread_t self = pthread_self();

  if (pthread_getschedparam(self, &policy, &sp) != 0)
    return OS_ERR;

  sp.sched_priority = newpri;
  if (pthread_setschedparam(self, policy, &sp) != 0)
    return OS_ERR;

  return OS_OK;
#else
  int ret = setpriority(PRIO_PROCESS, thread->osthread()->thread_id(), newpri);
  return (ret == 0) ? OS_OK : OS_ERR;
#endif
}

OSReturn os::get_native_priority(const Thread* const thread, int *priority_ptr) {
  if ( !UseThreadPriorities || ThreadPriorityPolicy == 0 ) {
    *priority_ptr = java_to_os_priority[NormPriority];
    return OS_OK;
  }

  errno = 0;
#if defined(__OpenBSD__) || defined(__FreeBSD__)
  *priority_ptr = pthread_getprio(thread->osthread()->pthread_id());
#elif defined(__APPLE__) || defined(__NetBSD__)
  int policy;
  struct sched_param sp;

  pthread_getschedparam(pthread_self(), &policy, &sp);
  *priority_ptr = sp.sched_priority;
#else
  *priority_ptr = getpriority(PRIO_PROCESS, thread->osthread()->thread_id());
#endif
  return (*priority_ptr != -1 || errno == 0 ? OS_OK : OS_ERR);
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
// Currently only ever called on the VMThread or JavaThread
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

      sr_semaphore.signal();
      // wait here until we are resumed
      while (1) {
        sigsuspend(&suspend_set);

        os::SuspendResume::State result = osthread->sr.running();
        if (result == os::SuspendResume::SR_RUNNING) {
          sr_semaphore.signal();
          break;
        } else if (result != os::SuspendResume::SR_SUSPENDED) {
          ShouldNotReachHere();
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
    // ignore
  }

  errno = old_errno;
}


static int SR_initialize() {
  struct sigaction act;
  char *s;
  /* Get signal number to use for suspend/resume */
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

  /* Set up signal handler for suspend/resume */
  act.sa_flags = SA_RESTART|SA_SIGINFO;
  act.sa_handler = (void (*)(int)) SR_handler;

  // SR_signum is blocked by default.
  // 4528190 - We also need to block pthread restart signal (32 on all
  // supported Bsd platforms). Note that BsdThreads need to block
  // this signal for all threads to work properly. So we don't have
  // to use hard-coded signal number when setting up the mask.
  pthread_sigmask(SIG_BLOCK, NULL, &act.sa_mask);

  if (sigaction(SR_signum, &act, 0) == -1) {
    return -1;
  }

  // Save signal flag
  os::Bsd::set_our_sigflags(SR_signum, act.sa_flags);
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
  assert(!sr_semaphore.trywait(), "semaphore has invalid state");

  // mark as suspended and send signal
  if (osthread->sr.request_suspend() != os::SuspendResume::SR_SUSPEND_REQUEST) {
    // failed to switch, state wasn't running?
    ShouldNotReachHere();
    return false;
  }

  if (sr_notify(osthread) != 0) {
    ShouldNotReachHere();
  }

  // managed to send the signal and switch to SUSPEND_REQUEST, now wait for SUSPENDED
  while (true) {
    if (sr_semaphore.timedwait(0, 2 * NANOSECS_PER_MILLISEC)) {
      break;
    } else {
      // timeout
      os::SuspendResume::State cancelled = osthread->sr.cancel_suspend();
      if (cancelled == os::SuspendResume::SR_RUNNING) {
        return false;
      } else if (cancelled == os::SuspendResume::SR_SUSPENDED) {
        // make sure that we consume the signal on the semaphore as well
        sr_semaphore.wait();
        break;
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
  assert(osthread->sr.is_suspended(), "thread should be suspended");
  assert(!sr_semaphore.trywait(), "invalid semaphore state");

  if (osthread->sr.request_wakeup() != os::SuspendResume::SR_WAKEUP_REQUEST) {
    // failed to switch to WAKEUP_REQUEST
    ShouldNotReachHere();
    return;
  }

  while (true) {
    if (sr_notify(osthread) == 0) {
      if (sr_semaphore.timedwait(0, 2 * NANOSECS_PER_MILLISEC)) {
        if (osthread->sr.is_running()) {
          return;
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
    ParkEvent * const slp = thread->_SleepEvent ;
    if (slp != NULL) slp->unpark() ;
  }

  // For JSR166. Unpark even if interrupt status already was set
  if (thread->is_Java_thread())
    ((JavaThread*)thread)->parker()->unpark();

  ParkEvent * ev = thread->_ParkEvent ;
  if (ev != NULL) ev->unpark() ;

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
// return immediately.  If the flag "abort_if_unrecognized" is true, then this
// routine will never retun false (zero), but instead will execute a VM panic
// routine kill the process.
//
// If this routine returns false, it is OK to call it again.  This allows
// the user-defined signal handler to perform checks either before or after
// the VM performs its own checks.  Naturally, the user code would be making
// a serious error if it tried to handle an exception (such as a null check
// or breakpoint) that the VM was generating for its own correct operation.
//
// This routine may recognize any of the following kinds of signals:
//    SIGBUS, SIGSEGV, SIGILL, SIGFPE, SIGQUIT, SIGPIPE, SIGXFSZ, SIGUSR1.
// It should be consulted by handlers for any of those signals.
//
// The caller of this routine must pass in the three arguments supplied
// to the function referred to in the "sa_sigaction" (not the "sa_handler")
// field of the structure passed to sigaction().  This routine assumes that
// the sa_flags field passed to sigaction() includes SA_SIGINFO and SA_RESTART.
//
// Note that the VM will print warnings if it detects conflicting signal
// handlers, unless invoked with the option "-XX:+AllowUserSignalHandlers".
//
extern "C" JNIEXPORT int
JVM_handle_bsd_signal(int signo, siginfo_t* siginfo,
                        void* ucontext, int abort_if_unrecognized);

void signalHandler(int sig, siginfo_t* info, void* uc) {
  assert(info != NULL && uc != NULL, "it must be old kernel");
  int orig_errno = errno;  // Preserve errno value over signal handler.
  JVM_handle_bsd_signal(sig, info, uc, true);
  errno = orig_errno;
}


// This boolean allows users to forward their own non-matching signals
// to JVM_handle_bsd_signal, harmlessly.
bool os::Bsd::signal_handlers_are_installed = false;

// For signal-chaining
struct sigaction os::Bsd::sigact[MAXSIGNUM];
unsigned int os::Bsd::sigs = 0;
bool os::Bsd::libjsig_is_loaded = false;
typedef struct sigaction *(*get_signal_t)(int);
get_signal_t os::Bsd::get_signal_action = NULL;

struct sigaction* os::Bsd::get_chained_signal_action(int sig) {
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

    sa_handler_t hand;
    sa_sigaction_t sa;
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

bool os::Bsd::chained_handler(int sig, siginfo_t* siginfo, void* context) {
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

struct sigaction* os::Bsd::get_preinstalled_handler(int sig) {
  if ((( (unsigned int)1 << sig ) & sigs) != 0) {
    return &sigact[sig];
  }
  return NULL;
}

void os::Bsd::save_preinstalled_handler(int sig, struct sigaction& oldAct) {
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  sigact[sig] = oldAct;
  sigs |= (unsigned int)1 << sig;
}

// for diagnostic
int os::Bsd::sigflags[MAXSIGNUM];

int os::Bsd::get_our_sigflags(int sig) {
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  return sigflags[sig];
}

void os::Bsd::set_our_sigflags(int sig, int flags) {
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  sigflags[sig] = flags;
}

void os::Bsd::set_signal_handler(int sig, bool set_installed) {
  // Check for overwrite.
  struct sigaction oldAct;
  sigaction(sig, (struct sigaction*)NULL, &oldAct);

  void* oldhand = oldAct.sa_sigaction
                ? CAST_FROM_FN_PTR(void*,  oldAct.sa_sigaction)
                : CAST_FROM_FN_PTR(void*,  oldAct.sa_handler);
  if (oldhand != CAST_FROM_FN_PTR(void*, SIG_DFL) &&
      oldhand != CAST_FROM_FN_PTR(void*, SIG_IGN) &&
      oldhand != CAST_FROM_FN_PTR(void*, (sa_sigaction_t)signalHandler)) {
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
  sigAct.sa_handler = SIG_DFL;
  if (!set_installed) {
    sigAct.sa_flags = SA_SIGINFO|SA_RESTART;
  } else {
    sigAct.sa_sigaction = signalHandler;
    sigAct.sa_flags = SA_SIGINFO|SA_RESTART;
  }
#if __APPLE__
  // Needed for main thread as XNU (Mac OS X kernel) will only deliver SIGSEGV
  // (which starts as SIGBUS) on main thread with faulting address inside "stack+guard pages"
  // if the signal handler declares it will handle it on alternate stack.
  // Notice we only declare we will handle it on alt stack, but we are not
  // actually going to use real alt stack - this is just a workaround.
  // Please see ux_exception.c, method catch_mach_exception_raise for details
  // link http://www.opensource.apple.com/source/xnu/xnu-2050.18.24/bsd/uxkern/ux_exception.c
  if (sig == SIGSEGV) {
    sigAct.sa_flags |= SA_ONSTACK;
  }
#endif

  // Save flags, which are set by ours
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  sigflags[sig] = sigAct.sa_flags;

  int ret = sigaction(sig, &sigAct, &oldAct);
  assert(ret == 0, "check");

  void* oldhand2  = oldAct.sa_sigaction
                  ? CAST_FROM_FN_PTR(void*, oldAct.sa_sigaction)
                  : CAST_FROM_FN_PTR(void*, oldAct.sa_handler);
  assert(oldhand2 == oldhand, "no concurrent signal handler installation");
}

// install signal handlers for signals that HotSpot needs to
// handle in order to support Java-level exception handling.

void os::Bsd::install_signal_handlers() {
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
    set_signal_handler(SIGXFSZ, true);

#if defined(__APPLE__)
    // In Mac OS X 10.4, CrashReporter will write a crash log for all 'fatal' signals, including
    // signals caught and handled by the JVM. To work around this, we reset the mach task
    // signal handler that's placed on our process by CrashReporter. This disables
    // CrashReporter-based reporting.
    //
    // This work-around is not necessary for 10.5+, as CrashReporter no longer intercedes
    // on caught fatal signals.
    //
    // Additionally, gdb installs both standard BSD signal handlers, and mach exception
    // handlers. By replacing the existing task exception handler, we disable gdb's mach
    // exception handling, while leaving the standard BSD signal handlers functional.
    kern_return_t kr;
    kr = task_set_exception_ports(mach_task_self(),
        EXC_MASK_BAD_ACCESS | EXC_MASK_ARITHMETIC,
        MACH_PORT_NULL,
        EXCEPTION_STATE_IDENTITY,
        MACHINE_THREAD_STATE);

    assert(kr == KERN_SUCCESS, "could not set mach task signal handler");
#endif

    if (libjsig_is_loaded) {
      // Tell libjsig jvm finishes setting signal handlers
      (*end_signal_setting)();
    }

    // We don't activate signal checker if libjsig is in place, we trust ourselves
    // and if UserSignalHandler is installed all bets are off
    if (CheckJNICalls) {
      if (libjsig_is_loaded) {
        if (PrintJNIResolving) {
          tty->print_cr("Info: libjsig is activated, all active signal checking is disabled");
        }
        check_signals = false;
      }
      if (AllowUserSignalHandlers) {
        if (PrintJNIResolving) {
          tty->print_cr("Info: AllowUserSignalHandlers is activated, all active signal checking is disabled");
        }
        check_signals = false;
      }
    }
  }
}


/////
// glibc on Bsd platform uses non-documented flag
// to indicate, that some special sort of signal
// trampoline is used.
// We will never set this flag, and we should
// ignore this flag in our diagnostic
#ifdef SIGNIFICANT_SIGNAL_MASK
#undef SIGNIFICANT_SIGNAL_MASK
#endif
#define SIGNIFICANT_SIGNAL_MASK (~0x04000000)

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
    jio_snprintf(buf, buflen, "%s+0x%x", p1, offset);
  } else {
    jio_snprintf(buf, buflen, PTR_FORMAT, handler);
  }
  return buf;
}

static void print_signal_handler(outputStream* st, int sig,
                                 char* buf, size_t buflen) {
  struct sigaction sa;

  sigaction(sig, NULL, &sa);

  // See comment for SIGNIFICANT_SIGNAL_MASK define
  sa.sa_flags &= SIGNIFICANT_SIGNAL_MASK;

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

  st->print(", sa_mask[0]=" PTR32_FORMAT, *(uint32_t*)&sa.sa_mask);

  address rh = VMError::get_resetted_sighandler(sig);
  // May be, handler was resetted by VMError?
  if(rh != NULL) {
    handler = rh;
    sa.sa_flags = VMError::get_resetted_sigflags(sig) & SIGNIFICANT_SIGNAL_MASK;
  }

  st->print(", sa_flags="   PTR32_FORMAT, sa.sa_flags);

  // Check: is it our handler?
  if(handler == CAST_FROM_FN_PTR(address, (sa_sigaction_t)signalHandler) ||
     handler == CAST_FROM_FN_PTR(address, (sa_sigaction_t)SR_handler)) {
    // It is our signal handler
    // check for flags, reset system-used one!
    if((int)sa.sa_flags != os::Bsd::get_our_sigflags(sig)) {
      st->print(
                ", flags was changed from " PTR32_FORMAT ", consider using jsig library",
                os::Bsd::get_our_sigflags(sig));
    }
  }
  st->cr();
}


#define DO_SIGNAL_CHECK(sig) \
  if (!sigismember(&check_signal_done, sig)) \
    os::Bsd::check_signal_handler(sig)

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

void os::Bsd::check_signal_handler(int sig) {
  char buf[O_BUFLEN];
  address jvmHandler = NULL;


  struct sigaction act;
  if (os_sigaction == NULL) {
    // only trust the default sigaction, in case it has been interposed
    os_sigaction = (os_sigaction_t)dlsym(RTLD_DEFAULT, "sigaction");
    if (os_sigaction == NULL) return;
  }

  os_sigaction(sig, (struct sigaction*)NULL, &act);


  act.sa_flags &= SIGNIFICANT_SIGNAL_MASK;

  address thisHandler = (act.sa_flags & SA_SIGINFO)
    ? CAST_FROM_FN_PTR(address, act.sa_sigaction)
    : CAST_FROM_FN_PTR(address, act.sa_handler) ;


  switch(sig) {
  case SIGSEGV:
  case SIGBUS:
  case SIGFPE:
  case SIGPIPE:
  case SIGILL:
  case SIGXFSZ:
    jvmHandler = CAST_FROM_FN_PTR(address, (sa_sigaction_t)signalHandler);
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
  } else if(os::Bsd::get_our_sigflags(sig) != 0 && (int)act.sa_flags != os::Bsd::get_our_sigflags(sig)) {
    tty->print("Warning: %s handler flags ", exception_name(sig, buf, O_BUFLEN));
    tty->print("expected:" PTR32_FORMAT, os::Bsd::get_our_sigflags(sig));
    tty->print_cr("  found:" PTR32_FORMAT, act.sa_flags);
    // No need to check this sig any longer
    sigaddset(&check_signal_done, sig);
  }

  // Dump all the signal
  if (sigismember(&check_signal_done, sig)) {
    print_signal_handlers(tty, buf, O_BUFLEN);
  }
}

extern void report_error(char* file_name, int line_no, char* title, char* format, ...);

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

// this is called _before_ the most of global arguments have been parsed
void os::init(void) {
  char dummy;   /* used to get a guess on initial stack address */
//  first_hrtime = gethrtime();

  // With BsdThreads the JavaMain thread pid (primordial thread)
  // is different than the pid of the java launcher thread.
  // So, on Bsd, the launcher thread pid is passed to the VM
  // via the sun.java.launcher.pid property.
  // Use this property instead of getpid() if it was correctly passed.
  // See bug 6351349.
  pid_t java_launcher_pid = (pid_t) Arguments::sun_java_launcher_pid();

  _initial_pid = (java_launcher_pid > 0) ? java_launcher_pid : getpid();

  clock_tics_per_sec = CLK_TCK;

  init_random(1234567);

  ThreadCritical::initialize();

  Bsd::set_page_size(getpagesize());
  if (Bsd::page_size() == -1) {
    fatal(err_msg("os_bsd.cpp: os::init: sysconf failed (%s)",
                  strerror(errno)));
  }
  init_page_sizes((size_t) Bsd::page_size());

  Bsd::initialize_system_info();

  // main_thread points to the aboriginal thread
  Bsd::_main_thread = pthread_self();

  Bsd::clock_init();
  initial_time_count = os::elapsed_counter();

#ifdef __APPLE__
  // XXXDARWIN
  // Work around the unaligned VM callbacks in hotspot's
  // sharedRuntime. The callbacks don't use SSE2 instructions, and work on
  // Linux, Solaris, and FreeBSD. On Mac OS X, dyld (rightly so) enforces
  // alignment when doing symbol lookup. To work around this, we force early
  // binding of all symbols now, thus binding when alignment is known-good.
  _dyld_bind_fully_image_containing_address((const void *) &os::init);
#endif
}

// To install functions for atexit system call
extern "C" {
  static void perfMemory_exit_helper() {
    perfMemory_exit();
  }
}

// this is called _after_ the global arguments have been parsed
jint os::init_2(void)
{
  // Allocate a single page and mark it as readable for safepoint polling
  address polling_page = (address) ::mmap(NULL, Bsd::page_size(), PROT_READ, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0);
  guarantee( polling_page != MAP_FAILED, "os::init_2: failed to allocate polling page" );

  os::set_polling_page( polling_page );

#ifndef PRODUCT
  if(Verbose && PrintMiscellaneous)
    tty->print("[SafePoint Polling address: " INTPTR_FORMAT "]\n", (intptr_t)polling_page);
#endif

  if (!UseMembar) {
    address mem_serialize_page = (address) ::mmap(NULL, Bsd::page_size(), PROT_READ | PROT_WRITE, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0);
    guarantee( mem_serialize_page != MAP_FAILED, "mmap Failed for memory serialize page");
    os::set_memory_serialize_page( mem_serialize_page );

#ifndef PRODUCT
    if(Verbose && PrintMiscellaneous)
      tty->print("[Memory Serialize  Page address: " INTPTR_FORMAT "]\n", (intptr_t)mem_serialize_page);
#endif
  }

  // initialize suspend/resume support - must do this before signal_sets_init()
  if (SR_initialize() != 0) {
    perror("SR_initialize failed");
    return JNI_ERR;
  }

  Bsd::signal_sets_init();
  Bsd::install_signal_handlers();

  // Check minimum allowable stack size for thread creation and to initialize
  // the java system classes, including StackOverflowError - depends on page
  // size.  Add a page for compiler2 recursion in main thread.
  // Add in 2*BytesPerWord times page size to account for VM stack during
  // class initialization depending on 32 or 64 bit VM.
  os::Bsd::min_stack_allowed = MAX2(os::Bsd::min_stack_allowed,
            (size_t)(StackYellowPages+StackRedPages+StackShadowPages+
                    2*BytesPerWord COMPILER2_PRESENT(+1)) * Bsd::page_size());

  size_t threadStackSizeInBytes = ThreadStackSize * K;
  if (threadStackSizeInBytes != 0 &&
      threadStackSizeInBytes < os::Bsd::min_stack_allowed) {
        tty->print_cr("\nThe stack size specified is too small, "
                      "Specify at least %dk",
                      os::Bsd::min_stack_allowed/ K);
        return JNI_ERR;
  }

  // Make the stack size a multiple of the page size so that
  // the yellow/red zones can be guarded.
  JavaThread::set_stack_size_at_create(round_to(threadStackSizeInBytes,
        vm_page_size()));

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

#ifdef __APPLE__
      // Darwin returns RLIM_INFINITY for rlim_max, but fails with EINVAL if
      // you attempt to use RLIM_INFINITY. As per setrlimit(2), OPEN_MAX must
      // be used instead
      nbr_files.rlim_cur = MIN(OPEN_MAX, nbr_files.rlim_cur);
#endif

      status = setrlimit(RLIMIT_NOFILE, &nbr_files);
      if (status != 0) {
        if (PrintMiscellaneous && (Verbose || WizardMode))
          perror("os::init_2 setrlimit failed");
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
      warning("os::init2 atexit(perfMemory_exit_helper) failed");
    }
  }

  // initialize thread priority policy
  prio_init();

#ifdef __APPLE__
  // dynamically link to objective c gc registration
  void *handleLibObjc = dlopen(OBJC_LIB, RTLD_LAZY);
  if (handleLibObjc != NULL) {
    objc_registerThreadWithCollectorFunction = (objc_registerThreadWithCollector_t) dlsym(handleLibObjc, OBJC_GCREGISTER);
  }
#endif

  return JNI_OK;
}

// this is called at the end of vm_initialization
void os::init_3(void) { }

// Mark the polling page as unreadable
void os::make_polling_page_unreadable(void) {
  if( !guard_memory((char*)_polling_page, Bsd::page_size()) )
    fatal("Could not disable polling page");
};

// Mark the polling page as readable
void os::make_polling_page_readable(void) {
  if( !bsd_mprotect((char *)_polling_page, Bsd::page_size(), PROT_READ)) {
    fatal("Could not enable polling page");
  }
};

int os::active_processor_count() {
  return _processor_count;
}

void os::set_native_thread_name(const char *name) {
#if defined(__APPLE__) && MAC_OS_X_VERSION_MIN_REQUIRED > MAC_OS_X_VERSION_10_5
  // This is only supported in Snow Leopard and beyond
  if (name != NULL) {
    // Add a "Java: " prefix to the name
    char buf[MAXTHREADNAMESIZE];
    snprintf(buf, sizeof(buf), "Java: %s", name);
    pthread_setname_np(buf);
  }
#endif
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

///
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
    _epc = os::Bsd::ucontext_get_pc((ucontext_t *) context.ucontext());
  } else {
    // NULL context is unexpected, double-check this is the VMThread
    guarantee(thread->is_VM_thread(), "can only be called for VMThread");
  }
}

// Suspends the target using the signal mechanism and then grabs the PC before
// resuming the target. Used by the flat-profiler only
ExtendedPC os::get_thread_pc(Thread* thread) {
  // Make sure that it is called by the watcher for the VMThread
  assert(Thread::current()->is_Watcher_thread(), "Must be watcher");
  assert(thread->is_VM_thread(), "Can only be called for VMThread");

  PcFetcher fetcher(thread);
  fetcher.run();
  return fetcher.result();
}

int os::Bsd::safe_cond_timedwait(pthread_cond_t *_cond, pthread_mutex_t *_mutex, const struct timespec *_abstime)
{
  return pthread_cond_timedwait(_cond, _mutex, _abstime);
}

////////////////////////////////////////////////////////////////////////////////
// debug support

bool os::find(address addr, outputStream* st) {
  Dl_info dlinfo;
  memset(&dlinfo, 0, sizeof(dlinfo));
  if (dladdr(addr, &dlinfo) != 0) {
    st->print(PTR_FORMAT ": ", addr);
    if (dlinfo.dli_sname != NULL && dlinfo.dli_saddr != NULL) {
      st->print("%s+%#x", dlinfo.dli_sname,
                 addr - (intptr_t)dlinfo.dli_saddr);
    } else if (dlinfo.dli_fbase != NULL) {
      st->print("<offset %#x>", addr - (intptr_t)dlinfo.dli_fbase);
    } else {
      st->print("<absolute address>");
    }
    if (dlinfo.dli_fname != NULL) {
      st->print(" in %s", dlinfo.dli_fname);
    }
    if (dlinfo.dli_fbase != NULL) {
      st->print(" at " PTR_FORMAT, dlinfo.dli_fbase);
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
          && end > dlinfo2.dli_saddr && dlinfo2.dli_saddr > begin)
        end = (address) dlinfo2.dli_saddr;
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

int local_vsnprintf(char* buf, size_t count, const char* format, va_list args) {
  return ::vsnprintf(buf, count, format, args);
}

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

  fd = ::open(path, oflag, mode);
  if (fd == -1) return -1;

  //If the open succeeded, the file might still be a directory
  {
    struct stat buf;
    int ret = ::fstat(fd, &buf);
    int st_mode = buf.st_mode;

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

    /*
     * All file descriptors that are opened in the JVM and not
     * specifically destined for a subprocess should have the
     * close-on-exec flag set.  If we don't set it, then careless 3rd
     * party native code might fork and exec without closing all
     * appropriate file descriptors (e.g. as we do in closeDescriptors in
     * UNIXProcess.c), and this in turn might:
     *
     * - cause end-of-file to fail to be detected on some file
     *   descriptors, resulting in mysterious hangs, or
     *
     * - might cause an fopen in the subprocess to fail on a system
     *   suffering from bug 1085341.
     *
     * (Yes, the default setting of the close-on-exec flag is a Unix
     * design flaw)
     *
     * See:
     * 1085341: 32-bit stdio routines should support file descriptors >255
     * 4843136: (process) pipe file descriptor from Runtime.exec not being closed
     * 6339493: (process) Runtime.exec does not close all file descriptors on Solaris 9
     */
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
  return ::open(path, oflags, S_IREAD | S_IWRITE);
}

// return current position of file pointer
jlong os::current_file_offset(int fd) {
  return (jlong)::lseek(fd, (off_t)0, SEEK_CUR);
}

// move file pointer to the specified offset
jlong os::seek_to_file_offset(int fd, jlong offset) {
  return (jlong)::lseek(fd, (off_t)offset, SEEK_SET);
}

// This code originates from JDK's sysAvailable
// from src/solaris/hpi/src/native_threads/src/sys_api_td.c

int os::available(int fd, jlong *bytes) {
  jlong cur, end;
  int mode;
  struct stat buf;

  if (::fstat(fd, &buf) >= 0) {
    mode = buf.st_mode;
    if (S_ISCHR(mode) || S_ISFIFO(mode) || S_ISSOCK(mode)) {
      /*
      * XXX: is the following call interruptible? If so, this might
      * need to go through the INTERRUPT_IO() wrapper as for other
      * blocking, interruptible calls in this file.
      */
      int n;
      if (::ioctl(fd, FIONREAD, &n) >= 0) {
        *bytes = n;
        return 1;
      }
    }
  }
  if ((cur = ::lseek(fd, 0L, SEEK_CUR)) == -1) {
    return 0;
  } else if ((end = ::lseek(fd, 0L, SEEK_END)) == -1) {
    return 0;
  } else if (::lseek(fd, cur, SEEK_SET) == -1) {
    return 0;
  }
  *bytes = end - cur;
  return 1;
}

int os::socket_available(int fd, jint *pbytes) {
   if (fd < 0)
     return OS_OK;

   int ret;

   RESTARTABLE(::ioctl(fd, FIONREAD, pbytes), ret);

   //%% note ioctl can return 0 when successful, JVM_SocketAvailable
   // is expected to return 0 on failure and 1 on success to the jdk.

   return (ret == OS_ERR) ? 0 : 1;
}

// Map a block of memory.
char* os::pd_map_memory(int fd, const char* file_name, size_t file_offset,
                     char *addr, size_t bytes, bool read_only,
                     bool allow_exec) {
  int prot;
  int flags;

  if (read_only) {
    prot = PROT_READ;
    flags = MAP_SHARED;
  } else {
    prot = PROT_READ | PROT_WRITE;
    flags = MAP_PRIVATE;
  }

  if (allow_exec) {
    prot |= PROT_EXEC;
  }

  if (addr != NULL) {
    flags |= MAP_FIXED;
  }

  char* mapped_address = (char*)mmap(addr, (size_t)bytes, prot, flags,
                                     fd, file_offset);
  if (mapped_address == MAP_FAILED) {
    return NULL;
  }
  return mapped_address;
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
  if (kr != KERN_SUCCESS)
    return -1;

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


// Refer to the comments in os_solaris.cpp park-unpark.
//
// Beware -- Some versions of NPTL embody a flaw where pthread_cond_timedwait() can
// hang indefinitely.  For instance NPTL 0.60 on 2.4.21-4ELsmp is vulnerable.
// For specifics regarding the bug see GLIBC BUGID 261237 :
//    http://www.mail-archive.com/debian-glibc@lists.debian.org/msg10837.html.
// Briefly, pthread_cond_timedwait() calls with an expiry time that's not in the future
// will either hang or corrupt the condvar, resulting in subsequent hangs if the condvar
// is used.  (The simple C test-case provided in the GLIBC bug report manifests the
// hang).  The JVM is vulernable via sleep(), Object.wait(timo), LockSupport.parkNanos()
// and monitorenter when we're using 1-0 locking.  All those operations may result in
// calls to pthread_cond_timedwait().  Using LD_ASSUME_KERNEL to use an older version
// of libpthread avoids the problem, but isn't practical.
//
// Possible remedies:
//
// 1.   Establish a minimum relative wait time.  50 to 100 msecs seems to work.
//      This is palliative and probabilistic, however.  If the thread is preempted
//      between the call to compute_abstime() and pthread_cond_timedwait(), more
//      than the minimum period may have passed, and the abstime may be stale (in the
//      past) resultin in a hang.   Using this technique reduces the odds of a hang
//      but the JVM is still vulnerable, particularly on heavily loaded systems.
//
// 2.   Modify park-unpark to use per-thread (per ParkEvent) pipe-pairs instead
//      of the usual flag-condvar-mutex idiom.  The write side of the pipe is set
//      NDELAY. unpark() reduces to write(), park() reduces to read() and park(timo)
//      reduces to poll()+read().  This works well, but consumes 2 FDs per extant
//      thread.
//
// 3.   Embargo pthread_cond_timedwait() and implement a native "chron" thread
//      that manages timeouts.  We'd emulate pthread_cond_timedwait() by enqueuing
//      a timeout request to the chron thread and then blocking via pthread_cond_wait().
//      This also works well.  In fact it avoids kernel-level scalability impediments
//      on certain platforms that don't handle lots of active pthread_cond_timedwait()
//      timers in a graceful fashion.
//
// 4.   When the abstime value is in the past it appears that control returns
//      correctly from pthread_cond_timedwait(), but the condvar is left corrupt.
//      Subsequent timedwait/wait calls may hang indefinitely.  Given that, we
//      can avoid the problem by reinitializing the condvar -- by cond_destroy()
//      followed by cond_init() -- after all calls to pthread_cond_timedwait().
//      It may be possible to avoid reinitialization by checking the return
//      value from pthread_cond_timedwait().  In addition to reinitializing the
//      condvar we must establish the invariant that cond_signal() is only called
//      within critical sections protected by the adjunct mutex.  This prevents
//      cond_signal() from "seeing" a condvar that's in the midst of being
//      reinitialized or that is corrupt.  Sadly, this invariant obviates the
//      desirable signal-after-unlock optimization that avoids futile context switching.
//
//      I'm also concerned that some versions of NTPL might allocate an auxilliary
//      structure when a condvar is used or initialized.  cond_destroy()  would
//      release the helper structure.  Our reinitialize-after-timedwait fix
//      put excessive stress on malloc/free and locks protecting the c-heap.
//
// We currently use (4).  See the WorkAroundNTPLTimedWaitHang flag.
// It may be possible to refine (4) by checking the kernel and NTPL verisons
// and only enabling the work-around for vulnerable environments.

// utility to compute the abstime argument to timedwait:
// millis is the relative timeout time
// abstime will be the absolute timeout time
// TODO: replace compute_abstime() with unpackTime()

static struct timespec* compute_abstime(struct timespec* abstime, jlong millis) {
  if (millis < 0)  millis = 0;
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
    const int v = _Event ;
    guarantee ((v == 0) || (v == 1), "invariant") ;
    if (Atomic::cmpxchg (0, &_Event, v) == v) return v  ;
  }
}

void os::PlatformEvent::park() {       // AKA "down()"
  // Invariant: Only the thread associated with the Event/PlatformEvent
  // may call park().
  // TODO: assert that _Assoc != NULL or _Assoc == Self
  int v ;
  for (;;) {
      v = _Event ;
      if (Atomic::cmpxchg (v-1, &_Event, v) == v) break ;
  }
  guarantee (v >= 0, "invariant") ;
  if (v == 0) {
     // Do this the hard way by blocking ...
     int status = pthread_mutex_lock(_mutex);
     assert_status(status == 0, status, "mutex_lock");
     guarantee (_nParked == 0, "invariant") ;
     ++ _nParked ;
     while (_Event < 0) {
        status = pthread_cond_wait(_cond, _mutex);
        // for some reason, under 2.7 lwp_cond_wait() may return ETIME ...
        // Treat this the same as if the wait was interrupted
        if (status == ETIMEDOUT) { status = EINTR; }
        assert_status(status == 0 || status == EINTR, status, "cond_wait");
     }
     -- _nParked ;

    _Event = 0 ;
     status = pthread_mutex_unlock(_mutex);
     assert_status(status == 0, status, "mutex_unlock");
    // Paranoia to ensure our locked and lock-free paths interact
    // correctly with each other.
    OrderAccess::fence();
  }
  guarantee (_Event >= 0, "invariant") ;
}

int os::PlatformEvent::park(jlong millis) {
  guarantee (_nParked == 0, "invariant") ;

  int v ;
  for (;;) {
      v = _Event ;
      if (Atomic::cmpxchg (v-1, &_Event, v) == v) break ;
  }
  guarantee (v >= 0, "invariant") ;
  if (v != 0) return OS_OK ;

  // We do this the hard way, by blocking the thread.
  // Consider enforcing a minimum timeout value.
  struct timespec abst;
  compute_abstime(&abst, millis);

  int ret = OS_TIMEOUT;
  int status = pthread_mutex_lock(_mutex);
  assert_status(status == 0, status, "mutex_lock");
  guarantee (_nParked == 0, "invariant") ;
  ++_nParked ;

  // Object.wait(timo) will return because of
  // (a) notification
  // (b) timeout
  // (c) thread.interrupt
  //
  // Thread.interrupt and object.notify{All} both call Event::set.
  // That is, we treat thread.interrupt as a special case of notification.
  // The underlying Solaris implementation, cond_timedwait, admits
  // spurious/premature wakeups, but the JLS/JVM spec prevents the
  // JVM from making those visible to Java code.  As such, we must
  // filter out spurious wakeups.  We assume all ETIME returns are valid.
  //
  // TODO: properly differentiate simultaneous notify+interrupt.
  // In that case, we should propagate the notify to another waiter.

  while (_Event < 0) {
    status = os::Bsd::safe_cond_timedwait(_cond, _mutex, &abst);
    if (status != 0 && WorkAroundNPTLTimedWaitHang) {
      pthread_cond_destroy (_cond);
      pthread_cond_init (_cond, NULL) ;
    }
    assert_status(status == 0 || status == EINTR ||
                  status == ETIMEDOUT,
                  status, "cond_timedwait");
    if (!FilterSpuriousWakeups) break ;                 // previous semantics
    if (status == ETIMEDOUT) break ;
    // We consume and ignore EINTR and spurious wakeups.
  }
  --_nParked ;
  if (_Event >= 0) {
     ret = OS_OK;
  }
  _Event = 0 ;
  status = pthread_mutex_unlock(_mutex);
  assert_status(status == 0, status, "mutex_unlock");
  assert (_nParked == 0, "invariant") ;
  // Paranoia to ensure our locked and lock-free paths interact
  // correctly with each other.
  OrderAccess::fence();
  return ret;
}

void os::PlatformEvent::unpark() {
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

  // Wait for the thread associated with the event to vacate
  int status = pthread_mutex_lock(_mutex);
  assert_status(status == 0, status, "mutex_lock");
  int AnyWaiters = _nParked;
  assert(AnyWaiters == 0 || AnyWaiters == 1, "invariant");
  if (AnyWaiters != 0 && WorkAroundNPTLTimedWaitHang) {
    AnyWaiters = 0;
    pthread_cond_signal(_cond);
  }
  status = pthread_mutex_unlock(_mutex);
  assert_status(status == 0, status, "mutex_unlock");
  if (AnyWaiters != 0) {
    status = pthread_cond_signal(_cond);
    assert_status(status == 0, status, "cond_signal");
  }

  // Note that we signal() _after dropping the lock for "immortal" Events.
  // This is safe and avoids a common class of  futile wakeups.  In rare
  // circumstances this can cause a thread to return prematurely from
  // cond_{timed}wait() but the spurious wakeup is benign and the victim will
  // simply re-test the condition and re-park itself.
}


// JSR166
// -------------------------------------------------------

/*
 * The solaris and bsd implementations of park/unpark are fairly
 * conservative for now, but can be improved. They currently use a
 * mutex/condvar pair, plus a a count.
 * Park decrements count if > 0, else does a condvar wait.  Unpark
 * sets count to 1 and signals condvar.  Only one thread ever waits
 * on the condvar. Contention seen when trying to park implies that someone
 * is unparking you, so don't wait. And spurious returns are fine, so there
 * is no need to track notifications.
 */

#define MAX_SECS 100000000
/*
 * This code is common to bsd and solaris and will be moved to a
 * common place in dolphin.
 *
 * The passed in time value is either a relative time in nanoseconds
 * or an absolute time in milliseconds. Either way it has to be unpacked
 * into suitable seconds and nanoseconds components and stored in the
 * given timespec structure.
 * Given time is a 64-bit value and the time_t used in the timespec is only
 * a signed-32-bit value (except on 64-bit Bsd) we have to watch for
 * overflow if times way in the future are given. Further on Solaris versions
 * prior to 10 there is a restriction (see cond_timedwait) that the specified
 * number of seconds, in abstime, is less than current_time  + 100,000,000.
 * As it will be 28 years before "now + 100000000" will overflow we can
 * ignore overflow and just impose a hard-limit on seconds using the value
 * of "now + 100,000,000". This places a limit on the timeout of about 3.17
 * years from "now".
 */

static void unpackTime(struct timespec* absTime, bool isAbsolute, jlong time) {
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
  // Ideally we'd do something useful while spinning, such
  // as calling unpackTime().

  // Optional fast-path check:
  // Return immediately if a permit is available.
  // We depend on Atomic::xchg() having full barrier semantics
  // since we are doing a lock-free update to _counter.
  if (Atomic::xchg(0, &_counter) > 0) return;

  Thread* thread = Thread::current();
  assert(thread->is_Java_thread(), "Must be JavaThread");
  JavaThread *jt = (JavaThread *)thread;

  // Optional optimization -- avoid state transitions if there's an interrupt pending.
  // Check interrupt before trying to wait
  if (Thread::is_interrupted(thread, false)) {
    return;
  }

  // Next, demultiplex/decode time arguments
  struct timespec absTime;
  if (time < 0 || (isAbsolute && time == 0) ) { // don't wait at all
    return;
  }
  if (time > 0) {
    unpackTime(&absTime, isAbsolute, time);
  }


  // Enter safepoint region
  // Beware of deadlocks such as 6317397.
  // The per-thread Parker:: mutex is a classic leaf-lock.
  // In particular a thread must never block on the Threads_lock while
  // holding the Parker:: mutex.  If safepoints are pending both the
  // the ThreadBlockInVM() CTOR and DTOR may grab Threads_lock.
  ThreadBlockInVM tbivm(jt);

  // Don't wait if cannot get lock since interference arises from
  // unblocking.  Also. check interrupt before trying wait
  if (Thread::is_interrupted(thread, false) || pthread_mutex_trylock(_mutex) != 0) {
    return;
  }

  int status ;
  if (_counter > 0)  { // no wait needed
    _counter = 0;
    status = pthread_mutex_unlock(_mutex);
    assert (status == 0, "invariant") ;
    // Paranoia to ensure our locked and lock-free paths interact
    // correctly with each other and Java-level accesses.
    OrderAccess::fence();
    return;
  }

#ifdef ASSERT
  // Don't catch signals while blocked; let the running threads have the signals.
  // (This allows a debugger to break into the running thread.)
  sigset_t oldsigs;
  sigset_t* allowdebug_blocked = os::Bsd::allowdebug_blocked_signals();
  pthread_sigmask(SIG_BLOCK, allowdebug_blocked, &oldsigs);
#endif

  OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
  jt->set_suspend_equivalent();
  // cleared by handle_special_suspend_equivalent_condition() or java_suspend_self()

  if (time == 0) {
    status = pthread_cond_wait (_cond, _mutex) ;
  } else {
    status = os::Bsd::safe_cond_timedwait (_cond, _mutex, &absTime) ;
    if (status != 0 && WorkAroundNPTLTimedWaitHang) {
      pthread_cond_destroy (_cond) ;
      pthread_cond_init    (_cond, NULL);
    }
  }
  assert_status(status == 0 || status == EINTR ||
                status == ETIMEDOUT,
                status, "cond_timedwait");

#ifdef ASSERT
  pthread_sigmask(SIG_SETMASK, &oldsigs, NULL);
#endif

  _counter = 0 ;
  status = pthread_mutex_unlock(_mutex) ;
  assert_status(status == 0, status, "invariant") ;
  // Paranoia to ensure our locked and lock-free paths interact
  // correctly with each other and Java-level accesses.
  OrderAccess::fence();

  // If externally suspended while waiting, re-suspend
  if (jt->handle_special_suspend_equivalent_condition()) {
    jt->java_suspend_self();
  }
}

void Parker::unpark() {
  int s, status ;
  status = pthread_mutex_lock(_mutex);
  assert (status == 0, "invariant") ;
  s = _counter;
  _counter = 1;
  if (s < 1) {
     if (WorkAroundNPTLTimedWaitHang) {
        status = pthread_cond_signal (_cond) ;
        assert (status == 0, "invariant") ;
        status = pthread_mutex_unlock(_mutex);
        assert (status == 0, "invariant") ;
     } else {
        status = pthread_mutex_unlock(_mutex);
        assert (status == 0, "invariant") ;
        status = pthread_cond_signal (_cond) ;
        assert (status == 0, "invariant") ;
     }
  } else {
    pthread_mutex_unlock(_mutex);
    assert (status == 0, "invariant") ;
  }
}


/* Darwin has no "environ" in a dynamic library. */
#ifdef __APPLE__
#include <crt_externs.h>
#define environ (*_NSGetEnviron())
#else
extern char** environ;
#endif

// Run the specified command in a separate process. Return its exit value,
// or -1 on failure (e.g. can't fork a new process).
// Unlike system(), this function can be called from signal handler. It
// doesn't block SIGINT et al.
int os::fork_and_exec(char* cmd) {
  const char * argv[4] = {"sh", "-c", cmd, NULL};

  // fork() in BsdThreads/NPTL is not async-safe. It needs to run
  // pthread_atfork handlers and reset pthread library. All we need is a
  // separate process to execve. Make a direct syscall to fork process.
  // On IA64 there's no fork syscall, we have to use fork() and hope for
  // the best...
  pid_t pid = fork();

  if (pid < 0) {
    // fork failed
    return -1;

  } else if (pid == 0) {
    // child process

    // execve() in BsdThreads will call pthread_kill_other_threads_np()
    // first to kill every thread on the thread list. Because this list is
    // not reset by fork() (see notes above), execve() will instead kill
    // every thread in the parent process. We know this is the only thread
    // in the new process, so make a system call directly.
    // IA64 should use normal execve() from glibc to match the glibc fork()
    // above.
    execve("/bin/sh", (char* const*)argv, environ);

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
}

// is_headless_jre()
//
// Test for the existence of xawt/libmawt.so or libawt_xawt.so
// in order to report if we are running in a headless jre
//
// Since JDK8 xawt/libmawt.so was moved into the same directory
// as libawt.so, and renamed libawt_xawt.so
//
bool os::is_headless_jre() {
    struct stat statbuf;
    char buf[MAXPATHLEN];
    char libmawtpath[MAXPATHLEN];
    const char *xawtstr  = "/xawt/libmawt" JNI_LIB_SUFFIX;
    const char *new_xawtstr = "/libawt_xawt" JNI_LIB_SUFFIX;
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
  int n = jio_snprintf(buffer, bufferSize, "/cores");

  // Truncate if theoretical string was longer than bufferSize
  n = MIN2(n, (int)bufferSize);

  return n;
}

#ifndef PRODUCT
void TestReserveMemorySpecial_test() {
  // No tests available for this platform
}
#endif

/*
 * Copyright 1999-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# define __STDC_FORMAT_MACROS

// do not include  precompiled  header file
# include "incls/_os_linux.cpp.incl"

// put OS-includes here
# include <sys/types.h>
# include <sys/mman.h>
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
# include <pwd.h>
# include <poll.h>
# include <semaphore.h>
# include <fcntl.h>
# include <string.h>
# include <syscall.h>
# include <sys/sysinfo.h>
# include <gnu/libc-version.h>
# include <sys/ipc.h>
# include <sys/shm.h>
# include <link.h>
# include <stdint.h>
# include <inttypes.h>

#define MAX_PATH    (2 * K)

// for timer info max values which include all bits
#define ALL_64_BITS CONST64(0xFFFFFFFFFFFFFFFF)
#define SEC_IN_NANOSECS  1000000000LL

////////////////////////////////////////////////////////////////////////////////
// global variables
julong os::Linux::_physical_memory = 0;

address   os::Linux::_initial_thread_stack_bottom = NULL;
uintptr_t os::Linux::_initial_thread_stack_size   = 0;

int (*os::Linux::_clock_gettime)(clockid_t, struct timespec *) = NULL;
int (*os::Linux::_pthread_getcpuclockid)(pthread_t, clockid_t *) = NULL;
Mutex* os::Linux::_createThread_lock = NULL;
pthread_t os::Linux::_main_thread;
int os::Linux::_page_size = -1;
bool os::Linux::_is_floating_stack = false;
bool os::Linux::_is_NPTL = false;
bool os::Linux::_supports_fast_thread_cpu_time = false;
const char * os::Linux::_glibc_version = NULL;
const char * os::Linux::_libpthread_version = NULL;

static jlong initial_time_count=0;

static int clock_tics_per_sec = 100;

// For diagnostics to print a message once. see run_periodic_checks
static sigset_t check_signal_done;
static bool check_signals = true;;

static pid_t _initial_pid = 0;

/* Signal number used to suspend/resume a thread */

/* do not use any signal number less than SIGSEGV, see 4355769 */
static int SR_signum = SIGUSR2;
sigset_t SR_sigset;

/* Used to protect dlsym() calls */
static pthread_mutex_t dl_mutex;

////////////////////////////////////////////////////////////////////////////////
// utility functions

static int SR_initialize();
static int SR_finalize();

julong os::available_memory() {
  return Linux::available_memory();
}

julong os::Linux::available_memory() {
  // values in struct sysinfo are "unsigned long"
  struct sysinfo si;
  sysinfo(&si);

  return (julong)si.freeram * si.mem_unit;
}

julong os::physical_memory() {
  return Linux::physical_memory();
}

julong os::allocatable_physical_memory(julong size) {
#ifdef _LP64
  return size;
#else
  julong result = MIN2(size, (julong)3800*M);
   if (!is_allocatable(result)) {
     // See comments under solaris for alignment considerations
     julong reasonable_size = (julong)2*G - 2 * os::vm_page_size();
     result =  MIN2(size, reasonable_size);
   }
   return result;
#endif // _LP64
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


#ifndef SYS_gettid
// i386: 224, ia64: 1105, amd64: 186, sparc 143
#ifdef __ia64__
#define SYS_gettid 1105
#elif __i386__
#define SYS_gettid 224
#elif __amd64__
#define SYS_gettid 186
#elif __sparc__
#define SYS_gettid 143
#else
#error define gettid for the arch
#endif
#endif

// Cpu architecture string
#if   defined(ZERO)
static char cpu_arch[] = ZERO_LIBARCH;
#elif defined(IA64)
static char cpu_arch[] = "ia64";
#elif defined(IA32)
static char cpu_arch[] = "i386";
#elif defined(AMD64)
static char cpu_arch[] = "amd64";
#elif defined(SPARC)
#  ifdef _LP64
static char cpu_arch[] = "sparcv9";
#  else
static char cpu_arch[] = "sparc";
#  endif
#else
#error Add appropriate cpu_arch setting
#endif


// pid_t gettid()
//
// Returns the kernel thread id of the currently running thread. Kernel
// thread id is used to access /proc.
//
// (Note that getpid() on LinuxThreads returns kernel thread id too; but
// on NPTL, it returns the same pid for all threads, as required by POSIX.)
//
pid_t os::Linux::gettid() {
  int rslt = syscall(SYS_gettid);
  if (rslt == -1) {
     // old kernel, no NPTL support
     return getpid();
  } else {
     return (pid_t)rslt;
  }
}

// Most versions of linux have a bug where the number of processors are
// determined by looking at the /proc file system.  In a chroot environment,
// the system call returns 1.  This causes the VM to act as if it is
// a single processor and elide locking (see is_MP() call).
static bool unsafe_chroot_detected = false;
static const char *unstable_chroot_error = "/proc file system not found.\n"
                     "Java may be unstable running multithreaded in a chroot "
                     "environment on Linux when /proc filesystem is not mounted.";

void os::Linux::initialize_system_info() {
  set_processor_count(sysconf(_SC_NPROCESSORS_CONF));
  if (processor_count() == 1) {
    pid_t pid = os::Linux::gettid();
    char fname[32];
    jio_snprintf(fname, sizeof(fname), "/proc/%d", pid);
    FILE *fp = fopen(fname, "r");
    if (fp == NULL) {
      unsafe_chroot_detected = true;
    } else {
      fclose(fp);
    }
  }
  _physical_memory = (julong)sysconf(_SC_PHYS_PAGES) * (julong)sysconf(_SC_PAGESIZE);
  assert(processor_count() > 0, "linux error");
}

void os::init_system_properties_values() {
//  char arch[12];
//  sysinfo(SI_ARCHITECTURE, arch, sizeof(arch));

  // The next steps are taken in the product version:
  //
  // Obtain the JAVA_HOME value from the location of libjvm[_g].so.
  // This library should be located at:
  // <JAVA_HOME>/jre/lib/<arch>/{client|server}/libjvm[_g].so.
  //
  // If "/jre/lib/" appears at the right place in the path, then we
  // assume libjvm[_g].so is installed in a JDK and we use this path.
  //
  // Otherwise exit with message: "Could not create the Java virtual machine."
  //
  // The following extra steps are taken in the debugging version:
  //
  // If "/jre/lib/" does NOT appear at the right place in the path
  // instead of exit check for $JAVA_HOME environment variable.
  //
  // If it is defined and we are able to locate $JAVA_HOME/jre/lib/<arch>,
  // then we append a fake suffix "hotspot/libjvm[_g].so" to this path so
  // it looks like libjvm[_g].so is installed there
  // <JAVA_HOME>/jre/lib/<arch>/hotspot/libjvm[_g].so.
  //
  // Otherwise exit.
  //
  // Important note: if the location of libjvm.so changes this
  // code needs to be changed accordingly.

  // The next few definitions allow the code to be verbatim:
#define malloc(n) (char*)NEW_C_HEAP_ARRAY(char, (n))
#define getenv(n) ::getenv(n)

/*
 * See ld(1):
 *      The linker uses the following search paths to locate required
 *      shared libraries:
 *        1: ...
 *        ...
 *        7: The default directories, normally /lib and /usr/lib.
 */
#if defined(AMD64) || defined(_LP64) && (defined(SPARC) || defined(PPC) || defined(S390))
#define DEFAULT_LIBPATH "/usr/lib64:/lib64:/lib:/usr/lib"
#else
#define DEFAULT_LIBPATH "/lib:/usr/lib"
#endif

#define EXTENSIONS_DIR  "/lib/ext"
#define ENDORSED_DIR    "/lib/endorsed"
#define REG_DIR         "/usr/java/packages"

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
                *pslash = '\0';       /* get rid of /<arch> */
                pslash = strrchr(buf, '/');
                if (pslash != NULL)
                    *pslash = '\0';   /* get rid of /lib */
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
     * on legacy Linux implementations (which are no longer supported).
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
        ld_library_path = (char *) malloc(sizeof(REG_DIR) + sizeof("/lib/") +
            strlen(cpu_arch) + sizeof(DEFAULT_LIBPATH));
        sprintf(ld_library_path, REG_DIR "/lib/%s:" DEFAULT_LIBPATH, cpu_arch);

        /*
         * Get the user setting of LD_LIBRARY_PATH, and prepended it.  It
         * should always exist (until the legacy problem cited above is
         * addressed).
         */
        char *v = getenv("LD_LIBRARY_PATH");
        if (v != NULL) {
            char *t = ld_library_path;
            /* That's +1 for the colon and +1 for the trailing '\0' */
            ld_library_path = (char *) malloc(strlen(v) + 1 + strlen(t) + 1);
            sprintf(ld_library_path, "%s:%s", v, t);
        }
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
        char *buf = malloc(strlen(Arguments::get_java_home()) +
            sizeof(EXTENSIONS_DIR) + sizeof(REG_DIR) + sizeof(EXTENSIONS_DIR));
        sprintf(buf, "%s" EXTENSIONS_DIR ":" REG_DIR EXTENSIONS_DIR,
            Arguments::get_java_home());
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

bool os::Linux::is_sig_ignored(int sig) {
      struct sigaction oact;
      sigaction(sig, (struct sigaction*)NULL, &oact);
      void* ohlr = oact.sa_sigaction ? CAST_FROM_FN_PTR(void*,  oact.sa_sigaction)
                                     : CAST_FROM_FN_PTR(void*,  oact.sa_handler);
      if (ohlr == CAST_FROM_FN_PTR(void*, SIG_IGN))
           return true;
      else
           return false;
}

void os::Linux::signal_sets_init() {
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
   if (!os::Linux::is_sig_ignored(SHUTDOWN1_SIGNAL)) {
      sigaddset(&unblocked_sigs, SHUTDOWN1_SIGNAL);
      sigaddset(&allowdebug_blocked_sigs, SHUTDOWN1_SIGNAL);
   }
   if (!os::Linux::is_sig_ignored(SHUTDOWN2_SIGNAL)) {
      sigaddset(&unblocked_sigs, SHUTDOWN2_SIGNAL);
      sigaddset(&allowdebug_blocked_sigs, SHUTDOWN2_SIGNAL);
   }
   if (!os::Linux::is_sig_ignored(SHUTDOWN3_SIGNAL)) {
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
sigset_t* os::Linux::unblocked_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &unblocked_sigs;
}

// These are the signals that are blocked while a (non-VM) thread is
// running Java. Only the VM thread handles these signals.
sigset_t* os::Linux::vm_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &vm_sigs;
}

// These are signals that are blocked during cond_wait to allow debugger in
sigset_t* os::Linux::allowdebug_blocked_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &allowdebug_blocked_sigs;
}

void os::Linux::hotspot_sigmask(Thread* thread) {

  //Save caller's signal mask before setting VM signal mask
  sigset_t caller_sigmask;
  pthread_sigmask(SIG_BLOCK, NULL, &caller_sigmask);

  OSThread* osthread = thread->osthread();
  osthread->set_caller_sigmask(caller_sigmask);

  pthread_sigmask(SIG_UNBLOCK, os::Linux::unblocked_signals(), NULL);

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
// detecting pthread library

void os::Linux::libpthread_init() {
  // Save glibc and pthread version strings. Note that _CS_GNU_LIBC_VERSION
  // and _CS_GNU_LIBPTHREAD_VERSION are supported in glibc >= 2.3.2. Use a
  // generic name for earlier versions.
  // Define macros here so we can build HotSpot on old systems.
# ifndef _CS_GNU_LIBC_VERSION
# define _CS_GNU_LIBC_VERSION 2
# endif
# ifndef _CS_GNU_LIBPTHREAD_VERSION
# define _CS_GNU_LIBPTHREAD_VERSION 3
# endif

  size_t n = confstr(_CS_GNU_LIBC_VERSION, NULL, 0);
  if (n > 0) {
     char *str = (char *)malloc(n);
     confstr(_CS_GNU_LIBC_VERSION, str, n);
     os::Linux::set_glibc_version(str);
  } else {
     // _CS_GNU_LIBC_VERSION is not supported, try gnu_get_libc_version()
     static char _gnu_libc_version[32];
     jio_snprintf(_gnu_libc_version, sizeof(_gnu_libc_version),
              "glibc %s %s", gnu_get_libc_version(), gnu_get_libc_release());
     os::Linux::set_glibc_version(_gnu_libc_version);
  }

  n = confstr(_CS_GNU_LIBPTHREAD_VERSION, NULL, 0);
  if (n > 0) {
     char *str = (char *)malloc(n);
     confstr(_CS_GNU_LIBPTHREAD_VERSION, str, n);
     // Vanilla RH-9 (glibc 2.3.2) has a bug that confstr() always tells
     // us "NPTL-0.29" even we are running with LinuxThreads. Check if this
     // is the case. LinuxThreads has a hard limit on max number of threads.
     // So sysconf(_SC_THREAD_THREADS_MAX) will return a positive value.
     // On the other hand, NPTL does not have such a limit, sysconf()
     // will return -1 and errno is not changed. Check if it is really NPTL.
     if (strcmp(os::Linux::glibc_version(), "glibc 2.3.2") == 0 &&
         strstr(str, "NPTL") &&
         sysconf(_SC_THREAD_THREADS_MAX) > 0) {
       free(str);
       os::Linux::set_libpthread_version("linuxthreads");
     } else {
       os::Linux::set_libpthread_version(str);
     }
  } else {
    // glibc before 2.3.2 only has LinuxThreads.
    os::Linux::set_libpthread_version("linuxthreads");
  }

  if (strstr(libpthread_version(), "NPTL")) {
     os::Linux::set_is_NPTL();
  } else {
     os::Linux::set_is_LinuxThreads();
  }

  // LinuxThreads have two flavors: floating-stack mode, which allows variable
  // stack size; and fixed-stack mode. NPTL is always floating-stack.
  if (os::Linux::is_NPTL() || os::Linux::supports_variable_stack_size()) {
     os::Linux::set_is_floating_stack();
  }
}

/////////////////////////////////////////////////////////////////////////////
// thread stack

// Force Linux kernel to expand current thread stack. If "bottom" is close
// to the stack guard, caller should block all signals.
//
// MAP_GROWSDOWN:
//   A special mmap() flag that is used to implement thread stacks. It tells
//   kernel that the memory region should extend downwards when needed. This
//   allows early versions of LinuxThreads to only mmap the first few pages
//   when creating a new thread. Linux kernel will automatically expand thread
//   stack as needed (on page faults).
//
//   However, because the memory region of a MAP_GROWSDOWN stack can grow on
//   demand, if a page fault happens outside an already mapped MAP_GROWSDOWN
//   region, it's hard to tell if the fault is due to a legitimate stack
//   access or because of reading/writing non-exist memory (e.g. buffer
//   overrun). As a rule, if the fault happens below current stack pointer,
//   Linux kernel does not expand stack, instead a SIGSEGV is sent to the
//   application (see Linux kernel fault.c).
//
//   This Linux feature can cause SIGSEGV when VM bangs thread stack for
//   stack overflow detection.
//
//   Newer version of LinuxThreads (since glibc-2.2, or, RH-7.x) and NPTL do
//   not use this flag. However, the stack of initial thread is not created
//   by pthread, it is still MAP_GROWSDOWN. Also it's possible (though
//   unlikely) that user code can create a thread with MAP_GROWSDOWN stack
//   and then attach the thread to JVM.
//
// To get around the problem and allow stack banging on Linux, we need to
// manually expand thread stack after receiving the SIGSEGV.
//
// There are two ways to expand thread stack to address "bottom", we used
// both of them in JVM before 1.5:
//   1. adjust stack pointer first so that it is below "bottom", and then
//      touch "bottom"
//   2. mmap() the page in question
//
// Now alternate signal stack is gone, it's harder to use 2. For instance,
// if current sp is already near the lower end of page 101, and we need to
// call mmap() to map page 100, it is possible that part of the mmap() frame
// will be placed in page 100. When page 100 is mapped, it is zero-filled.
// That will destroy the mmap() frame and cause VM to crash.
//
// The following code works by adjusting sp first, then accessing the "bottom"
// page to force a page fault. Linux kernel will then automatically expand the
// stack mapping.
//
// _expand_stack_to() assumes its frame size is less than page size, which
// should always be true if the function is not inlined.

#if __GNUC__ < 3    // gcc 2.x does not support noinline attribute
#define NOINLINE
#else
#define NOINLINE __attribute__ ((noinline))
#endif

static void _expand_stack_to(address bottom) NOINLINE;

static void _expand_stack_to(address bottom) {
  address sp;
  size_t size;
  volatile char *p;

  // Adjust bottom to point to the largest address within the same page, it
  // gives us a one-page buffer if alloca() allocates slightly more memory.
  bottom = (address)align_size_down((uintptr_t)bottom, os::Linux::page_size());
  bottom += os::Linux::page_size() - 1;

  // sp might be slightly above current stack pointer; if that's the case, we
  // will alloca() a little more space than necessary, which is OK. Don't use
  // os::current_stack_pointer(), as its result can be slightly below current
  // stack pointer, causing us to not alloca enough to reach "bottom".
  sp = (address)&sp;

  if (sp > bottom) {
    size = sp - bottom;
    p = (volatile char *)alloca(size);
    assert(p != NULL && p <= (volatile char *)bottom, "alloca problem?");
    p[0] = '\0';
  }
}

bool os::Linux::manually_expand_stack(JavaThread * t, address addr) {
  assert(t!=NULL, "just checking");
  assert(t->osthread()->expanding_stack(), "expand should be set");
  assert(t->stack_base() != NULL, "stack_base was not initialized");

  if (addr <  t->stack_base() && addr >= t->stack_yellow_zone_base()) {
    sigset_t mask_all, old_sigset;
    sigfillset(&mask_all);
    pthread_sigmask(SIG_SETMASK, &mask_all, &old_sigset);
    _expand_stack_to(addr);
    pthread_sigmask(SIG_SETMASK, &old_sigset, NULL);
    return true;
  }
  return false;
}

//////////////////////////////////////////////////////////////////////////////
// create new thread

static address highest_vm_reserved_address();

// check if it's safe to start a new thread
static bool _thread_safety_check(Thread* thread) {
  if (os::Linux::is_LinuxThreads() && !os::Linux::is_floating_stack()) {
    // Fixed stack LinuxThreads (SuSE Linux/x86, and some versions of Redhat)
    //   Heap is mmap'ed at lower end of memory space. Thread stacks are
    //   allocated (MAP_FIXED) from high address space. Every thread stack
    //   occupies a fixed size slot (usually 2Mbytes, but user can change
    //   it to other values if they rebuild LinuxThreads).
    //
    // Problem with MAP_FIXED is that mmap() can still succeed even part of
    // the memory region has already been mmap'ed. That means if we have too
    // many threads and/or very large heap, eventually thread stack will
    // collide with heap.
    //
    // Here we try to prevent heap/stack collision by comparing current
    // stack bottom with the highest address that has been mmap'ed by JVM
    // plus a safety margin for memory maps created by native code.
    //
    // This feature can be disabled by setting ThreadSafetyMargin to 0
    //
    if (ThreadSafetyMargin > 0) {
      address stack_bottom = os::current_stack_base() - os::current_stack_size();

      // not safe if our stack extends below the safety margin
      return stack_bottom - ThreadSafetyMargin >= highest_vm_reserved_address();
    } else {
      return true;
    }
  } else {
    // Floating stack LinuxThreads or NPTL:
    //   Unlike fixed stack LinuxThreads, thread stacks are not MAP_FIXED. When
    //   there's not enough space left, pthread_create() will fail. If we come
    //   here, that means enough space has been reserved for stack.
    return true;
  }
}

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

  // non floating stack LinuxThreads needs extra check, see above
  if (!_thread_safety_check(thread)) {
    // notify parent thread
    MutexLockerEx ml(sync, Mutex::_no_safepoint_check_flag);
    osthread->set_state(ZOMBIE);
    sync->notify_all();
    return NULL;
  }

  // thread_id is kernel thread id (similar to Solaris LWP id)
  osthread->set_thread_id(os::Linux::gettid());

  if (UseNUMA) {
    int lgrp_id = os::numa_get_group_id();
    if (lgrp_id != -1) {
      thread->set_lgrp_id(lgrp_id);
    }
  }
  // initialize signal mask for this thread
  os::Linux::hotspot_sigmask(thread);

  // initialize floating point control register
  os::Linux::init_thread_fpu_state();

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
  if (os::Linux::supports_variable_stack_size()) {
    // calculate stack size if it's not specified by caller
    if (stack_size == 0) {
      stack_size = os::Linux::default_stack_size(thr_type);

      switch (thr_type) {
      case os::java_thread:
        // Java threads use ThreadStackSize which default value can be changed with the flag -Xss
        if (JavaThread::stack_size_at_create() > 0) stack_size = JavaThread::stack_size_at_create();
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

    stack_size = MAX2(stack_size, os::Linux::min_stack_allowed);
    pthread_attr_setstacksize(&attr, stack_size);
  } else {
    // let pthread_create() pick the default value.
  }

  // glibc guard page
  pthread_attr_setguardsize(&attr, os::Linux::default_guard_size(thr_type));

  ThreadState state;

  {
    // Serialize thread creation if we are running with fixed stack LinuxThreads
    bool lock = os::Linux::is_LinuxThreads() && !os::Linux::is_floating_stack();
    if (lock) {
      os::Linux::createThread_lock()->lock_without_safepoint_check();
    }

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
      if (lock) os::Linux::createThread_lock()->unlock();
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

    if (lock) {
      os::Linux::createThread_lock()->unlock();
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
  assert(os::Linux::_main_thread == pthread_self(), "should be called inside main thread");
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
  osthread->set_thread_id(os::Linux::gettid());
  osthread->set_pthread_id(::pthread_self());

  // initialize floating point control register
  os::Linux::init_thread_fpu_state();

  // Initial thread state is RUNNABLE
  osthread->set_state(RUNNABLE);

  thread->set_osthread(osthread);

  if (UseNUMA) {
    int lgrp_id = os::numa_get_group_id();
    if (lgrp_id != -1) {
      thread->set_lgrp_id(lgrp_id);
    }
  }

  if (os::Linux::is_initial_thread()) {
    // If current thread is initial thread, its stack is mapped on demand,
    // see notes about MAP_GROWSDOWN. Here we try to force kernel to map
    // the entire stack region to avoid SEGV in stack banging.
    // It is also useful to get around the heap-stack-gap problem on SuSE
    // kernel (see 4821821 for details). We first expand stack to the top
    // of yellow zone, then enable stack yellow zone (order is significant,
    // enabling yellow zone first will crash JVM on SuSE Linux), so there
    // is no gap between the last two virtual memory regions.

    JavaThread *jt = (JavaThread *)thread;
    address addr = jt->stack_yellow_zone_base();
    assert(addr != NULL, "initialization problem?");
    assert(jt->stack_available(addr) > 0, "stack guard should not be enabled");

    osthread->set_expanding_stack();
    os::Linux::manually_expand_stack(jt, addr);
    osthread->clear_expanding_stack();
  }

  // initialize signal mask for this thread
  // and save the caller's signal mask
  os::Linux::hotspot_sigmask(thread);

  return true;
}

void os::pd_start_thread(Thread* thread) {
  OSThread * osthread = thread->osthread();
  assert(osthread->get_state() != INITIALIZED, "just checking");
  Monitor* sync_with_child = osthread->startThread_lock();
  MutexLockerEx ml(sync_with_child, Mutex::_no_safepoint_check_flag);
  sync_with_child->notify();
}

// Free Linux resources related to the OSThread
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

//////////////////////////////////////////////////////////////////////////////
// initial thread

// Check if current thread is the initial thread, similar to Solaris thr_main.
bool os::Linux::is_initial_thread(void) {
  char dummy;
  // If called before init complete, thread stack bottom will be null.
  // Can be called if fatal error occurs before initialization.
  if (initial_thread_stack_bottom() == NULL) return false;
  assert(initial_thread_stack_bottom() != NULL &&
         initial_thread_stack_size()   != 0,
         "os::init did not locate initial thread's stack region");
  if ((address)&dummy >= initial_thread_stack_bottom() &&
      (address)&dummy < initial_thread_stack_bottom() + initial_thread_stack_size())
       return true;
  else return false;
}

// Find the virtual memory area that contains addr
static bool find_vma(address addr, address* vma_low, address* vma_high) {
  FILE *fp = fopen("/proc/self/maps", "r");
  if (fp) {
    address low, high;
    while (!feof(fp)) {
      if (fscanf(fp, "%p-%p", &low, &high) == 2) {
        if (low <= addr && addr < high) {
           if (vma_low)  *vma_low  = low;
           if (vma_high) *vma_high = high;
           fclose (fp);
           return true;
        }
      }
      for (;;) {
        int ch = fgetc(fp);
        if (ch == EOF || ch == (int)'\n') break;
      }
    }
    fclose(fp);
  }
  return false;
}

// Locate initial thread stack. This special handling of initial thread stack
// is needed because pthread_getattr_np() on most (all?) Linux distros returns
// bogus value for initial thread.
void os::Linux::capture_initial_stack(size_t max_size) {
  // stack size is the easy part, get it from RLIMIT_STACK
  size_t stack_size;
  struct rlimit rlim;
  getrlimit(RLIMIT_STACK, &rlim);
  stack_size = rlim.rlim_cur;

  // 6308388: a bug in ld.so will relocate its own .data section to the
  //   lower end of primordial stack; reduce ulimit -s value a little bit
  //   so we won't install guard page on ld.so's data section.
  stack_size -= 2 * page_size();

  // 4441425: avoid crash with "unlimited" stack size on SuSE 7.1 or Redhat
  //   7.1, in both cases we will get 2G in return value.
  // 4466587: glibc 2.2.x compiled w/o "--enable-kernel=2.4.0" (RH 7.0,
  //   SuSE 7.2, Debian) can not handle alternate signal stack correctly
  //   for initial thread if its stack size exceeds 6M. Cap it at 2M,
  //   in case other parts in glibc still assumes 2M max stack size.
  // FIXME: alt signal stack is gone, maybe we can relax this constraint?
#ifndef IA64
  if (stack_size > 2 * K * K) stack_size = 2 * K * K;
#else
  // Problem still exists RH7.2 (IA64 anyway) but 2MB is a little small
  if (stack_size > 4 * K * K) stack_size = 4 * K * K;
#endif

  // Try to figure out where the stack base (top) is. This is harder.
  //
  // When an application is started, glibc saves the initial stack pointer in
  // a global variable "__libc_stack_end", which is then used by system
  // libraries. __libc_stack_end should be pretty close to stack top. The
  // variable is available since the very early days. However, because it is
  // a private interface, it could disappear in the future.
  //
  // Linux kernel saves start_stack information in /proc/<pid>/stat. Similar
  // to __libc_stack_end, it is very close to stack top, but isn't the real
  // stack top. Note that /proc may not exist if VM is running as a chroot
  // program, so reading /proc/<pid>/stat could fail. Also the contents of
  // /proc/<pid>/stat could change in the future (though unlikely).
  //
  // We try __libc_stack_end first. If that doesn't work, look for
  // /proc/<pid>/stat. If neither of them works, we use current stack pointer
  // as a hint, which should work well in most cases.

  uintptr_t stack_start;

  // try __libc_stack_end first
  uintptr_t *p = (uintptr_t *)dlsym(RTLD_DEFAULT, "__libc_stack_end");
  if (p && *p) {
    stack_start = *p;
  } else {
    // see if we can get the start_stack field from /proc/self/stat
    FILE *fp;
    int pid;
    char state;
    int ppid;
    int pgrp;
    int session;
    int nr;
    int tpgrp;
    unsigned long flags;
    unsigned long minflt;
    unsigned long cminflt;
    unsigned long majflt;
    unsigned long cmajflt;
    unsigned long utime;
    unsigned long stime;
    long cutime;
    long cstime;
    long prio;
    long nice;
    long junk;
    long it_real;
    uintptr_t start;
    uintptr_t vsize;
    uintptr_t rss;
    unsigned long rsslim;
    uintptr_t scodes;
    uintptr_t ecode;
    int i;

    // Figure what the primordial thread stack base is. Code is inspired
    // by email from Hans Boehm. /proc/self/stat begins with current pid,
    // followed by command name surrounded by parentheses, state, etc.
    char stat[2048];
    int statlen;

    fp = fopen("/proc/self/stat", "r");
    if (fp) {
      statlen = fread(stat, 1, 2047, fp);
      stat[statlen] = '\0';
      fclose(fp);

      // Skip pid and the command string. Note that we could be dealing with
      // weird command names, e.g. user could decide to rename java launcher
      // to "java 1.4.2 :)", then the stat file would look like
      //                1234 (java 1.4.2 :)) R ... ...
      // We don't really need to know the command string, just find the last
      // occurrence of ")" and then start parsing from there. See bug 4726580.
      char * s = strrchr(stat, ')');

      i = 0;
      if (s) {
        // Skip blank chars
        do s++; while (isspace(*s));

        /*                                     1   1   1   1   1   1   1   1   1   1   2   2   2   2   2   2   2   2   2 */
        /*              3  4  5  6  7  8   9   0   1   2   3   4   5   6   7   8   9   0   1   2   3   4   5   6   7   8 */
        i = sscanf(s, "%c %d %d %d %d %d %lu %lu %lu %lu %lu %lu %lu %ld %ld %ld %ld %ld %ld "
                   UINTX_FORMAT UINTX_FORMAT UINTX_FORMAT
                   " %lu "
                   UINTX_FORMAT UINTX_FORMAT UINTX_FORMAT,
             &state,          /* 3  %c  */
             &ppid,           /* 4  %d  */
             &pgrp,           /* 5  %d  */
             &session,        /* 6  %d  */
             &nr,             /* 7  %d  */
             &tpgrp,          /* 8  %d  */
             &flags,          /* 9  %lu  */
             &minflt,         /* 10 %lu  */
             &cminflt,        /* 11 %lu  */
             &majflt,         /* 12 %lu  */
             &cmajflt,        /* 13 %lu  */
             &utime,          /* 14 %lu  */
             &stime,          /* 15 %lu  */
             &cutime,         /* 16 %ld  */
             &cstime,         /* 17 %ld  */
             &prio,           /* 18 %ld  */
             &nice,           /* 19 %ld  */
             &junk,           /* 20 %ld  */
             &it_real,        /* 21 %ld  */
             &start,          /* 22 UINTX_FORMAT  */
             &vsize,          /* 23 UINTX_FORMAT  */
             &rss,            /* 24 UINTX_FORMAT  */
             &rsslim,         /* 25 %lu  */
             &scodes,         /* 26 UINTX_FORMAT  */
             &ecode,          /* 27 UINTX_FORMAT  */
             &stack_start);   /* 28 UINTX_FORMAT  */
      }

      if (i != 28 - 2) {
         assert(false, "Bad conversion from /proc/self/stat");
         // product mode - assume we are the initial thread, good luck in the
         // embedded case.
         warning("Can't detect initial thread stack location - bad conversion");
         stack_start = (uintptr_t) &rlim;
      }
    } else {
      // For some reason we can't open /proc/self/stat (for example, running on
      // FreeBSD with a Linux emulator, or inside chroot), this should work for
      // most cases, so don't abort:
      warning("Can't detect initial thread stack location - no /proc/self/stat");
      stack_start = (uintptr_t) &rlim;
    }
  }

  // Now we have a pointer (stack_start) very close to the stack top, the
  // next thing to do is to figure out the exact location of stack top. We
  // can find out the virtual memory area that contains stack_start by
  // reading /proc/self/maps, it should be the last vma in /proc/self/maps,
  // and its upper limit is the real stack top. (again, this would fail if
  // running inside chroot, because /proc may not exist.)

  uintptr_t stack_top;
  address low, high;
  if (find_vma((address)stack_start, &low, &high)) {
    // success, "high" is the true stack top. (ignore "low", because initial
    // thread stack grows on demand, its real bottom is high - RLIMIT_STACK.)
    stack_top = (uintptr_t)high;
  } else {
    // failed, likely because /proc/self/maps does not exist
    warning("Can't detect initial thread stack location - find_vma failed");
    // best effort: stack_start is normally within a few pages below the real
    // stack top, use it as stack top, and reduce stack size so we won't put
    // guard page outside stack.
    stack_top = stack_start;
    stack_size -= 16 * page_size();
  }

  // stack_top could be partially down the page so align it
  stack_top = align_size_up(stack_top, page_size());

  if (max_size && stack_size > max_size) {
     _initial_thread_stack_size = max_size;
  } else {
     _initial_thread_stack_size = stack_size;
  }

  _initial_thread_stack_size = align_size_down(_initial_thread_stack_size, page_size());
  _initial_thread_stack_bottom = (address)stack_top - _initial_thread_stack_size;
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

// For now, we say that linux does not support vtime.  I have no idea
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
  assert(status != -1, "linux error");
  return jlong(time.tv_sec) * 1000  +  jlong(time.tv_usec / 1000);
}

#ifndef CLOCK_MONOTONIC
#define CLOCK_MONOTONIC (1)
#endif

void os::Linux::clock_init() {
  // we do dlopen's in this particular order due to bug in linux
  // dynamical loader (see 6348968) leading to crash on exit
  void* handle = dlopen("librt.so.1", RTLD_LAZY);
  if (handle == NULL) {
    handle = dlopen("librt.so", RTLD_LAZY);
  }

  if (handle) {
    int (*clock_getres_func)(clockid_t, struct timespec*) =
           (int(*)(clockid_t, struct timespec*))dlsym(handle, "clock_getres");
    int (*clock_gettime_func)(clockid_t, struct timespec*) =
           (int(*)(clockid_t, struct timespec*))dlsym(handle, "clock_gettime");
    if (clock_getres_func && clock_gettime_func) {
      // See if monotonic clock is supported by the kernel. Note that some
      // early implementations simply return kernel jiffies (updated every
      // 1/100 or 1/1000 second). It would be bad to use such a low res clock
      // for nano time (though the monotonic property is still nice to have).
      // It's fixed in newer kernels, however clock_getres() still returns
      // 1/HZ. We check if clock_getres() works, but will ignore its reported
      // resolution for now. Hopefully as people move to new kernels, this
      // won't be a problem.
      struct timespec res;
      struct timespec tp;
      if (clock_getres_func (CLOCK_MONOTONIC, &res) == 0 &&
          clock_gettime_func(CLOCK_MONOTONIC, &tp)  == 0) {
        // yes, monotonic clock is supported
        _clock_gettime = clock_gettime_func;
      } else {
        // close librt if there is no monotonic clock
        dlclose(handle);
      }
    }
  }
}

#ifndef SYS_clock_getres

#if defined(IA32) || defined(AMD64)
#define SYS_clock_getres IA32_ONLY(266)  AMD64_ONLY(229)
#else
#error Value of SYS_clock_getres not known on this platform
#endif

#endif

#define sys_clock_getres(x,y)  ::syscall(SYS_clock_getres, x, y)

void os::Linux::fast_thread_clock_init() {
  if (!UseLinuxPosixThreadCPUClocks) {
    return;
  }
  clockid_t clockid;
  struct timespec tp;
  int (*pthread_getcpuclockid_func)(pthread_t, clockid_t *) =
      (int(*)(pthread_t, clockid_t *)) dlsym(RTLD_DEFAULT, "pthread_getcpuclockid");

  // Switch to using fast clocks for thread cpu time if
  // the sys_clock_getres() returns 0 error code.
  // Note, that some kernels may support the current thread
  // clock (CLOCK_THREAD_CPUTIME_ID) but not the clocks
  // returned by the pthread_getcpuclockid().
  // If the fast Posix clocks are supported then the sys_clock_getres()
  // must return at least tp.tv_sec == 0 which means a resolution
  // better than 1 sec. This is extra check for reliability.

  if(pthread_getcpuclockid_func &&
     pthread_getcpuclockid_func(_main_thread, &clockid) == 0 &&
     sys_clock_getres(clockid, &tp) == 0 && tp.tv_sec == 0) {

    _supports_fast_thread_cpu_time = true;
    _pthread_getcpuclockid = pthread_getcpuclockid_func;
  }
}

jlong os::javaTimeNanos() {
  if (Linux::supports_monotonic_clock()) {
    struct timespec tp;
    int status = Linux::clock_gettime(CLOCK_MONOTONIC, &tp);
    assert(status == 0, "gettime error");
    jlong result = jlong(tp.tv_sec) * (1000 * 1000 * 1000) + jlong(tp.tv_nsec);
    return result;
  } else {
    timeval time;
    int status = gettimeofday(&time, NULL);
    assert(status != -1, "linux error");
    jlong usecs = jlong(time.tv_sec) * (1000 * 1000) + jlong(time.tv_usec);
    return 1000 * usecs;
  }
}

void os::javaTimeNanos_info(jvmtiTimerInfo *info_ptr) {
  if (Linux::supports_monotonic_clock()) {
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
  // _exit() on LinuxThreads only kills current thread
  ::abort();
}

// unused on linux for now.
void os::set_error_file(const char *logfile) {}

intx os::current_thread_id() { return (intx)pthread_self(); }
int os::current_process_id() {

  // Under the old linux thread library, linux gives each thread
  // its own process id. Because of this each thread will return
  // a different pid if this method were to return the result
  // of getpid(2). Linux provides no api that returns the pid
  // of the launcher thread for the vm. This implementation
  // returns a unique pid, the pid of the launcher thread
  // that starts the vm 'process'.

  // Under the NPTL, getpid() returns the same pid as the
  // launcher thread rather than a unique pid per thread.
  // Use gettid() if you want the old pre NPTL behaviour.

  // if you are looking for the result of a call to getpid() that
  // returns a unique pid for the calling thread, then look at the
  // OSThread::thread_id() method in osThread_linux.hpp file

  return (int)(_initial_pid ? _initial_pid : getpid());
}

// DLL functions

const char* os::dll_file_extension() { return ".so"; }

const char* os::get_temp_directory() {
  const char *prop = Arguments::get_property("java.io.tmpdir");
  return prop == NULL ? "/tmp" : prop;
}

static bool file_exists(const char* filename) {
  struct stat statbuf;
  if (filename == NULL || strlen(filename) == 0) {
    return false;
  }
  return os::stat(filename, &statbuf) == 0;
}

void os::dll_build_name(char* buffer, size_t buflen,
                        const char* pname, const char* fname) {
  // Copied from libhpi
  const size_t pnamelen = pname ? strlen(pname) : 0;

  // Quietly truncate on buffer overflow.  Should be an error.
  if (pnamelen + strlen(fname) + 10 > (size_t) buflen) {
      *buffer = '\0';
      return;
  }

  if (pnamelen == 0) {
    snprintf(buffer, buflen, "lib%s.so", fname);
  } else if (strchr(pname, *os::path_separator()) != NULL) {
    int n;
    char** pelements = split_path(pname, &n);
    for (int i = 0 ; i < n ; i++) {
      // Really shouldn't be NULL, but check can't hurt
      if (pelements[i] == NULL || strlen(pelements[i]) == 0) {
        continue; // skip the empty path values
      }
      snprintf(buffer, buflen, "%s/lib%s.so", pelements[i], fname);
      if (file_exists(buffer)) {
        break;
      }
    }
    // release the storage
    for (int i = 0 ; i < n ; i++) {
      if (pelements[i] != NULL) {
        FREE_C_HEAP_ARRAY(char, pelements[i]);
      }
    }
    if (pelements != NULL) {
      FREE_C_HEAP_ARRAY(char*, pelements);
    }
  } else {
    snprintf(buffer, buflen, "%s/lib%s.so", pname, fname);
  }
}

const char* os::get_current_directory(char *buf, int buflen) {
  return getcwd(buf, buflen);
}

// check if addr is inside libjvm[_g].so
bool os::address_is_in_vm(address addr) {
  static address libjvm_base_addr;
  Dl_info dlinfo;

  if (libjvm_base_addr == NULL) {
    dladdr(CAST_FROM_FN_PTR(void *, os::address_is_in_vm), &dlinfo);
    libjvm_base_addr = (address)dlinfo.dli_fbase;
    assert(libjvm_base_addr !=NULL, "Cannot obtain base address for libjvm");
  }

  if (dladdr((void *)addr, &dlinfo)) {
    if (libjvm_base_addr == (address)dlinfo.dli_fbase) return true;
  }

  return false;
}

bool os::dll_address_to_function_name(address addr, char *buf,
                                      int buflen, int *offset) {
  Dl_info dlinfo;

  if (dladdr((void*)addr, &dlinfo) && dlinfo.dli_sname != NULL) {
    if (buf) jio_snprintf(buf, buflen, "%s", dlinfo.dli_sname);
    if (offset) *offset = addr - (address)dlinfo.dli_saddr;
    return true;
  } else {
    if (buf) buf[0] = '\0';
    if (offset) *offset = -1;
    return false;
  }
}

struct _address_to_library_name {
  address addr;          // input : memory address
  size_t  buflen;        //         size of fname
  char*   fname;         // output: library name
  address base;          //         library base addr
};

static int address_to_library_name_callback(struct dl_phdr_info *info,
                                            size_t size, void *data) {
  int i;
  bool found = false;
  address libbase = NULL;
  struct _address_to_library_name * d = (struct _address_to_library_name *)data;

  // iterate through all loadable segments
  for (i = 0; i < info->dlpi_phnum; i++) {
    address segbase = (address)(info->dlpi_addr + info->dlpi_phdr[i].p_vaddr);
    if (info->dlpi_phdr[i].p_type == PT_LOAD) {
      // base address of a library is the lowest address of its loaded
      // segments.
      if (libbase == NULL || libbase > segbase) {
        libbase = segbase;
      }
      // see if 'addr' is within current segment
      if (segbase <= d->addr &&
          d->addr < segbase + info->dlpi_phdr[i].p_memsz) {
        found = true;
      }
    }
  }

  // dlpi_name is NULL or empty if the ELF file is executable, return 0
  // so dll_address_to_library_name() can fall through to use dladdr() which
  // can figure out executable name from argv[0].
  if (found && info->dlpi_name && info->dlpi_name[0]) {
    d->base = libbase;
    if (d->fname) {
      jio_snprintf(d->fname, d->buflen, "%s", info->dlpi_name);
    }
    return 1;
  }
  return 0;
}

bool os::dll_address_to_library_name(address addr, char* buf,
                                     int buflen, int* offset) {
  Dl_info dlinfo;
  struct _address_to_library_name data;

  // There is a bug in old glibc dladdr() implementation that it could resolve
  // to wrong library name if the .so file has a base address != NULL. Here
  // we iterate through the program headers of all loaded libraries to find
  // out which library 'addr' really belongs to. This workaround can be
  // removed once the minimum requirement for glibc is moved to 2.3.x.
  data.addr = addr;
  data.fname = buf;
  data.buflen = buflen;
  data.base = NULL;
  int rslt = dl_iterate_phdr(address_to_library_name_callback, (void *)&data);

  if (rslt) {
     // buf already contains library name
     if (offset) *offset = addr - data.base;
     return true;
  } else if (dladdr((void*)addr, &dlinfo)){
     if (buf) jio_snprintf(buf, buflen, "%s", dlinfo.dli_fname);
     if (offset) *offset = addr - (address)dlinfo.dli_fbase;
     return true;
  } else {
     if (buf) buf[0] = '\0';
     if (offset) *offset = -1;
     return false;
  }
}

  // Loads .dll/.so and
  // in case of error it checks if .dll/.so was built for the
  // same architecture as Hotspot is running on

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

/*
 * glibc-2.0 libdl is not MT safe.  If you are building with any glibc,
 * chances are you might want to run the generated bits against glibc-2.0
 * libdl.so, so always use locking for any version of glibc.
 */
void* os::dll_lookup(void* handle, const char* name) {
  pthread_mutex_lock(&dl_mutex);
  void* res = dlsym(handle, name);
  pthread_mutex_unlock(&dl_mutex);
  return res;
}


bool _print_ascii_file(const char* filename, outputStream* st) {
  int fd = open(filename, O_RDONLY);
  if (fd == -1) {
     return false;
  }

  char buf[32];
  int bytes;
  while ((bytes = read(fd, buf, sizeof(buf))) > 0) {
    st->print_raw(buf, bytes);
  }

  close(fd);

  return true;
}

void os::print_dll_info(outputStream *st) {
   st->print_cr("Dynamic libraries:");

   char fname[32];
   pid_t pid = os::Linux::gettid();

   jio_snprintf(fname, sizeof(fname), "/proc/%d/maps", pid);

   if (!_print_ascii_file(fname, st)) {
     st->print("Can not get library information for pid = %d\n", pid);
   }
}


void os::print_os_info(outputStream* st) {
  st->print("OS:");

  // Try to identify popular distros.
  // Most Linux distributions have /etc/XXX-release file, which contains
  // the OS version string. Some have more than one /etc/XXX-release file
  // (e.g. Mandrake has both /etc/mandrake-release and /etc/redhat-release.),
  // so the order is important.
  if (!_print_ascii_file("/etc/mandrake-release", st) &&
      !_print_ascii_file("/etc/sun-release", st) &&
      !_print_ascii_file("/etc/redhat-release", st) &&
      !_print_ascii_file("/etc/SuSE-release", st) &&
      !_print_ascii_file("/etc/turbolinux-release", st) &&
      !_print_ascii_file("/etc/gentoo-release", st) &&
      !_print_ascii_file("/etc/debian_version", st)) {
      st->print("Linux");
  }
  st->cr();

  // kernel
  st->print("uname:");
  struct utsname name;
  uname(&name);
  st->print(name.sysname); st->print(" ");
  st->print(name.release); st->print(" ");
  st->print(name.version); st->print(" ");
  st->print(name.machine);
  st->cr();

  // Print warning if unsafe chroot environment detected
  if (unsafe_chroot_detected) {
    st->print("WARNING!! ");
    st->print_cr(unstable_chroot_error);
  }

  // libc, pthread
  st->print("libc:");
  st->print(os::Linux::glibc_version()); st->print(" ");
  st->print(os::Linux::libpthread_version()); st->print(" ");
  if (os::Linux::is_LinuxThreads()) {
     st->print("(%s stack)", os::Linux::is_floating_stack() ? "floating" : "fixed");
  }
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
  getrlimit(RLIMIT_NPROC, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%d", rlim.rlim_cur);

  st->print(", NOFILE ");
  getrlimit(RLIMIT_NOFILE, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%d", rlim.rlim_cur);

  st->print(", AS ");
  getrlimit(RLIMIT_AS, &rlim);
  if (rlim.rlim_cur == RLIM_INFINITY) st->print("infinity");
  else st->print("%uk", rlim.rlim_cur >> 10);
  st->cr();

  // load average
  st->print("load average:");
  double loadavg[3];
  os::loadavg(loadavg, 3);
  st->print("%0.02f %0.02f %0.02f", loadavg[0], loadavg[1], loadavg[2]);
  st->cr();
}

void os::print_memory_info(outputStream* st) {

  st->print("Memory:");
  st->print(" %dk page", os::vm_page_size()>>10);

  // values in struct sysinfo are "unsigned long"
  struct sysinfo si;
  sysinfo(&si);

  st->print(", physical " UINT64_FORMAT "k",
            os::physical_memory() >> 10);
  st->print("(" UINT64_FORMAT "k free)",
            os::available_memory() >> 10);
  st->print(", swap " UINT64_FORMAT "k",
            ((jlong)si.totalswap * si.mem_unit) >> 10);
  st->print("(" UINT64_FORMAT "k free)",
            ((jlong)si.freeswap * si.mem_unit) >> 10);
  st->cr();
}

// Taken from /usr/include/bits/siginfo.h  Supposed to be architecture specific
// but they're the same for all the linux arch that we support
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

// Find the full path to the current module, libjvm.so or libjvm_g.so
void os::jvm_path(char *buf, jint len) {
  // Error checking.
  if (len < MAXPATHLEN) {
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
  assert(ret != 0, "cannot locate libjvm");
  if (realpath(dli_fname, buf) == NULL)
    return;

  if (strcmp(Arguments::sun_java_launcher(), "gamma") == 0) {
    // Support for the gamma launcher.  Typical value for buf is
    // "<JAVA_HOME>/jre/lib/<arch>/<vmtype>/libjvm.so".  If "/jre/lib/" appears at
    // the right place in the string, then assume we are installed in a JDK and
    // we're done.  Otherwise, check for a JAVA_HOME environment variable and fix
    // up the path so it looks like libjvm.so is installed there (append a
    // fake suffix hotspot/libjvm.so).
    const char *p = buf + strlen(buf) - 1;
    for (int count = 0; p > buf && count < 5; ++count) {
      for (--p; p > buf && *p != '/'; --p)
        /* empty */ ;
    }

    if (strncmp(p, "/jre/lib/", 9) != 0) {
      // Look for JAVA_HOME in the environment.
      char* java_home_var = ::getenv("JAVA_HOME");
      if (java_home_var != NULL && java_home_var[0] != 0) {
        // Check the current module name "libjvm.so" or "libjvm_g.so".
        p = strrchr(buf, '/');
        assert(strstr(p, "/libjvm") == p, "invalid library name");
        p = strstr(p, "_g") ? "_g" : "";

        if (realpath(java_home_var, buf) == NULL)
          return;
        sprintf(buf + strlen(buf), "/jre/lib/%s", cpu_arch);
        if (0 == access(buf, F_OK)) {
          // Use current module name "libjvm[_g].so" instead of
          // "libjvm"debug_only("_g")".so" since for fastdebug version
          // we should have "libjvm.so" but debug_only("_g") adds "_g"!
          // It is used when we are choosing the HPI library's name
          // "libhpi[_g].so" in hpi::initialize_get_interface().
          sprintf(buf + strlen(buf), "/hotspot/libjvm%s.so", p);
        } else {
          // Go back to path of .so
          if (realpath(dli_fname, buf) == NULL)
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

// Linux(POSIX) specific hand shaking semaphore.
static sem_t sig_sem;

void os::signal_init_pd() {
  // Initialize signal structures
  ::memset((void*)pending_signals, 0, sizeof(pending_signals));

  // Initialize signal semaphore
  ::sem_init(&sig_sem, 0, 0);
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

int os::vm_page_size() {
  // Seems redundant as all get out
  assert(os::Linux::page_size() != -1, "must call os::init");
  return os::Linux::page_size();
}

// Solaris allocates memory by pages.
int os::vm_allocation_granularity() {
  assert(os::Linux::page_size() != -1, "must call os::init");
  return os::Linux::page_size();
}

// Rationale behind this function:
//  current (Mon Apr 25 20:12:18 MSD 2005) oprofile drops samples without executable
//  mapping for address (see lookup_dcookie() in the kernel module), thus we cannot get
//  samples for JITted code. Here we create private executable mapping over the code cache
//  and then we can use standard (well, almost, as mapping can change) way to provide
//  info for the reporting script by storing timestamp and location of symbol
void linux_wrap_code(char* base, size_t size) {
  static volatile jint cnt = 0;

  if (!UseOprofile) {
    return;
  }

  char buf[PATH_MAX+1];
  int num = Atomic::add(1, &cnt);

  snprintf(buf, sizeof(buf), "%s/hs-vm-%d-%d",
           os::get_temp_directory(), os::current_process_id(), num);
  unlink(buf);

  int fd = open(buf, O_CREAT | O_RDWR, S_IRWXU);

  if (fd != -1) {
    off_t rv = lseek(fd, size-2, SEEK_SET);
    if (rv != (off_t)-1) {
      if (write(fd, "", 1) == 1) {
        mmap(base, size,
             PROT_READ|PROT_WRITE|PROT_EXEC,
             MAP_PRIVATE|MAP_FIXED|MAP_NORESERVE, fd, 0);
      }
    }
    close(fd);
    unlink(buf);
  }
}

// NOTE: Linux kernel does not really reserve the pages for us.
//       All it does is to check if there are enough free pages
//       left at the time of mmap(). This could be a potential
//       problem.
bool os::commit_memory(char* addr, size_t size, bool exec) {
  int prot = exec ? PROT_READ|PROT_WRITE|PROT_EXEC : PROT_READ|PROT_WRITE;
  uintptr_t res = (uintptr_t) ::mmap(addr, size, prot,
                                   MAP_PRIVATE|MAP_FIXED|MAP_ANONYMOUS, -1, 0);
  return res != (uintptr_t) MAP_FAILED;
}

bool os::commit_memory(char* addr, size_t size, size_t alignment_hint,
                       bool exec) {
  return commit_memory(addr, size, exec);
}

void os::realign_memory(char *addr, size_t bytes, size_t alignment_hint) { }

void os::free_memory(char *addr, size_t bytes) {
  ::mmap(addr, bytes, PROT_READ | PROT_WRITE,
         MAP_PRIVATE|MAP_FIXED|MAP_ANONYMOUS, -1, 0);
}

void os::numa_make_global(char *addr, size_t bytes) {
  Linux::numa_interleave_memory(addr, bytes);
}

void os::numa_make_local(char *addr, size_t bytes, int lgrp_hint) {
  Linux::numa_tonode_memory(addr, bytes, lgrp_hint);
}

bool os::numa_topology_changed()   { return false; }

size_t os::numa_get_groups_num() {
  int max_node = Linux::numa_max_node();
  return max_node > 0 ? max_node + 1 : 1;
}

int os::numa_get_group_id() {
  int cpu_id = Linux::sched_getcpu();
  if (cpu_id != -1) {
    int lgrp_id = Linux::get_node_by_cpu(cpu_id);
    if (lgrp_id != -1) {
      return lgrp_id;
    }
  }
  return 0;
}

size_t os::numa_get_leaf_groups(int *ids, size_t size) {
  for (size_t i = 0; i < size; i++) {
    ids[i] = i;
  }
  return size;
}

bool os::get_page_info(char *start, page_info* info) {
  return false;
}

char *os::scan_pages(char *start, char* end, page_info* page_expected, page_info* page_found) {
  return end;
}

extern "C" void numa_warn(int number, char *where, ...) { }
extern "C" void numa_error(char *where) { }


// If we are running with libnuma version > 2, then we should
// be trying to use symbols with versions 1.1
// If we are running with earlier version, which did not have symbol versions,
// we should use the base version.
void* os::Linux::libnuma_dlsym(void* handle, const char *name) {
  void *f = dlvsym(handle, name, "libnuma_1.1");
  if (f == NULL) {
    f = dlsym(handle, name);
  }
  return f;
}

bool os::Linux::libnuma_init() {
  // sched_getcpu() should be in libc.
  set_sched_getcpu(CAST_TO_FN_PTR(sched_getcpu_func_t,
                                  dlsym(RTLD_DEFAULT, "sched_getcpu")));

  if (sched_getcpu() != -1) { // Does it work?
    void *handle = dlopen("libnuma.so.1", RTLD_LAZY);
    if (handle != NULL) {
      set_numa_node_to_cpus(CAST_TO_FN_PTR(numa_node_to_cpus_func_t,
                                           libnuma_dlsym(handle, "numa_node_to_cpus")));
      set_numa_max_node(CAST_TO_FN_PTR(numa_max_node_func_t,
                                       libnuma_dlsym(handle, "numa_max_node")));
      set_numa_available(CAST_TO_FN_PTR(numa_available_func_t,
                                        libnuma_dlsym(handle, "numa_available")));
      set_numa_tonode_memory(CAST_TO_FN_PTR(numa_tonode_memory_func_t,
                                            libnuma_dlsym(handle, "numa_tonode_memory")));
      set_numa_interleave_memory(CAST_TO_FN_PTR(numa_interleave_memory_func_t,
                                            libnuma_dlsym(handle, "numa_interleave_memory")));


      if (numa_available() != -1) {
        set_numa_all_nodes((unsigned long*)libnuma_dlsym(handle, "numa_all_nodes"));
        // Create a cpu -> node mapping
        _cpu_to_node = new (ResourceObj::C_HEAP) GrowableArray<int>(0, true);
        rebuild_cpu_to_node_map();
        return true;
      }
    }
  }
  return false;
}

// rebuild_cpu_to_node_map() constructs a table mapping cpud id to node id.
// The table is later used in get_node_by_cpu().
void os::Linux::rebuild_cpu_to_node_map() {
  const size_t NCPUS = 32768; // Since the buffer size computation is very obscure
                              // in libnuma (possible values are starting from 16,
                              // and continuing up with every other power of 2, but less
                              // than the maximum number of CPUs supported by kernel), and
                              // is a subject to change (in libnuma version 2 the requirements
                              // are more reasonable) we'll just hardcode the number they use
                              // in the library.
  const size_t BitsPerCLong = sizeof(long) * CHAR_BIT;

  size_t cpu_num = os::active_processor_count();
  size_t cpu_map_size = NCPUS / BitsPerCLong;
  size_t cpu_map_valid_size =
    MIN2((cpu_num + BitsPerCLong - 1) / BitsPerCLong, cpu_map_size);

  cpu_to_node()->clear();
  cpu_to_node()->at_grow(cpu_num - 1);
  size_t node_num = numa_get_groups_num();

  unsigned long *cpu_map = NEW_C_HEAP_ARRAY(unsigned long, cpu_map_size);
  for (size_t i = 0; i < node_num; i++) {
    if (numa_node_to_cpus(i, cpu_map, cpu_map_size * sizeof(unsigned long)) != -1) {
      for (size_t j = 0; j < cpu_map_valid_size; j++) {
        if (cpu_map[j] != 0) {
          for (size_t k = 0; k < BitsPerCLong; k++) {
            if (cpu_map[j] & (1UL << k)) {
              cpu_to_node()->at_put(j * BitsPerCLong + k, i);
            }
          }
        }
      }
    }
  }
  FREE_C_HEAP_ARRAY(unsigned long, cpu_map);
}

int os::Linux::get_node_by_cpu(int cpu_id) {
  if (cpu_to_node() != NULL && cpu_id >= 0 && cpu_id < cpu_to_node()->length()) {
    return cpu_to_node()->at(cpu_id);
  }
  return -1;
}

GrowableArray<int>* os::Linux::_cpu_to_node;
os::Linux::sched_getcpu_func_t os::Linux::_sched_getcpu;
os::Linux::numa_node_to_cpus_func_t os::Linux::_numa_node_to_cpus;
os::Linux::numa_max_node_func_t os::Linux::_numa_max_node;
os::Linux::numa_available_func_t os::Linux::_numa_available;
os::Linux::numa_tonode_memory_func_t os::Linux::_numa_tonode_memory;
os::Linux::numa_interleave_memory_func_t os::Linux::_numa_interleave_memory;
unsigned long* os::Linux::_numa_all_nodes;

bool os::uncommit_memory(char* addr, size_t size) {
  return ::mmap(addr, size, PROT_NONE,
                MAP_PRIVATE|MAP_FIXED|MAP_NORESERVE|MAP_ANONYMOUS, -1, 0)
    != MAP_FAILED;
}

// Linux uses a growable mapping for the stack, and if the mapping for
// the stack guard pages is not removed when we detach a thread the
// stack cannot grow beyond the pages where the stack guard was
// mapped.  If at some point later in the process the stack expands to
// that point, the Linux kernel cannot expand the stack any further
// because the guard pages are in the way, and a segfault occurs.
//
// However, it's essential not to split the stack region by unmapping
// a region (leaving a hole) that's already part of the stack mapping,
// so if the stack mapping has already grown beyond the guard pages at
// the time we create them, we have to truncate the stack mapping.
// So, we need to know the extent of the stack mapping when
// create_stack_guard_pages() is called.

// Find the bounds of the stack mapping.  Return true for success.
//
// We only need this for stacks that are growable: at the time of
// writing thread stacks don't use growable mappings (i.e. those
// creeated with MAP_GROWSDOWN), and aren't marked "[stack]", so this
// only applies to the main thread.
static bool
get_stack_bounds(uintptr_t *bottom, uintptr_t *top)
{
  FILE *f = fopen("/proc/self/maps", "r");
  if (f == NULL)
    return false;

  while (!feof(f)) {
    size_t dummy;
    char *str = NULL;
    ssize_t len = getline(&str, &dummy, f);
    if (len == -1) {
      fclose(f);
      return false;
    }

    if (len > 0 && str[len-1] == '\n') {
      str[len-1] = 0;
      len--;
    }

    static const char *stack_str = "[stack]";
    if (len > (ssize_t)strlen(stack_str)
       && (strcmp(str + len - strlen(stack_str), stack_str) == 0)) {
      if (sscanf(str, "%" SCNxPTR "-%" SCNxPTR, bottom, top) == 2) {
        uintptr_t sp = (uintptr_t)__builtin_frame_address(0);
        if (sp >= *bottom && sp <= *top) {
          free(str);
          fclose(f);
          return true;
        }
      }
    }
    free(str);
  }
  fclose(f);
  return false;
}

// If the (growable) stack mapping already extends beyond the point
// where we're going to put our guard pages, truncate the mapping at
// that point by munmap()ping it.  This ensures that when we later
// munmap() the guard pages we don't leave a hole in the stack
// mapping.
bool os::create_stack_guard_pages(char* addr, size_t size) {
  uintptr_t stack_extent, stack_base;
  if (get_stack_bounds(&stack_extent, &stack_base)) {
    if (stack_extent < (uintptr_t)addr)
      ::munmap((void*)stack_extent, (uintptr_t)addr - stack_extent);
  }

  return os::commit_memory(addr, size);
}

// If this is a growable mapping, remove the guard pages entirely by
// munmap()ping them.  If not, just call uncommit_memory().
bool os::remove_stack_guard_pages(char* addr, size_t size) {
  uintptr_t stack_extent, stack_base;
  if (get_stack_bounds(&stack_extent, &stack_base)) {
    return ::munmap(addr, size) == 0;
  }

  return os::uncommit_memory(addr, size);
}

static address _highest_vm_reserved_address = NULL;

// If 'fixed' is true, anon_mmap() will attempt to reserve anonymous memory
// at 'requested_addr'. If there are existing memory mappings at the same
// location, however, they will be overwritten. If 'fixed' is false,
// 'requested_addr' is only treated as a hint, the return value may or
// may not start from the requested address. Unlike Linux mmap(), this
// function returns NULL to indicate failure.
static char* anon_mmap(char* requested_addr, size_t bytes, bool fixed) {
  char * addr;
  int flags;

  flags = MAP_PRIVATE | MAP_NORESERVE | MAP_ANONYMOUS;
  if (fixed) {
    assert((uintptr_t)requested_addr % os::Linux::page_size() == 0, "unaligned address");
    flags |= MAP_FIXED;
  }

  // Map uncommitted pages PROT_READ and PROT_WRITE, change access
  // to PROT_EXEC if executable when we commit the page.
  addr = (char*)::mmap(requested_addr, bytes, PROT_READ|PROT_WRITE,
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

char* os::reserve_memory(size_t bytes, char* requested_addr,
                         size_t alignment_hint) {
  return anon_mmap(requested_addr, bytes, (requested_addr != NULL));
}

bool os::release_memory(char* addr, size_t size) {
  return anon_munmap(addr, size);
}

static address highest_vm_reserved_address() {
  return _highest_vm_reserved_address;
}

static bool linux_mprotect(char* addr, size_t size, int prot) {
  // Linux wants the mprotect address argument to be page aligned.
  char* bottom = (char*)align_size_down((intptr_t)addr, os::Linux::page_size());

  // According to SUSv3, mprotect() should only be used with mappings
  // established by mmap(), and mmap() always maps whole pages. Unaligned
  // 'addr' likely indicates problem in the VM (e.g. trying to change
  // protection of malloc'ed or statically allocated memory). Check the
  // caller if you hit this assert.
  assert(addr == bottom, "sanity check");

  size = align_size_up(pointer_delta(addr, bottom, 1) + size, os::Linux::page_size());
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
  return linux_mprotect(addr, bytes, p);
}

bool os::guard_memory(char* addr, size_t size) {
  return linux_mprotect(addr, size, PROT_NONE);
}

bool os::unguard_memory(char* addr, size_t size) {
  return linux_mprotect(addr, size, PROT_READ|PROT_WRITE);
}

// Large page support

static size_t _large_page_size = 0;

bool os::large_page_init() {
  if (!UseLargePages) return false;

  if (LargePageSizeInBytes) {
    _large_page_size = LargePageSizeInBytes;
  } else {
    // large_page_size on Linux is used to round up heap size. x86 uses either
    // 2M or 4M page, depending on whether PAE (Physical Address Extensions)
    // mode is enabled. AMD64/EM64T uses 2M page in 64bit mode. IA64 can use
    // page as large as 256M.
    //
    // Here we try to figure out page size by parsing /proc/meminfo and looking
    // for a line with the following format:
    //    Hugepagesize:     2048 kB
    //
    // If we can't determine the value (e.g. /proc is not mounted, or the text
    // format has been changed), we'll use the largest page size supported by
    // the processor.

#ifndef ZERO
    _large_page_size = IA32_ONLY(4 * M) AMD64_ONLY(2 * M) IA64_ONLY(256 * M) SPARC_ONLY(4 * M);
#endif // ZERO

    FILE *fp = fopen("/proc/meminfo", "r");
    if (fp) {
      while (!feof(fp)) {
        int x = 0;
        char buf[16];
        if (fscanf(fp, "Hugepagesize: %d", &x) == 1) {
          if (x && fgets(buf, sizeof(buf), fp) && strcmp(buf, " kB\n") == 0) {
            _large_page_size = x * K;
            break;
          }
        } else {
          // skip to next line
          for (;;) {
            int ch = fgetc(fp);
            if (ch == EOF || ch == (int)'\n') break;
          }
        }
      }
      fclose(fp);
    }
  }

  const size_t default_page_size = (size_t)Linux::page_size();
  if (_large_page_size > default_page_size) {
    _page_sizes[0] = _large_page_size;
    _page_sizes[1] = default_page_size;
    _page_sizes[2] = 0;
  }

  // Large page support is available on 2.6 or newer kernel, some vendors
  // (e.g. Redhat) have backported it to their 2.4 based distributions.
  // We optimistically assume the support is available. If later it turns out
  // not true, VM will automatically switch to use regular page size.
  return true;
}

#ifndef SHM_HUGETLB
#define SHM_HUGETLB 04000
#endif

char* os::reserve_memory_special(size_t bytes, char* req_addr, bool exec) {
  // "exec" is passed in but not used.  Creating the shared image for
  // the code cache doesn't have an SHM_X executable permission to check.
  assert(UseLargePages, "only for large pages");

  key_t key = IPC_PRIVATE;
  char *addr;

  bool warn_on_failure = UseLargePages &&
                        (!FLAG_IS_DEFAULT(UseLargePages) ||
                         !FLAG_IS_DEFAULT(LargePageSizeInBytes)
                        );
  char msg[128];

  // Create a large shared memory region to attach to based on size.
  // Currently, size is the total size of the heap
  int shmid = shmget(key, bytes, SHM_HUGETLB|IPC_CREAT|SHM_R|SHM_W);
  if (shmid == -1) {
     // Possible reasons for shmget failure:
     // 1. shmmax is too small for Java heap.
     //    > check shmmax value: cat /proc/sys/kernel/shmmax
     //    > increase shmmax value: echo "0xffffffff" > /proc/sys/kernel/shmmax
     // 2. not enough large page memory.
     //    > check available large pages: cat /proc/meminfo
     //    > increase amount of large pages:
     //          echo new_value > /proc/sys/vm/nr_hugepages
     //      Note 1: different Linux may use different name for this property,
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

  return addr;
}

bool os::release_memory_special(char* base, size_t bytes) {
  // detaching the SHM segment will also delete it, see reserve_memory_special()
  int rslt = shmdt(base);
  return rslt == 0;
}

size_t os::large_page_size() {
  return _large_page_size;
}

// Linux does not support anonymous mmap with large page memory. The only way
// to reserve large page memory without file backing is through SysV shared
// memory API. The entire memory region is committed and pinned upfront.
// Hopefully this will change in the future...
bool os::can_commit_large_page_memory() {
  return false;
}

bool os::can_execute_large_page_memory() {
  return false;
}

// Reserve memory at an arbitrary address, only if that area is
// available (and not reserved for something else).

char* os::attempt_reserve_memory_at(size_t bytes, char* requested_addr) {
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
  // fixed-stack LinuxThreads. Because here we may attempt to reserve more
  // space than needed, it could confuse the collision detecting code. To
  // solve the problem, save current _highest_vm_reserved_address and
  // calculate the correct value before return.
  address old_highest = _highest_vm_reserved_address;

  // Linux mmap allows caller to pass an address as hint; give it a try first,
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
  return ::read(fd, buf, nBytes);
}

// TODO-FIXME: reconcile Solaris' os::sleep with the linux variation.
// Solaris uses poll(), linux uses park().
// Poll() is likely a better choice, assuming that Thread.interrupt()
// generates a SIGUSRx signal. Note that SIGUSR1 can interfere with
// SIGSEGV, see 4355769.

const int NANOSECS_PER_MILLISECS = 1000000;

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
        assert(!Linux::supports_monotonic_clock(), "time moving backwards");
      } else {
        millis -= (newtime - prevtime) / NANOSECS_PER_MILLISECS;
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
        assert(!Linux::supports_monotonic_clock(), "time moving backwards");
      } else {
        millis -= (newtime - prevtime) / NANOSECS_PER_MILLISECS;
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

// Note: Normal Linux applications are run with SCHED_OTHER policy. SCHED_OTHER
// only supports dynamic priority, static priority must be zero. For real-time
// applications, Linux supports SCHED_RR which allows static priority (1-99).
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

int os::java_to_os_priority[MaxPriority + 1] = {
  19,              // 0 Entry should never be used

   4,              // 1 MinPriority
   3,              // 2
   2,              // 3

   1,              // 4
   0,              // 5 NormPriority
  -1,              // 6

  -2,              // 7
  -3,              // 8
  -4,              // 9 NearMaxPriority

  -5               // 10 MaxPriority
};

static int prio_init() {
  if (ThreadPriorityPolicy == 1) {
    // Only root can raise thread priority. Don't allow ThreadPriorityPolicy=1
    // if effective uid is not root. Perhaps, a more elegant way of doing
    // this is to test CAP_SYS_NICE capability, but that will require libcap.so
    if (geteuid() != 0) {
      if (!FLAG_IS_DEFAULT(ThreadPriorityPolicy)) {
        warning("-XX:ThreadPriorityPolicy requires root privilege on Linux");
      }
      ThreadPriorityPolicy = 0;
    }
  }
  return 0;
}

OSReturn os::set_native_priority(Thread* thread, int newpri) {
  if ( !UseThreadPriorities || ThreadPriorityPolicy == 0 ) return OS_OK;

  int ret = setpriority(PRIO_PROCESS, thread->osthread()->thread_id(), newpri);
  return (ret == 0) ? OS_OK : OS_ERR;
}

OSReturn os::get_native_priority(const Thread* const thread, int *priority_ptr) {
  if ( !UseThreadPriorities || ThreadPriorityPolicy == 0 ) {
    *priority_ptr = java_to_os_priority[NormPriority];
    return OS_OK;
  }

  errno = 0;
  *priority_ptr = getpriority(PRIO_PROCESS, thread->osthread()->thread_id());
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

  // notify the suspend action is completed, we have now resumed
  osthread->sr.clear_suspended();
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
// Currently only ever called on the VMThread
//
static void SR_handler(int sig, siginfo_t* siginfo, ucontext_t* context) {
  // Save and restore errno to avoid confusing native code with EINTR
  // after sigsuspend.
  int old_errno = errno;

  Thread* thread = Thread::current();
  OSThread* osthread = thread->osthread();
  assert(thread->is_VM_thread(), "Must be VMThread");
  // read current suspend action
  int action = osthread->sr.suspend_action();
  if (action == SR_SUSPEND) {
    suspend_save_context(osthread, siginfo, context);

    // Notify the suspend action is about to be completed. do_suspend()
    // waits until SR_SUSPENDED is set and then returns. We will wait
    // here for a resume signal and that completes the suspend-other
    // action. do_suspend/do_resume is always called as a pair from
    // the same thread - so there are no races

    // notify the caller
    osthread->sr.set_suspended();

    sigset_t suspend_set;  // signals for sigsuspend()

    // get current set of blocked signals and unblock resume signal
    pthread_sigmask(SIG_BLOCK, NULL, &suspend_set);
    sigdelset(&suspend_set, SR_signum);

    // wait here until we are resumed
    do {
      sigsuspend(&suspend_set);
      // ignore all returns until we get a resume signal
    } while (osthread->sr.suspend_action() != SR_CONTINUE);

    resume_clear_context(osthread);

  } else {
    assert(action == SR_CONTINUE, "unexpected sr action");
    // nothing special to do - just leave the handler
  }

  errno = old_errno;
}


static int SR_initialize() {
  struct sigaction act;
  char *s;
  /* Get signal number to use for suspend/resume */
  if ((s = ::getenv("_JAVA_SR_SIGNUM")) != 0) {
    int sig = ::strtol(s, 0, 10);
    if (sig > 0 || sig < _NSIG) {
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
  // supported Linux platforms). Note that LinuxThreads need to block
  // this signal for all threads to work properly. So we don't have
  // to use hard-coded signal number when setting up the mask.
  pthread_sigmask(SIG_BLOCK, NULL, &act.sa_mask);

  if (sigaction(SR_signum, &act, 0) == -1) {
    return -1;
  }

  // Save signal flag
  os::Linux::set_our_sigflags(SR_signum, act.sa_flags);
  return 0;
}

static int SR_finalize() {
  return 0;
}


// returns true on success and false on error - really an error is fatal
// but this seems the normal response to library errors
static bool do_suspend(OSThread* osthread) {
  // mark as suspended and send signal
  osthread->sr.set_suspend_action(SR_SUSPEND);
  int status = pthread_kill(osthread->pthread_id(), SR_signum);
  assert_status(status == 0, status, "pthread_kill");

  // check status and wait until notified of suspension
  if (status == 0) {
    for (int i = 0; !osthread->sr.is_suspended(); i++) {
      os::yield_all(i);
    }
    osthread->sr.set_suspend_action(SR_NONE);
    return true;
  }
  else {
    osthread->sr.set_suspend_action(SR_NONE);
    return false;
  }
}

static void do_resume(OSThread* osthread) {
  assert(osthread->sr.is_suspended(), "thread should be suspended");
  osthread->sr.set_suspend_action(SR_CONTINUE);

  int status = pthread_kill(osthread->pthread_id(), SR_signum);
  assert_status(status == 0, status, "pthread_kill");
  // check status and wait unit notified of resumption
  if (status == 0) {
    for (int i = 0; osthread->sr.is_suspended(); i++) {
      os::yield_all(i);
    }
  }
  osthread->sr.set_suspend_action(SR_NONE);
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
extern "C" int
JVM_handle_linux_signal(int signo, siginfo_t* siginfo,
                        void* ucontext, int abort_if_unrecognized);

void signalHandler(int sig, siginfo_t* info, void* uc) {
  assert(info != NULL && uc != NULL, "it must be old kernel");
  JVM_handle_linux_signal(sig, info, uc, true);
}


// This boolean allows users to forward their own non-matching signals
// to JVM_handle_linux_signal, harmlessly.
bool os::Linux::signal_handlers_are_installed = false;

// For signal-chaining
struct sigaction os::Linux::sigact[MAXSIGNUM];
unsigned int os::Linux::sigs = 0;
bool os::Linux::libjsig_is_loaded = false;
typedef struct sigaction *(*get_signal_t)(int);
get_signal_t os::Linux::get_signal_action = NULL;

struct sigaction* os::Linux::get_chained_signal_action(int sig) {
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

bool os::Linux::chained_handler(int sig, siginfo_t* siginfo, void* context) {
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

struct sigaction* os::Linux::get_preinstalled_handler(int sig) {
  if ((( (unsigned int)1 << sig ) & sigs) != 0) {
    return &sigact[sig];
  }
  return NULL;
}

void os::Linux::save_preinstalled_handler(int sig, struct sigaction& oldAct) {
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  sigact[sig] = oldAct;
  sigs |= (unsigned int)1 << sig;
}

// for diagnostic
int os::Linux::sigflags[MAXSIGNUM];

int os::Linux::get_our_sigflags(int sig) {
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  return sigflags[sig];
}

void os::Linux::set_our_sigflags(int sig, int flags) {
  assert(sig > 0 && sig < MAXSIGNUM, "vm signal out of expected range");
  sigflags[sig] = flags;
}

void os::Linux::set_signal_handler(int sig, bool set_installed) {
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

void os::Linux::install_signal_handlers() {
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

    if (libjsig_is_loaded) {
      // Tell libjsig jvm finishes setting signal handlers
      (*end_signal_setting)();
    }

    // We don't activate signal checker if libjsig is in place, we trust ourselves
    // and if UserSignalHandler is installed all bets are off
    if (CheckJNICalls) {
      if (libjsig_is_loaded) {
        tty->print_cr("Info: libjsig is activated, all active signal checking is disabled");
        check_signals = false;
      }
      if (AllowUserSignalHandlers) {
        tty->print_cr("Info: AllowUserSignalHandlers is activated, all active signal checking is disabled");
        check_signals = false;
      }
    }
  }
}

// This is the fastest way to get thread cpu time on Linux.
// Returns cpu time (user+sys) for any thread, not only for current.
// POSIX compliant clocks are implemented in the kernels 2.6.16+.
// It might work on 2.6.10+ with a special kernel/glibc patch.
// For reference, please, see IEEE Std 1003.1-2004:
//   http://www.unix.org/single_unix_specification

jlong os::Linux::fast_thread_cpu_time(clockid_t clockid) {
  struct timespec tp;
  int rc = os::Linux::clock_gettime(clockid, &tp);
  assert(rc == 0, "clock_gettime is expected to return 0 code");

  return (tp.tv_sec * SEC_IN_NANOSECS) + tp.tv_nsec;
}

/////
// glibc on Linux platform uses non-documented flag
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
    if((int)sa.sa_flags != os::Linux::get_our_sigflags(sig)) {
      st->print(
                ", flags was changed from " PTR32_FORMAT ", consider using jsig library",
                os::Linux::get_our_sigflags(sig));
    }
  }
  st->cr();
}


#define DO_SIGNAL_CHECK(sig) \
  if (!sigismember(&check_signal_done, sig)) \
    os::Linux::check_signal_handler(sig)

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

void os::Linux::check_signal_handler(int sig) {
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
  } else if(os::Linux::get_our_sigflags(sig) != 0 && (int)act.sa_flags != os::Linux::get_our_sigflags(sig)) {
    tty->print("Warning: %s handler flags ", exception_name(sig, buf, O_BUFLEN));
    tty->print("expected:" PTR32_FORMAT, os::Linux::get_our_sigflags(sig));
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

  // With LinuxThreads the JavaMain thread pid (primordial thread)
  // is different than the pid of the java launcher thread.
  // So, on Linux, the launcher thread pid is passed to the VM
  // via the sun.java.launcher.pid property.
  // Use this property instead of getpid() if it was correctly passed.
  // See bug 6351349.
  pid_t java_launcher_pid = (pid_t) Arguments::sun_java_launcher_pid();

  _initial_pid = (java_launcher_pid > 0) ? java_launcher_pid : getpid();

  clock_tics_per_sec = sysconf(_SC_CLK_TCK);

  init_random(1234567);

  ThreadCritical::initialize();

  Linux::set_page_size(sysconf(_SC_PAGESIZE));
  if (Linux::page_size() == -1) {
    fatal(err_msg("os_linux.cpp: os::init: sysconf failed (%s)",
                  strerror(errno)));
  }
  init_page_sizes((size_t) Linux::page_size());

  Linux::initialize_system_info();

  // main_thread points to the aboriginal thread
  Linux::_main_thread = pthread_self();

  Linux::clock_init();
  initial_time_count = os::elapsed_counter();
  pthread_mutex_init(&dl_mutex, NULL);
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
  Linux::fast_thread_clock_init();

  // Allocate a single page and mark it as readable for safepoint polling
  address polling_page = (address) ::mmap(NULL, Linux::page_size(), PROT_READ, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0);
  guarantee( polling_page != MAP_FAILED, "os::init_2: failed to allocate polling page" );

  os::set_polling_page( polling_page );

#ifndef PRODUCT
  if(Verbose && PrintMiscellaneous)
    tty->print("[SafePoint Polling address: " INTPTR_FORMAT "]\n", (intptr_t)polling_page);
#endif

  if (!UseMembar) {
    address mem_serialize_page = (address) ::mmap(NULL, Linux::page_size(), PROT_READ | PROT_WRITE, MAP_PRIVATE|MAP_ANONYMOUS, -1, 0);
    guarantee( mem_serialize_page != NULL, "mmap Failed for memory serialize page");
    os::set_memory_serialize_page( mem_serialize_page );

#ifndef PRODUCT
    if(Verbose && PrintMiscellaneous)
      tty->print("[Memory Serialize  Page address: " INTPTR_FORMAT "]\n", (intptr_t)mem_serialize_page);
#endif
  }

  FLAG_SET_DEFAULT(UseLargePages, os::large_page_init());

  // initialize suspend/resume support - must do this before signal_sets_init()
  if (SR_initialize() != 0) {
    perror("SR_initialize failed");
    return JNI_ERR;
  }

  Linux::signal_sets_init();
  Linux::install_signal_handlers();

  size_t threadStackSizeInBytes = ThreadStackSize * K;
  if (threadStackSizeInBytes != 0 &&
      threadStackSizeInBytes < Linux::min_stack_allowed) {
        tty->print_cr("\nThe stack size specified is too small, "
                      "Specify at least %dk",
                      Linux::min_stack_allowed / K);
        return JNI_ERR;
  }

  // Make the stack size a multiple of the page size so that
  // the yellow/red zones can be guarded.
  JavaThread::set_stack_size_at_create(round_to(threadStackSizeInBytes,
        vm_page_size()));

  Linux::capture_initial_stack(JavaThread::stack_size_at_create());

  Linux::libpthread_init();
  if (PrintMiscellaneous && (Verbose || WizardMode)) {
     tty->print_cr("[HotSpot is running with %s, %s(%s)]\n",
          Linux::glibc_version(), Linux::libpthread_version(),
          Linux::is_floating_stack() ? "floating stack" : "fixed stack");
  }

  if (UseNUMA) {
    if (!Linux::libnuma_init()) {
      UseNUMA = false;
    } else {
      if ((Linux::numa_max_node() < 1)) {
        // There's only one node(they start from 0), disable NUMA.
        UseNUMA = false;
      }
    }
    if (!UseNUMA && ForceNUMA) {
      UseNUMA = true;
    }
  }

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

  // Initialize lock used to serialize thread creation (see os::create_thread)
  Linux::set_createThread_lock(new Mutex(Mutex::leaf, "createThread_lock", false));

  // Initialize HPI.
  jint hpi_result = hpi::initialize();
  if (hpi_result != JNI_OK) {
    tty->print_cr("There was an error trying to initialize the HPI library.");
    return hpi_result;
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

  return JNI_OK;
}

// Mark the polling page as unreadable
void os::make_polling_page_unreadable(void) {
  if( !guard_memory((char*)_polling_page, Linux::page_size()) )
    fatal("Could not disable polling page");
};

// Mark the polling page as readable
void os::make_polling_page_readable(void) {
  if( !linux_mprotect((char *)_polling_page, Linux::page_size(), PROT_READ)) {
    fatal("Could not enable polling page");
  }
};

int os::active_processor_count() {
  // Linux doesn't yet have a (official) notion of processor sets,
  // so just return the number of online processors.
  int online_cpus = ::sysconf(_SC_NPROCESSORS_ONLN);
  assert(online_cpus > 0 && online_cpus <= processor_count(), "sanity check");
  return online_cpus;
}

bool os::distribute_processes(uint length, uint* distribution) {
  // Not yet implemented.
  return false;
}

bool os::bind_to_processor(uint processor_id) {
  // Not yet implemented.
  return false;
}

///

// Suspends the target using the signal mechanism and then grabs the PC before
// resuming the target. Used by the flat-profiler only
ExtendedPC os::get_thread_pc(Thread* thread) {
  // Make sure that it is called by the watcher for the VMThread
  assert(Thread::current()->is_Watcher_thread(), "Must be watcher");
  assert(thread->is_VM_thread(), "Can only be called for VMThread");

  ExtendedPC epc;

  OSThread* osthread = thread->osthread();
  if (do_suspend(osthread)) {
    if (osthread->ucontext() != NULL) {
      epc = os::Linux::ucontext_get_pc(osthread->ucontext());
    } else {
      // NULL context is unexpected, double-check this is the VMThread
      guarantee(thread->is_VM_thread(), "can only be called for VMThread");
    }
    do_resume(osthread);
  }
  // failure means pthread_kill failed for some reason - arguably this is
  // a fatal problem, but such problems are ignored elsewhere

  return epc;
}

int os::Linux::safe_cond_timedwait(pthread_cond_t *_cond, pthread_mutex_t *_mutex, const struct timespec *_abstime)
{
   if (is_NPTL()) {
      return pthread_cond_timedwait(_cond, _mutex, _abstime);
   } else {
#ifndef IA64
      // 6292965: LinuxThreads pthread_cond_timedwait() resets FPU control
      // word back to default 64bit precision if condvar is signaled. Java
      // wants 53bit precision.  Save and restore current value.
      int fpu = get_fpu_control_word();
#endif // IA64
      int status = pthread_cond_timedwait(_cond, _mutex, _abstime);
#ifndef IA64
      set_fpu_control_word(fpu);
#endif // IA64
      return status;
   }
}

////////////////////////////////////////////////////////////////////////////////
// debug support

#ifndef PRODUCT
static address same_page(address x, address y) {
  int page_bits = -os::vm_page_size();
  if ((intptr_t(x) & page_bits) == (intptr_t(y) & page_bits))
    return x;
  else if (x > y)
    return (address)(intptr_t(y) | ~page_bits) + 1;
  else
    return (address)(intptr_t(y) & page_bits);
}

bool os::find(address addr) {
  Dl_info dlinfo;
  memset(&dlinfo, 0, sizeof(dlinfo));
  if (dladdr(addr, &dlinfo)) {
    tty->print(PTR_FORMAT ": ", addr);
    if (dlinfo.dli_sname != NULL) {
      tty->print("%s+%#x", dlinfo.dli_sname,
                 addr - (intptr_t)dlinfo.dli_saddr);
    } else if (dlinfo.dli_fname) {
      tty->print("<offset %#x>", addr - (intptr_t)dlinfo.dli_fbase);
    } else {
      tty->print("<absolute address>");
    }
    if (dlinfo.dli_fname) {
      tty->print(" in %s", dlinfo.dli_fname);
    }
    if (dlinfo.dli_fbase) {
      tty->print(" at " PTR_FORMAT, dlinfo.dli_fbase);
    }
    tty->cr();

    if (Verbose) {
      // decode some bytes around the PC
      address begin = same_page(addr-40, addr);
      address end   = same_page(addr+40, addr);
      address       lowest = (address) dlinfo.dli_sname;
      if (!lowest)  lowest = (address) dlinfo.dli_fbase;
      if (begin < lowest)  begin = lowest;
      Dl_info dlinfo2;
      if (dladdr(end, &dlinfo2) && dlinfo2.dli_saddr != dlinfo.dli_saddr
          && end > dlinfo2.dli_saddr && dlinfo2.dli_saddr > begin)
        end = (address) dlinfo2.dli_saddr;
      Disassembler::decode(begin, end);
    }
    return true;
  }
  return false;
}

#endif

////////////////////////////////////////////////////////////////////////////////
// misc

// This does not do anything on Linux. This is basically a hook for being
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
  hpi::native_path(strcpy(pathbuf, path));
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

// Map a block of memory.
char* os::map_memory(int fd, const char* file_name, size_t file_offset,
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
char* os::remap_memory(int fd, const char* file_name, size_t file_offset,
                       char *addr, size_t bytes, bool read_only,
                       bool allow_exec) {
  // same as map_memory() on this OS
  return os::map_memory(fd, file_name, file_offset, addr, bytes, read_only,
                        allow_exec);
}


// Unmap a block of memory.
bool os::unmap_memory(char* addr, size_t bytes) {
  return munmap(addr, bytes) == 0;
}

static jlong slow_thread_cpu_time(Thread *thread, bool user_sys_cpu_time);

static clockid_t thread_cpu_clockid(Thread* thread) {
  pthread_t tid = thread->osthread()->pthread_id();
  clockid_t clockid;

  // Get thread clockid
  int rc = os::Linux::pthread_getcpuclockid(tid, &clockid);
  assert(rc == 0, "pthread_getcpuclockid is expected to return 0 code");
  return clockid;
}

// current_thread_cpu_time(bool) and thread_cpu_time(Thread*, bool)
// are used by JVM M&M and JVMTI to get user+sys or user CPU time
// of a thread.
//
// current_thread_cpu_time() and thread_cpu_time(Thread*) returns
// the fast estimate available on the platform.

jlong os::current_thread_cpu_time() {
  if (os::Linux::supports_fast_thread_cpu_time()) {
    return os::Linux::fast_thread_cpu_time(CLOCK_THREAD_CPUTIME_ID);
  } else {
    // return user + sys since the cost is the same
    return slow_thread_cpu_time(Thread::current(), true /* user + sys */);
  }
}

jlong os::thread_cpu_time(Thread* thread) {
  // consistent with what current_thread_cpu_time() returns
  if (os::Linux::supports_fast_thread_cpu_time()) {
    return os::Linux::fast_thread_cpu_time(thread_cpu_clockid(thread));
  } else {
    return slow_thread_cpu_time(thread, true /* user + sys */);
  }
}

jlong os::current_thread_cpu_time(bool user_sys_cpu_time) {
  if (user_sys_cpu_time && os::Linux::supports_fast_thread_cpu_time()) {
    return os::Linux::fast_thread_cpu_time(CLOCK_THREAD_CPUTIME_ID);
  } else {
    return slow_thread_cpu_time(Thread::current(), user_sys_cpu_time);
  }
}

jlong os::thread_cpu_time(Thread *thread, bool user_sys_cpu_time) {
  if (user_sys_cpu_time && os::Linux::supports_fast_thread_cpu_time()) {
    return os::Linux::fast_thread_cpu_time(thread_cpu_clockid(thread));
  } else {
    return slow_thread_cpu_time(thread, user_sys_cpu_time);
  }
}

//
//  -1 on error.
//

static jlong slow_thread_cpu_time(Thread *thread, bool user_sys_cpu_time) {
  static bool proc_pid_cpu_avail = true;
  static bool proc_task_unchecked = true;
  static const char *proc_stat_path = "/proc/%d/stat";
  pid_t  tid = thread->osthread()->thread_id();
  int i;
  char *s;
  char stat[2048];
  int statlen;
  char proc_name[64];
  int count;
  long sys_time, user_time;
  char string[64];
  int idummy;
  long ldummy;
  FILE *fp;

  // We first try accessing /proc/<pid>/cpu since this is faster to
  // process.  If this file is not present (linux kernels 2.5 and above)
  // then we open /proc/<pid>/stat.
  if ( proc_pid_cpu_avail ) {
    sprintf(proc_name, "/proc/%d/cpu", tid);
    fp =  fopen(proc_name, "r");
    if ( fp != NULL ) {
      count = fscanf( fp, "%s %lu %lu\n", string, &user_time, &sys_time);
      fclose(fp);
      if ( count != 3 ) return -1;

      if (user_sys_cpu_time) {
        return ((jlong)sys_time + (jlong)user_time) * (1000000000 / clock_tics_per_sec);
      } else {
        return (jlong)user_time * (1000000000 / clock_tics_per_sec);
      }
    }
    else proc_pid_cpu_avail = false;
  }

  // The /proc/<tid>/stat aggregates per-process usage on
  // new Linux kernels 2.6+ where NPTL is supported.
  // The /proc/self/task/<tid>/stat still has the per-thread usage.
  // See bug 6328462.
  // There can be no directory /proc/self/task on kernels 2.4 with NPTL
  // and possibly in some other cases, so we check its availability.
  if (proc_task_unchecked && os::Linux::is_NPTL()) {
    // This is executed only once
    proc_task_unchecked = false;
    fp = fopen("/proc/self/task", "r");
    if (fp != NULL) {
      proc_stat_path = "/proc/self/task/%d/stat";
      fclose(fp);
    }
  }

  sprintf(proc_name, proc_stat_path, tid);
  fp = fopen(proc_name, "r");
  if ( fp == NULL ) return -1;
  statlen = fread(stat, 1, 2047, fp);
  stat[statlen] = '\0';
  fclose(fp);

  // Skip pid and the command string. Note that we could be dealing with
  // weird command names, e.g. user could decide to rename java launcher
  // to "java 1.4.2 :)", then the stat file would look like
  //                1234 (java 1.4.2 :)) R ... ...
  // We don't really need to know the command string, just find the last
  // occurrence of ")" and then start parsing from there. See bug 4726580.
  s = strrchr(stat, ')');
  i = 0;
  if (s == NULL ) return -1;

  // Skip blank chars
  do s++; while (isspace(*s));

  count = sscanf(s,"%*c %d %d %d %d %d %lu %lu %lu %lu %lu %lu %lu",
                 &idummy, &idummy, &idummy, &idummy, &idummy,
                 &ldummy, &ldummy, &ldummy, &ldummy, &ldummy,
                 &user_time, &sys_time);
  if ( count != 12 ) return -1;
  if (user_sys_cpu_time) {
    return ((jlong)sys_time + (jlong)user_time) * (1000000000 / clock_tics_per_sec);
  } else {
    return (jlong)user_time * (1000000000 / clock_tics_per_sec);
  }
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

// System loadavg support.  Returns -1 if load average cannot be obtained.
// Linux doesn't yet have a (official) notion of processor sets,
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
    close(fd);
    while (::stat(filename, &buf) == 0) {
      (void)::poll(NULL, 0, 100);
    }
  } else {
    jio_fprintf(stderr,
      "Could not open pause file '%s', continuing immediately.\n", filename);
  }
}

extern "C" {

/**
 * NOTE: the following code is to keep the green threads code
 * in the libjava.so happy. Once the green threads is removed,
 * these code will no longer be needed.
 */
int
jdk_waitpid(pid_t pid, int* status, int options) {
    return waitpid(pid, status, options);
}

int
fork1() {
    return fork();
}

int
jdk_sem_init(sem_t *sem, int pshared, unsigned int value) {
    return sem_init(sem, pshared, value);
}

int
jdk_sem_post(sem_t *sem) {
    return sem_post(sem);
}

int
jdk_sem_wait(sem_t *sem) {
    return sem_wait(sem);
}

int
jdk_pthread_sigmask(int how , const sigset_t* newmask, sigset_t* oldmask) {
    return pthread_sigmask(how , newmask, oldmask);
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

static struct timespec* compute_abstime(timespec* abstime, jlong millis) {
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
        if (status == ETIME) { status = EINTR; }
        assert_status(status == 0 || status == EINTR, status, "cond_wait");
     }
     -- _nParked ;

    // In theory we could move the ST of 0 into _Event past the unlock(),
    // but then we'd need a MEMBAR after the ST.
    _Event = 0 ;
     status = pthread_mutex_unlock(_mutex);
     assert_status(status == 0, status, "mutex_unlock");
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
    status = os::Linux::safe_cond_timedwait(_cond, _mutex, &abst);
    if (status != 0 && WorkAroundNPTLTimedWaitHang) {
      pthread_cond_destroy (_cond);
      pthread_cond_init (_cond, NULL) ;
    }
    assert_status(status == 0 || status == EINTR ||
                  status == ETIME || status == ETIMEDOUT,
                  status, "cond_timedwait");
    if (!FilterSpuriousWakeups) break ;                 // previous semantics
    if (status == ETIME || status == ETIMEDOUT) break ;
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
  return ret;
}

void os::PlatformEvent::unpark() {
  int v, AnyWaiters ;
  for (;;) {
      v = _Event ;
      if (v > 0) {
         // The LD of _Event could have reordered or be satisfied
         // by a read-aside from this processor's write buffer.
         // To avoid problems execute a barrier and then
         // ratify the value.
         OrderAccess::fence() ;
         if (_Event == v) return ;
         continue ;
      }
      if (Atomic::cmpxchg (v+1, &_Event, v) == v) break ;
  }
  if (v < 0) {
     // Wait for the thread associated with the event to vacate
     int status = pthread_mutex_lock(_mutex);
     assert_status(status == 0, status, "mutex_lock");
     AnyWaiters = _nParked ;
     assert (AnyWaiters == 0 || AnyWaiters == 1, "invariant") ;
     if (AnyWaiters != 0 && WorkAroundNPTLTimedWaitHang) {
        AnyWaiters = 0 ;
        pthread_cond_signal (_cond);
     }
     status = pthread_mutex_unlock(_mutex);
     assert_status(status == 0, status, "mutex_unlock");
     if (AnyWaiters != 0) {
        status = pthread_cond_signal(_cond);
        assert_status(status == 0, status, "cond_signal");
     }
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
 * The solaris and linux implementations of park/unpark are fairly
 * conservative for now, but can be improved. They currently use a
 * mutex/condvar pair, plus a a count.
 * Park decrements count if > 0, else does a condvar wait.  Unpark
 * sets count to 1 and signals condvar.  Only one thread ever waits
 * on the condvar. Contention seen when trying to park implies that someone
 * is unparking you, so don't wait. And spurious returns are fine, so there
 * is no need to track notifications.
 */


#define NANOSECS_PER_SEC 1000000000
#define NANOSECS_PER_MILLISEC 1000000
#define MAX_SECS 100000000
/*
 * This code is common to linux and solaris and will be moved to a
 * common place in dolphin.
 *
 * The passed in time value is either a relative time in nanoseconds
 * or an absolute time in milliseconds. Either way it has to be unpacked
 * into suitable seconds and nanoseconds components and stored in the
 * given timespec structure.
 * Given time is a 64-bit value and the time_t used in the timespec is only
 * a signed-32-bit value (except on 64-bit Linux) we have to watch for
 * overflow if times way in the future are given. Further on Solaris versions
 * prior to 10 there is a restriction (see cond_timedwait) that the specified
 * number of seconds, in abstime, is less than current_time  + 100,000,000.
 * As it will be 28 years before "now + 100000000" will overflow we can
 * ignore overflow and just impose a hard-limit on seconds using the value
 * of "now + 100,000,000". This places a limit on the timeout of about 3.17
 * years from "now".
 */

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
      _counter = 0 ;
      OrderAccess::fence();
      return ;
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
  if (time < 0) { // don't wait at all
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
    OrderAccess::fence();
    return;
  }

#ifdef ASSERT
  // Don't catch signals while blocked; let the running threads have the signals.
  // (This allows a debugger to break into the running thread.)
  sigset_t oldsigs;
  sigset_t* allowdebug_blocked = os::Linux::allowdebug_blocked_signals();
  pthread_sigmask(SIG_BLOCK, allowdebug_blocked, &oldsigs);
#endif

  OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
  jt->set_suspend_equivalent();
  // cleared by handle_special_suspend_equivalent_condition() or java_suspend_self()

  if (time == 0) {
    status = pthread_cond_wait (_cond, _mutex) ;
  } else {
    status = os::Linux::safe_cond_timedwait (_cond, _mutex, &absTime) ;
    if (status != 0 && WorkAroundNPTLTimedWaitHang) {
      pthread_cond_destroy (_cond) ;
      pthread_cond_init    (_cond, NULL);
    }
  }
  assert_status(status == 0 || status == EINTR ||
                status == ETIME || status == ETIMEDOUT,
                status, "cond_timedwait");

#ifdef ASSERT
  pthread_sigmask(SIG_SETMASK, &oldsigs, NULL);
#endif

  _counter = 0 ;
  status = pthread_mutex_unlock(_mutex) ;
  assert_status(status == 0, status, "invariant") ;
  // If externally suspended while waiting, re-suspend
  if (jt->handle_special_suspend_equivalent_condition()) {
    jt->java_suspend_self();
  }

  OrderAccess::fence();
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


extern char** environ;

#ifndef __NR_fork
#define __NR_fork IA32_ONLY(2) IA64_ONLY(not defined) AMD64_ONLY(57)
#endif

#ifndef __NR_execve
#define __NR_execve IA32_ONLY(11) IA64_ONLY(1033) AMD64_ONLY(59)
#endif

// Run the specified command in a separate process. Return its exit value,
// or -1 on failure (e.g. can't fork a new process).
// Unlike system(), this function can be called from signal handler. It
// doesn't block SIGINT et al.
int os::fork_and_exec(char* cmd) {
  const char * argv[4] = {"sh", "-c", cmd, NULL};

  // fork() in LinuxThreads/NPTL is not async-safe. It needs to run
  // pthread_atfork handlers and reset pthread library. All we need is a
  // separate process to execve. Make a direct syscall to fork process.
  // On IA64 there's no fork syscall, we have to use fork() and hope for
  // the best...
  pid_t pid = NOT_IA64(syscall(__NR_fork);)
              IA64_ONLY(fork();)

  if (pid < 0) {
    // fork failed
    return -1;

  } else if (pid == 0) {
    // child process

    // execve() in LinuxThreads will call pthread_kill_other_threads_np()
    // first to kill every thread on the thread list. Because this list is
    // not reset by fork() (see notes above), execve() will instead kill
    // every thread in the parent process. We know this is the only thread
    // in the new process, so make a system call directly.
    // IA64 should use normal execve() from glibc to match the glibc fork()
    // above.
    NOT_IA64(syscall(__NR_execve, "/bin/sh", argv, environ);)
    IA64_ONLY(execve("/bin/sh", (char* const*)argv, environ);)

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

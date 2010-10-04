/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

// do not include  precompiled  header file
# include "incls/_os_solaris.cpp.incl"

// put OS-includes here
# include <dlfcn.h>
# include <errno.h>
# include <link.h>
# include <poll.h>
# include <pthread.h>
# include <pwd.h>
# include <schedctl.h>
# include <setjmp.h>
# include <signal.h>
# include <stdio.h>
# include <alloca.h>
# include <sys/filio.h>
# include <sys/ipc.h>
# include <sys/lwp.h>
# include <sys/machelf.h>     // for elf Sym structure used by dladdr1
# include <sys/mman.h>
# include <sys/processor.h>
# include <sys/procset.h>
# include <sys/pset.h>
# include <sys/resource.h>
# include <sys/shm.h>
# include <sys/socket.h>
# include <sys/stat.h>
# include <sys/systeminfo.h>
# include <sys/time.h>
# include <sys/times.h>
# include <sys/types.h>
# include <sys/wait.h>
# include <sys/utsname.h>
# include <thread.h>
# include <unistd.h>
# include <sys/priocntl.h>
# include <sys/rtpriocntl.h>
# include <sys/tspriocntl.h>
# include <sys/iapriocntl.h>
# include <sys/loadavg.h>
# include <string.h>

# define _STRUCTURED_PROC 1  //  this gets us the new structured proc interfaces of 5.6 & later
# include <sys/procfs.h>     //  see comment in <sys/procfs.h>

#define MAX_PATH (2 * K)

// for timer info max values which include all bits
#define ALL_64_BITS CONST64(0xFFFFFFFFFFFFFFFF)

#ifdef _GNU_SOURCE
// See bug #6514594
extern "C" int madvise(caddr_t, size_t, int);
extern "C"  int memcntl(caddr_t addr, size_t len, int cmd, caddr_t  arg,
     int attr, int mask);
#endif //_GNU_SOURCE

/*
  MPSS Changes Start.
  The JVM binary needs to be built and run on pre-Solaris 9
  systems, but the constants needed by MPSS are only in Solaris 9
  header files.  They are textually replicated here to allow
  building on earlier systems.  Once building on Solaris 8 is
  no longer a requirement, these #defines can be replaced by ordinary
  system .h inclusion.

  In earlier versions of the  JDK and Solaris, we used ISM for large pages.
  But ISM requires shared memory to achieve this and thus has many caveats.
  MPSS is a fully transparent and is a cleaner way to get large pages.
  Although we still require keeping ISM for backward compatiblitiy as well as
  giving the opportunity to use large pages on older systems it is
  recommended that MPSS be used for Solaris 9 and above.

*/

#ifndef MC_HAT_ADVISE

struct memcntl_mha {
  uint_t          mha_cmd;        /* command(s) */
  uint_t          mha_flags;
  size_t          mha_pagesize;
};
#define MC_HAT_ADVISE   7       /* advise hat map size */
#define MHA_MAPSIZE_VA  0x1     /* set preferred page size */
#define MAP_ALIGN       0x200   /* addr specifies alignment */

#endif
// MPSS Changes End.


// Here are some liblgrp types from sys/lgrp_user.h to be able to
// compile on older systems without this header file.

#ifndef MADV_ACCESS_LWP
# define  MADV_ACCESS_LWP         7       /* next LWP to access heavily */
#endif
#ifndef MADV_ACCESS_MANY
# define  MADV_ACCESS_MANY        8       /* many processes to access heavily */
#endif

#ifndef LGRP_RSRC_CPU
# define LGRP_RSRC_CPU           0       /* CPU resources */
#endif
#ifndef LGRP_RSRC_MEM
# define LGRP_RSRC_MEM           1       /* memory resources */
#endif

// Some more macros from sys/mman.h that are not present in Solaris 8.

#ifndef MAX_MEMINFO_CNT
/*
 * info_req request type definitions for meminfo
 * request types starting with MEMINFO_V are used for Virtual addresses
 * and should not be mixed with MEMINFO_PLGRP which is targeted for Physical
 * addresses
 */
# define MEMINFO_SHIFT           16
# define MEMINFO_MASK            (0xFF << MEMINFO_SHIFT)
# define MEMINFO_VPHYSICAL       (0x01 << MEMINFO_SHIFT) /* get physical addr */
# define MEMINFO_VLGRP           (0x02 << MEMINFO_SHIFT) /* get lgroup */
# define MEMINFO_VPAGESIZE       (0x03 << MEMINFO_SHIFT) /* size of phys page */
# define MEMINFO_VREPLCNT        (0x04 << MEMINFO_SHIFT) /* no. of replica */
# define MEMINFO_VREPL           (0x05 << MEMINFO_SHIFT) /* physical replica */
# define MEMINFO_VREPL_LGRP      (0x06 << MEMINFO_SHIFT) /* lgrp of replica */
# define MEMINFO_PLGRP           (0x07 << MEMINFO_SHIFT) /* lgroup for paddr */

/* maximum number of addresses meminfo() can process at a time */
# define MAX_MEMINFO_CNT 256

/* maximum number of request types */
# define MAX_MEMINFO_REQ 31
#endif

// see thr_setprio(3T) for the basis of these numbers
#define MinimumPriority 0
#define NormalPriority  64
#define MaximumPriority 127

// Values for ThreadPriorityPolicy == 1
int prio_policy1[MaxPriority+1] = { -99999, 0, 16, 32, 48, 64,
                                        80, 96, 112, 124, 127 };

// System parameters used internally
static clock_t clock_tics_per_sec = 100;

// For diagnostics to print a message once. see run_periodic_checks
static bool check_addr0_done = false;
static sigset_t check_signal_done;
static bool check_signals = true;

address os::Solaris::handler_start;  // start pc of thr_sighndlrinfo
address os::Solaris::handler_end;    // end pc of thr_sighndlrinfo

address os::Solaris::_main_stack_base = NULL;  // 4352906 workaround


// "default" initializers for missing libc APIs
extern "C" {
  static int lwp_mutex_init(mutex_t *mx, int scope, void *arg) { memset(mx, 0, sizeof(mutex_t)); return 0; }
  static int lwp_mutex_destroy(mutex_t *mx)                 { return 0; }

  static int lwp_cond_init(cond_t *cv, int scope, void *arg){ memset(cv, 0, sizeof(cond_t)); return 0; }
  static int lwp_cond_destroy(cond_t *cv)                   { return 0; }
}

// "default" initializers for pthread-based synchronization
extern "C" {
  static int pthread_mutex_default_init(mutex_t *mx, int scope, void *arg) { memset(mx, 0, sizeof(mutex_t)); return 0; }
  static int pthread_cond_default_init(cond_t *cv, int scope, void *arg){ memset(cv, 0, sizeof(cond_t)); return 0; }
}

// Thread Local Storage
// This is common to all Solaris platforms so it is defined here,
// in this common file.
// The declarations are in the os_cpu threadLS*.hpp files.
//
// Static member initialization for TLS
Thread* ThreadLocalStorage::_get_thread_cache[ThreadLocalStorage::_pd_cache_size] = {NULL};

#ifndef PRODUCT
#define _PCT(n,d)       ((100.0*(double)(n))/(double)(d))

int ThreadLocalStorage::_tcacheHit = 0;
int ThreadLocalStorage::_tcacheMiss = 0;

void ThreadLocalStorage::print_statistics() {
  int total = _tcacheMiss+_tcacheHit;
  tty->print_cr("Thread cache hits %d misses %d total %d percent %f\n",
                _tcacheHit, _tcacheMiss, total, _PCT(_tcacheHit, total));
}
#undef _PCT
#endif // PRODUCT

Thread* ThreadLocalStorage::get_thread_via_cache_slowly(uintptr_t raw_id,
                                                        int index) {
  Thread *thread = get_thread_slow();
  if (thread != NULL) {
    address sp = os::current_stack_pointer();
    guarantee(thread->_stack_base == NULL ||
              (sp <= thread->_stack_base &&
                 sp >= thread->_stack_base - thread->_stack_size) ||
               is_error_reported(),
              "sp must be inside of selected thread stack");

    thread->_self_raw_id = raw_id;  // mark for quick retrieval
    _get_thread_cache[ index ] = thread;
  }
  return thread;
}


static const double all_zero[ sizeof(Thread) / sizeof(double) + 1 ] = {0};
#define NO_CACHED_THREAD ((Thread*)all_zero)

void ThreadLocalStorage::pd_set_thread(Thread* thread) {

  // Store the new value before updating the cache to prevent a race
  // between get_thread_via_cache_slowly() and this store operation.
  os::thread_local_storage_at_put(ThreadLocalStorage::thread_index(), thread);

  // Update thread cache with new thread if setting on thread create,
  // or NO_CACHED_THREAD (zeroed) thread if resetting thread on exit.
  uintptr_t raw = pd_raw_thread_id();
  int ix = pd_cache_index(raw);
  _get_thread_cache[ix] = thread == NULL ? NO_CACHED_THREAD : thread;
}

void ThreadLocalStorage::pd_init() {
  for (int i = 0; i < _pd_cache_size; i++) {
    _get_thread_cache[i] = NO_CACHED_THREAD;
  }
}

// Invalidate all the caches (happens to be the same as pd_init).
void ThreadLocalStorage::pd_invalidate_all() { pd_init(); }

#undef NO_CACHED_THREAD

// END Thread Local Storage

static inline size_t adjust_stack_size(address base, size_t size) {
  if ((ssize_t)size < 0) {
    // 4759953: Compensate for ridiculous stack size.
    size = max_intx;
  }
  if (size > (size_t)base) {
    // 4812466: Make sure size doesn't allow the stack to wrap the address space.
    size = (size_t)base;
  }
  return size;
}

static inline stack_t get_stack_info() {
  stack_t st;
  int retval = thr_stksegment(&st);
  st.ss_size = adjust_stack_size((address)st.ss_sp, st.ss_size);
  assert(retval == 0, "incorrect return value from thr_stksegment");
  assert((address)&st < (address)st.ss_sp, "Invalid stack base returned");
  assert((address)&st > (address)st.ss_sp-st.ss_size, "Invalid stack size returned");
  return st;
}

address os::current_stack_base() {
  int r = thr_main() ;
  guarantee (r == 0 || r == 1, "CR6501650 or CR6493689") ;
  bool is_primordial_thread = r;

  // Workaround 4352906, avoid calls to thr_stksegment by
  // thr_main after the first one (it looks like we trash
  // some data, causing the value for ss_sp to be incorrect).
  if (!is_primordial_thread || os::Solaris::_main_stack_base == NULL) {
    stack_t st = get_stack_info();
    if (is_primordial_thread) {
      // cache initial value of stack base
      os::Solaris::_main_stack_base = (address)st.ss_sp;
    }
    return (address)st.ss_sp;
  } else {
    guarantee(os::Solaris::_main_stack_base != NULL, "Attempt to use null cached stack base");
    return os::Solaris::_main_stack_base;
  }
}

size_t os::current_stack_size() {
  size_t size;

  int r = thr_main() ;
  guarantee (r == 0 || r == 1, "CR6501650 or CR6493689") ;
  if(!r) {
    size = get_stack_info().ss_size;
  } else {
    struct rlimit limits;
    getrlimit(RLIMIT_STACK, &limits);
    size = adjust_stack_size(os::Solaris::_main_stack_base, (size_t)limits.rlim_cur);
  }
  // base may not be page aligned
  address base = current_stack_base();
  address bottom = (address)align_size_up((intptr_t)(base - size), os::vm_page_size());;
  return (size_t)(base - bottom);
}

struct tm* os::localtime_pd(const time_t* clock, struct tm*  res) {
  return localtime_r(clock, res);
}

// interruptible infrastructure

// setup_interruptible saves the thread state before going into an
// interruptible system call.
// The saved state is used to restore the thread to
// its former state whether or not an interrupt is received.
// Used by classloader os::read
// hpi calls skip this layer and stay in _thread_in_native

void os::Solaris::setup_interruptible(JavaThread* thread) {

  JavaThreadState thread_state = thread->thread_state();

  assert(thread_state != _thread_blocked, "Coming from the wrong thread");
  assert(thread_state != _thread_in_native, "Native threads skip setup_interruptible");
  OSThread* osthread = thread->osthread();
  osthread->set_saved_interrupt_thread_state(thread_state);
  thread->frame_anchor()->make_walkable(thread);
  ThreadStateTransition::transition(thread, thread_state, _thread_blocked);
}

// Version of setup_interruptible() for threads that are already in
// _thread_blocked. Used by os_sleep().
void os::Solaris::setup_interruptible_already_blocked(JavaThread* thread) {
  thread->frame_anchor()->make_walkable(thread);
}

JavaThread* os::Solaris::setup_interruptible() {
  JavaThread* thread = (JavaThread*)ThreadLocalStorage::thread();
  setup_interruptible(thread);
  return thread;
}

void os::Solaris::try_enable_extended_io() {
  typedef int (*enable_extended_FILE_stdio_t)(int, int);

  if (!UseExtendedFileIO) {
    return;
  }

  enable_extended_FILE_stdio_t enabler =
    (enable_extended_FILE_stdio_t) dlsym(RTLD_DEFAULT,
                                         "enable_extended_FILE_stdio");
  if (enabler) {
    enabler(-1, -1);
  }
}


#ifdef ASSERT

JavaThread* os::Solaris::setup_interruptible_native() {
  JavaThread* thread = (JavaThread*)ThreadLocalStorage::thread();
  JavaThreadState thread_state = thread->thread_state();
  assert(thread_state == _thread_in_native, "Assumed thread_in_native");
  return thread;
}

void os::Solaris::cleanup_interruptible_native(JavaThread* thread) {
  JavaThreadState thread_state = thread->thread_state();
  assert(thread_state == _thread_in_native, "Assumed thread_in_native");
}
#endif

// cleanup_interruptible reverses the effects of setup_interruptible
// setup_interruptible_already_blocked() does not need any cleanup.

void os::Solaris::cleanup_interruptible(JavaThread* thread) {
  OSThread* osthread = thread->osthread();

  ThreadStateTransition::transition(thread, _thread_blocked, osthread->saved_interrupt_thread_state());
}

// I/O interruption related counters called in _INTERRUPTIBLE

void os::Solaris::bump_interrupted_before_count() {
  RuntimeService::record_interrupted_before_count();
}

void os::Solaris::bump_interrupted_during_count() {
  RuntimeService::record_interrupted_during_count();
}

static int _processors_online = 0;

         jint os::Solaris::_os_thread_limit = 0;
volatile jint os::Solaris::_os_thread_count = 0;

julong os::available_memory() {
  return Solaris::available_memory();
}

julong os::Solaris::available_memory() {
  return (julong)sysconf(_SC_AVPHYS_PAGES) * os::vm_page_size();
}

julong os::Solaris::_physical_memory = 0;

julong os::physical_memory() {
   return Solaris::physical_memory();
}

julong os::allocatable_physical_memory(julong size) {
#ifdef _LP64
   return size;
#else
   julong result = MIN2(size, (julong)3835*M);
   if (!is_allocatable(result)) {
     // Memory allocations will be aligned but the alignment
     // is not known at this point.  Alignments will
     // be at most to LargePageSizeInBytes.  Protect
     // allocations from alignments up to illegal
     // values. If at this point 2G is illegal.
     julong reasonable_size = (julong)2*G - 2 * LargePageSizeInBytes;
     result =  MIN2(size, reasonable_size);
   }
   return result;
#endif
}

static hrtime_t first_hrtime = 0;
static const hrtime_t hrtime_hz = 1000*1000*1000;
const int LOCK_BUSY = 1;
const int LOCK_FREE = 0;
const int LOCK_INVALID = -1;
static volatile hrtime_t max_hrtime = 0;
static volatile int max_hrtime_lock = LOCK_FREE;     // Update counter with LSB as lock-in-progress


void os::Solaris::initialize_system_info() {
  set_processor_count(sysconf(_SC_NPROCESSORS_CONF));
  _processors_online = sysconf (_SC_NPROCESSORS_ONLN);
  _physical_memory = (julong)sysconf(_SC_PHYS_PAGES) * (julong)sysconf(_SC_PAGESIZE);
}

int os::active_processor_count() {
  int online_cpus = sysconf(_SC_NPROCESSORS_ONLN);
  pid_t pid = getpid();
  psetid_t pset = PS_NONE;
  // Are we running in a processor set or is there any processor set around?
  if (pset_bind(PS_QUERY, P_PID, pid, &pset) == 0) {
    uint_t pset_cpus;
    // Query the number of cpus available to us.
    if (pset_info(pset, NULL, &pset_cpus, NULL) == 0) {
      assert(pset_cpus > 0 && pset_cpus <= online_cpus, "sanity check");
      _processors_online = pset_cpus;
      return pset_cpus;
    }
  }
  // Otherwise return number of online cpus
  return online_cpus;
}

static bool find_processors_in_pset(psetid_t        pset,
                                    processorid_t** id_array,
                                    uint_t*         id_length) {
  bool result = false;
  // Find the number of processors in the processor set.
  if (pset_info(pset, NULL, id_length, NULL) == 0) {
    // Make up an array to hold their ids.
    *id_array = NEW_C_HEAP_ARRAY(processorid_t, *id_length);
    // Fill in the array with their processor ids.
    if (pset_info(pset, NULL, id_length, *id_array) == 0) {
      result = true;
    }
  }
  return result;
}

// Callers of find_processors_online() must tolerate imprecise results --
// the system configuration can change asynchronously because of DR
// or explicit psradm operations.
//
// We also need to take care that the loop (below) terminates as the
// number of processors online can change between the _SC_NPROCESSORS_ONLN
// request and the loop that builds the list of processor ids.   Unfortunately
// there's no reliable way to determine the maximum valid processor id,
// so we use a manifest constant, MAX_PROCESSOR_ID, instead.  See p_online
// man pages, which claim the processor id set is "sparse, but
// not too sparse".  MAX_PROCESSOR_ID is used to ensure that we eventually
// exit the loop.
//
// In the future we'll be able to use sysconf(_SC_CPUID_MAX), but that's
// not available on S8.0.

static bool find_processors_online(processorid_t** id_array,
                                   uint*           id_length) {
  const processorid_t MAX_PROCESSOR_ID = 100000 ;
  // Find the number of processors online.
  *id_length = sysconf(_SC_NPROCESSORS_ONLN);
  // Make up an array to hold their ids.
  *id_array = NEW_C_HEAP_ARRAY(processorid_t, *id_length);
  // Processors need not be numbered consecutively.
  long found = 0;
  processorid_t next = 0;
  while (found < *id_length && next < MAX_PROCESSOR_ID) {
    processor_info_t info;
    if (processor_info(next, &info) == 0) {
      // NB, PI_NOINTR processors are effectively online ...
      if (info.pi_state == P_ONLINE || info.pi_state == P_NOINTR) {
        (*id_array)[found] = next;
        found += 1;
      }
    }
    next += 1;
  }
  if (found < *id_length) {
      // The loop above didn't identify the expected number of processors.
      // We could always retry the operation, calling sysconf(_SC_NPROCESSORS_ONLN)
      // and re-running the loop, above, but there's no guarantee of progress
      // if the system configuration is in flux.  Instead, we just return what
      // we've got.  Note that in the worst case find_processors_online() could
      // return an empty set.  (As a fall-back in the case of the empty set we
      // could just return the ID of the current processor).
      *id_length = found ;
  }

  return true;
}

static bool assign_distribution(processorid_t* id_array,
                                uint           id_length,
                                uint*          distribution,
                                uint           distribution_length) {
  // We assume we can assign processorid_t's to uint's.
  assert(sizeof(processorid_t) == sizeof(uint),
         "can't convert processorid_t to uint");
  // Quick check to see if we won't succeed.
  if (id_length < distribution_length) {
    return false;
  }
  // Assign processor ids to the distribution.
  // Try to shuffle processors to distribute work across boards,
  // assuming 4 processors per board.
  const uint processors_per_board = ProcessDistributionStride;
  // Find the maximum processor id.
  processorid_t max_id = 0;
  for (uint m = 0; m < id_length; m += 1) {
    max_id = MAX2(max_id, id_array[m]);
  }
  // The next id, to limit loops.
  const processorid_t limit_id = max_id + 1;
  // Make up markers for available processors.
  bool* available_id = NEW_C_HEAP_ARRAY(bool, limit_id);
  for (uint c = 0; c < limit_id; c += 1) {
    available_id[c] = false;
  }
  for (uint a = 0; a < id_length; a += 1) {
    available_id[id_array[a]] = true;
  }
  // Step by "boards", then by "slot", copying to "assigned".
  // NEEDS_CLEANUP: The assignment of processors should be stateful,
  //                remembering which processors have been assigned by
  //                previous calls, etc., so as to distribute several
  //                independent calls of this method.  What we'd like is
  //                It would be nice to have an API that let us ask
  //                how many processes are bound to a processor,
  //                but we don't have that, either.
  //                In the short term, "board" is static so that
  //                subsequent distributions don't all start at board 0.
  static uint board = 0;
  uint assigned = 0;
  // Until we've found enough processors ....
  while (assigned < distribution_length) {
    // ... find the next available processor in the board.
    for (uint slot = 0; slot < processors_per_board; slot += 1) {
      uint try_id = board * processors_per_board + slot;
      if ((try_id < limit_id) && (available_id[try_id] == true)) {
        distribution[assigned] = try_id;
        available_id[try_id] = false;
        assigned += 1;
        break;
      }
    }
    board += 1;
    if (board * processors_per_board + 0 >= limit_id) {
      board = 0;
    }
  }
  if (available_id != NULL) {
    FREE_C_HEAP_ARRAY(bool, available_id);
  }
  return true;
}

bool os::distribute_processes(uint length, uint* distribution) {
  bool result = false;
  // Find the processor id's of all the available CPUs.
  processorid_t* id_array  = NULL;
  uint           id_length = 0;
  // There are some races between querying information and using it,
  // since processor sets can change dynamically.
  psetid_t pset = PS_NONE;
  // Are we running in a processor set?
  if ((pset_bind(PS_QUERY, P_PID, P_MYID, &pset) == 0) && pset != PS_NONE) {
    result = find_processors_in_pset(pset, &id_array, &id_length);
  } else {
    result = find_processors_online(&id_array, &id_length);
  }
  if (result == true) {
    if (id_length >= length) {
      result = assign_distribution(id_array, id_length, distribution, length);
    } else {
      result = false;
    }
  }
  if (id_array != NULL) {
    FREE_C_HEAP_ARRAY(processorid_t, id_array);
  }
  return result;
}

bool os::bind_to_processor(uint processor_id) {
  // We assume that a processorid_t can be stored in a uint.
  assert(sizeof(uint) == sizeof(processorid_t),
         "can't convert uint to processorid_t");
  int bind_result =
    processor_bind(P_LWPID,                       // bind LWP.
                   P_MYID,                        // bind current LWP.
                   (processorid_t) processor_id,  // id.
                   NULL);                         // don't return old binding.
  return (bind_result == 0);
}

bool os::getenv(const char* name, char* buffer, int len) {
  char* val = ::getenv( name );
  if ( val == NULL
  ||   strlen(val) + 1  >  len ) {
    if (len > 0)  buffer[0] = 0; // return a null string
    return false;
  }
  strcpy( buffer, val );
  return true;
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


void os::init_system_properties_values() {
  char arch[12];
  sysinfo(SI_ARCHITECTURE, arch, sizeof(arch));

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
#define free(p) FREE_C_HEAP_ARRAY(char, p)
#define getenv(n) ::getenv(n)

#define EXTENSIONS_DIR  "/lib/ext"
#define ENDORSED_DIR    "/lib/endorsed"
#define COMMON_DIR      "/usr/jdk/packages"

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
     */
    {
      // Use dlinfo() to determine the correct java.library.path.
      //
      // If we're launched by the Java launcher, and the user
      // does not set java.library.path explicitly on the commandline,
      // the Java launcher sets LD_LIBRARY_PATH for us and unsets
      // LD_LIBRARY_PATH_32 and LD_LIBRARY_PATH_64.  In this case
      // dlinfo returns LD_LIBRARY_PATH + crle settings (including
      // /usr/lib), which is exactly what we want.
      //
      // If the user does set java.library.path, it completely
      // overwrites this setting, and always has.
      //
      // If we're not launched by the Java launcher, we may
      // get here with any/all of the LD_LIBRARY_PATH[_32|64]
      // settings.  Again, dlinfo does exactly what we want.

      Dl_serinfo     _info, *info = &_info;
      Dl_serpath     *path;
      char*          library_path;
      char           *common_path;
      int            i;

      // determine search path count and required buffer size
      if (dlinfo(RTLD_SELF, RTLD_DI_SERINFOSIZE, (void *)info) == -1) {
        vm_exit_during_initialization("dlinfo SERINFOSIZE request", dlerror());
      }

      // allocate new buffer and initialize
      info = (Dl_serinfo*)malloc(_info.dls_size);
      if (info == NULL) {
        vm_exit_out_of_memory(_info.dls_size,
                              "init_system_properties_values info");
      }
      info->dls_size = _info.dls_size;
      info->dls_cnt = _info.dls_cnt;

      // obtain search path information
      if (dlinfo(RTLD_SELF, RTLD_DI_SERINFO, (void *)info) == -1) {
        free(info);
        vm_exit_during_initialization("dlinfo SERINFO request", dlerror());
      }

      path = &info->dls_serpath[0];

      // Note: Due to a legacy implementation, most of the library path
      // is set in the launcher.  This was to accomodate linking restrictions
      // on legacy Solaris implementations (which are no longer supported).
      // Eventually, all the library path setting will be done here.
      //
      // However, to prevent the proliferation of improperly built native
      // libraries, the new path component /usr/jdk/packages is added here.

      // Determine the actual CPU architecture.
      char cpu_arch[12];
      sysinfo(SI_ARCHITECTURE, cpu_arch, sizeof(cpu_arch));
#ifdef _LP64
      // If we are a 64-bit vm, perform the following translations:
      //   sparc   -> sparcv9
      //   i386    -> amd64
      if (strcmp(cpu_arch, "sparc") == 0)
        strcat(cpu_arch, "v9");
      else if (strcmp(cpu_arch, "i386") == 0)
        strcpy(cpu_arch, "amd64");
#endif

      // Construct the invariant part of ld_library_path. Note that the
      // space for the colon and the trailing null are provided by the
      // nulls included by the sizeof operator.
      size_t bufsize = sizeof(COMMON_DIR) + sizeof("/lib/") + strlen(cpu_arch);
      common_path = malloc(bufsize);
      if (common_path == NULL) {
        free(info);
        vm_exit_out_of_memory(bufsize,
                              "init_system_properties_values common_path");
      }
      sprintf(common_path, COMMON_DIR "/lib/%s", cpu_arch);

      // struct size is more than sufficient for the path components obtained
      // through the dlinfo() call, so only add additional space for the path
      // components explicitly added here.
      bufsize = info->dls_size + strlen(common_path);
      library_path = malloc(bufsize);
      if (library_path == NULL) {
        free(info);
        free(common_path);
        vm_exit_out_of_memory(bufsize,
                              "init_system_properties_values library_path");
      }
      library_path[0] = '\0';

      // Construct the desired Java library path from the linker's library
      // search path.
      //
      // For compatibility, it is optimal that we insert the additional path
      // components specific to the Java VM after those components specified
      // in LD_LIBRARY_PATH (if any) but before those added by the ld.so
      // infrastructure.
      if (info->dls_cnt == 0) { // Not sure this can happen, but allow for it
        strcpy(library_path, common_path);
      } else {
        int inserted = 0;
        for (i = 0; i < info->dls_cnt; i++, path++) {
          uint_t flags = path->dls_flags & LA_SER_MASK;
          if (((flags & LA_SER_LIBPATH) == 0) && !inserted) {
            strcat(library_path, common_path);
            strcat(library_path, os::path_separator());
            inserted = 1;
          }
          strcat(library_path, path->dls_name);
          strcat(library_path, os::path_separator());
        }
        // eliminate trailing path separator
        library_path[strlen(library_path)-1] = '\0';
      }

      // happens before argument parsing - can't use a trace flag
      // tty->print_raw("init_system_properties_values: native lib path: ");
      // tty->print_raw_cr(library_path);

      // callee copies into its own buffer
      Arguments::set_library_path(library_path);

      free(common_path);
      free(library_path);
      free(info);
    }

    /*
     * Extensions directories.
     *
     * Note that the space for the colon and the trailing null are provided
     * by the nulls included by the sizeof operator (so actually one byte more
     * than necessary is allocated).
     */
    {
        char *buf = (char *) malloc(strlen(Arguments::get_java_home()) +
            sizeof(EXTENSIONS_DIR) + sizeof(COMMON_DIR) +
            sizeof(EXTENSIONS_DIR));
        sprintf(buf, "%s" EXTENSIONS_DIR ":" COMMON_DIR EXTENSIONS_DIR,
            Arguments::get_java_home());
        Arguments::set_ext_dirs(buf);
    }

    /* Endorsed standards default directory. */
    {
        char * buf = malloc(strlen(Arguments::get_java_home()) + sizeof(ENDORSED_DIR));
        sprintf(buf, "%s" ENDORSED_DIR, Arguments::get_java_home());
        Arguments::set_endorsed_dirs(buf);
    }
  }

#undef malloc
#undef free
#undef getenv
#undef EXTENSIONS_DIR
#undef ENDORSED_DIR
#undef COMMON_DIR

}

void os::breakpoint() {
  BREAKPOINT;
}

bool os::obsolete_option(const JavaVMOption *option)
{
  if (!strncmp(option->optionString, "-Xt", 3)) {
    return true;
  } else if (!strncmp(option->optionString, "-Xtm", 4)) {
    return true;
  } else if (!strncmp(option->optionString, "-Xverifyheap", 12)) {
    return true;
  } else if (!strncmp(option->optionString, "-Xmaxjitcodesize", 16)) {
    return true;
  }
  return false;
}

bool os::Solaris::valid_stack_address(Thread* thread, address sp) {
  address  stackStart  = (address)thread->stack_base();
  address  stackEnd    = (address)(stackStart - (address)thread->stack_size());
  if (sp < stackStart && sp >= stackEnd ) return true;
  return false;
}

extern "C" void breakpoint() {
  // use debugger to set breakpoint here
}

// Returns an estimate of the current stack pointer. Result must be guaranteed to
// point into the calling threads stack, and be no lower than the current stack
// pointer.
address os::current_stack_pointer() {
  volatile int dummy;
  address sp = (address)&dummy + 8;     // %%%% need to confirm if this is right
  return sp;
}

static thread_t main_thread;

// Thread start routine for all new Java threads
extern "C" void* java_start(void* thread_addr) {
  // Try to randomize the cache line index of hot stack frames.
  // This helps when threads of the same stack traces evict each other's
  // cache lines. The threads can be either from the same JVM instance, or
  // from different JVM instances. The benefit is especially true for
  // processors with hyperthreading technology.
  static int counter = 0;
  int pid = os::current_process_id();
  alloca(((pid ^ counter++) & 7) * 128);

  int prio;
  Thread* thread = (Thread*)thread_addr;
  OSThread* osthr = thread->osthread();

  osthr->set_lwp_id( _lwp_self() );  // Store lwp in case we are bound
  thread->_schedctl = (void *) schedctl_init () ;

  if (UseNUMA) {
    int lgrp_id = os::numa_get_group_id();
    if (lgrp_id != -1) {
      thread->set_lgrp_id(lgrp_id);
    }
  }

  // If the creator called set priority before we started,
  // we need to call set priority now that we have an lwp.
  // Get the priority from libthread and set the priority
  // for the new Solaris lwp.
  if ( osthr->thread_id() != -1 ) {
    if ( UseThreadPriorities ) {
      thr_getprio(osthr->thread_id(), &prio);
      if (ThreadPriorityVerbose) {
        tty->print_cr("Starting Thread " INTPTR_FORMAT ", LWP is " INTPTR_FORMAT ", setting priority: %d\n",
                      osthr->thread_id(), osthr->lwp_id(), prio );
      }
      os::set_native_priority(thread, prio);
    }
  } else if (ThreadPriorityVerbose) {
    warning("Can't set priority in _start routine, thread id hasn't been set\n");
  }

  assert(osthr->get_state() == RUNNABLE, "invalid os thread state");

  // initialize signal mask for this thread
  os::Solaris::hotspot_sigmask(thread);

  thread->run();

  // One less thread is executing
  // When the VMThread gets here, the main thread may have already exited
  // which frees the CodeHeap containing the Atomic::dec code
  if (thread != VMThread::vm_thread() && VMThread::vm_thread() != NULL) {
    Atomic::dec(&os::Solaris::_os_thread_count);
  }

  if (UseDetachedThreads) {
    thr_exit(NULL);
    ShouldNotReachHere();
  }
  return NULL;
}

static OSThread* create_os_thread(Thread* thread, thread_t thread_id) {
  // Allocate the OSThread object
  OSThread* osthread = new OSThread(NULL, NULL);
  if (osthread == NULL) return NULL;

  // Store info on the Solaris thread into the OSThread
  osthread->set_thread_id(thread_id);
  osthread->set_lwp_id(_lwp_self());
  thread->_schedctl = (void *) schedctl_init () ;

  if (UseNUMA) {
    int lgrp_id = os::numa_get_group_id();
    if (lgrp_id != -1) {
      thread->set_lgrp_id(lgrp_id);
    }
  }

  if ( ThreadPriorityVerbose ) {
    tty->print_cr("In create_os_thread, Thread " INTPTR_FORMAT ", LWP is " INTPTR_FORMAT "\n",
                  osthread->thread_id(), osthread->lwp_id() );
  }

  // Initial thread state is INITIALIZED, not SUSPENDED
  osthread->set_state(INITIALIZED);

  return osthread;
}

void os::Solaris::hotspot_sigmask(Thread* thread) {

  //Save caller's signal mask
  sigset_t sigmask;
  thr_sigsetmask(SIG_SETMASK, NULL, &sigmask);
  OSThread *osthread = thread->osthread();
  osthread->set_caller_sigmask(sigmask);

  thr_sigsetmask(SIG_UNBLOCK, os::Solaris::unblocked_signals(), NULL);
  if (!ReduceSignalUsage) {
    if (thread->is_VM_thread()) {
      // Only the VM thread handles BREAK_SIGNAL ...
      thr_sigsetmask(SIG_UNBLOCK, vm_signals(), NULL);
    } else {
      // ... all other threads block BREAK_SIGNAL
      assert(!sigismember(vm_signals(), SIGINT), "SIGINT should not be blocked");
      thr_sigsetmask(SIG_BLOCK, vm_signals(), NULL);
    }
  }
}

bool os::create_attached_thread(JavaThread* thread) {
#ifdef ASSERT
  thread->verify_not_published();
#endif
  OSThread* osthread = create_os_thread(thread, thr_self());
  if (osthread == NULL) {
     return false;
  }

  // Initial thread state is RUNNABLE
  osthread->set_state(RUNNABLE);
  thread->set_osthread(osthread);

  // initialize signal mask for this thread
  // and save the caller's signal mask
  os::Solaris::hotspot_sigmask(thread);

  return true;
}

bool os::create_main_thread(JavaThread* thread) {
#ifdef ASSERT
  thread->verify_not_published();
#endif
  if (_starting_thread == NULL) {
    _starting_thread = create_os_thread(thread, main_thread);
     if (_starting_thread == NULL) {
        return false;
     }
  }

  // The primodial thread is runnable from the start
  _starting_thread->set_state(RUNNABLE);

  thread->set_osthread(_starting_thread);

  // initialize signal mask for this thread
  // and save the caller's signal mask
  os::Solaris::hotspot_sigmask(thread);

  return true;
}

// _T2_libthread is true if we believe we are running with the newer
// SunSoft lwp/libthread.so (2.8 patch, 2.9 default)
bool os::Solaris::_T2_libthread = false;

bool os::create_thread(Thread* thread, ThreadType thr_type, size_t stack_size) {
  // Allocate the OSThread object
  OSThread* osthread = new OSThread(NULL, NULL);
  if (osthread == NULL) {
    return false;
  }

  if ( ThreadPriorityVerbose ) {
    char *thrtyp;
    switch ( thr_type ) {
      case vm_thread:
        thrtyp = (char *)"vm";
        break;
      case cgc_thread:
        thrtyp = (char *)"cgc";
        break;
      case pgc_thread:
        thrtyp = (char *)"pgc";
        break;
      case java_thread:
        thrtyp = (char *)"java";
        break;
      case compiler_thread:
        thrtyp = (char *)"compiler";
        break;
      case watcher_thread:
        thrtyp = (char *)"watcher";
        break;
      default:
        thrtyp = (char *)"unknown";
        break;
    }
    tty->print_cr("In create_thread, creating a %s thread\n", thrtyp);
  }

  // Calculate stack size if it's not specified by caller.
  if (stack_size == 0) {
    // The default stack size 1M (2M for LP64).
    stack_size = (BytesPerWord >> 2) * K * K;

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
  stack_size = MAX2(stack_size, os::Solaris::min_stack_allowed);

  // Initial state is ALLOCATED but not INITIALIZED
  osthread->set_state(ALLOCATED);

  if (os::Solaris::_os_thread_count > os::Solaris::_os_thread_limit) {
    // We got lots of threads. Check if we still have some address space left.
    // Need to be at least 5Mb of unreserved address space. We do check by
    // trying to reserve some.
    const size_t VirtualMemoryBangSize = 20*K*K;
    char* mem = os::reserve_memory(VirtualMemoryBangSize);
    if (mem == NULL) {
      delete osthread;
      return false;
    } else {
      // Release the memory again
      os::release_memory(mem, VirtualMemoryBangSize);
    }
  }

  // Setup osthread because the child thread may need it.
  thread->set_osthread(osthread);

  // Create the Solaris thread
  // explicit THR_BOUND for T2_libthread case in case
  // that assumption is not accurate, but our alternate signal stack
  // handling is based on it which must have bound threads
  thread_t tid = 0;
  long     flags = (UseDetachedThreads ? THR_DETACHED : 0) | THR_SUSPENDED
                   | ((UseBoundThreads || os::Solaris::T2_libthread() ||
                       (thr_type == vm_thread) ||
                       (thr_type == cgc_thread) ||
                       (thr_type == pgc_thread) ||
                       (thr_type == compiler_thread && BackgroundCompilation)) ?
                      THR_BOUND : 0);
  int      status;

  // 4376845 -- libthread/kernel don't provide enough LWPs to utilize all CPUs.
  //
  // On multiprocessors systems, libthread sometimes under-provisions our
  // process with LWPs.  On a 30-way systems, for instance, we could have
  // 50 user-level threads in ready state and only 2 or 3 LWPs assigned
  // to our process.  This can result in under utilization of PEs.
  // I suspect the problem is related to libthread's LWP
  // pool management and to the kernel's SIGBLOCKING "last LWP parked"
  // upcall policy.
  //
  // The following code is palliative -- it attempts to ensure that our
  // process has sufficient LWPs to take advantage of multiple PEs.
  // Proper long-term cures include using user-level threads bound to LWPs
  // (THR_BOUND) or using LWP-based synchronization.  Note that there is a
  // slight timing window with respect to sampling _os_thread_count, but
  // the race is benign.  Also, we should periodically recompute
  // _processors_online as the min of SC_NPROCESSORS_ONLN and the
  // the number of PEs in our partition.  You might be tempted to use
  // THR_NEW_LWP here, but I'd recommend against it as that could
  // result in undesirable growth of the libthread's LWP pool.
  // The fix below isn't sufficient; for instance, it doesn't take into count
  // LWPs parked on IO.  It does, however, help certain CPU-bound benchmarks.
  //
  // Some pathologies this scheme doesn't handle:
  // *  Threads can block, releasing the LWPs.  The LWPs can age out.
  //    When a large number of threads become ready again there aren't
  //    enough LWPs available to service them.  This can occur when the
  //    number of ready threads oscillates.
  // *  LWPs/Threads park on IO, thus taking the LWP out of circulation.
  //
  // Finally, we should call thr_setconcurrency() periodically to refresh
  // the LWP pool and thwart the LWP age-out mechanism.
  // The "+3" term provides a little slop -- we want to slightly overprovision.

  if (AdjustConcurrency && os::Solaris::_os_thread_count < (_processors_online+3)) {
    if (!(flags & THR_BOUND)) {
      thr_setconcurrency (os::Solaris::_os_thread_count);       // avoid starvation
    }
  }
  // Although this doesn't hurt, we should warn of undefined behavior
  // when using unbound T1 threads with schedctl().  This should never
  // happen, as the compiler and VM threads are always created bound
  DEBUG_ONLY(
      if ((VMThreadHintNoPreempt || CompilerThreadHintNoPreempt) &&
          (!os::Solaris::T2_libthread() && (!(flags & THR_BOUND))) &&
          ((thr_type == vm_thread) || (thr_type == cgc_thread) ||
           (thr_type == pgc_thread) || (thr_type == compiler_thread && BackgroundCompilation))) {
         warning("schedctl behavior undefined when Compiler/VM/GC Threads are Unbound");
      }
  );


  // Mark that we don't have an lwp or thread id yet.
  // In case we attempt to set the priority before the thread starts.
  osthread->set_lwp_id(-1);
  osthread->set_thread_id(-1);

  status = thr_create(NULL, stack_size, java_start, thread, flags, &tid);
  if (status != 0) {
    if (PrintMiscellaneous && (Verbose || WizardMode)) {
      perror("os::create_thread");
    }
    thread->set_osthread(NULL);
    // Need to clean up stuff we've allocated so far
    delete osthread;
    return false;
  }

  Atomic::inc(&os::Solaris::_os_thread_count);

  // Store info on the Solaris thread into the OSThread
  osthread->set_thread_id(tid);

  // Remember that we created this thread so we can set priority on it
  osthread->set_vm_created();

  // Set the default thread priority otherwise use NormalPriority

  if ( UseThreadPriorities ) {
     thr_setprio(tid, (DefaultThreadPriority == -1) ?
                        java_to_os_priority[NormPriority] :
                        DefaultThreadPriority);
  }

  // Initial thread state is INITIALIZED, not SUSPENDED
  osthread->set_state(INITIALIZED);

  // The thread is returned suspended (in state INITIALIZED), and is started higher up in the call chain
  return true;
}

/* defined for >= Solaris 10. This allows builds on earlier versions
 *  of Solaris to take advantage of the newly reserved Solaris JVM signals
 *  With SIGJVM1, SIGJVM2, INTERRUPT_SIGNAL is SIGJVM1, ASYNC_SIGNAL is SIGJVM2
 *  and -XX:+UseAltSigs does nothing since these should have no conflict
 */
#if !defined(SIGJVM1)
#define SIGJVM1 39
#define SIGJVM2 40
#endif

debug_only(static bool signal_sets_initialized = false);
static sigset_t unblocked_sigs, vm_sigs, allowdebug_blocked_sigs;
int os::Solaris::_SIGinterrupt = INTERRUPT_SIGNAL;
int os::Solaris::_SIGasync = ASYNC_SIGNAL;

bool os::Solaris::is_sig_ignored(int sig) {
      struct sigaction oact;
      sigaction(sig, (struct sigaction*)NULL, &oact);
      void* ohlr = oact.sa_sigaction ? CAST_FROM_FN_PTR(void*,  oact.sa_sigaction)
                                     : CAST_FROM_FN_PTR(void*,  oact.sa_handler);
      if (ohlr == CAST_FROM_FN_PTR(void*, SIG_IGN))
           return true;
      else
           return false;
}

// Note: SIGRTMIN is a macro that calls sysconf() so it will
// dynamically detect SIGRTMIN value for the system at runtime, not buildtime
static bool isJVM1available() {
  return SIGJVM1 < SIGRTMIN;
}

void os::Solaris::signal_sets_init() {
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

  if (isJVM1available) {
    os::Solaris::set_SIGinterrupt(SIGJVM1);
    os::Solaris::set_SIGasync(SIGJVM2);
  } else if (UseAltSigs) {
    os::Solaris::set_SIGinterrupt(ALT_INTERRUPT_SIGNAL);
    os::Solaris::set_SIGasync(ALT_ASYNC_SIGNAL);
  } else {
    os::Solaris::set_SIGinterrupt(INTERRUPT_SIGNAL);
    os::Solaris::set_SIGasync(ASYNC_SIGNAL);
  }

  sigaddset(&unblocked_sigs, os::Solaris::SIGinterrupt());
  sigaddset(&unblocked_sigs, os::Solaris::SIGasync());

  if (!ReduceSignalUsage) {
   if (!os::Solaris::is_sig_ignored(SHUTDOWN1_SIGNAL)) {
      sigaddset(&unblocked_sigs, SHUTDOWN1_SIGNAL);
      sigaddset(&allowdebug_blocked_sigs, SHUTDOWN1_SIGNAL);
   }
   if (!os::Solaris::is_sig_ignored(SHUTDOWN2_SIGNAL)) {
      sigaddset(&unblocked_sigs, SHUTDOWN2_SIGNAL);
      sigaddset(&allowdebug_blocked_sigs, SHUTDOWN2_SIGNAL);
   }
   if (!os::Solaris::is_sig_ignored(SHUTDOWN3_SIGNAL)) {
      sigaddset(&unblocked_sigs, SHUTDOWN3_SIGNAL);
      sigaddset(&allowdebug_blocked_sigs, SHUTDOWN3_SIGNAL);
   }
  }
  // Fill in signals that are blocked by all but the VM thread.
  sigemptyset(&vm_sigs);
  if (!ReduceSignalUsage)
    sigaddset(&vm_sigs, BREAK_SIGNAL);
  debug_only(signal_sets_initialized = true);

  // For diagnostics only used in run_periodic_checks
  sigemptyset(&check_signal_done);
}

// These are signals that are unblocked while a thread is running Java.
// (For some reason, they get blocked by default.)
sigset_t* os::Solaris::unblocked_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &unblocked_sigs;
}

// These are the signals that are blocked while a (non-VM) thread is
// running Java. Only the VM thread handles these signals.
sigset_t* os::Solaris::vm_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &vm_sigs;
}

// These are signals that are blocked during cond_wait to allow debugger in
sigset_t* os::Solaris::allowdebug_blocked_signals() {
  assert(signal_sets_initialized, "Not initialized");
  return &allowdebug_blocked_sigs;
}

// First crack at OS-specific initialization, from inside the new thread.
void os::initialize_thread() {
  int r = thr_main() ;
  guarantee (r == 0 || r == 1, "CR6501650 or CR6493689") ;
  if (r) {
    JavaThread* jt = (JavaThread *)Thread::current();
    assert(jt != NULL,"Sanity check");
    size_t stack_size;
    address base = jt->stack_base();
    if (Arguments::created_by_java_launcher()) {
      // Use 2MB to allow for Solaris 7 64 bit mode.
      stack_size = JavaThread::stack_size_at_create() == 0
        ? 2048*K : JavaThread::stack_size_at_create();

      // There are rare cases when we may have already used more than
      // the basic stack size allotment before this method is invoked.
      // Attempt to allow for a normally sized java_stack.
      size_t current_stack_offset = (size_t)(base - (address)&stack_size);
      stack_size += ReservedSpace::page_align_size_down(current_stack_offset);
    } else {
      // 6269555: If we were not created by a Java launcher, i.e. if we are
      // running embedded in a native application, treat the primordial thread
      // as much like a native attached thread as possible.  This means using
      // the current stack size from thr_stksegment(), unless it is too large
      // to reliably setup guard pages.  A reasonable max size is 8MB.
      size_t current_size = current_stack_size();
      // This should never happen, but just in case....
      if (current_size == 0) current_size = 2 * K * K;
      stack_size = current_size > (8 * K * K) ? (8 * K * K) : current_size;
    }
    address bottom = (address)align_size_up((intptr_t)(base - stack_size), os::vm_page_size());;
    stack_size = (size_t)(base - bottom);

    assert(stack_size > 0, "Stack size calculation problem");

    if (stack_size > jt->stack_size()) {
      NOT_PRODUCT(
        struct rlimit limits;
        getrlimit(RLIMIT_STACK, &limits);
        size_t size = adjust_stack_size(base, (size_t)limits.rlim_cur);
        assert(size >= jt->stack_size(), "Stack size problem in main thread");
      )
      tty->print_cr(
        "Stack size of %d Kb exceeds current limit of %d Kb.\n"
        "(Stack sizes are rounded up to a multiple of the system page size.)\n"
        "See limit(1) to increase the stack size limit.",
        stack_size / K, jt->stack_size() / K);
      vm_exit(1);
    }
    assert(jt->stack_size() >= stack_size,
          "Attempt to map more stack than was allocated");
    jt->set_stack_size(stack_size);
  }

   // 5/22/01: Right now alternate signal stacks do not handle
   // throwing stack overflow exceptions, see bug 4463178
   // Until a fix is found for this, T2 will NOT imply alternate signal
   // stacks.
   // If using T2 libthread threads, install an alternate signal stack.
   // Because alternate stacks associate with LWPs on Solaris,
   // see sigaltstack(2), if using UNBOUND threads, or if UseBoundThreads
   // we prefer to explicitly stack bang.
   // If not using T2 libthread, but using UseBoundThreads any threads
   // (primordial thread, jni_attachCurrentThread) we do not create,
   // probably are not bound, therefore they can not have an alternate
   // signal stack. Since our stack banging code is generated and
   // is shared across threads, all threads must be bound to allow
   // using alternate signal stacks.  The alternative is to interpose
   // on _lwp_create to associate an alt sig stack with each LWP,
   // and this could be a problem when the JVM is embedded.
   // We would prefer to use alternate signal stacks with T2
   // Since there is currently no accurate way to detect T2
   // we do not. Assuming T2 when running T1 causes sig 11s or assertions
   // on installing alternate signal stacks


   // 05/09/03: removed alternate signal stack support for Solaris
   // The alternate signal stack mechanism is no longer needed to
   // handle stack overflow. This is now handled by allocating
   // guard pages (red zone) and stackbanging.
   // Initially the alternate signal stack mechanism was removed because
   // it did not work with T1 llibthread. Alternate
   // signal stacks MUST have all threads bound to lwps. Applications
   // can create their own threads and attach them without their being
   // bound under T1. This is frequently the case for the primordial thread.
   // If we were ever to reenable this mechanism we would need to
   // use the dynamic check for T2 libthread.

  os::Solaris::init_thread_fpu_state();
}



// Free Solaris resources related to the OSThread
void os::free_thread(OSThread* osthread) {
  assert(osthread != NULL, "os::free_thread but osthread not set");


  // We are told to free resources of the argument thread,
  // but we can only really operate on the current thread.
  // The main thread must take the VMThread down synchronously
  // before the main thread exits and frees up CodeHeap
  guarantee((Thread::current()->osthread() == osthread
     || (osthread == VMThread::vm_thread()->osthread())), "os::free_thread but not current thread");
  if (Thread::current()->osthread() == osthread) {
    // Restore caller's signal mask
    sigset_t sigmask = osthread->caller_sigmask();
    thr_sigsetmask(SIG_SETMASK, &sigmask, NULL);
  }
  delete osthread;
}

void os::pd_start_thread(Thread* thread) {
  int status = thr_continue(thread->osthread()->thread_id());
  assert_status(status == 0, status, "thr_continue failed");
}


intx os::current_thread_id() {
  return (intx)thr_self();
}

static pid_t _initial_pid = 0;

int os::current_process_id() {
  return (int)(_initial_pid ? _initial_pid : getpid());
}

int os::allocate_thread_local_storage() {
  // %%%       in Win32 this allocates a memory segment pointed to by a
  //           register.  Dan Stein can implement a similar feature in
  //           Solaris.  Alternatively, the VM can do the same thing
  //           explicitly: malloc some storage and keep the pointer in a
  //           register (which is part of the thread's context) (or keep it
  //           in TLS).
  // %%%       In current versions of Solaris, thr_self and TSD can
  //           be accessed via short sequences of displaced indirections.
  //           The value of thr_self is available as %g7(36).
  //           The value of thr_getspecific(k) is stored in %g7(12)(4)(k*4-4),
  //           assuming that the current thread already has a value bound to k.
  //           It may be worth experimenting with such access patterns,
  //           and later having the parameters formally exported from a Solaris
  //           interface.  I think, however, that it will be faster to
  //           maintain the invariant that %g2 always contains the
  //           JavaThread in Java code, and have stubs simply
  //           treat %g2 as a caller-save register, preserving it in a %lN.
  thread_key_t tk;
  if (thr_keycreate( &tk, NULL ) )
    fatal(err_msg("os::allocate_thread_local_storage: thr_keycreate failed "
                  "(%s)", strerror(errno)));
  return int(tk);
}

void os::free_thread_local_storage(int index) {
  // %%% don't think we need anything here
  // if ( pthread_key_delete((pthread_key_t) tk) )
  //   fatal("os::free_thread_local_storage: pthread_key_delete failed");
}

#define SMALLINT 32   // libthread allocate for tsd_common is a version specific
                      // small number - point is NO swap space available
void os::thread_local_storage_at_put(int index, void* value) {
  // %%% this is used only in threadLocalStorage.cpp
  if (thr_setspecific((thread_key_t)index, value)) {
    if (errno == ENOMEM) {
       vm_exit_out_of_memory(SMALLINT, "thr_setspecific: out of swap space");
    } else {
      fatal(err_msg("os::thread_local_storage_at_put: thr_setspecific failed "
                    "(%s)", strerror(errno)));
    }
  } else {
      ThreadLocalStorage::set_thread_in_slot ((Thread *) value) ;
  }
}

// This function could be called before TLS is initialized, for example, when
// VM receives an async signal or when VM causes a fatal error during
// initialization. Return NULL if thr_getspecific() fails.
void* os::thread_local_storage_at(int index) {
  // %%% this is used only in threadLocalStorage.cpp
  void* r = NULL;
  return thr_getspecific((thread_key_t)index, &r) != 0 ? NULL : r;
}


const int NANOSECS_PER_MILLISECS = 1000000;
// gethrtime can move backwards if read from one cpu and then a different cpu
// getTimeNanos is guaranteed to not move backward on Solaris
// local spinloop created as faster for a CAS on an int than
// a CAS on a 64bit jlong. Also Atomic::cmpxchg for jlong is not
// supported on sparc v8 or pre supports_cx8 intel boxes.
// oldgetTimeNanos for systems which do not support CAS on 64bit jlong
// i.e. sparc v8 and pre supports_cx8 (i486) intel boxes
inline hrtime_t oldgetTimeNanos() {
  int gotlock = LOCK_INVALID;
  hrtime_t newtime = gethrtime();

  for (;;) {
// grab lock for max_hrtime
    int curlock = max_hrtime_lock;
    if (curlock & LOCK_BUSY)  continue;
    if (gotlock = Atomic::cmpxchg(LOCK_BUSY, &max_hrtime_lock, LOCK_FREE) != LOCK_FREE) continue;
    if (newtime > max_hrtime) {
      max_hrtime = newtime;
    } else {
      newtime = max_hrtime;
    }
    // release lock
    max_hrtime_lock = LOCK_FREE;
    return newtime;
  }
}
// gethrtime can move backwards if read from one cpu and then a different cpu
// getTimeNanos is guaranteed to not move backward on Solaris
inline hrtime_t getTimeNanos() {
  if (VM_Version::supports_cx8()) {
    const hrtime_t now = gethrtime();
    // Use atomic long load since 32-bit x86 uses 2 registers to keep long.
    const hrtime_t prev = Atomic::load((volatile jlong*)&max_hrtime);
    if (now <= prev)  return prev;   // same or retrograde time;
    const hrtime_t obsv = Atomic::cmpxchg(now, (volatile jlong*)&max_hrtime, prev);
    assert(obsv >= prev, "invariant");   // Monotonicity
    // If the CAS succeeded then we're done and return "now".
    // If the CAS failed and the observed value "obs" is >= now then
    // we should return "obs".  If the CAS failed and now > obs > prv then
    // some other thread raced this thread and installed a new value, in which case
    // we could either (a) retry the entire operation, (b) retry trying to install now
    // or (c) just return obs.  We use (c).   No loop is required although in some cases
    // we might discard a higher "now" value in deference to a slightly lower but freshly
    // installed obs value.   That's entirely benign -- it admits no new orderings compared
    // to (a) or (b) -- and greatly reduces coherence traffic.
    // We might also condition (c) on the magnitude of the delta between obs and now.
    // Avoiding excessive CAS operations to hot RW locations is critical.
    // See http://blogs.sun.com/dave/entry/cas_and_cache_trivia_invalidate
    return (prev == obsv) ? now : obsv ;
  } else {
    return oldgetTimeNanos();
  }
}

// Time since start-up in seconds to a fine granularity.
// Used by VMSelfDestructTimer and the MemProfiler.
double os::elapsedTime() {
  return (double)(getTimeNanos() - first_hrtime) / (double)hrtime_hz;
}

jlong os::elapsed_counter() {
  return (jlong)(getTimeNanos() - first_hrtime);
}

jlong os::elapsed_frequency() {
   return hrtime_hz;
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
    // For consistency return the real time from getTimeNanos()
    // converted to seconds.
    *process_real_time = ((double) getTimeNanos()) / ((double) NANOUNITS);

    return true;
  }
}

bool os::supports_vtime() { return true; }

bool os::enable_vtime() {
  int fd = open("/proc/self/ctl", O_WRONLY);
  if (fd == -1)
    return false;

  long cmd[] = { PCSET, PR_MSACCT };
  int res = write(fd, cmd, sizeof(long) * 2);
  close(fd);
  if (res != sizeof(long) * 2)
    return false;

  return true;
}

bool os::vtime_enabled() {
  int fd = open("/proc/self/status", O_RDONLY);
  if (fd == -1)
    return false;

  pstatus_t status;
  int res = read(fd, (void*) &status, sizeof(pstatus_t));
  close(fd);
  if (res != sizeof(pstatus_t))
    return false;

  return status.pr_flags & PR_MSACCT;
}

double os::elapsedVTime() {
  return (double)gethrvtime() / (double)hrtime_hz;
}

// Used internally for comparisons only
// getTimeMillis guaranteed to not move backwards on Solaris
jlong getTimeMillis() {
  jlong nanotime = getTimeNanos();
  return (jlong)(nanotime / NANOSECS_PER_MILLISECS);
}

// Must return millis since Jan 1 1970 for JVM_CurrentTimeMillis
jlong os::javaTimeMillis() {
  timeval t;
  if (gettimeofday( &t, NULL) == -1)
    fatal(err_msg("os::javaTimeMillis: gettimeofday (%s)", strerror(errno)));
  return jlong(t.tv_sec) * 1000  +  jlong(t.tv_usec) / 1000;
}

jlong os::javaTimeNanos() {
  return (jlong)getTimeNanos();
}

void os::javaTimeNanos_info(jvmtiTimerInfo *info_ptr) {
  info_ptr->max_value = ALL_64_BITS;      // gethrtime() uses all 64 bits
  info_ptr->may_skip_backward = false;    // not subject to resetting or drifting
  info_ptr->may_skip_forward = false;     // not subject to resetting or drifting
  info_ptr->kind = JVMTI_TIMER_ELAPSED;   // elapsed not CPU time
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
    ::abort(); // dump core (for debugging)
  }

  ::exit(1);
}

// Die immediately, no exit hook, no abort hook, no cleanup.
void os::die() {
  _exit(-1);
}

// unused
void os::set_error_file(const char *logfile) {}

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
      // really shouldn't be NULL but what the heck, check can't hurt
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

typedef int (*dladdr1_func_type) (void *, Dl_info *, void **, int);
static dladdr1_func_type dladdr1_func = NULL;

bool os::dll_address_to_function_name(address addr, char *buf,
                                      int buflen, int * offset) {
  Dl_info dlinfo;

  // dladdr1_func was initialized in os::init()
  if (dladdr1_func){
      // yes, we have dladdr1

      // Support for dladdr1 is checked at runtime; it may be
      // available even if the vm is built on a machine that does
      // not have dladdr1 support.  Make sure there is a value for
      // RTLD_DL_SYMENT.
      #ifndef RTLD_DL_SYMENT
      #define RTLD_DL_SYMENT 1
      #endif
      Sym * info;
      if (dladdr1_func((void *)addr, &dlinfo, (void **)&info,
                       RTLD_DL_SYMENT)) {
          if (buf) jio_snprintf(buf, buflen, "%s", dlinfo.dli_sname);
          if (offset) *offset = addr - (address)dlinfo.dli_saddr;

          // check if the returned symbol really covers addr
          return ((char *)dlinfo.dli_saddr + info->st_size > (char *)addr);
      } else {
          if (buf) buf[0] = '\0';
          if (offset) *offset  = -1;
          return false;
      }
  } else {
      // no, only dladdr is available
      if(dladdr((void *)addr, &dlinfo)) {
          if (buf) jio_snprintf(buf, buflen, dlinfo.dli_sname);
          if (offset) *offset = addr - (address)dlinfo.dli_saddr;
          return true;
      } else {
          if (buf) buf[0] = '\0';
          if (offset) *offset  = -1;
          return false;
      }
  }
}

bool os::dll_address_to_library_name(address addr, char* buf,
                                     int buflen, int* offset) {
  Dl_info dlinfo;

  if (dladdr((void*)addr, &dlinfo)){
     if (buf) jio_snprintf(buf, buflen, "%s", dlinfo.dli_fname);
     if (offset) *offset = addr - (address)dlinfo.dli_fbase;
     return true;
  } else {
     if (buf) buf[0] = '\0';
     if (offset) *offset = -1;
     return false;
  }
}

// Prints the names and full paths of all opened dynamic libraries
// for current process
void os::print_dll_info(outputStream * st) {
    Dl_info dli;
    void *handle;
    Link_map *map;
    Link_map *p;

    st->print_cr("Dynamic libraries:"); st->flush();

    if (!dladdr(CAST_FROM_FN_PTR(void *, os::print_dll_info), &dli)) {
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
    {EM_ARM,         EM_ARM,     ELFCLASS32, ELFDATA2LSB, (char*)"ARM 32"}
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
  #elif (defined ARM)
    static  Elf32_Half running_arch_code=EM_ARM;
  #else
    #error Method os::dll_load requires that one of following is defined:\
         IA32, AMD64, IA64, __sparc, __powerpc__, ARM, ARM
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

  if (lib_arch.elf_class != arch_array[running_arch_index].elf_class) {
    ::snprintf(diag_msg_buf, diag_msg_max_length-1," (Possible cause: architecture word width mismatch)");
    return NULL;
  }

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

void* os::dll_lookup(void* handle, const char* name) {
  return dlsym(handle, name);
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

void os::print_os_info(outputStream* st) {
  st->print("OS:");

  if (!_print_ascii_file("/etc/release", st)) {
    st->print("Solaris");
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

  // libthread
  if (os::Solaris::T2_libthread()) st->print("  (T2 libthread)");
  else st->print("  (T1 libthread)");
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


static bool check_addr0(outputStream* st) {
  jboolean status = false;
  int fd = open("/proc/self/map",O_RDONLY);
  if (fd >= 0) {
    prmap_t p;
    while(read(fd, &p, sizeof(p)) > 0) {
      if (p.pr_vaddr == 0x0) {
        st->print("Warning: Address: 0x%x, Size: %dK, ",p.pr_vaddr, p.pr_size/1024, p.pr_mapname);
        st->print("Mapped file: %s, ", p.pr_mapname[0] == '\0' ? "None" : p.pr_mapname);
        st->print("Access:");
        st->print("%s",(p.pr_mflags & MA_READ)  ? "r" : "-");
        st->print("%s",(p.pr_mflags & MA_WRITE) ? "w" : "-");
        st->print("%s",(p.pr_mflags & MA_EXEC)  ? "x" : "-");
        st->cr();
        status = true;
      }
      close(fd);
    }
  }
  return status;
}

void os::print_memory_info(outputStream* st) {
  st->print("Memory:");
  st->print(" %dk page", os::vm_page_size()>>10);
  st->print(", physical " UINT64_FORMAT "k", os::physical_memory()>>10);
  st->print("(" UINT64_FORMAT "k free)", os::available_memory() >> 10);
  st->cr();
  (void) check_addr0(st);
}

// Taken from /usr/include/sys/machsig.h  Supposed to be architecture specific
// but they're the same for all the solaris architectures that we support.
const char *ill_names[] = { "ILL0", "ILL_ILLOPC", "ILL_ILLOPN", "ILL_ILLADR",
                          "ILL_ILLTRP", "ILL_PRVOPC", "ILL_PRVREG",
                          "ILL_COPROC", "ILL_BADSTK" };

const char *fpe_names[] = { "FPE0", "FPE_INTDIV", "FPE_INTOVF", "FPE_FLTDIV",
                          "FPE_FLTOVF", "FPE_FLTUND", "FPE_FLTRES",
                          "FPE_FLTINV", "FPE_FLTSUB" };

const char *segv_names[] = { "SEGV0", "SEGV_MAPERR", "SEGV_ACCERR" };

const char *bus_names[] = { "BUS0", "BUS_ADRALN", "BUS_ADRERR", "BUS_OBJERR" };

void os::print_siginfo(outputStream* st, void* siginfo) {
  st->print("siginfo:");

  const int buflen = 100;
  char buf[buflen];
  siginfo_t *si = (siginfo_t*)siginfo;
  st->print("si_signo=%s: ", os::exception_name(si->si_signo, buf, buflen));
  char *err = strerror(si->si_errno);
  if (si->si_errno != 0 && err != NULL) {
    st->print("si_errno=%s", err);
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

// Moved from whole group, because we need them here for diagnostic
// prints.
#define OLDMAXSIGNUM 32
static int Maxsignum = 0;
static int *ourSigFlags = NULL;

extern "C" void sigINTRHandler(int, siginfo_t*, void*);

int os::Solaris::get_our_sigflags(int sig) {
  assert(ourSigFlags!=NULL, "signal data structure not initialized");
  assert(sig > 0 && sig < Maxsignum, "vm signal out of expected range");
  return ourSigFlags[sig];
}

void os::Solaris::set_our_sigflags(int sig, int flags) {
  assert(ourSigFlags!=NULL, "signal data structure not initialized");
  assert(sig > 0 && sig < Maxsignum, "vm signal out of expected range");
  ourSigFlags[sig] = flags;
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
    sa.sa_flags = VMError::get_resetted_sigflags(sig);
  }

  st->print(", sa_flags="   PTR32_FORMAT, sa.sa_flags);

  // Check: is it our handler?
  if(handler == CAST_FROM_FN_PTR(address, signalHandler) ||
     handler == CAST_FROM_FN_PTR(address, sigINTRHandler)) {
    // It is our signal handler
    // check for flags
    if(sa.sa_flags != os::Solaris::get_our_sigflags(sig)) {
      st->print(
        ", flags was changed from " PTR32_FORMAT ", consider using jsig library",
        os::Solaris::get_our_sigflags(sig));
    }
  }
  st->cr();
}

void os::print_signal_handlers(outputStream* st, char* buf, size_t buflen) {
  st->print_cr("Signal Handlers:");
  print_signal_handler(st, SIGSEGV, buf, buflen);
  print_signal_handler(st, SIGBUS , buf, buflen);
  print_signal_handler(st, SIGFPE , buf, buflen);
  print_signal_handler(st, SIGPIPE, buf, buflen);
  print_signal_handler(st, SIGXFSZ, buf, buflen);
  print_signal_handler(st, SIGILL , buf, buflen);
  print_signal_handler(st, INTERRUPT_SIGNAL, buf, buflen);
  print_signal_handler(st, ASYNC_SIGNAL, buf, buflen);
  print_signal_handler(st, BREAK_SIGNAL, buf, buflen);
  print_signal_handler(st, SHUTDOWN1_SIGNAL , buf, buflen);
  print_signal_handler(st, SHUTDOWN2_SIGNAL , buf, buflen);
  print_signal_handler(st, SHUTDOWN3_SIGNAL, buf, buflen);
  print_signal_handler(st, os::Solaris::SIGinterrupt(), buf, buflen);
  print_signal_handler(st, os::Solaris::SIGasync(), buf, buflen);
}

static char saved_jvm_path[MAXPATHLEN] = { 0 };

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
  realpath((char *)dlinfo.dli_fname, buf);

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
        char cpu_arch[12];
        char* jrelib_p;
        int   len;
        sysinfo(SI_ARCHITECTURE, cpu_arch, sizeof(cpu_arch));
#ifdef _LP64
        // If we are on sparc running a 64-bit vm, look in jre/lib/sparcv9.
        if (strcmp(cpu_arch, "sparc") == 0) {
          strcat(cpu_arch, "v9");
        } else if (strcmp(cpu_arch, "i386") == 0) {
          strcpy(cpu_arch, "amd64");
        }
#endif
        // Check the current module name "libjvm.so" or "libjvm_g.so".
        p = strrchr(buf, '/');
        assert(strstr(p, "/libjvm") == p, "invalid library name");
        p = strstr(p, "_g") ? "_g" : "";

        realpath(java_home_var, buf);
        // determine if this is a legacy image or modules image
        // modules image doesn't have "jre" subdirectory
        len = strlen(buf);
        jrelib_p = buf + len;
        snprintf(jrelib_p, buflen-len, "/jre/lib/%s", cpu_arch);
        if (0 != access(buf, F_OK)) {
          snprintf(jrelib_p, buflen-len, "/lib/%s", cpu_arch);
        }

        if (0 == access(buf, F_OK)) {
          // Use current module name "libjvm[_g].so" instead of
          // "libjvm"debug_only("_g")".so" since for fastdebug version
          // we should have "libjvm.so" but debug_only("_g") adds "_g"!
          // It is used when we are choosing the HPI library's name
          // "libhpi[_g].so" in hpi::initialize_get_interface().
          len = strlen(buf);
          snprintf(buf + len, buflen-len, "/hotspot/libjvm%s.so", p);
        } else {
          // Go back to path of .so
          realpath((char *)dlinfo.dli_fname, buf);
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


// sun.misc.Signal

extern "C" {
  static void UserHandler(int sig, void *siginfo, void *context) {
    // Ctrl-C is pressed during error reporting, likely because the error
    // handler fails to abort. Let VM die immediately.
    if (sig == SIGINT && is_error_reported()) {
       os::die();
    }

    os::signal_notify(sig);
    // We do not need to reinstate the signal handler each time...
  }
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
  sigAct.sa_flags = SA_RESTART & ~SA_RESETHAND;
  sigAct.sa_handler = CAST_TO_FN_PTR(sa_handler_t, handler);

  if (sigaction(signal_number, &sigAct, &oldSigAct))
    // -1 means registration failed
    return (void *)-1;

  return CAST_FROM_FN_PTR(void*, oldSigAct.sa_handler);
}

void os::signal_raise(int signal_number) {
  raise(signal_number);
}

/*
 * The following code is moved from os.cpp for making this
 * code platform specific, which it is by its very nature.
 */

// a counter for each possible signal value
static int Sigexit = 0;
static int Maxlibjsigsigs;
static jint *pending_signals = NULL;
static int *preinstalled_sigs = NULL;
static struct sigaction *chainedsigactions = NULL;
static sema_t sig_sem;
typedef int (*version_getting_t)();
version_getting_t os::Solaris::get_libjsig_version = NULL;
static int libjsigversion = NULL;

int os::sigexitnum_pd() {
  assert(Sigexit > 0, "signal memory not yet initialized");
  return Sigexit;
}

void os::Solaris::init_signal_mem() {
  // Initialize signal structures
  Maxsignum = SIGRTMAX;
  Sigexit = Maxsignum+1;
  assert(Maxsignum >0, "Unable to obtain max signal number");

  Maxlibjsigsigs = Maxsignum;

  // pending_signals has one int per signal
  // The additional signal is for SIGEXIT - exit signal to signal_thread
  pending_signals = (jint *)os::malloc(sizeof(jint) * (Sigexit+1));
  memset(pending_signals, 0, (sizeof(jint) * (Sigexit+1)));

  if (UseSignalChaining) {
     chainedsigactions = (struct sigaction *)malloc(sizeof(struct sigaction)
       * (Maxsignum + 1));
     memset(chainedsigactions, 0, (sizeof(struct sigaction) * (Maxsignum + 1)));
     preinstalled_sigs = (int *)os::malloc(sizeof(int) * (Maxsignum + 1));
     memset(preinstalled_sigs, 0, (sizeof(int) * (Maxsignum + 1)));
  }
  ourSigFlags = (int*)malloc(sizeof(int) * (Maxsignum + 1 ));
  memset(ourSigFlags, 0, sizeof(int) * (Maxsignum + 1));
}

void os::signal_init_pd() {
  int ret;

  ret = ::sema_init(&sig_sem, 0, NULL, NULL);
  assert(ret == 0, "sema_init() failed");
}

void os::signal_notify(int signal_number) {
  int ret;

  Atomic::inc(&pending_signals[signal_number]);
  ret = ::sema_post(&sig_sem);
  assert(ret == 0, "sema_post() failed");
}

static int check_pending_signals(bool wait_for_signal) {
  int ret;
  while (true) {
    for (int i = 0; i < Sigexit + 1; i++) {
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
      while((ret = ::sema_wait(&sig_sem)) == EINTR)
          ;
      assert(ret == 0, "sema_wait() failed");

      // were we externally suspended while we were waiting?
      threadIsSuspended = thread->handle_special_suspend_equivalent_condition();
      if (threadIsSuspended) {
        //
        // The semaphore has been incremented, but while we were waiting
        // another thread suspended us. We don't want to continue running
        // while suspended because that would surprise the thread that
        // suspended us.
        //
        ret = ::sema_post(&sig_sem);
        assert(ret == 0, "sema_post() failed");

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

static int page_size = -1;

// The mmap MAP_ALIGN flag is supported on Solaris 9 and later.  init_2() will
// clear this var if support is not available.
static bool has_map_align = true;

int os::vm_page_size() {
  assert(page_size != -1, "must call os::init");
  return page_size;
}

// Solaris allocates memory by pages.
int os::vm_allocation_granularity() {
  assert(page_size != -1, "must call os::init");
  return page_size;
}

bool os::commit_memory(char* addr, size_t bytes, bool exec) {
  int prot = exec ? PROT_READ|PROT_WRITE|PROT_EXEC : PROT_READ|PROT_WRITE;
  size_t size = bytes;
  return
     NULL != Solaris::mmap_chunk(addr, size, MAP_PRIVATE|MAP_FIXED, prot);
}

bool os::commit_memory(char* addr, size_t bytes, size_t alignment_hint,
                       bool exec) {
  if (commit_memory(addr, bytes, exec)) {
    if (UseMPSS && alignment_hint > (size_t)vm_page_size()) {
      // If the large page size has been set and the VM
      // is using large pages, use the large page size
      // if it is smaller than the alignment hint. This is
      // a case where the VM wants to use a larger alignment size
      // for its own reasons but still want to use large pages
      // (which is what matters to setting the mpss range.
      size_t page_size = 0;
      if (large_page_size() < alignment_hint) {
        assert(UseLargePages, "Expected to be here for large page use only");
        page_size = large_page_size();
      } else {
        // If the alignment hint is less than the large page
        // size, the VM wants a particular alignment (thus the hint)
        // for internal reasons.  Try to set the mpss range using
        // the alignment_hint.
        page_size = alignment_hint;
      }
      // Since this is a hint, ignore any failures.
      (void)Solaris::set_mpss_range(addr, bytes, page_size);
    }
    return true;
  }
  return false;
}

// Uncommit the pages in a specified region.
void os::free_memory(char* addr, size_t bytes) {
  if (madvise(addr, bytes, MADV_FREE) < 0) {
    debug_only(warning("MADV_FREE failed."));
    return;
  }
}

bool os::create_stack_guard_pages(char* addr, size_t size) {
  return os::commit_memory(addr, size);
}

bool os::remove_stack_guard_pages(char* addr, size_t size) {
  return os::uncommit_memory(addr, size);
}

// Change the page size in a given range.
void os::realign_memory(char *addr, size_t bytes, size_t alignment_hint) {
  assert((intptr_t)addr % alignment_hint == 0, "Address should be aligned.");
  assert((intptr_t)(addr + bytes) % alignment_hint == 0, "End should be aligned.");
  Solaris::set_mpss_range(addr, bytes, alignment_hint);
}

// Tell the OS to make the range local to the first-touching LWP
void os::numa_make_local(char *addr, size_t bytes, int lgrp_hint) {
  assert((intptr_t)addr % os::vm_page_size() == 0, "Address should be page-aligned.");
  if (madvise(addr, bytes, MADV_ACCESS_LWP) < 0) {
    debug_only(warning("MADV_ACCESS_LWP failed."));
  }
}

// Tell the OS that this range would be accessed from different LWPs.
void os::numa_make_global(char *addr, size_t bytes) {
  assert((intptr_t)addr % os::vm_page_size() == 0, "Address should be page-aligned.");
  if (madvise(addr, bytes, MADV_ACCESS_MANY) < 0) {
    debug_only(warning("MADV_ACCESS_MANY failed."));
  }
}

// Get the number of the locality groups.
size_t os::numa_get_groups_num() {
  size_t n = Solaris::lgrp_nlgrps(Solaris::lgrp_cookie());
  return n != -1 ? n : 1;
}

// Get a list of leaf locality groups. A leaf lgroup is group that
// doesn't have any children. Typical leaf group is a CPU or a CPU/memory
// board. An LWP is assigned to one of these groups upon creation.
size_t os::numa_get_leaf_groups(int *ids, size_t size) {
   if ((ids[0] = Solaris::lgrp_root(Solaris::lgrp_cookie())) == -1) {
     ids[0] = 0;
     return 1;
   }
   int result_size = 0, top = 1, bottom = 0, cur = 0;
   for (int k = 0; k < size; k++) {
     int r = Solaris::lgrp_children(Solaris::lgrp_cookie(), ids[cur],
                                    (Solaris::lgrp_id_t*)&ids[top], size - top);
     if (r == -1) {
       ids[0] = 0;
       return 1;
     }
     if (!r) {
       // That's a leaf node.
       assert (bottom <= cur, "Sanity check");
       // Check if the node has memory
       if (Solaris::lgrp_resources(Solaris::lgrp_cookie(), ids[cur],
                                   NULL, 0, LGRP_RSRC_MEM) > 0) {
         ids[bottom++] = ids[cur];
       }
     }
     top += r;
     cur++;
   }
   if (bottom == 0) {
     // Handle a situation, when the OS reports no memory available.
     // Assume UMA architecture.
     ids[0] = 0;
     return 1;
   }
   return bottom;
}

// Detect the topology change. Typically happens during CPU plugging-unplugging.
bool os::numa_topology_changed() {
  int is_stale = Solaris::lgrp_cookie_stale(Solaris::lgrp_cookie());
  if (is_stale != -1 && is_stale) {
    Solaris::lgrp_fini(Solaris::lgrp_cookie());
    Solaris::lgrp_cookie_t c = Solaris::lgrp_init(Solaris::LGRP_VIEW_CALLER);
    assert(c != 0, "Failure to initialize LGRP API");
    Solaris::set_lgrp_cookie(c);
    return true;
  }
  return false;
}

// Get the group id of the current LWP.
int os::numa_get_group_id() {
  int lgrp_id = Solaris::lgrp_home(P_LWPID, P_MYID);
  if (lgrp_id == -1) {
    return 0;
  }
  const int size = os::numa_get_groups_num();
  int *ids = (int*)alloca(size * sizeof(int));

  // Get the ids of all lgroups with memory; r is the count.
  int r = Solaris::lgrp_resources(Solaris::lgrp_cookie(), lgrp_id,
                                  (Solaris::lgrp_id_t*)ids, size, LGRP_RSRC_MEM);
  if (r <= 0) {
    return 0;
  }
  return ids[os::random() % r];
}

// Request information about the page.
bool os::get_page_info(char *start, page_info* info) {
  const uint_t info_types[] = { MEMINFO_VLGRP, MEMINFO_VPAGESIZE };
  uint64_t addr = (uintptr_t)start;
  uint64_t outdata[2];
  uint_t validity = 0;

  if (os::Solaris::meminfo(&addr, 1, info_types, 2, outdata, &validity) < 0) {
    return false;
  }

  info->size = 0;
  info->lgrp_id = -1;

  if ((validity & 1) != 0) {
    if ((validity & 2) != 0) {
      info->lgrp_id = outdata[0];
    }
    if ((validity & 4) != 0) {
      info->size = outdata[1];
    }
    return true;
  }
  return false;
}

// Scan the pages from start to end until a page different than
// the one described in the info parameter is encountered.
char *os::scan_pages(char *start, char* end, page_info* page_expected, page_info* page_found) {
  const uint_t info_types[] = { MEMINFO_VLGRP, MEMINFO_VPAGESIZE };
  const size_t types = sizeof(info_types) / sizeof(info_types[0]);
  uint64_t addrs[MAX_MEMINFO_CNT], outdata[types * MAX_MEMINFO_CNT];
  uint_t validity[MAX_MEMINFO_CNT];

  size_t page_size = MAX2((size_t)os::vm_page_size(), page_expected->size);
  uint64_t p = (uint64_t)start;
  while (p < (uint64_t)end) {
    addrs[0] = p;
    size_t addrs_count = 1;
    while (addrs_count < MAX_MEMINFO_CNT && addrs[addrs_count - 1] < (uint64_t)end) {
      addrs[addrs_count] = addrs[addrs_count - 1] + page_size;
      addrs_count++;
    }

    if (os::Solaris::meminfo(addrs, addrs_count, info_types, types, outdata, validity) < 0) {
      return NULL;
    }

    size_t i = 0;
    for (; i < addrs_count; i++) {
      if ((validity[i] & 1) != 0) {
        if ((validity[i] & 4) != 0) {
          if (outdata[types * i + 1] != page_expected->size) {
            break;
          }
        } else
          if (page_expected->size != 0) {
            break;
          }

        if ((validity[i] & 2) != 0 && page_expected->lgrp_id > 0) {
          if (outdata[types * i] != page_expected->lgrp_id) {
            break;
          }
        }
      } else {
        return NULL;
      }
    }

    if (i != addrs_count) {
      if ((validity[i] & 2) != 0) {
        page_found->lgrp_id = outdata[types * i];
      } else {
        page_found->lgrp_id = -1;
      }
      if ((validity[i] & 4) != 0) {
        page_found->size = outdata[types * i + 1];
      } else {
        page_found->size = 0;
      }
      return (char*)addrs[i];
    }

    p = addrs[addrs_count - 1] + page_size;
  }
  return end;
}

bool os::uncommit_memory(char* addr, size_t bytes) {
  size_t size = bytes;
  // Map uncommitted pages PROT_NONE so we fail early if we touch an
  // uncommitted page. Otherwise, the read/write might succeed if we
  // have enough swap space to back the physical page.
  return
    NULL != Solaris::mmap_chunk(addr, size,
                                MAP_PRIVATE|MAP_FIXED|MAP_NORESERVE,
                                PROT_NONE);
}

char* os::Solaris::mmap_chunk(char *addr, size_t size, int flags, int prot) {
  char *b = (char *)mmap(addr, size, prot, flags, os::Solaris::_dev_zero_fd, 0);

  if (b == MAP_FAILED) {
    return NULL;
  }
  return b;
}

char* os::Solaris::anon_mmap(char* requested_addr, size_t bytes, size_t alignment_hint, bool fixed) {
  char* addr = requested_addr;
  int flags = MAP_PRIVATE | MAP_NORESERVE;

  assert(!(fixed && (alignment_hint > 0)), "alignment hint meaningless with fixed mmap");

  if (fixed) {
    flags |= MAP_FIXED;
  } else if (has_map_align && (alignment_hint > (size_t) vm_page_size())) {
    flags |= MAP_ALIGN;
    addr = (char*) alignment_hint;
  }

  // Map uncommitted pages PROT_NONE so we fail early if we touch an
  // uncommitted page. Otherwise, the read/write might succeed if we
  // have enough swap space to back the physical page.
  return mmap_chunk(addr, bytes, flags, PROT_NONE);
}

char* os::reserve_memory(size_t bytes, char* requested_addr, size_t alignment_hint) {
  char* addr = Solaris::anon_mmap(requested_addr, bytes, alignment_hint, (requested_addr != NULL));

  guarantee(requested_addr == NULL || requested_addr == addr,
            "OS failed to return requested mmap address.");
  return addr;
}

// Reserve memory at an arbitrary address, only if that area is
// available (and not reserved for something else).

char* os::attempt_reserve_memory_at(size_t bytes, char* requested_addr) {
  const int max_tries = 10;
  char* base[max_tries];
  size_t size[max_tries];

  // Solaris adds a gap between mmap'ed regions.  The size of the gap
  // is dependent on the requested size and the MMU.  Our initial gap
  // value here is just a guess and will be corrected later.
  bool had_top_overlap = false;
  bool have_adjusted_gap = false;
  size_t gap = 0x400000;

  // Assert only that the size is a multiple of the page size, since
  // that's all that mmap requires, and since that's all we really know
  // about at this low abstraction level.  If we need higher alignment,
  // we can either pass an alignment to this method or verify alignment
  // in one of the methods further up the call chain.  See bug 5044738.
  assert(bytes % os::vm_page_size() == 0, "reserving unexpected size block");

  // Since snv_84, Solaris attempts to honor the address hint - see 5003415.
  // Give it a try, if the kernel honors the hint we can return immediately.
  char* addr = Solaris::anon_mmap(requested_addr, bytes, 0, false);
  volatile int err = errno;
  if (addr == requested_addr) {
    return addr;
  } else if (addr != NULL) {
    unmap_memory(addr, bytes);
  }

  if (PrintMiscellaneous && Verbose) {
    char buf[256];
    buf[0] = '\0';
    if (addr == NULL) {
      jio_snprintf(buf, sizeof(buf), ": %s", strerror(err));
    }
    warning("attempt_reserve_memory_at: couldn't reserve %d bytes at "
            PTR_FORMAT ": reserve_memory_helper returned " PTR_FORMAT
            "%s", bytes, requested_addr, addr, buf);
  }

  // Address hint method didn't work.  Fall back to the old method.
  // In theory, once SNV becomes our oldest supported platform, this
  // code will no longer be needed.
  //
  // Repeatedly allocate blocks until the block is allocated at the
  // right spot. Give up after max_tries.
  int i;
  for (i = 0; i < max_tries; ++i) {
    base[i] = reserve_memory(bytes);

    if (base[i] != NULL) {
      // Is this the block we wanted?
      if (base[i] == requested_addr) {
        size[i] = bytes;
        break;
      }

      // check that the gap value is right
      if (had_top_overlap && !have_adjusted_gap) {
        size_t actual_gap = base[i-1] - base[i] - bytes;
        if (gap != actual_gap) {
          // adjust the gap value and retry the last 2 allocations
          assert(i > 0, "gap adjustment code problem");
          have_adjusted_gap = true;  // adjust the gap only once, just in case
          gap = actual_gap;
          if (PrintMiscellaneous && Verbose) {
            warning("attempt_reserve_memory_at: adjusted gap to 0x%lx", gap);
          }
          unmap_memory(base[i], bytes);
          unmap_memory(base[i-1], size[i-1]);
          i-=2;
          continue;
        }
      }

      // Does this overlap the block we wanted? Give back the overlapped
      // parts and try again.
      //
      // There is still a bug in this code: if top_overlap == bytes,
      // the overlap is offset from requested region by the value of gap.
      // In this case giving back the overlapped part will not work,
      // because we'll give back the entire block at base[i] and
      // therefore the subsequent allocation will not generate a new gap.
      // This could be fixed with a new algorithm that used larger
      // or variable size chunks to find the requested region -
      // but such a change would introduce additional complications.
      // It's rare enough that the planets align for this bug,
      // so we'll just wait for a fix for 6204603/5003415 which
      // will provide a mmap flag to allow us to avoid this business.

      size_t top_overlap = requested_addr + (bytes + gap) - base[i];
      if (top_overlap >= 0 && top_overlap < bytes) {
        had_top_overlap = true;
        unmap_memory(base[i], top_overlap);
        base[i] += top_overlap;
        size[i] = bytes - top_overlap;
      } else {
        size_t bottom_overlap = base[i] + bytes - requested_addr;
        if (bottom_overlap >= 0 && bottom_overlap < bytes) {
          if (PrintMiscellaneous && Verbose && bottom_overlap == 0) {
            warning("attempt_reserve_memory_at: possible alignment bug");
          }
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

  return (i < max_tries) ? requested_addr : NULL;
}

bool os::release_memory(char* addr, size_t bytes) {
  size_t size = bytes;
  return munmap(addr, size) == 0;
}

static bool solaris_mprotect(char* addr, size_t bytes, int prot) {
  assert(addr == (char*)align_size_down((uintptr_t)addr, os::vm_page_size()),
         "addr must be page aligned");
  int retVal = mprotect(addr, bytes, prot);
  return retVal == 0;
}

// Protect memory (Used to pass readonly pages through
// JNI GetArray<type>Elements with empty arrays.)
// Also, used for serialization page and for compressed oops null pointer
// checking.
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
  return solaris_mprotect(addr, bytes, p);
}

// guard_memory and unguard_memory only happens within stack guard pages.
// Since ISM pertains only to the heap, guard and unguard memory should not
/// happen with an ISM region.
bool os::guard_memory(char* addr, size_t bytes) {
  return solaris_mprotect(addr, bytes, PROT_NONE);
}

bool os::unguard_memory(char* addr, size_t bytes) {
  return solaris_mprotect(addr, bytes, PROT_READ|PROT_WRITE);
}

// Large page support

// UseLargePages is the master flag to enable/disable large page memory.
// UseMPSS and UseISM are supported for compatibility reasons. Their combined
// effects can be described in the following table:
//
// UseLargePages UseMPSS UseISM
//    false         *       *   => UseLargePages is the master switch, turning
//                                 it off will turn off both UseMPSS and
//                                 UseISM. VM will not use large page memory
//                                 regardless the settings of UseMPSS/UseISM.
//     true      false    false => Unless future Solaris provides other
//                                 mechanism to use large page memory, this
//                                 combination is equivalent to -UseLargePages,
//                                 VM will not use large page memory
//     true      true     false => JVM will use MPSS for large page memory.
//                                 This is the default behavior.
//     true      false    true  => JVM will use ISM for large page memory.
//     true      true     true  => JVM will use ISM if it is available.
//                                 Otherwise, JVM will fall back to MPSS.
//                                 Becaues ISM is now available on all
//                                 supported Solaris versions, this combination
//                                 is equivalent to +UseISM -UseMPSS.

typedef int (*getpagesizes_func_type) (size_t[], int);
static size_t _large_page_size = 0;

bool os::Solaris::ism_sanity_check(bool warn, size_t * page_size) {
  // x86 uses either 2M or 4M page, depending on whether PAE (Physical Address
  // Extensions) mode is enabled. AMD64/EM64T uses 2M page in 64bit mode. Sparc
  // can support multiple page sizes.

  // Don't bother to probe page size because getpagesizes() comes with MPSS.
  // ISM is only recommended on old Solaris where there is no MPSS support.
  // Simply choose a conservative value as default.
  *page_size = LargePageSizeInBytes ? LargePageSizeInBytes :
               SPARC_ONLY(4 * M) IA32_ONLY(4 * M) AMD64_ONLY(2 * M)
               ARM_ONLY(2 * M);

  // ISM is available on all supported Solaris versions
  return true;
}

// Insertion sort for small arrays (descending order).
static void insertion_sort_descending(size_t* array, int len) {
  for (int i = 0; i < len; i++) {
    size_t val = array[i];
    for (size_t key = i; key > 0 && array[key - 1] < val; --key) {
      size_t tmp = array[key];
      array[key] = array[key - 1];
      array[key - 1] = tmp;
    }
  }
}

bool os::Solaris::mpss_sanity_check(bool warn, size_t * page_size) {
  getpagesizes_func_type getpagesizes_func =
    CAST_TO_FN_PTR(getpagesizes_func_type, dlsym(RTLD_DEFAULT, "getpagesizes"));
  if (getpagesizes_func == NULL) {
    if (warn) {
      warning("MPSS is not supported by the operating system.");
    }
    return false;
  }

  const unsigned int usable_count = VM_Version::page_size_count();
  if (usable_count == 1) {
    return false;
  }

  // Fill the array of page sizes.
  int n = getpagesizes_func(_page_sizes, page_sizes_max);
  assert(n > 0, "Solaris bug?");
  if (n == page_sizes_max) {
    // Add a sentinel value (necessary only if the array was completely filled
    // since it is static (zeroed at initialization)).
    _page_sizes[--n] = 0;
    DEBUG_ONLY(warning("increase the size of the os::_page_sizes array.");)
  }
  assert(_page_sizes[n] == 0, "missing sentinel");

  if (n == 1) return false;     // Only one page size available.

  // Skip sizes larger than 4M (or LargePageSizeInBytes if it was set) and
  // select up to usable_count elements.  First sort the array, find the first
  // acceptable value, then copy the usable sizes to the top of the array and
  // trim the rest.  Make sure to include the default page size :-).
  //
  // A better policy could get rid of the 4M limit by taking the sizes of the
  // important VM memory regions (java heap and possibly the code cache) into
  // account.
  insertion_sort_descending(_page_sizes, n);
  const size_t size_limit =
    FLAG_IS_DEFAULT(LargePageSizeInBytes) ? 4 * M : LargePageSizeInBytes;
  int beg;
  for (beg = 0; beg < n && _page_sizes[beg] > size_limit; ++beg) /* empty */ ;
  const int end = MIN2((int)usable_count, n) - 1;
  for (int cur = 0; cur < end; ++cur, ++beg) {
    _page_sizes[cur] = _page_sizes[beg];
  }
  _page_sizes[end] = vm_page_size();
  _page_sizes[end + 1] = 0;

  if (_page_sizes[end] > _page_sizes[end - 1]) {
    // Default page size is not the smallest; sort again.
    insertion_sort_descending(_page_sizes, end + 1);
  }
  *page_size = _page_sizes[0];

  return true;
}

bool os::large_page_init() {
  if (!UseLargePages) {
    UseISM = false;
    UseMPSS = false;
    return false;
  }

  // print a warning if any large page related flag is specified on command line
  bool warn_on_failure = !FLAG_IS_DEFAULT(UseLargePages)        ||
                         !FLAG_IS_DEFAULT(UseISM)               ||
                         !FLAG_IS_DEFAULT(UseMPSS)              ||
                         !FLAG_IS_DEFAULT(LargePageSizeInBytes);
  UseISM = UseISM &&
           Solaris::ism_sanity_check(warn_on_failure, &_large_page_size);
  if (UseISM) {
    // ISM disables MPSS to be compatible with old JDK behavior
    UseMPSS = false;
    _page_sizes[0] = _large_page_size;
    _page_sizes[1] = vm_page_size();
  }

  UseMPSS = UseMPSS &&
            Solaris::mpss_sanity_check(warn_on_failure, &_large_page_size);

  UseLargePages = UseISM || UseMPSS;
  return UseLargePages;
}

bool os::Solaris::set_mpss_range(caddr_t start, size_t bytes, size_t align) {
  // Signal to OS that we want large pages for addresses
  // from addr, addr + bytes
  struct memcntl_mha mpss_struct;
  mpss_struct.mha_cmd = MHA_MAPSIZE_VA;
  mpss_struct.mha_pagesize = align;
  mpss_struct.mha_flags = 0;
  if (memcntl(start, bytes, MC_HAT_ADVISE,
              (caddr_t) &mpss_struct, 0, 0) < 0) {
    debug_only(warning("Attempt to use MPSS failed."));
    return false;
  }
  return true;
}

char* os::reserve_memory_special(size_t bytes, char* addr, bool exec) {
  // "exec" is passed in but not used.  Creating the shared image for
  // the code cache doesn't have an SHM_X executable permission to check.
  assert(UseLargePages && UseISM, "only for ISM large pages");

  size_t size = bytes;
  char* retAddr = NULL;
  int shmid;
  key_t ismKey;

  bool warn_on_failure = UseISM &&
                        (!FLAG_IS_DEFAULT(UseLargePages)         ||
                         !FLAG_IS_DEFAULT(UseISM)                ||
                         !FLAG_IS_DEFAULT(LargePageSizeInBytes)
                        );
  char msg[128];

  ismKey = IPC_PRIVATE;

  // Create a large shared memory region to attach to based on size.
  // Currently, size is the total size of the heap
  shmid = shmget(ismKey, size, SHM_R | SHM_W | IPC_CREAT);
  if (shmid == -1){
     if (warn_on_failure) {
       jio_snprintf(msg, sizeof(msg), "Failed to reserve shared memory (errno = %d).", errno);
       warning(msg);
     }
     return NULL;
  }

  // Attach to the region
  retAddr = (char *) shmat(shmid, 0, SHM_SHARE_MMU | SHM_R | SHM_W);
  int err = errno;

  // Remove shmid. If shmat() is successful, the actual shared memory segment
  // will be deleted when it's detached by shmdt() or when the process
  // terminates. If shmat() is not successful this will remove the shared
  // segment immediately.
  shmctl(shmid, IPC_RMID, NULL);

  if (retAddr == (char *) -1) {
    if (warn_on_failure) {
      jio_snprintf(msg, sizeof(msg), "Failed to attach shared memory (errno = %d).", err);
      warning(msg);
    }
    return NULL;
  }

  return retAddr;
}

bool os::release_memory_special(char* base, size_t bytes) {
  // detaching the SHM segment will also delete it, see reserve_memory_special()
  int rslt = shmdt(base);
  return rslt == 0;
}

size_t os::large_page_size() {
  return _large_page_size;
}

// MPSS allows application to commit large page memory on demand; with ISM
// the entire memory region must be allocated as shared memory.
bool os::can_commit_large_page_memory() {
  return UseISM ? false : true;
}

bool os::can_execute_large_page_memory() {
  return UseISM ? false : true;
}

static int os_sleep(jlong millis, bool interruptible) {
  const jlong limit = INT_MAX;
  jlong prevtime;
  int res;

  while (millis > limit) {
    if ((res = os_sleep(limit, interruptible)) != OS_OK)
      return res;
    millis -= limit;
  }

  // Restart interrupted polls with new parameters until the proper delay
  // has been completed.

  prevtime = getTimeMillis();

  while (millis > 0) {
    jlong newtime;

    if (!interruptible) {
      // Following assert fails for os::yield_all:
      // assert(!thread->is_Java_thread(), "must not be java thread");
      res = poll(NULL, 0, millis);
    } else {
      JavaThread *jt = JavaThread::current();

      INTERRUPTIBLE_NORESTART_VM_ALWAYS(poll(NULL, 0, millis), res, jt,
        os::Solaris::clear_interrupted);
    }

    // INTERRUPTIBLE_NORESTART_VM_ALWAYS returns res == OS_INTRPT for
    // thread.Interrupt.

    if((res == OS_ERR) && (errno == EINTR)) {
      newtime = getTimeMillis();
      assert(newtime >= prevtime, "time moving backwards");
    /* Doing prevtime and newtime in microseconds doesn't help precision,
       and trying to round up to avoid lost milliseconds can result in a
       too-short delay. */
      millis -= newtime - prevtime;
      if(millis <= 0)
        return OS_OK;
      prevtime = newtime;
    } else
      return res;
  }

  return OS_OK;
}

// Read calls from inside the vm need to perform state transitions
size_t os::read(int fd, void *buf, unsigned int nBytes) {
  INTERRUPTIBLE_RETURN_INT_VM(::read(fd, buf, nBytes), os::Solaris::clear_interrupted);
}

int os::sleep(Thread* thread, jlong millis, bool interruptible) {
  assert(thread == Thread::current(),  "thread consistency check");

  // TODO-FIXME: this should be removed.
  // On Solaris machines (especially 2.5.1) we found that sometimes the VM gets into a live lock
  // situation with a JavaThread being starved out of a lwp. The kernel doesn't seem to generate
  // a SIGWAITING signal which would enable the threads library to create a new lwp for the starving
  // thread. We suspect that because the Watcher thread keeps waking up at periodic intervals the kernel
  // is fooled into believing that the system is making progress. In the code below we block the
  // the watcher thread while safepoint is in progress so that it would not appear as though the
  // system is making progress.
  if (!Solaris::T2_libthread() &&
      thread->is_Watcher_thread() && SafepointSynchronize::is_synchronizing() && !Arguments::has_profile()) {
    // We now try to acquire the threads lock. Since this lock is held by the VM thread during
    // the entire safepoint, the watcher thread will  line up here during the safepoint.
    Threads_lock->lock_without_safepoint_check();
    Threads_lock->unlock();
  }

  if (thread->is_Java_thread()) {
    // This is a JavaThread so we honor the _thread_blocked protocol
    // even for sleeps of 0 milliseconds. This was originally done
    // as a workaround for bug 4338139. However, now we also do it
    // to honor the suspend-equivalent protocol.

    JavaThread *jt = (JavaThread *) thread;
    ThreadBlockInVM tbivm(jt);

    jt->set_suspend_equivalent();
    // cleared by handle_special_suspend_equivalent_condition() or
    // java_suspend_self() via check_and_wait_while_suspended()

    int ret_code;
    if (millis <= 0) {
      thr_yield();
      ret_code = 0;
    } else {
      // The original sleep() implementation did not create an
      // OSThreadWaitState helper for sleeps of 0 milliseconds.
      // I'm preserving that decision for now.
      OSThreadWaitState osts(jt->osthread(), false /* not Object.wait() */);

      ret_code = os_sleep(millis, interruptible);
    }

    // were we externally suspended while we were waiting?
    jt->check_and_wait_while_suspended();

    return ret_code;
  }

  // non-JavaThread from this point on:

  if (millis <= 0) {
    thr_yield();
    return 0;
  }

  OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);

  return os_sleep(millis, interruptible);
}

int os::naked_sleep() {
  // %% make the sleep time an integer flag. for now use 1 millisec.
  return os_sleep(1, false);
}

// Sleep forever; naked call to OS-specific sleep; use with CAUTION
void os::infinite_sleep() {
  while (true) {    // sleep forever ...
    ::sleep(100);   // ... 100 seconds at a time
  }
}

// Used to convert frequent JVM_Yield() to nops
bool os::dont_yield() {
  if (DontYieldALot) {
    static hrtime_t last_time = 0;
    hrtime_t diff = getTimeNanos() - last_time;

    if (diff < DontYieldALotInterval * 1000000)
      return true;

    last_time += diff;

    return false;
  }
  else {
    return false;
  }
}

// Caveat: Solaris os::yield() causes a thread-state transition whereas
// the linux and win32 implementations do not.  This should be checked.

void os::yield() {
  // Yields to all threads with same or greater priority
  os::sleep(Thread::current(), 0, false);
}

// Note that yield semantics are defined by the scheduling class to which
// the thread currently belongs.  Typically, yield will _not yield to
// other equal or higher priority threads that reside on the dispatch queues
// of other CPUs.

os::YieldResult os::NakedYield() { thr_yield(); return os::YIELD_UNKNOWN; }


// On Solaris we found that yield_all doesn't always yield to all other threads.
// There have been cases where there is a thread ready to execute but it doesn't
// get an lwp as the VM thread continues to spin with sleeps of 1 millisecond.
// The 1 millisecond wait doesn't seem long enough for the kernel to issue a
// SIGWAITING signal which will cause a new lwp to be created. So we count the
// number of times yield_all is called in the one loop and increase the sleep
// time after 8 attempts. If this fails too we increase the concurrency level
// so that the starving thread would get an lwp

void os::yield_all(int attempts) {
  // Yields to all threads, including threads with lower priorities
  if (attempts == 0) {
    os::sleep(Thread::current(), 1, false);
  } else {
    int iterations = attempts % 30;
    if (iterations == 0 && !os::Solaris::T2_libthread()) {
      // thr_setconcurrency and _getconcurrency make sense only under T1.
      int noofLWPS = thr_getconcurrency();
      if (noofLWPS < (Threads::number_of_threads() + 2)) {
        thr_setconcurrency(thr_getconcurrency() + 1);
      }
    } else if (iterations < 25) {
      os::sleep(Thread::current(), 1, false);
    } else {
      os::sleep(Thread::current(), 10, false);
    }
  }
}

// Called from the tight loops to possibly influence time-sharing heuristics
void os::loop_breaker(int attempts) {
  os::yield_all(attempts);
}


// Interface for setting lwp priorities.  If we are using T2 libthread,
// which forces the use of BoundThreads or we manually set UseBoundThreads,
// all of our threads will be assigned to real lwp's.  Using the thr_setprio
// function is meaningless in this mode so we must adjust the real lwp's priority
// The routines below implement the getting and setting of lwp priorities.
//
// Note: There are three priority scales used on Solaris.  Java priotities
//       which range from 1 to 10, libthread "thr_setprio" scale which range
//       from 0 to 127, and the current scheduling class of the process we
//       are running in.  This is typically from -60 to +60.
//       The setting of the lwp priorities in done after a call to thr_setprio
//       so Java priorities are mapped to libthread priorities and we map from
//       the latter to lwp priorities.  We don't keep priorities stored in
//       Java priorities since some of our worker threads want to set priorities
//       higher than all Java threads.
//
// For related information:
// (1)  man -s 2 priocntl
// (2)  man -s 4 priocntl
// (3)  man dispadmin
// =    librt.so
// =    libthread/common/rtsched.c - thrp_setlwpprio().
// =    ps -cL <pid> ... to validate priority.
// =    sched_get_priority_min and _max
//              pthread_create
//              sched_setparam
//              pthread_setschedparam
//
// Assumptions:
// +    We assume that all threads in the process belong to the same
//              scheduling class.   IE. an homogenous process.
// +    Must be root or in IA group to change change "interactive" attribute.
//              Priocntl() will fail silently.  The only indication of failure is when
//              we read-back the value and notice that it hasn't changed.
// +    Interactive threads enter the runq at the head, non-interactive at the tail.
// +    For RT, change timeslice as well.  Invariant:
//              constant "priority integral"
//              Konst == TimeSlice * (60-Priority)
//              Given a priority, compute appropriate timeslice.
// +    Higher numerical values have higher priority.

// sched class attributes
typedef struct {
        int   schedPolicy;              // classID
        int   maxPrio;
        int   minPrio;
} SchedInfo;


static SchedInfo tsLimits, iaLimits, rtLimits;

#ifdef ASSERT
static int  ReadBackValidate = 1;
#endif
static int  myClass     = 0;
static int  myMin       = 0;
static int  myMax       = 0;
static int  myCur       = 0;
static bool priocntl_enable = false;


// Call the version of priocntl suitable for all supported versions
// of Solaris. We need to call through this wrapper so that we can
// build on Solaris 9 and run on Solaris 8, 9 and 10.
//
// This code should be removed if we ever stop supporting Solaris 8
// and earlier releases.

static long priocntl_stub(int pcver, idtype_t idtype, id_t id, int cmd, caddr_t arg);
typedef long (*priocntl_type)(int pcver, idtype_t idtype, id_t id, int cmd, caddr_t arg);
static priocntl_type priocntl_ptr = priocntl_stub;

// Stub to set the value of the real pointer, and then call the real
// function.

static long priocntl_stub(int pcver, idtype_t idtype, id_t id, int cmd, caddr_t arg) {
  // Try Solaris 8- name only.
  priocntl_type tmp = (priocntl_type)dlsym(RTLD_DEFAULT, "__priocntl");
  guarantee(tmp != NULL, "priocntl function not found.");
  priocntl_ptr = tmp;
  return (*priocntl_ptr)(PC_VERSION, idtype, id, cmd, arg);
}


// lwp_priocntl_init
//
// Try to determine the priority scale for our process.
//
// Return errno or 0 if OK.
//
static
int     lwp_priocntl_init ()
{
  int rslt;
  pcinfo_t ClassInfo;
  pcparms_t ParmInfo;
  int i;

  if (!UseThreadPriorities) return 0;

  // We are using Bound threads, we need to determine our priority ranges
  if (os::Solaris::T2_libthread() || UseBoundThreads) {
    // If ThreadPriorityPolicy is 1, switch tables
    if (ThreadPriorityPolicy == 1) {
      for (i = 0 ; i < MaxPriority+1; i++)
        os::java_to_os_priority[i] = prio_policy1[i];
    }
  }
  // Not using Bound Threads, set to ThreadPolicy 1
  else {
    for ( i = 0 ; i < MaxPriority+1; i++ ) {
      os::java_to_os_priority[i] = prio_policy1[i];
    }
    return 0;
  }


  // Get IDs for a set of well-known scheduling classes.
  // TODO-FIXME: GETCLINFO returns the current # of classes in the
  // the system.  We should have a loop that iterates over the
  // classID values, which are known to be "small" integers.

  strcpy(ClassInfo.pc_clname, "TS");
  ClassInfo.pc_cid = -1;
  rslt = (*priocntl_ptr)(PC_VERSION, P_ALL, 0, PC_GETCID, (caddr_t)&ClassInfo);
  if (rslt < 0) return errno;
  assert(ClassInfo.pc_cid != -1, "cid for TS class is -1");
  tsLimits.schedPolicy = ClassInfo.pc_cid;
  tsLimits.maxPrio = ((tsinfo_t*)ClassInfo.pc_clinfo)->ts_maxupri;
  tsLimits.minPrio = -tsLimits.maxPrio;

  strcpy(ClassInfo.pc_clname, "IA");
  ClassInfo.pc_cid = -1;
  rslt = (*priocntl_ptr)(PC_VERSION, P_ALL, 0, PC_GETCID, (caddr_t)&ClassInfo);
  if (rslt < 0) return errno;
  assert(ClassInfo.pc_cid != -1, "cid for IA class is -1");
  iaLimits.schedPolicy = ClassInfo.pc_cid;
  iaLimits.maxPrio = ((iainfo_t*)ClassInfo.pc_clinfo)->ia_maxupri;
  iaLimits.minPrio = -iaLimits.maxPrio;

  strcpy(ClassInfo.pc_clname, "RT");
  ClassInfo.pc_cid = -1;
  rslt = (*priocntl_ptr)(PC_VERSION, P_ALL, 0, PC_GETCID, (caddr_t)&ClassInfo);
  if (rslt < 0) return errno;
  assert(ClassInfo.pc_cid != -1, "cid for RT class is -1");
  rtLimits.schedPolicy = ClassInfo.pc_cid;
  rtLimits.maxPrio = ((rtinfo_t*)ClassInfo.pc_clinfo)->rt_maxpri;
  rtLimits.minPrio = 0;


  // Query our "current" scheduling class.
  // This will normally be IA,TS or, rarely, RT.
  memset (&ParmInfo, 0, sizeof(ParmInfo));
  ParmInfo.pc_cid = PC_CLNULL;
  rslt = (*priocntl_ptr) (PC_VERSION, P_PID, P_MYID, PC_GETPARMS, (caddr_t)&ParmInfo );
  if ( rslt < 0 ) return errno;
  myClass = ParmInfo.pc_cid;

  // We now know our scheduling classId, get specific information
  // the class.
  ClassInfo.pc_cid = myClass;
  ClassInfo.pc_clname[0] = 0;
  rslt = (*priocntl_ptr) (PC_VERSION, (idtype)0, 0, PC_GETCLINFO, (caddr_t)&ClassInfo );
  if ( rslt < 0 ) return errno;

  if (ThreadPriorityVerbose)
    tty->print_cr ("lwp_priocntl_init: Class=%d(%s)...", myClass, ClassInfo.pc_clname);

  memset(&ParmInfo, 0, sizeof(pcparms_t));
  ParmInfo.pc_cid = PC_CLNULL;
  rslt = (*priocntl_ptr)(PC_VERSION, P_PID, P_MYID, PC_GETPARMS, (caddr_t)&ParmInfo);
  if (rslt < 0) return errno;

  if (ParmInfo.pc_cid == rtLimits.schedPolicy) {
    myMin = rtLimits.minPrio;
    myMax = rtLimits.maxPrio;
  } else if (ParmInfo.pc_cid == iaLimits.schedPolicy) {
    iaparms_t *iaInfo  = (iaparms_t*)ParmInfo.pc_clparms;
    myMin = iaLimits.minPrio;
    myMax = iaLimits.maxPrio;
    myMax = MIN2(myMax, (int)iaInfo->ia_uprilim);       // clamp - restrict
  } else if (ParmInfo.pc_cid == tsLimits.schedPolicy) {
    tsparms_t *tsInfo  = (tsparms_t*)ParmInfo.pc_clparms;
    myMin = tsLimits.minPrio;
    myMax = tsLimits.maxPrio;
    myMax = MIN2(myMax, (int)tsInfo->ts_uprilim);       // clamp - restrict
  } else {
    // No clue - punt
    if (ThreadPriorityVerbose)
      tty->print_cr ("Unknown scheduling class: %s ... \n", ClassInfo.pc_clname);
    return EINVAL;      // no clue, punt
  }

  if (ThreadPriorityVerbose)
        tty->print_cr ("Thread priority Range: [%d..%d]\n", myMin, myMax);

  priocntl_enable = true;  // Enable changing priorities
  return 0;
}

#define IAPRI(x)        ((iaparms_t *)((x).pc_clparms))
#define RTPRI(x)        ((rtparms_t *)((x).pc_clparms))
#define TSPRI(x)        ((tsparms_t *)((x).pc_clparms))


// scale_to_lwp_priority
//
// Convert from the libthread "thr_setprio" scale to our current
// lwp scheduling class scale.
//
static
int     scale_to_lwp_priority (int rMin, int rMax, int x)
{
  int v;

  if (x == 127) return rMax;            // avoid round-down
    v = (((x*(rMax-rMin)))/128)+rMin;
  return v;
}


// set_lwp_priority
//
// Set the priority of the lwp.  This call should only be made
// when using bound threads (T2 threads are bound by default).
//
int     set_lwp_priority (int ThreadID, int lwpid, int newPrio )
{
  int rslt;
  int Actual, Expected, prv;
  pcparms_t ParmInfo;                   // for GET-SET
#ifdef ASSERT
  pcparms_t ReadBack;                   // for readback
#endif

  // Set priority via PC_GETPARMS, update, PC_SETPARMS
  // Query current values.
  // TODO: accelerate this by eliminating the PC_GETPARMS call.
  // Cache "pcparms_t" in global ParmCache.
  // TODO: elide set-to-same-value

  // If something went wrong on init, don't change priorities.
  if ( !priocntl_enable ) {
    if (ThreadPriorityVerbose)
      tty->print_cr("Trying to set priority but init failed, ignoring");
    return EINVAL;
  }


  // If lwp hasn't started yet, just return
  // the _start routine will call us again.
  if ( lwpid <= 0 ) {
    if (ThreadPriorityVerbose) {
      tty->print_cr ("deferring the set_lwp_priority of thread " INTPTR_FORMAT " to %d, lwpid not set",
                     ThreadID, newPrio);
    }
    return 0;
  }

  if (ThreadPriorityVerbose) {
    tty->print_cr ("set_lwp_priority(" INTPTR_FORMAT "@" INTPTR_FORMAT " %d) ",
                   ThreadID, lwpid, newPrio);
  }

  memset(&ParmInfo, 0, sizeof(pcparms_t));
  ParmInfo.pc_cid = PC_CLNULL;
  rslt = (*priocntl_ptr)(PC_VERSION, P_LWPID, lwpid, PC_GETPARMS, (caddr_t)&ParmInfo);
  if (rslt < 0) return errno;

  if (ParmInfo.pc_cid == rtLimits.schedPolicy) {
    rtparms_t *rtInfo  = (rtparms_t*)ParmInfo.pc_clparms;
    rtInfo->rt_pri     = scale_to_lwp_priority (rtLimits.minPrio, rtLimits.maxPrio, newPrio);
    rtInfo->rt_tqsecs  = RT_NOCHANGE;
    rtInfo->rt_tqnsecs = RT_NOCHANGE;
    if (ThreadPriorityVerbose) {
      tty->print_cr("RT: %d->%d\n", newPrio, rtInfo->rt_pri);
    }
  } else if (ParmInfo.pc_cid == iaLimits.schedPolicy) {
    iaparms_t *iaInfo  = (iaparms_t*)ParmInfo.pc_clparms;
    int maxClamped     = MIN2(iaLimits.maxPrio, (int)iaInfo->ia_uprilim);
    iaInfo->ia_upri    = scale_to_lwp_priority(iaLimits.minPrio, maxClamped, newPrio);
    iaInfo->ia_uprilim = IA_NOCHANGE;
    iaInfo->ia_mode    = IA_NOCHANGE;
    if (ThreadPriorityVerbose) {
      tty->print_cr ("IA: [%d...%d] %d->%d\n",
               iaLimits.minPrio, maxClamped, newPrio, iaInfo->ia_upri);
    }
  } else if (ParmInfo.pc_cid == tsLimits.schedPolicy) {
    tsparms_t *tsInfo  = (tsparms_t*)ParmInfo.pc_clparms;
    int maxClamped     = MIN2(tsLimits.maxPrio, (int)tsInfo->ts_uprilim);
    prv                = tsInfo->ts_upri;
    tsInfo->ts_upri    = scale_to_lwp_priority(tsLimits.minPrio, maxClamped, newPrio);
    tsInfo->ts_uprilim = IA_NOCHANGE;
    if (ThreadPriorityVerbose) {
      tty->print_cr ("TS: %d [%d...%d] %d->%d\n",
               prv, tsLimits.minPrio, maxClamped, newPrio, tsInfo->ts_upri);
    }
    if (prv == tsInfo->ts_upri) return 0;
  } else {
    if ( ThreadPriorityVerbose ) {
      tty->print_cr ("Unknown scheduling class\n");
    }
      return EINVAL;    // no clue, punt
  }

  rslt = (*priocntl_ptr)(PC_VERSION, P_LWPID, lwpid, PC_SETPARMS, (caddr_t)&ParmInfo);
  if (ThreadPriorityVerbose && rslt) {
    tty->print_cr ("PC_SETPARMS ->%d %d\n", rslt, errno);
  }
  if (rslt < 0) return errno;

#ifdef ASSERT
  // Sanity check: read back what we just attempted to set.
  // In theory it could have changed in the interim ...
  //
  // The priocntl system call is tricky.
  // Sometimes it'll validate the priority value argument and
  // return EINVAL if unhappy.  At other times it fails silently.
  // Readbacks are prudent.

  if (!ReadBackValidate) return 0;

  memset(&ReadBack, 0, sizeof(pcparms_t));
  ReadBack.pc_cid = PC_CLNULL;
  rslt = (*priocntl_ptr)(PC_VERSION, P_LWPID, lwpid, PC_GETPARMS, (caddr_t)&ReadBack);
  assert(rslt >= 0, "priocntl failed");
  Actual = Expected = 0xBAD;
  assert(ParmInfo.pc_cid == ReadBack.pc_cid, "cid's don't match");
  if (ParmInfo.pc_cid == rtLimits.schedPolicy) {
    Actual   = RTPRI(ReadBack)->rt_pri;
    Expected = RTPRI(ParmInfo)->rt_pri;
  } else if (ParmInfo.pc_cid == iaLimits.schedPolicy) {
    Actual   = IAPRI(ReadBack)->ia_upri;
    Expected = IAPRI(ParmInfo)->ia_upri;
  } else if (ParmInfo.pc_cid == tsLimits.schedPolicy) {
    Actual   = TSPRI(ReadBack)->ts_upri;
    Expected = TSPRI(ParmInfo)->ts_upri;
  } else {
    if ( ThreadPriorityVerbose ) {
      tty->print_cr("set_lwp_priority: unexpected class in readback: %d\n", ParmInfo.pc_cid);
    }
  }

  if (Actual != Expected) {
    if ( ThreadPriorityVerbose ) {
      tty->print_cr ("set_lwp_priority(%d %d) Class=%d: actual=%d vs expected=%d\n",
             lwpid, newPrio, ReadBack.pc_cid, Actual, Expected);
    }
  }
#endif

  return 0;
}



// Solaris only gives access to 128 real priorities at a time,
// so we expand Java's ten to fill this range.  This would be better
// if we dynamically adjusted relative priorities.
//
// The ThreadPriorityPolicy option allows us to select 2 different
// priority scales.
//
// ThreadPriorityPolicy=0
// Since the Solaris' default priority is MaximumPriority, we do not
// set a priority lower than Max unless a priority lower than
// NormPriority is requested.
//
// ThreadPriorityPolicy=1
// This mode causes the priority table to get filled with
// linear values.  NormPriority get's mapped to 50% of the
// Maximum priority an so on.  This will cause VM threads
// to get unfair treatment against other Solaris processes
// which do not explicitly alter their thread priorities.
//


int os::java_to_os_priority[MaxPriority + 1] = {
  -99999,         // 0 Entry should never be used

  0,              // 1 MinPriority
  32,             // 2
  64,             // 3

  96,             // 4
  127,            // 5 NormPriority
  127,            // 6

  127,            // 7
  127,            // 8
  127,            // 9 NearMaxPriority

  127             // 10 MaxPriority
};


OSReturn os::set_native_priority(Thread* thread, int newpri) {
  assert(newpri >= MinimumPriority && newpri <= MaximumPriority, "bad priority mapping");
  if ( !UseThreadPriorities ) return OS_OK;
  int status = thr_setprio(thread->osthread()->thread_id(), newpri);
  if ( os::Solaris::T2_libthread() || (UseBoundThreads && thread->osthread()->is_vm_created()) )
    status |= (set_lwp_priority (thread->osthread()->thread_id(),
                    thread->osthread()->lwp_id(), newpri ));
  return (status == 0) ? OS_OK : OS_ERR;
}


OSReturn os::get_native_priority(const Thread* const thread, int *priority_ptr) {
  int p;
  if ( !UseThreadPriorities ) {
    *priority_ptr = NormalPriority;
    return OS_OK;
  }
  int status = thr_getprio(thread->osthread()->thread_id(), &p);
  if (status != 0) {
    return OS_ERR;
  }
  *priority_ptr = p;
  return OS_OK;
}


// Hint to the underlying OS that a task switch would not be good.
// Void return because it's a hint and can fail.
void os::hint_no_preempt() {
  schedctl_start(schedctl_init());
}

void os::interrupt(Thread* thread) {
  assert(Thread::current() == thread || Threads_lock->owned_by_self(), "possibility of dangling Thread pointer");

  OSThread* osthread = thread->osthread();

  int isInterrupted = osthread->interrupted();
  if (!isInterrupted) {
      osthread->set_interrupted(true);
      OrderAccess::fence();
      // os::sleep() is implemented with either poll (NULL,0,timeout) or
      // by parking on _SleepEvent.  If the former, thr_kill will unwedge
      // the sleeper by SIGINTR, otherwise the unpark() will wake the sleeper.
      ParkEvent * const slp = thread->_SleepEvent ;
      if (slp != NULL) slp->unpark() ;
  }

  // For JSR166:  unpark after setting status but before thr_kill -dl
  if (thread->is_Java_thread()) {
    ((JavaThread*)thread)->parker()->unpark();
  }

  // Handle interruptible wait() ...
  ParkEvent * const ev = thread->_ParkEvent ;
  if (ev != NULL) ev->unpark() ;

  // When events are used everywhere for os::sleep, then this thr_kill
  // will only be needed if UseVMInterruptibleIO is true.

  if (!isInterrupted) {
    int status = thr_kill(osthread->thread_id(), os::Solaris::SIGinterrupt());
    assert_status(status == 0, status, "thr_kill");

    // Bump thread interruption counter
    RuntimeService::record_thread_interrupt_signaled_count();
  }
}


bool os::is_interrupted(Thread* thread, bool clear_interrupted) {
  assert(Thread::current() == thread || Threads_lock->owned_by_self(), "possibility of dangling Thread pointer");

  OSThread* osthread = thread->osthread();

  bool res = osthread->interrupted();

  // NOTE that since there is no "lock" around these two operations,
  // there is the possibility that the interrupted flag will be
  // "false" but that the interrupt event will be set. This is
  // intentional. The effect of this is that Object.wait() will appear
  // to have a spurious wakeup, which is not harmful, and the
  // possibility is so rare that it is not worth the added complexity
  // to add yet another lock. It has also been recommended not to put
  // the interrupted flag into the os::Solaris::Event structure,
  // because it hides the issue.
  if (res && clear_interrupted) {
    osthread->set_interrupted(false);
  }
  return res;
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

// A lightweight implementation that does not suspend the target thread and
// thus returns only a hint. Used for profiling only!
ExtendedPC os::get_thread_pc(Thread* thread) {
  // Make sure that it is called by the watcher and the Threads lock is owned.
  assert(Thread::current()->is_Watcher_thread(), "Must be watcher and own Threads_lock");
  // For now, is only used to profile the VM Thread
  assert(thread->is_VM_thread(), "Can only be called for VMThread");
  ExtendedPC epc;

  GetThreadPC_Callback  cb(ProfileVM_lock);
  OSThread *osthread = thread->osthread();
  const int time_to_wait = 400; // 400ms wait for initial response
  int status = cb.interrupt(thread, time_to_wait);

  if (cb.is_done() ) {
    epc = cb.addr();
  } else {
    DEBUG_ONLY(tty->print_cr("Failed to get pc for thread: %d got %d status",
                              osthread->thread_id(), status););
    // epc is already NULL
  }
  return epc;
}


// This does not do anything on Solaris. This is basically a hook for being
// able to use structured exception handling (thread-local exception filters) on, e.g., Win32.
void os::os_exception_wrapper(java_call_t f, JavaValue* value, methodHandle* method, JavaCallArguments* args, Thread* thread) {
  f(value, method, args, thread);
}

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
// SIGBUS, SIGSEGV, SIGILL, SIGFPE, BREAK_SIGNAL, SIGPIPE, SIGXFSZ,
// os::Solaris::SIGasync
// It should be consulted by handlers for any of those signals.
// It explicitly does not recognize os::Solaris::SIGinterrupt
//
// The caller of this routine must pass in the three arguments supplied
// to the function referred to in the "sa_sigaction" (not the "sa_handler")
// field of the structure passed to sigaction().  This routine assumes that
// the sa_flags field passed to sigaction() includes SA_SIGINFO and SA_RESTART.
//
// Note that the VM will print warnings if it detects conflicting signal
// handlers, unless invoked with the option "-XX:+AllowUserSignalHandlers".
//
extern "C" int JVM_handle_solaris_signal(int signo, siginfo_t* siginfo, void* ucontext, int abort_if_unrecognized);


void signalHandler(int sig, siginfo_t* info, void* ucVoid) {
  JVM_handle_solaris_signal(sig, info, ucVoid, true);
}

/* Do not delete - if guarantee is ever removed,  a signal handler (even empty)
   is needed to provoke threads blocked on IO to return an EINTR
   Note: this explicitly does NOT call JVM_handle_solaris_signal and
   does NOT participate in signal chaining due to requirement for
   NOT setting SA_RESTART to make EINTR work. */
extern "C" void sigINTRHandler(int sig, siginfo_t* info, void* ucVoid) {
   if (UseSignalChaining) {
      struct sigaction *actp = os::Solaris::get_chained_signal_action(sig);
      if (actp && actp->sa_handler) {
        vm_exit_during_initialization("Signal chaining detected for VM interrupt signal, try -XX:+UseAltSigs");
      }
   }
}

// This boolean allows users to forward their own non-matching signals
// to JVM_handle_solaris_signal, harmlessly.
bool os::Solaris::signal_handlers_are_installed = false;

// For signal-chaining
bool os::Solaris::libjsig_is_loaded = false;
typedef struct sigaction *(*get_signal_t)(int);
get_signal_t os::Solaris::get_signal_action = NULL;

struct sigaction* os::Solaris::get_chained_signal_action(int sig) {
  struct sigaction *actp = NULL;

  if ((libjsig_is_loaded)  && (sig <= Maxlibjsigsigs)) {
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
    thr_sigsetmask(SIG_SETMASK, &(actp->sa_mask), &oset);

    // call into the chained handler
    if (siginfo_flag_set) {
      (*sa)(sig, siginfo, context);
    } else {
      (*hand)(sig);
    }

    // restore the signal mask
    thr_sigsetmask(SIG_SETMASK, &oset, 0);
  }
  // Tell jvm's signal handler the signal is taken care of.
  return true;
}

bool os::Solaris::chained_handler(int sig, siginfo_t* siginfo, void* context) {
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

struct sigaction* os::Solaris::get_preinstalled_handler(int sig) {
  assert((chainedsigactions != (struct sigaction *)NULL) && (preinstalled_sigs != (int *)NULL) , "signals not yet initialized");
  if (preinstalled_sigs[sig] != 0) {
    return &chainedsigactions[sig];
  }
  return NULL;
}

void os::Solaris::save_preinstalled_handler(int sig, struct sigaction& oldAct) {

  assert(sig > 0 && sig <= Maxsignum, "vm signal out of expected range");
  assert((chainedsigactions != (struct sigaction *)NULL) && (preinstalled_sigs != (int *)NULL) , "signals not yet initialized");
  chainedsigactions[sig] = oldAct;
  preinstalled_sigs[sig] = 1;
}

void os::Solaris::set_signal_handler(int sig, bool set_installed, bool oktochain) {
  // Check for overwrite.
  struct sigaction oldAct;
  sigaction(sig, (struct sigaction*)NULL, &oldAct);
  void* oldhand = oldAct.sa_sigaction ? CAST_FROM_FN_PTR(void*,  oldAct.sa_sigaction)
                                      : CAST_FROM_FN_PTR(void*,  oldAct.sa_handler);
  if (oldhand != CAST_FROM_FN_PTR(void*, SIG_DFL) &&
      oldhand != CAST_FROM_FN_PTR(void*, SIG_IGN) &&
      oldhand != CAST_FROM_FN_PTR(void*, signalHandler)) {
    if (AllowUserSignalHandlers || !set_installed) {
      // Do not overwrite; user takes responsibility to forward to us.
      return;
    } else if (UseSignalChaining) {
      if (oktochain) {
        // save the old handler in jvm
        save_preinstalled_handler(sig, oldAct);
      } else {
        vm_exit_during_initialization("Signal chaining not allowed for VM interrupt signal, try -XX:+UseAltSigs.");
      }
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

  sigAct.sa_sigaction = signalHandler;
  // Handle SIGSEGV on alternate signal stack if
  // not using stack banging
  if (!UseStackBanging && sig == SIGSEGV) {
    sigAct.sa_flags = SA_SIGINFO | SA_RESTART | SA_ONSTACK;
  // Interruptible i/o requires SA_RESTART cleared so EINTR
  // is returned instead of restarting system calls
  } else if (sig == os::Solaris::SIGinterrupt()) {
    sigemptyset(&sigAct.sa_mask);
    sigAct.sa_handler = NULL;
    sigAct.sa_flags = SA_SIGINFO;
    sigAct.sa_sigaction = sigINTRHandler;
  } else {
    sigAct.sa_flags = SA_SIGINFO | SA_RESTART;
  }
  os::Solaris::set_our_sigflags(sig, sigAct.sa_flags);

  sigaction(sig, &sigAct, &oldAct);

  void* oldhand2 = oldAct.sa_sigaction ? CAST_FROM_FN_PTR(void*, oldAct.sa_sigaction)
                                       : CAST_FROM_FN_PTR(void*, oldAct.sa_handler);
  assert(oldhand2 == oldhand, "no concurrent signal handler installation");
}


#define DO_SIGNAL_CHECK(sig) \
  if (!sigismember(&check_signal_done, sig)) \
    os::Solaris::check_signal_handler(sig)

// This method is a periodic task to check for misbehaving JNI applications
// under CheckJNI, we can add any periodic checks here

void os::run_periodic_checks() {
  // A big source of grief is hijacking virt. addr 0x0 on Solaris,
  // thereby preventing a NULL checks.
  if(!check_addr0_done) check_addr0_done = check_addr0(tty);

  if (check_signals == false) return;

  // SEGV and BUS if overridden could potentially prevent
  // generation of hs*.log in the event of a crash, debugging
  // such a case can be very challenging, so we absolutely
  // check for the following for a good measure:
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

  // See comments above for using JVM1/JVM2 and UseAltSigs
  DO_SIGNAL_CHECK(os::Solaris::SIGinterrupt());
  DO_SIGNAL_CHECK(os::Solaris::SIGasync());

}

typedef int (*os_sigaction_t)(int, const struct sigaction *, struct sigaction *);

static os_sigaction_t os_sigaction = NULL;

void os::Solaris::check_signal_handler(int sig) {
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
    : CAST_FROM_FN_PTR(address, act.sa_handler) ;


  switch(sig) {
    case SIGSEGV:
    case SIGBUS:
    case SIGFPE:
    case SIGPIPE:
    case SIGXFSZ:
    case SIGILL:
      jvmHandler = CAST_FROM_FN_PTR(address, signalHandler);
      break;

    case SHUTDOWN1_SIGNAL:
    case SHUTDOWN2_SIGNAL:
    case SHUTDOWN3_SIGNAL:
    case BREAK_SIGNAL:
      jvmHandler = (address)user_handler();
      break;

    default:
      int intrsig = os::Solaris::SIGinterrupt();
      int asynsig = os::Solaris::SIGasync();

      if (sig == intrsig) {
        jvmHandler = CAST_FROM_FN_PTR(address, sigINTRHandler);
      } else if (sig == asynsig) {
        jvmHandler = CAST_FROM_FN_PTR(address, signalHandler);
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
  } else if(os::Solaris::get_our_sigflags(sig) != 0 && act.sa_flags != os::Solaris::get_our_sigflags(sig)) {
    tty->print("Warning: %s handler flags ", exception_name(sig, buf, O_BUFLEN));
    tty->print("expected:" PTR32_FORMAT, os::Solaris::get_our_sigflags(sig));
    tty->print_cr("  found:" PTR32_FORMAT, act.sa_flags);
    // No need to check this sig any longer
    sigaddset(&check_signal_done, sig);
  }

  // Print all the signal handler state
  if (sigismember(&check_signal_done, sig)) {
    print_signal_handlers(tty, buf, O_BUFLEN);
  }

}

void os::Solaris::install_signal_handlers() {
  bool libjsigdone = false;
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
    get_libjsig_version = CAST_TO_FN_PTR(version_getting_t,
                                         dlsym(RTLD_DEFAULT, "JVM_get_libjsig_version"));
    libjsig_is_loaded = true;
    if (os::Solaris::get_libjsig_version != NULL) {
      libjsigversion =  (*os::Solaris::get_libjsig_version)();
    }
    assert(UseSignalChaining, "should enable signal-chaining");
  }
  if (libjsig_is_loaded) {
    // Tell libjsig jvm is setting signal handlers
    (*begin_signal_setting)();
  }

  set_signal_handler(SIGSEGV, true, true);
  set_signal_handler(SIGPIPE, true, true);
  set_signal_handler(SIGXFSZ, true, true);
  set_signal_handler(SIGBUS, true, true);
  set_signal_handler(SIGILL, true, true);
  set_signal_handler(SIGFPE, true, true);


  if (os::Solaris::SIGinterrupt() > OLDMAXSIGNUM || os::Solaris::SIGasync() > OLDMAXSIGNUM) {

    // Pre-1.4.1 Libjsig limited to signal chaining signals <= 32 so
    // can not register overridable signals which might be > 32
    if (libjsig_is_loaded && libjsigversion <= JSIG_VERSION_1_4_1) {
    // Tell libjsig jvm has finished setting signal handlers
      (*end_signal_setting)();
      libjsigdone = true;
    }
  }

  // Never ok to chain our SIGinterrupt
  set_signal_handler(os::Solaris::SIGinterrupt(), true, false);
  set_signal_handler(os::Solaris::SIGasync(), true, true);

  if (libjsig_is_loaded && !libjsigdone) {
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


void report_error(const char* file_name, int line_no, const char* title, const char* format, ...);

const char * signames[] = {
  "SIG0",
  "SIGHUP", "SIGINT", "SIGQUIT", "SIGILL", "SIGTRAP",
  "SIGABRT", "SIGEMT", "SIGFPE", "SIGKILL", "SIGBUS",
  "SIGSEGV", "SIGSYS", "SIGPIPE", "SIGALRM", "SIGTERM",
  "SIGUSR1", "SIGUSR2", "SIGCLD", "SIGPWR", "SIGWINCH",
  "SIGURG", "SIGPOLL", "SIGSTOP", "SIGTSTP", "SIGCONT",
  "SIGTTIN", "SIGTTOU", "SIGVTALRM", "SIGPROF", "SIGXCPU",
  "SIGXFSZ", "SIGWAITING", "SIGLWP", "SIGFREEZE", "SIGTHAW",
  "SIGCANCEL", "SIGLOST"
};

const char* os::exception_name(int exception_code, char* buf, size_t size) {
  if (0 < exception_code && exception_code <= SIGRTMAX) {
    // signal
    if (exception_code < sizeof(signames)/sizeof(const char*)) {
       jio_snprintf(buf, size, "%s", signames[exception_code]);
    } else {
       jio_snprintf(buf, size, "SIG%d", exception_code);
    }
    return buf;
  } else {
    return NULL;
  }
}

// (Static) wrappers for the new libthread API
int_fnP_thread_t_iP_uP_stack_tP_gregset_t os::Solaris::_thr_getstate;
int_fnP_thread_t_i_gregset_t os::Solaris::_thr_setstate;
int_fnP_thread_t_i os::Solaris::_thr_setmutator;
int_fnP_thread_t os::Solaris::_thr_suspend_mutator;
int_fnP_thread_t os::Solaris::_thr_continue_mutator;

// (Static) wrapper for getisax(2) call.
os::Solaris::getisax_func_t os::Solaris::_getisax = 0;

// (Static) wrappers for the liblgrp API
os::Solaris::lgrp_home_func_t os::Solaris::_lgrp_home;
os::Solaris::lgrp_init_func_t os::Solaris::_lgrp_init;
os::Solaris::lgrp_fini_func_t os::Solaris::_lgrp_fini;
os::Solaris::lgrp_root_func_t os::Solaris::_lgrp_root;
os::Solaris::lgrp_children_func_t os::Solaris::_lgrp_children;
os::Solaris::lgrp_resources_func_t os::Solaris::_lgrp_resources;
os::Solaris::lgrp_nlgrps_func_t os::Solaris::_lgrp_nlgrps;
os::Solaris::lgrp_cookie_stale_func_t os::Solaris::_lgrp_cookie_stale;
os::Solaris::lgrp_cookie_t os::Solaris::_lgrp_cookie = 0;

// (Static) wrapper for meminfo() call.
os::Solaris::meminfo_func_t os::Solaris::_meminfo = 0;

static address resolve_symbol_lazy(const char* name) {
  address addr = (address) dlsym(RTLD_DEFAULT, name);
  if(addr == NULL) {
    // RTLD_DEFAULT was not defined on some early versions of 2.5.1
    addr = (address) dlsym(RTLD_NEXT, name);
  }
  return addr;
}

static address resolve_symbol(const char* name) {
  address addr = resolve_symbol_lazy(name);
  if(addr == NULL) {
    fatal(dlerror());
  }
  return addr;
}



// isT2_libthread()
//
// Routine to determine if we are currently using the new T2 libthread.
//
// We determine if we are using T2 by reading /proc/self/lstatus and
// looking for a thread with the ASLWP bit set.  If we find this status
// bit set, we must assume that we are NOT using T2.  The T2 team
// has approved this algorithm.
//
// We need to determine if we are running with the new T2 libthread
// since setting native thread priorities is handled differently
// when using this library.  All threads created using T2 are bound
// threads. Calling thr_setprio is meaningless in this case.
//
bool isT2_libthread() {
  static prheader_t * lwpArray = NULL;
  static int lwpSize = 0;
  static int lwpFile = -1;
  lwpstatus_t * that;
  char lwpName [128];
  bool isT2 = false;

#define ADR(x)  ((uintptr_t)(x))
#define LWPINDEX(ary,ix)   ((lwpstatus_t *)(((ary)->pr_entsize * (ix)) + (ADR((ary) + 1))))

  lwpFile = open("/proc/self/lstatus", O_RDONLY, 0);
  if (lwpFile < 0) {
      if (ThreadPriorityVerbose) warning ("Couldn't open /proc/self/lstatus\n");
      return false;
  }
  lwpSize = 16*1024;
  for (;;) {
    lseek (lwpFile, 0, SEEK_SET);
    lwpArray = (prheader_t *)NEW_C_HEAP_ARRAY(char, lwpSize);
    if (read(lwpFile, lwpArray, lwpSize) < 0) {
      if (ThreadPriorityVerbose) warning("Error reading /proc/self/lstatus\n");
      break;
    }
    if ((lwpArray->pr_nent * lwpArray->pr_entsize) <= lwpSize) {
       // We got a good snapshot - now iterate over the list.
      int aslwpcount = 0;
      for (int i = 0; i < lwpArray->pr_nent; i++ ) {
        that = LWPINDEX(lwpArray,i);
        if (that->pr_flags & PR_ASLWP) {
          aslwpcount++;
        }
      }
      if (aslwpcount == 0) isT2 = true;
      break;
    }
    lwpSize = lwpArray->pr_nent * lwpArray->pr_entsize;
    FREE_C_HEAP_ARRAY(char, lwpArray);  // retry.
  }

  FREE_C_HEAP_ARRAY(char, lwpArray);
  close (lwpFile);
  if (ThreadPriorityVerbose) {
    if (isT2) tty->print_cr("We are running with a T2 libthread\n");
    else tty->print_cr("We are not running with a T2 libthread\n");
  }
  return isT2;
}


void os::Solaris::libthread_init() {
  address func = (address)dlsym(RTLD_DEFAULT, "_thr_suspend_allmutators");

  // Determine if we are running with the new T2 libthread
  os::Solaris::set_T2_libthread(isT2_libthread());

  lwp_priocntl_init();

  // RTLD_DEFAULT was not defined on some early versions of 5.5.1
  if(func == NULL) {
    func = (address) dlsym(RTLD_NEXT, "_thr_suspend_allmutators");
    // Guarantee that this VM is running on an new enough OS (5.6 or
    // later) that it will have a new enough libthread.so.
    guarantee(func != NULL, "libthread.so is too old.");
  }

  // Initialize the new libthread getstate API wrappers
  func = resolve_symbol("thr_getstate");
  os::Solaris::set_thr_getstate(CAST_TO_FN_PTR(int_fnP_thread_t_iP_uP_stack_tP_gregset_t, func));

  func = resolve_symbol("thr_setstate");
  os::Solaris::set_thr_setstate(CAST_TO_FN_PTR(int_fnP_thread_t_i_gregset_t, func));

  func = resolve_symbol("thr_setmutator");
  os::Solaris::set_thr_setmutator(CAST_TO_FN_PTR(int_fnP_thread_t_i, func));

  func = resolve_symbol("thr_suspend_mutator");
  os::Solaris::set_thr_suspend_mutator(CAST_TO_FN_PTR(int_fnP_thread_t, func));

  func = resolve_symbol("thr_continue_mutator");
  os::Solaris::set_thr_continue_mutator(CAST_TO_FN_PTR(int_fnP_thread_t, func));

  int size;
  void (*handler_info_func)(address *, int *);
  handler_info_func = CAST_TO_FN_PTR(void (*)(address *, int *), resolve_symbol("thr_sighndlrinfo"));
  handler_info_func(&handler_start, &size);
  handler_end = handler_start + size;
}


int_fnP_mutex_tP os::Solaris::_mutex_lock;
int_fnP_mutex_tP os::Solaris::_mutex_trylock;
int_fnP_mutex_tP os::Solaris::_mutex_unlock;
int_fnP_mutex_tP_i_vP os::Solaris::_mutex_init;
int_fnP_mutex_tP os::Solaris::_mutex_destroy;
int os::Solaris::_mutex_scope = USYNC_THREAD;

int_fnP_cond_tP_mutex_tP_timestruc_tP os::Solaris::_cond_timedwait;
int_fnP_cond_tP_mutex_tP os::Solaris::_cond_wait;
int_fnP_cond_tP os::Solaris::_cond_signal;
int_fnP_cond_tP os::Solaris::_cond_broadcast;
int_fnP_cond_tP_i_vP os::Solaris::_cond_init;
int_fnP_cond_tP os::Solaris::_cond_destroy;
int os::Solaris::_cond_scope = USYNC_THREAD;

void os::Solaris::synchronization_init() {
  if(UseLWPSynchronization) {
    os::Solaris::set_mutex_lock(CAST_TO_FN_PTR(int_fnP_mutex_tP, resolve_symbol("_lwp_mutex_lock")));
    os::Solaris::set_mutex_trylock(CAST_TO_FN_PTR(int_fnP_mutex_tP, resolve_symbol("_lwp_mutex_trylock")));
    os::Solaris::set_mutex_unlock(CAST_TO_FN_PTR(int_fnP_mutex_tP, resolve_symbol("_lwp_mutex_unlock")));
    os::Solaris::set_mutex_init(lwp_mutex_init);
    os::Solaris::set_mutex_destroy(lwp_mutex_destroy);
    os::Solaris::set_mutex_scope(USYNC_THREAD);

    os::Solaris::set_cond_timedwait(CAST_TO_FN_PTR(int_fnP_cond_tP_mutex_tP_timestruc_tP, resolve_symbol("_lwp_cond_timedwait")));
    os::Solaris::set_cond_wait(CAST_TO_FN_PTR(int_fnP_cond_tP_mutex_tP, resolve_symbol("_lwp_cond_wait")));
    os::Solaris::set_cond_signal(CAST_TO_FN_PTR(int_fnP_cond_tP, resolve_symbol("_lwp_cond_signal")));
    os::Solaris::set_cond_broadcast(CAST_TO_FN_PTR(int_fnP_cond_tP, resolve_symbol("_lwp_cond_broadcast")));
    os::Solaris::set_cond_init(lwp_cond_init);
    os::Solaris::set_cond_destroy(lwp_cond_destroy);
    os::Solaris::set_cond_scope(USYNC_THREAD);
  }
  else {
    os::Solaris::set_mutex_scope(USYNC_THREAD);
    os::Solaris::set_cond_scope(USYNC_THREAD);

    if(UsePthreads) {
      os::Solaris::set_mutex_lock(CAST_TO_FN_PTR(int_fnP_mutex_tP, resolve_symbol("pthread_mutex_lock")));
      os::Solaris::set_mutex_trylock(CAST_TO_FN_PTR(int_fnP_mutex_tP, resolve_symbol("pthread_mutex_trylock")));
      os::Solaris::set_mutex_unlock(CAST_TO_FN_PTR(int_fnP_mutex_tP, resolve_symbol("pthread_mutex_unlock")));
      os::Solaris::set_mutex_init(pthread_mutex_default_init);
      os::Solaris::set_mutex_destroy(CAST_TO_FN_PTR(int_fnP_mutex_tP, resolve_symbol("pthread_mutex_destroy")));

      os::Solaris::set_cond_timedwait(CAST_TO_FN_PTR(int_fnP_cond_tP_mutex_tP_timestruc_tP, resolve_symbol("pthread_cond_timedwait")));
      os::Solaris::set_cond_wait(CAST_TO_FN_PTR(int_fnP_cond_tP_mutex_tP, resolve_symbol("pthread_cond_wait")));
      os::Solaris::set_cond_signal(CAST_TO_FN_PTR(int_fnP_cond_tP, resolve_symbol("pthread_cond_signal")));
      os::Solaris::set_cond_broadcast(CAST_TO_FN_PTR(int_fnP_cond_tP, resolve_symbol("pthread_cond_broadcast")));
      os::Solaris::set_cond_init(pthread_cond_default_init);
      os::Solaris::set_cond_destroy(CAST_TO_FN_PTR(int_fnP_cond_tP, resolve_symbol("pthread_cond_destroy")));
    }
    else {
      os::Solaris::set_mutex_lock(CAST_TO_FN_PTR(int_fnP_mutex_tP, resolve_symbol("mutex_lock")));
      os::Solaris::set_mutex_trylock(CAST_TO_FN_PTR(int_fnP_mutex_tP, resolve_symbol("mutex_trylock")));
      os::Solaris::set_mutex_unlock(CAST_TO_FN_PTR(int_fnP_mutex_tP, resolve_symbol("mutex_unlock")));
      os::Solaris::set_mutex_init(::mutex_init);
      os::Solaris::set_mutex_destroy(::mutex_destroy);

      os::Solaris::set_cond_timedwait(CAST_TO_FN_PTR(int_fnP_cond_tP_mutex_tP_timestruc_tP, resolve_symbol("cond_timedwait")));
      os::Solaris::set_cond_wait(CAST_TO_FN_PTR(int_fnP_cond_tP_mutex_tP, resolve_symbol("cond_wait")));
      os::Solaris::set_cond_signal(CAST_TO_FN_PTR(int_fnP_cond_tP, resolve_symbol("cond_signal")));
      os::Solaris::set_cond_broadcast(CAST_TO_FN_PTR(int_fnP_cond_tP, resolve_symbol("cond_broadcast")));
      os::Solaris::set_cond_init(::cond_init);
      os::Solaris::set_cond_destroy(::cond_destroy);
    }
  }
}

bool os::Solaris::liblgrp_init() {
  void *handle = dlopen("liblgrp.so.1", RTLD_LAZY);
  if (handle != NULL) {
    os::Solaris::set_lgrp_home(CAST_TO_FN_PTR(lgrp_home_func_t, dlsym(handle, "lgrp_home")));
    os::Solaris::set_lgrp_init(CAST_TO_FN_PTR(lgrp_init_func_t, dlsym(handle, "lgrp_init")));
    os::Solaris::set_lgrp_fini(CAST_TO_FN_PTR(lgrp_fini_func_t, dlsym(handle, "lgrp_fini")));
    os::Solaris::set_lgrp_root(CAST_TO_FN_PTR(lgrp_root_func_t, dlsym(handle, "lgrp_root")));
    os::Solaris::set_lgrp_children(CAST_TO_FN_PTR(lgrp_children_func_t, dlsym(handle, "lgrp_children")));
    os::Solaris::set_lgrp_resources(CAST_TO_FN_PTR(lgrp_resources_func_t, dlsym(handle, "lgrp_resources")));
    os::Solaris::set_lgrp_nlgrps(CAST_TO_FN_PTR(lgrp_nlgrps_func_t, dlsym(handle, "lgrp_nlgrps")));
    os::Solaris::set_lgrp_cookie_stale(CAST_TO_FN_PTR(lgrp_cookie_stale_func_t,
                                       dlsym(handle, "lgrp_cookie_stale")));

    lgrp_cookie_t c = lgrp_init(LGRP_VIEW_CALLER);
    set_lgrp_cookie(c);
    return true;
  }
  return false;
}

void os::Solaris::misc_sym_init() {
  address func;

  // getisax
  func = resolve_symbol_lazy("getisax");
  if (func != NULL) {
    os::Solaris::_getisax = CAST_TO_FN_PTR(getisax_func_t, func);
  }

  // meminfo
  func = resolve_symbol_lazy("meminfo");
  if (func != NULL) {
    os::Solaris::set_meminfo(CAST_TO_FN_PTR(meminfo_func_t, func));
  }
}

uint_t os::Solaris::getisax(uint32_t* array, uint_t n) {
  assert(_getisax != NULL, "_getisax not set");
  return _getisax(array, n);
}

// Symbol doesn't exist in Solaris 8 pset.h
#ifndef PS_MYID
#define PS_MYID -3
#endif

// int pset_getloadavg(psetid_t pset, double loadavg[], int nelem);
typedef long (*pset_getloadavg_type)(psetid_t pset, double loadavg[], int nelem);
static pset_getloadavg_type pset_getloadavg_ptr = NULL;

void init_pset_getloadavg_ptr(void) {
  pset_getloadavg_ptr =
    (pset_getloadavg_type)dlsym(RTLD_DEFAULT, "pset_getloadavg");
  if (PrintMiscellaneous && Verbose && pset_getloadavg_ptr == NULL) {
    warning("pset_getloadavg function not found");
  }
}

int os::Solaris::_dev_zero_fd = -1;

// this is called _before_ the global arguments have been parsed
void os::init(void) {
  _initial_pid = getpid();

  max_hrtime = first_hrtime = gethrtime();

  init_random(1234567);

  page_size = sysconf(_SC_PAGESIZE);
  if (page_size == -1)
    fatal(err_msg("os_solaris.cpp: os::init: sysconf failed (%s)",
                  strerror(errno)));
  init_page_sizes((size_t) page_size);

  Solaris::initialize_system_info();

  // Initialize misc. symbols as soon as possible, so we can use them
  // if we need them.
  Solaris::misc_sym_init();

  int fd = open("/dev/zero", O_RDWR);
  if (fd < 0) {
    fatal(err_msg("os::init: cannot open /dev/zero (%s)", strerror(errno)));
  } else {
    Solaris::set_dev_zero_fd(fd);

    // Close on exec, child won't inherit.
    fcntl(fd, F_SETFD, FD_CLOEXEC);
  }

  clock_tics_per_sec = CLK_TCK;

  // check if dladdr1() exists; dladdr1 can provide more information than
  // dladdr for os::dll_address_to_function_name. It comes with SunOS 5.9
  // and is available on linker patches for 5.7 and 5.8.
  // libdl.so must have been loaded, this call is just an entry lookup
  void * hdl = dlopen("libdl.so", RTLD_NOW);
  if (hdl)
    dladdr1_func = CAST_TO_FN_PTR(dladdr1_func_type, dlsym(hdl, "dladdr1"));

  // (Solaris only) this switches to calls that actually do locking.
  ThreadCritical::initialize();

  main_thread = thr_self();

  // Constant minimum stack size allowed. It must be at least
  // the minimum of what the OS supports (thr_min_stack()), and
  // enough to allow the thread to get to user bytecode execution.
  Solaris::min_stack_allowed = MAX2(thr_min_stack(), Solaris::min_stack_allowed);
  // If the pagesize of the VM is greater than 8K determine the appropriate
  // number of initial guard pages.  The user can change this with the
  // command line arguments, if needed.
  if (vm_page_size() > 8*K) {
    StackYellowPages = 1;
    StackRedPages = 1;
    StackShadowPages = round_to((StackShadowPages*8*K), vm_page_size()) / vm_page_size();
  }
}

// To install functions for atexit system call
extern "C" {
  static void perfMemory_exit_helper() {
    perfMemory_exit();
  }
}

// this is called _after_ the global arguments have been parsed
jint os::init_2(void) {
  // try to enable extended file IO ASAP, see 6431278
  os::Solaris::try_enable_extended_io();

  // Allocate a single page and mark it as readable for safepoint polling.  Also
  // use this first mmap call to check support for MAP_ALIGN.
  address polling_page = (address)Solaris::mmap_chunk((char*)page_size,
                                                      page_size,
                                                      MAP_PRIVATE | MAP_ALIGN,
                                                      PROT_READ);
  if (polling_page == NULL) {
    has_map_align = false;
    polling_page = (address)Solaris::mmap_chunk(NULL, page_size, MAP_PRIVATE,
                                                PROT_READ);
  }

  os::set_polling_page(polling_page);

#ifndef PRODUCT
  if( Verbose && PrintMiscellaneous )
    tty->print("[SafePoint Polling address: " INTPTR_FORMAT "]\n", (intptr_t)polling_page);
#endif

  if (!UseMembar) {
    address mem_serialize_page = (address)Solaris::mmap_chunk( NULL, page_size, MAP_PRIVATE, PROT_READ | PROT_WRITE );
    guarantee( mem_serialize_page != NULL, "mmap Failed for memory serialize page");
    os::set_memory_serialize_page( mem_serialize_page );

#ifndef PRODUCT
    if(Verbose && PrintMiscellaneous)
      tty->print("[Memory Serialize  Page address: " INTPTR_FORMAT "]\n", (intptr_t)mem_serialize_page);
#endif
}

  FLAG_SET_DEFAULT(UseLargePages, os::large_page_init());

  // Check minimum allowable stack size for thread creation and to initialize
  // the java system classes, including StackOverflowError - depends on page
  // size.  Add a page for compiler2 recursion in main thread.
  // Add in BytesPerWord times page size to account for VM stack during
  // class initialization depending on 32 or 64 bit VM.
  guarantee((Solaris::min_stack_allowed >=
    (StackYellowPages+StackRedPages+StackShadowPages+BytesPerWord
     COMPILER2_PRESENT(+1)) * page_size),
    "need to increase Solaris::min_stack_allowed on this platform");

  size_t threadStackSizeInBytes = ThreadStackSize * K;
  if (threadStackSizeInBytes != 0 &&
    threadStackSizeInBytes < Solaris::min_stack_allowed) {
    tty->print_cr("\nThe stack size specified is too small, Specify at least %dk",
                  Solaris::min_stack_allowed/K);
    return JNI_ERR;
  }

  // For 64kbps there will be a 64kb page size, which makes
  // the usable default stack size quite a bit less.  Increase the
  // stack for 64kb (or any > than 8kb) pages, this increases
  // virtual memory fragmentation (since we're not creating the
  // stack on a power of 2 boundary.  The real fix for this
  // should be to fix the guard page mechanism.

  if (vm_page_size() > 8*K) {
      threadStackSizeInBytes = (threadStackSizeInBytes != 0)
         ? threadStackSizeInBytes +
           ((StackYellowPages + StackRedPages) * vm_page_size())
         : 0;
      ThreadStackSize = threadStackSizeInBytes/K;
  }

  // Make the stack size a multiple of the page size so that
  // the yellow/red zones can be guarded.
  JavaThread::set_stack_size_at_create(round_to(threadStackSizeInBytes,
        vm_page_size()));

  Solaris::libthread_init();

  if (UseNUMA) {
    if (!Solaris::liblgrp_init()) {
      UseNUMA = false;
    } else {
      size_t lgrp_limit = os::numa_get_groups_num();
      int *lgrp_ids = NEW_C_HEAP_ARRAY(int, lgrp_limit);
      size_t lgrp_num = os::numa_get_leaf_groups(lgrp_ids, lgrp_limit);
      FREE_C_HEAP_ARRAY(int, lgrp_ids);
      if (lgrp_num < 2) {
        // There's only one locality group, disable NUMA.
        UseNUMA = false;
      }
    }
    if (!UseNUMA && ForceNUMA) {
      UseNUMA = true;
    }
  }

  Solaris::signal_sets_init();
  Solaris::init_signal_mem();
  Solaris::install_signal_handlers();

  if (libjsigversion < JSIG_VERSION_1_4_1) {
    Maxlibjsigsigs = OLDMAXSIGNUM;
  }

  // initialize synchronization primitives to use either thread or
  // lwp synchronization (controlled by UseLWPSynchronization)
  Solaris::synchronization_init();

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

  // Initialize HPI.
  jint hpi_result = hpi::initialize();
  if (hpi_result != JNI_OK) {
    tty->print_cr("There was an error trying to initialize the HPI library.");
    return hpi_result;
  }

  // Calculate theoretical max. size of Threads to guard gainst
  // artifical out-of-memory situations, where all available address-
  // space has been reserved by thread stacks. Default stack size is 1Mb.
  size_t pre_thread_stack_size = (JavaThread::stack_size_at_create()) ?
    JavaThread::stack_size_at_create() : (1*K*K);
  assert(pre_thread_stack_size != 0, "Must have a stack");
  // Solaris has a maximum of 4Gb of user programs. Calculate the thread limit when
  // we should start doing Virtual Memory banging. Currently when the threads will
  // have used all but 200Mb of space.
  size_t max_address_space = ((unsigned int)4 * K * K * K) - (200 * K * K);
  Solaris::_os_thread_limit = max_address_space / pre_thread_stack_size;

  // at-exit methods are called in the reverse order of their registration.
  // In Solaris 7 and earlier, atexit functions are called on return from
  // main or as a result of a call to exit(3C). There can be only 32 of
  // these functions registered and atexit() does not set errno. In Solaris
  // 8 and later, there is no limit to the number of functions registered
  // and atexit() sets errno. In addition, in Solaris 8 and later, atexit
  // functions are called upon dlclose(3DL) in addition to return from main
  // and exit(3C).

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

  // Init pset_loadavg function pointer
  init_pset_getloadavg_ptr();

  return JNI_OK;
}

void os::init_3(void) {
  return;
}

// Mark the polling page as unreadable
void os::make_polling_page_unreadable(void) {
  if( mprotect((char *)_polling_page, page_size, PROT_NONE) != 0 )
    fatal("Could not disable polling page");
};

// Mark the polling page as readable
void os::make_polling_page_readable(void) {
  if( mprotect((char *)_polling_page, page_size, PROT_READ) != 0 )
    fatal("Could not enable polling page");
};

// OS interface.

int os::stat(const char *path, struct stat *sbuf) {
  char pathbuf[MAX_PATH];
  if (strlen(path) > MAX_PATH - 1) {
    errno = ENAMETOOLONG;
    return -1;
  }
  hpi::native_path(strcpy(pathbuf, path));
  return ::stat(pathbuf, sbuf);
}


bool os::check_heap(bool force) { return true; }

typedef int (*vsnprintf_t)(char* buf, size_t count, const char* fmt, va_list argptr);
static vsnprintf_t sol_vsnprintf = NULL;

int local_vsnprintf(char* buf, size_t count, const char* fmt, va_list argptr) {
  if (!sol_vsnprintf) {
    //search  for the named symbol in the objects that were loaded after libjvm
    void* where = RTLD_NEXT;
    if ((sol_vsnprintf = CAST_TO_FN_PTR(vsnprintf_t, dlsym(where, "__vsnprintf"))) == NULL)
        sol_vsnprintf = CAST_TO_FN_PTR(vsnprintf_t, dlsym(where, "vsnprintf"));
    if (!sol_vsnprintf){
      //search  for the named symbol in the objects that were loaded before libjvm
      where = RTLD_DEFAULT;
      if ((sol_vsnprintf = CAST_TO_FN_PTR(vsnprintf_t, dlsym(where, "__vsnprintf"))) == NULL)
        sol_vsnprintf = CAST_TO_FN_PTR(vsnprintf_t, dlsym(where, "vsnprintf"));
      assert(sol_vsnprintf != NULL, "vsnprintf not found");
    }
  }
  return (*sol_vsnprintf)(buf, count, fmt, argptr);
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
  struct dirent *dbuf = (struct dirent *) buf;
  while (result && (ptr = readdir(dir, dbuf)) != NULL) {
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

#ifndef PRODUCT
#ifdef INTERPOSE_ON_SYSTEM_SYNCH_FUNCTIONS
// Turn this on if you need to trace synch operations.
// Set RECORD_SYNCH_LIMIT to a large-enough value,
// and call record_synch_enable and record_synch_disable
// around the computation of interest.

void record_synch(char* name, bool returning);  // defined below

class RecordSynch {
  char* _name;
 public:
  RecordSynch(char* name) :_name(name)
                 { record_synch(_name, false); }
  ~RecordSynch() { record_synch(_name,   true);  }
};

#define CHECK_SYNCH_OP(ret, name, params, args, inner)          \
extern "C" ret name params {                                    \
  typedef ret name##_t params;                                  \
  static name##_t* implem = NULL;                               \
  static int callcount = 0;                                     \
  if (implem == NULL) {                                         \
    implem = (name##_t*) dlsym(RTLD_NEXT, #name);               \
    if (implem == NULL)  fatal(dlerror());                      \
  }                                                             \
  ++callcount;                                                  \
  RecordSynch _rs(#name);                                       \
  inner;                                                        \
  return implem args;                                           \
}
// in dbx, examine callcounts this way:
// for n in $(eval whereis callcount | awk '{print $2}'); do print $n; done

#define CHECK_POINTER_OK(p) \
  (Universe::perm_gen() == NULL || !Universe::is_reserved_heap((oop)(p)))
#define CHECK_MU \
  if (!CHECK_POINTER_OK(mu)) fatal("Mutex must be in C heap only.");
#define CHECK_CV \
  if (!CHECK_POINTER_OK(cv)) fatal("Condvar must be in C heap only.");
#define CHECK_P(p) \
  if (!CHECK_POINTER_OK(p))  fatal(false,  "Pointer must be in C heap only.");

#define CHECK_MUTEX(mutex_op) \
CHECK_SYNCH_OP(int, mutex_op, (mutex_t *mu), (mu), CHECK_MU);

CHECK_MUTEX(   mutex_lock)
CHECK_MUTEX(  _mutex_lock)
CHECK_MUTEX( mutex_unlock)
CHECK_MUTEX(_mutex_unlock)
CHECK_MUTEX( mutex_trylock)
CHECK_MUTEX(_mutex_trylock)

#define CHECK_COND(cond_op) \
CHECK_SYNCH_OP(int, cond_op, (cond_t *cv, mutex_t *mu), (cv, mu), CHECK_MU;CHECK_CV);

CHECK_COND( cond_wait);
CHECK_COND(_cond_wait);
CHECK_COND(_cond_wait_cancel);

#define CHECK_COND2(cond_op) \
CHECK_SYNCH_OP(int, cond_op, (cond_t *cv, mutex_t *mu, timestruc_t* ts), (cv, mu, ts), CHECK_MU;CHECK_CV);

CHECK_COND2( cond_timedwait);
CHECK_COND2(_cond_timedwait);
CHECK_COND2(_cond_timedwait_cancel);

// do the _lwp_* versions too
#define mutex_t lwp_mutex_t
#define cond_t  lwp_cond_t
CHECK_MUTEX(  _lwp_mutex_lock)
CHECK_MUTEX(  _lwp_mutex_unlock)
CHECK_MUTEX(  _lwp_mutex_trylock)
CHECK_MUTEX( __lwp_mutex_lock)
CHECK_MUTEX( __lwp_mutex_unlock)
CHECK_MUTEX( __lwp_mutex_trylock)
CHECK_MUTEX(___lwp_mutex_lock)
CHECK_MUTEX(___lwp_mutex_unlock)

CHECK_COND(  _lwp_cond_wait);
CHECK_COND( __lwp_cond_wait);
CHECK_COND(___lwp_cond_wait);

CHECK_COND2(  _lwp_cond_timedwait);
CHECK_COND2( __lwp_cond_timedwait);
#undef mutex_t
#undef cond_t

CHECK_SYNCH_OP(int, _lwp_suspend2,       (int lwp, int *n), (lwp, n), 0);
CHECK_SYNCH_OP(int,__lwp_suspend2,       (int lwp, int *n), (lwp, n), 0);
CHECK_SYNCH_OP(int, _lwp_kill,           (int lwp, int n),  (lwp, n), 0);
CHECK_SYNCH_OP(int,__lwp_kill,           (int lwp, int n),  (lwp, n), 0);
CHECK_SYNCH_OP(int, _lwp_sema_wait,      (lwp_sema_t* p),   (p),  CHECK_P(p));
CHECK_SYNCH_OP(int,__lwp_sema_wait,      (lwp_sema_t* p),   (p),  CHECK_P(p));
CHECK_SYNCH_OP(int, _lwp_cond_broadcast, (lwp_cond_t* cv),  (cv), CHECK_CV);
CHECK_SYNCH_OP(int,__lwp_cond_broadcast, (lwp_cond_t* cv),  (cv), CHECK_CV);


// recording machinery:

enum { RECORD_SYNCH_LIMIT = 200 };
char* record_synch_name[RECORD_SYNCH_LIMIT];
void* record_synch_arg0ptr[RECORD_SYNCH_LIMIT];
bool record_synch_returning[RECORD_SYNCH_LIMIT];
thread_t record_synch_thread[RECORD_SYNCH_LIMIT];
int record_synch_count = 0;
bool record_synch_enabled = false;

// in dbx, examine recorded data this way:
// for n in name arg0ptr returning thread; do print record_synch_$n[0..record_synch_count-1]; done

void record_synch(char* name, bool returning) {
  if (record_synch_enabled) {
    if (record_synch_count < RECORD_SYNCH_LIMIT) {
      record_synch_name[record_synch_count] = name;
      record_synch_returning[record_synch_count] = returning;
      record_synch_thread[record_synch_count] = thr_self();
      record_synch_arg0ptr[record_synch_count] = &name;
      record_synch_count++;
    }
    // put more checking code here:
    // ...
  }
}

void record_synch_enable() {
  // start collecting trace data, if not already doing so
  if (!record_synch_enabled)  record_synch_count = 0;
  record_synch_enabled = true;
}

void record_synch_disable() {
  // stop collecting trace data
  record_synch_enabled = false;
}

#endif // INTERPOSE_ON_SYSTEM_SYNCH_FUNCTIONS
#endif // PRODUCT

const intptr_t thr_time_off  = (intptr_t)(&((prusage_t *)(NULL))->pr_utime);
const intptr_t thr_time_size = (intptr_t)(&((prusage_t *)(NULL))->pr_ttime) -
                               (intptr_t)(&((prusage_t *)(NULL))->pr_utime);


// JVMTI & JVM monitoring and management support
// The thread_cpu_time() and current_thread_cpu_time() are only
// supported if is_thread_cpu_time_supported() returns true.
// They are not supported on Solaris T1.

// current_thread_cpu_time(bool) and thread_cpu_time(Thread*, bool)
// are used by JVM M&M and JVMTI to get user+sys or user CPU time
// of a thread.
//
// current_thread_cpu_time() and thread_cpu_time(Thread *)
// returns the fast estimate available on the platform.

// hrtime_t gethrvtime() return value includes
// user time but does not include system time
jlong os::current_thread_cpu_time() {
  return (jlong) gethrvtime();
}

jlong os::thread_cpu_time(Thread *thread) {
  // return user level CPU time only to be consistent with
  // what current_thread_cpu_time returns.
  // thread_cpu_time_info() must be changed if this changes
  return os::thread_cpu_time(thread, false /* user time only */);
}

jlong os::current_thread_cpu_time(bool user_sys_cpu_time) {
  if (user_sys_cpu_time) {
    return os::thread_cpu_time(Thread::current(), user_sys_cpu_time);
  } else {
    return os::current_thread_cpu_time();
  }
}

jlong os::thread_cpu_time(Thread *thread, bool user_sys_cpu_time) {
  char proc_name[64];
  int count;
  prusage_t prusage;
  jlong lwp_time;
  int fd;

  sprintf(proc_name, "/proc/%d/lwp/%d/lwpusage",
                     getpid(),
                     thread->osthread()->lwp_id());
  fd = open(proc_name, O_RDONLY);
  if ( fd == -1 ) return -1;

  do {
    count = pread(fd,
                  (void *)&prusage.pr_utime,
                  thr_time_size,
                  thr_time_off);
  } while (count < 0 && errno == EINTR);
  close(fd);
  if ( count < 0 ) return -1;

  if (user_sys_cpu_time) {
    // user + system CPU time
    lwp_time = (((jlong)prusage.pr_stime.tv_sec +
                 (jlong)prusage.pr_utime.tv_sec) * (jlong)1000000000) +
                 (jlong)prusage.pr_stime.tv_nsec +
                 (jlong)prusage.pr_utime.tv_nsec;
  } else {
    // user level CPU time only
    lwp_time = ((jlong)prusage.pr_utime.tv_sec * (jlong)1000000000) +
                (jlong)prusage.pr_utime.tv_nsec;
  }

  return(lwp_time);
}

void os::current_thread_cpu_time_info(jvmtiTimerInfo *info_ptr) {
  info_ptr->max_value = ALL_64_BITS;      // will not wrap in less than 64 bits
  info_ptr->may_skip_backward = false;    // elapsed time not wall time
  info_ptr->may_skip_forward = false;     // elapsed time not wall time
  info_ptr->kind = JVMTI_TIMER_USER_CPU;  // only user time is returned
}

void os::thread_cpu_time_info(jvmtiTimerInfo *info_ptr) {
  info_ptr->max_value = ALL_64_BITS;      // will not wrap in less than 64 bits
  info_ptr->may_skip_backward = false;    // elapsed time not wall time
  info_ptr->may_skip_forward = false;     // elapsed time not wall time
  info_ptr->kind = JVMTI_TIMER_USER_CPU;  // only user time is returned
}

bool os::is_thread_cpu_time_supported() {
  if ( os::Solaris::T2_libthread() || UseBoundThreads ) {
    return true;
  } else {
    return false;
  }
}

// System loadavg support.  Returns -1 if load average cannot be obtained.
// Return the load average for our processor set if the primitive exists
// (Solaris 9 and later).  Otherwise just return system wide loadavg.
int os::loadavg(double loadavg[], int nelem) {
  if (pset_getloadavg_ptr != NULL) {
    return (*pset_getloadavg_ptr)(PS_MYID, loadavg, nelem);
  } else {
    return ::getloadavg(loadavg, nelem);
  }
}

//---------------------------------------------------------------------------------

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
  Dl_info dlinfo;
  memset(&dlinfo, 0, sizeof(dlinfo));
  if (dladdr(addr, &dlinfo)) {
#ifdef _LP64
    st->print("0x%016lx: ", addr);
#else
    st->print("0x%08x: ", addr);
#endif
    if (dlinfo.dli_sname != NULL)
      st->print("%s+%#lx", dlinfo.dli_sname, addr-(intptr_t)dlinfo.dli_saddr);
    else if (dlinfo.dli_fname)
      st->print("<offset %#lx>", addr-(intptr_t)dlinfo.dli_fbase);
    else
      st->print("<absolute address>");
    if (dlinfo.dli_fname)  st->print(" in %s", dlinfo.dli_fname);
#ifdef _LP64
    if (dlinfo.dli_fbase)  st->print(" at 0x%016lx", dlinfo.dli_fbase);
#else
    if (dlinfo.dli_fbase)  st->print(" at 0x%08x", dlinfo.dli_fbase);
#endif
    st->cr();

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
      Disassembler::decode(begin, end, st);
    }
    return true;
  }
  return false;
}

// Following function has been added to support HotSparc's libjvm.so running
// under Solaris production JDK 1.2.2 / 1.3.0.  These came from
// src/solaris/hpi/native_threads in the EVM codebase.
//
// NOTE: This is no longer needed in the 1.3.1 and 1.4 production release
// libraries and should thus be removed. We will leave it behind for a while
// until we no longer want to able to run on top of 1.3.0 Solaris production
// JDK. See 4341971.

#define STACK_SLACK 0x800

extern "C" {
  intptr_t sysThreadAvailableStackWithSlack() {
    stack_t st;
    intptr_t retval, stack_top;
    retval = thr_stksegment(&st);
    assert(retval == 0, "incorrect return value from thr_stksegment");
    assert((address)&st < (address)st.ss_sp, "Invalid stack base returned");
    assert((address)&st > (address)st.ss_sp-st.ss_size, "Invalid stack size returned");
    stack_top=(intptr_t)st.ss_sp-st.ss_size;
    return ((intptr_t)&stack_top - stack_top - STACK_SLACK);
  }
}

// Just to get the Kernel build to link on solaris for testing.

extern "C" {
class ASGCT_CallTrace;
void AsyncGetCallTrace(ASGCT_CallTrace *trace, jint depth, void* ucontext)
  KERNEL_RETURN;
}


// ObjectMonitor park-unpark infrastructure ...
//
// We implement Solaris and Linux PlatformEvents with the
// obvious condvar-mutex-flag triple.
// Another alternative that works quite well is pipes:
// Each PlatformEvent consists of a pipe-pair.
// The thread associated with the PlatformEvent
// calls park(), which reads from the input end of the pipe.
// Unpark() writes into the other end of the pipe.
// The write-side of the pipe must be set NDELAY.
// Unfortunately pipes consume a large # of handles.
// Native solaris lwp_park() and lwp_unpark() work nicely, too.
// Using pipes for the 1st few threads might be workable, however.
//
// park() is permitted to return spuriously.
// Callers of park() should wrap the call to park() in
// an appropriate loop.  A litmus test for the correct
// usage of park is the following: if park() were modified
// to immediately return 0 your code should still work,
// albeit degenerating to a spin loop.
//
// An interesting optimization for park() is to use a trylock()
// to attempt to acquire the mutex.  If the trylock() fails
// then we know that a concurrent unpark() operation is in-progress.
// in that case the park() code could simply set _count to 0
// and return immediately.  The subsequent park() operation *might*
// return immediately.  That's harmless as the caller of park() is
// expected to loop.  By using trylock() we will have avoided a
// avoided a context switch caused by contention on the per-thread mutex.
//
// TODO-FIXME:
// 1.  Reconcile Doug's JSR166 j.u.c park-unpark with the
//     objectmonitor implementation.
// 2.  Collapse the JSR166 parker event, and the
//     objectmonitor ParkEvent into a single "Event" construct.
// 3.  In park() and unpark() add:
//     assert (Thread::current() == AssociatedWith).
// 4.  add spurious wakeup injection on a -XX:EarlyParkReturn=N switch.
//     1-out-of-N park() operations will return immediately.
//
// _Event transitions in park()
//   -1 => -1 : illegal
//    1 =>  0 : pass - return immediately
//    0 => -1 : block
//
// _Event serves as a restricted-range semaphore.
//
// Another possible encoding of _Event would be with
// explicit "PARKED" == 01b and "SIGNALED" == 10b bits.
//
// TODO-FIXME: add DTRACE probes for:
// 1.   Tx parks
// 2.   Ty unparks Tx
// 3.   Tx resumes from park


// value determined through experimentation
#define ROUNDINGFIX 11

// utility to compute the abstime argument to timedwait.
// TODO-FIXME: switch from compute_abstime() to unpackTime().

static timestruc_t* compute_abstime(timestruc_t* abstime, jlong millis) {
  // millis is the relative timeout time
  // abstime will be the absolute timeout time
  if (millis < 0)  millis = 0;
  struct timeval now;
  int status = gettimeofday(&now, NULL);
  assert(status == 0, "gettimeofday");
  jlong seconds = millis / 1000;
  jlong max_wait_period;

  if (UseLWPSynchronization) {
    // forward port of fix for 4275818 (not sleeping long enough)
    // There was a bug in Solaris 6, 7 and pre-patch 5 of 8 where
    // _lwp_cond_timedwait() used a round_down algorithm rather
    // than a round_up. For millis less than our roundfactor
    // it rounded down to 0 which doesn't meet the spec.
    // For millis > roundfactor we may return a bit sooner, but
    // since we can not accurately identify the patch level and
    // this has already been fixed in Solaris 9 and 8 we will
    // leave it alone rather than always rounding down.

    if (millis > 0 && millis < ROUNDINGFIX) millis = ROUNDINGFIX;
       // It appears that when we go directly through Solaris _lwp_cond_timedwait()
           // the acceptable max time threshold is smaller than for libthread on 2.5.1 and 2.6
           max_wait_period = 21000000;
  } else {
    max_wait_period = 50000000;
  }
  millis %= 1000;
  if (seconds > max_wait_period) {      // see man cond_timedwait(3T)
     seconds = max_wait_period;
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

void os::PlatformEvent::park() {           // AKA: down()
  // Invariant: Only the thread associated with the Event/PlatformEvent
  // may call park().
  int v ;
  for (;;) {
      v = _Event ;
      if (Atomic::cmpxchg (v-1, &_Event, v) == v) break ;
  }
  guarantee (v >= 0, "invariant") ;
  if (v == 0) {
     // Do this the hard way by blocking ...
     // See http://monaco.sfbay/detail.jsf?cr=5094058.
     // TODO-FIXME: for Solaris SPARC set fprs.FEF=0 prior to parking.
     // Only for SPARC >= V8PlusA
#if defined(__sparc) && defined(COMPILER2)
     if (ClearFPUAtPark) { _mark_fpu_nosave() ; }
#endif
     int status = os::Solaris::mutex_lock(_mutex);
     assert_status(status == 0, status,  "mutex_lock");
     guarantee (_nParked == 0, "invariant") ;
     ++ _nParked ;
     while (_Event < 0) {
        // for some reason, under 2.7 lwp_cond_wait() may return ETIME ...
        // Treat this the same as if the wait was interrupted
        // With usr/lib/lwp going to kernel, always handle ETIME
        status = os::Solaris::cond_wait(_cond, _mutex);
        if (status == ETIME) status = EINTR ;
        assert_status(status == 0 || status == EINTR, status, "cond_wait");
     }
     -- _nParked ;
     _Event = 0 ;
     status = os::Solaris::mutex_unlock(_mutex);
     assert_status(status == 0, status, "mutex_unlock");
  }
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

  int ret = OS_TIMEOUT;
  timestruc_t abst;
  compute_abstime (&abst, millis);

  // See http://monaco.sfbay/detail.jsf?cr=5094058.
  // For Solaris SPARC set fprs.FEF=0 prior to parking.
  // Only for SPARC >= V8PlusA
#if defined(__sparc) && defined(COMPILER2)
 if (ClearFPUAtPark) { _mark_fpu_nosave() ; }
#endif
  int status = os::Solaris::mutex_lock(_mutex);
  assert_status(status == 0, status, "mutex_lock");
  guarantee (_nParked == 0, "invariant") ;
  ++ _nParked ;
  while (_Event < 0) {
     int status = os::Solaris::cond_timedwait(_cond, _mutex, &abst);
     assert_status(status == 0 || status == EINTR ||
                   status == ETIME || status == ETIMEDOUT,
                   status, "cond_timedwait");
     if (!FilterSpuriousWakeups) break ;                // previous semantics
     if (status == ETIME || status == ETIMEDOUT) break ;
     // We consume and ignore EINTR and spurious wakeups.
  }
  -- _nParked ;
  if (_Event >= 0) ret = OS_OK ;
  _Event = 0 ;
  status = os::Solaris::mutex_unlock(_mutex);
  assert_status(status == 0, status, "mutex_unlock");
  return ret;
}

void os::PlatformEvent::unpark() {
  int v, AnyWaiters;

  // Increment _Event.
  // Another acceptable implementation would be to simply swap 1
  // into _Event:
  //   if (Swap (&_Event, 1) < 0) {
  //      mutex_lock (_mutex) ; AnyWaiters = nParked; mutex_unlock (_mutex) ;
  //      if (AnyWaiters) cond_signal (_cond) ;
  //   }

  for (;;) {
    v = _Event ;
    if (v > 0) {
       // The LD of _Event could have reordered or be satisfied
       // by a read-aside from this processor's write buffer.
       // To avoid problems execute a barrier and then
       // ratify the value.  A degenerate CAS() would also work.
       // Viz., CAS (v+0, &_Event, v) == v).
       OrderAccess::fence() ;
       if (_Event == v) return ;
       continue ;
    }
    if (Atomic::cmpxchg (v+1, &_Event, v) == v) break ;
  }

  // If the thread associated with the event was parked, wake it.
  if (v < 0) {
     int status ;
     // Wait for the thread assoc with the PlatformEvent to vacate.
     status = os::Solaris::mutex_lock(_mutex);
     assert_status(status == 0, status, "mutex_lock");
     AnyWaiters = _nParked ;
     status = os::Solaris::mutex_unlock(_mutex);
     assert_status(status == 0, status, "mutex_unlock");
     guarantee (AnyWaiters == 0 || AnyWaiters == 1, "invariant") ;
     if (AnyWaiters != 0) {
       // We intentional signal *after* dropping the lock
       // to avoid a common class of futile wakeups.
       status = os::Solaris::cond_signal(_cond);
       assert_status(status == 0, status, "cond_signal");
     }
  }
}

// JSR166
// -------------------------------------------------------

/*
 * The solaris and linux implementations of park/unpark are fairly
 * conservative for now, but can be improved. They currently use a
 * mutex/condvar pair, plus _counter.
 * Park decrements _counter if > 0, else does a condvar wait.  Unpark
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

  // Optional fast-exit: Check interrupt before trying to wait
  Thread* thread = Thread::current();
  assert(thread->is_Java_thread(), "Must be JavaThread");
  JavaThread *jt = (JavaThread *)thread;
  if (Thread::is_interrupted(thread, false)) {
    return;
  }

  // First, demultiplex/decode time arguments
  timespec absTime;
  if (time < 0 || (isAbsolute && time == 0) ) { // don't wait at all
    return;
  }
  if (time > 0) {
    // Warning: this code might be exposed to the old Solaris time
    // round-down bugs.  Grep "roundingFix" for details.
    unpackTime(&absTime, isAbsolute, time);
  }

  // Enter safepoint region
  // Beware of deadlocks such as 6317397.
  // The per-thread Parker:: _mutex is a classic leaf-lock.
  // In particular a thread must never block on the Threads_lock while
  // holding the Parker:: mutex.  If safepoints are pending both the
  // the ThreadBlockInVM() CTOR and DTOR may grab Threads_lock.
  ThreadBlockInVM tbivm(jt);

  // Don't wait if cannot get lock since interference arises from
  // unblocking.  Also. check interrupt before trying wait
  if (Thread::is_interrupted(thread, false) ||
      os::Solaris::mutex_trylock(_mutex) != 0) {
    return;
  }

  int status ;

  if (_counter > 0)  { // no wait needed
    _counter = 0;
    status = os::Solaris::mutex_unlock(_mutex);
    assert (status == 0, "invariant") ;
    OrderAccess::fence();
    return;
  }

#ifdef ASSERT
  // Don't catch signals while blocked; let the running threads have the signals.
  // (This allows a debugger to break into the running thread.)
  sigset_t oldsigs;
  sigset_t* allowdebug_blocked = os::Solaris::allowdebug_blocked_signals();
  thr_sigsetmask(SIG_BLOCK, allowdebug_blocked, &oldsigs);
#endif

  OSThreadWaitState osts(thread->osthread(), false /* not Object.wait() */);
  jt->set_suspend_equivalent();
  // cleared by handle_special_suspend_equivalent_condition() or java_suspend_self()

  // Do this the hard way by blocking ...
  // See http://monaco.sfbay/detail.jsf?cr=5094058.
  // TODO-FIXME: for Solaris SPARC set fprs.FEF=0 prior to parking.
  // Only for SPARC >= V8PlusA
#if defined(__sparc) && defined(COMPILER2)
  if (ClearFPUAtPark) { _mark_fpu_nosave() ; }
#endif

  if (time == 0) {
    status = os::Solaris::cond_wait (_cond, _mutex) ;
  } else {
    status = os::Solaris::cond_timedwait (_cond, _mutex, &absTime);
  }
  // Note that an untimed cond_wait() can sometimes return ETIME on older
  // versions of the Solaris.
  assert_status(status == 0 || status == EINTR ||
                status == ETIME || status == ETIMEDOUT,
                status, "cond_timedwait");

#ifdef ASSERT
  thr_sigsetmask(SIG_SETMASK, &oldsigs, NULL);
#endif
  _counter = 0 ;
  status = os::Solaris::mutex_unlock(_mutex);
  assert_status(status == 0, status, "mutex_unlock") ;

  // If externally suspended while waiting, re-suspend
  if (jt->handle_special_suspend_equivalent_condition()) {
    jt->java_suspend_self();
  }
  OrderAccess::fence();
}

void Parker::unpark() {
  int s, status ;
  status = os::Solaris::mutex_lock (_mutex) ;
  assert (status == 0, "invariant") ;
  s = _counter;
  _counter = 1;
  status = os::Solaris::mutex_unlock (_mutex) ;
  assert (status == 0, "invariant") ;

  if (s < 1) {
    status = os::Solaris::cond_signal (_cond) ;
    assert (status == 0, "invariant") ;
  }
}

extern char** environ;

// Run the specified command in a separate process. Return its exit value,
// or -1 on failure (e.g. can't fork a new process).
// Unlike system(), this function can be called from signal handler. It
// doesn't block SIGINT et al.
int os::fork_and_exec(char* cmd) {
  char * argv[4];
  argv[0] = (char *)"sh";
  argv[1] = (char *)"-c";
  argv[2] = cmd;
  argv[3] = NULL;

  // fork is async-safe, fork1 is not so can't use in signal handler
  pid_t pid;
  Thread* t = ThreadLocalStorage::get_thread_slow();
  if (t != NULL && t->is_inside_signal_handler()) {
    pid = fork();
  } else {
    pid = fork1();
  }

  if (pid < 0) {
    // fork failed
    warning("fork failed: %s", strerror(errno));
    return -1;

  } else if (pid == 0) {
    // child process

    // try to be consistent with system(), which uses "/usr/bin/sh" on Solaris
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
}

// is_headless_jre()
//
// Test for the existence of libmawt in motif21 or xawt directories
// in order to report if we are running in a headless jre
//
bool os::is_headless_jre() {
    struct stat statbuf;
    char buf[MAXPATHLEN];
    char libmawtpath[MAXPATHLEN];
    const char *xawtstr  = "/xawt/libmawt.so";
    const char *motifstr = "/motif21/libmawt.so";
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

    // check motif21/libmawt.so
    strcpy(libmawtpath, buf);
    strcat(libmawtpath, motifstr);
    if (::stat(libmawtpath, &statbuf) == 0) return false;

    return true;
}



/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2013, 2015 SAP AG. All rights reserved.
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

#ifndef OS_AIX_VM_OS_AIX_HPP
#define OS_AIX_VM_OS_AIX_HPP

// Information about the protection of the page at address '0' on this os.
static bool zero_page_read_protected() { return false; }

// Class Aix defines the interface to the Aix operating systems.

class Aix {
  friend class os;

  // Length of strings included in the libperfstat structures.
#define IDENTIFIER_LENGTH 64

  static bool libjsig_is_loaded;        // libjsig that interposes sigaction(),
                                        // __sigaction(), signal() is loaded
  static struct sigaction *(*get_signal_action)(int);
  static struct sigaction *get_preinstalled_handler(int);
  static void save_preinstalled_handler(int, struct sigaction&);

  static void check_signal_handler(int sig);

 protected:

  static julong _physical_memory;
  static pthread_t _main_thread;
  static Mutex* _createThread_lock;
  static int _page_size;
  static int _logical_cpus;

  // -1 = uninitialized, 0 = AIX, 1 = OS/400 (PASE)
  static int _on_pase;

  // -1 = uninitialized, otherwise 16 bit number:
  //  lower 8 bit - minor version
  //  higher 8 bit - major version
  //  For AIX, e.g. 0x0601 for AIX 6.1
  //  for OS/400 e.g. 0x0504 for OS/400 V5R4
  static int _os_version;

  // -1 = uninitialized,
  //  0 - SPEC1170 not requested (XPG_SUS_ENV is OFF or not set)
  //  1 - SPEC1170 requested (XPG_SUS_ENV is ON)
  static int _xpg_sus_mode;

  // -1 = uninitialized,
  //  0 - EXTSHM=OFF or not set
  //  1 - EXTSHM=ON
  static int _extshm;

  // page sizes on AIX.
  //
  //  AIX supports four different page sizes - 4K, 64K, 16MB, 16GB. The latter two
  //  (16M "large" resp. 16G "huge" pages) require special setup and are normally
  //  not available.
  //
  //  AIX supports multiple page sizes per process, for:
  //  - Stack (of the primordial thread, so not relevant for us)
  //  - Data - data, bss, heap, for us also pthread stacks
  //  - Text - text code
  //  - shared memory
  //
  //  Default page sizes can be set via linker options (-bdatapsize, -bstacksize, ...)
  //  and via environment variable LDR_CNTRL (DATAPSIZE, STACKPSIZE, ...)
  //
  //  For shared memory, page size can be set dynamically via shmctl(). Different shared memory
  //  regions can have different page sizes.
  //
  //  More information can be found at AIBM info center:
  //   http://publib.boulder.ibm.com/infocenter/aix/v6r1/index.jsp?topic=/com.ibm.aix.prftungd/doc/prftungd/multiple_page_size_app_support.htm
  //
  // -----
  //  We want to support 4K and 64K and, if the machine is set up correctly, 16MB pages.
  //

  // page size of the stack of newly created pthreads
  // (should be LDR_CNTRL DATAPSIZE because stack is allocated on heap by pthread lib)
  static int _stack_page_size;

  static julong available_memory();
  static julong physical_memory() { return _physical_memory; }
  static void initialize_system_info();

  // OS recognitions (PASE/AIX, OS level) call this before calling any
  // one of Aix::on_pase(), Aix::os_version().
  static void initialize_os_info();

  // Scan environment for important settings which might effect the
  // VM. Trace out settings. Warn about invalid settings and/or
  // correct them.
  //
  // Must run after os::Aix::initialue_os_info().
  static void scan_environment();

  // Initialize libo4 (on PASE) and libperfstat (on AIX). Call this
  // before relying on functions from either lib, e.g. Aix::get_meminfo().
  static void initialize_libo4();
  static void initialize_libperfstat();

 public:
  static void init_thread_fpu_state();
  static pthread_t main_thread(void)                                { return _main_thread; }
  // returns kernel thread id (similar to LWP id on Solaris), which can be
  // used to access /proc
  static pid_t gettid();
  static void set_createThread_lock(Mutex* lk)                      { _createThread_lock = lk; }
  static Mutex* createThread_lock(void)                             { return _createThread_lock; }
  static void hotspot_sigmask(Thread* thread);

  // Given an address, returns the size of the page backing that address
  static size_t query_pagesize(void* p);

  // Return `true' if the calling thread is the primordial thread. The
  // primordial thread is the thread which contains the main function,
  // *not* necessarily the thread which initialized the VM by calling
  // JNI_CreateJavaVM.
  static bool is_primordial_thread(void);

  static int page_size(void) {
    assert(_page_size != -1, "not initialized");
    return _page_size;
  }

  // Accessor methods for stack page size which may be different from usual page size.
  static int stack_page_size(void) {
    assert(_stack_page_size != -1, "not initialized");
    return _stack_page_size;
  }

  // This is used to scale stack space (guard pages etc.). The name is somehow misleading.
  static int vm_default_page_size(void ) { return 8*K; }

  static address   ucontext_get_pc(const ucontext_t* uc);
  static intptr_t* ucontext_get_sp(ucontext_t* uc);
  static intptr_t* ucontext_get_fp(ucontext_t* uc);
  // Set PC into context. Needed for continuation after signal.
  static void ucontext_set_pc(ucontext_t* uc, address pc);

  // This boolean allows users to forward their own non-matching signals
  // to JVM_handle_aix_signal, harmlessly.
  static bool signal_handlers_are_installed;

  static int get_our_sigflags(int);
  static void set_our_sigflags(int, int);
  static void signal_sets_init();
  static void install_signal_handlers();
  static void set_signal_handler(int, bool);
  static bool is_sig_ignored(int sig);

  static sigset_t* unblocked_signals();
  static sigset_t* vm_signals();
  static sigset_t* allowdebug_blocked_signals();

  // For signal-chaining
  static struct sigaction *get_chained_signal_action(int sig);
  static bool chained_handler(int sig, siginfo_t* siginfo, void* context);

  // libpthread version string
  static void libpthread_init();

  // Minimum stack size a thread can be created with (allowing
  // the VM to completely create the thread and enter user code)
  static size_t min_stack_allowed;

  // Return default stack size or guard size for the specified thread type
  static size_t default_stack_size(os::ThreadType thr_type);
  static size_t default_guard_size(os::ThreadType thr_type);

  // Function returns true if we run on OS/400 (pase), false if we run
  // on AIX.
  static bool on_pase() {
    assert(_on_pase != -1, "not initialized");
    return _on_pase ? true : false;
  }

  // Function returns true if we run on AIX, false if we run on OS/400
  // (pase).
  static bool on_aix() {
    assert(_on_pase != -1, "not initialized");
    return _on_pase ? false : true;
  }

  // -1 = uninitialized, otherwise 16 bit number:
  // lower 8 bit - minor version
  // higher 8 bit - major version
  // For AIX, e.g. 0x0601 for AIX 6.1
  // for OS/400 e.g. 0x0504 for OS/400 V5R4
  static int os_version () {
    assert(_os_version != -1, "not initialized");
    return _os_version;
  }

  // Convenience method: returns true if running on PASE V5R4 or older.
  static bool on_pase_V5R4_or_older() {
    return on_pase() && os_version() <= 0x0504;
  }

  // Convenience method: returns true if running on AIX 5.3 or older.
  static bool on_aix_53_or_older() {
    return on_aix() && os_version() <= 0x0503;
  }

  // Returns true if we run in SPEC1170 compliant mode (XPG_SUS_ENV=ON).
  static bool xpg_sus_mode() {
    assert(_xpg_sus_mode != -1, "not initialized");
    return _xpg_sus_mode;
  }

  // Returns true if EXTSHM=ON.
  static bool extshm() {
    assert(_extshm != -1, "not initialized");
    return _extshm;
  }

  // result struct for get_meminfo()
  struct meminfo_t {

    // Amount of virtual memory (in units of 4 KB pages)
    unsigned long long virt_total;

    // Amount of real memory, in bytes
    unsigned long long real_total;

    // Amount of free real memory, in bytes
    unsigned long long real_free;

    // Total amount of paging space, in bytes
    unsigned long long pgsp_total;

    // Amount of free paging space, in bytes
    unsigned long long pgsp_free;

  };

  // Result struct for get_cpuinfo().
  struct cpuinfo_t {
    char description[IDENTIFIER_LENGTH];  // processor description (type/official name)
    u_longlong_t processorHZ;             // processor speed in Hz
    int ncpus;                            // number of active logical processors
    double loadavg[3];                    // (1<<SBITS) times the average number of runnables processes during the last 1, 5 and 15 minutes.
                                          // To calculate the load average, divide the numbers by (1<<SBITS). SBITS is defined in <sys/proc.h>.
    char version[20];                     // processor version from _system_configuration (sys/systemcfg.h)
  };

  // Functions to retrieve memory information on AIX, PASE.
  // (on AIX, using libperfstat, on PASE with libo4.so).
  // Returns true if ok, false if error.
  static bool get_meminfo(meminfo_t* pmi);

  // Function to retrieve cpu information on AIX
  // (on AIX, using libperfstat)
  // Returns true if ok, false if error.
  static bool get_cpuinfo(cpuinfo_t* pci);

}; // os::Aix class


class PlatformEvent : public CHeapObj<mtInternal> {
  private:
    double CachePad [4];   // increase odds that _mutex is sole occupant of cache line
    volatile int _Event;
    volatile int _nParked;
    pthread_mutex_t _mutex  [1];
    pthread_cond_t  _cond   [1];
    double PostPad  [2];
    Thread * _Assoc;

  public:       // TODO-FIXME: make dtor private
    ~PlatformEvent() { guarantee (0, "invariant"); }

  public:
    PlatformEvent() {
      int status;
      status = pthread_cond_init (_cond, NULL);
      assert_status(status == 0, status, "cond_init");
      status = pthread_mutex_init (_mutex, NULL);
      assert_status(status == 0, status, "mutex_init");
      _Event   = 0;
      _nParked = 0;
      _Assoc   = NULL;
    }

    // Use caution with reset() and fired() -- they may require MEMBARs
    void reset() { _Event = 0; }
    int  fired() { return _Event; }
    void park ();
    void unpark ();
    int  TryPark ();
    int  park (jlong millis);
    void SetAssociation (Thread * a) { _Assoc = a; }
};

class PlatformParker : public CHeapObj<mtInternal> {
  protected:
    pthread_mutex_t _mutex [1];
    pthread_cond_t  _cond  [1];

  public:       // TODO-FIXME: make dtor private
    ~PlatformParker() { guarantee (0, "invariant"); }

  public:
    PlatformParker() {
      int status;
      status = pthread_cond_init (_cond, NULL);
      assert_status(status == 0, status, "cond_init");
      status = pthread_mutex_init (_mutex, NULL);
      assert_status(status == 0, status, "mutex_init");
    }
};

#endif // OS_AIX_VM_OS_AIX_HPP

/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_LINUX_VM_OS_LINUX_HPP
#define OS_LINUX_VM_OS_LINUX_HPP

// Linux_OS defines the interface to Linux operating systems

// Information about the protection of the page at address '0' on this os.
static bool zero_page_read_protected() { return true; }

class Linux {
  friend class os;
  friend class TestReserveMemorySpecial;

  static bool libjsig_is_loaded;        // libjsig that interposes sigaction(),
                                        // __sigaction(), signal() is loaded
  static struct sigaction *(*get_signal_action)(int);
  static struct sigaction *get_preinstalled_handler(int);
  static void save_preinstalled_handler(int, struct sigaction&);

  static void check_signal_handler(int sig);

  static int (*_clock_gettime)(clockid_t, struct timespec *);
  static int (*_pthread_getcpuclockid)(pthread_t, clockid_t *);
  static int (*_pthread_setname_np)(pthread_t, const char*);

  static address   _initial_thread_stack_bottom;
  static uintptr_t _initial_thread_stack_size;

  static const char *_glibc_version;
  static const char *_libpthread_version;

  static bool _supports_fast_thread_cpu_time;

  static GrowableArray<int>* _cpu_to_node;

 protected:

  static julong _physical_memory;
  static pthread_t _main_thread;
  static Mutex* _createThread_lock;
  static int _page_size;
  static const int _vm_default_page_size;

  static julong available_memory();
  static julong physical_memory() { return _physical_memory; }
  static void initialize_system_info();

  static int commit_memory_impl(char* addr, size_t bytes, bool exec);
  static int commit_memory_impl(char* addr, size_t bytes,
                                size_t alignment_hint, bool exec);

  static void set_glibc_version(const char *s)      { _glibc_version = s; }
  static void set_libpthread_version(const char *s) { _libpthread_version = s; }

  static void rebuild_cpu_to_node_map();
  static GrowableArray<int>* cpu_to_node()    { return _cpu_to_node; }

  static size_t find_large_page_size();
  static size_t setup_large_page_size();

  static bool setup_large_page_type(size_t page_size);
  static bool transparent_huge_pages_sanity_check(bool warn, size_t pages_size);
  static bool hugetlbfs_sanity_check(bool warn, size_t page_size);

  static char* reserve_memory_special_shm(size_t bytes, size_t alignment, char* req_addr, bool exec);
  static char* reserve_memory_special_huge_tlbfs(size_t bytes, size_t alignment, char* req_addr, bool exec);
  static char* reserve_memory_special_huge_tlbfs_only(size_t bytes, char* req_addr, bool exec);
  static char* reserve_memory_special_huge_tlbfs_mixed(size_t bytes, size_t alignment, char* req_addr, bool exec);

  static bool release_memory_special_impl(char* base, size_t bytes);
  static bool release_memory_special_shm(char* base, size_t bytes);
  static bool release_memory_special_huge_tlbfs(char* base, size_t bytes);

  static void print_full_memory_info(outputStream* st);
  static void print_distro_info(outputStream* st);
  static void print_libversion_info(outputStream* st);

 public:
  static bool _stack_is_executable;
  static void *dlopen_helper(const char *name, char *ebuf, int ebuflen);
  static void *dll_load_in_vmthread(const char *name, char *ebuf, int ebuflen);

  static void init_thread_fpu_state();
  static int  get_fpu_control_word();
  static void set_fpu_control_word(int fpu_control);
  static pthread_t main_thread(void)                                { return _main_thread; }
  // returns kernel thread id (similar to LWP id on Solaris), which can be
  // used to access /proc
  static pid_t gettid();
  static void set_createThread_lock(Mutex* lk)                      { _createThread_lock = lk; }
  static Mutex* createThread_lock(void)                             { return _createThread_lock; }
  static void hotspot_sigmask(Thread* thread);

  static address   initial_thread_stack_bottom(void)                { return _initial_thread_stack_bottom; }
  static uintptr_t initial_thread_stack_size(void)                  { return _initial_thread_stack_size; }
  static bool is_initial_thread(void);

  static int page_size(void)                                        { return _page_size; }
  static void set_page_size(int val)                                { _page_size = val; }

  static int vm_default_page_size(void)                             { return _vm_default_page_size; }

  static address   ucontext_get_pc(ucontext_t* uc);
  static void ucontext_set_pc(ucontext_t* uc, address pc);
  static intptr_t* ucontext_get_sp(ucontext_t* uc);
  static intptr_t* ucontext_get_fp(ucontext_t* uc);

  // For Analyzer Forte AsyncGetCallTrace profiling support:
  //
  // This interface should be declared in os_linux_i486.hpp, but
  // that file provides extensions to the os class and not the
  // Linux class.
  static ExtendedPC fetch_frame_from_ucontext(Thread* thread, ucontext_t* uc,
                                              intptr_t** ret_sp, intptr_t** ret_fp);

  static bool get_frame_at_stack_banging_point(JavaThread* thread, ucontext_t* uc, frame* fr);

  // This boolean allows users to forward their own non-matching signals
  // to JVM_handle_linux_signal, harmlessly.
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

  // GNU libc and libpthread version strings
  static const char *glibc_version()          { return _glibc_version; }
  static const char *libpthread_version()     { return _libpthread_version; }

  static void libpthread_init();
  static bool libnuma_init();
  static void* libnuma_dlsym(void* handle, const char* name);
  // Minimum stack size a thread can be created with (allowing
  // the VM to completely create the thread and enter user code)
  static size_t min_stack_allowed;

  // Return default stack size or guard size for the specified thread type
  static size_t default_stack_size(os::ThreadType thr_type);
  static size_t default_guard_size(os::ThreadType thr_type);

  static void capture_initial_stack(size_t max_size);

  // Stack overflow handling
  static bool manually_expand_stack(JavaThread * t, address addr);
  static int max_register_window_saves_before_flushing();

  // Real-time clock functions
  static void clock_init(void);

  // fast POSIX clocks support
  static void fast_thread_clock_init(void);

  static int clock_gettime(clockid_t clock_id, struct timespec *tp) {
    return _clock_gettime ? _clock_gettime(clock_id, tp) : -1;
  }

  static int pthread_getcpuclockid(pthread_t tid, clockid_t *clock_id) {
    return _pthread_getcpuclockid ? _pthread_getcpuclockid(tid, clock_id) : -1;
  }

  static bool supports_fast_thread_cpu_time() {
    return _supports_fast_thread_cpu_time;
  }

  static jlong fast_thread_cpu_time(clockid_t clockid);

  // pthread_cond clock suppport
 private:
  static pthread_condattr_t _condattr[1];

 public:
  static pthread_condattr_t* condAttr() { return _condattr; }

  // Stack repair handling

  // none present

 private:
  typedef int (*sched_getcpu_func_t)(void);
  typedef int (*numa_node_to_cpus_func_t)(int node, unsigned long *buffer, int bufferlen);
  typedef int (*numa_max_node_func_t)(void);
  typedef int (*numa_available_func_t)(void);
  typedef int (*numa_tonode_memory_func_t)(void *start, size_t size, int node);
  typedef void (*numa_interleave_memory_func_t)(void *start, size_t size, unsigned long *nodemask);
  typedef void (*numa_set_bind_policy_func_t)(int policy);

  static sched_getcpu_func_t _sched_getcpu;
  static numa_node_to_cpus_func_t _numa_node_to_cpus;
  static numa_max_node_func_t _numa_max_node;
  static numa_available_func_t _numa_available;
  static numa_tonode_memory_func_t _numa_tonode_memory;
  static numa_interleave_memory_func_t _numa_interleave_memory;
  static numa_set_bind_policy_func_t _numa_set_bind_policy;
  static unsigned long* _numa_all_nodes;

  static void set_sched_getcpu(sched_getcpu_func_t func) { _sched_getcpu = func; }
  static void set_numa_node_to_cpus(numa_node_to_cpus_func_t func) { _numa_node_to_cpus = func; }
  static void set_numa_max_node(numa_max_node_func_t func) { _numa_max_node = func; }
  static void set_numa_available(numa_available_func_t func) { _numa_available = func; }
  static void set_numa_tonode_memory(numa_tonode_memory_func_t func) { _numa_tonode_memory = func; }
  static void set_numa_interleave_memory(numa_interleave_memory_func_t func) { _numa_interleave_memory = func; }
  static void set_numa_set_bind_policy(numa_set_bind_policy_func_t func) { _numa_set_bind_policy = func; }
  static void set_numa_all_nodes(unsigned long* ptr) { _numa_all_nodes = ptr; }
  static int sched_getcpu_syscall(void);
 public:
  static int sched_getcpu()  { return _sched_getcpu != NULL ? _sched_getcpu() : -1; }
  static int numa_node_to_cpus(int node, unsigned long *buffer, int bufferlen) {
    return _numa_node_to_cpus != NULL ? _numa_node_to_cpus(node, buffer, bufferlen) : -1;
  }
  static int numa_max_node() { return _numa_max_node != NULL ? _numa_max_node() : -1; }
  static int numa_available() { return _numa_available != NULL ? _numa_available() : -1; }
  static int numa_tonode_memory(void *start, size_t size, int node) {
    return _numa_tonode_memory != NULL ? _numa_tonode_memory(start, size, node) : -1;
  }
  static void numa_interleave_memory(void *start, size_t size) {
    if (_numa_interleave_memory != NULL && _numa_all_nodes != NULL) {
      _numa_interleave_memory(start, size, _numa_all_nodes);
    }
  }
  static void numa_set_bind_policy(int policy) {
    if (_numa_set_bind_policy != NULL) {
      _numa_set_bind_policy(policy);
    }
  }
  static int get_node_by_cpu(int cpu_id);
};


class PlatformEvent : public CHeapObj<mtInternal> {
 private:
  double CachePad[4];   // increase odds that _mutex is sole occupant of cache line
  volatile int _Event;
  volatile int _nParked;
  pthread_mutex_t _mutex[1];
  pthread_cond_t  _cond[1];
  double PostPad[2];
  Thread * _Assoc;

 public:       // TODO-FIXME: make dtor private
  ~PlatformEvent() { guarantee(0, "invariant"); }

 public:
  PlatformEvent() {
    int status;
    status = pthread_cond_init(_cond, os::Linux::condAttr());
    assert_status(status == 0, status, "cond_init");
    status = pthread_mutex_init(_mutex, NULL);
    assert_status(status == 0, status, "mutex_init");
    _Event   = 0;
    _nParked = 0;
    _Assoc   = NULL;
  }

  // Use caution with reset() and fired() -- they may require MEMBARs
  void reset() { _Event = 0; }
  int  fired() { return _Event; }
  void park();
  void unpark();
  int  park(jlong millis); // relative timed-wait only
  void SetAssociation(Thread * a) { _Assoc = a; }
};

class PlatformParker : public CHeapObj<mtInternal> {
 protected:
  enum {
    REL_INDEX = 0,
    ABS_INDEX = 1
  };
  int _cur_index;  // which cond is in use: -1, 0, 1
  pthread_mutex_t _mutex[1];
  pthread_cond_t  _cond[2]; // one for relative times and one for abs.

 public:       // TODO-FIXME: make dtor private
  ~PlatformParker() { guarantee(0, "invariant"); }

 public:
  PlatformParker() {
    int status;
    status = pthread_cond_init(&_cond[REL_INDEX], os::Linux::condAttr());
    assert_status(status == 0, status, "cond_init rel");
    status = pthread_cond_init(&_cond[ABS_INDEX], NULL);
    assert_status(status == 0, status, "cond_init abs");
    status = pthread_mutex_init(_mutex, NULL);
    assert_status(status == 0, status, "mutex_init");
    _cur_index = -1; // mark as unused
  }
};

#endif // OS_LINUX_VM_OS_LINUX_HPP

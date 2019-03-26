/*
 * Copyright (c) 1999, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_LINUX_OS_LINUX_HPP
#define OS_LINUX_OS_LINUX_HPP

// Linux_OS defines the interface to Linux operating systems

// Information about the protection of the page at address '0' on this os.
static bool zero_page_read_protected() { return true; }

class Linux {
  friend class os;
  friend class OSContainer;
  friend class TestReserveMemorySpecial;

  static bool libjsig_is_loaded;        // libjsig that interposes sigaction(),
                                        // __sigaction(), signal() is loaded
  static struct sigaction *(*get_signal_action)(int);

  static void check_signal_handler(int sig);

  static int (*_pthread_getcpuclockid)(pthread_t, clockid_t *);
  static int (*_pthread_setname_np)(pthread_t, const char*);

  static address   _initial_thread_stack_bottom;
  static uintptr_t _initial_thread_stack_size;

  static const char *_glibc_version;
  static const char *_libpthread_version;

  static bool _supports_fast_thread_cpu_time;

  static GrowableArray<int>* _cpu_to_node;
  static GrowableArray<int>* _nindex_to_node;

  // 0x00000000 = uninitialized,
  // 0x01000000 = kernel version unknown,
  // otherwise a 32-bit number:
  // Ox00AABBCC
  // AA, Major Version
  // BB, Minor Version
  // CC, Fix   Version
  static uint32_t _os_version;

 protected:

  static julong _physical_memory;
  static pthread_t _main_thread;
  static Mutex* _createThread_lock;
  static int _page_size;

  static julong available_memory();
  static julong physical_memory() { return _physical_memory; }
  static void set_physical_memory(julong phys_mem) { _physical_memory = phys_mem; }
  static int active_processor_count();

  static void initialize_system_info();

  static int commit_memory_impl(char* addr, size_t bytes, bool exec);
  static int commit_memory_impl(char* addr, size_t bytes,
                                size_t alignment_hint, bool exec);

  static void set_glibc_version(const char *s)      { _glibc_version = s; }
  static void set_libpthread_version(const char *s) { _libpthread_version = s; }

  static void rebuild_cpu_to_node_map();
  static void rebuild_nindex_to_node_map();
  static GrowableArray<int>* cpu_to_node()    { return _cpu_to_node; }
  static GrowableArray<int>* nindex_to_node()  { return _nindex_to_node; }

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
  static void print_container_info(outputStream* st);
  static void print_virtualization_info(outputStream* st);
  static void print_distro_info(outputStream* st);
  static void print_libversion_info(outputStream* st);
  static void print_proc_sys_info(outputStream* st);
  static void print_ld_preload_file(outputStream* st);

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

  static int page_size(void)                                        { return _page_size; }
  static void set_page_size(int val)                                { _page_size = val; }

  static address   ucontext_get_pc(const ucontext_t* uc);
  static void ucontext_set_pc(ucontext_t* uc, address pc);
  static intptr_t* ucontext_get_sp(const ucontext_t* uc);
  static intptr_t* ucontext_get_fp(const ucontext_t* uc);

  // For Analyzer Forte AsyncGetCallTrace profiling support:
  //
  // This interface should be declared in os_linux_i486.hpp, but
  // that file provides extensions to the os class and not the
  // Linux class.
  static ExtendedPC fetch_frame_from_ucontext(Thread* thread, const ucontext_t* uc,
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

  static sigset_t* unblocked_signals();
  static sigset_t* vm_signals();

  // For signal-chaining
  static struct sigaction *get_chained_signal_action(int sig);
  static bool chained_handler(int sig, siginfo_t* siginfo, void* context);

  // GNU libc and libpthread version strings
  static const char *glibc_version()          { return _glibc_version; }
  static const char *libpthread_version()     { return _libpthread_version; }

  static void libpthread_init();
  static void sched_getcpu_init();
  static bool libnuma_init();
  static void* libnuma_dlsym(void* handle, const char* name);
  // libnuma v2 (libnuma_1.2) symbols
  static void* libnuma_v2_dlsym(void* handle, const char* name);

  // Return default guard size for the specified thread type
  static size_t default_guard_size(os::ThreadType thr_type);

  static void capture_initial_stack(size_t max_size);

  // Stack overflow handling
  static bool manually_expand_stack(JavaThread * t, address addr);
  static int max_register_window_saves_before_flushing();

  // fast POSIX clocks support
  static void fast_thread_clock_init(void);

  static int pthread_getcpuclockid(pthread_t tid, clockid_t *clock_id) {
    return _pthread_getcpuclockid ? _pthread_getcpuclockid(tid, clock_id) : -1;
  }

  static bool supports_fast_thread_cpu_time() {
    return _supports_fast_thread_cpu_time;
  }

  static jlong fast_thread_cpu_time(clockid_t clockid);

  static void initialize_os_info();
  static bool os_version_is_known();
  static uint32_t os_version();

  // Stack repair handling

  // none present

 private:
  static void numa_init();
  static void expand_stack_to(address bottom);

  typedef int (*sched_getcpu_func_t)(void);
  typedef int (*numa_node_to_cpus_func_t)(int node, unsigned long *buffer, int bufferlen);
  typedef int (*numa_max_node_func_t)(void);
  typedef int (*numa_num_configured_nodes_func_t)(void);
  typedef int (*numa_available_func_t)(void);
  typedef int (*numa_tonode_memory_func_t)(void *start, size_t size, int node);
  typedef void (*numa_interleave_memory_func_t)(void *start, size_t size, unsigned long *nodemask);
  typedef void (*numa_interleave_memory_v2_func_t)(void *start, size_t size, struct bitmask* mask);
  typedef struct bitmask* (*numa_get_membind_func_t)(void);
  typedef struct bitmask* (*numa_get_interleave_mask_func_t)(void);

  typedef void (*numa_set_bind_policy_func_t)(int policy);
  typedef int (*numa_bitmask_isbitset_func_t)(struct bitmask *bmp, unsigned int n);
  typedef int (*numa_distance_func_t)(int node1, int node2);

  static sched_getcpu_func_t _sched_getcpu;
  static numa_node_to_cpus_func_t _numa_node_to_cpus;
  static numa_max_node_func_t _numa_max_node;
  static numa_num_configured_nodes_func_t _numa_num_configured_nodes;
  static numa_available_func_t _numa_available;
  static numa_tonode_memory_func_t _numa_tonode_memory;
  static numa_interleave_memory_func_t _numa_interleave_memory;
  static numa_interleave_memory_v2_func_t _numa_interleave_memory_v2;
  static numa_set_bind_policy_func_t _numa_set_bind_policy;
  static numa_bitmask_isbitset_func_t _numa_bitmask_isbitset;
  static numa_distance_func_t _numa_distance;
  static numa_get_membind_func_t _numa_get_membind;
  static numa_get_interleave_mask_func_t _numa_get_interleave_mask;
  static unsigned long* _numa_all_nodes;
  static struct bitmask* _numa_all_nodes_ptr;
  static struct bitmask* _numa_nodes_ptr;
  static struct bitmask* _numa_interleave_bitmask;
  static struct bitmask* _numa_membind_bitmask;

  static void set_sched_getcpu(sched_getcpu_func_t func) { _sched_getcpu = func; }
  static void set_numa_node_to_cpus(numa_node_to_cpus_func_t func) { _numa_node_to_cpus = func; }
  static void set_numa_max_node(numa_max_node_func_t func) { _numa_max_node = func; }
  static void set_numa_num_configured_nodes(numa_num_configured_nodes_func_t func) { _numa_num_configured_nodes = func; }
  static void set_numa_available(numa_available_func_t func) { _numa_available = func; }
  static void set_numa_tonode_memory(numa_tonode_memory_func_t func) { _numa_tonode_memory = func; }
  static void set_numa_interleave_memory(numa_interleave_memory_func_t func) { _numa_interleave_memory = func; }
  static void set_numa_interleave_memory_v2(numa_interleave_memory_v2_func_t func) { _numa_interleave_memory_v2 = func; }
  static void set_numa_set_bind_policy(numa_set_bind_policy_func_t func) { _numa_set_bind_policy = func; }
  static void set_numa_bitmask_isbitset(numa_bitmask_isbitset_func_t func) { _numa_bitmask_isbitset = func; }
  static void set_numa_distance(numa_distance_func_t func) { _numa_distance = func; }
  static void set_numa_get_membind(numa_get_membind_func_t func) { _numa_get_membind = func; }
  static void set_numa_get_interleave_mask(numa_get_interleave_mask_func_t func) { _numa_get_interleave_mask = func; }
  static void set_numa_all_nodes(unsigned long* ptr) { _numa_all_nodes = ptr; }
  static void set_numa_all_nodes_ptr(struct bitmask **ptr) { _numa_all_nodes_ptr = (ptr == NULL ? NULL : *ptr); }
  static void set_numa_nodes_ptr(struct bitmask **ptr) { _numa_nodes_ptr = (ptr == NULL ? NULL : *ptr); }
  static void set_numa_interleave_bitmask(struct bitmask* ptr)     { _numa_interleave_bitmask = ptr ;   }
  static void set_numa_membind_bitmask(struct bitmask* ptr)        { _numa_membind_bitmask = ptr ;      }
  static int sched_getcpu_syscall(void);

  enum NumaAllocationPolicy{
    NotInitialized,
    Membind,
    Interleave
  };
  static NumaAllocationPolicy _current_numa_policy;

 public:
  static int sched_getcpu()  { return _sched_getcpu != NULL ? _sched_getcpu() : -1; }
  static int numa_node_to_cpus(int node, unsigned long *buffer, int bufferlen) {
    return _numa_node_to_cpus != NULL ? _numa_node_to_cpus(node, buffer, bufferlen) : -1;
  }
  static int numa_max_node() { return _numa_max_node != NULL ? _numa_max_node() : -1; }
  static int numa_num_configured_nodes() {
    return _numa_num_configured_nodes != NULL ? _numa_num_configured_nodes() : -1;
  }
  static int numa_available() { return _numa_available != NULL ? _numa_available() : -1; }
  static int numa_tonode_memory(void *start, size_t size, int node) {
    return _numa_tonode_memory != NULL ? _numa_tonode_memory(start, size, node) : -1;
  }

  static bool is_running_in_interleave_mode() {
    return _current_numa_policy == Interleave;
  }

  static void set_configured_numa_policy(NumaAllocationPolicy numa_policy) {
    _current_numa_policy = numa_policy;
  }

  static NumaAllocationPolicy identify_numa_policy() {
    for (int node = 0; node <= Linux::numa_max_node(); node++) {
      if (Linux::_numa_bitmask_isbitset(Linux::_numa_interleave_bitmask, node)) {
        return Interleave;
      }
    }
    return Membind;
  }

  static void numa_interleave_memory(void *start, size_t size) {
    // Prefer v2 API
    if (_numa_interleave_memory_v2 != NULL) {
      if (is_running_in_interleave_mode()) {
        _numa_interleave_memory_v2(start, size, _numa_interleave_bitmask);
      } else if (_numa_membind_bitmask != NULL) {
        _numa_interleave_memory_v2(start, size, _numa_membind_bitmask);
      }
    } else if (_numa_interleave_memory != NULL) {
      _numa_interleave_memory(start, size, _numa_all_nodes);
    }
  }
  static void numa_set_bind_policy(int policy) {
    if (_numa_set_bind_policy != NULL) {
      _numa_set_bind_policy(policy);
    }
  }
  static int numa_distance(int node1, int node2) {
    return _numa_distance != NULL ? _numa_distance(node1, node2) : -1;
  }
  static int get_node_by_cpu(int cpu_id);
  static int get_existing_num_nodes();
  // Check if numa node is configured (non-zero memory node).
  static bool is_node_in_configured_nodes(unsigned int n) {
    if (_numa_bitmask_isbitset != NULL && _numa_all_nodes_ptr != NULL) {
      return _numa_bitmask_isbitset(_numa_all_nodes_ptr, n);
    } else
      return false;
  }
  // Check if numa node exists in the system (including zero memory nodes).
  static bool is_node_in_existing_nodes(unsigned int n) {
    if (_numa_bitmask_isbitset != NULL && _numa_nodes_ptr != NULL) {
      return _numa_bitmask_isbitset(_numa_nodes_ptr, n);
    } else if (_numa_bitmask_isbitset != NULL && _numa_all_nodes_ptr != NULL) {
      // Not all libnuma API v2 implement numa_nodes_ptr, so it's not possible
      // to trust the API version for checking its absence. On the other hand,
      // numa_nodes_ptr found in libnuma 2.0.9 and above is the only way to get
      // a complete view of all numa nodes in the system, hence numa_nodes_ptr
      // is used to handle CPU and nodes on architectures (like PowerPC) where
      // there can exist nodes with CPUs but no memory or vice-versa and the
      // nodes may be non-contiguous. For most of the architectures, like
      // x86_64, numa_node_ptr presents the same node set as found in
      // numa_all_nodes_ptr so it's possible to use numa_all_nodes_ptr as a
      // substitute.
      return _numa_bitmask_isbitset(_numa_all_nodes_ptr, n);
    } else
      return false;
  }
  // Check if node is in bound node set.
  static bool is_node_in_bound_nodes(int node) {
    if (_numa_bitmask_isbitset != NULL) {
      if (is_running_in_interleave_mode()) {
        return _numa_bitmask_isbitset(_numa_interleave_bitmask, node);
      } else {
        return _numa_membind_bitmask != NULL ? _numa_bitmask_isbitset(_numa_membind_bitmask, node) : false;
      }
    }
    return false;
  }
  // Check if bound to only one numa node.
  // Returns true if bound to a single numa node, otherwise returns false.
  static bool is_bound_to_single_node() {
    int nodes = 0;
    struct bitmask* bmp = NULL;
    unsigned int node = 0;
    unsigned int highest_node_number = 0;

    if (_numa_get_membind != NULL && _numa_max_node != NULL && _numa_bitmask_isbitset != NULL) {
      bmp = _numa_get_membind();
      highest_node_number = _numa_max_node();
    } else {
      return false;
    }

    for (node = 0; node <= highest_node_number; node++) {
      if (_numa_bitmask_isbitset(bmp, node)) {
        nodes++;
      }
    }

    if (nodes == 1) {
      return true;
    } else {
      return false;
    }
  }
};

#endif // OS_LINUX_OS_LINUX_HPP

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

// os defines the interface to operating system; this includes traditional
// OS services (time, I/O) as well as other functionality with system-
// dependent code.

typedef void (*dll_func)(...);

class Thread;
class JavaThread;
class Event;
class DLL;
class FileHandle;
template<class E> class GrowableArray;

// %%%%% Moved ThreadState, START_FN, OSThread to new osThread.hpp. -- Rose

// Platform-independent error return values from OS functions
enum OSReturn {
  OS_OK         =  0,        // Operation was successful
  OS_ERR        = -1,        // Operation failed
  OS_INTRPT     = -2,        // Operation was interrupted
  OS_TIMEOUT    = -3,        // Operation timed out
  OS_NOMEM      = -5,        // Operation failed for lack of memory
  OS_NORESOURCE = -6         // Operation failed for lack of nonmemory resource
};

enum ThreadPriority {        // JLS 20.20.1-3
  NoPriority       = -1,     // Initial non-priority value
  MinPriority      =  1,     // Minimum priority
  NormPriority     =  5,     // Normal (non-daemon) priority
  NearMaxPriority  =  9,     // High priority, used for VMThread
  MaxPriority      = 10      // Highest priority, used for WatcherThread
                             // ensures that VMThread doesn't starve profiler
};

// Typedef for structured exception handling support
typedef void (*java_call_t)(JavaValue* value, methodHandle* method, JavaCallArguments* args, Thread* thread);

class os: AllStatic {
 public:
  enum { page_sizes_max = 9 }; // Size of _page_sizes array (8 plus a sentinel)

 private:
  static OSThread*          _starting_thread;
  static address            _polling_page;
  static volatile int32_t * _mem_serialize_page;
  static uintptr_t          _serialize_page_mask;
 public:
  static size_t             _page_sizes[page_sizes_max];

 private:
  static void init_page_sizes(size_t default_page_size) {
    _page_sizes[0] = default_page_size;
    _page_sizes[1] = 0; // sentinel
  }

 public:

  static void init(void);                      // Called before command line parsing
  static jint init_2(void);                    // Called after command line parsing
  static void init_3(void);                    // Called at the end of vm init

  // File names are case-insensitive on windows only
  // Override me as needed
  static int    file_name_strcmp(const char* s1, const char* s2);

  static bool getenv(const char* name, char* buffer, int len);
  static bool have_special_privileges();

  static jlong  javaTimeMillis();
  static jlong  javaTimeNanos();
  static void   javaTimeNanos_info(jvmtiTimerInfo *info_ptr);
  static void   run_periodic_checks();


  // Returns the elapsed time in seconds since the vm started.
  static double elapsedTime();

  // Returns real time in seconds since an arbitrary point
  // in the past.
  static bool getTimesSecs(double* process_real_time,
                           double* process_user_time,
                           double* process_system_time);

  // Interface to the performance counter
  static jlong elapsed_counter();
  static jlong elapsed_frequency();

  // The "virtual time" of a thread is the amount of time a thread has
  // actually run.  The first function indicates whether the OS supports
  // this functionality for the current thread, and if so:
  //   * the second enables vtime tracking (if that is required).
  //   * the third tells whether vtime is enabled.
  //   * the fourth returns the elapsed virtual time for the current
  //     thread.
  static bool supports_vtime();
  static bool enable_vtime();
  static bool vtime_enabled();
  static double elapsedVTime();

  // Return current local time in a string (YYYY-MM-DD HH:MM:SS).
  // It is MT safe, but not async-safe, as reading time zone
  // information may require a lock on some platforms.
  static char*      local_time_string(char *buf, size_t buflen);
  static struct tm* localtime_pd     (const time_t* clock, struct tm*  res);
  // Fill in buffer with current local time as an ISO-8601 string.
  // E.g., YYYY-MM-DDThh:mm:ss.mmm+zzzz.
  // Returns buffer, or NULL if it failed.
  static char* iso8601_time(char* buffer, size_t buffer_length);

  // Interface for detecting multiprocessor system
  static inline bool is_MP() {
    assert(_processor_count > 0, "invalid processor count");
    return _processor_count > 1;
  }
  static julong available_memory();
  static julong physical_memory();
  static julong allocatable_physical_memory(julong size);
  static bool is_server_class_machine();

  // number of CPUs
  static int processor_count() {
    return _processor_count;
  }
  static void set_processor_count(int count) { _processor_count = count; }

  // Returns the number of CPUs this process is currently allowed to run on.
  // Note that on some OSes this can change dynamically.
  static int active_processor_count();

  // Bind processes to processors.
  //     This is a two step procedure:
  //     first you generate a distribution of processes to processors,
  //     then you bind processes according to that distribution.
  // Compute a distribution for number of processes to processors.
  //    Stores the processor id's into the distribution array argument.
  //    Returns true if it worked, false if it didn't.
  static bool distribute_processes(uint length, uint* distribution);
  // Binds the current process to a processor.
  //    Returns true if it worked, false if it didn't.
  static bool bind_to_processor(uint processor_id);

  // Interface for stack banging (predetect possible stack overflow for
  // exception processing)  There are guard pages, and above that shadow
  // pages for stack overflow checking.
  static bool uses_stack_guard_pages();
  static bool allocate_stack_guard_pages();
  static void bang_stack_shadow_pages();
  static bool stack_shadow_pages_available(Thread *thread, methodHandle method);

  // OS interface to Virtual Memory

  // Return the default page size.
  static int    vm_page_size();

  // Return the page size to use for a region of memory.  The min_pages argument
  // is a hint intended to limit fragmentation; it says the returned page size
  // should be <= region_max_size / min_pages.  Because min_pages is a hint,
  // this routine may return a size larger than region_max_size / min_pages.
  //
  // The current implementation ignores min_pages if a larger page size is an
  // exact multiple of both region_min_size and region_max_size.  This allows
  // larger pages to be used when doing so would not cause fragmentation; in
  // particular, a single page can be used when region_min_size ==
  // region_max_size == a supported page size.
  static size_t page_size_for_region(size_t region_min_size,
                                     size_t region_max_size,
                                     uint min_pages);

  // Method for tracing page sizes returned by the above method; enabled by
  // TracePageSizes.  The region_{min,max}_size parameters should be the values
  // passed to page_size_for_region() and page_size should be the result of that
  // call.  The (optional) base and size parameters should come from the
  // ReservedSpace base() and size() methods.
  static void trace_page_sizes(const char* str, const size_t region_min_size,
                               const size_t region_max_size,
                               const size_t page_size,
                               const char* base = NULL,
                               const size_t size = 0) PRODUCT_RETURN;

  static int    vm_allocation_granularity();
  static char*  reserve_memory(size_t bytes, char* addr = 0,
                               size_t alignment_hint = 0);
  static char*  attempt_reserve_memory_at(size_t bytes, char* addr);
  static void   split_reserved_memory(char *base, size_t size,
                                      size_t split, bool realloc);
  static bool   commit_memory(char* addr, size_t bytes,
                              bool executable = false);
  static bool   commit_memory(char* addr, size_t size, size_t alignment_hint,
                              bool executable = false);
  static bool   uncommit_memory(char* addr, size_t bytes);
  static bool   release_memory(char* addr, size_t bytes);

  enum ProtType { MEM_PROT_NONE, MEM_PROT_READ, MEM_PROT_RW, MEM_PROT_RWX };
  static bool   protect_memory(char* addr, size_t bytes, ProtType prot,
                               bool is_committed = true);

  static bool   guard_memory(char* addr, size_t bytes);
  static bool   unguard_memory(char* addr, size_t bytes);
  static bool   create_stack_guard_pages(char* addr, size_t bytes);
  static bool   remove_stack_guard_pages(char* addr, size_t bytes);

  static char*  map_memory(int fd, const char* file_name, size_t file_offset,
                           char *addr, size_t bytes, bool read_only = false,
                           bool allow_exec = false);
  static char*  remap_memory(int fd, const char* file_name, size_t file_offset,
                             char *addr, size_t bytes, bool read_only,
                             bool allow_exec);
  static bool   unmap_memory(char *addr, size_t bytes);
  static void   free_memory(char *addr, size_t bytes);
  static void   realign_memory(char *addr, size_t bytes, size_t alignment_hint);

  // NUMA-specific interface
  static bool   numa_has_static_binding();
  static bool   numa_has_group_homing();
  static void   numa_make_local(char *addr, size_t bytes, int lgrp_hint);
  static void   numa_make_global(char *addr, size_t bytes);
  static size_t numa_get_groups_num();
  static size_t numa_get_leaf_groups(int *ids, size_t size);
  static bool   numa_topology_changed();
  static int    numa_get_group_id();

  // Page manipulation
  struct page_info {
    size_t size;
    int lgrp_id;
  };
  static bool   get_page_info(char *start, page_info* info);
  static char*  scan_pages(char *start, char* end, page_info* page_expected, page_info* page_found);

  static char*  non_memory_address_word();
  // reserve, commit and pin the entire memory region
  static char*  reserve_memory_special(size_t size, char* addr = NULL,
                bool executable = false);
  static bool   release_memory_special(char* addr, size_t bytes);
  static bool   large_page_init();
  static size_t large_page_size();
  static bool   can_commit_large_page_memory();
  static bool   can_execute_large_page_memory();

  // OS interface to polling page
  static address get_polling_page()             { return _polling_page; }
  static void    set_polling_page(address page) { _polling_page = page; }
  static bool    is_poll_address(address addr)  { return addr >= _polling_page && addr < (_polling_page + os::vm_page_size()); }
  static void    make_polling_page_unreadable();
  static void    make_polling_page_readable();

  // Routines used to serialize the thread state without using membars
  static void    serialize_thread_states();

  // Since we write to the serialize page from every thread, we
  // want stores to be on unique cache lines whenever possible
  // in order to minimize CPU cross talk.  We pre-compute the
  // amount to shift the thread* to make this offset unique to
  // each thread.
  static int     get_serialize_page_shift_count() {
    return SerializePageShiftCount;
  }

  static void     set_serialize_page_mask(uintptr_t mask) {
    _serialize_page_mask = mask;
  }

  static unsigned int  get_serialize_page_mask() {
    return _serialize_page_mask;
  }

  static void    set_memory_serialize_page(address page);

  static address get_memory_serialize_page() {
    return (address)_mem_serialize_page;
  }

  static inline void write_memory_serialize_page(JavaThread *thread) {
    uintptr_t page_offset = ((uintptr_t)thread >>
                            get_serialize_page_shift_count()) &
                            get_serialize_page_mask();
    *(volatile int32_t *)((uintptr_t)_mem_serialize_page+page_offset) = 1;
  }

  static bool    is_memory_serialize_page(JavaThread *thread, address addr) {
    if (UseMembar) return false;
    // Previously this function calculated the exact address of this
    // thread's serialize page, and checked if the faulting address
    // was equal.  However, some platforms mask off faulting addresses
    // to the page size, so now we just check that the address is
    // within the page.  This makes the thread argument unnecessary,
    // but we retain the NULL check to preserve existing behaviour.
    if (thread == NULL) return false;
    address page = (address) _mem_serialize_page;
    return addr >= page && addr < (page + os::vm_page_size());
  }

  static void block_on_serialize_page_trap();

  // threads

  enum ThreadType {
    vm_thread,
    cgc_thread,        // Concurrent GC thread
    pgc_thread,        // Parallel GC thread
    java_thread,
    compiler_thread,
    watcher_thread,
    os_thread
  };

  static bool create_thread(Thread* thread,
                            ThreadType thr_type,
                            size_t stack_size = 0);
  static bool create_main_thread(JavaThread* thread);
  static bool create_attached_thread(JavaThread* thread);
  static void pd_start_thread(Thread* thread);
  static void start_thread(Thread* thread);

  static void initialize_thread();
  static void free_thread(OSThread* osthread);

  // thread id on Linux/64bit is 64bit, on Windows and Solaris, it's 32bit
  static intx current_thread_id();
  static int current_process_id();
  // hpi::read for calls from non native state
  // For performance, hpi::read is only callable from _thread_in_native
  static size_t read(int fd, void *buf, unsigned int nBytes);
  static int sleep(Thread* thread, jlong ms, bool interruptable);
  static int naked_sleep();
  static void infinite_sleep(); // never returns, use with CAUTION
  static void yield();        // Yields to all threads with same priority
  enum YieldResult {
    YIELD_SWITCHED = 1,         // caller descheduled, other ready threads exist & ran
    YIELD_NONEREADY = 0,        // No other runnable/ready threads.
                                // platform-specific yield return immediately
    YIELD_UNKNOWN = -1          // Unknown: platform doesn't support _SWITCHED or _NONEREADY
    // YIELD_SWITCHED and YIELD_NONREADY imply the platform supports a "strong"
    // yield that can be used in lieu of blocking.
  } ;
  static YieldResult NakedYield () ;
  static void yield_all(int attempts = 0); // Yields to all other threads including lower priority
  static void loop_breaker(int attempts);  // called from within tight loops to possibly influence time-sharing
  static OSReturn set_priority(Thread* thread, ThreadPriority priority);
  static OSReturn get_priority(const Thread* const thread, ThreadPriority& priority);

  static void interrupt(Thread* thread);
  static bool is_interrupted(Thread* thread, bool clear_interrupted);

  static int pd_self_suspend_thread(Thread* thread);

  static ExtendedPC fetch_frame_from_context(void* ucVoid, intptr_t** sp, intptr_t** fp);
  static frame      fetch_frame_from_context(void* ucVoid);

  static ExtendedPC get_thread_pc(Thread *thread);
  static void breakpoint();

  static address current_stack_pointer();
  static address current_stack_base();
  static size_t current_stack_size();

  static int message_box(const char* title, const char* message);
  static char* do_you_want_to_debug(const char* message);

  // run cmd in a separate process and return its exit code; or -1 on failures
  static int fork_and_exec(char *cmd);

  // Set file to send error reports.
  static void set_error_file(const char *logfile);

  // os::exit() is merged with vm_exit()
  // static void exit(int num);

  // Terminate the VM, but don't exit the process
  static void shutdown();

  // Terminate with an error.  Default is to generate a core file on platforms
  // that support such things.  This calls shutdown() and then aborts.
  static void abort(bool dump_core = true);

  // Die immediately, no exit hook, no abort hook, no cleanup.
  static void die();

  // Reading directories.
  static DIR*           opendir(const char* dirname);
  static int            readdir_buf_size(const char *path);
  static struct dirent* readdir(DIR* dirp, dirent* dbuf);
  static int            closedir(DIR* dirp);

  // Dynamic library extension
  static const char*    dll_file_extension();

  static const char*    get_temp_directory();
  static const char*    get_current_directory(char *buf, int buflen);

  // Builds a platform-specific full library path given a ld path and lib name
  static void           dll_build_name(char* buffer, size_t size,
                                       const char* pathname, const char* fname);

  // Symbol lookup, find nearest function name; basically it implements
  // dladdr() for all platforms. Name of the nearest function is copied
  // to buf. Distance from its base address is returned as offset.
  // If function name is not found, buf[0] is set to '\0' and offset is
  // set to -1.
  static bool dll_address_to_function_name(address addr, char* buf,
                                           int buflen, int* offset);

  // Locate DLL/DSO. On success, full path of the library is copied to
  // buf, and offset is set to be the distance between addr and the
  // library's base address. On failure, buf[0] is set to '\0' and
  // offset is set to -1.
  static bool dll_address_to_library_name(address addr, char* buf,
                                          int buflen, int* offset);

  // Find out whether the pc is in the static code for jvm.dll/libjvm.so.
  static bool address_is_in_vm(address addr);

  // Loads .dll/.so and
  // in case of error it checks if .dll/.so was built for the
  // same architecture as Hotspot is running on
  static void* dll_load(const char *name, char *ebuf, int ebuflen);

  // lookup symbol in a shared library
  static void* dll_lookup(void* handle, const char* name);

  // Print out system information; they are called by fatal error handler.
  // Output format may be different on different platforms.
  static void print_os_info(outputStream* st);
  static void print_cpu_info(outputStream* st);
  static void print_memory_info(outputStream* st);
  static void print_dll_info(outputStream* st);
  static void print_environment_variables(outputStream* st, const char** env_list, char* buffer, int len);
  static void print_context(outputStream* st, void* context);
  static void print_register_info(outputStream* st, void* context);
  static void print_siginfo(outputStream* st, void* siginfo);
  static void print_signal_handlers(outputStream* st, char* buf, size_t buflen);
  static void print_date_and_time(outputStream* st);

  static void print_location(outputStream* st, intptr_t x, bool verbose = false);

  // The following two functions are used by fatal error handler to trace
  // native (C) frames. They are not part of frame.hpp/frame.cpp because
  // frame.hpp/cpp assume thread is JavaThread, and also because different
  // OS/compiler may have different convention or provide different API to
  // walk C frames.
  //
  // We don't attempt to become a debugger, so we only follow frames if that
  // does not require a lookup in the unwind table, which is part of the binary
  // file but may be unsafe to read after a fatal error. So on x86, we can
  // only walk stack if %ebp is used as frame pointer; on ia64, it's not
  // possible to walk C stack without having the unwind table.
  static bool is_first_C_frame(frame *fr);
  static frame get_sender_for_C_frame(frame *fr);

  // return current frame. pc() and sp() are set to NULL on failure.
  static frame      current_frame();

  static void print_hex_dump(outputStream* st, address start, address end, int unitsize);

  // returns a string to describe the exception/signal;
  // returns NULL if exception_code is not an OS exception/signal.
  static const char* exception_name(int exception_code, char* buf, size_t buflen);

  // Returns native Java library, loads if necessary
  static void*    native_java_library();

  // Fills in path to jvm.dll/libjvm.so (this info used to find hpi).
  static void     jvm_path(char *buf, jint buflen);

  // Returns true if we are running in a headless jre.
  static bool     is_headless_jre();

  // JNI names
  static void     print_jni_name_prefix_on(outputStream* st, int args_size);
  static void     print_jni_name_suffix_on(outputStream* st, int args_size);

  // File conventions
  static const char* file_separator();
  static const char* line_separator();
  static const char* path_separator();

  // Init os specific system properties values
  static void init_system_properties_values();

  // IO operations, non-JVM_ version.
  static int stat(const char* path, struct stat* sbuf);
  static bool dir_is_empty(const char* path);

  // IO operations on binary files
  static int create_binary_file(const char* path, bool rewrite_existing);
  static jlong current_file_offset(int fd);
  static jlong seek_to_file_offset(int fd, jlong offset);

  // Thread Local Storage
  static int   allocate_thread_local_storage();
  static void  thread_local_storage_at_put(int index, void* value);
  static void* thread_local_storage_at(int index);
  static void  free_thread_local_storage(int index);

  // General allocation (must be MT-safe)
  static void* malloc  (size_t size);
  static void* realloc (void *memblock, size_t size);
  static void  free    (void *memblock);
  static bool  check_heap(bool force = false);      // verify C heap integrity
  static char* strdup(const char *);  // Like strdup

#ifndef PRODUCT
  static int  num_mallocs;            // # of calls to malloc/realloc
  static size_t  alloc_bytes;         // # of bytes allocated
  static int  num_frees;              // # of calls to free
#endif

  // Printing 64 bit integers
  static const char* jlong_format_specifier();
  static const char* julong_format_specifier();

  // Support for signals (see JVM_RaiseSignal, JVM_RegisterSignal)
  static void  signal_init();
  static void  signal_init_pd();
  static void  signal_notify(int signal_number);
  static void* signal(int signal_number, void* handler);
  static void  signal_raise(int signal_number);
  static int   signal_wait();
  static int   signal_lookup();
  static void* user_handler();
  static void  terminate_signal_thread();
  static int   sigexitnum_pd();

  // random number generation
  static long random();                    // return 32bit pseudorandom number
  static void init_random(long initval);   // initialize random sequence

  // Structured OS Exception support
  static void os_exception_wrapper(java_call_t f, JavaValue* value, methodHandle* method, JavaCallArguments* args, Thread* thread);

  // JVMTI & JVM monitoring and management support
  // The thread_cpu_time() and current_thread_cpu_time() are only
  // supported if is_thread_cpu_time_supported() returns true.
  // They are not supported on Solaris T1.

  // Thread CPU Time - return the fast estimate on a platform
  // On Solaris - call gethrvtime (fast) - user time only
  // On Linux   - fast clock_gettime where available - user+sys
  //            - otherwise: very slow /proc fs - user+sys
  // On Windows - GetThreadTimes - user+sys
  static jlong current_thread_cpu_time();
  static jlong thread_cpu_time(Thread* t);

  // Thread CPU Time with user_sys_cpu_time parameter.
  //
  // If user_sys_cpu_time is true, user+sys time is returned.
  // Otherwise, only user time is returned
  static jlong current_thread_cpu_time(bool user_sys_cpu_time);
  static jlong thread_cpu_time(Thread* t, bool user_sys_cpu_time);

  // Return a bunch of info about the timers.
  // Note that the returned info for these two functions may be different
  // on some platforms
  static void current_thread_cpu_time_info(jvmtiTimerInfo *info_ptr);
  static void thread_cpu_time_info(jvmtiTimerInfo *info_ptr);

  static bool is_thread_cpu_time_supported();

  // System loadavg support.  Returns -1 if load average cannot be obtained.
  static int loadavg(double loadavg[], int nelem);

  // Hook for os specific jvm options that we don't want to abort on seeing
  static bool obsolete_option(const JavaVMOption *option);

  // Platform dependent stuff
  #include "incls/_os_pd.hpp.incl"

  // debugging support (mostly used by debug.cpp but also fatal error handler)
  static bool find(address pc, outputStream* st = tty); // OS specific function to make sense out of an address

  static bool dont_yield();                     // when true, JVM_Yield() is nop
  static void print_statistics();

  // Thread priority helpers (implemented in OS-specific part)
  static OSReturn set_native_priority(Thread* thread, int native_prio);
  static OSReturn get_native_priority(const Thread* const thread, int* priority_ptr);
  static int java_to_os_priority[MaxPriority + 1];
  // Hint to the underlying OS that a task switch would not be good.
  // Void return because it's a hint and can fail.
  static void hint_no_preempt();

  // Used at creation if requested by the diagnostic flag PauseAtStartup.
  // Causes the VM to wait until an external stimulus has been applied
  // (for Unix, that stimulus is a signal, for Windows, an external
  // ResumeThread call)
  static void pause();

 protected:
  static long _rand_seed;                   // seed for random number generator
  static int _processor_count;              // number of processors

  static char* format_boot_path(const char* format_string,
                                const char* home,
                                int home_len,
                                char fileSep,
                                char pathSep);
  static bool set_boot_path(char fileSep, char pathSep);
  static char** split_path(const char* path, int* n);
};

// Note that "PAUSE" is almost always used with synchronization
// so arguably we should provide Atomic::SpinPause() instead
// of the global SpinPause() with C linkage.
// It'd also be eligible for inlining on many platforms.

extern "C" int SpinPause () ;
extern "C" int SafeFetch32 (int * adr, int errValue) ;
extern "C" intptr_t SafeFetchN (intptr_t * adr, intptr_t errValue) ;

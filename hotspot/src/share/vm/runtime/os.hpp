/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_OS_HPP
#define SHARE_VM_RUNTIME_OS_HPP

#include "jvmtifiles/jvmti.h"
#include "runtime/extendedPC.hpp"
#include "runtime/handles.hpp"
#include "utilities/top.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "jvm_linux.h"
# include <setjmp.h>
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "jvm_solaris.h"
# include <setjmp.h>
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "jvm_windows.h"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "jvm_aix.h"
# include <setjmp.h>
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "jvm_bsd.h"
# include <setjmp.h>
# ifdef __APPLE__
#  include <mach/mach_time.h>
# endif
#endif

class AgentLibrary;

// os defines the interface to operating system; this includes traditional
// OS services (time, I/O) as well as other functionality with system-
// dependent code.

typedef void (*dll_func)(...);

class Thread;
class JavaThread;
class Event;
class DLL;
class FileHandle;
class NativeCallStack;

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
  MaxPriority      = 10,     // Highest priority, used for WatcherThread
                             // ensures that VMThread doesn't starve profiler
  CriticalPriority = 11      // Critical thread priority
};

// Executable parameter flag for os::commit_memory() and
// os::commit_memory_or_exit().
const bool ExecMem = true;

// Typedef for structured exception handling support
typedef void (*java_call_t)(JavaValue* value, const methodHandle& method, JavaCallArguments* args, Thread* thread);

class MallocTracker;

class os: AllStatic {
  friend class VMStructs;
  friend class MallocTracker;
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

  static char*  pd_reserve_memory(size_t bytes, char* addr = 0,
                               size_t alignment_hint = 0);
  static char*  pd_attempt_reserve_memory_at(size_t bytes, char* addr);
  static void   pd_split_reserved_memory(char *base, size_t size,
                                      size_t split, bool realloc);
  static bool   pd_commit_memory(char* addr, size_t bytes, bool executable);
  static bool   pd_commit_memory(char* addr, size_t size, size_t alignment_hint,
                                 bool executable);
  // Same as pd_commit_memory() that either succeeds or calls
  // vm_exit_out_of_memory() with the specified mesg.
  static void   pd_commit_memory_or_exit(char* addr, size_t bytes,
                                         bool executable, const char* mesg);
  static void   pd_commit_memory_or_exit(char* addr, size_t size,
                                         size_t alignment_hint,
                                         bool executable, const char* mesg);
  static bool   pd_uncommit_memory(char* addr, size_t bytes);
  static bool   pd_release_memory(char* addr, size_t bytes);

  static char*  pd_map_memory(int fd, const char* file_name, size_t file_offset,
                           char *addr, size_t bytes, bool read_only = false,
                           bool allow_exec = false);
  static char*  pd_remap_memory(int fd, const char* file_name, size_t file_offset,
                             char *addr, size_t bytes, bool read_only,
                             bool allow_exec);
  static bool   pd_unmap_memory(char *addr, size_t bytes);
  static void   pd_free_memory(char *addr, size_t bytes, size_t alignment_hint);
  static void   pd_realign_memory(char *addr, size_t bytes, size_t alignment_hint);

  static size_t page_size_for_region(size_t region_size, size_t min_pages, bool must_be_aligned);

  // Get summary strings for system information in buffer provided
  static bool  get_host_name(char* buf, size_t buflen) PRODUCT_RETURN_(return false;);  // true if available
  static void  get_summary_cpu_info(char* buf, size_t buflen);
  static void  get_summary_os_info(char* buf, size_t buflen);

 public:
  static void init(void);                      // Called before command line parsing
  static void init_before_ergo(void);          // Called after command line parsing
                                               // before VM ergonomics processing.
  static jint init_2(void);                    // Called after command line parsing
                                               // and VM ergonomics processing
  static void init_globals(void) {             // Called from init_globals() in init.cpp
    init_globals_ext();
  }

  // File names are case-insensitive on windows only
  // Override me as needed
  static int    file_name_strcmp(const char* s1, const char* s2);

  // unset environment variable
  static bool unsetenv(const char* name);

  static bool have_special_privileges();

  static jlong  javaTimeMillis();
  static jlong  javaTimeNanos();
  static void   javaTimeNanos_info(jvmtiTimerInfo *info_ptr);
  static void   javaTimeSystemUTC(jlong &seconds, jlong &nanos);
  static void   run_periodic_checks();
  static bool   supports_monotonic_clock();

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
    // During bootstrap if _processor_count is not yet initialized
    // we claim to be MP as that is safest. If any platform has a
    // stub generator that might be triggered in this phase and for
    // which being declared MP when in fact not, is a problem - then
    // the bootstrap routine for the stub generator needs to check
    // the processor count directly and leave the bootstrap routine
    // in place until called after initialization has ocurred.
    return (_processor_count != 1) || AssumeMP;
  }
  static julong available_memory();
  static julong physical_memory();
  static bool has_allocatable_memory_limit(julong* limit);
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

  // Give a name to the current thread.
  static void set_native_thread_name(const char *name);

  // Interface for stack banging (predetect possible stack overflow for
  // exception processing)  There are guard pages, and above that shadow
  // pages for stack overflow checking.
  static bool uses_stack_guard_pages();
  static bool allocate_stack_guard_pages();
  static void map_stack_shadow_pages();
  static bool stack_shadow_pages_available(Thread *thread, const methodHandle& method);

  // OS interface to Virtual Memory

  // Return the default page size.
  static int    vm_page_size();

  // Returns the page size to use for a region of memory.
  // region_size / min_pages will always be greater than or equal to the
  // returned value. The returned value will divide region_size.
  static size_t page_size_for_region_aligned(size_t region_size, size_t min_pages);

  // Returns the page size to use for a region of memory.
  // region_size / min_pages will always be greater than or equal to the
  // returned value. The returned value might not divide region_size.
  static size_t page_size_for_region_unaligned(size_t region_size, size_t min_pages);

  // Return the largest page size that can be used
  static size_t max_page_size() {
    // The _page_sizes array is sorted in descending order.
    return _page_sizes[0];
  }

  // Methods for tracing page sizes returned by the above method; enabled by
  // TracePageSizes.  The region_{min,max}_size parameters should be the values
  // passed to page_size_for_region() and page_size should be the result of that
  // call.  The (optional) base and size parameters should come from the
  // ReservedSpace base() and size() methods.
  static void trace_page_sizes(const char* str, const size_t* page_sizes,
                               int count) PRODUCT_RETURN;
  static void trace_page_sizes(const char* str, const size_t region_min_size,
                               const size_t region_max_size,
                               const size_t page_size,
                               const char* base = NULL,
                               const size_t size = 0) PRODUCT_RETURN;

  static int    vm_allocation_granularity();
  static char*  reserve_memory(size_t bytes, char* addr = 0,
                               size_t alignment_hint = 0);
  static char*  reserve_memory(size_t bytes, char* addr,
                               size_t alignment_hint, MEMFLAGS flags);
  static char*  reserve_memory_aligned(size_t size, size_t alignment);
  static char*  attempt_reserve_memory_at(size_t bytes, char* addr);
  static void   split_reserved_memory(char *base, size_t size,
                                      size_t split, bool realloc);
  static bool   commit_memory(char* addr, size_t bytes, bool executable);
  static bool   commit_memory(char* addr, size_t size, size_t alignment_hint,
                              bool executable);
  // Same as commit_memory() that either succeeds or calls
  // vm_exit_out_of_memory() with the specified mesg.
  static void   commit_memory_or_exit(char* addr, size_t bytes,
                                      bool executable, const char* mesg);
  static void   commit_memory_or_exit(char* addr, size_t size,
                                      size_t alignment_hint,
                                      bool executable, const char* mesg);
  static bool   uncommit_memory(char* addr, size_t bytes);
  static bool   release_memory(char* addr, size_t bytes);

  // Touch memory pages that cover the memory range from start to end (exclusive)
  // to make the OS back the memory range with actual memory.
  // Current implementation may not touch the last page if unaligned addresses
  // are passed.
  static void   pretouch_memory(char* start, char* end);

  enum ProtType { MEM_PROT_NONE, MEM_PROT_READ, MEM_PROT_RW, MEM_PROT_RWX };
  static bool   protect_memory(char* addr, size_t bytes, ProtType prot,
                               bool is_committed = true);

  static bool   guard_memory(char* addr, size_t bytes);
  static bool   unguard_memory(char* addr, size_t bytes);
  static bool   create_stack_guard_pages(char* addr, size_t bytes);
  static bool   pd_create_stack_guard_pages(char* addr, size_t bytes);
  static bool   remove_stack_guard_pages(char* addr, size_t bytes);

  static char*  map_memory(int fd, const char* file_name, size_t file_offset,
                           char *addr, size_t bytes, bool read_only = false,
                           bool allow_exec = false);
  static char*  remap_memory(int fd, const char* file_name, size_t file_offset,
                             char *addr, size_t bytes, bool read_only,
                             bool allow_exec);
  static bool   unmap_memory(char *addr, size_t bytes);
  static void   free_memory(char *addr, size_t bytes, size_t alignment_hint);
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
  static char*  reserve_memory_special(size_t size, size_t alignment,
                                       char* addr, bool executable);
  static bool   release_memory_special(char* addr, size_t bytes);
  static void   large_page_init();
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
    // but we retain the NULL check to preserve existing behavior.
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

  static void initialize_thread(Thread* thr);
  static void free_thread(OSThread* osthread);

  // thread id on Linux/64bit is 64bit, on Windows and Solaris, it's 32bit
  static intx current_thread_id();
  static int current_process_id();
  static int sleep(Thread* thread, jlong ms, bool interruptable);
  // Short standalone OS sleep suitable for slow path spin loop.
  // Ignores Thread.interrupt() (so keep it short).
  // ms = 0, will sleep for the least amount of time allowed by the OS.
  static void naked_short_sleep(jlong ms);
  static void infinite_sleep(); // never returns, use with CAUTION
  static void naked_yield () ;
  static OSReturn set_priority(Thread* thread, ThreadPriority priority);
  static OSReturn get_priority(const Thread* const thread, ThreadPriority& priority);

  static void interrupt(Thread* thread);
  static bool is_interrupted(Thread* thread, bool clear_interrupted);

  static int pd_self_suspend_thread(Thread* thread);

  static ExtendedPC fetch_frame_from_context(const void* ucVoid, intptr_t** sp, intptr_t** fp);
  static frame      fetch_frame_from_context(const void* ucVoid);
  static frame      fetch_frame_from_ucontext(Thread* thread, void* ucVoid);

  static ExtendedPC get_thread_pc(Thread *thread);
  static void breakpoint();
  static bool start_debugging(char *buf, int buflen);

  static address current_stack_pointer();
  static address current_stack_base();
  static size_t current_stack_size();

  static void verify_stack_alignment() PRODUCT_RETURN;

  static bool message_box(const char* title, const char* message);
  static char* do_you_want_to_debug(const char* message);

  // run cmd in a separate process and return its exit code; or -1 on failures
  static int fork_and_exec(char *cmd);

  // Call ::exit() on all platforms but Windows
  static void exit(int num);

  // Terminate the VM, but don't exit the process
  static void shutdown();

  // Terminate with an error.  Default is to generate a core file on platforms
  // that support such things.  This calls shutdown() and then aborts.
  static void abort(bool dump_core, void *siginfo, const void *context);
  static void abort(bool dump_core = true);

  // Die immediately, no exit hook, no abort hook, no cleanup.
  static void die();

  // File i/o operations
  static const int default_file_open_flags();
  static int open(const char *path, int oflag, int mode);
  static FILE* open(int fd, const char* mode);
  static int close(int fd);
  static jlong lseek(int fd, jlong offset, int whence);
  static char* native_path(char *path);
  static int ftruncate(int fd, jlong length);
  static int fsync(int fd);
  static int available(int fd, jlong *bytes);

  //File i/o operations

  static size_t read(int fd, void *buf, unsigned int nBytes);
  static size_t read_at(int fd, void *buf, unsigned int nBytes, jlong offset);
  static size_t restartable_read(int fd, void *buf, unsigned int nBytes);
  static size_t write(int fd, const void *buf, unsigned int nBytes);

  // Reading directories.
  static DIR*           opendir(const char* dirname);
  static int            readdir_buf_size(const char *path);
  static struct dirent* readdir(DIR* dirp, dirent* dbuf);
  static int            closedir(DIR* dirp);

  // Dynamic library extension
  static const char*    dll_file_extension();

  static const char*    get_temp_directory();
  static const char*    get_current_directory(char *buf, size_t buflen);

  // Builds a platform-specific full library path given a ld path and lib name
  // Returns true if buffer contains full path to existing file, false otherwise
  static bool           dll_build_name(char* buffer, size_t size,
                                       const char* pathname, const char* fname);

  // Symbol lookup, find nearest function name; basically it implements
  // dladdr() for all platforms. Name of the nearest function is copied
  // to buf. Distance from its base address is optionally returned as offset.
  // If function name is not found, buf[0] is set to '\0' and offset is
  // set to -1 (if offset is non-NULL).
  static bool dll_address_to_function_name(address addr, char* buf,
                                           int buflen, int* offset,
                                           bool demangle = true);

  // Locate DLL/DSO. On success, full path of the library is copied to
  // buf, and offset is optionally set to be the distance between addr
  // and the library's base address. On failure, buf[0] is set to '\0'
  // and offset is set to -1 (if offset is non-NULL).
  static bool dll_address_to_library_name(address addr, char* buf,
                                          int buflen, int* offset);

  // Find out whether the pc is in the static code for jvm.dll/libjvm.so.
  static bool address_is_in_vm(address addr);

  // Loads .dll/.so and
  // in case of error it checks if .dll/.so was built for the
  // same architecture as HotSpot is running on
  static void* dll_load(const char *name, char *ebuf, int ebuflen);

  // lookup symbol in a shared library
  static void* dll_lookup(void* handle, const char* name);

  // Unload library
  static void  dll_unload(void *lib);

  // Callback for loaded module information
  // Input parameters:
  //    char*     module_file_name,
  //    address   module_base_addr,
  //    address   module_top_addr,
  //    void*     param
  typedef int (*LoadedModulesCallbackFunc)(const char *, address, address, void *);

  static int get_loaded_modules_info(LoadedModulesCallbackFunc callback, void *param);

  // Return the handle of this process
  static void* get_default_process_handle();

  // Check for static linked agent library
  static bool find_builtin_agent(AgentLibrary *agent_lib, const char *syms[],
                                 size_t syms_len);

  // Find agent entry point
  static void *find_agent_function(AgentLibrary *agent_lib, bool check_lib,
                                   const char *syms[], size_t syms_len);

  // Write to stream
  static int log_vsnprintf(char* buf, size_t len, const char* fmt, va_list args) ATTRIBUTE_PRINTF(3, 0);

  // Print out system information; they are called by fatal error handler.
  // Output format may be different on different platforms.
  static void print_os_info(outputStream* st);
  static void print_os_info_brief(outputStream* st);
  static void print_cpu_info(outputStream* st, char* buf, size_t buflen);
  static void pd_print_cpu_info(outputStream* st, char* buf, size_t buflen);
  static void print_summary_info(outputStream* st, char* buf, size_t buflen);
  static void print_memory_info(outputStream* st);
  static void print_dll_info(outputStream* st);
  static void print_environment_variables(outputStream* st, const char** env_list);
  static void print_context(outputStream* st, const void* context);
  static void print_register_info(outputStream* st, const void* context);
  static void print_siginfo(outputStream* st, const void* siginfo);
  static void print_signal_handlers(outputStream* st, char* buf, size_t buflen);
  static void print_date_and_time(outputStream* st, char* buf, size_t buflen);

  static void print_location(outputStream* st, intptr_t x, bool verbose = false);
  static size_t lasterror(char *buf, size_t len);
  static int get_last_error();

  // Determines whether the calling process is being debugged by a user-mode debugger.
  static bool is_debugger_attached();

  // wait for a key press if PauseAtExit is set
  static void wait_for_keypress_at_exit(void);

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

  // Returns the signal number (e.g. 11) for a given signal name (SIGSEGV).
  static int get_signal_number(const char* signal_name);

  // Returns native Java library, loads if necessary
  static void*    native_java_library();

  // Fills in path to jvm.dll/libjvm.so (used by the Disassembler)
  static void     jvm_path(char *buf, jint buflen);

  // Returns true if we are running in a headless jre.
  static bool     is_headless_jre();

  // JNI names
  static void     print_jni_name_prefix_on(outputStream* st, int args_size);
  static void     print_jni_name_suffix_on(outputStream* st, int args_size);

  // Init os specific system properties values
  static void init_system_properties_values();

  // IO operations, non-JVM_ version.
  static int stat(const char* path, struct stat* sbuf);
  static bool dir_is_empty(const char* path);

  // IO operations on binary files
  static int create_binary_file(const char* path, bool rewrite_existing);
  static jlong current_file_offset(int fd);
  static jlong seek_to_file_offset(int fd, jlong offset);

  // Retrieve native stack frames.
  // Parameter:
  //   stack:  an array to storage stack pointers.
  //   frames: size of above array.
  //   toSkip: number of stack frames to skip at the beginning.
  // Return: number of stack frames captured.
  static int get_native_stack(address* stack, int size, int toSkip = 0);

  // General allocation (must be MT-safe)
  static void* malloc  (size_t size, MEMFLAGS flags, const NativeCallStack& stack);
  static void* malloc  (size_t size, MEMFLAGS flags);
  static void* realloc (void *memblock, size_t size, MEMFLAGS flag, const NativeCallStack& stack);
  static void* realloc (void *memblock, size_t size, MEMFLAGS flag);

  static void  free    (void *memblock);
  static bool  check_heap(bool force = false);      // verify C heap integrity
  static char* strdup(const char *, MEMFLAGS flags = mtInternal);  // Like strdup
  // Like strdup, but exit VM when strdup() returns NULL
  static char* strdup_check_oom(const char*, MEMFLAGS flags = mtInternal);

#ifndef PRODUCT
  static julong num_mallocs;         // # of calls to malloc/realloc
  static julong alloc_bytes;         // # of bytes allocated
  static julong num_frees;           // # of calls to free
  static julong free_bytes;          // # of bytes freed
#endif

  // SocketInterface (ex HPI SocketInterface )
  static int socket(int domain, int type, int protocol);
  static int socket_close(int fd);
  static int recv(int fd, char* buf, size_t nBytes, uint flags);
  static int send(int fd, char* buf, size_t nBytes, uint flags);
  static int raw_send(int fd, char* buf, size_t nBytes, uint flags);
  static int connect(int fd, struct sockaddr* him, socklen_t len);
  static struct hostent* get_host_by_name(char* name);

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
  static void os_exception_wrapper(java_call_t f, JavaValue* value, const methodHandle& method, JavaCallArguments* args, Thread* thread);

  // On Posix compatible OS it will simply check core dump limits while on Windows
  // it will check if dump file can be created. Check or prepare a core dump to be
  // taken at a later point in the same thread in os::abort(). Use the caller
  // provided buffer as a scratch buffer. The status message which will be written
  // into the error log either is file location or a short error message, depending
  // on the checking result.
  static void check_dump_limit(char* buffer, size_t bufferSize);

  // Get the default path to the core file
  // Returns the length of the string
  static int get_core_path(char* buffer, size_t bufferSize);

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

  // Amount beyond the callee frame size that we bang the stack.
  static int extra_bang_size_in_bytes();

  // Extensions
#include "runtime/os_ext.hpp"

 public:
  class CrashProtectionCallback : public StackObj {
  public:
    virtual void call() = 0;
  };

  // Platform dependent stuff
#ifdef TARGET_OS_FAMILY_linux
# include "os_linux.hpp"
# include "os_posix.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "os_solaris.hpp"
# include "os_posix.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "os_windows.hpp"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "os_aix.hpp"
# include "os_posix.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "os_posix.hpp"
# include "os_bsd.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_x86
# include "os_linux_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_sparc
# include "os_linux_sparc.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_zero
# include "os_linux_zero.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_x86
# include "os_solaris_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_solaris_sparc
# include "os_solaris_sparc.hpp"
#endif
#ifdef TARGET_OS_ARCH_windows_x86
# include "os_windows_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_arm
# include "os_linux_arm.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_ppc
# include "os_linux_ppc.hpp"
#endif
#ifdef TARGET_OS_ARCH_aix_ppc
# include "os_aix_ppc.hpp"
#endif
#ifdef TARGET_OS_ARCH_linux_aarch64
# include "os_linux_aarch64.hpp"
#endif
#ifdef TARGET_OS_ARCH_bsd_x86
# include "os_bsd_x86.hpp"
#endif
#ifdef TARGET_OS_ARCH_bsd_zero
# include "os_bsd_zero.hpp"
#endif

#ifndef OS_NATIVE_THREAD_CREATION_FAILED_MSG
#define OS_NATIVE_THREAD_CREATION_FAILED_MSG "unable to create native thread: possibly out of memory or process/resource limits reached"
#endif

 public:
#ifndef PLATFORM_PRINT_NATIVE_STACK
  // No platform-specific code for printing the native stack.
  static bool platform_print_native_stack(outputStream* st, const void* context,
                                          char *buf, int buf_size) {
    return false;
  }
#endif

  // debugging support (mostly used by debug.cpp but also fatal error handler)
  static bool find(address pc, outputStream* st = tty); // OS specific function to make sense out of an address

  static bool dont_yield();                     // when true, JVM_Yield() is nop
  static void print_statistics();

  // Thread priority helpers (implemented in OS-specific part)
  static OSReturn set_native_priority(Thread* thread, int native_prio);
  static OSReturn get_native_priority(const Thread* const thread, int* priority_ptr);
  static int java_to_os_priority[CriticalPriority + 1];
  // Hint to the underlying OS that a task switch would not be good.
  // Void return because it's a hint and can fail.
  static void hint_no_preempt();
  static const char* native_thread_creation_failed_msg() {
    return OS_NATIVE_THREAD_CREATION_FAILED_MSG;
  }

  // Used at creation if requested by the diagnostic flag PauseAtStartup.
  // Causes the VM to wait until an external stimulus has been applied
  // (for Unix, that stimulus is a signal, for Windows, an external
  // ResumeThread call)
  static void pause();

  // Builds a platform dependent Agent_OnLoad_<libname> function name
  // which is used to find statically linked in agents.
  static char*  build_agent_function_name(const char *sym, const char *cname,
                                          bool is_absolute_path);

  class SuspendedThreadTaskContext {
  public:
    SuspendedThreadTaskContext(Thread* thread, void *ucontext) : _thread(thread), _ucontext(ucontext) {}
    Thread* thread() const { return _thread; }
    void* ucontext() const { return _ucontext; }
  private:
    Thread* _thread;
    void* _ucontext;
  };

  class SuspendedThreadTask {
  public:
    SuspendedThreadTask(Thread* thread) : _thread(thread), _done(false) {}
    virtual ~SuspendedThreadTask() {}
    void run();
    bool is_done() { return _done; }
    virtual void do_task(const SuspendedThreadTaskContext& context) = 0;
  protected:
  private:
    void internal_do_task();
    Thread* _thread;
    bool _done;
  };

#ifndef TARGET_OS_FAMILY_windows
  // Suspend/resume support
  // Protocol:
  //
  // a thread starts in SR_RUNNING
  //
  // SR_RUNNING can go to
  //   * SR_SUSPEND_REQUEST when the WatcherThread wants to suspend it
  // SR_SUSPEND_REQUEST can go to
  //   * SR_RUNNING if WatcherThread decides it waited for SR_SUSPENDED too long (timeout)
  //   * SR_SUSPENDED if the stopped thread receives the signal and switches state
  // SR_SUSPENDED can go to
  //   * SR_WAKEUP_REQUEST when the WatcherThread has done the work and wants to resume
  // SR_WAKEUP_REQUEST can go to
  //   * SR_RUNNING when the stopped thread receives the signal
  //   * SR_WAKEUP_REQUEST on timeout (resend the signal and try again)
  class SuspendResume {
   public:
    enum State {
      SR_RUNNING,
      SR_SUSPEND_REQUEST,
      SR_SUSPENDED,
      SR_WAKEUP_REQUEST
    };

  private:
    volatile State _state;

  private:
    /* try to switch state from state "from" to state "to"
     * returns the state set after the method is complete
     */
    State switch_state(State from, State to);

  public:
    SuspendResume() : _state(SR_RUNNING) { }

    State state() const { return _state; }

    State request_suspend() {
      return switch_state(SR_RUNNING, SR_SUSPEND_REQUEST);
    }

    State cancel_suspend() {
      return switch_state(SR_SUSPEND_REQUEST, SR_RUNNING);
    }

    State suspended() {
      return switch_state(SR_SUSPEND_REQUEST, SR_SUSPENDED);
    }

    State request_wakeup() {
      return switch_state(SR_SUSPENDED, SR_WAKEUP_REQUEST);
    }

    State running() {
      return switch_state(SR_WAKEUP_REQUEST, SR_RUNNING);
    }

    bool is_running() const {
      return _state == SR_RUNNING;
    }

    bool is_suspend_request() const {
      return _state == SR_SUSPEND_REQUEST;
    }

    bool is_suspended() const {
      return _state == SR_SUSPENDED;
    }
  };
#endif


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

extern "C" int SpinPause();

#endif // SHARE_VM_RUNTIME_OS_HPP

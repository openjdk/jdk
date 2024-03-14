/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_OS_HPP
#define SHARE_RUNTIME_OS_HPP

#include "jvm_md.h"
#include "runtime/osInfo.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/macros.hpp"
#ifdef __APPLE__
# include <mach/mach_time.h>
#endif

class frame;
class JvmtiAgent;

// Rules for using and implementing methods declared in the "os" class
// ===================================================================
//
// The "os" class defines a number of the interfaces for porting HotSpot
// to different operating systems. For example, I/O, memory, timing, etc.
// Note that additional classes such as Semaphore, Mutex, etc., are used for
// porting specific groups of features.
//
// Structure of os*.{cpp, hpp} files
//
// - os.hpp
//
//   (This file) declares the entire API of the "os" class.
//
// - os.inline.hpp
//
//   To use any of the inline methods declared in the "os" class, this
//   header file must be included.
//
// - src/hotspot/os/<os>/os_<os>.hpp
// - src/hotspot/os/posix/os_posix.hpp
//
//   These headers declare APIs that should be used only within the
//   platform-specific source files for that particular OS.
//
//   For example, os_linux.hpp declares the os::Linux class, which provides
//   many methods that can be used by files under os/linux/ and os_cpu/linux_*/
//
//   os_posix.hpp can be used by platform-specific files for POSIX-like
//   OSes such as aix, bsd and linux.
//
//   Platform-independent source files should not include these header files
//   (although sadly there are some rare exceptions ...)
//
// - os.cpp
//
//   Platform-independent methods of the "os" class are defined
//   in os.cpp. These are not part of the porting interface, but rather
//   can be considered as convenience functions for accessing
//   the porting interface. E.g., os::print_function_and_library_name().
//
// The methods declared in os.hpp but not implemented in os.cpp are
// a part the HotSpot Porting APIs. They must be implemented in one of
// the following four files:
//
// - src/hotspot/os/<os>/os_<os>.inline.hpp
// - src/hotspot/os_cpu/<os>_<cpu>/os_<os>_<cpu>.inline.hpp
// - src/hotspot/os/<os>/os_<os>.cpp
// - src/hotspot/os_cpu/<os>_<cpu>/os_<os>_<cpu>.cpp
//
//   The Porting APIs declared as "inline" in os.hpp MUST be
//   implemented in one of the two .inline.hpp files, depending on
//   whether the feature is specific to a particular CPU architecture
//   for this OS. These two files are automatically included by
//   os.inline.hpp. Platform-independent source files must not include
//   these two files directly.
//
//   If the full definition of an inline method is too complex to fit in a
//   header file, the actual implementation can be deferred to another
//   method defined in the .cpp files.
//
//   The Porting APIs that are *not* declared as "inline" in os.hpp MUST
//   be implemented in one of the two .cpp files above. These files
//   also implement OS-specific APIs such as os::Linux, os::Posix, etc.
//
// (Note: on the POSIX-like platforms, some of the Porting APIs are implemented
// in os_posix.cpp instead).

class Thread;
class JavaThread;
class NativeCallStack;
class methodHandle;
class OSThread;
class Mutex;

struct jvmtiTimerInfo;

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

enum WXMode {
  WXWrite,
  WXExec
};

// Executable parameter flag for os::commit_memory() and
// os::commit_memory_or_exit().
const bool ExecMem = true;

// Typedef for structured exception handling support
typedef void (*java_call_t)(JavaValue* value, const methodHandle& method, JavaCallArguments* args, JavaThread* thread);

class MallocTracker;

class os: AllStatic {
  friend class VMStructs;
  friend class JVMCIVMStructs;
  friend class MallocTracker;

#ifdef ASSERT
 private:
  static bool _mutex_init_done;
 public:
  static void set_mutex_init_done() { _mutex_init_done = true; }
  static bool mutex_init_done() { return _mutex_init_done; }
#endif

 public:

  // A simple value class holding a set of page sizes (similar to sigset_t)
  class PageSizes {
    size_t _v; // actually a bitmap.
  public:
    PageSizes() : _v(0) {}
    void add(size_t pagesize);
    bool contains(size_t pagesize) const;
    // Given a page size, return the next smaller page size in this set, or 0.
    size_t next_smaller(size_t pagesize) const;
    // Given a page size, return the next larger page size in this set, or 0.
    size_t next_larger(size_t pagesize) const;
    // Returns the largest page size in this set, or 0 if set is empty.
    size_t largest() const;
    // Returns the smallest page size in this set, or 0 if set is empty.
    size_t smallest() const;
    // Prints one line of comma separated, human readable page sizes, "empty" if empty.
    void print_on(outputStream* st) const;
  };

 private:
  static OSThread*          _starting_thread;
  static PageSizes          _page_sizes;

  // The default value for os::vm_min_address() unless the platform knows better. This value
  // is chosen to give us reasonable protection against null pointer dereferences while being
  // low enough to leave most of the valuable low-4gb address space open.
  static constexpr size_t _vm_min_address_default = 16 * M;

  static char*  pd_reserve_memory(size_t bytes, bool executable);

  static char*  pd_attempt_reserve_memory_at(char* addr, size_t bytes, bool executable);

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
  static bool   pd_uncommit_memory(char* addr, size_t bytes, bool executable);
  static bool   pd_release_memory(char* addr, size_t bytes);

  static char*  pd_attempt_map_memory_to_file_at(char* addr, size_t bytes, int file_desc);

  static char*  pd_map_memory(int fd, const char* file_name, size_t file_offset,
                           char *addr, size_t bytes, bool read_only = false,
                           bool allow_exec = false);
  static bool   pd_unmap_memory(char *addr, size_t bytes);
  static void   pd_free_memory(char *addr, size_t bytes, size_t alignment_hint);
  static void   pd_realign_memory(char *addr, size_t bytes, size_t alignment_hint);

  // Returns 0 if pretouch is done via platform dependent method, or otherwise
  // returns page_size that should be used for the common method.
  static size_t pd_pretouch_memory(void* first, void* last, size_t page_size);

  static char*  pd_reserve_memory_special(size_t size, size_t alignment, size_t page_size,

                                          char* addr, bool executable);
  static bool   pd_release_memory_special(char* addr, size_t bytes);

  static size_t page_size_for_region(size_t region_size, size_t min_pages, bool must_be_aligned);

  // Get summary strings for system information in buffer provided
  static void  get_summary_cpu_info(char* buf, size_t buflen);
  static void  get_summary_os_info(char* buf, size_t buflen);
  // Returns number of bytes written on success, OS_ERR on failure.
  static ssize_t pd_write(int fd, const void *buf, size_t nBytes);

  static void initialize_initial_active_processor_count();

  LINUX_ONLY(static void pd_init_container_support();)

 public:
  static void init(void);                      // Called before command line parsing

  static void init_container_support() {       // Called during command line parsing.
     LINUX_ONLY(pd_init_container_support();)
  }

  static void init_before_ergo(void);          // Called after command line parsing
                                               // before VM ergonomics processing.
  static jint init_2(void);                    // Called after command line parsing
                                               // and VM ergonomics processing

  // Get environ pointer, platform independently
  static char** get_environ();

  static bool have_special_privileges();

  static jlong  javaTimeMillis();
  static jlong  javaTimeNanos();
  static void   javaTimeNanos_info(jvmtiTimerInfo *info_ptr);
  static void   javaTimeSystemUTC(jlong &seconds, jlong &nanos);
  static void   run_periodic_checks(outputStream* st);

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
  // this functionality for the current thread, and if so the second
  // returns the elapsed virtual time for the current thread.
  static bool supports_vtime();
  static double elapsedVTime();

  // Return current local time in a string (YYYY-MM-DD HH:MM:SS).
  // It is MT safe, but not async-safe, as reading time zone
  // information may require a lock on some platforms.
  static char*      local_time_string(char *buf, size_t buflen);
  static struct tm* localtime_pd     (const time_t* clock, struct tm*  res);
  static struct tm* gmtime_pd        (const time_t* clock, struct tm*  res);

  // "YYYY-MM-DDThh:mm:ss.mmm+zzzz" incl. terminating zero
  static const size_t iso8601_timestamp_size = 29;

  // Fill in buffer with an ISO-8601 string corresponding to the given javaTimeMillis value
  // E.g., YYYY-MM-DDThh:mm:ss.mmm+zzzz.
  // Returns buffer, or null if it failed.
  static char* iso8601_time(jlong milliseconds_since_19700101, char* buffer,
                            size_t buffer_length, bool utc = false);

  // Fill in buffer with current local time as an ISO-8601 string.
  // E.g., YYYY-MM-DDThh:mm:ss.mmm+zzzz.
  // Returns buffer, or null if it failed.
  static char* iso8601_time(char* buffer, size_t buffer_length, bool utc = false);

  // Interface for detecting multiprocessor system
  static inline bool is_MP() {
    // During bootstrap if _processor_count is not yet initialized
    // we claim to be MP as that is safest. If any platform has a
    // stub generator that might be triggered in this phase and for
    // which being declared MP when in fact not, is a problem - then
    // the bootstrap routine for the stub generator needs to check
    // the processor count directly and leave the bootstrap routine
    // in place until called after initialization has occurred.
    return (_processor_count != 1);
  }

  // On some platforms there is a distinction between "available" memory and "free" memory.
  // For example, on Linux, "available" memory (`MemAvailable` in `/proc/meminfo`) is greater
  // than "free" memory (`MemFree` in `/proc/meminfo`) because Linux can free memory
  // aggressively (e.g. clear caches) so that it becomes available.
  static julong available_memory();
  static julong free_memory();

  static jlong total_swap_space();
  static jlong free_swap_space();

  static julong physical_memory();
  static bool has_allocatable_memory_limit(size_t* limit);
  static bool is_server_class_machine();

  // Returns the id of the processor on which the calling thread is currently executing.
  // The returned value is guaranteed to be between 0 and (os::processor_count() - 1).
  static uint processor_id();

  // number of CPUs
  static int processor_count() {
    return _processor_count;
  }
  static void set_processor_count(int count) { _processor_count = count; }

  // Returns the number of CPUs this process is currently allowed to run on.
  // Note that on some OSes this can change dynamically.
  static int active_processor_count();

  // At startup the number of active CPUs this process is allowed to run on.
  // This value does not change dynamically. May be different from active_processor_count().
  static int initial_active_processor_count() {
    assert(_initial_active_processor_count > 0, "Initial active processor count not set yet.");
    return _initial_active_processor_count;
  }

  // Give a name to the current thread.
  static void set_native_thread_name(const char *name);

  // Interface for stack banging (predetect possible stack overflow for
  // exception processing)  There are guard pages, and above that shadow
  // pages for stack overflow checking.
  inline static bool uses_stack_guard_pages();
  inline static bool must_commit_stack_guard_pages();
  inline static void map_stack_shadow_pages(address sp);
  static bool stack_shadow_pages_available(Thread *thread, const methodHandle& method, address sp);

 private:
  // Minimum stack size a thread can be created with (allowing
  // the VM to completely create the thread and enter user code).
  // The initial values exclude any guard pages (by HotSpot or libc).
  // set_minimum_stack_sizes() will add the size required for
  // HotSpot guard pages depending on page size and flag settings.
  // Libc guard pages are never considered by these values.
  static size_t _compiler_thread_min_stack_allowed;
  static size_t _java_thread_min_stack_allowed;
  static size_t _vm_internal_thread_min_stack_allowed;
  static size_t _os_min_stack_allowed;

  // Check and sets minimum stack sizes
  static jint set_minimum_stack_sizes();

 public:
  // Find committed memory region within specified range (start, start + size),
  // return true if found any
  static bool committed_in_range(address start, size_t size, address& committed_start, size_t& committed_size);

  // OS interface to Virtual Memory

  // Return the default page size.
  static size_t vm_page_size() { return OSInfo::vm_page_size(); }

  // The set of page sizes which the VM is allowed to use (may be a subset of
  //  the page sizes actually available on the platform).
  static const PageSizes& page_sizes() { return _page_sizes; }

  // Returns the page size to use for a region of memory.
  // region_size / min_pages will always be greater than or equal to the
  // returned value. The returned value will divide region_size.
  static size_t page_size_for_region_aligned(size_t region_size, size_t min_pages);

  // Returns the page size to use for a region of memory.
  // region_size / min_pages will always be greater than or equal to the
  // returned value. The returned value might not divide region_size.
  static size_t page_size_for_region_unaligned(size_t region_size, size_t min_pages);

  // Return the largest page size that can be used
  static size_t max_page_size() { return page_sizes().largest(); }

  // Return a lower bound for page sizes. Also works before os::init completed.
  static size_t min_page_size() { return 4 * K; }

  // Methods for tracing page sizes returned by the above method.
  // The region_{min,max}_size parameters should be the values
  // passed to page_size_for_region() and page_size should be the result of that
  // call.  The (optional) base and size parameters should come from the
  // ReservedSpace base() and size() methods.
  static void trace_page_sizes(const char* str,
                               const size_t region_min_size,
                               const size_t region_max_size,
                               const char* base,
                               const size_t size,
                               const size_t page_size);
  static void trace_page_sizes_for_requested_size(const char* str,
                                                  const size_t requested_size,
                                                  const size_t requested_page_size,
                                                  const char* base,
                                                  const size_t size,
                                                  const size_t page_size);

  static size_t vm_allocation_granularity() { return OSInfo::vm_allocation_granularity(); }

  // Returns the lowest address the process is allowed to map against.
  static size_t vm_min_address();

  inline static size_t cds_core_region_alignment();

  // Reserves virtual memory.
  static char*  reserve_memory(size_t bytes, bool executable = false, MEMFLAGS flags = mtNone);

  // Reserves virtual memory that starts at an address that is aligned to 'alignment'.
  static char*  reserve_memory_aligned(size_t size, size_t alignment, bool executable = false);

  // Attempts to reserve the virtual memory at [addr, addr + bytes).
  // Does not overwrite existing mappings.
  static char*  attempt_reserve_memory_at(char* addr, size_t bytes, bool executable = false, MEMFLAGS flag = mtNone);

  // Given an address range [min, max), attempts to reserve memory within this area, with the given alignment.
  // If randomize is true, the location will be randomized.
  static char* attempt_reserve_memory_between(char* min, char* max, size_t bytes, size_t alignment, bool randomize);

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
  static bool   uncommit_memory(char* addr, size_t bytes, bool executable = false);
  static bool   release_memory(char* addr, size_t bytes);

  // Does the platform support trimming the native heap?
  static bool can_trim_native_heap();

  // Trim the C-heap. Optionally returns working set size change (RSS+Swap) in *rss_change.
  // Note: If trimming succeeded but no size change information could be obtained,
  // rss_change.after will contain SIZE_MAX upon return.
  struct size_change_t { size_t before; size_t after; };
  static bool trim_native_heap(size_change_t* rss_change = nullptr);

  // A diagnostic function to print memory mappings in the given range.
  static void print_memory_mappings(char* addr, size_t bytes, outputStream* st);
  // Prints all mappings
  static void print_memory_mappings(outputStream* st);

  // Touch memory pages that cover the memory range from start to end
  // (exclusive) to make the OS back the memory range with actual memory.
  // Other threads may use the memory range concurrently with pretouch.
  static void   pretouch_memory(void* start, void* end, size_t page_size = vm_page_size());

  enum ProtType { MEM_PROT_NONE, MEM_PROT_READ, MEM_PROT_RW, MEM_PROT_RWX };
  static bool   protect_memory(char* addr, size_t bytes, ProtType prot,
                               bool is_committed = true);

  static bool   guard_memory(char* addr, size_t bytes);
  static bool   unguard_memory(char* addr, size_t bytes);
  static bool   create_stack_guard_pages(char* addr, size_t bytes);
  static bool   pd_create_stack_guard_pages(char* addr, size_t bytes);
  static bool   remove_stack_guard_pages(char* addr, size_t bytes);
  // Helper function to create a new file with template jvmheap.XXXXXX.
  // Returns a valid fd on success or else returns -1
  static int create_file_for_heap(const char* dir);
  // Map memory to the file referred by fd. This function is slightly different from map_memory()
  // and is added to be used for implementation of -XX:AllocateHeapAt
  static char* map_memory_to_file(size_t size, int fd, MEMFLAGS flag = mtNone);
  static char* map_memory_to_file_aligned(size_t size, size_t alignment, int fd, MEMFLAGS flag = mtNone);
  static char* map_memory_to_file(char* base, size_t size, int fd);
  static char* attempt_map_memory_to_file_at(char* base, size_t size, int fd, MEMFLAGS flag = mtNone);
  // Replace existing reserved memory with file mapping
  static char* replace_existing_mapping_with_file_mapping(char* base, size_t size, int fd);

  static char*  map_memory(int fd, const char* file_name, size_t file_offset,
                           char *addr, size_t bytes, bool read_only = false,
                           bool allow_exec = false, MEMFLAGS flags = mtNone);
  static bool   unmap_memory(char *addr, size_t bytes);
  static void   free_memory(char *addr, size_t bytes, size_t alignment_hint);
  static void   realign_memory(char *addr, size_t bytes, size_t alignment_hint);

  // NUMA-specific interface
  static bool   numa_has_group_homing();
  static void   numa_make_local(char *addr, size_t bytes, int lgrp_hint);
  static void   numa_make_global(char *addr, size_t bytes);
  static size_t numa_get_groups_num();
  static size_t numa_get_leaf_groups(uint *ids, size_t size);
  static bool   numa_topology_changed();
  static int    numa_get_group_id();
  static int    numa_get_group_id_for_address(const void* address);
  static bool   numa_get_group_ids_for_range(const void** addresses, int* lgrp_ids, size_t count);

  // Page manipulation
  struct page_info {
    size_t size;
    int lgrp_id;
  };
  static char*  non_memory_address_word();
  // reserve, commit and pin the entire memory region
  static char*  reserve_memory_special(size_t size, size_t alignment, size_t page_size,
                                       char* addr, bool executable);
  static bool   release_memory_special(char* addr, size_t bytes);
  static void   large_page_init();
  static size_t large_page_size();
  static bool   can_commit_large_page_memory();

  // Check if pointer points to readable memory (by 4-byte read access)
  static bool    is_readable_pointer(const void* p);
  static bool    is_readable_range(const void* from, const void* to);

  // threads

  enum ThreadType {
    vm_thread,
    gc_thread,         // GC thread
    java_thread,       // Java, JVMTIAgent and Service threads.
    compiler_thread,
    watcher_thread,
    asynclog_thread,   // dedicated to flushing logs
    os_thread
  };

  static bool create_thread(Thread* thread,
                            ThreadType thr_type,
                            size_t req_stack_size = 0);

  // The "main thread", also known as "starting thread", is the thread
  // that loads/creates the JVM via JNI_CreateJavaVM.
  static bool create_main_thread(JavaThread* thread);

  // The primordial thread is the initial process thread. The java
  // launcher never uses the primordial thread as the main thread, but
  // applications that host the JVM directly may do so. Some platforms
  // need special-case handling of the primordial thread if it attaches
  // to the VM.
  static bool is_primordial_thread(void)
#if defined(_WINDOWS) || defined(BSD)
    // No way to identify the primordial thread.
    { return false; }
#else
  ;
#endif

  static bool create_attached_thread(JavaThread* thread);
  static void pd_start_thread(Thread* thread);
  static void start_thread(Thread* thread);

  // Returns true if successful.
  static bool signal_thread(Thread* thread, int sig, const char* reason);

  static void free_thread(OSThread* osthread);

  // thread id on Linux/64bit is 64bit, on Windows it's 32bit
  static intx current_thread_id();
  static int current_process_id();

  // Short standalone OS sleep routines suitable for slow path spin loop.
  // Ignores safepoints/suspension/Thread.interrupt() (so keep it short).
  // ms/ns = 0, will sleep for the least amount of time allowed by the OS.
  // Maximum sleep time is just under 1 second.
  static void naked_short_sleep(jlong ms);
  static void naked_short_nanosleep(jlong ns);
  // Longer standalone OS sleep routine - a convenience wrapper around
  // multiple calls to naked_short_sleep. Only for use by non-JavaThreads.
  static void naked_sleep(jlong millis);
  // Never returns, use with CAUTION
  ATTRIBUTE_NORETURN static void infinite_sleep();
  static void naked_yield () ;
  static OSReturn set_priority(Thread* thread, ThreadPriority priority);
  static OSReturn get_priority(const Thread* const thread, ThreadPriority& priority);

  static address    fetch_frame_from_context(const void* ucVoid, intptr_t** sp, intptr_t** fp);
  static frame      fetch_frame_from_context(const void* ucVoid);
  static frame      fetch_compiled_frame_from_context(const void* ucVoid);

  static void breakpoint();
  static bool start_debugging(char *buf, int buflen);

  static address current_stack_pointer();
  static void current_stack_base_and_size(address* base, size_t* size);

  static void verify_stack_alignment() PRODUCT_RETURN;

  static bool message_box(const char* title, const char* message);

  // run cmd in a separate process and return its exit code; or -1 on failures.
  // Note: only safe to use in fatal error situations.
  static int fork_and_exec(const char *cmd);

  // Call ::exit() on all platforms
  ATTRIBUTE_NORETURN static void exit(int num);

  // Call ::_exit() on all platforms. Similar semantics to die() except we never
  // want a core dump.
  ATTRIBUTE_NORETURN static void _exit(int num);

  // Terminate the VM, but don't exit the process
  static void shutdown();

  // Terminate with an error.  Default is to generate a core file on platforms
  // that support such things.  This calls shutdown() and then aborts.
  ATTRIBUTE_NORETURN static void abort(bool dump_core, void *siginfo, const void *context);
  ATTRIBUTE_NORETURN static void abort(bool dump_core = true);

  // Die immediately, no exit hook, no abort hook, no cleanup.
  // Dump a core file, if possible, for debugging. os::abort() is the
  // preferred means to abort the VM on error. os::die() should only
  // be called if something has gone badly wrong. CreateCoredumpOnCrash
  // is intentionally not honored by this function.
  ATTRIBUTE_NORETURN static void die();

  // File i/o operations
  static int open(const char *path, int oflag, int mode);
  static FILE* fdopen(int fd, const char* mode);
  static FILE* fopen(const char* path, const char* mode);
  static jlong lseek(int fd, jlong offset, int whence);
  static bool file_exists(const char* file);
  // This function, on Windows, canonicalizes a given path (see os_windows.cpp for details).
  // On Posix, this function is a noop: it does not change anything and just returns
  // the input pointer.
  static char* native_path(char *path);
  static int ftruncate(int fd, jlong length);
  static int get_fileno(FILE* fp);
  static void flockfile(FILE* fp);
  static void funlockfile(FILE* fp);

  static int compare_file_modified_times(const char* file1, const char* file2);

  static bool same_files(const char* file1, const char* file2);

  //File i/o operations

  static ssize_t read_at(int fd, void *buf, unsigned int nBytes, jlong offset);
  // Writes the bytes completely. Returns true on success, false otherwise.
  static bool write(int fd, const void *buf, size_t nBytes);

  // Reading directories.
  static DIR*           opendir(const char* dirname);
  static struct dirent* readdir(DIR* dirp);
  static int            closedir(DIR* dirp);

  static const char*    get_temp_directory();
  static const char*    get_current_directory(char *buf, size_t buflen);

  static void           prepare_native_symbols();

  // Builds the platform-specific name of a library.
  // Returns false if the buffer is too small.
  static bool           dll_build_name(char* buffer, size_t size,
                                       const char* fname);

  // Builds a platform-specific full library path given an ld path and
  // unadorned library name. Returns true if the buffer contains a full
  // path to an existing file, false otherwise. If pathname is empty,
  // uses the path to the current directory.
  static bool           dll_locate_lib(char* buffer, size_t size,
                                       const char* pathname, const char* fname);

  // Symbol lookup, find nearest function name; basically it implements
  // dladdr() for all platforms. Name of the nearest function is copied
  // to buf. Distance from its base address is optionally returned as offset.
  // If function name is not found, buf[0] is set to '\0' and offset is
  // set to -1 (if offset is non-null).
  static bool dll_address_to_function_name(address addr, char* buf,
                                           int buflen, int* offset,
                                           bool demangle = true);

  // Locate DLL/DSO. On success, full path of the library is copied to
  // buf, and offset is optionally set to be the distance between addr
  // and the library's base address. On failure, buf[0] is set to '\0'
  // and offset is set to -1 (if offset is non-null).
  static bool dll_address_to_library_name(address addr, char* buf,
                                          int buflen, int* offset);

  // Given an address, attempt to locate both the symbol and the library it
  // resides in. If at least one of these steps was successful, prints information
  // and returns true.
  // - if no scratch buffer is given, stack is used
  // - shorten_paths: path is omitted from library name
  // - demangle: function name is demangled
  // - strip_arguments: arguments are stripped (requires demangle=true)
  // On success prints either one of:
  // "<function name>+<offset> in <library>"
  // "<function name>+<offset>"
  // "<address> in <library>+<offset>"
  static bool print_function_and_library_name(outputStream* st,
                                              address addr,
                                              char* buf = nullptr, int buflen = 0,
                                              bool shorten_paths = true,
                                              bool demangle = true,
                                              bool strip_arguments = false);

  // Used only on PPC.
  inline static void* resolve_function_descriptor(void* p);

  // Find out whether the pc is in the static code for jvm.dll/libjvm.so.
  static bool address_is_in_vm(address addr);

  // Loads .dll/.so and
  // in case of error it checks if .dll/.so was built for the
  // same architecture as HotSpot is running on
  // in case of an error null is returned and an error message is stored in ebuf
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
  static bool find_builtin_agent(JvmtiAgent *agent_lib, const char *syms[],
                                 size_t syms_len);

  // Find agent entry point
  static void *find_agent_function(JvmtiAgent *agent_lib, bool check_lib,
                                   const char *syms[], size_t syms_len);

  // Provide C99 compliant versions of these functions, since some versions
  // of some platforms don't.
  static int vsnprintf(char* buf, size_t len, const char* fmt, va_list args) ATTRIBUTE_PRINTF(3, 0);
  static int snprintf(char* buf, size_t len, const char* fmt, ...) ATTRIBUTE_PRINTF(3, 4);

  // Performs snprintf and asserts the result is non-negative (so there was not
  // an encoding error) and that the output was not truncated.
  static int snprintf_checked(char* buf, size_t len, const char* fmt, ...) ATTRIBUTE_PRINTF(3, 4);

  // Get host name in buffer provided
  static bool get_host_name(char* buf, size_t buflen);

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
  static void print_tos_pc(outputStream* st, const void* context);
  static void print_tos(outputStream* st, address sp);
  static void print_instructions(outputStream* st, address pc, int unitsize = 1);
  static void print_register_info(outputStream* st, const void* context, int& continuation);
  static void print_register_info(outputStream* st, const void* context);
  static bool signal_sent_by_kill(const void* siginfo);
  static void print_siginfo(outputStream* st, const void* siginfo);
  static void print_signal_handlers(outputStream* st, char* buf, size_t buflen);
  static void print_date_and_time(outputStream* st, char* buf, size_t buflen);
  static void print_elapsed_time(outputStream* st, double time);

  static void print_user_info(outputStream* st);
  static void print_active_locale(outputStream* st);

  // helper for output of seconds in days , hours and months
  static void print_dhm(outputStream* st, const char* startStr, long sec);

  static void print_location(outputStream* st, intptr_t x, bool verbose = false);
  static size_t lasterror(char *buf, size_t len);
  static int get_last_error();

  // Send JFR memory info event
  static void jfr_report_memory_info() NOT_JFR_RETURN();

  // Replacement for strerror().
  // Will return the english description of the error (e.g. "File not found", as
  //  suggested in the POSIX standard.
  // Will return "Unknown error" for an unknown errno value.
  // Will not attempt to localize the returned string.
  // Will always return a valid string which is a static constant.
  // Will not change the value of errno.
  static const char* strerror(int e);

  // Will return the literalized version of the given errno (e.g. "EINVAL"
  //  for EINVAL).
  // Will return "Unknown error" for an unknown errno value.
  // Will always return a valid string which is a static constant.
  // Will not change the value of errno.
  static const char* errno_name(int e);

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

  // return current frame. pc() and sp() are set to null on failure.
  static frame      current_frame();

  static void print_hex_dump(outputStream* st, address start, address end, int unitsize,
                             int bytes_per_line, address logical_start);
  static void print_hex_dump(outputStream* st, address start, address end, int unitsize) {
    print_hex_dump(st, start, end, unitsize, /*bytes_per_line=*/16, /*logical_start=*/start);
  }

  // returns a string to describe the exception/signal;
  // returns null if exception_code is not an OS exception/signal.
  static const char* exception_name(int exception_code, char* buf, size_t buflen);

  // Returns the signal number (e.g. 11) for a given signal name (SIGSEGV).
  static int get_signal_number(const char* signal_name);

  // Returns native Java library, loads if necessary
  static void*    native_java_library();

  // Fills in path to jvm.dll/libjvm.so (used by the Disassembler)
  static void     jvm_path(char *buf, jint buflen);

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

  // handles null pointers
  static void  free    (void *memblock);
  static char* strdup(const char *, MEMFLAGS flags = mtInternal);  // Like strdup
  // Like strdup, but exit VM when strdup() returns null
  static char* strdup_check_oom(const char*, MEMFLAGS flags = mtInternal);

  // SocketInterface (ex HPI SocketInterface )
  static int socket_close(int fd);
  static ssize_t recv(int fd, char* buf, size_t nBytes, uint flags);
  static ssize_t send(int fd, char* buf, size_t nBytes, uint flags);
  static ssize_t raw_send(int fd, char* buf, size_t nBytes, uint flags);
  static ssize_t connect(int fd, struct sockaddr* him, socklen_t len);

  // Support for signals
  static void  initialize_jdk_signal_support(TRAPS);
  static void  signal_notify(int signal_number);
  static int   signal_wait();
  static void  terminate_signal_thread();
  static int   sigexitnum_pd();

  // random number generation
  static int random();                     // return 32bit pseudorandom number
  static int next_random(unsigned int rand_seed); // pure version of random()
  static void init_random(unsigned int initval);    // initialize random sequence

  // Structured OS Exception support
  static void os_exception_wrapper(java_call_t f, JavaValue* value, const methodHandle& method, JavaCallArguments* args, JavaThread* thread);

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

  // Thread CPU Time - return the fast estimate on a platform
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

  // Amount beyond the callee frame size that we bang the stack.
  static int extra_bang_size_in_bytes();

  static char** split_path(const char* path, size_t* elements, size_t file_name_length);

  // support for mapping non-volatile memory using MAP_SYNC
  static bool supports_map_sync();

 public:

  // File conventions
  static const char* file_separator();
  static const char* line_separator();
  static const char* path_separator();

  // Information about the protection of the page at address '0' on this os.
  inline static bool zero_page_read_protected();

  static void setup_fpu();
  static juint cpu_microcode_revision();

  static inline jlong rdtsc();

  // Used to register dynamic code cache area with the OS
  // Note: Currently only used in 64 bit Windows implementations
  inline static bool register_code_area(char *low, char *high);

  // Platform-specific code for interacting with individual OSes.
  // TODO: This is for compatibility only with current usage of os::Linux, etc.
  // We can get rid of the following block if we rename such a class to something
  // like ::LinuxUtils
#if defined(AIX)
  class Aix;
#elif defined(BSD)
  class Bsd;
#elif defined(LINUX)
  class Linux;
#elif defined(_WINDOWS)
  class win32;
#endif

  // Ditto - Posix-specific API. Ideally should be moved to something like ::PosixUtils.
#ifndef _WINDOWS
  class Posix;
#endif

  // FIXME - some random stuff that was in os_windows.hpp
#ifdef _WINDOWS
  // strtok_s is the Windows thread-safe equivalent of POSIX strtok_r
# define strtok_r strtok_s
# define S_ISCHR(mode)   (((mode) & _S_IFCHR) == _S_IFCHR)
# define S_ISFIFO(mode)  (((mode) & _S_IFIFO) == _S_IFIFO)
#endif

#ifndef OS_NATIVE_THREAD_CREATION_FAILED_MSG
#define OS_NATIVE_THREAD_CREATION_FAILED_MSG "unable to create native thread: possibly out of memory or process/resource limits reached"
#endif

 public:
  inline static bool platform_print_native_stack(outputStream* st, const void* context,
                                                 char *buf, int buf_size, address& lastpc);

  // debugging support (mostly used by debug.cpp but also fatal error handler)
  static bool find(address pc, outputStream* st = tty); // OS specific function to make sense out of an address

  static bool dont_yield();                     // when true, JVM_Yield() is nop

  // Thread priority helpers (implemented in OS-specific part)
  static OSReturn set_native_priority(Thread* thread, int native_prio);
  static OSReturn get_native_priority(const Thread* const thread, int* priority_ptr);
  static int java_to_os_priority[CriticalPriority + 1];
  // Hint to the underlying OS that a task switch would not be good.
  // Void return because it's a hint and can fail.
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

#if defined(__APPLE__) && defined(AARCH64)
  // Enables write or execute access to writeable and executable pages.
  static void current_thread_enable_wx(WXMode mode);
#endif // __APPLE__ && AARCH64

 protected:
  static volatile unsigned int _rand_seed;    // seed for random number generator
  static int _processor_count;                // number of processors
  static int _initial_active_processor_count; // number of active processors during initialization.

  static char* format_boot_path(const char* format_string,
                                const char* home,
                                int home_len,
                                char fileSep,
                                char pathSep);
  static bool set_boot_path(char fileSep, char pathSep);

  static bool pd_dll_unload(void* libhandle, char* ebuf, int ebuflen);
};

// Note that "PAUSE" is almost always used with synchronization
// so arguably we should provide Atomic::SpinPause() instead
// of the global SpinPause() with C linkage.
// It'd also be eligible for inlining on many platforms.

extern "C" int SpinPause();

#endif // SHARE_RUNTIME_OS_HPP

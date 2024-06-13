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

#include "precompiled.hpp"
#include "cds/cdsConfig.hpp"
#include "classfile/javaClasses.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "code/vtableStubs.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "interpreter/interpreter.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "nmt/mallocHeader.inline.hpp"
#include "nmt/mallocTracker.hpp"
#include "nmt/memTracker.inline.hpp"
#include "nmt/nmtCommon.hpp"
#include "nmt/nmtPreInit.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/oop.inline.hpp"
#include "prims/jvm_misc.hpp"
#include "prims/jvmtiAgent.hpp"
#include "runtime/arguments.hpp"
#include "runtime/atomic.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/java.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.inline.hpp"
#include "runtime/osThread.hpp"
#include "runtime/safefetch.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/threadCrashProtection.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vm_version.hpp"
#include "sanitizers/address.hpp"
#include "services/attachListener.hpp"
#include "services/threadService.hpp"
#include "utilities/align.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/count_trailing_zeros.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/events.hpp"
#include "utilities/fastrand.hpp"
#include "utilities/macros.hpp"
#include "utilities/powerOfTwo.hpp"

#ifndef _WINDOWS
# include <poll.h>
#endif

# include <signal.h>
# include <errno.h>

OSThread*         os::_starting_thread    = nullptr;
volatile unsigned int os::_rand_seed      = 1234567;
int               os::_processor_count    = 0;
int               os::_initial_active_processor_count = 0;
os::PageSizes     os::_page_sizes;

DEBUG_ONLY(bool os::_mutex_init_done = false;)

int os::snprintf(char* buf, size_t len, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  int result = os::vsnprintf(buf, len, fmt, args);
  va_end(args);
  return result;
}

int os::snprintf_checked(char* buf, size_t len, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  int result = os::vsnprintf(buf, len, fmt, args);
  va_end(args);
  assert(result >= 0, "os::snprintf error");
  assert(static_cast<size_t>(result) < len, "os::snprintf truncated");
  return result;
}

int os::vsnprintf(char* buf, size_t len, const char* fmt, va_list args) {
  ALLOW_C_FUNCTION(::vsnprintf, int result = ::vsnprintf(buf, len, fmt, args);)
  // If an encoding error occurred (result < 0) then it's not clear
  // whether the buffer is NUL terminated, so ensure it is.
  if ((result < 0) && (len > 0)) {
    buf[len - 1] = '\0';
  }
  return result;
}

// Fill in buffer with current local time as an ISO-8601 string.
// E.g., YYYY-MM-DDThh:mm:ss.mmm+zzzz.
// Returns buffer, or null if it failed.
char* os::iso8601_time(char* buffer, size_t buffer_length, bool utc) {
  const jlong now = javaTimeMillis();
  return os::iso8601_time(now, buffer, buffer_length, utc);
}

// Fill in buffer with an ISO-8601 string corresponding to the given javaTimeMillis value
// E.g., yyyy-mm-ddThh:mm:ss-zzzz.
// Returns buffer, or null if it failed.
// This would mostly be a call to
//     strftime(...., "%Y-%m-%d" "T" "%H:%M:%S" "%z", ....)
// except that on Windows the %z behaves badly, so we do it ourselves.
// Also, people wanted milliseconds on there,
// and strftime doesn't do milliseconds.
char* os::iso8601_time(jlong milliseconds_since_19700101, char* buffer, size_t buffer_length, bool utc) {
  // Output will be of the form "YYYY-MM-DDThh:mm:ss.mmm+zzzz\0"

  // Sanity check the arguments
  if (buffer == nullptr) {
    assert(false, "null buffer");
    return nullptr;
  }
  if (buffer_length < os::iso8601_timestamp_size) {
    assert(false, "buffer_length too small");
    return nullptr;
  }
  const int milliseconds_per_second = 1000;
  const time_t seconds_since_19700101 =
    milliseconds_since_19700101 / milliseconds_per_second;
  const int milliseconds_after_second =
    checked_cast<int>(milliseconds_since_19700101 % milliseconds_per_second);
  // Convert the time value to a tm and timezone variable
  struct tm time_struct;
  if (utc) {
    if (gmtime_pd(&seconds_since_19700101, &time_struct) == nullptr) {
      assert(false, "Failed gmtime_pd");
      return nullptr;
    }
  } else {
    if (localtime_pd(&seconds_since_19700101, &time_struct) == nullptr) {
      assert(false, "Failed localtime_pd");
      return nullptr;
    }
  }

  const time_t seconds_per_minute = 60;
  const time_t minutes_per_hour = 60;
  const time_t seconds_per_hour = seconds_per_minute * minutes_per_hour;

  // No offset when dealing with UTC
  time_t UTC_to_local = 0;
  if (!utc) {
#if (defined(_ALLBSD_SOURCE) || defined(_GNU_SOURCE)) && !defined(AIX)
    UTC_to_local = -(time_struct.tm_gmtoff);
#elif defined(_WINDOWS)
    long zone;
    _get_timezone(&zone);
    UTC_to_local = static_cast<time_t>(zone);
#else
    UTC_to_local = timezone;
#endif

    // tm_gmtoff already includes adjustment for daylight saving
#if !defined(_ALLBSD_SOURCE) && !defined(_GNU_SOURCE)
    // If daylight savings time is in effect,
    // we are 1 hour East of our time zone
    if (time_struct.tm_isdst > 0) {
      UTC_to_local = UTC_to_local - seconds_per_hour;
    }
#endif
  }

  // Compute the time zone offset.
  //    localtime_pd() sets timezone to the difference (in seconds)
  //    between UTC and local time.
  //    ISO 8601 says we need the difference between local time and UTC,
  //    we change the sign of the localtime_pd() result.
  const time_t local_to_UTC = -(UTC_to_local);
  // Then we have to figure out if if we are ahead (+) or behind (-) UTC.
  char sign_local_to_UTC = '+';
  time_t abs_local_to_UTC = local_to_UTC;
  if (local_to_UTC < 0) {
    sign_local_to_UTC = '-';
    abs_local_to_UTC = -(abs_local_to_UTC);
  }
  // Convert time zone offset seconds to hours and minutes.
  const time_t zone_hours = (abs_local_to_UTC / seconds_per_hour);
  const time_t zone_min =
    ((abs_local_to_UTC % seconds_per_hour) / seconds_per_minute);

  // Print an ISO 8601 date and time stamp into the buffer
  const int year = 1900 + time_struct.tm_year;
  const int month = 1 + time_struct.tm_mon;
  const int printed = jio_snprintf(buffer, buffer_length,
                                   "%04d-%02d-%02dT%02d:%02d:%02d.%03d%c%02d%02d",
                                   year,
                                   month,
                                   time_struct.tm_mday,
                                   time_struct.tm_hour,
                                   time_struct.tm_min,
                                   time_struct.tm_sec,
                                   milliseconds_after_second,
                                   sign_local_to_UTC,
                                   zone_hours,
                                   zone_min);
  if (printed == 0) {
    assert(false, "Failed jio_printf");
    return nullptr;
  }
  return buffer;
}

OSReturn os::set_priority(Thread* thread, ThreadPriority p) {
  debug_only(Thread::check_for_dangling_thread_pointer(thread);)

  if ((p >= MinPriority && p <= MaxPriority) ||
      (p == CriticalPriority && thread->is_ConcurrentGC_thread())) {
    int priority = java_to_os_priority[p];
    return set_native_priority(thread, priority);
  } else {
    assert(false, "Should not happen");
    return OS_ERR;
  }
}

// The mapping from OS priority back to Java priority may be inexact because
// Java priorities can map M:1 with native priorities. If you want the definite
// Java priority then use JavaThread::java_priority()
OSReturn os::get_priority(const Thread* const thread, ThreadPriority& priority) {
  int p;
  int os_prio;
  OSReturn ret = get_native_priority(thread, &os_prio);
  if (ret != OS_OK) return ret;

  if (java_to_os_priority[MaxPriority] > java_to_os_priority[MinPriority]) {
    for (p = MaxPriority; p > MinPriority && java_to_os_priority[p] > os_prio; p--) ;
  } else {
    // niceness values are in reverse order
    for (p = MaxPriority; p > MinPriority && java_to_os_priority[p] < os_prio; p--) ;
  }
  priority = (ThreadPriority)p;
  return OS_OK;
}

bool os::dll_build_name(char* buffer, size_t size, const char* fname) {
  int n = jio_snprintf(buffer, size, "%s%s%s", JNI_LIB_PREFIX, fname, JNI_LIB_SUFFIX);
  return (n != -1);
}

#if !defined(LINUX) && !defined(_WINDOWS)
bool os::committed_in_range(address start, size_t size, address& committed_start, size_t& committed_size) {
  committed_start = start;
  committed_size = size;
  return true;
}
#endif

// Helper for dll_locate_lib.
// Pass buffer and printbuffer as we already printed the path to buffer
// when we called get_current_directory. This way we avoid another buffer
// of size MAX_PATH.
static bool conc_path_file_and_check(char *buffer, char *printbuffer, size_t printbuflen,
                                     const char* pname, char lastchar, const char* fname) {

  // Concatenate path and file name, but don't print double path separators.
  const char *filesep = (WINDOWS_ONLY(lastchar == ':' ||) lastchar == os::file_separator()[0]) ?
                        "" : os::file_separator();
  int ret = jio_snprintf(printbuffer, printbuflen, "%s%s%s", pname, filesep, fname);
  // Check whether file exists.
  if (ret != -1) {
    struct stat statbuf;
    return os::stat(buffer, &statbuf) == 0;
  }
  return false;
}

// Frees all memory allocated on the heap for the
// supplied array of arrays of chars (a), where n
// is the number of elements in the array.
static void free_array_of_char_arrays(char** a, size_t n) {
      while (n > 0) {
          n--;
          if (a[n] != nullptr) {
            FREE_C_HEAP_ARRAY(char, a[n]);
          }
      }
      FREE_C_HEAP_ARRAY(char*, a);
}

bool os::dll_locate_lib(char *buffer, size_t buflen,
                        const char* pname, const char* fname) {
  bool retval = false;

  size_t fullfnamelen = strlen(JNI_LIB_PREFIX) + strlen(fname) + strlen(JNI_LIB_SUFFIX);
  char* fullfname = NEW_C_HEAP_ARRAY(char, fullfnamelen + 1, mtInternal);
  if (dll_build_name(fullfname, fullfnamelen + 1, fname)) {
    const size_t pnamelen = pname ? strlen(pname) : 0;

    if (pnamelen == 0) {
      // If no path given, use current working directory.
      const char* p = get_current_directory(buffer, buflen);
      if (p != nullptr) {
        const size_t plen = strlen(buffer);
        const char lastchar = buffer[plen - 1];
        retval = conc_path_file_and_check(buffer, &buffer[plen], buflen - plen,
                                          "", lastchar, fullfname);
      }
    } else if (strchr(pname, *os::path_separator()) != nullptr) {
      // A list of paths. Search for the path that contains the library.
      size_t n;
      char** pelements = split_path(pname, &n, fullfnamelen);
      if (pelements != nullptr) {
        for (size_t i = 0; i < n; i++) {
          char* path = pelements[i];
          // Really shouldn't be null, but check can't hurt.
          size_t plen = (path == nullptr) ? 0 : strlen(path);
          if (plen == 0) {
            continue; // Skip the empty path values.
          }
          const char lastchar = path[plen - 1];
          retval = conc_path_file_and_check(buffer, buffer, buflen, path, lastchar, fullfname);
          if (retval) break;
        }
        // Release the storage allocated by split_path.
        free_array_of_char_arrays(pelements, n);
      }
    } else {
      // A definite path.
      const char lastchar = pname[pnamelen-1];
      retval = conc_path_file_and_check(buffer, buffer, buflen, pname, lastchar, fullfname);
    }
  }

  FREE_C_HEAP_ARRAY(char*, fullfname);
  return retval;
}

// --------------------- sun.misc.Signal (optional) ---------------------


// SIGBREAK is sent by the keyboard to query the VM state
#ifndef SIGBREAK
#define SIGBREAK SIGQUIT
#endif

// sigexitnum_pd is a platform-specific special signal used for terminating the Signal thread.


static void signal_thread_entry(JavaThread* thread, TRAPS) {
  os::set_priority(thread, NearMaxPriority);
  while (true) {
    int sig;
    {
      // FIXME : Currently we have not decided what should be the status
      //         for this java thread blocked here. Once we decide about
      //         that we should fix this.
      sig = os::signal_wait();
    }
    if (sig == os::sigexitnum_pd()) {
       // Terminate the signal thread
       return;
    }

    switch (sig) {
      case SIGBREAK: {
#if INCLUDE_SERVICES
        // Check if the signal is a trigger to start the Attach Listener - in that
        // case don't print stack traces.
        if (!DisableAttachMechanism) {
          // Attempt to transit state to AL_INITIALIZING.
          AttachListenerState cur_state = AttachListener::transit_state(AL_INITIALIZING, AL_NOT_INITIALIZED);
          if (cur_state == AL_INITIALIZING) {
            // Attach Listener has been started to initialize. Ignore this signal.
            continue;
          } else if (cur_state == AL_NOT_INITIALIZED) {
            // Start to initialize.
            if (AttachListener::is_init_trigger()) {
              // Attach Listener has been initialized.
              // Accept subsequent request.
              continue;
            } else {
              // Attach Listener could not be started.
              // So we need to transit the state to AL_NOT_INITIALIZED.
              AttachListener::set_state(AL_NOT_INITIALIZED);
            }
          } else if (AttachListener::check_socket_file()) {
            // Attach Listener has been started, but unix domain socket file
            // does not exist. So restart Attach Listener.
            continue;
          }
        }
#endif
        // Print stack traces
        // Any SIGBREAK operations added here should make sure to flush
        // the output stream (e.g. tty->flush()) after output.  See 4803766.
        // Each module also prints an extra carriage return after its output.
        VM_PrintThreads op(tty, PrintConcurrentLocks, false /* no extended info */, true /* print JNI handle info */);
        VMThread::execute(&op);
        VM_FindDeadlocks op1(tty);
        VMThread::execute(&op1);
        Universe::print_heap_at_SIGBREAK();
        if (PrintClassHistogram) {
          VM_GC_HeapInspection op1(tty, true /* force full GC before heap inspection */);
          VMThread::execute(&op1);
        }
        if (JvmtiExport::should_post_data_dump()) {
          JvmtiExport::post_data_dump();
        }
        break;
      }
      default: {
        // Dispatch the signal to java
        HandleMark hm(THREAD);
        Klass* klass = SystemDictionary::resolve_or_null(vmSymbols::jdk_internal_misc_Signal(), THREAD);
        if (klass != nullptr) {
          JavaValue result(T_VOID);
          JavaCallArguments args;
          args.push_int(sig);
          JavaCalls::call_static(
            &result,
            klass,
            vmSymbols::dispatch_name(),
            vmSymbols::int_void_signature(),
            &args,
            THREAD
          );
        }
        if (HAS_PENDING_EXCEPTION) {
          // tty is initialized early so we don't expect it to be null, but
          // if it is we can't risk doing an initialization that might
          // trigger additional out-of-memory conditions
          if (tty != nullptr) {
            char klass_name[256];
            char tmp_sig_name[16];
            const char* sig_name = "UNKNOWN";
            InstanceKlass::cast(PENDING_EXCEPTION->klass())->
              name()->as_klass_external_name(klass_name, 256);
            if (os::exception_name(sig, tmp_sig_name, 16) != nullptr)
              sig_name = tmp_sig_name;
            warning("Exception %s occurred dispatching signal %s to handler"
                    "- the VM may need to be forcibly terminated",
                    klass_name, sig_name );
          }
          CLEAR_PENDING_EXCEPTION;
        }
      }
    }
  }
}

void os::init_before_ergo() {
  initialize_initial_active_processor_count();
  // We need to initialize large page support here because ergonomics takes some
  // decisions depending on large page support and the calculated large page size.
  large_page_init();

  StackOverflow::initialize_stack_zone_sizes();

  // VM version initialization identifies some characteristics of the
  // platform that are used during ergonomic decisions.
  VM_Version::init_before_ergo();
}

void os::initialize_jdk_signal_support(TRAPS) {
  if (!ReduceSignalUsage) {
    // Setup JavaThread for processing signals
    const char* name = "Signal Dispatcher";
    Handle thread_oop = JavaThread::create_system_thread_object(name, CHECK);

    JavaThread* thread = new JavaThread(&signal_thread_entry);
    JavaThread::vm_exit_on_osthread_failure(thread);

    JavaThread::start_internal_daemon(THREAD, thread, thread_oop, NearMaxPriority);
  }
}


void os::terminate_signal_thread() {
  if (!ReduceSignalUsage)
    signal_notify(sigexitnum_pd());
}


// --------------------- loading libraries ---------------------

typedef jint (JNICALL *JNI_OnLoad_t)(JavaVM *, void *);
extern struct JavaVM_ main_vm;

static void* _native_java_library = nullptr;

void* os::native_java_library() {
  if (_native_java_library == nullptr) {
    char buffer[JVM_MAXPATHLEN];
    char ebuf[1024];

    // Load java dll
    if (dll_locate_lib(buffer, sizeof(buffer), Arguments::get_dll_dir(),
                       "java")) {
      _native_java_library = dll_load(buffer, ebuf, sizeof(ebuf));
    }
    if (_native_java_library == nullptr) {
      vm_exit_during_initialization("Unable to load native library", ebuf);
    }

#if defined(__OpenBSD__)
    // Work-around OpenBSD's lack of $ORIGIN support by pre-loading libnet.so
    // ignore errors
    if (dll_locate_lib(buffer, sizeof(buffer), Arguments::get_dll_dir(),
                       "net")) {
      dll_load(buffer, ebuf, sizeof(ebuf));
    }
#endif
  }
  return _native_java_library;
}

/*
 * Support for finding Agent_On(Un)Load/Attach<_lib_name> if it exists.
 * If check_lib == true then we are looking for an
 * Agent_OnLoad_lib_name or Agent_OnAttach_lib_name function to determine if
 * this library is statically linked into the image.
 * If check_lib == false then we will look for the appropriate symbol in the
 * executable if agent_lib->is_static_lib() == true or in the shared library
 * referenced by 'handle'.
 */
void* os::find_agent_function(JvmtiAgent *agent_lib, bool check_lib,
                              const char *syms[], size_t syms_len) {
  assert(agent_lib != nullptr, "sanity check");
  const char *lib_name;
  void *handle = agent_lib->os_lib();
  void *entryName = nullptr;
  char *agent_function_name;
  size_t i;

  // If checking then use the agent name otherwise test is_static_lib() to
  // see how to process this lookup
  lib_name = ((check_lib || agent_lib->is_static_lib()) ? agent_lib->name() : nullptr);
  for (i = 0; i < syms_len; i++) {
    agent_function_name = build_agent_function_name(syms[i], lib_name, agent_lib->is_absolute_path());
    if (agent_function_name == nullptr) {
      break;
    }
    entryName = dll_lookup(handle, agent_function_name);
    FREE_C_HEAP_ARRAY(char, agent_function_name);
    if (entryName != nullptr) {
      break;
    }
  }
  return entryName;
}

// See if the passed in agent is statically linked into the VM image.
bool os::find_builtin_agent(JvmtiAgent* agent, const char *syms[],
                            size_t syms_len) {
  void *ret;
  void *proc_handle;
  void *save_handle;

  assert(agent != nullptr, "sanity check");
  if (agent->name() == nullptr) {
    return false;
  }
  proc_handle = get_default_process_handle();
  // Check for Agent_OnLoad/Attach_lib_name function
  save_handle = agent->os_lib();
  // We want to look in this process' symbol table.
  agent->set_os_lib(proc_handle);
  ret = find_agent_function(agent, true, syms, syms_len);
  if (ret != nullptr) {
    // Found an entry point like Agent_OnLoad_lib_name so we have a static agent
    agent->set_static_lib();
    agent->set_loaded();
    return true;
  }
  agent->set_os_lib(save_handle);
  return false;
}

// --------------------- heap allocation utilities ---------------------

char *os::strdup(const char *str, MEMFLAGS flags) {
  size_t size = strlen(str);
  char *dup_str = (char *)malloc(size + 1, flags);
  if (dup_str == nullptr) return nullptr;
  strcpy(dup_str, str);
  return dup_str;
}

char* os::strdup_check_oom(const char* str, MEMFLAGS flags) {
  char* p = os::strdup(str, flags);
  if (p == nullptr) {
    vm_exit_out_of_memory(strlen(str) + 1, OOM_MALLOC_ERROR, "os::strdup_check_oom");
  }
  return p;
}

#ifdef ASSERT
static void check_crash_protection() {
  assert(!ThreadCrashProtection::is_crash_protected(Thread::current_or_null()),
         "not allowed when crash protection is set");
}
static void break_if_ptr_caught(void* ptr) {
  if (p2i(ptr) == (intptr_t)MallocCatchPtr) {
    log_warning(malloc, free)("ptr caught: " PTR_FORMAT, p2i(ptr));
    breakpoint();
  }
}
#endif // ASSERT

void* os::malloc(size_t size, MEMFLAGS flags) {
  return os::malloc(size, flags, CALLER_PC);
}

void* os::malloc(size_t size, MEMFLAGS memflags, const NativeCallStack& stack) {

  // Special handling for NMT preinit phase before arguments are parsed
  void* rc = nullptr;
  if (NMTPreInit::handle_malloc(&rc, size)) {
    // No need to fill with 0 because CDS static dumping doesn't use these
    // early allocations.
    return rc;
  }

  DEBUG_ONLY(check_crash_protection());

  // On malloc(0), implementations of malloc(3) have the choice to return either
  // null or a unique non-null pointer. To unify libc behavior across our platforms
  // we chose the latter.
  size = MAX2((size_t)1, size);

  // Observe MallocLimit
  if (MemTracker::check_exceeds_limit(size, memflags)) {
    return nullptr;
  }

  const size_t outer_size = size + MemTracker::overhead_per_malloc();

  // Check for overflow.
  if (outer_size < size) {
    return nullptr;
  }

  ALLOW_C_FUNCTION(::malloc, void* const outer_ptr = ::malloc(outer_size);)
  if (outer_ptr == nullptr) {
    return nullptr;
  }

  void* const inner_ptr = MemTracker::record_malloc((address)outer_ptr, size, memflags, stack);

  if (CDSConfig::is_dumping_static_archive()) {
    // Need to deterministically fill all the alignment gaps in C++ structures.
    ::memset(inner_ptr, 0, size);
  } else {
    DEBUG_ONLY(::memset(inner_ptr, uninitBlockPad, size);)
  }
  DEBUG_ONLY(break_if_ptr_caught(inner_ptr);)
  return inner_ptr;
}

void* os::realloc(void *memblock, size_t size, MEMFLAGS flags) {
  return os::realloc(memblock, size, flags, CALLER_PC);
}

void* os::realloc(void *memblock, size_t size, MEMFLAGS memflags, const NativeCallStack& stack) {

  // Special handling for NMT preinit phase before arguments are parsed
  void* rc = nullptr;
  if (NMTPreInit::handle_realloc(&rc, memblock, size, memflags)) {
    return rc;
  }

  if (memblock == nullptr) {
    return os::malloc(size, memflags, stack);
  }

  DEBUG_ONLY(check_crash_protection());

  // On realloc(p, 0), implementers of realloc(3) have the choice to return either
  // null or a unique non-null pointer. To unify libc behavior across our platforms
  // we chose the latter.
  size = MAX2((size_t)1, size);

  if (MemTracker::enabled()) {
    // NMT realloc handling

    const size_t new_outer_size = size + MemTracker::overhead_per_malloc();

    // Handle size overflow.
    if (new_outer_size < size) {
      return nullptr;
    }

    const size_t old_size = MallocTracker::malloc_header(memblock)->size();

    // Observe MallocLimit
    if ((size > old_size) && MemTracker::check_exceeds_limit(size - old_size, memflags)) {
      return nullptr;
    }

    // Perform integrity checks on and mark the old block as dead *before* calling the real realloc(3) since it
    // may invalidate the old block, including its header.
    MallocHeader* header = MallocHeader::resolve_checked(memblock);
    assert(memflags == header->flags(), "weird NMT flags mismatch (new:\"%s\" != old:\"%s\")\n",
           NMTUtil::flag_to_name(memflags), NMTUtil::flag_to_name(header->flags()));
    const MallocHeader::FreeInfo free_info = header->free_info();

    header->mark_block_as_dead();

    // the real realloc
    ALLOW_C_FUNCTION(::realloc, void* const new_outer_ptr = ::realloc(header, new_outer_size);)

    if (new_outer_ptr == nullptr) {
      // realloc(3) failed and the block still exists.
      // We have however marked it as dead, revert this change.
      header->revive();
      return nullptr;
    }
    // realloc(3) succeeded, variable header now points to invalid memory and we need to deaccount the old block.
    MemTracker::deaccount(free_info);

    // After a successful realloc(3), we account the resized block with its new size
    // to NMT.
    void* const new_inner_ptr = MemTracker::record_malloc(new_outer_ptr, size, memflags, stack);

#ifdef ASSERT
    assert(old_size == free_info.size, "Sanity");
    if (old_size < size) {
      // We also zap the newly extended region.
      ::memset((char*)new_inner_ptr + old_size, uninitBlockPad, size - old_size);
    }
#endif

    rc = new_inner_ptr;

  } else {

    // NMT disabled.
    ALLOW_C_FUNCTION(::realloc, rc = ::realloc(memblock, size);)
    if (rc == nullptr) {
      return nullptr;
    }

  }

  DEBUG_ONLY(break_if_ptr_caught(rc);)

  return rc;
}

void  os::free(void *memblock) {

  // Special handling for NMT preinit phase before arguments are parsed
  if (NMTPreInit::handle_free(memblock)) {
    return;
  }

  if (memblock == nullptr) {
    return;
  }

  DEBUG_ONLY(break_if_ptr_caught(memblock);)

  // When NMT is enabled this checks for heap overwrites, then deaccounts the old block.
  void* const old_outer_ptr = MemTracker::record_free(memblock);

  ALLOW_C_FUNCTION(::free, ::free(old_outer_ptr);)
}

void os::init_random(unsigned int initval) {
  _rand_seed = initval;
}


int os::next_random(unsigned int rand_seed) {
  /* standard, well-known linear congruential random generator with
   * next_rand = (16807*seed) mod (2**31-1)
   * see
   * (1) "Random Number Generators: Good Ones Are Hard to Find",
   *      S.K. Park and K.W. Miller, Communications of the ACM 31:10 (Oct 1988),
   * (2) "Two Fast Implementations of the 'Minimal Standard' Random
   *     Number Generator", David G. Carta, Comm. ACM 33, 1 (Jan 1990), pp. 87-88.
  */
  const unsigned int a = 16807;
  const unsigned int m = 2147483647;
  const int q = m / a;        assert(q == 127773, "weird math");
  const int r = m % a;        assert(r == 2836, "weird math");

  // compute az=2^31p+q
  unsigned int lo = a * (rand_seed & 0xFFFF);
  unsigned int hi = a * (rand_seed >> 16);
  lo += (hi & 0x7FFF) << 16;

  // if q overflowed, ignore the overflow and increment q
  if (lo > m) {
    lo &= m;
    ++lo;
  }
  lo += hi >> 15;

  // if (p+q) overflowed, ignore the overflow and increment (p+q)
  if (lo > m) {
    lo &= m;
    ++lo;
  }
  return lo;
}

int os::random() {
  // Make updating the random seed thread safe.
  while (true) {
    unsigned int seed = _rand_seed;
    unsigned int rand = next_random(seed);
    if (Atomic::cmpxchg(&_rand_seed, seed, rand, memory_order_relaxed) == seed) {
      return static_cast<int>(rand);
    }
  }
}

// The INITIALIZED state is distinguished from the SUSPENDED state because the
// conditions in which a thread is first started are different from those in which
// a suspension is resumed.  These differences make it hard for us to apply the
// tougher checks when starting threads that we want to do when resuming them.
// However, when start_thread is called as a result of Thread.start, on a Java
// thread, the operation is synchronized on the Java Thread object.  So there
// cannot be a race to start the thread and hence for the thread to exit while
// we are working on it.  Non-Java threads that start Java threads either have
// to do so in a context in which races are impossible, or should do appropriate
// locking.

void os::start_thread(Thread* thread) {
  OSThread* osthread = thread->osthread();
  osthread->set_state(RUNNABLE);
  pd_start_thread(thread);
}

void os::abort(bool dump_core) {
  abort(dump_core && CreateCoredumpOnCrash, nullptr, nullptr);
}

//---------------------------------------------------------------------------
// Helper functions for fatal error handler

bool os::print_function_and_library_name(outputStream* st,
                                         address addr,
                                         char* buf, int buflen,
                                         bool shorten_paths,
                                         bool demangle,
                                         bool strip_arguments) {
  // If no scratch buffer given, allocate one here on stack.
  // (used during error handling; its a coin toss, really, if on-stack allocation
  //  is worse than (raw) C-heap allocation in that case).
  char* p = buf;
  if (p == nullptr) {
    p = (char*)::alloca(O_BUFLEN);
    buflen = O_BUFLEN;
  }
  int offset = 0;
  bool have_function_name = dll_address_to_function_name(addr, p, buflen,
                                                         &offset, demangle);
  bool is_function_descriptor = false;
#ifdef HAVE_FUNCTION_DESCRIPTORS
  // When we deal with a function descriptor instead of a real code pointer, try to
  // resolve it. There is a small chance that a random pointer given to this function
  // may just happen to look like a valid descriptor, but this is rare and worth the
  // risk to see resolved function names. But we will print a little suffix to mark
  // this as a function descriptor for the reader (see below).
  if (!have_function_name && os::is_readable_pointer(addr)) {
    address addr2 = (address)os::resolve_function_descriptor(addr);
    if ((have_function_name = is_function_descriptor =
        dll_address_to_function_name(addr2, p, buflen, &offset, demangle))) {
      addr = addr2;
    }
  }
#endif // HAVE_FUNCTION_DESCRIPTORS

  if (have_function_name) {
    // Print function name, optionally demangled
    if (demangle && strip_arguments) {
      char* args_start = strchr(p, '(');
      if (args_start != nullptr) {
        *args_start = '\0';
      }
    }
    // Print offset. Omit printing if offset is zero, which makes the output
    // more readable if we print function pointers.
    if (offset == 0) {
      st->print("%s", p);
    } else {
      st->print("%s+%d", p, offset);
    }
  } else {
    st->print(PTR_FORMAT, p2i(addr));
  }
  offset = 0;

  const bool have_library_name = dll_address_to_library_name(addr, p, buflen, &offset);
  if (have_library_name) {
    // Cut path parts
    if (shorten_paths) {
      char* p2 = strrchr(p, os::file_separator()[0]);
      if (p2 != nullptr) {
        p = p2 + 1;
      }
    }
    st->print(" in %s", p);
    if (!have_function_name) { // Omit offset if we already printed the function offset
      st->print("+%d", offset);
    }
  }

  // Write a trailing marker if this was a function descriptor
  if (have_function_name && is_function_descriptor) {
    st->print_raw(" (FD)");
  }

  return have_function_name || have_library_name;
}

ATTRIBUTE_NO_ASAN static bool read_safely_from(intptr_t* p, intptr_t* result) {
  const intptr_t errval = 0x1717;
  intptr_t i = SafeFetchN(p, errval);
  if (i == errval) {
    i = SafeFetchN(p, ~errval);
    if (i == ~errval) {
      return false;
    }
  }
  (*result) = i;
  return true;
}

static void print_hex_location(outputStream* st, address p, int unitsize) {
  assert(is_aligned(p, unitsize), "Unaligned");
  address pa = align_down(p, sizeof(intptr_t));
#ifndef _LP64
  // Special handling for printing qwords on 32-bit platforms
  if (unitsize == 8) {
    intptr_t i1, i2;
    if (read_safely_from((intptr_t*)pa, &i1) &&
        read_safely_from((intptr_t*)pa + 1, &i2)) {
      const uint64_t value =
        LITTLE_ENDIAN_ONLY((((uint64_t)i2) << 32) | i1)
        BIG_ENDIAN_ONLY((((uint64_t)i1) << 32) | i2);
      st->print("%016" FORMAT64_MODIFIER "x", value);
    } else {
      st->print_raw("????????????????");
    }
    return;
  }
#endif // 32-bit, qwords
  intptr_t i = 0;
  if (read_safely_from((intptr_t*)pa, &i)) {
    // bytes:   CA FE BA BE DE AD C0 DE
    // bytoff:   0  1  2  3  4  5  6  7
    // LE bits:  0  8 16 24 32 40 48 56
    // BE bits: 56 48 40 32 24 16  8  0
    const int offset = (int)(p - (address)pa);
    const int bitoffset =
      LITTLE_ENDIAN_ONLY(offset * BitsPerByte)
      BIG_ENDIAN_ONLY((int)((sizeof(intptr_t) - unitsize - offset) * BitsPerByte));
    const int bitfieldsize = unitsize * BitsPerByte;
    intptr_t value = bitfield(i, bitoffset, bitfieldsize);
    switch (unitsize) {
      case 1: st->print("%02x", (u1)value); break;
      case 2: st->print("%04x", (u2)value); break;
      case 4: st->print("%08x", (u4)value); break;
      case 8: st->print("%016" FORMAT64_MODIFIER "x", (u8)value); break;
    }
  } else {
    switch (unitsize) {
      case 1: st->print_raw("??"); break;
      case 2: st->print_raw("????"); break;
      case 4: st->print_raw("????????"); break;
      case 8: st->print_raw("????????????????"); break;
    }
  }
}

void os::print_hex_dump(outputStream* st, address start, address end, int unitsize,
                        int bytes_per_line, address logical_start) {
  assert(unitsize == 1 || unitsize == 2 || unitsize == 4 || unitsize == 8, "just checking");

  start = align_down(start, unitsize);
  logical_start = align_down(logical_start, unitsize);
  bytes_per_line = align_up(bytes_per_line, 8);

  int cols = 0;
  int cols_per_line = bytes_per_line / unitsize;

  address p = start;
  address logical_p = logical_start;

  // Print out the addresses as if we were starting from logical_start.
  st->print(PTR_FORMAT ":   ", p2i(logical_p));
  while (p < end) {
    print_hex_location(st, p, unitsize);
    p += unitsize;
    logical_p += unitsize;
    cols++;
    if (cols >= cols_per_line && p < end) {
       cols = 0;
       st->cr();
       st->print(PTR_FORMAT ":   ", p2i(logical_p));
    } else {
       st->print(" ");
    }
  }
  st->cr();
}

void os::print_dhm(outputStream* st, const char* startStr, long sec) {
  long days    = sec/86400;
  long hours   = (sec/3600) - (days * 24);
  long minutes = (sec/60) - (days * 1440) - (hours * 60);
  if (startStr == nullptr) startStr = "";
  st->print_cr("%s %ld days %ld:%02ld hours", startStr, days, hours, minutes);
}

void os::print_tos(outputStream* st, address sp) {
  st->print_cr("Top of Stack: (sp=" PTR_FORMAT ")", p2i(sp));
  print_hex_dump(st, sp, sp + 512, sizeof(intptr_t));
}

void os::print_instructions(outputStream* st, address pc, int unitsize) {
  st->print_cr("Instructions: (pc=" PTR_FORMAT ")", p2i(pc));
  print_hex_dump(st, pc - 256, pc + 256, unitsize);
}

void os::print_environment_variables(outputStream* st, const char** env_list) {
  if (env_list) {
    st->print_cr("Environment Variables:");

    for (int i = 0; env_list[i] != nullptr; i++) {
      char *envvar = ::getenv(env_list[i]);
      if (envvar != nullptr) {
        st->print("%s", env_list[i]);
        st->print("=");
        st->print("%s", envvar);
        // Use separate cr() printing to avoid unnecessary buffer operations that might cause truncation.
        st->cr();
      }
    }
  }
}

void os::print_register_info(outputStream* st, const void* context) {
  int continuation = 0;
  print_register_info(st, context, continuation);
}

void os::print_cpu_info(outputStream* st, char* buf, size_t buflen) {
  // cpu
  st->print("CPU:");
#if defined(__APPLE__) && !defined(ZERO)
   if (VM_Version::is_cpu_emulated()) {
     st->print(" (EMULATED)");
   }
#endif
  st->print(" total %d", os::processor_count());
  // It's not safe to query number of active processors after crash
  // st->print("(active %d)", os::active_processor_count()); but we can
  // print the initial number of active processors.
  // We access the raw value here because the assert in the accessor will
  // fail if the crash occurs before initialization of this value.
  st->print(" (initial active %d)", _initial_active_processor_count);
  st->print(" %s", VM_Version::features_string());
  st->cr();
  pd_print_cpu_info(st, buf, buflen);
}

// Print a one line string summarizing the cpu, number of cores, memory, and operating system version
void os::print_summary_info(outputStream* st, char* buf, size_t buflen) {
  st->print("Host: ");
#ifndef PRODUCT
  if (get_host_name(buf, buflen)) {
    st->print("%s, ", buf);
  }
#endif // PRODUCT
  get_summary_cpu_info(buf, buflen);
  st->print("%s, ", buf);
  size_t mem = physical_memory()/G;
  if (mem == 0) {  // for low memory systems
    mem = physical_memory()/M;
    st->print("%d cores, " SIZE_FORMAT "M, ", processor_count(), mem);
  } else {
    st->print("%d cores, " SIZE_FORMAT "G, ", processor_count(), mem);
  }
  get_summary_os_info(buf, buflen);
  st->print_raw(buf);
  st->cr();
}

static constexpr int secs_per_day  = 86400;
static constexpr int secs_per_hour = 3600;
static constexpr int secs_per_min  = 60;

void os::print_date_and_time(outputStream *st, char* buf, size_t buflen) {

  time_t tloc;
  (void)time(&tloc);
  char* timestring = ctime(&tloc);  // ctime adds newline.
  // edit out the newline
  char* nl = strchr(timestring, '\n');
  if (nl != nullptr) {
    *nl = '\0';
  }

  struct tm tz;
  if (localtime_pd(&tloc, &tz) != nullptr) {
    wchar_t w_buf[80];
    size_t n = ::wcsftime(w_buf, 80, L"%Z", &tz);
    if (n > 0) {
      ::wcstombs(buf, w_buf, buflen);
      st->print("Time: %s %s", timestring, buf);
    } else {
      st->print("Time: %s", timestring);
    }
  } else {
    st->print("Time: %s", timestring);
  }

  double t = os::elapsedTime();
  st->print(" elapsed time: ");
  print_elapsed_time(st, t);
  st->cr();
}

void os::print_elapsed_time(outputStream* st, double time) {
  // NOTE: a crash using printf("%f",...) on Linux was historically noted here.
  int eltime = (int)time;  // elapsed time in seconds
  int eltimeFraction = (int) ((time - eltime) * 1000000);

  // print elapsed time in a human-readable format:
  int eldays = eltime / secs_per_day;
  int day_secs = eldays * secs_per_day;
  int elhours = (eltime - day_secs) / secs_per_hour;
  int hour_secs = elhours * secs_per_hour;
  int elmins = (eltime - day_secs - hour_secs) / secs_per_min;
  int minute_secs = elmins * secs_per_min;
  int elsecs = (eltime - day_secs - hour_secs - minute_secs);
  st->print("%d.%06d seconds (%dd %dh %dm %ds)", eltime, eltimeFraction, eldays, elhours, elmins, elsecs);
}


// Check if pointer can be read from (4-byte read access).
// Helps to prove validity of a non-null pointer.
// Returns true in very early stages of VM life when stub is not yet generated.
bool os::is_readable_pointer(const void* p) {
  int* const aligned = (int*) align_down((intptr_t)p, 4);
  int cafebabe = 0xcafebabe;  // tester value 1
  int deadbeef = 0xdeadbeef;  // tester value 2
  return (SafeFetch32(aligned, cafebabe) != cafebabe) || (SafeFetch32(aligned, deadbeef) != deadbeef);
}

bool os::is_readable_range(const void* from, const void* to) {
  if ((uintptr_t)from >= (uintptr_t)to) return false;
  for (uintptr_t p = align_down((uintptr_t)from, min_page_size()); p < (uintptr_t)to; p += min_page_size()) {
    if (!is_readable_pointer((const void*)p)) {
      return false;
    }
  }
  return true;
}


// moved from debug.cpp (used to be find()) but still called from there
// The verbose parameter is only set by the debug code in one case
void os::print_location(outputStream* st, intptr_t x, bool verbose) {
  address addr = (address)x;
  // Handle null first, so later checks don't need to protect against it.
  if (addr == nullptr) {
    st->print_cr("0x0 is null");
    return;
  }

  // Check if addr points into a code blob.
  CodeBlob* b = CodeCache::find_blob(addr);
  if (b != nullptr) {
    b->dump_for_addr(addr, st, verbose);
    return;
  }

  // Check if addr points into Java heap.
  if (Universe::heap()->print_location(st, addr)) {
    return;
  }

#if !INCLUDE_ASAN

  bool accessible = is_readable_pointer(addr);

  // Check if addr is a JNI handle.
  if (align_down((intptr_t)addr, sizeof(intptr_t)) != 0 && accessible) {
    if (JNIHandles::is_global_handle((jobject) addr)) {
      st->print_cr(INTPTR_FORMAT " is a global jni handle", p2i(addr));
      return;
    }
    if (JNIHandles::is_weak_global_handle((jobject) addr)) {
      st->print_cr(INTPTR_FORMAT " is a weak global jni handle", p2i(addr));
      return;
    }
  }

  // Check if addr belongs to a Java thread.
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *thread = jtiwh.next(); ) {
    // If the addr is a java thread print information about that.
    if (addr == (address)thread) {
      if (verbose) {
        thread->print_on(st);
      } else {
        st->print_cr(INTPTR_FORMAT " is a thread", p2i(addr));
      }
      return;
    }
    // If the addr is in the stack region for this thread then report that
    // and print thread info
    if (thread->is_in_full_stack(addr)) {
      st->print_cr(INTPTR_FORMAT " is pointing into the stack for thread: "
                   INTPTR_FORMAT, p2i(addr), p2i(thread));
      if (verbose) thread->print_on(st);
      return;
    }
  }

  // Check if in metaspace and print types that have vptrs
  if (Metaspace::contains(addr)) {
    if (Klass::is_valid((Klass*)addr)) {
      st->print_cr(INTPTR_FORMAT " is a pointer to class: ", p2i(addr));
      ((Klass*)addr)->print_on(st);
    } else if (Method::is_valid_method((const Method*)addr)) {
      ((Method*)addr)->print_value_on(st);
      st->cr();
    } else {
      // Use addr->print() from the debugger instead (not here)
      st->print_cr(INTPTR_FORMAT " is pointing into metadata", p2i(addr));
    }
    return;
  }

  // Compressed klass needs to be decoded first.
#ifdef _LP64
  if (UseCompressedClassPointers && ((uintptr_t)addr &~ (uintptr_t)max_juint) == 0) {
    narrowKlass narrow_klass = (narrowKlass)(uintptr_t)addr;
    Klass* k = CompressedKlassPointers::decode_without_asserts(narrow_klass);

    if (Klass::is_valid(k)) {
      st->print_cr(UINT32_FORMAT " is a compressed pointer to class: " INTPTR_FORMAT, narrow_klass, p2i((HeapWord*)k));
      k->print_on(st);
      return;
    }
  }
#endif

  // Still nothing? If NMT is enabled, we can ask what it thinks...
  if (MemTracker::print_containing_region(addr, st)) {
    return;
  }

  // Try an OS specific find
  if (os::find(addr, st)) {
    return;
  }

  if (accessible) {
    st->print(INTPTR_FORMAT " points into unknown readable memory:", p2i(addr));
    if (is_aligned(addr, sizeof(intptr_t))) {
      st->print(" " PTR_FORMAT " |", *(intptr_t*)addr);
    }
    for (address p = addr; p < align_up(addr + 1, sizeof(intptr_t)); ++p) {
      st->print(" %02x", *(u1*)p);
    }
    st->cr();
    return;
  }

#endif // !INCLUDE_ASAN

  st->print_cr(INTPTR_FORMAT " is an unknown value", p2i(addr));

}

static bool is_pointer_bad(intptr_t* ptr) {
  return !is_aligned(ptr, sizeof(uintptr_t)) || !os::is_readable_pointer(ptr);
}

// Looks like all platforms can use the same function to check if C
// stack is walkable beyond current frame.
// Returns true if this is not the case, i.e. the frame is possibly
// the first C frame on the stack.
bool os::is_first_C_frame(frame* fr) {

#ifdef _WINDOWS
  return true; // native stack isn't walkable on windows this way.
#endif
  // Load up sp, fp, sender sp and sender fp, check for reasonable values.
  // Check usp first, because if that's bad the other accessors may fault
  // on some architectures.  Ditto ufp second, etc.

  if (is_pointer_bad(fr->sp())) return true;

  uintptr_t ufp    = (uintptr_t)fr->fp();
  if (is_pointer_bad(fr->fp())) return true;

  uintptr_t old_sp = (uintptr_t)fr->sender_sp();
  if ((uintptr_t)fr->sender_sp() == (uintptr_t)-1 || is_pointer_bad(fr->sender_sp())) return true;

  uintptr_t old_fp = (uintptr_t)fr->link_or_null();
  if (old_fp == 0 || old_fp == (uintptr_t)-1 || old_fp == ufp ||
    is_pointer_bad(fr->link_or_null())) return true;

  // stack grows downwards; if old_fp is below current fp or if the stack
  // frame is too large, either the stack is corrupted or fp is not saved
  // on stack (i.e. on x86, ebp may be used as general register). The stack
  // is not walkable beyond current frame.
  if (old_fp < ufp) return true;
  if (old_fp - ufp > 64 * K) return true;

  return false;
}

// Set up the boot classpath.

char* os::format_boot_path(const char* format_string,
                           const char* home,
                           int home_len,
                           char fileSep,
                           char pathSep) {
    assert((fileSep == '/' && pathSep == ':') ||
           (fileSep == '\\' && pathSep == ';'), "unexpected separator chars");

    // Scan the format string to determine the length of the actual
    // boot classpath, and handle platform dependencies as well.
    int formatted_path_len = 0;
    const char* p;
    for (p = format_string; *p != 0; ++p) {
        if (*p == '%') formatted_path_len += home_len - 1;
        ++formatted_path_len;
    }

    char* formatted_path = NEW_C_HEAP_ARRAY(char, formatted_path_len + 1, mtInternal);

    // Create boot classpath from format, substituting separator chars and
    // java home directory.
    char* q = formatted_path;
    for (p = format_string; *p != 0; ++p) {
        switch (*p) {
        case '%':
            strcpy(q, home);
            q += home_len;
            break;
        case '/':
            *q++ = fileSep;
            break;
        case ':':
            *q++ = pathSep;
            break;
        default:
            *q++ = *p;
        }
    }
    *q = '\0';

    assert((q - formatted_path) == formatted_path_len, "formatted_path size botched");
    return formatted_path;
}

// This function is a proxy to fopen, it tries to add a non standard flag ('e' or 'N')
// that ensures automatic closing of the file on exec. If it can not find support in
// the underlying c library, it will make an extra system call (fcntl) to ensure automatic
// closing of the file on exec.
FILE* os::fopen(const char* path, const char* mode) {
  char modified_mode[20];
  assert(strlen(mode) + 1 < sizeof(modified_mode), "mode chars plus one extra must fit in buffer");
  os::snprintf_checked(modified_mode, sizeof(modified_mode), "%s" LINUX_ONLY("e") BSD_ONLY("e") WINDOWS_ONLY("N"), mode);
  FILE* file = ::fopen(path, modified_mode);

#if !(defined LINUX || defined BSD || defined _WINDOWS)
  // assume fcntl FD_CLOEXEC support as a backup solution when 'e' or 'N'
  // is not supported as mode in fopen
  if (file != nullptr) {
    int fd = fileno(file);
    if (fd != -1) {
      int fd_flags = fcntl(fd, F_GETFD);
      if (fd_flags != -1) {
        fcntl(fd, F_SETFD, fd_flags | FD_CLOEXEC);
      }
    }
  }
#endif

  return file;
}

bool os::set_boot_path(char fileSep, char pathSep) {
  const char* home = Arguments::get_java_home();
  int home_len = (int)strlen(home);

  struct stat st;

  // modular image if "modules" jimage exists
  char* jimage = format_boot_path("%/lib/" MODULES_IMAGE_NAME, home, home_len, fileSep, pathSep);
  if (jimage == nullptr) return false;
  bool has_jimage = (os::stat(jimage, &st) == 0);
  if (has_jimage) {
    Arguments::set_boot_class_path(jimage, true);
    FREE_C_HEAP_ARRAY(char, jimage);
    return true;
  }
  FREE_C_HEAP_ARRAY(char, jimage);

  // check if developer build with exploded modules
  char* base_classes = format_boot_path("%/modules/" JAVA_BASE_NAME, home, home_len, fileSep, pathSep);
  if (base_classes == nullptr) return false;
  if (os::stat(base_classes, &st) == 0) {
    Arguments::set_boot_class_path(base_classes, false);
    FREE_C_HEAP_ARRAY(char, base_classes);
    return true;
  }
  FREE_C_HEAP_ARRAY(char, base_classes);

  return false;
}

bool os::file_exists(const char* filename) {
  struct stat statbuf;
  if (filename == nullptr || strlen(filename) == 0) {
    return false;
  }
  return os::stat(filename, &statbuf) == 0;
}

bool os::write(int fd, const void *buf, size_t nBytes) {
  ssize_t res;

  while (nBytes > 0) {
    res = pd_write(fd, buf, nBytes);
    if (res == OS_ERR) {
      return false;
    }
    buf = (void *)((char *)buf + res);
    nBytes -= res;
  }

  return true;
}


// Splits a path, based on its separator, the number of
// elements is returned back in "elements".
// file_name_length is used as a modifier for each path's
// length when compared to JVM_MAXPATHLEN. So if you know
// each returned path will have something appended when
// in use, you can pass the length of that in
// file_name_length, to ensure we detect if any path
// exceeds the maximum path length once prepended onto
// the sub-path/file name.
// It is the callers responsibility to:
//   a> check the value of "elements", which may be 0.
//   b> ignore any empty path elements
//   c> free up the data.
char** os::split_path(const char* path, size_t* elements, size_t file_name_length) {
  *elements = (size_t)0;
  if (path == nullptr || strlen(path) == 0 || file_name_length == (size_t)nullptr) {
    return nullptr;
  }
  const char psepchar = *os::path_separator();
  char* inpath = NEW_C_HEAP_ARRAY(char, strlen(path) + 1, mtInternal);
  strcpy(inpath, path);
  size_t count = 1;
  char* p = strchr(inpath, psepchar);
  // Get a count of elements to allocate memory
  while (p != nullptr) {
    count++;
    p++;
    p = strchr(p, psepchar);
  }

  char** opath = NEW_C_HEAP_ARRAY(char*, count, mtInternal);

  // do the actual splitting
  p = inpath;
  for (size_t i = 0 ; i < count ; i++) {
    size_t len = strcspn(p, os::path_separator());
    if (len + file_name_length > JVM_MAXPATHLEN) {
      // release allocated storage before exiting the vm
      free_array_of_char_arrays(opath, i++);
      vm_exit_during_initialization("The VM tried to use a path that exceeds the maximum path length for "
                                    "this system. Review path-containing parameters and properties, such as "
                                    "sun.boot.library.path, to identify potential sources for this path.");
    }
    // allocate the string and add terminator storage
    char* s = NEW_C_HEAP_ARRAY(char, len + 1, mtInternal);
    strncpy(s, p, len);
    s[len] = '\0';
    opath[i] = s;
    p += len + 1;
  }
  FREE_C_HEAP_ARRAY(char, inpath);
  *elements = count;
  return opath;
}

// Returns true if the current stack pointer is above the stack shadow
// pages, false otherwise.
bool os::stack_shadow_pages_available(Thread *thread, const methodHandle& method, address sp) {
  if (!thread->is_Java_thread()) return false;
  // Check if we have StackShadowPages above the guard zone. This parameter
  // is dependent on the depth of the maximum VM call stack possible from
  // the handler for stack overflow.  'instanceof' in the stack overflow
  // handler or a println uses at least 8k stack of VM and native code
  // respectively.
  const int framesize_in_bytes =
    Interpreter::size_top_interpreter_activation(method()) * wordSize;

  address limit = JavaThread::cast(thread)->stack_overflow_state()->shadow_zone_safe_limit();
  return sp > (limit + framesize_in_bytes);
}

size_t os::page_size_for_region(size_t region_size, size_t min_pages, bool must_be_aligned) {
  assert(min_pages > 0, "sanity");
  if (UseLargePages) {
    const size_t max_page_size = region_size / min_pages;

    for (size_t page_size = page_sizes().largest(); page_size != 0;
         page_size = page_sizes().next_smaller(page_size)) {
      if (page_size <= max_page_size) {
        if (!must_be_aligned || is_aligned(region_size, page_size)) {
          return page_size;
        }
      }
    }
  }

  return vm_page_size();
}

size_t os::page_size_for_region_aligned(size_t region_size, size_t min_pages) {
  return page_size_for_region(region_size, min_pages, true);
}

size_t os::page_size_for_region_unaligned(size_t region_size, size_t min_pages) {
  return page_size_for_region(region_size, min_pages, false);
}

#ifndef MAX_PATH
#define MAX_PATH    (2 * K)
#endif

void os::pause() {
  char filename[MAX_PATH];
  if (PauseAtStartupFile && PauseAtStartupFile[0]) {
    jio_snprintf(filename, MAX_PATH, "%s", PauseAtStartupFile);
  } else {
    jio_snprintf(filename, MAX_PATH, "./vm.paused.%d", current_process_id());
  }

  int fd = ::open(filename, O_WRONLY | O_CREAT | O_TRUNC, 0666);
  if (fd != -1) {
    struct stat buf;
    ::close(fd);
    while (::stat(filename, &buf) == 0) {
#if defined(_WINDOWS)
      Sleep(100);
#else
      (void)::poll(nullptr, 0, 100);
#endif
    }
  } else {
    jio_fprintf(stderr,
                "Could not open pause file '%s', continuing immediately.\n", filename);
  }
}

static const char* errno_to_string (int e, bool short_text) {
  #define ALL_SHARED_ENUMS(X) \
    X(E2BIG, "Argument list too long") \
    X(EACCES, "Permission denied") \
    X(EADDRINUSE, "Address in use") \
    X(EADDRNOTAVAIL, "Address not available") \
    X(EAFNOSUPPORT, "Address family not supported") \
    X(EAGAIN, "Resource unavailable, try again") \
    X(EALREADY, "Connection already in progress") \
    X(EBADF, "Bad file descriptor") \
    X(EBADMSG, "Bad message") \
    X(EBUSY, "Device or resource busy") \
    X(ECANCELED, "Operation canceled") \
    X(ECHILD, "No child processes") \
    X(ECONNABORTED, "Connection aborted") \
    X(ECONNREFUSED, "Connection refused") \
    X(ECONNRESET, "Connection reset") \
    X(EDEADLK, "Resource deadlock would occur") \
    X(EDESTADDRREQ, "Destination address required") \
    X(EDOM, "Mathematics argument out of domain of function") \
    X(EEXIST, "File exists") \
    X(EFAULT, "Bad address") \
    X(EFBIG, "File too large") \
    X(EHOSTUNREACH, "Host is unreachable") \
    X(EIDRM, "Identifier removed") \
    X(EILSEQ, "Illegal byte sequence") \
    X(EINPROGRESS, "Operation in progress") \
    X(EINTR, "Interrupted function") \
    X(EINVAL, "Invalid argument") \
    X(EIO, "I/O error") \
    X(EISCONN, "Socket is connected") \
    X(EISDIR, "Is a directory") \
    X(ELOOP, "Too many levels of symbolic links") \
    X(EMFILE, "Too many open files") \
    X(EMLINK, "Too many links") \
    X(EMSGSIZE, "Message too large") \
    X(ENAMETOOLONG, "Filename too long") \
    X(ENETDOWN, "Network is down") \
    X(ENETRESET, "Connection aborted by network") \
    X(ENETUNREACH, "Network unreachable") \
    X(ENFILE, "Too many files open in system") \
    X(ENOBUFS, "No buffer space available") \
    X(ENODATA, "No message is available on the STREAM head read queue") \
    X(ENODEV, "No such device") \
    X(ENOENT, "No such file or directory") \
    X(ENOEXEC, "Executable file format error") \
    X(ENOLCK, "No locks available") \
    X(ENOLINK, "Reserved") \
    X(ENOMEM, "Not enough space") \
    X(ENOMSG, "No message of the desired type") \
    X(ENOPROTOOPT, "Protocol not available") \
    X(ENOSPC, "No space left on device") \
    X(ENOSR, "No STREAM resources") \
    X(ENOSTR, "Not a STREAM") \
    X(ENOSYS, "Function not supported") \
    X(ENOTCONN, "The socket is not connected") \
    X(ENOTDIR, "Not a directory") \
    X(ENOTEMPTY, "Directory not empty") \
    X(ENOTSOCK, "Not a socket") \
    X(ENOTSUP, "Not supported") \
    X(ENOTTY, "Inappropriate I/O control operation") \
    X(ENXIO, "No such device or address") \
    X(EOPNOTSUPP, "Operation not supported on socket") \
    X(EOVERFLOW, "Value too large to be stored in data type") \
    X(EPERM, "Operation not permitted") \
    X(EPIPE, "Broken pipe") \
    X(EPROTO, "Protocol error") \
    X(EPROTONOSUPPORT, "Protocol not supported") \
    X(EPROTOTYPE, "Protocol wrong type for socket") \
    X(ERANGE, "Result too large") \
    X(EROFS, "Read-only file system") \
    X(ESPIPE, "Invalid seek") \
    X(ESRCH, "No such process") \
    X(ETIME, "Stream ioctl() timeout") \
    X(ETIMEDOUT, "Connection timed out") \
    X(ETXTBSY, "Text file busy") \
    X(EWOULDBLOCK, "Operation would block") \
    X(EXDEV, "Cross-device link")

  #define DEFINE_ENTRY(e, text) { e, #e, text },

  static const struct {
    int v;
    const char* short_text;
    const char* long_text;
  } table [] = {

    ALL_SHARED_ENUMS(DEFINE_ENTRY)

    // The following enums are not defined on all platforms.
    #ifdef ESTALE
    DEFINE_ENTRY(ESTALE, "Reserved")
    #endif
    #ifdef EDQUOT
    DEFINE_ENTRY(EDQUOT, "Reserved")
    #endif
    #ifdef EMULTIHOP
    DEFINE_ENTRY(EMULTIHOP, "Reserved")
    #endif

    // End marker.
    { -1, "Unknown errno", "Unknown error" }

  };

  #undef DEFINE_ENTRY
  #undef ALL_FLAGS

  int i = 0;
  while (table[i].v != -1 && table[i].v != e) {
    i ++;
  }

  return short_text ? table[i].short_text : table[i].long_text;

}

const char* os::strerror(int e) {
  return errno_to_string(e, false);
}

const char* os::errno_name(int e) {
  return errno_to_string(e, true);
}

// create binary file, rewriting existing file if required
int os::create_binary_file(const char* path, bool rewrite_existing) {
  int oflags = O_WRONLY | O_CREAT WINDOWS_ONLY(| O_BINARY);
  oflags |= rewrite_existing ? O_TRUNC : O_EXCL;
  return ::open(path, oflags, S_IREAD | S_IWRITE);
}

#define trace_page_size_params(size) byte_size_in_exact_unit(size), exact_unit_for_byte_size(size)

void os::trace_page_sizes(const char* str,
                          const size_t region_min_size,
                          const size_t region_max_size,
                          const char* base,
                          const size_t size,
                          const size_t page_size) {

  log_info(pagesize)("%s: "
                     " min=" SIZE_FORMAT "%s"
                     " max=" SIZE_FORMAT "%s"
                     " base=" PTR_FORMAT
                     " size=" SIZE_FORMAT "%s"
                     " page_size=" SIZE_FORMAT "%s",
                     str,
                     trace_page_size_params(region_min_size),
                     trace_page_size_params(region_max_size),
                     p2i(base),
                     trace_page_size_params(size),
                     trace_page_size_params(page_size));
}

void os::trace_page_sizes_for_requested_size(const char* str,
                                             const size_t requested_size,
                                             const size_t requested_page_size,
                                             const char* base,
                                             const size_t size,
                                             const size_t page_size) {

  log_info(pagesize)("%s:"
                     " req_size=" SIZE_FORMAT "%s"
                     " req_page_size=" SIZE_FORMAT "%s"
                     " base=" PTR_FORMAT
                     " size=" SIZE_FORMAT "%s"
                     " page_size=" SIZE_FORMAT "%s",
                     str,
                     trace_page_size_params(requested_size),
                     trace_page_size_params(requested_page_size),
                     p2i(base),
                     trace_page_size_params(size),
                     trace_page_size_params(page_size));
}


// This is the working definition of a server class machine:
// >= 2 physical CPU's and >=2GB of memory, with some fuzz
// because the graphics memory (?) sometimes masks physical memory.
// If you want to change the definition of a server class machine
// on some OS or platform, e.g., >=4GB on Windows platforms,
// then you'll have to parameterize this method based on that state,
// as was done for logical processors here, or replicate and
// specialize this method for each platform.  (Or fix os to have
// some inheritance structure and use subclassing.  Sigh.)
// If you want some platform to always or never behave as a server
// class machine, change the setting of AlwaysActAsServerClassMachine
// and NeverActAsServerClassMachine in globals*.hpp.
bool os::is_server_class_machine() {
  // First check for the early returns
  if (NeverActAsServerClassMachine) {
    return false;
  }
  if (AlwaysActAsServerClassMachine) {
    return true;
  }
  // Then actually look at the machine
  bool         result            = false;
  const unsigned int    server_processors = 2;
  const julong server_memory     = 2UL * G;
  // We seem not to get our full complement of memory.
  //     We allow some part (1/8?) of the memory to be "missing",
  //     based on the sizes of DIMMs, and maybe graphics cards.
  const julong missing_memory   = 256UL * M;

  /* Is this a server class machine? */
  if ((os::active_processor_count() >= (int)server_processors) &&
      (os::physical_memory() >= (server_memory - missing_memory))) {
    const unsigned int logical_processors =
      VM_Version::logical_processors_per_package();
    if (logical_processors > 1) {
      const unsigned int physical_packages =
        os::active_processor_count() / logical_processors;
      if (physical_packages >= server_processors) {
        result = true;
      }
    } else {
      result = true;
    }
  }
  return result;
}

void os::initialize_initial_active_processor_count() {
  assert(_initial_active_processor_count == 0, "Initial active processor count already set.");
  _initial_active_processor_count = active_processor_count();
  log_debug(os)("Initial active processor count set to %d" , _initial_active_processor_count);
}

bool os::create_stack_guard_pages(char* addr, size_t bytes) {
  return os::pd_create_stack_guard_pages(addr, bytes);
}

char* os::reserve_memory(size_t bytes, bool executable, MEMFLAGS flags) {
  char* result = pd_reserve_memory(bytes, executable);
  if (result != nullptr) {
    MemTracker::record_virtual_memory_reserve(result, bytes, CALLER_PC, flags);
    log_debug(os, map)("Reserved " RANGEFMT, RANGEFMTARGS(result, bytes));
  } else {
    log_info(os, map)("Reserve failed (%zu bytes)", bytes);
  }
  return result;
}

char* os::attempt_reserve_memory_at(char* addr, size_t bytes, bool executable, MEMFLAGS flag) {
  char* result = SimulateFullAddressSpace ? nullptr : pd_attempt_reserve_memory_at(addr, bytes, executable);
  if (result != nullptr) {
    MemTracker::record_virtual_memory_reserve((address)result, bytes, CALLER_PC, flag);
    log_debug(os, map)("Reserved " RANGEFMT, RANGEFMTARGS(result, bytes));
  } else {
    log_info(os, map)("Attempt to reserve " RANGEFMT " failed",
                      RANGEFMTARGS(addr, bytes));
  }
  return result;
}

#ifdef ASSERT
static void print_points(const char* s, unsigned* points, unsigned num) {
  stringStream ss;
  for (unsigned i = 0; i < num; i ++) {
    ss.print("%u ", points[i]);
  }
  log_trace(os, map)("%s, %u Points: %s", s, num, ss.base());
}
#endif

// Helper for os::attempt_reserve_memory_between
// Given an array of things, shuffle them (Fisher-Yates)
template <typename T>
static void shuffle_fisher_yates(T* arr, unsigned num, FastRandom& frand) {
  for (unsigned i = num - 1; i >= 1; i--) {
    unsigned j = frand.next() % i;
    swap(arr[i], arr[j]);
  }
}

// Helper for os::attempt_reserve_memory_between
// Given an array of things, do a hemisphere split such that the resulting
// order is: [first, last, first + 1, last - 1, ...]
template <typename T>
static void hemi_split(T* arr, unsigned num) {
  T* tmp = (T*)::alloca(sizeof(T) * num);
  for (unsigned i = 0; i < num; i++) {
    tmp[i] = arr[i];
  }
  for (unsigned i = 0; i < num; i++) {
    arr[i] = is_even(i) ? tmp[i / 2] : tmp[num - (i / 2) - 1];
  }
}

// Given an address range [min, max), attempts to reserve memory within this area, with the given alignment.
// If randomize is true, the location will be randomized.
char* os::attempt_reserve_memory_between(char* min, char* max, size_t bytes, size_t alignment, bool randomize) {

  // Please keep the following constants in sync with the companion gtests:

  // Number of mmap attemts we will undertake.
  constexpr unsigned max_attempts = 32;

  // In randomization mode: We require a minimum number of possible attach points for
  // randomness. Below that we refuse to reserve anything.
  constexpr unsigned min_random_value_range = 16;

  // In randomization mode: If the possible value range is below this threshold, we
  // use a total shuffle without regard for address space fragmentation, otherwise
  // we attempt to minimize fragmentation.
  constexpr unsigned total_shuffle_threshold = 1024;

#define ARGSFMT "range [" PTR_FORMAT "-" PTR_FORMAT "), size " SIZE_FORMAT_X ", alignment " SIZE_FORMAT_X ", randomize: %d"
#define ARGSFMTARGS p2i(min), p2i(max), bytes, alignment, randomize

  log_debug(os, map) ("reserve_between (" ARGSFMT ")", ARGSFMTARGS);

  assert(is_power_of_2(alignment), "alignment invalid (" ARGSFMT ")", ARGSFMTARGS);
  assert(alignment < SIZE_MAX / 2, "alignment too large (" ARGSFMT ")", ARGSFMTARGS);
  assert(is_aligned(bytes, os::vm_page_size()), "size not page aligned (" ARGSFMT ")", ARGSFMTARGS);
  assert(max >= min, "invalid range (" ARGSFMT ")", ARGSFMTARGS);

  char* const absolute_max = (char*)(NOT_LP64(G * 3) LP64_ONLY(G * 128 * 1024));
  char* const absolute_min = (char*) os::vm_min_address();

  // AIX is the only platform that uses System V shm for reserving virtual memory.
  // In this case, the required alignment of the allocated size (64K) and the alignment
  // of possible start points of the memory region (256M) differ.
  // This is not reflected by os_allocation_granularity().
  // The logic here is dual to the one in pd_reserve_memory in os_aix.cpp
  const size_t system_allocation_granularity =
    AIX_ONLY(os::vm_page_size() == 4*K ? 4*K : 256*M)
    NOT_AIX(os::vm_allocation_granularity());

  const size_t alignment_adjusted = MAX2(alignment, system_allocation_granularity);

  // Calculate first and last possible attach points:
  char* const lo_att = align_up(MAX2(absolute_min, min), alignment_adjusted);
  if (lo_att == nullptr) {
    return nullptr; // overflow
  }

  char* const hi_end = MIN2(max, absolute_max);
  if ((uintptr_t)hi_end < bytes) {
    return nullptr; // no need to go on
  }
  char* const hi_att = align_down(hi_end - bytes, alignment_adjusted);
  if (hi_att > max) {
    return nullptr; // overflow
  }

  // no possible attach points
  if (hi_att < lo_att) {
    return nullptr;
  }

  char* result = nullptr;

  const size_t num_attach_points = (size_t)((hi_att - lo_att) / alignment_adjusted) + 1;
  assert(num_attach_points > 0, "Sanity");

  // If this fires, the input range is too large for the given alignment (we work
  // with int below to keep things simple). Since alignment is bound to page size,
  // and the lowest page size is 4K, this gives us a minimum of 4K*4G=8TB address
  // range.
  assert(num_attach_points <= UINT_MAX,
         "Too many possible attach points - range too large or alignment too small (" ARGSFMT ")", ARGSFMTARGS);

  const unsigned num_attempts = MIN2((unsigned)num_attach_points, max_attempts);
  unsigned points[max_attempts];

  if (randomize) {
    FastRandom frand;

    if (num_attach_points < min_random_value_range) {
      return nullptr;
    }

    // We pre-calc the attach points:
    // 1 We divide the attach range into equidistant sections and calculate an attach
    //   point within each section.
    // 2 We wiggle those attach points around within their section (depends on attach
    //   point granularity)
    // 3 Should that not be enough to get effective randomization, shuffle all
    //   attach points
    // 4 Otherwise, re-order them to get an optimized probing sequence.
    const unsigned stepsize = (unsigned)num_attach_points / num_attempts;
    const unsigned half = num_attempts / 2;

    // 1+2: pre-calc points
    for (unsigned i = 0; i < num_attempts; i++) {
      const unsigned deviation = stepsize > 1 ? (frand.next() % stepsize) : 0;
      points[i] = (i * stepsize) + deviation;
    }

    if (num_attach_points < total_shuffle_threshold) {
      // 3:
      // The numeber of possible attach points is too low for the "wiggle" from
      // point 2 to be enough to provide randomization. In that case, shuffle
      // all attach points at the cost of possible fragmentation (e.g. if we
      // end up mapping into the middle of the range).
      shuffle_fisher_yates(points, num_attempts, frand);
    } else {
      // 4
      // We have a large enough number of attach points to satisfy the randomness
      // goal without. In that case, we optimize probing by sorting the attach
      // points: We attempt outermost points first, then work ourselves up to
      // the middle. That reduces address space fragmentation. We also alternate
      // hemispheres, which increases the chance of successfull mappings if the
      // previous mapping had been blocked by large maps.
      hemi_split(points, num_attempts);
    }
  } // end: randomized
  else
  {
    // Non-randomized. We just attempt to reserve by probing sequentially. We
    // alternate between hemispheres, working ourselves up to the middle.
    const int stepsize = (unsigned)num_attach_points / num_attempts;
    for (unsigned i = 0; i < num_attempts; i++) {
      points[i] = (i * stepsize);
    }
    hemi_split(points, num_attempts);
  }

#ifdef ASSERT
  // Print + check all pre-calculated attach points
  print_points("before reserve", points, num_attempts);
  for (unsigned i = 0; i < num_attempts; i++) {
    assert(points[i] < num_attach_points, "Candidate attach point %d out of range (%u, num_attach_points: %zu) " ARGSFMT,
           i, points[i], num_attach_points, ARGSFMTARGS);
  }
#endif

  // Now reserve
  for (unsigned i = 0; result == nullptr && i < num_attempts; i++) {
    const unsigned candidate_offset = points[i];
    char* const candidate = lo_att + candidate_offset * alignment_adjusted;
    assert(candidate <= hi_att, "Invalid offset %u (" ARGSFMT ")", candidate_offset, ARGSFMTARGS);
    result = SimulateFullAddressSpace ? nullptr : os::pd_attempt_reserve_memory_at(candidate, bytes, false);
    if (!result) {
      log_trace(os, map)("Failed to attach at " PTR_FORMAT, p2i(candidate));
    }
  }

  // Sanity checks, logging, NMT stuff:
  if (result != nullptr) {
#define ERRFMT "result: " PTR_FORMAT " " ARGSFMT
#define ERRFMTARGS p2i(result), ARGSFMTARGS
    assert(result >= min, "OOB min (" ERRFMT ")", ERRFMTARGS);
    assert((result + bytes) <= max, "OOB max (" ERRFMT ")", ERRFMTARGS);
    assert(result >= (char*)os::vm_min_address(), "OOB vm.map min (" ERRFMT ")", ERRFMTARGS);
    assert((result + bytes) <= absolute_max, "OOB vm.map max (" ERRFMT ")", ERRFMTARGS);
    assert(is_aligned(result, alignment), "alignment invalid (" ERRFMT ")", ERRFMTARGS);
    log_trace(os, map)(ERRFMT, ERRFMTARGS);
    log_debug(os, map)("successfully attached at " PTR_FORMAT, p2i(result));
    MemTracker::record_virtual_memory_reserve((address)result, bytes, CALLER_PC);
  } else {
    log_debug(os, map)("failed to attach anywhere in [" PTR_FORMAT "-" PTR_FORMAT ")", p2i(min), p2i(max));
  }
  return result;
#undef ARGSFMT
#undef ERRFMT
#undef ARGSFMTARGS
#undef ERRFMTARGS
}

static void assert_nonempty_range(const char* addr, size_t bytes) {
  assert(addr != nullptr && bytes > 0, "invalid range [" PTR_FORMAT ", " PTR_FORMAT ")",
         p2i(addr), p2i(addr) + bytes);
}

bool os::commit_memory(char* addr, size_t bytes, bool executable) {
  assert_nonempty_range(addr, bytes);
  bool res = pd_commit_memory(addr, bytes, executable);
  if (res) {
    MemTracker::record_virtual_memory_commit((address)addr, bytes, CALLER_PC);
    log_debug(os, map)("Committed " RANGEFMT, RANGEFMTARGS(addr, bytes));
  } else {
    log_info(os, map)("Failed to commit " RANGEFMT, RANGEFMTARGS(addr, bytes));
  }
  return res;
}

bool os::commit_memory(char* addr, size_t size, size_t alignment_hint,
                              bool executable) {
  assert_nonempty_range(addr, size);
  bool res = os::pd_commit_memory(addr, size, alignment_hint, executable);
  if (res) {
    MemTracker::record_virtual_memory_commit((address)addr, size, CALLER_PC);
    log_debug(os, map)("Committed " RANGEFMT, RANGEFMTARGS(addr, size));
  } else {
    log_info(os, map)("Failed to commit " RANGEFMT, RANGEFMTARGS(addr, size));
  }
  return res;
}

void os::commit_memory_or_exit(char* addr, size_t bytes, bool executable,
                               const char* mesg) {
  assert_nonempty_range(addr, bytes);
  pd_commit_memory_or_exit(addr, bytes, executable, mesg);
  MemTracker::record_virtual_memory_commit((address)addr, bytes, CALLER_PC);
}

void os::commit_memory_or_exit(char* addr, size_t size, size_t alignment_hint,
                               bool executable, const char* mesg) {
  assert_nonempty_range(addr, size);
  os::pd_commit_memory_or_exit(addr, size, alignment_hint, executable, mesg);
  MemTracker::record_virtual_memory_commit((address)addr, size, CALLER_PC);
}

bool os::uncommit_memory(char* addr, size_t bytes, bool executable) {
  assert_nonempty_range(addr, bytes);
  bool res;
  if (MemTracker::enabled()) {
    ThreadCritical tc;
    res = pd_uncommit_memory(addr, bytes, executable);
    if (res) {
      MemTracker::record_virtual_memory_uncommit((address)addr, bytes);
    }
  } else {
    res = pd_uncommit_memory(addr, bytes, executable);
  }

  if (res) {
    log_debug(os, map)("Uncommitted " RANGEFMT, RANGEFMTARGS(addr, bytes));
  } else {
    log_info(os, map)("Failed to uncommit " RANGEFMT, RANGEFMTARGS(addr, bytes));
  }

  return res;
}

bool os::release_memory(char* addr, size_t bytes) {
  assert_nonempty_range(addr, bytes);
  bool res;
  if (MemTracker::enabled()) {
    ThreadCritical tc;
    res = pd_release_memory(addr, bytes);
    if (res) {
      MemTracker::record_virtual_memory_release((address)addr, bytes);
    }
  } else {
    res = pd_release_memory(addr, bytes);
  }
  if (!res) {
    log_info(os, map)("Failed to release " RANGEFMT, RANGEFMTARGS(addr, bytes));
  } else {
    log_debug(os, map)("Released " RANGEFMT, RANGEFMTARGS(addr, bytes));
  }
  return res;
}

// Prints all mappings
void os::print_memory_mappings(outputStream* st) {
  os::print_memory_mappings(nullptr, SIZE_MAX, st);
}

// Pretouching must use a store, not just a load.  On many OSes loads from
// fresh memory would be satisfied from a single mapped page containing all
// zeros.  We need to store something to each page to get them backed by
// their own memory, which is the effect we want here.  An atomic add of
// zero is used instead of a simple store, allowing the memory to be used
// while pretouch is in progress, rather than requiring users of the memory
// to wait until the entire range has been touched.  This is technically
// a UB data race, but doesn't cause any problems for us.
void os::pretouch_memory(void* start, void* end, size_t page_size) {
  assert(start <= end, "invalid range: " PTR_FORMAT " -> " PTR_FORMAT, p2i(start), p2i(end));
  assert(is_power_of_2(page_size), "page size misaligned: %zu", page_size);
  assert(page_size >= sizeof(int), "page size too small: %zu", page_size);
  if (start < end) {
    // We're doing concurrent-safe touch and memory state has page
    // granularity, so we can touch anywhere in a page.  Touch at the
    // beginning of each page to simplify iteration.
    void* first = align_down(start, page_size);
    void* last = align_down(static_cast<char*>(end) - 1, page_size);
    assert(first <= last, "invariant");
    const size_t pd_page_size = pd_pretouch_memory(first, last, page_size);
    if (pd_page_size > 0) {
      // Iterate from first page through last (inclusive), being careful to
      // avoid overflow if the last page abuts the end of the address range.
      last = align_down(static_cast<char*>(end) - 1, pd_page_size);
      for (char* cur = static_cast<char*>(first); /* break */; cur += pd_page_size) {
        Atomic::add(reinterpret_cast<int*>(cur), 0, memory_order_relaxed);
        if (cur >= last) break;
      }
    }
  }
}

char* os::map_memory_to_file(size_t bytes, int file_desc, MEMFLAGS flag) {
  // Could have called pd_reserve_memory() followed by replace_existing_mapping_with_file_mapping(),
  // but AIX may use SHM in which case its more trouble to detach the segment and remap memory to the file.
  // On all current implementations null is interpreted as any available address.
  char* result = os::map_memory_to_file(nullptr /* addr */, bytes, file_desc);
  if (result != nullptr) {
    MemTracker::record_virtual_memory_reserve_and_commit(result, bytes, CALLER_PC, flag);
  }
  return result;
}

char* os::attempt_map_memory_to_file_at(char* addr, size_t bytes, int file_desc, MEMFLAGS flag) {
  char* result = pd_attempt_map_memory_to_file_at(addr, bytes, file_desc);
  if (result != nullptr) {
    MemTracker::record_virtual_memory_reserve_and_commit((address)result, bytes, CALLER_PC, flag);
  }
  return result;
}

char* os::map_memory(int fd, const char* file_name, size_t file_offset,
                           char *addr, size_t bytes, bool read_only,
                           bool allow_exec, MEMFLAGS flags) {
  char* result = pd_map_memory(fd, file_name, file_offset, addr, bytes, read_only, allow_exec);
  if (result != nullptr) {
    MemTracker::record_virtual_memory_reserve_and_commit((address)result, bytes, CALLER_PC, flags);
  }
  return result;
}

bool os::unmap_memory(char *addr, size_t bytes) {
  bool result;
  if (MemTracker::enabled()) {
    ThreadCritical tc;
    result = pd_unmap_memory(addr, bytes);
    if (result) {
      MemTracker::record_virtual_memory_release((address)addr, bytes);
    }
  } else {
    result = pd_unmap_memory(addr, bytes);
  }
  return result;
}

void os::free_memory(char *addr, size_t bytes, size_t alignment_hint) {
  pd_free_memory(addr, bytes, alignment_hint);
}

void os::realign_memory(char *addr, size_t bytes, size_t alignment_hint) {
  pd_realign_memory(addr, bytes, alignment_hint);
}

char* os::reserve_memory_special(size_t size, size_t alignment, size_t page_size,
                                 char* addr, bool executable) {

  assert(is_aligned(addr, alignment), "Unaligned request address");

  char* result = pd_reserve_memory_special(size, alignment, page_size, addr, executable);
  if (result != nullptr) {
    // The memory is committed
    MemTracker::record_virtual_memory_reserve_and_commit((address)result, size, CALLER_PC);
    log_debug(os, map)("Reserved and committed " RANGEFMT, RANGEFMTARGS(result, size));
  } else {
    log_info(os, map)("Reserve and commit failed (%zu bytes)", size);
  }

  return result;
}

bool os::release_memory_special(char* addr, size_t bytes) {
  bool res;
  if (MemTracker::enabled()) {
    ThreadCritical tc;
    res = pd_release_memory_special(addr, bytes);
    if (res) {
      MemTracker::record_virtual_memory_release((address)addr, bytes);
    }
  } else {
    res = pd_release_memory_special(addr, bytes);
  }
  return res;
}

// Convenience wrapper around naked_short_sleep to allow for longer sleep
// times. Only for use by non-JavaThreads.
void os::naked_sleep(jlong millis) {
  assert(!Thread::current()->is_Java_thread(), "not for use by JavaThreads");
  const jlong limit = 999;
  while (millis > limit) {
    naked_short_sleep(limit);
    millis -= limit;
  }
  naked_short_sleep(millis);
}


////// Implementation of PageSizes

void os::PageSizes::add(size_t page_size) {
  assert(is_power_of_2(page_size), "page_size must be a power of 2: " SIZE_FORMAT_X, page_size);
  _v |= page_size;
}

bool os::PageSizes::contains(size_t page_size) const {
  assert(is_power_of_2(page_size), "page_size must be a power of 2: " SIZE_FORMAT_X, page_size);
  return (_v & page_size) != 0;
}

size_t os::PageSizes::next_smaller(size_t page_size) const {
  assert(is_power_of_2(page_size), "page_size must be a power of 2: " SIZE_FORMAT_X, page_size);
  size_t v2 = _v & (page_size - 1);
  if (v2 == 0) {
    return 0;
  }
  return round_down_power_of_2(v2);
}

size_t os::PageSizes::next_larger(size_t page_size) const {
  assert(is_power_of_2(page_size), "page_size must be a power of 2: " SIZE_FORMAT_X, page_size);
  if (page_size == max_power_of_2<size_t>()) { // Shift by 32/64 would be UB
    return 0;
  }
  // Remove current and smaller page sizes
  size_t v2 = _v & ~(page_size + (page_size - 1));
  if (v2 == 0) {
    return 0;
  }
  return (size_t)1 << count_trailing_zeros(v2);
}

size_t os::PageSizes::largest() const {
  const size_t max = max_power_of_2<size_t>();
  if (contains(max)) {
    return max;
  }
  return next_smaller(max);
}

size_t os::PageSizes::smallest() const {
  // Strictly speaking the set should not contain sizes < os::vm_page_size().
  // But this is not enforced.
  return next_larger(1);
}

void os::PageSizes::print_on(outputStream* st) const {
  bool first = true;
  for (size_t sz = smallest(); sz != 0; sz = next_larger(sz)) {
    if (first) {
      first = false;
    } else {
      st->print_raw(", ");
    }
    if (sz < M) {
      st->print(SIZE_FORMAT "k", sz / K);
    } else if (sz < G) {
      st->print(SIZE_FORMAT "M", sz / M);
    } else {
      st->print(SIZE_FORMAT "G", sz / G);
    }
  }
  if (first) {
    st->print("empty");
  }
}

// Check minimum allowable stack sizes for thread creation and to initialize
// the java system classes, including StackOverflowError - depends on page
// size.
// The space needed for frames during startup is platform dependent. It
// depends on word size, platform calling conventions, C frame layout and
// interpreter/C1/C2 design decisions. Therefore this is given in a
// platform (os/cpu) dependent constant.
// To this, space for guard mechanisms is added, which depends on the
// page size which again depends on the concrete system the VM is running
// on. Space for libc guard pages is not included in this size.
jint os::set_minimum_stack_sizes() {

  _java_thread_min_stack_allowed = _java_thread_min_stack_allowed +
                                   StackOverflow::stack_guard_zone_size() +
                                   StackOverflow::stack_shadow_zone_size();

  _java_thread_min_stack_allowed = align_up(_java_thread_min_stack_allowed, vm_page_size());
  _java_thread_min_stack_allowed = MAX2(_java_thread_min_stack_allowed, _os_min_stack_allowed);

  size_t stack_size_in_bytes = ThreadStackSize * K;
  if (stack_size_in_bytes != 0 &&
      stack_size_in_bytes < _java_thread_min_stack_allowed) {
    // The '-Xss' and '-XX:ThreadStackSize=N' options both set
    // ThreadStackSize so we go with "Java thread stack size" instead
    // of "ThreadStackSize" to be more friendly.
    tty->print_cr("\nThe Java thread stack size specified is too small. "
                  "Specify at least " SIZE_FORMAT "k",
                  _java_thread_min_stack_allowed / K);
    return JNI_ERR;
  }

  // Make the stack size a multiple of the page size so that
  // the yellow/red zones can be guarded.
  JavaThread::set_stack_size_at_create(align_up(stack_size_in_bytes, vm_page_size()));

  // Reminder: a compiler thread is a Java thread.
  _compiler_thread_min_stack_allowed = _compiler_thread_min_stack_allowed +
                                       StackOverflow::stack_guard_zone_size() +
                                       StackOverflow::stack_shadow_zone_size();

  _compiler_thread_min_stack_allowed = align_up(_compiler_thread_min_stack_allowed, vm_page_size());
  _compiler_thread_min_stack_allowed = MAX2(_compiler_thread_min_stack_allowed, _os_min_stack_allowed);

  stack_size_in_bytes = CompilerThreadStackSize * K;
  if (stack_size_in_bytes != 0 &&
      stack_size_in_bytes < _compiler_thread_min_stack_allowed) {
    tty->print_cr("\nThe CompilerThreadStackSize specified is too small. "
                  "Specify at least " SIZE_FORMAT "k",
                  _compiler_thread_min_stack_allowed / K);
    return JNI_ERR;
  }

  _vm_internal_thread_min_stack_allowed = align_up(_vm_internal_thread_min_stack_allowed, vm_page_size());
  _vm_internal_thread_min_stack_allowed = MAX2(_vm_internal_thread_min_stack_allowed, _os_min_stack_allowed);

  stack_size_in_bytes = VMThreadStackSize * K;
  if (stack_size_in_bytes != 0 &&
      stack_size_in_bytes < _vm_internal_thread_min_stack_allowed) {
    tty->print_cr("\nThe VMThreadStackSize specified is too small. "
                  "Specify at least " SIZE_FORMAT "k",
                  _vm_internal_thread_min_stack_allowed / K);
    return JNI_ERR;
  }
  return JNI_OK;
}

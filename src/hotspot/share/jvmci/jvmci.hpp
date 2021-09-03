/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
 */

#ifndef SHARE_JVMCI_JVMCI_HPP
#define SHARE_JVMCI_JVMCI_HPP

#include "compiler/compiler_globals.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "utilities/exceptions.hpp"

class BoolObjectClosure;
class constantPoolHandle;
class JavaThread;
class JVMCIEnv;
class JVMCIRuntime;
class Metadata;
class MetadataHandleBlock;
class OopClosure;
class OopStorage;

template <size_t>
class FormatStringEventLog;

typedef FormatStringEventLog<256> StringEventLog;

struct _jmetadata;
typedef struct _jmetadata *jmetadata;

class JVMCI : public AllStatic {
  friend class JVMCIRuntime;
  friend class JVMCIEnv;
  friend class VM_JVMCIResizeCounters;

 private:
  // Access to the HotSpotJVMCIRuntime used by the CompileBroker.
  static JVMCIRuntime* _compiler_runtime;

  // True when at least one JVMCIRuntime::initialize_HotSpotJVMCIRuntime()
  // execution has completed successfully.
  static volatile bool _is_initialized;

  // True once boxing cache classes are guaranteed to be initialized.
  static bool _box_caches_initialized;

  // Handle created when loading the JVMCI shared library with os::dll_load.
  // Must hold JVMCI_lock when initializing.
  static void* _shared_library_handle;

  // Argument to os::dll_load when loading JVMCI shared library
  static char* _shared_library_path;

  // Records whether JVMCI::shutdown has been called.
  static volatile bool _in_shutdown;

  // Access to the HotSpot heap based JVMCIRuntime
  static JVMCIRuntime* _java_runtime;

  // The file descriptor to which fatal_log() writes. Initialized on
  // first call to fatal_log().
  static volatile int _fatal_log_fd;

  // The path of the file underlying _fatal_log_fd if it is a normal file.
  static const char* _fatal_log_filename;

  // Native thread id of thread that will initialize _fatal_log_fd.
  static volatile intx _fatal_log_init_thread;

  // JVMCI event log (shows up in hs_err crash logs).
  static StringEventLog* _events;
  static StringEventLog* _verbose_events;
  enum {
    max_EventLog_level = 4
  };

  // Gets the Thread* value for the current thread or NULL if it's not available.
  static Thread* current_thread_or_null();

  // Accumulated counters for threads which have exited.
  static jlong* _jvmci_old_thread_counters;

 public:
  enum CodeInstallResult {
     ok,
     dependencies_failed,
     cache_full,
     nmethod_reclaimed, // code cache sweeper reclaimed nmethod in between its creation and being marked "in_use"
     code_too_large,
     first_permanent_bailout = code_too_large
  };

  // Gets the handle to the loaded JVMCI shared library, loading it
  // first if not yet loaded and `load` is true. The path from
  // which the library is loaded is returned in `path`. If
  // `load` is true then JVMCI_lock must be locked.
  static void* get_shared_library(char*& path, bool load);

  // Logs the fatal crash data in `buf` to the appropriate stream.
  static void fatal_log(const char* buf, size_t count);

  // Gets the name of the opened JVMCI shared library crash data file or NULL
  // if this file has not been created.
  static const char* fatal_log_filename() { return _fatal_log_filename; }

  static void do_unloading(bool unloading_occurred);

  static void metadata_do(void f(Metadata*));

  static void shutdown();

  // Returns whether JVMCI::shutdown has been called.
  static bool in_shutdown();

  static bool is_compiler_initialized();

  /**
   * Determines if the VM is sufficiently booted to initialize JVMCI.
   */
  static bool can_initialize_JVMCI();

  static void initialize_globals();

  static void initialize_compiler(TRAPS);

  // Ensures the boxing cache classes (e.g., java.lang.Integer.IntegerCache) are initialized.
  static void ensure_box_caches_initialized(TRAPS);

  // Increments a value indicating some JVMCI compilation activity
  // happened on `thread` if it is a CompilerThread.
  // Returns `thread`.
  static JavaThread* compilation_tick(JavaThread* thread);

  static JVMCIRuntime* compiler_runtime() { return _compiler_runtime; }
  // Gets the single runtime for JVMCI on the Java heap. This is the only
  // JVMCI runtime available when !UseJVMCINativeLibrary.
  static JVMCIRuntime* java_runtime()     { return _java_runtime; }

  // Appends an event to the JVMCI event log if JVMCIEventLogLevel >= `level`
  static void vlog(int level, const char* format, va_list ap) ATTRIBUTE_PRINTF(2, 0);

  // Traces an event to tty if JVMCITraceLevel >= `level`
  static void vtrace(int level, const char* format, va_list ap) ATTRIBUTE_PRINTF(2, 0);

  // Log/trace a JVMCI event
  static void event(int level, const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
  static void event1(const char* format, ...) ATTRIBUTE_PRINTF(1, 2);
  static void event2(const char* format, ...) ATTRIBUTE_PRINTF(1, 2);
  static void event3(const char* format, ...) ATTRIBUTE_PRINTF(1, 2);
  static void event4(const char* format, ...) ATTRIBUTE_PRINTF(1, 2);

  // Manage shared global counter storage
  static void init_counters();
  static void free_counters();
  static bool resize_all_jvmci_counters(int new_size);

  // Return the total of the counters from all live and exited threads
  static void collect_counters(jlong* array, int length);

  // Accumulate the counters of this exiting thread into the global counts
  static void accumulate_counters(JavaThread* thread);

  // Release the storage for the per thread counters
  static void free_counters(JavaThread* thread);

  // Enlarge the per thread counter storage
  static bool resize_counters(JavaThread* thread, int current_size, int new_size);
};



class JVMCIThreadState {
 public:
  JVMCIThreadState();

 private:
  friend class JVMCI;
  friend class JVMCIVMStructs;

  // The _pending_* fields below are used to communicate extra information
  // from an uncommon trap in JVMCI compiled code to the uncommon trap handler.

  // Communicates the DeoptReason and DeoptAction of the uncommon trap
  int _pending_deoptimization;

  // Specifies whether the uncommon trap is to bci 0 of a synchronized method
  // before the monitor has been acquired.
  bool _pending_monitorenter;

  // Specifies if the DeoptReason for the last uncommon trap was Reason_transfer_to_interpreter
  bool _pending_transfer_to_interpreter;

  // True if in a runtime call from compiled code that will deoptimize
  // and re-execute a failed heap allocation in the interpreter.
  bool _in_retryable_allocation;

  // An id of a speculation that JVMCI compiled code can use to further describe and
  // uniquely identify the speculative optimization guarded by an uncommon trap.
  // See JVMCINMethodData::SPECULATION_LENGTH_BITS for further details.
  jlong _pending_failed_speculation;

  // These fields are mutually exclusive in terms of live ranges.
  union {
    // Communicates the pc at which the most recent implicit exception occurred
    // from the signal handler to a deoptimization stub.
    address _implicit_exception_pc;

    // Communicates an alternative call target to an i2c stub from a JavaCall .
    address _alternate_call_target;
  } _union;

  // Support for high precision, thread sensitive counters in JVMCI compiled code.
  jlong* _jvmci_counters;

  // Fast thread locals for use by JVMCI
  jlong _jvmci_reserved0;
  jlong _jvmci_reserved1;
  oop _jvmci_reserved_oop0;

 public :
  int  pending_deoptimization() const             { return _pending_deoptimization; }
  jlong pending_failed_speculation() const        { return _pending_failed_speculation; }
  bool has_pending_monitorenter() const           { return _pending_monitorenter; }
  void set_pending_monitorenter(bool b)           { _pending_monitorenter = b; }
  void set_pending_deoptimization(int reason)     { _pending_deoptimization = reason; }
  void set_pending_failed_speculation(jlong failed_speculation) { _pending_failed_speculation = failed_speculation; }
  void set_pending_transfer_to_interpreter(bool b) { _pending_transfer_to_interpreter = b; }
  void set_jvmci_alternate_call_target(address a) { assert(_union._alternate_call_target == NULL, "must be"); _union._alternate_call_target = a; }
  void set_jvmci_implicit_exception_pc(address a) { assert(_union._implicit_exception_pc == NULL, "must be"); _union._implicit_exception_pc = a; }
  address implicit_exception_pc()                 { return _union._implicit_exception_pc; }
  void set_in_retryable_allocation(bool b)        { _in_retryable_allocation = b; }
  bool in_retryable_allocation() const            { return _in_retryable_allocation; }

  void set_jvmci_reserved_oop0(oop value) { _jvmci_reserved_oop0 = value;  }
  void set_jvmci_reserved0(jlong value)   { _jvmci_reserved0 = value;  }
  void set_jvmci_reserved1(jlong value)   { _jvmci_reserved1 = value;  }

  oop get_jvmci_reserved_oop0()           { return _jvmci_reserved_oop0;  }
  jlong get_jvmci_reserved0()             { return _jvmci_reserved0;  }
  jlong get_jvmci_reserved1()             { return _jvmci_reserved1;  }

  oop* jvmci_reserved_oop0_addr()         { return &_jvmci_reserved_oop0; }

  static ByteSize pending_deoptimization_offset();
  static ByteSize pending_monitorenter_offset();
  static ByteSize jvmci_alternate_call_target_offset();
  static ByteSize jvmci_implicit_exception_pc_offset();
};

// JVMCI event macros.
#define JVMCI_event_1 if (JVMCITraceLevel < 1 && JVMCIEventLogLevel < 1) ; else ::JVMCI::event1
#define JVMCI_event_2 if (JVMCITraceLevel < 2 && JVMCIEventLogLevel < 2) ; else ::JVMCI::event2
#define JVMCI_event_3 if (JVMCITraceLevel < 3 && JVMCIEventLogLevel < 3) ; else ::JVMCI::event3
#define JVMCI_event_4 if (JVMCITraceLevel < 4 && JVMCIEventLogLevel < 4) ; else ::JVMCI::event4

#endif // SHARE_JVMCI_JVMCI_HPP

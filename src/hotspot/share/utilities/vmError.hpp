/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, 2022 SAP SE. All rights reserved.
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

#ifndef SHARE_UTILITIES_VMERROR_HPP
#define SHARE_UTILITIES_VMERROR_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

class Decoder;
class frame;
class VM_ReportJavaOutOfMemory;

class VMError : public AllStatic {
  friend class VMStructs;

  static int         _id;               // Solaris/Linux signals: 0 - SIGRTMAX
                                        // Windows exceptions: 0xCxxxxxxx system errors
                                        //                     0x8xxxxxxx system warnings

  static const char* _message;
  static char        _detail_msg[1024];

  static Thread*     _thread;           // null if it's native thread

  // additional info for crashes
  static address     _pc;               // faulting PC
  static const void* _siginfo;          // ExceptionRecord on Windows,
                                        // siginfo_t on Solaris/Linux
  static const void* _context;          // ContextRecord on Windows,
                                        // ucontext_t on Solaris/Linux

  // records if frame-based stack walking was used to
  // print the native stack instead of os::platform_print_native_stack
  static bool        _print_stack_from_frame_used;

  // additional info for VM internal errors
  static const char* _filename;
  static int         _lineno;

  // used by reporting about OOM
  static size_t      _size;

  // used by fatal error handler
  static int         _current_step;
  static const char* _current_step_info;

  // used for reattempt step logic
  static const size_t _reattempt_required_stack_headroom;

  // Thread id of the first error. We must be able to handle native thread,
  // so use thread id instead of Thread* to identify thread.
  static volatile intptr_t _first_error_tid;

  // Core dump status, false if we have been unable to write a core/minidump for some reason
  static bool coredump_status;

  // When coredump_status is set to true this will contain the name/path to the core/minidump,
  // if coredump_status if false, this will (hopefully) contain a useful error explaining why
  // no core/minidump has been written to disk
  static char coredump_message[O_BUFLEN];

  // Timeout handling:
  // Timestamp at which error reporting started; -1 if no error reporting in progress.
  static volatile jlong _reporting_start_time;
  // Whether or not error reporting did timeout.
  static volatile bool _reporting_did_timeout;
  // Timestamp at which the last error reporting step started; -1 if no error reporting
  //   in progress.
  static volatile jlong _step_start_time;
  // Whether or not the last error reporting step did timeout.
  static volatile bool _step_did_timeout;
  // Whether or not the last error reporting step did succeed.
  static volatile bool _step_did_succeed;

  // Install secondary signal handler to handle secondary faults during error reporting
  // (see VMError::crash_handler)
  static void install_secondary_signal_handler();

  // handle -XX:+ShowMessageBoxOnError. buf is used to format the message string
  static void show_message_box(char* buf, int buflen);

  // generate an error report
  static void report(outputStream* st, bool verbose);

  // generate a stack trace
  static void print_stack_trace(outputStream* st, JavaThread* jt,
                                char* buf, int buflen, bool verbose = false);

  static const char* get_filename_only();

  static bool should_report_bug(unsigned int id) {
    return (id != OOM_MALLOC_ERROR) && (id != OOM_MMAP_ERROR);
  }

  static bool should_submit_bug_report(unsigned int id) {
    return should_report_bug(id) && (id != OOM_JAVA_HEAP_FATAL);
  }

  DEBUG_ONLY(static void reattempt_test_hit_stack_limit(outputStream* st));
  static bool can_reattempt_step(const char* &stop_reason);

  // Write a hint to the stream in case siginfo relates to a segv/bus error
  // and the offending address points into CDS store.
  static void check_failing_cds_access(outputStream* st, const void* siginfo);

  // Timeout handling.
  // Hook functions for platform dependent functionality:
  static void reporting_started();
  static void interrupt_reporting_thread();

  // Helper function to get the current timestamp.
  static jlong get_current_timestamp();

  // Accessors to get/set the start times for step and total timeout.
  static void record_reporting_start_time();
  static jlong get_reporting_start_time();
  static void record_step_start_time();
  static jlong get_step_start_time();
  static void clear_step_start_time();

  WINDOWS_ONLY([[noreturn]] static void raise_fail_fast(const void* exrecord, const void* context);)

public:

  // return a string to describe the error
  static char* error_string(char* buf, int buflen);

  // Record status of core/minidump
  static void record_coredump_status(const char* message, bool status);

  // support for VM.info diagnostic command
  static void print_vm_info(outputStream* st);

  // main error reporting function
  [[noreturn]]
  ATTRIBUTE_PRINTF(6, 7)
  static void report_and_die(Thread* thread, unsigned int sig, address pc, const void* siginfo,
                             const void* context, const char* detail_fmt, ...);

  [[noreturn]]
  ATTRIBUTE_PRINTF(6, 7)
  static void report_and_die(Thread* thread, const void* context, const char* filename,
                             int lineno, const char* message, const char* detail_fmt, ...);

  [[noreturn]]
  ATTRIBUTE_PRINTF(3, 0)
  static void report_and_die(int id, const char* message, const char* detail_fmt, va_list detail_args,
                             Thread* thread, address pc, const void* siginfo, const void* context,
                             const char* filename, int lineno, size_t size);

  [[noreturn]]
  static void report_and_die(Thread* thread, unsigned int sig, address pc,
                             const void* siginfo, const void* context);

  [[noreturn]]
  ATTRIBUTE_PRINTF(6, 0)
  static void report_and_die(Thread* thread, const void* context, const char* filename,
                             int lineno, const char* message, const char* detail_fmt, va_list detail_args);

  [[noreturn]]
  ATTRIBUTE_PRINTF(6, 0)
  static void report_and_die(Thread* thread, const char* filename, int lineno, size_t size,
                             VMErrorType vm_err_type, const char* detail_fmt,
                             va_list detail_args);

  // reporting OutOfMemoryError
  static void report_java_out_of_memory(const char* message);

  // Called by the WatcherThread to check if error reporting has timed-out.
  //  Returns true if error reporting has not completed within the ErrorLogTimeout limit.
  static bool check_timeout();

  // Returns true if at least one thread reported a fatal error and
  //  fatal error handling is in process.
  static bool is_error_reported();

  // Returns true if the current thread reported a fatal error.
  static bool is_error_reported_in_current_thread();

  DEBUG_ONLY(static void controlled_crash(int how);)

  // Non-null address guaranteed to generate a SEGV mapping error on read, for test purposes.
  static const intptr_t segfault_address;

  // Max value for the ErrorLogPrintCodeLimit flag.
  static const int max_error_log_print_code = 10;

  // Needed when printing signal handlers.
  NOT_WINDOWS(static const void* crash_handler_address;)

  // Construct file name for a log file and return it's file descriptor.
  // Name and location depends on pattern, default_pattern params and access
  // permissions.
  static int prepare_log_file(const char* pattern, const char* default_pattern, bool overwrite_existing, char* buf, size_t buflen);

  static bool was_assert_poison_crash(const void* sigInfo);
};

class VMErrorCallback {
  friend class VMError;
  friend class VMErrorCallbackMark;

  // Link through all callbacks active on a thread
  VMErrorCallback* _next;

  // Called by VMError reporting
  virtual void call(outputStream* st) = 0;

public:
  VMErrorCallback() : _next(nullptr) {}
};

class VMErrorCallbackMark : public StackObj {
  Thread* _thread;

public:
  VMErrorCallbackMark(VMErrorCallback* callback);
  ~VMErrorCallbackMark();
};

#endif // SHARE_UTILITIES_VMERROR_HPP

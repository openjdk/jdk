/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_UTILITIES_VMERROR_HPP
#define SHARE_VM_UTILITIES_VMERROR_HPP

#include "utilities/globalDefinitions.hpp"

class Decoder;
class VM_ReportJavaOutOfMemory;

class VMError : public AllStatic {
  friend class VM_ReportJavaOutOfMemory;
  friend class Decoder;
  friend class VMStructs;

  static int         _id;               // Solaris/Linux signals: 0 - SIGRTMAX
                                        // Windows exceptions: 0xCxxxxxxx system errors
                                        //                     0x8xxxxxxx system warnings

  static const char* _message;
  static char        _detail_msg[1024];

  static Thread*     _thread;           // NULL if it's native thread

  // additional info for crashes
  static address     _pc;               // faulting PC
  static void*       _siginfo;          // ExceptionRecord on Windows,
                                        // siginfo_t on Solaris/Linux
  static void*       _context;          // ContextRecord on Windows,
                                        // ucontext_t on Solaris/Linux

  // additional info for VM internal errors
  static const char* _filename;
  static int         _lineno;

  // used by reporting about OOM
  static size_t      _size;

  // used by fatal error handler
  static int         _current_step;
  static const char* _current_step_info;

  // Thread id of the first error. We must be able to handle native thread,
  // so use thread id instead of Thread* to identify thread.
  static volatile intptr_t first_error_tid;

  // Core dump status, false if we have been unable to write a core/minidump for some reason
  static bool coredump_status;

  // When coredump_status is set to true this will contain the name/path to the core/minidump,
  // if coredump_status if false, this will (hopefully) contain a useful error explaining why
  // no core/minidump has been written to disk
  static char coredump_message[O_BUFLEN];


  // set signal handlers on Solaris/Linux or the default exception filter
  // on Windows, to handle recursive crashes.
  static void reset_signal_handlers();

  // handle -XX:+ShowMessageBoxOnError. buf is used to format the message string
  static void show_message_box(char* buf, int buflen);

  // generate an error report
  static void report(outputStream* st, bool verbose);

  // generate a stack trace
  static void print_stack_trace(outputStream* st, JavaThread* jt,
                                char* buf, int buflen, bool verbose = false);

  static bool should_report_bug(unsigned int id) {
    return (id != OOM_MALLOC_ERROR) && (id != OOM_MMAP_ERROR);
  }

  // Write a hint to the stream in case siginfo relates to a segv/bus error
  // and the offending address points into CDS store.
  static void check_failing_cds_access(outputStream* st, const void* siginfo);

  static void report_and_die(Thread* thread, unsigned int sig, address pc, void* siginfo,
                             void* context, const char* detail_fmt, ...) ATTRIBUTE_PRINTF(6, 7);
  static void report_and_die(const char* message, const char* detail_fmt, ...) ATTRIBUTE_PRINTF(2, 3);

  static fdStream out;
  static fdStream log; // error log used by VMError::report_and_die()

public:

  // return a string to describe the error
  static char* error_string(char* buf, int buflen);

  // Record status of core/minidump
  static void record_coredump_status(const char* message, bool status);

  // support for VM.info diagnostic command
  static void print_vm_info(outputStream* st);

  // main error reporting function
  static void report_and_die(int id, const char* message, const char* detail_fmt, va_list detail_args,
                             Thread* thread, address pc, void* siginfo, void* context,
                             const char* filename, int lineno, size_t size) ATTRIBUTE_PRINTF(3, 0);

  static void report_and_die(Thread* thread, unsigned int sig, address pc,
                             void* siginfo, void* context);

  static void report_and_die(Thread* thread,const char* filename, int lineno, const char* message,
                             const char* detail_fmt, va_list detail_args) ATTRIBUTE_PRINTF(5, 0);

  static void report_and_die(Thread* thread, const char* filename, int lineno, size_t size,
                             VMErrorType vm_err_type, const char* detail_fmt,
                             va_list detail_args) ATTRIBUTE_PRINTF(6, 0);

  static void report_and_die(const char* message);

  // reporting OutOfMemoryError
  static void report_java_out_of_memory(const char* message);

  // returns original flags for signal, if it was resetted, or -1 if
  // signal was not changed by error reporter
  static int get_resetted_sigflags(int sig);

  // returns original handler for signal, if it was resetted, or NULL if
  // signal was not changed by error reporter
  static address get_resetted_sighandler(int sig);

  // check to see if fatal error reporting is in progress
  static bool fatal_error_in_progress() { return first_error_tid != -1; }

  static intptr_t get_first_error_tid() { return first_error_tid; }
};

#endif // SHARE_VM_UTILITIES_VMERROR_HPP

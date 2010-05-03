/*
 * Copyright 2003-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */


class VM_ReportJavaOutOfMemory;

class VMError : public StackObj {
  friend class VM_ReportJavaOutOfMemory;

  enum ErrorType {
    internal_error = 0xe0000000,
    oom_error      = 0xe0000001
  };
  int          _id;          // Solaris/Linux signals: 0 - SIGRTMAX
                             // Windows exceptions: 0xCxxxxxxx system errors
                             //                     0x8xxxxxxx system warnings

  const char * _message;
  const char * _detail_msg;

  Thread *     _thread;      // NULL if it's native thread


  // additional info for crashes
  address      _pc;          // faulting PC
  void *       _siginfo;     // ExceptionRecord on Windows,
                             // siginfo_t on Solaris/Linux
  void *       _context;     // ContextRecord on Windows,
                             // ucontext_t on Solaris/Linux

  // additional info for VM internal errors
  const char * _filename;
  int          _lineno;

  // used by fatal error handler
  int          _current_step;
  const char * _current_step_info;
  int          _verbose;

  // used by reporting about OOM
  size_t       _size;

  // set signal handlers on Solaris/Linux or the default exception filter
  // on Windows, to handle recursive crashes.
  void reset_signal_handlers();

  // handle -XX:+ShowMessageBoxOnError. buf is used to format the message string
  void show_message_box(char* buf, int buflen);

  // generate an error report
  void report(outputStream* st);

  // generate a stack trace
  static void print_stack_trace(outputStream* st, JavaThread* jt,
                                char* buf, int buflen, bool verbose = false);

  // accessor
  const char* message() const    { return _message; }
  const char* detail_msg() const { return _detail_msg; }

public:
  // Constructor for crashes
  VMError(Thread* thread, int sig, address pc, void* siginfo, void* context);
  // Constructor for VM internal errors
  VMError(Thread* thread, const char* filename, int lineno,
          const char* message, const char * detail_msg);

  // Constructor for VM OOM errors
  VMError(Thread* thread, const char* filename, int lineno, size_t size,
          const char* message);
  // Constructor for non-fatal errors
  VMError(const char* message);

  // return a string to describe the error
  char *error_string(char* buf, int buflen);

  // main error reporting function
  void report_and_die();

  // reporting OutOfMemoryError
  void report_java_out_of_memory();

  // returns original flags for signal, if it was resetted, or -1 if
  // signal was not changed by error reporter
  static int get_resetted_sigflags(int sig);

  // returns original handler for signal, if it was resetted, or NULL if
  // signal was not changed by error reporter
  static address get_resetted_sighandler(int sig);
};

/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_vmError_windows.cpp.incl"


void VMError::show_message_box(char *buf, int buflen) {
  bool yes;
  do {
    error_string(buf, buflen);
    int len = (int)strlen(buf);
    char *p = &buf[len];

    jio_snprintf(p, buflen - len,
               "\n\n"
               "Do you want to debug the problem?\n\n"
               "To debug, attach Visual Studio to process %d; then switch to thread 0x%x\n"
               "Select 'Yes' to launch Visual Studio automatically (PATH must include msdev)\n"
               "Otherwise, select 'No' to abort...",
               os::current_process_id(), os::current_thread_id());

    yes = os::message_box("Unexpected Error", buf) != 0;

    if (yes) {
      // yes, user asked VM to launch debugger
      //
      // os::breakpoint() calls DebugBreak(), which causes a breakpoint
      // exception. If VM is running inside a debugger, the debugger will
      // catch the exception. Otherwise, the breakpoint exception will reach
      // the default windows exception handler, which can spawn a debugger and
      // automatically attach to the dying VM.
      os::breakpoint();
      yes = false;
    }
  } while (yes);
}

int VMError::get_resetted_sigflags(int sig) {
  return -1;
}

address VMError::get_resetted_sighandler(int sig) {
  return NULL;
}

LONG WINAPI crash_handler(struct _EXCEPTION_POINTERS* exceptionInfo) {
  DWORD exception_code = exceptionInfo->ExceptionRecord->ExceptionCode;
  VMError err(NULL, exception_code, NULL,
                exceptionInfo->ExceptionRecord, exceptionInfo->ContextRecord);
  err.report_and_die();
  return EXCEPTION_CONTINUE_SEARCH;
}

void VMError::reset_signal_handlers() {
  SetUnhandledExceptionFilter(crash_handler);
}

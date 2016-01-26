/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/filemap.hpp"
#include "runtime/arguments.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "utilities/vmError.hpp"

int VMError::get_resetted_sigflags(int sig) {
  return -1;
}

address VMError::get_resetted_sighandler(int sig) {
  return NULL;
}

LONG WINAPI crash_handler(struct _EXCEPTION_POINTERS* exceptionInfo) {
  DWORD exception_code = exceptionInfo->ExceptionRecord->ExceptionCode;
  VMError::report_and_die(NULL, exception_code, NULL, exceptionInfo->ExceptionRecord,
                          exceptionInfo->ContextRecord);
  return EXCEPTION_CONTINUE_SEARCH;
}

void VMError::reset_signal_handlers() {
  SetUnhandledExceptionFilter(crash_handler);
}

// Write a hint to the stream in case siginfo relates to a segv/bus error
// and the offending address points into CDS archive.
void VMError::check_failing_cds_access(outputStream* st, const void* siginfo) {
  if (siginfo && UseSharedSpaces) {
    const EXCEPTION_RECORD* const er = (const EXCEPTION_RECORD*)siginfo;
    if (er->ExceptionCode == EXCEPTION_IN_PAGE_ERROR &&
        er->NumberParameters >= 2) {
      const void* const fault_addr = (const void*) er->ExceptionInformation[1];
      if (fault_addr != NULL) {
        FileMapInfo* const mapinfo = FileMapInfo::current_info();
        if (mapinfo->is_in_shared_space(fault_addr)) {
          st->print("Error accessing class data sharing archive. "
            "Mapped file inaccessible during execution, possible disk/network problem.");
      }
    }
    }
  }
}

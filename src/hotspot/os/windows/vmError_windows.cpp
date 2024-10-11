/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "cds/metaspaceShared.hpp"
#include "runtime/arguments.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/os.hpp"
#include "utilities/vmError.hpp"

LONG WINAPI crash_handler(struct _EXCEPTION_POINTERS* exceptionInfo) {
  DWORD exception_code = exceptionInfo->ExceptionRecord->ExceptionCode;
  VMError::report_and_die(nullptr, exception_code, nullptr, exceptionInfo->ExceptionRecord,
                          exceptionInfo->ContextRecord);
  return EXCEPTION_CONTINUE_SEARCH;
}

void VMError::install_secondary_signal_handler() {
  SetUnhandledExceptionFilter(crash_handler);
}

// Write a hint to the stream in case siginfo relates to a segv/bus error
// and the offending address points into CDS archive.
void VMError::check_failing_cds_access(outputStream* st, const void* siginfo) {
#if INCLUDE_CDS
  if (siginfo && CDSConfig::is_using_archive()) {
    const EXCEPTION_RECORD* const er = (const EXCEPTION_RECORD*)siginfo;
    if (er->ExceptionCode == EXCEPTION_IN_PAGE_ERROR &&
        er->NumberParameters >= 2) {
      const void* const fault_addr = (const void*) er->ExceptionInformation[1];
      if (fault_addr != nullptr) {
        if (MetaspaceShared::is_in_shared_metaspace(fault_addr)) {
          st->print("Error accessing class data sharing archive. "
            "Mapped file inaccessible during execution, possible disk/network problem.");
        }
      }
    }
  }
#endif
}

// Error reporting cancellation: there is no easy way to implement this on Windows, because we do
// not have an easy way to send signals to threads (aka to cause a win32 Exception in another
// thread). We would need something like "RaiseException(HANDLE thread)"...
void VMError::reporting_started() {}
void VMError::interrupt_reporting_thread() {}

void VMError::raise_fail_fast(void* exrecord, void* context) {
  DWORD flags = (exrecord == nullptr) ? FAIL_FAST_GENERATE_EXCEPTION_ADDRESS : 0;
  RaiseFailFastException(static_cast<PEXCEPTION_RECORD>(exrecord),
                         static_cast<PCONTEXT>(context),
                         flags);
  ::abort();
}

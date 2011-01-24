/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/deoptimization.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/stubRoutines.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "thread_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "thread_solaris.inline.hpp"
#endif

// Implementation of the platform-specific part of StubRoutines - for
// a description of how to extend it, see the stubRoutines.hpp file.


extern "C" {
  address _flush_reg_windows();   // in .s file.
  // Flush registers to stack. In case of error we will need to stack walk.
  address bootstrap_flush_windows(void) {
    Thread* thread = ThreadLocalStorage::get_thread_slow();
    // Very early in process there is no thread.
    if (thread != NULL) {
      guarantee(thread->is_Java_thread(), "Not a Java thread.");
      JavaThread* jt = (JavaThread*)thread;
      guarantee(!jt->has_last_Java_frame(), "Must be able to flush registers!");
    }
    return (address)_flush_reg_windows();
  };
};

address StubRoutines::Sparc::_test_stop_entry = NULL;
address StubRoutines::Sparc::_stop_subroutine_entry = NULL;
address StubRoutines::Sparc::_flush_callers_register_windows_entry = CAST_FROM_FN_PTR(address, bootstrap_flush_windows);

address StubRoutines::Sparc::_partial_subtype_check = NULL;

int StubRoutines::Sparc::_atomic_memory_operation_lock = StubRoutines::Sparc::unlocked;

int StubRoutines::Sparc::_v8_oop_lock_cache[StubRoutines::Sparc::nof_v8_oop_lock_cache_entries];

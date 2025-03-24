/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/thread.hpp"
#include "runtime/threadCrashProtection.hpp"

/*
 * See the caveats for this class in threadCrashProtection_posix.hpp
 * Protects the callback call so that SIGSEGV / SIGBUS jumps back into this
 * method and returns false. If none of the signals are raised, returns true.
 * The callback is supposed to provide the method that should be protected.
 */
bool ThreadCrashProtection::call(CrashProtectionCallback& cb) {
  sigset_t saved_sig_mask;

  // we cannot rely on sigsetjmp/siglongjmp to save/restore the signal mask
  // since on at least some systems (OS X) siglongjmp will restore the mask
  // for the process, not the thread
  pthread_sigmask(0, nullptr, &saved_sig_mask);

  Thread* current_thread = Thread::current();
  assert(current_thread->is_jfr_sampling(), "should be JFR sampling related");
  assert(current_thread->crash_protection() == nullptr, "not reentrant");

  if (sigsetjmp(_jmpbuf, 0) == 0) {
    current_thread->set_crash_protection(this);
    cb.call();
    current_thread->set_crash_protection(nullptr);
    return true;
  }
  // this happens when we siglongjmp() back
  current_thread->set_crash_protection(nullptr);
  pthread_sigmask(SIG_SETMASK, &saved_sig_mask, nullptr);
  return false;
}

void ThreadCrashProtection::restore() {
  siglongjmp(_jmpbuf, 1);
}

void ThreadCrashProtection::check_crash_protection(int sig, Thread* thread) {
  if (thread != nullptr && thread->crash_protection() != nullptr) {
    if (sig == SIGSEGV || sig == SIGBUS) {
      thread->crash_protection()->restore();
    }
  }
}

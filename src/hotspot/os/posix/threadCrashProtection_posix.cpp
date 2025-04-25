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

Thread* ThreadCrashProtection::_protected_thread = nullptr;
ThreadCrashProtection* ThreadCrashProtection::_crash_protection = nullptr;

ThreadCrashProtection::ThreadCrashProtection() {
  _protected_thread = Thread::current();
  assert(_protected_thread->is_JfrSampler_thread(), "should be JFRSampler");
}

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
  if (sigsetjmp(_jmpbuf, 0) == 0) {
    // make sure we can see in the signal handler that we have crash protection
    // installed
    _crash_protection = this;
    cb.call();
    // and clear the crash protection
    _crash_protection = nullptr;
    _protected_thread = nullptr;
    return true;
  }
  // this happens when we siglongjmp() back
  pthread_sigmask(SIG_SETMASK, &saved_sig_mask, nullptr);
  _crash_protection = nullptr;
  _protected_thread = nullptr;
  return false;
}

void ThreadCrashProtection::restore() {
  assert(_crash_protection != nullptr, "must have crash protection");
  siglongjmp(_jmpbuf, 1);
}

void ThreadCrashProtection::check_crash_protection(int sig,
    Thread* thread) {

  if (thread != nullptr &&
      thread == _protected_thread &&
      _crash_protection != nullptr) {

    if (sig == SIGSEGV || sig == SIGBUS) {
      _crash_protection->restore();
    }
  }
}

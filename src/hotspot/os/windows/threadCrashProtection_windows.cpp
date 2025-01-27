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

// See the caveats for this class in os_windows.hpp
// Protects the callback call so that raised OS EXCEPTIONS causes a jump back
// into this method and returns false. If no OS EXCEPTION was raised, returns
// true.
// The callback is supposed to provide the method that should be protected.
//
bool ThreadCrashProtection::call(CrashProtectionCallback& cb) {
  bool success = true;
  __try {
    _crash_protection = this;
    cb.call();
  } __except(EXCEPTION_EXECUTE_HANDLER) {
    // only for protection, nothing to do
    success = false;
  }
  _crash_protection = nullptr;
  _protected_thread = nullptr;
  return success;
}

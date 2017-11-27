/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadSMR.hpp"
#include "services/threadService.hpp"

// 'entries + 1' so we always have at least one entry.
ThreadsList::ThreadsList(int entries) : _length(entries), _threads(NEW_C_HEAP_ARRAY(JavaThread*, entries + 1, mtThread)), _next_list(NULL) {
  *(JavaThread**)(_threads + entries) = NULL;  // Make sure the extra entry is NULL.
}

ThreadsList::~ThreadsList() {
  FREE_C_HEAP_ARRAY(JavaThread*, _threads);
}

ThreadsListSetter::~ThreadsListSetter() {
  if (_target_needs_release) {
    // The hazard ptr in the target needs to be released.
    Threads::release_stable_list(_target);
  }
}

void ThreadsListSetter::set() {
  assert(_target->get_threads_hazard_ptr() == NULL, "hazard ptr should not already be set");
  (void) Threads::acquire_stable_list(_target, /* is_ThreadsListSetter */ true);
  _target_needs_release = true;
}

ThreadsListHandle::ThreadsListHandle(Thread *self) : _list(Threads::acquire_stable_list(self, /* is_ThreadsListSetter */ false)), _self(self) {
  assert(self == Thread::current(), "sanity check");
  if (EnableThreadSMRStatistics) {
    _timer.start();
  }
}

ThreadsListHandle::~ThreadsListHandle() {
  Threads::release_stable_list(_self);
  if (EnableThreadSMRStatistics) {
    _timer.stop();
    uint millis = (uint)_timer.milliseconds();
    Threads::inc_smr_tlh_cnt();
    Threads::add_smr_tlh_times(millis);
    Threads::update_smr_tlh_time_max(millis);
  }
}

// Convert an internal thread reference to a JavaThread found on the
// associated ThreadsList. This ThreadsListHandle "protects" the
// returned JavaThread *.
//
// If thread_oop_p is not NULL, then the caller wants to use the oop
// after this call so the oop is returned. On success, *jt_pp is set
// to the converted JavaThread * and true is returned. On error,
// returns false.
//
bool ThreadsListHandle::cv_internal_thread_to_JavaThread(jobject jthread,
                                                         JavaThread ** jt_pp,
                                                         oop * thread_oop_p) {
  assert(this->list() != NULL, "must have a ThreadsList");
  assert(jt_pp != NULL, "must have a return JavaThread pointer");
  // thread_oop_p is optional so no assert()

  // The JVM_* interfaces don't allow a NULL thread parameter; JVM/TI
  // allows a NULL thread parameter to signify "current thread" which
  // allows us to avoid calling cv_external_thread_to_JavaThread().
  // The JVM_* interfaces have no such leeway.

  oop thread_oop = JNIHandles::resolve_non_null(jthread);
  // Looks like an oop at this point.
  if (thread_oop_p != NULL) {
    // Return the oop to the caller; the caller may still want
    // the oop even if this function returns false.
    *thread_oop_p = thread_oop;
  }

  JavaThread *java_thread = java_lang_Thread::thread(thread_oop);
  if (java_thread == NULL) {
    // The java.lang.Thread does not contain a JavaThread * so it has
    // not yet run or it has died.
    return false;
  }
  // Looks like a live JavaThread at this point.

  if (java_thread != JavaThread::current()) {
    // jthread is not for the current JavaThread so have to verify
    // the JavaThread * against the ThreadsList.
    if (EnableThreadSMRExtraValidityChecks && !includes(java_thread)) {
      // Not on the JavaThreads list so it is not alive.
      return false;
    }
  }

  // Return a live JavaThread that is "protected" by the
  // ThreadsListHandle in the caller.
  *jt_pp = java_thread;
  return true;
}

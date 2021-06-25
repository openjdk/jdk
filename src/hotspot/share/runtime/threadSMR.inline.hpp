/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_THREADSMR_INLINE_HPP
#define SHARE_RUNTIME_THREADSMR_INLINE_HPP

#include "runtime/threadSMR.hpp"

#include "gc/shared/gc_globals.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "runtime/atomic.hpp"
#include "memory/iterator.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/thread.inline.hpp"

// Devirtualize known thread closure types.
template <class T>
inline void ThreadsList::threads_do_dispatch(T *cl, JavaThread *const thread) const {
  cl->T::do_thread(thread);
}

template <>
inline void ThreadsList::threads_do_dispatch<ThreadClosure>(ThreadClosure *cl, JavaThread *const thread) const {
  cl->do_thread(thread);
}

template <class T>
inline void ThreadsList::threads_do(T *cl) const {
  const intx scan_interval = PrefetchScanIntervalInBytes;
  JavaThread *const *const end = _threads + _length;
  for (JavaThread *const *current_p = _threads; current_p != end; current_p++) {
    Prefetch::read((void*)current_p, scan_interval);
    JavaThread *const current = *current_p;
    threads_do_dispatch(cl, current);
  }
}

// These three inlines are private to ThreadsSMRSupport, but
// they are called by public inline update_tlh_stats() below:

inline void ThreadsSMRSupport::add_tlh_times(uint add_value) {
  Atomic::add(&_tlh_times, add_value);
}

inline void ThreadsSMRSupport::inc_tlh_cnt() {
  Atomic::inc(&_tlh_cnt);
}

inline void ThreadsSMRSupport::update_tlh_time_max(uint new_value) {
  while (true) {
    uint cur_value = _tlh_time_max;
    if (new_value <= cur_value) {
      // No need to update max value so we're done.
      break;
    }
    if (Atomic::cmpxchg(&_tlh_time_max, cur_value, new_value) == cur_value) {
      // Updated max value so we're done. Otherwise try it all again.
      break;
    }
  }
}

inline ThreadsList* ThreadsSMRSupport::get_java_thread_list() {
  return (ThreadsList*)Atomic::load_acquire(&_java_thread_list);
}

inline bool ThreadsSMRSupport::is_a_protected_JavaThread_with_lock(JavaThread *thread) {
  MutexLocker ml(Threads_lock->owned_by_self() ? NULL : Threads_lock);
  return is_a_protected_JavaThread(thread);
}

inline void ThreadsSMRSupport::update_tlh_stats(uint millis) {
  ThreadsSMRSupport::inc_tlh_cnt();
  ThreadsSMRSupport::add_tlh_times(millis);
  ThreadsSMRSupport::update_tlh_time_max(millis);
}

#endif // SHARE_RUNTIME_THREADSMR_INLINE_HPP

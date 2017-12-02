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

#ifndef SHARE_VM_RUNTIME_THREADSMR_INLINE_HPP
#define SHARE_VM_RUNTIME_THREADSMR_INLINE_HPP

#include "runtime/atomic.hpp"
#include "runtime/prefetch.inline.hpp"
#include "runtime/thread.inline.hpp"
#include "runtime/threadSMR.hpp"

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

inline ThreadsList* ThreadsListSetter::list() {
  ThreadsList *ret = _target->get_threads_hazard_ptr();
  assert(ret != NULL, "hazard ptr should be set");
  assert(!Thread::is_hazard_ptr_tagged(ret), "hazard ptr should be validated");
  return ret;
}

#endif // SHARE_VM_RUNTIME_THREADSMR_INLINE_HPP

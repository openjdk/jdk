/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_RUNTIME_THREAD_INLINE_HPP
#define SHARE_VM_RUNTIME_THREAD_INLINE_HPP

#define SHARE_VM_RUNTIME_THREAD_INLINE_HPP_SCOPE

#include "runtime/thread.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "thread_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "thread_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "thread_windows.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_aix
# include "thread_aix.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_bsd
# include "thread_bsd.inline.hpp"
#endif

#undef SHARE_VM_RUNTIME_THREAD_INLINE_HPP_SCOPE

inline jlong Thread::cooked_allocated_bytes() {
  jlong allocated_bytes = OrderAccess::load_acquire(&_allocated_bytes);
  if (UseTLAB) {
    size_t used_bytes = tlab().used_bytes();
    if ((ssize_t)used_bytes > 0) {
      // More-or-less valid tlab. The load_acquire above should ensure
      // that the result of the add is <= the instantaneous value.
      return allocated_bytes + used_bytes;
    }
  }
  return allocated_bytes;
}

#ifdef PPC64
inline JavaThreadState JavaThread::thread_state() const    {
  return (JavaThreadState) OrderAccess::load_acquire((volatile jint*)&_thread_state);
}

inline void JavaThread::set_thread_state(JavaThreadState s) {
  OrderAccess::release_store((volatile jint*)&_thread_state, (jint)s);
}
#endif

inline void JavaThread::set_done_attaching_via_jni() {
  _jni_attach_state = _attached_via_jni;
  OrderAccess::fence();
}

#endif // SHARE_VM_RUNTIME_THREAD_INLINE_HPP

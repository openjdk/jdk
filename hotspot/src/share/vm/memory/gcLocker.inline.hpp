/*
 * Copyright (c) 2000, 2005, Oracle and/or its affiliates. All rights reserved.
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

inline bool GC_locker::is_active() {
  return _lock_count > 0 || _jni_lock_count > 0;
}

inline bool GC_locker::check_active_before_gc() {
  if (is_active()) {
    set_needs_gc();
  }
  return is_active();
}

inline void GC_locker::lock() {
  // cast away volatile
  Atomic::inc(&_lock_count);
  CHECK_UNHANDLED_OOPS_ONLY(
    if (CheckUnhandledOops) { Thread::current()->_gc_locked_out_count++; })
  assert(Universe::heap() == NULL ||
         !Universe::heap()->is_gc_active(), "locking failed");
}

inline void GC_locker::unlock() {
  // cast away volatile
  Atomic::dec(&_lock_count);
  CHECK_UNHANDLED_OOPS_ONLY(
    if (CheckUnhandledOops) { Thread::current()->_gc_locked_out_count--; })
}

inline void GC_locker::lock_critical(JavaThread* thread) {
  if (!thread->in_critical()) {
    if (!needs_gc()) {
      jni_lock();
    } else {
      jni_lock_slow();
    }
  }
  thread->enter_critical();
}

inline void GC_locker::unlock_critical(JavaThread* thread) {
  thread->exit_critical();
  if (!thread->in_critical()) {
    if (!needs_gc()) {
      jni_unlock();
    } else {
      jni_unlock_slow();
    }
  }
}

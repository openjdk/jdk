/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_GCLOCKER_INLINE_HPP
#define SHARE_GC_SHARED_GCLOCKER_INLINE_HPP

#include "gc/shared/gcLocker.hpp"

#include "runtime/javaThread.inline.hpp"

void GCLocker::enter(JavaThread* current_thread) {
  assert(current_thread == JavaThread::current(), "Must be this thread");

  if (!current_thread->in_critical()) {
    current_thread->enter_critical();

    // Matching the fence in GCLocker::block.
    OrderAccess::fence();

    if (Atomic::load(&_is_gc_request_pending)) {
      current_thread->exit_critical();
      // slow-path
      enter_slow(current_thread);
    }

    DEBUG_ONLY(Atomic::add(&_verify_in_cr_count, (uint64_t)1);)
  } else {
    current_thread->enter_critical();
  }
}

void GCLocker::exit(JavaThread* current_thread) {
  assert(current_thread == JavaThread::current(), "Must be this thread");

#ifdef ASSERT
  if (current_thread->in_last_critical()) {
    Atomic::add(&_verify_in_cr_count, (uint64_t)-1);
    // Matching the loadload in GCLocker::block.
    OrderAccess::storestore();
  }
#endif

  current_thread->exit_critical();
}

#endif // SHARE_GC_SHARED_GCLOCKER_INLINE_HPP

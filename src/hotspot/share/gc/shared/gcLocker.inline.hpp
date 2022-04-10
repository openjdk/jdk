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

#include "runtime/thread.inline.hpp"

void GCLocker::lock_critical(JavaThread* thread) {
  if (!thread->in_critical() && needs_gc()) {
    stall_until_no_needs_gc(thread);
    assert(!needs_gc(), "postcondition");
  }
  thread->enter_critical();
}

void GCLocker::unlock_critical(JavaThread* thread) {
  thread->exit_critical();
  if (!thread->in_critical() && needs_gc()) {
    ack_needs_gc_if_last_thread(thread);
  }
}

#endif // SHARE_GC_SHARED_GCLOCKER_INLINE_HPP

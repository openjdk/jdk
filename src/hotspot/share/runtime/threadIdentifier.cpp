/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/atomic.hpp"
#include "runtime/threadIdentifier.hpp"

// starting at 3, excluding reserved values defined in ObjectMonitor.hpp
static const int64_t INITIAL_TID = 3;
static volatile int64_t next_thread_id = INITIAL_TID;

int64_t ThreadIdentifier::initial() {
  return INITIAL_TID;
}

int64_t ThreadIdentifier::unsafe_offset() {
  return reinterpret_cast<int64_t>(&next_thread_id);
}

int64_t ThreadIdentifier::current() {
  return Atomic::load(&next_thread_id);
}

int64_t ThreadIdentifier::next() {
  int64_t next_tid;
  do {
    next_tid = Atomic::load(&next_thread_id);
  } while (Atomic::cmpxchg(&next_thread_id, next_tid, next_tid + 1) != next_tid);
  return next_tid;
}

#ifdef ASSERT
void ThreadIdentifier::verify_id(int64_t id) {
  int64_t current_id = current();
  assert(id >= initial() && id < current_id, "invalid id, " INT64_FORMAT " and current is " INT64_FORMAT, id, current_id);
}
#endif

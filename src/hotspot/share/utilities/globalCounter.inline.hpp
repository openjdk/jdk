/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_GLOBAL_COUNTER_INLINE_HPP
#define SHARE_UTILITIES_GLOBAL_COUNTER_INLINE_HPP

#include "runtime/orderAccess.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/globalCounter.hpp"

inline void GlobalCounter::critical_section_begin(Thread *thread) {
  assert(thread == Thread::current(), "must be current thread");
  assert((*thread->get_rcu_counter() & COUNTER_ACTIVE) == 0x0, "nested critical sections, not supported yet");
  uintx gbl_cnt = OrderAccess::load_acquire(&_global_counter._counter);
  OrderAccess::release_store_fence(thread->get_rcu_counter(), gbl_cnt | COUNTER_ACTIVE);
}

inline void GlobalCounter::critical_section_end(Thread *thread) {
  assert(thread == Thread::current(), "must be current thread");
  assert((*thread->get_rcu_counter() & COUNTER_ACTIVE) == COUNTER_ACTIVE, "must be in critical section");
  // Mainly for debugging we set it to 'now'.
  uintx gbl_cnt = OrderAccess::load_acquire(&_global_counter._counter);
  OrderAccess::release_store(thread->get_rcu_counter(), gbl_cnt);
}

class GlobalCounter::CriticalSection {
 private:
  Thread* _thread;
 public:
  inline CriticalSection(Thread* thread) : _thread(thread) {
    GlobalCounter::critical_section_begin(_thread);
  }
  inline  ~CriticalSection() {
    GlobalCounter::critical_section_end(_thread);
  }
};

#endif // include guard

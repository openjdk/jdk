/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Azul Systems, Inc. All rights reserved.
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

#ifndef SHARE_RUNTIME_THREAD_INLINE_HPP
#define SHARE_RUNTIME_THREAD_INLINE_HPP

#include "runtime/javaThread.hpp"

#include "gc/shared/tlab_globals.hpp"
#include "runtime/atomic.hpp"
#include "utilities/events.hpp"

#if defined(__APPLE__) && defined(AARCH64)
#include "runtime/os.hpp"
#endif

inline jlong Thread::cooked_allocated_bytes() {
  jlong allocated_bytes = Atomic::load_acquire(&_allocated_bytes);
  if (UseTLAB) {
    // These reads are unsynchronized and unordered with the thread updating its tlab pointers.
    // Use only if top > start && used_bytes <= max_tlab_size_bytes.
    const HeapWord* const top = tlab().top_relaxed();
    const HeapWord* const start = tlab().start_relaxed();
    if (top <= start) {
      return allocated_bytes;
    }
    const size_t used_bytes = pointer_delta(top, start, 1);
    if (used_bytes <= ThreadLocalAllocBuffer::max_size_in_bytes()) {
      // Comparing used_bytes with the maximum allowed size will ensure
      // that we don't add the used bytes from a semi-initialized TLAB
      // ending up with incorrect values. There is still a race between
      // incrementing _allocated_bytes and clearing the TLAB, that might
      // cause double counting in rare cases.
      return allocated_bytes + used_bytes;
    }
  }
  return allocated_bytes;
}

inline ThreadsList* Thread::cmpxchg_threads_hazard_ptr(ThreadsList* exchange_value, ThreadsList* compare_value) {
  return (ThreadsList*)Atomic::cmpxchg(&_threads_hazard_ptr, compare_value, exchange_value);
}

inline ThreadsList* Thread::get_threads_hazard_ptr() const {
  return (ThreadsList*)Atomic::load_acquire(&_threads_hazard_ptr);
}

inline void Thread::set_threads_hazard_ptr(ThreadsList* new_list) {
  Atomic::release_store_fence(&_threads_hazard_ptr, new_list);
}

#if INCLUDE_WX
inline void Thread::init_wx() {
  assert(this == Thread::current(), "should only be called for current thread");
#if INCLUDE_WX_OLD
  assert(!_wx_init, "second init");
  _wx_state = WXWrite;
  set_os_wx_mode_old(_wx_state);
  DEBUG_ONLY(_wx_init = true);
#endif
#if INCLUDE_WX_NEW
  assert(!_wx._init, "second init");
  set_os_wx_mode_new(wx_state().wx_mode());
  DEBUG_ONLY(_wx._init = true;)
#endif
}

#if INCLUDE_WX_OLD
inline void Thread::set_os_wx_mode_old(WXMode mode) {
#ifdef ASSERT
  ++_wx._changes_old;
#endif
  os::current_thread_enable_wx(mode, false);
}

inline WXMode Thread::enable_wx(WXMode new_state) {
  assert(this == Thread::current(), "should only be called for current thread");
  assert(_wx_init, "should be inited");
  WXMode old = _wx_state;
  if (_wx_state != new_state) {
    _wx_state = new_state;
    set_os_wx_mode_old(new_state);
  }
  return old;
}
#endif // INCLUDE_WX_OLD

#if INCLUDE_WX_NEW
inline void Thread::set_os_wx_mode_new(WXMode mode) {
#ifdef ASSERT
  ++_wx._changes_new;
#endif
#if INCLUDE_WX_OLD
  assert(_wx._changes_new <= _wx._changes_old + 2, "new code not better?");
#endif
  os::current_thread_enable_wx(mode, true);
}

inline Thread::WXState Thread::set_wx_state(WXState new_state, const char* FILE, int LINE) {
  assert(_wx._init, "should be inited");
  WXState old_state = _wx._state;
  if (AssertWX) {
    guarantee(this == Thread::current(), "should only be called for current thread");
#ifdef ASSERT
    if (new_state != old_state) {
      assert_can_change_wx_state(new_state);
    }
#endif
  }

  if (new_state.wx_mode() != old_state.wx_mode()) {
    set_os_wx_mode_new(new_state.wx_mode());
  }
#ifdef ASSERT
  if (new_state != old_state) {
    set_last_wx_change_loc(FILE, LINE);
  }
#endif
  _wx._state = new_state;

  return old_state;
}

#endif // INCLUDE_WX_NEW
#endif // INCLUDE_WX

#endif // SHARE_RUNTIME_THREAD_INLINE_HPP

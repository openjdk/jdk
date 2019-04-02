/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_SAFEPOINTMECHANISM_INLINE_HPP
#define SHARE_RUNTIME_SAFEPOINTMECHANISM_INLINE_HPP

#include "runtime/safepointMechanism.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.inline.hpp"

bool SafepointMechanism::local_poll_armed(JavaThread* thread) {
  const intptr_t poll_word = reinterpret_cast<intptr_t>(thread->get_polling_page());
  return mask_bits_are_true(poll_word, poll_bit());
}

bool SafepointMechanism::global_poll() {
  return (SafepointSynchronize::_state != SafepointSynchronize::_not_synchronized);
}

bool SafepointMechanism::local_poll(Thread* thread) {
  if (thread->is_Java_thread()) {
    return local_poll_armed((JavaThread*)thread);
  } else {
    // If the poll is on a non-java thread we can only check the global state.
    return global_poll();
  }
}

bool SafepointMechanism::should_block(Thread* thread) {
  if (uses_thread_local_poll()) {
    return local_poll(thread);
  } else {
    return global_poll();
  }
}

void SafepointMechanism::block_if_requested(JavaThread *thread) {
  if (uses_thread_local_poll() && !local_poll_armed(thread)) {
    return;
  }
  block_if_requested_slow(thread);
}

void SafepointMechanism::arm_local_poll(JavaThread* thread) {
  thread->set_polling_page(poll_armed_value());
}

void SafepointMechanism::disarm_local_poll(JavaThread* thread) {
  thread->set_polling_page(poll_disarmed_value());
}

void SafepointMechanism::disarm_if_needed(JavaThread* thread, bool memory_order_release) {
  JavaThreadState jts = thread->thread_state();
  if (jts == _thread_in_native || jts == _thread_in_native_trans) {
    // JavaThread will disarm itself and execute cross_modify_fence() before continuing
    return;
  }
  if (memory_order_release) {
    thread->set_polling_page_release(poll_disarmed_value());
  } else {
    thread->set_polling_page(poll_disarmed_value());
  }
}

void SafepointMechanism::arm_local_poll_release(JavaThread* thread) {
  thread->set_polling_page_release(poll_armed_value());
}

void SafepointMechanism::disarm_local_poll_release(JavaThread* thread) {
  thread->set_polling_page_release(poll_disarmed_value());
}

#endif // SHARE_RUNTIME_SAFEPOINTMECHANISM_INLINE_HPP

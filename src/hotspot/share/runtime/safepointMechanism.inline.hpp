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

#ifndef SHARE_RUNTIME_SAFEPOINTMECHANISM_INLINE_HPP
#define SHARE_RUNTIME_SAFEPOINTMECHANISM_INLINE_HPP

#include "runtime/safepointMechanism.hpp"

#include "runtime/atomic.hpp"
#include "runtime/handshake.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/stackWatermarkSet.hpp"
#include "runtime/thread.inline.hpp"

// Caller is responsible for using a memory barrier if needed.
inline void SafepointMechanism::ThreadData::set_polling_page(uintptr_t poll_value) {
  Atomic::store(&_polling_page, poll_value);
}

// The acquire makes sure reading of polling page is done before
// the reading the handshake operation or the global state
inline uintptr_t SafepointMechanism::ThreadData::get_polling_page() {
  return Atomic::load_acquire(&_polling_page);
}

// Caller is responsible for using a memory barrier if needed.
inline void SafepointMechanism::ThreadData::set_polling_word(uintptr_t poll_value) {
  Atomic::store(&_polling_word, poll_value);
}

// The acquire makes sure reading of polling page is done before
// the reading the handshake operation or the global state
inline uintptr_t SafepointMechanism::ThreadData::get_polling_word() {
  return Atomic::load_acquire(&_polling_word);
}

bool SafepointMechanism::local_poll_armed(JavaThread* thread) {
  return thread->poll_data()->get_polling_word() & poll_bit();
}

bool SafepointMechanism::global_poll() {
  return (SafepointSynchronize::_state != SafepointSynchronize::_not_synchronized);
}

bool SafepointMechanism::should_process(JavaThread* thread, bool allow_suspend) {
  if (!local_poll_armed(thread)) {
    return false;
  } else if (allow_suspend) {
    return true;
  }
  //  We are armed but we should ignore suspend operations.
  if (global_poll() || // Safepoint
      thread->handshake_state()->has_a_non_suspend_operation() || // Non-suspend handshake
      !StackWatermarkSet::processing_started(thread)) { // StackWatermark processing is not started
    return true;
  }

  // It has boiled down to two possibilities:
  // 1: We have nothing to process, this just a disarm poll.
  // 2: We have a suspend handshake, which cannot be processed.
  // We update the poll value in case of a disarm, to reduce false positives.
  update_poll_values(thread);

  // We are now about to avoid processing and thus no cross modify fence will be executed.
  // In case a safepoint happened, while being blocked, we execute it here.
  OrderAccess::cross_modify_fence();
  return false;
}

void SafepointMechanism::process_if_requested(JavaThread* thread, bool allow_suspend) {
  // Check NoSafepointVerifier. This also clears unhandled oops if CheckUnhandledOops is used.
  thread->check_possible_safepoint();

  if (local_poll_armed(thread)) {
    process(thread, allow_suspend);
  }
}

void SafepointMechanism::process_if_requested_with_exit_check(JavaThread* thread, bool check_asyncs) {
  process_if_requested(thread);
  if (thread->has_special_runtime_exit_condition()) {
    thread->handle_special_runtime_exit_condition(check_asyncs);
  }
}

void SafepointMechanism::arm_local_poll(JavaThread* thread) {
  thread->poll_data()->set_polling_word(_poll_word_armed_value);
  thread->poll_data()->set_polling_page(_poll_page_armed_value);
}

void SafepointMechanism::disarm_local_poll(JavaThread* thread) {
  thread->poll_data()->set_polling_word(_poll_word_disarmed_value);
  thread->poll_data()->set_polling_page(_poll_page_disarmed_value);
}

void SafepointMechanism::arm_local_poll_release(JavaThread* thread) {
  OrderAccess::release();
  thread->poll_data()->set_polling_word(_poll_word_armed_value);
  thread->poll_data()->set_polling_page(_poll_page_armed_value);
}

#endif // SHARE_RUNTIME_SAFEPOINTMECHANISM_INLINE_HPP

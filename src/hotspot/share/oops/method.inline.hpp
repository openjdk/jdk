/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_METHOD_INLINE_HPP
#define SHARE_OOPS_METHOD_INLINE_HPP

#include "oops/method.hpp"

#include "classfile/vmIntrinsics.hpp"
#include "oops/methodCounters.hpp"
#include "runtime/atomic.hpp"

inline address Method::from_compiled_entry() const {
  return Atomic::load_acquire(&_from_compiled_entry);
}

inline address Method::from_interpreted_entry() const {
  return Atomic::load_acquire(&_from_interpreted_entry);
}

inline CompiledMethod* Method::code() const {
  assert( check_code(), "" );
  return Atomic::load_acquire(&_code);
}

// Write (bci, line number) pair to stream
inline void CompressedLineNumberWriteStream::write_pair_regular(int bci_delta, int line_delta) {
  // bci and line number does not compress into single byte.
  // Write out escape character and use regular compression for bci and line number.
  write_byte((jubyte)0xFF);
  write_signed_int(bci_delta);
  write_signed_int(line_delta);
}

inline void CompressedLineNumberWriteStream::write_pair_inline(int bci, int line) {
  int bci_delta = bci - _bci;
  int line_delta = line - _line;
  _bci = bci;
  _line = line;
  // Skip (0,0) deltas - they do not add information and conflict with terminator.
  if (bci_delta == 0 && line_delta == 0) return;
  // Check if bci is 5-bit and line number 3-bit unsigned.
  if (((bci_delta & ~0x1F) == 0) && ((line_delta & ~0x7) == 0)) {
    // Compress into single byte.
    jubyte value = (jubyte)((bci_delta << 3) | line_delta);
    // Check that value doesn't match escape character.
    if (value != 0xFF) {
      write_byte(value);
      return;
    }
  }
  write_pair_regular(bci_delta, line_delta);
}

inline void CompressedLineNumberWriteStream::write_pair(int bci, int line) {
  write_pair_inline(bci, line);
}

inline bool Method::has_compiled_code() const { return code() != nullptr; }

inline bool Method::is_empty_method() const {
  return  code_size() == 1
      && *code_base() == Bytecodes::_return;
}

inline bool Method::is_continuation_enter_intrinsic() const {
  return intrinsic_id() == vmIntrinsics::_Continuation_enterSpecial;
}

inline bool Method::is_continuation_yield_intrinsic() const {
  return intrinsic_id() == vmIntrinsics::_Continuation_doYield;
}

inline bool Method::is_continuation_native_intrinsic() const {
  return intrinsic_id() == vmIntrinsics::_Continuation_enterSpecial ||
         intrinsic_id() == vmIntrinsics::_Continuation_doYield;
}

inline bool Method::is_special_native_intrinsic() const {
  return is_method_handle_intrinsic() || is_continuation_native_intrinsic();
}

#if INCLUDE_JVMTI
inline u2 Method::number_of_breakpoints() const {
  MethodCounters* mcs = method_counters();
  if (mcs == nullptr) {
    return 0;
  } else {
    return mcs->number_of_breakpoints();
  }
}

inline void Method::incr_number_of_breakpoints(Thread* current) {
  MethodCounters* mcs = get_method_counters(current);
  if (mcs != nullptr) {
    mcs->incr_number_of_breakpoints();
  }
}

inline void Method::decr_number_of_breakpoints(Thread* current) {
  MethodCounters* mcs = get_method_counters(current);
  if (mcs != nullptr) {
    mcs->decr_number_of_breakpoints();
  }
}

// Initialization only
inline void Method::clear_number_of_breakpoints() {
  MethodCounters* mcs = method_counters();
  if (mcs != nullptr) {
    mcs->clear_number_of_breakpoints();
  }
}
#endif // INCLUDE_JVMTI

#if COMPILER2_OR_JVMCI
inline void Method::interpreter_throwout_increment(Thread* current) {
  MethodCounters* mcs = get_method_counters(current);
  if (mcs != nullptr) {
    mcs->interpreter_throwout_increment();
  }
}
#endif

inline int Method::interpreter_throwout_count() const        {
  MethodCounters* mcs = method_counters();
  if (mcs == nullptr) {
    return 0;
  } else {
    return mcs->interpreter_throwout_count();
  }
}

inline int Method::prev_event_count() const {
  MethodCounters* mcs = method_counters();
  return mcs == nullptr ? 0 : mcs->prev_event_count();
}

inline void Method::set_prev_event_count(int count) {
  MethodCounters* mcs = method_counters();
  if (mcs != nullptr) {
    mcs->set_prev_event_count(count);
  }
}

inline jlong Method::prev_time() const {
  MethodCounters* mcs = method_counters();
  return mcs == nullptr ? 0 : mcs->prev_time();
}

inline void Method::set_prev_time(jlong time) {
  MethodCounters* mcs = method_counters();
  if (mcs != nullptr) {
    mcs->set_prev_time(time);
  }
}

inline float Method::rate() const {
  MethodCounters* mcs = method_counters();
  return mcs == nullptr ? 0 : mcs->rate();
}

inline void Method::set_rate(float rate) {
  MethodCounters* mcs = method_counters();
  if (mcs != nullptr) {
    mcs->set_rate(rate);
  }
}

#endif // SHARE_OOPS_METHOD_INLINE_HPP

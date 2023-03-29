/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "runtime/lockStack.inline.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/stackWatermark.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "runtime/thread.hpp"
#include "utilities/copy.hpp"
#include "utilities/ostream.hpp"

LockStack::LockStack(JavaThread* jt) :
  _offset(in_bytes(JavaThread::lock_stack_base_offset())), _base()
#ifdef ASSERT
  , _thread(jt)
#endif
{ }

uint32_t LockStack::start_offset() {
  int offset = in_bytes(JavaThread::lock_stack_base_offset());
  assert(offset > 0, "must be positive offset");
  return static_cast<uint32_t>(offset);
}

uint32_t LockStack::end_offset() {
  int offset = in_bytes(JavaThread::lock_stack_base_offset()) + CAPACITY * oopSize;
  assert(offset > 0, "must be positive offset");
  return static_cast<uint32_t>(offset);
}

static bool is_stack_watermark_processing(JavaThread* thread) {
  StackWatermark* watermark = StackWatermarkSet::get(thread, StackWatermarkKind::gc);
  return watermark->processing_started() && !watermark->processing_completed();
}

#ifndef PRODUCT
void LockStack::verify(const char* msg) const {
  assert(is_self() || SafepointSynchronize::is_at_safepoint() || _thread->is_handshake_safe_for(Thread::current()) || _thread->is_suspended() || _thread->is_obj_deopt_suspend() || is_stack_watermark_processing(_thread),
         "access only thread-local, or when target thread safely holds stil");
  verify_no_thread(msg);
}

void LockStack::verify_no_thread(const char* msg) const {
  assert(UseFastLocking && !UseHeavyMonitors, "never use lock-stack when fast-locking is disabled");
  assert((_offset <=  end_offset()), "lockstack overflow: _offset %d end_offset %d", _offset, end_offset());
  assert((_offset >= start_offset()), "lockstack underflow: _offset %d end_offset %d", _offset, start_offset());
  int end = to_index(_offset);
  for (int i = 0; i < end; i++) {
    assert(_base[i] != nullptr, "no null on lock-stack");
    for (int j = i + 1; j < end; j++) {
      assert(_base[i] != _base[j], "entries must be unique: %s", msg);
    }
  }
}
#endif

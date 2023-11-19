/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_RUNTIME_LOCKSTACK_INLINE_HPP
#define SHARE_RUNTIME_LOCKSTACK_INLINE_HPP

#include "memory/iterator.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/lockStack.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/stackWatermark.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"

inline int LockStack::to_index(uint32_t offset) {
  return (offset - lock_stack_base_offset) / oopSize;
}

JavaThread* LockStack::get_thread() const {
  char* addr = reinterpret_cast<char*>(const_cast<LockStack*>(this));
  return reinterpret_cast<JavaThread*>(addr - lock_stack_offset);
}

inline bool LockStack::can_push() const {
  return to_index(_top) < CAPACITY;
}

inline bool LockStack::is_owning_thread() const {
  Thread* current = Thread::current();
  if (current->is_Java_thread()) {
    JavaThread* thread = JavaThread::cast(current);
    bool is_owning = &thread->lock_stack() == this;
    assert(is_owning == (get_thread() == thread), "is_owning sanity");
    return is_owning;
  }
  return false;
}

inline void LockStack::push(oop o) {
  verify("pre-push");
  assert(oopDesc::is_oop(o), "must be");
  assert(!contains(o), "entries must be unique");
  assert(can_push(), "must have room");
  assert(_base[to_index(_top)] == nullptr, "expect zapped entry");
  _base[to_index(_top)] = o;
  _top += oopSize;
  verify("post-push");
}

inline oop LockStack::pop() {
  verify("pre-pop");
  assert(to_index(_top) > 0, "underflow, probably unbalanced push/pop");
  _top -= oopSize;
  oop o = _base[to_index(_top)];
#ifdef ASSERT
  _base[to_index(_top)] = nullptr;
#endif
  assert(!contains(o), "entries must be unique: " PTR_FORMAT, p2i(o));
  verify("post-pop");
  return o;
}

inline void LockStack::remove(oop o) {
  verify("pre-remove");
  assert(contains(o), "entry must be present: " PTR_FORMAT, p2i(o));
  int end = to_index(_top);
  for (int i = 0; i < end; i++) {
    if (_base[i] == o) {
      int last = end - 1;
      for (; i < last; i++) {
        _base[i] = _base[i + 1];
      }
      _top -= oopSize;
#ifdef ASSERT
      _base[to_index(_top)] = nullptr;
#endif
      break;
    }
  }
  assert(!contains(o), "entries must be unique: " PTR_FORMAT, p2i(o));
  verify("post-remove");
}

inline bool LockStack::contains(oop o) const {
  verify("pre-contains");

  // Can't poke around in thread oops without having started stack watermark processing.
  assert(StackWatermarkSet::processing_started(get_thread()), "Processing must have started!");

  int end = to_index(_top);
  for (int i = end - 1; i >= 0; i--) {
    if (_base[i] == o) {
      verify("post-contains");
      return true;
    }
  }
  verify("post-contains");
  return false;
}

inline void LockStack::oops_do(OopClosure* cl) {
  verify("pre-oops-do");
  int end = to_index(_top);
  for (int i = 0; i < end; i++) {
    cl->do_oop(&_base[i]);
  }
  verify("post-oops-do");
}

#endif // SHARE_RUNTIME_LOCKSTACK_INLINE_HPP

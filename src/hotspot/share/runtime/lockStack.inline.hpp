/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/lockStack.hpp"

#include "memory/iterator.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/stackWatermark.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/globalDefinitions.hpp"

inline int LockStack::to_index(uint32_t offset) {
  assert(is_aligned(offset, oopSize), "Bad alignment: %u", offset);
  assert((offset <= end_offset()), "lockstack overflow: offset %d end_offset %d", offset, end_offset());
  assert((offset >= start_offset()), "lockstack underflow: offset %d start_offset %d", offset, start_offset());
  return (offset - lock_stack_base_offset) / oopSize;
}

JavaThread* LockStack::get_thread() const {
  char* addr = reinterpret_cast<char*>(const_cast<LockStack*>(this));
  return reinterpret_cast<JavaThread*>(addr - lock_stack_offset);
}

inline bool LockStack::is_full() const {
  return to_index(_top) == CAPACITY;
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
  assert(!is_full(), "must have room");
  assert(_base[to_index(_top)] == nullptr, "expect zapped entry");
  _base[to_index(_top)] = o;
  _top += oopSize;
  verify("post-push");
}

inline oop LockStack::bottom() const {
  assert(to_index(_top) > 0, "must contain an oop");
  return _base[0];
}

inline bool LockStack::is_empty() const {
  return to_index(_top) == 0;
}

inline bool LockStack::is_recursive(oop o) const {
  if (!VM_Version::supports_recursive_lightweight_locking()) {
    return false;
  }
  verify("pre-is_recursive");

  // This will succeed iff there is a consecutive run of oops on the
  // lock-stack with a length of at least 2.

  assert(contains(o), "at least one entry must exist");
  int end = to_index(_top);
  // Start iterating from the top because the runtime code is more
  // interested in the balanced locking case when the top oop on the
  // lock-stack matches o. This will cause the for loop to break out
  // in the first loop iteration if it is non-recursive.
  for (int i = end - 1; i > 0; i--) {
    if (_base[i - 1] == o && _base[i] == o) {
      verify("post-is_recursive");
      return true;
    }
    if (_base[i] == o) {
      // o can only occur in one consecutive run on the lock-stack.
      // Only one of the two oops checked matched o, so this run
      // must be of length 1 and thus not be recursive. Stop the search.
      break;
    }
  }

  verify("post-is_recursive");
  return false;
}

inline bool LockStack::try_recursive_enter(oop o) {
  if (!VM_Version::supports_recursive_lightweight_locking()) {
    return false;
  }
  verify("pre-try_recursive_enter");

  // This will succeed iff the top oop on the stack matches o.
  // When successful o will be pushed to the lock-stack creating
  // a consecutive run at least 2 oops that matches o on top of
  // the lock-stack.

  assert(!is_full(), "precond");

  int end = to_index(_top);
  if (end == 0 || _base[end - 1] != o) {
    // Topmost oop does not match o.
    verify("post-try_recursive_enter");
    return false;
  }

  _base[end] = o;
  _top += oopSize;
  verify("post-try_recursive_enter");
  return true;
}

inline bool LockStack::try_recursive_exit(oop o) {
  if (!VM_Version::supports_recursive_lightweight_locking()) {
    return false;
  }
  verify("pre-try_recursive_exit");

  // This will succeed iff the top two oops on the stack matches o.
  // When successful the top oop will be popped of the lock-stack.
  // When unsuccessful the lock may still be recursive, in which
  // case the locking is unbalanced. This case is handled externally.

  assert(contains(o), "entries must exist");

  int end = to_index(_top);
  if (end <= 1 || _base[end - 1] != o || _base[end - 2] != o) {
    // The two topmost oops do not match o.
    verify("post-try_recursive_exit");
    return false;
  }

  _top -= oopSize;
  DEBUG_ONLY(_base[to_index(_top)] = nullptr;)
  verify("post-try_recursive_exit");
  return true;
}

inline size_t LockStack::remove(oop o) {
  verify("pre-remove");
  assert(contains(o), "entry must be present: " PTR_FORMAT, p2i(o));

  int end = to_index(_top);
  int inserted = 0;
  for (int i = 0; i < end; i++) {
    if (_base[i] != o) {
      if (inserted != i) {
        _base[inserted] = _base[i];
      }
      inserted++;
    }
  }

#ifdef ASSERT
  for (int i = inserted; i < end; i++) {
    _base[i] = nullptr;
  }
#endif

  uint32_t removed = end - inserted;
  _top -= removed * oopSize;
  assert(!contains(o), "entry must have been removed: " PTR_FORMAT, p2i(o));
  verify("post-remove");
  return removed;
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

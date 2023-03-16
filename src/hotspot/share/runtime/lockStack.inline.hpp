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

#ifndef SHARE_RUNTIME_LOCKSTACK_INLINE_HPP
#define SHARE_RUNTIME_LOCKSTACK_INLINE_HPP

#include "memory/iterator.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/lockStack.hpp"

inline int LockStack::to_index(int offset) {
  return (offset - in_bytes(JavaThread::lock_stack_base_offset())) / oopSize;
}

inline bool LockStack::can_push() const {
  return to_index(_offset) < CAPACITY;
}

inline void LockStack::push(oop o) {
  validate("pre-push");
  assert(oopDesc::is_oop(o), "must be");
  assert(!contains(o), "entries must be unique");
  assert(can_push(), "must have room");
  _base[to_index(_offset)] = o;
  _offset += oopSize;
  validate("post-push");
}

inline oop LockStack::pop() {
  validate("pre-pop");
  assert(to_index(_offset) > 0, "underflow, probably unbalanced push/pop");
  _offset -= oopSize;
  oop o = _base[to_index(_offset)];
  assert(!contains(o), "entries must be unique");
  validate("post-pop");
  return o;
}

inline void LockStack::remove(oop o) {
  validate("pre-remove");
  assert(contains(o), "entry must be present");
  int end = to_index(_offset);
  for (int i = 0; i < end; i++) {
    if (_base[i] == o) {
      int last = end - 1;
      for (; i < last; i++) {
        _base[i] = _base[i + 1];
      }
      _offset -= oopSize;
      break;
    }
  }
  assert(!contains(o), "entries must be unique: " PTR_FORMAT, p2i(o));
  validate("post-remove");
}

inline bool LockStack::contains(oop o) const {
  validate("pre-contains");
  int end = to_index(_offset);
  for (int i = end - 1; i >= 0; i--) {
    if (_base[i] == o) {
      validate("post-contains");
      return true;
    }
  }
  validate("post-contains");
  return false;
}

inline void LockStack::oops_do(OopClosure* cl) {
  validate("pre-oops-do");
  int end = to_index(_offset);
  for (int i = 0; i < end; i++) {
    cl->do_oop(&_base[i]);
  }
  validate("post-oops-do");
}

#endif // SHARE_RUNTIME_LOCKSTACK_INLINE_HPP

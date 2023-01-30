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
#include "runtime/lockStack.hpp"

inline void LockStack::push(oop o) {
  validate("pre-push");
  assert(oopDesc::is_oop(o), "must be");
  assert(!contains(o), "entries must be unique");
  if (_current >= _limit) {
    grow((_limit - _base) + 1);
  }
  *_current = o;
  _current++;
  validate("post-push");
}

inline oop LockStack::pop() {
  validate("pre-pop");
  oop* new_loc = _current - 1;
  assert(new_loc < _current, "underflow, probably unbalanced push/pop");
  _current = new_loc;
  oop o = *_current;
  assert(!contains(o), "entries must be unique");
  validate("post-pop");
  return o;
}

inline void LockStack::remove(oop o) {
  validate("pre-remove");
  assert(contains(o), "entry must be present");
  for (oop* loc = _base; loc < _current; loc++) {
    if (*loc == o) {
      oop* last = _current - 1;
      for (; loc < last; loc++) {
        *loc = *(loc + 1);
      }
      _current--;
      break;
    }
  }
  assert(!contains(o), "entries must be unique: " PTR_FORMAT, p2i(o));
  validate("post-remove");
}

inline bool LockStack::contains(oop o) const {
  validate("pre-contains");
  bool found = false;
  size_t i = 0;
  size_t found_i = 0;
  for (oop* loc = _current - 1; loc >= _base; loc--) {
    if (*loc == o) {
      validate("post-contains");
      return true;
    }
  }
  validate("post-contains");
  return false;
}

inline void LockStack::oops_do(OopClosure* cl) {
  validate("pre-oops-do");
  for (oop* loc = _base; loc < _current; loc++) {
    cl->do_oop(loc);
  }
  validate("post-oops-do");
}

#endif // SHARE_RUNTIME_LOCKSTACK_INLINE_HPP

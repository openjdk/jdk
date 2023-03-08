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
#include "runtime/lockStack.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/thread.hpp"
#include "utilities/copy.hpp"
#include "utilities/ostream.hpp"

LockStack::LockStack() :
  _base(UseFastLocking && !UseHeavyMonitors ? NEW_C_HEAP_ARRAY(oop, INITIAL_CAPACITY, mtSynchronizer) : NULL),
  _limit(_base + INITIAL_CAPACITY),
  _current(_base) {
}

LockStack::~LockStack() {
  if (UseFastLocking && !UseHeavyMonitors) {
    FREE_C_HEAP_ARRAY(oop, _base);
  }
}

#ifndef PRODUCT
void LockStack::validate(const char* msg) const {
  assert(UseFastLocking && !UseHeavyMonitors, "never use lock-stack when fast-locking is disabled");
  for (oop* loc1 = _base; loc1 < _current - 1; loc1++) {
    for (oop* loc2 = loc1 + 1; loc2 < _current; loc2++) {
      assert(*loc1 != *loc2, "entries must be unique: %s", msg);
    }
  }
}
#endif

void LockStack::grow(size_t min_capacity) {
  // Grow stack.
  assert(_limit > _base, "invariant");
  size_t capacity = _limit - _base;
  size_t index = _current - _base;
  size_t new_capacity = MAX2(min_capacity, capacity * 2);
  _base = REALLOC_C_HEAP_ARRAY(oop, _base, new_capacity, mtSynchronizer);
  _limit = _base + new_capacity;
  _current = _base + index;
  assert(_current < _limit, "must fit after growing");
  assert((_limit - _base) >= (ptrdiff_t) min_capacity, "must grow enough");
}

void LockStack::ensure_lock_stack_size(oop* required_limit) {
  JavaThread* jt = JavaThread::current();
  LockStack& lock_stack = jt->lock_stack();
  lock_stack.grow(required_limit - lock_stack._base);
}

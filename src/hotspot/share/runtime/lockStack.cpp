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
#include "runtime/thread.hpp"
#include "utilities/copy.hpp"
#include "utilities/ostream.hpp"

int LockStack::end_offset() {
  return in_bytes(JavaThread::lock_stack_base_offset()) + CAPACITY * oopSize;
}

LockStack::LockStack() :
  _offset(in_bytes(JavaThread::lock_stack_base_offset())) {
}

#ifndef PRODUCT
void LockStack::validate(const char* msg) const {
  assert(UseFastLocking && !UseHeavyMonitors, "never use lock-stack when fast-locking is disabled");
  int end = to_index(_offset);
  for (int i = 0; i < end; i++) {
    for (int j = i + 1; j < end; j++) {
      assert(_base[i] != _base[j], "entries must be unique: %s", msg);
    }
  }
}
#endif

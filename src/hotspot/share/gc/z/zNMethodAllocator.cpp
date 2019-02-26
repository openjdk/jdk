/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "gc/z/zArray.inline.hpp"
#include "gc/z/zNMethodAllocator.hpp"
#include "memory/allocation.hpp"

ZArray<void*> ZNMethodAllocator::_deferred_frees;
bool          ZNMethodAllocator::_defer_frees(false);

void ZNMethodAllocator::immediate_free(void* data) {
  FREE_C_HEAP_ARRAY(uint8_t, data);
}

void ZNMethodAllocator::deferred_free(void* data) {
  _deferred_frees.add(data);
}

void* ZNMethodAllocator::allocate(size_t size) {
  return NEW_C_HEAP_ARRAY(uint8_t, size, mtGC);
}

void ZNMethodAllocator::free(void* data) {
  if (data == NULL) {
    return;
  }

  if (_defer_frees) {
    deferred_free(data);
  } else {
    immediate_free(data);
  }
}

void ZNMethodAllocator::activate_deferred_frees() {
  assert(_deferred_frees.is_empty(), "precondition");
  _defer_frees = true;
}

void ZNMethodAllocator::deactivate_and_process_deferred_frees() {
  _defer_frees = false;

  ZArrayIterator<void*> iter(&_deferred_frees);
  for (void* data; iter.next(&data);) {
    immediate_free(data);
  }
  _deferred_frees.clear();
}

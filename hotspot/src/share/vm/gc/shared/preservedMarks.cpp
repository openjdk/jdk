/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/preservedMarks.inline.hpp"
#include "memory/allocation.inline.hpp"
#include "oops/oop.inline.hpp"

void PreservedMarks::restore() {
  // First, iterate over the stack and restore all marks.
  StackIterator<OopAndMarkOop, mtGC> iter(_stack);
  while (!iter.is_empty()) {
    OopAndMarkOop elem = iter.next();
    elem.set_mark();
  }

  // Second, reclaim all the stack memory
  _stack.clear(true /* clear_cache */);
}

void RemoveForwardedPointerClosure::do_object(oop obj) {
  if (obj->is_forwarded()) {
    obj->init_mark();
  }
}

void PreservedMarksSet::init(uint num) {
  assert(_stacks == NULL && _num == 0, "do not re-initialize");
  assert(num > 0, "pre-condition");
  if (_in_c_heap) {
    _stacks = NEW_C_HEAP_ARRAY(Padded<PreservedMarks>, num, mtGC);
  } else {
    _stacks = NEW_RESOURCE_ARRAY(Padded<PreservedMarks>, num);
  }
  for (uint i = 0; i < num; i += 1) {
    ::new (_stacks + i) PreservedMarks();
  }
  _num = num;

  assert_empty();
}

void PreservedMarksSet::restore() {
  for (uint i = 0; i < _num; i += 1) {
    get(i)->restore();
  }
}

void PreservedMarksSet::reclaim() {
  assert_empty();

  for (uint i = 0; i < _num; i += 1) {
    _stacks[i].~Padded<PreservedMarks>();
  }

  if (_in_c_heap) {
    FREE_C_HEAP_ARRAY(Padded<PreservedMarks>, _stacks);
  } else {
    // the array was resource-allocated, so nothing to do
  }
  _stacks = NULL;
  _num = 0;
}

#ifndef PRODUCT
void PreservedMarksSet::assert_empty() {
  assert(_stacks != NULL && _num > 0, "should have been initialized");
  for (uint i = 0; i < _num; i += 1) {
    assert(get(i)->is_empty(), "stack should be empty");
  }
}
#endif // ndef PRODUCT

/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/debug.hpp"
#include "utilities/intrusiveList.hpp"
#include "utilities/macros.hpp"

#ifdef ASSERT
IntrusiveListEntry::~IntrusiveListEntry() {
  assert(_list == nullptr, "deleting list entry while in list");
  assert(_prev == nullptr, "invariant");
  assert(_next == nullptr, "invariant");
}
#endif // ASSERT

IntrusiveListImpl::IntrusiveListImpl() {
  _root._prev = add_tag_to_root_entry(&_root);
  _root._next = add_tag_to_root_entry(&_root);
  DEBUG_ONLY(_root._list = this;)
}

#ifdef ASSERT
IntrusiveListImpl::~IntrusiveListImpl() {
  assert(is_tagged_root_entry(_root._prev), "deleting non-empty list");
  assert(is_tagged_root_entry(_root._next), "deleting non-empty list");
  // Clear _root's information before running its asserting destructor.
  _root._prev = nullptr;
  _root._next = nullptr;
  _root._list = nullptr;
}
#endif // ASSERT

#ifdef ASSERT

const IntrusiveListImpl* IntrusiveListImpl::entry_list(const Entry* entry) {
  // Ensure consistency.
  if (entry->_list == nullptr) {
    assert(entry->_next == nullptr, "invariant");
    assert(entry->_prev == nullptr, "invariant");
  } else {
    assert(entry->_next != nullptr, "invariant");
    assert(entry->_prev != nullptr, "invariant");
  }
  return entry->_list;
}

void IntrusiveListImpl::set_entry_list(const Entry* entry, IntrusiveListImpl* list) {
  entry->_list = list;
}

#endif // ASSERT

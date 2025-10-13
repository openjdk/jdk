/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_OOPHANDLE_INLINE_HPP
#define SHARE_OOPS_OOPHANDLE_INLINE_HPP

#include "oops/oopHandle.hpp"

#include "gc/shared/oopStorage.inline.hpp"
#include "oops/access.inline.hpp"

inline oop OopHandle::resolve() const {
  if (_obj == nullptr) {
    return (oop) nullptr;
  } else {
    oop oop = NativeAccess<>::oop_load(_obj);
    assert(oopDesc::is_oop_or_null(oop), "Should be oop: " PTR_FORMAT, p2i(oop));
    return oop;
  }
}

inline oop OopHandle::peek() const {
  if (_obj == nullptr) {
    return (oop) nullptr;
  } else {
    oop obj = NativeAccess<AS_NO_KEEPALIVE>::oop_load(_obj);
    assert(oopDesc::is_oop_or_null(obj), "Should be oop: " PTR_FORMAT, p2i(obj));
    return obj;
  }
}

inline OopHandle::OopHandle(OopStorage* storage, oop obj) :
    _obj(storage->allocate()) {
  if (_obj == nullptr) {
    vm_exit_out_of_memory(sizeof(oop), OOM_MALLOC_ERROR,
                          "Cannot create oop handle");
  }
  assert(oopDesc::is_oop_or_null(obj), "Should be oop: " PTR_FORMAT, p2i(obj));
  NativeAccess<>::oop_store(_obj, obj);
}

inline void OopHandle::release(OopStorage* storage) {
  if (_obj != nullptr) {
    // Clear the OopHandle first
    NativeAccess<>::oop_store(_obj, nullptr);
    storage->release(_obj);
    _obj = nullptr;
  }
}

inline void OopHandle::replace(oop obj) {
  assert(!is_empty(), "Must not use replace on empty handle");
  assert(oopDesc::is_oop_or_null(obj), "Should be oop: " PTR_FORMAT, p2i(obj));
  NativeAccess<>::oop_store(_obj, obj);
}

inline oop OopHandle::xchg(oop new_value) {
  assert(!is_empty(), "Must not use xchg on empty handle");
  assert(oopDesc::is_oop_or_null(new_value), "Should be oop: " PTR_FORMAT, p2i(new_value));
  oop obj = NativeAccess<MO_SEQ_CST>::oop_atomic_xchg(_obj, new_value);
  assert(oopDesc::is_oop_or_null(obj), "Should be oop: " PTR_FORMAT, p2i(obj));
  return obj;
}

inline oop OopHandle::cmpxchg(oop old_value, oop new_value) {
  assert(!is_empty(), "Must not use cmpxchg on empty handle");
  assert(oopDesc::is_oop_or_null(new_value), "Should be oop: " PTR_FORMAT, p2i(new_value));
  oop obj = NativeAccess<MO_SEQ_CST>::oop_atomic_cmpxchg(_obj, old_value, new_value);
  assert(oopDesc::is_oop_or_null(obj), "Should be oop: " PTR_FORMAT, p2i(obj));
  return obj;
}

#endif // SHARE_OOPS_OOPHANDLE_INLINE_HPP

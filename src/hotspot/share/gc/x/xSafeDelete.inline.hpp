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

#ifndef SHARE_GC_X_XSAFEDELETE_INLINE_HPP
#define SHARE_GC_X_XSAFEDELETE_INLINE_HPP

#include "gc/x/xSafeDelete.hpp"

#include "gc/x/xArray.inline.hpp"
#include "utilities/debug.hpp"

#include <type_traits>

template <typename T>
XSafeDeleteImpl<T>::XSafeDeleteImpl(XLock* lock) :
    _lock(lock),
    _enabled(0),
    _deferred() {}

template <typename T>
bool XSafeDeleteImpl<T>::deferred_delete(ItemT* item) {
  XLocker<XLock> locker(_lock);
  if (_enabled > 0) {
    _deferred.append(item);
    return true;
  }

  return false;
}

template <typename T>
void XSafeDeleteImpl<T>::immediate_delete(ItemT* item) {
  if (std::is_array<T>::value) {
    delete [] item;
  } else {
    delete item;
  }
}

template <typename T>
void XSafeDeleteImpl<T>::enable_deferred_delete() {
  XLocker<XLock> locker(_lock);
  _enabled++;
}

template <typename T>
void XSafeDeleteImpl<T>::disable_deferred_delete() {
  XArray<ItemT*> deferred;

  {
    XLocker<XLock> locker(_lock);
    assert(_enabled > 0, "Invalid state");
    if (--_enabled == 0) {
      deferred.swap(&_deferred);
    }
  }

  XArrayIterator<ItemT*> iter(&deferred);
  for (ItemT* item; iter.next(&item);) {
    immediate_delete(item);
  }
}

template <typename T>
void XSafeDeleteImpl<T>::operator()(ItemT* item) {
  if (!deferred_delete(item)) {
    immediate_delete(item);
  }
}

template <typename T>
XSafeDelete<T>::XSafeDelete() :
    XSafeDeleteImpl<T>(&_lock),
    _lock() {}

template <typename T>
XSafeDeleteNoLock<T>::XSafeDeleteNoLock() :
    XSafeDeleteImpl<T>(nullptr) {}

#endif // SHARE_GC_X_XSAFEDELETE_INLINE_HPP

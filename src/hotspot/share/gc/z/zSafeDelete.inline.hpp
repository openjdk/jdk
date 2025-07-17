/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZSAFEDELETE_INLINE_HPP
#define SHARE_GC_Z_ZSAFEDELETE_INLINE_HPP

#include "gc/z/zSafeDelete.hpp"

#include "gc/z/zArray.inline.hpp"

#include <type_traits>

template <typename T>
ZSafeDelete<T>::ZSafeDelete(bool locked)
  : _deferred(locked) {}

template <typename T>
void ZSafeDelete<T>::immediate_delete(ItemT* item) {
  if (std::is_array<T>::value) {
    delete [] item;
  } else {
    delete item;
  }
}

template <typename T>
void ZSafeDelete<T>::enable_deferred_delete() {
  _deferred.activate();
}

template <typename T>
void ZSafeDelete<T>::disable_deferred_delete() {
  _deferred.deactivate_and_apply(immediate_delete);
}

template <typename T>
void ZSafeDelete<T>::schedule_delete(ItemT* item) {
  if (!_deferred.add_if_activated(item)) {
    immediate_delete(item);
  }
}

#endif // SHARE_GC_Z_ZSAFEDELETE_INLINE_HPP

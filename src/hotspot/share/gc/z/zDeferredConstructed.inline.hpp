/*
* Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_ZDEFERREDCONSTRUCTED_INLINE_HPP
#define SHARE_GC_Z_ZDEFERREDCONSTRUCTED_INLINE_HPP

#include "gc/z/zDeferredConstructed.hpp"

#include <new>
#include <type_traits>

template <typename T>
inline ZDeferredConstructed<T>::ZDeferredConstructed()
  DEBUG_ONLY(: _initialized(false)) {
  // Do not construct value immediately. Value is constructed at a later point
  // in time using initialize().
}

template <typename T>
inline ZDeferredConstructed<T>::~ZDeferredConstructed() {
  assert(_initialized, "must be initialized before being destructed");
  _t.~T();
}

template <typename T>
inline T* ZDeferredConstructed<T>::get() {
  assert(_initialized, "must be initialized before access");
  return &_t;
}

template <typename T>
inline const T* ZDeferredConstructed<T>::get() const {
  assert(_initialized, "must be initialized before access");
  return &_t;
}

template <typename T>
inline T& ZDeferredConstructed<T>::operator*() {
  return *get();
}

template <typename T>
inline const T& ZDeferredConstructed<T>::operator*() const {
  return *get();
}

template <typename T>
inline T* ZDeferredConstructed<T>::operator->() {
  return get();
}

template <typename T>
inline const T* ZDeferredConstructed<T>::operator->() const {
  return get();
}

template <typename T>
template <typename... Ts>
inline void ZDeferredConstructed<T>::initialize(Ts&&... args) {
  assert(!_initialized, "Double initialization forbidden");
  DEBUG_ONLY(_initialized = true;)
  using NCVP = std::add_pointer_t<std::remove_cv_t<T>>;
  ::new (const_cast<NCVP>(get())) T(args...);
}

#endif // SHARE_GC_Z_ZDEFERREDCONSTRUCTED_INLINE_HPP

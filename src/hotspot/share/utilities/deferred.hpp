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

#ifndef SHARE_UTILITIES_STABLEVALUE_HPP
#define SHARE_UTILITIES_STABLEVALUE_HPP

#include "globalDefinitions.hpp"
#include <type_traits>

// The purpose of this class is to defer initialization of a T to a later point in time,
// and then to never deallocate it. This is mainly useful for deferring the initialization of
// static fields in classes, in order to avoid "Static Initialization Order Fiasco".
template<typename T>
class Deferred {
  union {
    T _t;
  };

  DEBUG_ONLY(bool _initialized);

public:
  NONCOPYABLE(Deferred);

  Deferred()
  DEBUG_ONLY(: _initialized(false)) {
    // Do not construct value, on purpose.
  }

  ~Deferred() {
    // Do not destruct value, on purpose.
  }

  T* get() {
    assert(_initialized, "must be initialized before access");
    return &_t;
  }

  T& operator*() {
    return *get();
  }

  T* operator->() {
    return get();
  }

  template<typename... Ts>
  void initialize(Ts&... args) {
    assert(!_initialized, "Double initialization forbidden");
    DEBUG_ONLY(_initialized = true);
    using NCVP = std::add_pointer_t<std::remove_cv_t<T>>;
    ::new (const_cast<NCVP>(get())) T(args...);
  }
};

#endif // SHARE_UTILITIES_STABLEVALUE_HPP

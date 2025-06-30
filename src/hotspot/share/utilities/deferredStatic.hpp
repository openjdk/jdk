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

#ifndef SHARE_UTILITIES_DEFERREDSTATIC_HPP
#define SHARE_UTILITIES_DEFERREDSTATIC_HPP

#include "utilities/globalDefinitions.hpp"

#include <new>
#include <type_traits>

// The purpose of this class is to provide control over the initialization
// time for an object of type T with static storage duration. An instance of
// this class provides storage for an object, sized and aligned for T. The
// object must be explicitly initialized before use. This avoids problems
// resulting from the unspecified initialization time and ordering between
// different objects that comes from using undeferred objects (the so-called
// "Static Initialization Order Fiasco).
//
// Once initialized, the object is never destroyed. This avoids similar issues
// with the timing and ordering of destruction on normal program exit.
//
// T must not be a reference type. T may be cv-qualified; accessors will
// return a correspondingly cv-qualified reference to the object.
template<typename T>
class DeferredStatic {
  union {
    T _t;
  };

  DEBUG_ONLY(bool _initialized);

public:
  NONCOPYABLE(DeferredStatic);

  DeferredStatic()
  DEBUG_ONLY(: _initialized(false)) {
    // Do not construct value, on purpose.
  }

  ~DeferredStatic() {
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

#endif // SHARE_UTILITIES_DEFERREDSTATIC_HPP

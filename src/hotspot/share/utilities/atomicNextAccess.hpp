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

#ifndef SHARE_UTILITIES_ATOMICNEXTACCESS_HPP
#define SHARE_UTILITIES_ATOMICNEXTACCESS_HPP

#include "runtime/atomic.hpp"
#include "runtime/atomicAccess.hpp"

// A helper class for LockFreeStack and similar intrusive-list style data
// structures that involve atomicity.  These classes require the list element
// provide a function pointer template parameter for getting the "next" field
// from an element object.  That function pointer may take one of two forms,
// where T is the element type:
//
// - T* volatile* (*)(T&)
// - Atomic<T>* (*)(T&)
//
// Specializations of this class provide functions that manipulate the next
// field according to the access mechanism.
template<typename T, auto next_access>
struct AtomicNextAccess;

template<typename T, T* volatile* (*next_access)(T&)>
struct AtomicNextAccess<T, next_access> {
  static T* next(const T& value) {
    return AtomicAccess::load(next_access(const_cast<T&>(value)));
  }

  static T* next_acquire(const T& value) {
    return AtomicAccess::load_acquire(next_access(const_cast<T&>(value)));
  }

  static void set_next(T& value, T* new_next) {
    AtomicAccess::store(next_access(value), new_next);
  }

  static T* compare_exchange(T& value, const T* compare, T* exchange) {
    return AtomicAccess::cmpxchg(next_access(value), compare, exchange);
  }
};

template<typename T, Atomic<T*>* (*next_access)(T&)>
struct AtomicNextAccess<T, next_access> {
  static T* next(const T& value) {
    return next_access(const_cast<T&>(value))->load_relaxed();
  }

  static T* next_acquire(const T& value) {
    return next_access(const_cast<T&>(value))->load_acquire();
  }

  static void set_next(T& value, T* new_next) {
    next_access(value)->store_relaxed(new_next);
  }

  static T* compare_exchange(T& value, const T* compare, T* exchange) {
    return next_access(value)->compare_exchange(compare, exchange);
  }
};

#endif // SHARE_UTILITIES_ATOMICNEXTACCESS_HPP

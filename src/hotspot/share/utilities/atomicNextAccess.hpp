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

// A helper class for LockFreeStack and the like, that have a function pointer
// template parameter for getting the "next" field from an entry object.  That
// function pointer may take one of two forms, where T is the entry type:
//
// - T* volatile* (*)(T&)
// - AtomicValue<T>* (*)(T&)
//
// Specializations of this class provide next and set_next functions that
// manipulate the next field according to the access mechanism.
template<typename T, auto next_access>
struct AtomicNextAccess;

template<typename T, T* volatile* (*next_ptr)(T&)>
struct AtomicNextAccess<T, next_ptr> {
  static T* next(const T& value) {
    return Atomic::load(next_ptr(const_cast<T&>(value)));
  }

  static void set_next(T& value, T* new_next) {
    Atomic::store(next_ptr(value), new_next);
  }
};

template<typename T, AtomicValue<T*>* (*next_ptr)(T&)>
struct AtomicNextAccess<T, next_ptr> {
  static T* next(const T& value) {
    return next_ptr(const_cast<T&>(value))->load_relaxed();
  }

  static void set_next(T& value, T* new_next) {
    next_ptr(value)->relaxed_store(new_next);
  }
};

#endif // SHARE_UTILITIES_ATOMICNEXTACCESS_HPP

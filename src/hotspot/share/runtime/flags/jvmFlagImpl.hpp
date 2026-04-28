/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_FLAGS_JVMFLAGIMPL_HPP
#define SHARE_RUNTIME_FLAGS_JVMFLAGIMPL_HPP

#include "utilities/globalDefinitions.hpp"

typedef void (*JVMFlagReadTracer)(const void* addr);
extern JVMFlagReadTracer _flag_tracer;

template <typename T>
class JVMFlagImpl {
  T _value;
public:
  constexpr JVMFlagImpl(T v) : _value{v} {}
  constexpr JVMFlagImpl() : _value{} {}
  operator T() const {
    if (_flag_tracer != nullptr) {
      _flag_tracer(&_value);
    }
    return _value;
  }
  JVMFlagImpl& operator=(const T& v) {
    _value = v;
    return *this;
  }
  T value() const {
    return _value;
  }
  T& value_ref() {
    return _value; // used by gcConfig.cpp ??FIXME??
  }
};

template<typename T> constexpr T MAX2(JVMFlagImpl<T> a, T b)   { return MAX2(a.value(), b); }
template<typename T> constexpr T MAX2(T a, JVMFlagImpl<T> b)   { return MAX2(a, b.value()); }
template<typename T> constexpr T MAX2(JVMFlagImpl<T> a, JVMFlagImpl<T> b)  { return MAX2(a.value(), b.value()); }

template<typename T> constexpr T MIN2(JVMFlagImpl<T> a, T b)   { return MIN2(a.value(), b); }
template<typename T> constexpr T MIN2(T a, JVMFlagImpl<T> b)   { return MIN2(a, b.value()); }
template<typename T> constexpr T MIN2(JVMFlagImpl<T> a, JVMFlagImpl<T> b)  { return MIN2(a.value(), b.value()); }

template <class T>
inline T byte_size_in_proper_unit(JVMFlagImpl<T> s) {
  return byte_size_in_proper_unit(s.value());
}

#endif // SHARE_RUNTIME_FLAGS_JVMFLAGIMPL_HPP

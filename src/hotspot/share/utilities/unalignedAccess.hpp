/*
 * Copyright (c) 2025 Google and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_UNALIGNED_ACCESS_HPP
#define SHARE_UTILITIES_UNALIGNED_ACCESS_HPP

#include "memory/allStatic.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#ifdef ADDRESS_SANITIZER
// ASan, HWAsan, MSan, and TSan have special support for unaligned access.
// If we ever support the others, update the above ifdef.
#define SANITIZER_HAS_UNALIGNED_ACCESS 1
#endif

#ifdef SANITIZER_HAS_UNALIGNED_ACCESS
#include <sanitizer/common_interface_defs.h>
#endif

#include <cstdint>
#include <cstring>
#include <type_traits>

// Provides access to unaligned data.
class UnalignedAccess : AllStatic {
 public:
  template<typename T>
  static void store(void* ptr, T value) {
    STATIC_ASSERT(std::is_trivially_copyable<T>::value);
    STATIC_ASSERT(std::is_trivially_default_constructible<T>::value);
    assert(ptr != nullptr, "nullptr");
    StoreImpl<sizeof(T)>{}(static_cast<T*>(ptr), value);
  }

  template<typename T>
  static T load(const void* ptr) {
    STATIC_ASSERT(std::is_trivially_copyable<T>::value);
    STATIC_ASSERT(std::is_trivially_default_constructible<T>::value);
    assert(ptr != nullptr, "nullptr");
    return LoadImpl<sizeof(T)>{}(static_cast<const T*>(ptr));
  }

 private:
  template<size_t byte_size> struct StoreImpl;
  template<size_t byte_size> struct LoadImpl;
};

template<>
struct StoreImpl<1> {
  template<typename T>
  void operator()(T* ptr, T value) const {
    STATIC_ASSERT(sizeof(T) == sizeof(uint8_t));
    *ptr = value;
  }
};

template<>
struct LoadImpl<1> {
  template<typename T>
  T operator()(const T* ptr) const {
    STATIC_ASSERT(sizeof(T) == sizeof(uint8_t));
    return *ptr;
  }
};

#ifdef SANITIZER_HAS_UNALIGNED_ACCESS
template<>
struct StoreImpl<2> {
  template<typename T>
  void operator()(T* ptr, T value) const {
    STATIC_ASSERT(sizeof(T) == sizeof(uint16_t));
    __sanitizer_unaligned_store16(ptr, static_cast<uint16_t>(value));
  }
};

template<>
struct StoreImpl<4> {
  template<typename T>
  void operator()(T* ptr, T value) const {
    STATIC_ASSERT(sizeof(T) == sizeof(uint32_t));
    __sanitizer_unaligned_store32(ptr, static_cast<uint32_t>(value));
  }
};

template<>
struct StoreImpl<8> {
  template<typename T>
  void operator()(T* ptr, T value) const {
    STATIC_ASSERT(sizeof(T) == sizeof(uint64_t));
    __sanitizer_unaligned_store64(ptr, static_cast<uint64_t>(value));
  }
};

template<>
struct LoadImpl<2> {
  template<typename T>
  T operator()(const T* ptr) const {
    STATIC_ASSERT(sizeof(T) == sizeof(uint16_t));
    return static_cast<T>(__sanitizer_unaligned_load16(ptr));
  }
};

template<>
struct LoadImpl<4> {
  template<typename T>
  T operator()(const T* ptr) const {
    STATIC_ASSERT(sizeof(T) == sizeof(uint32_t));
    return static_cast<T>(__sanitizer_unaligned_load32(ptr));
  }
};

template<>
struct LoadImpl<8> {
  template<typename T>
  T operator()(const T* ptr) const {
    STATIC_ASSERT(sizeof(T) == sizeof(uint64_t));
    return static_cast<T>(__sanitizer_unaligned_load64(ptr));
  }
};
#endif

template<size_t byte_size>
struct StoreImpl {
  template<typename T>
  void operator()(T* ptr, T value) const {
    STATIC_ASSERT(sizeof(T) == byte_size);
    STATIC_ASSERT(byte_size != 0);  // Incomplete type
    // The only portable way to implement unaligned stores is to use memcpy.
    // Fortunately all decent compilers are able to inline this and avoid
    // the actual call to memcpy. On platforms which allow unaligned access,
    // the compiler will emit a normal store instruction.
    memcpy(ptr, &value, sizeof(T));
  }
};

template<size_t byte_size>
struct LoadImpl {
  template<typename T>
  T operator()(const T* ptr) const {
    STATIC_ASSERT(sizeof(T) == byte_size);
    STATIC_ASSERT(byte_size != 0);  // Incomplete type
    // The only portable way to implement unaligned loads is to use memcpy.
    // Fortunately all decent compilers are able to inline this and avoid
    // the actual call to memcpy. On platforms which allow unaligned access,
    // the compiler will emit a normal load instruction.
    T value;
    memcpy(&value, ptr, sizeof(T));
    return value;
  }
};

#undef SANITIZER_HAS_UNALIGNED_ACCESS

#endif // SHARE_UTILITIES_UNALIGNED_ACCESS_HPP

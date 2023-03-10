/*
 * Copyright (c) 2023, Google and/or its affiliates. All rights reserved.
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
#include "metaprogramming/enableIf.hpp"
#include "metaprogramming/primitiveConversions.hpp"
#include "utilities/globalDefinitions.hpp"

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <type_traits>

#if defined(ADDRESS_SANITIZER)
#include <sanitizer/common_interface_defs.h>
#endif

template <typename T, size_t S = sizeof(T), size_t A = alignof(T)>
struct UnalignedLoadImpl;
template <typename T, size_t S = sizeof(T), size_t A = alignof(T)>
struct UnalignedStoreImpl;

// Support for well defined potentially unaligned memory access, regardless of underlying
// architecture support.
//
// Unaligned access is undefined behavior according to the standard. Some architectures support
// aligned and unaligned memory access via the same instructions (i.e. x86, AArch64) while some do
// not permit unaligned access at all. Compilers are free to assume that all memory access of a
// type T are done at a suitably aligned address for type T, that is an address aligned to
// alignof(T). This is not always the case, as there are use cases where we may want to access type
// T at a non-suitably aligned address. For example, when serializing scalar types to a buffer
// without padding.
//
// IMPORTANT: On some architectures the cost for unaligned accesses is cheap, while on others it is
// expensive. Only use unaligned accesses when necessary.
class UnalignedAccess final : public AllStatic {
 public:
  // Loads the bits of the value of type T from the specified address. The address may or may not be
  // suitably aligned for type T. T must be trivially copyable and must be default constructible.
  template <typename T, ENABLE_IF(std::is_trivially_copyable<T>::value &&
                                  std::is_default_constructible<T>::value)>
  static inline T load(const void* p) {
    return UnalignedLoadImpl<T>{}(p);
  }

  // Stores the bits of the value of type T at the specified address. The address may or may not be
  // suitably aligned for type T. T must be trivially copyable and must be default constructible.
  template <typename T, ENABLE_IF(std::is_trivially_copyable<T>::value)>
  static inline void store(void* p, T x) {
    UnalignedStoreImpl<T>{}(p, x);
  }
};

template <typename T, size_t S, size_t A>
struct UnalignedLoadImpl {
  inline T operator()(const void* p) const {
#if defined(TARGET_COMPILER_gcc) || defined(TARGET_COMPILER_xlc)
    // When available, explicitly prefer the builtin memcpy variant. This ensures GCC/Clang will
    // do its best at generating optimal machine code regardless of build options. For architectures
    // which support unaligned access, this typically results in a single instruction. For other
    // architectures, GCC/Clang will attempt to determine if the access is aligned first at compile
    // time and generate a single instruction otherwise it will fallback to a more general approach.
    T x;
    __builtin_memcpy(&x, p, S);
    return x;
#elif defined(TARGET_COMPILER_visCPP)
    return *static_cast<__unaligned const T*>(p);
#else
    // Most compilers will generate optimal machine code.
    T x;
    std::memcpy(&x, p, S);
    return x;
#endif
  }
};

template <typename T, size_t S, size_t A>
struct UnalignedStoreImpl {
  inline void operator()(void* p, T x) const {
#if defined(TARGET_COMPILER_gcc) || defined(TARGET_COMPILER_xlc)
    // When available, explicitly prefer the builtin memcpy variant. This ensures GCC/Clang will
    // do its best at generating optimal machine code regardless of build options. For architectures
    // which support unaligned access, this typically results in a single instruction. For other
    // architectures, GCC/Clang will attempt to determine if the access is aligned first at compile
    // time and generate a single instruction otherwise it will fallback to a more general approach.
    __builtin_memcpy(p, &x, S);
#elif defined(TARGET_COMPILER_visCPP)
    *static_cast<__unaligned T*>(p) = x;
#else
    // Most compilers will generate optimal machine code.
    std::memcpy(p, &x, S);
#endif
  }
};

// Loads for types with an alignment of 1 byte are always aligned, but for simplicity of
// metaprogramming we accept them in UnalignedAccess.
template <typename T, size_t S>
struct UnalignedLoadImpl<T, S, 1> {
  inline T operator()(const void* p) const {
    return *static_cast<const T*>(p);
  }
};

// Stores for types with an alignment of 1 byte are always aligned, but for simplicity of
// metaprogramming we accept them in UnalignedAccess.
template <typename T, size_t S>
struct UnalignedStoreImpl<T, S, 1> {
  inline void operator()(void* p, T x) const {
    *static_cast<T*>(p) = x;
  }
};

#if defined(ADDRESS_SANITIZER)
// Intercept unaligned accesses of size 2, 4, and 8 for ASan which can miss some bugs related to
// unaligned accesses if these are not used.
//
// NOTE: these should also be enabled for MSan and TSan as well when/if we use those.

template <typename T, size_t A>
struct UnalignedLoadImpl<T, 2, A> {
  inline T operator()(const void* p) const {
    return PrimitiveConversions::cast<T>(__sanitizer_unaligned_load16(p));
  }
};

template <typename T, size_t A>
struct UnalignedStoreImpl<T, 2, A> {
  inline void operator()(void* p, T x) const {
    __sanitizer_unaligned_store16(p, PrimitiveConversions::cast<uint16_t>(x));
  }
};

template <typename T, size_t A>
struct UnalignedLoadImpl<T, 4, A> {
  inline T operator()(const void* p) const {
    return PrimitiveConversions::cast<T>(__sanitizer_unaligned_load32(p));
  }
};

template <typename T, size_t A>
struct UnalignedStoreImpl<T, 4, A> {
  inline void operator()(void* p, T x) const {
    __sanitizer_unaligned_store32(p, PrimitiveConversions::cast<uint32_t>(x));
  }
};

template <typename T, size_t A>
struct UnalignedLoadImpl<T, 8, A> final {
  inline T operator()(const void* p) const {
    return PrimitiveConversions::cast<T>(__sanitizer_unaligned_load64(p));
  }
};

template <typename T, size_t A>
struct UnalignedStoreImpl<T, 8, A> final {
  inline void operator()(void* p, T x) const {
    __sanitizer_unaligned_store64(p, PrimitiveConversions::cast<uint64_t>(x));
  }
};
#endif

#endif // SHARE_UTILITIES_UNALIGNED_ACCESS_HPP

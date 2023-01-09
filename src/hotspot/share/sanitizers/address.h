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

#ifndef SHARE_SANITIZERS_ADDRESS_HPP
#define SHARE_SANITIZERS_ADDRESS_HPP

#ifdef ADDRESS_SANITIZER
#include <sanitizer/asan_interface.h>
#endif

// NO_SANITIZE_ADDRESS
//
// Function attribute that can be applied to disable ASan instrumentation for the function.
#ifdef ADDRESS_SANITIZER
// We currently only support ASan with GCC and Clang, but technically MSVC also has ASan so we could
// support it in the future. Thus we pre-emptively support the MSVC-specific attribute.
#ifdef _MSC_VER
#define NO_SANITIZE_ADDRESS __declspec(no_sanitize_address)
#else
#define NO_SANITIZE_ADDRESS __attribute__((no_sanitize_address))
#endif
#else
#define NO_SANITIZE_ADDRESS
#endif

// ASAN_POISON_MEMORY_REGION()
//
// Poisons the specified memory region. Subsequent reads and writes to the memory region will result
// in a fatal error.
#ifdef ADDRESS_SANITIZER
#undef ASAN_POISON_MEMORY_REGION
#define ASAN_POISON_MEMORY_REGION(addr, size) __asan_poison_memory_region((addr), (size))
#else
// NOOP implementation which preserves the arguments, ensuring they still compile, but ensures they
// are stripped due to being unreachable.
#define ASAN_POISON_MEMORY_REGION(addr, size) \
  do {                                        \
    if (false) {                              \
      ((void) (addr));                        \
      ((void) (size));                        \
    }                                         \
  } while (false)
#endif

// ASAN_UNPOISON_MEMORY_REGION()
//
// Unpoisons the specified memory region. Subsequent reads and writes to the memory region are
// valid.
#ifdef ADDRESS_SANITIZER
#undef ASAN_UNPOISON_MEMORY_REGION
#define ASAN_UNPOISON_MEMORY_REGION(addr, size) __asan_unpoison_memory_region((addr), (size))
#else
// NOOP implementation which preserves the arguments, ensuring they still compile, but ensures they
// are stripped due to being unreachable.
#define ASAN_UNPOISON_MEMORY_REGION(addr, size) \
  do {                                          \
    if (false) {                                \
      ((void) (addr));                          \
      ((void) (size));                          \
    }                                           \
  } while (false)
#endif

#endif // SHARE_SANITIZERS_ADDRESS_HPP

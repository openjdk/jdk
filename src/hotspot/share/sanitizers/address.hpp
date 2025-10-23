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
#include "memory/allStatic.hpp"

#include <sanitizer/asan_interface.h>
#endif

// ATTRIBUTE_NO_ASAN
//
// Function attribute which informs the compiler to not instrument memory accesses in the function.
// Useful if the function is known to do something dangerous, such as reading previous stack frames
// or reading arbitrary regions of memory when dumping during a crash.
#ifdef ADDRESS_SANITIZER
#if defined(TARGET_COMPILER_gcc)
// GCC-like, including Clang.
#define ATTRIBUTE_NO_ASAN __attribute__((no_sanitize_address))
#elif defined(TARGET_COMPILER_visCPP)
// Microsoft Visual C++
#define ATTRIBUTE_NO_ASAN __declspec(no_sanitize_address)
#endif
#endif

#ifndef ATTRIBUTE_NO_ASAN
#define ATTRIBUTE_NO_ASAN
#endif

// ASAN_POISON_MEMORY_REGION()/ASAN_UNPOISON_MEMORY_REGION()
//
// Poisons/unpoisons the specified memory region. When ASan is available this is the macro of the
// same name from <sanitizer/asan_interface.h>. When ASan is not available this macro is a NOOP
// which preserves the arguments, ensuring they still compile, but ensures they are stripped due to
// being unreachable. This helps ensure developers do not accidently break ASan builds.
#ifdef ADDRESS_SANITIZER
// ASAN_POISON_MEMORY_REGION is defined in <sanitizer/asan_interface.h>
// ASAN_UNPOISON_MEMORY_REGION is defined in <sanitizer/asan_interface.h>
#else
#define ASAN_POISON_MEMORY_REGION(addr, size) \
  do {                                        \
    if (false) {                              \
      ((void) (addr));                        \
      ((void) (size));                        \
    }                                         \
  } while (false)
#define ASAN_UNPOISON_MEMORY_REGION(addr, size) \
  do {                                          \
    if (false) {                                \
      ((void) (addr));                          \
      ((void) (size));                          \
    }                                           \
  } while (false)
#endif

class outputStream;

#ifdef ADDRESS_SANITIZER
struct Asan : public AllStatic {
  static void initialize();
  static bool had_error();
  static void report(outputStream* st);
};
#endif

#endif // SHARE_SANITIZERS_ADDRESS_HPP

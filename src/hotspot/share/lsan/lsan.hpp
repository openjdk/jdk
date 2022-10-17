/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_LSAN_LSAN_HPP
#define SHARE_LSAN_LSAN_HPP

#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

#include <cstddef>

#ifdef LEAK_SANITIZER
#include <sanitizer/lsan_interface.h>
#define NO_SANITIE_LEAK __attribute__((no_sanitize("leak")))
#else
#define NO_SANITIE_LEAK
#endif

// Class-based namespace enclosing methods for interacting with LSan. This
// interface is always available regardless of whether LSan is available or not
// in the build. If LSan is not available, all methods are NOOP and will be
// compiled out. This approach makes maintaining code related to LSan easier as
// the code is always compiled, removing the chance of accidental breakages.
class Lsan final : AllStatic {
 public:
  // Returns true IIF LSan is enabled and available.
  static ATTRIBUTE_ARTIFICIAL ALWAYSINLINE bool enabled() {
#ifdef LEAK_SANITIZER
    return true;
#else
    return false;
#endif
  }

  // Perform a leak check. If any leaks are detected the program immediatley
  // exits with a non-zero code.
  static ATTRIBUTE_ARTIFICIAL ALWAYSINLINE void do_leak_check() {
#ifdef LEAK_SANITIZER
    __lsan_do_leak_check();
#endif
  }

  // Returns true IFF leaks were detected.
  static ATTRIBUTE_ARTIFICIAL ALWAYSINLINE bool do_recoverable_leak_check() {
#ifdef LEAK_SANITIZER
    return __lsan_do_recoverable_leak_check() != 0;
#else
    return false;
#endif
  }

  // Registers a region of memory that may contain pointers to malloc-based
  // memory. This only needs to be done for manually mapped memory via mmap.
  static ATTRIBUTE_ARTIFICIAL ALWAYSINLINE void register_root_region(const void* ptr, size_t n) {
#ifdef LEAK_SANITIZER
    __lsan_register_root_region(ptr, n);
#else
    static_cast<void>(ptr);
    static_cast<void>(n);
#endif
  }

  // Unregisters a previously registered region of memory.
  static ATTRIBUTE_ARTIFICIAL ALWAYSINLINE void unregister_root_region(const void* ptr, size_t n) {
#ifdef LEAK_SANITIZER
    __lsan_unregister_root_region(ptr, n);
#else
    static_cast<void>(ptr);
    static_cast<void>(n);
#endif
  }

  // Ignore any leak related to the memory pointed to by `ptr`.
  template <typename T>
  static ATTRIBUTE_ARTIFICIAL ALWAYSINLINE T* ignore_leak(T* ptr) {
#ifdef LEAK_SANITIZER
    __lsan_ignore_object(ptr);
#endif
    return ptr;
  }
};

#endif // SHARE_LSAN_LSAN_HPP

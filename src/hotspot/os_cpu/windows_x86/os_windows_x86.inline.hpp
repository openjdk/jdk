/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef OS_CPU_WINDOWS_X86_OS_WINDOWS_X86_INLINE_HPP
#define OS_CPU_WINDOWS_X86_OS_WINDOWS_X86_INLINE_HPP

#include "runtime/os.hpp"
#include "os_windows.hpp"

#define HAVE_PLATFORM_PRINT_NATIVE_STACK 1
inline bool os::platform_print_native_stack(outputStream* st, const void* context,
                                     char *buf, int buf_size, address& lastpc) {
  return os::win32::platform_print_native_stack(st, context, buf, buf_size, lastpc);
}

inline jlong os::rdtsc() {
  // 32 bit: 64 bit result in edx:eax
  // 64 bit: 64 bit value in rax
  uint64_t res;
  res = (uint64_t)__rdtsc();
  return (jlong)res;
}

inline bool os::register_code_area(char *low, char *high) {
  return os::win32::register_code_area(low, high);
}

#endif // OS_CPU_WINDOWS_X86_OS_WINDOWS_X86_INLINE_HPP

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
 */

#ifndef SHARE_GC_Z_ZONERROR_HPP
#define SHARE_GC_Z_ZONERROR_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/vmError.hpp"

#define z_expand_64_1(first)      uint64_t(first)
#define z_expand_64_2(first, ...) z_expand_64_1(first), z_expand_64_1(__VA_ARGS__)
#define z_expand_64_3(first, ...) z_expand_64_1(first), z_expand_64_2(__VA_ARGS__)
#define z_expand_64_4(first, ...) z_expand_64_1(first), z_expand_64_3(__VA_ARGS__)
#define z_expand_64_5(first, ...) z_expand_64_1(first), z_expand_64_4(__VA_ARGS__)
#define z_expand_64_6(first, ...) z_expand_64_1(first), z_expand_64_5(__VA_ARGS__)

#define z_expand_format_64_1(first)      #first ": " UINT64_FORMAT_X " "
#define z_expand_format_64_2(first, ...) z_expand_format_64_1(first) z_expand_format_64_1(__VA_ARGS__)
#define z_expand_format_64_3(first, ...) z_expand_format_64_1(first) z_expand_format_64_2(__VA_ARGS__)
#define z_expand_format_64_4(first, ...) z_expand_format_64_1(first) z_expand_format_64_3(__VA_ARGS__)
#define z_expand_format_64_5(first, ...) z_expand_format_64_1(first) z_expand_format_64_4(__VA_ARGS__)
#define z_expand_format_64_6(first, ...) z_expand_format_64_1(first) z_expand_format_64_5(__VA_ARGS__)

#define z_on_error_capture_64(N, ...)                  \
  OnVMError on_error([&](outputStream* st) {           \
    st->print("Captured: "                             \
              z_expand_format_64_##N(__VA_ARGS__),     \
              z_expand_64_##N(__VA_ARGS__));           \
  })

#define z_on_error_capture_64_1(...) z_on_error_capture_64(1, __VA_ARGS__)
#define z_on_error_capture_64_2(...) z_on_error_capture_64(2, __VA_ARGS__)
#define z_on_error_capture_64_3(...) z_on_error_capture_64(3, __VA_ARGS__)
#define z_on_error_capture_64_4(...) z_on_error_capture_64(4, __VA_ARGS__)
#define z_on_error_capture_64_5(...) z_on_error_capture_64(5, __VA_ARGS__)
#define z_on_error_capture_64_6(...) z_on_error_capture_64(6, __VA_ARGS__)

#ifdef ASSERT
#define z_assert_capture_64(N, ...) z_on_error_capture_64(N, __VA_ARGS__)
#else
#define z_assert_capture_64(N, ...)
#endif

#define z_assert_capture_64_1(...) z_assert_capture_64(1, __VA_ARGS__)
#define z_assert_capture_64_2(...) z_assert_capture_64(2, __VA_ARGS__)
#define z_assert_capture_64_3(...) z_assert_capture_64(3, __VA_ARGS__)
#define z_assert_capture_64_4(...) z_assert_capture_64(4, __VA_ARGS__)
#define z_assert_capture_64_5(...) z_assert_capture_64(5, __VA_ARGS__)
#define z_assert_capture_64_6(...) z_assert_capture_64(6, __VA_ARGS__)

#endif // SHARE_GC_Z_ZONERROR_HPP

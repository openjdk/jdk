/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022 SAP SE. All rights reserved.
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

#ifndef SHARE_UTILITIES_PARSE_INTEGER_HPP
#define SHARE_UTILITIES_PARSE_INTEGER_HPP

#include "metaprogramming/enableIf.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#include <errno.h>
#include <limits>
#include <stdlib.h>

template <typename T, ENABLE_IF(std::is_signed<T>::value), ENABLE_IF(sizeof(T) == 4)> // signed 32-bit
inline bool parse_integer_impl(const char *s, char **endptr, int base, T* result) {
  // Don't use strtol -- on 64-bit builds, "long" could be either 32- or 64-bits
  // so the range tests could be tautological and might cause compiler warnings.
  STATIC_ASSERT(sizeof(long long) >= 8); // C++ specification
  errno = 0; // errno is thread safe
  long long v = strtoll(s, endptr, base);
  if (errno != 0 || v < min_jint || v > max_jint) {
    return false;
  }
  *result = static_cast<T>(v);
  return true;
}

template <typename T, ENABLE_IF(!std::is_signed<T>::value), ENABLE_IF(sizeof(T) == 4)> // unsigned 32-bit
inline bool parse_integer_impl(const char *s, char **endptr, int base, T* result) {
  if (s[0] == '-') {
    return false;
  }
  // Don't use strtoul -- same reason as above.
  STATIC_ASSERT(sizeof(unsigned long long) >= 8); // C++ specification
  errno = 0; // errno is thread safe
  unsigned long long v = strtoull(s, endptr, base);
  if (errno != 0 || v > max_juint) {
    return false;
  }
  *result = static_cast<T>(v);
  return true;
}

template <typename T, ENABLE_IF(std::is_signed<T>::value), ENABLE_IF(sizeof(T) == 8)> // signed 64-bit
inline bool parse_integer_impl(const char *s, char **endptr, int base, T* result) {
  errno = 0; // errno is thread safe
  *result = strtoll(s, endptr, base);
  return errno == 0;
}

template <typename T, ENABLE_IF(!std::is_signed<T>::value), ENABLE_IF(sizeof(T) == 8)> // unsigned 64-bit
inline bool parse_integer_impl(const char *s, char **endptr, int base, T* result) {
  if (s[0] == '-') {
    return false;
  }
  errno = 0; // errno is thread safe
  *result = strtoull(s, endptr, base);
  return errno == 0;
}

template <typename T>
inline bool parse_integer(const char *s, char **endptr, int base, T* result) {
  bool rc = parse_integer_impl(s, endptr, base, result);
  // We fail also if we have not parsed anything
  rc = rc && (*endptr > s);
  return rc;
}

#endif // SHARE_UTILITIES_COPY_HPP

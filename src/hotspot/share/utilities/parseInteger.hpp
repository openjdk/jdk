/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

// *************************************************************************
// ** Attention compatibility!                                            **
// ** These functions are used to parse JVM arguments (-XX). Be careful   **
// ** with behavioral changes here.                                       **
// *************************************************************************


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


// Helper for parse_memory_size
template<typename T>
inline bool multiply_by_1k(T& n) {
  if (n >= std::numeric_limits<T>::min() / 1024 &&
      n <= std::numeric_limits<T>::max() / 1024) {
    n *= 1024;
    return true;
  } else {
    return false;
  }
}

// Parses a memory size in the form "<number>[<unit>]" with valid units being
// "k", "K", "m", "M", "g", "G", "t", "T". Unit omitted means bytes. If unit is given,
// no space is allowed between number and unit. Number can be in either decimal form
// or in hexadecimal form, the latter must start with "0x".
//
// Valid template arguments for T are signed/unsigned 32/64-bit values.
//
// This function will parse until it encounters unparseable parts, then
// stop. If it read no valid memory size, it will fail.
//
// Example: "1024M:oom" will yield true, result=1G, endptr pointing to ":oom"

template<typename T>
static bool parse_integer(const char *s, char **endptr, T* result) {

  if (!isdigit(s[0]) && s[0] != '-') {
    // strtoll/strtoull may allow leading spaces. Forbid it.
    return false;
  }

  T n = 0;
  bool is_hex = (s[0] == '0' && (s[1] == 'x' || s[1] == 'X')) ||
                (s[0] == '-' && s[1] == '0' && (s[2] == 'x' || s[3] == 'X'));
  char* remainder;

  if (!parse_integer_impl<T>(s, &remainder, (is_hex ? 16 : 10), &n)) {
    return false;
  }
  // Nothing parsed? That is an error too.
  if (remainder == s) {
    return false;
  }

  switch (*remainder) {
    case 'T': case 't':
      if (!multiply_by_1k(n)) return false;
      // fall-through
    case 'G': case 'g':
      if (!multiply_by_1k(n)) return false;
      // fall-through
    case 'M': case 'm':
      if (!multiply_by_1k(n)) return false;
      // fall-through
    case 'K': case 'k':
      if (!multiply_by_1k(n)) return false;
      remainder ++; // shave off parsed unit char
      break;
    default:
      // nothing. Return remainder unparsed.
      break;
  };

  *result = n;
  *endptr = remainder;
  return true;
}

// Same as parse_integer(const char *s, char **endptr, T* result), but does not allow unrecognizable
// characters. No remainder are allowed here.
// Example: "100m" - okay, "100m:oom" -> not okay
template<typename T>
static bool parse_integer(const char *s, T* result) {
  char* remainder;
  bool rc = parse_integer(s, &remainder, result);
  rc = rc && (*remainder == '\0');
  return rc;
}

#endif // SHARE_UTILITIES_PARSE_INTEGER_HPP

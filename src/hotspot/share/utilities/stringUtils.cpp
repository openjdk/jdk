/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "jvm_io.h"
#include "utilities/debug.hpp"
#include "utilities/stringUtils.hpp"

#include <ctype.h>
#include <string.h>

int StringUtils::replace_no_expand(char* string, const char* from, const char* to) {
  int replace_count = 0;
  size_t from_len = strlen(from);
  size_t to_len = strlen(to);
  assert(from_len >= to_len, "must not expand input");

  for (char* dst = string; *dst && (dst = strstr(dst, from)) != nullptr;) {
    char* left_over = dst + from_len;
    memmove(dst, to, to_len);                       // does not copy trailing 0 of <to>
    dst += to_len;                                  // skip over the replacement.
    memmove(dst, left_over, strlen(left_over) + 1); // copies the trailing 0 of <left_over>
    ++ replace_count;
  }

  return replace_count;
}

double StringUtils::similarity(const char* str1, size_t len1, const char* str2, size_t len2) {
  assert(str1 != nullptr && str2 != nullptr, "sanity");

  // filter out zero-length strings else we will underflow on len-1 below
  if (len1 == 0 || len2 == 0) {
    return 0.0;
  }

  size_t total = len1 + len2;
  size_t hit = 0;

  for (size_t i = 0; i < len1 - 1; i++) {
    for (size_t j = 0; j < len2 - 1; j++) {
      if ((str1[i] == str2[j]) && (str1[i+1] == str2[j+1])) {
        ++hit;
        break;
      }
    }
  }

  return 2.0 * (double) hit / (double) total;
}

template <bool CASE_SENSITIVE>
inline static bool _is_wildcard_match(const char* pattern, const char* str) {
  // Match leading stars
  if (*pattern == '*') {
    while (*pattern == '*') { // collapse consective stars
      pattern++;
    }
    if (*pattern == '\0') {
      return true;
    }
    while (*str != '\0') {
      // If the star matches a prefix, does the rest of the pattern match the rest of the string?
      if (_is_wildcard_match<CASE_SENSITIVE>(pattern, str)) {
        return true;
      } else {
        str ++;
      }
    }
    
    return false;
  }

  while (*pattern != '\0' && *str != '\0') {
    // Match regular characters until we see a star.
    assert(*pattern != '*', "must be");
    char p = *pattern;
    char s = *str;
    if (CASE_SENSITIVE) {
      if (p != s) {
        return false;
      }
    } else {
      if (tolower(p) != tolower(s)) {
        return false;
      }
    }
    pattern ++;
    str ++;
    if (*pattern == '*') {
      return _is_wildcard_match<CASE_SENSITIVE>(pattern, str);
    }
  }

  return *pattern == '\0' && *str == '\0';
}

bool StringUtils::is_wildcard_match(const char* pattern, const char* str) {
  return _is_wildcard_match</*case sensitive = */true>(pattern, str);
}

bool StringUtils::is_wildcard_match_nocase(const char* pattern, const char* str) {
  return _is_wildcard_match</*case sensitive = */false>(pattern, str);
}

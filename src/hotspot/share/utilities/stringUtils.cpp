/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
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

const char* StringUtils::strstr_nocase(const char* haystack, const char* needle) {
  if (needle[0] == '\0') {
    return haystack; // empty needle matches with anything
  }
  for (size_t i = 0; haystack[i] != '\0'; i++) {
    bool matches = true;
    for (size_t j = 0; needle[j] != '\0'; j++) {
      if (haystack[i + j] == '\0') {
        return nullptr; // hit end of haystack, abort
      }
      if (tolower(haystack[i + j]) != tolower(needle[j])) {
        matches = false;
        break; // abort, try next i
      }
    }
    if (matches) {
      return &haystack[i]; // all j were ok for this i
    }
  }
  return nullptr; // no i was a match
}

bool StringUtils::is_star_match(const char* star_pattern, const char* str) {
  const int N = 1000;
  char pattern[N]; // copy pattern into this to ensure null termination
  jio_snprintf(pattern, N, "%s", star_pattern);// ensures null termination
  char buf[N]; // copy parts of pattern into this
  const char* str_idx = str;
  const char* pattern_idx = pattern;
  while (strlen(pattern_idx) > 0) {
    // find next section in pattern
    const char* pattern_part_end = strstr(pattern_idx, "*");
    const char* pattern_part = pattern_idx;
    if (pattern_part_end != nullptr) { // copy part into buffer
      size_t pattern_part_len = pattern_part_end-pattern_part;
      strncpy(buf, pattern_part, pattern_part_len);
      buf[pattern_part_len] = '\0'; // end of string
      pattern_part = buf;
    }
    // find this section in s, case insensitive
    const char* str_match = strstr_nocase(str_idx, pattern_part);
    if (str_match == nullptr) {
      return false; // r_part did not match - abort
    }
    size_t match_len = strlen(pattern_part);
    // advance to match position plus part length
    str_idx = str_match + match_len;
    // advance by part length and "*"
    pattern_idx += match_len + (pattern_part_end == nullptr ? 0 : 1);
  }
  return true; // all parts of pattern matched
}

StringUtils::CommaSeparatedStringIterator::~CommaSeparatedStringIterator() {
  FREE_C_HEAP_ARRAY(char, _list);
}

ccstrlist StringUtils::CommaSeparatedStringIterator::canonicalize(ccstrlist option_value) {
  char* canonicalized_list = NEW_C_HEAP_ARRAY(char, strlen(option_value) + 1, mtCompiler);
  int i = 0;
  char current;
  while ((current = option_value[i]) != '\0') {
    if (current == '\n' || current == ' ') {
      canonicalized_list[i] = ',';
    } else {
      canonicalized_list[i] = current;
    }
    i++;
  }
  canonicalized_list[i] = '\0';
  return canonicalized_list;
}

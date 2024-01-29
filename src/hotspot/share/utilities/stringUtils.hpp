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

#ifndef SHARE_UTILITIES_STRINGUTILS_HPP
#define SHARE_UTILITIES_STRINGUTILS_HPP

#include "memory/allStatic.hpp"

#ifdef _WINDOWS
  // strtok_s is the Windows thread-safe equivalent of POSIX strtok_r
# define strtok_r strtok_s
#endif

class StringUtils : AllStatic {
public:
  // Replace the substring <from> with another string <to>. <to> must be
  // no longer than <from>. The input string is modified in-place.
  //
  // Replacement is done in a single pass left-to-right. So replace_no_expand("aaa", "aa", "a")
  // will result in "aa", not "a".
  //
  // Returns the count of substrings that have been replaced.
  static int replace_no_expand(char* string, const char* from, const char* to);

  // Compute string similarity based on Dice's coefficient
  static double similarity(const char* str1, size_t len1, const char* str2, size_t len2);

  // Find needle in haystack, case insensitive.
  // Custom implementation of strcasestr, as it is not available on windows.
  static const char* strstr_nocase(const char* haystack, const char* needle);

  // Check if str matches the star_pattern.
  // eg. str "_abc____def__" would match pattern "abc*def".
  // The matching is case insensitive.
  static bool is_star_match(const char* star_pattern, const char* str);

  class CommaSeparatedStringIterator {
  private:
    char* _token;
    char* _saved_ptr;
    char* _list;

  public:
    CommaSeparatedStringIterator(ccstrlist option) {
      // Immediately make a private copy of option, and
      // replace spaces and newlines with comma.
      _list = (char*) canonicalize(option);
      _saved_ptr = _list;
      _token = strtok_r(_saved_ptr, ",", &_saved_ptr);
    }

    ~CommaSeparatedStringIterator();

    const char* operator*() const { return _token; }

    CommaSeparatedStringIterator& operator++() {
      _token = strtok_r(nullptr, ",", &_saved_ptr);
      return *this;
    }

    ccstrlist canonicalize(ccstrlist option_value);
  };
};

#endif // SHARE_UTILITIES_STRINGUTILS_HPP

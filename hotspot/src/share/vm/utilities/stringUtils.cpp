/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/stringUtils.hpp"

int StringUtils::replace_no_expand(char* string, const char* from, const char* to) {
  int replace_count = 0;
  size_t from_len = strlen(from);
  size_t to_len = strlen(to);
  assert(from_len >= to_len, "must not expand input");

  for (char* dst = string; *dst && (dst = strstr(dst, from)) != NULL;) {
    char* left_over = dst + from_len;
    memmove(dst, to, to_len);                       // does not copy trailing 0 of <to>
    dst += to_len;                                  // skip over the replacement.
    memmove(dst, left_over, strlen(left_over) + 1); // copies the trailing 0 of <left_over>
    ++ replace_count;
  }

  return replace_count;
}

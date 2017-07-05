/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "utilities/utf8.hpp"
#include "unittest.hpp"

TEST(utf8, length) {
  char res[60];
  jchar str[20];

  for (int i = 0; i < 20; i++) {
    str[i] = 0x0800; // char that is 2B in UTF-16 but 3B in UTF-8
  }
  str[19] = (jchar) '\0';

  // The resulting string in UTF-8 is 3*19 bytes long, but should be truncated
  UNICODE::as_utf8(str, 19, res, 10);
  ASSERT_EQ(strlen(res), (size_t) 9) << "string should be truncated here";

  UNICODE::as_utf8(str, 19, res, 18);
  ASSERT_EQ(strlen(res), (size_t) 15) << "string should be truncated here";

  UNICODE::as_utf8(str, 19, res, 20);
  ASSERT_EQ(strlen(res), (size_t) 18) << "string should be truncated here";

  // Test with an "unbounded" buffer
  UNICODE::as_utf8(str, 19, res, INT_MAX);
  ASSERT_EQ(strlen(res), (size_t) 3 * 19) << "string should end here";
}

/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/resourceArea.hpp"
#include "utilities/stringUtils.hpp"
#include "unittest.hpp"

TEST(StringUtils, similarity) {
  const char* str1 = "the quick brown fox jumps over the lazy dog";
  const char* str2 = "the quick brown fox jumps over the lazy doh";
  EXPECT_NEAR(0.95349, StringUtils::similarity(str1, strlen(str1), str2, strlen(str2)), 1e-5);
}

TEST_VM(StringUtils, tr_delete) {
  ResourceMark rm;
  const char* str = "the quick brown fox jumps over the lazy dog.";
  char* buf = os::strdup(str);
  size_t sz;

  sz = StringUtils::tr_delete(buf, " \n.");
  EXPECT_EQ(sz, (size_t)9);
  EXPECT_STREQ(buf, "thequickbrownfoxjumpsoverthelazydog");

  sz = StringUtils::tr_delete(buf, "");
  EXPECT_EQ(sz, (size_t)0);

  sz = StringUtils::tr_delete(NULL, NULL);
  EXPECT_EQ(sz, (size_t)0);

  char buf2[4] = {'a', 'b', 'c', '\0'};
  sz = StringUtils::tr_delete(buf2, "efg");
  EXPECT_EQ(sz, (size_t)0);
  EXPECT_STREQ(buf2, "abc");

  sz = StringUtils::tr_delete(buf2, "abc");
  EXPECT_EQ(sz, (size_t)3);
  EXPECT_STREQ(buf2, "");

  sz = StringUtils::tr_delete(buf2, "abc");
  EXPECT_EQ(sz, (size_t)0);

  os::free(buf);
}

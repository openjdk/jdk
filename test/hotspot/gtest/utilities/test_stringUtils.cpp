/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/resourceArea.hpp"
#include "utilities/stringUtils.hpp"
#include "unittest.hpp"

TEST(StringUtils, similarity) {
  const char* str1 = "the quick brown fox jumps over the lazy dog";
  const char* str2 = "the quick brown fox jumps over the lazy doh";
  EXPECT_NEAR(0.95349, StringUtils::similarity(str1, strlen(str1), str2, strlen(str2)), 1e-5);
}

static size_t count_char(const char* s, size_t len, char ch) {
  size_t cnt = 0;

  for (size_t i = 0; i < len; ++i) {
    if (s[i] == ch) {
      cnt++;
    }
  }
  return cnt;
}

static size_t count_char(const stringStream& ss, char ch) {
  return count_char(ss.base(), ss.size(), ch);
}

static const char* const lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit,\n"            \
                                 "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n"  \
                                 "Lacinia at quis risus sed vulputate odio ut enim blandit.\n"           \
                                 "Amet risus nullam eget felis eget.\n"                                  \
                                 "Viverra orci sagittis eu volutpat odio facilisis mauris sit.\n"        \
                                 "Erat velit scelerisque in dictum non.\n";


TEST_VM(StringUtils, replace_no_expand) {
  ResourceMark rm;
  stringStream ss;

  ss.print_raw(lorem);
  size_t newlines = count_char(ss, '\n');
  char* s2 = ss.as_string(false);
  int deleted = StringUtils::replace_no_expand(s2, "\n", "");
  ASSERT_EQ(newlines, (size_t)deleted);

  newlines = count_char(s2, strlen(s2), '\n');
  ASSERT_EQ(newlines, (size_t)0);

  deleted = StringUtils::replace_no_expand(s2, "\n", "");
  ASSERT_EQ(deleted, 0);
}

TEST_VM(StringUtils, find_trailing_number) {
  static const struct {
    const char* s; int expected;
  } totest[] = {
      { "",       -1 },
      { "Hallo",  -1 },
      { "123",     0 },
      { "A123",    1 },
      { "123A",   -1 },
      { "C2 CompilerThread12", 17 },
      { nullptr, -1}
  };
  for (int i = 0; totest[i].s != nullptr; i++) {
    ASSERT_EQ( StringUtils::find_trailing_number(totest[i].s), totest[i].expected );
  }
}

TEST_VM(StringUtils, abbreviate_preserve_trailing_number) {
  static const struct {
    const char* s; size_t outlen; const char* expected;
  } totest[] = {
      // No truncation needed
      { "",       10, "" },
      { "Hallo",  10, "Hallo" },
      { "123",    10, "123" },
      { "C2 CompilerThread1267223",    100 + 1,  "C2 CompilerThread1267223" },
      // Output buffer too short, plain truncation expected:
      { "C2 CompilerThread12",           7 + 1,  "C2 Comp" },
      // Output buffer long enough to abbreviate:
      //                                     .123456789.123456789.1234567899
      { "C2 CompilerThread12",          10 + 1,  "C2 Com..12" },
      { "C2 CompilerThread12",          15 + 1,  "C2 Compiler..12" },
      { "C2 CompilerThread",            10 + 1,  "C2 Compile" },
      { "C2 CompilerThread1",           15 + 1,  "C2 CompilerT..1" },
      { "C2 CompilerThread1267223",     15 + 1,  "C2 Com..1267223" },
      // Number would be eating up more than half of output len, start of number is sacrificed:
      { "C2 CompilerThread1334267223",  15 + 1,  "C2 Com..4267223" },
      { nullptr, 0, nullptr }
  };
  char out[100 + 1];
  for (int i = 0; totest[i].s != nullptr; i++) {
    assert(sizeof(out) >= totest[i].outlen, "Sanity");
    EXPECT_STREQ(StringUtils::abbreviate_preserve_trailing_number(totest[i].s, out, totest[i].outlen),
                 totest[i].expected) << " for case " << i;
  }
}

/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

static bool is_wildcard_match(const char* pattern, const char* string) {
  return StringUtils::is_wildcard_match(pattern, string);
}

static bool is_wildcard_match_nocase(const char* pattern, const char* string) {
  return StringUtils::is_wildcard_match_nocase(pattern, string);
}

TEST(StringUtils, is_wildcard_match) {
  ASSERT_EQ(is_wildcard_match("abc", "abc"), true);
  ASSERT_EQ(is_wildcard_match("ab*", "abc"), true);
  ASSERT_EQ(is_wildcard_match("a*c", "abc"), true);
  ASSERT_EQ(is_wildcard_match("a**", "abc"), true);
  ASSERT_EQ(is_wildcard_match("*bc", "abc"), true);
  ASSERT_EQ(is_wildcard_match("*b*", "abc"), true);
  ASSERT_EQ(is_wildcard_match("**c", "abc"), true);
  ASSERT_EQ(is_wildcard_match("***", "abc"), true);

  ASSERT_EQ(is_wildcard_match("abc", "ABC"), false);

  ASSERT_EQ(is_wildcard_match_nocase("abc", "ABC"), true);
  ASSERT_EQ(is_wildcard_match_nocase("ab*", "ABC"), true);
  ASSERT_EQ(is_wildcard_match_nocase("a*c", "ABC"), true);
  ASSERT_EQ(is_wildcard_match_nocase("a**", "ABC"), true);
  ASSERT_EQ(is_wildcard_match_nocase("*bc", "ABC"), true);
  ASSERT_EQ(is_wildcard_match_nocase("*b*", "ABC"), true);
  ASSERT_EQ(is_wildcard_match_nocase("**c", "ABC"), true);
  ASSERT_EQ(is_wildcard_match_nocase("***", "ABC"), true);

  // Must match full string (no implicit leading/trailing stars)
  ASSERT_EQ(is_wildcard_match("bc*",  "abcd"), false);
  ASSERT_EQ(is_wildcard_match("*bc",  "abcd"), false);

  // Multiple stars
  ASSERT_EQ(is_wildcard_match("***bcd*", "abcd"), true);
  ASSERT_EQ(is_wildcard_match("***bc****", "abcd"), true);
  ASSERT_EQ(is_wildcard_match("***ccd", "abcd"), false);

  // Some common cases
  ASSERT_EQ(is_wildcard_match("java/*/Object",             "java/lang/Object"), true);
  ASSERT_EQ(is_wildcard_match("java/*/Class*",             "java/lang/ClassLoader"), true);
  ASSERT_EQ(is_wildcard_match("java/*/Class",              "java/lang/ClassLoader"), false);

  ASSERT_EQ(is_wildcard_match_nocase("java/*/object",      "java/lang/Object"), true);
  ASSERT_EQ(is_wildcard_match_nocase("java/*/class*",      "java/lang/ClassLoader"), true);
  ASSERT_EQ(is_wildcard_match_nocase("java/*/class",       "java/lang/ClassLoader"), false);

  // Special chars that are commonly used in Symbols - bracket, semi-colon, <> and ()
  ASSERT_EQ(is_wildcard_match("[Ljava/*/Object;",          "[Ljava/lang/Object;"), true);
  ASSERT_EQ(is_wildcard_match("[*Ljava/*/Object;",         "[[[[[Ljava/lang/Object;"), true);

  ASSERT_EQ(is_wildcard_match_nocase("[LJava/*/object;",   "[Ljava/lang/Object;"), true);
  ASSERT_EQ(is_wildcard_match_nocase("[*LJava/*/object;",  "[[[[[Ljava/lang/Object;"), true);

  ASSERT_EQ(is_wildcard_match_nocase("<*init>",            "<init>"), true);
  ASSERT_EQ(is_wildcard_match_nocase("<*init>",            "<clinit>"), true);

  ASSERT_EQ(is_wildcard_match_nocase("<*init>(L*ject;)L*", "<init>(Ljava/lang/String;III;Ljava/lang/Object;)V"), false);
  ASSERT_EQ(is_wildcard_match_nocase("<*init>(L*ject;)L*", "<init>(Ljava/lang/String;III;Ljava/lang/Object;)Ljava/lang/String;"), true);

  // Performance
  // Oops - this is slow ... see https://www.codeproject.com/Articles/5163931/Fast-String-Matching-with-Wildcards-Globs-and-Giti
  //ASSERT_EQ(is_wildcard_match("a*a*a*a*a*a*a*a*b", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), true);
}

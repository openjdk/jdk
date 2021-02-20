/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019 SAP SE. All rights reserved.
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
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "utilities/stringUtils.hpp"

#include "unittest.hpp"

static size_t print_lorem(outputStream* st) {
  // Create a ResourceMark just to make sure the stream does not use ResourceArea
  ResourceMark rm;
  static const char* const lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, "
      "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Lacinia at quis "
      "risus sed vulputate odio ut enim blandit. Amet risus nullam eget felis eget. Viverra "
      "orci sagittis eu volutpat odio facilisis mauris sit. Erat velit scelerisque in dictum non.";
  static const size_t len_lorem = strlen(lorem);
  // Randomly alternate between short and long writes at a ratio of 9:1.
  const bool short_write = (os::random() % 10) > 0;
  const size_t len = os::random() % (short_write ? 10 : len_lorem);
  st->write(lorem, len);
  return len;
}

static void test_stringStream_is_zero_terminated(const stringStream* ss) {
  ASSERT_EQ(ss->base()[ss->size()], '\0');
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

static size_t count_char(const stringStream* ss, char ch) {
  return count_char(ss->as_string(), ss->size(), ch);
}

static void test_stringStream_tr_delete(stringStream* ss) {
  ResourceMark rm;
  size_t whitespaces = count_char(ss, ' ');

  char* s2 = os::strdup(ss->as_string());
  size_t deleted = StringUtils::tr_delete(s2, " ");
  ASSERT_EQ(whitespaces, deleted);

  whitespaces = count_char(s2, strlen(s2), ' ');
  ASSERT_EQ(whitespaces, (size_t)0);

  StringUtils::tr_delete(s2, "mno");
  size_t t = count_char(s2, strlen(s2), 'm');
  ASSERT_EQ(t, (size_t)0);
  t = count_char(s2, strlen(s2), 'n');
  ASSERT_EQ(t, (size_t)0);
  t = count_char(s2, strlen(s2), 'o');
  ASSERT_EQ(t, (size_t)0);

  os::free(s2);
}

static void do_test_stringStream(stringStream* ss, size_t expected_cap) {
  test_stringStream_is_zero_terminated(ss);
  size_t written = 0;
  for (int i = 0; i < 1000; i ++) {
    written += print_lorem(ss);
    if (expected_cap > 0 && written >= expected_cap) {
      ASSERT_EQ(ss->size(), expected_cap - 1);
    } else {
      ASSERT_EQ(ss->size(), written);
    }
    // Internal buffer should always be zero-terminated.
    test_stringStream_is_zero_terminated(ss);
  }

  test_stringStream_tr_delete(ss);

  // Reset should zero terminate too
  ss->reset();
  ASSERT_EQ(ss->size(), (size_t)0);
  test_stringStream_is_zero_terminated(ss);
}

TEST_VM(ostream, stringStream_dynamic_start_with_internal_buffer) {
  stringStream ss;
  do_test_stringStream(&ss, 0);
  ss.reset();
  do_test_stringStream(&ss, 0);
}

TEST_VM(ostream, stringStream_dynamic_start_with_malloced_buffer) {
  stringStream ss(128);
  do_test_stringStream(&ss, 0);
  ss.reset();
  do_test_stringStream(&ss, 0);
}

TEST_VM(ostream, stringStream_static) {
  char buffer[128 + 1];
  char* canary_at = buffer + sizeof(buffer) - 1;
  *canary_at = 'X';
  size_t stream_buf_size = sizeof(buffer) - 1;
  stringStream ss(buffer, stream_buf_size);
  do_test_stringStream(&ss, stream_buf_size);
  ASSERT_EQ(*canary_at, 'X'); // canary
}

TEST_VM(ostream, bufferedStream_static) {
  char buf[100 + 1];
  char* canary_at = buf + sizeof(buf) - 1;
  *canary_at = 'X';
  size_t stream_buf_size = sizeof(buf) - 1;
  bufferedStream bs(buf, stream_buf_size);
  size_t written = 0;
  for (int i = 0; i < 100; i ++) {
    written += print_lorem(&bs);
    if (written < stream_buf_size) {
      ASSERT_EQ(bs.size(), written);
    } else {
      ASSERT_EQ(bs.size(), stream_buf_size - 1);
    }
  }
  ASSERT_EQ(*canary_at, 'X'); // canary
}

TEST_VM(ostream, bufferedStream_dynamic_small) {
  bufferedStream bs(1); // small to excercise realloc.
  size_t written = 0;
  // The max cap imposed is 100M, we should be safely below this in this test.
  for (int i = 0; i < 10; i ++) {
    written += print_lorem(&bs);
    ASSERT_EQ(bs.size(), written);
  }
}

/* Activate to manually test bufferedStream dynamic cap.

TEST_VM(ostream, bufferedStream_dynamic_large) {
  bufferedStream bs(1); // small to excercise realloc.
  size_t written = 0;
  // The max cap imposed is 100M. Writing this much should safely hit it.
  // Note that this will assert in debug builds which is the expected behavior.
  size_t expected_cap_at = 100 * M;
  for (int i = 0; i < 10000000; i ++) {
    written += print_lorem(&bs);
    if (written < expected_cap_at) {
      ASSERT_EQ(bs.size(), written);
    } else {
      ASSERT_EQ(bs.size(), expected_cap_at - 1);
    }
  }
}

*/





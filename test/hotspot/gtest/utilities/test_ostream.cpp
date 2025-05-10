/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/defaultStream.hpp"
#include "utilities/ostream.hpp"

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

TEST_VM(ostream, bufferedStream_dynamic_small) {
  bufferedStream bs(1); // small to excercise realloc.
  size_t written = 0;
  // The max cap imposed is 100M, we should be safely below this in this test.
  for (int i = 0; i < 10; i ++) {
    written += print_lorem(&bs);
    ASSERT_EQ(bs.size(), written);
  }
}

TEST_VM(ostream, streamIndentor) {
  stringStream ss;

  {
    StreamIndentor si(&ss, 5);
    ss.print("ABC");
    ss.print("DEF");
    ss.cr();
    ss.print_cr("0123");
    {
      StreamIndentor si(&ss, 5);
      ss.print_cr("4567");
      ss.print_raw("89AB");
      ss.print_raw("CDEXXXX", 3);
      ss.print_raw_cr("XYZ");
    }
    ss.print("%u", 100);
    ss.print_raw("KB");
    ss.cr();
  }
  ss.print("end");

  EXPECT_STREQ(ss.base(),
      "     ABCDEF\n"
      "     0123\n"
      "          4567\n"
      "          89ABCDEXYZ\n"
      "     100KB\n"
      "end"
  );
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

// Test helper for do_vsnprintf
class outputStream::TestSupport : AllStatic {

  // Shared constants and variables for all subtests.
  static const size_t buflen = 11;
  static char buffer[buflen];
  static const size_t max_len = buflen - 1;
  static size_t result_len;
  static const char* result;

  static void reset() {
    result_len = 0;
    result = nullptr;
    buffer[0] = '\0';
  }

  static const char* test(char* buf, size_t len, bool add_cr,
                          size_t& rlen, const char* format, ...) {
    va_list ap;
    va_start(ap, format);
    const char* res = do_vsnprintf(buf, len, format, ap, add_cr, rlen);
    va_end(ap);
    return res;
  }

 public:
  // Case set 1: constant string with no format specifiers
  static void test_constant_string() {
    reset();
    // Case 1-1: no cr, no truncation, excess capacity
    {
      const char* str = "012345678";
      size_t initial_len = strlen(str);
      ASSERT_TRUE(initial_len < max_len);
      result = test(buffer, buflen, false, result_len, str);
      ASSERT_EQ(result, str);
      ASSERT_EQ(strlen(result), result_len);
    }
    reset();
    // Case 1-2: no cr, no truncation, exact capacity
    {
      const char* str = "0123456789";
      size_t initial_len = strlen(str);
      ASSERT_EQ(initial_len, max_len);
      result = test(buffer, buflen, false, result_len, str);
      ASSERT_EQ(result, str);
      ASSERT_EQ(strlen(result), result_len);
    }
    reset();
    // Case 1-3: no cr, no truncation, exceeds capacity
    {
      const char* str = "0123456789A";
      size_t initial_len = strlen(str);
      ASSERT_TRUE(initial_len > max_len);
      result = test(buffer, buflen, false, result_len, str);
      ASSERT_EQ(result, str);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len);
    }
    reset();
    // Case 1-4: add cr, no truncation, excess capacity
    {
      const char* str = "01234567";
      size_t initial_len = strlen(str);
      ASSERT_TRUE(initial_len < max_len);
      result = test(buffer, buflen, true, result_len, str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len + 1);
      ASSERT_TRUE(result_len <= max_len);
    }
    reset();
    // Case 1-5: add cr, no truncation, exact capacity
    {
      const char* str = "012345678";
      size_t initial_len = strlen(str);
      ASSERT_TRUE(initial_len < max_len);
      result = test(buffer, buflen, true, result_len, str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len + 1);
      ASSERT_TRUE(result_len <= max_len);
    }
    reset();
    // Case 1-6: add cr, truncation
    {
      const char* str = "0123456789";
      size_t initial_len = strlen(str);
      ASSERT_EQ(initial_len, max_len);
      ::printf("Truncation warning expected: requires %d\n", (int)(initial_len + 1 + 1));
      result = test(buffer, buflen, true, result_len, str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len);
      ASSERT_TRUE(result_len <= max_len);
    }
  }

  // Case set 2: "%s" string
  static void test_percent_s_string() {
    reset();
    // Case 2-1: no cr, no truncation, excess capacity
    {
      const char* str = "012345678";
      size_t initial_len = strlen(str);
      ASSERT_TRUE(initial_len < max_len);
      result = test(buffer, buflen, false, result_len, "%s", str);
      ASSERT_EQ(result, str);
      ASSERT_EQ(strlen(result), result_len);
    }
    reset();
    // Case 2-2: no cr, no truncation, exact capacity
    {
      const char* str = "0123456789";
      size_t initial_len = strlen(str);
      ASSERT_EQ(initial_len, max_len);
      result = test(buffer, buflen, false, result_len, "%s", str);
      ASSERT_EQ(result, str);
      ASSERT_EQ(strlen(result), result_len);
    }
    reset();
    // Case 2-3: no cr, no truncation, exceeds capacity
    {
      const char* str = "0123456789A";
      size_t initial_len = strlen(str);
      ASSERT_TRUE(initial_len > max_len);
      result = test(buffer, buflen, false, result_len, "%s", str);
      ASSERT_EQ(result, str);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len);
    }
    reset();
    // Case 2-4: add cr, no truncation, excess capacity
    {
      const char* str = "01234567";
      size_t initial_len = strlen(str);
      ASSERT_TRUE(initial_len < max_len);
      result = test(buffer, buflen, true, result_len, "%s", str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len + 1);
      ASSERT_TRUE(result_len <= max_len);
    }
    reset();
    // Case 2-5: add cr, no truncation, exact capacity
    {
      const char* str = "012345678";
      size_t initial_len = strlen(str);
      ASSERT_TRUE(initial_len < max_len);
      result = test(buffer, buflen, true, result_len, "%s", str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len + 1);
      ASSERT_TRUE(result_len <= max_len);
    }
    reset();
    // Case 2-6: add cr, truncation
    {
      const char* str = "0123456789";
      size_t initial_len = strlen(str);
      ASSERT_EQ(initial_len, max_len);
      ::printf("Truncation warning expected: requires %d\n", (int)(initial_len + 1 + 1));
      result = test(buffer, buflen, true, result_len, "%s", str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len);
      ASSERT_TRUE(result_len <= max_len);
    }
  }

  // Case set 3: " %s" string - the space means we avoid the pass-through optimization and use vsnprintf
  static void test_general_string() {
    reset();
    // Case 3-1: no cr, no truncation, excess capacity
    {
      const char* str = "01234567";
      size_t initial_len = strlen(str) + 1;
      ASSERT_TRUE(initial_len < max_len);
      result = test(buffer, buflen, false, result_len, " %s", str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
    }
    reset();
    // Case 3-2: no cr, no truncation, exact capacity
    {
      const char* str = "012345678";
      size_t initial_len = strlen(str) + 1;
      ASSERT_EQ(initial_len, max_len);
      result = test(buffer, buflen, false, result_len, " %s", str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
    }
    reset();
    // Case 3-3: no cr, truncation
    {
      const char* str = "0123456789";
      size_t initial_len = strlen(str) + 1;
      ASSERT_TRUE(initial_len > max_len);
      ::printf("Truncation warning expected: requires %d\n", (int)(initial_len + 1));
      result = test(buffer, buflen, false, result_len, " %s", str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
    }
    reset();
    // Case 3-4: add cr, no truncation, excess capacity
    {
      const char* str = "0123456";
      size_t initial_len = strlen(str) + 1;
      ASSERT_TRUE(initial_len < max_len);
      result = test(buffer, buflen, true, result_len, " %s", str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len + 1);
      ASSERT_TRUE(result_len <= max_len);
    }
    reset();
    // Case 3-5: add cr, no truncation, exact capacity
    {
      const char* str = "01234567";
      size_t initial_len = strlen(str) + 1;
      ASSERT_TRUE(initial_len < max_len);
      result = test(buffer, buflen, true, result_len, " %s", str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len + 1);
      ASSERT_TRUE(result_len <= max_len);
    }
    reset();
    // Case 3-6: add cr, truncation
    {
      const char* str = "012345678";
      size_t initial_len = strlen(str) + 1;
      ASSERT_EQ(initial_len, max_len);
      ::printf("Truncation warning expected: requires %d\n", (int)(initial_len + 1 + 1));
      result = test(buffer, buflen, true, result_len, " %s", str);
      ASSERT_EQ(result, buffer);
      ASSERT_EQ(strlen(result), result_len);
      ASSERT_EQ(result_len, initial_len);
      ASSERT_TRUE(result_len <= max_len);
    }
  }

};

const size_t outputStream::TestSupport::max_len;
char outputStream::TestSupport::buffer[outputStream::TestSupport::buflen];
size_t outputStream::TestSupport::result_len = 0;
const char* outputStream::TestSupport::result = nullptr;

TEST_VM(ostream, do_vsnprintf_buffering) {
  outputStream::TestSupport::test_constant_string();
  outputStream::TestSupport::test_percent_s_string();
  outputStream::TestSupport::test_general_string();
}

/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "nmt/memflags.hpp"
#include "runtime/os.hpp"
#include "utilities/utf8.hpp"
#include "unittest.hpp"

static void stamp(char* p, size_t len) {
  if (len > 0) {
    ::memset(p, 'A', len);
  }
}

static bool test_stamp(const char* p, size_t len) {
  for (const char* q = p; q < p + len; q++) {
    if (*q != 'A') {
      return false;
    }
  }
  return true;
}

TEST_VM(utf8, jchar_length) {
  char res[60];
  jchar str[20];

  for (int i = 0; i < 20; i++) {
    str[i] = 0x0800; // char that is 2B in UTF-16 but 3B in UTF-8
  }
  str[19] = (jchar) '\0';

  // The resulting string in UTF-8 is 3*19 bytes long, but should be truncated
  stamp(res, sizeof(res));
  UNICODE::as_utf8(str, 19, res, 10);
  ASSERT_EQ(strlen(res), (size_t) 9) << "string should be truncated here";
  ASSERT_TRUE(test_stamp(res + 10, sizeof(res) - 10));

  stamp(res, sizeof(res));
  UNICODE::as_utf8(str, 19, res, 18);
  ASSERT_EQ(strlen(res), (size_t) 15) << "string should be truncated here";
  ASSERT_TRUE(test_stamp(res + 18, sizeof(res) - 18));

  stamp(res, sizeof(res));
  UNICODE::as_utf8(str, 19, res, 20);
  ASSERT_EQ(strlen(res), (size_t) 18) << "string should be truncated here";
  ASSERT_TRUE(test_stamp(res + 20, sizeof(res) - 20));

  // Test with an "unbounded" buffer
  UNICODE::as_utf8(str, 19, res, INT_MAX);
  ASSERT_EQ(strlen(res), (size_t) 3 * 19) << "string should end here";

  // Test that we do not overflow the output buffer
  for (int i = 1; i < 5; i ++) {
    stamp(res, sizeof(res));
    UNICODE::as_utf8(str, 19, res, i);
    EXPECT_TRUE(test_stamp(res + i, sizeof(res) - i));
  }

}

TEST_VM(utf8, jbyte_length) {
  char res[60];
  jbyte str[20];

  for (int i = 0; i < 19; i++) {
    str[i] = 0x42;
  }
  str[19] = '\0';

  stamp(res, sizeof(res));
  UNICODE::as_utf8(str, 19, res, 10);
  ASSERT_EQ(strlen(res), (size_t) 9) << "string should be truncated here";
  ASSERT_TRUE(test_stamp(res + 10, sizeof(res) - 10));

  UNICODE::as_utf8(str, 19, res, INT_MAX);
  ASSERT_EQ(strlen(res), (size_t) 19) << "string should end here";

  // Test that we do not overflow the output buffer
  for (int i = 1; i < 5; i ++) {
    stamp(res, sizeof(res));
    UNICODE::as_utf8(str, 19, res, i);
    EXPECT_TRUE(test_stamp(res + i, sizeof(res) - i));
  }
}

TEST_VM(utf8, truncation) {

  // Test that truncation removes partial encodings as expected.

  const char orig_bytes[] = { 'A', 'B', 'C', 'D', 'E', '\0' };
  const int orig_length = sizeof(orig_bytes)/sizeof(char);
  ASSERT_TRUE(UTF8::is_legal_utf8((const unsigned char*)orig_bytes, orig_length - 1, false));
  const char* orig_str = &orig_bytes[0];
  ASSERT_EQ((int)strlen(orig_str), orig_length - 1);

  unsigned char* temp_bytes;
  const char* temp_str;
  char* utf8;
  int n_utf8; // Number of bytes in the encoding

  // Test 1: a valid UTF8 "ascii" ending string should be returned as-is

  temp_bytes = (unsigned char*) os::malloc(sizeof(unsigned char) * orig_length, mtTest);
  strcpy((char*)temp_bytes, orig_str);
  temp_str = (const char*) temp_bytes;
  UTF8::truncate_to_legal_utf8(temp_bytes, orig_length);
  ASSERT_EQ((int)strlen(temp_str), orig_length - 1) << "bytes should be unchanged";
  ASSERT_EQ(strcmp(orig_str, temp_str), 0) << "bytes should be unchanged";
  os::free(temp_bytes);

  // Test 2: a UTF8 sequence that "ends" with a 2-byte encoding
  //         drops the 2-byte encoding

  jchar two_byte_char[] = { 0x00D1 }; // N with tilde
  n_utf8 = 2;
  utf8 = (char*) os::malloc(sizeof(char) * (n_utf8 + 1), mtTest); // plus NUL
  UNICODE::convert_to_utf8(two_byte_char, 1, utf8);
  int utf8_len = (int)strlen(utf8);
  ASSERT_EQ(utf8_len, n_utf8) << "setup error";

  // Now drop zero or one byte from the end and check it truncates as expected
  for (int drop = 0; drop < n_utf8; drop++) {
    int temp_len = orig_length + utf8_len - drop;
    temp_bytes = (unsigned char*) os::malloc(sizeof(unsigned char) * temp_len, mtTest);
    temp_str = (const char*) temp_bytes;
    strcpy((char*)temp_bytes, orig_str);
    strncat((char*)temp_bytes, utf8, utf8_len - drop);
    ASSERT_EQ((int)strlen(temp_str), temp_len - 1) << "setup error";
    UTF8::truncate_to_legal_utf8(temp_bytes, temp_len);
    ASSERT_EQ((int)strlen(temp_str), orig_length - 1) << "bytes should be truncated to original length";
    ASSERT_EQ(strcmp(orig_str, temp_str), 0) << "bytes should be truncated to original";
    os::free(temp_bytes);
  }
  os::free(utf8);

  // Test 3: a UTF8 sequence that "ends" with a 3-byte encoding
  //         drops the 3-byte encoding
  n_utf8 = 3;
  jchar three_byte_char[] = { 0x0800 };
  utf8 = (char*) os::malloc(sizeof(char) * (n_utf8 + 1), mtTest); // plus NUL
  UNICODE::convert_to_utf8(three_byte_char, 1, utf8);
  utf8_len = (int)strlen(utf8);
  ASSERT_EQ(utf8_len, n_utf8) << "setup error";

  // Now drop zero, to two bytes from the end and check it truncates as expected
  for (int drop = 0; drop < n_utf8; drop++) {
    int temp_len = orig_length + utf8_len - drop;
    temp_bytes = (unsigned char*) os::malloc(sizeof(unsigned char) * temp_len, mtTest);
    temp_str = (const char*) temp_bytes;
    strcpy((char*)temp_bytes, orig_str);
    strncat((char*)temp_bytes, utf8, utf8_len - drop);
    ASSERT_EQ((int)strlen(temp_str), temp_len - 1) << "setup error";
    UTF8::truncate_to_legal_utf8(temp_bytes, temp_len);
    ASSERT_EQ((int)strlen(temp_str), orig_length - 1) << "bytes should be truncated to original length";
    ASSERT_EQ(strcmp(orig_str, temp_str), 0) << "bytes should be truncated to original";
    os::free(temp_bytes);
  }
  os::free(utf8);

  // Test 4: a UTF8 sequence that "ends" with a 6-byte encoding
  //         drops the 6-byte encoding
  n_utf8 = 6;
  jchar six_byte_char[] = { 0xD801, 0xDC37 }; // U+10437 as its UTF-16 surrogate pairs
  utf8 = (char*) os::malloc(sizeof(char) * (n_utf8 + 1), mtTest); // plus NUL
  UNICODE::convert_to_utf8(six_byte_char, 2, utf8);
  utf8_len = (int)strlen(utf8);
  ASSERT_EQ(utf8_len, n_utf8) << "setup error";

  // Now drop zero to five bytes from the end and check it truncates as expected
  for (int drop = 0; drop < n_utf8; drop++) {
    int temp_len = orig_length + utf8_len - drop;
    temp_bytes = (unsigned char*) os::malloc(sizeof(unsigned char) * temp_len, mtTest);
    temp_str = (const char*) temp_bytes;
    strcpy((char*)temp_bytes, orig_str);
    strncat((char*)temp_bytes, utf8, utf8_len - drop);
    ASSERT_EQ((int)strlen(temp_str), temp_len - 1) << "setup error";
    UTF8::truncate_to_legal_utf8(temp_bytes, temp_len);
    ASSERT_EQ((int)strlen(temp_str), orig_length - 1) << "bytes should be truncated to original length";
    ASSERT_EQ(strcmp(orig_str, temp_str), 0) << "bytes should be truncated to original";
    os::free(temp_bytes);
  }
  os::free(utf8);


}

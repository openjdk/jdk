/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "include/jvm_io.h"
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "utilities/lineReader.hpp"
#include "unittest.hpp"

// Write num_lines into filename.
// The i-th line has (first_len + step_size * i) chars, plus \n
static FILE* get_input(const char* filename, int num_lines, int step_size, int first_len) {
  ResourceMark rm;

  const char* tmp_dir = os::get_temp_directory();
  const char* file_sep = os::file_separator();
  size_t temp_file_len = strlen(tmp_dir) + strlen(file_sep) + strlen(filename) + 1;
  char* temp_file = NEW_RESOURCE_ARRAY(char, temp_file_len);
  jio_snprintf(temp_file, temp_file_len, "%s%s%s",
               tmp_dir, file_sep, filename);

  FILE* fp = os::fopen(filename, "w+");
  for (int i = 0; i < num_lines; i++) {
    int len = first_len + i * step_size;
    for (; len > 0; len --) {
      fputc('x', fp);
    }
    fputc('\n', fp);
  }
  fclose(fp);

  fp = os::fopen(filename, "r");
  return fp;
}

// Test the expansion of LineReader::_buffer
TEST_VM(LineReader, increasingly_longer_lines) {
  const int num_lines = 161;
  const int step_size = 100; // The last line will be 16000 chars + \n
  const int first_len = 0;
  FILE* fp = get_input("input", num_lines, step_size, first_len);
  LineReader lr(fp);

  for (int i = 0; i < num_lines; i++) {
    char* line = lr.read_line();
    ASSERT_NE(nullptr, line);

    int line_len = (int)strlen(line);
    EXPECT_TRUE(line_len == i * step_size + 1)
      << "line[" << i << "] should have " << i * step_size + 1
      << " chars but has " << line_len << " chars instead";

    for (int n = 0; n < line_len-1; n++) {
      EXPECT_TRUE(line[n] == 'x') << " unexpected character " << line[n];
    }

    // Each line should have a trailing \n
    char last_char = line[line_len - 1];
    EXPECT_TRUE(last_char == '\n') << " unexpected character " << last_char;

    // line_num() should be numbered from 1
    EXPECT_TRUE(lr.line_num() == i + 1)
      << " line_num() should be " << (i + 1)
      << " but is " << lr.line_num();
  }
  fclose(fp);
}

// If line is too long, break it up into multiple chunks (just as fgets() would)
TEST_VM(LineReader, longer_than_MAX_LEN) {
  const int MAX_LEN = LineReader::MAX_LEN;
  FILE* fp = get_input("verylong", 1, 0, MAX_LEN);
  LineReader lr(fp);

  // If the input has MAX_LEN chars, LineReader should split it into two parts
  // [1] MAX_LEN-1 chars, plus \0
  // [2] 1 char, plus \n, plus \0

  char* line1 = lr.read_line();
  int line1_len = (int)strlen(line1);
  EXPECT_TRUE(line1_len == MAX_LEN - 1)
    << "the first line returned by LineReader should have " << (MAX_LEN - 1)
    << " chars but has " << line1_len << " chars instead";

  char* line2 = lr.read_line();
  int line2_len = (int)strlen(line2);
  EXPECT_TRUE(line2_len == 2)
    << "the second line returned by LineReader should have " << 2
    << " chars but has " << line2_len << " chars instead";

  EXPECT_TRUE(line2[0] == 'x' && line2[1] == '\n')
    << "the second line returned by LineReader should be \"x\\n";

  fclose(fp);
}


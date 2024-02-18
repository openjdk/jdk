/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifdef LINUX

#include "runtime/os.hpp"
#include "cgroupSubsystem_linux.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

#include <stdio.h>


// Utilities
static bool file_exists(const char* filename) {
  struct stat st;
  return os::stat(filename, &st) == 0;
}

static char* temp_file(const char* prefix) {
  const testing::TestInfo* test_info = ::testing::UnitTest::GetInstance()->current_test_info();
  stringStream path;
  path.print_raw(os::get_temp_directory());
  path.print_raw(os::file_separator());
  path.print("%s-test-jdk.pid%d.%s.%s", prefix, os::current_process_id(),
             test_info->test_case_name(), test_info->name());
  return path.as_string(true);
}

static void delete_file(const char* filename) {
  if (!file_exists(filename)) {
    return;
  }
  int ret = remove(filename);
  EXPECT_TRUE(ret == 0 || errno == ENOENT) << "failed to remove file '" << filename << "': "
      << os::strerror(errno) << " (" << errno << ")";
}

class TestController : public CgroupController {
public:
  char* subsystem_path() override {
    // The real subsystem is in /tmp/, generaed by temp_file()
    return (char*)"/";
  };
};

static void fill_file(const char* path, const char* content) {
  delete_file(path);
  FILE* fp = os::fopen(path, "w");
  if (fp == nullptr) {
    return;
  }
  if (content != nullptr) {
    fprintf(fp, "%s", content);
  }
  fclose(fp);
}

TEST(cgroupTest, SubSystemFileLineContentsMultipleLinesErrorCases) {
  TestController my_controller{};
  const char* test_file = temp_file("cgroups");
  int x = 0;
  char s[1024];
  int err = 0;

  s[0] = '\0';
  fill_file(test_file, "foo ");
  err = subsystem_file_line_contents(&my_controller, test_file, "foo", "%s", &s);
  EXPECT_NE(err, 0) << "Value must not be missing in key/value case";

  s[0] = '\0';
  fill_file(test_file, "faulty_start foo bar");
  err = subsystem_file_line_contents(&my_controller, test_file, "foo", "%s", &s);
  EXPECT_NE(err, 0) << "Key must be at start";

  s[0] = '\0';
  fill_file(test_file, "foof bar");
  err = subsystem_file_line_contents(&my_controller, test_file, "foo", "%s", &s);
  EXPECT_NE(err, 0) << "Key must be exact match";
}

TEST(cgroupTest, SubSystemFileLineContentsMultipleLinesSuccessCases) {
  TestController my_controller{};
  const char* test_file = temp_file("cgroups");
  int x = 0;
  char s[1024];
  int err = 0;

  s[0] = '\0';
  fill_file(test_file, "foo bar");
  err = subsystem_file_line_contents(&my_controller, test_file, "foo", "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "bar") << "Incorrect!";

  s[0] = '\0';
  fill_file(test_file, "foo\tbar");
  err = subsystem_file_line_contents(&my_controller, test_file, "foo", "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "bar") << "Incorrect!";

  s[0] = '\0';
  fill_file(test_file, "foof bar\nfoo car");
  err = subsystem_file_line_contents(&my_controller, test_file, "foo", "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "car");

  s[0] = '\0';
  fill_file(test_file, "foo\ttest\nfoot car");
  err = subsystem_file_line_contents(&my_controller, test_file, "foo", "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "test");

  s[0] = '\0';
  fill_file(test_file, "foo 1\nfoo car");
  err = subsystem_file_line_contents(&my_controller, test_file, "foo", "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "1");

  s[0] = '\0';
  fill_file(test_file, "max 10000");
  err = subsystem_file_line_contents(&my_controller, test_file, nullptr, "%s %*d", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "max");

  x = -3;
  fill_file(test_file, "max 10001");
  err = subsystem_file_line_contents(&my_controller, test_file, nullptr, "%*s %d", &x);
  EXPECT_EQ(err, 0);
  EXPECT_EQ(x, 10001);
}

TEST(cgroupTest, SubSystemFileLineContentsSingleLine) {
  TestController my_controller{};
  const char* test_file = temp_file("cgroups");
  int x = 0;
  char s[1024];
  int err = 0;

  fill_file(test_file, "foo");
  err = subsystem_file_line_contents(&my_controller, test_file, nullptr, "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "foo");

  fill_file(test_file, "1337");
  err = subsystem_file_line_contents(&my_controller, test_file, nullptr, "%d", &x);
  EXPECT_EQ(err, 0);
  EXPECT_EQ(x, 1337) << "Wrong value for x";

  s[0] = '\0';
  fill_file(test_file, "1337");
  err = subsystem_file_line_contents(&my_controller, test_file, nullptr, "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "1337");

  x = -1;
  fill_file(test_file, nullptr);
  err = subsystem_file_line_contents(&my_controller, test_file, nullptr, "%d", &x);
  EXPECT_NE(err, 0) << "Empty file should've failed";
  EXPECT_EQ(x, -1) << "x was altered";

  jlong y;
  fill_file(test_file, "1337");
  err = subsystem_file_line_contents(&my_controller, test_file, nullptr, JLONG_FORMAT, &y);
  EXPECT_EQ(err, 0);
  EXPECT_EQ(y, 1337) << "Wrong value for y";
  julong z;
  fill_file(test_file, "1337");
  err = subsystem_file_line_contents(&my_controller, test_file, nullptr, JULONG_FORMAT, &z);
  EXPECT_EQ(err, 0);
  EXPECT_EQ(z, (julong)1337) << "Wrong value for z";
}

#endif // LINUX

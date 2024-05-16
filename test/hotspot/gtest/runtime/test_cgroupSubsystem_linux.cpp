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
#include "cgroupV1Subsystem_linux.hpp"
#include "cgroupV2Subsystem_linux.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

#include <stdio.h>

typedef struct {
  const char* mount_path;
  const char* root_path;
  const char* cgroup_path;
  const char* expected_path;
} TestCase;

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
private:
  char* _path;
public:
  TestController(char *p) {
    _path = p;
  }
  char* subsystem_path() override {
    return _path;
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

TEST(cgroupTest, cg_file_multi_line_impl_failure_cases) {
  const char* test_file = temp_file("cgroups");
  int x = 0;
  char s[1024];
  int err = 0;

  s[0] = '\0';
  fill_file(test_file, "foo ");
  err = __cg_file_multi_line_impl(test_file, "foo", "%s", &s);
  EXPECT_NE(err, 0) << "Value must not be missing in key/value case";

  s[0] = '\0';
  fill_file(test_file, "faulty_start foo bar");
  err = __cg_file_multi_line_impl(test_file, "foo", "%s", &s);
  EXPECT_NE(err, 0) << "Key must be at start";

  s[0] = '\0';
  fill_file(test_file, "foof bar");
  err = __cg_file_multi_line_impl(test_file, "foo", "%s", &s);
  EXPECT_NE(err, 0) << "Key must be exact match";

  // Cleanup
  delete_file(test_file);
}

TEST(cgroupTest, cg_file_multi_line_impl_success_cases) {
  const char* test_file = temp_file("cgroups");
  int x = 0;
  char s[1024];
  int err = 0;

  s[0] = '\0';
  fill_file(test_file, "foo bar");
  err = __cg_file_multi_line_impl(test_file, "foo", "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "bar") << "Incorrect!";

  s[0] = '\0';
  fill_file(test_file, "foo\tbar");
  err = __cg_file_multi_line_impl(test_file, "foo", "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "bar") << "Incorrect!";

  s[0] = '\0';
  fill_file(test_file, "foof bar\nfoo car");
  err = __cg_file_multi_line_impl(test_file, "foo", "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "car");

  s[0] = '\0';
  fill_file(test_file, "foo\ttest\nfoot car");
  err = __cg_file_multi_line_impl(test_file, "foo", "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "test");

  s[0] = '\0';
  fill_file(test_file, "foo 1\nfoo car");
  err = __cg_file_multi_line_impl(test_file, "foo", "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "1");

  // Cleanup
  delete_file(test_file);
}

TEST(cgroupTest, cg_file_contents_impl) {
  const char* test_file = temp_file("cgroups");
  int x = 0;
  char s[1024];
  int err = 0;

  fill_file(test_file, "foo");
  err = __cg_file_contents_impl(test_file, "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "foo");

  err = __cg_file_contents_impl(test_file, "%d", &x);
  EXPECT_NE(err, 0) << "'foo' cannot be read as int";
  EXPECT_EQ(x, 0);

  fill_file(test_file, "1337");
  err = __cg_file_contents_impl(test_file, "%d", &x);
  EXPECT_EQ(err, 0);
  EXPECT_EQ(x, 1337) << "Wrong value for x";

  s[0] = '\0';
  fill_file(test_file, "1337");
  err = __cg_file_contents_impl(test_file, "%s", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "1337");

  x = -1;
  fill_file(test_file, nullptr);
  err = __cg_file_contents_impl(test_file, "%d", &x);
  EXPECT_NE(err, 0) << "Empty file should've failed";
  EXPECT_EQ(x, -1) << "x was altered";

  jlong y;
  fill_file(test_file, "1337");
  err = __cg_file_contents_impl(test_file, JLONG_FORMAT, &y);
  EXPECT_EQ(err, 0);
  EXPECT_EQ(y, 1337) << "Wrong value for y";
  julong z;
  fill_file(test_file, "1337");
  err = __cg_file_contents_impl(test_file, JULONG_FORMAT, &z);
  EXPECT_EQ(err, 0);
  EXPECT_EQ(z, (julong)1337) << "Wrong value for z";

  s[0] = '\0';
  fill_file(test_file, "max 10000");
  err = __cg_file_contents_impl(test_file, "%s %*d", &s);
  EXPECT_EQ(err, 0);
  EXPECT_STREQ(s, "max");

  x = -3;
  fill_file(test_file, "max 10001");
  err = __cg_file_contents_impl(test_file, "%*s %d", &x);
  EXPECT_EQ(err, 0);
  EXPECT_EQ(x, 10001);

  // Cleanup
  delete_file(test_file);
}

TEST(cgroupTest, cg_file_contents_ctrl_null) {
  TestController* null_path_controller = new TestController((char*)nullptr);
  const char* test_file_path = "/not-used";
  const char* scan_fmt = "%d";
  int a = -1;
  // null subsystem_path() case
  int err = cg_file_contents_ctrl<int*>(null_path_controller, test_file_path, scan_fmt, &a);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Null subsystem path should be an error";
  EXPECT_EQ(-1, a) << "Expected untouched scan value";
  // null controller
  err = cg_file_contents_ctrl<int*>(nullptr, test_file_path, scan_fmt, &a);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Null subsystem path should be an error";
  EXPECT_EQ(-1, a) << "Expected untouched scan value";
  // null scan_fmt, null return pointer
  TestController* test_controller = new TestController((char*)"/something");
  err = cg_file_contents_ctrl<int*>(test_controller, test_file_path, nullptr, &a);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Null scan format should be an error";
  err = cg_file_contents_ctrl<int*>(test_controller, test_file_path, scan_fmt, nullptr);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Null return pointer should be an error";
}

TEST(cgroupTest, cg_file_contents_ctrl_beyond_max_path) {
  char larger_than_max[MAXPATHLEN + 1];
  for (int i = 0; i < (MAXPATHLEN); i++) {
    larger_than_max[i] = 'A' + (i % 26);
  }
  larger_than_max[MAXPATHLEN] = '\0';
  TestController* too_large_path_controller = new TestController(larger_than_max);
  const char* test_file_path = "/file-not-found";
  const char* scan_fmt = "%d";
  int foo = -1;
  int err = cg_file_contents_ctrl<int*>(too_large_path_controller, test_file_path, scan_fmt, &foo);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Too long path should be an error";
  EXPECT_EQ(-1, foo) << "Expected untouched scan value";
}

TEST(cgroupTest, cg_file_contents_ctrl_file_not_exist) {
  TestController* unknown_path_ctrl = new TestController((char*)"/do/not/exist");
  const char* test_file_path = "/file-not-found";
  const char* scan_fmt = "/not-used";
  const char* ret_val[2] = { "/one", "/two" };
  int err = cg_file_contents_ctrl<const char*>(unknown_path_ctrl, test_file_path, scan_fmt, ret_val[0]);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "File not found should be an error";
  EXPECT_EQ("/one", ret_val[0]) << "Expected untouched scan value";
}

TEST(cgroupTest, cg_file_multi_line_ctrl_null) {
  TestController* null_path_controller = new TestController((char*)nullptr);
  const char* test_file_path = "/not-used";
  const char* scan_fmt = "%d";
  const char* key = "something";
  int a = -1;
  // null subsystem_path() case
  int err = cg_file_multi_line_ctrl<int*>(null_path_controller, test_file_path, key, scan_fmt, &a);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Null subsystem path should be an error";
  EXPECT_EQ(-1, a) << "Expected untouched scan value";
  // null controller
  err = cg_file_multi_line_ctrl<int*>(nullptr, test_file_path, key, scan_fmt, &a);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Null subsystem path should be an error";
  EXPECT_EQ(-1, a) << "Expected untouched scan value";
  // null key, null scan_fmt, null return pointer
  TestController* test_controller = new TestController((char*)"/something");
  err = cg_file_multi_line_ctrl<int*>(test_controller, test_file_path, nullptr, scan_fmt, &a);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Null key should be an error";
  err = cg_file_multi_line_ctrl<int*>(test_controller, test_file_path, key, nullptr, &a);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Null scan format should be an error";
  err = cg_file_multi_line_ctrl<int*>(test_controller, test_file_path, key, scan_fmt, nullptr);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Null return pointer should be an error";
}

TEST(cgroupTest, cg_read_number_tests) {
  const char* test_file = temp_file("cgroups");
  const char* b = basename(test_file);
  EXPECT_TRUE(b != nullptr) << "basename was null";
  stringStream path;
  path.print_raw(os::file_separator());
  path.print_raw(b);
  const char* base_with_slash = path.as_string(true);
  fill_file(test_file, "8888");

  TestController* controller = new TestController((char*)os::get_temp_directory());
  julong foo = 0xBAD;
  bool ok = controller->read_number_from_file(base_with_slash, &foo);
  EXPECT_TRUE(ok) << "Number parsing should have been successful";
  EXPECT_EQ((julong)8888, foo) << "Expected julongs to be equal (and not: " << 0xBAD << " == 0xBAD)";
  delete_file(test_file);
}

TEST(cgroupTest, cg_read_string_tests) {
  const char* test_file = temp_file("cgroups");
  const char* b = basename(test_file);
  EXPECT_TRUE(b != nullptr) << "basename was null";
  stringStream path;
  path.print_raw(os::file_separator());
  path.print_raw(b);
  const char* base_with_slash = path.as_string(true);
  fill_file(test_file, "foo-bar");

  TestController* controller = new TestController((char*)os::get_temp_directory());
  char* result = nullptr;
  bool ok = controller->read_string_from_file(base_with_slash, &result);
  EXPECT_TRUE(ok) << "String parsing should have been successful";
  EXPECT_TRUE(result != nullptr) << "Expected non-null result";
  EXPECT_STREQ("foo-bar", result) << "Expected strings to be equal";
  delete_file(test_file);
}

TEST(cgroupTest, cg_file_multi_line_ctrl_beyond_max_path) {
  char larger_than_max[MAXPATHLEN + 1];
  for (int i = 0; i < (MAXPATHLEN); i++) {
    larger_than_max[i] = 'A' + (i % 26);
  }
  larger_than_max[MAXPATHLEN] = '\0';
  TestController* too_large_path_controller = new TestController(larger_than_max);
  const char* test_file_path = "/file-not-found";
  const char* scan_fmt = "%d";
  const char* key = "something";
  int foo = -1;
  int err = cg_file_multi_line_ctrl<int*>(too_large_path_controller, test_file_path, key, scan_fmt, &foo);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "Too long path should be an error";
  EXPECT_EQ(-1, foo) << "Expected untouched scan value";
}

TEST(cgroupTest, cg_file_multi_line_ctrl_file_not_exist) {
  TestController* unknown_path_ctrl = new TestController((char*)"/do/not/exist");
  const char* test_file_path = "/file-not-found";
  const char* scan_fmt = "/not-used";
  const char* key = "something";
  const char* ret_val[2] = { "/one", "/two" };
  int err = cg_file_multi_line_ctrl<const char*>(unknown_path_ctrl, test_file_path, key, scan_fmt, ret_val[0]);
  EXPECT_EQ(err, OSCONTAINER_ERROR) << "File not found should be an error";
  EXPECT_EQ("/one", ret_val[0]) << "Expected untouched scan value";
}

TEST(cgroupTest, set_cgroupv1_subsystem_path) {
  TestCase host = {
    "/sys/fs/cgroup/memory",                                             // mount_path
    "/",                                                                 // root_path
    "/user.slice/user-1000.slice/user@1000.service",                     // cgroup_path
    "/sys/fs/cgroup/memory/user.slice/user-1000.slice/user@1000.service" // expected_path
  };
  TestCase container_engine = {
    "/sys/fs/cgroup/mem",                            // mount_path
    "/user.slice/user-1000.slice/user@1000.service", // root_path
    "/user.slice/user-1000.slice/user@1000.service", // cgroup_path
    "/sys/fs/cgroup/mem"                             // expected_path
  };
  int length = 2;
  TestCase* testCases[] = { &host,
                            &container_engine };
  for (int i = 0; i < length; i++) {
    CgroupV1Controller* ctrl = new CgroupV1Controller( (char*)testCases[i]->root_path,
                                                       (char*)testCases[i]->mount_path);
    ctrl->set_subsystem_path((char*)testCases[i]->cgroup_path);
    ASSERT_STREQ(testCases[i]->expected_path, ctrl->subsystem_path());
  }
}

TEST(cgroupTest, set_cgroupv2_subsystem_path) {
  TestCase at_mount_root = {
    "/sys/fs/cgroup",       // mount_path
    nullptr,                // root_path, ignored
    "/",                    // cgroup_path
    "/sys/fs/cgroup"        // expected_path
  };
  TestCase sub_path = {
    "/sys/fs/cgroup",       // mount_path
    nullptr,                // root_path, ignored
    "/foobar",              // cgroup_path
    "/sys/fs/cgroup/foobar" // expected_path
  };
  int length = 2;
  TestCase* testCases[] = { &at_mount_root,
                            &sub_path };
  for (int i = 0; i < length; i++) {
    CgroupV2Controller* ctrl = new CgroupV2Controller( (char*)testCases[i]->mount_path,
                                                       (char*)testCases[i]->cgroup_path);
    ASSERT_STREQ(testCases[i]->expected_path, ctrl->subsystem_path());
  }
}

#endif // LINUX

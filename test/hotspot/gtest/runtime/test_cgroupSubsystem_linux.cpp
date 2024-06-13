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

// for basename
#include <libgen.h>

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

// we rely on temp_file returning modifiable memory in resource area.
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
  TestController(char* p): _path(p) {}
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

TEST(cgroupTest, read_numerical_key_value_failure_cases) {
  char* test_file = temp_file("cgroups");
  const char* b = basename(test_file);
  EXPECT_TRUE(b != nullptr) << "basename was null";
  stringStream path;
  path.print_raw(os::file_separator());
  path.print_raw(b);
  const char* base_with_slash = path.as_string(true);

  TestController* controller = new TestController((char*)os::get_temp_directory());
  constexpr julong bad = 0xBAD;
  julong x = bad;

  fill_file(test_file, "foo ");
  bool is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_FALSE(is_ok) << "Value is missing in key/value case, expecting false";
  EXPECT_EQ(bad, x) << "x must be unchanged";

  x = bad;
  fill_file(test_file, "faulty_start foo 101");
  is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_FALSE(is_ok) << "key must be at the start";
  EXPECT_EQ(bad, x) << "x must be unchanged";

  x = bad;
  fill_file(test_file, nullptr);
  is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_FALSE(is_ok) << "key not in empty file";
  EXPECT_EQ(bad, x) << "x must be unchanged";

  x = bad;
  fill_file(test_file, "foo\n");
  is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_FALSE(is_ok) << "key must have a value";
  EXPECT_EQ(bad, x) << "x must be unchanged";

  x = bad;
  fill_file(test_file, "foof 1002");
  is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_FALSE(is_ok) << "key must be exact match";
  EXPECT_EQ(bad, x) << "x must be unchanged";

  // Cleanup
  delete_file(test_file);
}

TEST(cgroupTest, read_numerical_key_value_success_cases) {
  char* test_file = temp_file("cgroups");
  const char* b = basename(test_file);
  EXPECT_TRUE(b != nullptr) << "basename was null";
  stringStream path;
  path.print_raw(os::file_separator());
  path.print_raw(b);
  const char* base_with_slash = path.as_string(true);

  TestController* controller = new TestController((char*)os::get_temp_directory());
  constexpr julong bad = 0xBAD;
  julong x = bad;

  fill_file(test_file, "foo 100");
  bool is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_TRUE(is_ok);
  EXPECT_EQ((julong)100, x);

  x = bad;
  fill_file(test_file, "foo\t111");
  is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_TRUE(is_ok);
  EXPECT_EQ((julong)111, x);

  x = bad;
  fill_file(test_file, "foo\nbar 333\nfoo\t111");
  is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_TRUE(is_ok);
  EXPECT_EQ((julong)111, x);

  x = bad;
  fill_file(test_file, "foof 100\nfoo 133");
  is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_TRUE(is_ok);
  EXPECT_EQ((julong)133, x);

  x = bad;
  fill_file(test_file, "foo\t333\nfoot 999");
  is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_TRUE(is_ok);
  EXPECT_EQ((julong)333, x);

  x = bad;
  fill_file(test_file, "foo 1\nfoo car");
  is_ok = controller->read_numerical_key_value(base_with_slash, "foo", &x);
  EXPECT_TRUE(is_ok);
  EXPECT_EQ((julong)1, x);

  // Cleanup
  delete_file(test_file);
}

TEST(cgroupTest, read_number_null) {
  TestController* null_path_controller = new TestController((char*)nullptr);
  const char* test_file_path = "/not-used";
  constexpr julong bad = 0xBAD;
  julong a = bad;
  // null subsystem_path() case
  bool is_ok = null_path_controller->read_number(test_file_path, &a);
  EXPECT_FALSE(is_ok) << "Null subsystem path should be an error";
  EXPECT_EQ(bad, a) << "Expected untouched scan value";
}

TEST(cgroupTest, read_string_beyond_max_path) {
  char larger_than_max[MAXPATHLEN + 1];
  for (int i = 0; i < (MAXPATHLEN); i++) {
    larger_than_max[i] = 'A' + (i % 26);
  }
  larger_than_max[MAXPATHLEN] = '\0';
  TestController* too_large_path_controller = new TestController(larger_than_max);
  const char* test_file_path = "/file-not-found";
  char foo[1024];
  foo[0] = '\0';
  bool is_ok = too_large_path_controller->read_string(test_file_path, foo, 1024);
  EXPECT_FALSE(is_ok) << "Too long path should be an error";
  EXPECT_STREQ("", foo) << "Expected untouched scan value";
}

TEST(cgroupTest, read_number_file_not_exist) {
  TestController* unknown_path_ctrl = new TestController((char*)"/do/not/exist");
  const char* test_file_path = "/file-not-found";
  constexpr julong bad = 0xBAD;
  julong result = bad;
  bool is_ok = unknown_path_ctrl->read_number(test_file_path, &result);
  EXPECT_FALSE(is_ok) << "File not found should be an error";
  EXPECT_EQ(bad, result) << "Expected untouched scan value";
}

TEST(cgroupTest, read_numerical_key_value_null) {
  TestController* null_path_controller = new TestController((char*)nullptr);
  const char* test_file_path = "/not-used";
  const char* key = "something";
  constexpr julong bad = 0xBAD;
  julong a = bad;
  // null subsystem_path() case
  bool is_ok = null_path_controller->read_numerical_key_value(test_file_path, key, &a);
  EXPECT_FALSE(is_ok) << "Null subsystem path should be an error";
  EXPECT_EQ(bad, a) << "Expected untouched scan value";
}

TEST(cgroupTest, read_number_tests) {
  char* test_file = temp_file("cgroups");
  const char* b = basename(test_file);
  constexpr julong bad = 0xBAD;
  EXPECT_TRUE(b != nullptr) << "basename was null";
  stringStream path;
  path.print_raw(os::file_separator());
  path.print_raw(b);
  const char* base_with_slash = path.as_string(true);
  fill_file(test_file, "8888");

  TestController* controller = new TestController((char*)os::get_temp_directory());
  julong foo = bad;
  bool ok = controller->read_number(base_with_slash, &foo);
  EXPECT_TRUE(ok) << "Number parsing should have been successful";
  EXPECT_EQ((julong)8888, foo) << "Wrong value for 'foo' (NOTE: 0xBAD == " << 0xBAD << ")";

  // Some interface files might have negative values, ensure we can read
  // them and manually cast them as needed.
  fill_file(test_file, "-1");
  foo = bad;
  ok = controller->read_number(base_with_slash, &foo);
  EXPECT_TRUE(ok) << "Number parsing should have been successful";
  EXPECT_EQ((jlong)-1, (jlong)foo) << "Wrong value for 'foo' (NOTE: 0xBAD == " << 0xBAD << ")";

  foo = bad;
  fill_file(test_file, nullptr);
  ok = controller->read_number(base_with_slash, &foo);
  EXPECT_FALSE(ok) << "Empty file should have failed";
  EXPECT_EQ(bad, foo) << "foo was altered";

  // Some interface files have numbers as well as the string
  // 'max', which means unlimited.
  jlong result = -10;
  fill_file(test_file, "max\n");
  ok = controller->read_number_handle_max(base_with_slash, &result);
  EXPECT_TRUE(ok) << "Number parsing for 'max' string should have been successful";
  EXPECT_EQ((jlong)-1, result) << "'max' means unlimited (-1)";

  result = -10;
  fill_file(test_file, "11114\n");
  ok = controller->read_number_handle_max(base_with_slash, &result);
  EXPECT_TRUE(ok) << "Number parsing for should have been successful";
  EXPECT_EQ((jlong)11114, result) << "Incorrect result";

  result = -10;
  fill_file(test_file, "-51114\n");
  ok = controller->read_number_handle_max(base_with_slash, &result);
  EXPECT_TRUE(ok) << "Number parsing for should have been successful";
  EXPECT_EQ((jlong)-51114, result) << "Incorrect result";

  delete_file(test_file);
}

TEST(cgroupTest, read_string_tests) {
  char* test_file = temp_file("cgroups");
  const char* b = basename(test_file);
  EXPECT_TRUE(b != nullptr) << "basename was null";
  stringStream path;
  path.print_raw(os::file_separator());
  path.print_raw(b);
  const char* base_with_slash = path.as_string(true);
  fill_file(test_file, "foo-bar");

  TestController* controller = new TestController((char*)os::get_temp_directory());
  char result[1024];
  bool ok = controller->read_string(base_with_slash, result, 1024);
  EXPECT_TRUE(ok) << "String parsing should have been successful";
  EXPECT_STREQ("foo-bar", result);

  result[0] = '\0';
  fill_file(test_file, "1234");
  ok = controller->read_string(base_with_slash, result, 1024);
  EXPECT_TRUE(ok) << "String parsing should have been successful";
  EXPECT_STREQ("1234", result);

  // values with a space
  result[0] = '\0';
  fill_file(test_file, "abc def");
  ok = controller->read_string(base_with_slash, result, 1024);
  EXPECT_TRUE(ok) << "String parsing should have been successful";
  EXPECT_STREQ("abc def", result);

  result[0] = '\0';
  fill_file(test_file, "  \na");
  ok = controller->read_string(base_with_slash, result, 1024);
  EXPECT_TRUE(ok) << "String parsing should have been successful";
  EXPECT_STREQ("  ", result);

  // only the first line are being returned
  result[0] = '\0';
  fill_file(test_file, "test\nabc");
  ok = controller->read_string(base_with_slash, result, 1024);
  EXPECT_TRUE(ok) << "String parsing should have been successful";
  EXPECT_STREQ("test", result);

  result[0] = '\0';
  fill_file(test_file, nullptr);
  ok = controller->read_string(base_with_slash, result, 1024);
  EXPECT_FALSE(ok) << "Empty file should have failed";
  EXPECT_STREQ("", result) << "Expected untouched result";
  delete_file(test_file);

  // File contents larger than 1K
  // We only read in the first 1K - 1 bytes
  const size_t large_len = 2 * 1024;
  char too_large[large_len];
  for (size_t i = 0; i < large_len; i++) {
    too_large[i] = 'A' + (i % 26);
  }
  too_large[large_len - 1] = '\0';
  result[0] = '\0';
  fill_file(test_file, too_large);
  ok = controller->read_string(base_with_slash, result, 1024);
  EXPECT_TRUE(ok) << "String parsing should have been successful";
  EXPECT_TRUE(1023 == strlen(result)) << "Expected only the first 1023 chars to be read in";
  EXPECT_EQ(0, strncmp(too_large, result, 1023));
  EXPECT_EQ(result[1023], '\0') << "The last character must be the null character";
}

TEST(cgroupTest, read_number_tuple_test) {
  char* test_file = temp_file("cgroups");
  const char* b = basename(test_file);
  EXPECT_TRUE(b != nullptr) << "basename was null";
  stringStream path;
  path.print_raw(os::file_separator());
  path.print_raw(b);
  const char* base_with_slash = path.as_string(true);
  fill_file(test_file, "max 10000");

  TestController* controller = new TestController((char*)os::get_temp_directory());
  jlong result = -10;
  bool ok = controller->read_numerical_tuple_value(base_with_slash, true /* use_first */, &result);
  EXPECT_TRUE(ok) << "Should be OK to read value";
  EXPECT_EQ((jlong)-1, result) << "max should be unlimited (-1)";

  result = -10;
  ok = controller->read_numerical_tuple_value(base_with_slash, false /* use_first */, &result);
  EXPECT_TRUE(ok) << "Should be OK to read the value";
  EXPECT_EQ((jlong)10000, result);

  // non-max strings
  fill_file(test_file, "abc 10000");
  result = -10;
  ok = controller->read_numerical_tuple_value(base_with_slash, true /* use_first */, &result);
  EXPECT_FALSE(ok) << "abc should not be parsable";
  EXPECT_EQ((jlong)-10, result) << "result value should be unchanged";

  fill_file(test_file, nullptr);
  result = -10;
  ok = controller->read_numerical_tuple_value(base_with_slash, true /* use_first */, &result);
  EXPECT_FALSE(ok) << "Empty file should be an error";
  EXPECT_EQ((jlong)-10, result) << "result value should be unchanged";
}

TEST(cgroupTest, read_numerical_key_beyond_max_path) {
  char larger_than_max[MAXPATHLEN + 1];
  for (int i = 0; i < (MAXPATHLEN); i++) {
    larger_than_max[i] = 'A' + (i % 26);
  }
  larger_than_max[MAXPATHLEN] = '\0';
  TestController* too_large_path_controller = new TestController(larger_than_max);
  const char* test_file_path = "/file-not-found";
  const char* key = "something";
  julong a = 0xBAD;
  bool is_ok = too_large_path_controller->read_numerical_key_value(test_file_path, key, &a);
  EXPECT_FALSE(is_ok) << "Too long path should be an error";
  EXPECT_EQ((julong)0xBAD, a) << "Expected untouched scan value";
}

TEST(cgroupTest, read_numerical_key_file_not_exist) {
  TestController* unknown_path_ctrl = new TestController((char*)"/do/not/exist");
  const char* test_file_path = "/file-not-found";
  const char* key = "something";
  julong a = 0xBAD;
  bool is_ok = unknown_path_ctrl->read_numerical_key_value(test_file_path, key, &a);
  EXPECT_FALSE(is_ok) << "File not found should be an error";
  EXPECT_EQ((julong)0xBAD, a) << "Expected untouched scan value";
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

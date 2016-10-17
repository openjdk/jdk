/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "logTestFixture.hpp"
#include "logTestUtils.inline.hpp"
#include "logging/logConfiguration.hpp"
#include "memory/resourceArea.hpp"
#include "unittest.hpp"
#include "utilities/ostream.hpp"

LogTestFixture::LogTestFixture() {
  // Set up TestLogFileName to include PID, testcase name and test name
  int ret = jio_snprintf(_filename, sizeof(_filename), "testlog.pid%d.%s.%s.log",
                         os::current_process_id(),
                         ::testing::UnitTest::GetInstance()->current_test_info()->test_case_name(),
                         ::testing::UnitTest::GetInstance()->current_test_info()->name());
  EXPECT_GT(ret, 0) << "_filename buffer issue";
  TestLogFileName = _filename;
}

LogTestFixture::~LogTestFixture() {
  restore_default_log_config();
  delete_file(TestLogFileName);
}

bool LogTestFixture::set_log_config(const char* output,
                                    const char* what,
                                    const char* decorators,
                                    const char* options,
                                    bool allow_failure) {
  ResourceMark rm;
  stringStream stream;
  bool success = LogConfiguration::parse_log_arguments(output, what, decorators, options, &stream);
  if (!allow_failure) {
    const char* errmsg = stream.as_string();
    EXPECT_STREQ("", errmsg) << "Unexpected error reported";
    EXPECT_TRUE(success) << "Shouldn't cause errors";
  }
  return success;
}

void LogTestFixture::restore_default_log_config() {
  LogConfiguration::disable_logging();
  set_log_config("stdout", "all=warning");
}

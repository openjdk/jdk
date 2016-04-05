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
 *
 */
#include "precompiled.hpp"
#include "logTestFixture.hpp"
#include "logTestUtils.inline.hpp"
#include "logging/logConfiguration.hpp"
#include "logging/logLevel.hpp"
#include "logging/logOutput.hpp"
#include "logging/logTag.hpp"
#include "logging/logTagSet.hpp"
#include "memory/resourceArea.hpp"
#include "unittest.hpp"
#include "utilities/ostream.hpp"

class LogConfigurationTest : public LogTestFixture {
 protected:
  static char _all_decorators[256];

 public:
  static void SetUpTestCase();
};

char LogConfigurationTest::_all_decorators[256];

// Prepare _all_decorators to contain the full list of decorators (comma separated)
void LogConfigurationTest::SetUpTestCase() {
  char *pos = _all_decorators;
  for (size_t i = 0; i < LogDecorators::Count; i++) {
    pos += jio_snprintf(pos, sizeof(_all_decorators) - (pos - _all_decorators), "%s%s",
                        (i == 0 ? "" : ","),
                        LogDecorators::name(static_cast<LogDecorators::Decorator>(i)));
  }
}

// Check if the given text is included by LogConfiguration::describe()
static bool is_described(const char* text) {
  ResourceMark rm;
  stringStream ss;
  LogConfiguration::describe(&ss);
  return string_contains_substring(ss.as_string(), text);
}

TEST_F(LogConfigurationTest, describe) {
  ResourceMark rm;
  stringStream ss;
  LogConfiguration::describe(&ss);
  const char* description = ss.as_string();

  // Verify that stdout and stderr are listed by default
  EXPECT_PRED2(string_contains_substring, description, LogOutput::Stdout->name());
  EXPECT_PRED2(string_contains_substring, description, LogOutput::Stderr->name());

  // Verify that each tag, level and decorator is listed
  for (size_t i = 0; i < LogTag::Count; i++) {
    EXPECT_PRED2(string_contains_substring, description, LogTag::name(static_cast<LogTagType>(i)));
  }
  for (size_t i = 0; i < LogLevel::Count; i++) {
    EXPECT_PRED2(string_contains_substring, description, LogLevel::name(static_cast<LogLevelType>(i)));
  }
  for (size_t i = 0; i < LogDecorators::Count; i++) {
    EXPECT_PRED2(string_contains_substring, description, LogDecorators::name(static_cast<LogDecorators::Decorator>(i)));
  }

  // Verify that the default configuration is printed
  char expected_buf[256];
  int ret = jio_snprintf(expected_buf, sizeof(expected_buf), "=%s", LogLevel::name(LogLevel::Default));
  ASSERT_NE(-1, ret);
  EXPECT_PRED2(string_contains_substring, description, expected_buf);
  EXPECT_PRED2(string_contains_substring, description, "#1: stderr all=off");

  // Verify default decorators are listed
  LogDecorators default_decorators;
  expected_buf[0] = '\0';
  for (size_t i = 0; i < LogDecorators::Count; i++) {
    LogDecorators::Decorator d = static_cast<LogDecorators::Decorator>(i);
    if (default_decorators.is_decorator(d)) {
      ASSERT_LT(strlen(expected_buf), sizeof(expected_buf));
      ret = jio_snprintf(expected_buf + strlen(expected_buf),
                         sizeof(expected_buf) - strlen(expected_buf),
                         "%s%s",
                         strlen(expected_buf) > 0 ? "," : "",
                         LogDecorators::name(d));
      ASSERT_NE(-1, ret);
    }
  }
  EXPECT_PRED2(string_contains_substring, description, expected_buf);

  // Add a new output and verify that it gets described after it has been added
  const char* what = "all=trace";
  EXPECT_FALSE(is_described(TestLogFileName)) << "Test output already exists!";
  set_log_config(TestLogFileName, what);
  EXPECT_TRUE(is_described(TestLogFileName));
  EXPECT_TRUE(is_described("logging=trace"));
}

// Test updating an existing log output
TEST_F(LogConfigurationTest, update_output) {
  // Update stdout twice, first using it's name, and the second time its index #
  const char* test_outputs[] = { "stdout", "#0" };
  for (size_t i = 0; i < ARRAY_SIZE(test_outputs); i++) {
    set_log_config(test_outputs[i], "all=info");

    // Verify configuration using LogConfiguration::describe
    EXPECT_TRUE(is_described("#0: stdout"));
    EXPECT_TRUE(is_described("logging=info"));

    // Verify by iterating over tagsets
    LogOutput* o = LogOutput::Stdout;
    for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
      EXPECT_TRUE(ts->has_output(o));
      EXPECT_TRUE(ts->is_level(LogLevel::Info));
      EXPECT_FALSE(ts->is_level(LogLevel::Debug));
    }

    // Now change the level and verify the change propagated
    set_log_config(test_outputs[i], "all=debug");
    for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
      EXPECT_TRUE(ts->has_output(o));
      EXPECT_TRUE(ts->is_level(LogLevel::Debug));
      EXPECT_FALSE(ts->is_level(LogLevel::Trace));
    }
  }
}

// Test adding a new output to the configuration
TEST_F(LogConfigurationTest, add_new_output) {
  const char* what = "all=trace";

  ASSERT_FALSE(is_described(TestLogFileName));
  set_log_config(TestLogFileName, what);

  // Verify new output using LogConfiguration::describe
  EXPECT_TRUE(is_described(TestLogFileName));
  EXPECT_TRUE(is_described("logging=trace"));

  // Also verify by iterating over tagsets, checking levels on tagsets
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    EXPECT_TRUE(ts->is_level(LogLevel::Trace));
  }
}

TEST_F(LogConfigurationTest, disable_logging) {
  // Add TestLogFileName as an output
  set_log_config(TestLogFileName, "logging=info");

  LogConfiguration::disable_logging();

  // Verify TestLogFileName was disabled
  EXPECT_FALSE(is_described(TestLogFileName));

  // Verify that no tagset has logging enabled
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    EXPECT_FALSE(ts->has_output(LogOutput::Stdout));
    EXPECT_FALSE(ts->has_output(LogOutput::Stderr));
    EXPECT_FALSE(ts->is_level(LogLevel::Error));
  }
}

// Test disabling a particular output
TEST_F(LogConfigurationTest, disable_output) {
  // Disable the default configuration for stdout
  set_log_config("stdout", "all=off");

  // Verify configuration using LogConfiguration::describe
  EXPECT_TRUE(is_described("#0: stdout all=off"));

  // Verify by iterating over tagsets
  LogOutput* o = LogOutput::Stdout;
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    EXPECT_FALSE(ts->has_output(o));
    EXPECT_FALSE(ts->is_level(LogLevel::Error));
  }

  // Add a new file output
  const char* what = "all=debug";
  set_log_config(TestLogFileName, what);
  EXPECT_TRUE(is_described(TestLogFileName));

  // Now disable it, verifying it is removed completely
  set_log_config(TestLogFileName, "all=off");
  EXPECT_FALSE(is_described(TestLogFileName));
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    EXPECT_FALSE(ts->is_level(LogLevel::Error));
  }
}

// Test reconfiguration of the selected decorators for an output
TEST_F(LogConfigurationTest, reconfigure_decorators) {
  // Configure stderr with all decorators
  set_log_config("stderr", "all=off", _all_decorators);
  char buf[256];
  int ret = jio_snprintf(buf, sizeof(buf), "#1: stderr all=off %s", _all_decorators);
  ASSERT_NE(-1, ret);
  EXPECT_TRUE(is_described(buf)) << "'" << buf << "' not described after reconfiguration";

  // Now reconfigure logging on stderr with no decorators
  set_log_config("stderr", "all=off", "none");
  EXPECT_TRUE(is_described("#1: stderr all=off \n")) << "Expecting no decorators";
}

// Test that invalid options cause configuration errors
TEST_F(LogConfigurationTest, invalid_configure_options) {
  LogConfiguration::disable_logging();
  const char* invalid_outputs[] = { "#2", "invalidtype=123", ":invalid/path}to*file?" };
  for (size_t i = 0; i < ARRAY_SIZE(invalid_outputs); i++) {
    EXPECT_FALSE(set_log_config(invalid_outputs[i], "", "", "", true))
      << "Accepted invalid output '" << invalid_outputs[i] << "'";
  }
  EXPECT_FALSE(LogConfiguration::parse_command_line_arguments("all=invalid_level"));
  EXPECT_FALSE(LogConfiguration::parse_command_line_arguments("what=invalid"));
  EXPECT_FALSE(LogConfiguration::parse_command_line_arguments("all::invalid_decorator"));
}

// Test empty configuration options
TEST_F(LogConfigurationTest, parse_empty_command_line_arguments) {
  const char* empty_variations[] = { "", ":", "::", ":::", "::::" };
  for (size_t i = 0; i < ARRAY_SIZE(empty_variations); i++) {
    const char* cmdline = empty_variations[i];
    bool ret = LogConfiguration::parse_command_line_arguments(cmdline);
    EXPECT_TRUE(ret) << "Error parsing command line arguments '" << cmdline << "'";
    for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
      EXPECT_EQ(LogLevel::Unspecified, ts->level_for(LogOutput::Stdout));
    }
  }
}

// Test basic command line parsing & configuration
TEST_F(LogConfigurationTest, parse_command_line_arguments) {
  // Prepare a command line for logging*=debug on stderr with all decorators
  int ret;
  char buf[256];
  ret = jio_snprintf(buf, sizeof(buf), "logging*=debug:stderr:%s", _all_decorators);
  ASSERT_NE(-1, ret);

  bool success = LogConfiguration::parse_command_line_arguments(buf);
  EXPECT_TRUE(success) << "Error parsing valid command line arguments '" << buf << "'";
  // Ensure the new configuration applied
  EXPECT_TRUE(is_described("logging=debug"));
  EXPECT_TRUE(is_described(_all_decorators));

  // Test the configuration of file outputs as well
  ret = jio_snprintf(buf, sizeof(buf), ":%s", TestLogFileName);
  ASSERT_NE(-1, ret);
  EXPECT_TRUE(LogConfiguration::parse_command_line_arguments(buf));
}

// Test split up log configuration arguments
TEST_F(LogConfigurationTest, parse_log_arguments) {
  ResourceMark rm;
  stringStream ss;
  // Verify that it's possible to configure each individual tag
  for (size_t t = 1 /* Skip _NO_TAG */; t < LogTag::Count; t++) {
    const LogTagType tag = static_cast<LogTagType>(t);
    EXPECT_TRUE(LogConfiguration::parse_log_arguments("stdout", LogTag::name(tag), "", "", &ss));
  }
  // Same for each level
  for (size_t l = 0; l < LogLevel::Count; l++) {
    const LogLevelType level = static_cast<LogLevelType>(l);
    char expected_buf[256];
    int ret = jio_snprintf(expected_buf, sizeof(expected_buf), "all=%s", LogLevel::name(level));
    ASSERT_NE(-1, ret);
    EXPECT_TRUE(LogConfiguration::parse_log_arguments("stderr", expected_buf, "", "", &ss));
  }
  // And for each decorator
  for (size_t d = 0; d < LogDecorators::Count; d++) {
    const LogDecorators::Decorator decorator = static_cast<LogDecorators::Decorator>(d);
    EXPECT_TRUE(LogConfiguration::parse_log_arguments("#0", "", LogDecorators::name(decorator), "", &ss));
  }
}

TEST_F(LogConfigurationTest, parse_invalid_tagset) {
  static const char* invalid_tagset = "logging+start+exit+safepoint+gc"; // Must not exist for test to function.

  // Make sure warning is produced if one or more configured tagsets are invalid
  ResourceMark rm;
  stringStream ss;
  bool success = LogConfiguration::parse_log_arguments("stdout", invalid_tagset, NULL, NULL, &ss);
  const char* msg = ss.as_string();
  EXPECT_TRUE(success) << "Should only cause a warning, not an error";
  EXPECT_TRUE(string_contains_substring(msg, "No tag set matches selection(s):"));
  EXPECT_TRUE(string_contains_substring(msg, invalid_tagset));
}

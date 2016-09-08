/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/////////////// Unit tests ///////////////

#ifndef PRODUCT

#include "gc/shared/gcTraceTime.inline.hpp"
#include "logging/log.hpp"
#include "logging/logConfiguration.hpp"
#include "logging/logFileOutput.hpp"
#include "logging/logMessage.hpp"
#include "logging/logMessageBuffer.hpp"
#include "logging/logOutput.hpp"
#include "logging/logTagLevelExpression.hpp"
#include "logging/logTagSet.hpp"
#include "logging/logTagSetDescriptions.hpp"
#include "logging/logStream.inline.hpp"
#include "memory/resourceArea.hpp"

#define assert_str_eq(s1, s2) \
  assert(strcmp(s1, s2) == 0, "Expected '%s' to equal '%s'", s1, s2)

#define assert_char_in(c, s) \
  assert(strchr(s, c) != NULL, "Expected '%s' to contain character '%c'", s, c)

#define assert_char_not_in(c, s) \
  assert(strchr(s, c) == NULL, "Expected '%s' to *not* contain character '%c'", s, c)

// Read a complete line from fp and return it as a resource allocated string.
// Returns NULL on EOF.
static char* read_line(FILE* fp) {
  assert(fp != NULL, "bad fp");
  int buflen = 512;
  char* buf = NEW_RESOURCE_ARRAY(char, buflen);
  long pos = ftell(fp);

  char* ret = fgets(buf, buflen, fp);
  while (ret != NULL && buf[strlen(buf) - 1] != '\n' && !feof(fp)) {
    // retry with a larger buffer
    buf = REALLOC_RESOURCE_ARRAY(char, buf, buflen, buflen * 2);
    buflen *= 2;
    // rewind to beginning of line
    fseek(fp, pos, SEEK_SET);
    // retry read with new buffer
    ret = fgets(buf, buflen, fp);
  }
  return ret;
}

static bool file_contains_substrings_in_order(const char* filename, const char* substrs[]) {
  FILE* fp = fopen(filename, "r");
  assert(fp != NULL, "error opening file %s: %s", filename, strerror(errno));

  size_t idx = 0;
  while (substrs[idx] != NULL) {
    ResourceMark rm;
    char* line = read_line(fp);
    if (line == NULL) {
      break;
    }
    for (char* match = strstr(line, substrs[idx]); match != NULL;) {
      size_t match_len = strlen(substrs[idx]);
      idx++;
      if (substrs[idx] == NULL) {
        break;
      }
      match = strstr(match + match_len, substrs[idx]);
    }
  }

  fclose(fp);
  return substrs[idx] == NULL;
}

static bool file_contains_substring(const char* filename, const char* substr) {
  const char* strs[] = {substr, NULL};
  return file_contains_substrings_in_order(filename, strs);
}

static size_t number_of_lines_with_substring_in_file(const char* filename,
                                                     const char* substr) {
  FILE* fp = fopen(filename, "r");
  assert(fp != NULL, "error opening file %s: %s", filename, strerror(errno));

  size_t ret = 0;
  for (;;) {
    ResourceMark rm;
    char* line = read_line(fp);
    if (line == NULL) {
      break;
    }
    if (strstr(line, substr) != NULL) {
      ret++;
    }
  }

  fclose(fp);
  return ret;
}

static bool file_exists(const char* filename) {
  struct stat st;
  return os::stat(filename, &st) == 0;
}

static void delete_file(const char* filename) {
  if (!file_exists(filename)) {
    return;
  }
  int ret = remove(filename);
  assert(ret == 0, "failed to remove file '%s': %s", filename, strerror(errno));
}

static void create_directory(const char* name) {
  assert(!file_exists(name), "can't create directory: %s already exists", name);
  bool failed;
#ifdef _WINDOWS
  failed = !CreateDirectory(name, NULL);
#else
  failed = mkdir(name, 0777);
#endif
  assert(!failed, "failed to create directory %s", name);
}

class TestLogFile {
 private:
  char file_name[256];

  void set_name(const char* test_name) {
    const char* tmpdir = os::get_temp_directory();
    int pos = jio_snprintf(file_name, sizeof(file_name), "%s%svmtest.%s.%d.log", tmpdir, os::file_separator(), test_name, os::current_process_id());
    assert(pos > 0, "too small log file name buffer");
    assert((size_t)pos < sizeof(file_name), "too small log file name buffer");
  }

 public:
  TestLogFile(const char* test_name) {
    set_name(test_name);
    remove(name());
  }

  ~TestLogFile() {
    remove(name());
  }

  const char* name() {
    return file_name;
  }
};

class TestLogSavedConfig {
 private:
  char* _saved_config;
  char* _new_output;
  Log(logging) _log;
 public:
  TestLogSavedConfig(const char* apply_output = NULL, const char* apply_setting = NULL) : _new_output(0) {
    ResourceMark rm;
    _saved_config = os::strdup_check_oom(LogOutput::Stdout->config_string());
    bool success = LogConfiguration::parse_log_arguments("stdout", "all=off", NULL, NULL, _log.error_stream());
    assert(success, "test unable to turn all off");

    if (apply_output) {
      _new_output = os::strdup_check_oom(apply_output);
      bool success = LogConfiguration::parse_log_arguments(_new_output, apply_setting,  NULL, NULL, _log.error_stream());
      assert(success, "test unable to apply test log configuration");
    }
  }

  ~TestLogSavedConfig() {
    ResourceMark rm;
    if (_new_output) {
      bool success = LogConfiguration::parse_log_arguments(_new_output, "all=off", NULL, NULL, _log.error_stream());
      assert(success, "test unable to turn all off");
      os::free(_new_output);
    }

    bool success = LogConfiguration::parse_log_arguments("stdout", _saved_config, NULL, NULL, _log.error_stream());
    assert(success, "test unable to restore log configuration");
    os::free(_saved_config);
  }
};

void Test_configure_stdout() {
  LogOutput* stdoutput = LogOutput::Stdout;
  TestLogSavedConfig tlsc;

  // Enable 'logging=info', verifying it has been set
  LogConfiguration::configure_stdout(LogLevel::Info, true, LOG_TAGS(logging));
  assert_str_eq("logging=info", stdoutput->config_string());
  assert(log_is_enabled(Info, logging), "logging was not properly enabled");

  // Enable 'gc=debug' (no wildcard), verifying no other tags are enabled
  LogConfiguration::configure_stdout(LogLevel::Debug, true, LOG_TAGS(gc));
  // No '+' character means only single tags are enabled, and no combinations
  assert_char_not_in('+', stdoutput->config_string());
  assert(log_is_enabled(Debug, gc), "logging was not properly enabled");

  // Enable 'gc*=trace' (with wildcard), verifying at least one tag combination is enabled (gc+...)
  LogConfiguration::configure_stdout(LogLevel::Trace, false, LOG_TAGS(gc));
  assert_char_in('+', stdoutput->config_string());
  assert(log_is_enabled(Trace, gc), "logging was not properly enabled");

  // Disable 'gc*' and 'logging', verifying all logging is properly disabled
  LogConfiguration::configure_stdout(LogLevel::Off, false, LOG_TAGS(gc));
  LogConfiguration::configure_stdout(LogLevel::Off, true, LOG_TAGS(logging));
  assert_str_eq("all=off", stdoutput->config_string());
}

static const char* ExpectedLine = "a (hopefully) unique log line for testing";

static void init_file(const char* filename, const char* options = "") {
  LogConfiguration::parse_log_arguments(filename, "logging=trace", "", options,
                                        Log(logging)::error_stream());
  log_debug(logging)("%s", ExpectedLine);
  LogConfiguration::parse_log_arguments(filename, "all=off", "", "",
                                        Log(logging)::error_stream());
}

void Test_log_file_startup_rotation() {
  ResourceMark rm;
  const size_t rotations = 5;
  const char* filename = "start-rotate-test";
  char* rotated_file[rotations];
  for (size_t i = 0; i < rotations; i++) {
    size_t len = strlen(filename) + 3;
    rotated_file[i] = NEW_RESOURCE_ARRAY(char, len);
    jio_snprintf(rotated_file[i], len, "%s." SIZE_FORMAT, filename, i);
    delete_file(rotated_file[i]);
  };

  delete_file(filename);
  init_file(filename);
  assert(file_exists(filename),
         "configured logging to file '%s' but file was not found", filename);

  // Initialize the same file a bunch more times to trigger rotations
  for (size_t i = 0; i < rotations; i++) {
    init_file(filename);
    assert(file_exists(rotated_file[i]), "existing file was not rotated");
  }

  // Remove a file and expect its slot to be re-used
  delete_file(rotated_file[1]);
  init_file(filename);
  assert(file_exists(rotated_file[1]), "log file not properly rotated");

  // Clean up after test
  delete_file(filename);
  for (size_t i = 0; i < rotations; i++) {
    delete_file(rotated_file[i]);
  }
}

void Test_log_file_startup_truncation() {
  ResourceMark rm;
  const char* filename = "start-truncate-test";
  const char* archived_filename = "start-truncate-test.0";

  delete_file(filename);
  delete_file(archived_filename);

  // Use the same log file twice and expect it to be overwritten/truncated
  init_file(filename, "filecount=0");
  assert(file_exists(filename), "couldn't find log file: %s", filename);

  init_file(filename, "filecount=0");
  assert(file_exists(filename), "couldn't find log file: %s", filename);
  assert(!file_exists(archived_filename),
         "existing log file %s was not properly truncated when filecount was 0",
         filename);

  // Verify that the file was really truncated and not just appended
  assert(number_of_lines_with_substring_in_file(filename, ExpectedLine) == 1,
         "log file %s appended rather than truncated", filename);

  delete_file(filename);
  delete_file(archived_filename);
}

class LogMessageTest {
 private:
  static Log(logging) _log;
  static const char* _level_filename[];

  static void test_level_inclusion();
  static void test_long_message();
  static void test_message_with_many_lines();
  static void test_line_order();
  static void test_prefixing();
  static void test_scoped_messages();
  static void test_scoped_flushing();
  static void test_scoped_reset();

 public:
  static void test();
};

const char* LogMessageTest::_level_filename[] = {
  NULL, // LogLevel::Off
#define LOG_LEVEL(name, printname) "multiline-" #printname ".log",
  LOG_LEVEL_LIST
#undef LOG_LEVEL
};

void Test_multiline_logging() {
  LogMessageTest::test();
}

void LogMessageTest::test() {
  ResourceMark rm;

  for (int i = 0; i < LogLevel::Count; i++) {
    char buf[32];
    // Attempt to remove possibly pre-existing log files
    remove(_level_filename[i]);

    jio_snprintf(buf, sizeof(buf), "logging=%s", LogLevel::name(static_cast<LogLevelType>(i)));
    bool success = LogConfiguration::parse_log_arguments(_level_filename[i], buf,
                                                         NULL, NULL, _log.error_stream());
    assert(success, "unable to configure logging to file '%s'", _level_filename[i]);
  }

  test_level_inclusion();
  test_line_order();
  test_long_message();
  test_message_with_many_lines();
  test_prefixing();
  test_scoped_messages();
  test_scoped_flushing();
  test_scoped_reset();

  // Stop logging to the files and remove them.
  for (int i = 0; i < LogLevel::Count; i++) {
    LogConfiguration::parse_log_arguments(_level_filename[i], "all=off", NULL, NULL, _log.error_stream());
    remove(_level_filename[i]);
  }
}

// Verify that messages with multiple levels are written
// to outputs configured for all the corresponding levels
void LogMessageTest::test_level_inclusion() {
  const size_t message_count = 10;
  LogMessageBuffer msg[message_count];

  struct {
    int message_number;
    LogLevelType level;
  } lines[] = {
    { 0, LogLevel::Error },
    { 1, LogLevel::Info },
    { 2, LogLevel::Info }, { 2, LogLevel::Debug },
    { 3, LogLevel::Info }, { 3, LogLevel::Warning },
    { 4, LogLevel::Debug }, { 4, LogLevel::Warning },
    { 5, LogLevel::Trace }, { 5, LogLevel::Debug },
    { 6, LogLevel::Warning }, { 6, LogLevel::Error },
    { 7, LogLevel::Trace }, { 7, LogLevel::Info }, { 7, LogLevel::Debug },
    { 8, LogLevel::Trace }, { 8, LogLevel::Debug }, { 8, LogLevel::Info },
    { 8, LogLevel::Warning }, { 8, LogLevel::Error},
    { 9, LogLevel::Trace }
  };

  // Fill in messages with the above lines
  for (size_t i = 0; i < ARRAY_SIZE(lines); i++) {
    switch (lines[i].level) {
#define LOG_LEVEL(name, printname) \
    case LogLevel::name: \
      msg[lines[i].message_number].printname("msg[%d]: " #printname, lines[i].message_number); \
      break;
LOG_LEVEL_LIST
#undef LOG_LEVEL
    }
  }

  for (size_t i = 0; i < message_count; i++) {
    _log.write(msg[i]);
  }

  // Verify that lines are written to the expected log files
  for (size_t i = 0; i < ARRAY_SIZE(lines); i++) {
    char expected[256];
    jio_snprintf(expected, sizeof(expected), "msg[%d]: %s",
                 lines[i].message_number, LogLevel::name(lines[i].level));
    for (int level = lines[i].level; level > 0; level--) {
      assert(file_contains_substring(_level_filename[level], expected),
      "line #" SIZE_FORMAT " missing from log file '%s'", i, _level_filename[level]);
    }
    for (int level = lines[i].level + 1; level < LogLevel::Count; level++) {
      assert(!file_contains_substring(_level_filename[level], expected),
      "line #" SIZE_FORMAT " erroneously included in log file '%s'", i, _level_filename[level]);
    }
  }
}

// Verify that messages are logged in the order they are added to the log message
void LogMessageTest::test_line_order() {
  LogMessageBuffer msg;
  msg.info("info line").error("error line").trace("trace line")
      .error("another error").warning("warning line").debug("debug line");
  _log.write(msg);

  const char* expected[] = { "info line", "error line", "trace line",
                             "another error", "warning line", "debug line", NULL };
  assert(file_contains_substrings_in_order(_level_filename[LogLevel::Trace], expected),
         "output missing or in incorrect order");
}

void LogMessageTest::test_long_message() {
  // Write 10K bytes worth of log data
  LogMessageBuffer msg;
  const size_t size = 10 * K;
  const char* start_marker = "#start#";
  const char* end_marker = "#the end#";
  char* data = NEW_C_HEAP_ARRAY(char, size, mtLogging);

  // fill buffer with start_marker...some data...end_marker
  sprintf(data, "%s", start_marker);
  for (size_t i = strlen(start_marker); i < size; i++) {
    data[i] = '0' + (i % 10);
  }
  sprintf(data + size - strlen(end_marker) - 1, "%s", end_marker);

  msg.trace("%s", data); // Adds a newline, making the message exactly 10K in length.
  _log.write(msg);

  const char* expected[] = { start_marker, "0123456789", end_marker, NULL };
  assert(file_contains_substrings_in_order(_level_filename[LogLevel::Trace], expected),
         "unable to print long line");
  FREE_C_HEAP_ARRAY(char, data);
}

void LogMessageTest::test_message_with_many_lines() {
  const size_t lines = 100;
  const size_t line_length = 16;

  LogMessageBuffer msg;
  for (size_t i = 0; i < lines; i++) {
    msg.info("Line #" SIZE_FORMAT, i);
  }
  _log.write(msg);

  char expected_lines_data[lines][line_length];
  const char* expected_lines[lines + 1];
  for (size_t i = 0; i < lines; i++) {
    jio_snprintf(&expected_lines_data[i][0], line_length, "Line #" SIZE_FORMAT, i);
    expected_lines[i] = expected_lines_data[i];
  }
  expected_lines[lines] = NULL;

  assert(file_contains_substrings_in_order(_level_filename[LogLevel::Trace], expected_lines),
         "couldn't find all lines in multiline message");
}

static size_t dummy_prefixer(char* buf, size_t len) {
  static int i = 0;
  const char* prefix = "some prefix: ";
  const size_t prefix_len = strlen(prefix);
  if (len < prefix_len) {
    return prefix_len;
  }
  jio_snprintf(buf, len, "%s", prefix);
  return prefix_len;
}

void LogMessageTest::test_prefixing() {
  LogMessageBuffer msg;
  msg.set_prefix(dummy_prefixer);
  for (int i = 0; i < 3; i++) {
    msg.info("test %d", i);
  }
  msg.set_prefix(NULL);
  msg.info("test 3");
  _log.write(msg);

  const char* expected[] = {
    "] some prefix: test 0",
    "] some prefix: test 1",
    "] some prefix: test 2",
    "] test 3",
    NULL
  };
  assert(file_contains_substrings_in_order(_level_filename[LogLevel::Trace], expected), "error in prefixed output");
}

void LogMessageTest::test_scoped_messages() {
  {
    LogMessage(logging) msg;
    msg.info("scoped info");
    msg.warning("scoped warn");
    assert(!file_contains_substring(_level_filename[LogLevel::Info], "scoped info"),
           "scoped log message written prematurely");
  }
  assert(file_contains_substring(_level_filename[LogLevel::Info], "scoped info"),
         "missing output from scoped log message");
  assert(file_contains_substring(_level_filename[LogLevel::Warning], "scoped warn"),
         "missing output from scoped log message");
}

void LogMessageTest::test_scoped_flushing() {
  {
    LogMessage(logging) msg;
    msg.info("manual flush info");
    msg.flush();
    assert(file_contains_substring(_level_filename[LogLevel::Info], "manual flush info"),
           "missing output from manually flushed scoped log message");
  }
  const char* tmp[] = {"manual flush info", "manual flush info", NULL};
  assert(!file_contains_substrings_in_order(_level_filename[LogLevel::Info], tmp),
         "log file contains duplicate lines from single scoped log message");
}

void LogMessageTest::test_scoped_reset() {
  {
    LogMessage(logging) msg, partial;
    msg.info("%s", "info reset msg");
    msg.reset();
    partial.info("%s", "info reset msg");
    partial.reset();
    partial.trace("%s", "trace reset msg");
  }
  assert(!file_contains_substring(_level_filename[LogLevel::Info], "info reset msg"),
         "reset message written anyway");
  assert(file_contains_substring(_level_filename[LogLevel::Trace], "trace reset msg"),
         "missing message from partially reset scoped log message");
}


static int Test_logconfiguration_subscribe_triggered = 0;

static void Test_logconfiguration_subscribe_helper() {
  Test_logconfiguration_subscribe_triggered++;
}

void Test_logconfiguration_subscribe() {
  ResourceMark rm;
  Log(logging) log;

  TestLogSavedConfig log_cfg("stdout", "logging*=trace");

  LogConfiguration::register_update_listener(&Test_logconfiguration_subscribe_helper);

  LogConfiguration::parse_log_arguments("stdout", "logging=trace", NULL, NULL, log.error_stream());
  assert(Test_logconfiguration_subscribe_triggered == 1, "subscription not triggered (1)");

  LogConfiguration::configure_stdout(LogLevel::Debug, true, LOG_TAGS(gc));
  assert(Test_logconfiguration_subscribe_triggered == 2, "subscription not triggered (2)");

  LogConfiguration::disable_logging();
  assert(Test_logconfiguration_subscribe_triggered == 3, "subscription not triggered (3)");
}

#define LOG_PREFIX_STR "THE_PREFIX "
#define LOG_LINE_STR "a log line"

size_t Test_log_prefix_prefixer(char* buf, size_t len) {
  int ret = jio_snprintf(buf, len, LOG_PREFIX_STR);
  assert(ret > 0, "Failed to print prefix. Log buffer too small?");
  return (size_t) ret;
}

void Test_log_prefix() {
  TestLogFile log_file("log_prefix");
  TestLogSavedConfig log_cfg(log_file.name(), "logging+test=trace");

  log_trace(logging, test)(LOG_LINE_STR);

  FILE* fp = fopen(log_file.name(), "r");
  assert(fp, "File read error");
  char output[1024];
  if (fgets(output, 1024, fp) != NULL) {
    assert(strstr(output, LOG_PREFIX_STR LOG_LINE_STR), "logging prefix error");
  }
  fclose(fp);
}

void Test_log_big() {
  char big_msg[4096] = {0};
  char Xchar = '~';

  TestLogFile log_file("log_big");
  TestLogSavedConfig log_cfg(log_file.name(), "logging+test=trace");

  memset(big_msg, Xchar, sizeof(big_msg) - 1);

  log_trace(logging, test)("%s", big_msg);

  FILE* fp = fopen(log_file.name(), "r");
  assert(fp, "File read error");
  char output[sizeof(big_msg)+128 /*decorators*/ ];
  if (fgets(output, sizeof(output), fp) != NULL) {
    assert(strstr(output, LOG_PREFIX_STR), "logging prefix error");
    size_t count = 0;
    for (size_t ps = 0 ; output[ps + count] != '\0'; output[ps + count] == Xchar ? count++ : ps++);
    assert(count == (sizeof(big_msg) - 1) , "logging msg error");
  }
  fclose(fp);
}

#define Test_logtarget_string_literal "First line"


static void Test_logtarget_on() {
  TestLogFile log_file("log_target");
  TestLogSavedConfig tlsc(log_file.name(), "gc=debug");

  LogTarget(Debug, gc) log;

  assert(log.is_enabled(), "assert");

  // Log the line and expect it to be available in the output file.
  log.print(Test_logtarget_string_literal);

  FILE* fp = fopen(log_file.name(), "r");
  assert(fp != NULL, "File read error");

  char output[256 /* Large enough buffer */];
  char* res = fgets(output, sizeof(output), fp);
  assert(res != NULL, "assert");

  assert(strstr(output, Test_logtarget_string_literal) != NULL, "log line missing");

  fclose(fp);
}

static void Test_logtarget_off() {
  TestLogFile log_file("log_target");
  TestLogSavedConfig tlsc(log_file.name(), "gc=info");

  LogTarget(Debug, gc) log;

  if (log.is_enabled()) {
    // The log config could have been redirected gc=debug to a file. If gc=debug
    // is enabled, we can only test that the LogTarget returns the same value
    // as the log_is_enabled function. The rest of the test will be ignored.
    assert(log.is_enabled() == log_is_enabled(Debug, gc), "assert");
    log_warning(logging)("This test doesn't support runs with -Xlog");
    return;
  }

  // Try to log, but expect this to be filtered out.
  log.print(Test_logtarget_string_literal);

  // Log a dummy line so that fgets doesn't return NULL because the file is empty.
  log_info(gc)("Dummy line");

  FILE* fp = fopen(log_file.name(), "r");
  assert(fp != NULL, "File read error");

  char output[256 /* Large enough buffer */];
  char* res = fgets(output, sizeof(output), fp);
  assert(res != NULL, "assert");

  assert(strstr(output, Test_logtarget_string_literal) == NULL, "log line not missing");

  fclose(fp);
}

void Test_logtarget() {
  Test_logtarget_on();
  Test_logtarget_off();
}


static void Test_logstream_helper(outputStream* stream) {
  TestLogFile log_file("log_stream");
  TestLogSavedConfig tlsc(log_file.name(), "gc=debug");

  // Try to log, but expect this to be filtered out.
  stream->print("%d ", 3); stream->print("workers"); stream->cr();

  FILE* fp = fopen(log_file.name(), "r");
  assert(fp != NULL, "File read error");

  char output[256 /* Large enough buffer */];
  char* res = fgets(output, sizeof(output), fp);
  assert(res != NULL, "assert");

  assert(strstr(output, "3 workers") != NULL, "log line missing");

  fclose(fp);
}

static void Test_logstream_log() {
  Log(gc) log;
  LogStream stream(log.debug());

  Test_logstream_helper(&stream);
}

static void Test_logstream_logtarget() {
  LogTarget(Debug, gc) log;
  LogStream stream(log);

  Test_logstream_helper(&stream);
}

static void Test_logstream_logstreamhandle() {
  LogStreamHandle(Debug, gc) stream;

  Test_logstream_helper(&stream);
}

static void Test_logstream_no_rm() {
  ResourceMark rm;
  outputStream* stream = LogTarget(Debug, gc)::stream();

  Test_logstream_helper(stream);
}

static void Test_logstreamcheap_log() {
  Log(gc) log;
  LogStreamCHeap stream(log.debug());

  Test_logstream_helper(&stream);
}

static void Test_logstreamcheap_logtarget() {
  LogTarget(Debug, gc) log;
  LogStreamCHeap stream(log);

  Test_logstream_helper(&stream);
}

void Test_logstream() {
  // Test LogStreams with embedded ResourceMark.
  Test_logstream_log();
  Test_logstream_logtarget();
  Test_logstream_logstreamhandle();

  // Test LogStreams without embedded ResourceMark.
  Test_logstream_no_rm();

  // Test LogStreams backed by CHeap memory.
  Test_logstreamcheap_log();
  Test_logstreamcheap_logtarget();
}

void Test_loghandle_on() {
  TestLogFile log_file("log_handle");
  TestLogSavedConfig tlsc(log_file.name(), "gc=debug");

  Log(gc) log;
  LogHandle log_handle(log);

  assert(log_handle.is_debug(), "assert");

  // Try to log through a LogHandle.
  log_handle.debug("%d workers", 3);

  FILE* fp = fopen(log_file.name(), "r");
  assert(fp, "File read error");

  char output[256 /* Large enough buffer */];
  char* res = fgets(output, sizeof(output), fp);
  assert(res != NULL, "assert");

  assert(strstr(output, "3 workers") != NULL, "log line missing");

  fclose(fp);
}

void Test_loghandle_off() {
  TestLogFile log_file("log_handle");
  TestLogSavedConfig tlsc(log_file.name(), "gc=info");

  Log(gc) log;
  LogHandle log_handle(log);

  if (log_handle.is_debug()) {
    // The log config could have been redirected gc=debug to a file. If gc=debug
    // is enabled, we can only test that the LogTarget returns the same value
    // as the log_is_enabled function. The rest of the test will be ignored.
    assert(log_handle.is_debug() == log_is_enabled(Debug, gc), "assert");
    log_warning(logging)("This test doesn't support runs with -Xlog");
    return;
  }

  // Try to log through a LogHandle. Should fail, since only info is turned on.
  log_handle.debug("%d workers", 3);

  // Log a dummy line so that fgets doesn't return NULL because the file is empty.
  log_info(gc)("Dummy line");

  FILE* fp = fopen(log_file.name(), "r");
  assert(fp, "File read error");

  char output[256 /* Large enough buffer */];
  char* res = fgets(output, sizeof(output), fp);
  assert(res != NULL, "assert");

  assert(strstr(output, "3 workers") == NULL, "log line missing");

  fclose(fp);
}

void Test_loghandle() {
  Test_loghandle_on();
  Test_loghandle_off();
}

static void Test_logtargethandle_on() {
  TestLogFile log_file("log_handle");
  TestLogSavedConfig tlsc(log_file.name(), "gc=debug");

  LogTarget(Debug, gc) log;
  LogTargetHandle log_handle(log);

  assert(log_handle.is_enabled(), "assert");

  // Try to log through a LogHandle.
  log_handle.print("%d workers", 3);

  FILE* fp = fopen(log_file.name(), "r");
  assert(fp, "File read error");

  char output[256 /* Large enough buffer */];
  char* res = fgets(output, sizeof(output), fp);
  assert(res != NULL, "assert");

  assert(strstr(output, "3 workers") != NULL, "log line missing");

  fclose(fp);
}

static void Test_logtargethandle_off() {
  TestLogFile log_file("log_handle");
  TestLogSavedConfig tlsc(log_file.name(), "gc=info");

  LogTarget(Debug, gc) log;
  LogTargetHandle log_handle(log);

  if (log_handle.is_enabled()) {
    // The log config could have been redirected gc=debug to a file. If gc=debug
    // is enabled, we can only test that the LogTarget returns the same value
    // as the log_is_enabled function. The rest of the test will be ignored.
    assert(log_handle.is_enabled() == log_is_enabled(Debug, gc), "assert");
    log_warning(logging)("This test doesn't support runs with -Xlog");
    return;
  }

  // Try to log through a LogHandle. Should fail, since only info is turned on.
  log_handle.print("%d workers", 3);

  // Log a dummy line so that fgets doesn't return NULL because the file is empty.
  log_info(gc)("Dummy line");

  FILE* fp = fopen(log_file.name(), "r");
  assert(fp, "File read error");

  char output[256 /* Large enough buffer */];
  char* res = fgets(output, sizeof(output), fp);
  assert(res != NULL, "assert");

  assert(strstr(output, "3 workers") == NULL, "log line missing");

  fclose(fp);
}

void Test_logtargethandle() {
  Test_logtargethandle_on();
  Test_logtargethandle_off();
}

void Test_invalid_log_file() {
  ResourceMark rm;
  stringStream ss;
  const char* target_name = "tmplogdir";

  // Attempt to log to a directory (existing log not a regular file)
  create_directory(target_name);
  LogFileOutput bad_file("file=tmplogdir");
  assert(bad_file.initialize("", &ss) == false, "file was initialized "
         "when there was an existing directory with the same name");
  assert(strstr(ss.as_string(), "tmplogdir is not a regular file") != NULL,
         "missing expected error message, received msg: %s", ss.as_string());
  ss.reset();
  remove(target_name);
}

#endif // PRODUCT

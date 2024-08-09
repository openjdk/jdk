/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */
#include "precompiled.hpp"
#include "jvm.h"
#include "logTestFixture.hpp"
#include "logTestUtils.inline.hpp"
#include "logging/log.hpp"
#include "logging/logAsyncWriter.hpp"
#include "logging/logFileOutput.hpp"
#include "logging/logMessage.hpp"
#include "unittest.hpp"

class AsyncLogTest : public LogTestFixture {
public:
  // msg is 128 bytes.
  const char* large_message = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                              "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  AsyncLogTest() {
    if (!LogConfiguration::is_async_mode()) {
      fprintf(stderr, "Warning: asynclog is OFF.\n");
    }
  }
  void test_asynclog_raw() {
    Log(logging) logger;
#define LOG_LEVEL(level, name) logger.name("1" #level);
    LOG_LEVEL_LIST
#undef LOG_LEVEL

    LogTarget(Trace, logging) t;
    LogTarget(Debug, logging) d;
    EXPECT_FALSE(t.is_enabled());
    EXPECT_TRUE(d.is_enabled());

    d.print("AsyncLogTarget.print = %d", 1);
    log_trace(logging)("log_trace-test");
    log_debug(logging)("log_debug-test");
  }

  void test_asynclog_drop_messages() {
    // Write more messages than available in buffer.
    test_asynclog_ls(); // roughly 200 bytes.
    const size_t msg_number = AsyncLogBufferSize / strlen(large_message);
    LogMessage(logging) lm;
    // + 5 to go past the buffer size, forcing it to drop the message.
    for (size_t i = 0; i < (msg_number + 5); i++) {
      lm.debug("%s", large_message);
    }
    lm.flush();
  }

  void test_asynclog_ls() {
    LogStream ls(Log(logging)::info());
    outputStream* os = &ls;
    os->print_cr("LogStreamWithAsyncLogImpl");
    os->print_cr("LogStreamWithAsyncLogImpl secondline");

    //multi-lines
    os->print("logStream msg1-");
    os->print("msg2-");
    os->print("msg3\n");
    os->print_cr("logStream newline");
  }
  // stdout/stderr support
  bool write_to_file(const std::string& output) {
    FILE* f = os::fopen(TestLogFileName, "w");

    if (f != nullptr) {
      size_t sz = output.size();
      size_t written = fwrite(output.c_str(), sizeof(char), output.size(), f);
      return fclose(f) == 0 && sz == written;
    }

    return false;
  }
  template<typename F>
  void test_stdout_or_stderr(const char* mode, F get_captured_string) {

    if (!set_log_config(mode, "logging=debug")) {
      return;
    }

    bool async = AsyncLogWriter::instance() != nullptr;
    if (async) {
      test_asynclog_drop_messages();
      AsyncLogWriter::flush();
    } else {
      test_asynclog_ls();
    }

    fflush(nullptr);
    if (!write_to_file(get_captured_string())) {
      return;
    }

    EXPECT_TRUE(file_contains_substring(TestLogFileName, "LogStreamWithAsyncLogImpl"));
    EXPECT_TRUE(file_contains_substring(TestLogFileName, "logStream msg1-msg2-msg3"));
    EXPECT_TRUE(file_contains_substring(TestLogFileName, "logStream newline"));

    if (async) {
      EXPECT_TRUE(
          file_contains_substring(TestLogFileName, "messages dropped due to async logging"));
    }
  }
  void test_room_for_flush() {
    PlatformMonitor lock; // For statistics
    CircularStringBuffer::StatisticsMap map;
    CircularStringBuffer cb(map, lock, os::vm_page_size());
    const size_t count = (cb.circular_mapping.size / (strlen(large_message)+1 + sizeof(CircularStringBuffer::Message))) - 1;
    stringStream ss;
    ss.print("file=%s", TestLogFileName);
    LogFileOutput out(ss.freeze());
    for (size_t i = 0; i < count; i++) {
      cb.enqueue_locked(large_message, strlen(large_message), &out, CircularStringBuffer::None);
    }
    unsigned int* missing = map.get(&out);
    EXPECT_TRUE(missing == nullptr);
    cb.enqueue_locked(large_message, strlen(large_message), &out, CircularStringBuffer::None);
    cb.enqueue_locked(large_message, strlen(large_message), &out, CircularStringBuffer::None);
    missing = map.get(&out);
    EXPECT_TRUE(missing !=nullptr && *missing > 0);
    size_t old_tail = cb._tail;
    cb.enqueue_locked(nullptr, 0, nullptr, CircularStringBuffer::None);
    EXPECT_TRUE(cb._tail != old_tail);
    unsigned int* new_missing = map.get(&out);
    EXPECT_TRUE(new_missing != nullptr && *missing == *new_missing);
  }
};

TEST_VM_F(AsyncLogTest, logMessage) {
  set_log_config(TestLogFileName, "logging=debug");

  const int MULTI_LINES = 20;
  {

    LogMessage(logging) msg;
    Log(logging) logger;

    for (int i = 0; i < MULTI_LINES; ++i) {
      msg.debug("nonbreakable log message line-%02d", i);

      if (0 == (i % 4)) {
        logger.debug("a noisy message from other logger");
      }
    }
    logger.debug("a noisy message from other logger");
  }
  AsyncLogWriter::flush();

  ResourceMark rm;
  const char* strs[MULTI_LINES + 1];
  strs[MULTI_LINES] = nullptr;
  for (int i = 0; i < MULTI_LINES; ++i) {
    stringStream ss;
    ss.print_cr("nonbreakable log message line-%02d", i);
    strs[i] = ss.as_string();
  }
  // check nonbreakable log messages are consecutive
  EXPECT_TRUE(file_contains_substrings_in_order(TestLogFileName, strs));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "a noisy message from other logger"));
}

TEST_VM_F(AsyncLogTest, asynclog) {
  set_log_config(TestLogFileName, "logging=debug");

  test_asynclog_ls();
  test_asynclog_raw();
  AsyncLogWriter::flush();

  EXPECT_TRUE(file_contains_substring(TestLogFileName, "LogStreamWithAsyncLogImpl"));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "logStream msg1-msg2-msg3"));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "logStream newline"));

  EXPECT_TRUE(file_contains_substring(TestLogFileName, "1Debug"));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "1Info"));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "1Warning"));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "1Error"));
  EXPECT_FALSE(file_contains_substring(TestLogFileName, "1Trace")); // trace message is masked out

  EXPECT_TRUE(file_contains_substring(TestLogFileName, "AsyncLogTarget.print = 1"));
  EXPECT_FALSE(
      file_contains_substring(TestLogFileName, "log_trace-test")); // trace message is masked out
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "log_debug-test"));
}

TEST_VM_F(AsyncLogTest, stdoutOutput) {
  testing::internal::CaptureStdout();
  test_stdout_or_stderr("stdout", testing::internal::GetCapturedStdout);
}

TEST_VM_F(AsyncLogTest, stderrOutput) {
  testing::internal::CaptureStderr();
  test_stdout_or_stderr("stderr", testing::internal::GetCapturedStderr);
}

TEST_VM_F(AsyncLogTest, droppingMessage) {
  if (AsyncLogWriter::instance() == nullptr) {
    return;
  }
  set_log_config(TestLogFileName, "logging=debug");
  test_asynclog_drop_messages();
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "messages dropped due to async logging"));
}

TEST_F(AsyncLogTest, CircularStringBufferAlwaysRoomForFlush) {
  test_room_for_flush();
}

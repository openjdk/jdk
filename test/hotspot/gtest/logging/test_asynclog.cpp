/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "logging/logAsyncWriter.hpp"
#include "logging/logFileOutput.hpp"
#include "logging/logMessage.hpp"
#include "logTestFixture.hpp"
#include "logTestUtils.inline.hpp"
#include "unittest.hpp"

class AsyncLogTest : public LogTestFixture {
 public:
  AsyncLogTest() {
    if(!LogConfiguration::is_async_mode()) {
      fprintf(stderr, "Warning: asynclog is OFF.\n");
    }
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

  // Caveat: BufferUpdater is not MT-safe. We use it only for testing.
  // We would observe missing loglines if we interleaved buffers.
  // Emit all logs between constructor and destructor of BufferUpdater.
  void test_asynclog_drop_messages() {
    const size_t sz = 2000;

    // shrink async buffer.
    AsyncLogWriter::BufferUpdater saver(1024);
    test_asynclog_ls(); // roughly 200 bytes.
    LogMessage(logging) lm;

    // write more messages than its capacity in burst
    for (size_t i = 0; i < sz; ++i) {
      lm.debug("a lot of log...");
    }
    lm.flush();
  }

  // stdout/stderr support
  bool write_to_file(const std::string& output) {
    FILE* f = os::fopen(TestLogFileName, "w");

    if (f != NULL) {
      size_t sz = output.size();
      size_t written = fwrite(output.c_str(), sizeof(char), output.size(), f);
      return fclose(f) == 0 && sz == written;
    }

    return false;
  }
};

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
  EXPECT_FALSE(file_contains_substring(TestLogFileName, "log_trace-test")); // trace message is masked out
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "log_debug-test"));
}

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
  LogMessageBuffer buffer;
  const char* strs[MULTI_LINES + 1];
  strs[MULTI_LINES] = NULL;
  for (int i = 0; i < MULTI_LINES; ++i) {
    stringStream ss;
    ss.print_cr("nonbreakable log message line-%02d", i);
    strs[i] = ss.as_string();
  }
  // check nonbreakable log messages are consecutive
  EXPECT_TRUE(file_contains_substrings_in_order(TestLogFileName, strs));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "a noisy message from other logger"));
}

TEST_VM_F(AsyncLogTest, logBuffer) {
  const auto Default = LogDecorations(LogLevel::Warning, LogTagSetMapping<LogTag::__NO_TAG>::tagset(),
                                      LogDecorators());
  size_t len = strlen(TestLogFileName) + strlen(LogFileOutput::Prefix) + 1;
  char* name = NEW_C_HEAP_ARRAY(char, len, mtLogging);
  snprintf(name, len, "%s%s", LogFileOutput::Prefix, TestLogFileName);

  LogFileStreamOutput* output = new LogFileOutput(name);
  output->initialize(nullptr, nullptr);
  auto buffer = new AsyncLogWriter::Buffer(1024);

  int line = 0;
  int written;
  uintptr_t addr;
  const uintptr_t mask = (uintptr_t)(sizeof(void*) - 1);
  bool res;

  res = buffer->push_back(output, Default, "a log line");
  EXPECT_TRUE(res) << "first message should succeed.";
  line++;
  res = buffer->push_back(output, Default, "yet another");
  EXPECT_TRUE(res) << "second message should succeed.";
  line++;

  auto it = buffer->iterator();
  EXPECT_TRUE(it.hasNext());
  const AsyncLogWriter::Message* e = it.next();
  addr = reinterpret_cast<uintptr_t>(e);
  EXPECT_EQ(0, (int)(addr & (sizeof(void*)-1))); // returned vaue aligns on sizeof(pointer)
  EXPECT_EQ(output, e->output());
  EXPECT_EQ(0, memcmp(&Default, &e->decorations(), sizeof(LogDecorations)));
  EXPECT_STREQ("a log line", e->message());
  written = e->output()->write_blocking(e->decorations(), e->message());
  EXPECT_GT(written, 0);

  EXPECT_TRUE(it.hasNext());
  e = it.next();
  addr = reinterpret_cast<uintptr_t>(e);
  EXPECT_EQ(0, (int)(addr & (sizeof(void*)-1)));
  EXPECT_EQ(output, e->output());
  EXPECT_EQ(0, memcmp(&Default, &e->decorations(), sizeof(LogDecorations)));
  EXPECT_STREQ("yet another", e->message());
  written = e->output()->write_blocking(e->decorations(), e->message());
  EXPECT_GT(written, 0);

  while (buffer->push_back(output, Default, "0123456789abcdef")) {
    line++;
  }

  EXPECT_GT(line, 2);
  while (it.hasNext()) {
    e = it.next();
    addr = reinterpret_cast<uintptr_t>(e);
    EXPECT_EQ(0, (int)(addr & (sizeof(void*)-1)));
    EXPECT_EQ(output, e->output());
    EXPECT_STREQ("0123456789abcdef", e->message());
    written = e->output()->write_blocking(e->decorations(), e->message());
    EXPECT_GT(written, 0);
    line--;
  }
  EXPECT_EQ(line, 2);

  // last one, flush token. expect to succeed even buffer has been full.
  buffer->push_flush_token();
  EXPECT_TRUE(it.hasNext());
  e = it.next();
  EXPECT_EQ(e->output(), nullptr);
  EXPECT_TRUE(e->is_token());
  EXPECT_STREQ("", e->message());
  EXPECT_FALSE(it.hasNext());

  // reset buffer
  buffer->reset();
  EXPECT_FALSE(buffer->iterator().hasNext());

  delete output; // close file
  FREE_C_HEAP_ARRAY(char, name);

  const char* strs[4];
  strs[0] = "a log line";
  strs[1] = "yet another";
  strs[2] = "0123456789abcdef";
  strs[3] = nullptr; // sentinel!
  EXPECT_TRUE(file_contains_substrings_in_order(TestLogFileName, strs));
}

TEST_VM_F(AsyncLogTest, droppingMessage) {
  if (AsyncLogWriter::instance() == nullptr) {
    return;
  }

  set_log_config(TestLogFileName, "logging=debug");
  test_asynclog_drop_messages();
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "messages dropped due to async logging"));
}

TEST_VM_F(AsyncLogTest, stdoutOutput) {
  testing::internal::CaptureStdout();

  if (!set_log_config("stdout", "logging=debug")) {
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
  if (!write_to_file(testing::internal::GetCapturedStdout())) {
    return;
  }

  EXPECT_TRUE(file_contains_substring(TestLogFileName, "LogStreamWithAsyncLogImpl"));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "logStream msg1-msg2-msg3"));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "logStream newline"));

  if (async) {
    EXPECT_TRUE(file_contains_substring(TestLogFileName, "messages dropped due to async logging"));
  }
}

TEST_VM_F(AsyncLogTest, stderrOutput) {
  testing::internal::CaptureStderr();

  if (!set_log_config("stderr", "logging=debug")) {
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
  if (!write_to_file(testing::internal::GetCapturedStderr())) {
    return;
  }

  EXPECT_TRUE(file_contains_substring(TestLogFileName, "LogStreamWithAsyncLogImpl"));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "logStream msg1-msg2-msg3"));
  EXPECT_TRUE(file_contains_substring(TestLogFileName, "logStream newline"));

  if (async) {
    EXPECT_TRUE(file_contains_substring(TestLogFileName, "messages dropped due to async logging"));
  }
}

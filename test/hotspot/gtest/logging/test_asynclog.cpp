/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "logging/log.hpp"
#include "logTestFixture.hpp"
#include "logTestUtils.inline.hpp"
#include "logging/logAsyncFlusher.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "unittest.hpp"


class AsyncLogTest : public LogTestFixture {
  const FlagSetting enabler;
 protected:
  AsyncLogTest(): enabler(AsyncLogging, true) {}
};

TEST_VM_F(AsyncLogTest, fifo) {
  LinkedListDeque<int> fifo;
  LinkedListImpl<int, ResourceObj::C_HEAP, mtLogging> result;

  fifo.push_back(1);
  EXPECT_EQ(fifo.size(), (size_t)1);
  EXPECT_EQ(*(fifo.back()), 1);

  fifo.pop_all(&result);
  EXPECT_EQ(fifo.size(), (size_t)0);
  EXPECT_EQ(NULL, fifo.back());
  EXPECT_EQ(result.size(), (size_t)1);
  EXPECT_EQ(*(result.head()->data()), 1);
  result.clear();

  fifo.push_back(2);
  fifo.push_back(1);
  fifo.pop_all(&result);
  EXPECT_EQ(result.size(), (size_t)2);
  EXPECT_EQ(*(result.head()->data()), 2);
  EXPECT_EQ(*(result.head()->next()->data()), 1);
  result.clear();
  const int N = 1000;
  for (int i=0; i<N; ++i) {
    fifo.push_back(i);
  }
  fifo.pop_all(&result);

  EXPECT_EQ(result.size(), (size_t)N);
  LinkedListIterator<int> it(result.head());
  for (int i=0; i<N; ++i) {
    int* e = it.next();
    EXPECT_EQ(*e, i);
  }
}

TEST_VM_F(AsyncLogTest, deque) {
  LinkedListDeque<int> deque;
  const int N = 10;

  EXPECT_EQ(NULL, deque.front());
  EXPECT_EQ(NULL, deque.back());
  for (int i = 0; i < N; ++i) {
    deque.push_back(i);
  }

  EXPECT_EQ(*(deque.front()), 0);
  EXPECT_EQ(*(deque.back()), N-1);
  EXPECT_EQ(deque.size(), (size_t)N);

  deque.pop_front();
  EXPECT_EQ(deque.size(), (size_t)(N - 1));
  EXPECT_EQ(*(deque.front()), 1);
  EXPECT_EQ(*(deque.back()), N - 1);

  deque.pop_front();
  EXPECT_EQ(deque.size(), (size_t)(N - 2));
  EXPECT_EQ(*(deque.front()), 2);
  EXPECT_EQ(*(deque.back()), N - 1);


  for (int i=2; i < N-1; ++i) {
    deque.pop_front();
  }
  EXPECT_EQ(deque.size(), (size_t)1);
  EXPECT_EQ(*(deque.back()), N - 1);
  EXPECT_EQ(deque.front(), deque.back());

  deque.pop_front();
  EXPECT_EQ(deque.size(), (size_t)0);
}

class VM_TestFlusher: public VM_GTestExecuteAtSafepoint {
public:
  void doit() {
    LogStream ls(Log(logging)::info());
    outputStream* os = &ls;
    os->print_cr("LogStreamWithAsyncLogImpl");
    os->print_cr("LogStreamWithAsyncLogImpl secondline");

    //multi-lines
    os->print("logStream msg1-");
    os->print("msg2-");
    os->print("msg3\n");
    os->print_cr("logStream newline");

    test_asynclog_raw();
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
};

TEST_VM_F(AsyncLogTest, asynclog) {
  set_log_config(TestLogFileName, "logging=debug", NULL, "async=true");

  VM_TestFlusher op;
  ThreadInVMfromNative invm(JavaThread::current());
  VMThread::execute(&op);

  LogAsyncFlusher::cleanup();
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
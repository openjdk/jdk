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
 * ac_heapanied this code).
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
#include "logging/logLevel.hpp"
#include "logging/logOutput.hpp"
#include "logging/logTag.hpp"
#include "logging/logTagSet.hpp"
#include "unittest.hpp"

// Test the default level for each tagset
TEST(LogTagSet, defaults) {
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    char buf[256];
    ts->label(buf, sizeof(buf));
    SCOPED_TRACE(buf);
    EXPECT_TRUE(ts->is_level(LogLevel::Error));
    EXPECT_TRUE(ts->is_level(LogLevel::Warning));
    EXPECT_FALSE(ts->is_level(LogLevel::Info));
    EXPECT_TRUE(ts->has_output(LogOutput::Stdout));
    EXPECT_FALSE(ts->has_output(LogOutput::Stderr));
  }
}

TEST(LogTagSet, has_output) {
  LogTagSet& ts = LogTagSetMapping<LOG_TAGS(logging)>::tagset();
  ts.set_output_level(LogOutput::Stderr, LogLevel::Trace);
  EXPECT_TRUE(ts.has_output(LogOutput::Stderr));
  EXPECT_FALSE(ts.has_output(NULL));
  ts.set_output_level(LogOutput::Stderr, LogLevel::Off);
  EXPECT_FALSE(ts.has_output(LogOutput::Stderr));
}

TEST(LogTagSet, ntags) {
  const LogTagSet& ts = LogTagSetMapping<LOG_TAGS(logging)>::tagset();
  EXPECT_EQ(1u, ts.ntags());
  const LogTagSet& ts2 = LogTagSetMapping<LOG_TAGS(logging, gc, class, safepoint, heap)>::tagset();
  EXPECT_EQ(5u, ts2.ntags());
}

TEST(LogTagSet, is_level) {
  LogTagSet& ts = LogTagSetMapping<LOG_TAGS(logging)>::tagset();
  // Set info level on stdout and verify that is_level() reports correctly
  ts.set_output_level(LogOutput::Stdout, LogLevel::Info);
  EXPECT_TRUE(ts.is_level(LogLevel::Error));
  EXPECT_TRUE(ts.is_level(LogLevel::Warning));
  EXPECT_TRUE(ts.is_level(LogLevel::Info));
  EXPECT_FALSE(ts.is_level(LogLevel::Debug));
  EXPECT_FALSE(ts.is_level(LogLevel::Trace));
  ts.set_output_level(LogOutput::Stdout, LogLevel::Default);
  EXPECT_TRUE(ts.is_level(LogLevel::Default));
}

TEST(LogTagSet, level_for) {
  LogOutput* output = LogOutput::Stdout;
  LogTagSet& ts = LogTagSetMapping<LOG_TAGS(logging)>::tagset();
  for (uint i = 0; i < LogLevel::Count; i++) {
    LogLevelType level = static_cast<LogLevelType>(i);
    // Set the level and verify that level_for() reports it back
    ts.set_output_level(output, level);
    EXPECT_EQ(level, ts.level_for(output));
  }
  ts.set_output_level(output, LogLevel::Default);
}

TEST(LogTagSet, contains) {
  // Verify that contains works as intended for a few predetermined tagsets
  const LogTagSet& ts = LogTagSetMapping<LOG_TAGS(logging)>::tagset();
  EXPECT_TRUE(ts.contains(PREFIX_LOG_TAG(logging)));
  EXPECT_FALSE(ts.contains(PREFIX_LOG_TAG(gc)));
  EXPECT_FALSE(ts.contains(PREFIX_LOG_TAG(class)));

  const LogTagSet& ts2 = LogTagSetMapping<LOG_TAGS(logging, gc)>::tagset();
  EXPECT_TRUE(ts2.contains(PREFIX_LOG_TAG(logging)));
  EXPECT_TRUE(ts2.contains(PREFIX_LOG_TAG(gc)));
  EXPECT_FALSE(ts2.contains(PREFIX_LOG_TAG(class)));

  const LogTagSet& ts3 = LogTagSetMapping<LOG_TAGS(logging, gc, class)>::tagset();
  EXPECT_TRUE(ts3.contains(PREFIX_LOG_TAG(logging)));
  EXPECT_TRUE(ts3.contains(PREFIX_LOG_TAG(gc)));
  EXPECT_TRUE(ts3.contains(PREFIX_LOG_TAG(class)));
  EXPECT_FALSE(ts3.contains(PREFIX_LOG_TAG(safepoint)));

  const LogTagSet& ts4 = LogTagSetMapping<LOG_TAGS(logging, gc, class, safepoint, heap)>::tagset();
  EXPECT_TRUE(ts4.contains(PREFIX_LOG_TAG(logging)));
  EXPECT_TRUE(ts4.contains(PREFIX_LOG_TAG(gc)));
  EXPECT_TRUE(ts4.contains(PREFIX_LOG_TAG(class)));
  EXPECT_TRUE(ts4.contains(PREFIX_LOG_TAG(safepoint)));
  EXPECT_TRUE(ts4.contains(PREFIX_LOG_TAG(heap)));
}

TEST(LogTagSet, label) {
  char buf[256];
  const LogTagSet& ts = LogTagSetMapping<LOG_TAGS(logging, safepoint)>::tagset();
  ASSERT_NE(-1, ts.label(buf, sizeof(buf)));
  EXPECT_STREQ("logging,safepoint", buf);
  // Verify using a custom separator
  ASSERT_NE(-1, ts.label(buf, sizeof(buf), "++"));
  EXPECT_STREQ("logging++safepoint", buf);

  // Verify with three tags
  const LogTagSet& ts1 = LogTagSetMapping<LOG_TAGS(logging, safepoint, jni)>::tagset();
  ASSERT_NE(-1, ts1.label(buf, sizeof(buf)));
  EXPECT_STREQ("logging,safepoint,jni", buf);

  // Verify with a single tag
  const LogTagSet& ts2 = LogTagSetMapping<LOG_TAGS(logging)>::tagset();
  ASSERT_NE(-1, ts2.label(buf, sizeof(buf)));
  EXPECT_STREQ("logging", buf);
}

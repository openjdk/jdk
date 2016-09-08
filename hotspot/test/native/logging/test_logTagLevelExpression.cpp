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
#include "logging/logLevel.hpp"
#include "logging/logTagLevelExpression.hpp"
#include "logging/logTagSet.hpp"
#include "unittest.hpp"
#include "utilities/globalDefinitions.hpp"

TEST(LogTagLevelExpression, combination_limit) {
  size_t max_combinations = LogTagLevelExpression::MaxCombinations;
  EXPECT_GT(max_combinations, LogTagSet::ntagsets())
      << "Combination limit not sufficient for configuring all available tag sets";
}

TEST(LogTagLevelExpression, parse) {
  char buf[256];
  const char* invalid_substr[] = {
    "=", "+", " ", "+=", "+=*", "*+", " +", "**", "++", ".", ",", ",," ",+",
    " *", "all+", "all*", "+all", "+all=Warning", "==Info", "=InfoWarning",
    "BadTag+", "logging++", "logging*+", ",=", "gc+gc+"
  };
  const char* valid_expression[] = {
    "all", "gc", "gc,logging", "gc+logging", "logging+gc", "logging+gc,gc", "logging+gc*", "gc=trace",
    "gc=trace,logging=info", "logging+gc=trace", "logging+gc=trace,gc+logging=warning,logging",
    "gc,all=info", "logging*", "logging*=info", "gc+logging*=error", "logging*,gc=info"
  };

  // Verify valid expressions parse without problems
  for (size_t i = 0; i < ARRAY_SIZE(valid_expression); i++) {
    LogTagLevelExpression expr;
    EXPECT_TRUE(expr.parse(valid_expression[i])) << "Valid expression '" << valid_expression[i] << "' did not parse";
  }

  // Verify we can use 'all' with each available level
  for (uint level = LogLevel::First; level <= LogLevel::Last; level++) {
    char buf[32];
    int ret = jio_snprintf(buf, sizeof(buf), "all=%s", LogLevel::name(static_cast<LogLevelType>(level)));
    ASSERT_NE(ret, -1);

    LogTagLevelExpression expr;
    EXPECT_TRUE(expr.parse(buf));
  }

  // Verify invalid expressions do not parse
  for (size_t i = 0; i < ARRAY_SIZE(valid_expression); i++) {
    for (size_t j = 0; j < ARRAY_SIZE(invalid_substr); j++) {
      // Prefix with invalid substr
      LogTagLevelExpression expr;
      jio_snprintf(buf, sizeof(buf), "%s%s", invalid_substr[j], valid_expression[i]);
      EXPECT_FALSE(expr.parse(buf)) << "'" << buf << "'" << " considered legal";

      // Suffix with invalid substr
      LogTagLevelExpression expr1;
      jio_snprintf(buf, sizeof(buf), "%s%s", valid_expression[i], invalid_substr[j]);
      EXPECT_FALSE(expr1.parse(buf)) << "'" << buf << "'" << " considered legal";

      // Use only the invalid substr
      LogTagLevelExpression expr2;
      EXPECT_FALSE(expr2.parse(invalid_substr[j])) << "'" << invalid_substr[j] << "'" << " considered legal";
    }

    // Suffix/prefix with some unique invalid prefixes/suffixes
    LogTagLevelExpression expr;
    jio_snprintf(buf, sizeof(buf), "*%s", valid_expression[i]);
    EXPECT_FALSE(expr.parse(buf)) << "'" << buf << "'" << " considered legal";

    LogTagLevelExpression expr1;
    jio_snprintf(buf, sizeof(buf), "logging*%s", valid_expression[i]);
    EXPECT_FALSE(expr1.parse(buf)) << "'" << buf << "'" << " considered legal";
  }
}

// Test the level_for() function for an empty expression
TEST(LogTagLevelExpression, level_for_empty) {
  LogTagLevelExpression emptyexpr;
  ASSERT_TRUE(emptyexpr.parse(""));
  // All tagsets should be unspecified since the expression doesn't involve any tagset
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    EXPECT_EQ(LogLevel::Unspecified, emptyexpr.level_for(*ts));
  }
}

// Test level_for() with "all" without any specified level
TEST(LogTagLevelExpression, level_for_all) {
  LogTagLevelExpression allexpr;
  ASSERT_TRUE(allexpr.parse("all"));
  // Level will be unspecified since no level was given
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    EXPECT_EQ(LogLevel::Unspecified, allexpr.level_for(*ts));
  }
}

// Test level_for() with "all=debug"
TEST(LogTagLevelExpression, level_for_all_debug) {
  LogTagLevelExpression alldebugexpr;
  ASSERT_TRUE(alldebugexpr.parse("all=debug"));
  // All tagsets should report debug level
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    EXPECT_EQ(LogLevel::Debug, alldebugexpr.level_for(*ts));
  }
}

// Test level_for() with "all=off"
TEST(LogTagLevelExpression, level_for_all_off) {
  LogTagLevelExpression alloffexpr;
  ASSERT_TRUE(alloffexpr.parse("all=off"));
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    EXPECT_EQ(LogLevel::Off, alloffexpr.level_for(*ts));
  }
}

// Test level_for() with an expression that has overlap (last subexpression should be used)
TEST(LogTagLevelExpression, level_for_overlap) {
  LogTagLevelExpression overlapexpr;
  // The all=warning will be overridden with gc=info and/or logging+safepoint*=trace
  ASSERT_TRUE(overlapexpr.parse("all=warning,gc=info,logging+safepoint*=trace"));
  for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
    if (ts->contains(PREFIX_LOG_TAG(gc)) && ts->ntags() == 1) {
      EXPECT_EQ(LogLevel::Info, overlapexpr.level_for(*ts));
    } else if (ts->contains(PREFIX_LOG_TAG(logging)) && ts->contains(PREFIX_LOG_TAG(safepoint))) {
      EXPECT_EQ(LogLevel::Trace, overlapexpr.level_for(*ts));
    } else {
      EXPECT_EQ(LogLevel::Warning, overlapexpr.level_for(*ts));
    }
  }
  EXPECT_EQ(LogLevel::Warning, overlapexpr.level_for(LogTagSetMapping<LOG_TAGS(class)>::tagset()));
  EXPECT_EQ(LogLevel::Info, overlapexpr.level_for(LogTagSetMapping<LOG_TAGS(gc)>::tagset()));
  EXPECT_EQ(LogLevel::Trace, overlapexpr.level_for(LogTagSetMapping<LOG_TAGS(logging, safepoint)>::tagset()));
  EXPECT_EQ(LogLevel::Trace,
            overlapexpr.level_for(LogTagSetMapping<LOG_TAGS(logging, gc, class, safepoint, heap)>::tagset()));
}

// Test level_for() with an expression containing two independent subexpressions
TEST(LogTagLevelExpression, level_for_disjoint) {
  LogTagLevelExpression reducedexpr;
  ASSERT_TRUE(reducedexpr.parse("gc+logging=trace,class*=error"));
  EXPECT_EQ(LogLevel::Error, reducedexpr.level_for(LogTagSetMapping<LOG_TAGS(class)>::tagset()));
  EXPECT_EQ(LogLevel::Error, reducedexpr.level_for(LogTagSetMapping<LOG_TAGS(safepoint, class)>::tagset()));
  EXPECT_EQ(LogLevel::NotMentioned, reducedexpr.level_for(LogTagSetMapping<LOG_TAGS(safepoint)>::tagset()));
  EXPECT_EQ(LogLevel::NotMentioned, reducedexpr.level_for(LogTagSetMapping<LOG_TAGS(logging)>::tagset()));
  EXPECT_EQ(LogLevel::NotMentioned, reducedexpr.level_for(LogTagSetMapping<LOG_TAGS(gc)>::tagset()));
  EXPECT_EQ(LogLevel::Trace, reducedexpr.level_for(LogTagSetMapping<LOG_TAGS(logging, gc)>::tagset()));
}

// Test level_for() with an expression that is completely overridden in the last part of the expression
TEST(LogTagLevelExpression, level_for_override) {
  LogTagLevelExpression overrideexpr;
  // No matter what, everything should be set to error level because of the last part
  ASSERT_TRUE(overrideexpr.parse("logging,gc*=trace,all=error"));
  EXPECT_EQ(LogLevel::Error, overrideexpr.level_for(LogTagSetMapping<LOG_TAGS(class)>::tagset()));
  EXPECT_EQ(LogLevel::Error, overrideexpr.level_for(LogTagSetMapping<LOG_TAGS(logging)>::tagset()));
  EXPECT_EQ(LogLevel::Error, overrideexpr.level_for(LogTagSetMapping<LOG_TAGS(gc)>::tagset()));
  EXPECT_EQ(LogLevel::Error, overrideexpr.level_for(LogTagSetMapping<LOG_TAGS(logging, gc)>::tagset()));
}

// Test level_for() with a mixed expression with a bit of everything
TEST(LogTagLevelExpression, level_for_mixed) {
  LogTagLevelExpression mixedexpr;
  ASSERT_TRUE(mixedexpr.parse("all=warning,gc*=debug,gc=trace,safepoint*=off"));
  EXPECT_EQ(LogLevel::Warning, mixedexpr.level_for(LogTagSetMapping<LOG_TAGS(logging)>::tagset()));
  EXPECT_EQ(LogLevel::Warning, mixedexpr.level_for(LogTagSetMapping<LOG_TAGS(logging, class)>::tagset()));
  EXPECT_EQ(LogLevel::Debug, mixedexpr.level_for(LogTagSetMapping<LOG_TAGS(gc, class)>::tagset()));
  EXPECT_EQ(LogLevel::Off, mixedexpr.level_for(LogTagSetMapping<LOG_TAGS(gc, safepoint, logging)>::tagset()));
  EXPECT_EQ(LogLevel::Off, mixedexpr.level_for(LogTagSetMapping<LOG_TAGS(safepoint)>::tagset()));
  EXPECT_EQ(LogLevel::Debug, mixedexpr.level_for(LogTagSetMapping<LOG_TAGS(logging, gc)>::tagset()));
  EXPECT_EQ(LogLevel::Trace, mixedexpr.level_for(LogTagSetMapping<LOG_TAGS(gc)>::tagset()));
}

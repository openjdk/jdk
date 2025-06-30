/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jvm.h"
#include "logging/logDecorators.hpp"
#include "logging/logTag.hpp"
#include "unittest.hpp"


class TestLogDecorators : public testing::Test {
  using LD = LogDecorators;

  static const size_t defaults_cnt = 3;
  LD::DefaultUndecoratedSelection defaults[defaults_cnt] = {
    LD::DefaultUndecoratedSelection::make<LogLevelType::Trace, LOG_TAGS(gc)>(),
    LD::DefaultUndecoratedSelection::make<LogLevelType::Trace, LOG_TAGS(jit)>(),
    LD::DefaultUndecoratedSelection::make<LogLevelType::NotMentioned, LOG_TAGS(ref)>(),
  };

public:
  void test_default_decorators() {
    LogTagType tags[LogTag::MaxTags] = { LogTag::__NO_TAG, LogTag::__NO_TAG, LogTag::__NO_TAG, LogTag::__NO_TAG, LogTag::__NO_TAG };
    bool result;

    // If a -Xlog selection matches one of the undecorated defaults, the default decorators will be disabled
    tags[0] = LogTagType::_jit;
    result = LD::has_disabled_default_decorators(LogSelection(tags, false, LogLevelType::Trace), defaults, defaults_cnt);
    EXPECT_TRUE(result);


    // If a -Xlog selection contains one of the undecorated defaults, the default decorators will be disabled
    tags[0] = LogTagType::_jit;
    tags[1] = LogTagType::_inlining;
    result = LD::has_disabled_default_decorators(LogSelection(tags, false, LogLevelType::Trace), defaults, defaults_cnt);
    EXPECT_TRUE(result);


    // Wildcards are ignored
    tags[0] = LogTag::_compilation;
    result = LD::has_disabled_default_decorators(LogSelection(tags, true, LogLevelType::Debug), defaults, defaults_cnt);
    EXPECT_FALSE(result);


    // If there is no level match, default decorators are kept
    tags[0] = LogTagType::_gc;
    result = LD::has_disabled_default_decorators(LogSelection(tags, false, LogLevelType::Info), defaults, defaults_cnt);
    EXPECT_FALSE(result);


    // If NotMentioned is specified, it will match every level and so default decorators will never be added if there is a positive tagset match
    tags[0] = LogTagType::_ref;
    result = LD::has_disabled_default_decorators(LogSelection(tags, false, LogLevelType::Error), defaults, defaults_cnt);
    EXPECT_TRUE(result);
    result = LD::has_disabled_default_decorators(LogSelection(tags, false, LogLevelType::Warning), defaults, defaults_cnt);
    EXPECT_TRUE(result);
    result = LD::has_disabled_default_decorators(LogSelection(tags, false, LogLevelType::Info), defaults, defaults_cnt);
    EXPECT_TRUE(result);
    result = LD::has_disabled_default_decorators(LogSelection(tags, false, LogLevelType::Debug), defaults, defaults_cnt);
    EXPECT_TRUE(result);
    result = LD::has_disabled_default_decorators(LogSelection(tags, false, LogLevelType::Trace), defaults, defaults_cnt);
    EXPECT_TRUE(result);
  }

  void test_mask_from_decorators() {
    // Single tags should yield 2^{decorator_value_in_enum}
    EXPECT_EQ(LD::mask_from_decorators(LD::time_decorator), (uint)(1 << LD::time_decorator));
    EXPECT_EQ(LD::mask_from_decorators(LD::pid_decorator),  (uint)(1 << LD::pid_decorator));
    EXPECT_EQ(LD::mask_from_decorators(LD::tid_decorator),  (uint)(1 << LD::tid_decorator));
    EXPECT_EQ(LD::mask_from_decorators(LD::tags_decorator), (uint)(1 << LD::tags_decorator));

    // Combinations of decorators should fill the mask accordingly to their bitmask positions
    uint mask = (1 << LD::time_decorator) | (1 << LD::uptimemillis_decorator) | (1 << LD::tid_decorator);
    EXPECT_EQ(LD::mask_from_decorators(LD::time_decorator, LD::uptimemillis_decorator, LD::tid_decorator), mask);
  }
};

TEST_VM_F(TestLogDecorators, MaskFromDecorators) {
  test_mask_from_decorators();
}

TEST_VM_F(TestLogDecorators, HasDefaultDecorators) {
  test_default_decorators();
}

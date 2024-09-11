/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "logging/logDecorators.hpp"
#include "runtime/os.hpp"
#include "unittest.hpp"


class TestLogDecorators : public testing::Test {
  using DefaultDecorator = LogDecorators::DefaultDecorator;
  using LD = LogDecorators;

  DefaultDecorator defaults[6] = {
    { LogLevelType::Trace, LD::mask_from_decorators(LD::pid_decorator), LogTagType::_gc },
    { LogLevelType::Trace, LD::mask_from_decorators(LD::NoDecorators), LogTagType::_jit },
    { LogLevelType::NotMentioned, LD::mask_from_decorators(LD::pid_decorator, LD::tid_decorator), LogTagType::__NO_TAG },
    { LogLevelType::Trace, LD::mask_from_decorators(LD::time_decorator), LogTagType::_gc },
    { LogLevelType::Debug, LD::mask_from_decorators(LD::pid_decorator), LogTagType::_compilation },
    { LogLevelType::Trace, LD::mask_from_decorators(LD::uptimemillis_decorator), LogTagType::_compilation, LogTagType::_codecache }
  };

public:
  void test_default_decorators() {
    LogTagType tags[LogTag::MaxTags] = { LogTag::__NO_TAG, LogTag::__NO_TAG, LogTag::__NO_TAG, LogTag::__NO_TAG, LogTag::__NO_TAG };
    uint out_mask;


    // Log selection with matching tag exactly should find default
    tags[0] = LogTagType::_jit;
    bool result = LD::has_default_decorator(LogSelection(tags, false, LogLevelType::Trace), &out_mask, defaults);
    EXPECT_TRUE(result);
    EXPECT_EQ(LD::mask_from_decorators(LD::NoDecorators), out_mask);


    // Wildcards are ignored
    tags[0] = LogTag::__NO_TAG;
    result = LD::has_default_decorator(LogSelection(tags, true, LogLevelType::Trace), &out_mask, defaults);
    EXPECT_FALSE(result);


    // If several defaults match with the same specificity, all defaults are applied
    tags[0] = LogTagType::_gc;
    result = LD::has_default_decorator(LogSelection(tags, false, LogLevelType::Trace), &out_mask, defaults);
    EXPECT_TRUE(result);
    EXPECT_EQ(LD::mask_from_decorators(LD::pid_decorator, LD::time_decorator), out_mask);
  }
};

TEST_VM_F(TestLogDecorators, MaskFromDecorators) {
}

TEST_VM_F(TestLogDecorators, HasDefaultDecorators) {
  test_default_decorators();
}

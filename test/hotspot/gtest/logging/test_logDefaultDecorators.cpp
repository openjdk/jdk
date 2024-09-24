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

  static const size_t defaults_cnt = 6;
  DefaultDecorator defaults[defaults_cnt] = {
    { LogLevelType::Trace, LD::mask_from_decorators(LD::pid_decorator), LogTagType::_gc },
    { LogLevelType::Trace, LD::mask_from_decorators(LD::NoDecorators), LogTagType::_jit },
    { LogLevelType::NotMentioned, LD::mask_from_decorators(LD::pid_decorator, LD::tid_decorator), LogTagType::_ref },
    { LogLevelType::Trace, LD::mask_from_decorators(LD::time_decorator), LogTagType::_gc },
    { LogLevelType::Debug, LD::mask_from_decorators(LD::pid_decorator), LogTagType::_compilation },
    { LogLevelType::Debug, LD::mask_from_decorators(LD::uptimemillis_decorator), LogTagType::_compilation, LogTagType::_codecache }
  };

public:
  void test_default_decorators() {
    LogTagType tags[LogTag::MaxTags] = { LogTag::__NO_TAG, LogTag::__NO_TAG, LogTag::__NO_TAG, LogTag::__NO_TAG, LogTag::__NO_TAG };
    uint out_mask = 0;


    // Log selection with matching tag exactly should find default
    tags[0] = LogTagType::_jit;
    LD::get_default_decorators(LogSelection(tags, false, LogLevelType::Trace), &out_mask, defaults, defaults_cnt);
    EXPECT_EQ(LD::mask_from_decorators(LD::NoDecorators), out_mask);


    // Wildcards are ignored
    tags[0] = LogTag::_compilation;
    out_mask = 0;
    LD::get_default_decorators(LogSelection(tags, true, LogLevelType::Debug), &out_mask, defaults, defaults_cnt);
    EXPECT_EQ(LD::mask_from_decorators(LD::pid_decorator), out_mask);


    // If several defaults match with the same specificity, all defaults are applied
    tags[0] = LogTagType::_gc;
    out_mask = 0;
    LD::get_default_decorators(LogSelection(tags, false, LogLevelType::Trace), &out_mask, defaults, defaults_cnt);
    EXPECT_EQ(LD::mask_from_decorators(LD::pid_decorator, LD::time_decorator), out_mask);


    // If several defaults match but one has higher specificity, only its defaults are applied
    tags[0] = LogTagType::_compilation;
    tags[1] = LogTagType::_codecache;
    out_mask = 0;
    LD::get_default_decorators(LogSelection(tags, false, LogLevelType::Debug), &out_mask, defaults, defaults_cnt);
    EXPECT_EQ(LD::mask_from_decorators(LD::uptimemillis_decorator), out_mask);


    // If a level is not specified to match (via AnyTag or NotMentioned), it should not be taken into account
    tags[0] = LogTagType::_ref;
    tags[1] = LogTagType::__NO_TAG;
    out_mask = 0;
    LD::get_default_decorators(LogSelection(tags, false, LogLevelType::Info), &out_mask, defaults, defaults_cnt);
    EXPECT_EQ(LD::mask_from_decorators(LD::pid_decorator, LD::tid_decorator), out_mask);
    out_mask = 0;
    LD::get_default_decorators(LogSelection(tags, false, LogLevelType::Trace), &out_mask, defaults, defaults_cnt);
    EXPECT_EQ(LD::mask_from_decorators(LD::pid_decorator, LD::tid_decorator), out_mask);


    // In spite of the previous, higher specificities should still prevail
    defaults[5] = { LogLevelType::Debug, LD::mask_from_decorators(LD::uptimemillis_decorator), LogTagType::_ref, LogTagType::_codecache };
    tags[1] = LogTagType::_codecache;
    out_mask = 0;
    LD::get_default_decorators(LogSelection(tags, false, LogLevelType::Debug), &out_mask, defaults, defaults_cnt);
    EXPECT_EQ(LD::mask_from_decorators(LD::uptimemillis_decorator), out_mask);
  }

  void test_mask_from_decorators() {
    // Single tags should yield 2^{decorator_value_in_enum}
    EXPECT_EQ(LD::mask_from_decorators(LD::time_decorator), (uint)(1 << LD::time_decorator));
    EXPECT_EQ(LD::mask_from_decorators(LD::pid_decorator),  (uint)(1 << LD::pid_decorator));
    EXPECT_EQ(LD::mask_from_decorators(LD::tid_decorator),  (uint)(1 << LD::tid_decorator));
    EXPECT_EQ(LD::mask_from_decorators(LD::tags_decorator), (uint)(1 << LD::tags_decorator));


    // NoDecorators should yield an empty mask
    EXPECT_EQ(LD::mask_from_decorators(LD::NoDecorators), 0U);


    // Combinations of decorators should fill the mask accordingly to their bitmask positions
    uint mask = (1 << LD::time_decorator) | (1 << LD::uptimemillis_decorator) | (1 << LD::tid_decorator);
    EXPECT_EQ(LD::mask_from_decorators(LD::time_decorator, LD::uptimemillis_decorator, LD::tid_decorator), mask);


    // If a combination has NoDecorators in it, it takes precedence and the mask is zero
    EXPECT_EQ(LD::mask_from_decorators(LD::time_decorator, LD::NoDecorators, LD::tid_decorator), 0U);
  }
};

TEST_VM_F(TestLogDecorators, MaskFromDecorators) {
  test_mask_from_decorators();
}

TEST_VM_F(TestLogDecorators, HasDefaultDecorators) {
  test_default_decorators();
}

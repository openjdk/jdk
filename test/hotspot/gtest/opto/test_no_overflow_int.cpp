/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "opto/noOverflowInt.hpp"
#include "unittest.hpp"

static void check_jlong(const jlong val) {
  const NoOverflowInt x(val);

  if (val > max_jint || min_jint > val) {
    ASSERT_TRUE(x.is_NaN());
  } else {
    ASSERT_FALSE(x.is_NaN());
    ASSERT_EQ(x.value(), val);
  }
}

TEST_VM(opto, NoOverflowInt_check_jlong) {
  jlong start = (jlong)min_jint - 10000LL;
  jlong end   = (jlong)max_jint + 10000LL;
  for (jlong i = start; i < end; i+= 1000LL) {
    check_jlong(i);
  }

  check_jlong((jlong)min_jint - 1LL);
  check_jlong((jlong)min_jint);
  check_jlong((jlong)min_jint + 1LL);
  check_jlong((jlong)max_jint - 1LL);
  check_jlong((jlong)max_jint);
  check_jlong((jlong)max_jint + 1LL);

  const NoOverflowInt nan;
  ASSERT_TRUE(nan.is_NaN());
}

TEST_VM(opto, NoOverflowInt_add_sub) {
  const NoOverflowInt nan;
  const NoOverflowInt zero(0);
  const NoOverflowInt one(1);
  const NoOverflowInt two(2);
  const NoOverflowInt big(1 << 30);

  ASSERT_EQ((one + two).value(), 3);
  ASSERT_EQ((one - two).value(), -1);
  ASSERT_TRUE((nan + one).is_NaN());
  ASSERT_TRUE((one + nan).is_NaN());
  ASSERT_TRUE((nan + nan).is_NaN());
  ASSERT_TRUE((nan - one).is_NaN());
  ASSERT_TRUE((one - nan).is_NaN());
  ASSERT_TRUE((nan - nan).is_NaN());

  ASSERT_EQ((big + one).value(), (1 << 30) + 1);
  ASSERT_TRUE((big + big).is_NaN());
  ASSERT_EQ((big - one).value(), (1 << 30) - 1);
  ASSERT_EQ((big - big).value(), 0);

  ASSERT_EQ((big - one + big).value(), max_jint);
  ASSERT_EQ((zero - big - big).value(), min_jint);
  ASSERT_TRUE((zero - big - big - one).is_NaN());
}

TEST_VM(opto, NoOverflowInt_mul) {
  const NoOverflowInt nan;
  const NoOverflowInt zero(0);
  const NoOverflowInt one(1);
  const NoOverflowInt two(2);
  const NoOverflowInt big(1 << 30);

  ASSERT_EQ((one * two).value(), 2);
  ASSERT_TRUE((nan * one).is_NaN());
  ASSERT_TRUE((one * nan).is_NaN());
  ASSERT_TRUE((nan * nan).is_NaN());

  ASSERT_EQ((big * one).value(), (1 << 30));
  ASSERT_EQ((one * big).value(), (1 << 30));
  ASSERT_EQ((big * zero).value(), 0);
  ASSERT_EQ((zero * big).value(), 0);
  ASSERT_TRUE((big * big).is_NaN());
  ASSERT_TRUE((big * two).is_NaN());

  ASSERT_EQ(((big - one) * two).value(), max_jint - 1);
  ASSERT_EQ(((one - big) * two).value(), min_jint + 2);
  ASSERT_EQ(((zero - big) * two).value(), min_jint);
  ASSERT_TRUE(((big + one) * two).is_NaN());
  ASSERT_TRUE(((zero - big - one) * two).is_NaN());
}

TEST_VM(opto, NoOverflowInt_lshift) {
  const NoOverflowInt nan;
  const NoOverflowInt zero(0);
  const NoOverflowInt one(1);
  const NoOverflowInt two(2);
  const NoOverflowInt big(1 << 30);

  for (int i = 0; i < 31; i++) {
    ASSERT_EQ((one << NoOverflowInt(i)).value(), 1LL << i);
  }
  for (int i = 31; i < 1000; i++) {
    ASSERT_TRUE((one << NoOverflowInt(i)).is_NaN());
  }
  for (int i = -1000; i < 0; i++) {
    ASSERT_TRUE((one << NoOverflowInt(i)).is_NaN());
  }

  ASSERT_EQ((NoOverflowInt(3) << NoOverflowInt(2)).value(), 3 * 4);
  ASSERT_EQ((NoOverflowInt(11) << NoOverflowInt(5)).value(), 11 * 32);
  ASSERT_EQ((NoOverflowInt(-13) << NoOverflowInt(4)).value(), -13 * 16);
}

TEST_VM(opto, NoOverflowInt_misc) {
  const NoOverflowInt nan;
  const NoOverflowInt zero(0);
  const NoOverflowInt one(1);
  const NoOverflowInt two(2);
  const NoOverflowInt big(1 << 30);

  // operator==
  ASSERT_FALSE(nan == nan);
  ASSERT_FALSE(nan == zero);
  ASSERT_FALSE(zero == nan);
  ASSERT_TRUE(zero == zero);
  ASSERT_TRUE(one == one);
  ASSERT_TRUE((one + two) == (two + one));
  ASSERT_TRUE((big + two) == (two + big));
  ASSERT_FALSE((big + big) == (big + big));
  ASSERT_TRUE((big - one + big) == (big - one + big));

  // abs
  for (int i = 0; i < (1 << 31); i += 1024) {
    ASSERT_EQ(NoOverflowInt(i).abs().value(), i);
    ASSERT_EQ(NoOverflowInt(-i).abs().value(), i);
  }
  ASSERT_EQ(NoOverflowInt(max_jint).abs().value(), max_jint);
  ASSERT_EQ(NoOverflowInt(min_jint + 1).abs().value(), max_jint);
  ASSERT_TRUE(NoOverflowInt(min_jint).abs().is_NaN());
  ASSERT_TRUE(NoOverflowInt(nan).abs().is_NaN());

  // is_multiple_of
  ASSERT_TRUE(one.is_multiple_of(one));
  ASSERT_FALSE(one.is_multiple_of(nan));
  ASSERT_FALSE(nan.is_multiple_of(one));
  ASSERT_FALSE(nan.is_multiple_of(nan));
  for (int i = 0; i < (1 << 31); i += 1023) {
    ASSERT_TRUE(NoOverflowInt(i).is_multiple_of(one));
    ASSERT_TRUE(NoOverflowInt(-i).is_multiple_of(one));
    ASSERT_FALSE(NoOverflowInt(i).is_multiple_of(zero));
    ASSERT_FALSE(NoOverflowInt(-i).is_multiple_of(zero));
  }
  ASSERT_TRUE(NoOverflowInt(33 * 7).is_multiple_of(NoOverflowInt(33)));
  ASSERT_TRUE(NoOverflowInt(13 * 5).is_multiple_of(NoOverflowInt(5)));
  ASSERT_FALSE(NoOverflowInt(7).is_multiple_of(NoOverflowInt(5)));
}


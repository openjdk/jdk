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
 */

#include "precompiled.hpp"
#include "gc/shenandoah/shenandoahNumberSeq.hpp"
#include <iostream>
#include "unittest.hpp"
#include "utilities/ostream.hpp"

class ShenandoahNumberSeqTest: public ::testing::Test {
 protected:
  HdrSeq seq;
};

class BasicShenandoahNumberSeqTest: public ShenandoahNumberSeqTest {
 protected:
  const double err = 0.5;
  BasicShenandoahNumberSeqTest() {
    seq.add(0);
    seq.add(1);
    seq.add(10);
    for (int i = 0; i < 7; i++) {
      seq.add(100);
    }
    std::cout << " p0 = " << seq.percentile(0);
    std::cout << " p10 = " << seq.percentile(10);
    std::cout << " p20 = " << seq.percentile(20);
    std::cout << " p30 = " << seq.percentile(30);
    std::cout << " p50 = " << seq.percentile(50);
    std::cout << " p80 = " << seq.percentile(80);
    std::cout << " p90 = " << seq.percentile(90);
    std::cout << " p100 = " << seq.percentile(100);
  }
};

TEST_VM_F(BasicShenandoahNumberSeqTest, maximum_test) {
  EXPECT_EQ(seq.maximum(), 100);
}

TEST_VM_F(BasicShenandoahNumberSeqTest, minimum_test) {
  EXPECT_EQ(0, seq.percentile(0));
}

TEST_VM_F(BasicShenandoahNumberSeqTest, percentile_test) {
  EXPECT_NEAR(0, seq.percentile(10), err);
  EXPECT_NEAR(1, seq.percentile(20), err);
  EXPECT_NEAR(10, seq.percentile(30), err);
  EXPECT_NEAR(100, seq.percentile(40), err);
  EXPECT_NEAR(100, seq.percentile(50), err);
  EXPECT_NEAR(100, seq.percentile(75), err);
  EXPECT_NEAR(100, seq.percentile(90), err);
  EXPECT_NEAR(100, seq.percentile(100), err);
}

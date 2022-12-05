/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
#include "unittest.hpp"
#include "utilities/ostream.hpp"

static const double epsilon = 0.0001;

template<typename T>
class ShenandoahNumberSeqTest : public ::testing::Test {
  HdrSeq seq;

 public:
  void initialize_seq(double newSeq[]);
  bool test_seq(double v_percentile, double v_value);
};

class BasicShenandoahNumberSeqTest : public ShenandoahNumberSeqTest {
 protected:
  BasicShenandoahNumberSeqTest() {
    seq.add(1);
    seq.add(10);
    for (int i = 0; i < 8; i++) {
      seq.add(100);
    }
  }
};

TEST_VM_F(BasicShenandoahNumberSeqTest, maximum_test) {
  ASSERT_EQ(100, seq.maximum());
  ASSERT_EQ(100, seq.percentile(100));
}

TEST_VM_F(BasicShenandoahNumberSeqTest, minimum_test) {
  ASSERT_EQ(1, seq.minimum());
  ASSERT_EQ(1, seq.percentile(0));
}

TEST_VM_F(BasicShenandoahNumberSeqTest, percentile_test) {
  ASSERT_EQ(100, seq.percentile(75));
  ASSERT_EQ(100, seq.percentile(50));
  ASSERT_EQ(100, seq.percentile(25));
  ASSERT_EQ(10, seq.percentile(20));
  ASSERT_EQ(1, seq.percentile(10));
}

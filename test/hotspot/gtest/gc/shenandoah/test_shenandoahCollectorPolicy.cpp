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

#include "gc/shenandoah/shenandoahCollectorPolicy.hpp"
#include "unittest.hpp"

TEST(ShenandoahCollectorPolicyTest, track_degen_cycles_sanity) {
  ShenandoahCollectorPolicy policy;
  EXPECT_EQ(policy.consecutive_degenerated_gc_count(), 0UL);
  EXPECT_EQ(policy.should_upgrade_degenerated_gc(), false);
}

TEST(ShenandoahCollectorPolicyTest, track_degen_cycles_no_upgrade) {
  ShenandoahCollectorPolicy policy;
  policy.record_degenerated(true, true, true);
  policy.record_degenerated(true, true, true);
  EXPECT_EQ(policy.consecutive_degenerated_gc_count(), 2UL);
  EXPECT_EQ(policy.should_upgrade_degenerated_gc(), false);
}

TEST(ShenandoahCollectorPolicyTest, track_degen_cycles_upgrade) {
  ShenandoahCollectorPolicy policy;
  policy.record_degenerated(true, true, false);
  policy.record_degenerated(true, true, false);
  EXPECT_EQ(policy.consecutive_degenerated_gc_count(), 2UL);
  EXPECT_EQ(policy.should_upgrade_degenerated_gc(), true);
}

TEST(ShenandoahCollectorPolicyTest, track_degen_cycles_reset_progress) {
  ShenandoahCollectorPolicy policy;
  policy.record_degenerated(true, true, false);
  policy.record_degenerated(true, true, true);
  EXPECT_EQ(policy.consecutive_degenerated_gc_count(), 2UL);
  EXPECT_EQ(policy.should_upgrade_degenerated_gc(), false);
}

TEST(ShenandoahCollectorPolicyTest, track_degen_cycles_full_reset) {
  ShenandoahCollectorPolicy policy;
  policy.record_degenerated(true, true, false);
  policy.record_success_full();
  EXPECT_EQ(policy.consecutive_degenerated_gc_count(), 0UL);
  EXPECT_EQ(policy.should_upgrade_degenerated_gc(), false);
}

TEST(ShenandoahCollectorPolicyTest, track_degen_cycles_reset) {
  ShenandoahCollectorPolicy policy;
  policy.record_degenerated(true, true, false);
  policy.record_success_concurrent(true, true);
  EXPECT_EQ(policy.consecutive_degenerated_gc_count(), 0UL);
  EXPECT_EQ(policy.should_upgrade_degenerated_gc(), false);
}

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
#include "gc/shenandoah/shenandoahController.hpp"
#include "unittest.hpp"
#include "utilities/ostream.hpp"

using ::testing::HasSubstr;

TEST_VM(ShenandoahCollectorPolicyTest, track_allocation_stalls) {
  ShenandoahCollectorPolicy policy;
  for (int i = 0; i < ShenandoahController::PHASE_LIMIT; ++i) {
    policy.record_allocation_stall(static_cast<ShenandoahController::ShenandoahCollectorPhase>(i));
  }
  stringStream ss;
  policy.print_gc_stats(&ss);
  ASSERT_THAT(ss.base(), HasSubstr("6 Stalls"));
  ASSERT_THAT(ss.base(), HasSubstr("1 happened at Outside of Cycle"));
  ASSERT_THAT(ss.base(), HasSubstr("1 happened at Initializing"));
  ASSERT_THAT(ss.base(), HasSubstr("1 happened at Roots"));
  ASSERT_THAT(ss.base(), HasSubstr("1 happened at Mark"));
  ASSERT_THAT(ss.base(), HasSubstr("1 happened at Evacuation"));
  ASSERT_THAT(ss.base(), HasSubstr("1 happened at Update References"));
}

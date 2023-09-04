/*
 * Copyright (c) 2023 Red Hat Inc. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "runtime/os.hpp"
#include "runtime/trimNativeHeap.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"
#include "testutils.hpp"
#include "unittest.hpp"

using ::testing::HasSubstr;

// Check the state of the trimmer via print_state; returns the suspend count
static int check_trim_state() {
  char buf [1024];
  stringStream ss(buf, sizeof(buf));
  NativeHeapTrimmer::print_state(&ss);
  if (NativeHeapTrimmer::enabled()) {
    assert(TrimNativeHeapInterval > 0, "Sanity");
    EXPECT_THAT(buf, HasSubstr("Periodic native trim enabled"));

    const char* s = ::strstr(buf, "Trims performed");
    EXPECT_NOT_NULL(s);

    uint64_t num_trims = 0;
    int suspend_count = 0;
    int stopped = 0;
    EXPECT_EQ(::sscanf(s, "Trims performed: " UINT64_FORMAT ", current suspend count: %d, stopped: %d",
                       &num_trims, &suspend_count, &stopped), 3);

    // Number of trims we can reasonably expect should be limited
    const double fudge_factor = 1.5;
    const uint64_t elapsed_ms = (uint64_t)(os::elapsedTime() * fudge_factor * 1000.0);
    const uint64_t max_num_trims = (elapsed_ms / TrimNativeHeapInterval) + 1;
    EXPECT_LE(num_trims, max_num_trims);

    // We should not be stopped
    EXPECT_EQ(stopped, 0);

    // Suspend count must not underflow
    EXPECT_GE(suspend_count, 0);
    return suspend_count;

  } else {
    EXPECT_THAT(buf, HasSubstr("Periodic native trim disabled"));
    EXPECT_THAT(buf, Not(HasSubstr("Trims performed")));
    return 0;
  }
}

TEST_VM(os, TrimNative) {

  if (!NativeHeapTrimmer::enabled()) {
    return;
  }

  // Try recursive pausing. This tests that we are able to pause, that pauses stack,
  // and that stacking works within the same thread.
  int c1 = 0, c2 = 0, c3 = 0;
  {
    NativeHeapTrimmer::SuspendMark sm1("Test1");
    c1 = check_trim_state();
    {
      NativeHeapTrimmer::SuspendMark sm2("Test2");
      c2 = check_trim_state();
      {
        NativeHeapTrimmer::SuspendMark sm3("Test3");
        c3 = check_trim_state();
      }
    }
  }
  // We also check the state: the suspend count should go up. But since we don't know
  // whether concurrent code will have increased the suspend count too, this is fuzzy and
  // we must avoid intermittent false positives.
  EXPECT_GT(c2, c1);
  EXPECT_GT(c3, c2);
}
